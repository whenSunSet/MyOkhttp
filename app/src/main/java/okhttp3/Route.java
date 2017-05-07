package okhttp3;

/**
 * Created by Administrator on 2017/5/7 0007.
 */

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * 一个被使用在连接上的达到抽象原服务器的具体路线，当创建一个连接的时候客户端有很多选项
 * The concrete route used by a connection to reach an abstract origin server. When creating a
 * connection the client has many options:
 *
 * <ul>
 *     HTTP 代理：一个客户端可能会显式的配置一个代理服务器，如果没有那么
 *     {@linkplain java.net.ProxySelector proxy selector}将会被使用的。这个将会返回多个可以尝试的代理服务器
 *     <li><strong>HTTP proxy:</strong> a proxy server may be explicitly configured for the client.
 *         Otherwise the {@linkplain java.net.ProxySelector proxy selector} is used. It may return
 *         multiple proxies to attempt.
 *         IP 地址：无论是否直接连接到原始服务器或使用代理都需要打开套接字并需要一个IP地址。DNS服务器可能返回多个IP地址进行尝试
 *     <li><strong>IP address:</strong> whether connecting directly to an origin server or a proxy,
 *         opening a socket requires an IP address. The DNS server may return multiple IP addresses
 *         to attempt.
 * </ul>
 *
 * <p>Each route is a specific selection of these options.
 */
public final class Route {
    final Address address;
    final Proxy proxy;
    final InetSocketAddress inetSocketAddress;

    public Route(Address address, Proxy proxy, InetSocketAddress inetSocketAddress) {
        if (address == null) {
            throw new NullPointerException("address == null");
        }
        if (proxy == null) {
            throw new NullPointerException("proxy == null");
        }
        if (inetSocketAddress == null) {
            throw new NullPointerException("inetSocketAddress == null");
        }
        this.address = address;
        this.proxy = proxy;
        this.inetSocketAddress = inetSocketAddress;
    }

    public Address address() {
        return address;
    }

    /**
     * 返回{@link Proxy}
     * Returns the {@link Proxy} of this route.
     * 警告：这里可能不推荐使用{@link Address#proxy}当其为null的时候，当proxy为null的时候
     * proxy selector 将会被使用
     * <strong>Warning:</strong> This may disagree with {@link Address#proxy} when it is null. When
     * the address's proxy is null, the proxy selector is used.
     */
    public Proxy proxy() {
        return proxy;
    }

    public InetSocketAddress socketAddress() {
        return inetSocketAddress;
    }

    /**
     * 返回true，如果这个HTTPS隧道通过一个HTTP代理
     * Returns true if this route tunnels HTTPS through an HTTP proxy. See <a
     * href="http://www.ietf.org/rfc/rfc2817.txt">RFC 2817, Section 5.2</a>.
     */
    public boolean requiresTunnel() {
        return address.sslSocketFactory != null && proxy.type() == Proxy.Type.HTTP;
    }

    @Override public boolean equals(Object obj) {
        if (obj instanceof Route) {
            Route other = (Route) obj;
            return address.equals(other.address)
                    && proxy.equals(other.proxy)
                    && inetSocketAddress.equals(other.inetSocketAddress);
        }
        return false;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + address.hashCode();
        result = 31 * result + proxy.hashCode();
        result = 31 * result + inetSocketAddress.hashCode();
        return result;
    }

    @Override public String toString() {
        return "Route{" + inetSocketAddress + "}";
    }
}
