package version4.SEC;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks that the signal baseline after the elution peak returns to the pre-peak baseline.
 *
 * <p>In a good SEC-SAXS run the integrated signal (ratio of each frame to the averaged buffer) is flat at the
 * buffer level before and after the peaks. If the baseline stays elevated after the peak, something in the
 * beam is scattering more than the buffer did: typically protein that radiation damage has deposited on the
 * capillary wall (fouling), or a change in normalisation or transmission.</p>
 *
 * <p>The check:</p>
 * <ol>
 *   <li>takes the baseline as the median signal of the buffer frames before the peak (a flat line: before
 *       and after the peaks should sit on the same line) and the frame-to-frame noise from their scatter;
 *       buffer frames on the foot of the peak are trimmed so they cannot bias it</li>
 *   <li>finds the highest peak above that line and where it falls back to half height</li>
 *   <li>takes the tail of the run (the last {@value #TAIL_FRACTION_PERCENT}% of frames, at least
 *       {@value #MIN_TAIL_FRAMES}), well after the peak, and measures its median offset from the baseline.
 *       A fitted slope is not extrapolated: over hundreds of frames a tiny slope, e.g. from a buffer selection
 *       that touches the peak, would dominate the result.</li>
 *   <li>finds the unbroken run of frames, ending at the last frame, that stay above (or below) the baseline;
 *       a run that starts at the peak means the signal never came back down</li>
 *   <li>flags the run if the offset is both significant (more than {@value #SIGNIFICANCE} standard errors) and
 *       large ({@value #FLAG_PERCENT_OF_PEAK}% of the peak height or more)</li>
 *   <li>divides the averaged tail frames by the averaged buffer to see where in q the excess is: mostly at low q
 *       means large particles (deposits, aggregates); the same at all q points to normalisation</li>
 * </ol>
 */
public final class BaselineCheck {

    public static final int MIN_TAIL_FRAMES = 20;
    public static final int TAIL_FRACTION_PERCENT = 10;
    public static final double SIGNIFICANCE = 5.0;
    public static final double FLAG_PERCENT_OF_PEAK = 2.0;
    /** Frames in a shifted run must each be more than this many noise from the baseline (after smoothing). */
    public static final double RUN_SIGMA = 3.0;
    /** Width of the running median used when finding the run of shifted frames. */
    public static final int RUN_SMOOTHING = 5;
    /** Low-q excess must exceed the high-q excess by this factor to be called low-q. */
    public static final double LOW_Q_DOMINANCE = 3.0;

    public enum Status {
        /** Post-peak baseline agrees with the pre-peak baseline. */
        OK,
        /** Post-peak baseline differs from the pre-peak baseline. */
        BASELINE_SHIFT,
        /** Too few frames before or after the peak to tell. */
        NOT_ASSESSABLE
    }

    public enum Shape {
        /** Excess scattering mostly at low q: large particles, e.g. protein deposited on the capillary. */
        LOW_Q,
        /** About the same excess at all q: normalisation, transmission or beam-intensity change. */
        UNIFORM,
        /** Neither pattern clearly. */
        MIXED,
        /** Not determined (no shift, or no averaged buffer in the file). */
        UNKNOWN
    }

    /** Outcome of the check; all signal values are in units of the integrated signal. */
    public static final class Result {
        public Status status = Status.NOT_ASSESSABLE;
        public Shape shape = Shape.UNKNOWN;
        public String reason = "";

        /** Pre-peak baseline: signal = intercept + slope * frame. The check uses a flat baseline (slope 0);
         *  {@link #preSlope} is the least-squares slope of the pre-peak frames, for information. */
        public double intercept, slope, preSlope;
        /** Frames used for the pre-peak baseline. */
        public int preFirst = -1, preLast = -1, preCount;
        /** Frame-to-frame noise of the pre-peak signal (robust standard deviation). */
        public double noise;

        public int peakFrame = -1;
        public double peakHeight;
        public int peakHalfHeightEnd = -1;

        /** Tail frames compared with the baseline (inclusive). */
        public int tailFirst = -1, tailLast = -1;
        /** Median of (signal - extrapolated baseline) over the tail. */
        public double offset;
        public double offsetStandardError;

        /**
         * Unbroken run of frames ending at the last frame whose signal (median of {@value #RUN_SMOOTHING}
         * neighbours) stays more than {@value #RUN_SIGMA} noise above (or below) the baseline;
         * -1 if the run ends before the tail. A run that starts at the peak means the signal never returned.
         */
        public int runFirst = -1;
        public int runLength;
        /**
         * First frame after the peak where the signal has come down to the post-peak level (baseline + offset),
         * i.e. where elution has finished and the displaced plateau begins; -1 if not found.
         */
        public int plateauFirst = -1;

        /** Mean ratio of averaged tail frames to averaged buffer, minus 1, at low and high q. */
        public double lowQExcess = Double.NaN, highQExcess = Double.NaN;
        public double lowQMax = Double.NaN, highQMin = Double.NaN;

        public boolean isFlagged() { return status == Status.BASELINE_SHIFT; }

        /** Offset as a percentage of the peak height. */
        public double offsetPercentOfPeak() { return peakHeight > 0 ? 100.0 * offset / peakHeight : Double.NaN; }

        /** Offset in standard errors. */
        public double significance() { return offsetStandardError > 0 ? offset / offsetStandardError : Double.NaN; }

        public double baselineAt(int frame) { return intercept + slope * frame; }

        /** True if the run starts within the peak, i.e. the signal never returns to the baseline after it. */
        public boolean neverReturns() { return runFirst >= 0 && runFirst <= peakHalfHeightEnd; }

        /** Description of the run of shifted frames, e.g. "frames 368-599 (232) never return to the baseline". */
        public String runDescription() {
            if (runFirst < 0) return "";
            return String.format("frames %d-%d (%d) %s the baseline", runFirst, runFirst + runLength - 1, runLength,
                    neverReturns() ? "never return to" : "all " + (offset > 0 ? "above" : "below"));
        }

        /** One-line summary for a status bar or log. */
        public String summary() {
            switch (status) {
                case OK:
                    return String.format("Baseline OK :: post-peak baseline within %.1f%% of peak height of the pre-peak baseline",
                            Math.abs(offsetPercentOfPeak()));
                case BASELINE_SHIFT:
                    return String.format("WARNING :: post-peak baseline %s by %.1f%% of peak height (%.4f, %.0f sigma)%s - %s",
                            offset > 0 ? "elevated" : "lowered", Math.abs(offsetPercentOfPeak()), Math.abs(offset),
                            Math.abs(significance()), runFirst >= 0 ? "; " + runDescription() : "", shapeDescription());
                default:
                    return "Baseline check not possible :: " + reason;
            }
        }

        public String shapeDescription() {
            switch (shape) {
                case LOW_Q:
                    return String.format("excess at low q (%.1f%% at q < %.3f vs %.2f%% at q > %.3f): possible capillary fouling or aggregation",
                            100 * lowQExcess, lowQMax, 100 * highQExcess, highQMin);
                case UNIFORM:
                    return String.format("similar excess at all q (%.2f%% low q, %.2f%% high q): check normalisation or transmission",
                            100 * lowQExcess, 100 * highQExcess);
                case MIXED:
                    return String.format("excess %.2f%% at low q, %.2f%% at high q", 100 * lowQExcess, 100 * highQExcess);
                default:
                    return "cause not determined";
            }
        }

        @Override
        public String toString() { return summary(); }
    }

    /** Source of unsubtracted frames, so the check works on a SECFile or in tests. */
    public interface FrameSource {
        double[] unsubtracted(int frame) throws IOException;
    }

    private BaselineCheck() {}

    /** Run the check on an open SEC file. */
    public static Result analyse(SECFile secFile) throws IOException {
        int n = secFile.getTotalFrames();
        double[] signal = new double[n];
        for (int i = 0; i < n; i++) signal[i] = secFile.getSignalSeries().getY(i).doubleValue();
        boolean[] buffer = new boolean[n];
        for (int i = 0; i < secFile.getBackground().getItemCount(); i++) {
            int frame = (int) Math.round(secFile.getBackground().getX(i).doubleValue());
            if (frame >= 0 && frame < n) buffer[frame] = true;
        }
        double[] q = toArray(secFile.getQvalues());
        double[] avg = secFile.getAveragedBuffer();
        return analyse(signal, buffer, q, avg, frame -> toArray(secFile.getUnSubtractedFrameAt(frame)));
    }

    private static double[] toArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    /**
     * @param signal         integrated signal per frame
     * @param isBuffer       frames used for the averaged buffer
     * @param q              q-values (may be null to skip the q analysis)
     * @param averagedBuffer averaged buffer intensities at q (may be null to skip the q analysis)
     * @param frames         unsubtracted frames (may be null to skip the q analysis)
     */
    public static Result analyse(double[] signal, boolean[] isBuffer, double[] q, double[] averagedBuffer,
                                 FrameSource frames) throws IOException {
        Result r = new Result();
        int n = signal.length;
        if (n < SecProcessor.MIN_FRAMES) {
            r.reason = "only " + n + " frames";
            return r;
        }

        // 1. pre-peak baseline from buffer frames before the global maximum
        int globalMax = argMax(signal, 0, n);
        List<Integer> pre = new ArrayList<>();
        for (int i = 0; i < globalMax; i++) if (isBuffer[i] && !Double.isNaN(signal[i])) pre.add(i);
        if (pre.size() < 10) {
            pre.clear();
            int tenPercent = Math.max(10, n / 10);
            for (int i = 0; i < Math.min(tenPercent, globalMax); i++) if (!Double.isNaN(signal[i])) pre.add(i);
        }
        if (pre.size() < 10) {
            r.reason = "fewer than 10 baseline frames before the peak";
            return r;
        }
        fitBaseline(signal, pre, r);

        // 2. highest peak above the baseline. Buffer frames on the foot of the peak (e.g. a manual selection
        //    that runs into the rising edge) tilt the line, so trim them: walk back from the peak until the
        //    signal is within 3 noise of the line, drop baseline frames after that point and refit.
        findPeak(signal, r);
        for (int iteration = 0; iteration < 3; iteration++) {
            if (!(r.peakHeight > SIGNIFICANCE * r.noise)) break;
            int foot = r.peakFrame;
            while (foot > 0 && signal[foot] - r.baselineAt(foot) > 3 * r.noise) foot--;
            List<Integer> clean = new ArrayList<>();
            for (int i : pre) if (i < foot) clean.add(i);
            if (clean.size() == pre.size() || clean.size() < 10) break;
            pre = clean;
            fitBaseline(signal, pre, r);
            findPeak(signal, r);
        }
        if (!(r.peakHeight > SIGNIFICANCE * r.noise)) {
            r.reason = "no clear elution peak above the baseline";
            return r;
        }

        int end = r.peakFrame;
        while (end < n - 1 && signal[end] - r.baselineAt(end) > 0.5 * r.peakHeight) end++;
        r.peakHalfHeightEnd = end;

        // 3. tail of the run, at least one more half-width after the half-height point
        int halfWidth = Math.max(1, end - r.peakFrame);
        int earliestTail = end + halfWidth;
        int tailLength = Math.max(MIN_TAIL_FRAMES, n * TAIL_FRACTION_PERCENT / 100);
        int tailFirst = Math.max(earliestTail, n - tailLength);
        if (n - tailFirst < 10) {
            r.reason = "peak elutes too close to the end of the run";
            return r;
        }
        r.tailFirst = tailFirst;
        r.tailLast = n - 1;

        double[] tailResidual = new double[n - tailFirst];
        double meanTailFrame = 0;
        for (int i = tailFirst; i < n; i++) {
            tailResidual[i - tailFirst] = signal[i] - r.baselineAt(i);
            meanTailFrame += i;
        }
        meanTailFrame /= tailResidual.length;
        r.offset = median(tailResidual);

        // standard error of the tail median plus uncertainty of the extrapolated line
        double tailNoise = 1.4826 * mad(tailResidual);
        double seMedian = 1.2533 * Math.max(tailNoise, r.noise) / Math.sqrt(tailResidual.length);
        double seLine = lineError(pre, r.noise, meanTailFrame);
        r.offsetStandardError = Math.sqrt(seMedian * seMedian + seLine * seLine);

        findShiftedRun(signal, r, n);

        boolean significant = Math.abs(r.offset) > SIGNIFICANCE * r.offsetStandardError;
        boolean large = Math.abs(r.offset) >= FLAG_PERCENT_OF_PEAK / 100.0 * r.peakHeight;
        r.status = (significant && large) ? Status.BASELINE_SHIFT : Status.OK;

        // 4. where in q is the excess?
        if (r.status == Status.BASELINE_SHIFT && q != null && averagedBuffer != null && frames != null) {
            classifyShape(r, q, averagedBuffer, frames, tailFirst, n);
        }
        return r;
    }

    /**
     * Walk back from the last frame while the (smoothed) signal stays on the same side of the baseline by more than
     * RUN_SIGMA noise. Only reported if the run covers at least the whole tail.
     */
    private static void findShiftedRun(double[] signal, Result r, int n) {
        double side = Math.signum(r.offset);
        if (side == 0 || r.noise <= 0) return;
        double limit = RUN_SIGMA * r.noise;
        int half = RUN_SMOOTHING / 2;
        int first = n;
        double[] window = new double[RUN_SMOOTHING];
        for (int i = n - 1; i > r.peakFrame; i--) {
            int from = Math.max(0, i - half), to = Math.min(n - 1, i + half);
            int m = 0;
            for (int j = from; j <= to; j++) window[m++] = signal[j] - r.baselineAt(j);
            double smoothed = median(Arrays.copyOf(window, m));
            if (side * smoothed > limit) first = i; else break;
        }
        if (first <= r.tailFirst) {
            r.runFirst = first;
            r.runLength = n - first;
        }

        // where the falling edge of the peak reaches the post-peak level
        for (int i = r.peakFrame; i <= r.tailFirst; i++) {
            int from = Math.max(0, i - half), to = Math.min(n - 1, i + half);
            int m = 0;
            for (int j = from; j <= to; j++) window[m++] = signal[j] - r.baselineAt(j);
            if (side * median(Arrays.copyOf(window, m)) <= side * r.offset) {
                r.plateauFirst = i;
                break;
            }
        }
    }

    private static void fitBaseline(double[] signal, List<Integer> pre, Result r) {
        fitLine(signal, pre, r);
        r.preFirst = pre.get(0);
        r.preLast = pre.get(pre.size() - 1);
        r.preCount = pre.size();
        double[] residual = new double[pre.size()];
        for (int k = 0; k < pre.size(); k++) residual[k] = signal[pre.get(k)] - r.baselineAt(pre.get(k));
        r.noise = 1.4826 * mad(residual);
    }

    private static void findPeak(double[] signal, Result r) {
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < signal.length; i++) {
            double h = signal[i] - r.baselineAt(i);
            if (h > best) { best = h; r.peakFrame = i; }
        }
        r.peakHeight = best;
    }

    private static void classifyShape(Result r, double[] q, double[] avg, FrameSource frames, int first, int n) throws IOException {
        int totalQ = Math.min(q.length, avg.length);
        double[] sum = new double[totalQ];
        int count = 0;
        for (int i = first; i < n; i++) {
            double[] frame = frames.unsubtracted(i);
            for (int k = 0; k < totalQ && k < frame.length; k++) sum[k] += frame[k];
            count++;
        }
        if (count == 0) return;

        int lowEnd = Math.max(1, totalQ / 10);   // lowest 10% of q points
        int highStart = totalQ / 2;              // upper half of q points
        r.lowQExcess = meanRatio(sum, count, avg, 0, lowEnd) - 1.0;
        r.highQExcess = meanRatio(sum, count, avg, highStart, totalQ) - 1.0;
        r.lowQMax = q[lowEnd - 1];
        r.highQMin = q[highStart];

        if (Double.isNaN(r.lowQExcess) || Double.isNaN(r.highQExcess)) {
            r.shape = Shape.UNKNOWN;
        } else if (r.lowQExcess > 0 && r.lowQExcess > LOW_Q_DOMINANCE * Math.abs(r.highQExcess)) {
            r.shape = Shape.LOW_Q;
        } else if (Math.signum(r.lowQExcess) == Math.signum(r.highQExcess)
                && Math.abs(r.lowQExcess) <= LOW_Q_DOMINANCE * Math.abs(r.highQExcess)
                && Math.abs(r.highQExcess) <= LOW_Q_DOMINANCE * Math.abs(r.lowQExcess)) {
            r.shape = Shape.UNIFORM;
        } else {
            r.shape = Shape.MIXED;
        }
    }

    private static double meanRatio(double[] sum, int count, double[] avg, int from, int to) {
        double total = 0;
        int used = 0;
        for (int k = from; k < to; k++) {
            double ratio = (sum[k] / count) / avg[k];
            if (avg[k] > 0 && !Double.isNaN(ratio) && !Double.isInfinite(ratio)) {
                total += ratio;
                used++;
            }
        }
        return used == 0 ? Double.NaN : total / used;
    }

    // ------------------------------------------------------------------ small statistics helpers

    private static int argMax(double[] v, int from, int to) {
        int best = from;
        for (int i = from; i < to; i++) if (v[i] > v[best]) best = i;
        return best;
    }

    /** Flat baseline at the median of the pre-peak frames; the least-squares slope is kept for information. */
    private static void fitLine(double[] signal, List<Integer> frames, Result r) {
        double[] values = new double[frames.size()];
        double mx = 0, my = 0;
        for (int k = 0; k < values.length; k++) {
            int i = frames.get(k);
            values[k] = signal[i];
            mx += i;
            my += signal[i];
        }
        mx /= values.length;
        my /= values.length;
        double sxx = 0, sxy = 0;
        for (int i : frames) {
            sxx += (i - mx) * (i - mx);
            sxy += (i - mx) * (signal[i] - my);
        }
        r.preSlope = sxx > 0 ? sxy / sxx : 0;
        r.slope = 0;
        r.intercept = median(values);
    }

    /** Standard error of the baseline median. */
    private static double lineError(List<Integer> frames, double noise, double x) {
        return 1.2533 * noise / Math.sqrt(frames.size());
    }

    static double median(double[] values) {
        double[] v = values.clone();
        Arrays.sort(v);
        int m = v.length / 2;
        return (v.length % 2 == 1) ? v[m] : 0.5 * (v[m - 1] + v[m]);
    }

    static double mad(double[] values) {
        double med = median(values);
        double[] dev = new double[values.length];
        for (int i = 0; i < values.length; i++) dev[i] = Math.abs(values[i] - med);
        return median(dev);
    }
}
