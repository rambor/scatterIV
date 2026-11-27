package version4.Buffer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import version4.sasCIF.SampleRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * One-line buffer entry: reading the line, the library, ionic strength / Debye length / electron
 * density against hand calculations, remembered buffers and the sample record.
 */
public class BufferTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final BufferLibrary LIB = BufferLibrary.builtIn();

    private static BufferParser.Parsed parse(String s) { return BufferParser.parse(s, LIB); }

    private static BufferCalculator.Result calc(String s) { return BufferCalculator.compute(parse(s), null); }

    // ------------------------------------------------------------------ reading the line

    @Test
    public void readsTheUsualWayOfWritingABuffer() {
        BufferParser.Parsed p = parse("20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 5% glycerol, 1 mM TCEP");
        assertEquals(7.5, p.pH, 0);
        assertEquals(4, p.items.size());
        BufferParser.Item hepes = p.items.get(0);
        assertEquals("HEPES", hepes.chemical.name);
        assertEquals(20, hepes.amount, 0);
        assertEquals("mM", hepes.unit);
        assertEquals("NaOH", hepes.titrantText);
        assertEquals("Na+", hepes.titrant.symbol);
        assertEquals("NaCl", p.items.get(1).chemical.name);
        assertEquals("%(v/v)", p.items.get(2).unit);          // glycerol is a liquid
        assertEquals("TCEP", p.items.get(3).chemical.name);
    }

    @Test
    public void separatorsAreOptionalAndSpacingFree() {
        BufferParser.Parsed p = parse("20mM HEPES 150mM NaCl 5% glycerol pH7.5");
        assertEquals(3, p.items.size());
        assertEquals(150, p.items.get(1).amount, 0);
        assertEquals(7.5, p.pH, 0);
    }

    @Test
    public void titrantsAsSuffixPrefixAndNames() {
        assertEquals("Cl-", parse("50 mM Tris-HCl").items.get(0).titrant.symbol);
        assertEquals("K+", parse("20 mM HEPES/KOH").items.get(0).titrant.symbol);
        assertEquals("Na+", parse("10 mM Na-HEPES").items.get(0).titrant.symbol);
        assertEquals("phosphate", parse("50 mM potassium phosphate").items.get(0).chemical.name);
        assertEquals("NaCl", parse("100 mM sodium chloride").items.get(0).chemical.name);   // not "Na" + "chloride"
    }

    @Test
    public void unitsAndPercentages() {
        BufferParser.Parsed p = parse("10 % (v/v) glycerol, 20% sucrose, 5% glycerol (w/v), 2 mg/mL DDM, 500 uM ZnCl2, 0.5 M urea, 10 µM EDTA");
        assertEquals("%(v/v)", p.items.get(0).unit);
        assertEquals("%(w/v)", p.items.get(1).unit);           // sucrose is a solid
        assertEquals("%(w/v)", p.items.get(2).unit);           // written after the name
        assertEquals("mg/mL", p.items.get(3).unit);
        assertEquals("uM", p.items.get(4).unit);
        assertEquals("M", p.items.get(5).unit);
        assertEquals("uM", p.items.get(6).unit);
        // conversions to mol/L
        assertEquals(0.10*1261/92.09, p.items.get(0).molar(), 1e-9);      // 10% v/v glycerol = 1.369 M
        assertEquals(200/342.30, p.items.get(1).molar(), 1e-9);           // 20% w/v sucrose = 0.584 M
        assertEquals(2.0/510.62, p.items.get(3).molar(), 1e-12);
        assertEquals(5e-4, p.items.get(4).molar(), 1e-15);
    }

    @Test
    public void temperatureOnlyWhenItIsClearlyATemperature() {
        assertEquals(10.0, parse("20 mM Tris pH 8 at 10 °C").temperatureC, 1e-9);
        assertEquals(10.0, parse("20 mM Tris pH 8, 283.15 K").temperatureC, 1e-9);
        assertEquals(4.0, parse("20 mM Tris @ 4C").temperatureC, 1e-9);
        assertNull(parse("20 mM Tris, 10 mM K-phosphate").temperatureC);
    }

    @Test
    public void namedBuffersExpandAndScale() {
        BufferParser.Parsed pbs = parse("PBS");
        assertEquals(7.4, pbs.pH, 0);
        assertEquals(3, pbs.items.size());
        assertEquals(137, pbs.items.get(0).amount, 1e-9);
        BufferParser.Parsed half = parse("0.5x PBS + 1 mM EDTA");
        assertEquals(68.5, half.items.get(0).amount, 1e-9);
        assertEquals("EDTA", half.items.get(3).chemical.name);
        assertEquals("own pH wins", 8.0, parse("TBS pH 8.0").pH, 0);
    }

    @Test
    public void unknownNamesAreKeptAndReported() {
        BufferParser.Parsed p = parse("20 mM foo-buffer, 5% bar");
        assertEquals(2, p.items.size());
        assertNull(p.items.get(0).chemical);
        assertEquals("foo", p.items.get(0).name);
        assertTrue(String.join(" ", p.warnings).contains("not in the buffer library"));
        assertTrue(String.join(" ", p.warnings).contains("v/v or w/v"));
    }

    @Test
    public void emptyLineIsEmpty() {
        assertTrue(parse("").isEmpty());
        assertTrue(parse(null).isEmpty());
        assertTrue(Double.isInfinite(calc("").debyeLengthA));
    }

    @Test
    public void detergentsAreRecognizedWithTheirUsualUnits() {
        BufferParser.Parsed p = parse("20 mM Tris-HCl pH 8, 150 mM NaCl, 0.1% Triton X-100, 0.05% Tween 20, 0.03% DDM, 2 mM LMNG");
        assertEquals(6, p.items.size());
        assertEquals("Triton X-100", p.items.get(2).chemical.name);
        assertEquals("%(v/v)", p.items.get(2).unit);                    // liquid
        assertEquals("Tween 20", p.items.get(3).chemical.name);
        assertEquals(0.05, p.items.get(3).amount, 0);                   // not "20" of something
        assertEquals("%(v/v)", p.items.get(3).unit);
        assertEquals("DDM", p.items.get(4).chemical.name);
        assertEquals("%(w/v)", p.items.get(4).unit);                    // solid
        assertEquals(0.1/100*1000*1.065/646.85, p.items.get(2).molar(), 1e-9);   // 1.65 mM Triton
        for (String alias : new String[]{"TX-100", "triton x100", "Tween-20", "polysorbate 80", "NP40", "beta-DDM", "Fos-choline-12", "octyl glucoside", "SDS"}) {
            assertNotNull(alias, LIB.find(alias));
        }
    }

    @Test
    public void detergentAboveItsCmcWarnsAboutMicelles() {
        String w = String.join(" ", calc("20 mM HEPES pH 7.5, 0.1% Triton X-100").warnings);
        assertTrue(w, w.contains("Triton X-100 is 7 x its CMC"));
        assertTrue(w.contains("micelles scatter"));
        String below = String.join(" ", calc("20 mM HEPES pH 7.5, 0.1 mM CHAPS").warnings);
        assertTrue(below.contains("below its CMC"));
    }

    @Test
    public void sdsCountsTowardIonicStrength() {
        assertEquals(0.010, calc("10 mM SDS").ionicStrength, 1e-12);
        assertTrue(String.join(" ", parse("10 mM SDS").warnings).contains("denatures"));
    }

    // ------------------------------------------------------------------ calculations vs hand

    @Test
    public void saltOnlyIonicStrengthAndDebyeLength() {
        BufferCalculator.Result r = calc("150 mM NaCl");
        assertEquals(0.150, r.ionicStrength, 1e-12);
        assertEquals(3.04/Math.sqrt(0.150), r.debyeLengthA, 0.03);                // 7.85 A at 25 C
        assertEquals(0.300, calc("100 mM MgCl2").ionicStrength, 1e-12);           // 1/2 (0.1*4 + 0.2*1)
        assertEquals(0.300, calc("100 mM Na2SO4").ionicStrength, 1e-12);
        assertEquals(3.04/Math.sqrt(0.1), calc("100 mM NaCl").debyeLengthA, 0.03);
    }

    @Test
    public void hepesAtItsPkaIsHalfChargedWithSodium() {
        // 20 mM HEPES-NaOH pH 7.5 + 150 mM NaCl, by hand with the Davies correction at I = 0.1616 M:
        // f = sqrt(I)/(1+sqrt(I)) - 0.3 I = 0.2377, pKa' = 7.48 - 0.509 f = 7.359, alpha(-) = 0.581
        // I = 0.150 + 1/2 (0.02*0.581 + 0.02*0.581) = 0.1616
        BufferCalculator.Result r = calc("20 mM HEPES-NaOH pH 7.5, 150 mM NaCl");
        assertEquals(0.1616, r.ionicStrength, 3e-4);
        assertEquals(7.359, r.buffers.get(0).pKaApparent, 0.005);
        assertEquals(-0.581, r.buffers.get(0).meanCharge, 0.005);
        assertEquals("Na+", r.counterIons.get(0).ion);
        assertEquals(0.02*0.581, r.counterIons.get(0).molar, 2e-4);
        assertEquals(3.04/Math.sqrt(0.1616), r.debyeLengthA, 0.04);
    }

    @Test
    public void trisHclCarriesChloride() {
        BufferCalculator.Result r = calc("50 mM Tris-HCl pH 7.5");
        assertEquals("Cl-", r.counterIons.get(0).ion);
        assertTrue(r.buffers.get(0).meanCharge > 0.8 && r.buffers.get(0).meanCharge < 0.83);
        // I = 1/2 (c z^2 Tris+ + c Cl-) = c * zbar
        assertEquals(0.05*r.buffers.get(0).meanCharge, r.ionicStrength, 1e-6);
    }

    @Test
    public void temperatureShiftsTrisPka() {
        double at25 = calc("50 mM Tris-HCl pH 8.0").buffers.get(0).pKaApparent;
        double at4 = calc("50 mM Tris-HCl pH 8.0 at 4 °C").buffers.get(0).pKaApparent;
        assertEquals(0.028*21, at4 - at25, 0.01);                                  // -0.028 per degree
    }

    @Test
    public void phosphatePka2ShiftsWithIonicStrength() {
        // thermodynamic pKa2 7.20; the familiar ~6.8-6.9 at physiological ionic strength
        BufferCalculator.Result r = calc("100 mM sodium phosphate pH 7.0");
        assertEquals(6.81, r.buffers.get(0).pKaApparent, 0.03);
        assertTrue(r.ionicStrength > 0.2 && r.ionicStrength < 0.24);
    }

    @Test
    public void pbsComesOutNearItsKnownIonicStrength() {
        BufferCalculator.Result r = calc("PBS");
        assertEquals(0.17, r.ionicStrength, 0.01);                                // ~0.16-0.17 M
        assertEquals(7.4, r.debyeLengthA, 0.2);
    }

    @Test
    public void noPhIsAssumedAndSaid() {
        BufferCalculator.Result r = calc("20 mM HEPES, 150 mM NaCl");
        assertTrue(r.pHAssumed);
        assertEquals(7.48, r.pH, 1e-9);
        assertTrue(String.join(" ", r.warnings).contains("no pH given"));
    }

    @Test
    public void bufferOutsideItsRangeIsFlaggedButNotAChelator() {
        assertTrue(String.join(" ", calc("20 mM HEPES pH 9.5").warnings).contains("buffers poorly"));
        assertFalse(String.join(" ", calc("20 mM HEPES pH 7.4, 1 mM EDTA").warnings).contains("EDTA buffers poorly"));
    }

    @Test
    public void waterElectronDensityAndAdditives() {
        BufferCalculator.Result water = calc("");
        assertEquals(0.3334, water.electronDensity, 0.0002);                       // 0.99705 g/mL, 10 e/molecule
        assertEquals(1.0, water.contrastRelativeToWater, 1e-9);
        // 20% (w/v) sucrose is ~0.357 e/A^3 and cuts the protein contrast by ~40%
        BufferCalculator.Result sucrose = calc("20% sucrose");
        assertEquals(0.357, sucrose.electronDensity, 0.004);
        assertEquals(0.60, sucrose.contrastRelativeToWater, 0.05);
        // 150 mM NaCl hardly matters
        assertEquals(0.335, calc("150 mM NaCl").electronDensity, 0.002);
        assertTrue(calc("10% glycerol").electronDensity > calc("5% glycerol").electronDensity);
    }

    @Test
    public void waterPropertiesAt25C() {
        assertEquals(78.3, BufferCalculator.waterPermittivity(25), 0.1);
        assertEquals(0.99705, BufferCalculator.waterDensity(25), 1e-5);
        assertEquals(0.99997, BufferCalculator.waterDensity(4), 1e-4);
    }

    @Test
    public void formulaElectrons() {
        assertEquals(10, Formula.electrons("H2O"));
        assertEquals(128, Formula.electrons("C8H18N2O4S"));                       // HEPES
        assertEquals(50, Formula.electrons("C3H8O3"));                            // glycerol
        assertEquals(70, Formula.electrons("N2H8SO4"));
        assertEquals(24, Formula.electrons("(C2H4O)n"));
        try { Formula.electrons("Xx2"); fail(); } catch (IllegalArgumentException expected) { }
    }

    // ------------------------------------------------------------------ library file, remembered buffers

    @Test
    public void beamlineLibraryFileAddsChemicalsAndPresets() throws Exception {
        Path f = tmp.newFile("buffer_library.json").toPath();
        Files.write(f, ("{ \"presets\": [ {\"name\": \"B21 SEC\", \"text\": \"20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 1 mM TCEP\"} ],"
                + " \"chemicals\": [ {\"name\": \"MOPSO\", \"kind\": \"BUFFER\", \"formula\": \"C7H15NO5S\", \"molar_mass\": 225.26,"
                + " \"pKa\": [6.87], \"dpKa_dT\": [-0.015], \"charge_protonated\": 0, \"aliases\": [\"Mopso\"]},"
                + " {\"name\": \"broken\", \"formula\": \"Qq\"} ] }").getBytes(StandardCharsets.UTF_8));
        BufferLibrary lib = BufferLibrary.builtIn();
        lib.addUserFile(f);
        assertNotNull(lib.find("mopso"));
        assertEquals(6.87, lib.find("MOPSO").pKa[0], 0);
        assertTrue(lib.presets().containsKey("B21 SEC"));
        assertEquals(3, BufferParser.parse("B21 SEC", lib).items.size());
        assertEquals("the bad entry is reported, not loaded", 1, lib.loadProblems().size());
        assertNull(lib.find("broken"));
    }

    @Test
    public void recentBuffersAreRememberedMostRecentFirstWithoutDuplicates() throws Exception {
        Path f = tmp.getRoot().toPath().resolve("sub").resolve("recent_buffers.json");
        RecentBuffers r = new RecentBuffers(f);
        r.add("20 mM HEPES pH 7.5, 150 mM NaCl");
        r.add("PBS");
        r.add("20 mM HEPES  pH 7.5,  150 mM NaCl");               // same buffer, other spacing
        List<String> l = r.list();
        assertEquals(2, l.size());
        assertEquals("20 mM HEPES pH 7.5, 150 mM NaCl", l.get(0));
        assertEquals("PBS", l.get(1));
        assertEquals("kept across sessions", l, new RecentBuffers(f).list());
        for (int i = 0; i < 30; i++) r.add("buffer " + i);
        assertEquals(RecentBuffers.MAX, r.list().size());
        assertEquals("buffer 29", r.list().get(0));
    }

    // ------------------------------------------------------------------ sample record

    @Test
    public void recordGetsComponentsComputedValuesAndStatus() {
        BufferParser.Parsed p = parse("20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 5% glycerol");
        SampleRecord.Buffer b = BufferRecords.toRecord(p, 10.0, SampleRecord.Status.CONFIRMED);
        assertEquals(p.text, b.text);
        assertEquals(3, b.components.size());
        assertEquals("HEPES", b.components.get(0).name);
        assertEquals("NaOH", b.components.get(0).titrant);
        assertEquals("%(v/v)", b.components.get(2).unit);
        assertEquals(7.5, b.pH, 0);
        assertEquals(283.15, b.temperatureK, 1e-9);
        assertEquals(SampleRecord.Status.CONFIRMED, b.status);
        assertTrue(b.ionicStrengthM > 0.15 && b.ionicStrengthM < 0.17);
        assertNotNull(b.debyeLengthA);
        assertNotNull(b.electronDensity);
        String remarks = String.join("\n", recordWith(b).remarkLines());
        assertTrue(remarks.contains("IONIC STRENGTH (M) : 0.159"));        // at 10 C HEPES is a little less charged
        assertTrue(remarks.contains("DEBYE LENGTH (ANGSTROMS) : 7.6"));
        assertTrue(remarks.contains("ELECTRON DENSITY (E/A^3) : 0.3"));
        assertEquals(SampleRecord.Status.NONE, BufferRecords.toRecord(parse(""), null, SampleRecord.Status.CONFIRMED).status);
    }

    @Test
    public void storedComponentsBecomeALineTheParserReadsBack() {
        SampleRecord.Buffer b = BufferRecords.toRecord(parse("50 mM Tris-HCl, 150 mM KCl, pH 8"), null, SampleRecord.Status.CONFIRMED);
        b.text = null;                                         // e.g. written by another program
        String line = BufferRecords.lineFor(b);
        BufferParser.Parsed again = parse(line);
        assertEquals(2, again.items.size());
        assertEquals("Cl-", again.items.get(0).titrant.symbol);
        assertEquals(8.0, again.pH, 0);
    }

    private static SampleRecord recordWith(SampleRecord.Buffer b) {
        SampleRecord r = new SampleRecord();
        r.buffer = b;
        return r;
    }
}
