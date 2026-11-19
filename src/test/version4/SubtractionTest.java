package version4;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;

import static org.junit.Assert.*;

/** Simulated frame series through Subtraction: the merged curve must match the truth within its errors. */
public class SubtractionTest {

    private static double truth(double q) {
        double x = q * 30, f = 3 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
        return 100 * f * f;
    }

    private static double buffer(double q) { return 1.0 + 0.5 * Math.exp(-q * 20); }

    /** {chi2 per point, mean z at q < 0.06, points} of every merged curve */
    private static double[][] run(boolean bufferOffGrid, boolean badFrames, boolean byAverage) throws Exception {
        Random r = new Random(1);
        int n = 400;
        double[] q = new double[n];
        for (int j = 0; j < n; j++) q[j] = 0.008 + j * (0.3 - 0.008) / (n - 1);
        Collection buffers = new Collection("b"), samples = new Collection("s");
        for (int f = 0; f < 10; f++) {
            XYSeries d = new XYSeries("b" + f), e = new XYSeries("be" + f);
            for (int j = 0; j < n; j++) {
                double qq = bufferOffGrid && j % 7 == 3 ? q[j] + 1e-5 : q[j];
                double s = 0.01 * buffer(qq);
                d.add(qq, buffer(qq) + s * r.nextGaussian());
                e.add(qq, s);
            }
            buffers.addDataset(new Dataset(d, e, "buf_" + f, f, false));
            buffers.getDataset(f).setInUse(true);
        }
        for (int f = 0; f < 20; f++) {
            XYSeries d = new XYSeries("s" + f), e = new XYSeries("se" + f);
            for (int j = 0; j < n; j++) {
                double t = truth(q[j]);
                if (badFrames && f >= 17) t += 0.4 * (f - 16) * Math.exp(-q[j] * q[j] * 400 * 400 / 3);  // aggregation
                if (badFrames && f == 0) t *= 0.9;                                                      // meniscus
                double v = buffer(q[j]) + t, s = 0.01 * v;
                d.add(q[j], v + s * r.nextGaussian());
                e.add(q[j], s);
            }
            samples.addDataset(new Dataset(d, e, "smp_" + f, f, false));
            samples.getDataset(f).setInUse(true);
        }
        File dir = Files.createTempDirectory("subtest").toFile();
        Subtraction sub = new Subtraction(buffers, samples, 0.01, 0.25, byAverage, false, false, 2, false,
                new JLabel(), new JProgressBar());
        sub.setBinsAndCutoff(23, 2.5);
        sub.setNameAndDirectory("sim", dir.getAbsolutePath());
        sub.setCollectionToUpdate(new Collection("out"));
        sub.execute();
        sub.get();
        Collection res = sub.getSubtractedMergedCollection();
        double[][] out = new double[res.getTotalDatasets()][];
        for (int k = 0; k < res.getTotalDatasets(); k++) {
            XYSeries y = res.getDataset(k).getAllData(), e = res.getDataset(k).getAllDataError();
            double chi = 0, lo = 0;
            int nlo = 0;
            for (int j = 0; j < y.getItemCount(); j++) {
                double qq = y.getX(j).doubleValue(), z = (y.getY(j).doubleValue() - truth(qq)) / e.getY(j).doubleValue();
                chi += z * z;
                if (qq < 0.06) { lo += z; nlo++; }
            }
            out[k] = new double[]{chi / y.getItemCount(), lo / nlo, y.getItemCount()};
        }
        return out;
    }

    @Test
    public void averageMatchesTruthWithHonestErrors() throws Exception {
        for (double[] m : run(false, false, true)) {
            assertEquals("all q points kept", 400, m[2], 0);
            assertEquals("chi2 per point " + m[0], 1.0, m[0], 0.25);
        }
    }

    @Test
    public void bufferOffTheSampleGridIsInterpolatedAndSubtracted() throws Exception {
        for (double[] m : run(true, false, true)) assertTrue("chi2 per point " + m[0], m[0] < 1.3);
    }

    @Test
    public void rejectionRemovesFramesThatDifferInEitherDirection() throws Exception {
        for (double[] m : run(false, true, true)) {
            assertTrue("chi2 per point " + m[0], m[0] < 1.3);
            assertEquals("no bias at low q", 0, m[1], 0.3);
        }
    }

    @Test
    public void medianMergeHasStandardErrorOfTheMedian() throws Exception {
        for (double[] m : run(false, false, false)) assertEquals("chi2 per point " + m[0], 1.0, m[0], 0.3);
    }
}
