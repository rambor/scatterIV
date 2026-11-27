package version4.Buffer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import version4.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import version4.Buffer.Chemical.Ion;
import version4.Buffer.Chemical.Kind;

/**
 * The chemicals and named buffers Scatter recognizes in a one-line buffer description.
 *
 * <p>Built in: Good's buffers and other common buffering agents (pKa at 25 C with temperature
 * coefficients, from the standard tables), common salts, and neutral additives with the densities
 * and partial molar volumes needed for the solvent's electron density. Named buffers (PBS, TBS,
 * HBS) expand to their usual recipes.</p>
 *
 * <p>A beamline or user can add chemicals and named buffers in {@code buffer_library.json} in
 * Scatter's settings folder:</p>
 * <pre>
 * { "presets":   [ { "name": "lab SEC buffer", "text": "20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 1 mM TCEP" } ],
 *   "chemicals": [ { "name": "MOPSO", "kind": "BUFFER", "formula": "C7H15NO5S", "molar_mass": 225.26,
 *                    "pKa": [6.87], "dpKa_dT": [-0.015], "charge_protonated": 0 } ] }
 * </pre>
 * Entries there are added to, or replace (same name), the built-in ones.
 */
public final class BufferLibrary {

    public static final String USER_FILE = "buffer_library.json";

    // ions: symbol, charge, count, electrons, partial molar volume (cm^3/mol)
    static final Ion NA = new Ion("Na+", 1, 1, 10, -1.2);
    static final Ion K = new Ion("K+", 1, 1, 18, 9.0);
    static final Ion LI = new Ion("Li+", 1, 1, 2, -0.9);
    static final Ion NH4 = new Ion("NH4+", 1, 1, 10, 18.0);
    static final Ion MG = new Ion("Mg2+", 2, 1, 10, -21.2);
    static final Ion CA = new Ion("Ca2+", 2, 1, 18, -17.9);
    static final Ion ZN = new Ion("Zn2+", 2, 1, 28, -21.6);
    static final Ion MN = new Ion("Mn2+", 2, 1, 23, -17.7);
    static final Ion CL = new Ion("Cl-", -1, 1, 18, 17.8);
    static final Ion BR = new Ion("Br-", -1, 1, 36, 24.7);
    static final Ion SO4 = new Ion("SO4 2-", -2, 1, 50, 14.0);
    static final Ion ACETATE = new Ion("acetate-", -1, 1, 32, 40.5);
    static final Ion GDM = new Ion("Gdm+", 1, 1, 32, 41.1);
    static final Ion AZIDE = new Ion("N3-", -1, 1, 22, 27.0);
    static final Ion ARG = new Ion("ArgH+", 1, 1, 94, 125.0);
    static final Ion GLU = new Ion("Glu-", -1, 1, 78, 85.0);

    /** counter-ions by titrant name (lower case) */
    static final Map<String, Ion> TITRANTS = new LinkedHashMap<>();
    static {
        TITRANTS.put("hcl", CL); TITRANTS.put("cl", CL); TITRANTS.put("hydrochloric acid", CL);
        TITRANTS.put("acetic acid", ACETATE); TITRANTS.put("hac", ACETATE); TITRANTS.put("acetate", ACETATE);
        TITRANTS.put("naoh", NA); TITRANTS.put("na", NA); TITRANTS.put("sodium", NA);
        TITRANTS.put("koh", K); TITRANTS.put("k", K); TITRANTS.put("potassium", K);
        TITRANTS.put("lioh", LI); TITRANTS.put("nh3", NH4); TITRANTS.put("nh4oh", NH4); TITRANTS.put("ammonium", NH4);
    }

    private final Map<String, Chemical> byName = new LinkedHashMap<>();
    private final Map<String, Chemical> byAlias = new HashMap<>();
    private final Map<String, String> presets = new LinkedHashMap<>();
    private final List<String> loadProblems = new ArrayList<>();

    private static BufferLibrary standard;

    /** the built-in library plus the user's file, read once */
    public static synchronized BufferLibrary standard() {
        if (standard == null) {
            standard = builtIn();
            standard.addUserFile(AppConfig.configDirectory().resolve(USER_FILE));
        }
        return standard;
    }

    /** only the built-in entries */
    public static BufferLibrary builtIn() {
        BufferLibrary lib = new BufferLibrary();
        for (Chemical c : builtInChemicals()) lib.add(c);
        lib.presets.put("PBS", "137 mM NaCl, 2.7 mM KCl, 11.8 mM sodium phosphate, pH 7.4");
        lib.presets.put("TBS", "50 mM Tris-HCl, 150 mM NaCl, pH 7.6");
        lib.presets.put("HBS", "10 mM HEPES-NaOH, 150 mM NaCl, pH 7.4");
        return lib;
    }

    public void add(Chemical c) {
        Chemical old = byName.put(c.name.toLowerCase(Locale.ROOT), c);
        if (old != null) {
            byAlias.values().removeIf(v -> v == old);
        }
        byAlias.put(normalize(c.name), c);
        for (String a : c.aliases) byAlias.put(normalize(a), c);
    }

    public void addPreset(String name, String text) { presets.put(name, text); }

    /** the chemical called {@code name} (any alias, case and hyphens ignored), or null */
    public Chemical find(String name) {
        if (name == null) return null;
        Chemical c = byAlias.get(normalize(name));
        if (c == null && name.matches("(?i)^[LD]-.+")) c = byAlias.get(normalize(name.substring(2)));
        if (c == null && name.matches("(?i)^peg\\s*-?\\s*\\d+.*")) c = byAlias.get("peg");
        return c;
    }

    /** named buffers, e.g. PBS -> "137 mM NaCl, ..." */
    public Map<String, String> presets() { return Collections.unmodifiableMap(presets); }

    public Collection<Chemical> chemicals() { return Collections.unmodifiableCollection(byName.values()); }

    /** problems reading the user's file, for the status bar */
    public List<String> loadProblems() { return Collections.unmodifiableList(loadProblems); }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace('·', ' ').replaceAll("[\\s_-]+", "").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class UserFile {
        @JsonProperty("presets") public List<Map<String, String>> presets = new ArrayList<>();
        @JsonProperty("chemicals") public List<Chemical> chemicals = new ArrayList<>();
    }

    /** add the chemicals and presets of a user's buffer_library.json, if it exists */
    public void addUserFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            UserFile u = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(file.toFile(), UserFile.class);
            for (Chemical c : u.chemicals) {
                if (c.name == null || c.name.trim().isEmpty()) { loadProblems.add("a chemical without a name"); continue; }
                if (c.aliases == null) c.aliases = new ArrayList<>();
                List<String> lower = new ArrayList<>();
                for (String a : c.aliases) lower.add(a.toLowerCase(Locale.ROOT));
                c.aliases = lower;
                try { c.electrons(); } catch (IllegalArgumentException e) { loadProblems.add(c.name + ": " + e.getMessage()); continue; }
                add(c);
            }
            for (Map<String, String> p : u.presets) {
                if (p.get("name") != null && p.get("text") != null) presets.put(p.get("name"), p.get("text"));
            }
        } catch (IOException e) {
            loadProblems.add(file.getFileName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ built-in entries

    private static double[] p(double... v) { return v; }

    static List<Chemical> builtInChemicals() {
        List<Chemical> l = new ArrayList<>();
        // Good's and other buffers: charge of the most protonated form, default counter-ion, pKa (25 C), dpKa/dT
        l.add(new Chemical("MES", Kind.BUFFER, "C6H13NO4S", 195.24, "mes").buffer(0, "Na+", p(6.10), -0.011));
        l.add(new Chemical("Bis-Tris", Kind.BUFFER, "C8H19NO5", 209.24, "bistris", "bis tris").buffer(1, "Cl-", p(6.46), -0.017));
        l.add(new Chemical("PIPES", Kind.BUFFER, "C8H18N2O6S2", 302.37, "pipes").buffer(-1, "Na+", p(6.76), -0.0085));
        l.add(new Chemical("MOPS", Kind.BUFFER, "C7H15NO4S", 209.26, "mops").buffer(0, "Na+", p(7.14), -0.011));
        l.add(new Chemical("HEPES", Kind.BUFFER, "C8H18N2O4S", 238.30, "hepes").buffer(0, "Na+", p(7.48), -0.014));
        l.add(new Chemical("EPPS", Kind.BUFFER, "C9H20N2O4S", 252.33, "hepps").buffer(0, "Na+", p(8.00), -0.015));
        l.add(new Chemical("Tricine", Kind.BUFFER, "C6H13NO5", 179.17, "tricine").buffer(0, "Na+", p(8.05), -0.021));
        l.add(new Chemical("Tris", Kind.BUFFER, "C4H11NO3", 121.14, "tris", "trizma", "tris base", "trometamol").buffer(1, "Cl-", p(8.07), -0.028).volume(102.2));
        l.add(new Chemical("Bicine", Kind.BUFFER, "C6H13NO4", 163.17, "bicine").buffer(0, "Na+", p(8.26), -0.018));
        l.add(new Chemical("TAPS", Kind.BUFFER, "C7H17NO6S", 243.28, "taps").buffer(0, "Na+", p(8.40), -0.018));
        l.add(new Chemical("CHES", Kind.BUFFER, "C8H17NO3S", 207.29, "ches").buffer(0, "Na+", p(9.50), -0.011));
        l.add(new Chemical("CAPS", Kind.BUFFER, "C9H19NO3S", 221.32, "caps").buffer(0, "Na+", p(10.40), -0.009));
        l.add(new Chemical("imidazole", Kind.BUFFER, "C3H4N2", 68.08, "imidazole").buffer(1, "Cl-", p(6.95), -0.020));
        l.add(new Chemical("phosphate", Kind.BUFFER, "H3PO4", 97.99, "phosphate", "sodium phosphate", "potassium phosphate",
                "na phosphate", "k phosphate", "napi", "kpi", "na2hpo4", "nah2po4", "k2hpo4", "kh2po4")
                .buffer(0, "Na+", p(2.15, 7.20, 12.35), 0.005, -0.0028, -0.026).volume(20.0));
        l.add(new Chemical("acetate", Kind.BUFFER, "C2H4O2", 60.05, "acetate", "sodium acetate", "naoac", "acetic acid")
                .buffer(0, "Na+", p(4.76), 0.0002));
        l.add(new Chemical("citrate", Kind.BUFFER, "C6H8O7", 192.12, "citrate", "sodium citrate", "citric acid")
                .buffer(0, "Na+", p(3.13, 4.76, 6.40), -0.0024, -0.0016, 0.0));
        l.add(new Chemical("succinate", Kind.BUFFER, "C4H6O4", 118.09, "succinate", "succinic acid").buffer(0, "Na+", p(4.21, 5.64), -0.0018, 0.0));
        l.add(new Chemical("glycine", Kind.BUFFER, "C2H5NO2", 75.07, "glycine", "gly").buffer(1, "Na+", p(2.35, 9.78), -0.002, -0.025));
        l.add(new Chemical("borate", Kind.BUFFER, "H3BO3", 61.83, "borate", "boric acid").buffer(0, "Na+", p(9.24), -0.008));
        l.add(new Chemical("EDTA", Kind.BUFFER, "C10H16N2O8", 292.24, "edta", "na2edta", "disodium edta").buffer(0, "Na+", p(2.0, 2.7, 6.16, 10.26), 0.0));
        l.add(new Chemical("EGTA", Kind.BUFFER, "C14H24N2O10", 380.35, "egta").buffer(0, "Na+", p(2.0, 2.65, 8.85, 9.47), 0.0));

        // salts
        l.add(new Chemical("NaCl", Kind.SALT, "NaCl", 58.44, "sodium chloride").ions(NA, CL).volume(16.6));
        l.add(new Chemical("KCl", Kind.SALT, "KCl", 74.55, "potassium chloride").ions(K, CL).volume(26.8));
        l.add(new Chemical("LiCl", Kind.SALT, "LiCl", 42.39, "lithium chloride").ions(LI, CL));
        l.add(new Chemical("NH4Cl", Kind.SALT, "NH4Cl", 53.49, "ammonium chloride").ions(NH4, CL));
        l.add(new Chemical("MgCl2", Kind.SALT, "MgCl2", 95.21, "magnesium chloride").ions(MG, CL.times(2)).volume(14.4));
        l.add(new Chemical("CaCl2", Kind.SALT, "CaCl2", 110.98, "calcium chloride").ions(CA, CL.times(2)).volume(17.7));
        l.add(new Chemical("ZnCl2", Kind.SALT, "ZnCl2", 136.29, "zinc chloride").ions(ZN, CL.times(2)));
        l.add(new Chemical("MnCl2", Kind.SALT, "MnCl2", 125.84, "manganese chloride").ions(MN, CL.times(2)));
        l.add(new Chemical("NaBr", Kind.SALT, "NaBr", 102.89, "sodium bromide").ions(NA, BR));
        l.add(new Chemical("KBr", Kind.SALT, "KBr", 119.00, "potassium bromide").ions(K, BR));
        l.add(new Chemical("MgSO4", Kind.SALT, "MgSO4", 120.37, "magnesium sulfate", "magnesium sulphate").ions(MG, SO4));
        l.add(new Chemical("Na2SO4", Kind.SALT, "Na2SO4", 142.04, "sodium sulfate", "sodium sulphate").ions(NA.times(2), SO4));
        l.add(new Chemical("K2SO4", Kind.SALT, "K2SO4", 174.26, "potassium sulfate", "potassium sulphate").ions(K.times(2), SO4));
        l.add(new Chemical("(NH4)2SO4", Kind.SALT, "N2H8SO4", 132.14, "ammonium sulfate", "ammonium sulphate", "amso4", "nh42so4").ions(NH4.times(2), SO4));
        l.add(new Chemical("potassium acetate", Kind.SALT, "C2H3KO2", 98.14, "koac", "kac").ions(K, ACETATE));
        l.add(new Chemical("ammonium acetate", Kind.SALT, "C2H7NO2", 77.08, "nh4oac", "nh4ac").ions(NH4, ACETATE));
        l.add(new Chemical("magnesium acetate", Kind.SALT, "C4H6MgO4", 142.39, "mgoac", "mg(oac)2", "mgac2").ions(MG, ACETATE.times(2)));
        l.add(new Chemical("guanidine hydrochloride", Kind.SALT, "CH6N3Cl", 95.53, "gdmcl", "gdncl", "gdnhcl", "guanidinium chloride", "guhcl", "gdn-hcl").ions(GDM, CL).volume(58.9));
        l.add(new Chemical("sodium azide", Kind.SALT, "NaN3", 65.01, "nan3", "azide").ions(NA, AZIDE));
        l.add(new Chemical("arginine hydrochloride", Kind.SALT, "C6H15N4O2Cl", 210.66, "arginine", "arg", "arg-hcl", "arginine-hcl", "l-arginine")
                .ions(ARG, CL).note("arginine taken as Arg+ with Cl- (as the hydrochloride)"));
        l.add(new Chemical("sodium glutamate", Kind.SALT, "C5H8NNaO4", 169.11, "glutamate", "glu", "l-glutamate", "glutamic acid", "na-glutamate", "msg")
                .ions(NA, GLU).note("glutamate taken as Glu- with Na+"));

        // neutral additives
        l.add(new Chemical("glycerol", Kind.NEUTRAL, "C3H8O3", 92.09, "glycerin", "glycerine").liquid(1.261).volume(71.0));
        l.add(new Chemical("ethylene glycol", Kind.NEUTRAL, "C2H6O2", 62.07, "eg", "ethyleneglycol").liquid(1.113).volume(54.6));
        l.add(new Chemical("DMSO", Kind.NEUTRAL, "C2H6OS", 78.13, "dimethyl sulfoxide", "dimethyl sulphoxide").liquid(1.100));
        l.add(new Chemical("sucrose", Kind.NEUTRAL, "C12H22O11", 342.30).volume(211.3));
        l.add(new Chemical("trehalose", Kind.NEUTRAL, "C12H22O11", 342.30).volume(206.9));
        l.add(new Chemical("glucose", Kind.NEUTRAL, "C6H12O6", 180.16, "dextrose").volume(112.0));
        l.add(new Chemical("urea", Kind.NEUTRAL, "CH4N2O", 60.06).volume(44.2));
        l.add(new Chemical("DTT", Kind.NEUTRAL, "C4H10O2S2", 154.25, "dithiothreitol"));
        l.add(new Chemical("TCEP", Kind.NEUTRAL, "C9H15O6P", 250.19, "tcep-hcl", "tcep hcl")
                .note("TCEP's charge is not modelled (small at mM)"));
        l.add(new Chemical("beta-mercaptoethanol", Kind.NEUTRAL, "C2H6OS", 78.13, "bme", "b-me", "2-mercaptoethanol", "betame", "β-me", "βme").liquid(1.114));
        l.add(new Chemical("PEG", Kind.NEUTRAL, "C2H4O", 44.05, "polyethylene glycol")
                .note("PEG: per ethylene-oxide unit; mM of PEG is not meaningful, give % (w/v)"));
        // detergents: CMC in water (mM, approximate; lower in salt), partial specific volume (mL/g).
        // Polydisperse ones (Triton, NP-40, Tween, Brij) use a typical average formula.
        l.add(new Chemical("Triton X-100", Kind.NEUTRAL, "C34H62O11", 646.85, "triton", "tx100", "tx-100", "triton x100", "tritonx100")
                .liquid(1.065).detergent(0.24, 0.908));
        l.add(new Chemical("NP-40", Kind.NEUTRAL, "C33H60O10", 616.82, "np40", "igepal ca-630", "igepal", "nonidet p-40")
                .liquid(1.06).detergent(0.29, 0.91));
        l.add(new Chemical("Tween 20", Kind.NEUTRAL, "C58H114O26", 1227.54, "tween20", "tween-20", "polysorbate 20")
                .liquid(1.105).detergent(0.06, 0.869));
        l.add(new Chemical("Tween 80", Kind.NEUTRAL, "C64H124O26", 1310.0, "tween80", "tween-80", "polysorbate 80")
                .liquid(1.07).detergent(0.012, 0.896));
        l.add(new Chemical("Brij-35", Kind.NEUTRAL, "C58H118O24", 1199.55, "brij 35", "brij35").detergent(0.09, 0.91));
        l.add(new Chemical("DDM", Kind.NEUTRAL, "C24H46O11", 510.62, "n-dodecyl-beta-d-maltoside", "dodecyl maltoside",
                "beta-ddm", "b-ddm", "β-ddm", "dodecylmaltoside").detergent(0.17, 0.82));
        l.add(new Chemical("UDM", Kind.NEUTRAL, "C23H44O11", 496.59, "undecyl maltoside").detergent(0.59, 0.82));
        l.add(new Chemical("DM", Kind.NEUTRAL, "C22H42O11", 482.56, "decyl maltoside", "n-decyl-beta-d-maltoside").detergent(1.8, 0.81));
        l.add(new Chemical("LMNG", Kind.NEUTRAL, "C47H88O22", 1005.19, "lauryl maltose neopentyl glycol", "mng-3").detergent(0.01, 0.83));
        l.add(new Chemical("GDN", Kind.NEUTRAL, "C56H92O25", 1165.31, "glyco-diosgenin").detergent(0.018, 0.80));
        l.add(new Chemical("OG", Kind.NEUTRAL, "C14H28O6", 292.37, "octyl glucoside", "n-octyl-beta-d-glucoside", "beta-og", "b-og", "octylglucoside")
                .detergent(20, 0.859));
        l.add(new Chemical("CHAPS", Kind.NEUTRAL, "C32H58N2O7S", 614.88, "chaps").detergent(8, 0.81)
                .note("CHAPS is zwitterionic: no net charge counted"));
        l.add(new Chemical("LDAO", Kind.NEUTRAL, "C14H31NO", 229.40, "lauryldimethylamine oxide", "ddao", "lauryldimethylamine n-oxide")
                .detergent(1.0, 1.10));
        l.add(new Chemical("Fos-choline-12", Kind.NEUTRAL, "C17H38NO4P", 351.46, "fc-12", "fc12", "dpc", "dodecylphosphocholine", "foscholine-12", "fos-choline 12")
                .detergent(1.5, 0.94).note("Fos-choline is zwitterionic: no net charge counted"));
        l.add(new Chemical("digitonin", Kind.NEUTRAL, "C56H92O29", 1229.31, "digitonin").detergent(0.5, 0.75));
        l.add(new Chemical("SDS", Kind.SALT, "C12H25NaO4S", 288.38, "sodium dodecyl sulfate", "sodium dodecyl sulphate", "sodium lauryl sulfate")
                .ions(NA, new Ion("DS-", -1, 1, 146, 250.0)).detergent(8.2, 0.87)
                .note("SDS denatures most proteins"));
        return l;
    }
}
