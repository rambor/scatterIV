package version4.SEC;

import org.jfree.data.xy.XYSeries;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import version4.Scaler.ScaleManagerSAS;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;

import static org.junit.Assert.*;
import static version4.SEC.SecRunFixture.*;

/**
 * SECTool's MERGE: frames selected under the peak are scaled to the strongest one and averaged
 * (ScaleManagerSAS), from a .sec file built from a simulated run.
 */
public class SecMergeTest {

    private static Path dir;
    private static File secPath;

    @BeforeClass
    public static void buildSecFile() throws Exception {
        dir = Files.createTempDirectory("secmerge");
        SECBuilder builder = new SECBuilder(run(), new JLabel(), new JProgressBar(), "merge", dir.toString(), 1.01);
        builder.doInBackground();
        secPath = new File(builder.getOutputname());
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static ScaleManagerSAS merge(SECFile sec, int first, int last, double qmin, double qmax) throws Exception {
        ScaleManagerSAS m = new ScaleManagerSAS(first, last, sec, new JProgressBar(), new JLabel(), true);
        m.setUpperLowerQLimits(qmin, qmax);
        m.run();
        m.get();
        return m;
    }

    @Test
    public void framesAreScaledToTheStrongestFrame() throws Exception {
        SECFile sec = new SECFile(secPath);
        try {
            ScaleManagerSAS m = merge(sec, 120, 140, 0.01, 0.15);
            ArrayList<Double> scales = m.getScaleFactors();
            assertEquals(21, scales.size());
            // the reference is the peak frame (strongest signal), scale 1
            assertEquals(1.0, scales.get((int) PEAK_FRAME - 120), 0);
            for (int f = 120; f <= 140; f++)
                assertEquals("scale of frame " + f, concentration((int) PEAK_FRAME) / concentration(f), scales.get(f - 120),
                        0.03 * concentration((int) PEAK_FRAME) / concentration(f));
        } finally {
            sec.closeFile();
        }
    }

    @Test
    public void mergedCurveIsThePeakFrameScatteringWithSmallerErrors() throws Exception {
        SECFile sec = new SECFile(secPath);
        try {
            ScaleManagerSAS m = merge(sec, 120, 140, 0.01, 0.15);
            XYSeries merged = m.getMerged(), err = m.getMergedErrors();
            assertEquals(TOTAL_Q, merged.getItemCount());
            double cRef = concentration((int) PEAK_FRAME), chi = 0;
            ArrayList<Double> peakErr = sec.getSubtractedErrorAtFrame((int) PEAK_FRAME);
            for (int k = 0; k < TOTAL_Q; k++) {
                double q = merged.getX(k).doubleValue();
                double z = (merged.getY(k).doubleValue() - cRef * particle(q)) / err.getY(k).doubleValue();
                chi += z * z;
                assertTrue("merging reduces the error at q " + q, err.getY(k).doubleValue() < peakErr.get(k));
            }
            // every frame shares the subtracted buffer, so its error must not average down with the frames
            // (it used to, and chi2 per point was ~2.7); the scale factors add a little noise of their own
            assertEquals("chi2 per point", 1.0, chi / TOTAL_Q, 0.4);
            double[] be = sec.getAveragedBufferErrors();
            for (int k = 0; k < TOTAL_Q; k++)
                assertTrue("merged error includes the whole buffer error at k = " + k, err.getY(k).doubleValue() >= 0.99 * be[k]);
        } finally {
            sec.closeFile();
        }
    }

    @Test
    public void medianAgreesWithTheWeightedMean() throws Exception {
        SECFile sec = new SECFile(secPath);
        try {
            for (int last : new int[]{140, 139}) {          // odd and even number of frames
                ScaleManagerSAS m = merge(sec, 120, last, 0.01, 0.15);
                XYSeries merged = m.getMerged(), median = m.getMedian(), err = m.getMergedErrors();
                double meanAbsZ = 0;
                for (int k = 0; k < TOTAL_Q; k++)
                    meanAbsZ += Math.abs(median.getY(k).doubleValue() - merged.getY(k).doubleValue()) / err.getY(k).doubleValue();
                meanAbsZ /= TOTAL_Q;
                // the median of n frames scatters ~1.25 sigma_1 / sqrt(n) = ~1.25 merged errors around the mean
                assertTrue("median vs mean (" + (last - 119) + " frames): " + meanAbsZ, meanAbsZ < 2.0);
            }
        } finally {
            sec.closeFile();
        }
    }

    @Test
    public void scalingRangeBeyondTheDataUsesTheDataThatIsThere() throws Exception {
        SECFile sec = new SECFile(secPath);
        try {
            ScaleManagerSAS wide = merge(sec, 120, 140, 0.01, 10.0);
            ScaleManagerSAS all = merge(sec, 120, 140, 0, 0);     // 0 = from the first / to the last q
            for (int f = 120; f <= 140; f++) {
                double expected = concentration((int) PEAK_FRAME) / concentration(f);
                assertEquals("frame " + f, expected, wide.getScaleFactors().get(f - 120), 0.05 * expected);
                assertEquals("frame " + f, expected, all.getScaleFactors().get(f - 120), 0.05 * expected);
            }
        } finally {
            sec.closeFile();
        }
    }
}
