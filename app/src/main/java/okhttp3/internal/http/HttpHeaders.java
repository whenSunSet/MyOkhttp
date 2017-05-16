package okhttp3.internal.http;

/**
 * Created by heshixiyang on 2017/5/7.
 */
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Challenge;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static okhttp3.internal.Util.equal;
import static okhttp3.internal.http.StatusLine.HTTP_CONTINUE;

/**
 * 在OkHttp中使用的Header工具
 * Headers and utilities for internal use by OkHttp. */
public final class HttpHeaders {
    private static final String TOKEN = "([^ \"=]*)";
    private static final String QUOTED_STRING = "\"([^\"]*)\"";
    private static final Pattern PARAMETER
            = Pattern.compile(" +" + TOKEN + "=(:?" + QUOTED_STRING + "|" + TOKEN + ") *(:?,|$)");

    private HttpHeaders() {
    }

    public static long contentLength(Response response) {
        return contentLength(response.headers());
    }

    public static long contentLength(Headers headers) {
        return stringToLong(headers.get("Content-Length"));
    }

    private static long stringToLong(String s) {
        if (s == null) return -1;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 返回true如果header在{@code cachedRequest} 和 {@code newRequest}.之间变化了
     * Returns true if none of the Vary headers have changed between {@code cachedRequest} and {@code
     * newRequest}.
     */
    public static boolean varyMatches(
            Response cachedResponse, Headers cachedRequest, Request newRequest) {
        for (String field : varyFields(cachedResponse)) {
            if (!equal(cachedRequest.values(field), newRequest.headers(field))) return false;
        }
        return true;
    }

    /**
     * 返回true如果header 包含了asterisk。这样的responses不能被缓存
     * Returns true if a Vary header contains an asterisk. Such responses cannot be cached.
     */
    public static boolean hasVaryAll(Response response) {
        return hasVaryAll(response.headers());
    }

    /**
     * 返回true如果header 包含了asterisk。这样的responses不能被缓存
     * Returns true if a Vary header contains an asterisk. Such responses cannot be cached.
     */
    public static boolean hasVaryAll(Headers responseHeaders) {
        return varyFields(responseHeaders).contains("*");
    }

    private static Set<String> varyFields(Response response) {
        return varyFields(response.headers());
    }

    /**
     * 返回request headers 的name，这里需要检查相等性和缓存
     * Returns the names of the request headers that need to be checked for equality when caching.
     */
    public static Set<String> varyFields(Headers responseHeaders) {
        Set<String> result = Collections.emptySet();
        for (int i = 0, size = responseHeaders.size(); i < size; i++) {
            if (!"Vary".equalsIgnoreCase(responseHeaders.name(i))) continue;

            String value = responseHeaders.value(i);
            if (result.isEmpty()) {
                result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            }
            for (String varyField : value.split(",")) {
                result.add(varyField.trim());
            }
        }
        return result;
    }

    /**
     * 返回{@code response}的request中的header的子集，这样将影响response's body的内容
     * Returns the subset of the headers in {@code response}'s request that impact the content of
     * response's body.
     */
    public static Headers varyHeaders(Response response) {
        //将request header发送至网络，如果他的response是变化的。
        //否则OkHttp 所支持的像"Accept-Encoding: gzip"这样的功能会失效
        // Use the request headers sent over the network, since that's what the
        // response varies on. Otherwise OkHttp-supplied headers like
        // "Accept-Encoding: gzip" may be lost.
        Headers requestHeaders = response.networkResponse().request().headers();
        Headers responseHeaders = response.headers();
        return varyHeaders(requestHeaders, responseHeaders);
    }

    /**
     * 返回{@code requestHeaders}中header的子集，这样将影响response's body的内容
     * Returns the subset of the headers in {@code requestHeaders} that impact the content of
     * response's body.
     */
    public static Headers varyHeaders(Headers requestHeaders, Headers responseHeaders) {
        Set<String> varyFields = varyFields(responseHeaders);
        if (varyFields.isEmpty()) return new Headers.Builder().build();

        Headers.Builder result = new Headers.Builder();
        for (int i = 0, size = requestHeaders.size(); i < size; i++) {
            String fieldName = requestHeaders.name(i);
            if (varyFields.contains(fieldName)) {
                result.add(fieldName, requestHeaders.value(i));
            }
        }
        return result.build();
    }

    /**
     * 解析RFC 2617 的challenges，也是错误的指令
     * 这个API只对scheme name 和 realm感兴趣
     * Parse RFC 2617 challenges, also wrong ordered ones.
     * This API is only interested in the scheme name and realm.
     */
    public static List<Challenge> parseChallenges(Headers responseHeaders, String challengeHeader) {
        // auth-scheme = token
        // auth-param  = token "=" ( token | quoted-string )
        // challenge   = auth-scheme 1*SP 1#auth-param
        // realm       = "realm" "=" realm-value
        // realm-value = quoted-string
        List<Challenge> challenges = new ArrayList<>();
        List<String> authenticationHeaders = responseHeaders.values(challengeHeader);
        for (String header : authenticationHeaders) {
            int index = header.indexOf(' ');
            if (index == -1) continue;

            Matcher matcher = PARAMETER.matcher(header);
            for (int i = index; matcher.find(i); i = matcher.end()) {
                if (header.regionMatches(true, matcher.start(1), "realm", 0, 5)) {
                    String scheme = header.substring(0, index);
                    String realm = matcher.group(3);
                    if (realm != null) {
                        challenges.add(new Challenge(scheme, realm));
                        break;
                    }
                }
            }
        }
        return challenges;
    }

    public static void receiveHeaders(CookieJar cookieJar, HttpUrl url, Headers headers) {
        if (cookieJar == CookieJar.NO_COOKIES) return;

        List<Cookie> cookies = Cookie.parseAll(url, headers);
        if (cookies.isEmpty()) return;

        cookieJar.saveFromResponse(url, cookies);
    }

    /**
     * 返回true如果这里的response 会有一个(possibly 0-length) body。可以查看RFC 7231
     * Returns true if the response must have a (possibly 0-length) body. See RFC 7231. */
    public static boolean hasBody(Response response) {
        // HEAD请求绝对不会产生body，无论这里的response是怎么样的
        // HEAD requests never yield a body regardless of the response headers.
        if (response.request().method().equals("HEAD")) {
            return false;
        }

        int responseCode = response.code();
        if ((responseCode < HTTP_CONTINUE || responseCode >= 200)
                && responseCode != HTTP_NO_CONTENT
                && responseCode != HTTP_NOT_MODIFIED) {
            return true;
        }

        // 如果这里的Content-Length 或者Transfer-Encoding header不同意返回的 response 状态码
        // 这里的response是不符合标准的。为了最好的兼容性，我们支持这个headers
        // If the Content-Length or Transfer-Encoding headers disagree with the response code, the
        // response is malformed. For best compatibility, we honor the headers.
        if (contentLength(response) != -1
                || "chunked".equalsIgnoreCase(response.header("Transfer-Encoding"))) {
            return true;
        }

        return false;
    }

    /**
     * 返回{@code input}中包含了{@code characters}中包含的character的第{@code pos}后面一个序号
     * 返回input 的长度，如果请求的characters没有被找到
     * Returns the next index in {@code input} at or after {@code pos} that contains a character from
     * {@code characters}. Returns the input length if none of the requested characters can be found.
     */
    public static int skipUntil(String input, int pos, String characters) {
        for (; pos < input.length(); pos++) {
            if (characters.indexOf(input.charAt(pos)) != -1) {
                break;
            }
        }
        return pos;
    }

    /**
     * 从{@code input}中返回下一个非空格的character 。
     * 结果是非定义的如果这里的input包含新行的characters
     * Returns the next non-whitespace character in {@code input} that is white space. Result is
     * undefined if input contains newline characters.
     */
    public static int skipWhitespace(String input, int pos) {
        for (; pos < input.length(); pos++) {
            char c = input.charAt(pos);
            if (c != ' ' && c != '\t') {
                break;
            }
        }
        return pos;
    }

    /**
     * 返回一个正的{@code value}，如果是负数就返回0，或者返回{@code defaultValue}如果不能解析
     * Returns {@code value} as a positive integer, or 0 if it is negative, or {@code defaultValue} if
     * it cannot be parsed.
     */
    public static int parseSeconds(String value, int defaultValue) {
        try {
            long seconds = Long.parseLong(value);
            if (seconds > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            } else if (seconds < 0) {
                return 0;
            } else {
                return (int) seconds;
            }
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

