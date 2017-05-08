package okhttp3.internal;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
public final class Version {
    public static String userAgent() {
        return "okhttp/${project.version}";
    }

    private Version() {
    }
}
