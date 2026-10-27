package version4.FormFactors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prepares I(q) data before the distribution fit:
 *
 * <ol>
 *   <li><b>Error calibration.</b> The data are fitted with a band-limited (Moore / Shannon) expansion,
 *       P(r) = sum_n a_n sin(n pi r / D) on [0, D], which is the most flexible curve a particle of
 *       maximum dimension D can produce. Its number of parameters is ~ q_max D / pi, so its reduced chi^2
 *       (with the right degrees of freedom) measures how well the reported sigma describe the actual
 *       scatter. sigma is rescaled by sqrt(chi^2_red). If D is not given it is estimated as the smallest
 *       D whose chi^2_red is statistically indistinguishable from the best over a scan.
 *       A model-free cross-check (leave-one-out local quadratic prediction, robust median) is reported too.</li>
 *   <li><b>Rebinning.</b> Points are merged by inverse-variance weighting into bins of width
 *       pi / (D * pointsPerChannel). The noise autocorrelation of the band-limited residuals is measured
 *       first: merged / regridded data have correlated neighbours, and the variance of a bin mean of m points
 *       is then inflated by 1 + 2 sum_k (1 - k/m) rho_k. No information is lost while bins are narrower
 *       than the Shannon channel; the fit cost drops in proportion.</li>
 * </ol>
 * The fit itself always uses the raw (rebinned) data, never a smoothed curve.
 */
public final class DataPreparation {

    public static final class Report {
        public int pointsRaw, pointsUsed;
        public double dmax;               // Angstrom
        public boolean dmaxEstimated;
        public double shannonChannels;
        public double chi2RedBandLimited; // chi^2_red of the band-limited fit with the ORIGINAL sigma
        public double errorScaleBandLimited;
        public double errorScaleLocal;    // model-free cross-check
        public double errorScaleApplied = 1.0;
        public boolean rebinned;
        public double[] dScan, chi2Scan;  // D scan (if D was estimated)
        public double[] autocorrelation;  // noise autocorrelation rho_k of the band-limited residuals, k = 0..12
        public double meanVarianceInflation = 1.0;  // mean factor applied to bin variances for correlated noise

        public String toRemarks() {
            StringBuilder sb = new StringBuilder();
            sb.append("REMARK 265\n");
            sb.append("REMARK 265 DATA PREPARATION\n");
            sb.append(String.format("REMARK 265        DMAX (%s) : %.1f%n", dmaxEstimated ? "ESTIMATED" : "GIVEN    ", dmax));
            sb.append(String.format("REMARK 265            SHANNON CHANNELS : %.1f%n", shannonChannels));
            sb.append(String.format("REMARK 265  BAND-LIMITED FIT CHI2_RED  : %.3f   (with reported errors)%n", chi2RedBandLimited));
            sb.append(String.format("REMARK 265   ERROR SCALE (BAND-LIMITED) : %.3f%n", errorScaleBandLimited));
            sb.append(String.format("REMARK 265   ERROR SCALE (LOCAL CHECK)  : %.3f%n", errorScaleLocal));
            sb.append(String.format("REMARK 265          ERROR SCALE APPLIED : %.3f%n", errorScaleApplied));
            sb.append(String.format("REMARK 265               POINTS RAW/USED : %d / %d%s%n", pointsRaw, pointsUsed, rebinned ? " (rebinned)" : ""));
            if (autocorrelation != null)
                sb.append(String.format("REMARK 265  NOISE AUTOCORRELATION LAG 1/2/3 : %.2f / %.2f / %.2f%n",
                        autocorrelation[1], autocorrelation[2], autocorrelation[3]));
            if (rebinned)
                sb.append(String.format("REMARK 265  BIN VARIANCE INFLATION (MEAN)  : %.2f   (correlated noise)%n", meanVarianceInflation));
            return sb.toString();
        }
    }

    public final double[] q, I, sigma;
    public final Report report;

    private DataPreparation(double[] q, double[] I, double[] s, Report r) {
        this.q = q; this.I = I; this.sigma = s; this.report = r;
    }

    /**
     * @param dmaxHint  maximum dimension if known (e.g. from your P(r)); NaN or <= 0 to estimate
     * @param dmaxUpper upper end of the D scan when estimating (e.g. 2 * largest length bound * 1.2)
     */
    public static DataPreparation prepare(double[] q, double[] I, double[] sigma, double dmaxHint, double dmaxUpper,
                                          boolean calibrate, boolean rebin, double pointsPerChannel, boolean background) {
        int n = q.length;
        Report rep = new Report();
        rep.pointsRaw = n;
        double qmax = q[n - 1];

        // ---- D: given or estimated -------------------------------------------------------------
        double D;
        if (dmaxHint > 0) {
            D = dmaxHint;
            rep.dmaxEstimated = false;
        } else {
            double dLo = Math.max(3.0 * Math.PI / qmax, 10.0);
            double dHi = Math.max(dmaxUpper, 2 * dLo);
            int nd = 40;
            rep.dScan = new double[nd];
            rep.chi2Scan = new double[nd];
            double best = Double.POSITIVE_INFINITY;
            int[] dofs = new int[nd];
            for (int k = 0; k < nd; k++) {
                double d = dLo * Math.pow(dHi / dLo, k / (nd - 1.0));
                rep.dScan[k] = d;
                int[] dof = new int[1];
                rep.chi2Scan[k] = bandLimitedChi2(q, I, sigma, d, background, dof);
                dofs[k] = dof[0];
                if (dofs[k] > 5) best = Math.min(best, rep.chi2Scan[k]);
            }
            D = dHi;
            for (int k = 0; k < nd; k++) {
                if (dofs[k] <= 5) continue;
                double tol = best * (1.0 + 2.0 * Math.sqrt(2.0 / dofs[k]));
                if (rep.chi2Scan[k] <= tol) { D = rep.dScan[k]; break; }
            }
            rep.dmaxEstimated = true;
        }
        rep.dmax = D;
        rep.shannonChannels = (qmax - q[0]) * D / Math.PI;

        // ---- calibration -------------------------------------------------------------------------
        int[] dof = new int[1];
        double[] resid = new double[n];
        rep.chi2RedBandLimited = bandLimitedChi2(q, I, sigma, D, background, dof, resid);
        // noise autocorrelation between neighbouring points (merged / regridded data are correlated);
        // needed to propagate errors correctly when points are averaged into bins
        int maxLag = 12;
        rep.autocorrelation = new double[maxLag + 1];
        rep.autocorrelation[0] = 1;
        double var = 0;
        for (int j = 0; j < n; j++) var += resid[j] * resid[j];
        for (int lag = 1; lag <= maxLag && lag < n; lag++) {
            double c = 0;
            for (int j = 0; j + lag < n; j++) c += resid[j] * resid[j + lag];
            double r = var > 0 ? c / var * n / (n - lag) : 0;
            rep.autocorrelation[lag] = Math.abs(r) < 2.0 / Math.sqrt(n) ? 0.0 : r;   // drop lags consistent with 0
        }
        rep.errorScaleBandLimited = Math.sqrt(rep.chi2RedBandLimited);
        rep.errorScaleLocal = localErrorScale(q, I, sigma);
        double[] s = sigma.clone();
        if (calibrate && rep.chi2RedBandLimited > 0 && dof[0] > 5) {
            rep.errorScaleApplied = rep.errorScaleBandLimited;
            for (int j = 0; j < n; j++) s[j] *= rep.errorScaleApplied;
        }

        // ---- rebinning ---------------------------------------------------------------------------
        double[] qo = q, Io = I, so = s;
        if (rebin && pointsPerChannel > 0) {
            // an estimated D is biased low for polydisperse samples (weak large-size tails), so bin
            // against 1.5 D to keep >= pointsPerChannel points in the channels of the largest particles
            double dBin = rep.dmaxEstimated ? 1.5 * D : D;
            double width = Math.PI / (dBin * pointsPerChannel);
            List<double[]> out = new ArrayList<>();
            double inflSum = 0;
            int inflCount = 0;
            int j = 0;
            while (j < n) {
                double q0 = q[j];
                double sw = 0, swq = 0, swI = 0;
                int k = j;
                while (k < n && q[k] < q0 + width) {
                    double w = 1.0 / (s[k] * s[k]);
                    sw += w; swq += w * q[k]; swI += w * I[k];
                    k++;
                }
                // variance of a mean of m correlated points: (sigma^2/m) [1 + 2 sum_k (1 - k/m) rho_k]
                int m = k - j;
                double infl = 1.0;
                for (int lag = 1; lag < m && lag < rep.autocorrelation.length; lag++)
                    infl += 2.0 * (1.0 - (double) lag / m) * rep.autocorrelation[lag];
                infl = Math.max(1.0, infl);
                inflSum += infl; inflCount++;
                out.add(new double[]{swq / sw, swI / sw, Math.sqrt(infl / sw)});
                j = k;
            }
            rep.meanVarianceInflation = inflCount > 0 ? inflSum / inflCount : 1.0;
            if (out.size() < n) {
                qo = new double[out.size()]; Io = new double[out.size()]; so = new double[out.size()];
                for (int i = 0; i < out.size(); i++) { qo[i] = out.get(i)[0]; Io[i] = out.get(i)[1]; so[i] = out.get(i)[2]; }
                rep.rebinned = true;
            }
        }
        rep.pointsUsed = qo.length;
        return new DataPreparation(qo, Io, so, rep);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Weighted least squares with the Moore basis B_n(q) = 4 pi int_0^D sin(n pi r/D) sin(qr)/(qr) dr,
     * n = 1..N, N = ceil(q_max D / pi) + 1 (plus a constant column if background). Returns chi^2 / dof.
     */
    static double bandLimitedChi2(double[] q, double[] I, double[] sigma, double D, boolean background, int[] dofOut) {
        return bandLimitedChi2(q, I, sigma, D, background, dofOut, null);
    }

    /** as above; if residOut != null it receives the normalised residuals (I - fit)/sigma */
    static double bandLimitedChi2(double[] q, double[] I, double[] sigma, double D, boolean background, int[] dofOut,
                                  double[] residOut) {
        int n = q.length;
        double qmax = q[n - 1];
        int N = (int) Math.ceil(qmax * D / Math.PI) + 1;
        int p = N + (background ? 1 : 0);
        int dof = n - p;
        if (dofOut != null) dofOut[0] = dof;
        if (dof <= 0) return Double.NaN;

        // quadrature on [0, D]: composite Gauss-Legendre, enough panels for the fastest oscillation
        int panels = Math.max(4, (int) Math.ceil((N + qmax * D / Math.PI) / 2.0));
        int gn = 16;
        double[] gx = GaussLegendre.nodes(gn), gw = GaussLegendre.weights(gn);
        int m = panels * gn;
        double[] r = new double[m], wr = new double[m];
        double h = D / panels;
        for (int pnl = 0; pnl < panels; pnl++)
            for (int g = 0; g < gn; g++) {
                r[pnl * gn + g] = (pnl + gx[g]) * h;
                wr[pnl * gn + g] = gw[g] * h;
            }

        // design matrix A (n x p), scaled by 1/sigma
        double[][] A = new double[n][p];
        double[] sinNr = new double[m];
        for (int k = 1; k <= N; k++) {
            for (int t = 0; t < m; t++) sinNr[t] = Math.sin(k * Math.PI * r[t] / D) * wr[t];
            for (int j = 0; j < n; j++) {
                double acc = 0, qj = q[j];
                for (int t = 0; t < m; t++) {
                    double x = qj * r[t];
                    acc += sinNr[t] * (x < 1e-8 ? 1.0 : Math.sin(x) / x);
                }
                A[j][k - 1] = 4 * Math.PI * acc / sigma[j];
            }
        }
        if (background) for (int j = 0; j < n; j++) A[j][N] = 1.0 / sigma[j];
        double[] b = new double[n];
        for (int j = 0; j < n; j++) b[j] = I[j] / sigma[j];

        double[] x = leastSquares(A, b, p);
        double chi2 = 0;
        for (int j = 0; j < n; j++) {
            double f = 0;
            for (int k = 0; k < p; k++) f += A[j][k] * x[k];
            double rr = b[j] - f;
            if (residOut != null) residOut[j] = rr;
            chi2 += rr * rr;
        }
        return chi2 / dof;
    }

    /** normal equations with column scaling + tiny ridge, Cholesky solve */
    private static double[] leastSquares(double[][] A, double[] b, int p) {
        int n = A.length;
        double[] scale = new double[p];
        for (int k = 0; k < p; k++) {
            double s = 0;
            for (int j = 0; j < n; j++) s += A[j][k] * A[j][k];
            scale[k] = s > 0 ? 1.0 / Math.sqrt(s) : 1.0;
        }
        double[][] M = new double[p][p];
        double[] v = new double[p];
        for (int a = 0; a < p; a++) {
            for (int c = a; c < p; c++) {
                double s = 0;
                for (int j = 0; j < n; j++) s += A[j][a] * A[j][c];
                M[a][c] = M[c][a] = s * scale[a] * scale[c];
            }
            double s = 0;
            for (int j = 0; j < n; j++) s += A[j][a] * b[j];
            v[a] = s * scale[a];
        }
        for (int a = 0; a < p; a++) M[a][a] += 1e-10;
        // Cholesky
        double[][] L = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j <= i; j++) {
                double s = M[i][j];
                for (int k = 0; k < j; k++) s -= L[i][k] * L[j][k];
                if (i == j) L[i][i] = Math.sqrt(Math.max(s, 1e-300));
                else L[i][j] = s / L[j][j];
            }
        }
        double[] y = new double[p];
        for (int i = 0; i < p; i++) {
            double s = v[i];
            for (int k = 0; k < i; k++) s -= L[i][k] * y[k];
            y[i] = s / L[i][i];
        }
        double[] x = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double s = y[i];
            for (int k = i + 1; k < p; k++) s -= L[k][i] * x[k];
            x[i] = s / L[i][i];
        }
        for (int k = 0; k < p; k++) x[k] *= scale[k];
        return x;
    }

    /**
     * Model-free cross-check: predict each point from a quadratic through its two neighbours on each side,
     * compare the prediction error to sigma (including the prediction's own variance), take a robust median.
     * Needs the curve to be smooth over 5 points (true for oversampled SAXS data).
     */
    static double localErrorScale(double[] q, double[] I, double[] sigma) {
        int n = q.length;
        if (n < 20) return Double.NaN;
        double[] ratio = new double[n - 4];
        int cnt = 0;
        int[] nb = {-2, -1, 1, 2};
        for (int j = 2; j < n - 2; j++) {
            // X: [1, dx, dx^2] for the 4 neighbours
            double[][] X = new double[4][3];
            for (int k = 0; k < 4; k++) {
                double dx = q[j + nb[k]] - q[j];
                X[k][0] = 1; X[k][1] = dx; X[k][2] = dx * dx;
            }
            double[][] XtX = new double[3][3];
            for (int a = 0; a < 3; a++) for (int c = 0; c < 3; c++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += X[k][a] * X[k][c];
                XtX[a][c] = s;
            }
            double[][] inv = invert3(XtX);
            if (inv == null) continue;
            double pred = 0, vp = 0;
            for (int k = 0; k < 4; k++) {
                double hk = 0;
                for (int c = 0; c < 3; c++) hk += inv[0][c] * X[k][c];
                pred += hk * I[j + nb[k]];
                vp += hk * hk * sigma[j + nb[k]] * sigma[j + nb[k]];
            }
            double e = I[j] - pred;
            ratio[cnt++] = e * e / (sigma[j] * sigma[j] + vp);
        }
        if (cnt < 10) return Double.NaN;
        double[] r = Arrays.copyOf(ratio, cnt);
        Arrays.sort(r);
        double med = r[cnt / 2];
        return Math.sqrt(med / 0.4549364);   // median of chi^2_1
    }

    private static double[][] invert3(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2], d = m[1][0], e = m[1][1], f = m[1][2], g = m[2][0], h = m[2][1], i = m[2][2];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-300) return null;
        double[][] r = new double[3][3];
        r[0][0] = A / det; r[0][1] = -(b * i - c * h) / det; r[0][2] = (b * f - c * e) / det;
        r[1][0] = B / det; r[1][1] = (a * i - c * g) / det; r[1][2] = -(a * f - c * d) / det;
        r[2][0] = C / det; r[2][1] = -(a * h - b * g) / det; r[2][2] = (a * e - b * d) / det;
        return r;
    }
}
