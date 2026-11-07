package version4.SEC;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Post-peak baseline check on real runs and synthetic cases. */
public class BaselineCheckTest {

    private static final int FRAMES = 600;
    private static final int TOTAL_Q = 200;

    // ------------------------------------------------------------------ real data

    @Test
    public void radiationDamagedRunIsFlaggedAsLowQExcess() throws IOException {
        File file = new File("src/test/testData/BSA_1xPBS1_0_1xPBS_raddam.sec.gz");
        assumeTrue(file.exists());
        SECFile sec = new SECFile(file);
        try {
            BaselineCheck.Result r = sec.getBaselineCheck();
            assertEquals(r.summary(), BaselineCheck.Status.BASELINE_SHIFT, r.status);
            assertEquals(BaselineCheck.Shape.LOW_Q, r.shape);
            assertTrue(r.offset > 0);
            assertEquals(12.4, r.offsetPercentOfPeak(), 1.0);
            assertEquals(367, r.peakFrame);
            assertTrue(r.summary().contains("fouling"));
            // every frame after the peak stays above the baseline
            assertTrue(r.summary(), r.neverReturns());
            assertTrue(r.runFirst <= 370);
            assertEquals(599, r.runFirst + r.runLength - 1);
            // the falling edge reaches the displaced plateau well after the peak
            assertTrue("plateau starts at " + r.plateauFirst, r.plateauFirst > 420 && r.plateauFirst < 500);
        } finally {
            sec.closeFile();
        }
    }

    @Test
    public void cleanRunPasses() throws IOException {
        File file = new File("src/test/testData/BSA_sec.sec");
        assumeTrue(file.exists());
        SECFile sec = new SECFile(file);
        try {
            BaselineCheck.Result r = sec.getBaselineCheck();
            assertEquals(r.summary(), BaselineCheck.Status.OK, r.status);
            assertTrue(Math.abs(r.offsetPercentOfPeak()) < 1.0);
            assertEquals("no run of shifted frames", -1, r.runFirst);
        } finally {
            sec.closeFile();
        }
    }

    // ------------------------------------------------------------------ synthetic

    /**
     * Run with buffer frames 50-200, a peak at frame 350 and, after frame 420, an optional post-peak change:
     * {@code lowQ} adds scattering that decays with q (deposit), {@code uniform} adds the same at all q.
     */
    private static BaselineCheck.Result synthetic(double lowQ, double uniform, long seed) throws IOException {
        Random random = new Random(seed);
        double[] q = new double[TOTAL_Q];
        double[] buffer = new double[TOTAL_Q];
        for (int k = 0; k < TOTAL_Q; k++) {
            q[k] = 0.005 + k * 0.0015;
            buffer[k] = 1.0 + 0.3 * Math.exp(-q[k] * 8);
        }
        double[][] frames = new double[FRAMES][TOTAL_Q];
        double[] signal = new double[FRAMES];
        boolean[] isBuffer = new boolean[FRAMES];
        for (int f = 0; f < FRAMES; f++) {
            isBuffer[f] = f >= 50 && f <= 200;
            double peak = 0.3 * Math.exp(-Math.pow((f - 350) / 12.0, 2));
            double after = f > 420 ? 1.0 : 0.0;
            double ratioSum = 0;
            for (int k = 0; k < TOTAL_Q; k++) {
                double particle = peak * Math.exp(-q[k] * q[k] * 30 * 30 / 3.0);
                double deposit = after * lowQ * Math.exp(-q[k] * q[k] * 150 * 150 / 3.0) * 20;
                double offset = after * uniform;
                frames[f][k] = buffer[k] * (1 + particle + offset) + buffer[k] * deposit;
                ratioSum += frames[f][k] / buffer[k];
            }
            signal[f] = ratioSum / TOTAL_Q + 0.0004 * random.nextGaussian();
        }
        return BaselineCheck.analyse(signal, isBuffer, q, buffer, frame -> frames[frame]);
    }

    @Test
    public void flatBaselinePasses() throws IOException {
        BaselineCheck.Result r = synthetic(0, 0, 1);
        assertEquals(r.summary(), BaselineCheck.Status.OK, r.status);
        assertEquals(350, r.peakFrame, 2);
        assertTrue(r.tailFirst > 420);
    }

    @Test
    public void lowQDepositIsFlaggedAsFouling() throws IOException {
        BaselineCheck.Result r = synthetic(0.01, 0, 2);
        assertEquals(r.summary(), BaselineCheck.Status.BASELINE_SHIFT, r.status);
        assertEquals(r.summary(), BaselineCheck.Shape.LOW_Q, r.shape);
        // the deposit starts at frame 421, after the peak has returned to baseline
        assertFalse(r.neverReturns());
        assertEquals(421, r.runFirst, 3);
    }

    @Test
    public void uniformOffsetPointsToNormalisation() throws IOException {
        BaselineCheck.Result r = synthetic(0, 0.02, 3);
        assertEquals(r.summary(), BaselineCheck.Status.BASELINE_SHIFT, r.status);
        assertEquals(r.summary(), BaselineCheck.Shape.UNIFORM, r.shape);
        assertTrue(r.summary().contains("normalisation"));
    }

    @Test
    public void loweredBaselineIsFlagged() throws IOException {
        BaselineCheck.Result r = synthetic(0, -0.02, 4);
        assertEquals(BaselineCheck.Status.BASELINE_SHIFT, r.status);
        assertTrue(r.offset < 0);
        assertTrue(r.summary().contains("lowered"));
    }

    @Test
    public void smallShiftBelowTwoPercentOfPeakIsNotFlagged() throws IOException {
        double peakHeight = synthetic(0, 0, 5).peakHeight;
        BaselineCheck.Result r = synthetic(0, 0.01 * peakHeight, 5); // 1% of the peak: real but too small to flag
        assertTrue("shift should be statistically clear", Math.abs(r.significance()) > BaselineCheck.SIGNIFICANCE);
        assertEquals(r.summary(), BaselineCheck.Status.OK, r.status);
    }

    @Test
    public void bufferSelectionOnThePeakFootDoesNotBiasTheBaseline() throws IOException {
        Random random = new Random(6);
        double[] signal = new double[FRAMES];
        boolean[] isBuffer = new boolean[FRAMES];
        for (int f = 0; f < FRAMES; f++) {
            signal[f] = 1.0 + 0.3 * Math.exp(-Math.pow((f - 350) / 25.0, 2)) + 0.0004 * random.nextGaussian();
            isBuffer[f] = f >= 50 && f <= 320; // runs well into the rising edge
        }
        BaselineCheck.Result r = BaselineCheck.analyse(signal, isBuffer, null, null, null);
        assertEquals(r.summary(), BaselineCheck.Status.OK, r.status);
        assertTrue("foot frames should be trimmed", r.preLast < 300);
    }

    @Test
    public void tooShortOrPeakAtEndIsNotAssessable() throws IOException {
        double[] signal = new double[FRAMES];
        boolean[] isBuffer = new boolean[FRAMES];
        for (int f = 0; f < FRAMES; f++) {
            signal[f] = 1.0 + 0.3 * Math.exp(-Math.pow((f - 590) / 10.0, 2));
            isBuffer[f] = f < 200;
        }
        assertEquals(BaselineCheck.Status.NOT_ASSESSABLE, BaselineCheck.analyse(signal, isBuffer, null, null, null).status);
        assertEquals(BaselineCheck.Status.NOT_ASSESSABLE,
                BaselineCheck.analyse(new double[50], new boolean[50], null, null, null).status);
    }
}
