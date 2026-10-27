package version4.FormFactors;

import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;

/**
 * Where the FormFactor panel gets its datasets from. The panel only needs, per dataset, a name, the measured
 * q / I(q) / sigma and (optionally) the Dmax of its P(r) model, so it does not depend on SCATTER's Dataset class
 * (version3 or version4). See the {@code FormFactors} class (default package, next to RealSpacePr) for the wiring
 * to {@code Scatter.collectionSelected}.
 */
public interface DatasetProvider {

    int count();

    String name(int index);

    /** {q, I, sigma} of all points of the dataset (any order; points with sigma <= 0 are skipped by the fitter) */
    double[][] data(int index);

    /** Dmax of the dataset's P(r) model, or 0 when none has been determined */
    default double dmax(int index) { return 0; }

    static DatasetProvider of(IntSupplier count, IntFunction<String> name, IntFunction<double[][]> data, IntToDoubleFunction dmax) {
        return new DatasetProvider() {
            @Override public int count() { return count.getAsInt(); }
            @Override public String name(int index) { return name.apply(index); }
            @Override public double[][] data(int index) { return data.apply(index); }
            @Override public double dmax(int index) { return dmax == null ? 0 : dmax.applyAsDouble(index); }
        };
    }

    /** converts SCATTER's intensity and error series (getAllData(), getAllDataError()) to {q, I, sigma} */
    static double[][] fromSeries(XYSeries intensity, XYSeries error) {
        if (intensity == null || error == null || error.getItemCount() != intensity.getItemCount())
            throw new IllegalArgumentException("Dataset has no error column matching the data");
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < intensity.getItemCount(); i++) {
            double q = intensity.getX(i).doubleValue(), y = intensity.getY(i).doubleValue(), e = error.getY(i).doubleValue();
            pts.add(new double[]{q, y, e});
        }
        double[][] out = new double[3][pts.size()];
        for (int i = 0; i < pts.size(); i++) for (int k = 0; k < 3; k++) out[k][i] = pts.get(i)[k];
        return out;
    }
}
