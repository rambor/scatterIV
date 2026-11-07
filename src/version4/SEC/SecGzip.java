package version4.SEC;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gzip support for *.sec files ({@code run.sec.gz}).
 *
 * <p>A compressed file is standard gzip of an ordinary *.sec file, so {@code gunzip}, Python's
 * {@code gzip.open} and similar tools read it directly. Scatter works on compressed files by unpacking them
 * to a temporary *.sec (random access needs an uncompressed file), and compresses edits back.</p>
 */
public final class SecGzip {

    public static final String EXTENSION = ".gz";
    private static final int BUFFER = 1 << 16;

    private SecGzip() {}

    /** True if the file starts with the gzip magic number (independent of its name). */
    public static boolean isGzip(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            int b1 = in.read();
            int b2 = in.read();
            return b1 == 0x1f && b2 == 0x8b;
        }
    }

    /** "run.sec.gz" -> "run.sec", "run.gz" -> "run", anything else unchanged. */
    public static String stripGzipExtension(String name) {
        return name.toLowerCase().endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : name;
    }

    /** Unpack to a new temporary *.sec in the system temp directory (deleted on JVM exit if not before). */
    public static Path decompressToTemp(Path gz) throws IOException {
        String base = stripGzipExtension(gz.getFileName().toString());
        Path temp = Files.createTempFile("scatter-" + base.replaceAll("[^A-Za-z0-9._-]", "_") + "-", ".sec");
        temp.toFile().deleteOnExit();
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(gz), BUFFER), BUFFER);
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp), 1 << 20)) {
            copy(in, out);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw new IOException("Could not decompress " + gz.getFileName() + ": " + e.getMessage(), e);
        }
        return temp;
    }

    /** Compress {@code source} to {@code target}; the target is replaced atomically. */
    public static void compress(Path source, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temp = SecFileWriter.siblingTempFile(absolute);
        try {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(source), 1 << 20);
                 OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temp), BUFFER), BUFFER)) {
                copy(in, out);
            }
            try {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(absolute));
            } catch (UnsupportedOperationException | NoSuchFileException ignored) {
                // new file or non-POSIX file system: keep defaults
            }
            moveIntoPlace(temp, absolute);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    /** Uncompress {@code source} (.sec.gz) to {@code target} (.sec), replacing it atomically. */
    public static void decompress(Path source, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temp = SecFileWriter.siblingTempFile(absolute);
        try {
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(source), BUFFER), BUFFER);
                 OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp), 1 << 20)) {
                copy(in, out);
            }
            moveIntoPlace(temp, absolute);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    /** Copy an uncompressed file to {@code target}, replacing it atomically. */
    static void copy(Path source, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temp = SecFileWriter.siblingTempFile(absolute);
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            moveIntoPlace(temp, absolute);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[BUFFER];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
