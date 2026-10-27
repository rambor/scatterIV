package version4.FormFactors;

/**
 * Core-shell spheroid: core semi-axes (a, a, c), shell outer semi-axes (A, A, C) = (a + t, a + t, c + t),
 * i.e. the same convention as the original CoreShell model. Parameters (log): u = (ln a, ln c, ln t).
 *
 * Amplitude for orientation mu (cosine between the symmetry axis and q):
 *   A(q, mu) = drc * Vc * F(q rc(mu)) + drs * Vs * F(q rs(mu))
 *   rc(mu)^2 = a^2 (1 - mu^2) + c^2 mu^2,   rs(mu)^2 = A^2 (1 - mu^2) + C^2 mu^2
 *   drc = rho_core - rho_shell,  drs = rho_shell - rho_solvent,  Vc = 4/3 pi a^2 c,  Vs = 4/3 pi A^2 C
 *
 * Normalised form factor (P(0) = 1 when both contrast terms have the same sign):
 *   P(q) = < f^2 >_mu,  f = wc F(q rc) + ws F(q rs),  w = drc Vc / S, drs Vs / S,  S = |drc| Vc + |drs| Vs
 * S stays positive for hollow shells (rho_core = rho_solvent) and for opposite-sign contrasts, where A(0) may
 * vanish; the particle ensemble is then weighted by S^2 instead of A(0)^2 (forwardScatteringPower = S^2).
 *
 * Log-derivatives (G(x) = x F'(x)):
 *   df/du_k = dwc_k F_c + wc G_c dln rc/du_k + dws_k F_s + ws G_s dln rs/du_k,   dP/du_k = < 2 f df/du_k >
 *   dln rc/du = (a^2(1-mu^2), c^2 mu^2, 0) / rc^2
 *   dln rs/du = (A a (1-mu^2), C c mu^2, t (A (1-mu^2) + C mu^2)) / rs^2
 *   dw_k = w (dln V_k - dS_k / S),  dln Vc = (2, 1, 0),  dln Vs = (2a/A, c/C, 2t/A + t/C)
 */
public final class CoreShellSpheroidKernel implements ShapeKernel {

    private static final double FOUR_PI_3 = 4.0 * Math.PI / 3.0;
    private final FormFactorTable table;
    private final double qmax;
    private final double[] drcs;     // core - shell contrast per species
    private final String[] names;
    private final double drs;
    private final int minNodes = 8, maxNodes = 64;

    public CoreShellSpheroidKernel(FormFactorTable table, double qmax, double rhoSolvent, double rhoShell, double rhoCore) {
        this(table, qmax, rhoSolvent, rhoShell, new double[]{rhoCore}, null);
    }

    /**
     * Several core species (e.g. {buffer, RNA}): each particle carries one of these core densities and the fitter
     * samples the species of every particle together with its geometry.
     */
    public CoreShellSpheroidKernel(FormFactorTable table, double qmax, double rhoSolvent, double rhoShell,
                                   double[] rhoCores, String[] speciesNames) {
        this.table = table;
        this.qmax = qmax;
        this.drs = rhoShell - rhoSolvent;
        this.drcs = new double[rhoCores.length];
        this.names = new String[rhoCores.length];
        for (int k = 0; k < rhoCores.length; k++) {
            drcs[k] = rhoCores[k] - rhoShell;
            if (drcs[k] == 0 && drs == 0) throw new IllegalArgumentException("core-shell: all densities equal, no contrast");
            names[k] = speciesNames != null && k < speciesNames.length ? speciesNames[k]
                    : (rhoCores[k] == rhoSolvent ? "empty" : String.format("core %.2f", rhoCores[k]));
        }
    }

    @Override public int speciesCount() { return drcs.length; }
    @Override public String speciesName(int s) { return names[s]; }

    @Override public int dim() { return 3; }
    @Override public String[] parameterNames() { return new String[]{"core_a", "core_c", "shell_t"}; }
    @Override public String label() { return "CORESHELL_SPHEROID"; }
    @Override public boolean sortParameters() { return false; }
    @Override public int scratchSize() { return 7 * maxNodes; }

    /** total (outer) particle volume */
    @Override
    public double volume(double[] u, int off) {
        double a = Math.exp(u[off]), c = Math.exp(u[off + 1]), t = Math.exp(u[off + 2]);
        return FOUR_PI_3 * (a + t) * (a + t) * (c + t);
    }

    @Override
    public double forwardScatteringPower(double[] u, int off) { return forwardScatteringPower(0, u, off); }

    @Override
    public double forwardScatteringPower(int sp, double[] u, int off) {
        final double drc = drcs[sp];
        double a = Math.exp(u[off]), c = Math.exp(u[off + 1]), t = Math.exp(u[off + 2]);
        double S = Math.abs(drc) * FOUR_PI_3 * a * a * c + Math.abs(drs) * FOUR_PI_3 * (a + t) * (a + t) * (c + t);
        return S * S;
    }

    @Override
    public double maxEffectiveRadius(double[] upper) { return Math.max(upper[0], upper[1]) + upper[2]; }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] sc) {
        evaluate(0, u, uOff, q, P, pOff, dP, dOff, sc);
    }

    @Override
    public void evaluate(int sp, double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] sc) {
        final double drc = drcs[sp];
        final double a = Math.exp(u[uOff]), c = Math.exp(u[uOff + 1]), t = Math.exp(u[uOff + 2]);
        final double A = a + t, C = c + t;
        final double Vc = FOUR_PI_3 * a * a * c, Vs = FOUR_PI_3 * A * A * C;
        final double ac = drc * Vc, as = drs * Vs;
        final double S = Math.abs(ac) + Math.abs(as);
        final double wc = ac / S, ws = as / S;

        // dln V and dS/S per parameter
        final double[] dlnVc = {2.0, 1.0, 0.0};
        final double[] dlnVs = {2.0 * a / A, c / C, 2.0 * t / A + t / C};
        final double[] dwc = new double[3], dws = new double[3];
        for (int k = 0; k < 3; k++) {
            double dSoverS = (Math.abs(ac) * dlnVc[k] + Math.abs(as) * dlnVs[k]) / S;
            dwc[k] = wc * (dlnVc[k] - dSoverS);
            dws[k] = ws * (dlnVs[k] - dSoverS);
        }

        final int n = GaussLegendre.nodesFor(qmax * Math.abs(c - a), minNodes, maxNodes);
        final double[] mu = GaussLegendre.nodes(n);
        final double[] w = GaussLegendre.weights(n);
        // scratch: rc | rs | lrc_a | lrc_c | lrs_a | lrs_c | lrs_t
        for (int m = 0; m < n; m++) {
            double mu2 = mu[m] * mu[m], om = 1.0 - mu2;
            double rc2 = a * a * om + c * c * mu2;
            double rs2 = A * A * om + C * C * mu2;
            sc[m] = Math.sqrt(rc2);
            sc[n + m] = Math.sqrt(rs2);
            sc[2 * n + m] = a * a * om / rc2;
            sc[3 * n + m] = c * c * mu2 / rc2;
            sc[4 * n + m] = A * a * om / rs2;
            sc[5 * n + m] = C * c * mu2 / rs2;
            sc[6 * n + m] = t * (A * om + C * mu2) / rs2;
        }

        final double[] tab = table.tab;
        final double invH = table.invH;
        final int last = table.last;
        final int nq = q.length;

        for (int j = 0; j < nq; j++) {
            final double qj = q[j];
            double acc = 0, d0 = 0, d1 = 0, d2 = 0;
            for (int m = 0; m < n; m++) {
                double Fc, Gc, Fs, Gs;
                double x = qj * sc[m];
                double tt = x * invH;
                int i = (int) tt;
                if (i < last) {
                    double fr = tt - i; int k = i << 1;
                    Fc = tab[k] + fr * (tab[k + 2] - tab[k]);
                    Gc = tab[k + 1] + fr * (tab[k + 3] - tab[k + 1]);
                } else { Fc = FormFactorTable.amplitude(x); Gc = FormFactorTable.xDerivative(x); }
                x = qj * sc[n + m];
                tt = x * invH;
                i = (int) tt;
                if (i < last) {
                    double fr = tt - i; int k = i << 1;
                    Fs = tab[k] + fr * (tab[k + 2] - tab[k]);
                    Gs = tab[k + 1] + fr * (tab[k + 3] - tab[k + 1]);
                } else { Fs = FormFactorTable.amplitude(x); Gs = FormFactorTable.xDerivative(x); }

                double f = wc * Fc + ws * Fs;
                double wm = w[m];
                acc += wm * f * f;
                double gc = wc * Gc, gs = ws * Gs;
                double df0 = dwc[0] * Fc + gc * sc[2 * n + m] + dws[0] * Fs + gs * sc[4 * n + m];
                double df1 = dwc[1] * Fc + gc * sc[3 * n + m] + dws[1] * Fs + gs * sc[5 * n + m];
                double df2 = dwc[2] * Fc + dws[2] * Fs + gs * sc[6 * n + m];
                double tw = 2.0 * wm * f;
                d0 += tw * df0; d1 += tw * df1; d2 += tw * df2;
            }
            P[pOff + j] = acc;
            dP[dOff + j] = d0;
            dP[dOff + nq + j] = d1;
            dP[dOff + 2 * nq + j] = d2;
        }
    }
}
