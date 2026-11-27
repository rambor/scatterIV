package version4.FormFactors;

import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;

import static org.junit.Assert.*;

/**
 * Every loaded dataset reaches the panel's dropdown.
 *
 * <p>The panel offers one dataset at a time and the dropdown is how the user chooses. A dataset missing
 * from it has, as far as the user can tell, not been loaded: four files opened and one listed looked like
 * the panel had lost three of them. The cause was a filter on whether a dataset was marked in use, which
 * governs what the Analysis panel plots and has nothing to do with which one somebody wants to model here.
 */
public class LnpDatasetListTest {

    @BeforeClass
    public static void headless() { System.setProperty("java.awt.headless", "true"); }

    private static double[][] curve() {
        double[][] d = new double[3][120];
        for (int i = 0; i < 120; i++) {
            d[0][i] = 0.007 + 0.4 * i / 119.0;
            d[1][i] = 1.0 / (1 + i);
            d[2][i] = 0.02 * d[1][i];
        }
        return d;
    }

    private static JComboBox<?> firstCombo(Component root) {
        ArrayDeque<Component> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty()) {
            Component c = todo.poll();
            if (c instanceof JComboBox) return (JComboBox<?>) c;
            if (c instanceof Container) for (Component k : ((Container) c).getComponents()) todo.add(k);
        }
        return null;
    }

    /** whatever the provider reports, all of it is offered */
    @Test
    public void everyDatasetTheProviderReportsIsListed() {
        for (int n : new int[]{1, 2, 4, 9}) {
            final int count = n;
            double[][] d = curve();
            LnpPanel p = new LnpPanel(DatasetProvider.of(() -> count,
                    i -> "file_" + i, i -> d, i -> 580.0));
            p.setSize(1100, 760);
            p.addNotify();
            p.validate();
            p.refreshDatasets();
            JComboBox<?> box = firstCombo(p);
            assertNotNull("the panel should have a dataset dropdown", box);
            assertEquals(count + " datasets loaded should give " + count + " entries",
                    count, box.getItemCount());
            for (int i = 0; i < count; i++)
                assertTrue("entry " + i + " should name its file, was " + box.getItemAt(i),
                        String.valueOf(box.getItemAt(i)).contains("file_" + i));
        }
    }

    /** the numbering matches the position in the collection, so it agrees with the rest of SCATTER */
    @Test
    public void entriesAreNumberedFromOne() {
        double[][] d = curve();
        LnpPanel p = new LnpPanel(DatasetProvider.of(() -> 3, i -> "x" + i, i -> d, i -> 580.0));
        p.setSize(1100, 760);
        p.addNotify();
        p.validate();
        p.refreshDatasets();
        JComboBox<?> box = firstCombo(p);
        for (int i = 0; i < 3; i++)
            assertTrue("entry should start with " + (i + 1) + ": but was " + box.getItemAt(i),
                    String.valueOf(box.getItemAt(i)).startsWith((i + 1) + ":"));
    }

    /** a dataset with no name still gets an entry rather than vanishing */
    @Test
    public void anUnnamedDatasetStillAppears() {
        double[][] d = curve();
        LnpPanel p = new LnpPanel(DatasetProvider.of(() -> 2,
                i -> i == 0 ? "" : null, i -> d, i -> 580.0));
        p.setSize(1100, 760);
        p.addNotify();
        p.validate();
        p.refreshDatasets();
        assertEquals("both should be listed even with no usable name", 2, firstCombo(p).getItemCount());
    }

    /** no datasets at all is not an error */
    @Test
    public void noDatasetsIsHandled() {
        double[][] d = curve();
        LnpPanel p = new LnpPanel(DatasetProvider.of(() -> 0, i -> "none", i -> d, i -> 0.0));
        p.setSize(1100, 760);
        p.addNotify();
        p.validate();
        p.refreshDatasets();
        assertEquals(0, firstCombo(p).getItemCount());
    }

    /**
     * Choosing another dataset clears what belonged to the last one.
     *
     * <p>The plots carry no dataset name of their own, so a fit of the previous file left sitting under a
     * newly chosen one reads as a fit of the new one. The dataset line updated and the curves did not,
     * which is worse than either updating or not: it looked as though the new data had been loaded.
     */
    @Test
    public void switchingDatasetClearsTheOldResults() {
        final double[][][] sets = new double[2][][];
        for (int k = 0; k < 2; k++) {
            double[][] d = new double[3][200];
            for (int i = 0; i < 200; i++) {
                d[0][i] = 0.007 + 0.4 * i / 199.0;
                d[1][i] = (k + 1) * 1.0 / (1 + i);
                d[2][i] = 0.02 * d[1][i];
            }
            sets[k] = d;
        }
        LnpPanel p = new LnpPanel(DatasetProvider.of(() -> 2, i -> "file_" + i, i -> sets[i], i -> 580.0));
        p.setSize(1200, 800);
        p.addNotify();
        p.validate();
        p.refreshDatasets();

        JComboBox<?> box = firstCombo(p);
        box.setSelectedIndex(0);
        p.runAssessment();
        assertTrue("the first dataset should have been assessed", p.hasCachedData());

        box.setSelectedIndex(1);
        assertFalse("choosing another dataset must drop the previous one's results", p.hasCachedData());
    }

    /** and going back to the same dataset does not throw away a current assessment for no reason */
    @Test
    public void reselectingTheSameDatasetKeepsItsResults() {
        double[][] d = curve();
        LnpPanel p = new LnpPanel(DatasetProvider.of(() -> 2, i -> "file_" + i, i -> d, i -> 580.0));
        p.setSize(1200, 800);
        p.addNotify();
        p.validate();
        p.refreshDatasets();
        JComboBox<?> box = firstCombo(p);
        box.setSelectedIndex(0);
        p.runAssessment();
        box.setSelectedIndex(0);
        assertTrue("re-choosing the dataset already assessed should keep it", p.hasCachedData());
    }
}
