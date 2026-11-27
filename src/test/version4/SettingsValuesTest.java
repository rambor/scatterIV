package version4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * The values behind the Settings tab: defaults, the rules for typed values, number formats in any
 * language, and saving to / loading from the settings file.
 */
public class SettingsValuesTest {

    private static final int ROUNDS = 5, CUTOFFS = 6, CS = 6;   // choices in the Settings form's boxes

    // ---------------------------------------------------------------- defaults

    @Test
    public void defaultsAreTheDialogsOriginalValues() {
        SettingsValues v = new SettingsValues();
        assertEquals(0.02, v.qmin, 0);
        assertEquals(0.14, v.qmax, 0);
        assertEquals(0.4, v.qmaxForPDB, 0);
        assertEquals(97, v.defaultDmax, 0);
        assertTrue(v.excludeWaters);
        assertEquals(2, v.refinementRoundsIndex);
        assertEquals(3, v.rejectionCutoffIndex);
        assertEquals(0, v.cIndex);
    }

    // ---------------------------------------------------------------- numbers

    @Test
    public void numbersAreReadWithAPointOrACommaAndSpaces() {
        assertEquals(0.02, SettingsValues.parse("0.02"), 0);
        assertEquals(0.02, SettingsValues.parse("0,02"), 0);
        assertEquals(97, SettingsValues.parse(" 97 "), 0);
        assertEquals(1.5e-2, SettingsValues.parse("1.5e-2"), 0);
        assertTrue(Double.isNaN(SettingsValues.parse("")));
        assertTrue(Double.isNaN(SettingsValues.parse(null)));
        assertTrue(Double.isNaN(SettingsValues.parse("abc")));
        assertTrue("thousands separators are not guessed", Double.isNaN(SettingsValues.parse("1,000,5")));
        assertTrue(Double.isNaN(SettingsValues.parse("NaN")));
        assertTrue(Double.isNaN(SettingsValues.parse("Infinity")));
    }

    @Test
    public void numbersAreWrittenWithAPointWhateverTheComputersLanguage() {
        Locale before = Locale.getDefault();
        try {
            for (Locale l : new Locale[]{Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("pt-BR"), Locale.UK}) {
                Locale.setDefault(l);
                assertEquals(l.toString(), "97.0", SettingsValues.format1(97));
                assertEquals(l.toString(), "0.02", SettingsValues.formatQ(0.02));
                // and what is written can be read back
                assertEquals(l.toString(), 97.0, SettingsValues.parse(SettingsValues.format1(97)), 0);
            }
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    public void qValuesKeepTheDigitsTheyNeed() {
        assertEquals("0.02", SettingsValues.formatQ(0.02));
        assertEquals("0.145", SettingsValues.formatQ(0.145));
        assertEquals("0.0125", SettingsValues.formatQ(0.0125));
        assertEquals("1.0", SettingsValues.formatQ(1));
    }

    // ---------------------------------------------------------------- rules

    @Test
    public void scalingQminMustBePositiveAndBelowQmax() {
        SettingsValues v = new SettingsValues();
        assertTrue(v.checkQmin("0.01").accepted());
        assertEquals(0.01, v.checkQmin("0,01").value, 0);
        SettingsValues.Checked tooBig = v.checkQmin("0.2");
        assertFalse(tooBig.accepted());
        assertTrue(tooBig.value < v.qmax);
        assertTrue(tooBig.message, tooBig.message.contains("less than"));
        assertFalse("equal to qmax", v.checkQmin("0.14").accepted());
        assertFalse(v.checkQmin("0").accepted());
        assertFalse(v.checkQmin("-0.01").accepted());
        assertFalse(v.checkQmin("x").accepted());
        assertTrue(v.checkQmin("x").value > 0);
    }

    @Test
    public void replacementQminStaysBelowASmallQmax() {
        SettingsValues v = new SettingsValues();
        v.qmax = 0.015;                      // below the default qmin
        SettingsValues.Checked c = v.checkQmin("0.3");
        assertTrue(c.value + " < " + v.qmax, c.value < v.qmax);
    }

    @Test
    public void scalingQmaxMustBeAboveQmin() {
        SettingsValues v = new SettingsValues();
        assertTrue(v.checkQmax("0.3").accepted());
        SettingsValues.Checked c = v.checkQmax("0.01");
        assertFalse(c.accepted());
        assertTrue(c.value > v.qmin);
        assertTrue(c.message, c.message.contains("greater than"));
        assertFalse("equal to qmin", v.checkQmax("0.02").accepted());
        v.qmin = 0.5;
        assertTrue("replacement above a large qmin", v.checkQmax("bad").value > 0.5);
    }

    @Test
    public void pdbResolutionIsPositiveAndAtMostTwo() {
        SettingsValues v = new SettingsValues();
        assertTrue(v.checkQmaxForPDB("0.5").accepted());
        assertTrue(v.checkQmaxForPDB("2").accepted());
        assertFalse(v.checkQmaxForPDB("2.5").accepted());
        assertEquals(0.4, v.checkQmaxForPDB("2.5").value, 0);
        assertFalse(v.checkQmaxForPDB("0").accepted());
        assertFalse(v.checkQmaxForPDB("-1").accepted());
        assertEquals(0.3, v.checkQmaxForPDB("0,3").value, 0);
    }

    @Test
    public void defaultDmaxIsAtLeastTen() {
        SettingsValues v = new SettingsValues();
        assertTrue(v.checkDefaultDmax("150").accepted());
        assertTrue(v.checkDefaultDmax("10").accepted());
        assertFalse(v.checkDefaultDmax("9.9").accepted());
        assertEquals(97, v.checkDefaultDmax("5").value, 0);
        assertEquals(97, v.checkDefaultDmax("").value, 0);
    }

    @Test
    public void oldDmaxSearchEntriesAreIgnoredAndNotWrittenAgain() {
        Properties old = new Properties();
        old.setProperty("dmaxSearchMin", "20");             // written by earlier versions
        old.setProperty("dmaxSearchMax", "300");
        old.setProperty(SettingsValues.K_DMAX, "120");
        SettingsValues v = SettingsValues.load(old, ROUNDS, CUTOFFS, CS);
        assertEquals(120, v.defaultDmax, 0);
        Properties saved = new Properties();                // Scatter.updateProp saves into a new Properties
        v.store(saved);
        assertNull(saved.getProperty("dmaxSearchMin"));
        assertNull(saved.getProperty("dmaxSearchMax"));
        assertEquals(120, Double.parseDouble(saved.getProperty(SettingsValues.K_DMAX)), 0);
    }

    // ---------------------------------------------------------------- settings file

    @Test
    public void everyValueSurvivesSavingAndLoading() {
        SettingsValues v = new SettingsValues();
        v.qmin = 0.011; v.qmax = 0.21; v.qmaxForPDB = 0.75; v.excludeWaters = false; v.defaultDmax = 140.5;
        v.refinementRoundsIndex = 4; v.rejectionCutoffIndex = 0; v.cIndex = 5;
        Properties p = new Properties();
        v.store(p);
        SettingsValues back = SettingsValues.load(p, ROUNDS, CUTOFFS, CS);
        assertEquals(0.011, back.qmin, 0);
        assertEquals(0.21, back.qmax, 0);
        assertEquals(0.75, back.qmaxForPDB, 0);
        assertFalse(back.excludeWaters);
        assertEquals(140.5, back.defaultDmax, 0);
        assertEquals(4, back.refinementRoundsIndex);
        assertEquals(0, back.rejectionCutoffIndex);
        assertEquals(5, back.cIndex);
    }

    @Test
    public void anOldSettingsFileWithoutTheseEntriesGivesTheDefaults() {
        Properties old = new Properties();
        old.setProperty("workingDirectory", "/data");
        old.setProperty("theme", "dark");
        SettingsValues v = SettingsValues.load(old, ROUNDS, CUTOFFS, CS);
        assertEquals(0.02, v.qmin, 0);
        assertEquals(97, v.defaultDmax, 0);
        assertEquals(2, v.refinementRoundsIndex);
        assertEquals(0.02, SettingsValues.load(null, ROUNDS, CUTOFFS, CS).qmin, 0);
    }

    @Test
    public void brokenOrInconsistentEntriesKeepTheirDefaults() {
        Properties p = new Properties();
        p.setProperty(SettingsValues.K_QMIN, "0.3");         // above qmax: both ignored
        p.setProperty(SettingsValues.K_QMAX, "0.1");
        p.setProperty(SettingsValues.K_QMAX_PDB, "5");
        p.setProperty(SettingsValues.K_EXCLUDE_WATERS, "maybe");
        p.setProperty(SettingsValues.K_DMAX, "3");
        p.setProperty(SettingsValues.K_ROUNDS, "9");         // only 5 choices
        p.setProperty(SettingsValues.K_CUTOFF, "1.5");       // not an index
        p.setProperty(SettingsValues.K_C, "-1");
        SettingsValues v = SettingsValues.load(p, ROUNDS, CUTOFFS, CS);
        assertEquals(0.02, v.qmin, 0);
        assertEquals(0.14, v.qmax, 0);
        assertEquals(0.4, v.qmaxForPDB, 0);
        assertTrue(v.excludeWaters);
        assertEquals(97, v.defaultDmax, 0);
        assertEquals(2, v.refinementRoundsIndex);
        assertEquals(3, v.rejectionCutoffIndex);
        assertEquals(0, v.cIndex);
    }

    @Test
    public void aHandEditedFileWithADecimalCommaIsRead() {
        Properties p = new Properties();
        p.setProperty(SettingsValues.K_QMIN, "0,015");
        p.setProperty(SettingsValues.K_QMAX, "0,2");
        SettingsValues v = SettingsValues.load(p, ROUNDS, CUTOFFS, CS);
        assertEquals(0.015, v.qmin, 0);
        assertEquals(0.2, v.qmax, 0);
    }

    // ---------------------------------------------------------------- with the real settings file

    private Path dir;
    private String previousDir;

    @Before
    public void useTemporaryConfigFolder() throws Exception {
        dir = Files.createTempDirectory("settings");
        previousDir = System.getProperty("scatter.configDir");
        System.setProperty("scatter.configDir", dir.toString());
    }

    @After
    public void restoreConfigFolder() throws Exception {
        if (previousDir == null) System.clearProperty("scatter.configDir");
        else System.setProperty("scatter.configDir", previousDir);
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void valuesSurviveTheSettingsFileAlongsideTheOtherEntries() throws Exception {
        Properties p = new Properties();
        p.setProperty("workingDirectory", "/data/run");
        p.setProperty("theme", "light");
        SettingsValues v = new SettingsValues();
        v.qmin = 0.012;
        v.cIndex = 3;
        v.store(p);
        AppConfig.save(p);

        Properties read = AppConfig.load();
        assertEquals("/data/run", read.getProperty("workingDirectory"));
        assertEquals("light", read.getProperty("theme"));
        SettingsValues back = SettingsValues.load(read, ROUNDS, CUTOFFS, CS);
        assertEquals(0.012, back.qmin, 0);
        assertEquals(3, back.cIndex);
    }
}
