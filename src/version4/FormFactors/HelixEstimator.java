package version4.FormFactors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;

/**
 * Starting values for infinitely long helices, straight from the data, so the user does not have to guess them.
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
        public String summary() {
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
    static Estimate estimate(double[] q, double[] I, double[] sigma, DoubleConsumer progress, boolean quick) {
        int n = q.length;
        if (n < 20) throw new IllegalArgumentException("Too few points for an estimate");
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
        List<double[]> fine = new ArrayList<>();
        List<Double> aFine = new ArrayList<>();
        for (double a : STRAND_RADII) aFine.add(a);
        // candidates: the best of every q limit, plus the 4 best pitches per strand count over the full range
        List<Object[]> cands = new ArrayList<>();
        for (Object[] at : bestAt) if (at != null) cands.add(at);
        for (int st : strandsList) {
            sv.full.stream().filter(g -> g[0] == st && g[1] >= pLo).sorted((x, y) -> Double.compare(x[2], y[2])).limit(4)
                    .forEach(g -> cands.add(new Object[]{st, g[1], 20.0, null, null}));
        }
        for (Object[] at : cands) {
            int st = (Integer) at[0];
            double p0 = (Double) at[1], a0 = (Double) at[2];
            for (double da = -7.5; da <= 7.5; da += 2.5) if (a0 + da >= 0 && !aFine.contains(a0 + da)) aFine.add(a0 + da);
            if (p0 < pLo) continue;                          // "no layer line": nothing to refine
            for (int k = -10; k <= 10; k++) {
                double p = p0 * Math.pow(1.18, k / 10.0);
                boolean dup = false;
                for (double[] f : fine) if (f[0] == st && Math.abs(f[1] / p - 1) < 1e-3) dup = true;
                if (!dup) fine.add(new double[]{st, p});
            }
        }
        double[] aArr = aFine.stream().mapToDouble(Double::doubleValue).sorted().toArray();
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
            for (Object[] at : bestAt) if (at != null) {
                double[] g = {(Integer) at[0], (Double) at[1]};
                boolean dup = false;
                for (double[] f : fine) if (f[0] == g[0] && Math.abs(f[1] / g[1] - 1) < 1e-3) dup = true;
                if (!dup) fine.add(g);
            }
            new Survey(q, sigma, b, fineRadii, cut, bestChi, bestAt).run(fine, aArr, progress, 0.6, 0.85);

            // 3. polish: high-order layer lines need the pitch to ~0.3 % and the strand radius to ~1 A
            List<double[]> polish = new ArrayList<>();
            List<Double> aPol = new ArrayList<>();
            for (Object[] at : bestAt) {
                if (at == null) continue;
                int st = (Integer) at[0];
                double p0 = (Double) at[1], a0 = (Double) at[2];
                for (double da = -3; da <= 3; da += 1) if (a0 + da >= 0 && !aPol.contains(a0 + da)) aPol.add(a0 + da);
                if (p0 < pLo) continue;
                for (int k = -12; k <= 12; k++) {
                    double p = p0 * (1 + 0.003 * k);
                    boolean dup = false;
                    for (double[] f : polish) if (f[0] == st && Math.abs(f[1] / p - 1) < 1e-4) dup = true;
                    if (!dup) polish.add(new double[]{st, p});
                }
            }
            if (!polish.isEmpty())
                new Survey(q, sigma, b, fineRadii, cut, bestChi, bestAt)
                        .run(polish, aPol.stream().mapToDouble(Double::doubleValue).sorted().toArray(), progress, 0.85, 1.0);
        }

        return finish(q, n, cut, bestChi, bestAt, pLo);
    }

    private static Estimate finish(double[] q, int n, List<Integer> cut, double[] bestChi, Object[][] bestAt, double pLo) {
        // largest q limit whose fit stays close to the best of the ladder
        double floor = Arrays.stream(bestChi).min().orElse(Double.NaN);
        int pick = 0;
        for (int c = 0; c < cut.size(); c++) if (bestChi[c] <= Math.max(2 * floor, floor + 1.5)) pick = c;

        Estimate e = new Estimate();
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
                for (double a : strandRadii) {
                    double[][] A = new double[n][nr + 1];
                    for (int j = 0; j < n; j++) {
                        double cs = Math.exp(-q[j] * q[j] * a * a / 4);
                        for (int i = 0; i < nr; i++) A[j][i] = base[j][i] * cs / sigma[j];
                        A[j][nr] = 1.0 / sigma[j];                       // background
                    }
                    for (int c = 0; c < cut.size(); c++) {
                        int m = cut.get(c);
                        double[] w = nnls(A, b, m);
                        double chi = 0;
                        for (int j = 0; j < m; j++) {
                            double f = 0;
                            for (int i = 0; i <= nr; i++) f += A[j][i] * w[i];
                            chi += (b[j] - f) * (b[j] - f);
                        }
                        chi /= Math.max(1, m - 4);
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
