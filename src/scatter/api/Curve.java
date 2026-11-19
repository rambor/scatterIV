package scatter.api;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.jfree.data.xy.XYSeries;

/**
 * One SAXS curve: q, I(q) and sigma(q), sorted by q. Immutable.
 *
 * Plain double[] arrays so it can be used from Python (JPype converts them to and from numpy)
 * and from the command line without any of Scatter's GUI classes.
 * q is in inverse Angstrom; sigma is NaN where a file had no error column.
 */
public final class Curve {

    private final String name;
    private final double[] q;
    private final double[] i;
    private final double[] sigma;

    public Curve(String name, double[] q, double[] intensity, double[] sigma) {
        if (q == null || intensity == null) throw new IllegalArgumentException("q and I are required");
        if (q.length != intensity.length || (sigma != null && sigma.length != q.length)) {
            throw new IllegalArgumentException("q, I and sigma must have the same length");
        }
        this.name = name == null ? "curve" : name;
        int n = q.length;
        Integer[] order = new Integer[n];
        for (int k = 0; k < n; k++) order[k] = k;
        Arrays.sort(order, (a, b) -> Double.compare(q[a], q[b]));
        this.q = new double[n];
        this.i = new double[n];
        this.sigma = new double[n];
        for (int k = 0; k < n; k++) {
            int s = order[k];
            this.q[k] = q[s];
            this.i[k] = intensity[s];
            this.sigma[k] = sigma == null ? Double.NaN : sigma[s];
        }
    }

    public String getName() { return name; }
    public int size() { return q.length; }
    public double[] getQ() { return q.clone(); }
    public double[] getI() { return i.clone(); }
    public double[] getSigma() { return sigma.clone(); }
    public double q(int k) { return q[k]; }
    public double i(int k) { return i[k]; }
    public double sigma(int k) { return sigma[k]; }

    public boolean hasErrors() {
        for (double s : sigma) if (!(s > 0) || Double.isInfinite(s)) return false;
        return q.length > 0;
    }

    public Curve withName(String newName) { return new Curve(newName, q, i, sigma); }

    /** Index of the point at q within a relative tolerance, or -1. */
    public int indexOf(double qValue, double relTol) {
        int k = Arrays.binarySearch(q, qValue);
        if (k >= 0) return k;
        int ins = -k - 1;
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int c = ins - 1; c <= ins; c++) {
            if (c < 0 || c >= q.length) continue;
            double d = Math.abs(q[c] - qValue);
            if (d < bestDiff) { bestDiff = d; best = c; }
        }
        return (best >= 0 && bestDiff <= relTol * Math.abs(qValue)) ? best : -1;
    }

    // ------------------------------------------------------------------ files

    public static Curve load(String path) throws IOException { return load(Paths.get(path), false); }

    /**
     * Reads a 2- or 3-column text file (.dat, .txt, .csv, .int; optionally .gz).
     * Lines that are not numbers (headers, comments, JSON) are skipped. Points with q <= 0
     * are dropped.
     *
     * @param qInNanometres divide q by 10 (nm^-1 to A^-1)
     */
    public static Curve load(Path path, boolean qInNanometres) throws IOException {
        List<double[]> rows = new ArrayList<>();
        boolean gz = path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                gz ? new GZIPInputStream(Files.newInputStream(path)) : Files.newInputStream(path),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                double[] row = parseLine(line);
                if (row == null) continue;
                if (qInNanometres) row[0] /= 10.0;
                if (row[0] > 0) rows.add(row);
            }
        }
        if (rows.size() < 2) throw new IOException("No data found in " + path);
        int n = rows.size();
        double[] q = new double[n], in = new double[n], s = new double[n];
        for (int k = 0; k < n; k++) {
            q[k] = rows.get(k)[0];
            in[k] = rows.get(k)[1];
            s[k] = rows.get(k)[2];
        }
        return new Curve(baseName(path), q, in, s);
    }

    /** q, I, sigma from a data line, or null if it is not one. */
    static double[] parseLine(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("{")) return null;
        // decimal commas (0,0123 4,5E-02) when the line has no '.' and is whitespace separated
        if (t.indexOf('.') < 0 && t.indexOf(',') >= 0 && t.matches(".*\\d,\\d.*\\s+.*")) {
            t = t.replace(',', '.');
        }
        String[] cols = t.split("[\\s;,]+");
        if (cols.length < 2) return null;
        double[] v = {Double.NaN, Double.NaN, Double.NaN};
        try {
            v[0] = Double.parseDouble(cols[0]);
            v[1] = Double.parseDouble(cols[1]);
            if (cols.length > 2) v[2] = Double.parseDouble(cols[2]);
        } catch (NumberFormatException e) {
            if (Double.isNaN(v[1])) return null;  // not a data line
            v[2] = Double.NaN;                    // third column is text
        }
        if (!Double.isFinite(v[0]) || !Double.isFinite(v[1])) return null;
        return v;
    }

    /** Writes q, I, sigma with optional comment lines (each prefixed by '#'). */
    public void write(Path path, String... comments) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String c : comments) {
                if (c == null) continue;
                for (String l : c.split("\\R")) w.write("# " + l + "\n");
            }
            for (int k = 0; k < q.length; k++) {
                w.write(String.format(Locale.US, "%.6E %.6E %.6E%n", q[k], i[k], sigma[k]));
            }
        }
    }

    static String baseName(Path path) {
        String f = path.getFileName().toString();
        if (f.toLowerCase(Locale.ROOT).endsWith(".gz")) f = f.substring(0, f.length() - 3);
        int dot = f.lastIndexOf('.');
        return dot > 0 ? f.substring(0, dot) : f;
    }

    // ------------------------------------------------------------------ bridges to Scatter classes

    XYSeries intensitySeries() {
        XYSeries s = new XYSeries(name, true, false);
        for (int k = 0; k < q.length; k++) s.add(q[k], i[k]);
        return s;
    }

    XYSeries errorSeries() {
        XYSeries s = new XYSeries(name + "_err", true, false);
        for (int k = 0; k < q.length; k++) s.add(q[k], sigma[k]);
        return s;
    }

    @Override
    public String toString() {
        return name + " (" + q.length + " points, q " + (q.length > 0 ? q[0] + "-" + q[q.length - 1] : "-") + ")";
    }
}
