package version4.SEC;

import org.jfree.data.xy.XYSeries;
import version4.Collection;

import java.util.Random;
import java.util.function.IntToDoubleFunction;

/**
 * Simulated SEC-SAXS runs with a known answer: a buffer, and a particle of known Rg eluting as a
 * Gaussian peak, with Gaussian noise of known size. Frame f has intensity buffer(q) + c(f) P(q).
 */
final class SecRunFixture {

    private SecRunFixture() {}

    static final int FRAMES = 240, TOTAL_Q = 150;
    static final double PEAK_FRAME = 130, PEAK_WIDTH = 10, PEAK_HEIGHT = 2.0;
    /** sphere radius giving Rg = 30 A */
    static final double RADIUS = 30 / Math.sqrt(3.0 / 5.0), RG = 30;
    static final double NOISE = 0.004;

    static double q(int k) { return 0.008 + k * (0.3 - 0.008) / (TOTAL_Q - 1); }

    static double buffer(double q) { return 1.0 + 0.3 * Math.exp(-10 * q); }

    /** normalised sphere form factor, P(0) = 1 */
    static double particle(double q) {
        double x = q * RADIUS;
        if (x < 1e-6) return 1;
        double f = 3 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
        return f * f;
    }

    /** elution profile: concentration (as I(0)) of frame f */
    static double concentration(int f) {
        double z = (f - PEAK_FRAME) / PEAK_WIDTH;
        return PEAK_HEIGHT * Math.exp(-0.5 * z * z);
    }

    static double sigma(double q) { return NOISE * buffer(q); }

    static Collection run() { return run(FRAMES, SecRunFixture::concentration, 42); }

    static Collection run(int frames, IntToDoubleFunction conc, long seed) {
        Random r = new Random(seed);
        Collection c = new Collection("sec");
        for (int f = 0; f < frames; f++) {
            XYSeries d = new XYSeries("f" + f), e = new XYSeries("e" + f);
            double cf = conc.applyAsDouble(f);
            for (int k = 0; k < TOTAL_Q; k++) {
                double q = q(k), s = sigma(q);
                d.add(q, buffer(q) + cf * particle(q) + s * r.nextGaussian());
                e.add(q, s);
            }
            c.createDataset(d, e);
        }
        return c;
    }

    static SecProcessor processor(Collection c, double threshold) {
        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(c);
        if (!common.isAcceptable()) throw new IllegalStateException(common.describeProblem());
        return new SecProcessor(c, common.qvalues, threshold, 0, null);
    }

    /** the signal a frame of concentration c should have: mean over q of (buffer + c P) / buffer */
    static double expectedSignal(double c) {
        // trapezoid over the q grid, normalised as SecProcessor normalises (range * (n+1)/n)
        double sum = 0;
        for (int k = 1; k < TOTAL_Q; k++) {
            double q0 = q(k - 1), q1 = q(k);
            double r0 = 1 + c * particle(q0) / buffer(q0), r1 = 1 + c * particle(q1) / buffer(q1);
            sum += 0.5 * (r0 + r1) * (q1 - q0);
        }
        double range = q(TOTAL_Q - 1) - q(0);
        return sum / (range * (TOTAL_Q + 1) / (double) TOTAL_Q);
    }
}
