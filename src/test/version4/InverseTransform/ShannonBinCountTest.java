package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * The Shannon-binned P(r) written to _pr.dat (getHeader -> BIN_i lines) must have
 * ceil(qmax*dmax/pi) bins of width pi/qmax at the mid-points (k + 1/2) pi/qmax, the last one
 * holding the partial bin that contains dmax, and the header must report that width and count.
 * Cases: the BSA file (qmax 0.370884, dmax 90.5 -> Shannon number 10.68, 11 bins) and an
 * SMRC-like case (Shannon number 7.94, 8 bins).
 */
public class ShannonBinCountTest {

    private static final Pattern BIN = Pattern.compile("BIN_\\d+\\s+([0-9.]+)\\s*:");
    private static final Pattern WIDTH = Pattern.compile("BIN WIDTH \\(delta r\\) : ([0-9.]+)");
    private static final Pattern TOTAL = Pattern.compile("TOTAL BINS : (\\d+)");
    private static final Pattern SHANNON = Pattern.compile("SHANNON NUMBER \\(QMAX\\*DMAX/PI\\) : ([0-9.]+)");

    /** q*I(q) of a uniform sphere of radius R, with 1% errors */
    private static XYSeries[] sphere(double radius, double qmax) {
        XYSeries d = new XYSeries("d"), e = new XYSeries("e");
        int n = 400;
        double qmin = 0.008;
        for (int i = 0; i < n; i++) {
            double q = qmin + (qmax - qmin)*i/(n - 1);
            double x = q*radius;
            double f = 3*(Math.sin(x) - x*Math.cos(x))/(x*x*x);
            double iq = 1000*f*f + 1e-4;
            d.add(q, q*iq);
            e.add(q, 0.01*q*iq + 1e-6);
        }
        return new XYSeries[]{d, e};
    }

    private static List<IndirectFT> transforms(double dmax, double qmax) {
        XYSeries[] s = sphere(0.5*dmax*0.98, qmax);
        List<IndirectFT> list = new ArrayList<>();
        list.add(new MooreTransformApache(s[0], s[1], dmax, qmax, 1e-4, false));
        list.add(new SineIntegralTransform(s[0], s[1], dmax, qmax, 1e-4, false, false));
        list.add(new LegendreTransform(s[0], s[1], dmax, qmax, 1e-4, false));
        list.add(new DirectSineIntegralTransform(s[0], s[1], dmax, qmax, 1e-4, false));
        list.add(new SVD(s[0], s[1], dmax, qmax, false));
        return list;
    }

    private void check(double dmax, double qmax, int expectedBins) {
        assertEquals(expectedBins, (int) Math.ceil(qmax*dmax/Math.PI));
        double B = Math.PI/qmax;
        for (IndirectFT ft : transforms(dmax, qmax)) {
            String name = ft.getClass().getSimpleName();
            String header = ft.getHeader(1.0);

            List<Double> r = new ArrayList<>();
            Matcher m = BIN.matcher(header);
            while (m.find()) r.add(Double.parseDouble(m.group(1)));
            assertEquals(name + " bin count", expectedBins, r.size());
            for (int k = 0; k < r.size(); k++) {
                assertEquals(name + " mid-point of bin " + (k + 1), (k + 0.5)*B, r.get(k), 2e-3);
            }
            assertTrue(name + ": last bin must contain dmax", (r.size() - 0.5)*B + 0.5*B >= dmax - 1e-9);

            Matcher w = WIDTH.matcher(header);
            int widths = 0;
            while (w.find()) {
                widths++;
                assertEquals(name + " BIN WIDTH (delta r)", B, Double.parseDouble(w.group(1)), 1e-3);
            }
            assertTrue(name + " writes BIN WIDTH (delta r)", widths > 0);

            Matcher t = TOTAL.matcher(header);
            assertTrue(name + " writes TOTAL BINS", t.find());
            assertEquals(name, expectedBins, Integer.parseInt(t.group(1)));

            Matcher sn = SHANNON.matcher(header);
            assertTrue(name + " writes SHANNON NUMBER", sn.find());
            assertEquals(name, qmax*dmax/Math.PI, Double.parseDouble(sn.group(1)), 0.006);

            // area of the bins = area of P(r)
            double area = 0;
            for (int k = 0; k < ft.prDistributionForFitting.getItemCount(); k++) {
                area += ft.prDistributionForFitting.getY(k).doubleValue()*B;
            }
            double ref = 0, h = dmax/2000;
            for (int j = 1; j < 2000; j++) ref += ft.calculatePofRAtR(j*h, 1.0)*h;
            assertEquals(name + " bin area vs P(r) area", 1.0, area/ref, 0.03);
        }
    }

    @Test
    public void bsaCaseWritesElevenBins() {
        check(90.5, 0.370884, 11);
    }

    @Test
    public void smrcLikeCaseWritesEightBins() {
        // Shannon number 7.94
        double qmax = 0.30;
        check(7.94*Math.PI/qmax, qmax, 8);
    }

    @Test
    public void svdWithFittedBinsOnlyStillAvailable() {
        boolean old = SVD.MODEL_FITTED_BINS_ONLY;
        try {
            SVD.MODEL_FITTED_BINS_ONLY = true;
            XYSeries[] s = sphere(44, 0.370884);
            SVD svd = new SVD(s[0], s[1], 90.5, 0.370884, false);
            assertEquals(10, svd.prDistributionForFitting.getItemCount());   // opt-in: dmax bin left out
        } finally {
            SVD.MODEL_FITTED_BINS_ONLY = old;
        }
    }
}
