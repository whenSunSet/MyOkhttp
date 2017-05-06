package okhttp3.internal.http;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Sink;

/**
 * 编码HTTP请求和解码HTTP响应
 *
 * Encodes HTTP requests and decodes HTTP responses. */
public interface HttpCodec {
    /**
     * 当丢弃一个输入数据的流的时候使用这个 timeout参数 。由于这个参数是用于连接的重用，
     * 所以这个参数应该明显地短于建立一个新连接的时间
     * The timeout to use while discarding a stream of input data. Since this is used for connection
     * reuse, this timeout should be significantly less than the time it takes to establish a new
     * connection.
     */
    int DISCARD_STREAM_TIMEOUT_MILLIS = 100;

    /**
     * 返回一个输出的流，这个流是由请求的body构成的
     * Returns an output stream where the request body can be streamed. */
    Sink createRequestBody(Request request, long contentLength);

    /**
     * 这个应该更新 HTTP请求发送引擎的 sentRequestMillis 字段
     * This should update the HTTP engine's sentRequestMillis field. */
    void writeRequestHeaders(Request request) throws IOException;

    /**
     * 将请求刷入底层的socket
     * Flush the request to the underlying socket. */
    void flushRequest() throws IOException;

    /**
     * 将请求刷入底层的socket并且表示没有更多的字节将会被继续传输
     * Flush the request to the underlying socket and signal no more bytes will be transmitted. */
    void finishRequest() throws IOException;

    /**
     * 解析一个HTTP请求响应的header字节数组
     * Parses bytes of a response header from an HTTP transport.
     *
     * @param expectContinue true就返回null，如果这个是一个响应的中间态，这个中间态返回的响应码是100.
     *                       其他情况下这个方法绝不返回null
     * @param expectContinue true to return null if this is an intermediate response with a "100"
     *     response code. Otherwise this method never returns null.
     */
    Response.Builder readResponseHeaders(boolean expectContinue) throws IOException;

    /**
     * 返回一个从响应中提取出来的响应body的流
     * Returns a stream that reads the response body. */
    ResponseBody openResponseBody(Response response) throws IOException;

    /**
     * 终止这个流，这个资源持有的流需要被清理关闭，即时不是同步的。
     * 这个事件可能发送在连接了线程池之后。
     * Cancel this stream. Resources held by this stream will be cleaned up, though not synchronously.
     * That may happen later by the connection pool thread.
     */
    void cancel();
}

