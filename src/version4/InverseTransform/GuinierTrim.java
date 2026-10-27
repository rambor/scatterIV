package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatic low-q trimming and Guinier analysis, using the measurement errors.
 *
 * Start (low-q end). For each candidate start the weighted Guinier fit is made up to qRg = 1.3
 * (Rg self-consistent) and its misfit measured as ratio = chi2r / scatter, where scatter is the
 * noise level from point-to-point second differences (independent of whether the error bars are
 * right, and blind to a smooth bend). Candidates keep at least half of the first window and start
 * below qRg = 1.0.
 *   - A low-q defect (aggregation upturn, beamstop dip) makes the misfit high at the first point
 *     and removing it brings the misfit down: the profile DROPS.
 *   - The bend of an elongated particle's Guinier plot looks the same at any single start, but
 *     trimming does not cure it: the profile stays FLAT.
 * So points are trimmed only if ratio(first) exceeds the best ratio by IMPROVEMENT_SIGMAS*sqrt(2/nu),
 * and then the start is the first point whose ratio is within 1 sigma of the best.
 *
 * Robust check: a least-median-of-squares line (all point pairs, or a seeded random sample) is
 * fitted over the final window and its Rg reported as robustRg; a disagreement of more than
 * ROBUST_RG_SIGMAS with the weighted Rg is flagged. It is not used to choose the start: when an
 * upturn covers about half of the Guinier window (KASH5), a median fit is at its breakdown point
 * and settles on a compromise line.
 *
 * Upper end: starts at qRg = 1.3 and shrinks in steps of 0.05 (down to 0.8) while the last 15% of
 * the window deviates systematically (CUSUM of normalized residuals) from the line through the
 * points before them; this handles the elongated case.
 *
 * Input: q*I(q) and q*sigma(q), the convention used throughout the IFT classes.
 */
public final class GuinierTrim {

    public static double Z_LIMIT = 3.0;
    public static double QRG_MAX = 1.3, QRG_MIN_UPPER = 0.8, QRG_STEP = 0.05;
    public static double QRG_REFERENCE = 1.0;      // reference line for the leading-block test
    public static double QRG_TRIM_LIMIT = 0.65;     // P(r)-model check: never flag beyond this qRg
    /** trimming happens only when the misfit at the first point exceeds the best by this many sigma */
    public static double IMPROVEMENT_SIGMAS = 2.0;
    /** candidate starts must keep at least this fraction of the first window */
    public static double MIN_KEPT_FRACTION = 0.5;
    /** the Guinier window from a candidate start must keep at least this many points... */
    public static int MIN_WINDOW = 12;
    /** ...and must start below this qRg */
    public static double QRG_START_MAX = 1.0;
    public static double BLOCK_FRACTION = 0.15;
    public static int MIN_BLOCK = 5, MIN_REFERENCE_POINTS = 10;

    public static final class Result {
        public final int startIndex;          // first kept point, index into the input series
        public final double qmin, rg, rgSigma, izero, izeroSigma, qRgLow, qRgHigh, chi2Reduced;
        /** Rg of the least-median-of-squares line over the final window (independent check) */
        public final double robustRg;
        public final int endIndex;            // last point of the Guinier window (inclusive)
        public final String message;
        Result(int start, int end, double qmin, double rg, double rgS, double i0, double i0S,
               double low, double high, double chi2, double robustRg, String message) {
            startIndex = start; endIndex = end; this.qmin = qmin; this.rg = rg; rgSigma = rgS; izero = i0; izeroSigma = i0S;
            qRgLow = low; qRgHigh = high; chi2Reduced = chi2; this.robustRg = robustRg; this.message = message;
        }
        @Override public String toString() {
            return String.format("start %d (q %.4f), Guinier Rg %.2f +/- %.2f, I(0) %.5g +/- %.2g, qRg %.2f-%.2f, chi2r %.2f%s",
                    startIndex, qmin, rg, rgSigma, izero, izeroSigma, qRgLow, qRgHigh, chi2Reduced,
                    message == null ? "" : " (" + message + ")");
        }
    }

    private final double[] q, x, y, w;   // q, q^2, ln I, weight of ln I
    private final boolean[] valid;

    private GuinierTrim(XYSeries qIq, XYSeries qSigma) {
        int n = qIq.getItemCount();
        q = new double[n]; x = new double[n]; y = new double[n]; w = new double[n]; valid = new boolean[n];
        for (int i = 0; i < n; i++) {
            double qi = qIq.getX(i).doubleValue();
            double I = qIq.getY(i).doubleValue()/qi, s = qSigma.getY(i).doubleValue()/qi;
            q[i] = qi; x[i] = qi*qi;
            valid[i] = I > 0 && s > 0;
            if (valid[i]) { y[i] = Math.log(I); w[i] = (I/s)*(I/s); }
        }
    }

    /** trims the low-q end and fits the Guinier region; never throws for ordinary data */
    public static Result trim(XYSeries qIq, XYSeries qSigma) {
        return new GuinierTrim(qIq, qSigma).run();
    }

    private Result run() {
        int n = q.length;
        int first = 0;
        while (first < n && !valid[first]) first++;
        Window g0 = guinierWindow(first, 30.0);
        if (g0 == null) return fallback(first, "no Guinier region found");

        // misfit profile: for each candidate start, the self-consistent window up to qRg = QRG_MAX and
        // its misfit relative to the point-to-point noise, ratio = chi2r / scatter
        double rg = g0.rg;
        int[] ks = new int[n];
        double[] ratio = new double[n], nu = new double[n];
        int nc = 0, n0 = -1;
        for (int st = first; st < n; st++) {
            if (!valid[st]) continue;
            Window f = fixedWindow(st, rg, QRG_MAX);
            if (f == null) break;
            rg = f.rg;
            int len = f.hi - st;
            if (n0 < 0) n0 = len;
            if (len < Math.max(MIN_WINDOW, (int) (MIN_KEPT_FRACTION*n0)) || q[st]*f.rg > QRG_START_MAX) break;
            ks[nc] = st;
            ratio[nc] = f.line.chi2r/localScatter(st, f.hi);
            nu[nc] = len - 2;
            nc++;
        }

        int start = first;
        List<String> notes = new ArrayList<>();
        if (nc >= 2) {
            int jmin = 0;
            for (int j = 1; j < nc; j++) if (ratio[j] < ratio[jmin]) jmin = j;
            // trim only if the first window is significantly worse than the best one: a defect at low q
            // is removed by trimming, the bend of an elongated particle is not (its profile stays flat)
            if (ratio[0] - ratio[jmin] > IMPROVEMENT_SIGMAS*Math.sqrt(2.0/nu[0])) {
                for (int j = 0; j <= jmin; j++) {
                    if (ratio[j] <= ratio[jmin] + Math.sqrt(2.0/nu[j])) { start = ks[j]; break; }
                }
                notes.add(String.format("%d leading point%s removed: the Guinier fit improves from %.2f to %.2f of the noise level",
                        start - first, start - first == 1 ? "" : "s", ratio[0], ratio[Math.max(0, indexOf(ks, nc, start))]));
            }
        }
        Window g = guinierWindow(start, rg);
        if (g == null) return fallback(start, "no Guinier region found");

        // independent check: least-median-of-squares line over the final window
        Robust robust = lmeds(start, g.hi);
        double robustRg = (robust == null) ? Double.NaN : Math.sqrt(-3*robust.b);
        Result r = result(start, g, null);
        if (!Double.isNaN(robustRg) && Math.abs(robustRg - r.rg) > ROBUST_RG_SIGMAS*Math.max(r.rgSigma, 1e-3*r.rg)) {
            notes.add(String.format("robust (median) Rg %.1f differs from the weighted fit (%.1f +/- %.1f): inspect the Guinier plot",
                    robustRg, r.rg, r.rgSigma));
        }
        return new Result(r.startIndex, r.endIndex, r.qmin, r.rg, r.rgSigma, r.izero, r.izeroSigma, r.qRgLow, r.qRgHigh,
                r.chi2Reduced, robustRg, notes.isEmpty() ? null : String.join("; ", notes));
    }

    private static int indexOf(int[] a, int n, int v) {
        for (int i = 0; i < n; i++) if (a[i] == v) return i;
        return -1;
    }

    /** robust and weighted Rg are flagged when they differ by more than this many sigma */
    public static double ROBUST_RG_SIGMAS = 3.0;

    /* ---------------------------------------------------------------- robust (LMedS) line */

    private static final class Robust {
        final double a, b, scale;     // ln I = a + b q^2; scale = robust sd of normalized residuals
        Robust(double a, double b, double scale) { this.a = a; this.b = b; this.scale = scale; }
    }

    /** max number of point pairs tried (all pairs when fewer) */
    public static int LMEDS_MAX_PAIRS = 20000;

    /**
     * least median of squares: the line through each pair of points (all pairs, or a seeded random
     * sample when there are more than LMEDS_MAX_PAIRS) is scored by the median squared normalized
     * residual over [lo, hi); the best line is kept, with the robust scale
     * 1.4826 (1 + 5/(n - 2)) sqrt(median).
     */
    private Robust lmeds(int lo, int hi) {
        int[] idx = new int[hi - lo];
        int n = 0;
        for (int i = lo; i < hi; i++) if (valid[i]) idx[n++] = i;
        if (n < 6) return null;
        double[] r2 = new double[n];
        long pairs = (long) n*(n - 1)/2;
        java.util.Random rnd = new java.util.Random(12345);
        double bestMed = Double.POSITIVE_INFINITY, ba = 0, bb = 0;
        long tries = Math.min(pairs, LMEDS_MAX_PAIRS);
        int ii = 0, jj = 1;
        for (long t = 0; t < tries; t++) {
            int i, j;
            if (pairs <= LMEDS_MAX_PAIRS) {            // enumerate all pairs
                i = ii; j = jj;
                if (++jj >= n) { ii++; jj = ii + 1; }
            } else {
                i = rnd.nextInt(n); j = rnd.nextInt(n);
                if (i == j) continue;
            }
            int p = idx[i], r = idx[j];
            if (x[p] == x[r]) continue;
            double slope = (y[r] - y[p])/(x[r] - x[p]);
            if (slope >= 0) continue;
            double icpt = y[p] - slope*x[p];
            for (int k = 0; k < n; k++) {
                int m = idx[k];
                double z = (y[m] - icpt - slope*x[m])*Math.sqrt(w[m]);
                r2[k] = z*z;
            }
            double med = median(r2, n);
            if (med < bestMed) { bestMed = med; ba = icpt; bb = slope; }
        }
        if (!(bestMed < Double.POSITIVE_INFINITY)) return null;
        double scale = 1.4826*(1 + 5.0/(n - 2))*Math.sqrt(bestMed);
        return new Robust(ba, bb, Math.max(scale, 1e-12));
    }

    private static double median(double[] v, int n) {
        double[] c = java.util.Arrays.copyOf(v, n);
        java.util.Arrays.sort(c);
        return (n % 2 == 1) ? c[n/2] : 0.5*(c[n/2 - 1] + c[n/2]);
    }

    /** self-consistent window from lo with a fixed upper qRg (no shrinking) */
    private Window fixedWindow(int lo, double rgStart, double qrg) {
        double rg = rgStart;
        Line line = null;
        int hi = lo;
        for (int it = 0; it < 60; it++) {
            hi = Math.max(indexAtQRg(qrg, rg), lo + MIN_REFERENCE_POINTS);
            if (hi > q.length) return null;
            line = fit(lo, hi);
            if (line == null || line.b >= 0) return null;
            double next = Math.sqrt(-3*line.b);
            boolean done = Math.abs(next - rg) < 1e-5*rg;
            rg = next;
            if (done) break;
        }
        hi = Math.max(indexAtQRg(qrg, rg), lo + MIN_REFERENCE_POINTS);
        if (hi > q.length) return null;
        line = fit(lo, hi);
        return (line == null || line.b >= 0) ? null : new Window(lo, hi, Math.sqrt(-3*line.b), qrg, line);
    }

    /** mean r_i^2/var(r_i) of second differences over [lo, hi): about 1 when the errors are right */
    private double localScatter(int lo, int hi) {
        double sum = 0;
        int c = 0;
        for (int i = Math.max(lo, 1); i < Math.min(hi, q.length - 1); i++) {
            if (!valid[i - 1] || !valid[i] || !valid[i + 1]) continue;
            double r = y[i] - 0.5*(y[i - 1] + y[i + 1]);
            sum += r*r/(1/w[i] + 0.25*(1/w[i - 1] + 1/w[i + 1]));
            c++;
        }
        return (c < 5 || !(sum > 0)) ? 1.0 : sum/c;
    }

    /* ---------------------------------------------------------------- Guinier window */

    private static final class Window {
        final int lo, hi;        // [lo, hi)
        final double rg, qRgHigh;
        final Line line;
        Window(int lo, int hi, double rg, double qRgHigh, Line line) { this.lo = lo; this.hi = hi; this.rg = rg; this.qRgHigh = qRgHigh; this.line = line; }
    }

    /** window from lo with the largest upper qRg (<= QRG_MAX) whose trailing block is linear */
    private Window guinierWindow(int lo, double rgStart) {
        double rg = rgStart;
        Window last = null;
        for (double qrg = QRG_MAX; qrg >= QRG_MIN_UPPER - 1e-9; qrg -= QRG_STEP) {
            Line line = null;
            int hi = lo;
            for (int it = 0; it < 30; it++) {
                hi = Math.max(indexAtQRg(qrg, rg), lo + MIN_REFERENCE_POINTS);
                if (hi > q.length) return last;
                line = fit(lo, hi);
                if (line == null || line.b >= 0) return last;
                double next = Math.sqrt(-3*line.b);
                boolean done = Math.abs(next - rg) < 1e-4*rg;
                rg = next;
                if (done) break;
            }
            last = new Window(lo, hi, rg, qrg, line);
            int nWin = hi - lo, m = Math.max(MIN_BLOCK, (int) (BLOCK_FRACTION*nWin));
            if (nWin - m < MIN_REFERENCE_POINTS) return last;
            Line ref = fit(lo, hi - m);
            if (ref == null || blockConsistent(ref, hi - m, hi, true, lo, hi)) return last;
        }
        return last;
    }

    /** CUSUM + scatter test of points [a, b) against a reference line; fromEnd walks the block from b-1 down */
    private boolean blockConsistent(Line ref, int a, int b, boolean fromEnd, int noiseLo, int noiseHi) {
        double c = localNoiseScale(noiseLo, noiseHi);
        double cum = 0, maxCusum = 0, sumSq = 0;
        int k = 0;
        for (int j = 0; j < b - a; j++) {
            int i = fromEnd ? b - 1 - j : a + j;
            if (!valid[i]) return false;
            double pred = ref.a + ref.b*x[i];
            double varPred = ref.caa + 2*x[i]*ref.cab + x[i]*x[i]*ref.cbb;
            double z = (y[i] - pred)/Math.sqrt(c/w[i] + varPred);
            k++;
            cum += z;
            sumSq += z*z;
            maxCusum = Math.max(maxCusum, Math.abs(cum)/Math.sqrt(k));
        }
        return maxCusum < Z_LIMIT && sumSq/k < 1 + Z_LIMIT*Math.sqrt(2.0/k);
    }

    /**
     * error scale from point-to-point scatter, max(1, mean of r_i^2 / var(r_i)) with
     * r_i = y_i - (y_(i-1) + y_(i+1))/2: a second difference, blind to smooth trends, so curvature or
     * an upturn cannot inflate it (a fit's reduced chi^2 would absorb exactly what is being tested)
     */
    private double localNoiseScale(int lo, int hi) {
        double sum = 0;
        int n = 0;
        for (int i = Math.max(lo, 1); i < Math.min(hi, q.length - 1); i++) {
            if (!valid[i - 1] || !valid[i] || !valid[i + 1]) continue;
            double r = y[i] - 0.5*(y[i - 1] + y[i + 1]);
            double var = 1/w[i] + 0.25*(1/w[i - 1] + 1/w[i + 1]);
            sum += r*r/var;
            n++;
        }
        return (n < 5) ? 1.0 : Math.max(1.0, sum/n);
    }

    /* ---------------------------------------------------------------- weighted line ln I = a + b q^2 */

    private static final class Line {
        final double a, b, caa, cab, cbb, chi2r;   // covariance from the weights (not scaled)
        Line(double a, double b, double caa, double cab, double cbb, double chi2r) {
            this.a = a; this.b = b; this.caa = caa; this.cab = cab; this.cbb = cbb; this.chi2r = chi2r;
        }
    }

    private Line fit(int lo, int hi) {
        double s0 = 0, s1 = 0, s2 = 0, t0 = 0, t1 = 0;
        int n = 0;
        for (int i = lo; i < hi && i < q.length; i++) {
            if (!valid[i]) continue;
            s0 += w[i]; s1 += w[i]*x[i]; s2 += w[i]*x[i]*x[i]; t0 += w[i]*y[i]; t1 += w[i]*x[i]*y[i];
            n++;
        }
        double det = s0*s2 - s1*s1;
        if (n < 3 || !(det > 0)) return null;
        double a = (s2*t0 - s1*t1)/det, b = (s0*t1 - s1*t0)/det;
        double chi = 0;
        for (int i = lo; i < hi && i < q.length; i++) {
            if (!valid[i]) continue;
            double r = y[i] - a - b*x[i];
            chi += w[i]*r*r;
        }
        return new Line(a, b, s2/det, -s1/det, s0/det, chi/(n - 2));
    }

    private int indexAtQRg(double qrg, double rg) {
        int i = 0;
        while (i < q.length && q[i]*rg <= qrg) i++;
        return i;
    }

    private Result result(int start, Window g, String message) {
        Line l = g.line;
        double c = Math.max(1.0, l.chi2r);
        double rg = Math.sqrt(-3*l.b), rgS = 1.5*Math.sqrt(l.cbb*c)/rg;
        double i0 = Math.exp(l.a), i0S = i0*Math.sqrt(l.caa*c);
        return new Result(start, g.hi - 1, q[start], rg, rgS, i0, i0S, q[start]*rg, q[g.hi - 1]*rg, l.chi2r, Double.NaN, message);
    }

    private Result fallback(int start, String message) {
        return new Result(start, start, q.length > start ? q[start] : Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, message);
    }

    /* ---------------------------------------------------------------- validation against an IFT model */

    /**
     * Number of leading points of (qIq, qSigma) that deviate systematically from the P(r) models: for
     * each method in the ensemble (weight > 0), the smallest s such that the block starting at s
     * passes the same CUSUM/scatter test against that method's I(q), with errors scaled by its
     * reduced chi^2 when above 1. A point counts as bad only if it disagrees with EVERY method, so a
     * single member that misses I(0) (e.g. a short histogram grid) cannot trigger trimming.
     * 0 means the low-q end agrees with P(r).
     */
    public static int leadingInconsistentWithModel(EnsembleIFT ens, XYSeries qIq, XYSeries qSigma, double rg, double dmax) {
        int n = qIq.getItemCount();
        int limit = 0;
        while (limit < n && qIq.getX(limit).doubleValue()*rg <= QRG_TRIM_LIMIT) limit++;
        int m = MIN_BLOCK;
        double edge = qIq.getX(0).doubleValue() + Math.PI/dmax;                        // one Shannon channel
        while (m < n && qIq.getX(m).doubleValue() < edge && m < 4*MIN_BLOCK) m++;
        int best = Integer.MAX_VALUE;
        for (int mem = 0; mem < ens.members.length; mem++) {
            if (ens.weights[mem] <= 0) continue;
            IndirectFT f = ens.members[mem];
            double[] r = new double[n];
            double chi = 0;
            for (int i = 0; i < n; i++) {
                double qi = qIq.getX(i).doubleValue();
                r[i] = (qIq.getY(i).doubleValue() - f.calculateQIQ(qi))/qSigma.getY(i).doubleValue();
                chi += r[i]*r[i];
            }
            double scale = Math.sqrt(Math.max(1.0, chi/n));
            int sMem = Math.max(0, limit);
            for (int s = 0; s < Math.max(1, limit); s++) {
                if (s + m > n) break;
                double cum = 0, maxCusum = 0, sumSq = 0;
                for (int k = 0; k < m; k++) {
                    double z = r[s + k]/scale;
                    cum += z;
                    sumSq += z*z;
                    maxCusum = Math.max(maxCusum, Math.abs(cum)/Math.sqrt(k + 1));
                }
                if (maxCusum < Z_LIMIT && sumSq/m < 1 + Z_LIMIT*Math.sqrt(2.0/m)) { sMem = s; break; }
            }
            best = Math.min(best, sMem);
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }
}
