package version4.FormFactors;

/** Shape families available to the Langevin fitter. */
public enum ShapeFamily {
    SPHERE,
    /** ellipsoid of revolution (a,a,c): covers both prolate (c>a) and oblate (c<a) */
    SPHEROID,
    TRIAXIAL_ELLIPSOID,
    /** core spheroid (a,a,c) inside a shell (a+t, a+t, c+t); parameters a, c, t */
    CORESHELL_SPHEROID,
    /** Gaussian polymer chain (Debye), parameter Rg */
    GAUSSIAN_CHAIN,
    /** excluded-volume chain (Hammouda), parameter Rg; Flory exponent from LangevinSettings.chainNu */
    EXCLUDED_VOLUME_CHAIN,
    /** wormlike chain with excluded volume (SasView flexible_cylinder), parameter contour length L */
    WORMLIKE_CHAIN,
    /** block-copolymer micelle (Pedersen-Gerstenberg), parameters R_core and Rg_brush */
    POLYMER_MICELLE;

    public int dim() {
        switch (this) {
            case SPHERE: case GAUSSIAN_CHAIN: case EXCLUDED_VOLUME_CHAIN: case WORMLIKE_CHAIN: return 1;
            case SPHEROID: case POLYMER_MICELLE: return 2;
            default: return 3;
        }
    }

    /** true when parameters 0 and 1 are the spheroid (core) axes a and c, so the aspect-ratio window applies */
    public boolean hasSpheroidAxes() {
        return this == SPHEROID || this == CORESHELL_SPHEROID;
    }

    /** true for the polymer families (chains and micelles): "volume" weighting means mass weighting */
    public boolean isPolymer() {
        return this == GAUSSIAN_CHAIN || this == EXCLUDED_VOLUME_CHAIN || this == WORMLIKE_CHAIN || this == POLYMER_MICELLE;
    }

    /** largest q*r the sphere-amplitude table must cover */
    public double tableRadius(double[] maxParams) {
        switch (this) {
            case GAUSSIAN_CHAIN: case EXCLUDED_VOLUME_CHAIN: case WORMLIKE_CHAIN: return 1.0;   // table unused
            case POLYMER_MICELLE: return maxParams[0];
            case CORESHELL_SPHEROID: return Math.max(maxParams[0], maxParams[1]) + maxParams[2];
            default: {
                double r = 0;
                for (double v : maxParams) r = Math.max(r, v);
                return r;
            }
        }
    }

    public ShapeKernel create(FormFactorTable table, double qmax, LangevinSettings set) {
        return create(table, qmax, set, null, null);
    }

    /** @param minParams, maxParams parameter bounds (needed by WORMLIKE_CHAIN, which tabulates over L) */
    public ShapeKernel create(FormFactorTable table, double qmax, LangevinSettings set, double[] minParams, double[] maxParams) {
        switch (this) {
            case GAUSSIAN_CHAIN: return new ChainKernel(0.5);
            case EXCLUDED_VOLUME_CHAIN: return new ChainKernel(set.chainNu);
            case WORMLIKE_CHAIN:
                if (minParams == null || maxParams == null) throw new IllegalArgumentException("WORMLIKE_CHAIN needs bounds");
                return new WormlikeKernel(set.kuhnLength, set.chainRadius, minParams[0], maxParams[0]);
            case POLYMER_MICELLE:
                return new MicelleKernel(table, set.micelleCoreBlockVolume, set.micelleCoronaBlockVolume, set.solventDensity,
                        set.micelleCoreDensity, set.micelleCoronaDensity, set.micellePenetration, set.micellePacking);
            case SPHERE: return new SphereKernel(table);
            case SPHEROID: return new SpheroidKernel(table, qmax);
            case CORESHELL_SPHEROID: {
                ShapeKernel k = new CoreShellSpheroidKernel(table, qmax, set.solventDensity, set.shellDensity,
                        set.coreDensities != null && set.coreDensities.length > 0 ? set.coreDensities : new double[]{set.coreDensity},
                        set.speciesNames);
                if (set.sharedGeometry && k.speciesCount() == 2) {
                    if (!(set.fixedSpeciesFraction >= 0 && set.fixedSpeciesFraction <= 1))
                        throw new IllegalArgumentException("sharedGeometry needs fixedSpeciesFraction (or use SpeciesProfile to estimate it)");
                    return new MixedSpeciesKernel(k, set.fixedSpeciesFraction);
                }
                return k;
            }
            default: return new TriaxialKernel(table, qmax);
        }
    }
}
