package okhttp3;

/**
 * Created by Administrator on 2017/5/7 0007.
 */

import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.internal.Util;
import static okhttp3.internal.Util.equal;
/**
 * 一个原服务器连接的规范，对于简单的连接来说，这是服务器的 主机名和端口号。如果一个显式的代理被请求了
 * (或者 {@linkplain Proxy#NO_PROXY 没有代理}，这是明确的请求），这里也包括了代理服务器的信息。为了安全
 * 连接地址也包括了SSL socket 工厂、主机名验证器 和 证书
 * A specification for a connection to an origin server. For simple connections, this is the
 * server's hostname and port. If an explicit proxy is requested (or {@linkplain Proxy#NO_PROXY no
 * proxy} is explicitly requested), this also includes that proxy information. For secure
 * connections the address also includes the SSL socket factory, hostname verifier, and certificate
 * pinner.
 * 一个共享{@code Address}的HTTP请求可能也共享一个{@link Connection}.
 * <p>HTTP requests that share the same {@code Address} may also share the same {@link Connection}.
 */
public final class Address {
    final HttpUrl url;
    final Dns dns;
    final SocketFactory socketFactory;
    final Authenticator proxyAuthenticator;
    final List<Protocol> protocols;
    final List<ConnectionSpec> connectionSpecs;
    final ProxySelector proxySelector;
    final Proxy proxy;
    final SSLSocketFactory sslSocketFactory;
    final HostnameVerifier hostnameVerifier;
    final CertificatePinner certificatePinner;

    public Address(String uriHost, int uriPort, Dns dns, SocketFactory socketFactory,
                   SSLSocketFactory sslSocketFactory, HostnameVerifier hostnameVerifier,
                   CertificatePinner certificatePinner, Authenticator proxyAuthenticator, Proxy proxy,
                   List<Protocol> protocols, List<ConnectionSpec> connectionSpecs, ProxySelector proxySelector) {
        this.url = new HttpUrl.Builder()
                .scheme(sslSocketFactory != null ? "https" : "http")
                .host(uriHost)
                .port(uriPort)
                .build();

        if (dns == null) throw new NullPointerException("dns == null");
        this.dns = dns;

        if (socketFactory == null) throw new NullPointerException("socketFactory == null");
        this.socketFactory = socketFactory;

        if (proxyAuthenticator == null) {
            throw new NullPointerException("proxyAuthenticator == null");
        }
        this.proxyAuthenticator = proxyAuthenticator;

        if (protocols == null) throw new NullPointerException("protocols == null");
        this.protocols = Util.immutableList(protocols);

        if (connectionSpecs == null) throw new NullPointerException("connectionSpecs == null");
        this.connectionSpecs = Util.immutableList(connectionSpecs);

        if (proxySelector == null) throw new NullPointerException("proxySelector == null");
        this.proxySelector = proxySelector;

        this.proxy = proxy;
        this.sslSocketFactory = sslSocketFactory;
        this.hostnameVerifier = hostnameVerifier;
        this.certificatePinner = certificatePinner;
    }

    /**
     * 返回一个包括了 原服务器的主机名和端口 的URL。
     * Returns a URL with the hostname and port of the origin server. The path, query, and fragment of
     * this URL are always empty, since they are not significant for planning a route.
     */
    public HttpUrl url() {
        return url;
    }

    /** Returns the service that will be used to resolve IP addresses for hostnames. */
    public Dns dns() {
        return dns;
    }

    /**
     * 返回一个新连接的socket工厂
     * Returns the socket factory for new connections. */
    public SocketFactory socketFactory() {
        return socketFactory;
    }

    /**
     * 返回代理服务器的authenticator
     * Returns the client's proxy authenticator. */
    public Authenticator proxyAuthenticator() {
        return proxyAuthenticator;
    }

    /**
     * 返回支持的协议，这个方法经常会返回包含HTTP/1.1的非空 list
     * Returns the protocols the client supports. This method always returns a non-null list that
     * contains minimally {@link Protocol#HTTP_1_1}.
     */
    public List<Protocol> protocols() {
        return protocols;
    }

    public List<ConnectionSpec> connectionSpecs() {
        return connectionSpecs;
    }

    /**
     * 返回一个address的代理选择器，这个只用在proxy为null的时候。
     * Returns this address's proxy selector. Only used if the proxy is null. If none of this
     * selector's proxies are reachable, a direct connection will be attempted.
     */
    public ProxySelector proxySelector() {
        return proxySelector;
    }

    /**
     * 返回这个address显式指定的代理HTTP。如果是null就使用{@linkplain #proxySelector proxy selector}.
     * Returns this address's explicitly-specified HTTP proxy, or null to delegate to the {@linkplain
     * #proxySelector proxy selector}.
     */
    public Proxy proxy() {
        return proxy;
    }

    /**
     * 返回SSL socket工厂，如果是null说明这个不是HTTPS地址
     * Returns the SSL socket factory, or null if this is not an HTTPS address. */
    public SSLSocketFactory sslSocketFactory() {
        return sslSocketFactory;
    }

    /**
     * 返回hostname verifier，如果是null那么说明这个不是HTTPS地址
     * Returns the hostname verifier, or null if this is not an HTTPS address. */
    public HostnameVerifier hostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * 返回certificate pinner，如果是null数目这个不是HTTPS地址
     * Returns this address's certificate pinner, or null if this is not an HTTPS address. */
    public CertificatePinner certificatePinner() {
        return certificatePinner;
    }

    @Override public boolean equals(Object other) {
        return other instanceof Address
                && url.equals(((Address) other).url)
                && equalsNonHost((Address) other);
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + url.hashCode();
        result = 31 * result + dns.hashCode();
        result = 31 * result + proxyAuthenticator.hashCode();
        result = 31 * result + protocols.hashCode();
        result = 31 * result + connectionSpecs.hashCode();
        result = 31 * result + proxySelector.hashCode();
        result = 31 * result + (proxy != null ? proxy.hashCode() : 0);
        result = 31 * result + (sslSocketFactory != null ? sslSocketFactory.hashCode() : 0);
        result = 31 * result + (hostnameVerifier != null ? hostnameVerifier.hashCode() : 0);
        result = 31 * result + (certificatePinner != null ? certificatePinner.hashCode() : 0);
        return result;
    }

    boolean equalsNonHost(Address that) {
        return this.dns.equals(that.dns)
                && this.proxyAuthenticator.equals(that.proxyAuthenticator)
                && this.protocols.equals(that.protocols)
                && this.connectionSpecs.equals(that.connectionSpecs)
                && this.proxySelector.equals(that.proxySelector)
                && equal(this.proxy, that.proxy)
                && equal(this.sslSocketFactory, that.sslSocketFactory)
                && equal(this.hostnameVerifier, that.hostnameVerifier)
                && equal(this.certificatePinner, that.certificatePinner)
                && this.url().port() == that.url().port();
    }

    @Override public String toString() {
        StringBuilder result = new StringBuilder()
                .append("Address{")
                .append(url.host()).append(":").append(url.port());

        if (proxy != null) {
            result.append(", proxy=").append(proxy);
        } else {
            result.append(", proxySelector=").append(proxySelector);
        }

        result.append("}");
        return result.toString();
    }
}

