package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Simulating the fitted particle at other radii, to show whether its size was measured at all.
 *
 * <p>A fit always returns a radius and an error bar, whether or not the measurement could see the particle:
 * the error bar is the curvature of the likelihood where the fit stopped, and a flat plain has a curvature.
 * On real liposome data a fit reported 1517 &plusmn; 12 &#8491; while every radius from 600 to 2500 &#8491;
 * gave the same reduced chi-squared to within 0.13.
 */
public class LnpSizeLimitTest {

    private static final double QMIN = 0.0045, QMAX = 0.34;

    private static double[] q() {
        double[] q = new double[500];
        for (int i = 0; i < q.length; i++)
            q[i] = QMIN * Math.pow(QMAX / QMIN, i / (q.length - 1.0));
        return q;
    }

    private static LnpModel vesicle() { return new LnpModel(4, false); }

    private static double[] params(LnpModel m, double r0) {
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1e7;
        p[LnpModel.R0] = r0; p[LnpModel.SIGMA] = 0.155 * r0;
        p[LnpModel.T1] = 21.2; p[LnpModel.T2] = 24.6; p[LnpModel.T3] = 14.0;
        p[LnpModel.T4] = 0; p[LnpModel.rhoIndex(4)] = 0;
        p[LnpModel.RHO_C] = 0; p[LnpModel.RHO_1] = 0.0269;
        p[LnpModel.RHO_2] = -0.025; p[LnpModel.RHO_3] = 0.0452;
        p[LnpModel.BG] = 0;
        return p;
    }

    /** error bars at a few per cent, as in the real measurement */
    private static double[] sigma(LnpModel m, double[] p, double[] q) {
        double[] I = m.intensity(p, q);
        double[] s = new double[q.length];
        for (int i = 0; i < q.length; i++) s[i] = 0.03 * I[i];
        return s;
    }

    private static LnpSizeLimit.Result at(double r0, int rungs, double decades) {
        LnpModel m = vesicle();
        double[] p = params(m, r0), q = q();
        return LnpSizeLimit.analyse(m, p, q, sigma(m, p, q), LnpSizeLimit.ladder(r0, rungs, decades));
    }

    /** the ladder has to come back with the rungs on it */
    @Test
    public void theLadderIsReturned() {
        LnpSizeLimit.Result r = at(600, 6, 1.2);
        assertEquals("one rung per requested step", 6, r.rungs.size());
        assertTrue("the fitted radius itself must be one of them",
                r.rungs.stream().anyMatch(k -> k.isFit));
        for (LnpSizeLimit.Rung k : r.rungs)
            assertEquals("every rung carries a full curve", 500, k.intensity.length);
    }

    /** it spans downwards from the fitted radius and ends on it */
    @Test
    public void theLadderDescendsFromTheFittedRadius() {
        double[] l = LnpSizeLimit.ladder(1000, 5, 1.0);
        assertEquals("the top rung is the fitted radius", 1000, l[l.length - 1], 1e-6);
        assertEquals("the bottom rung is a decade below", 100, l[0], 1e-6);
        for (int i = 1; i < l.length; i++) assertTrue("ascending", l[i] > l[i - 1]);
    }

    /** a particle the measurement can see: changing its radius has to move the curve */
    @Test
    public void aSmallParticleHasAMeasuredSize() {
        LnpSizeLimit.Result r = at(300, 6, 1.0);
        assertFalse("300 A is well inside 2*pi/q_min", r.beyondCamera);
        assertFalse("so the verdict must not say the size is unmeasured", r.verdict().contains("not measured"));
        double worst = r.rungs.stream().filter(k -> !k.isFit)
                .mapToDouble(k -> k.sigmaPerPoint).max().orElse(0);
        assertTrue("a wrong radius must cost many sigma, was " + worst, worst > 5);
    }

    /** a particle larger than the measurement encodes: say so, whatever the fit reported */
    @Test
    public void aLargeParticleIsFlaggedAsUnmeasured() {
        LnpSizeLimit.Result r = at(1517, 6, 1.2);
        assertTrue("1517 A is beyond 2*pi/q_min = 1396 A", r.beyondCamera);
        assertTrue("and the verdict has to say so plainly: " + r.verdict(),
                r.verdict().contains("not measured"));
        assertEquals("the threshold is the longest distance the q range encodes",
                2 * Math.PI / QMIN, r.longestEncoded, 1);
    }

    /**
     * The separation has to be a difference in shape, not in brightness. A bigger vesicle scatters more, and
     * the fitted scale absorbs that instantly, so counting it would make every radius look distinguishable.
     */
    @Test
    public void brightnessAloneIsNotCountedAsADifference() {
        LnpModel m = vesicle();
        double[] q = q(), p = params(m, 400);
        double[] s = sigma(m, p, q);

        LnpSizeLimit.Result normal = LnpSizeLimit.analyse(m, p, q, s, new double[]{400});
        double[] brighter = p.clone();
        brighter[LnpModel.SCALE] *= 37;
        LnpSizeLimit.Result scaled = LnpSizeLimit.analyse(m, brighter, q, s, new double[]{400});

        assertEquals("the same particle 37 times brighter is the same particle",
                normal.rungs.get(0).sigmaPerPoint, scaled.rungs.get(0).sigmaPerPoint, 1e-6);
    }

    /** the size spread follows the radius, so the ladder changes size and not polydispersity */
    @Test
    public void polydispersityStaysInProportion() {
        LnpModel m = vesicle();
        double[] q = q(), p = params(m, 800);
        p[LnpModel.SIGMA] = 0.25 * 800;
        LnpSizeLimit.Result r = LnpSizeLimit.analyse(m, p, q, sigma(m, p, q), new double[]{200, 800});

        // a quarter-spread 200 A vesicle, built independently, must match the rung
        double[] ref = p.clone();
        ref[LnpModel.R0] = 200; ref[LnpModel.SIGMA] = 0.25 * 200; ref[LnpModel.BG] = 0;
        double[] expect = m.intensity(ref, q);
        double[] got = r.rungs.get(0).intensity;
        for (int i = 0; i < q.length; i += 50)
            assertEquals("rung must keep sigma/R0 at the fitted value", expect[i], got[i],
                    1e-6 * Math.max(1e-30, Math.abs(expect[i])));
    }

    /** the fitted rung is itself, at zero separation */
    @Test
    public void theFittedRungIsZeroFromItself() {
        LnpSizeLimit.Result r = at(500, 4, 0.8);
        LnpSizeLimit.Rung fit = r.rungs.stream().filter(k -> k.isFit).findFirst().orElseThrow(AssertionError::new);
        assertEquals(0.0, fit.sigmaPerPoint, 1e-12);
        assertEquals(500, fit.r0, 1e-6);
    }
}
