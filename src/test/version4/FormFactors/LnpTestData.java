package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Synthetic curves for the LNP tests. Everything here is generated from a known truth so the tests can assert the
 * value that comes back, and the published numbers each case reproduces are named in the test that uses it.
 */
public final class LnpTestData {

    private LnpTestData() { }

    /** fractional noise added to every synthetic curve */
    public static final double NOISE = 0.02;

    /** {q, I, sigma} from a model at known parameters, log-spaced, with Gaussian noise */
    public static double[][] fromModel(LnpModel m, double[] p, double qlo, double qhi, int n, long seed) {
        Random r = new Random(seed);
        double[] q = new double[n], s = new double[n];
        for (int i = 0; i < n; i++) q[i] = qlo * Math.pow(qhi / qlo, i / (double) (n - 1));
        double[] I = m.intensity(p, q);
        for (int i = 0; i < n; i++) {
            s[i] = NOISE * I[i];
            I[i] += s[i] * r.nextGaussian();
        }
        return new double[][]{q, I, s};
    }

    /**
     * A Bragg pattern: every allowed reflection of one lattice as a Gaussian of the width a domain of this size
     * would give, on a power-law background.
     *
     * @param orders how many reflections to include
     */
    public static double[][] bragg(LnpLattice.Lattice lattice, double a, double domain, int orders,
                                   double qlo, double qhi, int n, long seed) {
        Random rnd = new Random(seed);
        double[] q = new double[n], I = new double[n], s = new double[n];
        double width = 2 * Math.PI * 0.9 / domain / 2.355;     // Scherrer: FWHM = 2 pi K / D, K = 0.9
        for (int i = 0; i < n; i++) {
            q[i] = qlo + (qhi - qlo) * i / (n - 1.0);
            double background = 0.02 * Math.pow(q[i] / qlo, -2.2) + 0.0005;
            double v = background;
            for (int k = 0; k < Math.min(orders, lattice.m.length); k++) {
                double qc = 2 * Math.PI * lattice.m[k] / a;
                // peaks are scaled against the background where they sit, the way measured reflections are: a first
                // order an order of magnitude above it, the higher orders falling away. Making them absolutely
                // large instead puts the peak forest thousands of times above the baseline, which no real pattern
                // does and which the baseline polynomial cannot follow.
                double local = 0.02 * Math.pow(qc / qlo, -2.2) + 0.0005;
                v += local * 12.0 / (1 + k * k) * Math.exp(-0.5 * Math.pow((q[i] - qc) / width, 2));
            }
            s[i] = NOISE * v;
            I[i] = v + s[i] * rnd.nextGaussian();
        }
        return new double[][]{q, I, s};
    }

    /**
     * A sponge (L3) curve from the Teubner-Strey form at a known repeat and correlation length, on a flat
     * background. The coefficients follow the standard parameterisation: a2 = (k^2 + 1/xi^2)^2,
     * c1 = 2(1/xi^2 - k^2), c2 = 1, with k = 2 pi / d.
     */
    public static double[][] sponge(double d, double xi, double background, double qlo, double qhi, int n, long seed) {
        Random rnd = new Random(seed);
        double k = 2 * Math.PI / d, inv = 1 / xi;
        double a2 = Math.pow(k * k + inv * inv, 2), c1 = 2 * (inv * inv - k * k), c2 = 1;
        double[] q = new double[n], I = new double[n], s = new double[n];
        for (int i = 0; i < n; i++) q[i] = qlo * Math.pow(qhi / qlo, i / (n - 1.0));
        for (int i = 0; i < n; i++) I[i] = 1 / (a2 + c1 * q[i] * q[i] + c2 * Math.pow(q[i], 4));
        double scale = 0.01 / I[0];
        for (int i = 0; i < n; i++) {
            I[i] = I[i] * scale + background;
            s[i] = NOISE * I[i] + 1e-6;
            I[i] += s[i] * rnd.nextGaussian();
        }
        return new double[][]{q, I, s};
    }

    /** a detected peak, as the assessment would report one */
    public static LnpAssessment.Peak peak(double q, double height, double fwhm, double significance) {
        LnpAssessment.Peak p = new LnpAssessment.Peak();
        p.q = q;
        p.height = height;
        p.fwhm = fwhm;
        p.significance = significance;
        p.area = height * fwhm;
        return p;
    }

    /** the first {@code orders} reflections of a lattice as detected peaks */
    public static List<LnpAssessment.Peak> peaksOf(LnpLattice.Lattice lattice, double a, int orders) {
        List<LnpAssessment.Peak> out = new ArrayList<>();
        for (int k = 0; k < Math.min(orders, lattice.m.length); k++) {
            double q = 2 * Math.PI * lattice.m[k] / a;
            out.add(peak(q, 0.9 / (1 + k * k), q / 40, 60 - 10 * k));
        }
        return out;
    }
}
