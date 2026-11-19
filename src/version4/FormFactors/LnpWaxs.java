package version4.FormFactors;

import java.util.ArrayList;
import java.util.List;

/**
 * The wide-angle companion to a SAXS measurement: the chain-packing region, roughly q = 1.0 to 1.8 Å⁻¹, where the
 * hydrocarbon chains of a bilayer scatter.
 *
 * <p>SAXS says how the bilayers are arranged — the repeat distance, the phase, the particle size. It says very
 * little about the state of the chains themselves, and the two are easy to confuse: a thick bilayer can mean
 * extended, ordered chains or simply a longer lipid. WAXS settles it, because ordered and disordered chains look
 * quite different at wide angle:
 *
 * <ul>
 *   <li><b>Fluid (L<sub>α</sub>)</b> — one broad halo near d = 4.5 Å, typically 0.2 to 0.4 Å⁻¹ wide. The chains
 *       are melted and only liquid-like in their packing.</li>
 *   <li><b>Gel (L<sub>β</sub>)</b> — a narrow reflection near d = 4.1 to 4.2 Å. The chains are extended and
 *       packed on a lattice, so the line is sharp.</li>
 *   <li><b>Tilted gel (L<sub>β'</sub>) or rippled (P<sub>β'</sub>)</b> — the same sharp line with a second, weaker
 *       one near 4.1 Å, the two together marking a distorted hexagonal packing.</li>
 *   <li><b>Liquid-ordered</b>, as produced by cholesterol above roughly 30 mol% — a broad halo like the fluid
 *       phase but shifted to about 4.6 Å. Sterols order the chains without crystallising them, so a sharp line is
 *       <i>not</i> expected.</li>
 * </ul>
 *
 * <p>This is a reading of the chain region, not a fit. It locates the packing peak, measures how wide it is, and
 * says which of the above the width and position are consistent with.
 */
final class LnpWaxs {

    /** the window where hydrocarbon chain packing appears */
    static final double Q_LOW = 0.9, Q_HIGH = 1.95;

    enum Packing { FLUID, GEL, ORDERED_SHARP, LIQUID_ORDERED, NONE }

    /** the spacings a reader compares a measured peak against: {d in Angstrom, label} */
    static final Object[][] REFERENCES = {
            {4.20, "gel, ordered chains"},
            {4.50, "fluid (Lα)"},
            {4.60, "liquid-ordered (sterol)"},
    };

    static final class Peak {
        final double q, d, fwhm, height, coherence;
        /** the local background the peak stands on, so it can be drawn where it was measured */
        final double baseline;
        Peak(double q, double d, double fwhm, double height, double baseline) {
            this.q = q;
            this.d = d;
            this.fwhm = fwhm;
            this.height = height;
            this.baseline = baseline;
            this.coherence = fwhm > 0 ? 2 * Math.PI * 0.9 / fwhm : Double.NaN;
        }

        /** the Gaussian this peak was measured as, for drawing over the data */
        double curveAt(double qq) {
            double sigma = fwhm / 2.355;
            return sigma > 0 ? baseline + height * Math.exp(-0.5 * Math.pow((qq - q) / sigma, 2)) : baseline;
        }
    }

    static final class Result {
        final List<Peak> peaks = new ArrayList<>();
        Packing packing = Packing.NONE;
        double qmin, qmax;
        /** highest q at which the data is still usable; beyond it the subtraction has gone negative */
        double usableTo;
        final List<String> notes = new ArrayList<>();

        Peak main() { return peaks.isEmpty() ? null : peaks.get(0); }
    }

    private LnpWaxs() { }

    static Result analyse(double[] q, double[] I, double[] s) {
        Result r = new Result();
        if (q == null || q.length < 20) {
            r.notes.add("Too few points to read a chain-packing peak.");
            return r;
        }
        r.qmin = q[0];
        r.qmax = q[q.length - 1];

        // where the subtraction stops being usable: a run of negative intensities means the background has been
        // over-subtracted, and nothing beyond that point should be read as a feature
        r.usableTo = r.qmax;
        int negatives = 0;
        for (int i = 0; i < q.length; i++) {
            if (I[i] <= 0) {
                if (++negatives >= 10) { r.usableTo = q[Math.max(0, i - 10)]; break; }
            } else negatives = 0;
        }
        if (r.usableTo < r.qmax)
            r.notes.add(String.format("Intensities go negative above q ≈ %.2f Å⁻¹, so the buffer has been "
                    + "over-subtracted there; only the region below that is read.", r.usableTo));

        if (r.qmin > Q_HIGH || Math.min(r.qmax, r.usableTo) < Q_LOW) {
            r.notes.add(String.format("This dataset covers q = %.2f–%.2f Å⁻¹ and does not reach the chain-packing "
                    + "region (%.1f–%.1f Å⁻¹).", r.qmin, r.qmax, Q_LOW, Q_HIGH));
            return r;
        }

        // smooth, then take maxima that stand clear of the local chord
        int n = q.length;
        double[] sm = new double[n];
        int w = Math.max(2, n / 150);
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int c = 0;
            for (int k = Math.max(0, i - w); k <= Math.min(n - 1, i + w); k++) { sum += I[k]; c++; }
            sm[i] = sum / c;
        }
        for (int i = w + 1; i < n - w - 1; i++) {
            if (q[i] < Q_LOW || q[i] > Math.min(Q_HIGH, r.usableTo)) continue;
            boolean top = true;
            int reach = Math.max(6, n / 60);
            for (int k = Math.max(0, i - reach); k <= Math.min(n - 1, i + reach); k++) if (sm[k] > sm[i]) top = false;
            if (!top) continue;
            double half = 0.5 * sm[i];
            int lo = i, hi = i;
            while (lo > 0 && sm[lo] > half) lo--;
            while (hi < n - 1 && sm[hi] > half) hi++;
            double fwhm = q[hi] - q[lo];
            // it has to rise above the chord a full width away, the same test the SAXS peaks face
            int li = Math.max(0, i - (hi - lo)), ri = Math.min(n - 1, i + (hi - lo));
            double chord = 0.5 * (sm[li] + sm[ri]);
            double err = s[i] / Math.sqrt(2 * w + 1.0);
            if (sm[i] - chord < 4 * err) continue;
            // The tallest smoothed point of a broad halo wanders with the noise by more than the difference
            // between a fluid and a liquid-ordered spacing, so the position comes from the centroid of the peak
            // above its own half height instead. For a narrow line the two agree.
            double num = 0, den = 0;
            for (int k = lo; k <= hi; k++) {
                double above = sm[k] - half;
                if (above <= 0) continue;
                num += q[k] * above;
                den += above;
            }
            double qc = den > 0 ? num / den : q[i];
            r.peaks.add(new Peak(qc, 2 * Math.PI / qc, fwhm, sm[i] - chord, chord));
        }
        r.peaks.sort((x, y) -> Double.compare(y.height, x.height));
        // two maxima closer than half a width are the same feature seen twice through the noise
        for (int i = 0; i < r.peaks.size(); i++)
            for (int j = r.peaks.size() - 1; j > i; j--)
                if (Math.abs(r.peaks.get(j).q - r.peaks.get(i).q) < 0.5 * r.peaks.get(i).fwhm) r.peaks.remove(j);
        // drop anything far broader than the strongest feature: those are shoulders of the background, not
        // packing lines, and listing them beside a reflection invites them to be read as one
        if (!r.peaks.isEmpty()) {
            double main = r.peaks.get(0).fwhm;
            r.peaks.removeIf(p -> p != r.peaks.get(0) && p.fwhm > Math.max(0.25, 3 * main));
        }
        while (r.peaks.size() > 3) r.peaks.remove(r.peaks.size() - 1);

        classify(r);
        return r;
    }

    private static void classify(Result r) {
        Peak m = r.main();
        if (m == null) {
            r.packing = Packing.NONE;
            r.notes.add("No chain-packing peak stands out. Either the chains are very disordered, the sample is "
                    + "dilute, or the wide-angle background has removed it.");
            return;
        }
        boolean sharp = m.fwhm < 0.15;
        // A distorted lattice shows a second *reflection*, which is sharp like the first. A broad shoulder on the
        // rising background sits at a similar distance but is several times wider, and calling that a second line
        // turns an ordinary gel into a tilted one.
        boolean second = false;
        for (int i = 1; i < r.peaks.size(); i++) {
            Peak p = r.peaks.get(i);
            double gap = Math.abs(p.q - m.q);
            if (gap > 0.03 && gap < 0.25 && p.fwhm < 2 * m.fwhm && p.fwhm < 0.2) second = true;
        }
        if (sharp && second) r.packing = Packing.ORDERED_SHARP;
        else if (sharp) r.packing = Packing.GEL;
        else if (m.d > 4.55) r.packing = Packing.LIQUID_ORDERED;
        else r.packing = Packing.FLUID;
        // The fluid halo sits near 4.5 Å and the liquid-ordered one near 4.6 Å. A tenth of an ångström is not a
        // safe basis for choosing between them, so where the peak falls between the two the reading is marked as
        // undecided rather than presented as a result; the sterol content decides it.
        if (!sharp && m.d > 4.4 && m.d < 4.7)
            r.notes.add(String.format("At d = %.2f Å the halo lies between the fluid (≈4.5 Å) and liquid-ordered "
                    + "(≈4.6 Å) positions, which are too close to separate on position alone. The sterol content "
                    + "of the sample decides which it is.", m.d));
    }

    static String describe(Result r, boolean sterolPresent) {
        StringBuilder sb = new StringBuilder("CHAIN PACKING (wide angle)\n");
        sb.append(String.format("  q range %.2f–%.2f Å⁻¹, read up to %.2f%n%n", r.qmin, r.qmax, r.usableTo));
        if (r.peaks.isEmpty()) {
            for (String n : r.notes) sb.append("  ! ").append(n).append('\n');
            return sb.toString();
        }
        sb.append(String.format("  %-10s %10s %10s %12s %12s%n", "", "q (Å⁻¹)", "d (Å)", "FWHM", "coherence"));
        for (int i = 0; i < r.peaks.size(); i++) {
            Peak p = r.peaks.get(i);
            sb.append(String.format("  %-10s %10.4f %10.3f %12.3f %10.0f Å%n",
                    i == 0 ? "main" : "second", p.q, p.d, p.fwhm, p.coherence));
        }
        Peak m = r.main();
        sb.append('\n');
        switch (r.packing) {
            case GEL:
                sb.append(String.format("  A narrow line at d = %.2f Å: the chains are ORDERED, a gel phase (Lβ).%n", m.d));
                sb.append("  Extended chains make the bilayer thicker than the same lipid would be when fluid, so a\n"
                        + "  SAXS thickness should be read against the gel state, not the fluid one.\n");
                break;
            case ORDERED_SHARP:
                sb.append(String.format("  A narrow line at d = %.2f Å with a second nearby: ordered chains on a "
                        + "distorted lattice,%n  a tilted or rippled gel (Lβ' or Pβ').%n", m.d));
                break;
            case LIQUID_ORDERED:
                sb.append(String.format("  A broad halo at d = %.2f Å: the chains are disordered but stretched — the "
                        + "liquid-ordered state%n  that a sterol produces above roughly 30 mol%%.%n", m.d));
                break;
            case FLUID:
                sb.append(String.format("  A broad halo at d = %.2f Å: the chains are MELTED, a fluid bilayer (Lα).%n", m.d));
                sb.append("  This is the usual state of an unsaturated lipid at room temperature.\n");
                break;
            default:
                break;
        }
        if (r.packing == Packing.GEL || r.packing == Packing.ORDERED_SHARP) {
            if (sterolPresent)
                sb.append("\n  ! A sterol is in the declared composition, but a sharp chain line is not what a\n"
                        + "    sterol-rich bilayer gives: above about 30 mol% it suppresses ordered packing and\n"
                        + "    leaves a broad halo near 4.6 Å. Either the sterol fraction is lower than entered,\n"
                        + "    or something else in the sample is ordering the chains.\n");
            else
                sb.append("\n  A sharp line argues against a sterol-rich bilayer: cholesterol above roughly\n"
                        + "  30 mol% leaves a broad halo near 4.6 Å instead.\n");
        }
        for (String n : r.notes) sb.append("  ! ").append(n).append('\n');
        sb.append("\n  Widths here are not corrected for instrumental broadening, so the coherence length is a\n"
                + "  lower limit on how far the packing order extends.\n");
        return sb.toString();
    }
}
