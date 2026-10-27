package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

/**
 * GNOM-like indirect transform: P(r) as a histogram of heights p_j on an even grid,
 *
 *     q*I(q) = a_0 + sum_j p_j sin(q r_j) / r_j,      r_j = (j+1) dr,  j = 0..M-1,  dr = dmax/(M+1)
 *
 * so that P(0) = P(dmax) = 0 sit exactly one grid step beyond the first and last heights.
 * Regularized by the squared second difference of P(r) (L2), with the same penalty whether or
 * not the background a_0 is fitted (the background is never penalized):
 *
 *     minimize chi^2 + w * sum_j (p_{j+1} - 2 p_j + p_{j-1})^2,   p_{-1} = p_M = 0
 *
 * The problem is quadratic in (a_0, p), so it is solved exactly by Householder QR in one step
 * rather than iteratively. lambda = 0 gives the least smoothing that keeps the problem solvable
 * (see MIN_RELATIVE_WEIGHT): with more heights than Shannon channels, no smoothing at all is singular.
 *
 * Penalty weight w: by default w = lambda (what the previous code actually applied, see notes).
 * Set NORMALIZE_LAMBDA = true to make lambda dimensionless (w = lambda * trace(A'WA)/trace(L'L)),
 * so the same lambda smooths comparably at every dmax in a scan.
 */
public class DirectSineIntegralTransform extends IndirectFT {

    /** false: w = lambda (previous effective behaviour). true: lambda is dimensionless. */
    public static boolean NORMALIZE_LAMBDA = false;
    /**
     * Constrain P(r) >= 0 (background stays free). The solution is then the L2-regularized fit
     * with no negative heights; positivityCost holds how much chi^2 that costs relative to the
     * unconstrained fit (~0 when negatives were regularization artefacts, large when the data demand them).
     */
    public static boolean POSITIVE_ONLY = false;
    public static int POSITIVE_MAX_ITER = 2000;
    /** chi^2(P >= 0) - chi^2(unconstrained); 0 when POSITIVE_ONLY is off */
    public double positivityCost = 0;

    /** smallest penalty weight, relative to trace(A'WA)/trace(L'L); keeps lambda = 0 well-posed */
    public static double MIN_RELATIVE_WEIGHT = 1e-9;
    /** histogram points per Shannon bin (pi/qmax); previously fixed at 4 */
    public static int POINTS_PER_SHANNON_BIN = 4;

    private double del_r;
    double[] r_vector;
    int r_vector_size;
    double[] qvalues;      // data
    double[] target;       // standardized q*I(q)
    double[] invVariance;  // 1/sigma^2 of standardized q*I(q)
    private double[][] design;   // rows x coeffs_size, row-major

    /**
     * Regularized by 2nd derivative of Pr-distribution using L2-NORM
     */
    public DirectSineIntegralTransform(XYSeries dataset, XYSeries errors, double dmax, double qmax, double lambda, boolean includeBackground) {
        super(dataset, errors, dmax, qmax, lambda, includeBackground);
        this.createDesignMatrix(this.data);
        this.setModelUsed("DirectSineIntegralTransform L2-NORM");
        this.invertStandardVariance();
        this.solve();
    }

    public DirectSineIntegralTransform(XYSeries dataset, XYSeries scaledqIqErrors, double dmax, double qmax, double lambda,
                                       double stdmin, double stdscale, boolean useBkgrnd) {
        this(dataset, scaledqIqErrors, null, dmax, qmax, lambda, stdmin, stdscale, useBkgrnd);
    }

    /**
     * @param priors accepted for compatibility; the exact solve has a unique answer and needs no start
     */
    public DirectSineIntegralTransform(XYSeries dataset, XYSeries scaledqIqErrors, double[] priors, double dmax, double qmax,
                                       double lambda, double stdmin, double stdscale, boolean useBkgrnd) {
        super(dataset, scaledqIqErrors, dmax, qmax, lambda, useBkgrnd, stdmin, stdscale);
        if (priors != null) {
            this.prior_coefficients = priors.clone();
            priorExists = true;
        }

        standardVariance = new XYSeries("standardized error");
        int totalItems = data.getItemCount();
        for (int r = 0; r < totalItems; r++) {
            double sigma = scaledqIqErrors.getY(r).doubleValue();   // already q*I(q)/scale
            standardVariance.add(data.getX(r), sigma*sigma);
        }

        this.createDesignMatrix(dataset);
        this.setModelUsed("DirectSineIntegralTransform L2-NORM");
        this.invertStandardVariance();
        this.solve();
    }

    /** copy constructor */
    public DirectSineIntegralTransform(DirectSineIntegralTransform original) {
        super(original);
        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();
        this.r_vector_size = original.r_vector_size;
        this.qvalues = original.qvalues.clone();
        this.target = original.target.clone();
        this.invVariance = original.invVariance.clone();
        this.design = new double[original.design.length][];
        for (int i = 0; i < design.length; i++) this.design[i] = original.design[i].clone();
    }

    /* ---------------------------------------------------------------------- design matrix */

    @Override
    void createDesignMatrix(XYSeries datasetInuse) {
        // ns is the Shannon number (used by AIC, CIF output); the histogram has more points than that
        ns = (int) Math.ceil(qmax*dmax*INV_PI);
        bin_width = Math.PI/qmax;                        // Shannon bin, for prDistributionForFitting

        // even grid with dmax an exact multiple of del_r, so P(dmax) = 0 is one step past the last height
        double target_dr = Math.PI/qmax/(double) POINTS_PER_SHANNON_BIN;
        r_vector_size = Math.max(3, (int) Math.ceil(dmax/target_dr) - 1);
        del_r = dmax/(double)(r_vector_size + 1);
        r_vector = new double[r_vector_size];
        for (int i = 0; i < r_vector_size; i++) r_vector[i] = (i + 1)*del_r;

        coeffs_size = includeBackground ? r_vector_size + 1 : r_vector_size;
        final int offset = includeBackground ? 1 : 0;
        rows = datasetInuse.getItemCount();

        design = new double[rows][coeffs_size];
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        y_vector = new SimpleMatrix(rows, 1);
        qvalues = new double[rows];
        target = new double[rows];

        for (int row = 0; row < rows; row++) {
            XYDataItem item = datasetInuse.getDataItem(row);
            double q = item.getXValue();
            double[] dRow = design[row];
            if (includeBackground) dRow[0] = 1.0;
            // sin(q r_j) for evenly spaced r_j by recurrence: one sin + one cos per row
            double s1 = FastMath.sin(q*del_r), twoCos = 2*FastMath.cos(q*del_r);
            double sPrev = 0, sCur = s1;
            for (int j = 0; j < r_vector_size; j++) {
                dRow[j + offset] = sCur/r_vector[j];
                double sNext = twoCos*sCur - sPrev;   // sin((j+2) q dr)
                sPrev = sCur;
                sCur = sNext;
            }
            for (int c = 0; c < coeffs_size; c++) a_matrix.set(row, c, dRow[c]);
            y_vector.set(row, 0, item.getYValue());
            qvalues[row] = q;
            target[row] = item.getYValue();
        }
    }

    /* ---------------------------------------------------------------------- solver */

    void solve() {
        try {
            final int k = coeffs_size, offset = includeBackground ? 1 : 0, M = r_vector_size;
            double weight = penaltyWeight();
            int extra = M;                       // always > 0: see penaltyWeight()

            // stacked system [ sqrt(W) A ; sqrt(w) L ], L = second difference with zero boundaries
            double[][] S = new double[rows + extra][];
            double[] rhs = new double[rows + extra];
            for (int i = 0; i < rows; i++) {
                double s = Math.sqrt(invVariance[i]);
                double[] row = new double[k];
                for (int c = 0; c < k; c++) row[c] = s*design[i][c];
                S[i] = row;
                rhs[i] = s*target[i];
            }
            if (extra > 0) {
                double sw = Math.sqrt(weight);
                for (int j = 0; j < M; j++) {
                    double[] row = new double[k];
                    if (j > 0) row[offset + j - 1] = sw;
                    row[offset + j] = -2*sw;
                    if (j < M - 1) row[offset + j + 1] = sw;
                    S[rows + j] = row;
                }
            }
            double[] solution = householderSolve(S, rhs, k);

            positivityCost = 0;
            if (POSITIVE_ONLY) {
                double chiFree = chiSquared(solution);
                boolean anyNegative = false;
                for (int j = offset; j < k; j++) if (solution[j] < 0) { anyNegative = true; break; }
                if (anyNegative) {
                    // same quadratic (chi^2 + w * |L p|^2) with heights >= 0: Lawson-Hanson active set
                    double[][] G = new double[k][k];
                    double[] b = new double[k];
                    for (int i = 0; i < rows; i++) {
                        double[] dRow = design[i];
                        for (int p = 0; p < k; p++) {
                            double wa = invVariance[i]*dRow[p];
                            b[p] += wa*target[i];
                            for (int s2 = p; s2 < k; s2++) G[p][s2] += wa*dRow[s2];
                        }
                    }
                    // + w L'L: each penalty row j has 1, -2, 1 at heights j-1, j, j+1 (zero boundaries)
                    for (int j = 0; j < M; j++) {
                        int[] idx = {j - 1, j, j + 1};
                        double[] val = {1, -2, 1};
                        for (int x = 0; x < 3; x++) {
                            if (idx[x] < 0 || idx[x] >= M) continue;
                            for (int y = x; y < 3; y++) {
                                if (idx[y] < 0 || idx[y] >= M) continue;
                                G[offset + idx[x]][offset + idx[y]] += weight*val[x]*val[y];   // upper triangle (idx ascending)
                            }
                        }
                    }
                    for (int p = 0; p < k; p++) for (int s2 = 0; s2 < p; s2++) G[p][s2] = G[s2][p];
                    solution = activeSetNNLS(G, b, offset, POSITIVE_MAX_ITER, solution);
                    positivityCost = chiSquared(solution) - chiFree;
                }
            }

            coefficients = new double[includeBackground ? k : k + 1];
            am_vector = new SimpleMatrix(k, 1);
            if (includeBackground) {
                System.arraycopy(solution, 0, coefficients, 0, k);
            } else {
                coefficients[0] = 0;
                System.arraycopy(solution, 0, coefficients, 1, k);
            }
            for (int j = 0; j < k; j++) am_vector.set(j, 0, solution[j]);
            totalCoefficients = coefficients.length;

            // posterior covariance of the (unconstrained) regularized fit, for sigma(P(r))
            double[][] L = new double[M][k];
            for (int j = 0; j < M; j++) {
                if (j > 0) L[j][offset + j - 1] = 1;
                L[j][offset + j] = -2;
                if (j < M - 1) L[j][offset + j + 1] = 1;
            }
            setPosterior(normalMatrix(design, invVariance, L, weight),
                    reducedChiSquared(design, target, invVariance, solution), design, invVariance, L, weight, solution);

            status = "solved";
            this.setPrDistribution();
            this.calculateIzeroRg();
        } catch (RuntimeException e) {
            status = "solve failed: " + e.getMessage();
            System.out.println("Exception occurred " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Penalty weight w. Default w = lambda: the previous code intended lambda / dr^4, but
     * `1.0/diff*diff` evaluates to 1, so lambda was applied as-is; keeping that preserves tuned
     * lambdas. NORMALIZE_LAMBDA: w = lambda * trace(A'WA) / trace(L'L), dimensionless.
     */
    private double penaltyWeight() {
        double tA = 0;
        int offset = includeBackground ? 1 : 0;
        for (int i = 0; i < rows; i++)
            for (int c = offset; c < coeffs_size; c++) tA += invVariance[i]*design[i][c]*design[i][c];
        double tL = 6.0*r_vector_size - 2.0;   // trace(L'L) for the zero-boundary second difference
        double scale = tA/tL;

        double w = (lambda <= 0) ? 0 : (NORMALIZE_LAMBDA ? lambda*scale : lambda);
        // The histogram has ~POINTS_PER_SHANNON_BIN x more heights than the data can determine,
        // so the unregularized problem is singular (condition number ~1e17 on SYCP1). A floor of
        // MIN_RELATIVE_WEIGHT keeps it solvable: lambda = 0 then means "minimal smoothing".
        return Math.max(w, MIN_RELATIVE_WEIGHT*scale);
    }

    private double chiSquared(double[] a) {
        double chi = 0;
        for (int i = 0; i < rows; i++) {
            double calc = 0;
            for (int c = 0; c < coeffs_size; c++) calc += design[i][c]*a[c];
            double res = calc - target[i];
            chi += res*res*invVariance[i];
        }
        return chi;
    }

    /* ---------------------------------------------------------------------- outputs */

    @Override
    void calculateIzeroRg() {
        double tempRgSum = 0, tempRg2Sum = 0, xaverage = 0;
        for (int i = 1; i < totalInDistribution - 1; i++) {     // end points are zero
            XYDataItem item = prDistribution.getDataItem(i);
            double rvalue = item.getXValue(), p = item.getYValue();
            tempRg2Sum += rvalue*rvalue*p*del_r;
            tempRgSum += p*del_r;
            xaverage += rvalue*p*del_r;
        }
        rg = Math.sqrt(0.5*tempRg2Sum/tempRgSum);

        double sum = coefficients[0];
        for (int j = 0; j < r_vector_size; j++) sum += coefficients[j + 1];
        izero = sum*standardizedScale + standardizedMin;

        rAverage = xaverage/tempRgSum;
        area = tempRgSum;
    }

    @Override
    void setPrDistribution() {
        prDistribution = new XYSeries("PRDistribution");
        prDistribution.add(0, 0);
        for (int j = 0; j < r_vector_size; j++) prDistribution.add(r_vector[j], coefficients[j + 1]);
        prDistribution.add(dmax, 0);
        totalInDistribution = prDistribution.getItemCount();

        prDistributionBasis = new double[totalInDistribution][];
        int offset = includeBackground ? 1 : 0;
        for (int j = 0; j < r_vector_size; j++) {
            double[] b = new double[coeffs_size];
            b[offset + j] = 1.0;                                      // prDistribution holds the heights
            prDistributionBasis[j + 1] = b;
        }
        deriveDistributionSigma();

        // P(r) at the mid-point of each Shannon bin (pi/qmax), from the spline through the heights
        setSplineFunction();
        setShannonBinnedDistribution();

        scoreDistribution();
        setHeaderDetails();
    }

    private void setHeaderDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS DIRECT INVERSE FOURIER TRANSFORM OF I(q) %n"));
        sb.append(String.format("REMARK 265  COEFFICIENTS ARE THE HISTOGRAM HEIGHTS WITH EQUAL BIN WIDTHS %n"));
        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265            BIN WIDTH (delta r) : %.4f %n", del_r));   // was dmax/ns
        sb.append(String.format("REMARK 265          SHANNON BIN (pi/qmax) : %.4f %n", bin_width));
        sb.append(String.format("REMARK 265             DISTRIBUTION SCORE : %.4f %n", prScore));
        sb.append(String.format("REMARK 265 %n"));
        if (!includeBackground) {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n"));
        } else {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]));
        }
        this.description = sb.toString();
    }

    /** spline through the histogram; used by calculatePofRAtR */
    private void setSplineFunction() {
        double[] totalrvalues = new double[totalInDistribution];
        double[] totalPrvalues = new double[totalInDistribution];
        for (int i = 0; i < totalInDistribution; i++) {
            XYDataItem item = prDistribution.getDataItem(i);
            totalPrvalues[i] = item.getYValue();
            totalrvalues[i] = item.getXValue();
        }
        splineFunction = new SplineInterpolator().interpolate(totalrvalues, totalPrvalues);
    }

    /** exact model for the invariants: I(q) = scale * sum_j p_j sin(q r_j)/(q r_j) */
    @Override
    protected SincModel sincModel() {
        double[] h = new double[r_vector_size];
        for (int j = 0; j < r_vector_size; j++) h[j] = coefficients[j + 1];
        return histogramSincModel(r_vector, h);
    }

    @Override
    public double calculateQIQ(double qvalue) {
        double sum = coefficients[0];
        double s1 = FastMath.sin(qvalue*del_r), twoCos = 2*FastMath.cos(qvalue*del_r);
        double sPrev = 0, sCur = s1;
        for (int j = 0; j < r_vector_size; j++) {
            sum += coefficients[j + 1]*sCur/r_vector[j];
            double sNext = twoCos*sCur - sPrev;
            sPrev = sCur;
            sCur = sNext;
        }
        return sum*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return this.calculateQIQ(qvalue)/qvalue;
    }

    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        // heights p_j relate to P(r) by q*I(q) = 4 pi dr sum_j P(r_j) sin(q r_j)/r_j (midpoint rule),
        // so P(r) = standardizedScale * p / (4 pi dr): same units as the Moore P(r)
        return standardizedScale/(4*Math.PI*del_r)*splineFunction.value(r_value)*scale;
    }

    @Override
    public void estimateErrors(XYSeries fittedData) { }

    @Override
    public void normalizeDistribution() {
        double sum = 0;
        for (int i = 0; i < totalInDistribution; i++) sum += prDistribution.getY(i).doubleValue();
        sum *= del_r;
        if (sum == 0) return;
        double invSum = 1.0/sum;
        for (int i = 0; i < totalInDistribution; i++) {
            prDistribution.updateByIndex(i, prDistribution.getY(i).doubleValue()*invSum);
        }
    }

    private void invertStandardVariance() {
        int totalItems = standardVariance.getItemCount();
        invVariance = new double[totalItems];
        for (int r = 0; r < totalItems; r++) {
            invVariance[r] = 1.0/standardVariance.getY(r).doubleValue();
        }
    }
}
