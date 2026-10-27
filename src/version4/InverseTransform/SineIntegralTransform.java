package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Functions;

import java.util.ArrayList;
import java.util.Random;

/**
 * Indirect transform with P(r) as a histogram at the Shannon bin centres,
 *
 *     q*I(q) = a_0 + sum_j p_j sin(q r_j) / r_j,     r_j = (j + 0.5) dmax/ns,  ns = ceil(qmax dmax / pi)
 *
 * with L1 regularization on the heights, optionally constrained positive:
 *
 *     minimize chi^2 + w * sum_j |p_j|          (positiveOnly = false)
 *     minimize chi^2 + w * sum_j p_j,  p_j >= 0 (positiveOnly = true)
 *
 * The background a_0 (which in standardized q*I(q) also absorbs the -min/scale offset) is never
 * penalized or sign-constrained. Both problems are convex and small (ns + 1 unknowns), and are
 * solved exactly by coordinate descent (IndirectFT.coordinateDescentL1).
 *
 * chi^2 is weighted by the errors (1/sigma^2) when WEIGHT_BY_ERRORS is true, as in the other IFT
 * classes and the AIC; the previous interior-point code fitted unweighted.
 */
public class SineIntegralTransform extends IndirectFT {

    /** weight chi^2 by 1/sigma^2 (consistent with the other IFTs and the AIC). false = previous behaviour */
    public static boolean WEIGHT_BY_ERRORS = true;
    /** false: w = lambda. true: lambda is a fraction of lambda_max (1 -> all heights zero) */
    public static boolean NORMALIZE_LAMBDA = false;
    public static int L1_MAX_SWEEPS = 20000;
    public static double L1_TOLERANCE = 1e-10;

    private double del_r;
    double[] r_vector;
    int r_vector_size;
    boolean positiveOnly;
    private double[] invVariance;   // 1/sigma^2 of standardized q*I(q), aligned with the fitted data

    // Dataset should be standardized and in form of [q, q*I(q)]
    public SineIntegralTransform(XYSeries dataset, XYSeries errors, double dmax, double qmax, double lambda,
                                 boolean includeBackground, boolean positiveOnly) {
        super(dataset, errors, dmax, qmax, lambda, includeBackground);
        this.positiveOnly = positiveOnly;
        this.createDesignMatrix(this.data);
        this.solve();
    }

    public SineIntegralTransform(XYSeries scaledqIqdataset, XYSeries scaledqIqErrors, double dmax, double qmax,
                                 double lambda, boolean includeBackground, boolean positiveOnly,
                                 double stdmin, double stdscale) {
        this(scaledqIqdataset, scaledqIqErrors, null, dmax, qmax, lambda, includeBackground, positiveOnly, stdmin, stdscale);
    }

    /**
     * @param priors starting coefficients (with background first when includeBackground), used as a
     *               warm start. Previously these were stored but priorExists was set to false.
     */
    public SineIntegralTransform(XYSeries scaledqIqdataset, XYSeries scaledqIqErrors, double[] priors, double dmax,
                                 double qmax, double lambda, boolean includeBackground, boolean positiveOnly,
                                 double stdmin, double stdscale) {
        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, includeBackground, stdmin, stdscale);
        setStandardVariance(scaledqIqdataset, scaledqIqErrors);
        if (priors != null) {
            this.prior_coefficients = priors.clone();
            priorExists = true;
        }
        this.positiveOnly = positiveOnly;
        this.createDesignMatrix(scaledqIqdataset);
        this.solve();
    }

    /**
     * Datasets and errors are already standardized. Always positive-only (as before: this
     * signature has no positiveOnly argument).
     */
    public SineIntegralTransform(XYSeries dataset, XYSeries errors, double dmax, double qmax, double lambda,
                                 double stdmin, double stdscale, boolean useBackground) {
        super(dataset, errors, dmax, qmax, lambda, useBackground, stdmin, stdscale);
        setStandardVariance(dataset, errors);
        this.positiveOnly = true;
        this.createDesignMatrix(dataset);
        this.solve();
    }

    /** Copy constructor. */
    public SineIntegralTransform(SineIntegralTransform toCopy) {
        super(toCopy);
        this.del_r = toCopy.del_r;
        this.r_vector = toCopy.r_vector.clone();
        this.positiveOnly = toCopy.positiveOnly;
        this.r_vector_size = toCopy.r_vector_size;
        this.invVariance = (toCopy.invVariance == null) ? null : toCopy.invVariance.clone();
    }

    private void setStandardVariance(XYSeries dataset, XYSeries errs) {
        standardVariance = new XYSeries("standardized error");
        for (int r = 0; r < dataset.getItemCount(); r++) {
            double sigma = errs.getY(r).doubleValue();       // already q*I(q)/scale
            standardVariance.add(dataset.getX(r), sigma*sigma);
        }
    }

    private String modelName() {
        String name = positiveOnly ? "DIRECT L1-NORM POSITIVE ONLY" : "DIRECT L1-NORM";
        return includeBackground ? name + " + BKGRND" : name;
    }

    /* ---------------------------------------------------------------------- design matrix */

    private void setGrid() {
        ns = (int) Math.ceil(qmax*dmax*INV_PI);
        r_vector_size = ns;
        del_r = dmax/(double) ns;
        bin_width = Math.PI/qmax;                         // Shannon bin (heights sit at dmax/ns spacing)
        r_vector = new double[r_vector_size];
        for (int i = 0; i < r_vector_size; i++) r_vector[i] = (0.5 + i)*del_r;
        coeffs_size = includeBackground ? ns + 1 : ns;
    }

    /** one design-matrix row at q: [1,] sin(q r_j)/r_j, sines by recurrence (r_j evenly spaced) */
    private void designRow(double q, double[] out) {
        int offset = includeBackground ? 1 : 0;
        if (includeBackground) out[0] = 1.0;
        double twoCos = 2*FastMath.cos(q*del_r);
        double sCur = FastMath.sin(0.5*q*del_r), sPrev = -sCur;   // sin(q r_0), sin(q r_{-1})
        for (int j = 0; j < r_vector_size; j++) {
            out[j + offset] = sCur/r_vector[j];
            double sNext = twoCos*sCur - sPrev;
            sPrev = sCur;
            sCur = sNext;
        }
    }

    public void createDesignMatrix(XYSeries datasetInuse) {
        setGrid();
        rows = datasetInuse.getItemCount();
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        y_vector = new SimpleMatrix(rows, 1);
        fittedQ = new double[rows];
        double[] row = new double[coeffs_size];
        for (int i = 0; i < rows; i++) {
            XYDataItem item = datasetInuse.getDataItem(i);
            fittedQ[i] = item.getXValue();
            designRow(item.getXValue(), row);
            for (int c = 0; c < coeffs_size; c++) a_matrix.set(i, c, row[c]);
            y_vector.set(i, 0, item.getYValue());
        }
        // weights aligned with this dataset
        invVariance = new double[rows];
        for (int i = 0; i < rows; i++) {
            double v = (standardVariance != null && i < standardVariance.getItemCount())
                    ? standardVariance.getY(i).doubleValue() : 1.0;
            invVariance[i] = (WEIGHT_BY_ERRORS && v > 0) ? 1.0/v : 1.0;
        }
    }

    /* ---------------------------------------------------------------------- solver */

    /**
     * Solve for (background, heights) on arbitrary data, without touching object state.
     * Returns coefficients in the a_matrix column layout (background first when included).
     */
    private double[] fit(double[] qs, double[] ys, double[] w, double[] start) {
        final int k = coeffs_size, offset = includeBackground ? 1 : 0;
        double[][] G = new double[k][k];
        double[] b = new double[k];
        double[] row = new double[k];
        for (int i = 0; i < qs.length; i++) {
            designRow(qs[i], row);
            for (int p = 0; p < k; p++) {
                double wa = w[i]*row[p];
                b[p] += wa*ys[i];
                for (int s = p; s < k; s++) G[p][s] += wa*row[s];
            }
        }
        for (int p = 0; p < k; p++) for (int s = 0; s < p; s++) G[p][s] = G[s][p];

        double weight = penaltyWeight(G, b, offset);
        double[] a = (start != null && start.length == k) ? start.clone() : new double[k];
        return coordinateDescentL1(G, b, weight, offset, positiveOnly, a, L1_MAX_SWEEPS, L1_TOLERANCE);
    }

    /** w = lambda, or (NORMALIZE_LAMBDA) lambda times the smallest w that zeroes every height */
    private double penaltyWeight(double[][] G, double[] b, int offset) {
        if (lambda <= 0) return 0;
        if (!NORMALIZE_LAMBDA) return lambda;
        double a0 = (offset == 1 && G[0][0] > 0) ? b[0]/G[0][0] : 0;   // background-only fit
        double lmax = 0;
        for (int p = offset; p < coeffs_size; p++) {
            double grad = 2*(b[p] - (offset == 1 ? G[p][0]*a0 : 0));
            lmax = Math.max(lmax, positiveOnly ? grad : Math.abs(grad));
        }
        return lambda*lmax;
    }

    private void solve() {
        double[] qs = new double[rows], ys = new double[rows];
        for (int i = 0; i < rows; i++) {
            qs[i] = a_matrixQ(i);
            ys[i] = y_vector.get(i, 0);
        }
        double[] start = null;
        if (priorExists && prior_coefficients != null) {
            if (prior_coefficients.length == coeffs_size) start = prior_coefficients.clone();
            else if (!includeBackground && prior_coefficients.length == coeffs_size + 1) {
                start = new double[coeffs_size];
                System.arraycopy(prior_coefficients, 1, start, 0, coeffs_size);
            }
        }
        double[] solution = fit(qs, ys, invVariance, start);
        storeSolution(solution);

        // sigma(P(r)): covariance of the unpenalized fit (the L1 posterior is not Gaussian; this is
        // the data-only uncertainty, which is what the significance of negative values should use)
        double[][] des = new double[rows][coeffs_size];
        for (int i = 0; i < rows; i++) for (int c = 0; c < coeffs_size; c++) des[i][c] = a_matrix.get(i, c);
        setPosterior(normalMatrix(des, invVariance, null, 0),
                reducedChiSquared(des, ys, invVariance, solution), des, invVariance, null, 0, solution);
        status = "solved";
        setPrDistribution();
        calculateIzeroRg();
        setModelUsed(modelName());
    }

    // q-values of the rows currently in a_matrix (kept alongside so fit() can rebuild rows)
    private double[] fittedQ;
    private double a_matrixQ(int i) { return fittedQ[i]; }

    private void storeSolution(double[] solution) {
        am_vector = new SimpleMatrix(coeffs_size, 1);
        for (int j = 0; j < coeffs_size; j++) am_vector.set(j, 0, solution[j]);
        coefficients = new double[includeBackground ? coeffs_size : coeffs_size + 1];
        if (includeBackground) System.arraycopy(solution, 0, coefficients, 0, coeffs_size);
        else System.arraycopy(solution, 0, coefficients, 1, coeffs_size);     // coefficients[0] = 0
        totalCoefficients = coefficients.length;
    }

    /* ---------------------------------------------------------------------- outputs */

    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        // heights p_j relate to P(r) by q*I(q) = 4 pi dr sum_j P(r_j) sin(q r_j)/r_j (midpoint rule),
        // so P(r) = standardizedScale * p / (4 pi dr): same units as the Moore P(r)
        return standardizedScale/(4*Math.PI*del_r)*splineFunction.value(r_value)*scale;
    }

    /** Rg, I(0), <r> and area from histogram heights (coefficients layout: [bkg, p_0..p_{ns-1}]) */
    private double[] moments(double[] coeffs) {
        double s0 = 0, s1 = 0, s2 = 0, sum = coeffs[0];
        for (int j = 0; j < r_vector_size; j++) {
            double p = coeffs[j + 1], r = r_vector[j];
            s0 += p; s1 += r*p; s2 += r*r*p;
            sum += p;
        }
        s0 *= del_r; s1 *= del_r; s2 *= del_r;
        return new double[]{ Math.sqrt(0.5*s2/s0), sum*standardizedScale + standardizedMin, s1/s0, s0 };
    }

    @Override
    void calculateIzeroRg() {
        double[] m = moments(coefficients);
        rg = m[0];
        izero = m[1];
        rAverage = m[2];
        area = m[3];
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
        StringBuilder sb = new StringBuilder();   // previously several lines used '=' and overwrote the rest
        sb.append(String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS DIRECT INVERSE FOURIER TRANSFORM OF I(q) %n"));
        sb.append(String.format("REMARK 265  COEFFICIENTS ARE THE HISTOGRAM HEIGHTS WITH EQUAL BIN WIDTHS %n"));
        sb.append(String.format("REMARK 265  PERFORMED WITH L1-NORM REGULARIZATION %n"));
        sb.append(String.format("REMARK 265  REGULARIZATION => minimize|ABS_VALUE HISTOGRAM|  %n"));
        if (positiveOnly) sb.append(String.format("REMARK 265  HISTOGRAM CONSTRAINED AS POSITIVE ONLY %n"));
        sb.append(String.format("REMARK 265  CHI^2 %s %n", WEIGHT_BY_ERRORS ? "WEIGHTED BY 1/SIGMA^2" : "UNWEIGHTED"));
        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265                LAMBDA (WEIGHT) : %.2E %n", lambda));
        sb.append(String.format("REMARK 265            BIN WIDTH (delta r) : %.4f %n", del_r));
        sb.append(String.format("REMARK 265             DISTRIBUTION SCORE : %.4f %n", prScore));
        sb.append(String.format("REMARK 265 %n"));
        if (!includeBackground) {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n"));
        } else {
            sb.append(String.format("REMARK 265        CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]));
        }
        this.description = sb.toString();
    }

    private void setSplineFunction() {
        double[] rr = new double[totalInDistribution], pp = new double[totalInDistribution];
        for (int i = 0; i < totalInDistribution; i++) {
            XYDataItem item = prDistribution.getDataItem(i);
            rr[i] = item.getXValue();
            pp[i] = item.getYValue();
        }
        splineFunction = new SplineInterpolator().interpolate(rr, pp);
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
        double[] row = new double[coeffs_size];
        designRow(qvalue, row);
        int offset = includeBackground ? 1 : 0;
        double sum = coefficients[0];
        for (int j = 0; j < r_vector_size; j++) sum += coefficients[j + 1]*row[j + offset];
        return sum*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return this.calculateQIQ(qvalue)/qvalue;
    }

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

    /**
     * Relative errors of Rg and I(0) by refitting random subsets of the data (same sampling as
     * before: per bin, a random 1-46% of points). Subset fits use fit(), which does not modify this
     * object, so the coefficients, design matrix, rows, P(r), Rg and I(0) are left exactly as they were.
     */
    @Override
    public void estimateErrors(XYSeries fittedqIq) {
        int size = fittedqIq.getItemCount();
        double upperq = fittedqIq.getMaxX();
        int bins = r_vector_size;
        double delta_q = upperq/bins;
        double invStdev = 1.0/standardizedScale;

        // weights for the fitted data, by q (fittedqIq may differ from the data used to build a_matrix)
        double[] wAll = new double[size];
        for (int i = 0; i < size; i++) wAll[i] = weightAtQ(fittedqIq.getX(i).doubleValue());

        Random randomGenerator = new Random();
        ArrayList<Double> rgValues = new ArrayList<>(), izeroValues = new ArrayList<>();
        double[] start = (am_vector == null) ? null : new double[coeffs_size];
        if (start != null) for (int j = 0; j < coeffs_size; j++) start[j] = am_vector.get(j, 0);

        int totalRuns = 71;
        for (int run = 0; run < totalRuns; run++) {
            ArrayList<Integer> picked = new ArrayList<>();
            int startbb = 0;
            for (int b = 1; b <= bins; b++) {
                double upperBound = delta_q*b;
                int upperbb = startbb;
                while (upperbb < size && fittedqIq.getX(upperbb).doubleValue() < upperBound) upperbb++;
                if (b == bins) upperbb = size;                       // last bin includes q = upperq
                if (upperbb > startbb) {
                    double samplingLimit = (1.0 + randomGenerator.nextInt(46))/100.0;
                    for (int idx : Functions.randomIntegersBounded(startbb, upperbb, samplingLimit)) picked.add(idx);
                }
                startbb = upperbb;
            }
            if (picked.size() <= coeffs_size) continue;               // too few points to fit

            double[] qs = new double[picked.size()], ys = new double[picked.size()], ws = new double[picked.size()];
            for (int i = 0; i < qs.length; i++) {
                XYDataItem item = fittedqIq.getDataItem(picked.get(i));
                qs[i] = item.getXValue();
                ys[i] = (item.getYValue() - standardizedMin)*invStdev;
                ws[i] = wAll[picked.get(i)];
            }
            double[] sol = fit(qs, ys, ws, start);
            double[] coeffs = new double[r_vector_size + 1];
            if (includeBackground) System.arraycopy(sol, 0, coeffs, 0, coeffs_size);
            else System.arraycopy(sol, 0, coeffs, 1, coeffs_size);
            double[] m = moments(coeffs);
            if (m[3] > 0 && !Double.isNaN(m[0])) {
                rgValues.add(m[0]);
                izeroValues.add(m[1]);     // was (sum + min)*scale; I(0) is sum*scale + min
            }
        }
        rgError = relativeSpread(rgValues);
        iZeroError = relativeSpread(izeroValues);
    }

    /** 1/sigma^2 at the data point nearest q (1 when unweighted) */
    private double weightAtQ(double q) {
        if (!WEIGHT_BY_ERRORS || standardVariance == null || standardVariance.getItemCount() == 0) return 1.0;
        int lo = 0, hi = standardVariance.getItemCount() - 1;
        while (lo < hi) {                                  // binary search, x sorted
            int mid = (lo + hi) >>> 1;
            if (standardVariance.getX(mid).doubleValue() < q) lo = mid + 1; else hi = mid;
        }
        if (lo > 0 && Math.abs(standardVariance.getX(lo - 1).doubleValue() - q) < Math.abs(standardVariance.getX(lo).doubleValue() - q)) lo--;
        double v = standardVariance.getY(lo).doubleValue();
        return (v > 0) ? 1.0/v : 1.0;
    }

    /** sample standard deviation / mean (NaN if fewer than 2 values) */
    private static double relativeSpread(ArrayList<Double> values) {
        int n = values.size();
        if (n < 2) return Double.NaN;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= n;
        double ss = 0;
        for (double v : values) ss += (v - mean)*(v - mean);
        return Math.sqrt(ss/(n - 1))/mean;
    }
}
