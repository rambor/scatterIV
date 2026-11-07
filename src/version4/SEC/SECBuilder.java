package version4.SEC;

import org.jfree.data.xy.XYSeries;
import version4.Collection;
import version4.Dataset;
import version4.sasCIF.SasObject;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Swing front end for creating or updating a *.sec file.
 *
 * <p>All processing lives in {@link SecProcessor} and all file I/O in {@link SecFileWriter} /
 * {@link SecFileEditor}; this class only gathers the inputs, runs the work in {@link #doInBackground()} and
 * reports progress to the given JLabel/JProgressBar on the Event Dispatch Thread. A JavaFX front end can do
 * the same with a {@code javafx.concurrent.Task} and its own {@link ProgressListener}.</p>
 *
 * Three modes, one per constructor:
 * <ol>
 *   <li>new file from a collection of frames (buffer frames estimated)</li>
 *   <li>rebuild an existing file with a new buffer selection (every frame re-subtracted)</li>
 *   <li>update Rg/I(0) only, after a threshold or excluded-points change (edits the file in place)</li>
 * </ol>
 */
public class SECBuilder extends SwingWorker<Void, Integer> {

    private enum Mode { NEW, REBUILD, GUINIER_ONLY }

    private final Mode mode;
    private final Collection collection;
    private final ProgressListener progress;
    private final double threshold;
    private int excludePoints = 13;

    private ArrayList<Number> qvalues;
    private SortedSet<Integer> bufferIndices;   // REBUILD
    private String outputname;
    private final String workingDirectory;
    private boolean compressOutput;             // write .sec.gz instead of .sec

    // GUINIER_ONLY
    private String secfilename;
    private SasObject sasObject;
    private int totalFrames;

    /**
     * New SEC file from a collection of frames.
     *
     * @param newSecfilename       output name; if empty a name is derived from the first frame.
     *                             A name ending in ".gz" (e.g. "run.sec.gz") writes a compressed file.
     * @param workingDirectoryName directory for the output
     * @param threshold            signal threshold for AutoRg, typically > 1.003
     * @throws IOException if the run has fewer than {@link SecProcessor#MIN_FRAMES} frames in use,
     *                     or the frames do not share enough q-values
     */
    public SECBuilder(Collection collection, JLabel status, JProgressBar bar, String newSecfilename,
                      String workingDirectoryName, double threshold) throws IOException {
        this.mode = Mode.NEW;
        this.collection = collection;
        this.threshold = threshold;
        this.progress = new SwingProgressListener(status, bar);
        this.workingDirectory = workingDirectoryName;

        int inUse = SecProcessor.framesInUse(collection);
        if (inUse < SecProcessor.MIN_FRAMES) {
            progress.message(SecProcessor.tooShortMessage(inUse));
            throw new IOException(SecProcessor.tooShortMessage(inUse));
        }

        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(collection);
        if (!common.isAcceptable()) {
            progress.message("Improper dataset for SEC SAXS :: Run Scatter via console and examine output");
            throw new IOException("Improper dataset for SEC SAXS :: " + common.describeProblem());
        }
        this.qvalues = common.qvalues;

        String requested = (newSecfilename == null) ? "" : newSecfilename.trim();
        if (requested.toLowerCase().endsWith(SecGzip.EXTENSION)) { // "name.sec.gz" or "name.gz" asks for compression
            compressOutput = true;
            requested = SecGzip.stripGzipExtension(requested);
        }
        if (requested.toLowerCase().endsWith(".sec")) requested = requested.substring(0, requested.length() - 4);

        this.outputname = requested.isEmpty()
                ? deriveOutputName(collection.getDataset(0).getFileName())
                : requested;
    }

    /**
     * Rebuild an existing SEC file with new buffer frames: every frame is re-subtracted and Rg/I(0) re-determined.
     * All frames are read here, so the old file may be closed before {@link #run()}.
     */
    public SECBuilder(SECFile oldsecfile, SortedSet<Integer> bufferIndices, JLabel status, JProgressBar bar,
                      String workingDirectoryName, double threshold) throws IOException {
        this.mode = Mode.REBUILD;
        this.threshold = threshold;
        this.progress = new SwingProgressListener(status, bar);
        this.workingDirectory = workingDirectoryName;
        this.bufferIndices = new TreeSet<>(bufferIndices);
        this.outputname = oldsecfile.getFilebase();
        this.compressOutput = oldsecfile.isCompressed(); // rebuild keeps the original's format
        this.collection = new Collection("rebuild");

        ArrayList<Double> oldq = oldsecfile.getQvalues();
        this.qvalues = new ArrayList<>(oldq);

        int frames = oldsecfile.getTotalFrames();
        progress.message("Reading frames from " + oldsecfile.getFilename());
        XYSeries iofq = new XYSeries("");
        XYSeries eofq = new XYSeries("");
        for (int i = 0; i < frames; i++) {
            ArrayList<Double> frame = oldsecfile.getUnSubtractedFrameAt(i);
            ArrayList<Double> error = oldsecfile.getUnSubtractedErrorFrameAt(i);
            for (int q = 0; q < oldq.size(); q++) {
                if (i == 0) {
                    iofq.add(oldq.get(q), frame.get(q));
                    eofq.add(oldq.get(q), error.get(q));
                } else {
                    iofq.updateByIndex(q, frame.get(q));
                    eofq.updateByIndex(q, error.get(q));
                }
            }
            collection.createDataset(iofq, eofq); // copies the series
            progress.progress(i + 1, frames);
        }
    }

    /**
     * Recalculate Rg and I(0) only, because the threshold or the number of excluded points changed.
     * The subtracted frames above the threshold are read here; the file is updated in {@link #run()}.
     */
    public SECBuilder(SECFile oldsecfile, JLabel status, JProgressBar bar, String workingDirectoryName,
                      double threshold, int excludePoints) throws IOException {
        this.mode = Mode.GUINIER_ONLY;
        this.threshold = threshold;
        this.excludePoints = excludePoints;
        this.progress = new SwingProgressListener(status, bar);
        this.workingDirectory = workingDirectoryName;
        this.secfilename = oldsecfile.getAbsolutePath();
        this.outputname = oldsecfile.getFilebase();
        this.sasObject = new SasObject(oldsecfile.getSasObject());
        this.totalFrames = oldsecfile.getTotalFrames();
        this.collection = new Collection("rebuild");

        ArrayList<Double> oldq = oldsecfile.getQvalues();
        this.qvalues = new ArrayList<>(oldq);
        XYSeries signalSeries = oldsecfile.getSignalSeries();

        XYSeries iofq = new XYSeries("");
        XYSeries eofq = new XYSeries("");
        for (Double q : oldq) {
            iofq.add(q.doubleValue(), 0.0d);
            eofq.add(q.doubleValue(), 0.0d);
        }

        progress.message("Reading frames above threshold");
        for (int i = 0; i < totalFrames; i++) {
            if (signalSeries.getY(i).doubleValue() >= threshold) {
                ArrayList<Double> frame = oldsecfile.getSubtractedFrameAt(i);
                ArrayList<Double> error = oldsecfile.getSubtractedErrorAtFrame(i);
                for (int q = 0; q < oldq.size(); q++) {
                    iofq.updateByIndex(q, frame.get(q));
                    eofq.updateByIndex(q, error.get(q));
                }
                collection.createDataset(iofq, eofq, i);
            }
        }
    }

    /**
     * Derive an output name from a frame filename by dropping a leading or trailing run number,
     * e.g. "23543_BSA_11" -> "BSA_11", "BSA_11_00012" -> "BSA_11".
     */
    static String deriveOutputName(String name) {
        String[] parts = name.split("[-_]+");
        String onlyDigits = "\\d+";
        boolean lastIsNumber = parts[parts.length - 1].matches(onlyDigits);
        boolean firstIsNumber = parts[0].matches(onlyDigits);

        if (parts.length > 1 && lastIsNumber && firstIsNumber) {
            if (parts[parts.length - 1].length() > parts[0].length()) { // assume the last is the run number
                return name.substring(0, name.length() - parts[parts.length - 1].length() - 1);
            }
            return name.substring(parts[0].length() + 1);               // 000001_filename_2
        } else if (parts.length > 1 && lastIsNumber) {
            return name.substring(0, name.length() - parts[parts.length - 1].length() - 1);
        } else if (parts.length > 1 && firstIsNumber) {
            return name.substring(parts[0].length() + 1);
        }
        return name; // previously this produced "null.sec"
    }

    @Override
    protected Void doInBackground() throws Exception {
        try {
            if (mode == Mode.GUINIER_ONLY) {
                updateRgIzero();
            } else {
                buildFile();
            }
        } finally {
            progress.reset();
        }
        return null;
    }

    private void buildFile() throws IOException {
        SecProcessor processor = new SecProcessor(collection, qvalues, threshold, excludePoints, progress);
        if (mode == Mode.REBUILD) {
            processor.useBufferFrames(bufferIndices);
        } else {
            processor.estimateBufferFrames();
        }
        SecData data = processor.process();

        SasObject header = null;
        for (int i = 0; i < collection.getTotalDatasets(); i++) {
            Dataset d = collection.getDataset(i);
            if (d.getSasObject() != null) {
                header = d.getSasObject();
                break;
            }
        }
        if (header == null) header = new SasObject();

        String fileName = outputname + ".sec" + (compressOutput ? SecGzip.EXTENSION : "");
        Path target = (workingDirectory == null || workingDirectory.isEmpty())
                ? Paths.get(fileName)
                : Paths.get(workingDirectory, fileName);
        progress.message("Writing " + target.getFileName());
        if (compressOutput) {
            Path plain = SecFileWriter.siblingTempFile(target.toAbsolutePath());
            try {
                SecFileWriter.write(plain, header, data, progress);
                progress.indeterminate(true);
                progress.message("Compressing " + target.getFileName());
                SecGzip.compress(plain, target);
            } finally {
                java.nio.file.Files.deleteIfExists(plain);
            }
        } else {
            SecFileWriter.write(target, header, data, progress);
        }
        outputname = target.toString();
        progress.message("Finished writing " + target.getFileName());
    }

    private void updateRgIzero() throws IOException {
        SecProcessor.GuinierResults results = SecProcessor.autoRg(collection, totalFrames, excludePoints, progress);
        progress.indeterminate(true);
        progress.message("Updating SEC File... please wait");
        Path file = Paths.get(secfilename);
        boolean gz = SecGzip.isGzip(file);
        Path working = gz ? SecGzip.decompressToTemp(file) : file;
        try {
            try (SecFileReader reader = new SecFileReader(working)) {
                SECFile.writeGuinier(reader, sasObject, threshold, results);
            }
            if (gz) {
                progress.message("Compressing " + file.getFileName());
                SecGzip.compress(working, file);
            }
        } finally {
            if (gz) java.nio.file.Files.deleteIfExists(working);
        }
        progress.indeterminate(false);
        progress.message("Finished updating SEC file");
    }

    /** Weighted average of curves at this builder's common q-values. */
    public void averageWithoutScaling(ArrayList<XYSeries> data, ArrayList<XYSeries> errors, XYSeries output, XYSeries outputErrors) {
        SecProcessor.averageWithoutScaling(qvalues, data, errors, output, outputErrors);
    }

    /** Full path of the written file after {@link #run()} (NEW and REBUILD), otherwise the file base name. */
    public String getOutputname() { return outputname; }

    public void setExcludePoints(int value) { this.excludePoints = value; }

    /** Write the new file gzip-compressed (.sec.gz). Ending the output name in ".gz" does the same. */
    public void setCompressOutput(boolean compress) { this.compressOutput = compress; }

    public boolean isCompressOutput() { return compressOutput; }

    public int getTotalQValues() { return qvalues.size(); }

    // ------------------------------------------------------------------ formatting, kept for existing callers

    public String formattedQ(double qvalue, int numberOfDigits) { return SecFileWriter.formattedQ(qvalue, numberOfDigits); }

    public static String convertSignalToString(double value) { return SecFileWriter.convertSignalToString(value); }

    public static String convertDoubleTo3EString(double value) { return SecFileWriter.convertDoubleTo3EString(value); }

    public static String convertqIqToString(double value) { return SecFileWriter.convertqIqToString(value); }

    public static String convertSingleBinaryToString(int value) { return SecFileWriter.convertSingleBinaryToString(value); }

    public static String convertIntensityToString(double value) { return SecFileWriter.convertIntensityToString(value); }
}
