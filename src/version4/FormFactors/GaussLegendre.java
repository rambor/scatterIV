package version4.FormFactors;

/**
 * Gauss-Legendre rules mapped to [0,1] with weights summing to 1 (i.e. they compute averages).
 * Rules for n = 1..MAX_N are built once at class load.
 */
public final class GaussLegendre {

    public static final int MAX_N = 128;
    private static final double[][] NODES = new double[MAX_N + 1][];
    private static final double[][] WEIGHTS = new double[MAX_N + 1][];

    static {
        for (int n = 1; n <= MAX_N; n++) build(n);
    }

    private GaussLegendre() {}

    public static double[] nodes(int n) { return NODES[n]; }
    public static double[] weights(int n) { return WEIGHTS[n]; }

    /**
     * Number of nodes needed to average F(q r)^2 over an interval where q*r changes by qSpan.
     * F^2 oscillates with period ~pi in x, so ~2 nodes per oscillation plus a floor is ample.
     */
    public static int nodesFor(double qSpan, int minN, int maxN) {
        int n = (int) Math.ceil(2.0 * qSpan / Math.PI) + minN;
        n = ((n + 3) / 4) * 4;
        if (n < minN) n = minN;
        if (n > maxN) n = maxN;
        return n;
    }

    private static void build(int n) {
        double[] x = new double[n];
        double[] w = new double[n];
        int m = (n + 1) / 2;
        for (int i = 0; i < m; i++) {
            double z = Math.cos(Math.PI * (i + 0.75) / (n + 0.5));
            double pp = 0;
            for (int it = 0; it < 100; it++) {
                double p1 = 1.0, p2 = 0.0;
                for (int j = 1; j <= n; j++) {
                    double p3 = p2;
                    p2 = p1;
                    p1 = ((2.0 * j - 1.0) * z * p2 - (j - 1.0) * p3) / j;
                }
                pp = n * (z * p1 - p2) / (z * z - 1.0);
                double z1 = z;
                z = z1 - p1 / pp;
                if (Math.abs(z - z1) < 1e-15) break;
            }
            double wt = 2.0 / ((1.0 - z * z) * pp * pp);
            // map [-1,1] -> [0,1], weights scaled so they sum to 1
            x[i] = 0.5 * (1.0 - z);
            x[n - 1 - i] = 0.5 * (1.0 + z);
            w[i] = 0.5 * wt;
            w[n - 1 - i] = 0.5 * wt;
        }
        NODES[n] = x;
        WEIGHTS[n] = w;
    }
}