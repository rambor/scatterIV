package version4.FormFactors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYErrorRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.jfree.ui.Layer;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;

import java.awt.*;
import java.awt.geom.Ellipse2D;

import static version4.FormFactors.FormFactorCharts.*;

/** Charts for the LNP panel, in the style of {@link FormFactorCharts}. */
final class LnpCharts {

    private LnpCharts() { }

    private static final Stroke DASH = new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 4}, 0);
    private static final Stroke DOT = new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2, 3}, 0);

    /** data with error bars on a log-log plot; returns {plot, ymin} */
    /**
     * Plot weighting. Multiplying by a power of q flattens the falling background so that peaks stand out against
     * it: over a limited range the diffuse scattering of these samples behaves like a power law, and q·I(q) or
     * q²·I(q) removes most of its slope. q²·I(q) is the Lorentz correction for a lamellar stack and q·I(q) the one
     * for cylinders, so the weighted curve is also the right thing to integrate a reflection on.
     */
    /**
     * A chart panel with the conveniences every plot in this panel should have: the wheel zooms, and a double
     * click puts both axes back to the data. JFreeChart binds neither by default, so without this a zoomed plot
     * can only be reset through the context menu, which nobody finds.
     */
    static ChartPanel panelFor(JFreeChart chart) {
        ChartPanel cp = new ChartPanel(chart);
        cp.setMouseWheelEnabled(true);
        cp.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && !javax.swing.SwingUtilities.isRightMouseButton(e)) cp.restoreAutoBounds();
            }
        });
        cp.setToolTipText("Wheel to zoom, double click to fit the data again");
        return cp;
    }

    enum Weight {
        NONE("I(q)", 0), Q("q·I(q)", 1), Q2("q²·I(q)", 2);
        final String label;
        final int power;
        Weight(String label, int power) { this.label = label; this.power = power; }
        double apply(double q, double v) { return power == 0 ? v : v * Math.pow(q, power); }
        /** a flattened baseline reads better on a linear axis, which is the point of weighting it */
        boolean linearY() { return power > 0; }
        @Override public String toString() { return label; }
    }

    private static XYPlot dataPlot(double[] q, double[] I, double[] s, double[] yminOut) {
        return dataPlot(q, I, s, yminOut, Weight.NONE);
    }

    private static XYPlot dataPlot(double[] q, double[] I, double[] s, double[] yminOut, Weight w) {
        YIntervalSeries data = new YIntervalSeries("data");
        double ymin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < q.length; j++) {
            if (!(I[j] > 0)) continue;
            double v = w.apply(q[j], I[j]), e = w.apply(q[j], s[j]);
            double lo = v - e;
            if (lo <= 0) lo = v * 0.1;
            data.add(q[j], v, lo, v + e);
            ymin = Math.min(ymin, v);
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
        ValueAxis y;
        if (w.linearY()) {
            NumberAxis lin = new NumberAxis(w.label);
            lin.setAutoRangeIncludesZero(true);
            y = lin;
        } else {
            org.jfree.chart.axis.LogAxis log = logAxis(w.label);
            log.setSmallestValue(ymin > 0 && ymin < Double.POSITIVE_INFINITY ? ymin * 0.3 : 1e-6);
            y = log;
        }
        XYPlot plot = new XYPlot(null, null, y, null);
        plot.setDataset(1, dc);
        plot.setRenderer(1, err);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        style(plot);
        yminOut[0] = ymin;
        return plot;
    }

    /** log I(q) with the model, its components and a residual panel */
    static JFreeChart fitChart(LnpFitter.Result r, String title) {
        double[] ymin = new double[1];
        XYPlot top = dataPlot(r.q, r.I, r.sigma, ymin);
        XYSeriesCollection lines = new XYSeriesCollection();
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        XYSeries tot = new XYSeries(String.format("model (red. χ² %.2f)", r.chi2Reduced), false, true);
        for (int j = 0; j < r.q.length; j++) if (r.fit[j] > 0) tot.add(r.q[j], r.fit[j]);
        lines.addSeries(tot);
        lr.setSeriesPaint(0, FIT);
        lr.setSeriesStroke(0, new BasicStroke(1.8f));

        double[][] comp = r.model.components(r.params, r.q);
        String[] names = {r.model.shells == 0 ? "sphere" : "core + " + r.model.shells + " shell" + (r.model.shells > 1 ? "s" : ""),
                "mesophase peak", "background"};
        Color[] cols = {ACCENT, PURPLE, MUTED};
        if (r.model.peak || r.params[LnpModel.BG] > ymin[0] * 0.02) {
            for (int c = 0; c < 3; c++) {
                if (comp[c] == null) continue;
                XYSeries sr = new XYSeries(names[c], false, true);
                for (int j = 0; j < r.q.length; j++) if (comp[c][j] > ymin[0] * 0.02) sr.add(r.q[j], comp[c][j]);
                if (sr.getItemCount() == 0) continue;
                lines.addSeries(sr);
                int idx = lines.getSeriesCount() - 1;
                lr.setSeriesPaint(idx, cols[c]);
                lr.setSeriesStroke(idx, c == 2 ? DOT : DASH);
            }
        }
        top.setDataset(0, lines);
        top.setRenderer(0, lr);

        XYSeries res = new XYSeries("residuals", false, true);
        for (int j = 0; j < r.q.length; j++) res.add(r.q[j], (r.I[j] - r.fit[j]) / r.sigma[j]);
        XYLineAndShapeRenderer rr = new XYLineAndShapeRenderer(true, true);
        rr.setSeriesPaint(0, FIT);
        rr.setSeriesShape(0, new Ellipse2D.Double(-1.5, -1.5, 3, 3));
        rr.setSeriesStroke(0, new BasicStroke(0.8f));
        rr.setSeriesVisibleInLegend(0, false);
        NumberAxis ry = new NumberAxis("(I − model)/σ");
        ry.setTickLabelFont(SMALL);
        XYPlot bottom = new XYPlot(new XYSeriesCollection(res), null, ry, rr);
        style(bottom);
        bottom.addRangeMarker(new IntervalMarker(-2, 2, new Color(0xd0d7de), new BasicStroke(0f), null, null, 0.35f), Layer.BACKGROUND);
        bottom.addRangeMarker(new ValueMarker(0, MUTED, new BasicStroke(0.8f)));

        org.jfree.chart.axis.LogAxis x = logAxis("q (Å⁻¹)");
        x.setSmallestValue(1e-5);
        CombinedDomainXYPlot plot = new CombinedDomainXYPlot(x);
        plot.setGap(6);
        plot.add(top, 3);
        plot.add(bottom, 1);
        JFreeChart chart = new JFreeChart(title, TITLE, plot, true);
        finish(chart);
        return chart;
    }

    /** data, peak-search baseline and detected peaks / oscillations */
    static JFreeChart assessmentChart(double[] q, double[] I, double[] s, LnpAssessment.Report rep, String title) {
        return assessmentChart(q, I, s, rep, title, Weight.NONE);
    }

    static JFreeChart assessmentChart(double[] q, double[] I, double[] s, LnpAssessment.Report rep, String title, Weight w) {
        double[] ymin = new double[1];
        XYPlot plot = dataPlot(q, I, s, ymin, w);
        XYSeriesCollection lines = new XYSeriesCollection();
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        if (rep.baselineQ != null && rep.baselineQ.length > 0) {
            XYSeries b = new XYSeries("smooth baseline", false, true);
            for (int i = 0; i < rep.baselineQ.length; i++) b.add(rep.baselineQ[i], w.apply(rep.baselineQ[i], rep.baseline[i]));
            lines.addSeries(b);
            lr.setSeriesPaint(0, ACCENT);
            lr.setSeriesStroke(0, DASH);
        }
        if (rep.guinier != null && rep.guinier.found) {
            XYSeries g = new XYSeries(String.format("Guinier Rg %.0f Å", rep.guinier.rg), false, true);
            for (int j = 0; j < q.length && q[j] <= rep.guinier.qmax * 1.15; j++)
                g.add(q[j], w.apply(q[j], rep.guinier.i0 * Math.exp(-q[j] * q[j] * rep.guinier.rg * rep.guinier.rg / 3)));
            lines.addSeries(g);
            int idx = lines.getSeriesCount() - 1;
            lr.setSeriesPaint(idx, GOOD);
            lr.setSeriesStroke(idx, new BasicStroke(1.6f));
        }
        plot.setDataset(0, lines);
        plot.setRenderer(0, lr);
        plot.addDomainMarker(new ValueMarker(rep.searchFrom, new Color(0xc0c7cf), DOT));
        String[] idx = rep.phases.isEmpty() ? null : rep.phases.get(0).indices;
        // Labels are tiered down from the top of the plot, because peaks close together would otherwise print on
        // top of each other and neither could be read. The unit is in the axis title and the "d" in a label that
        // sits on a peak says nothing, so the number carries it alone.
        double lastQ = Double.NEGATIVE_INFINITY;
        double span = q.length > 1 ? q[q.length - 1] - q[0] : 1;
        int tier = 0;
        for (int i = 0; i < rep.peaks.size(); i++) {
            LnpAssessment.Peak p = rep.peaks.get(i);
            ValueMarker m = new ValueMarker(p.q, FIT, new BasicStroke(1.2f));
            String label = idx != null && idx[i] != null ? idx[i] : String.format("%.1f nm", p.d() / 10);
            m.setLabel(label);
            m.setLabelFont(SMALL);
            m.setLabelPaint(FIT);
            m.setLabelAnchor(RectangleAnchor.TOP_LEFT);
            m.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
            // A new tier whenever this peak is close enough to the last that the labels would collide. The gap
            // that matters is the one on screen, so it scales with the plotted range rather than with q; the
            // ratio test catches crowding at the low-q end, where a log axis compresses the spacing.
            boolean crowded = p.q - lastQ < 0.05 * span || p.q / lastQ < 1.2;
            tier = crowded ? (tier + 1) % 4 : 0;
            lastQ = p.q;
            m.setLabelOffset(new org.jfree.ui.RectangleInsets(3 + 13 * tier, 4, 3, 4));
            plot.addDomainMarker(m);
        }
        for (LnpAssessment.Peak p : rep.oscillations)
            plot.addDomainMarker(new ValueMarker(p.q, new Color(0xf0b37e), DOT));
        if (w.linearY()) {
            NumberAxis xl = new NumberAxis("q (Å⁻¹)");
            xl.setAutoRangeIncludesZero(false);
            plot.setDomainAxis(xl);
        } else {
            org.jfree.chart.axis.LogAxis x = logAxis("q (Å⁻¹)");
            x.setSmallestValue(1e-5);
            plot.setDomainAxis(x);
        }
        JFreeChart chart = new JFreeChart(title, TITLE, plot, true);
        finish(chart);
        return chart;
    }

    /** radial electron-density contrast of the mean particle */
    static JFreeChart densityChart(LnpModel model, double[] p) {
        double[][] prof = model.densityProfile(p);
        XYSeries s = new XYSeries("Δρ(r)", false, true);
        for (int i = 0; i < prof[0].length; i++) s.add(prof[0][i], prof[1][i]);
        XYStepRenderer rend = new XYStepRenderer();
        rend.setSeriesPaint(0, FIT);
        rend.setSeriesStroke(0, new BasicStroke(2f));
        NumberAxis x = new NumberAxis("r (Å)");
        x.setTickLabelFont(SMALL);
        NumberAxis y = new NumberAxis("Δρ vs buffer (e/Å³)");
        y.setTickLabelFont(SMALL);
        y.setUpperMargin(0.15);
        y.setLowerMargin(0.15);
        XYPlot plot = new XYPlot(new XYSeriesCollection(s), x, y, rend);
        style(plot);
        plot.addRangeMarker(new ValueMarker(0, MUTED, new BasicStroke(0.8f)));
        // labels for the layers
        String[] names = model.isVesicle() ? new String[]{"water", "heads", "chains", "heads", "PEG"} : new String[]{"core", "lipid", "head", "PEG"};
        double[] r = model.radii(p, p[LnpModel.R0]);
        double lo = 0;
        double ymax = 0, ymin = 0;
        for (double v : prof[1]) { ymax = Math.max(ymax, v); ymin = Math.min(ymin, v); }
        for (int k = 0; k <= model.shells; k++) {
            double mid = 0.5 * (lo + r[k]);
            double val = p[LnpModel.rhoIndex(k)];
            XYTextAnnotation a = new XYTextAnnotation(names[k], mid, val + (val >= 0 ? 0.06 : -0.08) * (ymax - ymin + 1e-9));
            a.setFont(SMALL);
            a.setPaint(MUTED);
            plot.addAnnotation(a);
            lo = r[k];
        }
        JFreeChart chart = new JFreeChart("Radial density profile (mean particle)", TITLE, plot, false);
        finish(chart);
        return chart;
    }

    /** number-weighted outer-diameter histogram per morphology class, stacked (2 nm bins), as for cryo-EM classes */
    static JFreeChart populationChart(LnpPopulations.Result r) {
        org.jfree.data.xy.DefaultTableXYDataset ds = new org.jfree.data.xy.DefaultTableXYDataset();
        double dmax = 0;
        for (LnpPopulations.Component c : r.components) if (!Double.isNaN(c.diameter)) dmax = Math.max(dmax, c.diameter * (1 + 3 * c.sigmaRel));
        double bin = 2;
        int nb = (int) Math.ceil(dmax / bin) + 2;
        org.jfree.chart.renderer.xy.StackedXYBarRenderer rend = new org.jfree.chart.renderer.xy.StackedXYBarRenderer(0.08);
        rend.setShadowVisible(false);
        rend.setBarPainter(new org.jfree.chart.renderer.xy.StandardXYBarPainter());
        int si = 0;
        for (LnpPopulations.Component c : r.components) {
            if (c.kind == LnpPopulations.Kind.AGGREGATE || c.kind == LnpPopulations.Kind.FREE_RNA || Double.isNaN(c.numberFraction)) continue;
            XYSeries s = new XYSeries(String.format("%s (%.0f %%)", c.kind.label, 100 * c.numberFraction), false, false);
            double sd = Math.max(0.5, c.sigmaRel * c.diameter);
            for (int b = 0; b < nb; b++) {
                double lo = b * bin, hi = lo + bin;
                double p = 0.5 * (erf((hi - c.diameter) / (sd * Math.sqrt(2))) - erf((lo - c.diameter) / (sd * Math.sqrt(2))));
                s.add(lo + bin / 2, 100 * c.numberFraction * p);
            }
            ds.addSeries(s);
            rend.setSeriesPaint(si++, c.kind.color);
        }
        NumberAxis x = new NumberAxis("particle diameter (nm)");
        x.setTickLabelFont(SMALL);
        NumberAxis y = new NumberAxis("% of particles per 2 nm");
        y.setTickLabelFont(SMALL);
        XYPlot plot = new XYPlot(ds, x, y, rend);
        style(plot);
        JFreeChart chart = new JFreeChart("Morphology populations from SAXS (number-weighted)", TITLE, plot, true);
        finish(chart);
        return chart;
    }

    /** data in the window with the fitted background, the phase Lorentzians and their sum */
    static JFreeChart phaseChart(LnpPhasePeaks.Result r, String title) {
        XYSeriesCollection pts = new XYSeriesCollection();
        XYSeries data = new XYSeries("data", false, true);
        for (int i = 0; i < r.q.length; i++) data.add(r.q[i], r.signal[i]);
        pts.addSeries(data);
        XYLineAndShapeRenderer pr = new XYLineAndShapeRenderer(false, true);
        pr.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-2, -2, 4, 4));
        pr.setSeriesPaint(0, DATA);
        XYSeriesCollection lines = new XYSeriesCollection();
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        int k = 0;
        XYSeries tot = new XYSeries(String.format("fit (red. χ² %.2f)", r.chi2Reduced), false, true);
        for (int i = 0; i < r.q.length; i++) tot.add(r.q[i], r.fit[i]);
        lines.addSeries(tot);
        lr.setSeriesPaint(k, FIT);
        lr.setSeriesStroke(k++, new BasicStroke(2f));
        if (r.baselineSubtracted) {
            XYSeries b = new XYSeries("background", false, true);
            for (int i = 0; i < r.q.length; i++) b.add(r.q[i], r.baseline[i]);
            lines.addSeries(b);
            lr.setSeriesPaint(k, MUTED);
            lr.setSeriesStroke(k++, DOT);
        }
        for (LnpPhasePeaks.Component c : r.components) {
            XYSeries s = new XYSeries(String.format("%s  d %.0f Å  (%.0f %%)", c.name, c.d(), 100 * r.share(c)), false, true);
            for (int i = 0; i < r.q.length; i++)
                s.add(r.q[i], (r.baselineSubtracted ? r.baseline[i] : 0) + LnpPhasePeaks.lorentz(r.q[i], c.qc, c.width, c.area));
            lines.addSeries(s);
            lr.setSeriesPaint(k, c.color);
            lr.setSeriesStroke(k++, DASH);
        }
        NumberAxis x = new NumberAxis("q (Å⁻¹)");
        x.setTickLabelFont(SMALL);
        x.setAutoRangeIncludesZero(false);
        NumberAxis y = new NumberAxis("I(q)");
        y.setTickLabelFont(SMALL);
        y.setAutoRangeIncludesZero(false);
        XYPlot plot = new XYPlot(lines, x, y, lr);
        plot.setDataset(1, pts);
        plot.setRenderer(1, pr);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        style(plot);
        double ratio = r.disorderedToHII();
        JFreeChart chart = new JFreeChart(Double.isNaN(ratio) ? "Phase decomposition – " + title
                : String.format("Phase decomposition – %s   (disordered/HII = %.2f)", title, ratio), TITLE, plot, true);
        finish(chart);
        return chart;
    }

    /** lattice fit: data, total fit, diffuse background and the indexed reflections */
    /**
     * The chain-packing region, with what was measured marked on it. A width and a spacing in a table are hard to
     * judge; seen against the reference spacings for melted, ordered and sterol-stiffened chains, and with the
     * measured peak drawn over the data, the reading can be checked by eye.
     */
    static JFreeChart waxsChart(LnpWaxs.Result r, double[] q, double[] I, double[] s, String title) {
        YIntervalSeries data = new YIntervalSeries("data");
        double lo = Double.POSITIVE_INFINITY, hi = -Double.MAX_VALUE;
        for (int i = 0; i < q.length; i++) {
            if (q[i] < LnpWaxs.Q_LOW - 0.15 || q[i] > Math.min(LnpWaxs.Q_HIGH + 0.15, r.qmax)) continue;
            data.add(q[i], I[i], I[i] - s[i], I[i] + s[i]);
            lo = Math.min(lo, I[i] - s[i]);
            hi = Math.max(hi, I[i] + s[i]);
        }
        YIntervalSeriesCollection dc = new YIntervalSeriesCollection();
        dc.addSeries(data);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setSeriesShape(0, new Ellipse2D.Double(-1.4, -1.4, 2.8, 2.8));
        err.setSeriesPaint(0, DATA);
        err.setErrorPaint(new Color(0xe1e5ea));
        err.setDrawXError(false);
        err.setCapLength(0);

        NumberAxis x = new NumberAxis("q (Å⁻¹)"), y = new NumberAxis("I(q)");
        x.setAutoRangeIncludesZero(false);
        y.setAutoRangeIncludesZero(false);
        XYPlot plot = new XYPlot(dc, x, y, err);
        style(plot);

        // the measured peak, drawn as it was measured: a Gaussian of the fitted width on its local background
        XYSeriesCollection fits = new XYSeriesCollection();
        for (int k = 0; k < r.peaks.size(); k++) {
            LnpWaxs.Peak p = r.peaks.get(k);
            XYSeries c = new XYSeries(k == 0 ? String.format("measured peak, d = %.2f Å", p.d) : "second line", false, true);
            for (double qq = p.q - 3 * p.fwhm; qq <= p.q + 3 * p.fwhm; qq += p.fwhm / 40) c.add(qq, p.curveAt(qq));
            fits.addSeries(c);
        }
        XYLineAndShapeRenderer fr = new XYLineAndShapeRenderer(true, false);
        for (int k = 0; k < fits.getSeriesCount(); k++) {
            fr.setSeriesPaint(k, k == 0 ? FIT : PURPLE);
            fr.setSeriesStroke(k, new BasicStroke(k == 0 ? 2f : 1.4f));
        }
        plot.setDataset(1, fits);
        plot.setRenderer(1, fr);

        // where each state of the chains would put its peak
        for (Object[] ref : LnpWaxs.REFERENCES) {
            double qr = 2 * Math.PI / (Double) ref[0];
            ValueMarker m = new ValueMarker(qr, new Color(0x8a94a0),
                    new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3, 4}, 0));
            m.setLabel(String.format("%.1f Å  %s", (Double) ref[0], ref[1]));
            m.setLabelFont(SMALL);
            m.setLabelPaint(new Color(0x6b7480));
            m.setLabelAnchor(RectangleAnchor.TOP);
            m.setLabelTextAnchor(TextAnchor.TOP_LEFT);
            m.setLabelOffset(new org.jfree.ui.RectangleInsets(3 + 13 * refRow(ref), 4, 3, 4));
            plot.addDomainMarker(m);
        }

        // the part of the curve the subtraction has ruined
        if (r.usableTo < r.qmax) {
            IntervalMarker bad = new IntervalMarker(r.usableTo, Math.min(LnpWaxs.Q_HIGH + 0.15, r.qmax));
            bad.setPaint(new Color(0xb42318));
            bad.setAlpha(0.06f);
            plot.addDomainMarker(bad, org.jfree.ui.Layer.BACKGROUND);
            // the band is drawn faint so the data stays readable through it, and a marker label takes that same
            // transparency, so the caption is added separately to keep it legible
            double mid = 0.5 * (r.usableTo + Math.min(LnpWaxs.Q_HIGH + 0.15, r.qmax));
            XYTextAnnotation caption = new XYTextAnnotation("over-subtracted", mid, lo + 0.04 * (hi - lo));
            caption.setFont(SMALL);
            caption.setPaint(new Color(0xb42318));
            plot.addAnnotation(caption);
        }

        LnpWaxs.Peak main = r.main();
        String verdict = main == null ? "no chain-packing peak"
                : String.format("d = %.2f Å, FWHM %.3f Å⁻¹ — %s", main.d, main.fwhm, describePacking(r.packing));
        JFreeChart chart = new JFreeChart(title + " — chain packing:  " + verdict, TITLE, plot, true);
        finish(chart);
        return chart;
    }

    private static int refRow(Object[] ref) {
        for (int i = 0; i < LnpWaxs.REFERENCES.length; i++) if (LnpWaxs.REFERENCES[i] == ref) return i;
        return 0;
    }

    private static String describePacking(LnpWaxs.Packing p) {
        switch (p) {
            case GEL: return "ordered chains (gel)";
            case ORDERED_SHARP: return "ordered chains on a distorted lattice";
            case FLUID: return "melted chains (fluid)";
            case LIQUID_ORDERED: return "liquid-ordered";
            default: return "not assigned";
        }
    }

    /**
     * The sponge-phase fit: one broad correlation peak and no higher orders. The quasi-periodic repeat is marked at
     * 2 pi / d, and the correlation length is quoted in the title, since it is what distinguishes a sponge from the
     * ordered cubic phase it sits next to on a phase diagram.
     */
    static JFreeChart spongeChart(LnpLattice.Sponge sp, String title) {
        YIntervalSeries data = new YIntervalSeries("data");
        for (int i = 0; i < sp.q.length; i++) {
            if (!(sp.I[i] > 0)) continue;
            data.add(sp.q[i], sp.I[i], Math.max(sp.I[i] - sp.sigma[i], sp.I[i] * 0.1), sp.I[i] + sp.sigma[i]);
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
        XYSeries fit = new XYSeries(String.format("Teubner–Strey (red. χ² %.2f)", sp.chi2Reduced), false, true);
        for (int i = 0; i < sp.q.length; i++) fit.add(sp.q[i], sp.fit[i]);
        XYSeriesCollection lines = new XYSeriesCollection();
        lines.addSeries(fit);
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        lr.setSeriesPaint(0, FIT);
        lr.setSeriesStroke(0, new BasicStroke(1.8f));
        XYPlot plot = new XYPlot(dc, logAxis("q (Å⁻¹)"), logAxis("I(q)"), err);
        plot.setDataset(1, lines);
        plot.setRenderer(1, lr);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        style(plot);
        if (sp.d > 0) {
            ValueMarker m = new ValueMarker(2 * Math.PI / sp.d, PURPLE,
                    new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 4}, 0));
            m.setLabel(String.format("2π/d = %.3f Å⁻¹", 2 * Math.PI / sp.d));
            m.setLabelFont(SMALL);
            m.setLabelPaint(PURPLE);
            m.setLabelAnchor(RectangleAnchor.TOP);
            m.setLabelTextAnchor(TextAnchor.TOP_LEFT);
            plot.addDomainMarker(m);
        }
        JFreeChart chart = new JFreeChart(String.format("Sponge phase L3   d = %.1f Å   ξ = %.1f Å – %s", sp.d, sp.xi, title),
                TITLE, plot, true);
        finish(chart);
        return chart;
    }

    static JFreeChart latticeChart(LnpLattice.Result r, String title) {
        return latticeChart(r, title, Weight.NONE);
    }

    static JFreeChart latticeChart(LnpLattice.Result r, String title, Weight w) {
        YIntervalSeries data = new YIntervalSeries("data");
        double ymin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < r.q.length; i++) {
            if (!(r.I[i] > 0)) continue;
            double v = w.apply(r.q[i], r.I[i]), e = w.apply(r.q[i], r.sigma[i]);
            double lo = Math.max(v - e, v * 0.1);
            data.add(r.q[i], v, lo, v + e);
            ymin = Math.min(ymin, v);
        }
        YIntervalSeriesCollection dc = new YIntervalSeriesCollection();
        dc.addSeries(data);
        XYErrorRenderer err = new XYErrorRenderer();
        err.setBaseLinesVisible(false);
        err.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-1.5, -1.5, 3, 3));
        err.setSeriesPaint(0, DATA);
        err.setErrorPaint(new Color(0xd5d9de));
        err.setDrawXError(false);
        err.setCapLength(0);
        XYSeriesCollection lines = new XYSeriesCollection();
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        XYSeries fit = new XYSeries(String.format("%s fit (red. χ² %.2f)", r.lattice.symmetry, r.chi2Reduced), false, true);
        XYSeries bg = new XYSeries("diffuse background", false, true);
        for (int i = 0; i < r.q.length; i++) {
            fit.add(r.q[i], w.apply(r.q[i], r.fit[i]));
            bg.add(r.q[i], w.apply(r.q[i], r.background[i]));
        }
        lines.addSeries(fit);
        lines.addSeries(bg);
        lr.setSeriesPaint(0, FIT);
        lr.setSeriesStroke(0, new BasicStroke(1.8f));
        lr.setSeriesPaint(1, MUTED);
        lr.setSeriesStroke(1, DOT);
        if (r.dopantCurve != null && r.dopantScale > 0) {
            XYSeries dop = new XYSeries(String.format("dopant (%.1f nm)", 2 * r.dopantR / 10), false, true);
            for (int i = 0; i < r.q.length; i++) dop.add(r.q[i], w.apply(r.q[i], r.dopantCurve[i]));
            lines.addSeries(dop);
            lr.setSeriesPaint(2, PURPLE);
            lr.setSeriesStroke(2, DASH);
        }
        org.jfree.chart.axis.LogAxis y = logAxis("I(q)");
        y.setSmallestValue(ymin > 0 && ymin < Double.POSITIVE_INFINITY ? ymin * 0.5 : 1e-6);
        org.jfree.chart.axis.LogAxis x = logAxis("q (Å⁻¹)");
        x.setSmallestValue(1e-5);
        XYPlot plot = new XYPlot(lines, x, y, lr);
        plot.setDataset(1, dc);
        plot.setRenderer(1, err);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        style(plot);
        for (LnpLattice.Reflection rf : r.reflections) {
            if (!rf.inRange || !(rf.area > 0)) continue;
            ValueMarker m = new ValueMarker(rf.q, new Color(0xdc2626), new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 4}, 0));
            m.setLabel(rf.hkl);
            m.setLabelFont(SMALL);
            m.setLabelPaint(new Color(0xdc2626));
            m.setLabelAnchor(org.jfree.ui.RectangleAnchor.TOP);
            m.setLabelTextAnchor(org.jfree.ui.TextAnchor.TOP_LEFT);
            plot.addDomainMarker(m);
        }
        if (!Double.isNaN(r.extraQ)) {
            ValueMarker m = new ValueMarker(r.extraQ, PURPLE, new BasicStroke(1.6f));
            m.setLabel("extra");
            m.setLabelFont(SMALL);
            m.setLabelPaint(PURPLE);
            plot.addDomainMarker(m);
        }
        JFreeChart chart = new JFreeChart(String.format("%s   a = %.1f Å   domain %.0f Å – %s", r.lattice, r.a, r.domain, title),
                TITLE, plot, true);
        finish(chart);
        return chart;
    }

    /**
     * A series: the chosen quantities against the varying condition, with phase changes marked. Several quantities
     * share the plot through one axis per unit.
     */
    static JFreeChart seriesChart(LnpSeries.Result r, java.util.List<LnpSeries.Descriptor> chosen) {
        NumberAxis x = new NumberAxis(r.conditionName + (r.conditionUnit.isEmpty() ? "" : " (" + r.conditionUnit + ")"));
        x.setAutoRangeIncludesZero(false);
        x.setTickLabelFont(SMALL);
        XYPlot plot = new XYPlot();
        plot.setDomainAxis(x);
        Color[] cols = {FIT, ACCENT, GOOD, PURPLE, new Color(0xd97706), new Color(0x0891b2)};
        java.util.Map<String, Integer> axisOfUnit = new java.util.LinkedHashMap<>();
        int ds = 0;
        for (LnpSeries.Descriptor d : chosen) {
            java.util.List<double[]> pts = new java.util.ArrayList<>();
            for (LnpSeries.Point p : r.points) {
                double v = p.value(d);
                if (!Double.isNaN(v)) pts.add(new double[]{p.condition, v});
            }
            if (pts.size() < 2) continue;
            XYSeries s = new XYSeries(d.toString(), false, true);
            for (double[] pt : pts) s.add(pt[0], pt[1]);
            int axis = axisOfUnit.computeIfAbsent(d.unit, u -> {
                NumberAxis y = new NumberAxis(u.isEmpty() ? "value" : u);
                y.setAutoRangeIncludesZero(false);
                y.setTickLabelFont(SMALL);
                int idx = axisOfUnit.size();
                plot.setRangeAxis(idx, y);
                return idx;
            });
            plot.setDataset(ds, new XYSeriesCollection(s));
            XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, true);
            rend.setSeriesPaint(0, cols[ds % cols.length]);
            rend.setSeriesStroke(0, new BasicStroke(1.8f));
            rend.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6));
            plot.setRenderer(ds, rend);
            plot.mapDatasetToRangeAxis(ds, axis);
            ds++;
        }
        // mark where the phase assignment changes
        LnpSeries.Point prev = null;
        for (LnpSeries.Point p : r.points) {
            if (p.error != null) continue;
            if (prev != null && !prev.phase.equals(p.phase) && !p.phase.equals("?") && !prev.phase.equals("?")) {
                ValueMarker m = new ValueMarker(0.5 * (prev.condition + p.condition), new Color(0xdc2626),
                        new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 4}, 0));
                m.setLabel(prev.phase + " → " + p.phase);
                m.setLabelFont(SMALL);
                m.setLabelPaint(new Color(0xdc2626));
                m.setLabelAnchor(org.jfree.ui.RectangleAnchor.TOP);
                m.setLabelTextAnchor(org.jfree.ui.TextAnchor.TOP_LEFT);
                plot.addDomainMarker(m);
            }
            prev = p;
        }
        style(plot);
        JFreeChart chart = new JFreeChart("Series – " + r.mode.label, TITLE, plot, ds > 1);
        finish(chart);
        return chart;
    }

    /** Abramowitz-Stegun erf, |error| < 1.5e-7 */
    static double erf(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }

    /** number distribution of core and outer radii */
    static JFreeChart sizeChart(LnpModel model, double[] p) {
        double r0 = p[LnpModel.R0], sg = Math.max(p[LnpModel.SIGMA], 1e-3 * r0), shell = model.outerRadius(p) - r0;
        XYSeries core = new XYSeries("core radius", false, true), outer = new XYSeries("outer radius", false, true);
        for (int i = 0; i <= 200; i++) {
            double rc = r0 - 4 * sg + i * 8 * sg / 200;
            if (rc <= 0) continue;
            double z = (rc - r0) / sg, f = Math.exp(-0.5 * z * z) / (sg * Math.sqrt(2 * Math.PI));
            core.add(rc, f);
            outer.add(rc + shell, f);
        }
        XYSeriesCollection c = new XYSeriesCollection();
        c.addSeries(core);
        c.addSeries(outer);
        XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, false);
        rend.setSeriesPaint(0, FIT);
        rend.setSeriesPaint(1, ACCENT);
        rend.setSeriesStroke(0, new BasicStroke(1.8f));
        rend.setSeriesStroke(1, DASH);
        NumberAxis x = new NumberAxis("radius (Å)");
        x.setTickLabelFont(SMALL);
        x.setAutoRangeIncludesZero(false);
        NumberAxis y = new NumberAxis("number fraction (1/Å)");
        y.setTickLabelFont(SMALL);
        XYPlot plot = new XYPlot(c, x, y, rend);
        style(plot);
        JFreeChart chart = new JFreeChart(String.format("Size distribution (σ/R₀ = %.2f)", sg / r0), TITLE, plot, true);
        finish(chart);
        return chart;
    }
}
