package version4.FormFactors;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Explanatory text has to fit the window the user has.
 *
 * <p>A pixel width in the style attribute is the usual way to make a JLabel wrap at all, but it fixes the
 * label's preferred width too. Several of these were written at 820 and 900 px, which made the tabs holding
 * them refuse to be any narrower: the sentence ran off the side and the only way to read the end of it was
 * to drag the frame out to full width.
 */
public class LnpWrappingTest {

    @BeforeClass
    public static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static LnpPanel panel() {
        double[][] data = new double[3][200];
        for (int i = 0; i < 200; i++) {
            data[0][i] = 0.005 + 0.33 * i / 199.0;
            data[1][i] = 1.0 / (1 + i);
            data[2][i] = 0.02 * data[1][i];
        }
        return new LnpPanel(DatasetProvider.of(() -> 1, i -> "test", i -> data, i -> 0.0));
    }

    private static void collect(Component c, List<JLabel> out) {
        if (c instanceof JLabel) out.add((JLabel) c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, out);
    }

    /**
     * No tab may be made wider than the window by its own explanatory text.
     *
     * <p>This is the invariant the earlier tests here only approached. They checked the width written into the
     * CSS, which is necessary and turned out not to be sufficient: a label wrapped correctly to 867 px still
     * reported a preferred width of 1127, that demand became the tab's minimum width, and the sentence ran off
     * the side of the panel anyway. Eight of the fourteen tabs did this, which is why the check is now over
     * every tab at several window sizes rather than over one label.</p>
     *
     * <p>The event queue has to be flushed: the labels re-wrap on a resize event, so without it every label is
     * still at the width it was first laid out at and the test passes while saying nothing.</p>
     */
    @Test
    public void noTabIsForcedWiderThanItsWindowByItsOwnText() throws Exception {
        for (int width : new int[]{1024, 1280, 1680}) {
            LnpPanel p = panel();
            p.setSize(width, 880);
            p.addNotify();
            p.validate();
            JTabbedPane tabs = p.tabs();

            for (int i = 0; i < tabs.getTabCount(); i++) {
                tabs.setSelectedIndex(i);
                for (int pass = 0; pass < 2; pass++) {
                    p.validate();
                    SwingUtilities.invokeAndWait(() -> { });
                }
                Component tab = tabs.getComponentAt(i);
                if (tab.getWidth() <= 0) continue;

                List<JLabel> labels = new ArrayList<>();
                collect(tab, labels);
                for (JLabel l : labels) {
                    String t = l.getText();
                    if (t == null || !t.startsWith("<html") || t.length() < 200) continue;
                    int want = l.getPreferredSize().width;
                    assertTrue(String.format("at a %d px window the '%s' tab is %d px wide but a label asks for"
                                    + " %d, so the end of the sentence is off the side of the panel:%n%s",
                                    width, tabs.getTitleAt(i), tab.getWidth(), want,
                                    t.substring(0, Math.min(180, t.length()))),
                            want <= tab.getWidth());
                }
            }
        }
    }

    /** the explanatory labels must not carry a width that no ordinary window can satisfy */
    @Test
    public void noLabelDemandsAnOversizeWindow() {
        LnpPanel p = panel();
        p.setSize(900, 700);
        p.doLayout();

        List<JLabel> labels = new ArrayList<>();
        collect(p, labels);
        assertFalse("the panel should have labels to check", labels.isEmpty());

        for (JLabel l : labels) {
            String t = l.getText();
            if (t == null || !t.contains("width:")) continue;
            int at = t.indexOf("width:") + 6;
            int px = t.indexOf("px", at);
            int w = Integer.parseInt(t.substring(at, px));
            assertTrue("a label is pinned to " + w + " px, wider than the panel it sits in:\n" + t,
                    w <= 820);
        }
    }

    private static int askedWidth(JLabel l) {
        String t = l.getText();
        int at = t.indexOf("width:") + 6;
        return Integer.parseInt(t.substring(at, t.indexOf("px", at)));
    }

    /** the width follows the container it was told to track, rather than staying where it was written */
    @Test
    public void theTextFollowsTheContainerWidth() {
        JPanel host = new JPanel();
        JLabel l = Fields.wrappingLabel(host, "Some explanatory text that has to fit the window.");

        host.setSize(1400, 200);
        host.dispatchEvent(new java.awt.event.ComponentEvent(host, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        int wide = askedWidth(l);

        host.setSize(520, 200);
        host.dispatchEvent(new java.awt.event.ComponentEvent(host, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        int narrow = askedWidth(l);

        assertTrue("narrow " + narrow + " should be less than wide " + wide, narrow < wide);
        assertTrue("and it must fit inside the container (asked " + narrow + " of 520)", narrow <= 520);
        assertTrue("while still using most of a wide one (asked " + wide + " of 1400)", wide >= 1400 - 60);
    }

    /** dragged very narrow it stops at a floor instead of collapsing to one word per line */
    @Test
    public void itDoesNotCollapseToAColumn() {
        JPanel host = new JPanel();
        JLabel l = Fields.wrappingLabel(host, "Some explanatory text.");
        host.setSize(60, 200);
        host.dispatchEvent(new java.awt.event.ComponentEvent(host, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        assertTrue("a floor should hold the text readable", askedWidth(l) >= 200);
    }

    /** a label whose message changes keeps wrapping to the current width, not the width when it was built */
    @Test
    public void aChangingMessageStillWraps() {
        JPanel host = new JPanel();
        JLabel l = new JLabel();
        host.setSize(1200, 100);
        Fields.setWrappingText(l, host, "first message");
        assertTrue(askedWidth(l) > 1000);

        host.setSize(400, 100);
        host.dispatchEvent(new java.awt.event.ComponentEvent(host, java.awt.event.ComponentEvent.COMPONENT_RESIZED));
        Fields.setWrappingText(l, host, "a different and rather longer second message");
        assertTrue("the new message must use the new width, not the old one", askedWidth(l) <= 400);
        assertTrue("and it must actually be the new message", l.getText().contains("second message"));
    }

    /**
     * The Model column's explanatory note wraps, and nothing it does disturbs the column it sits in.
     *
     * <p>Two attempts at this note broke the form, and neither threw anything. Asking the form for its
     * panel so the note could track it is what closes a form off — it adds the vertical glue and advances
     * the row counter — so every control added after it landed below the glue. Attaching the tracking
     * after the form was built removed that and introduced a worse fault: inside a GridBagLayout, a label
     * whose preferred width follows its container is a feedback loop, each wider measurement widening the
     * column being measured, and the parameter table was driven off to the right until its numbers could
     * not be seen.
     *
     * <p>So the test is not only that the note wraps. It is that the column does not move when the window
     * does, which is what a feedback loop of that kind destroys.
     */
    @Test
    public void theModelScopeNoteWrapsWithoutDisturbingTheColumn() {
        Integer tableRight = null, noteWidth = null;
        for (int width : new int[]{1024, 1440, 1920}) {
            LnpPanel p = panel();
            p.setSize(width, 860);
            p.addNotify();
            p.validate();
            p.refreshDatasets();
            p.validate();

            JLabel note = null;
            JTable table = null;
            java.util.ArrayDeque<Component> todo = new java.util.ArrayDeque<>();
            todo.add(p);
            while (!todo.isEmpty()) {
                Component c = todo.poll();
                if (c instanceof JLabel
                        && String.valueOf(((JLabel) c).getText()).contains("discrete particles")) note = (JLabel) c;
                if (c instanceof JTable && table == null) table = (JTable) c;
                if (c instanceof Container) for (Component k : ((Container) c).getComponents()) todo.add(k);
            }

            assertNotNull("the Model section should carry the scope note", note);
            assertTrue("it should wrap onto several lines, not one: height was " + note.getHeight(),
                    note.getHeight() > 60);
            assertTrue("it should point at where stacked phases are handled",
                    note.getText().contains("Structure"));

            assertNotNull("the parameter table should be laid out", table);
            Point at = SwingUtilities.convertPoint(table.getParent(), table.getLocation(), p);
            int right = at.x + table.getWidth();
            assertTrue("the parameter table should stay in the left column, its right edge was " + right
                    + " in a " + width + " px window", right < width * 0.6);

            // and none of it may move when the window does
            if (tableRight == null) { tableRight = right; noteWidth = note.getWidth(); }
            else {
                assertEquals("the parameter table must not move with the window width",
                        tableRight.intValue(), right);
                assertEquals("nor the note widen with it", noteWidth.intValue(), note.getWidth());
            }
        }
    }
}
