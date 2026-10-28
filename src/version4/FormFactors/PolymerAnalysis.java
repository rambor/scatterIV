package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Model-free and single-chain analysis of polymer / disordered-protein SAXS data:
 * <ul>
 *   <li>Guinier Rg, I(0) (coil limit q Rg <= 1.0)</li>
 *   <li>Debye (Gaussian coil) fit: I0, Rg, background, over q Rg <= 3</li>
 *   <li>excluded-volume chain fit (Hammouda / SasView polymer_excl_volume): I0, Rg, Flory exponent nu, background</li>
 *   <li>wormlike chain with excluded volume (SasView flexible_cylinder, thin chain): I0, contour length L, Kuhn length b,
 *       background</li>
 *   <li>high-q power law I ~ q^-m (background subtracted): apparent nu = 1/m</li>
 *   <li>dimensionless Kratky curve (q Rg)^2 I/I0 with Debye and compact-sphere references</li>
 * </ul>
 * Single-chain fits describe the ensemble average; for polydisperse samples use them as starting points (nu, b) for
 * the distribution fits.
 */
public final class PolymerAnalysis {

    private PolymerAnalysis() {}

    /** a fitted model: parameter values, 1-sigma errors, reduced chi^2, fitted curve on the analysis q */
    public static final class Fit {
        public String name;
        public String[] names;
        public double[] values, errors;
        public double chi2Reduced;
        public double[] curve;          // model on q (NaN where the fit range ends)
        public int from, to;            // fitted index range [from, to)
        public boolean ok;
        /** fit converged but does not describe the data (reduced chi^2 > 5) */
        public boolean poor;
        public String note = "";

        public String format(int k, String unit) {
            return String.format("%s = %s +- %s%s", names[k], g(values[k]), g(errors[k]), unit);
        }
    }

    public static final class Result {
        public double[] q, I, sigma;
        public Fit guinier, debye, excludedVolume, wormlike, powerLaw;
        /** dimensionless Kratky (Guinier Rg, I0): x = q Rg, y = (q Rg)^2 I / I0, dy its error */
        public double[] kratkyX, kratkyY, kratkyErr;
        public double kratkyPeakX = Double.NaN, kratkyPeakY = Double.NaN;
        public String interpretation = "";

        public String toRemarks() { return GelModels.ascii(remarks()); }

        private String remarks() {
            StringBuilder sb = new StringBuilder();
            sb.append("REMARK 265\nREMARK 265 POLYMER ANALYSIS\n");
            line(sb, guinier, new String[]{" A^-1 (I0)", " A"});
            line(sb, debye, new String[]{"", " A", ""});
            line(sb, excludedVolume, new String[]{"", " A", "", ""});
            line(sb, wormlike, new String[]{"", " A", " A", ""});
            line(sb, powerLaw, new String[]{"", ""});
            if (!Double.isNaN(kratkyPeakX))
                sb.append(String.format("REMARK 265  DIMENSIONLESS KRATKY PEAK : q Rg = %.2f, height %.3f (compact sphere 1.73, 1.10; Gaussian coil -> plateau 2)%n",
                        kratkyPeakX, kratkyPeakY));
            for (String l : interpretation.split("\n")) if (!l.isEmpty()) sb.append("REMARK 265  ").append(l).append('\n');
            return sb.toString();
        }

        /** short human-readable summary for the panel */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (guinier.ok) sb.append(String.format("Guinier        Rg %s ± %s Å, I(0) %s  (q·Rg ≤ 1, points 1–%d)%n",
                    g(guinier.values[1]), g(guinier.errors[1]), g(guinier.values[0]), guinier.to));
            else sb.append("Guinier        not determined ").append(guinier.note).append('\n');
            fitLine(sb, "Debye coil", debye, String.format("Rg %s Å", debye.ok ? g(debye.values[1]) : "–"));
            fitLine(sb, "Excl. volume", excludedVolume, excludedVolume.ok ? String.format("Rg %s Å, ν %s ± %s",
                    g(excludedVolume.values[1]), g(excludedVolume.values[2]), g(excludedVolume.errors[2])) : "");
            fitLine(sb, "Wormlike", wormlike, wormlike.ok ? String.format("L %s Å, b %s Å (lp %s Å)",
                    g(wormlike.values[1]), g(wormlike.values[2]), g(wormlike.values[2] / 2)) : "");
            if (powerLaw.ok) sb.append(String.format("Power law      I ~ q^-%s  →  ν_app %s  (q·Rg ≥ 3)%n", g(powerLaw.values[0]), g(powerLaw.values[1])));
            else sb.append("Power law      not determined ").append(powerLaw.note).append('\n');
            if (!Double.isNaN(kratkyPeakX)) sb.append(String.format("Kratky peak    q·Rg %.2f, height %.2f  (compact sphere: 1.73, 1.10)%n", kratkyPeakX, kratkyPeakY));
            sb.append('\n').append(interpretation.replace(" : ", ": "));
            sb.append("\nSingle-chain fits treat the sample as monodisperse; size polydispersity raises the apparent ν. "
                    + "For a polydisperse sample use ν as a starting value and check it with the distribution fit.");
            return sb.toString();
        }

        private static void fitLine(StringBuilder sb, String name, Fit f, String main) {
            sb.append(String.format("%-14s ", name));
            if (!f.ok) { sb.append("not determined ").append(f.note).append('\n'); return; }
            sb.append(main).append(String.format("   red. χ² %.2f", f.chi2Reduced));
            if (f.poor) sb.append("  – does not describe the data");
            sb.append('\n');
        }

        private static void line(StringBuilder sb, Fit f, String[] units) {
            if (f == null) return;
            sb.append(String.format("REMARK 265  %-26s", f.name.toUpperCase() + " :"));
            if (!f.ok) { sb.append(" not determined ").append(f.note).append('\n'); return; }
            for (int k = 0; k < f.names.length; k++)
                sb.append(' ').append(f.format(k, k < units.length ? units[k] : "")).append(k + 1 < f.names.length ? "," : "");
            sb.append(String.format("  (red. chi2 %.3f, points %d-%d)%s%n", f.chi2Reduced, f.from + 1, f.to, f.note.isEmpty() ? "" : "  " + f.note));
        }
    }

    static String g(double v) {
        if (Double.isNaN(v)) return "nan";
        double a = Math.abs(v);
        if (a != 0 && (a >= 1e5 || a < 1e-3)) return String.format("%.3e", v);
        return String.format(a >= 100 ? "%.1f" : a >= 1 ? "%.3f" : "%.4f", v);
    }

    // ------------------------------------------------------------------ main entry

    public static Result analyse(double[] q, double[] I, double[] sigma) {
        Result r = new Result();
        r.q = q; r.I = I; r.sigma = sigma;
        int n = q.length;
        if (n < 10) throw new IllegalArgumentException("need at least 10 points");

        r.guinier = guinier(q, I, sigma);
        double rg0 = r.guinier.ok ? r.guinier.values[1] : 1.0 / q[Math.min(5, n - 1)];
        double i00 = r.guinier.ok ? r.guinier.values[0] : I[0];

        // Debye fit over q Rg <= 3
        int dTo = n;
        for (int j = 0; j < n; j++) if (q[j] * rg0 > 3) { dTo = j; break; }
        if (dTo < 8) dTo = Math.min(n, 8);
        r.debye = levenberg("Debye (Gaussian coil)", new String[]{"I0", "Rg", "bg"}, q, I, sigma, 0, dTo,
                new double[]{i00, rg0, 0}, new int[]{1, 1, 0},
                (p, qq) -> p[0] * PolymerMath.debye(qq * qq * p[1] * p[1]) + p[2]);

        // excluded-volume chain over the full range
        r.excludedVolume = levenberg("Excluded-volume chain", new String[]{"I0", "Rg", "nu", "bg"}, q, I, sigma, 0, n,
                new double[]{i00, rg0, 0.55, 0}, new int[]{1, 1, 2, 0},
                (p, qq) -> p[0] * PolymerMath.evChain(PolymerMath.evFactor(p[2]) * qq * qq * p[1] * p[1], p[2]) + p[3]);

        // wormlike chain: start from a few Kuhn lengths with L from Rg^2 ~ L b / 6
        Fit best = null;
        for (double b : new double[]{20, 50, 100, 200, 400}) {
            double L = Math.max(1.5 * b, 6 * rg0 * rg0 / b);
            Fit f = levenberg("Wormlike chain (EXV)", new String[]{"I0", "L", "b", "bg"}, q, I, sigma, 0, n,
                    new double[]{i00, L, b, 0}, new int[]{1, 1, 1, 0},
                    (p, qq) -> p[0] * PolymerMath.wormlikeEXV(qq, p[1], p[2]) + p[3]);
            if (f.ok && (best == null || f.chi2Reduced < best.chi2Reduced)) best = f;
        }
        if (best == null) { best = new Fit(); best.name = "Wormlike chain (EXV)"; best.note = "(fit failed)"; }
        else if (!best.poor) {
            best.note = String.format("lp = b/2 = %s A, model Rg %s A", g(best.values[2] / 2), g(PolymerMath.wormlikeRg(best.values[1], best.values[2])));
            if (best.values[1] > 0 && 2 * Math.PI / q[0] < PolymerMath.wormlikeRg(best.values[1], best.values[2]))
                best.note += "; L poorly defined (chain larger than the q-range resolves)";
        }
        r.wormlike = best;

        // high-q power law, background from the EV fit
        double bg = r.excludedVolume.ok ? r.excludedVolume.values[3] : 0;
        r.powerLaw = powerLaw(q, I, sigma, rg0, bg);

        // dimensionless Kratky
        r.kratkyX = new double[n];
        r.kratkyY = new double[n];
        r.kratkyErr = new double[n];
        for (int j = 0; j < n; j++) {
            double x = q[j] * rg0;
            r.kratkyX[j] = x;
            r.kratkyY[j] = x * x * I[j] / i00;
            r.kratkyErr[j] = x * x * sigma[j] / i00;
        }
        // peak of a 5-point running mean, within q Rg < 6
        double py = -1, px = Double.NaN;
        for (int j = 2; j < n - 2; j++) {
            if (r.kratkyX[j] > 6) break;
            double m = 0;
            for (int k = -2; k <= 2; k++) m += r.kratkyY[j + k];
            m /= 5;
            if (m > py) { py = m; px = r.kratkyX[j]; }
        }
        boolean interior = !Double.isNaN(px) && px < 0.9 * Math.min(6, r.kratkyX[n - 3]);
        if (interior) { r.kratkyPeakX = px; r.kratkyPeakY = py; }
        r.interpretation = interpret(r, interior);
        return r;
    }

    private static String interpret(Result r, boolean peak) {
        StringBuilder sb = new StringBuilder();
        if (r.excludedVolume.ok && !r.excludedVolume.poor) {
            double nu = r.excludedVolume.values[2];
            String state = nu < 0.40 ? "compact / collapsed (nu ~ 1/3)" : nu < 0.47 ? "partly compact (poor solvent)"
                    : nu < 0.55 ? "theta-like / Gaussian coil (nu ~ 1/2)" : nu < 0.65 ? "expanded coil, good solvent (nu ~ 0.59)" : "stiff / rod-like (nu > 0.65)";
            sb.append(String.format("FLORY EXPONENT nu = %.3f : %s%n", nu, state));
        }
        if (peak) {
            double x = r.kratkyPeakX, y = r.kratkyPeakY;
            if (Math.abs(x - Math.sqrt(3)) < 0.35 && y > 0.95 && y < 1.3) sb.append("KRATKY: bell-shaped near sqrt(3), 1.10 : compact, folded particle\n");
            else if (x > 2.1) sb.append("KRATKY: peak shifted to higher q Rg : extended / flexible or partly unfolded\n");
            else sb.append("KRATKY: peak present but not at the compact-sphere position\n");
        } else {
            sb.append("KRATKY: no maximum (plateau or rise) : flexible chain / disordered\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Guinier

    static Fit guinier(double[] q, double[] I, double[] s) {
        Fit f = new Fit();
        f.name = "Guinier (q Rg <= 1.0)";
        f.names = new String[]{"I0", "Rg"};
        int n = q.length;
        int to = Math.min(n, 10);
        double rg = Double.NaN, i0 = Double.NaN, erg = Double.NaN, ei0 = Double.NaN, chi = Double.NaN;
        for (int it = 0; it < 30; it++) {
            // weighted linear fit ln I = a - (Rg^2/3) q^2
            double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
            int m = 0;
            for (int j = 0; j < to; j++) {
                if (!(I[j] > 0)) continue;
                double w = Math.pow(I[j] / s[j], 2), x = q[j] * q[j], y = Math.log(I[j]);
                sw += w; sx += w * x; sy += w * y; sxx += w * x * x; sxy += w * x * y;
                m++;
            }
            if (m < 4) break;
            double det = sw * sxx - sx * sx;
            double slope = (sw * sxy - sx * sy) / det, a = (sxx * sy - sx * sxy) / det;
            if (!(slope < 0)) { f.note = "(no Guinier region: positive slope)"; break; }
            rg = Math.sqrt(-3 * slope);
            i0 = Math.exp(a);
            double vs = sw / det, va = sxx / det;
            erg = 1.5 / rg * Math.sqrt(vs);
            ei0 = i0 * Math.sqrt(va);
            double c2 = 0;
            for (int j = 0; j < to; j++) if (I[j] > 0) c2 += Math.pow((Math.log(I[j]) - a - slope * q[j] * q[j]) * I[j] / s[j], 2);
            chi = c2 / Math.max(1, m - 2);
            int nto = 0;
            while (nto < n && q[nto] * rg <= 1.0) nto++;
            nto = Math.max(nto, 5);
            if (nto == to) break;
            to = nto;
        }
        if (!Double.isNaN(rg)) {
            f.values = new double[]{i0, rg};
            f.errors = new double[]{ei0, erg};
            f.chi2Reduced = chi;
            f.from = 0;
            f.to = to;
            f.ok = true;
            f.curve = new double[n];
            for (int j = 0; j < n; j++) f.curve[j] = j < to ? i0 * Math.exp(-q[j] * q[j] * rg * rg / 3) : Double.NaN;
            f.note = "(for chains Guinier reads a few % low; prefer the Debye / excluded-volume Rg)";
            if (q[0] * rg > 1.0) { f.note = "(q min too large: q min Rg = " + g(q[0] * rg) + ")"; }
        }
        return f;
    }

    // ------------------------------------------------------------------ power law

    static Fit powerLaw(double[] q, double[] I, double[] s, double rg, double bg) {
        Fit f = new Fit();
        f.name = "High-q power law";
        f.names = new String[]{"m", "nu_app"};
        int n = q.length, from = n;
        for (int j = 0; j < n; j++) if (q[j] * rg >= 3) { from = j; break; }
        double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
        int m = 0;
        for (int j = from; j < n; j++) {
            double y = I[j] - bg;
            if (!(y > 2 * s[j])) continue;    // only points clearly above background
            double w = Math.pow(y / s[j], 2), x = Math.log(q[j]), ly = Math.log(y);
            sw += w; sx += w * x; sy += w * ly; sxx += w * x * x; sxy += w * x * ly;
            m++;
        }
        if (m < 5) { f.note = "(fewer than 5 points with q Rg >= 3 above background)"; return f; }
        double det = sw * sxx - sx * sx;
        double slope = (sw * sxy - sx * sy) / det, a = (sxx * sy - sx * sxy) / det;
        double es = Math.sqrt(sw / det);
        double mm = -slope;
        f.values = new double[]{mm, 1 / mm};
        f.errors = new double[]{es, es / (mm * mm)};
        double c2 = 0;
        for (int j = from; j < n; j++) {
            double y = I[j] - bg;
            if (y > 2 * s[j]) c2 += Math.pow((Math.log(y) - a - slope * Math.log(q[j])) * y / s[j], 2);
        }
        f.chi2Reduced = c2 / Math.max(1, m - 2);
        f.from = from;
        f.to = n;
        f.ok = true;
        f.curve = new double[n];
        for (int j = 0; j < n; j++) f.curve[j] = j >= from ? Math.exp(a + slope * Math.log(q[j])) + bg : Double.NaN;
        f.note = "(q Rg >= 3, background subtracted)";
        return f;
    }

    // ------------------------------------------------------------------ Levenberg-Marquardt

    /**
     * @param mode per parameter: 0 = free (linear), 1 = positive (fitted in log), 2 = Flory exponent in (0.28, 0.95)
     */
    static Fit levenberg(String name, String[] names, double[] q, double[] I, double[] s, int from, int to,
                         double[] start, int[] mode, BiFunction<double[], Double, Double> model) {
        Fit f = new Fit();
        f.name = name;
        f.names = names;
        f.from = from;
        f.to = to;
        int np = start.length, m = to - from;
        if (m <= np) { f.note = "(too few points)"; return f; }
        double[] t = new double[np];
        for (int k = 0; k < np; k++) t[k] = toInternal(start[k], mode[k]);
        double lambda = 1e-3;
        double chi = chi2(t, mode, q, I, s, from, to, model);
        if (!Double.isFinite(chi)) { f.note = "(model not finite at start)"; return f; }
        double[][] JtJ = new double[np][np];
        double[] Jtr = new double[np];
        for (int it = 0; it < 200; it++) {
            // Jacobian by central differences in internal coordinates
            double[][] J = new double[m][np];
            double[] res = new double[m];
            double[] p = toExternal(t, mode);
            for (int j = 0; j < m; j++) res[j] = (I[from + j] - model.apply(p, q[from + j])) / s[from + j];
            for (int k = 0; k < np; k++) {
                double h = 1e-6 * Math.max(1, Math.abs(t[k]));
                double[] tp = t.clone(), tm = t.clone();
                tp[k] += h; tm[k] -= h;
                double[] pp = toExternal(tp, mode), pm = toExternal(tm, mode);
                for (int j = 0; j < m; j++)
                    J[j][k] = (model.apply(pp, q[from + j]) - model.apply(pm, q[from + j])) / (2 * h) / s[from + j];
            }
            for (int a = 0; a < np; a++) {
                Jtr[a] = 0;
                for (int j = 0; j < m; j++) Jtr[a] += J[j][a] * res[j];
                for (int b = 0; b < np; b++) {
                    double v = 0;
                    for (int j = 0; j < m; j++) v += J[j][a] * J[j][b];
                    JtJ[a][b] = v;
                }
            }
            boolean improved = false;
            for (int tries = 0; tries < 12; tries++) {
                double[][] A = new double[np][np];
                for (int a = 0; a < np; a++) {
                    for (int b = 0; b < np; b++) A[a][b] = JtJ[a][b];
                    A[a][a] += lambda * Math.max(JtJ[a][a], 1e-12);
                }
                double[] step = solve(A, Jtr);
                if (step == null) { lambda *= 10; continue; }
                double[] tn = t.clone();
                for (int k = 0; k < np; k++) tn[k] += step[k];
                double cn = chi2(tn, mode, q, I, s, from, to, model);
                if (Double.isFinite(cn) && cn < chi) {
                    boolean small = (chi - cn) < 1e-9 * chi;
                    t = tn;
                    chi = cn;
                    lambda = Math.max(lambda / 10, 1e-12);
                    improved = true;
                    if (small) it = 1000;
                    break;
                }
                lambda *= 10;
            }
            if (!improved) break;
        }
        // errors from the covariance in internal coordinates, propagated
        double[][] cov = invert(JtJ);
        double red = chi / (m - np);
        double[] p = toExternal(t, mode);
        double[] e = new double[np];
        for (int k = 0; k < np; k++) {
            double v = cov == null ? Double.NaN : cov[k][k] * Math.max(1, red);
            double dpdt = mode[k] == 0 ? 1 : mode[k] == 1 ? p[k] : dNu(t[k]);
            e[k] = Math.sqrt(Math.max(0, v)) * Math.abs(dpdt);
        }
        f.values = p;
        f.errors = e;
        f.chi2Reduced = red;
        f.ok = true;
        f.poor = red > 5;
        if (f.poor) f.note = "(poor fit: this model does not describe the data)";
        for (int k = 0; k < np; k++)
            if (mode[k] == 2 && (p[k] < NU_LO + 0.005 || p[k] > NU_HI - 0.005)) { f.poor = true; f.note = "(nu at its bound: model does not apply)"; }
        f.curve = new double[q.length];
        for (int j = 0; j < q.length; j++) f.curve[j] = j >= from && j < to ? model.apply(p, q[j]) : Double.NaN;
        return f;
    }

    private static final double NU_LO = 0.28, NU_HI = 0.95;

    private static double toInternal(double v, int mode) {
        if (mode == 1) return Math.log(Math.max(v, 1e-300));
        if (mode == 2) { double x = (v - NU_LO) / (NU_HI - NU_LO); x = Math.min(0.999, Math.max(0.001, x)); return Math.log(x / (1 - x)); }
        return v;
    }

    private static double[] toExternal(double[] t, int[] mode) {
        double[] p = new double[t.length];
        for (int k = 0; k < t.length; k++)
            p[k] = mode[k] == 1 ? Math.exp(t[k]) : mode[k] == 2 ? NU_LO + (NU_HI - NU_LO) / (1 + Math.exp(-t[k])) : t[k];
        return p;
    }

    private static double dNu(double t) {
        double e = Math.exp(-t);
        return (NU_HI - NU_LO) * e / ((1 + e) * (1 + e));
    }

    private static double chi2(double[] t, int[] mode, double[] q, double[] I, double[] s, int from, int to,
                               BiFunction<double[], Double, Double> model) {
        double[] p = toExternal(t, mode);
        double c = 0;
        for (int j = from; j < to; j++) {
            double r = (I[j] - model.apply(p, q[j])) / s[j];
            c += r * r;
        }
        return c;
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

    private static double[][] invert(double[][] A) {
        int n = A.length;
        double[][] inv = new double[n][];
        for (int c = 0; c < n; c++) {
            double[] e = new double[n];
            e[c] = 1;
            double[] x = solve(A, e);
            if (x == null) return null;
            inv[c] = x;
        }
        // inv[c] is column c; symmetric so fine
        return inv;
    }

    /** reference curves for the dimensionless Kratky plot, x = q Rg */
    public static double kratkyDebye(double x) { return x * x * PolymerMath.debye(x * x); }

    public static double kratkySphere(double x) {
        double R = x * Math.sqrt(5.0 / 3.0);     // Rg of a sphere = sqrt(3/5) R
        double a = PolymerMath.sphereAmplitude(R);
        return x * x * a * a;
    }

    /** helper for callers that have arrays with sigma <= 0 or NaNs: keep valid points inside [qmin, qmax] */
    public static double[][] clean(double[] q, double[] I, double[] s, double qmin, double qmax) {
        List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < q.length; j++)
            if (q[j] >= qmin && q[j] <= qmax && s[j] > 0 && Double.isFinite(I[j])) pts.add(new double[]{q[j], I[j], s[j]});
        pts.sort((a, b) -> Double.compare(a[0], b[0]));
        double[][] out = new double[3][pts.size()];
        for (int i = 0; i < pts.size(); i++) for (int k = 0; k < 3; k++) out[k][i] = pts.get(i)[k];
        return out;
    }
}
