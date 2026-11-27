package version4.SEC;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import version4.sasCIF.SampleRecord;
import version4.sasCIF.SasObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.*;

/** The sample record is kept in the *.sec header, survives editing it, and passes to datasets made from the run. */
public class SecSampleRecordTest {

    private Path dir;

    @Before
    public void setUp() throws IOException { dir = Files.createTempDirectory("secrecord"); }

    @After
    public void tearDown() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static SampleRecord record() {
        SampleRecord r = new SampleRecord();
        r.buffer.components.add(new SampleRecord.Component("Tris", 20, "mM"));
        r.buffer.components.get(0).titrant = "HCl";
        r.buffer.components.add(new SampleRecord.Component("NaCl", 150, "mM"));
        r.buffer.pH = 7.4;
        r.buffer.status = SampleRecord.Status.CONFIRMED;
        r.concentration.injected = new SampleRecord.Measurement();
        r.concentration.injected.concentrationMgPerMl = 5.0;
        r.concentration.injectionVolumeUl = 45.0;
        r.concentration.column = new SampleRecord.Column();
        r.concentration.column.name = "Superdex 200 Increase";
        r.concentration.column.format = "3.2/300";
        r.concentration.column.volumeMl = 2.4;
        return r;
    }

    @Test
    public void recordIsWrittenWithTheRunAndReadBack() throws IOException {
        Path file = dir.resolve("run.sec");
        SasObject header = new SasObject();
        header.setSampleRecord(record());
        SecFileWriter.write(file, header, SecFileIOTest.process(SecFileIOTest.syntheticRun()), null);

        SECFile sec = new SECFile(file.toFile());
        SampleRecord r = sec.getSasObject().getSampleRecord();
        assertEquals("Tris", r.buffer.components.get(0).name);
        assertEquals("HCl", r.buffer.components.get(0).titrant);
        assertEquals(7.4, r.buffer.pH, 0);
        assertEquals(5.0, r.concentration.injected.concentrationMgPerMl, 0);
        assertEquals("3.2/300", r.concentration.column.format);
        sec.closeFile();
    }

    @Test
    public void editedRecordIsSavedByUpdateHeader() throws IOException {
        Path file = dir.resolve("edit.sec");
        SecFileWriter.write(file, new SasObject(), SecFileIOTest.process(SecFileIOTest.syntheticRun()), null);
        SECFile sec = new SECFile(file.toFile());
        int frames = sec.getTotalFrames();
        sec.getSasObject().setSampleRecord(record());
        sec.getSasObject().getSampleRecord().concentration.peak = new SampleRecord.Measurement();
        sec.getSasObject().getSampleRecord().concentration.peak.absorbance280.add(0.041);
        sec.updateHeader();                       // grows the header line: the file is rewritten
        sec.closeFile();

        SECFile reopened = new SECFile(file.toFile());
        SampleRecord r = reopened.getSasObject().getSampleRecord();
        assertEquals(2, r.buffer.components.size());
        assertEquals(0.041, r.concentration.peak.absorbance280.get(0), 0);
        assertEquals("the frames are intact", frames, reopened.getTotalFrames());
        reopened.closeFile();
    }

    @Test
    public void datasetMadeFromTheRunCarriesTheRecord() {
        SasObject header = new SasObject();
        header.setSampleRecord(record());
        version4.Dataset d = new version4.Dataset(new org.jfree.data.xy.XYSeries("d"), new org.jfree.data.xy.XYSeries("e"), 0);
        d.setSasObject(header);                    // as SECTool does for merged frames
        assertNull("no SEC format on a dataset", d.getSasObject().getSecFormat());
        assertEquals("Superdex 200 Increase", d.getSasObject().getSampleRecord().concentration.column.name);
        header.getSampleRecord().buffer.pH = 9.0;  // a copy, not shared
        assertEquals(7.4, d.getSasObject().getSampleRecord().buffer.pH, 0);
    }
}
