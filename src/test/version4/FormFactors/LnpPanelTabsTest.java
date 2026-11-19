package version4.FormFactors;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Bringing a tab to the front. This is where a crash hid: components get wrapped as the panel grows — the
 * assessment chart gained a toolbar above it, the fit curve lives in a sub-tabbed pane — and
 * JTabbedPane.setSelectedComponent throws "component not found in tabbed pane" for anything that is not itself
 * one of the tabs. The panel therefore asks for the tab *holding* a component, and these check that this keeps
 * working however deeply the component is nested.
 */
public class LnpPanelTabsTest {

    @BeforeClass
    public static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static LnpPanel panel() {
        double[][] data = new double[3][120];
        for (int i = 0; i < 120; i++) {
            data[0][i] = 0.01 + 0.3 * i / 119.0;
            data[1][i] = 1.0 / (1 + i);
            data[2][i] = 0.02 * data[1][i];
        }
        return new LnpPanel(DatasetProvider.of(() -> 1, i -> "test", i -> data, i -> 0.0));
    }

    /** every component nested anywhere under the panel can be brought to the front without throwing */
    @Test
    public void anyNestedComponentCanBeBroughtToTheFront() {
        LnpPanel p = panel();
        JTabbedPane tabs = findTabs(p);
        assertNotNull("the panel should have a tabbed pane", tabs);

        List<Component> deep = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) collect(tabs.getComponentAt(i), deep, 0);
        assertTrue("there should be deeply nested components to try", deep.size() > 20);

        for (Component c : deep) p.showTabFor(c);          // the crash was an exception from exactly this
    }

    /** and the tab that ends up selected is the one actually holding the component */
    @Test
    public void theRightTabIsSelected() {
        LnpPanel p = panel();
        JTabbedPane tabs = findTabs(p);
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component tab = tabs.getComponentAt(i);
            List<Component> inside = new ArrayList<>();
            collect(tab, inside, 0);
            if (inside.isEmpty()) continue;
            Component deepest = inside.get(inside.size() - 1);
            p.showTabFor(deepest);
            assertEquals("asking for a component in tab '" + tabs.getTitleAt(i) + "' should select that tab",
                    tab, tabs.getSelectedComponent());
        }
    }

    /** a component belonging to no tab, and null, must be ignored rather than throw */
    @Test
    public void unknownComponentsAreIgnored() {
        LnpPanel p = panel();
        JTabbedPane tabs = findTabs(p);
        int before = tabs.getSelectedIndex();
        p.showTabFor(new JLabel("not in the panel"));
        p.showTabFor(null);
        assertEquals("the selection should not move", before, tabs.getSelectedIndex());
    }

    private static JTabbedPane findTabs(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTabbedPane && ((JTabbedPane) k).getTabCount() > 5) return (JTabbedPane) k;
            if (k instanceof Container) {
                JTabbedPane t = findTabs((Container) k);
                if (t != null) return t;
            }
        }
        return null;
    }

    private static void collect(Component c, List<Component> out, int depth) {
        if (depth > 6) return;
        out.add(c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, out, depth + 1);
    }
}
