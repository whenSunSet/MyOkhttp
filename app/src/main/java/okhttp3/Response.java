package okhttp3;

/**
 * Created by Administrator on 2017/5/7 0007.
 */

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import okhttp3.internal.http.HttpHeaders;
import okio.Buffer;
import okio.BufferedSource;

import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static okhttp3.internal.http.StatusLine.HTTP_PERM_REDIRECT;
import static okhttp3.internal.http.StatusLine.HTTP_TEMP_REDIRECT;
/**
 * 一个HTTP的response，这个class的实例是不可变的：这里的response body 是一次性的，只能读取一次然后就会被关闭，其他的属性都是不可变的。
 * An HTTP response. Instances of this class are not immutable: the response body is a one-shot
 * value that may be consumed only once and then closed. All other properties are immutable.
 *
 * 这个class实现了{@link Closeable}。关闭这个实例表示这关闭response body，可以看{@link ResponseBody}中的介绍与例子
 * <p>This class implements {@link Closeable}. Closing it simply closes its response body. See
 * {@link ResponseBody} for an explanation and examples.
 */
public final class Response implements Closeable {
    final Request request;
    final Protocol protocol;
    final int code;
    final String message;
    final Handshake handshake;
    final Headers headers;
    final ResponseBody body;
    final Response networkResponse;
    final Response cacheResponse;
    final Response priorResponse;
    final long sentRequestAtMillis;
    final long receivedResponseAtMillis;

    private volatile CacheControl cacheControl; // Lazily initialized.

    Response(Builder builder) {
        this.request = builder.request;
        this.protocol = builder.protocol;
        this.code = builder.code;
        this.message = builder.message;
        this.handshake = builder.handshake;
        this.headers = builder.headers.build();
        this.body = builder.body;
        this.networkResponse = builder.networkResponse;
        this.cacheResponse = builder.cacheResponse;
        this.priorResponse = builder.priorResponse;
        this.sentRequestAtMillis = builder.sentRequestAtMillis;
        this.receivedResponseAtMillis = builder.receivedResponseAtMillis;
    }

    /**
     * 这个response的发起请求。这里不需要是应用发布的同样的请求，额就是说这里的请求可能和原始的请求不同
     * The wire-level request that initiated this HTTP response. This is not necessarily the same
     * request issued by the application:
     *
     * <ul>
     *     这里可能改变了HTTP客户端：例如客户端可能从request body的header中拷贝了{@code Content-Length}
     *     <li>It may be transformed by the HTTP client. For example, the client may copy headers like
     *         {@code Content-Length} from the request body.
     *         可能产生的请求响应HTTP重定向或进行了身份验证。在这种情况下,请求URL可能不同于初始请求的URL。
     *     <li>It may be the request generated in response to an HTTP redirect or authentication
     *         challenge. In this case the request URL may be different than the initial request URL.
     * </ul>
     */
    public Request request() {
        return request;
    }

    /**
     * 返回HTTP协议
     * Returns the HTTP protocol, such as {@link Protocol#HTTP_1_1} or {@link Protocol#HTTP_1_0}.
     */
    public Protocol protocol() {
        return protocol;
    }

    /**
     * 返回http状态码
     * Returns the HTTP status code. */
    public int code() {
        return code;
    }

    /**
     * 返回的code如果在[200..300), 那么就表示请求成功了
     * Returns true if the code is in [200..300), which means the request was successfully received,
     * understood, and accepted.
     */
    public boolean isSuccessful() {
        return code >= 200 && code < 300;
    }

    /**
     * 返回HTTP状态信息，如果null表示未知
     * Returns the HTTP status message or null if it is unknown. */
    public String message() {
        return message;
    }

    /**
     * 返回TLS的握手，如果null表示response没收到TLS
     * Returns the TLS handshake of the connection that carried this response, or null if the response
     * was received without TLS.
     */
    public Handshake handshake() {
        return handshake;
    }

    public List<String> headers(String name) {
        return headers.values(name);
    }

    public String header(String name) {
        return header(name, null);
    }

    public String header(String name, String defaultValue) {
        String result = headers.get(name);
        return result != null ? result : defaultValue;
    }

    public Headers headers() {
        return headers;
    }

    /**
     * 从一个查看response body 中的{@code byteCount}数量的byte然后返回一个新的response body
     * 如果response body中的byte很少，那么就返回全部的response body 。如果这里的byte数超过了{@code byteCount}
     * 的数量，那么只会返回截取了{@code byteCount}数量的byte的response body
     * Peeks up to {@code byteCount} bytes from the response body and returns them as a new response
     * body. If fewer than {@code byteCount} bytes are in the response body, the full response body is
     * returned. If more than {@code byteCount} bytes are in the response body, the returned value
     * will be truncated to {@code byteCount} bytes.
     *
     * 如果body已经关闭了，那么就会造成一个error
     * <p>It is an error to call this method after the body has been consumed.
     *
     * 警告：这个方法将response body加载进了内存中，但是绝大部分应用需要给{@code byteCount}进行一个限制，比如说1MB
     * <p><strong>Warning:</strong> this method loads the requested bytes into memory. Most
     * applications should set a modest limit on {@code byteCount}, such as 1 MiB.
     */
    public ResponseBody peekBody(long byteCount) throws IOException {
        BufferedSource source = body.source();
        source.request(byteCount);
        Buffer copy = source.buffer().clone();

        // There may be more than byteCount bytes in source.buffer(). If there is, return a prefix.
        Buffer result;
        if (copy.size() > byteCount) {
            result = new Buffer();
            result.write(copy, byteCount);
            copy.clear();
        } else {
            result = copy;
        }

        return ResponseBody.create(body.contentType(), result.size(), result);
    }

    /**
     * 返回一个非null 的value，如果这个response是通过{@link Callback#onResponse}或者从{@link Call#execute()}中返回
     * Response bodies 需要被{@linkplain ResponseBody closed}和被仅仅消耗一次
     * Returns a non-null value if this response was passed to {@link Callback#onResponse} or returned
     * from {@link Call#execute()}. Response bodies must be {@linkplain ResponseBody closed} and may
     * be consumed only once.
     * 从{@link #cacheResponse}, {@link #networkResponse}, and {@link #priorResponse()}.获取response的时候
     * 这里返回null
     * <p>This always returns null on responses returned from {@link #cacheResponse}, {@link
     * #networkResponse}, and {@link #priorResponse()}.
     */
    public ResponseBody body() {
        return body;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }

    /**
     * 返回true,如果这种response重定向到另一个资源
     * Returns true if this response redirects to another resource. */
    public boolean isRedirect() {
        switch (code) {
            case HTTP_PERM_REDIRECT:
            case HTTP_TEMP_REDIRECT:
            case HTTP_MULT_CHOICE:
            case HTTP_MOVED_PERM:
            case HTTP_MOVED_TEMP:
            case HTTP_SEE_OTHER:
                return true;
            default:
                return false;
        }
    }

    /**
     * 返回原始的从网络上获取的response。这里将会返回null，如果这里的response不是从网络上获取的
     * 例如这个response已经完全被缓存了，此时response中的body不应该被读取
     * Returns the raw response received from the network. Will be null if this response didn't use
     * the network, such as when the response is fully cached. The body of the returned response
     * should not be read.
     */
    public Response networkResponse() {
        return networkResponse;
    }

    /**
     * 返回原始的从缓存中接收到的response。这里可能会为null，如果这里的response不是从缓存中获取的
     * 对于有条件的GET请求这里的缓存和网络response可能都是非null的。
     * 这里返回的Response的body不能被再次读取
     * Returns the raw response received from the cache. Will be null if this response didn't use the
     * cache. For conditional get requests the cache response and network response may both be
     * non-null. The body of the returned response should not be read.
     */
    public Response cacheResponse() {
        return cacheResponse;
    }

    /**
     * 返回被重定向或者被进行了身份验证的的response。
     * Returns the response for the HTTP redirect or authorization challenge that triggered this
     * response, or null if this response wasn't triggered by an automatic retry. The body of the
     * returned response should not be read because it has already been consumed by the redirecting
     * client.
     */
    public Response priorResponse() {
        return priorResponse;
    }

    /**
     * 根据response的状态码，返回身份验证信息。如果状态码是401表示验证失败，返回"WWW-Authenticate"
     * 如果状态码是407表示 代理服务器验证失败 返回"Proxy-Authenticate" 。其他情况返回一个空的列表
     * Returns the authorization challenges appropriate for this response's code. If the response code
     * is 401 unauthorized, this returns the "WWW-Authenticate" challenges. If the response code is
     * 407 proxy unauthorized, this returns the "Proxy-Authenticate" challenges. Otherwise this
     * returns an empty list of challenges.
     */
    public List<Challenge> challenges() {
        String responseField;
        if (code == HTTP_UNAUTHORIZED) {
            responseField = "WWW-Authenticate";
        } else if (code == HTTP_PROXY_AUTH) {
            responseField = "Proxy-Authenticate";
        } else {
            return Collections.emptyList();
        }
        return HttpHeaders.parseChallenges(headers(), responseField);
    }

    /**
     * 返回此response的缓存控制指令，这个绝不可能是null，甚至这个response包含不缓存的header
     * Returns the cache control directives for this response. This is never null, even if this
     * response contains no {@code Cache-Control} header.
     */
    public CacheControl cacheControl() {
        CacheControl result = cacheControl;
        return result != null ? result : (cacheControl = CacheControl.parse(headers));
    }

    /**
     * 返回一个{@linkplain System#currentTimeMillis() timestamp}
     * Returns a {@linkplain System#currentTimeMillis() timestamp} taken immediately before OkHttp
     * transmitted the initiating request over the network. If this response is being served from the
     * cache then this is the timestamp of the original request.
     */
    public long sentRequestAtMillis() {
        return sentRequestAtMillis;
    }

    /**
     * Returns a {@linkplain System#currentTimeMillis() timestamp} taken immediately after OkHttp
     * received this response's headers from the network. If this response is being served from the
     * cache then this is the timestamp of the original response.
     */
    public long receivedResponseAtMillis() {
        return receivedResponseAtMillis;
    }

    /** Closes the response body. Equivalent to {@code body().close()}. */
    @Override public void close() {
        body.close();
    }

    @Override public String toString() {
        return "Response{protocol="
                + protocol
                + ", code="
                + code
                + ", message="
                + message
                + ", url="
                + request.url()
                + '}';
    }

    public static class Builder {
        Request request;
        Protocol protocol;
        int code = -1;
        String message;
        Handshake handshake;
        Headers.Builder headers;
        ResponseBody body;
        Response networkResponse;
        Response cacheResponse;
        Response priorResponse;
        long sentRequestAtMillis;
        long receivedResponseAtMillis;

        public Builder() {
            headers = new Headers.Builder();
        }

        Builder(Response response) {
            this.request = response.request;
            this.protocol = response.protocol;
            this.code = response.code;
            this.message = response.message;
            this.handshake = response.handshake;
            this.headers = response.headers.newBuilder();
            this.body = response.body;
            this.networkResponse = response.networkResponse;
            this.cacheResponse = response.cacheResponse;
            this.priorResponse = response.priorResponse;
            this.sentRequestAtMillis = response.sentRequestAtMillis;
            this.receivedResponseAtMillis = response.receivedResponseAtMillis;
        }

        public Builder request(Request request) {
            this.request = request;
            return this;
        }

        public Builder protocol(Protocol protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder code(int code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder handshake(Handshake handshake) {
            this.handshake = handshake;
            return this;
        }

        /**
         * Sets the header named {@code name} to {@code value}. If this request already has any headers
         * with that name, they are all replaced.
         */
        public Builder header(String name, String value) {
            headers.set(name, value);
            return this;
        }

        /**
         * Adds a header with {@code name} and {@code value}. Prefer this method for multiply-valued
         * headers like "Set-Cookie".
         */
        public Builder addHeader(String name, String value) {
            headers.add(name, value);
            return this;
        }

        public Builder removeHeader(String name) {
            headers.removeAll(name);
            return this;
        }

        /** Removes all headers on this builder and adds {@code headers}. */
        public Builder headers(Headers headers) {
            this.headers = headers.newBuilder();
            return this;
        }

        public Builder body(ResponseBody body) {
            this.body = body;
            return this;
        }

        public Builder networkResponse(Response networkResponse) {
            if (networkResponse != null) checkSupportResponse("networkResponse", networkResponse);
            this.networkResponse = networkResponse;
            return this;
        }

        public Builder cacheResponse(Response cacheResponse) {
            if (cacheResponse != null) checkSupportResponse("cacheResponse", cacheResponse);
            this.cacheResponse = cacheResponse;
            return this;
        }

        private void checkSupportResponse(String name, Response response) {
            if (response.body != null) {
                throw new IllegalArgumentException(name + ".body != null");
            } else if (response.networkResponse != null) {
                throw new IllegalArgumentException(name + ".networkResponse != null");
            } else if (response.cacheResponse != null) {
                throw new IllegalArgumentException(name + ".cacheResponse != null");
            } else if (response.priorResponse != null) {
                throw new IllegalArgumentException(name + ".priorResponse != null");
            }
        }

        public Builder priorResponse(Response priorResponse) {
            if (priorResponse != null) checkPriorResponse(priorResponse);
            this.priorResponse = priorResponse;
            return this;
        }

        private void checkPriorResponse(Response response) {
            if (response.body != null) {
                throw new IllegalArgumentException("priorResponse.body != null");
            }
        }

        public Builder sentRequestAtMillis(long sentRequestAtMillis) {
            this.sentRequestAtMillis = sentRequestAtMillis;
            return this;
        }

        public Builder receivedResponseAtMillis(long receivedResponseAtMillis) {
            this.receivedResponseAtMillis = receivedResponseAtMillis;
            return this;
        }

        public Response build() {
            if (request == null) throw new IllegalStateException("request == null");
            if (protocol == null) throw new IllegalStateException("protocol == null");
            if (code < 0) throw new IllegalStateException("code < 0: " + code);
            return new Response(this);
        }
    }
}

