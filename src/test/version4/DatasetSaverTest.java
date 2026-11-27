package version4;

import FileManager.FileObject;
import FileManager.LoadedFile;
import org.jfree.data.xy.XYSeries;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** Save File: offers the dataset's own file, writes the whole curve, keeps a non-Scatter original once. */
public class DatasetSaverTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Dataset dataset(String name) {
        XYSeries d = new XYSeries("d", false, false), e = new XYSeries("e", false, false);
        for (int i = 0; i < 120; i++) {
            double q = 0.01 + i*0.002, x = q*20;
            double iq = 100*Math.exp(-x*x/3) + 0.01;
            d.add(q, iq);
            e.add(q, 0.01*iq);
        }
        return new Dataset(d, e, name, 0, false);
    }

    /** a file as a beamline pipeline might write it: three columns, its own comments */
    private File pipelineFile(File dir, String name) throws Exception {
        StringBuilder s = new StringBuilder("# written by the beamline pipeline\n# sample: lysozyme\n");
        for (int i = 0; i < 120; i++) {
            double q = 0.01 + i*0.002, x = q*20, iq = 100*Math.exp(-x*x/3) + 0.01;
            s.append(String.format(java.util.Locale.ROOT, "%.6f %.6e %.6e%n", q, iq, 0.01*iq));
        }
        File f = new File(dir, name);
        Files.write(f.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static int dataLines(File f) throws Exception {
        int n = 0;
        for (String l : Files.readAllLines(f.toPath(), StandardCharsets.ISO_8859_1)) {
            if (l.matches("^\\s*\\d.*")) n++;
        }
        return n;
    }

    @Test
    public void namesAndSuggestions() throws Exception {
        assertEquals("sample", DatasetSaver.baseName("sample.dat"));
        assertEquals("sample", DatasetSaver.baseName("sample"));
        assertEquals("my_sample_1", DatasetSaver.baseName("my sample 1.txt"));
        Dataset d = dataset("lyso");
        File dir = tmp.newFolder();
        assertEquals(new File(dir, "lyso.dat"), DatasetSaver.suggestedFile(d, dir));
        File other = tmp.newFolder();
        d.setSourceFile(new File(other, "lyso_run3.dat"));
        assertEquals("its own file comes first", new File(other, "lyso_run3.dat"), DatasetSaver.suggestedFile(d, dir));
    }

    @Test
    public void overwritingAPipelineFileKeepsTheOriginalOnce() throws Exception {
        File dir = tmp.newFolder();
        File own = pipelineFile(dir, "lyso.dat");
        assertFalse(DatasetSaver.writtenByScatter(own));
        Dataset d = dataset("lyso");
        d.setSourceFile(own);
        d.markSaved();
        d.getSasObject().getSampleRecord().theoreticalMassDa = 14313.0;
        d.setStart(10);                                  // a selected range must not trim the saved file
        d.setEnd(60);

        DatasetSaver.Outcome first = DatasetSaver.save(d, own, "test");
        assertTrue(first.replaced);
        assertEquals(new File(dir, "lyso.orig.dat"), first.backup);
        assertTrue(new String(Files.readAllBytes(first.backup.toPath()), StandardCharsets.UTF_8).contains("beamline pipeline"));
        assertTrue(DatasetSaver.writtenByScatter(own));
        assertTrue(new String(Files.readAllBytes(own.toPath()), StandardCharsets.UTF_8).contains("THEORETICAL MASS (DA) : 14313"));
        assertEquals("whole curve", 120, dataLines(own));
        assertFalse(d.hasUnsavedChangesNow());
        assertEquals(own, d.getSourceFile());
        assertTrue(first.describe().contains("original kept as lyso.orig.dat"));

        d.getSasObject().getSampleRecord().theoreticalMassDa = 14314.0;
        DatasetSaver.Outcome second = DatasetSaver.save(d, own, "test");
        assertTrue(second.replaced);
        assertNull("Scatter's own file is just replaced", second.backup);
        assertTrue("the first backup is untouched",
                new String(Files.readAllBytes(new File(dir, "lyso.orig.dat").toPath()), StandardCharsets.UTF_8).contains("beamline pipeline"));
    }

    @Test
    public void savingUnderANewNameRenamesTheDataset() throws Exception {
        File dir = tmp.newFolder();
        Dataset d = dataset("lyso");
        DatasetSaver.Outcome o = DatasetSaver.save(d, new File(dir, "lyso_checked.txt"), "test");
        assertEquals(new File(dir, "lyso_checked.dat"), o.written);
        assertFalse(o.replaced);
        assertNull(o.backup);
        assertEquals("lyso_checked", d.getFileName());
        assertEquals(o.written, d.getSourceFile());
        assertFalse(d.hasUnsavedChangesNow());
    }

    @Test
    public void savedFileLoadsBackWithItsDetails() throws Exception {
        File dir = tmp.newFolder();
        Dataset d = dataset("lyso");
        d.getSasObject().getSampleRecord().theoreticalMassDa = 14313.0;
        File f = DatasetSaver.save(d, new File(dir, "lyso.dat"), "test").written;
        LoadedFile back = new LoadedFile(f, 0, false);
        assertEquals(120, back.allData.getItemCount());
        assertEquals(f, back.getFile());
        Dataset again = new Dataset(back.allData, back.allDataError, back.filebase, 1, false);
        again.setSasObject(back.getJSONString());
        assertEquals(14313.0, again.getSasObject().getSampleRecord().theoreticalMassDa, 0);
    }

    @Test
    public void exportingTheRangeIsNotSavingTheDataset() throws Exception {
        File dir = tmp.newFolder();
        Dataset d = dataset("lyso");
        d.markSaved();
        d.getSasObject().getSampleRecord().theoreticalMassDa = 14313.0;
        d.setStart(10);
        d.setEnd(60);
        new FileObject(dir).writeSingleSAXSFile("lyso_range", d);
        assertEquals(51, dataLines(new File(dir, "lyso_range.dat")));
        assertTrue("still unsaved", d.hasUnsavedChangesNow());
        assertNull(d.getSourceFile());
    }
}
