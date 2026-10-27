package version4.InverseTransform;

import net.jafama.FastMath;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

/**
 * Indirect Fourier transform using the Moore (Fourier sine series) basis:
 *
 *     P(r) = r * sum_n a_n sin(n pi r / dmax),   n = 1..ns,   ns = ceil(qmax dmax / pi)
 *
 * fitted to standardized q*I(q), with an optional constant background a_0 and one of three
 * regularizations (the background is never penalized):
 *
 *     NONE : minimize chi^2                                  (exact, Householder QR)
 *     L2   : minimize chi^2 + w * sum_j P''(r_j)^2           (exact, Householder QR)
 *     L1   : minimize chi^2 + w * sum_n |a_n|                (coordinate descent, warm-started)
 *
 * chi^2 = sum_i (y_i - (A a)_i)^2 / sigma_i^2. NONE and L2 are quadratic in a, so they are solved
 * directly rather than iteratively: the answer is exact and takes ~1 ms instead of thousands of
 * objective/gradient evaluations.
 *
 * Penalty weight w: by default w = 10000 * lambda (as before). Set NORMALIZE_LAMBDA = true to make
 * lambda dimensionless instead, so the same lambda means the same relative smoothing at every
 * dmax in a scan (see l2Weight / l1Weight).
 */
public class MooreTransformApache extends IndirectFT implements Cloneable {

    public enum Regularization { NONE, L1, L2 }

    /** false: w = 10000*lambda (original behaviour). true: lambda is a dimensionless fraction. */
    public static boolean NORMALIZE_LAMBDA = false;
    /** L1 coordinate descent: maximum sweeps and relative convergence tolerance. */
    public static int L1_MAX_SWEEPS = 20000;
    public static double L1_TOLERANCE = 1e-10;

    private final Regularization regularization;
    private int cols;
    private double del_r;
    double[] r_vector;                 // regularization points, strictly inside (0, dmax)
    double[] regularization_r_vector;  // kept for compatibility; unused
    double[] target;                   // standardized qI(q)
    double[] invVariance;              // 1/sigma^2 of standardized qI(q)
    double[] qvalues;

    public double invDmax;
    int r_vector_size;

    private double[][] design;          // rows x cols, row-major
    private double[][] secondDerivative; // r_vector_size x cols: P''(r_j) per unit coefficient

    /* ---------------------------------------------------------------------- constructors */

    public MooreTransformApache(XYSeries dataset, XYSeries untransformedErrors, double dmax, double qmax,
                                double lambda, boolean includeBackground) {
        this(dataset, untransformedErrors, dmax, qmax, lambda, includeBackground, Regularization.L2);
    }

    public MooreTransformApache(XYSeries dataset, XYSeries untransformedErrors, double dmax, double qmax,
                                double lambda, boolean includeBackground, Regularization regularization) {
        super(dataset, untransformedErrors, dmax, qmax, lambda, includeBackground);
        this.regularization = regularization;
        this.invDmax = 1.0/dmax;
        int total = standardVariance.getItemCount();
        invVariance = new double[total];
        for (int r = 0; r < total; r++) {
            invVariance[r] = 1.0/standardVariance.getY(r).doubleValue();
        }
        this.createDesignMatrix(this.data);
        this.solve();
    }

    public MooreTransformApache(XYSeries scaledqIqData, XYSeries scaledqIqErrors, double dmax, double qmax,
                                double lambda, double stdmin, double stdscale, boolean useBackground) {
        this(scaledqIqData, scaledqIqErrors, null, dmax, qmax, lambda, stdmin, stdscale, useBackground, Regularization.L2);
    }

    public MooreTransformApache(XYSeries scaledqIqData, XYSeries scaledqIqErrors, double dmax, double qmax,
                                double lambda, double stdmin, double stdscale, boolean useBackground,
                                Regularization regularization) {
        this(scaledqIqData, scaledqIqErrors, null, dmax, qmax, lambda, stdmin, stdscale, useBackground, regularization);
    }

    public MooreTransformApache(XYSeries scaledqIqData, XYSeries scaledqIqErrors, double[] priors, double dmax,
                                double qmax, double lambda, double stdmin, double stdscale, boolean useBackground) {
        this(scaledqIqData, scaledqIqErrors, priors, dmax, qmax, lambda, stdmin, stdscale, useBackground, Regularization.L2);
    }

    /**
     * @param priors optional starting coefficients (same layout as {@link #getCoefficients()} with
     *               background); used to warm-start L1. NONE and L2 have a unique exact solution.
     */
    public MooreTransformApache(XYSeries scaledqIqData, XYSeries scaledqIqErrors, double[] priors, double dmax,
                                double qmax, double lambda, double stdmin, double stdscale, boolean useBackground,
                                Regularization regularization) {
        super(scaledqIqData, scaledqIqErrors, dmax, qmax, lambda, useBackground, stdmin, stdscale);
        this.regularization = regularization;
        this.invDmax = 1.0/dmax;
        if (priors != null) {
            this.prior_coefficients = priors.clone();
            priorExists = true;
        }

        // data and errors are already standardized q*I(q); errors are sigma
        standardVariance = new XYSeries("standardized error");
        int totalItems = scaledqIqData.getItemCount();
        invVariance = new double[totalItems];
        for (int r = 0; r < totalItems; r++) {
            double sigma = scaledqIqErrors.getY(r).doubleValue();
            standardVariance.add(scaledqIqData.getX(r), sigma*sigma);
            invVariance[r] = 1.0/(sigma*sigma);
        }

        this.createDesignMatrix(scaledqIqData);
        this.solve();
    }

    /** copy constructor */
    public MooreTransformApache(MooreTransformApache original) {
        super(original);
        this.regularization = original.regularization;
        this.cols = original.cols;
        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();
        if (original.regularization_r_vector != null) {
            this.regularization_r_vector = original.regularization_r_vector.clone();
        }
        this.target = original.target.clone();
        this.invVariance = original.invVariance;   // never modified after construction
        this.qvalues = original.qvalues;
        this.invDmax = original.invDmax;
        this.r_vector_size = original.r_vector_size;
        this.design = deepCopy(original.design);
        this.secondDerivative = deepCopy(original.secondDerivative);
    }

    public Object clone() {
        return new MooreTransformApache(this);
    }

    public Regularization getRegularization() { return regularization; }

    private static double[][] deepCopy(double[][] m) {
        if (m == null) return null;
        double[][] c = new double[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    /* ---------------------------------------------------------------------- design matrix */

    /** (n pi)^2 without the 250-term table limit */
    private static double nPiSquared(int n) {
        double npi = n*Math.PI;
        return npi*npi;
    }

    /**
     * Column n of the design matrix at qd = q*dmax: the q*I(q) contribution of sin(n pi r/dmax).
     * At qd = n*pi the expression is 0/0; its limit is dmax/pi (= dmax_PI_TWO_INV_PI / 2pi).
     */
    private double mooreBasis(int n, double qd, double sinqd) {
        double npi = n*Math.PI;
        if (Math.abs(qd - npi) < 1e-7*npi) {
            return dmax_PI_TWO_INV_PI/(2*Math.PI);
        }
        double sign = ((n & 1) == 1) ? 1.0 : -1.0;   // (-1)^(n+1)
        return dmax_PI_TWO_INV_PI*n*sign*sinqd/(npi*npi - qd*qd);
    }

    @Override
    void createDesignMatrix(XYSeries datasetInuse) {
        ns = (int) Math.ceil(qmax*dmax*INV_PI);
        coeffs_size = includeBackground ? ns + 1 : ns;
        bin_width = Math.PI/qmax;                         // Shannon bin
        rows = datasetInuse.getItemCount();
        cols = coeffs_size;
        final int offset = includeBackground ? 1 : 0;   // column of the first sine term

        /*
         * Regularization points: 3 per Shannon bin, strictly inside (0, dmax).
         * (Previously spaced pi/qmax, which placed them out to ~3*dmax, i.e. mostly outside the
         * particle, where the sine series is not P(r).)
         */
        r_vector_size = ns*3 - 1;
        del_r = dmax/(double)(ns*3);
        r_vector = new double[r_vector_size];
        for (int i = 0; i < r_vector_size; i++) {
            r_vector[i] = (i + 1)*del_r;
        }

        design = new double[rows][cols];
        a_matrix = new SimpleMatrix(rows, cols);
        y_vector = new SimpleMatrix(rows, 1);
        qvalues = new double[rows];
        target = new double[rows];

        for (int row = 0; row < rows; row++) {
            XYDataItem item = datasetInuse.getDataItem(row);
            double q = item.getXValue();
            double qd = q*dmax;
            double sinqd = FastMath.sin(qd);        // same for every column: computed once per row
            double[] dRow = design[row];
            if (includeBackground) dRow[0] = 1.0;
            for (int n = 1; n <= ns; n++) {
                dRow[n - 1 + offset] = mooreBasis(n, qd, sinqd);
            }
            for (int c = 0; c < cols; c++) a_matrix.set(row, c, dRow[c]);
            y_vector.set(row, 0, item.getYValue());
            qvalues[row] = q;
            target[row] = item.getYValue();
        }

        // P''(r) for each unit coefficient: d^2/dr^2 [ r sin(k r) ] = 2k cos(kr) - r k^2 sin(kr)
        secondDerivative = new double[r_vector_size][cols];
        for (int j = 0; j < r_vector_size; j++) {
            double r = r_vector[j];
            for (int n = 1; n <= ns; n++) {
                double k = n*PI_INV_DMAX;
                double kr = k*r;
                secondDerivative[j][n - 1 + offset] = 2*k*FastMath.cos(kr) - r*k*k*FastMath.sin(kr);
            }
        }
    }

    /* ---------------------------------------------------------------------- solvers */

    public void solve() {
        try {
            double[] solution;
            double covWeight = 0;
            double[][] covPenalty = null;
            switch (regularization) {
                case NONE: solution = leastSquares(0, null); break;
                case L1:   solution = solveL1(); break;     // covariance: data-only (L1 posterior is not Gaussian)
                case L2:
                default:
                    covWeight = l2Weight();
                    covPenalty = secondDerivative;
                    solution = leastSquares(covWeight, covPenalty);
                    break;
            }
            setPosterior(normalMatrix(design, invVariance, covPenalty, covWeight),
                    reducedChiSquared(design, target, invVariance, solution), design, invVariance, covPenalty, covWeight, solution);

            coefficients = new double[includeBackground ? cols : cols + 1];
            am_vector = new SimpleMatrix(cols, 1);
            if (includeBackground) {
                System.arraycopy(solution, 0, coefficients, 0, cols);
            } else {
                coefficients[0] = 0;                       // no background
                System.arraycopy(solution, 0, coefficients, 1, cols);
            }
            for (int j = 0; j < cols; j++) am_vector.set(j, 0, solution[j]);
            totalCoefficients = coefficients.length;

            status = "solved: " + regularization;
            this.setPrDistribution();
            this.calculateIzeroRg();
            this.setModelUsed(regularization == Regularization.NONE ? "Moore NO REGULARIZATION"
                    : "Moore " + regularization + "-NORM");
        } catch (RuntimeException e) {
            status = "solve failed: " + e.getMessage();
            System.out.println("Exception occurred " + e.getMessage());
        }
    }

    /** L2 penalty weight. NORMALIZE_LAMBDA: lambda * trace(A'WA) / trace(D'D), dimensionless. */
    private double l2Weight() {
        if (lambda <= 0) return 0;
        if (!NORMALIZE_LAMBDA) return 10000*lambda;
        double tA = 0, tD = 0;
        for (int i = 0; i < rows; i++)
            for (int c = 0; c < cols; c++) tA += invVariance[i]*design[i][c]*design[i][c];
        for (double[] dRow : secondDerivative)
            for (double v : dRow) tD += v*v;
        return (tD > 0) ? lambda*tA/tD : 0;
    }

    /**
     * Weighted least squares, optionally with the stacked L2 penalty rows sqrt(w)*D, solved by
     * Householder QR on [ sqrt(W) A ; sqrt(w) D ] a = [ sqrt(W) y ; 0 ].
     * QR avoids squaring the condition number as the normal equations would.
     */
    private double[] leastSquares(double weight, double[][] penalty) {
        int extra = (weight > 0 && penalty != null) ? penalty.length : 0;
        int m = rows + extra;
        double[][] M = new double[m][];
        double[] rhs = new double[m];
        for (int i = 0; i < rows; i++) {
            double s = Math.sqrt(invVariance[i]);
            double[] row = new double[cols];
            for (int c = 0; c < cols; c++) row[c] = s*design[i][c];
            M[i] = row;
            rhs[i] = s*target[i];
        }
        if (extra > 0) {
            double sw = Math.sqrt(weight);
            for (int j = 0; j < extra; j++) {
                double[] row = new double[cols];
                for (int c = 0; c < cols; c++) row[c] = sw*penalty[j][c];
                M[rows + j] = row;
            }
        }
        return householderSolve(M, rhs, cols);
    }

    /**
     * L1 (lasso) on the sine coefficients, background unpenalized:
     *     minimize a'Ga - 2b'a + w * sum_{n>=1} |a_n|,  G = A'WA, b = A'Wy   (= chi^2 + const)
     * by cyclic coordinate descent, warm-started from priors or the unregularized solution.
     */
    private double[] solveL1() {
        final int k = cols, offset = includeBackground ? 1 : 0;
        double[][] G = new double[k][k];
        double[] b = new double[k];
        for (int i = 0; i < rows; i++) {
            double w = invVariance[i];
            double[] dRow = design[i];
            for (int p = 0; p < k; p++) {
                double wa = w*dRow[p];
                b[p] += wa*target[i];
                for (int s = p; s < k; s++) G[p][s] += wa*dRow[s];
            }
        }
        for (int p = 0; p < k; p++) for (int s = 0; s < p; s++) G[p][s] = G[s][p];

        final double weight = l1Weight(G, b, offset);

        double[] a = null;
        if (priorExists && prior_coefficients != null) {
            if (includeBackground && prior_coefficients.length == k) a = prior_coefficients.clone();
            else if (!includeBackground && prior_coefficients.length == k + 1) {
                a = new double[k];
                System.arraycopy(prior_coefficients, 1, a, 0, k);
            }
        }
        if (a == null) a = leastSquares(0, null);

        return coordinateDescentL1(G, b, weight, offset, false, a, L1_MAX_SWEEPS, L1_TOLERANCE);
    }

    /**
     * L1 penalty weight. NORMALIZE_LAMBDA: lambda is a fraction of lambda_max, the smallest weight
     * that sets every sine coefficient to zero (lambda = 1 -> background only, 0 -> no penalty).
     */
    private double l1Weight(double[][] G, double[] b, int offset) {
        if (lambda <= 0) return 0;
        if (!NORMALIZE_LAMBDA) return 10000*lambda;
        double a0 = (offset == 1 && G[0][0] > 0) ? b[0]/G[0][0] : 0;   // background-only fit
        double lmax = 0;
        for (int p = offset; p < cols; p++) {
            double grad = 2*(b[p] - (offset == 1 ? G[p][0]*a0 : 0));
            lmax = Math.max(lmax, Math.abs(grad));
        }
        return lambda*lmax;
    }

    /* ---------------------------------------------------------------------- outputs */

    @Override
    void calculateIzeroRg() {
        double i_zero = 0, partial_rg = 0, rsum = 0;
        for (int i = 1; i < coefficients.length; i++) {
            double am = coefficients[i];
            double sign = ((i & 1) == 1) ? 1.0 : -1.0;     // (-1)^(i+1)
            double am_inv_pow_3 = am/((double) i*i*i);
            double npi2 = nPiSquared(i);
            i_zero += am/i*sign;
            partial_rg += am_inv_pow_3*(npi2 - 6)*sign;
            rsum += am_inv_pow_3*((npi2 - 2)*sign - 2);
        }

        double dmax2 = dmax*dmax;
        double dmax3 = dmax2*dmax;
        double dmax4 = dmax2*dmax2;
        double inv_pi_fourth = 1.0/(nPiSquared(1)*nPiSquared(1));

        double izero_temp = TWO_INV_PI*i_zero*dmax2/Math.PI + coefficients[0];
        izero = standardizedScale*izero_temp + standardizedMin;
        rg = Math.sqrt(2*dmax4*inv_pi_fourth/izero_temp*partial_rg)*0.7071067811865475; // 1/sqrt(2)
        rAverage = 2*dmax3*inv_pi_fourth/izero_temp*rsum;
    }

    /** sum_n a_n sin(n x), with sin(n x) from the Chebyshev recurrence: 1 sin + 1 cos per point */
    private double sineSeries(double x) {
        double s1 = FastMath.sin(x), twoCos = 2*FastMath.cos(x);
        double sPrev = 0, sCur = s1, sum = 0;
        for (int n = 1; n < totalCoefficients; n++) {
            sum += coefficients[n]*sCur;
            double sNext = twoCos*sCur - sPrev;   // sin((n+1)x) = 2cos(x) sin(nx) - sin((n-1)x)
            sPrev = sCur;
            sCur = sNext;
        }
        return sum;
    }

    @Override
    void setPrDistribution() {
        prDistribution = new XYSeries("PRDistribution");

        r_vector_size = ns*3 - 1;
        del_r = dmax/(double)(ns*3);
        totalInDistribution = r_vector_size;

        // one standardizedScale factor, consistent with calculatePofRAtR(r, 1) and the
        // Shannon-bin series below (previously this series carried standardizedScale twice)
        final double prefactor = standardizedScale/(2.0*nPiSquared(1));
        negativeValuesInModel = false;
        prDistribution.add(0.0d, 0.0d);
        for (int j = 0; j < totalInDistribution; j++) {
            double r_value = (j + 1)*del_r;
            double resultM = sineSeries(PI_INV_DMAX*r_value);
            prDistribution.add(r_value, prefactor*r_value*resultM);
            if (resultM < 0) negativeValuesInModel = true;
        }
        prDistribution.add(dmax, 0);

        // sd of each P(r) value: P(r) = prefactor * r * sum_n a_n sin(n pi r/dmax), linear in a
        {
            int n = prDistribution.getItemCount(), offset = includeBackground ? 1 : 0;
            prDistributionBasis = new double[n][];
            for (int j = 0; j < totalInDistribution; j++) {
                double r_value = (j + 1)*del_r, x = PI_INV_DMAX*r_value;
                double[] b = new double[cols];
                for (int m = 1; m <= ns; m++) b[m - 1 + offset] = prefactor*r_value*FastMath.sin(m*x);
                prDistributionBasis[j + 1] = b;
            }
            deriveDistributionSigma();
        }

        // P(r) at the mid-point of each Shannon bin (pi/qmax)
        setShannonBinnedDistribution();

        scoreDistribution();
        prScore *= 10;
        setHeaderDetails();
    }

    private void setHeaderDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS INDIRECT INVERSE FOURIER TRANSFORM OF I(q) %n"));
        sb.append(String.format("REMARK 265  P(r) APPROXIMATED USING FOURIER SINE SERIES %n"));   // was overwriting line 1
        sb.append(String.format("REMARK 265  COEFFICIENTS ARE MOORE WEIGHTS FOR EACH SINE FUNCTION %n"));
        sb.append(String.format("REMARK 265        REGULARIZATION : %s (lambda = %.3E) %n", regularization, lambda));
        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", dmax/(double) ns));
        sb.append(String.format("REMARK 265            DISTRIBUTION SCORE : %.4f %n", prScore));
        sb.append(String.format("REMARK 265 %n"));
        if (!includeBackground) {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n"));
        } else {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]));
            sb.append(String.format("REMARK 265          SCALED  s*m(0) + min : %.4E %n", coefficients[0]*standardizedScale + standardizedMin));
        }
        sb.append(String.format("REMARK 265  MOORE COEFFICIENTS (UNSCALED)%n"));
        for (int i = 1; i < totalCoefficients; i++) {
            sb.append(String.format("REMARK 265                        m_(%2d) : %.3E %n", i, coefficients[i]));
        }
        this.description = sb.toString();
    }

    /**
     * Invariants: P(r) = s r sum_n a_n sin(n pi r/D), s = standardizedScale/(2 pi^2), written as
     * weighted sincs by midpoint quadrature, with the exact gradient d w_i / d a_n.
     */
    @Override
    protected SincModel sincModel() {
        int m = INVARIANT_QUADRATURE_POINTS, offset = includeBackground ? 1 : 0;
        double h = dmax/m, s = standardizedScale/(2*nPiSquared(1));
        double[] r = new double[m], w = new double[m];
        double[][] dw = new double[m][cols];
        for (int i = 0; i < m; i++) {
            r[i] = (i + 0.5)*h;
            double x = PI_INV_DMAX*r[i], f = 4*Math.PI*h*s*r[i], sum = 0;
            for (int n = 1; n <= ns; n++) {
                double b = f*FastMath.sin(n*x);
                dw[i][n - 1 + offset] = b;
                sum += coefficients[n]*b;
            }
            w[i] = sum;
        }
        return new SincModel(r, w, dw);
    }

    /**
     * Analytic Porod invariant to infinite q: Q = 2 pi^2 lim P(r)/r^2 = standardizedScale (pi/D) sum n a_n
     * (Grant 2022 eq. 12 in terms of these coefficients).
     */
    @Override
    protected Estimate porodInvariantInfinite() {
        double[] g = porodInvariantInfiniteGradient();
        double v = 0;
        int offset = includeBackground ? 1 : 0;
        for (int n = 1; n <= ns; n++) v += g[n - 1 + offset]*coefficients[n];
        double var = 0;
        if (coefficientCovariance != null)
            for (int i = 0; i < cols; i++) for (int j = 0; j < cols; j++) var += g[i]*coefficientCovariance[i][j]*g[j];
        return new Estimate(v, coefficientCovariance == null ? Double.NaN : Math.sqrt(Math.max(var, 0)));
    }

    @Override
    protected double[] porodInvariantInfiniteGradient() {
        int offset = includeBackground ? 1 : 0;
        double[] g = new double[cols];
        for (int n = 1; n <= ns; n++) g[n - 1 + offset] = standardizedScale*(Math.PI/dmax)*n;
        return g;
    }

    @Override
    public double calculateQIQ(double qvalue) {
        double qd = dmax*qvalue;
        double sinqd = FastMath.sin(qd);            // hoisted out of the loop
        double resultM = coefficients[0];           // 0 when there is no background
        for (int i = 1; i < totalCoefficients; i++) {
            resultM += coefficients[i]*mooreBasis(i, qd, sinqd);
        }
        return resultM*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return this.calculateQIQ(qvalue)/qvalue;
    }

    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        return (inv2PI2*standardizedScale)*r_value*sineSeries(PI_INV_DMAX*r_value)*scale;
    }

    @Override
    public void estimateErrors(XYSeries fittedData) { }

    @Override
    public void normalizeDistribution() {
        int total = prDistribution.getItemCount();
        double sum = 0;
        for (int i = 0; i < total; i++) sum += prDistribution.getY(i).doubleValue();
        sum *= del_r;                                   // area (end points are zero)
        if (sum == 0) return;
        double invSum = 1.0/sum;
        for (int i = 0; i < total; i++) {
            prDistribution.updateByIndex(i, prDistribution.getY(i).doubleValue()*invSum);
        }
    }
}
