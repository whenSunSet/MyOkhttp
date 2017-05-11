package okhttp3.internal.connection;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
import java.util.LinkedHashSet;
import java.util.Set;
import okhttp3.Route;

/**
 * 在连接一个目标服务器的时候，使用这个黑名单来避免失败的链路。
 * 因为这个所以OkHttp可以学习到自己的错误。当试图连接一个特别的IP或者是代理服务器的时候出了错
 * 这个链路会被替代和使用更好的链路
 * A blacklist of failed routes to avoid when creating a new connection to a target address. This is
 * used so that OkHttp can learn from its mistakes: if there was a failure attempting to connect to
 * a specific IP address or proxy server, that failure is remembered and alternate routes are
 * preferred.
 */
public final class RouteDatabase {
    private final Set<Route> failedRoutes = new LinkedHashSet<>();

    /**
     * 从{@code failedRoute}中记录一个失败的连接
     * Records a failure connecting to {@code failedRoute}. */
    public synchronized void failed(Route failedRoute) {
        failedRoutes.add(failedRoute);
    }

    /**
     * 从{@code route}中记录一个成功的链接，即将这个成功的Route从failedRoutes中删掉
     * Records success connecting to {@code route}. */
    public synchronized void connected(Route route) {
        failedRoutes.remove(route);
    }

    /**
     * 返回true，如果传入的{@code route}是错误的路由
     * Returns true if {@code route} has failed recently and should be avoided. */
    public synchronized boolean shouldPostpone(Route route) {
        return failedRoutes.contains(route);
    }
}
