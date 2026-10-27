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
        CORESHELL("Core-shell spheroid");

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

    public ModelSettingsPanel() {
        super(new BorderLayout(0, 6));
        setBorder(new TitledBorder("Model"));
        add(modelBox, BorderLayout.NORTH);
        cardPanel.add(spherePanel(), Model.SPHERE.name());
        cardPanel.add(spheroidPanel(), Model.SPHEROID.name());
        cardPanel.add(triaxialPanel(), Model.TRIAXIAL.name());
        cardPanel.add(coreShellPanel(), Model.CORESHELL.name());
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
