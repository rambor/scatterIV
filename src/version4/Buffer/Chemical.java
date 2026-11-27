package version4.Buffer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One buffer ingredient in the library: what it is called, how it ionizes and what it adds to the
 * solvent's electron density. Built-in entries are in {@link BufferLibrary}; a beamline or user can
 * add more in {@code buffer_library.json} in Scatter's settings folder, using the same field names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Chemical {

    public enum Kind {
        /** weak acid/base with pKa values: its charge depends on pH, and it needs a counter-ion */
        BUFFER,
        /** fully dissociated into the listed ions */
        SALT,
        /** uncharged at working pH (glycerol, sucrose, DTT, ...) */
        NEUTRAL
    }

    /** an ion released by a salt, or a buffer's counter-ion */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Ion {
        @JsonProperty("symbol") public String symbol;
        @JsonProperty("charge") public int charge;
        /** ions per formula unit */
        @JsonProperty("count") public int count = 1;
        @JsonProperty("electrons") public int electrons;
        /** partial molar volume in water, cm^3/mol (infinite dilution) */
        @JsonProperty("molar_volume") public double molarVolume;

        public Ion() { }

        public Ion(String symbol, int charge, int count, int electrons, double molarVolume) {
            this.symbol = symbol;
            this.charge = charge;
            this.count = count;
            this.electrons = electrons;
            this.molarVolume = molarVolume;
        }

        public Ion times(int n) { return new Ion(symbol, charge, count*n, electrons, molarVolume); }
    }

    @JsonProperty("name") public String name;
    /** lower-case names it is recognized by; the name itself is always recognized */
    @JsonProperty("aliases") public List<String> aliases = new ArrayList<>();
    @JsonProperty("kind") public Kind kind = Kind.NEUTRAL;
    /** formula of the neutral compound (buffers: the uncharged/zwitterionic form as sold), for its electrons */
    @JsonProperty("formula") public String formula;
    @JsonProperty("molar_mass") public double molarMass;

    // buffers
    /** pKa values at 25 C, lowest first */
    @JsonProperty("pKa") public double[] pKa = new double[0];
    /** d(pKa)/dT per degree, one per pKa (or a single value for all) */
    @JsonProperty("dpKa_dT") public double[] dpKadT = new double[0];
    /** charge of the fully protonated species; each pKa removes one proton */
    @JsonProperty("charge_protonated") public int chargeProtonated;
    /** counter-ion assumed when the titrant is not given (Cl- for amines titrated with HCl, Na+ for acids) */
    @JsonProperty("counter_ion") public String counterIon;

    // salts
    @JsonProperty("ions") public List<Ion> ions = new ArrayList<>();

    // all
    /** partial molar volume in water, cm^3/mol; NaN = estimate from the mass */
    @JsonProperty("molar_volume") public double molarVolume = Double.NaN;
    /** density of the pure liquid, g/mL, for %(v/v); NaN if it is a solid */
    @JsonProperty("density") public double density = Double.NaN;
    /** what a bare "%" means for this chemical */
    @JsonProperty("percent_unit") public String percentUnit = "%(w/v)";
    /** critical micelle concentration in water, mol/L (detergents); NaN if not a detergent */
    @JsonProperty("cmc_M") public double cmcMolar = Double.NaN;
    /** a note shown when it is used, e.g. that its charge is not modelled */
    @JsonProperty("note") public String note;

    public Chemical() { }

    Chemical(String name, Kind kind, String formula, double molarMass, String... aliases) {
        this.name = name;
        this.kind = kind;
        this.formula = formula;
        this.molarMass = molarMass;
        for (String a : aliases) this.aliases.add(a.toLowerCase(Locale.ROOT));
    }

    Chemical buffer(int chargeProtonated, String counterIon, double[] pKa, double... dpKadT) {
        this.kind = Kind.BUFFER;
        this.chargeProtonated = chargeProtonated;
        this.counterIon = counterIon;
        this.pKa = pKa;
        this.dpKadT = dpKadT;
        return this;
    }

    Chemical ions(Ion... list) {
        this.kind = Kind.SALT;
        this.ions = new ArrayList<>(Arrays.asList(list));
        return this;
    }

    Chemical volume(double cm3PerMol) { this.molarVolume = cm3PerMol; return this; }

    Chemical liquid(double gPerMl) { this.density = gPerMl; this.percentUnit = "%(v/v)"; return this; }

    Chemical note(String n) { this.note = n; return this; }

    /** a detergent: CMC in mM, partial specific volume in mL/g */
    Chemical detergent(double cmcMilliMolar, double specificVolume) {
        this.cmcMolar = cmcMilliMolar*1e-3;
        this.molarVolume = specificVolume*molarMass;
        return this;
    }

    /** pKa i at temperature t (C) */
    public double pKaAt(int i, double tCelsius) {
        double slope = (dpKadT.length == 0) ? 0 : dpKadT[Math.min(i, dpKadT.length - 1)];
        return pKa[i] + slope*(tCelsius - 25.0);
    }

    /** electrons in one formula unit of the neutral compound, from its formula */
    @JsonIgnore
    public int electrons() {
        return Formula.electrons(formula);
    }

    /** partial molar volume, cm^3/mol: the stored value or an estimate of 0.62 cm^3/g x mass */
    @JsonIgnore
    public double molarVolumeOrEstimate() {
        if (!Double.isNaN(molarVolume)) return molarVolume;
        if (kind == Kind.SALT && !ions.isEmpty()) {
            double v = 0;
            for (Ion ion : ions) v += ion.count*ion.molarVolume;
            return v;
        }
        return 0.62*molarMass;
    }

    @JsonIgnore
    public boolean molarVolumeIsEstimated() {
        return Double.isNaN(molarVolume) && !(kind == Kind.SALT && !ions.isEmpty());
    }

    @Override
    public String toString() { return name; }
}
