package okhttp3.internal.platform;

/**
 * Created by Administrator on 2017/5/7 0007.
 */

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.internal.tls.BasicCertificateChainCleaner;
import okhttp3.internal.tls.CertificateChainCleaner;
import okhttp3.internal.tls.TrustRootIndex;
import okio.Buffer;

/**
 * 访问特定平台的特性
 * Access to platform-specific features.
 *
 * 服务的名称可以显示
 * <h3>Server name indication (SNI)</h3>
 *
 * 支持Android 2.3+.
 * <p>Supported on Android 2.3+.
 *
 * 支持OpenJDK 7+
 * Supported on OpenJDK 7+
 *
 * Session的过程
 * <h3>Session Tickets</h3>
 *
 * 支持Android 2.3+.
 * <p>Supported on Android 2.3+.
 *
 * android运输统计
 * <h3>Android Traffic Stats (Socket Tagging)</h3>
 *
 * 支持Android 4.0+
 * <p>Supported on Android 4.0+.
 *
 * 应用程序层协议谈判
 * <h3>ALPN (Application Layer Protocol Negotiation)</h3>
 *
 * 支持Android 5.0+，api在Android 4.4就实现的但事实不稳定的
 * <p>Supported on Android 5.0+. The APIs were present in Android 4.4, but that implementation was
 * unstable.
 *
 * 支持OpenJDK 7 和 8 (通过JettyALPN-boot库)
 * Supported on OpenJDK 7 and 8 (via the JettyALPN-boot library).
 *
 * 支持OpenJDK 9 （通过SSLParameters and SSLSocket特性）
 * Supported on OpenJDK 9 via SSLParameters and SSLSocket features.
 *
 * 信任管理器提取
 * <h3>Trust Manager Extraction</h3>
 *
 * 支持Android 2.3+ 和 OpenJDK 7+.， 没有公共的API来恢复 信任管理器 所以我们重新创建一个SSLSocketFactory
 * <p>Supported on Android 2.3+ and OpenJDK 7+. There are no public APIs to recover the trust
 * manager that was used to create an {@link SSLSocketFactory}.
 *
 * Android明文允许检测
 * <h3>Android Cleartext Permit Detection</h3>
 * 支持Android 6.0+ 通过{@code NetworkSecurityPolicy}.实现
 * <p>Supported on Android 6.0+ via {@code NetworkSecurityPolicy}.
 */
public class Platform {
    private static final Platform PLATFORM = findPlatform();
    public static final int INFO = 4;
    public static final int WARN = 5;
    private static final Logger logger = Logger.getLogger(OkHttpClient.class.getName());

    public static Platform get() {
        return PLATFORM;
    }

    /**
     * 前缀用于自定义header
     * Prefix used on custom headers. */
    public String getPrefix() {
        return "OkHttp";
    }

    public X509TrustManager trustManager(SSLSocketFactory sslSocketFactory) {
        // 试图获取 信用管理器 从OpenJDK socket 中。我们在所有的平台中使用这个为了支持Robolectric
        // 这个class是Android和Oracle JDK的混合物。
        // 注意：在Robolectric中我们不支持HTTP/2或者其他好的特性
        // Attempt to get the trust manager from an OpenJDK socket factory. We attempt this on all
        // platforms in order to support Robolectric, which mixes classes from both Android and the
        // Oracle JDK. Note that we don't support HTTP/2 or other nice features on Robolectric.
        try {
            Class<?> sslContextClass = Class.forName("sun.security.ssl.SSLContextImpl");
            Object context = readFieldOrNull(sslSocketFactory, sslContextClass, "context");
            if (context == null) return null;
            return readFieldOrNull(context, X509TrustManager.class, "trustManager");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * 在{@code sslSocket} 中配置TLS的扩展为了{@code route}.
     * Configure TLS extensions on {@code sslSocket} for {@code route}.
     *
     * @param hostname non-null for client-side handshakes; null for server-side handshakes.
     */
    public void configureTlsExtensions(SSLSocket sslSocket, String hostname,
                                       List<Protocol> protocols) {
    }

    /**
     * 在TLS握手之调用，为了释放被分配的内存资源{@link #configureTlsExtensions}.
     * Called after the TLS handshake to release resources allocated by {@link
     * #configureTlsExtensions}.
     */
    public void afterHandshake(SSLSocket sslSocket) {
    }

    /**
     * 返回商谈好的协议，或者如果没有协议可以商谈的话就返回null
     * Returns the negotiated protocol, or null if no protocol was negotiated. */
    public String getSelectedProtocol(SSLSocket socket) {
        return null;
    }

    public void connectSocket(Socket socket, InetSocketAddress address,
                              int connectTimeout) throws IOException {
        socket.connect(address, connectTimeout);
    }

    public void log(int level, String message, Throwable t) {
        Level logLevel = level == WARN ? Level.WARNING : Level.INFO;
        logger.log(logLevel, message, t);
    }

    public boolean isCleartextTrafficPermitted(String hostname) {
        return true;
    }

    /**
     * 当这个方法被执行的瞬间返回一个持有着 追踪信息 的对象。这个方法需要特别的使用在实现了
     * {@link java.io.Closeable}的对象中，并且结合{@link #logCloseableLeak(String, Object)}.方法
     * Returns an object that holds a stack trace created at the moment this method is executed. This
     * should be used specifically for {@link java.io.Closeable} objects and in conjunction with
     * {@link #logCloseableLeak(String, Object)}.
     */
    public Object getStackTraceForCloseable(String closer) {
        if (logger.isLoggable(Level.FINE)) {
            return new Throwable(closer); // These are expensive to allocate.
        }
        return null;
    }

    public void logCloseableLeak(String message, Object stackTrace) {
        if (stackTrace == null) {
            message += " To see where this was allocated, set the OkHttpClient logger level to FINE: "
                    + "Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);";
        }
        log(WARN, message, (Throwable) stackTrace);
    }

    public static List<String> alpnProtocolNames(List<Protocol> protocols) {
        List<String> names = new ArrayList<>(protocols.size());
        for (int i = 0, size = protocols.size(); i < size; i++) {
            Protocol protocol = protocols.get(i);
            if (protocol == Protocol.HTTP_1_0) continue; // No HTTP/1.0 for ALPN.
            names.add(protocol.toString());
        }
        return names;
    }

    public CertificateChainCleaner buildCertificateChainCleaner(X509TrustManager trustManager) {
        return new BasicCertificateChainCleaner(TrustRootIndex.get(trustManager));
    }

    /**
     * 试图比较运行时的host来获取Platform的实现
     * Attempt to match the host runtime to a capable Platform implementation. */
    private static Platform findPlatform() {
        Platform android = AndroidPlatform.buildIfSupported();

        if (android != null) {
            return android;
        }

        Platform jdk9 = Jdk9Platform.buildIfSupported();

        if (jdk9 != null) {
            return jdk9;
        }

        Platform jdkWithJettyBoot = JdkWithJettyBootPlatform.buildIfSupported();

        if (jdkWithJettyBoot != null) {
            return jdkWithJettyBoot;
        }

        // Probably an Oracle JDK like OpenJDK.
        return new Platform();
    }

    /**
     * 返回byte的连接，协议的前缀名称
     * Returns the concatenation of 8-bit, length prefixed protocol names.
     * http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
     */
    static byte[] concatLengthPrefixed(List<Protocol> protocols) {
        Buffer result = new Buffer();
        for (int i = 0, size = protocols.size(); i < size; i++) {
            Protocol protocol = protocols.get(i);
            if (protocol == Protocol.HTTP_1_0) continue; // No HTTP/1.0 for ALPN.
            result.writeByte(protocol.toString().length());
            result.writeUtf8(protocol.toString());
        }
        return result.readByteArray();
    }

    static <T> T readFieldOrNull(Object instance, Class<T> fieldType, String fieldName) {
        for (Class<?> c = instance.getClass(); c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value == null || !fieldType.isInstance(value)) return null;
                return fieldType.cast(value);
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException e) {
                throw new AssertionError();
            }
        }

        // Didn't find the field we wanted. As a last gasp attempt, try to find the value on a delegate.
        if (!fieldName.equals("delegate")) {
            Object delegate = readFieldOrNull(instance, Object.class, "delegate");
            if (delegate != null) return readFieldOrNull(delegate, fieldType, fieldName);
        }

        return null;
    }
}

