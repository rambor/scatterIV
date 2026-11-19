package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;

/**
 * What the user expects of the sample (cargo, formulation, size) and a checklist that compares the SAXS result
 * against it: did particles form, is there an RNA-lipid internal structure, does the size match, and are there
 * formulation problems. Also estimates how many cargo molecules a particle of the fitted size can hold.
 *
 * Evidence used:
 * <ul>
 *   <li>a ~5-6.5 nm internal peak appears with nucleic acid and is absent or weak without it (Yanez Arteta et al.,
 *       PNAS 2018; Li et al., Membranes 2025; Pattipeiluhu et al., Nat. Commun. 2024), but DOPE-rich empty LNPs can
 *       form inverse hexagonal phases without RNA (Pattipeiluhu et al.), so a peak is supporting, not conclusive;</li>
 *   <li>~24 % water by volume in mRNA-LNPs (Yanez Arteta et al.), used for the copies-per-particle estimate;</li>
 *   <li>cholesterol monohydrate peaks at d = 3.38 / 1.69 nm (Yanez Arteta et al.).</li>
 * </ul>
 */
public final class LnpExpectations {

    public enum Cargo {
        NONE("No cargo (empty LNP)", 0, "nt"),
        SIRNA("siRNA (duplex)", 21, "bp"),
        MRNA("mRNA", 4284, "nt"),
        OTHER("Other nucleic acid", 1000, "nt");
        public final String label, unit;
        public final int defaultLength;
        Cargo(String l, int len, String u) { label = l; defaultLength = len; unit = u; }
        @Override public String toString() { return label; }

        /** nucleotides (phosphates) per cargo molecule */
        public double nucleotides(double length) { return this == SIRNA ? 2 * length : length; }
    }

    public Cargo cargo = Cargo.NONE;
    /** bp for siRNA, nt otherwise */
    public double length = 4284;
    /** N/P: ionizable amines per nucleic-acid phosphate */
    public double np = 6;
    /** ionizable lipid, mol % of total lipid */
    public double ionizableMolPercent = 50;
    /** expected diameter range (nm), e.g. from DLS; NaN = not given */
    public double expectedDiameterMin = Double.NaN, expectedDiameterMax = Double.NaN;
    /** mean molecular volume of the lipids (A^3; ionizable lipid / DSPC ~1300-1600, cholesterol ~620) */
    public double lipidVolume = 1100;
    /** volume fraction of water in the particle (Yanez Arteta et al. 2018: 24 %) */
    public double waterFraction = 0.24;
    /** internal phase expected from the lipid composition (LnpComposition.expectedPhase), or null */
    public String expectedPhase;
    /** partial volume of one nucleotide (A^3; 330 Da at 1.7 g/cm^3) */
    public static final double NUCLEOTIDE_VOLUME = 320;

    // ------------------------------------------------------------------ copies per particle

    /**
     * Cargo molecules held by a particle whose lipid-containing radius is r (A): the volume not taken by water is
     * shared by lipids and nucleotides, with one phosphate per (ionizable lipid / N/P).
     */
    public double copies(double r) {
        if (cargo == Cargo.NONE || !(np > 0) || !(length > 0)) return 0;
        double v = 4.0 / 3.0 * Math.PI * r * r * r * (1 - waterFraction);
        double x = ionizableMolPercent / 100.0;
        double nLipid = v / (lipidVolume + x * NUCLEOTIDE_VOLUME / np);
        double nucleotides = x * nLipid / np;
        return nucleotides / cargo.nucleotides(length);
    }

    /** {mean copies per particle, fraction of particles (number) holding less than one full copy} */
    public double[] copiesOverDistribution(LnpModel m, double[] p) {
        double shell = 0;
        for (int k = 1; k <= Math.min(m.shells - (m.shells >= 3 ? 1 : 0), 3); k++) shell += p[LnpModel.tIndex(k)];   // lipid layers, not PEG
        double r0 = p[LnpModel.R0], s = Math.max(p[LnpModel.SIGMA], 1e-3 * r0);
        double sum = 0, wsum = 0, below = 0;
        for (int i = 0; i <= 400; i++) {
            double rc = r0 - 4 * s + 8 * s * i / 400;
            if (rc <= 0) continue;
            double z = (rc - r0) / s, w = Math.exp(-0.5 * z * z);
            double c = copies(rc + shell);
            sum += w * c;
            wsum += w;
            if (c < 1) below += w;
        }
        return new double[]{sum / wsum, below / wsum};
    }

    // ------------------------------------------------------------------ checklist

    public enum Status { PASS, CAUTION, FAIL, INFO }

    public static final class Check {
        public final Status status;
        public final String title, detail;
        Check(Status s, String t, String d) { status = s; title = t; detail = d; }
    }

    /**
     * @param q, I, s   the data used for the assessment
     * @param a         model-free assessment (required)
     * @param fit       model fit (may be null)
     * @param ref       assessment of an empty / reference sample measured the same way (may be null)
     */
    public List<Check> evaluate(double[] q, double[] I, double[] s, LnpAssessment.Report a, LnpFitter.Result fit,
                                LnpAssessment.Report ref) {
        List<Check> out = new ArrayList<>();
        boolean expectsRna = cargo != Cargo.NONE;

        // ---- 1. particles formed?
        double slope = lowQSlope(q, I);
        double outerD = Double.NaN, pd = Double.NaN;
        double visibleD = Double.NaN;
        if (fit != null) {
            outerD = 2 * fit.model.outerRadius(fit.params) / 10;
            visibleD = 2 * fit.model.visibleOuterRadius(fit.params) / 10;
            pd = fit.params[LnpModel.SIGMA] / fit.params[LnpModel.R0];
        }
        LnpAssessment.Guinier g = a.guinier;
        if (g.found && g.rg < 40) {
            out.add(new Check(Status.FAIL, "Particles formed?",
                    String.format("The main scatterers are small (Rg = %.1f nm): micelles or unassembled lipid, not LNPs of tens of nm.", g.rg / 10)));
        } else if (fit != null && fit.chi2Reduced < 5 && outerD > 15) {
            out.add(new Check(Status.PASS, "Particles formed?",
                    String.format("Yes. The curve is described by spherical particles of outer diameter %.0f nm (σ/R₀ = %.2f)%s.",
                            outerD, pd, a.oscillations.isEmpty() && !hasOscillationText(a) ? "" : " with visible form-factor oscillations")));
        } else if (!g.found || g.rg > 100) {
            out.add(new Check(fit == null ? Status.CAUTION : Status.CAUTION, "Particles formed?",
                    fit == null ? "Large scatterers are present (no Guinier region at the lowest q). Fit the model to confirm their size."
                            : String.format("Large scatterers are present but the model fits poorly (reduced χ² %.1f): check for aggregates or a second population.", fit.chi2Reduced)));
        } else {
            out.add(new Check(Status.CAUTION, "Particles formed?", String.format("Particles with Rg = %.0f nm. Fit the model for the size distribution.", g.rg / 10)));
        }

        // ---- 2. aggregation
        if (slope < -3.3 && fit != null) {
            double ratio = I[0] / Math.max(fit.fit[0], 1e-30);
            if (ratio > 1.3)
                out.add(new Check(Status.CAUTION, "Aggregation",
                        String.format("Excess intensity at the lowest q (data %.1f× the particle model, slope q^%.1f): larger aggregates are present.", ratio, slope)));
        }

        // ---- 3. size vs expectation
        if (!Double.isNaN(outerD) && !Double.isNaN(expectedDiameterMin) && !Double.isNaN(expectedDiameterMax)) {
            boolean in = outerD >= 0.75 * expectedDiameterMin && outerD <= 1.1 * expectedDiameterMax;
            String extra = !Double.isNaN(visibleD) && visibleD < outerD - 0.5
                    ? String.format(" Only %.0f nm of that is visible to X-rays; the outer layer has no contrast, so its thickness is an assumption"
                    + " (DLS would still see it).", visibleD) : "";
            out.add(new Check(in ? Status.PASS : Status.CAUTION, "Size as expected?",
                    String.format("SAXS outer diameter %.0f nm vs expected %.0f–%.0f nm. DLS (hydrodynamic, intensity-weighted) usually reads 10–30 %% larger than SAXS.%s",
                            outerD, expectedDiameterMin, expectedDiameterMax, extra)));
        }
        if (!Double.isNaN(pd) && pd > 0.25)
            out.add(new Check(Status.CAUTION, "Size uniformity", String.format("Broad size distribution (σ/R₀ = %.2f). Li et al. found this at low N/P ratios.", pd)));

        // ---- 4. RNA-lipid internal structure
        LnpAssessment.Peak rnaPeak = rnaPeak(a);
        if (expectsRna) {
            if (rnaPeak != null) {
                String kind = a.mesophasePeak ? "broad (disordered inverse hexagonal / mRNA–lipid packing)" : "ordered (Bragg)";
                out.add(new Check(Status.PASS, cargo == Cargo.SIRNA ? "siRNA–lipid structure present?" : "RNA–lipid structure present?",
                        String.format("Yes, consistent with encapsulated nucleic acid: %s peak at d = %.1f nm, in the 4.5–7 nm range reported for "
                                + "RNA-loaded LNPs. A peak supports, but does not prove, encapsulation (DOPE-rich empty LNPs can show one).",
                                kind, rnaPeak.d() / 10)));
            } else {
                out.add(new Check(Status.CAUTION, "RNA–lipid structure present?",
                        "No internal peak at d ≈ 4.5–7 nm. In mRNA-LNPs this peak is normally present and is absent without RNA "
                                + "(Yanez Arteta et al. 2018). The RNA may be missing, at low loading, or not ordered. Confirm encapsulation "
                                + "with a RiboGreen-type assay."));
            }
        } else if (rnaPeak != null) {
            out.add(new Check(Status.CAUTION, "Unexpected internal structure",
                    String.format("A peak at d = %.1f nm is present although no cargo was specified. Possible causes: nucleic acid in the sample, "
                            + "or a lipid mesophase (DOPE-rich LNPs form inverse hexagonal phases without RNA, Pattipeiluhu et al. 2024).", rnaPeak.d() / 10)));
        } else {
            out.add(new Check(Status.PASS, "Empty LNP", "No internal RNA–lipid peak, as expected for particles without cargo."));
        }

        // ---- 4b. internal phase vs lipid composition
        if (expectedPhase != null) {
            String seen = a.coexistence != null ? "coexisting " + a.coexistence[0].symmetry + " + " + a.coexistence[1].symmetry
                    : !a.phases.isEmpty() ? a.phases.get(0).phase + " [" + a.phases.get(0).symmetry + "]"
                    : a.mesophasePeak ? "broad peak (disordered inverse hexagonal / RNA–lipid packing)"
                    : a.peaks.size() == 1 ? "one sharp peak (lamellar or HII)" : a.peaks.isEmpty() ? "no internal order" : "unindexed peaks";
            String exp = expectedPhase.toLowerCase(), obs = seen.toLowerCase();
            boolean match = (exp.contains("cubic") && obs.contains("cubic"))
                    || (exp.startsWith("inverse hexagonal") && (obs.contains("hexagonal") || obs.contains("hii")))
                    || (exp.startsWith("lamellar") && obs.contains("lamellar"))
                    || (exp.startsWith("mixed") && (obs.contains("lamellar") || obs.contains("hexagonal") || obs.contains("one sharp")))
                    || (exp.startsWith("amorphous") && (obs.contains("broad") || obs.contains("no internal")));
            out.add(new Check(match ? Status.PASS : Status.CAUTION, "Internal phase vs lipid composition",
                    "Composition suggests: " + expectedPhase + ". SAXS shows: " + seen + "."
                            + (match ? "" : " The mismatch may point to a different lipid distribution, incomplete mixing or cargo-induced restructuring.")));
        }

        // ---- 5. comparison with an empty reference
        if (ref != null && rnaPeak != null) {
            LnpAssessment.Peak near = null;
            for (LnpAssessment.Peak p : ref.peaks) if (Math.abs(p.q / rnaPeak.q - 1) < 0.15 && (near == null || p.area > near.area)) near = p;
            double refArea = near == null ? 0 : near.area;
            double ratio = refArea > 0 ? rnaPeak.area / refArea : Double.POSITIVE_INFINITY;
            Status st = ratio > 3 ? Status.PASS : ratio > 1.5 ? Status.CAUTION : Status.FAIL;
            out.add(new Check(st, "Compared with the empty reference",
                    near == null ? "The reference has no peak here, so the structure comes with the cargo: strong evidence that RNA is inside."
                            : String.format("Peak area is %.1f× the reference%s.", ratio,
                            ratio > 3 ? ": the structure is induced by the cargo" : ": the lipids alone already give much of this structure")));
        }

        // ---- 6. copies per particle
        if (expectsRna && fit != null) {
            double[] c = copiesOverDistribution(fit.model, fit.params);
            String unit = cargo == Cargo.SIRNA ? "siRNA duplexes" : cargo == Cargo.MRNA ? "mRNA molecules" : "molecules";
            String detail = String.format("A particle of the fitted size holds about %s %s of %.0f %s at N/P %.1f "
                            + "(assumes %.0f mol%% ionizable lipid, %.0f %% water, every amine paired with a phosphate).",
                    c[0] >= 10 ? String.format("%.0f", c[0]) : String.format("%.1f", c[0]), unit, length, cargo.unit, np,
                    ionizableMolPercent, 100 * waterFraction);
            if (cargo != Cargo.SIRNA && c[1] > 0.05)
                detail += String.format(" About %.0f %% of particles are too small to hold one full copy, so empty or partly loaded particles are likely.", 100 * c[1]);
            out.add(new Check(Status.INFO, "Expected cargo per particle", detail));
        }

        // ---- 7. formulation problems
        if (!a.cholesterol.isEmpty())
            out.add(new Check(Status.CAUTION, "Cholesterol crystals",
                    "Sharp peaks at d = 3.38 / 1.69 nm: cholesterol monohydrate. Part of the cholesterol is not in the particles."));
        if (fit != null && fit.chi2Reduced > 5)
            out.add(new Check(Status.CAUTION, "Model agreement",
                    String.format("Reduced χ² = %.1f: the core–shell model misses features of the curve; see the residuals.", fit.chi2Reduced)));
        return out;
    }

    private static boolean hasOscillationText(LnpAssessment.Report a) {
        return a.text != null && a.text.contains("Form-factor oscillations are visible");
    }

    /** the strongest peak with d between 4.5 and 7 nm (q 0.09-0.14), if any */
    static LnpAssessment.Peak rnaPeak(LnpAssessment.Report a) {
        LnpAssessment.Peak best = null;
        for (LnpAssessment.Peak p : a.peaks)
            if (p.q > 0.088 && p.q < 0.14 && (best == null || p.area > best.area)) best = p;
        return best;
    }

    /** log-log slope over the lowest ~12 % of the q range */
    static double lowQSlope(double[] q, double[] I) {
        int n = Math.max(6, q.length / 8);
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int m = 0;
        for (int j = 0; j < Math.min(n, q.length); j++) {
            if (!(I[j] > 0)) continue;
            double x = Math.log(q[j]), y = Math.log(I[j]);
            sx += x; sy += y; sxx += x * x; sxy += x * y; m++;
        }
        double det = m * sxx - sx * sx;
        return det == 0 ? 0 : (m * sxy - sx * sy) / det;
    }

    /** checklist as plain text for the report */
    public static String text(List<Check> checks) {
        StringBuilder sb = new StringBuilder("CHECKS AGAINST YOUR EXPECTATIONS\n");
        for (Check c : checks) {
            String tag = c.status == Status.PASS ? "[ OK ]" : c.status == Status.CAUTION ? "[ ?? ]" : c.status == Status.FAIL ? "[FAIL]" : "[info]";
            sb.append(tag).append(' ').append(c.title).append('\n');
            sb.append(wrap(c.detail, 96, "       ")).append('\n');
        }
        return sb.toString();
    }

    static String wrap(String s, int width, String indent) {
        StringBuilder out = new StringBuilder(indent);
        int col = 0;
        for (String w : s.split(" ")) {
            if (col + w.length() > width && col > 0) { out.append('\n').append(indent); col = 0; }
            out.append(w).append(' ');
            col += w.length() + 1;
        }
        return out.toString();
    }
}
