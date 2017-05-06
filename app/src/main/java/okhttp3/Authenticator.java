package okhttp3;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

/**
 * 响应一个 身份验证 这里不是一个远端的web server就是一个代理服务
 * 实现类可以试图满足验证通过返回一个包括了授权header，也可以拒绝验证以返回nul。
 * 在这种情况下,未经身份验证的响应将被返回给调用者来触发它
 * 实现类 应该检查是否在初始请求中早已经包含了一个身份验证，如果是这样，
 * 那么可能进一步尝试验证就不会有用，所以应该放弃这个验证。
 *
 * Responds to an authentication challenge from either a remote web server or a proxy server.
 * Implementations may either attempt to satisfy the challenge by returning a request that includes
 * an authorization header, or they may refuse the challenge by returning null. In this case the
 * unauthenticated response will be returned to the caller that triggered it.
 *
 * <p>Implementations should check if the initial request already included an attempt to
 * authenticate. If so it is likely that further attempts will not be useful and the authenticator
 * should give up.
 *
 * 当一个身份验证请求发出给原服务器，而且返回的响应码是401，那么应该重新发出一个请求，这个请求被设置了"Authorization" header.
 * <p>When authentication is requested by an origin server, the response code is 401 and the
 * implementation should respond with a new request that sets the "Authorization" header.
 * <pre>   {@code
 *
 *    if (response.request().header("Authorization") != null) {
 *      我们需要放弃，因为我们的身份验证早就失败了
 *      return null; // Give up, we've already failed to authenticate.
 *    }
 *
 *    String credential = Credentials.basic(...)
 *    return response.request().newBuilder()
 *        .header("Authorization", credential)
 *        .build();
 * }</pre>
 *
 * 如果身份验证请求发出给代理服务器，并且响应码是407，那么应该重新发出一个请求，这个请求被设置了"Proxy-Authorization" header.
 * <p>When authentication is requested by a proxy server, the response code is 407 and the
 * implementation should respond with a new request that sets the "Proxy-Authorization" header.
 * <pre>   {@code
 *
 *    if (response.request().header("Proxy-Authorization") != null) {
 *      return null; // Give up, we've already failed to authenticate.
 *    }
 *
 *    String credential = Credentials.basic(...)
 *    return response.request().newBuilder()
 *        .header("Proxy-Authorization", credential)
 *        .build();
 * }</pre>
 *
 * 应用可以在OkHttp中配置一个请求到底是发送给原服务器验证身份还是发送给代理服务器验证身份，或者都发。
 * <p>Applications may configure OkHttp with an authenticator for origin servers, or proxy servers,
 * or both.
 */
public interface Authenticator {
    /**
     * 一个不需要验证的Authenticator
     * An authenticator that knows no credentials and makes no attempt to authenticate. */
    Authenticator NONE = new Authenticator() {
        @Override public Request authenticate(Route route, Response response) {
            return null;
        }
    };

    /**
     * 返回一个包含了credential的安全的身份验证请求，这里的验证从上一个{@code response｝中来。
     * 返回null说明身份验证失败。
     * Returns a request that includes a credential to satisfy an authentication challenge in {@code
     * response}. Returns null if the challenge cannot be satisfied.
     */
    Request authenticate(Route route, Response response) throws IOException;
}
