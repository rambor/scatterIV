package version4.FormFactors;

/**
 * Orientation-averaged form factor of continuous helices: van der Maarel, J. Appl. Cryst. 56 (2023) 1714-1720.
 *
 * A helix of radius r, pitch 2 pi p and axial (projected) length L, made of n coaxial strands at azimuths psi_s:
 *
 *   P(q) = 1/2 int_{-1}^{1} dmu ( sinc^2(q mu L/2) J0^2[q (1-mu^2)^1/2 r]
 *          + sum_{k>=1} F_k { sinc^2[(q mu + k/p) L/2] + sinc^2[(q mu - k/p) L/2] } Jk^2[q (1-mu^2)^1/2 r] )   (eq. 15)
 *
 * with F_k = |(1/n) sum_s exp(i k psi_s)|^2: 1 for a single helix, cos^2(k phi/2) for a double helix with enclosed
 * angle phi (eq. 28), 1 for k a multiple of n and 0 otherwise for n equally spaced strands (eq. 37). The integrand is
 * even in mu, so the integral runs over [0, 1]. For qL >> 1 (eq. 23, plus the rod end correction -2/(qL)^2):
 *
 *   P(q) ~ pi/(qL) [ J0^2(qr) + 2 sum_{k=1}^{floor(pq)} F_k Jk^2( r (q^2 - (k/p)^2)^1/2 ) ] - 2/(qL)^2
 *
 * The finite strand cross-section enters as P_c(q) = exp(-q^2 <r_s^2>/2) (Gaussian strand profile; <r_s^2> = a^2/2
 * for a strand of radius a). P(0) = 1. Exact form below qL = {@link #EXACT_BELOW}, the limit above
 * {@link #LIMIT_ABOVE}, a smooth blend in between. Near deep minima of the limit its O(1/(qL)^2) error is
 * comparable to the value itself, hence the high switch-over.
 */
public final class HelixMath {

    /** exact quadrature up to qL = EXACT_BELOW, eq. 23 above LIMIT_ABOVE, smooth blend in between */
    public static final double EXACT_BELOW = 300, LIMIT_ABOVE = 600;
    /** Gauss-Legendre panel length in mu: PANEL / (q L_max), i.e. about 2/3 of a sinc^2 period at L_max */
    static final double PANEL = 4.0;
    private static final double[] GL_X, GL_W;      // 8-point Gauss-Legendre on [0, 1]

    static {
        double[] x = {-0.9602898564975363, -0.7966664774136267, -0.5255324099163290, -0.1834346424956498,
                0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};
        double[] w = {0.1012285362903763, 0.2223810344533745, 0.3137066458778873, 0.3626837833783620,
                0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};
        GL_X = new double[8];
        GL_W = new double[8];
        for (int i = 0; i < 8; i++) { GL_X[i] = 0.5 * (x[i] + 1); GL_W[i] = 0.5 * w[i]; }
    }

    private HelixMath() {}

    /** Helix geometry; immutable. */
    public static final class Geometry {
        public final double radius, pitch, strandRadius;
        /** strand azimuths (radians); length = number of strands */
        public final double[] psi;
        final double p;

        public Geometry(double radius, double pitch, double strandRadius, double[] psi) {
            if (!(radius >= 0)) throw new IllegalArgumentException("helix radius must be >= 0");
            if (!(pitch > 0)) throw new IllegalArgumentException("pitch must be > 0");
            if (psi == null || psi.length < 1) throw new IllegalArgumentException("at least one strand");
            this.radius = radius;
            this.pitch = pitch;
            this.strandRadius = Math.max(0, strandRadius);
            this.psi = psi.clone();
            this.p = pitch / (2 * Math.PI);
        }

        /** n strands equally spaced in azimuth (n = 1: single helix) */
        public static Geometry symmetric(double radius, double pitch, double strandRadius, int n) {
            double[] psi = new double[n];
            for (int s = 0; s < n; s++) psi[s] = 2 * Math.PI * s / n;
            return new Geometry(radius, pitch, strandRadius, psi);
        }

        /** double helix with enclosed azimuthal angle phi (pi = symmetric; eq. 29: phi = pi w_minor/(w_major + w_minor)) */
        public static Geometry doubleHelix(double radius, double pitch, double strandRadius, double phi) {
            return new Geometry(radius, pitch, strandRadius, new double[]{-phi / 2, phi / 2});
        }

        public int strands() { return psi.length; }

        /** F_k = |(1/n) sum_s exp(i k psi_s)|^2 */
        public double strandFactor(int k) {
            if (k == 0) return 1;
            double c = 0, s = 0;
            for (double a : psi) { c += Math.cos(k * a); s += Math.sin(k * a); }
            int n = psi.length;
            return (c * c + s * s) / ((double) n * n);
        }

        /** contour length of one strand for axial length L */
        public double contourLength(double L) {
            return L * Math.sqrt(p * p + radius * radius) / p;
        }

        /** eq. 19 */
        public double radiusOfGyration(double L) {
            double rg2 = L * L / 12 + radius * radius
                    + 2 * strandFactor(1) * p * p * radius * radius * (Math.cos(L / p) - 1) / (L * L);
            return Math.sqrt(rg2);
        }

        public double crossSection(double q) {
            return Math.exp(-q * q * strandRadius * strandRadius / 4);   // <r_s^2> = a^2 / 2
        }
    }

    /** P(q) for axial length L (exact, limiting or blended), including the strand cross-section */
    public static double formFactor(Geometry g, double q, double L) {
        if (q <= 0) return 1;
        return new AtQ(g, q, Math.min(L, LIMIT_ABOVE / q)).formFactor(L);
    }

    /**
     * Everything that depends only on q, so P(q, L) for many L costs one pass over the mu nodes each.
     * The mu nodes resolve the sinc^2 peaks up to L = lMax (beyond EXACT_BELOW/q only the limit is used anyway).
     */
    public static final class AtQ {
        final Geometry g;
        final double q, limitValue;
        final int K;
        final double[] F;            // strand factors
        /** node sets for decreasing L: levels[i] resolves L up to lMax / 2^i */
        final Nodes[] levels;

        /** mu nodes, weights and B[k][node] = F_k J_k^2(q r sqrt(1-mu^2)) resolving sinc^2 peaks up to L = lMax */
        static final class Nodes {
            final double lMax;
            final double[] mu, w;
            final double[][] B;
            Nodes(double lMax, double[] mu, double[] w, double[][] B) { this.lMax = lMax; this.mu = mu; this.w = w; this.B = B; }
        }

        public AtQ(Geometry g, double q, double lMax) {
            this.g = g;
            this.q = q;
            double xr = q * g.radius;
            this.K = besselOrders(xr);
            this.F = new double[K + 1];
            F[0] = 1;
            for (int k = 1; k <= K; k++) F[k] = g.strandFactor(k);
            this.limitValue = limitSum(g, q);

            // breakpoints at the peaks mu = k/(pq) inside (0,1)
            int kPeaks = (int) Math.min(K, Math.floor(g.p * q));
            double[] bp = new double[kPeaks + 2];
            int nb = 0;
            bp[nb++] = 0;
            for (int k = 1; k <= kPeaks; k++) {
                double m = k / (g.p * q);
                if (m > 0 && m < 1 && F[k] > 1e-14) bp[nb++] = m;
            }
            bp[nb++] = 1;
            java.util.Arrays.sort(bp, 0, nb);

            java.util.List<Nodes> list = new java.util.ArrayList<>();
            double lm = Math.max(lMax, 1e-9);
            while (true) {
                list.add(nodes(bp, nb, xr, lm));
                if (q * lm <= 8 || list.size() >= 14) break;   // coarsest grid reached
                lm /= 2;
            }
            this.levels = list.toArray(new Nodes[0]);
        }

        private Nodes nodes(double[] bp, int nb, double xr, double lMax) {
            double maxPanel = Math.min(0.05, PANEL / (q * lMax));
            if (xr > 1) maxPanel = Math.min(maxPanel, 2.0 / xr);   // Bessel functions vary on ~1/(q r) in mu
            int total = 0;
            int[] panels = new int[nb];
            for (int b = 0; b + 1 < nb; b++) {
                double len = bp[b + 1] - bp[b];
                panels[b] = len <= 0 ? 0 : Math.max(1, (int) Math.ceil(len / maxPanel));
                total += 8 * panels[b];
            }
            double[] mu = new double[total], w = new double[total];
            int n = 0;
            for (int b = 0; b + 1 < nb; b++) {
                if (panels[b] == 0) continue;
                double pl = (bp[b + 1] - bp[b]) / panels[b];
                for (int pn = 0; pn < panels[b]; pn++)
                    for (int i = 0; i < 8; i++) {
                        mu[n] = bp[b] + (pn + GL_X[i]) * pl;
                        w[n] = GL_W[i] * pl;
                        n++;
                    }
            }
            double[][] B = new double[K + 1][total];
            double[] J = new double[K + 2];
            for (int j = 0; j < total; j++) {
                besselArray(xr * Math.sqrt(Math.max(0, 1 - mu[j] * mu[j])), K, J);
                for (int k = 0; k <= K; k++) B[k][j] = F[k] * J[k] * J[k];
            }
            return new Nodes(lMax, mu, w, B);
        }

        /** P(q, L) including the strand cross-section */
        public double formFactor(double L) {
            double qL = q * L, v;
            if (qL <= EXACT_BELOW) v = exact(L);
            else if (qL >= LIMIT_ABOVE) v = Math.PI / qL * limitValue - 2 / (qL * qL);
            else {
                double t = (Math.log(qL) - Math.log(EXACT_BELOW)) / (Math.log(LIMIT_ABOVE) - Math.log(EXACT_BELOW));
                double s = t * t * (3 - 2 * t);
                v = (1 - s) * exact(L) + s * (Math.PI / qL * limitValue - 2 / (qL * qL));
            }
            return Math.max(v, 0) * g.crossSection(q);
        }

        /** eq. 15 on the coarsest node set that resolves L; no cross-section */
        public double exact(double L) {
            Nodes nd = levels[0];
            for (Nodes c : levels) if (c.lMax >= L) nd = c;
            final double[] mu = nd.mu, w = nd.w;
            final double[][] B = nd.B;
            double half = 0.5 * L, delta = half / g.p, sd = Math.sin(delta), cd = Math.cos(delta), total = 0;
            for (int j = 0; j < mu.length; j++) {
                double x0 = q * mu[j] * half;
                double val = sinc2(x0) * B[0][j];
                double s0 = Math.sin(x0), c0 = Math.cos(x0);
                double sp = s0, cp = c0, sm = s0, cm = c0;
                for (int k = 1; k <= K; k++) {
                    double nsp = sp * cd + cp * sd, ncp = cp * cd - sp * sd;
                    double nsm = sm * cd - cm * sd, ncm = cm * cd + sm * sd;
                    sp = nsp; cp = ncp; sm = nsm; cm = ncm;
                    double b = B[k][j];
                    if (b == 0) continue;
                    double xp = x0 + k * delta, xm = x0 - k * delta;
                    double tp = Math.abs(xp) < 1e-6 ? 1 : (sp / xp) * (sp / xp);
                    double tm = Math.abs(xm) < 1e-6 ? 1 : (sm / xm) * (sm / xm);
                    val += (tp + tm) * b;
                }
                total += w[j] * val;
            }
            return total;
        }
    }

    /** J0^2(qr) + 2 sum_{k <= pq} F_k Jk^2(r sqrt(q^2 - (k/p)^2)) (the bracket of eq. 23) */
    static double limitSum(Geometry g, double q) {
        double sum = sq(besselJ0(q * g.radius));
        int kMax = (int) Math.floor(g.p * q);
        for (int k = 1; k <= kMax; k++) {
            double f = g.strandFactor(k);
            if (f < 1e-14) continue;
            double arg = g.radius * Math.sqrt(Math.max(0, q * q - (k / g.p) * (k / g.p)));
            sum += 2 * f * sq(besselJ(k, arg));
        }
        return sum;
    }

    /** eq. 23 with the rod end correction; no cross-section factor */
    public static double limit(Geometry g, double q, double L) {
        double qL = q * L;
        return Math.PI / qL * limitSum(g, q) - 2 / (qL * qL);
    }

    /** eq. 15 by quadrature; no cross-section factor */
    public static double exact(Geometry g, double q, double L) {
        return new AtQ(g, q, L).exact(L);
    }

    // ------------------------------------------------------------------ Bessel functions

    /** orders needed: J_k(x)^2 is negligible beyond k ~ x + 3 x^(1/3) + 12 */
    static int besselOrders(double x) {
        return (int) Math.ceil(x + 3 * Math.cbrt(x) + 12);
    }

    /** J_0 .. J_K at x into out (length >= K+1), Miller's downward recurrence normalised by J0 + 2 sum J_2k = 1 */
    static void besselArray(double x, int K, double[] out) {
        if (x < 1e-10) {
            out[0] = 1;
            for (int k = 1; k <= K; k++) out[k] = 0;
            return;
        }
        int start = 2 * ((Math.max(K, (int) x) + 15 + (int) Math.sqrt(40.0 * Math.max(K, x))) / 2);
        double jp1 = 0, j = 1e-30, norm = 0;
        for (int m = start; m >= 1; m--) {
            double jm1 = (2.0 * m / x) * j - jp1;
            jp1 = j;
            j = jm1;
            int order = m - 1;
            if (order <= K) out[order] = j;
            if (order > 0 && order % 2 == 0) norm += 2 * j;
            if (Math.abs(j) > 1e250) {                    // rescale to avoid overflow
                j *= 1e-250; jp1 *= 1e-250; norm *= 1e-250;
                for (int i = order; i <= K && i < out.length; i++) out[i] *= 1e-250;
            }
        }
        norm += out[0];
        double inv = 1.0 / norm;
        for (int k = 0; k <= K; k++) out[k] *= inv;
    }

    static double besselJ0(double x) { return besselJ(0, x); }

    static double besselJ(int k, double x) {
        if (x < 1e-10) return k == 0 ? 1 : 0;
        int K = Math.max(k, 1);
        double[] out = new double[K + 2];
        besselArray(x, K, out);
        return out[k];
    }

    static double sinc2(double x) {
        if (Math.abs(x) < 1e-6) return 1 - x * x / 3;
        double s = Math.sin(x) / x;
        return s * s;
    }

    private static double sq(double v) { return v * v; }
}
