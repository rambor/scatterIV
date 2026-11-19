package version4.FormFactors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lipid composition of a formulation and what it implies for SAXS: layer electron densities (X-ray contrast against
 * buffer), the share of ionizable lipid, the PEG corona thickness and the internal phase the lipid mix tends to form.
 *
 * Electron counts are from the molecular formulae; molecular volumes from MW / (density * N_A) with liquid-state
 * densities (~0.90-0.92 g/cm^3 for ionizable lipids, 1.0 for phospholipids, 1.03 for cholesterol), close to published
 * values (DSPC ~1300, DOPE ~1160, cholesterol ~630 A^3). Placement follows SANS contrast variation and all-atom MD:
 * ionizable lipid and nucleic acid in the core, DSPC and PEG-lipid at the surface (Yanez Arteta et al. 2018; Gilbert
 * et al. 2024), with cholesterol split between an oil-like core (ionizable lipid : cholesterol = 72 : 28) and a
 * cholesterol-rich surface monolayer (DSPC : ionizable : PEG-lipid : cholesterol = 22 : 9 : 6 : 63) as found for
 * Comirnaty by Trollmann &amp; Boeckmann, Biophys. J. 121, 3927 (2022).
 */
public final class LnpComposition {

    public enum Role { IONIZABLE, HELPER, STEROL, PEG, OTHER }

    /** one lipid species */
    public static final class Lipid {
        public final String name;
        public final Role role;
        public final double electrons, volume;   // per molecule, volume in A^3 (lipid part only for PEG-lipids)
        public final int pegUnits;                // ethylene-oxide repeats of PEG-lipids
        /** phase the lipid favours on its own: "lamellar", "HII", "cubic" or "" */
        public final String propensity;
        /** molecular formula of the lipid (anchor only for PEG-lipids), may be empty */
        public final String formula;
        /** molecular weight (Da) of the lipid part, NaN if unknown */
        public final double mw;
        /** false for lipids from the user's private library */
        public final boolean builtIn;
        public final String notes;
        /**
         * The acyl chains, as "carbons:double-bonds" separated by slashes — "16:0/18:1" for POPC, four entries
         * for a cardiolipin. Empty where the molecule has no acyl chains (cholesterol) or chains too branched
         * for the description to mean anything (phytantriol, ALC-0315).
         */
        public final String chains;

        Lipid(String name, Role role, double electrons, double volume, int pegUnits, String propensity) {
            this(name, role, electrons, volume, pegUnits, propensity, "", Double.NaN, true, "");
        }

        public Lipid(String name, Role role, double electrons, double volume, int pegUnits, String propensity,
                     String formula, double mw, boolean builtIn, String notes) {
            this(name, role, electrons, volume, pegUnits, propensity, formula, mw, builtIn, notes, "");
        }

        public Lipid(String name, Role role, double electrons, double volume, int pegUnits, String propensity,
                     String formula, double mw, boolean builtIn, String notes, String chains) {
            this.name = name; this.role = role; this.electrons = electrons; this.volume = volume;
            this.pegUnits = pegUnits; this.propensity = propensity == null ? "" : propensity;
            this.formula = formula == null ? "" : formula; this.mw = mw; this.builtIn = builtIn;
            this.notes = notes == null ? "" : notes; this.chains = chains == null ? "" : chains;
        }

        /** the same lipid with its acyl chains declared */
        public Lipid withChains(String spec) {
            return new Lipid(name, role, electrons, volume, pegUnits, propensity, formula, mw, builtIn, notes, spec);
        }

        /** mean electron density of the molecule (e/A^3) */
        public double density() { return electrons / volume; }

        /** how many acyl chains the molecule carries; 0 when they are not described */
        public int chainCount() { return chains.isEmpty() ? 0 : chains.split("/").length; }

        /**
         * Electrons in the hydrocarbon region of this molecule's chains.
         *
         * <p>The carbonyl carbon is left out: it belongs to the interface, not to the hydrocarbon core. What
         * remains of an n:d chain is (n-1) carbons, of which 2d carry one hydrogen each (the double bonds),
         * one is the terminal methyl and the rest are methylenes.
         */
        public double tailElectrons() {
            double e = 0;
            for (int[] c : parsedChains()) {
                int carbons = c[0] - 1, dbl = c[1];
                int doubleBonded = Math.min(2 * dbl, Math.max(0, carbons - 1));
                int methylenes = carbons - 1 - doubleBonded;
                e += 8.0 * methylenes + 7.0 * doubleBonded + 9.0;     // CH2 = 8, CH= = 7, CH3 = 9
            }
            return e;
        }

        /**
         * Volume of the hydrocarbon region of this molecule's chains, at the given methylene volume. The
         * terminal methyl takes twice a methylene (Nagle &amp; Wilkinson 1978; Petrache et al. 2000) and a
         * double-bonded carbon is counted as a methylene, which is the usual simplification.
         */
        public double tailVolume(double vCH2) {
            double v = 0;
            for (int[] c : parsedChains()) v += vCH2 * ((c[0] - 1) + 1);   // (n-1) carbons, methyl counts twice
            return v;
        }

        private List<int[]> parsedChains() {
            List<int[]> out = new ArrayList<>();
            if (chains.isEmpty()) return out;
            for (String part : chains.split("/")) {
                String[] cd = part.trim().split(":");
                if (cd.length != 2) continue;
                try {
                    out.add(new int[]{Integer.parseInt(cd[0].trim()), Integer.parseInt(cd[1].trim())});
                } catch (NumberFormatException ignored) { }
            }
            return out;
        }

        @Override public String toString() { return builtIn ? name : name + "  (private)"; }
    }

    /** lipids shipped with Scatter */
    public static final Map<String, Lipid> BUILT_IN = new LinkedHashMap<>();
    static {
        add(new Lipid("ALC-0315", Role.IONIZABLE, 430, 1383, 0, ""));
        add(new Lipid("SM-102", Role.IONIZABLE, 398, 1281, 0, ""));
        add(new Lipid("DLin-MC3-DMA", Role.IONIZABLE, 360, 1184, 0, "").withChains("18:2/18:2"));
        add(new Lipid("DODAP", Role.IONIZABLE, 362, 1170, 0, "HII").withChains("18:1/18:1"));
        add(new Lipid("DODMA", Role.IONIZABLE, 340, 1150, 0, "").withChains("18:1/18:1"));
        add(new Lipid("DOTAP", Role.IONIZABLE, 370, 1200, 0, "lamellar").withChains("18:1/18:1"));
        add(new Lipid("DSPC", Role.HELPER, 438, 1325, 0, "lamellar").withChains("18:0/18:0"));
        add(new Lipid("DOPC", Role.HELPER, 434, 1300, 0, "lamellar").withChains("18:1/18:1"));
        add(new Lipid("DOPE", Role.HELPER, 410, 1160, 0, "HII").withChains("18:1/18:1"));
        add(new Lipid("GMO (monoolein)", Role.HELPER, 198, 620, 0, "cubic").withChains("18:1"));
        add(new Lipid("Monolinolein (GML)", Role.HELPER, 196, 626, 0, "cubic").withChains("18:2"));
        add(new Lipid("Phytantriol", Role.HELPER, 186, 596, 0, "cubic"));
        add(new Lipid("DOPG", Role.HELPER, 414, 1230, 0, "lamellar").withChains("18:1/18:1"));
        add(new Lipid("Cholesterol", Role.STEROL, 216, 630, 0, ""));

        // Membrane phospholipids, for samples that are bilayers rather than delivery particles: model membranes,
        // bacterial and mitochondrial mimics, surfactant preparations. Electrons are counted from the formula;
        // volumes are representative literature values near 20-30 C, and both the volume and the chain packing
        // change with temperature and phase, so the gel value of a saturated species is not its fluid value.
        add(new Lipid("DPPC (16:0 PC)", Role.HELPER, 406, 1144, 0, "lamellar", "C40H80NO8P", 734.0, true,
                "Zwitterionic. Gel below 41 C; the volume here is the gel value near 20 C.").withChains("16:0/16:0"));
        add(new Lipid("DMPC (14:0 PC)", Role.HELPER, 374, 1101, 0, "lamellar", "C36H72NO8P", 677.9, true,
                "Zwitterionic. Main transition near 24 C, so the phase depends on the measurement temperature.").withChains("14:0/14:0"));
        add(new Lipid("POPC (16:0-18:1 PC)", Role.HELPER, 420, 1256, 0, "lamellar", "C42H82NO8P", 760.1, true,
                "Zwitterionic, fluid at room temperature. The usual neutral background lipid.").withChains("16:0/18:1"));
        add(new Lipid("POPE (16:0-18:1 PE)", Role.HELPER, 396, 1180, 0, "HII", "C39H76NO8P", 718.0, true,
                "Zwitterionic, small headgroup: curves the monolayer and favours inverse phases.").withChains("16:0/18:1"));
        add(new Lipid("DPPE (16:0 PE)", Role.HELPER, 382, 1080, 0, "HII", "C37H74NO8P", 691.9, true,
                "Zwitterionic, saturated; gel well above room temperature.").withChains("16:0/16:0"));
        add(new Lipid("POPG (16:0-18:1 PG)", Role.HELPER, 412, 1210, 0, "lamellar", "C40H77O10P", 770.9, true,
                "Anionic at neutral pH. Bacterial membranes and surfactant preparations.").withChains("16:0/18:1"));
        add(new Lipid("DPPG (16:0 PG)", Role.HELPER, 398, 1130, 0, "lamellar", "C38H75O10P", 744.9, true,
                "Anionic, saturated; gel below about 41 C.").withChains("16:0/16:0"));
        add(new Lipid("Cardiolipin TOCL (18:1)4", Role.HELPER, 794, 2460, 0, "lamellar", "C81H142O17P2", 1457.9, true,
                "Mitochondrial and bacterial. Two phosphates and four chains in one molecule, so it counts once "
                        + "but carries twice the charge and twice the chains; divalent cations drive it to HII.").withChains("18:1/18:1/18:1/18:1"));
        add(new Lipid("Cardiolipin TMCL (14:0)4", Role.HELPER, 682, 2080, 0, "lamellar", "C65H126O17P2", 1241.6, true,
                "Saturated cardiolipin, gel at room temperature.").withChains("14:0/14:0/14:0/14:0"));
        add(new Lipid("Sphingomyelin (16:0 SM)", Role.HELPER, 390, 1150, 0, "lamellar", "C39H79N2O6P", 703.0, true,
                "Zwitterionic sphingolipid; orders chains and partners cholesterol in raft-like mixtures.").withChains("16:0/18:1"));
        add(new Lipid("Ceramide (C16)", Role.HELPER, 302, 900, 0, "lamellar", "C34H67NO3", 537.9, true,
                "Very small headgroup; strongly orders chains and tends to separate into its own gel domains.").withChains("16:0/18:1"));
        add(new Lipid("ALC-0159 (PEG2000)", Role.PEG, 360, 1000, 45, "").withChains("14:0/14:0"));
        add(new Lipid("DMG-PEG2000", Role.PEG, 330, 950, 45, "").withChains("14:0/14:0"));
        add(new Lipid("DMPE-PEG2000", Role.PEG, 380, 1100, 45, "").withChains("14:0/14:0"));
        add(new Lipid("DSPE-PEG2000", Role.PEG, 420, 1250, 45, "").withChains("18:0/18:0"));
        // Polymer stabilizers of cubosomes (Barriga, Holme & Stevens, Angew. Chem. Int. Ed. 58, 2958 (2019)).
        // The PPO block is the anchor, so electrons and volume are those of the PPO block; pegUnits is the EO
        // repeats of ONE arm, which is what sets the corona thickness.
        add(new Lipid("Pluronic F127", Role.PEG, 2080, 6270, 100, ""));
        add(new Lipid("Pluronic F108", Role.PEG, 1600, 4820, 132, ""));
    }
    private static void add(Lipid l) { BUILT_IN.put(l.name, l); }

    /** a lipid by name; private lipids cannot reuse a built-in name, so names are unique */
    public static Lipid lookup(String name) {
        Lipid l = BUILT_IN.get(name);
        return l != null ? l : LnpLipidLibrary.get().lipid(name);
    }

    /** built-in lipids followed by the user's private ones */
    public static List<Lipid> allLipids() {
        List<Lipid> out = new ArrayList<>(BUILT_IN.values());
        out.addAll(LnpLipidLibrary.get().lipids());
        return out;
    }

    /** clinical / literature formulations (mol %) */
    public static final Map<String, Object[][]> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("Comirnaty (BNT162b2)", new Object[][]{{"ALC-0315", 46.3}, {"DSPC", 9.4}, {"Cholesterol", 42.7}, {"ALC-0159 (PEG2000)", 1.6}});
        PRESETS.put("Spikevax (mRNA-1273)", new Object[][]{{"SM-102", 50.0}, {"DSPC", 10.0}, {"Cholesterol", 38.5}, {"DMG-PEG2000", 1.5}});
        PRESETS.put("Onpattro (patisiran)", new Object[][]{{"DLin-MC3-DMA", 50.0}, {"DSPC", 10.0}, {"Cholesterol", 38.5}, {"DMG-PEG2000", 1.5}});
        PRESETS.put("Cardiolipin : PC 1:1 (mitochondrial mimic)", new Object[][]{
                {"Cardiolipin TOCL (18:1)4", 50.0}, {"POPC (16:0-18:1 PC)", 50.0}});
        PRESETS.put("Bacterial inner membrane (PE : PG : CL)", new Object[][]{
                {"POPE (16:0-18:1 PE)", 70.0}, {"POPG (16:0-18:1 PG)", 25.0}, {"Cardiolipin TOCL (18:1)4", 5.0}});
        PRESETS.put("Cholesterol : PC 1:1 (liquid-ordered)", new Object[][]{
                {"Cholesterol", 50.0}, {"POPC (16:0-18:1 PC)", 50.0}});
        PRESETS.put("DOPE-rich HII (Pattipeiluhu 2024)", new Object[][]{{"DODAP", 50.0}, {"DOPE", 49.0}, {"DMPE-PEG2000", 1.0}});
        PRESETS.put("Cuboplex GMO (Zheng 2023)", new Object[][]{{"GMO (monoolein)", 85.0}, {"DOTAP", 14.0}, {"DSPE-PEG2000", 1.0}});
        PRESETS.put("Cubosome: monoolein + F127", new Object[][]{{"GMO (monoolein)", 99.5}, {"Pluronic F127", 0.5}});
        PRESETS.put("Cubosome: phytantriol + F127", new Object[][]{{"Phytantriol", 99.5}, {"Pluronic F127", 0.5}});
        PRESETS.put("Swollen cubosome (MO:DOTAP:PEG 95:4:1)", new Object[][]{{"GMO (monoolein)", 95.0}, {"DOTAP", 4.0}, {"DMG-PEG2000", 1.0}});
    }

    /** names of the built-in and the user's saved formulations */
    public static List<String> presetNames() {
        List<String> out = new ArrayList<>(PRESETS.keySet());
        out.addAll(LnpLipidLibrary.get().formulationNames());
        return out;
    }

    /** a built-in or saved formulation; lipids missing from the library are skipped (null if none is left) */
    public static LnpComposition preset(String name) {
        Object[][] rows = PRESETS.get(name);
        if (rows == null) rows = LnpLipidLibrary.get().formulation(name);
        if (rows == null) return null;
        LnpComposition c = new LnpComposition();
        for (Object[] e : rows) {
            Lipid l = lookup((String) e[0]);
            if (l != null) c.add(l, ((Number) e[1]).doubleValue());
        }
        return c.lipids.isEmpty() ? null : c;
    }

    // ------------------------------------------------------------------ molecular formula

    private static final Map<String, double[]> ELEMENTS = new LinkedHashMap<>();   // {Z, atomic mass}
    static {
        Object[][] e = {{"H", 1, 1.008}, {"B", 5, 10.81}, {"C", 6, 12.011}, {"N", 7, 14.007}, {"O", 8, 15.999},
                {"F", 9, 18.998}, {"Na", 11, 22.990}, {"Si", 14, 28.085}, {"P", 15, 30.974}, {"S", 16, 32.06},
                {"Cl", 17, 35.45}, {"K", 19, 39.098}, {"Br", 35, 79.904}, {"I", 53, 126.904}, {"D", 1, 2.014}};
        for (Object[] x : e) ELEMENTS.put((String) x[0], new double[]{((Number) x[1]).doubleValue(), ((Number) x[2]).doubleValue()});
    }

    /**
     * Electrons and molecular weight from a formula such as "C48H95NO5", "C42H80NO4+" or "(C2H4O)45C35H70NO2".
     * A trailing + or - (optionally followed by a count, e.g. "+2") gives the charge. Returns {electrons, MW}.
     * @throws IllegalArgumentException for unknown elements or bad syntax
     */
    public static double[] parseFormula(String formula) {
        String f = formula.replaceAll("\\s+", "");
        int charge = 0;
        // charge as a trailing sign with an optional count: "C42H80NO4+", "...-", "...+2"
        java.util.regex.Matcher cm = java.util.regex.Pattern.compile("([+-])(\\d*)$").matcher(f);
        if (cm.find()) {
            int n = cm.group(2).isEmpty() ? 1 : Integer.parseInt(cm.group(2));
            charge = cm.group(1).equals("+") ? n : -n;
            f = f.substring(0, cm.start());
        }
        int[] pos = {0};
        double[] r = group(f, pos);
        if (pos[0] != f.length()) throw new IllegalArgumentException("Cannot read the formula near '" + f.substring(pos[0]) + "'");
        if (r[0] <= 0) throw new IllegalArgumentException("Empty formula");
        r[0] -= charge;
        return r;
    }

    private static double[] group(String f, int[] pos) {
        double e = 0, m = 0;
        while (pos[0] < f.length()) {
            char c = f.charAt(pos[0]);
            double[] part;
            if (c == '(' || c == '[') {
                pos[0]++;
                part = group(f, pos);
                if (pos[0] >= f.length() || (f.charAt(pos[0]) != ')' && f.charAt(pos[0]) != ']'))
                    throw new IllegalArgumentException("Unbalanced bracket in the formula");
                pos[0]++;
            } else if (c == ')' || c == ']') {
                return new double[]{e, m};
            } else if (Character.isUpperCase(c)) {
                int st = pos[0]++;
                while (pos[0] < f.length() && Character.isLowerCase(f.charAt(pos[0]))) pos[0]++;
                String sym = f.substring(st, pos[0]);
                double[] el = ELEMENTS.get(sym);
                if (el == null) throw new IllegalArgumentException("Unknown element '" + sym + "'");
                part = new double[]{el[0], el[1]};
            } else throw new IllegalArgumentException("Unexpected '" + c + "' in the formula");
            int st = pos[0];
            while (pos[0] < f.length() && (Character.isDigit(f.charAt(pos[0])) || f.charAt(pos[0]) == '.')) pos[0]++;
            double n = st == pos[0] ? 1 : Double.parseDouble(f.substring(st, pos[0]));
            e += n * part[0];
            m += n * part[1];
        }
        return new double[]{e, m};
    }

    /** molecular volume (A^3) from molecular weight (Da) and mass density (g/cm^3) */
    public static double volumeFromDensity(double mw, double gPerCm3) { return mw / (gPerCm3 * 0.6022); }

    /** typical liquid-state mass densities (g/cm^3) by role, for when the user has no measured value */
    public static double typicalDensity(Role r) {
        switch (r) {
            case IONIZABLE: return 0.92;
            case STEROL: return 1.03;
            case PEG: return 0.95;
            default: return 1.00;
        }
    }

    public final List<Lipid> lipids = new ArrayList<>();
    public final List<Double> molPercent = new ArrayList<>();


    /**
     * Adds a lipid at a mol percentage. A null lipid is rejected here rather than stored: it comes from a name
     * that is not in the library, and left in the list it surfaces much later as a null dereference somewhere
     * inside a contrast or a phase, where nothing points back at the name that was wrong.
     */
    public void add(Lipid l, double mol) {
        if (l == null) throw new IllegalArgumentException("no such lipid in the library");
        lipids.add(l); molPercent.add(mol);
    }

    double total() { double t = 0; for (double m : molPercent) t += m; return t; }

    double fraction(Role r) {
        double t = total(), s = 0;
        for (int i = 0; i < lipids.size(); i++) if (lipids.get(i).role == r) s += molPercent.get(i);
        return t > 0 ? s / t : 0;
    }

    public double ionizableMolPercent() { return 100 * fraction(Role.IONIZABLE); }

    /** PEG-lipid content; with none there is no corona to put in a model */
    public double pegMolPercent() { return 100 * fraction(Role.PEG); }

    /** sterol content, which decides whether ordered chain packing is expected at all */
    public double sterolMolPercent() { return 100 * fraction(Role.STEROL); }

    /** mean molecular volume of the lipid part (A^3) */
    public double meanLipidVolume() {
        double t = total(), v = 0;
        for (int i = 0; i < lipids.size(); i++) v += molPercent.get(i) * lipids.get(i).volume;
        return t > 0 ? v / t : 1100;
    }

    static final double WATER = 0.334, NT_E = 165, NT_V = 320;

    /**
     * Electron-density contrast of the core (e/A^3 vs buffer): ionizable lipid, cholesterol and non-surface helper
     * lipid, plus nucleic acid (one nucleotide per N/P ionizable lipids) and water.
     * @param np N/P ratio (Double.POSITIVE_INFINITY = no cargo); water volume fraction of the core
     */
    public double coreContrast(double np, double water) {
        double e = 0, v = 0;
        double sterolCap = surfaceHelpers() ? CORE_STEROL_PER_IL * molOf(Role.IONIZABLE) : Double.POSITIVE_INFINITY;
        double sterolTotal = molOf(Role.STEROL);
        for (int i = 0; i < lipids.size(); i++) {
            Lipid l = lipids.get(i);
            double m = molPercent.get(i);
            if (l.role == Role.STEROL && sterolTotal > 0) m *= Math.min(1, sterolCap / sterolTotal);
            // phospholipid helpers and PEG-lipids sit mainly at the surface in DSPC-type LNPs; non-lamellar helpers
            // (DOPE, GMO) build the core
            boolean core = l.role == Role.IONIZABLE || l.role == Role.STEROL || l.role == Role.OTHER
                    || (l.role == Role.HELPER && !l.propensity.equals("lamellar"));
            if (!core) continue;
            e += m * l.electrons;
            v += m * l.volume;
            if (l.role == Role.IONIZABLE && np > 0 && !Double.isInfinite(np)) { e += m * NT_E / np; v += m * NT_V / np; }
        }
        if (v == 0) return 0;
        double dry = e / v;
        return (1 - water) * dry + water * WATER - WATER;
    }

    /** cholesterol per ionizable lipid in the oil-like core: 28 / 72 (Trollmann & Boeckmann 2022) */
    static final double CORE_STEROL_PER_IL = 28.0 / 72.0;

    double molOf(Role r) {
        double t = 0;
        for (int i = 0; i < lipids.size(); i++) if (lipids.get(i).role == r) t += molPercent.get(i);
        return t;
    }

    /** DSPC-type (lamellar) helpers present: they form the surface monolayer */
    boolean surfaceHelpers() {
        for (Lipid l : lipids) if (l.role == Role.HELPER && l.propensity.equals("lamellar")) return true;
        return false;
    }

    /**
     * Contrast of the chain region of the surface monolayer (the "inner lipid layer", e/A^3 vs buffer). Monolayer
     * made of the lamellar helper, PEG-lipid anchors, ionizable lipid (9 per 22 helpers) and the cholesterol not in
     * the core (up to 63 per 22 helpers). Chains ~0.29 e/A^3, cholesterol 0.343 e/A^3. Hydrocarbon-only monolayers
     * give about -0.04; the cholesterol-rich Comirnaty shell about -0.02.
     */
    /**
     * Methylene volume of the hydrocarbon region, in A^3.
     *
     * <p>Gel 25.9 and fluid 28.7 are the DPPC values at 20 and 50 C from Nagle &amp; Tristram-Nagle, BBA 1469,
     * 159 (2000), Table 1. They differ by 11%, which is the whole reason the chain state has to be known: the
     * same lipid is a different scatterer melted and frozen, and no amount of fitting recovers that.
     */
    public static final double V_CH2_GEL = 25.9, V_CH2_FLUID = 28.7;

    /** fluid chains, the usual case; use {@link #innerShellContrast(boolean)} when the state is known */
    public double innerShellContrast() { return innerShellContrast(false); }

    /**
     * Electron-density contrast of the bilayer chain region against buffer, in e/A^3.
     *
     * <p>This is the reference the whole density scale is anchored to, so it is worth computing rather than
     * assuming. It used to be a flat 0.29 e/A^3 for the chains of every lipid, with a fixed 1000 A^3 of chain
     * per molecule, which meant every sterol-free composition returned the same -0.044 whatever it was made
     * of: pure POPC, a PC/PE mixture and a cardiolipin bilayer were indistinguishable, and a cardiolipin's
     * four chains were counted as two chains' worth of volume.
     *
     * <p>Each lipid now contributes the electrons and the volume of its own chains, built from the group
     * volumes above: a chain of n carbons with d double bonds occupies (n-1)+1 methylene volumes and holds
     * 8 electrons per methylene, 7 per double-bonded carbon and 9 for the terminal methyl. For DPPC in the
     * gel this gives a hydrocarbon volume of 829 A^3 against the 825 A^3 measured by Nagle and
     * Tristram-Nagle, and a chain-region density of 0.292 e/A^3.
     *
     * <p>Note that 0.292 is the average over the whole hydrocarbon slab, which is what a layer in this model
     * represents. It is lower than the methylene density of 8/25.9 = 0.309 quoted for gel chains, because the
     * terminal methyls occupy twice the volume for barely more electrons and leave a trough of low density at
     * the bilayer centre.
     *
     * <p>Cholesterol is not a chain molecule and is handled as before: the fraction of it that partitions
     * into the hydrocarbon region is added at its own density of 216 e per 630 A^3.
     *
     * @param gelChains whether the chains are frozen. A wide-angle dataset answers this directly; without one
     *                  the fluid state is the safer default, being the commoner one at room temperature.
     */
    public double innerShellContrast(boolean gelChains) {
        double vCH2 = gelChains ? V_CH2_GEL : V_CH2_FLUID;
        double helper = 0;
        for (int i = 0; i < lipids.size(); i++)
            if (lipids.get(i).role == Role.HELPER && lipids.get(i).propensity.equals("lamellar")) helper += molPercent.get(i);
        if (helper == 0) return -0.035;
        double sterolLeft = Math.max(0, molOf(Role.STEROL) - CORE_STEROL_PER_IL * molOf(Role.IONIZABLE));
        double chol = Math.min(sterolLeft, 63.0 / 22.0 * helper);
        double il = Math.min(molOf(Role.IONIZABLE), 9.0 / 22.0 * helper), peg = molOf(Role.PEG);

        // the chain-bearing lipids, each weighted by how much of it is in the hydrocarbon region
        double vol = 0, el = 0;
        double scaled = scaleOf(Role.HELPER, helper), scaledIl = scaleOf(Role.IONIZABLE, il),
                scaledPeg = scaleOf(Role.PEG, peg);
        for (int i = 0; i < lipids.size(); i++) {
            Lipid l = lipids.get(i);
            double share = l.role == Role.HELPER ? scaled : l.role == Role.IONIZABLE ? scaledIl
                    : l.role == Role.PEG ? scaledPeg : 0;
            if (share <= 0) continue;
            double mol = molPercent.get(i) * share;
            double v = l.tailVolume(vCH2);
            if (v <= 0) { v = FALLBACK_TAIL_VOLUME; el += FALLBACK_TAIL_DENSITY * mol * v; }
            else el += mol * l.tailElectrons();
            vol += mol * v;
        }
        vol += chol * 630; el += chol * 216;
        return vol <= 0 ? -0.035 : el / vol - WATER;
    }

    /** chains that are too branched to describe fall back on the old flat figures */
    private static final double FALLBACK_TAIL_VOLUME = 1000, FALLBACK_TAIL_DENSITY = 0.29;

    /** what fraction of a role's declared mol% sits in the hydrocarbon region */
    private double scaleOf(Role role, double inRegion) {
        double total = molOf(role);
        return total <= 0 ? 0 : Math.min(1.0, inRegion / total);
    }

    /** PEG corona: {thickness (A, Flory radius of the PEG coil, 3.5 N^0.6), contrast (e/A^3)} for core radius r0 */
    public double[] corona(double r0, double rHead) {
        double nPeg = 0, unitsSum = 0;
        for (int i = 0; i < lipids.size(); i++)
            if (lipids.get(i).role == Role.PEG) { nPeg += molPercent.get(i); unitsSum += molPercent.get(i) * lipids.get(i).pegUnits; }
        if (nPeg == 0) return new double[]{0, 0};
        double units = unitsSum / nPeg;
        double thickness = 3.5 * Math.pow(units, 0.6);   // PEG2000 (45 units) ~ 34 A, cf. 40 A in Li et al.
        // PEG-lipids per particle from the lipid volume of the particle
        double nLipid = 0.76 * 4.0 / 3.0 * Math.PI * Math.pow(rHead, 3) / meanLipidVolume();
        double nPegLipids = nLipid * nPeg / total();
        double shellVol = 4.0 / 3.0 * Math.PI * (Math.pow(rHead + thickness, 3) - Math.pow(rHead, 3));
        double phi = Math.min(0.5, nPegLipids * units * 61 / shellVol);   // 61 A^3 per ethylene oxide unit
        return new double[]{thickness, phi * (24.0 / 61 - WATER)};
    }

    /** internal phase the mixture tends to form (Pattipeiluhu 2024, Zheng 2023, Yanez Arteta 2018) */
    public String expectedPhase() {
        double hii = 0, cubic = 0, lam = 0;
        for (int i = 0; i < lipids.size(); i++) {
            Lipid l = lipids.get(i);
            double f = molPercent.get(i);
            if (l.role == Role.IONIZABLE && l.propensity.isEmpty()) continue;
            if (l.propensity.equals("HII")) hii += f;
            else if (l.propensity.equals("cubic")) cubic += f;
            else if (l.propensity.equals("lamellar")) lam += f;
        }
        // The phase is set by the lipids that have a shape preference. Cholesterol and PEG-lipid have none, so
        // they must come out of the denominator: PC/cholesterol 1:1 is a lamellar bilayer, not a half-lamellar
        // one, and dividing by the total made it miss every threshold and fall through to the LNP default.
        double shaping = hii + cubic + lam;
        double t = total();
        if (shaping > 0) { hii /= shaping; cubic /= shaping; lam /= shaping; }
        // no ionizable lipid means this is not an LNP at all: a phospholipid with or without sterol makes bilayers
        if (fraction(Role.IONIZABLE) <= 0.02 && lam > 0.5) {
            double sterol = sterolMolPercent();
            return sterol > 30
                    ? String.format("lamellar bilayer, liquid-ordered (Lₒ): %.0f mol%% sterol is enough to "
                            + "abolish gel chain packing, so expect one broad wide-angle peak near 4.6 Å", sterol)
                    : sterol > 5
                            ? "lamellar bilayer; the sterol broadens the chain packing without removing the gel phase"
                            : "lamellar bilayer (vesicles or multilayers), gel or fluid depending on temperature";
        }
        if (cubic > 0.5) return fraction(Role.IONIZABLE) > 0.02
                ? "bicontinuous cubic, with inverse hexagonal possible once nucleic acid is added (cuboplexes)"
                : "bicontinuous cubic (cubosome); Pn3m and Im3m may coexist";
        if (hii > 0.6) return "inverse hexagonal (HII), as for DOPE-rich LNPs";
        if (lam > 0.5) return "lamellar (lipoplex-like multilayers)";
        double helperHii = 0;
        for (int i = 0; i < lipids.size(); i++) if (lipids.get(i).name.equals("DOPE")) helperHii += molPercent.get(i) / t;
        if (helperHii > 0.25) return "mixed lamellar / inverse hexagonal";
        return "amorphous ionizable-lipid core; with RNA a disordered inverse hexagonal packing (broad peak, d ≈ 5–6.5 nm)";
    }

    public String summary(double np, double water) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lipids.size(); i++)
            sb.append(i == 0 ? "" : " / ").append(lipids.get(i).name);
        sb.append(" = ");
        for (int i = 0; i < molPercent.size(); i++) sb.append(i == 0 ? "" : "/").append(Fields.fmt(molPercent.get(i)));
        sb.append(String.format(" mol%%; ionizable %.1f %%, mean lipid volume %.0f Å³, core contrast %+.3f e/Å³",
                ionizableMolPercent(), meanLipidVolume(), coreContrast(np, water)));
        return sb.toString();
    }
}
