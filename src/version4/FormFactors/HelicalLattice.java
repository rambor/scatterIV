package version4.FormFactors;

import java.util.Arrays;

/**
 * Orientation-averaged structure factor, per subunit, of identical point subunits on an infinitely long helical
 * lattice, and the orientation-averaged form factor of an ellipsoid of revolution (for the decoupling approximation).
 *
 * Lattice: S strands (azimuths psi_s); along a strand subunit j sits at radius r, angle j * twist, height j * h.
 * The genetic pitch is P = 360 h / twist (h = P / u for u subunits per turn). The Fourier transform of a discrete
 * helix is non-zero on layer lines q_z = 2 pi (n / P + m / h) (n, m integers), with amplitude J_n(q_r r)
 * (Cochran, Crick and Vand 1952); averaging over orientations gives, per subunit,
 *
 *     S(q) = pi S / (q h) * sum_{n, m : |q_z| < q} F_n J_n^2( r (q^2 - q_z^2)^1/2 ),   F_n = |(1/S) sum_s e^{i n psi_s}|^2,
 *
 * which tends to 1 at high q (many layer lines) and to the rod limit pi S / (q h) J0^2(qr) at low q. The m = 0 terms
 * alone are the continuous helix of {@link HelixMath} (eq. 23 of van der Maarel 2023).
 *
 * Decoupling approximation (Kotlarchyk and Chen 1983) for subunits of form factor amplitude F(q, orientation):
 *     I_subunit(q) = <|F|^2> [ 1 + beta(q) (S(q) - 1) ],   beta = |<F>|^2 / <|F|^2>,
 * exact for spheres, approximate for anisotropic subunits whose orientations are correlated with their positions.
 */
public final class HelicalLattice {

    private HelicalLattice() {}

    // ------------------------------------------------------------------ Bessel table

    /**
     * J_n(x) on a uniform x grid (step DX), cubic Hermite with J_n' = (J_{n-1} - J_{n+1}) / 2. Row i holds the orders
     * that matter at x = i DX (n up to about x + 3 x^1/3 + 14; beyond, J_n is negligible and read as 0). Floats keep
     * the table small (~25 MB at x = 500). Grown in large steps; immutable snapshots shared by all threads.
     */
    private static final double DX = 0.05;
    private static volatile float[][] table = new float[0][];

    private static float[][] tableFor(double xMax) {
        float[][] t = table;
        int need = (int) Math.ceil(xMax / DX) + 3;
        if (t.length >= need) return t;
        synchronized (HelicalLattice.class) {
            t = table;
            if (t.length >= need) return t;
            int nx = Math.max(need, 2 * t.length);
            float[][] fresh = Arrays.copyOf(t, nx);
            double[] tmp = new double[HelixMath.besselOrders(nx * DX) + 8];
            for (int i = t.length; i < nx; i++) {
                double x = i * DX;
                int k = HelixMath.besselOrders(x) + 2;
                HelixMath.besselArray(x, k + 1, tmp);
                float[] row = new float[k + 2];
                for (int n = 0; n < row.length; n++) row[n] = (float) tmp[n];
                fresh[i] = row;
            }
            table = fresh;
            return fresh;
        }
    }

    /** builds the table up to x = xMax once (call before parallel loops) */
    public static void prepare(double xMax) {
        tableFor(xMax);
    }

    private static double at(float[] row, int n) {
        return n < row.length ? row[n] : 0.0;
    }

    /** J_n(x) from the table; n >= 0 */
    static double besselJ(int n, double x, float[][] t) {
        double u = x / DX;
        int i = (int) u;
        double f = u - i, f2 = f * f, f3 = f2 * f;
        float[] a = t[i], b = t[i + 1];
        double da = 0.5 * ((n == 0 ? -at(a, 1) : at(a, n - 1)) - at(a, n + 1)) * DX;   // J_{-1} = -J_1
        double db = 0.5 * ((n == 0 ? -at(b, 1) : at(b, n - 1)) - at(b, n + 1)) * DX;
        return (2 * f3 - 3 * f2 + 1) * at(a, n) + (f3 - 2 * f2 + f) * da + (-2 * f3 + 3 * f2) * at(b, n) + (f3 - f2) * db;
    }

    /** per-subunit structure factor S(q) of the infinite lattice (see class comment); P may be negative (handedness) */
    public static double structureFactor(double q, double r, double pitch, double rise, double[] psi) {
        if (q <= 0) return Double.POSITIVE_INFINITY;
        int S = psi.length;
        double invP = 1.0 / pitch, invH = 1.0 / rise, twoPi = 2 * Math.PI;
        int nMax = HelixMath.besselOrders(q * r);
        float[][] t = tableFor(q * r);
        double sum = 0;
        for (int n = -nMax; n <= nMax; n++) {
            double f = strandFactor(n, psi);
            if (f < 1e-14) continue;
            int an = Math.abs(n);
            // m such that |2 pi (n/P + m/h)| < q
            double base = n * invP;
            int mLo = (int) Math.ceil((-q / twoPi - base) * rise - 1e-12);
            int mHi = (int) Math.floor((q / twoPi - base) * rise + 1e-12);
            for (int m = mLo; m <= mHi; m++) {
                double qz = twoPi * (base + m * invH);
                double qr2 = q * q - qz * qz;
                if (qr2 < 0) continue;
                double x = r * Math.sqrt(qr2);
                double jn = besselJ(an, x, t);
                sum += f * jn * jn;
            }
        }
        return Math.PI * S / (q * rise) * sum;
    }

    static double strandFactor(int n, double[] psi) {
        if (n == 0) return 1;
        double c = 0, s = 0;
        for (double a : psi) { c += Math.cos(n * a); s += Math.sin(n * a); }
        return (c * c + s * s) / ((double) psi.length * psi.length);
    }

    /** n strands equally spaced in azimuth */
    public static double[] symmetricStrands(int n) {
        double[] psi = new double[n];
        for (int s = 0; s < n; s++) psi[s] = 2 * Math.PI * s / n;
        return psi;
    }

    // ------------------------------------------------------------------ ellipsoid of revolution

    private static final int NMU = 96;
    private static final double[] MU, WMU;

    static {
        // Gauss-Legendre on [0, 1] from 12 panels of 8 points
        double[] x = {-0.9602898564975363, -0.7966664774136267, -0.5255324099163290, -0.1834346424956498,
                0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};
        double[] w = {0.1012285362903763, 0.2223810344533745, 0.3137066458778873, 0.3626837833783620,
                0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};
        MU = new double[NMU];
        WMU = new double[NMU];
        int panels = NMU / 8;
        for (int p = 0; p < panels; p++)
            for (int i = 0; i < 8; i++) {
                MU[p * 8 + i] = (p + 0.5 * (x[i] + 1)) / panels;
                WMU[p * 8 + i] = 0.5 * w[i] / panels;
            }
    }

    /**
     * {<|F|^2>, |<F>|^2} of a homogeneous ellipsoid with semi-axes (a, a, c), F normalised to 1 at q = 0,
     * averaged over orientations
     */
    public static double[] ellipsoid(double q, double a, double c) {
        double f1 = 0, f2 = 0;
        for (int i = 0; i < NMU; i++) {
            double mu = MU[i];
            double R = Math.sqrt(a * a * (1 - mu * mu) + c * c * mu * mu);
            double F = FormFactorTable.amplitude(q * R);
            f1 += WMU[i] * F;
            f2 += WMU[i] * F * F;
        }
        return new double[]{f2, f1 * f1};
    }

    public static double ellipsoidVolume(double a, double c) {
        return 4.0 / 3.0 * Math.PI * a * a * c;
    }
}
