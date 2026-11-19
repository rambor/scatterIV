package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The electron-density contrast of the bilayer chain region.
 *
 * <p>This is the reference the whole density scale is anchored to, so everything else is quoted relative to
 * it. It used to be a flat 0.29 e/A^3 of chain with 1000 A^3 of chain per molecule, which returned the same
 * -0.044 for every sterol-free composition: a cardiolipin bilayer, a PC/PE mixture and pure POPC were
 * indistinguishable, and cardiolipin's four chains were counted as two chains' worth of volume.
 *
 * <p>Each lipid now contributes its own chains, built from the group volumes of Nagle &amp; Tristram-Nagle,
 * BBA 1469, 159 (2000), so the arithmetic can be checked against their measured hydrocarbon volumes.
 */
public class LnpTailContrastTest {

    private static LnpComposition.Lipid lipid(String name) {
        LnpComposition.Lipid l = LnpComposition.lookup(name);
        assertNotNull("no lipid in the library is called '" + name + "'", l);
        return l;
    }

    private static LnpComposition of(Object... nameThenPercent) {
        LnpComposition c = new LnpComposition();
        for (int i = 0; i < nameThenPercent.length; i += 2)
            c.add(lipid((String) nameThenPercent[i]), ((Number) nameThenPercent[i + 1]).doubleValue());
        return c;
    }

    /**
     * DPPC's hydrocarbon volume is measured: 825 A^3 in the gel at 20 C and 913 A^3 in the fluid at 50 C
     * (Nagle &amp; Tristram-Nagle 2000, Table 1). Building it from methylene volumes has to reproduce that,
     * or none of the contrasts derived from it mean anything.
     */
    @Test
    public void hydrocarbonVolumesMatchTheMeasuredOnes() {
        LnpComposition.Lipid dppc = lipid("DPPC (16:0 PC)");
        assertEquals("gel DPPC hydrocarbon volume", 825, dppc.tailVolume(LnpComposition.V_CH2_GEL), 15);
        assertEquals("fluid DPPC hydrocarbon volume", 913, dppc.tailVolume(LnpComposition.V_CH2_FLUID), 15);
    }

    /** and the chain-region density that follows is the slab average, below the methylene density */
    @Test
    public void theChainDensityIsTheSlabAverage() {
        LnpComposition.Lipid dppc = lipid("DPPC (16:0 PC)");
        double rho = dppc.tailElectrons() / dppc.tailVolume(LnpComposition.V_CH2_GEL);
        assertEquals("gel chain-region density", 0.292, rho, 0.004);
        assertTrue("it must be below the methylene density of 8/25.9 = 0.309, because the terminal methyls "
                + "take twice the volume for one more electron", rho < 8.0 / LnpComposition.V_CH2_GEL);
    }

    /** cardiolipin has four chains, and they have to be counted as four */
    @Test
    public void cardiolipinHasFourChains() {
        assertEquals(4, lipid("Cardiolipin TMCL (14:0)4").chainCount());
        assertEquals(4, lipid("Cardiolipin TOCL (18:1)4").chainCount());
        assertEquals(2, lipid("DPPC (16:0 PC)").chainCount());

        double cl = lipid("Cardiolipin TMCL (14:0)4").tailVolume(LnpComposition.V_CH2_GEL);
        double pc = lipid("DMPC (14:0 PC)").tailVolume(LnpComposition.V_CH2_GEL);
        assertEquals("the same chains, twice as many of them", 2.0, cl / pc, 0.01);
    }

    /**
     * The state of the chains is the dominant term, which is the point of reading it off a wide-angle
     * dataset: the same lipid frozen and melted are different scatterers by a wide margin.
     */
    @Test
    public void gelAndFluidAreMaterriallyDifferent() {
        LnpComposition c = of("DMPC (14:0 PC)", 50, "Cardiolipin TMCL (14:0)4", 50);
        double gel = c.innerShellContrast(true), fluid = c.innerShellContrast(false);
        assertTrue("both are below buffer", gel < 0 && fluid < 0);
        assertTrue("melted chains are less dense, so the contrast is more negative", fluid < gel);
        assertTrue("and the difference is large, not a detail: gel " + gel + ", fluid " + fluid,
                Math.abs(fluid - gel) > 0.02);
    }

    /** a sterol raises the chain-region density towards buffer, which is why it changes the whole scale */
    @Test
    public void cholesterolRaisesTheChainDensity() {
        double without = of("POPC (16:0-18:1 PC)", 100).innerShellContrast(false);
        double with = of("POPC (16:0-18:1 PC)", 50, "Cholesterol", 50).innerShellContrast(false);
        assertTrue("cholesterol at 0.343 e/A^3 is denser than chains, so the contrast moves towards zero"
                + " (without " + without + ", with " + with + ")", with > without);
    }

    /** the old flat figure gave every sterol-free mixture the same answer; it must not any more */
    @Test
    public void differentLipidsGiveDifferentAnswers() {
        double dppcGel = of("DPPC (16:0 PC)", 100).innerShellContrast(true);
        double dmpcGel = of("DMPC (14:0 PC)", 100).innerShellContrast(true);
        assertNotEquals("a 16:0 and a 14:0 bilayer are not the same scatterer", dppcGel, dmpcGel, 1e-6);
    }

    /** a lipid whose chains are too branched to describe still returns something usable */
    @Test
    public void anUndescribedLipidFallsBack() {
        assertEquals("phytantriol's chains are branched and not described", 0, lipid("Phytantriol").chainCount());
        double rho = of("Phytantriol", 50, "DOPC", 50).innerShellContrast(false);
        assertTrue("a plausible chain contrast is still returned: " + rho, rho < 0 && rho > -0.12);
    }

    /** an empty composition cannot divide by zero */
    @Test
    public void anEmptyCompositionIsSafe() {
        double rho = new LnpComposition().innerShellContrast(true);
        assertTrue("a default is returned rather than a NaN", Double.isFinite(rho) && rho < 0);
    }
}
