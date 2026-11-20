package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/** Continuous helix form factor (van der Maarel 2023) and the subunit helix. */
public class HelixMathTest {

    /** exact rod: 2 Si(x)/x - sinc^2(x/2), x = qL */
    private static double rod(double x) {
        double si = 0;
        int n = 100000;
        double h = x / n;
        for (int i = 0; i < n; i++) { double t = (i + 0.5) * h; si += Math.sin(t) / t * h; }
        double s = Math.sin(x / 2) / (x / 2);
        return 2 * si / x - s * s;
    }

    @Test
    public void besselValues() {
        assertEquals(0.4400505857, HelixMath.besselJ(1, 1.0), 1e-9);
        assertEquals(-0.2340615282, HelixMath.besselJ(5, 10.0), 1e-9);
        assertEquals(1.551e-12, HelixMath.besselJ(30, 10.0), 1e-14);
        assertEquals(0.0, HelixMath.besselJ(0, 2.404825557695773), 1e-9);
    }

    @Test
    public void zeroRadiusIsARod() {
        HelixMath.Geometry g = HelixMath.Geometry.symmetric(0, 20, 0, 1);
        for (double x : new double[]{1, 5, 20, 59, 150, 400, 900})
            assertEquals("qL=" + x, rod(x), HelixMath.formFactor(g, x / 250, 250), 2e-4 * rod(x) + 1e-7);
    }

    @Test
    public void lowQGivesRadiusOfGyration() {
        HelixMath.Geometry g = HelixMath.Geometry.symmetric(5, 20, 0, 1);
        double q = 2e-4, L = 250;
        double rg = Math.sqrt(3 * (1 - HelixMath.formFactor(g, q, L)) / (q * q));
        assertEquals(g.radiusOfGyration(L), rg, 0.05);
    }

    @Test
    public void strandFactors() {
        HelixMath.Geometry two = HelixMath.Geometry.symmetric(5, 20, 0, 2);
        assertEquals(0, two.strandFactor(1), 1e-12);
        assertEquals(1, two.strandFactor(2), 1e-12);
        HelixMath.Geometry grooves = HelixMath.Geometry.doubleHelix(5, 20, 0, Math.toRadians(120));
        assertEquals(Math.pow(Math.cos(Math.toRadians(60)), 2), grooves.strandFactor(1), 1e-12);   // eq. 28
    }

    @Test
    public void continuousHelixIsTheLimitOfDenseSubunits() {
        double rise = 0.25, twist = 360 * rise / 20;
        for (int strands = 1; strands <= 2; strands++) {
            HelixMath.Geometry g = HelixMath.Geometry.symmetric(5, 20, 0, strands);
            SubunitHelixKernel sub = new SubunitHelixKernel(5, rise, twist, strands, 0, 50, 400);
            for (double q : new double[]{0.02, 0.05, 0.1, 0.2, 0.3, 0.4, 0.8}) {
                double c = HelixMath.formFactor(g, q, 250), d = sub.formFactor(q, 250 / rise);
                assertEquals("strands " + strands + " q " + q, d, c, 2e-3 * d + 2e-6);
            }
        }
    }

    @Test
    public void kernelTableAndDerivative() {
        HelixMath.Geometry g = HelixMath.Geometry.symmetric(10, 35, 4, 2);
        double[] q = new double[60];
        for (int j = 0; j < q.length; j++) q[j] = 0.01 + j * 0.004;
        HelixKernel k = new HelixKernel(g, 50, 1500);
        double[] P = new double[q.length], dP = new double[q.length], P1 = new double[q.length], P2 = new double[q.length];
        double L = 300, e = 1e-4;
        k.evaluate(new double[]{Math.log(L)}, 0, q, P, 0, dP, 0, null);
        k.evaluate(new double[]{Math.log(L) + e}, 0, q, P1, 0, new double[q.length], 0, null);
        k.evaluate(new double[]{Math.log(L) - e}, 0, q, P2, 0, new double[q.length], 0, null);
        for (int j = 0; j < q.length; j++) {
            double direct = HelixMath.formFactor(g, q[j], L);
            assertEquals("P at q " + q[j], direct, P[j], 5e-3 * direct + 1e-6);
            assertEquals("dP at q " + q[j], (P1[j] - P2[j]) / (2 * e), dP[j], 1e-3 * Math.abs(dP[j]) + 1e-7);
        }
    }

    @Test
    public void infiniteHelixIsTheLongFilamentLimitPerUnitLength() {
        InfiniteHelixKernel inf = new InfiniteHelixKernel(35, 2, 180, 4, 5, 50);
        HelixMath.Geometry g = HelixMath.Geometry.symmetric(10, 35, 4, 2);
        double L = 20000;
        for (double q : new double[]{0.02, 0.05, 0.1, 0.2, 0.3}) {
            // finite form factor x L -> infinite form factor x L_ref (the -2/(qL)^2 end term vanishes)
            double finite = HelixMath.formFactor(g, q, L) * L, infinite = inf.formFactor(q, 10) * InfiniteHelixKernel.L_REF;
            assertEquals("q " + q, finite, infinite, 0.01 * infinite);
        }
    }
}
