package version4.SEC;

import FileManager.FileListBuilder;
import FileManager.ReceivedDroppedFiles;
import version4.tableModels.SampleBufferElement;
import org.jfree.data.xy.XYSeries;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import version4.Collection;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.TreeSet;

import static org.junit.Assert.*;
import static version4.SEC.SecRunFixture.*;

/**
 * SECTool's workflow from frame files on disk, without the window: drop one frame file, find the
 * series, load it, TRACE (build the .sec file), then UPDATE with a new buffer selection and with a
 * new threshold - checking the numbers that end up in the file.
 */
public class SecWorkflowTest {

    private Path dir;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("secworkflow");
    }

    @After
    public void tearDown() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** the simulated run as beamline frame files: sample_00000.dat ... */
    private File writeFrames(Collection run) throws IOException {
        Path frames = Files.createDirectories(dir.resolve("frames"));
        File first = null;
        for (int f = 0; f < run.getTotalDatasets(); f++) {
            File file = frames.resolve(String.format(Locale.ROOT, "sample_%05d.dat", f)).toFile();
            if (first == null) first = file;
            XYSeries d = run.getDataset(f).getAllData(), e = run.getDataset(f).getAllDataError();
            try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
                w.println("# q I(q) sigma");
                for (int k = 0; k < d.getItemCount(); k++)
                    w.printf(Locale.ROOT, "%.8f %.8e %.8e%n", d.getX(k).doubleValue(), d.getY(k).doubleValue(), e.getY(k).doubleValue());
            }
        }
        return first;
    }

    /** what SECTool does when one frame file is dropped: find the series in its folder and load it */
    private Collection dropOneFrameFile(File first) throws Exception {
        FileListBuilder builder = new FileListBuilder(first, first.getParent());
        File[] found = builder.getFoundFiles();
        Collection collection = new Collection("dropped");
        ReceivedDroppedFiles loader = new ReceivedDroppedFiles(found, collection, new DefaultListModel<SampleBufferElement>(),
                new JLabel(), false, true, new JProgressBar(), first.getParent());
        loader.run();
        loader.get();
        return collection;
    }

    private SECFile trace(Collection collection, String name, double threshold) throws Exception {
        SECBuilder builder = new SECBuilder(collection, new JLabel(), new JProgressBar(), name, dir.toString(), threshold);
        builder.doInBackground();
        return new SECFile(new File(builder.getOutputname()));
    }

    @Test
    public void droppingOneFrameFindsAndLoadsTheWholeSeriesInOrder() throws Exception {
        Collection collection = dropOneFrameFile(writeFrames(run()));
        assertEquals(FRAMES, collection.getTotalDatasets());
        // in acquisition order: the peak frame is where the simulation put it
        int peak = 0;
        for (int f = 0; f < FRAMES; f++) {
            double i0 = collection.getDataset(f).getAllData().getY(0).doubleValue();
            if (i0 > collection.getDataset(peak).getAllData().getY(0).doubleValue()) peak = f;
        }
        assertEquals(PEAK_FRAME, peak, 3);
    }

    @Test
    public void traceWritesTheChromatogramAndGuinierValues() throws Exception {
        Collection collection = dropOneFrameFile(writeFrames(run()));
        SECFile sec = trace(collection, "bsa", 1.01);
        try {
            assertEquals(FRAMES, sec.getTotalFrames());
            assertEquals(TOTAL_Q, sec.getTotalQValues());
            assertEquals(q(0), sec.getQvalues().get(0), 1e-7);
            XYSeries signal = sec.getSignalSeries();
            for (int f = 0; f < FRAMES; f += 7)
                assertEquals("signal of frame " + f, expectedSignal(concentration(f)), signal.getY(f).doubleValue(), 0.002);
            for (int f = 115; f <= 145; f++) {
                assertEquals("Rg of frame " + f, RG, sec.getRgbyIndex(f), 2.0);
                assertEquals("I(0) of frame " + f, concentration(f), sec.getIzerobyIndex(f), 0.04 * concentration(f));
            }
            assertTrue("buffer frames recorded", sec.getBufferCount() >= SecProcessor.MIN_BUFFER_FRAMES);
            assertEquals(1.01, sec.getThreshold(), 1e-9);
            // the subtracted peak frame in the file is the particle scattering
            java.util.ArrayList<Double> sub = sec.getSubtractedFrameAt((int) PEAK_FRAME);
            for (int k = 0; k < TOTAL_Q; k += 10)
                assertEquals("subtracted at q " + q(k), PEAK_HEIGHT * particle(q(k)), sub.get(k), 5 * sigma(q(k)));
        } finally {
            sec.closeFile();
        }
        assertTrue(new File(dir.toFile(), "bsa.sec").isFile());
    }

    @Test
    public void updateWithAChosenBufferRecordsExactlyThoseFrames() throws Exception {
        SECFile sec = trace(dropOneFrameFile(writeFrames(run())), "bsa", 1.01);
        double tracedRg = sec.getRgbyIndex((int) PEAK_FRAME);
        TreeSet<Integer> chosen = new TreeSet<>();
        for (int f = 20; f < 60; f++) chosen.add(f);
        SECBuilder rebuild = new SECBuilder(sec, chosen, new JLabel(), new JProgressBar(), dir.toString(), 1.01);
        sec.closeFile();
        rebuild.doInBackground();

        SECFile rebuilt = new SECFile(new File(rebuild.getOutputname()));
        try {
            assertEquals(chosen.size(), rebuilt.getBufferCount());
            XYSeries background = rebuilt.getBackground();
            for (int i = 0; i < background.getItemCount(); i++) {
                int frame = (int) Math.round(background.getX(i).doubleValue());
                assertTrue("frame " + frame + " was not chosen", chosen.contains(frame));
            }
            // another clean buffer choice gives the same result (TRACE skips 13 low-q points by default,
            // which puts this sphere's Guinier fit about 1 A high in both)
            assertEquals(tracedRg, rebuilt.getRgbyIndex((int) PEAK_FRAME), 0.3);
        } finally {
            rebuilt.closeFile();
        }
    }

    @Test
    public void aBufferChosenOnThePeakShowsUpInTheSignal() throws Exception {
        // choosing frames that carry sample as buffer over-subtracts: the peak signal drops
        SECFile sec = trace(dropOneFrameFile(writeFrames(run())), "bsa", 1.01);
        double before = sec.getSignalSeries().getY((int) PEAK_FRAME).doubleValue();
        TreeSet<Integer> wrong = new TreeSet<>();
        for (int f = 115; f < 125; f++) wrong.add(f);
        SECBuilder rebuild = new SECBuilder(sec, wrong, new JLabel(), new JProgressBar(), dir.toString(), 1.01);
        sec.closeFile();
        rebuild.doInBackground();
        SECFile rebuilt = new SECFile(new File(rebuild.getOutputname()));
        try {
            double after = rebuilt.getSignalSeries().getY((int) PEAK_FRAME).doubleValue();
            assertTrue("signal " + before + " -> " + after, after < before - 0.05);
        } finally {
            rebuilt.closeFile();
        }
    }

    @Test
    public void updateWithANewThresholdChangesWhichFramesHaveGuinierValues() throws Exception {
        SECFile sec = trace(dropOneFrameFile(writeFrames(run())), "bsa", 1.01);
        int lowThresholdCount = countWithRg(sec);
        SECBuilder higher = new SECBuilder(sec, new JLabel(), new JProgressBar(), dir.toString(), 1.10, 0);
        String path = sec.getAbsolutePath();
        sec.closeFile();
        higher.doInBackground();

        SECFile updated = new SECFile(new File(path));
        try {
            assertEquals(1.10, updated.getThreshold(), 1e-9);
            int count = countWithRg(updated);
            assertTrue("fewer frames above a higher threshold: " + lowThresholdCount + " -> " + count,
                    count < lowThresholdCount && count > 0);
            for (int f = 0; f < FRAMES; f++) {
                double s = updated.getSignalSeries().getY(f).doubleValue();
                if (s < 1.10) assertEquals("frame " + f + " below threshold", 0, updated.getRgbyIndex(f), 0);
            }
            assertEquals("the peak still has its Rg", RG, updated.getRgbyIndex((int) PEAK_FRAME), 1.5);
        } finally {
            updated.closeFile();
        }
    }

    private static int countWithRg(SECFile sec) {
        int n = 0;
        for (int f = 0; f < sec.getTotalFrames(); f++) if (sec.getRgbyIndex(f) > 0) n++;
        return n;
    }

    @Test
    public void tooShortSeriesIsRefusedBeforeAnythingIsWritten() throws Exception {
        Collection collection = dropOneFrameFile(writeFrames(run(SecProcessor.MIN_FRAMES - 1, f -> 0, 1)));
        try {
            new SECBuilder(collection, new JLabel(), new JProgressBar(), "short", dir.toString(), 1.01);
            fail("a short run should be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("too short"));
        }
        assertFalse(new File(dir.toFile(), "short.sec").exists());
    }

    @Test
    public void nameEndingInGzWritesACompressedFileWithTheSameContent() throws Exception {
        Collection collection = dropOneFrameFile(writeFrames(run()));
        SECFile plain = trace(collection, "plain", 1.01);
        SECFile gz = trace(collection, "packed.sec.gz", 1.01);
        try {
            assertTrue(gz.isCompressed());
            assertTrue(new File(dir.toFile(), "packed.sec.gz").isFile());
            for (int f = 0; f < FRAMES; f += 11) {
                assertEquals(plain.getSignalSeries().getY(f).doubleValue(), gz.getSignalSeries().getY(f).doubleValue(), 0);
                assertEquals(plain.getRgbyIndex(f), gz.getRgbyIndex(f), 0);
            }
        } finally {
            plain.closeFile();
            gz.closeFile();
        }
    }
}
