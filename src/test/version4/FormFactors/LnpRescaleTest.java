package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Moving the fixed density reference must carry the other densities with it.
 *
 * <p>Only ratios of density are determined by the data. The model divides its amplitude by the sum of
 * |drho_k| V(r_k), so multiplying every contrast by the same constant leaves the curve identical with the
 * scale untouched. So when the reference moves — a composition is declared,
 * or a wide-angle dataset shows the chains are frozen — the solution on screen is still a solution, but only
 * if the rest moves too. Left behind, it is nowhere near the minimum, and the next fit does not recover: on
 * real liposome data, changing the reference from -0.025 to -0.044 and refitting from the stale densities
 * reached a reduced chi-squared of 496 with an outer headgroup layer 168 A thick.
 */
public class LnpRescaleTest {

    private static double[][] data() {
        LnpModel m = new LnpModel(4, false);
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1;
        p[LnpModel.R0] = 1517; p[LnpModel.SIGMA] = 235;
        p[LnpModel.T1] = 21.2; p[LnpModel.T2] = 24.6; p[LnpModel.T3] = 14.0;
        p[LnpModel.T4] = 0; p[LnpModel.rhoIndex(4)] = 0;
        p[LnpModel.RHO_C] = 0; p[LnpModel.RHO_1] = 0.101;
        p[LnpModel.RHO_2] = -0.025; p[LnpModel.RHO_3] = 0.113;
        return LnpTestData.fromModel(m, p, 0.005, 0.34, 600, 11L);
    }

    /**
     * The degeneracy itself: the same curve, to the last digit, from densities scaled together. If this ever
     * stops holding, the rescaling below is not a safe thing to do.
     */
    @Test
    public void scalingEveryDensityTogetherLeavesTheCurveAlone() {
        LnpModel m = new LnpModel(4, false);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 600; p[LnpModel.SIGMA] = 90;
        p[LnpModel.T4] = 0; p[LnpModel.rhoIndex(4)] = 0;

        double[] q = new double[120];
        for (int i = 0; i < q.length; i++) q[i] = 0.005 + 0.3 * i / (q.length - 1.0);
        double[] before = m.intensity(p, q);

        double k = 1.76;
        double[] scaled = p.clone();
        for (int i = 0; i <= m.shells; i++) scaled[LnpModel.rhoIndex(i)] *= k;
        double[] after = m.intensity(scaled, q);

        for (int i = 0; i < q.length; i++)
            assertEquals("the curve must be unchanged at q = " + q[i], before[i], after[i],
                    1e-9 * Math.max(1e-30, Math.abs(before[i])));
    }

    /**
     * And the fit must survive the move. Seeded from the rescaled densities it returns the same geometry;
     * seeded from the stale ones it does not, which is what the panel used to do.
     */
    @Test
    public void theFitSurvivesAChangeOfReference() {
        double[][] d = data();
        LnpModel m = new LnpModel(4, false);

        double[] p = m.templateDefaults();
        boolean[] fx = m.templateFixed();
        p[LnpModel.T4] = 0; fx[LnpModel.T4] = true;
        p[LnpModel.rhoIndex(4)] = 0; fx[LnpModel.rhoIndex(4)] = true;
        fx[LnpModel.T1] = false; fx[LnpModel.T3] = false;
        p[LnpModel.R0] = 1517; p[LnpModel.SIGMA] = 235;
        p[LnpModel.T1] = 21.2; p[LnpModel.T2] = 24.6; p[LnpModel.T3] = 14.0;
        p[LnpModel.RHO_1] = 0.101; p[LnpModel.RHO_2] = -0.025; p[LnpModel.RHO_3] = 0.113;

        LnpFitter.Result at25 = new LnpFitter(m, d[0], d[1], d[2]).fit(p.clone(), fx, true, () -> false);

        // the reference moves to the value a declared gel composition gives, and the rest moves with it
        double k = -0.044 / -0.025;
        double[] moved = p.clone();
        for (int i = 0; i <= m.shells; i++) moved[LnpModel.rhoIndex(i)] *= k;
        LnpFitter.Result at44 = new LnpFitter(m, d[0], d[1], d[2]).fit(moved, fx, true, () -> false);

        assertEquals("the fit quality must be unchanged", at25.chi2Reduced, at44.chi2Reduced, 0.05);
        double was = at25.params[LnpModel.T1] + at25.params[LnpModel.T2] + at25.params[LnpModel.T3];
        double now = at44.params[LnpModel.T1] + at44.params[LnpModel.T2] + at44.params[LnpModel.T3];
        assertEquals("and so must the bilayer thickness: the geometry does not depend on the reference",
                was, now, 0.5);
    }
}
