package okhttp3.internal.http;

/**
 * Created by heshixiyang on 2017/5/7.
 */

public final class HttpMethod {
    public static boolean invalidatesCache(String method) {
        return method.equals("POST")
                || method.equals("PATCH")
                || method.equals("PUT")
                || method.equals("DELETE")
                || method.equals("MOVE");     // WebDAV
    }

    public static boolean requiresRequestBody(String method) {
        return method.equals("POST")
                || method.equals("PUT")
                || method.equals("PATCH")
                || method.equals("PROPPATCH") // WebDAV
                || method.equals("REPORT");   // CalDAV/CardDAV (defined in WebDAV Versioning)
    }

    public static boolean permitsRequestBody(String method) {
        return requiresRequestBody(method)
                || method.equals("OPTIONS")
                || method.equals("DELETE")    // Permitted as spec is ambiguous.
                || method.equals("PROPFIND")  // (WebDAV) without body: request <allprop/>
                || method.equals("MKCOL")     // (WebDAV) may contain a body, but behaviour is unspecified
                || method.equals("LOCK");     // (WebDAV) body: create lock, without body: refresh lock
    }

    public static boolean redirectsWithBody(String method) {
        return method.equals("PROPFIND");
        // 重定向应该维护request body
        // (WebDAV) redirects should also maintain the request body
    }

    public static boolean redirectsToGet(String method) {
        // 所有的requests除了PROPFIND 都需要重定向到一个GET请求
        // All requests but PROPFIND should redirect to a GET request.
        return !method.equals("PROPFIND");
    }

    private HttpMethod() {
    }
}
