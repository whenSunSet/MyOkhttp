package okhttp3;

/**
 * Created by heshixiyang on 2017/5/7.
 */
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLSocket;
import okhttp3.internal.Util;

import static okhttp3.internal.Util.concat;
import static okhttp3.internal.Util.indexOf;
import static okhttp3.internal.Util.intersect;
import static okhttp3.internal.Util.nonEmptyIntersection;

/**
 * HTTP交流数据使用的socket connection 的特别配置。对于{@code https:}的URL，这里包括了TLS版本和cipher suites在
 * 商谈过后使用安全的连接的情况下使用
 * Specifies configuration for the socket connection that HTTP traffic travels through. For {@code
 * https:} URLs, this includes the TLS version and cipher suites to use when negotiating a secure
 * connection.
 *
 * 当这里只允许使用SSL socket的时候，TLS版本会在connection spec中配置。例如如果一个SSL socket对TSL1.3没有允许
 * ，connection spec中就不会使用1.3版本的特性。这里的规则也同样适用于cipher suites
 * <p>The TLS versions configured in a connection spec are only be used if they are also enabled in
 * the SSL socket. For example, if an SSL socket does not have TLS 1.3 enabled, it will not be used
 * even if it is present on the connection spec. The same policy also applies to cipher suites.
 *
 * 使用{@link Builder#allEnabledTlsVersions()} 和 {@link Builder#allEnabledCipherSuites}来
 * 推迟所有的在SSL socket下的特性选择
 * <p>Use {@link Builder#allEnabledTlsVersions()} and {@link Builder#allEnabledCipherSuites} to
 * defer all feature selection to the underlying SSL socket.
 */
public final class ConnectionSpec {

    // 这里是最近 Chrome 51 所支持的cipher suites，在 2016-05-25之前。
    // 这里所有的cipher suites都可以在Android 7.0中获得，早期的Android则支持这里的子集
    // This is nearly equal to the cipher suites supported in Chrome 51, current as of 2016-05-25.
    // All of these suites are available on Android 7.0; earlier releases support a subset of these
    // suites. https://github.com/square/okhttp/issues/1972
    private static final CipherSuite[] APPROVED_CIPHER_SUITES = new CipherSuite[] {
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,

            // 注意这里下面的cipher suites 都在HTTP/2的糟糕的cipher suites 列表中。
            // 我们需要持续持有下面这些cipher suites 直至更好的suites可以被获取。
            // 例如在Android 4.4 或 Java 7中没有更好的cipher suites，除了下面这些
            // Note that the following cipher suites are all on HTTP/2's bad cipher suites list. We'll
            // continue to include them until better suites are commonly available. For example, none
            // of the better cipher suites listed above shipped with Android 4.4 or Java 7.
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
    };

    /**
     * 一个现代的TLS connection，其可以扩展到使得SNI 和 ALPN是可以被获取的
     * A modern TLS connection with extensions like SNI and ALPN available. */
    public static final ConnectionSpec MODERN_TLS = new Builder(true)
            .cipherSuites(APPROVED_CIPHER_SUITES)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .supportsTlsExtensions(true)
            .build();

    /**
     * 向后兼容与过时服务器的互操作
     * A backwards-compatible fallback connection for interop with obsolete servers. */
    public static final ConnectionSpec COMPATIBLE_TLS = new Builder(MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_0)
            .supportsTlsExtensions(true)
            .build();

    /**
     * 未加密的，没有进行身份验证的URL
     * Unencrypted, unauthenticated connections for {@code http:} URLs. */
    public static final ConnectionSpec CLEARTEXT = new Builder(false).build();

    final boolean tls;
    final boolean supportsTlsExtensions;
    final String[] cipherSuites;
    final String[] tlsVersions;

    ConnectionSpec(Builder builder) {
        this.tls = builder.tls;
        this.cipherSuites = builder.cipherSuites;
        this.tlsVersions = builder.tlsVersions;
        this.supportsTlsExtensions = builder.supportsTlsExtensions;
    }

    public boolean isTls() {
        return tls;
    }

    /**
     * 返回一个被使用在一个connection中的cipher suites。
     * Returns the cipher suites to use for a connection. Returns {@code null} if all of the SSL
     * socket's enabled cipher suites should be used.
     */
    public List<CipherSuite> cipherSuites() {
        return cipherSuites != null ? CipherSuite.forJavaNames(cipherSuites) : null;
    }

    /**
     * Returns the TLS versions to use when negotiating a connection. Returns {@code null} if all of
     * the SSL socket's enabled TLS versions should be used.
     */
    public List<TlsVersion> tlsVersions() {
        return tlsVersions != null ? TlsVersion.forJavaNames(tlsVersions) : null;
    }

    public boolean supportsTlsExtensions() {
        return supportsTlsExtensions;
    }

    /** Applies this spec to {@code sslSocket}. */
    void apply(SSLSocket sslSocket, boolean isFallback) {
        ConnectionSpec specToApply = supportedSpec(sslSocket, isFallback);

        if (specToApply.tlsVersions != null) {
            sslSocket.setEnabledProtocols(specToApply.tlsVersions);
        }
        if (specToApply.cipherSuites != null) {
            sslSocket.setEnabledCipherSuites(specToApply.cipherSuites);
        }
    }

    /**
     * 返回一个本对象的拷贝，这个拷贝省略了cipher suites 和 TLS，没有启用 {@code sslSocket}.
     * Returns a copy of this that omits cipher suites and TLS versions not enabled by {@code
     * sslSocket}.
     */
    private ConnectionSpec supportedSpec(SSLSocket sslSocket, boolean isFallback) {
        String[] cipherSuitesIntersection = cipherSuites != null
                ? intersect(CipherSuite.ORDER_BY_NAME, sslSocket.getEnabledCipherSuites(), cipherSuites)
                : sslSocket.getEnabledCipherSuites();
        String[] tlsVersionsIntersection = tlsVersions != null
                ? intersect(Util.NATURAL_ORDER, sslSocket.getEnabledProtocols(), tlsVersions)
                : sslSocket.getEnabledProtocols();

        // In accordance with https://tools.ietf.org/html/draft-ietf-tls-downgrade-scsv-00
        // the SCSV cipher is added to signal that a protocol fallback has taken place.
        String[] supportedCipherSuites = sslSocket.getSupportedCipherSuites();
        int indexOfFallbackScsv = indexOf(
                CipherSuite.ORDER_BY_NAME, supportedCipherSuites, "TLS_FALLBACK_SCSV");
        if (isFallback && indexOfFallbackScsv != -1) {
            cipherSuitesIntersection = concat(
                    cipherSuitesIntersection, supportedCipherSuites[indexOfFallbackScsv]);
        }

        return new Builder(this)
                .cipherSuites(cipherSuitesIntersection)
                .tlsVersions(tlsVersionsIntersection)
                .build();
    }

    /**
     * 返回true，如果这里的socket，按照当前的配置，支持connection spec。为了让一个socket兼容启用cipher suites
     * 协议必须相交换
     * Returns {@code true} if the socket, as currently configured, supports this connection spec. In
     * order for a socket to be compatible the enabled cipher suites and protocols must intersect.
     *
     * 对一个cipher suites，最少{@link #cipherSuites() required cipher suites}一定要和socket开启的cipher suites
     * 进行比较。如果没有需要启动的cipher suites ，那么socket必须启用一个
     * <p>For cipher suites, at least one of the {@link #cipherSuites() required cipher suites} must
     * match the socket's enabled cipher suites. If there are no required cipher suites the socket
     * must have at least one cipher suite enabled.
     *
     * 对于协议，最少{@link #tlsVersions() required protocols}一定要和socket开启的一个协议进行比较
     * <p>For protocols, at least one of the {@link #tlsVersions() required protocols} must match the
     * socket's enabled protocols.
     */
    public boolean isCompatible(SSLSocket socket) {
        if (!tls) {
            return false;
        }

        if (tlsVersions != null && !nonEmptyIntersection(
                Util.NATURAL_ORDER, tlsVersions, socket.getEnabledProtocols())) {
            return false;
        }

        if (cipherSuites != null && !nonEmptyIntersection(
                CipherSuite.ORDER_BY_NAME, cipherSuites, socket.getEnabledCipherSuites())) {
            return false;
        }

        return true;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ConnectionSpec)) return false;
        if (other == this) return true;

        ConnectionSpec that = (ConnectionSpec) other;
        if (this.tls != that.tls) return false;

        if (tls) {
            if (!Arrays.equals(this.cipherSuites, that.cipherSuites)) return false;
            if (!Arrays.equals(this.tlsVersions, that.tlsVersions)) return false;
            if (this.supportsTlsExtensions != that.supportsTlsExtensions) return false;
        }

        return true;
    }

    @Override public int hashCode() {
        int result = 17;
        if (tls) {
            result = 31 * result + Arrays.hashCode(cipherSuites);
            result = 31 * result + Arrays.hashCode(tlsVersions);
            result = 31 * result + (supportsTlsExtensions ? 0 : 1);
        }
        return result;
    }

    @Override public String toString() {
        if (!tls) {
            return "ConnectionSpec()";
        }

        String cipherSuitesString = cipherSuites != null ? cipherSuites().toString() : "[all enabled]";
        String tlsVersionsString = tlsVersions != null ? tlsVersions().toString() : "[all enabled]";
        return "ConnectionSpec("
                + "cipherSuites=" + cipherSuitesString
                + ", tlsVersions=" + tlsVersionsString
                + ", supportsTlsExtensions=" + supportsTlsExtensions
                + ")";
    }

    public static final class Builder {
        boolean tls;
        String[] cipherSuites;
        String[] tlsVersions;
        boolean supportsTlsExtensions;

        Builder(boolean tls) {
            this.tls = tls;
        }

        public Builder(ConnectionSpec connectionSpec) {
            this.tls = connectionSpec.tls;
            this.cipherSuites = connectionSpec.cipherSuites;
            this.tlsVersions = connectionSpec.tlsVersions;
            this.supportsTlsExtensions = connectionSpec.supportsTlsExtensions;
        }

        public Builder allEnabledCipherSuites() {
            if (!tls) throw new IllegalStateException("no cipher suites for cleartext connections");
            this.cipherSuites = null;
            return this;
        }

        public Builder cipherSuites(CipherSuite... cipherSuites) {
            if (!tls) throw new IllegalStateException("no cipher suites for cleartext connections");

            String[] strings = new String[cipherSuites.length];
            for (int i = 0; i < cipherSuites.length; i++) {
                strings[i] = cipherSuites[i].javaName;
            }
            return cipherSuites(strings);
        }

        public Builder cipherSuites(String... cipherSuites) {
            if (!tls) throw new IllegalStateException("no cipher suites for cleartext connections");

            if (cipherSuites.length == 0) {
                throw new IllegalArgumentException("At least one cipher suite is required");
            }

            this.cipherSuites = cipherSuites.clone(); // Defensive copy.
            return this;
        }

        public Builder allEnabledTlsVersions() {
            if (!tls) throw new IllegalStateException("no TLS versions for cleartext connections");
            this.tlsVersions = null;
            return this;
        }

        public Builder tlsVersions(TlsVersion... tlsVersions) {
            if (!tls) throw new IllegalStateException("no TLS versions for cleartext connections");

            String[] strings = new String[tlsVersions.length];
            for (int i = 0; i < tlsVersions.length; i++) {
                strings[i] = tlsVersions[i].javaName;
            }

            return tlsVersions(strings);
        }

        public Builder tlsVersions(String... tlsVersions) {
            if (!tls) throw new IllegalStateException("no TLS versions for cleartext connections");

            if (tlsVersions.length == 0) {
                throw new IllegalArgumentException("At least one TLS version is required");
            }

            this.tlsVersions = tlsVersions.clone(); // Defensive copy.
            return this;
        }

        public Builder supportsTlsExtensions(boolean supportsTlsExtensions) {
            if (!tls) throw new IllegalStateException("no TLS extensions for cleartext connections");
            this.supportsTlsExtensions = supportsTlsExtensions;
            return this;
        }

        public ConnectionSpec build() {
            return new ConnectionSpec(this);
        }
    }
}
