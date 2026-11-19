package version4.FormFactors;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The linearity test of the inset of Fig. 2C of Caselli et al. (2024): the index multiplier of each reflection
 * against its measured position, which should be a straight line through the origin of slope a / 2 pi.
 */
public class LnpIndexCheckTest {

    private static final double A = 174.0;

    @Test
    public void correctAssignmentIsCollinearAndGivesTheLatticeParameter() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m");
        LnpIndexCheck c = new LnpIndexCheck();
        c.set(im3m, A, LnpTestData.peaksOf(im3m, A, 4), 0.03);

        LnpIndexCheck.Fit f = c.fit();
        assertNotNull(f);
        assertEquals("all four reflections should be matched", 4, f.points.size());
        assertEquals("lattice parameter from the slope", A, f.a, 0.5);
        assertEquals("the peaks should agree with each other", 0, f.aSpread, 0.5);
        assertEquals("R squared", 1.0, f.r2, 1e-6);
        assertTrue("nothing should be missing from a complete pattern", f.absent.isEmpty());
    }

    /**
     * One reflection displaced by 4.5% -- a peak belonging to a second phase -- has to show up as scatter about the
     * line and be named, rather than being averaged into the mean lattice parameter.
     */
    @Test
    public void displacedReflectionIsIdentified() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m");
        List<LnpAssessment.Peak> peaks = new ArrayList<>(LnpTestData.peaksOf(im3m, A, 4));
        double moved = 2 * Math.PI * im3m.m[2] / A * 1.045;
        peaks.set(2, LnpTestData.peak(moved, 0.3, moved / 40, 40));

        LnpIndexCheck c = new LnpIndexCheck();
        c.set(im3m, A, peaks, 0.06);
        LnpIndexCheck.Fit f = c.fit();

        assertEquals("the displaced reflection should be named", "(211)", f.worstHkl);
        assertTrue("the spread should exceed 2% of the parameter, was " + f.aSpread, f.aSpread / f.a > 0.02);
        assertTrue("R squared should drop below 1, was " + f.r2, f.r2 < 0.999);
    }

    /**
     * The point of the absence list. Every reflection of Im3m is also allowed in Pn3m, so Pn3m fits those peaks
     * perfectly collinearly and linearity alone cannot tell the two apart. What rules Pn3m out is its (111), which
     * the data does not show.
     */
    @Test
    public void linearityAloneCannotSeparatePn3mFromIm3mSoAbsencesAreReported() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m"), pn3m = LnpLattice.byName("Pn3m");
        List<LnpAssessment.Peak> peaks = LnpTestData.peaksOf(im3m, A, 4);

        LnpIndexCheck c = new LnpIndexCheck();
        c.set(pn3m, A, peaks, 0.03);
        LnpIndexCheck.Fit f = c.fit();

        assertTrue("Pn3m does fit these peaks collinearly, which is why the absence matters",
                f.aSpread / f.a < 0.02);
        assertTrue("Pn3m predicts (111), which is absent here; got " + f.absent, f.absent.contains("(111)"));
        assertTrue("the text should warn about the absence",
                LnpIndexCheck.describe(f, pn3m).contains("Allowed but not observed"));
    }

    /** Two points always lie on a line, so the test must decline to draw a conclusion from them. */
    @Test
    public void twoReflectionsAreNotEnoughToConclude() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m");
        LnpIndexCheck c = new LnpIndexCheck();
        c.set(im3m, A, LnpTestData.peaksOf(im3m, A, 2), 0.03);
        assertTrue(LnpIndexCheck.describe(c.fit(), im3m).contains("says little"));
    }

    /** With nothing matched there is no line, and the component must not invent one. */
    @Test
    public void noMatchesGivesNoFit() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m");
        LnpIndexCheck c = new LnpIndexCheck();
        c.set(im3m, A, LnpTestData.peaksOf(im3m, A * 1.5, 4), 0.01);
        assertNull("peaks from a different parameter should match nothing", c.fit());
        assertTrue(LnpIndexCheck.describe(null, im3m).contains("fewer than two"));
    }
}
