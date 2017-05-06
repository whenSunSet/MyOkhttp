package okhttp3.internal.http2;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

import okhttp3.Protocol;
import okio.BufferedSource;

/**
 * 仅仅在{@link Protocol#HTTP_2 HTTP/2}使用，后台线程在客户端初始化了一个HTTP请求之后，
 * 这个的实现类。必须迅速的将请求分派给回调，这样可以避免性能瓶颈
 * {@link Protocol#HTTP_2 HTTP/2} only. Processes server-initiated HTTP requests on the client.
 * Implementations must quickly dispatch callbacks to avoid creating a bottleneck.
 *
 * {@link #onReset}将发送在任何时刻，接下来的回调将通过stream ID获取相关流
 * <p>While {@link #onReset} may occur at any time, the following callbacks are expected in order,
 * correlated by stream ID.
 *
 * <ul>
 *     <li>{@link #onRequest}</li> <li>{@link #onHeaders} (unless canceled)
 *     <li>{@link #onData} (optional sequence of data frames)
 * </ul>
 *
 * 一个stream ID 只单单作用在一个HTTP/2的连接上，如果实现类的目标是复数个链接，那么需要使用多个stream ID
 * <p>As a stream ID is scoped to a single HTTP/2 connection, implementations which target multiple
 * connections should expect repetition of stream IDs.
 *
 * 返回true去请求取消推流，注意这里不能保证未来的帧不会到达这个stream ID锁代表的流
 * <p>Return true to request cancellation of a pushed stream.  Note that this does not guarantee
 * future frames won't arrive on the stream ID.
 */
public interface PushObserver {
    /**
     * 这里描述了一个 服务器将要返回响应 的请求
     * Describes the request that the server intends to push a response for.
     *
     * @param streamId 服务器初始化的stream ID：一个数字
     * @param requestHeaders 最少包括了  {@code :method}, {@code :scheme}, {@code :authority},and {@code :path}.
     *
     * @param streamId server-initiated stream ID: an even number.
     * @param requestHeaders minimally includes {@code :method}, {@code :scheme}, {@code :authority},
     * and {@code :path}.
     */
    boolean onRequest(int streamId, List<Header> requestHeaders);

    /**
     * 对应请求的 响应headers，当{@code last}是true，将没有数据帧继续传递
     * The response headers corresponding to a pushed request.  When {@code last} is true, there are
     * no data frames to follow.
     *
     * @param streamId server-initiated stream ID: an even number.
     * @param responseHeaders minimally includes {@code :status}.
     * @param last when true, there is no response data.
     */
    boolean onHeaders(int streamId, List<Header> responseHeaders, boolean last);

    /**
     * 一个 请求的 响应数据块，这个数据块需要被读取或者跳过
     * A chunk of response data corresponding to a pushed request.  This data must either be read or
     * skipped.
     *
     * @param streamId server-initiated stream ID: an even number.
     * @param source location of data corresponding with this stream ID.
     * @param byteCount number of bytes to read or skip from the source.
     * @param last when true, there are no data frames to follow.
     */
    boolean onData(int streamId, BufferedSource source, int byteCount, boolean last)
            throws IOException;

    /**
     * 表明为什么这个流被取消了
     * Indicates the reason why this stream was canceled. */
    void onReset(int streamId, ErrorCode errorCode);

    PushObserver CANCEL = new PushObserver() {

        @Override public boolean onRequest(int streamId, List<Header> requestHeaders) {
            return true;
        }

        @Override public boolean onHeaders(int streamId, List<Header> responseHeaders, boolean last) {
            return true;
        }

        @Override public boolean onData(int streamId, BufferedSource source, int byteCount,
                                        boolean last) throws IOException {
            source.skip(byteCount);
            return true;
        }

        @Override public void onReset(int streamId, ErrorCode errorCode) {
        }
    };
}

