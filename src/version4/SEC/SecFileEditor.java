package version4.SEC;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Replaces whole lines of a *.sec file.
 *
 * <p>If every new line fits in the space of the line it replaces, the bytes are written in place and
 * the remainder of the line is padded with spaces (readers split on whitespace and the checksum ignores
 * trailing padding, so this is invisible to them). Nothing else in the file moves.</p>
 *
 * <p>Otherwise the file is rewritten once, in a single pass, to a temporary file in the same directory,
 * which then atomically replaces the original. Lines that grew get some spare capacity so the next
 * edit can usually be done in place. The file's existing line terminator is preserved.</p>
 *
 * <p>After an edit the {@link SecFileReader} passed in is up to date (it is re-indexed after a rewrite).</p>
 */
public class SecFileEditor {

    /** Extra space reserved on a line that had to grow: max(MIN_SLACK, length * SLACK_FRACTION). */
    static final int MIN_SLACK = 64;
    static final double SLACK_FRACTION = 0.10;

    private final SecFileReader reader;

    public SecFileEditor(SecFileReader reader) {
        this.reader = reader;
    }

    /** Replace a single line. {@code content} must not contain a line terminator. */
    public void replaceLine(int line, String content) throws IOException {
        Map<Integer, String> one = new TreeMap<>();
        one.put(line, content);
        replaceLines(one);
    }

    /**
     * Replace several lines in one operation (at most one file rewrite).
     *
     * @param replacements line number -> new content without line terminator
     * @return true if the edit was done in place, false if the file had to be rewritten
     */
    public boolean replaceLines(Map<Integer, String> replacements) throws IOException {
        if (replacements.isEmpty()) return true;

        TreeMap<Integer, byte[]> lines = new TreeMap<>();
        boolean fits = true;
        for (Map.Entry<Integer, String> e : replacements.entrySet()) {
            int line = e.getKey();
            String content = e.getValue();
            if (content.indexOf('\n') >= 0 || content.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Replacement for line " + line + " contains a line terminator");
            }
            reader.getLineLength(line); // bounds check
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            lines.put(line, bytes);
            if (bytes.length > reader.getLineLength(line)) fits = false;
        }

        if (fits) {
            writeInPlace(lines);
            return true;
        }
        rewrite(lines);
        return false;
    }

    private void writeInPlace(TreeMap<Integer, byte[]> lines) throws IOException {
        try (FileChannel out = FileChannel.open(reader.getPath(), StandardOpenOption.WRITE)) {
            for (Map.Entry<Integer, byte[]> e : lines.entrySet()) {
                int line = e.getKey();
                byte[] padded = pad(e.getValue(), reader.getLineLength(line));
                writeFully(out, ByteBuffer.wrap(padded), reader.getLineOffset(line));
            }
            out.force(false);
        }
    }

    private void rewrite(TreeMap<Integer, byte[]> lines) throws IOException {
        Path target = reader.getPath();
        Path temp = SecFileWriter.siblingTempFile(target);
        try {
            Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target)); // keep the original's permissions
        } catch (UnsupportedOperationException ignored) {
            // not a POSIX file system (Windows): the new file inherits the directory's ACLs
        }
        byte[] eol = reader.getLineSeparator().getBytes(StandardCharsets.US_ASCII);
        FileChannel in = reader.channel();
        int lastLine = reader.getLineCount() - 1;

        try {
            try (FileChannel out = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                long copyFrom = 0;
                for (Map.Entry<Integer, byte[]> e : lines.entrySet()) {
                    int line = e.getKey();
                    long lineStart = reader.getLineOffset(line);
                    transferFully(in, copyFrom, lineStart - copyFrom, out);

                    byte[] content = e.getValue();
                    int oldLength = reader.getLineLength(line);
                    int capacity = (content.length <= oldLength)
                            ? oldLength
                            : content.length + Math.max(MIN_SLACK, (int) (content.length * SLACK_FRACTION));
                    writeFully(out, ByteBuffer.wrap(pad(content, capacity)), out.position());
                    out.position(out.position() + capacity);
                    writeFully(out, ByteBuffer.wrap(eol), out.position());
                    out.position(out.position() + eol.length);

                    // skip the old line and its terminator (the last line may have none)
                    copyFrom = (line < lastLine) ? reader.getLineOffset(line + 1) : in.size();
                }
                transferFully(in, copyFrom, in.size() - copyFrom, out);
                out.force(false);
            }

            reader.close(); // required on Windows before the original can be replaced
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            if (!reader.isOpen()) reader.open();
            throw e;
        }
        reader.open();
    }

    private static byte[] pad(byte[] content, int length) {
        if (content.length == length) return content;
        byte[] padded = Arrays.copyOf(content, length);
        Arrays.fill(padded, content.length, length, (byte) ' ');
        return padded;
    }

    private static void writeFully(FileChannel out, ByteBuffer buf, long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            pos += out.write(buf, pos);
        }
    }

    /** FileChannel.transferTo may move fewer bytes than asked; loop until done. */
    private static void transferFully(FileChannel in, long position, long count, FileChannel out) throws IOException {
        long done = 0;
        long outPos = out.position();
        while (done < count) {
            long n = in.transferTo(position + done, count - done, out);
            if (n <= 0) {
                // some platforms return 0 for large counts; fall back to a buffered copy
                ByteBuffer buf = ByteBuffer.allocate((int) Math.min(1 << 20, count - done));
                int r = in.read(buf, position + done);
                if (r <= 0) throw new IOException("Unexpected end of file while copying");
                buf.flip();
                out.position(outPos + done);
                while (buf.hasRemaining()) out.write(buf);
                n = r;
            }
            done += n;
        }
        out.position(outPos + count);
    }
}
