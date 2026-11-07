package version4.SEC;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.AutoRg;
import version4.Collection;
import version4.Dataset;
import version4.Functions;

import java.util.*;

/**
 * SEC-SAXS processing, with no user-interface code:
 * <ol>
 *   <li>find the q-values common to all frames</li>
 *   <li>estimate (or accept) the buffer frames and average them</li>
 *   <li>subtract the averaged buffer from every frame, compute the signal and integrated qI(q)</li>
 *   <li>run AutoRg on frames whose signal exceeds the threshold</li>
 * </ol>
 * The result is a {@link SecData} ready for {@link SecFileWriter}.
 *
 * Progress is reported through a {@link ProgressListener}, so the same code can sit behind a Swing
 * SwingWorker, a JavaFX Task or a command-line tool.
 */
public class SecProcessor {

    /** Number of consecutive frames compared when looking for buffer regions. */
    public static final int WINDOW = 37;
    /** Fewest buffer frames accepted before the tolerance is relaxed. */
    public static final int MIN_BUFFER_FRAMES = 6;
    /** Fraction of q-values that must be common to all frames. */
    public static final double MIN_COMMON_Q_FRACTION = 0.6;

    private final Collection collection;
    private final ProgressListener progress;
    private final double threshold;
    private int excludePoints;

    private final ArrayList<Number> qvalues;
    private final Number minQvalueInCommon;
    private final Number maxQvalueInCommon;
    private double noSignal;

    private double backgroundSpread = 0.2;
    private double minSpread = 10;

    private TreeSet<Integer> keptBuffers;
    private TreeSet<Integer> bufferRejects;
    private XYSeries buffer;
    private XYSeries bufferError;

    /**
     * @param collection    frames, in acquisition order
     * @param qvalues       q-values common to all frames in use (see {@link #findCommonQValues})
     * @param threshold     frames with signal above this get AutoRg
     * @param excludePoints low-q points skipped by AutoRg
     */
    public SecProcessor(Collection collection, List<? extends Number> qvalues, double threshold, int excludePoints, ProgressListener listener) {
        if (qvalues.isEmpty()) throw new IllegalArgumentException("No q-values");
        this.collection = collection;
        this.qvalues = new ArrayList<>(qvalues);
        this.threshold = threshold;
        this.excludePoints = excludePoints;
        this.progress = ProgressListener.orNone(listener);
        this.minQvalueInCommon = this.qvalues.get(0);
        this.maxQvalueInCommon = this.qvalues.get(this.qvalues.size() - 1);
        int totalN = this.qvalues.size();
        this.noSignal = (maxQvalueInCommon.doubleValue() - minQvalueInCommon.doubleValue()) * (totalN + 1) / (double) totalN;
    }

    public void setExcludePoints(int excludePoints) { this.excludePoints = excludePoints; }

    public List<Number> getQvalues() { return Collections.unmodifiableList(qvalues); }

    public SortedSet<Integer> getBufferFrames() { return Collections.unmodifiableSortedSet(keptBuffers); }

    public XYSeries getAveragedBuffer() { return buffer; }

    public XYSeries getAveragedBufferError() { return bufferError; }

    // ------------------------------------------------------------------ q-values

    /** Result of {@link #findCommonQValues}. */
    public static class CommonQ {
        public final ArrayList<Number> qvalues = new ArrayList<>();
        public final Map<String, Integer> framesMissingQ = new LinkedHashMap<>();
        public int totalInFirstFrame;

        public double fractionInCommon() {
            return totalInFirstFrame == 0 ? 0 : qvalues.size() / (double) totalInFirstFrame;
        }

        public boolean isAcceptable() {
            return !qvalues.isEmpty() && fractionInCommon() >= MIN_COMMON_Q_FRACTION;
        }

        public String describeProblem() {
            StringBuilder sb = new StringBuilder(" too few values in common => "
                    + String.format("%.2f of TOTAL :: %s %n", fractionInCommon(), qvalues.size()));
            for (Map.Entry<String, Integer> cf : framesMissingQ.entrySet()) {
                sb.append(String.format("frame => %s missing %d q-values %n", cf.getKey(), cf.getValue()));
            }
            return sb.toString();
        }
    }

    /**
     * Starting with the first dataset, keep each q-value that is present in every other dataset in use.
     */
    public static CommonQ findCommonQValues(Collection collection) {
        CommonQ result = new CommonQ();
        XYSeries baseSet = collection.getDataset(0).getAllData();
        int total = baseSet.getItemCount();
        result.totalInFirstFrame = total;

        for (int i = 0; i < total; i++) {
            XYDataItem baseItem = baseSet.getDataItem(i);
            int notFoundIndex = -1;
            for (int d = 1; d < collection.getTotalDatasets(); d++) {
                Dataset testset = collection.getDataset(d);
                if (testset.getInUse() && testset.getAllData().indexOf(baseItem.getX()) < 0) {
                    notFoundIndex = d;
                    break;
                }
            }

            if (notFoundIndex < 0) {
                result.qvalues.add(baseItem.getX());
            } else {
                result.framesMissingQ.merge(collection.getDataset(notFoundIndex).getFileName(), 1, Integer::sum);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ buffer selection

    /** Use the given frames as buffer. */
    public void useBufferFrames(SortedSet<Integer> bufferIndices) {
        progress.indeterminate(true);
        progress.message("Setting background frames");

        keptBuffers = new TreeSet<>(bufferIndices);
        bufferRejects = new TreeSet<>();
        for (int m = 0; m < collection.getTotalDatasets(); m++) {
            Dataset dd = collection.getDataset(m);
            if (dd.getInUse() && !keptBuffers.contains(dd.getId())) {
                bufferRejects.add(dd.getId());
            }
        }
        averageBufferFrames();
    }

    /**
     * Estimate buffer frames: slide a window of {@value #WINDOW} frames along the run and accept windows
     * whose frames are mutually similar (small spread of Durbin-Watson statistics of their residuals).
     * The tolerance is relaxed until at least {@value #MIN_BUFFER_FRAMES} frames are found; if that fails,
     * the first 10% of frames are used.
     */
    public void estimateBufferFrames() {
        progress.indeterminate(true);
        progress.message("Estimating background frames");

        final int totalInCollection = collection.getTotalDatasets();
        final int lowerIndex = collection.getDataset(0).getAllData().indexOf(minQvalueInCommon);
        final int upperIndex = collection.getDataset(0).getAllData().indexOf(maxQvalueInCommon);

        // The spread of each window does not depend on the tolerance, so compute it once.
        // (Previously every relaxation step recomputed all windows.)
        int firstWindowEnd = WINDOW;
        int lastWindowEnd = totalInCollection - WINDOW; // exclusive; unchanged from the original scan range
        int windows = Math.max(0, lastWindowEnd - firstWindowEnd);
        double[] spreads = new double[windows];
        for (int k = 0; k < windows; k++) {
            int w = firstWindowEnd + k;
            ArrayList<XYSeries> collectionWindow = new ArrayList<>();
            for (int m = (w - WINDOW); m < w; m++) {
                Dataset dd = collection.getDataset(m);
                if (dd.getInUse()) collectionWindow.add(dd.getAllData());
            }
            double spread = durbinWatsonSpread(collectionWindow, lowerIndex, upperIndex);
            if (spread < minSpread) minSpread = spread;
            spreads[k] = spread;
        }

        keptBuffers = new TreeSet<>();
        bufferRejects = new TreeSet<>();
        classifyWindows(spreads, firstWindowEnd);

        if (keptBuffers.isEmpty()) {
            backgroundSpread = minSpread;
        }

        double step = 0.3 * minSpread;
        while (keptBuffers.size() < MIN_BUFFER_FRAMES && backgroundSpread < 10) {
            if (!(step > 0)) break; // all windows identical or undefined: avoid an endless loop
            backgroundSpread += step;
            progress.message(String.format("Estimating background...kept buffers is nil, adjusting tolerance :: %.4f", backgroundSpread));
            classifyWindows(spreads, firstWindowEnd);
        }

        // in case it all fails, use the first 10% of frames as background
        if (keptBuffers.size() < MIN_BUFFER_FRAMES) {
            keptBuffers.clear();
            bufferRejects.clear();
            int tenPercent = (int) (totalInCollection * 0.1) + 1;
            for (int i = 0; i < Math.min(tenPercent, totalInCollection); i++) {
                keptBuffers.add(collection.getDataset(i).getId());
            }
            for (int i = tenPercent; i < totalInCollection; i++) {
                bufferRejects.add(collection.getDataset(i).getId());
            }
        }

        averageBufferFrames();
    }

    /** Same first-come assignment as the original scan: a frame keeps the first verdict it receives. */
    private void classifyWindows(double[] spreads, int firstWindowEnd) {
        keptBuffers.clear();
        bufferRejects.clear();
        for (int k = 0; k < spreads.length; k++) {
            int w = firstWindowEnd + k;
            boolean similar = spreads[k] < backgroundSpread;
            for (int m = (w - WINDOW); m < w; m++) {
                Dataset dd = collection.getDataset(m);
                if (!dd.getInUse()) continue;
                int id = dd.getId();
                if (similar) {
                    if (!bufferRejects.contains(id)) keptBuffers.add(id);
                } else {
                    if (!keptBuffers.contains(id)) bufferRejects.add(id);
                }
            }
        }
    }

    /**
     * Spread (max - min) of the Durbin-Watson statistic of the relative residuals between the first frame
     * in the window and each of the others. Identical frames give a small spread. NaN if fewer than two frames.
     */
    static double durbinWatsonSpread(ArrayList<XYSeries> window, int lowerIndex, int upperIndex) {
        int windowSize = window.size();
        if (windowSize < 2) return Double.NaN;

        double[] values = new double[windowSize - 1];
        double[] residuals = new double[upperIndex - lowerIndex + 1];

        XYSeries refData = window.get(0);
        int totalInRef = refData.getItemCount();
        int upperLimit = ((upperIndex + 1) > totalInRef) ? (totalInRef - 1) : upperIndex;

        for (int j = 1; j < windowSize; j++) {
            XYSeries tempData = window.get(j);
            int tt = 0;
            for (int q = lowerIndex; q < (upperLimit + 1); q++) {
                XYDataItem refXY = refData.getDataItem(q);
                int foundIndex = tempData.indexOf(refXY.getX());
                if (foundIndex > -1) {
                    residuals[tt] = (tempData.getY(foundIndex).doubleValue() - refXY.getYValue()) / refXY.getYValue();
                    tt++;
                }
            }
            values[j - 1] = durbinWatson(residuals, tt);
        }
        return StatUtils.max(values) - StatUtils.min(values);
    }

    /**
     * Durbin-Watson-like statistic: sum (x_t - x_{t-1})^2 / sum x_t^2. ~2 means no autocorrelation.
     */
    static double durbinWatson(double[] residuals, int totalResiduals) {
        double numerator = 0;
        double denominator = residuals[0] * residuals[0];
        for (int i = 1; i < totalResiduals; i++) {
            double value = residuals[i];
            double diff = value - residuals[i - 1];
            numerator += diff * diff;
            denominator += value * value;
        }
        return numerator / denominator;
    }

    private void averageBufferFrames() {
        ArrayList<XYSeries> keptData = new ArrayList<>();
        ArrayList<XYSeries> keptErrors = new ArrayList<>();
        for (Integer kept : keptBuffers) {
            Dataset tempDataset = collection.getDataset(kept);
            keptData.add(tempDataset.getAllData());
            keptErrors.add(tempDataset.getAllDataError());
        }
        if (keptData.isEmpty()) {
            throw new IllegalStateException("No buffer frames selected");
        }

        progress.message("Averaging background frames :: " + keptBuffers.size());
        buffer = new XYSeries("averaged buffer");
        bufferError = new XYSeries("averaged buffer errors");
        averageWithoutScaling(qvalues, keptData, keptErrors, buffer, bufferError);
        progress.indeterminate(false);
        progress.message("Finished averaging buffer");
    }

    /**
     * Inverse-variance weighted average of the given curves at {@code qvalues}. Makes no assumption that
     * the curves share a q-grid: every value is looked up.
     */
    public static void averageWithoutScaling(List<? extends Number> qvalues, List<XYSeries> data, List<XYSeries> errors,
                                             XYSeries output, XYSeries outputErrors) {
        XYSeries datasum = new XYSeries("averaged");
        XYSeries datasumerror = new XYSeries("averagederror");

        XYSeries tempset = data.get(0);
        XYSeries tempsetError = errors.get(0);
        for (Number qv : qvalues) {
            int index = tempset.indexOf(qv);
            if (index > -1) {
                XYDataItem xyDataItem = tempset.getDataItem(index);
                double sigma = 1.0 / tempsetError.getDataItem(index).getYValue();
                double weight = sigma * sigma;
                datasum.add(xyDataItem.getX(), xyDataItem.getYValue() * weight);
                datasumerror.add(xyDataItem.getX(), weight);
            }
        }

        int totalIn = datasum.getItemCount();
        for (int d = 1; d < data.size(); d++) {
            tempset = data.get(d);
            tempsetError = errors.get(d);
            for (int i = 0; i < totalIn; i++) {
                XYDataItem refItem = datasum.getDataItem(i);
                int index = tempset.indexOf(refItem.getX());
                if (index > -1) {
                    double tderror = tempsetError.getDataItem(index).getYValue();
                    double weight = 1.0 / (tderror * tderror);
                    datasum.updateByIndex(i, tempset.getY(index).doubleValue() * weight + refItem.getYValue());
                    datasumerror.updateByIndex(i, weight + datasumerror.getY(i).doubleValue());
                }
            }
        }

        output.clear();
        outputErrors.clear();
        for (int i = 0; i < totalIn; i++) {
            XYDataItem base = datasum.getDataItem(i);
            double weight = datasumerror.getY(i).doubleValue();
            output.add(base.getX(), base.getYValue() / weight);
            outputErrors.add(base.getX(), 1.0 / Math.sqrt(weight));
        }
    }

    // ------------------------------------------------------------------ subtraction and signal

    /**
     * Subtract the buffer at every q present in both; errors add in quadrature.
     * @return [subtracted intensities, subtracted errors]
     */
    static XYSeries[] subtract(XYSeries sample, XYSeries sampleError, XYSeries buffer, XYSeries bufferError) {
        XYSeries subData = new XYSeries("subtracted");
        XYSeries subError = new XYSeries("errorSubtracted");
        int tempTotal = sample.getItemCount();
        for (int q = 0; q < tempTotal; q++) {
            XYDataItem tempDataItem = sample.getDataItem(q);
            double qValue = tempDataItem.getXValue();
            int indexOf = buffer.indexOf(qValue);
            if (indexOf > -1) {
                double sigma = sampleError.getY(q).doubleValue();
                double bufferSigma = bufferError.getY(indexOf).doubleValue();
                subData.add(qValue, tempDataItem.getYValue() - buffer.getY(indexOf).doubleValue());
                subError.add(qValue, Math.sqrt(sigma * sigma + bufferSigma * bufferSigma));
            }
        }
        return new XYSeries[]{subData, subError};
    }

    /**
     * Subtract the averaged buffer from every frame, compute signal and integrated qI(q), run AutoRg on frames
     * above the threshold, and collect everything needed for the file.
     * Requires {@link #estimateBufferFrames()} or {@link #useBufferFrames(SortedSet)} first.
     */
    public SecData process() {
        if (buffer == null) throw new IllegalStateException("Buffer frames not set");

        int totalInCollection = collection.getTotalDatasets();
        double[] q = new double[qvalues.size()];
        for (int i = 0; i < q.length; i++) q[i] = qvalues.get(i).doubleValue();

        SecData data = new SecData(q, totalInCollection);
        data.setThreshold(threshold);

        progress.message("Estimating signal and performing subtraction... please wait");
        XYSeries ratio = new XYSeries("ratio");
        XYSeries qIqData = new XYSeries("qIq");
        boolean[] summarySet = new boolean[totalInCollection];

        for (int i = 0; i < totalInCollection; i++) {
            Dataset tempDataset = collection.getDataset(i);
            XYSeries allData = tempDataset.getAllData();
            XYSeries allErrors = tempDataset.getAllDataError();

            // unsubtracted frame, bounded to the common q-values
            double[] unsub = new double[q.length];
            double[] unsubErr = new double[q.length];
            for (int k = 0; k < q.length; k++) {
                int index = allData.indexOf(qvalues.get(k));
                // frames not in use were not checked for common q-values; mark any gap as missing
                unsub[k] = (index < 0) ? Double.NaN : allData.getY(index).doubleValue();
                unsubErr[k] = (index < 0) ? Double.NaN : allErrors.getY(index).doubleValue();
            }
            data.setUnsubtracted(i, unsub, unsubErr);

            if (tempDataset.getInUse()) {
                // signal: integral of the ratio of frame to buffer
                ratio.clear();
                for (Number qv : qvalues) {
                    int index = allData.indexOf(qv);
                    if (index > -1) {
                        XYDataItem tempXY = allData.getDataItem(index);
                        ratio.add(tempXY.getXValue(), tempXY.getYValue() / buffer.getY(buffer.indexOf(qv)).doubleValue());
                    }
                }
                double signal = Functions.trapezoid_integrate(ratio) / noSignal;

                int row = tempDataset.getId();
                boolean isBuffer = keptBuffers.contains(row);

                XYSeries[] subtraction = subtract(allData, allErrors, buffer, bufferError);
                XYSeries subtracted = subtraction[0];
                XYSeries suberrors = subtraction[1];

                double rg = 0, izero = 0, rgError = 0, izeroError = 0;
                if (!isBuffer && signal > threshold) {
                    AutoRg temp = new AutoRg(subtracted, excludePoints);
                    rg = temp.getRg();
                    izero = temp.getI_zero();
                    rgError = temp.getRg_error();
                    izeroError = temp.getI_zero_error();
                    if (Double.isNaN(rg)) {
                        System.out.println(row + " NAN " + rg + " " + izero + " " + signal);
                    }
                }

                qIqData.clear();
                for (int k = 0; k < subtracted.getItemCount(); k++) {
                    XYDataItem tempXY = subtracted.getDataItem(k);
                    qIqData.add(tempXY.getX(), tempXY.getYValue() * tempXY.getXValue());
                }
                double qIq = Functions.trapezoid_integrate(qIqData);

                if (row >= 0 && row < totalInCollection) {
                    data.setFrameSummary(row, signal, qIq, rg, rgError, izero, izeroError, isBuffer);
                    summarySet[row] = true;
                }

                double[] sub = new double[q.length];
                double[] subErr = new double[q.length];
                for (int k = 0; k < q.length; k++) {
                    int index = subtracted.indexOf(qvalues.get(k));
                    sub[k] = subtracted.getY(index).doubleValue();
                    subErr[k] = suberrors.getY(index).doubleValue();
                }
                data.setSubtracted(i, sub, subErr);
            }
            progress.progress(i + 1, totalInCollection);
        }

        // frames not in use: neutral signal, no Guinier values, not buffer, subtracted written as '.'
        for (int i = 0; i < totalInCollection; i++) {
            if (!summarySet[i]) data.setFrameSummary(i, 1.0, 0, 0, 0, 0, 0, false);
        }

        double[] avg = new double[q.length];
        double[] avgErr = new double[q.length];
        for (int k = 0; k < q.length; k++) {
            avg[k] = buffer.getY(k).doubleValue();
            avgErr[k] = bufferError.getY(k).doubleValue();
        }
        data.setAveragedBuffer(avg, avgErr);

        progress.reset();
        progress.message("Finished creating samples");
        return data;
    }

    // ------------------------------------------------------------------ Guinier only

    /** Rg and I(0) (with errors) for every frame; frames not computed are 0. */
    public static class GuinierResults {
        public final double[] rg, rgError, izero, izeroError;

        GuinierResults(int frames) {
            rg = new double[frames];
            rgError = new double[frames];
            izero = new double[frames];
            izeroError = new double[frames];
        }
    }

    /**
     * Run AutoRg on each dataset of {@code subtractedFrames}; each dataset's id is its frame number.
     */
    public static GuinierResults autoRg(Collection subtractedFrames, int totalFrames, int excludePoints, ProgressListener listener) {
        ProgressListener progress = ProgressListener.orNone(listener);
        GuinierResults results = new GuinierResults(totalFrames);
        int total = subtractedFrames.getTotalDatasets();
        progress.message("Calculating Rg and I(0)... please wait");
        for (int i = 0; i < total; i++) {
            Dataset tempData = subtractedFrames.getDataset(i);
            int frame = tempData.getId();
            AutoRg temp = new AutoRg(tempData.getAllData(), excludePoints);
            results.rg[frame] = temp.getRg();
            results.rgError[frame] = temp.getRg_error();
            results.izero[frame] = temp.getI_zero();
            results.izeroError[frame] = temp.getI_zero_error();
            progress.progress(i + 1, total);
        }
        progress.reset();
        return results;
    }
}
