package version4.FormFactors;

/**
 * Homogeneous sphere, one parameter R, radius.
 *   P(q) = F(qR)^2   (normalised, P(0) = 1),  V = 4/3 pi R^3
 *   dP/dlnR = 2 F G   with G = x F'(x), x = qR
 */
public final class SphereKernel implements ShapeKernel {

    private static final double FOUR_PI_3 = 4.0 * Math.PI / 3.0;
    private final FormFactorTable table;

    public SphereKernel(FormFactorTable table) { this.table = table; }

    @Override public int dim() { return 1; }
    @Override public String[] parameterNames() { return new String[]{"R"}; }
    @Override public String label() { return "SPHERE"; }
    @Override public boolean sortParameters() { return false; }
    @Override public int scratchSize() { return 0; }

    @Override
    public double volume(double[] u, int off) {
        double r = Math.exp(u[off]);
        return FOUR_PI_3 * r * r * r;
    }

    @Override
    public double maxEffectiveRadius(double[] upper) { return upper[0]; }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        final double R = Math.exp(u[uOff]);
        final double[] tab = table.tab;
        final double invH = table.invH;
        final int last = table.last;
        final int nq = q.length;

        for (int j = 0; j < nq; j++) {
            double x = q[j] * R;
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
            double ff = F * F;
            P[pOff + j] = ff;
            dP[dOff + j] = 2.0 * F * G;
        }
    }
}
