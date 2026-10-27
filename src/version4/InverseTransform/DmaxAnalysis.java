package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Headless driver behind FindDmax: automatic qmax, consensus dmax, and the ensemble P(r) at the
 * chosen (qmax, dmax). No Swing here, so it can also be used in batch mode or tests.
 *
 *   DmaxAnalysis.Result r = DmaxAnalysis.run(qIq, qSigma, 50, 200, Double.NaN, Double.NaN,
 *                                            null, true, msg -> System.out.println(msg));
 *
 * Data are q*I(q) and q*sigma(q) (unscaled), already cut at the low-q end the user chose.
 */
public final class DmaxAnalysis {

    private DmaxAnalysis() {}

    public static final class Result {
        public final DmaxSelector.DmaxScan dmaxScan;
        public final DmaxSelector.QmaxScan qmaxScan;     // null when qmax was fixed
        public final EnsembleIFT ensemble;               // at (qmax, lowest adequate dmax)
        public final List<IndirectFT> fits;              // members of the ensemble
        public final List<String> names;
        public final double qmax, dmax;
        public final List<String> warnings;
        public final List<String> log;
        /** low-q handling: Guinier trim (null when not requested), first kept point (index into the
         *  series passed to run), its q, and leading points still inconsistent with the ensemble model */
        public GuinierTrim.Result guinier;
        public int lowQStart;
        public double qmin;
        public int lowQInconsistentWithModel;

        Result(DmaxSelector.DmaxScan ds, DmaxSelector.QmaxScan qs, EnsembleIFT ens, List<IndirectFT> fits,
               List<String> names, List<String> warnings, List<String> log) {
            dmaxScan = ds; qmaxScan = qs; ensemble = ens; this.fits = fits; this.names = names;
            qmax = ds.qmax; dmax = ds.suggested; this.warnings = warnings; this.log = log;
        }

        /** plain-text report for a summary panel or log file */
        public String report() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("qmin %.4f 1/A (%s)%n", qmin,
                    guinier == null ? "as given" : (lowQStart == 0 ? "no low-q points removed" : lowQStart + " low-q point" + (lowQStart == 1 ? "" : "s") + " removed")));
            if (guinier != null && !Double.isNaN(guinier.rg)) {
                sb.append(String.format("Guinier: Rg %.2f +/- %.2f A, I(0) %.5g +/- %.2g, qRg %.2f-%.2f%n",
                        guinier.rg, guinier.rgSigma, guinier.izero, guinier.izeroSigma, guinier.qRgLow, guinier.qRgHigh));
            }
            sb.append(String.format("qmax %.4f 1/A%s%n", qmax, qmaxScan == null ? " (fixed)" : " (automatic)"));
            sb.append(String.format("dmax: suggested %.1f A (data-supported band %.1f-%.1f A, most probable %.1f A)%n",
                    dmax, dmaxScan.lowestAdequate, dmaxScan.largestAdequate, dmaxScan.best));
            sb.append(String.format("      68%% range %.0f-%.0f A, 95%% range %.0f-%.0f A%n",
                    dmaxScan.interval68[0], dmaxScan.interval68[1], dmaxScan.interval95[0], dmaxScan.interval95[1]));
            sb.append("Best dmax per method:\n");
            for (int m = 0; m < dmaxScan.methods.length; m++) {
                sb.append(String.format("  %-11s %6.1f%s%n", dmaxScan.methods[m], dmaxScan.methodBest(m),
                        dmaxScan.included[m] ? "" : "  (excluded: oscillates)"));
            }
            if (ensemble != null) {
                sb.append(String.format("%nEnsemble at dmax %.1f A (%d Shannon bins of %.2f A):%n", dmax, ensemble.nBins, ensemble.binWidth));
                for (int m = 0; m < names.size(); m++) {
                    sb.append(String.format("  %-11s weight %.2f%s%n", names.get(m), ensemble.weights[m],
                            ensemble.exclusionReason[m] == null ? "" : "  (" + ensemble.exclusionReason[m] + ")"));
                }
                IndirectFT.Estimate i0 = ensemble.getIzeroParticle(), rg = ensemble.getRg(), vp = ensemble.getPorodVolume(qmax);
                sb.append(String.format("  I(0) %s%n  Rg   %s A%n  Vp(qmax) %s A^3%n", i0, rg, vp));
                int prior = 0;
                for (int k = 0; k < ensemble.nBins; k++) if (ensemble.isPriorDominated(k)) prior++;
                sb.append(String.format("  %d of %d bins set by model choice rather than data%n", prior, ensemble.nBins));
            }
            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String w : warnings) sb.append("  ").append(w).append('\n');
            }
            return sb.toString();
        }
    }

    /** a step giving about 40 trial values between lo and hi, rounded to 0.5, 1, 2, 2.5, 5, 10, ... */
    public static double niceStep(double lo, double hi) {
        double raw = (hi - lo)/40.0;
        double p = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double f : new double[]{1, 2, 2.5, 5, 10}) if (f*p >= raw) return Math.max(0.5, f*p);
        return Math.max(0.5, 10*p);
    }

    /**
     * @param qIq, qSigma   q*I(q) and q*sigma(q), low-q cut applied, high-q end as far as available
     * @param dmaxLo, dmaxHi search range
     * @param step          dmax step, NaN for niceStep
     * @param fixedQmax     qmax to use, NaN for automatic selection
     * @param methodNames   subset of DmaxSelector.defaultMethods() keys, null for all
     * @param positivity    include the positivity term in the scores
     * @param progress      stage messages (may be null)
     */
    public static Result run(XYSeries qIq, XYSeries qSigma, double dmaxLo, double dmaxHi, double step,
                             double fixedQmax, Set<String> methodNames, boolean positivity, Consumer<String> progress) {
        return run(qIq, qSigma, dmaxLo, dmaxHi, step, fixedQmax, methodNames, positivity, false, progress);
    }

    /**
     * As above; with autoLowQ the low-q end is trimmed automatically (GuinierTrim) before the scans,
     * and after the ensemble is built its model I(q) is used to validate the low-q end: leading
     * points that still deviate systematically are removed and the analysis is rerun once. Without
     * autoLowQ the low-q end is only validated (a warning is added if it fails).
     * qIq/qSigma should then start at the first measured point.
     */
    public static Result run(XYSeries qIq, XYSeries qSigma, double dmaxLo, double dmaxHi, double step,
                             double fixedQmax, Set<String> methodNames, boolean positivity, boolean autoLowQ,
                             Consumer<String> progress) {
        Consumer<String> say = (progress == null) ? s -> {} : progress;
        GuinierTrim.Result guinier = null;
        int start = 0;
        if (autoLowQ) {
            say.accept("Trimming the low-q end (Guinier consistency)");
            guinier = GuinierTrim.trim(qIq, qSigma);
            start = guinier.startIndex;
        }
        Result r = runOnce(from(qIq, start), from(qSigma, start), dmaxLo, dmaxHi, step, fixedQmax, methodNames, positivity, say);
        int extra = validateLowQ(r, qIq, qSigma, start);
        if (extra > 0 && autoLowQ) {
            say.accept(String.format("%d more low-q point%s disagree with P(r): rerunning", extra, extra == 1 ? "" : "s"));
            start += extra;
            List<String> earlier = r.log;
            r = runOnce(from(qIq, start), from(qSigma, start), dmaxLo, dmaxHi, step, fixedQmax, methodNames, positivity, say);
            r.log.add(0, String.format("first pass removed %d point(s) by Guinier; P(r) model flagged %d more", guinier.startIndex, extra));
            r.log.addAll(0, earlier);
            extra = validateLowQ(r, qIq, qSigma, start);
        }
        r.guinier = guinier;
        r.lowQStart = start;
        r.qmin = qIq.getX(start).doubleValue();
        r.lowQInconsistentWithModel = extra;
        if (extra > 0) {
            r.warnings.add(String.format("the first %d point%s systematically from the P(r) model (aggregation, beamstop or interparticle effects?)",
                    extra, extra == 1 ? " deviates" : "s deviate"));
        }
        say.accept("Done");
        return r;
    }

    /** leading points (from start) that deviate systematically from the ensemble's model I(q) */
    private static int validateLowQ(Result r, XYSeries qIq, XYSeries qSigma, int start) {
        if (r.ensemble == null) return 0;
        XYSeries d = IndirectFT.truncateSeries(from(qIq, start), r.qmax), e = IndirectFT.truncateSeries(from(qSigma, start), r.qmax);
        double rg = r.ensemble.getRg().value;
        return GuinierTrim.leadingInconsistentWithModel(r.ensemble, d, e, rg, r.dmax);
    }

    /** copy of a series from index start on */
    static XYSeries from(XYSeries s, int start) {
        XYSeries out = new XYSeries(s.getKey());
        for (int i = start; i < s.getItemCount(); i++) out.add(s.getDataItem(i));
        return out;
    }

    private static Result runOnce(XYSeries qIq, XYSeries qSigma, double dmaxLo, double dmaxHi, double step,
                                  double fixedQmax, Set<String> methodNames, boolean positivity, Consumer<String> say) {
        if (Double.isNaN(step) || step <= 0) step = niceStep(dmaxLo, dmaxHi);
        double[] grid = DmaxSelector.grid(dmaxLo, dmaxHi, step);
        DmaxSelector.POSITIVITY_WEIGHT = positivity ? 1.0 : 0.0;

        Map<String, DmaxSelector.Method> all = DmaxSelector.defaultMethods(), methods = new LinkedHashMap<>();
        for (Map.Entry<String, DmaxSelector.Method> e : all.entrySet()) {
            if (methodNames == null || methodNames.contains(e.getKey())) methods.put(e.getKey(), e.getValue());
        }
        if (methods.isEmpty()) throw new IllegalArgumentException("no transform method selected");

        List<String> log = new ArrayList<>(), warnings = new ArrayList<>();
        DmaxSelector.DmaxScan ds;
        DmaxSelector.QmaxScan qs = null;
        if (!Double.isNaN(fixedQmax)) {
            say.accept(String.format("Scanning dmax %.0f-%.0f A at qmax %.4f", dmaxLo, dmaxHi, fixedQmax));
            ds = DmaxSelector.scanDmax(qIq, qSigma, fixedQmax, grid, methods);
            log.add(ds.summary());
        } else {
            say.accept(String.format("Scanning dmax %.0f-%.0f A using all data (q <= %.4f)", dmaxLo, dmaxHi, qIq.getMaxX()));
            ds = DmaxSelector.scanDmax(qIq, qSigma, qIq.getMaxX(), grid, methods);
            log.add(ds.summary());
            for (int it = 0; it < 3; it++) {
                say.accept(String.format("Selecting qmax at dmax %.0f A", ds.suggested));
                qs = DmaxSelector.selectQmax(qIq, qSigma, ds.suggested, methods);
                log.add(qs.summary());
                if (Math.abs(qs.qmax - ds.qmax) < 1e-9) break;
                double prev = ds.suggested;
                say.accept(String.format("Rescanning dmax at qmax %.4f", qs.qmax));
                ds = DmaxSelector.scanDmax(qIq, qSigma, qs.qmax, grid, methods);
                log.add(ds.summary());
                if (Math.abs(ds.suggested - prev) <= step) break;
            }
        }

        double bLo = Double.POSITIVE_INFINITY, bHi = Double.NEGATIVE_INFINITY;
        for (int m = 0; m < ds.methods.length; m++) {
            if (!ds.included[m]) continue;
            bLo = Math.min(bLo, ds.methodBest(m));
            bHi = Math.max(bHi, ds.methodBest(m));
        }
        if (bHi - bLo > Math.max(3*step, 0.1*bLo)) {
            warnings.add(String.format("methods disagree on dmax (best values %.0f-%.0f A): the suggestion is less reliable; check qmax and the error model", bLo, bHi));
        }
        if (ds.lowestAdequate <= grid[0] + 1e-9) warnings.add("the data-supported band starts at the lower search bound: lower the bound");
        if (ds.largestAdequate >= grid[grid.length - 1] - 1e-9) warnings.add("the data-supported band reaches the upper search bound: raise the bound");
        if (ds.best >= grid[grid.length - 1] - 1e-9) warnings.add("most probable dmax is at the upper search bound: raise the bound");
        if (ds.interval95[1] >= grid[grid.length - 1] - 1e-9) warnings.add("95% range reaches the upper bound: dmax may be larger");

        say.accept(String.format("Building ensemble P(r) at dmax %.1f A", ds.suggested));
        XYSeries d = IndirectFT.truncateSeries(qIq, ds.qmax), e = IndirectFT.truncateSeries(qSigma, ds.qmax);
        double cHat = ds.cHat;
        List<IndirectFT> fits = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, DmaxSelector.Method> me : methods.entrySet()) {
            IndirectFT f = DmaxSelector.fitOrNull(me.getValue(), d, e, ds.suggested, d.getMaxX(), cHat);
            if (f == null) { warnings.add(me.getKey() + " could not be fitted at the suggested dmax and is left out of the ensemble"); continue; }
            fits.add(f);
            names.add(me.getKey());
        }
        // ceil(qmax dmax / pi) >= MIN  <=>  dmax > (MIN - 1) pi / qmax
        double dShannon = (DmaxSelector.MIN_SHANNON_CHANNELS - 1)*Math.PI/ds.qmax;
        if (grid[0] <= dShannon) {
            warnings.add(String.format("trial dmax up to %.0f A skipped: fewer than %d Shannon channels at qmax %.4f",
                    dShannon, DmaxSelector.MIN_SHANNON_CHANNELS, ds.qmax));
        }
        EnsembleIFT ens = null;
        try {
            if (fits.isEmpty()) throw new IllegalArgumentException("no method could be fitted");
            ens = new EnsembleIFT(fits, names, EnsembleIFT.Weighting.EQUAL);
        } catch (IllegalArgumentException ex) {
            warnings.add("ensemble not built: " + ex.getMessage());
        }
        Result r = new Result(ds, qs, ens, fits, names, warnings, log);
        r.qmin = qIq.getX(0).doubleValue();
        return r;
    }
}
