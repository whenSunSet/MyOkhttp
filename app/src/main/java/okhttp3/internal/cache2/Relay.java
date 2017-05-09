/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.cache2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import okio.Buffer;
import okio.ByteString;
import okio.Source;
import okio.Timeout;

import static okhttp3.internal.Util.closeQuietly;

/**
 * 将单个upstream source 拷贝进入多个downstream sources。每一个downstream source将返回和upstream source
 * 相同的byte。Downstream sources 可能读取upstream的数据，也可能upstream的数据已经耗尽
 * Replicates a single upstream source into multiple downstream sources. Each downstream source
 * returns the same bytes as the upstream source. Downstream sources may read data either as it
 * is returned by upstream, or after the upstream source has been exhausted.
 *
 * 从upstream中返回的byte数据被写入了本地文件。Downstream sources 读取这个文件就会成功
 * <p>As bytes are returned from upstream they are written to a local file. Downstream sources read
 * from this file as necessary.
 *
 * 这个class也会保持一个小的缓冲区，这是为了从upstream读取少量的数据。
 * 这种方法用来代替保存一个小的文件IO和数据拷贝
 * <p>This class also keeps a small buffer of bytes recently read from upstream. This is intended to
 * save a small amount of file I/O and data copying.
 */
// TODO(jwilson): what to do about timeouts? They could be different and unfortunately when any
//     timeout is hit we like to tear down the whole stream.
final class Relay {
  private static final int SOURCE_UPSTREAM = 1;
  private static final int SOURCE_FILE = 2;

  static final ByteString PREFIX_CLEAN = ByteString.encodeUtf8("OkHttp cache v1\n");
  static final ByteString PREFIX_DIRTY = ByteString.encodeUtf8("OkHttp DIRTY :(\n");
  private static final long FILE_HEADER_SIZE = 32L;

  /**
   * upstream source 的读取和写入的持久化存储文件，这也是他的元数据。其布局如下
   * Read/write persistence of the upstream source and its metadata. Its layout is as follows:
   *
   * <ul>
   *     16 bytes：如果持久化文件是完整的。如果这个文件是不完整的并且不能被使用那么这就是其他的字节序列
   *   <li>16 bytes: either {@code OkHttp cache v1\n} if the persisted file is complete. This is
   *       another sequence of bytes if the file is incomplete and should not be used.
   *       8 bytes:  upstream数据的数量
   *   <li>8 bytes: <i>n</i>: upstream data size
   *      8 bytes:  元数据的数量
   *   <li>8 bytes: <i>m</i>: metadata size
   *   <li><i>n</i> bytes: upstream data
   *   <li><i>m</i> bytes: metadata
   * </ul>
   *
   * 当最后的source被关闭或者没有进一步的允许，那么这个文件将会关闭并返回null
   * <p>This is closed and assigned to null when the last source is closed and no further sources
   * are permitted.
   */
  RandomAccessFile file;

  /**
   * upstream的线程，可能是null，谨慎使用
   * The thread that currently has access to upstream. Possibly null. Guarded by this. */
  Thread upstreamReader;

  /**
   * 当文件已经完整的从upstream被拷贝出来的时候为null，只有{@code upstreamReader}可以访问这个字段
   * Null once the file has a complete copy of the upstream bytes. Only the {@code upstreamReader}
   * thread may access this source.
   */
  Source upstream;

  /**
   * 从upstream抽取数据时候的一个{@code upstreamReader}的缓存。只有{@code upstreamReader}
   * 可以访问这个缓冲
   * A buffer for {@code upstreamReader} to use when pulling bytes from upstream. Only the {@code
   * upstreamReader} thread may access this buffer.
   */
  final Buffer upstreamBuffer = new Buffer();

  /**
   * {@link #upstream}的byte 数量的消耗值。谨慎使用
   * The number of bytes consumed from {@link #upstream}. Guarded by this. */
  long upstreamPos;

  /**
   * 返回true，那么已经没有更多的bytes会从{@code upstream}返回。谨慎使用
   * True if there are no further bytes to read from {@code upstream}. Guarded by this. */
  boolean complete;

  /**
   * 为用户提供的对source 数据的额外持久化
   * User-supplied additional data persisted with the source data. */
  private final ByteString metadata;

  /**
   * 最近从{@link #upstream}中读取的byte，这是一个{@link #file}的后缀。谨慎使用
   * The most recently read bytes from {@link #upstream}. This is a suffix of {@link #file}. Guarded
   * by this.
   */
  final Buffer buffer = new Buffer();

  /**
   * {@code buffer}的最大值
   * The maximum size of {@code buffer}. */
  final long bufferMaxSize;

  /**
   * 活跃的读取这个流的sources的引用数量。当逐渐减少到0的时候释放又有的资源并且调用{@link #newSource}
   * 返回null
   * Reference count of the number of active sources reading this stream. When decremented to 0
   * resources are released and all following calls to {@link #newSource} return null. Guarded by
   * this.
   */
  int sourceCount;

  private Relay(RandomAccessFile file, Source upstream, long upstreamPos, ByteString metadata,
      long bufferMaxSize) {
    this.file = file;
    this.upstream = upstream;
    this.complete = upstream == null;
    this.upstreamPos = upstreamPos;
    this.metadata = metadata;
    this.bufferMaxSize = bufferMaxSize;
  }

  /**
   * 创建一个新的relay，这个relay从一个存活的{@code upstream}中读取数据，使用{@code file}
   * 来和其他的sources共享数据
   * Creates a new relay that reads a live stream from {@code upstream}, using {@code file} to share
   * that data with other sources.
   *
   * <p><strong>Warning:</strong> callers to this method must immediately call {@link #newSource} to
   * create a source and close that when they're done. Otherwise a handle to {@code file} will be
   * leaked.
   */
  public static Relay edit(File file, Source upstream, ByteString metadata, long bufferMaxSize) throws IOException {
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    Relay result = new Relay(randomAccessFile, upstream, 0L, metadata, bufferMaxSize);

    // Write a dirty header. That way if we crash we won't attempt to recover this.
    randomAccessFile.setLength(0L);
    result.writeHeader(PREFIX_DIRTY, -1L, -1L);

    return result;
  }

  /**
   * 创建一个relay来从{@code file}中读取记录
   * Creates a relay that reads a recorded stream from {@code file}.
   *
   * 警告：调用者调用了这个方法之后需要立即调用{@link #newSource}来创建一个source
   * 然后关闭他，当他已经使用完毕的时候。此外被操作的{@code file}将会被关闭
   * <p><strong>Warning:</strong> callers to this method must immediately call {@link #newSource} to
   * create a source and close that when they're done. Otherwise a handle to {@code file} will be
   * leaked.
   */
  public static Relay read(File file) throws IOException {
    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
    FileOperator fileOperator = new FileOperator(randomAccessFile.getChannel());

    // Read the header.
    Buffer header = new Buffer();
    fileOperator.read(0, header, FILE_HEADER_SIZE);
    ByteString prefix = header.readByteString(PREFIX_CLEAN.size());
    if (!prefix.equals(PREFIX_CLEAN)) throw new IOException("unreadable cache file");
    long upstreamSize = header.readLong();
    long metadataSize = header.readLong();

    // Read the metadata.
    Buffer metadataBuffer = new Buffer();
    fileOperator.read(FILE_HEADER_SIZE + upstreamSize, metadataBuffer, metadataSize);
    ByteString metadata = metadataBuffer.readByteString();

    // Return the result.
    return new Relay(randomAccessFile, null, upstreamSize, metadata, 0L);
  }

  private void writeHeader(
      ByteString prefix, long upstreamSize, long metadataSize) throws IOException {
    Buffer header = new Buffer();
    header.write(prefix);
    header.writeLong(upstreamSize);
    header.writeLong(metadataSize);
    if (header.size() != FILE_HEADER_SIZE) throw new IllegalArgumentException();

    FileOperator fileOperator = new FileOperator(file.getChannel());
    fileOperator.write(0, header, FILE_HEADER_SIZE);
  }

  private void writeMetadata(long upstreamSize) throws IOException {
    Buffer metadataBuffer = new Buffer();
    metadataBuffer.write(metadata);

    FileOperator fileOperator = new FileOperator(file.getChannel());
    fileOperator.write(FILE_HEADER_SIZE + upstreamSize, metadataBuffer, metadata.size());
  }

  void commit(long upstreamSize) throws IOException {
    // Write metadata to the end of the file.
    writeMetadata(upstreamSize);
    file.getChannel().force(false);

    // Once everything else is in place we can swap the dirty header for a clean one.
    writeHeader(PREFIX_CLEAN, upstreamSize, metadata.size());
    file.getChannel().force(false);

    // This file is complete.
    synchronized (Relay.this) {
      complete = true;
    }

    closeQuietly(upstream);
    upstream = null;
  }

  boolean isClosed() {
    return file == null;
  }

  public ByteString metadata() {
    return metadata;
  }

  /**
   * 返回一个新的source这里的source和upstream有着相同的byte数据。返回null如果relay已经被close 或者
   * 没有进一步的sources被提供。在这种情况下调用者需要在通过{@link #read}创建了一个relay之后进行重试。
   * Returns a new source that returns the same bytes as upstream. Returns null if this relay has
   * been closed and no further sources are possible. In that case callers should retry after
   * building a new relay with {@link #read}.
   */
  public Source newSource() {
    synchronized (Relay.this) {
      if (file == null) return null;
      sourceCount++;
    }

    return new RelaySource();
  }

  class RelaySource implements Source {
    private final Timeout timeout = new Timeout();

    /**
     * 这里的operator用来读写一个共享文件。如果source是关闭的话那么就返回null
     * The operator to read and write the shared file. Null if this source is closed. */
    private FileOperator fileOperator = new FileOperator(file.getChannel());

    /**
     * 下一个要读取的byte。这里总是少于等于{@code upstreamPos}
     * The next byte to read. This is always less than or equal to {@code upstreamPos}. */
    private long sourcePos;

    /**
     * 选择在哪里找到byte然后读取他，这是三个sources之一
     * Selects where to find the bytes for a read and read them. This is one of three sources.
     *
     * 在这种情况下当前的线程被分配给upstream 进行读取。我们从upstream中读取byte和拷贝他们
     * 进入到同一个file和缓冲池中。最后我们将释放这个upstream reader锁并且返回新的byte
     * <h3>Upstream:</h3>
     * In this case the current thread is assigned as the upstream reader. We read bytes from
     * upstream and copy them to both the file and to the buffer. Finally we release the upstream
     * reader lock and return the new bytes.
     *
     * 在这种情况下我们从file中拷贝byte进入{@code sink}
     * <h3>The file</h3>
     * In this case we copy bytes from the file to the {@code sink}.
     *
     * 在这种情况下byte需要立即被拷贝进入{@code sink}，并且返回byte的数量
     * <h3>The buffer</h3>
     * In this case the bytes are immediately copied into {@code sink} and the number of bytes
     * copied is returned.
     *
     * 如果upstream被选择了但是其他的线程早就被用来读取upstream这个将被锁挂起，直至读取结束。
     * 这里可能会超出时间当等待的时候。
     * <p>If upstream would be selected but another thread is already reading upstream this will
     * block until that read completes. It is possible to time out while waiting for that.
     */
    @Override public long read(Buffer sink, long byteCount) throws IOException {
      if (fileOperator == null) throw new IllegalStateException("closed");

      long upstreamPos;
      int source;

      selectSource:
      synchronized (Relay.this) {
        // 我们需要的数据从upstream中来
        // We need new data from upstream.
        while (sourcePos == (upstreamPos = Relay.this.upstreamPos)) {
          // 没有更多的数据了，我们就返回
          // No more data upstream. We're done.
          if (complete) return -1L;

          // 其他的线程已经进行读取了，我们需要等待
          // Another thread is already reading. Wait for that.
          if (upstreamReader != null) {
            timeout.waitUntilNotified(Relay.this);
            continue;
          }

          // 我们将去读取
          // We will do the read.
          upstreamReader = Thread.currentThread();
          source = SOURCE_UPSTREAM;
          break selectSource;
        }

        long bufferPos = upstreamPos - buffer.size();

        // 读取byte来覆盖缓冲区，这里的读取是从file中来的
        // Bytes of the read precede the buffer. Read from the file.
        if (sourcePos < bufferPos) {
          source = SOURCE_FILE;
          break selectSource;
        }

        // 缓冲区中有我们需要的数据，立即从其中读取
        // The buffer has the data we need. Read from there and return immediately.
        long bytesToRead = Math.min(byteCount, upstreamPos - sourcePos);
        buffer.copyTo(sink, sourcePos - bufferPos, bytesToRead);
        sourcePos += bytesToRead;
        return bytesToRead;
      }

      // 从file中读取数据
      // Read from the file.
      if (source == SOURCE_FILE) {
        long bytesToRead = Math.min(byteCount, upstreamPos - sourcePos);
        fileOperator.read(FILE_HEADER_SIZE + sourcePos, sink, bytesToRead);
        sourcePos += bytesToRead;
        return bytesToRead;
      }

      // 从upstream中读取时间，这里总是读取到充满缓冲区：这里可能超过当前调用Source.read()的请求数据量
      // Read from upstream. This always reads a full buffer: that might be more than what the
      // current call to Source.read() has requested.
      try {
        long upstreamBytesRead = upstream.read(upstreamBuffer, bufferMaxSize);

        // 如果我们耗尽了upstream，我们将返回
        // If we've exhausted upstream, we're done.
        if (upstreamBytesRead == -1L) {
          commit(upstreamPos);
          return -1L;
        }

        // 更新source返回准备这个方法的返回值
        // Update this source and prepare this call's result.
        long bytesRead = Math.min(upstreamBytesRead, byteCount);
        upstreamBuffer.copyTo(sink, 0, bytesRead);
        sourcePos += bytesRead;

        // 将upstream中的byte加到file中
        // Append the upstream bytes to the file.
        fileOperator.write(
            FILE_HEADER_SIZE + upstreamPos, upstreamBuffer.clone(), upstreamBytesRead);

        synchronized (Relay.this) {
          // 将新的upstream byte加到buffer中。削减他的最大值
          // Append new upstream bytes into the buffer. Trim it to its max size.
          buffer.write(upstreamBuffer, upstreamBytesRead);
          if (buffer.size() > bufferMaxSize) {
            buffer.skip(buffer.size() - bufferMaxSize);
          }

          // 现在file和buffer已经有byte了，更改upstreamPos
          // Now that the file and buffer have bytes, adjust upstreamPos.
          Relay.this.upstreamPos += upstreamBytesRead;
        }

        return bytesRead;
      } finally {
        synchronized (Relay.this) {
          upstreamReader = null;
          Relay.this.notifyAll();
        }
      }
    }

    @Override public Timeout timeout() {
      return timeout;
    }

    @Override public void close() throws IOException {
      if (fileOperator == null) return; // Already closed.
      fileOperator = null;

      RandomAccessFile fileToClose = null;
      synchronized (Relay.this) {
        sourceCount--;
        if (sourceCount == 0) {
          fileToClose = file;
          file = null;
        }
      }

      if (fileToClose != null) {
        closeQuietly(fileToClose);
      }
    }
  }
}
