package version4.Buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ionic strength, Debye length and solvent electron density of a parsed buffer.
 *
 * <p><b>Ionic strength</b> I = 1/2 sum c_i z_i^2 over every ion: salts fully dissociated, buffers
 * split into their protonation states at the given pH and temperature, and the counter-ion each
 * buffer needs to be electrically neutral (Cl- for a buffer that is net positive, e.g. Tris-HCl;
 * Na+ for one that is net negative, e.g. HEPES-NaOH; or the titrant if one is given). The pKa values
 * are corrected to the working ionic strength with the Davies equation, solved self-consistently
 * (phosphate's pKa2 7.20 becomes about 6.9 at 0.1 M). H+ and OH- are neglected.</p>
 *
 * <p><b>Debye length</b> lambda = sqrt(eps0 eps_r kB T / (2 N_A e^2 I)), with the permittivity of
 * water at T (3.04/sqrt(I) Å at 25 C). Co-solvents that lower the permittivity (glycerol, DMSO)
 * are not taken into account.</p>
 *
 * <p><b>Electron density</b> of the buffer from the electrons of every solute and of the water
 * left after the solutes' partial molar volumes are taken out of 1 L; and the contrast of a protein
 * (0.44 e/A^3) in it, relative to the contrast in pure water, which scales I(0).</p>
 */
public final class BufferCalculator {

    public static final double PROTEIN_ELECTRON_DENSITY = 0.44;   // e/A^3

    private static final double EPS0 = 8.8541878128e-12, KB = 1.380649e-23, NA = 6.02214076e23, E = 1.602176634e-19;

    /** a counter-ion added for a buffer */
    public static final class CounterIon {
        public final String ion, forBuffer;
        public final double molar;
        CounterIon(String ion, String forBuffer, double molar) { this.ion = ion; this.forBuffer = forBuffer; this.molar = molar; }
        @Override public String toString() { return String.format(Locale.ROOT, "%s %.3g mM (for %s)", ion, molar*1e3, forBuffer); }
    }

    /** a buffer's state at the working pH */
    public static final class BufferState {
        public final String name;
        public final double pKaApparent, meanCharge, molar;
        BufferState(String name, double pKaApparent, double meanCharge, double molar) {
            this.name = name; this.pKaApparent = pKaApparent; this.meanCharge = meanCharge; this.molar = molar;
        }
    }

    public static final class Result {
        /** mol/L */
        public double ionicStrength;
        /** Angstrom; infinite for I = 0 */
        public double debyeLengthA;
        /** e/A^3 */
        public double electronDensity;
        /** e/A^3 of pure water at the same temperature */
        public double waterElectronDensity;
        /** (rho_protein - rho_buffer)^2 / (rho_protein - rho_water)^2: factor on I(0) */
        public double contrastRelativeToWater;
        public double pH, temperatureC;
        public boolean pHAssumed;
        public final List<CounterIon> counterIons = new ArrayList<>();
        public final List<BufferState> buffers = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        /** "ionic strength 0.162 M, Debye length 7.6 Å, ..." */
        public String summary() {
            return String.format(Locale.ROOT, "ionic strength %.3g M, Debye length %s, buffer %.4f e/Å³ (I(0) x %.3f vs water)",
                    ionicStrength, Double.isInfinite(debyeLengthA) ? "unbounded" : String.format(Locale.ROOT, "%.1f \u00c5", debyeLengthA),
                    electronDensity, contrastRelativeToWater);
        }
    }

    private BufferCalculator() { }

    /** temperature in C: the buffer's own, else {@code defaultC}, else 25 */
    public static Result compute(BufferParser.Parsed p, Double defaultC) {
        Result r = new Result();
        r.temperatureC = p.temperatureC != null ? p.temperatureC : (defaultC != null ? defaultC : 25.0);
        double tK = r.temperatureC + 273.15;

        // pH: given, or assumed at the first buffer's nearest pKa
        Chemical firstBuffer = null;
        for (BufferParser.Item it : p.items) if (it.chemical != null && it.chemical.kind == Chemical.Kind.BUFFER && it.molar() != null) { firstBuffer = it.chemical; break; }
        if (p.pH != null) {
            r.pH = p.pH;
        } else if (firstBuffer != null) {
            double best = firstBuffer.pKaAt(0, r.temperatureC);
            for (int i = 0; i < firstBuffer.pKa.length; i++) {
                double v = firstBuffer.pKaAt(i, r.temperatureC);
                if (Math.abs(v - 7) < Math.abs(best - 7)) best = v;
            }
            r.pH = best;
            r.pHAssumed = true;
            r.warnings.add(String.format(Locale.ROOT, "no pH given: assumed %.2f (pKa of %s)", best, firstBuffer.name));
        } else {
            r.pH = 7.0;
        }

        double eps = waterPermittivity(r.temperatureC);
        double davA = 1.82483e6*Math.sqrt(waterDensity(r.temperatureC))/Math.pow(eps*tK, 1.5);

        // fixed-point iteration on I (speciation depends on I through the activity correction)
        double I = 0;
        for (int iter = 0; iter < 60; iter++) {
            double next = ionicStrength(p, r.pH, r.temperatureC, davA, I, null);
            if (Math.abs(next - I) < 1e-10) { I = next; break; }
            I = 0.5*(I + next);
        }
        r.ionicStrength = ionicStrength(p, r.pH, r.temperatureC, davA, I, r);
        if (r.ionicStrength > 0.5) r.warnings.add("ionic strength above 0.5 M: the activity correction (Davies) is approximate there");

        r.debyeLengthA = r.ionicStrength > 0
                ? Math.sqrt(EPS0*eps*KB*tK/(2*NA*E*E*r.ionicStrength*1000.0))*1e10
                : Double.POSITIVE_INFINITY;

        electronDensity(p, r);
        for (BufferParser.Item it : p.items) {
            Double molar = it.molar();
            if (it.chemical == null || molar == null || Double.isNaN(it.chemical.cmcMolar)) continue;
            if (molar > it.chemical.cmcMolar) {
                r.warnings.add(String.format(Locale.ROOT, "%s is %.0f x its CMC (%.2g mM): micelles scatter, so the buffer must match the sample exactly",
                        it.chemical.name, molar/it.chemical.cmcMolar, it.chemical.cmcMolar*1e3));
            } else {
                r.warnings.add(String.format(Locale.ROOT, "%s is below its CMC (%.2g mM in water; lower in salt)", it.chemical.name, it.chemical.cmcMolar*1e3));
            }
        }
        for (BufferState b : r.buffers) {
            // only buffers used as buffers (>= 5 mM; EDTA at 1 mM is a chelator, not the pH buffer)
            if (Math.abs(r.pH - b.pKaApparent) > 1.0 && !r.pHAssumed && b.molar >= 5e-3) {
                r.warnings.add(String.format(Locale.ROOT, "%s buffers poorly at pH %.2f (pKa %.2f)", b.name, r.pH, b.pKaApparent));
            }
        }
        return r;
    }

    /** I for the given I used in the activity correction; fills r (buffers, counter-ions) if not null */
    private static double ionicStrength(BufferParser.Parsed p, double pH, double tC, double davA, double I, Result r) {
        double sum = 0;   // sum c z^2
        double f = Math.sqrt(I)/(1 + Math.sqrt(I)) - 0.3*I;
        for (BufferParser.Item it : p.items) {
            Chemical c = it.chemical;
            Double molar = it.molar();
            if (c == null || molar == null || molar <= 0) continue;
            if (c.kind == Chemical.Kind.SALT) {
                for (Chemical.Ion ion : c.ions) sum += molar*ion.count*ion.charge*ion.charge;
            } else if (c.kind == Chemical.Kind.BUFFER && c.pKa.length > 0) {
                int n = c.pKa.length;
                double[] pKaApp = new double[n];
                for (int k = 0; k < n; k++) {
                    int z = c.chargeProtonated - k;           // charge of the acid in step k
                    pKaApp[k] = c.pKaAt(k, tC) + davA*f*(2*z - 1);
                }
                // fractions of species j = 0..n (j protons removed), charge z0 - j
                double[] logw = new double[n + 1];
                for (int j = 1; j <= n; j++) logw[j] = logw[j - 1] + (pH - pKaApp[j - 1]);
                double max = Double.NEGATIVE_INFINITY;
                for (double v : logw) max = Math.max(max, v);
                double norm = 0;
                double[] a = new double[n + 1];
                for (int j = 0; j <= n; j++) { a[j] = Math.pow(10, logw[j] - max); norm += a[j]; }
                double zMean = 0, z2 = 0;
                for (int j = 0; j <= n; j++) {
                    a[j] /= norm;
                    int z = c.chargeProtonated - j;
                    zMean += a[j]*z;
                    z2 += a[j]*z*z;
                }
                sum += molar*z2;
                // counter-ion for neutrality: anion if the buffer is net positive, cation if negative
                Chemical.Ion counter;
                if (zMean > 0) counter = (it.titrant != null && it.titrant.charge < 0) ? it.titrant : BufferLibrary.CL;
                else counter = (it.titrant != null && it.titrant.charge > 0) ? it.titrant : BufferLibrary.NA;
                double nCounter = molar*Math.abs(zMean)/Math.abs(counter.charge);
                sum += nCounter*counter.charge*counter.charge;
                if (r != null) {
                    int nearest = 0;
                    for (int k = 1; k < n; k++) if (Math.abs(pKaApp[k] - pH) < Math.abs(pKaApp[nearest] - pH)) nearest = k;
                    r.buffers.add(new BufferState(c.name, pKaApp[nearest], zMean, molar));
                    if (nCounter > 1e-12) r.counterIons.add(new CounterIon(counter.symbol, c.name, nCounter));
                    if (it.titrant != null && Math.signum(-it.titrant.charge) != Math.signum(zMean) && Math.abs(zMean) > 1e-6) {
                        r.warnings.add(c.name + ": a " + it.titrantText + " titrant cannot balance its charge at this pH; "
                                + counter.symbol + " used");
                    }
                }
            }
        }
        return 0.5*sum;
    }

    /** electrons of water and solutes in 1 L (partial molar volumes taken out of the water) */
    private static void electronDensity(BufferParser.Parsed p, Result r) {
        double soluteVolume = 0;     // cm^3 per L
        double soluteElectrons = 0;  // mol electrons per L
        boolean estimated = false;
        for (BufferParser.Item it : p.items) {
            Chemical c = it.chemical;
            Double molar = it.molar();
            if (c == null || molar == null || molar <= 0) continue;
            if (c.kind == Chemical.Kind.SALT && !c.ions.isEmpty()) {
                for (Chemical.Ion ion : c.ions) {
                    soluteElectrons += molar*ion.count*ion.electrons;
                }
                soluteVolume += molar*c.molarVolumeOrEstimate();
            } else {
                soluteElectrons += molar*c.electrons();
                soluteVolume += molar*c.molarVolumeOrEstimate();
                if (c.molarVolumeIsEstimated() && molar > 0.05) estimated = true;
            }
        }
        for (CounterIon ci : r.counterIons) {
            Chemical.Ion ion = ionBySymbol(ci.ion);
            if (ion == null) continue;
            soluteElectrons += ci.molar*ion.electrons;
            soluteVolume += ci.molar*ion.molarVolume;
        }
        double rhoW = waterDensity(r.temperatureC);
        double waterMol = Math.max(0, 1000.0 - soluteVolume)*rhoW/18.015;
        r.waterElectronDensity = 1000.0*rhoW/18.015*10*NA/1e27;
        r.electronDensity = (waterMol*10 + soluteElectrons)*NA/1e27;
        double dW = PROTEIN_ELECTRON_DENSITY - r.waterElectronDensity, dB = PROTEIN_ELECTRON_DENSITY - r.electronDensity;
        r.contrastRelativeToWater = (dB*dB)/(dW*dW);
        if (estimated) r.warnings.add("electron density uses an estimated partial molar volume for at least one component above 50 mM");
    }

    private static Chemical.Ion ionBySymbol(String s) {
        for (Chemical.Ion ion : new Chemical.Ion[]{BufferLibrary.NA, BufferLibrary.K, BufferLibrary.LI, BufferLibrary.NH4,
                BufferLibrary.CL, BufferLibrary.ACETATE}) {
            if (ion.symbol.equals(s)) return ion;
        }
        return null;
    }

    /** relative permittivity of water, Malmberg & Maryott (1956), 0-100 C */
    public static double waterPermittivity(double tC) {
        return 87.740 - 0.40008*tC + 9.398e-4*tC*tC - 1.410e-6*tC*tC*tC;
    }

    /** density of water, g/mL, Kell (1975) */
    public static double waterDensity(double tC) {
        double t = tC;
        double num = 999.83952 + 16.945176*t - 7.9870401e-3*t*t - 46.170461e-6*t*t*t + 105.56302e-9*Math.pow(t, 4) - 280.54253e-12*Math.pow(t, 5);
        return num/(1 + 16.879850e-3*t)/1000.0;
    }
}
