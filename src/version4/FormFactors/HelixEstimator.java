package version4.FormFactors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;

/**
 * Starting values for helical filaments, straight from the data, so the user does not have to guess them.
 * Two models compete at every q limit and the better one is reported: infinitely long helices with a radius
 * distribution (below) and finite filaments, one helix with a length distribution (see {@link Finite}). The data are
 * first rebinned to at most 200 log-spaced points.
 *
 * Model: I(q) = sum_r w_r P_inf(q; r, pitch, a, strands) + background with w_r >= 0 on a log grid of helix radii
 * (non-negative least squares, error weighted), P_inf as in {@link InfiniteHelixKernel}. Surveyed:
 *   strands 1 and 2 (symmetric), strand radius a on a grid, pitch on a log grid between 2 pi / q_max (first layer
 *   line at the top of the data) and 4 pi / q_min, plus "no layer line in the data";
 *   and a ladder of upper q limits, because uniform strands describe the cross-section of real filaments only up to
 *   some q (beyond it, domain structure dominates). The suggested q max is the largest limit whose best reduced chi^2
 *   stays within twice the best over the ladder.
 * P_inf factorises: [J0^2(qr) + 2 sum F_k Jk^2(..)] depends on (strands, pitch, r), the strand radius only scales a
 * row by exp(-q^2 a^2 / 4) and a q limit only drops rows, so one matrix per (strands, pitch) serves the whole survey.
 */
public final class HelixEstimator {

    public static final class Estimate {
        public double qmax;                 // suggested upper q limit
        public double rMin, rMax, rMean;    // radius range holding the central 90 % of the mass (plus a margin), mean
        public double[] peaks = new double[0];
        public double strandRadius;
        public int strands;
        public double pitch;                // best pitch (NaN = no layer line in the data)
        public boolean pitchVisible;        // the best pitch puts a layer line inside q <= qmax
        public double pitchScanLo, pitchScanHi;
        public double chi2;                 // reduced chi^2 of the best survey fit at qmax
        public double[] ladderQ, ladderChi2;
        /** finite filaments (one helix, a length distribution) fit better than infinitely long ones */
        public boolean finite;
        public double helixRadius = Double.NaN;          // finite: the (single) helix radius
        public double lMin, lMax, lMedian;               // finite: mass-weighted length range (central 90 %) and median
        public double lResolved;                         // longest length the q range resolves (~ 5.2 / q min)
        public double fractionUnresolved;                // finite: mass fraction longer than lResolved
        public double chi2Infinite = Double.NaN, chi2Finite = Double.NaN;   // best of each model at qmax
        /** (strands, pitch lo, pitch hi) ranges whose survey fit is about as good as the best (pitch not resolved) */
        public List<double[]> pitchAlternatives = new ArrayList<>();
        public boolean pitchDetermined = true;
        /** if q max was reduced: the best model over the full q range (for "keep q range"); else null */
        public Estimate fullRange;

        public String summary() {
            String main = finite ? summaryFinite() : summaryInfinite();
            if (fullRange == null) return main;
            Estimate f = fullRange;
            StringBuilder sb = new StringBuilder(main);
            sb.append(String.format(Locale.US, "%n%nIf you keep the full q range (to %.3f Å⁻¹), the best model is:%n   ", f.qmax));
            if (f.finite) {
                sb.append(String.format(Locale.US, "finite filaments, helix radius %.0f Å, strand radius %.0f Å, %d strand%s, ",
                        f.helixRadius, f.strandRadius, f.strands, f.strands > 1 ? "s" : ""));
                sb.append(f.pitchVisible ? String.format(Locale.US, "pitch %.0f Å%s", f.pitch, f.pitchDetermined ? "" : " (not determined)")
                        : "no layer line");
                sb.append(String.format(Locale.US, ", length %.0f-%.0f Å", f.lMin, f.lMax));
            } else
                sb.append(String.format(Locale.US, "infinitely long, helix radius %.0f-%.0f Å, strand radius %.0f Å",
                        f.rMin, f.rMax, f.strandRadius));
            sb.append(String.format(Locale.US, "; reduced χ² %.2f", f.chi2));
            return sb.toString();
        }

        private String summaryInfinite() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "Suggested q max: %.3f Å⁻¹%n", qmax));
            sb.append(String.format(Locale.US, "Helix radius: %.0f-%.0f Å (mean %.0f Å", rMin, rMax, rMean));
            if (peaks.length > 0) {
                sb.append("; peaks at");
                for (double p : peaks) sb.append(String.format(Locale.US, " %.0f", p));
            }
            if (pitchVisible) sb.append(String.format(Locale.US, " Å)%nStrands: %d, strand radius %.0f Å%n", strands, strandRadius));
            else sb.append(String.format(Locale.US, " Å)%nStrand radius %.0f Å; number of strands not determined "
                    + "(it only shows in the layer lines)%n", strandRadius));
            if (pitchVisible) sb.append(String.format(Locale.US, "Pitch: about %.0f Å (scan %.0f-%.0f Å)%n", pitch, pitchScanLo, pitchScanHi));
            else sb.append(String.format(Locale.US, "Pitch: no helical layer line is supported below q max "
                    + "(pitch < %.0f Å per strand, or layer lines too weak to see)%n", strands * 2 * Math.PI / qmax));
            sb.append(String.format(Locale.US, "Survey fit: reduced χ² %.2f%n", chi2));
            sb.append("Reduced χ² of the best survey fit by upper q limit:");
            for (int i = 0; i < ladderQ.length; i++)
                sb.append(String.format(Locale.US, "%n   q ≤ %.3f : %.2f", ladderQ[i], ladderChi2[i]));
            if (Double.isFinite(chi2Finite))
                sb.append(String.format(Locale.US, "%nFinite filaments (one helix, length distribution): reduced χ² %.2f", chi2Finite));
            return sb.toString();
        }

        private static String repeat(int strands, double pitch) {
            return strands > 1 ? String.format(Locale.US, " (axial repeat %.0f Å)", pitch / strands) : "";
        }

        private String summaryFinite() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "Model: finite filaments (one helix, length distribution); reduced χ² %.2f", chi2));
            if (Double.isFinite(chi2Infinite))
                sb.append(String.format(Locale.US, "%n   (infinitely long, radius distribution: %.2f)", chi2Infinite));
            sb.append(String.format(Locale.US, "%nSuggested q max: %.3f Å⁻¹%n", qmax));
            sb.append(String.format(Locale.US, "Helix radius: %.0f Å, strand radius %.0f Å (outer diameter ≈ %.0f Å",
                    helixRadius, strandRadius, 2 * (helixRadius + strandRadius)));
            if (helixRadius > strandRadius) sb.append(String.format(Locale.US, ", lumen ≈ %.0f Å", 2 * (helixRadius - strandRadius)));
            sb.append(")\n");
            sb.append(String.format(Locale.US, "Length (mass-weighted): %.0f-%.0f Å, median %.0f Å", lMin, lMax, lMedian));
            if (fractionUnresolved > 0.05)
                sb.append(String.format(Locale.US, "%n   %.0f %% of the mass is longer than the q range resolves (> %.0f Å): "
                        + "only a lower bound", 100 * fractionUnresolved, lResolved));
            sb.append('\n');
            if (pitchVisible && pitchDetermined)
                sb.append(String.format(Locale.US, "Strands: %d; pitch about %.0f Å%s (scan %.0f-%.0f Å)%n", strands, pitch,
                        repeat(strands, pitch), pitchScanLo, pitchScanHi));
            else if (pitchVisible) {
                sb.append(String.format(Locale.US, "Best: %d strand%s, pitch %.0f Å%s, but the pitch is not determined; "
                        + "about as good:%n", strands, strands > 1 ? "s" : "", pitch, repeat(strands, pitch)));
                for (double[] alt : pitchAlternatives)
                    sb.append(String.format(Locale.US, "   %d strand%s, pitch %.0f-%.0f Å%s%n", (int) alt[0], alt[0] > 1 ? "s" : "",
                            alt[1], alt[2], alt[0] > 1 ? String.format(Locale.US, " (axial repeat %.0f-%.0f Å)", alt[1] / alt[0], alt[2] / alt[0]) : ""));
                sb.append("   (an n-start helix of pitch P has its layer lines where a 1-start helix of pitch P/n has them;\n"
                        + "    they are weak after orientational averaging: test the candidates with the pitch scan)\n");
            } else
                sb.append(String.format(Locale.US, "Pitch: no helical layer line is supported below q max (pitch < %.0f Å per strand, "
                        + "or layer lines too weak to see); number of strands not determined%n", strands * 2 * Math.PI / qmax));
            sb.append("Reduced χ² of the best survey fit by upper q limit:");
            for (int i = 0; i < ladderQ.length; i++)
                sb.append(String.format(Locale.US, "%n   q ≤ %.3f : %.2f", ladderQ[i], ladderChi2[i]));
            return sb.toString();
        }
    }

    private static final double[] STRAND_RADII = {0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 80};
    private static final int NR0 = 44, NPITCH = 28;

    private HelixEstimator() {}

    /**
     * @param q, I, sigma data (sorted by q, positive errors), already limited to the user's q range
     * @param progress  receives 0..1 (may be null)
     */
    public static Estimate estimate(double[] q, double[] I, double[] sigma, DoubleConsumer progress) {
        return estimate(q, I, sigma, progress, false);
    }

    /** @param quick coarse survey only (no pitch or radius refinement): enough for a radius range and a q limit */
    static Estimate estimate(double[] q0, double[] I0, double[] sigma0, DoubleConsumer progress0, boolean quick) {
        if (q0.length < 20) throw new IllegalArgumentException("Too few points for an estimate");
        double[][] rb = rebin(q0, I0, sigma0, 200);
        double[] q = rb[0], I = rb[1], sigma = rb[2];
        int n = q.length;
        // the infinite survey takes the first half of the progress, the finite survey the second
        DoubleConsumer progress = progress0 == null || quick ? progress0 : f -> progress0.accept(0.5 * f);
        double qmin = q[0], qmax = q[n - 1];

        // radius grid: from what q max resolves to beyond what q min resolves
        double rLo = Math.max(3, 1.0 / qmax), rHi = Math.min(1500, 4.0 / qmin);
        double[] radii = new double[NR0];
        for (int i = 0; i < NR0; i++) radii[i] = rLo * Math.pow(rHi / rLo, i / (NR0 - 1.0));

        // pitch grid: index 0 = no layer line in the data, then first layer line from the top of the data downwards
        double pLo = 2 * Math.PI / qmax, pHi = Math.min(2000, 4 * Math.PI / qmin);
        double[] pitches = new double[NPITCH + 1];
        pitches[0] = 0.8 * pLo;
        for (int i = 0; i < NPITCH; i++) pitches[i + 1] = pLo * Math.pow(pHi / pLo, i / (NPITCH - 1.0));

        // q-limit ladder
        List<Integer> cut = new ArrayList<>();
        double qStart = Math.max(qmin * 4, Math.min(0.04, qmax));
        for (int i = 0; i < 9; i++) {
            double qc = qStart * Math.pow(qmax / qStart, i / 8.0);
            int m = 0;
            while (m < n && q[m] <= qc * (1 + 1e-9)) m++;
            if (m >= 15 && (cut.isEmpty() || m > cut.get(cut.size() - 1))) cut.add(m);
        }
        if (cut.isEmpty() || cut.get(cut.size() - 1) != n) cut.add(n);

        double[] bestChi = new double[cut.size()];
        Arrays.fill(bestChi, Double.POSITIVE_INFINITY);
        Object[][] bestAt = new Object[cut.size()][];   // {strands, pitch, a, weights, radii}
        double[] b = new double[n];
        for (int j = 0; j < n; j++) b[j] = I[j] / sigma[j];
        Survey sv = new Survey(q, sigma, b, radii, cut, bestChi, bestAt);

        // 1. coarse survey
        int[] strandsList = {1, 2};
        List<double[]> coarse = new ArrayList<>();          // {strands, pitch}
        for (int st : strandsList) for (double p : pitches) coarse.add(new double[]{st, p});
        sv.run(coarse, STRAND_RADII, progress, 0, quick ? 1.0 : 0.6);
        if (quick) return finish(q, n, cut, bestChi, bestAt, pLo);

        // 2. refine around the best candidates of every q limit: layer lines are sharp, so the pitch needs ~1 %
        // every geometry carries its own strand radii (around its seed's), so the fine radius grid stays affordable
        List<double[]> fine = new ArrayList<>();
        // candidates: the best of every q limit, plus the 4 best pitches per strand count over the full range
        List<Object[]> cands = new ArrayList<>();
        for (Object[] at : bestAt) if (at != null) cands.add(at);
        for (int st : strandsList) {
            sv.full.stream().filter(g -> g[0] == st && g[1] >= pLo).sorted((x, y) -> Double.compare(x[2], y[2])).limit(4)
                    .forEach(g -> cands.add(new Object[]{st, g[1], Double.NaN, null, null}));
        }
        for (Object[] at : cands) {
            int st = (Integer) at[0];
            double p0 = (Double) at[1], a0 = (Double) at[2];
            double[] as = Double.isNaN(a0) ? STRAND_RADII : aroundA(a0, 7.5, 2.5);
            if (p0 < pLo) { addGeometry(fine, st, p0, as); continue; }   // "no layer line": nothing to refine
            for (int k = -10; k <= 10; k++) addGeometry(fine, st, p0 * Math.pow(1.18, k / 10.0), as);
        }
        // fine radius grid over the range the coarse fits use: at high q the Bessel terms oscillate in r with a
        // period ~ 2 pi / q, so the steps must stay below about pi / (4 q max) (at most 140 radii)
        double fLo = rHi, fHi = rLo;
        for (Object[] at : bestAt) {
            if (at == null) continue;
            double[] w = (double[]) at[3], rr = (double[]) at[4];
            double tot = 0;
            for (int i = 0; i < rr.length; i++) tot += w[i];
            for (int i = 0; i < rr.length; i++)
                if (w[i] > 0.003 * tot) { fLo = Math.min(fLo, rr[i]); fHi = Math.max(fHi, rr[i]); }
        }
        fLo = Math.max(rLo, 0.7 * fLo);
        fHi = Math.min(rHi, 1.3 * fHi);
        List<Double> fr = new ArrayList<>();
        for (double r = fLo; r <= fHi * 1.0001 && fr.size() < 140; r += Math.max(Math.min(0.06 * r, Math.PI / (4 * qmax)), (fHi - fLo) / 139.0) + 1e-9) fr.add(r);
        double[] fineRadii = fr.stream().mapToDouble(Double::doubleValue).toArray();
        if (fineRadii.length >= 5) {
            // the coarse best (strands, pitch) also gets the fine radii
            new Survey(q, sigma, b, fineRadii, cut, bestChi, bestAt).run(fine, STRAND_RADII, progress, 0.6, 0.85);

            // 3. polish: high-order layer lines need the pitch to ~0.3 % and the strand radius to ~1 A
            List<double[]> polish = new ArrayList<>();
            for (Object[] at : bestAt) {
                if (at == null) continue;
                int st = (Integer) at[0];
                double p0 = (Double) at[1], a0 = (Double) at[2];
                double[] as = aroundA(a0, 3, 1.5);
                if (p0 < pLo) { addGeometry(polish, st, p0, as); continue; }
                for (int k = -8; k <= 8; k++) addGeometry(polish, st, p0 * (1 + 0.003 * k), as);
            }
            new Survey(q, sigma, b, fineRadii, cut, bestChi, bestAt).run(polish, STRAND_RADII, progress, 0.85, 1.0);
        }

        double[] ladderQ = new double[cut.size()];
        for (int c = 0; c < cut.size(); c++) ladderQ[c] = q[cut.get(c) - 1];
        Finite fin = new Finite(q, I, sigma, ladderQ, pitches, pLo);
        fin.run(progress0 == null ? null : f -> progress0.accept(0.5 + 0.5 * f));
        return finish(q, n, cut, bestChi, bestAt, pLo, fin);
    }

    private static double[] aroundA(double a0, double half, double step) {
        List<Double> l = new ArrayList<>();
        for (double da = -half; da <= half + 1e-9; da += step) if (a0 + da >= 0) l.add(a0 + da);
        return l.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** {strands, pitch, strand radii...}; a geometry already listed gets the union of strand radii */
    private static void addGeometry(List<double[]> list, int st, double p, double[] as) {
        for (int i = 0; i < list.size(); i++) {
            double[] f = list.get(i);
            if (f[0] == st && Math.abs(f[1] / p - 1) < 1e-4) {
                double[] u = java.util.stream.DoubleStream.concat(Arrays.stream(f, 2, f.length), Arrays.stream(as))
                        .map(a -> Math.rint(a * 1000) / 1000).distinct().sorted().toArray();
                double[] g = new double[2 + u.length];
                g[0] = st; g[1] = f[1];
                System.arraycopy(u, 0, g, 2, u.length);
                list.set(i, g);
                return;
            }
        }
        double[] g = new double[2 + as.length];
        g[0] = st; g[1] = p;
        System.arraycopy(as, 0, g, 2, as.length);
        list.add(g);
    }

    private static Estimate finish(double[] q, int n, List<Integer> cut, double[] bestChi, Object[][] bestAt, double pLo) {
        return finish(q, n, cut, bestChi, bestAt, pLo, null);
    }

    private static Estimate finish(double[] q, int n, List<Integer> cut, double[] infChi, Object[][] bestAt, double pLo,
                                   Finite fin) {
        // per q limit the better of the two models; then the largest q limit whose fit stays close to the best
        double[] bestChi = infChi.clone();
        if (fin != null) for (int c = 0; c < cut.size(); c++) bestChi[c] = Math.min(bestChi[c], fin.bestChi[c]);
        double floor = Arrays.stream(bestChi).min().orElse(Double.NaN);
        int pick = 0;
        for (int c = 0; c < cut.size(); c++) if (bestChi[c] <= Math.max(2 * floor, floor + 1.5)) pick = c;
        Estimate e = buildAt(pick, q, cut, infChi, bestChi, bestAt, pLo, fin);
        int last = cut.size() - 1;
        // the user may keep the full q range: give the best model there too
        if (pick < last && fin != null) e.fullRange = buildAt(last, q, cut, infChi, bestChi, bestAt, pLo, fin);
        return e;
    }

    private static double[] ladderQ(double[] q, List<Integer> cut) {
        double[] l = new double[cut.size()];
        for (int c = 0; c < cut.size(); c++) l[c] = q[cut.get(c) - 1];
        return l;
    }

    private static Estimate buildAt(int pick, double[] q, List<Integer> cut, double[] infChi, double[] bestChi,
                                    Object[][] bestAt, double pLo, Finite fin) {
        if (fin != null && fin.bestAt[pick] != null && fin.bestChi[pick] < infChi[pick])
            return fin.estimate(pick, ladderQ(q, cut), bestChi, infChi[pick]);

        Estimate e = new Estimate();
        if (fin != null) e.chi2Finite = fin.bestChi[pick];
        e.qmax = q[cut.get(pick) - 1];
        e.ladderQ = new double[cut.size()];
        e.ladderChi2 = bestChi.clone();
        for (int c = 0; c < cut.size(); c++) e.ladderQ[c] = q[cut.get(c) - 1];
        Object[] best = bestAt[pick];
        e.strands = (Integer) best[0];
        double bestPitch = (Double) best[1];
        e.strandRadius = (Double) best[2];
        e.chi2 = bestChi[pick];
        // with n symmetric strands the first layer line is at q = n 2 pi / pitch
        e.pitchVisible = bestPitch >= pLo && e.strands * 2 * Math.PI / bestPitch < e.qmax;
        e.pitch = e.pitchVisible ? bestPitch : Double.NaN;
        if (e.pitchVisible) {
            e.pitchScanLo = Math.max(e.strands * 2 * Math.PI / e.qmax, 0.7 * e.pitch);
            e.pitchScanHi = 1.4 * e.pitch;
        }

        // radius distribution: weights -> mass-weighted fractions (intensity weights / mass per length)
        double[] w = (double[]) best[3];
        double[] radii = (double[]) best[4];
        int NR = radii.length;
        double[] mass = new double[NR];
        double total = 0;
        for (int i = 0; i < NR; i++) {
            double p = (e.pitchVisible ? e.pitch : 1e6) / (2 * Math.PI);
            double perLength = Math.sqrt(p * p + radii[i] * radii[i]) / p;      // mass per length ~ contour / axial
            mass[i] = w[i] / perLength;
            total += mass[i];
        }
        if (!(total > 0)) throw new IllegalStateException("No helix radius fits the data");
        double acc = 0, mean = 0;
        e.rMin = radii[0];
        e.rMax = radii[NR - 1];
        boolean lowSet = false;
        for (int i = 0; i < NR; i++) {
            acc += mass[i];
            mean += mass[i] * radii[i];
            if (!lowSet && acc >= 0.05 * total) { e.rMin = radii[Math.max(0, i - 1)]; lowSet = true; }
            if (acc <= 0.95 * total) e.rMax = radii[Math.min(NR - 1, i + 2)];
        }
        e.rMean = mean / total;
        e.rMin = Math.max(1, Math.floor(e.rMin * 0.9 / 5) * 5);
        e.rMax = Math.ceil(e.rMax * 1.1 / 5) * 5;
        List<Double> pk = new ArrayList<>();
        double mx = Arrays.stream(mass).max().orElse(0);
        for (int i = 0; i < NR; i++) {
            boolean up = i == 0 || mass[i] > mass[i - 1], down = i == NR - 1 || mass[i] >= mass[i + 1];
            if (up && down && mass[i] > 0.1 * mx) pk.add(radii[i]);
        }
        e.peaks = pk.stream().mapToDouble(Double::doubleValue).toArray();
        return e;
    }

    // ================================================================== finite filaments

    /** reduced chi^2 (full range, rebinned as in the estimate) of one finite helix with a length distribution (tests) */
    static double finiteChi(double[] q0, double[] I0, double[] s0, int strands, double pitch, double r, double a) {
        double[][] rb = rebin(q0, I0, s0, 200);
        int n = rb[0].length;
        Finite.Data d = new Finite.Data(rb[0], rb[1], rb[2], new double[]{rb[0][n - 1]}, Finite.NL, 0);
        return d.fit(d.table(strands, pitch, r), a, null)[0];
    }

    /**
     * Finite filaments: ONE helix (strands, pitch, radius r, strand radius a) with a distribution of lengths,
     * I(q) = sum_L w_L P(q; L) + background (NNLS), P the exact finite continuous helix (eq. 15 / 23, as in
     * {@link HelixKernel}). Unlike the radius distribution of the infinite survey, this keeps the cross-section sharp
     * (its first minimum pins r and a, and finite lengths fill it in) and follows the low-q bend of finite lengths.
     * Rod-times-cross-section shortcuts get that filling wrong by orders of magnitude, so every fit is exact; to keep
     * it fast the survey runs on <= 80 points and 12 lengths, and the strand radius and q limit cost nothing extra
     * (a row scale and a prefix of the normal equations).
     *
     * Stage 1, cross-section: radius x strand radius with no layer line in the data (pitch-independent).
     * Stage 2, layer lines: strands 1, 2 x pitch grid at the best radii of every q limit.
     * Stage 3, refinement: pitch, then radius, around the best few of every q limit.
     * Stage 4: the best candidates again on the full (200-point) data with 24 lengths; only these are reported.
     */
    private static final class Finite {
        static final int NL = 24, NS = 12, NR = 36;
        static final double[] A_GRID = {0, 5, 10, 15, 20, 25, 30, 35, 40, 50, 60, 80};

        /** data, q ladder and length grid; fits of one geometry over every q limit */
        static final class Data {
            final double[] q, sigma, b, bb, L;
            final int[] cut;
            final int nl;

            Data(double[] q, double[] I, double[] sigma, double[] ladderQ, int nl, double lHi) {
                this.q = q; this.sigma = sigma; this.nl = nl;
                int n = q.length;
                b = new double[n];
                bb = new double[n + 1];
                for (int j = 0; j < n; j++) { b[j] = I[j] / sigma[j]; bb[j + 1] = bb[j] + b[j] * b[j]; }
                cut = new int[ladderQ.length];
                for (int c = 0; c < ladderQ.length; c++) {
                    int m = 0;
                    while (m < n && q[m] <= ladderQ[c] * (1 + 1e-9)) m++;
                    cut[c] = Math.max(m, 6);
                }
                // lengths: from ~ a subunit to well past what q min resolves (rod Rg = L / sqrt 12)
                double hi = lHi > 0 ? lHi : Math.min(20000, 12 / q[0]);
                L = new double[nl];
                for (int k = 0; k < nl; k++) L[k] = 40 * Math.pow(hi / 40, k / (nl - 1.0));
            }

            /** P(q_j; L_k) without the strand cross-section */
            double[][] table(int strands, double pitch, double r) {
                HelixMath.Geometry geo = HelixMath.Geometry.symmetric(r, pitch, 0, strands);
                int n = q.length;
                double[][] T = new double[n][nl];
                for (int j = 0; j < n; j++) {
                    HelixMath.AtQ at = new HelixMath.AtQ(geo, q[j], Math.min(L[nl - 1], HelixMath.LIMIT_ABOVE / q[j]));
                    for (int k = 0; k < nl; k++) T[j][k] = at.formFactor(L[k]);
                }
                return T;
            }

            /**
             * NNLS of I ~ sum_k exp(-q^2 a^2/4) T_jk w_k + bg over every q limit (normal equations accumulated row by
             * row); returns the reduced chi^2 per q limit and fills weights[c] if not null
             */
            double[] fit(double[][] T, double a, double[][] weights) {
                int K = nl + 1, nc = cut.length;
                double[][] G = new double[K][K];
                double[] h = new double[K], row = new double[K], chi = new double[nc];
                int j = 0;
                for (int ci = 0; ci < nc; ci++) {
                    int m = cut[ci];
                    for (; j < m; j++) {
                        double cs = Math.exp(-q[j] * q[j] * a * a / 4) / sigma[j];
                        for (int k = 0; k < nl; k++) row[k] = cs * T[j][k];
                        row[nl] = 1 / sigma[j];
                        for (int x = 0; x < K; x++) {
                            double rx = row[x];
                            if (rx == 0) continue;
                            h[x] += rx * b[j];
                            double[] Gx = G[x];
                            for (int y = x; y < K; y++) Gx[y] += rx * row[y];
                        }
                    }
                    double[][] Gs = new double[K][K];
                    for (int x = 0; x < K; x++) for (int y = x; y < K; y++) Gs[x][y] = Gs[y][x] = G[x][y];
                    double[] w = nnlsGram(Gs, h);
                    double s = bb[m];
                    for (int x = 0; x < K; x++) {
                        if (w[x] == 0) continue;
                        s -= 2 * w[x] * h[x];
                        for (int y = 0; y < K; y++) s += w[x] * Gs[x][y] * w[y];
                    }
                    chi[ci] = Math.max(s, 0) / Math.max(1, m - 5);
                    if (weights != null) weights[ci] = w;
                }
                return chi;
            }
        }

        final Data coarse, full;
        final double pLo, lResolved;
        final double[] pitches;
        final int nc;
        /** coarse-data results {strands, pitch, r, a, chi per q limit...} */
        final List<double[]> results = new ArrayList<>();
        final double[] bestChi;                       // full data, per q limit
        final Object[][] bestAt;                      // {strands, pitch, r, a, weights}

        Finite(double[] q, double[] I, double[] sigma, double[] ladderQ, double[] pitches, double pLo) {
            double[][] c = rebin(q, I, sigma, 80);
            this.full = new Data(q, I, sigma, ladderQ, NL, 0);
            this.coarse = new Data(c[0], c[1], c[2], ladderQ, NS, full.L[NL - 1]);
            this.pitches = pitches;
            this.pLo = pLo;
            this.nc = ladderQ.length;
            this.lResolved = Math.sqrt(12) * 1.5 / q[0];
            bestChi = new double[nc];
            Arrays.fill(bestChi, Double.POSITIVE_INFINITY);
            bestAt = new Object[nc][];
        }

        /** exact coarse fits of the given {strands, pitch, r} geometries x strand radii */
        void survey(List<double[]> geoms, java.util.function.Function<double[], double[]> strandRadii,
                    DoubleConsumer progress, double p0, double p1) {
            int[] done = {0};
            IntStream.range(0, geoms.size()).parallel().forEach(gi -> {
                double[] g = geoms.get(gi);
                double[][] T = coarse.table((int) g[0], g[1], g[2]);
                List<double[]> out = new ArrayList<>();
                for (double a : strandRadii.apply(g)) {
                    if (a < 0) continue;
                    double[] chi = coarse.fit(T, a, null);
                    double[] rec = new double[4 + nc];
                    rec[0] = g[0]; rec[1] = g[1]; rec[2] = g[2]; rec[3] = a;
                    System.arraycopy(chi, 0, rec, 4, nc);
                    out.add(rec);
                }
                synchronized (results) {
                    results.addAll(out);
                    if (progress != null) progress.accept(p0 + (p1 - p0) * (++done[0]) / geoms.size());
                }
            });
        }

        /** the best k distinct results of q limit ci (optionally only those passing a filter) */
        List<double[]> top(int ci, int k, java.util.function.Predicate<double[]> filter) {
            List<double[]> sorted = new ArrayList<>();
            for (double[] s : results) if (filter == null || filter.test(s)) sorted.add(s);
            sorted.sort((x, y) -> Double.compare(x[4 + ci], y[4 + ci]));
            List<double[]> out = new ArrayList<>();
            for (double[] s : sorted) {
                boolean near = false;
                for (double[] o : out)
                    if (o[0] == s[0] && Math.abs(o[1] / s[1] - 1) < 0.02 && Math.abs(o[2] / s[2] - 1) < 0.04)
                        near = true;
                if (!near) out.add(s);
                if (out.size() == k) break;
            }
            return out;
        }

        private static void addGeom(List<double[]> list, double st, double p, double r) {
            for (double[] o : list)
                if (o[0] == st && Math.abs(o[1] / p - 1) < 1e-3 && Math.abs(o[2] / r - 1) < 1e-3) return;
            list.add(new double[]{st, p, r});
        }

        void run(DoubleConsumer progress) {
            double[] q = full.q;
            int n = q.length;
            double rLo = Math.max(3, 1.0 / q[n - 1]), rHi = Math.min(600, 4.0 / q[0]);
            double noLayer = pitches[0];
            // 1. cross-section, no layer line in the data
            List<double[]> geoms = new ArrayList<>();
            for (int i = 0; i < NR; i++) addGeom(geoms, 1, noLayer, rLo * Math.pow(rHi / rLo, i / (NR - 1.0)));
            survey(geoms, g -> A_GRID, progress, 0, 0.15);

            // 2. layer lines at the best radii of every q limit
            // seeds: the best radius of every q limit and the two best of the full range, distinct by > 6 %, at most 5
            List<double[]> seeds = new ArrayList<>();
            List<double[]> pool = new ArrayList<>(top(nc - 1, 2, null));
            for (int ci = nc - 2; ci >= 0; ci--) pool.addAll(top(ci, 1, null));
            for (double[] s : pool) {
                boolean near = false;
                for (double[] o : seeds) if (Math.abs(o[2] / s[2] - 1) < 0.06) near = true;
                if (!near && seeds.size() < 5) seeds.add(new double[]{1, 0, s[2]});
            }
            geoms = new ArrayList<>();
            for (double[] s : seeds)
                for (int st = 1; st <= 2; st++)
                    for (int i = 1; i < pitches.length; i++) addGeom(geoms, st, pitches[i], s[2]);
            survey(geoms, g -> A_GRID, progress, 0.15, 0.55);

            // 3. refinement: pitch (layer lines are sharp), then radius, around the best of every q limit
            List<double[]> best = new ArrayList<>();
            for (int ci = 0; ci < nc; ci++) for (double[] s : top(ci, 3, null)) best.add(s);
            geoms = new ArrayList<>();
            for (double[] s : best)
                if (s[1] >= pLo) for (int k = -4; k <= 4; k++) addGeom(geoms, s[0], s[1] * Math.pow(1.12, k / 4.0), s[2]);
            java.util.function.Function<double[], double[]> near = g -> {
                double a0 = Double.NaN, bestC = Double.POSITIVE_INFINITY;
                for (double[] s : best) if (s[0] == g[0] && Math.abs(s[2] / g[2] - 1) < 0.1 && s[4 + nc - 1] < bestC) { bestC = s[4 + nc - 1]; a0 = s[3]; }
                if (Double.isNaN(a0)) return A_GRID;
                return new double[]{a0 - 5, a0 - 2.5, a0, a0 + 2.5, a0 + 5};
            };
            survey(geoms, near, progress, 0.55, 0.7);
            List<double[]> best2 = new ArrayList<>();
            for (int ci = 0; ci < nc; ci++) for (double[] s : top(ci, 3, null)) best2.add(s);
            geoms = new ArrayList<>();
            for (double[] s : best2) for (int i = -2; i <= 2; i++) addGeom(geoms, s[0], s[1], s[2] * Math.pow(1.03, i));
            survey(geoms, g -> {
                double a0 = 20, bestC = Double.POSITIVE_INFINITY;
                for (double[] s : best2) if (s[0] == g[0] && s[1] == g[1] && s[4 + nc - 1] < bestC) { bestC = s[4 + nc - 1]; a0 = s[3]; }
                return new double[]{a0 - 4, a0 - 2, a0, a0 + 2, a0 + 4};
            }, progress, 0.7, 0.85);

            // 4. the best of every q limit on the full data
            List<double[]> cands = new ArrayList<>();
            for (int ci = 0; ci < nc; ci++)
                for (double[] s : top(ci, 2, null)) {
                    boolean dup = false;
                    for (double[] o : cands) if (o[0] == s[0] && o[1] == s[1] && o[2] == s[2] && o[3] == s[3]) dup = true;
                    if (!dup) cands.add(s);
                }
            int[] done = {0};
            IntStream.range(0, cands.size()).parallel().forEach(i -> {
                double[] s = cands.get(i);
                double[][] T = full.table((int) s[0], s[1], s[2]);
                for (double a : new double[]{s[3] - 1, s[3], s[3] + 1}) {
                    if (a < 0) continue;
                    double[][] w = new double[nc][];
                    double[] chi = full.fit(T, a, w);
                    synchronized (bestChi) {
                        for (int ci = 0; ci < nc; ci++)
                            if (chi[ci] < bestChi[ci]) { bestChi[ci] = chi[ci]; bestAt[ci] = new Object[]{(int) s[0], s[1], s[2], a, w[ci]}; }
                    }
                }
                synchronized (bestChi) {
                    if (progress != null) progress.accept(0.85 + 0.15 * (++done[0]) / cands.size());
                }
            });
        }

        Estimate estimate(int pick, double[] ladderQ, double[] ladderChi, double infChi) {
            Object[] at = bestAt[pick];
            Estimate e = new Estimate();
            e.finite = true;
            e.qmax = ladderQ[pick];
            e.ladderQ = ladderQ.clone();
            e.ladderChi2 = ladderChi.clone();
            e.chi2 = e.chi2Finite = bestChi[pick];
            e.chi2Infinite = infChi;
            e.strands = (Integer) at[0];
            double bestPitch = (Double) at[1];
            e.helixRadius = Math.round((Double) at[2]);
            e.strandRadius = Math.round((Double) at[3] * 2) / 2.0;
            e.pitchVisible = bestPitch >= pLo && e.strands * 2 * Math.PI / bestPitch < e.qmax;
            e.pitch = e.pitchVisible ? bestPitch : Double.NaN;

            // pitches about as good as the best (coarse fits at this q limit, best over r and a per (strands, pitch))
            double best = Double.POSITIVE_INFINITY;
            java.util.TreeMap<Double, Double>[] perPitch = new java.util.TreeMap[3];
            for (int s = 1; s <= 2; s++) perPitch[s] = new java.util.TreeMap<>();
            for (double[] s : results) best = Math.min(best, s[4 + pick]);
            for (double[] s : results) {
                if (s[1] < pLo || (int) s[0] * 2 * Math.PI / s[1] >= e.qmax) continue;    // no layer line inside
                perPitch[(int) s[0]].merge(Math.rint(s[1] * 10) / 10, s[4 + pick], Math::min);
            }
            double tol = Math.max(1.3 * best, best + 1);
            for (int s = 1; s <= 2; s++) {
                double lo = Double.NaN, hi = Double.NaN;
                for (java.util.Map.Entry<Double, Double> en : perPitch[s].entrySet()) {
                    boolean ok = en.getValue() <= tol;
                    if (ok) { if (Double.isNaN(lo)) lo = en.getKey(); hi = en.getKey(); }
                    else if (Double.isFinite(lo)) { e.pitchAlternatives.add(new double[]{s, Math.floor(lo), Math.ceil(hi)}); lo = hi = Double.NaN; }
                }
                if (Double.isFinite(lo)) e.pitchAlternatives.add(new double[]{s, Math.floor(lo), Math.ceil(hi)});
            }
            if (e.pitchVisible) {
                double span = 0;
                for (double[] alt : e.pitchAlternatives) span += alt[2] / alt[1] - 1;
                e.pitchDetermined = e.pitchAlternatives.size() <= 1 && span < 0.25;
                double lo = 0.85 * e.pitch, hi = 1.15 * e.pitch;
                for (double[] alt : e.pitchAlternatives)
                    if (alt[0] == e.strands && alt[1] <= e.pitch * 1.001 && alt[2] >= e.pitch * 0.999) { lo = Math.min(lo, alt[1]); hi = Math.max(hi, alt[2]); }
                e.pitchScanLo = Math.max(e.strands * 2 * Math.PI / e.qmax, lo);
                e.pitchScanHi = hi;
            } else e.pitchDetermined = false;

            // lengths: intensity weight w_L ~ N M^2 with M ~ L, so the mass fraction ~ w_L / L
            double[] w = (double[]) at[4], L = full.L;
            double[] mass = new double[NL];
            double total = 0;
            for (int k = 0; k < NL; k++) { mass[k] = w[k] / L[k]; total += mass[k]; }
            if (!(total > 0)) throw new IllegalStateException("No filament length fits the data");
            double acc = 0;
            e.lMin = L[0];
            e.lMax = L[NL - 1];
            e.lMedian = Double.NaN;
            boolean lowSet = false;
            for (int k = 0; k < NL; k++) {
                acc += mass[k];
                if (!lowSet && acc >= 0.05 * total) { e.lMin = L[Math.max(0, k - 1)]; lowSet = true; }
                if (Double.isNaN(e.lMedian) && acc >= 0.5 * total) e.lMedian = L[k];
                if (acc <= 0.95 * total) e.lMax = L[Math.min(NL - 1, k + 2)];
                if (L[k] > lResolved) e.fractionUnresolved += mass[k] / total;
            }
            e.lResolved = lResolved;
            e.lMin = Math.max(10, Math.floor(e.lMin / 10) * 10);
            e.lMax = Math.ceil(e.lMax / 10) * 10;
            // the radius range (used if the user switches to infinite filaments or scans the radius): +-25 %
            e.rMin = Math.floor(0.75 * e.helixRadius);
            e.rMax = Math.ceil(1.25 * e.helixRadius);
            e.rMean = e.helixRadius;
            return e;
        }
    }

    // ================================================================== ellipsoidal subunits

    /** starting values for an infinite helix of ellipsoidal subunits */
    public static final class SubunitEstimate {
        public double qmax, rMin, rMax, rMean;
        public double[] peaks = new double[0];
        public double a, c, volume, volumePrior = Double.NaN;
        public double pitch, perTurn;
        public int strands;
        public double chi2, chi2HelixOnly, qmaxHelixOnly;
        public double highQSplit;

        public double rise() { return pitch / perTurn; }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US, "Subunit: ellipsoid a = %.0f Å, c = %.0f Å (%s), volume %.3g Å³", a, c,
                    c > 1.15 * a ? "prolate" : c < a / 1.15 ? "oblate" : "near-spherical", volume));
            if (Double.isFinite(volumePrior))
                sb.append(String.format(Locale.US, " (%.0f %% of the expected %.3g Å³)", 100 * volume / volumePrior, volumePrior));
            sb.append(String.format(Locale.US, "%n   from q > %.3f Å⁻¹, where the subunit dominates%n", highQSplit));
            sb.append(String.format(Locale.US, "Helix radius: %.0f-%.0f Å (mean %.0f Å", rMin, rMax, rMean));
            if (peaks.length > 0) {
                sb.append("; peaks at");
                for (double p : peaks) sb.append(String.format(Locale.US, " %.0f", p));
            }
            sb.append(String.format(Locale.US, " Å)%nLattice (as given): pitch %.0f Å, %.1f subunits per turn per strand "
                    + "(rise %.1f Å, twist %.1f°), %d strand%s%n", pitch, perTurn, rise(), 360 / perTurn, strands, strands > 1 ? "s" : ""));
            sb.append(String.format(Locale.US, "Fit over the full range up to %.3f Å⁻¹: reduced χ² %.2f%n", qmax, chi2));
            sb.append(String.format(Locale.US, "   (uniform-strand helix: %.2f, but only up to q %.3f Å⁻¹)", chi2HelixOnly, qmaxHelixOnly));
            return sb.toString();
        }
    }

    /**
     * Starting values for an infinite helix of ellipsoidal subunits on a given lattice (pitch, subunits per turn,
     * strands: from cryo-EM or the literature; a 1D powder pattern does not determine them reliably on its own,
     * because the layer lines are diffuse after orientational averaging; use the profile scan to test them).
     *
     * Stage 0: the uniform-strand helix estimate (radius range, and the q above which uniform strands fail).
     * Stage 1: the subunit shape from the q above that limit, where the lattice contributes little (S ~ 1):
     *          I ~ s <|F|^2>(q; a, c) + background; the optional volume prior enters as a penalty.
     * Stage 2: the radius distribution (NNLS) over the full range for the best subunit shapes, refined locally.
     *
     * @param volumePrior expected subunit volume (Angstrom^3) or NaN; tol its relative tolerance (e.g. 0.2)
     */
    public static SubunitEstimate estimateSubunits(double[] q0, double[] I0, double[] s0, double pitch, double perTurn,
                                                   int strands, double volumePrior, double tol, DoubleConsumer progress) {
        double[][] d = rebin(q0, I0, s0, 200);
        Estimate h = estimate(d[0], d[1], d[2], progress == null ? null : f -> progress.accept(0.4 * f), true);
        double[] q = d[0], I = d[1], sigma = d[2];
        int n = q.length;

        // ---- stage 1: subunit shape from the high q
        double split = Math.min(h.qmax, q[(int) (0.6 * n)]);
        int j0 = 0;
        while (j0 < n && q[j0] <= split) j0++;
        if (n - j0 < 12) j0 = Math.max(0, n - 12);
        double[] grid = new double[40];
        for (int i = 0; i < grid.length; i++) grid[i] = 4 * Math.pow(250 / 4.0, i / (grid.length - 1.0));
        List<double[]> shapes = new ArrayList<>();        // {score, a, c}
        final int from = j0;
        for (double a : grid)
            for (double c : grid) {
                double[] fit = linearFit(q, I, sigma, from, n, k -> HelicalLattice.ellipsoid(q[k], a, c)[0]);
                shapes.add(new double[]{fit[2] + volumePenalty(a, c, volumePrior, tol), a, c});
            }
        shapes.sort((x, y) -> Double.compare(x[0], y[0]));
        List<double[]> best = new ArrayList<>();
        for (double[] sh : shapes) {                        // the 3 best distinct shapes
            boolean near = false;
            for (double[] b : best) if (Math.abs(Math.log(sh[1] / b[1])) < 0.15 && Math.abs(Math.log(sh[2] / b[2])) < 0.15) near = true;
            if (!near) best.add(sh);
            if (best.size() == 3) break;
        }
        if (progress != null) progress.accept(0.7);

        // ---- stage 2: radius distribution on the given lattice, shapes refined around the best three
        // radii: at high q the lattice terms oscillate in r with period ~ 2 pi / q, so steps of ~ pi / (4 q max)
        double qTop = q[n - 1];
        double rLo = Math.max(5, 0.6 * h.rMin), rHi = Math.min(1500, 1.4 * h.rMax);
        List<Double> rl = new ArrayList<>();
        for (double r = rLo; r <= rHi && rl.size() < 160; r += Math.max(Math.min(0.06 * r, Math.PI / (4 * qTop)), (rHi - rLo) / 159.0)) rl.add(r);
        double[] radii = rl.stream().mapToDouble(Double::doubleValue).toArray();
        List<double[]> geom = new ArrayList<>();
        geom.add(new double[]{strands, pitch, perTurn});
        double[][][] S = new double[1][][];
        List<double[]> local = new ArrayList<>();
        for (double[] b : best)
            for (int i = -2; i <= 2; i++) for (int k = -2; k <= 2; k++)
                local.add(new double[]{0, b[1] * Math.pow(1.07, i), b[2] * Math.pow(1.07, k)});
        Object[] fit = latticeSurvey(q, I, sigma, radii, geom, local, volumePrior, tol, S, null, 0, 0);
        if (progress != null) progress.accept(0.85);
        double[] sb = (double[]) fit[2];
        List<double[]> polish = new ArrayList<>();
        for (int i = -3; i <= 3; i++) for (int k = -3; k <= 3; k++)
            polish.add(new double[]{0, sb[1] * Math.pow(1.02, i), sb[2] * Math.pow(1.02, k)});
        Object[] fit2 = latticeSurvey(q, I, sigma, radii, geom, polish, volumePrior, tol, S, null, 0, 0);
        if ((Double) fit2[0] < (Double) fit[0]) fit = fit2;
        if (progress != null) progress.accept(1.0);

        SubunitEstimate e = new SubunitEstimate();
        double[] sh = (double[]) fit[2], w = (double[]) fit[3];
        e.chi2 = (Double) fit[4];
        e.strands = strands;
        e.pitch = pitch;
        e.perTurn = perTurn;
        e.a = sh[1];
        e.c = sh[2];
        e.volume = HelicalLattice.ellipsoidVolume(e.a, e.c);
        e.volumePrior = volumePrior;
        e.qmax = q[n - 1];
        e.highQSplit = split;
        e.chi2HelixOnly = h.chi2;
        e.qmaxHelixOnly = h.qmax;
        radiusStats(radii, w, e);
        return e;
    }

    static double volumePenalty(double a, double c, double volumePrior, double tol) {
        if (!Double.isFinite(volumePrior)) return 0;
        double dv = (HelicalLattice.ellipsoidVolume(a, c) - volumePrior) / (tol * volumePrior);
        return dv * dv;
    }

    private static void radiusStats(double[] radii, double[] w, SubunitEstimate e) {
        int nr = radii.length;
        double total = 0, mean = 0;
        for (int i = 0; i < nr; i++) { total += w[i]; mean += w[i] * radii[i]; }   // mass ~ weight (same subunits)
        e.rMean = total > 0 ? mean / total : Double.NaN;
        double acc = 0;
        e.rMin = radii[0];
        e.rMax = radii[nr - 1];
        boolean lowSet = false;
        for (int i = 0; i < nr; i++) {
            acc += w[i];
            if (!lowSet && acc >= 0.05 * total) { e.rMin = radii[Math.max(0, i - 1)]; lowSet = true; }
            if (acc <= 0.95 * total) e.rMax = radii[Math.min(nr - 1, i + 2)];
        }
        e.rMin = Math.max(1, Math.floor(e.rMin * 0.9 / 5) * 5);
        e.rMax = Math.ceil(e.rMax * 1.1 / 5) * 5;
        List<Double> pk = new ArrayList<>();
        double mx = Arrays.stream(w, 0, nr).max().orElse(0);
        for (int i = 0; i < nr; i++) {
            boolean up = i == 0 || w[i] > w[i - 1], down = i == nr - 1 || w[i] >= w[i + 1];
            if (up && down && w[i] > 0.1 * mx) pk.add(radii[i]);
        }
        e.peaks = pk.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * NNLS over all geometries x subunit shapes on the full range, ranked by reduced chi^2 plus the volume penalty;
     * returns {score, geometry, shape, weights, reduced chi^2}
     */
    private static Object[] latticeSurvey(double[] q, double[] I, double[] sigma, double[] radii, List<double[]> geoms,
                                          List<double[]> shapes, double volumePrior, double tol, double[][][] cache,
                                          DoubleConsumer progress, double p0, double p1) {
        int n = q.length, nr = radii.length;
        double[][][] ell = new double[shapes.size()][n][];
        for (int s = 0; s < shapes.size(); s++)
            for (int j = 0; j < n; j++) ell[s][j] = HelicalLattice.ellipsoid(q[j], shapes.get(s)[1], shapes.get(s)[2]);
        double[] b = new double[n];
        for (int j = 0; j < n; j++) b[j] = I[j] / sigma[j];
        Object[][] best = {{Double.POSITIVE_INFINITY, null, null, null, Double.NaN}};
        int[] done = {0};
        Object lock = new Object();
        IntStream.range(0, geoms.size()).forEach(gi -> {
            double[] g = geoms.get(gi);
            double[] psi = HelicalLattice.symmetricStrands((int) g[0]);
            double rise = g[1] / g[2];
            double[][] S;
            HelicalLattice.prepare(q[n - 1] * radii[nr - 1]);
            if (cache != null && cache[0] != null) S = cache[0];
            else {
                double[][] fresh = new double[n][nr];
                IntStream.range(0, n).parallel().forEach(j -> {
                    for (int i = 0; i < nr; i++) fresh[j][i] = HelicalLattice.structureFactor(q[j], radii[i], g[1], rise, psi);
                });
                S = fresh;
                if (cache != null) cache[0] = S;
            }
            IntStream.range(0, shapes.size()).parallel().forEach(s -> {
                double[][] A = new double[n][nr + 1];
                for (int j = 0; j < n; j++) {
                    double f2 = ell[s][j][0], f1 = ell[s][j][1];
                    for (int i = 0; i < nr; i++) A[j][i] = (f2 + f1 * (S[j][i] - 1)) / sigma[j];
                    A[j][nr] = 1.0 / sigma[j];
                }
                double[] w = nnls(A, b, n);
                double chi = 0;
                for (int j = 0; j < n; j++) {
                    double f = 0;
                    for (int i = 0; i <= nr; i++) f += A[j][i] * w[i];
                    chi += (b[j] - f) * (b[j] - f);
                }
                chi /= Math.max(1, n - 6);
                double score = chi + volumePenalty(shapes.get(s)[1], shapes.get(s)[2], volumePrior, tol);
                synchronized (lock) {
                    if (score < (Double) best[0][0]) best[0] = new Object[]{score, g.clone(), shapes.get(s).clone(), w, chi};
                }
            });
            if (progress != null) synchronized (lock) { progress.accept(p0 + (p1 - p0) * (++done[0]) / geoms.size()); }
        });
        return best[0];
    }

    /** weighted fit I ~ s f(q) + bg (s, bg >= 0) on rows [from, to); returns {s, bg, chi2 reduced} */
    private static double[] linearFit(double[] q, double[] I, double[] sigma, int from, int to,
                                      java.util.function.IntToDoubleFunction f) {
        int m = to - from;
        double[][] A = new double[m][2];
        double[] b = new double[m];
        for (int k = 0; k < m; k++) {
            int j = from + k;
            A[k][0] = f.applyAsDouble(j) / sigma[j];
            A[k][1] = 1.0 / sigma[j];
            b[k] = I[j] / sigma[j];
        }
        double[] x = nnls(A, b, m);
        double chi = 0;
        for (int k = 0; k < m; k++) {
            double r = b[k] - A[k][0] * x[0] - A[k][1] * x[1];
            chi += r * r;
        }
        return new double[]{x[0], x[1], chi / Math.max(1, m - 2)};
    }

    /** log-spaced rebinning to at most maxPoints (error-weighted means) */
    static double[][] rebin(double[] q, double[] I, double[] s, int maxPoints) {
        int n = q.length;
        if (n <= maxPoints) return new double[][]{q.clone(), I.clone(), s.clone()};
        double lo = Math.log(q[0]), hi = Math.log(q[n - 1]) + 1e-12;
        List<double[]> out = new ArrayList<>();
        int j = 0;
        for (int b = 0; b < maxPoints; b++) {
            double edge = lo + (hi - lo) * (b + 1) / maxPoints;
            double sw = 0, swq = 0, swi = 0;
            while (j < n && Math.log(q[j]) <= edge) {
                double w = 1 / (s[j] * s[j]);
                sw += w; swq += w * q[j]; swi += w * I[j];
                j++;
            }
            if (sw > 0) out.add(new double[]{swq / sw, swi / sw, Math.sqrt(1 / sw)});
        }
        double[][] r = new double[3][out.size()];
        for (int k = 0; k < out.size(); k++) for (int c = 0; c < 3; c++) r[c][k] = out.get(k)[c];
        return r;
    }

    /** NNLS fits of a list of (strands, pitch) x strand radii, keeping the best per q limit */
    private static final class Survey {
        final double[] q, sigma, b, radii;
        final List<Integer> cut;
        final double[] bestChi;
        final Object[][] bestAt;
        /** {strands, pitch, chi2 at the largest q limit} for every geometry run (best over strand radii) */
        final List<double[]> full = new ArrayList<>();

        Survey(double[] q, double[] sigma, double[] b, double[] radii, List<Integer> cut, double[] bestChi, Object[][] bestAt) {
            this.q = q; this.sigma = sigma; this.b = b; this.radii = radii; this.cut = cut; this.bestChi = bestChi; this.bestAt = bestAt;
        }

        void run(List<double[]> geometries, double[] strandRadii, DoubleConsumer progress, double p0, double p1) {
            int n = q.length, nr = radii.length, jobs = geometries.size();
            int[] done = {0};
            IntStream.range(0, jobs).parallel().forEach(job -> {
                int strands = (int) geometries.get(job)[0];
                double pitch = geometries.get(job)[1];
                double[][] base = new double[n][nr];
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < nr; i++) {
                        HelixMath.Geometry g = HelixMath.Geometry.symmetric(radii[i], pitch, 0, strands);
                        base[j][i] = Math.PI / (q[j] * InfiniteHelixKernel.L_REF) * HelixMath.limitSum(g, q[j]);
                    }
                double fullBest = Double.POSITIVE_INFINITY;
                int K = nr + 1;
                double[] row = new double[K];
                double[] as = geometries.get(job).length > 2
                        ? Arrays.copyOfRange(geometries.get(job), 2, geometries.get(job).length) : strandRadii;
                for (double a : as) {
                    // normal equations accumulated row by row; every q limit is a prefix
                    double[][] G = new double[K][K];
                    double[] h = new double[K];
                    double bb = 0;
                    int j = 0;
                    for (int c = 0; c < cut.size(); c++) {
                        int m = cut.get(c);
                        for (; j < m; j++) {
                            double cs = Math.exp(-q[j] * q[j] * a * a / 4) / sigma[j];
                            for (int i = 0; i < nr; i++) row[i] = base[j][i] * cs;
                            row[nr] = 1.0 / sigma[j];                        // background
                            bb += b[j] * b[j];
                            for (int x = 0; x < K; x++) {
                                double rx = row[x];
                                if (rx == 0) continue;
                                h[x] += rx * b[j];
                                double[] Gx = G[x];
                                for (int y = x; y < K; y++) Gx[y] += rx * row[y];
                            }
                        }
                        double[][] Gs = new double[K][K];
                        for (int x = 0; x < K; x++) for (int y = x; y < K; y++) Gs[x][y] = Gs[y][x] = G[x][y];
                        double[] w = nnlsGram(Gs, h);
                        double chi = bb;
                        for (int x = 0; x < K; x++) {
                            if (w[x] == 0) continue;
                            chi -= 2 * w[x] * h[x];
                            for (int y = 0; y < K; y++) chi += w[x] * Gs[x][y] * w[y];
                        }
                        chi = Math.max(chi, 0) / Math.max(1, m - 4);
                        synchronized (this) {
                            if (chi < bestChi[c]) { bestChi[c] = chi; bestAt[c] = new Object[]{strands, pitch, a, w, radii}; }
                        }
                        if (c == cut.size() - 1) fullBest = Math.min(fullBest, chi);
                    }
                }
                synchronized (this) { full.add(new double[]{strands, pitch, fullBest}); }
                if (progress != null) synchronized (this) { progress.accept(p0 + (p1 - p0) * (++done[0]) / jobs); }
            });
        }
    }

    /**
     * Non-negative least squares (Lawson-Hanson) on the first m rows of A: minimise |A x - b| with x >= 0.
     * Small problems (tens of unknowns): normal equations on the passive set, solved by Cholesky.
     */
    static double[] nnls(double[][] A, double[] b, int m) {
        int k = A[0].length;
        double[][] G = new double[k][k];
        double[] h = new double[k];
        for (int j = 0; j < m; j++) {
            double[] row = A[j];
            for (int a = 0; a < k; a++) {
                double ra = row[a];
                if (ra == 0) continue;
                h[a] += ra * b[j];
                for (int c = a; c < k; c++) G[a][c] += ra * row[c];
            }
        }
        for (int a = 0; a < k; a++) for (int c = 0; c < a; c++) G[a][c] = G[c][a];
        return nnlsGram(G, h);
    }

    /** NNLS from the normal equations: minimise x'Gx - 2 h'x with x >= 0 (G symmetric, both filled) */
    static double[] nnlsGram(double[][] G, double[] h) {
        int k = h.length;
        double scale = 0;
        for (int a = 0; a < k; a++) scale = Math.max(scale, G[a][a]);
        double tol = 1e-12 * Math.max(scale, 1e-300);

        double[] x = new double[k];
        boolean[] passive = new boolean[k];
        for (int iter = 0; iter < 3 * k + 30; iter++) {
            // gradient w = h - G x
            int best = -1;
            double bestW = 1e-10 * Math.max(1, Math.abs(maxAbs(h)));
            for (int a = 0; a < k; a++) {
                if (passive[a]) continue;
                double wv = h[a];
                for (int c = 0; c < k; c++) wv -= G[a][c] * x[c];
                if (wv > bestW) { bestW = wv; best = a; }
            }
            if (best < 0) break;
            passive[best] = true;
            while (true) {
                double[] z = solvePassive(G, h, passive, tol);
                boolean ok = true;
                for (int a = 0; a < k; a++) if (passive[a] && z[a] <= 0) { ok = false; break; }
                if (ok) { x = z; break; }
                double alpha = 1;
                for (int a = 0; a < k; a++)
                    if (passive[a] && z[a] <= 0) alpha = Math.min(alpha, x[a] / (x[a] - z[a]));
                for (int a = 0; a < k; a++) {
                    x[a] += alpha * (z[a] - x[a]);
                    if (passive[a] && x[a] <= 1e-15 * Math.max(1, Math.abs(x[a]))) { passive[a] = false; x[a] = 0; }
                }
            }
        }
        return x;
    }

    private static double maxAbs(double[] v) {
        double m = 0;
        for (double d : v) m = Math.max(m, Math.abs(d));
        return m;
    }

    /** solves G_PP z_P = h_P (Cholesky with a tiny ridge), z = 0 outside P */
    private static double[] solvePassive(double[][] G, double[] h, boolean[] passive, double tol) {
        int k = G.length, p = 0;
        int[] idx = new int[k];
        for (int a = 0; a < k; a++) if (passive[a]) idx[p++] = a;
        double[][] L = new double[p][p];
        for (int i = 0; i < p; i++)
            for (int j = 0; j <= i; j++) {
                double s = G[idx[i]][idx[j]] + (i == j ? tol : 0);
                for (int t = 0; t < j; t++) s -= L[i][t] * L[j][t];
                L[i][j] = i == j ? Math.sqrt(Math.max(s, tol)) : s / L[j][j];
            }
        double[] y = new double[p];
        for (int i = 0; i < p; i++) {
            double s = h[idx[i]];
            for (int t = 0; t < i; t++) s -= L[i][t] * y[t];
            y[i] = s / L[i][i];
        }
        double[] zp = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double s = y[i];
            for (int t = i + 1; t < p; t++) s -= L[t][i] * zp[t];
            zp[i] = s / L[i][i];
        }
        double[] z = new double[k];
        for (int i = 0; i < p; i++) z[idx[i]] = zp[i];
        return z;
    }
}
