package version4.Buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a buffer written the way people write it on a tube:
 * {@code "20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 5% glycerol, 1 mM TCEP"}.
 *
 * <ul>
 *   <li>amounts: M, mM, uM/µM, nM, mg/mL, %, %(v/v), %(w/v), and "1x"/"0.5x" for named buffers;</li>
 *   <li>pH anywhere ("pH 7.5", "pH7.5"); temperature as "10 C", "10 °C" or "283 K";</li>
 *   <li>titrants: "Tris-HCl", "HEPES/KOH", "Na-HEPES", "potassium phosphate";</li>
 *   <li>named buffers from the library ("PBS", "0.5x PBS", "TBS") expand to their recipes;</li>
 *   <li>commas, semicolons and "+" separate components but are not needed between them.</li>
 * </ul>
 * Anything not in the library is kept, by name, and reported.
 */
public final class BufferParser {

    /** one ingredient as read */
    public static final class Item {
        /** null if the name is not in the library */
        public Chemical chemical;
        /** the name as written (without titrant) */
        public String name;
        public Double amount;
        /** M, mM, uM, nM, mg/mL, %(v/v), %(w/v), or % if it could not be decided */
        public String unit;
        /** counter-ion from the titrant ("Tris-HCl" -> Cl-), or null */
        public Chemical.Ion titrant;
        public String titrantText;

        /** concentration in mol/L, or null if it cannot be worked out */
        public Double molar() {
            if (amount == null || unit == null) return null;
            switch (unit) {
                case "M": return amount;
                case "mM": return amount*1e-3;
                case "uM": return amount*1e-6;
                case "nM": return amount*1e-9;
            }
            if (chemical == null || !(chemical.molarMass > 0)) return null;
            switch (unit) {
                case "mg/mL": return amount/chemical.molarMass;
                case "%(w/v)": return amount*10.0/chemical.molarMass;
                case "%(v/v)": return Double.isNaN(chemical.density) ? null : amount/100.0*1000.0*chemical.density/chemical.molarMass;
                default: return null;
            }
        }

        /** "20 mM HEPES (NaOH)" */
        @Override
        public String toString() {
            StringBuilder s = new StringBuilder();
            if (amount != null) s.append(num(amount)).append(' ').append(unit).append(' ');
            s.append(chemical != null ? chemical.name : name);
            if (titrantText != null) s.append(" (").append(titrantText).append(')');
            return s.toString();
        }
    }

    /** the whole buffer as read */
    public static final class Parsed {
        public final String text;
        public final List<Item> items = new ArrayList<>();
        public Double pH;
        public Double temperatureC;
        public final List<String> warnings = new ArrayList<>();

        Parsed(String text) { this.text = text; }

        public boolean isEmpty() { return items.isEmpty() && pH == null; }
    }

    private static final String NUM = "(\\d+(?:\\.\\d*)?|\\.\\d+)";
    private static final String UNIT = "(mM|uM|µM|μM|nM|M|mg\\s*/\\s*ml|mg\\s*/\\s*mL|%\\s*\\(?\\s*[vwVW]\\s*/\\s*[vV]\\s*\\)?|%|[xX])";
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\w.])" + NUM + "\\s*" + UNIT + "(?![A-Za-z0-9])");
    private static final Pattern PH = Pattern.compile("(?i)(?<![a-z])p\\s*H\\s*[=:~]?\\s*" + NUM);
    private static final Pattern TEMP = Pattern.compile("(?i)(?:\\bat\\s+|@\\s*)?" + "(-?\\d+(?:\\.\\d+)?)\\s*(°\\s*C|º\\s*C|deg\\s*C|C|K)(?![A-Za-z0-9])");
    private static final Pattern RATIO_AFTER = Pattern.compile("(?i)\\(?\\s*([vw])\\s*/\\s*v\\s*\\)?");
    private static final Pattern SUFFIX_TITRANT = Pattern.compile("(?i)^(.+?)\\s*[-/\\s]\\s*(HCl|NaOH|KOH|LiOH|NH4OH|NH3|acetic acid|HAc)$");
    private static final Pattern PREFIX_TITRANT = Pattern.compile("(?i)^(Na|K|sodium|potassium|ammonium)\\s*[-\\s]\\s*(.+)$");

    private BufferParser() { }

    public static Parsed parse(String text) { return parse(text, BufferLibrary.standard()); }

    public static Parsed parse(String text, BufferLibrary lib) {
        Parsed out = new Parsed(text == null ? "" : text.trim());
        if (text == null || text.trim().isEmpty()) return out;
        String s = " " + text.replace(' ', ' ').replace(' ', ' ') + " ";

        Matcher m = PH.matcher(s);
        if (m.find()) {
            out.pH = Double.parseDouble(m.group(1));
            s = s.substring(0, m.start()) + " , " + s.substring(m.end());
            if (PH.matcher(s).find()) out.warnings.add("more than one pH given; using " + num(out.pH));
            s = PH.matcher(s).replaceAll(" , ");
        }
        // temperature only with an explicit degree sign or K, or "at"/"@" before a plain C
        m = TEMP.matcher(s);
        StringBuffer kept = new StringBuffer();
        while (m.find()) {
            String unit = m.group(2).toUpperCase(Locale.ROOT).replaceAll("\\s", "");
            boolean explicit = unit.startsWith("°") || unit.startsWith("º") || unit.startsWith("DEG")
                    || m.group(0).trim().toLowerCase(Locale.ROOT).startsWith("at") || m.group(0).trim().startsWith("@");
            double v = Double.parseDouble(m.group(1));
            if (unit.equals("K") && v > 200 && v < 400) explicit = true;
            if (explicit && out.temperatureC == null) {
                out.temperatureC = unit.equals("K") ? v - 273.15 : v;
                m.appendReplacement(kept, " , ");
            } else {
                m.appendReplacement(kept, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(kept);
        s = kept.toString();

        // components: an amount starts one; the name runs to the next amount or separator
        List<int[]> starts = new ArrayList<>();
        m = AMOUNT.matcher(s);
        while (m.find()) starts.add(new int[]{m.start(), m.end()});
        // text before the first amount: names without amounts (e.g. "PBS")
        int first = starts.isEmpty() ? s.length() : starts.get(0)[0];
        for (String bare : s.substring(0, first).split("[,;+]")) addBare(out, bare, lib);
        for (int i = 0; i < starts.size(); i++) {
            int[] a = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1)[0] : s.length();
            Matcher am = AMOUNT.matcher(s.substring(a[0], a[1]));
            am.find();
            String rest = s.substring(a[1], end);
            // "20 mM HEPES, 150 mM NaCl": stop at a separator; anything after it is a bare name
            String[] parts = rest.split("[,;+]", -1);
            addItem(out, Double.parseDouble(am.group(1)), am.group(2), parts[0], lib);
            for (int k = 1; k < parts.length; k++) addBare(out, parts[k], lib);
        }
        return out;
    }

    private static void addBare(Parsed out, String raw, BufferLibrary lib) {
        String name = clean(raw);
        if (name.isEmpty()) return;
        if (expandPreset(out, name, 1.0, lib)) return;
        Item it = item(name, lib);
        out.items.add(it);
        if (it.chemical == null) out.warnings.add("\"" + name + "\" is not in the buffer library");
        else out.warnings.add(it.chemical.name + " has no concentration");
    }

    private static void addItem(Parsed out, double amount, String unitRaw, String rawName, BufferLibrary lib) {
        String unit = unit(unitRaw);
        String name = rawName;
        Matcher r = RATIO_AFTER.matcher(name);
        if (unit.equals("%") && r.find()) {               // "5% glycerol (v/v)"
            unit = r.group(1).equalsIgnoreCase("v") ? "%(v/v)" : "%(w/v)";
            name = name.substring(0, r.start()) + name.substring(r.end());
        }
        name = clean(name);
        if (name.isEmpty()) {
            out.warnings.add(num(amount) + " " + unit + " of what?");
            return;
        }
        if (unit.equals("x")) {
            if (!expandPreset(out, name, amount, lib)) out.warnings.add("\"" + num(amount) + "x " + name + "\": not a named buffer in the library");
            return;
        }
        if (expandPreset(out, name, 1.0, lib)) {
            out.warnings.add(name + " is a named buffer; its amount (" + num(amount) + " " + unit + ") was ignored");
            return;
        }
        Item it = item(name, lib);
        it.amount = amount;
        it.unit = unit;
        if (unit.equals("%")) {
            if (it.chemical != null) {
                it.unit = it.chemical.percentUnit;
                out.warnings.add(num(amount) + "% " + it.chemical.name + " read as " + it.chemical.percentUnit);
            } else {
                out.warnings.add(num(amount) + "% " + name + ": v/v or w/v?");
            }
        }
        if (it.chemical == null) out.warnings.add("\"" + name + "\" is not in the buffer library: counted as uncharged");
        else if (it.chemical.note != null) out.warnings.add(it.chemical.note);
        if (it.chemical != null && it.molar() == null && !it.unit.equals("%")) {
            out.warnings.add(it.chemical.name + ": cannot convert " + it.unit + " to a molar concentration");
        }
        out.items.add(it);
    }

    /** look the name up, with a titrant split off if needed */
    private static Item item(String name, BufferLibrary lib) {
        Item it = new Item();
        it.name = name;
        it.chemical = lib.find(name);
        if (it.chemical != null) return it;
        Matcher m = SUFFIX_TITRANT.matcher(name);
        if (m.matches() && lib.find(m.group(1)) != null) {
            it.chemical = lib.find(m.group(1));
            it.name = m.group(1).trim();
            it.titrantText = m.group(2);
            it.titrant = BufferLibrary.TITRANTS.get(m.group(2).toLowerCase(Locale.ROOT));
            return it;
        }
        m = PREFIX_TITRANT.matcher(name);
        if (m.matches() && lib.find(m.group(2)) != null) {
            it.chemical = lib.find(m.group(2));
            it.name = m.group(2).trim();
            it.titrantText = m.group(1);
            it.titrant = BufferLibrary.TITRANTS.get(m.group(1).toLowerCase(Locale.ROOT));
            return it;
        }
        return it;
    }

    private static boolean expandPreset(Parsed out, String name, double factor, BufferLibrary lib) {
        for (Map.Entry<String, String> e : lib.presets().entrySet()) {
            if (!e.getKey().equalsIgnoreCase(name.trim())) continue;
            Parsed p = parse(e.getValue(), lib);
            for (Item it : p.items) {
                if (it.amount != null) it.amount = it.amount*factor;
                out.items.add(it);
            }
            if (out.pH == null) out.pH = p.pH;
            out.warnings.addAll(p.warnings);
            return true;
        }
        return false;
    }

    private static String unit(String raw) {
        String u = raw.replaceAll("\\s", "");
        if (u.equalsIgnoreCase("x")) return "x";
        if (u.startsWith("%")) {
            String l = u.toLowerCase(Locale.ROOT);
            if (l.contains("v/v")) return "%(v/v)";
            if (l.contains("w/v")) return "%(w/v)";
            return "%";
        }
        if (u.equalsIgnoreCase("mg/ml")) return "mg/mL";
        if (u.equals("µM") || u.equals("μM")) return "uM";
        return u;
    }

    private static String clean(String s) {
        String t = s.replaceAll("(?i)\\b(and|in|with|buffer|buffered)\\b", " ")
                .replaceAll("[()\\[\\]]", " ")
                .replaceAll("\\s+", " ").trim();
        t = t.replaceAll("^[,;:+\\s]+|[,;:+\\s-]+$", "");
        return t;
    }

    static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.4g", v);
        if (s.contains("e")) return s;
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
