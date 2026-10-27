package version4.FormFactors;

/** Shape families available to the Langevin fitter. */
public enum ShapeFamily {
    SPHERE,
    /** ellipsoid of revolution (a,a,c): covers both prolate (c>a) and oblate (c<a) */
    SPHEROID,
    TRIAXIAL_ELLIPSOID;

    public int dim() {
        switch (this) {
            case SPHERE: return 1; // searches radii
            case SPHEROID: return 2; // searches 2 dimensions for prolate like objects
            default: return 3;
        }
    }

    public ShapeKernel create(FormFactorTable table, double qmax) {
        switch (this) {
            case SPHERE: return new SphereKernel(table);
            case SPHEROID: return new SpheroidKernel(table, qmax);
            default: return new TriaxialKernel(table, qmax);
        }
    }
}
