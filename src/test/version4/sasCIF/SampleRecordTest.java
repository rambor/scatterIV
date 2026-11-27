package version4.sasCIF;

import FileManager.FileObject;
import FileManager.LoadedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfree.data.xy.XYSeries;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import version4.Dataset;
import version4.SEC.SecFileWriter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The sample record (buffer, temperature, concentration and their sources): JSON round trips through
 * the SasObject, compatibility with files written before it existed and after it changes, the REMARK
 * 265 block, and the trip through a *.dat file written and read back by Scatter.
 */
public class SampleRecordTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    static SampleRecord example() {
        SampleRecord r = new SampleRecord();
        SampleRecord.Component hepes = new SampleRecord.Component("HEPES", 20, "mM");
        hepes.titrant = "NaOH";
        r.buffer.components.add(hepes);
        r.buffer.components.add(new SampleRecord.Component("NaCl", 150, "mM"));
        r.buffer.components.add(new SampleRecord.Component("glycerol", 5, "%(v/v)"));
        r.buffer.text = "20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 5% glycerol";
        r.buffer.pH = 7.5;
        r.buffer.temperatureK = 283.15;
        r.buffer.status = SampleRecord.Status.CONFIRMED;

        SampleRecord.Concentration c = r.concentration;
        c.source = SampleRecord.ConcentrationSource.PEAK_ABSORBANCE;
        c.valueMgPerMl = 0.42;
        c.uncertaintyMgPerMl = 0.06;
        c.method = "apex drop, frames 410-430 averaged";
        c.extinctionCoefficient = 0.667;
        c.injected = new SampleRecord.Measurement();
        c.injected.concentrationMgPerMl = 4.1;
        c.peak = new SampleRecord.Measurement();
        c.peak.absorbance280.addAll(Arrays.asList(0.030, 0.034, 0.029));
        c.peak.dropVolumeUl = 2.0;
        c.injectionVolumeUl = 50.0;
        c.column = new SampleRecord.Column();
        c.column.name = "Shodex KW-403-4F";
        c.column.format = "4.6 x 300 mm";
        c.column.volumeMl = 5.0;
        return r;
    }

    // ------------------------------------------------------------------ JSON

    @Test
    public void recordSurvivesTheSasObjectJson() throws Exception {
        SasObject o = new SasObject();
        o.setSampleRecord(example());
        SasObject back = new SasObject(SecFileWriter.sasObjectJson(o));

        SampleRecord r = back.getSampleRecord();
        assertEquals(3, r.buffer.components.size());
        assertEquals("HEPES", r.buffer.components.get(0).name);
        assertEquals("NaOH", r.buffer.components.get(0).titrant);
        assertEquals(20, r.buffer.components.get(0).concentration, 0);
        assertEquals("%(v/v)", r.buffer.components.get(2).unit);
        assertEquals(7.5, r.buffer.pH, 0);
        assertEquals(283.15, r.buffer.temperatureK, 0);
        assertEquals(SampleRecord.Status.CONFIRMED, r.buffer.status);
        assertEquals(SampleRecord.ConcentrationSource.PEAK_ABSORBANCE, r.concentration.source);
        assertEquals(0.42, r.concentration.valueMgPerMl, 0);
        assertEquals(0.06, r.concentration.uncertaintyMgPerMl, 0);
        assertEquals(4.1, r.concentration.injected.concentrationMgPerMl, 0);
        assertEquals(Arrays.asList(0.030, 0.034, 0.029), r.concentration.peak.absorbance280);
        assertEquals("Shodex KW-403-4F", r.concentration.column.name);
        assertEquals(5.0, r.concentration.column.volumeMl, 0);
        assertTrue(back.isReadFromFile());
        assertFalse(o.isReadFromFile());
    }

    @Test
    public void recordIsItsOwnTopLevelNodeSoOlderVersionsIgnoreIt() throws Exception {
        SasObject o = new SasObject();
        o.setSampleRecord(example());
        JsonNode root = new ObjectMapper().readTree(SecFileWriter.sasObjectJson(o));
        assertFalse(root.path("sample_record").isMissingNode());
        // the nodes older versions read are unchanged: nothing new inside sas_buffer or sas_sample
        assertTrue(root.path("sas_buffer").path("components").size() == 0);
        assertTrue(root.path("sas_buffer").path("concentration").isMissingNode());
        assertTrue(root.path("sas_sample").path("buffer").isMissingNode());
        assertEquals("mM", root.path("sample_record").path("buffer").path("components").get(0).path("unit").asText());
        assertEquals("PEAK_ABSORBANCE", root.path("sample_record").path("concentration").path("source").asText());
    }

    @Test
    public void fieldsAddedByLaterVersionsAreIgnored() {
        String json = "{\"sas_beam\":{\"radiation_type\":\"xrays\"},"
                + "\"sample_record\":{\"record_version\":7,\"something_new\":{\"a\":1},"
                + "\"buffer\":{\"text\":\"PBS\",\"status\":\"CONFIRMED\",\"viscosity_mPa_s\":0.89,"
                + "\"components\":[{\"name\":\"NaCl\",\"concentration\":137,\"unit\":\"mM\",\"activity\":0.75}]},"
                + "\"concentration\":{\"source\":\"MASS_BALANCE\",\"value_mg_per_ml\":0.8,\"per_frame\":[1,2,3]}}}";
        SampleRecord r = new SasObject(json).getSampleRecord();
        assertEquals("PBS", r.buffer.text);
        assertEquals(SampleRecord.Status.CONFIRMED, r.buffer.status);
        assertEquals(137, r.buffer.components.get(0).concentration, 0);
        assertEquals(SampleRecord.ConcentrationSource.MASS_BALANCE, r.concentration.source);
        assertEquals(0.8, r.concentration.valueMgPerMl, 0);
    }

    @Test
    public void unreadableRecordGivesAnEmptyOneNotAnError() {
        String json = "{\"sample_record\":{\"buffer\":{\"status\":\"PROBABLY\"}}}";   // unknown enum value
        SasObject o = new SasObject(json);
        assertNotNull(o.getSampleRecord());
        assertEquals(SampleRecord.Status.NONE, o.getSampleRecord().buffer.status);
        assertNotNull(o.getSasBuffer());
    }

    @Test
    public void copyIsDeep() {
        SasObject o = new SasObject();
        o.setSampleRecord(example());
        SasObject copy = new SasObject(o);
        o.getSampleRecord().buffer.components.get(0).concentration = 99.0;
        o.getSampleRecord().concentration.valueMgPerMl = 9.0;
        assertEquals(20, copy.getSampleRecord().buffer.components.get(0).concentration, 0);
        assertEquals(0.42, copy.getSampleRecord().concentration.valueMgPerMl, 0);
    }

    // ------------------------------------------------------------------ older files

    @Test
    public void olderFileWithoutRecordReportsItsFreeTextBufferAsUnconfirmed() {
        String json = "{\"sas_buffer\":{\"name\":\"20 mM Tris-HCl\",\"salt\":\"150 mM NaCl\",\"other\":\"1 mM TCEP\","
                + "\"pH\":8.0,\"comment\":\"no buffer details specified\"},"
                + "\"sas_sample\":{\"cell_temperature\":283.0}}";
        SasObject o = new SasObject(json);
        assertTrue("stored record is empty", o.getSampleRecord().buffer.isEmpty());
        SampleRecord r = o.getEffectiveSampleRecord();
        assertEquals("20 mM Tris-HCl, 150 mM NaCl, 1 mM TCEP", r.buffer.text);
        assertEquals(8.0, r.buffer.pH, 0);
        assertEquals(283.0, r.buffer.temperatureK, 0);
        assertEquals(SampleRecord.Status.UNCONFIRMED, r.buffer.status);
    }

    @Test
    public void defaultFreeTextFieldsAreNotABuffer() {
        SampleRecord r = new SasObject().getEffectiveSampleRecord();
        assertTrue(r.buffer.isEmpty());
        assertEquals(SampleRecord.Status.NONE, r.buffer.status);
        assertEquals(298.0, r.buffer.temperatureK, 0);       // SasSample's default
    }

    @Test
    public void missingSasBufferNoLongerLeavesItNull() {
        SasObject o = new SasObject("{\"sas_beam\":{\"radiation_type\":\"neutrons\"}}");
        assertNotNull(o.getSasBuffer());
        assertEquals("neutrons", o.getSasBeam().getRadiation_type());   // was reset to a new SasBeam
    }

    @Test
    public void storedRecordWinsOverFreeTextAndKeepsItsTemperature() {
        SasObject o = new SasObject();
        o.getSasBuffer().setName("old text");
        o.setSampleRecord(example());
        SampleRecord r = o.getEffectiveSampleRecord();
        assertEquals("HEPES", r.buffer.components.get(0).name);
        assertEquals(283.15, r.buffer.temperatureK, 0);
    }

    @Test
    public void temperatureBelow150IsTakenAsCelsius() {
        assertEquals(298.15, SampleRecord.kelvin(25), 1e-9);
        assertEquals(277.0, SampleRecord.kelvin(277), 0);
        assertNull(SampleRecord.kelvin(0));
        assertNull(SampleRecord.kelvin(Double.NaN));
    }

    // ------------------------------------------------------------------ replicate readings

    @Test
    public void replicateReadingsGiveMeanAndStandardError() {
        SampleRecord.Measurement m = new SampleRecord.Measurement();
        assertNull(m.meanAbsorbance());
        m.absorbance280.add(0.030);
        assertEquals(0.030, m.meanAbsorbance(), 1e-12);
        assertNull("one reading has no spread", m.absorbanceStandardError());
        m.absorbance280.addAll(Arrays.asList(0.034, 0.029));
        assertEquals(0.031, m.meanAbsorbance(), 1e-12);
        // sd of (0.030, 0.034, 0.029) = 0.0026458; / sqrt(3)
        assertEquals(0.0026458/Math.sqrt(3), m.absorbanceStandardError(), 1e-6);
    }

    // ------------------------------------------------------------------ REMARK 265

    @Test
    public void remarkBlockListsComponentsAndConcentration() {
        List<String> lines = example().remarkLines();
        String all = String.join("\n", lines);
        assertTrue(all.contains("TEMPERATURE (KELVIN) : 283.15"));
        assertTrue(all.contains("PH : 7.50"));
        assertTrue(all.contains("SAMPLE BUFFER : 20 mM HEPES (titrated with NaOH)"));
        assertTrue(all.contains("SAMPLE BUFFER : 150 mM NaCl"));
        assertTrue(all.contains("SAMPLE BUFFER : 5 %(v/v) glycerol"));
        assertTrue(all.contains("BUFFER STATUS : CONFIRMED"));
        assertTrue(all.contains("CONCENTRATION (MG/ML) : 0.4200 +/- 0.060"));
        assertTrue(all.contains("CONCENTRATION SOURCE : PEAK ABSORBANCE"));
        assertTrue(all.contains("INJECTED (MG/ML) : 4.100"));
        assertTrue(all.contains("INJECTION VOLUME (UL) : 50"));
        assertTrue(all.contains("SEC COLUMN : Shodex KW-403-4F 4.6 x 300 mm, 5 mL"));
        for (String l : lines) assertTrue(l, l.startsWith("REMARK 265"));
    }

    @Test
    public void remarkBlockWritesNoPlaceholders() {
        String all = String.join("\n", new SampleRecord().remarkLines());
        assertFalse(all.contains("XXX"));
        assertFalse(all.contains("X.X"));
        assertTrue(all.contains("SAMPLE BUFFER : NOT PROVIDED"));
        assertTrue(all.contains("TEMPERATURE (KELVIN) : NOT PROVIDED"));
        assertTrue(all.contains("CONCENTRATION (MG/ML) : NOT PROVIDED"));
        assertTrue(all.contains("BUFFER STATUS : NONE"));
    }

    // ------------------------------------------------------------------ theoretical mass, calibration

    @Test
    public void theoreticalMassIsReadInDaOrKda() {
        assertEquals(66432, SampleRecord.parseMassDa("66432"), 0);
        assertEquals(66432, SampleRecord.parseMassDa("66,432"), 0);
        assertEquals(66432, SampleRecord.parseMassDa("66432 Da"), 0);
        assertEquals(66400, SampleRecord.parseMassDa("66.4 kDa"), 1e-9);
        assertEquals(66400, SampleRecord.parseMassDa("66.4kda"), 1e-9);
        assertEquals(1.2e6, SampleRecord.parseMassDa("1.2 MDa"), 1e-6);
        assertNull(SampleRecord.parseMassDa(""));
        assertNull(SampleRecord.parseMassDa("big"));
        assertNull(SampleRecord.parseMassDa("-5"));
        assertNull(SampleRecord.parseMassDa(null));
    }

    @Test
    public void theoreticalMassIsStoredAndWritten() throws Exception {
        SasObject o = new SasObject();
        assertTrue(String.join("\n", o.getEffectiveSampleRecord().remarkLines()).contains("THEORETICAL MASS (DA) : NOT PROVIDED"));
        o.getSampleRecord().theoreticalMassDa = 132864.0;
        SasObject back = new SasObject(SecFileWriter.sasObjectJson(o));
        assertEquals(132864.0, back.getSampleRecord().theoreticalMassDa, 0);
        assertTrue(String.join("\n", back.getEffectiveSampleRecord().remarkLines()).contains("THEORETICAL MASS (DA) : 132864"));
    }

    @Test
    public void calibrationDefaultsToNone() {
        assertEquals("none", new SasSample().getCalibration_details());
    }

    // ------------------------------------------------------------------ through a *.dat file

    private static Dataset dataset() {
        XYSeries d = new XYSeries("d", false, false), e = new XYSeries("e", false, false);
        for (int i = 0; i < 200; i++) {
            double q = 0.01 + i*0.0015, x = q*25;
            double iq = 100*Math.exp(-x*x/3) + 0.01;
            d.add(q, iq);
            e.add(q, 0.01*iq);
        }
        return new Dataset(d, e, "sample", 0, false);
    }

    @Test
    public void datFileCarriesTheRecordInItsHeaderAndJson() throws Exception {
        Dataset ds = dataset();
        ds.getSasObject().setSampleRecord(example());
        File dir = tmp.newFolder();
        new FileObject(dir, "test").writeSAXSFile("sample_out", ds);
        File out = new File(dir, "sample_out.dat");
        String text = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("SAMPLE BUFFER : 150 mM NaCl"));
        assertTrue(text.contains("CONCENTRATION SOURCE : PEAK ABSORBANCE"));
        assertFalse("no placeholder buffer", text.contains("XXX mM HEPES"));
        assertFalse("temperature is no longer fixed", text.contains("TEMPERATURE (KELVIN) : 298\n"));

        LoadedFile loaded = new LoadedFile(out, 0, false);
        assertTrue(loaded.hasJson());
        Dataset back = new Dataset(loaded.allData, loaded.allDataError, loaded.filebase, 1, false);
        back.setSasObject(loaded.getJSONString());
        SampleRecord r = back.getSasObject().getSampleRecord();
        assertEquals(3, r.buffer.components.size());
        assertEquals(SampleRecord.Status.CONFIRMED, r.buffer.status);
        assertEquals(0.42, r.concentration.valueMgPerMl, 0);
        assertTrue(back.getSasObject().isReadFromFile());
    }

    @Test
    public void datFileFromFreeTextBufferWritesThatText() throws Exception {
        Dataset ds = dataset();
        ds.getSasObject().getSasBuffer().setName("50 mM Tris-HCl");
        ds.getSasObject().getSasBuffer().setSalt("100 mM KCl");
        ds.getSasObject().getSasBuffer().setpH(8.0f);
        File dir = tmp.newFolder();
        new FileObject(dir, "test").writeSAXSFile("legacy_out", ds);
        String text = new String(Files.readAllBytes(new File(dir, "legacy_out.dat").toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("SAMPLE BUFFER : 50 mM Tris-HCl, 100 mM KCl"));
        assertTrue(text.contains("PH : 8.00"));
        assertTrue(text.contains("BUFFER STATUS : UNCONFIRMED"));
    }

    @Test
    public void datFileWithNothingRecordedSaysSo() throws Exception {
        Dataset ds = dataset();
        File dir = tmp.newFolder();
        new FileObject(dir, "test").writeSAXSFile("empty_out", ds);
        String text = new String(Files.readAllBytes(new File(dir, "empty_out.dat").toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("SAMPLE BUFFER : NOT PROVIDED"));
        assertTrue(text.contains("BUFFER STATUS : NONE"));
        assertTrue(text.contains("TEMPERATURE (KELVIN) : 298.00"));
    }
}
