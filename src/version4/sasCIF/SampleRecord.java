package version4.sasCIF;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What was in the sample: the buffer, its temperature and the protein concentration, with where each
 * value came from and how certain it is. Stored in the SasObject JSON under {@code "sample_record"},
 * so it travels in *.sec headers and at the end of every *.dat file Scatter writes, and is read back
 * when such a file is loaded.
 *
 * <p>Units are fixed and part of each field name (mg/mL, uL, mL, K), so downstream code never has to
 * guess them. A value that was not given is null and is left out of the JSON.</p>
 *
 * <p>Compatibility: the record is a separate top-level node, which earlier versions of Scatter ignore,
 * and unknown fields inside it are ignored here, so files written by later versions still load.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SampleRecord {

    public static final int VERSION = 1;

    /** units a buffer component concentration may be given in */
    public static final List<String> UNITS = Arrays.asList("M", "mM", "uM", "nM", "%(v/v)", "%(w/v)", "mg/mL");

    /** has anyone looked at the buffer for this dataset? */
    public enum Status {
        /** nothing recorded */
        NONE,
        /** recorded somewhere (a loaded file, an older version's free text) but nobody has confirmed it */
        UNCONFIRMED,
        /** entered or confirmed by the user */
        CONFIRMED;

        @Override public String toString() { return name().toLowerCase(Locale.ROOT); }
    }

    /** where the protein concentration came from, best first */
    public enum ConcentrationSource {
        /** an in-line UV (or RI) trace: a measured concentration for every frame */
        UV_TRACE,
        /** absorbance of a drop taken at the peak apex, relative to the injected sample */
        PEAK_ABSORBANCE,
        /** injected concentration divided by the column's known dilution */
        COLUMN_DILUTION,
        /** each frame's share of the injected mass, from its share of the summed I(0) */
        MASS_BALANCE,
        /** a batch (non-SEC) sample of known concentration */
        STATIC,
        NONE;

        @Override public String toString() { return name().toLowerCase(Locale.ROOT).replace('_', ' '); }
    }

    @JsonProperty("record_version") public int recordVersion = VERSION;
    @JsonProperty("buffer") public Buffer buffer = new Buffer();
    @JsonProperty("concentration") public Concentration concentration = new Concentration();
    /** mass of the whole particle (all subunits) from its sequence, entered by the user */
    @JsonProperty("theoretical_mass_Da") public Double theoreticalMassDa;

    // ------------------------------------------------------------------ buffer

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Buffer {
        /** the buffer as the user typed it, e.g. "20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 5% glycerol" */
        @JsonProperty("text") public String text;
        @JsonProperty("components") public List<Component> components = new ArrayList<>();
        @JsonProperty("pH") public Double pH;
        @JsonProperty("temperature_K") public Double temperatureK;
        @JsonProperty("status") public Status status = Status.NONE;
        /** computed by Scatter from the components (version4.Buffer.BufferCalculator); null if not computed */
        @JsonProperty("ionic_strength_M") public Double ionicStrengthM;
        @JsonProperty("debye_length_A") public Double debyeLengthA;
        @JsonProperty("electron_density_e_per_A3") public Double electronDensity;

        @JsonIgnore
        public boolean isEmpty() {
            return (text == null || text.trim().isEmpty()) && components.isEmpty() && pH == null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Component {
        @JsonProperty("name") public String name;
        @JsonProperty("concentration") public Double concentration;
        /** one of {@link #UNITS} */
        @JsonProperty("unit") public String unit;
        /** acid or base the buffer was titrated with (e.g. "HCl" for Tris-HCl), if known */
        @JsonProperty("titrant") public String titrant;

        public Component() { }

        public Component(String name, double concentration, String unit) {
            this.name = name;
            this.concentration = concentration;
            this.unit = unit;
        }

        /** e.g. "20 mM HEPES (titrated with NaOH)" */
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            if (concentration != null) s.append(trim(concentration)).append(' ');
            if (unit != null) s.append(unit).append(' ');
            s.append(name == null ? "?" : name);
            if (titrant != null && !titrant.isEmpty()) s.append(" (titrated with ").append(titrant).append(')');
            return s.toString().trim();
        }
    }

    // ------------------------------------------------------------------ concentration

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Concentration {
        /** concentration of the sample these data describe (for merged SEC frames: of those frames) */
        @JsonProperty("value_mg_per_ml") public Double valueMgPerMl;
        /** one standard uncertainty */
        @JsonProperty("uncertainty_mg_per_ml") public Double uncertaintyMgPerMl;
        @JsonProperty("source") public ConcentrationSource source = ConcentrationSource.NONE;
        /** how the value was obtained, in words (selection, assumptions) */
        @JsonProperty("method") public String method;

        /** needed to convert a molar concentration to mg/mL */
        @JsonProperty("molecular_mass_Da") public Double molecularMassDa;
        /** A280 of a 1 mg/mL solution over 1 cm */
        @JsonProperty("extinction_coefficient_ml_per_mg_cm") public Double extinctionCoefficient;

        @JsonProperty("injected") public Measurement injected;
        /** drop taken at the peak apex after the column (with the SAXS cell disconnected) */
        @JsonProperty("peak") public Measurement peak;
        @JsonProperty("injection_volume_ul") public Double injectionVolumeUl;
        @JsonProperty("column") public Column column;
        /** fraction of the injected protein that elutes in the peak (1 unless known) */
        @JsonProperty("recovery") public Double recovery;
    }

    /** a concentration, given directly or as replicate absorbance readings */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Measurement {
        @JsonProperty("concentration_mg_per_ml") public Double concentrationMgPerMl;
        @JsonProperty("uncertainty_mg_per_ml") public Double uncertaintyMgPerMl;
        /** replicate A280 readings, already corrected to a 1 cm path */
        @JsonProperty("absorbance_280") public List<Double> absorbance280 = new ArrayList<>();
        /** volume of each drop measured, e.g. 2 uL */
        @JsonProperty("drop_volume_ul") public Double dropVolumeUl;

        /** mean of the replicate readings, or null */
        @JsonIgnore
        public Double meanAbsorbance() {
            if (absorbance280.isEmpty()) return null;
            double s = 0;
            for (double a : absorbance280) s += a;
            return s/absorbance280.size();
        }

        /** standard error of the mean of the replicate readings, or null with fewer than two */
        @JsonIgnore
        public Double absorbanceStandardError() {
            int n = absorbance280.size();
            if (n < 2) return null;
            double m = meanAbsorbance(), s = 0;
            for (double a : absorbance280) s += (a - m)*(a - m);
            return Math.sqrt(s/(n - 1))/Math.sqrt(n);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class Column {
        /** e.g. "Shodex KW-403-4F", "Superdex 200 Increase" */
        @JsonProperty("name") public String name;
        /** e.g. "4.6 x 300 mm", "10/300 GL" */
        @JsonProperty("format") public String format;
        @JsonProperty("volume_ml") public Double volumeMl;
        @JsonProperty("void_volume_ml") public Double voidVolumeMl;
        /** injected / apex concentration measured at {@link #referenceInjectionUl} */
        @JsonProperty("dilution_factor") public Double dilutionFactor;
        @JsonProperty("reference_injection_ul") public Double referenceInjectionUl;

        @Override
        public String toString() {
            String s = (name == null ? "" : name) + (format == null || format.isEmpty() ? "" : " " + format);
            return s.trim();
        }
    }

    // ------------------------------------------------------------------ copies and JSON

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SampleRecord() { }

    /** deep copy */
    public static SampleRecord copyOf(SampleRecord r) {
        if (r == null) return new SampleRecord();
        return MAPPER.convertValue(MAPPER.valueToTree(r), SampleRecord.class);
    }

    /** read from the "sample_record" node of a SasObject; a missing or unreadable node gives an empty record */
    public static SampleRecord fromJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return new SampleRecord();
        try {
            SampleRecord r = MAPPER.treeToValue(node, SampleRecord.class);
            if (r.buffer == null) r.buffer = new Buffer();
            if (r.buffer.components == null) r.buffer.components = new ArrayList<>();
            if (r.buffer.status == null) r.buffer.status = Status.NONE;
            if (r.concentration == null) r.concentration = new Concentration();
            if (r.concentration.source == null) r.concentration.source = ConcentrationSource.NONE;
            return r;
        } catch (Exception e) {
            return new SampleRecord();
        }
    }

    /**
     * A record for data written before this record existed: the free-text buffer fields of
     * {@link SasBuffer} and the temperature of {@link SasSample}, marked unconfirmed. Defaults
     * ("water", "no buffer details specified", pH 7 with nothing else) do not count as a buffer.
     */
    public static SampleRecord fromLegacy(SasBuffer b, SasSample s) {
        SampleRecord r = new SampleRecord();
        if (b != null) {
            List<String> parts = new ArrayList<>();
            if (meaningful(b.getName()) && !"water".equalsIgnoreCase(b.getName().trim())) parts.add(b.getName().trim());
            if (meaningful(b.getSalt())) parts.add(b.getSalt().trim());
            if (meaningful(b.getOther())) parts.add(b.getOther().trim());
            String comment = b.getComment();
            if (meaningful(comment) && !"no buffer details specified".equalsIgnoreCase(comment.trim())) parts.add(comment.trim());
            if (b.getComponents() != null) {
                for (SasBufferComponent c : b.getComponents()) {
                    Component k = new Component();
                    k.name = c.name;
                    k.concentration = (double) c.concentration;
                    k.unit = c.unit;
                    r.buffer.components.add(k);
                }
            }
            if (!parts.isEmpty() || !r.buffer.components.isEmpty()) {
                r.buffer.text = String.join(", ", parts);
                if (b.getpH() != 7.0f) r.buffer.pH = (double) b.getpH();
                else if (!parts.isEmpty()) r.buffer.pH = 7.0;
                r.buffer.status = Status.UNCONFIRMED;
            }
        }
        if (s != null) r.buffer.temperatureK = kelvin(s.getCell_temperature());
        return r;
    }

    /** the form has stored the temperature both in kelvin and in degrees C; anything below 150 is taken as C */
    public static Double kelvin(double t) {
        if (!(t > 0) || Double.isNaN(t)) return null;
        return t < 150 ? t + 273.15 : t;
    }

    private static boolean meaningful(String s) {
        return s != null && !s.trim().isEmpty() && !s.trim().equalsIgnoreCase("null");
    }

    // ------------------------------------------------------------------ REMARK 265 block

    /**
     * The buffer and concentration as REMARK 265 lines for *.dat headers. The line keys of the
     * previous block (TEMPERATURE (KELVIN), PH, SAMPLE BUFFER) are kept so existing readers still
     * find them; nothing is written for a value that is not known (no placeholders).
     */
    public List<String> remarkLines() {
        List<String> out = new ArrayList<>();
        out.add("REMARK 265 BUFFER COMPOSITION");
        Buffer b = buffer;
        out.add(kv("TEMPERATURE (KELVIN)", b.temperatureK == null ? "NOT PROVIDED" : String.format(Locale.ROOT, "%.2f", b.temperatureK)));
        if (b.pH != null) out.add(kv("PH", String.format(Locale.ROOT, "%.2f", b.pH)));
        if (!b.components.isEmpty()) {
            for (Component c : b.components) out.add(kv("SAMPLE BUFFER", c.toString()));
        } else if (b.text != null && !b.text.trim().isEmpty()) {
            for (String line : b.text.replace("\r\n", "\n").split("\n")) {
                if (!line.trim().isEmpty()) out.add(kv("SAMPLE BUFFER", line.trim()));
            }
        } else {
            out.add(kv("SAMPLE BUFFER", "NOT PROVIDED"));
        }
        out.add(kv("BUFFER STATUS", b.status.name()));
        if (b.ionicStrengthM != null) out.add(kv("IONIC STRENGTH (M)", String.format(Locale.ROOT, "%.4f", b.ionicStrengthM)));
        if (b.debyeLengthA != null && !b.debyeLengthA.isInfinite()) out.add(kv("DEBYE LENGTH (ANGSTROMS)", String.format(Locale.ROOT, "%.2f", b.debyeLengthA)));
        if (b.electronDensity != null) out.add(kv("ELECTRON DENSITY (E/A^3)", String.format(Locale.ROOT, "%.4f", b.electronDensity)));

        Concentration c = concentration;
        out.add("REMARK 265 SAMPLE CONCENTRATION");
        if (c.valueMgPerMl != null) {
            String v = String.format(Locale.ROOT, "%.4g", c.valueMgPerMl)
                    + (c.uncertaintyMgPerMl != null ? String.format(Locale.ROOT, " +/- %.2g", c.uncertaintyMgPerMl) : "");
            out.add(kv("CONCENTRATION (MG/ML)", v));
        } else {
            out.add(kv("CONCENTRATION (MG/ML)", "NOT PROVIDED"));
        }
        out.add(kv("CONCENTRATION SOURCE", c.source.name().replace('_', ' ')));
        if (c.method != null && !c.method.isEmpty()) out.add(kv("CONCENTRATION METHOD", c.method));
        if (c.injected != null && c.injected.concentrationMgPerMl != null) {
            out.add(kv("INJECTED (MG/ML)", String.format(Locale.ROOT, "%.4g", c.injected.concentrationMgPerMl)));
        }
        if (c.injectionVolumeUl != null) out.add(kv("INJECTION VOLUME (UL)", trim(c.injectionVolumeUl)));
        if (c.column != null && !c.column.toString().isEmpty()) {
            out.add(kv("SEC COLUMN", c.column.toString()
                    + (c.column.volumeMl != null ? ", " + trim(c.column.volumeMl) + " mL" : "")));
        }
        if (c.molecularMassDa != null) out.add(kv("MOLECULAR MASS (DA)", trim(c.molecularMassDa)));
        out.add(kv("THEORETICAL MASS (DA)", theoreticalMassDa == null ? "NOT PROVIDED" : trim(Math.rint(theoreticalMassDa))));
        return out;
    }

    /**
     * A mass typed by a user: "66432", "66,432", "66432 Da", "66.4 kDa", "0.066 MDa". Null if empty,
     * not a number or not positive.
     */
    public static Double parseMassDa(String text) {
        if (text == null) return null;
        String t = text.trim().replace(",", "").replace("\u00a0", "").replaceAll("\\s+", "");
        if (t.isEmpty()) return null;
        double factor = 1;
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.endsWith("kda")) { factor = 1e3; t = t.substring(0, t.length() - 3); }
        else if (lower.endsWith("mda")) { factor = 1e6; t = t.substring(0, t.length() - 3); }
        else if (lower.endsWith("da")) { t = t.substring(0, t.length() - 2); }
        else if (lower.endsWith("d")) { t = t.substring(0, t.length() - 1); }
        try {
            double v = Double.parseDouble(t)*factor;
            return v > 0 && !Double.isInfinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String kv(String key, String value) {
        return String.format(Locale.ROOT, "REMARK 265%30s : %s", key, value);   // same columns as before
    }

    static String trim(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.6g", v);
        if (s.contains("e") || s.contains("E")) return s;
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
