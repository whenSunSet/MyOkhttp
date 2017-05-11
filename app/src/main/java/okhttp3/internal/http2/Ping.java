package okhttp3.internal.http2;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 一个本地的ping类
 * A locally-originated ping.
 */
final class Ping {
    private final CountDownLatch latch = new CountDownLatch(1);
    private long sent = -1;
    private long received = -1;

    Ping() {
    }

    void send() {
        if (sent != -1) throw new IllegalStateException();
        sent = System.nanoTime();
    }

    void receive() {
        if (received != -1 || sent == -1) throw new IllegalStateException();
        received = System.nanoTime();
        latch.countDown();
    }

    void cancel() {
        if (received != -1 || sent == -1) throw new IllegalStateException();
        received = sent - 1;
        latch.countDown();
    }

    /**
     * 返回往返一次ping 时间的纳秒数，如果必要的话胡等待resonse的返回。如果返回-1的话，那么就表示response被取消了
     * Returns the round trip time for this ping in nanoseconds, waiting for the response to arrive if
     * necessary. Returns -1 if the response was canceled.
     */
    public long roundTripTime() throws InterruptedException {
        latch.await();
        return received - sent;
    }

    /**
     * 返回往返一次ping 时间的纳秒数，如果返回-1的话，那么就表示response被取消了，如果返回-2的时候表示时间超出了
     * Returns the round trip time for this ping in nanoseconds, or -1 if the response was canceled,
     * or -2 if the timeout elapsed before the round trip completed.
     */
    public long roundTripTime(long timeout, TimeUnit unit) throws InterruptedException {
        if (latch.await(timeout, unit)) {
            return received - sent;
        } else {
            return -2;
        }
    }
}

