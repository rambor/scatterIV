package version4.FormFactors;

/**
 * Flexible polymer chain, one parameter Rg. nu = 1/2: Gaussian chain (Debye); otherwise the excluded-volume chain
 * of Hammouda (SasView polymer_excl_volume with porod_exp = 1/nu). nu is the same for every chain, so the fitted
 * distribution is a distribution of chain sizes (molecular weights) at fixed solvent quality.
 *
 * P(U) is tabulated once on a fine ln U grid (cubic Hermite with exact slopes), U = c(nu) (q Rg)^2.
 * "Volume" is the chain mass M ~ Rg^(1/nu): volume-weighted distributions are mass-weighted, number-weighted are
 * per chain.
 */
public final class ChainKernel implements ShapeKernel {

    private static final double LN_UMIN = Math.log(1e-6), LN_UMAX = Math.log(1e8);
    private static final int NG = 4000;

    private final double nu, c, invNu;
    private final double[] p = new double[NG], m = new double[NG];   // P and dP/dlnU on the grid
    private final double h, invH;
    private final double s1, s2, g1, g2;                                // asymptote above U_max

    public ChainKernel(double nu) {
        if (!(nu > 0.26 && nu < 1.0)) throw new IllegalArgumentException("Flory exponent must be between 0.26 and 1");
        this.nu = nu;
        this.c = PolymerMath.evFactor(nu);
        this.invNu = 1 / nu;
        this.h = (LN_UMAX - LN_UMIN) / (NG - 1);
        this.invH = 1 / h;
        for (int i = 0; i < NG; i++) {
            double U = Math.exp(LN_UMIN + i * h);
            p[i] = PolymerMath.evChain(U, nu);
            m[i] = PolymerMath.evChainLogDeriv(U, nu);
        }
        s1 = 1 / (2 * nu);
        s2 = 1 / nu;
        g1 = Math.exp(PolymerMath.lnGamma(s1));
        g2 = Math.exp(PolymerMath.lnGamma(s2));
    }

    public double nu() { return nu; }

    @Override public int dim() { return 1; }
    @Override public String[] parameterNames() { return new String[]{"Rg"}; }
    @Override public String label() { return Math.abs(nu - 0.5) < 1e-9 ? "GAUSSIAN_CHAIN" : String.format("EXCLUDED_VOLUME_CHAIN(nu=%.3f)", nu); }
    @Override public boolean sortParameters() { return false; }
    @Override public int scratchSize() { return 0; }
    @Override public double maxEffectiveRadius(double[] upper) { return upper[0]; }

    /** chain mass in arbitrary units, M ~ Rg^(1/nu) */
    @Override public double volume(double[] u, int off) { return Math.exp(u[off] * invNu); }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        double rg2 = Math.exp(2 * u[uOff]);
        for (int j = 0; j < q.length; j++) {
            double U = c * q[j] * q[j] * rg2;
            double val, lg;
            if (U <= 1e-6) {
                double x = q[j] * q[j] * rg2;         // (q Rg)^2: P = 1 - x/3 + ...
                val = 1 - x / 3;
                lg = -x / 3;                          // dP/dlnU
            } else if (U >= 1e8) {
                double a = g1 * Math.pow(U, -s1), b = g2 * Math.pow(U, -s2);
                val = (a - b) / nu;
                lg = (-s1 * a + s2 * b) / nu;
            } else {
                double t = (Math.log(U) - LN_UMIN) * invH;
                int i = Math.min((int) t, NG - 2);
                double f = t - i, f2 = f * f, f3 = f2 * f;
                double h00 = 2 * f3 - 3 * f2 + 1, h10 = f3 - 2 * f2 + f, h01 = -2 * f3 + 3 * f2, h11 = f3 - f2;
                val = h00 * p[i] + h10 * h * m[i] + h01 * p[i + 1] + h11 * h * m[i + 1];
                lg = m[i] + f * (m[i + 1] - m[i]);
            }
            P[pOff + j] = val;
            dP[dOff + j] = 2 * lg;                    // dP/dlnRg = 2 dP/dlnU
        }
    }
}
