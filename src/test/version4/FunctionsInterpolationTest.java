package version4;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import static org.junit.Assert.*;

/** Kriging interpolation used to subtract buffers on a different q grid, including both ends of the data. */
public class FunctionsInterpolationTest {

    private static XYSeries line(int n) {
        XYSeries s = new XYSeries("line");
        for (int i = 0; i < n; i++) s.add(0.01 * (i + 1), 2.0 + 3.0 * 0.01 * (i + 1));
        return s;
    }

    @Test
    public void interpolatesInsideTheData() {
        XYSeries s = line(50);
        double q = 0.2555;
        assertEquals(2.0 + 3.0 * q, Functions.interpolate(s, q)[1], 1e-3);
        assertEquals(2.0 + 3.0 * q, Functions.interpolateSigma(s, q)[1], 1e-3);
    }

    @Test
    public void interpolatesInTheFirstInterval() {
        XYSeries s = line(50);
        double q = 0.0105;
        assertEquals(2.0 + 3.0 * q, Functions.interpolate(s, q)[1], 1e-3);
        assertEquals(2.0 + 3.0 * q, Functions.interpolateSigma(s, q)[1], 1e-3);
    }

    @Test
    public void interpolatesInTheLastInterval() {
        // used to select indices -2..3 (interpolate: IndexOutOfBounds) or the first six points (interpolateSigma)
        XYSeries s = line(50);
        double q = 0.4995;
        assertEquals(2.0 + 3.0 * q, Functions.interpolate(s, q)[1], 1e-3);
        assertEquals(2.0 + 3.0 * q, Functions.interpolateSigma(s, q)[1], 1e-3);
    }
}
