package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Which analysis an assessment points to.
 *
 * <p>This was a chain of else-ifs written in the order the flags were added, which is not the order they
 * should be consulted in, and the answer was then stored in a field that outlived the assessment it came
 * from. Both faults showed up at once on real liposome data: a fit with a reduced chi-squared under 6 was
 * told to switch to the model that gives 557.
 */
public class LnpSuggestionTest {

    private static LnpAssessment.Report report() {
        LnpAssessment.Report r = new LnpAssessment.Report();
        r.peaks = new java.util.ArrayList<>();
        r.phases = new java.util.ArrayList<>();
        return r;
    }

    private static LnpAssessment.Peak aPeak() {
        return LnpTestData.peak(0.082, 1e-3, 0.027, 25);
    }

    /**
     * A form-factor lobe decides what the broad maxima mean, so it has to be asked before anything that reads
     * those same maxima differently. Tested last, the mesophase branch answered first and sent bilayer data to
     * a cargo-bearing mRNA-LNP, where the peak component sat on the lobe and invented a 51 A spacing.
     */
    @Test
    public void aFormFactorLobeOutranksAMesophasePeak() {
        LnpAssessment.Report r = report();
        r.peaks.add(aPeak());
        r.formFactorLobe = true;
        r.mesophasePeak = true;                     // both set: the lobe is the more specific finding
        assertEquals(LnpPanel.Preset.VESICLE, LnpPanel.suggestionFor(r).preset);
    }

    /** and it outranks the no-peaks case, which would otherwise send a hollow shell to a filled core */
    @Test
    public void aFormFactorLobeOutranksTheEmptyCase() {
        LnpAssessment.Report r = report();
        r.formFactorLobe = true;
        assertEquals(LnpPanel.Preset.VESICLE, LnpPanel.suggestionFor(r).preset);
    }

    /** an indexed lattice is a positive identification of the whole pattern and still comes first */
    @Test
    public void anIndexedLatticeComesFirst() {
        LnpAssessment.Report r = report();
        r.formFactorLobe = true;
        r.phases.add(new LnpAssessment.PhaseMatch());
        LnpPanel.Suggestion s = LnpPanel.suggestionFor(r);
        assertNull("the lattice fit is not one of the particle presets", s.preset);
        assertEquals("lattice", s.path);
    }

    /** as does a sponge */
    @Test
    public void aSpongeComesFirst() {
        LnpAssessment.Report r = report();
        r.formFactorLobe = true;
        r.spongeCandidate = true;
        assertEquals("sponge", LnpPanel.suggestionFor(r).path);
    }

    /** a real mesophase peak, with no lobe found, still routes to the mRNA-LNP */
    @Test
    public void aMesophasePeakAloneStillRoutesToTheLnp() {
        LnpAssessment.Report r = report();
        r.peaks.add(aPeak());
        r.mesophasePeak = true;
        assertEquals(LnpPanel.Preset.MRNA, LnpPanel.suggestionFor(r).preset);
    }

    /** featureless data routes to the empty LNP */
    @Test
    public void featurelessDataRoutesToTheEmptyLnp() {
        assertEquals(LnpPanel.Preset.EMPTY, LnpPanel.suggestionFor(report()).preset);
    }

    /** and no assessment suggests nothing rather than throwing */
    @Test
    public void noAssessmentSuggestsNothing() {
        LnpPanel.Suggestion s = LnpPanel.suggestionFor(null);
        assertNull(s.preset);
        assertEquals("fit", s.path);
    }

    /**
     * The suggestion must be a function of the assessment alone. Held in a field it goes stale, and the fit
     * then reports a recommendation that the current assessment does not make.
     */
    @Test
    public void theSameAssessmentAlwaysGivesTheSameAnswer() {
        LnpAssessment.Report r = report();
        r.peaks.add(aPeak());
        r.formFactorLobe = true;
        LnpPanel.Preset first = LnpPanel.suggestionFor(r).preset;
        for (int i = 0; i < 5; i++)
            assertEquals("the answer must not drift", first, LnpPanel.suggestionFor(r).preset);
    }

    /** a reduced chi-squared under the good-fit mark is better evidence than any of this */
    @Test
    public void aGoodFitIsBelowTheThreshold() {
        assertTrue("the fit that prompted this was 5.88 and must count as good", 5.88 < LnpPanel.GOOD_FIT);
        assertTrue("the filled-core failure on the same data was 557 and must not", 557.45 > LnpPanel.GOOD_FIT);
    }
}
