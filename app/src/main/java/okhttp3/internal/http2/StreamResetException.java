package okhttp3.internal.http2;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

/**
 *
 * Thrown when an HTTP/2 stream is canceled without damage to the socket that carries it. */
public final class StreamResetException extends IOException {
    public final ErrorCode errorCode;

    public StreamResetException(ErrorCode errorCode) {
        super("stream was reset: " + errorCode);
        this.errorCode = errorCode;
    }
}
