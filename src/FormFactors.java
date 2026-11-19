import FileManager.WorkingDirectory;
import version4.FormFactors.DatasetProvider;
import version4.FormFactors.FormFactorPanel;
import version4.FormFactors.LnpPanel;
import version4.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * FormFactors screen for SCATTER, written like RealSpacePr. In the Scatter constructor, next to realSpacePr:
 * <pre>
 *   formFactorsTab = new FormFactors(WORKING_DIRECTORY, mainProgressBar, status);
 * </pre>
 * The existing formsPanel mouse handler ({@code analysisPane.add(formFactorsTab.getPanel())}) then shows it.
 *
 * A FormFactors.form is optional. If you keep one, bind its root JPanel to {@code contentPane} and leave it empty:
 * the whole screen is built in code and added to it.
 *
 *
 * The dataset list holds the datasets marked "in use" in the Analysis panel (all loaded datasets when none is
 * marked) and is re-read on every getPanel() call. Results stay on screen when the user switches away and back,
 * and a running fit keeps running (its dataset list is not changed until it finishes).
 */
public class FormFactors {

    /** root panel; bound to FormFactors.form if you made one (the GUI Designer creates it), otherwise made here */
    private JPanel contentPane;
    private final FormFactorPanel formFactorPanel;
    /** lipid nanoparticle assessment and core-shell fitting, second tab of the Forms screen */
    private final LnpPanel lnpPanel;
    private final JTabbedPane formTabs = new JTabbedPane();
    private final WorkingDirectory WORKING_DIRECTORY;

    /** collection indices of the datasets listed in the panel */
    private final List<Integer> listed = new ArrayList<>();

    public FormFactors(WorkingDirectory wkd, JProgressBar progressBar, JLabel status) {
        this.WORKING_DIRECTORY = wkd;
        int cpuCores = Runtime.getRuntime().availableProcessors();

        DatasetProvider provider = DatasetProvider.of(
                listed::size,
                i -> dataset(i).getFileName(),
                i -> DatasetProvider.fromSeries(dataset(i).getAllData(), dataset(i).getAllDataError()),
                i -> {
                    RealSpace pr = dataset(i).getRealSpaceModel();
                    return pr == null ? 0 : pr.getDmax();
                });

        formFactorPanel = new FormFactorPanel(provider, cpuCores, Scatter.version);
        formFactorPanel.setMainStatus(progressBar, status);
        // Save starts in the current working directory (changeCWD() may change it while SCATTER runs)
        formFactorPanel.setWorkingDirectory(() -> {
            WorkingDirectory w = Scatter.WORKING_DIRECTORY != null ? Scatter.WORKING_DIRECTORY : WORKING_DIRECTORY;
            return w == null ? null : w.getWorkingDirectory();
        });

        lnpPanel = new LnpPanel(provider);
        lnpPanel.setMainStatus(progressBar, status);
        lnpPanel.setWorkingDirectory(() -> {
            WorkingDirectory w = Scatter.WORKING_DIRECTORY != null ? Scatter.WORKING_DIRECTORY : WORKING_DIRECTORY;
            return w == null ? null : w.getWorkingDirectory();
        });

        formTabs.addTab("Form factors", formFactorPanel);
        formTabs.addTab("Lipid nanoparticles", lnpPanel);
        formTabs.setToolTipTextAt(1, "Assess and fit lipid nanoparticles (LNPs): size, shells, internal mesophase");

        // works with or without a .form: the GUI Designer has already created contentPane at this point
        if (contentPane == null) contentPane = new JPanel();
        contentPane.removeAll();                     // anything placed in the form is replaced by the panel
        contentPane.setLayout(new BorderLayout());   // replaces the designer's GridLayoutManager
        contentPane.add(formTabs, BorderLayout.CENTER);
    }

    /** re-reads the loaded collection and returns the panel to place in the main form */
    public JPanel getPanel() {
        if (!formFactorPanel.isRunning() && !lnpPanel.isRunning()) {
            Collection collection = Scatter.collectionSelected;
            listed.clear();
            int total = collection == null ? 0 : collection.getTotalDatasets();
            for (int i = 0; i < total; i++) if (collection.getDataset(i).getInUse()) listed.add(i);
            if (listed.isEmpty()) for (int i = 0; i < total; i++) listed.add(i);
            formFactorPanel.refreshDatasets();
            lnpPanel.refreshDatasets();
        }
        return contentPane;
    }

    private Dataset dataset(int i) {
        return Scatter.collectionSelected.getDataset(listed.get(i));
    }
}
