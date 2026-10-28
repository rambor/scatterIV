package version4.FormFactors;

/**
 * Polymer form factors shared by the chain kernels and the model-free analysis. All are normalised, P(0) = 1.
 *
 * <ul>
 *   <li>Gaussian chain (Debye): P = 2 (e^-x - 1 + x) / x^2, x = (q Rg)^2</li>
 *   <li>Excluded-volume chain (Hammouda 1993 / Benoit): with U = (q Rg)^2 (2nu+1)(2nu+2) / 6,
 *       P = [gamma(1/2nu, U) U^-1/2nu - gamma(1/nu, U) U^-1/nu] / nu, gamma = lower incomplete gamma.
 *       nu = 1/2 gives the Debye function.</li>
 *   <li>Wormlike chain with excluded volume (SasView flexible_cylinder / WormLikeChainEXV, Pedersen-Schurtenberger
 *       method 3 with Chen 2006 corrections), contour length L, Kuhn length b, optional cross-section radius.</li>
 * </ul>
 */
public final class PolymerMath {

    private PolymerMath() {}

    // ------------------------------------------------------------------ Gaussian chain

    /** Debye function of x = (q Rg)^2 */
    public static double debye(double x) {
        if (x < 1e-3) return 1 - x / 3 + x * x / 12 - x * x * x / 60;
        return 2 * (Math.expm1(-x) + x) / (x * x);
    }

    /** d debye / dx */
    public static double debyeDx(double x) {
        if (x < 1e-3) return -1.0 / 3 + x / 6 - x * x / 20;
        double e = Math.exp(-x);
        // P = 2(e - 1 + x)/x^2  ->  P' = 2(1 - e)/x^2 - 4(e - 1 + x)/x^3
        return 2 * (-Math.expm1(-x)) / (x * x) - 4 * (Math.expm1(-x) + x) / (x * x * x);
    }

    /** amplitude of a Gaussian chain attached at one end: psi = (1 - e^-x)/x, x = (q Rg)^2 */
    public static double psi(double x) {
        if (x < 1e-4) return 1 - x / 2 + x * x / 6;
        return -Math.expm1(-x) / x;
    }

    public static double psiDx(double x) {
        if (x < 1e-4) return -0.5 + x / 3;
        return (Math.exp(-x) * x + Math.expm1(-x)) / (x * x);
    }

    // ------------------------------------------------------------------ excluded-volume chain

    /** factor c(nu) with U = c (q Rg)^2 */
    public static double evFactor(double nu) {
        return (2 * nu + 1) * (2 * nu + 2) / 6.0;
    }

    /** Hammouda excluded-volume chain as a function of U = (q Rg)^2 (2nu+1)(2nu+2)/6 */
    public static double evChain(double U, double nu) {
        if (Math.abs(nu - 0.5) < 1e-12) return debye(U);
        double s1 = 1 / (2 * nu), s2 = 1 / nu;
        if (U < 1e-6) {
            // series: gamma(s,U) U^-s = sum (-U)^k / (k! (s+k))
            return (series(s1, U) - series(s2, U)) / nu;
        }
        return (lowerGamma(s1, U) * Math.pow(U, -s1) - lowerGamma(s2, U) * Math.pow(U, -s2)) / nu;
    }

    /** U dP/dU for the excluded-volume chain:  (s2 g2 - s1 g1) / nu  with g = gamma(s,U) U^-s, plus the e^-U terms cancel */
    public static double evChainLogDeriv(double U, double nu) {
        if (Math.abs(nu - 0.5) < 1e-12) return U * debyeDx(U);
        double s1 = 1 / (2 * nu), s2 = 1 / nu;
        double g1, g2;
        if (U < 1e-6) { g1 = series(s1, U); g2 = series(s2, U); }
        else { g1 = lowerGamma(s1, U) * Math.pow(U, -s1); g2 = lowerGamma(s2, U) * Math.pow(U, -s2); }
        return (s2 * g2 - s1 * g1) / nu;
    }

    private static double series(double s, double U) {
        return 1 / s - U / (s + 1) + U * U / (2 * (s + 2));
    }

    /** lower incomplete gamma function gamma(a, x) = Gamma(a) P(a, x) */
    public static double lowerGamma(double a, double x) {
        if (x <= 0) return 0;
        double gln = lnGamma(a);
        if (x < a + 1) {
            double ap = a, sum = 1 / a, del = sum;
            for (int n = 0; n < 500; n++) {
                ap += 1;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * 1e-15) break;
            }
            return sum * Math.exp(-x + a * Math.log(x));
        }
        // continued fraction for the upper function, Lentz
        double b = x + 1 - a, c = 1 / 1e-300, d = 1 / b, h = d;
        for (int i = 1; i < 500; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < 1e-300) d = 1e-300;
            c = b + an / c;
            if (Math.abs(c) < 1e-300) c = 1e-300;
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-15) break;
        }
        double upper = Math.exp(-x + a * Math.log(x)) * h;
        return Math.exp(gln) - upper;
    }

    /** ln Gamma(x), Lanczos (g = 7, n = 9), |rel err| < 1e-14 for x > 0 */
    public static double lnGamma(double x) {
        if (x < 0.5) return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - lnGamma(1 - x);
        final double[] c = {0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        x -= 1;
        double a = c[0], t = x + 7.5;
        for (int i = 1; i < 9; i++) a += c[i] / (x + i);
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }

    // ------------------------------------------------------------------ wormlike chain with excluded volume

    /*
     * Port of SasView / sasmodels lib/wrc_cyl.c (flexible_cylinder, "WormLikeChainEXV"):
     * Pedersen & Schurtenberger, Macromolecules 29 (1996) 7602, method 3 (excluded volume), with the corrections of
     * Chen, Butler & Magid, Langmuir 22 (2006) 6539. L = contour length, b = Kuhn length (= 2 x persistence length).
     * Normalised: Sk_WR(0) = 1.
     */

    private static double square(double x) { return x * x; }

    static double rgSquare(double L, double b) {
        double x = L / b;
        double alphasq = Math.pow(1.0 + x * x * (1.027284681130835e-01 + 1.534414548417740e-03 * x), 5.866666666666667e-02);
        return alphasq * L * b / 6.0;
    }

    static double rgSquareShort(double L, double b) {
        double r = b / L;
        return rgSquare(L, b) * (1.0 + r * (-1.5 + r * (1.5 + r * 0.75 * Math.expm1(-2.0 / r))));
    }

    private static double wWR(double x) {
        return 0.5 + 0.5 * Math.tanh((x - 1.523) / 0.1477);
    }

    /** Debye function of qsq = (q Rg)^2, with the Pade form sasmodels uses at small argument */
    static double sDebye(double qsq) {
        if (qsq < 0.25) {
            double x = qsq;
            double A1 = 1. / 15., A2 = 1. / 60, A3 = 0., A4 = 1. / 75600.;
            double B1 = 2. / 5., B2 = 1. / 15., B3 = 1. / 180., B4 = 1. / 5040.;
            return ((((A4 * x + A3) * x + A2) * x + A1) * x + 1.) / ((((B4 * x + B3) * x + B2) * x + B1) * x + 1.);
        }
        return 2. * (Math.expm1(-qsq) + qsq) / (qsq * qsq);
    }

    private static double aLong(double q, double L, double b) {
        final double p1 = 4.12, p2 = 4.42, q0 = 3.1;
        final double C1 = 1.22, C2 = 0.4288, C3 = -1.651, C4 = 1.523, C5 = 0.1477, miu = 0.585;
        double C = (L / b > 10.0 ? 3.06 * Math.pow(L / b, -0.44) : 1.0);
        double r2 = rgSquare(L, b);
        double r = Math.sqrt(r2);
        double qr_b = q0 * r / b;
        double qr_b_sq = qr_b * qr_b;
        double qr_b_4 = qr_b_sq * qr_b_sq;
        double qr_b_miu = Math.pow(qr_b, -1.0 / miu);
        double em1_qr_b_sq = Math.expm1(-qr_b_sq);
        double sech2 = 1.0 / square(Math.cosh((qr_b - C4) / C5));
        double w = wWR(qr_b);
        double t1 = Math.pow(q0, 1.0 + p1 + p2) / (b * (p1 - p2));
        double t2 = C / (15.0 * L) * (
                +14.0 * b * b * em1_qr_b_sq / (q0 * qr_b_sq)
                + 2.0 * q0 * r2 * Math.exp(-qr_b_sq) * (11.0 + 7.0 / qr_b_sq));
        double t11 = ((C3 * qr_b_miu + C2) * qr_b_miu + C1) * qr_b_miu;
        double t3 = r * sech2 / (2. * C5) * t11;
        double t4 = r * (em1_qr_b_sq + qr_b_sq) * sech2 / (C5 * qr_b_4);
        double t5 = -4.0 * r * qr_b * em1_qr_b_sq / qr_b_4 * (1.0 - w);
        double t10 = 2.0 * (em1_qr_b_sq + qr_b_sq) / qr_b_4 * (1.0 - w);
        double t6 = 4.0 * b / q0 * t10;
        double t7 = r * ((-3.0 * C3 * qr_b_miu - 2.0 * C2) * qr_b_miu - 1.0 * C1) * qr_b_miu / (miu * qr_b);
        double t9 = C * b / L * (4.0 - Math.exp(-qr_b_sq) * (11.0 + 7.0 / qr_b_sq) + 7.0 / qr_b_sq) / 15.0;
        double t12 = b * b * Math.PI / (L * q0 * q0) + t2 + t3 - t4 + t5 - t6 + t7 * w;
        double t13 = -b * Math.PI / (L * q0) + t9 + t10 + t11 * w;
        double a1 = Math.pow(q0, p1) * t13 - t1 * Math.pow(q0, -p2) * (t12 + b * p1 / q0 * t13);
        double a2 = t1 * Math.pow(q0, -p1) * (t12 + b * p1 / q0 * t13);
        return a1 * Math.pow(q * b, -p1) + a2 * Math.pow(q * b, -p2) + Math.PI / (q * L);
    }

    private static double shortTerm(double r2, double exp_qr_b, double L, double b, double p1short, double p2short, double q0) {
        double qr2 = q0 * q0 * r2;
        double b3 = b * b * b;
        double q0p = Math.pow(q0, -4.0 + p1short);
        return 1.0 / (L * r2 * r2) * b / exp_qr_b * q0p
                * (8.0 * b3 * L
                - 8.0 * b3 * exp_qr_b * L
                + 2.0 * b3 * exp_qr_b * L * p2short
                - 2.0 * b * exp_qr_b * L * p2short * qr2
                + 4.0 * b * exp_qr_b * L * qr2
                - 2.0 * b3 * L * p2short
                + 4.0 * b * L * qr2
                - Math.PI * exp_qr_b * qr2 * q0 * r2
                + Math.PI * exp_qr_b * p2short * qr2 * q0 * r2);
    }

    private static double aShort(double qp, double L, double b, double q0) {
        final double p1short = 5.36, p2short = 5.62;
        double r2 = rgSquareShort(L, b);
        double exp_qr_b = Math.exp(r2 * square(q0 / b));
        double pdiff = p1short - p2short;
        double a1 = shortTerm(r2, exp_qr_b, L, b, p1short, p2short, q0) / pdiff;
        double a2 = -shortTerm(r2, exp_qr_b, L, b, p2short, p1short, q0) / pdiff;
        return a1 * Math.pow(qp * b, -p1short) + a2 * Math.pow(qp * b, -p2short) + Math.PI / (qp * L);
    }

    private static double sExv(double q, double L, double b) {
        final double C1 = 1.22, C2 = 0.4288, C3 = -1.651, miu = 0.585;
        double qr = q * Math.sqrt(rgSquare(L, b));
        double qr_miu = Math.pow(qr, -1.0 / miu);
        double w = wWR(qr);
        double t10 = sDebye(qr * qr) * (1.0 - w);
        double t11 = ((C3 * qr_miu + C2) * qr_miu + C1) * qr_miu;
        return t10 + w * t11;
    }

    private static double sExvNew(double q, double L, double b) {
        double qr = q * Math.sqrt(rgSquare(L, b));
        double qr2 = qr * qr;
        double C = (L / b > 10.0) ? 3.06 * Math.pow(L / b, -0.44) : 1.0;
        double t9 = C * b / L * (4.0 - Math.exp(-qr2) * (11.0 + 7.0 / qr2) + 7.0 / qr2) / 15.0;
        double orig = sExv(q, L, b);
        double del = 1.05;
        double qdel = (sExv(q * del, L, b) - orig) / (q * (del - 1.0));
        if (qdel < 0) return t9 + orig;
        double w = wWR(qr);
        return t9 + sDebye(qr * qr) * (1.0 - w);
    }

    /** SasView Sk_WR: wormlike chain with excluded volume, contour length L, Kuhn length b; P(0) = 1 */
    public static double wormlikeEXV(double q, double L, double b) {
        if (q <= 0) return 1.0;
        double rgShort = Math.sqrt(rgSquareShort(L, b));
        double q0short = Math.max(1.9 / rgShort, 3.0);
        if (L > 4 * b) {
            return q * b <= 3.1 ? sExvNew(q, L, b) : aLong(q, L, b);
        }
        return q * b <= q0short ? sDebye(square(q * rgShort)) : aShort(q, L, b, q0short);
    }

    /** radius of gyration used by the model (Pedersen eq. 15, with the short-chain correction for L <= 4b) */
    public static double wormlikeRg(double L, double b) {
        return Math.sqrt(L > 4 * b ? rgSquare(L, b) : rgSquareShort(L, b));
    }

    /** cross-section factor of the flexible cylinder, [2 J1(qR)/(qR)]^2 (1 for R = 0) */
    public static double crossSection(double q, double R) {
        double x = q * R;
        if (x < 1e-6) return 1.0;
        double v = 2 * besselJ1(x) / x;
        return v * v;
    }

    /** Bessel J1 (rational approximations, |err| ~ 1e-8) */
    public static double besselJ1(double x) {
        double ax = Math.abs(x);
        if (ax < 8.0) {
            double y = x * x;
            double a1 = x * (72362614232.0 + y * (-7895059235.0 + y * (242396853.1 + y * (-2972611.439 + y * (15704.48260 + y * (-30.16036606))))));
            double a2 = 144725228442.0 + y * (2300535178.0 + y * (18583304.74 + y * (99447.43394 + y * (376.9991397 + y * 1.0))));
            return a1 / a2;
        }
        double z = 8.0 / ax, y = z * z, xx = ax - 2.356194491;
        double a1 = 1.0 + y * (0.183105e-2 + y * (-0.3516396496e-4 + y * (0.2457520174e-5 + y * (-0.240337019e-6))));
        double a2 = 0.04687499995 + y * (-0.2002690873e-3 + y * (0.8449199096e-5 + y * (-0.88228987e-6 + y * 0.105787412e-6)));
        double ans = Math.sqrt(0.636619772 / ax) * (Math.cos(xx) * a1 - z * Math.sin(xx) * a2);
        return x < 0 ? -ans : ans;
    }

    // ------------------------------------------------------------------ block-copolymer micelle

    /**
     * Pedersen-Gerstenberg micelle (SasView polymer_micelle.c): spherical core of radius R made of N core blocks
     * (volume vCore each), N Gaussian corona chains (volume vCorona, radius of gyration rg) centred at R + d rg.
     * Returns the unnormalised amplitude-squared sum (no number density / unit factors):
     * N^2 bs^2 Phi^2 + N bc^2 Pc + 2 N^2 bs bc Phi psi s + N (N-1) bc^2 psi^2 s^2.
     */
    public static double micelle(double q, double N, double betaCore, double betaCorona, double R, double rg, double d) {
        double x = q * R;
        double phi = sphereAmplitude(x);
        double z = square(q * rg);
        double pc = z == 0 ? 1.0 : 2.0 * (Math.expm1(-z) + z) / (z * z);
        double ps = z == 0 ? 1.0 : -Math.expm1(-z) / z;
        double y = q * (R + d * rg);
        double s = y == 0 ? 1.0 : Math.sin(y) / y;
        return square(N * betaCore * phi) + N * betaCorona * betaCorona * pc
                + 2.0 * N * N * betaCore * betaCorona * phi * ps * s
                + N * (N - 1.0) * square(betaCorona * ps * s);
    }

    /** 3 j1(x)/x */
    public static double sphereAmplitude(double x) {
        if (x < 1e-3) return 1 - x * x / 10 + x * x * x * x / 280;
        return 3 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
    }
}
