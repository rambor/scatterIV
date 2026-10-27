package version4.FormFactors;

/**
 * A shape family whose members are described by d positive parameters (lengths in Angstrom).
 *
 * The fitter works in log-parameters u_k = ln(p_k). For one particle the kernel returns
 *
 *     P(q)       = < F(q r_eff)^2 >_orientation        (normalised form factor, P(0) = 1)
 *     dP_k(q)    = d P(q) / d u_k
 *
 * so each particle represents an equal share of the forward scattering I(0) ("intensity-weighted").
 * Every particle is therefore equally visible to the data, which keeps the forces on all sizes on the
 * same scale and leaves no region of parameter space where mass can hide for free. Volume-fraction
 * and number distributions are recovered afterwards by re-weighting each particle with 1/V and 1/V^2.
 *
 * Implementations must be stateless apart from the shared immutable {@link FormFactorTable};
 * per-call scratch space is passed in so kernels can be called from many threads at once.
 */
public interface ShapeKernel {

    /** number of fitted parameters */
    int dim();

    /** human readable parameter names, e.g. {"R"} or {"R_eq", "R_polar"} */
    String[] parameterNames();

    /** volume of the particle for log-parameters u[off .. off+dim) */
    double volume(double[] u, int off);

    /**
     * The constant by which the raw orientation-averaged intensity was divided to make P(0) = 1 (i.e. the
     * particle's forward-scattering power, contrast^2 * volume^2 for a homogeneous body). Used to convert the
     * intensity-weighted particle ensemble to volume-fraction (weight V / N) and number (weight 1 / N)
     * distributions. Default: V^2 (unit contrast).
     */
    default double forwardScatteringPower(double[] u, int off) {
        double v = volume(u, off);
        return v * v;
    }

    // ---- discrete species (e.g. empty vs filled cores). Single-species kernels need not override these.

    /** number of discrete particle species the kernel distinguishes (e.g. core contents) */
    default int speciesCount() { return 1; }

    /** name of species s */
    default String speciesName(int s) { return "all"; }

    /** evaluate for a given species; default ignores the species */
    default void evaluate(int species, double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch) {
        evaluate(u, uOff, q, P, pOff, dP, dOff, scratch);
    }

    /** forward-scattering power for a given species; default ignores the species */
    default double forwardScatteringPower(int species, double[] u, int off) { return forwardScatteringPower(u, off); }

    /** largest effective radius any particle inside the bounds can have (sizes the lookup table) */
    double maxEffectiveRadius(double[] upperBounds);

    /** scratch doubles needed by one evaluate call */
    int scratchSize();

    /**
     * True when the parameters are interchangeable (e.g. the three semi-axes of a triaxial ellipsoid);
     * the fitter then keeps them sorted ascending, which removes label-switching symmetry.
     */
    boolean sortParameters();

    /**
     * @param u       log-parameters, read from u[uOff .. uOff+dim)
     * @param q       q-values (1/Angstrom), ascending
     * @param P       output, P[pOff + j]
     * @param dP      output, dP[dOff + k*nq + j] = dP(q_j)/du_k
     * @param scratch scratch array of length >= scratchSize()
     */
    void evaluate(double[] u, int uOff, double[] q, double[] P, int pOff, double[] dP, int dOff, double[] scratch);

    /** short label used in output headers */
    String label();
}
