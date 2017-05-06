package okhttp3.internal.cache;

import java.io.IOException;

import okio.Sink;

/**
 * Created by heshixiyang on 2017/5/6.
 */

public interface CacheRequest {
    Sink body() throws IOException;

    void abort();
}
