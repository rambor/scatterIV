package version4.FormFactors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The methods page: every relation the LNP panel applies, written out with the source it comes from, so that a
 * number taken off a fit can be traced back to the equation that produced it and the paper that proposed it.
 *
 * <p>This is reference text rather than a calculation. Where a constant appears in the code it is repeated here
 * with the same value, so a disagreement between the two is a bug worth reporting.
 */
final class LnpFormulas {

    private LnpFormulas() { }

    /** monospaced-formula HTML, laid out in one scrollable page */
    static JComponent panel() {
        JEditorPane pane = new JEditorPane("text/html", page());
        pane.setEditable(false);
        pane.setBorder(new EmptyBorder(10, 14, 14, 14));
        pane.setBackground(Color.WHITE);
        pane.setCaretPosition(0);
        // track the viewport width so long lines re-wrap instead of scrolling sideways
        JScrollPane sc = new JScrollPane(pane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        return sc;
    }

    private static String h(String title) {
        return "<h3 style='margin:16px 0 4px 0;color:#24292f'>" + title + "</h3>";
    }

    private static String eq(String formula) {
        return "<div style='font-family:monospace;font-size:12px;background:#f6f8fa;padding:6px 10px;"
                + "margin:4px 0;border-left:3px solid #d0d7de'>" + formula + "</div>";
    }

    private static String note(String text) {
        return "<div style='margin:2px 0 6px 0;color:#57606a'>" + text + "</div>";
    }

    private static String ref(String text) {
        return "<div style='margin:2px 0 8px 0;color:#0969da'>" + text + "</div>";
    }

    static String page() {
        // No width here: a JEditorPane showing HTML wraps to the viewport it is given, and a fixed width
        // would instead force a horizontal scrollbar on any window narrower than the number written.
        StringBuilder b = new StringBuilder("<html><body style='font-family:sans-serif;font-size:12px'>");
        b.append("<h2 style='margin:0 0 2px 0'>Methods and formulas</h2>");
        b.append(note("Every relation this panel applies, with its source. Lengths are in Å and q in Å⁻¹ throughout; "
                + "data recorded in nm⁻¹ must be converted before loading, since every constant below assumes Å."));

        b.append(h("1. From a peak position to a distance"));
        b.append(note("The starting point for everything below. A peak in a scattering curve means that something "
                + "in the sample repeats at a fixed distance, and the position of the peak gives that distance."));
        b.append(eq("d = 2π / q"));
        b.append(note("This is Bragg's law written in the variable a scattering experiment actually measures. The "
                + "familiar form is λ = 2d sin θ; since q = 4π sin θ / λ, the two say the same thing, and using q "
                + "removes the wavelength from the arithmetic."));
        b.append(note("<b>Worked example.</b> The strongest peak of the example dataset sits at q = 0.1126 Å⁻¹:"));
        b.append(eq("d = 2π / 0.1126 Å⁻¹ = 55.8 Å = 5.58 nm"));
        b.append(note("So that sample contains something repeating every 5.58 nm. For a stack of bilayers it is the "
                + "repeat distance of the stack: one bilayer plus the water next to it, which is the distance from "
                + "one bilayer to the next."));
        b.append(note("<b>Why there is more than one peak.</b> A repeating stack does not give one peak but a series, "
                + "at whole multiples of the first:"));
        b.append(eq("q<sub>h</sub> = h · q<sub>1</sub> = 2πh / d&nbsp;&nbsp;&nbsp;h = 1, 2, 3, …"));
        b.append(note("The first of these, the (001), spans the whole repeat, so it alone gives d; the others confirm "
                + "it. Peaks in the ratio 1 : 2 : 3 are the signature of a lamellar stack, and a series in other "
                + "ratios points to one of the other phases in section 5. Two peaks that are <i>not</i> multiples of "
                + "one another cannot come from a single repeat, which is how coexisting phases are recognised."));
        b.append(note("<b>Units.</b> Everything in this panel is in ångströms, so q is in Å⁻¹ and d comes out in Å; "
                + "divide by 10 for nanometres. Data recorded in nm⁻¹ has to be converted before loading, or every "
                + "distance will be wrong by a factor of ten."));
        b.append(note("The intensities of these orders carry more than their positions do: see section 7."));

        b.append(h("2. Model-free assessment"));
        b.append(eq("I(q) = I(0) exp(−q²R<sub>g</sub>²/3)&nbsp;&nbsp;&nbsp;valid for qR<sub>g</sub> ≲ 1.3"));
        b.append(note("The Guinier law gives the radius of gyration without assuming a shape. For a uniform sphere "
                + "the outer radius follows as R = R<sub>g</sub>√(5/3); for a particle with shells of differing "
                + "density that conversion is only indicative, which is why the panel also fits a model."));
        b.append(eq("d = 2π/q&nbsp;&nbsp;&nbsp;&nbsp;number of correlated repeats ≈ q/FWHM"));
        b.append(note("Any maximum in the curve is reported as a real-space spacing. The ratio of position to width "
                + "says over how many repeats that spacing persists, which separates a sharp reflection from a broad "
                + "correlation peak."));

        b.append(h("3. Particle form factor"));
        b.append(eq("I(q) = scale · ∫ N(R) |F(q,R)|² dR + background"));
        b.append(eq("F(q,R) = Σ<sub>k</sub> (ρ<sub>k</sub> − ρ<sub>k+1</sub>) V<sub>k</sub> · 3[sin(qR<sub>k</sub>) "
                + "− qR<sub>k</sub>cos(qR<sub>k</sub>)]/(qR<sub>k</sub>)³"));
        b.append(note("A core with up to four concentric shells, each a uniform electron density in e/Å³, averaged "
                + "over a Gaussian distribution of core radii N(R) of width σ. The scattering depends on differences "
                + "between neighbouring densities, so a layer whose density equals the buffer contributes nothing and "
                + "the panel reports the visible radius separately from the modelled one."));
        b.append(ref("Core–multishell treatment as applied to mRNA-LNPs: Li et al., Membranes 15, 153 (2025); "
                + "Yanez Arteta et al., PNAS 115, E3351 (2018)."));

        b.append(h("4. Internal nucleic-acid peak (mesophase peak modelling)"));
        b.append(note("Listed in the fit and on the chart as the <b>mesophase peak</b>. It is the one term in the "
                + "model that describes the <i>inside</i> of the particle rather than its shape: everything else "
                + "builds a core with shells around it, and this says that within that core something repeats."));
        b.append(eq("I(q) = scale · ⟨A²⟩/S₀²&nbsp;&nbsp;+&nbsp;&nbsp;A exp[−(q − q₀)²/2w²]&nbsp;&nbsp;+&nbsp;&nbsp;background"));
        b.append(note("Three fitted parameters: the height A, the position q₀ and the width w. The position is what "
                + "matters, through section 1:"));
        b.append(eq("d = 2π/q₀"));
        b.append(note("<b>Why 'mesophase'.</b> Lipid and water together form liquid-crystalline phases — lamellar, "
                + "hexagonal, cubic — which are mesophases: ordered like a crystal in some directions, fluid like a "
                + "liquid in others. The core of a loaded particle is one of these, built from nucleic acid and "
                + "ionizable lipid, and the peak is its first reflection. Cargo and lipid are held apart at a fixed "
                + "distance, and that distance is d."));
        b.append(note("<b>Why it is broad.</b> A crystal gives sharp reflections because the repeat holds over many "
                + "thousands of unit cells. Here it holds over a few neighbours, so the reflection is broad and "
                + "symmetric, and its width measures how far the order persists — section 8 turns w into a domain "
                + "size. A peak this broad is better described by a Gaussian than by a lattice."));
        b.append(note("<b>It is added, not multiplied.</b> Strictly, internal order enters as a structure factor "
                + "S(q) multiplying the form factor. Here the peak is added to it. For a single broad reflection on "
                + "a steeply falling background the two are close, and adding keeps the peak's parameters "
                + "independent of the particle's. But it means the fitted area is not a rigorous measure of how much "
                + "of the sample is ordered, and should not be quoted as one."));
        b.append(note("<b>It will fit something that is not there.</b> A bilayer shell produces broad maxima of its "
                + "own, from its thickness rather than from any internal repeat. A Gaussian placed on one of those "
                + "fits well and reports a spacing the sample does not have. Two things separate the cases: the peak "
                + "should disappear from a cargo-free preparation of the same formulation, and a vesicle model should "
                + "reproduce the maxima with no peak term at all. The Cargo tab tests this directly, and the fit "
                + "report says so whenever the assessment has found form-factor lobes."));
        b.append(note("<b>What is seen in practice.</b> mRNA and siRNA packed with ionizable lipid give one broad "
                + "maximum near d ≈ 5 nm, absent when the cargo is absent. It is the principal evidence that the "
                + "cargo is inside the particle and not merely alongside it."));
        b.append(ref("Li et al. (2025) report d ≈ 4.9 nm; SANS contrast variation assigns it to a disordered inverse "
                + "hexagonal core with water/mRNA cylinders ≈ 6.2 nm apart: Yanez Arteta et al., PNAS (2018)."));

        b.append(h("5. Bragg indexing"));
        b.append(eq("cubic&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;q<sub>hkl</sub> = (2π/a)·√(h² + k² + l²)<br>"
                + "hexagonal&nbsp;&nbsp;q<sub>hk</sub>&nbsp; = (4π/√3a)·√(h² + hk + k²)<br>"
                + "lamellar&nbsp;&nbsp;&nbsp;q<sub>l</sub>&nbsp;&nbsp;&nbsp; = (2π/d)·l"));
        b.append(note("Allowed reflections per space group, in the order they appear: "
                + "Lα 1:2:3 · HII √1:√3:√4:√7 · Pn3m √2:√3:√4:√6:√8:√9 · Im3m √2:√4:√6:√8:√10 · "
                + "Ia3d √6:√8:√14:√16:√20 · Fd3m √3:√8:√11:√12 · Pm3n √2:√4:√5:√6 · Fm3m √3:√4:√8:√11."));
        b.append(ref("Reflection conditions: Marsh, Handbook of Lipid Bilayers, 2nd ed., ch. 14 (CRC Press, 2013). "
                + "Lattice-parameter relation as eq. S1 of Mendozza et al., Nanoscale 10 (2018)."));

        b.append(h("6. Indexing check (linearity)"));
        b.append(eq("√(h²+k²+l²) = (a/2π) · q<sub>hkl</sub>&nbsp;&nbsp;&nbsp;a straight line through the origin"));
        b.append(note("Plotting the index multiplier of each reflection against its measured position tests an "
                + "assignment more strictly than the eye: a wrong space group can place several reflections near real "
                + "peaks, but rarely while keeping them collinear through the origin. Linearity cannot separate two "
                + "groups that allow the same reflections — every Im3m reflection is allowed in Pn3m too — so the "
                + "panel also lists the reflections a phase predicts that the data does not show."));
        b.append(ref("Inset of Fig. 2C of Caselli, Conti, De Santis & Berti, Adv. Colloid Interface Sci. 327, "
                + "103156 (2024)."));

        b.append(h("7. What the order intensities say about the bilayer"));
        b.append(note("The positions of the orders give the repeat distance and nothing more. Their relative "
                + "<i>intensities</i> are the bilayer's own form factor sampled at each order, so they measure the "
                + "bilayer itself — which matters, because the water layer is otherwise found by subtracting a lipid "
                + "length the user has typed in rather than measured."));
        b.append(note("For a powder of stacks the measured intensity carries a Lorentz factor, so the amplitude is "
                + "recovered as"));
        b.append(eq("I<sub>h</sub> ∝ |F(q<sub>h</sub>)|² / q<sub>h</sub>²&nbsp;&nbsp;&nbsp;⇒&nbsp;&nbsp;"
                + "|F(q<sub>h</sub>)| = q<sub>h</sub> √I<sub>h</sub>"));
        b.append(note("A symmetric bilayer, treated as two headgroup layers a distance d<sub>HH</sub> apart each of "
                + "Gaussian width σ, has"));
        b.append(eq("F(q) = 2 cos(q·d<sub>HH</sub>/2) · exp(−q²σ²/2)"));
        b.append(note("The cosine puts zeros in the series and the Gaussian damps the higher orders. <b>A weak order "
                + "usually means q<sub>h</sub> has landed near a zero of F, not that the measurement is poor.</b> "
                + "Fitting the observed ratios gives d<sub>HH</sub>, and the water between bilayers follows as "
                + "d − d<sub>HH</sub>."));
        b.append(note("<b>The sign of F is not measured, and that limits what can be concluded.</b> Replacing "
                + "d<sub>HH</sub> by d − d<sub>HH</sub> leaves |F| unchanged at every order, so a bilayer and the "
                + "water gap that completes the repeat fit the intensities equally well. The panel reports every "
                + "thickness that fits and marks the one nearest the expected bilayer; settling it properly needs a "
                + "swelling series, SANS contrast variation, or a composition you trust. Two orders cannot separate "
                + "d<sub>HH</sub> from σ at all, so a fit is offered only from three."));
        b.append(ref("Lorentz factor and bilayer form factors for lamellar phases: Marsh, Handbook of Lipid Bilayers, "
                + "2nd ed., ch. 14 (2013). The phase problem and its resolution by swelling: Caselli et al., "
                + "Adv. Colloid Interface Sci. 327, 103156 (2024)."));

        b.append(h("8. Ordered domain size"));
        b.append(eq("FWHM = 2πK/D&nbsp;&nbsp;&nbsp;K = 0.9&nbsp;&nbsp;&nbsp;⇒&nbsp;&nbsp;D = 2πK/FWHM"));
        b.append(note("The Scherrer relation, giving the coherence length over which the lattice is ordered. Any "
                + "instrumental broadening entered by the user is subtracted in quadrature first; without it the "
                + "result is a lower limit on the domain."));

        b.append(h("9. Water-channel geometry"));
        b.append(eq("bicontinuous cubic&nbsp;&nbsp;r<sub>w</sub> = √(−A₀/2πχ)·a − l<sub>c</sub>"));
        b.append(eq("inverse hexagonal&nbsp;&nbsp;&nbsp;r<sub>w</sub> = (0.5256·a − l<sub>c</sub>)/0.994"));
        b.append(eq("lamellar&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + "d<sub>w</sub> = d − 2l<sub>c</sub>"));
        b.append(note("l<sub>c</sub> is the lipid monolayer length, the one quantity here that the scattering does "
                + "not supply: it comes from the composition. Monoolein is taken as 17 Å over 25–50 °C by Mendozza "
                + "et al. and 18.1 Å by Jin et al.; an error in it shifts the channel radius and the water fraction "
                + "together, which is why the panel prints the value it used."));
        b.append(note("The hexagonal relation is <i>not</i> a/2 − l<sub>c</sub>: the cylinders sit on a hexagonal "
                + "lattice, and the difference is about 17 % at a = 50 Å."));
        b.append(ref("Cubic: eq. S2 of Mendozza et al., Nanoscale 10 (2018), with the minimal-surface constants of "
                + "Marsh ch. 14. Hexagonal: Reese, Strango, Dell, Tristram-Nagle & Harper, Phys. Chem. Chem. Phys. "
                + "17, 9194 (2015), used as eq. S4 of Mendozza et al."));

        b.append(h("10. Water volume fraction"));
        b.append(eq("bicontinuous cubic&nbsp;&nbsp;φ<sub>w</sub> = 1 − 2A₀(l<sub>c</sub>/a) "
                + "− (4/3)πχ(l<sub>c</sub>/a)³"));
        b.append(eq("inverse hexagonal&nbsp;&nbsp;&nbsp;φ<sub>w</sub> = 2πr<sub>w</sub>²/(√3·a²)"));
        b.append(eq("lamellar&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
                + "φ<sub>w</sub> = 1 − 2l<sub>c</sub>/d"));
        b.append(note("How much of the phase is channel rather than lipid, which is what a loading capacity is "
                + "worked out from. Reported as not available where the geometry cannot be satisfied — a lattice too "
                + "small for the bilayer — and for Fd3m, whose micelles are discrete and have no channel."));
        b.append(ref("Eqs. S3 and S5 of Mendozza et al., Nanoscale 10 (2018), after Turner & Gruner."));

        b.append(h("11. Minimal-surface constants"));
        b.append("<table style='font-family:monospace;font-size:12px;margin:4px 0;border-collapse:collapse'>"
                + "<tr style='background:#f6f8fa'><td style='padding:3px 14px'><b>phase</b></td>"
                + "<td style='padding:3px 14px'><b>surface</b></td><td style='padding:3px 14px'><b>A₀</b></td>"
                + "<td style='padding:3px 14px'><b>χ</b></td><td style='padding:3px 14px'><b>Bonnet</b></td></tr>"
                + "<tr><td style='padding:3px 14px'>Pn3m</td><td style='padding:3px 14px'>D</td>"
                + "<td style='padding:3px 14px'>1.9189</td><td style='padding:3px 14px'>−2</td>"
                + "<td style='padding:3px 14px'>1.000</td></tr>"
                + "<tr><td style='padding:3px 14px'>Im3m</td><td style='padding:3px 14px'>P</td>"
                + "<td style='padding:3px 14px'>2.3451</td><td style='padding:3px 14px'>−4</td>"
                + "<td style='padding:3px 14px'>1.279</td></tr>"
                + "<tr><td style='padding:3px 14px'>Ia3d</td><td style='padding:3px 14px'>G (gyroid)</td>"
                + "<td style='padding:3px 14px'>3.0915</td><td style='padding:3px 14px'>−8</td>"
                + "<td style='padding:3px 14px'>1.576</td></tr></table>");
        b.append(note("A₀ is the dimensionless area of the minimal surface in one unit cell and χ its Euler "
                + "characteristic. Two cubic phases built on the same bilayer have lattice parameters in the Bonnet "
                + "ratios of the last column, so a coexisting pair that departs from 1 : 1.279 : 1.576 is not simply "
                + "one bilayer adopting two symmetries."));
        b.append(ref("Marsh, Handbook of Lipid Bilayers, 2nd ed., ch. 14 (2013)."));

        b.append(h("12. Sponge phase (L3)"));
        b.append(eq("I(q) = 1/(a₂ + c₁q² + c₂q⁴) + background"));
        b.append(eq("d&nbsp; = 2π[½√(a₂/c₂) − ¼(c₁/c₂)]<sup>−½</sup>&nbsp;&nbsp;&nbsp;&nbsp;"
                + "ξ = [½√(a₂/c₂) + ¼(c₁/c₂)]<sup>−½</sup>"));
        b.append(eq("amphiphilicity&nbsp;&nbsp;f = c₁/√(4a₂c₂)"));
        b.append(note("A sponge is bicontinuous like a cubic phase but without long-range order, so it shows one "
                + "broad correlation peak and no Bragg reflections and there is nothing to index. The Teubner–Strey "
                + "form gives the quasi-periodic repeat d and the correlation length ξ over which it survives. "
                + "f below 0 means a structured phase; above 0 the scattering is that of a disordered mixture."));
        b.append(ref("Teubner & Strey, J. Chem. Phys. 87, 3195 (1987). The sponge phase as one of the common LNP "
                + "structures: Fig. 1G of Caselli et al. (2024)."));

        b.append(h("13. Phase decomposition of the core peak"));
        b.append(eq("I(q) = Σ<sub>i</sub> A<sub>i</sub>·w<sub>i</sub>²/[(q − q<sub>i</sub>)² + w<sub>i</sub>²] "
                + "+ Bq<sup>−p</sup> + C"));
        b.append(note("The maximum near 1 nm⁻¹ is split into disordered, inverse-hexagonal and lamellar "
                + "contributions with bounded centres, fitted together with a power-law background. The ratio of the "
                + "disordered to the hexagonal area tracks delivery efficacy across a formulation series."));
        b.append(ref("Hammel et al., ACS Nano 17, 11454 (2023)."));

        b.append(h("14. Cargo and charge balance"));
        b.append(eq("N/P = moles of ionizable amine / moles of nucleic-acid phosphate"));
        b.append(eq("copies per particle ≈ V<sub>particle</sub> / (V<sub>lipid</sub> + "
                + "x·V<sub>nucleotide</sub>/(N/P))&nbsp;&nbsp;&nbsp;V<sub>nucleotide</sub> = 320 Å³"));
        b.append(note("One phosphate per nucleotide, and two per base pair for a duplex such as siRNA. The estimate "
                + "pairs every amine with a phosphate at the stated N/P and fills the fitted particle volume with "
                + "lipid and cargo in that proportion, so it is an upper bound on what a particle of the measured "
                + "size could hold rather than a measurement of what it does hold."));
        b.append(ref("Typical formulations: N/P ≈ 6 for mRNA-LNPs, ≈ 3 for siRNA. Li et al. (2025) examine N/P 3–8 "
                + "in Comirnaty-like particles."));

        b.append(h("15. Reported ranges for comparison"));
        b.append(note("Cubosome water channels run from about 4.0 nm (monoolein, Pn3m) to 38.1 nm "
                + "(monoolein:DOTAP:PEG-monoolein 95:4:1, Im3m, a = 68.4 nm). A fitted channel outside that range is "
                + "worth re-checking, usually against the lipid length used."));
        b.append(ref("Barriga, Holme & Stevens, Angew. Chem. Int. Ed. 58, 2958 (2019)."));

        b.append(h("References"));
        b.append("<ol style='margin:4px 0 0 18px;padding:0'>");
        for (String r : new String[]{
                "Barriga, H. M. G., Holme, M. N. & Stevens, M. M. Cubosomes: the next generation of smart lipid nanoparticles? <i>Angew. Chem. Int. Ed.</i> <b>58</b>, 2958 (2019).",
                "Caselli, L., Conti, L., De Santis, I. & Berti, D. Small-angle X-ray and neutron scattering applied to lipid-based nanoparticles: recent advancements across different length scales. <i>Adv. Colloid Interface Sci.</i> <b>327</b>, 103156 (2024).",
                "Caselli, L. <i>et al.</i> Lipid cubic mesophases combined with superparamagnetic iron oxide nanoparticles: a hybrid multifunctional platform with tunable magnetic properties. <i>Int. J. Mol. Sci.</i> <b>22</b>, 9268 (2021).",
                "Gilbert, J., Sebastiani, F., Yanez Arteta, M., Terry, A., Fornell, A., Russell, R., Mahmoudi, N. &amp; Nylander, T. Evolution of the structure of lipid nanoparticles for nucleic acid delivery: from in situ studies of formulation to colloidal stability. <i>J. Colloid Interface Sci.</i> <b>660</b>, 66 (2024).",
                "Hammel, M. <i>et al.</i> Correlating the structure and gene silencing activity of oligonucleotide-loaded lipid nanoparticles using small-angle X-ray scattering. <i>ACS Nano</i> <b>17</b>, 11454 (2023).",
                "Jin, H. <i>et al.</i> Premixing enables loading of long RNA in cubic phase lipid nanoparticles. <i>Nat. Commun.</i> (2025). doi:10.1038/s41467-025-60380-6",
                "Li, H., Song, P., Li, Y., Tu, S., Mehmood, M., Chen, L., Li, N. &amp; Tian, Q. Mesoscopic structure of lipid nanoparticles studied by small-angle X-ray scattering: a spherical core&ndash;triple shell model analysis. <i>Membranes</i> <b>15</b>, 153 (2025).",
                "Malheiros, B., de Castro, R. D., Lotierzo, M. C., Casadei, B. R., Mariani, P. &amp; Barbosa, L. R. S. Influence of hexadecylphosphocholine (miltefosine) in phytantriol-based cubosomes: a structural investigation. <i>Colloids Surf. A</i> <b>632</b>, 127720 (2022).",
                "Marsh, D. <i>Handbook of Lipid Bilayers</i>, 2nd ed., ch. 14 (CRC Press, 2013).",
                "Mendozza, M., Montis, C., Caselli, L., Wolf, M., Baglioni, P. &amp; Berti, D. On the thermotropic and magnetotropic phase behaviour of lipid liquid crystals containing magnetic nanoparticles. <i>Nanoscale</i> <b>10</b>, 3480 (2018).",
                "Pattipeiluhu, R., Zeng, Y., Hendrix, M. M. R. M., Voets, I. K., Kros, A. &amp; Sharp, T. H. Liquid crystalline inverted lipid phases encapsulating siRNA enhance lipid nanoparticle mediated transfection. <i>Nat. Commun.</i> <b>15</b>, 1303 (2024).",
                "Reese, C. W., Strango, Z. I., Dell, Z. R., Tristram-Nagle, S. &amp; Harper, P. E. <i>Phys. Chem. Chem. Phys.</i> <b>17</b>, 9194 (2015).",
                "Spinozzi, F., Moretti, P., Perinelli, D. R., Corucci, G., Piergiovanni, P., Amenitsch, H., Sancini, G. A., Franzese, G. &amp; Blasi, P. Small-angle X-ray scattering unveils the internal structure of lipid nanoparticles. <i>J. Colloid Interface Sci.</i> <b>662</b>, 446 (2024).",
                "Teubner, M. &amp; Strey, R. Origin of the scattering peak in microemulsions. <i>J. Chem. Phys.</i> <b>87</b>, 3195 (1987).",
                "Thi, T. T. H., Suys, E. J. A., Lee, J. S., Nguyen, D. H., Park, K. D. &amp; Truong, N. P. Lipid-based nanoparticles in the clinic and clinical trials: from cancer nanomedicine to COVID-19 vaccines. <i>Vaccines</i> <b>9</b>, 359 (2021).",
                "Trollmann, M. F. W. &amp; B&ouml;ckmann, R. A. mRNA lipid nanoparticle phase transition. <i>Biophys. J.</i> (2022).",
                "Yanez Arteta, M. <i>et al.</i> Successful reprogramming of cellular protein production through mRNA delivered by functionalized lipid nanoparticles. <i>PNAS</i> <b>115</b>, E3351 (2018).",
                "Zheng, L., Bandara, S. R., Tan, Z. &amp; Leal, C. Lipid nanoparticle topology regulates endosomal escape and delivery of RNA to the cytoplasm. <i>PNAS</i> <b>120</b> (2023)."})
            b.append("<li style='margin-bottom:4px'>").append(r).append("</li>");
        b.append("</ol>");
        b.append("<div style='margin-top:14px;color:#57606a'>Citations are given so a result can be traced to its "
                + "source; check the original before quoting a constant in a publication.</div>");
        return b.append("</body></html>").toString();
    }
}
