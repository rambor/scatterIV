package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The units check. Reading nm⁻¹ data as Å⁻¹ makes every distance ten times too small without raising an error,
 * and the panel's conclusions are decided by windows in absolute Å⁻¹, so the wrong scale runs a different branch
 * of the reasoning rather than producing the same answer scaled.
 */
public class LnpUnitsTest {

    private static double[] range(double lo, double hi, int n) {
        double[] q = new double[n];
        for (int i = 0; i < n; i++) q[i] = lo + (hi - lo) * i / (n - 1.0);
        return q;
    }

    /** An ordinary SAXS curve in Å⁻¹ must pass without comment. */
    @Test
    public void anAngstromCurveIsAccepted() {
        LnpUnits u = LnpUnits.inspect(range(0.0045, 0.34, 500));   // the phosphatidylcholine measurement
        assertEquals(LnpUnits.Verdict.ANGSTROM, u.verdict);
        assertFalse(u.worthWarning());
        assertEquals("", u.message());
    }

    /** The same curve left in nm⁻¹ has to be caught. */
    @Test
    public void aNanometreCurveIsCaught() {
        LnpUnits u = LnpUnits.inspect(range(0.0855, 5.0, 500));    // the lipid dispersion, as written in the file
        assertEquals(LnpUnits.Verdict.NANOMETRE, u.verdict);
        assertTrue(u.worthWarning());
        assertTrue("it should name the conversion the user needs", u.message().contains("convert nm to Å"));
        assertTrue("and say why its reading is impossible", u.message().contains("nothing larger than"));
    }

    /** A curve that stops in the overlap region must say it cannot tell, rather than guess. */
    @Test
    public void anAmbiguousRangeIsNotGuessed() {
        LnpUnits u = LnpUnits.inspect(range(0.01, 1.2, 300));
        assertEquals(LnpUnits.Verdict.UNCLEAR, u.verdict);
        assertTrue(u.worthWarning());
        assertTrue(u.message().contains("cannot be told"));
    }

    /** The note that goes into a saved report carries the range and flags anything unresolved. */
    @Test
    public void theReportNoteRecordsWhatWasAssumed() {
        assertFalse(LnpUnits.inspect(range(0.0045, 0.34, 100)).note().contains("check the units"));
        assertTrue(LnpUnits.inspect(range(0.0855, 5.0, 100)).note().contains("check the units"));
    }

    /** Empty or unusable input must not throw. */
    @Test
    public void emptyInputIsHandled() {
        assertEquals(LnpUnits.Verdict.UNCLEAR, LnpUnits.inspect(new double[0]).verdict);
        assertEquals(LnpUnits.Verdict.UNCLEAR, LnpUnits.inspect(null).verdict);
        assertEquals(LnpUnits.Verdict.UNCLEAR, LnpUnits.inspect(new double[]{0, -1}).verdict);
    }
}
