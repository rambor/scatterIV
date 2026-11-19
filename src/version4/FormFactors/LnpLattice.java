package version4.FormFactors;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Whole-pattern fit of an indexed liquid-crystalline lattice: every allowed reflection of one space group shares a
 * single lattice parameter and a single domain size, on top of a diffuse background and (optionally) one extra
 * non-lattice peak such as the RNA correlation peak.
 *
 * <pre>
 *   I(q) = sum_hkl A_hkl S(q - q_hkl, w)  +  A_x S(q - q_x, w_x)  +  c0 (q/q0)^-p + c1
 *   q_hkl = 2 pi m_hkl / a       (m = sqrt(h^2+k^2+l^2) cubic, sqrt(h^2+hk+k^2)*2/sqrt3 hexagonal, l lamellar)
 *   FWHM  = sqrt( (2 pi K / D)^2 + w_inst^2 ),  K = 0.9 (Scherrer)
 * </pre>
 * The lattice parameter, domain size, background exponent and the extra peak are refined by Levenberg-Marquardt;
 * the peak areas and the background amplitudes are solved exactly at each step and kept non-negative.
 *
 * This is the model needed for cubosomes and hexosomes, where several orders are visible: e.g. Jin et al.,
 * Nat. Commun. 16, 5054 (2025) report an Im3m cubosome whose lattice parameter falls from 174 to 151 A when long RNA
 * is loaded, with an RNA peak at q = 0.12 1/A (d = 52 A) and a 550 A ordered domain.
 */
public final class LnpLattice {

    /** reflections of a space group: label and the multiplier m with q_hkl = 2 pi m / a */
    public static final class Lattice {
        public final String name, symmetry;
        public final String[] hkl;
        public final double[] m;
        public final boolean cubic, hexagonal, lamellar;
        Lattice(String name, String symmetry, String[] hkl, double[] m, String kind) {
            this.name = name; this.symmetry = symmetry; this.hkl = hkl; this.m = m;
            cubic = kind.equals("cubic"); hexagonal = kind.equals("hex"); lamellar = kind.equals("lam");
        }
        @Override public String toString() { return name + " [" + symmetry + "]"; }
    }

    private static double[] sq(double... v) {
        double[] o = new double[v.length];
        for (int i = 0; i < v.length; i++) o[i] = Math.sqrt(v[i]);
        return o;
    }

    /** hexagonal: q_hk = (4 pi / (sqrt3 a)) sqrt(h^2+hk+k^2), so m = 2 sqrt(h^2+hk+k^2) / sqrt3 */
    private static double[] hex(double... hk2) {
        double[] o = new double[hk2.length];
        for (int i = 0; i < hk2.length; i++) o[i] = 2 * Math.sqrt(hk2[i]) / Math.sqrt(3);
        return o;
    }

    public static final Lattice[] LATTICES = {
            new Lattice("Bicontinuous cubic", "Pn3m, Q224",
                    new String[]{"(110)", "(111)", "(200)", "(211)", "(220)", "(221)", "(310)", "(311)", "(222)"},
                    sq(2, 3, 4, 6, 8, 9, 10, 11, 12), "cubic"),
            new Lattice("Bicontinuous cubic", "Im3m, Q229",
                    new String[]{"(110)", "(200)", "(211)", "(220)", "(310)", "(222)", "(321)", "(400)"},
                    sq(2, 4, 6, 8, 10, 12, 14, 16), "cubic"),
            new Lattice("Bicontinuous cubic", "Ia3d, Q230",
                    new String[]{"(211)", "(220)", "(321)", "(400)", "(420)", "(332)", "(422)", "(431)"},
                    sq(6, 8, 14, 16, 20, 22, 24, 26), "cubic"),
            new Lattice("Micellar cubic", "Pm3n, Q223",
                    new String[]{"(110)", "(200)", "(210)", "(211)", "(220)", "(310)", "(222)", "(320)", "(321)"},
                    sq(2, 4, 5, 6, 8, 10, 12, 13, 14), "cubic"),
            new Lattice("Micellar cubic", "Fm3m, Q225",
                    new String[]{"(111)", "(200)", "(220)", "(311)", "(222)", "(400)", "(331)", "(420)"},
                    sq(3, 4, 8, 11, 12, 16, 19, 20), "cubic"),
            new Lattice("Micellar cubic", "Fd3m, Q227",
                    new String[]{"(111)", "(220)", "(311)", "(222)", "(400)", "(331)", "(422)", "(511)"},
                    sq(3, 8, 11, 12, 16, 19, 24, 27), "cubic"),
            new Lattice("Inverse hexagonal", "p6m, HII",
                    new String[]{"(10)", "(11)", "(20)", "(21)", "(30)", "(22)", "(31)"},
                    hex(1, 3, 4, 7, 9, 12, 13), "hex"),
            new Lattice("Lamellar", "Lα / Lβ",
                    new String[]{"(001)", "(002)", "(003)", "(004)", "(005)", "(006)"},
                    new double[]{1, 2, 3, 4, 5, 6}, "lam"),
    };

    public static Lattice byName(String symmetry) {
        for (Lattice l : LATTICES) if (l.symmetry.equals(symmetry) || l.symmetry.startsWith(symmetry)) return l;
        return null;
    }

    /**
     * Lattice parameter relative to Pn3m for inverse bicontinuous cubic phases built on the same bilayer
     * (Bonnet relation; Marsh, Handbook of Lipid Bilayers, 2013, ch. 14): Pn3m 1, Im3m 1.279, Ia3d 1.576.
     * Two coexisting cubic phases in equilibrium should show these ratios.
     */
    public static double bonnetRatio(Lattice l) {
        if (l.symmetry.startsWith("Im3m")) return 1.279;
        if (l.symmetry.startsWith("Ia3d")) return 1.576;
        if (l.symmetry.startsWith("Pn3m")) return 1.0;
        return Double.NaN;
    }

    public enum Shape { GAUSSIAN, LORENTZIAN }

    public static final class Reflection {
        public String hkl;
        public double q, d, area, areaErr;
        public boolean inRange;
    }

    public static final class Result {
        /** the second coexisting phase, or null */
        public Result second;
        /** fraction of the Bragg area belonging to this phase when two coexist */
        public double phaseShare = 1;
        public Lattice lattice;
        public Shape shape;
        public double a, aErr, domain, domainErr, fwhm, instrument;
        public double extraQ = Double.NaN, extraWidth, extraArea, extraQErr;
        /** inorganic dopant (e.g. SPIONs): mean radius, relative polydispersity and scale; NaN when not fitted */
        public double dopantR = Double.NaN, dopantPd, dopantScale, dopantRErr;
        public double[] dopantCurve;
        public double chi2, chi2Reduced, qmin, qmax, crystallinity;
        public double[] q, I, sigma, fit, background;
        public final List<Reflection> reflections = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();

        /** unit cells across one ordered domain */
        public double cellsPerDomain() { return domain / a; }

        /**
         * Radius of the water channels (A) for a given lipid monolayer length l.
         * Cubic: r_w = a sqrt(-A0 / (2 pi chi)) - l with the minimal-surface constants of the space group.
         * Hexagonal: r_w = a / 2 - l. Lamellar: the water layer thickness d - 2 l.
         */
        public double channelRadius(double lipidLength) {
            if (lattice.lamellar) return a - 2 * lipidLength;
            // HII: the cylinders sit on a hexagonal lattice, so the radius is not simply a/2 - l. Reese et al.
            // (Phys. Chem. Chem. Phys. 17, 9194 (2015)), as used by Mendozza et al. (Nanoscale 2018, eq. S4):
            //   r_w = (0.5256 a - l) / 0.994
            if (lattice.hexagonal) return (0.5256 * a - lipidLength) / 0.994;
            if (lattice.symmetry.startsWith("Fd3m")) return Double.NaN;
            return a * Math.sqrt(-surfaceArea() / (2 * Math.PI * eulerCharacteristic())) - lipidLength;
        }

        /**
         * Volume fraction of water, which says how much of the particle is channel rather than lipid. For the
         * bicontinuous cubic phases (Mendozza et al. 2018, eq. S3, after Turner and Gruner):
         * <pre>
         *   phi_w = 1 - 2 A0 (l/a) - (4/3) pi chi (l/a)^3
         * </pre>
         * for the inverse hexagonal phase (eq. S5), with one cylinder per unit cell of area (sqrt(3)/2) a^2:
         * <pre>
         *   phi_w = 2 pi r_w^2 / (sqrt(3) a^2)
         * </pre>
         * and for a lamellar stack simply the fraction of the repeat that is not bilayer. Returns NaN where the
         * geometry does not define one (Fd3m, whose micelles are discrete).
         */
        public double waterFraction(double lipidLength) {
            if (!(a > 0) || !(lipidLength > 0)) return Double.NaN;
            if (lattice.lamellar) return clampFraction(1 - 2 * lipidLength / a);
            if (lattice.hexagonal) {
                double rw = channelRadius(lipidLength);
                return rw > 0 ? clampFraction(2 * Math.PI * rw * rw / (Math.sqrt(3) * a * a)) : Double.NaN;
            }
            if (lattice.symmetry.startsWith("Fd3m")) return Double.NaN;
            // the expansion stays finite for a lattice too small to hold the bilayer, where it means nothing:
            // the channel radius has gone negative there, so there is no water channel to take a fraction of
            if (!(channelRadius(lipidLength) > 0)) return Double.NaN;
            double x = lipidLength / a;
            return clampFraction(1 - 2 * surfaceArea() * x - 4.0 / 3.0 * Math.PI * eulerCharacteristic() * x * x * x);
        }

        private static double clampFraction(double v) {
            return v < 0 || v > 1 ? Double.NaN : v;
        }

        /** dimensionless surface area A0 of the minimal surface of this space group (Marsh, Handbook, ch. 14) */
        private double surfaceArea() {
            if (lattice.symmetry.startsWith("Im3m")) return 2.3451;
            if (lattice.symmetry.startsWith("Ia3d")) return 3.0915;
            return 1.9189;                      // Pn3m
        }

        /** Euler characteristic per unit cell of the same surface */
        private double eulerCharacteristic() {
            if (lattice.symmetry.startsWith("Im3m")) return -4;
            if (lattice.symmetry.startsWith("Ia3d")) return -8;
            return -2;                          // Pn3m
        }
    }

    static double peak(Shape s, double q, double qc, double fwhm) {
        double x = q - qc;
        if (s == Shape.LORENTZIAN) return 2 / Math.PI * fwhm / (4 * x * x + fwhm * fwhm);
        double sd = fwhm / 2.3548;
        return Math.exp(-0.5 * x * x / (sd * sd)) / (sd * Math.sqrt(2 * Math.PI));
    }

    /** Scherrer: FWHM in q for a domain of size D, broadened by the instrumental width */
    static double widthOf(double domain, double instrument) {
        double w = 2 * Math.PI * 0.9 / Math.max(domain, 1);
        return Math.sqrt(w * w + instrument * instrument);
    }

    /**
     * @param lattice     space group to fit
     * @param aStart      starting lattice parameter (A); from the assessment's indexing
     * @param extraQ      centre of an extra non-lattice peak (e.g. the RNA peak), or NaN for none
     * @param instrument  instrumental peak width (FWHM in 1/A), 0 if unknown
     */
    public static Result fit(double[] q, double[] I, double[] s, Lattice lattice, double aStart, double qmin, double qmax,
                             Shape shape, double extraQ, double instrument) {
        return fitPhases(q, I, s, new Lattice[]{lattice}, new double[]{aStart}, qmin, qmax, shape, extraQ, instrument, Double.NaN);
    }

    /** with an inorganic dopant of starting radius {@code dopantR} (A), e.g. SPIONs in a magnetocubosome */
    public static Result fit(double[] q, double[] I, double[] s, Lattice lattice, double aStart, double qmin, double qmax,
                             Shape shape, double extraQ, double instrument, double dopantR) {
        return fitPhases(q, I, s, new Lattice[]{lattice}, new double[]{aStart}, qmin, qmax, shape, extraQ, instrument, dopantR);
    }

    /** two coexisting lattices (e.g. Pn3m + Im3m in cubosomes) sharing one background and one extra peak */
    public static Result fitPair(double[] q, double[] I, double[] s, Lattice l1, double a1, Lattice l2, double a2,
                                 double qmin, double qmax, Shape shape, double extraQ, double instrument) {
        return fitPhases(q, I, s, new Lattice[]{l1, l2}, new double[]{a1, a2}, qmin, qmax, shape, extraQ, instrument, Double.NaN);
    }

    public static Result fitPair(double[] q, double[] I, double[] s, Lattice l1, double a1, Lattice l2, double a2,
                                 double qmin, double qmax, Shape shape, double extraQ, double instrument, double dopantR) {
        return fitPhases(q, I, s, new Lattice[]{l1, l2}, new double[]{a1, a2}, qmin, qmax, shape, extraQ, instrument, dopantR);
    }

    /**
     * Scattering of a polydisperse sphere of mean radius R and relative width pd (Gaussian in R), normalised to 1 at
     * q = 0. Used for an inorganic dopant such as the iron-oxide cores of SPIONs, whose own form factor and
     * interparticle correlation peak sit under the lattice pattern (Caselli et al., magnetocubosomes).
     */
    static double[] sphereCurve(double[] q, double R, double pd) {
        int nn = pd > 0.01 ? 41 : 1;
        double sd = pd * R;
        double[] out = new double[q.length];
        double wsum = 0, v0 = 0;
        for (int i = 0; i < nn; i++) {
            double z = nn == 1 ? 0 : -3 + 6.0 * i / (nn - 1);
            double r = R + z * sd;
            if (r <= 0) continue;
            double w = Math.exp(-0.5 * z * z) * Math.pow(r, 6);   // intensity weighting: volume squared
            wsum += w;
            v0 += w;
            for (int j = 0; j < q.length; j++) {
                double amp = LnpModel.sphereAmp(q[j] * r);
                out[j] += w * amp * amp;
            }
        }
        if (wsum <= 0) return out;
        for (int j = 0; j < q.length; j++) out[j] /= wsum;
        return out;
    }

    /**
     * Fits one or two lattices: each has its own lattice parameter and domain size; they share the diffuse
     * background and the optional extra peak. Reflection areas and background amplitudes are solved exactly
     * (non-negative) at every step; everything else is refined by Levenberg-Marquardt.
     */
    static Result fitPhases(double[] q, double[] I, double[] s, Lattice[] lats, double[] aStarts, double qmin, double qmax,
                            Shape shape, double extraQ, double instrument, double dopantR0) {
        final List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < q.length; j++) if (q[j] >= qmin && q[j] <= qmax && s[j] > 0) pts.add(new double[]{q[j], I[j], s[j]});
        final int n = pts.size();
        if (n < 20) throw new IllegalArgumentException("Too few points in the fitting window");
        final double[] qq = new double[n], yy = new double[n], ss = new double[n];
        for (int i = 0; i < n; i++) { qq[i] = pts.get(i)[0]; yy[i] = pts.get(i)[1]; ss[i] = pts.get(i)[2]; }
        final boolean hasExtra = !Double.isNaN(extraQ) && extraQ > qmin && extraQ < qmax;
        final double q0 = Math.sqrt(qmin * qmax);
        final boolean hasDopant = !Double.isNaN(dopantR0) && dopantR0 > 0;
        final int P = lats.length, iBg = 2 * P;
        final int iExtra = iBg + 1, iDop = iExtra + (hasExtra ? 2 : 0);
        final int nu = iDop + (hasDopant ? 2 : 0);
        final Shape sh = shape;
        final double inst = instrument;

        double[] u0 = new double[nu];
        for (int p = 0; p < P; p++) { u0[2 * p] = Math.log(aStarts[p]); u0[2 * p + 1] = Math.log(20 * aStarts[p]); }
        if (hasExtra) { u0[iExtra] = Math.log(extraQ); u0[iExtra + 1] = Math.log(0.015); }
        if (hasDopant) { u0[iDop] = Math.log(dopantR0); u0[iDop + 1] = Math.log(0.15); }

        java.util.function.Function<double[], double[][]> columns = u -> {
            List<double[]> cols = new ArrayList<>();
            for (int p = 0; p < P; p++) {
                double a = Math.exp(u[2 * p]), fw = widthOf(Math.exp(u[2 * p + 1]), inst);
                for (double m : lats[p].m) {
                    double qc = 2 * Math.PI * m / a;
                    double[] c = new double[n];
                    if (qc >= qmin - fw && qc <= qmax + fw) for (int i = 0; i < n; i++) c[i] = peak(sh, qq[i], qc, fw);
                    cols.add(c);
                }
            }
            if (hasExtra) {
                double qx = Math.exp(u[iExtra]), wx = Math.exp(u[iExtra + 1]);
                double[] c = new double[n];
                for (int i = 0; i < n; i++) c[i] = peak(sh, qq[i], qx, wx);
                cols.add(c);
            }
            if (hasDopant) cols.add(sphereCurve(qq, Math.exp(u[iDop]), Math.min(0.5, Math.exp(u[iDop + 1]))));
            double[] pw = new double[n], one = new double[n];
            for (int i = 0; i < n; i++) { pw[i] = Math.pow(qq[i] / q0, -u[iBg]); one[i] = 1; }
            cols.add(pw);
            cols.add(one);
            return cols.toArray(new double[0][]);
        };
        java.util.function.Function<double[], double[]> model = u -> {
            double[][] cols = columns.apply(u);
            double[] amp = solveNonNegative(cols, yy, ss);
            double[] f = new double[n];
            for (int k = 0; k < cols.length; k++) if (amp[k] != 0) for (int i = 0; i < n; i++) f[i] += amp[k] * cols[k][i];
            return f;
        };

        // the background exponent is badly determined by the window ends (Bragg peaks sit on them), so scan it
        // together with the domain sizes and start the refinement from the best grid point
        double bestGrid = Double.POSITIVE_INFINITY;
        for (double p = 0; p <= 4.8; p += 0.2)
            for (double cells : new double[]{3, 8, 20, 60}) {
                double[] t = u0.clone();
                for (int ph = 0; ph < P; ph++) t[2 * ph + 1] = Math.log(cells * aStarts[ph]);
                t[iBg] = p;
                double[] f = model.apply(t);
                double c = 0;
                for (int i = 0; i < n; i++) { double dd = (yy[i] - f[i]) / ss[i]; c += dd * dd; }
                if (c < bestGrid) { bestGrid = c; System.arraycopy(t, 0, u0, 0, t.length); }
            }

        double[] target = new double[n];
        for (int i = 0; i < n; i++) target[i] = yy[i] / ss[i];
        double[] u;
        try {
            u = new LevenbergMarquardtOptimizer().withCostRelativeTolerance(1e-8).withParameterRelativeTolerance(1e-8)
                    .optimize(new LeastSquaresBuilder().model(point -> {
                        double[] uu = point.toArray();
                        double[] f0 = model.apply(uu);
                        double[][] jac = new double[n][uu.length];
                        for (int p = 0; p < uu.length; p++) {
                            double h = 1e-5 * Math.max(1, Math.abs(uu[p]));
                            double[] up = uu.clone();
                            up[p] += h;
                            double[] f1 = model.apply(up);
                            for (int i = 0; i < n; i++) jac[i][p] = (f1[i] - f0[i]) / h / ss[i];
                        }
                        double[] w = new double[n];
                        for (int i = 0; i < n; i++) w[i] = f0[i] / ss[i];
                        return new Pair<>(new ArrayRealVector(w, false), new Array2DRowRealMatrix(jac, false));
                    }).target(target).start(u0).maxEvaluations(6000).maxIterations(600).build()).getPoint().toArray();
        } catch (RuntimeException notConverged) {
            u = u0;
        }

        double[] amp = solveNonNegative(columns.apply(u), yy, ss);
        double[][] cols = columns.apply(u);
        double[] fitted = model.apply(u);
        double chi2 = 0;
        for (int i = 0; i < n; i++) { double d = (yy[i] - fitted[i]) / ss[i]; chi2 += d * d; }
        int free = u.length + (int) java.util.Arrays.stream(amp).filter(v -> v != 0).count();

        // uncertainties from a numerical Jacobian at the solution (SVD pseudo-inverse: pinned parameters do not
        // make the others unavailable)
        double[] err = new double[u.length];
        java.util.Arrays.fill(err, Double.NaN);
        try {
            double[][] jac = new double[n][u.length];
            for (int p = 0; p < u.length; p++) {
                double h = 1e-5 * Math.max(1, Math.abs(u[p]));
                double[] up = u.clone();
                up[p] += h;
                double[] f1 = model.apply(up);
                for (int i = 0; i < n; i++) jac[i][p] = (f1[i] - fitted[i]) / h / ss[i];
            }
            RealMatrix J = new Array2DRowRealMatrix(jac, false);
            org.apache.commons.math3.linear.SingularValueDecomposition svd =
                    new org.apache.commons.math3.linear.SingularValueDecomposition(J.transpose().multiply(J));
            double[] sv = svd.getSingularValues();
            double tol = sv[0] * 1e-12;
            RealMatrix U = svd.getU(), V = svd.getV();
            double red = Math.max(1, chi2 / Math.max(1, n - free));
            for (int p = 0; p < u.length; p++) {
                double v = 0;
                for (int kk = 0; kk < sv.length; kk++) if (sv[kk] > tol) v += V.getEntry(p, kk) * U.getEntry(p, kk) / sv[kk];
                err[p] = Math.sqrt(Math.max(0, v) * red);
            }
        } catch (RuntimeException ignored) { }

        // one Result per phase, chained through Result.second
        Result[] rs = new Result[P];
        double braggAll = 0;
        int col = 0;
        for (int p = 0; p < P; p++) {
            Result r = new Result();
            r.lattice = lats[p];
            r.shape = shape;
            r.qmin = qmin;
            r.qmax = qmax;
            r.instrument = instrument;
            r.a = Math.exp(u[2 * p]);
            r.aErr = r.a * err[2 * p];
            r.domain = Math.exp(u[2 * p + 1]);
            r.domainErr = r.domain * err[2 * p + 1];
            r.fwhm = widthOf(r.domain, instrument);
            r.q = qq; r.I = yy; r.sigma = ss;
            r.fit = fitted;
            r.chi2 = chi2;
            r.chi2Reduced = chi2 / Math.max(1, n - free);
            double bragg = 0;
            for (int k = 0; k < lats[p].m.length; k++) {
                Reflection rf = new Reflection();
                rf.hkl = lats[p].hkl[k];
                rf.q = 2 * Math.PI * lats[p].m[k] / r.a;
                rf.d = 2 * Math.PI / rf.q;
                rf.area = amp[col + k];
                rf.inRange = rf.q >= qmin && rf.q <= qmax;
                r.reflections.add(rf);
                bragg += rf.area;
            }
            col += lats[p].m.length;
            r.phaseShare = bragg;
            braggAll += bragg;
            rs[p] = r;
        }
        for (int p = 0; p + 1 < P; p++) rs[p].second = rs[p + 1];
        for (Result r : rs) r.phaseShare = braggAll > 0 ? r.phaseShare / braggAll : Double.NaN;

        Result r0 = rs[0];
        if (hasExtra) {
            r0.extraQ = Math.exp(u[iExtra]);
            r0.extraWidth = Math.exp(u[iExtra + 1]);
            r0.extraArea = amp[col];
            r0.extraQErr = r0.extraQ * err[iExtra];
            col++;
        }
        if (hasDopant) {
            r0.dopantR = Math.exp(u[iDop]);
            r0.dopantPd = Math.min(0.5, Math.exp(u[iDop + 1]));
            r0.dopantScale = amp[col];
            r0.dopantRErr = r0.dopantR * err[iDop];
            r0.dopantCurve = new double[n];
            double[] sc = sphereCurve(qq, r0.dopantR, r0.dopantPd);
            for (int i = 0; i < n; i++) r0.dopantCurve[i] = amp[col] * sc[i];
            col++;
            if (!(r0.dopantScale > 0)) r0.notes.add("The dopant term fitted to zero: no separate nanoparticle scattering is needed here.");
            else if (r0.dopantPd >= 0.495)
                r0.notes.add("The dopant polydispersity hit its limit (0.5): its form factor overlaps the lattice peaks, so the "
                        + "dopant size is unreliable here. Measure the dopant on its own (e.g. dispersed in solvent) and fix its size.");
        }
        r0.background = new double[n];
        for (int i = 0; i < n; i++) r0.background[i] = amp[col] * cols[col][i] + amp[col + 1];
        for (Result r : rs) { r.background = r0.background; r.dopantCurve = r0.dopantCurve; r.dopantR = r0.dopantR; }
        double total = 0;
        for (int i = 1; i < n; i++) total += 0.5 * (fitted[i] + fitted[i - 1]) * (qq[i] - qq[i - 1]);
        r0.crystallinity = total > 0 ? braggAll / total : Double.NaN;
        for (Result r : rs) r.crystallinity = r0.crystallinity;

        for (Result r : rs) {
            int seen = 0;
            for (Reflection rf : r.reflections) if (rf.inRange && rf.area > 0) seen++;
            if (seen < 2) r.notes.add(r.lattice.symmetry + ": fewer than two reflections in the window, so its lattice parameter is not constrained.");
            if (r.domain < 2 * r.a) r.notes.add(r.lattice.symmetry + ": the ordered domain is barely larger than one unit cell, so the peaks are broad and the assignment is weak.");
            if (instrument > 0 && r.fwhm < 1.2 * instrument) r.notes.add(r.lattice.symmetry + ": peaks are near the instrumental width, so the domain size is a lower limit.");
        }
        if (P == 2) {
            double ratio = rs[1].a / rs[0].a, expect = bonnetRatio(rs[1].lattice) / bonnetRatio(rs[0].lattice);
            if (!Double.isNaN(expect)) {
                double dev = 100 * (ratio / expect - 1);
                r0.notes.add(String.format("Lattice-parameter ratio %s/%s = %.3f; the Bonnet relation for two cubic phases on the same "
                                + "bilayer predicts %.3f (%+.0f %%)%s.", rs[1].lattice.symmetry.split(",")[0], rs[0].lattice.symmetry.split(",")[0],
                        ratio, expect, dev, Math.abs(dev) < 8 ? ", consistent with coexistence at equilibrium" : ", so the two phases are probably not in equilibrium"));
            }
        }
        return r0;
    }

    /**
     * Whole-pattern indexing: every known lattice is scanned over a grid of lattice parameters (coarse score, then a
     * full fit of the best few) and the results are returned ranked by chi^2. This does not need a clean list of
     * peaks, so it works on cubosome patterns where a peak finder struggles with the steep particle background.
     *
     * @return up to {@code keep} fits, best first; empty if the window holds too few points
     */
    public static List<Result> identify(double[] q, double[] I, double[] s, double qmin, double qmax, Shape shape,
                                        double extraQ, double instrument, int keep) {
        List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < q.length; j++) if (q[j] >= qmin && q[j] <= qmax && s[j] > 0) pts.add(new double[]{q[j], I[j], s[j]});
        if (pts.size() < 20) return new ArrayList<>();
        int n = pts.size();
        double[] qq = new double[n], yy = new double[n], ss = new double[n];
        for (int i = 0; i < n; i++) { qq[i] = pts.get(i)[0]; yy[i] = pts.get(i)[1]; ss[i] = pts.get(i)[2]; }
        List<double[]> best = new ArrayList<>();   // {lattice index, a, chi2}
        for (int li = 0; li < LATTICES.length; li++) {
            Lattice L = LATTICES[li];
            // the first reflection must fall inside the window: that fixes the range of a to scan
            double aLo = 2 * Math.PI * L.m[0] / qmax, aHi = 2 * Math.PI * L.m[0] / qmin;
            double bestChi = Double.POSITIVE_INFINITY, bestA = aLo;
            int steps = 160;
            for (int i = 0; i <= steps; i++) {
                double a = aLo * Math.pow(aHi / aLo, i / (double) steps);
                double c = coarseChi2(qq, yy, ss, L, a, shape, extraQ, instrument, qmin, qmax);
                if (c < bestChi) { bestChi = c; bestA = a; }
            }
            best.add(new double[]{li, bestA, bestChi});
        }
        best.sort((a, b) -> Double.compare(a[2], b[2]));
        List<Result> out = new ArrayList<>();
        for (int i = 0; i < Math.min(keep, best.size()); i++) {
            try {
                out.add(fit(q, I, s, LATTICES[(int) best.get(i)[0]], best.get(i)[1], qmin, qmax, shape, extraQ, instrument));
            } catch (RuntimeException ignored) { }
        }
        out.sort((a, b) -> Double.compare(a.chi2, b.chi2));
        return out;
    }

    /** chi^2 of the best background / domain combination at a fixed lattice and lattice parameter */
    private static double coarseChi2(double[] qq, double[] yy, double[] ss, Lattice L, double a, Shape shape,
                                     double extraQ, double instrument, double qmin, double qmax) {
        int n = qq.length;
        boolean hasExtra = !Double.isNaN(extraQ) && extraQ > qmin && extraQ < qmax;
        double q0 = Math.sqrt(qmin * qmax);
        double best = Double.POSITIVE_INFINITY;
        for (double cells : new double[]{5, 15, 50}) {
            double fw = widthOf(cells * a, instrument);
            List<double[]> cols = new ArrayList<>();
            for (double m : L.m) {
                double qc = 2 * Math.PI * m / a;
                if (qc < qmin - fw || qc > qmax + fw) continue;
                double[] c = new double[n];
                for (int i = 0; i < n; i++) c[i] = peak(shape, qq[i], qc, fw);
                cols.add(c);
            }
            if (cols.isEmpty()) continue;
            if (hasExtra) {
                double[] c = new double[n];
                for (int i = 0; i < n; i++) c[i] = peak(shape, qq[i], extraQ, 0.015);
                cols.add(c);
            }
            for (double p : new double[]{0, 1, 2, 3, 4}) {
                List<double[]> all = new ArrayList<>(cols);
                double[] pw = new double[n], one = new double[n];
                for (int i = 0; i < n; i++) { pw[i] = Math.pow(qq[i] / q0, -p); one[i] = 1; }
                all.add(pw);
                all.add(one);
                double[][] A = all.toArray(new double[0][]);
                double[] amp = solveNonNegative(A, yy, ss);
                double c = 0;
                for (int i = 0; i < n; i++) {
                    double f = 0;
                    for (int k = 0; k < A.length; k++) if (amp[k] != 0) f += amp[k] * A[k][i];
                    double d = (yy[i] - f) / ss[i];
                    c += d * d;
                }
                if (c < best) best = c;
            }
        }
        return best;
    }

    /**
     * The L3 sponge phase (spongeosomes) of Fig. 1G of Caselli et al. (2024): bicontinuous like a cubic phase but
     * without long-range order, so it shows one broad correlation peak and no Bragg reflections. Fitted with the
     * Teubner-Strey structure factor
     * <pre>
     *   I(q) = scale / (a2 + c1 q^2 + c2 q^4) + background
     *   d  = 2 pi [ (1/2) sqrt(a2/c2) - (1/4)(c1/c2) ]^(-1/2)     quasi-periodic repeat
     *   xi = [ (1/2) sqrt(a2/c2) + (1/4)(c1/c2) ]^(-1/2)          correlation length
     * </pre>
     * An amphiphilicity factor f = c1 / sqrt(4 a2 c2) below 0 means a structured (sponge-like) phase; above 0 it is
     * a disordered mixture with no well-defined repeat.
     */
    public static final class Sponge {
        public double d, xi, amphiphilicity, chi2Reduced, qmin, qmax;
        public double[] q, I, sigma, fit;
        /** The fitted Teubner-Strey coefficients a2, c1, c2 and the flat background. */
        public double[] ts;
        public final List<String> notes = new ArrayList<>();
    }

    /**
     * Refines the coefficients of a sponge fit against the intensities themselves. The linear solve above works on
     * 1/I, which is quick and needs no starting point but weights the tail too heavily; a few Gauss-Newton steps in
     * I-space recover the correlation length, which that first pass leaves short.
     */
    private static void polishSponge(Sponge sp) {
        double[] p = sp.ts.clone();
        int n = sp.q.length;
        double bestChi = sp.chi2Reduced * Math.max(1, n - 4);
        double[] bestP = p.clone();
        for (int it = 0; it < 60; it++) {
            double[][] jtj = new double[4][4];
            double[] jtr = new double[4];
            for (int i = 0; i < n; i++) {
                double q2 = sp.q[i] * sp.q[i], q4 = q2 * q2;
                double den = p[0] + p[1] * q2 + p[2] * q4;
                if (den <= 0) return;
                double model = 1 / den + p[3];
                double w = 1 / sp.sigma[i];
                double[] g = {-1 / (den * den), -q2 / (den * den), -q4 / (den * den), 1};
                double r = (sp.I[i] - model) * w;
                for (int a = 0; a < 4; a++) {
                    jtr[a] += w * g[a] * r;
                    for (int b = 0; b < 4; b++) jtj[a][b] += w * w * g[a] * g[b];
                }
            }
            double lambda = 1e-3;
            boolean improved = false;
            for (int tries = 0; tries < 12 && !improved; tries++) {
                double[] step;
                try {
                    double[][] m = new double[4][4];
                    for (int a = 0; a < 4; a++) {
                        System.arraycopy(jtj[a], 0, m[a], 0, 4);
                        m[a][a] *= 1 + lambda;
                    }
                    step = new LUDecomposition(new Array2DRowRealMatrix(m)).getSolver().solve(new ArrayRealVector(jtr)).toArray();
                } catch (RuntimeException singular) { return; }
                double[] trial = p.clone();
                for (int a = 0; a < 4; a++) trial[a] += step[a];
                if (!(trial[0] > 0) || !(trial[2] > 0) || trial[3] < 0) { lambda *= 10; continue; }
                double chi = 0;
                for (int i = 0; i < n; i++) {
                    double q2 = sp.q[i] * sp.q[i];
                    double den = trial[0] + trial[1] * q2 + trial[2] * q2 * q2;
                    if (den <= 0) { chi = Double.POSITIVE_INFINITY; break; }
                    double r = (sp.I[i] - (1 / den + trial[3])) / sp.sigma[i];
                    chi += r * r;
                }
                if (chi < bestChi) { bestChi = chi; bestP = trial.clone(); p = trial; improved = true; }
                else lambda *= 10;
            }
            if (!improved) break;
        }
        double a2 = bestP[0], c1 = bestP[1], c2 = bestP[2];
        double root = 0.5 * Math.sqrt(a2 / c2), half = 0.25 * c1 / c2;
        sp.ts = bestP;
        sp.d = root - half > 0 ? 2 * Math.PI / Math.sqrt(root - half) : Double.NaN;
        sp.xi = root + half > 0 ? 1 / Math.sqrt(root + half) : Double.NaN;
        sp.amphiphilicity = c1 / Math.sqrt(4 * a2 * c2);
        sp.chi2Reduced = bestChi / Math.max(1, n - 4);
        for (int i = 0; i < n; i++) {
            double q2 = sp.q[i] * sp.q[i];
            sp.fit[i] = 1 / (a2 + c1 * q2 + c2 * q2 * q2) + bestP[3];
        }
    }

    public static Sponge fitSponge(double[] q, double[] I, double[] s, double qmin, double qmax) {
        List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < q.length; j++) if (q[j] >= qmin && q[j] <= qmax && s[j] > 0 && I[j] > 0) pts.add(new double[]{q[j], I[j], s[j]});
        int n = pts.size();
        if (n < 12) throw new IllegalArgumentException("Too few points in the window");
        final double[] qq = new double[n], yy = new double[n], ss = new double[n];
        for (int i = 0; i < n; i++) { qq[i] = pts.get(i)[0]; yy[i] = pts.get(i)[1]; ss[i] = pts.get(i)[2]; }
        // 1/I is linear in q^2 and q^4 once the background is removed, so fit that by weighted least squares over a
        // small grid of backgrounds and keep the best
        double ymin = Double.POSITIVE_INFINITY;
        for (double v : yy) ymin = Math.min(ymin, v);
        Sponge best = null;
        for (int ib = 0; ib <= 120; ib++) {
            double bg = (ib / 120.0) * 0.995 * ymin;
            double[][] a = new double[3][4];
            for (int i = 0; i < n; i++) {
                double y = yy[i] - bg;
                if (y <= 0) continue;
                double inv = 1 / y, w = (y * y * y * y) / (ss[i] * ss[i]);   // weight of 1/I from sigma(I)
                double[] basis = {1, qq[i] * qq[i], Math.pow(qq[i], 4)};
                for (int r = 0; r < 3; r++) {
                    a[r][3] += w * basis[r] * inv;
                    for (int c = 0; c < 3; c++) a[r][c] += w * basis[r] * basis[c];
                }
            }
            double[] x;
            try {
                double[][] m = new double[3][3];
                double[] b = new double[3];
                for (int r = 0; r < 3; r++) { b[r] = a[r][3]; System.arraycopy(a[r], 0, m[r], 0, 3); }
                x = new LUDecomposition(new Array2DRowRealMatrix(m)).getSolver().solve(new ArrayRealVector(b)).toArray();
            } catch (RuntimeException singular) { continue; }
            double a2 = x[0], c1 = x[1], c2 = x[2];
            if (!(a2 > 0) || !(c2 > 0)) continue;
            double chi = 0;
            double[] fit = new double[n];
            for (int i = 0; i < n; i++) {
                fit[i] = 1 / (a2 + c1 * qq[i] * qq[i] + c2 * Math.pow(qq[i], 4)) + bg;
                double r = (yy[i] - fit[i]) / ss[i];
                chi += r * r;
            }
            double red = chi / Math.max(1, n - 4);
            if (best != null && red >= best.chi2Reduced) continue;
            Sponge sp = new Sponge();
            sp.chi2Reduced = red;
            sp.qmin = qmin;
            sp.qmax = qmax;
            sp.q = qq; sp.I = yy; sp.sigma = ss; sp.fit = fit;
            double root = 0.5 * Math.sqrt(a2 / c2), half = 0.25 * c1 / c2;
            sp.d = root - half > 0 ? 2 * Math.PI / Math.sqrt(root - half) : Double.NaN;
            sp.xi = root + half > 0 ? 1 / Math.sqrt(root + half) : Double.NaN;
            sp.amphiphilicity = c1 / Math.sqrt(4 * a2 * c2);
            sp.ts = new double[]{a2, c1, c2, bg};
            best = sp;
        }
        if (best == null) throw new IllegalArgumentException("The Teubner-Strey form does not fit this window");
        polishSponge(best);
        if (Double.isNaN(best.d)) best.notes.add("No quasi-periodic repeat: this is a disordered mixture rather than a sponge.");
        if (best.amphiphilicity > 0) best.notes.add(String.format("Amphiphilicity factor %+.2f (above 0): the scattering is that of a "
                + "disordered mixture, not a structured sponge phase.", best.amphiphilicity));
        return best;
    }

    public static String describeSponge(Sponge sp) {
        StringBuilder sb = new StringBuilder("L3 SPONGE PHASE (Teubner–Strey)\n");
        sb.append(String.format("Window %.3f–%.3f Å⁻¹, reduced χ² %.2f%n%n", sp.qmin, sp.qmax, sp.chi2Reduced));
        sb.append(String.format("  Quasi-periodic repeat d  %8.1f Å   (%.2f nm)%n", sp.d, sp.d / 10));
        sb.append(String.format("  Correlation length ξ     %8.1f Å   (%.2f nm)%n", sp.xi, sp.xi / 10));
        sb.append(String.format("  Amphiphilicity factor    %8.2f     (below 0 = structured; −1 is the lamellar/sponge boundary)%n", sp.amphiphilicity));
        if (sp.xi > 0 && sp.d > 0)
            sb.append(String.format("  ξ/d = %.2f: order persists over about %.1f repeats, so no Bragg peaks are expected.%n", sp.xi / sp.d, sp.xi / sp.d));
        sb.append("\n  A sponge (L3) phase is bicontinuous like a cubic phase but without long-range order\n"
                + "  (Fig. 1G of Caselli et al. 2024). If sharp reflections are present, fit a lattice instead.\n");
        for (String n : sp.notes) sb.append("  ! ").append(n).append('\n');
        return sb.toString();
    }

    public static String describe(Result r, double lipidLength) {
        StringBuilder sb = new StringBuilder("LATTICE FIT – ").append(r.lattice);
        if (r.second != null) sb.append("  +  ").append(r.second.lattice);
        sb.append('\n');
        sb.append(String.format("Window %.3f–%.3f Å⁻¹, %s peaks, reduced χ² %.2f%n%n", r.qmin, r.qmax,
                r.shape == Shape.GAUSSIAN ? "Gaussian" : "Lorentzian", r.chi2Reduced));
        sb.append(String.format("  Lattice parameter a   %8.1f ± %.2f Å   (%.2f nm)%n", r.a, r.aErr, r.a / 10));
        sb.append(String.format("  Ordered domain D      %8.0f ± %.0f Å   (Scherrer, K = 0.9; ≈ %.1f unit cells)%n", r.domain, r.domainErr, r.cellsPerDomain()));
        sb.append(String.format("  Peak width (FWHM)     %8.4f Å⁻¹%s%n", r.fwhm, r.instrument > 0 ? String.format("  (instrument %.4f)", r.instrument) : ""));
        double rw = r.channelRadius(lipidLength);
        if (!Double.isNaN(rw)) {
            sb.append(String.format("  %-20s  %8.1f Å        (diameter %.1f nm, lipid length %.1f Å)%n",
                    r.lattice.lamellar ? "Water layer" : "Water channel radius", rw, 2 * rw / 10, lipidLength));
            if (r.lattice.cubic && !r.lattice.symmetry.startsWith("Fd3m") && !r.lattice.symmetry.startsWith("Pm3n")
                    && !r.lattice.symmetry.startsWith("Fm3m"))
                sb.append(String.format("     Reported cubosome range: 4.0 nm (monoolein, Pn3m) to 38.1 nm (monoolein:DOTAP:PEG-monoolein%n"
                        + "     95:4:1, Im3m, a = 68.4 nm) [Barriga, Holme & Stevens, Angew. Chem. 2019]. This one is %s.%n",
                        2 * rw / 10 < 4 ? "below that range – check the lipid length"
                                : 2 * rw / 10 > 38 ? "above it – an unusually swollen phase" : "inside it"));
            double phi = r.waterFraction(lipidLength);
            if (!Double.isNaN(phi)) {
                sb.append(String.format("  %-20s  %8.0f %%%n", "Water volume fraction", 100 * phi));
                sb.append(String.format("     %.0f %% of the phase is water channel and %.0f %% is lipid, which is what a loading%n"
                        + "     capacity is calculated from. Both follow from the lattice parameter and the lipid length,%n"
                        + "     so an error in the lipid length moves them together.%n", 100 * phi, 100 * (1 - phi)));
            }
        }
        sb.append(String.format("  Bragg share of the window %5.0f %%          (rest is diffuse scattering)%n", 100 * r.crystallinity));
        sb.append(String.format("%n  %-8s %10s %10s %12s%n", "hkl", "q (Å⁻¹)", "d (Å)", "area"));
        for (Reflection rf : r.reflections) {
            if (!rf.inRange) continue;
            sb.append(String.format("  %-8s %10.4f %10.1f %12.4g%s%n", rf.hkl, rf.q, rf.d, rf.area, rf.area > 0 ? "" : "   (absent)"));
        }
        if (r.second != null) {
            Result t = r.second;
            sb.append(String.format("%n  Second phase: %s%n", t.lattice));
            sb.append(String.format("  Lattice parameter a   %8.1f ± %.2f Å   (%.2f nm)%n", t.a, t.aErr, t.a / 10));
            sb.append(String.format("  Ordered domain D      %8.0f ± %.0f Å   (≈ %.1f unit cells)%n", t.domain, t.domainErr, t.cellsPerDomain()));
            double rw2 = t.channelRadius(lipidLength);
            if (!Double.isNaN(rw2)) sb.append(String.format("  Water channel radius  %8.1f Å%n", rw2));
            double phi2 = t.waterFraction(lipidLength);
            if (!Double.isNaN(phi2)) sb.append(String.format("  Water volume fraction %8.0f %%%n", 100 * phi2));
            sb.append(String.format("  Share of the Bragg area %6.0f %% (first phase %.0f %%)%n", 100 * t.phaseShare, 100 * r.phaseShare));
            sb.append(String.format("%n  %-8s %10s %10s %12s%n", "hkl", "q (Å⁻¹)", "d (Å)", "area"));
            for (Reflection rf : t.reflections)
                if (rf.inRange) sb.append(String.format("  %-8s %10.4f %10.1f %12.4g%s%n", rf.hkl, rf.q, rf.d, rf.area, rf.area > 0 ? "" : "   (absent)"));
            for (String nt : t.notes) if (!r.notes.contains(nt)) r.notes.add(nt);
        }
        if (!Double.isNaN(r.dopantR) && r.dopantScale > 0)
            sb.append(String.format("%n  Inorganic dopant: diameter %.1f ± %.1f nm, polydispersity %.2f%n"
                            + "    A separate nanoparticle population (e.g. SPIONs in a magnetocubosome). Its form factor is fitted%n"
                            + "    with the lattice so that it is not absorbed into the background.%n",
                    2 * r.dopantR / 10, 2 * r.dopantRErr / 10, r.dopantPd));
        if (!Double.isNaN(r.extraQ)) {
            double dx = 2 * Math.PI / r.extraQ;
            sb.append(String.format("%n  Extra (non-lattice) peak: q = %.4f ± %.4f Å⁻¹, d = %.1f Å (%.1f nm), FWHM %.4f Å⁻¹, area %.4g%n",
                    r.extraQ, r.extraQErr, dx, dx / 10, r.extraWidth, r.extraArea));
            sb.append(dx >= 40 && dx <= 70
                    ? "    In this range it is the nucleic-acid correlation peak reported for RNA inside cubic and hexagonal\n"
                    + "    phases (Jin et al. 2025: q = 0.12 Å⁻¹, d = 52 Å).\n"
                    : String.format("    This is not a reflection of the lattice. At %.1f nm it is most likely a correlation distance%n"
                    + "    between dopant particles or domains; a nucleic-acid correlation peak would sit at 4–7 nm.%n", dx / 10));
        }
        for (String n : r.notes) sb.append("  ! ").append(n).append('\n');
        return sb.toString();
    }

    /** weighted least squares with non-negative amplitudes (drop the most negative column and repeat) */
    static double[] solveNonNegative(double[][] cols, double[] y, double[] s) {
        int m = cols.length, n = y.length;
        boolean[] use = new boolean[m];
        double[] out = new double[m];
        for (int k = 0; k < m; k++) {
            double norm = 0;
            for (int i = 0; i < n; i++) norm += cols[k][i] * cols[k][i];
            use[k] = norm > 0;
        }
        for (int pass = 0; pass < m + 1; pass++) {
            List<Integer> idx = new ArrayList<>();
            for (int k = 0; k < m; k++) if (use[k]) idx.add(k);
            if (idx.isEmpty()) return out;
            int na = idx.size();
            double[][] a = new double[na][na];
            double[] b = new double[na];
            for (int i = 0; i < n; i++) {
                double w = 1 / (s[i] * s[i]);
                for (int p = 0; p < na; p++) {
                    b[p] += w * cols[idx.get(p)][i] * y[i];
                    for (int qi = 0; qi < na; qi++) a[p][qi] += w * cols[idx.get(p)][i] * cols[idx.get(qi)][i];
                }
            }
            double[] x;
            try {
                x = new LUDecomposition(new Array2DRowRealMatrix(a)).getSolver().solve(new ArrayRealVector(b)).toArray();
            } catch (RuntimeException singular) {
                return out;
            }
            int worst = -1;
            double worstV = 0;
            for (int p = 0; p < na; p++) if (x[p] < worstV) { worstV = x[p]; worst = idx.get(p); }
            if (worst < 0) {
                java.util.Arrays.fill(out, 0);
                for (int p = 0; p < na; p++) out[idx.get(p)] = x[p];
                return out;
            }
            use[worst] = false;
        }
        return out;
    }
}
