package okhttp3;

/**
 * Created by Administrator on 2017/5/7 0007.
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import okhttp3.internal.Util;
import okio.Buffer;
import okio.BufferedSource;
import static okhttp3.internal.Util.UTF_8;
/**
 * 一个从原始服务器向客户端应用发送的一次性的原始字节流的response body。
 * 每一个response body被一个存活的连接着web服务器的连接所支持
 * 这将给客户端一些限制
 * A one-shot stream from the origin server to the client application with the raw bytes of the
 * response body. Each response body is supported by an active connection to the webserver. This
 * imposes both obligations and limits on the client application.
 *
 * 这个response body必须关闭
 * <h3>The response body must be closed.</h3>
 *
 * 每一个response body 都是基于有限的资源的例如socket（网络的response）或者一个打开的文件（response的缓存）
 * 忘记关闭这个response body 将会造成资源泄漏，以至于造成应用变慢或者crash
 * Each response body is backed by a limited resource like a socket (live network responses) or
 * an open file (for cached responses). Failing to close the response body will leak resources and
 * may ultimately cause the application to slow down or crash.
 *
 * 这个class和{@link Response}都实现了{@link Closeable}。关闭一个response简单来说就是关闭一个response body
 * 如果你调用了{@link Call#execute()}或者实现了{@link Callback#onResponse}那么你必须关闭这个body 通过下面的方法
 * <p>Both this class and {@link Response} implement {@link Closeable}. Closing a response simply
 * closes its response body. If you invoke {@link Call#execute()} or implement {@link
 * Callback#onResponse} you must close this body by calling any of the following methods:
 *
 * <ul>
 *   <li>Response.close()</li>
 *   <li>Response.body().close()</li>
 *   <li>Response.body().source().close()</li>
 *   <li>Response.body().charStream().close()</li>
 *   <li>Response.body().byteString().close()</li>
 *   <li>Response.body().bytes()</li>
 *   <li>Response.body().string()</li>
 * </ul>
 *
 * 对同一个response body多次调用{@code close()}的方法不是有益的
 * <p>There is no benefit to invoking multiple {@code close()} methods for the same response body.
 *
 * 对于同步调用，通过try catch来确保response body 被关闭是非常方便的。
 * <p>For synchronous calls, the easiest way to make sure a response body is closed is with a {@code
 * try} block. With this structure the compiler inserts an implicit {@code finally} clause that
 * calls {@code close()} for you.
 *
 * <pre>   {@code
 *
 *   Call call = client.newCall(request);
 *   try (Response response = call.execute()) {
 *     ... // Use the response.
 *   }
 * }</pre>
 *
 * 你也可以使用异步调用
 * You can use a similar block for asynchronous calls: <pre>   {@code
 *
 *   Call call = client.newCall(request);
 *   call.enqueue(new Callback() {
 *     public void onResponse(Call call, Response response) throws IOException {
 *       try (ResponseBody responseBody = response.body()) {
 *         ... // Use the response.
 *       }
 *     }
 *
 *     public void onFailure(Call call, IOException e) {
 *       ... // Handle the failure.
 *     }
 *   });
 * }</pre>
 *
 * 如果你在其他线程消费response body 那么上面这些例子不会工作。
 * 在这种情况下，消费了该response body 的线程将需要调用{@link #close}，当我们读取response body结束的时候
 * These examples will not work if you're consuming the response body on another thread. In such
 * cases the consuming thread must call {@link #close} when it has finished reading the response
 * body.
 *
 * 这里的response body 只能被消费一次
 * <h3>The response body can be consumed only once.</h3>
 *
 * 这个class可能会使用流读取特别大的responses。例如，读取的responses大于当前进程所分配的内存是可能
 * 这里的response甚至会大于本地存储系统的大小，在读取视频的时候。
 * <p>This class may be used to stream very large responses. For example, it is possible to use this
 * class to read a response that is larger than the entire memory allocated to the current process.
 * It can even stream a response larger than the total storage on the current device, which is a
 * common requirement for video streaming applications.
 *
 * 因为这个class不能缓冲所有的response进内存中，所以应用不能对response的byte进行重读。
 * 使用这个一次性读取所有response进入内存可以使用{@link #bytes()} 或者 {@link #string()}
 * 或者将response通过流放入另一个{@link #source()}、{@link #byteStream()}, 或 {@link #charStream()}.中
 * <p>Because this class does not buffer the full response in memory, the application may not
 * re-read the bytes of the response. Use this one shot to read the entire response into memory with
 * {@link #bytes()} or {@link #string()}. Or stream the response with either {@link #source()},
 * {@link #byteStream()}, or {@link #charStream()}.
 */
public abstract class ResponseBody implements Closeable {
    /**
     * 多个不同的调用 {@link #charStream()}将会返回相同的实例
     * Multiple calls to {@link #charStream()} must return the same instance. */
    private Reader reader;

    public abstract MediaType contentType();

    /**
     * Returns the number of bytes in that will returned by {@link #bytes}, or {@link #byteStream}, or
     * -1 if unknown.
     */
    public abstract long contentLength();

    public final InputStream byteStream() {
        return source().inputStream();
    }

    public abstract BufferedSource source();

    /**
     * 返回response作为一个byte数组
     * Returns the response as a byte array.
     *
     * 这个方法将所有的response body完全加载到内存中。如果response body非常大那么就会造成OOM
     * 相比于这个方法，使用流是一个更好的选择。
     * <p>This method loads entire response body into memory. If the response body is very large this
     * may trigger an {@link OutOfMemoryError}. Prefer to stream the response body if this is a
     * possibility for your response.
     */
    public final byte[] bytes() throws IOException {
        long contentLength = contentLength();
        if (contentLength > Integer.MAX_VALUE) {
            throw new IOException("Cannot buffer entire body for content length: " + contentLength);
        }

        BufferedSource source = source();
        byte[] bytes;
        try {
            bytes = source.readByteArray();
        } finally {
            Util.closeQuietly(source);
        }
        if (contentLength != -1 && contentLength != bytes.length) {
            throw new IOException("Content-Length ("
                    + contentLength
                    + ") and stream length ("
                    + bytes.length
                    + ") disagree");
        }
        return bytes;
    }

    /**
     * 返回response使用Content-Type header.中的字符集进行解码
     * 如果缺失字符集的话，那么将会使用UTF8来解码
     * Returns the response as a character stream decoded with the charset of the Content-Type header.
     * If that header is either absent or lacks a charset, this will attempt to decode the response
     * body in accordance to <a href="https://en.wikipedia.org/wiki/Byte_order_mark">its BOM</a> or
     * UTF-8.
     */
    public final Reader charStream() {
        Reader r = reader;
        return r != null ? r : (reader = new BomAwareReader(source(), charset()));
    }

    /**
     * 返回string的结果，使用的解码字符集是Content-Type header中的，
     * 如果缺失字符集的话，那么将会使用UTF8来解码
     * Returns the response as a string decoded with the charset of the Content-Type header. If that
     * header is either absent or lacks a charset, this will attempt to decode the response body in
     * accordance to <a href="https://en.wikipedia.org/wiki/Byte_order_mark">its BOM</a> or UTF-8.
     * Closes {@link ResponseBody} automatically.
     *
     * 这个方法将所有的response body完全加载到内存中。如果response body非常大那么就会造成OOM
     * 相比于这个方法，使用流是一个更好的选择。
     * <p>This method loads entire response body into memory. If the response body is very large this
     * may trigger an {@link OutOfMemoryError}. Prefer to stream the response body if this is a
     * possibility for your response.
     */
    public final String string() throws IOException {
        BufferedSource source = source();
        try {
            Charset charset = Util.bomAwareCharset(source, charset());
            return source.readString(charset);
        } finally {
            Util.closeQuietly(source);
        }
    }

    private Charset charset() {
        MediaType contentType = contentType();
        return contentType != null ? contentType.charset(UTF_8) : UTF_8;
    }

    @Override public void close() {
        Util.closeQuietly(source());
    }

    /**
     * 从{@code content}中返回一个新的response body，
     * 如果缺失字符集的话，那么将会使用UTF8来解码
     * Returns a new response body that transmits {@code content}. If {@code contentType} is non-null
     * and lacks a charset, this will use UTF-8.
     */
    public static ResponseBody create(MediaType contentType, String content) {
        Charset charset = UTF_8;
        if (contentType != null) {
            charset = contentType.charset();
            if (charset == null) {
                charset = UTF_8;
                contentType = MediaType.parse(contentType + "; charset=utf-8");
            }
        }
        Buffer buffer = new Buffer().writeString(content, charset);
        return create(contentType, buffer.size(), buffer);
    }

    /**
     * 从{@code content}中返回一个新的response body，
     * Returns a new response body that transmits {@code content}. */
    public static ResponseBody create(final MediaType contentType, byte[] content) {
        Buffer buffer = new Buffer().write(content);
        return create(contentType, content.length, buffer);
    }

    /**
     * 从{@code content}中返回一个新的response body，
     * Returns a new response body that transmits {@code content}. */
    public static ResponseBody create(
            final MediaType contentType, final long contentLength, final BufferedSource content) {
        if (content == null) throw new NullPointerException("source == null");
        return new ResponseBody() {
            @Override public MediaType contentType() {
                return contentType;
            }

            @Override public long contentLength() {
                return contentLength;
            }

            @Override public BufferedSource source() {
                return content;
            }
        };
    }

    static final class BomAwareReader extends Reader {
        private final BufferedSource source;
        private final Charset charset;

        private boolean closed;
        private Reader delegate;

        BomAwareReader(BufferedSource source, Charset charset) {
            this.source = source;
            this.charset = charset;
        }

        @Override public int read(char[] cbuf, int off, int len) throws IOException {
            if (closed) throw new IOException("Stream closed");

            Reader delegate = this.delegate;
            if (delegate == null) {
                Charset charset = Util.bomAwareCharset(source, this.charset);
                delegate = this.delegate = new InputStreamReader(source.inputStream(), charset);
            }
            return delegate.read(cbuf, off, len);
        }

        @Override public void close() throws IOException {
            closed = true;
            if (delegate != null) {
                delegate.close();
            } else {
                source.close();
            }
        }
    }
}

