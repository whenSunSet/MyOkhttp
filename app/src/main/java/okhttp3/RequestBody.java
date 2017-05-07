package okhttp3;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.ByteString;
import okio.Okio;
import okio.Source;

/**
 * Created by Administrator on 2017/5/7 0007.
 */
public abstract class RequestBody {
    /**
     * 返回这个body的Content-Type
     * Returns the Content-Type header for this body. */
    public abstract MediaType contentType();

    /**
     * 返回将要写入{@code out}的byte数，在调用{@link #writeTo}的时候，如果返回-1表示不知道
     * Returns the number of bytes that will be written to {@code out} in a call to {@link #writeTo},
     * or -1 if that count is unknown.
     */
    public long contentLength() throws IOException {
        return -1;
    }

    /**
     * 将内容写入{@code out}中
     * Writes the content of this request to {@code out}. */
    public abstract void writeTo(BufferedSink sink) throws IOException;

    /**
     * 从{@code content}中返回一个新的request body，如果{@code contentType}不是null并且缺少描述的字符集
     * 那么这里将会使用UTF-8
     * Returns a new request body that transmits {@code content}. If {@code contentType} is non-null
     * and lacks a charset, this will use UTF-8.
     */
    public static RequestBody create(MediaType contentType, String content) {
        Charset charset = Util.UTF_8;
        if (contentType != null) {
            charset = contentType.charset();
            if (charset == null) {
                charset = Util.UTF_8;
                contentType = MediaType.parse(contentType + "; charset=utf-8");
            }
        }
        byte[] bytes = content.getBytes(charset);
        return create(contentType, bytes);
    }

    /**
     * 从{@code content}中返回一个新的request body
     * Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final ByteString content) {
        return new RequestBody() {
            @Override public MediaType contentType() {
                return contentType;
            }

            @Override public long contentLength() throws IOException {
                return content.size();
            }

            @Override public void writeTo(BufferedSink sink) throws IOException {
                sink.write(content);
            }
        };
    }

    /**
     * 从{@code content}中返回一个新的request body
     * Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final byte[] content) {
        return create(contentType, content, 0, content.length);
    }

    /**
     * 从{@code content}中返回一个新的request body
     * Returns a new request body that transmits {@code content}. */
    public static RequestBody create(final MediaType contentType, final byte[] content,
                                     final int offset, final int byteCount) {
        if (content == null) throw new NullPointerException("content == null");
        Util.checkOffsetAndCount(content.length, offset, byteCount);
        return new RequestBody() {
            @Override public MediaType contentType() {
                return contentType;
            }

            @Override public long contentLength() {
                return byteCount;
            }

            @Override public void writeTo(BufferedSink sink) throws IOException {
                sink.write(content, offset, byteCount);
            }
        };
    }

    /**
     * 从{@code file}中返回一个新的request body
     * Returns a new request body that transmits the content of {@code file}. */
    public static RequestBody create(final MediaType contentType, final File file) {
        if (file == null) throw new NullPointerException("content == null");

        return new RequestBody() {
            @Override public MediaType contentType() {
                return contentType;
            }

            @Override public long contentLength() {
                return file.length();
            }

            @Override public void writeTo(BufferedSink sink) throws IOException {
                Source source = null;
                try {
                    source = Okio.source(file);
                    sink.writeAll(source);
                } finally {
                    Util.closeQuietly(source);
                }
            }
        };
    }
}
