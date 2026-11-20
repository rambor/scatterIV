package scatter.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Finds the frames of a static (flow-cell) exposure series that agree with each other, so
 * anomalous frames are left out before merging.
 *
 * Typical series: 30 x 1 s frames of a flowing sample. The first frames can see the leading
 * meniscus (mixing, bubbles, wrong concentration); the last frames can show radiation damage
 * (aggregation, which grows from frame to frame).
 *
 * Method
 *  1. Reference (default, ANCHOR) = the earliest stable frames: the first window of three
 *     consecutive frames that agree pairwise and extend to a plateau of at least five. Damage only
 *     grows and the meniscus only disturbs the start, so the earliest plateau is the undamaged
 *     sample even when damage sets in early and most frames are damaged (where a median would
 *     describe the damaged state). Frames are kept forward from the anchor while they agree
 *     with it. MEDIAN (median of all frames, iterated) remains for sets with no time order.
 *  2. Each frame is compared with the reference by CorMap: the longest run of residuals
 *     (frame - reference) with the same sign, and the probability of a run at least that
 *     long by chance (Schilling 1990; Franke, Jeffries & Svergun 2015, Nat. Methods 12, 419).
 *     It needs no error model. Aggregation and the meniscus first change the lowest q, often
 *     over too few points to stand out in the full range, so the low-q quarter is also tested on
 *     its own, by CorMap and by chi^2 with the errors calibrated on the reference frames
 *     themselves (so wrongly scaled errors do not matter); the smallest p is kept, times the
 *     number of tests.
 *  3. p-values are Holm-Bonferroni adjusted over the frames; a frame is "similar" when the
 *     adjusted p >= alpha.
 *  4. The accepted frames are the longest stretch of similar frames, allowing gaps of at most
 *     maxGap dissimilar frames (those are dropped as outliers). Everything before it is
 *     "leading", everything after "trailing" - so damage at the end is cut as a block even if
 *     a damaged frame happens to pass.
 *  5. The reference is rebuilt from the accepted frames and steps 2-4 repeated (iterations).
 */
public final class FrameFilter {

    public static final class Options {
        /** q range used for the comparison (A^-1) */
        public double qmin = 0.0, qmax = Double.POSITIVE_INFINITY;
        /** significance level after Holm correction (CorMap / DATCMP use 0.01) */
        public double alpha = 0.01;
        /** dissimilar frames allowed inside the accepted stretch */
        public int maxGap = 1;
        /** fewer frames than this are not filtered (all accepted) */
        public int minFramesToFilter = 3;
        /** reference rebuild passes */
        public int iterations = 2;
        /** relative tolerance for matching q values between frames */
        public double qTolerance = 1e-4;
        /**
         * ANCHOR (default): reference = the earliest stable frames, for exposure series where the
         * first frames can be disturbed and damage grows with time. MEDIAN: median of all frames,
         * for sets with no time order (only valid while most frames are good).
         */
        public Reference reference = Reference.ANCHOR;
        /** consecutive frames that must agree pairwise to form the anchor */
        public int anchorFrames = 3;
        /** the anchor must extend to a stretch of at least this many frames to be taken */
        public int minPlateau = 5;

        public Options qRange(double lo, double hi) { qmin = lo; qmax = hi; return this; }
        public Options alpha(double a) { alpha = a; return this; }
        public Options maxGap(int g) { maxGap = g; return this; }
    }

    /**
     * DAMAGED = cut by the Rg trend check (DamageCheck), not by CorMap.
     * INCOMPLETE = far fewer data points than the other frames (an empty or truncated frame, e.g. a
     * dark frame with a couple of non-zero pixels); left out of the comparison so it cannot shrink
     * the common q grid of all the others.
     */
    public enum Reason { ACCEPTED, LEADING, TRAILING, OUTLIER, DAMAGED, INCOMPLETE }

    public enum Reference { ANCHOR, MEDIAN }

    public static final class Frame {
        public final int index;
        public final String name;
        /** longest same-sign run and number of compared points */
        public final int longestRun, points;
        public final double pValue, adjustedP, chi2;
        public final boolean similar;
        public final Reason reason;

        Frame(int index, String name, int longestRun, int points, double p, double adj, double chi2,
              boolean similar, Reason reason) {
            this.index = index; this.name = name; this.longestRun = longestRun; this.points = points;
            this.pValue = p; this.adjustedP = adj; this.chi2 = chi2; this.similar = similar; this.reason = reason;
        }

        public boolean isAccepted() { return reason == Reason.ACCEPTED; }
    }

    public static final class Result {
        private final List<Frame> frames;
        private final int points;
        private final String message;
        private final int anchorStart, anchorSize;

        Result(List<Frame> frames, int points, String message) {
            this(frames, points, message, -1, 0);
        }

        Result(List<Frame> frames, int points, String message, int anchorStart, int anchorSize) {
            this.frames = Collections.unmodifiableList(frames);
            this.points = points;
            this.message = message;
            this.anchorStart = anchorStart;
            this.anchorSize = anchorSize;
        }

        /** first frame of the reference anchor (0-based), or -1 when the median was used */
        public int getAnchorStart() { return anchorStart; }
        public int getAnchorSize() { return anchorSize; }

        public List<Frame> getFrames() { return frames; }
        /** q points used in the comparison */
        public int getPoints() { return points; }
        public String getMessage() { return message; }

        public int[] getAccepted() {
            return frames.stream().filter(Frame::isAccepted).mapToInt(f -> f.index).toArray();
        }

        /** Copy with the given frames marked DAMAGED. */
        Result withDamaged(int[] indices, String note) {
            boolean[] mark = new boolean[frames.size()];
            for (int k : indices) mark[k] = true;
            List<Frame> out = new ArrayList<>();
            for (Frame f : frames) {
                out.add(mark[f.index]
                        ? new Frame(f.index, f.name, f.longestRun, f.points, f.pValue, f.adjustedP, f.chi2, f.similar, Reason.DAMAGED)
                        : f);
            }
            String msg = message == null ? note : message + "; " + note;
            return new Result(out, points, msg, anchorStart, anchorSize);
        }

        public int count(Reason r) {
            return (int) frames.stream().filter(f -> f.reason == r).count();
        }

        public String summary() {
            int[] acc = getAccepted();
            StringBuilder sb = new StringBuilder();
            sb.append(acc.length).append(" of ").append(frames.size()).append(" frames accepted");
            if (acc.length > 0) sb.append(" (").append(range(acc)).append(")");   // numbered from 1
            int lead = count(Reason.LEADING), trail = count(Reason.TRAILING), out = count(Reason.OUTLIER);
            if (lead > 0) sb.append("; ").append(lead).append(" leading");
            if (trail > 0) sb.append("; ").append(trail).append(" trailing");
            if (out > 0) sb.append("; ").append(out).append(" outlier");
            int dam = count(Reason.DAMAGED);
            if (dam > 0) sb.append("; ").append(dam).append(" damaged");
            int inc = count(Reason.INCOMPLETE);
            if (inc > 0) sb.append("; ").append(inc).append(" incomplete");
            if (anchorStart >= 0) {
                sb.append("; reference frames ").append(anchorStart + 1).append("-").append(anchorStart + anchorSize);
            }
            if (message != null) sb.append("; ").append(message);
            return sb.toString();
        }

        /** "3-25,27" with frames numbered from 1 */
        private static String range(int[] idx) {
            idx = Arrays.stream(idx).map(k -> k + 1).toArray();
            StringBuilder sb = new StringBuilder();
            int start = idx[0], prev = idx[0];
            for (int k = 1; k <= idx.length; k++) {
                if (k < idx.length && idx[k] == prev + 1) { prev = idx[k]; continue; }
                if (sb.length() > 0) sb.append(",");
                sb.append(start == prev ? String.valueOf(start) : start + "-" + prev);
                if (k < idx.length) { start = idx[k]; prev = idx[k]; }
            }
            return sb.toString();
        }
    }

    private FrameFilter() {}

    /** frames with fewer points than this fraction of the median frame are INCOMPLETE */
    static final double INCOMPLETE_FRACTION = 0.5;

    public static Result filter(List<Curve> frames, Options o) {
        if (frames.isEmpty()) throw new IllegalArgumentException("no frames");
        // set aside empty or truncated frames, compare the rest, then put them back in place
        int[] sizes = frames.stream().mapToInt(Curve::size).toArray();
        int[] sorted = sizes.clone();
        Arrays.sort(sorted);
        double median = sorted.length % 2 == 1 ? sorted[sorted.length / 2]
                : 0.5 * (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]);
        List<Integer> keep = new ArrayList<>();
        for (int f = 0; f < frames.size(); f++) if (sizes[f] >= INCOMPLETE_FRACTION * median) keep.add(f);
        if (keep.size() == frames.size() || keep.isEmpty()) return filterComplete(frames, o);

        List<Curve> sub = new ArrayList<>();
        for (int f : keep) sub.add(frames.get(f));
        Result r = filterComplete(sub, o);
        List<Frame> out = new ArrayList<>();
        int k = 0;
        for (int f = 0; f < frames.size(); f++) {
            if (k < keep.size() && keep.get(k) == f) {
                Frame g = r.getFrames().get(k++);
                out.add(new Frame(f, g.name, g.longestRun, g.points, g.pValue, g.adjustedP, g.chi2, g.similar, g.reason));
            } else {
                out.add(new Frame(f, frames.get(f).getName(), 0, sizes[f], Double.NaN, Double.NaN, Double.NaN, false,
                        Reason.INCOMPLETE));
            }
        }
        int anchor = r.getAnchorStart() >= 0 ? keep.get(r.getAnchorStart()) : -1;
        return new Result(out, r.getPoints(), r.getMessage(), anchor, r.getAnchorSize());
    }

    private static Result filterComplete(List<Curve> frames, Options o) {
        if (o == null) o = new Options();
        int m = frames.size();
        if (m == 0) throw new IllegalArgumentException("no frames");

        Grid g = Grid.common(frames, o.qmin, o.qmax, o.qTolerance);
        if (m < o.minFramesToFilter || g.size() < 10) {
            String why = m < o.minFramesToFilter
                    ? "too few frames to filter (" + m + "), all used"
                    : "too few common q points in range (" + g.size() + "), all frames used";
            return acceptAll(frames, g.size(), why);
        }

        if (o.reference == Reference.ANCHOR && m >= Math.max(o.anchorFrames, 2) + 1) {
            Result anchored = filterAnchored(frames, g, o);
            if (anchored != null) return anchored;
            // no consistent window anywhere: fall through to the median, and say so
            Result med = filterMedian(frames, g, o);
            String note = "no stable frames found for a reference; median of all frames used";
            return new Result(med.getFrames(), med.getPoints(),
                    med.getMessage() == null ? note : med.getMessage() + "; " + note);
        }
        return filterMedian(frames, g, o);
    }

    private static Result filterMedian(List<Curve> frames, Grid g, Options o) {
        int m = frames.size();
        boolean[] inReference = new boolean[m];
        Arrays.fill(inReference, true);
        List<Frame> result = null;
        for (int pass = 0; pass < Math.max(1, o.iterations); pass++) {
            double[][] ref = g.median(inReference);
            result = compare(frames, g, ref, o);
            boolean[] accepted = new boolean[m];
            int count = 0;
            for (Frame f : result) if (f.isAccepted()) { accepted[f.index] = true; count++; }
            if (count < 2 || Arrays.equals(accepted, inReference)) break;
            inReference = accepted;
        }
        String msg = null;
        int accepted = (int) result.stream().filter(Frame::isAccepted).count();
        if (accepted < Math.max(2, m / 3)) msg = "few frames agree - check the data";
        return new Result(result, g.size(), msg);
    }

    /**
     * Reference = the earliest window of anchorFrames consecutive frames that agree pairwise and
     * extend to a stretch of at least minPlateau frames agreeing with the window's mean.
     * Frames are then kept forward from the anchor while they agree with it (gaps of maxGap
     * allowed) and backward while they agree (no gaps). Comparing with the anchor rather than
     * with the neighbouring frame means slow drift accumulates and is caught.
     * Returns null when no window is consistent.
     */
    private static Result filterAnchored(List<Curve> frames, Grid g, Options o) {
        int m = frames.size(), k = Math.max(2, o.anchorFrames);
        int pairs = k * (k - 1) / 2;
        // error calibration for the chi^2 test: neighbouring frames nearly always agree (a meniscus or
        // damage changes only a few of the m - 1 pairs), so the median of their reduced chi^2 is the
        // factor by which the given errors are off
        double[] neighbours = new double[m - 1];
        for (int f = 0; f + 1 < m; f++) neighbours[f] = stats(frames, g, f, g.frame(f + 1))[3];
        double errorScale2 = median(neighbours);
        Result fallback = null;
        int fallbackLength = 0;
        for (int s = 0; s + k <= m; s++) {
            // window consistent pairwise (Bonferroni over its pairs)
            boolean ok = true;
            for (int a = s; a < s + k && ok; a++) {
                for (int b = a + 1; b < s + k && ok; b++) {
                    double p = stats(frames, g, a, g.frame(b), errorScale2)[2];
                    if (Math.min(1.0, p * pairs) < o.alpha) ok = false;
                }
            }
            if (!ok) continue;

            boolean[] window = new boolean[m];
            for (int f = s; f < s + k; f++) window[f] = true;
            double[][] ref = g.mean(window);
            int[] runs = new int[m], pts = new int[m];
            double[] p = new double[m], chi2 = new double[m];
            for (int f = 0; f < m; f++) {
                double[] st = stats(frames, g, f, ref, errorScale2);
                runs[f] = (int) st[0]; pts[f] = (int) st[1]; p[f] = st[2]; chi2[f] = st[3];
            }
            double[] adj = holm(p);
            boolean[] similar = new boolean[m];
            for (int f = 0; f < m; f++) similar[f] = window[f] || adj[f] >= o.alpha;

            int end = s + k - 1, gap = 0;
            for (int f = s + k; f < m; f++) {
                if (similar[f]) { end = f; gap = 0; }
                else if (++gap > o.maxGap) break;
            }
            int start = s;
            while (start > 0 && similar[start - 1]) start--;

            List<Frame> out = new ArrayList<>();
            for (int f = 0; f < m; f++) {
                Reason r = f < start ? Reason.LEADING : f > end ? Reason.TRAILING
                        : similar[f] ? Reason.ACCEPTED : Reason.OUTLIER;
                out.add(new Frame(f, frames.get(f).getName(), runs[f], pts[f], p[f], adj[f], chi2[f], similar[f], r));
            }
            int length = end - start + 1;
            String msg = null;
            int accepted = (int) out.stream().filter(Frame::isAccepted).count();
            if (accepted < Math.max(2, m / 3)) msg = "few frames agree - check the data";
            Result r = new Result(out, g.size(), msg, s, k);
            if (length >= o.minPlateau) return r;
            if (length > fallbackLength) { fallback = r; fallbackLength = length; }
        }
        if (fallback == null) return null;
        String note = "no stretch of " + o.minPlateau + " agreeing frames; longest used";
        return new Result(fallback.getFrames(), fallback.getPoints(),
                fallback.getMessage() == null ? note : fallback.getMessage() + "; " + note,
                fallback.getAnchorStart(), fallback.getAnchorSize());
    }

    /** {longest run, points, p, reduced chi^2} of frame f against ref, CorMap only (no error calibration). */
    private static double[] stats(List<Curve> frames, Grid g, int f, double[][] ref) {
        return stats(frames, g, f, ref, Double.NaN);
    }

    /**
     * {longest run, points, p, reduced chi^2} of frame f against ref ([0] I, [1] sigma on the grid).
     *
     * p combines up to three tests (smallest p, times the number of tests):
     *  - CorMap over the whole range;
     *  - CorMap over the low-q quarter, where aggregation, damage and the meniscus show first,
     *    often over too few points to stand out in the full range;
     *  - chi^2 over the low-q eighth, with the errors calibrated by errorScale2 (the reduced chi^2
     *    of frames that agree, so badly scaled errors do not matter). CorMap only counts signs, so it
     *    misses a large excess over a handful of points; this test does not. Skipped when
     *    errorScale2 is NaN.
     */
    private static double[] stats(List<Curve> frames, Grid g, int f, double[][] ref, double errorScale2) {
        Curve c = frames.get(f);
        // CorMap on the lowest quarter; chi^2 on the lowest eighth (at least LOW_Q_MIN_POINTS), where
        // an aggregate's excess is concentrated - a wider block would dilute it
        int n = g.size(), lowEnd = n / 4, chiEnd = Math.min(n, Math.max(LOW_Q_MIN_POINTS, n / 8));
        boolean[] signs = new boolean[n];
        int count = 0, chiCount = 0, lowSigns = 0, lowChiCount = 0;
        double sum = 0, lowSum = 0;
        for (int k = 0; k < n; k++) {
            int idx = g.index[f][k];
            double d = c.i(idx) - ref[0][k];
            double s2 = c.sigma(idx) * c.sigma(idx) + ref[1][k] * ref[1][k];
            if (s2 > 0 && Double.isFinite(s2)) {
                sum += d * d / s2; chiCount++;
                if (k < chiEnd) { lowSum += d * d / s2; lowChiCount++; }
            }
            if (d == 0) continue;
            signs[count++] = d > 0;
            if (k < lowEnd) lowSigns = count;
        }
        int run = longestRun(signs, count);
        double best = probabilityOfRunAtLeast(count, run);
        int tests = 1;
        if (lowSigns >= LOW_Q_MIN_POINTS) {
            best = Math.min(best, probabilityOfRunAtLeast(lowSigns, longestRun(signs, lowSigns)));
            tests++;
        }
        if (Double.isFinite(errorScale2) && errorScale2 > 0 && lowChiCount >= LOW_Q_MIN_POINTS) {
            best = Math.min(best, chiSquareUpperTail(lowSum / errorScale2, lowChiCount));
            tests++;
        }
        double p = Math.min(1.0, tests * best);
        return new double[]{run, count, p, chiCount > 0 ? sum / chiCount : Double.NaN};
    }

    /** P(chi^2 with k degrees of freedom >= x), Wilson-Hilferty (good to a few % in p for k >= 20) */
    static double chiSquareUpperTail(double x, int k) {
        if (!(x > 0)) return 1.0;
        double h = 2.0 / (9.0 * k);
        double z = (Math.cbrt(x / k) - (1 - h)) / Math.sqrt(h);
        return 0.5 * erfc(z / Math.sqrt(2));
    }

    /** complementary error function (Numerical Recipes erfcc, relative error < 1.2e-7) */
    static double erfc(double x) {
        double z = Math.abs(x), t = 1 / (1 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? r : 2 - r;
    }

    /** median of the finite values (NaN if none) */
    private static double median(double[] v) {
        double[] f = Arrays.stream(v).filter(Double::isFinite).sorted().toArray();
        if (f.length == 0) return Double.NaN;
        return f.length % 2 == 1 ? f[f.length / 2] : 0.5 * (f[f.length / 2 - 1] + f[f.length / 2]);
    }

    /** the low-q quarter is tested separately when it has at least this many points */
    static final int LOW_Q_MIN_POINTS = 20;

    /** Every frame accepted, no comparison made (filtering switched off or impossible). */
    public static Result acceptAll(List<Curve> frames, String message) {
        return acceptAll(frames, 0, message);
    }

    private static Result acceptAll(List<Curve> frames, int points, String message) {
        List<Frame> all = new ArrayList<>();
        for (int f = 0; f < frames.size(); f++) {
            all.add(new Frame(f, frames.get(f).getName(), 0, points, Double.NaN, Double.NaN, Double.NaN, true, Reason.ACCEPTED));
        }
        return new Result(all, points, message);
    }

    private static List<Frame> compare(List<Curve> frames, Grid g, double[][] ref, Options o) {
        int m = frames.size();
        int[] runs = new int[m], pts = new int[m];
        double[] p = new double[m], chi2 = new double[m];
        for (int f = 0; f < m; f++) chi2[f] = stats(frames, g, f, ref)[3];
        double errorScale2 = median(chi2);       // most frames agree with the median of all frames
        for (int f = 0; f < m; f++) {
            double[] st = stats(frames, g, f, ref, errorScale2);
            runs[f] = (int) st[0]; pts[f] = (int) st[1]; p[f] = st[2]; chi2[f] = st[3];
        }
        double[] adj = holm(p);
        boolean[] similar = new boolean[m];
        for (int f = 0; f < m; f++) similar[f] = adj[f] >= o.alpha;
        Reason[] reasons = select(similar, o.maxGap);
        List<Frame> out = new ArrayList<>();
        for (int f = 0; f < m; f++) {
            out.add(new Frame(f, frames.get(f).getName(), runs[f], pts[f], p[f], adj[f], chi2[f], similar[f], reasons[f]));
        }
        return out;
    }

    /** Longest stretch of similar frames with gaps of at most maxGap dissimilar frames. */
    static Reason[] select(boolean[] similar, int maxGap) {
        int m = similar.length;
        int bestStart = -1, bestEnd = -1, bestCount = 0;
        int start = -1, end = -1, count = 0, gap = 0;
        for (int f = 0; f < m; f++) {
            if (similar[f]) {
                if (start < 0 || gap > maxGap) { start = f; count = 0; }
                end = f; count++; gap = 0;
                if (count > bestCount) { bestCount = count; bestStart = start; bestEnd = end; }
            } else if (start >= 0) {
                gap++;
            }
        }
        Reason[] r = new Reason[m];
        for (int f = 0; f < m; f++) {
            if (bestStart < 0 || f < bestStart) r[f] = Reason.LEADING;
            else if (f > bestEnd) r[f] = Reason.TRAILING;
            else r[f] = similar[f] ? Reason.ACCEPTED : Reason.OUTLIER;
        }
        return r;
    }

    static int longestRun(boolean[] s, int n) {
        if (n == 0) return 0;
        int best = 1, cur = 1;
        for (int k = 1; k < n; k++) {
            cur = (s[k] == s[k - 1]) ? cur + 1 : 1;
            if (cur > best) best = cur;
        }
        return best;
    }

    /**
     * P(longest run of identical outcomes >= c) in n fair coin flips (Schilling).
     * g(j) = number of compositions of j into parts <= c-1, divided by 2^j; then
     * P(longest <= c-1) = 2 g(n).
     */
    static double probabilityOfRunAtLeast(int n, int c) {
        if (n <= 0 || c <= 1) return 1.0;
        if (c > n) return 0.0;
        int k = c - 1;
        double[] g = new double[n + 1];
        double[] pow = new double[k + 1];
        for (int j = 1; j <= k; j++) pow[j] = Math.pow(0.5, j);
        g[0] = 1.0;
        for (int j = 1; j <= n; j++) {
            double s = 0;
            for (int t = 1; t <= k && t <= j; t++) s += g[j - t] * pow[t];
            g[j] = s;
        }
        double pAtMost = Math.min(1.0, 2.0 * g[n]);
        return Math.max(0.0, 1.0 - pAtMost);
    }

    /** Holm-Bonferroni adjusted p-values (same order as the input). */
    static double[] holm(double[] p) {
        int m = p.length;
        Integer[] order = new Integer[m];
        for (int k = 0; k < m; k++) order[k] = k;
        Arrays.sort(order, (a, b) -> Double.compare(p[a], p[b]));
        double[] adj = new double[m];
        double running = 0;
        for (int r = 0; r < m; r++) {
            int k = order[r];
            running = Math.max(running, Math.min(1.0, (m - r) * p[k]));
            adj[k] = running;
        }
        return adj;
    }

    // ------------------------------------------------------------------ common q grid

    /** q points (inside the range) present in every frame, with each frame's index for them. */
    static final class Grid {
        final double[] q;
        final int[][] index;    // [frame][point]
        final List<Curve> frames;

        private Grid(double[] q, int[][] index, List<Curve> frames) {
            this.q = q; this.index = index; this.frames = frames;
        }

        int size() { return q.length; }

        static Grid common(List<Curve> frames, double qmin, double qmax, double tol) {
            Curve first = frames.get(0);
            List<Integer> keep = new ArrayList<>();
            List<int[]> idx = new ArrayList<>();
            for (int k = 0; k < first.size(); k++) {
                double qk = first.q(k);
                if (qk < qmin || qk > qmax) continue;
                int[] col = new int[frames.size()];
                boolean ok = true;
                for (int f = 0; f < frames.size() && ok; f++) {
                    int j = f == 0 ? k : frames.get(f).indexOf(qk, tol);
                    if (j < 0 || !Double.isFinite(frames.get(f).i(j))) ok = false;
                    col[f] = j;
                }
                if (ok) { keep.add(k); idx.add(col); }
            }
            double[] q = new double[keep.size()];
            int[][] index = new int[frames.size()][keep.size()];
            for (int p = 0; p < keep.size(); p++) {
                q[p] = first.q(keep.get(p));
                for (int f = 0; f < frames.size(); f++) index[f][p] = idx.get(p)[f];
            }
            return new Grid(q, index, frames);
        }

        /** one frame's [0] intensity and [1] sigma on the grid */
        double[][] frame(int f) {
            double[][] out = new double[2][q.length];
            Curve c = frames.get(f);
            for (int k = 0; k < q.length; k++) {
                out[0][k] = c.i(index[f][k]);
                double s = c.sigma(index[f][k]);
                out[1][k] = Double.isFinite(s) ? s : 0;
            }
            return out;
        }

        /** [0] mean intensity and [1] its error sqrt(sum sigma^2)/N over the chosen frames */
        double[][] mean(boolean[] use) {
            double[][] out = new double[2][q.length];
            int count = 0;
            for (boolean u : use) if (u) count++;
            for (int k = 0; k < q.length; k++) {
                double sum = 0, var = 0;
                for (int f = 0; f < use.length; f++) {
                    if (!use[f]) continue;
                    Curve c = frames.get(f);
                    sum += c.i(index[f][k]);
                    double s = c.sigma(index[f][k]);
                    var += Double.isFinite(s) ? s * s : 0;
                }
                out[0][k] = sum / count;
                out[1][k] = Math.sqrt(var) / count;
            }
            return out;
        }

        /** [0] median intensity, [1] its standard error (1.2533 sigma / sqrt(N)) over the chosen frames */
        double[][] median(boolean[] use) {
            int n = q.length;
            double[][] out = new double[2][n];
            int count = 0;
            for (boolean u : use) if (u) count++;
            double[] values = new double[count];
            for (int k = 0; k < n; k++) {
                int c = 0;
                double var = 0;
                for (int f = 0; f < use.length; f++) {
                    if (!use[f]) continue;
                    Curve cur = frames.get(f);
                    values[c++] = cur.i(index[f][k]);
                    double s = cur.sigma(index[f][k]);
                    var += Double.isFinite(s) ? s * s : 0;
                }
                Arrays.sort(values, 0, c);
                out[0][k] = (c % 2 == 1) ? values[c / 2] : 0.5 * (values[c / 2 - 1] + values[c / 2]);
                out[1][k] = c > 0 ? 1.2533 * Math.sqrt(var / c) / Math.sqrt(c) : 0;
            }
            return out;
        }
    }
}
