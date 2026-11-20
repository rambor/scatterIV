package version4.FormFactors;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Every tab builds, lays out and paints without throwing.
 *
 * <p>This is the cheapest test in the module and it covers the failure users actually hit: the panel
 * crashed on switching tabs because a component was looked up in a tabbed pane it did not belong to. No
 * numerical test would have caught it, because nothing numerical was wrong. The unit tests elsewhere check
 * that the answers are right; this one checks that the user can reach them.
 *
 * <p>It paints as well as builds. A component that constructs cleanly and then divides by zero in
 * {@code paintComponent} is a blank panel and a stack trace in the log, which is how several of these
 * drawings would fail on a dataset with no fit behind them yet.
 */
public class LnpSmokeTest {

    @BeforeClass
    public static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static double[][] curve(int n, boolean peak) {
        LnpModel m = new LnpModel(3, peak);
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1e6; p[LnpModel.R0] = 220; p[LnpModel.SIGMA] = 26;
        if (peak) {
            p[LnpModel.PEAK_Q] = 0.127; p[LnpModel.PEAK_W] = 0.012;
            p[LnpModel.PEAK_A] = 0.3 * m.intensity(p, new double[]{0.127})[0];
        }
        return LnpTestData.fromModel(m, p, 0.007, 0.41, n, 21L);
    }

    private static LnpPanel panel(double[][] d) {
        return new LnpPanel(DatasetProvider.of(() -> 1, i -> "smoke", i -> d, i -> 0.0));
    }

    private static List<Component> all(Component c) {
        List<Component> out = new ArrayList<>();
        ArrayDeque<Component> todo = new ArrayDeque<>();
        todo.add(c);
        while (!todo.isEmpty()) {
            Component k = todo.poll();
            out.add(k);
            if (k instanceof Container) for (Component j : ((Container) k).getComponents()) todo.add(j);
        }
        return out;
    }

    /** paints the whole panel into an image, which exercises every drawing that is currently on screen */
    private static void paint(LnpPanel p) {
        p.setSize(1280, 860);
        realise(p);
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(1280, 860, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try { p.paint(g); } finally { g.dispose(); }
    }

    /** every tab can be selected, laid out and painted, on data that has never been fitted */
    @Test
    public void everyTabSurvivesBeingShownBeforeAnyFit() {
        LnpPanel p = panel(curve(300, true));
        p.setSize(1280, 860);
        realise(p);
        for (Component c : all(p)) {
            if (!(c instanceof JTabbedPane)) continue;
            JTabbedPane tabs = (JTabbedPane) c;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                String name = tabs.getTitleAt(i);
                try {
                    tabs.setSelectedIndex(i);
                    paint(p);
                } catch (RuntimeException e) {
                    throw new AssertionError("tab '" + name + "' failed before any fit: " + e, e);
                }
            }
        }
    }

    /** and again after a fit, which is when most of the drawings have something to draw */
    @Test
    public void everyTabSurvivesAfterAFit() {
        LnpPanel p = panel(curve(300, true));
        p.setSize(1280, 860);
        realise(p);
        p.runAssessment();
        p.selectPreset(LnpPanel.Preset.VESICLE);
        for (Component c : all(p)) {
            if (!(c instanceof JTabbedPane)) continue;
            JTabbedPane tabs = (JTabbedPane) c;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                String name = tabs.getTitleAt(i);
                try {
                    tabs.setSelectedIndex(i);
                    paint(p);
                } catch (RuntimeException e) {
                    throw new AssertionError("tab '" + name + "' failed after a fit: " + e, e);
                }
            }
        }
    }

    /** switching presets does not leave a drawing holding stale parameters it cannot paint */
    @Test
    public void everyPresetCanBeSelectedInTurn() {
        LnpPanel p = panel(curve(300, false));
        p.setSize(1280, 860);
        realise(p);
        for (LnpPanel.Preset preset : LnpPanel.Preset.values()) {
            try {
                p.selectPreset(preset);
                paint(p);
            } catch (RuntimeException e) {
                throw new AssertionError("preset '" + preset + "' failed: " + e, e);
            }
        }
    }

    /** the assessment runs on curves that have nothing in them to find */
    @Test
    public void theAssessmentSurvivesUninformativeData() {
        double[] q = new double[220], I = new double[220], s = new double[220];
        for (int i = 0; i < q.length; i++) {
            q[i] = 0.007 + 0.4 * i / (q.length - 1.0);
            I[i] = 1.0;                                  // flat: no particle, no peak, no slope
            s[i] = 0.05;
        }
        LnpAssessment.Report r = LnpAssessment.analyse(q, I, s, Double.NaN, false);
        assertNotNull(r);
        assertNotNull(r.peaks);
        LnpPanel.Suggestion sg = LnpPanel.suggestionFor(r);
        assertNotNull("a featureless curve still gets a route", sg);
    }

    /** a curve of almost no points does not crash the pipeline */
    @Test
    public void aVeryShortCurveIsHandled() {
        double[][] d = curve(25, false);
        LnpPanel p = panel(d);
        p.setSize(1000, 700);
        realise(p);
        try {
            p.runAssessment();
            paint(p);
        } catch (RuntimeException e) {
            throw new AssertionError("25 points should be handled, not crash: " + e, e);
        }
    }

    /**
     * No control is pushed off the side of the window it sits in.
     *
     * <p>A flow layout does not push the overflow off the right edge, it wraps it to a second line — and
     * then the row is clipped, because the height its parent asked for was the height of one line. The
     * control is laid out, is reported visible, and cannot be seen. Twice now that has hidden the control a
     * user needed: the Edit and Library buttons on the lipid row, and the button that starts the cargo
     * analysis. Neither threw anything. The test is therefore whether each control lies inside the box its
     * parent actually has, not whether it fits the window.
     */
    @Test
    public void noControlIsLaidOutBeyondTheWindow() {
        LnpPanel p = panel(curve(300, true));
        int width = 980;                                 // a normal laptop window, not a wide one
        p.setSize(width, 760);
        realise(p);
        for (Component c : all(p)) {
            if (!(c instanceof JTabbedPane)) continue;
            JTabbedPane tabs = (JTabbedPane) c;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                tabs.setSelectedIndex(i);
                realise(p);
                paint(p);
                for (Component k : all(tabs.getComponentAt(i))) {
                    if (!(k instanceof AbstractButton) && !(k instanceof JTextField)
                            && !(k instanceof JComboBox) && !(k instanceof JSpinner)) continue;
                    if (!k.isVisible() || k.getWidth() == 0) continue;
                    if (inScrollPane(k)) continue;       // a scroll pane is allowed to hold more than it shows
                    Container parent = k.getParent();
                    if (parent == null || parent.getHeight() == 0) continue;
                    assertTrue(describe(k) + " on tab '" + tabs.getTitleAt(i) + "' is clipped: it runs to y = "
                                    + (k.getY() + k.getHeight()) + " inside a parent "
                                    + parent.getHeight() + " px tall (window " + width + " px wide)",
                            k.getY() + k.getHeight() <= parent.getHeight() + 1);
                    assertTrue(describe(k) + " on tab '" + tabs.getTitleAt(i) + "' runs past its parent: x = "
                                    + (k.getX() + k.getWidth()) + " of " + parent.getWidth(),
                            k.getX() + k.getWidth() <= parent.getWidth() + 1);
                }
            }
        }
    }

    /**
     * Lays the panel out for real.
     *
     * <p>{@code doLayout} only arranges the top level; everything nested inside a tab stays at zero size,
     * which silently turned the check below into no check at all — every control was skipped for having no
     * width. {@code addNotify} followed by {@code validate} lays out the whole tree, headless included.
     */
    private static void realise(LnpPanel p) {
        if (!p.isDisplayable()) p.addNotify();
        p.validate();
    }

    private static boolean inScrollPane(Component c) {
        for (Component k = c; k != null; k = k.getParent()) if (k instanceof JScrollPane) return true;
        return false;
    }

    private static String describe(Component c) {
        if (c instanceof AbstractButton) {
            String t = ((AbstractButton) c).getText();
            if (t != null && !t.isEmpty()) return "button '" + t + "'";
        }
        return c.getClass().getSimpleName();
    }

    /** data in nm^-1 is recognised rather than silently fitted as Angstrom */
    @Test
    public void nanometreDataIsRecognised() {
        double[][] d = curve(300, true);
        double[] nm = new double[d[0].length];
        for (int i = 0; i < nm.length; i++) nm[i] = d[0][i] * 10;
        assertEquals("a curve reaching 4 nm^-1 is not Angstrom data",
                LnpUnits.Verdict.NANOMETRE, LnpUnits.inspect(nm).verdict);
        assertEquals(LnpUnits.Verdict.ANGSTROM, LnpUnits.inspect(d[0]).verdict);
    }
}
