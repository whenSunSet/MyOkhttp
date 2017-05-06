package okhttp3.internal.http2;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.IOException;

/**
 * 在HTTP/2连接停止但是仍试图使用这个连接的时候抛出异常
 * Thrown when an HTTP/2 connection is shutdown (either explicitly or if the peer has sent a GOAWAY
 * frame) and an attempt is made to use the connection.
 */
public final class ConnectionShutdownException extends IOException {
}

