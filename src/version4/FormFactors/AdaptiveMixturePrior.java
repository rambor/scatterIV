package version4.FormFactors;

/**
 * Adaptive Gaussian-mixture reference measure for the entropy term ("distribution-shape constraint").
 *
 * With a reference G_theta the fitter minimises   chi^2/2 + T * KL(mu || G_theta^lambda)
 * whose stationary state is   mu(u) ∝ G_theta(u)^lambda * exp(-V(u)/T).
 * theta (weights, means, full covariances in ln-space) is re-fitted to the particles by a few EM
 * iterations every few steps, so the user specifies only the NUMBER of modes (1 = Gaussian-like,
 * 2 = bimodal, ...) and which axes the constraint acts on, not where the modes are.
 *
 * The constraint is soft: the distribution may still deviate from the mixture wherever the data
 * require it; it only replaces "as flat as possible" by "as close to K Gaussian modes as possible".
 */
public final class AdaptiveMixturePrior {

    private final int[] axes;       // which particle parameters the prior acts on
    private final int m, K;         // dimension of the prior, number of components
    private final double minSd;     // floor on component standard deviations (ln units)
    private final double[] w;
    private final double[][] mean;
    private final double[][][] cov, inv;
    private final double[] logNorm;
    private boolean initialised = false;

    public AdaptiveMixturePrior(int[] axes, int components, double minSd) {
        this.axes = axes.clone();
        this.m = axes.length;
        if (m < 1 || m > 3) throw new IllegalArgumentException("prior acts on 1..3 axes");
        this.K = Math.max(1, components);
        this.minSd = minSd;
        w = new double[K];
        mean = new double[K][m];
        cov = new double[K][m][m];
        inv = new double[K][m][m];
        logNorm = new double[K];
    }

    public int components() { return K; }
    public int[] axes() { return axes.clone(); }

    /** initialise from particles: components placed at quantiles of the first prior axis */
    public void initialise(double[] u, int N, int d) {
        int a0 = axes[0];
        double[] x = new double[N];
        for (int i = 0; i < N; i++) x[i] = u[i * d + a0];
        java.util.Arrays.sort(x);
        // global moments
        double[] mu = new double[m];
        double[][] c = new double[m][m];
        for (int i = 0; i < N; i++) for (int p = 0; p < m; p++) mu[p] += u[i * d + axes[p]] / N;
        for (int i = 0; i < N; i++)
            for (int p = 0; p < m; p++)
                for (int r = 0; r < m; r++)
                    c[p][r] += (u[i * d + axes[p]] - mu[p]) * (u[i * d + axes[r]] - mu[r]) / N;
        for (int k = 0; k < K; k++) {
            w[k] = 1.0 / K;
            for (int p = 0; p < m; p++) mean[k][p] = mu[p];
            mean[k][0] = x[(int) Math.min(N - 1, (k + 0.5) / K * N)];
            for (int p = 0; p < m; p++) for (int r = 0; r < m; r++) cov[k][p][r] = c[p][r] / (K * K > 1 ? K : 1);
            finalizeComponent(k);
        }
        initialised = true;
    }

    /** a few EM iterations on the current particle cloud (warm-started) */
    public void update(double[] u, int N, int d, int iterations) {
        if (!initialised) initialise(u, N, d);
        double[] resp = new double[K];
        double[] x = new double[m];
        for (int it = 0; it < iterations; it++) {
            double[] sw = new double[K];
            double[][] sm = new double[K][m];
            double[][][] sc = new double[K][m][m];
            for (int i = 0; i < N; i++) {
                for (int p = 0; p < m; p++) x[p] = u[i * d + axes[p]];
                responsibilities(x, resp);
                for (int k = 0; k < K; k++) {
                    double r = resp[k];
                    sw[k] += r;
                    for (int p = 0; p < m; p++) {
                        sm[k][p] += r * x[p];
                        for (int q = 0; q < m; q++) sc[k][p][q] += r * x[p] * x[q];
                    }
                }
            }
            for (int k = 0; k < K; k++) {
                if (sw[k] < 0.02 * N) {
                    // dead component: re-seed at a random particle with the global spread
                    int i = (int) (Math.random() * N);
                    for (int p = 0; p < m; p++) mean[k][p] = u[i * d + axes[p]];
                    w[k] = 0.02;
                    finalizeComponent(k);
                    continue;
                }
                w[k] = sw[k] / N;
                for (int p = 0; p < m; p++) mean[k][p] = sm[k][p] / sw[k];
                for (int p = 0; p < m; p++)
                    for (int q = 0; q < m; q++)
                        cov[k][p][q] = sc[k][p][q] / sw[k] - mean[k][p] * mean[k][q];
                finalizeComponent(k);
            }
            double tot = 0;
            for (int k = 0; k < K; k++) tot += w[k];
            for (int k = 0; k < K; k++) w[k] /= tot;
        }
    }

    /**
     * Adds  scale * d/du ln G(u)  to the drift of every particle (only on the prior axes).
     * scale = lambda * T.
     */
    public void addDrift(double[] u, int N, int d, double scale, double[] drift) {
        double[] resp = new double[K];
        double[] x = new double[m];
        double[] diff = new double[m];
        for (int i = 0; i < N; i++) {
            for (int p = 0; p < m; p++) x[p] = u[i * d + axes[p]];
            responsibilities(x, resp);
            for (int k = 0; k < K; k++) {
                if (resp[k] < 1e-12) continue;
                for (int p = 0; p < m; p++) diff[p] = x[p] - mean[k][p];
                for (int p = 0; p < m; p++) {
                    double g = 0;
                    for (int q = 0; q < m; q++) g -= inv[k][p][q] * diff[q];
                    drift[i * d + axes[p]] += scale * resp[k] * g;
                }
            }
        }
    }

    private void responsibilities(double[] x, double[] resp) {
        double mx = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < K; k++) {
            double qf = 0;
            for (int p = 0; p < m; p++)
                for (int q = 0; q < m; q++)
                    qf += (x[p] - mean[k][p]) * inv[k][p][q] * (x[q] - mean[k][q]);
            resp[k] = Math.log(w[k]) + logNorm[k] - 0.5 * qf;
            mx = Math.max(mx, resp[k]);
        }
        double s = 0;
        for (int k = 0; k < K; k++) { resp[k] = Math.exp(resp[k] - mx); s += resp[k]; }
        for (int k = 0; k < K; k++) resp[k] /= s;
    }

    /** symmetrise, floor the variances, invert (m <= 3) */
    private void finalizeComponent(int k) {
        double[][] c = cov[k];
        for (int p = 0; p < m; p++) {
            for (int q = p + 1; q < m; q++) { double v = 0.5 * (c[p][q] + c[q][p]); c[p][q] = v; c[q][p] = v; }
            c[p][p] = Math.max(c[p][p], 0) + minSd * minSd;
        }
        double det;
        double[][] iv = inv[k];
        if (m == 1) {
            det = c[0][0];
            iv[0][0] = 1.0 / det;
        } else if (m == 2) {
            det = c[0][0] * c[1][1] - c[0][1] * c[1][0];
            if (det <= 0) { c[0][1] = c[1][0] = 0; det = c[0][0] * c[1][1]; }
            iv[0][0] = c[1][1] / det; iv[1][1] = c[0][0] / det;
            iv[0][1] = iv[1][0] = -c[0][1] / det;
        } else {
            double a = c[0][0], b = c[0][1], cc = c[0][2], e = c[1][1], f = c[1][2], i = c[2][2];
            double A = e * i - f * f, B = -(b * i - f * cc), C = b * f - e * cc;
            det = a * A + b * B + cc * C;
            if (det <= 0) {
                for (int p = 0; p < 3; p++) for (int q = 0; q < 3; q++) if (p != q) c[p][q] = 0;
                a = c[0][0]; e = c[1][1]; i = c[2][2]; b = cc = f = 0;
                A = e * i; B = 0; C = 0; det = a * e * i;
            }
            iv[0][0] = A / det;
            iv[0][1] = iv[1][0] = B / det;
            iv[0][2] = iv[2][0] = C / det;
            iv[1][1] = (a * i - cc * cc) / det;
            iv[1][2] = iv[2][1] = -(a * f - b * cc) / det;
            iv[2][2] = (a * e - b * b) / det;
        }
        logNorm[k] = -0.5 * Math.log(det) - 0.5 * m * Math.log(2 * Math.PI);
    }

    /** ln of the mixture density at x (prior-axis coordinates) */
    public double logDensity(double[] x) {
        double mx = Double.NEGATIVE_INFINITY;
        double[] t = new double[K];
        for (int k = 0; k < K; k++) {
            double qf = 0;
            for (int p = 0; p < m; p++)
                for (int q = 0; q < m; q++)
                    qf += (x[p] - mean[k][p]) * inv[k][p][q] * (x[q] - mean[k][q]);
            t[k] = Math.log(w[k]) + logNorm[k] - 0.5 * qf;
            mx = Math.max(mx, t[k]);
        }
        double sum = 0;
        for (int k = 0; k < K; k++) sum += Math.exp(t[k] - mx);
        return mx + Math.log(sum);
    }

    /** n samples from the mixture (rows of length m) */
    public double[][] sample(java.util.Random rnd, int n) {
        double[][] out = new double[n][m];
        for (int i = 0; i < n; i++) {
            double r = rnd.nextDouble(), acc = 0;
            int k = K - 1;
            for (int c = 0; c < K; c++) { acc += w[c]; if (r < acc) { k = c; break; } }
            // Cholesky of cov[k]
            double[][] L = new double[m][m];
            for (int a = 0; a < m; a++)
                for (int b = 0; b <= a; b++) {
                    double v = cov[k][a][b];
                    for (int c = 0; c < b; c++) v -= L[a][c] * L[b][c];
                    L[a][b] = a == b ? Math.sqrt(Math.max(v, 1e-300)) : v / L[b][b];
                }
            double[] z = new double[m];
            for (int p = 0; p < m; p++) z[p] = rnd.nextGaussian();
            for (int a = 0; a < m; a++) {
                double v = mean[k][a];
                for (int b = 0; b <= a; b++) v += L[a][b] * z[b];
                out[i][a] = v;
            }
        }
        return out;
    }

    /**
     * Estimated KL(points || mixture) in nats: mean of [ln KDE_loo(x_i) - ln G(x_i)], minus the same estimator
     * applied to an equal-size sample drawn from G (removes the KDE bias). ~0 when K Gaussian modes describe
     * the points; large when they contain structure the mixture cannot hold.
     */
    public double klFrom(double[][] pts, java.util.Random rnd) {
        return klRaw(pts) - klRaw(sample(rnd, pts.length));
    }

    private double klRaw(double[][] pts) {
        int n = pts.length;
        double[] h = new double[m];
        for (int p = 0; p < m; p++) {
            double mu = 0, v = 0;
            for (double[] x : pts) mu += x[p];
            mu /= n;
            for (double[] x : pts) v += (x[p] - mu) * (x[p] - mu);
            h[p] = Math.max(1e-4, 1.06 * Math.sqrt(v / n) * Math.pow(n, -1.0 / (m + 4)));
        }
        double logNormK = 0;
        for (int p = 0; p < m; p++) logNormK -= Math.log(h[p] * Math.sqrt(2 * Math.PI));
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double dens = 0;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                double e = 0;
                for (int p = 0; p < m; p++) { double z = (pts[i][p] - pts[j][p]) / h[p]; e += z * z; }
                if (e < 60) dens += Math.exp(-0.5 * e);
            }
            double lf = Math.log(Math.max(dens, 1e-300) / (n - 1)) + logNormK;
            sum += lf - logDensity(pts[i]);
        }
        return sum / n;
    }

    /** human-readable summary in real units: median exp(mean) and ln-width per axis */
    public String summary(String[] names) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < K; k++) {
            sb.append(String.format("REMARK 265   PRIOR COMPONENT %d  WEIGHT %.3f :", k + 1, w[k]));
            for (int p = 0; p < m; p++)
                sb.append(String.format("  %s ~ %.2f (sd_ln %.3f)", names[axes[p]], Math.exp(mean[k][p]), Math.sqrt(cov[k][p][p])));
            sb.append('\n');
        }
        return sb.toString();
    }
}
