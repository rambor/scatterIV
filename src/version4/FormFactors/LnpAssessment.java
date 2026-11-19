package version4.FormFactors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;

/**
 * Model-free first look at a lipid-nanoparticle SAXS curve, meant for users who do not yet know what is in their
 * sample:
 * <ul>
 *   <li>Guinier fit at low q, or a statement that the particles are too large for the measured q range;</li>
 *   <li>detection of peaks above a smooth baseline at higher q (internal structure);</li>
 *   <li>indexing of the peaks against the common lipid mesophases (Caselli, Conti, De Santis &amp; Berti,
 *       Adv. Colloid Interface Sci. 327, 103156 (2024), Table 1): lamellar, inverse hexagonal and the cubic
 *       phases Pn3m, Im3m, Ia3d and Fd3m;</li>
 *   <li>recognition of the single broad peak (d ~ 4-7 nm) of nucleic-acid / ionizable-lipid assemblies in
 *       mRNA-LNPs (Li et al., Membranes 15, 153 (2025)).</li>
 * </ul>
 */
public final class LnpAssessment {

    // ------------------------------------------------------------------ results

    public static final class Guinier {
        public double rg, rgErr, i0, i0Err, qmin, qmax;
        public int points;
        public boolean found;
    }

    public static final class Peak {
        public double q, height, fwhm, significance;   // height above baseline (intensity units), significance in sigma
        /** integrated intensity above the baseline (I x q units); tracks how much ordered structure there is */
        public double area;
        public double d() { return 2 * Math.PI / q; }
        /** rough number of correlated repeats, q / FWHM */
        public double order() { return fwhm > 0 ? q / fwhm : Double.NaN; }
    }

    public static final class PhaseMatch {
        public String phase, symmetry, lattice;
        public double latticeParameter, meanError;
        public int matched;
        /** allowed reflections between the first and last observed peak that were not observed */
        public int missing;
        /** weak peaks this phase does not explain */
        public int unexplained;
        public String[] indices;   // per detected peak, Miller index or null
        /** indices into Report.peaks of the peaks this match explains (all peaks for a single-phase match) */
        public int[] peakIndex;
        public boolean isLamellar() { return phase.startsWith("Lamellar"); }
        public boolean isHexagonal() { return phase.contains("hexagonal"); }
    }

    public static final class Report {
        public Guinier guinier;
        public List<Peak> peaks = new ArrayList<>();
        public List<PhaseMatch> phases = new ArrayList<>();
        public double[] baselineQ, baseline;   // smooth baseline used for peak search
        /** sharp peaks at d = 3.38 / 1.69 nm: cholesterol monohydrate crystals, removed before indexing */
        public List<Peak> cholesterol = new ArrayList<>();
        /** two coexisting lattices that together explain all peaks, when no single phase does (else null) */
        public PhaseMatch[] coexistence;
        /** other two-lattice explanations that fit about as well */
        public List<PhaseMatch[]> coexistenceAlternatives = new ArrayList<>();
        public double searchFrom;
        public boolean mesophasePeak;          // single broad peak typical of mRNA-lipid assemblies
        /**
         * One broad peak at a large repeat with no higher orders: the signature of a sponge (L3) phase, panel G of
         * Fig. 1 of Caselli et al. (2024). Distinguished from {@link #mesophasePeak} by the repeat distance, which is
         * 10 nm and up for a sponge against roughly 5 nm for nucleic acid packed with ionizable lipid.
         */
        public boolean spongeCandidate;
        /**
         * The broad maximum is a lobe of the particle's own form factor rather than a repeat distance. A bilayer
         * or a vesicle shell produces one in the same q window as a nucleic-acid mesophase, so breadth alone does
         * not tell them apart: what does is that a lobe comes with the other oscillations of the same form factor
         * and has no second order at twice its position.
         */
        public boolean formFactorLobe;
        /** whether the user said the sample carries nucleic acid; without it no RNA reading is offered */
        public boolean cargoDeclared = true;
        /** evenly spaced maxima recognised as form-factor oscillations (not internal structure) */
        public List<Peak> oscillations = new ArrayList<>();
        /** outer radius from the oscillation period, pi / spacing (NaN when none) */
        public double oscillationRadius = Double.NaN;
        /**
         * Minima in q⁴·I, which is how form-factor oscillations show themselves whatever their origin: a sphere's
         * envelope, a vesicle shell or a single bilayer. Counted once and used both in the text and in deciding
         * whether a broad maximum is a lobe of that form factor rather than a repeat distance.
         */
        public int porodMinima;
        public String text;
    }

    // ------------------------------------------------------------------ phases (Caselli et al. 2024, Table 1)

    private static final class Phase {
        final String name, symmetry;
        final double[] ratios;   // peak positions relative to the first allowed reflection
        final String[] hkl;
        final double[] m;        // sqrt(h^2+k^2+l^2) (cubic), sqrt(h^2+hk+k^2) (hex) or l (lamellar)
        Phase(String name, String symmetry, String[] hkl, double[] m) {
            this.name = name; this.symmetry = symmetry; this.hkl = hkl; this.m = m;
            ratios = new double[m.length];
            for (int i = 0; i < m.length; i++) ratios[i] = m[i] / m[0];
        }
    }

    private static double[] sq(double... v) { double[] o = new double[v.length]; for (int i = 0; i < v.length; i++) o[i] = Math.sqrt(v[i]); return o; }

    private static final Phase[] PHASES = {
            new Phase("Lamellar (multilamellar / onion)", "Lα / Lβ",
                    new String[]{"(001)", "(002)", "(003)", "(004)", "(005)", "(006)"}, new double[]{1, 2, 3, 4, 5, 6}),
            new Phase("Inverse hexagonal (hexosome)", "p6m, HII",
                    new String[]{"(10)", "(11)", "(20)", "(21)", "(30)", "(22)"}, sq(1, 3, 4, 7, 9, 12)),
            new Phase("Bicontinuous cubic (cubosome)", "Pn3m, Q224",
                    new String[]{"(110)", "(111)", "(200)", "(211)", "(220)", "(221)", "(310)"}, sq(2, 3, 4, 6, 8, 9, 10)),
            new Phase("Bicontinuous cubic (cubosome)", "Im3m, Q229",
                    new String[]{"(110)", "(200)", "(211)", "(220)", "(310)", "(222)"}, sq(2, 4, 6, 8, 10, 12)),
            new Phase("Bicontinuous cubic (cubosome)", "Ia3d, Q230",
                    new String[]{"(211)", "(220)", "(321)", "(400)", "(420)", "(332)"}, sq(6, 8, 14, 16, 20, 22)),
            new Phase("Micellar cubic", "Fd3m, Q227",
                    new String[]{"(111)", "(220)", "(311)", "(222)", "(400)", "(331)"}, sq(3, 8, 11, 12, 16, 19)),
    };

    // ------------------------------------------------------------------ analysis

    /**
     * @param q, I, s   data (ascending q, sigma &gt; 0)
     * @param searchFrom lowest q for the peak search (form-factor oscillations of large particles sit below it);
     *                   NaN picks 0.05 1/A or the middle of the range, whichever is lower
     */
    public static Report analyse(double[] q, double[] I, double[] s, double searchFrom) {
        return analyse(q, I, s, searchFrom, true);
    }

    public static Report analyse(double[] q, double[] I, double[] s, double searchFrom, boolean cargoDeclared) {
        Report r = new Report();
        r.guinier = guinier(q, I, s);
        double qmaxData = q[q.length - 1];
        if (Double.isNaN(searchFrom)) searchFrom = Math.min(0.05, Math.sqrt(q[0] * qmaxData));
        r.searchFrom = searchFrom;
        findPeaks(q, I, s, searchFrom, r);
        separateOscillations(r);
        separateCholesterol(r);
        indexPeaks(r);
        r.cargoDeclared = cargoDeclared;
        r.mesophasePeak = r.peaks.size() >= 1 && r.phases.isEmpty() && r.coexistence == null && isMesophase(r.peaks);
        if (r.mesophasePeak && looksLikeFormFactorLobe(r, q, I)) {
            r.formFactorLobe = true;
            r.mesophasePeak = false;
        }
        if (r.peaks.size() == 1 && r.phases.isEmpty() && r.coexistence == null) {
            Peak only = r.peaks.get(0);
            // broad (order counts the correlated repeats) and at a repeat too large for the nucleic-acid core
            r.spongeCandidate = only.d() > 80 && (Double.isNaN(only.order()) || only.order() < 4);
            if (r.spongeCandidate) r.mesophasePeak = false;
        }
        r.text = describe(r, q, I);
        return r;
    }

    /** longest low-q window with qmax*Rg &lt;= 1.3 and a straight Guinier plot */
    static Guinier guinier(double[] q, double[] I, double[] s) {
        Guinier best = new Guinier();
        int n = q.length;
        int startMax = Math.min(n - 8, 15);
        for (int a = 0; a <= startMax; a++) {
            for (int b = a + 7; b < n; b++) {
                double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
                boolean ok = true;
                for (int j = a; j <= b; j++) {
                    if (!(I[j] > 0)) { ok = false; break; }
                    double x = q[j] * q[j], y = Math.log(I[j]), w = (I[j] / s[j]) * (I[j] / s[j]);
                    sw += w; sx += w * x; sy += w * y; sxx += w * x * x; sxy += w * x * y;
                }
                if (!ok) break;
                double det = sw * sxx - sx * sx;
                if (det <= 0) continue;
                double slope = (sw * sxy - sx * sy) / det, icpt = (sxx * sy - sx * sxy) / det;
                if (slope >= 0) continue;
                double rg = Math.sqrt(-3 * slope);
                if (q[b] * rg > 1.3) break;
                // straightness: reduced chi^2 of the line
                double chi = 0;
                for (int j = a; j <= b; j++) {
                    double rr = (Math.log(I[j]) - (icpt + slope * q[j] * q[j])) * I[j] / s[j];
                    chi += rr * rr;
                }
                chi /= (b - a + 1 - 2);
                if (chi > 3) continue;
                int pts = b - a + 1;
                if (!best.found || pts > best.points) {
                    best.found = true;
                    best.points = pts;
                    best.rg = rg;
                    best.i0 = Math.exp(icpt);
                    double vs = sw / det, vi = sxx / det;
                    best.rgErr = 1.5 / rg * Math.sqrt(vs);
                    best.i0Err = best.i0 * Math.sqrt(vi);
                    best.qmin = q[a]; best.qmax = q[b];
                }
            }
        }
        return best;
    }

    /** iterative (peak-excluding) cubic baseline in log I vs log q; peaks are runs well above it */
    static void findPeaks(double[] q, double[] I, double[] s, double from, Report r) {
        List<Integer> use = new ArrayList<>();
        for (int j = 0; j < q.length; j++) if (q[j] >= from && I[j] > 0) use.add(j);
        if (use.size() < 15) { r.baselineQ = new double[0]; r.baseline = new double[0]; return; }
        int n = use.size();
        double[] x = new double[n], y = new double[n], w = new double[n];
        for (int i = 0; i < n; i++) {
            int j = use.get(i);
            x[i] = Math.log(q[j]);
            y[i] = Math.log(I[j]);
            w[i] = Math.min(1e8, (I[j] / s[j]) * (I[j] / s[j]));
        }
        boolean[] mask = new boolean[n];
        Arrays.fill(mask, true);
        double[] base = new double[n];
        int degree = 3;
        for (int it = 0; it < 30; it++) {
            double[] c = polyfit(x, y, w, mask, degree);
            boolean changed = false;
            for (int i = 0; i < n; i++) base[i] = polyval(c, x[i]);
            for (int i = 0; i < n; i++) {
                double z = (y[i] - base[i]) * Math.sqrt(w[i]);
                boolean keep = z < 2.0;
                if (mask[i] && !keep) { mask[i] = false; changed = true; }
            }
            if (!changed) break;
        }
        r.baselineQ = new double[n];
        r.baseline = new double[n];
        for (int i = 0; i < n; i++) { r.baselineQ[i] = q[use.get(i)]; r.baseline[i] = Math.exp(base[i]); }

        // excess intensity and its significance, smoothed over 5 points
        double[] ex = new double[n], sig = new double[n];
        for (int i = 0; i < n; i++) { int j = use.get(i); ex[i] = I[j] - Math.exp(base[i]); sig[i] = s[j]; }
        double[] z = new double[n];
        for (int i = 0; i < n; i++) {
            double e = 0, v = 0;
            int c = 0;
            for (int k = Math.max(0, i - 2); k <= Math.min(n - 1, i + 2); k++) { e += ex[k]; v += sig[k] * sig[k]; c++; }
            z[i] = e / Math.sqrt(v);   // significance of the summed excess
        }
        int i = 0;
        while (i < n) {
            if (z[i] < 6) { i++; continue; }
            int a = i;
            while (i < n && z[i] >= 2) i++;
            int b = i - 1;
            if (b - a < 2) continue;
            if (a <= 2 || b >= n - 3) continue;   // touches the edge of the search window: baseline artefact
            List<Integer> maxima = maximaIn(ex, sig, a, b);
            for (int mi = 0; mi < maxima.size(); mi++) {
            int m = maxima.get(mi);
            // this maximum's own basin: out to the lowest point between it and the next maximum along, or to the
            // edge of the region when there is none. Width and area are measured inside the basin, so a peak
            // sharing a region with its neighbour reports its own shape rather than the pair's.
            int left = a, right = b;
            if (mi > 0) {
                int prev = maxima.get(mi - 1), v = prev;
                for (int k = prev; k <= m; k++) if (ex[k] < ex[v]) v = k;
                left = v;
            }
            if (mi < maxima.size() - 1) {
                int next = maxima.get(mi + 1), v = m;
                for (int k = m; k <= next; k++) if (ex[k] < ex[v]) v = k;
                right = v;
            }
            Peak p = new Peak();
            // parabolic refinement of the maximum
            double qm = q[use.get(m)];
            if (m > 0 && m < n - 1) {
                double y0 = ex[m - 1], y1 = ex[m], y2 = ex[m + 1];
                double den = y0 - 2 * y1 + y2;
                if (den < 0) {
                    double dx = 0.5 * (y0 - y2) / den;
                    if (Math.abs(dx) < 1) {
                        double qa = q[use.get(m - 1)], qb = q[use.get(m + 1)];
                        qm = q[use.get(m)] + dx * (dx > 0 ? qb - q[use.get(m)] : q[use.get(m)] - qa);
                    }
                }
            }
            p.q = qm;
            p.height = ex[m];
            // the significance of this maximum, not of the region it shares with others: a region wide enough to
            // hold two reflections also holds the noise between them, and that must not inherit their standing
            double zmax = 0;
            for (int k = Math.max(a, m - 2); k <= Math.min(b, m + 2); k++) zmax = Math.max(zmax, z[k]);
            if (zmax < 6) continue;
            p.significance = zmax;
            // FWHM from half-height crossings of the excess
            double half = 0.5 * ex[m];
            int lo = m, hi = m;
            // bounded to this region so a peak sharing one with its neighbour cannot take the whole span as its
            // width, but without a monotonicity test, which would stop at the first noise wiggle and make a broad
            // correlation peak look narrow
            while (lo > left && ex[lo] > half) lo--;
            while (hi < right && ex[hi] > half) hi++;
            double qlo = interp(q[use.get(lo)], ex[lo], q[use.get(Math.min(lo + 1, n - 1))], ex[Math.min(lo + 1, n - 1)], half);
            double qhi = interp(q[use.get(Math.max(hi - 1, 0))], ex[Math.max(hi - 1, 0)], q[use.get(hi)], ex[hi], half);
            p.fwhm = Math.max(qhi - qlo, 0);

            // A peak has to show curvature in the measured curve, not merely stand above a fitted baseline.
            // Comparing the maximum with the chord joining the points one half-width away on each side removes
            // whatever smooth slope the background has: a real peak rises above that chord, a stretch of curve the
            // baseline polynomial happens to undershoot does not. Without this a smooth descending region offers
            // up a noise ripple with a convincing significance, and it then gets indexed as a reflection.
            // a full width out on each side, where even a broad lobe has fallen to a few per cent of its height;
            // half a width still sits high on a wide feature and would reject real ones
            int reach = Math.max(3, hi - lo);
            int li = Math.max(0, m - reach), ri = Math.min(n - 1, m + reach);
            double top = I[use.get(m)], chord = 0.5 * (I[use.get(li)] + I[use.get(ri)]);
            double sm = s[use.get(m)], sl = s[use.get(li)], sr = s[use.get(ri)];
            if (top - chord < 4 * Math.sqrt(sm * sm + 0.25 * (sl * sl + sr * sr))) continue;

            for (int k = Math.max(left, 1); k <= right; k++) {
                double dq = q[use.get(k)] - q[use.get(k - 1)];
                p.area += 0.5 * (ex[k] + ex[k - 1]) * dq;
            }
            r.peaks.add(p);
            }
        }
    }

    /**
     * Every resolved maximum inside one detected region, rather than only its tallest point. Two reflections
     * closer together than the width over which the excess stays significant fall into a single region -- two
     * lamellar phases a few per cent apart in repeat distance do exactly that -- and reporting one peak for the
     * pair loses a whole phase.
     *
     * <p>A local maximum is kept when it is separated from every taller one in the region by a dip to below 70 %
     * of the smaller of the two, which is the usual prominence test, and when it stands at least a tenth as high
     * as the tallest maximum present.
     */
    private static List<Integer> maximaIn(double[] ex, double[] sigma, int a, int b) {
        List<Integer> raw = new ArrayList<>();
        for (int k = a; k <= b; k++) {
            boolean top = true;
            for (int j = Math.max(a, k - 2); j <= Math.min(b, k + 2); j++) if (ex[j] > ex[k]) top = false;
            if (top && (raw.isEmpty() || k - raw.get(raw.size() - 1) > 2)) raw.add(k);
        }
        if (raw.isEmpty()) {
            int m = a;
            for (int k = a; k <= b; k++) if (ex[k] > ex[m]) m = k;
            raw.add(m);
        }
        double tallest = 0;
        for (int k : raw) tallest = Math.max(tallest, ex[k]);
        List<Integer> kept = new ArrayList<>();
        for (int k : raw) {
            if (ex[k] < 0.1 * tallest) continue;
            // A maximum has to rise above the valleys on either side of it by more than the noise. Without this a
            // broad region sitting a little above the baseline -- the usual state of the diffuse tail -- offers up
            // every ripple in it as a peak.
            double leftValley = ex[k], rightValley = ex[k];
            for (int j = k; j >= a && ex[j] <= ex[k]; j--) leftValley = Math.min(leftValley, ex[j]);
            for (int j = k; j <= b && ex[j] <= ex[k]; j++) rightValley = Math.min(rightValley, ex[j]);
            if (ex[k] - Math.max(leftValley, rightValley) < 5 * sigma[k]) continue;
            boolean resolved = true;
            for (int other : raw) {
                if (other == k || ex[other] <= ex[k]) continue;
                double dip = Double.POSITIVE_INFINITY;
                for (int j = Math.min(k, other); j <= Math.max(k, other); j++) dip = Math.min(dip, ex[j]);
                if (dip > 0.7 * ex[k]) { resolved = false; break; }   // no real valley between them
            }
            if (resolved) kept.add(k);
        }
        if (kept.isEmpty()) {
            int m = a;
            for (int k = a; k <= b; k++) if (ex[k] > ex[m]) m = k;
            kept.add(m);
        }
        return kept;
    }

    private static double interp(double x0, double y0, double x1, double y1, double y) {
        if (y1 == y0) return 0.5 * (x0 + x1);
        return x0 + (y - y0) * (x1 - x0) / (y1 - y0);
    }

    /**
     * Form-factor maxima of a sphere are evenly spaced in q (spacing pi/R) and do not extrapolate to q = 0, unlike
     * lamellar orders. Three or more such maxima at the low-q end of the peak list are moved to r.oscillations.
     */
    /**
     * Whether these maxima index as a crystallographic phase, which decides between Bragg reflections and the
     * oscillations of a particle envelope. Deliberately strict: every peak in the set has to be explained, and
     * well, so that a genuine oscillation series is not kept on the strength of a loose match.
     */
    private static boolean indexesAsAPhase(List<Peak> candidate, double searchFrom) {
        if (candidate.size() < 3) return false;
        int[] index = new int[candidate.size()];
        for (int i = 0; i < index.length; i++) index[i] = i;
        for (PhaseMatch m : matchPhases(new ArrayList<>(candidate), index, searchFrom))
            if (m.matched >= candidate.size() && m.unexplained == 0 && m.meanError < 0.01) return true;
        return false;
    }

    /**
     * Whether these maxima are too narrow to be the oscillations of a particle envelope. Compares the median peak
     * width with the spacing of the series: envelope lobes run about half a period wide, while a Bragg reflection
     * is narrower than its spacing by the number of ordered repeats.
     */
    private static boolean tooSharpForOscillations(List<Peak> candidate, double spacing) {
        if (!(spacing > 0)) return false;
        List<Double> ratios = new ArrayList<>();
        for (Peak p : candidate) if (p.fwhm > 0) ratios.add(p.fwhm / spacing);
        if (ratios.isEmpty()) return false;
        Collections.sort(ratios);
        double median = ratios.get(ratios.size() / 2);
        return median < 0.3;
    }

    static void separateOscillations(Report r) {
        List<Peak> pk = r.peaks;
        int best = 0;
        double bestSpacing = 0;
        for (int n = pk.size(); n >= 3; n--) {
            double d0 = pk.get(1).q - pk.get(0).q;
            boolean even = d0 > 0;
            for (int i = 2; i < n && even; i++) {
                double d = pk.get(i).q - pk.get(i - 1).q;
                if (Math.abs(d / d0 - 1) > 0.2) even = false;
            }
            if (!even) continue;
            double spacing = (pk.get(n - 1).q - pk.get(0).q) / (n - 1);
            double offset = pk.get(0).q / spacing;
            if (Math.abs(offset - Math.rint(offset)) < 0.15) continue;   // passes through the origin: lamellar
            // Equal spacing is necessary but not sufficient. Form-factor oscillations are equally spaced because
            // they come from one particle envelope; Bragg reflections follow the sqrt(N) ratios of a lattice, and
            // over a short series the two can look alike -- three cubic reflections can sit within the tolerance
            // above. Before discarding them, check whether they index: a set that does is reflections, not ripple.
            if (indexesAsAPhase(pk.subList(0, n), r.searchFrom)) continue;
            // Width is the other discriminator, and the decisive one when a weak spurious maximum lands between
            // two orders and makes a Bragg series look evenly spaced. The oscillations of a particle envelope are
            // broad: each lobe is about as wide as the gap to the next. A reflection from an ordered domain is far
            // narrower than its spacing, so a sharp series is not ripple however evenly it happens to fall.
            if (tooSharpForOscillations(pk.subList(0, n), spacing)) continue;
            best = n;
            bestSpacing = spacing;
            break;
        }
        if (best == 0) return;
        for (int i = 0; i < best; i++) r.oscillations.add(pk.get(i));
        r.peaks = new ArrayList<>(pk.subList(best, pk.size()));
        r.oscillationRadius = Math.PI / bestSpacing;
    }

    /**
     * Cholesterol monohydrate crystals give sharp reflections at q = 0.186 and 0.371 1/A (d = 3.38 and 1.69 nm) when
     * cholesterol is not taken up by the particles (Yanez Arteta et al., PNAS 115, E3351 (2018)).
     */
    static void separateCholesterol(Report r) {
        List<Peak> keep = new ArrayList<>();
        for (Peak p : r.peaks) {
            boolean chol = (Math.abs(p.q / 0.186 - 1) < 0.02 || Math.abs(p.q / 0.371 - 1) < 0.02) && p.order() > 25;
            if (chol) r.cholesterol.add(p); else keep.add(p);
        }
        r.peaks = keep;
    }

    /**
     * Best match of every phase to the given peaks (sorted, equally good alternatives kept). The first peak must be
     * the lowest allowed reflection of the phase, unless the lower reflections fall below the peak-search range.
     */
    static List<PhaseMatch> matchPhases(List<Peak> pk, int[] peakIndex, double searchFrom) {
        List<PhaseMatch> out = new ArrayList<>();
        if (pk.size() < 2) return out;
        double tol = 0.025;
        // A weak peak may be noise or a trace of another phase, so it must not veto an otherwise clean assignment.
        // Every strong peak has to be explained; weak ones are allowed to be left over and are reported.
        double maxSig = 0;
        for (Peak p : pk) maxSig = Math.max(maxSig, p.significance);
        final double strong = Math.max(8, 0.15 * maxSig);
        int nStrong = 0;
        for (Peak p : pk) if (p.significance >= strong) nStrong++;
        for (Phase ph : PHASES) {
            PhaseMatch best = null;
            for (int first = 0; first < Math.min(3, ph.m.length); first++) {
                double unit = pk.get(0).q / ph.m[first];   // q of a reflection with index m is unit * m
                if (first > 0 && unit * ph.m[first - 1] >= searchFrom) continue;   // a lower order would be visible
                String[] idx = new String[pk.size()];
                int matched = 0;
                double err = 0;
                for (int i = 0; i < pk.size(); i++) {
                    double ratio = pk.get(i).q / unit;
                    int bestK = -1;
                    double bestE = tol;
                    for (int k = 0; k < ph.m.length; k++) {
                        double e = Math.abs(ratio / ph.m[k] - 1);
                        if (e < bestE) { bestE = e; bestK = k; }
                    }
                    if (bestK >= 0) { idx[i] = ph.hkl[bestK]; matched++; err += bestE; }
                }
                int strongMatched = 0, weakLeft = 0;
                for (int i = 0; i < pk.size(); i++) {
                    boolean ok = idx[i] != null;
                    if (pk.get(i).significance >= strong) { if (ok) strongMatched++; }
                    else if (!ok) weakLeft++;
                }
                if (matched < 2 || strongMatched < Math.max(2, nStrong)) continue;
                // Two peaks are not an assignment for a cubic or hexagonal phase: any pair of positions fits some
                // ratio of two sqrt(N) values, so a match on two reflections says almost nothing and the form-factor
                // ripples of a plain particle will find one. A lamellar phase is the exception, because its orders
                // are exact integer multiples and a 1:2 pair is already a statement.
                if (matched < 3 && !ph.name.startsWith("Lamellar")) continue;
                int missing = 0;
                double qLast = pk.get(pk.size() - 1).q;
                for (int k = 0; k < ph.m.length; k++) {
                    double qk = unit * ph.m[k];
                    if (qk < pk.get(0).q * (1 - tol) || qk > qLast * (1 + tol)) continue;
                    boolean seen = false;
                    for (Peak p : pk) if (Math.abs(p.q / qk - 1) < tol) seen = true;
                    if (!seen) missing++;
                }
                PhaseMatch m = new PhaseMatch();
                m.unexplained = weakLeft;
                m.missing = missing;
                m.phase = ph.name;
                m.symmetry = ph.symmetry;
                m.indices = idx;
                m.peakIndex = peakIndex;
                m.matched = matched;
                m.meanError = err / matched;
                double twoPi = 2 * Math.PI;
                if (m.isLamellar()) { m.latticeParameter = twoPi / unit; m.lattice = "repeat distance d"; }
                else if (m.isHexagonal()) { m.latticeParameter = 2 * twoPi / (Math.sqrt(3) * unit); m.lattice = "lattice parameter a"; }
                else { m.latticeParameter = twoPi / unit; m.lattice = "lattice parameter a"; }
                if (best == null || m.missing < best.missing || (m.missing == best.missing && m.meanError < best.meanError)) best = m;
            }
            if (best != null) out.add(best);
        }
        out.sort((a, b) -> a.missing != b.missing ? a.missing - b.missing
                : a.matched != b.matched ? b.matched - a.matched : Double.compare(a.meanError, b.meanError));
        if (!out.isEmpty()) {
            int m0 = out.get(0).missing;
            out.removeIf(p -> p.missing > m0);
        }
        return out;
    }

    /**
     * Single-phase indexing of all peaks; when that fails (or leaves reflections unexplained) and there are at least
     * four peaks, every split into two lattices of at least two peaks each is tried. Coexisting phases are common in
     * LNP cores: Pattipeiluhu et al., Nat. Commun. 15, 1303 (2024) find inverse-hexagonal tubes next to hexagonally
     * packed inverse micelles in DOPE-rich siRNA-LNPs.
     */
    /** most peaks the two-lattice search will consider; the cost of trying every split grows as 3^n */
    private static final int MAX_COEXISTENCE_PEAKS = 8;

    static void indexPeaks(Report r) {
        List<Peak> pk = r.peaks;
        int n = pk.size();
        if (n < 2) return;
        int[] all = new int[n];
        for (int i = 0; i < n; i++) all[i] = i;
        r.phases.addAll(matchPhases(pk, all, r.searchFrom));
        boolean singleOk = !r.phases.isEmpty() && r.phases.get(0).missing == 0;
        if (singleOk || n < 3) return;
        // The assignment below tries every way of splitting the peaks between two lattices, which costs 3^n, so it
        // works from the strongest peaks when there are many. Dropping the weakest is the right choice rather than
        // giving up: a handful of faint features in the tail should not stop two clear series from being found,
        // and matchPhases already tolerates weak peaks it cannot explain.
        int[] origIndex = new int[n];
        for (int i = 0; i < n; i++) origIndex[i] = i;
        if (n > MAX_COEXISTENCE_PEAKS) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            final List<Peak> src = pk;
            Arrays.sort(order, (x, y) -> Double.compare(src.get(y).significance, src.get(x).significance));
            List<Integer> keep = new ArrayList<>(Arrays.asList(order).subList(0, MAX_COEXISTENCE_PEAKS));
            Collections.sort(keep);                       // back into q order
            List<Peak> reduced = new ArrayList<>();
            origIndex = new int[keep.size()];
            for (int i = 0; i < keep.size(); i++) {
                origIndex[i] = keep.get(i);               // so the reported indices still point at r.peaks
                reduced.add(src.get(keep.get(i)));
            }
            pk = reduced;
            n = pk.size();
        }
        // every peak goes to lattice A, lattice B or both (overlapping reflections, e.g. Ia3d (211) on HII (10)
        // in cuboplexes, Zheng et al. PNAS 2023); shared peaks cost a little so that disjoint
        // explanations are preferred when they work equally well
        List<PhaseMatch[]> cands = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        int combos = 1;
        for (int i = 0; i < n; i++) combos *= 3;
        for (int code = 0; code < combos; code++) {
            int[] where = new int[n];   // 0 = A, 1 = B, 2 = both
            int c = code;
            int shared = 0;
            for (int i = 0; i < n; i++) { where[i] = c % 3; c /= 3; if (where[i] == 2) shared++; }
            if (where[0] == 1) continue;   // peak 0 belongs to A (or both): each split is tried once
            List<Peak> a = new ArrayList<>(), b = new ArrayList<>();
            List<Integer> ia = new ArrayList<>(), ib = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (where[i] != 1) { a.add(pk.get(i)); ia.add(origIndex[i]); }
                if (where[i] != 0) { b.add(pk.get(i)); ib.add(origIndex[i]); }
            }
            if (a.size() < 2 || b.size() < 2 || shared > 1) continue;
            List<PhaseMatch> ma = matchPhases(a, ia.stream().mapToInt(Integer::intValue).toArray(), r.searchFrom);
            if (ma.isEmpty()) continue;
            List<PhaseMatch> mb = matchPhases(b, ib.stream().mapToInt(Integer::intValue).toArray(), r.searchFrom);
            if (mb.isEmpty()) continue;
            // fewer missing reflections, more reflections explained per lattice, smaller position error
            double score = 10 * (ma.get(0).missing + mb.get(0).missing) + ma.get(0).meanError + mb.get(0).meanError
                    - 0.1 * (a.size() + b.size()) + 0.05 * shared;
            cands.add(new PhaseMatch[]{ma.get(0), mb.get(0)});
            scores.add(score);
        }
        PhaseMatch[] best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < cands.size(); i++) if (scores.get(i) < bestScore) { bestScore = scores.get(i); best = cands.get(i); }
        if (best != null) {
            final PhaseMatch[] b0 = best;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < cands.size(); i++) order.add(i);
            order.sort((x, y) -> Double.compare(scores.get(x), scores.get(y)));
            List<String> seen = new ArrayList<>();
            seen.add(key(b0));
            for (int i : order) {
                if (scores.get(i) > bestScore + 0.25 || r.coexistenceAlternatives.size() >= 2) break;
                String k = key(cands.get(i));
                if (seen.contains(k)) continue;
                seen.add(k);
                r.coexistenceAlternatives.add(cands.get(i));
            }
        }
        int singleMissing = r.phases.isEmpty() ? Integer.MAX_VALUE : r.phases.get(0).missing;
        if (best != null && best[0].missing + best[1].missing < singleMissing) r.coexistence = best;
    }

    private static String key(PhaseMatch[] m) {
        String a = m[0].symmetry + Math.round(m[0].latticeParameter), b = m[1].symmetry + Math.round(m[1].latticeParameter);
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    /** one broad peak at d ~ 3.5-8 nm: nucleic-acid / ionizable-lipid assembly */
    /**
     * Whether the broad maximum is better read as part of the particle's form factor. Three things together say
     * so: the curve already shows form-factor oscillations, the maximum persists over too few repeats to be a
     * lattice, and nothing sits at twice its position. A stack of bilayers gives a second order; one bilayer does
     * not, it gives the next lobe of the same oscillation.
     */
    private static boolean looksLikeFormFactorLobe(Report r, double[] q, double[] I) {
        if (countPorodMinima(q, I) == 0) return false;
        Peak tallest = r.peaks.get(0);
        for (Peak k : r.peaks) if (k.height > tallest.height) tallest = k;
        if (!(tallest.order() < 5)) return false;             // sharp enough to be a reflection
        for (Peak k : r.peaks)
            if (k != tallest && Math.abs(k.q / (2 * tallest.q) - 1) < 0.04) return false;   // has a second order
        return true;
    }

    /** minima in q⁴·I: the mark of a form factor with resolved oscillations */
    static int countPorodMinima(double[] q, double[] I) {
        int minima = 0;
        for (int j = 2; j < q.length - 2; j++) {
            if (q[j] < 0.02) continue;
            double c = Math.pow(q[j], 4) * I[j];
            double a = Math.pow(q[j - 2], 4) * I[j - 2], b = Math.pow(q[j + 2], 4) * I[j + 2];
            if (c < 0.97 * a && c < 0.97 * b) { minima++; j += 3; }
        }
        return minima;
    }

    static boolean isMesophase(List<Peak> peaks) {
        Peak p = peaks.get(0);
        for (Peak k : peaks) if (k.height > p.height) p = k;
        return p.q > 0.075 && p.q < 0.18 && p.order() < 15;
    }

    // ------------------------------------------------------------------ text

    static String describe(Report r, double[] q, double[] I) {
        StringBuilder sb = new StringBuilder();
        sb.append("SAMPLE ASSESSMENT (model-free)\n");
        sb.append(String.format("q range %.4f – %.4f Å⁻¹  (resolves real-space sizes ≈ %.0f – %.0f Å)%n%n",
                q[0], q[q.length - 1], Math.PI / q[q.length - 1], Math.PI / q[0]));

        sb.append("1. Overall size\n");
        Guinier g = r.guinier;
        if (g.found) {
            sb.append(String.format("   Guinier Rg = %.1f ± %.1f Å (%.1f nm), I(0) = %.4g, %d points, q·Rg ≤ %.2f%n",
                    g.rg, g.rgErr, g.rg / 10, g.i0, g.points, g.qmax * g.rg));
            sb.append(String.format("   For a uniform sphere this is a radius of ≈ %.0f Å (diameter ≈ %.0f nm).%n",
                    g.rg * Math.sqrt(5.0 / 3.0), 2 * g.rg * Math.sqrt(5.0 / 3.0) / 10));
        } else {
            sb.append(String.format("   No Guinier region: the lowest q (%.4f Å⁻¹) is too high for these particles.%n", q[0]));
            sb.append(String.format("   Particles are probably larger than Rg ≈ %.0f Å. Use the model fit below for the size,%n", 1.3 / q[0]));
            sb.append("   or measure to lower q (longer sample-detector distance).\n");
        }
        // form-factor oscillations: local minima of q^4 I below the search range
        int minima = 0;
        for (int j = 2; j < q.length - 2 && q[j] < r.searchFrom; j++) {
            double c = q[j] * q[j] * q[j] * q[j] * I[j];
            double a = Math.pow(q[j - 2], 4) * I[j - 2], b = Math.pow(q[j + 2], 4) * I[j + 2];
            if (c < 0.97 * a && c < 0.97 * b) { minima++; j += 3; }
        }
        r.porodMinima = minima;
        sb.append(minima > 0
                ? "   Form-factor oscillations are visible: the particles are fairly uniform in size (σ/R ≲ 0.2),\n   so a shell model can be fitted with confidence.\n"
                : "   No clear form-factor oscillations: the size distribution is broad (σ/R ≳ 0.2) or the particles are\n   too large for this q range; shell thicknesses from a model fit will be poorly determined.\n");

        if (!r.oscillations.isEmpty()) {
            sb.append(String.format("   %d evenly spaced maxima above q = %.3f Å⁻¹ are form-factor oscillations, not internal order;%n",
                    r.oscillations.size(), r.searchFrom));
            sb.append(String.format("   their spacing puts the main density interface at a radius of ≈ %.0f Å (diameter ≈ %.0f nm;%n   a faint PEG corona adds to this).%n",
                    r.oscillationRadius, 2 * r.oscillationRadius / 10));
        }
        if (!r.cholesterol.isEmpty()) {
            // reported after the size section, before internal structure
        }
        sb.append("\n2. Internal structure (peaks above q = ").append(String.format("%.3f", r.searchFrom)).append(" Å⁻¹)\n");
        if (r.peaks.isEmpty()) {
            sb.append("   No peaks. No ordered internal structure is detected. This is typical of empty LNPs, unilamellar\n")
              .append("   vesicles and nanoemulsions. Use the 'Empty LNP' or 'Core–shell' model.\n");
        } else {
            for (int i = 0; i < r.peaks.size(); i++) {
                Peak p = r.peaks.get(i);
                sb.append(String.format("   Peak %d: q = %.4f Å⁻¹, d = 2π/q = %.1f Å (%.2f nm), FWHM = %.4f Å⁻¹, area %.3g, %.0fσ%n",
                        i + 1, p.q, p.d(), p.d() / 10, p.fwhm, p.area, p.significance));
            }
            if (!r.phases.isEmpty()) {
                PhaseMatch m = r.phases.get(0);
                sb.append(String.format("   Best match: %s [%s], %s = %.1f Å (%.2f nm)%n",
                        m.phase, m.symmetry, m.lattice, m.latticeParameter, m.latticeParameter / 10));
                if (m.missing > 0) sb.append(String.format("   (%d expected reflection(s) in this range are not seen. Treat the assignment as tentative.)%n", m.missing));
                if (m.unexplained > 0) sb.append(String.format("   (%d weak peak(s) are not explained by this phase: noise, or a trace of a second phase.)%n", m.unexplained));
                sb.append("   Indexing:");
                for (int i = 0; i < m.indices.length; i++) sb.append(' ').append(m.indices[i]);
                sb.append('\n');
                for (int k = 1; k < r.phases.size(); k++) {
                    PhaseMatch o = r.phases.get(k);
                    sb.append(String.format("   Also consistent: %s [%s], %s = %.1f Å%n", o.phase, o.symmetry, o.lattice, o.latticeParameter));
                }
                sb.append("   Peak ratios follow Caselli et al. (2024) Table 1. Check the assignment against the higher-order\n")
                  .append("   reflections; with only two peaks several phases can fit.\n");
                if (r.peaks.size() == 2) {
                    Peak a = r.peaks.get(0), b = r.peaks.get(1);
                    sb.append(String.format("   Caution: two peaks can also be the first reflections of TWO coexisting lattices%n"))
                      .append(String.format("   (d = %.2f and %.2f nm). In DOPE-rich siRNA-LNPs, Pattipeiluhu et al. (2024) found exactly%n", a.d() / 10, b.d() / 10))
                      .append("   this ambiguity: HII tubes next to hexagonally packed inverse micelles. cryo-EM can settle it.\n");
                }
            } else if (r.coexistence != null) {
                sb.append("   No single phase explains all peaks; two coexisting lattices do:\n");
                for (PhaseMatch m : r.coexistence) {
                    sb.append(String.format("   • %s [%s], %s = %.1f Å (%.2f nm), peaks", m.phase, m.symmetry, m.lattice, m.latticeParameter, m.latticeParameter / 10));
                    // a peak offered to this lattice that it could not place has a null index: it belongs to the
                    // other phase, so listing it here with "null" after its number is just noise
                    for (int i = 0; i < m.peakIndex.length; i++)
                        if (m.indices[i] != null) sb.append(' ').append(m.peakIndex[i] + 1).append(m.indices[i]);
                    sb.append('\n');
                }
                for (PhaseMatch[] alt : r.coexistenceAlternatives) {
                    sb.append("   Also possible:");
                    for (PhaseMatch m : alt) sb.append(String.format(" %s a = %.1f Å;", m.symmetry, m.latticeParameter));
                    sb.append('\n');
                }
                if (r.coexistence[0].isLamellar() && r.coexistence[1].isLamellar()) {
                double d1 = r.coexistence[0].latticeParameter, d2 = r.coexistence[1].latticeParameter;
                sb.append(String.format("   Both are lamellar, with repeats of %.2f and %.2f nm. One bilayer cannot have two repeat%n",
                        Math.max(d1, d2) / 10, Math.min(d1, d2) / 10));
                sb.append("   distances, so this is not a single phase: either two populations of multilamellar\n"
                        + "   (onion) particles are present, or one population whose stacks hold two amounts of water.\n"
                        + "   The difference between the repeats is how much the water layer differs, since the bilayer\n"
                        + "   is the same; the lattice fit reports the water layer of each.\n");
            }
            sb.append("   Coexisting phases are common in LNP cores (lamellar + HII, HII tubes + inverse micelles,\n")
                  .append("   Pattipeiluhu et al. 2024; gyroid cubic + HII in cuboplexes, Zheng et al. 2023). A reflection may\n")
                  .append("   be shared by both lattices. Confirm with cryo-EM.\n");
            } else if (r.mesophasePeak) {
                Peak p = r.peaks.get(0);
                for (Peak k : r.peaks) if (k.height > p.height) p = k;
                sb.append(String.format("   A single broad peak at d ≈ %.1f nm (about %.0f correlated repeats).%n", p.d() / 10, p.order()));
                if (r.cargoDeclared) {
                    sb.append("   In an LNP this is nucleic acid packed with ionizable lipid (Li et al. 2025: d ≈ 4.9 nm). SANS\n")
                      .append("   contrast variation identifies it as a DISORDERED inverse hexagonal core: water/mRNA cylinders\n")
                      .append("   ≈ 6.2 nm apart, absent without mRNA (Yanez Arteta et al. 2018).\n")
                      .append("   Use the 'mRNA-LNP' model, which adds a Gaussian peak to the core–triple-shell particle.\n");
                } else {
                    sb.append("   No cargo has been declared for this sample, so this is not read as a nucleic-acid peak.\n")
                      .append("   A broad maximum here can equally be a lipid mesophase, or a lobe of the particle's own form\n")
                      .append("   factor. If the sample does carry nucleic acid, set the cargo and run the assessment again.\n");
                }
                sb.append(singlePeakReadings(p));
            } else if (r.formFactorLobe) {
                Peak p = r.peaks.get(0);
                for (Peak k : r.peaks) if (k.height > p.height) p = k;
                sb.append(String.format("   A broad maximum at q = %.4f Å⁻¹, surviving only about %.0f repeats, with nothing at twice%n",
                        p.q, p.order()));
                sb.append("   its position. The curve also shows form-factor oscillations, so this is most likely a lobe of\n"
                        + "   the particle's own form factor rather than a repeat distance: a single bilayer or a vesicle\n"
                        + "   shell produces one in this q range, and 2π/q would not then be a spacing in the sample.\n");
                sb.append(String.format("   Were it a repeat it would be %.1f nm, and a stack would show a second order near\n"
                        + "   q = %.4f Å⁻¹; none is present.%n", p.d() / 10, 2 * p.q));
                sb.append("   Fit the 'Vesicle' or a shell model, whose minima set the bilayer thickness, rather than a\n"
                        + "   lattice. If you expected a stack, look for a second order before reading a repeat here.\n");
            } else if (r.spongeCandidate) {
                Peak p = r.peaks.get(0);
                sb.append(String.format("   One broad peak at d ≈ %.1f nm, with order surviving only about %.0f repeats, and no higher%n",
                        p.d() / 10, p.order()));
                sb.append("   orders. That is the signature of a sponge (L3) phase: bicontinuous like a cubic phase but\n")
                  .append("   without long-range order, so there is nothing to index (Fig. 1G of Caselli et al. 2024).\n");
                sb.append(singlePeakReadings(p));
                sb.append("   Fit it as a sponge in the Structure tab, which gives the repeat and the correlation length.\n")
                  .append("   A sponge sits next to the cubic phases on a phase diagram, so check whether a change of\n")
                  .append("   temperature or additive has driven an ordered cubosome across that boundary.\n");
            } else if (r.peaks.size() == 1) {
                Peak p = r.peaks.get(0);
                sb.append(String.format("   A single, sharp peak (about %.0f correlated repeats) cannot be assigned to a phase on its own.%n", p.order()));
                sb.append(singlePeakReadings(p));
                sb.append("   Look for weak higher orders (lamellar: 2q, hexagonal: √3q and 2q, Pn3m: √(3/2)q), or\n")
                  .append("   compare with cryo-EM: lamellar cores show concentric rings, HII cores show hexagons and straight lines.\n");
            } else {
                sb.append("   The peaks do not match a single lamellar, hexagonal or cubic phase. The sample may contain\n")
                  .append("   coexisting phases, or free surfactant micelles (cf. Spinozzi et al. 2024).\n");
            }
        }

        if (!r.cholesterol.isEmpty()) {
            boolean primary = false, secondary = false;
            for (Peak p : r.cholesterol) {
                if (Math.abs(p.q / 0.186 - 1) < 0.02) primary = true;
                if (Math.abs(p.q / 0.371 - 1) < 0.02) secondary = true;
            }
            StringBuilder which = new StringBuilder();
            for (Peak p : r.cholesterol) which.append(String.format("%s%.2f nm", which.length() > 0 ? ", " : "", p.d() / 10));
            sb.append("   Sharp peak(s) at d ≈ ").append(which).append(": cholesterol monohydrate crystals. Part of the cholesterol\n")
              .append("   is not incorporated in the particles (Yanez Arteta et al. 2018). These peaks were left out of the indexing.\n");
            if (secondary && !primary)
                sb.append("   Only the 1.69 nm order is present; the stronger 3.38 nm reflection is not, so treat this as tentative.\n");
            if (primary && !secondary)
                sb.append("   Only the 3.38 nm order is present; look for 1.69 nm to confirm it.\n");
        }
        sb.append("\n3. Suggested model\n");
        if (r.formFactorLobe)
            sb.append("   Vesicle or shell model: the oscillations, not a lattice, carry the structure here.\n");
        else if (r.mesophasePeak && !r.cargoDeclared)
            sb.append("   Empty LNP (core + 3 shells). Declare a cargo if the sample carries nucleic acid.\n");
        else if (r.mesophasePeak) sb.append("   mRNA-LNP: core + 3 shells + Gaussian mesophase peak.\n");
        else if (r.coexistence != null)
            sb.append("   Two ordered phases: fit them together in the Structure tab (Lattice fit, second phase enabled).\n")
              .append("   A core-shell model describes the particle envelope only and will not reproduce these peaks.\n")
              .append("   For a particle size, fit 'Empty LNP' with q max set below the first reflection.\n");
        else if (r.spongeCandidate) sb.append("   Sponge (L3) phase: fit the Teubner–Strey form in the Structure tab.\n");
        else if (!r.phases.isEmpty()) sb.append("   Ordered internal phase: report the lattice parameter above. For the particle size, fit the\n   'Empty LNP' model with q max set below the first Bragg peak.\n");
        else sb.append("   Empty LNP: core + 3 shells (inner lipid layer, headgroups, PEG corona).\n");
        return sb.toString();
    }

    /** what a single peak would mean under each common reading, with literature values for LNPs */
    static String singlePeakReadings(Peak p) {
        double d = p.d() / 10, aHex = 2 * p.d() / Math.sqrt(3) / 10;
        return String.format("   Possible readings of this one peak:%n"
                + "     lamellar (siRNA between bilayers)  d = %.2f nm   [siRNA-LNPs: 5.9–6.2 nm, Pattipeiluhu 2024]%n"
                + "     inverse hexagonal HII [10]          a = %.2f nm   [empty 4.8, siRNA 6.0–6.2 nm, ibid.; disordered HII in%n"
                + "                                                         mRNA-LNPs 6.2 nm, Yanez Arteta 2018; cuboplex HII 5.1–6.0 nm, Zheng 2023]%n"
                + "     mRNA–ionizable lipid mesophase      d = %.2f nm   [4.9–5.2 nm, Li et al. 2025]%n", d, aHex, d);
    }

    // ------------------------------------------------------------------ small linear algebra

    private static double[] polyfit(double[] x, double[] y, double[] w, boolean[] mask, int deg) {
        int m = deg + 1;
        double x0 = 0, ws = 0;
        for (int i = 0; i < x.length; i++) if (mask[i]) { x0 += w[i] * x[i]; ws += w[i]; }
        x0 /= ws;
        double[][] a = new double[m][m + 1];
        for (int i = 0; i < x.length; i++) {
            if (!mask[i]) continue;
            double[] pw = new double[2 * m];
            pw[0] = 1;
            for (int k = 1; k < 2 * m; k++) pw[k] = pw[k - 1] * (x[i] - x0);
            for (int r = 0; r < m; r++) {
                for (int c = 0; c < m; c++) a[r][c] += w[i] * pw[r + c];
                a[r][m] += w[i] * pw[r] * y[i];
            }
        }
        for (int c = 0; c < m; c++) {   // Gaussian elimination with partial pivoting
            int piv = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(a[r][c]) > Math.abs(a[piv][c])) piv = r;
            double[] t = a[c]; a[c] = a[piv]; a[piv] = t;
            if (a[c][c] == 0) continue;
            for (int r = c + 1; r < m; r++) {
                double f = a[r][c] / a[c][c];
                for (int k = c; k <= m; k++) a[r][k] -= f * a[c][k];
            }
        }
        double[] coef = new double[m + 1];
        for (int r = m - 1; r >= 0; r--) {
            double v = a[r][m];
            for (int k = r + 1; k < m; k++) v -= a[r][k] * coef[k];
            coef[r] = a[r][r] == 0 ? 0 : v / a[r][r];
        }
        coef[m] = x0;
        return coef;
    }

    private static double polyval(double[] c, double x) {
        int m = c.length - 1;
        double dx = x - c[m], v = 0;
        for (int k = m - 1; k >= 0; k--) v = v * dx + c[k];
        return v;
    }
}
