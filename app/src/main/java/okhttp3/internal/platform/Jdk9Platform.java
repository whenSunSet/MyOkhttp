package okhttp3.internal.platform;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.Protocol;

/**
 * OpenJDK 9+.
 */
final class Jdk9Platform extends Platform {
    final Method setProtocolMethod;
    final Method getProtocolMethod;

    public Jdk9Platform(Method setProtocolMethod, Method getProtocolMethod) {
        this.setProtocolMethod = setProtocolMethod;
        this.getProtocolMethod = getProtocolMethod;
    }

    @Override
    public void configureTlsExtensions(SSLSocket sslSocket, String hostname,
                                       List<Protocol> protocols) {
        try {
            SSLParameters sslParameters = sslSocket.getSSLParameters();

            List<String> names = alpnProtocolNames(protocols);

            setProtocolMethod.invoke(sslParameters,
                    new Object[] {names.toArray(new String[names.size()])});

            sslSocket.setSSLParameters(sslParameters);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError();
        }
    }

    @Override
    public String getSelectedProtocol(SSLSocket socket) {
        try {
            String protocol = (String) getProtocolMethod.invoke(socket);

            // SSLSocket.getApplicationProtocol 返回“” 如果应用的协议值没有被使用
            // 如果你没有指定SSLParameters.setApplicationProtocols
            // SSLSocket.getApplicationProtocol returns "" if application protocols values will not
            // be used. Observed if you didn't specify SSLParameters.setApplicationProtocols
            if (protocol == null || protocol.equals("")) {
                return null;
            }

            return protocol;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError();
        }
    }

    @Override public X509TrustManager trustManager(SSLSocketFactory sslSocketFactory) {
        // Not supported due to access checks on JDK 9+:
        // java.lang.reflect.InaccessibleObjectException: Unable to make member of class
        // sun.security.ssl.SSLSocketFactoryImpl accessible:  module java.base does not export
        // sun.security.ssl to unnamed module @xxx
        throw new UnsupportedOperationException(
                "clientBuilder.sslSocketFactory(SSLSocketFactory) not supported on JDK 9+");
    }

    public static Jdk9Platform buildIfSupported() {
        // Find JDK 9 new methods
        try {
            Method setProtocolMethod =
                    SSLParameters.class.getMethod("setApplicationProtocols", String[].class);
            Method getProtocolMethod = SSLSocket.class.getMethod("getApplicationProtocol");

            return new Jdk9Platform(setProtocolMethod, getProtocolMethod);
        } catch (NoSuchMethodException ignored) {
            // pre JDK 9
        }

        return null;
    }
}

