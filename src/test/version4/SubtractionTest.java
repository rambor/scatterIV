package version4;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;
import static version4.SubtractionFixture.*;

/**
 * Subtraction (the engine behind the Subtract panel) on simulated frame series with a known answer:
 * merging modes, which frames and buffers are used, interpolation, outlier rejection, error
 * propagation, scaling, output files and the awkward inputs that used to crash it.
 */
public class SubtractionTest {

    private static final double TOL = 0.25;    // allowed deviation of chi2 per point from 1

    // ---------------------------------------------------------------- merging modes

    @Test
    public void averageMergeMatchesTruthWithHonestErrors() throws Exception {
        Run r = run(buffers(10, 1), samples(20, 2), Mode.AVERAGE);
        for (int i = 0; i < r.merged().getTotalDatasets(); i++) {
            double[] m = compare(r.merged().getDataset(i));
            assertEquals("all q points kept", SAXS_Q.length, m[3], 0);
            assertEquals("chi2 per point", 1.0, m[0], TOL);
            assertEquals("no bias at low q", 0, m[1], 0.25);
            assertEquals("no bias at high q", 0, m[2], 0.25);
        }
    }

    @Test
    public void medianMergeMatchesTruthWithStandardErrorOfTheMedian() throws Exception {
        Run r = run(buffers(10, 1), samples(20, 2), Mode.MEDIAN);
        for (int i = 0; i < r.merged().getTotalDatasets(); i++) {
            double[] m = compare(r.merged().getDataset(i));
            assertEquals("all q points kept", SAXS_Q.length, m[3], 0);
            assertEquals("chi2 per point " + m[0], 1.0, m[0], 0.35);
        }
    }

    @Test
    public void averageWithThreeOrMoreBuffersAlsoUsesTheMedianBuffer() throws Exception {
        Run r = run(buffers(3, 1), samples(8, 2), Mode.AVERAGE);
        assertEquals(2, r.merged().getTotalDatasets());
        assertTrue(r.file("run_average_average.dat").isFile());
        assertTrue(r.file("run_average_median.dat").isFile());
        assertTrue("report written", r.file("run.pdf").isFile());
    }

    @Test
    public void averageWithTwoBuffersHasOnlyTheAverageBufferVersion() throws Exception {
        Run r = run(buffers(2, 1), samples(8, 2), Mode.AVERAGE);
        assertEquals(1, r.merged().getTotalDatasets());
        assertTrue(r.file("run_average_average.dat").isFile());
        assertFalse(r.file("run_average_median.dat").exists());
    }

    @Test
    public void medianOutputFilesHaveTheSameSafeNamesAsTheAverageOnes() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("subtract").toFile();
        Subtraction sub = new Subtraction(buffers(3, 1), samples(6, 2), 0.01, 0.1, false, false, false, 1, false,
                new javax.swing.JLabel(), new javax.swing.JProgressBar());
        sub.setBinsAndCutoff(23, 2.5);
        sub.setNameAndDirectory("my run/1", dir.getAbsolutePath());
        sub.setCollectionToUpdate(new Collection("out"));
        sub.execute();
        sub.get();
        String[] names = dir.list();
        Arrays.sort(names);
        for (String n : names) assertFalse("no spaces in " + n, n.contains(" "));
        assertTrue(Arrays.toString(names), new File(dir, "my_run_per_1_medianSample_averageBuffer.dat").isFile());
        assertTrue(Arrays.toString(names), new File(dir, "my_run_per_1_medianSample_medianBuffer.dat").isFile());
    }

    @Test
    public void singlesWritesOneFilePerTickedFrameWithBufferErrorIncluded() throws Exception {
        Collection samples = samples(6, 2);
        samples.getDataset(4).setInUse(false);
        Run r = run(buffers(10, 1), samples, Mode.SINGLES);
        assertEquals("no merged sets", 0, r.merged().getTotalDatasets());
        Collection singles = r.subtraction.getReturnCollection();
        assertEquals("one per ticked frame", 5, singles.getTotalDatasets());
        for (int f : new int[]{0, 1, 2, 3, 5}) assertTrue(r.file("smp_" + f + "_sub.dat").isFile());
        assertFalse("unticked frame not written", r.file("smp_4_sub.dat").exists());
        for (int i = 0; i < singles.getTotalDatasets(); i++) {
            double[] m = compare(singles.getDataset(i));
            assertEquals(SAXS_Q.length, m[3], 0);
            assertEquals("single frame chi2 " + m[0], 1.0, m[0], 0.3);
        }
    }

    @Test
    public void oneSampleFrameAndOneBufferFrame() throws Exception {
        Run r = run(buffers(1, 1), samples(1, 2), Mode.AVERAGE);
        double[] m = compare(r.merged("average_average"));
        assertEquals(SAXS_Q.length, m[3], 0);
        assertEquals("chi2 " + m[0], 1.0, m[0], 0.35);
    }

    // ---------------------------------------------------------------- which frames are used

    @Test
    public void untickedSampleFramesAreLeftOut() throws Exception {
        // frames 0 and 19 are wildly off; unticked they must not matter even without rejection
        Collection samples = frames("smp", 20, SAXS_Q, (f, q) -> buffer(q) + sphere(q) * (f == 0 || f == 19 ? 3 : 1), 2);
        samples.getDataset(0).setInUse(false);
        samples.getDataset(19).setInUse(false);
        Run r = run(buffers(10, 1), samples, Mode.AVERAGE, false, 1000, 0.01, 0.1);
        double[] m = compare(r.merged("average_average"));
        assertEquals("chi2 " + m[0], 1.0, m[0], TOL);
    }

    @Test
    public void untickedBufferFramesAreLeftOut() throws Exception {
        Collection buffers = frames("buf", 10, SAXS_Q, (f, q) -> buffer(q) * (f == 3 ? 1.5 : 1), 1);
        buffers.getDataset(3).setInUse(false);
        Run r = run(buffers, samples(20, 2), Mode.AVERAGE, false, 1000, 0.01, 0.1);
        for (int i = 0; i < r.merged().getTotalDatasets(); i++) {
            double[] m = compare(r.merged().getDataset(i));
            assertEquals("chi2 " + m[0], 1.0, m[0], TOL);
        }
    }

    @Test
    public void withOneBufferTickedThatBufferIsUsedNotTheFirst() throws Exception {
        // buffer 1 is the only ticked one and is offset by +0.2: the result must be sphere - 0.2
        Collection buffers = frames("buf", 3, SAXS_Q, (f, q) -> buffer(q) + (f == 1 ? 0.2 : f == 0 ? -5 : 5), 1);
        buffers.getDataset(0).setInUse(false);
        buffers.getDataset(2).setInUse(false);
        Run r = run(buffers, samples(20, 2), Mode.AVERAGE);
        double[] m = compare(r.merged("average_average"), q -> sphere(q) - 0.2);
        assertEquals("chi2 " + m[0], 1.0, m[0], 0.4);
    }

    // ---------------------------------------------------------------- q grids

    @Test
    public void bufferOffTheSampleGridIsInterpolatedAndSubtracted() throws Exception {
        double[] shifted = SAXS_Q.clone();
        for (int j = 3; j < shifted.length; j += 7) shifted[j] += 1e-5;
        Collection buffers = frames("buf", 10, shifted, (f, q) -> buffer(q), 1);
        Run r = run(buffers, samples(20, 2), Mode.AVERAGE);
        double[] m = compare(r.merged("average_average"));
        assertEquals("chi2 " + m[0], 1.0, m[0], 0.3);
    }

    @Test
    public void sampleQBeyondTheBufferRangeIsDroppedNotFilledWithGarbage() throws Exception {
        double[] narrow = grid(0.02, 0.25, 240);
        Collection buffers = frames("buf", 10, narrow, (f, q) -> buffer(q), 1);
        Run r = run(buffers, samples(20, 2), Mode.AVERAGE, false, 2.5, 0.03, 0.1);
        Dataset d = r.merged("average_average");
        XYSeries y = d.getAllData();
        assertTrue("starts inside the buffer range: " + y.getMinX(), y.getMinX() >= 0.02 - 1e-9);
        assertTrue("ends inside the buffer range: " + y.getMaxX(), y.getMaxX() <= 0.25 + 1e-9);
        double[] m = compare(d);
        assertEquals("chi2 " + m[0], 1.0, m[0], 0.35);
    }

    @Test
    public void framesWithDifferentQRangesMergeOverTheirUnion() throws Exception {
        Collection samples = samples(20, 2);
        // frame 5 lost its last 100 points (e.g. a detector gap)
        XYSeries d = samples.getDataset(5).getAllData(), e = samples.getDataset(5).getAllDataError();
        int n = d.getItemCount();
        d.delete(n - 100, n - 1);
        e.delete(n - 100, n - 1);
        Run r = run(buffers(10, 1), samples, Mode.AVERAGE);
        double[] m = compare(r.merged("average_average"));
        assertEquals("full q range", SAXS_Q.length, m[3], 0);
        assertEquals("chi2 " + m[0], 1.0, m[0], TOL);
    }

    // ---------------------------------------------------------------- outlier rejection

    @Test
    public void rejectionRemovesFramesThatDifferInEitherDirection() throws Exception {
        // aggregation (too high at low q) in the last 3 frames, an under-filled meniscus frame (too low)
        Collection samples = frames("smp", 20, SAXS_Q, (f, q) -> {
            double t = sphere(q);
            if (f >= 17) t += 0.4 * (f - 16) * Math.exp(-q * q * 400 * 400 / 3);
            if (f == 0) t *= 0.9;
            return buffer(q) + t;
        }, 2);
        Run r = run(buffers(10, 1), samples, Mode.AVERAGE);
        for (int i = 0; i < r.merged().getTotalDatasets(); i++) {
            double[] m = compare(r.merged().getDataset(i));
            assertTrue("chi2 " + m[0], m[0] < 1.3);
            assertEquals("no bias at low q", 0, m[1], 0.3);
        }
    }

    @Test
    public void rejectionLeavesCleanDataAlone() throws Exception {
        Run strict = run(buffers(10, 1), samples(20, 2), Mode.AVERAGE, false, 2.5, 0.01, 0.1);
        Run none = run(buffers(10, 1), samples(20, 2), Mode.AVERAGE, false, 1e6, 0.01, 0.1);
        XYSeries a = strict.merged("average_average").getAllData(), b = none.merged("average_average").getAllData();
        XYSeries e = none.merged("average_average").getAllDataError();
        assertEquals(a.getItemCount(), b.getItemCount());
        double meanShift = 0;
        for (int j = 0; j < a.getItemCount(); j++)
            meanShift += Math.abs(a.getY(j).doubleValue() - b.getY(j).doubleValue()) / e.getY(j).doubleValue();
        meanShift /= a.getItemCount();
        assertTrue("mean shift " + meanShift + " sigma", meanShift < 0.3);
    }

    // ---------------------------------------------------------------- error propagation

    @Test
    public void mergedErrorCarriesTheSharedBufferErrorOnce() throws Exception {
        int ns = 20, nb = 10;
        Run r = run(buffers(nb, 1), samples(ns, 2), Mode.AVERAGE, false, 1e6, 0.01, 0.1);
        Dataset d = r.merged("average_average");
        XYSeries e = d.getAllDataError();
        for (int j = 0; j < e.getItemCount(); j += 37) {
            double q = e.getX(j).doubleValue();
            double ss = sigmaOf(buffer(q) + sphere(q)), sb = sigmaOf(buffer(q));
            double expected = Math.sqrt(ss * ss / ns + sb * sb / nb);
            assertEquals("sigma at q " + q, expected, e.getY(j).doubleValue(), 0.15 * expected);
        }
    }

    // ---------------------------------------------------------------- scaling

    @Test
    public void scaleBeforeMergingNormalisesConcentration() throws Exception {
        double[] conc = new double[8];
        for (int f = 0; f < conc.length; f++) conc[f] = 0.8 + 0.05 * f;
        Collection samples = frames("smp", conc.length, SAXS_Q, (f, q) -> buffer(q) + conc[f] * sphere(q), 2);
        Run r = run(buffers(5, 1), samples, Mode.AVERAGE, true, 2.5, 0.01, 0.1);
        Collection scaled = r.subtraction.getReturnCollection();
        double ref = scaled.getDataset(0).getScaleFactor() * conc[0];
        for (int i = 0; i < scaled.getTotalDatasets(); i++) {
            double product = scaled.getDataset(i).getScaleFactor() * conc[i];
            assertEquals("scale x concentration, frame " + i, ref, product, 0.03 * ref);
        }
        // the merged curve has the shape of the truth (on the reference frame's scale)
        Dataset d = r.merged("average_average");
        XYSeries y = d.getAllData(), e = d.getAllDataError();
        double num = 0, den = 0;
        for (int j = 0; j < y.getItemCount(); j++) {
            double t = sphere(y.getX(j).doubleValue()), w = 1 / Math.pow(e.getY(j).doubleValue(), 2);
            num += y.getY(j).doubleValue() * t * w;
            den += t * t * w;
        }
        double k = num / den;
        assertEquals("on the scale of the frames", ref, k, 0.03 * ref);
        double[] m = compare(d, q -> sphere(q) * k);
        assertTrue("chi2 " + m[0], m[0] < 1.5);
    }

    // ---------------------------------------------------------------- awkward inputs (former crashes)

    @Test
    public void subtractionNegativeAlmostEverywhereIsFine() throws Exception {
        // WAXS-like: the solute displaces solvent, so sample < buffer at nearly every q
        Collection samples = frames("smp", 12, SAXS_Q, (f, q) -> 0.997 * buffer(q), 2);
        for (Mode mode : Mode.values()) {
            Run r = run(buffers(10, 1), samples, mode);
            Collection out = mode == Mode.SINGLES ? r.subtraction.getReturnCollection() : r.merged();
            assertTrue(mode + " produced output", out.getTotalDatasets() > 0);
            for (int i = 0; i < out.getTotalDatasets(); i++)
                assertEquals(mode + " keeps all points", SAXS_Q.length, out.getDataset(i).getAllData().getItemCount());
            assertTrue(mode + " report written", r.file("run.pdf").isFile());
        }
    }

    @Test
    public void aTickedNearlyEmptyFrameDoesNotCrashMergingOrTheReport() throws Exception {
        // a dark frame with two non-zero pixels, left ticked
        Collection samples = samples(12, 2);
        XYSeries d = samples.getDataset(0).getAllData(), e = samples.getDataset(0).getAllDataError();
        double q1 = d.getX(100).doubleValue(), q2 = d.getX(200).doubleValue();
        d.clear();
        e.clear();
        d.add(q1, 1e-4);
        e.add(q1, 1e-4);
        d.add(q2, 1e-4);
        e.add(q2, 1e-4);
        for (Mode mode : new Mode[]{Mode.AVERAGE, Mode.MEDIAN}) {
            Run r = run(buffers(10, 1), samples, mode);
            assertTrue(mode + " merged", r.merged().getTotalDatasets() > 0);
            assertTrue(mode + " report written", r.file("run.pdf").isFile());
        }
    }

    @Test
    public void scalingRangeWithoutDataDoesNotCorruptTheMerge() throws Exception {
        // SAXS default range with data that has no points there: scaling cannot work, merging still must
        double[] waxs = grid(0.7, 3.4, 200);
        Collection buffers = frames("buf", 5, waxs, (f, q) -> buffer(q), 1);
        Collection samples = frames("smp", 6, waxs, (f, q) -> buffer(q) + 0.01, 2);
        Run r = run(buffers, samples, Mode.AVERAGE, true, 2.5, 0.01, 0.14);
        assertTrue(r.merged().getTotalDatasets() > 0);
        assertEquals(200, r.merged("average_average").getAllData().getItemCount());
    }
}
