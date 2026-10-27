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
 * Indirect transform with P(r) parameterized by Legendre polynomials on [0, dmax]:
 *
 *     P(r_i) = sum_{l=0}^{K-1} c_l P_l(x_i),   x_i = (2 r_i - dmax)/dmax,   K = ns - 1,  ns = ceil(qmax dmax / pi)
 *     q*I(q) = a_0 + sum_i P(r_i) sin(q r_i)/r_i,    r_i = (i + 0.5) h,  i = 0..K-1
 *
 * One point per (Shannon-width) bin, as many points as polynomials: P(r) is effectively a
 * Shannon-limited histogram, which is what keeps this model well behaved.
 *
 * Grid spacing h:
 *   STRETCH_GRID_WITH_DMAX = false (default): h = pi/qmax, the original grid. It does not depend
 *       on dmax, so every dmax with the same Shannon number gives an identical fit (and AIC);
 *       within such a step only the P(r) score distinguishes dmax values.
 *   STRETCH_GRID_WITH_DMAX = true: h = dmax/K, so the bins tile (0, dmax) and the fit changes
 *       continuously with dmax (spacing stays within ~1/K of pi/qmax).
 *
 * L2 regularization, unchanged in form and scale from the original:
 *     w * [ sum_{i=1}^{K-2} (P_i - (P_{i-1} + P_{i+1})/2)^2 + P_0^2/2 + P_{K-1}^2/2 ]
 * Solved exactly (Householder QR); Legendre values from the three-term recurrence.
 */
public class LegendreTransform extends IndirectFT {

    /** false: w = lambda (as before). true: lambda is dimensionless, w = lambda * trace(A'WA)/trace(R'R) */
    public static boolean NORMALIZE_LAMBDA = false;
    /**
     * false (default): original fixed spacing pi/qmax. true: spacing dmax/K, so the fit changes
     * continuously with dmax. On SYCP1 (AIC + prScore) the original grid picked 216 A and the
     * stretched grid 210 A.
     */
    public static boolean STRETCH_GRID_WITH_DMAX = false;

    private double del_r;
    double[] r_vector;          // bin centres
    double[] regularization_r_vector;   // kept for compatibility; the penalty uses r_vector
    double[] target;            // standardized q*I(q)
    double[] invVariance;       // 1/sigma^2 of standardized q*I(q)
    double[] qvalues;
    int r_vector_size;
    public final double invDmax;
    private int nLegendre;               // K
    private double[][] legendreTable;    // [M][K]: P_l(x_i)
    private double[][] design;           // rows x coeffs_size
    private double[] heights;            // P(r_i) of the current solution

    public LegendreTransform(XYSeries dataset, XYSeries untransformedErrors, double dmax, double qmax,
                             double lambda, boolean includeBackground) {
        super(dataset, untransformedErrors, dmax, qmax, lambda, includeBackground);
        this.invDmax = 1.0/dmax;
        this.invertStandardVariance();
        this.createDesignMatrix(this.data);
        this.solve();
        this.setModelUsed("Legendre L2-NORM");
    }

    public LegendreTransform(XYSeries scaledqIqData, XYSeries scaledqIqErrors, double dmax, double qmax,
                             double lambda, double stdmin, double stdscale, boolean background) {
        this(scaledqIqData, scaledqIqErrors, null, dmax, qmax, lambda, stdmin, stdscale, background);
    }

    /** @param priors accepted for compatibility; the exact solve needs no starting point */
    public LegendreTransform(XYSeries scaledqIqData, XYSeries scaledqIqErrors, double[] priors, double dmax,
                             double qmax, double lambda, double stdmin, double stdscale, boolean background) {
        super(scaledqIqData, scaledqIqErrors, dmax, qmax, lambda, background, stdmin, stdscale);
        this.invDmax = 1.0/dmax;
        if (priors != null) {
            this.prior_coefficients = priors.clone();
            priorExists = true;
        }
        standardVariance = new XYSeries("standardized error");
        int totalItems = scaledqIqData.getItemCount();
        invVariance = new double[totalItems];
        for (int r = 0; r < totalItems; r++) {
            double sigma = scaledqIqErrors.getY(r).doubleValue();   // q*sigma/scale
            standardVariance.add(scaledqIqData.getX(r), sigma*sigma);
            invVariance[r] = 1.0/(sigma*sigma);
        }
        this.createDesignMatrix(scaledqIqData);
        this.solve();
        this.setModelUsed("Legendre L2-NORM");
    }

    public LegendreTransform(LegendreTransform original) {
        super(original);
        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();
        this.target = original.target.clone();
        this.qvalues = original.qvalues.clone();
        this.invVariance = original.invVariance.clone();
        this.r_vector_size = original.r_vector_size;
        this.invDmax = original.invDmax;
        this.nLegendre = original.nLegendre;
        this.legendreTable = deepCopy(original.legendreTable);
        this.design = deepCopy(original.design);
        this.heights = (original.heights == null) ? null : original.heights.clone();
    }

    private static double[][] deepCopy(double[][] m) {
        if (m == null) return null;
        double[][] c = new double[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    /* ---------------------------------------------------------------------- basis */

    /** P_0..P_{K-1} at x by Bonnet's recurrence: (n+1) P_{n+1} = (2n+1) x P_n - n P_{n-1} */
    static void legendreValues(double x, double[] out) {
        int K = out.length;
        if (K == 0) return;
        out[0] = 1.0;
        if (K == 1) return;
        out[1] = x;
        for (int n = 1; n + 1 < K; n++) {
            out[n + 1] = ((2*n + 1)*x*out[n] - n*out[n - 1])/(n + 1);
        }
    }

    /** sin(q r_i)/r_i for the bin centres, by recurrence */
    private void sincRow(double q, double[] out) {
        double twoCos = 2*FastMath.cos(q*del_r);
        double sCur = FastMath.sin(0.5*q*del_r), sPrev = -sCur;      // sin(q r_0), sin(q r_{-1})
        for (int i = 0; i < r_vector_size; i++) {
            out[i] = sCur/r_vector[i];
            double sNext = twoCos*sCur - sPrev;
            sPrev = sCur;
            sCur = sNext;
        }
    }

    /** design row: [1,] sum_i sinc_i * P_l(x_i) */
    private void designRow(double q, double[] sinc, double[] out) {
        sincRow(q, sinc);
        int offset = includeBackground ? 1 : 0;
        if (includeBackground) out[0] = 1.0;
        for (int l = 0; l < nLegendre; l++) {
            double s = 0;
            for (int i = 0; i < r_vector_size; i++) s += sinc[i]*legendreTable[i][l];
            out[l + offset] = s;
        }
    }

    /* datasetInUse does not have to be standardized */
    public void createDesignMatrix(XYSeries datasetInuse) {
        ns = (int) Math.ceil(qmax*dmax*INV_PI);
        bin_width = Math.PI/qmax;                         // Shannon bin
        nLegendre = Math.max(1, ns - 1);                  // One less than Shannon bins (see my paper)
        coeffs_size = includeBackground ? nLegendre + 1 : nLegendre;

        r_vector_size = nLegendre;                        // one point per bin, square map (as originally)
        del_r = STRETCH_GRID_WITH_DMAX ? dmax/(double) r_vector_size : Math.PI/qmax;
        r_vector = new double[r_vector_size];
        legendreTable = new double[r_vector_size][nLegendre];
        for (int i = 0; i < r_vector_size; i++) {
            r_vector[i] = (0.5 + i)*del_r;
            legendreValues((2*r_vector[i] - dmax)*invDmax, legendreTable[i]);
        }
        regularization_r_vector = r_vector;

        rows = datasetInuse.getItemCount();
        target = new double[rows];
        qvalues = new double[rows];
        design = new double[rows][coeffs_size];
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        y_vector = new SimpleMatrix(rows, 1);
        double[] sinc = new double[r_vector_size];
        for (int row = 0; row < rows; row++) {
            XYDataItem item = datasetInuse.getDataItem(row);
            designRow(item.getXValue(), sinc, design[row]);
            for (int c = 0; c < coeffs_size; c++) a_matrix.set(row, c, design[row][c]);
            y_vector.set(row, 0, item.getYValue());
            target[row] = item.getYValue();
            qvalues[row] = item.getXValue();
        }
    }

    /* ---------------------------------------------------------------------- solver */

    /** penalty rows in coefficient space (the background column is zero) */
    private double[][] penaltyRows() {
        int offset = includeBackground ? 1 : 0, M = r_vector_size;
        double[][] R = new double[M][coeffs_size];
        for (int i = 1; i < M - 1; i++)
            for (int l = 0; l < nLegendre; l++)
                R[i - 1][l + offset] = legendreTable[i][l] - 0.5*(legendreTable[i - 1][l] + legendreTable[i + 1][l]);
        double h = Math.sqrt(0.5);
        for (int l = 0; l < nLegendre; l++) {
            R[M - 2][l + offset] = h*legendreTable[0][l];
            R[M - 1][l + offset] = h*legendreTable[M - 1][l];
        }
        return R;
    }

    private double penaltyWeight(double[][] R, double[][] des, double[] w) {
        if (lambda <= 0) return 0;
        if (!NORMALIZE_LAMBDA) return lambda;
        int offset = includeBackground ? 1 : 0;
        double tA = 0, tR = 0;
        for (int i = 0; i < des.length; i++)
            for (int c = offset; c < coeffs_size; c++) tA += w[i]*des[i][c]*des[i][c];
        for (double[] row : R) for (double v : row) tR += v*v;
        return (tR > 0) ? lambda*tA/tR : 0;
    }

    /** exact fit on arbitrary rows of the design; does not modify object state */
    private double[] fit(double[][] des, double[] ys, double[] w) {
        double[][] R = penaltyRows();
        double weight = penaltyWeight(R, des, w);
        int extra = (weight > 0) ? R.length : 0, m = des.length + extra, k = coeffs_size;
        double[][] S = new double[m][];
        double[] rhs = new double[m];
        for (int i = 0; i < des.length; i++) {
            double s = Math.sqrt(w[i]);
            double[] row = new double[k];
            for (int c = 0; c < k; c++) row[c] = s*des[i][c];
            S[i] = row;
            rhs[i] = s*ys[i];
        }
        double sw = Math.sqrt(weight);
        for (int j = 0; j < extra; j++) {
            double[] row = new double[k];
            for (int c = 0; c < k; c++) row[c] = sw*R[j][c];
            S[des.length + j] = row;
        }
        return householderSolve(S, rhs, k);
    }

    public void solve() {
        try {
            double[] solution = fit(design, target, invVariance);
            coefficients = new double[includeBackground ? coeffs_size : coeffs_size + 1];
            am_vector = new SimpleMatrix(coeffs_size, 1);
            if (includeBackground) System.arraycopy(solution, 0, coefficients, 0, coeffs_size);
            else System.arraycopy(solution, 0, coefficients, 1, coeffs_size);
            for (int j = 0; j < coeffs_size; j++) am_vector.set(j, 0, solution[j]);
            totalCoefficients = coefficients.length;
            heights = gridHeights(coefficients);
            double[][] R = penaltyRows();
            double wgt = penaltyWeight(R, design, invVariance);
            setPosterior(normalMatrix(design, invVariance, R, wgt),
                    reducedChiSquared(design, target, invVariance, solution), design, invVariance, R, wgt, solution);
            status = "solved";
            this.setPrDistribution();
            this.calculateIzeroRg();
        } catch (RuntimeException e) {
            status = "solve failed: " + e.getMessage();
            System.out.println("Exception occurred " + e.getMessage());
        }
    }

    /** P(r_i) on the quadrature grid; coeffs layout [bkg, c_0..c_{K-1}] */
    private double[] gridHeights(double[] coeffs) {
        double[] h = new double[r_vector_size];
        for (int i = 0; i < r_vector_size; i++) {
            double p = 0;
            for (int l = 0; l < nLegendre; l++) p += coeffs[l + 1]*legendreTable[i][l];
            h[i] = p;
        }
        return h;
    }

    /* ---------------------------------------------------------------------- outputs */

    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        // heights relate to P(r) as q*I(q) = 4 pi dr sum P(r_i) sin(q r_i)/r_i; spline between points
        if (r_value <= 0 || r_value >= dmax) return 0;
        return standardizedScale/(4*Math.PI*del_r)*splineFunction.value(r_value)*scale;
    }

    /** {Rg, I(0), <r>, area} from grid heights and background */
    private double[] moments(double[] h, double background) {
        double s0 = 0, s1 = 0, s2 = 0;
        for (int i = 0; i < r_vector_size; i++) {
            double r = r_vector[i];
            s0 += h[i]; s1 += r*h[i]; s2 += r*r*h[i];
        }
        double izeroValue = (s0 + background)*standardizedScale + standardizedMin;
        return new double[]{ Math.sqrt(0.5*s2/s0), izeroValue, s1/s0, s0*del_r };
    }

    @Override
    void calculateIzeroRg() {
        double[] m = moments(heights, coefficients[0]);
        rg = m[0];
        izero = m[1];
        rAverage = m[2];
        area = m[3];
    }

    @Override
    void setPrDistribution() {
        prDistribution = new XYSeries("PRDistribution");
        prDistribution.add(0, 0);
        for (int i = 0; i < r_vector_size; i++) prDistribution.add(r_vector[i], heights[i]);
        prDistribution.add(dmax, 0);
        totalInDistribution = prDistribution.getItemCount();

        // d(height)/d(coefficient) for each prDistribution point (null at r = 0 and dmax)
        prDistributionBasis = new double[totalInDistribution][];
        int offset = includeBackground ? 1 : 0;
        for (int i = 0; i < r_vector_size; i++) {
            double[] b = new double[coeffs_size];
            for (int l = 0; l < nLegendre; l++) b[l + offset] = legendreTable[i][l];
            prDistributionBasis[i + 1] = b;
        }
        deriveDistributionSigma();

        // P(r) at the mid-point of each Shannon bin (pi/qmax); on the default grid these coincide
        // with the fitted heights, with STRETCH_GRID_WITH_DMAX they come from the spline
        setSplineFunction();
        setShannonBinnedDistribution();

        scoreDistribution();
        setHeaderDetails();
    }

    private void setHeaderDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS INDIRECT INVERSE FOURIER TRANSFORM OF I(q) %n"));
        sb.append(String.format("REMARK 265  P(r) APPROXIMATED AS A LEGENDRE POLYNOMIAL %n"));   // was overwriting line 1
        sb.append(String.format("REMARK 265  COEFFICIENTS ARE LEGENDRE WEIGHTS FOR EACH POLYNOMIAL %n"));
        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", del_r));
        sb.append(String.format("REMARK 265         SHANNON BIN (pi/qmax) : %.4f %n", bin_width));
        sb.append(String.format("REMARK 265            DISTRIBUTION SCORE : %.4f %n", prScore));
        sb.append(String.format("REMARK 265 %n"));
        if (!includeBackground) {
            sb.append(String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n"));
        } else {
            sb.append(String.format("REMARK 265        CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]));
            sb.append(String.format("REMARK 265            SCALED  s*m(0) + min : %.4E %n", coefficients[0]*standardizedScale + standardizedMin));
        }
        sb.append(String.format("REMARK 265  LEGENDRE COEFFICIENTS (UNSCALED)%n"));
        for (int i = 1; i < totalCoefficients; i++) {
            sb.append(String.format("REMARK 265                        l_(%2d) : %.3E %n", i - 1, coefficients[i]));
        }
        this.description = sb.toString();
    }

    /** exact model for the invariants: I(q) = scale * sum_i P(r_i) sin(q r_i)/(q r_i), P(r_i) = heights */
    @Override
    protected SincModel sincModel() {
        return histogramSincModel(r_vector, heights);
    }

    /** unscaled q*I(q) */
    @Override
    public double calculateQIQ(double qvalue) {
        double[] sinc = new double[r_vector_size];
        sincRow(qvalue, sinc);
        double sum = coefficients[0];
        for (int i = 0; i < r_vector_size; i++) sum += heights[i]*sinc[i];
        return sum*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return this.calculateQIQ(qvalue)/qvalue;
    }

    /**
     * Relative errors of Rg and I(0) from refits of random subsets (same sampling scheme as before).
     * Subset fits do not modify this object. Previously: weights were misaligned with the subset rows,
     * I(0) summed Legendre coefficients instead of P(r), and the design matrix, rows, Rg and I(0) were
     * left from the last subset.
     */
    @Override
    public void estimateErrors(XYSeries fittedqIq) {
        int size = fittedqIq.getItemCount();
        double upperq = fittedqIq.getMaxX();
        int bins = totalCoefficients - 1;
        double delta_q = upperq/bins;
        double invStdev = 1.0/standardizedScale;
        Random randomGenerator = new Random();
        ArrayList<Double> rgValues = new ArrayList<>(), izeroValues = new ArrayList<>();
        double[] sinc = new double[r_vector_size];

        for (int run = 0; run < 71; run++) {
            ArrayList<Integer> picked = new ArrayList<>();
            int startbb = 0;
            for (int b = 1; b <= bins; b++) {
                int upperbb = startbb;
                while (upperbb < size && fittedqIq.getX(upperbb).doubleValue() < delta_q*b) upperbb++;
                if (b == bins) upperbb = size;
                if (upperbb > startbb) {
                    double samplingLimit = (1.0 + randomGenerator.nextInt(46))/100.0;
                    for (int idx : Functions.randomIntegersBounded(startbb, upperbb, samplingLimit)) picked.add(idx);
                }
                startbb = upperbb;
            }
            if (picked.size() <= coeffs_size) continue;

            double[][] des = new double[picked.size()][coeffs_size];
            double[] ys = new double[picked.size()], ws = new double[picked.size()];
            for (int i = 0; i < ys.length; i++) {
                XYDataItem item = fittedqIq.getDataItem(picked.get(i));
                designRow(item.getXValue(), sinc, des[i]);
                ys[i] = (item.getYValue() - standardizedMin)*invStdev;
                ws[i] = weightAtQ(item.getXValue());
            }
            double[] sol = fit(des, ys, ws);
            double[] coeffs = new double[nLegendre + 1];
            if (includeBackground) System.arraycopy(sol, 0, coeffs, 0, coeffs_size);
            else System.arraycopy(sol, 0, coeffs, 1, coeffs_size);
            double[] m = moments(gridHeights(coeffs), coeffs[0]);
            if (m[3] > 0 && !Double.isNaN(m[0])) {
                rgValues.add(m[0]);
                izeroValues.add(m[1]);
            }
        }
        rgError = relativeSpread(rgValues);
        iZeroError = relativeSpread(izeroValues);
    }

    /** 1/sigma^2 at the data point nearest q */
    private double weightAtQ(double q) {
        if (standardVariance == null || standardVariance.getItemCount() == 0) return 1.0;
        int lo = 0, hi = standardVariance.getItemCount() - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (standardVariance.getX(mid).doubleValue() < q) lo = mid + 1; else hi = mid;
        }
        if (lo > 0 && Math.abs(standardVariance.getX(lo - 1).doubleValue() - q) < Math.abs(standardVariance.getX(lo).doubleValue() - q)) lo--;
        double v = standardVariance.getY(lo).doubleValue();
        return (v > 0) ? 1.0/v : 1.0;
    }

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

    private void setSplineFunction() {
        double[] rr = new double[totalInDistribution], pp = new double[totalInDistribution];
        for (int i = 0; i < totalInDistribution; i++) {
            rr[i] = prDistribution.getX(i).doubleValue();
            pp[i] = prDistribution.getY(i).doubleValue();
        }
        splineFunction = new SplineInterpolator().interpolate(rr, pp);
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

    private void invertStandardVariance() {
        int totalItems = standardVariance.getItemCount();
        invVariance = new double[totalItems];
        for (int r = 0; r < totalItems; r++) invVariance[r] = 1.0/standardVariance.getY(r).doubleValue();
    }
}
