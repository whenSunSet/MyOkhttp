package okhttp3;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.util.Collections;
import java.util.List;

/**
 * 给HTTP的cookie提供 策略和持久化
 * Provides <strong>policy</strong> and <strong>persistence</strong> for HTTP cookies.
 * 作为一个策略，实现类有责任选择哪一个cookie可以通过哪一个cookie将会被拒绝。
 * 一个合理的策略是拒绝所有cookie，虽然这里会干扰到基于cookie和身份验证的session。
 * <p>As policy, implementations of this interface are responsible for selecting which cookies to
 * accept and which to reject. A reasonable policy is to reject all cookies, though that may
 * interfere with session-based authentication schemes that require cookies.
 *
 * 作为持久化，实现类需要提供cookie的存储。简单的实现是将cookie存在内存中，复杂一点可以让cookie存储在文件系统或者数据库中
 *
 * <p>As persistence, implementations of this interface must also provide storage of cookies. Simple
 * implementations may store cookies in memory; sophisticated ones may use the file system or
 * database to hold accepted cookies. The <a
 * href="https://tools.ietf.org/html/rfc6265#section-5.3">cookie storage model</a> specifies
 * policies for updating and expiring cookies.
 */
public interface CookieJar {
    /**
     * 一个不通过任何cookie的cookie jar
     * A cookie jar that never accepts any cookies. */
    CookieJar NO_COOKIES = new CookieJar() {
        @Override public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        }

        @Override public List<Cookie> loadForRequest(HttpUrl url) {
            return Collections.emptyList();
        }
    };

    /**
     * 将{@code cookies}从HTTP响应中保存下来根据jar的策略
     * Saves {@code cookies} from an HTTP response to this store according to this jar's policy.
     * 注意：这个方法可能被调用两次对单个的HTTP响应，如果这个响应包含了一个trailer。这是HTTP的模糊特性
     * {@code cookies}包含了trailer's的cookie
     * <p>Note that this method may be called a second time for a single HTTP response if the response
     * includes a trailer. For this obscure HTTP feature, {@code cookies} contains only the trailer's
     * cookies.
     */
    void saveFromResponse(HttpUrl url, List<Cookie> cookies);

    /**
     * Load cookies from the jar for an HTTP request to {@code url}. This method returns a possibly
     * empty list of cookies for the network request.
     *
     * <p>Simple implementations will return the accepted cookies that have not yet expired and that
     * {@linkplain Cookie#matches match} {@code url}.
     */
    List<Cookie> loadForRequest(HttpUrl url);
}

