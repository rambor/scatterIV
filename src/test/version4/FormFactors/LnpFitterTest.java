package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The staged Levenberg-Marquardt fit, checked by recovering parameters from a curve generated at known values.
 */
public class LnpFitterTest {

    private static final LnpFitter.Cancel RUN = () -> false;

    /**
     * Li et al. (2025) fit an mRNA-LNP with R0 = 201 Angstrom, sigma = 31 Angstrom and an internal repeat of
     * 50.7 Angstrom. Starting well away from those values, the fitter has to find them.
     */
    @Test
    public void recoversSizeSpreadAndInternalRepeat() {
        LnpModel m = new LnpModel(3, true);           // core, three shells, plus the nucleic-acid peak
        double[] truth = m.templateDefaults();
        truth[LnpModel.R0] = 201;
        truth[LnpModel.SIGMA] = 31;
        truth[LnpModel.PEAK_Q] = 2 * Math.PI / 50.7;
        double[][] d = LnpTestData.fromModel(m, truth, 0.008, 0.4, 500, 11);

        double[] start = m.templateDefaults();
        start[LnpModel.R0] = 170;                     // deliberately off
        start[LnpModel.SIGMA] = 20;
        start[LnpModel.PEAK_Q] = 0.13;

        LnpFitter.Result r = new LnpFitter(m, d[0], d[1], d[2]).fit(start, m.templateFixed(), true, RUN);

        assertEquals("R0", 201, r.params[LnpModel.R0], 8);
        assertEquals("sigma", 31, r.params[LnpModel.SIGMA], 6);
        assertEquals("internal repeat d", 50.7, 2 * Math.PI / r.params[LnpModel.PEAK_Q], 2);
        assertTrue("fitting a curve to its own model should approach a reduced chi2 of 1, was " + r.chi2Reduced,
                r.chi2Reduced < 3);
    }

    /** Parameters held fixed must come back untouched. */
    @Test
    public void fixedParametersDoNotMove() {
        LnpModel m = new LnpModel(3, false);
        double[] truth = m.templateDefaults();
        truth[LnpModel.R0] = 210;
        double[][] d = LnpTestData.fromModel(m, truth, 0.008, 0.4, 400, 5);

        boolean[] fixed = m.templateFixed();
        fixed[LnpModel.T1] = true;
        double[] start = m.templateDefaults();
        start[LnpModel.T1] = 17.5;
        start[LnpModel.R0] = 180;

        LnpFitter.Result r = new LnpFitter(m, d[0], d[1], d[2]).fit(start, fixed, true, RUN);
        assertEquals("a fixed thickness must not move", 17.5, r.params[LnpModel.T1], 1e-9);
        assertNotEquals("a free radius should move", 180, r.params[LnpModel.R0], 1.0);
    }

    /**
     * A regression. Parameters are kept inside limits so the fit can search in log space, where a thickness
     * cannot reach zero. Those limits were being applied to fixed parameters too, so a layer the user had
     * removed by setting its thickness to zero came back as 0.5 Å — the model silently differed from the one
     * asked for, and the report showed the clamped value.
     */
    @Test
    public void fixedValuesAreNotMovedByTheLimits() {
        LnpModel m = new LnpModel(4, false);
        double[] truth = m.templateDefaults();
        double[][] d = LnpTestData.fromModel(m, truth, 0.008, 0.4, 400, 29);

        double[] start = m.templateDefaults();
        boolean[] fixed = m.templateFixed();
        start[LnpModel.T4] = 0;                       // the layer removed, below the fitter's lower limit
        fixed[LnpModel.T4] = true;
        start[LnpModel.rhoIndex(4)] = 0;
        fixed[LnpModel.rhoIndex(4)] = true;

        LnpFitter.Result r = new LnpFitter(m, d[0], d[1], d[2]).fit(start, fixed, true, RUN);
        assertEquals("a fixed zero thickness must stay zero", 0, r.params[LnpModel.T4], 1e-12);
        assertEquals("and its density with it", 0, r.params[LnpModel.rhoIndex(4)], 1e-12);
    }

    /** The same holds for a fixed value below any other limit: held means held. */
    @Test
    public void aFixedValueBelowItsLimitIsLeftAlone() {
        LnpModel m = new LnpModel(3, false);
        double[][] d = LnpTestData.fromModel(m, m.templateDefaults(), 0.008, 0.4, 400, 33);
        double[] start = m.templateDefaults();
        boolean[] fixed = m.templateFixed();
        start[LnpModel.T3] = 0.2;                     // under the 0.5 Å lower limit, but held
        fixed[LnpModel.T3] = true;
        LnpFitter.Result r = new LnpFitter(m, d[0], d[1], d[2]).fit(start, fixed, true, RUN);
        assertEquals(0.2, r.params[LnpModel.T3], 1e-12);
    }

    /** Thicknesses and radii are fitted in log space, so no fit may return a negative one. */
    @Test
    public void fittedGeometryStaysPositive() {
        LnpModel m = new LnpModel(3, true);
        double[][] d = LnpTestData.fromModel(m, m.templateDefaults(), 0.008, 0.4, 400, 23);
        double[] start = m.templateDefaults();
        start[LnpModel.R0] = 320;                     // start far out, where a bad step could cross zero
        start[LnpModel.SIGMA] = 70;
        LnpFitter.Result r = new LnpFitter(m, d[0], d[1], d[2]).fit(start, m.templateFixed(), true, RUN);
        assertTrue("R0 positive", r.params[LnpModel.R0] > 0);
        assertTrue("sigma positive", r.params[LnpModel.SIGMA] > 0);
        for (int k = 1; k <= 3; k++)
            assertTrue("thickness of layer " + k + " positive", r.params[LnpModel.tIndex(k)] > 0);
    }
}
