package version4.FormFactors;

/**
 * Infinitely long continuous helices (van der Maarel, J. Appl. Cryst. 56 (2023) 1714, limit qL >> 1), one fitted
 * parameter: the helix radius r. For filaments longer than the measurement resolves (L >~ 5 / q_min) the length only
 * scales the intensity; the shape of the curve comes from the cross-section, so the fitted distribution is the
 * radius distribution (e.g. different constriction states of dynamin-like assemblies).
 *
 * Per unit length (eq. 23 without the end correction, which vanishes as L -> infinity):
 *
 *     P(q; r) = pi / (q L_ref) [ J0^2(qr) + 2 sum_{k <= pq} F_k Jk^2( r (q^2 - (k/p)^2)^1/2 ) ] exp(-q^2 a^2 / 4)
 *
 * L_ref is a fixed nominal length, so every radius gets the same low-q intensity per particle ("intensity-weighted"),
 * as the fitter expects. Pitch, strands (and the double-helix strand angle) and the strand radius a are the same for
 * every filament. "Volume" = mass per unit length x L_ref, i.e. volume weighting is mass weighting.
 */
public final class InfiniteHelixKernel extends TabulatedKernel {

    /** nominal filament length (A): a common scale only */
    static final double L_REF = 1000.0;
    private static final int NL = 400;
    private final double pitch, strandRadius, strandAngleDeg;
    private final int strands;

    public InfiniteHelixKernel(double pitch, int strands, double strandAngleDegrees, double strandRadius,
                               double minR, double maxR) {
        super(minR, maxR, NL);
        if (!(pitch > 0)) throw new IllegalArgumentException("pitch must be > 0");
        if (strands < 1 || strands > 24) throw new IllegalArgumentException("strands must be 1-24");
        this.pitch = pitch;
        this.strands = strands;
        this.strandAngleDeg = strandAngleDegrees;
        this.strandRadius = Math.max(0, strandRadius);
    }

    private HelixMath.Geometry geometry(double r) {
        return strands == 2 && strandAngleDeg > 0 && strandAngleDeg < 360 && strandAngleDeg != 180
                ? HelixMath.Geometry.doubleHelix(r, pitch, strandRadius, Math.toRadians(strandAngleDeg))
                : HelixMath.Geometry.symmetric(r, pitch, strandRadius, strands);
    }

    @Override public String[] parameterNames() { return new String[]{"r"}; }

    @Override
    public String label() {
        return String.format("HELIX_INFINITE(pitch=%.1f, strands=%d%s, strand a=%.1f)", pitch, strands,
                strands == 2 && strandAngleDeg != 180 ? String.format(", phi=%.0f", strandAngleDeg) : "", strandRadius);
    }

    @Override
    public double maxEffectiveRadius(double[] upper) { return Math.max(upper[0] + strandRadius, 1); }

    @Override
    public double volume(double[] u, int off) {
        double r = Math.exp(u[off]), p = pitch / (2 * Math.PI);
        double area = strandRadius > 0 ? Math.PI * strandRadius * strandRadius : 1.0;
        return strands * Math.sqrt(p * p + r * r) / p * area * L_REF;
    }

    @Override
    void tabulate(double q, double[] radii, double[] out) {
        for (int i = 0; i < radii.length; i++) out[i] = formFactor(q, radii[i]);
    }

    /** P(q; r) per nominal length L_ref */
    public double formFactor(double q, double r) {
        if (q <= 0) return Math.PI / (1e-12 * L_REF);
        HelixMath.Geometry g = geometry(r);
        return Math.PI / (q * L_REF) * HelixMath.limitSum(g, q) * g.crossSection(q);
    }
}
