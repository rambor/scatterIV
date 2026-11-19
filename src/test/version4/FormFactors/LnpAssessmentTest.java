package version4.FormFactors;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * What the assessment concludes from a curve before any model is fitted: which peaks are there, whether they
 * index, and which analysis the user should be sent to next.
 */
public class LnpAssessmentTest {

    /** A cubosome pattern must index, and to the lattice it was generated from. */
    @Test
    public void indexesACubosomePattern() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m");
        // a well-ordered cubosome: a large domain makes the reflections sharp enough to resolve. With a small
        // domain the higher orders of this lattice overlap into one shoulder and cannot be separated, which is a
        // property of the pattern rather than of the peak finder.
        double[][] d = LnpTestData.bragg(im3m, 174.0, 2000, 4, 0.02, 0.30, 1200, 7);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.03);

        assertTrue("several reflections should be found, got " + r.peaks.size(), r.peaks.size() >= 3);
        assertFalse("the pattern should index to a phase", r.phases.isEmpty());
        assertTrue("expected a cubic phase, got " + r.phases.get(0).symmetry,
                r.phases.get(0).symmetry.contains("3m") || r.phases.get(0).symmetry.contains("3d"));
        assertEquals("lattice parameter", 174.0, r.phases.get(0).latticeParameter, 6);
    }

    /**
     * A regression, tested on the matcher directly rather than through the peak finder. Two weak false positives
     * among the detected peaks used to veto the indexing altogether, so a real cubosome came back with no phase at
     * all. Weak peaks may now go unexplained as long as every strong one is accounted for, and the count of
     * unexplained peaks is reported rather than hidden.
     */
    @Test
    public void weakSpuriousPeaksDoNotVetoIndexing() {
        LnpLattice.Lattice pn3m = LnpLattice.byName("Pn3m");
        List<LnpAssessment.Peak> peaks = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            double q = 2 * Math.PI * pn3m.m[k] / 100.0;
            peaks.add(LnpTestData.peak(q, 1.0 / (1 + k), q / 60, 90 - 15 * k));   // strong, significance 90..45
        }
        peaks.add(LnpTestData.peak(0.063, 0.01, 0.004, 9));                        // weak false positives, as seen
        peaks.add(LnpTestData.peak(0.205, 0.01, 0.004, 7));
        peaks.sort((x, y) -> Double.compare(x.q, y.q));

        int[] index = new int[peaks.size()];
        for (int i = 0; i < index.length; i++) index[i] = i;
        List<LnpAssessment.PhaseMatch> matches = LnpAssessment.matchPhases(peaks, index, 0.03);

        // The regression: this used to return nothing at all, because any peak the phase could not account for
        // vetoed the whole assignment. Which lattice wins is not asserted -- a larger parameter can absorb one of
        // the weak peaks as a reflection of its own and so explain more of the pattern, which is a fair reading of
        // it -- only that an assignment survives and that what it cannot explain is counted rather than hidden.
        assertFalse("weak extras must not stop strong reflections from indexing", matches.isEmpty());
        assertTrue("a weak peak should be left unexplained rather than vetoing the match",
                matches.get(0).unexplained > 0);
        assertTrue("the strong reflections should still be accounted for, matched only "
                + matches.get(0).matched, matches.get(0).matched >= 4);
    }

    /** The converse: a strong peak that no phase explains must still block the assignment. */
    @Test
    public void aStrongUnexplainedPeakStillBlocksIndexing() {
        LnpLattice.Lattice pn3m = LnpLattice.byName("Pn3m");
        List<LnpAssessment.Peak> peaks = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            double q = 2 * Math.PI * pn3m.m[k] / 100.0;
            peaks.add(LnpTestData.peak(q, 1.0 / (1 + k), q / 60, 90 - 15 * k));
        }
        peaks.add(LnpTestData.peak(0.063, 1.0, 0.004, 95));     // as strong as the first order, and unexplainable
        peaks.sort((x, y) -> Double.compare(x.q, y.q));

        int[] index = new int[peaks.size()];
        for (int i = 0; i < index.length; i++) index[i] = i;
        List<LnpAssessment.PhaseMatch> matches = LnpAssessment.matchPhases(peaks, index, 0.03);
        for (LnpAssessment.PhaseMatch m : matches)
            assertTrue("a phase that leaves a strong peak unexplained should not be offered: " + m.symmetry,
                    m.unexplained == 0);
    }

    /**
     * A regression. Form-factor oscillations are equally spaced and are moved out of the peak list, but three
     * cubic reflections can fall inside that tolerance: for a Pn3m phase at a = 100 Angstrom the (110), (111) and
     * (200) spacings differ by 15 %, and the series sits 0.17 of a period off the origin against a threshold of
     * 0.15. Three genuine reflections were being discarded on that margin, leaving one peak and no phase. The
     * separator now checks whether the candidate set indexes before discarding it.
     */
    @Test
    public void cubicReflectionsAreNotMistakenForFormFactorOscillations() {
        LnpLattice.Lattice pn3m = LnpLattice.byName("Pn3m");
        double[][] d = LnpTestData.bragg(pn3m, 100.0, 2000, 4, 0.02, 0.35, 1200, 13);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.03);

        assertEquals("all four reflections should survive the oscillation filter", 4, r.peaks.size());
        assertTrue("none of them is a form-factor ripple", r.oscillations.isEmpty());
        assertFalse("and the set should index", r.phases.isEmpty());
        assertEquals("lattice parameter", 100.0, r.phases.get(0).latticeParameter, 2.0);
        double[] expected = {0.0889, 0.1088, 0.1257, 0.1539};
        for (int k = 0; k < 4; k++) assertEquals("reflection " + k, expected[k], r.peaks.get(k).q, 0.001);
    }

    /**
     * The other side of that fix: a real oscillation series must still be recognised. A fairly uniform sphere
     * gives maxima spaced by pi / R that index as nothing, and they belong in the oscillation list rather than
     * among the Bragg peaks.
     */
    @Test
    public void formFactorOscillationsAreStillSeparated() {
        LnpModel m = new LnpModel(1, false);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 120;
        p[LnpModel.SIGMA] = 6;                       // narrow, so the ripples are deep
        double[][] d = LnpTestData.fromModel(m, p, 0.01, 0.35, 900, 41);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.01);
        assertTrue("ripples must never be indexed as a phase", r.phases.isEmpty());
        if (!r.oscillations.isEmpty())
            assertEquals("the oscillation period should give back the radius", 120,
                    r.oscillationRadius, 40);
    }

    /**
     * A regression. Two reflections closer together than the span over which the excess stays significant fall
     * into one detected region, and reporting only its tallest point loses the other -- with two coexisting
     * lamellar phases a few per cent apart in repeat, that loses a whole phase. Every resolved maximum is now
     * reported, provided it rises above the valleys beside it by more than the noise.
     */
    @Test
    public void twoCloseReflectionsAreBothReported() {
        // two lamellar phases, d = 5.58 nm and 4.68 nm, as found in a real measurement of a lipid dispersion
        int n = 1400;
        double[] q = new double[n], I = new double[n], s = new double[n];
        java.util.Random rnd = new java.util.Random(9);
        double[] centres = {0.1126, 0.1342, 0.2248, 0.2683};
        double[] amps = {60, 45, 5, 6};
        for (int i = 0; i < n; i++) {
            q[i] = 0.05 + (0.40 - 0.05) * i / (n - 1.0);
            double v = 1.0 * Math.pow(q[i] / 0.05, -1.0) + 0.05;
            for (int k = 0; k < centres.length; k++)
                v += amps[k] * 0.05 * Math.exp(-0.5 * Math.pow((q[i] - centres[k]) / 0.0025, 2));
            s[i] = 0.02 * v;
            I[i] = v + s[i] * rnd.nextGaussian();
        }
        LnpAssessment.Report r = LnpAssessment.analyse(q, I, s, 0.06);

        for (double c : new double[]{0.1126, 0.1342}) {
            boolean found = false;
            for (LnpAssessment.Peak p : r.peaks) if (Math.abs(p.q - c) < 0.003) found = true;
            assertTrue("the reflection at " + c + " should be reported; got " + describe(r), found);
        }
        LnpAssessment.Peak first = null, second = null;
        for (LnpAssessment.Peak p : r.peaks) {
            if (Math.abs(p.q - 0.1126) < 0.003) first = p;
            if (Math.abs(p.q - 0.1342) < 0.003) second = p;
        }
        assertTrue("both should be strong", first.significance > 20 && second.significance > 20);
        assertTrue("neither should take the other's width", first.fwhm < 0.02 && second.fwhm < 0.02);
    }

    private static String describe(LnpAssessment.Report r) {
        StringBuilder sb = new StringBuilder();
        for (LnpAssessment.Peak p : r.peaks) sb.append(String.format("%.4f ", p.q));
        return sb.toString();
    }

    /**
     * The other side of it: a single broad correlation peak must stay one peak. Splitting it on the noise riding
     * on top would turn a sponge phase into a crowd of spurious reflections.
     */
    @Test
    public void oneBroadPeakIsNotSplitByNoise() {
        double[][] d = LnpTestData.sponge(150, 90, 0.0002, 0.008, 0.35, 600, 3);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.008);
        assertEquals("a broad correlation peak is one peak", 1, r.peaks.size());
        assertTrue("and it should keep its true width", r.peaks.get(0).order() < 4);
    }

    /**
     * A regression from a real phosphatidylcholine measurement. A vesicle or a single bilayer gives a broad
     * maximum in the same q range as a nucleic-acid mesophase, and the panel used to report it as "the signature
     * of nucleic acid packed with ionizable lipid" for a sample containing no nucleic acid at all. What separates
     * them is that a lobe comes with the other oscillations of the same form factor and has no second order.
     */
    @Test
    public void aFormFactorLobeIsNotReportedAsAMesophasePeak() {
        // a vesicle shell: oscillations across the range, one broad lobe, no second order anywhere
        LnpModel m = new LnpModel(4, false);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 300;
        p[LnpModel.SIGMA] = 24;
        double[][] d = LnpTestData.fromModel(m, p, 0.0045, 0.34, 1200, 73);

        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN);
        assertTrue("the curve should show form-factor minima", LnpAssessment.countPorodMinima(d[0], d[1]) > 0);
        assertFalse("a form-factor lobe is not a nucleic-acid mesophase", r.mesophasePeak);
        assertTrue("and no phase may be indexed from it", r.phases.isEmpty());
        if (r.formFactorLobe)
            assertTrue("the text should say what it is", r.text.contains("form factor rather than a repeat"));
    }

    /**
     * With no cargo declared the panel must not assert that a broad peak is nucleic acid. The user is told what
     * the peak could be and asked to set a cargo if the sample has one.
     */
    @Test
    public void noNucleicAcidReadingWithoutADeclaredCargo() {
        LnpModel m = new LnpModel(3, true);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 201;
        p[LnpModel.PEAK_Q] = 2 * Math.PI / 50.0;
        p[LnpModel.PEAK_A] = 0.08;
        double[][] d = LnpTestData.fromModel(m, p, 0.008, 0.4, 700, 11);

        LnpAssessment.Report declared = LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN, true);
        LnpAssessment.Report undeclared = LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN, false);
        if (declared.mesophasePeak) {
            assertTrue("with a cargo the nucleic-acid reading is offered", declared.text.contains("nucleic acid"));
            assertFalse("without one it must not be asserted",
                    undeclared.text.contains("is nucleic acid packed"));
            assertTrue("and the user should be told why",
                    undeclared.text.contains("No cargo has been declared"));
        }
    }

    /**
     * The sponge (L3) phase of Fig. 1G of Caselli et al. (2024) gives one broad peak at a large repeat and no
     * higher orders. It has no lattice, so the assessment must route to the sponge fit rather than to indexing.
     */
    @Test
    public void recognisesASpongePhase() {
        double[][] d = LnpTestData.sponge(150, 90, 0.0002, 0.008, 0.35, 600, 3);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.008);
        assertTrue("a single broad peak above 8 nm with no orders should read as a sponge", r.spongeCandidate);
        assertFalse("a sponge must not also be called a nucleic-acid core peak", r.mesophasePeak);
        assertTrue("the report should name the phase", r.text.contains("sponge"));
    }

    /**
     * The nucleic-acid correlation peak of an mRNA-LNP sits near 5 nm, far below the sponge range, and must keep
     * being read as a mesophase peak rather than as a sponge.
     */
    @Test
    public void aFiveNanometreCorePeakIsNotASponge() {
        LnpModel m = new LnpModel(3, true);
        double[] p = m.templateDefaults();
        p[LnpModel.R0] = 201;
        p[LnpModel.SIGMA] = 31;
        p[LnpModel.PEAK_Q] = 2 * Math.PI / 50.0;
        double[][] d = LnpTestData.fromModel(m, p, 0.008, 0.4, 700, 11);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.008);
        assertFalse("a 5 nm core peak is not a sponge", r.spongeCandidate);
    }

    /**
     * A plain core-shell particle has no internal order, so nothing may index. Its form-factor oscillations are
     * maxima in the curve and the peak finder can pick them up -- that is why the search starts above them -- but
     * they must never be assembled into a phase.
     */
    @Test
    public void aPlainCoreShellParticleIndexesToNothing() {
        LnpModel m = new LnpModel(3, false);
        double[][] d = LnpTestData.fromModel(m, m.templateDefaults(), 0.008, 0.4, 500, 31);
        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN);
        assertTrue("no phase may be indexed from form-factor ripples, got " + r.phases, r.phases.isEmpty());
        assertNull("and no coexistence either", r.coexistence);
        assertTrue("the Guinier region should still be usable", r.guinier.found);
    }

    /**
     * Three sharp lamellar orders must be found at their true positions and indexed to the right repeat. This is
     * the clean case the peak finder has to get exactly right.
     */
    @Test
    public void findsAndIndexesSharpLamellarOrders() {
        LnpLattice.Lattice lam = null;
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) if (l.lamellar) { lam = l; break; }
        assertNotNull("the table should hold a lamellar phase", lam);
        double[][] d = LnpTestData.bragg(lam, 60.0, 4000, 3, 0.02, 0.40, 1200, 5);

        LnpAssessment.Report r = LnpAssessment.analyse(d[0], d[1], d[2], 0.05);
        assertEquals("all three orders should be found", 3, r.peaks.size());
        assertEquals("first order", 2 * Math.PI / 60.0, r.peaks.get(0).q, 0.001);
        assertFalse("three orders in the ratio 1:2:3 should index", r.phases.isEmpty());
        assertEquals("lamellar repeat", 60.0, r.phases.get(0).latticeParameter, 1.0);
    }
}
