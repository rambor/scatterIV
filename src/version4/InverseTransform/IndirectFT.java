package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.function.Constant;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import org.ejml.simple.SimpleMatrix;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Constants;
import version4.Functions;
import version4.Interpolator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public abstract class IndirectFT implements RealSpacePrObjectInterface, Cloneable {

    /**
     * (n*PI)^2 for n = 0..250. Shared by all instances (was a per-instance literal table,
     * allocated again for every IndirectFT built during a dmax scan).
     */
    static final double[] n_pi_squared = new double[251];
    static {
        for (int i = 0; i < n_pi_squared.length; i++) {
            double npi = i * Math.PI;
            n_pi_squared[i] = npi * npi;
        }
    }

    public double[] coefficients, prior_coefficients; // 1 size larger than am_vector, holds the background term at [0]

    public final double qmax, dmax, lambda;
    public static final double inv2PI2 = 1.0/(2*Math.PI*Math.PI);
    public static final double INV_PI = 1.0/Math.PI;
    public static final double TWO_INV_PI = 2.0/Math.PI;
    public XYSeries nonData; // non-standardized data, not used for fitting
    public XYSeries data, residuals, errors, standardVariance; // should be qI(q) dataset ( non-standardized)
    public double standardizedMin;
    public double standardizedScale;
    public boolean includeBackground = true;
    public double rg, izero, rAverage, sphericalRg;
    public double rgError, iZeroError, rAverageError;
    public int totalInDistribution, totalCoefficients, rows;
    public XYSeries prDistribution;
    public XYSeries sphericalDistribution;
    public XYSeries sphericalDistributionFine;
    public XYSeries prDistributionForFitting; //used by IkeTama for Modeling, this is the Shannon Limited PrDistribution
    public boolean negativeValuesInModel;
    public PolynomialSplineFunction splineFunction;
    public String description="";
    public double sphericalVolume;
    public double degreesOfFreedom, distribution_score;
    public double n_chipoints, aic, prScore;

    /*
     * LINE SEARCH PARAMETERS
     */
    public double alpha = 0.01;            // minimum fraction of decrease in the objective
    public double beta  = 0.17;             // stepsize decrease factor
    public int max_ls_iter = 400;          // maximum backtracking line search iteration
    /*
     * Interior Point Parameters
     */
    public int MU = 2;                    // updating parameter of t
    public int max_nt_iter = 500;         // maximum IPM (Newton) iteration
    public double reltol = 0.0001;
    public double eta = 0.001;
    public double tau = 0.01;
    public double bin_width;
    public int pcgmaxi = 5000;
    public double dobj = Double.NEGATIVE_INFINITY;
    public double pobj = Double.POSITIVE_INFINITY;
    public final double dmax_PI_TWO_INV_PI;
    public final double PI_INV_DMAX;
    public String status;
    public int ns, coeffs_size;
    public SimpleMatrix a_matrix;
    public SimpleMatrix y_vector;
    public SimpleMatrix am_vector;
    private String modelUsed;
    public double area;
    public boolean priorExists=false;
//    public double finalScore;

    public IndirectFT(XYSeries nonStandardizedData, XYSeries errors, double dmax, double qmax, double lambda, boolean includeBackground){

        try {
            this.nonData = nonStandardizedData.createCopy(0, nonStandardizedData.getItemCount()-1);
            this.errors = errors.createCopy(0, errors.getItemCount()-1); // must be transformed back to unscaled (see createNonStandardizedData)
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        this.lambda = lambda;
        this.qmax = qmax;
        this.dmax = dmax;
        this.includeBackground = includeBackground;
        this.dmax_PI_TWO_INV_PI = dmax * Math.PI * TWO_INV_PI;
        this.PI_INV_DMAX = Math.PI/dmax;
        this.standardizeData();
    }


    public IndirectFT(XYSeries standardizedData, XYSeries scaledqIqerrors, double dmax, double qmax, double lambda, boolean includeBackground, double standardizationMin, double standardizationStDev){

        try {
            this.data = standardizedData.createCopy(0, standardizedData.getItemCount()-1);
            this.errors = scaledqIqerrors.createCopy(0, scaledqIqerrors.getItemCount()-1); // must be transformed back to unscaled (see createNonStandardizedData)
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        this.qmax = qmax;
        this.dmax = dmax;
        this.lambda = lambda;
        this.includeBackground = includeBackground;
        this.dmax_PI_TWO_INV_PI = dmax * Math.PI * TWO_INV_PI;
        this.PI_INV_DMAX = Math.PI/dmax;
        standardizedMin = standardizationMin;
        standardizedScale = standardizationStDev;
        this.createNonStandardizedData();
    }


    /**
     * copy constructor
     * @param original
     */
    public IndirectFT(IndirectFT original){

        try {
            this.data = copyOf(original.data);
            this.errors = copyOf(original.errors);
            this.nonData = copyOf(original.nonData);
            this.residuals = copyOf(original.residuals);
            // standardVariance is only built by standardizeData(); objects made with the
            // standardized-data constructor don't have one, which used to throw here
            this.standardVariance = copyOf(original.standardVariance);
            this.prDistribution = copyOf(original.prDistribution);
            this.sphericalDistribution = copyOf(original.sphericalDistribution);
            this.sphericalDistributionFine = copyOf(original.sphericalDistributionFine);
            this.prDistributionForFitting = copyOf(original.prDistributionForFitting);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        coefficients = (original.coefficients == null) ? null : original.coefficients.clone();

        if (original.prior_coefficients instanceof double[] ){ // when switching IFT models, priors may not exist
            prior_coefficients = original.prior_coefficients.clone();
        }



        this.qmax = original.qmax;
        this.dmax = original.dmax;
        this.lambda = original.lambda;
        this.dmax_PI_TWO_INV_PI = dmax * Math.PI * TWO_INV_PI;
        this.PI_INV_DMAX = Math.PI/dmax;
        standardizedMin = original.standardizedMin;
        standardizedScale = original.standardizedScale;
        includeBackground = original.includeBackground;
        this.rg = original.rg;
        this.izero = original.izero;
        this.rAverage =  original.rAverage;
        this.sphericalRg = original.sphericalRg;
        this.rgError = original.rgError;
        this.iZeroError = original.iZeroError;
        this.rAverageError = original.rAverageError;
        this.totalInDistribution = original.totalInDistribution;
        this.totalCoefficients = original.totalCoefficients;
        this.rows = original.rows;
        this.negativeValuesInModel = original.negativeValuesInModel;
        this.description = original.description;
        this.sphericalVolume = original.sphericalVolume;
        this.degreesOfFreedom = original.degreesOfFreedom;
        this.distribution_score = original.distribution_score;
        this.n_chipoints = original.n_chipoints;
        this.aic = original.aic;
        this.prScore = original.prScore;
        this.prRoughness = original.prRoughness;
        this.prNegTail = original.prNegTail;
        this.prNegHead = original.prNegHead;
        this.prOscillation = original.prOscillation;
        this.prEndConcave = original.prEndConcave;
        this.prTailExcess = original.prTailExcess;
        this.prDeadZone = original.prDeadZone;
        this.prReversals = original.prReversals;
        this.prNegDepth = original.prNegDepth;
        this.prNegZ = original.prNegZ;
        this.prNegSignificance = original.prNegSignificance;
        if (original.coefficientCovariance != null) {
            this.coefficientCovariance = new double[original.coefficientCovariance.length][];
            for (int i = 0; i < coefficientCovariance.length; i++) coefficientCovariance[i] = original.coefficientCovariance[i].clone();
        }
        this.prDistributionSigma = (original.prDistributionSigma == null) ? null : original.prDistributionSigma.clone();
        this.effectiveParameters = original.effectiveParameters;
        this.penaltyValue = original.penaltyValue;
        this.logDetNormal = original.logDetNormal;
        this.logDetPrior = original.logDetPrior;
        this.unpenalizedCount = original.unpenalizedCount;
        if (original.prDistributionBasis != null) {
            this.prDistributionBasis = new double[original.prDistributionBasis.length][];
            for (int i = 0; i < prDistributionBasis.length; i++)
                prDistributionBasis[i] = (original.prDistributionBasis[i] == null) ? null : original.prDistributionBasis[i].clone();
        }

        if (totalInDistribution > 2 && prDistribution != null){ // spline needs >= 3 points
            double[] totalrvalues = new double[totalInDistribution];
            double[] totalPrvalues = new double[totalInDistribution];

            // coefficients start at r_1 > 0 and ends at midpoint of last bin which is less dmax
            for(int i=0; i < totalInDistribution; i++){
                XYDataItem item = this.prDistribution.getDataItem(i);
                totalPrvalues[i] = item.getYValue();
                totalrvalues[i] = item.getXValue();
            }

            SplineInterpolator spline = new SplineInterpolator();
            this.splineFunction = spline.interpolate(totalrvalues, totalPrvalues);
        }


        this.alpha = original.alpha;
        this.beta = original.beta;
        this.max_ls_iter = original.max_ls_iter;

        this.bin_width = original.bin_width;
        this.status = original.status;

        this.coeffs_size = original.coeffs_size; // Shannon_number + 1 (if background included)
        this.ns = original.ns;

        this.a_matrix = (original.a_matrix == null) ? null : original.a_matrix.copy();
        this.y_vector = (original.y_vector == null) ? null : original.y_vector.copy();
        this.am_vector = (original.am_vector == null) ? null : original.am_vector.copy();
        this.modelUsed = original.modelUsed;
        this.area = original.area;
        this.priorExists = original.priorExists;
    }





    /**
     * Least squares by Householder QR: minimize ||M x - b|| for M m x n (m >= n).
     * M and b are overwritten. Columns that are numerically rank-deficient get x = 0.
     * Shared by the IFT subclasses that solve quadratic (NONE / L2) problems exactly.
     */
    protected static double[] householderSolve(double[][] M, double[] b, int n) {
        int m = M.length;
        double[] diag = new double[n];
        double maxDiag = 0;
        for (int c = 0; c < n; c++) {
            double norm = 0;
            for (int i = c; i < m; i++) norm += M[i][c]*M[i][c];
            norm = Math.sqrt(norm);
            if (norm == 0) { diag[c] = 0; continue; }
            double alpha = (M[c][c] > 0) ? -norm : norm;
            M[c][c] -= alpha;                                   // v = x - alpha e1, stored in column c
            double vnorm2 = 0;
            for (int i = c; i < m; i++) vnorm2 += M[i][c]*M[i][c];
            for (int j = c + 1; j < n; j++) {
                double s = 0;
                for (int i = c; i < m; i++) s += M[i][c]*M[i][j];
                s = 2*s/vnorm2;
                for (int i = c; i < m; i++) M[i][j] -= s*M[i][c];
            }
            double s = 0;
            for (int i = c; i < m; i++) s += M[i][c]*b[i];
            s = 2*s/vnorm2;
            for (int i = c; i < m; i++) b[i] -= s*M[i][c];
            diag[c] = alpha;
            maxDiag = Math.max(maxDiag, Math.abs(alpha));
        }
        double[] x = new double[n];
        double tiny = 1e-13*maxDiag;
        for (int i = n - 1; i >= 0; i--) {
            if (Math.abs(diag[i]) <= tiny) { x[i] = 0; continue; }
            double s = b[i];
            for (int j = i + 1; j < n; j++) s -= M[i][j]*x[j];
            x[i] = s/diag[i];
        }
        return x;
    }

    /**
     * Exact solver for the convex problems
     *     minimize a'Ga - 2b'a + w * sum_{j >= unpenalized} |a_j|       (nonNegative = false)
     *     minimize a'Ga - 2b'a + w * sum_{j >= unpenalized} a_j,  a_j >= 0 for j >= unpenalized
     * by cyclic coordinate descent. With G = A'WA and b = A'Wy this is chi^2 + L1 penalty (+ const).
     * The first `unpenalized` coefficients (e.g. a background) are free and unpenalized.
     * `a` is the starting point and is overwritten with the solution.
     */
    protected static double[] coordinateDescentL1(double[][] G, double[] b, double w, int unpenalized,
                                                 boolean nonNegative, double[] a, int maxSweeps, double tol) {
        final int k = b.length;
        if (nonNegative) for (int j = unpenalized; j < k; j++) a[j] = Math.max(0, a[j]);
        double[] Ga = new double[k];
        for (int p = 0; p < k; p++) { double s = 0; for (int q = 0; q < k; q++) s += G[p][q]*a[q]; Ga[p] = s; }
        final double half = 0.5*w;
        for (int sweep = 0; sweep < maxSweeps; sweep++) {
            double maxDelta = 0, maxA = 0;
            for (int j = 0; j < k; j++) {
                double gjj = G[j][j];
                if (gjj <= 0) continue;
                double rho = b[j] - (Ga[j] - gjj*a[j]);
                double next;
                if (j < unpenalized) {
                    next = rho/gjj;
                } else if (nonNegative) {
                    next = Math.max(0, rho - half)/gjj;
                } else {
                    next = (rho > half) ? (rho - half)/gjj : (rho < -half) ? (rho + half)/gjj : 0.0;
                }
                double delta = next - a[j];
                if (delta != 0) {
                    for (int p = 0; p < k; p++) Ga[p] += G[p][j]*delta;
                    a[j] = next;
                }
                double sg = Math.sqrt(gjj);
                maxDelta = Math.max(maxDelta, Math.abs(delta)*sg);
                maxA = Math.max(maxA, Math.abs(a[j])*sg);
            }
            if (maxDelta <= tol*Math.max(maxA, 1e-300)) break;
        }
        return a;
    }

    /**
     * Lawson-Hanson active-set solver for
     *     minimize a'Ga - 2b'a   subject to  a_j >= 0 for j >= free
     * (the first `free` coefficients are unconstrained). G must be symmetric positive definite.
     * `warm` (optional) is a starting guess: its positive constrained entries seed the passive set.
     * Entry tests use a per-component round-off tolerance, and a variable that re-enters without
     * progress is blocked until the solution moves (prevents cycling on ill-conditioned G).
     */
    protected static double[] activeSetNNLS(double[][] G, double[] b, int free, int maxIter) {
        return activeSetNNLS(G, b, free, maxIter, null);
    }

    protected static double[] activeSetNNLS(double[][] G, double[] b, int free, int maxIter, double[] warm) {
        final int k = b.length;
        final double eps = 1e-10;
        boolean[] passive = new boolean[k], blocked = new boolean[k];
        for (int j = 0; j < free; j++) passive[j] = true;
        if (warm != null) for (int j = free; j < k; j++) if (warm[j] > 0) passive[j] = true;

        // feasible start: x = 0 on constrained variables; then make the passive set feasible
        double[] x = new double[k];
        lastNNLSIterations = 0;
        x = feasibleSubsetSolve(G, b, free, passive, x);

        for (int iter = 0; iter < maxIter; iter++) {
            lastNNLSIterations = iter + 1;
            int enter = -1;
            double best = 0;
            for (int j = free; j < k; j++) {
                if (passive[j] || blocked[j]) continue;
                double w = b[j], scale = Math.abs(b[j]);
                for (int p = 0; p < k; p++) { double t = G[j][p]*x[p]; w -= t; scale += Math.abs(t); }
                if (w > eps*scale && w > best) { best = w; enter = j; }
            }
            if (enter < 0) break;                                    // KKT satisfied (to round-off)
            passive[enter] = true;
            double[] before = x.clone();
            x = feasibleSubsetSolve(G, b, free, passive, x);
            boolean moved = false;
            for (int j = 0; j < k; j++) if (x[j] != before[j]) { moved = true; break; }
            if (!passive[enter] && !moved) blocked[enter] = true;    // entered and left without progress
            else if (moved) java.util.Arrays.fill(blocked, false);
        }
        return x;
    }

    /** Lawson-Hanson inner loop: solve on the passive set, stepping back to stay feasible. */
    private static double[] feasibleSubsetSolve(double[][] G, double[] b, int free, boolean[] passive, double[] x) {
        final int k = b.length;
        while (true) {
            double[] z = solveSubset(G, b, passive);
            double alpha = 1.0;
            int leave = -1;
            for (int j = free; j < k; j++) {
                if (passive[j] && z[j] <= 0) {
                    double t = (x[j] - z[j] > 0) ? x[j]/(x[j] - z[j]) : 0;
                    if (t < alpha) { alpha = t; leave = j; }
                }
            }
            if (leave < 0) return z;                                 // all passive variables feasible
            for (int j = 0; j < k; j++) x[j] += alpha*(z[j] - x[j]);
            for (int j = free; j < k; j++) if (passive[j] && x[j] <= 0) { passive[j] = false; x[j] = 0; }
            passive[leave] = false;
            x[leave] = 0;
        }
    }

    /** iterations used by the last activeSetNNLS call (diagnostic) */
    static int lastNNLSIterations;

    /** solve G_PP x_P = b_P by Cholesky on the passive set; x = 0 elsewhere */
    private static double[] solveSubset(double[][] G, double[] b, boolean[] passive) {
        int k = b.length, m = 0;
        int[] idx = new int[k];
        for (int j = 0; j < k; j++) if (passive[j]) idx[m++] = j;
        double[][] Lc = new double[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j <= i; j++) {
                double s = G[idx[i]][idx[j]];
                for (int p = 0; p < j; p++) s -= Lc[i][p]*Lc[j][p];
                if (i == j) Lc[i][i] = Math.sqrt(Math.max(s, 1e-300));
                else Lc[i][j] = s/Lc[j][j];
            }
        }
        double[] y = new double[m];
        for (int i = 0; i < m; i++) {
            double s = b[idx[i]];
            for (int p = 0; p < i; p++) s -= Lc[i][p]*y[p];
            y[i] = s/Lc[i][i];
        }
        double[] xs = new double[m];
        for (int i = m - 1; i >= 0; i--) {
            double s = y[i];
            for (int p = i + 1; p < m; p++) s -= Lc[p][i]*xs[p];
            xs[i] = s/Lc[i][i];
        }
        double[] x = new double[k];
        for (int i = 0; i < m; i++) x[idx[i]] = xs[i];
        return x;
    }

    /**
     * Normal matrix G = A'WA (+ weight * R'R) of a weighted, optionally penalized, least-squares fit.
     * design: rows x k; invVar: 1/sigma^2 per row; penalty: rows x k or null.
     */
    protected static double[][] normalMatrix(double[][] design, double[] invVar, double[][] penalty, double weight) {
        int k = design[0].length;
        double[][] G = new double[k][k];
        for (int i = 0; i < design.length; i++) {
            double[] d = design[i];
            double w = invVar[i];
            for (int p = 0; p < k; p++) {
                double wp = w*d[p];
                if (wp == 0) continue;
                for (int q = p; q < k; q++) G[p][q] += wp*d[q];
            }
        }
        if (penalty != null && weight > 0) {
            for (double[] row : penalty)
                for (int p = 0; p < k; p++) {
                    if (row[p] == 0) continue;
                    for (int q = p; q < k; q++) G[p][q] += weight*row[p]*row[q];
                }
        }
        for (int p = 0; p < k; p++) for (int q = 0; q < p; q++) G[p][q] = G[q][p];
        return G;
    }

    /** reduced chi^2 of coefficients a against (design, target, invVar) */
    protected static double reducedChiSquared(double[][] design, double[] target, double[] invVar, double[] a) {
        double chi = 0;
        for (int i = 0; i < design.length; i++) {
            double calc = 0;
            for (int c = 0; c < a.length; c++) calc += design[i][c]*a[c];
            double res = calc - target[i];
            chi += res*res*invVar[i];
        }
        int dof = design.length - a.length;
        return (dof > 0) ? chi/dof : Double.NaN;
    }

    /**
     * Posterior covariance G^-1 * max(1, chi2red). Inflating by chi2red when it exceeds 1 keeps the
     * significance of negative P(r) honest when the errors are underestimated. A tiny ridge is added
     * if G is numerically singular. Returns null if the inverse cannot be formed.
     */
    protected static double[][] posteriorCovariance(double[][] G, double chi2red) {
        int k = G.length;
        double scale = (chi2red > 1 && !Double.isNaN(chi2red)) ? chi2red : 1.0;
        double maxDiag = 0;
        for (int i = 0; i < k; i++) maxDiag = Math.max(maxDiag, G[i][i]);
        for (double ridge : new double[]{0, 1e-12, 1e-9, 1e-6}) {
            double[][] L = new double[k][k];
            boolean ok = true;
            for (int i = 0; i < k && ok; i++) {
                for (int j = 0; j <= i; j++) {
                    double s = G[i][j] + ((i == j) ? ridge*maxDiag : 0);
                    for (int p = 0; p < j; p++) s -= L[i][p]*L[j][p];
                    if (i == j) {
                        if (!(s > 0)) { ok = false; break; }
                        L[i][i] = Math.sqrt(s);
                    } else {
                        L[i][j] = s/L[j][j];
                    }
                }
            }
            if (!ok) continue;
            // inverse of L, then C = L^-T L^-1
            double[][] Li = new double[k][k];
            for (int i = 0; i < k; i++) {
                Li[i][i] = 1.0/L[i][i];
                for (int j = 0; j < i; j++) {
                    double s = 0;
                    for (int p = j; p < i; p++) s -= L[i][p]*Li[p][j];
                    Li[i][j] = s/L[i][i];
                }
            }
            double[][] C = new double[k][k];
            for (int i = 0; i < k; i++)
                for (int j = 0; j <= i; j++) {
                    double s = 0;
                    for (int p = i; p < k; p++) s += Li[p][i]*Li[p][j];
                    C[i][j] = C[j][i] = s*scale;
                }
            return C;
        }
        return null;
    }

    /** fills prDistributionSigma from prDistributionBasis and coefficientCovariance */
    protected void deriveDistributionSigma() {
        prDistributionSigma = null;
        if (prDistributionBasis == null || coefficientCovariance == null) return;
        prDistributionSigma = new double[prDistributionBasis.length];
        for (int i = 0; i < prDistributionBasis.length; i++) {
            if (prDistributionBasis[i] != null) prDistributionSigma[i] = sigmaOfLinear(prDistributionBasis[i]);
        }
    }

    /**
     * Sets coefficientCovariance (G^-1 * max(1, chi2red)), effectiveParameters, and the terms needed
     * for the penalized AIC and the Bayesian evidence. G = A'WA + w R'R is the normal matrix of the
     * fit; design/invVar the fitted rows and 1/sigma^2; penalty/weight the regularization (null/0 for
     * none, e.g. L1 fits, where k_eff is the column count and the evidence is undefined); solution
     * the fitted coefficients.
     */
    protected void setPosterior(double[][] G, double chi2red, double[][] design, double[] invVar,
                                double[][] penalty, double weight, double[] solution) {
        coefficientCovariance = posteriorCovariance(G, chi2red);
        effectiveParameters = Double.NaN;
        penaltyValue = 0;
        logDetNormal = logDetPrior = Double.NaN;
        int k = G.length;
        boolean dataOnly = (penalty == null || weight <= 0);
        unpenalizedCount = k;
        if (!dataOnly) {
            double pv = 0;
            for (double[] row : penalty) {
                double s2 = 0;
                for (int c = 0; c < k; c++) s2 += row[c]*solution[c];
                pv += s2*s2;
            }
            penaltyValue = weight*pv;
            logDetNormal = logDetStacked(design, invVar, penalty, weight);
            // prior precision restricted to the penalized columns
            boolean[] pen = new boolean[k];
            for (double[] row : penalty) for (int c = 0; c < k; c++) if (row[c] != 0) pen[c] = true;
            int m = 0;
            int[] idx = new int[k];
            for (int c = 0; c < k; c++) if (pen[c]) idx[m++] = c;
            unpenalizedCount = k - m;
            // ln det(w R_p'R_p) by QR of sqrt(w) R_p (backward stable; R_p'R_p can be far worse
            // conditioned than R_p itself, e.g. for the Legendre basis)
            double[][] Rp = new double[penalty.length][m];
            for (int i = 0; i < penalty.length; i++)
                for (int a = 0; a < m; a++) Rp[i][a] = penalty[i][idx[a]];
            double[] wv = new double[penalty.length];
            java.util.Arrays.fill(wv, weight);
            logDetPrior = (penalty.length >= m) ? logDetStacked(Rp, wv, null, 0) : Double.NaN;
        }
        if (coefficientCovariance == null) return;
        if (dataOnly) { effectiveParameters = k; return; }
        double scale = (chi2red > 1 && !Double.isNaN(chi2red)) ? chi2red : 1.0;
        double tr = 0;                                   // sum_i w_i d_i' G^-1 d_i
        double[] tmp = new double[k];
        for (int i = 0; i < design.length; i++) {
            double[] d = design[i];
            for (int p = 0; p < k; p++) {
                double s2 = 0;
                for (int q = 0; q < k; q++) s2 += coefficientCovariance[p][q]*d[q];
                tmp[p] = s2;
            }
            double v = 0;
            for (int p = 0; p < k; p++) v += d[p]*tmp[p];
            tr += invVar[i]*v/scale;
        }
        // the trace cannot exceed the column count; values above it are round-off from an
        // ill-conditioned G at very small penalty weights
        effectiveParameters = Math.max(0, Math.min(tr, k));
    }

    /**
     * ln det(A'WA + w R'R) from a Householder QR of the stacked system [sqrt(W) A ; sqrt(w) R]
     * (2 sum ln|R_ii|). Backward stable, unlike a Cholesky of the formed normal matrix, whose
     * condition number is squared. NaN if rank deficient.
     */
    protected static double logDetStacked(double[][] design, double[] invVar, double[][] penalty, double weight) {
        int k = design[0].length, m = design.length + ((penalty != null && weight > 0) ? penalty.length : 0);
        double[][] M = new double[m][k];
        for (int i = 0; i < design.length; i++) {
            double s = Math.sqrt(invVar[i]);
            for (int c = 0; c < k; c++) M[i][c] = s*design[i][c];
        }
        if (m > design.length) {
            double sw = Math.sqrt(weight);
            for (int j = 0; j < penalty.length; j++)
                for (int c = 0; c < k; c++) M[design.length + j][c] = sw*penalty[j][c];
        }
        double ld = 0;
        for (int c = 0; c < k; c++) {
            double norm = 0;
            for (int i = c; i < m; i++) norm += M[i][c]*M[i][c];
            norm = Math.sqrt(norm);
            if (!(norm > 0)) return Double.NaN;
            double alpha = (M[c][c] > 0) ? -norm : norm;
            M[c][c] -= alpha;
            double vnorm2 = 0;
            for (int i = c; i < m; i++) vnorm2 += M[i][c]*M[i][c];
            if (vnorm2 > 0) {
                for (int j = c + 1; j < k; j++) {
                    double s = 0;
                    for (int i = c; i < m; i++) s += M[i][c]*M[i][j];
                    s = 2*s/vnorm2;
                    for (int i = c; i < m; i++) M[i][j] -= s*M[i][c];
                }
            }
            ld += 2*Math.log(Math.abs(alpha));
        }
        return ld;
    }

    /**
     * ln det of a symmetric positive-definite matrix by Cholesky after equilibration
     * (D A D with D = diag(A)^-1/2), which keeps badly scaled columns from losing precision.
     * NaN if not positive definite.
     */
    protected static double logDetSPD(double[][] A) {
        int n = A.length;
        double[] dsc = new double[n];
        double ld = 0;
        for (int i = 0; i < n; i++) {
            if (!(A[i][i] > 0)) return Double.NaN;
            dsc[i] = 1.0/Math.sqrt(A[i][i]);
            ld += Math.log(A[i][i]);                      // -2 ln det D
        }
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double s = A[i][j]*dsc[i]*dsc[j];
                for (int p = 0; p < j; p++) s -= L[i][p]*L[j][p];
                if (i == j) {
                    if (!(s > 0)) return Double.NaN;
                    L[i][i] = Math.sqrt(s);
                    ld += 2*Math.log(L[i][i]);
                } else {
                    L[i][j] = s/L[j][j];
                }
            }
        }
        return ld;
    }

    /**
     * Penalized AIC: (chi^2 + w|Ra|^2)/cHat + 2 k_eff. The penalty value is the cost of the
     * expectation imposed by the regularizer: negligible at small lambda, growing with lambda.
     */
    public double getPenalizedAIC(double cHat) {
        double c = (cHat > 1) ? cHat : 1.0;
        double k = Double.isNaN(effectiveParameters) ? ns + 2 : effectiveParameters;
        return (getChiSquaredAllPoints() + penaltyValue)/c + 2.0*k;
    }

    /**
     * -2 ln(Bayesian evidence) for the regularized fit (lower is better), up to a constant shared by
     * every fit of the same data:
     *     (chi^2 + w|Ra|^2)/cHat + ln det(G) - ln det(w R_p'R_p) - u ln(cHat)
     * with G = A'WA + wR'R and u the number of unpenalized coefficients. The determinant terms are
     * the Occam factor: they penalize too weak a prior as the penalty value penalizes too strong a
     * one. Comparable across lambda and across dmax. NaN for unregularized or L1 fits.
     */
    public double getMinusTwoLogEvidence(double cHat) {
        if (Double.isNaN(logDetNormal) || Double.isNaN(logDetPrior)) return Double.NaN;
        double c = (cHat > 1) ? cHat : 1.0;
        return (getChiSquaredAllPoints() + penaltyValue)/c + logDetNormal - logDetPrior - unpenalizedCount*Math.log(c);
    }

    /** true when the fit produced a solution (constructors catch solver errors and leave none) */
    public boolean isSolved() {
        return am_vector != null && a_matrix != null && y_vector != null;
    }

    /**
     * Regularization choice by maximum evidence (see getMinusTwoLogEvidence); falls back to
     * AIC_eff for fits without a quadratic prior.
     */
    public static <T extends IndirectFT> T selectLambdaByEvidence(java.util.function.DoubleFunction<T> fitAt,
                                                                 double[] lambdas, double cHat) {
        T best = null;
        double bestValue = Double.POSITIVE_INFINITY;
        T last = null;
        for (double lam : lambdas) {
            T f = fitAt.apply(lam);
            last = f;
            if (!f.isSolved()) continue;                       // solver failed at this lambda
            double v = f.getMinusTwoLogEvidence(cHat);
            if (Double.isNaN(v)) v = f.getAICEffective(cHat);
            if (v < bestValue) { bestValue = v; best = f; }
        }
        return (best != null) ? best : last;                    // caller checks isSolved()
    }

    /**
     * AIC with the effective number of parameters: chi^2/cHat + 2 k_eff. Unlike getAICAllPoints,
     * which charges a fixed ns + 2, this responds to the regularization weight, so it can be
     * compared across lambda as well as dmax. cHat as in getAICAllPoints(double).
     */
    public double getAICEffective(double cHat) {
        double c = (cHat > 1) ? cHat : 1.0;
        double k = Double.isNaN(effectiveParameters) ? ns + 2 : effectiveParameters;
        return getChiSquaredAllPoints()/c + 2.0*k;
    }

    /** log-spaced grid from lo to hi with `perDecade` points per decade (inclusive) */
    public static double[] logGrid(double lo, double hi, int perDecade) {
        int n = (int) Math.round(Math.log10(hi/lo)*perDecade) + 1;
        double[] g = new double[n];
        for (int i = 0; i < n; i++) g[i] = lo*Math.pow(10, (double) i/perDecade);
        return g;
    }

    /**
     * Regularization choice at fixed dmax: fits each lambda and returns the fit with the lowest
     * AIC_eff (chi^2/cHat + 2 k_eff). The P(r) score is deliberately not minimized over lambda: its
     * smoothness terms duplicate the regularizer and push toward over-smoothing; compare the
     * returned fits across dmax with getAICEffective(cHat) + getPrScore().
     *
     * Usage:  IndirectFT best = IndirectFT.selectLambda(lam -> new LegendreTransform(d, e, dmax,
     *             qmax, lam, min, scale, true), IndirectFT.logGrid(1e-6, 10, 2), cHat);
     */
    public static <T extends IndirectFT> T selectLambda(java.util.function.DoubleFunction<T> fitAt,
                                                       double[] lambdas, double cHat) {
        T best = null;
        double bestValue = Double.POSITIVE_INFINITY;
        T last = null;
        for (double lam : lambdas) {
            T f = fitAt.apply(lam);
            last = f;
            if (!f.isSolved()) continue;
            double v = f.getAICEffective(cHat);
            if (v < bestValue) { bestValue = v; best = f; }
        }
        return (best != null) ? best : last;
    }

    /** generalized cross-validation: N chi^2 / (N - k_eff)^2 (lower is better) */
    public double getGCV() {
        double chi = getChiSquaredAllPoints();
        int N = residuals.getItemCount();
        double k = Double.isNaN(effectiveParameters) ? ns + 2 : effectiveParameters;
        return N*chi/((N - k)*(N - k));
    }

    /** sd of the linear combination b'a under coefficientCovariance (NaN if unknown) */
    protected double sigmaOfLinear(double[] b) {
        if (coefficientCovariance == null) return Double.NaN;
        int k = b.length;
        double v = 0;
        for (int i = 0; i < k; i++) {
            if (b[i] == 0) continue;
            double s = 0;
            for (int j = 0; j < k; j++) s += coefficientCovariance[i][j]*b[j];
            v += b[i]*s;
        }
        return (v > 0) ? Math.sqrt(v) : 0;
    }

    /** sub-intervals per Shannon bin for the bin-average integration (composite Simpson, even) */
    public static int SHANNON_BIN_SUBDIVISIONS = 64;

    /**
     * Shannon-binned P(r) (prDistributionForFitting, used for modelling). Bins have width
     * bin_width = B = pi/qmax: bin k covers [kB, (k+1)B) and is reported at its mid-point (k + 1/2)B.
     * Every bin overlapping (0, dmax) is included, i.e. ceil(dmax/B) bins, and each value is the bin
     * average
     *     h_k = (1/B) * integral over the bin of P(r) dr,   with P(r) = 0 beyond dmax,
     * so the bin containing dmax holds its (partial) content rather than P(dmax) = 0, and
     * sum_k h_k B equals the area of P(r). P(r) is calculatePofRAtR(r, 1), the same units for every
     * transform. Call once the solution (and any spline used by calculatePofRAtR) is final.
     */
    protected void setShannonBinnedDistribution() {
        bin_width = Math.PI/qmax;
        final double B = bin_width;
        final int nb = (int) Math.ceil(dmax/B - 1e-12);
        int m = SHANNON_BIN_SUBDIVISIONS;
        if (m < 2) m = 2;
        if ((m & 1) == 1) m++;
        prDistributionForFitting = new XYSeries("OutputPrDistribution");
        for (int k = 0; k < nb; k++) {
            double lo = k*B, hi = Math.min((k + 1)*B, dmax);      // never evaluate beyond dmax
            double h = (hi - lo)/m, sum = 0;
            for (int j = 0; j <= m; j++) {
                double r = lo + j*h;
                double w = (j == 0 || j == m) ? 1 : ((j & 1) == 1 ? 4 : 2);
                sum += w*pOfRWithinSupport(r);
            }
            double integral = sum*h/3.0;
            prDistributionForFitting.add((k + 0.5)*B, integral/B);
        }
    }

    /**
     * P(r) in calculatePofRAtR units, 0 at and outside the support [0, dmax]. If calculatePofRAtR
     * fails (e.g. a spline whose knots stop short of dmax throws OutOfRangeException), falls back to
     * linear interpolation of prDistribution scaled to the same units, so no bin is ever dropped.
     */
    private double pOfRWithinSupport(double r) {
        if (r <= 0 || r >= dmax) return 0;
        try {
            double v = calculatePofRAtR(r, 1.0);
            if (!Double.isNaN(v) && !Double.isInfinite(v)) return v;
        } catch (RuntimeException ignored) {
            // fall through
        }
        return interpolatedPlottingValue(r)*plottingToPofRScale();
    }

    /** linear interpolation of prDistribution at r (0 outside its range) */
    private double interpolatedPlottingValue(double r) {
        int n = (prDistribution == null) ? 0 : prDistribution.getItemCount();
        if (n == 0) return 0;
        double x0 = prDistribution.getX(0).doubleValue();
        if (r <= x0) return 0;
        for (int i = 1; i < n; i++) {
            double x1 = prDistribution.getX(i).doubleValue();
            if (r <= x1) {
                double y0 = prDistribution.getY(i - 1).doubleValue(), y1 = prDistribution.getY(i).doubleValue();
                return (x1 > x0) ? y0 + (y1 - y0)*(r - x0)/(x1 - x0) : y1;
            }
            x0 = x1;
        }
        return 0;
    }

    /**
     * factor converting prDistribution values to calculatePofRAtR(r, 1) units, estimated at the
     * prDistribution point of largest |value| (1 if it cannot be determined)
     */
    protected double plottingToPofRScale() {
        int n = (prDistribution == null) ? 0 : prDistribution.getItemCount();
        int best = -1;
        double big = 0;
        for (int i = 1; i < n - 1; i++) {
            double y = Math.abs(prDistribution.getY(i).doubleValue());
            if (y > big) { big = y; best = i; }
        }
        if (best < 0) return 1.0;
        try {
            double v = calculatePofRAtR(prDistribution.getX(best).doubleValue(), 1.0);
            double y = prDistribution.getY(best).doubleValue();
            return (y != 0 && !Double.isNaN(v)) ? v/y : 1.0;
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    /* =====================================================================================
     *  SAXS invariants from the transform (after Grant 2022, J. Appl. Cryst. 55, 1116, which
     *  derives them for the Moore coefficients). Every transform here models the particle's
     *  scattering as weighted sincs,  I(q) = sum_i w_i sin(q r_i)/(q r_i),  so
     *      I(0)        = sum w_i
     *      Q(L)        = int_0^L q^2 I dq = sum w_i [sin(L r_i) - L r_i cos(L r_i)] / r_i^3
     *      int q I dq  = sum w_i / r_i^2           (to infinity; = 4 pi int P/r^2 dr)
     *      V_p         = 2 pi^2 I(0) / Q           (Porod volume, A^3; the intensity scale cancels)
     *      V_c         = I(0) / int q I dq         (volume of correlation, A^2)
     *      l_c         = V_p / (2 pi V_c)          (correlation length, A)
     *  Background and the standardization offset are excluded (particle scattering only).
     *  Uncertainties are propagated from coefficientCovariance (delta method for ratios).
     * ===================================================================================== */

    /** particle scattering as weighted sincs: r_i, w_i (I(q) units) and d w_i / d coefficient (may be null) */
    protected static final class SincModel {
        public final double[] r, w;
        public final double[][] dw;
        public SincModel(double[] r, double[] w, double[][] dw) { this.r = r; this.w = w; this.dw = dw; }
    }

    /** points for the default (quadrature) representation */
    public static int INVARIANT_QUADRATURE_POINTS = 2000;

    /**
     * Default representation by midpoint quadrature of I(q) = 4 pi int P(r) sin(qr)/(qr) dr using
     * calculatePofRAtR(r, 1); no gradient, so no uncertainties. Subclasses override with their exact
     * model (histogram heights, or the Moore series with its gradient).
     */
    protected SincModel sincModel() {
        int m = INVARIANT_QUADRATURE_POINTS;
        double h = dmax/m;
        double[] r = new double[m], w = new double[m];
        for (int i = 0; i < m; i++) {
            r[i] = (i + 0.5)*h;
            double p;
            try { p = calculatePofRAtR(r[i], 1.0); } catch (RuntimeException e) { p = 0; }
            w[i] = 4*Math.PI*p*h;
        }
        return new SincModel(r, w, null);
    }

    /** helper for histogram-type subclasses: w_i = scale * height_i, gradient from prDistributionBasis */
    protected SincModel histogramSincModel(double[] rValues, double[] heights) {
        int n = rValues.length;
        double[] w = new double[n];
        double[][] dw = (prDistributionBasis != null && prDistributionBasis.length >= n + 1) ? new double[n][] : null;
        for (int i = 0; i < n; i++) {
            w[i] = standardizedScale*heights[i];
            if (dw != null) {
                double[] b = prDistributionBasis[i + 1];
                if (b == null) { dw = null; continue; }
                dw[i] = new double[b.length];
                for (int c = 0; c < b.length; c++) dw[i][c] = standardizedScale*b[c];
            }
        }
        return new SincModel(rValues.clone(), w, dw);
    }

    /** a value and its standard deviation (NaN when no covariance is available) */
    public static final class Estimate {
        public final double value, sigma;
        Estimate(double value, double sigma) { this.value = value; this.sigma = sigma; }
        @Override public String toString() { return String.format("%.6g +/- %.3g", value, sigma); }
    }

    /** coefficients of the linear functionals {I(0), Q(L), int q I dq} in terms of w */
    private static double[] porodKernel(double[] r, double L) {
        double[] k = new double[r.length];
        for (int i = 0; i < r.length; i++) {
            double x = L*r[i];
            k[i] = (Math.sin(x) - x*Math.cos(x))/(r[i]*r[i]*r[i]);
        }
        return k;
    }

    private static double dot(double[] a, double[] b) { double s = 0; for (int i = 0; i < a.length; i++) s += a[i]*b[i]; return s; }

    /** gradient (w.r.t. coefficients) of sum_i k_i w_i */
    private static double[] gradientOf(SincModel m, double[] k) {
        if (m.dw == null) return null;
        int nc = m.dw[0].length;
        double[] g = new double[nc];
        for (int i = 0; i < k.length; i++) for (int c = 0; c < nc; c++) g[c] += k[i]*m.dw[i][c];
        return g;
    }

    private double covarianceOf(double[] ga, double[] gb) {
        if (ga == null || gb == null || coefficientCovariance == null || coefficientCovariance.length != ga.length) return Double.NaN;
        double s = 0;
        for (int i = 0; i < ga.length; i++) {
            if (ga[i] == 0) continue;
            double t = 0;
            for (int j = 0; j < gb.length; j++) t += coefficientCovariance[i][j]*gb[j];
            s += ga[i]*t;
        }
        return s;
    }

    /** ratio a/b with delta-method uncertainty */
    private Estimate ratio(double a, double[] ga, double b, double[] gb, double factor) {
        double v = factor*a/b;
        double va = covarianceOf(ga, ga), vb = covarianceOf(gb, gb), cab = covarianceOf(ga, gb);
        double rel2 = va/(a*a) + vb/(b*b) - 2*cab/(a*b);
        return new Estimate(v, (rel2 >= 0) ? Math.abs(v)*Math.sqrt(rel2) : Double.NaN);
    }

    private static double[] ones(int n) { double[] o = new double[n]; java.util.Arrays.fill(o, 1.0); return o; }

    /** forward scattering of the particle, sum w_i (background excluded) */
    public Estimate getIzeroParticle() {
        SincModel m = sincModel();
        double[] k = ones(m.r.length);
        double v = dot(k, m.w);
        double[] g = gradientOf(m, k);
        double var = covarianceOf(g, g);
        return new Estimate(v, Double.isNaN(var) ? Double.NaN : Math.sqrt(Math.max(var, 0)));
    }

    /**
     * Porod invariant Q = int_0^L q^2 I(q) dq of the model. L = Double.POSITIVE_INFINITY uses the
     * analytic infinite-q value where the model has one (porodInvariantInfinite; Moore series, Grant
     * eq. 12); histogram models have none (NaN).
     */
    public Estimate getPorodInvariant(double qLimit) {
        if (Double.isInfinite(qLimit)) return porodInvariantInfinite();
        SincModel m = sincModel();
        double[] k = porodKernel(m.r, qLimit);
        double v = dot(k, m.w);
        double[] g = gradientOf(m, k);
        double var = covarianceOf(g, g);
        return new Estimate(v, Double.isNaN(var) ? Double.NaN : Math.sqrt(Math.max(var, 0)));
    }

    /** analytic Q to infinite q; NaN unless a subclass provides it */
    protected Estimate porodInvariantInfinite() { return new Estimate(Double.NaN, Double.NaN); }

    /**
     * Porod volume V_p = 2 pi^2 I(0) / Q(L), in A^3 (the intensity scale cancels). Q is integrated to
     * qLimit; use getPorodVolume() for the default cut-off min(qmax, 8/Rg) (Grant 2022; Manalastas-
     * Cantos et al. 2021), or POSITIVE_INFINITY for the analytic value where available.
     */
    public Estimate getPorodVolume(double qLimit) {
        SincModel m = sincModel();
        double[] k0 = ones(m.r.length);
        double a = dot(k0, m.w);
        double[] ga = gradientOf(m, k0);
        double b;
        double[] gb;
        if (Double.isInfinite(qLimit)) {
            Estimate qi = porodInvariantInfinite();
            if (Double.isNaN(qi.value)) return new Estimate(Double.NaN, Double.NaN);
            b = qi.value;
            gb = porodInvariantInfiniteGradient();
        } else {
            double[] kq = porodKernel(m.r, qLimit);
            b = dot(kq, m.w);
            gb = gradientOf(m, kq);
        }
        return ratio(a, ga, b, gb, 2*Math.PI*Math.PI);
    }

    /** gradient of the analytic infinite-q invariant (null unless a subclass provides it) */
    protected double[] porodInvariantInfiniteGradient() { return null; }

    /** default Porod-volume cut-off: min(qmax, 8/Rg) */
    public double defaultPorodCutoff() {
        double g = (rg > 0 && !Double.isNaN(rg)) ? 8.0/rg : qmax;
        return Math.min(qmax, g);
    }

    /** Porod volume with the default cut-off min(qmax, 8/Rg) */
    public Estimate getPorodVolume() { return getPorodVolume(defaultPorodCutoff()); }

    /** volume of correlation V_c = I(0) / int_0^inf q I dq = sum w / sum (w/r^2), in A^2 */
    public Estimate getVolumeOfCorrelation() {
        SincModel m = sincModel();
        double[] k0 = ones(m.r.length), k1 = new double[m.r.length];
        for (int i = 0; i < k1.length; i++) k1[i] = 1.0/(m.r[i]*m.r[i]);
        return ratio(dot(k0, m.w), gradientOf(m, k0), dot(k1, m.w), gradientOf(m, k1), 1.0);
    }

    /* ---------------------------------------------------------------------------------
     *  Volume from V_c (no Porod invariant): Q_R = V_c^2 / Rg -> MW -> V, and P(r) normalized to V
     *  (Rambo & Tainer 2013, Nature 496, 477). With the model I(q) = sum w_i sinc(q r_i):
     *     A = sum w_i,  B = sum w_i / r_i^2,  C = sum w_i r_i^2
     *     V_c = A / B,  Rg^2 = C / (2A),  Q_R = V_c^2 / Rg = sqrt(2) A^(5/2) / (B^2 sqrt(C))
     *  All are sums over the fitted coefficients: closed form, cheap, no q-integration, and the
     *  uncertainty comes from one gradient, so the correlation of V_c and Rg is included.
     * --------------------------------------------------------------------------------- */

    /** Q_R -> MW calibration, MW = (Q_R / c)^k with V_c in A^2, Rg in A, MW in Da */
    public enum SampleType {
        PROTEIN(0.1231, 1.0), RNA(0.00934, 0.808);
        public final double c, k;
        SampleType(double c, double k) { this.c = c; this.k = k; }
    }

    /** sample type for the Q_R calibration */
    public static SampleType SAMPLE_TYPE = SampleType.PROTEIN;
    /** A^3 per Da converting MW to the normalization volume (1.6: hydrated, matches V_p = 1.6 MW) */
    public static double VOLUME_PER_DALTON = 1.6;

    /** Q_R = V_c^2 / Rg (A^3), both from this fit's model */
    public Estimate getQR() {
        SincModel m = sincModel();
        int n = m.r.length;
        double[] kA = ones(n), kB = new double[n], kC = new double[n];
        for (int i = 0; i < n; i++) { double r2 = m.r[i]*m.r[i]; kB[i] = 1.0/r2; kC[i] = r2; }
        double A = dot(kA, m.w), B = dot(kB, m.w), C = dot(kC, m.w);
        if (!(A > 0 && B > 0 && C > 0)) return new Estimate(Double.NaN, Double.NaN);
        double qr = Math.sqrt(2.0)*Math.pow(A, 2.5)/(B*B*Math.sqrt(C));
        // d ln Q_R = 2.5 dA/A - 2 dB/B - 0.5 dC/C
        double[] gA = gradientOf(m, kA), gB = gradientOf(m, kB), gC = gradientOf(m, kC);
        if (gA == null || gB == null || gC == null) return new Estimate(qr, Double.NaN);
        double[] g = new double[gA.length];
        for (int j = 0; j < g.length; j++) g[j] = qr*(2.5*gA[j]/A - 2.0*gB[j]/B - 0.5*gC[j]/C);
        double var = covarianceOf(g, g);
        return new Estimate(qr, var >= 0 ? Math.sqrt(var) : Double.NaN);
    }

    /** MW from Q_R, (Q_R / c)^k in Da, for SAMPLE_TYPE */
    public Estimate getMolecularWeightQR() {
        Estimate qr = getQR();
        double c = SAMPLE_TYPE.c, k = SAMPLE_TYPE.k;
        double mw = Math.pow(qr.value/c, k);
        return new Estimate(mw, Double.isNaN(qr.sigma) ? Double.NaN : mw*k*qr.sigma/qr.value);
    }

    /** particle volume from V_c: VOLUME_PER_DALTON x MW(Q_R), A^3 (no Porod invariant) */
    public Estimate getVolumeFromVc() {
        Estimate mw = getMolecularWeightQR();
        return new Estimate(VOLUME_PER_DALTON*mw.value, VOLUME_PER_DALTON*mw.sigma);
    }

    /**
     * P(r) normalized to the particle volume V = getVolumeFromVc(): P(r) in A^2 with integral P dr = V (A^3),
     * sampled at the r values of prDistribution (drop-in replacement for getPrDistribution() when plotting).
     * <p>
     * The values come from calculatePofRAtR, not from prDistribution itself, because prDistribution is held in
     * class-specific units (Moore: P(r); histogram classes: bin heights with their own 1/(4 pi del_r)), which is
     * why plots of getPrDistribution() change scale when the transform method changes. The curve's area is
     * integrated numerically, so the result is exact for every transform whatever its internal units.
     */
    public XYSeries getPrDistributionNormalizedToVolume() {
        XYSeries out = new XYSeries("PrDistributionNormalizedToVolume");
        double f = getVolumeNormalizationFactor();
        if (prDistribution == null || Double.isNaN(f)) return out;
        for (int i = 0; i < prDistribution.getItemCount(); i++) {
            double r = prDistribution.getX(i).doubleValue();
            out.add(r, f*pofrOrZero(r));
        }
        return out;
    }

    /** the same volume-normalized P(r) on a uniform grid of points+1 values from 0 to dmax (smooth curve for plots) */
    public XYSeries getPrDistributionNormalizedToVolume(int points) {
        XYSeries out = new XYSeries("PrDistributionNormalizedToVolume");
        double f = getVolumeNormalizationFactor();
        if (Double.isNaN(f) || points < 2) return out;
        for (int i = 0; i <= points; i++) {
            double r = dmax*i/points;
            out.add(r, f*pofrOrZero(r));
        }
        return out;
    }

    /** the Shannon bins (prDistributionForFitting) scaled so that sum h_k * bin_width = V */
    public XYSeries getShannonBinsNormalizedToVolume() {
        return scaledToArea(prDistributionForFitting, getVolumeFromVc().value, true, "ShannonBinsNormalizedToVolume");
    }

    /** factor that turns this fit's P(r) (calculatePofRAtR(r, 1) units) into the volume-normalized P(r) */
    public double getVolumeNormalizationFactor() {
        double area = getPofRArea();
        double v = getVolumeFromVc().value;
        return (area > 0 && !Double.isNaN(v)) ? v/area : Double.NaN;
    }

    /** integral of calculatePofRAtR(r, 1) over [0, dmax] (midpoint rule, 2000 panels) */
    public double getPofRArea() {
        int n = 2000;
        double h = dmax/n, a = 0;
        for (int i = 0; i < n; i++) a += pofrOrZero((i + 0.5)*h);
        return a*h;
    }

    /** calculatePofRAtR(r, 1), 0 outside (0, dmax) or where the transform cannot evaluate it */
    private double pofrOrZero(double r) {
        if (!(r > 0 && r < dmax)) return 0;
        try {
            double v = calculatePofRAtR(r, 1.0);
            return Double.isNaN(v) || Double.isInfinite(v) ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** copy of s scaled to the given area (histogram: sum y * bin_width; curve: trapezoid) */
    private XYSeries scaledToArea(XYSeries s, double area, boolean histogram, String key) {
        XYSeries out = new XYSeries(key);
        if (s == null || s.getItemCount() == 0 || Double.isNaN(area)) return out;
        double a = 0;
        if (histogram) {
            for (int i = 0; i < s.getItemCount(); i++) a += s.getY(i).doubleValue()*bin_width;
        } else {
            for (int i = 1; i < s.getItemCount(); i++) {
                a += 0.5*(s.getY(i).doubleValue() + s.getY(i - 1).doubleValue())*(s.getX(i).doubleValue() - s.getX(i - 1).doubleValue());
            }
        }
        if (a == 0) return out;
        double f = area/a;
        for (int i = 0; i < s.getItemCount(); i++) out.add(s.getX(i).doubleValue(), s.getY(i).doubleValue()*f);
        return out;
    }

    /** correlation length l_c = V_p / (2 pi V_c), in A (Porod volume with the given cut-off) */
    public Estimate getCorrelationLength(double qLimit) {
        Estimate vp = getPorodVolume(qLimit), vc = getVolumeOfCorrelation();
        double v = vp.value/(2*Math.PI*vc.value);
        double rel = Math.sqrt(Math.pow(vp.sigma/vp.value, 2) + Math.pow(vc.sigma/vc.value, 2));   // ignores their covariance
        return new Estimate(v, Math.abs(v)*rel);
    }

    /* ---------------------------------------------------------------------------------
     *  Porod volume by Grant's method (Grant 2022, sections 2.4.2 and 2.4.4)
     * --------------------------------------------------------------------------------- */

    /** builds a transform of the same kind for the given data, errors and regularization weight */
    public interface Refit<T extends IndirectFT> {
        T fit(XYSeries data, XYSeries errors, double lambda);
    }

    /** result of grantPorodVolume */
    public static final class GrantPorodVolume {
        public final Estimate volume, izero, porodInvariant;
        public final double qCutoff, lambdaOptimal, lambdaOversmoothed, chi2Best, chi2AtOptimal;
        public final IndirectFT oversmoothedFit;
        GrantPorodVolume(Estimate v, Estimate i0, Estimate q, double qc, double lo, double lv, double cb, double co, IndirectFT f) {
            volume = v; izero = i0; porodInvariant = q; qCutoff = qc; lambdaOptimal = lo; lambdaOversmoothed = lv;
            chi2Best = cb; chi2AtOptimal = co; oversmoothedFit = f;
        }
        @Override public String toString() {
            return String.format("Vp %s (I0 %s, Q %s, q <= %.4f, lambda_opt %.3g, lambda_used %.3g)",
                    volume, izero, porodInvariant, qCutoff, lambdaOptimal, lambdaOversmoothed);
        }
    }

    /** copy of a series keeping points with x <= xMax */
    public static XYSeries truncateSeries(XYSeries s, double xMax) {
        XYSeries out = new XYSeries(s.getKey());
        for (int i = 0; i < s.getItemCount(); i++) {
            if (s.getX(i).doubleValue() <= xMax) out.add(s.getDataItem(i));
        }
        return out;
    }

    /** multiplier applied to the chi^2-optimal lambda for the volume fit (Grant: 10) */
    public static double GRANT_OVERSMOOTH_FACTOR = 10.0;
    /** chi^2 rise defining the optimal lambda (Grant: 10%, chi^2 = 1.1 chi^2_best) */
    public static double GRANT_CHI2_RISE = 1.1;
    /** q cut-off in units of 1/Rg for the volume fit (Grant, Manalastas-Cantos et al.: 8) */
    public static double GRANT_QRG_LIMIT = 8.0;

    /**
     * Porod volume by Grant's method:
     *  1. restrict the data to q <= 8/Rg (Rg from the base fit);
     *  2. chi^2_best = fit at lambda = 0;
     *  3. lambda_opt = where chi^2 rises to 1.1 chi^2_best (log-interpolated on lambdaGrid);
     *  4. refit at 10 x lambda_opt (oversmoothed: suppresses shape scattering, enforces Porod decay);
     *  5. Q from that fit, integrated to the cut-off; V_p = 2 pi^2 I(0) / Q.
     * I(0) is NOT taken from the oversmoothed fit (oversmoothing moves it 2-3% off the data): it is
     * izeroOverride when given (e.g. Guinier I(0), in the units of I(q), as returned by getIzeroParticle),
     * otherwise the base fit's particle I(0) (background excluded), so it stays on the experimental data.
     * Use a no-background base fit: the q*I constant term inflates V_p by 12-15%.
     *
     * @param base          the normal fit (dmax, Rg, I(0))
     * @param data, errors  the series passed to the transform constructors (standardized q*I(q) form)
     * @param refit         builds the same transform for (data, errors, lambda), same dmax
     * @param lambdaGrid    increasing lambdas to scan (same meaning as the transform's lambda)
     * @param izeroOverride I(0) to use, or NaN for the base fit's particle I(0)
     */
    public static GrantPorodVolume grantPorodVolume(IndirectFT base, XYSeries data, XYSeries errors,
                                                    Refit<? extends IndirectFT> refit, double[] lambdaGrid,
                                                    double izeroOverride) {
        double rgBase = base.getRg();
        double qc = Math.min(data.getMaxX(), GRANT_QRG_LIMIT/rgBase);
        XYSeries dT = truncateSeries(data, qc), eT = truncateSeries(errors, qc);

        double chi2Best = refit.fit(dT, eT, 0.0).getChiSquaredAllPoints();
        double target = GRANT_CHI2_RISE*chi2Best;

        double lamOpt = Double.NaN, prevLam = Double.NaN, prevChi = chi2Best;
        for (double lam : lambdaGrid) {
            if (lam <= 0) continue;
            double chi = refit.fit(dT, eT, lam).getChiSquaredAllPoints();
            if (chi >= target) {
                if (Double.isNaN(prevLam)) { lamOpt = lam; }
                else {                                      // interpolate in log(lambda)
                    double t = (target - prevChi)/(chi - prevChi);
                    lamOpt = Math.exp(Math.log(prevLam) + t*(Math.log(lam) - Math.log(prevLam)));
                }
                break;
            }
            prevLam = lam;
            prevChi = chi;
        }
        if (Double.isNaN(lamOpt)) lamOpt = lambdaGrid[lambdaGrid.length - 1];   // never reached: use the largest

        double lamUsed = GRANT_OVERSMOOTH_FACTOR*lamOpt;
        IndirectFT over = refit.fit(dT, eT, lamUsed);
        double chiOpt = refit.fit(dT, eT, lamOpt).getChiSquaredAllPoints();

        Estimate q = over.getPorodInvariant(qc);
        Estimate i0 = Double.isNaN(izeroOverride) ? base.getIzeroParticle() : new Estimate(izeroOverride, Double.NaN);
        double v = 2*Math.PI*Math.PI*i0.value/q.value;
        double rel2 = 0;
        boolean known = false;
        if (!Double.isNaN(i0.sigma)) { rel2 += Math.pow(i0.sigma/i0.value, 2); known = true; }
        if (!Double.isNaN(q.sigma)) { rel2 += Math.pow(q.sigma/q.value, 2); known = true; }
        Estimate vp = new Estimate(v, known ? Math.abs(v)*Math.sqrt(rel2) : Double.NaN);
        return new GrantPorodVolume(vp, i0, q, qc, lamOpt, lamUsed, chi2Best, chiOpt, over);
    }

    /** null-safe full copy of a series */
    private static XYSeries copyOf(XYSeries series) throws CloneNotSupportedException {
        if (series == null) return null;
        if (series.getItemCount() == 0) return (XYSeries) series.clone();
        return series.createCopy(0, series.getItemCount() - 1);
    }

    @Override
    public double getStandardizedScale(){
        return standardizedScale;
    }

    @Override
    public double getStandardizedLocation(){
        return standardizedMin;
    }

    abstract void calculateIzeroRg();
    abstract void setPrDistribution();

    /**
     * implementing classes should calculate and set area
     * @return
     */
    @Override
    public double getArea(){
        return area;
    }

    @Override
    public double[] getCoefficients(){
        return coefficients;
    }

    /**
     * use MonteCarlo esque method for estimating errors through sampling dataset
     * @param fittedData
     */
    public abstract void estimateErrors(XYSeries fittedData);

    abstract void createDesignMatrix(XYSeries series);

    @Override
    public int getTotalInDistribution(){
        return totalInDistribution;
    }

    @Override
    public XYSeries getPrDistribution() {
        return prDistribution;
    }

    @Override
    public double getRg() {
        return rg;
    }

    @Override
    public double getRgError() {
        return rgError;
    }

    @Override
    public double getIZero() {
        return izero;
    }

    @Override
    public double getIZeroError() {
        return iZeroError;
    }

    @Override
    public double getRAverage(){
        return rAverage;
    }

    @Override
    public XYSeries getIqcalc() {
        return null;
    }

    @Override
    public XYSeries getqIqCalc() {
        return null;
    }

    public double normL1(SimpleMatrix vec){
        double sum = 0;
        int size = vec.getNumElements();

        for(int i=0; i<size; i++){
            sum += Math.abs(vec.get(i));
        }
        return sum;
    }

    public SimpleMatrix hessphi_coeffs_positivity_constrained(SimpleMatrix ata, SimpleMatrix d1, int rows) {

        // plus() already returns a new matrix; only copy out a block if a smaller one is requested
        SimpleMatrix t_ata = ata.plus(d1);
        if (t_ata.numRows() == rows && t_ata.numCols() == rows) {
            return t_ata;
        }
        return t_ata.extractMatrix(0, rows, 0, rows);
    }


    /**
     * Return maximum absolute value in vector set
     * @param vec
     * @return
     */
    public double inf_norm(SimpleMatrix vec){
        int sizeof = vec.getNumElements();
        double maxi = Math.abs(vec.get(0));
        double current;
        for(int i=1; i<sizeof; i++){
            current = Math.abs(vec.get(i));
            if (current > maxi){
                maxi = current;
            }
        }
        return maxi;
    }


    public double min(SimpleMatrix vec){

        double initial = vec.get(0);
        int length = vec.getNumElements();
        double current;

        for (int i=1; i<length; i++){
            current = vec.get(i);
            if (current < initial){
                initial = current;
            }
        }

        return initial;
    }

    public double getBin_width(){ return bin_width;}

    public double max(SimpleMatrix vec){
        double initial = vec.get(0);
        int length = vec.getNumElements();
        double current;

        for (int i=1; i<length; i++){
            current = vec.get(i);
            if (current > initial){
                initial = current;
            }
        }

        return initial;
    }

    public ArrayList<SimpleMatrix> linearPCGPositiveOnly(SimpleMatrix designMatrix, SimpleMatrix bMatrix, SimpleMatrix initial, SimpleMatrix d1, double pcgtol, int pcgmaxi){

        ArrayList<SimpleMatrix> returnElements = new ArrayList<SimpleMatrix>();

        final int cols = d1.numCols();
        final int size = designMatrix.numRows();

        /*
         * Jacobi preconditioner M = diag(2 + D1). M is diagonal, so M^-1 is applied element-wise
         * instead of building and inverting a dense matrix (previously O(n^3) per call).
         * Rows beyond the D1 block (if any) keep the Hessian's own diagonal, as before.
         */
        final double[] invM = new double[size];
        for (int i = 0; i < size; i++) {
            double m = (i < cols) ? (2 + d1.get(i, i)) : designMatrix.get(i, i);
            invM[i] = (m != 0) ? 1.0 / m : 1.0;
        }

        SimpleMatrix r_matrix = bMatrix.minus(designMatrix.mult(initial)); // residual
        SimpleMatrix z = applyDiagonal(invM, r_matrix);
        SimpleMatrix p = z.copy();
        SimpleMatrix delx_vector = initial.copy();
        final int xu_size = delx_vector.getNumElements();

        double rkTzk = r_matrix.dot(z);
        final double bNorm = bMatrix.normF();
        final double magB = (bNorm > 0) ? 1.0 / bNorm : 1.0;
        double topB = r_matrix.normF() * magB;

        int i = 0;
        while ((i < pcgmaxi) && topB > pcgtol) {

            SimpleMatrix q = designMatrix.mult(p);
            double pTq = p.dot(q);
            if (!(pTq > 0) || rkTzk == 0) break;      // converged, or lost positive definiteness
            double alpha = rkTzk / pTq;

            delx_vector = delx_vector.plus(alpha, p);
            SimpleMatrix r_plus_1 = r_matrix.minus(q.scale(alpha)); // r_k+1 = r_k - alpha_k * A*p_k
            SimpleMatrix z_plus_1 = applyDiagonal(invM, r_plus_1);

            // Polak-Ribiere beta
            double beta = z_plus_1.dot(r_plus_1.minus(r_matrix)) / rkTzk;

            p = z_plus_1.plus(beta, p);
            r_matrix = r_plus_1;
            z = z_plus_1;
            rkTzk = r_matrix.dot(z);

            // the recurrence residual replaces an extra A*x product per iteration
            topB = r_matrix.normF() * magB;
            i++;
        }

        SimpleMatrix x_vector = new SimpleMatrix(cols, 1);
        for (int ii = 0; ii < Math.min(xu_size, cols); ii++) {
            x_vector.set(ii, 0, delx_vector.get(ii));
        }

        SimpleMatrix pitr = new SimpleMatrix(1, 1);
        pitr.set(0, 0, i);

        returnElements.add(x_vector);    // 0
        returnElements.add(delx_vector); // 1
        returnElements.add(pitr);        // 2
        return returnElements;
    }

    /** element-wise diag(d) * v for a column vector v */
    private static SimpleMatrix applyDiagonal(double[] d, SimpleMatrix v) {
        int n = v.getNumElements();
        SimpleMatrix out = new SimpleMatrix(n, 1);
        for (int i = 0; i < n; i++) out.set(i, 0, d[i] * v.get(i));
        return out;
    }


    public SimpleMatrix hessphi_coeffs(SimpleMatrix ata, SimpleMatrix d1, SimpleMatrix d2, int coeffs_size_row) {

        int n = ata.numCols();

        SimpleMatrix hessian = new SimpleMatrix(2*coeffs_size_row,2*coeffs_size_row);
        SimpleMatrix t_ata;
        t_ata =  ata.plus(d1);

        double d2_value;

        for (int r=0; r < coeffs_size_row; r++){

            for(int c=0; c < coeffs_size_row; c++){
                hessian.set(r,c,t_ata.get(r,c));
            }

            d2_value = d2.get(r,r);
            hessian.set(r+n, r, d2_value);   //D2(transpose) lower-left
            hessian.set(r, r+n, d2_value);   //D2 upper-right
            hessian.set(r+n, r+n, d1.get(r,r)); // lower-right
        }
        return hessian;
    }

    /**
     *
     * @param designMatrix  Hessian
     * @param bMatrix  -gradient
     * @param initial dxu
     * @param d1
     * @param pcgtol
     * @param pcgmaxi
     * @param tauT
     * @return
     */
    public ArrayList<SimpleMatrix> linearPCG(SimpleMatrix designMatrix, SimpleMatrix bMatrix, SimpleMatrix initial, SimpleMatrix d1, double pcgtol, int pcgmaxi, double tauT){

        ArrayList<SimpleMatrix> returnElements = new ArrayList<SimpleMatrix>();
        SimpleMatrix r = bMatrix.minus(designMatrix.mult(initial)); // residual
        SimpleMatrix r_plus_1;
        SimpleMatrix preConditioner = designMatrix.copy();

        int n = d1.numCols();
        /*
         * P = M  D2
         *     D2 D3
         * Preconditioner, P is Hessian with first block set to:
         * M = 2*diagATA + D1
         *
         */
        //SimpleMatrix d3 = new SimpleMatrix(n,n);
        for(int row=0; row < n; row++){
            for(int col=0; col< n; col++){
                if (row == col){
                    //preConditioner.set(row,row, designMatrix.get(row,row));
                    preConditioner.set(row,row,2*tauT+d1.get(row,row));
                    // d3.set(row,row,2+d1.get(row,row));
                }
                if (row != col){ // preserve diagonal entries and just add D1
                    preConditioner.set(row, col, 0);
                    //System.out.println("d1 " + row + " " + d1.get(row, col));
                    //preConditioner.set(row, col, d1.get(row, col));
                }
            }
        }


        /*
        // Boyd Inversion Start
        SimpleMatrix d1d3minusd22 = d1.mult(d3).minus(d2.elementMult(d2));
        SimpleMatrix p1 = new SimpleMatrix(n,n);
        SimpleMatrix p2 = new SimpleMatrix(n,n);
        SimpleMatrix p3 = new SimpleMatrix(n,n);

        for(int row=0; row < n; row++){
             p1.set(row,row, d1.get(row,row)/d1d3minusd22.get(row,row));
             p2.set(row,row, d2.get(row,row)/d1d3minusd22.get(row,row));
             p3.set(row,row, d3.get(row,row)/d1d3minusd22.get(row,row));
        }
        // Boyd Inversion END
        */
        SimpleMatrix invertM = preConditioner.invert();
        SimpleMatrix z = invertM.mult(r); // search direction
        SimpleMatrix p = z.copy();

        SimpleMatrix xu_vector = initial.copy();
        int xu_size = xu_vector.getNumElements();

        SimpleMatrix x_vector = new SimpleMatrix(n, 1);
        SimpleMatrix u_vector = new SimpleMatrix(xu_size-n, 1);

        double rkTzk = r.dot(z);
        final double bNorm = bMatrix.normF();
        final double magB = (bNorm > 0) ? 1.0 / bNorm : 1.0;
        double topB = r.normF() * magB;

        int i=0;
        while( (i < pcgmaxi) && (topB > pcgtol) ){

            SimpleMatrix q = designMatrix.mult(p);
            double pTq = p.dot(q);
            if (!(pTq > 0) || rkTzk == 0) break;      // converged, or lost positive definiteness
            double alpha = rkTzk/pTq;

            xu_vector = xu_vector.plus(alpha, p);

            if (i < 50){
                r_plus_1 = bMatrix.minus(designMatrix.mult(xu_vector)); // exact residual early on
            } else {
                r_plus_1 = r.minus(q.scale(alpha)); // r_k+1 = r_k - alpha_k * A*p_k
            }

            SimpleMatrix z_plus_1 = invertM.mult(r_plus_1);

            //Polak-Ribiere Beta
            double beta = z_plus_1.dot(r_plus_1.minus(r))/rkTzk;

            p = z_plus_1.plus(beta, p);
            r = r_plus_1;
            z = z_plus_1;
            rkTzk = r.dot(z);
            // r is the exact residual for i < 50 and the recurrence afterwards;
            // this saves a second A*x product per iteration
            topB = r.normF()*magB;
            i++;
        }

        // System.out.println("PCG STOP: " + stop + " > " + rkTzk + " at " + i);

        for(int ii=0; ii<(xu_size-n); ii++){

            if (ii<n){
                x_vector.set(ii,0,xu_vector.get(ii));
            }

            u_vector.set(ii,0,xu_vector.get(ii+n));
        }

        // set final iterate number
        SimpleMatrix pitr = new SimpleMatrix(1,1);
        pitr.set(0,0,i);

        returnElements.add(x_vector);  // 0
        returnElements.add(u_vector);  // 1
        returnElements.add(xu_vector); // 2
        returnElements.add(pitr);      // 3
        return returnElements;
    }

    /**
     * value of second derivative calculated at points in r_vector
     * @param am Moore coefficients, assume am[0] is constant backgroud
     * @param r_vector set of equally distributions points along dmax
     * @param inv_d 1/dmax
     * @return
     */
    public SimpleMatrix p_dd_r(SimpleMatrix am, double[] r_vector, double inv_d){

        double r_value, pi_r_n_inv_d, pi_r_inv_d, cos_pi_r_n_inv_d, sin_pi_r_n_inv_d;
        double pi_inv_d = Math.PI*inv_d;
        double a_i;

        int r_limit = r_vector.length;

        SimpleMatrix p_dd_r = new SimpleMatrix(r_limit,1);

        int elements = am.getNumElements();

        double a_i_sum, pi_inv_d_n;

        for (int r=0; r < r_limit; r++){
            r_value = r_vector[r];
            pi_r_inv_d = pi_inv_d*r_value;
            a_i_sum = 0;

            for(int n=1; n < elements; n++){
                a_i = am.get(n,0);

                pi_inv_d_n = pi_inv_d*n;
                pi_r_n_inv_d = pi_r_inv_d*n;

                cos_pi_r_n_inv_d = FastMath.cos(pi_r_n_inv_d);
                sin_pi_r_n_inv_d = FastMath.sin(pi_r_n_inv_d); // if r_value is ZERO, sine is ZERO

                a_i_sum += 2*a_i*pi_inv_d_n*cos_pi_r_n_inv_d - r_value*a_i*pi_inv_d_n*pi_inv_d_n*sin_pi_r_n_inv_d;
            }

            p_dd_r.set(r,0, a_i_sum*0.5*inv_d);
        }

        return p_dd_r;
    }


    /**
     * 1st derivative of P"(r) with respect to the Moore coefficients
     * @param n_i
     * @param r
     * @param inv_d
     * @return
     */
    public double c_ni_r(int n_i, double r, double inv_d){
        double inv_d_pi_n = inv_d*Math.PI*n_i;
        double theta = r*inv_d_pi_n;
        double cir;
        //cir = inv_d*ni*FastMath.cos(theta) - Math.PI*r*0.5*inv_d*inv_d*ni*ni*FastMath.sin(theta);
        cir = 2.0*inv_d_pi_n*FastMath.cos(theta) - r*inv_d_pi_n*inv_d_pi_n*FastMath.sin(theta);
        return 0.5*inv_d*cir;
    }



    public SimpleMatrix hessphi(SimpleMatrix ata, SimpleMatrix d1, SimpleMatrix d2, SimpleMatrix d3){

        int h = d2.numCols();
        int n = ata.numCols();

        SimpleMatrix hessian = new SimpleMatrix(n+h,n+h);

        SimpleMatrix t_ata;
        t_ata =  ata.plus(d1); //ata and d1 are not modified

        double d2_value;

        for (int r=0; r < h; r++){

            for(int c=0; c < h; c++){

                if (r<n && c<n) {
                    d2_value = d2.get(r,c);
                    hessian.set(r,c,t_ata.get(r,c));    // ATA + D1
                    hessian.set(c+n, r, d2_value);      // D2(transpose) lower-left
                    hessian.set(r, c+n, d2_value);      // D2 upper-right
                } else if (r<n && c >=n) {
                    // d2 nxh
                    d2_value = d2.get(r,c);
                    hessian.set(c+n, r, d2_value);   //D2 (transpose) lower-left
                    hessian.set(r, c+n, d2_value);   //D2 upper-right
                }

                hessian.set(r+n, c+n, d3.get(r,c));
            }
        }
        //hessian.print();
        return hessian;
    }


    /**
     *
     * standardize data
     *
     * min-max normalization
     *
     */
    private void standardizeData(){
        XYDataItem tempData;
        data = new XYSeries("Standardized data");
        standardVariance = new XYSeries("standardized error");

        int totalItems = nonData.getItemCount();

        // smallest moving average over a window of 7% of the points (at least 1).
        // Previously the inner loop summed the same point windowSize times (getDataItem(r)
        // instead of r+w), so this was just the minimum single point.
        int windowSize = Math.max(1, (int)(0.07*totalItems));
        double windowSum = 0;
        for (int w=0; w<windowSize && w<totalItems; w++){
            windowSum += nonData.getY(w).doubleValue();
        }
        double minavg = windowSum;
        for (int r=1; r+windowSize <= totalItems; r++){   // slide: O(n) instead of O(n*window)
            windowSum += nonData.getY(r+windowSize-1).doubleValue() - nonData.getY(r-1).doubleValue();
            if (windowSum < minavg){
                minavg = windowSum;
            }
        }

        standardizedMin = minavg/(double)windowSize;
        standardizedScale = Math.abs(nonData.getMaxY() - standardizedMin);
        double invstdev = 1.0/standardizedScale;

        double temperrorvalue;

        for(int r=0; r<totalItems; r++){
            tempData = nonData.getDataItem(r);
            data.add(tempData.getX(), (tempData.getYValue() - standardizedMin)*invstdev);
            temperrorvalue = errors.getY(r).doubleValue()*tempData.getXValue()*invstdev; // q*sigma/scale
            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // get residual for this q-value
        }
    }


    /**
     * calculate residuals from current model and data
     */
    public void makeResiduals(){
        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        residuals = new XYSeries("residuals");
        for(int i=0; i < rows; i++){
            //double diff = tempResiduals.get(i,0);
            residuals.add(data.getDataItem(i).getX(), tempResiduals.get(i,0));
        }
    }

    /**
     * estimate chi-squared by evaluating the function at the points of the cardinal series
     * errors must be standardized
     *
     * @return
     */
    @Override
    public double getChiEstimate(){
        // residuals at points nearest the Shannon-sampled q-values (odd multiples of PI/(6 dmax))
        makeResiduals();

        final double delta_q = Math.PI/(dmax*6.0d);
        final int total = data.getItemCount();
        final double maxQ = data.getMaxX();

        int startIndex = (int)Math.floor(data.getMinX()/delta_q);
        startIndex += ((startIndex & 1) == 0) ? 1 : 2;   // first odd index above q_min

        ArrayList<Integer> chiIndices = new ArrayList<>();
        chiIndices.add(0);
        int startAt = 0;
        for (; startIndex*delta_q < maxQ; startIndex += 2){   // odd indices only
            double shannon_q = startIndex*delta_q;
            for (int i = startAt; i < total; i++){            // was: condition on startAt (never changes)
                double qi = data.getX(i).doubleValue();
                if (qi > shannon_q){                           // first point beyond shannon_q
                    int pick = i;
                    if (i > 0 && (shannon_q - data.getX(i-1).doubleValue()) < (qi - shannon_q)){
                        pick = i-1;                            // previous point is closer
                    }
                    if (pick != chiIndices.get(chiIndices.size()-1)){   // don't count a point twice
                        chiIndices.add(pick);
                    }
                    startAt = i;
                    break;
                }
            }
        }

        double chi = 0;
        n_chipoints = 0;
        for (int idx : chiIndices){
            double variance = standardVariance.getY(idx).doubleValue();
            if (!(variance > 0)) continue;                     // skip points with no usable error
            double diff = residuals.getY(idx).doubleValue();
            chi += diff*diff/variance;
            n_chipoints += 1.0;
        }

        final int aic_k = ns + 2;
        double aicDenom = n_chipoints - aic_k - 1;
        aic = (aicDenom > 0)
                ? 2.0*aic_k + chi + (2.0*aic_k*aic_k + 2.0*aic_k)/aicDenom
                : Double.POSITIVE_INFINITY;                    // too few points for AICc

        degreesOfFreedom = n_chipoints - 2;
        return (degreesOfFreedom > 0) ? chi/degreesOfFreedom : Double.POSITIVE_INFINITY;
    }


    /**
     * AIC for Gaussian errors with known sigma: chi^2 summed over EVERY fitted point, plus 2k,
     * with k = ns + 2.
     *
     * Intended for comparing fits across a dmax scan, with the minimum at the lowest adequate
     * dmax: chi^2 rises steeply once dmax is too short, while the 2k term charges ~2 per extra
     * Shannon channel once the fit has stopped improving. Using all points (rather than ~3 per
     * Shannon channel) is what makes that wall sharp and the minimum stable.
     *
     * Suggested use:  current_score = getAICAllPoints() + 4*kt + getPrScore();
     *
     * Residuals and errors are both put in standardized q*I(q) units, so q and the scale cancel.
     */
    public double getAICAllPoints(){
        return getAICAllPoints(1.0);
    }

    /**
     * Quasi-AIC: chi^2/cHat + 2k. Use cHat = the reduced chi^2 of the best (lowest) fit in the dmax
     * scan (see referenceCHat) when chi^2_red is well above 1: underestimated errors or systematic
     * misfit otherwise inflate every AIC difference by that factor and swamp the P(r) score.
     * cHat <= 1 gives the ordinary AIC.
     */
    public double getAICAllPoints(double cHat){
        double c = (cHat > 1) ? cHat : 1.0;
        return getChiSquaredAllPoints()/c + 2.0*(ns + 2);
    }

    /** reduced chi^2 over all fitted points, dof = points - (ns + 2) */
    public double getReducedChiSquaredAllPoints(){
        double chi = getChiSquaredAllPoints();                // also refreshes residuals
        int dof = residuals.getItemCount() - (ns + 2);
        return (dof > 0) ? chi/dof : Double.NaN;
    }

    /** cHat for a dmax scan: the smallest reduced chi^2 among the fits */
    public static double referenceCHat(java.util.List<? extends IndirectFT> scan){
        double best = Double.POSITIVE_INFINITY;
        for (IndirectFT f : scan) best = Math.min(best, f.getReducedChiSquaredAllPoints());
        return best;
    }

    /** chi^2 over all fitted points (standardized q*I(q) units) */
    public double getChiSquaredAllPoints(){
        makeResiduals();
        double chi = 0;
        final int total = Math.min(residuals.getItemCount(), errors.getItemCount());
        for (int i=0; i<total; i++){
            double q = residuals.getX(i).doubleValue();
            double sigma = errors.getY(i).doubleValue()*q/standardizedScale;  // sigma of standardized qI(q)
            if (!(sigma > 0)) continue;
            double z = residuals.getY(i).doubleValue()/sigma;
            chi += z*z;
        }
        return chi;
    }


    /**
     * Use Durbin-Watson test for normality (randomness)
     * Number is reported as the median from random subselection
     * @param rounds
     * @return
     */
    @Override
    public double getKurtosisEstimate(int rounds){
        /*
         * Durbin-Watson statistic over all residuals, d = Sum(e_t - e_[t-1])^2 / Sum(e_t^2);
         * returns |2 - d| (0 = uncorrelated residuals).
         *
         * The previous version also ran `rounds` random sub-samples and sorted them, but then
         * returned this full-residual value regardless, so that work was discarded. It has been
         * removed; `rounds` is kept for the interface.
         */
        int total = residuals.getItemCount();
        if (total < 2) return 0;
        double prev = residuals.getY(0).doubleValue();
        double numerator = 0, denominator = prev*prev;
        for (int j=1; j<total; j++){
            double value = residuals.getY(j).doubleValue();
            double diff = value - prev;
            numerator += diff*diff;
            denominator += value*value;
            prev = value;
        }
        return (denominator > 0) ? Math.abs(2 - numerator/denominator) : 0;
    }


    @Override
    public int getTotalFittedCoefficients(){
        return includeBackground ? totalCoefficients : totalCoefficients -1 ;
    }



    /**
     * Dataset must be standardized and transformed as q*Iq
     * @param dataset
     * @return
     */
    @Override
    public double calculateMedianResidual(XYSeries dataset){

        List<Double> residualsList = new ArrayList<Double>();
        int dataLimit = dataset.getItemCount();
        double resi;

        this.createDesignMatrix(dataset); // this creates a_matrix and y_vector

        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector); // this would resize rows and residuals via a_matrix and y_vector

        for (int i = 0; i < dataLimit; i++){
            resi = tempResiduals.get(i,0);
            residualsList.add(resi*resi);
        } //
        // need to resetDesignMatrix so that a_matrix and y_vector revert back to the original dataset
        return Statistics.calculateMedian(residualsList);
    }


    /**
     * Dataset must be standardized and transformed as q*Iq
     * @param tempDataset
     * @return
     */
    @Override
    public double calculateChiFromDataset(XYSeries tempDataset, XYSeries tempErrors){

        List<Double> residualsList = new ArrayList<Double>();
        int dataLimit = tempDataset.getItemCount();
        double resi;

        XYSeries fitme = new XYSeries("FITME");
        XYSeries errorsInUse = new XYSeries("errors");

        final double delta_q = Math.PI/(dmax*6.0d);
        //define q-values for calculating chi2
        double maxQ = tempDataset.getMaxX();
        ArrayList<Integer> chiIndices = new ArrayList<>();

        int startIndex = (int)Math.floor(tempDataset.getMinX()/delta_q); //check first point
        if ( (startIndex & 1) == 0){ // if even, starting index is greater than startIndex + 1
            startIndex += 1;
        } else { // if odd
            startIndex += 2;
        }
        int startAt = 0;
        chiIndices.add(0);
        for (; startIndex*delta_q < maxQ; startIndex += 2){   // odd indices only
            double shannon_q = startIndex*delta_q;
            for(int i = startAt; i<dataLimit; i++){            // was: condition on startAt
                if (tempDataset.getX(i).doubleValue() > shannon_q){ // first value greater than shannon_q
                    if (i != chiIndices.get(chiIndices.size()-1)) chiIndices.add(i);
                    startAt = i;
                    break;
                }
            }
        }

        if (dataLimit-1 != chiIndices.get(chiIndices.size()-1)) chiIndices.add(dataLimit-1);


        for(int i=0; i<chiIndices.size();i++){
            fitme.add(tempDataset.getDataItem(chiIndices.get(i)));
            XYDataItem item = tempErrors.getDataItem(chiIndices.get(i));
            double value = item.getYValue();
            errorsInUse.add(item.getX(),value*value);
        }


        /*
         * createDesignMatrix with fitme will make:
         * a_matrix and y_vector
         * overwwrites any exisiting a_matrix or y_vector
         */
        this.createDesignMatrix(fitme); // this creates a_matrix and y_vector

        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector); // this would resize rows and residuals via a_matrix and y_vector

        double sum=0;
        for (int i = 0; i < fitme.getItemCount(); i++){
            resi = tempResiduals.get(i,0);
            sum += resi*resi/errorsInUse.getY(i).doubleValue();
        } //
        //System.out.println(fitme.getItemCount() + " sum " + sum/(double)fitme.getItemCount() );
        // need to resetDesignMatrix so that a_matrix and y_vector revert back to the original dataset
        return sum/((double)fitme.getItemCount() + 2);
    }

    @Override
    public String getModelUsed(){ return this.modelUsed; }

    @Override
    public void setNonStandardizedData(XYSeries nonStandardizedData){
        this.nonData = nonStandardizedData;
    }

    @Override
    public void createNonStandardizedData(){
        nonData = new XYSeries("nonstandard");
        for (int i=0; i<this.data.getItemCount(); i++){
            XYDataItem item = this.data.getDataItem(i);
            nonData.add(item.getX(), item.getYValue()*standardizedScale+standardizedMin);
            errors.updateByIndex(i, errors.getY(i).doubleValue()*standardizedScale/item.getXValue());
        }
    }

    @Override
    public String getHeader(double scale){

        String output = String.format("REMARK 265 %n");
        output += String.format("REMARK 265 EXPERIMENTAL REAL SPACE FILE %n");
        output += String.format("REMARK 265    P(r)-DISTRIBUTION BASED ON : %s %n", modelUsed);
        output += description;

        output += String.format("REMARK 265 %n");
        output += String.format("REMARK 265  BIN COEFFICIENTS (UNSCALED)%n");
        output += String.format("REMARK 265 %n");

        StringBuilder sb = new StringBuilder(output);
        sb.append(getPrDistributionForFitting());

        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265  SCALED P(r) DISTRIBUTION %n"));
        sb.append(String.format("REMARK 265      SCALE : %.3E %n", scale));
        sb.append(String.format("REMARK 265    COLUMNS : r, P(r), error%n"));
        sb.append(String.format("REMARK 265          r : defined in Angstroms%n"));

        double incr = Math.PI/qmax/3.0;
        incr = (incr < 3.1 ) ? 3.1 : incr;

        // appended directly: passing pre-formatted text through String.format() as the
        // format string would throw if it ever contained a '%'
        sb.append(Constants.Scientific1dot4e2.format(0)).append('\t').append(Constants.Scientific1dot2e1.format(0)).append("\t 0.00 \n");
        for (int r = 1; r*incr < dmax; r++){
            double r_incr = r*incr;
            sb.append(Constants.Scientific1dot4e2.format(r_incr)).append('\t')
              .append(Constants.Scientific1dot2e1.format(this.calculatePofRAtR(r_incr, scale))).append("\t 0.00 \n");
        }
        sb.append(Constants.Scientific1dot4e2.format(dmax)).append('\t').append(Constants.Scientific1dot2e1.format(0)).append("\t 0.00 \n");

        return sb.toString();
    }

    public void setModelUsed(String text){
        this.modelUsed = text;
    }

    /**
     * Required for structural modeling using Iketama
     * @return
     */
    private String getPrDistributionForFitting(){

        String temp = String.format("REMARK 265 P(R) DISTRIBUTION BINNED USING SHANNON NUMBER %n");
              temp += String.format("REMARK 265 R-values REPRESENT THE MID-POINT OF EACH BIN (WIDTH PI/QMAX) %n");
              temp += String.format("REMARK 265 BIN HEIGHT IS THE MEAN OF P(R) OVER THE BIN (P = 0 BEYOND DMAX) %n");
              temp += String.format("REMARK 265        BIN            R-value : BIN HEIGHT %n");
              temp += String.format("REMARK 265 %n");

        int total = prDistributionForFitting.getItemCount();
        StringBuilder sb = new StringBuilder(temp);
        for(int i=0; i<total; i++){
            XYDataItem item = prDistributionForFitting.getDataItem(i);
            sb.append(String.format("REMARK 265       BIN_%-2d          %9.3f : %.5E %n", i+1, item.getXValue(), item.getYValue()));
        }

        sb.append(String.format("REMARK 265 %n"));
        sb.append(String.format("REMARK 265            TOTAL SHANNON BINS : %d %n", total));
        return sb.toString();
    }

    /** identical output to getPrDistributionForFittingCIFFormat(id) */
    public String getIFTParametersCIFFormat(int id){
        return getPrDistributionForFittingCIFFormat(id);
    }

    public String getPrDistributionForFittingCIFFormat(int id){
        int total = prDistributionForFitting.getItemCount();
        String tempHeader =  String.format("# REMARK 265 P(r) histogram details for modeling %n");
        tempHeader += String.format("_sas_p_of_R_details.Shannon_number %d\n", ns);
        tempHeader += String.format("_sas_p_of_R_details.bin_width %.3f\n", bin_width);
        tempHeader += String.format("_sas_p_of_R_details.r_average %.3f\n", rAverage);
        tempHeader += String.format("loop_%n");
        tempHeader += String.format("_sas_p_of_R.id %n");
        tempHeader += String.format("_sas_p_of_R.ordinal %n");
        tempHeader += String.format("_sas_p_of_R.bin %n");
        tempHeader += String.format("_sas_p_of_R.bin_height %n");
        StringBuilder sb = new StringBuilder(tempHeader);
        for(int i=0; i<total; i++){
            XYDataItem item = prDistributionForFitting.getDataItem(i);
            sb.append(String.format("%-2d %-2d %.3f %.4E %n", id, i+1, item.getXValue(), item.getYValue()));
        }
        sb.append("# \n");
        return sb.toString();
    }


    public String getPrDistributionForPlottingCIFFormat(int id){
        String tempHeader = String.format("# REMARK 265 P(r)-distribution for plotting %n");
        tempHeader += String.format("loop_%n");
        tempHeader += String.format("_sas_p_of_R.id%n");
        tempHeader += String.format("_sas_p_of_R.ordinal%n");
        tempHeader += String.format("_sas_p_of_R.R%n");
        tempHeader += String.format("_sas_p_of_R.P%n");
        tempHeader += String.format("_sas_p_of_R.P_error%n");

        StringBuilder sb = new StringBuilder(tempHeader);
        for (int n=0; n < prDistribution.getItemCount(); n++) {
            XYDataItem item = prDistribution.getDataItem(n);
            sb.append(String.format("%-2d %-3d %.3E %.4E ? %n", id, n+1, item.getXValue(), item.getYValue()));
        }
        sb.append("# \n");
        return sb.toString();
    }


    /**
     * Pr-distribution series must be calculated first, will through an error
     *
     */
    public void calculateSphericalCalibration(){
        sphericalDistribution = new XYSeries("Spherical Distribution");
        sphericalDistributionFine = new XYSeries("FINE");

        double radius = dmax*0.5d;
        double radiusCubed = radius*radius*radius;
        double invR3 = 1.0/radiusCubed;
        double constantA = 3.0*invR3;
        double constantB = 9.0/4*invR3/radius;
        double constantC = 3.0/16.0*invR3*invR3;

        sphericalVolume = 4.0/3.0*Math.PI*radiusCubed;
        sphericalDistribution.add(0,0);

        double delta = prDistribution.getX(2).doubleValue() - prDistribution.getX(1).doubleValue();
        double increment = delta/2.0d;
        double rat = 0.25*delta;
        while(rat < dmax){
            double rsquared = rat*rat;
            double rcubed = rsquared*rat;
            sphericalDistributionFine.add(rat, sphericalVolume*(constantA*rsquared - constantB*rcubed + constantC*rsquared*rcubed));
            rat += increment;
        }

        int last = totalInDistribution-1;
        for(int i=1; i<last; i++){
            XYDataItem value = prDistribution.getDataItem(i);
            double rvalue = value.getXValue();
            double rsquared = rvalue*rvalue;
            double rcubed = rsquared*rvalue;
            sphericalDistribution.add(value.getX(), sphericalVolume*(constantA*rsquared - constantB*rcubed + constantC*rsquared*rcubed));
        }

        sphericalDistribution.add(dmax, 0);

        sphericalRg = Math.sqrt((dmax*dmax*0.25)*3.0/5.0d);
//        System.out.println("Spherical VOlume " + sphericalVolume + " :: " + dmax + " Rg :: " + sphericalRg);
//        System.out.println("distribution sizes " + sphericalDistribution.getItemCount() + " == " + prDistribution.getItemCount());
    }




    /**
     *
     * Using spherical distribution, scale P(r)-distribution to the smallest value that provides only positive differences
     * which scale factor produces the largest non-negative difference in areas
     *
     */
    public void calibratePrDistribution(){

        double scalefactor=1, keptScalefactor = 1;
        int last = totalInDistribution-1;
        double keptsum = Double.POSITIVE_INFINITY;
        double delta = sphericalDistribution.getX(2).doubleValue() - sphericalDistribution.getX(1).doubleValue();

        // NOTE: correctedVolume below is computed but not stored or returned, so this method
        // currently has no observable effect. (A disabled loop over sphericalDistributionFine,
        // `i < 0*lastIn`, has been removed.)
        for(int i=1; i<last; i++){

            double pr_i = prDistribution.getY(i).doubleValue();
            if (pr_i <= 0) continue;                      // avoid infinite / negative scale factors
            scalefactor = sphericalDistribution.getY(i).doubleValue()/pr_i;
            boolean pleaseCOntinue = true;
            double diffsum = 0;

            for(int j=1; j<last; j++){ // go through each trapezoid and calculate area

                double diff = sphericalDistribution.getY(j).doubleValue() - scalefactor*prDistribution.getY(j).doubleValue();

                if (diff < 0){
                    pleaseCOntinue = false;
                    break;
                }
                diffsum+=diff;
            }

            if(pleaseCOntinue && diffsum < keptsum){
                keptsum = diffsum;
                keptScalefactor = scalefactor;
                //System.out.println("kept factor " + i + " " + scalefactor + " => " + keptsum);
            }
        }

        // need two corrections, one is rescaling based on size and the other is shape
        double correctedVolume = sphericalVolume - keptsum*delta;
//        System.out.println(sphericalVolume +  " => Corrected Volume : " + correctedVolume*this.rg/sphericalRg);

//        for(int i=0; i<totalInDistribution; i++){
//            System.out.println(sphericalDistribution.getX(i) + " " + sphericalDistribution.getY(i).doubleValue() + " " + keptScalefactor*prDistribution.getY(i).doubleValue());
//        }
    }

    /** Weights for the P(r) quality score; tune against reference datasets with known dmax. */
    /**
     * Penalty on STATISTICALLY SIGNIFICANT negative P(r) after the peak:
     *     W_NEG_SIG * max(0, -z_min - NEG_SIG_ALLOWED)^2
     * z_min = most negative z of the mean of P(r) over any window one Shannon bin (pi/qmax) wide
     * after the peak, with sigma from the fit's posterior covariance (prDistributionBasis +
     * coefficientCovariance). Averaging over the resolution element makes the test independent of
     * how finely a class samples P(r), and uses the correlations between neighbouring points.
     * In chi^2-like units, so it is commensurate with the (quasi-)AIC; the weight of 100 makes
     * crossing the threshold decisive against AIC gains. Noise-level dips cost nothing;
     * dips the data force below zero (dmax too long, interparticle repulsion, over-subtraction)
     * are penalized severely.
     */
    public static double W_NEG_SIG = 100.0, NEG_SIG_ALLOWED = 3.0;
    /** window width for the significance test, in Shannon bins (pi/qmax) */
    public static double NEG_SIG_WINDOW_BINS = 1.0;
    /** min P/sigma after the peak (NaN when no sigma is available) and the resulting penalty argument */
    public double prNegZ = Double.NaN, prNegSignificance;

    /** posterior covariance of the fitted coefficients (a_matrix column layout); null = unknown */
    protected double[][] coefficientCovariance;
    /**
     * Effective number of fitted parameters, trace of the hat matrix: tr[(A'WA + wR'R)^-1 A'WA].
     * Equals the number of coefficients without regularization and falls toward 1 (background)
     * as the penalty weight grows. NaN until a subclass calls setPosterior.
     */
    public double effectiveParameters = Double.NaN;
    /** value of the regularization term at the solution, w * |R a|^2 (0 without a penalty) */
    public double penaltyValue = 0;
    /** ln det(A'WA + wR'R) and ln det(w R_p'R_p) over the penalized columns (NaN if unavailable) */
    protected double logDetNormal = Double.NaN, logDetPrior = Double.NaN;
    /** number of unpenalized coefficients (e.g. background) */
    protected int unpenalizedCount = 0;
    /**
     * d(prDistribution value)/d(coefficient) for every prDistribution point (rows aligned with
     * prDistribution, a_matrix column layout; null rows at r = 0 and dmax). Set by the IFT classes;
     * with coefficientCovariance it gives the joint uncertainty of any combination of P(r) values.
     */
    protected double[][] prDistributionBasis;
    /** sd of each prDistribution value (derived from the basis), 0 at r = 0 and dmax; null = unknown */
    protected double[] prDistributionSigma;

    /** penalty on the depth of negative P(r) near dmax, relative to the peak (0 disables) */
    public static double W_NEG_DEPTH = 100.0, NEG_DEPTH_POWER = 2.0;
    public static double W_ROUGH = 1.0, W_NEG_TAIL = 40.0, W_NEG_HEAD = 20.0,
            W_OSC = 10.0, W_END_CONCAVE = 3.0, W_TAIL = 3.0, W_DEAD = 10.0;
    /** Fraction of the post-peak range treated as "near dmax". */
    public static double END_FRACTION = 0.30;
    /** |P(r)| below this fraction of the maximum counts as "zero". */
    public static double MIN_ANCHOR = 0.03;
    /** Fraction of the range allowed to sit at ~0 before dmax (a quadratic finish uses ~10%). */
    public static double DEAD_ZONE_ALLOWED = 0.12;

    /** Score components from the last call to scoreDistribution(), for diagnostics and logging. */
    public double prRoughness, prNegTail, prNegHead, prOscillation, prEndConcave, prTailExcess, prDeadZone, prNegDepth;
    public int prReversals;

    /**
     * Quality score of the P(r)-distribution. Lower is better; ~0.5 for a clean, compact particle.
     *
     * Every component is dimensionless, so the score does not depend on the units of r,
     * the absolute scale of P(r), or dmax itself, and can be compared across a dmax scan.
     *
     * Expects prDistribution to run from r = 0 to r = dmax (as setPrDistribution() builds it),
     * and prDistributionForFitting to hold P(r) at the Shannon nodes, excluding 0 and dmax.
     *
     * Sets prScore, distribution_score (the shape penalties, i.e. prScore minus roughness)
     * and the pr* component fields.
     */
    public double scoreDistribution() {
        totalInDistribution = prDistribution.getItemCount();
        final int n = totalInDistribution;
        prRoughness = prNegTail = prNegHead = prOscillation = prEndConcave = prTailExcess = prDeadZone = prNegDepth = 0;
        prNegZ = Double.NaN;
        prNegSignificance = 0;
        prReversals = 0;

        if (n < 6) {                                  // too few points to judge the shape
            distribution_score = prScore = Double.MAX_VALUE;
            return prScore;
        }

        // values are read several times below, so take them out of the series once
        double[] r = new double[n], p = new double[n];
        for (int i = 0; i < n; i++) {
            r[i] = prDistribution.getX(i).doubleValue();
            p[i] = prDistribution.getY(i).doubleValue();
        }

        // --- normalise to unit area on u = r/dmax ------------------------------------------
        double absArea = 0;
        for (int i = 1; i < n; i++) absArea += 0.5 * (Math.abs(p[i]) + Math.abs(p[i - 1])) * (r[i] - r[i - 1]);
        if (!(absArea > 0)) {
            distribution_score = prScore = Double.MAX_VALUE;
            return prScore;
        }
        double[] u = new double[n], f = new double[n];
        for (int i = 0; i < n; i++) { u[i] = r[i] / dmax; f[i] = p[i] * dmax / absArea; }

        int peak = 1;
        for (int i = 1; i < n - 1; i++) if (f[i] > f[peak]) peak = i;
        final double fMax = f[peak];
        if (!(fMax > 0)) {
            distribution_score = prScore = Double.MAX_VALUE;
            return prScore;
        }

        // --- second derivative, non-uniform 3-point stencil (handles half-steps at 0 and dmax)
        double[] d2 = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double h1 = u[i] - u[i - 1], h2 = u[i + 1] - u[i];
            d2[i] = 2.0 * (f[i - 1] * h2 - f[i] * (h1 + h2) + f[i + 1] * h1) / (h1 * h2 * (h1 + h2));
        }

        // --- roughness, measured on the particle's own size (Rg from P(r)), not dmax ----------
        double m0 = 0, m2 = 0;
        for (int i = 1; i < n; i++) {
            double dr = r[i] - r[i - 1];
            m0 += 0.5 * (p[i] + p[i - 1]) * dr;
            m2 += 0.5 * (r[i] * r[i] * p[i] + r[i - 1] * r[i - 1] * p[i - 1]) * dr;
        }
        double rgOverD = (m0 > 0 && m2 > 0) ? Math.sqrt(m2 / (2 * m0)) / dmax : 0.5;
        double ss = 0;
        for (int i = 1; i < n - 1; i++) ss += d2[i] * d2[i] * 0.5 * (u[i + 1] - u[i - 1]);
        prRoughness = Math.log10(1.0 + Math.sqrt(ss) * Math.pow(rgOverD, 2.5));

        // --- negativity, split at the peak ---------------------------------------------------
        for (int i = 1; i < n; i++) {
            double neg = 0.5 * (Math.max(-f[i], 0) + Math.max(-f[i - 1], 0)) * (u[i] - u[i - 1]);
            if (i > peak) prNegTail += neg; else prNegHead += neg;
        }

        // --- depth of negative P(r) after the peak, weighted toward dmax --------------------
        // max over r > r_peak of (-P/Pmax) * ((r - r_peak)/(dmax - r_peak))^NEG_DEPTH_POWER
        for (int i = peak + 1; i < n; i++) {
            if (f[i] >= 0) continue;
            double ramp = Math.pow((u[i] - u[peak])/(1.0 - u[peak]), NEG_DEPTH_POWER);
            prNegDepth = Math.max(prNegDepth, -f[i]/fMax*ramp);
        }

        // --- significance of negative P(r) after the peak (needs basis + covariance) ---------
        prNegZ = Double.NaN;
        prNegSignificance = 0;
        if (prDistributionBasis != null && prDistributionBasis.length == n && coefficientCovariance != null) {
            double window = NEG_SIG_WINDOW_BINS*Math.PI/qmax;
            double zmin = Double.POSITIVE_INFINITY;
            for (int i = peak + 1; i < n - 1; i++) {
                if (prDistributionBasis[i] == null) continue;
                int k = prDistributionBasis[i].length;
                double[] v = new double[k];
                double mean = 0;
                int count = 0;
                for (int j = i; j < n - 1 && r[j] < r[i] + window; j++) {
                    double[] b = prDistributionBasis[j];
                    if (b == null) continue;
                    for (int c = 0; c < k; c++) v[c] += b[c];
                    mean += p[j];
                    count++;
                }
                if (count == 0) continue;
                double sd = sigmaOfLinear(v);              // sd of the window sum
                if (sd > 0) zmin = Math.min(zmin, mean/sd);
            }
            if (zmin < Double.POSITIVE_INFINITY) {
                prNegZ = zmin;
                double excess = -zmin - NEG_SIG_ALLOWED;
                if (excess > 0) prNegSignificance = excess*excess;
            }
        }

        // --- oscillation at the Shannon nodes: excess total variation after the peak ---------
        int ns_count = (prDistributionForFitting == null) ? 0 : prDistributionForFitting.getItemCount();
        if (ns_count >= 2) {
            double[] pn = new double[ns_count];
            int pk = 0;
            for (int i = 0; i < ns_count; i++) {
                pn[i] = prDistributionForFitting.getY(i).doubleValue();
                if (pn[i] > pn[pk]) pk = i;
            }
            double tv = 0, prevStep = 0;
            for (int i = pk + 1; i <= ns_count; i++) {
                double next = (i == ns_count) ? 0.0 : pn[i];     // close the path at P(dmax) = 0
                double step = next - pn[i - 1];
                tv += Math.abs(step);
                if (prevStep != 0 && step != 0 && Math.signum(step) != Math.signum(prevStep)) prReversals++;
                if (step != 0) prevStep = step;
            }
            double top = pn[pk];
            prOscillation = (top > 0) ? Math.max(0, tv - top) / (2 * top) : 1.0;
        }

        // --- the final stretch before dmax -------------------------------------------------------
        double uStart = u[peak] + (1.0 - END_FRACTION) * (1.0 - u[peak]);
        int a = n - 2;
        while (a > peak && u[a - 1] >= uStart) a--;
        if (a > n - 3) a = Math.max(peak, n - 3);        // at least two interior points

        // concave-down finish = dmax truncated
        double cc = 0, cAll = 0;
        for (int i = a; i < n - 1; i++) {
            double w = 0.5 * (u[i + 1] - u[i - 1]);
            cAll += Math.abs(d2[i]) * w;
            cc += Math.max(-d2[i], 0) * w;
        }
        prEndConcave = (cAll > 0) ? cc / cAll : 0;

        // tail area vs chord: quadratic approach -> 2/3, straight line -> 1, bulge -> > 1
        double tailArea = 0;
        for (int i = a + 1; i < n; i++) tailArea += 0.5 * (f[i] + f[i - 1]) * (u[i] - u[i - 1]);
        double chord = 0.5 * (1.0 - u[a]) * f[a];
        if (f[a] > MIN_ANCHOR * fMax) {
            double ratio = Math.min(tailArea / chord, 2.0);
            prTailExcess = Math.max(0, ratio - 2.0 / 3.0) * 3.0;
        }

        // dead zone: range sitting at ~0 before dmax = dmax too large
        int z = n - 1;
        while (z > peak && Math.abs(f[z - 1]) < MIN_ANCHOR * fMax) z--;
        prDeadZone = Math.max(0, (1.0 - u[z]) - DEAD_ZONE_ALLOWED);

        distribution_score = W_NEG_SIG * prNegSignificance + W_NEG_DEPTH * prNegDepth + W_NEG_TAIL * prNegTail + W_NEG_HEAD * prNegHead + W_OSC * prOscillation
                + W_END_CONCAVE * prEndConcave + W_TAIL * prTailExcess + W_DEAD * prDeadZone;
        prScore = W_ROUGH * prRoughness + distribution_score;
        return prScore;
    }

    /**
     * Indices of the local minima of a dmax scan whose score is within {@code tolerance}
     * (e.g. 1.5 = 50%) of the best. More than one well-separated minimum is a strong sign
     * of a mixture or aggregation: one at the main species' size, one at the large species'.
     */
    public static List<Integer> competingMinima(double[] scanScores, double tolerance) {
        List<Integer> out = new ArrayList<>();
        double best = Double.MAX_VALUE;
        for (double s : scanScores) best = Math.min(best, s);
        for (int i = 0; i < scanScores.length; i++) {
            boolean left = (i == 0) || scanScores[i] <= scanScores[i - 1];
            boolean right = (i == scanScores.length - 1) || scanScores[i] <= scanScores[i + 1];
            if (left && right && scanScores[i] <= best * tolerance) out.add(i);
        }
        return out;
    }

    public double getPrScore() { return prScore;}
    /*
     * use in refining since dmax is fixed
     *
     */
    public void setPrior(double[] coefficients){
        prior_coefficients = coefficients.clone();
        priorExists = true;
    }


    public double getShannonNumber(){
        return ns;
    }


    @Override
    public double extrapolateToIofQ(double q) {

        //double intensity = Math.log(this.izero) - (this.rg*this.rg)/3.0*q*q;
        double slope = rg*rg/3.0;
        int total = nonData.getItemCount();

        XYDataItem startItem = nonData.getDataItem(0);
        double limit = 1.05/this.rg;
        int nnextItem = 0;
        while(nnextItem < total && nonData.getX(nnextItem).doubleValue() < limit){ // was unbounded
            nnextItem+=1;
        }
        // use all points up to limit for Guinier extrapolation

        if (nnextItem > 1){
            double xvals;
            double yvals = 0;


            for(int i=0; i<nnextItem; i++){
                startItem = nonData.getDataItem(i);

                xvals = startItem.getXValue()*startItem.getXValue();

                yvals += Math.log(startItem.getYValue()/startItem.getXValue()) + slope*xvals;
                //System.out.println(i + " "+startItem.getX() + " " + (Math.log(startItem.getYValue()/startItem.getXValue()) + slope*xvals) + " :: " + this.rg);
            }

            double tizero = Math.exp(yvals/(double)nnextItem);
            double weight = Math.exp(-(izero-tizero)/izero);

            double lnintensity = (izero+weight*tizero)/(1.0d+weight)*Math.exp(-slope*q*q);
            return lnintensity;

        } else {

            return izero*Math.exp(-slope*q*q);
            //return (Math.exp(lnintensity)+ coefficients[0])*standardizedScale + standardizedMin;
        }

    }
}
