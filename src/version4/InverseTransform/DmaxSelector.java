package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Automated choice of qmax and dmax from several indirect Fourier transforms of the same data.
 *
 * dmax: each method m gives a score S_m(D) on the -2 ln(likelihood) scale at every trial dmax D
 * (-2 ln evidence where the method has a quadratic prior, AIC_eff otherwise; one cHat for the whole
 * scan so the values are comparable across D). exp(-S/2) is a relative likelihood (an Akaike weight
 * for AIC), so each method gives a probability over the grid
 *     p_m(D) = exp(-(S_m(D) - min S_m)/2) / sum_D' exp(-(S_m(D') - min S_m)/2).
 * The methods share the same data, so they are NOT multiplied (that would count the data several
 * times and overstate the confidence); they are averaged:
 *     p(D) = sum_m pi_m p_m(D),     pi_m equal over the methods that pass the oscillation gate.
 * Agreeing methods give a sharp p(D); disagreeing ones a broad or multi-modal p(D). The consensus
 * score -2 ln(p(D)/max p) is on the same scale as a Delta AIC.
 *
 * qmax: data are added one Shannon channel (pi/dmax) at a time. While the extra channels carry
 * structural information, the methods' P(r) stay smooth; once they mostly carry noise or systematic
 * error (buffer mismatch), the lightly regularized methods start encoding it as ripples of period
 * about 2 pi/q. qmax is the last channel before the median oscillation score of the methods exceeds
 * QMAX_OSC_THRESHOLD for QMAX_PERSISTENCE consecutive channels. qmax and dmax depend on each other,
 * so select() alternates the two until dmax stops moving.
 */
public class DmaxSelector {

    /** one transform method: fit at (dmax, qmax), choosing its own regularization */
    public interface Method {
        IndirectFT fit(XYSeries data, XYSeries errors, double dmax, double qmax, double cHat);
    }

    /** median oscillation above which a channel counts as degrading the transform */
    public static double QMAX_OSC_THRESHOLD = 0.05;
    /** consecutive degrading channels required before qmax is cut */
    public static int QMAX_PERSISTENCE = 2;
    /** smallest qmax considered, in Shannon channels (pi/dmax) */
    public static int QMAX_MIN_CHANNELS = 6;
    /** method excluded from the dmax consensus when its median oscillation over the scan exceeds this */
    public static double METHOD_OSC_GATE = 0.25;
    /**
     * methods scored even when they oscillate. Moore-L2 at a low fixed lambda rings by construction
     * (a truncated sine series), at every dmax, so its oscillation score says nothing about dmax; its
     * likelihood and positivity scores are still valid, and its P(r) turns negative sharply once
     * dmax is too large, which makes it a sensitive member of the consensus.
     */
    public static java.util.Set<String> OSC_GATE_EXEMPT = new java.util.HashSet<>(java.util.Collections.singletonList("Moore-L2"));
    /** fixed (low) regularization for Moore-L2, in normalized units (NORMALIZE_LAMBDA = true) */
    public static double MOORE_LAMBDA = 1e-3;
    /**
     * "lowest adequate" dmax = the smallest trial dmax whose consensus score -2 ln(p/max p) is within
     * this of the best (2 = the usual Delta AIC for substantial support). Scores typically rise steeply
     * below the true dmax and only gently above it, so this picks the onset of the plateau.
     */
    public static double ADEQUATE_DELTA = 2.0;
    /**
     * the SUGGESTED dmax is the largest trial dmax in the contiguous band around the best whose
     * consensus score is within this of the best (4 = a 95% band for one parameter). With the
     * positivity term on, the band ends where P(r) starts to go negative, so this is the largest dmax
     * the data support with a non-negative P(r) - the usual GNOM practice, and unlike the lowest
     * adequate value it is not biased low when the tail of P(r) is weak. Without the positivity term
     * nothing bounds dmax from above, and the suggestion falls back to lowestAdequate.
     */
    public static double SUGGESTED_DELTA = 4.0;
    /**
     * weight of the positivity term added to every score (0 switches it off). The term is
     *     -2 ln Pr(P(r) >= 0 in every Shannon bin after the peak | data, dmax)
     *       = sum_k -2 ln Phi(h_k / sigma_k)
     * with h_k the bin average and sigma_k its posterior sd. Shannon bins are close to independent
     * information channels, so the joint probability is taken as the product. A bin clearly above
     * zero costs ~0; a bin sitting on zero costs ln 4 = 1.39 (half its posterior is unphysical);
     * a bin at -1, -2, -3 sigma costs 3.7, 7.6, 13.2. It is on the same -2 ln L scale as the
     * evidence/AIC, so weight 1 is the natural value.
     */
    public static double POSITIVITY_WEIGHT = 1.0;
    /**
     * trial dmax values giving fewer Shannon channels than this, ceil(qmax dmax / pi), are skipped:
     * with one or two channels there is nothing to transform (Legendre cannot even be set up)
     */
    public static int MIN_SHANNON_CHANNELS = 3;
    /** fit in parallel (each fit is independent) */
    public static boolean PARALLEL = true;

    /** the five transforms, each choosing lambda by evidence (AIC_eff for L1); data as q*I(q), no background */
    public static Map<String, Method> defaultMethods() {
        MooreTransformApache.NORMALIZE_LAMBDA = true;
        LegendreTransform.NORMALIZE_LAMBDA = true;
        DirectSineIntegralTransform.NORMALIZE_LAMBDA = true;
        SineIntegralTransform.NORMALIZE_LAMBDA = true;
        SVD.MODEL_FITTED_BINS_ONLY = false;
        LegendreTransform.STRETCH_GRID_WITH_DMAX = true;
        final double[] grid = IndirectFT.logGrid(1e-7, 1e2, 2), gridL1 = IndirectFT.logGrid(1e-9, 1e-2, 2);
        Map<String, Method> m = new LinkedHashMap<>();
        m.put("GNOM-like", (d, e, D, q, c) -> IndirectFT.selectLambdaByEvidence(l -> new DirectSineIntegralTransform(d, e, D, q, l, 0.0, 1.0, false), grid, c));
        m.put("Legendre", (d, e, D, q, c) -> IndirectFT.selectLambdaByEvidence(l -> new LegendreTransform(d, e, D, q, l, 0.0, 1.0, false), grid, c));
        m.put("Shannon-L1", (d, e, D, q, c) -> IndirectFT.selectLambdaByEvidence(l -> new SineIntegralTransform(d, e, D, q, l, false, false, 0.0, 1.0), gridL1, c));
        m.put("SPI", (d, e, D, q, c) -> new SVD(d, e, D, q, 0, 0.0, 1.0));           // Shannon-limited pseudo-inverse
        m.put("Moore-L2", (d, e, D, q, c) -> new MooreTransformApache(d, e, D, q, MOORE_LAMBDA, 0.0, 1.0, false, MooreTransformApache.Regularization.L2));
        return m;
    }

    /* ------------------------------------------------------------------ dmax */

    public static final class DmaxScan {
        public final double qmax, cHat;
        public final double[] dmax;
        public final String[] methods;
        public final double[][] score, oscillation, negZ, rg, izero;   // [method][dmax]
        public final double[][] positivity;                            // -2 ln Pr(P >= 0 after the peak), included in score
        public final boolean[] included;
        public final double[][] methodProbability;                     // p_m(D)
        public final double[] probability, consensusScore;             // p(D), -2 ln(p/max p)
        public final double best, mean, lowestAdequate, largestAdequate, suggested;
        public final double[] interval68, interval95;                  // highest-probability sets, as [lo, hi]

        DmaxScan(double qmax, double cHat, double[] dmax, String[] methods, double[][] score, double[][] osc,
                 double[][] negZ, double[][] rg, double[][] izero, double[][] positivity) {
            this.qmax = qmax; this.cHat = cHat; this.dmax = dmax; this.methods = methods; this.positivity = positivity;
            this.score = score; this.oscillation = osc; this.negZ = negZ; this.rg = rg; this.izero = izero;
            int M = methods.length, n = dmax.length;
            included = new boolean[M];
            int nIn = 0;
            for (int m = 0; m < M; m++) {
                boolean scored = false;
                for (double sc : score[m]) if (!Double.isNaN(sc)) { scored = true; break; }
                double med = finiteMedian(osc[m]);
                included[m] = scored && (med <= METHOD_OSC_GATE || OSC_GATE_EXEMPT.contains(methods[m]));
                if (included[m]) nIn++;
            }
            if (nIn == 0) {
                for (int m = 0; m < M; m++) {
                    for (double sc : score[m]) if (!Double.isNaN(sc)) { included[m] = true; break; }
                    if (included[m]) nIn++;
                }
            }
            if (nIn == 0) throw new IllegalStateException("no method could be fitted at any trial dmax (q range too short for this dmax range?)");
            methodProbability = new double[M][n];
            probability = new double[n];
            for (int m = 0; m < M; m++) {
                double lo = Double.POSITIVE_INFINITY;
                for (double s : score[m]) if (!Double.isNaN(s)) lo = Math.min(lo, s);
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    double s = score[m][i];
                    methodProbability[m][i] = Double.isNaN(s) ? 0 : Math.exp(-0.5*(s - lo));
                    sum += methodProbability[m][i];
                }
                for (int i = 0; i < n; i++) {
                    methodProbability[m][i] = (sum > 0) ? methodProbability[m][i]/sum : 0;
                    if (included[m]) probability[i] += methodProbability[m][i]/nIn;
                }
            }
            int arg = 0;
            double mu = 0;
            for (int i = 0; i < n; i++) { if (probability[i] > probability[arg]) arg = i; mu += probability[i]*dmax[i]; }
            best = dmax[arg];
            mean = mu;
            consensusScore = new double[n];
            for (int i = 0; i < n; i++) consensusScore[i] = -2*Math.log(probability[i]/probability[arg]);
            double la = best;
            for (int i = 0; i < n; i++) if (consensusScore[i] <= ADEQUATE_DELTA) { la = dmax[i]; break; }
            lowestAdequate = la;
            int hi = arg;
            while (hi + 1 < n && consensusScore[hi + 1] <= SUGGESTED_DELTA) hi++;
            largestAdequate = dmax[hi];
            suggested = (POSITIVITY_WEIGHT > 0) ? largestAdequate : lowestAdequate;
            interval68 = highestProbabilitySet(0.68);
            interval95 = highestProbabilitySet(0.95);
        }

        private double[] highestProbabilitySet(double level) {
            Integer[] idx = new Integer[dmax.length];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Double.compare(probability[b], probability[a]));
            double acc = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int i : idx) {
                lo = Math.min(lo, dmax[i]); hi = Math.max(hi, dmax[i]);
                acc += probability[i];
                if (acc >= level) break;
            }
            return new double[]{lo, hi};
        }

        /** best dmax of one method */
        public double methodBest(int m) {
            int arg = 0;
            for (int i = 1; i < dmax.length; i++) if (methodProbability[m][i] > methodProbability[m][arg]) arg = i;
            return dmax[arg];
        }

        public String summary() {
            StringBuilder sb = new StringBuilder(String.format("dmax scan at qmax %.4f: suggested dmax %.0f (band %.0f-%.0f), most probable %.0f (68%% %.0f-%.0f, 95%% %.0f-%.0f)%n",
                    qmax, suggested, lowestAdequate, largestAdequate, best, interval68[0], interval68[1], interval95[0], interval95[1]));
            for (int m = 0; m < methods.length; m++) {
                sb.append(String.format("  %-11s best %.0f%s%n", methods[m], methodBest(m), included[m] ? "" : "   (excluded: oscillates)"));
            }
            return sb.toString();
        }
    }

    /** scores every method at every trial dmax (qmax fixed) and builds the consensus */
    public static DmaxScan scanDmax(XYSeries data, XYSeries errors, double qmax, double[] dmaxGrid, Map<String, Method> methods) {
        XYSeries d = IndirectFT.truncateSeries(data, qmax), e = IndirectFT.truncateSeries(errors, qmax);
        final double q = d.getMaxX();
        final double cHat = referenceCHat(d, e, dmaxGrid[dmaxGrid.length - 1], q);
        String[] names = methods.keySet().toArray(new String[0]);
        Method[] ms = methods.values().toArray(new Method[0]);
        int M = ms.length, n = dmaxGrid.length;
        double[][] score = new double[M][n], osc = new double[M][n], z = new double[M][n], rg = new double[M][n], i0 = new double[M][n];
        double[][] pos = new double[M][n];
        IntStream jobs = IntStream.range(0, M*n);
        if (PARALLEL) jobs = jobs.parallel();
        for (double[] row : score) Arrays.fill(row, Double.NaN);
        for (double[] row : osc) Arrays.fill(row, Double.NaN);
        for (double[] row : z) Arrays.fill(row, Double.NaN);
        for (double[] row : rg) Arrays.fill(row, Double.NaN);
        for (double[] row : i0) Arrays.fill(row, Double.NaN);
        for (double[] row : pos) Arrays.fill(row, Double.NaN);
        jobs.forEach(job -> {
            int m = job/n, i = job % n;
            if (Math.ceil(q*dmaxGrid[i]/Math.PI) < MIN_SHANNON_CHANNELS) return;   // too few channels
            IndirectFT f = fitOrNull(ms[m], d, e, dmaxGrid[i], q, cHat);
            if (f == null) return;                                                // fit failed: no score
            double s = f.getMinusTwoLogEvidence(cHat);
            score[m][i] = Double.isNaN(s) ? f.getAICEffective(cHat) : s;
            pos[m][i] = positivityTerm(f);
            if (POSITIVITY_WEIGHT > 0 && !Double.isNaN(pos[m][i])) score[m][i] += POSITIVITY_WEIGHT*pos[m][i];
            osc[m][i] = f.prOscillation;
            z[m][i] = f.prNegZ;
            rg[m][i] = f.getRg();
            i0[m][i] = f.getIzeroParticle().value;
        });
        return new DmaxScan(q, cHat, dmaxGrid.clone(), names, score, osc, z, rg, i0, pos);
    }

    /** -2 ln Pr(every Shannon bin after the peak is >= 0), see POSITIVITY_WEIGHT; NaN if no covariance */
    public static double positivityTerm(IndirectFT f) {
        XYSeries bins = f.prDistributionForFitting;
        if (bins == null || bins.getItemCount() < 2) return Double.NaN;
        int nb = bins.getItemCount();
        double[][] cov = EnsembleIFT.binCovariance(f, nb, Math.PI/f.qmax);
        int peak = 0;
        for (int k = 1; k < nb; k++) if (bins.getY(k).doubleValue() > bins.getY(peak).doubleValue()) peak = k;
        double sum = 0;
        for (int k = peak + 1; k < nb; k++) {
            double sd = Math.sqrt(Math.max(0, cov[k][k]));
            if (!(sd > 0)) return Double.NaN;
            sum += -2*logPhi(bins.getY(k).doubleValue()/sd);
        }
        return sum;
    }

    /** ln of the standard normal cdf, accurate in the far lower tail */
    static double logPhi(double z) {
        if (z < -8) {                         // asymptotic expansion
            double z2 = z*z;
            return -0.5*z2 - Math.log(-z) - 0.5*Math.log(2*Math.PI) + Math.log(1 - 1/z2 + 3/(z2*z2));
        }
        return Math.log(0.5*erfc(-z/Math.sqrt(2)));
    }

    /** complementary error function (Numerical Recipes erfcc, relative error < 1.2e-7) */
    static double erfc(double x) {
        double z = Math.abs(x), t = 1/(1 + 0.5*z);
        double r = t*Math.exp(-z*z - 1.26551223 + t*(1.00002368 + t*(0.37409196 + t*(0.09678418 + t*(-0.18628806
                + t*(0.27886807 + t*(-1.13520398 + t*(1.48851587 + t*(-0.82215223 + t*0.17087277)))))))));
        return x >= 0 ? r : 2 - r;
    }

    /** the method's fit, or null if it throws or its solver failed */
    static IndirectFT fitOrNull(Method method, XYSeries d, XYSeries e, double dmax, double qmax, double cHat) {
        try {
            IndirectFT f = method.fit(d, e, dmax, qmax, cHat);
            return (f != null && f.isSolved()) ? f : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** median of the finite values (NaN if there are none) */
    static double finiteMedian(double[] v) {
        double[] c = Arrays.stream(v).filter(x -> !Double.isNaN(x) && !Double.isInfinite(x)).sorted().toArray();
        int n = c.length;
        if (n == 0) return Double.NaN;
        return (n % 2 == 1) ? c[n/2] : 0.5*(c[n/2 - 1] + c[n/2]);
    }

    /** reduced chi^2 of a weakly regularized fit at the largest trial dmax: the noise scale for the scan */
    static double referenceCHat(XYSeries d, XYSeries e, double dmax, double qmax) {
        return new MooreTransformApache(d, e, dmax, qmax, 1e-7, 0.0, 1.0, false, MooreTransformApache.Regularization.L2)
                .getReducedChiSquaredAllPoints();
    }

    /* ------------------------------------------------------------------ qmax */

    public static final class QmaxScan {
        public final double dmax, qmax;
        public final double[] q, medianOscillation;
        public final double[][] oscillation;     // [channel][method]
        public final String[] methods;
        QmaxScan(double dmax, double qmax, double[] q, double[] med, double[][] osc, String[] methods) {
            this.dmax = dmax; this.qmax = qmax; this.q = q; medianOscillation = med; oscillation = osc; this.methods = methods;
        }
        public String summary() {
            StringBuilder sb = new StringBuilder(String.format("qmax scan at dmax %.0f: qmax %.4f%n   q        median osc%n", dmax, qmax));
            for (int k = 0; k < q.length; k++) sb.append(String.format("  %.4f   %.4f%s%n", q[k], medianOscillation[k], q[k] == qmax ? "   <- qmax" : ""));
            return sb.toString();
        }
    }

    /**
     * qmax for a given dmax. Moore-L2 is left out of the median: at its low fixed lambda it rings
     * at every qmax, so its oscillation does not mark where the data stop carrying information.
     */
    public static QmaxScan selectQmax(XYSeries data, XYSeries errors, double dmax, Map<String, Method> methods) {
        Map<String, Method> use = new LinkedHashMap<>(methods);
        use.remove("Moore-L2");
        if (use.isEmpty()) use = new LinkedHashMap<>(methods);   // only Moore selected: use it rather than nothing
        String[] names = use.keySet().toArray(new String[0]);
        Method[] ms = use.values().toArray(new Method[0]);
        double w = Math.PI/dmax, qData = data.getMaxX();
        List<Double> qs = new ArrayList<>();
        for (int k = QMAX_MIN_CHANNELS; k*w < qData; k++) qs.add(k*w);
        qs.add(qData);
        int K = qs.size(), M = ms.length;
        double[] qv = new double[K];
        double[][] osc = new double[K][M];
        XYSeries[] dk = new XYSeries[K], ek = new XYSeries[K];
        double[] cHat = new double[K];
        for (int k = 0; k < K; k++) {
            dk[k] = IndirectFT.truncateSeries(data, qs.get(k));
            ek[k] = IndirectFT.truncateSeries(errors, qs.get(k));
            qv[k] = dk[k].getMaxX();
        }
        IntStream pre = IntStream.range(0, K);
        if (PARALLEL) pre = pre.parallel();
        pre.forEach(k -> cHat[k] = referenceCHat(dk[k], ek[k], dmax, qv[k]));
        IntStream jobs = IntStream.range(0, K*M);
        if (PARALLEL) jobs = jobs.parallel();
        jobs.forEach(job -> {
            int k = job/M, m = job % M;
            IndirectFT f = (Math.ceil(qv[k]*dmax/Math.PI) < MIN_SHANNON_CHANNELS) ? null : fitOrNull(ms[m], dk[k], ek[k], dmax, qv[k], cHat[k]);
            osc[k][m] = (f == null) ? Double.NaN : f.prOscillation;
        });
        double[] med = new double[K];
        for (int k = 0; k < K; k++) {
            med[k] = finiteMedian(osc[k]);
            if (Double.isNaN(med[k])) med[k] = 0;      // nothing could be fitted at this qmax: no evidence of ripples
        }
        int last = K - 1;
        for (int k = 0; k < K; k++) {
            boolean bad = true;
            for (int j = k; j < Math.min(K, k + QMAX_PERSISTENCE); j++) bad &= med[j] > QMAX_OSC_THRESHOLD;
            if (bad && k + QMAX_PERSISTENCE <= K) { last = Math.max(0, k - 1); break; }
        }
        return new QmaxScan(dmax, qv[last], qv, med, osc, names);
    }

    /* ------------------------------------------------------------------ both */

    public static final class Selection {
        public final double qmax, dmax;
        public final DmaxScan dmaxScan;
        public final QmaxScan qmaxScan;
        public final List<String> log;
        Selection(DmaxScan ds, QmaxScan qs, List<String> log) {
            dmaxScan = ds; qmaxScan = qs; this.log = log; qmax = ds.qmax; dmax = ds.suggested;
        }
    }

    /**
     * Alternates dmax (lowest adequate consensus dmax over dmaxGrid) and qmax (oscillation onset at that dmax), starting
     * from the full data range, until dmax moves by less than one grid step (at most maxIter rounds).
     */
    public static Selection select(XYSeries data, XYSeries errors, double[] dmaxGrid, Map<String, Method> methods, int maxIter) {
        List<String> log = new ArrayList<>();
        double qmax = data.getMaxX();
        DmaxScan ds = scanDmax(data, errors, qmax, dmaxGrid, methods);
        log.add(ds.summary());
        QmaxScan qs = null;
        double step = (dmaxGrid.length > 1) ? dmaxGrid[1] - dmaxGrid[0] : 0;
        for (int it = 0; it < maxIter; it++) {
            qs = selectQmax(data, errors, ds.suggested, methods);
            log.add(qs.summary());
            if (Math.abs(qs.qmax - ds.qmax) < 1e-9) break;           // qmax unchanged: done
            double prev = ds.suggested;
            ds = scanDmax(data, errors, qs.qmax, dmaxGrid, methods);
            log.add(ds.summary());
            if (Math.abs(ds.suggested - prev) <= step) break;
        }
        return new Selection(ds, qs, log);
    }

    public static double[] grid(double lo, double hi, double step) {
        int n = (int) Math.floor((hi - lo)/step + 1e-9) + 1;
        double[] g = new double[n];
        for (int i = 0; i < n; i++) g[i] = lo + i*step;
        return g;
    }
}
