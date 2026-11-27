package version4.Buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Electron count of a chemical formula such as "C8H18N2O4S" or "(C2H4O)n" (n taken as 1). */
final class Formula {

    private Formula() { }

    private static final Map<String, Integer> Z = new HashMap<>();
    static {
        String[] el = {"H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar",
                "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr",
                "Rb", "Sr", "Y", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I", "Xe", "Cs", "Ba"};
        for (int i = 0; i < el.length; i++) Z.put(el[i], i + 1);
    }

    private static final Pattern TOKEN = Pattern.compile("([A-Z][a-z]?)(\\d*)|(\\()|(\\))(\\d*)");

    /** electrons in the formula; 0 for null or empty; throws for an unknown element */
    static int electrons(String formula) {
        if (formula == null || formula.trim().isEmpty()) return 0;
        String f = formula.replaceAll("[\\s·.]", "").replaceAll("\\)n", ")");
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        int total = 0;
        Matcher m = TOKEN.matcher(f);
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) throw new IllegalArgumentException("cannot read formula " + formula);
            pos = m.end();
            if (m.group(1) != null) {
                Integer z = Z.get(m.group(1));
                if (z == null) throw new IllegalArgumentException("unknown element " + m.group(1) + " in " + formula);
                int n = m.group(2).isEmpty() ? 1 : Integer.parseInt(m.group(2));
                total += z*n;
            } else if (m.group(3) != null) {
                stack.push(total);
                total = 0;
            } else {
                int n = m.group(5).isEmpty() ? 1 : Integer.parseInt(m.group(5));
                total = stack.pop() + total*n;
            }
        }
        if (pos != f.length() || !stack.isEmpty()) throw new IllegalArgumentException("cannot read formula " + formula);
        return total;
    }
}
