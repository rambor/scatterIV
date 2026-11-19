package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Reading the chain-packing region. The distinction the wide-angle data settles — melted chains against ordered
 * ones — is invisible to SAXS, so these check that each state is recognised and, as importantly, that ordinary
 * background structure is not mistaken for a packing line.
 */
public class LnpWaxsTest {

    /** a chain-packing peak of given position and width on a rising background */
    private static double[][] curve(double d, double fwhm, double amplitude, long seed) {
        int n = 1000;
        double[] q = new double[n], I = new double[n], s = new double[n];
        java.util.Random rnd = new java.util.Random(seed);
        double qc = 2 * Math.PI / d, sigma = fwhm / 2.355;
        for (int i = 0; i < n; i++) {
            q[i] = 0.7 + (1.95 - 0.7) * i / (n - 1.0);
            double v = 1.0e-5 * Math.pow(q[i] / 0.7, 1.4);                       // rising background
            v += amplitude * Math.exp(-0.5 * Math.pow((q[i] - qc) / sigma, 2));
            s[i] = 0.03 * v;
            I[i] = v + s[i] * rnd.nextGaussian();
        }
        return new double[][]{q, I, s};
    }

    /** A narrow line near 4.1 Å is a gel. */
    @Test
    public void recognisesAGelPhase() {
        double[][] d = curve(4.13, 0.10, 1.8e-4, 3);
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        assertEquals(LnpWaxs.Packing.GEL, r.packing);
        assertEquals("the spacing", 4.13, r.main().d, 0.05);
        assertTrue("the text should say the chains are ordered", LnpWaxs.describe(r, false).contains("ORDERED"));
    }

    /** A broad halo near 4.5 Å is a fluid bilayer. */
    @Test
    public void recognisesAFluidPhase() {
        double[][] d = curve(4.5, 0.32, 1.2e-4, 5);
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        assertEquals(LnpWaxs.Packing.FLUID, r.packing);
        assertTrue(LnpWaxs.describe(r, false).contains("MELTED"));
        assertTrue("and it should admit the fluid / liquid-ordered boundary is close",
                LnpWaxs.describe(r, false).contains("too close to separate"));
    }

    /** A broad halo pushed out to 4.6 Å is the liquid-ordered state a sterol produces. */
    @Test
    public void recognisesLiquidOrdered() {
        double[][] d = curve(4.62, 0.30, 1.2e-4, 7);
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        assertEquals(LnpWaxs.Packing.LIQUID_ORDERED, r.packing);
    }

    /**
     * A regression from the real measurement. A broad shoulder of the background sits a plausible distance from
     * the packing line, and counting it as a second reflection turned an ordinary gel into a tilted one. A real
     * second reflection is as sharp as the first.
     */
    @Test
    public void aBroadShoulderIsNotASecondReflection() {
        double[][] d = curve(4.13, 0.10, 1.8e-4, 11);
        // add a wide shoulder at 4.8 Å, as the rising background produces
        double qs = 2 * Math.PI / 4.8, sig = 0.65 / 2.355;
        for (int i = 0; i < d[0].length; i++)
            d[1][i] += 3.0e-5 * Math.exp(-0.5 * Math.pow((d[0][i] - qs) / sig, 2));
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        assertEquals("still a plain gel", LnpWaxs.Packing.GEL, r.packing);
        assertEquals("the spacing is unchanged", 4.13, r.main().d, 0.08);
    }

    /** Over-subtracted data must be cut off rather than read as structure. */
    @Test
    public void overSubtractedTailIsNotRead() {
        double[][] d = curve(4.13, 0.10, 1.8e-4, 13);
        for (int i = 0; i < d[0].length; i++) if (d[0][i] > 1.75) d[1][i] = -2e-5;
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        assertTrue("it should stop where the data goes negative", r.usableTo < 1.8);
        assertTrue("and say so", LnpWaxs.describe(r, false).contains("over-subtracted"));
        assertEquals(LnpWaxs.Packing.GEL, r.packing);
    }

    /** A SAXS dataset handed to the wide-angle reader must be declined, not analysed. */
    @Test
    public void aSmallAngleDatasetIsDeclined() {
        int n = 400;
        double[] q = new double[n], I = new double[n], s = new double[n];
        for (int i = 0; i < n; i++) { q[i] = 0.005 + 0.3 * i / (n - 1.0); I[i] = 1.0 / (1 + i); s[i] = 0.02 * I[i]; }
        LnpWaxs.Result r = LnpWaxs.analyse(q, I, s);
        assertEquals(LnpWaxs.Packing.NONE, r.packing);
        assertTrue(LnpWaxs.describe(r, false).contains("does not reach the chain-packing region"));
    }

    /** The peak is drawn as it was measured, so the curve has to sit on the data it came from. */
    @Test
    public void theDrawnPeakMatchesTheMeasuredOne() {
        double[][] d = curve(4.13, 0.10, 1.8e-4, 23);
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        LnpWaxs.Peak p = r.main();
        assertNotNull(p);
        assertEquals("at its centre the drawn curve is the baseline plus the full height",
                p.baseline + p.height, p.curveAt(p.q), 1e-12);
        assertEquals("one FWHM away it has fallen to half", p.baseline + 0.5 * p.height,
                p.curveAt(p.q + p.fwhm / 2), 0.02 * p.height);
        assertTrue("and far away it returns to the baseline",
                Math.abs(p.curveAt(p.q + 5 * p.fwhm) - p.baseline) < 0.01 * p.height);
        assertTrue("the baseline should be positive here", p.baseline > 0);
    }

    /** The reference spacings on the chart must be the ones the classifier actually uses. */
    @Test
    public void chartReferencesCoverTheStatesClassified() {
        assertEquals(3, LnpWaxs.REFERENCES.length);
        double gel = (Double) LnpWaxs.REFERENCES[0][0], fluid = (Double) LnpWaxs.REFERENCES[1][0],
                lo = (Double) LnpWaxs.REFERENCES[2][0];
        assertTrue("they should be ordered by spacing", gel < fluid && fluid < lo);
        // a peak at each reference must be classified as that state
        assertEquals(LnpWaxs.Packing.GEL, LnpWaxs.analyse(curve(gel, 0.10, 1.8e-4, 31)[0],
                curve(gel, 0.10, 1.8e-4, 31)[1], curve(gel, 0.10, 1.8e-4, 31)[2]).packing);
        double[][] f = curve(fluid, 0.30, 1.2e-4, 37);
        assertEquals(LnpWaxs.Packing.FLUID, LnpWaxs.analyse(f[0], f[1], f[2]).packing);
        double[][] l = curve(lo, 0.30, 1.2e-4, 41);
        assertEquals(LnpWaxs.Packing.LIQUID_ORDERED, LnpWaxs.analyse(l[0], l[1], l[2]).packing);
    }

    /** A sharp line with a sterol declared is a contradiction worth flagging. */
    @Test
    public void sharpLineWithASterolIsFlagged() {
        double[][] d = curve(4.13, 0.10, 1.8e-4, 17);
        LnpWaxs.Result r = LnpWaxs.analyse(d[0], d[1], d[2]);
        assertTrue(LnpWaxs.describe(r, true).contains("not what a"));
    }
}
