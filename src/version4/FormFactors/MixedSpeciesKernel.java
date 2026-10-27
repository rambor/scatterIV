package version4.FormFactors;

/**
 * Two species that share ONE geometry distribution (e.g. empty and filled capsids: same shell, contents independent
 * of size). Each particle u is an incoherent number mixture: a fraction p of particles with this geometry is species 0
 * (e.g. empty), 1 - p species 1 (filled):
 *
 *   raw intensity    I(q; u) = p * S0^2 P0(q; u) + (1 - p) * S1^2 P1(q; u)     (Pk normalised, Sk^2 = power of species k)
 *   normalised       P(q; u) = I(q; u) / W(u),   W(u) = p S0^2 + (1 - p) S1^2   (= forward-scattering power)
 *
 * p is a fixed global parameter here; SpeciesProfile scans it. Because both species share u, the number (and volume)
 * fraction of species 0 is exactly p, and the geometry cannot trade size against filling.
 *
 * dP/du = [w0 dP0 + w1 dP1 + (P0 - P) dw0 + (P1 - P) dw1] / W,  w0 = p S0^2, w1 = (1 - p) S1^2,
 * dw_k / du = w_k dln S_k^2 / du (central differences of the closed-form power; cheap).
 */
public final class MixedSpeciesKernel implements ShapeKernel {

    private final ShapeKernel base;   // must provide 2 species
    private final double p;

    public MixedSpeciesKernel(ShapeKernel base, double fractionSpecies0) {
        if (base.speciesCount() != 2) throw new IllegalArgumentException("shared-geometry mixture needs exactly 2 species");
        if (!(fractionSpecies0 >= 0 && fractionSpecies0 <= 1)) throw new IllegalArgumentException("species fraction must be in [0,1]");
        this.base = base;
        this.p = fractionSpecies0;
    }

    public double fraction() { return p; }
    public ShapeKernel base() { return base; }

    @Override public int dim() { return base.dim(); }
    @Override public String[] parameterNames() { return base.parameterNames(); }
    @Override public String label() { return base.label() + String.format("_MIX(%s=%.3f)", base.speciesName(0), p); }
    @Override public boolean sortParameters() { return base.sortParameters(); }
    @Override public int scratchSize() { return base.scratchSize(); }
    @Override public double volume(double[] u, int off) { return base.volume(u, off); }
    @Override public double maxEffectiveRadius(double[] upper) { return base.maxEffectiveRadius(upper); }

    @Override
    public double forwardScatteringPower(double[] u, int off) {
        return p * base.forwardScatteringPower(0, u, off) + (1 - p) * base.forwardScatteringPower(1, u, off);
    }

    // per-thread buffers for the two species curves
    private final ThreadLocal<double[][]> buf = new ThreadLocal<>();

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        final int nq = q.length, d = base.dim();
        double[][] b = buf.get();
        if (b == null || b[0].length != nq) {
            b = new double[][]{new double[nq], new double[nq], new double[d * nq], new double[d * nq]};
            buf.set(b);
        }
        double[] P0 = b[0], P1 = b[1], D0 = b[2], D1 = b[3];
        base.evaluate(0, u, uOff, q, P0, 0, D0, 0, scratch);
        base.evaluate(1, u, uOff, q, P1, 0, D1, 0, scratch);

        double w0 = p * base.forwardScatteringPower(0, u, uOff);
        double w1 = (1 - p) * base.forwardScatteringPower(1, u, uOff);
        double W = w0 + w1;
        // dln S_k^2 / du by central differences of the closed-form power
        double[] dl0 = new double[d], dl1 = new double[d];
        double h = 1e-5;
        double[] uu = new double[d];
        for (int k = 0; k < d; k++) uu[k] = u[uOff + k];
        for (int k = 0; k < d; k++) {
            double keep = uu[k];
            uu[k] = keep + h;
            double a0 = Math.log(base.forwardScatteringPower(0, uu, 0)), a1 = Math.log(base.forwardScatteringPower(1, uu, 0));
            uu[k] = keep - h;
            dl0[k] = (a0 - Math.log(base.forwardScatteringPower(0, uu, 0))) / (2 * h);
            dl1[k] = (a1 - Math.log(base.forwardScatteringPower(1, uu, 0))) / (2 * h);
            uu[k] = keep;
        }
        for (int j = 0; j < nq; j++) {
            double Pm = (w0 * P0[j] + w1 * P1[j]) / W;
            P[pOff + j] = Pm;
            for (int k = 0; k < d; k++) {
                double dw0 = w0 * dl0[k], dw1 = w1 * dl1[k];
                dP[dOff + k * nq + j] = (w0 * D0[k * nq + j] + w1 * D1[k * nq + j] + (P0[j] - Pm) * dw0 + (P1[j] - Pm) * dw1) / W;
            }
        }
    }
}
