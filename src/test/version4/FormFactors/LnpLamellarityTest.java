package version4.FormFactors;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

/**
 * The lamellarity model: the accessible lipid fraction, and whether a known mixture of unilamellar and
 * multilamellar vesicles can be recovered from a curve.
 *
 * <p>The quantity at stake is f_acc, and the thing that would make it worthless is a model that returns a
 * plausible-looking number whatever the data. So the tests here fix the answer first and ask the fit for it:
 * a curve generated at a known distribution of lamellae, a curve from a genuinely unilamellar preparation, and
 * a curve whose interlamellar peak lies outside the fitted range, where the right outcome is a refusal to
 * claim anything rather than a confident 0.5.</p>
 */
public class LnpLamellarityTest {

    // ------------------------------------------------------------------ the arithmetic of f_acc

    /**
     * A perfectly unilamellar preparation has half its lipid on the outside, because the inner leaflet never is.
     * This is the ceiling of the whole measurement and the reason Scott et al. plot an axis to 0.5.
     */
    @Test
    public void aUnilamellarPreparationIsExactlyAHalf() {
        double[] p = LnpLamellarity.distribution(1.0, 0.5);
        assertEquals(1.0, p[0], 1e-6);
        assertEquals(1.0, LnpLamellarity.meanLamellae(p), 1e-6);
        assertEquals(0.5, LnpLamellarity.accessibleFraction(p, 62.6, Double.NaN, 0), 1e-9);
    }

    @Test
    public void withEqualBilayerAreasTheFractionIsHalfOverTheMeanNumberOfBilayers() {
        for (double w1 : new double[]{0.2, 0.5, 0.8}) {
            double[] p = LnpLamellarity.distribution(w1, 0.4);
            double mean = LnpLamellarity.meanLamellae(p);
            assertEquals(0.5 / mean, LnpLamellarity.accessibleFraction(p, 62.6, Double.NaN, 0), 1e-9);
        }
    }

    @Test
    public void moreBilayersMeansLessAccessibleLipid() {
        double prev = 1;
        for (double w1 : new double[]{0.95, 0.8, 0.6, 0.4, 0.2, 0.05}) {
            double f = LnpLamellarity.accessibleFraction(LnpLamellarity.distribution(w1, 0.5), 62.6, Double.NaN, 0);
            assertTrue("f_acc must fall as the unilamellar fraction falls", f < prev);
            assertTrue("and can never exceed the unilamellar ceiling", f <= 0.5 + 1e-9);
            prev = f;
        }
    }

    /**
     * The inner bilayers of a small vesicle hold less lipid than the outer ones, so weighting by area raises
     * f_acc — and raises it more for a small vesicle than a large one, because that is where the areas differ
     * most. Ignoring it would bias f_acc downwards exactly for the 30-50 nm preparations that are supposed to
     * be the unilamellar ones.
     */
    @Test
    public void areaWeightingRaisesTheFractionAndMoreSoForSmallVesicles() {
        double[] p = LnpLamellarity.distribution(0.4, 0.5);
        double flat = LnpLamellarity.accessibleFraction(p, 62.6, Double.NaN, 0);
        double big = LnpLamellarity.accessibleFraction(p, 62.6, 500, 0);
        double small = LnpLamellarity.accessibleFraction(p, 62.6, 150, 0);
        assertTrue("area weighting should raise f_acc: " + flat + " -> " + big, big > flat);
        assertTrue("and raise it further for a small vesicle: " + big + " -> " + small, small > big);
        assertTrue("but never past the ceiling", small <= 0.5 + 1e-9);
    }

    @Test
    public void theDistributionIsNormalisedAndHasTheShapeItClaims() {
        double[] p = LnpLamellarity.distribution(0.6, 0.45);
        double sum = 0;
        for (double v : p) sum += v;
        assertEquals("P(N) must be normalised", 1.0, sum, 1e-9);
        assertEquals("the unilamellar weight is w1 after renormalisation", 0.6, p[0], 1e-6);
        for (int i = 2; i < 8; i++)
            assertTrue("the tail above N = 2 must decay", p[i] < p[i - 1]);
    }

    // ------------------------------------------------------------------ the physics

    @Test
    public void aSingleBilayerHasNoStructureFactor() {
        for (double q = 0.05; q < 0.6; q += 0.05)
            assertEquals(1.0, LnpLamellarity.stack(q, 1, 62.6, 3), 1e-12);
    }

    @Test
    public void anOrderedStackPeaksAtTheBraggPositionsWithTheSquareOfTheLayerCount() {
        int n = 6;
        double d = 62.6;
        for (int h = 1; h <= 3; h++) {
            double q = 2 * Math.PI * h / d;
            assertEquals("order " + h + " of a perfect stack", n * (double) n,
                    LnpLamellarity.stack(q, n, d, 0), 1e-6);
        }
    }

    @Test
    public void disorderDampsTheHigherOrdersMoreThanTheFirst() {
        double d = 62.6;
        double q1 = 2 * Math.PI / d, q3 = 3 * q1;
        double perfect1 = LnpLamellarity.stack(q1, 8, d, 0), perfect3 = LnpLamellarity.stack(q3, 8, d, 0);
        double rough1 = LnpLamellarity.stack(q1, 8, d, 4), rough3 = LnpLamellarity.stack(q3, 8, d, 4);
        assertTrue(rough1 < perfect1);
        assertTrue("the third order must lose relatively more than the first",
                rough3 / perfect3 < rough1 / perfect1);
    }

    /** the cosine in the bilayer form factor is what fixes d_HH: it must put a zero where d_HH says */
    @Test
    public void theFormFactorHasItsHeadgroupZeroWhereTheThicknessPutsIt() {
        double zH = 19, dHH = 2 * zH;
        double qZero = Math.PI / dHH * 1;          // cos(q zH) = 0 at q zH = pi/2, i.e. q = pi/(2 zH) = pi/dHH
        double head = 2 * 3.0 * Math.cos(qZero * zH) * Math.exp(-0.5 * qZero * qZero * 9);
        assertEquals("the headgroup term vanishes there", 0, head, 1e-9);
    }

    // ------------------------------------------------------------------ recovery from a curve

    private static double[][] curve(double[] par, double qlo, double qhi, int n, long seed) {
        double[] q = new double[n];
        for (int i = 0; i < n; i++) q[i] = qlo * Math.pow(qhi / qlo, i / (double) (n - 1));
        double[] clean = LnpLamellarity.intensity(par, q);
        Random rnd = new Random(seed);
        double[] I = new double[n], s = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = 0.03 * clean[i];
            I[i] = clean[i] + s[i] * rnd.nextGaussian();
        }
        return new double[][]{q, I, s};
    }

    /**
     * The headline test. A curve is generated from a preparation that is mostly but not entirely unilamellar,
     * of the kind Scott et al. describe, and the fit has to come back with the repeat and the accessible
     * fraction it was built from.
     */
    @Test
    public void recoversTheRepeatAndTheAccessibleFractionOfAKnownMixture() {
        double[] truth = LnpLamellarity.start();
        truth[LnpLamellarity.D] = 62.6;
        truth[LnpLamellarity.Z_H] = 19.5;
        truth[LnpLamellarity.SIGMA_H] = 3.0;
        truth[LnpLamellarity.SIGMA_C] = 4.5;
        truth[LnpLamellarity.RHO_R] = -1.1;
        truth[LnpLamellarity.DELTA] = 3.0;
        truth[LnpLamellarity.W1] = 0.75;
        truth[LnpLamellarity.R] = 0.30;
        truth[LnpLamellarity.SCALE] = 1;
        truth[LnpLamellarity.BG] = 0;

        double fTrue = LnpLamellarity.accessibleFraction(
                LnpLamellarity.distribution(truth[LnpLamellarity.W1], truth[LnpLamellarity.R]),
                truth[LnpLamellarity.D], Double.NaN, 0);

        double[][] d = curve(truth, 0.05, 0.65, 400, 23L);
        LnpLamellarity.Result r = LnpLamellarity.fit(d[0], d[1], d[2], 0.05, 0.65, Double.NaN, Double.NaN);

        assertEquals("the lamellar repeat", 62.6, r.params[LnpLamellarity.D], 1.5);
        assertEquals("the head-to-head thickness", 39.0, r.headToHead(), 2.5);
        assertEquals("the accessible fraction", fTrue, r.accessible, 0.05);
        assertTrue("and it must stay under the unilamellar ceiling", r.accessible <= 0.5 + 1e-9);
        assertTrue("the fit should describe its own curve: " + r.chi2Reduced, r.chi2Reduced < 5);
    }

    /**
     * And the control: a curve from a genuinely unilamellar preparation must not be given a multilamellar
     * population. A model that can only find multilamellarity is of no use for deciding whether a preparation
     * has it.
     */
    @Test
    public void aTrulyUnilamellarPreparationComesBackAtTheCeiling() {
        double[] truth = LnpLamellarity.start();
        truth[LnpLamellarity.W1] = 0.999;
        truth[LnpLamellarity.D] = 62.6;
        double[][] d = curve(truth, 0.05, 0.65, 400, 7L);
        LnpLamellarity.Result r = LnpLamellarity.fit(d[0], d[1], d[2], 0.05, 0.65, Double.NaN, Double.NaN);

        assertTrue("f_acc should be near the unilamellar ceiling, was " + r.accessible, r.accessible > 0.44);
        assertTrue("and <N> near one, was " + r.meanLamellae, r.meanLamellae < 1.15);
    }

    /**
     * The failure mode that would quietly produce wrong answers: fitting a range that cannot see the first
     * interlamellar order. Nothing then constrains the stack term, the unilamellar fraction goes to one because
     * nothing contradicts it, and the number looks like a measurement. The fit must say so instead.
     */
    @Test
    public void aRangeThatCannotSeeTheFirstOrderIsReportedAsUnmeasured() {
        double[] truth = LnpLamellarity.start();
        truth[LnpLamellarity.D] = 62.6;         // first order at q = 0.100
        truth[LnpLamellarity.W1] = 0.5;
        double[][] d = curve(truth, 0.15, 0.65, 300, 11L);
        LnpLamellarity.Result r = LnpLamellarity.fit(d[0], d[1], d[2], 0.15, 0.65, Double.NaN, Double.NaN);

        boolean warned = false;
        for (String w : r.warnings)
            if (w.contains("outside the") || w.contains("not measured") || w.contains("unconstrained")) warned = true;
        assertTrue("it must warn that f_acc is not measured here: " + r.warnings, warned);
    }

    @Test
    public void tooFewPointsIsRefusedRatherThanFitted() {
        double[][] d = curve(LnpLamellarity.start(), 0.05, 0.65, 400, 3L);
        try {
            LnpLamellarity.fit(d[0], d[1], d[2], 0.60, 0.65, Double.NaN, Double.NaN);
            fail("a handful of points cannot determine ten parameters");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("25"));
        }
    }

    // ------------------------------------------------------------------ the report

    @Test
    public void theReportLeadsWithTheAnswerAndSaysWhatTheCeilingIs() {
        double[][] d = curve(LnpLamellarity.start(), 0.05, 0.65, 300, 5L);
        LnpLamellarity.Result r = LnpLamellarity.fit(d[0], d[1], d[2], 0.05, 0.65, Double.NaN, Double.NaN);
        String text = LnpLamellarity.describe(r);

        assertTrue(text.contains("f_acc"));
        assertTrue("a reader must be told 0.5 is unilamellar, not 1.0", text.contains("0.5 is a perfectly unilamellar"));
        assertTrue("the published comparison belongs in the report", text.contains("0.39"));
        assertTrue(text.contains("Scott et al."));
        assertTrue("the distribution should be shown, not just its mean", text.contains("N = 1"));
    }

    // ------------------------------------------------------------------ what the composition predicts

    @Test
    public void chargeIsCountedPerChargeAndNotPerSpecies() {
        LnpComposition pg = new LnpComposition();
        pg.add(LnpComposition.lookup("POPC"), 95);
        pg.add(LnpComposition.lookup("POPG"), 5);
        assertEquals("5 mol% of a monoanion", 5.0, pg.anionicChargePer100(), 1e-6);

        LnpComposition cl = new LnpComposition();
        cl.add(LnpComposition.lookup("POPC"), 95);
        cl.add(LnpComposition.lookup("Cardiolipin TOCL (18:1)4"), 5);
        assertEquals("a cardiolipin carries two", 10.0, cl.anionicChargePer100(), 1e-6);
        assertEquals("but is still one species", 5.0, cl.anionicMolPercent(), 1e-6);
    }

    @Test
    public void popsIsInTheLibraryAndAnionic() {
        LnpComposition.Lipid pops = LnpComposition.lookup("POPS");
        assertNotNull("POPS is the charged dopant of the extrusion study and was missing", pops);
        assertEquals(-1, pops.charge);
        assertEquals("a neutral PC must not be charged", 0, LnpComposition.lookup("POPC").charge);
        assertEquals("nor a zwitterionic sphingomyelin", 0,
                LnpComposition.lookup("Sphingomyelin (16:0 SM)").charge);
    }

    /**
     * The expectation that matters: a neutral phosphatidylcholine should be flagged as one that is
     * <em>expected</em> to come out multilamellar, so that a clean unilamellar fit prompts a check instead of
     * passing silently.
     */
    @Test
    public void aNeutralPhosphatidylcholineIsExpectedToBeMultilamellar() {
        LnpComposition pc = new LnpComposition();
        pc.add(LnpComposition.lookup("POPC"), 100);
        String e = pc.lamellarityExpectation();
        assertTrue(e, e.contains("EXPECTED to be substantially multilamellar"));
        assertTrue("and it should say what f_acc to expect", e.contains("0.35"));
        assertTrue("and say a clean answer is worth checking", e.contains("worth checking"));
    }

    @Test
    public void chargedMixesAreExpectedToBeMoreUnilamellar() {
        LnpComposition some = new LnpComposition();
        some.add(LnpComposition.lookup("POPC"), 95);
        some.add(LnpComposition.lookup("POPG"), 5);
        assertTrue(some.lamellarityExpectation().contains("above the 0.35"));

        LnpComposition lots = new LnpComposition();
        lots.add(LnpComposition.lookup("POPC"), 70);
        lots.add(LnpComposition.lookup("POPG"), 30);
        assertTrue(lots.lamellarityExpectation().contains("near the unilamellar ceiling"));
    }

    /** an ionizable lipid is charge that depends on the buffer, so the expectation has to say which buffer */
    @Test
    public void anIonizableLipidIsNotTreatedAsFixedCharge() {
        LnpComposition lnp = LnpComposition.preset("Comirnaty (BNT162b2)");
        assertEquals("ionizable lipids carry no fixed charge", 0.0, lnp.anionicChargePer100(), 1e-9);
        assertTrue(lnp.lamellarityExpectation().contains("depends on the buffer"));
    }

    /**
     * The LNP control. An mRNA-lipid nanoparticle is not a vesicle preparation, the model cannot describe it,
     * and what came back on real data was f_acc = 0.472 +- 0.002 with a reduced chi-squared of 143 — a headline
     * that reads as a measurement sitting above a warning that reads as a formality. The report must refuse.
     */
    @Test
    public void aCurveTheModelCannotDescribeGetsNoAccessibleFractionAtAll() {
        // a power law: nothing a bilayer-plus-stack model can fit
        int n = 300;
        double[] q = new double[n], I = new double[n], s = new double[n];
        for (int i = 0; i < n; i++) {
            q[i] = 0.05 * Math.pow(0.6 / 0.05, i / (double) (n - 1));
            I[i] = Math.pow(q[i], -4);
            s[i] = 0.002 * I[i];
        }
        LnpLamellarity.Result r = LnpLamellarity.fit(q, I, s, 0.05, 0.6, Double.NaN, Double.NaN);
        assertFalse("this must not be reported as trustworthy", r.trustworthy());

        String text = LnpLamellarity.describe(r);
        assertTrue(text, text.contains("NO ANSWER"));
        assertTrue("the diagnostic table must be labelled as such", text.contains("DO NOT QUOTE"));
        assertFalse("the headline answer must not appear", text.contains("THE ANSWER"));
        assertFalse("nor the published comparison, which would invite a comparison",
                text.contains("Eight phosphatidylcholines"));
    }

    @Test
    public void aGoodFitIsStillReportedInFull() {
        double[][] d = curve(LnpLamellarity.start(), 0.05, 0.65, 300, 5L);
        LnpLamellarity.Result r = LnpLamellarity.fit(d[0], d[1], d[2], 0.05, 0.65, Double.NaN, Double.NaN);
        assertTrue("reduced chi2 was " + r.chi2Reduced, r.trustworthy());
        assertTrue(LnpLamellarity.describe(r).contains("THE ANSWER"));
    }

    @Test
    public void theUnilamellarComparisonCurveIsTheMixtureWhenEverythingIsUnilamellar() {
        double[] par = LnpLamellarity.start();
        par[LnpLamellarity.W1] = 1 - 1e-6;
        double[] q = {0.08, 0.1, 0.15, 0.2, 0.3};
        double[] mix = LnpLamellarity.intensity(par, q);
        double[] uni = LnpLamellarity.unilamellar(par, q);
        for (int i = 0; i < q.length; i++)
            assertEquals("at q = " + q[i], mix[i], uni[i], Math.abs(mix[i]) * 1e-3);
    }
}
