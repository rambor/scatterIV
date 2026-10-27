package version4;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYErrorRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import version4.InverseTransform.DmaxAnalysis;
import version4.InverseTransform.DmaxSelector;
import version4.InverseTransform.EnsembleIFT;
import version4.InverseTransform.IndirectFT;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Finds qmax and dmax by consensus over several indirect Fourier transforms.
 *
 * For every trial dmax each selected method chooses its own regularization by Bayesian evidence and
 * scores the fit on the -2 ln(likelihood) scale (evidence, or AIC_eff for L1 and SVD), plus the
 * positivity term -2 ln Pr(P(r) >= 0 after the peak). Each method's scores become a probability
 * over dmax; the probabilities are averaged over the methods that do not oscillate. The suggested
 * dmax is the lowest one whose consensus score is within 2 of the best. qmax (optional) is the last
 * Shannon channel before the methods' P(r) start to ripple. The ensemble P(r) at the chosen values
 * is shown with its total uncertainty (data noise + spread between methods).
 *
 * Panels: consensus dmax curves | ensemble P(r)
 *         qmax selection        | report
 *
 * The UI is built in code: delete FindDmax.form, which bound the previous version's fields.
 * The computation lives in version4.InverseTransform.DmaxAnalysis.
 */
public class FindDmax extends JDialog {

    /* palette: categorical slots in fixed order (one per method), ink for the consensus */
    private static final Color INK = new Color(0x1f1f1d), MUTED = new Color(0x6b6a64), GRID = new Color(0xe6e5df),
            BAND = new Color(0xf3, 0xef, 0xe4), SURFACE = Color.white;
    private static final Map<String, Color> METHOD_COLORS = new LinkedHashMap<>();
    static {
        METHOD_COLORS.put("GNOM-like", new Color(0xeb6834));
        METHOD_COLORS.put("Legendre", new Color(0x1baf7a));
        METHOD_COLORS.put("Shannon-L1", new Color(0xeda100));
        METHOD_COLORS.put("SPI", new Color(0xe87ba4));
        METHOD_COLORS.put("Moore-L2", new Color(0x2a78d6));
    }

    private final RealSpace dataset;
    private final WorkingDirectory workingDirectory;
    private final Color datasetColor;

    private final JFrame frame;
    private final JTextField dmaxLowerField = new JTextField("50", 5);
    private final JTextField dmaxUpperField = new JTextField("200", 5);
    private final JTextField dmaxStepField = new JTextField("auto", 4);
    private final JTextField qmaxField = new JTextField(7);
    private final JCheckBox autoQmaxBox = new JCheckBox("auto", true);
    private final JCheckBox positivityBox = new JCheckBox("penalize P(r) < 0", true);
    private final JCheckBox autoLowQBox = new JCheckBox("auto low-q trim", true);
    private final Map<String, JCheckBox> methodBoxes = new LinkedHashMap<>();
    private final JButton startButton = new JButton("Start");
    private final JButton applyButton = new JButton("Apply qmax");
    private final JButton closeButton = new JButton("Close");
    private final JButton saveButton = new JButton("Save figure\u2026");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel(" ");
    private final JPanel consensusPanel = chartHolder(), likelihoodPanel = chartHolder(), prPanel = chartHolder(), qmaxPanel = chartHolder();
    private final JTextArea reportArea = new JTextArea();

    private DmaxAnalysis.Result lastResult;
    private JFreeChart consensusChart, likelihoodChart, prChart, qmaxChart;   // current charts, for export

    public FindDmax(RealSpace dataset, double currentqmax, WorkingDirectory workingDirectory) {
        this.dataset = dataset;
        this.workingDirectory = workingDirectory;
        this.datasetColor = (dataset.getColor() == null) ? INK : dataset.getColor();
        qmaxField.setText(String.format("%.4f", currentqmax));
        qmaxField.setEnabled(false);

        for (String name : METHOD_COLORS.keySet()) {
            JCheckBox box = new JCheckBox(name, true);
            box.setForeground(METHOD_COLORS.get(name).darker());
            methodBoxes.put(name, box);
        }
        autoQmaxBox.addActionListener(e -> qmaxField.setEnabled(!autoQmaxBox.isSelected()));
        startButton.addActionListener(e -> start());
        applyButton.setEnabled(false);
        applyButton.addActionListener(e -> applyQmax());
        closeButton.addActionListener(e -> close());
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveFigure());

        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        reportArea.setText(String.join("\n",
                "Set the dmax search range, choose the methods and press Start.",
                "",
                "Each method picks its own regularization at every trial dmax.",
                "The thick curve is the consensus over methods; the shaded band",
                "marks dmax values the data support (within 4 of the best, a 95%",
                "band). With P(r) < 0 penalized the band ends where P(r) starts to",
                "go negative; the suggested dmax is the upper edge of the band.",
                "",
                "With 'auto low-q trim', leading points that deviate systematically",
                "from the Guinier line (aggregation, beamstop) are removed, and the",
                "low-q end is checked again against the final P(r) models.",
                "",
                "With 'auto' qmax, data are added one Shannon channel at a time",
                "until the transforms start to ripple (noise or buffer mismatch)."));

        // top: consensus | likelihood | P(r);  bottom: qmax | report (two columns wide)
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.weighty = 1;
        gc.insets = new Insets(2, 2, 2, 2);
        gc.gridy = 0;
        gc.gridx = 0; center.add(titled(consensusPanel, "dmax consensus"), gc);
        gc.gridx = 1; center.add(titled(likelihoodPanel, "dmax likelihood"), gc);
        gc.gridx = 2; center.add(titled(prPanel, "Ensemble P(r)"), gc);
        gc.gridy = 1;
        gc.gridx = 0; center.add(titled(qmaxPanel, "qmax selection"), gc);
        gc.gridx = 1; gc.gridwidth = 2; center.add(titled(new JScrollPane(reportArea), "Report"), gc);

        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        content.add(controls(), BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(statusBar(), BorderLayout.SOUTH);

        // shown in its own frame, as before (a component can only live in one window)
        frame = new JFrame("Find dmax \u2013 " + datasetName());
        frame.setContentPane(content);
        frame.getRootPane().setDefaultButton(startButton);
        frame.setPreferredSize(new Dimension(1500, 860));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /* ------------------------------------------------------------------ layout */

    private JPanel controls() {
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row1.add(new JLabel("dmax from"));
        row1.add(dmaxLowerField);
        row1.add(new JLabel("to"));
        row1.add(dmaxUpperField);
        row1.add(new JLabel("Å   step"));
        row1.add(dmaxStepField);
        row1.add(Box.createHorizontalStrut(16));
        row1.add(new JLabel("qmax"));
        row1.add(qmaxField);
        row1.add(autoQmaxBox);
        row1.add(Box.createHorizontalStrut(16));
        row1.add(positivityBox);
        row1.add(autoLowQBox);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row2.add(new JLabel("Methods:"));
        for (JCheckBox b : methodBoxes.values()) row2.add(b);
        row2.add(Box.createHorizontalStrut(16));
        row2.add(startButton);
        row2.add(applyButton);
        row2.add(saveButton);
        row2.add(closeButton);

        JPanel p = new JPanel(new GridLayout(2, 1));
        p.add(row1);
        p.add(row2);
        return p;
    }

    private JPanel statusBar() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        progressBar.setPreferredSize(new Dimension(180, 16));
        p.add(statusLabel, BorderLayout.CENTER);
        p.add(progressBar, BorderLayout.EAST);
        return p;
    }

    private static JPanel chartHolder() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(SURFACE);
        return p;
    }

    private static JComponent titled(JComponent c, String title) {
        c.setBorder(BorderFactory.createTitledBorder(title));
        return c;
    }

    /* ------------------------------------------------------------------ actions */

    private void start() {
        final double lo, hi, step, fixedQmax;
        try {
            lo = Double.parseDouble(dmaxLowerField.getText().trim());
            hi = Double.parseDouble(dmaxUpperField.getText().trim());
            String s = dmaxStepField.getText().trim();
            step = s.isEmpty() || s.equalsIgnoreCase("auto") ? Double.NaN : Double.parseDouble(s);
            fixedQmax = autoQmaxBox.isSelected() ? Double.NaN : Double.parseDouble(qmaxField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("Numbers only: dmax bounds, step (or 'auto') and qmax");
            return;
        }
        if (!(lo > 0 && hi > lo)) { statusLabel.setText("dmax bounds must satisfy 0 < lower < upper"); return; }
        if (!Double.isNaN(step) && step <= 0) { statusLabel.setText("step must be positive"); return; }

        final Set<String> methods = new LinkedHashSet<>();
        for (Map.Entry<String, JCheckBox> e : methodBoxes.entrySet()) if (e.getValue().isSelected()) methods.add(e.getKey());
        if (methods.isEmpty()) { statusLabel.setText("Select at least one method (two or more for a consensus)"); return; }

        final XYSeries[] data;
        try {
            data = analysisData();
        } catch (RuntimeException ex) {
            reportFailure("reading the data", ex);
            return;
        }
        if (data[0].getItemCount() < 10) { statusLabel.setText("Too few data points"); return; }
        final boolean positivity = positivityBox.isSelected();
        final boolean autoLowQ = autoLowQBox.isSelected();

        setRunning(true);
        new SwingWorker<DmaxAnalysis.Result, String>() {
            @Override protected DmaxAnalysis.Result doInBackground() {
                return DmaxAnalysis.run(data[0], data[1], lo, hi, step, fixedQmax, methods, positivity, autoLowQ, this::publish);
            }
            @Override protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }
            @Override protected void done() {
                setRunning(false);
                DmaxAnalysis.Result result;
                try {
                    result = get();
                } catch (InterruptedException | ExecutionException ex) {
                    reportFailure("the analysis", (ex.getCause() == null) ? ex : ex.getCause());
                    return;
                }
                try {
                    show(result);
                } catch (RuntimeException ex) {
                    reportFailure("drawing the results", ex);
                }
            }
        }.execute();
    }

    /** shows the error and its full stack trace in the Report panel (and on stderr) */
    private void reportFailure(String stage, Throwable t) {
        setRunning(false);
        statusLabel.setText("Failed while " + stage + ": " + t);
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        reportArea.setText("FindDmax failed while " + stage + " (" + datasetName() + ")\n\n" + sw);
        reportArea.setCaretPosition(0);
        t.printStackTrace();
    }

    /** the dataset's file name, or a placeholder */
    private String datasetName() {
        String n = null;
        try {
            n = dataset.getFilename();
        } catch (RuntimeException ignored) {
            // fall through
        }
        return (n == null || n.trim().isEmpty()) ? "unnamed dataset" : n.trim();
    }

    private void setRunning(boolean running) {
        startButton.setEnabled(!running);
        applyButton.setEnabled(!running && lastResult != null);
        progressBar.setIndeterminate(running);
    }

    /**
     * q*I(q) and q*sigma(q) from the first point of the fitted region (keeps the low-q cut) to the
     * end of the data, so automatic qmax can look beyond the current qmax.
     */
    private XYSeries[] analysisData() {
        XYSeries fitted = dataset.getfittedqIq();
        XYSeries all = dataset.getOriginalqIq();
        XYSeries errors = dataset.getErrorAllData();
        // auto low-q trim starts from the first measured point; otherwise keep the user's low-q cut
        double qStart = autoLowQBox.isSelected() ? all.getMinX() : fitted.getMinX();
        double qEnd = autoQmaxBox.isSelected() ? all.getMaxX() : fitted.getMaxX();
        XYSeries source = autoQmaxBox.isSelected() ? all : fitted;
        XYSeries qIq = new XYSeries("qIq"), qSigma = new XYSeries("q*sigma");
        for (int i = 0; i < source.getItemCount(); i++) {
            XYDataItem item = source.getDataItem(i);
            double q = item.getXValue();
            if (q < qStart || q > qEnd) continue;
            int j = errors.indexOf(item.getX());
            if (j < 0) continue;
            double sigma = errors.getY(j).doubleValue();
            if (!(sigma > 0)) continue;
            qIq.add(q, item.getYValue());
            qSigma.add(q, q*sigma);
        }
        return new XYSeries[]{qIq, qSigma};
    }

    /** restricts the dataset's fitted range to the chosen qmax (same calls as the previous version) */
    private void applyQmax() {
        if (lastResult == null) return;
        dataset.resetStartStop();
        XYSeries fitted = dataset.getfittedqIq();
        int last = fitted.getItemCount() - 1;
        for (int i = 0; i < fitted.getItemCount(); i++) {
            if (fitted.getX(i).doubleValue() > lastResult.qmax) { last = i - 1; break; }
        }
        dataset.decrementHigh(last);
        statusLabel.setText(String.format("qmax set to %.4f; use dmax %.1f Å", lastResult.qmax, lastResult.dmax));
    }

    private void close() {
        frame.dispose();
        dispose();
    }

    /* ------------------------------------------------------------------ results */

    private void show(DmaxAnalysis.Result r) {
        lastResult = r;
        applyButton.setEnabled(true);
        saveButton.setEnabled(true);
        qmaxField.setText(String.format("%.4f", r.qmax));
        statusLabel.setText(String.format("Suggested dmax %.1f \u212B (data support %.0f-%.0f) at q %.4f-%.4f",
                r.dmax, r.dmaxScan.lowestAdequate, r.dmaxScan.largestAdequate, r.qmin, r.qmax));
        reportArea.setText(datasetName() + "\n\n" + r.report());
        reportArea.setCaretPosition(0);
        consensusChart = consensusChart(r.dmaxScan);
        likelihoodChart = likelihoodChart(r.dmaxScan);
        prChart = prChart(r);
        qmaxChart = (r.qmaxScan == null) ? null : qmaxChart(r.qmaxScan);
        setChart(consensusPanel, consensusChart);
        setChart(likelihoodPanel, likelihoodChart);
        setChart(prPanel, prChart);
        setChart(qmaxPanel, qmaxChart);
    }

    private void setChart(JPanel holder, JFreeChart chart) {
        holder.removeAll();
        if (chart == null) {
            JLabel l = new JLabel("qmax fixed by the user", SwingConstants.CENTER);
            l.setForeground(MUTED);
            holder.add(l, BorderLayout.CENTER);
        } else {
            ChartPanel cp = new ChartPanel(chart);
            if (workingDirectory != null) cp.setDefaultDirectoryForSaveAs(new File(workingDirectory.getWorkingDirectory()));
            holder.add(cp, BorderLayout.CENTER);
        }
        holder.revalidate();
        holder.repaint();
    }

    /** per-method Delta(-2 ln Likelihood) curves, the consensus, the within-2 band and the chosen dmax */
    private JFreeChart consensusChart(DmaxSelector.DmaxScan s) {
        XYSeriesCollection methods = new XYSeriesCollection();
        XYLineAndShapeRenderer mr = new XYLineAndShapeRenderer(true, true);
        for (int m = 0; m < s.methods.length; m++) {
            double lo = Double.POSITIVE_INFINITY;
            for (double v : s.score[m]) if (!Double.isNaN(v)) lo = Math.min(lo, v);
            XYSeries series = new XYSeries(s.methods[m] + (s.included[m] ? "" : " (excluded)"));
            for (int i = 0; i < s.dmax.length; i++) {
                double v = s.score[m][i] - lo;
                if (!Double.isNaN(v) && !Double.isInfinite(v)) series.add(s.dmax[i], v);
            }
            methods.addSeries(series);
            Color c = METHOD_COLORS.getOrDefault(s.methods[m], MUTED);
            mr.setSeriesPaint(m, s.included[m] ? c : withAlpha(c, 90));
            mr.setSeriesStroke(m, s.included[m] ? new BasicStroke(1.4f)
                    : new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 3f}, 0f));
            mr.setSeriesShape(m, new Ellipse2D.Double(-2.5, -2.5, 5, 5));
            mr.setSeriesShapesFilled(m, false);
        }

        XYSeries consensus = new XYSeries("consensus");
        for (int i = 0; i < s.dmax.length; i++) {
            if (!Double.isNaN(s.consensusScore[i]) && !Double.isInfinite(s.consensusScore[i])) consensus.add(s.dmax[i], s.consensusScore[i]);
        }
        XYSeriesCollection cc = new XYSeriesCollection(consensus);
        XYLineAndShapeRenderer cr = new XYLineAndShapeRenderer(true, false);
        cr.setSeriesPaint(0, INK);
        cr.setSeriesStroke(0, new BasicStroke(3.0f));

        JFreeChart chart = ChartFactory.createXYLineChart(null, "dmax (Å)", "Δ(−2 ln Likelihood)", cc,
                PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = chart.getXYPlot();
        styleChart(chart);
        plot.setRenderer(0, cr);
        plot.setDataset(1, methods);
        plot.setRenderer(1, mr);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.REVERSE);   // consensus (dataset 0) on top

        IntervalMarker band = new IntervalMarker(0, DmaxSelector.SUGGESTED_DELTA);
        band.setPaint(BAND);
        plot.addRangeMarker(band, org.jfree.ui.Layer.BACKGROUND);
        plot.addDomainMarker(labelledMarker(s.suggested, String.format("suggested %.1f \u212B", s.suggested), INK, false));
        if (s.lowestAdequate != s.suggested) plot.addDomainMarker(labelledMarker(s.lowestAdequate, String.format("band starts %.1f", s.lowestAdequate), MUTED, true));

        NumberAxis x = (NumberAxis) plot.getDomainAxis();
        x.setAutoRangeIncludesZero(false);
        x.setRange(s.dmax[0], s.dmax[s.dmax.length - 1]);
        NumberAxis y = (NumberAxis) plot.getRangeAxis();
        y.setRange(-1, 30);
        return chart;
    }

    /**
     * the consensus as a likelihood over dmax (as in Rambo 2025): Gaussian KDE (h = KDE_BANDWIDTH) of
     * the averaged probability p(d), each included method's share drawn faintly underneath, the
     * data-supported band shaded, and markers at the peak and at the suggested dmax
     */
    private JFreeChart likelihoodChart(DmaxSelector.DmaxScan s) {
        double h = DmaxSelector.KDE_BANDWIDTH, dx = 0.25;
        double[][] L = s.likelihood(h, dx);
        XYSeries kde = new XYSeries("likelihood (KDE, h = " + h + " \u212B)");
        for (int i = 0; i < L[0].length; i++) kde.add(L[0][i], L[1][i]);
        XYSeriesCollection kc = new XYSeriesCollection(kde);
        XYLineAndShapeRenderer kr = new XYLineAndShapeRenderer(true, false);
        kr.setSeriesPaint(0, INK);
        kr.setSeriesStroke(0, new BasicStroke(3.0f));

        XYSeriesCollection perMethod = new XYSeriesCollection();
        XYLineAndShapeRenderer mr = new XYLineAndShapeRenderer(true, false);
        int k = 0;
        for (int m = 0; m < s.methods.length; m++) {
            if (!s.included[m]) continue;
            double[][] Lm = s.methodLikelihood(m, h, dx);
            XYSeries series = new XYSeries(s.methods[m]);
            for (int i = 0; i < Lm[0].length; i++) series.add(Lm[0][i], Lm[1][i]);
            perMethod.addSeries(series);
            mr.setSeriesPaint(k, withAlpha(METHOD_COLORS.getOrDefault(s.methods[m], MUTED), 170));
            mr.setSeriesStroke(k, new BasicStroke(1.2f));
            k++;
        }

        JFreeChart chart = ChartFactory.createXYLineChart(null, "dmax (\u212B)", "likelihood", kc,
                PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = chart.getXYPlot();
        styleChart(chart);
        plot.setRenderer(0, kr);
        plot.setDataset(1, perMethod);
        plot.setRenderer(1, mr);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.REVERSE);   // KDE (dataset 0) on top

        IntervalMarker band = new IntervalMarker(s.lowestAdequate, s.largestAdequate);
        band.setPaint(BAND);
        plot.addDomainMarker(band, org.jfree.ui.Layer.BACKGROUND);
        double peak = s.likelihoodPeak(h);
        plot.addDomainMarker(labelledMarker(s.suggested, String.format("suggested %.1f \u212B", s.suggested), INK, false));
        if (Math.abs(peak - s.suggested) > 0.5) {
            plot.addDomainMarker(labelledMarker(peak, String.format("peak %.1f", peak), MUTED, true));
        }
        NumberAxis x = (NumberAxis) plot.getDomainAxis();
        x.setAutoRangeIncludesZero(false);
        x.setRange(s.dmax[0], s.dmax[s.dmax.length - 1]);
        NumberAxis y = (NumberAxis) plot.getRangeAxis();
        y.setTickLabelsVisible(false);
        return chart;
    }

    /** ensemble Shannon bins (mean +/- 2 sigma total) over the members' P(r) curves */
    private JFreeChart prChart(DmaxAnalysis.Result r) {
        EnsembleIFT ens = r.ensemble;
        XYSeriesCollection curves = new XYSeriesCollection();
        XYLineAndShapeRenderer lr = new XYLineAndShapeRenderer(true, false);
        int k = 0;
        for (int m = 0; m < r.fits.size(); m++) {
            boolean in = (ens == null) || ens.included[m];
            IndirectFT f = r.fits.get(m);
            XYSeries s = new XYSeries(r.names.get(m) + (in ? "" : " (excluded)"));
            int n = 300;
            for (int i = 0; i <= n; i++) {
                double rr = r.dmax*i/n;
                double v;
                try {
                    v = (i == 0 || i == n) ? 0 : f.calculatePofRAtR(rr, 1.0);
                } catch (RuntimeException ex) {
                    continue;
                }
                if (!Double.isNaN(v)) s.add(rr, v);
            }
            curves.addSeries(s);
            Color c = METHOD_COLORS.getOrDefault(r.names.get(m), MUTED);
            lr.setSeriesPaint(k, in ? c : withAlpha(c, 70));
            lr.setSeriesStroke(k, new BasicStroke(in ? 1.6f : 1.0f));
            k++;
        }

        JFreeChart chart = ChartFactory.createXYLineChart(null, "r (Å)", "P(r)", curves,
                PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = chart.getXYPlot();
        styleChart(chart);
        plot.setRenderer(0, lr);

        if (ens != null) {
            YIntervalSeries bins = new YIntervalSeries("ensemble ± 2σ");
            for (int b = 0; b < ens.nBins; b++) bins.add(ens.r[b], ens.mean[b], ens.mean[b] - 2*ens.sigma[b], ens.mean[b] + 2*ens.sigma[b]);
            YIntervalSeriesCollection bc = new YIntervalSeriesCollection();
            bc.addSeries(bins);
            XYErrorRenderer er = new XYErrorRenderer();
            er.setDrawXError(false);
            er.setDrawYError(true);
            er.setBaseLinesVisible(false);
            er.setBaseShapesVisible(true);
            er.setSeriesShape(0, new Rectangle2D.Double(-4, -4, 8, 8));
            er.setSeriesPaint(0, datasetColor);
            er.setErrorPaint(INK);
            plot.setDataset(1, bc);
            plot.setRenderer(1, er);
            plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);   // bins (dataset 1) over the curves
        }
        plot.addRangeMarker(new ValueMarker(0.0, MUTED, new BasicStroke(1f)));
        NumberAxis x = (NumberAxis) plot.getDomainAxis();
        x.setRange(0, r.dmax*1.03);
        ((NumberAxis) plot.getRangeAxis()).setTickLabelsVisible(false);
        return chart;
    }

    /** median oscillation of the methods against offered qmax (log scale), threshold and choice */
    private JFreeChart qmaxChart(DmaxSelector.QmaxScan q) {
        final double floor = 1e-4;
        XYSeriesCollection perMethod = new XYSeriesCollection();
        XYLineAndShapeRenderer mr = new XYLineAndShapeRenderer(true, true);
        for (int m = 0; m < q.methods.length; m++) {
            XYSeries s = new XYSeries(q.methods[m]);
            for (int k = 0; k < q.q.length; k++) s.add(q.q[k], Math.max(floor, q.oscillation[k][m]));
            perMethod.addSeries(s);
            mr.setSeriesPaint(m, withAlpha(METHOD_COLORS.getOrDefault(q.methods[m], MUTED), 170));
            mr.setSeriesStroke(m, new BasicStroke(1.1f));
            mr.setSeriesShape(m, new Ellipse2D.Double(-2.5, -2.5, 5, 5));
            mr.setSeriesShapesFilled(m, false);
        }
        XYSeries median = new XYSeries("median");
        for (int k = 0; k < q.q.length; k++) median.add(q.q[k], Math.max(floor, q.medianOscillation[k]));
        XYSeriesCollection mc = new XYSeriesCollection(median);
        XYLineAndShapeRenderer medr = new XYLineAndShapeRenderer(true, false);
        medr.setSeriesPaint(0, INK);
        medr.setSeriesStroke(0, new BasicStroke(3.0f));

        JFreeChart chart = ChartFactory.createXYLineChart(null, "qmax offered (Å⁻¹)", "oscillation score", mc,
                PlotOrientation.VERTICAL, true, true, false);
        XYPlot plot = chart.getXYPlot();
        styleChart(chart);
        LogAxis y = new LogAxis("oscillation score");
        y.setSmallestValue(floor);
        y.setRange(floor*0.8, 3);
        y.setTickLabelPaint(MUTED);
        y.setLabelPaint(INK);
        plot.setRangeAxis(y);
        plot.setRenderer(0, medr);
        plot.setDataset(1, perMethod);
        plot.setRenderer(1, mr);
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.REVERSE);   // median (dataset 0) on top
        ValueMarker threshold = new ValueMarker(DmaxSelector.QMAX_OSC_THRESHOLD, MUTED,
                new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
        threshold.setLabel("threshold");
        threshold.setLabelPaint(MUTED);
        threshold.setLabelAnchor(RectangleAnchor.TOP_LEFT);
        threshold.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
        plot.addRangeMarker(threshold);
        plot.addDomainMarker(labelledMarker(q.qmax, String.format("qmax %.4f", q.qmax), INK, false));
        ((NumberAxis) plot.getDomainAxis()).setAutoRangeIncludesZero(false);
        return chart;
    }

    /* ------------------------------------------------------------------ export */

    /** figure size in inches and resolution used for PNG and PDF export */
    public static double FIGURE_WIDTH_IN = 15.0, FIGURE_HEIGHT_IN = 8.5;
    public static int FIGURE_DPI = 300;

    /** asks for a file and writes the 2x2 figure (charts + report) as PNG or PDF */
    private void saveFigure() {
        if (lastResult == null) return;
        JFileChooser chooser = new JFileChooser(workingDirectory == null ? null : new File(workingDirectory.getWorkingDirectory()));
        javax.swing.filechooser.FileNameExtensionFilter png = new javax.swing.filechooser.FileNameExtensionFilter("PNG image (*.png)", "png");
        javax.swing.filechooser.FileNameExtensionFilter pdf = new javax.swing.filechooser.FileNameExtensionFilter("PDF document (*.pdf)", "pdf");
        chooser.addChoosableFileFilter(png);
        chooser.addChoosableFileFilter(pdf);
        chooser.setFileFilter(pdf);
        String base = datasetName().replaceAll("\\.[A-Za-z0-9]+$", "").replaceAll("[^A-Za-z0-9._-]+", "_");
        chooser.setSelectedFile(new File(String.format("%s_dmax%.0f.pdf", base, lastResult.dmax)));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        String name = file.getName().toLowerCase();
        boolean asPdf = name.endsWith(".pdf") || (!name.endsWith(".png") && chooser.getFileFilter() == pdf);
        if (!name.endsWith(".pdf") && !name.endsWith(".png")) file = new File(file.getPath() + (asPdf ? ".pdf" : ".png"));
        if (file.exists() && JOptionPane.showConfirmDialog(frame, file.getName() + " exists. Replace it?", "Save figure",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            if (asPdf) {
                boolean vector = writePdf(file);
                statusLabel.setText("Saved " + file.getName() + (vector ? " (vector)" : " (300 dpi image; add pdfbox-graphics2d for vector output)"));
            } else {
                javax.imageio.ImageIO.write(renderFigure(), "png", file);
                statusLabel.setText("Saved " + file.getName());
            }
        } catch (java.io.IOException | RuntimeException ex) {
            statusLabel.setText("Could not save: " + ex.getMessage());
        }
    }

    /** the figure as an image at FIGURE_DPI (charts drawn at full resolution, not a scaled screenshot) */
    java.awt.image.BufferedImage renderFigure() {
        int W = (int) Math.round(FIGURE_WIDTH_IN*FIGURE_DPI), H = (int) Math.round(FIGURE_HEIGHT_IN*FIGURE_DPI);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            drawFigure(g, FIGURE_DPI/100.0);
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * draws the 2x2 figure (three charts + report) into g. The layout is defined at 100 units per inch;
     * scale converts to the device: FIGURE_DPI/100 for an image, 72/100 for PDF points.
     */
    private void drawFigure(Graphics2D g, double scale) {
        double w = FIGURE_WIDTH_IN*100, h = FIGURE_HEIGHT_IN*100, pad = 8, title = 22, header = 24;
        g.scale(scale, scale);
        g.setColor(SURFACE);
        g.fill(new Rectangle2D.Double(0, 0, w, h));
        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(String.format("%s   dmax %.1f \u212B   q %.4f\u2013%.4f \u212B\u207B\u00B9", datasetName(),
                lastResult.dmax, lastResult.qmin, lastResult.qmax), (float) (pad + 4), (float) (pad + 16));
        double top = header;
        double cw = (w - 4*pad)/3, ch = (h - top - 3*pad)/2, y2 = top + 2*pad + ch;
        Rectangle2D[] cells = {
                new Rectangle2D.Double(pad, top + pad, cw, ch),
                new Rectangle2D.Double(2*pad + cw, top + pad, cw, ch),
                new Rectangle2D.Double(3*pad + 2*cw, top + pad, cw, ch),
                new Rectangle2D.Double(pad, y2, cw, ch),
                new Rectangle2D.Double(2*pad + cw, y2, 2*cw + pad, ch)};
        String[] titles = {"dmax consensus", "dmax likelihood", "Ensemble P(r)", "qmax selection", "Report"};
        JFreeChart[] charts = {consensusChart, likelihoodChart, prChart, qmaxChart, null};
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 13);
        for (int i = 0; i < cells.length; i++) {
            Rectangle2D c = cells[i];
            g.setColor(INK);
            g.setFont(titleFont);
            g.drawString(titles[i], (float) (c.getX() + 4), (float) (c.getY() + 15));
            Rectangle2D area = new Rectangle2D.Double(c.getX(), c.getY() + title, c.getWidth(), c.getHeight() - title);
            if (charts[i] != null) {
                charts[i].draw(g, area);
            } else if (i == 3) {
                g.setColor(MUTED);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                g.drawString("qmax fixed by the user", (float) (area.getCenterX() - 60), (float) area.getCenterY());
            } else {
                drawReport(g, area);
            }
        }
    }

    private void drawReport(Graphics2D g, Rectangle2D area) {
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 10);
        g.setFont(mono);
        g.setColor(INK);
        FontMetrics fm = g.getFontMetrics();
        float y = (float) (area.getY() + fm.getAscent() + 4);
        for (String line : reportArea.getText().split("\n")) {
            if (y > area.getMaxY() - 2) break;
            g.drawString(line, (float) (area.getX() + 6), y);
            y += fm.getHeight();
        }
    }

    /**
     * one-page PDF with Apache PDFBox. If pdfbox-graphics2d (de.rototor.pdfbox.graphics2d) is on the
     * classpath the figure is drawn as vector graphics (charts and text stay sharp at any zoom);
     * otherwise it is embedded as a lossless FIGURE_DPI image. The vector library is looked up at
     * run time, so Scatter compiles with or without it.
     *
     * @return true if the figure was written as vector graphics
     */
    boolean writePdf(File file) throws java.io.IOException {
        float pw = (float) (FIGURE_WIDTH_IN*72), ph = (float) (FIGURE_HEIGHT_IN*72);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pw, ph));
            doc.addPage(page);
            PDFormXObject vector = vectorFigure(doc, pw, ph);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                if (vector != null) {
                    cs.drawForm(vector);
                } else {
                    PDImageXObject image = LosslessFactory.createFromImage(doc, renderFigure());
                    cs.drawImage(image, 0, 0, pw, ph);
                }
            }
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(String.format("FindDmax %s: dmax %.1f A, qmax %.4f 1/A", datasetName(), lastResult.dmax, lastResult.qmax));
            info.setCreator("Scatter FindDmax");
            info.setCreationDate(java.util.Calendar.getInstance());
            doc.save(file);
            return vector != null;
        }
    }

    /** the figure as a PDF form via pdfbox-graphics2d (reflection), or null if that library is absent */
    private PDFormXObject vectorFigure(PDDocument doc, float pw, float ph) {
        Class<?> cls;
        try {
            cls = Class.forName("de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D");
        } catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
        try {
            Graphics2D g = (Graphics2D) cls.getConstructor(PDDocument.class, float.class, float.class).newInstance(doc, pw, ph);
            try {
                drawFigure(g, 0.72);                  // 100 layout units per inch -> 72 points per inch
            } finally {
                g.dispose();
            }
            return (PDFormXObject) cls.getMethod("getXFormObject").invoke(g);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;                              // fall back to the image
        }
    }

    /* ------------------------------------------------------------------ styling */

    private static void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(SURFACE);
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(SURFACE);
            chart.getLegend().setItemPaint(INK);
        }
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(SURFACE);
        plot.setDomainGridlinePaint(GRID);
        plot.setRangeGridlinePaint(GRID);
        plot.setOutlineVisible(false);
        plot.getDomainAxis().setTickLabelPaint(MUTED);
        plot.getDomainAxis().setLabelPaint(INK);
        plot.getRangeAxis().setTickLabelPaint(MUTED);
        plot.getRangeAxis().setLabelPaint(INK);
    }

    private static ValueMarker labelledMarker(double x, String label, Color color, boolean dotted) {
        BasicStroke stroke = dotted
                ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{2f, 3f}, 0f)
                : new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 4f}, 0f);
        ValueMarker m = new ValueMarker(x, color, stroke);
        m.setLabel(label);
        m.setLabelPaint(color);
        m.setLabelAnchor(dotted ? RectangleAnchor.BOTTOM_RIGHT : RectangleAnchor.TOP_RIGHT);
        m.setLabelTextAnchor(dotted ? TextAnchor.BOTTOM_LEFT : TextAnchor.TOP_LEFT);
        return m;
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** previous code path compatibility: the suggested values after a run (NaN before) */
    public double getSuggestedDmax() { return lastResult == null ? Double.NaN : lastResult.dmax; }
    public double getSuggestedQmax() { return lastResult == null ? Double.NaN : lastResult.qmax; }
    public XYSeries getEnsembleDistribution() {
        return (lastResult == null || lastResult.ensemble == null) ? null : lastResult.ensemble.getDistributionForFitting();
    }
}
