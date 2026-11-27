package version4.FormFactors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The brief: everything the LNP module has concluded about one dataset, collected into one piece of text.
 *
 * <p>Each tab already writes a report that carries its own numbers — the assessment, the fit with its
 * parameters and errors, the expectation checks, the lattice indexing, the lamellarity, the chain-packing peak,
 * the populations, the cargo arrangement. Read one at a time they are hard to hold together, and a sample is
 * understood by reading them against each other. The brief is those reports, labelled and concatenated, so the
 * whole analysis can be read in one pass, copied into a notebook, or pasted into a methods section.</p>
 *
 * <p>It is assembled from what the tabs have already displayed rather than recomputed. The numbers a reader
 * sees on the Fit tab and the numbers in the brief are then the same numbers by construction, and the brief
 * cannot drift away from the panel it sits in. Sections with nothing in them are dropped, so the brief says
 * only what has actually been run.</p>
 */
final class LnpBrief {

    private LnpBrief() { }

    /** Builds a brief. Sections are kept in the order they are added; blank ones are dropped. */
    static final class Builder {
        private final Map<String, String> sections = new LinkedHashMap<>();

        Builder add(String heading, String body) {
            if (body != null && !body.trim().isEmpty()) sections.put(heading, body.trim());
            return this;
        }

        /** Section headings present, in order. Used by the tests and by the tab's own listing. */
        List<String> headings() { return new ArrayList<>(sections.keySet()); }

        String build() {
            if (sections.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : sections.entrySet()) {
                sb.append("==== ").append(e.getKey()).append(" ====\n").append(e.getValue()).append("\n\n");
            }
            return sb.toString().trim() + "\n";
        }
    }

}
