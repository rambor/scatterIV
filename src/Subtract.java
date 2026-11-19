import FileManager.FileListBuilder;
import FileManager.ReceivedDroppedFiles;
import net.iharder.dnd.FileDrop;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import scatter.api.Curve;
import scatter.api.FrameFilter;
import version4.Collection;
import version4.Constants;
import version4.Subtraction;
import version4.plots.XYPlot;
import version4.sasCIF.SasObject;
import version4.sasCIF.SasObjectForm;
import version4.tableModels.SampleBufferElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Subtract {
    // ---- components bound by Subtract.form ----
    private JPanel contentPane;
    private JCheckBox averageCheckBox;
    private JComboBox comboBoxSubtractBins;
    private JComboBox subtractionCutOff;
    private JPanel OutMergePanel;
    private JButton outputDirButton;
    private JButton SUBTRACTButton;
    private JButton clearSamplesButton;
    private JButton clearBuffersButton;
    private JButton editDetailsButton;
    private JCheckBox subtractFromMedianCheckBox;
    private JCheckBox scaleThenMergeCheckBox;
    private JCheckBox buildListFromFileCheckBox;
    private JTextField saveAsTextField;
    private JButton loadSamplesButton;
    private JPanel bufferIofQPanel;
    private JPanel SampleIofQPanel;
    private JPanel sampleSimPanel;
    private JPanel BufferSimPanel;
    private JScrollPane samples;
    private JScrollPane buffers;
    private JButton loadBuffersButton;
    private JPanel samplesPanel;
    private JPanel BuffersPanel;
    private JTextField qminField;
    private JTextField qmaxField;
    private JLabel outputDirLabel;
    private JTextField sampleStarttextField;
    private JTextField sampleEndtextField;
    private JTextField bufferStartField;
    private JTextField bufferEndField;
    private JButton stopSubtractButton;

    // ---- models, plots, state ----
    public DefaultListModel<SampleBufferElement> bufferFilesModel;
    public DefaultListModel<SampleBufferElement> sampleFilesModel;

    private XYPlot samplesPlot;
    private XYPlot buffersPlot;
    private DataChart samplesSimPlot;
    private DataChart buffersSimPlot;
    private MouseMarker samplesMarker;
    private MouseMarker buffersMarker;

    private final JLabel status;
    private final Color defaultStatusColor;
    private final JProgressBar progressBar;
    private final JCheckBox convertNm1ToCheckBox;

    private final JList<SampleBufferElement> buffersList;
    private final JList<SampleBufferElement> samplesList;
    private Collection samplesCollection;
    private Collection buffersCollection;

    // selection state: one Boolean per frame, kept in step with the list check boxes
    private final ArrayList<Boolean> selectedIndices = new ArrayList<>();
    private final ArrayList<Boolean> selectedBufferIndices = new ArrayList<>();
    private int selectedStart, selectedEnd, selectedBufferStart, selectedBufferEnd;

    // only touched on the Event Dispatch Thread
    private boolean samplesLoading = false;
    private boolean buffersLoading = false;

    private final AtomicBoolean subtracting = new AtomicBoolean(false);
    private volatile Subtraction currentSubtraction;

    private SasObject sasObject = new SasObject();

    private final int cpuCores;

    public Subtract(JLabel status, JProgressBar progressBar, JCheckBox convert) {
        this.status = status;
        this.defaultStatusColor = status.getForeground();
        this.progressBar = progressBar;
        this.convertNm1ToCheckBox = convert;
        this.cpuCores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        sampleFilesModel = new DefaultListModel<>();
        bufferFilesModel = new DefaultListModel<>();
        samplesList = new JList<>(sampleFilesModel);
        buffersList = new JList<>(bufferFilesModel);
        configureList(samplesList);
        configureList(buffersList);
        samples.setViewportView(samplesList);
        buffers.setViewportView(buffersList);

        setupCharts();

        String[] rejections = {"1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5"};
        for (String i : rejections) {
            subtractionCutOff.addItem(i);
        }
        subtractionCutOff.setSelectedIndex(3);
        comboBoxSubtractBins.setSelectedIndex(2);

        sampleStarttextField.setText("-");
        sampleEndtextField.setText("-");
        bufferStartField.setText("-");
        bufferEndField.setText("-");

        installFileDrop(samplesPanel, true);
        installFileDrop(BuffersPanel, false);

        loadSamplesButton.addActionListener(e -> chooseAndLoad(true));
        loadBuffersButton.addActionListener(e -> chooseAndLoad(false));

        clearSamplesButton.addActionListener(e -> {
            if (samplesLoading) { warn("Samples are still loading"); return; }
            clearData(true);
            sasObject = new SasObject();
        });
        clearBuffersButton.addActionListener(e -> {
            if (buffersLoading) { warn("Buffers are still loading"); return; }
            clearData(false);
        });

        outputDirButton.addActionListener(e -> chooseOutputDirectory());

        installRangeField(sampleStarttextField, true, true);
        installRangeField(sampleEndtextField, true, false);
        installRangeField(bufferStartField, false, true);
        installRangeField(bufferEndField, false, false);

        installListToggle(samplesList, true);
        installListToggle(buffersList, false);

        // Average and Median are mutually exclusive; neither ticked = subtract individual frames
        averageCheckBox.addActionListener(e -> {
            if (averageCheckBox.isSelected()) subtractFromMedianCheckBox.setSelected(false);
        });
        subtractFromMedianCheckBox.addActionListener(e -> {
            if (subtractFromMedianCheckBox.isSelected()) averageCheckBox.setSelected(false);
        });

        // recompute the similarity plots and the selection when the q-range changes
        // (only when the range really changed, so moving the focus does not undo manual ticks)
        FocusAdapter qRangeFocus = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                onQRangeEdited();
            }
        };
        qminField.addFocusListener(qRangeFocus);
        qmaxField.addFocusListener(qRangeFocus);
        qminField.addActionListener(e -> onQRangeEdited());
        qmaxField.addActionListener(e -> onQRangeEdited());

        SUBTRACTButton.addActionListener(e -> startSubtraction());
        stopSubtractButton.addActionListener(e -> stopSubtraction());
        stopSubtractButton.setEnabled(false);

        editDetailsButton.addActionListener(e -> {
            if (sasObject == null) {
                sasObject = new SasObject();
            }
            SasObjectForm editForm = new SasObjectForm(sasObject, false);
            editForm.pack();
            editForm.setVisible(true);
        });
    }

    public JPanel getPanel() {
        return contentPane;
    }

    public void printDimensions() {
        System.out.println("ContentPane " + contentPane.getSize().getWidth() + " x " + contentPane.getSize().getHeight());
    }

    // ------------------------------------------------------------------------------------
    // Accessors so samples and buffers share one code path
    // ------------------------------------------------------------------------------------

    private Collection collectionFor(boolean isSample) { return isSample ? samplesCollection : buffersCollection; }
    private DefaultListModel<SampleBufferElement> modelFor(boolean isSample) { return isSample ? sampleFilesModel : bufferFilesModel; }
    private JList<SampleBufferElement> listFor(boolean isSample) { return isSample ? samplesList : buffersList; }
    private ArrayList<Boolean> selectionFor(boolean isSample) { return isSample ? selectedIndices : selectedBufferIndices; }
    private XYPlot plotFor(boolean isSample) { return isSample ? samplesPlot : buffersPlot; }
    private DataChart simPlotFor(boolean isSample) { return isSample ? samplesSimPlot : buffersSimPlot; }
    private MouseMarker markerFor(boolean isSample) { return isSample ? samplesMarker : buffersMarker; }
    private JTextField startFieldFor(boolean isSample) { return isSample ? sampleStarttextField : bufferStartField; }
    private JTextField endFieldFor(boolean isSample) { return isSample ? sampleEndtextField : bufferEndField; }
    private String labelFor(boolean isSample) { return isSample ? "sample" : "buffer"; }

    private boolean isLoading(boolean isSample) { return isSample ? samplesLoading : buffersLoading; }
    private void setLoading(boolean isSample, boolean value) {
        if (isSample) samplesLoading = value; else buffersLoading = value;
    }

    // ------------------------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------------------------

    private void configureList(JList<SampleBufferElement> list) {
        list.setCellRenderer(new SampleBufferListRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    private void setupCharts() {
        samplesCollection = new Collection("samples");
        samplesPlot = new XYPlot(samplesCollection, Scatter.WORKING_DIRECTORY, false);
        samplesPlot.setTitle("Samples");
        samplesPlot.setRangeAxisTitle("Intensity");
        samplesPlot.setDomainAxisTitle("q");
        SampleIofQPanel.add(samplesPlot.getChartPanel());

        buffersCollection = new Collection("buffers");
        buffersPlot = new XYPlot(buffersCollection, Scatter.WORKING_DIRECTORY, true);
        buffersPlot.setTitle("Buffers");
        buffersPlot.setRangeAxisTitle("Intensity");
        buffersPlot.setDomainAxisTitle("q");
        bufferIofQPanel.add(buffersPlot.getChartPanel());

        samplesSimPlot = new DataChart("samples");
        buffersSimPlot = new DataChart("buffers");
        sampleSimPanel.add(samplesSimPlot.getChartPanel());
        BufferSimPanel.add(buffersSimPlot.getChartPanel());

        samplesMarker = new MouseMarker(samplesSimPlot.getChartPanel(), true);
        buffersMarker = new MouseMarker(buffersSimPlot.getChartPanel(), false);
        samplesSimPlot.getChartPanel().addMouseListener(samplesMarker);
        buffersSimPlot.getChartPanel().addMouseListener(buffersMarker);
    }

    private void installFileDrop(JPanel panel, final boolean isSample) {
        new FileDrop(panel, new FileDrop.Listener() {
            @Override
            public void filesDropped(final File[] files) {
                // drops append to what is already loaded; the Clear buttons start over
                loadFiles(files, isSample, buildListFromFileCheckBox.isSelected(), null, true);
            }
        });
    }

    private void installRangeField(final JTextField field, final boolean isSample, final boolean isStart) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                onRangeFieldEdited(isSample, isStart);
            }
        });
        field.addActionListener(e -> onRangeFieldEdited(isSample, isStart)); // Enter key
    }

    /** Clicking a list entry toggles that frame in or out of the subtraction. */
    private void installListToggle(final JList<SampleBufferElement> list, final boolean isSample) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index < 0) return;
                Rectangle bounds = list.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) return; // click below last row

                DefaultListModel<SampleBufferElement> model = modelFor(isSample);
                ArrayList<Boolean> selection = selectionFor(isSample);
                SampleBufferElement item = model.get(index);
                boolean nowSelected = !item.isSelected();
                item.setSelected(nowSelected);
                if (index < selection.size()) {
                    selection.set(index, nowSelected);
                    plotFor(isSample).updateVisibility(selection);
                }
                list.repaint(bounds);
            }
        });
    }

    // ------------------------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------------------------

    private void chooseAndLoad(boolean isSample) {
        if (isLoading(isSample)) {
            warn("Still loading " + labelFor(isSample) + " files, please wait");
            return;
        }

        JFileChooser fc = new JFileChooser(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
        fc.setMultiSelectionEnabled(true);
        int option = fc.showOpenDialog(contentPane);

        if (option != JFileChooser.APPROVE_OPTION) {
            info("Nothing selected");
            return;
        }

        File[] files = fc.getSelectedFiles();
        if (files == null || files.length == 0) {
            info("Nothing selected");
            return;
        }

        int notDat = 0;
        for (File f : files) {
            if (!isDat(f)) notDat++;
        }
        if (notDat > 0) {
            warn(notDat + " of " + files.length + " selected files are not .dat files; please select suitable dat files");
            return;
        }

        info("Selected file(s) :: " + files.length);
        loadFiles(files, isSample, false, fc.getCurrentDirectory(), true); // appends; Clear resets
    }

    /**
     * Single entry point for loading samples or buffers (drop or file chooser).
     * Must be called on the Event Dispatch Thread. With append = false, whatever was loaded
     * before is cleared first; with append = true, the new frames are added after it.
     */
    private void loadFiles(final File[] incoming, final boolean isSample, final boolean buildList,
                           final File rememberDirectory, final boolean append) {
        if (incoming == null || incoming.length == 0) {
            warn("No files received");
            return;
        }
        if (isLoading(isSample)) {
            warn("Still loading " + labelFor(isSample) + " files, please wait");
            return;
        }
        if (subtracting.get()) {
            warn("Wait for the subtraction to finish before loading new files");
            return;
        }

        final Collection collection = collectionFor(isSample);
        final DefaultListModel<SampleBufferElement> model = modelFor(isSample);
        final boolean convert = convertNm1ToCheckBox.isSelected();
        final String workingDir = Scatter.WORKING_DIRECTORY.getWorkingDirectory();

        if (!append) {
            clearData(isSample);
        } else {
            // ReceivedDroppedFiles rebuilds the list from the whole collection (and now clears it
            // itself); emptying only the list here keeps older versions of it from duplicating
            // entries. The loaded data is kept, and ticks are restored in finishLoad.
            model.clear();
        }
        final int framesBefore = collection.getTotalDatasets();
        setLoading(isSample, true);
        info("Loading files, please wait");

        new SwingWorker<File[], Void>() {
            private String baseName = null;

            @Override
            protected File[] doInBackground() throws Exception {
                File[] toLoad = incoming;

                // a single .dat file with "build list from file" ticked -> find the whole series
                if (incoming.length == 1 && buildList && isDat(incoming[0])) {
                    FileListBuilder builder;
                    try {
                        builder = new FileListBuilder(incoming[0], workingDir);
                    } catch (Exception ex) {
                        throw new Exception("Could not build file list from " + incoming[0].getName()
                                + (ex.getMessage() != null ? ": " + ex.getMessage() : ""), ex);
                    }
                    toLoad = builder.getFoundFiles();
                    if (toLoad == null || toLoad.length == 0) {
                        throw new Exception("No matching files found for " + incoming[0].getName());
                    }
                    if (isSample) {
                        baseName = builder.getBase();
                    }
                }

                final int n = toLoad.length;
                SwingUtilities.invokeLater(() -> info("Loading " + n + " files, please wait"));

                ReceivedDroppedFiles rec = new ReceivedDroppedFiles(toLoad, collection, model, status,
                        convert, true, progressBar, workingDir);
                rec.run();
                rec.get();
                return toLoad;
            }

            @Override
            protected void done() {
                setLoading(isSample, false);
                resetProgressBar();
                try {
                    get();
                } catch (InterruptedException | CancellationException ex) {
                    warn("Loading cancelled");
                    return;
                } catch (ExecutionException ex) {
                    Throwable cause = rootCause(ex);
                    cause.printStackTrace();
                    warn("Loading failed: " + describe(cause));
                    return;
                }

                if (baseName != null) {
                    saveAsTextField.setText(baseName);
                }
                if (rememberDirectory != null) {
                    Scatter.WORKING_DIRECTORY.setWorkingDirectory(rememberDirectory.toString());
                    Scatter.updateProp();
                }
                finishLoad(isSample, framesBefore);
            }
        }.execute();
    }

    /**
     * Colours, selection mask, plots and range fields after a load. Runs on the EDT.
     * Frames below framesBefore were already loaded: their ticks are kept as they were,
     * and the newly added frames come in ticked.
     */
    private void finishLoad(boolean isSample, int framesBefore) {
        Collection collection = collectionFor(isSample);
        DefaultListModel<SampleBufferElement> model = modelFor(isSample);
        ArrayList<Boolean> selection = selectionFor(isSample);

        int total = collection.getTotalDatasets();
        if (total == 0) {
            warn("No usable " + labelFor(isSample) + " files were loaded");
            return;
        }

        // keep earlier ticks only if the mask still lines up with what was loaded before
        boolean keepEarlier = framesBefore > 0 && framesBefore <= total && selection.size() == framesBefore;
        if (!keepEarlier) {
            selection.clear();
        }

        float inv = 1.0f / 255.0f;
        int last = Math.max(1, total - 1);
        for (int i = 0; i < total; i++) {
            // re-spread the colour gradient over all frames, old and new
            int r = interpolate(104, 255, i, last);
            int g = interpolate(166, 93, i, last);
            int b = interpolate(255, 135, i, last);
            collection.getDataset(i).setColor(new Color(r * inv, g * inv, b * inv, 0.25f));

            boolean selected = (i < selection.size()) ? selection.get(i) : true;
            if (i >= selection.size()) {
                selection.add(true);
            }
            if (i < model.getSize()) {
                model.get(i).setColor(new Color(r, g, b));
                model.get(i).setSelected(selected);
            }
        }

        JList<SampleBufferElement> list = listFor(isSample);
        list.revalidate();
        list.repaint();

        resize();
        plotFor(isSample).updateChart();

        if (keepEarlier) {
            // extend the shown range to cover the new frames without retouching earlier ticks
            int start = isSample ? selectedStart : selectedBufferStart;
            showRange(isSample, start, total - 1);
            plotFor(isSample).updateVisibility(selection);
        } else {
            applySelectionRange(isSample, 0, total - 1);
        }
        // compare the frames and tick the ones that agree (the user can still change the selection)
        FrameFilter.Result filtered = refreshSimilarityPlot(isSample);
        if (filtered != null) applyFilterSelection(isSample, filtered);
        contentPane.validate();

        int added = total - (keepEarlier ? framesBefore : 0);
        if (model.getSize() != total) {
            warn("Warning: list shows " + model.getSize() + " files but " + total + " " + labelFor(isSample) + " frames were loaded");
        } else {
            info("Added " + added + " " + labelFor(isSample) + " frame" + (added == 1 ? "" : "s")
                    + " (" + total + " total)" + (filtered != null ? ". " + describeFilter(isSample, filtered) : ""));
        }
    }

    /** Updates the start/end fields and marker band only; the selection mask is left alone. */
    private void showRange(boolean isSample, int start, int end) {
        int size = selectionFor(isSample).size();
        if (size == 0) return;
        start = clamp(Math.min(start, end), 0, size - 1);
        end = clamp(Math.max(start, end), 0, size - 1);
        if (isSample) {
            selectedStart = start;
            selectedEnd = end;
        } else {
            selectedBufferStart = start;
            selectedBufferEnd = end;
        }
        startFieldFor(isSample).setText(Integer.toString(start));
        endFieldFor(isSample).setText(Integer.toString(end));
        markerFor(isSample).setRange(start, end);
    }

    private void clearData(boolean isSample) {
        collectionFor(isSample).removeAllDatasets();
        modelFor(isSample).clear();
        simPlotFor(isSample).clear();
        selectionFor(isSample).clear();
        markerFor(isSample).clear();
        plotFor(isSample).clear();
        startFieldFor(isSample).setText("-");
        endFieldFor(isSample).setText("-");
        if (isSample) {
            selectedStart = 0;
            selectedEnd = 0;
        } else {
            selectedBufferStart = 0;
            selectedBufferEnd = 0;
        }
    }

    // ------------------------------------------------------------------------------------
    // Frame selection (marker drag, start/end fields)
    // ------------------------------------------------------------------------------------

    /**
     * Selects frames start..end (inclusive) and deselects the rest. Keeps the selection
     * mask, list check boxes, I(q) plot visibility, text fields and marker in step.
     */
    private void applySelectionRange(boolean isSample, int start, int end) {
        ArrayList<Boolean> selection = selectionFor(isSample);
        int size = selection.size();
        if (size == 0) {
            startFieldFor(isSample).setText("-");
            endFieldFor(isSample).setText("-");
            return;
        }

        if (start > end) { int t = start; start = end; end = t; }
        start = clamp(start, 0, size - 1);
        end = clamp(end, 0, size - 1);

        if (isSample) {
            selectedStart = start;
            selectedEnd = end;
        } else {
            selectedBufferStart = start;
            selectedBufferEnd = end;
        }
        startFieldFor(isSample).setText(Integer.toString(start));
        endFieldFor(isSample).setText(Integer.toString(end));

        DefaultListModel<SampleBufferElement> model = modelFor(isSample);
        for (int i = 0; i < size; i++) {
            boolean in = (i >= start && i <= end);
            selection.set(i, in);
            if (i < model.getSize()) {
                model.get(i).setSelected(in);
            }
        }
        listFor(isSample).repaint();
        plotFor(isSample).updateVisibility(selection);
        markerFor(isSample).setRange(start, end);
    }

    private void onRangeFieldEdited(boolean isSample, boolean editedStart) {
        int size = selectionFor(isSample).size();
        if (size == 0) {
            startFieldFor(isSample).setText("-");
            endFieldFor(isSample).setText("-");
            return;
        }

        int currentStart = isSample ? selectedStart : selectedBufferStart;
        int currentEnd = isSample ? selectedEnd : selectedBufferEnd;
        Integer start = parseInt(startFieldFor(isSample).getText());
        Integer end = parseInt(endFieldFor(isSample).getText());

        // focus moving away without an edit should not undo manual list toggles
        if (start != null && end != null && start == currentStart && end == currentEnd) {
            return;
        }

        String problem = null;
        if (editedStart) {
            if (start == null || start < 0 || start >= size) {
                problem = "Invalid start frame, setting to 0";
                start = 0;
            }
            if (end == null) end = currentEnd;
            if (start > end) {
                problem = "Start is after end, setting start to 0";
                start = 0;
            }
        } else {
            if (end == null || end < 0 || end >= size) {
                problem = "Invalid end frame, setting to " + (size - 1);
                end = size - 1;
            }
            if (start == null) start = currentStart;
            if (end < start) {
                problem = "End is before start, setting end to " + (size - 1);
                end = size - 1;
            }
        }

        applySelectionRange(isSample, start, end);
        if (problem != null) {
            warn(problem);
        }
    }

    // ------------------------------------------------------------------------------------
    // Subtraction
    // ------------------------------------------------------------------------------------

    private void startSubtraction() {
        Double qminValue = parseDouble(qminField.getText());
        Double qmaxValue = parseDouble(qmaxField.getText());
        if (qminValue == null || qmaxValue == null) {
            warn("q-range (qmin, qmax) is not a number");
            return;
        }
        if (qminValue >= qmaxValue) {
            showWarningDialog("q-range (qmin >= qmax) invalid range");
            warn("q-range (qmin >= qmax) invalid range");
            return;
        }

        String name = saveAsTextField.getText().trim();
        if (name.length() < 3) {
            showWarningDialog("Provide a meaningful name");
            return;
        }

        if (samplesLoading || buffersLoading) {
            warn("Wait for files to finish loading");
            return;
        }

        String problem = checkReady(true);
        if (problem == null) problem = checkReady(false);
        if (problem != null) {
            warn(problem);
            return;
        }

        // scaling needs data inside the q range (e.g. WAXS frames with the SAXS default range cannot be scaled)
        if (scaleThenMergeCheckBox.isSelected()) {
            int inside = pointsInRange(samplesCollection, qminValue, qmaxValue);
            if (inside < 10) {
                String msg = String.format(Locale.ROOT, "Scaling needs data in the q range %.3g-%.3g (the samples have %d points there): "
                        + "set qmin/qmax inside the data, e.g. %.3g-%.3g, or untick 'scale then merge'",
                        qminValue, qmaxValue, inside, samplesCollection.getDataset(0).getAllData().getMinX(),
                        samplesCollection.getDataset(0).getAllData().getMaxX());
                showWarningDialog(msg);
                warn(msg);
                return;
            }
        }

        if (!subtracting.compareAndSet(false, true)) {
            warn("A subtraction is already running");
            return;
        }

        for (int i = 0; i < buffersCollection.getTotalDatasets(); i++) {
            buffersCollection.getDataset(i).setInUse(selectedBufferIndices.get(i));
        }
        for (int i = 0; i < samplesCollection.getTotalDatasets(); i++) {
            samplesCollection.getDataset(i).setInUse(selectedIndices.get(i));
        }

        final double qmin = qminValue;
        final double qmax = qmaxValue;
        final boolean mergeByAverage = averageCheckBox.isSelected();
        final boolean singles = !averageCheckBox.isSelected() && !subtractFromMedianCheckBox.isSelected();
        final boolean scaleBefore = scaleThenMergeCheckBox.isSelected();
        final double bins = Double.parseDouble(comboBoxSubtractBins.getSelectedItem().toString());
        final double cutoff = Double.parseDouble(subtractionCutOff.getSelectedItem().toString());
        final String outputName = name;
        final String outputDir = Scatter.WORKING_DIRECTORY.getWorkingDirectory();

        setRunning(true);
        info("Subtracting...");

        new Thread(() -> {
            try {
                Subtraction sub = new Subtraction(buffersCollection, samplesCollection, qmin, qmax,
                        mergeByAverage, singles, scaleBefore, cpuCores, Scatter.useAutoRg, status, progressBar);
                sub.setBinsAndCutoff(bins, cutoff);
                sub.setNameAndDirectory(outputName, outputDir);
                sub.setCollectionToUpdate(Scatter.collectionSelected);

                currentSubtraction = sub;
                sub.run();
                sub.get();
            } catch (CancellationException | InterruptedException ex) {
                SwingUtilities.invokeLater(() -> warn("Subtraction stopped"));
            } catch (ExecutionException | RuntimeException ex) {
                Throwable cause = rootCause(ex);
                cause.printStackTrace();
                SwingUtilities.invokeLater(() -> warn("Subtraction failed: " + describe(cause)));
            } finally {
                currentSubtraction = null;
                subtracting.set(false);
                SwingUtilities.invokeLater(() -> {
                    setRunning(false);
                    resetProgressBar();
                });
            }
        }, "subtraction").start();
    }

    private static int pointsInRange(Collection collection, double qmin, double qmax) {
        if (collection.getTotalDatasets() == 0) return 0;
        XYSeries data = collection.getDataset(0).getAllData();
        int count = 0;
        for (int j = 0; j < data.getItemCount(); j++) {
            double q = data.getX(j).doubleValue();
            if (q >= qmin && q <= qmax) count++;
        }
        return count;
    }

    private void stopSubtraction() {
        Subtraction running = currentSubtraction;
        if (running != null) {
            running.cancel(true);
            info("Stopping subtraction...");
        }
    }

    private void setRunning(boolean running) {
        SUBTRACTButton.setEnabled(!running);
        stopSubtractButton.setEnabled(running);
    }

    /** @return a message describing why we can't subtract yet, or null if ready */
    private String checkReady(boolean isSample) {
        String label = labelFor(isSample);
        int total = collectionFor(isSample).getTotalDatasets();
        ArrayList<Boolean> selection = selectionFor(isSample);
        if (total == 0) {
            return "Load " + label + " files first";
        }
        if (selection.size() != total) {
            return "Selection does not match the loaded " + label + " frames (" + selection.size()
                    + " vs " + total + "), please reload the " + label + "s";
        }
        if (!selection.contains(Boolean.TRUE)) {
            return "No " + label + " frames selected";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------
    // Similarity: every frame against the earliest stable frames (CorMap), see scatter.api.FrameFilter
    // ------------------------------------------------------------------------------------

    /** significance level for "frame differs from the reference" (after Holm correction over the frames) */
    private static final double ALPHA = 0.01;
    /** plot cap for -log10 p */
    private static final double MAX_SCORE = 12;
    private String lastQRange = "";

    /** q range edited: compare the frames again and re-select (only when the range really changed) */
    private void onQRangeEdited() {
        String now = qminField.getText().trim() + "|" + qmaxField.getText().trim();
        if (now.equals(lastQRange)) return;
        StringBuilder msg = new StringBuilder();
        for (boolean isSample : new boolean[]{true, false}) {
            FrameFilter.Result r = refreshSimilarityPlot(isSample);
            if (r == null) continue;
            applyFilterSelection(isSample, r);
            if (msg.length() > 0) msg.append("   ");
            msg.append(describeFilter(isSample, r));
        }
        if (msg.length() > 0) info(msg.toString());
    }

    /** recomputes and redraws the similarity plot; returns the comparison (null if not possible) */
    private FrameFilter.Result refreshSimilarityPlot(boolean isSample) {
        Collection collection = collectionFor(isSample);
        DataChart chart = simPlotFor(isSample);
        if (collection.getTotalDatasets() == 0) {
            chart.clear();
            return null;
        }

        Double qmin = parseDouble(qminField.getText());
        Double qmax = parseDouble(qmaxField.getText());
        if (qmin == null || qmax == null || qmin >= qmax) {
            warn("q-range (qmin, qmax) is invalid; similarity plot not updated");
            return null;
        }
        lastQRange = qminField.getText().trim() + "|" + qmaxField.getText().trim();

        try {
            FrameFilter.Result r = computeSimilarity(collection, qmin, qmax);
            chart.update(r);
            return r;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            warn("Could not compute frame similarity: " + describe(ex));
            return null;
        }
    }

    /**
     * Every frame is compared with a reference made of the earliest run of frames that agree with each
     * other (not frame 0, which may see the meniscus, and not the median, which is the damaged state
     * once most frames are damaged). The comparison is CorMap: the longest run of residuals with the
     * same sign and its probability (no error model needed), Holm-corrected over the frames. The
     * accepted frames are the longest stretch that agrees with the reference, so a disturbed start
     * (meniscus) and a drifting end (radiation damage, aggregation, capillary fouling) are cut as blocks.
     */
    private FrameFilter.Result computeSimilarity(Collection collection, double qmin, double qmax) {
        List<Curve> frames = new ArrayList<>();
        for (int i = 0; i < collection.getTotalDatasets(); i++) {
            version4.Dataset d = collection.getDataset(i);
            XYSeries data = d.getAllData(), err = d.getAllDataError();
            int n = data.getItemCount();
            double[] q = new double[n], intensity = new double[n], sigma = new double[n];
            for (int j = 0; j < n; j++) {
                q[j] = data.getX(j).doubleValue();
                intensity[j] = data.getY(j).doubleValue();
                sigma[j] = (err != null && j < err.getItemCount()) ? err.getY(j).doubleValue() : Double.NaN;
            }
            frames.add(new Curve(d.getFileName(), q, intensity, sigma));
        }
        // the q range may not cover these frames at all (e.g. the SAXS default range with WAXS frames):
        // then compare over the frames' own q range rather than not at all
        double lo = qmin, hi = qmax;
        Curve first = frames.get(0);
        for (Curve c : frames) if (c.size() > first.size()) first = c;     // not an empty or truncated frame
        int inside = 0;
        for (int j = 0; j < first.size(); j++) if (first.q(j) >= qmin && first.q(j) <= qmax) inside++;
        String note = null;
        if (inside < 10 && first.size() >= 10) {
            lo = first.q(0);
            hi = first.q(first.size() - 1);
            note = String.format(Locale.ROOT, "compared over q %.3g-%.3g (no data in the q range %.3g-%.3g)", lo, hi, qmin, qmax);
        }
        FrameFilter.Options o = new FrameFilter.Options().qRange(lo, hi).alpha(ALPHA);
        FrameFilter.Result r = FrameFilter.filter(frames, o);
        rangeNote.put(collection, note);
        return r;
    }

    /** per collection: why the comparison used a different q range (null if it did not) */
    private final java.util.Map<Collection, String> rangeNote = new java.util.IdentityHashMap<>();

    /** Ticks exactly the accepted frames (not necessarily contiguous: single outliers stay unticked). */
    private void applyFilterSelection(boolean isSample, FrameFilter.Result r) {
        int[] accepted = r.getAccepted();
        ArrayList<Boolean> selection = selectionFor(isSample);
        if (accepted.length == 0 || selection.size() != r.getFrames().size()) return;

        boolean[] keep = new boolean[selection.size()];
        for (int k : accepted) keep[k] = true;
        DefaultListModel<SampleBufferElement> model = modelFor(isSample);
        for (int i = 0; i < selection.size(); i++) {
            selection.set(i, keep[i]);
            if (i < model.getSize()) model.get(i).setSelected(keep[i]);
        }
        int start = accepted[0], end = accepted[accepted.length - 1];
        if (isSample) { selectedStart = start; selectedEnd = end; }
        else { selectedBufferStart = start; selectedBufferEnd = end; }
        startFieldFor(isSample).setText(Integer.toString(start));
        endFieldFor(isSample).setText(Integer.toString(end));
        markerFor(isSample).setRange(start, end);
        listFor(isSample).repaint();
        plotFor(isSample).updateVisibility(selection);
    }

    /** status line, frames numbered from 0 as in the list and the start/end fields */
    private String describeFilter(boolean isSample, FrameFilter.Result r) {
        int[] acc = r.getAccepted();
        StringBuilder sb = new StringBuilder(isSample ? "Samples: " : "Buffers: ");
        sb.append(acc.length).append(" of ").append(r.getFrames().size()).append(" frames agree");
        if (acc.length > 0) sb.append(" (").append(acc[0]).append("-").append(acc[acc.length - 1]).append(")");
        int lead = r.count(FrameFilter.Reason.LEADING), trail = r.count(FrameFilter.Reason.TRAILING),
                out = r.count(FrameFilter.Reason.OUTLIER), incomplete = r.count(FrameFilter.Reason.INCOMPLETE);
        if (incomplete > 0) sb.append(", ").append(incomplete).append(" incomplete (too few points)");
        if (lead > 0) sb.append(", ").append(lead).append(" leading");
        if (trail > 0) sb.append(", ").append(trail).append(" trailing");
        if (out > 0) sb.append(", ").append(out).append(" outlier").append(out > 1 ? "s" : "");
        if (r.getAnchorStart() >= 0)
            sb.append("; reference ").append(r.getAnchorStart()).append("-").append(r.getAnchorStart() + r.getAnchorSize() - 1);
        if (r.getMessage() != null) sb.append("; ").append(r.getMessage());
        String note = rangeNote.get(collectionFor(isSample));
        if (note != null) sb.append("; ").append(note);
        return sb.toString();
    }

    /** -log10 of the Holm-adjusted CorMap p-value, capped for plotting; 0 when no comparison was made */
    private static double score(FrameFilter.Frame f) {
        double p = f.adjustedP;
        if (Double.isNaN(p)) return 0;
        return Math.min(MAX_SCORE, -Math.log10(Math.max(p, 1e-300)));
    }

    // ------------------------------------------------------------------------------------
    // Misc helpers
    // ------------------------------------------------------------------------------------

    private void chooseOutputDirectory() {
        File theCWD = new File(Scatter.WORKING_DIRECTORY.getWorkingDirectory());

        JFileChooser chooser = new JFileChooser(theCWD);
        chooser.setDialogTitle("Select Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            File dir = (selected != null && selected.isDirectory()) ? selected : chooser.getCurrentDirectory();
            Scatter.WORKING_DIRECTORY.setWorkingDirectory(dir.toString());
            outputDirLabel.setText(dir.getName() + "/");
            Scatter.updateProp();
        }
    }

    private void showWarningDialog(String message) {
        Toolkit.getDefaultToolkit().beep();
        JOptionPane optionPane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE);
        JDialog dialog = optionPane.createDialog("Warning!");
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);
    }

    private void info(String message) {
        status.setForeground(defaultStatusColor);
        status.setText(message);
    }

    private void warn(String message) {
        status.setForeground(Color.RED);
        status.setText(message);
    }

    private void resetProgressBar() {
        progressBar.setStringPainted(false);
        progressBar.setValue(0);
    }

    private void resize() {
        int height = (int) (contentPane.getHeight() * 0.25);
        if (height <= 0) {
            return; // not laid out yet
        }
        Dimension d = new Dimension(height, height);
        SampleIofQPanel.setPreferredSize(d);
        bufferIofQPanel.setPreferredSize(d);
        sampleSimPanel.setPreferredSize(d);
        BufferSimPanel.setPreferredSize(d);
    }

    private static int interpolate(int startValue, int endValue, int stepNumber, int lastStepNumber) {
        return (endValue - startValue) * stepNumber / lastStepNumber + startValue;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isDat(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && name.substring(dot + 1).toLowerCase(Locale.ROOT).equals("dat");
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static Double parseDouble(String text) {
        try {
            double v = Double.parseDouble(text.trim());
            return (Double.isNaN(v) || Double.isInfinite(v)) ? null : v;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause instanceof ExecutionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String describe(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------------------------
    // Inner classes
    // ------------------------------------------------------------------------------------

    private static final class SampleBufferListRenderer extends JCheckBox implements ListCellRenderer<SampleBufferElement> {
        private final Font font = new Font("SansSerif", Font.PLAIN, 12);

        @Override
        public Component getListCellRendererComponent(JList<? extends SampleBufferElement> list, SampleBufferElement value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setEnabled(list.isEnabled());
            setSelected(value.isSelected());
            setFont(font);
            setBackground(isSelected ? Constants.LightBlueGray : list.getBackground());
            setForeground(value.getColor());
            setText(value.toString());
            return this;
        }
    }

    public class DataChart {
        XYSeriesCollection plotCollection;
        JFreeChart chart;
        ChartPanel chartPanel;
        private final String name;

        double pt = 5;
        float strokeit = 1.1f;

        public DataChart(String name) {
            this.name = name;
            plotCollection = new XYSeriesCollection();
            chart = ChartFactory.createXYLineChart(
                    name,
                    "frame",
                    "-log10 p",
                    plotCollection,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );
            chart.getLegend().setItemFont(new Font("SansSerif", Font.PLAIN, 10));
            chart.getXYPlot().setBackgroundAlpha(0.0f);
            NumberAxis domainAxis = new NumberAxis("frame");
            domainAxis.setTickLabelFont(Constants.FONT_12);
            chart.getXYPlot().setDomainAxis(domainAxis);
            chart.getTitle().setVisible(false);

            NumberAxis rangeAxis = new NumberAxis("difference from reference, -log10 p");
            rangeAxis.setAutoRange(true);
            rangeAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 11));
            chart.getXYPlot().setRangeAxis(rangeAxis);
            // frames above this line differ from the reference (p < ALPHA after correction)
            ValueMarker threshold = new ValueMarker(-Math.log10(ALPHA));
            threshold.setPaint(new Color(200, 40, 40));
            threshold.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
            chart.getXYPlot().addRangeMarker(threshold);

            XYLineAndShapeRenderer baseRenderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
            double basePt = 2;
            double offset = -0.5 * basePt;
            baseRenderer.setBaseLinesVisible(false);
            baseRenderer.setBaseShapesVisible(true);
            baseRenderer.setBaseShape(new Ellipse2D.Double(offset, offset, basePt, basePt));
            baseRenderer.setBaseOutlineStroke(new BasicStroke(1.1f));
            baseRenderer.setBaseShapesFilled(false);

            chartPanel = new ChartPanel(chart) {
                @Override
                public void restoreAutoBounds() {
                    org.jfree.chart.plot.XYPlot plot = getChart().getXYPlot();
                    XYDataset ds = plot.getDataset(0);
                    if (ds == null || ds.getSeriesCount() == 0) {
                        super.restoreAutoBounds();
                        return;
                    }

                    double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
                    double minY = 0, maxY = 2 * -Math.log10(ALPHA);
                    for (int sIdx = 0; sIdx < ds.getSeriesCount(); sIdx++) {
                        for (int i = 0; i < ds.getItemCount(sIdx); i++) {
                            double x = ds.getXValue(sIdx, i);
                            double y = ds.getYValue(sIdx, i);
                            if (!Double.isNaN(x)) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
                            if (!Double.isNaN(y)) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
                        }
                    }

                    plot.getRangeAxis().setAutoRange(false);
                    plot.getRangeAxis().setRange(padded(minY, maxY));
                    plot.getDomainAxis().setRange(padded(minX, maxX));
                }
            };

            chartPanel.getChart().getXYPlot().getRenderer(0).setBaseToolTipGenerator(new XYToolTipGenerator() {
                @Override
                public String generateToolTip(XYDataset xyDataset, int series, int item) {
                    int frame = (int) xyDataset.getXValue(series, item);
                    FrameFilter.Result r = lastResult;
                    if (r == null || frame >= r.getFrames().size()) return "frame_" + frame;
                    FrameFilter.Frame f = r.getFrames().get(frame);
                    return String.format(Locale.ROOT, "frame_%d %s: p = %.2g, longest run %d of %d points",
                            frame, f.reason.toString().toLowerCase(Locale.ROOT), f.adjustedP, f.longestRun, f.points);
                }
            });

            chartPanel.setMouseZoomable(false);
            chartPanel.setHorizontalAxisTrace(true);
            chartPanel.setVerticalAxisTrace(false);
        }

        private Range padded(double lo, double hi) {
            if (Double.isInfinite(lo) || Double.isInfinite(hi)) {
                return new Range(0, 1);
            }
            double span = hi - lo;
            double pad = span > 0 ? span * 0.02 : Math.max(Math.abs(lo) * 0.02, 0.5);
            return new Range(lo - pad, hi + pad);
        }

        public ChartPanel getChartPanel() {
            return chartPanel;
        }

        private FrameFilter.Result lastResult;

        /** one series per verdict: agrees (black), leading (blue), trailing (red), single outlier (orange),
         *  incomplete frame (grey, plotted at 0: it was not compared) */
        public void update(FrameFilter.Result r) {
            lastResult = r;
            plotCollection.removeAllSeries();
            FrameFilter.Reason[] reasons = {FrameFilter.Reason.ACCEPTED, FrameFilter.Reason.LEADING,
                    FrameFilter.Reason.TRAILING, FrameFilter.Reason.OUTLIER, FrameFilter.Reason.DAMAGED,
                    FrameFilter.Reason.INCOMPLETE};
            String[] labels = {"agrees", "leading", "trailing", "outlier", "damaged", "incomplete"};
            Color[] colors = {Color.BLACK, new Color(0, 90, 200), new Color(200, 40, 40), new Color(230, 140, 0),
                    new Color(150, 0, 150), Color.GRAY};
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
            double offset = -0.5 * pt;
            int s = 0;
            for (int k = 0; k < reasons.length; k++) {
                XYSeries series = new XYSeries(labels[k]);
                for (FrameFilter.Frame f : r.getFrames()) if (f.reason == reasons[k]) series.add(f.index, score(f));
                if (series.getItemCount() == 0) continue;
                plotCollection.addSeries(series);
                renderer.setSeriesPaint(s, colors[k]);
                renderer.setSeriesShapesFilled(s, k != 0);
                renderer.setSeriesLinesVisible(s, false);
                renderer.setSeriesShape(s, new Ellipse2D.Double(offset, offset, pt, pt));
                renderer.setSeriesOutlineStroke(s, new BasicStroke(strokeit));
                s++;
            }
            chartPanel.restoreAutoBounds();
        }

        public boolean checkName(String name) {
            return this.name.equals(name);
        }

        public void clear() {
            lastResult = null;
            plotCollection.removeAllSeries();
        }
    }

    /** Click-and-drag on a similarity plot to select a contiguous range of frames. */
    private final class MouseMarker extends MouseAdapter {
        private final ChartPanel panel;
        private final org.jfree.chart.plot.XYPlot plot;
        private final boolean isSample;
        private Marker marker;
        private double pressedAt = Double.NaN;

        MouseMarker(ChartPanel panel, boolean isSample) {
            this.panel = panel;
            this.plot = panel.getChart().getXYPlot();
            this.isSample = isSample;
        }

        /** Mouse x in screen coordinates -> frame number on the domain axis. */
        private double frameAt(MouseEvent e) {
            Rectangle2D dataArea = panel.getScreenDataArea();
            return plot.getDomainAxis().java2DToValue(e.getX(), dataArea, plot.getDomainAxisEdge());
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            pressedAt = frameAt(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || Double.isNaN(pressedAt)) return;
            double releasedAt = frameAt(e);
            int a = (int) Math.round(pressedAt);
            int b = (int) Math.round(releasedAt);
            pressedAt = Double.NaN;
            applySelectionRange(isSample, Math.min(a, b), Math.max(a, b)); // also redraws this marker
        }

        /** Draws the shaded band only; selection state is handled by applySelectionRange. */
        void setRange(int start, int end) {
            removeMarker();
            marker = new IntervalMarker(start - 0.5, end + 0.5);
            marker.setPaint(new Color(0xDD, 0xFF, 0xDD, 0x80));
            marker.setAlpha(0.5f);
            plot.addDomainMarker(marker, Layer.BACKGROUND);
        }

        void clear() {
            removeMarker();
            pressedAt = Double.NaN;
        }

        private void removeMarker() {
            if (marker != null) {
                plot.removeDomainMarker(marker, Layer.BACKGROUND);
                marker = null;
            }
        }
    }
}
