package version4.FormFactors;

import java.util.Arrays;

/**
 * Wormlike chain with excluded volume (SasView flexible_cylinder / WormLikeChainEXV), one parameter: the contour
 * length L. The Kuhn length b (= 2 persistence length) and the cross-section radius are the same for every chain,
 * so the fitted distribution is the contour-length (molecular-weight) distribution.
 *
 * For each data q the model is tabulated on a fine ln L grid spanning the bounds (built on first use) and read with
 * cubic Hermite interpolation; dP/dlnL comes from the same interpolant. "Volume" is the chain mass M ~ L.
 */
public final class WormlikeKernel implements ShapeKernel {

    private static final int NL = 700;
    private final double b, radius, lnLo, lnHi, h, invH;
    private volatile Table table;

    private static final class Table {
        final double[] q;            // the q array the table belongs to (identity + values)
        final double[] p, m;         // [j*NL + i]: P and dP/dlnL
        Table(double[] q, double[] p, double[] m) { this.q = q; this.p = p; this.m = m; }
    }

    public WormlikeKernel(double kuhnLength, double crossSectionRadius, double minL, double maxL) {
        if (!(kuhnLength > 0)) throw new IllegalArgumentException("Kuhn length must be > 0");
        this.b = kuhnLength;
        this.radius = Math.max(0, crossSectionRadius);
        this.lnLo = Math.log(minL) - 0.02;
        this.lnHi = Math.log(maxL) + 0.02;
        this.h = (lnHi - lnLo) / (NL - 1);
        this.invH = 1 / h;
    }

    @Override public int dim() { return 1; }
    @Override public String[] parameterNames() { return new String[]{"L"}; }
    @Override public String label() { return String.format("WORMLIKE_CHAIN(b=%.1f, R=%.1f)", b, radius); }
    @Override public boolean sortParameters() { return false; }
    @Override public int scratchSize() { return 0; }
    @Override public double maxEffectiveRadius(double[] upper) { return Math.max(radius, 1); }
    @Override public double volume(double[] u, int off) { return Math.exp(u[off]); }

    private Table tableFor(double[] q) {
        Table t = table;
        if (t != null && (t.q == q || Arrays.equals(t.q, q))) return t;
        synchronized (this) {
            t = table;
            if (t != null && (t.q == q || Arrays.equals(t.q, q))) return t;
            int nq = q.length;
            double[] p = new double[nq * NL], m = new double[nq * NL];
            for (int j = 0; j < nq; j++) {
                double cs = PolymerMath.crossSection(q[j], radius);
                for (int i = 0; i < NL; i++) p[j * NL + i] = cs * PolymerMath.wormlikeEXV(q[j], Math.exp(lnLo + i * h), b);
                // slopes: centred differences (one-sided at the ends)
                for (int i = 0; i < NL; i++) {
                    int a = Math.max(0, i - 1), c = Math.min(NL - 1, i + 1);
                    m[j * NL + i] = (p[j * NL + c] - p[j * NL + a]) / ((c - a) * h);
                }
            }
            t = new Table(q.clone(), p, m);
            table = t;
            return t;
        }
    }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        Table t = tableFor(q);
        double x = (u[uOff] - lnLo) * invH;
        int i = Math.max(0, Math.min((int) Math.floor(x), NL - 2));
        double f = Math.max(0, Math.min(1, x - i)), f2 = f * f, f3 = f2 * f;
        double h00 = 2 * f3 - 3 * f2 + 1, h10 = f3 - 2 * f2 + f, h01 = -2 * f3 + 3 * f2, h11 = f3 - f2;
        double d00 = (6 * f2 - 6 * f) * invH, d10 = 3 * f2 - 4 * f + 1, d01 = (-6 * f2 + 6 * f) * invH, d11 = 3 * f2 - 2 * f;
        for (int j = 0; j < q.length; j++) {
            int k = j * NL + i;
            double p0 = t.p[k], p1 = t.p[k + 1], m0 = t.m[k], m1 = t.m[k + 1];
            P[pOff + j] = h00 * p0 + h10 * h * m0 + h01 * p1 + h11 * h * m1;
            dP[dOff + j] = d00 * p0 + d10 * m0 + d01 * p1 + d11 * m1;
        }
    }
}
