package version4.FormFactors;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYErrorRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import version4.FormFactors.LangevinResult;
import version4.FormFactors.PolymerAnalysis;
import version4.FormFactors.SpeciesProfile;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** JFreeChart (1.0.x) builders for the FormFactor panel. All methods return a finished chart; nothing is shared. */
public final class FormFactorCharts {

    private FormFactorCharts() {}

    public enum Weighting {
        VOLUME("Volume (mass) fraction"), NUMBER("Number"), INTENSITY("Intensity (as sampled)");
        final String label;
        Weighting(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    static final Color DATA = new Color(0x8c959f), FIT = new Color(0x2563eb), ACCENT = new Color(0xdc6b19),
            GOOD = new Color(0x16a34a), PURPLE = new Color(0x9333ea), GRID = new Color(0xe5e7eb), MUTED = new Color(0x6e7781);
    static final Color[] SERIES = {FIT, ACCENT, GOOD, PURPLE};
    static final Font TITLE = new Font("SansSerif", Font.BOLD, 13), SMALL = new Font("SansSerif", Font.PLAIN, 11);

    // ------------------------------------------------------------------ fit + residuals

    /**
     * log I(q) with error bars and the fit, plus a normalised residual panel underneath.
     * @param extra optional additional fits (e.g. rejected points of a profile scan), may be null
     */
    public static JFreeChart fitChart(LangevinResult r, String title, List<LangevinResult> extra, List<String> extraLabels) {
        YIntervalSeries data = new YIntervalSeries("data" + (r.preparation != null ? " (prepared)" : ""));
        double ymin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < r.q.length; j++) {
            double lo = r.intensity[j] - r.sigma[j], hi = r.intensity[j] + r.sigma[j];
            if (r.intensity[j] > 0) ymin = Math.min(ymin, r.intensity[j]);
            // log axis: clip the lower bar at a fraction of the point so it stays drawable
            if (lo <= 0) lo = r.intensity[j] > 0 ? r.intensity[j] * 0.1 : Double.NaN;
            if (r.intensity[j] > 0) data.add(r.q[j], r.intensity[j], lo, hi);
        }
        YIntervalSeriesCollection dataSet = new YIntervalSeriesCollection();
        dataSet.addSeries(data);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setBaseShapesVisible(true);
        err.setSeriesShape(0, new Ellipse2D.Double(-2, -2, 4, 4));
        err.setSeriesPaint(0, DATA);
        err.setErrorPaint(new Color(0xc4c9cf));
        err.setDrawXError(false);
        err.setCapLength(0);

        XYSeriesCollection fits = new XYSeriesCollection();
        XYLineAndShapeRenderer line = new XYLineAndShapeRenderer(true, false);
        addFit(fits, line, r, String.format("fit  red. χ² %.3f", r.chi2Reduced), 0, ymin);
        if (extra != null)
            for (int k = 0; k < extra.size(); k++)
                addFit(fits, line, extra.get(k), extraLabels.get(k), k + 1, ymin);

        double ymax = 0;
        for (double v : r.intensity) ymax = Math.max(ymax, v);
        LogAxis y = logAxis("I(q)");
        y.setSmallestValue(ymin > 0 && ymin < Double.POSITIVE_INFINITY ? ymin * 0.3 : 1e-6);
        if (ymin > 0 && ymax / ymin > 300) {           // many decades: one labelled tick per decade
            y.setTickUnit(new NumberTickUnit(1, y.getNumberFormatOverride()));
            y.setMinorTickCount(9);
            y.setMinorTickMarksVisible(true);
        }
        XYPlot top = new XYPlot(fits, null, y, line);
        top.setDataset(1, dataSet);
        top.setRenderer(1, err);
        top.setDatasetRenderingOrder(org.jfree.chart.plot.DatasetRenderingOrder.FORWARD);
        style(top);

        // residuals
        XYSeriesCollection res = new XYSeriesCollection();
        XYLineAndShapeRenderer rr = new XYLineAndShapeRenderer(true, true);
        addResiduals(res, rr, r, "best", 0);
        if (extra != null)
            for (int k = 0; k < extra.size(); k++) addResiduals(res, rr, extra.get(k), extraLabels.get(k), k + 1);
        NumberAxis ry = new NumberAxis("(I − fit)/σ");
        ry.setTickLabelFont(SMALL);
        ry.setAutoRangeIncludesZero(true);
        XYPlot bottom = new XYPlot(res, null, ry, rr);
        style(bottom);
        bottom.addRangeMarker(new IntervalMarker(-2, 2, new Color(0xd0d7de), new BasicStroke(0f), null, null, 0.35f),
                org.jfree.ui.Layer.BACKGROUND);
        bottom.addRangeMarker(new ValueMarker(0, MUTED, new BasicStroke(0.8f)));

        NumberAxis x = new NumberAxis("q (Å⁻¹)");
        x.setTickLabelFont(SMALL);
        x.setAutoRangeIncludesZero(false);
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(x);
        plot.setGap(6);
        plot.add(top, 3);
        plot.add(bottom, 1);
        JFreeChart chart = new JFreeChart(title, TITLE, plot, true);
        finish(chart);
        return chart;
    }

    private static void addFit(XYSeriesCollection c, XYLineAndShapeRenderer rend, LangevinResult r, String label, int k, double ymin) {
        XYSeries s = new XYSeries(label, false, true);
        double floor = ymin > 0 && ymin < Double.POSITIVE_INFINITY ? ymin * 0.05 : 1e-12;
        for (int j = 0; j < r.q.length; j++) if (r.fit[j] > floor) s.add(r.q[j], r.fit[j]);
        c.addSeries(s);
        int idx = c.getSeriesCount() - 1;
        rend.setSeriesPaint(idx, k == 0 ? FIT : SERIES[(k) % SERIES.length]);
        rend.setSeriesStroke(idx, k == 0 ? new BasicStroke(1.8f)
                : new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6f, 4f}, 0f));
    }

    private static void addResiduals(XYSeriesCollection c, XYLineAndShapeRenderer rend, LangevinResult r, String label, int k) {
        XYSeries s = new XYSeries(label, false, true);
        for (int j = 0; j < r.q.length; j++) s.add(r.q[j], (r.intensity[j] - r.fit[j]) / r.sigma[j]);
        c.addSeries(s);
        int idx = c.getSeriesCount() - 1;
        rend.setSeriesPaint(idx, k == 0 ? FIT : SERIES[k % SERIES.length]);
        rend.setSeriesShapesVisible(idx, k == 0);
        rend.setSeriesShape(idx, new Ellipse2D.Double(-1.5, -1.5, 3, 3));
        rend.setSeriesStroke(idx, new BasicStroke(k == 0 ? 0.8f : 1.1f));
        rend.setSeriesVisibleInLegend(idx, false);
    }

    // ------------------------------------------------------------------ 1D distributions

    /** distribution of parameter k (mean over replicas, shaded ± replica sd), on a log size axis */
    public static JFreeChart distributionChart(LangevinResult r, int k, Weighting w) {
        double[] dens, sd;
        switch (w) {
            case NUMBER: dens = r.numberDensity[k]; sd = r.numberDensityStd[k]; break;
            case INTENSITY: dens = r.intensityDensity[k]; sd = r.intensityDensityStd[k]; break;
            default: dens = r.volumeDensity[k]; sd = r.volumeDensityStd[k];
        }
        // densities are per ln-unit; normalise the peak to 1 for display
        double peak = 0;
        for (double v : dens) peak = Math.max(peak, v);
        if (!(peak > 0)) peak = 1;
        YIntervalSeries s = new YIntervalSeries(w.label);
        double[] e = r.binLnEdges[k];
        // step outline: two points per bin
        for (int b = 0; b < dens.length; b++) {
            double v = dens[b] / peak, d = sd == null ? 0 : sd[b] / peak;
            double lo = Math.max(0, v - d), hi = v + d;
            s.add(Math.exp(e[b]), v, lo, hi);
            s.add(Math.exp(e[b + 1]) * (1 - 1e-9), v, lo, hi);
        }
        YIntervalSeriesCollection c = new YIntervalSeriesCollection();
        c.addSeries(s);
        DeviationRenderer rend = new DeviationRenderer(true, false);
        rend.setSeriesPaint(0, FIT);
        rend.setSeriesFillPaint(0, FIT);
        rend.setAlpha(0.2f);
        rend.setSeriesStroke(0, new BasicStroke(1.6f));

        String name = r.parameterNames[k];
        LogAxis x = logAxis(name + " (Å)");
        x.setSmallestValue(1e-3);
        NumberAxis y = new NumberAxis("relative density per ln " + name);
        y.setTickLabelFont(SMALL);
        y.setRange(0, 1.1 * Math.max(1, maxHi(s)));
        XYPlot plot = new XYPlot(c, x, y, rend);
        style(plot);

        double[] mean = w == Weighting.NUMBER ? r.numberMean : w == Weighting.INTENSITY ? r.intensityMean : r.volumeMean;
        double[] std = w == Weighting.NUMBER ? r.numberStd : w == Weighting.INTENSITY ? r.intensityStd : r.volumeStd;
        if (mean != null && mean[k] > 0) {
            ValueMarker m = new ValueMarker(mean[k], ACCENT, new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0));
            m.setLabel(String.format("mean %.1f ± %.1f Å", mean[k], std[k]));
            m.setLabelFont(SMALL);
            m.setLabelPaint(ACCENT);
            m.setLabelAnchor(org.jfree.ui.RectangleAnchor.TOP_RIGHT);
            m.setLabelTextAnchor(org.jfree.ui.TextAnchor.TOP_LEFT);
            plot.addDomainMarker(m);
        }
        JFreeChart chart = new JFreeChart(name + " distribution", TITLE, plot, false);
        chart.addSubtitle(new TextTitle(w.label + " weighted; band = ± sd over replicas", SMALL));
        finish(chart);
        return chart;
    }

    private static double maxHi(YIntervalSeries s) {
        double m = 0;
        for (int i = 0; i < s.getItemCount(); i++) m = Math.max(m, s.getYHighValue(i));
        return m;
    }

    // ------------------------------------------------------------------ joint (pair) distribution

    /** volume-weighted joint histogram of parameter pair p, drawn in ln coordinates with Å tick labels */
    public static JFreeChart jointChart(LangevinResult r, int p) {
        int i = r.pairs[p][0], j = r.pairs[p][1];
        double[][] h = r.pairDensity[p];
        double[] ei = r.binLnEdges[i], ej = r.binLnEdges[j];
        int Bi = h.length, Bj = h[0].length;
        double max = 0;
        for (double[] row : h) for (double v : row) max = Math.max(max, v);
        if (!(max > 0)) max = 1;
        double[][] xyz = new double[3][Bi * Bj];
        int n = 0;
        for (int a = 0; a < Bi; a++)
            for (int b = 0; b < Bj; b++) {
                xyz[0][n] = 0.5 * (ei[a] + ei[a + 1]);
                xyz[1][n] = 0.5 * (ej[b] + ej[b + 1]);
                xyz[2][n] = h[a][b] / max;
                n++;
            }
        DefaultXYZDataset d = new DefaultXYZDataset();
        d.addSeries("joint", xyz);
        XYBlockRenderer rend = new XYBlockRenderer();
        rend.setBlockWidth(ei[1] - ei[0]);
        rend.setBlockHeight(ej[1] - ej[0]);
        LookupPaintScale scale = new LookupPaintScale(0, 1.0001, new Color(255, 255, 255, 0));
        // white -> blue ramp, values below 0.5 % stay white
        for (int t = 0; t <= 40; t++) {
            double v = t / 40.0;
            float g = (float) Math.pow(v, 0.6);
            scale.add(Math.max(0.005, v), new Color(1 - 0.85f * g, 1 - 0.62f * g, 1 - 0.08f * g));
        }
        rend.setPaintScale(scale);

        NumberAxis x = lnAxis(r.parameterNames[i]), y = lnAxis(r.parameterNames[j]);
        x.setRange(ei[0], ei[Bi]);
        y.setRange(ej[0], ej[Bj]);
        XYPlot plot = new XYPlot(d, x, y, rend);
        style(plot);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        plot.setDatasetRenderingOrder(org.jfree.chart.plot.DatasetRenderingOrder.FORWARD);

        // c = a guide line for spheroids (a, c)
        if (r.parameterNames[i].toLowerCase().contains("a") && r.parameterNames[j].toLowerCase().contains("c")) {
            XYSeries diag = new XYSeries("c = a");
            double lo = Math.max(ei[0], ej[0]), hi = Math.min(ei[Bi], ej[Bj]);
            if (hi > lo) {
                diag.add(lo, lo);
                diag.add(hi, hi);
                plot.setDataset(1, new XYSeriesCollection(diag));
                XYLineAndShapeRenderer dr = new XYLineAndShapeRenderer(true, false);
                dr.setSeriesPaint(0, MUTED);
                dr.setSeriesStroke(0, new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0));
                plot.setRenderer(1, dr);
            }
        }
        JFreeChart chart = new JFreeChart("Joint distribution " + r.parameterNames[i] + " vs " + r.parameterNames[j], TITLE, plot, false);
        NumberAxis sa = new NumberAxis("relative");
        sa.setRange(0, 1);
        sa.setTickLabelFont(SMALL);
        PaintScaleLegend legend = new PaintScaleLegend(scale, sa);
        legend.setPosition(RectangleEdge.RIGHT);
        legend.setMargin(new RectangleInsets(6, 6, 6, 6));
        legend.setStripWidth(10);
        chart.addSubtitle(legend);
        chart.addSubtitle(new TextTitle("volume-fraction weighted", SMALL));
        finish(chart);
        return chart;
    }

    /** base-10 log axis with compact labels: 0.0123, 1.58, 158, 1.2e5 */
    static LogAxis logAxis(String label) {
        LogAxis a = new LogAxis(label);
        a.setBase(10);
        a.setTickLabelFont(SMALL);
        a.setNumberFormatOverride(new NumberFormat() {
            @Override public StringBuffer format(double v, StringBuffer sb, FieldPosition pos) { return sb.append(compact(v)); }
            @Override public StringBuffer format(long v, StringBuffer sb, FieldPosition pos) { return sb.append(compact(v)); }
            @Override public Number parse(String s, ParsePosition p) { return null; }
        });
        return a;
    }

    static String compact(double v) {
        if (v == 0) return "0";
        double a = Math.abs(v);
        if (a >= 1e5 || a < 1e-3) {
            int e = (int) Math.floor(Math.log10(a) + 1e-9);
            double m = v / Math.pow(10, e);
            return (Math.abs(m - Math.rint(m)) < 1e-6 ? String.valueOf((long) Math.rint(m)) : String.format("%.2g", m)) + "e" + e;
        }
        String s = new DecimalFormat(a >= 100 ? "0" : a >= 10 ? "0.#" : a >= 1 ? "0.##" : "0.###").format(v);
        return s;
    }

    /** NumberAxis in ln(x) coordinates that labels ticks in real units */
    private static NumberAxis lnAxis(String name) {
        NumberAxis a = new NumberAxis(name + " (Å)");
        a.setTickLabelFont(SMALL);
        a.setNumberFormatOverride(new NumberFormat() {
            @Override public StringBuffer format(double v, StringBuffer sb, FieldPosition pos) {
                double x = Math.exp(v);
                return sb.append(x >= 100 ? String.format("%.0f", x) : x >= 10 ? String.format("%.1f", x) : String.format("%.2f", x));
            }
            @Override public StringBuffer format(long v, StringBuffer sb, FieldPosition pos) { return format((double) v, sb, pos); }
            @Override public Number parse(String s, ParsePosition p) { return null; }
        });
        a.setTickUnit(new NumberTickUnit(Math.log(10) / 4));   // ~ quarter decades
        a.setAutoRangeIncludesZero(false);
        return a;
    }

    // ------------------------------------------------------------------ convergence

    public static JFreeChart convergenceChart(LangevinResult r) {
        XYSeries chi = new XYSeries("reduced χ²"), temp = new XYSeries("temperature");
        if (r.traceChi2 != null)
            for (int t = 0; t < r.traceChi2.length; t++) if (r.traceChi2[t] > 0) chi.add(t, r.traceChi2[t]);
        if (r.traceTemperature != null)
            for (int t = 0; t < r.traceTemperature.length; t++) if (r.traceTemperature[t] > 0) temp.add(t, r.traceTemperature[t]);
        LogAxis y0 = logAxis("reduced χ²");
        y0.setSmallestValue(1e-3);
        y0.setLabelPaint(FIT);
        LogAxis y1 = logAxis("temperature");
        y1.setSmallestValue(1e-12);
        y1.setTickUnit(new NumberTickUnit(1, y1.getNumberFormatOverride()));
        y1.setLabelPaint(ACCENT);
        NumberAxis x = new NumberAxis("recorded step (replica 1)");
        x.setTickLabelFont(SMALL);
        XYLineAndShapeRenderer r0 = new XYLineAndShapeRenderer(true, false);
        r0.setSeriesPaint(0, FIT);
        XYLineAndShapeRenderer r1 = new XYLineAndShapeRenderer(true, false);
        r1.setSeriesPaint(0, ACCENT);
        XYPlot plot = new XYPlot(new XYSeriesCollection(chi), x, y0, r0);
        if (temp.getItemCount() > 0) {
            plot.setRangeAxis(1, y1);
            plot.setDataset(1, new XYSeriesCollection(temp));
            plot.mapDatasetToRangeAxis(1, 1);
            plot.setRenderer(1, r1);
        }
        style(plot);
        if (r.chi2Target != null && r.chi2Target.length > 0 && r.chi2Target[0] > 0) {
            ValueMarker m = new ValueMarker(r.chi2Target[0], MUTED, new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0));
            m.setLabel("target");
            m.setLabelFont(SMALL);
            m.setLabelAnchor(org.jfree.ui.RectangleAnchor.TOP_LEFT);
            m.setLabelTextAnchor(org.jfree.ui.TextAnchor.BOTTOM_LEFT);
            plot.addRangeMarker(m);
        }
        JFreeChart chart = new JFreeChart("Convergence", TITLE, plot, true);
        chart.addSubtitle(new TextTitle("quench (error floor, T→0), heat to target, equilibrate, sample", SMALL));
        finish(chart);
        return chart;
    }

    // ------------------------------------------------------------------ species-fraction profile

    /** Δχ² vs fraction. zoom = false: whole 0..1 range on a log axis of 1 + Δχ²; zoom = true: linear, near the minimum */
    public static JFreeChart profileChart(SpeciesProfile.Profile p, boolean zoom) {
        XYSeries pts = new XYSeries("fits (incl. seed repeats)", false, true);
        XYSeries env = new XYSeries("lower envelope");
        List<SpeciesProfile.Point> sorted = new ArrayList<>(p.points);
        sorted.sort(Comparator.comparingDouble(a -> a.numberFraction));
        double maxD = 0;
        double off = zoom ? 0 : 1;   // log axis: plot 1 + Δχ²
        for (SpeciesProfile.Point pt : sorted) {
            double d = pt.chi2Total - p.bestChi2Total;
            pts.add(pt.numberFraction, d + off);
            maxD = Math.max(maxD, d);
        }
        // lower envelope per target fraction (seed repeats share a target)
        java.util.TreeMap<Double, double[]> best = new java.util.TreeMap<>();
        for (SpeciesProfile.Point pt : sorted) {
            double key = Math.round(pt.intensityFraction * 1e4) / 1e4;   // field holds the target number fraction
            double d = pt.chi2Total - p.bestChi2Total;
            double[] cur = best.get(key);
            if (cur == null || d < cur[1]) best.put(key, new double[]{pt.numberFraction, d});
        }
        List<double[]> e = new ArrayList<>(best.values());
        e.sort(Comparator.comparingDouble(a -> a[0]));
        for (double[] v : e) env.add(v[0], v[1] + off);

        XYSeriesCollection c = new XYSeriesCollection();
        c.addSeries(env);
        c.addSeries(pts);
        XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer();
        rend.setSeriesLinesVisible(0, true);
        rend.setSeriesShapesVisible(0, false);
        rend.setSeriesPaint(0, FIT);
        rend.setSeriesLinesVisible(1, false);
        rend.setSeriesShapesVisible(1, true);
        rend.setSeriesShape(1, new Ellipse2D.Double(-3, -3, 6, 6));
        rend.setSeriesShapesFilled(1, false);
        rend.setSeriesPaint(1, FIT);

        String sp = p.speciesName == null ? "empty" : p.speciesName;
        NumberAxis x = new NumberAxis(sp + " fraction (by number)");
        x.setTickLabelFont(SMALL);
        org.jfree.chart.axis.ValueAxis y;
        if (zoom) {
            double half = Math.max(0.03, 1.5 * Math.max(p.bestNumberFraction - p.lo95, p.hi95 - p.bestNumberFraction));
            if (!Double.isNaN(p.parabolaHalfWidth95)) half = Math.max(half, 1.5 * p.parabolaHalfWidth95);
            half = Math.min(half, 0.25);
            x.setRange(Math.max(0, p.bestNumberFraction - half), Math.min(1, p.bestNumberFraction + half));
            NumberAxis ny = new NumberAxis("Δχ² (total)");
            ny.setTickLabelFont(SMALL);
            ny.setRange(-0.5, 16);
            y = ny;
        } else {
            x.setRange(0, 1);
            LogAxis ly = logAxis("1 + Δχ² (total)");
            ly.setSmallestValue(0.5);
            ly.setRange(0.7, Math.max(100, maxD * 2));
            ly.setTickUnit(new NumberTickUnit(1, ly.getNumberFormatOverride()));
            y = ly;
        }
        XYPlot plot = new XYPlot(c, x, y, rend);
        style(plot);
        for (double lev : new double[]{1, 4}) {
            ValueMarker m = new ValueMarker(lev + off, MUTED, new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                    lev == 1 ? new float[]{5, 3} : new float[]{2, 2}, 0));
            m.setLabel("Δχ² = " + (int) lev);
            m.setLabelFont(SMALL);
            m.setLabelAnchor(org.jfree.ui.RectangleAnchor.TOP_LEFT);
            m.setLabelTextAnchor(org.jfree.ui.TextAnchor.BOTTOM_LEFT);
            plot.addRangeMarker(m);
        }
        if (p.hi68 > p.lo68) {
            IntervalMarker band = new IntervalMarker(p.lo68, p.hi68, new Color(37, 99, 235, 40));
            band.setLabel("68 %");
            band.setLabelFont(SMALL);
            plot.addDomainMarker(band, org.jfree.ui.Layer.BACKGROUND);
        }
        ValueMarker b = new ValueMarker(p.bestNumberFraction, ACCENT, new BasicStroke(1.2f));
        plot.addDomainMarker(b);
        if (zoom && !Double.isNaN(p.parabolaCentre)) {
            XYSeries par = new XYSeries("parabola (3 lowest points)");
            double A = 1.0 / (p.parabolaHalfWidth68 * p.parabolaHalfWidth68);
            double y0 = 0;   // vertex at the best χ²
            for (int k = 0; k <= 100; k++) {
                double f = x.getLowerBound() + k * (x.getUpperBound() - x.getLowerBound()) / 100;
                double v = A * (f - p.parabolaCentre) * (f - p.parabolaCentre) + y0;
                if (v <= 17) par.add(f, v);
            }
            c.addSeries(par);
            rend.setSeriesLinesVisible(2, true);
            rend.setSeriesShapesVisible(2, false);
            rend.setSeriesPaint(2, GOOD);
            rend.setSeriesStroke(2, new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{5, 3}, 0));
        }
        String head = zoom
                ? (Double.isNaN(p.parabolaCentre) ? "Near the minimum"
                   : String.format("Near the minimum: parabola %.3f ± %.3f", p.parabolaCentre, p.parabolaHalfWidth68))
                : String.format("Profile: %s fraction %.3f (68 %%: %.3f-%.3f, 95 %%: %.3f-%.3f)", sp,
                    p.bestNumberFraction, p.lo68, p.hi68, p.lo95, p.hi95);
        JFreeChart chart = new JFreeChart(head, TITLE, plot, true);
        String sub = String.format("best χ² at each fixed fraction (geometry re-optimised); optimiser noise sd %.2f", p.optimizerNoise);
        if (p.optimizerNoise > 0.5) sub += " (≈ Δχ² = 1: interval approximate)";
        if (zoom) sub = "shaded: 68 % interval; dashed green: parabola through the 3 lowest points";
        chart.addSubtitle(new TextTitle(sub, SMALL));
        finish(chart);
        return chart;
    }

    /** profile of a global setting (Flory exponent, Kuhn length): full range (log 1 + Δχ²) or zoom near the minimum */
    public static JFreeChart parameterProfileChart(version4.FormFactors.ParameterProfile.Profile p, boolean zoom) {
        XYSeries pts = new XYSeries("fits (incl. seed repeats)", false, true);
        java.util.TreeMap<Double, Double> env = new java.util.TreeMap<>();
        double off = zoom ? 0 : 1, maxD = 0, vmin = Double.POSITIVE_INFINITY, vmax = Double.NEGATIVE_INFINITY;
        for (version4.FormFactors.ParameterProfile.Point pt : p.points) {
            double d = pt.chi2Total - p.bestChi2Total;
            pts.add(pt.value, d + off);
            env.merge(pt.value, d, Math::min);
            maxD = Math.max(maxD, d);
            vmin = Math.min(vmin, pt.value);
            vmax = Math.max(vmax, pt.value);
        }
        XYSeries line = new XYSeries("lower envelope");
        for (java.util.Map.Entry<Double, Double> e : env.entrySet()) line.add(e.getKey().doubleValue(), e.getValue() + off);
        XYSeriesCollection c = new XYSeriesCollection();
        c.addSeries(line);
        c.addSeries(pts);
        XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer();
        rend.setSeriesLinesVisible(0, true);
        rend.setSeriesShapesVisible(0, false);
        rend.setSeriesPaint(0, FIT);
        rend.setSeriesLinesVisible(1, false);
        rend.setSeriesShape(1, new Ellipse2D.Double(-3, -3, 6, 6));
        rend.setSeriesShapesFilled(1, false);
        rend.setSeriesPaint(1, FIT);
        String name = p.parameter.equals("nu") ? "Flory exponent ν" : p.parameter.equals("b") ? "Kuhn length b (Å)" : p.parameter;
        NumberAxis x = new NumberAxis(name);
        x.setTickLabelFont(SMALL);
        x.setAutoRangeIncludesZero(false);
        org.jfree.chart.axis.ValueAxis y;
        if (zoom) {
            double half = Math.max(p.hi95 - p.bestValue, p.bestValue - p.lo95);
            if (!Double.isNaN(p.parabolaHalfWidth95)) half = Math.max(half, p.parabolaHalfWidth95);
            half = Math.max(half * 1.6, 0.02 * Math.abs(p.bestValue));
            x.setRange(Math.max(vmin, p.bestValue - half), Math.min(vmax, p.bestValue + half));
            NumberAxis ny = new NumberAxis("Δχ² (total)");
            ny.setTickLabelFont(SMALL);
            ny.setRange(-0.5, 16);
            y = ny;
            if (!Double.isNaN(p.parabolaCentre)) {
                XYSeries par = new XYSeries("parabola (3 lowest points)");
                double A = 1 / (p.parabolaHalfWidth68 * p.parabolaHalfWidth68);
                for (int k = 0; k <= 100; k++) {
                    double f = x.getLowerBound() + k * (x.getUpperBound() - x.getLowerBound()) / 100;
                    double v = A * (f - p.parabolaCentre) * (f - p.parabolaCentre);
                    if (v <= 17) par.add(f, v);
                }
                c.addSeries(par);
                rend.setSeriesLinesVisible(2, true);
                rend.setSeriesShapesVisible(2, false);
                rend.setSeriesPaint(2, GOOD);
                rend.setSeriesStroke(2, new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{5, 3}, 0));
            }
        } else {
            x.setRange(vmin - 0.02 * (vmax - vmin), vmax + 0.02 * (vmax - vmin));
            LogAxis ly = logAxis("1 + Δχ² (total)");
            ly.setSmallestValue(0.5);
            ly.setRange(0.7, Math.max(100, maxD * 2));
            ly.setTickUnit(new NumberTickUnit(1, ly.getNumberFormatOverride()));
            y = ly;
        }
        XYPlot plot = new XYPlot(c, x, y, rend);
        style(plot);
        for (double lev : new double[]{1, 4}) {
            ValueMarker m = new ValueMarker(lev + off, MUTED, new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                    lev == 1 ? new float[]{5, 3} : new float[]{2, 2}, 0));
            m.setLabel("Δχ² = " + (int) lev);
            m.setLabelFont(SMALL);
            m.setLabelAnchor(org.jfree.ui.RectangleAnchor.TOP_LEFT);
            m.setLabelTextAnchor(org.jfree.ui.TextAnchor.BOTTOM_LEFT);
            plot.addRangeMarker(m);
        }
        if (p.hi68 > p.lo68) plot.addDomainMarker(new IntervalMarker(p.lo68, p.hi68, new Color(37, 99, 235, 40)), org.jfree.ui.Layer.BACKGROUND);
        plot.addDomainMarker(new ValueMarker(p.bestValue, ACCENT, new BasicStroke(1.2f)));
        String fmt = p.parameter.equals("nu") ? "%.3f" : "%.1f";
        String head = zoom
                ? (Double.isNaN(p.parabolaCentre) ? "Near the minimum" : String.format("Near the minimum: parabola " + fmt + " ± " + fmt, p.parabolaCentre, p.parabolaHalfWidth68))
                : String.format("Profile: %s = " + fmt + " (68 %%: " + fmt + "-" + fmt + ", 95 %%: " + fmt + "-" + fmt + ")",
                    name, p.bestValue, p.lo68, p.hi68, p.lo95, p.hi95);
        JFreeChart chart = new JFreeChart(head, TITLE, plot, true);
        String sub = zoom ? "shaded: 68 % interval; dashed green: parabola through the 3 lowest points"
                : String.format("distribution re-fitted at each value; optimiser noise sd %.2f%s", p.optimizerNoise,
                    p.optimizerNoise > 0.5 ? " (≈ Δχ² = 1: interval approximate)" : "");
        chart.addSubtitle(new TextTitle(sub, SMALL));
        finish(chart);
        return chart;
    }

    // ------------------------------------------------------------------ gel / network fits

    /** log-log data with the gel model, its components and the background, plus normalised residuals */
    public static JFreeChart gelFitChart(double[] q, double[] I, double[] s, version4.FormFactors.GelModels.Fit f, String title) {
        YIntervalSeries data = new YIntervalSeries("data");
        double ymin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < q.length; j++) {
            if (!(I[j] > 0)) continue;
            double lo = I[j] - s[j];
            if (lo <= 0) lo = I[j] * 0.1;
            data.add(q[j], I[j], lo, I[j] + s[j]);
            ymin = Math.min(ymin, I[j]);
        }
        YIntervalSeriesCollection dc = new YIntervalSeriesCollection();
        dc.addSeries(data);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setSeriesShape(0, new Ellipse2D.Double(-1.5, -1.5, 3, 3));
        err.setSeriesPaint(0, DATA);
        err.setErrorPaint(new Color(0xd5d9de));
        err.setDrawXError(false);
        err.setCapLength(0);

        XYSeriesCollection lines = new XYSeriesCollection();
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        XYSeries tot = new XYSeries(String.format("%s (red. χ² %.2f)", f.model.label, f.chi2Reduced), false, true);
        for (int j = 0; j < q.length; j++) if (f.curve[j] > 0) tot.add(q[j], f.curve[j]);
        lines.addSeries(tot);
        lr.setSeriesPaint(0, FIT);
        lr.setSeriesStroke(0, new BasicStroke(1.8f));
        java.util.List<String> comps = version4.FormFactors.GelModels.componentNames(f.model);
        double[] p = f.params();
        Color[] cc = {ACCENT, GOOD, PURPLE};
        for (int k = 0; k < comps.size(); k++) {
            XYSeries c = new XYSeries(comps.get(k), false, true);
            for (int j = 0; j < q.length; j++) {
                double v = version4.FormFactors.GelModels.component(f.model, k, p, q[j]);
                if (v > ymin * 0.02) c.add(q[j], v);
            }
            if (c.getItemCount() == 0) continue;
            lines.addSeries(c);
            int idx = lines.getSeriesCount() - 1;
            lr.setSeriesPaint(idx, cc[k % cc.length]);
            lr.setSeriesStroke(idx, new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 4}, 0));
        }
        if (f.background() > ymin * 0.02) {
            XYSeries bgs = new XYSeries("background", false, true);
            bgs.add(q[0], f.background());
            bgs.add(q[q.length - 1], f.background());
            lines.addSeries(bgs);
            int idx = lines.getSeriesCount() - 1;
            lr.setSeriesPaint(idx, MUTED);
            lr.setSeriesStroke(idx, new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2, 3}, 0));
        }
        LogAxis y = logAxis("I(q)");
        y.setSmallestValue(ymin > 0 && ymin < Double.POSITIVE_INFINITY ? ymin * 0.3 : 1e-6);
        XYPlot top = new XYPlot(lines, null, y, lr);
        top.setDataset(1, dc);
        top.setRenderer(1, err);
        top.setDatasetRenderingOrder(org.jfree.chart.plot.DatasetRenderingOrder.FORWARD);
        style(top);

        XYSeries res = new XYSeries("residuals", false, true);
        for (int j = 0; j < q.length; j++) res.add(q[j], (I[j] - f.curve[j]) / s[j]);
        XYLineAndShapeRenderer rr = new XYLineAndShapeRenderer(true, true);
        rr.setSeriesPaint(0, FIT);
        rr.setSeriesShape(0, new Ellipse2D.Double(-1.5, -1.5, 3, 3));
        rr.setSeriesStroke(0, new BasicStroke(0.8f));
        rr.setSeriesVisibleInLegend(0, false);
        NumberAxis ry = new NumberAxis("(I − fit)/σ");
        ry.setTickLabelFont(SMALL);
        XYPlot bottom = new XYPlot(new XYSeriesCollection(res), null, ry, rr);
        style(bottom);
        bottom.addRangeMarker(new IntervalMarker(-2, 2, new Color(0xd0d7de), new BasicStroke(0f), null, null, 0.35f), org.jfree.ui.Layer.BACKGROUND);
        bottom.addRangeMarker(new ValueMarker(0, MUTED, new BasicStroke(0.8f)));

        LogAxis x = logAxis("q (Å⁻¹)");
        x.setSmallestValue(1e-5);
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(x);
        plot.setGap(6);
        plot.add(top, 3);
        plot.add(bottom, 1);
        JFreeChart chart = new JFreeChart(title, TITLE, plot, true);
        finish(chart);
        return chart;
    }

    // ------------------------------------------------------------------ polymer analysis

    /** dimensionless Kratky plot with Gaussian-coil and compact-sphere references */
    public static JFreeChart kratkyChart(PolymerAnalysis.Result r) {
        YIntervalSeries data = new YIntervalSeries("data");
        double xmax = 0;
        for (int j = 0; j < r.kratkyX.length; j++) {
            double x = r.kratkyX[j];
            if (x > 10) break;
            data.add(x, r.kratkyY[j], r.kratkyY[j] - r.kratkyErr[j], r.kratkyY[j] + r.kratkyErr[j]);
            xmax = x;
        }
        YIntervalSeriesCollection dc = new YIntervalSeriesCollection();
        dc.addSeries(data);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setSeriesShape(0, new Ellipse2D.Double(-2, -2, 4, 4));
        err.setSeriesPaint(0, DATA);
        err.setErrorPaint(new Color(0xc4c9cf));
        err.setDrawXError(false);
        err.setCapLength(0);
        XYSeries deb = new XYSeries("Gaussian coil (Debye)"), sph = new XYSeries("compact sphere");
        for (int k = 1; k <= 400; k++) {
            double x = k * Math.max(xmax, 1) / 400;
            deb.add(x, PolymerAnalysis.kratkyDebye(x));
            sph.add(x, PolymerAnalysis.kratkySphere(x));
        }
        XYSeriesCollection refs = new XYSeriesCollection();
        refs.addSeries(deb);
        refs.addSeries(sph);
        XYLineAndShapeRenderer rr = new XYLineAndShapeRenderer(true, false);
        rr.setSeriesPaint(0, FIT);
        rr.setSeriesPaint(1, ACCENT);
        rr.setSeriesStroke(0, new BasicStroke(1.4f));
        rr.setSeriesStroke(1, new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 4}, 0));
        NumberAxis x = new NumberAxis("q Rg");
        x.setTickLabelFont(SMALL);
        x.setRange(0, Math.max(xmax, 1) * 1.02);
        NumberAxis y = new NumberAxis("(q Rg)² I(q) / I(0)");
        y.setTickLabelFont(SMALL);
        y.setAutoRangeIncludesZero(true);
        XYPlot plot = new XYPlot(refs, x, y, rr);
        plot.setDataset(1, dc);
        plot.setRenderer(1, err);
        style(plot);
        plot.addDomainMarker(new ValueMarker(Math.sqrt(3), GRID.darker(), new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3, 3}, 0)));
        JFreeChart c = new JFreeChart("Dimensionless Kratky", TITLE, plot, true);
        c.addSubtitle(new TextTitle(String.format("Guinier Rg %.1f Å, I(0) %.4g; compact: peak 1.10 at √3, coil: plateau → 2",
                r.guinier.ok ? r.guinier.values[1] : Double.NaN, r.guinier.ok ? r.guinier.values[0] : Double.NaN), SMALL));
        finish(c);
        return c;
    }

    /** log-log data with the single-chain fits and the high-q power law */
    public static JFreeChart polymerFitChart(PolymerAnalysis.Result r) {
        YIntervalSeries data = new YIntervalSeries("data");
        double ymin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < r.q.length; j++) {
            if (!(r.I[j] > 0)) continue;
            double lo = r.I[j] - r.sigma[j];
            if (lo <= 0) lo = r.I[j] * 0.1;
            data.add(r.q[j], r.I[j], lo, r.I[j] + r.sigma[j]);
            ymin = Math.min(ymin, r.I[j]);
        }
        YIntervalSeriesCollection dc = new YIntervalSeriesCollection();
        dc.addSeries(data);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setSeriesShape(0, new Ellipse2D.Double(-1.5, -1.5, 3, 3));
        err.setSeriesPaint(0, DATA);
        err.setErrorPaint(new Color(0xd5d9de));
        err.setDrawXError(false);
        err.setCapLength(0);
        XYSeriesCollection fits = new XYSeriesCollection();
        XYLineAndShapeRenderer fr = new XYLineAndShapeRenderer(true, false);
        PolymerAnalysis.Fit[] fs = {r.excludedVolume, r.wormlike, r.debye, r.powerLaw};
        String[] labels = {"excluded-volume chain", "wormlike chain", "Debye (q Rg ≤ 3)", "power law"};
        Color[] cols = {FIT, GOOD, PURPLE, ACCENT};
        for (int k = 0; k < fs.length; k++) {
            PolymerAnalysis.Fit f = fs[k];
            if (f == null || !f.ok || f.curve == null) continue;
            String lab = labels[k] + (k == 3 ? String.format(" (ν_app %.3f)", f.values[1])
                    : String.format(" (red. χ² %.2f)", f.chi2Reduced)) + (f.poor ? " – poor" : "");
            XYSeries sr = new XYSeries(lab, false, true);
            for (int j = 0; j < r.q.length; j++) if (f.curve[j] > ymin * 0.05) sr.add(r.q[j], f.curve[j]);
            if (sr.getItemCount() == 0) continue;
            fits.addSeries(sr);
            int idx = fits.getSeriesCount() - 1;
            fr.setSeriesPaint(idx, cols[k]);
            fr.setSeriesStroke(idx, k >= 2 ? new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 4}, 0)
                    : new BasicStroke(f.poor ? 1.0f : 1.7f));
        }
        LogAxis x = logAxis("q (Å⁻¹)");
        x.setSmallestValue(1e-5);
        LogAxis y = logAxis("I(q)");
        y.setSmallestValue(ymin > 0 && ymin < Double.POSITIVE_INFINITY ? ymin * 0.3 : 1e-6);
        XYPlot plot = new XYPlot(fits, x, y, fr);
        plot.setDataset(1, dc);
        plot.setRenderer(1, err);
        plot.setDatasetRenderingOrder(org.jfree.chart.plot.DatasetRenderingOrder.FORWARD);
        style(plot);
        JFreeChart c = new JFreeChart("Single-chain fits", TITLE, plot, true);
        if (r.excludedVolume.ok && !r.excludedVolume.poor)
            c.addSubtitle(new TextTitle(String.format("excluded volume: Rg %.1f Å, ν %.3f ± %.3f", r.excludedVolume.values[1],
                    r.excludedVolume.values[2], r.excludedVolume.errors[2]), SMALL));
        if (r.wormlike.ok && !r.wormlike.poor)
            c.addSubtitle(new TextTitle(String.format("wormlike: L %.0f Å, b %.1f Å (lp %.1f Å)", r.wormlike.values[1],
                    r.wormlike.values[2], r.wormlike.values[2] / 2), SMALL));
        finish(c);
        return c;
    }

    /** Guinier plot ln I vs q^2 over q Rg <= 2 with the fitted range highlighted */
    public static JFreeChart guinierChart(PolymerAnalysis.Result r) {
        double rg = r.guinier.ok ? r.guinier.values[1] : 1 / r.q[Math.min(5, r.q.length - 1)];
        YIntervalSeries in = new YIntervalSeries("fitted points"), out = new YIntervalSeries("other points");
        for (int j = 0; j < r.q.length; j++) {
            if (!(r.I[j] > 0) || r.q[j] * rg > 2) continue;
            double y = Math.log(r.I[j]), e = r.sigma[j] / r.I[j];
            (r.guinier.ok && j < r.guinier.to ? in : out).add(r.q[j] * r.q[j], y, y - e, y + e);
        }
        YIntervalSeriesCollection dc = new YIntervalSeriesCollection();
        dc.addSeries(in);
        dc.addSeries(out);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setSeriesShape(0, new Ellipse2D.Double(-2, -2, 4, 4));
        err.setSeriesShape(1, new Ellipse2D.Double(-2, -2, 4, 4));
        err.setSeriesPaint(0, FIT);
        err.setSeriesPaint(1, DATA);
        err.setErrorPaint(new Color(0xc4c9cf));
        err.setDrawXError(false);
        err.setCapLength(0);
        XYSeriesCollection line = new XYSeriesCollection();
        if (r.guinier.ok) {
            XYSeries l = new XYSeries(String.format("Rg %.2f ± %.2f Å", rg, r.guinier.errors[1]));
            double i0 = r.guinier.values[0], qm = 2 / rg;
            l.add(0, Math.log(i0));
            l.add(qm * qm, Math.log(i0) - qm * qm * rg * rg / 3);
            line.addSeries(l);
        }
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        lr.setSeriesPaint(0, ACCENT);
        NumberAxis x = new NumberAxis("q² (Å⁻²)");
        x.setTickLabelFont(SMALL);
        x.setAutoRangeIncludesZero(true);
        NumberAxis y = new NumberAxis("ln I(q)");
        y.setTickLabelFont(SMALL);
        y.setAutoRangeIncludesZero(false);
        XYPlot plot = new XYPlot(dc, x, y, err);
        plot.setDataset(1, line);
        plot.setRenderer(1, lr);
        style(plot);
        JFreeChart c = new JFreeChart("Guinier (q Rg ≤ 1)", TITLE, plot, true);
        finish(c);
        return c;
    }

    // ------------------------------------------------------------------ styling

    static void style(XYPlot p) {
        p.setBackgroundPaint(Color.WHITE);
        p.setDomainGridlinePaint(GRID);
        p.setRangeGridlinePaint(GRID);
        p.setOutlineVisible(false);
        p.setAxisOffset(new RectangleInsets(2, 2, 2, 2));
    }

    static void finish(JFreeChart c) {
        c.setBackgroundPaint(Color.WHITE);
        c.setAntiAlias(true);
        if (c.getLegend() != null) {
            c.getLegend().setFrame(BlockBorder.NONE);
            c.getLegend().setItemFont(SMALL);
        }
        for (Object o : c.getSubtitles()) if (o instanceof TextTitle) ((TextTitle) o).setPaint(MUTED);
    }

    /** helper used by the panel's empty state */
    static JFreeChart placeholder(String text) {
        XYPlot p = new XYPlot(new XYSeriesCollection(), new NumberAxis(), new NumberAxis(), new XYLineAndShapeRenderer());
        p.setNoDataMessage(text);
        p.setNoDataMessageFont(new Font("SansSerif", Font.PLAIN, 13));
        p.setNoDataMessagePaint(MUTED);
        p.getDomainAxis().setVisible(false);
        p.getRangeAxis().setVisible(false);
        style(p);
        p.setDomainGridlinesVisible(false);
        p.setRangeGridlinesVisible(false);
        JFreeChart c = new JFreeChart(null, null, p, false);
        c.setBackgroundPaint(Color.WHITE);
        return c;
    }
}
