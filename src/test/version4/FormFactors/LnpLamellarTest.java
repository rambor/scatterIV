package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What the relative intensities of the lamellar orders say about the bilayer, and the ambiguity that comes with
 * reading them.
 */
public class LnpLamellarTest {

    /** orders of a symmetric bilayer with two Gaussian headgroup layers, with the Lorentz factor applied */
    private static double[][] synthetic(double repeat, double headToHead, double sigma, int orders) {
        double[] q = new double[orders], area = new double[orders];
        for (int h = 1; h <= orders; h++) {
            q[h - 1] = 2 * Math.PI * h / repeat;
            double f = 2 * Math.cos(q[h - 1] * headToHead / 2) * Math.exp(-q[h - 1] * q[h - 1] * sigma * sigma / 2);
            area[h - 1] = f * f / (q[h - 1] * q[h - 1]);
        }
        return new double[][]{q, area};
    }

    /** The generating thickness has to be among the solutions offered, for a range of bilayers. */
    @Test
    public void recoversTheBilayerThickness() {
        for (double truth : new double[]{30, 36, 38, 44}) {
            double[][] d = synthetic(55.5, truth, 3.0, 3);
            LnpLamellar.Profile p = LnpLamellar.analyse(d[0], d[1], 55.5);
            boolean found = false;
            for (double[] s : p.solutions) if (Math.abs(s[0] - truth) < 2.5) found = true;
            assertTrue("the true thickness " + truth + " should be among the solutions " + show(p), found);

            double[] chosen = LnpLamellar.choose(p, truth);
            assertNotNull(chosen);
            assertEquals("told roughly what to expect, it should pick the right branch", truth, chosen[0], 2.5);
        }
    }

    /**
     * The point of reporting several solutions. Scattering measures |F| and not its sign, and a thickness t and
     * the gap d - t give identical intensities at every order, so the fit cannot tell bilayer from water on its
     * own. If this ever starts returning one answer, the ambiguity has been hidden rather than solved.
     */
    @Test
    public void thicknessAndWaterGapAreIndistinguishable() {
        double repeat = 55.5, truth = 38;
        double[][] d = synthetic(repeat, truth, 3.0, 3);
        LnpLamellar.Profile p = LnpLamellar.analyse(d[0], d[1], repeat);

        assertTrue("more than one thickness must be offered, got " + show(p), p.solutions.size() > 1);
        boolean mirror = false;
        for (double[] s : p.solutions) if (Math.abs(s[0] - (repeat - truth)) < 2.5) mirror = true;
        assertTrue("the mirror solution d - t should be offered too: " + show(p), mirror);

        String text = LnpLamellar.describe(p, 19);
        assertTrue("the text must say the sign is not measured", text.contains("SIGN OF F IS NOT MEASURED"));
        assertTrue("and say how to settle it", text.contains("swelling series"));
    }

    /** The amplitude reported is the Lorentz-corrected one, since that is what the bilayer form factor is. */
    @Test
    public void amplitudeCarriesTheLorentzFactor() {
        double[][] d = synthetic(55.5, 38, 3.0, 3);
        LnpLamellar.Profile p = LnpLamellar.analyse(d[0], d[1], 55.5);
        for (LnpLamellar.Order o : p.orders)
            assertEquals("|F| should be q sqrt(I)", o.q * Math.sqrt(o.area), o.structureFactor, 1e-9);
    }

    /** Two orders cannot fix both the thickness and the headgroup width, and the panel has to say so. */
    @Test
    public void twoOrdersAreNotEnough() {
        double[][] d = synthetic(55.5, 38, 3.0, 2);
        LnpLamellar.Profile p = LnpLamellar.analyse(d[0], d[1], 55.5);
        assertTrue(Double.isNaN(p.headToHead));
        assertFalse(p.notes.isEmpty());
        assertTrue(p.notes.get(0).contains("third order"));
    }

    /** One order says nothing about the bilayer at all. */
    @Test
    public void oneOrderSaysNothing() {
        LnpLamellar.Profile p = LnpLamellar.analyse(new double[]{0.113}, new double[]{10}, 55.5);
        assertTrue(p.solutions.isEmpty());
        assertTrue(p.notes.get(0).contains("say nothing"));
    }

    private static String show(LnpLamellar.Profile p) {
        StringBuilder sb = new StringBuilder();
        for (double[] s : p.solutions) sb.append(String.format("%.1f ", s[0]));
        return sb.toString();
    }
}
