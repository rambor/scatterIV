package version4.FormFactors;

/**
 * Shared, read-only lookup table for the homogeneous-sphere amplitude
 *
 *     F(x)  = 3 (sin x - x cos x) / x^3          (F(0) = 1)
 *     G(x)  = x F'(x) = 3 [ (x^2 - 3) sin x + 3 x cos x ] / x^3
 *
 * Every shape used here (sphere, spheroid, triaxial ellipsoid) is an orientational
 * average of F(q r_eff)^2, and every log-parameter derivative only needs F and x F'(x).
 *
 * One table is built per fit and shared by all threads (it is immutable after construction).
 * Values are interleaved, tab[2i] = F(i h), tab[2i+1] = G(i h), so a lookup touches one cache line.
 * Linear interpolation with h = 1e-3 gives absolute error below ~1e-7 (F(0) = 1).
 */
public final class FormFactorTable {

    final double[] tab;
    final double invH;
    final int last;          // last index i for which (i, i+1) are both valid
    private final double xMax;

    public FormFactorTable(double xMax, double h) {
        this.xMax = xMax;
        int n = (int) Math.ceil(xMax / h) + 2;
        this.tab = new double[2 * n];
        this.invH = 1.0 / h;
        this.last = n - 2;
        for (int i = 0; i < n; i++) {
            double x = i * h;
            tab[2 * i] = amplitude(x);
            tab[2 * i + 1] = xDerivative(x);
        }
    }

    public double getXMax() { return xMax; }

    /** exact F(x), series near zero */
    public static double amplitude(double x) {
        if (x < 0.05) {
            double x2 = x * x;
            return 1.0 - x2 / 10.0 + x2 * x2 / 280.0 - x2 * x2 * x2 / 15120.0;
        }
        return 3.0 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
    }

    /** exact G(x) = x F'(x), series near zero */
    public static double xDerivative(double x) {
        if (x < 0.05) {
            double x2 = x * x;
            return -x2 / 5.0 + x2 * x2 / 70.0 - x2 * x2 * x2 / 2520.0;
        }
        double s = Math.sin(x), c = Math.cos(x);
        return 3.0 * ((x * x - 3.0) * s + 3.0 * x * c) / (x * x * x);
    }

    /** interpolated F (used by tests; kernels inline the lookup) */
    public double F(double x) {
        double t = x * invH;
        int i = (int) t;
        if (i >= last) return amplitude(x);
        double fr = t - i;
        int k = i << 1;
        return tab[k] + fr * (tab[k + 2] - tab[k]);
    }

    /** interpolated G (used by tests; kernels inline the lookup) */
    public double G(double x) {
        double t = x * invH;
        int i = (int) t;
        if (i >= last) return xDerivative(x);
        double fr = t - i;
        int k = (i << 1) + 1;
        return tab[k] + fr * (tab[k + 2] - tab[k]);
    }
}
