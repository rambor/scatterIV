package version4.FormFactors;

/**
 * Spheroid (ellipsoid of revolution) with semi-axes (a, a, c).
 *   c > a : prolate,   c < a : oblate,   c = a : sphere.
 * One family covers both prolate and oblate, so the fit decides the aspect ratio
 * instead of the user choosing PROLATE/OBLATE up front.
 *
 *   P(q) = < F(q r(mu))^2 >_mu  (P(0) = 1),  r(mu)^2 = a^2 (1 - mu^2) + c^2 mu^2 ,  mu in [0,1]
 *   V = 4/3 pi a^2 c   (used only for re-weighting)
 *
 * Log-derivatives (u_a = ln a, u_c = ln c):
 *   dP/du_a = < 2 F G a^2 (1-mu^2) / r^2 >
 *   dP/du_c = < 2 F G c^2 mu^2      / r^2 >
 *
 * The mu-average uses Gauss-Legendre with a node count chosen per particle from q_max |c - a|,
 * so near-spherical particles are cheap and very anisotropic ones stay accurate.
 */
public final class SpheroidKernel implements ShapeKernel {

    private static final double FOUR_PI_3 = 4.0 * Math.PI / 3.0;
    private final FormFactorTable table;
    private final double qmax;
    private final int minNodes, maxNodes;

    public SpheroidKernel(FormFactorTable table, double qmax) {
        this(table, qmax, 8, 64);
    }

    public SpheroidKernel(FormFactorTable table, double qmax, int minNodes, int maxNodes) {
        this.table = table;
        this.qmax = qmax;
        this.minNodes = minNodes;
        this.maxNodes = Math.min(maxNodes, GaussLegendre.MAX_N);
    }

    @Override public int dim() { return 2; }
    @Override public String[] parameterNames() { return new String[]{"R_eq(a)", "R_polar(c)"}; }
    @Override public String label() { return "SPHEROID"; }
    @Override public boolean sortParameters() { return false; }
    @Override public int scratchSize() { return 3 * maxNodes; }

    @Override
    public double volume(double[] u, int off) {
        double a = Math.exp(u[off]), c = Math.exp(u[off + 1]);
        return FOUR_PI_3 * a * a * c;
    }

    @Override
    public double maxEffectiveRadius(double[] upper) { return Math.max(upper[0], upper[1]); }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] s) {
        final double a = Math.exp(u[uOff]);
        final double c = Math.exp(u[uOff + 1]);
        final double a2 = a * a, c2 = c * c;
        final int n = GaussLegendre.nodesFor(qmax * Math.abs(c - a), minNodes, maxNodes);
        final double[] mu = GaussLegendre.nodes(n);
        final double[] w = GaussLegendre.weights(n);

        // s[0..n) = r ; s[n..2n) = a^2(1-mu^2)/r^2 ; s[2n..3n) = c^2 mu^2 / r^2
        for (int m = 0; m < n; m++) {
            double mu2 = mu[m] * mu[m];
            double ta = a2 * (1.0 - mu2), tc = c2 * mu2;
            double r2 = ta + tc;
            s[m] = Math.sqrt(r2);
            s[n + m] = ta / r2;
            s[2 * n + m] = tc / r2;
        }

        final double[] tab = table.tab;
        final double invH = table.invH;
        final int last = table.last;
        final int nq = q.length;

        for (int j = 0; j < nq; j++) {
            final double qj = q[j];
            double acc = 0, da = 0, dc = 0;
            for (int m = 0; m < n; m++) {
                double x = qj * s[m];
                double F, G;
                double t = x * invH;
                int i = (int) t;
                if (i < last) {
                    double fr = t - i;
                    int k = i << 1;
                    F = tab[k] + fr * (tab[k + 2] - tab[k]);
                    G = tab[k + 1] + fr * (tab[k + 3] - tab[k + 1]);
                } else {
                    F = FormFactorTable.amplitude(x);
                    G = FormFactorTable.xDerivative(x);
                }
                double wm = w[m];
                acc += wm * F * F;
                double wfg = 2.0 * wm * F * G;
                da += wfg * s[n + m];
                dc += wfg * s[2 * n + m];
            }
            P[pOff + j] = acc;
            dP[dOff + j] = da;
            dP[dOff + nq + j] = dc;
        }
    }
}
