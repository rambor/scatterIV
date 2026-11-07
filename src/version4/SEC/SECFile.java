package version4.SEC;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import version4.sasCIF.SasObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * An open *.sec file: header, summary lines (signal, Rg, I(0), buffer flags) and random access to frames.
 *
 * <p>Reading goes through {@link SecFileReader} (byte-accurate line index, UTF-8, LF or CRLF, no memory
 * mapping) and edits through {@link SecFileEditor} (in place when the new line fits, otherwise one
 * atomic rewrite). Both legacy (version 1) and version 2 files are supported; edits keep the file's own
 * version and line terminator.</p>
 *
 * <p>The JFreeChart series exposed here are for the Swing UI; the engine classes do not depend on them.</p>
 */
public class SECFile {

    private final String filename, filebase, absolutePath, parentPath;
    private final java.nio.file.Path source;   // the file the user opened (.sec or .sec.gz)
    private final java.nio.file.Path working;  // the uncompressed file actually read and edited
    private final boolean compressed;
    private final SecFileReader reader;
    private final SecFileEditor editor;

    private final SasObject sasObject;
    private final SecFormat secFormat;

    private ArrayList<Double> qvalues;
    private int totalQValues;
    private ArrayList<Integer> frame_indices;

    private ArrayList<Double> rgvalues;
    private ArrayList<Double> rgErrorValues;
    private ArrayList<Double> iZerovalues;
    private ArrayList<Double> iZeroErrorValues;

    private XYSeries selectedBuffers;
    private XYSeriesCollection signalCollection;
    private XYSeries signalSeries; // integrated ratio of each frame to the buffer, x = frame index
    private final XYSeries frame = new XYSeries("frame");

    private final int totalFrames;

    public SECFile(File file) throws IOException {
        this.filename = file.getName();
        this.absolutePath = file.getAbsolutePath();
        this.parentPath = (file.getParent() == null) ? " " : file.getParent();
        this.filebase = SecGzip.stripGzipExtension(file.getName()).split("\\.(?=[^\\.]+$)")[0];

        this.source = file.toPath().toAbsolutePath();
        this.compressed = SecGzip.isGzip(source);
        this.working = compressed ? SecGzip.decompressToTemp(source) : source;

        SecFileReader opened;
        try {
            opened = new SecFileReader(working);
        } catch (IOException | RuntimeException e) {
            if (compressed) java.nio.file.Files.deleteIfExists(working);
            throw e;
        }
        this.reader = opened;
        this.editor = new SecFileEditor(reader);
        try {
            this.sasObject = reader.getSasObject();
            this.secFormat = reader.getSecFormat();

            loadSignal();

            if (secFormat.getRg_index() > 0) {
                rgvalues = toList(reader.readVector(secFormat.getRg_index(), 0.0));
                iZerovalues = toList(reader.readVector(secFormat.getIzero_index(), 0.0));
            }
            if (secFormat.getIzero_error_index() > 0) {
                iZeroErrorValues = toList(reader.readVector(secFormat.getIzero_error_index(), 0.0));
            }
            if (secFormat.getRg_error_index() > 0) {
                rgErrorValues = toList(reader.readVector(secFormat.getRg_error_index(), 0.0));
            }
        } catch (IOException | RuntimeException e) {
            reader.close();
            if (compressed) java.nio.file.Files.deleteIfExists(working);
            throw e;
        }

        totalFrames = signalSeries.getItemCount();
        if (secFormat.getTotal_frames() != totalFrames) {
            System.out.println("SECFile: header total_frames " + secFormat.getTotal_frames()
                    + " differs from " + totalFrames + " frames in file; using " + totalFrames);
        }
    }

    private static ArrayList<Double> toList(double[] values) {
        ArrayList<Double> list = new ArrayList<>(values.length);
        for (double v : values) list.add(v);
        return list;
    }

    private void loadSignal() throws IOException {
        int[] frames = reader.readIntVector(secFormat.getFrame_index());
        double[] signals = reader.readVector(secFormat.getSignal_index(), 0.0);
        int[] background = reader.readIntVector(secFormat.getBackground_index());
        if (signals.length < frames.length || background.length < frames.length) {
            throw new IOException(filename + ": signal or buffer line is shorter than the frame line");
        }

        frame_indices = new ArrayList<>(frames.length);
        signalCollection = new XYSeriesCollection();
        signalSeries = new XYSeries("Signal");
        selectedBuffers = new XYSeries("Selected Buffers");

        for (int i = 0; i < frames.length; i++) {
            frame_indices.add(frames[i]);
            XYSeries last = new XYSeries("frame " + i);
            last.add(frames[i], signals[i]);
            signalCollection.addSeries(last);
            signalSeries.add(last.getDataItem(0));
            if (background[i] > 0) {
                selectedBuffers.add(last.getDataItem(0));
            }
        }

        qvalues = toList(reader.readVector(secFormat.getMomentum_transfer_vector_index(), Double.NaN));
        totalQValues = qvalues.size();
    }

    // ------------------------------------------------------------------ frames

    private ArrayList<Double> frameAt(int blockStart, int index) {
        if (index < 0 || index >= totalFrames) {
            throw new IndexOutOfBoundsException("Frame " + index + " requested, file has " + totalFrames);
        }
        try {
            return toList(reader.readFrame(blockStart, index));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Could not read frame " + index + " of " + filename, e);
        }
    }

    /** Unsubtracted intensities of a frame. Values written as '.' are returned as NaN. */
    public ArrayList<Double> getUnSubtractedFrameAt(int index) {
        return frameAt(secFormat.getUnsubtracted_intensities_index(), index);
    }

    public ArrayList<Double> getUnSubtractedErrorFrameAt(int index) {
        return frameAt(secFormat.getUnsubtracted_intensities_error_index(), index);
    }

    /** Buffer-subtracted intensities of a frame; frames that were not used are all NaN. */
    public ArrayList<Double> getSubtractedFrameAt(int index) {
        return frameAt(secFormat.getSubtracted_intensities_index(), index);
    }

    public ArrayList<Double> getSubtractedErrorAtFrame(int index) {
        return frameAt(secFormat.getSubtracted_intensities_error_index(), index);
    }

    public XYSeries getUnsubtractedXYSeries(int frameByIndex) {
        ArrayList<Double> data = this.getUnSubtractedFrameAt(frameByIndex);
        XYSeries tempXY = new XYSeries("temp"); // can load multiple in graph as they will have same name/key
        for (int i = 0; i < totalQValues; i++) {
            tempXY.add(qvalues.get(i), data.get(i));
        }
        return tempXY;
    }

    /** @deprecated never implemented; returns an empty series. Use {@link #getUnsubtractedXYSeries(int)}. */
    @Deprecated
    public XYSeries getSeriesAtFrame(int frameIndex) {
        return frame;
    }

    // ------------------------------------------------------------------ summary values

    public double getRgbyIndex(int index) { return rgvalues.get(index); }

    /** Missing values are 0. */
    public double getIzerobyIndex(int index) { return iZerovalues.get(index); }

    public double getRgErrorbyIndex(int index) { return rgErrorValues.get(index); }

    public double getIzeroErrorbyIndex(int index) { return iZeroErrorValues.get(index); }

    public XYSeriesCollection getSignalCollection() { return signalCollection; }

    /** x = frame index starting from 0, y = integrated signal. */
    public XYSeries getSignalSeries() { return signalSeries; }

    public XYSeries getBackground() { return selectedBuffers; }

    public SasObject getSasObject() { return sasObject; }

    public ArrayList<Double> getQvalues() { return qvalues; }

    public int getFrameByIndex(int index) { return frame_indices.get(index); }

    public int getTotalQValues() { return qvalues.size(); }

    /** Number of frames actually stored in the file. */
    public int getTotalFrames() { return totalFrames; }

    public double getThreshold() { return secFormat.getThreshold(); }

    /** Format version of the file on disk (1 = written before versioning). */
    public int getFormatVersion() { return reader.getFormatVersion(); }

    /** Number of frames flagged as buffer, read from the file. */
    public int getBufferCount() {
        try {
            int sum = 0;
            for (int flag : reader.readIntVector(secFormat.getBackground_index())) sum += flag;
            return sum;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * Index of the q-value closest to {@code qvalue}, clamped to the available range.
     * Ties go to the lower index.
     */
    public int getClosestIndex(double qvalue) {
        if (totalQValues == 0) throw new IllegalStateException("No q-values");
        if (qvalue <= qvalues.get(0)) return 0;
        if (qvalue >= qvalues.get(totalQValues - 1)) return totalQValues - 1;

        int lo = 0, hi = totalQValues - 1; // invariant: q[lo] < qvalue < q[hi] or equal at lo
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (qvalues.get(mid) <= qvalue) lo = mid; else hi = mid;
        }
        double below = qvalue - qvalues.get(lo);
        double above = qvalues.get(hi) - qvalue;
        return (above < below) ? hi : lo;
    }

    /**
     * Checksum every data line.
     * @return line numbers whose checksum does not match (empty if the file is intact)
     */
    public List<Integer> validate(ProgressListener listener) throws IOException {
        return reader.validate(listener);
    }

    // ------------------------------------------------------------------ edits

    private static String vector(String values) {
        return SecFileReader.checksum(values) + " " + values;
    }

    private void replace(Map<Integer, String> lines) {
        try {
            editor.replaceLines(lines);
            if (compressed) SecGzip.compress(working, source); // keep the .gz on disk up to date
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Could not update " + filename, e);
        }
    }

    private void replace(int line, String content) {
        Map<Integer, String> one = new TreeMap<>();
        one.put(line, content);
        replace(one);
    }

    /** Mark the given frames as buffer (and all others as not buffer). */
    public void updateBufferIndices(SortedSet<Integer> newBufferIndices) {
        int[] flags = new int[totalFrames];
        selectedBuffers.clear();
        for (Integer newIndex : newBufferIndices) {
            selectedBuffers.add(signalSeries.getDataItem(newIndex));
            flags[newIndex] = 1;
        }
        StringBuilder bufferLine = new StringBuilder(totalFrames * 2);
        for (int flag : flags) bufferLine.append(SecFileWriter.convertSingleBinaryToString(flag));
        replace(secFormat.getBackground_index(), vector(bufferLine.toString()));
    }

    /** @param newLine values only, e.g. "1.234E+01 2.345E+01 " */
    public void updateIZeroLine(String newLine) { replace(secFormat.getIzero_index(), vector(newLine)); }

    public void updateqIqLine(String newLine) { replace(secFormat.getIntegrated_qIq_index(), vector(newLine)); }

    public void updateRgLine(String newLine) { replace(secFormat.getRg_index(), vector(newLine)); }

    public void updateSignalLine(String newLine) { replace(secFormat.getSignal_index(), vector(newLine)); }

    public void updateSubtractedFrame(int indexOfFrame, String newSub, String newSubSigmas) {
        Map<Integer, String> lines = new TreeMap<>();
        lines.put(secFormat.getSubtracted_intensities_index() + indexOfFrame,
                indexOfFrame + " " + SecFileReader.checksum(newSub) + " " + newSub);
        lines.put(secFormat.getSubtracted_intensities_error_index() + indexOfFrame,
                indexOfFrame + " " + SecFileReader.checksum(newSubSigmas) + " " + newSubSigmas);
        replace(lines);
    }

    public void updateAveragedBuffer(String newAveragedBuffer, String newSigmas) {
        Integer avg = secFormat.getAveraged_buffer_index();
        Integer avgErr = secFormat.getAveraged_buffer_error_index();
        if (avg == null || avgErr == null) {
            throw new IllegalStateException(filename + " has no averaged buffer lines");
        }
        Map<Integer, String> lines = new TreeMap<>();
        lines.put(avg, vector(newAveragedBuffer));
        lines.put(avgErr, vector(newSigmas));
        replace(lines);
    }

    /** Write the current {@link #getSasObject()} (e.g. after editing sample metadata) to the header line. */
    public void updateHeader() {
        try {
            replace(0, SecFileWriter.headerJson(sasObject, reader.getFormatVersion()));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * Store new Rg/I(0) results and the threshold they were computed with, in one edit.
     * Error lines are only written if the file has them (legacy files may not).
     */
    public void updateGuinier(double threshold, SecProcessor.GuinierResults results) {
        writeGuinier(reader, sasObject, threshold, results);
    }

    static void writeGuinier(SecFileReader reader, SasObject header, double threshold, SecProcessor.GuinierResults r) {
        SecFormat format = header.getSecFormat();
        format.setThreshold(threshold);

        Map<Integer, String> lines = new TreeMap<>();
        try {
            lines.put(0, SecFileWriter.headerJson(header, reader.getFormatVersion()));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        lines.put(format.getRg_index(), vector(e3(r.rg)));
        lines.put(format.getIzero_index(), vector(e3(r.izero)));
        if (format.getRg_error_index() > 0) lines.put(format.getRg_error_index(), vector(e3(r.rgError)));
        if (format.getIzero_error_index() > 0) lines.put(format.getIzero_error_index(), vector(e3(r.izeroError)));

        try {
            new SecFileEditor(reader).replaceLines(lines);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String e3(double[] values) {
        StringBuilder sb = new StringBuilder(values.length * SecFileWriter.E3_WIDTH);
        for (double v : values) sb.append(SecFileWriter.convertDoubleTo3EString(v));
        return sb.toString();
    }

    // ------------------------------------------------------------------ misc

    public String getFilename() { return filename; }

    public String getFilebase() { return filebase; }

    public String getAbsolutePath() { return absolutePath; }

    public String getParentPath() { return parentPath; }

    /** Line terminator used by this file. */
    public String getNew_line_separator() { return reader.getLineSeparator(); }

    /** JSON of the SasObject (without the file-version key). */
    public String getSasObjectJSON() {
        try {
            return SecFileWriter.sasObjectJson(sasObject);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void closeFile() {
        try {
            reader.close();
            if (compressed) java.nio.file.Files.deleteIfExists(working);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ compression

    /** True for names ending in .sec or .sec.gz (any case). */
    public static boolean isSecFileName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".sec") || lower.endsWith(".sec" + SecGzip.EXTENSION);
    }

    /** The uncompressed file being read (a temporary file when the source is .sec.gz). */
    java.nio.file.Path getWorkingPath() { return working; }

    /** True if the file the user opened is gzip-compressed. */
    public boolean isCompressed() { return compressed; }

    /**
     * Save a copy of this file, compressed (.sec.gz) or not (.sec). The open file is not changed.
     * @throws IOException if {@code target} is the open file itself
     */
    public void exportCopy(File target, boolean compress) throws IOException {
        java.nio.file.Path out = target.toPath().toAbsolutePath();
        if (out.equals(source) || out.equals(working)) {
            throw new IOException("Choose a different file name: " + target.getName() + " is the open file");
        }
        if (compress) {
            SecGzip.compress(working, out);
        } else {
            SecGzip.copy(working, out);
        }
    }
}
