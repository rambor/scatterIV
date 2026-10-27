package version4.FormFactors;

/**
 * Triaxial ellipsoid, semi-axes a <= b <= c (kept sorted by the fitter).
 *
 *   P(q) = < F(q r)^2 >_{x,y}  (P(0) = 1),   x,y in [0,1],  phi = pi x / 2
 *   r^2  = (a^2 cos^2 phi + b^2 sin^2 phi)(1 - y^2) + c^2 y^2
 *   V    = 4/3 pi a b c
 *
 * Log-derivatives: dP/du_k = < 2 F G (dr/du_k)/r >, with
 *   (dr/du_a)/r = a^2 cos^2 phi (1-y^2) / r^2   (and analogously for b, c).
 *
 * Node counts in phi and y are chosen per particle from q_max |b-a| and q_max (c-a).
 * This is the expensive kernel (n_phi * n_y evaluations per q); use fewer particles than for spheroids.
 */
public final class TriaxialKernel implements ShapeKernel {

    private static final double FOUR_PI_3 = 4.0 * Math.PI / 3.0;
    private final FormFactorTable table;
    private final double qmax;
    private final int minNodes, maxNodes;

    public TriaxialKernel(FormFactorTable table, double qmax) {
        this(table, qmax, 6, 40);
    }

    public TriaxialKernel(FormFactorTable table, double qmax, int minNodes, int maxNodes) {
        this.table = table;
        this.qmax = qmax;
        this.minNodes = minNodes;
        this.maxNodes = Math.min(maxNodes, GaussLegendre.MAX_N);
    }

    @Override public int dim() { return 3; }
    @Override public String[] parameterNames() { return new String[]{"R_a", "R_b", "R_c"}; }
    @Override public String label() { return "TRIAXIAL_ELLIPSOID"; }
    @Override public boolean sortParameters() { return true; }
    @Override public int scratchSize() { return 5 * maxNodes * maxNodes; }

    @Override
    public double volume(double[] u, int off) {
        return FOUR_PI_3 * Math.exp(u[off] + u[off + 1] + u[off + 2]);
    }

    @Override
    public double maxEffectiveRadius(double[] upper) {
        return Math.max(upper[0], Math.max(upper[1], upper[2]));
    }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] s) {
        final double a = Math.exp(u[uOff]), b = Math.exp(u[uOff + 1]), c = Math.exp(u[uOff + 2]);
        final double a2 = a * a, b2 = b * b, c2 = c * c;

        double lo = Math.min(a, Math.min(b, c)), hi = Math.max(a, Math.max(b, c));
        final int nPhi = GaussLegendre.nodesFor(qmax * Math.abs(b - a), minNodes, maxNodes);
        final int nY = GaussLegendre.nodesFor(qmax * (hi - lo), minNodes, maxNodes);
        final double[] xp = GaussLegendre.nodes(nPhi), wp = GaussLegendre.weights(nPhi);
        final double[] xy = GaussLegendre.nodes(nY), wy = GaussLegendre.weights(nY);
        final int n = nPhi * nY;

        // layout: r | fa | fb | fc | weight   each of length n
        int idx = 0;
        for (int ip = 0; ip < nPhi; ip++) {
            double phi = 0.5 * Math.PI * xp[ip];
            double cs = Math.cos(phi), sn = Math.sin(phi);
            double ca = a2 * cs * cs, sb = b2 * sn * sn;
            for (int iy = 0; iy < nY; iy++) {
                double y2 = xy[iy] * xy[iy];
                double ta = ca * (1.0 - y2), tb = sb * (1.0 - y2), tc = c2 * y2;
                double r2 = ta + tb + tc;
                s[idx] = Math.sqrt(r2);
                s[n + idx] = ta / r2;
                s[2 * n + idx] = tb / r2;
                s[3 * n + idx] = tc / r2;
                s[4 * n + idx] = wp[ip] * wy[iy];
                idx++;
            }
        }

        final double[] tab = table.tab;
        final double invH = table.invH;
        final int last = table.last;
        final int nq = q.length;

        for (int j = 0; j < nq; j++) {
            final double qj = q[j];
            double acc = 0, da = 0, db = 0, dc = 0;
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
                double wm = s[4 * n + m];
                acc += wm * F * F;
                double wfg = 2.0 * wm * F * G;
                da += wfg * s[n + m];
                db += wfg * s[2 * n + m];
                dc += wfg * s[3 * n + m];
            }
            P[pOff + j] = acc;
            dP[dOff + j] = da;
            dP[dOff + nq + j] = db;
            dP[dOff + 2 * nq + j] = dc;
        }
    }
}
