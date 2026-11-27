package version4;

import FileManager.FileObject;
import org.jfree.data.xy.XYSeries;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/** Change tracking on datasets, and "Save all" when Scatter is closed. */
public class UnsavedChangesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Dataset dataset(String name) {
        XYSeries d = new XYSeries("d", false, false), e = new XYSeries("e", false, false);
        for (int i = 0; i < 150; i++) {
            double q = 0.01 + i*0.002, x = q*20;
            double iq = 100*Math.exp(-x*x/3) + 0.01;
            d.add(q, iq);
            e.add(q, 0.01*iq);
        }
        return new Dataset(d, e, name, 0, false);
    }

    @Test
    public void madeInMemoryIsUnsavedUntilWritten() throws Exception {
        Dataset ds = dataset("merged");
        assertTrue(ds.hasUnsavedChanges());
        new FileObject(tmp.getRoot(), "test").writeSAXSFile("merged", ds);
        assertFalse(ds.hasUnsavedChangesNow());
    }

    @Test
    public void editingDetailsOrResultsMakesItUnsaved() {
        Dataset ds = dataset("a");
        ds.markSaved();
        assertFalse(ds.hasUnsavedChangesNow());
        ds.getSasObject().getSampleRecord().theoreticalMassDa = 66432.0;          // details edited
        assertTrue(ds.hasUnsavedChangesNow());
        ds.markSaved();
        ds.setPorodVolumeReal(41000);                                             // a result changed
        assertTrue(ds.hasUnsavedChangesNow());
        ds.markSaved();
        ds.appendExperimentalNotes("checked by hand");
        assertTrue(ds.hasUnsavedChangesNow());
    }

    @Test
    public void lookingAtItDoesNotMakeItUnsaved() {
        Dataset ds = dataset("a");
        ds.markSaved();
        for (int i = 0; i < 3; i++) {
            ds.getSasObjectJSON();
            ds.getSasObject().getEffectiveSampleRecord();
        }
        assertFalse(ds.hasUnsavedChangesNow());
    }

    @Test
    public void onlyChangedDatasetsAreListed() {
        Collection c = new Collection("test");
        Dataset clean = dataset("clean"), edited = dataset("edited"), fresh = dataset("fresh");
        c.addDataset(clean);
        c.addDataset(edited);
        c.addDataset(fresh);
        clean.markSaved();
        edited.markSaved();
        edited.getSasObject().getSampleRecord().theoreticalMassDa = 1.0e5;
        List<Dataset> u = UnsavedChanges.unsaved(c);
        assertEquals(2, u.size());
        assertSame(edited, u.get(0));
        assertSame(fresh, u.get(1));
        assertEquals("edited\nfresh", UnsavedChanges.names(u, 15));
        assertEquals("edited\n... and 1 more", UnsavedChanges.names(u, 1));
    }

    @Test
    public void freeNamesNeverOverwrite() throws Exception {
        File dir = tmp.getRoot();
        assertEquals("sample", UnsavedChanges.freeBaseName(dir, "sample"));
        assertEquals("sample", UnsavedChanges.freeBaseName(dir, "sample.dat"));
        Files.write(new File(dir, "sample.dat").toPath(), new byte[]{1});
        assertEquals("sample_edited", UnsavedChanges.freeBaseName(dir, "sample"));
        Files.write(new File(dir, "sample_edited.dat").toPath(), new byte[]{1});
        assertEquals("sample_edited2", UnsavedChanges.freeBaseName(dir, "sample"));
        assertEquals("a_b", UnsavedChanges.freeBaseName(dir, "a/b"));
        assertEquals("dataset", UnsavedChanges.freeBaseName(dir, " "));
    }

    @Test
    public void saveAllWritesEveryDatasetAndLeavesExistingFilesAlone() throws Exception {
        File dir = tmp.newFolder();
        File original = new File(dir, "loaded.dat");
        Files.write(original.toPath(), "original data".getBytes(StandardCharsets.UTF_8));
        Dataset loaded = dataset("loaded"), merged = dataset("merged");
        loaded.markSaved();
        loaded.getSasObject().getSampleRecord().theoreticalMassDa = 66432.0;

        UnsavedChanges.Result r = UnsavedChanges.saveAll(java.util.Arrays.asList(loaded, merged), dir, "test");
        assertTrue(r.failed.toString(), r.failed.isEmpty());
        assertEquals(2, r.written.size());
        assertEquals("loaded_edited.dat", r.written.get(0).getName());
        assertEquals("merged.dat", r.written.get(1).getName());
        assertEquals("the loaded file is untouched", "original data",
                new String(Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8));
        assertTrue(new String(Files.readAllBytes(r.written.get(0).toPath()), StandardCharsets.UTF_8).contains("THEORETICAL MASS (DA) : 66432"));
        assertFalse(loaded.hasUnsavedChangesNow());
        assertFalse(merged.hasUnsavedChangesNow());
    }

    @Test
    public void unusableFolderIsReportedNotIgnored() throws Exception {
        File notADir = tmp.newFile("plain.txt");
        UnsavedChanges.Result r = UnsavedChanges.saveAll(java.util.Collections.singletonList(dataset("x")), notADir, "test");
        assertEquals(1, r.failed.size());
        assertTrue(r.written.isEmpty());
    }
}
