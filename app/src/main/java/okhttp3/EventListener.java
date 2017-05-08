package okhttp3;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
import java.net.InetAddress;
import java.util.List;

// TODO(jwilson): make this public after the 3.7 release.
abstract class EventListener {
    public static final EventListener NONE = new EventListener() {
    };

    static EventListener.Factory factory(final EventListener listener) {
        return new EventListener.Factory() {
            public EventListener create(Call call) {
                return listener;
            }
        };
    }

    public void fetchStart(Call call) {
    }

    public void dnsStart(Call call, String domainName) {
    }

    public void dnsEnd(Call call, String domainName, List<InetAddress> inetAddressList,
                       Throwable throwable) {
    }

    public void connectStart(Call call, InetAddress address, int port) {
    }

    public void secureConnectStart(Call call) {
    }

    public void secureConnectEnd(Call call, Handshake handshake,
                                 Throwable throwable) {
    }

    public void connectEnd(Call call,  InetAddress address, int port, String protocol,
                           Throwable throwable) {
    }

    public void requestHeadersStart(Call call) {
    }

    public void requestHeadersEnd(Call call, Throwable throwable) {
    }

    public void requestBodyStart(Call call) {
    }

    public void requestBodyEnd(Call call, Throwable throwable) {
    }

    public void responseHeadersStart(Call call) {
    }

    public void responseHeadersEnd(Call call, Throwable throwable) {
    }

    public void responseBodyStart(Call call) {
    }

    public void responseBodyEnd(Call call, Throwable throwable) {
    }

    public void fetchEnd(Call call, Throwable throwable) {
    }

    public interface Factory {
        EventListener create(Call call);
    }
}

