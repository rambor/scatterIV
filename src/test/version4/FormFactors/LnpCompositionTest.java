package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What a declared lipid composition is allowed to conclude.
 *
 * <p>The composition is not decoration: it seeds the chain density that the whole density scale is referenced
 * to, decides whether a PEG corona exists at all, and tells the user which phase to expect before they fit
 * anything. A wrong sentence here sends the analysis the wrong way at step one, so the thresholds that produce
 * those sentences are worth pinning down.
 */
public class LnpCompositionTest {

    private static LnpComposition of(Object... nameThenPercent) {
        LnpComposition c = new LnpComposition();
        for (int i = 0; i < nameThenPercent.length; i += 2) {
            String name = (String) nameThenPercent[i];
            LnpComposition.Lipid l = LnpComposition.lookup(name);
            assertNotNull("no lipid in the library is called '" + name + "'", l);
            c.add(l, ((Number) nameThenPercent[i + 1]).doubleValue());
        }
        return c;
    }

    /**
     * A phospholipid with cholesterol makes bilayers. This used to fall through every threshold and report an
     * amorphous ionizable-lipid core with RNA in it: cholesterol carries no shape preference, so it belongs
     * outside the denominator, and PC/cholesterol 1:1 gave a lamellar fraction of exactly 0.5 against a test
     * for more than 0.5.
     */
    @Test
    public void phosphatidylcholineWithCholesterolIsLamellar() {
        for (String pc : new String[]{"POPC (16:0-18:1 PC)", "DPPC (16:0 PC)", "DMPC (14:0 PC)"}) {
            String phase = of(pc, 50, "Cholesterol", 50).expectedPhase();
            assertTrue(pc + " should be read as lamellar, was: " + phase, phase.contains("lamellar"));
            assertFalse(pc + " has no ionizable lipid, so it cannot have an ionizable-lipid core: " + phase,
                    phase.contains("ionizable"));
            assertFalse(pc + " has no cargo, so RNA should not be mentioned: " + phase, phase.contains("RNA"));
        }
    }

    /** and at half cholesterol the chains cannot be in a gel phase, which is what the wide-angle data is read against */
    @Test
    public void halfCholesterolIsCalledLiquidOrdered() {
        String phase = of("DPPC (16:0 PC)", 50, "Cholesterol", 50).expectedPhase();
        assertTrue("50 mol% sterol is the liquid-ordered phase: " + phase,
                phase.contains("liquid-ordered") || phase.contains("Lₒ"));
    }

    /** a little sterol does not abolish the gel phase, so the sentence must not claim it does */
    @Test
    public void aLittleCholesterolLeavesTheGelPhase() {
        String phase = of("DPPC (16:0 PC)", 90, "Cholesterol", 10).expectedPhase();
        assertTrue("10 mol% sterol is still lamellar: " + phase, phase.contains("lamellar"));
        assertFalse("10 mol% sterol does not make it liquid-ordered: " + phase, phase.contains("liquid-ordered"));
    }

    /** an actual LNP still reports as one: the new branch must not swallow the ionizable-lipid case */
    @Test
    public void anIonizableLipidFormulationIsStillReadAsAnLnp() {
        LnpComposition c = of("DLin-MC3-DMA", 50, "DSPC", 10, "Cholesterol", 38.5, "DMG-PEG2000", 1.5);
        String phase = c.expectedPhase();
        assertFalse("an LNP should not be described as a plain bilayer: " + phase,
                phase.startsWith("lamellar bilayer"));
        assertTrue("PEG-lipid is present, so a corona is expected", c.pegMolPercent() > 0.01);
    }

    /**
     * The chain density seeded from PC/cholesterol is the reference the whole density scale hangs from. Water is
     * 0.334 e/A^3 and lipid chains sit a little below it, so the contrast is small and negative; a sign error
     * here would inverse the entire density profile.
     */
    @Test
    public void bilayerChainContrastIsSmallAndNegative() {
        LnpComposition c = of("POPC (16:0-18:1 PC)", 50, "Cholesterol", 50);
        for (boolean gel : new boolean[]{true, false}) {
            double rho = c.innerShellContrast(gel);
            assertTrue("chains are less dense than water, so the contrast is negative: " + rho, rho < 0);
            assertTrue("and it stays a chain-like contrast, not a headgroup one: " + rho, rho > -0.09);
        }
        // Built from the chains themselves this is about -0.021 frozen and -0.040 melted. It used to be a flat
        // -0.024 for every composition, from a single assumed chain density; the number moved because the
        // calculation changed, not because the lipid did.
        assertEquals("gel", -0.021, c.innerShellContrast(true), 0.006);
        assertEquals("fluid", -0.040, c.innerShellContrast(false), 0.006);
    }

    /** with no PEG-lipid declared there is no corona to put in the model */
    @Test
    public void noPegLipidMeansNoCorona() {
        LnpComposition c = of("POPC (16:0-18:1 PC)", 50, "Cholesterol", 50);
        assertEquals(0.0, c.pegMolPercent(), 1e-9);
        assertEquals("no PEG-lipid, so the corona thickness is zero", 0.0, c.corona(500, 540)[0], 1e-9);
    }
}
