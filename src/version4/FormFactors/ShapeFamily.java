package version4.FormFactors;

/** Shape families available to the Langevin fitter. */
public enum ShapeFamily {
    SPHERE,
    /** ellipsoid of revolution (a,a,c): covers both prolate (c>a) and oblate (c<a) */
    SPHEROID,
    TRIAXIAL_ELLIPSOID,
    /** core spheroid (a,a,c) inside a shell (a+t, a+t, c+t); parameters a, c, t */
    CORESHELL_SPHEROID;

    public int dim() {
        switch (this) {
            case SPHERE: return 1;
            case SPHEROID: return 2;
            default: return 3;
        }
    }

    /** true when parameters 0 and 1 are the spheroid (core) axes a and c, so the aspect-ratio window applies */
    public boolean hasSpheroidAxes() {
        return this == SPHEROID || this == CORESHELL_SPHEROID;
    }

    public ShapeKernel create(FormFactorTable table, double qmax, LangevinSettings set) {
        switch (this) {
            case SPHERE: return new version4.FormFactors.SphereKernel(table);
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
