package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The core/multi-shell form factor, and the distinction between the radius a particle has and the radius X-rays
 * can see.
 */
public class LnpModelTest {

    /**
     * A regression. A PEG corona at zero contrast scatters exactly like the buffer around it, so it is invisible:
     * reporting the particle diameter as though the corona were there overstates what the measurement supports.
     * The panel reports {@link LnpModel#visibleOuterRadius} and says which layer it stopped at.
     */
    @Test
    public void aZeroContrastCoronaIsNotCountedInTheVisibleRadius() {
        LnpModel m = new LnpModel(3, false);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 200;
        p[LnpModel.T1] = 20;
        p[LnpModel.T2] = 3;
        p[LnpModel.T3] = 40;             // the corona: thick, and about to be made invisible
        p[LnpModel.RHO_3] = 0;           // contrast-matched to the buffer

        double visible = m.visibleOuterRadius(p);
        assertTrue("the invisible corona must not be counted, got " + visible, visible < 200 + 20 + 3 + 40 - 1);
        assertEquals("the visible edge is the outside of the last layer with contrast", 223, visible, 1.0);
        assertEquals("the corona is layer 3", 3, m.firstInvisibleLayer(p));
    }

    /** With contrast on every layer the visible radius is the full particle. */
    @Test
    public void aContrastedCoronaIsCounted() {
        LnpModel m = new LnpModel(3, false);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 200;
        p[LnpModel.T1] = 20;
        p[LnpModel.T2] = 3;
        p[LnpModel.T3] = 40;
        p[LnpModel.RHO_3] = 0.05;
        assertEquals(263, m.visibleOuterRadius(p), 1.0);
        assertTrue("no layer should be flagged invisible", m.firstInvisibleLayer(p) < 0);
    }

    /** A model with no contrast anywhere scatters nothing, and must not claim a radius. */
    @Test
    public void noContrastAnywhereGivesNoVisibleRadius() {
        LnpModel m = new LnpModel(3, false);
        double[] p = m.templateDefaults();
        for (int k = 0; k <= 3; k++) p[LnpModel.rhoIndex(k)] = 0;
        p[LnpModel.RHO_C] = 0;
        assertEquals(0, m.visibleOuterRadius(p), 1e-9);
    }

    /** Intensity must be positive and fall with q for a particle of this size. */
    @Test
    public void intensityIsPositiveAndDecaying() {
        LnpModel m = new LnpModel(3, true);
        double[] p = m.templateDefaults();
        double[] q = {0.01, 0.02, 0.05, 0.1, 0.2, 0.3};
        double[] I = m.intensity(p, q);
        for (int i = 0; i < q.length; i++) assertTrue("I(" + q[i] + ") must be positive", I[i] > 0);
        assertTrue("intensity should fall from 0.01 to 0.3 A^-1", I[0] > I[I.length - 1]);
    }

    /** Polydispersity smooths the form-factor minima; with none they are deep. */
    @Test
    public void polydispersityDampsTheOscillations() {
        LnpModel m = new LnpModel(1, false);
        double[] sharp = m.templateDefaults(), broad = m.templateDefaults();
        sharp[LnpModel.R0] = 200;
        broad[LnpModel.R0] = 200;
        sharp[LnpModel.SIGMA] = 1;
        broad[LnpModel.SIGMA] = 40;
        int n = 400;
        double[] q = new double[n];
        for (int i = 0; i < n; i++) q[i] = 0.01 + 0.19 * i / (n - 1.0);
        double[] a = m.intensity(sharp, q), b = m.intensity(broad, q);
        assertTrue("a narrow distribution should ripple more than a broad one",
                contrast(a) > contrast(b));
    }

    /** ratio of the largest to the smallest intensity over the second half of the curve */
    private static double contrast(double[] I) {
        double lo = Double.POSITIVE_INFINITY, hi = 0;
        for (int i = I.length / 2; i < I.length; i++) { lo = Math.min(lo, I[i]); hi = Math.max(hi, I[i]); }
        return hi / lo;
    }

    /**
     * A layer of zero thickness is removed from the model. A sample with no PEG-lipid has no corona and the
     * vesicle architecture offers no variant without one, so zero has to be a usable value rather than an error.
     */
    @Test
    public void aZeroThicknessLayerIsSimplyAbsent() {
        LnpModel m = new LnpModel(4, false);
        double[] with = m.templateDefaults(), without = m.templateDefaults();
        without[LnpModel.T4] = 0;
        without[LnpModel.rhoIndex(4)] = 0;

        assertEquals("the outer radius loses exactly that layer",
                m.outerRadius(with) - with[LnpModel.T4], m.outerRadius(without), 1e-9);
        assertEquals("and it is reported as the first layer with nothing in it", 4, m.firstInvisibleLayer(without));

        double[] q = {0.01, 0.05, 0.1, 0.2, 0.3};
        double[] I = m.intensity(without, q);
        for (int i = 0; i < q.length; i++) {
            assertTrue("intensity stays finite and positive at q = " + q[i], I[i] > 0 && !Double.isNaN(I[i]));
        }
        assertTrue("and the radius of gyration is still defined", m.radiusOfGyration(without) > 0);
    }

    /** Removing the outer layer must not disturb the ones inside it. */
    @Test
    public void removingTheOuterLayerLeavesTheRestAlone() {
        LnpModel m = new LnpModel(4, false);
        double[] p = m.templateDefaults();
        p[LnpModel.T4] = 0;
        p[LnpModel.rhoIndex(4)] = 0;
        double inner = p[LnpModel.R0] + p[LnpModel.T1] + p[LnpModel.T2] + p[LnpModel.T3];
        assertEquals("the bilayer still ends where it did", inner, m.outerRadius(p), 1e-9);
        assertEquals("and that is all the data can see", inner, m.visibleOuterRadius(p), 1e-9);
    }

    /** Layer names and the parameter layout must stay in step, since the editor indexes one by the other. */
    @Test
    public void layerNamesMatchTheShellCount() {
        for (int shells = 0; shells <= LnpModel.MAX_SHELLS; shells++) {
            LnpModel m = new LnpModel(shells, false);
            assertEquals("one name per layer plus the core", shells + 1, m.layerNames().length);
            for (int k = 1; k <= shells; k++) {
                assertTrue("thickness index in range", LnpModel.tIndex(k) < LnpModel.NPAR);
                assertTrue("density index in range", LnpModel.rhoIndex(k) < LnpModel.NPAR);
            }
        }
    }
}
