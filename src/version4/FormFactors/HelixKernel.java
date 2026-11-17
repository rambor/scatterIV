package version4.FormFactors;

/**
 * Continuous helix (van der Maarel, J. Appl. Cryst. 56 (2023) 1714), one fitted parameter: the axial length L.
 * Radius, pitch, strands (and their azimuths) and the strand radius are the same for every filament, so the fitted
 * distribution is the length distribution. Form factor: {@link HelixMath}. "Volume" is the mass ~ strands x contour length.
 */
public final class HelixKernel extends TabulatedKernel {

    private static final int NL = 400;
    private final HelixMath.Geometry g;

    public HelixKernel(HelixMath.Geometry geometry, double minL, double maxL) {
        super(minL, maxL, NL);
        this.g = geometry;
    }

    @Override
    void tabulate(double q, double[] lengths, double[] out) {
        if (q <= 0) { java.util.Arrays.fill(out, 1.0); return; }
        double lMax = Math.min(lengths[lengths.length - 1], HelixMath.LIMIT_ABOVE / q);
        HelixMath.AtQ at = new HelixMath.AtQ(g, q, lMax);
        for (int i = 0; i < lengths.length; i++) out[i] = at.formFactor(lengths[i]);
    }

    @Override
    public String label() {
        return String.format("HELIX(r=%.1f, pitch=%.1f, strands=%d, strand a=%.1f)", g.radius, g.pitch, g.strands(), g.strandRadius);
    }

    @Override public double maxEffectiveRadius(double[] upper) { return Math.max(g.radius + g.strandRadius, 1); }

    @Override
    public double volume(double[] u, int off) {
        double area = g.strandRadius > 0 ? Math.PI * g.strandRadius * g.strandRadius : 1.0;
        return g.strands() * g.contourLength(Math.exp(u[off])) * area;
    }
}
