package version4.SEC;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import version4.sasCIF.Hidable;
import version4.sasCIF.HidableSerializer;
import version4.sasCIF.SasObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/**
 * Writes a new *.sec file (format version {@value #CURRENT_VERSION}).
 *
 * <h3>Version 2 rules</h3>
 * <ul>
 *   <li>UTF-8, LF line endings on every platform.</li>
 *   <li>The JSON header carries {@code "sec_file_version": 2} at the top level (outside {@code sec_format},
 *       so older Scatter releases, which ignore unknown top-level keys, can still read the file).</li>
 *   <li>Lines that are edited after the file is created (header, signal, integrated qIq, Rg, I(0), their
 *       errors and the buffer flags) are padded with trailing spaces to a fixed capacity, so later edits are
 *       done in place instead of rewriting the whole file.</li>
 *   <li>Checksums are MD5 of the values text with trailing whitespace reduced to one space, which is
 *       exactly what version 1 files contain, so the checksum rule is the same for both versions.</li>
 * </ul>
 *
 * The line layout is unchanged from version 1:
 * <pre>
 *  0 JSON header          5 signal          9 buffer flags     13 .. unsubtracted frames
 *  1-3 # REMARK           6 integrated qIq 10 q values            .. unsubtracted errors
 *  4 frame indices        7 Rg             11 Rg errors           .. subtracted frames
 *                         8 I(0)           12 I(0) errors         .. subtracted errors
 *                                                                 averaged buffer, averaged buffer errors
 * </pre>
 */
public final class SecFileWriter {

    public static final int CURRENT_VERSION = 2;
    public static final String VERSION_KEY = "sec_file_version";
    public static final String EOL = "\n";

    public static final int FRAME_LINE = 4;
    public static final int SIGNAL_LINE = 5;
    public static final int QIQ_LINE = 6;
    public static final int RG_LINE = 7;
    public static final int IZERO_LINE = 8;
    public static final int BUFFER_LINE = 9;
    public static final int Q_LINE = 10;
    public static final int RG_ERROR_LINE = 11;
    public static final int IZERO_ERROR_LINE = 12;
    public static final int FIRST_FRAME_LINE = 13;

    /** Spare bytes after the header JSON for later metadata edits. */
    static final int HEADER_SLACK = 4096;
    /** Checksum (32 hex digits) plus separator. */
    static final int CHECKSUM_WIDTH = 33;

    // worst-case width of one formatted value, including sign and trailing space
    static final int SIGNAL_WIDTH = 16;   // -1.23456789E+00
    static final int QIQ_WIDTH = 14;      // -1.234567E+00
    static final int E3_WIDTH = 11;       // -1.234E+00
    static final int FLAG_WIDTH = 2;      // 1

    private SecFileWriter() {}

    // ------------------------------------------------------------------ number formatting (unchanged from v1)

    public static String convertSignalToString(double value) { return String.format(Locale.US, "%.8E ", value); }
    public static String convertDoubleTo3EString(double value) { return String.format(Locale.US, "%.3E ", value); }
    public static String convertqIqToString(double value) { return String.format(Locale.US, "%.6E ", value); }
    public static String convertSingleBinaryToString(int value) { return String.format(Locale.US, "%d ", value); }
    public static String convertIntensityToString(double value) { return String.format(Locale.US, "%.4E ", value); }

    /** Number of decimal digits in the shortest representation of a q-value, e.g. 0.00034566 -> 5. */
    public static int getDigits(double qvalue) {
        String toText = Double.toString(qvalue);
        int integerPlaces = toText.indexOf('.');
        String[] temp = toText.split("\\.0*");
        return (temp.length == 2) ? temp[1].length() : (toText.length() - integerPlaces - 1);
    }

    public static String formattedQ(double qvalue, int numberOfDigits) {
        int precision = (numberOfDigits >= 7 && numberOfDigits <= 14) ? numberOfDigits - 1 : 6;
        return String.format(Locale.US, "%." + precision + "E", qvalue);
    }

    // ------------------------------------------------------------------ header

    static ObjectMapper headerMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.registerModule(new SimpleModule() {
            @Override
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                context.addBeanSerializerModifier(new BeanSerializerModifier() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription desc, JsonSerializer<?> serializer) {
                        if (Hidable.class.isAssignableFrom(desc.getBeanClass())) {
                            return new HidableSerializer((JsonSerializer<Object>) serializer);
                        }
                        return serializer;
                    }
                });
            }
        });
        return mapper;
    }

    /** JSON of the SasObject alone, as written by version 1. */
    public static String sasObjectJson(SasObject sasObject) throws JsonProcessingException {
        return headerMapper().writeValueAsString(sasObject);
    }

    /** Header JSON for a file of the given version (version >= 2 adds {@value #VERSION_KEY}). */
    public static String headerJson(SasObject sasObject, int version) throws IOException {
        ObjectMapper mapper = headerMapper();
        String json = mapper.writeValueAsString(sasObject);
        if (version < 2) return json;
        ObjectNode body = (ObjectNode) mapper.readTree(json);
        ObjectNode root = mapper.createObjectNode();
        root.put(VERSION_KEY, version);
        root.setAll(body);
        return mapper.writeValueAsString(root);
    }

    /**
     * Create an empty temporary file next to {@code target} (same directory, so the final move is atomic).
     * Unlike Files.createTempFile it gets the normal default permissions rather than owner-only.
     */
    static Path siblingTempFile(Path target) throws IOException {
        Path dir = target.getParent() == null ? Paths.get(".") : target.getParent();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int attempt = 0; ; attempt++) {
            Path candidate = dir.resolve("." + target.getFileName() + "." + Long.toHexString(random.nextLong()) + ".tmp");
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException e) {
                if (attempt > 10) throw e;
            }
        }
    }

    static String padTo(String line, int capacity) {
        if (line.length() >= capacity) return line;
        StringBuilder sb = new StringBuilder(capacity).append(line);
        while (sb.length() < capacity) sb.append(' ');
        return sb.toString();
    }

    static String vectorLine(String values) {
        return SecFileReader.checksum(values) + " " + values;
    }

    static String frameLine(int frame, String values) {
        return frame + " " + SecFileReader.checksum(values) + " " + values;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Build the SecFormat (line indices) for {@code data}. The result is also set on {@code sasObject}.
     */
    public static SecFormat layout(SasObject sasObject, SecData data) {
        int frames = data.getTotalFrames();
        SecFormat f = new SecFormat(frames);
        f.setThreshold(data.getThreshold());
        f.setTotal_momentum_transfer_vectors(data.getTotalQ());
        f.setFrame_index(FRAME_LINE);
        f.setSignal_index(SIGNAL_LINE);
        f.setIntegrated_qIq_index(QIQ_LINE);
        f.setRg_index(RG_LINE);
        f.setIzero_index(IZERO_LINE);
        f.setBackground_index(BUFFER_LINE);
        f.setMomentum_transfer_vector_index(Q_LINE);
        f.setRg_error_index(RG_ERROR_LINE);
        f.setIzero_error_index(IZERO_ERROR_LINE);
        f.setUnsubtracted_intensities_index(FIRST_FRAME_LINE);
        f.setUnsubtracted_intensities_error_index(FIRST_FRAME_LINE + frames);
        f.setSubtracted_intensities_index(FIRST_FRAME_LINE + 2 * frames);
        f.setSubtracted_intensities_error_index(FIRST_FRAME_LINE + 3 * frames);
        f.setAveraged_buffer_index(FIRST_FRAME_LINE + 4 * frames);
        f.setAveraged_buffer_error_index(FIRST_FRAME_LINE + 4 * frames + 1);
        sasObject.setSecFormat(f);
        return f;
    }

    /**
     * Write {@code data} to {@code target}. The file is first written next to the target and then moved
     * into place, so an existing file is never left half-written.
     *
     * @return the SecFormat that was written (also set on {@code sasObject})
     */
    public static SecFormat write(Path target, SasObject sasObject, SecData data, ProgressListener listener) throws IOException {
        ProgressListener progress = ProgressListener.orNone(listener);
        int frames = data.getTotalFrames();
        int totalq = data.getTotalQ();
        if (data.averagedBuffer == null) {
            throw new IllegalStateException("SecData has no averaged buffer");
        }

        SecFormat format = layout(sasObject, data);
        sasObject.setSasResult(null);

        Path absolute = target.toAbsolutePath();
        Path dir = absolute.getParent() == null ? Paths.get(".") : absolute.getParent();
        Files.createDirectories(dir);
        Path temp = siblingTempFile(absolute);

        try {
            try (Writer out = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(temp), StandardCharsets.UTF_8), 1 << 20)) {
                progress.message("Writing header and summary lines");

                String header = headerJson(sasObject, CURRENT_VERSION);
                out.write(padTo(header, header.length() + HEADER_SLACK) + EOL);
                out.write("# REMARK" + EOL);
                out.write("# REMARK" + EOL);
                out.write("# REMARK" + EOL);

                StringBuilder fLine = new StringBuilder(frames * 4);
                StringBuilder signalLine = new StringBuilder(frames * SIGNAL_WIDTH);
                StringBuilder qIqLine = new StringBuilder(frames * QIQ_WIDTH);
                StringBuilder rgLine = new StringBuilder(frames * E3_WIDTH);
                StringBuilder izeroLine = new StringBuilder(frames * E3_WIDTH);
                StringBuilder rgErrorLine = new StringBuilder(frames * E3_WIDTH);
                StringBuilder izeroErrorLine = new StringBuilder(frames * E3_WIDTH);
                StringBuilder bufferLine = new StringBuilder(frames * FLAG_WIDTH);

                for (int i = 0; i < frames; i++) {
                    fLine.append(String.format(Locale.US, "%d ", i));
                    signalLine.append(convertSignalToString(data.signal[i]));
                    qIqLine.append(convertqIqToString(data.integratedQIq[i]));
                    rgLine.append(convertDoubleTo3EString(data.rg[i]));
                    izeroLine.append(convertDoubleTo3EString(data.izero[i]));
                    rgErrorLine.append(convertDoubleTo3EString(data.rgError[i]));
                    izeroErrorLine.append(convertDoubleTo3EString(data.izeroError[i]));
                    bufferLine.append(convertSingleBinaryToString(data.isBuffer[i]));
                }

                out.write(vectorLine(fLine.toString()) + EOL);                                                         // 4
                out.write(padTo(vectorLine(signalLine.toString()), CHECKSUM_WIDTH + frames * SIGNAL_WIDTH) + EOL);      // 5
                out.write(padTo(vectorLine(qIqLine.toString()), CHECKSUM_WIDTH + frames * QIQ_WIDTH) + EOL);            // 6
                out.write(padTo(vectorLine(rgLine.toString()), CHECKSUM_WIDTH + frames * E3_WIDTH) + EOL);              // 7
                out.write(padTo(vectorLine(izeroLine.toString()), CHECKSUM_WIDTH + frames * E3_WIDTH) + EOL);           // 8
                out.write(padTo(vectorLine(bufferLine.toString()), CHECKSUM_WIDTH + frames * FLAG_WIDTH) + EOL);        // 9

                double[] q = data.getQvalues();
                int digits = 0;
                for (double qv : q) digits = Math.max(digits, getDigits(qv));
                StringBuilder qline = new StringBuilder(totalq * (digits + 7));
                for (double qv : q) qline.append(formattedQ(qv, digits)).append(' ');
                out.write(vectorLine(qline.toString()) + EOL);                                                          // 10

                out.write(padTo(vectorLine(rgErrorLine.toString()), CHECKSUM_WIDTH + frames * E3_WIDTH) + EOL);         // 11
                out.write(padTo(vectorLine(izeroErrorLine.toString()), CHECKSUM_WIDTH + frames * E3_WIDTH) + EOL);      // 12

                StringBuilder blank = new StringBuilder(3 * totalq);
                for (int i = 0; i < totalq; i++) blank.append(" . ");
                String blankValues = blank.toString();

                int total = 4 * frames;
                int done = 0;
                progress.message("Writing unsubtracted and subtracted frames");
                done = writeFrameBlock(out, data.unsubtracted, blankValues, progress, done, total);
                done = writeFrameBlock(out, data.unsubtractedErrors, blankValues, progress, done, total);
                done = writeFrameBlock(out, data.subtracted, blankValues, progress, done, total);
                writeFrameBlock(out, data.subtractedErrors, blankValues, progress, done, total);

                out.write(vectorLine(intensities(data.averagedBuffer)) + EOL);
                out.write(vectorLine(intensities(data.averagedBufferErrors)) + EOL);
            }

            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }

        progress.reset();
        return format;
    }

    private static int writeFrameBlock(Writer out, java.util.List<double[]> block, String blank,
                                       ProgressListener progress, int done, int total) throws IOException {
        for (int i = 0; i < block.size(); i++) {
            double[] values = block.get(i);
            out.write(frameLine(i, values == null ? blank : intensities(values)));
            out.write(EOL);
            progress.progress(++done, total);
        }
        return done;
    }

    static String intensities(double[] values) {
        StringBuilder sb = new StringBuilder(values.length * 11);
        for (double v : values) sb.append(convertIntensityToString(v));
        return sb.toString();
    }
}
