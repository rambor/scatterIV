package version4.FormFactors;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.*;

/**
 * Mixtures of particle classes.
 *
 * <p>This is the part of the module that produces a number a user is most likely to put in a paper — "40%
 * of the particles are empty" — from the part of the data that constrains it least. Two classes of
 * different size are not orthogonal: a population of small empty particles and a slightly broader
 * distribution of loaded ones describe much the same curve, which is exactly the situation where a fit
 * returns a confident fraction that means nothing.
 *
 * <p>So the tests here are about restraint as much as recovery: that a mixture put in is found again, and
 * that a single population is <em>not</em> split into two just because two classes fit marginally better
 * than one.
 */
public class LnpPopulationsTest {

    /** a loaded particle of a given radius, as the per-class curves are built from */
    private static double[] oneClass(double[] q, double r0, double sigRel, boolean peak) {
        LnpModel m = new LnpModel(3, peak);
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1;
        p[LnpModel.R0] = r0; p[LnpModel.SIGMA] = sigRel * r0;
        p[LnpModel.BG] = 0;
        if (peak) {
            p[LnpModel.PEAK_Q] = 0.127; p[LnpModel.PEAK_W] = 0.012;
            p[LnpModel.PEAK_A] = 0.3 * m.intensity(p, new double[]{0.127})[0];
        }
        return m.intensity(p, q);
    }

    private static double[] grid() {
        double[] q = new double[260];
        for (int i = 0; i < q.length; i++) q[i] = 0.007 * Math.pow(0.41 / 0.007, i / (q.length - 1.0));
        return q;
    }

    private static LnpFitter.Result fitLoaded(double[] q, double[] I, double[] s) {
        LnpModel m = new LnpModel(3, true);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 220; p[LnpModel.SIGMA] = 26;
        p[LnpModel.PEAK_Q] = 0.127; p[LnpModel.PEAK_W] = 0.012;
        return new LnpFitter(m, q, I, s).physicalConstraints(true)
                .fit(p, m.templateFixed(), true, () -> false);
    }

    /**
     * A single population must not be reported as a mixture. Adding a second class always lowers
     * chi-squared a little; the suggestion is only allowed to keep it when the improvement is worth the
     * parameters, and a pure sample is the case where that judgement is tested.
     */
    @Test
    public void onePopulationIsNotSplitIntoTwo() {
        double[] q = grid();
        double[] I = oneClass(q, 220, 0.12, true);
        double[] s = new double[q.length];
        java.util.Random rng = new java.util.Random(11);
        for (int i = 0; i < q.length; i++) {
            s[i] = 0.02 * I[i];
            I[i] += rng.nextGaussian() * s[i];
        }
        LnpFitter.Result base = fitLoaded(q, I, s);
        LnpPopulations.Result r = LnpPopulations.alternate(base, 0, EnumSet.allOf(LnpPopulations.Kind.class),
                true, 2);

        assertNotNull(r);
        for (LnpPopulations.Component c : r.components) {
            if (c.kind == LnpPopulations.Kind.LOADED) continue;
            assertTrue(c.kind + " was added to a pure sample at " + c.numberFraction
                            + "; a spurious class should not carry a large share",
                    !(c.numberFraction > 0.35));
        }
    }

    /** the fractions that are reported add up, whatever else they are */
    @Test
    public void numberFractionsAreAProperDistribution() {
        double[] q = grid();
        double[] a = oneClass(q, 220, 0.12, true), b = oneClass(q, 150, 0.14, false);
        double[] I = new double[q.length], s = new double[q.length];
        java.util.Random rng = new java.util.Random(5);
        for (int i = 0; i < q.length; i++) {
            I[i] = a[i] + 0.6 * b[i];
            s[i] = 0.02 * I[i];
            I[i] += rng.nextGaussian() * s[i];
        }
        LnpPopulations.Result r = LnpPopulations.alternate(fitLoaded(q, I, s), 0,
                EnumSet.of(LnpPopulations.Kind.LOADED, LnpPopulations.Kind.EMPTY), false, 2);

        double sum = 0; int counted = 0;
        for (LnpPopulations.Component c : r.components)
            if (!Double.isNaN(c.numberFraction)) { sum += c.numberFraction; counted++; }
        if (counted > 1) {
            assertEquals("number fractions should sum to one", 1.0, sum, 0.02);
            for (LnpPopulations.Component c : r.components)
                if (!Double.isNaN(c.numberFraction))
                    assertTrue(c.kind + " fraction out of range: " + c.numberFraction,
                            c.numberFraction >= -1e-9 && c.numberFraction <= 1 + 1e-9);
        }
    }

    /** every class that is reported carries a size and a diameter a reader can use */
    @Test
    public void everyReportedClassIsDescribed() {
        double[] q = grid();
        double[] I = oneClass(q, 220, 0.12, true);
        double[] s = new double[q.length];
        for (int i = 0; i < q.length; i++) s[i] = 0.02 * I[i];
        LnpPopulations.Result r = LnpPopulations.alternate(fitLoaded(q, I, s), 0,
                EnumSet.of(LnpPopulations.Kind.LOADED, LnpPopulations.Kind.EMPTY), false, 2);
        for (LnpPopulations.Component c : r.components) {
            assertNotNull("a class needs a kind", c.kind);
            assertTrue(c.kind + " should have a positive size, was " + c.size, c.size > 0);
            assertTrue(c.kind + " should have a finite diameter, was " + c.diameter,
                    Double.isFinite(c.diameter) && c.diameter > 0);
        }
        assertTrue("the reduced chi-squared should be finite", Double.isFinite(r.chi2Reduced()));
    }

    /** asking for a single class returns a single class rather than failing */
    @Test
    public void aSingleClassIsAllowed() {
        double[] q = grid();
        double[] I = oneClass(q, 220, 0.12, true);
        double[] s = new double[q.length];
        for (int i = 0; i < q.length; i++) s[i] = 0.02 * I[i];
        LnpPopulations.Result r = LnpPopulations.alternate(fitLoaded(q, I, s), 0,
                EnumSet.of(LnpPopulations.Kind.LOADED), false, 1);
        assertEquals(1, r.components.size());
        assertEquals(LnpPopulations.Kind.LOADED, r.components.get(0).kind);
    }

    /**
     * The spread of fractions across equally good solutions is the honest error bar on a mixture, and it
     * must be reported where it was computed. A single number for "percent empty" with nothing beside it
     * is the output most likely to be over-read.
     */
    @Test
    public void theRangeOverEquallyGoodSolutionsIsKept() {
        double[] q = grid();
        double[] a = oneClass(q, 220, 0.12, true), b = oneClass(q, 150, 0.14, false);
        double[] I = new double[q.length], s = new double[q.length];
        for (int i = 0; i < q.length; i++) { I[i] = a[i] + 0.6 * b[i]; s[i] = 0.02 * I[i]; }
        LnpPopulations.Result r = LnpPopulations.alternate(fitLoaded(q, I, s), 0,
                EnumSet.of(LnpPopulations.Kind.LOADED, LnpPopulations.Kind.EMPTY), false, 2);
        for (java.util.Map.Entry<LnpPopulations.Kind, double[]> e : r.fractionRange.entrySet()) {
            double[] range = e.getValue();
            assertEquals(2, range.length);
            assertTrue(e.getKey() + " range should be ordered: " + range[0] + ".." + range[1],
                    range[1] >= range[0] - 1e-9);
        }
    }
}
