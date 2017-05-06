package okhttp3.internal.io;

/**
 * Created by heshixiyang on 2017/5/6.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * 通过读写文件来访问数据存储结构。绝大多数调用者使用{@link #SYSTEM}这个实现就行。这个实现使用了
 * 本地文件存储引擎。替换的实现可以去使用注入（正在测试）或者转换存储数据（添加加密）
 * Access to read and write files on a hierarchical data store. Most callers should use the {@link
 * #SYSTEM} implementation, which uses the host machine's local file system. Alternate
 * implementations may be used to inject faults (for testing) or to transform stored data (to add
 * encryption, for example).
 *
 * 所有的文件系统操作都是活动的，比如将{@link #source}和{@link #exists}保持隔离。
 * 不能保证{@link FileNotFoundException}不会被抛出。这个文件可能会在两个调用者直接移动
 * <p>All operations on a file system are racy. For example, guarding a call to {@link #source} with
 * {@link #exists} does not guarantee that {@link FileNotFoundException} will not be thrown. The
 * file may be moved between the two calls!
 *
 * 缺乏重要的特性,比如文件看,元数据,权限,以及磁盘空间信息。以换取这些限制,这个接口更容易实现和所有版本的Java和Android上工作。
 * <p>This interface is less ambitious than {@link java.nio} introduced in Java 7.
 * It lacks important features like file watching, metadata, permissions, and disk space
 * information. In exchange for these limitations, this interface is easier to implement and works
 * on all versions of Java and Android.
 */
public interface FileSystem {
    /** The host machine's local file system. */
    FileSystem SYSTEM = new FileSystem() {
        @Override public Source source(File file) throws FileNotFoundException {
            return Okio.source(file);
        }

        @Override public Sink sink(File file) throws FileNotFoundException {
            try {
                return Okio.sink(file);
            } catch (FileNotFoundException e) {
                // Maybe the parent directory doesn't exist? Try creating it first.
                file.getParentFile().mkdirs();
                return Okio.sink(file);
            }
        }

        @Override public Sink appendingSink(File file) throws FileNotFoundException {
            try {
                return Okio.appendingSink(file);
            } catch (FileNotFoundException e) {
                // Maybe the parent directory doesn't exist? Try creating it first.
                file.getParentFile().mkdirs();
                return Okio.appendingSink(file);
            }
        }

        @Override public void delete(File file) throws IOException {
            // If delete() fails, make sure it's because the file didn't exist!
            if (!file.delete() && file.exists()) {
                throw new IOException("failed to delete " + file);
            }
        }

        @Override public boolean exists(File file) {
            return file.exists();
        }

        @Override public long size(File file) {
            return file.length();
        }

        @Override public void rename(File from, File to) throws IOException {
            delete(to);
            if (!from.renameTo(to)) {
                throw new IOException("failed to rename " + from + " to " + to);
            }
        }

        @Override public void deleteContents(File directory) throws IOException {
            File[] files = directory.listFiles();
            if (files == null) {
                throw new IOException("not a readable directory: " + directory);
            }
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteContents(file);
                }
                if (!file.delete()) {
                    throw new IOException("failed to delete " + file);
                }
            }
        }
    };

    /** Reads from {@code file}. */
    Source source(File file) throws FileNotFoundException;

    /**
     * Writes to {@code file}, discarding any data already present. Creates parent directories if
     * necessary.
     */
    Sink sink(File file) throws FileNotFoundException;

    /**
     * Writes to {@code file}, appending if data is already present. Creates parent directories if
     * necessary.
     */
    Sink appendingSink(File file) throws FileNotFoundException;

    /** Deletes {@code file} if it exists. Throws if the file exists and cannot be deleted. */
    void delete(File file) throws IOException;

    /** Returns true if {@code file} exists on the file system. */
    boolean exists(File file);

    /** Returns the number of bytes stored in {@code file}, or 0 if it does not exist. */
    long size(File file);

    /** Renames {@code from} to {@code to}. Throws if the file cannot be renamed. */
    void rename(File from, File to) throws IOException;

    /**
     * Recursively delete the contents of {@code directory}. Throws an IOException if any file could
     * not be deleted, or if {@code dir} is not a readable directory.
     */
    void deleteContents(File directory) throws IOException;
}

