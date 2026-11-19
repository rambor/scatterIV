package version4.FormFactors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A series of datasets measured against one varying condition (temperature, time, concentration, N/P, pH, applied
 * field): every dataset is analysed the same way and the results are tracked against the condition. This is how
 * time-resolved and thermotropic SAXS experiments are read &ndash; cubosome digestion going cubic to lamellar or to
 * inverse hexagonal, the Pn3m to HII transition of magnetocubosomes on heating, vesicles collapsing to discs on
 * adding surfactant (Caselli, Conti, De Santis &amp; Berti, Adv. Colloid Interface Sci. 327, 103156 (2024), sec. 4.1).
 *
 * The per-dataset analysis is the same code the single-sample tabs use; this class only repeats it and reads the
 * numbers out.
 */
public final class LnpSeries {

    /** how each dataset is analysed */
    public enum Mode {
        ASSESSMENT("Assessment only (fast): size, peaks, phase"),
        MODEL_FIT("Particle model fit (core + shells)"),
        LATTICE_FIT("Lattice fit (cubosome, hexosome, lamellar)");
        final String label;
        Mode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    /** a quantity tracked across the series */
    public enum Descriptor {
        RG("Guinier Rg", "nm"), DIAMETER("Outer diameter", "nm"), PD("Size spread σ/R₀", ""),
        MAIN_D("Main peak spacing d", "nm"), MAIN_AREA("Main peak area", ""),
        LATTICE_A("Lattice parameter a", "nm"), LATTICE_A2("Second phase a", "nm"),
        PHASE_FRACTION("Second phase share", "%"), DOMAIN("Ordered domain", "nm"),
        CHANNEL("Water channel radius", "nm"), DOPANT("Dopant diameter", "nm"),
        DISORDER_RATIO("Disordered / HII", ""), CHI2("Reduced χ²", "");
        public final String label, unit;
        Descriptor(String l, String u) { label = l; unit = u; }
        @Override public String toString() { return unit.isEmpty() ? label : label + " (" + unit + ")"; }
    }

    public static final class Point {
        public String name;
        public double condition;
        public LnpAssessment.Report assessment;
        public LnpFitter.Result fit;
        public LnpLattice.Result lattice;
        public LnpPhasePeaks.Result phases;
        public String error;
        /** phase label used for transition detection: a space group, "disordered", "none" or "?" */
        public String phase = "?";

        public double value(Descriptor d) {
            switch (d) {
                case RG: return assessment != null && assessment.guinier.found ? assessment.guinier.rg / 10 : Double.NaN;
                case DIAMETER: return fit != null ? 2 * fit.model.visibleOuterRadius(fit.params) / 10 : Double.NaN;
                case PD: return fit != null ? fit.params[LnpModel.SIGMA] / fit.params[LnpModel.R0] : Double.NaN;
                case MAIN_D: {
                    if (lattice != null) {
                        for (LnpLattice.Reflection r : lattice.reflections) if (r.inRange && r.area > 0) return r.d / 10;
                    }
                    if (fit != null && fit.model.peak) return 2 * Math.PI / fit.params[LnpModel.PEAK_Q] / 10;
                    LnpAssessment.Peak p = mainPeak();
                    return p == null ? Double.NaN : p.d() / 10;
                }
                case MAIN_AREA: {
                    if (lattice != null) {
                        for (LnpLattice.Reflection r : lattice.reflections) if (r.inRange && r.area > 0) return r.area;
                    }
                    LnpAssessment.Peak p = mainPeak();
                    return p == null ? Double.NaN : p.area;
                }
                case LATTICE_A: return lattice != null ? lattice.a / 10 : Double.NaN;
                case LATTICE_A2: return lattice != null && lattice.second != null ? lattice.second.a / 10 : Double.NaN;
                case PHASE_FRACTION: return lattice != null && lattice.second != null ? 100 * lattice.second.phaseShare : Double.NaN;
                case DOMAIN: return lattice != null ? lattice.domain / 10 : Double.NaN;
                case CHANNEL: return lattice != null ? lattice.channelRadius(17) / 10 : Double.NaN;
                case DOPANT: return lattice != null && lattice.dopantScale > 0 ? 2 * lattice.dopantR / 10 : Double.NaN;
                case DISORDER_RATIO: return phases != null ? phases.disorderedToHII() : Double.NaN;
                case CHI2: return lattice != null ? lattice.chi2Reduced : fit != null ? fit.chi2Reduced : Double.NaN;
                default: return Double.NaN;
            }
        }

        LnpAssessment.Peak mainPeak() {
            if (assessment == null || assessment.peaks.isEmpty()) return null;
            LnpAssessment.Peak b = assessment.peaks.get(0);
            for (LnpAssessment.Peak p : assessment.peaks) if (p.area > b.area) b = p;
            return b;
        }
    }

    public static final class Result {
        public final List<Point> points = new ArrayList<>();
        public Mode mode;
        public String conditionName = "condition", conditionUnit = "";
        public final List<String> notes = new ArrayList<>();

        /** descriptors that have at least two finite values */
        public List<Descriptor> available() {
            List<Descriptor> out = new ArrayList<>();
            for (Descriptor d : Descriptor.values()) {
                int n = 0;
                for (Point p : points) if (!Double.isNaN(p.value(d))) n++;
                if (n >= 2) out.add(d);
            }
            return out;
        }
    }

    /** what each dataset of the series needs: its name, its condition and its data */
    public interface Source {
        int count();
        String name(int i);
        double condition(int i);
        double[][] data(int i);
    }

    /** settings carried over from the single-sample tabs so the series is analysed the same way */
    public static final class Settings {
        public LnpModel model = new LnpModel(3, true);
        public double[] start = LnpModel.defaults();
        public boolean[] fixed = LnpModel.defaultFixed();
        public boolean physical = true, autoStart = true;
        public LnpLattice.Lattice lattice;          // null = identify for each dataset
        public LnpLattice.Lattice second;           // null = single phase
        public double latticeA = Double.NaN, qmin = Double.NaN, qmax = Double.NaN,
                extraQ = Double.NaN, instrument = 0, dopant = Double.NaN;
        public LnpLattice.Shape shape = LnpLattice.Shape.GAUSSIAN;
        public boolean phaseDecomposition;
    }

    public interface Progress { void at(int i, int n, String name); boolean cancelled(); }

    public static Result run(Source src, Mode mode, Settings cfg, Progress progress) {
        Result r = new Result();
        r.mode = mode;
        double aCarry = cfg.latticeA;
        // A series is one sample under a changing condition, so the phase is identified once and then kept: letting
        // every dataset pick its own lattice makes the assignment jump about when two phases coexist.
        LnpLattice.Lattice lat = cfg.lattice;
        double aCarry2 = Double.NaN;
        if (mode == Mode.LATTICE_FIT && lat == null && src.count() > 0) {
            try {
                double[][] d0 = src.data(0);
                double qlo0 = Double.isNaN(cfg.qmin) ? d0[0][0] : cfg.qmin;
                double qhi0 = Double.isNaN(cfg.qmax) ? d0[0][d0[0].length - 1] : cfg.qmax;
                List<LnpLattice.Result> all = LnpLattice.identify(d0[0], d0[1], d0[2], qlo0, qhi0, cfg.shape, cfg.extraQ, cfg.instrument, 1);
                if (!all.isEmpty()) {
                    lat = all.get(0).lattice;
                    aCarry = all.get(0).a;
                    r.notes.add(String.format("Phase identified on the first dataset (%s, a = %.1f Å) and kept for the whole series.",
                            lat.symmetry, all.get(0).a));
                }
            } catch (RuntimeException ex) {
                r.notes.add("Could not identify a lattice on the first dataset: " + ex.getMessage());
            }
        }
        for (int i = 0; i < src.count(); i++) {
            if (progress != null) {
                if (progress.cancelled()) { r.notes.add("Stopped after " + i + " datasets."); break; }
                progress.at(i, src.count(), src.name(i));
            }
            Point p = new Point();
            p.name = src.name(i);
            p.condition = src.condition(i);
            try {
                double[][] d = src.data(i);
                p.assessment = LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN);
                double qlo = Double.isNaN(cfg.qmin) ? d[0][0] : cfg.qmin;
                double qhi = Double.isNaN(cfg.qmax) ? d[0][d[0].length - 1] : cfg.qmax;
                if (mode == Mode.MODEL_FIT)
                    p.fit = new LnpFitter(cfg.model, d[0], d[1], d[2]).physicalConstraints(cfg.physical)
                            .fit(cfg.start, cfg.fixed, cfg.autoStart, null);
                else if (mode == Mode.LATTICE_FIT) {
                    if (lat == null) {
                        List<LnpLattice.Result> all = LnpLattice.identify(d[0], d[1], d[2], qlo, qhi, cfg.shape, cfg.extraQ, cfg.instrument, 1);
                        if (!all.isEmpty()) p.lattice = all.get(0);
                    } else {
                        // the previous dataset's lattice parameter is the starting point: in a series the lattice
                        // usually moves gradually, so this keeps the fits on the same branch
                        double a0 = Double.isNaN(aCarry) ? 2 * Math.PI * lat.m[0] / (0.5 * (qlo + qhi)) : aCarry;
                        double a2 = Double.isNaN(aCarry2) ? a0 * safeRatio(cfg.second, lat) : aCarry2;
                        p.lattice = cfg.second == null
                                ? LnpLattice.fit(d[0], d[1], d[2], lat, a0, qlo, qhi, cfg.shape, cfg.extraQ, cfg.instrument, cfg.dopant)
                                : LnpLattice.fitPair(d[0], d[1], d[2], lat, a0, cfg.second, a2, qlo, qhi, cfg.shape, cfg.extraQ, cfg.instrument, cfg.dopant);
                        if (p.lattice != null && p.lattice.second != null) aCarry2 = p.lattice.second.a;
                    }
                    if (p.lattice != null) aCarry = p.lattice.a;
                }
                if (cfg.phaseDecomposition) {
                    try {
                        p.phases = LnpPhasePeaks.fit(d[0], d[1], d[2], LnpPhasePeaks.hammelDefaults(), 0.07, 0.17, true);
                    } catch (RuntimeException ignored) { }
                }
                p.phase = phaseOf(p);
            } catch (RuntimeException ex) {
                p.error = ex.getMessage();
            }
            r.points.add(p);
        }
        r.points.sort((a, b) -> Double.compare(a.condition, b.condition));
        return r;
    }

    private static double safeRatio(LnpLattice.Lattice to, LnpLattice.Lattice from) {
        double a = LnpLattice.bonnetRatio(to), b = LnpLattice.bonnetRatio(from);
        return Double.isNaN(a) || Double.isNaN(b) ? 1 : a / b;
    }

    static String phaseOf(Point p) {
        if (p.lattice != null) {
            String s = p.lattice.lattice.symmetry.split(",")[0];
            if (p.lattice.second != null && p.lattice.second.phaseShare > 0.1)
                return s + " + " + p.lattice.second.lattice.symmetry.split(",")[0];
            return s;
        }
        if (p.assessment == null) return "?";
        if (p.assessment.coexistence != null)
            return p.assessment.coexistence[0].symmetry.split(",")[0] + " + " + p.assessment.coexistence[1].symmetry.split(",")[0];
        if (!p.assessment.phases.isEmpty()) return p.assessment.phases.get(0).symmetry.split(",")[0];
        if (p.assessment.mesophasePeak) return "disordered";
        return p.assessment.peaks.isEmpty() ? "none" : "unindexed";
    }

    // ------------------------------------------------------------------ trends and transitions

    /** weighted straight-line fit of a descriptor against the condition: {slope, slope error, r², n} */
    public static double[] trend(Result r, Descriptor d) {
        int n = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (Point p : r.points) {
            double y = p.value(d);
            if (Double.isNaN(y) || Double.isNaN(p.condition)) continue;
            sx += p.condition; sy += y; sxx += p.condition * p.condition; sxy += p.condition * y; syy += y * y;
            n++;
        }
        if (n < 3) return new double[]{Double.NaN, Double.NaN, Double.NaN, n};
        double det = n * sxx - sx * sx;
        if (det == 0) return new double[]{Double.NaN, Double.NaN, Double.NaN, n};
        double slope = (n * sxy - sx * sy) / det, icpt = (sy - slope * sx) / n;
        double ss = 0;
        for (Point p : r.points) {
            double y = p.value(d);
            if (Double.isNaN(y)) continue;
            double e = y - (icpt + slope * p.condition);
            ss += e * e;
        }
        double sigma2 = ss / Math.max(1, n - 2);
        double slopeErr = Math.sqrt(sigma2 * n / det);
        double meanY = sy / n, tot = syy - n * meanY * meanY;
        double r2 = tot > 0 ? 1 - ss / tot : Double.NaN;
        return new double[]{slope, slopeErr, r2, n};
    }

    /** conditions where the phase assignment changes */
    public static List<String> transitions(Result r) {
        List<String> out = new ArrayList<>();
        Point prev = null;
        for (Point p : r.points) {
            if (p.error != null) continue;
            if (prev != null && !prev.phase.equals(p.phase) && !p.phase.equals("?") && !prev.phase.equals("?"))
                out.add(String.format("%s → %s between %s = %s and %s", prev.phase, p.phase, r.conditionName,
                        Fields.fmt(prev.condition), Fields.fmt(p.condition)));
            prev = p;
        }
        // midpoint of a two-phase crossover, when both shares are tracked
        Point below = null, above = null;
        for (Point p : r.points) {
            double f = p.value(Descriptor.PHASE_FRACTION);
            if (Double.isNaN(f)) continue;
            if (f < 50) below = p;
            if (f >= 50 && above == null && below != null) above = p;
        }
        if (below != null && above != null) {
            double f1 = below.value(Descriptor.PHASE_FRACTION), f2 = above.value(Descriptor.PHASE_FRACTION);
            double mid = below.condition + (50 - f1) * (above.condition - below.condition) / (f2 - f1);
            out.add(String.format("The two phases are equal in amount at %s ≈ %s", r.conditionName, Fields.fmt(mid)));
        }
        return out;
    }

    public static String describe(Result r) {
        StringBuilder sb = new StringBuilder("SERIES – ").append(r.points.size()).append(" datasets against ")
                .append(r.conditionName).append(r.conditionUnit.isEmpty() ? "" : " (" + r.conditionUnit + ")").append('\n');
        sb.append(r.mode.label).append("\n\n");
        List<Descriptor> cols = r.available();
        sb.append(String.format("  %-22s %10s %-18s", "dataset", r.conditionName, "phase"));
        for (Descriptor d : cols) sb.append(String.format(" %14s", d.label.length() > 14 ? d.label.substring(0, 14) : d.label));
        sb.append('\n');
        for (Point p : r.points) {
            sb.append(String.format("  %-22s %10s %-18s", trim(p.name, 22), Fields.fmt(p.condition), p.error != null ? "failed" : p.phase));
            for (Descriptor d : cols) {
                double v = p.value(d);
                sb.append(String.format(" %14s", Double.isNaN(v) ? "–" : Fields.fmt(v)));
            }
            sb.append('\n');
            if (p.error != null) sb.append("      ! ").append(p.error).append('\n');
        }
        List<String> tr = transitions(r);
        sb.append("\nTransitions\n");
        if (tr.isEmpty()) sb.append("  No change of phase across the series.\n");
        else for (String t : tr) sb.append("  • ").append(t).append('\n');

        sb.append("\nTrends (straight line against ").append(r.conditionName).append(")\n");
        for (Descriptor d : cols) {
            double[] t = trend(r, d);
            if (Double.isNaN(t[0])) { sb.append(String.format("  %-24s too few points%n", d.label)); continue; }
            boolean sig = Math.abs(t[0]) > 2 * t[1];
            sb.append(String.format("  %-24s %+10.4g %s per %s  (±%.2g, r² %.2f, n %.0f)  %s%n", d.label, t[0],
                    d.unit.isEmpty() ? "" : d.unit, r.conditionUnit.isEmpty() ? r.conditionName : r.conditionUnit,
                    t[1], t[2], t[3], sig ? "" : "not significant"));
        }
        sb.append("\nEach row is the same analysis the single-sample tabs run; open a dataset there to check any point.\n");
        for (String n : r.notes) sb.append("! ").append(n).append('\n');
        return sb.toString();
    }

    private static String trim(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    /** series table as CSV */
    public static String csv(Result r) {
        StringBuilder sb = new StringBuilder("dataset,").append(r.conditionName).append(",phase");
        List<Descriptor> cols = r.available();
        for (Descriptor d : cols) sb.append(',').append(d.label).append(d.unit.isEmpty() ? "" : " (" + d.unit + ")");
        sb.append('\n');
        for (Point p : r.points) {
            sb.append('"').append(p.name == null ? "" : p.name.replace("\"", "'")).append('"').append(',').append(p.condition).append(',').append(p.phase);
            for (Descriptor d : cols) {
                double v = p.value(d);
                sb.append(',').append(Double.isNaN(v) ? "" : String.valueOf(v));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** descriptor values as {condition, value} pairs, skipping missing points */
    public static Map<String, double[][]> curves(Result r, List<Descriptor> ds) {
        Map<String, double[][]> out = new LinkedHashMap<>();
        for (Descriptor d : ds) {
            List<double[]> pts = new ArrayList<>();
            for (Point p : r.points) {
                double v = p.value(d);
                if (!Double.isNaN(v)) pts.add(new double[]{p.condition, v});
            }
            if (pts.size() >= 2) out.put(d.toString(), pts.toArray(new double[0][]));
        }
        return out;
    }
}
