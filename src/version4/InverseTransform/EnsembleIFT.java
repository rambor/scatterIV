package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines several indirect Fourier transforms of the SAME data (same dmax, qmax and background
 * setting) into one Shannon-binned P(r) with a full covariance.
 *
 * The methods are not independent measurements: they share the data noise and differ in their
 * prior (smoothness, coefficient norm, truncated basis). So they are combined by model averaging,
 * not inverse-variance weighting:
 *
 *     mean       h_k  = sum_m w_m h_k^(m)
 *     covariance C_kl = sum_m w_m C_kl^(m)  +  sum_m w_m (h_k^(m) - h_k)(h_l^(m) - h_l)
 *                       (within: data noise)   (between: model choice)
 *
 * The between-method spread is the systematic uncertainty no single method reports. A bin whose
 * between-method sd exceeds its within-method sd is set by the priors rather than the data
 * (see isPriorDominated); down-weight it in modelling.
 *
 * Each member's bin covariance is g C g', with C the member's coefficient covariance and g the
 * gradient of the bin average with respect to the coefficients, integrated from the member's
 * prDistributionBasis with linear interpolation between its prDistribution points (exact for the
 * bin values themselves, which come from prDistributionForFitting).
 *
 * Requirements: every member must produce the same Shannon bins, i.e. ceil(dmax/(pi/qmax)) bin
 * averages. For SVD set SVD.MODEL_FITTED_BINS_ONLY = false; for Legendre prefer
 * LegendreTransform.STRETCH_GRID_WITH_DMAX = true (its default grid stops short of dmax).
 */
public class EnsembleIFT {

    public enum Weighting {
        /** equal weights over the members that pass the quality gate */
        EQUAL,
        /** Bayesian model averaging, w ~ exp(-(1/2) Delta(-2 ln E)); members without a proper
         *  Gaussian prior (unregularized, L1) get weight 0. Falls back to EQUAL if none has one. */
        EVIDENCE
    }

    /** exclude members whose P(r) is significantly negative after the peak (z below -NEG_SIG_ALLOWED) */
    public static boolean GATE_SIGNIFICANT_NEGATIVE = true;
    /**
     * exclude members whose P(r) oscillates more than the others: prOscillation (total variation
     * beyond one rise and one fall, in units of 2 x peak) above GATE_OSCILLATION_FLOOR AND above
     * GATE_OSCILLATION_FACTOR x the median over members. Relative, because real features of P(r)
     * (domains, mixtures) give every method some up-and-down travel; only the method that adds
     * ripples of its own is dropped. Infinity disables the gate.
     */
    public static double GATE_OSCILLATION_FLOOR = 0.05, GATE_OSCILLATION_FACTOR = 1.5;
    /** Simpson sub-intervals per bin for the bin-average gradient */
    public static int GRADIENT_SUBDIVISIONS = 32;

    public final IndirectFT[] members;
    public final String[] names;
    public final double[] weights;          // per member, sums to 1
    public final boolean[] included;        // passed the gate
    public final String[] exclusionReason;  // null if included
    public final double[] minusTwoLogEvidence;

    public final int nBins;
    public final double binWidth;
    public final double[] r;                 // bin mid-points
    public final double[][] memberBins;      // [member][bin]
    public final double[][] memberSigma;     // [member][bin], within-method sd
    public final double[] mean, sigma, withinSigma, betweenSigma;
    public final double[][] covariance;

    public EnsembleIFT(List<? extends IndirectFT> fits, List<String> labels, Weighting weighting) {
        int M = fits.size();
        members = fits.toArray(new IndirectFT[0]);
        names = labels.toArray(new String[0]);
        if (names.length != M) throw new IllegalArgumentException("one label per fit");

        nBins = members[0].prDistributionForFitting.getItemCount();
        for (int m = 1; m < M; m++) {
            int n = members[m].prDistributionForFitting.getItemCount();
            if (n != nBins) throw new IllegalArgumentException(names[m] + " has " + n + " Shannon bins, "
                    + names[0] + " has " + nBins + " (same dmax/qmax? SVD.MODEL_FITTED_BINS_ONLY = false?)");
        }
        binWidth = Math.PI/members[0].qmax;
        r = new double[nBins];
        for (int k = 0; k < nBins; k++) r[k] = members[0].prDistributionForFitting.getX(k).doubleValue();

        memberBins = new double[M][nBins];
        memberSigma = new double[M][nBins];
        double[][][] memberCov = new double[M][][];
        for (int m = 0; m < M; m++) {
            for (int k = 0; k < nBins; k++) memberBins[m][k] = members[m].prDistributionForFitting.getY(k).doubleValue();
            memberCov[m] = binCovariance(members[m], nBins, binWidth);
            for (int k = 0; k < nBins; k++) memberSigma[m][k] = Math.sqrt(Math.max(0, memberCov[m][k][k]));
        }

        // quality gate
        included = new boolean[M];
        exclusionReason = new String[M];
        int nIn = 0;
        double[] osc = new double[M];
        for (int m = 0; m < M; m++) osc[m] = members[m].prOscillation;
        double[] sortedOsc = osc.clone();
        java.util.Arrays.sort(sortedOsc);
        double medianOsc = (M % 2 == 1) ? sortedOsc[M/2] : 0.5*(sortedOsc[M/2 - 1] + sortedOsc[M/2]);
        double oscLimit = Math.max(GATE_OSCILLATION_FLOOR, GATE_OSCILLATION_FACTOR*medianOsc);
        for (int m = 0; m < M; m++) {
            double z = members[m].prNegZ;
            if (GATE_SIGNIFICANT_NEGATIVE && !Double.isNaN(z) && z < -IndirectFT.NEG_SIG_ALLOWED) {
                exclusionReason[m] = String.format("significant negative P(r), z = %.1f", z);
            } else if (osc[m] > oscLimit) {
                exclusionReason[m] = String.format("oscillating P(r), osc = %.3f > %.3f", osc[m], oscLimit);
            } else { included[m] = true; nIn++; }
        }
        if (nIn == 0) {                       // never leave the ensemble empty
            for (int m = 0; m < M; m++) { included[m] = true; exclusionReason[m] = null; }
            nIn = M;
        }

        // weights
        double cHat = Double.POSITIVE_INFINITY;
        for (IndirectFT f : members) cHat = Math.min(cHat, f.getReducedChiSquaredAllPoints());
        minusTwoLogEvidence = new double[M];
        for (int m = 0; m < M; m++) minusTwoLogEvidence[m] = members[m].getMinusTwoLogEvidence(cHat);
        weights = new double[M];
        boolean done = false;
        if (weighting == Weighting.EVIDENCE) {
            double best = Double.POSITIVE_INFINITY;
            for (int m = 0; m < M; m++) if (included[m] && !Double.isNaN(minusTwoLogEvidence[m])) best = Math.min(best, minusTwoLogEvidence[m]);
            if (best < Double.POSITIVE_INFINITY) {
                double s = 0;
                for (int m = 0; m < M; m++) {
                    if (included[m] && !Double.isNaN(minusTwoLogEvidence[m])) { weights[m] = Math.exp(-0.5*(minusTwoLogEvidence[m] - best)); s += weights[m]; }
                }
                for (int m = 0; m < M; m++) weights[m] /= s;
                done = true;
            }
        }
        if (!done) for (int m = 0; m < M; m++) weights[m] = included[m] ? 1.0/nIn : 0.0;

        // combination
        mean = new double[nBins];
        for (int m = 0; m < M; m++) for (int k = 0; k < nBins; k++) mean[k] += weights[m]*memberBins[m][k];
        covariance = new double[nBins][nBins];
        double[][] within = new double[nBins][nBins];
        for (int m = 0; m < M; m++) {
            if (weights[m] == 0) continue;
            for (int k = 0; k < nBins; k++) {
                double dk = memberBins[m][k] - mean[k];
                for (int l = 0; l < nBins; l++) {
                    within[k][l] += weights[m]*memberCov[m][k][l];
                    covariance[k][l] += weights[m]*(memberCov[m][k][l] + dk*(memberBins[m][l] - mean[l]));
                }
            }
        }
        sigma = new double[nBins];
        withinSigma = new double[nBins];
        betweenSigma = new double[nBins];
        for (int k = 0; k < nBins; k++) {
            sigma[k] = Math.sqrt(Math.max(0, covariance[k][k]));
            withinSigma[k] = Math.sqrt(Math.max(0, within[k][k]));
            betweenSigma[k] = Math.sqrt(Math.max(0, covariance[k][k] - within[k][k]));
        }
    }

    /** the combined Shannon-binned P(r), same units and grid as prDistributionForFitting */
    public XYSeries getDistributionForFitting() {
        XYSeries s = new XYSeries("EnsemblePrDistribution");
        for (int k = 0; k < nBins; k++) s.add(r[k], mean[k]);
        return s;
    }

    /** total sd per bin (within + between) as a series on the bin mid-points */
    public XYSeries getDistributionSigma() {
        XYSeries s = new XYSeries("EnsemblePrSigma");
        for (int k = 0; k < nBins; k++) s.add(r[k], sigma[k]);
        return s;
    }

    /** true when model choice, not data noise, dominates the bin */
    public boolean isPriorDominated(int k) { return betweenSigma[k] > withinSigma[k]; }

    /** weighted-average model q*I(q) at q (same units as the data passed to the members) */
    public double modelQIQ(double q) {
        double model = 0;
        for (int m = 0; m < members.length; m++) if (weights[m] > 0) model += weights[m]*members[m].calculateQIQ(q);
        return model;
    }

    /** chi^2 of the weighted-average model I(q) against q*I(q) data with sd q*sigma(q) */
    public double chiSquared(XYSeries qIq, XYSeries qIqSigma) {
        double chi = 0;
        for (int i = 0; i < qIq.getItemCount(); i++) {
            double q = qIq.getX(i).doubleValue(), s = qIqSigma.getY(i).doubleValue();
            if (!(s > 0)) continue;
            double model = 0;
            for (int m = 0; m < members.length; m++) if (weights[m] > 0) model += weights[m]*members[m].calculateQIQ(q);
            double z = (qIq.getY(i).doubleValue() - model)/s;
            chi += z*z;
        }
        return chi;
    }

    /** chi^2 of one member on the same footing as chiSquared */
    public double memberChiSquared(int m, XYSeries qIq, XYSeries qIqSigma) {
        double chi = 0;
        for (int i = 0; i < qIq.getItemCount(); i++) {
            double q = qIq.getX(i).doubleValue(), s = qIqSigma.getY(i).doubleValue();
            if (!(s > 0)) continue;
            double z = (qIq.getY(i).doubleValue() - members[m].calculateQIQ(q))/s;
            chi += z*z;
        }
        return chi;
    }

    /** model-averaged scalar (e.g. I(0), Rg, V_p): mean, and sd = sqrt(within^2 + between^2) */
    public IndirectFT.Estimate combine(IndirectFT.Estimate[] perMember) {
        double mu = 0;
        for (int m = 0; m < members.length; m++) if (weights[m] > 0) mu += weights[m]*perMember[m].value;
        double var = 0;
        boolean known = true;
        for (int m = 0; m < members.length; m++) {
            if (weights[m] == 0) continue;
            double s = perMember[m].sigma;
            if (Double.isNaN(s)) { known = false; s = 0; }
            double d = perMember[m].value - mu;
            var += weights[m]*(s*s + d*d);
        }
        return new IndirectFT.Estimate(mu, Math.sqrt(var));
    }

    public IndirectFT.Estimate getIzeroParticle() {
        IndirectFT.Estimate[] e = new IndirectFT.Estimate[members.length];
        for (int m = 0; m < members.length; m++) e[m] = members[m].getIzeroParticle();
        return combine(e);
    }

    public IndirectFT.Estimate getRg() {
        IndirectFT.Estimate[] e = new IndirectFT.Estimate[members.length];
        for (int m = 0; m < members.length; m++) e[m] = new IndirectFT.Estimate(members[m].getRg(), members[m].getRgError());
        return combine(e);
    }

    public IndirectFT.Estimate getPorodVolume(double qLimit) {
        IndirectFT.Estimate[] e = new IndirectFT.Estimate[members.length];
        for (int m = 0; m < members.length; m++) e[m] = members[m].getPorodVolume(qLimit);
        return combine(e);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (int m = 0; m < members.length; m++) {
            sb.append(String.format("  %-12s weight %.3f  -2lnE %s  prScore %7.2f  z_min %+5.1f  osc %.3g  rough %.3g%s%n", names[m], weights[m],
                    Double.isNaN(minusTwoLogEvidence[m]) ? "   n/a  " : String.format("%8.1f", minusTwoLogEvidence[m]),
                    members[m].getPrScore(), members[m].prNegZ, members[m].prOscillation, members[m].prRoughness,
                    exclusionReason[m] == null ? "" : "  EXCLUDED: " + exclusionReason[m]));
        }
        int prior = 0;
        for (int k = 0; k < nBins; k++) if (isPriorDominated(k)) prior++;
        sb.append(String.format("  %d of %d bins dominated by model choice (between sd > within sd)%n", prior, nBins));
        return sb.toString();
    }

    /* gradient of every bin average with respect to the coefficients, then g C g' */
    static double[][] binCovariance(IndirectFT f, int nb, double B) {
        double[][] C = f.coefficientCovariance;
        double[][] cov = new double[nb][nb];
        if (C == null || f.prDistributionBasis == null || f.prDistribution == null) {
            for (int k = 0; k < nb; k++) cov[k][k] = Double.NaN;
            return cov;
        }
        int K = C.length;
        int n = f.prDistribution.getItemCount();
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = f.prDistribution.getX(i).doubleValue();
        double toPofR = f.plottingToPofRScale();
        int msub = GRADIENT_SUBDIVISIONS + (GRADIENT_SUBDIVISIONS & 1);

        double[][] g = new double[nb][K];
        for (int k = 0; k < nb; k++) {
            double lo = k*B, hi = Math.min((k + 1)*B, f.dmax), h = (hi - lo)/msub;
            for (int j = 0; j <= msub; j++) {
                double rr = lo + j*h;
                double w = ((j == 0 || j == msub) ? 1 : ((j & 1) == 1 ? 4 : 2))*h/3.0/B*toPofR;
                int i = 1;
                while (i < n - 1 && x[i] < rr) i++;            // x[i-1] < rr <= x[i]
                double t = (x[i] > x[i - 1]) ? (rr - x[i - 1])/(x[i] - x[i - 1]) : 0;
                t = Math.max(0, Math.min(1, t));
                addScaled(g[k], f.prDistributionBasis[i - 1], w*(1 - t));
                addScaled(g[k], f.prDistributionBasis[i], w*t);
            }
        }
        for (int k = 0; k < nb; k++) {
            double[] Cg = new double[K];
            for (int p = 0; p < K; p++) { double s = 0; for (int q = 0; q < K; q++) s += C[p][q]*g[k][q]; Cg[p] = s; }
            for (int l = k; l < nb; l++) {
                double s = 0;
                for (int p = 0; p < K; p++) s += g[l][p]*Cg[p];
                cov[k][l] = cov[l][k] = s;
            }
        }
        return cov;
    }

    private static void addScaled(double[] acc, double[] b, double w) {
        if (b == null || w == 0) return;
        int n = Math.min(acc.length, b.length);
        for (int i = 0; i < n; i++) acc[i] += w*b[i];
    }
}
