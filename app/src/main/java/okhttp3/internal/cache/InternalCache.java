package okhttp3.internal.cache;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp 的内部缓存接口，应用不需要实现这个接口，相反应该使用 {@link okhttp3.Cache}来代替这个接口
 *
 * OkHttp's internal cache interface. Applications shouldn't implement this: instead use {@link
 * okhttp3.Cache}.
 */
public interface InternalCache {
    Response get(Request request) throws IOException;

    CacheRequest put(Response response) throws IOException;

    /**
     * 移除对传入Request的缓存实体，这个方法在客户端将缓存作废的时候调用，例如当使用POST请求的时候
     *
     * Remove any cache entries for the supplied {@code request}. This is invoked when the client
     * invalidates the cache, such as when making POST requests.
     */
    void remove(Request request) throws IOException;

    /**
     * 通过{@code network}的headers，处理一个需要更新的条件请求。缓存的body不会更新，如果缓存的响应在
     * {@code cached}之前改变了，那么就什么都不做。
     *
     * Handles a conditional request hit by updating the stored cache response with the headers from
     * {@code network}. The cached response body is not updated. If the stored response has changed
     * since {@code cached} was returned, this does nothing.
     */
    void update(Response cached, Response network);

    /**
     * 跟踪一个GET，这个GET的缓存很完美
     * Track an conditional GET that was satisfied by this cache. */
    void trackConditionalCacheHit();

    /**
     * 跟踪一个HTTP响应，这个响应对{@code cacheStrategy}来说很完美
     * Track an HTTP response being satisfied with {@code cacheStrategy}. */
    void trackResponse(CacheStrategy cacheStrategy);
}

