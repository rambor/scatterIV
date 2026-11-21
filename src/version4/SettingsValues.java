package version4;

import java.util.Locale;
import java.util.Properties;

/**
 * The values behind the Settings dialog, without any Swing: their defaults, the rules a typed value must
 * meet, number formatting that does not depend on the computer's language, and saving to and loading
 * from the settings file (see {@link AppConfig}).
 *
 * <p>Numbers are always written with a decimal point. Typed values may use a decimal comma ("0,02"),
 * as people in many countries will type them; previously a comma made the field silently fall back to
 * its default, and on computers set to such a language the dialog itself wrote "97,0" and then could
 * not read it back.</p>
 */
public final class SettingsValues {

    // defaults (as in the original dialog)
    public static final double QMIN = 0.02, QMAX = 0.14;
    public static final double QMAX_PDB = 0.4, QMAX_PDB_LIMIT = 2.0;
    public static final double DEFAULT_DMAX = 97, DMAX_LOWER_LIMIT = 10;
    public static final double DMAX_SEARCH_MIN = 37, DMAX_SEARCH_MAX = 176;
    public static final boolean EXCLUDE_WATERS = true;
    public static final int REFINEMENT_ROUNDS_INDEX = 2, REJECTION_CUTOFF_INDEX = 3, C_INDEX = 0;

    // keys in the settings file
    static final String K_QMIN = "scalingQmin", K_QMAX = "scalingQmax", K_QMAX_PDB = "pdbQmax",
            K_EXCLUDE_WATERS = "pdbExcludeWaters", K_DMAX = "defaultDmax", K_DMAX_MIN = "dmaxSearchMin",
            K_DMAX_MAX = "dmaxSearchMax", K_ROUNDS = "refinementRoundsIndex", K_CUTOFF = "rejectionCutoffIndex",
            K_C = "pointsFactorIndex";

    public double qmin = QMIN, qmax = QMAX, qmaxForPDB = QMAX_PDB, defaultDmax = DEFAULT_DMAX;
    public double dmaxSearchMin = DMAX_SEARCH_MIN, dmaxSearchMax = DMAX_SEARCH_MAX;
    public boolean excludeWaters = EXCLUDE_WATERS;
    public int refinementRoundsIndex = REFINEMENT_ROUNDS_INDEX, rejectionCutoffIndex = REJECTION_CUTOFF_INDEX, cIndex = C_INDEX;

    /** Outcome of checking a typed value: the value to use, and a message if the typed one was not accepted. */
    public static final class Checked {
        public final double value;
        public final String message;    // null when the typed value was accepted as it is

        Checked(double value, String message) {
            this.value = value;
            this.message = message;
        }

        public boolean accepted() { return message == null; }
    }

    // ------------------------------------------------------------------ numbers

    /** Number in "." or "," notation, surrounding spaces ignored; NaN if it is not a number. */
    public static double parse(String text) {
        if (text == null) return Double.NaN;
        String t = text.trim();
        if (t.isEmpty()) return Double.NaN;
        // a single comma and no point is a decimal comma
        if (t.indexOf(',') >= 0 && t.indexOf('.') < 0 && t.indexOf(',') == t.lastIndexOf(',')) t = t.replace(',', '.');
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** One decimal place, always with a point. */
    public static String format1(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    /** As many decimals as needed (q values), always with a point. */
    public static String formatQ(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }

    // ------------------------------------------------------------------ rules

    /** Scaling qmin: a positive number below qmax. */
    public Checked checkQmin(String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return new Checked(QMIN, "qmin is not a number; set to " + formatQ(QMIN));
        if (v <= 0) return new Checked(Math.min(QMIN, qmax / 2), "qmin must be greater than 0");
        if (v >= qmax) return new Checked(Math.min(QMIN, qmax / 2), "qmin must be less than qmax :: " + formatQ(qmax));
        return new Checked(v, null);
    }

    /** Scaling qmax: a number above qmin. */
    public Checked checkQmax(String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return new Checked(Math.max(QMAX, 2 * qmin), "qmax is not a number; set to " + formatQ(Math.max(QMAX, 2 * qmin)));
        if (v <= qmin) return new Checked(Math.max(QMAX, 2 * qmin), "qmax must be greater than qmin :: " + formatQ(qmin));
        return new Checked(v, null);
    }

    /** Resolution for PDB files: above 0 and at most {@value #QMAX_PDB_LIMIT}. */
    public Checked checkQmaxForPDB(String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return new Checked(QMAX_PDB, "q-max is not a number; set to " + format1(QMAX_PDB));
        if (v <= 0) return new Checked(QMAX_PDB, "q-max must be greater than 0");
        if (v > QMAX_PDB_LIMIT) return new Checked(QMAX_PDB, "q-max too large! (at most " + format1(QMAX_PDB_LIMIT) + ")");
        return new Checked(v, null);
    }

    /** Default dmax: at least {@value #DMAX_LOWER_LIMIT} A. */
    public Checked checkDefaultDmax(String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return new Checked(DEFAULT_DMAX, "d-max is not a number; set to " + format1(DEFAULT_DMAX));
        if (v < DMAX_LOWER_LIMIT) return new Checked(DEFAULT_DMAX, "d-max too small! (at least " + format1(DMAX_LOWER_LIMIT) + ")");
        return new Checked(v, null);
    }

    /** Lower end of the dmax search: positive and below the upper end (else 90 % of it). */
    public Checked checkDmaxSearchMin(String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return new Checked(DMAX_SEARCH_MIN, "min is not a number; set to " + format1(DMAX_SEARCH_MIN));
        if (v <= 0) return new Checked(Math.min(DMAX_SEARCH_MIN, 0.9 * dmaxSearchMax), "min must be greater than 0");
        if (v >= dmaxSearchMax) return new Checked(0.9 * dmaxSearchMax, "min must be less than max :: " + format1(dmaxSearchMax));
        return new Checked(v, null);
    }

    /** Upper end of the dmax search: above the lower end (else 1.5 times it). */
    public Checked checkDmaxSearchMax(String text) {
        double v = parse(text);
        if (Double.isNaN(v)) return new Checked(Math.max(DMAX_SEARCH_MAX, 1.5 * dmaxSearchMin), "max is not a number");
        if (v <= dmaxSearchMin) return new Checked(1.5 * dmaxSearchMin, "max must be greater than min :: " + format1(dmaxSearchMin));
        return new Checked(v, null);
    }

    // ------------------------------------------------------------------ settings file

    /** Adds these values to the settings to be saved. */
    public void store(Properties p) {
        p.setProperty(K_QMIN, Double.toString(qmin));
        p.setProperty(K_QMAX, Double.toString(qmax));
        p.setProperty(K_QMAX_PDB, Double.toString(qmaxForPDB));
        p.setProperty(K_EXCLUDE_WATERS, Boolean.toString(excludeWaters));
        p.setProperty(K_DMAX, Double.toString(defaultDmax));
        p.setProperty(K_DMAX_MIN, Double.toString(dmaxSearchMin));
        p.setProperty(K_DMAX_MAX, Double.toString(dmaxSearchMax));
        p.setProperty(K_ROUNDS, Integer.toString(refinementRoundsIndex));
        p.setProperty(K_CUTOFF, Integer.toString(rejectionCutoffIndex));
        p.setProperty(K_C, Integer.toString(cIndex));
    }

    /**
     * Values from a settings file. Missing or broken entries, and values that break the rules (e.g. a
     * hand-edited file with qmin above qmax), keep their defaults. Combo-box indices are checked against
     * the number of choices each box has.
     */
    public static SettingsValues load(Properties p, int roundsChoices, int cutoffChoices, int cChoices) {
        SettingsValues v = new SettingsValues();
        if (p == null) return v;
        double qmin = parse(p.getProperty(K_QMIN)), qmax = parse(p.getProperty(K_QMAX));
        if (qmin > 0 && qmax > qmin) { v.qmin = qmin; v.qmax = qmax; }
        double pdb = parse(p.getProperty(K_QMAX_PDB));
        if (pdb > 0 && pdb <= QMAX_PDB_LIMIT) v.qmaxForPDB = pdb;
        String waters = p.getProperty(K_EXCLUDE_WATERS);
        if ("true".equalsIgnoreCase(waters) || "false".equalsIgnoreCase(waters)) v.excludeWaters = Boolean.parseBoolean(waters);
        double dmax = parse(p.getProperty(K_DMAX));
        if (dmax >= DMAX_LOWER_LIMIT) v.defaultDmax = dmax;
        double lo = parse(p.getProperty(K_DMAX_MIN)), hi = parse(p.getProperty(K_DMAX_MAX));
        if (lo > 0 && hi > lo) { v.dmaxSearchMin = lo; v.dmaxSearchMax = hi; }
        v.refinementRoundsIndex = index(p.getProperty(K_ROUNDS), roundsChoices, REFINEMENT_ROUNDS_INDEX);
        v.rejectionCutoffIndex = index(p.getProperty(K_CUTOFF), cutoffChoices, REJECTION_CUTOFF_INDEX);
        v.cIndex = index(p.getProperty(K_C), cChoices, C_INDEX);
        return v;
    }

    private static int index(String text, int choices, int fallback) {
        double v = parse(text);
        if (Double.isNaN(v) || v != Math.rint(v) || v < 0 || v >= choices) return fallback;
        return (int) v;
    }
}
