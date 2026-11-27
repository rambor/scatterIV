import FileManager.WorkingDirectory;
import version4.FormFactors.DatasetProvider;
import version4.FormFactors.LnpPanel;
import version4.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * LNP screen for SCATTER, written like FormFactors and RealSpacePr. In the Scatter constructor:
 * <pre>
 *   lnpTab = new Lnp(WORKING_DIRECTORY, mainProgressBar, status);
 * </pre>
 * The lnpButtonPanel mouse handler ({@code analysisPane.add(lnpTab.getPanel())}) then shows it.
 *
 * <p>Lipid nanoparticles get a screen of their own rather than a tab under Forms because the analysis is a
 * different kind of work: it starts from an assessment of what the sample is (did a particle form, is the cargo
 * there, is there an internal mesophase) and only then reaches a form-factor fit, and much of it -- Bragg
 * indexing, phase sketches, morphology populations, the series across a formulation screen, the comparison with
 * electron microscopy -- has no counterpart in the form-factor library.
 *
 * <p>A Lnp.form is optional. If you keep one, bind its root JPanel to {@code contentPane} and leave it empty:
 * the whole screen is built in code and added to it.
 *
 * <p>The dataset list holds the datasets marked "in use" in the Analysis panel (all loaded datasets when none is
 * marked) and is re-read on every getPanel() call. Results stay on screen when the user switches away and back,
 * and a running fit keeps running (its dataset list is not changed until it finishes).
 */
public class Lnp {

    /** root panel; bound to Lnp.form if you made one (the GUI Designer creates it), otherwise made here */
    private JPanel contentPane;
    private final LnpPanel lnpPanel;
    private final WorkingDirectory WORKING_DIRECTORY;

    /** collection indices of the datasets listed in the panel */
    private final List<Integer> listed = new ArrayList<>();

    public Lnp(WorkingDirectory wkd, JProgressBar progressBar, JLabel status) {
        this.WORKING_DIRECTORY = wkd;

        DatasetProvider provider = DatasetProvider.of(
                listed::size,
                i -> dataset(i).getFileName(),
                i -> DatasetProvider.fromSeries(dataset(i).getAllData(), dataset(i).getAllDataError()),
                i -> {
                    RealSpace pr = dataset(i).getRealSpaceModel();
                    return pr == null ? 0 : pr.getDmax();
                });

        lnpPanel = new LnpPanel(provider);
        lnpPanel.setMainStatus(progressBar, status);
        // Save starts in the current working directory (changeCWD() may change it while SCATTER runs)
        lnpPanel.setWorkingDirectory(() -> {
            WorkingDirectory w = Scatter.WORKING_DIRECTORY != null ? Scatter.WORKING_DIRECTORY : WORKING_DIRECTORY;
            return w == null ? null : w.getWorkingDirectory();
        });

        // works with or without a .form: the GUI Designer has already created contentPane at this point
        if (contentPane == null) contentPane = new JPanel();
        contentPane.removeAll();                     // anything placed in the form is replaced by the panel
        contentPane.setLayout(new BorderLayout());   // replaces the designer's GridLayoutManager
        contentPane.add(lnpPanel, BorderLayout.CENTER);
    }

    /** re-reads the loaded collection and returns the panel to place in the main form */
    public JPanel getPanel() {
        if (!lnpPanel.isRunning()) {
            Collection collection = Scatter.collectionSelected;
            listed.clear();
            int total = collection == null ? 0 : collection.getTotalDatasets();
            listed.addAll(listedIndices(total));
            lnpPanel.refreshDatasets();
        }
        return contentPane;
    }

    /**
     * Which of the loaded datasets the panel offers: all of them.
     *
     * <p>This used to list only the datasets marked in use, falling back to all of them when none was.
     * Loading four files and having one of them active then left a dropdown with a single entry and no
     * indication that the other three existed — the panel appeared to have lost them. Whether a dataset is
     * in use governs what the Analysis panel plots; here the user is picking one dataset deliberately from
     * a list, which is a different question, and hiding the rest answers it wrongly.
     *
     * <p>The order is the collection's own, so the numbers in this dropdown are the numbers the dataset
     * carries everywhere else in SCATTER.
     */
    static List<Integer> listedIndices(int total) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Math.max(total, 0); i++) out.add(i);
        return out;
    }

    private Dataset dataset(int i) {
        return Scatter.collectionSelected.getDataset(listed.get(i));
    }
}
