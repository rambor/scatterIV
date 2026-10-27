package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;


/**
 * Shannon-limited direct inversion: P(r) as a histogram of K = ns - 1 heights at the Shannon bin
 * mid-points r_i = (i + 1/2) pi/qmax,
 *
 *     q*I(q) = a_0 + sum_i p_i sin(q r_i)/r_i,
 *
 * solved by (weighted) least squares without regularization. The bin containing dmax has no
 * fitted height (fitting all ns heights is unstable without regularization); P is taken to fall
 * from the last height to P(dmax) = 0. See MODEL_FITTED_BINS_ONLY for what prDistributionForFitting
 * holds.
 *
 * WEIGHT_BY_ERRORS (default true) weights chi^2 by 1/sigma^2, consistent with the other IFT classes
 * and with the AIC / evidence in IndirectFT; false restores the previous unweighted fit.
 */
public class SVD extends IndirectFT {

    /** weight the fit by 1/sigma^2 (consistent with the other transforms); false = previous behaviour */
    public static boolean WEIGHT_BY_ERRORS = true;

    /**
     * true (default): prDistributionForFitting holds only the ns - 1 fitted heights, one per Shannon
     * bin at its mid-point, in calculatePofRAtR units; the bin containing dmax, which has no fitted
     * height (fitting all ns heights is unstable without regularization), is left out of modelling.
     * false: bin averages over every Shannon bin, including the one containing dmax, as for the
     * other transforms (IndirectFT.setShannonBinnedDistribution).
     */
    public static boolean MODEL_FITTED_BINS_ONLY = true;

    private double del_r;
    double[] r_vector;
    int r_vector_size;
    private double[][] design;       // rows x coeffs_size
    private double[] target;         // standardized q*I(q)
    private double[] invVariance;    // fit weights (1/sigma^2, or 1)


    // Dataset should be standardized and in form of [q, q*I(q)]
    public SVD(XYSeries dataset, XYSeries errors, double dmax, double qmax, boolean includeBackground) {
        super(dataset, errors, dmax, qmax, 0, includeBackground);
        this.createDesignMatrix(this.data);
        this.solve();
        this.setModelUsed("DIRECT SVD");
    }


    /**
     * Dataset and errors are already standardized (constant background excluded, as before)
     *
     * @param scaledqIqdataset standardized data
     * @param scaledqIqErrors  errors in the form q*sigma/scale
     */
    public SVD(XYSeries scaledqIqdataset, XYSeries scaledqIqErrors, double dmax, double qmax, double lambda,
               double stdmin, double stdscale) {

        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, false, stdmin, stdscale);

        standardVariance = new XYSeries("standardized error");
        int totalItems = scaledqIqdataset.getItemCount();
        for (int r = 0; r < totalItems; r++) {
            double sigma = scaledqIqErrors.getY(r).doubleValue();       // already q*I(q)/scale
            standardVariance.add(scaledqIqdataset.getX(r), sigma*sigma);
        }

        this.createDesignMatrix(scaledqIqdataset);
        this.solve();
        this.setModelUsed("DIRECT SVD");
    }

    /** copy constructor */
    public SVD(SVD original) {
        super(original);
        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();
        this.r_vector_size = original.r_vector_size;
        if (original.design != null) {
            this.design = new double[original.design.length][];
            for (int i = 0; i < design.length; i++) design[i] = original.design[i].clone();
        }
        this.target = (original.target == null) ? null : original.target.clone();
        this.invVariance = (original.invVariance == null) ? null : original.invVariance.clone();
    }

    @Override
    void calculateIzeroRg() {
        double tempRgSum = 0, tempRg2Sum = 0, xaverage = 0;
        for (int i = 1; i < totalInDistribution - 1; i++) {        // end points are zero
            XYDataItem item = prDistribution.getDataItem(i);
            double rvalue = item.getXValue();
            tempRg2Sum += rvalue*rvalue*item.getYValue()*del_r;
            tempRgSum += item.getYValue()*del_r;                     // width x height => area
            xaverage += rvalue*item.getYValue()*del_r;
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
        // plotting series: the fitted heights, with P(0) = P(dmax) = 0
        prDistribution = new XYSeries("PRDistribution");
        prDistribution.add(0, 0);
        for (int i = 0; i < r_vector_size; i++) prDistribution.add(r_vector[i], coefficients[i + 1]);
        prDistribution.add(dmax, 0);
        totalInDistribution = prDistribution.getItemCount();

        // d(height)/d(coefficient) per prDistribution point, for the significance of negative P(r)
        prDistributionBasis = new double[totalInDistribution][];
        int offset = includeBackground ? 1 : 0;
        for (int i = 0; i < r_vector_size; i++) {
            double[] b = new double[coeffs_size];
            b[offset + i] = 1.0;
            prDistributionBasis[i + 1] = b;
        }
        deriveDistributionSigma();

        // modelling series (Shannon bins of width pi/qmax)
        setSplineFunction();
        if (MODEL_FITTED_BINS_ONLY) {
            setFittedBinsDistribution();
        } else {
            setShannonBinnedDistribution();
        }

        scoreDistribution();
        setHeaderDetails();
    }


    /** the ns - 1 fitted heights at their bin mid-points, converted to calculatePofRAtR units */
    private void setFittedBinsDistribution() {
        bin_width = Math.PI/qmax;
        final double toPofR = standardizedScale/(4*Math.PI*del_r);
        prDistributionForFitting = new XYSeries("OutputPrDistribution");
        for (int i = 0; i < r_vector_size; i++) {
            if (r_vector[i] < dmax) prDistributionForFitting.add(r_vector[i], coefficients[i + 1]*toPofR);
        }
    }

    private void setHeaderDetails() {
        StringBuilder sb = new StringBuilder();     // previously line 2 replaced line 1
        sb.append(String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS DIRECT INVERSE FOURIER TRANSFORM OF I(q) %n"));
        sb.append(String.format("REMARK 265  SHANNON LIMITED MOORE-PENROSE PSEUDO-INVERSE MATRIX INVERSION %n"));
        sb.append(String.format("REMARK 265  COEFFICIENTS ARE THE HISTOGRAM HEIGHTS WITH EQUAL BIN WIDTHS %n"));
        sb.append(String.format("REMARK 265  CHI^2 %s %n", WEIGHT_BY_ERRORS ? "WEIGHTED BY 1/SIGMA^2" : "UNWEIGHTED"));
        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", del_r));   // was dmax/ns
        sb.append(String.format("REMARK 265            DISTRIBUTION SCORE : %.4f %n", prScore));
        sb.append(String.format("REMARK 265 %n"));
        if (includeBackground) {
            sb.append(String.format("REMARK 265        CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]));
        } else {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n"));
        }
        this.description = sb.toString();
    }


    /** spline through the heights and the end points; used by calculatePofRAtR */
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
        for (int j = 0; j < r_vector_size; j++) {
            double rvalue = r_vector[j];
            sum += coefficients[j + 1]*FastMath.sin(rvalue*qvalue)/rvalue;
        }
        return sum*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return (this.calculateQIQ(qvalue))/qvalue;
    }


    /**
     * Heights relate to P(r) by q*I(q) = 4 pi dr sum P(r_i) sin(q r_i)/r_i (midpoint rule), so
     * P(r) = standardizedScale * p / (4 pi dr), the same units as the other transforms.
     * (Previously 1/(2 pi^2), the Moore-series factor, which does not apply to histogram heights.)
     */
    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        if (r_value <= 0 || r_value >= dmax) return 0;
        return standardizedScale/(4*Math.PI*del_r)*splineFunction.value(r_value)*scale;
    }

    @Override
    public void estimateErrors(XYSeries fittedData) {

    }

    @Override
    public void normalizeDistribution() {

    }

    @Override
    public void createDesignMatrix(XYSeries datasetInuse) {

        ns = (int) Math.ceil(qmax*dmax*INV_PI);
        del_r = Math.PI/qmax;
        bin_width = del_r;

        r_vector_size = ns - 1;                       // one height per Shannon bin, except the bin holding dmax
        coeffs_size = includeBackground ? r_vector_size + 1 : r_vector_size;
        rows = datasetInuse.getItemCount();

        r_vector = new double[r_vector_size];
        for (int i = 0; i < r_vector_size; i++) r_vector[i] = (0.5 + i)*del_r;   // bin mid-points

        a_matrix = new SimpleMatrix(rows, coeffs_size);
        y_vector = new SimpleMatrix(rows, 1);
        design = new double[rows][coeffs_size];
        target = new double[rows];
        invVariance = new double[rows];
        final int offset = includeBackground ? 1 : 0;

        for (int row = 0; row < rows; row++) {
            XYDataItem tempData = datasetInuse.getDataItem(row);
            double q = tempData.getXValue();
            double[] d = design[row];
            if (includeBackground) d[0] = 1.0;                  // constant background term
            for (int i = 0; i < r_vector_size; i++) d[i + offset] = FastMath.sin(r_vector[i]*q)/r_vector[i];
            for (int c = 0; c < coeffs_size; c++) a_matrix.set(row, c, d[c]);
            y_vector.set(row, 0, tempData.getYValue());
            target[row] = tempData.getYValue();

            double v = (standardVariance != null && row < standardVariance.getItemCount())
                    ? standardVariance.getY(row).doubleValue() : 1.0;
            invVariance[row] = (WEIGHT_BY_ERRORS && v > 0) ? 1.0/v : 1.0;
        }
    }


    /**
     * Weighted least squares by Householder QR on sqrt(W) A a = sqrt(W) y. Numerically rank-deficient
     * columns are set to zero (the role the pseudo-inverse played); the Shannon-spaced heights are
     * normally well conditioned.
     */
    void solve() {
        double[][] S = new double[rows][];
        double[] rhs = new double[rows];
        for (int i = 0; i < rows; i++) {
            double s = Math.sqrt(invVariance[i]);
            double[] row = new double[coeffs_size];
            for (int c = 0; c < coeffs_size; c++) row[c] = s*design[i][c];
            S[i] = row;
            rhs[i] = s*target[i];
        }
        double[] solution = householderSolve(S, rhs, coeffs_size);

        am_vector = new SimpleMatrix(coeffs_size, 1);
        for (int j = 0; j < coeffs_size; j++) am_vector.set(j, 0, solution[j]);

        totalCoefficients = includeBackground ? coeffs_size : coeffs_size + 1;
        coefficients = new double[totalCoefficients];
        if (includeBackground) System.arraycopy(solution, 0, coefficients, 0, coeffs_size);
        else System.arraycopy(solution, 0, coefficients, 1, coeffs_size);       // coefficients[0] = 0

        // covariance (for the significance of negative P(r)); unregularized, so k_eff = coeffs_size
        setPosterior(normalMatrix(design, invVariance, null, 0),
                reducedChiSquared(design, target, invVariance, solution), design, invVariance, null, 0, solution);

        status = "solved";
        this.setPrDistribution();
        this.calculateIzeroRg();
        // coeffs_size is left equal to the design-matrix column count (it used to be reset to
        // coefficients.length here, which no longer matched a_matrix without a background)
    }
}
