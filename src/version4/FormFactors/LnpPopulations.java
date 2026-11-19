package version4.FormFactors;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Morphology populations from SAXS: the measured curve is described as a sum of particle classes, each with its own
 * size distribution, the SAXS counterpart of classifying particles in cryo-EM images (solid core, bleb / bi-phasic,
 * vesicle, ...). SAXS cannot label individual particles; it returns the number fraction of each class that best
 * explains the ensemble curve.
 *
 * <pre>
 *   I(q) = sum_k n_k &lt;|A_k(q)|^2&gt;  +  peak  +  background,     n_k &gt;= 0 (number densities)
 * </pre>
 * All classes use the same density scale (e/A^3 vs buffer), so the n_k are comparable and give number fractions.
 * Classes:
 * <ul>
 *   <li>LOADED: the fitted core/multi-shell particle (from {@link LnpFitter}), with the RNA peak;</li>
 *   <li>EMPTY: the same particle without cargo (core contrast lowered, no RNA peak); SAXS mixes of loaded and empty
 *       LNPs were shown by Gilbert et al., JCIS 660, 66 (2024);</li>
 *   <li>BLEB: a lipid lobe joined to an aqueous compartment bounded by a bilayer ("bleb", "bi-phasic");
 *       I = A1^2 + A2^2 + 2 A1 A2 sin(qD)/(qD) for two touching bodies at centre distance D;</li>
 *   <li>VESICLE: unilamellar vesicle (water core, bilayer: head 8 A, tails 30 A, head 8 A);</li>
 *   <li>MICELLE: small free micelles (e.g. PEG-lipid or surfactant), homogeneous hydrophobic core;</li>
 *   <li>AGGREGATE: large aggregates, Porod law q^-4 (reported as an intensity share, not a number fraction).</li>
 * </ul>
 * Two ways of use: {@link #suggest} adds classes one at a time while the Bayesian information criterion (BIC) drops
 * by more than 10 (strong evidence); {@link #test} fits exactly the classes the user proposes and reports how the
 * proposal compares.
 */
public final class LnpPopulations {

    public enum Kind {
        LOADED("Solid core (loaded)", new java.awt.Color(0x8bc34a)),
        EMPTY("Empty LNP", new java.awt.Color(0x4fc3f7)),
        BLEB("Bleb / bi-phasic", new java.awt.Color(0xf0a030)),
        VESICLE("Vesicle", new java.awt.Color(0xe57373)),
        MICELLE("Free micelles", new java.awt.Color(0xba68c8)),
        AGGREGATE("Aggregates", new java.awt.Color(0x8d6e63)),
        FREE_RNA("Free RNA (not encapsulated)", new java.awt.Color(0x6d4c41));
        public final String label;
        public final java.awt.Color color;
        Kind(String l, java.awt.Color c) { label = l; color = c; }
        @Override public String toString() { return label; }
    }

    /** one class in a fitted mixture */
    public static final class Component {
        public Kind kind;
        /** FREE_RNA: share of all RNA in the sample that is free in solution (needs the cargo per particle) */
        public double freeRnaShare = Double.NaN;
        /** size parameter: core radius (LOADED, EMPTY, BLEB lobe), water radius (VESICLE), micelle radius (A) */
        public double size;
        /** BLEB: aqueous compartment radius / lobe radius */
        public double ratio = Double.NaN;
        public double sigmaRel;
        public double amplitude, amplitudeErr;
        public double numberFraction = Double.NaN, numberFractionErr = Double.NaN;
        public double intensityShare;   // share of the fitted intensity at the lowest q
        public double[] curve;          // per-particle intensity / norm
        /** outer diameter of the mean particle of this class (nm) */
        public double diameter;

        Component copy() {
            Component c = new Component();
            c.kind = kind; c.size = size; c.ratio = ratio; c.sigmaRel = sigmaRel; c.curve = curve;
            return c;
        }
    }

    public static final class Result {
        public final List<Component> components = new ArrayList<>();
        public double chi2, bic, background, peakAmplitude;
        public int points, parameters;
        public double[] fit;
        public final List<String> log = new ArrayList<>();
        /** number-fraction range per class over all solutions that fit about as well as the best (multi-start) */
        public final java.util.EnumMap<Kind, double[]> fractionRange = new java.util.EnumMap<>(Kind.class);
        /** classes that explain the data about equally well in place of an accepted one: {accepted, alternative, delta-BIC} */
        public final List<Object[]> ambiguous = new ArrayList<>();
        /** profile of chi^2 over the solid-core number fraction: {fraction, chi^2} */
        public List<double[]> profile;
        /** the loaded-particle fit the mixture was built on (refined by {@link #alternate}) */
        public LnpFitter.Result loadedFit;
        public double chi2Reduced() { return chi2 / Math.max(1, points - parameters); }
        public Component get(Kind k) { for (Component c : components) if (c.kind == k) return c; return null; }
    }

    private final LnpFitter.Result base;
    private final double[] q, I, s;
    private final double norm;              // common normalisation of per-particle intensities
    private final double emptyCoreShift;    // core contrast change when the cargo is absent (e/A^3)
    private final double[] peakShape;
    /** cargo for the FREE_RNA class: nucleotides per molecule and molecules per loaded particle (NaN = unknown) */
    static double cargoNucleotides = 4284, cargoPerParticle = Double.NaN;

    public static void setCargo(double nucleotidesPerMolecule, double moleculesPerLoadedParticle) {
        cargoNucleotides = nucleotidesPerMolecule > 0 ? nucleotidesPerMolecule : 4284;
        cargoPerParticle = moleculesPerLoadedParticle;
    }

    /** when set (0..1), the number fraction of the loaded class is held at this value (profile scans) */
    double fixLoaded = Double.NaN;

    /**
     * @param base           single-population fit that defines the loaded particle (shells, densities, size)
     * @param emptyCoreShift core contrast of empty particles minus that of loaded ones (negative: no RNA; from
     *                       {@link LnpComposition#coreContrast}, about -0.004 e/A^3 for clinical compositions)
     */
    public LnpPopulations(LnpFitter.Result base, double emptyCoreShift) {
        this(base, emptyCoreShift, base.q, base.I, base.sigma);
    }

    /** loaded-particle shape from {@code base}, fitted against the data (q, I, s) */
    public LnpPopulations(LnpFitter.Result base, double emptyCoreShift, double[] q, double[] I, double[] s) {
        this.base = base;
        this.q = q; this.I = I; this.s = s;
        this.emptyCoreShift = emptyCoreShift;
        double s0 = base.model.norm(base.params);
        this.norm = s0 > 0 ? s0 * s0 : 1;
        this.peakShape = base.model.peak ? LnpModel.peakShape(base.params, q) : null;
    }

    // ------------------------------------------------------------------ per-particle curves

    /** loaded or empty LNP with core radius distribution (mean r0, sigmaRel) */
    double[] lnpCurve(double r0, double sigmaRel, double coreShift) {
        double[] p = base.params.clone();
        p[LnpModel.R0] = r0;
        p[LnpModel.SIGMA] = sigmaRel * r0;
        p[LnpModel.RHO_C] += coreShift;
        double[] sh = base.model.particleShape(p, q);   // <A^2> / S0(p)^2
        double s0 = base.model.norm(p);
        for (int j = 0; j < q.length; j++) sh[j] *= s0 * s0 / norm;
        return sh;
    }

    static final double HEAD = 0.085, TAIL = -0.035, T_HEAD = 8, T_TAIL = 30;

    /** amplitude of a unilamellar vesicle with water radius rw */
    static double vesicleAmp(double q, double rw) {
        double r1 = rw, r2 = rw + T_HEAD, r3 = r2 + T_TAIL, r4 = r3 + T_HEAD;
        return (0 - HEAD) * vol(r1) * LnpModel.sphereAmp(q * r1) + (HEAD - TAIL) * vol(r2) * LnpModel.sphereAmp(q * r2)
                + (TAIL - HEAD) * vol(r3) * LnpModel.sphereAmp(q * r3) + HEAD * vol(r4) * LnpModel.sphereAmp(q * r4);
    }

    static double vol(double r) { return 4.0 / 3.0 * Math.PI * r * r * r; }

    /** amplitude of the fitted LNP particle (core radius rc) */
    double lnpAmp(double qq, double rc, double coreShift) {
        double[] p = base.params.clone();
        p[LnpModel.RHO_C] += coreShift;
        double[] r = base.model.radii(p, rc);
        double[] d = base.model.steps(p);
        double a = 0;
        for (int k = 0; k <= base.model.shells; k++) a += d[k] * vol(r[k]) * LnpModel.sphereAmp(qq * r[k]);
        return a;
    }

    double[] vesicleCurve(double rw, double sigmaRel) {
        double[] out = new double[q.length];
        double[][] nodes = gauss(rw, sigmaRel * rw);
        for (int i = 0; i < nodes[0].length; i++)
            for (int j = 0; j < q.length; j++) {
                double a = vesicleAmp(q[j], nodes[0][i]);
                out[j] += nodes[1][i] * a * a / norm;
            }
        return out;
    }

    /** lobe = LNP of core radius rc, compartment = vesicle of water radius ratio * (outer lobe radius), touching */
    double[] blebCurve(double r0, double sigmaRel, double ratio) {
        double[] out = new double[q.length];
        double shellT = base.model.outerRadius(base.params) - base.params[LnpModel.R0];
        double[][] nodes = gauss(r0, sigmaRel * r0);
        for (int i = 0; i < nodes[0].length; i++) {
            double rc = nodes[0][i], rLobe = rc + shellT;
            double rw = ratio * rLobe, rVes = rw + 2 * T_HEAD + T_TAIL;
            double D = 0.9 * (rLobe + rVes);   // slightly fused at the neck
            for (int j = 0; j < q.length; j++) {
                double a1 = lnpAmp(q[j], rc, emptyCoreShift), a2 = vesicleAmp(q[j], rw);
                double x = q[j] * D, sinc = x < 1e-6 ? 1 : Math.sin(x) / x;
                out[j] += nodes[1][i] * (a1 * a1 + a2 * a2 + 2 * a1 * a2 * sinc) / norm;
            }
        }
        return out;
    }

    double[] micelleCurve(double rm) {
        double[] out = new double[q.length];
        double[][] nodes = gauss(rm, 0.15 * rm);
        for (int i = 0; i < nodes[0].length; i++)
            for (int j = 0; j < q.length; j++) {
                double a = TAIL * vol(nodes[0][i]) * LnpModel.sphereAmp(q[j] * nodes[0][i]);
                out[j] += nodes[1][i] * a * a / norm;
            }
        return out;
    }

    /** free RNA molecules: Gaussian coils (Debye) of radius of gyration rg, contrast of RNA vs buffer */
    double[] freeRnaCurve(double rg) {
        double v = cargoNucleotides * LnpExpectations.NUCLEOTIDE_VOLUME;
        double a0 = 0.18 * v;   // (0.52 - 0.334) e/A^3 times the molecular volume
        double[] out = new double[q.length];
        for (int j = 0; j < q.length; j++) {
            double x = q[j] * q[j] * rg * rg;
            double p = x < 1e-4 ? 1 - x / 3 : 2 * (Math.expm1(-x) + x) / (x * x);
            out[j] = a0 * a0 * p / norm;
        }
        return out;
    }

    double[] aggregateCurve() {
        double[] out = new double[q.length];
        double q0 = q[0];
        for (int j = 0; j < q.length; j++) out[j] = Math.pow(q0 / q[j], 4) * 1e-3;   // amplitude carries the scale
        return out;
    }

    private static double[][] gauss(double m, double sd) {
        int n = 25;
        double[] x = new double[n], w = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double z = -3 + 6.0 * i / (n - 1);
            x[i] = Math.max(1, m + z * sd);
            w[i] = Math.exp(-0.5 * z * z);
            sum += w[i];
        }
        for (int i = 0; i < n; i++) w[i] /= sum;
        return new double[][]{x, w};
    }

    // ------------------------------------------------------------------ class candidates with a size scan

    /** best curve of a class over its size grid, given the other columns; returns the component and its chi^2 */
    private Component bestOf(Kind k, List<Component> others) {
        double r0 = base.params[LnpModel.R0], sr = Math.max(0.05, base.params[LnpModel.SIGMA] / r0);
        List<double[]> grid = new ArrayList<>();
        switch (k) {
            case EMPTY: for (double f = 0.6; f <= 1.41; f += 0.1) grid.add(new double[]{f * r0}); break;
            case BLEB: for (double ratio = 0.4; ratio <= 1.21; ratio += 0.2) grid.add(new double[]{r0, ratio}); break;
            case VESICLE: for (double rw = 80; rw <= 1200; rw *= 1.25) grid.add(new double[]{rw}); break;
            case MICELLE: for (double rm = 15; rm <= 60; rm *= 1.25) grid.add(new double[]{rm}); break;
            case FREE_RNA: for (double rg = 15; rg <= 400; rg *= 1.35) grid.add(new double[]{rg}); break;
            default: grid.add(new double[]{0});
        }
        Component best = null;
        double bestChi = Double.POSITIVE_INFINITY;
        for (double[] g : grid) {
            Component c = new Component();
            c.kind = k;
            c.size = g[0];
            switch (k) {
                case EMPTY: c.sigmaRel = sr; c.curve = lnpCurve(g[0], sr, emptyCoreShift); break;
                case BLEB: c.sigmaRel = sr; c.ratio = g[1]; c.curve = blebCurve(g[0], sr, g[1]); break;
                case VESICLE: c.sigmaRel = 0.2; c.curve = vesicleCurve(g[0], 0.2); break;
                case MICELLE: c.sigmaRel = 0.15; c.curve = micelleCurve(g[0]); break;
                case FREE_RNA: c.sigmaRel = 0; c.curve = freeRnaCurve(g[0]); break;
                default: c.curve = aggregateCurve();
            }
            // a candidate must be judged with the loaded particle re-sized for it: the single-population fit has
            // usually bent the loaded particle's size and spread to absorb what the candidate describes
            boolean hasLoaded = !others.isEmpty() && others.get(0).kind == Kind.LOADED;
            for (double lf : hasLoaded ? LOAD_F : new double[]{1})
                for (double lg : hasLoaded ? LOAD_G : new double[]{1}) {
                    List<Component> all = new ArrayList<>(others);
                    if (hasLoaded) all.set(0, loadedCached(lf, lg));
                    all.add(c);
                    Result r = solve(all);
                    if (r.chi2 < bestChi) { bestChi = r.chi2; best = c; }
                }
        }
        return best;
    }

    private final java.util.Map<String, Component> loadedCache = new java.util.HashMap<>();
    static final double[] LOAD_F = {0.85, 0.92, 0.97, 1.0, 1.03, 1.08, 1.15}, LOAD_G = {0.5, 0.75, 1.0, 1.3, 1.6};

    private Component loadedCached(double f, double g) {
        return loadedCache.computeIfAbsent(f + "|" + g, k -> loaded(f, g)).copy();
    }

    private Component loaded(double r0Factor, double sigFactor) {
        Component c = new Component();
        c.kind = Kind.LOADED;
        double sr = Math.max(0.03, sigFactor * base.params[LnpModel.SIGMA] / base.params[LnpModel.R0]);
        c.size = r0Factor * base.params[LnpModel.R0];
        c.sigmaRel = sr;
        c.curve = lnpCurve(c.size, sr, 0);
        return c;
    }

    // ------------------------------------------------------------------ linear solve (exact NNLS for few columns)

    Result solve(List<Component> comps) {
        int m = comps.size(), extra = (peakShape != null ? 1 : 0) + 1;
        int nc = m + extra, n = q.length;
        double[][] cols = new double[nc][];
        for (int k = 0; k < m; k++) cols[k] = comps.get(k).curve;
        if (peakShape != null) cols[m] = peakShape;
        double[] ones = new double[n];
        java.util.Arrays.fill(ones, 1);
        cols[nc - 1] = ones;
        double[] best = null;
        double bestChi = Double.POSITIVE_INFINITY;
        double[][] bestCov = null;
        int bestMask = 0;
        for (int mask = 1; mask < (1 << nc); mask++) {
            int na = Integer.bitCount(mask);
            int[] idx = new int[na];
            for (int k = 0, t = 0; k < nc; k++) if ((mask >> k & 1) == 1) idx[t++] = k;
            double[][] a = new double[na][na];
            double[] b = new double[na];
            for (int j = 0; j < n; j++) {
                double w = 1 / (s[j] * s[j]);
                for (int r = 0; r < na; r++) {
                    b[r] += w * cols[idx[r]][j] * I[j];
                    for (int c = 0; c < na; c++) a[r][c] += w * cols[idx[r]][j] * cols[idx[c]][j];
                }
            }
            if (!Double.isNaN(fixLoaded)) {
                // stiff pseudo-observation  n_L (1 - f) - f * sum(other number classes) = 0
                double[] cc = new double[na];
                boolean hasLoaded = false;
                for (int r = 0; r < na; r++) {
                    int k = idx[r];
                    if (k >= m) continue;
                    Kind kk = comps.get(k).kind;
                    if (kk == Kind.LOADED) { cc[r] = 1 - fixLoaded; hasLoaded = true; }
                    else if (kk != Kind.AGGREGATE) cc[r] = -fixLoaded;
                }
                if (!hasLoaded && fixLoaded > 0) continue;
                double tr = 0;
                for (int r = 0; r < na; r++) tr += a[r][r];
                double W = 1e8 * tr / na;
                for (int r = 0; r < na; r++) for (int c = 0; c < na; c++) a[r][c] += W * cc[r] * cc[c];
            }
            double[] x;
            double[][] inv;
            try {
                org.apache.commons.math3.linear.RealMatrix A = new org.apache.commons.math3.linear.Array2DRowRealMatrix(a);
                org.apache.commons.math3.linear.DecompositionSolver sol = new org.apache.commons.math3.linear.LUDecomposition(A).getSolver();
                x = sol.solve(new org.apache.commons.math3.linear.ArrayRealVector(b)).toArray();
                inv = sol.getInverse().getData();
            } catch (RuntimeException e) {
                continue;
            }
            boolean feasible = true;
            for (double v : x) if (v < 0) { feasible = false; break; }
            if (!feasible) continue;
            double chi = 0;
            for (int j = 0; j < n; j++) {
                double f = 0;
                for (int r = 0; r < na; r++) f += x[r] * cols[idx[r]][j];
                double rr = (I[j] - f) / s[j];
                chi += rr * rr;
            }
            if (chi < bestChi) {
                bestChi = chi;
                best = new double[nc];
                for (int r = 0; r < na; r++) best[idx[r]] = x[r];
                bestCov = new double[nc][nc];
                for (int r = 0; r < na; r++) for (int c = 0; c < na; c++) bestCov[idx[r]][idx[c]] = inv[r][c];
                bestMask = mask;
            }
        }
        Result res = new Result();
        res.points = n;
        res.chi2 = bestChi;
        if (best == null) { res.chi2 = Double.POSITIVE_INFINITY; return res; }
        double red = Math.max(1, bestChi / Math.max(1, n - nc));
        res.fit = new double[n];
        for (int k = 0; k < nc; k++) for (int j = 0; j < n; j++) res.fit[j] += best[k] * cols[k][j];
        for (int k = 0; k < m; k++) {
            Component c = comps.get(k).copy();   // results never write into the shared candidate objects
            c.amplitude = best[k];
            c.amplitudeErr = Math.sqrt(Math.max(0, bestCov[k][k]) * red);
            res.components.add(c);
        }
        res.peakAmplitude = peakShape != null ? best[m] : 0;
        res.background = best[nc - 1];
        // number fractions (aggregates excluded) with first-order errors
        double N = 0;
        for (Component c : res.components) if (c.kind != Kind.AGGREGATE && c.kind != Kind.FREE_RNA) N += c.amplitude;
        double fitLow = res.fit[0];
        for (int k = 0; k < m; k++) {
            Component c = res.components.get(k);
            c.intensityShare = c.amplitude * c.curve[0] / Math.max(fitLow, 1e-300);
            if (c.kind == Kind.FREE_RNA) {
                Component load = null;
                for (Component o : res.components) if (o.kind == Kind.LOADED) load = o;
                if (load != null && cargoPerParticle > 0) {
                    double enc = load.amplitude * cargoPerParticle, free = c.amplitude;
                    c.freeRnaShare = free / Math.max(1e-300, free + enc);
                }
                continue;
            }
            if (c.kind == Kind.AGGREGATE || N <= 0) continue;
            c.numberFraction = c.amplitude / N;
            double var = 0;
            for (int j = 0; j < m; j++) {
                if (res.components.get(j).kind == Kind.AGGREGATE || res.components.get(j).kind == Kind.FREE_RNA) continue;
                double dj = ((j == k ? N : 0) - c.amplitude) / (N * N);
                for (int l = 0; l < m; l++) {
                    if (res.components.get(l).kind == Kind.AGGREGATE || res.components.get(l).kind == Kind.FREE_RNA) continue;
                    double dl = ((l == k ? N : 0) - c.amplitude) / (N * N);
                    var += dj * dl * bestCov[j][l] * red;
                }
            }
            c.numberFractionErr = Math.sqrt(Math.max(0, var));
        }
        int sizeParams = 0;
        for (Component c : res.components) if (c.kind != Kind.LOADED && c.kind != Kind.AGGREGATE) sizeParams++;
        res.parameters = Integer.bitCount(bestMask) + sizeParams;
        res.bic = bestChi + res.parameters * Math.log(n);
        for (Component c : res.components) c.diameter = diameter(c);
        return res;
    }

    double diameter(Component c) {
        double shellT = base.model.outerRadius(base.params) - base.params[LnpModel.R0];
        switch (c.kind) {
            case LOADED: case EMPTY: return 2 * (c.size + shellT) / 10;
            case BLEB: {
                double rLobe = c.size + shellT, rVes = c.ratio * rLobe + 2 * T_HEAD + T_TAIL;
                return (0.9 * (rLobe + rVes) + rLobe + rVes) / 10;   // longest dimension
            }
            case VESICLE: return 2 * (c.size + 2 * T_HEAD + T_TAIL) / 10;
            case MICELLE: return 2 * c.size / 10 + 2 * 3.4;      // plus PEG corona
            case FREE_RNA: return 2 * c.size / 10;                // ~ 2 Rg
            default: return Double.NaN;
        }
    }

    // ------------------------------------------------------------------ the two modes

    /** the single-population description: loaded particles only (with the RNA peak) */
    public Result baseline() {
        List<Component> cs = new ArrayList<>();
        cs.add(loaded(1, 1));
        Result r = refineLoaded(cs);
        r.log.add(String.format("Single population: χ² = %.1f, BIC = %.1f", r.chi2, r.bic));
        return r;
    }

    /**
     * Data decide: forward selection by BIC. Every candidate class is scored after a short joint refinement (class
     * sizes and loaded-particle structure together), so a class is not rejected merely because the loaded particle
     * was first fitted to the whole mixture. Each accepted class must lower BIC by more than 10.
     */
    public Result suggest(Set<Kind> candidates) {
        Result cur = joint(withLoaded(baseline(), base), emptyCoreShift, q, I, s, 6);
        cur.log.add(0, String.format("Single population: χ² = %.1f, BIC = %.1f", cur.chi2, cur.bic));
        Set<Kind> left = EnumSet.copyOf(candidates);
        left.remove(Kind.LOADED);
        while (!left.isEmpty()) {
            Kind bestK = null;
            Result bestR = null;
            LnpPopulations here = new LnpPopulations(cur.loadedFit, emptyCoreShift, q, I, s);
            for (Kind k : left) {
                List<Component> others = new ArrayList<>(cur.components);
                Component c = here.bestOf(k, others);
                if (c == null) continue;
                others.add(c);
                Result r = withLoaded(here.refineLoaded(others), cur.loadedFit);
                if (r.bic < cur.bic + 40) r = joint(r, emptyCoreShift, q, I, s, 8);   // promising: refine before judging
                cur.log.add(String.format("  try + %s: χ² = %.1f, ΔBIC = %+.1f", k.label, r.chi2, r.bic - cur.bic));
                if (bestR == null || r.bic < bestR.bic) { bestR = r; bestK = k; }
            }
            if (bestR == null || bestR.bic > cur.bic - 10) {
                cur.log.add("  no further class lowers BIC by more than 10: stop");
                break;
            }
            Component added = bestR.get(bestK);
            if (added == null || !(added.amplitude > 2 * added.amplitudeErr)) {
                cur.log.add(String.format("  %s improves the fit but its amount is not significant: stop", bestK.label));
                break;
            }
            List<String> log = new ArrayList<>(cur.log);
            log.add(String.format("Accepted %s (ΔBIC = %+.1f)", bestK.label, bestR.bic - cur.bic));
            bestR.log.clear();
            bestR.log.addAll(log);
            cur = bestR;
            left.remove(bestK);
        }
        return cur;
    }

    /** user's proposal: exactly these classes (LOADED always included), each at its best size */
    public Result test(Set<Kind> proposal) {
        List<Component> cs = new ArrayList<>();
        cs.add(loaded(1, 1));
        for (Kind k : proposal) {
            if (k == Kind.LOADED) continue;
            Component c = bestOf(k, cs);
            if (c != null) cs.add(c);
        }
        // one more pass so that each class's size is optimised in the presence of the others
        for (int i = 1; i < cs.size(); i++) {
            List<Component> others = new ArrayList<>(cs);
            Component old = others.remove(i);
            Component c = bestOf(old.kind, others);
            if (c != null) cs.set(i, c);
        }
        Result r = refineLoaded(cs);
        return r.components.size() > 1 ? polish(r.components) : r;
    }

    /** rebuilds a component's curve after a size change */
    private Component rebuild(Component c, double size, double second) {
        Component n = c.copy();
        switch (c.kind) {
            case LOADED: n.size = size; n.sigmaRel = second; n.curve = lnpCurve(size, second, 0); break;
            case EMPTY: n.size = size; n.curve = lnpCurve(size, n.sigmaRel, emptyCoreShift); break;
            case BLEB: n.ratio = second; n.curve = blebCurve(n.size, n.sigmaRel, second); break;
            case VESICLE: n.size = size; n.curve = vesicleCurve(size, n.sigmaRel); break;
            case MICELLE: n.size = size; n.curve = micelleCurve(size); break;
            case FREE_RNA: n.size = size; n.curve = freeRnaCurve(size); break;
            default: break;
        }
        return n;
    }

    /**
     * Continuous refinement of every class's size (golden-section search, two sweeps), after the coarse grids: the
     * number fractions depend on getting each class's form factor right.
     */
    Result polish(List<Component> start) {
        List<Component> cs = new ArrayList<>(start);
        for (int sweep = 0; sweep < 2; sweep++) {
            for (int i = 0; i < cs.size(); i++) {
                final int idx = i;
                Component c = cs.get(i);
                // parameters of this class: {value, lower, upper, which (0 = size, 1 = second)}
                List<double[]> pars = new ArrayList<>();
                switch (c.kind) {
                    case LOADED: pars.add(new double[]{c.size, 0.85 * c.size, 1.15 * c.size, 0}); pars.add(new double[]{c.sigmaRel, 0.03, 0.35, 1}); break;
                    case EMPTY: case VESICLE: case MICELLE: case FREE_RNA: pars.add(new double[]{c.size, 0.75 * c.size, 1.3 * c.size, 0}); break;
                    case BLEB: pars.add(new double[]{c.ratio, 0.25, 1.6, 1}); break;
                    default: break;
                }
                for (double[] pr : pars) {
                    final boolean second = pr[3] == 1;
                    java.util.function.DoubleUnaryOperator chi = v -> {
                        Component cur = cs.get(idx);
                        Component t = rebuild(cur, second ? cur.size : v, second ? v : (cur.kind == Kind.LOADED ? cur.sigmaRel : cur.ratio));
                        List<Component> trial = new ArrayList<>(cs);
                        trial.set(idx, t);
                        return solve(trial).chi2;
                    };
                    double a = pr[1], b = pr[2], g = (Math.sqrt(5) - 1) / 2;
                    double x1 = b - g * (b - a), x2 = a + g * (b - a), f1 = chi.applyAsDouble(x1), f2 = chi.applyAsDouble(x2);
                    for (int it = 0; it < 14; it++) {
                        if (f1 < f2) { b = x2; x2 = x1; f2 = f1; x1 = b - g * (b - a); f1 = chi.applyAsDouble(x1); }
                        else { a = x1; x1 = x2; f1 = f2; x2 = a + g * (b - a); f2 = chi.applyAsDouble(x2); }
                    }
                    double v = f1 < f2 ? x1 : x2, fNew = Math.min(f1, f2), fOld = solve(cs).chi2;
                    if (fNew < fOld) {
                        Component cur = cs.get(idx);
                        cs.set(idx, rebuild(cur, second ? cur.size : v, second ? v : (cur.kind == Kind.LOADED ? cur.sigmaRel : cur.ratio)));
                    }
                }
            }
        }
        return solve(cs);
    }

    /** small grid over the loaded particle's size and spread, other classes fixed */
    private Result refineLoaded(List<Component> cs) {
        Result best = null;
        for (double f : LOAD_F)
            for (double g : LOAD_G) {
                List<Component> t = new ArrayList<>(cs);
                t.set(0, loadedCached(f, g));
                Result r = solve(t);
                if (best == null || r.chi2 < best.chi2) best = r;
            }
        return best;
    }

    /**
     * Alternating refinement: the loaded particle's internal structure (shells, densities, peak) was first fitted to
     * the whole mixture and so absorbed part of the other classes. Each cycle refits it to the data minus the other
     * classes, then re-solves the mixture with the same classes. Stops when chi^2 no longer improves.
     * @param suggest true: the data choose the classes first ({@link #suggest}); false: use {@code kinds} as proposed
     */
    public static Result alternate(LnpFitter.Result base, double emptyCoreShift, Set<Kind> kinds, boolean suggest, int cycles) {
        double[] q = base.q, I = base.I, s = base.sigma;
        LnpPopulations pop = new LnpPopulations(base, emptyCoreShift, q, I, s);
        Result r = suggest ? pop.suggest(kinds) : withLoaded(pop.test(kinds), base);
        if (r.loadedFit == null) r.loadedFit = base;
        if (r.components.size() < 2) return r;
        EnumSet<Kind> chosen = EnumSet.noneOf(Kind.class);
        for (Component c : r.components) chosen.add(c.kind);
        r = joint(r, emptyCoreShift, q, I, s, Math.max(10, 10 * cycles));
        LnpFitter.Result cur = r.loadedFit;
        r = multiStart(r, emptyCoreShift, q, I, s);
        // small scatterers look alike: free RNA coils vs free micelles. Swap and compare.
        if (suggest) for (Kind[] pair : new Kind[][]{{Kind.MICELLE, Kind.FREE_RNA}, {Kind.FREE_RNA, Kind.MICELLE}}) {
            if (r.get(pair[0]) == null || r.get(pair[1]) != null || !kinds.contains(pair[1])) continue;
            EnumSet<Kind> swap = EnumSet.noneOf(Kind.class);
            for (Component c : r.components) swap.add(c.kind == pair[0] ? pair[1] : c.kind);
            Result alt = new LnpPopulations(r.loadedFit, emptyCoreShift, q, I, s).test(swap);
            double d = alt.bic - r.bic;
            r.log.add(String.format("Swap test: %s instead of %s gives χ² %.1f, ΔBIC %+.1f", pair[1].label, pair[0].label, alt.chi2, d));
            Component rna = pair[1] == Kind.FREE_RNA ? alt.get(Kind.FREE_RNA) : r.get(Kind.FREE_RNA);
            double share = rna == null ? Double.NaN : rna.freeRnaShare;
            if (d < 40) r.ambiguous.add(new Object[]{pair[0], pair[1], d, share});
        }
        // drop classes whose amount is not significant any more, then re-solve
        EnumSet<Kind> keep = EnumSet.of(Kind.LOADED);
        for (Component c : r.components) if (c.kind != Kind.LOADED && c.amplitude > 2 * c.amplitudeErr) keep.add(c.kind);
        if (keep.size() < chosen.size()) {
            LnpPopulations p3 = new LnpPopulations(cur, emptyCoreShift, q, I, s);
            Result r3 = joint(withLoaded(p3.test(keep), cur), emptyCoreShift, q, I, s, 30);
            r3.log.addAll(r.log);
            r3.log.add("Dropped classes that were no longer significant");
            r = r3;
        }
        return r;
    }

    /**
     * Joint refinement by variable projection: the nonlinear parameters (free parameters of the loaded particle and
     * the size of every other class) are refined by Levenberg-Marquardt while the class amounts, the peak height and
     * the background are solved exactly (non-negative) at every step. This removes the bias of building the mixture
     * on a loaded-particle shape that was fitted to the whole mixture.
     */
    public static Result joint(Result start, double emptyCoreShift, double[] q, double[] I, double[] s, int maxIter) {
        return joint(start, emptyCoreShift, q, I, s, maxIter, Double.NaN);
    }

    static Result joint(Result start, double emptyCoreShift, double[] q, double[] I, double[] s, int maxIter, double fixLoaded) {
        final LnpFitter.Result lf = start.loadedFit;
        final LnpModel model = lf.model;
        final boolean[] fixed = lf.fixed;
        final List<Integer> lp = new ArrayList<>();   // loaded-model parameters refined here (linear ones excluded)
        for (int k = 0; k < LnpModel.NPAR; k++)
            if (model.uses(k) && !fixed[k] && k != LnpModel.SCALE && k != LnpModel.PEAK_A && k != LnpModel.BG) lp.add(k);
        final List<Component> others = new ArrayList<>();
        for (Component c : start.components) if (c.kind != Kind.LOADED) others.add(c);
        final int nl = lp.size(), npar = nl + others.size();
        double[] p0 = lf.params.clone();
        Component load = start.get(Kind.LOADED);
        if (load != null) { p0[LnpModel.R0] = load.size; p0[LnpModel.SIGMA] = load.sigmaRel * load.size; }
        final double[] base = p0;
        double[] u0 = new double[npar];
        for (int i = 0; i < nl; i++) u0[i] = LnpModel.POSITIVE[lp.get(i)] ? Math.log(base[lp.get(i)]) : base[lp.get(i)];
        for (int i = 0; i < others.size(); i++) {
            Component c = others.get(i);
            u0[nl + i] = Math.log(c.kind == Kind.BLEB ? c.ratio : Math.max(c.size, 1e-3));
        }
        final double[] dummyS = new double[q.length];
        java.util.Arrays.fill(dummyS, 1);
        final double[] zero = new double[q.length];
        java.util.function.Function<double[], Result> evaluate = u -> {
            double[] p = base.clone();
            for (int i = 0; i < nl; i++) { int k = lp.get(i); p[k] = LnpModel.POSITIVE[k] ? Math.exp(u[i]) : u[i]; }
            // keep the loaded particle physical
            p[LnpModel.SIGMA] = Math.min(p[LnpModel.SIGMA], 0.5 * p[LnpModel.R0]);
            for (int k = 0; k < LnpModel.NPAR; k++) {
                int sg = model.densitySign(k);
                if (sg > 0 && p[k] < 0) p[k] = 0;
                if (sg < 0 && p[k] > 0) p[k] = 0;
            }
            LnpFitter.Result ev = new LnpFitter(model, q, zero, dummyS).evaluate(p, fixed);
            LnpPopulations pop = new LnpPopulations(ev, emptyCoreShift, q, I, s);
            pop.fixLoaded = fixLoaded;
            List<Component> cs = new ArrayList<>();
            cs.add(pop.loaded(1, 1));
            for (int i = 0; i < others.size(); i++) {
                Component c = others.get(i);
                double v = Math.exp(u[nl + i]);
                switch (c.kind) {
                    case BLEB: v = Math.max(0.2, Math.min(2.0, v)); cs.add(pop.rebuild(c, c.size, v)); break;
                    case AGGREGATE: cs.add(pop.rebuild(c, c.size, c.ratio)); break;
                    default: cs.add(pop.rebuild(c, v, c.sigmaRel)); break;
                }
            }
            if (cs.get(0).curve == null) return null;
            Result r = pop.solve(cs);
            r.loadedFit = ev;
            return r;
        };
        final Result[] best = {start};
        org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction fn = point -> {
            double[] u = point.toArray();
            Result r0 = evaluate.apply(u);
            double[] f0 = weighted(r0, I, s);
            if (r0 != null && r0.chi2 < best[0].chi2) best[0] = r0;
            double[][] jac = new double[q.length][npar];
            for (int i = 0; i < npar; i++) {
                double h = 1e-4 * Math.max(1, Math.abs(u[i]));
                double[] up = u.clone();
                up[i] += h;
                double[] f1 = weighted(evaluate.apply(up), I, s);
                for (int j = 0; j < q.length; j++) jac[j][i] = (f1[j] - f0[j]) / h;
            }
            return new org.apache.commons.math3.util.Pair<>(new org.apache.commons.math3.linear.ArrayRealVector(f0, false),
                    new org.apache.commons.math3.linear.Array2DRowRealMatrix(jac, false));
        };
        double[] target = new double[q.length];
        for (int j = 0; j < q.length; j++) target[j] = I[j] / s[j];
        try {
            new org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer()
                    .withCostRelativeTolerance(1e-6).withParameterRelativeTolerance(1e-6)
                    .optimize(new org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder()
                            .model(fn).target(target).start(u0).maxEvaluations(maxIter * 2).maxIterations(maxIter).build());
        } catch (RuntimeException stopped) {
            // iteration limit: keep the best point seen
        }
        Result r = best[0];
        if (r != start) {
            r.log.clear();
            r.log.addAll(start.log);
            if (start.chi2 - r.chi2 > 1)
                r.log.add(String.format("Joint refinement (classes and loaded-particle structure together): χ² %.1f → %.1f", start.chi2, r.chi2));
        }
        return r;
    }

    private static Result withLoaded(Result r, LnpFitter.Result lf) { r.loadedFit = lf; return r; }

    /**
     * Profile over the number fraction of the loaded (solid-core) class: the fraction is held at 0.2 ... 0.9 and
     * everything else re-optimised (class amounts, sizes, loaded-particle structure). The lowest point becomes the
     * answer (this also acts as a global search), and all profile points within delta-chi^2 = max(9 max(1, reduced
     * chi^2), 3 % of chi^2) (statistics or model error, whichever is larger) give the plausible range of every class's fraction. Fractions of similar-looking classes trade off against each
     * other far more than the shape-fixed error bars suggest.
     */
    static Result multiStart(Result r, double shift, double[] q, double[] I, double[] s) {
        if (r.components.size() < 2 || r.loadedFit == null || r.get(Kind.LOADED) == null) return r;
        List<Result> ends = new ArrayList<>();
        ends.add(r);
        List<double[]> profile = new ArrayList<>();
        Component l0 = r.get(Kind.LOADED);
        profile.add(new double[]{l0.numberFraction, r.chi2});
        for (double f = 0.2; f <= 0.91; f += 0.1) {
            LnpPopulations pop = new LnpPopulations(r.loadedFit, shift, q, I, s);
            pop.fixLoaded = f;
            List<Component> cs = new ArrayList<>();
            for (Component c : r.components) cs.add(c.copy());
            Result st = withLoaded(pop.solve(cs), r.loadedFit);
            if (st.fit == null) continue;
            st.log.addAll(r.log);
            Result e = joint(st, shift, q, I, s, 8, f);
            // release the constraint and re-solve the amounts at the profile point's shapes
            LnpPopulations free = new LnpPopulations(e.loadedFit, shift, q, I, s);
            List<Component> cs2 = new ArrayList<>();
            for (Component c : e.components) cs2.add(c.copy());
            Result rel = withLoaded(free.solve(cs2), e.loadedFit);
            rel.log.addAll(r.log);
            profile.add(new double[]{f, e.chi2});
            ends.add(rel);
        }
        Result best = r;
        for (Result e : ends) if (e.fit != null && e.chi2 < best.chi2) best = e;
        if (best != r) best = joint(best, shift, q, I, s, 15);
        // statistical (3 sigma, scaled) or model-error allowance (3 % of chi^2), whichever is larger
        double tol = Math.max(9 * Math.max(1, best.chi2Reduced()), 0.03 * best.chi2);
        Component lb = best.get(Kind.LOADED);
        double[] lr = {lb.numberFraction, lb.numberFraction};
        for (double[] pf : profile) if (pf[1] <= best.chi2 + tol) { lr[0] = Math.min(lr[0], pf[0]); lr[1] = Math.max(lr[1], pf[0]); }
        best.fractionRange.put(Kind.LOADED, lr);
        for (Result e : ends) {
            if (e.fit == null || e.chi2 > best.chi2 + tol) continue;
            for (Component c : e.components) {
                if (Double.isNaN(c.numberFraction) || c.kind == Kind.LOADED) continue;
                double[] rg = best.fractionRange.computeIfAbsent(c.kind, k -> new double[]{c.numberFraction, c.numberFraction});
                rg[0] = Math.min(rg[0], c.numberFraction);
                rg[1] = Math.max(rg[1], c.numberFraction);
            }
        }
        StringBuilder pl = new StringBuilder("Profile of the solid-core fraction (fraction: χ²):");
        profile.sort((x, y) -> Double.compare(x[0], y[0]));
        for (double[] pf : profile) pl.append(String.format(" %.0f%%: %.0f;", 100 * pf[0], pf[1]));
        best.log.add(pl.toString());
        best.log.add(String.format("Plausible range: profile points within Δχ² ≤ %.0f of the best", tol));
        best.profile = profile;
        return best;
    }

    private static double[] weighted(Result r, double[] I, double[] s) {
        double[] f = new double[I.length];
        for (int j = 0; j < I.length; j++) f[j] = (r == null || r.fit == null ? 0 : r.fit[j]) / s[j];
        return f;
    }

    // ------------------------------------------------------------------ text

    public static String describe(Result r, String heading, Result reference, String referenceName) {
        StringBuilder sb = new StringBuilder(heading).append('\n');
        for (Object[] a : r.ambiguous) {
            double d = (Double) a[2], share = (Double) a[3];
            sb.append(String.format("  ! A small scatterer is present. It fits best as %s, but %s is %s (ΔBIC %+.1f):%n",
                    ((Kind) a[0]).label, ((Kind) a[1]).label, d < 10 ? "equally likely" : "a weaker but possible explanation", d));
            if (!Double.isNaN(share))
                sb.append(String.format("    if it is unencapsulated RNA, about %.0f %% of all RNA would be free (likely an underestimate:%n"
                        + "    the particle model absorbs part of the coil signal).%n", 100 * share));
            sb.append("    Small coils and small micelles scatter alike here. Check with a RiboGreen assay (± detergent) or SEC.\n");
        }
        sb.append(String.format("χ² = %.1f (reduced %.2f), BIC = %.1f", r.chi2, r.chi2Reduced(), r.bic));
        if (reference != null) sb.append(String.format(", ΔBIC vs %s = %+.1f", referenceName, r.bic - reference.bic));
        sb.append('\n');
        sb.append(String.format("  %-22s %16s %16s %14s %12s%n", "class", "number fraction", "plausible range", "diameter (nm)", "I share @qmin"));
        for (Component c : r.components) {
            String nf = Double.isNaN(c.numberFraction) ? "–" : String.format("%5.1f ± %4.1f %%", 100 * c.numberFraction, 100 * c.numberFractionErr);
            double[] rg = r.fractionRange.get(c.kind);
            String range = rg == null || rg[0] > rg[1] ? "–" : String.format("%.0f–%.0f %%", 100 * Math.min(rg[0], c.numberFraction), 100 * Math.max(rg[1], c.numberFraction));
            String d = Double.isNaN(c.diameter) ? "–" : String.format("%.0f", c.diameter);
            String extra = c.kind == Kind.BLEB ? String.format(" (compartment/lobe %.1f)", c.ratio)
                    : c.kind == Kind.FREE_RNA ? (Double.isNaN(c.freeRnaShare) ? String.format(" (Rg %.0f Å)", c.size)
                    : String.format(" (Rg %.0f Å; ≈ %.0f %% of all RNA is free)", c.size, 100 * c.freeRnaShare)) : "";
            sb.append(String.format("  %-22s %16s %16s %14s %11.0f %%%s%n", c.kind.label, nf, range, d, 100 * c.intensityShare, extra));
        }
        return sb.toString();
    }

    /** what SAXS can and cannot separate */
    public static String caveats() {
        return "How far to trust this:\n"
                + "  • SAXS fits the ensemble average; it cannot label individual particles as cryo-EM does.\n"
                + "  • Loaded vs empty LNPs differ mainly in the RNA peak and a small core-density change, so their\n"
                + "    split carries large errors unless an empty reference is measured (Gilbert et al. 2024).\n"
                + "  • Small blebs look almost like solid-core particles; vesicles and large blebs are well separated\n"
                + "    by their bilayer minima.\n"
                + "  • Classes are only added when BIC drops by more than 10 (strong evidence). A class that is not\n"
                + "    added may still be present below the detection limit.\n"
                + "  • Free RNA is detected by its coil-like scattering; its share of all RNA uses the expected cargo per\n"
                + "    particle, so it is an estimate. Confirm encapsulation with a RiboGreen-type assay.\n"
                + "  • Number fractions of classes with similar curves trade off against each other. The '±' is the\n"
                + "    error with the class shapes fixed; the 'plausible range' spans all restarts that fit equally well\n"
                + "    and is the more realistic uncertainty. Large particles dominate the intensity, so small numbers of\n"
                + "    vesicles or blebs are detected easily, but their exact share is less certain.\n";
    }
}
