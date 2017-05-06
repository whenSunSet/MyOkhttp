package okhttp3;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

/**
 * 一个 call表示一个请求已经做好了被执行的准备。一个call可以被取消，
 * 对于本对象，是单个request/response 的组合（也就是一个流），所以不能被执行两次
 * A call is a request that has been prepared for execution. A call can be canceled. As this object
 * represents a single request/response pair (stream), it cannot be executed twice.
 */
public interface Call extends Cloneable {
    /**
     * 返回一个原始的请求，这个请求被这个call初始化
     * Returns the original request that initiated this call. */
    Request request();

    /**
     * 立即调用并阻塞本线程，直至这个请求被响应或者产生error。同步阻塞
     * Invokes the request immediately, and blocks until the response can be processed or is in
     * error.
     * 为了避免资源泄漏，调用者必须确保{@link Response}返回的{@link ResponseBody}的底层资源被关闭
     * <p>To avoid leaking resources callers should close the {@link Response} which in turn will
     * close the underlying {@link ResponseBody}.
     *
     * <pre>@{code
     *
     *   // ensure the response (and underlying response body) is closed
     *   try (Response response = client.newCall(request).execute()) {
     *     ...
     *   }
     *
     * }</pre>
     *
     * 调用者可能读取response body 通过{@link Response#body}方法，为了避免资源泄漏调用者必须调用
     * {@linkplain ResponseBody close the response body}
     * <p>The caller may read the response body with the response's {@link Response#body} method. To
     * avoid leaking resources callers must {@linkplain ResponseBody close the response body} or the
     * Response.
     * 注意：运输层成功了（接受到了Http的响应码，headers和body）但是并部意味着 应用层是成功的：
     * {@code response}可能会持有不好的HTTP响应码如404和500等
     * <p>Note that transport-layer success (receiving a HTTP response code, headers and body) does
     * not necessarily indicate application-layer success: {@code response} may still indicate an
     * unhappy HTTP response code like 404 or 500.
     *
     * @throws IOException if the request could not be executed due to cancellation, a connectivity
     * problem or timeout. Because networks can fail during an exchange, it is possible that the
     * remote server accepted the request before the failure.
     * @throws IllegalStateException when the call has already been executed.
     */
    Response execute() throws IOException;

    /**
     * 异步请求
     * Schedules the request to be executed at some point in the future.
     *
     * {@link OkHttpClient#dispatcher dispatcher}中定义了：当请求要运行的时候，经常还有其他请求已经在
     * 运行了。
     * 客户端在之后会调用回调{@code responseCallback}来处理HTTP响应或者错误。
     * <p>The {@link OkHttpClient#dispatcher dispatcher} defines when the request will run: usually
     * immediately unless there are several other requests currently being executed.
     *
     * <p>This client will later call back {@code responseCallback} with either an HTTP response or a
     * failure exception.
     *
     * @throws IllegalStateException when the call has already been executed.
     */
    void enqueue(Callback responseCallback);

    /**
     * 取消一个请求，如果可以的话，请求早已完成，就不能再取消了
     * Cancels the request, if possible. Requests that are already complete cannot be canceled. */
    void cancel();

    /**
     * 返回true，如果这个请求已经被{@linkplain #execute() executed} or {@linkplain #enqueue(Callback) enqueued}执行
     * 多次调用同个执行将会产生一个错误。
     * Returns true if this call has been either {@linkplain #execute() executed} or {@linkplain
     * #enqueue(Callback) enqueued}. It is an error to execute a call more than once.
     */
    boolean isExecuted();

    boolean isCanceled();

    /**
     * 创建一个新的请求，使用相同的参数即使已经请求过了
     * Create a new, identical call to this one which can be enqueued or executed even if this call
     * has already been.
     */
    Call clone();

    interface Factory {
        Call newCall(Request request);
    }
}

