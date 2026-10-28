package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;

/**
 * Network / hydrogel scattering models (shape-independent), fitted by bounded Levenberg-Marquardt from several starts.
 * Every model has a flat background b (fitted or fixed at 0). Equations follow SasView / sasmodels where a model exists
 * there (named in brackets); all were checked numerically against sasmodels.
 *
 * <pre>
 *  OZ             Ornstein-Zernike [lorentz]            I0 / (1 + q^2 xi^2)
 *  DAB            Debye-Bueche [dab]                    I0 / (1 + q^2 a^2)^2
 *  OZ_DAB         dynamic + static (Geissler, Mallam)   I_OZ / (1 + q^2 xi^2) + I_DB / (1 + q^2 a^2)^2
 *  GAUSS_LORENTZ  [gauss_lorentz_gel]                   G exp(-q^2 Xi^2 / 2) + L / (1 + q^2 xi^2)
 *  GEL_FIT        [gel_fit] (fractal network + cluster) L / (1 + (D+1) q^2 xi^2 / 3)^(D/2) + G exp(-q^2 Rg^2 / 3)
 *  CORRELATION    [correlation_length] (Hammouda)       A / q^n + C / (1 + (q xi)^m)
 *  TWO_LORENTZIAN [two_lorentzian]                      L1 / (1 + (q xi1)^m1) + L2 / (1 + (q xi2)^m2)
 *  BROAD_PEAK     [broad_peak] (charged gels)           (A / q^n + C / (1 + (|q - q0| xi)^m))^s
 *  UNIFIED2       [unified_power_Rg, 2 levels] (Beaucage)
 *                 sum_i G_i exp(-q^2 Rg_i^2/3) + B_i exp(-q^2 Rg_{i+1}^2/3) (erf(q Rg_i/sqrt 6)^3 / q)^P_i, Rg_3 = 0
 * </pre>
 */
public final class GelModels {

    private GelModels() {}

    /** parameter transform: FREE (any real), POS (fitted in log), BOUNDED (logistic between lo and hi) */
    enum T { FREE, POS, BOUNDED }

    public enum Model {
        OZ("Ornstein–Zernike (mesh size)",
                new String[]{"I_OZ", "xi"}, new String[]{"", " A"}),
        DAB("Debye–Bueche (static inhomogeneities)",
                new String[]{"I_DB", "a"}, new String[]{"", " A"}),
        OZ_DAB("Ornstein–Zernike + Debye–Bueche",
                new String[]{"I_OZ", "xi", "I_DB", "a"}, new String[]{"", " A", "", " A"}),
        GAUSS_LORENTZ("Gauss–Lorentz gel",
                new String[]{"G", "Xi_static", "L", "xi_dynamic"}, new String[]{"", " A", "", " A"}),
        GEL_FIT("Gel fit (fractal network + clusters)",
                new String[]{"L", "xi", "D", "G", "Rg"}, new String[]{"", " A", "", "", " A"}),
        CORRELATION("Correlation length (Hammouda)",
                new String[]{"A", "n", "C", "xi", "m"}, new String[]{"", "", "", " A", ""}),
        TWO_LORENTZIAN("Two Lorentzian",
                new String[]{"L1", "xi1", "m1", "L2", "xi2", "m2"}, new String[]{"", " A", "", "", " A", ""}),
        BROAD_PEAK("Broad peak (charged gel)",
                new String[]{"A", "n", "C", "xi", "q0", "m", "s"}, new String[]{"", "", "", " A", " A^-1", "", ""}),
        UNIFIED2("Unified Beaucage, 2 levels",
                new String[]{"G1", "Rg1", "B1", "P1", "G2", "Rg2", "B2", "P2"}, new String[]{"", " A", "", "", "", " A", "", ""});

        public final String label;
        final String[] names, units;

        /** short name for tables */
        public String shortLabel() {
            switch (this) {
                case OZ: return "Ornstein-Zernike";
                case DAB: return "Debye-Bueche";
                case OZ_DAB: return "OZ + Debye-Bueche";
                case GAUSS_LORENTZ: return "Gauss-Lorentz gel";
                case GEL_FIT: return "Gel fit";
                case CORRELATION: return "Correlation length";
                case TWO_LORENTZIAN: return "Two Lorentzian";
                case BROAD_PEAK: return "Broad peak";
                default: return "Unified (2 levels)";
            }
        }

        Model(String label, String[] names, String[] units) {
            this.label = label;
            this.names = names;
            this.units = units;
        }

        @Override public String toString() { return label; }

        public String[] parameterNames() { return names.clone(); }
    }

    // ------------------------------------------------------------------ model functions (p without background)

    public static double evaluate(Model m, double[] p, double q) {
        switch (m) {
            case OZ: return p[0] / (1 + q * q * p[1] * p[1]);
            case DAB: { double d = 1 + q * q * p[1] * p[1]; return p[0] / (d * d); }
            case OZ_DAB: { double d = 1 + q * q * p[3] * p[3]; return p[0] / (1 + q * q * p[1] * p[1]) + p[2] / (d * d); }
            case GAUSS_LORENTZ: return p[0] * Math.exp(-q * q * p[1] * p[1] / 2) + p[2] / (1 + q * q * p[3] * p[3]);
            case GEL_FIT: {
                double D = p[2];
                double l = Math.pow(1 + (D + 1) / 3 * q * q * p[1] * p[1], D / 2);
                return p[0] / l + p[3] * Math.exp(-q * q * p[4] * p[4] / 3);
            }
            case CORRELATION: return p[0] / Math.pow(q, p[1]) + p[2] / (1 + Math.pow(q * p[3], p[4]));
            case TWO_LORENTZIAN: return p[0] / (1 + Math.pow(q * p[1], p[2])) + p[3] / (1 + Math.pow(q * p[4], p[5]));
            case BROAD_PEAK: {
                double z = Math.abs(q - p[4]) * p[3];
                return Math.pow(p[0] / Math.pow(q, p[1]) + p[2] / (1 + Math.pow(z, p[5])), p[6]);
            }
            case UNIFIED2: {
                double s6 = Math.sqrt(6);
                double r1 = p[1], r2 = p[5];
                // level 1 (small structure), level 2 (large structure); the power law of level i is cut off by Rg_{i+1}
                // sasmodels orders levels by index; level 1 = first; its power law is cut by level 2's Rg
                double l1 = p[0] * Math.exp(-q * q * r1 * r1 / 3)
                        + p[2] * Math.exp(-q * q * r2 * r2 / 3) * Math.pow(cube(erf(q * r1 / s6)) / q, p[3]);
                double l2 = p[4] * Math.exp(-q * q * r2 * r2 / 3) + p[6] * Math.pow(cube(erf(q * r2 / s6)) / q, p[7]);
                return l1 + l2;
            }
            default: throw new IllegalStateException();
        }
    }

    /** named components for plotting (background not included) */
    public static List<String> componentNames(Model m) {
        List<String> c = new ArrayList<>();
        switch (m) {
            case OZ_DAB: c.add("Ornstein–Zernike"); c.add("Debye–Bueche"); break;
            case GAUSS_LORENTZ: c.add("Gaussian (static)"); c.add("Lorentzian (dynamic)"); break;
            case GEL_FIT: c.add("network (Lorentzian, D)"); c.add("clusters (Guinier)"); break;
            case CORRELATION: c.add("power law A/qⁿ"); c.add("Lorentzian C/(1+(qξ)ᵐ)"); break;
            case TWO_LORENTZIAN: c.add("Lorentzian 1"); c.add("Lorentzian 2"); break;
            case UNIFIED2: c.add("level 1"); c.add("level 2"); break;
            default: break;
        }
        return c;
    }

    public static double component(Model m, int k, double[] p, double q) {
        double[] z = p.clone();
        switch (m) {
            case OZ_DAB: if (k == 0) z[2] = 0; else z[0] = 0; break;
            case GAUSS_LORENTZ: if (k == 0) z[2] = 0; else z[0] = 0; break;
            case GEL_FIT: if (k == 0) z[3] = 0; else z[0] = 0; break;
            case CORRELATION: if (k == 0) z[2] = 0; else z[0] = 0; break;
            case TWO_LORENTZIAN: if (k == 0) z[3] = 0; else z[0] = 0; break;
            case UNIFIED2: if (k == 0) { z[4] = 0; z[6] = 0; } else { z[0] = 0; z[2] = 0; } break;
            default: break;
        }
        return evaluate(m, z, q);
    }

    private static double cube(double x) { return x * x * x; }

    /** REMARK lines stay plain ASCII for older readers */
    static String ascii(String s) {
        return s.replace("–", "-").replace("ν", "nu").replace("≈", "~").replace("→", "->").replace("π", "pi")
                .replace("ξ", "xi").replace("ⁿ", "^n").replace("ᵐ", "^m").replace("±", "+-").replace("χ²", "chi2").replace("Å", "A");
    }

    /** erf, Abramowitz-Stegun 7.1.26 refined by one Newton-like series switch (|err| < 1.2e-7) */
    public static double erf(double x) {
        double ax = Math.abs(x);
        if (ax < 0.5) {   // Taylor series, accurate for small x
            double x2 = x * x, t = x, sum = x;
            for (int n = 1; n < 30; n++) {
                t *= -x2 / n;
                double term = t / (2 * n + 1);
                sum += term;
                if (Math.abs(term) < 1e-17) break;
            }
            return 2 / Math.sqrt(Math.PI) * sum;
        }
        // complementary error function via continued-fraction-quality Chebyshev fit (Numerical Recipes erfc, |rel err| < 1.2e-7)
        double t = 1 / (1 + 0.5 * ax);
        double r = t * Math.exp(-ax * ax - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
        double v = 1 - r;
        return x < 0 ? -v : v;
    }

    // ------------------------------------------------------------------ fitting

    public static final class Fit {
        public Model model;
        public String[] names;
        public String[] units;
        public double[] values, errors;   // last entry: background
        public boolean[] fixed;
        public double chi2, chi2Reduced, aic, bic;
        public int nPoints, nFree;
        public double[] curve;
        public boolean ok;
        public String note = "";

        public double background() { return values[values.length - 1]; }

        public double[] params() { return java.util.Arrays.copyOf(values, values.length - 1); }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(model.label).append(String.format("   red. χ² %.3f   BIC %.1f%n", chi2Reduced, bic));
            for (int k = 0; k < names.length; k++)
                sb.append(String.format("  %-11s %s%s%s%n", names[k], PolymerAnalysis.g(values[k]),
                        fixed[k] ? " (fixed)" : " ± " + PolymerAnalysis.g(errors[k]), units[k]));
            sb.append(derived());
            if (!note.isEmpty()) sb.append("  ").append(note).append('\n');
            return sb.toString();
        }

        public String toRemarks() { return ascii(remarks()); }

        private String remarks() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("REMARK 265  GEL MODEL : %s   RED. CHI2 %.4f   BIC %.2f   POINTS %d   FREE PARAMETERS %d%n",
                    model.label.toUpperCase(), chi2Reduced, bic, nPoints, nFree));
            for (int k = 0; k < names.length; k++)
                sb.append(String.format("REMARK 265     %-12s : %s +- %s%s%s%n", names[k].toUpperCase(), PolymerAnalysis.g(values[k]),
                        PolymerAnalysis.g(errors[k]), units[k], fixed[k] ? " (FIXED)" : ""));
            for (String l : derived().split("\n")) if (!l.trim().isEmpty()) sb.append("REMARK 265     ").append(l.trim()).append('\n');
            if (!note.isEmpty()) sb.append("REMARK 265     ").append(note).append('\n');
            return sb.toString();
        }

        /** physically meaningful derived quantities */
        String derived() {
            double[] p = values;
            switch (model) {
                case OZ_DAB: {
                    // excess (static) scattering relative to the dynamic network at q -> 0
                    return String.format("  static/dynamic ratio I_DB/I_OZ = %s (frozen-in inhomogeneities a = %s A vs mesh xi = %s A)%n",
                            PolymerAnalysis.g(p[2] / p[0]), PolymerAnalysis.g(p[3]), PolymerAnalysis.g(p[1]));
                }
                case GAUSS_LORENTZ:
                    return String.format("  static/dynamic ratio G/L = %s%n", PolymerAnalysis.g(p[0] / p[2]));
                case GEL_FIT:
                    return String.format("  network fractal dimension D = %s (good-solvent chains: 1/0.588 = 1.7; theta: 2; collapsed: 3)%n",
                            PolymerAnalysis.g(p[2]));
                case CORRELATION:
                    return String.format("  Lorentz exponent m = %s → chain Flory exponent ν ≈ 1/m = %s; low-q exponent n = %s (n ≈ 4: sharp interfaces, n < 3: mass fractal clusters)%n",
                            PolymerAnalysis.g(p[4]), PolymerAnalysis.g(1 / p[4]), PolymerAnalysis.g(p[1]));
                case BROAD_PEAK:
                    return String.format("  peak at q0 = %s A^-1 → d = 2π/q0 = %s A%n", PolymerAnalysis.g(p[4]), PolymerAnalysis.g(2 * Math.PI / p[4]));
                case UNIFIED2:
                    return String.format("  level 1: Rg %s A, P %s;  level 2: Rg %s A, P %s%n", PolymerAnalysis.g(p[1]), PolymerAnalysis.g(p[3]),
                            PolymerAnalysis.g(p[5]), PolymerAnalysis.g(p[7]));
                default: return "";
            }
        }
    }

    /** fits one model; fitBackground = false fixes b = 0 */
    public static Fit fit(Model m, double[] q, double[] I, double[] s, boolean fitBackground) {
        int np = m.names.length;
        List<double[]> starts = starts(m, q, I);
        T[] tr = new T[np + 1];
        double[] lo = new double[np + 1], hi = new double[np + 1];
        transforms(m, tr, lo, hi, q);
        tr[np] = T.FREE;
        boolean[] fixed = new boolean[np + 1];
        fixed[np] = !fitBackground;
        // random starts across the physical ranges (deterministic seed), in addition to the data heuristics
        java.util.Random rnd = new java.util.Random(20261027L + m.ordinal());
        int nRandom = 10 * np;
        double i0 = 0;
        for (int j = 0; j < Math.min(3, q.length); j++) i0 += I[j];
        i0 /= Math.min(3, q.length);
        for (int r = 0; r < nRandom; r++) starts.add(randomStart(m, q, i0, tr, lo, hi, rnd));
        // stage 1: short fits from every start; stage 2: refine the five best to convergence
        List<double[]> stage = new ArrayList<>();
        for (double[] st : starts) {
            double[] p0 = java.util.Arrays.copyOf(st, np + 1);
            p0[np] = fitBackground ? Math.max(0, minTail(I)) * 0.5 : 0;
            double[] res = lm(m, q, I, s, p0, tr, lo, hi, fixed, 40);
            if (res == null) continue;
            double c = chi2(m, q, I, s, java.util.Arrays.copyOf(res, np + 1));
            if (Double.isFinite(c)) {
                double[] e = java.util.Arrays.copyOf(res, res.length + 1);
                e[res.length] = c;
                stage.add(e);
            }
        }
        stage.sort((x, y) -> Double.compare(x[x.length - 1], y[y.length - 1]));
        Fit best = null;
        for (int k = 0; k < Math.min(5, stage.size()); k++) {
            double[] p0 = java.util.Arrays.copyOf(stage.get(k), np + 1);
            double[] res = lm(m, q, I, s, p0, tr, lo, hi, fixed, 400);
            if (res == null) continue;
            Fit f = finish(m, q, I, s, res, tr, lo, hi, fixed);
            if (f != null && (best == null || f.chi2 < best.chi2)) best = f;
        }
        if (best == null) {
            best = new Fit();
            best.model = m;
            best.note = "fit failed";
        }
        return best;
    }

    /** fits every model and returns them sorted by BIC (best first) */
    public static List<Fit> compareAll(double[] q, double[] I, double[] s, boolean fitBackground) {
        List<Fit> out = new ArrayList<>();
        for (Model m : Model.values()) {
            Fit f = fit(m, q, I, s, fitBackground);
            if (f.ok) out.add(f);
        }
        out.sort((a, b) -> Double.compare(a.bic, b.bic));
        return out;
    }

    // ------------------------------------------------------------------ starts and bounds

    private static double minTail(double[] I) {
        int n = I.length;
        double m = Double.POSITIVE_INFINITY;
        for (int j = Math.max(0, n - Math.max(5, n / 10)); j < n; j++) m = Math.min(m, I[j]);
        return Double.isFinite(m) ? m : 0;
    }

    private static void transforms(Model m, T[] tr, double[] lo, double[] hi, double[] q) {
        double qmin = q[0], qmax = q[q.length - 1];
        double lenHi = 20 / qmin, lenLo = 0.05 / qmax;
        for (int k = 0; k < m.names.length; k++) { tr[k] = T.POS; lo[k] = 0; hi[k] = 0; }
        java.util.function.BiConsumer<Integer, double[]> b = (k, r) -> { tr[k] = T.BOUNDED; lo[k] = r[0]; hi[k] = r[1]; };
        switch (m) {
            case OZ: case DAB: b.accept(1, new double[]{lenLo, lenHi}); break;
            case OZ_DAB: b.accept(1, new double[]{lenLo, lenHi}); b.accept(3, new double[]{lenLo, lenHi}); break;
            case GAUSS_LORENTZ: b.accept(1, new double[]{lenLo, lenHi}); b.accept(3, new double[]{lenLo, lenHi}); break;
            case GEL_FIT: b.accept(1, new double[]{lenLo, lenHi}); b.accept(2, new double[]{0.5, 4.5}); b.accept(4, new double[]{lenLo, lenHi}); break;
            case CORRELATION: b.accept(1, new double[]{0.5, 5}); b.accept(3, new double[]{lenLo, lenHi}); b.accept(4, new double[]{0.5, 5}); break;
            case TWO_LORENTZIAN:
                b.accept(1, new double[]{lenLo, lenHi}); b.accept(2, new double[]{0.5, 6});
                b.accept(4, new double[]{lenLo, lenHi}); b.accept(5, new double[]{0.5, 6}); break;
            case BROAD_PEAK:
                b.accept(1, new double[]{0.5, 5}); b.accept(3, new double[]{lenLo, lenHi}); b.accept(4, new double[]{qmin, qmax});
                b.accept(5, new double[]{0.5, 6}); b.accept(6, new double[]{0.3, 3}); break;
            case UNIFIED2:
                b.accept(1, new double[]{lenLo, lenHi}); b.accept(3, new double[]{1, 4.5});
                b.accept(5, new double[]{lenLo, lenHi}); b.accept(7, new double[]{1, 4.5}); break;
            default: break;
        }
    }

    /** heuristic starting points from the data: plateau, half-height q, high-q slope; several length scales */
    private static List<double[]> starts(Model m, double[] q, double[] I) {
        int n = q.length;
        double i0 = 0;
        for (int j = 0; j < Math.min(3, n); j++) i0 += I[j];
        i0 /= Math.min(3, n);
        double bg = Math.max(0, minTail(I));
        double half = q[n / 2];
        for (int j = 0; j < n; j++) if (I[j] - bg < 0.5 * (i0 - bg)) { half = q[j]; break; }
        double xi0 = 1 / half;
        // high-q slope (last 30 %)
        int a = (int) (0.7 * n);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int c = 0;
        for (int j = a; j < n; j++) {
            double y = I[j] - 0.5 * bg;
            if (!(y > 0)) continue;
            double x = Math.log(q[j]), ly = Math.log(y);
            sx += x; sy += ly; sxx += x * x; sxy += x * ly;
            c++;
        }
        double slope = c > 2 ? -(c * sxy - sx * sy) / (c * sxx - sx * sx) : 2;
        slope = Math.max(1, Math.min(4, slope));
        // low-q slope (first 15 %)
        int b2 = Math.max(4, n / 7);
        sx = sy = sxx = sxy = 0;
        c = 0;
        for (int j = 0; j < b2; j++) {
            if (!(I[j] > 0)) continue;
            double x = Math.log(q[j]), ly = Math.log(I[j]);
            sx += x; sy += ly; sxx += x * x; sxy += x * ly;
            c++;
        }
        double lowSlope = c > 2 ? -(c * sxy - sx * sy) / (c * sxx - sx * sx) : 0;
        lowSlope = Math.max(1, Math.min(4.5, lowSlope));
        double qmin = q[0], qmax = q[n - 1];
        double big = Math.min(10 / qmin, 30 * xi0);

        List<double[]> s = new ArrayList<>();
        for (double f : new double[]{0.3, 1, 3}) {
            double xi = xi0 * f;
            switch (m) {
                case OZ: s.add(new double[]{i0, xi}); break;
                case DAB: s.add(new double[]{i0, xi * 0.7}); break;
                case OZ_DAB: s.add(new double[]{0.5 * i0, Math.min(xi, 0.3 / qmin), 0.5 * i0, Math.max(3 * xi, 2 / qmin)}); break;
                case GAUSS_LORENTZ: s.add(new double[]{0.5 * i0, Math.max(3 * xi, 2 / qmin), 0.5 * i0, Math.min(xi, 0.3 / qmin)}); break;
                case GEL_FIT: s.add(new double[]{0.5 * i0, Math.min(xi, 0.3 / qmin), 2, 0.5 * i0, Math.max(3 * xi, 2 / qmin)}); break;
                case CORRELATION: {
                    double A = 0.1 * i0 * Math.pow(qmin, lowSlope);
                    s.add(new double[]{A, lowSlope, i0, xi, Math.max(1.2, slope)});
                    break;
                }
                case TWO_LORENTZIAN: s.add(new double[]{0.5 * i0, Math.max(3 * xi, 2 / qmin), 2, 0.5 * i0, Math.min(xi, 0.3 / qmin), Math.max(1.2, slope)}); break;
                case BROAD_PEAK: {
                    // peak position from the maximum of q^2 (I - bg) as a fallback; start at the maximum of I if one is interior
                    int jm = 0;
                    for (int j = 1; j < n - 1; j++) if (I[j] > I[jm] || jm == 0 && j == 1) jm = j;
                    double q0 = jm > 2 && jm < n - 3 ? q[jm] : 2 * half;
                    s.add(new double[]{0.01 * i0 * Math.pow(qmin, lowSlope), lowSlope, I[jm], 3 / q0 * f, q0, 2, 1});
                    break;
                }
                case UNIFIED2: {
                    double r1 = Math.min(xi, 0.3 / qmin), r2 = Math.min(big, Math.max(5 * xi, 3 / qmin));
                    double g1 = 0.3 * i0, g2 = 0.7 * i0;
                    s.add(new double[]{g1, r1, g1 * slope / Math.pow(r1, slope) * 0.9, slope, g2, r2, g2 * lowSlope / Math.pow(r2, lowSlope), lowSlope});
                    break;
                }
                default: break;
            }
        }
        return s;
    }

    /** one random start: lengths log-uniform in their bounds, scales log-uniform around I(0), exponents uniform */
    private static double[] randomStart(Model m, double[] q, double i0, T[] tr, double[] lo, double[] hi, java.util.Random rnd) {
        int np = m.names.length;
        double[] p = new double[np];
        double qmin = q[0];
        for (int k = 0; k < np; k++) {
            if (tr[k] == T.BOUNDED) {
                boolean logScale = lo[k] > 0 && hi[k] / lo[k] > 20;
                double u = rnd.nextDouble();
                p[k] = logScale ? lo[k] * Math.pow(hi[k] / lo[k], 0.05 + 0.9 * u) : lo[k] + (hi[k] - lo[k]) * (0.05 + 0.9 * u);
            } else {
                p[k] = Math.abs(i0) * Math.pow(10, -2.5 + 2.8 * rnd.nextDouble());
            }
        }
        // power-law prefactors must be set relative to q min, or they start orders of magnitude off
        switch (m) {
            case CORRELATION: p[0] = Math.abs(i0) * Math.pow(10, -3 + 3 * rnd.nextDouble()) * Math.pow(qmin, p[1]); break;
            case BROAD_PEAK: p[0] = Math.abs(i0) * Math.pow(10, -3 + 3 * rnd.nextDouble()) * Math.pow(qmin, p[1]); break;
            case UNIFIED2:   // Beaucage: B = G P / Rg^P (Guinier-Porod continuity), scattered by a decade
                p[2] = p[0] * p[3] / Math.pow(p[1], p[3]) * Math.pow(10, -1 + 2 * rnd.nextDouble());
                p[6] = p[4] * p[7] / Math.pow(p[5], p[7]) * Math.pow(10, -1 + 2 * rnd.nextDouble());
                if (p[1] > p[5]) { double t = p[1]; p[1] = p[5]; p[5] = t; }
                break;
            default: break;
        }
        return p;
    }

    // ------------------------------------------------------------------ Levenberg-Marquardt with transforms

    private static double toInt(double v, T t, double lo, double hi) {
        switch (t) {
            case POS: return Math.log(Math.max(v, 1e-300));
            case BOUNDED: { double x = (v - lo) / (hi - lo); x = Math.min(0.999, Math.max(0.001, x)); return Math.log(x / (1 - x)); }
            default: return v;
        }
    }

    private static double toExt(double t, T tr, double lo, double hi) {
        switch (tr) {
            case POS: return Math.exp(t);
            case BOUNDED: return lo + (hi - lo) / (1 + Math.exp(-t));
            default: return t;
        }
    }

    private static double dExt(double t, T tr, double lo, double hi) {
        switch (tr) {
            case POS: return Math.exp(t);
            case BOUNDED: { double e = Math.exp(-t); return (hi - lo) * e / ((1 + e) * (1 + e)); }
            default: return 1;
        }
    }

    private static double model(Model m, double[] pAll, double q) {
        int np = pAll.length - 1;
        double[] p = java.util.Arrays.copyOf(pAll, np);
        return evaluate(m, p, q) + pAll[np];
    }

    private static double chi2(Model m, double[] q, double[] I, double[] s, double[] p) {
        double c = 0;
        for (int j = 0; j < q.length; j++) {
            double r = (I[j] - model(m, p, q[j])) / s[j];
            c += r * r;
        }
        return c;
    }

    /** returns external parameters at the minimum, or null */
    private static double[] lm(Model m, double[] q, double[] I, double[] s, double[] p0, T[] tr, double[] lo, double[] hi, boolean[] fixed, int maxIter) {
        int np = p0.length, n = q.length;
        int[] free = java.util.stream.IntStream.range(0, np).filter(k -> !fixed[k]).toArray();
        int nf = free.length;
        double[] t = new double[np];
        for (int k = 0; k < np; k++) t[k] = toInt(p0[k], tr[k], lo[k], hi[k]);
        double[] p = ext(t, tr, lo, hi);
        double c = chi2(m, q, I, s, p);
        if (!Double.isFinite(c)) return null;
        double lambda = 1e-2;
        for (int it = 0; it < maxIter; it++) {
            double[][] J = new double[n][nf];
            double[] r = new double[n];
            for (int j = 0; j < n; j++) r[j] = (I[j] - model(m, p, q[j])) / s[j];
            for (int a = 0; a < nf; a++) {
                int k = free[a];
                double h = 1e-6 * Math.max(1, Math.abs(t[k]));
                double[] tp = t.clone(), tm = t.clone();
                tp[k] += h; tm[k] -= h;
                double[] pp = ext(tp, tr, lo, hi), pm = ext(tm, tr, lo, hi);
                for (int j = 0; j < n; j++) J[j][a] = (model(m, pp, q[j]) - model(m, pm, q[j])) / (2 * h * s[j]);
            }
            double[][] A = new double[nf][nf];
            double[] g = new double[nf];
            for (int a = 0; a < nf; a++) {
                for (int j = 0; j < n; j++) g[a] += J[j][a] * r[j];
                for (int b = a; b < nf; b++) {
                    double v = 0;
                    for (int j = 0; j < n; j++) v += J[j][a] * J[j][b];
                    A[a][b] = v;
                    A[b][a] = v;
                }
            }
            boolean improved = false;
            for (int tries = 0; tries < 15; tries++) {
                double[][] M = new double[nf][nf];
                for (int a = 0; a < nf; a++) {
                    M[a] = A[a].clone();
                    M[a][a] += lambda * Math.max(A[a][a], 1e-12);
                }
                double[] step = solve(M, g);
                if (step == null) { lambda *= 10; continue; }
                double[] tn = t.clone();
                for (int a = 0; a < nf; a++) tn[free[a]] += step[a];
                double[] pn = ext(tn, tr, lo, hi);
                double cn = chi2(m, q, I, s, pn);
                if (Double.isFinite(cn) && cn < c) {
                    boolean small = (c - cn) < 1e-10 * c;
                    t = tn; p = pn; c = cn;
                    lambda = Math.max(lambda / 10, 1e-12);
                    improved = true;
                    if (small) it = Integer.MAX_VALUE - 1;
                    break;
                }
                lambda *= 10;
            }
            if (!improved) break;
        }
        // stash internal coordinates at the end for the error propagation
        double[] out = new double[2 * np];
        System.arraycopy(p, 0, out, 0, np);
        System.arraycopy(t, 0, out, np, np);
        return out;
    }

    private static double[] ext(double[] t, T[] tr, double[] lo, double[] hi) {
        double[] p = new double[t.length];
        for (int k = 0; k < t.length; k++) p[k] = toExt(t[k], tr[k], lo[k], hi[k]);
        return p;
    }

    private static Fit finish(Model m, double[] q, double[] I, double[] s, double[] res, T[] tr, double[] lo, double[] hi, boolean[] fixed) {
        int np = res.length / 2, n = q.length;
        double[] p = java.util.Arrays.copyOf(res, np), t = java.util.Arrays.copyOfRange(res, np, 2 * np);
        int[] free = java.util.stream.IntStream.range(0, np).filter(k -> !fixed[k]).toArray();
        int nf = free.length;
        double c = chi2(m, q, I, s, p);
        if (!Double.isFinite(c) || n <= nf) return null;
        // covariance in internal coordinates from the Jacobian at the minimum
        double[][] A = new double[nf][nf];
        double[][] J = new double[n][nf];
        for (int a = 0; a < nf; a++) {
            int k = free[a];
            double h = 1e-6 * Math.max(1, Math.abs(t[k]));
            double[] tp = t.clone(), tm = t.clone();
            tp[k] += h; tm[k] -= h;
            double[] pp = ext(tp, tr, lo, hi), pm = ext(tm, tr, lo, hi);
            for (int j = 0; j < n; j++) J[j][a] = (model(m, pp, q[j]) - model(m, pm, q[j])) / (2 * h * s[j]);
        }
        for (int a = 0; a < nf; a++) for (int b = 0; b < nf; b++) {
            double v = 0;
            for (int j = 0; j < n; j++) v += J[j][a] * J[j][b];
            A[a][b] = v;
        }
        double red = c / (n - nf);
        double[] err = new double[np];
        for (int a = 0; a < nf; a++) {
            double[] e = new double[nf];
            e[a] = 1;
            double[] col = solve(A, e);
            int k = free[a];
            double v = col == null ? Double.NaN : col[a] * Math.max(1, red);
            err[k] = Math.sqrt(Math.max(0, v)) * Math.abs(dExt(t[k], tr[k], lo[k], hi[k]));
        }
        Fit f = new Fit();
        f.model = m;
        f.names = java.util.Arrays.copyOf(m.names, np);
        f.names[np - 1] = "background";
        f.units = java.util.Arrays.copyOf(m.units, np);
        f.units[np - 1] = "";
        f.values = p;
        f.errors = err;
        f.fixed = fixed.clone();
        f.chi2 = c;
        f.chi2Reduced = red;
        f.nPoints = n;
        f.nFree = nf;
        f.aic = c + 2 * nf;
        f.bic = c + nf * Math.log(n);
        f.ok = true;
        if (m == Model.TWO_LORENTZIAN && p[1] < p[4]) {   // report the longer correlation length as term 1
            for (int k = 0; k < 3; k++) {
                double t1 = p[k]; p[k] = p[k + 3]; p[k + 3] = t1;
                double t2 = err[k]; err[k] = err[k + 3]; err[k + 3] = t2;
            }
        }
        f.curve = new double[n];
        for (int j = 0; j < n; j++) f.curve[j] = model(m, p, q[j]);
        StringBuilder note = new StringBuilder();
        for (int k = 0; k < np; k++)
            if (tr[k] == T.BOUNDED && !fixed[k]) {
                double x = (p[k] - lo[k]) / (hi[k] - lo[k]);
                if (x < 0.002 || x > 0.998) note.append(f.names[k]).append(" at its bound; ");
            }
        if (note.length() > 0) f.note = "warning: " + note.toString().replaceAll("; $", "") + " (term not supported by the data?)";
        return f;
    }

    private static double[] solve(double[][] A, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) { System.arraycopy(A[i], 0, M[i], 0, n); M[i][n] = b[i]; }
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[piv][c])) piv = r;
            if (Math.abs(M[piv][c]) < 1e-300) return null;
            double[] tmp = M[c]; M[c] = M[piv]; M[piv] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == c) continue;
                double fct = M[r][c] / M[c][c];
                for (int k = c; k <= n; k++) M[r][k] -= fct * M[c][k];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = M[i][n] / M[i][i];
        return x;
    }
}
