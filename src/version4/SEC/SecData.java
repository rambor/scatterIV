package version4.SEC;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that goes into a *.sec file, independent of how it is formatted on disk.
 * Produced by {@link SecProcessor} and consumed by {@link SecFileWriter}.
 *
 * Arrays indexed by frame have length {@link #getTotalFrames()}; intensity arrays have one
 * value per q in {@link #getQvalues()}.
 */
public class SecData {

    private final double[] qvalues;
    private final int totalFrames;
    private double threshold;

    final double[] signal;
    final double[] integratedQIq;
    final double[] rg;
    final double[] rgError;
    final double[] izero;
    final double[] izeroError;
    final int[] isBuffer;

    final List<double[]> unsubtracted;
    final List<double[]> unsubtractedErrors;
    /** null entries mean "no subtracted data for this frame" and are written as '.' */
    final List<double[]> subtracted;
    final List<double[]> subtractedErrors;

    double[] averagedBuffer;
    double[] averagedBufferErrors;

    public SecData(double[] qvalues, int totalFrames) {
        this.qvalues = qvalues.clone();
        this.totalFrames = totalFrames;
        signal = new double[totalFrames];
        integratedQIq = new double[totalFrames];
        rg = new double[totalFrames];
        rgError = new double[totalFrames];
        izero = new double[totalFrames];
        izeroError = new double[totalFrames];
        isBuffer = new int[totalFrames];
        unsubtracted = nulls(totalFrames);
        unsubtractedErrors = nulls(totalFrames);
        subtracted = nulls(totalFrames);
        subtractedErrors = nulls(totalFrames);
    }

    private static List<double[]> nulls(int n) {
        List<double[]> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(null);
        return list;
    }

    public double[] getQvalues() { return qvalues; }
    public int getTotalFrames() { return totalFrames; }
    public int getTotalQ() { return qvalues.length; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public void setFrameSummary(int frame, double signal, double integratedQIq, double rg, double rgError,
                                double izero, double izeroError, boolean isBuffer) {
        this.signal[frame] = signal;
        this.integratedQIq[frame] = integratedQIq;
        this.rg[frame] = rg;
        this.rgError[frame] = rgError;
        this.izero[frame] = izero;
        this.izeroError[frame] = izeroError;
        this.isBuffer[frame] = isBuffer ? 1 : 0;
    }

    public void setUnsubtracted(int frame, double[] intensities, double[] errors) {
        unsubtracted.set(frame, checkLength(intensities));
        unsubtractedErrors.set(frame, checkLength(errors));
    }

    public void setSubtracted(int frame, double[] intensities, double[] errors) {
        subtracted.set(frame, intensities == null ? null : checkLength(intensities));
        subtractedErrors.set(frame, errors == null ? null : checkLength(errors));
    }

    public void setAveragedBuffer(double[] intensities, double[] errors) {
        averagedBuffer = checkLength(intensities);
        averagedBufferErrors = checkLength(errors);
    }

    private double[] checkLength(double[] values) {
        if (values == null || values.length != qvalues.length) {
            throw new IllegalArgumentException("Expected " + qvalues.length + " values, got "
                    + (values == null ? "null" : values.length));
        }
        return values;
    }

    public double getSignal(int frame) { return signal[frame]; }
    public double getRg(int frame) { return rg[frame]; }
    public double getIzero(int frame) { return izero[frame]; }
    public boolean isBuffer(int frame) { return isBuffer[frame] == 1; }
}
