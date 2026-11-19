package version4.FormFactors;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Turning a formulation (cargo length, N/P, composition) into an expected payload, and comparing it with what was
 * fitted. N/P is the molar ratio of ionizable amines to nucleic-acid phosphates; the cargo estimate pairs every
 * amine with a phosphate at that ratio.
 */
public class LnpExpectationsTest {

    private static LnpExpectations mrna() {
        LnpExpectations e = new LnpExpectations();
        e.cargo = LnpExpectations.Cargo.MRNA;
        e.length = 4284;                 // nucleotides
        e.np = 6;
        return e;
    }

    /** A bigger particle holds more cargo, and the count scales roughly with volume. */
    @Test
    public void copiesScaleWithParticleVolume() {
        LnpExpectations e = mrna();
        double small = e.copies(300), big = e.copies(600);
        assertTrue("a particle must hold some cargo", small > 0);
        double ratio = big / small;
        assertEquals("doubling the radius should hold about eight times the cargo", 8.0, ratio, 1.0);
    }

    /**
     * N/P is a charge balance: more amines per phosphate means fewer phosphates, so fewer copies of the cargo in a
     * particle of a given size.
     */
    @Test
    public void raisingNoverPLowersTheCargoPerParticle() {
        LnpExpectations low = mrna(), high = mrna();
        low.np = 3;
        high.np = 12;
        assertTrue("N/P 12 should hold less cargo than N/P 3",
                high.copies(400) < low.copies(400));
    }

    /** A longer message means fewer copies of it fit in the same particle. */
    @Test
    public void longerCargoMeansFewerCopies() {
        LnpExpectations shortOne = mrna(), longOne = mrna();
        shortOne.length = 1000;
        longOne.length = 4284;
        assertTrue(longOne.copies(400) < shortOne.copies(400));
    }

    /** siRNA is double stranded, so each molecule carries two phosphates per base pair. */
    @Test
    public void siRnaCountsBothStrands() {
        assertEquals(40, LnpExpectations.Cargo.SIRNA.nucleotides(20), 1e-9);
        assertEquals(20, LnpExpectations.Cargo.MRNA.nucleotides(20), 1e-9);
    }

    /** With no cargo declared there is nothing to estimate, and the answer must be zero rather than a guess. */
    @Test
    public void noCargoGivesNoCopies() {
        LnpExpectations e = new LnpExpectations();
        e.cargo = LnpExpectations.Cargo.NONE;
        assertEquals(0, e.copies(400), 1e-9);
    }

    /**
     * A broad size distribution is worth flagging: Li et al. saw it at low N/P. The checklist runs over the data,
     * the model-free assessment and the fit together, which is how the panel calls it.
     */
    @Test
    public void broadSizeDistributionIsFlagged() {
        LnpModel m = new LnpModel(3, true);
        double[] truth = m.templateDefaults();
        truth[LnpModel.R0] = 200;
        truth[LnpModel.SIGMA] = 80;                  // sigma / R0 = 0.4, very broad
        double[][] d = LnpTestData.fromModel(m, truth, 0.008, 0.4, 500, 17);

        LnpAssessment.Report a = LnpAssessment.analyse(d[0], d[1], d[2], 0.008);
        LnpFitter.Result fit = new LnpFitter(m, d[0], d[1], d[2]).fit(truth.clone(), m.templateFixed(), false, () -> false);
        List<LnpExpectations.Check> checks = mrna().evaluate(d[0], d[1], d[2], a, fit, null);

        boolean flagged = false;
        for (LnpExpectations.Check c : checks)
            if (c.title.toLowerCase().contains("uniform") && c.status != LnpExpectations.Status.PASS) flagged = true;
        assertTrue("a sigma/R0 of 0.4 should not pass silently", flagged);
    }

    /** Every check must carry a title and an explanation; the panel shows both. */
    @Test
    public void everyCheckIsExplained() {
        LnpModel m = new LnpModel(3, true);
        double[][] d = LnpTestData.fromModel(m, m.templateDefaults(), 0.008, 0.4, 500, 19);
        LnpAssessment.Report a = LnpAssessment.analyse(d[0], d[1], d[2], 0.008);
        List<LnpExpectations.Check> checks = mrna().evaluate(d[0], d[1], d[2], a, null, null);
        assertFalse("the checklist should not be empty", checks.isEmpty());
        for (LnpExpectations.Check c : checks) {
            assertNotNull(c.status);
            assertTrue("a check needs a title", c.title != null && !c.title.isEmpty());
            assertTrue("a check needs a detail line: " + c.title, c.detail != null && !c.detail.isEmpty());
        }
    }
}
