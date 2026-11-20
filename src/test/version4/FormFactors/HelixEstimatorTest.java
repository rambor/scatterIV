package version4.FormFactors;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/** Starting values for infinitely long helices from the data. */
public class HelixEstimatorTest {

    @Test
    public void nnlsKeepsWeightsNonNegative() {
        // b = 2 a0 + 0 a1 with a1 anti-correlated: unconstrained LS would make x1 negative
        double[][] A = {{1, 1}, {1, -1}, {1, 0.5}, {1, -0.2}};
        double[] b = {2, 2.5, 2, 2.2};
        double[] x = HelixEstimator.nnls(A, b, 4);
        assertTrue(x[0] > 0);
        assertEquals(0.0, x[1], 1e-12);
    }

    @Test
    public void recoversASimulatedDoubleHelix() {
        // truth: 2 strands, radii 60 and 80 A, pitch 90 A, strand radius 12 A, infinitely long
        int m = 200;
        double[] q = new double[m], I = new double[m], s = new double[m];
        Random r = new Random(4);
        InfiniteHelixKernel k = new InfiniteHelixKernel(90, 2, 180, 12, 10, 300);
        for (int j = 0; j < m; j++) {
            q[j] = 0.01 + j * (0.30 - 0.01) / (m - 1);
            double v = 0.6 * k.formFactor(q[j], 60) + 0.4 * k.formFactor(q[j], 80);
            double it = 1000 * v + 0.5;
            s[j] = 0.02 * it;
            I[j] = it + s[j] * r.nextGaussian();
        }
        HelixEstimator.Estimate e = HelixEstimator.estimate(q, I, s, null);
        assertEquals(2, e.strands);
        assertTrue("pitch visible", e.pitchVisible);
        assertEquals(90, e.pitch, 3);
        assertEquals(12, e.strandRadius, 3);
        assertTrue("r range " + e.rMin + "-" + e.rMax, e.rMin <= 60 && e.rMax >= 80);
        assertEquals("keeps the full q range", q[m - 1], e.qmax, 1e-9);
        assertTrue("chi2 " + e.chi2, e.chi2 < 2);
    }

    @Test
    public void latticeStructureFactorMatchesDebye() {
        // infinite helical lattice (layer lines) against an exact Debye sum over 2000 point subunits
        double r = 30, rise = 10, twist = 360.0 / 7, pitch = 360 * rise / twist;
        for (int st = 1; st <= 2; st++) {
            double[] psi = HelicalLattice.symmetricStrands(st);
            SubunitHelixKernel debye = new SubunitHelixKernel(r, rise, twist, st, 0, 50, 20000);
            double n = 20000 / rise;
            for (double q : new double[]{0.2, 0.3, 0.6, 0.9, 1.5}) {
                double s = HelicalLattice.structureFactor(q, r, pitch, rise, psi), d = debye.formFactor(q, n) * n * st;
                assertEquals("strands " + st + " q " + q, d, s, 0.01 * d);
            }
        }
    }

    @Test
    public void ellipsoidAverageReducesToSphere() {
        for (double q : new double[]{0.05, 0.2, 0.4}) {
            double x = q * 20, f = 3 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
            double[] e = HelicalLattice.ellipsoid(q, 20, 20);
            assertEquals(f * f, e[0], 1e-9);
            assertEquals(e[0], e[1], 1e-9);                 // beta = 1 for a sphere
        }
        double[] prolate = HelicalLattice.ellipsoid(0.2, 15, 45);
        assertTrue(prolate[1] < prolate[0]);                 // beta < 1 for anisotropic subunits
    }

    @Test
    public void recoversEllipsoidalSubunitsOnAKnownLattice() {
        int m = 300;
        double[] q = new double[m], I = new double[m], s = new double[m];
        Random rnd = new Random(7);
        EllipsoidHelixKernel k = new EllipsoidHelixKernel(100, 12, 1, 180, 20, 45, 50, 400);
        for (int j = 0; j < m; j++) {
            q[j] = 0.011 + j * (0.32 - 0.011) / (m - 1);
            double v = 0.6 * k.formFactor(q[j], 120) + 0.4 * k.formFactor(q[j], 150);
            double it = 1e5 * v + 0.1;
            s[j] = 0.02 * it;
            I[j] = it + s[j] * rnd.nextGaussian();
        }
        HelixEstimator.SubunitEstimate e = HelixEstimator.estimateSubunits(q, I, s, 100, 12, 1, Double.NaN, 0.2, null);
        assertEquals(20, e.a, 2);
        assertEquals(45, e.c, 4);
        assertTrue("r range " + e.rMin + "-" + e.rMax, e.rMin <= 120 && e.rMax >= 150);
        assertTrue("chi2 " + e.chi2, e.chi2 < 3);
        HelixEstimator.SubunitEstimate wrong = HelixEstimator.estimateSubunits(q, I, s, 90, 10, 1, Double.NaN, 0.2, null);
        assertTrue("wrong lattice fits worse", wrong.chi2 > 10 * e.chi2);
    }

    @Test
    public void recoversFiniteFilaments() {
        // truth: one 1-start helix, r 40 A, strand radius 25 A, pitch 120 A, lengths 300-1000 A (finite: the low q bends)
        int m = 180;
        double[] q = new double[m], I = new double[m], s = new double[m];
        Random r = new Random(7);
        HelixMath.Geometry g = HelixMath.Geometry.symmetric(40, 120, 25, 1);
        double[] L = {300, 450, 650, 1000}, w = {0.2, 0.35, 0.3, 0.15};
        for (int j = 0; j < m; j++) {
            q[j] = 0.006 * Math.pow(0.18 / 0.006, j / (m - 1.0));
            double v = 0;
            for (int k = 0; k < L.length; k++) v += w[k] * HelixMath.formFactor(g, q[j], L[k]);
            double it = 1000 * v + 0.05;
            s[j] = 0.01 * it + 0.002;
            I[j] = it + s[j] * r.nextGaussian();
        }
        HelixEstimator.Estimate e = HelixEstimator.estimate(q, I, s, null);
        assertTrue("finite model chosen: " + e.summary(), e.finite);
        assertEquals("helix radius", 40, e.helixRadius, 4);
        assertEquals("strand radius", 25, e.strandRadius, 4);
        assertTrue("median length " + e.lMedian, e.lMedian > 300 && e.lMedian < 1000);
        assertTrue("chi2 " + e.chi2, e.chi2 < 2);
        assertTrue("finite fits better than infinite", e.chi2Infinite > 3 * e.chi2);
    }

    @Test
    public void finiteChiPrefersTheTrueCrossSection() {
        int m = 150;
        double[] q = new double[m], I = new double[m], s = new double[m];
        HelixMath.Geometry g = HelixMath.Geometry.symmetric(45, 100, 30, 2);
        for (int j = 0; j < m; j++) {
            q[j] = 0.008 * Math.pow(0.15 / 0.008, j / (m - 1.0));
            I[j] = 1000 * HelixMath.formFactor(g, q[j], 800);
            s[j] = 0.01 * I[j] + 1e-3;
        }
        double truth = HelixEstimator.finiteChi(q, I, s, 2, 100, 45, 30);
        assertTrue("truth fits: " + truth, truth < 0.01);
        assertTrue("other radius fits worse", HelixEstimator.finiteChi(q, I, s, 2, 100, 52, 30) > 100 * truth + 1);
    }
}
