package okhttp3.internal.http2;

/**
 * Created by heshixiyang on 2017/5/6.
 */
// http://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-7
public enum ErrorCode {
    /** Not an error! */
    NO_ERROR(0),

    PROTOCOL_ERROR(1),

    INTERNAL_ERROR(2),

    FLOW_CONTROL_ERROR(3),

    REFUSED_STREAM(7),

    CANCEL(8);

    public final int httpCode;

    ErrorCode(int httpCode) {
        this.httpCode = httpCode;
    }

    public static ErrorCode fromHttp2(int code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.httpCode == code) return errorCode;
        }
        return null;
    }
}

