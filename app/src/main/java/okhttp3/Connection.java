package okhttp3;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.net.Socket;

/**
 * 一个HTTP, HTTPS, 或 HTTPS+HTTP/2 连接的sockets 和 streams类。这里将会使用复数个HTTP请求/响应交换
 * 这里的连接可能是和原服务器，也可能是和代理
 * The sockets and streams of an HTTP, HTTPS, or HTTPS+HTTP/2 connection. May be used for multiple
 * HTTP request/response exchanges. Connections may be direct to the origin server or via a proxy.
 *
 * 通常会创建一个这个类的实例，HTTP客户端会自动连接和执行。应用也许可以使用这个类来监听处于连接池中的HTTP连接，
 *
 * <p>Typically instances of this class are created, connected and exercised automatically by the
 * HTTP client. Applications may use this class to monitor HTTP connections as members of a
 * {@linkplain ConnectionPool connection pool}.
 *
 * 不要将本类和{@code HttpURLConnection}混淆，{@code HttpURLConnection}只是单个连接的交换。
 * <p>Do not confuse this class with the misnamed {@code HttpURLConnection}, which isn't so much a
 * connection as a single request/response exchange.
 *
 * <h3>Modern TLS</h3>
 *
 * <p>There are tradeoffs when selecting which options to include when negotiating a secure
 * connection to a remote host. Newer TLS options are quite useful:
 *
 * <ul>
 *     <li>Server Name Indication (SNI) enables one IP address to negotiate secure connections for
 *         multiple domain names.
 *     <li>Application Layer Protocol Negotiation (ALPN) enables the HTTPS port (443) to be used to
 *         negotiate HTTP/2.
 * </ul>
 * 不幸的是，比较老的HTTPS服务拒绝这样的连接，当这样的选项被提供的时候。
 * 与其彻底的避免这样的选项，这个class允许一个连接去尝试现代的选项，然后在重试的时候不包括尝试失败
 * <p>Unfortunately, older HTTPS servers refuse to connect when such options are presented.
 * Rather than avoiding these options entirely, this class allows a connection to be attempted with modern
 * options and then retried without them should the attempt fail.
 *
 * <h3>Connection Reuse</h3>
 * 每一个连接都可以携带不同数量的流，这个依赖于底层的协议被允许的数量。HTTP/1.x 连接可以携带1个或者0个流
 * HTTP/2连接可以携带任意数量的流，动态设置可以使用{@code SETTINGS_MAX_CONCURRENT_STREAMS}这个参数。
 * 一个连接目前携带0个流是一个空闲流。我们可以让这个连接保持存活，因为重新使用这个连接比重新创建一个连接快
 * <p>Each connection can carry a varying number streams, depending on the underlying protocol being
 * used. HTTP/1.x connections can carry either zero or one streams. HTTP/2 connections can carry any
 * number of streams, dynamically configured with {@code SETTINGS_MAX_CONCURRENT_STREAMS}. A
 * connection currently carrying zero streams is an idle stream. We keep it alive because reusing an
 * existing connection is typically faster than establishing a new one.
 *
 * 当一个逻辑调用请求需要多个流重定向或者身份验证。我们最好使用同一个物理连接给所有的流，使得流排成一个序列。
 * 这样的做法能带来潜在的性能上的好处。为了支持这样的特性，这个class将 内存分配从流中分离开来。一个内存分配被一个call创建
 * 被一个或者多个流所使用，用完之后释放资源。一个被分配内存的连接不会被其他调用所窃取，当一个重定向或者身份验证的call被执行的时候
 * <p>When a single logical call requires multiple streams due to redirects or authorization
 * challenges, we prefer to use the same physical connection for all streams in the sequence. There
 * are potential performance and behavior consequences to this preference. To support this feature,
 * this class separates <i>allocations</i> from <i>streams</i>. An allocation is created by a call,
 * used for one or more streams, and then released. An allocated connection won't be stolen by other
 * calls while a redirect or authorization challenge is being handled.
 *
 * 当当前最大的流数量的限制被减小的时候，一些内存分配将被取消，试图创建一个基于那些内存的新的流将会失败
 * <p>When the maximum concurrent streams limit is reduced, some allocations will be rescinded.
 * Attempting to create new streams on these allocations will fail.
 *
 * 注意：一个内存分配需要在流被关闭之前被释放。这个是试图使得内存记录对于调用者来说更加简便：
 * 尽可能快的将内存分配给释放了在终端流被发现之前。
 * <p>Note that an allocation may be released before its stream is completed. This is intended to
 * make bookkeeping easier for the caller: releasing the allocation as soon as the terminal stream
 * has been found. But only complete the stream once its data stream has been exhausted.
 */
public interface Connection {
    /**
     * 返回这个连接所使用到的路由
     * Returns the route used by this connection. */
    Route route();

    /**
     * 返回这个连接使用到的Socket。返回一个{@linkplain javax.net.ssl.SSLSocket SSL socket}
     * 如果这个协议使用的是HTTPS，如果这是一个HTTP/2的连接，那么这里的socket将被多个当前请求所共享
     * Returns the socket that this connection is using. Returns an {@linkplain
     * javax.net.ssl.SSLSocket SSL socket} if this connection is HTTPS. If this is an HTTP/2
     * connection the socket may be shared by multiple concurrent calls.
     */
    Socket socket();

    /**
     * 返回TLS握手用来创建一个新的HTTPS连接，或者如果是null的话那么连接就不是HTTPS
     * Returns the TLS handshake used to establish this connection, or null if the connection is not
     * HTTPS.
     */
    Handshake handshake();

    /**
     * 返回当前连接的协议，如果是{@link Protocol#HTTP_1_1}，那么没有协议被商谈。
     * Returns the protocol negotiated by this connection, or {@link Protocol#HTTP_1_1} if no protocol
     * has been negotiated. This method returns {@link Protocol#HTTP_1_1} even if the remote peer is
     * using {@link Protocol#HTTP_1_0}.
     */
    Protocol protocol();
}
