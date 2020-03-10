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
import version4.SEC.SECBuilder;
import version4.SEC.SECFile;
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
    private ChartPanel signalChartPanel;
    private ChartPanel intensityChartPanel;
    private ChartPanel selectedRegionsChartPanel;

    private SECFile secFile;
    public static int selectedStart, selectedEnd, frameToMergeStart, frameToMergeEnd;

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


                new Thread() {
                    public void run() {

                        try {
                            ReceivedDroppedFiles rec1 = null;
                            sampleFilesModel.removeAllElements();
                            collection.removeAllDatasets();

                            if (files.length == 1) { // auto build list from file if list == 1 and extension is not .sec

                                String[] filename = files[0].getName().split("\\.(?=[^\\.]+$)");
                                String ext = filename[1];

                                if (ext.equals("sec")){
                                    /*
                                     * validate file
                                     * grab JSON
                                     * load signals and associated graphs
                                     */
                                    try {
                                        secFile = new SECFile(files[0]);
                                        status.setText("Please wait... loading " + secFile.getFilename());
                                        secFileLabel.setText("Using SEC FILE :: " + secFile.getFilename());
                                        Scatter.WORKING_DIRECTORY.setWorkingDirectory(secFile.getParentPath());
                                        updateOutputDirLabel(Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                                        updateSignalPlot();
                                        selectedIndices.clear();

                                        for(int i=0;i<secFile.getTotalFrames(); i++){
                                            selectedIndices.add(false);
                                        }

                                        framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames() );
                                        selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());

                                        secFileLabel.setText("SEC FILE :: " + secFile.getFilename());
                                        secFileLabel.setForeground(Color.cyan);
                                        TRACEButton.setText("UPDATE");
                                        status.setText("");
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                } else if (ext.equals("dat")) { // dropping single file that ends in dat

                                    try {
                                        FileListBuilder builder = new FileListBuilder(files[0], Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                                        status.setText("Loading " + builder.getFoundFiles().length + " files, please wait");
                                        rec1 = new ReceivedDroppedFiles(builder.getFoundFiles(),
                                                collection,
                                                sampleFilesModel,
                                                status,
                                                convertNm1ToCheckBox.isSelected(),
                                                true,
                                                progressBar,
                                                Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                                        saveAsTextField.setText(builder.getBase());
                                    } catch (Exception e) {
                                        //programInstance.status.setText(" DROP only one file please");

                                    }
                                }

                            } else { // multiple files dropped
                                rec1 = new ReceivedDroppedFiles(files, collection, sampleFilesModel, status, convertNm1ToCheckBox.isSelected(), true, progressBar, Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                            }

                            if (rec1 instanceof Object){
                                rec1.run();
                                rec1.get();
                                status.setText("Click TRACE to extract chromatogram");
                            }

                            samplesList.revalidate();
                            samplesList.repaint();
                            samplesList.validate();
                            samplesList.updateUI();
                            progressBar.setStringPainted(false);
                            progressBar.setValue(0);
                            // convert Collection to a SECFile using SECBuilder

                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        } catch (ExecutionException e1) {
                            e1.printStackTrace();
                        }

                    }
                }.start();
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


        TRACEButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                    /*
                     * update trace plot from SECBuilder
                     */
                if (saveAsTextField.getText().length() < 3 && !(secFile instanceof SECFile)){
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane optionPane = new JOptionPane("Too short, provide a meaningful name",JOptionPane.WARNING_MESSAGE);
                    JDialog dialog = optionPane.createDialog("Warning!");
                    dialog.setAlwaysOnTop(true);
                    dialog.setVisible(true);
                    return;
                }

                if (collection.getTotalDatasets() > 2 && !(secFile instanceof SECFile)){ // make new SEC FILE

                    new Thread() {
                        public void run() {

                            try { // no secFile loaded, only data files
                                TRACEButton.setEnabled(false);
                                SetBufferButton.setEnabled(false);

                                int totalNotSelected = collection.getTotalNotSelected();
                                while(totalNotSelected > 0){ // remove unused datasets from collection
                                    for(int i=0; i<collection.getTotalDatasets(); i++){
                                        if (!collection.getDataset(i).getInUse()){
                                            collection.removeDataset(i);
                                            //samplesList.remove(i);
                                            sampleFilesModel.remove(i);
                                            break;
                                        }
                                    }
                                    totalNotSelected = collection.getTotalNotSelected();
                                }

                                SECBuilder secBuilder = new SECBuilder(collection, status, progressBar, saveAsTextField.getText(),  Scatter.WORKING_DIRECTORY.getWorkingDirectory(), Double.parseDouble(thresholdField.getText()));
                                secBuilder.setExcludePoints(Integer.parseInt(excludeComboBox.getSelectedItem().toString()));

                                secBuilder.run();
                                secBuilder.get();

                                progressBar.setStringPainted(false);
                                progressBar.setValue(0);

                                secFile = new SECFile(new File(secBuilder.getOutputname()));
                                updateSignalPlot();
                                selectedIndices.clear();

                                for(int i=0;i<secFile.getTotalFrames(); i++){
                                    selectedIndices.add(false);
                                }

                                framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames() );
                                selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());

                                TRACEButton.setEnabled(true);
                                SetBufferButton.setEnabled(true);
                                TRACEButton.setText("UPDATE");

                                sampleFilesModel.removeAllElements();
                                collection.removeAllDatasets();
                                secFileLabel.setText("SEC FILE :: " + secFile.getFilename());
                                status.setText("Using *.sec file now clearing samples list");

                            } catch (InterruptedException e1) {
                                TRACEButton.setEnabled(true);
                                SetBufferButton.setEnabled(true);
                                e1.printStackTrace();
                            } catch (ExecutionException | IOException e1) {
                                TRACEButton.setEnabled(true);
                                SetBufferButton.setEnabled(true);
                                e1.printStackTrace();
                            }
                        }
                    }.start();

                } else if (setBuffer){ // if new buffer is region is set

                    if (selectedBuffers.size() > 1){

                        // populate collection
                        collection.removeAllDatasets();
                        TRACEButton.setEnabled(false);
                        SetBufferButton.setEnabled(false);
                        new Thread() {
                            public void run() {
                                try {
                                    SECBuilder secBuilder = new SECBuilder(secFile, selectedBuffers, status, progressBar, Scatter.WORKING_DIRECTORY.getWorkingDirectory(), Double.parseDouble(thresholdField.getText()));
                                    secBuilder.setExcludePoints(Integer.parseInt(excludeComboBox.getSelectedItem().toString()));

                                    secFile.closeFile();
                                    secBuilder.run();
                                    secBuilder.get();
                                    // reset secFile
                                    progressBar.setStringPainted(false);
                                    progressBar.setIndeterminate(true);
                                    status.setText("Loading updated SEC file");
                                    secFile = new SECFile(new File(secBuilder.getOutputname()));
                                    updateSignalPlot();
                                    selectedIndices.clear();

                                    for(int i=0;i<secFile.getTotalFrames(); i++){
                                        selectedIndices.add(false);
                                    }

                                    framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames() );
                                    selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());
                                    TRACEButton.setEnabled(true);
                                    SetBufferButton.setEnabled(true);
                                    status.setText("Finished loading SEC file");
                                } catch (InterruptedException e1) {
                                    TRACEButton.setEnabled(true);
                                    SetBufferButton.setEnabled(true);
                                    e1.printStackTrace();
                                } catch (ExecutionException | IOException e1) {
                                    TRACEButton.setEnabled(true);
                                    SetBufferButton.setEnabled(true);
                                    e1.printStackTrace();
                                }
                                status.setText("Finished");
                                progressBar.setIndeterminate(false);
                                progressBar.setStringPainted(false);
                                progressBar.setValue(0);
                            }
                        }.start();
                    }

                } else {
                    // just update details
                    if (secFile instanceof SECFile){
                        // if threhold value change, recalculate Rg and Izero
                        String secfilename = secFile.getAbsolutePath();
                        double tval = Double.parseDouble(thresholdField.getText());
                        if (Math.abs(tval - secFile.getThreshold()) > 0.001){

                            TRACEButton.setEnabled(false);
                            SetBufferButton.setEnabled(false);
                            new Thread() {
                                public void run() {
                                    SECBuilder secBuilder = null;
                                    try {// SECFile oldsecfile, JLabel status, JProgressBar bar, String workingDirectoryName, double threshold, int excludePoints
                                        secBuilder = new SECBuilder(
                                                secFile,
                                                status,
                                                progressBar,
                                                Scatter.WORKING_DIRECTORY.getWorkingDirectory(),
                                                Double.parseDouble(thresholdField.getText()),
                                                Integer.parseInt(excludeComboBox.getSelectedItem().toString()));
                                        secFile.closeFile();
                                        secBuilder.run();
                                        secBuilder.get();

                                        // reset secFile
                                        status.setText("Loading updated SEC file");

//                                secFile = new SECFile(new File(secBuilder.getOutputname()));
//                                updateSignalPlot();
//                                selectedIndices.clear();
//
//                                for(int i=0;i<secFile.getTotalFrames(); i++){
//                                    selectedIndices.add(false);
//                                }
                                        try {
                                            secFile = new SECFile(new File(secfilename));
                                            updateSelectedRegionPanel();
                                        } catch (IOException ex) {
                                            ex.printStackTrace();
                                        }

                                        framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames() );
                                        selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());
                                        status.setText("Finished loading SEC file");

                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                    } catch (InterruptedException ex) {
                                        ex.printStackTrace();
                                    } catch (ExecutionException ex) {
                                        ex.printStackTrace();
                                    }
                                    TRACEButton.setEnabled(true);
                                    SetBufferButton.setEnabled(true);
                                }
                            }.start();


                            // reopen file and set reference
//                            try {
//                                secFile = new SECFile(new File(secfilename));
//                            } catch (IOException ex) {
//                                ex.printStackTrace();
//                            }

                        } else {

                            String sasObjectString = secFile.getSasObjectJSON() + System.lineSeparator();
                            String parentPath = secFile.getParentPath();
                            secFile.closeFile();

                            try {
                                int size = 8192 * 16;
                                // only replacing first line (sasObjectString)
                                System.out.println( System.getProperty("user.dir"));
                                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(secfilename)), size);
                                br.readLine();
                                BufferedWriter bw = new BufferedWriter(new FileWriter(parentPath+"/temp.sec"));
                                bw.write(sasObjectString);

                                int is;
                                do {
                                    is = br.read();
                                    if (is != -1) {
                                        bw.write((char) is);
                                    }
                                } while (is != -1);
                                bw.close();
                                br.close();
                                // overwrite original file
                                File f1 = new File(parentPath+"/temp.sec");
                                File f2 = new File(secfilename);
                                f1.renameTo(f2);

                            } catch (FileNotFoundException ex) {
                                ex.printStackTrace();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }

                            try {
                                secFile = new SECFile(new File(secfilename));
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }

                }

                setBuffer = false;
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

                // create report using SEC File
                SECReport report = new SECReport(frameToMergeStart, frameToMergeEnd, secFile, Scatter.WORKING_DIRECTORY.getWorkingDirectory(), averageCheckBox.isSelected());

                if (averageCheckBox.isSelected() && (frameToMergeEnd- frameToMergeStart) > 2){

                    ScaleManagerSAS peakToMerge = new ScaleManagerSAS(frameToMergeStart, frameToMergeEnd, secFile, progressBar, status, true);
                    String nameofnew = (saveAsTextField.getText().length() < 3) ? (secFile.getFilebase() + "_"+ Scatter.collectionSelected.getTotalDatasets()) : saveAsTextField.getText().replaceAll("\\W","_");

                    Thread mergeIt = new Thread() {
                        public void run() {
                            MERGEButton.setEnabled(false);
                            peakToMerge.run();
                            try {
                                peakToMerge.get();
                                /*
                                 * create new dataset from averaged and load into Analysis
                                 */

                                Scatter.collectionSelected.createDataset(peakToMerge.getMerged(), peakToMerge.getMergedErrors(), nameofnew, true);
                                // write dataset to file
                                FileObject dataToWrite = new FileObject(new File(secFile.getParentPath()), Scatter.version);
                                dataToWrite.writeSAXSFile(nameofnew, Scatter.collectionSelected.getLast());

                                MERGEButton.setEnabled(true);
                            } catch (InterruptedException ex) {
                                MERGEButton.setEnabled(true);
                                ex.printStackTrace();
                            } catch (ExecutionException ex) {
                                MERGEButton.setEnabled(true);
                                ex.printStackTrace();
                            }
                            progressBar.setStringPainted(false);
                            progressBar.setValue(0);
                        }
                    };
                    mergeIt.start();
                    try {
                        mergeIt.join();
                    } catch (Exception ex) {
                        System.out.println(ex);
                    }
                    // wait here to finish
                    report.setScaleFactors(peakToMerge.getScaleFactors());
                    report.setMergedAndMedian(peakToMerge.getMerged(), peakToMerge.getMedian());

                    report.writeReport(nameofnew);

                } else if (frameToMergeEnd > frameToMergeStart) { // write out selected frames

                    ArrayList<Double> qvalues = secFile.getQvalues();
                    int totalIn = qvalues.size();

                    for(int i=frameToMergeStart; i<=frameToMergeEnd; i++){

                        ArrayList<Double> iofq = secFile.getSubtractedFrameAt(i);
                        ArrayList<Double> eofq = secFile.getUnSubtractedErrorFrameAt(i);

                        XYSeries one = new XYSeries("iofq");
                        XYSeries two = new XYSeries("eofq");

                        double qvalue;
                        for(int q=0; q<totalIn; q++){
                            qvalue=qvalues.get(q);
                            one.add(qvalue, iofq.get(q));
                            two.add(qvalue, eofq.get(q));
                        }

                        FileObject dataToWrite = new FileObject(new File(secFile.getParentPath()), Scatter.version);

                        String nameofnew = secFile.getFilebase() +"_frame_"+i;
                        Scatter.collectionSelected.createDataset(one, two, nameofnew, true);

                        dataToWrite.writeSAXSFile(nameofnew, Scatter.collectionSelected.getLast());
                    }
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
                    String[] filename = files[0].getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    status.setText("Selected file(s) :: " + files.length);

                    if (files.length == 1){

                            if (ext.equals("sec")){
                                /*
                                 * validate file
                                 * grab JSON
                                 * load signals and associated graphs
                                 */
                                try {
                                    secFile = new SECFile(files[0]);
                                    sampleFilesModel.removeAllElements();
                                    collection.removeAllDatasets();

                                    secFileLabel.setText("Using SEC FILE :: " + secFile.getFilename());
                                    updateSignalPlot();
                                    selectedIndices.clear();

                                    for(int i=0;i<secFile.getTotalFrames(); i++){
                                        selectedIndices.add(false);
                                    }

                                    framesLabel.setText("TOTAL frames :: " + secFile.getTotalFrames() );
                                    selectedBufferIndicesLabel.setText("buffers :: " + secFile.getBufferCount());

                                    secFileLabel.setText("SEC FILE :: " + secFile.getFilename());
                                    secFileLabel.setForeground(Color.cyan);

                                } catch (IOException ee) {
                                    ee.printStackTrace();
                                }

                            } else if (ext.equals("dat")) { // dropping single file that ends in dat

                                new Thread() {
                                    public void run() {
                                        FileListBuilder builder = null;
                                        try {
                                            builder = new FileListBuilder(files[0], Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                                            status.setText("Loading " + builder.getFoundFiles().length + " files, please wait");

                                            ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(builder.getFoundFiles(),
                                                    collection,
                                                    sampleFilesModel,
                                                    status,
                                                    convertNm1ToCheckBox.isSelected(),
                                                    true,
                                                    progressBar,
                                                    Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                                            saveAsTextField.setText(builder.getBase());
                                            rec1.run();
                                            rec1.get();

                                        } catch (Exception ex) {
                                            ex.printStackTrace();
                                        }

                                        status.setText("Click TRACE to extract chromatogram");
                                        samplesList.revalidate();
                                        samplesList.repaint();
                                        samplesList.validate();
                                        samplesList.updateUI();
                                        progressBar.setStringPainted(false);
                                        progressBar.setValue(0);
                                    }
                                }.start();
                            }

                    } else if (files.length > 0 && ext.equals("dat")) {

                            new Thread() {
                                public void run() {

                                    try {

                                        status.setText("Loading " + files.length + " files, please wait");
                                        ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(files,
                                                collection,
                                                sampleFilesModel,
                                                status,
                                                convertNm1ToCheckBox.isSelected(),
                                                true,
                                                progressBar,
                                                Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                                        String basenamefilename = FilenameUtils.removeExtension(files[0].getName());
                                        saveAsTextField.setText(basenamefilename);

                                        rec1.run();
                                        rec1.get();
                                    } catch (InterruptedException e1) {
                                        e1.printStackTrace();
                                    } catch (ExecutionException e1) {
                                        e1.printStackTrace();
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                }
                            }.start();
                    }

                    Scatter.WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());
                    Scatter.updateProp();

                }

            }
        });
    }

    /*
     * check if file is proper format for SEC, if single dat file, then search directory and build list
     */
    public JPanel getPanel(){
        return contentPane;
    }


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
            //setFont(list.getFont());
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

        signalPlot.addSeries(secFile.getBackground()); // background is the first element in signalPlot
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
        chart.setNotify(true);
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

//        signalChartPanel.addChartMouseListener(new ChartMouseListener() {
//            private Double markerStart = Double.NaN;
//            private Double markerEnd = Double.NaN;
//
//            @Override
//            public void chartMouseClicked(ChartMouseEvent chartMouseEvent) {
//                System.out.println("Setting frame min ");
//            }
//
//            @Override
//            public void chartMouseMoved(ChartMouseEvent chartMouseEvent) {
//
//            }
//        });

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
        rangeAxis.setLabelFont(new Font("Times", Font.BOLD, 16));
        rangeAxis.setRange(selectedRegionCollection.getRangeLowerBound(true) - 0.001*selectedRegionCollection.getRangeLowerBound(true), selectedRegionCollection.getRangeUpperBound(true) + 0.005*selectedRegionCollection.getRangeUpperBound(true));
        selectedRegionChart.getXYPlot().setRangeAxis(0, rangeAxis);
        NumberAxis domainAxis = new NumberAxis("frame");
        domainAxis.setRange(selectedStart, selectedEnd);
        domainAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRange(true);
        combinedPlot.setDomainAxis(domainAxis);

        // create residual dataset
        Thread dwplotting = new Thread() {
            public void run() {

                try {
                    DWSimilarityPlot dw = new DWSimilarityPlot(selectedStart, selectedEnd, Double.parseDouble(qminSECField.getText()), Double.parseDouble(qmaxSECField.getText()), secFile, status, progressBar);
                    dw.run();
                    dw.get();
                    if (dw.isDone()){
                        combinedPlot.remove(lowerPlot);
                        lowerPlot = dw.createPlot();
                        combinedPlot.add(lowerPlot, 1);
                    }
                    //combinedPlot.add(dw.createPlot(), 1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

            }
        };
        dwplotting.start();
        try {
            dwplotting.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
        rangeAxisRight.setLabelPaint(new Color(51, 153, 255));
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

//        sasChart.setBackgroundPaint(Color.black);
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
        private int totalInCollection=0;


        public ArrowKeyListener(ValueMarker signalPlotMarker){
            this.mousemarker = signalPlotMarker;
        }

        public void setTotalInCollection(int tot){
            this.totalInCollection = tot;
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
}
