package version4.FormFactors;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Weighted least-squares fit of {@link LnpModel} (Levenberg-Marquardt, numerical Jacobian). Positive parameters are
 * fitted as logarithms; every parameter is kept inside physical limits. An optional coarse scan over the core radius
 * (with the linear parameters solved exactly at each point) gives a starting point, so a new user does not have to
 * guess the size.
 */
public final class LnpFitter {

    public static final class Result {
        public final LnpModel model;
        public final double[] params, errors;
        public final boolean[] fixed;
        public final double chi2, chi2Reduced;
        public final int points, free, iterations;
        public final double[] q, I, sigma, fit;
        public final List<String> warnings;

        Result(LnpModel model, double[] params, double[] errors, boolean[] fixed, double chi2, int points, int free,
               int iterations, double[] q, double[] I, double[] sigma, double[] fit, List<String> warnings) {
            this.model = model; this.params = params; this.errors = errors; this.fixed = fixed;
            this.chi2 = chi2; this.points = points; this.free = free; this.iterations = iterations;
            this.chi2Reduced = chi2 / Math.max(1, points - free);
            this.q = q; this.I = I; this.sigma = sigma; this.fit = fit; this.warnings = warnings;
        }
    }

    public interface Cancel { boolean cancelled(); }

    private final LnpModel model;
    private final double[] q, I, s;
    private final double qmin, qmax;
    /** lipid-physics sign constraints: tails below buffer, headgroups and PEG above, inner shell 5-100 A */
    private boolean physical = true;

    public LnpFitter physicalConstraints(boolean on) { physical = on; return this; }

    /** data must already be cut to the fitting range and contain only points with sigma &gt; 0 */
    public LnpFitter(LnpModel model, double[] q, double[] I, double[] sigma) {
        if (q.length < 10) throw new IllegalArgumentException("Need at least 10 data points in the q range");
        this.model = model;
        this.q = q; this.I = I; this.s = sigma;
        double lo = Double.POSITIVE_INFINITY, hi = 0;
        for (double v : q) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        qmin = lo; qmax = hi;
    }

    // ------------------------------------------------------------------ limits

    double lower(int k, double[] p) {
        switch (k) {
            case LnpModel.SCALE: case LnpModel.PEAK_A: return 1e-30;
            case LnpModel.R0: return 10;
            case LnpModel.SIGMA: return 1e-3 * p[LnpModel.R0];
            case LnpModel.T1: return physical && !model.isVesicle() ? 5 : 0.5;
            case LnpModel.T2: return physical && model.isVesicle() ? 15 : 0.5;
            case LnpModel.T3: case LnpModel.T4: return 0.5;
            case LnpModel.RHO_C: case LnpModel.RHO_1: case LnpModel.RHO_2: case LnpModel.RHO_3: case LnpModel.RHO_4:
                return physical && model.densitySign(k) > 0 ? 0 : Double.NEGATIVE_INFINITY;
            case LnpModel.PEAK_Q: return qmin;
            case LnpModel.PEAK_W: return 5e-4;
            default: return Double.NEGATIVE_INFINITY;
        }
    }

    double upper(int k, double[] p) {
        switch (k) {
            case LnpModel.R0: return 5000;
            case LnpModel.SIGMA: return 0.6 * p[LnpModel.R0];
            case LnpModel.T1: return physical && !model.isVesicle() ? 100 : 300;
            case LnpModel.T2: return physical && model.isVesicle() ? 60 : 300;
            case LnpModel.T3: case LnpModel.T4: return 300;
            case LnpModel.RHO_C: case LnpModel.RHO_1: case LnpModel.RHO_2: case LnpModel.RHO_3: case LnpModel.RHO_4:
                return physical && model.densitySign(k) < 0 ? 0 : Double.POSITIVE_INFINITY;
            case LnpModel.PEAK_Q: return qmax;
            case LnpModel.PEAK_W: return 0.25 * (qmax - qmin) + 5e-4;
            default: return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Brings the free parameters inside their limits before fitting. Parameters the user has fixed are left
     * exactly as they are: the limits exist to keep the fit in a region it can search — thicknesses are refined
     * in log space, so the lower bound stops one reaching zero — and none of that applies to a value that is not
     * being refined. Clamping a fixed value silently changes the model the user asked for, which is how a
     * corona set to zero came back as 0.5 Å.
     */
    /** everything the optimiser is not refining counts as held, so the limits never move it */
    private static boolean[] heldOutside(int[] free) {
        boolean[] held = new boolean[LnpModel.NPAR];
        java.util.Arrays.fill(held, true);
        for (int k : free) held[k] = false;
        return held;
    }

    private void clamp(double[] p, boolean[] fixed) {
        for (int pass = 0; pass < 2; pass++)   // sigma's limits depend on R0
            for (int k = 0; k < LnpModel.NPAR; k++) {
                if (fixed != null && fixed[k]) continue;
                p[k] = Math.max(lower(k, p), Math.min(upper(k, p), p[k]));
            }
    }

    // ------------------------------------------------------------------ start

    /**
     * Coarse scan of R0 (sigma kept at the current sigma/R0 ratio); for each R0 the linear parameters scale, peak
     * height and background are solved by weighted linear least squares. Returns the best start.
     */
    public double[] scanStart(double[] p0, boolean[] fixed) {
        double[] best = p0.clone();
        double bestChi = Double.POSITIVE_INFINITY;
        double ratio = Math.max(0.05, Math.min(0.3, p0[LnpModel.SIGMA] / p0[LnpModel.R0]));
        double shellSum = 0;
        for (int k = 1; k <= model.shells; k++) shellSum += p0[LnpModel.tIndex(k)];
        // core radii the data can resolve: first minimum (q R ~ 4.5) between qmin and the middle of the range
        double rLo = Math.max(10, 3.0 / qmax), rHi = Math.min(4000, 12.0 / qmin);
        if (fixed[LnpModel.R0]) { rLo = rHi = p0[LnpModel.R0]; }
        int n = rLo == rHi ? 1 : 60;
        for (int i = 0; i < n; i++) {
            double r0 = n == 1 ? rLo : rLo * Math.pow(rHi / rLo, i / (double) (n - 1));
            if (r0 + shellSum <= 0) continue;
            double[] p = p0.clone();
            p[LnpModel.R0] = r0;
            if (!fixed[LnpModel.SIGMA]) p[LnpModel.SIGMA] = ratio * r0;
            double chi = solveLinear(p, fixed);
            if (chi < bestChi) { bestChi = chi; best = p; }
        }
        return best;
    }

    /** solves scale, peak height and background (those that are free) for fixed shape; returns chi^2, updates p */
    double solveLinear(double[] p, boolean[] fixed) {
        double[] part = model.particleShape(p, q);
        double[] pk = model.peak ? LnpModel.peakShape(p, q) : null;
        List<double[]> cols = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        double[] rest = new double[q.length];
        if (!fixed[LnpModel.SCALE]) { cols.add(part); idx.add(LnpModel.SCALE); }
        else for (int j = 0; j < q.length; j++) rest[j] += p[LnpModel.SCALE] * part[j];
        if (pk != null) {
            if (!fixed[LnpModel.PEAK_A]) { cols.add(pk); idx.add(LnpModel.PEAK_A); }
            else for (int j = 0; j < q.length; j++) rest[j] += p[LnpModel.PEAK_A] * pk[j];
        }
        double[] ones = new double[q.length];
        java.util.Arrays.fill(ones, 1);
        if (!fixed[LnpModel.BG]) { cols.add(ones); idx.add(LnpModel.BG); }
        else for (int j = 0; j < q.length; j++) rest[j] += p[LnpModel.BG];
        int m = cols.size();
        if (m > 0) {
            double[][] a = new double[m][m];
            double[] b = new double[m];
            for (int j = 0; j < q.length; j++) {
                double w = 1 / (s[j] * s[j]), y = I[j] - rest[j];
                for (int r = 0; r < m; r++) {
                    b[r] += w * cols.get(r)[j] * y;
                    for (int c = 0; c < m; c++) a[r][c] += w * cols.get(r)[j] * cols.get(c)[j];
                }
            }
            double[] x;
            try {
                x = new org.apache.commons.math3.linear.LUDecomposition(new Array2DRowRealMatrix(a)).getSolver()
                        .solve(new ArrayRealVector(b)).toArray();
            } catch (RuntimeException e) {
                return Double.POSITIVE_INFINITY;
            }
            for (int r = 0; r < m; r++) {
                int k = idx.get(r);
                double v = x[r];
                if (LnpModel.POSITIVE[k]) v = Math.max(v, k == LnpModel.PEAK_A ? 1e-12 * Math.abs(x[0]) + 1e-30 : 1e-30);
                p[k] = v;
            }
        }
        double[] f = model.intensity(p, q);
        double chi = 0;
        for (int j = 0; j < q.length; j++) { double r = (I[j] - f[j]) / s[j]; chi += r * r; }
        return chi;
    }

    // ------------------------------------------------------------------ fit

    /** shell-structure parameters, refined in a second stage after the size, spread, peak and background */
    private static final int[] SHELL_STAGE = {LnpModel.T1, LnpModel.T2, LnpModel.T3, LnpModel.T4,
            LnpModel.RHO_C, LnpModel.RHO_1, LnpModel.RHO_2, LnpModel.RHO_3, LnpModel.RHO_4};

    /**
     * Staged fit: when {@code scan} is set, the core radius is scanned, then everything except the shell structure is
     * refined, then all free parameters together. Uncertainties come from the Jacobian at the final point; strongly
     * correlated pairs are reported as warnings.
     */
    public Result fit(double[] start, boolean[] fixedIn, boolean scan, Cancel cancel) {
        if (!scan) return fitOnce(start, fixedIn, false, cancel);
        boolean[] stage1 = fixedIn.clone();
        boolean anyShellFree = false;
        for (int k : SHELL_STAGE) if (model.uses(k) && !stage1[k]) { stage1[k] = true; anyShellFree = true; }
        if (!anyShellFree) return fitOnce(start, fixedIn, true, cancel);
        Result r1 = fitOnce(start, stage1, true, cancel);
        Result r2 = fitOnce(r1.params, fixedIn, false, cancel);
        return r2.chi2 <= r1.chi2 * (1 + 1e-9) ? r2 : withWarning(r1, "Releasing the shell parameters did not improve the fit; the stage-1 result (shells at their starting values) is shown.");
    }

    private Result withWarning(Result r, String w) {
        r.warnings.add(w);
        return r;
    }

    private Result fitOnce(double[] start, boolean[] fixedIn, boolean scan, Cancel cancel) {
        boolean[] fixed = fixedIn.clone();
        List<String> warnings = new ArrayList<>();
        // unused parameters are fixed
        for (int k = 0; k < LnpModel.NPAR; k++) if (!model.uses(k)) fixed[k] = true;
        // overall density magnitude is absorbed by the normalisation: one density must be fixed
        boolean anyRhoFixed = false;
        int firstRho = -1;
        for (int k = 0; k < LnpModel.NPAR; k++) if (LnpModel.isDensity(k) && model.uses(k)) {
            if (firstRho < 0) firstRho = k;
            if (fixed[k]) anyRhoFixed = true;
        }
        if (!anyRhoFixed && firstRho >= 0) {
            fixed[firstRho] = true;
            warnings.add("Only density ratios are set by the data; " + LnpModel.NAMES[firstRho] + " was held fixed as the reference.");
        }

        double[] p = start.clone();
        clamp(p, fixed);
        if (scan) p = scanStart(p, fixed);
        else solveLinear(p, fixed);
        clamp(p, fixed);

        final int[] free = freeIndices(fixed);
        final double[] base = p.clone();
        int iterations = 0;
        if (free.length > 0) {
            double[] u0 = new double[free.length];
            for (int i = 0; i < free.length; i++) u0[i] = toU(free[i], p[free[i]]);
            final double[] bestCost = {Double.POSITIVE_INFINITY};
            final double[][] bestU = {u0.clone()};
            final double[] target = new double[q.length];
            for (int j = 0; j < q.length; j++) target[j] = I[j] / s[j];

            MultivariateJacobianFunction fn = point -> {
                if (cancel != null && cancel.cancelled()) throw new CancelledException();
                double[] u = point.toArray();
                double[] f0 = weighted(u, free, base);
                double[][] jac = new double[q.length][free.length];
                for (int i = 0; i < free.length; i++) {
                    if (cancel != null && cancel.cancelled()) throw new CancelledException();
                    double h = 1e-5 * Math.max(1, Math.abs(u[i]));
                    double[] up = u.clone(), dn = u.clone();
                    up[i] += h; dn[i] -= h;
                    double[] fp = weighted(up, free, base), fm = weighted(dn, free, base);
                    for (int j = 0; j < q.length; j++) jac[j][i] = (fp[j] - fm[j]) / (2 * h);
                }
                double c = 0;
                for (int j = 0; j < q.length; j++) { double r = f0[j] - target[j]; c += r * r; }
                if (c < bestCost[0]) { bestCost[0] = c; bestU[0] = u.clone(); }
                return new Pair<RealVector, RealMatrix>(new ArrayRealVector(f0, false), new Array2DRowRealMatrix(jac, false));
            };
            LeastSquaresOptimizer.Optimum opt;
            try {
            opt = new LevenbergMarquardtOptimizer()
                    .withCostRelativeTolerance(1e-7).withParameterRelativeTolerance(1e-7)
                    .optimize(new LeastSquaresBuilder()
                            .model(fn)
                            .target(target)
                            .start(u0)
                            .maxEvaluations(400)
                            .maxIterations(120)
                            .parameterValidator(v -> {
                                double[] u = v.toArray();
                                double[] pp = assemble(u, free, base);
                                clamp(pp, heldOutside(free));
                                for (int i = 0; i < free.length; i++) u[i] = toU(free[i], pp[free[i]]);
                                return new ArrayRealVector(u, false);
                            })
                            .build());
            } catch (org.apache.commons.math3.exception.TooManyEvaluationsException
                     | org.apache.commons.math3.exception.TooManyIterationsException e) {
                opt = null;
            }
            if (opt == null) {
                p = assemble(bestU[0], free, base);
                clamp(p, fixed);
                double[] fit = model.intensity(p, q);
                double chi2 = chi2(fit);
                warnings.add("The fit stopped before converging (best point shown). This usually means some parameters "
                        + "cannot be separated by the data. See the correlation warnings, fix one of each pair and press Fit again.");
                double[] err = uncertainties(p, free, chi2, warnings);
                checkLimits(p, fixed, warnings);
                return new Result(model, p, err, fixed, chi2, q.length, free.length, 120, q, I, s, fit, warnings);
            }
            iterations = opt.getIterations();
            p = assemble(opt.getPoint().toArray(), free, base);
            clamp(p, fixed);

            double[] fit = model.intensity(p, q);
            double chi2 = chi2(fit);
            double[] err = uncertainties(p, free, chi2, warnings);
            checkLimits(p, fixed, warnings);
            return new Result(model, p, err, fixed, chi2, q.length, free.length, iterations, q, I, s, fit, warnings);
        }
        double[] fit = model.intensity(p, q);
        double[] err = new double[LnpModel.NPAR];
        java.util.Arrays.fill(err, Double.NaN);
        return new Result(model, p, err, fixed, chi2(fit), q.length, 0, 0, q, I, s, fit, warnings);
    }

    /** model curve for given parameters (no fitting), as a Result so the panel can show it */
    public Result evaluate(double[] p, boolean[] fixed) {
        double[] fit = model.intensity(p, q);
        double[] err = new double[LnpModel.NPAR];
        java.util.Arrays.fill(err, Double.NaN);
        int nfree = freeIndices(fixed).length;
        return new Result(model, p.clone(), err, fixed.clone(), chi2(fit), q.length, nfree, 0, q, I, s, fit, new ArrayList<>());
    }

    /**
     * Standard errors from (J^T J)^-1 at p (J of the weighted residuals with respect to the fitted coordinates),
     * scaled by sqrt(reduced chi^2) when it exceeds 1. Pairs with |correlation| &gt; 0.95 are reported.
     */
    double[] uncertainties(double[] p, int[] free, double chi2, List<String> warnings) {
        double[] err = new double[LnpModel.NPAR];
        Arrays.fill(err, Double.NaN);
        int m = free.length;
        if (m == 0) return err;
        double[] u = new double[m];
        for (int i = 0; i < m; i++) u[i] = toU(free[i], p[free[i]]);
        double[][] jac = new double[q.length][m];
        for (int i = 0; i < m; i++) {
            double h = 1e-5 * Math.max(1, Math.abs(u[i]));
            double[] up = u.clone(), dn = u.clone();
            up[i] += h; dn[i] -= h;
            double[] fp = weighted(up, free, p), fm = weighted(dn, free, p);
            for (int j = 0; j < q.length; j++) jac[j][i] = (fp[j] - fm[j]) / (2 * h);
        }
        RealMatrix J = new Array2DRowRealMatrix(jac, false);
        RealMatrix jtj = J.transpose().multiply(J);
        RealMatrix cov;
        try {
            org.apache.commons.math3.linear.SingularValueDecomposition svd = new org.apache.commons.math3.linear.SingularValueDecomposition(jtj);
            if (svd.getConditionNumber() > 1e14) throw new org.apache.commons.math3.linear.SingularMatrixException();
            cov = svd.getSolver().getInverse();
        } catch (RuntimeException singular) {
            warnings.add("Some parameters have no effect on the curve (singular fit); uncertainties are not available. Fix more parameters.");
            return err;
        }
        double red = Math.max(1, chi2 / Math.max(1, q.length - m));
        for (int i = 0; i < m; i++) {
            double su = Math.sqrt(Math.max(0, cov.getEntry(i, i)) * red);
            err[free[i]] = LnpModel.POSITIVE[free[i]] ? p[free[i]] * su : su;
        }
        // report the three most strongly correlated pairs (scale excluded: it always follows the densities)
        List<double[]> pairs = new ArrayList<>();
        for (int i = 0; i < m; i++)
            for (int k = i + 1; k < m; k++) {
                double c = cov.getEntry(i, k) / Math.sqrt(cov.getEntry(i, i) * cov.getEntry(k, k));
                if (Math.abs(c) > 0.95 && free[i] != LnpModel.SCALE && free[k] != LnpModel.SCALE)
                    pairs.add(new double[]{free[i], free[k], c});
            }
        pairs.sort((a, b) -> Double.compare(Math.abs(b[2]), Math.abs(a[2])));
        for (int i = 0; i < Math.min(3, pairs.size()); i++)
            warnings.add(String.format("%s and %s are strongly correlated (r = %+.2f): the data cannot separate them; fix one.",
                    LnpModel.NAMES[(int) pairs.get(i)[0]], LnpModel.NAMES[(int) pairs.get(i)[1]], pairs.get(i)[2]));
        return err;
    }

    private void checkLimits(double[] p, boolean[] fixed, List<String> warnings) {
        for (int k = 0; k < LnpModel.NPAR; k++) {
            if (fixed[k] || !model.uses(k)) continue;
            double lo = lower(k, p), hi = upper(k, p);
            boolean atLo = !Double.isInfinite(lo) && p[k] <= lo * (1 + 1e-6) + 1e-12;
            boolean atHi = !Double.isInfinite(hi) && p[k] >= hi * (1 - 1e-6);
            if (atLo || atHi) warnings.add(LnpModel.NAMES[k] + " ended at its " + (atLo ? "lower" : "upper") + " limit; consider fixing it or changing the model.");
        }
    }

    private double chi2(double[] fit) {
        double c = 0;
        for (int j = 0; j < q.length; j++) { double r = (I[j] - fit[j]) / s[j]; c += r * r; }
        return c;
    }

    private double[] weighted(double[] u, int[] free, double[] base) {
        double[] p = assemble(u, free, base);
        clamp(p, heldOutside(free));
        double[] f = model.intensity(p, q);
        for (int j = 0; j < q.length; j++) f[j] /= s[j];
        return f;
    }

    private static double[] assemble(double[] u, int[] free, double[] base) {
        double[] p = base.clone();
        for (int i = 0; i < free.length; i++) p[free[i]] = fromU(free[i], u[i]);
        return p;
    }

    static int[] freeIndices(boolean[] fixed) {
        int n = 0;
        for (boolean f : fixed) if (!f) n++;
        int[] out = new int[n];
        n = 0;
        for (int k = 0; k < fixed.length; k++) if (!fixed[k]) out[n++] = k;
        return out;
    }

    private static double toU(int k, double v) { return LnpModel.POSITIVE[k] ? Math.log(Math.max(v, 1e-300)) : v; }

    private static double fromU(int k, double u) { return LnpModel.POSITIVE[k] ? Math.exp(u) : u; }

    public static final class CancelledException extends RuntimeException {
        CancelledException() { super("cancelled"); }
    }
}
