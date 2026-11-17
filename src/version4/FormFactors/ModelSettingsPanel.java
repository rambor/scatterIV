package version4.FormFactors;

import version4.FormFactors.LangevinSettings;
import version4.FormFactors.ShapeFamily;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Model selector plus the parameters each model needs. {@link #build} validates the input and turns it into a
 * {@link FitSpec} (shape family, per-parameter bounds, model-specific settings).
 */
public class ModelSettingsPanel extends JPanel {

    public enum Model {
        SPHERE("Sphere"),
        SPHEROID("Spheroid (ellipsoid of revolution)"),
        TRIAXIAL("Triaxial ellipsoid"),
        CORESHELL("Core-shell spheroid"),
        HELIX("Helical filament"),
        GAUSSIAN_CHAIN("Polymer: Gaussian chain (Debye)"),
        EV_CHAIN("Polymer: excluded-volume chain (ν)"),
        WORMLIKE("Polymer: wormlike chain (flexible cylinder)"),
        MICELLE("Block-copolymer micelle");

        public boolean isPolymer() { return ordinal() >= GAUSSIAN_CHAIN.ordinal(); }

        final String label;
        Model(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    /** everything the worker needs to run one fit (or one profile scan) */
    public static final class FitSpec {
        public ShapeFamily family;
        public double[] min, max;
        public LangevinSettings settings;
        public boolean profileScan;          // core-shell empty/filled: estimate the fraction by a profile scan
        public double profileStep = 0.1;
        public int profileRepeats = 2;
        public String description;
        /** scan of a global setting (Flory exponent, Kuhn length) with a distribution fit at each value; null = single fit */
        public ParamScan paramScan;
    }

    public static final class ParamScan {
        public String parameter, unit;
        public java.util.function.BiConsumer<LangevinSettings, Double> setter;
        public double[] grid;
        public boolean logScale;
        public int repeats = 2;
    }

    private final JComboBox<Model> modelBox = new JComboBox<>(Model.values());
    private final CardLayout cards = new CardLayout();
    /** card panel that is only as tall as the visible card (CardLayout would use the tallest) */
    private final JPanel cardPanel = new JPanel(cards) {
        @Override public Dimension getPreferredSize() {
            for (Component c : getComponents())
                if (c.isVisible()) {
                    Dimension d = c.getPreferredSize();
                    Insets in = getInsets();
                    return new Dimension(super.getPreferredSize().width, d.height + in.top + in.bottom);
                }
            return super.getPreferredSize();
        }
    };

    // sphere
    private final JTextField rMin = Fields.num(""), rMax = Fields.num("");
    // spheroid
    private final JTextField sAMin = Fields.num(""), sAMax = Fields.num(""), sCMin = Fields.num(""), sCMax = Fields.num("");
    private final ShapeBox spheroidShape = new ShapeBox("a, c");
    // triaxial
    private final JTextField tMin = Fields.num(""), tMax = Fields.num("");
    // core-shell
    private final JTextField cAMin = Fields.num(""), cAMax = Fields.num(""), cCMin = Fields.num(""), cCMax = Fields.num("");
    private final ShapeBox coreShape = new ShapeBox("core a, c");
    private final JRadioButton shellFixed = new JRadioButton("Thickness", true), shellRange = new JRadioButton("Range");
    private final JTextField shellT = Fields.num("14"), shellTol = Fields.num("10"), shellMin = Fields.num("5"), shellMax = Fields.num("40");
    private final JTextField rhoSolvent = Fields.num("9.42"), rhoShell = Fields.num("12.0"), rhoCore = Fields.num("10.5"), rhoFilled = Fields.num("14.5");
    private final JRadioButton singleCore = new JRadioButton("Single core density", true),
            mixture = new JRadioButton("Empty + filled mixture");
    private final JRadioButton estimateFraction = new JRadioButton("Estimate (profile scan)", true),
            fixFraction = new JRadioButton("Fix at");
    private final JTextField fixedFraction = Fields.num("0.5"), profileStep = Fields.num("0.1");

    // polymer chains
    private final JTextField gRgMin = Fields.num(""), gRgMax = Fields.num("");
    private final JTextField eRgMin = Fields.num(""), eRgMax = Fields.num(""), eNu = Fields.num("0.588");
    private final JRadioButton eNuFixed = new JRadioButton("Fixed", true), eNuScan = new JRadioButton("Estimate by scan");
    private final JTextField eNuLo = Fields.num("0.40"), eNuHi = Fields.num("0.70"), eNuStep = Fields.num("0.05");
    private final JRadioButton wKFixed = new JRadioButton("Fixed", true), wKScan = new JRadioButton("Estimate by scan");
    private final JTextField wKLo = Fields.num("20"), wKHi = Fields.num("400");
    private final JTextField wLMin = Fields.num(""), wLMax = Fields.num(""), wKuhn = Fields.num("100"), wRadius = Fields.num("0");
    // helix
    private final JTextField hLMin = Fields.num(""), hLMax = Fields.num(""), hRMin = Fields.num("20"), hRMax = Fields.num("300");
    private final JRadioButton hFinite = new JRadioButton("Finite: fit the length distribution", true),
            hInfinite = new JRadioButton("Infinitely long: fit the radius distribution");
    private final JRadioButton hContinuous = new JRadioButton("Continuous strands (van der Maarel 2023)", true),
            hSubunits = new JRadioButton("Spherical subunits (exact Debye sum)"),
            hEllipsoids = new JRadioButton("Ellipsoidal subunits on a helical lattice (infinitely long only)");
    private final JTextField hPerTurn = Fields.num("12"), hEllA = Fields.num("25"), hEllC = Fields.num("40"),
            hUnitMw = Fields.num(""), hUnitVol = Fields.num(""), hVbar = Fields.num("0.73"), hUnitTol = Fields.num("20");
    private final JTextField hRadius = Fields.num("10"), hPitch = Fields.num("35"), hStrandRadius = Fields.num("0"),
            hStrandAngle = Fields.num("180"), hRise = Fields.num("27.5"), hTwist = Fields.num("-166.7"), hSubunitRadius = Fields.num("20");
    private final JSpinner hStrands = new JSpinner(new SpinnerNumberModel(1, 1, 12, 1));
    private final JComboBox<String> hScan = new JComboBox<>(new String[]{"None", "Helix radius", "Pitch", "Subunits per turn"});
    private final JButton hEstimate = new JButton("Estimate from data");
    private final JLabel hEstimateStatus = new JLabel(" ");
    private Runnable helixEnable = () -> { };
    /** current dataset limited to the q range ([q, I, sigma]), and a way to set q max; provided by the panel */
    private java.util.function.Supplier<double[][]> helixData;
    private java.util.function.DoubleConsumer helixQmax;
    private final JTextField hScanLo = Fields.num("5"), hScanHi = Fields.num("30");
    // micelle
    private final JTextField mRMin = Fields.num(""), mRMax = Fields.num("");
    private final JRadioButton brushFixed = new JRadioButton("Rg", true), brushRange = new JRadioButton("Range");
    private final JTextField brushRg = Fields.num("15"), brushTol = Fields.num("20"), brushMin = Fields.num("5"), brushMax = Fields.num("40");
    private final JTextField mRhoSolvent = Fields.num("9.42"), mRhoCore = Fields.num("9.0"), mRhoCorona = Fields.num("10.2");
    private final JTextField mVCore = Fields.num("20000"), mVCorona = Fields.num("30000"), mPacking = Fields.num("1.0"), mPen = Fields.num("1.0");
    private final JLabel nAggInfo = new JLabel(" ");
    private double lastQmin = Double.NaN, lastQmax = Double.NaN;

    public ModelSettingsPanel() {
        super(new BorderLayout(0, 6));
        setBorder(new TitledBorder("Model"));
        add(modelBox, BorderLayout.NORTH);
        cardPanel.add(spherePanel(), Model.SPHERE.name());
        cardPanel.add(spheroidPanel(), Model.SPHEROID.name());
        cardPanel.add(triaxialPanel(), Model.TRIAXIAL.name());
        cardPanel.add(coreShellPanel(), Model.CORESHELL.name());
        cardPanel.add(helixPanel(), Model.HELIX.name());
        cardPanel.add(gaussianPanel(), Model.GAUSSIAN_CHAIN.name());
        cardPanel.add(evPanel(), Model.EV_CHAIN.name());
        cardPanel.add(wormlikePanel(), Model.WORMLIKE.name());
        cardPanel.add(micellePanel(), Model.MICELLE.name());
        add(cardPanel, BorderLayout.CENTER);
        modelBox.addActionListener(e -> {
            cards.show(cardPanel, ((Model) modelBox.getSelectedItem()).name());
            revalidate();
            repaint();
        });
    }

    public Model selectedModel() { return (Model) modelBox.getSelectedItem(); }

    public void addModelChangeListener(Runnable r) { modelBox.addActionListener(e -> r.run()); }

    /** suggested size bounds from the q-range: smallest resolvable ~ pi/qmax, largest ~ pi/qmin (capped) */
    public void suggestBounds(double qmin, double qmax) {
        double lo = Math.max(1.0, Math.floor(Math.PI / qmax));
        double hi = Math.min(2000.0, Math.ceil(Math.PI / Math.max(qmin, 1e-6)));
        if (hi <= lo * 1.5) hi = lo * 10;
        for (JTextField f : new JTextField[]{rMin, sAMin, sCMin, tMin, cAMin, cCMin}) f.setText(Fields.fmt(lo));
        for (JTextField f : new JTextField[]{rMax, sAMax, sCMax, tMax, cAMax, cCMax}) f.setText(Fields.fmt(hi));
        lastQmin = qmin;
        lastQmax = qmax;
        // chains: Rg from ~1/qmax up to what q min still resolves (q min Rg ~ 1.5)
        double rgLo = Math.max(2.0, Math.floor(1.0 / qmax)), rgHi = Math.max(3 * rgLo, Math.ceil(1.5 / Math.max(qmin, 1e-6)));
        for (JTextField f : new JTextField[]{gRgMin, eRgMin}) f.setText(Fields.fmt(rgLo));
        for (JTextField f : new JTextField[]{gRgMax, eRgMax}) f.setText(Fields.fmt(Math.min(2000, rgHi)));
        suggestWormlike();
        // helices: L from a few times the resolution up to where a rod's Rg (L/sqrt 12) reaches 1.5 / q min
        hLMin.setText(Fields.fmt(Math.max(10, Math.round(2 * Math.PI / qmax))));
        hLMax.setText(Fields.fmt(Math.min(20000, Math.round(1.5 * Math.sqrt(12) / Math.max(qmin, 1e-6)))));
        mRMin.setText(Fields.fmt(lo));
        mRMax.setText(Fields.fmt(Math.min(hi, 1000)));
        updateNAgg();
    }

    /** contour length bounds: L_min ~ b/2, L_max where the model Rg reaches 1.5 / q min */
    private void suggestWormlike() {
        if (Double.isNaN(lastQmin)) return;
        double b;
        try { b = Double.parseDouble(wKuhn.getText().trim()); } catch (NumberFormatException e) { return; }
        if (!(b > 0)) return;
        double rgHi = 1.5 / lastQmin, L = Math.max(4 * b, 10);
        while (L < 1e6 && version4.FormFactors.PolymerMath.wormlikeRg(L, b) < rgHi) L *= 1.05;
        double lo = Math.max(5, Math.min(b / 2, L / 20));
        wLMin.setText(Fields.fmt(Math.round(lo)));
        wLMax.setText(Fields.fmt(Math.round(L)));
    }

    private void updateNAgg() {
        try {
            double v = Double.parseDouble(mVCore.getText().trim()), pk = Double.parseDouble(mPacking.getText().trim());
            double r0 = Double.parseDouble(mRMin.getText().trim()), r1 = Double.parseDouble(mRMax.getText().trim());
            if (!(v > 0 && pk > 0 && r0 > 0 && r1 > r0)) { nAggInfo.setText(" "); return; }
            double n0 = pk * 4 * Math.PI / 3 * r0 * r0 * r0 / v, n1 = pk * 4 * Math.PI / 3 * r1 * r1 * r1 / v;
            nAggInfo.setText(String.format("<html>N<sub>agg</sub> over the R_core range: %s – %s%s</html>",
                    n0 < 10 ? String.format("%.1f", n0) : Fields.fmt(Math.round(n0)), Fields.fmt(Math.round(n1)),
                    n0 < 2 ? " (below 2 at R min: raise R min)" : ""));
        } catch (NumberFormatException e) {
            nAggInfo.setText(" ");
        }
    }

    // ------------------------------------------------------------------ panels

    private JPanel spherePanel() {
        Fields.Form f = new Fields.Form();
        f.row("R min / max (Å)", Fields.pair(rMin, rMax), "Radius bounds. Lower bound ~ pi/qmax: smaller spheres are unresolved and act as a flat background.");
        return f.panel();
    }

    private JPanel spheroidPanel() {
        Fields.Form f = new Fields.Form();
        f.row("a min / max (Å)", Fields.pair(sAMin, sAMax), "Equatorial semi-axis bounds");
        f.row("c min / max (Å)", Fields.pair(sCMin, sCMax), "Polar semi-axis bounds");
        f.full(spheroidShape);
        return f.panel();
    }

    private JPanel triaxialPanel() {
        Fields.Form f = new Fields.Form();
        f.row("semi-axes min / max (Å)", Fields.pair(tMin, tMax), "Bounds for all three semi-axes (kept sorted a <= b <= c)");
        f.note("Expensive kernel: 300-500 particles are usually enough.");
        return f.panel();
    }

    private JPanel gaussianPanel() {
        Fields.Form f = new Fields.Form();
        f.row("Rg min / max (Å)", Fields.pair(gRgMin, gRgMax), "Radius-of-gyration bounds of the chains");
        f.note("Ideal (theta-solvent) chains. The fitted distribution is the chain-size distribution; volume weighting = mass weighting.");
        return f.panel();
    }

    private JPanel evPanel() {
        Fields.Form f = new Fields.Form();
        f.row("Rg min / max (Å)", Fields.pair(eRgMin, eRgMax), "Radius-of-gyration bounds of the chains");
        ButtonGroup ng = new ButtonGroup();
        ng.add(eNuFixed);
        ng.add(eNuScan);
        f.row("Flory exponent ν", Fields.flow(eNuFixed, eNu), "Same for every chain: 0.5 theta, 0.588 good solvent, ~0.33 collapsed. A starting value comes from the Polymer tab.");
        f.row("", Fields.flow(eNuScan, eNuLo, new JLabel("-"), eNuHi, new JLabel("step"), eNuStep),
                "Fit the distribution at each ν and take the χ² minimum (with 68 / 95 % interval). Needed for polydisperse samples: "
                        + "polydispersity biases the single-chain ν upwards.");
        Runnable enN = () -> {
            eNu.setEnabled(eNuFixed.isSelected());
            for (JTextField t : new JTextField[]{eNuLo, eNuHi, eNuStep}) t.setEnabled(eNuScan.isSelected());
        };
        eNuFixed.addActionListener(e -> enN.run());
        eNuScan.addActionListener(e -> enN.run());
        enN.run();
        f.note("Excluded-volume chains (SasView polymer_excl_volume, Porod exponent 1/ν). Volume weighting = mass weighting.");
        return f.panel();
    }

    private JPanel wormlikePanel() {
        Fields.Form f = new Fields.Form();
        f.row("L min / max (Å)", Fields.pair(wLMin, wLMax), "Contour-length bounds. The suggested maximum is where the chain Rg reaches 1.5 / q min: longer chains are not resolved.");
        ButtonGroup kg = new ButtonGroup();
        kg.add(wKFixed);
        kg.add(wKScan);
        f.row("Kuhn length b (Å)", Fields.flow(wKFixed, wKuhn, new JLabel("= 2 × persistence")), "Same for every chain. A starting value comes from the Polymer tab.");
        f.row("", Fields.flow(wKScan, wKLo, new JLabel("-"), wKHi, new JLabel("Å")),
                "Fit the contour-length distribution at 7 log-spaced b values (plus refinement) and take the χ² minimum");
        Runnable enK = () -> {
            wKuhn.setEnabled(wKFixed.isSelected());
            wKLo.setEnabled(wKScan.isSelected());
            wKHi.setEnabled(wKScan.isSelected());
        };
        wKFixed.addActionListener(e -> enK.run());
        wKScan.addActionListener(e -> enK.run());
        enK.run();
        f.row("Cross-section R (Å)", Fields.flow(wRadius), "Chain cross-section radius (flexible cylinder); 0 = thin chain");
        f.note("Wormlike chain with excluded volume (SasView flexible_cylinder / WormLikeChainEXV, Pedersen–Schurtenberger + Chen 2006). "
                + "The fitted distribution is the contour-length distribution; volume weighting = mass weighting.");
        wKuhn.addActionListener(e -> suggestWormlike());
        wKuhn.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { suggestWormlike(); }
        });
        return f.panel();
    }

    private JPanel micellePanel() {
        Fields.Form f = new Fields.Form();
        f.row("R_core min / max (Å)", Fields.pair(mRMin, mRMax), "Core radius bounds; the aggregation number follows the core size");
        ButtonGroup bg = new ButtonGroup();
        bg.add(brushFixed);
        bg.add(brushRange);
        f.row("Brush (corona chain)", Fields.flow(brushFixed, brushRg, new JLabel("Å  ±"), brushTol, new JLabel("%")),
                "Restrict the corona chain Rg to Rg ± tolerance (e.g. 15 Å ± 20 %); brush thickness ~ 2 Rg");
        f.row("", Fields.flow(brushRange, brushMin, new JLabel("-"), brushMax, new JLabel("Å")), "Or let the corona Rg vary inside a range");
        f.row("SLD solvent / core / corona", Fields.flow(mRhoSolvent, new JLabel("/"), mRhoCore, new JLabel("/"), mRhoCorona),
                "Scattering length densities of solvent, core block and corona block (consistent units, e.g. 1e-6/Å²)");
        f.row("Block volume core / corona (Å³)", Fields.pair(mVCore, mVCorona),
                "Molecular volume of one core block and one corona block: V(Å³) = 1.66 × M(g/mol) / ρ(g/cm³)");
        f.row("Core packing / penetration d", Fields.pair(mPacking, mPen),
                "Packing: polymer volume fraction in the core (N_agg = packing·4/3πR³ / V_core). d: chains centred at R + d·Rg (d ≈ 1, no penetration)");
        f.full(nAggInfo);
        f.note("Pedersen–Gerstenberg micelle (SasView polymer_micelle). The data fix the mean corona Rg well but not its spread: "
                + "keep the brush window narrow and read its width as an assumption.");
        nAggInfo.setForeground(new Color(0x6e7781));
        javax.swing.event.DocumentListener upd = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateNAgg(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateNAgg(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateNAgg(); }
        };
        for (JTextField t : new JTextField[]{mVCore, mPacking, mRMin, mRMax}) t.getDocument().addDocumentListener(upd);
        Runnable en = () -> {
            brushRg.setEnabled(brushFixed.isSelected());
            brushTol.setEnabled(brushFixed.isSelected());
            brushMin.setEnabled(brushRange.isSelected());
            brushMax.setEnabled(brushRange.isSelected());
        };
        brushFixed.addActionListener(e -> en.run());
        brushRange.addActionListener(e -> en.run());
        en.run();
        return f.panel();
    }

    /** used by the Polymer tab: copy fitted values into the chain models */
    public void setFloryExponent(double nu) {
        eNu.setText(String.format("%.3f", nu));
        if (!eNuFixed.isSelected()) eNuFixed.doClick();
    }

    public void setKuhnLength(double b) {
        wKuhn.setText(String.format("%.1f", b));
        if (!wKFixed.isSelected()) wKFixed.doClick();
        suggestWormlike();
    }

    public void selectModel(Model m) { modelBox.setSelectedItem(m); }

    /** subunit volume prior from the MW (kDa) or volume (A^3) field; NaN if neither is given */
    private double unitVolume() {
        String mw = hUnitMw.getText().trim(), vol = hUnitVol.getText().trim();
        if (!vol.isEmpty()) return Fields.pos(hUnitVol, "repeating unit volume");
        if (!mw.isEmpty()) {
            double vbar = Fields.pos(hVbar, "partial specific volume");
            return Fields.pos(hUnitMw, "repeating unit MW") * 1000 * vbar / 0.6022;   // Da * cm3/g / N_A, in A^3
        }
        return Double.NaN;
    }

    private void estimateHelix() {
        if (helixData == null) return;
        if (hInfinite.isSelected() && hEllipsoids.isSelected()) {
            estimateSubunits();
            return;
        }
        final double[][] d;
        try {
            d = helixData.get();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Estimate", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (d == null || d[0].length < 20) {
            JOptionPane.showMessageDialog(this, "Select a dataset with at least 20 points in the q range", "Estimate",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        hEstimate.setEnabled(false);
        hEstimateStatus.setText("Surveying…");
        new SwingWorker<HelixEstimator.Estimate, Integer>() {
            @Override
            protected HelixEstimator.Estimate doInBackground() {
                return HelixEstimator.estimate(d[0], d[1], d[2], f -> publish((int) Math.round(100 * f)));
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                hEstimateStatus.setText("Surveying… " + chunks.get(chunks.size() - 1) + " %");
            }

            @Override
            protected void done() {
                hEstimate.setEnabled(true);
                HelixEstimator.Estimate e;
                try {
                    e = get();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    hEstimateStatus.setText("Estimate failed");
                    JOptionPane.showMessageDialog(ModelSettingsPanel.this, "Estimate failed: " + c.getMessage(), "Estimate",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                hEstimateStatus.setText(" ");
                boolean qChange = e.qmax < d[0][d[0].length - 1] * 0.999;
                Object[] options = qChange
                        ? new Object[]{String.format("Apply and set q max to %.3f", e.qmax), "Apply, keep q range", "Cancel"}
                        : new Object[]{"Apply", "Cancel"};
                JTextArea text = new JTextArea(e.summary());
                text.setEditable(false);
                text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                int choice = JOptionPane.showOptionDialog(ModelSettingsPanel.this, new JScrollPane(text),
                        "Helix starting values", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                        options, options[0]);
                if (choice < 0 || choice == options.length - 1) return;
                applyHelixEstimate(e);
                if (qChange && choice == 0 && helixQmax != null) helixQmax.accept(e.qmax);
            }
        }.execute();
    }

    private void estimateSubunits() {
        final double[][] d;
        final double pitch, perTurn, vol, tol;
        final int strands = (Integer) hStrands.getValue();
        try {
            d = helixData.get();
            pitch = Fields.pos(hPitch, "pitch");
            perTurn = Fields.pos(hPerTurn, "subunits per turn");
            vol = unitVolume();
            tol = Fields.pos(hUnitTol, "volume tolerance") / 100.0;
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Estimate", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (d == null || d[0].length < 20) {
            JOptionPane.showMessageDialog(this, "Select a dataset with at least 20 points in the q range", "Estimate",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        hEstimate.setEnabled(false);
        hEstimateStatus.setText("Estimating…");
        new SwingWorker<HelixEstimator.SubunitEstimate, Integer>() {
            @Override
            protected HelixEstimator.SubunitEstimate doInBackground() {
                return HelixEstimator.estimateSubunits(d[0], d[1], d[2], pitch, perTurn, strands, vol, tol,
                        f -> publish((int) Math.round(100 * f)));
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                hEstimateStatus.setText("Estimating… " + chunks.get(chunks.size() - 1) + " %");
            }

            @Override
            protected void done() {
                hEstimate.setEnabled(true);
                hEstimateStatus.setText(" ");
                HelixEstimator.SubunitEstimate e;
                try {
                    e = get();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(ModelSettingsPanel.this, "Estimate failed: " + c.getMessage(), "Estimate",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                JTextArea text = new JTextArea(e.summary());
                text.setEditable(false);
                text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                Object[] options = {"Apply", "Cancel"};
                int choice = JOptionPane.showOptionDialog(ModelSettingsPanel.this, new JScrollPane(text),
                        "Helix of ellipsoidal subunits: starting values", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
                if (choice != 0) return;
                hRMin.setText(Fields.fmt(e.rMin));
                hRMax.setText(Fields.fmt(e.rMax));
                hEllA.setText(Fields.fmt(Math.round(e.a)));
                hEllC.setText(Fields.fmt(Math.round(e.c)));
                helixEnable.run();
            }
        }.execute();
    }

    private void applyHelixEstimate(HelixEstimator.Estimate e) {
        hInfinite.setSelected(true);
        hContinuous.setSelected(true);       // the uniform-strand estimate
        hRMin.setText(Fields.fmt(e.rMin));
        hRMax.setText(Fields.fmt(e.rMax));
        hStrands.setValue(e.strands);
        hStrandRadius.setText(Fields.fmt(e.strandRadius));
        hStrandAngle.setText("180");
        if (e.pitchVisible) {
            hPitch.setText(Fields.fmt(Math.round(e.pitch)));
            hScan.setSelectedIndex(2);
            hScanLo.setText(Fields.fmt(Math.round(e.pitchScanLo)));
            hScanHi.setText(Fields.fmt(Math.round(e.pitchScanHi)));
        } else {
            // no layer line inside q max: any pitch short enough to keep them out gives the same fit
            hScan.setSelectedIndex(0);
            hPitch.setText(Fields.fmt(Math.floor(0.9 * e.strands * 2 * Math.PI / e.qmax)));
        }
        helixEnable.run();
    }

    /** lets "Estimate from data" read the dataset (limited to the fitting q range) and change q max */
    public void setHelixDataSource(java.util.function.Supplier<double[][]> data, java.util.function.DoubleConsumer setQmax) {
        this.helixData = data;
        this.helixQmax = setQmax;
    }

    private JPanel helixPanel() {
        Fields.Form f = new Fields.Form();
        hEstimate.addActionListener(e -> estimateHelix());
        f.row("Starting values", Fields.flow(hEstimate, hEstimateStatus),
                "Survey of radius distribution, strand radius, strands and pitch by least squares (a few seconds); "
                        + "fills in the fields below and suggests q max");
        ButtonGroup length = new ButtonGroup();
        length.add(hFinite);
        length.add(hInfinite);
        f.row("Filaments", Fields.flow(hFinite), "Length distribution, helix radius fixed (or scanned)");
        f.row("", Fields.flow(hInfinite),
                "Filaments longer than the q range resolves (L > ~5 / q min): the length only scales the intensity, so the "
                        + "radius distribution is fitted instead (continuous strands only)");
        f.row("L min / max (Å)", Fields.pair(hLMin, hLMax),
                "Bounds of the filament (axial) length. The suggested maximum is where a rod's Rg reaches 1.5 / q min: longer filaments are not resolved.");
        f.row("r min / max (Å)", Fields.pair(hRMin, hRMax), "Infinitely long: bounds of the helix radius distribution");
        ButtonGroup kind = new ButtonGroup();
        kind.add(hContinuous);
        kind.add(hSubunits);
        kind.add(hEllipsoids);
        f.row("Strands", Fields.flow(hContinuous), "Continuous helical strands: form factor of van der Maarel, J. Appl. Cryst. 56 (2023) 1714");
        f.row("", Fields.flow(hSubunits), "Identical spherical subunits on a helical lattice (e.g. F-actin, amyloid protofilaments)");
        f.row("", Fields.flow(hEllipsoids),
                "Subunits as ellipsoids of revolution on an infinite helical lattice: the lattice gives the low and intermediate q, "
                        + "the subunit shape the high q (decoupling approximation: subunit orientations treated as random)");
        f.row("Helix radius (Å)", Fields.flow(hRadius), "Distance of the strand axes (or subunit centres) from the filament axis");
        f.row("Number of strands", Fields.flow(hStrands), "Coaxial strands (helix starts), equally spaced in azimuth; 1 = single helix, 2 = double helix");
        f.row("Pitch (Å)", Fields.flow(hPitch), "Continuous strands: axial distance for one full turn (2πp)");
        f.row("Strand radius (Å)", Fields.flow(hStrandRadius), "Continuous strands: strand thickness, as a Gaussian profile with <r²> = a²/2; 0 = thin strand");
        f.row("Strand angle φ (°)", Fields.flow(hStrandAngle),
                "Double helix only: azimuthal angle between the two strands. 180 = symmetric; otherwise major and minor grooves, φ = 180 w_minor / (w_major + w_minor)");
        f.row("Rise / twist", Fields.flow(hRise, new JLabel("Å"), hTwist, new JLabel("° per subunit")),
                "Subunits: axial rise and rotation per subunit along a strand. F-actin (1-start): 27.5 Å, -166.7°. Handedness does not affect the scattering.");
        f.row("Subunit radius (Å)", Fields.flow(hSubunitRadius), "Subunits: radius of the spherical subunit; 0 = point subunits");
        f.row("Subunits per turn", Fields.flow(hPerTurn),
                "Ellipsoidal subunits: subunits per turn along one strand (rise = pitch / this). Take pitch and subunits per turn "
                        + "from cryo-EM or the literature if known: a SAXS curve alone pins them down poorly; test them with the scan");
        f.row("Ellipsoid a / c (Å)", Fields.flow(hEllA, new JLabel("/"), hEllC), "Ellipsoidal subunits: semi-axes (a, a, c); c > a prolate, c < a oblate");
        f.row("Repeating unit (optional)", Fields.flow(new JLabel("MW"), hUnitMw, new JLabel("kDa  or volume"), hUnitVol,
                        new JLabel("Å³;  v̄"), hVbar, new JLabel("cm³/g;  ±"), hUnitTol, new JLabel("%")),
                "Optional constraint on the subunit volume for 'Estimate from data': V = MW v̄ / N_A (≈ 1.21 Å³ per Da at v̄ = 0.73). "
                        + "Give the helical repeating unit (e.g. a dimer if dimers repeat). SAXS sees the hydrated subunit, "
                        + "typically 10-30 % larger, hence the tolerance");
        f.row("Estimate by scan", Fields.flow(hScan, hScanLo, new JLabel("-"), hScanHi, new JLabel("Å")),
                "Fit the length distribution at 7 values of the chosen geometry parameter (plus refinement) and take the χ² minimum");
        Runnable en = () -> {
            boolean inf = hInfinite.isSelected();
            if (inf && hSubunits.isSelected()) hContinuous.setSelected(true);
            if (!inf && hEllipsoids.isSelected()) hContinuous.setSelected(true);
            hSubunits.setEnabled(!inf);
            hEllipsoids.setEnabled(inf);
            boolean ell = hEllipsoids.isSelected();
            if (inf && hScan.getSelectedIndex() == 1) hScan.setSelectedIndex(0);
            if (!ell && hScan.getSelectedIndex() == 3) hScan.setSelectedIndex(0);
            hLMin.setEnabled(!inf);
            hLMax.setEnabled(!inf);
            hRMin.setEnabled(inf);
            hRMax.setEnabled(inf);
            boolean cont = hContinuous.isSelected();
            int n = (Integer) hStrands.getValue();
            hPitch.setEnabled((cont || ell) && hScan.getSelectedIndex() != 2);
            hStrandRadius.setEnabled(cont);
            hStrandAngle.setEnabled((cont || ell) && n == 2);
            hPerTurn.setEnabled(ell && hScan.getSelectedIndex() != 3);
            for (JTextField t : new JTextField[]{hEllA, hEllC, hUnitMw, hUnitVol, hVbar, hUnitTol}) t.setEnabled(ell);
            hRise.setEnabled(hSubunits.isSelected());
            hTwist.setEnabled(hSubunits.isSelected());
            hSubunitRadius.setEnabled(hSubunits.isSelected());
            hRadius.setEnabled(!inf && hScan.getSelectedIndex() != 1);
            hScanLo.setEnabled(hScan.getSelectedIndex() > 0);
            hScanHi.setEnabled(hScan.getSelectedIndex() > 0);
        };
        hContinuous.addActionListener(e -> en.run());
        hSubunits.addActionListener(e -> en.run());
        hEllipsoids.addActionListener(e -> en.run());
        hFinite.addActionListener(e -> en.run());
        hInfinite.addActionListener(e -> {
            // infinitely long: the pitch is the geometry the data constrain next to the radius, so scan it by default
            if (hScan.getSelectedIndex() == 0) {
                hScan.setSelectedIndex(2);
                hScanLo.setText("50");
                hScanHi.setText("200");
            }
            en.run();
        });
        hStrands.addChangeListener(e -> en.run());
        hScan.addActionListener(e -> en.run());
        en.run();
        helixEnable = en;
        f.note("Finite: the fitted distribution is the filament-length distribution, the helix geometry is the same for every filament. "
                + "Infinitely long: the radius distribution, with pitch, strands and strand radius (or subunit lattice and shape) shared. "
                + "Volume weighting = mass weighting. The length is only resolved below about q L ~ 5.");
        return f.panel();
    }

    private JPanel coreShellPanel() {
        Fields.Form f = new Fields.Form();
        f.row("core a min / max (Å)", Fields.pair(cAMin, cAMax), "Core equatorial semi-axis bounds");
        f.row("core c min / max (Å)", Fields.pair(cCMin, cCMax), "Core polar semi-axis bounds");
        f.full(coreShape);

        ButtonGroup sg = new ButtonGroup();
        sg.add(shellFixed);
        sg.add(shellRange);
        JPanel shellA = Fields.flow(shellFixed, shellT, new JLabel("Å  ±"), shellTol, new JLabel("%"));
        JPanel shellB = Fields.flow(shellRange, shellMin, new JLabel("-"), shellMax, new JLabel("Å"));
        f.row("Shell", shellA, "Restrict the shell thickness to t ± tolerance (e.g. 14 Å ± 10 %)");
        f.row("", shellB, "Or let the thickness vary inside a range");

        f.row("SLD solvent / shell", Fields.pair(rhoSolvent, rhoShell),
                "Scattering length densities in any consistent units (X-ray: water 9.42, protein ~12, in 1e-6/Å²)");
        ButtonGroup cg = new ButtonGroup();
        cg.add(singleCore);
        cg.add(mixture);
        f.row("Core contents", Fields.flow(singleCore, rhoCore), "One core density for all particles (hollow shell: = solvent)");
        f.row("", Fields.flow(mixture, new JLabel("filled SLD"), rhoFilled),
                "Particles are either empty (core = solvent) or filled; both share one geometry distribution");
        ButtonGroup fg = new ButtonGroup();
        fg.add(estimateFraction);
        fg.add(fixFraction);
        f.row("Empty fraction", Fields.flow(estimateFraction, new JLabel("step"), profileStep),
                "Scan the empty fraction (by number) and report the best value with 68 / 95 % intervals");
        f.row("", Fields.flow(fixFraction, fixedFraction, new JLabel("(by number)")), "Fit with a known empty fraction (0..1, by number)");
        f.note("The empty/filled split is only as reliable as the filled SLD: a wrong value gives a confident, wrong fraction.");

        Runnable en = () -> {
            boolean mix = mixture.isSelected();
            rhoCore.setEnabled(!mix);
            rhoFilled.setEnabled(mix);
            estimateFraction.setEnabled(mix);
            fixFraction.setEnabled(mix);
            profileStep.setEnabled(mix && estimateFraction.isSelected());
            fixedFraction.setEnabled(mix && fixFraction.isSelected());
            shellT.setEnabled(shellFixed.isSelected());
            shellTol.setEnabled(shellFixed.isSelected());
            shellMin.setEnabled(shellRange.isSelected());
            shellMax.setEnabled(shellRange.isSelected());
        };
        for (AbstractButton b : new AbstractButton[]{singleCore, mixture, estimateFraction, fixFraction, shellFixed, shellRange})
            b.addActionListener(e -> en.run());
        en.run();
        return f.panel();
    }

    // ------------------------------------------------------------------ build

    /** validates the model inputs and applies them on top of the given settings (which are modified) */
    public FitSpec build(LangevinSettings s) {
        FitSpec spec = new FitSpec();
        spec.settings = s;
        Model m = selectedModel();
        switch (m) {
            case SPHERE: {
                spec.family = ShapeFamily.SPHERE;
                double lo = Fields.pos(rMin, "R min"), hi = Fields.pos(rMax, "R max");
                Fields.order(lo, hi, "R");
                spec.min = new double[]{lo};
                spec.max = new double[]{hi};
                spec.description = String.format("Sphere, R %s-%s Å", Fields.fmt(lo), Fields.fmt(hi));
                break;
            }
            case SPHEROID: {
                spec.family = ShapeFamily.SPHEROID;
                double a0 = Fields.pos(sAMin, "a min"), a1 = Fields.pos(sAMax, "a max");
                double c0 = Fields.pos(sCMin, "c min"), c1 = Fields.pos(sCMax, "c max");
                Fields.order(a0, a1, "a");
                Fields.order(c0, c1, "c");
                spec.min = new double[]{a0, c0};
                spec.max = new double[]{a1, c1};
                spheroidShape.apply(s);
                spec.description = "Spheroid" + spheroidShape.describe();
                break;
            }
            case TRIAXIAL: {
                spec.family = ShapeFamily.TRIAXIAL_ELLIPSOID;
                double lo = Fields.pos(tMin, "semi-axis min"), hi = Fields.pos(tMax, "semi-axis max");
                Fields.order(lo, hi, "semi-axes");
                spec.min = new double[]{lo, lo, lo};
                spec.max = new double[]{hi, hi, hi};
                spec.description = "Triaxial ellipsoid";
                break;
            }
            case GAUSSIAN_CHAIN: {
                spec.family = ShapeFamily.GAUSSIAN_CHAIN;
                double lo = Fields.pos(gRgMin, "Rg min"), hi = Fields.pos(gRgMax, "Rg max");
                Fields.order(lo, hi, "Rg");
                spec.min = new double[]{lo};
                spec.max = new double[]{hi};
                spec.description = String.format("Gaussian chains, Rg %s-%s Å", Fields.fmt(lo), Fields.fmt(hi));
                break;
            }
            case EV_CHAIN: {
                spec.family = ShapeFamily.EXCLUDED_VOLUME_CHAIN;
                double lo = Fields.pos(eRgMin, "Rg min"), hi = Fields.pos(eRgMax, "Rg max");
                Fields.order(lo, hi, "Rg");
                spec.min = new double[]{lo};
                spec.max = new double[]{hi};
                if (eNuScan.isSelected()) {
                    double a = Fields.pos(eNuLo, "ν from"), b = Fields.pos(eNuHi, "ν to"), st = Fields.pos(eNuStep, "ν step");
                    Fields.order(a, b, "ν scan");
                    if (a <= 0.26 || b >= 1) throw new IllegalArgumentException("ν scan must stay between 0.26 and 1");
                    int n = (int) Math.round((b - a) / st) + 1;
                    if (n < 3 || n > 25) throw new IllegalArgumentException("ν scan needs 3-25 grid points (check the step)");
                    ParamScan ps = new ParamScan();
                    ps.parameter = "nu";
                    ps.unit = "";
                    ps.setter = (set, v) -> set.chainNu = v;
                    ps.grid = new double[n];
                    for (int k = 0; k < n; k++) ps.grid[k] = Math.round((a + k * st) * 1e6) / 1e6;
                    spec.paramScan = ps;
                    s.chainNu = ps.grid[n / 2];
                    spec.description = String.format("Excluded-volume chains, ν scanned %s-%s, Rg %s-%s Å", Fields.fmt(a), Fields.fmt(b), Fields.fmt(lo), Fields.fmt(hi));
                } else {
                    double nu = Fields.pos(eNu, "Flory exponent");
                    if (nu <= 0.26 || nu >= 1) throw new IllegalArgumentException("Flory exponent must be between 0.26 and 1");
                    s.chainNu = nu;
                    spec.description = String.format("Excluded-volume chains (ν %.3f), Rg %s-%s Å", nu, Fields.fmt(lo), Fields.fmt(hi));
                }
                break;
            }
            case WORMLIKE: {
                spec.family = ShapeFamily.WORMLIKE_CHAIN;
                double lo = Fields.pos(wLMin, "L min"), hi = Fields.pos(wLMax, "L max");
                Fields.order(lo, hi, "L");
                s.chainRadius = Fields.nonNeg(wRadius, "cross-section radius");
                spec.min = new double[]{lo};
                spec.max = new double[]{hi};
                if (wKScan.isSelected()) {
                    double a = Fields.pos(wKLo, "b from"), b = Fields.pos(wKHi, "b to");
                    Fields.order(a, b, "b scan");
                    ParamScan ps = new ParamScan();
                    ps.parameter = "b";
                    ps.unit = " A";
                    ps.setter = (set, v) -> set.kuhnLength = v;
                    ps.logScale = true;
                    ps.grid = new double[7];
                    for (int k = 0; k < 7; k++) ps.grid[k] = Math.round(a * Math.pow(b / a, k / 6.0) * 10) / 10.0;
                    spec.paramScan = ps;
                    s.kuhnLength = ps.grid[3];
                    spec.description = String.format("Wormlike chains (b scanned %s-%s Å, R %s Å), L %s-%s Å", Fields.fmt(a), Fields.fmt(b),
                            Fields.fmt(s.chainRadius), Fields.fmt(lo), Fields.fmt(hi));
                } else {
                    s.kuhnLength = Fields.pos(wKuhn, "Kuhn length");
                    spec.description = String.format("Wormlike chains (b %s Å, R %s Å), L %s-%s Å", Fields.fmt(s.kuhnLength),
                            Fields.fmt(s.chainRadius), Fields.fmt(lo), Fields.fmt(hi));
                }
                break;
            }
            case HELIX: {
                boolean inf = hInfinite.isSelected();
                double lo = inf ? Fields.pos(hRMin, "r min") : Fields.pos(hLMin, "L min");
                double hi = inf ? Fields.pos(hRMax, "r max") : Fields.pos(hLMax, "L max");
                Fields.order(lo, hi, inf ? "r" : "L");
                spec.min = new double[]{lo};
                spec.max = new double[]{hi};
                boolean ell = inf && hEllipsoids.isSelected();
                boolean cont = (hContinuous.isSelected() || inf) && !ell;
                spec.family = ell ? ShapeFamily.HELIX_INFINITE_ELLIPSOIDS : inf ? ShapeFamily.HELIX_INFINITE
                        : cont ? ShapeFamily.HELIX : ShapeFamily.HELIX_SUBUNITS;
                s.helixStrands = (Integer) hStrands.getValue();
                int scan = hScan.getSelectedIndex();
                if (scan == 2 && !cont && !ell) throw new IllegalArgumentException("Pitch scan applies to continuous strands; for subunits the pitch follows from rise and twist");
                if (scan == 3 && !ell) throw new IllegalArgumentException("The subunits-per-turn scan applies to ellipsoidal subunits");
                if (inf && scan == 1) throw new IllegalArgumentException("The radius is the fitted distribution for infinitely long filaments; scan the pitch instead");
                if (scan != 1 && !inf) s.helixRadius = Fields.nonNeg(hRadius, "helix radius");
                String geom;
                if (ell) {
                    if (scan != 2) s.helixPitch = Fields.pos(hPitch, "pitch");
                    if (scan != 3) s.helixSubunitsPerTurn = Fields.pos(hPerTurn, "subunits per turn");
                    s.helixEllipsoidA = Fields.pos(hEllA, "ellipsoid a");
                    s.helixEllipsoidC = Fields.pos(hEllC, "ellipsoid c");
                    s.helixStrandAngle = 180;
                    if (s.helixStrands == 2) {
                        s.helixStrandAngle = Fields.pos(hStrandAngle, "strand angle");
                        if (s.helixStrandAngle >= 360) throw new IllegalArgumentException("Strand angle must be between 0 and 360°");
                    }
                    s.helixUnitVolume = unitVolume();
                    s.helixUnitVolumeTolerance = Fields.pos(hUnitTol, "volume tolerance") / 100.0;
                    geom = String.format("%d strand%s of ellipsoids %s × %s Å, %s per turn", s.helixStrands, s.helixStrands > 1 ? "s" : "",
                            Fields.fmt(s.helixEllipsoidA), Fields.fmt(s.helixEllipsoidC),
                            scan == 3 ? "scanned" : Fields.fmt(s.helixSubunitsPerTurn));
                } else if (cont) {
                    if (scan != 2) s.helixPitch = Fields.pos(hPitch, "pitch");
                    s.helixStrandRadius = Fields.nonNeg(hStrandRadius, "strand radius");
                    s.helixStrandAngle = 180;
                    if (s.helixStrands == 2) {
                        s.helixStrandAngle = Fields.pos(hStrandAngle, "strand angle");
                        if (s.helixStrandAngle >= 360) throw new IllegalArgumentException("Strand angle must be between 0 and 360°");
                    }
                    geom = String.format("%d strand%s%s, strand a %s Å", s.helixStrands, s.helixStrands > 1 ? "s" : "",
                            s.helixStrands == 2 && s.helixStrandAngle != 180 ? String.format(" (φ %s°)", Fields.fmt(s.helixStrandAngle)) : "",
                            Fields.fmt(s.helixStrandRadius));
                } else {
                    s.helixRise = Fields.pos(hRise, "rise");
                    s.helixTwist = Fields.num(hTwist, "twist");
                    s.helixSubunitRadius = Fields.nonNeg(hSubunitRadius, "subunit radius");
                    if (hi / s.helixRise > 2_000_000) throw new IllegalArgumentException("L max / rise is too large");
                    geom = String.format("%d strand%s of subunits a %s Å, rise %s Å, twist %s°", s.helixStrands, s.helixStrands > 1 ? "s" : "",
                            Fields.fmt(s.helixSubunitRadius), Fields.fmt(s.helixRise), Fields.fmt(s.helixTwist));
                }
                if (scan > 0) {
                    double a = scan == 1 ? Fields.nonNeg(hScanLo, "scan from") : Fields.pos(hScanLo, "scan from");
                    double b = Fields.pos(hScanHi, "scan to");
                    Fields.order(a, b, "scan");
                    ParamScan ps = new ParamScan();
                    ps.parameter = scan == 1 ? "r_helix" : scan == 2 ? "pitch" : "per_turn";
                    ps.unit = scan == 3 ? "" : " A";
                    ps.setter = scan == 1 ? (set, v) -> set.helixRadius = v : scan == 2 ? (set, v) -> set.helixPitch = v
                            : (set, v) -> set.helixSubunitsPerTurn = v;
                    // layer lines are sharp: the lattice scans need a finer grid than the 7 points of the others
                    int pts = ell && scan >= 2 ? 13 : 7;
                    ps.grid = new double[pts];
                    for (int k = 0; k < pts; k++) ps.grid[k] = Math.round((a + k * (b - a) / (pts - 1.0)) * 10) / 10.0;
                    spec.paramScan = ps;
                    double mid = ps.grid[pts / 2];
                    if (scan == 1) s.helixRadius = mid; else if (scan == 2) s.helixPitch = mid; else s.helixSubunitsPerTurn = mid;
                    spec.description = String.format("Helical filaments (%s scanned %s-%s Å; %s%s), L %s-%s Å", ps.parameter,
                            Fields.fmt(a), Fields.fmt(b), scan == 1 ? "" : "r " + Fields.fmt(s.helixRadius) + " Å, ", geom, Fields.fmt(lo), Fields.fmt(hi));
                } else {
                    spec.description = String.format("Helical filaments (r %s Å%s, %s), L %s-%s Å", Fields.fmt(s.helixRadius),
                            cont ? ", pitch " + Fields.fmt(s.helixPitch) + " Å" : "", geom, Fields.fmt(lo), Fields.fmt(hi));
                }
                if (inf) {
                    int last = spec.paramScan == null ? 0 : spec.paramScan.grid.length - 1;
                    spec.description = spec.paramScan != null
                            ? String.format("Infinitely long helices (%s scanned %s-%s; %s%s), r %s-%s Å",
                            scan == 3 ? "subunits per turn" : "pitch", Fields.fmt(spec.paramScan.grid[0]), Fields.fmt(spec.paramScan.grid[last]),
                            scan == 3 ? "pitch " + Fields.fmt(s.helixPitch) + " Å, " : "", geom, Fields.fmt(lo), Fields.fmt(hi))
                            : String.format("Infinitely long helices (pitch %s Å, %s), r %s-%s Å", Fields.fmt(s.helixPitch), geom,
                            Fields.fmt(lo), Fields.fmt(hi));
                }
                break;
            }
            case MICELLE: {
                spec.family = ShapeFamily.POLYMER_MICELLE;
                double lo = Fields.pos(mRMin, "R_core min"), hi = Fields.pos(mRMax, "R_core max");
                Fields.order(lo, hi, "R_core");
                double g0, g1;
                if (brushFixed.isSelected()) {
                    double rg = Fields.pos(brushRg, "brush Rg"), tol = Fields.nonNeg(brushTol, "brush tolerance") / 100.0;
                    if (tol <= 0 || tol >= 1) throw new IllegalArgumentException("Brush tolerance must be between 0 and 100 %");
                    s.brushRg = rg;
                    s.brushTolerance = tol;
                    g0 = rg * (1 - tol);
                    g1 = rg * (1 + tol);
                } else {
                    g0 = Fields.pos(brushMin, "brush Rg min");
                    g1 = Fields.pos(brushMax, "brush Rg max");
                    Fields.order(g0, g1, "brush Rg");
                    s.brushRg = Double.NaN;
                }
                s.solventDensity = Fields.num(mRhoSolvent, "solvent SLD");
                s.micelleCoreDensity = Fields.num(mRhoCore, "core SLD");
                s.micelleCoronaDensity = Fields.num(mRhoCorona, "corona SLD");
                s.micelleCoreBlockVolume = Fields.pos(mVCore, "core block volume");
                s.micelleCoronaBlockVolume = Fields.pos(mVCorona, "corona block volume");
                s.micellePacking = Fields.pos(mPacking, "core packing");
                if (s.micellePacking > 1) throw new IllegalArgumentException("Core packing is a volume fraction (<= 1)");
                s.micellePenetration = Fields.num(mPen, "penetration d");
                double bsum = s.micelleCoreBlockVolume * (s.micelleCoreDensity - s.solventDensity)
                        + s.micelleCoronaBlockVolume * (s.micelleCoronaDensity - s.solventDensity);
                if (Math.abs(bsum) < 1e-9) throw new IllegalArgumentException("Core and corona contrasts cancel: no forward scattering");
                double n0 = s.micellePacking * 4 * Math.PI / 3 * lo * lo * lo / s.micelleCoreBlockVolume;
                if (n0 < 1) throw new IllegalArgumentException(String.format("N_agg is %.2f at R_core min: raise R_core min or lower the core block volume", n0));
                spec.min = new double[]{lo, g0};
                spec.max = new double[]{hi, g1};
                spec.description = String.format("Block-copolymer micelle, R_core %s-%s Å, brush Rg %s-%s Å", Fields.fmt(lo), Fields.fmt(hi),
                        Fields.fmt(g0), Fields.fmt(g1));
                break;
            }
            default: {
                spec.family = ShapeFamily.CORESHELL_SPHEROID;
                double a0 = Fields.pos(cAMin, "core a min"), a1 = Fields.pos(cAMax, "core a max");
                double c0 = Fields.pos(cCMin, "core c min"), c1 = Fields.pos(cCMax, "core c max");
                Fields.order(a0, a1, "core a");
                Fields.order(c0, c1, "core c");
                double t0, t1;
                if (shellFixed.isSelected()) {
                    double t = Fields.pos(shellT, "shell thickness");
                    double tol = Fields.nonNeg(shellTol, "shell tolerance") / 100.0;
                    if (tol <= 0 || tol >= 1) throw new IllegalArgumentException("Shell tolerance must be between 0 and 100 %");
                    s.shellThickness = t;
                    s.shellTolerance = tol;
                    t0 = t * (1 - tol);
                    t1 = t * (1 + tol);
                } else {
                    t0 = Fields.pos(shellMin, "shell min");
                    t1 = Fields.pos(shellMax, "shell max");
                    Fields.order(t0, t1, "shell");
                    s.shellThickness = Double.NaN;
                }
                spec.min = new double[]{a0, c0, t0};
                spec.max = new double[]{a1, c1, t1};
                s.solventDensity = Fields.num(rhoSolvent, "solvent SLD");
                s.shellDensity = Fields.num(rhoShell, "shell SLD");
                coreShape.apply(s);
                String what;
                if (mixture.isSelected()) {
                    double filled = Fields.num(rhoFilled, "filled SLD");
                    if (filled == s.solventDensity) throw new IllegalArgumentException("Filled SLD equals the solvent SLD: nothing to distinguish");
                    s.coreDensities = new double[]{s.solventDensity, filled};
                    s.speciesNames = new String[]{"empty", "filled"};
                    s.sharedGeometry = true;
                    if (estimateFraction.isSelected()) {
                        spec.profileScan = true;
                        spec.profileStep = Fields.pos(profileStep, "profile step");
                        if (spec.profileStep > 0.5) throw new IllegalArgumentException("Profile step must be <= 0.5");
                        s.fixedSpeciesFraction = 0.5;   // placeholder; the scan sets it per point
                        what = "empty/filled, fraction by profile scan";
                    } else {
                        double p = Fields.num(fixedFraction, "empty fraction");
                        if (p < 0 || p > 1) throw new IllegalArgumentException("Empty fraction must be between 0 and 1");
                        s.fixedSpeciesFraction = p;
                        what = String.format("empty/filled, empty fraction fixed at %.3f", p);
                    }
                } else {
                    s.coreDensity = Fields.num(rhoCore, "core SLD");
                    s.coreDensities = null;
                    s.sharedGeometry = false;
                    what = String.format("core SLD %.2f", s.coreDensity);
                }
                if (s.shellDensity == s.solventDensity && !mixture.isSelected() && s.coreDensity == s.shellDensity)
                    throw new IllegalArgumentException("All SLDs are equal: no contrast");
                spec.description = String.format("Core-shell spheroid%s, shell %s-%s Å, %s", coreShape.describe(),
                        Fields.fmt(t0), Fields.fmt(t1), what);
            }
        }
        return spec;
    }

    // ------------------------------------------------------------------ shape-constraint sub-panel

    /** aspect-ratio window + optional unimodal prior on (a, c), shared by spheroid and core-shell */
    static final class ShapeBox extends JPanel {
        private final JComboBox<String> window = new JComboBox<>(new String[]{
                "Free (data decide prolate/oblate)", "Prolate (c/a >= 1.2)", "Oblate (c/a <= 1/1.2)", "Custom c/a range"});
        private final JTextField ratioMin = Fields.num("1.2"), ratioMax = Fields.num("10");
        private final JCheckBox prior = new JCheckBox("Unimodal prior on (a, c)");
        private final JSpinner modes = new JSpinner(new SpinnerNumberModel(1, 1, 3, 1));
        private final JTextField strength = Fields.num("0.9");
        private final String axes;

        ShapeBox(String axes) {
            super(new GridBagLayout());
            this.axes = axes;
            setBorder(new TitledBorder("Shape constraints"));
            Fields.Form f = new Fields.Form(this);
            f.row("c/a window", window, "Polydisperse spheroids are degenerate between shape and size spread: a window is usually needed");
            f.row("c/a min / max", Fields.pair(ratioMin, ratioMax), "Used with 'Custom'");
            f.row("", Fields.flow(prior, new JLabel("modes"), modes, new JLabel("strength"), strength),
                    "Distribution closest to K Gaussian modes in (ln a, ln c). Strength 0.9 default, check 0.75-1 before quoting widths.");
            Runnable en = () -> {
                boolean custom = window.getSelectedIndex() == 3;
                ratioMin.setEnabled(custom);
                ratioMax.setEnabled(custom);
                modes.setEnabled(prior.isSelected());
                strength.setEnabled(prior.isSelected());
            };
            window.addActionListener(e -> en.run());
            prior.addActionListener(e -> en.run());
            en.run();
        }

        void apply(LangevinSettings s) {
            switch (window.getSelectedIndex()) {
                case 1: s.aspectRatioMin = 1.2; s.aspectRatioMax = Double.POSITIVE_INFINITY; break;
                case 2: s.aspectRatioMin = 0.0; s.aspectRatioMax = 1.0 / 1.2; break;
                case 3: {
                    double lo = Fields.pos(ratioMin, "c/a min"), hi = Fields.pos(ratioMax, "c/a max");
                    Fields.order(lo, hi, "c/a");
                    s.aspectRatioMin = lo;
                    s.aspectRatioMax = hi;
                    break;
                }
                default: s.aspectRatioMin = 0.0; s.aspectRatioMax = Double.POSITIVE_INFINITY;
            }
            if (prior.isSelected()) {
                s.priorAxes = new int[]{0, 1};
                s.priorComponents = (Integer) modes.getValue();
                double l = Fields.pos(strength, "prior strength");
                if (l > 2) throw new IllegalArgumentException("Prior strength above 2 is not sensible");
                s.priorStrength = l;
            } else {
                s.priorAxes = null;
            }
        }

        String describe() {
            String w = new String[]{"", ", prolate", ", oblate", ", custom c/a"}[window.getSelectedIndex()];
            String p = prior.isSelected() ? String.format(", %d-mode prior on %s (strength %s)", (Integer) modes.getValue(), axes, strength.getText()) : "";
            return w + p;
        }
    }
}
