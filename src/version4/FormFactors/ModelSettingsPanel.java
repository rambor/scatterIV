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
