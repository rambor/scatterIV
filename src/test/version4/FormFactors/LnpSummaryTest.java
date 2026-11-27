package version4.FormFactors;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The Summary tab: the brief that collects what every other tab concluded.
 *
 * <p>The brief is assembled from text the tabs have already displayed, so the test that matters is that it
 * carries the conclusions and not the data — the panel makes that claim to the user and it should not rest on
 * a comment.</p>
 */
public class LnpSummaryTest {

    @BeforeClass
    public static void headless() {
        System.setProperty("java.awt.headless", "true");
        // keep the tests away from a real settings file
        System.setProperty("scatter.configDir", System.getProperty("java.io.tmpdir") + "/scatter-test-config");
    }

    // ------------------------------------------------------------------ the brief

    @Test
    public void theBriefIsLabelledAndSkipsSectionsThatHaveNotBeenRun() {
        String b = new LnpBrief.Builder()
                .add("Sample", "Dataset: empty_lnp\n")
                .add("Structure (Bragg indexing)", "")          // not run
                .add("Populations", null)                       // not run
                .add("Assessment, fit and expectation checks", "reduced chi2 = 1.74")
                .build();

        assertTrue("sections are headed so the model can tell them apart", b.contains("==== Sample ===="));
        assertTrue(b.contains("==== Assessment, fit and expectation checks ===="));
        assertFalse("an empty section says nothing and should not be sent", b.contains("Bragg"));
        assertFalse(b.contains("Populations"));
        assertTrue(b.contains("empty_lnp"));
    }

    @Test
    public void anEmptyBriefIsEmptyRatherThanAHeadingWithNothingUnderIt() {
        assertEquals("", new LnpBrief.Builder().add("Populations", "   ").build());
    }

    // ------------------------------------------------------------------ the panel

    /**
     * A curve built here rather than taken from the shared {@code LnpTestData} fixture, deliberately. This test
     * asks only for something the panel will accept and fit; it does not need a known truth. Reaching for the
     * shared helper made this the one new file in an update that depended on an unchanged one, and an IDE
     * recompiling just the changed files then failed on it — a build break that had nothing to do with the
     * test. A file with no fixture dependency cannot break that way.
     */
    private static double[][] testData() {
        int n = 160;
        double[][] d = new double[3][n];
        java.util.Random rnd = new java.util.Random(7L);
        for (int i = 0; i < n; i++) {
            double q = 0.01 * Math.pow(0.3 / 0.01, i / (double) (n - 1));
            double clean = 1 / (1 + Math.pow(q * 180, 4));      // a smooth decay with a sphere-like falloff
            d[0][i] = q;
            d[2][i] = 0.02 * clean;
            d[1][i] = clean + d[2][i] * rnd.nextGaussian();
        }
        return d;
    }

    private static LnpPanel panel() {
        double[][] data = testData();
        return new LnpPanel(DatasetProvider.of(() -> 1, i -> "empty_lnp", i -> data, i -> 0.0));
    }

    @Test
    public void theSummaryTabIsThere() {
        javax.swing.JTabbedPane tabs = panel().tabs();
        boolean found = false;
        for (int i = 0; i < tabs.getTabCount(); i++) if ("Summary".equals(tabs.getTitleAt(i))) found = true;
        assertTrue("the Summary tab should be present", found);
    }

    /**
     * The brief is built from the panel's own reports, and it carries the dataset's identity and the fit — but
     * no scattering data. That last point is the privacy claim the tab makes to the user, so it is checked
     * rather than asserted in a comment.
     */
    @Test
    public void theBriefHoldsTheConclusionsAndNotTheData() {
        LnpPanel p = panel();
        p.assessNow();
        p.fitNow(true);
        String b = p.brief();
        assertFalse("the brief should not be empty after an assessment", b.trim().isEmpty());
        assertTrue(b.contains("==== Sample as declared by the user ===="));
        assertTrue(b.contains("empty_lnp"));

        // the data itself must not be in there: no run of many intensities, and no q column
        double[][] data = testData();
        int leaked = 0;
        for (int i = 20; i < 60; i++) {
            if (b.contains(String.format("%.4f", data[1][i]))) leaked++;
        }
        assertEquals("intensities must never reach the brief", 0, leaked);
        assertTrue("the brief is text, not a data file", b.length() < 200_000);
    }
}
