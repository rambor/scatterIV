package version4.FormFactors;

/**
 * Guards against the commonest way an LNP analysis goes silently wrong: scattering data written in nm⁻¹ and read
 * as Å⁻¹, or the reverse. Since 1 nm⁻¹ = 0.1 Å⁻¹, the mistake makes every distance wrong by a factor of ten
 * without producing an error, and the distances that come out still look like distances — a 5.58 nm lamellar
 * repeat becomes a 0.56 nm one, which reads as a plausible chain spacing.
 *
 * <p>It is worse than a rescaling here, because almost everything the panel concludes is decided by absolute
 * windows in Å⁻¹: the nucleic-acid peak between 0.075 and 0.18, cholesterol monohydrate at 0.186 and 0.371, lipid
 * lengths of 17–18 Å, the minimal-surface constants. Data shifted by ten lands in different windows, so a
 * different branch of the reasoning runs and the panel tells a different story rather than the same story at the
 * wrong scale.
 *
 * <p>Scatter converts at load, through the "convert nm to Å" box on the main window. This class does not convert
 * anything; it reads the q range and says when that box looks to have been missed, so the user can decide. Where
 * the file itself declares its units — Scatter writes them into the JSON footer of a .dat — that declaration is
 * better than any guess and should be preferred.
 */
final class LnpUnits {

    enum Verdict {
        /** the q range is that of a SAXS curve in Å⁻¹ */
        ANGSTROM,
        /** the q range is ten times too wide for Å⁻¹ and fits nm⁻¹ */
        NANOMETRE,
        /** could be either: too narrow a range, or a genuine wide-angle measurement */
        UNCLEAR
    }

    /**
     * Above this, in Å⁻¹, a curve has left the small-angle range: a SAXS measurement of lipid particles seldom
     * passes 1.5 Å⁻¹, where the lipid chain–chain halo sits, and the models in this panel do not describe that
     * region anyway.
     */
    private static final double SAXS_LIMIT = 1.6;

    /** Below this the curve is comfortably within the small-angle range and nothing needs saying. */
    private static final double CLEARLY_SAXS = 0.8;

    final Verdict verdict;
    final double qmin, qmax;
    private final String reason;

    private LnpUnits(Verdict v, double qmin, double qmax, String reason) {
        this.verdict = v;
        this.qmin = qmin;
        this.qmax = qmax;
        this.reason = reason;
    }

    static LnpUnits inspect(double[] q) {
        if (q == null || q.length == 0) return new LnpUnits(Verdict.UNCLEAR, 0, 0, "no data");
        double lo = Double.POSITIVE_INFINITY, hi = 0;
        for (double v : q) {
            if (!(v > 0)) continue;
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (!(hi > 0)) return new LnpUnits(Verdict.UNCLEAR, 0, 0, "no positive q values");

        if (hi > SAXS_LIMIT)
            return new LnpUnits(Verdict.NANOMETRE, lo, hi, String.format(
                    "q reaches %.2f, past the %.1f Å⁻¹ where small-angle scattering ends; as nm⁻¹ it would be "
                            + "%.3f–%.3f Å⁻¹, an ordinary SAXS range", hi, SAXS_LIMIT, lo / 10, hi / 10));
        if (hi < CLEARLY_SAXS)
            return new LnpUnits(Verdict.ANGSTROM, lo, hi, String.format(
                    "q runs %.4f–%.3f, a normal SAXS range in Å⁻¹", lo, hi));
        return new LnpUnits(Verdict.UNCLEAR, lo, hi, String.format(
                "q reaches %.2f, which is high for SAXS in Å⁻¹ but not high enough to be sure; it would be "
                        + "%.3f Å⁻¹ if the file is in nm⁻¹", hi, hi / 10));
    }

    /** true when the user should be asked to look before trusting anything the panel says */
    boolean worthWarning() { return verdict != Verdict.ANGSTROM; }

    /** the largest spacing the data can resolve, as a sanity check a reader can apply themselves */
    double largestSpacing() { return qmin > 0 ? 2 * Math.PI / qmin : Double.NaN; }

    double smallestSpacing() { return qmax > 0 ? 2 * Math.PI / qmax : Double.NaN; }

    String message() {
        switch (verdict) {
            case NANOMETRE:
                return "This dataset looks like it is in nm⁻¹, not Å⁻¹: " + reason + ". Read as Å⁻¹ it says the "
                        + String.format("sample has nothing larger than %.2f nm in it, ", smallestSpacing() / 10)
                        + "which no lipid particle satisfies. Load it again with \"convert nm to Å\" ticked on the "
                        + "main window, or every distance here will be ten times too small.";
            case UNCLEAR:
                return "The units of this dataset cannot be told from its q range: " + reason + ". If it was "
                        + "recorded in nm⁻¹, load it again with \"convert nm to Å\" ticked; everything below "
                        + "assumes Å⁻¹.";
            default:
                return "";
        }
    }

    /** one line for the report, so a saved result records what was assumed */
    String note() {
        return String.format("q = %.4f–%.3f Å⁻¹, resolving %.1f–%.0f Å%s", qmin, qmax,
                smallestSpacing(), largestSpacing(),
                verdict == Verdict.ANGSTROM ? "" : "   ** check the units, see the Assessment tab **");
    }
}
