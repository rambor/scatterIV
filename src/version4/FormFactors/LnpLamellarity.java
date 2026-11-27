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
import java.util.List;

/**
 * How many bilayers a vesicle preparation actually has, and how much of its lipid is on the outside.
 *
 * <p>Extrusion through a porous filter is assumed to produce unilamellar vesicles, and Scott et al.
 * (<i>Biophys. J.</i> <b>117</b>, 1381 (2019)) show that for neutral phosphatidylcholines it frequently does not:
 * extruded POPC in physiological buffer keeps prominent, if broadened, lamellar reflections, confirmed by
 * cryo-EM, and leaves only about 35% of the lipid externally accessible. The same work finds that charged lipid
 * and low ionic strength raise that fraction, that 30-50 nm pores or sonication give essentially unilamellar
 * vesicles, and that across eight phosphatidylcholines the accessible fraction after 100 nm extrusion ran from
 * 0.39 to 0.46, with egg sphingomyelin an outlier at 0.49. A uniform size distribution by light scattering says
 * nothing about any of this: their multilamellar and unilamellar vesicles were the same size.</p>
 *
 * <p>This class fits the model that measures it: one bilayer form factor multiplied by the structure factor of a
 * stack, averaged over a distribution of the number of lamellae with the unilamellar population carrying its own
 * weight. That mixture is the point. Asking separately "is this a particle or a stack?" — a form factor on one
 * tab and an indexed lattice on another — is the wrong question for a vesicle preparation, because the honest
 * answer is nearly always "both, in some proportion", and the proportion is the quantity of interest.</p>
 *
 * <h3>The model</h3>
 * <p>A locally flat bilayer whose electron-density profile across the membrane is three Gaussians — two
 * headgroup layers either side of a methylene trough (the profile {@link LnpLamellar} fits from Bragg
 * intensities, so the two agree by construction):</p>
 * <pre>
 *   F(q) = sqrt(2 pi) [ 2 sigma_H cos(q z_H) exp(-q^2 sigma_H^2 / 2)
 *                       + rho_r sigma_C exp(-q^2 sigma_C^2 / 2) ]
 * </pre>
 * <p>with the headgroup density taken as 1 and {@code rho_r} the trough depth relative to it (negative: the
 * chains sit below water). The head-to-head thickness is d<sub>HH</sub> = 2 z<sub>H</sub>.</p>
 *
 * <p>N such bilayers at a repeat d, with the paracrystalline disorder of a stack whose layer positions wander by
 * an rms {@code delta} per interface, scatter as</p>
 * <pre>
 *   S_N(q) = N + 2 sum_{k=1}^{N-1} (N - k) cos(k q d) exp(-k q^2 delta^2 / 2)
 * </pre>
 * <p>The disorder enters with k rather than k squared because the displacements accumulate along the stack: the
 * variance between layers k apart grows linearly with k, which is what makes the higher orders broaden
 * progressively rather than all alike. S<sub>1</sub> = 1, so a unilamellar vesicle contributes its bilayer form
 * factor and nothing else, and no special case is needed for it.</p>
 *
 * <p>The number of lamellae is distributed. Following Scott et al. it is a modified exponential: a free weight on
 * the unilamellar population and a geometric tail above it,</p>
 * <pre>
 *   P(1) = w1,    P(N) = (1 - w1) (1 - r) r^(N-2)   for N &gt;= 2
 * </pre>
 * <p>so that a sample can be mostly unilamellar with a long tail — the shape their cryo-EM counts show — rather
 * than being forced to a single mean. The intensity is then the lipid-weighted average, with the 1/q<sup>2</sup>
 * Lorentz factor of randomly oriented locally flat sheets:</p>
 * <pre>
 *   I(q) = scale |F(q)|^2 &lt;S&gt; / (q^2 &lt;N&gt;) + background,   &lt;S&gt; = sum_N P(N) S_N(q)
 * </pre>
 *
 * <h3>What comes out</h3>
 * <p>The externally accessible lipid fraction. Only the outer leaflet of the outermost bilayer of a vesicle is
 * reachable from outside, so for bilayers of equal area</p>
 * <pre>
 *   f_acc = 0.5 / &lt;N&gt;
 * </pre>
 * <p>which is 0.5 for a perfectly unilamellar preparation — the ceiling, and the reason their figures run to 0.5
 * and not to 1. Where a vesicle radius is known the areas are not equal, since the inner bilayers of a small
 * vesicle hold less lipid than the outer ones; {@link #accessibleFraction(double, double, double, double)} then
 * weights each bilayer by (R - j d)^2, which raises f_acc above 0.5/&lt;N&gt;. That correction is small for 100 nm
 * vesicles and not small for 30 nm ones, so it is applied whenever a radius is available.</p>
 *
 * <h3>Where it is valid</h3>
 * <p>The flat-bilayer approximation with a Lorentz factor holds where the bilayer is thin against the vesicle
 * radius and the scattering is dominated by the membrane profile rather than the overall particle: roughly
 * q &gt; 0.05 A<sup>-1</sup>, which is also the range Brzustowicz &amp; Brunger (<i>J. Appl. Cryst.</i> <b>38</b>,
 * 126 (2005)) identify as the one sensitive to bilayer profile parameters, with the overall size instead
 * controlling q below about 0.05. The fit therefore starts at 0.05 by default and the overall radius is taken
 * from elsewhere rather than fitted here.</p>
 */
final class LnpLamellarity {

    /** the most lamellae the sum runs to; the geometric tail is renormalised over this range */
    static final int N_MAX = 40;

    /** parameter indices */
    static final int SCALE = 0, Z_H = 1, SIGMA_H = 2, SIGMA_C = 3, RHO_R = 4, D = 5, DELTA = 6,
            W1 = 7, R = 8, BG = 9;
    static final int NPAR = 10;

    static final String[] NAMES = {
            "Scale", "Headgroup position z_H", "Headgroup width σ_H", "Trough width σ_C",
            "Trough depth ρ_r", "Lamellar repeat d", "Stacking disorder Δ",
            "Unilamellar number fraction w₁", "Tail ratio r", "Background"};

    static final String[] UNITS = {"", "Å", "Å", "Å", "", "Å", "Å", "", "", ""};

    static final class Result {
        double[] params = new double[NPAR];
        double[] errors = new double[NPAR];
        double chi2, chi2Reduced;
        int points, free, iterations;
        double qmin, qmax;
        double[] q, I, sigma, fit;
        /** the same model with every vesicle unilamellar: the dashed curve of Scott et al. Fig. 1A */
        double[] unilamellarOnly;
        /** number-weighted P(N), index 0 holding N = 1 */
        double[] distribution = new double[N_MAX];
        double meanLamellae = Double.NaN;
        /** externally accessible lipid fraction, and 0.5 means fully unilamellar */
        double accessible = Double.NaN, accessibleErr = Double.NaN;
        /** vesicle radius used for the area weighting, or NaN if bilayers were taken as equal in area */
        double radius = Double.NaN;
        final List<String> warnings = new ArrayList<>();

        double headToHead() { return 2 * params[Z_H]; }

        /**
         * Whether anything here may be quoted. A model that does not describe the curve still returns an
         * accessible fraction with a formal error, and that pair of numbers is worse than no answer: it reads
         * as a measurement. Above a reduced chi-squared of 20 the fraction is withheld.
         */
        boolean trustworthy() { return chi2Reduced <= 20 && waterLayer() >= 0; }
        double waterLayer() { return params[D] - headToHead(); }
        /** how far the preparation is from the unilamellar ceiling, as a percentage of lipid */
        double inaccessiblePercent() { return 100 * (1 - accessible / 0.5); }
    }

    private LnpLamellarity() { }

    // ------------------------------------------------------------------ the physics

    /** bilayer form factor: two Gaussian headgroup layers and a methylene trough */
    static double formFactor(double q, double zH, double sigmaH, double sigmaC, double rhoR) {
        double root = Math.sqrt(2 * Math.PI);
        return root * (2 * sigmaH * Math.cos(q * zH) * Math.exp(-0.5 * q * q * sigmaH * sigmaH)
                + rhoR * sigmaC * Math.exp(-0.5 * q * q * sigmaC * sigmaC));
    }

    /** structure factor of N bilayers at repeat d with paracrystalline disorder delta */
    static double stack(double q, int n, double d, double delta) {
        if (n <= 1) return 1;
        double s = n;
        double var = 0.5 * q * q * delta * delta;
        for (int k = 1; k < n; k++) {
            double damp = Math.exp(-k * var);
            if (damp < 1e-12) break;
            s += 2 * (n - k) * Math.cos(k * q * d) * damp;
        }
        return s;
    }

    /** number-weighted P(N): a free unilamellar weight and a geometric tail, renormalised over 1..N_MAX */
    static double[] distribution(double w1, double r) {
        double[] p = new double[N_MAX];
        double w = Math.min(Math.max(w1, 0), 1);
        double rr = Math.min(Math.max(r, 0), 0.999);
        p[0] = w;
        double sum = w;
        for (int n = 2; n <= N_MAX; n++) {
            double v = (1 - w) * (1 - rr) * Math.pow(rr, n - 2);
            p[n - 1] = v;
            sum += v;
        }
        if (sum > 0) for (int i = 0; i < N_MAX; i++) p[i] /= sum;
        return p;
    }

    static double meanLamellae(double[] p) {
        double m = 0;
        for (int i = 0; i < p.length; i++) m += p[i] * (i + 1);
        return m;
    }

    /**
     * Externally accessible lipid fraction.
     *
     * <p>With {@code radius} NaN the bilayers of one vesicle are taken as equal in area and the result is
     * 0.5/&lt;N&gt;. With a radius the areas are not equal: bilayer j in from the outside has area proportional
     * to (R - j d)^2, so the outermost holds more lipid than the ones behind it and the accessible fraction is
     * correspondingly higher. A stack that would run past the centre of the vesicle is truncated, which is also
     * the physical statement that a small vesicle cannot hold many lamellae.</p>
     */
    static double accessibleFraction(double[] p, double d, double radius, double unusedForSignature) {
        double acc = 0, total = 0;
        for (int i = 0; i < p.length; i++) {
            int n = i + 1;
            if (p[i] <= 0) continue;
            double outer, all = 0;
            if (Double.isNaN(radius) || radius <= 0) {
                outer = 1;
                all = n;
            } else {
                outer = radius * radius;
                for (int j = 0; j < n; j++) {
                    double rj = radius - j * d;
                    if (rj <= 0) break;
                    all += rj * rj;
                }
                if (all <= 0) continue;
            }
            acc += p[i] * 0.5 * outer;
            total += p[i] * all;
        }
        return total > 0 ? acc / total : Double.NaN;
    }

    /**
     * Pair weights of the distribution: T(k) = sum over N &gt; k of P(N) (N - k), the number of pairs of
     * bilayers k apart.
     *
     * <p>Worth having because the average structure factor collapses onto it. Summing S<sub>N</sub> separately
     * for every N at every q costs O(N<sub>max</sub><sup>2</sup>) per point, but the N-sum and the q-dependence
     * separate:</p>
     * <pre>
     *   &lt;S&gt;(q) = &lt;N&gt; + 2 sum_k T(k) cos(k q d) exp(-k q^2 delta^2 / 2)
     * </pre>
     * <p>T depends only on the distribution, so it is computed once per parameter set and the cost per point
     * falls to O(N<sub>max</sub>). That is what makes the coarse scan below affordable, and the scan is what
     * makes the fit find the repeat at all.</p>
     */
    static double[] pairWeights(double[] p) {
        double[] t = new double[p.length];
        for (int k = 1; k < p.length; k++) {
            double s = 0;
            for (int n = k + 1; n <= p.length; n++) s += p[n - 1] * (n - k);
            t[k] = s;
        }
        return t;
    }

    /** average structure factor over the distribution, from its pair weights */
    static double meanStack(double q, double[] pairs, double mean, double d, double delta) {
        double s = mean;
        double var = 0.5 * q * q * delta * delta;
        for (int k = 1; k < pairs.length; k++) {
            if (pairs[k] <= 0) continue;
            double damp = Math.exp(-k * var);
            if (damp < 1e-12) break;
            s += 2 * pairs[k] * Math.cos(k * q * d) * damp;
        }
        return s;
    }

    /** the model curve */
    static double[] intensity(double[] par, double[] q) {
        double[] p = distribution(par[W1], par[R]);
        double mean = meanLamellae(p);
        double[] pairs = pairWeights(p);
        double[] out = new double[q.length];
        for (int i = 0; i < q.length; i++) {
            double f = formFactor(q[i], par[Z_H], par[SIGMA_H], par[SIGMA_C], par[RHO_R]);
            double s = meanStack(q[i], pairs, mean, par[D], par[DELTA]);
            out[i] = par[SCALE] * f * f * s / (q[i] * q[i] * Math.max(mean, 1e-9)) + par[BG];
        }
        return out;
    }

    /** the same bilayer with every vesicle unilamellar, for drawing beside the fit */
    static double[] unilamellar(double[] par, double[] q) {
        double[] out = new double[q.length];
        for (int i = 0; i < q.length; i++) {
            double f = formFactor(q[i], par[Z_H], par[SIGMA_H], par[SIGMA_C], par[RHO_R]);
            out[i] = par[SCALE] * f * f / (q[i] * q[i]) + par[BG];
        }
        return out;
    }

    // ------------------------------------------------------------------ parameter transforms

    /** positive parameters are fitted as logarithms, the two fractions through a logit, the rest directly */
    private static double toU(int k, double v) {
        switch (k) {
            case SCALE: case Z_H: case SIGMA_H: case SIGMA_C: case D:
                return Math.log(Math.max(v, 1e-12));
            case DELTA: case BG:
                return Math.log(Math.max(v, 1e-9));
            case W1: case R: {
                double x = Math.min(Math.max(v, 1e-4), 1 - 1e-4);
                return Math.log(x / (1 - x));
            }
            default:
                return v;
        }
    }

    private static double fromU(int k, double u) {
        switch (k) {
            case SCALE: case Z_H: case SIGMA_H: case SIGMA_C: case D: case DELTA: case BG:
                return Math.exp(u);
            case W1: case R:
                return 1 / (1 + Math.exp(-u));
            default:
                return u;
        }
    }

    private static void clamp(double[] p) {
        p[Z_H] = Math.min(Math.max(p[Z_H], 8), 40);
        p[SIGMA_H] = Math.min(Math.max(p[SIGMA_H], 1.0), 8);
        p[SIGMA_C] = Math.min(Math.max(p[SIGMA_C], 1.0), 12);
        p[RHO_R] = Math.min(Math.max(p[RHO_R], -4), -0.05);
        p[D] = Math.min(Math.max(p[D], 30), 200);
        p[DELTA] = Math.min(Math.max(p[DELTA], 0), 20);
        p[W1] = Math.min(Math.max(p[W1], 1e-4), 1 - 1e-4);
        p[R] = Math.min(Math.max(p[R], 1e-4), 0.95);
        p[SCALE] = Math.max(p[SCALE], 1e-30);
        p[BG] = Math.max(p[BG], 0);
    }

    /** sensible starting values for a phosphatidylcholine bilayer */
    static double[] start() {
        double[] p = new double[NPAR];
        p[SCALE] = 1;
        p[Z_H] = 19;          // d_HH about 38 A
        p[SIGMA_H] = 3.0;
        p[SIGMA_C] = 4.5;
        p[RHO_R] = -1.0;
        p[D] = 62.6;          // the POPC repeat of Scott et al., as good a starting guess as any PC
        p[DELTA] = 3.0;
        p[W1] = 0.6;
        p[R] = 0.5;
        p[BG] = 0;
        return p;
    }

    // ------------------------------------------------------------------ the fit

    /**
     * Fits the lamellarity model.
     *
     * @param dRepeat a repeat distance to start from, or NaN to use the default; pass the first lamellar order's
     *                2&pi;/q when the assessment found one, which is much the strongest constraint available
     * @param radius  mean vesicle radius in angstrom for the area weighting, or NaN to take the bilayers as equal
     *                in area
     */
    static Result fit(double[] q, double[] I, double[] s, double qmin, double qmax, double dRepeat, double radius) {
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < q.length; i++) {
            if (q[i] >= qmin && q[i] <= qmax && s[i] > 0 && I[i] > 0) keep.add(i);
        }
        if (keep.size() < 25)
            throw new IllegalArgumentException("Need at least 25 usable points between q = "
                    + Fields.fmt(qmin) + " and " + Fields.fmt(qmax));

        final int n = keep.size();
        final double[] qq = new double[n], ii = new double[n], ss = new double[n];
        for (int k = 0; k < n; k++) { int at = keep.get(k); qq[k] = q[at]; ii[k] = I[at]; ss[k] = s[at]; }

        double[] p = start();
        if (!Double.isNaN(dRepeat) && dRepeat > 30 && dRepeat < 200) p[D] = dRepeat;
        p[SCALE] = guessScale(p, qq, ii);

        Result r = new Result();
        r.qmin = qq[0];
        r.qmax = qq[n - 1];
        r.radius = radius;

        // Staged, and the order is the whole difficulty. Two things make this curve: a smooth bilayer envelope
        // and, on top of it, the interlamellar peaks. The envelope is far the larger, and with relative errors
        // of a few percent a small error in the bilayer profile costs more chi-squared than getting the repeat
        // completely wrong. So the profile has to be settled first, with the sample treated as unilamellar;
        // only then is there a residual in which the stack is the dominant feature, and only then can the
        // repeat be scanned for. Scanning first - which is the obvious thing to do and what I tried - searches
        // a surface dominated by profile mismatch and returns whichever repeat least disturbs the envelope.
        int[][] profileStages = {
                {SCALE, BG},
                {SCALE, Z_H, SIGMA_H, SIGMA_C, RHO_R, BG},
        };
        int[] stackStage = {SCALE, D, DELTA, W1, R, BG};
        int[] allStage = {SCALE, Z_H, SIGMA_H, SIGMA_C, RHO_R, D, DELTA, W1, R, BG};
        int iterations = 0;
        // the profile is fitted against a unilamellar sample, so the stack term cannot absorb envelope misfit
        double keepW1 = p[W1], keepR = p[R];
        p[W1] = 1 - 1e-6;
        for (int[] stage : profileStages) { p = run(p, stage, qq, ii, ss); iterations++; }
        p[W1] = keepW1;
        p[R] = keepR;
        // now the repeat is the dominant feature of what is left over, and a comb-shaped cost surface in d can
        // be searched for rather than walked down
        p = scan(p, qq, ii, ss);
        int[][] stages = {stackStage, allStage};
        for (int[] stage : stages) {
            p = run(p, stage, qq, ii, ss);
            iterations++;
        }

        double[] fit = intensity(p, qq);
        double chi2 = 0;
        for (int i = 0; i < n; i++) { double d = (ii[i] - fit[i]) / ss[i]; chi2 += d * d; }

        r.params = p;
        r.errors = errors(p, stages[stages.length - 1], qq, ss);
        r.points = n;
        r.free = allStage.length;
        r.iterations = iterations;
        r.chi2 = chi2;
        r.chi2Reduced = chi2 / Math.max(1, n - r.free);
        r.q = qq; r.I = ii; r.sigma = ss; r.fit = fit;
        r.unilamellarOnly = unilamellar(p, qq);
        r.distribution = distribution(p[W1], p[R]);
        r.meanLamellae = meanLamellae(r.distribution);
        r.accessible = accessibleFraction(r.distribution, p[D], radius, 0);
        r.accessibleErr = accessibleError(r, qq, ss);

        checks(r);
        return r;
    }

    /**
     * A coarse grid over the four parameters the fit cannot walk to: the repeat, the stacking disorder, and the
     * two describing the distribution of lamellae. At each grid point the scale is solved exactly rather than
     * fitted, so one point costs one model evaluation, and the data is thinned first because a grid search does
     * not need every point to tell a good region from a bad one.
     *
     * <p>The grid is centred on whatever repeat was passed in but spans the whole plausible range, because a
     * repeat guessed from a peak that turns out to be a form-factor lobe would otherwise pin the answer to a
     * spacing nothing in the sample has.</p>
     */
    private static double[] scan(double[] base, double[] q, double[] I, double[] s) {
        int step = Math.max(1, q.length / 120);
        int n = (q.length + step - 1) / step;
        double[] tq = new double[n], ti = new double[n], ts = new double[n];
        for (int i = 0, k = 0; i < q.length && k < n; i += step, k++) { tq[k] = q[i]; ti[k] = I[i]; ts[k] = s[i]; }

        double[] best = base.clone();
        double bestCost = Double.MAX_VALUE;
        double[] deltas = {0.5, 1.5, 3, 5, 8};
        double[] w1s = {0.05, 0.2, 0.4, 0.6, 0.8, 0.95};
        double[] rs = {0.15, 0.35, 0.6};
        for (double d = 40; d <= 130; d += 0.5) {
            for (double delta : deltas) {
                for (double w1 : w1s) {
                    for (double r : rs) {
                        double[] p = base.clone();
                        p[D] = d; p[DELTA] = delta; p[W1] = w1; p[R] = r;
                        p[SCALE] = 1; p[BG] = 0;
                        double[] m = intensity(p, tq);
                        // scale solved exactly for this shape
                        double num = 0, den = 0;
                        for (int i = 0; i < n; i++) {
                            double w = 1 / (ts[i] * ts[i]);
                            num += w * m[i] * ti[i];
                            den += w * m[i] * m[i];
                        }
                        if (den <= 0) continue;
                        double scale = Math.max(num / den, 1e-30);
                        double cost = 0;
                        for (int i = 0; i < n; i++) {
                            double res = (ti[i] - scale * m[i]) / ts[i];
                            cost += res * res;
                        }
                        if (cost < bestCost) {
                            bestCost = cost;
                            p[SCALE] = scale;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** the scale that minimises chi-squared for the current shape, solved rather than fitted */
    private static double guessScale(double[] p, double[] q, double[] I) {
        double[] unit = p.clone();
        unit[SCALE] = 1;
        unit[BG] = 0;
        double[] m = intensity(unit, q);
        double num = 0, den = 0;
        for (int i = 0; i < q.length; i++) { num += m[i] * I[i]; den += m[i] * m[i]; }
        return den > 0 ? Math.max(num / den, 1e-30) : 1;
    }

    private static double[] run(double[] base, int[] free, double[] q, double[] I, double[] s) {
        final double[] start = base.clone();
        double[] u0 = new double[free.length];
        for (int i = 0; i < free.length; i++) u0[i] = toU(free[i], start[free[i]]);

        final double[] target = new double[q.length];           // fitting I/sigma against zero residual
        for (int i = 0; i < q.length; i++) target[i] = I[i] / s[i];

        MultivariateJacobianFunction fn = point -> {
            double[] u = point.toArray();
            double[] f0 = weighted(u, free, start, q, s);
            double[][] jac = new double[q.length][free.length];
            for (int i = 0; i < free.length; i++) {
                double h = 1e-5 * Math.max(1, Math.abs(u[i]));
                double[] up = u.clone(), dn = u.clone();
                up[i] += h; dn[i] -= h;
                double[] fp = weighted(up, free, start, q, s), fm = weighted(dn, free, start, q, s);
                for (int j = 0; j < q.length; j++) jac[j][i] = (fp[j] - fm[j]) / (2 * h);
            }
            return new Pair<RealVector, RealMatrix>(new ArrayRealVector(f0, false),
                    new Array2DRowRealMatrix(jac, false));
        };

        try {
            LeastSquaresOptimizer.Optimum opt = new LevenbergMarquardtOptimizer()
                    .withCostRelativeTolerance(1e-8).withParameterRelativeTolerance(1e-8)
                    .optimize(new LeastSquaresBuilder()
                            .model(fn)
                            .target(target)
                            .start(u0)
                            .maxEvaluations(800)
                            .maxIterations(200)
                            .parameterValidator(v -> {
                                double[] u = v.toArray();
                                double[] pp = assemble(u, free, start);
                                clamp(pp);
                                for (int i = 0; i < free.length; i++) u[i] = toU(free[i], pp[free[i]]);
                                return new ArrayRealVector(u, false);
                            })
                            .build());
            double[] out = assemble(opt.getPoint().toArray(), free, start);
            clamp(out);
            return out;
        } catch (RuntimeException e) {
            // a stage that will not converge leaves the parameters where they were: the next stage may still
            // move them, and a thrown exception here would lose a usable partial fit
            return start;
        }
    }

    private static double[] assemble(double[] u, int[] free, double[] base) {
        double[] p = base.clone();
        for (int i = 0; i < free.length; i++) p[free[i]] = fromU(free[i], u[i]);
        return p;
    }

    private static double[] weighted(double[] u, int[] free, double[] base, double[] q, double[] s) {
        double[] p = assemble(u, free, base);
        clamp(p);
        double[] m = intensity(p, q);
        double[] out = new double[q.length];
        for (int i = 0; i < q.length; i++) out[i] = m[i] / s[i];
        return out;
    }

    /** parameter errors from the numerical Jacobian of the weighted residuals */
    private static double[] errors(double[] p, int[] free, double[] q, double[] s) {
        double[] err = new double[NPAR];
        for (int i = 0; i < NPAR; i++) err[i] = Double.NaN;
        try {
            double[][] jac = new double[q.length][free.length];
            for (int i = 0; i < free.length; i++) {
                double h = 1e-5 * Math.max(1e-6, Math.abs(p[free[i]]));
                double[] up = p.clone(), dn = p.clone();
                up[free[i]] += h; dn[free[i]] -= h;
                clamp(up); clamp(dn);
                double[] fp = intensity(up, q), fm = intensity(dn, q);
                double step = up[free[i]] - dn[free[i]];
                if (step == 0) return err;
                for (int j = 0; j < q.length; j++) jac[j][i] = (fp[j] - fm[j]) / (step * s[j]);
            }
            RealMatrix j = new Array2DRowRealMatrix(jac, false);
            RealMatrix cov = new org.apache.commons.math3.linear.LUDecomposition(
                    j.transpose().multiply(j)).getSolver().getInverse();
            for (int i = 0; i < free.length; i++) {
                double v = cov.getEntry(i, i);
                err[free[i]] = v > 0 ? Math.sqrt(v) : Double.NaN;
            }
        } catch (RuntimeException ignored) {
            // a singular normal matrix means some parameter is not determined by this data; NaN says so
        }
        return err;
    }

    /**
     * Error on the accessible fraction, propagated from the two distribution parameters.
     *
     * <p>f_acc is a function of w1 and r and of nothing else that was fitted, so its error is theirs carried
     * through the same function rather than a separate estimate. Where either is undetermined the fraction is
     * reported without an error, which is the honest outcome and not a small one.</p>
     */
    private static double accessibleError(Result r, double[] q, double[] s) {
        double ew = r.errors[W1], er = r.errors[R];
        if (Double.isNaN(ew) && Double.isNaN(er)) return Double.NaN;
        double w = r.params[W1], rr = r.params[R], d = r.params[D];
        double base = accessibleFraction(distribution(w, rr), d, r.radius, 0);
        double var = 0;
        if (!Double.isNaN(ew)) {
            double up = accessibleFraction(distribution(Math.min(w + ew, 1 - 1e-6), rr), d, r.radius, 0);
            var += (up - base) * (up - base);
        }
        if (!Double.isNaN(er)) {
            double up = accessibleFraction(distribution(w, Math.min(rr + er, 0.999)), d, r.radius, 0);
            var += (up - base) * (up - base);
        }
        return Math.sqrt(var);
    }

    // ------------------------------------------------------------------ what the answer means

    private static void checks(Result r) {
        double[] p = r.params;
        if (r.chi2Reduced > 20)
            r.warnings.add(String.format("The model does not describe this curve (reduced χ² = %.1f). Nothing "
                    + "below should be quoted; check the q range and whether the data is a vesicle preparation "
                    + "at all.", r.chi2Reduced));

        // A unilamellar answer is the easy one to get wrong: with no interlamellar peak in the fitted range
        // there is nothing for the stack term to grip, and w1 goes to one because nothing contradicts it.
        double q1 = 2 * Math.PI / p[D];
        if (q1 < r.qmin || q1 > r.qmax)
            r.warnings.add(String.format("The first order of the fitted repeat sits at q = %.4f Å⁻¹, outside the "
                    + "fitted range %.3f–%.3f. The lamellar term is then unconstrained and the unilamellar "
                    + "fraction below is not measured — refit over a range that includes it.", q1, r.qmin, r.qmax));
        else if (p[W1] > 0.98)
            r.warnings.add("The fit puts essentially every vesicle at one bilayer. That is a real result when "
                    + "the first order would have been visible and is not, which is the case here — but it is "
                    + "also what a fit returns when the peak is lost in a broad background, so check the curve.");

        if (Double.isNaN(r.errors[W1]) || Double.isNaN(r.errors[R]))
            r.warnings.add("The distribution of lamellae is not determined by this data: one of its two "
                    + "parameters has no error. Treat the accessible fraction as indicative only.");

        if (p[DELTA] > 10)
            r.warnings.add(String.format("The stacking disorder is large (Δ = %.1f Å against a repeat of %.1f Å). "
                    + "At that point the stack term is a broad hump rather than a peak and is hard to tell from "
                    + "the bilayer's own form factor.", p[DELTA], p[D]));

        double dhh = r.headToHead();
        if (dhh < 30 || dhh > 55)
            r.warnings.add(String.format("The head-to-head thickness of %.1f Å is outside the 30–55 Å a "
                    + "phosphatidylcholine bilayer normally gives. Check that the profile has not settled on a "
                    + "form-factor lobe of something else.", dhh));

        if (r.waterLayer() < 0)
            r.warnings.add("The fitted repeat is smaller than the fitted bilayer, which cannot be: the two "
                    + "parts of the model have gone to incompatible places. The fit has failed.");
    }

    static String describe(Result r) {
        StringBuilder sb = new StringBuilder();
        double[] p = r.params, e = r.errors;
        sb.append("LAMELLARITY AND ACCESSIBLE LIPID\n");
        sb.append("Scott et al., Biophys. J. 117, 1381 (2019): one bilayer form factor × the structure factor\n");
        sb.append("of a stack, averaged over a distribution of the number of lamellae.\n");
        sb.append(String.format("%d points over q = %.3f–%.3f Å⁻¹, %d free parameters, reduced χ² = %.2f%n%n",
                r.points, r.qmin, r.qmax, r.free, r.chi2Reduced));

        // A headline number above a warning is read as the answer and the warning as a formality, which is the
        // wrong way round: f_acc came back from the LNP test curve as 0.472 +- 0.002 on a fit with a reduced
        // chi-squared of 143. A small formal error on a meaningless fit is the most misleading pair of numbers
        // this panel can print, so where the fit has failed the headline says so and the fraction is withheld.
        if (!r.trustworthy()) {
            sb.append("  NO ANSWER: THE FIT DOES NOT DESCRIBE THIS CURVE\n");
            sb.append(String.format("  Reduced χ² = %.1f. The accessible fraction is not reported, because the "
                    + "number this fit%n  would give (%.3f) describes the fit and not the sample. The fitted "
                    + "parameters are below for%n  diagnosis only.%n%n", r.chi2Reduced, r.accessible));
            for (String w : r.warnings) sb.append("  ! ").append(w).append('\n');
            sb.append('\n');
            sb.append("  FITTED PARAMETERS (DIAGNOSTIC ONLY — DO NOT QUOTE)\n");
            for (int k = 0; k < NPAR; k++) {
                sb.append(String.format("  %-32s %12s %-12s %s%n", NAMES[k], Fields.fmt(p[k]),
                        Double.isNaN(e[k]) ? "" : "± " + Fields.fmt(e[k]), UNITS[k]));
            }
            return sb.toString();
        }

        sb.append("  THE ANSWER\n");
        sb.append(String.format("  Externally accessible lipid   f_acc = %.3f%s%n", r.accessible,
                Double.isNaN(r.accessibleErr) ? " (no error: see warnings)" : String.format(" ± %.3f", r.accessibleErr)));
        sb.append(String.format("  Mean number of bilayers       ⟨N⟩   = %.2f%n", r.meanLamellae));
        sb.append(String.format("  Unilamellar vesicles          w₁    = %.1f %% by number%n", 100 * p[W1]));
        sb.append(String.format("  Lipid NOT reachable from outside     %.0f %% more than a unilamellar prep%n%n",
                r.inaccessiblePercent()));
        sb.append("  f_acc = 0.5 is a perfectly unilamellar preparation: only the outer leaflet of the outer\n");
        sb.append("  bilayer is ever externally accessible, so 0.5 is the ceiling, not 1.0.\n");
        if (!Double.isNaN(r.radius))
            sb.append(String.format("  Bilayer areas weighted by (R − jd)² at R = %.0f Å, so inner bilayers hold less\n"
                    + "  lipid than outer ones; without that weighting f_acc would be %.3f.%n",
                    r.radius, accessibleFraction(r.distribution, p[D], Double.NaN, 0)));
        sb.append('\n');

        sb.append("  THE BILAYER\n");
        sb.append(String.format("  Head-to-head d_HH             %.1f Å%n", r.headToHead()));
        sb.append(String.format("  Lamellar repeat d             %s%n", pm(p[D], e[D])));
        sb.append(String.format("  Water layer d − d_HH          %.1f Å%n%n", r.waterLayer()));

        sb.append("  FITTED PARAMETERS\n");
        for (int k = 0; k < NPAR; k++) {
            sb.append(String.format("  %-32s %12s %-12s %s%n", NAMES[k], Fields.fmt(p[k]),
                    Double.isNaN(e[k]) ? "" : "± " + Fields.fmt(e[k]), UNITS[k]));
        }

        sb.append("\n  DISTRIBUTION OF LAMELLAE (number-weighted)\n");
        for (int i = 0; i < Math.min(10, N_MAX); i++) {
            if (r.distribution[i] < 0.005 && i > 0) continue;
            int bars = (int) Math.round(r.distribution[i] * 40);
            StringBuilder bar = new StringBuilder();
            for (int b = 0; b < bars; b++) bar.append('#');
            sb.append(String.format("  N = %-3d %5.1f %%  %s%n", i + 1, 100 * r.distribution[i], bar));
        }
        double tail = 0;
        for (int i = 10; i < N_MAX; i++) tail += r.distribution[i];
        if (tail >= 0.005) sb.append(String.format("  N > 10  %5.1f %%%n", 100 * tail));

        sb.append("\n  FOR CONTEXT (Scott et al. 2019, extrusion through 100 nm pores)\n");
        sb.append("  Neutral PC in physiological buffer      f_acc ≈ 0.35\n");
        sb.append("  Eight phosphatidylcholines              f_acc 0.39 – 0.46\n");
        sb.append("  Egg sphingomyelin                       f_acc 0.49\n");
        sb.append("  Charged lipid, or lower ionic strength, raises f_acc; 30–50 nm pores or sonication give\n");
        sb.append("  essentially unilamellar vesicles. A uniform size by light scattering does not: their\n");
        sb.append("  multilamellar and unilamellar vesicles were the same size.\n");

        if (!r.warnings.isEmpty()) {
            sb.append('\n');
            for (String w : r.warnings) sb.append("  ! ").append(w).append('\n');
        }
        return sb.toString();
    }

    private static String pm(double v, double e) {
        return Double.isNaN(e) ? Fields.fmt(v) : Fields.fmt(v) + " ± " + Fields.fmt(e);
    }
}
