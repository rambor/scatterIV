package version4.FormFactors;

/**
 * Polydisperse spherical core / multi-shell model for lipid nanoparticles, with an optional Gaussian peak for the
 * internal mesophase of nucleic-acid-loaded particles and a flat background.
 *
 * Model of Li, Song, Li, Tu, Mehmood, Chen, Li &amp; Tian, "Mesoscopic Structure of Lipid Nanoparticles Studied by
 * Small-Angle X-Ray Scattering: A Spherical Core-Triple Shell Model Analysis", Membranes 15, 153 (2025), Eqs. 3-4
 * and S1:
 * <pre>
 *   A(q; Rc) = sum_k (rho_k - rho_{k+1}) V(r_k) j(q r_k),   r_0 = Rc,  r_k = Rc + t_1 + ... + t_k,  rho_{N+1} = rho_s = 0
 *   j(x)     = 3 (sin x - x cos x) / x^3,   V(r) = 4/3 pi r^3
 *   I(q)     = scale * &lt;A^2&gt;_Rc / S0^2  +  A_peak exp(-(q - q_peak)^2 / (2 w^2))  +  background
 * </pre>
 * The core radius Rc is Gaussian distributed (mean R0, standard deviation sigma, truncated at Rc &gt; 0); the shell
 * thicknesses are the same for every particle. S0 = sum_k |rho_k - rho_{k+1}| V(r_k) at Rc = R0 makes {@code scale}
 * roughly the forward intensity and removes the overall magnitude of the densities, so only density RATIOS are
 * determined by the data and at least one density must be fixed.
 *
 * Units: lengths in Angstrom, q in 1/Angstrom, densities as electron-density contrast to the buffer in e/Angstrom^3.
 * Li et al. report dimensionless densities; their fitted ratios (rho_2/rho_1 ~ -2.4, rho_3/rho_1 ~ -0.02) are
 * reproduced by the defaults below with the inner lipid layer at -0.035 e/A^3 (hydrocarbon chains in water).
 */
public final class LnpModel {

    /** parameter indices */
    public static final int SCALE = 0, R0 = 1, SIGMA = 2, T1 = 3, T2 = 4, T3 = 5,
            RHO_C = 6, RHO_1 = 7, RHO_2 = 8, RHO_3 = 9, PEAK_A = 10, PEAK_Q = 11, PEAK_W = 12, BG = 13,
            T4 = 14, RHO_4 = 15;
    public static final int NPAR = 16;
    public static final int MAX_SHELLS = 4;

    /** parameter index of the thickness of shell k (1..4) */
    public static int tIndex(int k) { return k == 4 ? T4 : T1 + k - 1; }

    /** parameter index of the density of layer k (0 = core, 1..4 = shells) */
    public static int rhoIndex(int k) { return k == 0 ? RHO_C : k == 4 ? RHO_4 : RHO_C + k; }

    /** true for thickness parameters, false otherwise */
    public static boolean isThickness(int p) { return p == T1 || p == T2 || p == T3 || p == T4; }

    /** true for density parameters */
    public static boolean isDensity(int p) { return p == RHO_C || p == RHO_1 || p == RHO_2 || p == RHO_3 || p == RHO_4; }

    public static final String[] NAMES = {
            "Scale", "Core radius R₀", "Size spread σ", "Inner lipid shell t₁", "Headgroup shell t₂", "PEG corona t₃",
            "Core density ρc", "Inner shell density ρ₁", "Headgroup density ρ₂", "Corona density ρ₃",
            "Peak height", "Peak position q*", "Peak width w", "Background", "Shell 4 t₄", "Shell 4 density ρ₄"};

    public static final String[] UNITS = {
            "", "Å", "Å", "Å", "Å", "Å", "e/Å³", "e/Å³", "e/Å³", "e/Å³", "", "Å⁻¹", "Å⁻¹", "", "Å", "e/Å³"};

    public static final String[] TIPS = {
            "Overall intensity scale (about the forward intensity of the particle term)",
            "Mean radius of the core (Gaussian distribution of core radii)",
            "Standard deviation of the core radius. σ/R₀ above ~0.2 washes out the form-factor oscillations",
            "Inner lipid layer (ionizable lipid, DSPC tails, cholesterol); usually lower density than buffer",
            "Hydrophilic headgroup layer; Li et al. fix it at 3 Å",
            "Hydrated PEG-lipid corona; Li et al. fix it at 40 Å for PEG-2000",
            "Core electron density minus buffer (ionizable lipid, cholesterol, water and nucleic acid)",
            "Inner lipid shell electron density minus buffer; hydrocarbon tails are about -0.03 to -0.04 e/Å³. Held fixed as the reference by default",
            "Headgroup layer electron density minus buffer (phosphocholine headgroups are about +0.08 to +0.12 e/Å³)",
            "PEG corona electron density minus buffer (small and positive: the corona is mostly water)",
            "Height of the Gaussian peak from the nucleic-acid / ionizable-lipid mesophase",
            "Position of the mesophase peak; repeat distance d = 2π/q*",
            "Standard deviation of the Gaussian peak (FWHM = 2.355 w)",
            "Flat background",
            "Thickness of the fourth shell (vesicle: PEG corona)",
            "Density of the fourth shell relative to the buffer (vesicle: PEG corona)"};

    /** true = parameter must be positive and is fitted in log space */
    public static final boolean[] POSITIVE = {
            true, true, true, true, true, true, false, false, false, false, true, true, true, false, true, false};

    /** number of shells used (0 = homogeneous sphere ... 3 = core-triple shell) */
    public final int shells;
    /** whether the Gaussian mesophase peak is part of the model */
    public final boolean peak;

    public LnpModel(int shells, boolean peak) {
        if (shells < 0 || shells > MAX_SHELLS) throw new IllegalArgumentException("shells must be 0.." + MAX_SHELLS);
        this.shells = shells;
        this.peak = peak;
    }

    /** whether parameter k takes part in this model variant */
    public boolean uses(int k) {
        switch (k) {
            case T1: case RHO_1: return shells >= 1;
            case T2: case RHO_2: return shells >= 2;
            case T3: case RHO_3: return shells >= 3;
            case T4: case RHO_4: return shells >= 4;
            case PEAK_A: case PEAK_Q: case PEAK_W: return peak;
            default: return true;
        }
    }

    // ------------------------------------------------------------------ evaluation

    /** j(x) = 3 (sin x - x cos x) / x^3, the normalised sphere amplitude */
    static double sphereAmp(double x) {
        if (x < 1e-2) {
            double x2 = x * x;
            return 1 - x2 / 10 + x2 * x2 / 280;
        }
        return 3 * (net.jafama.FastMath.sin(x) - x * net.jafama.FastMath.cos(x)) / (x * x * x);
    }

    /** interface radii r_0 .. r_shells for core radius rc */
    double[] radii(double[] p, double rc) {
        double[] r = new double[shells + 1];
        r[0] = rc;
        for (int k = 1; k <= shells; k++) r[k] = r[k - 1] + p[tIndex(k)];
        return r;
    }

    /** density steps rho_k - rho_{k+1}, solvent = 0 */
    double[] steps(double[] p) {
        double[] rho = new double[shells + 2];
        rho[0] = p[RHO_C];
        for (int k = 1; k <= shells; k++) rho[k] = p[rhoIndex(k)];
        rho[shells + 1] = 0;
        double[] d = new double[shells + 1];
        for (int k = 0; k <= shells; k++) d[k] = rho[k] - rho[k + 1];
        return d;
    }

    /** S0 = sum |drho_k| V(r_k) at the mean core radius */
    double norm(double[] p) {
        double[] r = radii(p, p[R0]);
        double[] d = steps(p);
        double s = 0;
        for (int k = 0; k <= shells; k++) s += Math.abs(d[k]) * volume(r[k]);
        return s;
    }

    static double volume(double r) { return 4.0 / 3.0 * Math.PI * r * r * r; }

    /** quadrature nodes (core radii) and normalised weights for the Gaussian size distribution */
    double[][] sizeNodes(double[] p, double qmax) {
        double r0 = p[R0], s = Math.abs(p[SIGMA]);
        if (s < 1e-3 * r0) return new double[][]{{r0}, {1}};
        // a few points per oscillation period pi/q of A^2 in Rc
        int n = (int) Math.ceil(8 * s * qmax * 1.3) + 1;
        n = Math.max(31, Math.min(401, n | 1));
        double lo = Math.max(r0 - 4 * s, 1e-3 * r0), hi = r0 + 4 * s;
        double[] rc = new double[n], w = new double[n];
        double h = (hi - lo) / (n - 1), sum = 0;
        for (int i = 0; i < n; i++) {
            rc[i] = lo + i * h;
            double z = (rc[i] - r0) / s;
            w[i] = Math.exp(-0.5 * z * z) * ((i == 0 || i == n - 1) ? 0.5 : 1.0);
            sum += w[i];
        }
        for (int i = 0; i < n; i++) w[i] /= sum;
        return new double[][]{rc, w};
    }

    /** particle term &lt;A^2&gt; / S0^2 (no scale), per q */
    public double[] particleShape(double[] p, double[] q) {
        double qmax = 0;
        for (double v : q) qmax = Math.max(qmax, v);
        double[][] nodes = sizeNodes(p, qmax);
        double[] d = steps(p);
        double s0 = norm(p);
        double inv = s0 > 0 ? 1.0 / (s0 * s0) : 0;
        final int nn = nodes[0].length, ns = shells + 1;
        final double[] rr = new double[nn * ns], vv = new double[nn * ns];
        for (int i = 0; i < nn; i++) {
            double[] r = radii(p, nodes[0][i]);
            for (int k = 0; k < ns; k++) {
                rr[i * ns + k] = r[k];
                vv[i * ns + k] = nodes[1][i] > 0 ? d[k] * volume(r[k]) : 0;
            }
        }
        final double[] w = nodes[1];
        final double[] out = new double[q.length];
        java.util.function.IntConsumer body = j -> {
            double qj = q[j], acc = 0;
            for (int i = 0; i < nn; i++) {
                double a = 0;
                int o = i * ns;
                for (int k = 0; k < ns; k++) a += vv[o + k] * sphereAmp(qj * rr[o + k]);
                acc += w[i] * a * a;
            }
            out[j] = acc * inv;
        };
        if ((long) q.length * nn * ns > 20000) java.util.stream.IntStream.range(0, q.length).parallel().forEach(body);
        else for (int j = 0; j < q.length; j++) body.accept(j);
        return out;
    }

    /** unit-height Gaussian peak shape */
    public static double[] peakShape(double[] p, double[] q) {
        double[] out = new double[q.length];
        double w = p[PEAK_W];
        for (int j = 0; j < q.length; j++) {
            double z = (q[j] - p[PEAK_Q]) / w;
            out[j] = Math.exp(-0.5 * z * z);
        }
        return out;
    }

    /** full model intensity */
    public double[] intensity(double[] p, double[] q) {
        double[] part = particleShape(p, q);
        double[] pk = peak ? peakShape(p, q) : null;
        double[] out = new double[q.length];
        for (int j = 0; j < q.length; j++) {
            out[j] = p[SCALE] * part[j] + p[BG];
            if (pk != null) out[j] += p[PEAK_A] * pk[j];
        }
        return out;
    }

    /** {particle term, peak term (or null), background} per q, for plotting the components */
    public double[][] components(double[] p, double[] q) {
        double[] part = particleShape(p, q);
        for (int j = 0; j < q.length; j++) part[j] *= p[SCALE];
        double[] pk = null;
        if (peak) {
            pk = peakShape(p, q);
            for (int j = 0; j < q.length; j++) pk[j] *= p[PEAK_A];
        }
        double[] bg = new double[q.length];
        java.util.Arrays.fill(bg, p[BG]);
        return new double[][]{part, pk, bg};
    }

    /** radial density profile of the mean particle: {r, rho(r)} as a step function out to the buffer */
    public double[][] densityProfile(double[] p) {
        double[] r = radii(p, p[R0]);
        double[] rho = new double[shells + 1];
        rho[0] = p[RHO_C];
        for (int k = 1; k <= shells; k++) rho[k] = p[rhoIndex(k)];
        double outer = r[shells];
        int n = 2 * (shells + 1) + 2;
        double[] x = new double[n], y = new double[n];
        int m = 0;
        double start = 0;
        for (int k = 0; k <= shells; k++) {
            x[m] = start; y[m++] = rho[k];
            x[m] = r[k]; y[m++] = rho[k];
            start = r[k];
        }
        x[m] = outer; y[m++] = 0;
        x[m] = outer * 1.15 + 20; y[m] = 0;
        return new double[][]{x, y};
    }

    /** outer radius of the mean particle, counting every layer whether or not it has any contrast */
    public double outerRadius(double[] p) {
        double[] r = radii(p, p[R0]);
        return r[shells];
    }

    /**
     * Outermost interface that still has a measurable density step. A layer whose contrast is zero (a PEG corona
     * matched to the buffer, say) scatters nothing, so the data cannot see it: the particle looks smaller than
     * {@link #outerRadius}. Returns 0 when nothing has any contrast.
     */
    public double visibleOuterRadius(double[] p) {
        double[] r = radii(p, p[R0]);
        double[] d = steps(p);
        double max = 0;
        for (double v : d) max = Math.max(max, Math.abs(v));
        if (max == 0) return 0;
        for (int k = shells; k >= 0; k--) if (Math.abs(d[k]) > 1e-3 * max) return r[k];
        return 0;
    }

    /** index of the outermost layer with no measurable contrast, or -1 when every layer is visible */
    public int firstInvisibleLayer(double[] p) {
        double[] d = steps(p);
        double max = 0;
        for (double v : d) max = Math.max(max, Math.abs(v));
        if (max == 0) return 0;
        for (int k = shells; k >= 1; k--) if (Math.abs(d[k]) > 1e-3 * max) return k == shells ? -1 : k + 1;
        return 1;
    }

    /** radius of gyration of the mean particle from its density profile (ignores polydispersity) */
    public double radiusOfGyration(double[] p) {
        double[] r = radii(p, p[R0]);
        double[] d = steps(p);
        // A(0) = sum d_k V_k,  Rg^2 = (3/5) sum d_k V_k r_k^2 / sum d_k V_k
        double num = 0, den = 0;
        for (int k = 0; k <= shells; k++) {
            double v = d[k] * volume(r[k]);
            num += v * r[k] * r[k];
            den += v;
        }
        if (Math.abs(den) < 1e-12 * Math.abs(num) || den == 0) return Double.NaN;
        double rg2 = 0.6 * num / den;
        return rg2 > 0 ? Math.sqrt(rg2) : Double.NaN;
    }

    /** true for the bilayer vesicle (water core + inner heads + chains + outer heads + PEG) */
    public boolean isVesicle() { return shells == 4; }

    /** names of the layers, core first */
    public String[] layerNames() {
        switch (shells) {
            case 0: return new String[]{"particle"};
            case 1: return new String[]{"core", "shell"};
            case 2: return new String[]{"core", "inner lipid layer", "headgroups"};
            case 3: return new String[]{"core", "inner lipid layer", "headgroups", "PEG corona"};
            default: return new String[]{"water core", "inner headgroups", "bilayer chains", "outer headgroups", "PEG corona"};
        }
    }

    /**
     * Sign of the density contrast a layer must have when physical constraints are on: +1 above buffer, -1 below,
     * 0 free. LNP: chains of the monolayer below buffer, headgroups and PEG above. Vesicle: headgroups and PEG
     * above, bilayer chains below.
     */
    public int densitySign(int p) {
        if (isVesicle()) {
            if (p == RHO_1 || p == RHO_3 || p == RHO_4) return 1;
            if (p == RHO_2) return -1;
            return 0;
        }
        if (shells >= 2) {
            if (p == RHO_1) return -1;
            if (p == RHO_2 || p == RHO_3) return 1;
        }
        return 0;
    }

    /** template values for this architecture (Trollmann &amp; Boeckmann 2022, Fig. 5 d-f; Li et al. 2025) */
    public double[] templateDefaults() {
        double[] p = defaults();
        if (isVesicle()) {
            // Fig. 5 d: low-pH vesicle, bilayer of protonated ALC-0315, DSPC and cholesterol around a water core
            p[R0] = 250; p[SIGMA] = 50;
            p[T1] = 8; p[T2] = 30; p[T3] = 8; p[T4] = 34;
            p[RHO_C] = 0; p[RHO_1] = 0.085; p[RHO_2] = -0.025; p[RHO_3] = 0.085; p[RHO_4] = 0.003;
        }
        return p;
    }

    /** parameters held fixed by default for this architecture */
    public boolean[] templateFixed() {
        boolean[] f = defaultFixed();
        if (isVesicle()) {
            f = new boolean[NPAR];
            f[RHO_C] = true;     // water core = buffer
            f[RHO_2] = true;     // chain density is the reference of the density scale
            f[T1] = true; f[T3] = true; f[T4] = true; f[RHO_4] = true;
        }
        return f;
    }

    // ------------------------------------------------------------------ presets

    /** starting values in Angstrom, from Li et al. (2025) Table 1 (mRNA-LNP / empty LNP, Comirnaty-like) */
    public static double[] defaults() {
        double[] p = new double[NPAR];
        p[SCALE] = 1;
        p[R0] = 220;
        p[SIGMA] = 26;
        p[T1] = 20;
        p[T2] = 3;
        p[T3] = 40;
        p[RHO_C] = 0.01;
        p[RHO_1] = -0.035;
        p[RHO_2] = 0.085;
        p[RHO_3] = 0.0007;   // Li et al.: rho_3 / rho_1 ~ -0.02
        p[PEAK_A] = 0.01;
        p[PEAK_Q] = 0.127;
        p[PEAK_W] = 0.012;
        p[BG] = 0;
        p[T4] = 34;
        p[RHO_4] = 0.003;
        return p;
    }

    /**
     * Parameters held fixed by default: headgroup and PEG thicknesses (as in Li et al.), the inner-shell density as
     * the unit of the density scale, and the faint corona density (free it for high-quality data).
     */
    public static boolean[] defaultFixed() {
        boolean[] f = new boolean[NPAR];
        f[T2] = true;
        f[T3] = true;
        f[RHO_1] = true;
        f[RHO_3] = true;   // the hydrated PEG corona has a tiny contrast that trades off against R0, rho_c and background
        return f;
    }
}
