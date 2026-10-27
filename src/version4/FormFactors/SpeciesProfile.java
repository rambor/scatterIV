package version4.FormFactors;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Profile scan of a two-species population (e.g. empty / filled cores): the NUMBER fraction of species 0 is held
 * at a grid of values (stiff penalty), everything else (geometry distribution, which particle carries which label)
 * is re-optimised, and the best attainable chi^2 is recorded. (Fixing the intensity share instead does not work:
 * the geometry compensates and the number fraction barely moves.) The confidence interval on the species-0 number
 * fraction is read off where the total chi^2 rises by 1 (68 %) and 4 (95 %) above its minimum.
 *
 * The best point is repeated with new random seeds; the spread of chi^2_total between repeats ("optimiser
 * noise") is reported. If it is comparable to 1, the interval is limited by optimisation precision, not by the data.
 */
public final class SpeciesProfile {

    public interface Listener { void point(int index, int total, double intensityFraction, double chi2Total); }

    /** lets a GUI stop a scan (also stops the fit that is running) and watch progress inside each point */
    public static final class Control {
        volatile boolean cancelled;
        volatile LangevinFitter current;
        public volatile LangevinFitter.ProgressListener inner;   // progress of the fit at the current point
        public volatile int pointIndex, pointTotal;
        public void cancel() { cancelled = true; LangevinFitter f = current; if (f != null) f.cancel(); }
        public boolean isCancelled() { return cancelled; }
    }

    public static final class Point {
        public double intensityFraction, numberFraction, volumeFraction, chi2RedMin, chi2Total, chi2RedFit;
        public long seed;
        public LangevinResult result;
    }

    public static final class Profile {
        public String speciesName;
        public List<Point> points = new ArrayList<>();
        public int dof;
        public double bestNumberFraction, bestChi2Total;
        public double lo68, hi68, lo95, hi95;
        public boolean lo68Open, hi68Open, lo95Open, hi95Open;   // interval reaches the 0 / 1 boundary
        public double optimizerNoise;                             // sd of chi2_total over repeats at the best point
        /** parabola chi2 = a (f - f0)^2 + c through the lowest point and its two neighbours (lower envelope) */
        public double parabolaCentre = Double.NaN, parabolaHalfWidth68 = Double.NaN, parabolaHalfWidth95 = Double.NaN;
        public Point best;

        public String toRemarks() {
            StringBuilder sb = new StringBuilder();
            sb.append("REMARK 265\nREMARK 265 SPECIES PROFILE : ").append(speciesName).append(" FRACTION\n");
            sb.append("REMARK 265  TARGET-NUMBER  NUMBER-FRACTION  VOLUME-FRACTION  CHI2_RED(MIN)  DELTA CHI2_TOTAL\n");
            List<Point> srt = new ArrayList<>(points);
            srt.sort(Comparator.comparingDouble(p -> p.numberFraction));
            for (Point p : srt)
                sb.append(String.format("REMARK 265  %14.3f %16.3f %16.3f %14.4f %16.2f%n", p.intensityFraction, p.numberFraction,
                        p.volumeFraction, p.chi2RedMin, p.chi2Total - bestChi2Total));
            sb.append(String.format("REMARK 265  BEST %s NUMBER FRACTION : %.3f%n", speciesName.toUpperCase(), bestNumberFraction));
            sb.append(String.format("REMARK 265        68 %% INTERVAL (dchi2=1) : %s%.3f - %.3f%s%n", lo68Open ? "<=" : "", lo68, hi68, hi68Open ? " (open)" : ""));
            sb.append(String.format("REMARK 265        95 %% INTERVAL (dchi2=4) : %s%.3f - %.3f%s%n", lo95Open ? "<=" : "", lo95, hi95, hi95Open ? " (open)" : ""));
            if (!Double.isNaN(parabolaCentre))
                sb.append(String.format("REMARK 265  PARABOLIC FIT (3 lowest points)  : %.4f +- %.4f (68 %%), +- %.4f (95 %%)%n",
                        parabolaCentre, parabolaHalfWidth68, parabolaHalfWidth95));
            sb.append(String.format("REMARK 265  OPTIMISER NOISE (sd chi2_total) : %.2f%s%n", optimizerNoise,
                    optimizerNoise > 0.5 ? "   *** comparable to dchi2 = 1: interval limited by optimisation, widen or add repeats" : ""));
            return sb.toString();
        }

        public void writeCsv(Path file) throws IOException {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
                w.println("intensity_share,number_fraction,volume_fraction,chi2_red_min,chi2_total,delta_chi2,seed");
                List<Point> srt = new ArrayList<>(points);
                srt.sort(Comparator.comparingDouble(p -> p.numberFraction));
                for (Point p : srt)
                    w.printf("%.4f,%.5f,%.5f,%.6f,%.4f,%.4f,%d%n", p.intensityFraction, p.numberFraction, p.volumeFraction,
                            p.chi2RedMin, p.chi2Total, p.chi2Total - bestChi2Total, p.seed);
            }
        }
    }

    private SpeciesProfile() {}

    /**
     * @param coarseStep grid step of the target number fraction (e.g. 0.1 -> 0, 0.1, ..., 1)
     * @param repeats    extra runs at the best point with new seeds (optimiser-noise estimate), e.g. 2
     */
    public static Profile run(double[] q, double[] I, double[] sigma, ShapeFamily family, double[] min, double[] max,
                              LangevinSettings base, double coarseStep, int repeats, Listener listener) {
        return run(q, I, sigma, family, min, max, base, coarseStep, repeats, listener, new Control());
    }

    /** as above, cancellable; returns null when cancelled */
    public static Profile run(double[] q, double[] I, double[] sigma, ShapeFamily family, double[] min, double[] max,
                              LangevinSettings base, double coarseStep, int repeats, Listener listener, Control ctl) {
        CTL.set(ctl);
        try {
            return runInner(q, I, sigma, family, min, max, base, coarseStep, repeats, listener, ctl);
        } finally {
            CTL.remove();
        }
    }

    private static final ThreadLocal<Control> CTL = new ThreadLocal<>();

    private static Profile runInner(double[] q, double[] I, double[] sigma, ShapeFamily family, double[] min, double[] max,
                                    LangevinSettings base, double coarseStep, int repeats, Listener listener, Control ctl) {
        Profile prof = new Profile();
        List<Double> grid = new ArrayList<>();
        int nc = (int) Math.round(1.0 / coarseStep);
        for (int k = 0; k <= nc; k++) grid.add(Math.min(1.0, k * coarseStep));
        int total = grid.size() + 4 + repeats, done = 0;

        ctl.pointTotal = total;
        for (double f : grid) {
            if (ctl.cancelled) return null;
            ctl.pointIndex = done;
            Point p = point(q, I, sigma, family, min, max, base, f, base.seed, prof);
            if (p == null) return null;
            prof.points.add(p);
            if (listener != null) listener.point(done++, total, f, p.chi2Total);
        }

        // refine around the coarse minimum
        Point b = bestOf(prof.points);
        for (double df : new double[]{-0.5, -0.25, 0.25, 0.5}) {
            double f = b.intensityFraction + df * coarseStep;
            if (f < 0 || f > 1) continue;
            if (ctl.cancelled) return null;
            ctl.pointIndex = done;
            Point p = point(q, I, sigma, family, min, max, base, f, base.seed, prof);
            if (p == null) return null;
            prof.points.add(p);
            if (listener != null) listener.point(done++, total, f, p.chi2Total);
        }
        b = bestOf(prof.points);

        // adaptive bisection: while a neighbour of the best point is far above the dchi2 = 4 level, the interval
        // is not resolved by the grid (linear interpolation of a steep parabola squeezes it). Bisect towards it.
        for (int extra = 0; extra < 6; extra++) {
            Point[] nb = neighbours(prof.points, b);
            Point target = null;
            double worst = 9.0;
            for (Point n : nb)
                if (n != null && n.chi2Total - b.chi2Total > worst && Math.abs(n.intensityFraction - b.intensityFraction) > 0.004) {
                    worst = n.chi2Total - b.chi2Total;
                    target = n;
                }
            if (target == null) break;
            if (ctl.cancelled) return null;
            double f = 0.5 * (b.intensityFraction + target.intensityFraction);
            ctl.pointTotal = ++total;
            ctl.pointIndex = done;
            Point p = point(q, I, sigma, family, min, max, base, f, base.seed, prof);
            if (p == null) return null;
            prof.points.add(p);
            if (listener != null) listener.point(done++, total, f, p.chi2Total);
            b = bestOf(prof.points);
        }

        // optimiser noise at the best point
        List<Double> chis = new ArrayList<>();
        chis.add(b.chi2Total);
        for (int r = 1; r <= repeats; r++) {
            if (ctl.cancelled) return null;
            ctl.pointIndex = done;
            Point p = point(q, I, sigma, family, min, max, base, b.intensityFraction, base.seed + 104729L * r, prof);
            if (p == null) return null;
            chis.add(p.chi2Total);
            prof.points.add(p);
            if (listener != null) listener.point(done++, total, b.intensityFraction, p.chi2Total);
        }
        double m = 0, v = 0;
        for (double c : chis) m += c;
        m /= chis.size();
        for (double c : chis) v += (c - m) * (c - m);
        prof.optimizerNoise = chis.size() > 1 ? Math.sqrt(v / (chis.size() - 1)) : Double.NaN;

        // profile = lowest chi2 at each intensity share (repeats included)
        b = bestOf(prof.points);
        prof.best = b;
        prof.bestChi2Total = b.chi2Total;
        prof.bestNumberFraction = b.numberFraction;
        prof.speciesName = b.result.speciesNames != null ? b.result.speciesNames[0]
                : base.speciesNames != null && base.speciesNames.length > 0 ? base.speciesNames[0] : "species 0";
        double[] i68 = interval(prof, 1.0), i95 = interval(prof, 4.0);
        prof.lo68 = i68[0]; prof.hi68 = i68[1]; prof.lo68Open = i68[2] > 0; prof.hi68Open = i68[3] > 0;
        prof.lo95 = i95[0]; prof.hi95 = i95[1]; prof.lo95Open = i95[2] > 0; prof.hi95Open = i95[3] > 0;
        parabola(prof);
        return prof;
    }

    /** parabola through the lowest envelope point and its neighbours in number fraction */
    private static void parabola(Profile prof) {
        List<Point> env = new ArrayList<>();
        List<Point> srt = new ArrayList<>(prof.points);
        srt.sort(Comparator.comparingDouble(p -> p.intensityFraction));
        for (Point p : srt) {
            if (!env.isEmpty() && Math.abs(env.get(env.size() - 1).intensityFraction - p.intensityFraction) < 1e-9) {
                if (p.chi2Total < env.get(env.size() - 1).chi2Total) env.set(env.size() - 1, p);
            } else env.add(p);
        }
        env.sort(Comparator.comparingDouble(p -> p.numberFraction));
        int ib = 0;
        for (int k = 0; k < env.size(); k++) if (env.get(k).chi2Total < env.get(ib).chi2Total) ib = k;
        if (ib == 0 || ib == env.size() - 1) return;
        double x0 = env.get(ib - 1).numberFraction, x1 = env.get(ib).numberFraction, x2 = env.get(ib + 1).numberFraction;
        double y0 = env.get(ib - 1).chi2Total, y1 = env.get(ib).chi2Total, y2 = env.get(ib + 1).chi2Total;
        double den = (x0 - x1) * (x0 - x2) * (x1 - x2);
        double A = (x2 * (y1 - y0) + x1 * (y0 - y2) + x0 * (y2 - y1)) / den;
        double B = (x2 * x2 * (y0 - y1) + x1 * x1 * (y2 - y0) + x0 * x0 * (y1 - y2)) / den;
        if (!(A > 0)) return;
        prof.parabolaCentre = -B / (2 * A);
        prof.parabolaHalfWidth68 = 1.0 / Math.sqrt(A);
        prof.parabolaHalfWidth95 = 2.0 / Math.sqrt(A);
    }

    /** nearest lower and higher target fraction around b, lowest chi2 per target */
    private static Point[] neighbours(List<Point> pts, Point b) {
        Point lo = null, hi = null;
        for (Point p : pts) {
            double d = p.intensityFraction - b.intensityFraction;
            if (d < -1e-9) {
                if (lo == null || p.intensityFraction > lo.intensityFraction + 1e-9
                        || (Math.abs(p.intensityFraction - lo.intensityFraction) < 1e-9 && p.chi2Total < lo.chi2Total)) lo = p;
            } else if (d > 1e-9) {
                if (hi == null || p.intensityFraction < hi.intensityFraction - 1e-9
                        || (Math.abs(p.intensityFraction - hi.intensityFraction) < 1e-9 && p.chi2Total < hi.chi2Total)) hi = p;
            }
        }
        return new Point[]{lo, hi};
    }

    private static Point last(Profile p) { return p.points.get(p.points.size() - 1); }

    private static Point bestOf(List<Point> pts) {
        Point b = pts.get(0);
        for (Point p : pts) if (p.chi2Total < b.chi2Total) b = p;
        return b;
    }

    private static Point point(double[] q, double[] I, double[] sigma, ShapeFamily family, double[] min, double[] max,
                               LangevinSettings base, double f, long seed, Profile prof) {
        LangevinSettings s = base.copy();
        s.fixedSpeciesFraction = f;
        s.seed = seed;
        s.replicas = 1;
        // chi2_min comes from the quench; a short heat/sample phase is enough for the reported fractions
        s.equilibrationSteps = Math.min(base.equilibrationSteps, 300);
        s.sampleSteps = Math.min(base.sampleSteps, 400);
        LangevinFitter fit = new LangevinFitter(q, I, sigma, family, min, max, s);
        Control ctl = CTL.get();
        if (ctl != null) {
            ctl.current = fit;
            if (ctl.cancelled) fit.cancel();
        }
        LangevinResult r = fit.fit(ctl != null ? ctl.inner : null);
        if (ctl != null) ctl.current = null;
        if (r == null || (ctl != null && ctl.cancelled)) return null;
        Point p = new Point();
        p.intensityFraction = f;
        p.seed = seed;
        p.result = r;
        // shared geometry: the number and volume fraction of species 0 are exactly f
        p.numberFraction = !s.sharedGeometry && r.speciesNumberFraction != null ? r.speciesNumberFraction[0] : f;
        p.volumeFraction = !s.sharedGeometry && r.speciesVolumeFraction != null ? r.speciesVolumeFraction[0] : f;
        p.chi2RedMin = r.chi2Best[0];
        p.chi2RedFit = r.chi2Reduced;
        prof.dof = Math.max(1, r.q.length - (s.fitBackground ? 2 : 1));
        p.chi2Total = p.chi2RedMin * prof.dof;
        return p;
    }

    /** {lo, hi, loOpen, hiOpen} of the number fraction where the lower envelope of chi2_total <= min + delta */
    private static double[] interval(Profile prof, double delta) {
        // lower envelope per intensity share, ordered by number fraction
        List<Point> env = new ArrayList<>();
        List<Point> srt = new ArrayList<>(prof.points);
        srt.sort(Comparator.comparingDouble(p -> p.intensityFraction));
        for (Point p : srt) {
            if (!env.isEmpty() && Math.abs(env.get(env.size() - 1).intensityFraction - p.intensityFraction) < 1e-9) {
                if (p.chi2Total < env.get(env.size() - 1).chi2Total) env.set(env.size() - 1, p);
            } else env.add(p);
        }
        env.sort(Comparator.comparingDouble(p -> p.numberFraction));
        int ib = 0;
        for (int k = 0; k < env.size(); k++) if (env.get(k).chi2Total < env.get(ib).chi2Total) ib = k;
        double thr = env.get(ib).chi2Total + delta;
        double lo = env.get(0).numberFraction, hi = env.get(env.size() - 1).numberFraction;
        double loOpen = 1, hiOpen = 1;
        for (int k = ib; k > 0; k--) {
            Point a = env.get(k - 1), c = env.get(k);
            if (a.chi2Total > thr) {
                lo = a.numberFraction + (thr - a.chi2Total) / (c.chi2Total - a.chi2Total) * (c.numberFraction - a.numberFraction);
                loOpen = 0;
                break;
            }
        }
        for (int k = ib; k < env.size() - 1; k++) {
            Point a = env.get(k), c = env.get(k + 1);
            if (c.chi2Total > thr) {
                hi = a.numberFraction + (thr - a.chi2Total) / (c.chi2Total - a.chi2Total) * (c.numberFraction - a.numberFraction);
                hiOpen = 0;
                break;
            }
        }
        return new double[]{lo, hi, loOpen, hiOpen};
    }
}
