package okhttp3;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

/**
 * 修改所观察到的东西，通常拦截器添加、删除或转换头在请求或响应。
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * responses coming back in. Typically interceptors add, remove, or transform headers on the request
 * or response.
 */
public interface Interceptor {
    Response intercept(Chain chain) throws IOException;

    interface Chain {
        Request request();

        Response proceed(Request request) throws IOException;

        Connection connection();
    }
}

