import FileManager.FileListBuilder;
import FileManager.FileObject;
import FileManager.ReceivedDroppedFiles;
import FileManager.WorkingDirectory;
import net.iharder.dnd.FileDrop;
import org.apache.commons.io.FilenameUtils;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.*;
import version4.Collection;
import version4.Constants;
import version4.ReportPDF.SECReport;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYPolygonAnnotation;
import version4.SEC.BaselineCheck;
import version4.SEC.SECBuilder;
import version4.SEC.SECFile;
import version4.SVDCorMap;
import version4.Scaler.ScaleManagerSAS;
import version4.plots.DWSimilarityPlot;
import version4.sasCIF.SasObjectForm;
import version4.tableModels.AnalysisModel;
import version4.tableModels.DataFileElement;
import version4.tableModels.SampleBufferElement;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import static java.nio.file.Files.newInputStream;

public class SECTool extends JDialog {
    private JFreeChart chart;

    private JFreeChart sasChart;
    private JPanel contentPane;
    private JPanel chromatogramPanel;
    private JList samplesList;
    private boolean setBuffer = false;
    public DefaultListModel<SampleBufferElement> sampleFilesModel;

    private ValueMarker selectedDatasetMarker;

    private Color outlineColor = new Color(0, 170, 255, 100);

    private JPanel controlPanel;
    private JPanel filesPanel;
    private JPanel parametersPanel;
    private JPanel subGraphsPanel;
    private JCheckBox convertNm1ToCheckBox;
    private JTextField qminSECField; // for calculating similarity plot only
    private JTextField qmaxSECField; // for calculating similarity plot only
    private JCheckBox averageCheckBox;
    private JComboBox comboBoxSubtractBins;
    private JTextField thresholdField;
    private JLabel rangeSetLabel;
    private JScrollPane dataScrollPane;
    private JButton loadButton;
    private JPanel dropdataPanel_info;
    private JLabel MergeButtonLabel;
    private JPanel MergeButtonPanel;
    private JLabel OutputDirLabel;
    private JPanel OutMergePanel;
    private JLabel ClearBuffersLabel;
    private JLabel ClearSamplesLabel;
    private JPanel ClearBuffersPanel;
    private JPanel ClearSamplesPanel;
    private JLabel DropDataBelowOrLabel;
    private JLabel UpdateChartLabel;
    private JPanel UpdateChartPanel;
    private JPanel TraceReselectPanel;
    private JLabel TraceLabel;
    private JPanel TracePanel;
    private JLabel traceDirlabel;
    private JLabel reslectDirLabel;
    private JButton TRACEButton;
    private JPanel paramsPanel;
    private JButton outputDirButton;
    private JButton MERGEButton;
    private JButton clearSamplesButton;
    private JButton clearBuffersButton;
    private JButton editDetailsButton;
    private JTextField saveAsTextField;
    private JButton SetBufferButton;

    private JPanel selectedRegionPanel;
    private JPanel SASPanel;
    private JLabel selectedBufferIndicesLabel;
    private JLabel framesLabel;
    private JComboBox excludeComboBox;
    private JLabel secFileLabel;
    private JLabel outputDirLabel;
    private JLabel qmaxLabel;
    private JLabel qminLabel;
    //private JButton SVDCORMAPButton;
    private JButton exportTraceButton;
    private ChartPanel signalChartPanel;
    private ChartPanel intensityChartPanel;
    private ChartPanel selectedRegionsChartPanel;

    private SECFile secFile;
    public static int selectedStart, selectedEnd, frameToMergeStart=0, frameToMergeEnd=0;

    private Collection collection;
    private XYSeriesCollection sasPlotCollection;

    private ArrayList<Boolean> selectedIndices;

    private XYSeriesCollection signalPlot;
    private XYSeriesCollection splineChromatogram;
    private XYSeriesCollection rightSignalPlot;
    private XYSeriesCollection selectedRegionCollection;
    private XYSeriesCollection selectedRegionCollectionRight;

    private XYLineAndShapeRenderer backgroundRenderer;
    private XYLineAndShapeRenderer signalRenderer;
    private XYLineAndShapeRenderer sasPlotRenderer;
    private XYLineAndShapeRenderer selectedRegionRenderer;
    private XYLineAndShapeRenderer rightSignalPlotRenderer;
    private XYLineAndShapeRenderer splineRenderer = new XYSplineRenderer();
    private XYLineAndShapeRenderer selectedRegionRightRenderer;
    private XYSplineRenderer splineRend;

    private JLabel status;
    private JProgressBar progressBar;

    private ArrayList<Button> buttons;
    private TextTitle sasChartTitle;

    private CombinedDomainXYPlot combinedPlot;
    private JFreeChart selectedRegionChart;
    private JFreeChart combChart;
    private PaintScaleLegend pslegend;
    private int totalBuffers=0;
    private SortedSet<Integer> selectedBuffers;

    private NumberAxis rangeAxisRight;
    private NumberAxis rangeAxisLeft;
    private XYPlot upperPlot;
    private XYPlot lowerPlot;


    public SECTool(JLabel status, JProgressBar progressBar, JCheckBox convert) {
        setContentPane(contentPane);
        this.status = status;
        this.progressBar = progressBar;
        collection = new Collection("SEC Collection");
        samplesList = new JList();
        convertNm1ToCheckBox = convert;

//        String[] rejections ={"1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5"};
//        for (String i:rejections){
//            subtractionCutOff.addItem(i);
//        }
//
//        subtractionCutOff.setSelectedIndex(3);

        sampleFilesModel = new DefaultListModel<SampleBufferElement>();
        samplesList.setModel(sampleFilesModel);
        samplesList.setCellRenderer(new SampleBufferListRenderer());
        samplesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        samplesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                super.mouseClicked(event);
                JList list = (JList) event.getSource();
                // Get index of clicked item
                int index = list.locationToIndex(event.getPoint());
                // Toggle selected state
                if (index > -1){
                    DataFileElement item = (DataFileElement) list.getModel().getElementAt(index);
                    // Repaint cell
                    item.setSelected(!item.isSelected());
                    list.repaint(list.getCellBounds(index, index));
                    collection.getDataset(index).setInUse(item.isSelected);
                    System.out.println("index " + index + " " + collection.getDataset(index).getInUse());
                }
            }
        });

        dataScrollPane.setViewportView(samplesList);
        selectedIndices=new ArrayList<>();

        Border border = traceDirlabel.getBorder();
        Border margin = new EmptyBorder(0,0,0,6);
        traceDirlabel.setBorder(new CompoundBorder(border, margin));
        reslectDirLabel.setBorder(new CompoundBorder(border, margin));
        qminLabel.setBorder(new CompoundBorder(border, margin));
        qmaxLabel.setBorder(new CompoundBorder(border, margin));

        selectedBuffers = new TreeSet<>();
//        contentPane.setBackground(Main.background);

        selectedDatasetMarker = new ValueMarker(0);

        setupSignalPlot();
        setupIntensityChartPanel();
        setupSelectedRegionPanel();

        new FileDrop(this.getFilesPanel(), new FileDrop.Listener() {

            @Override
            public void filesDropped(final File[] files) {
                openFiles(files, false); // drop events arrive on the EDT
            }
        });

//        this.makeTestPlot();
        setModal(true);

        buttons = new ArrayList<>();

//        OutMergePanel.setBackground(Main.background);
//        OutputDirButtonPanel.setBackground(Main.background);
//        MergeButtonPanel.setBackground(Main.background);
//        ClearBuffersPanel.setBackground(Main.background);
//        ClearSamplesPanel.setBackground(Main.background);

        paramsPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.WHITE));

//        int totalComp = contentPane.getComponents().length;
//        for(int i=0; i<totalComp; i++){
//            contentPane.getComponent(i).setBackground(Main.background);
//        }


//        parametersPanel.setBackground(Main.background);
//        filesPanel.setBackground(Main.background);
//        dropdataPanel_info.setBackground(Main.background);
//        controlPanel.setBackground(Main.background);
//        TraceReselectPanel.setBackground(Main.background);
        editDetailsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (secFile instanceof Object){
                    SasObjectForm editForm = new SasObjectForm(secFile.getSasObject(), true);
                    editForm.pack();
                    editForm.setVisible(true);
                    status.setText("Must UPDATE sec file by clicking TRACE/UPDATE button after editing DETAILS");
                } else {

                }
            }
        });


        TRACEButton.addActionListener(new   ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                traceOrUpdate();
            }
        });


        SetBufferButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                int total = selectedIndices.size();

                if (total > 1){
                    boolean first = false;
                    for(int i=0; i<total; i++){
                        boolean test = selectedIndices.get(i);
                        if (test){
                            selectedBuffers.add(i);
                            first = true;
                        } else if (!test && first){
                            break;
                        }
                    }

                    //update buffers plot
                    XYSeries buff = signalPlot.getSeries(0);
                    buff.clear();
                    for(Integer ind : selectedBuffers){
                        buff.add(signalPlot.getSeries(ind-1).getDataItem(0));
                    }
                    totalBuffers = selectedBuffers.size();
                    selectedBufferIndicesLabel.setText("Total Buffers :: " + totalBuffers);
                    setBuffer = true;

                    TRACEButton.setText("Update");
                }
            }
        });

        clearBuffersButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                totalBuffers = 0;
                selectedBuffers.clear();
                signalPlot.getSeries(0).clear();
            }
        });


        MERGEButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (frameToMergeEnd < frameToMergeStart || frameToMergeStart==0 || frameToMergeEnd==0){
                    status.setText("Select frames before merging using plots on Right");
                    return;
                }

                // create report using SEC File
                SECReport report = new SECReport(frameToMergeStart, frameToMergeEnd, secFile, Scatter.WORKING_DIRECTORY.getWorkingDirectory(), averageCheckBox.isSelected());

                if (averageCheckBox.isSelected() && (frameToMergeEnd- frameToMergeStart) > 2){

                    ScaleManagerSAS peakToMerge = new ScaleManagerSAS(frameToMergeStart, frameToMergeEnd, secFile, progressBar, status, true);
                    peakToMerge.setUpperLowerQLimits(Double.parseDouble(qminSECField.getText()), Double.parseDouble(qmaxSECField.getText()));
                    String nameofnew = (saveAsTextField.getText().length() < 3) ? (secFile.getFilebase() + "_"+ Scatter.collectionSelected.getTotalDatasets()) : saveAsTextField.getText().replaceAll("\\W","_");

                    final SECFile source = secFile;
                    runInBackground("Merging frames " + frameToMergeStart + " to " + frameToMergeEnd + "... please wait", () -> {
                        peakToMerge.run(); // runs doInBackground on this worker thread
                        peakToMerge.get();
                        // create new dataset from averaged and load into Analysis
                        Scatter.collectionSelected.createDataset(peakToMerge.getMerged(), peakToMerge.getMergedErrors(), nameofnew, true);
                        Scatter.collectionSelected.getLast().setSasObject(source.getSasObject());
                        FileObject dataToWrite = new FileObject(new File(source.getParentPath()), Scatter.version);
                        dataToWrite.writeSAXSFile(nameofnew, Scatter.collectionSelected.getLast());

                        report.setScaleFactors(peakToMerge.getScaleFactors());
                        report.setMergedAndMedian(peakToMerge.getMerged(), peakToMerge.getMedian());
                        report.writeReport(nameofnew);
                        return nameofnew;
                    }, name -> status.setText("Merged frames written :: " + name));

                } else if (frameToMergeEnd > frameToMergeStart) { // write out selected frames

                    final SECFile source = secFile;
                    final int first = frameToMergeStart, last = frameToMergeEnd;
                    runInBackground("Writing frames " + first + " to " + last + "...", () -> {
                        ArrayList<Double> qvalues = source.getQvalues();
                        int totalIn = qvalues.size();
                        for (int i = first; i <= last; i++) {
                            ArrayList<Double> iofq = source.getSubtractedFrameAt(i);
                            ArrayList<Double> eofq = source.getUnSubtractedErrorFrameAt(i);
                            XYSeries one = new XYSeries("iofq");
                            XYSeries two = new XYSeries("eofq");
                            for (int q = 0; q < totalIn; q++) {
                                double qvalue = qvalues.get(q);
                                one.add(qvalue, iofq.get(q));
                                two.add(qvalue, eofq.get(q));
                            }
                            FileObject dataToWrite = new FileObject(new File(source.getParentPath()), Scatter.version);
                            String nameofnew = source.getFilebase() + "_frame_" + i;
                            Scatter.collectionSelected.createDataset(one, two, nameofnew, true);
                            dataToWrite.writeSAXSFile(nameofnew, Scatter.collectionSelected.getLast());
                        }
                        report.writeReport("unmerged_sec_report");
                        return last - first + 1;
                    }, count -> status.setText(count + " frames written"));
                }
            }

        });


        clearSamplesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sampleFilesModel.removeAllElements();
                collection.removeAllDatasets();
                secFileLabel.setText("SEC FILE :: ");
                secFileLabel.setForeground(Color.white);
                chart.setNotify(false);
                signalPlot.removeAllSeries();
                clearBaselineCheck(chart.getXYPlot()); // baseline lines, red band and warning title
                chart.setNotify(true);

                totalBuffers = 0;
                selectedBuffers.clear();
                //signalPlot.getSeries(0).clear();

                combinedPlot.setNotify(false);
                combinedPlot.remove(lowerPlot);
                selectedRegionCollection.removeAllSeries();
                selectedRegionCollectionRight.removeAllSeries();
                combinedPlot.setNotify(true);

                selectedIndices.clear();
                sasPlotCollection.removeAllSeries();
                secFile = null;
                frameToMergeStart=0;
                frameToMergeEnd=0;
                rangeSetLabel.setText("- : -");
                framesLabel.setText("TOTAL frames :: - | ");
                selectedBufferIndicesLabel.setText("buffers :: -");
                TRACEButton.setEnabled(true);
                SetBufferButton.setEnabled(true);
                TRACEButton.setText("TRACE");

            }
        });


        outputDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Set working directory
                File theCWD = new File(Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select Directory");

                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION){

                    if (chooser.getSelectedFile().isDirectory()){
                        Scatter.WORKING_DIRECTORY.setWorkingDirectory(chooser.getSelectedFile().toString());
                    } else {
                        Scatter.WORKING_DIRECTORY.setWorkingDirectory(chooser.getCurrentDirectory().toString());
                    }

                    updateOutputDirLabel(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
//                    outputDirLabel.setText(chooser.getSelectedFile().getName()+"/");
                    Scatter.updateProp();
                }
            }
        });

        // select single file to load
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JFileChooser fc = new JFileChooser(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                fc.setMultiSelectionEnabled(true);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION){
                    File[] files = fc.getSelectedFiles();
                    status.setText("Selected file(s) :: " + files.length);
                    openFiles(files, true);

                    Scatter.WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());
                    Scatter.updateProp();

                }

            }
        });

        averageCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (averageCheckBox.isSelected()){
                    MERGEButton.setText("MERGE");
                } else {
                    MERGEButton.setText("SUBTRACT");
                }
            }
        });

        exportTraceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // if file loaded
                if (signalPlot.getSeriesCount() > 1){
                    int total = signalPlot.getSeriesCount();

                    try {
                        String name  = secFile.getFilebase();
                        String outputname = Scatter.WORKING_DIRECTORY.getWorkingDirectory() +"/"+name+"_signal_plot.txt";
                        Path path = Paths.get(outputname);
                        Files.deleteIfExists(path);

                        FileWriter fw = new FileWriter(outputname);
                        BufferedWriter out = new BufferedWriter(fw);

                        out.write(String.format("# %s %n", name));
                        out.write(String.format("# %s %n", "index signal izero rg rg_error"));
                        for (int n=1; n < total; n++) {
                            XYSeries tempSeries = signalPlot.getSeries(n);

                        //    out.write( String.format("%f,%s%n",
                        //            tempSeries.getX(0).doubleValue()*2.1/60.0,
                        //            Constants.Scientific1dot5e2.format(tempSeries.getY(0).doubleValue())
                        //    ));

                            out.write( String.format("%d %s %s %s %s%n", n,
                                    Constants.Scientific1dot5e2.format(tempSeries.getY(0).doubleValue()),
                                            Constants.Scientific1dot5e2.format(secFile.getIzerobyIndex(n-1)),
                                            Constants.Scientific1dot5e2.format(secFile.getRgbyIndex(n-1)),
                                    Constants.Scientific1dot5e2.format(secFile.getRgErrorbyIndex(n-1))
                            ));
                        }
                        out.close();
                        status.setText("File written :: " + Scatter.WORKING_DIRECTORY.getWorkingDirectory() +"/"+name+"_signal_plot.txt");
                        Toolkit.getDefaultToolkit().beep();
                    } catch (IOException ee) {
                        ee.printStackTrace();
                    }
                } else {
                    exportTraceButton.setEnabled(false);
                }
            }
        });
    }

    /*
     * check if file is proper format for SEC, if single dat file, then search directory and build list
     */
    public JPanel getPanel(){ return contentPane;}


    public JPanel getFilesPanel() { return filesPanel;}
    public Collection getCollection() { return collection; }

    public void printDimensions(){
        System.out.println("ContentPane " + contentPane.getSize().getWidth() + " x " + contentPane.getSize().getHeight());
        System.out.println(chromatogramPanel.getSize().getWidth() + " -x- " + chromatogramPanel.getSize().getHeight());
    }

    class SampleBufferListRenderer extends JCheckBox implements ListCellRenderer {

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {

            setEnabled(list.isEnabled());
            setSelected(((SampleBufferElement) value).isSelected());
            setFont( new Font ("CenturyGothic", Font.PLAIN, 11));
            setBackground(list.getBackground());
            setForeground(((SampleBufferElement) value).getColor());
            setText(value.toString());

            if (isSelected) {
                setBackground(Constants.LightBlueGray);
            }

            return this;
        }
    }

    public DefaultListModel<SampleBufferElement> getModel() { return sampleFilesModel; }

    public void updateSignalPlot(){

        XYPlot plot = chart.getXYPlot();
        chart.setNotify(false);
        signalPlot.removeAllSeries();
        // Background is Series '0'
        signalPlot.addSeries(secFile.getBackground()); // background is the first element in signalPlot
        // everything after Series '0' is signgal plot
        XYSeriesCollection tempC = secFile.getSignalCollection();
        for(int i=0; i<tempC.getSeriesCount(); i++){
            signalPlot.addSeries(tempC.getSeries(i));
        }

        NumberAxis rangeAxis = new NumberAxis("Integral Ratio to Background");

        rangeAxis.setRange(signalPlot.getRangeLowerBound(true) - 0.001*signalPlot.getRangeLowerBound(true), signalPlot.getRangeUpperBound(true) + 0.005*signalPlot.getRangeUpperBound(true));
        rangeAxis.setAutoRangeIncludesZero(false);

        plot.setRangeAxis(rangeAxis);
//
//        Color fillColor = new Color(255, 140, 0, 70);
//        Color outlineColor = new Color(255, 127, 80, 100);
        Color fillColor = new Color(0, 170, 255, 70); //cyan

        signalRenderer = (XYLineAndShapeRenderer)plot.getRenderer();
        signalRenderer.setBaseShapesVisible(true);
        signalRenderer.setBaseShape(new Ellipse2D.Double(-0.5*6, -0.5*6.0, 6, 6));

        double pointSize = 5;
        double negativePointSize = -0.5*pointSize;

        for (int i=1; i < signalPlot.getSeriesCount(); i++) {
            // go over each series
            signalRenderer.setSeriesShape(i, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
            signalRenderer.setSeriesLinesVisible(i, false);
            signalRenderer.setSeriesPaint(i, fillColor);
            signalRenderer.setSeriesShapesFilled(i, true);
            signalRenderer.setSeriesOutlinePaint(i, outlineColor);
            signalRenderer.setSeriesOutlineStroke(i, new BasicStroke(2.0f));
        }

//        splineChromatogram.removeAllSeries();
//        splineChromatogram.addSeries(secFile.getSignalSeries());
//
//        splineRenderer.setBaseShapesVisible(false);
//        splineRenderer.setBasePaint(outlineColor);
//
//        chart.getXYPlot().setDataset(1, splineChromatogram);
//        chart.getXYPlot().setRenderer(1, splineRenderer);


        /*
         * adjust background renderer
         */
        pointSize *= 0.5;
        negativePointSize = -0.5*pointSize;
        signalRenderer.setSeriesShape(0, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
        signalRenderer.setSeriesLinesVisible(0, false);
        signalRenderer.setSeriesPaint(0, Color.GRAY);
        signalRenderer.setSeriesShapesFilled(0, true);
        signalRenderer.setSeriesOutlinePaint(0, Color.GRAY);
        signalRenderer.setSeriesOutlineStroke(0, new BasicStroke(1.0f));

        //plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis
        showBaselineCheck(plot);
        chart.setNotify(true);
    }

    private XYLineAnnotation baselineLine;
    private XYLineAnnotation baselinePostLine;
    private XYPolygonAnnotation baselineBand;
    private IntervalMarker baselineTailMarker;
    private TextTitle baselineWarning;

    /** Remove the baseline lines, red band, shading and warning title from the signal plot. */
    private void clearBaselineCheck(XYPlot plot) {
        if (baselineLine != null) plot.removeAnnotation(baselineLine);
        if (baselinePostLine != null) plot.removeAnnotation(baselinePostLine);
        if (baselineBand != null && signalRenderer != null) signalRenderer.removeAnnotation(baselineBand);
        baselinePostLine = null;
        baselineBand = null;
        if (baselineTailMarker != null) plot.removeDomainMarker(baselineTailMarker);
        if (baselineWarning != null) chart.removeSubtitle(baselineWarning);
        baselineLine = null;
        baselineTailMarker = null;
        baselineWarning = null;
    }

    /**
     * Draw the pre-peak baseline across the whole run. If the post-peak baseline does not return to it
     * (e.g. capillary fouling), draw the post-peak level over the shifted frames, fill the displacement
     * between the two lines in red and show a warning above the plot and in the status bar.
     * Otherwise lightly shade the tail that was compared.
     */
    private void showBaselineCheck(XYPlot plot) {
        clearBaselineCheck(plot);

        BaselineCheck.Result check;
        try {
            check = secFile.getBaselineCheck();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            return;
        }
        System.out.println("SEC baseline check :: " + check.summary());
        if (check.status == BaselineCheck.Status.NOT_ASSESSABLE) return;

        boolean flagged = check.isFlagged();
        Color lineColor = flagged ? new Color(220, 60, 30) : new Color(120, 120, 120);
        int lastFrame = secFile.getTotalFrames() - 1;
        baselineLine = new XYLineAnnotation(0, check.baselineAt(0), lastFrame, check.baselineAt(lastFrame),
                new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{6.0f, 4.0f}, 0.0f),
                lineColor);
        baselineLine.setToolTipText("pre-peak baseline " + String.format("%.5f", check.intercept));
        plot.addAnnotation(baselineLine);

        if (flagged) {
            // post-peak level as a second horizontal line over the shifted frames, and the displacement
            // between it and the pre-peak baseline filled in red
            // start where elution has finished and the signal reaches the post-peak level
            int first = (check.plateauFirst >= 0) ? check.plateauFirst : check.tailFirst;
            double base = check.baselineAt(first);
            double level = base + check.offset;
            Color red = new Color(220, 30, 30);

            baselineBand = new XYPolygonAnnotation(new double[]{
                    first, base, check.tailLast, base, check.tailLast, level, first, level},
                    null, null, new Color(220, 30, 30, 70));
            baselineBand.setToolTipText(String.format("displacement %.4f (%.1f%% of peak height)",
                    check.offset, check.offsetPercentOfPeak()));
            signalRenderer.addAnnotation(baselineBand, Layer.BACKGROUND); // behind the data points

            baselinePostLine = new XYLineAnnotation(first, level, check.tailLast, level, new BasicStroke(2.0f), red);
            baselinePostLine.setToolTipText(String.format("post-peak baseline %.5f", level));
            plot.addAnnotation(baselinePostLine);

        } else {
            // light band over the tail that was compared
            baselineTailMarker = new IntervalMarker(check.tailFirst, check.tailLast);
            baselineTailMarker.setPaint(new Color(180, 180, 180));
            baselineTailMarker.setAlpha(0.12f);
            baselineTailMarker.setLabel("baseline OK");
            baselineTailMarker.setLabelAnchor(RectangleAnchor.TOP);
            baselineTailMarker.setLabelTextAnchor(TextAnchor.TOP_CENTER);
            baselineTailMarker.setLabelPaint(lineColor);
            plot.addDomainMarker(baselineTailMarker, Layer.BACKGROUND);
        }

        if (flagged) {
            String shape = (check.shape == BaselineCheck.Shape.LOW_Q)
                    ? "excess scattering at low q - possible capillary fouling or aggregation"
                    : (check.shape == BaselineCheck.Shape.UNIFORM)
                    ? "similar excess at all q - check normalisation or transmission"
                    : "cause not determined";
            String run = check.neverReturns()
                    ? String.format("signal never returns to baseline after the peak (frames %d-%d)", check.runFirst, check.tailLast)
                    : (check.runFirst >= 0 ? check.runDescription() : "");
            baselineWarning = new TextTitle(String.format("Post-peak baseline %s by %.1f%% of peak height%s%n%s",
                    check.offset > 0 ? "elevated" : "lowered", Math.abs(check.offsetPercentOfPeak()),
                    run.isEmpty() ? "" : ": " + run, shape));
            baselineWarning.setPaint(lineColor);
            baselineWarning.setFont(new Font("SansSerif", Font.BOLD, 12));
            chart.addSubtitle(baselineWarning);
            final String summary = check.summary();
            SwingUtilities.invokeLater(() -> status.setText(summary));
        }
    }

    public void setupSignalPlot(){
        signalPlot = new XYSeriesCollection();
        //splineChromatogram = new XYSeriesCollection();
        //rightSignalPlot = new XYSeriesCollection();

        chart = ChartFactory.createXYLineChart(
                "Signal Plot",            // chart title
                "frame",                 // domain axis label
                "signal",                 // range axis label
                signalPlot,                   // data
                PlotOrientation.VERTICAL,
                false,                    // include legend
                true,                     // toolTip
                false
        );
        chart.setTitle("");
        signalChartPanel = new ChartPanel(chart);

        XYPlot plot = chart.getXYPlot();
        //plot.getRangeAxis().setLabelPaint(new Color(51, 153, 255));
        plot.setBackgroundAlpha(0.0f);
        //plot.setOutlineVisible(false);

        plot.getRenderer().setBaseToolTipGenerator(new XYToolTipGenerator() {
            @Override
            public String generateToolTip(XYDataset xyDataset, int i, int i2) {
                return (String) xyDataset.getSeriesKey(i);
            }
        });

        signalChartPanel.setRangeZoomable(false);
        signalChartPanel.setDomainZoomable(false);
        signalChartPanel.setMouseZoomable(false);
        signalChartPanel.setHorizontalAxisTrace(true);

        signalChartPanel.addMouseListener(new MouseMarker(this, signalChartPanel, selectedIndices));

        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(new JMenuItem(new AbstractAction("SVD-CORMAP") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                int total = selectedIndices.size();
                XYSeriesCollection tempCollection = new XYSeriesCollection();

                if (total > 3) {
                    int counter=0;
                    ArrayList<Double> qvalues = secFile.getQvalues();
                    int qtotal = qvalues.size();
                    for (int i = 0; i < total; i++) {
                        if (selectedIndices.get(i)){
                            ArrayList<Double> yvalues = secFile.getUnSubtractedFrameAt(i);
                            //ArrayList<Double> yvalues = secFile.getSubtractedFrameAt(i);
                            tempCollection.addSeries(new XYSeries("Series-"+i));
                            XYSeries tempSeries = tempCollection.getSeries(counter);
                            for(int q=0; q<qtotal; q++){
                                tempSeries.add(qvalues.get(q), yvalues.get(q));
                            }
                            counter++;
                        }
                    }

                    int startingIndex = 0;
                    for (int i = 0; i < total; i++) {
                        if (selectedIndices.get(i)){
                            startingIndex = i;
                            break;
                        }
                    }

                    final int finalStartingIndex = startingIndex;

                    final double svdQmin = Double.parseDouble(qminSECField.getText());
                    final double svdQmax = Double.parseDouble(qmaxSECField.getText());
                    runInBackground("SVD please wait, may take a minute...", () -> {
                        SVDCorMap svd = new SVDCorMap(svdQmin, svdQmax, tempCollection, finalStartingIndex);
                        svd.run(); // compute on this worker thread
                        svd.get();
                        return svd;
                    }, svd -> {
                        svd.createPlot(); // opens a window: EDT only
                        status.setText("SVD finished");
                    });

                } else {
                    status.setText("Too few frames for SVD correlation mapping");
                }
            }
        }));

        popupMenu.add(new JMenuItem(new AbstractAction("Save copy as .sec or .sec.gz...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!(secFile instanceof SECFile)) {
                    status.setText("Load a SEC file first");
                    return;
                }
                final SECFile current = secFile;
                // suggest the other format: compressed copy of a .sec, plain copy of a .sec.gz
                String suggested = current.getFilebase() + (current.isCompressed() ? ".sec" : ".sec.gz");
                JFileChooser chooser = new JFileChooser(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                chooser.setDialogTitle("Save copy (name ending in .gz is compressed)");
                chooser.setSelectedFile(new File(Scatter.WORKING_DIRECTORY.getWorkingDirectory(), suggested));
                if (chooser.showSaveDialog(contentPane) != JFileChooser.APPROVE_OPTION) return;

                final File target = chooser.getSelectedFile();
                final boolean compress = target.getName().toLowerCase().endsWith(".gz");
                if (target.exists() && JOptionPane.showConfirmDialog(contentPane, target.getName() + " exists. Replace it?",
                        "Save copy", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    return;
                }

                runInBackground((compress ? "Compressing to " : "Saving ") + target.getName() + "...", () -> {
                    current.exportCopy(target, compress);
                    return target.length();
                }, bytes -> status.setText("Saved " + target.getName() + String.format(" (%.1f MB)", bytes / 1.0e6)));
            }
        }));

        signalChartPanel.setComponentPopupMenu(popupMenu);

        chromatogramPanel.removeAll();
        chromatogramPanel.add(signalChartPanel);
    }


    public void updateSASPlot(int indexOfFrame){

        selectedDatasetMarker.setValue(indexOfFrame);
        //upperPlot.addDomainMarker(selectedDatasetMarker);

        sasPlotCollection.removeAllSeries();
        XYSeries intensity = new XYSeries("temp-" + indexOfFrame);

        sasChartTitle = sasChart.getTitle();
        //sasChartTitle.setTextAlignment(HorizontalAlignment.LEFT);
        sasChartTitle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        sasChartTitle.setVerticalAlignment(VerticalAlignment.BOTTOM);
        sasChartTitle.setText( "Frame "+indexOfFrame);

        ArrayList<Double> frame = secFile.getSubtractedFrameAt(indexOfFrame);
        ArrayList<Double> qvalues = secFile.getQvalues();

        int counter = 0;
        for(Double inten : frame){
            if (inten > 0){
                intensity.add( qvalues.get(counter).doubleValue(), Math.log10(inten) );
            }
            counter += 1;
        }
        sasPlotCollection.addSeries(intensity);

        final NumberAxis domainAxis = new NumberAxis("");
        domainAxis.setAutoRange(true);
        String quote = "q (\u212B\u207B\u00B9)";
        domainAxis.setLabelFont(new Font("Times", Font.PLAIN, 12));
        domainAxis.setTickLabelFont(Constants.FONT_12);
//        domainAxis.setLabelPaint(Color.white);
        domainAxis.setLabel(quote);

        final NumberAxis rangeAxis = new NumberAxis("log\u2081\u2080 [I(q)]");
        rangeAxis.setRange(sasPlotCollection.getRangeLowerBound(true) - 0.01*sasPlotCollection.getRangeLowerBound(true), sasPlotCollection.getRangeUpperBound(true) + 0.02*sasPlotCollection.getRangeUpperBound(true));
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelsVisible(false);
        rangeAxis.setLabelFont(new Font("Times", Font.PLAIN, 16));
//        rangeAxis.setLabelPaint(Color.white);
        rangeAxis.setAutoRange(true);



        final XYPlot plot = sasChart.getXYPlot();
        plot.setRangeAxis(rangeAxis);
        plot.setDomainAxis(domainAxis);

        plot.setDataset(0, sasPlotCollection);
        plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis
        double negativePointSize, pointSize;

plot.setDomainGridlinesVisible(false);
plot.setRangeGridlinesVisible(false);

        plot.setBackgroundPaint(Color.white);
        plot.setBackgroundAlpha(0.0f);
        plot.setOutlineVisible(false);

        sasPlotRenderer = (XYLineAndShapeRenderer) plot.getRenderer();
        sasPlotRenderer.setBaseShapesVisible(true);
        sasPlotRenderer.setBaseShape(new Ellipse2D.Double(-0.5*2.0, -0.5*2.0, 2, 2));

        pointSize = 4;
        negativePointSize = -0.5*pointSize;
        sasPlotRenderer.setSeriesShape(0, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
        sasPlotRenderer.setSeriesLinesVisible(0, false);

        sasPlotRenderer.setSeriesPaint(0, new Color(70, 130, 180, 70));
        sasPlotRenderer.setSeriesShapesFilled(0, true);

        sasPlotRenderer.setSeriesOutlinePaint(0, new Color(70, 130, 180, 100));
        sasPlotRenderer.setSeriesOutlineStroke(0, new BasicStroke(1.0f));
        selectedRegionsChartPanel.requestFocus();
    }



    /*
     * top right panels with Rg and durbin-watson graph
     */
    public void updateSelectedRegionPanel(){
        combinedPlot.setNotify(false);
        //selectedRegionChart.setNotify(false);
        selectedRegionCollection.removeAllSeries();
        selectedRegionCollectionRight.removeAllSeries();
        XYSeries rgs = new XYSeries("Rgs");
        XYSeries signals = new XYSeries("signals");

//        splineRend = new XYSplineRenderer();
//        splineRend.setBaseShapesVisible(false);

        selectedRegionRenderer = (XYLineAndShapeRenderer) upperPlot.getRenderer(0);
        selectedRegionRenderer.setBaseShapesVisible(true);
        double pointSize = 5.0;
        double negativePointSize = -0.5*pointSize;

        int count=0;
        for(int i=selectedStart; i<=selectedEnd; i++){
            selectedRegionCollection.addSeries(signalPlot.getSeries(i));
            signals.add(signalPlot.getSeries(i).getDataItem(0));
            // check if Rg > 0
            selectedRegionRenderer.setSeriesFillPaint(count, new Color(0, 170, 255, 70));
            selectedRegionRenderer.setSeriesShape(count,new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
            selectedRegionRenderer.setSeriesShapesVisible(count, true);
            selectedRegionRenderer.setSeriesShapesFilled(count, true);
            selectedRegionRenderer.setSeriesPaint(count, new Color(0, 170, 255, 70));
            selectedRegionRenderer.setSeriesOutlinePaint(count, outlineColor);

            double value = secFile.getRgbyIndex(i);
            if (value > 0){
                rgs.add(i, value);
            }
            count++;
        }


        selectedRegionCollectionRight.addSeries(rgs);
//        selectedRegionRightRenderer.setSeriesFillPaint(0, Color.GRAY);
        selectedRegionRightRenderer.setSeriesShape(0,new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
        selectedRegionRightRenderer.setSeriesShapesVisible(0, true);
        selectedRegionRightRenderer.setSeriesShapesFilled(0, true);
        selectedRegionRightRenderer.setBaseShapesVisible(true);
        selectedRegionRightRenderer.setBaseLinesVisible(false);
//        selectedRegionRightRenderer.setBasePaint(Color.GRAY);
//        selectedRegionRightRenderer.setBaseFillPaint(Color.GRAY);

        rangeAxisRight.setRange(selectedRegionCollectionRight.getRangeLowerBound(true), selectedRegionCollectionRight.getRangeUpperBound(true) + 0.02 * selectedRegionCollectionRight.getRangeUpperBound(true));

        NumberAxis rangeAxis = new NumberAxis("signal");
        rangeAxis.setTickLabelsVisible(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRange(true);
        rangeAxis.setLabelFont(new Font("Times", Font.BOLD, 16));
        rangeAxis.setLabelPaint(new Color(51, 153, 255));
        rangeAxis.setRange(selectedRegionCollection.getRangeLowerBound(true) - 0.001*selectedRegionCollection.getRangeLowerBound(true), selectedRegionCollection.getRangeUpperBound(true) + 0.005*selectedRegionCollection.getRangeUpperBound(true));
        selectedRegionChart.getXYPlot().setRangeAxis(0, rangeAxis);
        NumberAxis domainAxis = new NumberAxis("frame");
        domainAxis.setRange(selectedStart, selectedEnd);
        domainAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRange(true);
        combinedPlot.setDomainAxis(domainAxis);

        // Durbin-Watson similarity of the selected frames, computed off the EDT; only the latest request is shown.
        // Moving the frame marker (arrow keys) keeps the same region, so it is only recomputed when that changes.
        final int dwStart = selectedStart, dwEnd = selectedEnd;
        final double dwQmin = Double.parseDouble(qminSECField.getText());
        final double dwQmax = Double.parseDouble(qmaxSECField.getText());
        final SECFile dwFile = secFile;
        String dwKey = dwStart + ":" + dwEnd + ":" + dwQmin + ":" + dwQmax + ":" + System.identityHashCode(dwFile);
        if (dwKey.equals(dwLastKey)) {
            selectedRegionsChartPanel.requestFocus();
            combinedPlot.setNotify(true);
            return;
        }
        dwLastKey = dwKey;
        final int request = ++dwRequest;
        new SwingWorker<DWSimilarityPlot, Void>() {
            @Override
            protected DWSimilarityPlot doInBackground() throws Exception {
                DWSimilarityPlot dw = new DWSimilarityPlot(dwStart, dwEnd, dwQmin, dwQmax, dwFile, status, progressBar);
                dw.run();
                dw.get();
                return dw;
            }

            @Override
            protected void done() {
                try {
                    DWSimilarityPlot dw = get();
                    if (request != dwRequest) return; // a newer selection is on its way
                    combinedPlot.remove(lowerPlot);
                    lowerPlot = dw.createPlot();
                    combinedPlot.add(lowerPlot, 1);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    ex.getCause().printStackTrace();
                    status.setText("Similarity plot failed :: " + ex.getCause().getMessage());
                }
            }
        }.execute();

//        selectedRegionsChartPanel.getChart().getXYPlot().getRenderer(0).setBaseToolTipGenerator(new XYToolTipGenerator() {
//            @Override
//            public String generateToolTip(XYDataset xyDataset, int i, int i2) {
//                return (String) xyDataset.getSeriesKey(i);
//            }
//        });

//        rangeSetLabel.setText(selectedStart + " : " + selectedEnd);
        selectedRegionsChartPanel.requestFocus();
//        ArrowKeyListener keyl = (ArrowKeyListener) selectedRegionsChartPanel.getKeyListeners()[0];
//        keyl.setTotalInCollection(secFile.getTotalFrames());
//        intensityChartPanel.requestFocus(true);
        combinedPlot.setNotify(true);
    }


    public void setupSelectedRegionPanel(){
        // initialize collection
        selectedRegionCollection = new XYSeriesCollection();
        selectedRegionCollectionRight = new XYSeriesCollection();

        selectedRegionChart = ChartFactory.createXYLineChart(
                "",            // chart title
                "",                 // domain axis label
                "",                 // range axis label
                selectedRegionCollection,                   // data
                PlotOrientation.VERTICAL,
                false,                    // include legend
                true,                     // toolTip
                false
        );

        // prepare the chart
        //final NumberAxis domainAxis = new NumberAxis("frame");
        String quoteR = "Rg Å";
        rangeAxisRight = new NumberAxis("Rg");
        rangeAxisRight.setLabel(quoteR);
        rangeAxisRight.setLabelFont(new Font("Times", Font.BOLD, 16));
        rangeAxisRight.setLabelPaint(Color.red);
        rangeAxisRight.setAutoRange(true);
        rangeAxisRight.setAutoRangeIncludesZero(false);
        rangeAxisRight.setAutoRangeStickyZero(false);

        selectedRegionRightRenderer = new XYLineAndShapeRenderer();
        splineRend = new XYSplineRenderer();
        splineRend.setBaseShapesVisible(false);

        upperPlot = selectedRegionChart.getXYPlot();
        upperPlot.setDomainCrosshairVisible(true);
        upperPlot.setRangeCrosshairVisible(true);
        upperPlot.setDomainCrosshairLockedOnData(true);
        upperPlot.setBackgroundAlpha(0.0f);
        upperPlot.setOutlineVisible(false);

        upperPlot.setDataset(0, selectedRegionCollection);
        upperPlot.setRenderer(0, splineRend);

        upperPlot.setDataset(1, selectedRegionCollectionRight);
        upperPlot.setRangeAxis(1, rangeAxisRight);
        upperPlot.setRenderer(1, selectedRegionRightRenderer);       //render as a line

        upperPlot.mapDatasetToRangeAxis(0, 0); //1st dataset to 2nd y-axis
        upperPlot.mapDatasetToRangeAxis(1, 1); //2nd dataset to 2nd y-axis
        upperPlot.setDomainCrosshairVisible(false);
        upperPlot.setRangeCrosshairVisible(false);

        upperPlot.addDomainMarker(selectedDatasetMarker);
        /*
         * calculate heat plot within selected region
         */
        combinedPlot = new CombinedDomainXYPlot(new NumberAxis(""));
       // combinedPlot.setDomainAxis(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.add(upperPlot, 1);
        lowerPlot = new XYPlot();
        combinedPlot.add(lowerPlot,1);

        combinedPlot.setOrientation(PlotOrientation.VERTICAL);

        combChart = new JFreeChart("", JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);
        combChart.removeLegend();
        combChart.setBackgroundPaint(Color.WHITE);

        ChartFrame chartframe = new ChartFrame("", combChart); // chartpanel exists in frame
        //selectedRegionPanel
        selectedRegionsChartPanel = chartframe.getChartPanel();

        subGraphsPanel.setFocusable(true);
        selectedRegionPanel.setFocusable(true);
        selectedRegionsChartPanel.setFocusable(true);

       // subGraphsPanel.addKeyListener(new ArrowKeyListener(selectedDatasetMarker));
       // selectedRegionPanel.addKeyListener(new ArrowKeyListener(selectedDatasetMarker));
        selectedRegionsChartPanel.addKeyListener(new ArrowKeyListener(selectedDatasetMarker));

        selectedRegionsChartPanel.addChartMouseListener(new ChartMouseListener() {

            @Override
            public void chartMouseClicked(ChartMouseEvent chartMouseEvent) {
                ChartEntity ce = chartMouseEvent.getEntity();
                if (ce instanceof XYItemEntity){
                    XYItemEntity e = (XYItemEntity) ce;
                    XYDataset d = e.getDataset();
                    int series = e.getSeriesIndex();
                    int index = e.getItem();

                    Number trueStart = d.getX(series, index);
                    Number trueEnd = d.getY(series,index);

                    if (trueEnd.doubleValue() > trueStart.doubleValue()){
                        //chartMouseEvent.getChart().setTitle("Selected frames : " + Integer.toString((int)d.getX(series, index).intValue()) + " to " + Integer.toString((int)d.getY(series, index).intValue()));
                        // update markers on upperplot
                        updateMarkers(trueStart.doubleValue(), trueEnd.doubleValue());
                    }
                }
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent chartMouseEvent) {

            }
        });

        selectedRegionsChartPanel.setDisplayToolTips(true);
//        selectedRegionsChartPanel.setMouseZoomable(false);
        selectedRegionsChartPanel.setHorizontalAxisTrace(true);
        selectedRegionsChartPanel.setVerticalAxisTrace(true);

        selectedRegionPanel.add(selectedRegionsChartPanel);
    }


    public void setupIntensityChartPanel(){

        sasPlotCollection = new XYSeriesCollection();

        sasChart = ChartFactory.createXYLineChart(
                "",            // chart title
                "",                 // domain axis label
                "",                 // range axis label
                sasPlotCollection,                   // data
                PlotOrientation.VERTICAL,
                false,                    // include legend
                true,                     // toolTip
                false
        );

        intensityChartPanel = new ChartPanel(sasChart){
            @Override
            public void restoreAutoBounds(){
                super.restoreAutoDomainBounds();
                super.restoreAutoRangeBounds();
                super.getChart().getXYPlot().getRangeAxis().setAutoRange(false);

                double min = 1000;
                double max = -10;
                double tempYMin;

                int totalItems = super.getChart().getXYPlot().getDataset(0).getItemCount(0);

                double minX = super.getChart().getXYPlot().getDataset(0).getXValue(0,0);
                double maxX = super.getChart().getXYPlot().getDataset(0).getXValue(0,totalItems-1);


                for(int i=0; i<totalItems; i++){
                    tempYMin = super.getChart().getXYPlot().getDataset(0).getYValue(0, i);
                    if (tempYMin < min){
                        min = tempYMin;
                    }
                    if (tempYMin > max){
                        max = tempYMin;
                    }
                }

                super.getChart().getXYPlot().getRangeAxis().setRange(min-Math.abs(min*0.02), max+Math.abs(0.02*max));
                super.getChart().getXYPlot().getDomainAxis().setRange(minX-Math.abs(minX*0.02), maxX+Math.abs(0.02*maxX));
            }
        };

        sasChartTitle = sasChart.getTitle();
        sasChartTitle.setFont(new Font("SansSerif", Font.PLAIN, 12));

        sasChart.getTitle().setVerticalAlignment(VerticalAlignment.BOTTOM);

        intensityChartPanel.setHorizontalAxisTrace(false);
        intensityChartPanel.setVerticalAxisTrace(false);

        final NumberAxis domainAxis = new NumberAxis("");
        domainAxis.setAutoRange(true);

        final NumberAxis rangeAxis = new NumberAxis("");
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRange(true);

        XYPlot plot = sasChart.getXYPlot();
        plot.setRangeAxis(rangeAxis);
        plot.setDomainAxis(domainAxis);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        intensityChartPanel.setDisplayToolTips(true);
        intensityChartPanel.getChart().getXYPlot().getRenderer(0).setBaseToolTipGenerator(new XYToolTipGenerator() {
            @Override
            public String generateToolTip(XYDataset xyDataset, int i, int i2) {
                return (String) xyDataset.getSeriesKey(i);
            }
        });

        intensityChartPanel.setMouseZoomable(true);
        intensityChartPanel.setHorizontalAxisTrace(false);

        SASPanel.add(intensityChartPanel);
    }


    private final static class MouseMarker extends MouseAdapter {
        private Marker marker;
        private Double markerStart = Double.NaN;
        private Double markerEnd = Double.NaN;
        private final XYPlot plot;
        private final JFreeChart chart;
        private  final ChartPanel panel;
        private final SECTool sectool;
        private ArrayList<Boolean> samplesList;


        public MouseMarker(SECTool sectool, ChartPanel panel, ArrayList<Boolean> samplesList) {
            this.sectool = sectool;
            this.samplesList = samplesList;
            this.panel = panel;
            this.chart = panel.getChart();
            this.plot = (XYPlot) chart.getPlot();
        }

        private void updateMarker(){
            if (marker != null){
                plot.removeDomainMarker(marker, Layer.BACKGROUND);
            }

            if (!( markerStart.isNaN() && markerEnd.isNaN())){
                if ( markerEnd > markerStart){
                    marker = new IntervalMarker(markerStart, markerEnd);
                    marker.setPaint(new Color(0xDD, 0xFF, 0xDD, 0x80));
                    marker.setAlpha(0.5f);
                    plot.addDomainMarker(marker,Layer.BACKGROUND);

                    selectedStart = markerStart.intValue();
                    selectedEnd = markerEnd.intValue();
                    sectool.updateSelectedRegionPanel();

                    int frameit = selectedStart + (int)Math.ceil((selectedEnd - selectedStart)*0.5) + 1;
                    sectool.updateSASPlot(frameit);

                }
            }
        }

        private Double getPosition(MouseEvent e){
            Point2D p = panel.translateScreenToJava2D( e.getPoint());
            Rectangle2D plotArea = panel.getScreenDataArea();
            XYPlot plot = (XYPlot) chart.getPlot();
            // int mouseX = e.getX();
            // int onscreen = e.getXOnScreen();
            // System.out.println("x = " + mouseX + " onscreen " + onscreen);
            return plot.getDomainAxis().java2DToValue(p.getX(), plotArea, plot.getDomainAxisEdge());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            markerEnd = getPosition(e);

            int size = samplesList.size();
            int trueStart = markerStart.intValue();
            int trueEnd = markerEnd.intValue();

            // set everything within range to true, everything else false
            for(int i=0; i<size; i++){
                // sampleFilesModel.get(i).isSelected()
                if ((i < trueStart) || (i > trueEnd)){
                    samplesList.set(i, false);
                } else {
                    samplesList.set(i, true);
                }
            }
            updateMarker();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            markerStart = getPosition(e);
            // if key pressed
        }
    }



    public void updateMarkers(Double startValue, Double endValue){

        upperPlot.clearDomainMarkers();
        Marker marker = new IntervalMarker(startValue, endValue);
        marker.setPaint(new Color(0xF4, 0xBB, 0xFF, 0x80));
        marker.setAlpha(0.5f);
        upperPlot.addDomainMarker(marker,Layer.BACKGROUND);
        upperPlot.addDomainMarker(selectedDatasetMarker);
        frameToMergeStart = startValue.intValue();
        frameToMergeEnd = endValue.intValue();
        rangeSetLabel.setText("SELECTED :: "+startValue.intValue() + " : " + endValue.intValue());
        updateSASPlot( (int)(startValue + (int)Math.ceil(endValue-startValue)/2) + 1 );
    }





    private class ArrowKeyListener implements KeyListener {

        private ValueMarker mousemarker;

        public ArrowKeyListener(ValueMarker signalPlotMarker){
            this.mousemarker = signalPlotMarker;
        }

        public void keyPressed(KeyEvent event){

            int cvalue = (int)mousemarker.getValue();
            if (event.getKeyCode() == KeyEvent.VK_LEFT && (cvalue-1) > 0) {
                    mousemarker.setValue(cvalue -1);
                    updateSASPlot(cvalue-1);
            } else if (event.getKeyCode() == KeyEvent.VK_RIGHT && (cvalue+1) < secFile.getTotalFrames() ) {
                    mousemarker.setValue(cvalue+1);
                    updateSASPlot(cvalue+1);
            }

        }

        public void keyReleased(KeyEvent e) {
           // System.out.println("released");
        }

        public void keyTyped(KeyEvent e) {
           // System.out.println("typed");
        }
    }

    private void updateOutputDirLabel(String text){
        if (text.length() > 23){
            int length = text.length();
            outputDirLabel.setText("/..."+text.substring(length - 23,length));
        } else {
            outputDirLabel.setText(text);
        }
    }

    // ------------------------------------------------------------------ background work

    private int dwRequest = 0;
    private String dwLastKey = null; // region/q-range/file of the last similarity plot request

    /**
     * Run {@code work} on a background thread while the buttons are disabled, then {@code onSuccess} with its result
     * on the Event Dispatch Thread. Errors are shown in the status bar. Call from the EDT.
     * Background code must not touch widgets directly (use {@link version4.Ui} or a ProgressListener).
     */
    private <T> void runInBackground(String message, java.util.concurrent.Callable<T> work, java.util.function.Consumer<T> onSuccess) {
        disableAllButtons();
        if (message != null) status.setText(message);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = (ex.getCause() == null) ? ex : ex.getCause();
                    cause.printStackTrace();
                    status.setText("Error :: " + cause.getMessage());
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    status.setText("Error :: " + ex.getMessage());
                } finally {
                    enableAllButtons();
                    progressBar.setIndeterminate(false);
                    progressBar.setStringPainted(false);
                    progressBar.setValue(0);
                }
            }
        }.execute();
    }

    /**
     * Open dropped or chosen files: a single .sec/.sec.gz, a single .dat (the rest of the series is found in its
     * directory) or several .dat files. Call from the EDT.
     *
     * @param nameFromFirstFile for several files, use the first file's name as the "save as" name
     */
    private void openFiles(File[] files, boolean nameFromFirstFile) {
        if (files == null || files.length == 0) return;

        File first = files[0];
        String[] parts = first.getName().split("\\.(?=[^\\.]+$)");
        String ext = (parts.length > 1) ? parts[1].toLowerCase() : "";
        String workingDir = Scatter.WORKING_DIRECTORY.getWorkingDirectory();
        boolean convert = convertNm1ToCheckBox.isSelected();

        if (files.length == 1 && SECFile.isSecFileName(first.getName())) {
            sampleFilesModel.removeAllElements();
            collection.removeAllDatasets();
            runInBackground("Please wait... loading " + first.getName(), () -> new SECFile(first), loaded -> {
                Scatter.WORKING_DIRECTORY.setWorkingDirectory(loaded.getParentPath());
                updateOutputDirLabel(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                showSecFile(loaded);
                status.setText("");
            });
            return;
        }

        if (files.length == 1 && !ext.equals("dat")) {
            status.setText("Drop a .sec, .sec.gz or .dat file");
            return;
        }
        if (files.length > 1 && nameFromFirstFile && !ext.equals("dat")) {
            status.setText("Select .dat files");
            return;
        }

        sampleFilesModel.removeAllElements();
        collection.removeAllDatasets();
        exportTraceButton.setEnabled(false);
        final String suggestedName = (files.length > 1 && nameFromFirstFile) ? FilenameUtils.removeExtension(first.getName()) : null;

        runInBackground("Loading files, please wait", () -> {
            File[] toLoad = files;
            String base = suggestedName;
            if (files.length == 1) { // single .dat: build the series from its directory
                FileListBuilder builder = new FileListBuilder(first, workingDir);
                toLoad = builder.getFoundFiles();
                base = builder.getBase();
                version4.Ui.setText(status, "Loading " + toLoad.length + " files, please wait");
            }
            ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(toLoad, collection, sampleFilesModel, status,
                    convert, true, progressBar, workingDir);
            rec1.run(); // loads on this worker thread; the list model is updated on the EDT
            rec1.get();
            return base;
        }, base -> {
            if (base != null) saveAsTextField.setText(base);
            status.setText("Click TRACE to extract chromatogram");
            samplesList.revalidate();
            samplesList.repaint();
        });
    }

    /** Show a newly opened or rebuilt SEC file. Call from the EDT. */
    private void showSecFile(SECFile loaded) {
        if (secFile != null && secFile != loaded) secFile.closeFile();
        secFile = loaded;
        updateSignalPlot();
        selectedIndices.clear();
        for (int i = 0; i < secFile.getTotalFrames(); i++) {
            selectedIndices.add(false);
        }
        framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames());
        selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());
        secFileLabel.setText("SEC FILE :: " + secFile.getFilename());
        secFileLabel.setForeground(Color.cyan);
        thresholdField.setText(String.format("%.3f", secFile.getThreshold()));
        TRACEButton.setText("UPDATE");
        exportTraceButton.setEnabled(true);
    }

    /**
     * TRACE/UPDATE button: build a new SEC file from the loaded frames, rebuild it with a new buffer selection,
     * recalculate Rg/I(0) for a new threshold, or save edited details. Call from the EDT.
     */
    private void traceOrUpdate() {
        if (saveAsTextField.getText().length() < 3 && !(secFile instanceof SECFile)) {
            Toolkit.getDefaultToolkit().beep();
            JOptionPane optionPane = new JOptionPane("Too short, provide a meaningful name", JOptionPane.WARNING_MESSAGE);
            JDialog dialog = optionPane.createDialog("Warning!");
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            return;
        }

        // read every input on the EDT before handing work to the background
        final String workingDir = Scatter.WORKING_DIRECTORY.getWorkingDirectory();
        final double threshold;
        final int excludePoints;
        try {
            threshold = Double.parseDouble(thresholdField.getText());
            excludePoints = Integer.parseInt(excludeComboBox.getSelectedItem().toString());
        } catch (NumberFormatException ex) {
            status.setText("Threshold must be a number");
            return;
        }

        if (collection.getTotalDatasets() > 2 && !(secFile instanceof SECFile)) { // make new SEC file
            for (int i = collection.getTotalDatasets() - 1; i >= 0; i--) { // remove unused frames
                if (!collection.getDataset(i).getInUse()) {
                    collection.removeDataset(i);
                    sampleFilesModel.remove(i);
                }
            }
            final String name = saveAsTextField.getText();
            runInBackground("Building SEC file... please wait", () -> {
                SECBuilder secBuilder = new SECBuilder(collection, status, progressBar, name, workingDir, threshold);
                secBuilder.setExcludePoints(excludePoints);
                secBuilder.run();
                secBuilder.get();
                return new SECFile(new File(secBuilder.getOutputname()));
            }, built -> {
                showSecFile(built);
                sampleFilesModel.removeAllElements();
                collection.removeAllDatasets();
                status.setText("Using *.sec file now clearing samples list");
            });

        } else if (setBuffer) { // new buffer region: rebuild the entire file
            if (selectedBuffers.size() > 1) {
                collection.removeAllDatasets();
                final SECFile old = secFile;
                final TreeSet<Integer> buffers = new TreeSet<>(selectedBuffers);
                runInBackground("Rebuilding SEC file with new buffer frames... please wait", () -> {
                    SECBuilder secBuilder = new SECBuilder(old, buffers, status, progressBar, workingDir, threshold);
                    secBuilder.setExcludePoints(excludePoints);
                    old.closeFile();
                    secBuilder.run();
                    secBuilder.get();
                    version4.Ui.setText(status, "Loading updated SEC file");
                    return new SECFile(new File(secBuilder.getOutputname()));
                }, rebuilt -> {
                    showSecFile(rebuilt);
                    status.setText("Finished loading SEC file");
                });
            }

        } else if (secFile instanceof SECFile) {
            final SECFile old = secFile;
            final String secfilename = old.getAbsolutePath();

            if (Math.abs(threshold - old.getThreshold()) > 0.001) { // threshold changed: recalculate Rg and I(0)
                runInBackground("Recalculating Rg and I(0)... please wait", () -> {
                    SECBuilder secBuilder = new SECBuilder(old, status, progressBar, workingDir, threshold, excludePoints);
                    old.closeFile();
                    secBuilder.run();
                    secBuilder.get();
                    return new SECFile(new File(secfilename));
                }, updated -> {
                    secFile = updated;
                    if (selectedEnd > selectedStart) {
                        updateSelectedRegionPanel();
                    }
                    framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames());
                    selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());
                    status.setText("Finished loading SEC file");
                });

            } else { // only details edited: write the header line (in place when it fits)
                runInBackground("Saving details...", () -> {
                    old.updateHeader();
                    old.closeFile();
                    return new SECFile(new File(secfilename));
                }, updated -> {
                    secFile = updated;
                    status.setText("Details saved");
                });
            }
        }

        exportTraceButton.setEnabled(secFile instanceof SECFile);
        setBuffer = false;
    }

    private void disableAllButtons(){
        MERGEButton.setEnabled(false);
        editDetailsButton.setEnabled(false);
        clearBuffersButton.setEnabled(false);
        clearSamplesButton.setEnabled(false);
        SetBufferButton.setEnabled(false);
        TRACEButton.setEnabled(false);
        outputDirButton.setEnabled(false);
    }

    private void enableAllButtons(){
        MERGEButton.setEnabled(true);
        editDetailsButton.setEnabled(true);
        clearBuffersButton.setEnabled(true);
        clearSamplesButton.setEnabled(true);
        SetBufferButton.setEnabled(true);
        TRACEButton.setEnabled(true);
        outputDirButton.setEnabled(true);
    }
}
