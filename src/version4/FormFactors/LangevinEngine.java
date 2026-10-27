package version4.FormFactors;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import version4.Dataset;
import version4.FormFactors.ModelType;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Drop-in SwingWorker for the SCATTER GUI, parallel to FormFactorEngine, that runs the
 * mean-field Langevin particle fitter instead of the cross-entropy search.
 *
 * <pre>
 *   LangevinSettings s = new LangevinSettings();
 *   s.particles = 1000; s.replicas = 2; s.threads = cores; s.fitBackground = !useNoBackground;
 *   LangevinEngine eng = new LangevinEngine(dataset, ModelType.PROLATE_ELLIPSOID,
 *                                           new double[]{10}, new double[]{150}, qmin, qmax, s, version);
 *   eng.setProgressBar(progressBar);
 *   eng.setPlotPanels(dataPanel, residualsPanel, distributionPanel, tracePanel);
 *   eng.execute();
 * </pre>
 *
 * Model mapping:
 *   SPHERICAL          -> sphere (R)
 *   PROLATE_ELLIPSOID  -> spheroid (a, c) with c/a >= 1.2 unless settings give another window
 *   OBLATE_ELLIPSOID   -> spheroid (a, c) with c/a <= 1/1.2 unless settings give another window
 *   ELLIPSOID          -> triaxial ellipsoid (a <= b <= c)
 *   CORESHELL, CORESHELL_PROLATE_ELLIPSOID, CORESHELL_OBLATE_ELLIPSOID
 *                      -> core-shell spheroid (core a, core c, shell t); contrasts from settings.solventDensity,
 *                         shellDensity, coreDensity; restrict t with settings.shellThickness / shellTolerance
 *                         (e.g. 14, 0.10). Prolate/oblate windows act on the core aspect ratio.
 * Bounds: minParams/maxParams may hold one value (used for every axis, as in FormFactorEngine) or one per parameter.
 */
public class LangevinEngine extends SwingWorker<LangevinResult, Void> {

    private final LangevinFitter fitter;
    private final ShapeFamily family;
    private final LangevinSettings settings;
    private final String version;
    private final double qmin, qmax;
    private LangevinResult result;

    private JProgressBar progressBar;
    private JPanel dataPanel, residualsPanel, distributionPanel, tracePanel;

    public LangevinEngine(Dataset dataset, ModelType model, double[] minParams, double[] maxParams,
                          double qmin, double qmax, LangevinSettings settings, String version) {
        this.settings = settings;
        this.version = version;
        this.qmin = qmin;
        this.qmax = qmax;

        switch (model) {
            case SPHERICAL:
                family = ShapeFamily.SPHERE;
                break;
            case PROLATE_ELLIPSOID:
                family = ShapeFamily.SPHEROID;
                if (settings.aspectRatioMin <= 0 && settings.aspectRatioMax == Double.POSITIVE_INFINITY) {
                    settings.aspectRatioMin = 1.2;
                }
                break;
            case OBLATE_ELLIPSOID:
                family = ShapeFamily.SPHEROID;
                if (settings.aspectRatioMin <= 0 && settings.aspectRatioMax == Double.POSITIVE_INFINITY) {
                    settings.aspectRatioMax = 1.0 / 1.2;
                }
                break;
            case ELLIPSOID:
                family = ShapeFamily.TRIAXIAL_ELLIPSOID;
                break;
            case CORESHELL:
                family = ShapeFamily.CORESHELL_SPHEROID;   // core aspect ratio free
                break;
            case CORESHELL_PROLATE_ELLIPSOID:
                family = ShapeFamily.CORESHELL_SPHEROID;
                if (settings.aspectRatioMin <= 0 && settings.aspectRatioMax == Double.POSITIVE_INFINITY) settings.aspectRatioMin = 1.2;
                break;
            case CORESHELL_OBLATE_ELLIPSOID:
                family = ShapeFamily.CORESHELL_SPHEROID;
                if (settings.aspectRatioMin <= 0 && settings.aspectRatioMax == Double.POSITIVE_INFINITY) settings.aspectRatioMax = 1.0 / 1.2;
                break;
            default:
                throw new IllegalArgumentException("Langevin fitter does not support " + model + " yet");
        }

        int d = family.dim();
        double[] lo = new double[d], hi = new double[d];
        for (int k = 0; k < d; k++) {
            lo[k] = minParams[Math.min(k, minParams.length - 1)];
            hi[k] = maxParams[Math.min(k, maxParams.length - 1)];
        }

        // data inside [qmin, qmax], skipping points without a usable error
        XYSeries all = dataset.getAllData();
        XYSeries err = dataset.getAllDataError();
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < all.getItemCount(); i++) {
            double x = all.getX(i).doubleValue();
            if (x < qmin || x > qmax) continue;
            double y = all.getY(i).doubleValue();
            double e = err.getY(i).doubleValue();
            if (!(e > 0) || Double.isNaN(y)) continue;
            pts.add(new double[]{x, y, e});
        }
        pts.sort((a, b) -> Double.compare(a[0], b[0]));
        double[] q = new double[pts.size()], I = new double[pts.size()], s = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) { q[i] = pts.get(i)[0]; I[i] = pts.get(i)[1]; s[i] = pts.get(i)[2]; }

        // use the Dmax of the user's P(r) model when one has been determined
        if (!(settings.dmax > 0)) {
            try {
                double dm = dataset.getRealSpaceModel().getDmax();
                if (dm > 1) settings.dmax = dm;
            } catch (RuntimeException ignored) { /* no real-space model yet: Dmax will be estimated */ }
        }
        fitter = new LangevinFitter(q, I, s, family, lo, hi, settings);
    }

    public void setProgressBar(JProgressBar bar) {
        this.progressBar = bar;
        bar.setMinimum(0);
        bar.setMaximum(100);
        bar.setStringPainted(true);
    }

    public void setPlotPanels(JPanel dataPanel, JPanel residualsPanel, JPanel distributionPanel, JPanel tracePanel) {
        this.dataPanel = dataPanel;
        this.residualsPanel = residualsPanel;
        this.distributionPanel = distributionPanel;
        this.tracePanel = tracePanel;
    }

    /** convenience: restrict the shell thickness to t (1 +- relTol), as the old setCompleteness(..., thickness) did */
    public static void restrictShell(LangevinSettings s, double thickness, double relTol) {
        s.shellThickness = thickness;
        s.shellTolerance = relTol;
    }

    /** stops the fit at the next step; the partial result is discarded */
    public void stop() {
        fitter.cancel();
        cancel(false);
    }

    public LangevinResult getResult() { return result; }

    @Override
    protected LangevinResult doInBackground() {
        final int R = Math.max(1, settings.replicas);
        result = fitter.fit((rep, step, total, phase, T, chi2) -> {
            int pct = (int) Math.round(100.0 * (rep * total + step + 1) / ((double) R * total));
            setProgress(Math.max(0, Math.min(100, pct)));
            if (progressBar != null) {
                final String label = String.format("rep %d/%d  %s  chi2 %.3f", rep + 1, R, phase, chi2);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(pct);
                    progressBar.setString(label);
                });
            }
        });
        if (result != null) System.out.println(header() + result.toRemarks());
        return result;
    }

    @Override
    protected void done() {
        if (progressBar != null) {
            progressBar.setValue(0);
            progressBar.setString(result == null ? "Cancelled" : "Finished");
        }
        if (result == null) return;
        if (dataPanel != null) show(dataPanel, fitChart());
        if (residualsPanel != null) show(residualsPanel, residualChart());
        if (distributionPanel != null) show(distributionPanel, distributionChart());
        if (tracePanel != null) show(tracePanel, traceChart());
    }

    private String header() {
        StringBuilder sb = new StringBuilder();
        sb.append("REMARK 265 \n");
        sb.append("REMARK 265       DATA REDUCTION SOFTWARE : SCATTER (v ").append(version).append(")\n");
        sb.append("REMARK 265               SOFTWARE AUTHOR : RP RAMBO\n");
        sb.append(String.format("REMARK 265                          QMIN : %.4f %n", qmin));
        sb.append(String.format("REMARK 265                          QMAX : %.4f %n", qmax));
        return sb.toString();
    }

    // ------------------------------------------------------------------ charts

    private static void show(JPanel panel, JFreeChart chart) {
        panel.removeAll();
        panel.setLayout(new BorderLayout());
        panel.add(new ChartPanel(chart), BorderLayout.CENTER);
        panel.revalidate();
        panel.repaint();
    }

    private JFreeChart fitChart() {
        XYSeries obs = new XYSeries("data"), fit = new XYSeries("fit");
        for (int j = 0; j < result.q.length; j++) {
            if (result.intensity[j] > 0) obs.add(result.q[j], result.intensity[j]);
            if (result.fit[j] > 0) fit.add(result.q[j], result.fit[j]);
        }
        XYSeriesCollection c = new XYSeriesCollection();
        c.addSeries(obs);
        c.addSeries(fit);
        JFreeChart ch = ChartFactory.createXYLineChart("", "q", "I(q)", c, PlotOrientation.VERTICAL, true, false, false);
        XYPlot p = ch.getXYPlot();
        LogAxis y = new LogAxis("I(q)");
        y.setSmallestValue(1e-12);
        p.setRangeAxis(y);
        XYLineAndShapeRenderer r = new XYLineAndShapeRenderer();
        r.setSeriesLinesVisible(0, false);
        r.setSeriesShapesVisible(0, true);
        r.setSeriesShape(0, new java.awt.geom.Ellipse2D.Double(-1.5, -1.5, 3, 3));
        r.setSeriesPaint(0, Color.GRAY);
        r.setSeriesLinesVisible(1, true);
        r.setSeriesShapesVisible(1, false);
        r.setSeriesPaint(1, new Color(37, 99, 235));
        p.setRenderer(r);
        return ch;
    }

    private JFreeChart residualChart() {
        XYSeries res = new XYSeries("(I - fit)/sigma");
        for (int j = 0; j < result.q.length; j++)
            res.add(result.q[j], (result.intensity[j] - result.fit[j]) / result.sigma[j]);
        return ChartFactory.createScatterPlot("", "q", "residual / sigma",
                new XYSeriesCollection(res), PlotOrientation.VERTICAL, false, false, false);
    }

    /** volume-fraction distribution of every parameter vs. that parameter (log axis) */
    private JFreeChart distributionChart() {
        XYSeriesCollection c = new XYSeriesCollection();
        for (int k = 0; k < result.dim; k++) {
            XYSeries s = new XYSeries(result.parameterNames[k] + " (volume)");
            for (int b = 0; b < result.binCenters[k].length; b++) s.add(result.binCenters[k][b], result.volumeDensity[k][b]);
            c.addSeries(s);
        }
        JFreeChart ch = ChartFactory.createXYLineChart("", "length (Å)", "dφ / d ln p", c,
                PlotOrientation.VERTICAL, true, false, false);
        XYPlot p = ch.getXYPlot();
        p.setDomainAxis(new LogAxis("length (Å)"));
        p.setRenderer(new XYStepRenderer());
        return ch;
    }

    private JFreeChart traceChart() {
        XYSeries chi = new XYSeries("reduced chi2"), tt = new XYSeries("temperature");
        for (int t = 0; t < result.traceChi2.length; t++) {
            if (result.traceChi2[t] > 0) chi.add(t, result.traceChi2[t]);
            if (result.traceTemperature[t] > 0) tt.add(t, result.traceTemperature[t]);
        }
        XYSeriesCollection c = new XYSeriesCollection();
        c.addSeries(chi);
        c.addSeries(tt);
        JFreeChart ch = ChartFactory.createXYLineChart("", "step", "value", c, PlotOrientation.VERTICAL, true, false, false);
        LogAxis y = new LogAxis("value");
        y.setSmallestValue(1e-12);
        ch.getXYPlot().setRangeAxis(y);
        ch.getXYPlot().setDomainAxis(new NumberAxis("step"));
        return ch;
    }
}
