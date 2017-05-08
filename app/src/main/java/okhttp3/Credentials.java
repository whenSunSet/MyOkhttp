package okhttp3;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
import java.nio.charset.Charset;
import okio.ByteString;

/** Factory for HTTP authorization credentials. */
public final class Credentials {
    private Credentials() {
    }

    /** Returns an auth credential for the Basic scheme. */
    public static String basic(String userName, String password) {
        return basic(userName, password, Charset.forName("ISO-8859-1"));
    }

    public static String basic(String userName, String password, Charset charset) {
        String usernameAndPassword = userName + ":" + password;
        byte[] bytes = usernameAndPassword.getBytes(charset);
        String encoded = ByteString.of(bytes).base64();
        return "Basic " + encoded;
    }
}
