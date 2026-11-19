package version4.FormFactors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lipid-nanoparticle screen under Forms. A guided two-step workflow for users new to LNP SAXS:
 * <ol>
 *   <li><b>Assess</b>: model-free look at the curve (size, polydispersity, internal peaks and their phase), which
 *       also suggests a model and seeds its starting values ({@link LnpAssessment});</li>
 *   <li><b>Fit</b>: polydisperse core / triple-shell sphere with an optional mesophase peak (Li et al. 2025),
 *       {@link LnpModel} and {@link LnpFitter}.</li>
 * </ol>
 */
public class LnpPanel extends JPanel {

    /** model variants offered to the user */
    enum Preset {
        MRNA("mRNA-LNP, neutral pH", 3, true,
                "Oil-like core of neutral ionizable lipid and cholesterol with mRNA wrapped by charged ionizable lipid, inside a "
                        + "DSPC/cholesterol/PEG monolayer (Trollmann & Böckmann 2022, Fig. 5f; Li et al. 2025). The broad peak near "
                        + "q ≈ 0.12–0.13 Å⁻¹ comes from the mRNA–lipid packing."),
        EMPTY("Empty LNP, neutral pH", 3, false,
                "Same particle without cargo: oil-like ionizable-lipid/cholesterol core inside the monolayer (Fig. 5e)."),
        VESICLE("Vesicle / bilayer shell", 4, false,
                "A water core inside a bilayer (inner heads, chains, outer heads), with an outer corona layer. "
                        + "This is the LNP vesicle formed when the ionizable lipid is protonated (Trollmann & "
                        + "Böckmann 2022, Fig. 5d), and equally a liposome or any single-bilayer particle. For a "
                        + "sample with no PEG, set the corona thickness to zero rather than leaving it to absorb "
                        + "the misfit."),
        NO_PEG("LNP without a PEG corona", 2, false,
                "Core, lipid layer and headgroups only. Use this for particles with no PEG-lipid (or a corona matched to the "
                        + "buffer) instead of leaving a corona in the model with zero thickness or zero contrast."),
        CORE_SHELL("Core–shell sphere (vesicle, nanoemulsion)", 1, false,
                "One shell around a core: unilamellar vesicles, oil-in-water nanoemulsions, solid lipid nanoparticles."),
        SPHERE("Homogeneous sphere", 0, false,
                "Uniform polydisperse spheres. A quick size estimate when the internal structure is not resolved.");

        final String label, help;
        final int shells;
        final boolean peak;

        Preset(String label, int shells, boolean peak, String help) {
            this.label = label; this.shells = shells; this.peak = peak; this.help = help;
        }

        @Override public String toString() { return label; }
    }

    private final DatasetProvider provider;
    private Supplier<String> workingDirectory = () -> null;

    // dataset
    private final JComboBox<String> datasetBox = new JComboBox<>();
    private final JLabel datasetInfo = new JLabel(" ");
    private final JButton refresh = new JButton("Refresh");
    private final JTextField qminField = Fields.num(""), qmaxField = Fields.num("");
    private double dataQmin = Double.NaN, dataQmax = Double.NaN;

    // model
    private final JComboBox<Preset> presetBox = new JComboBox<>(Preset.values());
    private final JLabel presetHelp = new JLabel();
    private final ParamTableModel tableModel = new ParamTableModel();
    private final JTable table = new JTable(tableModel);
    private final JCheckBox physical = new JCheckBox("Physical sign constraints", true);
    private final JCheckBox autoStart = new JCheckBox("Find core radius automatically", true);
    private LnpModelEditor modelEditor;
    private final JCheckBox liveCurve = new JCheckBox("Update the model curve as you edit", true);
    private javax.swing.Timer liveTimer;   // created in the constructor (needs the provider)
    private LnpModel model = new LnpModel(3, true);
    private double[] params = LnpModel.defaults();
    private boolean[] fixed = LnpModel.defaultFixed();
    private double[] errors = nanArray();

    // actions
    private final JButton assessButton = new JButton("1. Assess sample");
    private final JButton fitButton = new JButton("2. Fit model"), calcButton = new JButton("Calculate"),
            resetButton = new JButton("Reset"), stopButton = new JButton("Stop"), saveButton = new JButton("Save…");
    private final JButton suggestButton = new JButton("Use suggested model");
    private final JLabel suggestLabel = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel(" ");
    private JProgressBar mainProgress;
    private JLabel mainStatus;

    // expectations
    private final JComboBox<LnpExpectations.Cargo> cargoBox = new JComboBox<>(LnpExpectations.Cargo.values());
    private final JTextField cargoLength = Fields.num("4284"), npField = Fields.num("6"), ilField = Fields.num("50"),
            dMin = Fields.num(""), dMax = Fields.num("");
    private final JLabel cargoUnit = new JLabel("nt");
    private final JComboBox<String> referenceBox = new JComboBox<>();
    private final JComboBox<String> compositionBox = new JComboBox<>();
    private final JLabel compositionInfo = new JLabel(" ");
    private LnpComposition composition = LnpComposition.preset("Comirnaty (BNT162b2)");

    // particle picture and checks
    private final LnpCartoon cartoon = new LnpCartoon();
    private final JComboBox<LnpCartoon.View> viewBox = new JComboBox<>(LnpCartoon.View.values());
    private final JComboBox<Object> interiorBox = new JComboBox<>();
    private final JEditorPane checksPane = new JEditorPane("text/html", "");
    private List<LnpExpectations.Check> checks;
    // morphology populations
    private final java.util.EnumMap<LnpPopulations.Kind, JCheckBox> proposal = new java.util.EnumMap<>(LnpPopulations.Kind.class);
    private final JPanel popChart = new JPanel(new BorderLayout());
    private final JTextArea popText = new JTextArea();
    private final JButton suggestPop = new JButton("Suggest from the data"), testPop = new JButton("Test my proposal");
    private final JLabel popStatus = new JLabel(" ");
    private LnpPopulations.Result population;
    private LnpEmPanel emPanel;
    // series across datasets
    private final javax.swing.table.DefaultTableModel seriesModel = new javax.swing.table.DefaultTableModel(
            new Object[]{"Use", "Dataset", "Condition"}, 0) {
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : c == 2 ? Double.class : String.class; }
        @Override public boolean isCellEditable(int r, int c) { return c != 1; }
    };
    private final JComboBox<LnpSeries.Mode> seriesMode = new JComboBox<>(LnpSeries.Mode.values());
    private final JTextField seriesName = new JTextField("temperature", 10), seriesUnit = new JTextField("°C", 5);
    private final JCheckBox seriesPhases = new JCheckBox("also decompose the primary peak (disordered/HII)");
    private final JPanel seriesChart = new JPanel(new BorderLayout());
    private final JTextArea seriesText = new JTextArea();
    private final JLabel seriesStatus = new JLabel(" ");
    private final JButton runSeries = new JButton("Run series"), stopSeries = new JButton("Stop"),
            seriesCsv = new JButton("Export CSV…");
    private final DefaultListModel<LnpSeries.Descriptor> seriesPlotModel = new DefaultListModel<>();
    private final JList<LnpSeries.Descriptor> seriesPlot = new JList<>(seriesPlotModel);
    private volatile boolean seriesCancel;
    private LnpSeries.Result series;
    private SwingWorker<LnpSeries.Result, String> seriesWorker;
    // phase decomposition (Hammel et al. 2023)
    private final javax.swing.table.DefaultTableModel phaseBounds = new javax.swing.table.DefaultTableModel(
            new Object[]{"Phase", "q from (Å⁻¹)", "q to (Å⁻¹)", "≈ d (Å)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return c == 1 || c == 2; }
    };
    private final JTextField phaseQmin = Fields.num("0.07"), phaseQmax = Fields.num("0.17");
    private final JCheckBox phaseBackground = new JCheckBox("Fit the background with the peaks (recommended)", true);
    private final JPanel phaseChart = new JPanel(new BorderLayout());
    private final JTextArea phaseText = new JTextArea();
    LnpPhasePeaks.Result phaseResult;
    // lattice (Bragg) fit for cubosomes / hexosomes / lamellar particles
    private final JComboBox<String> peakMode = new JComboBox<>(new String[]{
            "Phase decomposition of the primary peak (LNP core)", "Lattice fit (cubosome, hexosome, lamellar)",
            "Index the peaks by hand (slide a phase onto them)", "Sponge phase, L3 (no Bragg peaks)"});
    private final JComboBox<Object> latticeBox = new JComboBox<>(), latticeBox2 = new JComboBox<>();
    private final JTextField latticeA = Fields.num(""), latticeQmin = Fields.num(""), latticeQmax = Fields.num(""),
            latticeExtra = Fields.num(""), latticeInst = Fields.num("0"), latticeLipid = Fields.num("18"),
            latticeDopant = Fields.num("");
    private final JComboBox<LnpLattice.Shape> latticeShape = new JComboBox<>(LnpLattice.Shape.values());
    private final JComboBox<LnpCharts.Weight> plotWeight = new JComboBox<>(LnpCharts.Weight.values());
    /** starting lattice parameter for the second phase, carried over from the indexing; NaN = work one out */
    private double secondAStart = Double.NaN;
    private final LnpStackCartoon stackCartoon = new LnpStackCartoon();
    private final JLabel unitWarning = new JLabel();
    private final JComboBox<String> waxsBox = new JComboBox<>();
    private final JTextArea waxsText = new JTextArea();
    private LnpWaxs.Result waxsResult;

    /**
     * Whether the chains should be treated as frozen when the composition works out the chain-region density.
     *
     * <p>A wide-angle dataset answers this directly and the answer matters: the hydrocarbon volume of the same
     * lipid differs by about 11% between the two states, and the contrast it gives against buffer by rather
     * more than that. Without a wide-angle dataset the fluid state is assumed, being the commoner one at room
     * temperature, and a declared composition cannot settle it — a saturated lipid below its transition is gel
     * and the same lipid above it is not.
     */
    private boolean gelChains() {
        return waxsResult != null
                && (waxsResult.packing == LnpWaxs.Packing.GEL || waxsResult.packing == LnpWaxs.Packing.ORDERED_SHARP);
    }
    private final JPanel waxsChart = new JPanel(new BorderLayout());
    private LnpIndexer indexer;
    private final JPanel phaseBody = new JPanel(new CardLayout());
    private final JTextField spongeQmin = Fields.num(""), spongeQmax = Fields.num("");
    private LnpLattice.Sponge spongeResult;
    LnpLattice.Result latticeResult;
    private double[][] lastData;
    private int assessedIndex = -1;

    // results
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel assessTab = new JPanel(new BorderLayout()), fitTab = new JPanel(new BorderLayout()),
            profileTab = new JPanel(new BorderLayout()), sizeTab = new JPanel(new BorderLayout());
    private final JTextArea report = new JTextArea();
    private LnpAssessment.Report assessment;
    private LnpFitter.Result result;
    private String resultName = "";
    private Preset suggested;
    /** analysis the assessment recommends: "fit", "lattice" or "fit+populations" */
    private String suggestedPath = "fit";
    /** the suggestion that was on screen, unaccepted, when the current fit was started */
    private Preset pendingSuggestion;

    /** which model an assessment points to, and which part of the panel does the work */
    static final class Suggestion {
        final Preset preset; final String path;
        Suggestion(Preset preset, String path) { this.preset = preset; this.path = path; }
    }

    /**
     * Which analysis an assessment points to. This is a pure function of the assessment so that it can be
     * recomputed wherever it is needed rather than remembered: a suggestion held in a field outlives the
     * assessment that produced it, survives fits and dataset changes, and then contradicts the panel it is
     * displayed next to.
     *
     * <p>Order matters, and it is not the order the flags were added in. An indexed lattice or a sponge is a
     * positive identification of the whole pattern and comes first. A form-factor lobe comes next, ahead of
     * both the mesophase peak and the no-peaks case: it is the finding that the broad maxima belong to the
     * particle's own shell profile, so it decides what those maxima mean, and if it is tested last the
     * branches above it answer the same question first and wrongly.
     */
    static Suggestion suggestionFor(LnpAssessment.Report a) {
        if (a == null) return new Suggestion(null, "fit");
        if (!a.phases.isEmpty() || a.coexistence != null) return new Suggestion(null, "lattice");
        if (a.spongeCandidate) return new Suggestion(null, "sponge");
        if (a.formFactorLobe) return new Suggestion(Preset.VESICLE, "fit");
        if (a.mesophasePeak) return new Suggestion(Preset.MRNA, "fit");
        if (a.peaks.isEmpty()) return new Suggestion(Preset.EMPTY, "fit");
        return new Suggestion(null, "fit");
    }

    /** a fit this good is its own evidence: no heuristic should talk the user out of it */
    static final double GOOD_FIT = 10.0;
    private volatile boolean cancel;
    private SwingWorker<LnpFitter.Result, Void> worker;

    public LnpPanel(DatasetProvider provider) {
        super(new BorderLayout());
        this.provider = provider;
        liveTimer = new javax.swing.Timer(250, e -> {
            if (worker == null && datasetBox.getSelectedIndex() >= 0 && provider.count() > 0) fit(true);
        });

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(new EmptyBorder(4, 4, 4, 4));
        controls.add(sampleBox());
        controls.add(expectationsBox());
        controls.add(modelBox());
        controls.add(runBox());
        for (Component c : controls.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        // Tracking the viewport width keeps the settings column inside its pane: without it the column keeps its
        // own preferred width, and since the horizontal scrollbar is off, anything wider is simply cut off at the
        // edge with no way to reach it.
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(controls, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        int leftWidth = 430;
        scroll.setPreferredSize(new Dimension(leftWidth, 600));
        scroll.setMinimumSize(new Dimension(360, 200));

        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        report.setBorder(new EmptyBorder(6, 8, 6, 8));
        JTabbedPane fitTabs = new JTabbedPane();
        fitTabs.addTab("Curve + residuals", fitTab);
        fitTabs.addTab("Density profile", profileTab);
        fitTabs.addTab("Size distribution", sizeTab);
        JPanel assessWrap = new JPanel(new BorderLayout());
        unitWarning.setOpaque(true);
        unitWarning.setBackground(new Color(0xfff4e5));
        unitWarning.setForeground(new Color(0x7a3e00));
        unitWarning.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xe0b080)), new EmptyBorder(7, 10, 7, 10)));
        unitWarning.setVisible(false);
        assessWrap.add(unitWarning, BorderLayout.SOUTH);
        // lipid data falls roughly as q^-1 over the peak range, so q.I(q) leaves a flat baseline and the
        // reflections stand clear of it: the view to start from for this kind of sample
        plotWeight.setSelectedItem(LnpCharts.Weight.Q);
        plotWeight.setToolTipText("Multiplying by a power of q flattens the falling background so the peaks stand "
                + "out; q²·I(q) is the Lorentz correction for a lamellar stack, q·I(q) the one for cylinders");
        plotWeight.addActionListener(e -> redrawWeighted());
        JPanel weightBar = Fields.flow(new JLabel("Plot"), plotWeight);
        weightBar.setBorder(new EmptyBorder(2, 6, 0, 6));
        assessWrap.add(weightBar, BorderLayout.NORTH);
        assessWrap.add(assessTab, BorderLayout.CENTER);
        tabs.addTab("1 · Assessment", assessWrap);
        tabs.addTab("2 · Fit", fitTabs);
        tabs.addTab("3 · Checks", buildChecksTab());
        tabs.addTab("Structure", buildPhaseTab());
        tabs.addTab("Populations", buildPopulationTab());
        tabs.addTab("Series", buildSeriesTab());
        tabs.addTab("Particle model", buildCartoonTab());
        emPanel = new LnpEmPanel(new LnpEmPanel.SaxsSide() {
            @Override public java.util.List<Object[]> spacings() { return saxsSpacings(); }
            @Override public LnpPopulations.Result populations() { return population; }
            @Override public double[] sizeDistribution() {
                if (result == null) return null;
                return new double[]{2 * result.model.outerRadius(result.params) / 10, 2 * result.params[LnpModel.SIGMA] / 10};
            }
            @Override public Supplier<String> workingDirectory() { return workingDirectory; }
        });
        tabs.addTab("SAXS ↔ EM", emPanel);
        tabs.addTab("Report", new JScrollPane(report));
        tabs.addTab("Chain packing (WAXS)", buildWaxsTab());
        tabs.setToolTipTextAt(tabs.getTabCount() - 1,
                "Optional: a wide-angle dataset of the same sample, which says whether the chains are ordered");
        tabs.addTab("Methods", LnpFormulas.panel());
        tabs.setToolTipTextAt(tabs.getTabCount() - 1,
                "Every formula this panel applies, with the paper it comes from");
        tabs.setMinimumSize(new Dimension(300, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, tabs);
        split.setResizeWeight(0);
        split.setContinuousLayout(true);
        add(datasetBar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        presetBox.setSelectedItem(Preset.MRNA);
        presetBox.addActionListener(e -> presetChanged());
        assessButton.addActionListener(e -> assess());
        fitButton.addActionListener(e -> fit(false));
        calcButton.addActionListener(e -> fit(true));
        resetButton.addActionListener(e -> reset());
        stopButton.addActionListener(e -> cancel = true);
        saveButton.addActionListener(e -> save());
        suggestButton.addActionListener(e -> applySuggestion());
        stopButton.setEnabled(false);
        saveButton.setEnabled(false);
        suggestButton.setVisible(false);
        presetChanged();
        showEmpty();
        referenceBox.addActionListener(e -> { if (assessment != null) updateChecks(); });
        updateChecks();
        updateCartoon();

        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) refreshDatasets();
        });
        refreshDatasets();
    }

    public void setWorkingDirectory(Supplier<String> wd) { workingDirectory = wd == null ? () -> null : wd; }

    public void setMainStatus(JProgressBar bar, JLabel label) { mainProgress = bar; mainStatus = label; }

    public boolean isRunning() { return worker != null; }

    // ------------------------------------------------------------------ layout

    private JPanel datasetBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xd0d7de)), new EmptyBorder(6, 8, 6, 8)));
        refresh.setToolTipText("Re-read the datasets loaded in the Analysis panel");
        refresh.addActionListener(e -> refreshDatasets());
        datasetBox.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXXX");
        datasetBox.addActionListener(e -> datasetChanged());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 4, 0, 4);
        c.gridx = 0; bar.add(new JLabel("Dataset"), c);
        c.gridx = 1; c.weightx = 0.6; c.fill = GridBagConstraints.HORIZONTAL; bar.add(datasetBox, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE; bar.add(refresh, c);
        c.gridx = 3; c.weightx = 0.4; c.fill = GridBagConstraints.HORIZONTAL;
        datasetInfo.setForeground(new Color(0x6e7781));
        bar.add(datasetInfo, c);
        return bar;
    }

    private static JPanel titled(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xd0d7de)), title,
                        TitledBorder.LEFT, TitledBorder.TOP),
                new EmptyBorder(2, 2, 6, 2)));
        return p;
    }

    private JPanel sampleBox() {
        Fields.Form f = new Fields.Form(titled("Sample"));
        f.row("q range (Å⁻¹)", Fields.pair(qminField, qmaxField), "Fitting range. Exclude a low-q aggregation upturn or noisy high-q data");
        assessButton.setToolTipText("Model-free look at the curve: size, polydispersity, internal peaks and their phase");
        f.full(Fields.flow(assessButton));
        f.note("Start here. The assessment tells you what kind of particle you have, suggests a model and sets its starting values.");
        suggestLabel.setForeground(new Color(0x2563eb));
        f.full(suggestLabel);
        f.full(Fields.flow(suggestButton));
        return f.panel();
    }

    private JPanel modelBox() {
        Fields.Form f = new Fields.Form(titled("Model"));
        f.row("Particle", presetBox, "Choose a template: the three Comirnaty architectures of Trollmann & Böckmann (2022) Fig. 5 d–f, or a simpler sphere");
        presetHelp.setForeground(new Color(0x6e7781));
        f.full(presetHelp);
        modelEditor = new LnpModelEditor(new LnpModelEditor.Host() {
            @Override public LnpModel model() { return model; }
            @Override public double[] params() { return params; }
            @Override public boolean[] fixed() { return fixed; }
            @Override public double[] errors() { return errors; }
            @Override public String paramName(int k) { return name(model, k); }
            @Override public void changed(boolean done) {
                if (!anyDensityFixed() && model.shells >= 1) fixed[model.isVesicle() ? LnpModel.RHO_2 : LnpModel.RHO_1] = true;
                tableModel.fireTableDataChanged();
                if (done) { updateCartoon(); if (liveCurve.isSelected()) liveTimer.restart(); }
            }
        });
        modelEditor.setBorder(BorderFactory.createLineBorder(new Color(0xd0d7de)));
        f.full(modelEditor);
        liveCurve.setToolTipText("Recalculate the model curve against the data after every change in the cartoon or the table");
        liveTimer.setRepeats(false);
        f.full(Fields.flow(liveCurve));

        table.setRowHeight(table.getRowHeight() + 4);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setMaxWidth(36);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setMaxWidth(44);
        DefaultTableCellRenderer greyIfFixed = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                int k = tableModel.param(row);
                if (!sel) c.setForeground(fixed[k] ? new Color(0x8c959f) : Color.BLACK);
                if (c instanceof JComponent) ((JComponent) c).setToolTipText(LnpModel.TIPS[k]);
                return c;
            }
        };
        for (int col = 1; col < 5; col++) table.getColumnModel().getColumn(col).setCellRenderer(greyIfFixed);
        JScrollPane ts = new JScrollPane(table);
        ts.setPreferredSize(new Dimension(380, 14 * table.getRowHeight() + 28));
        f.full(ts);
        f.note("Tick <b>Fit</b> to refine a parameter; unticked values are held fixed. Only density ratios follow from the data, so "
                + "one density stays fixed (inner lipid layer at −0.035 e/Å³, typical of hydrocarbon chains). "
                + "Thickness defaults follow Li et al. 2025: headgroups 3 Å and PEG-2000 corona 40 Å, both held fixed.");
        physical.setToolTipText("Inner lipid layer below buffer density, headgroup and PEG layers above it, inner layer 5–100 Å");
        autoStart.setToolTipText("Scan the core radius before refining, so you do not have to guess the particle size");
        f.full(Fields.flow(physical, autoStart));
        return f.panel();
    }

    private JPanel expectationsBox() {
        Fields.Form f = new Fields.Form(titled("What you expect"));
        refreshCompositionMenu("Comirnaty (BNT162b2)");
        compositionBox.addActionListener(e -> { if (!menuUpdating) compositionChosen(); });
        JButton editComp = new JButton("Edit…"), applyComp = new JButton("Use for model"), library = new JButton("Library…");
        library.setToolTipText("Your private lipid library: add proprietary lipids, import or export them");
        library.addActionListener(e -> {
            if (LnpLibraryDialog.manage(this)) refreshCompositionMenu(String.valueOf(compositionBox.getSelectedItem()));
        });
        editComp.setToolTipText("Edit the lipids and their mol %");
        applyComp.setToolTipText("Set the core and PEG-corona densities and the corona thickness from the composition");
        editComp.addActionListener(e -> editComposition());
        applyComp.addActionListener(e -> applyComposition());
        // "Lipids" is a heading on its own line and the controls sit under it, so the drop-down and its two
        // buttons share the whole width of the column instead of competing with a label beside them. A formulation
        // name is long, and in a narrow window the buttons were pushed out of sight.
        JLabel lipidsTitle = new JLabel("Lipids");
        lipidsTitle.setFont(lipidsTitle.getFont().deriveFont(Font.BOLD));
        lipidsTitle.setToolTipText("Lipid mix of the formulation (mol %): a preset, one of your saved formulations, or Custom…");
        f.full(lipidsTitle);
        // leave room for both buttons on the same line; the full name stays in the list and the tooltip
        compositionBox.setPreferredSize(new Dimension(205, compositionBox.getPreferredSize().height));
        f.full(Fields.flow(compositionBox, editComp, library));
        compositionInfo.setForeground(new Color(0x6e7781));
        f.full(compositionInfo);
        f.full(Fields.flow(applyComp));
        f.row("Cargo", cargoBox, "Nucleic acid in the formulation. Used for the checks, copies per particle and the particle picture");
        f.row("Cargo length", Fields.flow(cargoLength, cargoUnit), "siRNA: base pairs of the duplex (21 for patisiran). mRNA: nucleotides (BNT162b2: 4284)");
        f.row("N/P ratio", npField, "Ionizable-lipid amines per nucleic-acid phosphate");
        // the settings column is too narrow to carry this beside the field without losing the end of it
        JLabel npHint = new JLabel("<html><div style='width:290px'>molar ratio ionizable nitrogen to phosphate</div></html>");
        npHint.setForeground(LnpCartoon.MUTED);
        npHint.setFont(npHint.getFont().deriveFont(Font.PLAIN, npHint.getFont().getSize2D() - 1f));
        npHint.setBorder(new EmptyBorder(0, 6, 4, 4));
        f.full(npHint);
        f.row("Ionizable lipid (mol %)", ilField, "Share of the ionizable lipid in the lipid mix (Comirnaty 46.3, Onpattro 50)");
        f.row("Expected diameter (nm)", Fields.pair(dMin, dMax), "Optional, e.g. from DLS. DLS usually reads 10–30 % larger than SAXS");
        f.row("Empty reference", referenceBox, "Optional: an empty-LNP dataset measured the same way. Its peak is compared with this sample's");
        cargoBox.setSelectedItem(LnpExpectations.Cargo.NONE);
        compositionUpdated();
        cargoBox.addActionListener(e -> {
            LnpExpectations.Cargo c = (LnpExpectations.Cargo) cargoBox.getSelectedItem();
            if (c == null) return;
            cargoUnit.setText(c.unit);
            if (c != LnpExpectations.Cargo.NONE) cargoLength.setText(String.valueOf(c.defaultLength));
            cargoLength.setEnabled(c != LnpExpectations.Cargo.NONE);
            npField.setEnabled(c != LnpExpectations.Cargo.NONE);
            updateChecks();
            updateCartoon();
        });
        // "No cargo" is the first item, so selecting it above fires no event: sync the dependent fields by hand
        cargoUnit.setText(LnpExpectations.Cargo.NONE.unit);
        cargoLength.setEnabled(false);
        npField.setEnabled(false);
        javax.swing.event.DocumentListener live = new javax.swing.event.DocumentListener() {
            void go() { SwingUtilities.invokeLater(() -> { updateCartoon(); if (assessment != null) updateChecks(); }); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { go(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { go(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { go(); }
        };
        for (JTextField t : new JTextField[]{cargoLength, npField, ilField, dMin, dMax}) t.getDocument().addDocumentListener(live);
        f.note("Tell Scatter what you made. The Checks tab then says whether particles formed, whether an RNA–lipid structure is present, and how much cargo a particle of the measured size can hold.");
        return f.panel();
    }

    private boolean menuUpdating;

    /** presets, the user's saved formulations, then Custom…; keeps the given selection when it still exists */
    private void refreshCompositionMenu(String select) {
        menuUpdating = true;
        compositionBox.removeAllItems();
        compositionBox.addItem("(not specified)");
        for (String k : LnpComposition.presetNames()) compositionBox.addItem(k);
        compositionBox.addItem("Custom…");
        boolean found = false;
        for (int i = 0; i < compositionBox.getItemCount(); i++) if (compositionBox.getItemAt(i).equals(select)) found = true;
        compositionBox.setSelectedItem(found ? select : "Custom…");
        menuUpdating = false;
    }

    private void compositionChosen() {
        Object sel = compositionBox.getSelectedItem();
        if ("Custom…".equals(sel)) { editComposition(); return; }
        if (!"(not specified)".equals(sel) && sel != null) {
            LnpComposition c = LnpComposition.preset((String) sel);
            if (c == null) { say("'" + sel + "' uses lipids that are no longer in your library"); return; }
            composition = c;
        } else composition = null;
        compositionUpdated();
    }

    private void compositionUpdated() {
        if (composition == null) {
            compositionInfo.setText("<html><div style='width:290px'>No composition: generic defaults are used.</div></html>");
        } else {
            ilField.setText(String.format("%.1f", composition.ionizableMolPercent()));
            double np = parse(npField, 6);
            compositionInfo.setText(String.format("<html><div style='width:290px'>Ionizable %.1f mol%%, mean lipid volume %.0f Å³, shell chains %+.3f, core %+.3f e/Å³."
                            + "<br>Expected interior: %s.</div></html>", composition.ionizableMolPercent(), composition.meanLipidVolume(), composition.innerShellContrast(gelChains()),
                    composition.coreContrast(cargoBox.getSelectedItem() == LnpExpectations.Cargo.NONE ? Double.POSITIVE_INFINITY : np, 0.24),
                    composition.expectedPhase()));
        }
        if (assessment != null) updateChecks();
        updateCartoon();
    }

    /** dialog with an editable lipid / mol % table */
    private void editComposition() {
        javax.swing.table.DefaultTableModel tm = new javax.swing.table.DefaultTableModel(new Object[]{"Lipid", "mol %"}, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 1 ? Double.class : Object.class; }
        };
        LnpComposition c0 = composition != null ? composition : LnpComposition.preset("Comirnaty (BNT162b2)");
        for (int i = 0; i < c0.lipids.size(); i++) tm.addRow(new Object[]{c0.lipids.get(i), c0.molPercent.get(i)});
        JTable t = new JTable(tm);
        final String[] savedName = {null};
        JComboBox<LnpComposition.Lipid> lipidChoice = new JComboBox<>(LnpComposition.allLipids().toArray(new LnpComposition.Lipid[0]));
        t.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(lipidChoice));
        t.setRowHeight(t.getRowHeight() + 4);
        JButton add = new JButton("Add row"), del = new JButton("Remove"), newLipid = new JButton("New lipid…"),
                saveForm = new JButton("Save as formulation…");
        add.addActionListener(e -> tm.addRow(new Object[]{LnpComposition.lookup("Cholesterol"), 0.0}));
        del.addActionListener(e -> { int r = t.getSelectedRow(); if (r >= 0) tm.removeRow(r); });
        newLipid.setToolTipText("Define a lipid that is not in the list (e.g. a proprietary ionizable lipid); it is kept in your private library");
        newLipid.addActionListener(e -> {
            LnpComposition.Lipid l = LnpLibraryDialog.editLipid(this, null, false);
            if (l == null) return;
            lipidChoice.setModel(new DefaultComboBoxModel<>(LnpComposition.allLipids().toArray(new LnpComposition.Lipid[0])));
            tm.addRow(new Object[]{l, 0.0});
        });
        saveForm.setToolTipText("Store this mix in your private library; it then appears in the Lipids menu");
        saveForm.addActionListener(e -> {
            if (t.isEditing()) t.getCellEditor().stopCellEditing();
            LnpComposition c = fromTable(tm);
            if (c == null) { say("Composition needs at least one lipid"); return; }
            String name = JOptionPane.showInputDialog(this, "Name for this formulation:", "Save formulation", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return;
            name = name.trim();
            if (LnpComposition.PRESETS.containsKey(name)) { JOptionPane.showMessageDialog(this, "That name is used by a built-in preset."); return; }
            try {
                LnpLipidLibrary.get().putFormulation(name, c);
                savedName[0] = name;
                say("Saved formulation '" + name + "'");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage());
            }
        });
        JPanel p = new JPanel(new BorderLayout(4, 4));
        JScrollPane sp = new JScrollPane(t);
        sp.setPreferredSize(new Dimension(380, 180));
        p.add(sp, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(2, 1));
        buttons.add(Fields.flow(add, del, newLipid, saveForm));
        buttons.add(Fields.flow(new JLabel("mol % need not add up to 100. Private lipids are marked (private).")));
        p.add(buttons, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(this, p, "Lipid composition", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            if ("Custom…".equals(compositionBox.getSelectedItem()) && composition == null) compositionBox.setSelectedIndex(0);
            if (savedName[0] != null) refreshCompositionMenu(String.valueOf(compositionBox.getSelectedItem()));
            return;
        }
        if (t.isEditing()) t.getCellEditor().stopCellEditing();
        LnpComposition c = fromTable(tm);
        if (c == null) { say("Composition needs at least one lipid"); return; }
        composition = c;
        refreshCompositionMenu(savedName[0] != null ? savedName[0] : "Custom…");
        compositionUpdated();
    }

    private static LnpComposition fromTable(javax.swing.table.DefaultTableModel tm) {
        LnpComposition c = new LnpComposition();
        for (int r = 0; r < tm.getRowCount(); r++) {
            Object l = tm.getValueAt(r, 0), m = tm.getValueAt(r, 1);
            if (l instanceof LnpComposition.Lipid && m instanceof Number && ((Number) m).doubleValue() > 0)
                c.add((LnpComposition.Lipid) l, ((Number) m).doubleValue());
        }
        return c.lipids.isEmpty() ? null : c;
    }

    /**
     * Moves the other densities with the reference when the reference itself changes.
     *
     * <p>Only ratios of density are determined by the data. The model divides its amplitude by
     * {@code sum |drho_k| V(r_k)}, so multiplying every contrast by the same constant leaves the curve exactly
     * where it was and the scale is what carries the absolute level. When the fixed reference moves — because
     * a composition was declared, or because a wide-angle dataset showed the chains are frozen — the solution
     * the panel is holding is still a solution, but only if the other densities move with it.
     *
     * <p>Left behind, they are no longer anywhere near the minimum, and the fit does not simply take longer:
     * refitting a good bilayer solution after changing the reference from -0.025 to -0.044 landed at a reduced
     * chi-squared of 496 with an outer headgroup layer 168 A thick, while the same fit seeded with the
     * densities rescaled returned the original answer to three figures.
     */
    private void rescaleDensities(int reference, double now) {
        double was = params[reference];
        if (!(was != 0) || !(now != 0) || !Double.isFinite(was) || !Double.isFinite(now)) return;
        double k = now / was;
        if (!Double.isFinite(k) || k <= 0 || Math.abs(k - 1) < 1e-9) return;
        for (int i = 0; i <= model.shells; i++) {
            int d = LnpModel.rhoIndex(i);
            if (d != reference && model.uses(d)) params[d] *= k;
        }
        // the scale is deliberately left alone: the model already normalises the density profile, so the
        // curve is unchanged by this and dividing the scale as well would move it by a factor of k squared
    }

    /** starting densities and corona from the composition */
    private void applyComposition() {
        if (composition == null) { say("Choose a lipid composition first"); return; }
        boolean cargo = cargoBox.getSelectedItem() != LnpExpectations.Cargo.NONE;
        if (model.isVesicle()) {
            rescaleDensities(LnpModel.RHO_2, composition.innerShellContrast(gelChains()));
            params[LnpModel.RHO_2] = composition.innerShellContrast(gelChains());
            double[] cor = composition.corona(params[LnpModel.R0], params[LnpModel.R0] + 46);
            if (cor[0] > 0) { params[LnpModel.T4] = cor[0]; params[LnpModel.RHO_4] = Math.max(0, cor[1]); }
            errors = nanArray();
            tableModel.fireTableDataChanged();
            if (modelEditor != null) modelEditor.repaint();
            say(String.format("From composition: bilayer chains %+.3f e/Å³, PEG corona %.0f Å", params[LnpModel.RHO_2], params[LnpModel.T4]));
            return;
        }
        rescaleDensities(LnpModel.RHO_1, composition.innerShellContrast(gelChains()));
        params[LnpModel.RHO_C] = composition.coreContrast(cargo ? parse(npField, 6) : Double.POSITIVE_INFINITY, 0.24);
        params[LnpModel.RHO_1] = composition.innerShellContrast(gelChains());   // the fixed reference of the density scale
        double rHead = params[LnpModel.R0] + params[LnpModel.T1] + params[LnpModel.T2];
        double[] cor = composition.corona(params[LnpModel.R0], rHead);
        if (cor[0] > 0) { params[LnpModel.T3] = cor[0]; params[LnpModel.RHO_3] = Math.max(0, cor[1]); }
        errors = nanArray();
        tableModel.fireTableDataChanged();
        if (modelEditor != null) modelEditor.repaint();
        say(String.format("From composition: shell chains %+.3f, core %+.3f e/Å³, PEG corona %.0f Å at %+.4f e/Å³. Press Fit",
                params[LnpModel.RHO_1], params[LnpModel.RHO_C], params[LnpModel.T3], params[LnpModel.RHO_3]));
    }

    /** characteristic spacings the SAXS analysis predicts, for the EM comparison: {label, d (A)} */
    java.util.List<Object[]> saxsSpacings() {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        if (assessment != null) {
            if (assessment.coexistence != null) {
                for (LnpAssessment.PhaseMatch m : assessment.coexistence) addLattice(out, m);
            } else if (!assessment.phases.isEmpty()) addLattice(out, assessment.phases.get(0));
            else for (LnpAssessment.Peak p : assessment.peaks) out.add(new Object[]{"peak", p.d()});
        }
        if (result != null && result.model.peak) out.add(new Object[]{"RNA peak", 2 * Math.PI / result.params[LnpModel.PEAK_Q]});
        if (latticeResult != null) {
            for (LnpLattice.Reflection rf : latticeResult.reflections)
                if (rf.inRange && rf.area > 0 && out.size() < 8) out.add(new Object[]{latticeResult.lattice.symmetry.split(",")[0] + " " + rf.hkl, rf.d});
            if (!Double.isNaN(latticeResult.extraQ)) out.add(new Object[]{"extra peak", 2 * Math.PI / latticeResult.extraQ});
        }
        if (phaseResult != null)
            for (LnpPhasePeaks.Component c : phaseResult.components) if (c.area > 0) out.add(new Object[]{c.name, c.d()});
        // drop near-duplicates (same spacing from two analyses)
        java.util.List<Object[]> uniq = new java.util.ArrayList<>();
        for (Object[] o : out) {
            boolean dup = false;
            for (Object[] u : uniq) if (Math.abs((Double) u[1] - (Double) o[1]) < 0.015 * (Double) o[1]) dup = true;
            if (!dup) uniq.add(o);
        }
        return uniq;
    }

    /** d spacings of the first reflections of an indexed phase */
    private static void addLattice(java.util.List<Object[]> out, LnpAssessment.PhaseMatch m) {
        double a = m.latticeParameter;
        if (m.isLamellar()) { out.add(new Object[]{"Lα (001)", a}); out.add(new Object[]{"Lα (002)", a / 2}); }
        else if (m.isHexagonal()) {
            out.add(new Object[]{"HII (10)", a * Math.sqrt(3) / 2});
            out.add(new Object[]{"HII (11)", a / 2});
            out.add(new Object[]{"HII (20)", a * Math.sqrt(3) / 4});
        } else {
            String[] hkl = m.indices;
            for (int i = 0; i < hkl.length && i < 3; i++) {
                if (hkl[i] == null) continue;
                String d = hkl[i].replaceAll("[()]", "");
                double h2 = 0;
                for (char c : d.toCharArray()) h2 += (c - '0') * (c - '0');
                out.add(new Object[]{m.symmetry.split(",")[0] + " " + hkl[i], a / Math.sqrt(h2)});
            }
        }
    }

    private JComponent buildSeriesTab() {
        JTable t = new JTable(seriesModel);
        t.setRowHeight(t.getRowHeight() + 3);
        t.getColumnModel().getColumn(0).setMaxWidth(44);
        t.getColumnModel().getColumn(2).setMaxWidth(110);
        JScrollPane ts = new JScrollPane(t);
        ts.setPreferredSize(new Dimension(420, 150));
        JButton fill = new JButton("Fill from names");
        fill.setToolTipText("Read the condition out of each file name (the first number it contains)");
        fill.addActionListener(e -> fillConditionsFromNames());
        JButton all = new JButton("All"), none = new JButton("None");
        all.addActionListener(e -> { for (int r = 0; r < seriesModel.getRowCount(); r++) seriesModel.setValueAt(true, r, 0); });
        none.addActionListener(e -> { for (int r = 0; r < seriesModel.getRowCount(); r++) seriesModel.setValueAt(false, r, 0); });

        for (LnpSeries.Descriptor d : LnpSeries.Descriptor.values()) seriesPlotModel.addElement(d);
        seriesPlot.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        seriesPlot.setSelectedIndices(new int[]{LnpSeries.Descriptor.LATTICE_A.ordinal()});
        seriesPlot.addListSelectionListener(e -> { if (!e.getValueIsAdjusting() && series != null) drawSeriesChart(); });
        JScrollPane ps = new JScrollPane(seriesPlot);
        ps.setPreferredSize(new Dimension(230, 150));
        ps.setBorder(BorderFactory.createTitledBorder("Plot"));

        runSeries.addActionListener(e -> runSeries());
        stopSeries.addActionListener(e -> seriesCancel = true);
        stopSeries.setEnabled(false);
        seriesCsv.addActionListener(e -> exportSeriesCsv());
        seriesCsv.setEnabled(false);
        seriesStatus.setForeground(new Color(0x6e7781));

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(ts, BorderLayout.CENTER);
        left.add(Fields.flow(all, none, fill), BorderLayout.SOUTH);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(6, 8, 4, 8));
        top.add(Fields.wrappingLabel(top, "Analyses several datasets the same way and tracks the result against a "
                + "condition you vary: temperature, time, concentration, N/P, pH. This is how a phase transition or a kinetic run is read "
                + "(Caselli et al., Adv. Colloid Interface Sci. 2024, sec. 4.1). The settings of the other tabs are used, so set up one "
                + "dataset there first."));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.add(left);
        row.add(ps);
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(Fields.flow(new JLabel("Analysis"), seriesMode));
        side.add(Fields.flow(new JLabel("Condition"), seriesName, new JLabel("unit"), seriesUnit));
        side.add(Fields.flow(seriesPhases));
        side.add(Fields.flow(runSeries, stopSeries, seriesCsv));
        side.add(seriesStatus);
        row.add(side);
        top.add(row);
        for (Component c : top.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        seriesText.setEditable(false);
        seriesText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        seriesChart.add(LnpCharts.panelFor(FormFactorCharts.placeholder("Set a condition for each dataset and press Run series")), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, seriesChart, new JScrollPane(seriesText));
        split.setResizeWeight(0.55);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    /** keeps the series table in step with the loaded datasets */
    private void refreshSeriesTable() {
        java.util.Map<String, Object[]> old = new java.util.HashMap<>();
        for (int r = 0; r < seriesModel.getRowCount(); r++)
            old.put(String.valueOf(seriesModel.getValueAt(r, 1)), new Object[]{seriesModel.getValueAt(r, 0), seriesModel.getValueAt(r, 2)});
        seriesModel.setRowCount(0);
        for (int i = 0; i < datasetBox.getItemCount(); i++) {
            String name = datasetBox.getItemAt(i);
            Object[] prev = old.get(name);
            seriesModel.addRow(new Object[]{prev == null ? Boolean.TRUE : prev[0], name, prev == null ? null : prev[1]});
        }
    }

    /** first number in each dataset name becomes its condition */
    private void fillConditionsFromNames() {
        java.util.regex.Pattern num = java.util.regex.Pattern.compile("(-?\\d+(?:[.,]\\d+)?)");
        int filled = 0;
        for (int r = 0; r < seriesModel.getRowCount(); r++) {
            String n = String.valueOf(seriesModel.getValueAt(r, 1)).replaceFirst("^\\d+:\\s*", "");
            java.util.regex.Matcher m = num.matcher(n);
            if (m.find()) {
                try { seriesModel.setValueAt(Double.parseDouble(m.group(1).replace(',', '.')), r, 2); filled++; }
                catch (NumberFormatException ignored) { }
            }
        }
        seriesStatus.setText(filled + " condition(s) read from the names – check them");
    }

    private void runSeries() {
        if (seriesWorker != null) return;
        final java.util.List<Integer> rows = new java.util.ArrayList<>();
        for (int r = 0; r < seriesModel.getRowCount(); r++) {
            Object use = seriesModel.getValueAt(r, 0), cond = seriesModel.getValueAt(r, 2);
            if (Boolean.TRUE.equals(use) && cond instanceof Number) rows.add(r);
        }
        if (rows.size() < 2) { seriesStatus.setText("Tick at least two datasets and give each a condition"); return; }
        final LnpSeries.Mode mode = (LnpSeries.Mode) seriesMode.getSelectedItem();
        final LnpSeries.Settings cfg = new LnpSeries.Settings();
        cfg.model = model;
        cfg.start = params.clone();
        cfg.fixed = fixed.clone();
        cfg.physical = physical.isSelected();
        cfg.autoStart = autoStart.isSelected();
        cfg.shape = (LnpLattice.Shape) latticeShape.getSelectedItem();
        cfg.extraQ = parse(latticeExtra, Double.NaN);
        cfg.instrument = parse(latticeInst, 0);
        double dop = parse(latticeDopant, Double.NaN);
        cfg.dopant = Double.isNaN(dop) ? Double.NaN : dop * 10 / 2;
        cfg.latticeA = parse(latticeA, Double.NaN);
        cfg.qmin = parse(qminField, Double.NaN);
        cfg.qmax = parse(qmaxField, Double.NaN);
        cfg.phaseDecomposition = seriesPhases.isSelected();
        Object sel = latticeBox.getSelectedItem(), sel2 = latticeBox2.getSelectedItem();
        cfg.lattice = sel instanceof LnpLattice.Lattice ? (LnpLattice.Lattice) sel : null;
        cfg.second = sel2 instanceof LnpLattice.Lattice ? (LnpLattice.Lattice) sel2 : null;
        final String cname = seriesName.getText().trim().isEmpty() ? "condition" : seriesName.getText().trim();
        final String cunit = seriesUnit.getText().trim();
        final LnpSeries.Source src = new LnpSeries.Source() {
            @Override public int count() { return rows.size(); }
            @Override public String name(int i) { return String.valueOf(seriesModel.getValueAt(rows.get(i), 1)); }
            @Override public double condition(int i) { return ((Number) seriesModel.getValueAt(rows.get(i), 2)).doubleValue(); }
            @Override public double[][] data(int i) {
                int idx = rows.get(i);
                double qlo = Double.isNaN(cfg.qmin) ? 0 : cfg.qmin, qhi = Double.isNaN(cfg.qmax) ? Double.MAX_VALUE : cfg.qmax;
                return FormFactorPanel.extract(provider.data(idx), qlo, qhi);
            }
        };
        seriesCancel = false;
        runSeries.setEnabled(false);
        stopSeries.setEnabled(true);
        seriesWorker = new SwingWorker<LnpSeries.Result, String>() {
            @Override protected LnpSeries.Result doInBackground() {
                LnpSeries.Result r = LnpSeries.run(src, mode, cfg, new LnpSeries.Progress() {
                    @Override public void at(int i, int n, String name) { publish(String.format("%d/%d  %s", i + 1, n, name)); }
                    @Override public boolean cancelled() { return seriesCancel; }
                });
                r.conditionName = cname;
                r.conditionUnit = cunit;
                return r;
            }

            @Override protected void process(java.util.List<String> chunks) { seriesStatus.setText(chunks.get(chunks.size() - 1)); }

            @Override protected void done() {
                seriesWorker = null;
                runSeries.setEnabled(true);
                stopSeries.setEnabled(false);
                try {
                    series = get();
                    seriesText.setText(LnpSeries.describe(series));
                    seriesText.setCaretPosition(0);
                    java.util.List<LnpSeries.Descriptor> avail = series.available();
                    if (!avail.isEmpty() && seriesPlot.getSelectedValuesList().stream().noneMatch(avail::contains))
                        seriesPlot.setSelectedValue(avail.get(0), true);
                    drawSeriesChart();
                    seriesCsv.setEnabled(true);
                    java.util.List<String> tr = LnpSeries.transitions(series);
                    seriesStatus.setText(tr.isEmpty() ? series.points.size() + " datasets analysed; no phase change"
                            : series.points.size() + " datasets analysed; " + tr.get(0));
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    seriesStatus.setText("Series failed: " + c.getMessage());
                }
            }
        };
        seriesWorker.execute();
    }

    private void drawSeriesChart() {
        if (series == null) return;
        java.util.List<LnpSeries.Descriptor> sel = seriesPlot.getSelectedValuesList();
        if (sel.isEmpty()) return;
        seriesChart.removeAll();
        seriesChart.add(LnpCharts.panelFor(LnpCharts.seriesChart(series, sel)), BorderLayout.CENTER);
        seriesChart.revalidate();
        seriesChart.repaint();
    }

    private void exportSeriesCsv() {
        if (series == null) return;
        JFileChooser fc = new JFileChooser(workingDirectory.get());
        fc.setSelectedFile(new File("lnp_series.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter w = new PrintWriter(fc.getSelectedFile(), "UTF-8")) {
            for (String line : LnpSeries.describe(series).split("\n")) w.println("# " + line);
            w.print(LnpSeries.csv(series));
            say("Saved " + fc.getSelectedFile().getName());
        } catch (IOException ex) {
            say("Could not save: " + ex.getMessage());
        }
    }

    private JComponent buildPhaseTab() {
        JPanel cards = new JPanel(new CardLayout());
        cards.add(buildPhaseControls(), "phase");
        cards.add(buildLatticeControls(), "lattice");
        cards.add(buildSpongeControls(), "sponge");
        cards.add(buildIndexerControls(), "index");
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(6, 8, 4, 8));
        top.add(Fields.flow(new JLabel("Analysis"), peakMode));
        top.add(cards);
        for (Component c : top.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        peakMode.addActionListener(e -> {
            int i = peakMode.getSelectedIndex();
            ((CardLayout) cards.getLayout()).show(cards, i == 0 ? "phase" : i == 2 ? "index" : i == 3 ? "sponge" : "lattice");
            ((CardLayout) phaseBody.getLayout()).show(phaseBody, i == 2 ? "index" : "chart");
            if (i == 2) refreshIndexer();
        });
        phaseText.setEditable(false);
        phaseText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        phaseChart.add(LnpCharts.panelFor(FormFactorCharts.placeholder("Choose an analysis and press the button")), BorderLayout.CENTER);
        JScrollPane stackScroll = new JScrollPane(stackCartoon);
        stackScroll.setBorder(BorderFactory.createTitledBorder("Model of the fitted phase"));
        JSplitPane lower = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(phaseText), stackScroll);
        lower.setResizeWeight(0.55);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, phaseChart, lower);
        split.setResizeWeight(0.55);
        indexer = new LnpIndexer(new IndexerHost());
        phaseBody.add(split, "chart");
        phaseBody.add(indexer, "index");
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(phaseBody, BorderLayout.CENTER);
        return p;
    }

    /**
     * Draws the lamellar result as a stack, and reads the bilayer out of the order intensities. The positions of
     * the orders give the repeat; their relative heights are the bilayer form factor sampled at each order, which
     * is what separates bilayer from water without relying on the lipid length the user typed in.
     */
    private void updateStack(LnpLattice.Result r, double lipid, StringBuilder sb) {
        java.util.List<LnpStackCartoon.Phase> drawn = new java.util.ArrayList<>();
        LnpLattice.Result[] both = r.second == null ? new LnpLattice.Result[]{r} : new LnpLattice.Result[]{r, r.second};
        for (int i = 0; i < both.length; i++) {
            LnpLattice.Result one = both[i];
            if (!one.lattice.lamellar) continue;
            java.util.List<Double> qs = new java.util.ArrayList<>(), areas = new java.util.ArrayList<>();
            for (LnpLattice.Reflection rf : one.reflections)
                if (rf.inRange && rf.area > 0) { qs.add(rf.q); areas.add(rf.area); }
            double thickness = Double.NaN;
            if (qs.size() >= 2) {
                double[] q = new double[qs.size()], a = new double[qs.size()];
                for (int k = 0; k < q.length; k++) { q[k] = qs.get(k); a[k] = areas.get(k); }
                LnpLamellar.Profile prof = LnpLamellar.analyse(q, a, one.a);
                double[] chosen = LnpLamellar.choose(prof, 2 * lipid);
                if (chosen != null) thickness = chosen[0];
                if (sb != null) {
                    sb.append(both.length > 1 ? String.format("%n  PHASE %d%n", i + 1) : "\n");
                    sb.append(LnpLamellar.describe(prof, lipid));
                }
            }
            drawn.add(new LnpStackCartoon.Phase(both.length > 1 ? "Phase " + (i + 1) : "Lamellar", one.a,
                    both.length > 1 ? one.phaseShare : Double.NaN, one.domain, thickness));
        }
        if (drawn.isEmpty()) {
            // the stack drawing only means anything for a lamellar phase; say which phase was fitted instead of
            // leaving an empty panel that looks broken
            stackCartoon.setEmptyMessage("Fitted " + r.lattice.symmetry + ", which is not a lamellar stack.",
                    "The stack drawing applies to lamellar phases; for this one the",
                    "phase sketch beside the indexer shows the geometry, and the",
                    "water-channel radius in the text is the size to read.");
        }
        stackCartoon.set(drawn, lipid);
    }

    private JComponent buildIndexerControls() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Fields.wrappingLabel(p, "Indexing by eye, as in Fig. 4A of Caselli et al. (2024): the allowed "
                + "reflections of a phase are drawn on the curve as sticks and slid onto the peaks together, since they share one "
                + "lattice parameter. The sketch beside them is that phase at the current parameter, with the repeat distance the "
                + "reflections measure marked on it. When the sticks sit on the peaks, <b>Use in the lattice fit</b> carries the phase "
                + "and parameter over to the whole-pattern fit, which refines them and reports the domain size and water-channel "
                + "radius."));
        p.add(Box.createVerticalStrut(4));
        JButton fromPeaks = new JButton("Take the phase from the assessment");
        fromPeaks.setToolTipText("Start from the phase the assessment indexed, if it found one");
        fromPeaks.addActionListener(e -> {
            if (assessment == null || assessment.phases.isEmpty()) { say("Run the assessment first, or pick a phase by hand"); return; }
            LnpAssessment.PhaseMatch pm = assessment.phases.get(0);
            LnpLattice.Lattice l = null;
            for (LnpLattice.Lattice c : LnpLattice.LATTICES) if (c.symmetry.startsWith(pm.symmetry.split(",")[0])) { l = c; break; }
            if (l == null) { say("The assessment's phase has no lattice entry"); return; }
            indexer.setPhase(l, pm.latticeParameter);
            say(String.format("Indexer set to %s, a = %.1f Å", l.symmetry, pm.latticeParameter));
        });
        p.add(Fields.flow(fromPeaks));
        for (Component c : p.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private void refreshIndexer() {
        if (indexer != null) indexer.refresh();
    }

    /** Lets the indexer read the current dataset and hand a phase back to the lattice fit. */
    private final class IndexerHost implements LnpIndexer.Host {
        @Override public double[][] data() {
            try { return currentData(); } catch (RuntimeException e) { return null; }
        }
        @Override public java.util.List<LnpAssessment.Peak> peaks() {
            return assessment == null ? java.util.Collections.emptyList() : assessment.peaks;
        }
        @Override public void accept(LnpLattice.Lattice lattice, double a) {
            latticeBox.setSelectedItem(lattice);
            latticeA.setText(String.format("%.1f", a));
            latticeBox2.setSelectedItem("(single phase)");
            peakMode.setSelectedIndex(1);
            say(String.format("%s with a = %.1f Å carried over; press Fit lattice to refine it", lattice.symmetry, a));
        }
        @Override public double lipidLength() { return parse(latticeLipid, 18); }
    }

    private JComponent buildSpongeControls() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Fields.wrappingLabel(p, "A sponge (L3) phase, panel G of Fig. 1 of Caselli et al. (2024), is "
                + "bicontinuous like a cubic phase but without long-range order, so it shows <b>one broad correlation peak and no "
                + "Bragg reflections</b> &mdash; the lattice fit has nothing to index. Fitting the Teubner&ndash;Strey form instead "
                + "gives the quasi-periodic repeat <i>d</i> and the correlation length &xi; over which that repeat survives. Use this "
                + "when the assessment finds a single broad peak and no higher orders, or when a cubosome has been driven into a "
                + "sponge by temperature or an additive."));
        p.add(Box.createVerticalStrut(4));
        JButton run = new JButton("Fit the sponge phase");
        run.addActionListener(e -> runSpongeFit());
        spongeQmin.setColumns(7);
        spongeQmax.setColumns(7);
        spongeQmin.setToolTipText("Leave empty to use the whole curve; exclude the Guinier region if aggregation dominates it");
        p.add(Fields.flow(new JLabel("q from"), spongeQmin, new JLabel("to"), spongeQmax, run));
        for (Component c : p.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private void runSpongeFit() {
        double[][] d;
        try { d = currentData(); } catch (RuntimeException e) { say(e.getMessage()); return; }
        double qlo = parse(spongeQmin, d[0][0]), qhi = parse(spongeQmax, d[0][d[0].length - 1]);
        try {
            spongeResult = LnpLattice.fitSponge(d[0], d[1], d[2], qlo, qhi);
            StringBuilder sb = new StringBuilder(LnpLattice.describeSponge(spongeResult));
            if (assessment != null && assessment.peaks.size() > 1)
                sb.append(String.format("%n  ! The assessment found %d peaks. Several peaks in a fixed ratio mean long-range order,%n"
                        + "    so try the lattice fit before accepting a sponge phase.%n", assessment.peaks.size()));
            phaseText.setText(sb.toString());
            phaseText.setCaretPosition(0);
            phaseChart.removeAll();
            phaseChart.add(LnpCharts.panelFor(LnpCharts.spongeChart(spongeResult, String.valueOf(datasetBox.getSelectedItem()))), BorderLayout.CENTER);
            phaseChart.revalidate();
            say(String.format("Sponge phase: d = %.1f Å, ξ = %.1f Å, reduced χ² %.2f", spongeResult.d, spongeResult.xi,
                    spongeResult.chi2Reduced));
        } catch (RuntimeException ex) {
            say("Sponge fit failed: " + ex.getMessage());
        }
    }

    private JComponent buildLatticeControls() {
        latticeBox.addItem("Identify automatically (scan all)");
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) latticeBox.addItem(l);
        latticeBox2.addItem("(single phase)");
        latticeBox2.addItem("Try a second phase automatically");
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) latticeBox2.addItem(l);
        latticeBox2.addActionListener(e -> forgetCarriedStart());
        latticeBox.addActionListener(e -> forgetCarriedStart());
        latticeBox2.setToolTipText("Cubosomes often show two coexisting cubic phases (e.g. Pn3m + Im3m); their lattice parameters "
                + "should follow the Bonnet ratios if they share one bilayer");
        JButton run = new JButton("Fit lattice");
        run.addActionListener(e -> runLatticeFit());
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Fields.wrappingLabel(p, "Fits every allowed reflection of one space group with a shared lattice parameter "
                + "and a shared domain size (Scherrer), on a diffuse background, plus an optional extra peak such as the RNA "
                + "correlation peak. This is the model for cubosomes and hexosomes, where several orders are visible "
                + "(Jin et al., Nat. Commun. 2025; Zheng et al., PNAS 2023)."));
        p.add(Fields.flow(new JLabel("Phase"), latticeBox, new JLabel("a start (Å)"), latticeA,
                new JLabel("window"), latticeQmin, new JLabel("–"), latticeQmax));
        p.add(Fields.flow(new JLabel("Second phase"), latticeBox2,
                new JLabel("dopant diameter (nm)"), latticeDopant));
        latticeDopant.setColumns(5);
        latticeDopant.setToolTipText("Inorganic nanoparticles dispersed in the lipid phase (e.g. SPIONs in magnetocubosomes): "
                + "their form factor is fitted with the lattice instead of being absorbed into the background. Leave empty for none");
        p.add(Fields.flow(new JLabel("peak shape"), latticeShape, new JLabel("extra peak q (Å⁻¹)"), latticeExtra,
                new JLabel("instrument FWHM"), latticeInst, new JLabel("lipid length (Å)"), latticeLipid, run));
        for (Component c : p.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        latticeA.setColumns(6);
        latticeQmin.setColumns(6);
        latticeQmax.setColumns(6);
        latticeExtra.setColumns(6);
        latticeInst.setColumns(6);
        latticeLipid.setColumns(4);
        latticeExtra.setToolTipText("A peak that is not a lattice reflection, e.g. the RNA correlation peak near 0.12 Å⁻¹; leave empty for none");
        latticeInst.setToolTipText("Instrumental peak width (FWHM in Å⁻¹); without it the domain size is a lower limit");
        latticeLipid.setToolTipText("Lipid monolayer length, used only to convert the lattice parameter into a water-channel radius (monoolein ≈ 18 Å)");
        return p;
    }

    /** clears the carried-over second-phase start, so a hand-picked phase is not fitted from someone else's value */
    private void forgetCarriedStart() { secondAStart = Double.NaN; }

    private void runLatticeFit() {
        double[][] d;
        try { d = currentData(); } catch (RuntimeException e) { say(e.getMessage()); return; }
        double qlo = parse(latticeQmin, d[0][0]), qhi = parse(latticeQmax, d[0][d[0].length - 1]);
        double extra = parse(latticeExtra, Double.NaN), inst = parse(latticeInst, 0), lipid = parse(latticeLipid, 18);
        double dopR = parse(latticeDopant, Double.NaN);
        if (!Double.isNaN(dopR)) dopR = dopR * 10 / 2;   // diameter in nm to radius in A
        LnpLattice.Shape shape = (LnpLattice.Shape) latticeShape.getSelectedItem();
        Object sel = latticeBox.getSelectedItem();
        say("Fitting the lattice…");
        try {
            StringBuilder sb = new StringBuilder();
            Object sel2 = latticeBox2.getSelectedItem();
            LnpLattice.Lattice first;
            double a0;
            if (sel instanceof LnpLattice.Lattice) {
                first = (LnpLattice.Lattice) sel;
                a0 = parse(latticeA, Double.NaN);
                if (Double.isNaN(a0)) {   // from the assessment's indexing when the user gave no starting value
                    if (assessment != null && !assessment.phases.isEmpty()) a0 = assessment.phases.get(0).latticeParameter;
                    else a0 = 2 * Math.PI * first.m[0] / (0.5 * (qlo + qhi));
                }
            } else {
                java.util.List<LnpLattice.Result> all = LnpLattice.identify(d[0], d[1], d[2], qlo, qhi, shape, extra, inst, 4);
                if (all.isEmpty()) { say("Not enough points in the window"); return; }
                sb.append("RANKING (whole-pattern, no peak list used)\n");
                for (LnpLattice.Result r : all)
                    sb.append(String.format("  %-34s a = %7.1f Å   reduced χ² = %7.2f%s%n", r.lattice, r.a, r.chi2Reduced,
                            r == all.get(0) ? "   <- best" : ""));
                if (all.size() > 1) {
                    double gap = all.get(1).chi2Reduced / Math.max(1e-9, all.get(0).chi2Reduced);
                    sb.append(gap > 1.5 ? "  The best lattice is clearly ahead of the alternatives.\n"
                            : "  ! The alternatives fit almost as well: treat it as tentative and look for higher orders.\n");
                }
                sb.append('\n');
                first = all.get(0).lattice;
                a0 = all.get(0).a;
                latticeResult = all.get(0);
            }
            if ("(single phase)".equals(sel2)) {
                latticeResult = LnpLattice.fit(d[0], d[1], d[2], first, a0, qlo, qhi, shape, extra, inst, dopR);
            } else {
                LnpLattice.Result single = LnpLattice.fit(d[0], d[1], d[2], first, a0, qlo, qhi, shape, extra, inst, dopR);
                LnpLattice.Result best = null;
                java.util.List<LnpLattice.Lattice> tries = new java.util.ArrayList<>();
                if (sel2 instanceof LnpLattice.Lattice) tries.add((LnpLattice.Lattice) sel2);
                else for (LnpLattice.Lattice l : LnpLattice.LATTICES) if (l != first) tries.add(l);
                for (LnpLattice.Lattice l2 : tries) {
                    // the Bonnet ratios only relate two cubic phases built on one bilayer; for anything else, and
                    // above all for two phases of the same symmetry, starting the second at the first's parameter
                    // just refits the same thing twice, so the value the indexing found is used when there is one
                    double guess = !Double.isNaN(secondAStart) && secondAStart > 0 ? secondAStart
                            : l2.cubic && first.cubic && !Double.isNaN(LnpLattice.bonnetRatio(l2)) && !Double.isNaN(LnpLattice.bonnetRatio(first))
                            ? single.a * LnpLattice.bonnetRatio(l2) / LnpLattice.bonnetRatio(first) : single.a;
                    try {
                        LnpLattice.Result two = LnpLattice.fitPair(d[0], d[1], d[2], first, single.a, l2, guess, qlo, qhi, shape, extra, inst, dopR);
                        if (two.second != null && two.second.phaseShare > 0.02 && (best == null || two.chi2 < best.chi2)) best = two;
                    } catch (RuntimeException ignored) { }
                }
                if (best != null && best.chi2 < single.chi2 - 20) {
                    latticeResult = best;
                    sb.append(String.format("A second phase improves the fit: reduced χ² %.2f → %.2f (one phase → two).%n%n",
                            single.chi2Reduced, best.chi2Reduced));
                } else {
                    latticeResult = single;
                    sb.append(best == null ? "No second phase could be fitted.\n\n"
                            : String.format("A second phase does not improve the fit (reduced χ² %.2f vs %.2f): one phase describes the data.%n%n",
                            best.chi2Reduced, single.chi2Reduced));
                }
            }
            sb.append(LnpLattice.describe(latticeResult, lipid));
            updateStack(latticeResult, lipid, sb);
            if (assessment != null && !assessment.peaks.isEmpty()) {
                LnpIndexCheck chk = new LnpIndexCheck();
                chk.set(latticeResult.lattice, latticeResult.a, assessment.peaks, 0.03);
                sb.append('\n').append(LnpIndexCheck.describe(chk.fit(), latticeResult.lattice));
            }
            phaseText.setText(sb.toString());
            phaseText.setCaretPosition(0);
            latticeA.setText(String.format("%.1f", latticeResult.a));
            phaseChart.removeAll();
            phaseChart.add(LnpCharts.panelFor(LnpCharts.latticeChart(latticeResult, String.valueOf(datasetBox.getSelectedItem()), weight())), BorderLayout.CENTER);
            phaseChart.revalidate();
            updateCartoon();
            say(String.format("%s, a = %.1f Å, domain %.0f Å, reduced χ² %.2f", latticeResult.lattice.symmetry, latticeResult.a,
                    latticeResult.domain, latticeResult.chi2Reduced));
        } catch (RuntimeException ex) {
            say("Lattice fit failed: " + ex.getMessage());
        }
    }

    private JComponent buildPhaseControls() {
        for (LnpPhasePeaks.Component c : LnpPhasePeaks.hammelDefaults())
            phaseBounds.addRow(new Object[]{c.name, c.qLo, c.qHi, String.format("%.0f", 4 * Math.PI / (c.qLo + c.qHi))});
        JTable t = new JTable(phaseBounds);
        t.setRowHeight(t.getRowHeight() + 3);
        JScrollPane ts = new JScrollPane(t);
        ts.setPreferredSize(new Dimension(420, 4 * t.getRowHeight() + 26));
        JButton run = new JButton("Decompose"), reset = new JButton("Hammel defaults");
        run.addActionListener(e -> { if (t.isEditing()) t.getCellEditor().stopCellEditing(); runPhaseDecomposition(); });
        reset.addActionListener(e -> {
            phaseBounds.setRowCount(0);
            for (LnpPhasePeaks.Component c : LnpPhasePeaks.hammelDefaults())
                phaseBounds.addRow(new Object[]{c.name, c.qLo, c.qHi, String.format("%.0f", 4 * Math.PI / (c.qLo + c.qHi))});
        });
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Fields.wrappingLabel(top, "Splits the primary internal peak into disordered, HII and Lα contributions "
                + "(three Lorentz functions with bounded centres; Hammel et al., ACS Nano 2023). The disordered/HII area ratio ranked "
                + "ASO-LNP efficacy in their screen: lower = more ordered core. Adjust the bounds for other lipid systems."));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.add(ts);
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(Fields.flow(new JLabel("Window (Å⁻¹)"), phaseQmin, new JLabel("–"), phaseQmax));
        side.add(Fields.flow(phaseBackground));
        side.add(Fields.flow(run, reset));
        row.add(side);
        top.add(row);
        for (Component c : top.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        return top;
    }

    private void runPhaseDecomposition() {
        double[][] d;
        try { d = currentData(); } catch (RuntimeException e) { say(e.getMessage()); return; }
        java.util.List<LnpPhasePeaks.Component> comps = new java.util.ArrayList<>();
        java.util.List<LnpPhasePeaks.Component> defs = LnpPhasePeaks.hammelDefaults();
        for (int r = 0; r < phaseBounds.getRowCount(); r++) {
            try {
                double lo = Double.parseDouble(String.valueOf(phaseBounds.getValueAt(r, 1))), hi = Double.parseDouble(String.valueOf(phaseBounds.getValueAt(r, 2)));
                if (!(hi > lo && lo > 0)) { say("Bounds of " + phaseBounds.getValueAt(r, 0) + " must satisfy 0 < from < to"); return; }
                comps.add(new LnpPhasePeaks.Component(String.valueOf(phaseBounds.getValueAt(r, 0)), lo, hi, defs.get(Math.min(r, defs.size() - 1)).color));
                phaseBounds.setValueAt(String.format("%.0f", 4 * Math.PI / (lo + hi)), r, 3);
            } catch (NumberFormatException ex) { say("Bounds must be numbers"); return; }
        }
        try {
            phaseResult = LnpPhasePeaks.fit(d[0], d[1], d[2], comps, Double.parseDouble(phaseQmin.getText().trim()),
                    Double.parseDouble(phaseQmax.getText().trim()), phaseBackground.isSelected());
        } catch (RuntimeException ex) { say("Decomposition failed: " + ex.getMessage()); return; }
        phaseText.setText(LnpPhasePeaks.describe(phaseResult));
        phaseText.setCaretPosition(0);
        phaseChart.removeAll();
        phaseChart.add(LnpCharts.panelFor(LnpCharts.phaseChart(phaseResult, String.valueOf(datasetBox.getSelectedItem()))), BorderLayout.CENTER);
        phaseChart.revalidate();
        say(String.format("Disordered/HII area ratio %.2f", phaseResult.disorderedToHII()));
    }

    private JComponent buildPopulationTab() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(6, 8, 4, 8));
        top.add(Fields.wrappingLabel(top, "Like classifying particles in cryo-EM, but for the whole sample at once: "
                + "the curve is described as a mix of particle classes, each with its own size. <b>Suggest from the data</b> adds a class only "
                + "when the fit improves strongly (BIC drop &gt; 10). <b>Test my proposal</b> fits the classes you tick and compares."));
        JPanel boxes = Fields.flow(new JLabel("Your proposal:  solid core (loaded) +"));
        for (LnpPopulations.Kind k : LnpPopulations.Kind.values()) {
            if (k == LnpPopulations.Kind.LOADED) continue;
            JCheckBox cb = new JCheckBox(k.label);
            cb.setForeground(k.color.darker());
            proposal.put(k, cb);
            boxes.add(cb);
        }
        top.add(boxes);
        suggestPop.addActionListener(e -> runPopulations(true));
        testPop.addActionListener(e -> runPopulations(false));
        popStatus.setForeground(new Color(0x6e7781));
        top.add(Fields.flow(suggestPop, testPop, popStatus));
        for (Component c : top.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        popText.setEditable(false);
        popText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        popText.setText("Fit a model first (2. Fit model). The populations are built on the fitted particle.\n\n" + LnpPopulations.caveats());
        popChart.add(LnpCharts.panelFor(FormFactorCharts.placeholder("Population histogram appears here")), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, popChart, new JScrollPane(popText));
        split.setResizeWeight(0.55);
        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private void runPopulations(boolean suggest) {
        if (result == null || worker != null) { popStatus.setText("Fit a model first (2. Fit model)"); return; }
        final LnpFitter.Result base = result;
        final java.util.EnumSet<LnpPopulations.Kind> kinds = java.util.EnumSet.of(LnpPopulations.Kind.LOADED);
        if (suggest) kinds.addAll(java.util.EnumSet.allOf(LnpPopulations.Kind.class));
        if (cargoBox.getSelectedItem() == LnpExpectations.Cargo.NONE) kinds.remove(LnpPopulations.Kind.FREE_RNA);
        else for (java.util.Map.Entry<LnpPopulations.Kind, JCheckBox> e : proposal.entrySet()) if (e.getValue().isSelected()) kinds.add(e.getKey());
        if (!suggest && kinds.size() == 1) { popStatus.setText("Tick at least one class to test"); return; }
        final double shift = composition != null ? composition.coreContrast(Double.POSITIVE_INFINITY, 0.24) - composition.coreContrast(parse(npField, 6), 0.24) : -0.005;
        // the single-population fit absorbed the other classes into the particle's internal structure; with a known
        // composition, start the loaded particle from physical densities instead (size, spread and peak from the fit)
        final LnpFitter.Result start;
        if (composition != null && base.model.shells == 3) {
            double[] ph = LnpModel.defaults();
            boolean cargo = cargoBox.getSelectedItem() != LnpExpectations.Cargo.NONE;
            ph[LnpModel.RHO_1] = composition.innerShellContrast(gelChains());
            ph[LnpModel.RHO_C] = composition.coreContrast(cargo ? parse(npField, 6) : Double.POSITIVE_INFINITY, 0.24);
            double[] cor = composition.corona(base.params[LnpModel.R0], base.params[LnpModel.R0] + 23);
            if (cor[0] > 0) { ph[LnpModel.T3] = cor[0]; ph[LnpModel.RHO_3] = Math.max(0, cor[1]); }
            for (int k : new int[]{LnpModel.R0, LnpModel.SIGMA, LnpModel.PEAK_Q, LnpModel.PEAK_W, LnpModel.PEAK_A, LnpModel.SCALE, LnpModel.BG})
                ph[k] = base.params[k];
            start = new LnpFitter(base.model, base.q, base.I, base.sigma).evaluate(ph, base.fixed);
        } else start = base;
        // cargo for the free-RNA class: molecules per loaded particle from the expectations
        LnpExpectations ex = expectations();
        LnpPopulations.setCargo(ex.cargo.nucleotides(ex.length), ex.cargo == LnpExpectations.Cargo.NONE ? Double.NaN
                : ex.copiesOverDistribution(base.model, base.params)[0]);
        suggestPop.setEnabled(false);
        testPop.setEnabled(false);
        popStatus.setText(suggest ? "Searching and profiling… (about 1–2 min)" : "Fitting your proposal and the data's suggestion… (2–4 min)");
        new SwingWorker<LnpPopulations.Result[], Void>() {
            @Override protected LnpPopulations.Result[] doInBackground() {
                LnpPopulations.Result single = new LnpPopulations(base, shift).baseline();
                LnpPopulations.Result r = LnpPopulations.alternate(start, shift, kinds, suggest, 3);
                LnpPopulations.Result other = suggest ? null : LnpPopulations.alternate(start, shift, java.util.EnumSet.allOf(LnpPopulations.Kind.class), true, 3);
                return new LnpPopulations.Result[]{r, single, other};
            }

            @Override protected void done() {
                suggestPop.setEnabled(true);
                testPop.setEnabled(true);
                try {
                    LnpPopulations.Result[] rr = get();
                    population = rr[0];
                    StringBuilder sb = new StringBuilder();
                    sb.append(LnpPopulations.describe(rr[0], suggest ? "SUGGESTED BY THE DATA" : "YOUR PROPOSAL", rr[1], "single population")).append('\n');
                    if (!suggest && rr[2] != null) {
                        double d = rr[0].bic - rr[2].bic;
                        sb.append(LnpPopulations.describe(rr[2], "FOR COMPARISON: SUGGESTED BY THE DATA", rr[1], "single population")).append('\n');
                        sb.append(d < -2 ? "Verdict: your proposal describes the data better than the data-driven suggestion.\n"
                                : d <= 10 ? String.format("Verdict: your proposal and the suggestion fit about equally well (ΔBIC %+.1f). SAXS cannot decide; cryo-EM can.%n", d)
                                : String.format("Verdict: the data favour the suggestion (ΔBIC %+.1f > 10 is strong evidence against your proposal).%n", d));
                        for (LnpPopulations.Kind k : kinds)
                            if (k != LnpPopulations.Kind.LOADED && rr[0].get(k) == null)
                                sb.append("  ").append(k.label).append(": not supported by the data (amount not significant).\n");
                        sb.append('\n');
                    }
                    if (rr[0].profile != null) {
                        sb.append("How well is the solid-core fraction determined? χ² with the fraction held fixed:\n");
                        double mn = Double.POSITIVE_INFINITY;
                        for (double[] pf : rr[0].profile) mn = Math.min(mn, pf[1]);
                        for (double[] pf : rr[0].profile) {
                            int bars = (int) Math.min(50, Math.round((pf[1] - mn) / Math.max(1, 0.005 * mn)));
                            sb.append(String.format("  %3.0f %%  %8.1f  %s%n", 100 * pf[0], pf[1], "▇".repeat(Math.max(0, 50 - bars))));
                        }
                        sb.append("  (longer bar = better fit; a flat top means the fraction is poorly determined)\n\n");
                    }
                    sb.append("Steps:\n");
                    for (String l : rr[0].log) sb.append("  ").append(l).append('\n');
                    sb.append('\n').append(LnpPopulations.caveats());
                    popText.setText(sb.toString());
                    popText.setCaretPosition(0);
                    popChart.removeAll();
                    popChart.add(LnpCharts.panelFor(LnpCharts.populationChart(rr[0])), BorderLayout.CENTER);
                    popChart.revalidate();
                    popStatus.setText(String.format("%d class(es); reduced χ² %.2f. The simulated cryo-EM view now shows this mix.",
                            rr[0].components.size(), rr[0].chi2Reduced()));
                    updateCartoon();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    popStatus.setText("Population analysis failed: " + c.getMessage());
                }
            }
        }.execute();
    }

    private JComponent buildChecksTab() {
        checksPane.setEditable(false);
        checksPane.setBorder(new EmptyBorder(8, 10, 8, 10));
        return new JScrollPane(checksPane);
    }

    private JComponent buildCartoonTab() {
        JPanel p = new JPanel(new BorderLayout());
        interiorBox.addItem("Interior: automatic (from the peaks)");
        for (LnpCartoon.Interior in : LnpCartoon.Interior.values()) if (in != LnpCartoon.Interior.COEXIST) interiorBox.addItem(in);
        interiorBox.setToolTipText("Override the drawn core interior, e.g. after checking a cryo-EM image; spacing stays the measured one");
        viewBox.addActionListener(e -> cartoon.setView((LnpCartoon.View) viewBox.getSelectedItem()));
        interiorBox.addActionListener(e -> updateCartoon());
        JButton shuffle = new JButton("New arrangement"), png = new JButton("PNG…"), svg = new JButton("SVG…");
        shuffle.setToolTipText("Redraw the random parts (disordered packing, simulated particle field)");
        shuffle.addActionListener(e -> cartoon.reshuffle());
        png.addActionListener(e -> exportCartoon(false));
        svg.addActionListener(e -> exportCartoon(true));
        JPanel bar = Fields.flow(new JLabel("View"), viewBox, interiorBox, shuffle, png, svg);
        bar.setBorder(new EmptyBorder(4, 4, 4, 4));
        p.add(bar, BorderLayout.NORTH);
        p.add(cartoon, BorderLayout.CENTER);
        return p;
    }

    private void exportCartoon(boolean svg) {
        if (cartoon.getSpec() == null) { say("Nothing to export yet: fit a model first"); return; }
        JFileChooser fc = new JFileChooser(workingDirectory.get());
        String view = String.valueOf(cartoon.getView()).toLowerCase().replaceAll("[^a-z]+", "_");
        fc.setSelectedFile(new File("lnp_" + view + (svg ? ".svg" : ".png")));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            int w = Math.max(900, cartoon.getWidth()), h = Math.max(650, cartoon.getHeight());
            File out = fc.getSelectedFile();
            if (svg) out = cartoon.saveSvgOrPng(out, w, h); else cartoon.savePng(out, 2 * w, 2 * h);
            say("Saved " + out.getName() + (svg && out.getName().endsWith(".png") ? " (the simulated micrograph is a bitmap, saved as PNG)" : ""));
        } catch (IOException ex) {
            say("Could not save: " + ex.getMessage());
        }
    }

    /** current expectations from the form; unparsable fields fall back to defaults */
    LnpExpectations expectations() {
        LnpExpectations e = new LnpExpectations();
        e.cargo = (LnpExpectations.Cargo) cargoBox.getSelectedItem();
        e.length = parse(cargoLength, e.cargo.defaultLength);
        e.np = parse(npField, 6);
        e.ionizableMolPercent = parse(ilField, 50);
        e.expectedDiameterMin = parse(dMin, Double.NaN);
        e.expectedDiameterMax = parse(dMax, Double.NaN);
        if (!Double.isNaN(e.expectedDiameterMin) && Double.isNaN(e.expectedDiameterMax)) e.expectedDiameterMax = e.expectedDiameterMin;
        if (composition != null) {
            e.lipidVolume = composition.meanLipidVolume();
            e.expectedPhase = composition.expectedPhase();
        }
        return e;
    }

    private static double parse(JTextField f, double def) {
        try {
            String t = f.getText().trim();
            return t.isEmpty() ? def : Double.parseDouble(t);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    /** reference assessment over the same q range, or null */
    private LnpAssessment.Report referenceAssessment() {
        int r = referenceBox.getSelectedIndex() - 1;
        if (r < 0 || r == datasetBox.getSelectedIndex()) return null;
        try {
            double qmin = Fields.pos(qminField, "q min"), qmax = Fields.pos(qmaxField, "q max");
            double[][] d = FormFactorPanel.extract(provider.data(r), qmin, qmax);
            return d[0].length < 20 ? null : LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN,
                    cargoBox.getSelectedItem() != LnpExpectations.Cargo.NONE);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void updateChecks() {
        if (assessment == null || lastData == null) {
            checksPane.setText("<html><body style='font-family:sans-serif;color:#6e7781'>Press <b>1. Assess sample</b> (and then <b>2. Fit model</b>) to check the sample against your expectations.</body></html>");
            return;
        }
        LnpFitter.Result fit = result != null && resultName.equals(String.valueOf(datasetBox.getSelectedItem())) ? result : null;
        checks = expectations().evaluate(lastData[0], lastData[1], lastData[2], assessment, fit, referenceAssessment());
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px'>");
        h.append("<h3 style='margin:2px 0 8px 0'>Is the sample what you expect?</h3>");
        if (fit == null) h.append("<p style='color:#6e7781'>Size, aggregation and cargo-per-particle checks need a model fit (2. Fit model).</p>");
        h.append("<table cellspacing='0' cellpadding='5'>");
        for (LnpExpectations.Check c : checks) {
            String bg, label;
            switch (c.status) {
                case PASS: bg = "#16a34a"; label = "OK"; break;
                case CAUTION: bg = "#d97706"; label = "CHECK"; break;
                case FAIL: bg = "#dc2626"; label = "NO"; break;
                default: bg = "#2563eb"; label = "INFO"; break;
            }
            h.append("<tr><td valign='top'><span style='background:").append(bg)
             .append(";color:white;font-weight:bold;padding:2px 6px'>&nbsp;").append(label).append("&nbsp;</span></td>")
             .append("<td valign='top'><b>").append(esc(c.title)).append("</b><br>").append(esc(c.detail)).append("</td></tr>");
        }
        h.append("</table><p style='color:#6e7781'>SAXS sees the average structure. Encapsulation efficiency and RNA integrity need "
                + "biochemical assays; cryo-EM confirms the internal phase.</p></body></html>");
        checksPane.setText(h.toString());
        checksPane.setCaretPosition(0);
    }

    private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ------------------------------------------------------------------ particle picture

    /** builds the cartoon description from the fitted (or current) parameters, the assessment and the expectations */
    private void updateCartoon() {
        LnpModel m = result != null ? result.model : model;
        double[] p = result != null ? result.params : params;
        double[] e = result != null ? result.errors : errors;
        boolean[] fx = result != null ? result.fixed : fixed;
        LnpCartoon.Spec sp = new LnpCartoon.Spec();
        sp.r0 = p[LnpModel.R0];
        sp.sigma = p[LnpModel.SIGMA];
        double[] r = m.radii(p, p[LnpModel.R0]);
        String[] names = m.layerNames();
        for (int k = 0; k <= m.shells; k++) {
            LnpCartoon.Layer L = new LnpCartoon.Layer();
            L.name = names[Math.min(k, names.length - 1)];
            L.outer = r[k];
            L.rho = p[LnpModel.rhoIndex(k)];
            if (k > 0) {
                int t = LnpModel.tIndex(k);
                boolean assumed = fx[t];
                boolean poor = !assumed && !Double.isNaN(e[t]) && e[t] > 0.3 * p[t];
                L.uncertain = assumed || poor || result == null;
                L.thickness = assumed ? String.format("%.1f nm (assumed)", p[t] / 10)
                        : Double.isNaN(e[t]) ? String.format("%.1f nm", p[t] / 10)
                        : String.format("%.1f ± %.1f nm%s", p[t] / 10, e[t] / 10, poor ? " (poorly determined)" : "");
            }
            sp.layers.add(L);
        }
        sp.coreLabel = Double.isNaN(e[LnpModel.R0]) ? String.format("R₀ = %.1f nm", p[LnpModel.R0] / 10)
                : String.format("R₀ = %.1f ± %.1f nm", p[LnpModel.R0] / 10, e[LnpModel.R0] / 10);
        sp.title = result == null ? "Particle model – starting values (not fitted yet)"
                : String.format("Particle model – %s (outer diameter %.0f nm)", resultName.replaceFirst("^\\d+: ", ""), 2 * m.outerRadius(p) / 10);

        LnpExpectations ex = expectations();
        sp.cargoKind = ex.cargo == LnpExpectations.Cargo.NONE ? 0 : ex.cargo == LnpExpectations.Cargo.SIRNA ? 1 : 2;
        sp.cargoLength = ex.length;
        sp.cargo = sp.cargoKind > 0;
        sp.ionizableFraction = ex.ionizableMolPercent / 100;
        sp.vesicle = m.isVesicle();
        chooseInterior(sp, m, p);
        if (m.isVesicle() && !(interiorBox.getSelectedItem() instanceof LnpCartoon.Interior)) {
            sp.interior = LnpCartoon.Interior.UNIFORM;
            sp.interiorNote = "Water-filled interior (vesicle, Fig. 5d).";
        }
        if (population != null && result != null && population.loadedFit != null && population.components.size() > 1)
            for (LnpPopulations.Component c : population.components)
                sp.population.add(new double[]{c.kind.ordinal(), c.kind == LnpPopulations.Kind.AGGREGATE ? c.intensityShare : c.numberFraction,
                        c.size, c.sigmaRel, Double.isNaN(c.ratio) ? 0 : c.ratio});
        Object choice = interiorBox.getSelectedItem();
        if (choice instanceof LnpCartoon.Interior && choice != sp.interior) {
            LnpCartoon.Interior forced = (LnpCartoon.Interior) choice;
            double d = mainSpacing(m, p);
            if (!Double.isNaN(d) || forced == LnpCartoon.Interior.UNIFORM) {
                sp.interior = forced;
                if (!Double.isNaN(d)) sp.spacing = forced == LnpCartoon.Interior.HEXAGONAL ? 2 * d / Math.sqrt(3)
                        : forced == LnpCartoon.Interior.CUBIC ? d * Math.sqrt(6) : d;
                if (forced == LnpCartoon.Interior.CUBIC && sp.symmetry.isEmpty()) sp.symmetry = "Ia3d";
                sp.interiorNote = "Interior chosen by you; spacing from the measured main peak.";
                sp.interiorUncertain = false;
            }
        }
        cartoon.setSpec(sp);
    }

    /** d = 2 pi / q of the main internal peak (fit peak first, then the assessment), NaN if none */
    private double mainSpacing(LnpModel m, double[] p) {
        if (m.peak && result != null) return 2 * Math.PI / p[LnpModel.PEAK_Q];
        if (assessment != null && !assessment.peaks.isEmpty()) {
            LnpAssessment.Peak b = assessment.peaks.get(0);
            for (LnpAssessment.Peak k : assessment.peaks) if (k.area > b.area) b = k;
            return b.d();
        }
        return Double.NaN;
    }

    private static LnpCartoon.Interior interiorOf(LnpAssessment.PhaseMatch pm) {
        if (pm.isLamellar()) return LnpCartoon.Interior.LAMELLAR;
        if (pm.isHexagonal() || pm.symmetry.contains("Fd3m")) return LnpCartoon.Interior.HEXAGONAL;
        return LnpCartoon.Interior.CUBIC;
    }

    private void chooseInterior(LnpCartoon.Spec sp, LnpModel m, double[] p) {
        LnpAssessment.Report a = assessment;
        if (a != null && a.coexistence != null) {
            LnpAssessment.PhaseMatch c0 = a.coexistence[0], c1 = a.coexistence[1];
            if (c0.latticeParameter > c1.latticeParameter) { LnpAssessment.PhaseMatch t = c0; c0 = c1; c1 = t; }
            sp.interior = LnpCartoon.Interior.COEXIST;
            sp.centre = interiorOf(c0); sp.spacing = c0.latticeParameter; sp.symmetry = c0.symmetry;
            sp.periphery = interiorOf(c1); sp.spacing2 = c1.latticeParameter; sp.symmetry2 = c1.symmetry;
            sp.interiorNote = "Two lattices from the Bragg peaks; where each sits in the particle is not known from SAXS.";
        } else if (a != null && !a.phases.isEmpty()) {
            LnpAssessment.PhaseMatch pm = a.phases.get(0);
            sp.interior = interiorOf(pm);
            sp.spacing = pm.latticeParameter;
            sp.symmetry = pm.symmetry;
            sp.interiorNote = String.format("From %d indexed Bragg peaks (%s).", pm.matched, pm.symmetry);
        } else if ((m.peak && result != null) || (a != null && a.mesophasePeak)) {
            double q = m.peak && result != null ? p[LnpModel.PEAK_Q] : a.peaks.get(0).q;
            double fwhm = m.peak && result != null ? 2.3548 * p[LnpModel.PEAK_W] : a.peaks.get(0).fwhm;
            sp.interior = LnpCartoon.Interior.MESOPHASE;
            sp.spacing = 2 * Math.PI / q;
            sp.correlation = fwhm > 0 ? 2 * Math.PI / fwhm : Double.POSITIVE_INFINITY;
            sp.interiorNote = "Broad peak: disordered inverse hexagonal packing (Yanez Arteta et al. 2018), domain size from the peak width.";
        } else if (a != null && a.peaks.size() == 1) {
            sp.interior = LnpCartoon.Interior.LAMELLAR;
            sp.spacing = a.peaks.get(0).d();
            sp.interiorUncertain = true;
            sp.interiorNote = "One sharp peak: drawn as lamellar, but inverse hexagonal fits equally well. Use the Interior menu after cryo-EM.";
        } else {
            sp.interior = LnpCartoon.Interior.UNIFORM;
            sp.interiorNote = a == null ? "Run the assessment to look for internal structure." : "No internal peak: no ordered internal structure resolved.";
        }
    }

    private JPanel runBox() {
        Fields.Form f = new Fields.Form(titled("Run"));
        fitButton.setToolTipText("Refine the ticked parameters against the data");
        calcButton.setToolTipText("Draw the model with the values in the table, without fitting");
        f.full(Fields.flow(fitButton, calcButton, resetButton, stopButton));
        progress.setIndeterminate(false);
        f.full(progress);
        status.setForeground(new Color(0x6e7781));
        f.full(status);
        f.full(Fields.flow(saveButton));
        return f.panel();
    }

    // ------------------------------------------------------------------ datasets

    public void refreshDatasets() {
        if (worker != null) return;
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
        Object refSel = referenceBox.getSelectedItem();
        DefaultComboBoxModel<String> rm = new DefaultComboBoxModel<>();
        rm.addElement("(none)");
        for (int i = 0; i < m.getSize(); i++) rm.addElement(m.getElementAt(i));
        referenceBox.setModel(rm);
        if (refSel != null && rm.getIndexOf(refSel) >= 0) referenceBox.setSelectedItem(refSel);
        // the wide-angle list offers the same datasets; the one to pick is whichever reaches q ~ 1.5 A^-1
        Object waxsSel = waxsBox.getSelectedItem();
        DefaultComboBoxModel<String> wm = new DefaultComboBoxModel<>();
        for (int i = 0; i < m.getSize(); i++) wm.addElement(m.getElementAt(i));
        waxsBox.setModel(wm);
        if (waxsSel != null && wm.getIndexOf(waxsSel) >= 0) waxsBox.setSelectedItem(waxsSel);
        else suggestWaxsDataset(n);
        boolean any = n > 0;
        assessButton.setEnabled(any);
        fitButton.setEnabled(any);
        calcButton.setEnabled(any);
        if (!any) {
            datasetInfo.setText("No datasets loaded – load files in the Analysis panel");
            return;
        }
        if (sel != null && m.getIndexOf(sel) >= 0) datasetBox.setSelectedItem(sel);
        else datasetBox.setSelectedIndex(0);
        datasetChanged();
        refreshSeriesTable();
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
        if (all == null || all[0].length == 0) { datasetInfo.setText("Dataset is empty"); return; }
        double lo = Double.POSITIVE_INFINITY, hi = 0;
        for (double x : all[0]) if (x > 0) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        boolean same = lo == dataQmin && hi == dataQmax;
        dataQmin = lo;
        dataQmax = hi;
        datasetInfo.setText(String.format("%d points, q %.4f – %.4f Å⁻¹", all[0].length, lo, hi));
        if (i != assessedIndex) {   // results of another dataset no longer apply
            assessment = null;
            lastData = null;
            updateChecks();
        }
        if (!same || qminField.getText().trim().isEmpty()) {
            qminField.setText(String.format("%.5f", lo));
            qmaxField.setText(String.format("%.5f", Math.min(hi, 0.5)));
        }
    }

    /** {q, I, sigma} of the selected dataset in the chosen q range */
    private double[][] currentData() {
        int idx = datasetBox.getSelectedIndex();
        if (idx < 0) throw new IllegalArgumentException("Select a dataset first");
        double qmin = Fields.pos(qminField, "q min"), qmax = Fields.pos(qmaxField, "q max");
        Fields.order(qmin, qmax, "q range");
        double[][] d = FormFactorPanel.extract(provider.data(idx), qmin, qmax);
        if (d[0].length < 20) throw new IllegalArgumentException("Fewer than 20 points with errors in the q range");
        return d;
    }

    // ------------------------------------------------------------------ actions

    /**
     * The text under the particle dropdown, which is the template's description plus anything the current
     * assessment has to say about choosing that template on this data.
     *
     * <p>This has to be refreshed when the assessment changes and not only when the template does. Running the
     * assessment after picking a model is the natural order — look at the data, then decide what to fit — and
     * it was the one order in which the warning never appeared, because the help had already been written while
     * there was no assessment to consult.
     */
    private void updatePresetHelp() {
        Preset p = (Preset) presetBox.getSelectedItem();
        if (p == null) return;
        String help = p.help;
        // A filled core and a hollow shell scatter differently in a way no amount of fitting can bridge: the
        // deep minima of a thin shell at large radius cannot be made by a core with shells on it. Saying so
        // where the model is chosen saves the user discovering it from a reduced chi-squared in the hundreds.
        if (assessment != null && assessment.formFactorLobe && !model.isVesicle() && p.shells > 0)
            help += "<br><br><b>The assessment found a form-factor lobe in this data</b>, which comes from a "
                    + "bilayer shell. A model with a filled core cannot reproduce the deep minima that go with it; "
                    + "choose <b>Vesicle / bilayer shell</b> unless you have a reason not to.";
        presetHelp.setText("<html><div style='width:290px'>" + help + "</div></html>");
    }

    /** visible for testing: the help text currently shown under the particle dropdown */
    String presetHelpText() { return presetHelp.getText(); }

    /** visible for testing: run the assessment as the user does from the button */
    void runAssessment() { assess(); }

    /** visible for testing: whether the current assessment found a bilayer form-factor lobe */
    boolean assessmentFoundFormFactorLobe() { return assessment != null && assessment.formFactorLobe; }

    /** visible for testing: choose a particle template exactly as the user does from the dropdown */
    void selectPreset(Preset p) { presetBox.setSelectedItem(p); }

    /** visible for testing: whether a parameter is currently held fixed */
    boolean isFixed(int k) { return fixed[k]; }

    private void presetChanged() {
        Preset p = (Preset) presetBox.getSelectedItem();
        if (p == null) return;
        boolean wasVesicle = model.isVesicle();
        model = new LnpModel(p.shells, p.peak);
        if (model.isVesicle() != wasVesicle) {
            // different architecture: start from its template (Fig. 5 d vs e/f) rather than reusing the other's values
            double[] keep = {params[LnpModel.SCALE], params[LnpModel.BG]};
            params = model.templateDefaults();
            fixed = model.templateFixed();
            errors = nanArray();
            params[LnpModel.SCALE] = keep[0];
            params[LnpModel.BG] = keep[1];
            result = null;
        }
        // The bilayer-shell model is only usable with its headgroup thicknesses free: held at the 8 A template
        // value they cannot absorb the real head-group size, and the misfit lands on the minima instead. Do this
        // whichever way the model was chosen, not only when the assessment suggested it.
        if (model.isVesicle()) prepareBilayerShell(!wasVesicle);
        updatePresetHelp();
        // a homogeneous sphere has a single density: it is only a reference
        if (p.shells == 0) fixed[LnpModel.RHO_C] = true;
        else if (!model.isVesicle() && fixed[LnpModel.RHO_C] && !fixed[LnpModel.RHO_1]) fixed[LnpModel.RHO_C] = false;
        if (p.shells >= 1 && !anyDensityFixed()) fixed[model.isVesicle() ? LnpModel.RHO_2 : LnpModel.RHO_1] = true;
        tableModel.rebuild();
        if (modelEditor != null) modelEditor.repaint();
        updateCartoon();
    }

    /** which layer a thickness parameter belongs to, or -1 */
    private static int layerOf(int k) {
        for (int layer = 1; layer <= LnpModel.MAX_SHELLS; layer++) if (LnpModel.tIndex(layer) == k) return layer;
        return -1;
    }

    private boolean anyDensityFixed() {
        for (int k = 0; k < LnpModel.NPAR; k++) if (LnpModel.isDensity(k) && model.uses(k) && fixed[k]) return true;
        return false;
    }

    private void reset() {
        params = LnpModel.defaults();
        fixed = LnpModel.defaultFixed();
        params = model.templateDefaults();
        fixed = model.templateFixed();
        errors = nanArray();
        presetChanged();
        if (modelEditor != null) modelEditor.repaint();
        say("Starting values reset to the template");
    }

    private void assess() {
        double[][] d;
        try { d = currentData(); } catch (RuntimeException e) { say(e.getMessage()); return; }
        assessment = LnpAssessment.analyse(d[0], d[1], d[2], Double.NaN,
                    cargoBox.getSelectedItem() != LnpExpectations.Cargo.NONE);
        lastData = d;
        checkUnits(d[0]);
        assessedIndex = datasetBox.getSelectedIndex();
        updatePresetHelp();   // the new assessment may have something to say about the model already chosen
        String name = String.valueOf(datasetBox.getSelectedItem());
        lastData = d;
        checkUnits(d[0]);
        setChart(assessTab, LnpCharts.assessmentChart(d[0], d[1], d[2], assessment, "Assessment – " + name, weight()));
        // seed starting values from what was found
        if (assessment.mesophasePeak) {
            LnpAssessment.Peak pk = strongest(assessment.peaks);
            params[LnpModel.PEAK_Q] = pk.q;
            if (pk.fwhm > 0) params[LnpModel.PEAK_W] = pk.fwhm / 2.355;
            params[LnpModel.PEAK_A] = Math.max(pk.height, 1e-12);
        }
        Suggestion sg = suggestionFor(assessment);
        suggested = sg.preset;
        suggestedPath = sg.path;
        double shell = 0;
        for (int k = 1; k <= 3; k++) shell += params[LnpModel.T1 + k - 1];
        if (!Double.isNaN(assessment.oscillationRadius)) params[LnpModel.R0] = Math.max(20, assessment.oscillationRadius - shell + params[LnpModel.T3]);
        else if (assessment.guinier.found) params[LnpModel.R0] = Math.max(20, assessment.guinier.rg * Math.sqrt(5.0 / 3.0) - shell);
        tableModel.fireTableDataChanged();

        if ("lattice".equals(suggestedPath)) {
            if (assessment.coexistence != null) {
                LnpAssessment.PhaseMatch a = assessment.coexistence[0], b = assessment.coexistence[1];
                suggestLabel.setText("<html><div style='width:290px'>Two phases were indexed:<br><b>"
                        + a.symmetry + "</b>, a ≈ " + String.format("%.1f nm", a.latticeParameter / 10) + "<br><b>"
                        + b.symmetry + "</b>, a ≈ " + String.format("%.1f nm", b.latticeParameter / 10)
                        + "<br><br>Next: fit both together, which refines the two lattice parameters and gives the "
                        + "domain size and water geometry of each.</div></html>");
            } else {
                LnpAssessment.PhaseMatch pm = assessment.phases.get(0);
                suggestLabel.setText("<html><div style='width:290px'><b>" + pm.phase + " [" + pm.symmetry + "]</b> was indexed, a ≈ "
                        + String.format("%.1f nm", pm.latticeParameter / 10) + ".<br><br>Next: fit every reflection at once, "
                        + "which refines the lattice parameter and gives the domain size and water geometry.</div></html>");
            }
            // runs the fit rather than only navigating to it: landing on a tab of filled-in fields leaves the user
            // to guess that a button still has to be pressed
            suggestButton.setText(assessment.coexistence != null ? "Fit both phases now" : "Fit the lattice now");
            suggestButton.setVisible(true);
        } else if ("sponge".equals(suggestedPath)) {
            LnpAssessment.Peak p = assessment.peaks.get(0);
            suggestLabel.setText("<html><div style='width:290px'>One broad peak at <b>d ≈ "
                    + String.format("%.1f nm", p.d() / 10) + "</b> with no higher orders: this looks like a <b>sponge (L3) "
                    + "phase</b>, which has no lattice to index.<br>Next: the sponge fit in the Structure tab.</div></html>");
            suggestButton.setText("Go to the sponge fit");
            suggestButton.setVisible(true);
        } else if (suggested != null) {
            suggestLabel.setText("<html><div style='width:290px'>Suggested: <b>" + suggested.label + "</b>."
                    + ("fit+populations".equals(suggestedPath) ? " Then run Populations." : "") + "</div></html>");
            suggestButton.setText("Use suggested model");
            suggestButton.setVisible(suggested != presetBox.getSelectedItem());
        } else {
            suggestLabel.setText("<html><div style='width:290px'>Peaks are present but could not be indexed: see the Report. "
                    + "The model fit describes the particle envelope only.</div></html>");
            suggestButton.setVisible(false);
        }
        updateChecks();
        updateCartoon();
        report.setText(unitLine() + assessment.text + "\n\n" + LnpExpectations.text(checks) + (result != null ? "\n\n" + fitReport(result) : ""));
        report.setCaretPosition(0);
        showTabFor(assessTab);
        say("Assessment done; see the Report tab for the full text");
    }

    private void applySuggestion() {
        if ("sponge".equals(suggestedPath) && assessment != null) {
            if (spongeQmin.getText().trim().isEmpty()) spongeQmin.setText(qminField.getText());
            if (spongeQmax.getText().trim().isEmpty()) spongeQmax.setText(qmaxField.getText());
            peakMode.setSelectedIndex(3);
            showTabFor(phaseChart);
            suggestButton.setVisible(false);
            runSpongeFit();
            return;
        }
        if ("lattice".equals(suggestedPath) && assessment != null) {
            LnpAssessment.PhaseMatch pm = assessment.coexistence != null ? assessment.coexistence[0] : assessment.phases.get(0);
            LnpLattice.Lattice l = LnpLattice.byName(pm.symmetry.split(",")[0]);
            latticeBox.setSelectedItem(l == null ? latticeBox.getItemAt(0) : l);
            latticeA.setText(String.format("%.1f", pm.latticeParameter));
            secondAStart = Double.NaN;
            if (assessment.coexistence != null) {
                LnpLattice.Lattice l2 = LnpLattice.byName(assessment.coexistence[1].symmetry.split(",")[0]);
                if (l2 != null) {
                    latticeBox2.setSelectedItem(l2);
                    secondAStart = assessment.coexistence[1].latticeParameter;
                }
            }
            if (latticeQmin.getText().trim().isEmpty()) latticeQmin.setText(qminField.getText());
            if (latticeQmax.getText().trim().isEmpty()) latticeQmax.setText(qmaxField.getText());
            peakMode.setSelectedIndex(1);
            showTabFor(phaseChart);
            suggestButton.setVisible(false);
            runLatticeFit();
            return;
        }
        if (suggested == null) return;
        presetBox.setSelectedItem(suggested);
        if (assessment != null && !assessment.phases.isEmpty() && !assessment.peaks.isEmpty()) {
            double cut = assessment.peaks.get(0).q - 2 * Math.max(assessment.peaks.get(0).fwhm, 0.002);
            qmaxField.setText(String.format("%.5f", cut));
        }
        String extra = "";
        if (suggested == Preset.VESICLE && assessment != null && assessment.formFactorLobe)
            extra = prepareBilayerShell();
        suggestButton.setVisible(false);
        say("Model set to " + suggested.label + extra + ". Press Fit");
    }

    /**
     * Sets up the bilayer-shell model for a particle whose structure is in its shells. Two changes to the
     * template matter enough to make here rather than leave to the user: a sample with no PEG-lipid has no
     * corona, and leaving one in the model lets it absorb the misfit instead of showing it; and the headgroup
     * thicknesses, fixed in the template, are what the form-factor oscillations actually measure, so they should
     * be free when those oscillations are present.
     *
     * @return a phrase for the status line describing what was changed
     */
    private String prepareBilayerShell() {
        return prepareBilayerShell(true);
    }

    /**
     * @param mayZeroCorona whether the corona may be set to zero. True when the model has just been built from its
     *                      template; false when the user may already have given the corona a thickness of their own.
     */
    private String prepareBilayerShell(boolean mayZeroCorona) {
        StringBuilder what = new StringBuilder();
        boolean hasPeg = composition != null && composition.pegMolPercent() > 0.01;
        if (!hasPeg && mayZeroCorona) {
            params[LnpModel.T4] = 0;
            fixed[LnpModel.T4] = true;
            params[LnpModel.rhoIndex(4)] = 0;
            fixed[LnpModel.rhoIndex(4)] = true;
            what.append(", corona set to zero (no PEG-lipid declared)");
        }
        if (fixed[LnpModel.T1] || fixed[LnpModel.T3]) {
            fixed[LnpModel.T1] = false;
            fixed[LnpModel.T3] = false;
            what.append(what.length() > 0 ? " and the headgroup thicknesses freed" : ", headgroup thicknesses freed");
        }
        tableModel.rebuild();
        if (modelEditor != null) modelEditor.repaint();
        updateCartoon();
        return what.toString();
    }

    private void fit(boolean calculateOnly) {
        if (worker != null) return;
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        final double[][] d;
        try { d = currentData(); } catch (RuntimeException e) { say(e.getMessage()); return; }
        final LnpModel m = model;
        final double[] start = params.clone();
        final boolean[] fx = fixed.clone();
        final boolean scan = autoStart.isSelected() && !calculateOnly;
        final LnpFitter fitter = new LnpFitter(m, d[0], d[1], d[2]).physicalConstraints(physical.isSelected());
        final String name = String.valueOf(datasetBox.getSelectedItem());
        cancel = false;
        // Fitting a different model from the one the assessment suggested is allowed and sometimes right, but it
        // must not be silent: the model selected at launch is an mRNA-LNP, and fitting that over a suggestion
        // nobody accepted produces a plausible-looking curve and an invented internal spacing.
        Preset points = suggestionFor(assessment).preset;   // from the current assessment, not a remembered one
        pendingSuggestion = !calculateOnly && points != null && points != presetBox.getSelectedItem()
                ? points : null;
        setBusy(true, calculateOnly ? "Calculating…" : "Fitting…");
        worker = new SwingWorker<LnpFitter.Result, Void>() {
            @Override protected LnpFitter.Result doInBackground() {
                return calculateOnly ? fitter.evaluate(start, fx) : fitter.fit(start, fx, scan, () -> cancel);
            }

            @Override protected void done() {
                worker = null;
                setBusy(false, null);
                try {
                    LnpFitter.Result r = get();
                    result = r;
                    population = null;
                    resultName = name;
                    params = r.params.clone();
                    if (!calculateOnly) {
                        errors = r.errors.clone();
                        // keep the user's choice; a density the fitter had to fix stays fixed
                        for (int k = 0; k < LnpModel.NPAR; k++) if (m.uses(k) && r.fixed[k]) fixed[k] = true;
                    }
                    tableModel.fireTableDataChanged();
                    if (modelEditor != null) modelEditor.repaint();
                    showResult(r, calculateOnly);
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable c = e.getCause();
                    say(c instanceof LnpFitter.CancelledException ? "Stopped" : "Fit failed: " + c.getMessage());
                } catch (InterruptedException e) {
                    say("Interrupted");
                }
            }
        };
        worker.execute();
    }

    private void setBusy(boolean busy, String msg) {
        progress.setIndeterminate(busy);
        if (mainProgress != null) mainProgress.setIndeterminate(busy);
        fitButton.setEnabled(!busy);
        calcButton.setEnabled(!busy);
        assessButton.setEnabled(!busy);
        resetButton.setEnabled(!busy);
        presetBox.setEnabled(!busy);
        stopButton.setEnabled(busy);
        if (msg != null) say(msg);
    }

    private void showResult(LnpFitter.Result r, boolean calculateOnly) {
        setChart(fitTab, LnpCharts.fitChart(r, (calculateOnly ? "Model – " : "Fit – ") + resultName));
        setChart(profileTab, LnpCharts.densityChart(r.model, r.params));
        setChart(sizeTab, LnpCharts.sizeChart(r.model, r.params));
        if (lastData == null || assessment == null) {   // checks need the model-free assessment too
            try {
                lastData = currentData();
                assessment = LnpAssessment.analyse(lastData[0], lastData[1], lastData[2], Double.NaN);
                assessedIndex = datasetBox.getSelectedIndex();
                updatePresetHelp();
            } catch (RuntimeException ignored) { }
        }
        updateChecks();
        updateCartoon();
        String txt = fitReport(r);
        report.setText(unitLine() + (assessment != null ? assessment.text + "\n\n" : "") + (checks != null ? LnpExpectations.text(checks) + "\n\n" : "") + txt);
        report.setCaretPosition(0);
        saveButton.setEnabled(true);
        // while only recalculating, leave the user where they are if they are already looking at the curve or
        // at the particle picture; otherwise bring the curve forward
        Component cur = tabs.getSelectedComponent();
        boolean onFit = cur instanceof Container && ((Container) cur).isAncestorOf(fitTab);
        boolean onCartoon = cur instanceof Container && ((Container) cur).isAncestorOf(cartoon);
        if (!calculateOnly || !(onFit || onCartoon)) showTabFor(fitTab);
        say(String.format("%s: reduced χ² = %.2f%s%s", calculateOnly ? "Model" : "Fit done", r.chi2Reduced,
                // A model that fits needs no second opinion. The suggestion is a heuristic read of the curve
                // before any model was tried; the reduced chi-squared is the answer to the same question with
                // the model actually tried, so where the fit is good the suggestion has been overtaken.
                pendingSuggestion == null || r.chi2Reduced <= GOOD_FIT ? ""
                        : " – this is " + modelName(r.model) + ", but the assessment suggests "
                                + pendingSuggestion.label + " (press 'Use suggested model')",
                r.warnings.isEmpty() ? "" : " – see warnings in the Report"));
    }

    // ------------------------------------------------------------------ report

    String fitReport(LnpFitter.Result r) {
        StringBuilder sb = new StringBuilder();
        double[] p = r.params, e = r.errors;
        sb.append("MODEL FIT – ").append(modelName(r.model)).append('\n');
        sb.append("Li et al., Membranes 15, 153 (2025): polydisperse spherical core + shells").append(r.model.peak ? " + Gaussian peak" : "").append('\n');
        sb.append(String.format("%d points, %d free parameters, χ² = %.1f, reduced χ² = %.2f%n%n", r.points, r.free, r.chi2, r.chi2Reduced));
        for (int k = 0; k < LnpModel.NPAR; k++) {
            if (!r.model.uses(k)) continue;
            String err = r.fixed[k] ? "(fixed)" : Double.isNaN(e[k]) ? "" : "± " + Fields.fmt(e[k]);
            sb.append(String.format("  %-24s %12s %-14s %s%n", name(r.model, k), Fields.fmt(p[k]), err, LnpModel.UNITS[k]));
        }
        // The size is only measured if the measurement reaches the Guinier region of the particle. Where q min
        // times the radius is well past that, the overall size is outside the window and the fit is pinning it
        // from the shape of the high-q decay alone: the number that comes out carries a small formal error and
        // almost no information, which is the most misleading combination a fit can produce.
        double qmin = r.q != null && r.q.length > 0 ? r.q[0] : Double.NaN;
        double rgRange = r.model.radiusOfGyration(p);
        if (Double.isNaN(rgRange) || rgRange <= 0) rgRange = p[LnpModel.R0] * Math.sqrt(3.0 / 5.0);
        if (assessment != null && assessment.formFactorLobe && !r.model.isVesicle() && r.chi2Reduced > 20)
            sb.append("\n  ! This is a filled-core model, but the data shows the oscillations of a bilayer shell.\n"
                    + "    A core with shells on it cannot make minima that deep, which is most of the misfit\n"
                    + "    above. Fit 'Vesicle / bilayer shell' instead before reading anything from these values.\n");
        // A model with a peak component fitted to data whose broad maxima are form-factor lobes is the worst
        // case of all, because it looks like it worked: the peak sits on the second lobe, the curve follows the
        // data through both minima, and the fit reports an internal spacing that nothing in the sample has.
        if (assessment != null && assessment.formFactorLobe && r.model.peak && !r.fixed[LnpModel.PEAK_Q]
                && p[LnpModel.PEAK_Q] > 0) {
            sb.append(String.format("%n  ! THE FITTED PEAK IS NOT A REAL SPACING. The assessment found the broad maxima%n"
                    + "    in this data to be lobes of the particle's own form factor, not reflections. The peak%n"
                    + "    component has settled on one of them at q* = %.4f Å⁻¹, which would read as d = %.1f Å,%n"
                    + "    and in doing so it reproduces a lobe the core-and-shells part cannot make. The curve%n"
                    + "    therefore follows the data while describing a structure that is not there. Do not quote%n"
                    + "    this spacing: fit 'Vesicle / bilayer shell', which makes those lobes from the bilayer.%n",
                    p[LnpModel.PEAK_Q], 2 * Math.PI / p[LnpModel.PEAK_Q]));
        }
        if (!Double.isNaN(qmin) && qmin * rgRange > 1.3) {
            sb.append(String.format("%n  ! THE OVERALL SIZE IS NOT MEASURED HERE. A radius of %.0f Å has its Guinier%n"
                    + "    region below q = %.4f Å⁻¹, and the data starts at %.4f, so q·Rg is already %.1f at the%n"
                    + "    first point. The fit still returns a radius with a small error, but that error describes%n"
                    + "    the fit, not the particle: treat the size as a lower limit and measure to lower q for it.%n"
                    + "    The shell thicknesses below come from the oscillations and are unaffected.%n",
                    p[LnpModel.R0], 1.3 / rgRange, qmin, qmin * rgRange));
        }
        double rout = r.model.outerRadius(p);
        double rvis = r.model.visibleOuterRadius(p);
        int invisible = r.model.firstInvisibleLayer(p);
        double pd = p[LnpModel.SIGMA] / p[LnpModel.R0];
        sb.append("\nDerived\n");
        sb.append(String.format("  Outer radius R₀ + Σt     %8.1f Å   (diameter %.1f nm)%n", rout, 2 * rout / 10));
        // a layer the user removed (thickness held at zero) is absent by choice, not contrast-matched by
        // accident, so it needs no warning: say it is not in the model and leave it there
        if (invisible >= 0 && p[LnpModel.tIndex(invisible)] == 0) {
            sb.append(String.format("  The %s is not in this model (thickness 0).%n",
                    r.model.layerNames()[invisible]));
        } else if (invisible >= 0 && rvis < rout - 0.5) {
            sb.append(String.format("  X-ray visible radius     %8.1f Å   (diameter %.1f nm)%n", rvis, 2 * rvis / 10));
            sb.append(String.format("    The %s has no contrast against the buffer, so the data cannot see it. Quote the visible%n"
                            + "    diameter unless you have independent evidence for that layer; its thickness here is an assumption.%n",
                    r.model.layerNames()[invisible]));
        }
        sb.append(String.format("  Polydispersity σ/R₀      %8.3f%n", pd));
        double rg = r.model.radiusOfGyration(p);
        if (!Double.isNaN(rg)) sb.append(String.format("  Rg of mean particle      %8.1f Å%n", rg));
        else sb.append("  Rg of mean particle          not defined: the positive and negative contrasts of this particle cancel\n"
                + "    (near contrast matching), so Rg² comes out negative. The size still follows from the oscillations.\n");
        if (r.model.peak) {
            double d = 2 * Math.PI / p[LnpModel.PEAK_Q], fwhm = 2.3548 * p[LnpModel.PEAK_W];
            sb.append(String.format("  Repeat distance 2π/q*    %8.1f Å   (%.2f nm)%n", d, d / 10));
            sb.append(String.format("  Peak FWHM                %8.4f Å⁻¹  → ordered domain ≈ 2π/FWHM = %.0f Å (~%.0f repeats)%n",
                    fwhm, 2 * Math.PI / fwhm, p[LnpModel.PEAK_Q] / fwhm));
        }

        sb.append("\nInterpretation\n");
        if (r.chi2Reduced > 3)
            sb.append("  • The model does not describe the data within the errors. Look at the residuals: a low-q upturn means\n")
              .append("    aggregates (raise q min); sharp peaks mean an ordered phase (see the Assessment); a misfit at\n")
              .append("    intermediate q can mean free micelles or a second population.\n");
        if (pd > 0.2)
            sb.append("  • σ/R₀ > 0.2: broad size distribution. Oscillations are washed out and the shell parameters are\n")
              .append("    poorly constrained; report the size and the peak, not the shell structure.\n");
        else
            sb.append("  • σ/R₀ ≤ 0.2: narrow size distribution. Shell parameters are meaningful when their errors are small.\n");
        for (int k : new int[]{LnpModel.T1, LnpModel.RHO_2, LnpModel.RHO_C}) {
            if (!r.model.uses(k) || r.fixed[k] || Double.isNaN(e[k])) continue;
            if (Math.abs(e[k]) > 0.3 * Math.abs(p[k]))
                sb.append(String.format("  • %s is poorly determined (± %.0f %%). Consider fixing it.%n", name(r.model, k), 100 * Math.abs(e[k] / p[k])));
        }
        if (r.model.shells == 3)
            sb.append("  • Reference (Li et al. 2025, Comirnaty-like LNPs, N/P 3–8): R₀ 177–229 Å, σ 25–52 Å, t₁ 18–26 Å,\n")
              .append("    mRNA peak at q* = 0.121–0.129 Å⁻¹ (d ≈ 4.9–5.2 nm). Higher N/P gave smaller, more uniform\n")
              .append("    particles and broader, weaker mRNA peaks.\n");
        for (String w : r.warnings) sb.append("  ! ").append(w).append('\n');
        return sb.toString();
    }

    private static String modelName(LnpModel m) {
        for (Preset p : Preset.values()) if (p.shells == m.shells && p.peak == m.peak) return p.label;
        return "core + " + m.shells + " shells" + (m.peak ? " + peak" : "");
    }

    /** parameter label adapted to the model variant */
    static String name(LnpModel m, int k) {
        if (m.isVesicle()) {
            switch (k) {
                case LnpModel.R0: return "Water-core radius R₀";
                case LnpModel.RHO_C: return "Water core ρc (= buffer)";
                case LnpModel.T1: return "Inner headgroups t₁";
                case LnpModel.T2: return "Bilayer chains t₂";
                case LnpModel.T3: return "Outer headgroups t₃";
                case LnpModel.T4: return "PEG corona t₄";
                case LnpModel.RHO_1: return "Inner headgroups ρ₁";
                case LnpModel.RHO_2: return "Bilayer chains ρ₂";
                case LnpModel.RHO_3: return "Outer headgroups ρ₃";
                case LnpModel.RHO_4: return "PEG corona ρ₄";
                default: break;
            }
        }
        if (m.shells == 1 && k == LnpModel.T1) return "Shell thickness t₁";
        if (m.shells == 1 && k == LnpModel.RHO_1) return "Shell density ρ₁";
        if (m.shells == 0 && k == LnpModel.R0) return "Radius R₀";
        if (m.shells == 0 && k == LnpModel.RHO_C) return "Density ρ (reference)";
        return LnpModel.NAMES[k];
    }

    // ------------------------------------------------------------------ save

    private void save() {
        if (result == null) return;
        JFileChooser fc = new JFileChooser(workingDirectory.get());
        String base = resultName.replaceFirst("^\\d+: ", "").replaceAll("\\.[A-Za-z0-9]+$", "").replaceAll("[^A-Za-z0-9._-]", "_");
        fc.setSelectedFile(new File(base + "_lnp_fit.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try {
            write(result, f);
            say("Saved " + f.getName());
        } catch (IOException ex) {
            say("Could not save: " + ex.getMessage());
        }
    }

    void write(LnpFitter.Result r, File f) throws IOException {
        try (PrintWriter w = new PrintWriter(f, StandardCharsets.UTF_8.name())) {
            for (String line : (report.getText()).split("\n")) w.println("# " + line);
            w.println("#");
            w.println("# q(1/A)  I  sigma  model  particle  peak  background  residual/sigma");
            double[][] c = r.model.components(r.params, r.q);
            for (int j = 0; j < r.q.length; j++)
                w.printf("%.6e %.6e %.6e %.6e %.6e %.6e %.6e %.4f%n", r.q[j], r.I[j], r.sigma[j], r.fit[j], c[0][j],
                        c[1] == null ? 0.0 : c[1][j], c[2][j], (r.I[j] - r.fit[j]) / r.sigma[j]);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void showEmpty() {
        setChart(assessTab, FormFactorCharts.placeholder("Select a dataset and press  1. Assess sample"));
        setChart(fitTab, FormFactorCharts.placeholder("Press  2. Fit model  to fit the core–shell model"));
        setChart(profileTab, LnpCharts.densityChart(model, params));
        setChart(sizeTab, LnpCharts.sizeChart(model, params));
        report.setText("Lipid nanoparticle analysis\n\n"
                + "1. Assess sample: model-free size, polydispersity and internal-structure check. Bragg peaks are\n"
                + "   indexed for lamellar, hexagonal and cubic phases (Caselli et al., Adv. Colloid Interface Sci. 2024).\n"
                + "2. Fit model: polydisperse core + up to three shells (inner lipid layer, headgroups, PEG corona),\n"
                + "   with a Gaussian peak for mRNA-loaded particles (Li et al., Membranes 2025).\n");
    }

    /**
     * Warns when the q range says the data is probably not in Å⁻¹. Nothing is converted here: Scatter converts at
     * load, so the fix belongs there, and a second conversion inside the panel would be invisible in the saved
     * result and impossible to reconcile later.
     */
    private void checkUnits(double[] q) {
        LnpUnits u = LnpUnits.inspect(q);
        if (q.length > 0 && q[0] > 0.6) {
            // a curve that starts this high is a wide-angle measurement, where q past 1.6 Å⁻¹ is the point
            unitWarning.setVisible(false);
            unitNote = u.note().replace("   ** check the units, see the Assessment tab **", "   (wide angle)");
            return;
        }
        unitWarning.setVisible(u.worthWarning());
        if (u.worthWarning())
            Fields.setWrappingText(unitWarning, this, "<b>Check the units.</b> " + u.message());
        unitNote = u.note();
    }

    private String unitNote = "";

    /** heads a saved report with the q range it was read at, so the units assumed are on the record */
    private String unitLine() {
        return unitNote.isEmpty() ? "" : "Data: " + unitNote + "\n\n";
    }

    /**
     * The wide-angle companion measurement. SAXS says how the bilayers are arranged; it says little about the
     * chains themselves, and a thick bilayer can mean ordered chains or simply a longer lipid. The chain-packing
     * region settles that, so it is offered as an optional second dataset rather than a separate analysis.
     */
    private JComponent buildWaxsTab() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(8, 10, 4, 10));
        top.add(Fields.wrappingLabel(p, "If a wide-angle dataset of the same sample is loaded, "
                + "choose it here. The chain-packing region near q = 1.5 Å⁻¹ shows whether the hydrocarbon chains "
                + "are <b>melted</b> (a broad halo near 4.5 Å, a fluid bilayer), <b>ordered</b> (a narrow line near "
                + "4.1 Å, a gel), or <b>liquid-ordered</b> (a broad halo near 4.6 Å, what a sterol produces). That "
                + "distinction is invisible to SAXS and changes how a fitted bilayer thickness should be read."));
        top.add(Box.createVerticalStrut(6));
        JButton run = new JButton("Read the chain packing");
        run.addActionListener(e -> runWaxs());
        waxsBox.setToolTipText("A dataset reaching q ≈ 1.5 Å⁻¹; load it in the Analysis panel first");
        top.add(Fields.flow(new JLabel("WAXS dataset"), waxsBox, run));
        for (Component c : top.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        waxsText.setEditable(false);
        waxsText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        waxsText.setText("No wide-angle dataset read yet. This step is optional.");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, waxsChart, new JScrollPane(waxsText));
        split.setResizeWeight(0.58);
        p.add(top, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    /** preselects the dataset that actually reaches the chain-packing region, so the user need not hunt for it */
    private void suggestWaxsDataset(int n) {
        for (int i = 0; i < n; i++) {
            try {
                double[][] d = provider.data(i);
                if (d[0].length > 0 && d[0][d[0].length - 1] > 1.2) { waxsBox.setSelectedIndex(i); return; }
            } catch (RuntimeException ignored) { }
        }
    }

    private void runWaxs() {
        int idx = waxsBox.getSelectedIndex();
        if (idx < 0) { say("Load a wide-angle dataset first, then choose it here"); return; }
        double[][] d;
        try {
            d = provider.data(idx);
        } catch (RuntimeException ex) { say("Could not read that dataset: " + ex.getMessage()); return; }
        waxsResult = LnpWaxs.analyse(d[0], d[1], d[2]);
        boolean sterol = composition != null && composition.sterolMolPercent() > 30;
        waxsText.setText(LnpWaxs.describe(waxsResult, sterol));
        waxsText.setCaretPosition(0);
        waxsChart.removeAll();
        waxsChart.add(LnpCharts.panelFor(LnpCharts.waxsChart(waxsResult, d[0], d[1], d[2],
                String.valueOf(waxsBox.getSelectedItem()))), BorderLayout.CENTER);
        waxsChart.revalidate();
        waxsChart.repaint();
        LnpWaxs.Peak m = waxsResult.main();
        say(m == null ? "No chain-packing peak found in that dataset"
                : String.format("Chain packing: d = %.2f Å, %s", m.d, waxsResult.packing));
    }

    /**
     * Brings the tab holding this component to the front, whatever it is nested inside. Selecting a component
     * directly throws when it is not itself one of the tabs, and components here do get wrapped — the assessment
     * chart sits under a toolbar, the fit curve inside a sub-tabbed pane — so the caller should not have to know
     * the current arrangement. Any nested tabbed pane on the way is advanced too, so the component really ends
     * up visible rather than merely on a selected branch.
     */
    void showTabFor(Component target) {
        if (target == null) return;
        for (Component outer = target; outer != null; outer = outer.getParent()) {
            Container parent = outer.getParent();
            if (parent == tabs) {
                tabs.setSelectedComponent(outer);
                // walk back down, selecting the inner tab that holds the target
                for (Component inner = target; inner != null && inner != outer; inner = inner.getParent()) {
                    Container ip = inner.getParent();
                    if (ip instanceof JTabbedPane && ip != tabs) ((JTabbedPane) ip).setSelectedComponent(inner);
                }
                return;
            }
        }
    }

    LnpCharts.Weight weight() {
        LnpCharts.Weight w = (LnpCharts.Weight) plotWeight.getSelectedItem();
        return w == null ? LnpCharts.Weight.NONE : w;
    }

    /** redraws whatever is already on screen under the chosen weighting, without recomputing anything */
    private void redrawWeighted() {
        if (assessment != null && lastData != null)
            setChart(assessTab, LnpCharts.assessmentChart(lastData[0], lastData[1], lastData[2], assessment,
                    "Assessment – " + datasetBox.getSelectedItem(), weight()));
        if (latticeResult != null) {
            phaseChart.removeAll();
            phaseChart.add(LnpCharts.panelFor(LnpCharts.latticeChart(latticeResult,
                    String.valueOf(datasetBox.getSelectedItem()), weight())), BorderLayout.CENTER);
            phaseChart.revalidate();
            phaseChart.repaint();
        }
    }

    private static void setChart(JPanel tab, JFreeChart chart) {
        tab.removeAll();
        tab.add(LnpCharts.panelFor(chart), BorderLayout.CENTER);
        tab.revalidate();
        tab.repaint();
    }

    private static LnpAssessment.Peak strongest(List<LnpAssessment.Peak> peaks) {
        LnpAssessment.Peak b = peaks.get(0);
        for (LnpAssessment.Peak p : peaks) if (p.height > b.height) b = p;
        return b;
    }

    private static double[] nanArray() {
        double[] a = new double[LnpModel.NPAR];
        java.util.Arrays.fill(a, Double.NaN);
        return a;
    }

    private void say(String s) {
        status.setText(s);
        if (mainStatus != null) mainStatus.setText(s == null || s.trim().isEmpty() ? "" : "LNP: " + s);
    }

    // accessors for tests
    JTabbedPane tabs() { return tabs; }
    JComboBox<Preset> presetBox() { return presetBox; }
    LnpFitter.Result getResult() { return result; }
    LnpAssessment.Report getAssessment() { return assessment; }
    void assessNow() { assess(); }
    void applySuggestionNow() { applySuggestion(); }
    void fitNow(boolean calculateOnly) { fit(calculateOnly); }
    double[] params() { return params; }
    String statusText() { return status.getText(); }
    LnpCartoon cartoon() { return cartoon; }
    JComboBox<LnpExpectations.Cargo> cargoBox() { return cargoBox; }
    JComboBox<String> referenceBox() { return referenceBox; }
    JComboBox<Object> interiorBox() { return interiorBox; }
    void setExpectedDiameter(String lo, String hi) { dMin.setText(lo); dMax.setText(hi); }
    JComboBox<String> compositionBox() { return compositionBox; }
    void runPopulationsNow(boolean suggest) { runPopulations(suggest); }
    void runPhaseNow() { runPhaseDecomposition(); }
    void runLatticeNow() { runLatticeFit(); }
    void runSeriesNow() { runSeries(); }
    boolean seriesRunning() { return seriesWorker != null; }
    String seriesReport() { return series == null ? "(no series)" : LnpSeries.describe(series); }
    void setupSeriesForTest(String name, String unit, LnpSeries.Mode mode) {
        seriesName.setText(name);
        seriesUnit.setText(unit);
        seriesMode.setSelectedItem(mode);
        fillConditionsFromNames();
    }
    JComboBox<Object> latticeBox() { return latticeBox; }
    JComboBox<String> peakMode() { return peakMode; }
    LnpEmPanel emPanel() { return emPanel; }
    LnpPopulations.Result getPopulation() { return population; }
    java.util.EnumMap<LnpPopulations.Kind, JCheckBox> proposalBoxes() { return proposal; }

    // ------------------------------------------------------------------ parameter table

    private final class ParamTableModel extends AbstractTableModel {
        private final List<Integer> rows = new ArrayList<>();
        private final String[] cols = {"Fit", "Parameter", "Value", "± error", "Unit"};

        void rebuild() {
            rows.clear();
            for (int k = 0; k < LnpModel.NPAR; k++) if (model.uses(k)) rows.add(k);
            fireTableDataChanged();
        }

        int param(int row) { return rows.get(row); }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
        @Override public boolean isCellEditable(int r, int c) { return worker == null && (c == 0 || c == 2); }

        @Override
        public Object getValueAt(int r, int c) {
            int k = rows.get(r);
            switch (c) {
                case 0: return !fixed[k];
                case 1: return name(model, k);
                case 2: return Fields.fmt(params[k]);
                case 3: return fixed[k] || Double.isNaN(errors[k]) ? "" : Fields.fmt(errors[k]);
                default: return LnpModel.UNITS[k];
            }
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            int k = rows.get(r);
            if (c == 0) {
                fixed[k] = !(Boolean) v;
                if (LnpModel.isDensity(k) && !anyDensityFixed()) {
                    fixed[k] = true;
                    say("One density must stay fixed: only density ratios follow from the data");
                }
            } else if (c == 2) {
                try {
                    double x = Double.parseDouble(String.valueOf(v).trim());
                    // A thickness of zero removes the layer, which is a legitimate thing to want: a sample with
                    // no PEG-lipid has no corona, and the vesicle architecture has no variant without one. It is
                    // only degenerate while the layer is being fitted — a layer with no thickness has no
                    // determinable density — so zero is accepted and the layer is held fixed with it.
                    if (LnpModel.isThickness(k) && x == 0) {
                        params[k] = 0;
                        fixed[k] = true;
                        int layer = layerOf(k);
                        if (layer > 0) {
                            params[LnpModel.rhoIndex(layer)] = 0;
                            fixed[LnpModel.rhoIndex(layer)] = true;
                        }
                        errors[k] = Double.NaN;
                        tableModel.rebuild();
                        say(name(model, k) + " set to zero: the layer is removed and held fixed");
                        updateCartoon();
                        if (modelEditor != null) modelEditor.repaint();
                        return;
                    }
                    if (LnpModel.POSITIVE[k] && !(x > 0)) { say(name(model, k) + " must be > 0"); return; }
                    params[k] = x;
                    errors[k] = Double.NaN;
                } catch (NumberFormatException ex) {
                    say("Not a number: " + v);
                    return;
                }
            }
            fireTableRowsUpdated(r, r);
            if (modelEditor != null) modelEditor.repaint();
            if (c == 2 && liveCurve.isSelected()) liveTimer.restart();
        }
    }
}
