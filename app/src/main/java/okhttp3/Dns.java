package okhttp3;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * 一个域名的服务，这个服务可以解析一个地址的IP或者主机名。很多应用都使用{@linkplain #SYSTEM system DNS service}
 * 这是默认的。有些应用可能提供一些自己的实现对于不同的DNS服务
 * A domain name service that resolves IP addresses for host names. Most applications will use the
 * {@linkplain #SYSTEM system DNS service}, which is the default. Some applications may provide
 * their own implementation to use a different DNS server, to prefer IPv6 addresses, to prefer IPv4
 * addresses, or to force a specific known IP address.
 *
 * 实现这个接口需要安全的并发
 * <p>Implementations of this interface must be safe for concurrent use.
 */
public interface Dns {
    /**
     * 一个DNS使用了{@link InetAddress#getAllByName}，去请求系统内置的查看IP地址的方法。
     * 大多数自定义的{@link Dns}实现需要代理这个实例
     * A DNS that uses {@link InetAddress#getAllByName} to ask the underlying operating system to
     * lookup IP addresses. Most custom {@link Dns} implementations should delegate to this instance.
     */
    Dns SYSTEM = new Dns() {
        @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            if (hostname == null) throw new UnknownHostException("hostname == null");
            return Arrays.asList(InetAddress.getAllByName(hostname));
        }
    };

    /**
     * 返回一个IP地址通过{@code hostname}，返回的ip将会被OKHttp按顺序尝试。如果一个连接地址出现了错误，那么OKHttp将会
     * 用下一个IP地址重试直至一个连接被建立起来，或者IP地址使用完毕了
     * Returns the IP addresses of {@code hostname}, in the order they will be attempted by OkHttp. If
     * a connection to an address fails, OkHttp will retry the connection with the next address until
     * either a connection is made, the set of IP addresses is exhausted, or a limit is exceeded.
     */
    List<InetAddress> lookup(String hostname) throws UnknownHostException;
}

