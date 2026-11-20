package version4.FormFactors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * The user's own lipid library, which is the one part of this panel that keeps something.
 *
 * <p>Everything else in the LNP module recomputes from the data each time, so a bug costs a wrong number
 * on screen. This writes a file the user accumulates over months, and a bug here costs their work. The
 * tests are therefore about not losing it: that a lipid survives a round trip, that a damaged file is left
 * alone rather than overwritten, and that validation refuses the entries which would make the library
 * unusable later.
 */
public class LnpLipidLibraryTest {

    private File dir, file;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("lnplib").toFile();
        file = new File(dir, "lipids.json");
    }

    @After
    public void tearDown() {
        if (file.exists()) file.delete();
        if (dir != null) dir.delete();
    }

    private static LnpComposition.Lipid lipid(String name) {
        return new LnpComposition.Lipid(name, LnpComposition.Role.HELPER, 400, 1150, 0,
                "lamellar", "C40H80NO8P", 760.0, false, "a test lipid");
    }

    /** a lipid written out comes back with its numbers intact */
    @Test
    public void aLipidSurvivesARoundTrip() throws Exception {
        LnpLipidLibrary lib = new LnpLipidLibrary(file);
        lib.put(lipid("Testolipid"), null);
        lib.save();
        assertTrue("the file should have been written", file.exists());

        LnpLipidLibrary again = new LnpLipidLibrary(file);
        again.load();
        LnpComposition.Lipid back = null;
        for (LnpComposition.Lipid l : again.lipids()) if (l.name.equals("Testolipid")) back = l;
        assertNotNull("the lipid should still be there", back);
        assertEquals(400, back.electrons, 1e-9);
        assertEquals(1150, back.volume, 1e-9);
        assertEquals("lamellar", back.propensity);
        assertEquals(760.0, back.mw, 1e-9);
        assertFalse("a user lipid is not built in", back.builtIn);
    }

    /**
     * A library that could not be read must never be written over. The file is the user's own work and a
     * parse failure is far more likely to be a bad edit or a half-copied file than a reason to start again
     * from empty.
     */
    @Test
    public void adamagedFileIsNotOverwritten() throws Exception {
        Files.write(file.toPath(), "{ this is not json".getBytes("UTF-8"));
        LnpLipidLibrary lib = new LnpLipidLibrary(file);
        try { lib.load(); } catch (Exception expected) { /* the point is what happens next */ }
        try {
            lib.save();
            fail("saving over an unreadable library should be refused");
        } catch (Exception e) {
            assertTrue("and it should say why: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().toLowerCase().contains("not overwriting"));
        }
        String still = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue("the user's file must be untouched", still.startsWith("{ this is not json"));
    }

    /** a library file that is simply absent is not an error: it is a user who has not added one yet */
    @Test
    public void amissingFileIsAnEmptyLibrary() throws Exception {
        LnpLipidLibrary lib = new LnpLipidLibrary(new File(dir, "nothing-here.json"));
        lib.load();
        assertNotNull(lib.lipids());
        assertNull("no error should be recorded for a file that was never created", lib.loadError);
    }

    /** the entries that would break a composition later are refused at the point of entry */
    @Test
    public void validationRefusesUnusableEntries() {
        assertFalse("a nameless lipid is not usable",
                LnpComposition.lookup("") != null);
        assertFalse("zero volume would divide by zero in every contrast",
                LnpLipidLibrary.validate(new LnpComposition.Lipid("Zero", LnpComposition.Role.HELPER,
                        400, 0, 0, "lamellar", "", 700, false, ""), null).isEmpty());
        assertFalse("a blank name cannot be looked up again",
                LnpLipidLibrary.validate(new LnpComposition.Lipid("  ", LnpComposition.Role.HELPER,
                        400, 1000, 0, "lamellar", "", 700, false, ""), null).isEmpty());
        assertTrue("a sound entry passes",
                LnpLipidLibrary.validate(lipid("Fine"), null).isEmpty());
    }

    /** a user lipid may not quietly replace a built-in one of the same name */
    @Test
    public void aUserLipidCannotShadowABuiltInSilently() {
        java.util.List<String> problems =
                LnpLipidLibrary.validate(lipid("Cholesterol"), null);
        assertFalse("redefining Cholesterol should be flagged", problems.isEmpty());
    }
}
