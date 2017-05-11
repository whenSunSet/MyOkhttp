package okhttp3.internal.connection;

/**
 * Created by Administrator on 2017/5/9 0009.
 */
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 抛出一个异常通过一个单Route指出一个连接的问题。多个可能*尝试替代协议,其中没有一个是成功的
 * An exception thrown to indicate a problem connecting via a single Route. Multiple attempts may
 * have been made with alternative protocols, none of which were successful.
 */
public final class RouteException extends RuntimeException {
    private static final Method addSuppressedExceptionMethod;

    static {
        Method m;
        try {
            m = Throwable.class.getDeclaredMethod("addSuppressed", Throwable.class);
        } catch (Exception e) {
            m = null;
        }
        addSuppressedExceptionMethod = m;
    }

    private IOException lastException;

    public RouteException(IOException cause) {
        super(cause);
        lastException = cause;
    }

    public IOException getLastConnectException() {
        return lastException;
    }

    public void addConnectException(IOException e) {
        addSuppressedIfPossible(e, lastException);
        lastException = e;
    }

    private void addSuppressedIfPossible(IOException e, IOException suppressed) {
        if (addSuppressedExceptionMethod != null) {
            try {
                addSuppressedExceptionMethod.invoke(e, suppressed);
            } catch (InvocationTargetException | IllegalAccessException ignored) {
            }
        }
    }
}

