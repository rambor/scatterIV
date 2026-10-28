package version4.FormFactors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import version4.FormFactors.LangevinFitter;
import version4.FormFactors.LangevinResult;
import version4.FormFactors.LangevinSettings;
import version4.FormFactors.PolymerAnalysis;
import version4.FormFactors.ParameterProfile;
import version4.FormFactors.GelModels;
import version4.FormFactors.SpeciesProfile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * SCATTER panel for polydisperse form-factor distribution fitting (mean-field Langevin particle flow).
 *
 * <p>Pick a dataset from the loaded collection, a model and its parameters, press Run. Results appear in the tabs:
 * fit + residuals, 1-D distributions (volume / number / intensity weighting), joint (a, c) map, convergence,
 * the empty-fraction profile (core-shell mixtures) and a text report (REMARK 265 lines). Save writes CSV files,
 * the report and PNGs of every chart.</p>
 *
 * <p>In SCATTER it is created by the {@code FormFactors} class (default package, next to RealSpacePr), whose
 * {@code getPanel()} returns it for the empty holder JPanel of the main form.</p>
 */
public class FormFactorPanel extends JPanel {

    private final DatasetProvider provider;
    private final int cores;
    private final String version;

    // dataset
    private final JComboBox<String> datasetBox = new JComboBox<>();
    private final JLabel datasetInfo = new JLabel(" ");
    private final JButton refresh = new JButton("Refresh");
    private final JTextField qminField = Fields.num(""), qmaxField = Fields.num("");
    private double dataQmin = Double.NaN, dataQmax = Double.NaN;

    // model
    private final ModelSettingsPanel model = new ModelSettingsPanel();

    // fit options
    private final JTextField particles = Fields.num("1000"), replicas = Fields.num("2"),
            quench = Fields.num("3000"), sample = Fields.num("2000"), dmaxField = Fields.num("");
    private final JCheckBox background = new JCheckBox("Fit constant background", true),
            calibrate = new JCheckBox("Calibrate errors (Shannon fit)", true),
            rebin = new JCheckBox("Rebin to Shannon channels", true);
    private final JComboBox<String> dmaxMode = new JComboBox<>(new String[]{"Estimate", "From P(r) model", "Manual"});

    // run
    private final JButton runButton = new JButton("Run"), stopButton = new JButton("Stop"), saveButton = new JButton("Save…");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel status = new JLabel(" ");
    // optional: SCATTER's main-window progress bar and status label, updated alongside the panel's own
    private JProgressBar mainProgress;
    private JLabel mainStatus;
    private java.util.function.Supplier<String> workingDirectory = () -> null;

    // results
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel fitTab = new JPanel(new BorderLayout()), distTab = new JPanel(new BorderLayout()),
            jointTab = new JPanel(new BorderLayout()), convTab = new JPanel(new BorderLayout()),
            profileTab = new JPanel(new BorderLayout());
    private final JTextArea report = new JTextArea();
    private final JComboBox<FormFactorCharts.Weighting> weighting = new JComboBox<>(FormFactorCharts.Weighting.values());
    private final JComboBox<String> pairBox = new JComboBox<>();

    // polymer analysis (independent of the distribution fit)
    private final JPanel polymerTab = new JPanel(new BorderLayout());
    private final JPanel polymerCharts = new JPanel(new GridLayout(2, 2, 6, 6));
    private final JTextArea polymerText = new JTextArea();
    private final JButton analyseButton = new JButton("Analyse"), useNu = new JButton("Use ν in excluded-volume model"),
            useKuhn = new JButton("Use b in wormlike model");
    private final JLabel polymerInfo = new JLabel(" ");
    private PolymerAnalysis.Result polymerResult;
    private String polymerKey;
    private final java.util.Map<String, JFreeChart> polymerChartMap = new java.util.LinkedHashMap<>();

    // gel / network fits (independent of the distribution fit)
    private final JPanel gelTab = new JPanel(new BorderLayout());
    private final JPanel gelCenter = new JPanel(new BorderLayout());
    private final JTextArea gelText = new JTextArea();
    private final JComboBox<Object> gelModelBox = new JComboBox<>();
    private final JComboBox<String> gelShowBox = new JComboBox<>();
    private final JCheckBox gelBackground = new JCheckBox("Fit background", true);
    private final JButton gelFitButton = new JButton("Fit");
    private final JLabel gelInfo = new JLabel(" ");
    private java.util.List<GelModels.Fit> gelFits;
    private double[][] gelData;
    private JFreeChart gelChart;
    private String gelHeader = "";
    private static final String COMPARE_ALL = "Compare all models (rank by BIC)";

    private Worker worker;
    private LangevinResult result;
    private SpeciesProfile.Profile profile;
    private ParameterProfile.Profile paramProfile;
    private String resultTitle = "";
    private final java.util.Map<String, JFreeChart> charts = new java.util.LinkedHashMap<>();

    public FormFactorPanel(DatasetProvider provider, int cpuCores, String version) {
        super(new BorderLayout());
        this.provider = provider;
        this.cores = Math.max(1, cpuCores);
        this.version = version == null ? "" : version;

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(4, 4, 4, 4));
        controls.add(rangeBox());
        controls.add(model);
        controls.add(optionsBox());
        controls.add(runBox());
        for (Component c : controls.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(controls, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        // the parameter column keeps its natural width; the plots get the rest of the window
        int leftWidth = controls.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width + 6;
        scroll.setPreferredSize(new Dimension(leftWidth, 600));
        scroll.setMinimumSize(new Dimension(leftWidth, 200));
        tabs.setMinimumSize(new Dimension(300, 200));

        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Fit", fitTab);
        tabs.addTab("Distributions", distTab);
        tabs.addTab("Joint", jointTab);
        tabs.addTab("Convergence", convTab);
        tabs.addTab("Profile", profileTab);
        tabs.addTab("Report", new JScrollPane(report));
        tabs.addTab("Polymer", polymerTab);
        buildPolymerTab();
        tabs.addTab("Gel", gelTab);
        buildGelTab();
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == polymerTab && worker == null && !currentPolymerKey().equals(polymerKey)) analysePolymer();
        });
        clearResults("Select a dataset and a model, then press Run");

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, tabs);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        add(datasetBar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        weighting.addActionListener(e -> showDistributions());
        pairBox.addActionListener(e -> showJoint());
        model.addModelChangeListener(() -> { if (!Double.isNaN(dataQmax)) suggestBounds(); });

        // re-read the collection whenever the panel becomes visible (files are loaded in the Analysis panel)
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) refreshDatasets();
        });
        refreshDatasets();
    }

    // ------------------------------------------------------------------ control boxes

    /** full-width bar above both panels: dataset selector, Refresh, summary of the selected dataset */
    private JPanel datasetBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xd0d7de)), new EmptyBorder(6, 8, 6, 8)));
        refresh.setToolTipText("Re-read the datasets loaded in the Analysis panel");
        refresh.addActionListener(e -> refreshDatasets());
        // the combo takes the free width; long names are cut with "…" and shown in full as tooltip / in the list
        datasetBox.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXXX");
        datasetBox.setMinimumSize(new Dimension(160, datasetBox.getPreferredSize().height));
        datasetBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, focus);
                l.setToolTipText(value == null ? null : value.toString());
                return l;
            }
        });
        datasetBox.addActionListener(e -> {
            Object v = datasetBox.getSelectedItem();
            datasetBox.setToolTipText(v == null ? null : v.toString());
            datasetChanged();
        });
        // let the open list be wider than the combo so long names can be read
        datasetBox.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                Object child = datasetBox.getAccessibleContext().getAccessibleChild(0);
                if (!(child instanceof javax.swing.plaf.basic.BasicComboPopup)) return;
                javax.swing.plaf.basic.BasicComboPopup popup = (javax.swing.plaf.basic.BasicComboPopup) child;
                JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, popup.getList());
                int w = Math.max(datasetBox.getWidth(), popup.getList().getPreferredSize().width + 30);
                w = Math.min(w, Math.max(datasetBox.getWidth(), FormFactorPanel.this.getWidth() - 40));
                if (sp != null && w > sp.getPreferredSize().width) {
                    Dimension d = sp.getPreferredSize();
                    sp.setPreferredSize(new Dimension(w, d.height));
                    sp.setMaximumSize(new Dimension(w, d.height));
                }
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { }
        });
        datasetInfo.setForeground(FormFactorCharts.MUTED);

        JLabel title = new JLabel("Dataset");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 8);
        c.anchor = GridBagConstraints.LINE_START;
        bar.add(title, c);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        bar.add(datasetBox, c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        bar.add(refresh, c);
        c.insets = new Insets(0, 8, 0, 0);
        bar.add(datasetInfo, c);
        return bar;
    }

    /** q-range of the fit (top of the parameter column) */
    private JPanel rangeBox() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Data range"));
        Fields.Form f = new Fields.Form(p);
        JButton full = new JButton("Full range");
        full.addActionListener(e -> resetRange());
        f.row("q min / max (Å⁻¹)", Fields.flow(qminField, new JLabel("-"), qmaxField, full),
                "Fitting range. The size bounds of the model are suggested from it (pi/qmax .. pi/qmin).");
        return p;
    }

    private JPanel optionsBox() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Fit options"));
        Fields.Form f = new Fields.Form(p);
        f.row("Particles", Fields.flow(particles), "Particles per replica (each carries an equal share of I(0)); 500-2000");
        f.row("Replicas", Fields.flow(replicas), "Independent runs; the band on the distributions is their spread. 2-4");
        f.row("Quench / sample steps", Fields.pair(quench, sample),
                "Quench (annealing to the best fit) and sampling steps; equilibration = quench / 2");
        f.full(background);
        f.full(calibrate);
        f.full(rebin);
        f.row("Dmax", Fields.flow(dmaxMode, dmaxField, new JLabel("Å")),
                "Maximum dimension used for error calibration and Shannon rebinning. 'From P(r)' uses the dataset's real-space model.");
        Runnable en = () -> {
            boolean need = calibrate.isSelected() || rebin.isSelected();
            dmaxMode.setEnabled(need);
            dmaxField.setEnabled(need && dmaxMode.getSelectedIndex() == 2);
        };
        dmaxMode.addActionListener(e -> en.run());
        calibrate.addActionListener(e -> en.run());
        rebin.addActionListener(e -> en.run());
        en.run();
        return p;
    }

    private JPanel runBox() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new TitledBorder("Run"));
        Fields.Form f = new Fields.Form(p);
        stopButton.setEnabled(false);
        saveButton.setEnabled(false);
        runButton.addActionListener(e -> run());
        stopButton.addActionListener(e -> stop());
        saveButton.addActionListener(e -> save());
        f.full(Fields.flow(runButton, stopButton, saveButton));
        progress.setStringPainted(true);
        barString("");
        f.full(progress);
        status.setForeground(FormFactorCharts.MUTED);
        f.full(status);
        return p;
    }

    // ------------------------------------------------------------------ datasets

    /** re-reads the collection, keeping the current selection by name */
    public void refreshDatasets() {
        if (worker != null) return;     // don't change the dataset under a running fit
        Object sel = datasetBox.getSelectedItem();
        int n = 0;
        try { n = provider.count(); } catch (RuntimeException ignored) { }
        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
        for (int i = 0; i < n; i++) {
            String name;
            try { name = provider.name(i); } catch (RuntimeException e) { name = null; }
            m.addElement((i + 1) + ": " + (name == null || name.isEmpty() ? "dataset " + (i + 1) : name));
        }
        datasetBox.setModel(m);
        if (n == 0) {
            datasetInfo.setText("No datasets loaded – load files in the Analysis panel");
            runButton.setEnabled(false);
            return;
        }
        runButton.setEnabled(true);
        if (sel != null && m.getIndexOf(sel) >= 0) datasetBox.setSelectedItem(sel);
        else datasetBox.setSelectedIndex(0);
        datasetChanged();
    }

    private void datasetChanged() {
        int i = datasetBox.getSelectedIndex();
        if (i < 0) return;
        double[][] all;
        try {
            all = provider.data(i);
        } catch (RuntimeException e) {
            datasetInfo.setText("Cannot read dataset: " + e.getMessage());
            return;
        }
        if (all == null || all[0].length == 0) {
            datasetInfo.setText("Dataset is empty");
            return;
        }
        double lo = Double.POSITIVE_INFINITY, hi = 0;
        for (double x : all[0]) if (x > 0) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        boolean sameRange = lo == dataQmin && hi == dataQmax;
        dataQmin = lo;
        dataQmax = hi;
        double dm = pofrDmax(i);
        datasetInfo.setText(String.format("%d points · q %.4f – %.4f Å⁻¹%s", all[0].length, lo, hi,
                dm > 0 ? String.format(" · P(r) Dmax %.0f Å", dm) : ""));
        if (dm > 0 && dmaxMode.getSelectedIndex() == 0) dmaxMode.setSelectedIndex(1);
        if (!sameRange) resetRange();
        if (polymerResult != null && !currentPolymerKey().equals(polymerKey)) clearPolymer("Dataset or range changed: press Analyse");
        if (gelFits != null) clearGel("Dataset changed: press Fit");
    }

    private void resetRange() {
        if (Double.isNaN(dataQmin)) return;
        qminField.setText(String.format("%.5f", dataQmin));
        qmaxField.setText(String.format("%.5f", dataQmax));
        suggestBounds();
    }

    private void suggestBounds() {
        try {
            model.suggestBounds(Fields.pos(qminField, "q min"), Fields.pos(qmaxField, "q max"));
        } catch (IllegalArgumentException ignored) { }
    }

    private double pofrDmax(int i) {
        try {
            return provider.dmax(i);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** points inside [qmin, qmax] with a positive error, sorted by q */
    static double[][] extract(double[][] d, double qmin, double qmax) {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < d[0].length; i++) {
            double x = d[0][i];
            if (x < qmin || x > qmax) continue;
            double y = d[1][i], e = d[2][i];
            if (!(e > 0) || Double.isNaN(y) || Double.isInfinite(y)) continue;
            pts.add(new double[]{x, y, e});
        }
        pts.sort((a, b) -> Double.compare(a[0], b[0]));
        double[][] out = new double[3][pts.size()];
        for (int i = 0; i < pts.size(); i++) for (int k = 0; k < 3; k++) out[k][i] = pts.get(i)[k];
        return out;
    }

    // ------------------------------------------------------------------ run / stop

    private void run() {
        if (worker != null) return;
        int idx = datasetBox.getSelectedIndex();
        if (idx < 0) return;
        ModelSettingsPanel.FitSpec spec;
        double[][] data;
        try {
            double qmin = Fields.pos(qminField, "q min"), qmax = Fields.pos(qmaxField, "q max");
            Fields.order(qmin, qmax, "q range");
            data = extract(provider.data(idx), qmin, qmax);
            if (data[0].length < 20) throw new IllegalArgumentException("Only " + data[0].length + " usable points in the q-range");

            LangevinSettings s = new LangevinSettings();
            s.particles = Fields.posInt(particles, "particles");
            s.replicas = Fields.posInt(replicas, "replicas");
            s.annealSteps = Fields.posInt(quench, "quench steps");
            s.equilibrationSteps = Math.max(200, s.annealSteps / 2);
            s.sampleSteps = Fields.posInt(sample, "sample steps");
            s.fitBackground = background.isSelected();
            s.calibrateErrors = calibrate.isSelected();
            s.rebin = rebin.isSelected();
            s.threads = cores;
            s.dmax = Double.NaN;
            if (s.calibrateErrors || s.rebin) {
                if (dmaxMode.getSelectedIndex() == 1) {
                    double dm = pofrDmax(idx);
                    if (!(dm > 1)) throw new IllegalArgumentException("This dataset has no P(r) model yet: choose 'Estimate' or 'Manual' Dmax");
                    s.dmax = dm;
                } else if (dmaxMode.getSelectedIndex() == 2) {
                    s.dmax = Fields.pos(dmaxField, "Dmax");
                }
            }
            if (s.particles < 100) throw new IllegalArgumentException("Use at least 100 particles");
            spec = model.build(s);
            for (int k = 0; k < spec.min.length; k++)
                if (spec.max[k] > 4 * Math.PI / qmin)
                    say(String.format("Note: upper bound %.0f Å is beyond what q min resolves (~%.0f Å)", spec.max[k], Math.PI / qmin));
            resultTitle = provider.name(idx) + " — " + spec.description;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "FormFactors", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setRunning(true);
        clearResults(spec.profileScan || spec.paramScan != null ? "Profile scan running…" : "Fit running…");
        worker = new Worker(data, spec);
        worker.execute();
    }

    private void stop() {
        if (worker != null) {
            worker.stopFit();
            say("Stopping…");
            stopButton.setEnabled(false);
        }
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        saveButton.setEnabled(!running && (result != null || polymerResult != null || gelFits != null));
        datasetBox.setEnabled(!running);
        refresh.setEnabled(!running);
    }

    /** runs one fit, or a full profile scan, off the event thread */
    private final class Worker extends SwingWorker<Object, Void> {
        final double[][] data;
        final ModelSettingsPanel.FitSpec spec;
        volatile LangevinFitter fitter;
        final SpeciesProfile.Control ctl = new SpeciesProfile.Control();
        volatile boolean stopped;
        final long t0 = System.nanoTime();

        Worker(double[][] data, ModelSettingsPanel.FitSpec spec) {
            this.data = data;
            this.spec = spec;
        }

        void stopFit() {
            stopped = true;
            ctl.cancel();
            LangevinFitter f = fitter;
            if (f != null) f.cancel();
        }

        @Override
        protected Object doInBackground() {
            final int R = Math.max(1, spec.settings.replicas);
            if (!spec.profileScan && spec.paramScan == null) {
                fitter = new LangevinFitter(data[0], data[1], data[2], spec.family, spec.min, spec.max, spec.settings);
                if (stopped) fitter.cancel();
                return fitter.fit((rep, step, total, phase, T, chi2) ->
                        report((rep * (double) total + step + 1) / ((double) R * total),
                                String.format("replica %d/%d · %s · red. χ² %.3f", rep + 1, R, phase, chi2)));
            }
            ctl.inner = (rep, step, total, phase, T, chi2) -> {
                int n = Math.max(1, ctl.pointTotal), i = Math.min(ctl.pointIndex, n - 1);
                double within = (rep * (double) total + step + 1) / ((double) R * total);
                report((i + within) / n, String.format("point %d/%d · replica %d/%d · %s · red. χ² %.3f", i + 1, n, rep + 1, R, phase, chi2));
            };
            if (spec.paramScan != null) {
                ModelSettingsPanel.ParamScan ps = spec.paramScan;
                return ParameterProfile.run(data[0], data[1], data[2], spec.family, spec.min, spec.max, spec.settings,
                        ps.parameter, ps.unit, ps.setter, ps.grid, ps.logScale, ps.repeats, ctl);
            }
            return SpeciesProfile.run(data[0], data[1], data[2], spec.family, spec.min, spec.max, spec.settings,
                    spec.profileStep, spec.profileRepeats, null, ctl);
        }

        private long lastUpdate;

        private void report(double frac, String text) {
            long now = System.nanoTime();
            if (now - lastUpdate < 100_000_000L && frac < 1) return;   // ~10 updates per second
            lastUpdate = now;
            double el = (now - t0) * 1e-9;
            String eta = frac > 0.02 ? String.format(" · %s left", time(el * (1 - frac) / frac)) : "";
            int v = (int) Math.round(1000 * Math.max(0, Math.min(1, frac)));
            SwingUtilities.invokeLater(() -> {
                barValue(v);
                barString(String.format("%.0f %%%s", v / 10.0, eta));
                say(text);
            });
        }

        @Override
        protected void done() {
            worker = null;
            Object out = null;
            String err = null;
            try {
                out = get();
            } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                out = null;
            } catch (ExecutionException e) {
                Throwable c = e.getCause() == null ? e : e.getCause();
                err = c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
                c.printStackTrace();
            }
            barValue(0);
            if (err != null) {
                barString("Failed");
                say(err);
                clearResults("Fit failed: " + err);
                JOptionPane.showMessageDialog(FormFactorPanel.this, err, "FormFactors", JOptionPane.ERROR_MESSAGE);
            } else if (out == null || stopped) {
                barString("Stopped");
                say(" ");
                clearResults("Stopped – no result");
            } else {
                barString(String.format("Finished in %s", time((System.nanoTime() - t0) * 1e-9)));
                profile = null;
                paramProfile = null;
                if (out instanceof SpeciesProfile.Profile) {
                    profile = (SpeciesProfile.Profile) out;
                    result = profile.best.result;
                } else if (out instanceof ParameterProfile.Profile) {
                    paramProfile = (ParameterProfile.Profile) out;
                    result = paramProfile.best.result;
                } else {
                    result = (LangevinResult) out;
                }
                say(result.warning != null ? "⚠ " + firstLine(result.warning) : String.format("red. χ² %.3f", result.chi2Reduced));
                showResults();
            }
            setRunning(false);
        }
    }

    /** chart titles: shorten a long file name in the middle so the title stays on one line */
    private static String chartTitle(String t) {
        int sep = t.indexOf(" — ");
        String name = sep < 0 ? t : t.substring(0, sep), rest = sep < 0 ? "" : t.substring(sep);
        int max = 48;
        if (name.length() > max) name = name.substring(0, max / 2 - 1) + "…" + name.substring(name.length() - (max / 2 - 1));
        return name + rest;
    }

    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    private static String time(double sec) {
        if (sec < 90) return String.format("%.0f s", sec);
        if (sec < 5400) return String.format("%.0f min", sec / 60);
        return String.format("%.1f h", sec / 3600);
    }

    // ------------------------------------------------------------------ results

    private void clearResults(String message) {
        result = null;
        profile = null;
        paramProfile = null;
        charts.clear();
        for (JPanel p : new JPanel[]{fitTab, distTab, jointTab, convTab, profileTab}) {
            p.removeAll();
            p.add(new ChartPanel(FormFactorCharts.placeholder(message)), BorderLayout.CENTER);
            p.revalidate();
            p.repaint();
        }
        report.setText("");
        tabs.setEnabledAt(4, false);
        tabs.setEnabledAt(2, true);
    }

    /** builds every chart for the current result (also used by tests and for saving) */
    void showResults() {
        charts.clear();
        // fit tab: for a profile scan, also show two rejected fractions (best ± ~0.2)
        List<LangevinResult> extra = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (profile != null) {
            SpeciesProfile.Point lo = null, hi = null;
            for (SpeciesProfile.Point p : profile.points) {
                double dx = p.numberFraction - profile.bestNumberFraction;
                if (p.result == null) continue;
                if (dx <= -0.15 && (lo == null || p.numberFraction > lo.numberFraction)) lo = p;
                if (dx >= 0.15 && (hi == null || p.numberFraction < hi.numberFraction)) hi = p;
            }
            for (SpeciesProfile.Point p : new SpeciesProfile.Point[]{lo, hi})
                if (p != null) {
                    extra.add(p.result);
                    labels.add(String.format("%s %.2f  (Δχ² %.0f)", profile.speciesName, p.numberFraction, p.chi2Total - profile.bestChi2Total));
                }
        }
        setChart("fit", fitTab, FormFactorCharts.fitChart(result, chartTitle(resultTitle), extra, labels));
        showDistributions();

        DefaultComboBoxModel<String> pm = new DefaultComboBoxModel<>();
        if (result.pairs != null)
            for (int[] pr : result.pairs) pm.addElement(result.parameterNames[pr[0]] + " vs " + result.parameterNames[pr[1]]);
        pairBox.setModel(pm);
        tabs.setEnabledAt(2, pm.getSize() > 0);
        showJoint();
        setChart("convergence", convTab, FormFactorCharts.convergenceChart(result));
        if (profile != null || paramProfile != null) {
            JFreeChart full = profile != null ? FormFactorCharts.profileChart(profile, false) : FormFactorCharts.parameterProfileChart(paramProfile, false);
            JFreeChart zoom = profile != null ? FormFactorCharts.profileChart(profile, true) : FormFactorCharts.parameterProfileChart(paramProfile, true);
            charts.put("profile", full);
            charts.put("profile_zoom", zoom);
            JPanel two = new JPanel(new GridLayout(1, 2, 6, 0));
            two.setBackground(Color.WHITE);
            two.add(new ChartPanel(full));
            two.add(new ChartPanel(zoom));
            profileTab.removeAll();
            profileTab.add(two, BorderLayout.CENTER);
            profileTab.revalidate();
            profileTab.repaint();
            tabs.setEnabledAt(4, true);
        } else tabs.setEnabledAt(4, false);

        StringBuilder sb = new StringBuilder();
        sb.append("REMARK 265 FORMFACTORS (MEAN-FIELD LANGEVIN)   SCATTER ").append(version).append('\n');
        sb.append("REMARK 265 DATASET/MODEL : ").append(resultTitle).append('\n');
        if (profile != null) sb.append(profile.toRemarks()).append('\n');
        if (paramProfile != null) sb.append(paramProfile.toRemarks()).append('\n');
        sb.append(result.toRemarks());
        report.setText(sb.toString());
        report.setCaretPosition(0);
        tabs.setSelectedIndex(profile != null || paramProfile != null ? 4 : 0);
        saveButton.setEnabled(true);
    }

    private void showDistributions() {
        if (result == null) return;
        FormFactorCharts.Weighting w = (FormFactorCharts.Weighting) weighting.getSelectedItem();
        JPanel grid = new JPanel(new GridLayout(0, result.dim <= 1 ? 1 : 2, 6, 6));
        grid.setBackground(Color.WHITE);
        charts.keySet().removeIf(k -> k.startsWith("distribution"));
        for (int k = 0; k < result.dim; k++) {
            JFreeChart c = FormFactorCharts.distributionChart(result, k, w);
            charts.put("distribution_" + result.parameterNames[k].replaceAll("\\W+", ""), c);
            grid.add(new ChartPanel(c));
        }
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Weighting"));
        top.add(weighting);
        if (result.speciesNames != null && result.speciesNumberFraction != null) {
            StringBuilder sb = new StringBuilder("   ");
            for (int s = 0; s < result.speciesNames.length; s++)
                sb.append(String.format("%s: %.1f %% by number, %.1f %% by volume   ", result.speciesNames[s],
                        100 * result.speciesNumberFraction[s], 100 * result.speciesVolumeFraction[s]));
            top.add(new JLabel(sb.toString()));
        }
        distTab.removeAll();
        distTab.add(top, BorderLayout.NORTH);
        distTab.add(grid, BorderLayout.CENTER);
        distTab.revalidate();
        distTab.repaint();
    }

    private void showJoint() {
        if (result == null || result.pairs == null || pairBox.getSelectedIndex() < 0) return;
        JFreeChart c = FormFactorCharts.jointChart(result, pairBox.getSelectedIndex());
        charts.put("joint", c);
        jointTab.removeAll();
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Pair"));
        top.add(pairBox);
        jointTab.add(top, BorderLayout.NORTH);
        jointTab.add(new ChartPanel(c), BorderLayout.CENTER);
        jointTab.revalidate();
        jointTab.repaint();
    }

    private void setChart(String key, JPanel tab, JFreeChart c) {
        charts.put(key, c);
        tab.removeAll();
        tab.add(new ChartPanel(c), BorderLayout.CENTER);
        tab.revalidate();
        tab.repaint();
    }

    // ------------------------------------------------------------------ polymer analysis

    private void buildPolymerTab() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        analyseButton.setToolTipText("Guinier, Debye, excluded-volume and wormlike single-chain fits, high-q power law and "
                + "dimensionless Kratky for the selected dataset and q range");
        analyseButton.addActionListener(e -> analysePolymer());
        useNu.setToolTipText("Copy the fitted Flory exponent into the excluded-volume chain model and select it");
        useNu.addActionListener(e -> {
            if (polymerResult == null || !polymerResult.excludedVolume.ok) return;
            model.setFloryExponent(polymerResult.excludedVolume.values[2]);
            model.selectModel(ModelSettingsPanel.Model.EV_CHAIN);
            say(String.format("Excluded-volume model: ν set to %.3f", polymerResult.excludedVolume.values[2]));
        });
        useKuhn.setToolTipText("Copy the fitted Kuhn length into the wormlike chain model and select it");
        useKuhn.addActionListener(e -> {
            if (polymerResult == null || !polymerResult.wormlike.ok) return;
            model.setKuhnLength(polymerResult.wormlike.values[2]);
            model.selectModel(ModelSettingsPanel.Model.WORMLIKE);
            say(String.format("Wormlike model: b set to %.1f Å", polymerResult.wormlike.values[2]));
        });
        bar.add(analyseButton);
        bar.add(useNu);
        bar.add(useKuhn);
        polymerInfo.setForeground(FormFactorCharts.MUTED);
        bar.add(polymerInfo);
        polymerTab.add(bar, BorderLayout.NORTH);
        polymerText.setEditable(false);
        polymerText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        polymerText.setLineWrap(true);
        polymerText.setWrapStyleWord(true);
        polymerText.setBorder(new EmptyBorder(6, 8, 6, 8));
        polymerCharts.setBackground(Color.WHITE);
        polymerTab.add(polymerCharts, BorderLayout.CENTER);
        clearPolymer("Press Analyse (runs on the selected dataset and q range)");
    }

    private String currentPolymerKey() {
        return datasetBox.getSelectedItem() + "|" + qminField.getText().trim() + "|" + qmaxField.getText().trim();
    }

    private void clearPolymer(String message) {
        polymerResult = null;
        polymerKey = null;
        polymerChartMap.clear();
        polymerCharts.removeAll();
        polymerCharts.setLayout(new BorderLayout());
        polymerCharts.add(new ChartPanel(FormFactorCharts.placeholder(message)), BorderLayout.CENTER);
        polymerText.setText("");
        useNu.setEnabled(false);
        useKuhn.setEnabled(false);
        polymerInfo.setText(" ");
        polymerCharts.revalidate();
        polymerCharts.repaint();
    }

    /** runs the model-free / single-chain analysis on the selected dataset and q range */
    void analysePolymer() {
        int idx = datasetBox.getSelectedIndex();
        if (idx < 0) return;
        double[][] d;
        try {
            double qmin = Fields.pos(qminField, "q min"), qmax = Fields.pos(qmaxField, "q max");
            Fields.order(qmin, qmax, "q range");
            d = extract(provider.data(idx), qmin, qmax);
            if (d[0].length < 10) throw new IllegalArgumentException("Only " + d[0].length + " usable points in the q-range");
        } catch (IllegalArgumentException ex) {
            clearPolymer(ex.getMessage());
            return;
        }
        final String key = currentPolymerKey();
        analyseButton.setEnabled(false);
        polymerInfo.setText("Analysing…");
        new SwingWorker<PolymerAnalysis.Result, Void>() {
            @Override protected PolymerAnalysis.Result doInBackground() { return PolymerAnalysis.analyse(d[0], d[1], d[2]); }
            @Override protected void done() {
                analyseButton.setEnabled(true);
                try {
                    polymerResult = get();
                    polymerKey = key;
                    showPolymer();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    clearPolymer("Analysis failed: " + c.getMessage());
                }
            }
        }.execute();
    }

    private void showPolymer() {
        PolymerAnalysis.Result r = polymerResult;
        polymerChartMap.clear();
        polymerChartMap.put("polymer_kratky", FormFactorCharts.kratkyChart(r));
        polymerChartMap.put("polymer_fits", FormFactorCharts.polymerFitChart(r));
        polymerChartMap.put("polymer_guinier", FormFactorCharts.guinierChart(r));
        polymerCharts.removeAll();
        polymerCharts.setLayout(new GridLayout(2, 2, 6, 6));
        for (JFreeChart c : polymerChartMap.values()) polymerCharts.add(new ChartPanel(c));
        polymerText.setText(r.summary());
        polymerText.setCaretPosition(0);
        polymerCharts.add(new JScrollPane(polymerText));
        polymerCharts.revalidate();
        polymerCharts.repaint();
        boolean nuOk = r.excludedVolume.ok && !r.excludedVolume.poor, bOk = r.wormlike.ok && !r.wormlike.poor;
        useNu.setEnabled(nuOk);
        useKuhn.setEnabled(bOk);
        polymerInfo.setText(nuOk ? String.format("ν = %.3f ± %.3f", r.excludedVolume.values[2], r.excludedVolume.errors[2])
                + (bOk ? String.format(" · b = %.1f Å", r.wormlike.values[2]) : "") : "single-chain models do not describe these data");
        saveButton.setEnabled(worker == null);
    }

    public PolymerAnalysis.Result getPolymerResult() { return polymerResult; }

    // ------------------------------------------------------------------ gel / network fits

    private void buildGelTab() {
        DefaultComboBoxModel<Object> m = new DefaultComboBoxModel<>();
        m.addElement(COMPARE_ALL);
        for (GelModels.Model g : GelModels.Model.values()) m.addElement(g);
        gelModelBox.setModel(m);
        gelModelBox.setToolTipText("<html>Shape-independent network models (SasView equations):<br>"
                + "Ornstein–Zernike: mesh size ξ · Debye–Bueche: frozen-in inhomogeneities a · OZ + DB: both<br>"
                + "Gauss–Lorentz gel · Gel fit (fractal dimension D + clusters) · Correlation length (Hammouda)<br>"
                + "Two Lorentzian · Broad peak (charged gels) · Unified Beaucage (2 levels)</html>");
        gelFitButton.addActionListener(e -> fitGel());
        gelShowBox.addActionListener(e -> showGel());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.add(new JLabel("Model"));
        bar.add(gelModelBox);
        bar.add(gelBackground);
        bar.add(gelFitButton);
        bar.add(new JLabel("  Show"));
        bar.add(gelShowBox);
        gelInfo.setForeground(FormFactorCharts.MUTED);
        bar.add(gelInfo);
        gelTab.add(bar, BorderLayout.NORTH);
        gelText.setEditable(false);
        gelText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        gelText.setLineWrap(true);
        gelText.setWrapStyleWord(true);
        gelText.setBorder(new EmptyBorder(6, 8, 6, 8));
        JScrollPane ts = new JScrollPane(gelText);
        ts.setPreferredSize(new Dimension(420, 200));
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gelCenter, ts);
        sp.setResizeWeight(0.62);
        gelTab.add(sp, BorderLayout.CENTER);
        clearGel("Choose a model (or Compare all) and press Fit – runs on the selected dataset and q range");
    }

    private void clearGel(String message) {
        gelFits = null;
        gelChart = null;
        gelShowBox.setModel(new DefaultComboBoxModel<>());
        gelShowBox.setEnabled(false);
        gelCenter.removeAll();
        gelCenter.add(new ChartPanel(FormFactorCharts.placeholder(message)), BorderLayout.CENTER);
        gelText.setText("");
        gelInfo.setText(" ");
        gelCenter.revalidate();
        gelCenter.repaint();
    }

    /** fits the selected gel model (or all of them) to the selected dataset and q range */
    void fitGel() {
        int idx = datasetBox.getSelectedIndex();
        if (idx < 0) return;
        double[][] d;
        try {
            double qmin = Fields.pos(qminField, "q min"), qmax = Fields.pos(qmaxField, "q max");
            Fields.order(qmin, qmax, "q range");
            d = extract(provider.data(idx), qmin, qmax);
            if (d[0].length < 15) throw new IllegalArgumentException("Only " + d[0].length + " usable points in the q-range");
        } catch (IllegalArgumentException ex) {
            clearGel(ex.getMessage());
            return;
        }
        final Object sel = gelModelBox.getSelectedItem();
        final boolean bg = gelBackground.isSelected();
        final String name = String.valueOf(datasetBox.getSelectedItem());
        gelFitButton.setEnabled(false);
        gelInfo.setText(sel == COMPARE_ALL ? "Fitting all models…" : "Fitting…");
        new SwingWorker<java.util.List<GelModels.Fit>, Void>() {
            @Override protected java.util.List<GelModels.Fit> doInBackground() {
                if (sel == COMPARE_ALL) return GelModels.compareAll(d[0], d[1], d[2], bg);
                java.util.List<GelModels.Fit> one = new ArrayList<>();
                one.add(GelModels.fit((GelModels.Model) sel, d[0], d[1], d[2], bg));
                return one;
            }
            @Override protected void done() {
                gelFitButton.setEnabled(true);
                try {
                    java.util.List<GelModels.Fit> fits = get();
                    fits.removeIf(f -> !f.ok);
                    if (fits.isEmpty()) { clearGel("Fit failed"); return; }
                    gelFits = fits;
                    gelData = d;
                    DefaultComboBoxModel<String> sm = new DefaultComboBoxModel<>();
                    for (GelModels.Fit f : fits) sm.addElement(f.model.label);
                    gelShowBox.setModel(sm);
                    gelShowBox.setEnabled(fits.size() > 1);
                    gelShowBox.setSelectedIndex(0);
                    gelHeader = gelSummary(name, fits);
                    showGel();
                    saveButton.setEnabled(worker == null);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    clearGel("Fit failed: " + c.getMessage());
                }
            }
        }.execute();
    }

    /** dataset name and, when several models were fitted, the BIC ranking */
    private static String gelSummary(String name, java.util.List<GelModels.Fit> fits) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("\n\n");
        if (fits.size() > 1) {
            sb.append("Ranked by BIC (ΔBIC > 10: strong evidence against)\n");
            sb.append(String.format("   %-19s %8s %8s%n", "model", "red.χ²", "ΔBIC"));
            double b0 = fits.get(0).bic;
            for (int k = 0; k < fits.size(); k++) {
                GelModels.Fit f = fits.get(k);
                sb.append(String.format("%d. %-19s %8.2f %8.1f%n", k + 1, f.model.shortLabel(), f.chi2Reduced, f.bic - b0));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private void showGel() {
        if (gelFits == null || gelShowBox.getSelectedIndex() < 0) return;
        GelModels.Fit f = gelFits.get(gelShowBox.getSelectedIndex());
        gelChart = FormFactorCharts.gelFitChart(gelData[0], gelData[1], gelData[2], f,
                chartTitle(String.valueOf(datasetBox.getSelectedItem()) + " — " + f.model.label));
        gelCenter.removeAll();
        gelCenter.add(new ChartPanel(gelChart), BorderLayout.CENTER);
        gelCenter.revalidate();
        gelCenter.repaint();
        gelText.setText(gelHeader + f.summary()
                + (gelFits.size() > 1 ? "\n(choose another model under Show to see its fit and parameters)\n" : ""));
        gelText.setCaretPosition(0);
        gelInfo.setText(String.format("%s: red. χ² %.2f", f.model.label, f.chi2Reduced) + (f.note.isEmpty() ? "" : "  ⚠"));
    }

    public java.util.List<GelModels.Fit> getGelFits() { return gelFits; }

    // ------------------------------------------------------------------ save

    private void save() {
        if (result == null && polymerResult == null && gelFits == null) return;
        String wd = workingDirectory.get();
        JFileChooser ch = wd == null ? new JFileChooser() : new JFileChooser(wd);
        ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        ch.setDialogTitle("Folder for FormFactor results");
        if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String prefix = JOptionPane.showInputDialog(this, "File prefix", defaultPrefix());
        if (prefix == null || prefix.trim().isEmpty()) return;
        try {
            List<Path> files = saveTo(ch.getSelectedFile().toPath(), prefix.trim());
            say("Saved " + files.size() + " files to " + ch.getSelectedFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage(), "FormFactors", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String defaultPrefix() {
        int i = datasetBox.getSelectedIndex();
        String n = i >= 0 ? provider.name(i) : "formfactor";
        n = n.replaceAll("\\.[A-Za-z0-9]{1,4}$", "").replaceAll("[^A-Za-z0-9_.-]+", "_");
        return n + "_ff";
    }

    /** writes CSVs (fit, distributions, pairs, profile), the report and a PNG of every chart */
    public List<Path> saveTo(Path dir, String prefix) throws IOException {
        Files.createDirectories(dir);
        List<Path> out = new ArrayList<>();
        if (result != null) {
            out.addAll(result.writeCsv(dir, prefix));
            Path rem = dir.resolve(prefix + "_report.txt");
            Files.write(rem, report.getText().getBytes(StandardCharsets.UTF_8));
            out.add(rem);
        }
        if (gelFits != null) {
            StringBuilder sb = new StringBuilder("REMARK 265\nREMARK 265 GEL / NETWORK FITS\n");
            for (GelModels.Fit f : gelFits) sb.append(f.toRemarks());
            Path gp = dir.resolve(prefix + "_gel.txt");
            Files.write(gp, sb.toString().getBytes(StandardCharsets.UTF_8));
            out.add(gp);
            Path gc = dir.resolve(prefix + "_gel_fit.csv");
            GelModels.Fit f = gelFits.get(Math.max(0, gelShowBox.getSelectedIndex()));
            try (java.io.PrintWriter w = new java.io.PrintWriter(Files.newBufferedWriter(gc))) {
                w.println("q,I,sigma,fit");
                for (int j = 0; j < gelData[0].length; j++)
                    w.printf("%.6e,%.6e,%.6e,%.6e%n", gelData[0][j], gelData[1][j], gelData[2][j], f.curve[j]);
            }
            out.add(gc);
            if (gelChart != null) {
                File cf = dir.resolve(prefix + "_gel_fit.png").toFile();
                ChartUtilities.saveChartAsPNG(cf, gelChart, 1000, 750);
                out.add(cf.toPath());
            }
        }
        if (polymerResult != null) {
            Path pr = dir.resolve(prefix + "_polymer.txt");
            Files.write(pr, polymerResult.toRemarks().getBytes(StandardCharsets.UTF_8));
            out.add(pr);
            for (java.util.Map.Entry<String, JFreeChart> c : polymerChartMap.entrySet()) {
                File f = dir.resolve(prefix + "_" + c.getKey() + ".png").toFile();
                ChartUtilities.saveChartAsPNG(f, c.getValue(), 900, 650);
                out.add(f.toPath());
            }
        }
        if (profile != null) {
            Path pf = dir.resolve(prefix + "_profile.csv");
            profile.writeCsv(pf);
            out.add(pf);
        }
        if (paramProfile != null) {
            Path pf = dir.resolve(prefix + "_" + paramProfile.parameter + "_profile.csv");
            paramProfile.writeCsv(pf);
            out.add(pf);
        }
        for (java.util.Map.Entry<String, JFreeChart> c : charts.entrySet()) {
            File f = dir.resolve(prefix + "_" + c.getKey() + ".png").toFile();
            ChartUtilities.saveChartAsPNG(f, c.getValue(), 1000, c.getKey().equals("fit") ? 800 : 600);
            out.add(f.toPath());
        }
        return out;
    }

    // ------------------------------------------------------------------ SCATTER integration

    /** also show progress and messages on SCATTER's main-window bar and status label (either may be null) */
    public void setMainStatus(JProgressBar bar, JLabel label) {
        this.mainProgress = bar;
        this.mainStatus = label;
    }

    /** where the Save dialog starts, e.g. () -> WORKING_DIRECTORY.getWorkingDirectory() */
    public void setWorkingDirectory(java.util.function.Supplier<String> dir) {
        this.workingDirectory = dir == null ? () -> null : dir;
    }

    /** true while a fit or profile scan is running */
    public boolean isRunning() { return worker != null; }

    private void barValue(int permille) {
        progress.setValue(permille);
        if (mainProgress != null) {
            int lo = mainProgress.getMinimum(), hi = mainProgress.getMaximum();
            mainProgress.setValue(lo + (int) Math.round((hi - lo) * permille / 1000.0));
        }
    }

    private void barString(String s) {
        progress.setString(s);
        if (mainProgress != null && mainProgress.isStringPainted()) mainProgress.setString(s);
    }

    private void say(String s) {
        status.setText(s);
        if (mainStatus != null) mainStatus.setText(s == null || s.trim().isEmpty() ? "" : "FormFactors: " + s);
    }

    // accessors for tests / embedding
    public LangevinResult getResult() { return result; }
    public SpeciesProfile.Profile getProfile() { return profile; }
    JTabbedPane tabs() { return tabs; }
    ModelSettingsPanel modelPanel() { return model; }

    /** runs the current settings synchronously (tests) */
    void runBlocking() throws Exception {
        run();
        Worker w = worker;
        if (w == null) return;
        try { w.get(); } catch (ExecutionException ignored) { }
        // done() is posted to the EDT after get() can already return: wait until it has cleared the worker
        boolean[] finished = {false};
        while (!finished[0]) {
            SwingUtilities.invokeAndWait(() -> finished[0] = worker == null);
            if (!finished[0]) Thread.sleep(50);
        }
    }

    JTextField field(String which) {
        switch (which) {
            case "particles": return particles;
            case "replicas": return replicas;
            case "quench": return quench;
            case "sample": return sample;
            default: throw new IllegalArgumentException(which);
        }
    }
}
