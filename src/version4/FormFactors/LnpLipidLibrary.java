package version4.FormFactors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The user's private lipid library: proprietary lipids and saved formulations. It lives in one JSON file on the
 * user's own computer (default ~/.scatter/lnp_lipid_library.json, or the path in the system property
 * {@code scatter.lnpLibrary}); Scatter never sends it anywhere. Teams can share a library by exporting it to a file
 * (e.g. on a company drive) and importing it, or by pointing {@code scatter.lnpLibrary} at a shared file.
 *
 * <pre>
 * { "version": 1,
 *   "lipids": [ { "name": "XL-17", "role": "IONIZABLE", "formula": "C50H99NO5", "electrons": 446, "mw": 794.3,
 *                 "volume": 1420, "pegUnits": 0, "propensity": "", "notes": "batch 3 density 0.93" } ],
 *   "formulations": { "XL-17 mRNA v2": [ ["XL-17", 50], ["DSPC", 10], ["Cholesterol", 38.5], ["DMG-PEG2000", 1.5] ] } }
 * </pre>
 */
public final class LnpLipidLibrary {

    private static LnpLipidLibrary instance;

    /** the library of this Scatter session, loaded on first use */
    public static synchronized LnpLipidLibrary get() {
        if (instance == null) {
            instance = new LnpLipidLibrary(defaultFile());
            try { instance.load(); } catch (IOException e) { instance.loadError = e.getMessage(); }
        }
        return instance;
    }

    static File defaultFile() {
        String p = System.getProperty("scatter.lnpLibrary");
        if (p != null && !p.trim().isEmpty()) return new File(p.trim());
        return new File(new File(System.getProperty("user.home"), ".scatter"), "lnp_lipid_library.json");
    }

    private final File file;
    private final Map<String, LnpComposition.Lipid> lipids = new LinkedHashMap<>();
    private final Map<String, Object[][]> formulations = new LinkedHashMap<>();
    /** non-null when the file exists but could not be read (it is then never overwritten silently) */
    public String loadError;

    LnpLipidLibrary(File file) { this.file = file; }

    public File file() { return file; }

    public List<LnpComposition.Lipid> lipids() { return new ArrayList<>(lipids.values()); }

    public LnpComposition.Lipid lipid(String name) { return lipids.get(name); }

    public List<String> formulationNames() { return new ArrayList<>(formulations.keySet()); }

    public Object[][] formulation(String name) { return formulations.get(name); }

    // ------------------------------------------------------------------ editing

    /** checks a lipid before it is stored; returns problems (empty = fine) and warnings prefixed with "!" */
    public static List<String> validate(LnpComposition.Lipid l, String replacing) {
        List<String> out = new ArrayList<>();
        if (l.name == null || l.name.trim().isEmpty()) out.add("Give the lipid a name.");
        else if (LnpComposition.BUILT_IN.containsKey(l.name.trim())) out.add("'" + l.name + "' is a built-in lipid; choose another name.");
        if (!(l.electrons > 0)) out.add("Electrons per molecule must be > 0 (enter a formula or the number).");
        if (!(l.volume >= 50 && l.volume <= 20000)) out.add("Molecular volume should be between 50 and 20000 Å³.");
        if (l.role == LnpComposition.Role.PEG && l.pegUnits <= 0) out.add("A PEG-lipid needs the PEG length (ethylene-oxide units or PEG molecular weight).");
        if (out.isEmpty()) {
            double rho = l.density();
            if (rho < 0.26 || rho > 0.55)
                out.add(String.format("!Mean electron density %.3f e/Å³ is unusual for a lipid (typically 0.29–0.36). Check the volume.", rho));
        }
        return out;
    }

    public void put(LnpComposition.Lipid l, String replacing) throws IOException {
        if (replacing != null && !replacing.equals(l.name)) {
            lipids.remove(replacing);
            // keep saved formulations pointing at the renamed lipid
            for (Object[][] rows : formulations.values()) for (Object[] r : rows) if (replacing.equals(r[0])) r[0] = l.name;
        }
        lipids.put(l.name, l);
        save();
    }

    public void remove(String name) throws IOException {
        lipids.remove(name);
        save();
    }

    /** formulations using this lipid (shown before deleting it) */
    public List<String> usedIn(String lipid) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object[][]> e : formulations.entrySet())
            for (Object[] r : e.getValue()) if (lipid.equals(r[0])) { out.add(e.getKey()); break; }
        return out;
    }

    public void putFormulation(String name, LnpComposition c) throws IOException {
        Object[][] rows = new Object[c.lipids.size()][];
        for (int i = 0; i < rows.length; i++) rows[i] = new Object[]{c.lipids.get(i).name, c.molPercent.get(i)};
        formulations.put(name, rows);
        save();
    }

    public void removeFormulation(String name) throws IOException {
        formulations.remove(name);
        save();
    }

    // ------------------------------------------------------------------ file I/O

    private static ObjectMapper mapper() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        return m;
    }

    void load() throws IOException {
        lipids.clear();
        formulations.clear();
        if (!file.exists()) return;
        Map<?, ?> root = mapper().readValue(file, Map.class);
        readInto(root, true);
    }

    /** @param replace true: entries with the same name are replaced; returns {lipids added, formulations added, skipped} */
    private int[] readInto(Map<?, ?> root, boolean replace) {
        int nl = 0, nf = 0, skipped = 0;
        Object ls = root.get("lipids");
        if (ls instanceof List) for (Object o : (List<?>) ls) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            try {
                String name = String.valueOf(m.get("name")).trim();
                if (LnpComposition.BUILT_IN.containsKey(name)) { skipped++; continue; }
                if (!replace && lipids.containsKey(name)) { skipped++; continue; }
                LnpComposition.Role role = LnpComposition.Role.valueOf(String.valueOf(m.get("role")));
                LnpComposition.Lipid l = new LnpComposition.Lipid(name, role, num(m.get("electrons")), num(m.get("volume")),
                        (int) Math.round(num(m.get("pegUnits"), 0)), str(m.get("propensity")), str(m.get("formula")),
                        num(m.get("mw"), Double.NaN), false, str(m.get("notes")));
                if (!validate(l, null).stream().allMatch(p -> p.startsWith("!"))) { skipped++; continue; }
                lipids.put(name, l);
                nl++;
            } catch (RuntimeException bad) {
                skipped++;
            }
        }
        Object fs = root.get("formulations");
        if (fs instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) fs).entrySet()) {
            String name = String.valueOf(e.getKey());
            if (LnpComposition.PRESETS.containsKey(name) || (!replace && formulations.containsKey(name))) { skipped++; continue; }
            if (!(e.getValue() instanceof List)) { skipped++; continue; }
            List<Object[]> rows = new ArrayList<>();
            for (Object r : (List<?>) e.getValue())
                if (r instanceof List && ((List<?>) r).size() >= 2)
                    rows.add(new Object[]{String.valueOf(((List<?>) r).get(0)), num(((List<?>) r).get(1))});
            if (rows.isEmpty()) { skipped++; continue; }
            formulations.put(name, rows.toArray(new Object[0][]));
            nf++;
        }
        return new int[]{nl, nf, skipped};
    }

    private Map<String, Object> toJson(boolean includeFormulations) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        List<Map<String, Object>> ls = new ArrayList<>();
        for (LnpComposition.Lipid l : lipids.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", l.name);
            m.put("role", l.role.name());
            m.put("formula", l.formula);
            m.put("electrons", l.electrons);
            if (!Double.isNaN(l.mw)) m.put("mw", Math.round(l.mw * 1000) / 1000.0);
            m.put("volume", l.volume);
            m.put("pegUnits", l.pegUnits);
            m.put("propensity", l.propensity);
            m.put("notes", l.notes);
            ls.add(m);
        }
        root.put("lipids", ls);
        if (includeFormulations) {
            Map<String, Object> fs = new LinkedHashMap<>();
            for (Map.Entry<String, Object[][]> e : formulations.entrySet()) {
                List<List<Object>> rows = new ArrayList<>();
                for (Object[] r : e.getValue()) { List<Object> row = new ArrayList<>(); row.add(r[0]); row.add(r[1]); rows.add(row); }
                fs.put(e.getKey(), rows);
            }
            root.put("formulations", fs);
        }
        return root;
    }

    /** writes atomically (temp file + move) so a crash never leaves a half-written library */
    void save() throws IOException {
        if (loadError != null)
            throw new IOException("The library file could not be read (" + loadError + "); not overwriting it. Fix or move " + file);
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File tmp = new File(dir, file.getName() + ".tmp");
        mapper().writeValue(tmp, toJson(true));
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** exports the private lipids (and optionally the saved formulations) to a file for colleagues */
    public void exportTo(File f, boolean includeFormulations) throws IOException {
        mapper().writeValue(f, toJson(includeFormulations));
    }

    /**
     * Merges a library file into this one.
     * @param replace true: entries with the same name are overwritten; false: existing entries are kept
     * @return {lipids added, formulations added, skipped}
     */
    public int[] importFrom(File f, boolean replace) throws IOException {
        Map<?, ?> root = mapper().readValue(f, Map.class);
        int[] n = readInto(root, replace);
        save();
        return n;
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    private static double num(Object o, double def) {
        if (o == null) return def;
        try { return num(o); } catch (RuntimeException e) { return def; }
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
