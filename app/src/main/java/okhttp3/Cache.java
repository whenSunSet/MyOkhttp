/*
 * Copyright (C) 2010 The Android Open Source Project
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
package okhttp3;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import okhttp3.internal.Util;
import okhttp3.internal.cache.CacheRequest;
import okhttp3.internal.cache.CacheStrategy;
import okhttp3.internal.cache.DiskLruCache;
import okhttp3.internal.cache.InternalCache;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http.HttpMethod;
import okhttp3.internal.http.StatusLine;
import okhttp3.internal.io.FileSystem;
import okhttp3.internal.platform.Platform;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ByteString;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * 缓存HTTP和HTTPS的response到文件系统中，所以他们可以被重用以节省时间和宽带
 * Caches HTTP and HTTPS responses to the filesystem so they may be reused, saving time and
 * bandwidth.
 *
 * 缓存优化
 * <h3>Cache Optimization</h3>
 *
 * 对缓存的有效性进行评估，这个class跟踪三个数据
 * <p>To measure cache effectiveness, this class tracks three statistics:
 * <ul>
 *     {@linkplain #requestCount() Request Count:}：从这个缓存被创建以来，有多少个request被进行了
 *     <li><strong>{@linkplain #requestCount() Request Count:}</strong> the number of HTTP
 *         requests issued since this cache was created.
 *     {@linkplain #networkCount() Network Count:}：需要使用网络的request的数量
 *     <li><strong>{@linkplain #networkCount() Network Count:}</strong> the number of those
 *         requests that required network use.
 *      {@linkplain #hitCount() Hit Count:}：有多少缓存使用到了缓存
 *     <li><strong>{@linkplain #hitCount() Hit Count:}</strong> the number of those requests
 *         whose responses were served by the cache.
 * </ul>
 * 有些时候一个request将会导致一个条件缓存的命中。如果这个缓存包含了一个陈旧的response的拷贝。
 * 那么客户端将会提交一个有条件的{@code GET}请求。服务器将会发送一个更新的response，如果这个response已经改变了
 * 或者返回一个简短的'not modified' response，如果客户端的拷贝依然有效。这时候network count和hit count都会被加一
 * Sometimes a request will result in a conditional cache hit. If the cache contains a stale copy of
 * the response, the client will issue a conditional {@code GET}. The server will then send either
 * the updated response if it has changed, or a short 'not modified' response if the client's copy
 * is still valid. Such responses increment both the network count and hit count.
 *
 * 最好的提升缓存被命中的方法是通过配置 web server以返回一个可以缓存的response。
 * <p>The best way to improve the cache hit rate is by configuring the web server to return
 * cacheable responses. Although this client honors all <a
 * href="http://tools.ietf.org/html/rfc7234">HTTP/1.1 (RFC 7234)</a> cache headers, it doesn't cache
 * partial responses.
 *
 * 推动一个网络response
 * <h3>Force a Network Response</h3>
 * 在有些情况下，例如用户点击了一个刷新的button。这时候跳过缓存可能是必须的，此时就需要从服务器获取数据了
 * 为了推动一个完整的刷新，没有缓存的代码就像下面一样
 * <p>In some situations, such as after a user clicks a 'refresh' button, it may be necessary to
 * skip the cache, and fetch data directly from the server. To force a full refresh, add the {@code
 * no-cache} directive: <pre>   {@code
 *
 *   Request request = new Request.Builder()
 *       .cacheControl(new CacheControl.Builder().noCache().build())
 *       .url("http://publicobject.com/helloworld.txt")
 *       .build();
 * }</pre>
 *
 * 如果只需要获取一个缓存的response，这个缓存只要在服务器允许的时间段下就不需要刷新的话，
 * 可以使用下面的代码
 * If it is only necessary to force a cached response to be validated by the server, use the more
 * efficient {@code max-age=0} directive instead: <pre>   {@code
 *
 *   Request request = new Request.Builder()
 *       .cacheControl(new CacheControl.Builder()
 *           .maxAge(0, TimeUnit.SECONDS)
 *           .build())
 *       .url("http://publicobject.com/helloworld.txt")
 *       .build();
 * }</pre>
 *
 * 推动一个缓存response
 * <h3>Force a Cache Response</h3>
 *
 * 有时候你将会想展示资源，如果他们是立即生效的，那么就没有必要这么做。
 * 这个可以被使用所以你的应用可以展示一些当等待一些最后的数据被下载。这样就能限制一个request只使用本地的缓存资源
 * <p>Sometimes you'll want to show resources if they are available immediately, but not otherwise.
 * This can be used so your application can show <i>something</i> while waiting for the latest data
 * to be downloaded. To restrict a request to locally-cached resources, add the {@code
 * only-if-cached} directive: <pre>   {@code
 *
 *     Request request = new Request.Builder()
 *         .cacheControl(new CacheControl.Builder()
 *             .onlyIfCached()
 *             .build())
 *         .url("http://publicobject.com/helloworld.txt")
 *         .build();
 *     Response forceCacheResponse = client.newCall(request).execute();
 *     if (forceCacheResponse.code() != 504) {
 *       // The resource was cached! Show it.
 *     } else {
 *       // The resource was not cached.
 *     }
 * }</pre>
 *
 * 下面这个技术在陈旧的response比没有response的情况好的时候适用。
 * 这样可以允许陈旧的缓存response被使用。
 * This technique works even better in situations where a stale response is better than no response.
 * To permit stale cached responses, use the {@code max-stale} directive with the maximum staleness
 * in seconds: <pre>   {@code
 *
 *   Request request = new Request.Builder()
 *       .cacheControl(new CacheControl.Builder()
 *           .maxStale(365, TimeUnit.DAYS)
 *           .build())
 *       .url("http://publicobject.com/helloworld.txt")
 *       .build();
 * }</pre>
 *
 * {@link CacheControl}类可以配置request的缓存法则和解析response缓存的准则。
 * 他甚至提供了常量来方便使用者使用{@link CacheControl#FORCE_NETWORK}和{@link CacheControl#FORCE_CACHE}
 * <p>The {@link CacheControl} class can configure request caching directives and parse response
 * caching directives. It even offers convenient constants {@link CacheControl#FORCE_NETWORK} and
 * {@link CacheControl#FORCE_CACHE} that address the use cases above.
 */
public final class Cache implements Closeable, Flushable {
  private static final int VERSION = 201105;
  private static final int ENTRY_METADATA = 0;
  private static final int ENTRY_BODY = 1;
  private static final int ENTRY_COUNT = 2;

  final InternalCache internalCache = new InternalCache() {
    @Override public Response get(Request request) throws IOException {
      return Cache.this.get(request);
    }

    @Override public CacheRequest put(Response response) throws IOException {
      return Cache.this.put(response);
    }

    @Override public void remove(Request request) throws IOException {
      Cache.this.remove(request);
    }

    @Override public void update(Response cached, Response network) {
      Cache.this.update(cached, network);
    }

    @Override public void trackConditionalCacheHit() {
      Cache.this.trackConditionalCacheHit();
    }

    @Override public void trackResponse(CacheStrategy cacheStrategy) {
      Cache.this.trackResponse(cacheStrategy);
    }
  };

  final DiskLruCache cache;

  /* read and write statistics, all guarded by 'this' */
  int writeSuccessCount;
  int writeAbortCount;
  private int networkCount;
  private int hitCount;
  private int requestCount;

  public Cache(File directory, long maxSize) {
    this(directory, maxSize, FileSystem.SYSTEM);
  }

  Cache(File directory, long maxSize, FileSystem fileSystem) {
    this.cache = DiskLruCache.create(fileSystem, directory, VERSION, ENTRY_COUNT, maxSize);
  }

  public static String key(HttpUrl url) {
    return ByteString.encodeUtf8(url.toString()).md5().hex();
  }

  Response get(Request request) {
    String key = key(request.url());
    DiskLruCache.Snapshot snapshot;
    Entry entry;
    try {
      snapshot = cache.get(key);
      if (snapshot == null) {
        return null;
      }
    } catch (IOException e) {
      // Give up because the cache cannot be read.
      return null;
    }

    try {
      entry = new Entry(snapshot.getSource(ENTRY_METADATA));
    } catch (IOException e) {
      Util.closeQuietly(snapshot);
      return null;
    }

    Response response = entry.response(snapshot);

    if (!entry.matches(request, response)) {
      Util.closeQuietly(response.body());
      return null;
    }

    return response;
  }

  CacheRequest put(Response response) {
    String requestMethod = response.request().method();

    if (HttpMethod.invalidatesCache(response.request().method())) {
      try {
        remove(response.request());
      } catch (IOException ignored) {
        // The cache cannot be written.
      }
      return null;
    }
    if (!requestMethod.equals("GET")) {
      // Don't cache non-GET responses. We're technically allowed to cache
      // HEAD requests and some POST requests, but the complexity of doing
      // so is high and the benefit is low.
      return null;
    }

    if (HttpHeaders.hasVaryAll(response)) {
      return null;
    }

    Entry entry = new Entry(response);
    DiskLruCache.Editor editor = null;
    try {
      editor = cache.edit(key(response.request().url()));
      if (editor == null) {
        return null;
      }
      entry.writeTo(editor);
      return new CacheRequestImpl(editor);
    } catch (IOException e) {
      abortQuietly(editor);
      return null;
    }
  }

  void remove(Request request) throws IOException {
    cache.remove(key(request.url()));
  }

  void update(Response cached, Response network) {
    Entry entry = new Entry(network);
    DiskLruCache.Snapshot snapshot = ((CacheResponseBody) cached.body()).snapshot;
    DiskLruCache.Editor editor = null;
    try {
      editor = snapshot.edit(); // Returns null if snapshot is not current.
      if (editor != null) {
        entry.writeTo(editor);
        editor.commit();
      }
    } catch (IOException e) {
      abortQuietly(editor);
    }
  }

  private void abortQuietly(DiskLruCache.Editor editor) {
    // Give up because the cache cannot be written.
    try {
      if (editor != null) {
        editor.abort();
      }
    } catch (IOException ignored) {
    }
  }

  /**
   * 初始化缓存。这将包括从本地存储中读取日志文件和构建必要的内存中的缓存信息
   * Initialize the cache. This will include reading the journal files from the storage and building
   * up the necessary in-memory cache information.
   *
   * 这里初始化的时间应该是取决于日志文件的大小和当前真实缓存的大小。应用需要在其他线程调用这个初始化方法
   * <p>The initialization time may vary depending on the journal file size and the current actual
   * cache size. The application needs to be aware of calling this function during the
   * initialization phase and preferably in a background worker thread.
   *
   * <p>Note that if the application chooses to not call this method to initialize the cache. By
   * default, the okhttp will perform lazy initialization upon the first usage of the cache.
   */
  public void initialize() throws IOException {
    cache.initialize();
  }

  /**
   * 关闭缓存斌且删除所有的存储值。这将删除所有的缓存目录下的文件包括没有被使用于缓存的文件
   * Closes the cache and deletes all of its stored values. This will delete all files in the cache
   * directory including files that weren't created by the cache.
   */
  public void delete() throws IOException {
    cache.delete();
  }

  /**
   * 删除所有缓存。正在写入的缓存将会被完成，但是相应的response将不会被储存
   * Deletes all values stored in the cache. In-flight writes to the cache will complete normally,
   * but the corresponding responses will not be stored.
   */
  public void evictAll() throws IOException {
    cache.evictAll();
  }

  /**
   * 返回一个在缓存中覆盖了所有URL的迭代器，这个迭代器不会抛出{@code ConcurrentModificationException},
   * 但是如果新的response在迭代的期间被添加了。他们的URL将不会被返回。如果存在response在迭代的期间被清除的，那么他们将会缺少（除非早就被返回了）
   * Returns an iterator over the URLs in this cache. This iterator doesn't throw {@code
   * ConcurrentModificationException}, but if new responses are added while iterating, their URLs
   * will not be returned. If existing responses are evicted during iteration, they will be absent
   * (unless they were already returned).
   *
   * 这里的迭代器支持{@linkplain Iterator#remove}。
   * <p>The iterator supports {@linkplain Iterator#remove}. Removing a URL from the iterator evicts
   * the corresponding response from the cache. Use this to evict selected responses.
   */
  public Iterator<String> urls() throws IOException {
    return new Iterator<String>() {
      final Iterator<DiskLruCache.Snapshot> delegate = cache.snapshots();

      String nextUrl;
      boolean canRemove;

      @Override public boolean hasNext() {
        if (nextUrl != null) return true;

        canRemove = false; // Prevent delegate.remove() on the wrong item!
        while (delegate.hasNext()) {
          DiskLruCache.Snapshot snapshot = delegate.next();
          try {
            BufferedSource metadata = Okio.buffer(snapshot.getSource(ENTRY_METADATA));
            nextUrl = metadata.readUtf8LineStrict();
            return true;
          } catch (IOException ignored) {
            // We couldn't read the metadata for this snapshot; possibly because the host filesystem
            // has disappeared! Skip it.
          } finally {
            snapshot.close();
          }
        }

        return false;
      }

      @Override public String next() {
        if (!hasNext()) throw new NoSuchElementException();
        String result = nextUrl;
        nextUrl = null;
        canRemove = true;
        return result;
      }

      @Override public void remove() {
        if (!canRemove) throw new IllegalStateException("remove() before next()");
        delegate.remove();
      }
    };
  }

  public synchronized int writeAbortCount() {
    return writeAbortCount;
  }

  public synchronized int writeSuccessCount() {
    return writeSuccessCount;
  }

  public long size() throws IOException {
    return cache.size();
  }

  public long maxSize() {
    return cache.getMaxSize();
  }

  @Override public void flush() throws IOException {
    cache.flush();
  }

  @Override public void close() throws IOException {
    cache.close();
  }

  public File directory() {
    return cache.getDirectory();
  }

  public boolean isClosed() {
    return cache.isClosed();
  }

  synchronized void trackResponse(CacheStrategy cacheStrategy) {
    requestCount++;

    if (cacheStrategy.networkRequest != null) {
      // If this is a conditional request, we'll increment hitCount if/when it hits.
      networkCount++;
    } else if (cacheStrategy.cacheResponse != null) {
      // This response uses the cache and not the network. That's a cache hit.
      hitCount++;
    }
  }

  synchronized void trackConditionalCacheHit() {
    hitCount++;
  }

  public synchronized int networkCount() {
    return networkCount;
  }

  public synchronized int hitCount() {
    return hitCount;
  }

  public synchronized int requestCount() {
    return requestCount;
  }

  private final class CacheRequestImpl implements CacheRequest {
    private final DiskLruCache.Editor editor;
    private Sink cacheOut;
    private Sink body;
    boolean done;

    public CacheRequestImpl(final DiskLruCache.Editor editor) {
      this.editor = editor;
      this.cacheOut = editor.newSink(ENTRY_BODY);
      this.body = new ForwardingSink(cacheOut) {
        @Override public void close() throws IOException {
          synchronized (Cache.this) {
            if (done) {
              return;
            }
            done = true;
            writeSuccessCount++;
          }
          super.close();
          editor.commit();
        }
      };
    }

    @Override public void abort() {
      synchronized (Cache.this) {
        if (done) {
          return;
        }
        done = true;
        writeAbortCount++;
      }
      Util.closeQuietly(cacheOut);
      try {
        editor.abort();
      } catch (IOException ignored) {
      }
    }

    @Override public Sink body() {
      return body;
    }
  }

  static int readInt(BufferedSource source) throws IOException {
    try {
      long result = source.readDecimalLong();
      String line = source.readUtf8LineStrict();
      if (result < 0 || result > Integer.MAX_VALUE || !line.isEmpty()) {
        throw new IOException("expected an int but was \"" + result + line + "\"");
      }
      return (int) result;
    } catch (NumberFormatException e) {
      throw new IOException(e.getMessage());
    }
  }

  private static final class Entry {
    /**
     * 合成响应header：请求被发送时候的本地时间
     * Synthetic response header: the local time when the request was sent. */
    private static final String SENT_MILLIS = Platform.get().getPrefix() + "-Sent-Millis";

    /**
     * 合成响应header：response被接收到的时间
     * Synthetic response header: the local time when the response was received. */
    private static final String RECEIVED_MILLIS = Platform.get().getPrefix() + "-Received-Millis";

    private final String url;
    private final Headers varyHeaders;
    private final String requestMethod;
    private final Protocol protocol;
    private final int code;
    private final String message;
    private final Headers responseHeaders;
    private final Handshake handshake;
    private final long sentRequestMillis;
    private final long receivedResponseMillis;

    /**
     * 从input stream读取一个entry。一个经典的entry就回像下面这样：
     * Reads an entry from an input stream. A typical entry looks like this:
     * <pre>{@code
     *   http://google.com/foo
     *   GET
     *   2
     *   Accept-Language: fr-CA
     *   Accept-Charset: UTF-8
     *   HTTP/1.1 200 OK
     *   3
     *   Content-Type: image/png
     *   Content-Length: 100
     *   Cache-Control: max-age=600
     * }</pre>
     *
     * 已经经典的HTTPS文件将会像下面这样
     * <p>A typical HTTPS file looks like this:
     * <pre>{@code
     *   https://google.com/foo
     *   GET
     *   2
     *   Accept-Language: fr-CA
     *   Accept-Charset: UTF-8
     *   HTTP/1.1 200 OK
     *   3
     *   Content-Type: image/png
     *   Content-Length: 100
     *   Cache-Control: max-age=600
     *
     *   AES_256_WITH_MD5
     *   2
     *   base64-encoded peerCertificate[0]
     *   base64-encoded peerCertificate[1]
     *   -1
     *   TLSv1.2
     * }</pre>
     *
     * 这个文件是以行分割的。第一第二行就是URL和reuqest的方法。接下来是HTTP的request的header的行数
     * The file is newline separated. The first two lines are the URL and the request method. Next
     * is the number of HTTP Vary request header lines, followed by those lines.
     *
     * 接下来是response的状态码，接下来是HTTP response的header数量
     * <p>Next is the response status line, followed by the number of HTTP response header lines,
     * followed by those lines.
     *
     * HTTPS的response也会包含SSL的session信息。这将会空一行开始，并且一行将会包含
     * 密码组。接下来是证书链的长度。这个证书是由base64编码的并且一个一行。接下来一行是
     * 本地的证书也是base64编码，一个占有一行。长度为-1表示编码的字符集是空列表。最后一行是可选的。
     * 如果存在，那么表示TLS的版本
     * <p>HTTPS responses also contain SSL session information. This begins with a blank line, and
     * then a line containing the cipher suite. Next is the length of the peer certificate chain.
     * These certificates are base64-encoded and appear each on their own line. The next line
     * contains the length of the local certificate chain. These certificates are also
     * base64-encoded and appear each on their own line. A length of -1 is used to encode a null
     * array. The last line is optional. If present, it contains the TLS version.
     */
    public Entry(Source in) throws IOException {
      try {
        BufferedSource source = Okio.buffer(in);
        url = source.readUtf8LineStrict();
        requestMethod = source.readUtf8LineStrict();
        Headers.Builder varyHeadersBuilder = new Headers.Builder();
        int varyRequestHeaderLineCount = readInt(source);
        for (int i = 0; i < varyRequestHeaderLineCount; i++) {
          varyHeadersBuilder.addLenient(source.readUtf8LineStrict());
        }
        varyHeaders = varyHeadersBuilder.build();

        StatusLine statusLine = StatusLine.parse(source.readUtf8LineStrict());
        protocol = statusLine.protocol;
        code = statusLine.code;
        message = statusLine.message;
        Headers.Builder responseHeadersBuilder = new Headers.Builder();
        int responseHeaderLineCount = readInt(source);
        for (int i = 0; i < responseHeaderLineCount; i++) {
          responseHeadersBuilder.addLenient(source.readUtf8LineStrict());
        }
        String sendRequestMillisString = responseHeadersBuilder.get(SENT_MILLIS);
        String receivedResponseMillisString = responseHeadersBuilder.get(RECEIVED_MILLIS);
        responseHeadersBuilder.removeAll(SENT_MILLIS);
        responseHeadersBuilder.removeAll(RECEIVED_MILLIS);
        sentRequestMillis = sendRequestMillisString != null
            ? Long.parseLong(sendRequestMillisString)
            : 0L;
        receivedResponseMillis = receivedResponseMillisString != null
            ? Long.parseLong(receivedResponseMillisString)
            : 0L;
        responseHeaders = responseHeadersBuilder.build();

        if (isHttps()) {
          String blank = source.readUtf8LineStrict();
          if (blank.length() > 0) {
            throw new IOException("expected \"\" but was \"" + blank + "\"");
          }
          String cipherSuiteString = source.readUtf8LineStrict();
          CipherSuite cipherSuite = CipherSuite.forJavaName(cipherSuiteString);
          List<Certificate> peerCertificates = readCertificateList(source);
          List<Certificate> localCertificates = readCertificateList(source);
          TlsVersion tlsVersion = !source.exhausted()
              ? TlsVersion.forJavaName(source.readUtf8LineStrict())
              : null;
          handshake = Handshake.get(tlsVersion, cipherSuite, peerCertificates, localCertificates);
        } else {
          handshake = null;
        }
      } finally {
        in.close();
      }
    }

    public Entry(Response response) {
      this.url = response.request().url().toString();
      this.varyHeaders = HttpHeaders.varyHeaders(response);
      this.requestMethod = response.request().method();
      this.protocol = response.protocol();
      this.code = response.code();
      this.message = response.message();
      this.responseHeaders = response.headers();
      this.handshake = response.handshake();
      this.sentRequestMillis = response.sentRequestAtMillis();
      this.receivedResponseMillis = response.receivedResponseAtMillis();
    }

    public void writeTo(DiskLruCache.Editor editor) throws IOException {
      BufferedSink sink = Okio.buffer(editor.newSink(ENTRY_METADATA));

      sink.writeUtf8(url)
          .writeByte('\n');
      sink.writeUtf8(requestMethod)
          .writeByte('\n');
      sink.writeDecimalLong(varyHeaders.size())
          .writeByte('\n');
      for (int i = 0, size = varyHeaders.size(); i < size; i++) {
        sink.writeUtf8(varyHeaders.name(i))
            .writeUtf8(": ")
            .writeUtf8(varyHeaders.value(i))
            .writeByte('\n');
      }

      sink.writeUtf8(new StatusLine(protocol, code, message).toString())
          .writeByte('\n');
      sink.writeDecimalLong(responseHeaders.size() + 2)
          .writeByte('\n');
      for (int i = 0, size = responseHeaders.size(); i < size; i++) {
        sink.writeUtf8(responseHeaders.name(i))
            .writeUtf8(": ")
            .writeUtf8(responseHeaders.value(i))
            .writeByte('\n');
      }
      sink.writeUtf8(SENT_MILLIS)
          .writeUtf8(": ")
          .writeDecimalLong(sentRequestMillis)
          .writeByte('\n');
      sink.writeUtf8(RECEIVED_MILLIS)
          .writeUtf8(": ")
          .writeDecimalLong(receivedResponseMillis)
          .writeByte('\n');

      if (isHttps()) {
        sink.writeByte('\n');
        sink.writeUtf8(handshake.cipherSuite().javaName())
            .writeByte('\n');
        writeCertList(sink, handshake.peerCertificates());
        writeCertList(sink, handshake.localCertificates());
        // The handshake’s TLS version is null on HttpsURLConnection and on older cached responses.
        if (handshake.tlsVersion() != null) {
          sink.writeUtf8(handshake.tlsVersion().javaName())
              .writeByte('\n');
        }
      }
      sink.close();
    }

    private boolean isHttps() {
      return url.startsWith("https://");
    }

    private List<Certificate> readCertificateList(BufferedSource source) throws IOException {
      int length = readInt(source);
      if (length == -1) return Collections.emptyList(); // OkHttp v1.2 used -1 to indicate null.

      try {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        List<Certificate> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
          String line = source.readUtf8LineStrict();
          Buffer bytes = new Buffer();
          bytes.write(ByteString.decodeBase64(line));
          result.add(certificateFactory.generateCertificate(bytes.inputStream()));
        }
        return result;
      } catch (CertificateException e) {
        throw new IOException(e.getMessage());
      }
    }

    private void writeCertList(BufferedSink sink, List<Certificate> certificates)
        throws IOException {
      try {
        sink.writeDecimalLong(certificates.size())
            .writeByte('\n');
        for (int i = 0, size = certificates.size(); i < size; i++) {
          byte[] bytes = certificates.get(i).getEncoded();
          String line = ByteString.of(bytes).base64();
          sink.writeUtf8(line)
              .writeByte('\n');
        }
      } catch (CertificateEncodingException e) {
        throw new IOException(e.getMessage());
      }
    }

    public boolean matches(Request request, Response response) {
      return url.equals(request.url().toString())
          && requestMethod.equals(request.method())
          && HttpHeaders.varyMatches(response, varyHeaders, request);
    }

    public Response response(DiskLruCache.Snapshot snapshot) {
      String contentType = responseHeaders.get("Content-Type");
      String contentLength = responseHeaders.get("Content-Length");
      Request cacheRequest = new Request.Builder()
          .url(url)
          .method(requestMethod, null)
          .headers(varyHeaders)
          .build();
      return new Response.Builder()
          .request(cacheRequest)
          .protocol(protocol)
          .code(code)
          .message(message)
          .headers(responseHeaders)
          .body(new CacheResponseBody(snapshot, contentType, contentLength))
          .handshake(handshake)
          .sentRequestAtMillis(sentRequestMillis)
          .receivedResponseAtMillis(receivedResponseMillis)
          .build();
    }
  }

  private static class CacheResponseBody extends ResponseBody {
    final DiskLruCache.Snapshot snapshot;
    private final BufferedSource bodySource;
    private final String contentType;
    private final String contentLength;

    public CacheResponseBody(final DiskLruCache.Snapshot snapshot,
        String contentType, String contentLength) {
      this.snapshot = snapshot;
      this.contentType = contentType;
      this.contentLength = contentLength;

      Source source = snapshot.getSource(ENTRY_BODY);
      bodySource = Okio.buffer(new ForwardingSource(source) {
        @Override public void close() throws IOException {
          snapshot.close();
          super.close();
        }
      });
    }

    @Override public MediaType contentType() {
      return contentType != null ? MediaType.parse(contentType) : null;
    }

    @Override public long contentLength() {
      try {
        return contentLength != null ? Long.parseLong(contentLength) : -1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }

    @Override public BufferedSource source() {
      return bodySource;
    }
  }
}
