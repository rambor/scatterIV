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
}
