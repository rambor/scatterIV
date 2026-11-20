package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the measurement could see the particle at all, and what it would have looked like if it were
 * smaller.
 *
 * <p>A vesicle much larger than 2&#960;/q<sub>min</sub> leaves no trace of its size in the data. The fit
 * still returns a radius, and still puts an error bar on it, because the covariance matrix only measures the
 * curvature at the point the fit stopped at; it cannot tell that the point sits on a plain. On real liposome
 * data a fit reported 1516 &plusmn; 14 &#8491; while every radius between 600 and 2500 &#8491; gave the same
 * reduced chi-squared to within 0.13.
 *
 * <p>So this asks the question the error bar cannot: hold everything the fit found about the bilayer, change
 * only the radius, and see how much the curve moves. If a quarter-change in radius moves the curve by less
 * than the error bars, the radius was never measured, whatever the fit printed.
 *
 * <p>Everything here is forward model evaluation. Nothing is refitted, so it is fast enough to redraw as the
 * user moves a control, and it is also the honest upper bound on what is knowable: a real fit has other
 * parameters to absorb the difference, so it can only do worse at telling two radii apart.
 */
final class LnpSizeLimit {

    /** one simulated vesicle: the whole curve, and how far it sits from the fitted one */
    static final class Rung {
        final double r0;
        /** the curve, on the same q grid as the data */
        final double[] intensity;
        /**
         * How many standard deviations, per point, this curve sits from the fitted one after both are scaled
         * to agree where the bilayer dominates. Below about 1 the two are indistinguishable in this data.
         */
        final double sigmaPerPoint;
        /** the fitted radius itself, rather than a simulated alternative to it */
        final boolean isFit;

        Rung(double r0, double[] intensity, double sigmaPerPoint, boolean isFit) {
            this.r0 = r0; this.intensity = intensity; this.sigmaPerPoint = sigmaPerPoint; this.isFit = isFit;
        }
    }

    static final class Result {
        final double[] q;
        final List<Rung> rungs = new ArrayList<>();
        final double qMin;
        /** the longest distance the measurement encodes, 2*pi/q_min */
        final double longestEncoded;
        /** the radius the fit settled on */
        final double fitted;
        /** the smallest radius change, as a fraction, that this data could have detected */
        final double resolvableFraction;
        /** whether the fitted radius is larger than the measurement can see */
        final boolean beyondCamera;

        Result(double[] q, double qMin, double fitted, double resolvableFraction) {
            this.q = q; this.qMin = qMin; this.fitted = fitted;
            this.longestEncoded = 2 * Math.PI / qMin;
            this.resolvableFraction = resolvableFraction;
            this.beyondCamera = fitted > this.longestEncoded;
        }

        /** a plain-language verdict for the panel */
        String verdict() {
            if (beyondCamera)
                return String.format("The fitted radius of %.0f Å is larger than the longest distance this "
                        + "measurement encodes (2π/q_min = %.0f Å). The size is not measured here: the "
                        + "value and its error bar describe where the fit stopped, not the sample.",
                        fitted, longestEncoded);
            if (Double.isNaN(resolvableFraction))
                return String.format("The fitted radius of %.0f Å is inside the window this measurement "
                        + "encodes (up to %.0f Å), but no simulated change in it moved the curve by as much "
                        + "as the error bars. Treat the radius as a lower limit.", fitted, longestEncoded);
            return String.format("The fitted radius of %.0f Å is inside the window this measurement encodes "
                    + "(up to %.0f Å). A change of about %.0f%% would show above the noise, so the radius "
                    + "is a real measurement.", fitted, longestEncoded, 100 * resolvableFraction);
        }
    }

    private LnpSizeLimit() {}

    /** where the bilayer rather than the particle envelope decides the curve, used to put the curves on a common scale */
    private static final double SCALE_AT_Q = 0.10;

    /**
     * Simulates the fitted particle at a ladder of radii.
     *
     * @param model  the fitted model
     * @param p      the fitted parameters; every one but the radius is kept, including the size spread, which
     *               is scaled with the radius so that the ladder is one of sizes and not of polydispersities
     * @param q      the data's own q grid, so the comparison is against what was actually measured
     * @param sigma  the data's own error bars, for the same reason
     * @param ladder the radii to simulate
     */
    static Result analyse(LnpModel model, double[] p, double[] q, double[] sigma, double[] ladder) {
        double fitted = p[LnpModel.R0];
        double relSpread = fitted > 0 ? p[LnpModel.SIGMA] / fitted : 0.15;

        double[] atFit = curve(model, p, fitted, relSpread, q);
        int ref = nearest(q, SCALE_AT_Q);

        List<Rung> built = new ArrayList<>();
        double bestDetectable = Double.NaN;
        for (double r : ladder) {
            double[] I = curve(model, p, r, relSpread, q);
            boolean isFit = Math.abs(r - fitted) < 1e-6 * Math.max(1, fitted);
            double sep = isFit ? 0 : separation(I, atFit, sigma, ref);
            built.add(new Rung(r, I, sep, isFit));
            // the smallest change that would have shown: the closest rung to the fit that still clears the noise
            if (!isFit && sep > 1.0) {
                double frac = Math.abs(r - fitted) / fitted;
                if (Double.isNaN(bestDetectable) || frac < bestDetectable) bestDetectable = frac;
            }
        }
        Result out = new Result(q, q[0], fitted, bestDetectable);
        out.rungs.addAll(built);
        return out;
    }

    /**
     * Root-mean-square discrepancy in units of the error bars, after scaling the two curves to agree where
     * the bilayer dominates.
     *
     * <p>The scaling matters: a bigger vesicle scatters more, and a difference in overall brightness is not a
     * difference in size — the fitted scale factor would absorb it immediately. What is left after scaling is
     * the difference in shape, which is the only thing that carries the radius.
     */
    private static double separation(double[] a, double[] b, double[] sigma, int ref) {
        if (a[ref] <= 0 || b[ref] <= 0) return 0;
        double k = b[ref] / a[ref];
        double sum = 0; int n = 0;
        for (int i = 0; i < a.length; i++) {
            if (!(sigma[i] > 0) || !Double.isFinite(a[i]) || !Double.isFinite(b[i])) continue;
            double d = (k * a[i] - b[i]) / sigma[i];
            sum += d * d; n++;
        }
        return n == 0 ? 0 : Math.sqrt(sum / n);
    }

    private static double[] curve(LnpModel model, double[] p, double r0, double relSpread, double[] q) {
        double[] c = p.clone();
        c[LnpModel.R0] = r0;
        c[LnpModel.SIGMA] = relSpread * r0;      // same polydispersity, so only the size changes
        c[LnpModel.BG] = 0;                      // background is of the instrument, not of the particle
        return model.intensity(c, q);
    }

    private static int nearest(double[] q, double target) {
        int best = 0;
        for (int i = 1; i < q.length; i++) if (Math.abs(q[i] - target) < Math.abs(q[best] - target)) best = i;
        return best;
    }

    /**
     * A ladder of radii spanning down from the fitted one.
     *
     * <p>Geometric rather than linear, because what matters is the ratio: the step from 100 to 200 &#8491;
     * changes the curve out of all recognition and the step from 1400 to 1500 &#8491; changes nothing.
     */
    static double[] ladder(double fitted, int count, double decadesBelow) {
        double[] out = new double[Math.max(2, count)];
        for (int i = 0; i < out.length; i++)
            out[i] = fitted * Math.pow(10, -decadesBelow * (out.length - 1 - i) / (out.length - 1.0));
        return out;
    }
}
