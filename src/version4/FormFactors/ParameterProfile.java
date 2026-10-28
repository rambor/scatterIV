package version4.FormFactors;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Profile scan of one global model setting that is shared by every particle (e.g. the Flory exponent nu of
 * excluded-volume chains, or the Kuhn length b of wormlike chains): at each value the size distribution is re-fitted
 * and the best chi^2 recorded. Polydispersity and nu (or b) are then determined together, instead of taking nu from a
 * single-chain fit, which polydispersity biases.
 *
 * Grid: coarse values, then adaptive bisection towards neighbours of the best point that are far above Delta chi^2 = 4,
 * then seed repeats at the best point (optimiser noise). Intervals: Delta chi^2_total = 1 (68 %) and 4 (95 %) by linear
 * interpolation of the lower envelope, plus a parabola through the three lowest points.
 */
public final class ParameterProfile {

    public static final class Point {
        public double value, chi2Total, chi2Red;
        public long seed;
        public LangevinResult result;
    }

    public static final class Profile {
        public String parameter, unit = "";
        public List<Point> points = new ArrayList<>();
        public Point best;
        public double bestValue, bestChi2Total;
        public double lo68, hi68, lo95, hi95;
        public boolean lo68Open, hi68Open, lo95Open, hi95Open;
        public double parabolaCentre = Double.NaN, parabolaHalfWidth68 = Double.NaN, parabolaHalfWidth95 = Double.NaN;
        public double optimizerNoise = Double.NaN;
        public int dof;

        public String toRemarks() {
            StringBuilder sb = new StringBuilder();
            sb.append("REMARK 265\nREMARK 265 PARAMETER PROFILE : ").append(parameter.toUpperCase()).append('\n');
            sb.append(String.format("REMARK 265  %12s  %14s  %16s%n", parameter.toUpperCase(), "CHI2_RED(MIN)", "DELTA CHI2_TOTAL"));
            List<Point> srt = new ArrayList<>(points);
            srt.sort(Comparator.comparingDouble(p -> p.value));
            for (Point p : srt)
                sb.append(String.format("REMARK 265  %12.4f  %14.4f  %16.2f%n", p.value, p.chi2Red, p.chi2Total - bestChi2Total));
            sb.append(String.format("REMARK 265  BEST %s : %.4f%s%n", parameter.toUpperCase(), bestValue, unit));
            sb.append(String.format("REMARK 265        68 %% INTERVAL (dchi2=1) : %s%.4f - %.4f%s%n", lo68Open ? "<=" : "", lo68, hi68, hi68Open ? " (open)" : ""));
            sb.append(String.format("REMARK 265        95 %% INTERVAL (dchi2=4) : %s%.4f - %.4f%s%n", lo95Open ? "<=" : "", lo95, hi95, hi95Open ? " (open)" : ""));
            if (!Double.isNaN(parabolaCentre))
                sb.append(String.format("REMARK 265  PARABOLIC FIT (3 lowest points)  : %.4f +- %.4f (68 %%), +- %.4f (95 %%)%n",
                        parabolaCentre, parabolaHalfWidth68, parabolaHalfWidth95));
            sb.append(String.format("REMARK 265  OPTIMISER NOISE (sd chi2_total) : %.2f%s%n", optimizerNoise,
                    optimizerNoise > 0.5 ? "   *** comparable to dchi2 = 1: interval approximate" : ""));
            return sb.toString();
        }

        public void writeCsv(Path file) throws IOException {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
                w.println(parameter + ",chi2_red_min,chi2_total,delta_chi2,seed");
                List<Point> srt = new ArrayList<>(points);
                srt.sort(Comparator.comparingDouble(p -> p.value));
                for (Point p : srt)
                    w.printf("%.6g,%.6f,%.4f,%.4f,%d%n", p.value, p.chi2Red, p.chi2Total, p.chi2Total - bestChi2Total, p.seed);
            }
        }
    }

    /**
     * @param setter   applies a scanned value to a copy of the settings (e.g. (s, v) -> s.chainNu = v)
     * @param grid     coarse values, ascending
     * @param logScale bisect in log space (positive scale parameters such as b)
     * @param repeats  extra seeds at the best point
     * @param ctl      progress / cancel (SpeciesProfile.Control); may be null
     * @return the profile, or null when cancelled
     */
    public static Profile run(double[] q, double[] I, double[] sigma, ShapeFamily family, double[] min, double[] max,
                              LangevinSettings base, String parameter, String unit, BiConsumer<LangevinSettings, Double> setter,
                              double[] grid, boolean logScale, int repeats, SpeciesProfile.Control ctl) {
        if (ctl == null) ctl = new SpeciesProfile.Control();
        Profile prof = new Profile();
        prof.parameter = parameter;
        prof.unit = unit;
        int total = grid.length + repeats + 3, done = 0;
        ctl.pointTotal = total;
        for (double v : grid) {
            ctl.pointIndex = done++;
            Point p = point(q, I, sigma, family, min, max, base, setter, v, base.seed, prof, ctl);
            if (p == null) return null;
            prof.points.add(p);
        }
        // adaptive bisection near the minimum
        Point b = bestOf(prof.points);
        for (int extra = 0; extra < 8; extra++) {
            Point[] nb = neighbours(prof.points, b);
            Point target = null;
            double worst = 9.0;
            for (Point n : nb)
                if (n != null && n.chi2Total - b.chi2Total > worst && Math.abs(n.value - b.value) > 1e-3 * Math.abs(b.value)) {
                    worst = n.chi2Total - b.chi2Total;
                    target = n;
                }
            if (target == null) break;
            double v = logScale ? Math.sqrt(b.value * target.value) : 0.5 * (b.value + target.value);
            ctl.pointTotal = ++total;
            ctl.pointIndex = done++;
            Point p = point(q, I, sigma, family, min, max, base, setter, v, base.seed, prof, ctl);
            if (p == null) return null;
            prof.points.add(p);
            b = bestOf(prof.points);
        }
        // optimiser noise
        List<Double> chis = new ArrayList<>();
        chis.add(b.chi2Total);
        for (int r = 1; r <= repeats; r++) {
            ctl.pointIndex = done++;
            Point p = point(q, I, sigma, family, min, max, base, setter, b.value, base.seed + 104729L * r, prof, ctl);
            if (p == null) return null;
            prof.points.add(p);
            chis.add(p.chi2Total);
        }
        if (chis.size() > 1) {
            double m = 0, s2 = 0;
            for (double c : chis) m += c;
            m /= chis.size();
            for (double c : chis) s2 += (c - m) * (c - m);
            prof.optimizerNoise = Math.sqrt(s2 / (chis.size() - 1));
        }
        b = bestOf(prof.points);
        prof.best = b;
        prof.bestValue = b.value;
        prof.bestChi2Total = b.chi2Total;
        double[] i68 = interval(prof, 1), i95 = interval(prof, 4);
        prof.lo68 = i68[0]; prof.hi68 = i68[1]; prof.lo68Open = i68[2] > 0; prof.hi68Open = i68[3] > 0;
        prof.lo95 = i95[0]; prof.hi95 = i95[1]; prof.lo95Open = i95[2] > 0; prof.hi95Open = i95[3] > 0;
        parabola(prof);
        return prof;
    }

    private static Point point(double[] q, double[] I, double[] sigma, ShapeFamily family, double[] min, double[] max,
                               LangevinSettings base, BiConsumer<LangevinSettings, Double> setter, double v, long seed,
                               Profile prof, SpeciesProfile.Control ctl) {
        if (ctl.isCancelled()) return null;
        LangevinSettings s = base.copy();
        setter.accept(s, v);
        s.seed = seed;
        s.replicas = 1;
        s.equilibrationSteps = Math.min(base.equilibrationSteps, 300);
        s.sampleSteps = Math.min(base.sampleSteps, 400);
        LangevinFitter fit = new LangevinFitter(q, I, sigma, family, min, max, s);
        ctl.current = fit;
        if (ctl.isCancelled()) fit.cancel();
        LangevinResult r = fit.fit(ctl.inner);
        ctl.current = null;
        if (r == null || ctl.isCancelled()) return null;
        Point p = new Point();
        p.value = v;
        p.seed = seed;
        p.result = r;
        p.chi2Red = r.chi2Best[0];
        prof.dof = Math.max(1, r.q.length - (s.fitBackground ? 2 : 1));
        p.chi2Total = p.chi2Red * prof.dof;
        return p;
    }

    private static Point bestOf(List<Point> pts) {
        Point b = pts.get(0);
        for (Point p : pts) if (p.chi2Total < b.chi2Total) b = p;
        return b;
    }

    private static Point[] neighbours(List<Point> pts, Point b) {
        Point lo = null, hi = null;
        for (Point p : pts) {
            if (p.value < b.value - 1e-12) {
                if (lo == null || p.value > lo.value + 1e-12 || (Math.abs(p.value - lo.value) <= 1e-12 && p.chi2Total < lo.chi2Total)) lo = p;
            } else if (p.value > b.value + 1e-12) {
                if (hi == null || p.value < hi.value - 1e-12 || (Math.abs(p.value - hi.value) <= 1e-12 && p.chi2Total < hi.chi2Total)) hi = p;
            }
        }
        return new Point[]{lo, hi};
    }

    /** lowest chi2 per scanned value, ordered by value */
    private static List<Point> envelope(Profile prof) {
        List<Point> srt = new ArrayList<>(prof.points);
        srt.sort(Comparator.comparingDouble(p -> p.value));
        List<Point> env = new ArrayList<>();
        for (Point p : srt) {
            if (!env.isEmpty() && Math.abs(env.get(env.size() - 1).value - p.value) <= 1e-12 * Math.max(1, Math.abs(p.value))) {
                if (p.chi2Total < env.get(env.size() - 1).chi2Total) env.set(env.size() - 1, p);
            } else env.add(p);
        }
        return env;
    }

    private static double[] interval(Profile prof, double delta) {
        List<Point> env = envelope(prof);
        int ib = 0;
        for (int k = 0; k < env.size(); k++) if (env.get(k).chi2Total < env.get(ib).chi2Total) ib = k;
        double thr = env.get(ib).chi2Total + delta;
        double lo = env.get(0).value, hi = env.get(env.size() - 1).value, loOpen = 1, hiOpen = 1;
        for (int k = ib; k > 0; k--) {
            Point a = env.get(k - 1), c = env.get(k);
            if (a.chi2Total > thr) {
                lo = a.value + (thr - a.chi2Total) / (c.chi2Total - a.chi2Total) * (c.value - a.value);
                loOpen = 0;
                break;
            }
        }
        for (int k = ib; k < env.size() - 1; k++) {
            Point a = env.get(k), c = env.get(k + 1);
            if (c.chi2Total > thr) {
                hi = a.value + (thr - a.chi2Total) / (c.chi2Total - a.chi2Total) * (c.value - a.value);
                hiOpen = 0;
                break;
            }
        }
        return new double[]{lo, hi, loOpen, hiOpen};
    }

    private static void parabola(Profile prof) {
        List<Point> env = envelope(prof);
        int ib = 0;
        for (int k = 0; k < env.size(); k++) if (env.get(k).chi2Total < env.get(ib).chi2Total) ib = k;
        if (ib == 0 || ib == env.size() - 1) return;
        double x0 = env.get(ib - 1).value, x1 = env.get(ib).value, x2 = env.get(ib + 1).value;
        double y0 = env.get(ib - 1).chi2Total, y1 = env.get(ib).chi2Total, y2 = env.get(ib + 1).chi2Total;
        double den = (x0 - x1) * (x0 - x2) * (x1 - x2);
        double A = (x2 * (y1 - y0) + x1 * (y0 - y2) + x0 * (y2 - y1)) / den;
        double B = (x2 * x2 * (y0 - y1) + x1 * x1 * (y2 - y0) + x0 * x0 * (y1 - y2)) / den;
        if (!(A > 0)) return;
        prof.parabolaCentre = -B / (2 * A);
        prof.parabolaHalfWidth68 = 1 / Math.sqrt(A);
        prof.parabolaHalfWidth95 = 2 / Math.sqrt(A);
    }
}
