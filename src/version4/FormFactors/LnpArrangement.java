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
import java.util.Collections;
import java.util.List;

/**
 * Which arrangement of the cargo the data supports, as a posterior probability over candidates.
 *
 * <p>A fit to one cargo model tells you how well that model does; it cannot tell you whether another would
 * have done as well. This fits every candidate to the same data, each with its <b>own free envelope</b>, and
 * compares them. Letting each keep its own envelope matters: a loaded and an unloaded preparation are not
 * obliged to have the same particle size or the same corona, and attributing an envelope difference to the
 * cargo would be the easiest way to get a confident wrong answer.
 *
 * <h3>Why the number of points is not the number of points</h3>
 *
 * <p>The comparison is by BIC, which needs a count of independent observations. A SAXS curve is heavily
 * oversampled: the independent count is the Shannon number, D<sub>max</sub>&middot;q<sub>max</sub>/&pi;,
 * which for a 58 nm particle measured to 4.1 A^-1 is about 76 against perhaps a thousand recorded points.
 *
 * <p>This is not a detail. Run against a known-empty LNP with the raw point count, the method prefers a
 * shell-bound cargo at a posterior of 1.0000 over no cargo at all, on the strength of a reduced chi-squared
 * of 1.84 against 1.94 — a difference of no consequence inflated into certainty by counting the same
 * information a dozen times. With Shannon channels the same data returns no cargo at 0.95.
 *
 * <h3>Rescaling</h3>
 *
 * <p>None of the candidates is true, and on real data the best of them still sits at a reduced chi-squared
 * of two or three. Errors are therefore rescaled so the best candidate sits at one, which makes the
 * comparison about relative fit rather than about absolute agreement that no model achieves.
 *
 * <h3>What the answer means</h3>
 *
 * <p>The posterior is over the candidates offered and nothing else. {@link Cargo#QUASI_PERIODIC} in
 * particular is a class, not a shape: a hexagonal array of strands and a solenoid wound at the same pitch
 * produce the same peak in the same place, and no measurement of this kind separates them.
 */
final class LnpArrangement {

    /** the candidate arrangements, each a cargo term added to a freely fitted envelope */
    enum Cargo {
        NONE("No cargo", 0,
                "The curve is described by the particle envelope alone."),
        QUASI_PERIODIC("Quasi-periodic packing", 3,
                "Cargo held at a repeating spacing — strands in an array, or a coil wound at a fixed pitch. "
                        + "These two cannot be told apart by scattering."),
        COMPACT("Condensed cargo", 1,
                "Cargo collapsed into one or more compact bodies, the way a genome is packed inside a virus "
                        + "capsid or ribosomal RNA is folded into a subunit. It then scatters like an object "
                        + "of its own size, which puts its signal at a lower q than the particle's internal "
                        + "detail and leaves no repeating spacing at all."),
        SHELL_BOUND("Bound to a surface", 2,
                "Cargo lying on a shell — against the inner monolayer, or outside it under the corona."),
        FREE("Not encapsulated", 1,
                "Cargo and particles loose in the same solution, uncorrelated, their intensities simply adding.");

        final String label; final int extra; final String description;
        Cargo(String label, int extra, String description) {
            this.label = label; this.extra = extra; this.description = description;
        }
    }

    /** one candidate, fitted */
    static final class Candidate implements Comparable<Candidate> {
        final Cargo cargo;
        double chi2Reduced;
        int parameters;
        double bic, deltaBic, posterior;
        double[] envelope, extra;
        /** set when the declared cargo cannot physically be arranged this way */
        boolean impossible;
        String reason = "";

        Candidate(Cargo c) { this.cargo = c; }
        @Override public int compareTo(Candidate o) { return Double.compare(bic, o.bic); }
    }

    static final class Result {
        final List<Candidate> candidates = new ArrayList<>();
        /** independent observations, D_max * q_max / pi */
        double channels;
        double dMax, qMax;
        /** reduced chi-squared of the best candidate, before rescaling */
        double bestChi2Reduced;
        /** how much the errors had to be inflated for the best candidate to reach one */
        double errorScale;

        /** the most probable arrangement that is not geometrically excluded */
        Candidate best() {
            for (Candidate c : candidates) if (!c.impossible) return c;
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        /**
         * The reading, in words. Deliberately phrased as exclusion: what the data rules out is far more
         * defensible than what it picks, and a short list of survivors is what the user can go and test by
         * other means.
         */
        String verdict() {
            if (candidates.isEmpty()) return "No candidate could be fitted.";
            StringBuilder sb = new StringBuilder();
            Candidate top = candidates.get(0);
            sb.append(String.format("%s, at a posterior of %s.%n  %s%n",
                    top.cargo.label, top.posterior > 0.999 ? "> 0.999" : String.format("%.3f", top.posterior),
                    top.cargo.description));
            List<String> ruled = new ArrayList<>();
            for (Candidate c : candidates) {
                if (c == top) continue;
                if (c.impossible) ruled.add(c.cargo.label + " (" + c.reason + ")");
                else if (c.deltaBic > 10) ruled.add(c.cargo.label);
            }
            if (!ruled.isEmpty()) sb.append("  Ruled out: ").append(String.join("; ", ruled)).append(".\n");
            List<String> open = new ArrayList<>();
            for (Candidate c : candidates)
                if (c != top && !c.impossible && c.deltaBic <= 10) open.add(c.cargo.label);
            if (!open.isEmpty())
                sb.append("  Not excluded: ").append(String.join("; ", open))
                        .append(" — the data does not separate these from the answer above.\n");
            sb.append(String.format("  Judged on %.0f independent channels (D_max %.1f Å, q_max %.3f), "
                    + "not the %s recorded points.%n", channels, dMax * 10, qMax, "many"));
            if (top.cargo == Cargo.QUASI_PERIODIC)
                sb.append("  This names a repeat, not a shape: an array of strands and a wound coil of the "
                        + "same pitch are indistinguishable here.\n");
            return sb.toString();
        }
    }

    private LnpArrangement() {}

    // ---------------------------------------------------------------- cargo terms

    private static double phi(double x) {
        double u = Math.abs(x) < 1e-8 ? 1e-8 : x;
        return 3 * (Math.sin(u) - u * Math.cos(u)) / (u * u * u);
    }

    /** Debye function for a random coil, the shape free cargo in solution actually has */
    private static double coil(double q, double rg) {
        double x = q * rg * q * rg;
        if (x < 1e-9) return 1;
        return 2 * (Math.exp(-x) + x - 1) / (x * x);
    }

    private static double cargoAt(Cargo c, double q, double[] e) {
        switch (c) {
            case QUASI_PERIODIC: {
                double z = (q - e[1]) / e[2];
                return e[0] * Math.exp(-0.5 * z * z);
            }
            case COMPACT: {
                double f = phi(q * e[1]);
                return e[0] * f * f;
            }
            // e[1] for COMPACT and FREE is supplied by the declared cargo, not fitted: see startExtra
            case SHELL_BOUND: {
                double x = q * e[1];
                double sinc = Math.abs(x) < 1e-8 ? 1 : Math.sin(x) / x;
                return e[0] * Math.exp(-0.5 * q * e[2] * q * e[2]) * sinc * sinc;
            }
            case FREE:
                return e[0] * coil(q, e[1]);
            default:
                return 0;
        }
    }

    /** sensible starting values, in the units the rest of the panel uses (Angstrom, e/A^3) */
    /**
     * Starting values, and which of them are allowed to move.
     *
     * <p>A cargo term with a free shape is a nuisance function: give a sphere form factor a free radius and
     * a free amplitude and it will absorb whatever the envelope model fails to describe, which on a
     * known-empty sample was enough to beat no-cargo-at-all. But the shape is not unknown. A condensed
     * chain of n nucleotides has a volume and therefore a radius; a free chain in solution has a radius of
     * gyration set by its length; cargo on a surface sits at a radius the envelope fit already reports.
     * Only the amount is genuinely free, so only the amount is fitted, and the declared cargo length is
     * what makes that possible.
     */
    /** radius of the declared cargo if condensed, its radius of gyration if free, and the shell it may sit on */
    private static double compactRadius = 65, coilRg = 200, shellRadius = 440;

    private static double[] startExtra(Cargo c, double[] q, double[] I, LnpAssessment.Peak peak) {
        double mid = I[I.length / 2];
        switch (c) {
            case QUASI_PERIODIC:
                // start where a peak was actually seen, not at a guess: the fit will not walk across a
                // decade of q to find one, and on the N/P series it wandered to 0.45 and stayed there
                return peak != null && peak.q > 0
                        ? new double[]{Math.max(peak.height, 1e-9), peak.q, Math.max(peak.fwhm, 0.01) / 2.355}
                        : new double[]{mid, 0.126, 0.015};
            case COMPACT:        return new double[]{mid, compactRadius};
            case SHELL_BOUND:    return new double[]{mid, shellRadius, 60};
            case FREE:           return new double[]{mid, coilRg};
            default:             return new double[0];
        }
    }

    // ---------------------------------------------------------------- the comparison

    /**
     * @param dMax  the maximum dimension from P(r), in Angstrom. This sets the channel count and so the
     *              confidence of the whole comparison; without it the method is not merely less precise but
     *              actively wrong, as the class note explains.
     * @param rnaNt cargo length in nucleotides, or 0 if not declared
     * @param copies how many copies per particle to test for, or 0 to skip the geometric filter
     */
    static Result analyse(double[] q, double[] I, double[] s, double dMax, double rnaNt, int copies) {
        Result r = new Result();
        r.dMax = dMax / 10.0;                       // nm, for reporting
        r.qMax = q[q.length - 1];
        r.channels = Math.max(8, dMax * q[q.length - 1] / Math.PI);

        // Fit on a thinned curve. The same oversampling that makes the raw point count the wrong divisor
        // makes it the wrong thing to fit: five candidates over a thousand points, each needing a numerical
        // derivative per parameter, is tens of thousands of evaluations of a polydisperse integral for
        // information that a few hundred points already carry. Several points per independent channel
        // leaves the curve's shape intact and the comparison unchanged.
        double[][] thin = thinTo(q, I, s, (int) Math.max(200, Math.min(q.length, 6 * r.channels)));

        // A shared envelope seed, fitted with any peak region masked out. Fitting the envelope across a
        // strong peak drags it into chasing the peak, and every candidate then starts from a distorted
        // particle: on the N/P series that produced reduced chi-squareds in the hundreds for candidates
        // that fit perfectly well from an honest start. The mask is for the seed only; all candidates are
        // then scored on the whole curve.
        LnpAssessment.Report seen = null;
        try { seen = LnpAssessment.analyse(q, I, s, Double.NaN, true); } catch (RuntimeException ignored) { }
        LnpAssessment.Peak strongest = null;
        if (seen != null) for (LnpAssessment.Peak k : seen.peaks)
            if (strongest == null || k.height > strongest.height) strongest = k;
        double[] seed = envelopeSeed(thin, strongest);
        // fix the cargo shapes from what is known before any candidate is fitted
        double vol = Math.max(rnaNt, 1) * Math.max(copies, 1) * 321.0 * 0.55 / 6.02214e23 * 1e24;  // A^3
        compactRadius = Math.cbrt(3 * vol / (4 * Math.PI));
        coilRg = 1.9 * Math.pow(Math.max(rnaNt, 1), 0.588);     // worm-like chain, A
        shellRadius = Math.max(seed[LnpModel.R0], 50);

        for (Cargo c : Cargo.values()) {
            Candidate cand = fitOne(c, thin[0], thin[1], thin[2], seed, strongest);
            if (cand != null) r.candidates.add(cand);
        }
        if (r.candidates.isEmpty()) return r;

        double best = Double.MAX_VALUE;
        for (Candidate c : r.candidates) best = Math.min(best, c.chi2Reduced);
        r.bestChi2Reduced = best;
        r.errorScale = Math.sqrt(Math.max(best, 1e-9));

        for (Candidate c : r.candidates) {
            double eff = (c.chi2Reduced / best) * Math.max(r.channels - c.parameters, 1);
            c.bic = eff + c.parameters * Math.log(r.channels);
        }
        if (rnaNt > 0 && copies > 0) applyGeometry(r, rnaNt, copies);

        double b0 = Double.MAX_VALUE;
        for (Candidate c : r.candidates) if (!c.impossible) b0 = Math.min(b0, c.bic);
        if (b0 == Double.MAX_VALUE) b0 = r.candidates.get(0).bic;
        double z = 0;
        for (Candidate c : r.candidates) {
            c.deltaBic = c.bic - b0;
            c.posterior = c.impossible ? 0 : Math.exp(-0.5 * Math.min(c.deltaBic, 1400));
            z += c.posterior;
        }
        for (Candidate c : r.candidates) c.posterior = z > 0 ? c.posterior / z : 0;
        Collections.sort(r.candidates);
        return r;
    }

    /**
     * Rules out arrangements that cannot hold the declared cargo.
     *
     * <p>This is a prior, not a likelihood: it says nothing about how well the curve was fitted, only that a
     * given number of copies of a given length will not go into that geometry at the spacing the fit itself
     * reports. A chain of n nucleotides is about 0.59n Angstrom fully extended and 0.273n/2 fully self-paired,
     * so the honest test uses the shorter, most accommodating figure — if even a fully compacted chain does
     * not fit, the arrangement is out.
     */
    private static void applyGeometry(Result r, double nt, int copies) {
        double need = copies * 0.273 * nt / 2;          // A, the most compact plausible contour
        for (Candidate c : r.candidates) {
            if (c.envelope == null) continue;
            double R0 = c.envelope[LnpModel.R0];
            if (!(R0 > 0)) continue;
            double d = c.cargo == Cargo.QUASI_PERIODIC && c.extra.length > 1 && c.extra[1] > 1e-6
                    ? 2 * Math.PI / c.extra[1] : 49.8;
            double capacity;
            switch (c.cargo) {
                case QUASI_PERIODIC:
                    capacity = (4.0 / 3 * Math.PI * R0 * R0 * R0) / (Math.sqrt(3) / 2 * d * d);
                    break;
                case SHELL_BOUND:
                    capacity = 4 * Math.PI * R0 * R0 / d;
                    break;
                default:
                    continue;                            // condensed and free cargo have no length limit
            }
            if (need > capacity) {
                c.impossible = true;
                c.reason = String.format("%d copies need %.0f Å of chain, this geometry holds %.0f",
                        copies, need, capacity);
            }
        }
    }

    /**
     * Thins a curve to about {@code want} points, evenly in log q so that the low-q decay and the wide-angle
     * tail keep the same share of the fit as they had. Points are averaged within each bin rather than
     * picked, so nothing is thrown away: the errors shrink as the square root of the count, which keeps the
     * chi-squared on the same footing as the full curve.
     */
    static double[][] thinTo(double[] q, double[] I, double[] s, int want) {
        int n = q.length;
        if (want >= n) return new double[][]{q, I, s};
        double lo = Math.log(q[0]), hi = Math.log(q[n - 1]);
        List<double[]> out = new ArrayList<>();
        int at = 0;
        for (int b = 0; b < want && at < n; b++) {
            double edge = Math.exp(lo + (hi - lo) * (b + 1.0) / want);
            double sq = 0, si = 0, sw = 0; int c = 0;
            while (at < n && (q[at] <= edge || c == 0)) {
                double w = 1 / (s[at] * s[at]);
                sq += q[at]; si += I[at] * w; sw += w; c++; at++;
            }
            if (c > 0) out.add(new double[]{sq / c, si / sw, Math.sqrt(1 / sw)});
        }
        double[][] d = new double[3][out.size()];
        for (int i = 0; i < out.size(); i++) {
            d[0][i] = out.get(i)[0]; d[1][i] = out.get(i)[1]; d[2][i] = out.get(i)[2];
        }
        return d;
    }

    // ---------------------------------------------------------------- fitting one candidate

    /** the envelope all candidates start from, fitted with any peak region left out */
    private static double[] envelopeSeed(double[][] d, LnpAssessment.Peak peak) {
        LnpModel m = new LnpModel(3, false);
        double[] q = d[0], I = d[1], s = d[2];
        if (peak != null && peak.q > 0) {
            double w = Math.max(peak.fwhm, 0.01) * 2;
            List<double[]> keep = new ArrayList<>();
            for (int j = 0; j < q.length; j++)
                if (Math.abs(q[j] - peak.q) > w) keep.add(new double[]{q[j], I[j], s[j]});
            if (keep.size() > 40) {
                q = new double[keep.size()]; I = new double[keep.size()]; s = new double[keep.size()];
                for (int j = 0; j < keep.size(); j++) {
                    q[j] = keep.get(j)[0]; I[j] = keep.get(j)[1]; s[j] = keep.get(j)[2];
                }
            }
        }
        try {
            LnpFitter.Result f = new LnpFitter(m, q, I, s).physicalConstraints(true)
                    .fit(m.templateDefaults(), m.templateFixed(), true, () -> false);
            if (f != null && Double.isFinite(f.chi2Reduced)) return f.params.clone();
        } catch (RuntimeException ignored) { }
        return m.templateDefaults();
    }

    /**
     * Fits one candidate: the envelope and its cargo term, alternately.
     *
     * <p>Nothing here does its own general-purpose fitting, which is the whole point. A single
     * Levenberg-Marquardt over all sixteen parameters at once was what failed: it returned reduced
     * chi-squareds in the hundreds on data where the existing fitter reaches eight, because it lacks the
     * staged refinement and the clamping to physical ranges that make the envelope converge at all.
     *
     * <p>So the envelope always goes through {@link LnpFitter}, with the cargo's current contribution
     * subtracted from the data first; then the cargo term is fitted on its own against what the envelope
     * leaves behind. Each block is small and well conditioned, and two rounds are enough. The quasi-periodic
     * case needs none of it: a peak on an envelope is exactly what {@code LnpModel(3, true)} already is.
     */
    private static Candidate fitOne(Cargo c, double[] q, double[] I, double[] s,
                                    double[] seed, LnpAssessment.Peak peak) {
        Candidate cand = new Candidate(c);

        LnpModel m = new LnpModel(3, false);
        boolean[] fixed = m.templateFixed();
        for (int k = 0; k < LnpModel.NPAR; k++)
            if (m.uses(k) && LnpModel.isThickness(k)) fixed[k] = false;

        double[] env = seed.clone();
        double[] ex = startExtra(c, q, I, peak);
        int nFree = 0;
        for (int k = 0; k < LnpModel.NPAR; k++) if (m.uses(k) && !fixed[k]) nFree++;
        double[] best = null; double bestChi = Double.MAX_VALUE;
        // Every candidate takes exactly this path, including the one with no cargo at all. Treating them
        // differently is how the comparison goes wrong: give one candidate a joint fit and another two
        // rounds of alternation and the difference in optimisation effort shows up as a difference in
        // chi-squared, which BIC then reads as evidence. On a known-empty sample that was enough to pick a
        // condensed cargo over no cargo.
        for (int round = 0; round < 2; round++) {
            // envelope, against the data with the cargo taken out
            double[] less = new double[q.length];
            for (int j = 0; j < q.length; j++)
                less[j] = ex.length == 0 ? I[j]
                        : Math.max(I[j] - cargoAt(c, q[j], ex), I[j] * 1e-4);
            try {
                LnpFitter.Result f = new LnpFitter(m, q, less, s).physicalConstraints(true)
                        .fit(env, fixed, round == 0, () -> false);
                env = f.params.clone();
            } catch (RuntimeException ignored) { }
            // cargo, against what the envelope leaves behind
            double[] base = m.intensity(env, q);
            if (ex.length > 0) ex = fitCargo(c, q, I, s, base, ex);
            double chi = 0;
            for (int j = 0; j < q.length; j++) {
                double r = (I[j] - base[j] - cargoAt(c, q[j], ex)) / s[j];
                chi += r * r;
            }
            if (chi < bestChi) { bestChi = chi; best = ex.clone(); }
        }
        cand.parameters = nFree + c.extra;
        cand.chi2Reduced = bestChi / Math.max(1, q.length - cand.parameters);
        cand.envelope = env;
        cand.extra = best == null ? ex : best;
        return cand;
    }

    /**
     * Fits the two or three cargo parameters with the envelope held. Small enough that a plain
     * Levenberg-Marquardt in log space is reliable, which is not true of the full problem.
     */
    private static double[] fitCargo(Cargo c, double[] q, double[] I, double[] s,
                                     double[] envelope, double[] start) {
        // only the leading parameter, the amount of cargo, is fitted; the rest describe a shape the
        // declared cargo already fixes
        final int n = c == Cargo.QUASI_PERIODIC ? start.length : 1;
        double[] u0 = new double[n];
        for (int i = 0; i < n; i++) u0[i] = Math.log(Math.max(start[i], 1e-14));
        double[] target = new double[q.length];
        for (int j = 0; j < q.length; j++) target[j] = (I[j] - envelope[j]) / s[j];

        MultivariateJacobianFunction fn = point -> {
            double[] u = point.toArray();
            double[] e = start.clone();
            for (int i = 0; i < n; i++) e[i] = Math.exp(u[i]);
            double[] f0 = new double[q.length];
            for (int j = 0; j < q.length; j++) f0[j] = cargoAt(c, q[j], e) / s[j];
            double[][] jac = new double[q.length][n];
            for (int i = 0; i < n; i++) {
                double h = 1e-5 * Math.max(1, Math.abs(u[i]));
                double[] up = u.clone(); up[i] += h;
                double[] eu = start.clone();
                for (int t = 0; t < n; t++) eu[t] = Math.exp(up[t]);
                for (int j = 0; j < q.length; j++)
                    jac[j][i] = (cargoAt(c, q[j], eu) / s[j] - f0[j]) / h;
            }
            return new Pair<RealVector, RealMatrix>(new ArrayRealVector(f0, false),
                    new Array2DRowRealMatrix(jac, false));
        };
        try {
            LeastSquaresOptimizer.Optimum opt = new LevenbergMarquardtOptimizer()
                    .withCostRelativeTolerance(1e-9).withParameterRelativeTolerance(1e-9)
                    .optimize(new LeastSquaresBuilder().model(fn).target(target).start(u0)
                            .maxEvaluations(300).maxIterations(80).build());
            double[] u = opt.getPoint().toArray();
            double[] out = start.clone();
            for (int i = 0; i < n; i++) out[i] = Math.exp(u[i]);
            return out;
        } catch (RuntimeException e) {
            return start;
        }
    }

}
