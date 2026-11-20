package version4;

import FileManager.LoadedFile;
import org.jfree.data.xy.XYSeries;
import org.junit.Test;
import scatter.api.Curve;
import scatter.api.FrameFilter;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static version4.SubtractionFixture.*;

/**
 * The whole Subtract workflow from files on disk, as the panel runs it: load the frame files,
 * compare the frames and untick the bad ones, subtract and merge, and read the written result back.
 */
public class SubtractionFilesTest {

    /** the frame comparison as the Subtract panel does it (scatter.api.FrameFilter on the loaded datasets) */
    private static FrameFilter.Result tickAgreeingFrames(Collection c, double qmin, double qmax) {
        List<Curve> curves = new ArrayList<>();
        for (int i = 0; i < c.getTotalDatasets(); i++) {
            XYSeries d = c.getDataset(i).getAllData(), e = c.getDataset(i).getAllDataError();
            int n = d.getItemCount();
            double[] q = new double[n], I = new double[n], s = new double[n];
            for (int j = 0; j < n; j++) {
                q[j] = d.getX(j).doubleValue();
                I[j] = d.getY(j).doubleValue();
                s[j] = e.getY(j).doubleValue();
            }
            curves.add(new Curve(c.getDataset(i).getFileName(), q, I, s));
        }
        FrameFilter.Result r = FrameFilter.filter(curves, new FrameFilter.Options().qRange(qmin, qmax));
        for (FrameFilter.Frame f : r.getFrames()) c.getDataset(f.index).setInUse(f.isAccepted());
        return r;
    }

    @Test
    public void anAllZeroDarkFrameIsNotLoaded() throws Exception {
        File f = File.createTempFile("dark_00000", ".dat");
        f.deleteOnExit();
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            for (double q : SAXS_Q) w.printf(java.util.Locale.ROOT, "%.6f 0.0 0.0%n", q);
        }
        try {
            new LoadedFile(f, 0, false);
            fail("an all-zero frame should not load");
        } catch (Exception expected) {
            // the panel skips it and loads the other frames
        }
    }

    @Test
    public void filesWithMeniscusAndDamageGiveTheUndamagedCurve() throws Exception {
        // 30 frames: an under-filled meniscus in frames 0-1, aggregation growing from frame 24
        Collection simulated = frames("smp", 30, SAXS_Q, (f, q) -> {
            double t = sphere(q);
            if (f == 0) t *= 0.8;
            if (f == 1) t *= 0.93;
            if (f >= 24) t += 4.0 * (f - 23) * Math.exp(-q * q * 150 * 150 / 3);
            return buffer(q) + t;
        }, 2);
        Collection samples = load(writeFiles(simulated, "smp"));
        Collection buffers = load(writeFiles(buffers(10, 1), "buf"));
        assertEquals(30, samples.getTotalDatasets());

        FrameFilter.Result r = tickAgreeingFrames(samples, 0.01, 0.25);
        tickAgreeingFrames(buffers, 0.01, 0.25);
        int[] acc = r.getAccepted();
        assertEquals("meniscus frames unticked", 2, acc[0]);
        assertTrue("damaged frames unticked (last kept " + acc[acc.length - 1] + ")", acc[acc.length - 1] <= 26);

        Run run = run(buffers, samples, Mode.AVERAGE, false, 2.5, 0.01, 0.1);
        File written = run.file("run_average_average.dat");
        assertTrue(written.isFile());

        // read the result back the way a user would open it
        LoadedFile back = new LoadedFile(written, 0, false);
        Collection reread = new Collection("reread");
        reread.createBareDataset(back);
        double[] m = compare(reread.getDataset(0));
        assertEquals("all q points written", SAXS_Q.length, m[3], 0);
        assertEquals("matches the undamaged truth, chi2 " + m[0], 1.0, m[0], 0.35);
        // the first damaged frames are below what any test can see per frame; what they leave in the
        // merge must stay well under one merged sigma
        assertEquals("little low-q bias from aggregation", 0, m[1], 0.5);
    }

    @Test
    public void waxsFramesWithADarkFirstFrameSubtractAndReport() throws Exception {
        // WAXS: q 0.7-3.4, the solute slightly below the buffer, first sample frame dark but for two pixels
        double[] waxs = grid(0.7, 3.4, 400);
        Collection simulated = frames("waxs", 12, waxs, (f, q) -> 0.997 * (1 + 0.5 * Math.exp(-(q - 2) * (q - 2) * 4)), 2);
        XYSeries d0 = simulated.getDataset(0).getAllData(), e0 = simulated.getDataset(0).getAllDataError();
        for (int j = 0; j < d0.getItemCount(); j++) {
            boolean pixel = j == 100 || j == 300;
            d0.updateByIndex(j, pixel ? 1e-4 : 0.0);
            e0.updateByIndex(j, pixel ? 1e-4 : 0.0);
        }
        Collection bufferFrames = frames("wbuf", 12, waxs, (f, q) -> 1 + 0.5 * Math.exp(-(q - 2) * (q - 2) * 4), 1);
        Collection samples = load(writeFiles(simulated, "waxs"));
        Collection buffers = load(writeFiles(bufferFrames, "wbuf"));
        assertEquals("the dark frame loads with its two pixels", 2, samples.getDataset(0).getAllData().getItemCount());

        FrameFilter.Result r = tickAgreeingFrames(samples, 0.7, 3.4);
        assertEquals(FrameFilter.Reason.INCOMPLETE, r.getFrames().get(0).reason);
        assertEquals("the others agree", 11, r.getAccepted().length);
        tickAgreeingFrames(buffers, 0.7, 3.4);

        for (Mode mode : new Mode[]{Mode.AVERAGE, Mode.MEDIAN}) {
            Run run = run(buffers, samples, mode, false, 2.5, 0.01, 0.14);
            assertTrue(mode + " merged", run.merged().getTotalDatasets() > 0);
            assertEquals(mode + " keeps all points", 400, run.merged().getDataset(0).getAllData().getItemCount());
            assertTrue(mode + " report", run.file("run.pdf").isFile());
        }

        // and with the dark frame left ticked by hand
        samples.getDataset(0).setInUse(true);
        Run run = run(buffers, samples, Mode.AVERAGE, false, 2.5, 0.01, 0.14);
        assertTrue(run.file("run.pdf").isFile());
    }

    @Test
    public void framesInNanometresAreConvertedOnLoad() throws Exception {
        // the same series written in nm^-1 (q x 10) and loaded with conversion gives the same result
        Collection simulated = samples(10, 2);
        File dir = Files.createTempDirectory("nm").toFile();
        dir.deleteOnExit();
        for (int i = 0; i < simulated.getTotalDatasets(); i++) {
            File f = new File(dir, String.format(java.util.Locale.ROOT, "nm_%05d.dat", i));
            f.deleteOnExit();
            XYSeries d = simulated.getDataset(i).getAllData(), e = simulated.getDataset(i).getAllDataError();
            try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
                for (int j = 0; j < d.getItemCount(); j++)
                    w.printf(java.util.Locale.ROOT, "%.8f %.7e %.7e%n", 10 * d.getX(j).doubleValue(), d.getY(j).doubleValue(), e.getY(j).doubleValue());
            }
        }
        File[] files = dir.listFiles();
        java.util.Arrays.sort(files);
        Collection samples = new Collection("nm");
        for (int i = 0; i < files.length; i++) samples.createBareDataset(new LoadedFile(files[i], i, true));
        for (int i = 0; i < samples.getTotalDatasets(); i++) samples.getDataset(i).setInUse(true);
        assertEquals(SAXS_Q[0], samples.getDataset(0).getAllData().getX(0).doubleValue(), 1e-6);

        Run run = run(buffers(5, 1), samples, Mode.AVERAGE);
        double[] m = compare(run.merged("average_average"));
        assertEquals("chi2 " + m[0], 1.0, m[0], 0.3);
    }
}
