package version4;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.InverseTransform.GuinierTrim;

/**
 * Automatic Guinier analysis for a newly opened dataset (drop-in replacement, same public API).
 *
 * The work is done by version4.InverseTransform.GuinierTrim:
 *  - weighted fit of ln I(q) = ln I(0) - (Rg^2/3) q^2, weights (I/sigma)^2, so noisy points near the
 *    beamstop count for little instead of as much as the best-measured ones;
 *  - Rg found self-consistently (window q*Rg <= upper limit, iterated), with no fixed q limits or
 *    window sizes, so it works from Rg ~ 5 A to several hundred A;
 *  - leading points removed only when that fixes the fit: the misfit (relative to point-to-point
 *    noise) must drop significantly as they are removed, which separates a low-q defect
 *    (aggregation upturn, beamstop dip) from the bend of an elongated particle;
 *  - a least-median-of-squares line over the final window gives an independent robust Rg;
 *  - upper end of the window starts at qRg = 1.3 and shrinks (to 0.8) while the last points bend
 *    away from the line, which keeps elongated particles from being biased low.
 *
 * Errors: pass them when available (the 3-argument constructor). Without them, sigma is estimated
 * from the local point-to-point scatter of I(q) (second differences over a sliding window).
 *
 * Failure (no Guinier region found) leaves Rg = 0 as before; getMessage() says why.
 */
public class AutoRg {

    /** points either side used for the scatter-based error estimate */
    public static int ERROR_ESTIMATE_HALF_WINDOW = 10;

    private final XYSeries inputData;
    private double rg = 0, i_zero = 0, i_zero_error, rg_error, correlation_coefficient, chi2Reduced = Double.NaN;
    private double qRgLow = Double.NaN, qRgHigh = Double.NaN, robustRg = Double.NaN;
    private Number qminFinal, qmaxFinal;
    private int keptStartAt = 0, keptEndAt = 0, trimmed = 0;
    private boolean errorsEstimated;
    private String message;

    /**
     * @param data    I(q), not transformed; non-positive intensities are skipped
     * @param startAt first point to consider, 1-based (as from the spinner)
     */
    public AutoRg(XYSeries data, int startAt) {
        this(data, null, startAt);
    }

    /**
     * @param data    I(q)
     * @param errors  sigma(q) for the same q values (matched by q), or null to estimate from scatter
     * @param startAt first point to consider, 1-based
     */
    public AutoRg(XYSeries data, XYSeries errors, int startAt) {
        inputData = data;
        int first = Math.max(0, startAt - 1);
        int n = data.getItemCount();

        // q*I and q*sigma over [first, n) for q > 0, with a map back to the input indices
        double[] sigma = (errors == null) ? scatterErrors(data) : null;
        errorsEstimated = (errors == null);
        XYSeries qIq = new XYSeries("qIq"), qSig = new XYSeries("qSigma");
        int[] map = new int[n];
        int m = 0;
        for (int i = first; i < n; i++) {
            XYDataItem item = data.getDataItem(i);
            double q = item.getXValue();
            if (!(q > 0)) continue;
            double s;
            if (errors != null) {
                int j = errors.indexOf(item.getX());
                s = (j < 0) ? Double.NaN : errors.getY(j).doubleValue();
            } else {
                s = sigma[i];
            }
            if (!(s > 0)) continue;
            qIq.add(q, q*item.getYValue());
            qSig.add(q, q*s);
            map[m++] = i;
        }
        if (m < 12) {
            message = "too few points for a Guinier analysis";
            return;
        }

        GuinierTrim.Result g = GuinierTrim.trim(qIq, qSig);
        if (Double.isNaN(g.rg) || !(g.rg > 0)) {
            message = (g.message == null) ? "no Guinier region found" : g.message;
            return;
        }
        rg = g.rg;
        rg_error = g.rgSigma;
        i_zero = g.izero;
        i_zero_error = g.izeroSigma;
        chi2Reduced = g.chi2Reduced;
        robustRg = g.robustRg;
        qRgLow = g.qRgLow;
        qRgHigh = g.qRgHigh;
        keptStartAt = map[g.startIndex];
        keptEndAt = map[g.endIndex];
        trimmed = g.startIndex;
        qminFinal = data.getX(keptStartAt);
        qmaxFinal = data.getX(keptEndAt);
        message = g.message;
        correlation_coefficient = correlation(keptStartAt, keptEndAt);
    }

    /** sigma from local scatter: r_i = I_i - (I_(i-1)+I_(i+1))/2 has variance ~1.5 sigma^2 */
    private static double[] scatterErrors(XYSeries data) {
        int n = data.getItemCount();
        double[] r2 = new double[n], sigma = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double r = data.getY(i).doubleValue() - 0.5*(data.getY(i - 1).doubleValue() + data.getY(i + 1).doubleValue());
            r2[i] = r*r/1.5;
        }
        r2[0] = r2[1];
        r2[n - 1] = r2[n - 2];
        int h = ERROR_ESTIMATE_HALF_WINDOW;
        for (int i = 0; i < n; i++) {
            double s = 0;
            int c = 0;
            for (int j = Math.max(0, i - h); j <= Math.min(n - 1, i + h); j++) { s += r2[j]; c++; }
            sigma[i] = Math.sqrt(s/c);
        }
        return sigma;
    }

    /** Pearson correlation of ln I vs q^2 over the final window (kept for compatibility) */
    private double correlation(int a, int b) {
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        int c = 0;
        for (int i = a; i <= b; i++) {
            double I = inputData.getY(i).doubleValue();
            if (!(I > 0)) continue;
            double x = Math.pow(inputData.getX(i).doubleValue(), 2), y = Math.log(I);
            sx += x; sy += y; sxx += x*x; syy += y*y; sxy += x*y;
            c++;
        }
        double d = Math.sqrt((c*sxx - sx*sx)*(c*syy - sy*sy));
        return (d > 0) ? Math.abs(c*sxy - sx*sy)/d : 0;
    }

    /** Failure of autoRg returns zero */
    public double getRg() { return rg; }
    public double getI_zero() { return i_zero; }
    public double getRg_error() { return rg_error; }
    public double getI_zero_error() { return i_zero_error; }
    public double getCorrelation_coefficient() { return correlation_coefficient; }
    /** index (into the input series) of the first point of the Guinier window */
    public int getStartIndex() { return keptStartAt; }
    /** index (into the input series) of the last point of the Guinier window */
    public int getEndIndex() { return keptEndAt; }
    public Number getQminFinal() { return qminFinal; }
    public Number getQmaxFinal() { return qmaxFinal; }

    /** weighted reduced chi^2 of the Guinier fit (about 1 for a good fit with correct errors) */
    public double getChi2Reduced() { return chi2Reduced; }
    /** Rg from a least-median-of-squares line over the same window (independent check) */
    public double getRobustRg() { return robustRg; }
    /** qRg at the ends of the Guinier window */
    public double getQRgLow() { return qRgLow; }
    public double getQRgHigh() { return qRgHigh; }
    /** leading points removed as inconsistent with Guinier behaviour (aggregation, beamstop, ...) */
    public int getTrimmedPoints() { return trimmed; }
    /** true when errors were estimated from the scatter of I(q) rather than supplied */
    public boolean errorsWereEstimated() { return errorsEstimated; }
    /** null when nothing noteworthy happened; otherwise a short explanation for the user */
    public String getMessage() { return message; }
}
