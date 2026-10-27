package version4.FormFactors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Mean-field Langevin particle flow for polydisperse form-factor distributions.
 *
 * <h3>Model</h3>
 * A population of N particles theta_i (log-parameters u_i = ln p_i inside user bounds) describes the
 * volume-fraction distribution mu. The ensemble intensity is
 *
 *     I_fit(q) = s * Ibar(q) + b ,    Ibar(q) = (1/N) sum_i P(q; u_i)
 *
 * with the scale s (and optional flat background b) profiled out by weighted least squares at every step.
 *
 * <h3>Dynamics</h3>
 * With residuals r_j = (I_j - I_fit(q_j)) / sigma_j and F(mu) = chi^2/2, every particle follows
 *
 *     du_i = s * sum_j (r_j / sigma_j) dP(q_j; u_i)/du  dt  +  sqrt(2 T dt) dW_i
 *
 * which is the Wasserstein gradient flow of   chi^2/2 + T * sum mu ln mu .
 * Its stationary state is the entropy-regularised distribution
 *
 *     mu(u) ∝ exp( - (dF/dmu)(u) / T ),
 *
 * i.e. maximum entropy (relative to a flat prior in ln p) subject to the data. The temperature T is the
 * regularisation strength. By default it is set by the discrepancy principle: T is annealed down from a
 * value at which the distribution is essentially flat, then a slow controller adjusts ln T until the
 * reduced chi^2 of the ensemble fit sits at the target (1.0), then T is frozen and the distribution is
 * accumulated over the sampling phase.
 *
 * <h3>Differences to McSAS</h3>
 * McSAS moves one contribution at a time by random proposal + accept-if-better, has no regularisation
 * term and stops at chi^2 ~ 1. Here all particles move at once along analytic gradients, the noise is
 * part of the objective (entropy), and the distribution is a stationary ensemble average.
 */
public class LangevinFitter {

    public interface ProgressListener {
        /** called every few steps from the fitting thread */
        void update(int replica, int step, int totalSteps, String phase, double temperature, double chi2Reduced);
    }

    private static final boolean DEBUG = Boolean.getBoolean("mfld.debug");
    private final double[] q, I, sig, invSig, w;   // w = 1/sigma^2
    private final ShapeFamily family;
    private final double[] lo, hi;                  // ln bounds
    private final LangevinSettings set;
    private final FormFactorTable table;
    private final ShapeKernel kernel;
    private final double sumLo, sumHi;   // ln V at the lower / upper bound corners
    private final double iMax;
    private final boolean ratioConstraint;
    private final double lnRatioLo, lnRatioHi;  // bounds on ln(c/a) for spheroids
    private volatile boolean cancelled = false;
    private final DataPreparation.Report preparation;

    /**
     * @param q         q values (1/Angstrom), ascending
     * @param intensity I(q) (NOT q*I(q); the transform cancels in a weighted chi^2 anyway)
     * @param sigma     errors on I(q), all > 0
     * @param family    shape family
     * @param minParams lower bounds of each parameter (Angstrom)
     * @param maxParams upper bounds of each parameter (Angstrom)
     */
    public LangevinFitter(double[] q, double[] intensity, double[] sigma, ShapeFamily family,
                          double[] minParams, double[] maxParams, LangevinSettings settings) {
        if (intensity.length != q.length || sigma.length != q.length) throw new IllegalArgumentException("array lengths differ");
        int d = family.dim();
        if (minParams.length < d || maxParams.length < d) throw new IllegalArgumentException("need " + d + " bounds");
        for (int j = 0; j < q.length; j++)
            if (!(sigma[j] > 0)) throw new IllegalArgumentException("sigma must be > 0 at index " + j);

        // error calibration (band-limited fit) and Shannon rebinning, see DataPreparation
        double maxLen = 0;
        for (int k = 0; k < d; k++) maxLen = Math.max(maxLen, maxParams[k]);
        if (settings.calibrateErrors || settings.rebin) {
            DataPreparation prep = DataPreparation.prepare(q, intensity, sigma, settings.dmax, 2.4 * maxLen,
                    settings.calibrateErrors, settings.rebin, settings.pointsPerChannel, settings.fitBackground);
            this.preparation = prep.report;
            q = prep.q; intensity = prep.I; sigma = prep.sigma;
        } else {
            this.preparation = null;
        }
        int nq = q.length;

        this.q = q.clone();
        this.I = intensity.clone();
        this.sig = sigma.clone();
        this.invSig = new double[nq];
        this.w = new double[nq];
        for (int j = 0; j < nq; j++) {
            if (!(sigma[j] > 0)) throw new IllegalArgumentException("sigma must be > 0 at index " + j);
            invSig[j] = 1.0 / sigma[j];
            w[j] = invSig[j] * invSig[j];
        }
        this.family = family;
        this.set = settings;
        this.lo = new double[d];
        this.hi = new double[d];
        double rmax = 0;
        if (family == ShapeFamily.CORESHELL_SPHEROID && settings.shellThickness > 0) {
            // restrict the shell thickness to t (1 +- tolerance)
            minParams = minParams.clone(); maxParams = maxParams.clone();
            double tol = Math.max(1e-3, settings.shellTolerance);
            minParams[2] = settings.shellThickness * (1 - tol);
            maxParams[2] = settings.shellThickness * (1 + tol);
        }
        for (int k = 0; k < d; k++) {
            if (!(minParams[k] > 0 && maxParams[k] > minParams[k])) throw new IllegalArgumentException("bad bounds for param " + k);
            lo[k] = Math.log(minParams[k]);
            hi[k] = Math.log(maxParams[k]);
            rmax = Math.max(rmax, maxParams[k]);
        }
        double qmax = q[nq - 1];
        if (family == ShapeFamily.CORESHELL_SPHEROID) rmax = Math.max(maxParams[0], maxParams[1]) + maxParams[2];
        this.table = new FormFactorTable(qmax * rmax * 1.05 + 1.0, settings.tableStep);
        this.kernel = family.create(table, qmax, settings);
        double im = 0;
        for (double v : this.I) im = Math.max(im, Math.abs(v));
        this.iMax = im;
        this.sumLo = Math.log(kernel.volume(lo, 0));
        this.sumHi = Math.log(kernel.volume(hi, 0));
        boolean rc = family.hasSpheroidAxes()
                && (settings.aspectRatioMin > 0 || settings.aspectRatioMax < Double.POSITIVE_INFINITY);
        this.ratioConstraint = rc;
        this.lnRatioLo = settings.aspectRatioMin > 0 ? Math.log(settings.aspectRatioMin) : Double.NEGATIVE_INFINITY;
        this.lnRatioHi = settings.aspectRatioMax < Double.POSITIVE_INFINITY ? Math.log(settings.aspectRatioMax) : Double.POSITIVE_INFINITY;
        if (rc && !(lnRatioHi > lnRatioLo)) throw new IllegalArgumentException("aspectRatioMax must exceed aspectRatioMin");
    }

    public void cancel() { cancelled = true; }
    public boolean isCancelled() { return cancelled; }
    public ShapeKernel getKernel() { return kernel; }
    /** calibration / rebinning report (null if both were switched off) */
    public DataPreparation.Report getPreparation() { return preparation; }

    // ------------------------------------------------------------------------------------------

    public LangevinResult fit(ProgressListener listener) {
        long t0 = System.nanoTime();
        int R = Math.max(1, set.replicas);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, set.threads));
        List<Replica> reps = new ArrayList<>();
        try {
            for (int r = 0; r < R && !cancelled; r++) {
                Replica rep = new Replica(r, set.seed + 7919L * r, pool);
                rep.run(listener);
                reps.add(rep);
            }
        } finally {
            pool.shutdownNow();
        }
        if (reps.isEmpty()) return null;
        LangevinResult res = combine(reps);
        res.elapsedSeconds = (System.nanoTime() - t0) * 1e-9;
        return res;
    }

    // ------------------------------------------------------------------------------------------

    /** one independent run */
    private final class Replica {
        final int index;
        final SplittableRandom rng;
        final ExecutorService pool;
        final int N, d, nq, B;
        final double[] u, vel, mob, mobSorted, P, dP, Ibar, c, drift, driftNorm, eig, eigTmp, pot;
        // effective weights; shadow the outer arrays so the error-floor continuation applies everywhere
        final double[] invSig, w;
        final List<Callable<Void>> tasks = new ArrayList<>();
        // discrete species (core contents): label per particle, potentials of every species, accumulators
        final int nsp;
        final boolean fixedFraction;
        final double vref2 = Math.exp(sumLo + sumHi);   // (reference volume)^2 for number weights
        final int[] spc;
        final double[] potK;
        final List<Callable<Void>> speciesTasks = new ArrayList<>();
        final double[] spIntW, spVolW, spNumW;
        final double[][] spParV;

        // accumulators
        final double[][] intHist, volHist, numHist;
        final int[][] pairs;
        final double[][][] pairHist;
        final double[] fitSum, fitSq;
        double sSum, bSum, chi2Sum;
        int nSamples;
        final double[] iSum, iSq, vSum, vSq, nSum, nSq;
        double iW, vW, nW, sumV, sumInvV, sumInvV2;

        double[] traceChi2, traceT;
        double lastChurn;
        double chi2Min = Double.NaN, chi2Target = Double.NaN;
        double chi2Best = Double.POSITIVE_INFINITY;   // lowest chi2_red after the error floor is gone, before heating
        final AdaptiveMixturePrior prior;
        double tempUsed;

        Replica(int index, long seed, ExecutorService pool) {
            this.index = index;
            this.rng = new SplittableRandom(seed);
            this.pool = pool;
            this.N = set.particles;
            this.d = kernel.dim();
            this.nq = q.length;
            this.B = set.histogramBins;
            u = new double[N * d];
            vel = new double[N * d];
            invSig = LangevinFitter.this.invSig.clone();
            w = LangevinFitter.this.w.clone();
            mob = new double[N * d];
            Arrays.fill(mob, 1.0);
            mobSorted = new double[N * d];
            pot = new double[N];
            if (set.priorAxes != null && set.priorAxes.length > 0) {
                for (int a : set.priorAxes)
                    if (a < 0 || a >= kernel.dim()) throw new IllegalArgumentException("prior axis " + a + " out of range");
                prior = new AdaptiveMixturePrior(set.priorAxes, set.priorComponents, set.priorMinSd);
            } else {
                prior = null;
            }
            P = new double[N * nq];
            dP = new double[N * d * nq];
            Ibar = new double[nq];
            c = new double[nq];
            drift = new double[N * d];
            driftNorm = new double[N];
            eig = new double[nq];
            eigTmp = new double[nq];

            intHist = new double[d][B];
            volHist = new double[d][B];
            numHist = new double[d][B];
            int np = d * (d - 1) / 2;
            pairs = new int[np][];
            int p = 0;
            for (int i = 0; i < d; i++) for (int j = i + 1; j < d; j++) pairs[p++] = new int[]{i, j};
            pairHist = new double[np][B][B];
            fitSum = new double[nq];
            fitSq = new double[nq];
            iSum = new double[d]; iSq = new double[d];
            vSum = new double[d]; vSq = new double[d]; nSum = new double[d]; nSq = new double[d];

            nsp = kernel.speciesCount();
            fixedFraction = nsp == 2 && set.fixedSpeciesFraction >= 0 && set.fixedSpeciesFraction <= 1;
            spc = new int[N];
            potK = new double[N * nsp];
            spIntW = new double[nsp]; spVolW = new double[nsp]; spNumW = new double[nsp];
            spParV = new double[nsp][d];

            int chunks = Math.max(1, Math.min(N, set.threads * 4));
            int per = (N + chunks - 1) / chunks;
            for (int start = 0; start < N; start += per) {
                final int s0 = start, s1 = Math.min(N, start + per);
                final double[] scratch = new double[Math.max(1, kernel.scratchSize())];
                tasks.add(() -> {
                    for (int i = s0; i < s1; i++)
                        kernel.evaluate(spc[i], u, i * d, q, P, i * nq, dP, i * d * nq, scratch);
                    return null;
                });
                if (nsp > 1) {
                    final double[] pAlt = new double[nq], dAlt = new double[d * nq];
                    final double[] scr2 = new double[Math.max(1, kernel.scratchSize())];
                    speciesTasks.add(() -> {
                        for (int i = s0; i < s1; i++) {
                            for (int k = 0; k < nsp; k++) {
                                double v = 0;
                                if (k == spc[i]) {
                                    int off = i * nq;
                                    for (int j = 0; j < nq; j++) v -= c[j] * P[off + j];
                                } else {
                                    kernel.evaluate(k, u, i * d, q, pAlt, 0, dAlt, 0, scr2);
                                    for (int j = 0; j < nq; j++) v -= c[j] * pAlt[j];
                                }
                                potK[i * nsp + k] = v;
                            }
                        }
                        return null;
                    });
                }
            }
        }

        void evaluateAll() {
            try {
                List<Future<Void>> fs = pool.invokeAll(tasks);
                for (Future<Void> f : fs) f.get();
            } catch (InterruptedException e) {
                cancelled = true;
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }

        void run(ProgressListener listener) {
            // flat initial distribution in ln-space (the maximum-entropy starting point)
            for (int i = 0; i < N; i++) {
                for (int tries = 0; tries < 10000; tries++) {
                    for (int k = 0; k < d; k++) u[i * d + k] = lo[k] + rng.nextDouble() * (hi[k] - lo[k]);
                    if (!ratioConstraint) break;
                    double dr = u[i * d + 1] - u[i * d];
                    if (dr >= lnRatioLo && dr <= lnRatioHi) break;
                }
                if (kernel.sortParameters()) Arrays.sort(u, i * d, i * d + d);
                if (ratioConstraint) applyRatio(i);
                spc[i] = nsp > 1 ? rng.nextInt(nsp) : 0;
            }
            if (fixedFraction) {
                // start with round(f N) particles of species 0 (the penalty then drives the NUMBER fraction to f)
                int n0 = (int) Math.round(set.fixedSpeciesFraction * N);
                for (int i = 0; i < N; i++) spc[i] = i < n0 ? 0 : 1;
                for (int i = N - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int tmp = spc[i]; spc[i] = spc[j]; spc[j] = tmp; }
            }

            final int A = set.annealSteps, E = set.equilibrationSteps, S = set.sampleSteps;
            final int total = A + E + S;
            traceChi2 = new double[total];
            traceT = new double[total];

            final int nFree = set.fitBackground ? 2 : 1;
            final double dof = Math.max(1, nq - nFree);
            final double invN = 1.0 / N;

            double logT = 0, logT0 = 0, logTmin = 0;
            double chi2ema = 0;
            double chi2MinSum = 0;
            int chi2MinCount = 0;
            double target = set.targetChi2;
            double logTAccum = 0;
            int logTCount = 0;
            double logTFrozen = 0;
            final double fixedLogT = set.autoTemperature ? Double.NaN : Math.log(Math.max(set.temperature, 1e-300));

            for (int j = 0; j < nq; j++) eig[j] = 1.0 + 0.01 * rng.nextDouble();

            for (int t = 0; t < total && !cancelled; t++) {
                evaluateAll();
                if (cancelled) break;

                // ensemble average
                Arrays.fill(Ibar, 0);
                for (int i = 0; i < N; i++) {
                    int off = i * nq;
                    for (int j = 0; j < nq; j++) Ibar[j] += P[off + j];
                }
                for (int j = 0; j < nq; j++) Ibar[j] *= invN;

                // error-floor continuation during the quench: sigma_eff^2 = sigma^2 + (phi(t) I_max)^2,
                // phi decreasing geometrically to 0 at 2/3 of the quench. Lets the strong low-q signal set
                // the sizes first when relative errors over many decades would otherwise let the many weak
                // high-q points drive the profiled scale to ~0. chi2_min is measured on the true errors.
                if (set.errorFloorStart > 0 && t <= A) {
                    double frac = t / (0.67 * A);
                    double phi = frac >= 1 ? 0 : set.errorFloorStart * Math.pow(1e-6, frac);
                    double fl = phi * iMax;
                    for (int j = 0; j < nq; j++) {
                        double se = Math.sqrt(sig[j] * sig[j] + fl * fl);
                        invSig[j] = 1.0 / se;
                        w[j] = invSig[j] * invSig[j];
                    }
                }

                // profile scale (and background) by weighted least squares
                double s, b = 0;
                {
                    double sxx = 0, sx = 0, sw = 0, sxy = 0, sy = 0;
                    for (int j = 0; j < nq; j++) {
                        double wj = w[j], x = Ibar[j], y = I[j];
                        sxx += wj * x * x; sx += wj * x; sw += wj; sxy += wj * x * y; sy += wj * y;
                    }
                    if (set.fitBackground) {
                        double det = sxx * sw - sx * sx;
                        s = (sxy * sw - sx * sy) / det;
                        b = (sxx * sy - sx * sxy) / det;
                        if (!(s > 0)) { s = Math.max(sxy / sxx, 1e-300); b = 0; }
                    } else {
                        s = Math.max(sxy / sxx, 1e-300);
                    }
                }

                double chi2 = 0;
                for (int j = 0; j < nq; j++) {
                    double rj = (I[j] - s * Ibar[j] - b) * invSig[j];
                    chi2 += rj * rj;
                    c[j] = s * rj * invSig[j];           // dF/dmu(u) = - sum_j c_j P(q_j;u)
                }
                double chi2red = chi2 / dof;

                // ---- temperature -----------------------------------------------------------
                if (t == 0) {
                    // T0: spread of the first-variation potential over the initial particles,
                    // so exp(-V/T0) is nearly flat -> start from an almost uniform distribution
                    double vmin = Double.POSITIVE_INFINITY, vmax = Double.NEGATIVE_INFINITY;
                    for (int i = 0; i < N; i++) {
                        double v = 0;
                        int off = i * nq;
                        for (int j = 0; j < nq; j++) v -= c[j] * P[off + j];
                        vmin = Math.min(vmin, v);
                        vmax = Math.max(vmax, v);
                    }
                    double T0 = vmax - vmin;
                    if (!(T0 > 0) || Double.isInfinite(T0)) T0 = Math.max(chi2, 1.0);
                    logT0 = Math.log(T0);
                    logTmin = logT0 + Math.log(1e-10);
                    logT = logT0;
                    chi2ema = chi2red;
                } else {
                    chi2ema += 0.05 * (chi2red - chi2ema);
                }

                String phase;
                if (t < A) {
                    // quench: geometric cooling over the first 2/3, then hold to measure the best chi^2
                    phase = "anneal";
                    double frac = Math.min(1.0, t / (0.67 * A));
                    if (set.autoTemperature) {
                        logT = logT0 + frac * (logTmin - logT0);
                        if (t >= A - A / 6) { chi2MinSum += chi2red; chi2MinCount++; }
                    } else {
                        logT = Math.min(logT0, logT0 + frac * (fixedLogT - logT0));
                    }
                } else if (t < A + E) {
                    phase = "equilibrate";
                    if (set.autoTemperature) {
                        if (t == A) {
                            // discrepancy relative to the best attainable fit: smoothest distribution whose
                            // chi^2 is within one standard deviation (sqrt(2/dof)) of the quenched optimum,
                            // but never tighter than the absolute target (Morozov, chi2_red = 1)
                            chi2Min = chi2MinCount > 0 ? chi2MinSum / chi2MinCount : chi2ema;
                            target = Math.max(set.targetChi2, chi2Min + set.chi2Tolerance * Math.sqrt(2.0 / dof));
                            chi2Target = target;
                        }
                        logT = controller(logT, chi2ema, target, logT0, logTmin);
                        if (t >= A + E / 2) { logTAccum += logT; logTCount++; }
                    } else {
                        logT = fixedLogT;
                    }
                } else {
                    phase = "sample";
                    if (t == A + E) logTFrozen = set.autoTemperature
                            ? (logTCount > 0 ? logTAccum / logTCount : logT) : fixedLogT;
                    logT = logTFrozen;
                }
                double T = Math.exp(logT);
                traceChi2[t] = chi2red;
                if (t >= (int) Math.ceil(0.67 * A) && t < A) chi2Best = Math.min(chi2Best, chi2red);
                traceT[t] = T;

                // ---- sampling (current state, before the move) -----------------------------
                if (t >= A + E && ((t - A - E) % Math.max(1, set.sampleEvery) == 0)) {
                    accumulate(s, b, chi2red);
                }

                // ---- species (core contents): mean-field Gibbs update against the same potential
                // dF/dmu(u, k) = -sum_j c_j P_k(q_j; u); p(k | u) ∝ exp(-V_k / T), argmin while T ~ 0.
                // Only a fraction of particles is reconsidered per update so the ensemble changes gradually.
                if (nsp > 1 && t % Math.max(1, set.speciesUpdateEvery) == 0) speciesGibbs(T, logT <= logTmin + 1e-9);

                // ---- drift -----------------------------------------------------------------
                for (int i = 0; i < N; i++) {
                    double norm2 = 0;
                    for (int k = 0; k < d; k++) {
                        int off = (i * d + k) * nq;
                        double g = 0;
                        for (int j = 0; j < nq; j++) g += c[j] * dP[off + j];
                        drift[i * d + k] = g;
                        norm2 += g * g;
                    }
                    driftNorm[i] = Math.sqrt(norm2);
                }

                // ---- fixed number fraction (profile scans): penalty (kappa/2)(f_num - f*)^2 acts on geometry too,
                // because the number weight 1/N_fs of a particle depends on its size
                if (fixedFraction) addFractionPenaltyDrift();

                // ---- distribution-shape prior: drift lambda*T*grad ln G_theta, theta re-fitted by EM
                if (prior != null && t >= A) {
                    if (t == A || t % Math.max(1, set.priorUpdateEvery) == 0) prior.update(u, N, d, 3);
                    prior.addDrift(u, N, d, set.priorStrength * T, drift);
                }

                // ---- time step from stability of the stiffest (collective) mode ---------------
                // Gauss-Newton matrix of the particle system: M = (s^2/N) sum_{i,k} g_ik g_ik^T,
                // g_ik(q_j) = dP_ik(q_j)/sigma_j.  Explicit Euler is stable for dt < 2/lambda_max(M);
                // we use dt = 1/lambda_max. A global time change does not alter the stationary state.
                // ---- per-particle preconditioning during the quench --------------------------------
                // mobility m_ik = 1 / (s^2 sum_j (dP_ik(q_j)/sigma_j)^2): each particle moves on its own
                // Fisher-information scale. Needed when the weights span many decades (e.g. relative errors
                // on a curve over 6 decades), otherwise the stiffest particle freezes all others.
                // A position-dependent mobility needs a T * dm/du correction for exact sampling; it is
                // therefore only used while T ~ 0 (quench). Heating and sampling use m = 1.
                boolean precond = set.preconditionQuench && t < A;
                if (precond) {
                    for (int col = 0; col < N * d; col++) {
                        int off = col * nq;
                        double g2 = 0;
                        for (int j = 0; j < nq; j++) { double g = dP[off + j] * invSig[j]; g2 += g * g; }
                        mob[col] = s * s * g2;
                    }
                    System.arraycopy(mob, 0, mobSorted, 0, N * d);
                    Arrays.sort(mobSorted);
                    double eps = 1e-8 * mobSorted[(N * d) / 2] + 1e-300;
                    for (int col = 0; col < N * d; col++) mob[col] = 1.0 / (mob[col] + eps);
                } else if (t == A && set.preconditionQuench) {
                    Arrays.fill(mob, 1.0);
                    Arrays.fill(vel, 0.0);   // velocities were in preconditioned units
                }

                double lam = largestEigenvalue(s);

                // ---- quench resampling: particles stranded against a bound, where they only hurt the fit,
                // cannot return by local moves; replace them by copies of good particles.
                // Optimisation phase only (T ~ 0), so no sampling bias.
                if (set.quenchResample && t > 0 && t < A - A / 6 && t % 10 == 0) resampleOutliers();

                // ---- birth-death (Fisher-Rao) moves: transfer mass between modes without crossing barriers.
                // Uses the overdamped time unit 1/lambda so W and FR parts run on the same clock.
                if (set.birthDeath && t > 0 && t % Math.max(1, set.birthDeathEvery) == 0)
                    lastChurn = birthDeath(T, Math.max(1, set.birthDeathEvery) / Math.max(lam, 1e-300));
                double maxMove = set.driftStep;

                if (!set.underdamped) {
                    // ---- overdamped Euler-Maruyama --------------------------------------------
                    double dt = 1.0 / Math.max(lam, 1e-300);
                    double dtCap = T > 0 ? set.noiseStep * set.noiseStep / (2.0 * T) : Double.POSITIVE_INFINITY;
                    for (int i = 0; i < N; i++) {
                        for (int k = 0; k < d; k++) {
                            int idx = i * d + k;
                            double dti = Math.min(dt * mob[idx], dtCap);
                            double m = dti * drift[idx];
                            if (m > maxMove) m = maxMove; else if (m < -maxMove) m = -maxMove;
                            u[idx] = reflect(u[idx] + m + Math.sqrt(2.0 * T * dti) * gaussian(), lo[k], hi[k]);
                        }
                        if (kernel.sortParameters()) Arrays.sort(u, i * d, i * d + d);
                        if (ratioConstraint) applyRatio(i);
                    }
                } else {
                    // ---- underdamped (kinetic) Langevin, O-B-A splitting -----------------------
                    // du = v dt,  dv = drift dt - gamma v dt + sqrt(2 gamma T) dW.
                    // Same stationary marginal in u; momentum moves along weakly determined
                    // (small-eigenvalue) directions ~ 1/(gamma h) times faster per step.
                    // with preconditioning the mobility acts as an inverse mass per coordinate
                    double h = 1.0 / Math.sqrt(Math.max(lam, 1e-300));
                    if (T > 0 && !precond) h = Math.min(h, set.noiseStep / Math.sqrt(T));
                    double mobCap = T > 0 ? set.noiseStep * set.noiseStep / (h * h * T) : Double.POSITIVE_INFINITY;
                    double gh = set.frictionPerStep;
                    double decay = Math.exp(-gh);
                    double vfac = Math.max(0, T * (1.0 - decay * decay));
                    for (int i = 0; i < N; i++) {
                        for (int k = 0; k < d; k++) {
                            int idx = i * d + k;
                            double mi = Math.min(mob[idx], mobCap);
                            double vv = vel[idx] * decay + Math.sqrt(vfac * mi) * gaussian();   // O
                            vv += h * mi * drift[idx];                                        // B
                            double m = h * vv;                                   // A
                            if (m > maxMove) { m = maxMove; vv = m / h; }
                            else if (m < -maxMove) { m = -maxMove; vv = m / h; }
                            double x = u[idx] + m;
                            // reflecting wall: mirror position, reverse velocity
                            if (x < lo[k] || x > hi[k]) { x = reflect(x, lo[k], hi[k]); vv = -vv; }
                            u[idx] = x;
                            vel[idx] = vv;
                        }
                        if (kernel.sortParameters()) sortWithVelocity(i * d, d);
                        if (ratioConstraint) applyRatio(i);
                    }
                }

                if (listener != null && (t % 10 == 0 || t == total - 1))
                    listener.update(index, t, total, phase, T, chi2red);
                if (DEBUG && t % 250 == 0)
                    System.out.printf("   [debug] t=%d lam=%.3e churn=%.4f%n", t, lam, lastChurn);
            }
            tempUsed = Math.exp(logTFrozen);
        }

        /**
         * Birth-death step for the Wasserstein-Fisher-Rao flow (Rotskoff et al. 2019; Lu, Lu & Nolen 2019).
         * Rate per particle: beta_i = V(u_i) + T ln mu(u_i) - <.>, with V = dF/dmu = -sum_j c_j P(q_j;u_i)
         * and mu estimated by a Gaussian KDE in ln-space. beta > 0: particle is replaced by a copy of a
         * random particle; beta < 0: particle is cloned over a random particle. N stays constant and the
         * stationary distribution, mu ∝ exp(-V/T), is the same as that of the Langevin part.
         */
        private double birthDeath(double T, double tau) {
            // potential from the stored P (c was computed for the current state)
            for (int i = 0; i < N; i++) {
                double v = 0;
                int off = i * nq;
                for (int j = 0; j < nq; j++) v -= c[j] * P[off + j];
                pot[i] = v;
            }
            if (T > 0) addEntropyTerm(T);

            double mean = 0;
            for (int i = 0; i < N; i++) mean += pot[i];
            mean /= N;
            for (int i = 0; i < N; i++) pot[i] -= mean;

            double a = set.birthDeathRate * tau;
            int events = 0;
            for (int i = 0; i < N; i++) {
                double p = 1.0 - Math.exp(-a * Math.abs(pot[i]));
                if (p > 0.5) p = 0.5;
                if (rng.nextDouble() >= p) continue;
                events++;
                int j = rng.nextInt(N - 1);
                if (j >= i) j++;
                if (pot[i] > 0) copyParticle(j, i);   // i dies, j reproduces
                else copyParticle(i, j);              // i reproduces, j dies
            }
            return (double) events / N;
        }

        /**
         * Replace particles stranded against a parameter bound (within 2 % in ln-space) whose potential is
         * above the median by clones of below-median particles (<= 5 % of N per call). Such particles are
         * pushed outward (a too-large particle can only shed its intensity by growing past the q-window) and
         * cannot come back by local moves.
         */
        private void resampleOutliers() {
            for (int i = 0; i < N; i++) {
                double v = 0;
                int off = i * nq;
                for (int j = 0; j < nq; j++) v -= c[j] * P[off + j];
                pot[i] = v;
            }
            double[] srt = pot.clone();
            Arrays.sort(srt);
            double med = srt[N / 2];
            int[] donors = new int[N];
            int nd = 0;
            for (int i = 0; i < N; i++) if (pot[i] <= med && !atBound(i)) donors[nd++] = i;
            if (nd == 0) return;
            int maxRepl = Math.max(1, N / 20), done = 0;
            for (int i = 0; i < N && done < maxRepl; i++) {
                if (pot[i] <= med || !atBound(i)) continue;
                int j = donors[rng.nextInt(nd)];
                copyParticle(j, i);
                done++;
            }
            lastChurn = (double) done / N;
        }

        private boolean atBound(int i) {
            for (int k = 0; k < d; k++) {
                double tol = 0.02 * (hi[k] - lo[k]);
                double x = u[i * d + k];
                if (x - lo[k] < tol || hi[k] - x < tol) return true;
            }
            return false;
        }

        /** adds T * ln(KDE density) to pot[], Gaussian kernel, Scott bandwidth per dimension */
        private void addEntropyTerm(double T) {
            double[] bw = new double[d];
            double fac = Math.pow(N, -1.0 / (d + 4));
            for (int k = 0; k < d; k++) {
                double m = 0, m2 = 0;
                for (int i = 0; i < N; i++) { double x = u[i * d + k]; m += x; m2 += x * x; }
                m /= N;
                double sd = Math.sqrt(Math.max(1e-12, m2 / N - m * m));
                bw[k] = Math.max(0.3 * sd * fac, 1e-3 * (hi[k] - lo[k]));   // narrow: keeps separate modes separate
            }
            for (int i = 0; i < N; i++) {
                double dens = 0;
                for (int j = 0; j < N; j++) {
                    double e = 0;
                    for (int k = 0; k < d; k++) {
                        double z = (u[i * d + k] - u[j * d + k]) / bw[k];
                        e += z * z;
                    }
                    if (e < 50) dens += Math.exp(-0.5 * e);
                }
                pot[i] += T * Math.log(dens);
            }
        }

        private void copyParticle(int from, int to) {
            // with a fixed species fraction the destination keeps its label so the counts cannot drift
            if (!fixedFraction) spc[to] = spc[from];
            for (int k = 0; k < d; k++) {
                double x = u[from * d + k] + set.birthDeathJitter * gaussian();
                u[to * d + k] = reflect(x, lo[k], hi[k]);
                vel[to * d + k] = vel[from * d + k];
            }
            if (kernel.sortParameters()) sortWithVelocity(to * d, d);
            if (ratioConstraint) applyRatio(to);
        }

        /**
         * Spheroid aspect-ratio window: reflect ln(c/a) into [lnRatioLo, lnRatioHi] keeping ln(a*c) fixed,
         * then re-apply the box. A couple of passes settle the rare corner cases.
         */
        private void applyRatio(int i) {
            int ia = i * d, ic = i * d + 1;
            for (int pass = 0; pass < 4; pass++) {
                double dr = u[ic] - u[ia];
                if (dr >= lnRatioLo && dr <= lnRatioHi
                        && u[ia] >= lo[0] && u[ia] <= hi[0] && u[ic] >= lo[1] && u[ic] <= hi[1]) return;
                double sm = u[ic] + u[ia];
                double vd = vel[ic] - vel[ia], vs = vel[ic] + vel[ia];
                if (dr < lnRatioLo || dr > lnRatioHi) {
                    double nd = Double.isInfinite(lnRatioLo) ? Math.min(dr, 2 * lnRatioHi - dr)
                            : Double.isInfinite(lnRatioHi) ? Math.max(dr, 2 * lnRatioLo - dr)
                            : reflect(dr, lnRatioLo, lnRatioHi);
                    dr = nd;
                    vd = -vd;
                }
                u[ia] = reflect(0.5 * (sm - dr), lo[0], hi[0]);
                u[ic] = reflect(0.5 * (sm + dr), lo[1], hi[1]);
                vel[ia] = 0.5 * (vs - vd);
                vel[ic] = 0.5 * (vs + vd);
            }
        }

        private void speciesGibbs(double T, boolean zeroT) {
            try {
                for (Future<Void> f : pool.invokeAll(speciesTasks)) f.get();
            } catch (InterruptedException e) {
                cancelled = true; Thread.currentThread().interrupt(); return;
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
            if (fixedFraction) addFractionPenaltyToLabelPotentials();
            double[] p = new double[nsp];
            for (int i = 0; i < N; i++) {
                if (rng.nextDouble() >= set.speciesFlipRate) continue;
                double vmin = Double.POSITIVE_INFINITY;
                int kmin = 0;
                for (int k = 0; k < nsp; k++) if (potK[i * nsp + k] < vmin) { vmin = potK[i * nsp + k]; kmin = k; }
                if (zeroT || !(T > 0)) { spc[i] = kmin; continue; }
                double sum = 0;
                for (int k = 0; k < nsp; k++) { p[k] = Math.exp(-(potK[i * nsp + k] - vmin) / T); sum += p[k]; }
                double r = rng.nextDouble() * sum;
                int k = 0;
                while (k < nsp - 1 && (r -= p[k]) > 0) k++;
                spc[i] = k;
            }
        }

        /** number weight 1/N_fs of particle i with label k (scaled; only ratios matter) */
        private double numberWeight(int i, int k) {
            return 1.0 / (kernel.forwardScatteringPower(k, u, i * d) / (vref2));
        }

        /** current number fraction of species 0 and the sums behind it: {f, W0, W} */
        private double[] numberFraction() {
            double w0 = 0, w = 0;
            for (int i = 0; i < N; i++) { double wi = numberWeight(i, spc[i]); w += wi; if (spc[i] == 0) w0 += wi; }
            return new double[]{w0 / w, w0, w};
        }

        private void addFractionPenaltyToLabelPotentials() {
            double[] fw = numberFraction();
            double W0 = fw[1], W = fw[2], kap = set.fractionPenalty, ft = set.fixedSpeciesFraction;
            for (int i = 0; i < N; i++) {
                double wc = numberWeight(i, spc[i]);
                double base0 = W0 - (spc[i] == 0 ? wc : 0), base = W - wc;
                for (int k = 0; k < nsp; k++) {
                    double wk = numberWeight(i, k);
                    double fk = (base0 + (k == 0 ? wk : 0)) / (base + wk);
                    potK[i * nsp + k] += N * 0.5 * kap * (fk - ft) * (fk - ft);
                }
            }
        }

        private void addFractionPenaltyDrift() {
            double[] fw = numberFraction();
            double f = fw[0], W = fw[2], kap = set.fractionPenalty, ft = set.fixedSpeciesFraction;
            double h = 1e-5;
            double pre = -N * kap * (f - ft);
            for (int i = 0; i < N; i++) {
                int k0 = spc[i];
                double wi = numberWeight(i, k0);
                double ind = (k0 == 0 ? 1.0 : 0.0) - f;
                for (int k = 0; k < d; k++) {
                    int idx = i * d + k;
                    double keep = u[idx];
                    u[idx] = keep + h; double lp = Math.log(kernel.forwardScatteringPower(k0, u, i * d));
                    u[idx] = keep - h; double lm = Math.log(kernel.forwardScatteringPower(k0, u, i * d));
                    u[idx] = keep;
                    double dlnN = (lp - lm) / (2 * h);
                    double df = -wi * dlnN * ind / W;     // d f / d u_ik
                    drift[idx] += pre * df;
                }
            }
        }

        /**
         * Count-preserving label moves (fixed species fraction, two species): pair a species-0 particle i with a
         * species-1 particle j and swap their labels. Gain = [V1(i) - V0(i)] + [V0(j) - V1(j)]. At T ~ 0 the
         * best pairs with negative gain are swapped; at T > 0 random pairs are accepted with Metropolis
         * probability. At most speciesFlipRate * N swaps per update.
         */
        private void speciesSwaps(double T, boolean zeroT) {
            int nSw = Math.max(1, (int) Math.round(set.speciesFlipRate * N));
            if (zeroT || !(T > 0)) {
                Integer[] zeros = new Integer[N], ones = new Integer[N];
                int n0 = 0, n1 = 0;
                for (int i = 0; i < N; i++) { if (spc[i] == 0) zeros[n0++] = i; else ones[n1++] = i; }
                // benefit of turning a 0 into 1 (most negative first), and of a 1 into 0
                Integer[] z = Arrays.copyOf(zeros, n0), o = Arrays.copyOf(ones, n1);
                Arrays.sort(z, (x, y) -> Double.compare(potK[x * nsp + 1] - potK[x * nsp], potK[y * nsp + 1] - potK[y * nsp]));
                Arrays.sort(o, (x, y) -> Double.compare(potK[x * nsp] - potK[x * nsp + 1], potK[y * nsp] - potK[y * nsp + 1]));
                for (int k = 0; k < Math.min(nSw, Math.min(n0, n1)); k++) {
                    int i = z[k], j = o[k];
                    double gain = (potK[i * nsp + 1] - potK[i * nsp]) + (potK[j * nsp] - potK[j * nsp + 1]);
                    if (gain >= 0) break;
                    spc[i] = 1; spc[j] = 0;
                }
            } else {
                for (int k = 0; k < nSw; k++) {
                    int i = rng.nextInt(N), j = rng.nextInt(N);
                    if (spc[i] == spc[j]) continue;
                    if (spc[i] == 1) { int tmp = i; i = j; j = tmp; }   // i: species 0, j: species 1
                    double gain = (potK[i * nsp + 1] - potK[i * nsp]) + (potK[j * nsp] - potK[j * nsp + 1]);
                    if (gain < 0 || rng.nextDouble() < Math.exp(-gain / T)) { spc[i] = 1; spc[j] = 0; }
                }
            }
        }

        /** insertion sort of one particle's parameters, carrying velocities along */
        private void sortWithVelocity(int off, int n) {
            for (int a = 1; a < n; a++) {
                double ku = u[off + a], kv = vel[off + a];
                int b = a - 1;
                while (b >= 0 && u[off + b] > ku) {
                    u[off + b + 1] = u[off + b];
                    vel[off + b + 1] = vel[off + b];
                    b--;
                }
                u[off + b + 1] = ku;
                vel[off + b + 1] = kv;
            }
        }

        /** a few warm-started power iterations on M = (s^2/N) sum g g^T (nq x nq, never formed) */
        private double largestEigenvalue(double s) {
            double lam = 0;
            for (int it = 0; it < 4; it++) {
                double nrm = 0;
                for (int j = 0; j < nq; j++) nrm += eig[j] * eig[j];
                nrm = Math.sqrt(nrm);
                for (int j = 0; j < nq; j++) eig[j] /= nrm;
                Arrays.fill(eigTmp, 0);
                for (int col = 0; col < N * d; col++) {
                    int off = col * nq;
                    double dot = 0;
                    for (int j = 0; j < nq; j++) dot += dP[off + j] * invSig[j] * eig[j];
                    dot *= mob[col];
                    for (int j = 0; j < nq; j++) eigTmp[j] += dot * dP[off + j] * invSig[j];
                }
                double f = s * s / N;
                lam = 0;
                for (int j = 0; j < nq; j++) { eigTmp[j] *= f; lam += eigTmp[j] * eig[j]; }
                System.arraycopy(eigTmp, 0, eig, 0, nq);
            }
            return lam;
        }

        private double controller(double logT, double chi2ema, double target, double logT0, double logTmin) {
            double err = Math.log(target / Math.max(chi2ema, 1e-300));
            // scale the error by the chi^2 tolerance so the controller reacts to differences that matter
            err /= Math.max(1e-3, Math.sqrt(2.0 / Math.max(1, q.length - 2)));
            if (err > 1) err = 1; else if (err < -1) err = -1;
            logT += set.controllerGain * err;
            if (logT > logT0) logT = logT0;
            if (logT < logTmin) logT = logTmin;
            return logT;
        }

        private double gaussian() {
            // Marsaglia polar method; SplittableRandom has no nextGaussian in older JDKs
            double x, y, r2;
            do {
                x = 2 * rng.nextDouble() - 1;
                y = 2 * rng.nextDouble() - 1;
                r2 = x * x + y * y;
            } while (r2 >= 1 || r2 == 0);
            return x * Math.sqrt(-2 * Math.log(r2) / r2);
        }

        private void accumulate(double s, double b, double chi2red) {
            nSamples++;
            sSum += s; bSum += b; chi2Sum += chi2red;
            for (int j = 0; j < nq; j++) {
                double f = s * Ibar[j] + b;
                fitSum[j] += f;
                fitSq[j] += f * f;
            }
            int[] bin = new int[d];
            // particle = equal share of I(0) = n * N_fs (N_fs = forward-scattering power, V^2 for a homogeneous
            // body): number weight 1/N_fs, volume weight V/N_fs (both rescaled by a reference volume)
            double vref = Math.exp(0.5 * (sumLo + sumHi));
            for (int i = 0; i < N; i++) {
                double V = kernel.volume(u, i * d) / vref;
                int sp = spc[i];
                double Nfs = kernel.forwardScatteringPower(sp, u, i * d) / (vref * vref);
                double nw = 1.0 / Nfs, vw = V * nw;
                spIntW[sp] += 1.0; spVolW[sp] += vw; spNumW[sp] += nw;
                for (int k = 0; k < d; k++) spParV[sp][k] += vw * Math.exp(u[i * d + k]);
                for (int k = 0; k < d; k++) {
                    double uk = u[i * d + k];
                    int bi = (int) ((uk - lo[k]) / (hi[k] - lo[k]) * B);
                    if (bi < 0) bi = 0; else if (bi >= B) bi = B - 1;
                    bin[k] = bi;
                    intHist[k][bi] += 1.0;
                    volHist[k][bi] += vw;
                    numHist[k][bi] += nw;
                    double p = Math.exp(uk);
                    iSum[k] += p; iSq[k] += p * p;
                    vSum[k] += vw * p; vSq[k] += vw * p * p;
                    nSum[k] += nw * p; nSq[k] += nw * p * p;
                }
                for (int pi = 0; pi < pairs.length; pi++) pairHist[pi][bin[pairs[pi][0]]][bin[pairs[pi][1]]] += vw;
                iW += 1.0; vW += vw; nW += nw;
                sumV += V * vref;
                sumInvV += vw * V;    // sum of volume-weighted V (relative to vref)
                sumInvV2 += nw * V;   // sum of number-weighted V
            }
        }
    }

    private static double reflect(double x, double lo, double hi) {
        double L = hi - lo;
        double y = (x - lo) % (2 * L);
        if (y < 0) y += 2 * L;
        if (y > L) y = 2 * L - y;
        return lo + y;
    }

    // ------------------------------------------------------------------------------------------

    private LangevinResult combine(List<Replica> reps) {
        Replica r0 = reps.get(0);
        int d = r0.d, B = r0.B, nq = r0.nq, R = reps.size();
        LangevinResult res = new LangevinResult();
        res.shapeLabel = kernel.label();
        res.parameterNames = kernel.parameterNames();
        res.dim = d;
        res.replicas = R;
        res.particles = r0.N;
        res.q = q.clone();
        res.intensity = I.clone();
        res.sigma = sig.clone();

        res.binCenters = new double[d][B];
        res.binLnEdges = new double[d][B + 1];
        for (int k = 0; k < d; k++) {
            double width = (hi[k] - lo[k]) / B;
            for (int b = 0; b <= B; b++) res.binLnEdges[k][b] = lo[k] + b * width;
            for (int b = 0; b < B; b++) res.binCenters[k][b] = Math.exp(lo[k] + (b + 0.5) * width);
        }

        res.intensityDensity = new double[d][B];
        res.intensityDensityStd = new double[d][B];
        res.volumeDensity = new double[d][B];
        res.volumeDensityStd = new double[d][B];
        res.numberDensity = new double[d][B];
        res.numberDensityStd = new double[d][B];
        for (int k = 0; k < d; k++) {
            double width = (hi[k] - lo[k]) / B;
            double[][] ii = new double[R][], vv = new double[R][], nn = new double[R][];
            for (int r = 0; r < R; r++) {
                ii[r] = normalise(reps.get(r).intHist[k], width);
                vv[r] = normalise(reps.get(r).volHist[k], width);
                nn[r] = normalise(reps.get(r).numHist[k], width);
            }
            meanStd(ii, res.intensityDensity[k], res.intensityDensityStd[k]);
            meanStd(vv, res.volumeDensity[k], res.volumeDensityStd[k]);
            meanStd(nn, res.numberDensity[k], res.numberDensityStd[k]);
        }

        res.pairs = r0.pairs;
        res.pairDensity = new double[r0.pairs.length][B][B];
        for (int p = 0; p < r0.pairs.length; p++) {
            double tot = 0;
            for (Replica r : reps) for (int a = 0; a < B; a++) for (int b = 0; b < B; b++) tot += r.pairHist[p][a][b];
            for (Replica r : reps) for (int a = 0; a < B; a++) for (int b = 0; b < B; b++)
                res.pairDensity[p][a][b] += r.pairHist[p][a][b] / Math.max(tot, 1e-300);
        }

        res.fit = new double[nq];
        res.fitSpread = new double[nq];
        double ns = 0, sS = 0, bS = 0;
        double[] fSq = new double[nq];
        for (Replica r : reps) {
            ns += r.nSamples; sS += r.sSum; bS += r.bSum;
            for (int j = 0; j < nq; j++) { res.fit[j] += r.fitSum[j]; fSq[j] += r.fitSq[j]; }
        }
        ns = Math.max(ns, 1);
        double chi2 = 0;
        for (int j = 0; j < nq; j++) {
            res.fit[j] /= ns;
            res.fitSpread[j] = Math.sqrt(Math.max(0, fSq[j] / ns - res.fit[j] * res.fit[j]));
            double rr = (I[j] - res.fit[j]) / sig[j];
            chi2 += rr * rr;
        }
        res.chi2Reduced = chi2 / Math.max(1, nq - (set.fitBackground ? 2 : 1));
        // residual randomness: lag-1 autocorrelation, Wald-Wolfowitz runs test on signs, longest run (CorMap-like)
        {
            double[] rr = new double[nq];
            for (int j = 0; j < nq; j++) rr[j] = (I[j] - res.fit[j]) / sig[j];
            double v = 0, c1 = 0;
            for (int j = 0; j < nq; j++) v += rr[j] * rr[j];
            for (int j = 0; j + 1 < nq; j++) c1 += rr[j] * rr[j + 1];
            res.residualLag1 = v > 0 ? c1 / v : 0;
            int np = 0, nm = 0, runs = 0, longest = 0, cur = 0;
            int prev = 0;
            for (int j = 0; j < nq; j++) {
                int sg = rr[j] >= 0 ? 1 : -1;
                if (sg > 0) np++; else nm++;
                if (sg != prev) { runs++; cur = 1; } else cur++;
                longest = Math.max(longest, cur);
                prev = sg;
            }
            double n = np + nm;
            double mu = 2.0 * np * nm / n + 1;
            double var = 2.0 * np * nm * (2.0 * np * nm - n) / (n * n * (n - 1));
            res.runsZ = var > 0 ? (runs - mu) / Math.sqrt(var) : 0;
            res.longestRun = longest;
            // P(longest run >= L) for n fair signs, Poisson approximation of run starts
            res.longestRunP = Math.min(1.0, 1.0 - Math.exp(-(n - longest + 1) * Math.pow(0.5, longest)));
        }
        // degenerate-fit check: compare with the "null" fit I_fit = 0
        double chi2Null = 0;
        for (int j = 0; j < nq; j++) { double z = I[j] / sig[j]; chi2Null += z * z; }
        res.fitExplainedFraction = 1.0 - chi2 / Math.max(chi2Null, 1e-300);
        if (res.fitExplainedFraction < 0.9)
            res.warning = String.format("FIT FAILED: the ensemble explains only %.1f %% of chi2 relative to a zero model. "
                    + "Check q-range, error model (deep minima with relative errors) and bounds.", 100 * res.fitExplainedFraction);
        res.scale = sS / ns;
        res.background = bS / ns;
        res.temperature = new double[R];
        res.chi2Min = new double[R];
        res.chi2Best = new double[R];
        res.chi2Target = new double[R];
        for (int r = 0; r < R; r++) {
            res.temperature[r] = reps.get(r).tempUsed;
            res.chi2Min[r] = reps.get(r).chi2Min;
            res.chi2Best[r] = reps.get(r).chi2Best;
            res.chi2Target[r] = reps.get(r).chi2Target;
        }

        res.intensityMean = new double[d]; res.intensityStd = new double[d];
        res.volumeMean = new double[d]; res.volumeStd = new double[d];
        res.numberMean = new double[d]; res.numberStd = new double[d];
        double iW = 0, vW = 0, nW = 0, sumV = 0, sumInvV = 0, sumInvV2 = 0;
        double[] iS = new double[d], iQ = new double[d], vS = new double[d], vQ = new double[d], nS = new double[d], nQ = new double[d];
        for (Replica r : reps) {
            iW += r.iW; vW += r.vW; nW += r.nW; sumV += r.sumV; sumInvV += r.sumInvV; sumInvV2 += r.sumInvV2;
            for (int k = 0; k < d; k++) {
                iS[k] += r.iSum[k]; iQ[k] += r.iSq[k];
                vS[k] += r.vSum[k]; vQ[k] += r.vSq[k];
                nS[k] += r.nSum[k]; nQ[k] += r.nSq[k];
            }
        }
        for (int k = 0; k < d; k++) {
            res.intensityMean[k] = iS[k] / iW;
            res.intensityStd[k] = Math.sqrt(Math.max(0, iQ[k] / iW - res.intensityMean[k] * res.intensityMean[k]));
            res.volumeMean[k] = vS[k] / vW;
            res.volumeStd[k] = Math.sqrt(Math.max(0, vQ[k] / vW - res.volumeMean[k] * res.volumeMean[k]));
            res.numberMean[k] = nS[k] / nW;
            res.numberStd[k] = Math.sqrt(Math.max(0, nQ[k] / nW - res.numberMean[k] * res.numberMean[k]));
        }
        // <V>_v = sum(w_v V)/sum(w_v),  <V>_n = sum(w_n V)/sum(w_n)   (V relative to vref)
        double vref = Math.exp(0.5 * (sumLo + sumHi));
        res.volumeAverageVolume = sumInvV / vW * vref;
        res.numberAverageVolume = sumInvV2 / nW * vref;
        res.intensityAverageVolume = sumV / iW;

        if (r0.prior != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("REMARK 265 SHAPE PRIOR : %d-COMPONENT GAUSSIAN MIXTURE IN LN-SPACE, STRENGTH %.2f%n",
                    set.priorComponents, set.priorStrength));
            for (Replica r : reps) sb.append(r.prior.summary(kernel.parameterNames()));
            res.priorSummary = sb.toString();
        }
        res.preparation = preparation;
        // ---- species fractions (mean +- spread over replicas) and volume-weighted mean parameters per species
        int nsp = r0.nsp;
        if (nsp > 1) {
            res.speciesNames = new String[nsp];
            for (int k = 0; k < nsp; k++) res.speciesNames[k] = kernel.speciesName(k);
            res.speciesIntensityFraction = new double[nsp]; res.speciesVolumeFraction = new double[nsp];
            res.speciesNumberFraction = new double[nsp]; res.speciesNumberFractionStd = new double[nsp];
            res.speciesMeanParams = new double[nsp][d];
            double[][] nf = new double[R][nsp];
            for (int r = 0; r < R; r++) {
                Replica rp = reps.get(r);
                double ti = 0, tv = 0, tn = 0;
                for (int k = 0; k < nsp; k++) { ti += rp.spIntW[k]; tv += rp.spVolW[k]; tn += rp.spNumW[k]; }
                for (int k = 0; k < nsp; k++) {
                    res.speciesIntensityFraction[k] += rp.spIntW[k] / ti / R;
                    res.speciesVolumeFraction[k] += rp.spVolW[k] / tv / R;
                    nf[r][k] = rp.spNumW[k] / tn;
                    res.speciesNumberFraction[k] += nf[r][k] / R;
                    for (int p = 0; p < d; p++)
                        res.speciesMeanParams[k][p] += rp.spVolW[k] > 0 ? rp.spParV[k][p] / rp.spVolW[k] / R : 0;
                }
            }
            if (R > 1) for (int k = 0; k < nsp; k++) {
                double m = res.speciesNumberFraction[k], v = 0;
                for (int r = 0; r < R; r++) v += (nf[r][k] - m) * (nf[r][k] - m);
                res.speciesNumberFractionStd[k] = Math.sqrt(v / (R - 1));
            }
        }
        // ---- mode diagnostic: how far is the final particle cloud from the best K-component Gaussian
        // mixture (all parameters, ln-space), K = 1..3. Large KL_1 that drops at K = 2 -> two populations.
        {
            int Nn = r0.N;
            double[][] pts = new double[Nn][d];
            for (int i = 0; i < Nn; i++) for (int k = 0; k < d; k++) pts[i][k] = r0.u[i * d + k];
            int[] all = new int[d];
            for (int k = 0; k < d; k++) all[k] = k;
            java.util.Random rnd = new java.util.Random(set.seed + 99);
            res.modeKL = new double[3];
            res.modeSummaries = new String[3];
            for (int K = 1; K <= 3; K++) {
                AdaptiveMixturePrior g = new AdaptiveMixturePrior(all, K, set.priorMinSd);
                g.update(r0.u, Nn, d, 60);
                res.modeKL[K - 1] = g.klFrom(pts, rnd);
                res.modeSummaries[K - 1] = g.summary(kernel.parameterNames());
            }
        }
        res.traceChi2 = r0.traceChi2;
        res.traceTemperature = r0.traceT;
        res.finalParticles = new double[r0.N][d];
        for (int i = 0; i < r0.N; i++)
            for (int k = 0; k < d; k++) res.finalParticles[i][k] = Math.exp(r0.u[i * d + k]);
        return res;
    }

    private static double[] normalise(double[] h, double width) {
        double tot = 0;
        for (double v : h) tot += v;
        double[] out = new double[h.length];
        if (tot <= 0) return out;
        for (int b = 0; b < h.length; b++) out[b] = h[b] / (tot * width);
        return out;
    }

    private static void meanStd(double[][] x, double[] mean, double[] std) {
        int R = x.length, n = mean.length;
        for (int b = 0; b < n; b++) {
            double s = 0, s2 = 0;
            for (double[] row : x) { s += row[b]; s2 += row[b] * row[b]; }
            mean[b] = s / R;
            std[b] = R > 1 ? Math.sqrt(Math.max(0, (s2 - s * s / R) / (R - 1))) : 0;
        }
    }
}
