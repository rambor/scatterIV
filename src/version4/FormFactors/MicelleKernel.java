package version4.FormFactors;

/**
 * Block-copolymer micelle (Pedersen & Gerstenberg 1996; SasView polymer_micelle), parameters R_core and Rg of the
 * corona chains. The aggregation number follows the core size, N = packing (4/3 pi R^3) / V_coreBlock, so a
 * distribution of R_core is a distribution of aggregation numbers; Rg can be held to a narrow window (brushRg +- %).
 *
 * I = N^2 bs^2 Phi^2 + N bc^2 Pc + 2 N^2 bs bc Phi psi s + N(N-1) bc^2 psi^2 s^2,   s = sin(q r)/(q r), r = R + d Rg,
 * normalised by I(0) = N^2 (bs + bc)^2, with bs = V_core (rho_core - rho_solv), bc = V_corona (rho_corona - rho_solv).
 * "Volume" is the micelle mass N (V_core + V_corona).
 */
public final class MicelleKernel implements ShapeKernel {

    private static final double FOUR_PI_3 = 4.0 * Math.PI / 3.0;
    private final FormFactorTable table;
    private final double bs, bc, vCore, vCorona, d, packing;

    public MicelleKernel(FormFactorTable table, double vCoreBlock, double vCoronaBlock, double rhoSolvent,
                         double rhoCore, double rhoCorona, double penetration, double packing) {
        this.table = table;
        this.vCore = vCoreBlock;
        this.vCorona = vCoronaBlock;
        this.bs = vCoreBlock * (rhoCore - rhoSolvent);
        this.bc = vCoronaBlock * (rhoCorona - rhoSolvent);
        this.d = penetration;
        this.packing = packing;
        if (!(vCoreBlock > 0 && vCoronaBlock > 0 && packing > 0)) throw new IllegalArgumentException("block volumes and packing must be > 0");
        if (Math.abs(bs + bc) < 1e-6 * (Math.abs(bs) + Math.abs(bc)))
            throw new IllegalArgumentException("core and corona contrasts cancel: the micelle has no forward scattering");
    }

    public double aggregationNumber(double R) { return packing * FOUR_PI_3 * R * R * R / vCore; }

    @Override public int dim() { return 2; }
    @Override public String[] parameterNames() { return new String[]{"R_core", "Rg_brush"}; }
    @Override public String label() { return "POLYMER_MICELLE"; }
    @Override public boolean sortParameters() { return false; }
    @Override public int scratchSize() { return 0; }
    @Override public double maxEffectiveRadius(double[] upper) { return upper[0]; }

    @Override public double volume(double[] u, int off) {
        return aggregationNumber(Math.exp(u[off])) * (vCore + vCorona);
    }

    @Override public double forwardScatteringPower(double[] u, int off) {
        double N = aggregationNumber(Math.exp(u[off]));
        return N * N * (bs + bc) * (bs + bc);
    }

    @Override
    public void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        final double R = Math.exp(u[uOff]), rg = Math.exp(u[uOff + 1]);
        final double N = aggregationNumber(R), N2 = N * N;
        final double I0 = N2 * (bs + bc) * (bs + bc);
        final double inv = 1 / I0;
        final double r = R + d * rg;
        final double[] tab = table.tab;
        final double invH = table.invH;
        final int last = table.last, nq = q.length;
        for (int j = 0; j < nq; j++) {
            double qq = q[j];
            // core amplitude Phi and G = x Phi'(x)
            double x = qq * R, F, G, t = x * invH;
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
            double z = qq * qq * rg * rg;
            double pc, dpc, ps, dps;
            if (z < 1e-3) {
                pc = PolymerMath.debye(z); dpc = PolymerMath.debyeDx(z);
                ps = PolymerMath.psi(z); dps = PolymerMath.psiDx(z);
            } else {   // one exponential for all four
                double em1 = Math.expm1(-z), iz = 1 / z, iz2 = iz * iz;
                pc = 2 * (em1 + z) * iz2;
                dpc = -2 * em1 * iz2 - 4 * (em1 + z) * iz2 * iz;
                ps = -em1 * iz;
                dps = ((em1 + 1) * z + em1) * iz2;
            }
            double y = qq * r, s, dsy;   // s = sin y / y, dsy = ds/dlny = cos y - s
            if (y < 1e-4) { s = 1 - y * y / 6; dsy = -y * y / 3; }
            else { s = Math.sin(y) / y; dsy = Math.cos(y) - s; }
            double dsR = dsy * R / r, dsG = dsy * d * rg / r;

            double t1 = N2 * bs * bs * F * F;
            double t2 = N * bc * bc * pc;
            double t3 = 2 * N2 * bs * bc * F * ps * s;
            double t4 = N * (N - 1) * bc * bc * ps * ps * s * s;
            double I = t1 + t2 + t3 + t4;
            double val = I * inv;
            P[pOff + j] = val;

            // d/dlnR: N ~ R^3
            double dIR = 6 * t1 + N2 * bs * bs * 2 * F * G
                    + 3 * t2
                    + 2 * bs * bc * (6 * N2 * F * ps * s + N2 * (G * ps * s + F * ps * dsR))
                    + bc * bc * ps * ps * ((6 * N2 - 3 * N) * s * s + N * (N - 1) * 2 * s * dsR);
            double dI0R = 6 * I0;
            dP[dOff + j] = (dIR - val * dI0R) * inv;
            // d/dlnRg: z ~ Rg^2
            double dIG = N * bc * bc * dpc * 2 * z
                    + 2 * N2 * bs * bc * F * (dps * 2 * z * s + ps * dsG)
                    + N * (N - 1) * bc * bc * (2 * ps * dps * 2 * z * s * s + ps * ps * 2 * s * dsG);
            dP[dOff + nq + j] = dIG * inv;
        }
    }
}
