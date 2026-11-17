package version4.FormFactors;

/**
 * Helical filament of identical spherical subunits, one fitted parameter: the filament length L.
 *
 * Geometry (the same for every filament):
 *   S strands (1 = single-start helix), related by rotations of 2 pi / S about the axis;
 *   on each strand subunit i sits at angle i * twist and height i * rise, at radius R from the axis;
 *   subunits are homogeneous spheres of radius a (a = 0: point subunits).
 * Examples: F-actin as the 1-start genetic helix (rise 27.5 A, twist -166.7 deg); a cross-beta fibril of two
 * protofilaments (S = 2, rise 4.8 A per strand, small twist).
 *
 * The orientation average is exact (Debye). Distances depend only on the index difference k and the strand offset
 * ds, d(k, ds)^2 = (2 R sin((k twist + 2 pi ds / S) / 2))^2 + (k rise)^2, so for N subunits per strand
 *
 *     P(q, N) = F_a(q)^2 * S * sum_ds sum_{|k|<N} (N - |k|) sinc(q d(k, ds)) / (S N)^2,
 *
 * and with prefix sums over k all N up to the maximum come out in one pass per q. N = L / rise is used as a
 * continuous variable (linear interpolation between whole numbers of subunits). Handedness does not change P(q).
 * In the limit of small rise this is the continuous helix of {@link HelixMath} (checked in the tests).
 */
public final class SubunitHelixKernel extends TabulatedKernel {

    private static final int NL = 400;
    private final double radius, rise, twist, subunitRadius;
    private final int strands, nMax;

    /**
     * @param helixRadius   radius of the subunit centres from the filament axis (A), >= 0
     * @param rise          axial rise per subunit along one strand (A), > 0
     * @param twistDegrees  rotation per subunit along one strand (degrees)
     * @param strands       number of strands (helix starts), 1-24
     * @param subunitRadius radius of the spherical subunit (A), >= 0
     */
    public SubunitHelixKernel(double helixRadius, double rise, double twistDegrees, int strands, double subunitRadius,
                              double minL, double maxL) {
        super(minL, maxL, NL);
        if (!(rise > 0)) throw new IllegalArgumentException("rise per subunit must be > 0");
        if (!(helixRadius >= 0)) throw new IllegalArgumentException("helix radius must be >= 0");
        if (strands < 1 || strands > 24) throw new IllegalArgumentException("strands must be 1-24");
        this.radius = helixRadius;
        this.rise = rise;
        this.twist = Math.toRadians(twistDegrees);
        this.strands = strands;
        this.subunitRadius = Math.max(0, subunitRadius);
        this.nMax = (int) Math.ceil(Math.exp(lnHi) / rise) + 2;
        if (nMax > 2_000_000) throw new IllegalArgumentException("L max / rise is too large (" + nMax + " subunits)");
    }

    @Override
    public String label() {
        return String.format("HELIX_SUBUNITS(R=%.1f, rise=%.2f, twist=%.2f, strands=%d, a=%.1f)",
                radius, rise, Math.toDegrees(twist), strands, subunitRadius);
    }

    @Override public double maxEffectiveRadius(double[] upper) { return Math.max(radius + subunitRadius, 1); }

    /** mass ~ number of subunits = S L / rise (times the subunit volume) */
    @Override
    public double volume(double[] u, int off) {
        double n = Math.max(1.0, Math.exp(u[off]) / rise) * strands;
        double v = subunitRadius > 0 ? 4.0 / 3.0 * Math.PI * subunitRadius * subunitRadius * subunitRadius : 1.0;
        return n * v;
    }

    @Override
    void tabulate(double q, double[] lengths, double[] out) {
        double[] pn = perSubunitCount(q);
        for (int i = 0; i < lengths.length; i++) out[i] = interpolate(pn, lengths[i] / rise);
    }

    /** normalised P(q) for a filament of n (>= 1, possibly fractional) subunits per strand */
    public double formFactor(double q, double n) {
        return interpolate(perSubunitCount(q), n);
    }

    /** P(q, N) for N = 1 .. nMax (index N), including the subunit form factor */
    private double[] perSubunitCount(double q) {
        int S = strands;
        // T(k) = sum over strand offsets of sinc(q d(k, ds)); symmetric in k
        double[] T = new double[nMax];
        for (int k = 0; k < nMax; k++) {
            double zk = k * rise, sum = 0;
            for (int ds = 0; ds < S; ds++) {
                double theta = k * twist + 2 * Math.PI * ds / S;
                double chord = 2 * radius * Math.sin(0.5 * theta);
                double x = q * Math.sqrt(chord * chord + zk * zk);
                sum += x < 1e-6 ? 1.0 - x * x / 6.0 : Math.sin(x) / x;
            }
            T[k] = sum;
        }
        double fa = subunitRadius > 0 ? FormFactorTable.amplitude(q * subunitRadius) : 1.0;
        double fa2 = fa * fa;
        double[] p = new double[nMax + 1];
        double A = 0, B = 0;              // sum_{k=1}^{N-1} T(k) and sum k T(k)
        for (int n = 1; n <= nMax; n++) {
            if (n >= 2) { A += T[n - 1]; B += (n - 1) * T[n - 1]; }
            double pairSum = S * (n * T[0] + 2 * (n * A - B));
            p[n] = fa2 * pairSum / ((double) S * S * n * n);
        }
        p[0] = p[1];
        return p;
    }

    private double interpolate(double[] p, double n) {
        if (n <= 1) return p[1];
        if (n >= nMax) return p[nMax];
        int i = (int) Math.floor(n);
        double f = n - i;
        return p[i] + f * (p[i + 1] - p[i]);
    }
}
