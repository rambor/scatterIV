package version4;

import FileManager.LoadedFile;
import org.jfree.data.xy.XYSeries;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * Simulated frame series for the Subtract tests: a known particle signal on a known buffer, with
 * Gaussian noise of known size, so every merged curve can be compared with the truth.
 */
final class SubtractionFixture {

    private SubtractionFixture() {}

    /** intensity of frame f at q */
    interface Signal { double at(int frame, double q); }

    /** sphere of radius 30 A, I(0) = 100 */
    static double sphere(double q) {
        double x = q * 30, f = 3 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
        return 100 * f * f;
    }

    /** smooth buffer (capillary + solvent) */
    static double buffer(double q) { return 1.0 + 0.5 * Math.exp(-q * 20); }

    /** relative error of every point; sigma = REL * I + FLOOR */
    static final double REL = 0.01, FLOOR = 1e-4;

    static double[] grid(double qmin, double qmax, int n) {
        double[] q = new double[n];
        for (int j = 0; j < n; j++) q[j] = qmin + j * (qmax - qmin) / (n - 1);
        return q;
    }

    static final double[] SAXS_Q = grid(0.008, 0.3, 300);

    static double sigmaOf(double intensity) { return REL * Math.abs(intensity) + FLOOR; }

    /** count frames on grid q, all ticked; noise from seed */
    static Collection frames(String prefix, int count, double[] q, Signal s, long seed) {
        Random r = new Random(seed);
        Collection c = new Collection(prefix);
        for (int f = 0; f < count; f++) {
            XYSeries d = new XYSeries(prefix + f), e = new XYSeries(prefix + "e" + f);
            for (double qq : q) {
                double v = s.at(f, qq), sig = sigmaOf(v);
                d.add(qq, v + sig * r.nextGaussian());
                e.add(qq, sig);
            }
            c.addDataset(new Dataset(d, e, prefix + "_" + f, f, false));
            c.getDataset(f).setInUse(true);
        }
        return c;
    }

    static Collection buffers(int count, long seed) {
        return frames("buf", count, SAXS_Q, (f, q) -> buffer(q), seed);
    }

    static Collection samples(int count, long seed) {
        return frames("smp", count, SAXS_Q, (f, q) -> buffer(q) + sphere(q), seed);
    }

    enum Mode { AVERAGE, MEDIAN, SINGLES }

    /** a finished subtraction and the directory it wrote to */
    static final class Run {
        final Subtraction subtraction;
        final File dir;
        Run(Subtraction s, File d) { subtraction = s; dir = d; }

        Collection merged() { return subtraction.getSubtractedMergedCollection(); }

        Dataset merged(String suffix) {
            Collection m = merged();
            for (int i = 0; i < m.getTotalDatasets(); i++)
                if (m.getDataset(i).getFileName().endsWith(suffix)) return m.getDataset(i);
            throw new AssertionError("no merged set ending in " + suffix);
        }

        File file(String name) { return new File(dir, name); }
    }

    static Run run(Collection buffers, Collection samples, Mode mode, boolean scale, double cutoff,
                   double qmin, double qmax) throws Exception {
        File dir = Files.createTempDirectory("subtract").toFile();
        dir.deleteOnExit();
        Subtraction sub = new Subtraction(buffers, samples, qmin, qmax, mode == Mode.AVERAGE, mode == Mode.SINGLES,
                scale, 1, false, new JLabel(), new JProgressBar());
        sub.setBinsAndCutoff(23, cutoff);
        sub.setNameAndDirectory("run", dir.getAbsolutePath());
        sub.setCollectionToUpdate(new Collection("out"));
        sub.execute();
        sub.get();
        return new Run(sub, dir);
    }

    static Run run(Collection buffers, Collection samples, Mode mode) throws Exception {
        return run(buffers, samples, mode, false, 2.5, 0.01, 0.1);
    }

    /** {chi2 per point, mean z (q < 0.06), mean z (q >= 0.06), points} of a curve against the truth */
    static double[] compare(Dataset d, DoubleUnaryOperator truth) {
        XYSeries y = d.getAllData(), e = d.getAllDataError();
        double chi = 0, lo = 0, hi = 0;
        int nlo = 0, nhi = 0;
        for (int j = 0; j < y.getItemCount(); j++) {
            double q = y.getX(j).doubleValue();
            double z = (y.getY(j).doubleValue() - truth.applyAsDouble(q)) / e.getY(j).doubleValue();
            chi += z * z;
            if (q < 0.06) { lo += z; nlo++; } else { hi += z; nhi++; }
        }
        int n = y.getItemCount();
        return new double[]{chi / n, nlo > 0 ? lo / nlo : 0, nhi > 0 ? hi / nhi : 0, n};
    }

    static double[] compare(Dataset d) { return compare(d, SubtractionFixture::sphere); }

    /** writes a frame series as 3-column .dat files (q, I, sigma), like a beamline does */
    static File writeFiles(Collection frames, String stem) throws IOException {
        File dir = Files.createTempDirectory("frames").toFile();
        dir.deleteOnExit();
        for (int i = 0; i < frames.getTotalDatasets(); i++) {
            File f = new File(dir, String.format(Locale.ROOT, "%s_%05d.dat", stem, i));
            f.deleteOnExit();
            XYSeries d = frames.getDataset(i).getAllData(), e = frames.getDataset(i).getAllDataError();
            try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
                w.println("# q I sigma");
                for (int j = 0; j < d.getItemCount(); j++)
                    w.printf(Locale.ROOT, "%.8f %.7e %.7e%n", d.getX(j).doubleValue(), d.getY(j).doubleValue(), e.getY(j).doubleValue());
            }
        }
        return dir;
    }

    /** loads every .dat file of a directory the way the Subtract panel does; files that cannot be read are skipped */
    static Collection load(File dir) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".dat"));
        java.util.Arrays.sort(files);
        Collection c = new Collection(dir.getName());
        for (int i = 0; i < files.length; i++) {
            try {
                LoadedFile lf = new LoadedFile(files[i], i, false);
                if (lf.isValid()) c.createBareDataset(lf);
            } catch (Exception skipped) {
                // the panel skips unreadable files the same way (e.g. an all-zero dark frame)
            }
        }
        for (int i = 0; i < c.getTotalDatasets(); i++) c.getDataset(i).setInUse(true);
        return c;
    }
}
