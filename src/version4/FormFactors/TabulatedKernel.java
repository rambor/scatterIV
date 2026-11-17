package version4.FormFactors;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Base for one-parameter kernels (a filament length L, a helix radius r, ...): for each data q the form factor is
 * tabulated on a fine grid in the log of the parameter spanning the bounds (built once per q array, in parallel over
 * q) and read with cubic Hermite interpolation; the derivative comes from the same interpolant. Same scheme as
 * {@link WormlikeKernel}.
 */
abstract class TabulatedKernel implements ShapeKernel {

    private final int nl;
    final double lnLo, lnHi, h, invH;
    private volatile Table table;

    private static final class Table {
        final double[] q;
        final double[] p, m;         // [j*nl + i]: P and dP/dlnL
        Table(double[] q, double[] p, double[] m) { this.q = q; this.p = p; this.m = m; }
    }

    TabulatedKernel(double minL, double maxL, int gridPoints) {
        if (!(minL > 0 && maxL > minL)) throw new IllegalArgumentException("bad parameter bounds");
        this.nl = gridPoints;
        this.lnLo = Math.log(minL) - 0.02;
        this.lnHi = Math.log(maxL) + 0.02;
        this.h = (lnHi - lnLo) / (nl - 1);
        this.invH = 1 / h;
    }

    /** P(q, x) for every parameter value x in lengths (ascending) */
    abstract void tabulate(double q, double[] lengths, double[] out);

    @Override public final int dim() { return 1; }
    @Override public String[] parameterNames() { return new String[]{"L"}; }
    @Override public final boolean sortParameters() { return false; }
    @Override public final int scratchSize() { return 0; }

    private Table tableFor(double[] q) {
        Table t = table;
        if (t != null && (t.q == q || Arrays.equals(t.q, q))) return t;
        synchronized (this) {
            t = table;
            if (t != null && (t.q == q || Arrays.equals(t.q, q))) return t;
            int nq = q.length;
            double[] lengths = new double[nl];
            for (int i = 0; i < nl; i++) lengths[i] = Math.exp(lnLo + i * h);
            double[] p = new double[nq * nl], m = new double[nq * nl];
            IntStream.range(0, nq).parallel().forEach(j -> {
                double[] row = new double[nl];
                tabulate(q[j], lengths, row);
                System.arraycopy(row, 0, p, j * nl, nl);
                for (int i = 0; i < nl; i++) {
                    int a = Math.max(0, i - 1), c = Math.min(nl - 1, i + 1);
                    m[j * nl + i] = (row[c] - row[a]) / ((c - a) * h);
                }
            });
            t = new Table(q.clone(), p, m);
            table = t;
            return t;
        }
    }

    @Override
    public final void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        Table t = tableFor(q);
        double x = (u[uOff] - lnLo) * invH;
        int i = Math.max(0, Math.min((int) Math.floor(x), nl - 2));
        double f = Math.max(0, Math.min(1, x - i)), f2 = f * f, f3 = f2 * f;
        double h00 = 2 * f3 - 3 * f2 + 1, h10 = f3 - 2 * f2 + f, h01 = -2 * f3 + 3 * f2, h11 = f3 - f2;
        double d00 = (6 * f2 - 6 * f) * invH, d10 = 3 * f2 - 4 * f + 1, d01 = (-6 * f2 + 6 * f) * invH, d11 = 3 * f2 - 2 * f;
        for (int j = 0; j < q.length; j++) {
            int k = j * nl + i;
            double p0 = t.p[k], p1 = t.p[k + 1], m0 = t.m[k], m1 = t.m[k + 1];
            P[pOff + j] = h00 * p0 + h10 * h * m0 + h01 * p1 + h11 * h * m1;
            dP[dOff + j] = d00 * p0 + d10 * m0 + d01 * p1 + d11 * m1;
        }
    }
}
