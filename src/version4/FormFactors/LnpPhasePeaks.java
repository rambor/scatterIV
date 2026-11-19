package version4.FormFactors;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Deconvolution of the primary internal-structure peak into phase contributions, after Hammel et al., "Correlating
 * the Structure and Gene Silencing Activity of Oligonucleotide-Loaded Lipid Nanoparticles Using Small-Angle X-ray
 * Scattering", ACS Nano 17, 11454 (2023):
 * <pre>
 *   I(q) = sum_k (2 A_k / pi) w_k / (4 (q - qc_k)^2 + w_k^2)
 * </pre>
 * with three Lorentz functions whose centres are bounded to the disordered (0.09-0.11 1/A, d ~ 60 A), HII
 * (0.120-0.128, d ~ 50 A) and L-alpha (0.137-0.139, d ~ 45 A) phases. Widths and areas are free (areas &gt;= 0).
 * In their library of 54 ASO-LNPs the disordered/HII area ratio predicted knockdown efficacy (R^2 = 0.91; lower =
 * more ordered core = more active), and the L-alpha width tracked particle size.
 *
 * Hammel et al. fitted the peak with a zero offset. By default here the smooth background under the peak (particle
 * form factor and buffer background) is fitted together with the peaks as B(q) = c0 (q/q0)^-p + c1; subtracting a
 * baseline first would eat into the broad disordered component. The zero-offset fit is available for strict
 * comparison with their values, and is only reliable when the background in the window is flat and small.
 */
public final class LnpPhasePeaks {

    /** one phase component with its centre bounds (1/A) */
    public static final class Component {
        public String name;
        public double qLo, qHi;
        public java.awt.Color color;
        // results
        public double qc = Double.NaN, width = Double.NaN, area = Double.NaN, qcErr = Double.NaN, widthErr = Double.NaN, areaErr = Double.NaN;
        public Component(String name, double qLo, double qHi, java.awt.Color color) {
            this.name = name; this.qLo = qLo; this.qHi = qHi; this.color = color;
        }
        public double d() { return 2 * Math.PI / qc; }
        Component copy() { Component c = new Component(name, qLo, qHi, color); return c; }
    }

    /** the three phases with the bounds of Hammel et al. (2023) */
    public static List<Component> hammelDefaults() {
        List<Component> l = new ArrayList<>();
        l.add(new Component("Disordered", 0.090, 0.110, new java.awt.Color(0x8e44ad)));
        l.add(new Component("HII", 0.120, 0.128, new java.awt.Color(0xdc2626)));
        l.add(new Component("Lα", 0.137, 0.139, new java.awt.Color(0x2563eb)));
        return l;
    }

    public static final class Result {
        public final List<Component> components = new ArrayList<>();
        public double[] q, signal, sigma, fit, baseline;
        public double chi2, chi2Reduced, qmin, qmax;
        public boolean baselineSubtracted;
        public final List<String> notes = new ArrayList<>();

        public Component get(String name) { for (Component c : components) if (c.name.equals(name)) return c; return null; }

        /** disordered / HII area ratio (Hammel et al.'s efficacy indicator), NaN if not available */
        public double disorderedToHII() {
            Component d = get("Disordered"), h = get("HII");
            return d == null || h == null || !(h.area > 0) ? Double.NaN : d.area / h.area;
        }

        public double disorderedToHIIErr() {
            Component d = get("Disordered"), h = get("HII");
            if (d == null || h == null || !(h.area > 0) || !(d.area > 0)) return Double.NaN;
            double r = d.area / h.area;
            return r * Math.sqrt(sq(d.areaErr / d.area) + sq(h.areaErr / h.area));
        }

        private static double sq(double x) { return x * x; }

        /** share of each phase in the total peak area */
        public double share(Component c) {
            double t = 0;
            for (Component k : components) if (k.area > 0) t += k.area;
            return t > 0 ? c.area / t : Double.NaN;
        }
    }

    public static double lorentz(double q, double qc, double w, double area) {
        double x = q - qc;
        return 2 * area / Math.PI * w / (4 * x * x + w * w);
    }

    /**
     * @param q, I, s          data (ascending)
     * @param comps            components with centre bounds
     * @param qmin, qmax       fitting window (default 0.07-0.17 1/A, around the primary peak)
     * @param subtractBaseline true: subtract the assessment's smooth baseline first; false: zero offset (as Hammel)
     */
    public static Result fit(double[] q, double[] I, double[] s, List<Component> comps, double qmin, double qmax, boolean subtractBaseline) {
        Result r = new Result();
        r.qmin = qmin;
        r.qmax = qmax;
        r.baselineSubtracted = subtractBaseline;
        List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < q.length; j++) if (q[j] >= qmin && q[j] <= qmax && s[j] > 0) pts.add(new double[]{q[j], I[j], s[j]});
        if (pts.size() < 3 * comps.size() + 5) throw new IllegalArgumentException("Too few points in the peak window");
        int n = pts.size();
        r.q = new double[n]; r.signal = new double[n]; r.sigma = new double[n]; r.baseline = new double[n];
        for (int i = 0; i < n; i++) { r.q[i] = pts.get(i)[0]; r.signal[i] = pts.get(i)[1]; r.sigma[i] = pts.get(i)[2]; }
        final double[] qq = r.q, yy = r.signal, ss = r.sigma;
        final int m = comps.size();
        final boolean withBase = subtractBaseline;
        final double q0 = Math.sqrt(qmin * qmax);
        // background start: power law through the window's end points (the peaks sit in between)
        double yLo = yy[0], yHi = yy[n - 1];
        double p0 = Math.max(0, Math.min(4, Math.log(Math.max(yLo, 1e-30) / Math.max(yHi, 1e-30)) / Math.log(qq[n - 1] / qq[0])));
        double c00 = Math.max(1e-30, Math.min(yLo, yHi) * Math.pow(Math.sqrt(qq[0] * qq[n - 1]) / q0, p0) * 0.8);
        int nb = withBase ? 3 : 0;
        // parameters per component: centre (bounded, via logistic), width (bounded 0.002-0.06, logistic), ln area
        double[] u0 = new double[3 * m + nb];
        double peakMax = 0;
        for (int i = 0; i < n; i++) peakMax = Math.max(peakMax, yy[i] - (withBase ? c00 * Math.pow(qq[i] / q0, -p0) : 0));
        for (int k = 0; k < m; k++) {
            Component c = comps.get(k);
            u0[3 * k] = 0;                                          // centre in the middle of its bounds
            u0[3 * k + 1] = 0;                                      // width in the middle of its bounds
            double hNear = 0;
            for (int i = 0; i < n; i++)
                if (Math.abs(qq[i] - 0.5 * (c.qLo + c.qHi)) < 0.004) hNear = Math.max(hNear, yy[i] - (withBase ? c00 * Math.pow(qq[i] / q0, -p0) : 0));
            u0[3 * k + 2] = Math.log(Math.max(1e-6 * peakMax + 1e-30, 0.5 * hNear * Math.PI * 0.015 / 2));
        }
        if (withBase) { u0[3 * m] = Math.log(c00); u0[3 * m + 1] = p0; u0[3 * m + 2] = 0; }
        final double W_LO = 0.002, W_HI = 0.06;
        java.util.function.Function<double[], double[]> model = u -> {
            double[] f = new double[n];
            for (int k = 0; k < m; k++) {
                Component c = comps.get(k);
                double qc = c.qLo + (c.qHi - c.qLo) / (1 + Math.exp(-u[3 * k]));
                double w = W_LO + (W_HI - W_LO) / (1 + Math.exp(-u[3 * k + 1])), A = Math.exp(u[3 * k + 2]);
                for (int i = 0; i < n; i++) f[i] += lorentz(qq[i], qc, w, A);
            }
            if (withBase) for (int i = 0; i < n; i++) f[i] += Math.exp(u[3 * m]) * Math.pow(qq[i] / q0, -u[3 * m + 1]) + u[3 * m + 2];
            return f;
        };
        double[] target = new double[n];
        for (int i = 0; i < n; i++) target[i] = yy[i] / ss[i];
        LeastSquaresOptimizer.Optimum opt = new LevenbergMarquardtOptimizer().optimize(new LeastSquaresBuilder()
                .model(point -> {
                    double[] u = point.toArray();
                    double[] f0 = model.apply(u);
                    double[][] jac = new double[n][u.length];
                    for (int p = 0; p < u.length; p++) {
                        double h = 1e-6 * Math.max(1, Math.abs(u[p]));
                        double[] up = u.clone();
                        up[p] += h;
                        double[] f1 = model.apply(up);
                        for (int i = 0; i < n; i++) jac[i][p] = (f1[i] - f0[i]) / h / ss[i];
                    }
                    double[] w0 = new double[n];
                    for (int i = 0; i < n; i++) w0[i] = f0[i] / ss[i];
                    return new Pair<>(new ArrayRealVector(w0, false), new Array2DRowRealMatrix(jac, false));
                })
                .target(target).start(u0).maxEvaluations(2000).maxIterations(1000).build());
        double[] u = opt.getPoint().toArray();
        r.fit = model.apply(u);
        if (withBase) for (int i = 0; i < n; i++) r.baseline[i] = Math.exp(u[3 * m]) * Math.pow(qq[i] / q0, -u[3 * m + 1]) + u[3 * m + 2];
        double chi2 = 0;
        for (int i = 0; i < n; i++) { double d = (yy[i] - r.fit[i]) / ss[i]; chi2 += d * d; }
        r.chi2 = chi2;
        r.chi2Reduced = chi2 / Math.max(1, n - 3 * m);
        // covariance from the Jacobian at the solution (pseudo-inverse: parameters pinned at a bound do not
        // make the other errors unavailable)
        RealMatrix cov = null;
        try {
            RealMatrix J = opt.getJacobian();
            org.apache.commons.math3.linear.SingularValueDecomposition svd =
                    new org.apache.commons.math3.linear.SingularValueDecomposition(J.transpose().multiply(J));
            double[] sv = svd.getSingularValues();
            double tol = sv[0] * 1e-12;
            RealMatrix U = svd.getU(), V = svd.getV();
            double[][] pinv = new double[sv.length][sv.length];
            for (int a = 0; a < sv.length; a++)
                for (int b = 0; b < sv.length; b++) {
                    double v = 0;
                    for (int k2 = 0; k2 < sv.length; k2++) if (sv[k2] > tol) v += V.getEntry(a, k2) * U.getEntry(b, k2) / sv[k2];
                    pinv[a][b] = v;
                }
            cov = new Array2DRowRealMatrix(pinv, false);
        } catch (RuntimeException ignored) { }
        double red = Math.max(1, r.chi2Reduced);
        for (int k = 0; k < m; k++) {
            Component c = comps.get(k).copy();
            double sg = 1 / (1 + Math.exp(-u[3 * k]));
            c.qc = c.qLo + (c.qHi - c.qLo) * sg;
            double sw = 1 / (1 + Math.exp(-u[3 * k + 1]));
            c.width = W_LO + (W_HI - W_LO) * sw;
            c.area = Math.exp(u[3 * k + 2]);
            if (cov != null) {
                c.qcErr = (c.qHi - c.qLo) * sg * (1 - sg) * Math.sqrt(Math.max(0, cov.getEntry(3 * k, 3 * k)) * red);
                c.widthErr = (W_HI - W_LO) * sw * (1 - sw) * Math.sqrt(Math.max(0, cov.getEntry(3 * k + 1, 3 * k + 1)) * red);
                c.areaErr = c.area * Math.sqrt(Math.max(0, cov.getEntry(3 * k + 2, 3 * k + 2)) * red);
            }
            if (sg < 0.02 || sg > 0.98) r.notes.add(String.format("%s centre is at its %s bound (%.4f Å⁻¹): the peak may sit outside the expected range.",
                    c.name, sg < 0.5 ? "lower" : "upper", c.qc));
            if (c.area < 1e-6 * peakMax * 0.01) r.notes.add(c.name + ": no measurable contribution.");
            if (sw > 0.98) r.notes.add(c.name + " is as broad as allowed: it may be describing background rather than a peak.");
            r.components.add(c);
        }
        return r;
    }

    private static double interpLog(double[] x, double[] y, double v) {
        if (v <= x[0]) return y[0];
        if (v >= x[x.length - 1]) return y[y.length - 1];
        int i = 1;
        while (x[i] < v) i++;
        double t = (Math.log(v) - Math.log(x[i - 1])) / (Math.log(x[i]) - Math.log(x[i - 1]));
        return Math.exp(Math.log(y[i - 1]) + t * (Math.log(y[i]) - Math.log(y[i - 1])));
    }

    public static String describe(Result r) {
        StringBuilder sb = new StringBuilder("PHASE DECOMPOSITION OF THE PRIMARY PEAK (Hammel et al., ACS Nano 2023)\n");
        sb.append(String.format("Window %.3f–%.3f Å⁻¹, %s, reduced χ² %.2f%n%n", r.qmin, r.qmax,
                r.baselineSubtracted ? "background fitted with the peaks" : "zero offset (as in the paper)", r.chi2Reduced));
        if (!r.baselineSubtracted && r.chi2Reduced > 3)
            sb.append("  ! The zero-offset fit is poor: the form factor slopes under the peak. Use the fitted background.\n\n");
        sb.append(String.format("  %-11s %18s %10s %16s %16s %8s%n", "phase", "centre (Å⁻¹)", "d (Å)", "width (Å⁻¹)", "area", "share"));
        for (Component c : r.components)
            sb.append(String.format("  %-11s %9.4f ± %-6.4f %10.1f %8.4f ± %-6.4f %8.3g ± %-6.2g %6.0f %%%n", c.name, c.qc, c.qcErr, c.d(),
                    c.width, c.widthErr, c.area, c.areaErr, 100 * r.share(c)));
        double ratio = r.disorderedToHII();
        if (!Double.isNaN(ratio)) {
            sb.append(String.format("%nDisordered / HII area ratio = %.2f ± %.2f%n", ratio, r.disorderedToHIIErr()));
            sb.append("  Lower = more of the core ordered as HII. In Hammel et al.'s 54 ASO-LNPs this ratio predicted\n")
              .append("  knockdown efficacy (R² = 0.91; the lowest ratios were ~4× more active than the highest).\n")
              .append("  Use it to rank formulations measured the same way, not as an absolute number.\n");
        }
        Component la = r.get("Lα");
        if (la != null && la.width > 0)
            sb.append(String.format("  Lα width %.4f Å⁻¹ (≈ %.0f Å ordered stack): narrow Lα went with larger, multilamellar LNPs.%n", la.width, 2 * Math.PI / la.width));
        for (String n : r.notes) sb.append("  ! ").append(n).append('\n');
        return sb.toString();
    }
}
