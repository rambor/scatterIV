package version4.FormFactors;

/**
 * Settings for {@link LangevinFitter}. Public fields with sensible defaults, so the GUI or a
 * script can change just what it needs.
 */
public class LangevinSettings {

    /** number of particles (each = equal share of scattering volume fraction) */
    public int particles = 1000;

    /** quench: steps spent cooling from T0 to ~0 (and measuring the best attainable chi^2) */
    public int annealSteps = 3000;
    /** steps at (auto-controlled or fixed) temperature before sampling */
    public int equilibrationSteps = 1500;
    /** steps during which histograms / fit are accumulated at frozen temperature */
    public int sampleSteps = 2000;
    /** accumulate every n-th step while sampling */
    public int sampleEvery = 5;

    /**
     * Cap on the deterministic move of any particle per step, in ln units (0.03 = 3 % change of a length).
     * Only active far from the fit; the step itself comes from a stability estimate.
     */
    public double driftStep = 0.03;
    /** cap on the noise standard deviation per step, ln units */
    public double noiseStep = 0.03;

    /** fit a flat background in I(q) (in your q*I(q) transform this is the b*q column) */
    public boolean fitBackground = false;

    /**
     * true  : choose temperature by the discrepancy principle so that the reduced chi^2 of the
     *         ensemble fit approaches {@link #targetChi2}. The temperature found is reported.
     * false : use {@link #temperature} (absolute units of chi^2/2).
     */
    public boolean autoTemperature = true;
    public double targetChi2 = 1.0;
    /**
     * Auto mode targets max(targetChi2, chi2_min + chi2Tolerance * sqrt(2/dof)), where chi2_min is the best
     * reduced chi^2 reached during the quench: the smoothest distribution statistically indistinguishable
     * from the best fit.
     */
    public double chi2Tolerance = 1.0;
    public double temperature = Double.NaN;
    /** per-step gain of the log-temperature controller (max change of ln T per step) */
    public double controllerGain = 0.02;

    /**
     * true  : underdamped (kinetic) Langevin with momentum - same stationary distribution, much faster
     *         along weakly determined directions of the distribution.
     * false : overdamped Langevin (plain noisy gradient flow).
     */
    public boolean underdamped = true;
    /**
     * Per-particle preconditioning (mobility = 1 / own Fisher information) during the quench. Essential when
     * the error weights span many decades (relative errors over a wide q range); harmless otherwise.
     */
    public boolean preconditionQuench = true;
    /**
     * Error-floor continuation during the quench: start with an extra error of errorFloorStart * max(I),
     * decreasing to zero by 2/3 of the quench. Prevents the profiled scale from collapsing when relative
     * errors span many decades. 0 disables.
     */
    public double errorFloorStart = 0.01;
    /**
     * During the quench, every 10 steps replace particles stranded against a parameter bound with an
     * above-median potential (at most 5 % per call) by copies of good particles. Not used while sampling.
     */
    public boolean quenchResample = true;
    /** gamma*h, fraction of velocity lost to friction per step (smaller = more momentum) */
    public double frictionPerStep = 0.2;

    /**
     * Birth-death (Fisher-Rao) moves: particles with above-average potential are replaced by copies of
     * others. Lets mass move between separated modes (e.g. the two peaks of a bimodal distribution)
     * without having to diffuse through the empty gap. Same stationary distribution.
     */
    public boolean birthDeath = false;
    /** Fisher-Rao rate alpha: particle i is replaced with probability 1 - exp(-alpha |beta_i| tau) */
    public double birthDeathRate = 1e3;
    /** apply birth-death every n steps (the KDE for the entropy term costs O(N^2)) */
    public int birthDeathEvery = 5;
    /** ln-space jitter given to a cloned particle so clones separate */
    public double birthDeathJitter = 0.005;

    /**
     * Spheroids only: optional window on the aspect ratio c/a. Orientation-averaged SAXS of polydisperse
     * spheroids is largely degenerate between shape and size spread, so prior knowledge is often needed:
     *   prolate : aspectRatioMin = 1.2 (say), aspectRatioMax = 10
     *   oblate  : aspectRatioMin = 0.1,       aspectRatioMax = 1/1.2
     * Leave at 0 / +infinity for no constraint.
     */
    public double aspectRatioMin = 0.0;
    public double aspectRatioMax = Double.POSITIVE_INFINITY;

    /**
     * Distribution-shape prior. When set, the flat reference of the entropy term is replaced by a
     * K-component Gaussian mixture in ln-space, re-fitted to the particles by EM as the fit runs:
     * the result is the distribution closest to K Gaussian-like modes that still fits the data.
     *   priorAxes       : parameter indices the prior acts on, e.g. {1} = spheroid polar axis only,
     *                     {0,1} = joint (a,c) with correlation; null = no prior (flat, default)
     *   priorComponents : 1 = unimodal, 2 = bimodal, ...
     *   priorStrength   : lambda. With an adaptive reference, lambda = 1 is neutral about the WIDTH of
     *                     each mode (spreading the whole distribution does not change KL(mu||G)), so the
     *                     width drifts wherever the data allow. lambda < 1 keeps a mild max-entropy push,
     *                     giving the broadest Gaussian-like modes consistent with the data (conservative).
     *                     0.9 recovered the true widths in the spheroid test; widths of weakly determined
     *                     axes remain lambda-sensitive, so check 0.75..1 before quoting a width.
     */
    public int[] priorAxes = null;
    public int priorComponents = 1;
    public double priorStrength = 0.9;
    public int priorUpdateEvery = 5;
    /** floor on each component's ln-width so a mode cannot collapse to a spike */
    public double priorMinSd = 0.02;

    /**
     * Data preparation (see DataPreparation).
     *   calibrateErrors  : rescale sigma by sqrt(chi^2_red) of a band-limited (Moore/Shannon P(r)) fit, so the
     *                      chi^2-based temperature rule works even when reported errors are mis-scaled
     *   rebin            : merge points (inverse-variance) to pointsPerChannel per Shannon channel pi/Dmax
     *   dmax             : maximum particle dimension in Angstrom if known (e.g. from your P(r)); NaN = estimate
     */
    public boolean calibrateErrors = true;
    public boolean rebin = true;
    public double pointsPerChannel = 4.0;
    public double dmax = Double.NaN;

    /**
     * Core-shell spheroid (CORESHELL_SPHEROID): scattering length densities (any consistent units; only ratios
     * matter) and an optional shell-thickness window. Hollow shell: coreDensity = solventDensity.
     *   shellThickness > 0 : thickness bounds become shellThickness * (1 +- shellTolerance), overriding the
     *                        third min/max parameter
     */
    public double solventDensity = 9.42;   // water, 1e-6 / A^2 (X-ray)
    public double shellDensity = 12.0;
    public double coreDensity = 10.5;
    public double shellThickness = Double.NaN;
    public double shellTolerance = 0.10;
    /**
     * Mixture of core contents, e.g. {9.42, 14.5} = empty (buffer) and nucleic-acid-filled. Each particle carries
     * one species; species are Gibbs-sampled with the geometry and their fractions are reported. null = single
     * species with coreDensity.
     */
    public double[] coreDensities = null;
    public String[] speciesNames = null;
    /** Gibbs update of particle species every n steps; fraction of particles reconsidered per update */
    public int speciesUpdateEvery = 5;
    public double speciesFlipRate = 0.01;
    /**
     * Two species only: hold the NUMBER fraction of species 0 at this value (0..1) with a stiff penalty
     * (fractionPenalty/2)(f - f*)^2 acting on labels and geometry. Used by SpeciesProfile. NaN = free.
     */
    public double fixedSpeciesFraction = Double.NaN;
    /**
     * Two species that share ONE geometry distribution (empty and filled capsids: same shell, contents independent of
     * size). Each particle is then a number mixture p*empty + (1-p)*filled with p = fixedSpeciesFraction; estimate p
     * with SpeciesProfile. Without this, species-specific geometry can trade size against filling and the number
     * fraction is not identifiable.
     */
    public boolean sharedGeometry = false;
    public double fractionPenalty = 1e6;

    /** independent runs with different seeds; spread between them gives error bars on the distribution */
    public int replicas = 1;
    public long seed = 20161027L;
    public int threads = Runtime.getRuntime().availableProcessors();

    // ---- polymers (ShapeFamily GAUSSIAN_CHAIN, EXCLUDED_VOLUME_CHAIN, WORMLIKE_CHAIN, POLYMER_MICELLE)

    /** Flory exponent of EXCLUDED_VOLUME_CHAIN (0.5 theta solvent / Gaussian, 0.588 good solvent, 1/3 collapsed) */
    public double chainNu = 0.588;
    /** WORMLIKE_CHAIN: Kuhn length b = 2 x persistence length (Angstrom), the same for every chain */
    public double kuhnLength = 100.0;
    /** WORMLIKE_CHAIN: cross-section radius of the chain (Angstrom); 0 = thin chain */
    public double chainRadius = 0.0;
    /** POLYMER_MICELLE: volume of one core block and one corona block (Angstrom^3) */
    public double micelleCoreBlockVolume = 20000, micelleCoronaBlockVolume = 30000;
    /** POLYMER_MICELLE: SLDs of core block and corona block (solvent: solventDensity) */
    public double micelleCoreDensity = 8.0, micelleCoronaDensity = 10.5;
    /** POLYMER_MICELLE: chain centres at R + d Rg (d ~ 1: chains do not penetrate the core) */
    public double micellePenetration = 1.0;
    /** POLYMER_MICELLE: polymer volume fraction in the core; N_agg = packing (4/3 pi R^3) / core block volume */
    public double micellePacking = 1.0;
    /** POLYMER_MICELLE: restrict the corona chain Rg to brushRg (1 +- brushTolerance); NaN = use the bounds */
    public double brushRg = Double.NaN, brushTolerance = 0.2;

    public int histogramBins = 60;
    /** lookup table spacing in x = qR */
    public double tableStep = 1e-3;

    public int totalSteps() { return annealSteps + equilibrationSteps + sampleSteps; }

    /** field-by-field copy (arrays are cloned) */
    public LangevinSettings copy() {
        LangevinSettings c = new LangevinSettings();
        try {
            for (java.lang.reflect.Field f : LangevinSettings.class.getFields()) {
                Object v = f.get(this);
                if (v instanceof double[]) v = ((double[]) v).clone();
                else if (v instanceof int[]) v = ((int[]) v).clone();
                else if (v instanceof String[]) v = ((String[]) v).clone();
                f.set(c, v);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return c;
    }
}
