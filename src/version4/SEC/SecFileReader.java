package version4.SEC;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import version4.sasCIF.SasObject;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Read-only, random-access view of a *.sec file.
 *
 * <p>On open, the file is scanned once at the byte level to record where every line starts and how
 * long it is (excluding its line terminator). This makes the index independent of the platform
 * charset and of whether the file uses LF or CRLF line endings. Lines are then read on demand with
 * positional reads; nothing is memory-mapped, so the file is never pinned (important on Windows,
 * where a mapped file cannot be truncated, replaced or deleted).</p>
 *
 * <p>Line layout (all versions): line 0 is the JSON header, lines 1-3 are remarks, vector lines
 * are {@code MD5 v0 v1 ...} and per-frame lines are {@code frameIndex MD5 v0 v1 ...}. The MD5 is
 * taken over the values text with trailing whitespace reduced to a single space, so padding added
 * by version-2 writers does not affect the checksum.</p>
 */
public class SecFileReader implements Closeable {

    /** Files without a {@code sec_file_version} key in the header. */
    public static final int LEGACY_VERSION = 1;

    static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Path path;
    private FileChannel channel;

    private long[] offsets;
    private int[] lengths;
    private int lineCount;
    private String lineSeparator;

    private String headerJson;
    private SasObject sasObject;
    private SecFormat secFormat;
    private int formatVersion;

    public SecFileReader(Path path) throws IOException {
        this.path = path.toAbsolutePath();
        open();
    }

    /** (Re)open the file, rebuild the line index and re-parse the header. */
    final void open() throws IOException {
        closeQuietly();
        channel = FileChannel.open(path, StandardOpenOption.READ);
        index();
        parseHeader();
    }

    private void index() throws IOException {
        long[] off = new long[4096];
        int[] len = new int[4096];
        int count = 0;
        String separator = null;

        ByteBuffer buf = ByteBuffer.allocate(1 << 20);
        long filePos = 0;          // absolute position of buf[0]
        long lineStart = 0;
        boolean previousWasCR = false;

        channel.position(0);
        while (true) {
            buf.clear();
            int read = channel.read(buf);
            if (read < 0) break;
            buf.flip();
            for (int i = 0; i < read; i++) {
                byte b = buf.get(i);
                if (b == '\n') {
                    long end = filePos + i;                    // position of '\n'
                    int length = (int) (end - lineStart - (previousWasCR ? 1 : 0));
                    if (separator == null) separator = previousWasCR ? "\r\n" : "\n";
                    if (count == off.length) {
                        off = Arrays.copyOf(off, count * 2);
                        len = Arrays.copyOf(len, count * 2);
                    }
                    off[count] = lineStart;
                    len[count] = length;
                    count++;
                    lineStart = end + 1;
                }
                previousWasCR = (b == '\r');
            }
            filePos += read;
        }

        // final line without terminator
        if (lineStart < filePos) {
            if (count == off.length) {
                off = Arrays.copyOf(off, count + 1);
                len = Arrays.copyOf(len, count + 1);
            }
            off[count] = lineStart;
            len[count] = (int) (filePos - lineStart);
            count++;
        }

        if (count == 0) {
            throw new IOException("Empty SEC file: " + path);
        }

        this.offsets = off;
        this.lengths = len;
        this.lineCount = count;
        this.lineSeparator = (separator == null) ? "\n" : separator;
    }

    private void parseHeader() throws IOException {
        headerJson = readLine(0).trim();
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(headerJson);
        } catch (IOException e) {
            throw new IOException("Line 0 of " + path.getFileName() + " is not a valid JSON header", e);
        }
        if (root == null || !root.isObject()) {
            throw new IOException("Line 0 of " + path.getFileName() + " is not a JSON object");
        }
        if (root.path("sec_format").isMissingNode()) {
            throw new IOException(path.getFileName() + " is not a SEC file: header has no sec_format");
        }

        formatVersion = root.path(SecFileWriter.VERSION_KEY).asInt(LEGACY_VERSION);
        sasObject = new SasObject(headerJson);
        secFormat = sasObject.getSecFormat();
        if (secFormat == null) {
            throw new IOException("Could not parse sec_format in " + path.getFileName());
        }
    }

    // ------------------------------------------------------------------ raw line access

    public Path getPath() { return path; }

    public int getLineCount() { return lineCount; }

    /** Byte offset of the first character of a line. */
    public long getLineOffset(int line) {
        checkLine(line);
        return offsets[line];
    }

    /** Length of a line in bytes, excluding its line terminator. */
    public int getLineLength(int line) {
        checkLine(line);
        return lengths[line];
    }

    /** Line terminator used by this file ("\n" or "\r\n"), taken from the first line. */
    public String getLineSeparator() { return lineSeparator; }

    FileChannel channel() { return channel; }

    private void checkLine(int line) {
        if (line < 0 || line >= lineCount) {
            throw new IndexOutOfBoundsException("Line " + line + " requested but " + path.getFileName() + " has " + lineCount + " lines");
        }
    }

    public byte[] readLineBytes(int line) throws IOException {
        checkLine(line);
        ByteBuffer buf = ByteBuffer.allocate(lengths[line]);
        long pos = offsets[line];
        while (buf.hasRemaining()) {
            int n = channel.read(buf, pos + buf.position());
            if (n < 0) throw new IOException("Unexpected end of file reading line " + line);
        }
        return buf.array();
    }

    public String readLine(int line) throws IOException {
        return new String(readLineBytes(line), StandardCharsets.UTF_8);
    }

    static String[] tokens(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() ? new String[0] : WHITESPACE.split(trimmed);
    }

    static double parse(String token, double missing) {
        if (".".equals(token)) return missing;
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            return missing;
        }
    }

    // ------------------------------------------------------------------ typed access

    /**
     * Values of a vector line ({@code MD5 v0 v1 ...}). Missing ('.') or unparsable entries become {@code missing}.
     */
    public double[] readVector(int line, double missing) throws IOException {
        String[] t = tokens(readLine(line));
        double[] values = new double[Math.max(0, t.length - 1)];
        for (int i = 1; i < t.length; i++) values[i - 1] = parse(t[i], missing);
        return values;
    }

    /** Integer values of a vector line, e.g. frame indices or buffer flags. */
    public int[] readIntVector(int line) throws IOException {
        String[] t = tokens(readLine(line));
        int[] values = new int[Math.max(0, t.length - 1)];
        for (int i = 1; i < t.length; i++) values[i - 1] = Integer.parseInt(t[i]);
        return values;
    }

    /**
     * Values of a per-frame line ({@code frame MD5 v0 v1 ...}) in the block starting at {@code blockStart}.
     * Missing values ('.', written for excluded frames) are returned as NaN.
     *
     * @throws IllegalArgumentException if the frame index written on the line does not match
     */
    public double[] readFrame(int blockStart, int frame) throws IOException {
        int line = blockStart + frame;
        String[] t = tokens(readLine(line));
        if (t.length == 0 || !String.valueOf(frame).equals(t[0])) {
            throw new IllegalArgumentException("Frame index mismatch on line " + line + ": expected " + frame
                    + " found '" + (t.length == 0 ? "" : t[0]) + "' - file may be corrupted, run validate()");
        }
        double[] values = new double[Math.max(0, t.length - 2)];
        for (int i = 2; i < t.length; i++) values[i - 2] = parse(t[i], Double.NaN);
        return values;
    }

    // ------------------------------------------------------------------ checksums

    /** MD5 (upper-case hex) of a values string, ignoring trailing padding. */
    public static String checksum(String values) {
        return DigestUtils.md5Hex(normalizeForChecksum(values)).toUpperCase();
    }

    static String normalizeForChecksum(String values) {
        int end = values.length();
        while (end > 0 && Character.isWhitespace(values.charAt(end - 1))) end--;
        return values.substring(0, end) + " ";
    }

    /** True if the line at {@code line} carries a frame index before its checksum. */
    public boolean isFrameLine(int line) {
        int start = secFormat.getUnsubtracted_intensities_index();
        Integer avg = secFormat.getAveraged_buffer_index();
        int end = (avg != null && avg > 0) ? avg : lineCount;
        return start > 0 && line >= start && line < end;
    }

    /** Verify the checksum of one data line (lines 0-3 have none and always pass). */
    public boolean verifyLine(int line) throws IOException {
        if (line < 4) return true;
        String text = readLine(line);
        int skip = isFrameLine(line) ? 2 : 1;
        int pos = 0;
        String md5 = null;
        for (int s = 0; s < skip; s++) {
            int space = text.indexOf(' ', pos);
            if (space < 0) return false;
            md5 = text.substring(pos, space);
            pos = space + 1;
        }
        return md5.equalsIgnoreCase(checksum(text.substring(pos)));
    }

    /** Check every data line; returns the line numbers whose checksum does not match. */
    public List<Integer> validate(ProgressListener listener) throws IOException {
        ProgressListener progress = ProgressListener.orNone(listener);
        List<Integer> bad = new ArrayList<>();
        for (int i = 4; i < lineCount; i++) {
            if (!verifyLine(i)) bad.add(i);
            progress.progress(i + 1, lineCount);
        }
        return bad;
    }

    // ------------------------------------------------------------------ header

    /** Header JSON as stored in the file (padding removed). */
    public String getHeaderJson() { return headerJson; }

    public SasObject getSasObject() { return sasObject; }

    public SecFormat getSecFormat() { return secFormat; }

    /** {@value #LEGACY_VERSION} for files written before versioning, otherwise the stored version. */
    public int getFormatVersion() { return formatVersion; }

    // ------------------------------------------------------------------ lifecycle

    private void closeQuietly() {
        if (channel != null) {
            try { channel.close(); } catch (IOException ignored) { }
            channel = null;
        }
    }

    public boolean isOpen() { return channel != null && channel.isOpen(); }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }
}
