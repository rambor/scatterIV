package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The methods page states the constants the panel computes with. A reference page that drifts from the code is
 * worse than none, so these check that each quoted value is still the one in use.
 */
public class LnpFormulasTest {

    private static final String PAGE = LnpFormulas.page();

    /** The minimal-surface constants quoted in the table must be the ones the geometry uses. */
    @Test
    public void minimalSurfaceConstantsMatchTheCode() {
        // r_w = sqrt(-A0 / 2 pi chi) a - l, so the quoted A0 and chi have to reproduce the computed radius
        double[][] expect = {{1.9189, -2}, {2.3451, -4}, {3.0915, -8}};
        String[] phase = {"Pn3m", "Im3m", "Ia3d"};
        for (int i = 0; i < phase.length; i++) {
            assertTrue("the page should quote A0 for " + phase[i], PAGE.contains(String.valueOf(expect[i][0])));
            LnpLattice.Result r = new LnpLattice.Result();
            r.lattice = LnpLattice.byName(phase[i]);
            r.a = 100;
            double fromPage = 100 * Math.sqrt(-expect[i][0] / (2 * Math.PI * expect[i][1])) - 17;
            assertEquals(phase[i] + ": the quoted constants must give the computed channel radius",
                    fromPage, r.channelRadius(17), 1e-9);
        }
    }

    /** The Bonnet ratios in the table must be the ones used to guess a coexisting lattice parameter. */
    @Test
    public void bonnetRatiosMatchTheCode() {
        double pn3m = LnpLattice.bonnetRatio(LnpLattice.byName("Pn3m"));
        assertEquals(1.279, LnpLattice.bonnetRatio(LnpLattice.byName("Im3m")) / pn3m, 0.001);
        assertEquals(1.576, LnpLattice.bonnetRatio(LnpLattice.byName("Ia3d")) / pn3m, 0.001);
        assertTrue("the page should quote 1.279", PAGE.contains("1.279"));
        assertTrue("the page should quote 1.576", PAGE.contains("1.576"));
    }

    /** The hexagonal coefficients are easy to mistype and change every channel radius if wrong. */
    @Test
    public void hexagonalCoefficientsMatchTheCode() {
        assertTrue("the page should quote 0.5256", PAGE.contains("0.5256"));
        assertTrue("the page should quote 0.994", PAGE.contains("0.994"));
        LnpLattice.Lattice hex = null;
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) if (l.hexagonal) { hex = l; break; }
        LnpLattice.Result r = new LnpLattice.Result();
        r.lattice = hex;
        r.a = 50;
        assertEquals((0.5256 * 50 - 17) / 0.994, r.channelRadius(17), 1e-9);
    }

    /** The nucleotide volume quoted is the one the cargo estimate divides by. */
    @Test
    public void nucleotideVolumeMatchesTheCode() {
        assertEquals(320, LnpExpectations.NUCLEOTIDE_VOLUME, 1e-9);
        assertTrue("the page should quote 320 Å³", PAGE.contains("320 Å³"));
    }

    /**
     * The page has to teach the step a newcomer meets first: a peak position is a distance. Bragg's law, the
     * conversion and a worked example all have to be there, with the order series that follows from it.
     */
    @Test
    public void theFirstSectionExplainsPeakPositionToDistance() {
        String opening = PAGE.substring(PAGE.indexOf("From a peak position"), PAGE.indexOf("Model-free assessment"));
        assertTrue("the conversion itself", opening.contains("d = 2π / q"));
        assertTrue("related to the familiar Bragg's law", opening.contains("2d sin") && opening.contains("Bragg"));
        assertTrue("a worked example with a real number", opening.contains("0.1126"));
        assertTrue("and the answer to it", opening.contains("55.8 Å") && opening.contains("5.58 nm"));
        assertTrue("the order series", opening.contains("2πh / d"));
        assertTrue("what 1:2:3 means", opening.contains("1 : 2 : 3"));
        assertTrue("and the units trap", opening.contains("nm⁻¹"));
    }

    /** The bilayer read from the order intensities, including the ambiguity that comes with it. */
    @Test
    public void orderIntensitySectionStatesTheMethodAndItsLimit() {
        String part = PAGE.substring(PAGE.indexOf("order intensities say"), PAGE.indexOf("Ordered domain size"));
        assertTrue("the Lorentz-corrected amplitude", part.contains("√I"));
        assertTrue("the bilayer form factor", part.contains("cos(q·d"));
        assertTrue("a weak order is structure", part.toLowerCase().contains("zero of f"));
        assertTrue("and the sign ambiguity must be stated", part.contains("sign of F is not measured"));
    }

    /** Sections must be numbered once each, in order, so a cross-reference points where it says. */
    @Test
    public void sectionsAreNumberedInOrder() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(">(\\d+)\\. ").matcher(PAGE);
        int expected = 1;
        while (m.find()) assertEquals("sections run in order", expected++, Integer.parseInt(m.group(1)));
        assertTrue("every section should be numbered", expected > 14);
    }

    /** Scherrer's shape factor. */
    @Test
    public void scherrerConstantIsStated() {
        assertTrue("the page should state K = 0.9", PAGE.contains("K = 0.9"));
    }

    /** Every section and the reference list must be present, and the page must be valid enough to render. */
    @Test
    public void pageIsCompleteAndWellFormed() {
        for (String section : new String[]{"From a peak position to a distance", "Guinier", "form factor",
                "Bragg indexing", "Indexing check", "order intensities", "domain size", "Water-channel geometry",
                "Water volume fraction", "Minimal-surface constants", "Sponge phase", "Phase decomposition",
                "charge balance", "References"})
            assertTrue("the page should cover " + section, PAGE.toLowerCase().contains(section.toLowerCase()));

        // every paper the panel draws a relation, a constant or a reference value from
        for (String citation : new String[]{"Marsh", "Mendozza", "Reese", "Teubner", "Caselli", "Hammel",
                "Barriga", "Yanez Arteta", "Gilbert", "Jin", "Li, H.", "Malheiros", "Pattipeiluhu", "Spinozzi",
                "Thi", "Trollmann", "Zheng"})
            assertTrue("the page should cite " + citation, PAGE.contains(citation));

        assertEquals("list tags should balance", count(PAGE, "<li"), count(PAGE, "</li>"));
        assertEquals("table rows should balance", count(PAGE, "<tr"), count(PAGE, "</tr>"));
        assertTrue(PAGE.startsWith("<html>") && PAGE.endsWith("</html>"));
    }

    /** Each entry must name a journal or a book, so the list stays usable as a bibliography. */
    @Test
    public void everyReferenceIsCitable() {
        String refs = PAGE.substring(PAGE.indexOf("<ol"));
        int entries = count(refs, "<li");
        assertTrue("the reference list should hold every paper used, found " + entries, entries >= 18);
        for (String part : refs.split("<li")) {
            if (!part.contains("</li>")) continue;
            String entry = part.substring(0, part.indexOf("</li>"));
            assertTrue("a reference needs a year: " + entry, entry.matches(".*(19|20)\\d{2}.*"));
            assertTrue("a reference needs a journal or publisher in italics, or a DOI: " + entry,
                    entry.contains("<i>") || entry.contains("doi:"));
        }
    }

    private static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }
}
