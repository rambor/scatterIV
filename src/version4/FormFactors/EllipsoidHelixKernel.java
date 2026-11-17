package version4.FormFactors;

/**
 * Infinitely long helix of identical ellipsoidal subunits (decoupling approximation), one fitted parameter: the
 * radius r of the subunit centres. Pitch P, subunits per turn u (rise h = P / u), strands and the ellipsoid
 * semi-axes (a, a, c) are the same for every filament.
 *
 * Per subunit: I_sub(q) = <|F|^2> + |<F>|^2 (S(q; r) - 1) with S the infinite-lattice structure factor of
 * {@link HelicalLattice}. At low and intermediate q the lattice (tube cross-section, layer lines) dominates; at high
 * q S -> 1 and the subunit form factor remains. Per nominal filament length L_ref with N = strands L_ref / h subunits:
 * P(q; r) = I_sub(q) / N (the same low-q scale as {@link InfiniteHelixKernel}); "volume" = N x subunit volume, i.e.
 * volume weighting is mass weighting.
 */
public final class EllipsoidHelixKernel extends TabulatedKernel {

    private static final int NL = 240;
    private final double pitch, perTurn, rise, a, c;
    private final double[] psi;
    private final double nPerLref;

    public EllipsoidHelixKernel(double pitch, double subunitsPerTurn, int strands, double strandAngleDeg,
                                double semiA, double semiC, double minR, double maxR) {
        super(minR, maxR, NL);
        if (!(pitch > 0)) throw new IllegalArgumentException("pitch must be > 0");
        if (!(subunitsPerTurn > 0)) throw new IllegalArgumentException("subunits per turn must be > 0");
        if (!(semiA > 0 && semiC > 0)) throw new IllegalArgumentException("ellipsoid semi-axes must be > 0");
        if (strands < 1 || strands > 24) throw new IllegalArgumentException("strands must be 1-24");
        this.pitch = pitch;
        this.perTurn = subunitsPerTurn;
        this.rise = pitch / subunitsPerTurn;
        this.a = semiA;
        this.c = semiC;
        this.psi = strands == 2 && strandAngleDeg > 0 && strandAngleDeg < 360 && strandAngleDeg != 180
                ? new double[]{0, Math.toRadians(strandAngleDeg)} : HelicalLattice.symmetricStrands(strands);
        this.nPerLref = strands * InfiniteHelixKernel.L_REF / rise;
    }

    @Override public String[] parameterNames() { return new String[]{"r"}; }

    @Override
    public String label() {
        return String.format("HELIX_ELLIPSOIDS(pitch=%.1f, %.2f per turn, strands=%d, a=%.1f, c=%.1f)", pitch, perTurn, psi.length, a, c);
    }

    @Override public double maxEffectiveRadius(double[] upper) { return Math.max(upper[0] + Math.max(a, c), 1); }

    @Override
    public double volume(double[] u, int off) {
        return nPerLref * HelicalLattice.ellipsoidVolume(a, c);
    }

    @Override
    void tabulate(double q, double[] radii, double[] out) {
        HelicalLattice.prepare(q * radii[radii.length - 1]);
        double[] f = HelicalLattice.ellipsoid(q, a, c);
        for (int i = 0; i < radii.length; i++) {
            double s = HelicalLattice.structureFactor(q, radii[i], pitch, rise, psi);
            out[i] = (f[0] + f[1] * (s - 1)) / nPerLref;
        }
    }

    /** P(q; r) per nominal length L_ref (for tests and the estimator) */
    public double formFactor(double q, double r) {
        double[] f = HelicalLattice.ellipsoid(q, a, c);
        return (f[0] + f[1] * (HelicalLattice.structureFactor(q, r, pitch, rise, psi) - 1)) / nPerLref;
    }
}
