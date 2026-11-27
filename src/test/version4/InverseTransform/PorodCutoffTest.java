package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The P(r) Porod volume integrates the model I(q) to qmax by default. For a long cylinder the old
 * min(qmax, 8/Rg) cut-off over-estimates V several-fold; integrating to qmax keeps it near the
 * exact truncated-Q value.
 */
public class PorodCutoffTest {

    @After
    public void reset() { IndirectFT.POROD_CUTOFF_QRG = 0; }

    /** orientationally averaged |F|^2 of a uniform cylinder (radius R, length L), F(0) = 1 */
    static double cylinder(double q, double R, double L) {
        int n = 400;
        double s = 0;
        for (int i = 0; i < n; i++) {
            double mu = (i + 0.5)/n;
            double a = q*R*Math.sqrt(1 - mu*mu), b = 0.5*q*L*mu;
            double fa = (a < 1e-8) ? 1 : 2*besselJ1(a)/a;
            double fb = (b < 1e-8) ? 1 : Math.sin(b)/b;
            s += fa*fa*fb*fb;
        }
        return s/n;
    }

    /** J1 by series / asymptotic form, adequate for this test */
    static double besselJ1(double x) {
        if (x < 12) {
            double term = x/2, sum = term;
            for (int k = 1; k < 60; k++) { term *= -(x*x/4)/(k*(k + 1.0)); sum += term; }
            return sum;
        }
        double w = x - 0.75*Math.PI;
        return Math.sqrt(2/(Math.PI*x))*(Math.cos(w) + 0.375/x*Math.sin(w));
    }

    private static XYSeries[] data(double R, double L, double qmax) {
        XYSeries d = new XYSeries("d"), e = new XYSeries("e");
        int n = 400;
        for (int i = 0; i < n; i++) {
            double q = 0.004 + (qmax - 0.004)*i/(n - 1);
            double iq = 1000*cylinder(q, R, L);
            d.add(q, q*iq);
            e.add(q, 0.01*q*iq + 1e-6);
        }
        return new XYSeries[]{d, e};
    }

    @Test
    public void defaultCutoffIsQmax() {
        XYSeries[] s = data(10, 200, 0.50);
        IndirectFT ft = new MooreTransformApache(s[0], s[1], 205, 0.50, 1e-4, false);
        assertEquals(0.50, ft.defaultPorodCutoff(), 1e-9);
        IndirectFT.POROD_CUTOFF_QRG = 8;
        assertEquals(8/ft.getRg(), ft.defaultPorodCutoff(), 1e-9);
    }

    @Test
    public void elongatedCylinderNoLongerBlowsUp() {
        double R = 10, L = 200, qmax = 0.50, V = Math.PI*R*R*L;
        XYSeries[] s = data(R, L, qmax);
        IndirectFT ft = new DirectSineIntegralTransform(s[0], s[1], 1.02*Math.sqrt(4*R*R + L*L), qmax, 1e-4, false);
        double atQmax = ft.getPorodVolume().value/V;
        IndirectFT.POROD_CUTOFF_QRG = 8;
        double at8Rg = ft.getPorodVolume().value/V;
        // exact truncated-Q values for this cylinder: 1.18 at q 0.50, 2.80 at 8/Rg
        assertEquals("V_p/V integrated to qmax", 1.18, atQmax, 0.06);
        assertEquals("V_p/V at 8/Rg", 2.80, at8Rg, 0.2);
    }
}
