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
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import version4.Collection;
import version4.Constants;
import version4.Subtraction;
import version4.plots.XYPlot;
import version4.sasCIF.SasObject;
import version4.sasCIF.SasObjectForm;
import version4.tableModels.DataFileElement;
import version4.tableModels.SampleBufferElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class Subtract {
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
    public DefaultListModel<SampleBufferElement> bufferFilesModel;
    public DefaultListModel<SampleBufferElement> sampleFilesModel;

    private XYPlot samplesPlot;
    private XYPlot buffersPlot;
    private DataChart samplesSimPlot;
    private DataChart buffersSimPlot;

    private JLabel status;
    private JProgressBar progressBar;
    private JCheckBox convertNm1ToCheckBox;

    private JList buffersList;
    private JList samplesList;
    private Collection samplesCollection;
    private Collection buffersCollection;

    private static int selectedStart, selectedEnd, selectedBufferStart, selectedBufferEnd;

    private ArrayList<Boolean> selectedIndices;
    private ArrayList<Boolean> selectedBufferIndices;

    private SasObject sasObject;

    private int cpuCores = 1;

    public Subtract(JLabel status, JProgressBar progressBar, JCheckBox convert) {
        this.status = status;
        this.progressBar = progressBar;
        this.convertNm1ToCheckBox = convert;

        selectedIndices = new ArrayList<>();
        selectedBufferIndices = new ArrayList<>();

        buffersList = new JList();
        samplesList = new JList();

        this.buffersCollection = new Collection("buffers");
        bufferFilesModel = new DefaultListModel<SampleBufferElement>();
        sampleFilesModel = new DefaultListModel<SampleBufferElement>();
        buffersList.setModel(bufferFilesModel);
        samplesList.setModel(sampleFilesModel);
        buffersList.setCellRenderer(new SampleBufferListRenderer());
        buffersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        samplesList.setCellRenderer(new SampleBufferListRenderer());
        samplesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        //Add to JScrollPane from GUI
        buffers.setViewportView(buffersList);
        samples.setViewportView(samplesList);

        samplesSimPlot = new DataChart("samples");
        sampleSimPanel.add(samplesSimPlot.getChartPanel());
        buffersSimPlot = new DataChart("buffers");
        BufferSimPanel.add(buffersSimPlot.getChartPanel());

        this.setupSamplesChartPanel();
        this.cpuCores = Runtime.getRuntime().availableProcessors() - 1;

        /*
         * set cutoff combo Box
         */
        String[] rejections ={"1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5"};
        for (String i:rejections){
            subtractionCutOff.addItem(i);
        }
        subtractionCutOff.setSelectedIndex(3);
        comboBoxSubtractBins.setSelectedIndex(2);

        new FileDrop(this.getSamplesPanel(), new FileDrop.Listener() {

            @Override
            public void filesDropped(final File[] files) {
                new Thread() {
                    public void run() {

                        try {
                            ReceivedDroppedFiles rec1 = null;
                            sampleFilesModel.removeAllElements();
//                            samplesCollection.removeAllDatasets();

                            if (files.length == 1 && buildListFromFileCheckBox.isSelected()) { // auto build list from file if list == 1 and extension is not .sec

                                String[] filename = files[0].getName().split("\\.(?=[^\\.]+$)");
                                String ext = filename[1];

                                if (ext.equals("dat")) { // dropping single file that ends in dat
                                    try {
                                        FileListBuilder builder = new FileListBuilder(files[0], Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                                        status.setText("Loading " + builder.getFoundFiles().length + " files, please wait");
                                        rec1 = new ReceivedDroppedFiles(builder.getFoundFiles(),
                                                samplesCollection,
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
                                rec1 = new ReceivedDroppedFiles(files, samplesCollection, sampleFilesModel, status, convertNm1ToCheckBox.isSelected(), true, progressBar, Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                            }

                            rec1.run();
                            rec1.get();

                            int totalIn = samplesCollection.getTotalDatasets();
                            if (samplesCollection.getTotalDatasets() > 0){
                                // reset colors
                                float inv = 1.0f/255.0f;
                                selectedIndices.clear();
                                for(int i=0; i<totalIn; i++){
                                    // end 15 108 255 (dark blue)
                                    // start 242 214 245
                                    int r = interpolate(104, 255, i, totalIn);
                                    int g = interpolate(166, 93, i, totalIn);
                                    int b = interpolate(255, 135, i, totalIn);
                                    samplesCollection.getDataset(i).setColor(new Color(r*inv,g*inv,b*inv, 0.25f));
                                    sampleFilesModel.get(i).setColor(new Color(r,g,b));
                                    selectedIndices.add(true);
                                }
                            }

                            samplesList.repaint();
                            samplesList.updateUI();

                            progressBar.setStringPainted(false);
                            progressBar.setValue(0);

                            // load plots
                            if (samplesCollection.getTotalDatasets() > 0){
                                updateSamplesPlot();
                            }

                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        } catch (ExecutionException e1) {
                            e1.printStackTrace();
                        }

                    }
                }.start();
            }
        });


        new FileDrop(this.getBuffersPanel(), new FileDrop.Listener() {

            @Override
            public void filesDropped(final File[] files) {
                new Thread() {
                    public void run() {

                        try {
                            ReceivedDroppedFiles rec1 = null;
                            bufferFilesModel.removeAllElements();
//                            buffersCollection.removeAllDatasets();

                            if (files.length == 1 && buildListFromFileCheckBox.isSelected()) { // auto build list from file if list == 1 and extension is not .sec

                                String[] filename = files[0].getName().split("\\.(?=[^\\.]+$)");
                                String ext = filename[1];

                                if (ext.equals("dat")) { // dropping single file that ends in dat

                                    try {
                                        FileListBuilder builder = new FileListBuilder(files[0], Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                                        status.setText("Loading " + builder.getFoundFiles().length + " files, please wait");
                                        rec1 = new ReceivedDroppedFiles(builder.getFoundFiles(),
                                                buffersCollection,
                                                bufferFilesModel,
                                                status,
                                                convertNm1ToCheckBox.isSelected(),
                                                true,
                                                progressBar,
                                                Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                                    } catch (Exception e) {
                                        //programInstance.status.setText(" DROP only one file please");

                                    }
                                }

                            } else { // multiple files dropped
                                rec1 = new ReceivedDroppedFiles(files, buffersCollection, bufferFilesModel, status, convertNm1ToCheckBox.isSelected(), true, progressBar, Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                            }

                            rec1.run();
                            rec1.get();

                            int totalIn = buffersCollection.getTotalDatasets();
                            if (buffersCollection.getTotalDatasets() > 0){
                                // reset colors
                                float inv = 1.0f/255.0f;
                                for(int i=0; i<totalIn; i++){
                                    // end 15 108 255 (dark blue)
                                    // start 242 214 245
                                    int r = interpolate(104, 255, i, totalIn);
                                    int g = interpolate(166, 93, i, totalIn);
                                    int b = interpolate(255, 135, i, totalIn);
                                    buffersCollection.getDataset(i).setColor(new Color(r*inv,g*inv,b*inv, 0.25f));
                                    bufferFilesModel.get(i).setColor(new Color(r,g,b));
                                }
                            }

                            buffersList.repaint();
                            buffersList.updateUI();

                            progressBar.setStringPainted(false);
                            progressBar.setValue(0);

                            // load plots
                            if (buffersCollection.getTotalDatasets() > 0){
                                updateBuffersPlot();
                               // updateBuffersSimPlot();
                                selectedBufferStart = 0;
                                selectedBufferEnd = buffersCollection.getTotalDatasets()-1;
                                bufferStartField.setText(Integer.toString(selectedBufferStart));
                                bufferEndField.setText(Integer.toString(selectedBufferEnd));

                                selectedBufferIndices.clear();

                                for(int i=0; i<buffersCollection.getTotalDatasets(); i++){
                                    selectedBufferIndices.add(true);
                                }
                            }

                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        } catch (ExecutionException e1) {
                            e1.printStackTrace();
                        }

                    }
                }.start();
            }
        });


        clearSamplesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                samplesCollection.removeAllDatasets();
                sampleFilesModel.clear();
                samplesSimPlot.clear();
                selectedIndices.clear();

                sampleStarttextField.setText("-");
                sampleEndtextField.setText("-");

                sasObject = new SasObject();

                ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).clear();
                samplesPlot.clear();
            }
        });


        clearBuffersButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                buffersCollection.removeAllDatasets();
                bufferFilesModel.clear();
                buffersSimPlot.clear();
                selectedBufferIndices.clear();

                bufferStartField.setText("-");
                bufferEndField.setText("-");

                ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).clear();
                buffersPlot.clear();
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

                    outputDirLabel.setText(chooser.getSelectedFile().getName()+"/");
                    Scatter.updateProp();
                }
            }
        });


        sampleStarttextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                int x = 0;
                try {
                    x = Integer.parseInt(sampleStarttextField.getText());
                    //update mouse marker
                    if(x > Integer.parseInt(sampleEndtextField.getText())){
                        status.setForeground(Color.RED);
                        status.setText("Invalid Input, setting to 0");
                        sampleStarttextField.setText("0");
                        x=0;
                    }

                    selectedStart = x;

                    ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerStart = (double)x;
                    setSampleSelectedStartEnd();

                } catch (NumberFormatException ee) {

                    status.setForeground(Color.RED);
                    status.setText("Invalid Input, setting to 0");
                    sampleStarttextField.setText("0");
                    ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerStart = 0.0d;
                    selectedStart=0;
                    setSampleSelectedStartEnd();

                }
            }
        });


        sampleEndtextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                int x = 0;
                try {
                    x = Integer.parseInt(sampleEndtextField.getText());

                    if(x < Integer.parseInt(sampleStarttextField.getText())){
                        status.setForeground(Color.RED);
                        x = selectedIndices.size()-1;
                        status.setText("Invalid Input, setting to " + x);
                        sampleEndtextField.setText( Integer.toString(x));
                    }

                    selectedEnd = x;
                    ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)x;
                    setSampleSelectedStartEnd();

                } catch (NumberFormatException ee) {
                    status.setForeground(Color.RED);
                    status.setText("Invalid Input, setting to 0");
                    x = selectedIndices.size()-1;
                    sampleEndtextField.setText(Integer.toString(x));
                    selectedEnd = x;
                    ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)x;
                    setSampleSelectedStartEnd();
                }
            }
        });


        bufferStartField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                int x = 0;
                try {
                    x = Integer.parseInt(bufferStartField.getText());

                    if(x > Integer.parseInt(bufferEndField.getText())){
                        status.setForeground(Color.RED);
                        status.setText("Invalid Input, setting to 0");
                        bufferStartField.setText("0");
                        x = 0;
                    }

                    selectedBufferStart = x;
                    ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).markerStart = (double)x;
                    setBufferSelectedStartEnd();

                } catch (NumberFormatException ee) {
                    status.setForeground(Color.RED);
                    status.setText("Invalid Input, setting to 0");
                    bufferStartField.setText("0");
                    selectedBufferStart = 0;
                    ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).markerStart = (double)0.0d;
                    setBufferSelectedStartEnd();
                }
            }
        });


        bufferEndField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                int x = 0;
                try {
                    x = Integer.parseInt(bufferEndField.getText());

                    if(x < Integer.parseInt(bufferStartField.getText()) || x > (selectedBufferIndices.size()-1)){
                        status.setForeground(Color.RED);
                        x = selectedBufferIndices.size()-1;
                        status.setText("Invalid Input, setting to " + x);
                        bufferEndField.setText( Integer.toString(x));
                    }

                    selectedBufferEnd = x;
                    ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)x;
                    setBufferSelectedStartEnd();

                } catch (NumberFormatException ee) {
                    status.setForeground(Color.RED);
                    x = selectedBufferIndices.size()-1;
                    status.setText("Invalid Input, setting to " + x);
                    bufferEndField.setText( Integer.toString(x));

                    selectedBufferEnd = x;
                    ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)x;
                    setBufferSelectedStartEnd();
                }
            }
        });
        // toggles selected Files in
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
                    //Collection inUse = (Collection) collections.get(96); // set dataset in the collection
                    //inUse.getDataset(index).setInUse(item.isSelected());
                    list.repaint(list.getCellBounds(index, index));
                }
            }
        });

        // toggles selected Files in
        buffersList.addMouseListener(new MouseAdapter() {
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
                    //Collection inUse = (Collection) collections.get(96); // set dataset in the collection
                    //inUse.getDataset(index).setInUse(item.isSelected());
                    list.repaint(list.getCellBounds(index, index));
                }
            }
        });


        SUBTRACTButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                double qmin=0;
                double qmax=0;

                if (!isNumber(qminField.getText()) || !isNumber(qmaxField.getText())){
                    status.setText("q-range (qmin, qmax) is not a number ");
                    return;
                } else {
                    qmin = Double.parseDouble(qminField.getText());
                    qmax = Double.parseDouble(qmaxField.getText());
                    if (qmin > qmax){
                        Toolkit.getDefaultToolkit().beep();
                        JOptionPane optionPane = new JOptionPane("q-range (qmin > qmax) invalid range",JOptionPane.WARNING_MESSAGE);
                        JDialog dialog = optionPane.createDialog("Warning!");
                        dialog.setAlwaysOnTop(true);
                        dialog.setVisible(true);

                        status.setText("q-range (qmin > qmax) invalid range ");
                        return;
                    }
                }

                if (saveAsTextField.getText().length() < 3){
                    Toolkit.getDefaultToolkit().beep();
                    JOptionPane optionPane = new JOptionPane("Provide a meaningful name",JOptionPane.WARNING_MESSAGE);
                    JDialog dialog = optionPane.createDialog("Warning!");
                    dialog.setAlwaysOnTop(true);
                    dialog.setVisible(true);
                    return;
                }

                // launch in separate thread
                final double finalQmin = qmin;
                final double finalQmax = qmax;

                boolean singles = false;
                if (!averageCheckBox.isSelected() && !subtractFromMedianCheckBox.isSelected()){
                    singles = true;
                }

                final boolean scaleBefore = scaleThenMergeCheckBox.isSelected();
                final boolean finalSingles = singles;
                final boolean mergeByAverage = averageCheckBox.isSelected();

                new Thread() {
                    public void run() {

                        for(int i=0; i<buffersCollection.getTotalDatasets(); i++){
                            buffersCollection.getDataset(i).setInUse(selectedBufferIndices.get(i));
                            System.out.println(i + " " + selectedBufferIndices.get(i));
                        }

                        for(int i=0; i<samplesCollection.getTotalDatasets(); i++){
                            samplesCollection.getDataset(i).setInUse(selectedIndices.get(i));
                        }

                        Subtraction subTemp = new Subtraction(buffersCollection, samplesCollection, finalQmin, finalQmax, mergeByAverage, finalSingles, scaleBefore, cpuCores, Scatter.useAutoRg, status, progressBar);

                        subTemp.setBinsAndCutoff(Double.parseDouble(comboBoxSubtractBins.getSelectedItem().toString()), Double.parseDouble(subtractionCutOff.getSelectedItem().toString()));
                        subTemp.setNameAndDirectory(saveAsTextField.getText(), Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                        subTemp.setCollectionToUpdate(Scatter.collectionSelected);

                        subTemp.run();
                        try {
                            subTemp.get();
                        } catch (InterruptedException | ExecutionException e1) {
                            e1.printStackTrace();
                        }

                    }
                }.start();
            }
        });


        sasObject = new SasObject();

        editDetailsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (sasObject instanceof Object){

                    SasObjectForm editForm = new SasObjectForm(sasObject, false);
                    editForm.pack();
                    editForm.setVisible(true);

                } else {

                }
            }
        });



        loadSamplesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JFileChooser fc = new JFileChooser(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                fc.setMultiSelectionEnabled(true);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION) {
                    File[] files = fc.getSelectedFiles();
                    String[] filename = files[0].getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    if (!ext.equals("dat")) {
                        status.setText("Please select suitable dat file ");
                        return;
                    }
                    status.setText("Selected file(s) :: " + files.length);
                    sampleFilesModel.clear();

                    new Thread(){
                        public void run(){

                            ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(files, samplesCollection, sampleFilesModel, status, convertNm1ToCheckBox.isSelected(), true, progressBar, Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                            rec1.run();

                            try {
                                rec1.get();
                                int totalIn = samplesCollection.getTotalDatasets();
                                if (samplesCollection.getTotalDatasets() > 0){
                                    // reset colors
                                    float inv = 1.0f/255.0f;
                                    selectedIndices.clear();
                                    for(int i=0; i<totalIn; i++){
                                        // end 15 108 255 (dark blue)
                                        // start 242 214 245
                                        int r = interpolate(104, 255, i, totalIn);
                                        int g = interpolate(166, 93, i, totalIn);
                                        int b = interpolate(255, 135, i, totalIn);
                                        samplesCollection.getDataset(i).setColor(new Color(r*inv,g*inv,b*inv, 0.25f));
                                        sampleFilesModel.get(i).setColor(new Color(r,g,b));
                                        selectedIndices.add(true);
                                    }
                                }

                                samplesList.repaint();
                                samplesList.updateUI();

                                progressBar.setStringPainted(false);
                                progressBar.setValue(0);

                                // load plots
                                if (samplesCollection.getTotalDatasets() > 0){
                                    updateSamplesPlot();
                                }

                                Scatter.WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());
                                Scatter.updateProp();

                            } catch (InterruptedException | ExecutionException ex) {
                                ex.printStackTrace();
                            }

                        }
                    }.start();
                }
            }
        });

        loadBuffersButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JFileChooser fc = new JFileChooser(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                fc.setMultiSelectionEnabled(true);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION) {
                    File[] files = fc.getSelectedFiles();
                    String[] filename = files[0].getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    if (!ext.equals("dat")) {
                        status.setText("Please select suitable dat file ");
                        return;
                    }
                    status.setText("Selected file(s) :: " + files.length);
                    bufferFilesModel.clear();

                    new Thread(){
                        public void run(){

                            ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(files, buffersCollection, bufferFilesModel, status, convertNm1ToCheckBox.isSelected(), true, progressBar, Scatter.WORKING_DIRECTORY.getWorkingDirectory());

                            rec1.run();

                            try {
                                rec1.get();
                                int totalIn = buffersCollection.getTotalDatasets();
                                if (buffersCollection.getTotalDatasets() > 0){
                                    // reset colors
                                    float inv = 1.0f/255.0f;
                                    selectedIndices.clear();
                                    for(int i=0; i<totalIn; i++){
                                        // end 15 108 255 (dark blue)
                                        // start 242 214 245
                                        int r = interpolate(104, 255, i, totalIn);
                                        int g = interpolate(166, 93, i, totalIn);
                                        int b = interpolate(255, 135, i, totalIn);
                                        buffersCollection.getDataset(i).setColor(new Color(r*inv,g*inv,b*inv, 0.25f));
                                        bufferFilesModel.get(i).setColor(new Color(r,g,b));
                                        selectedIndices.add(true);
                                    }
                                }

                                buffersList.repaint();
                                buffersList.updateUI();

                                progressBar.setStringPainted(false);
                                progressBar.setValue(0);

                                // load plots
                                if (buffersCollection.getTotalDatasets() > 0){
                                    updateBuffersPlot();
                                   // updateBuffersSimPlot();
                                    selectedBufferStart = 0;
                                    selectedBufferEnd = buffersCollection.getTotalDatasets()-1;
                                    bufferStartField.setText(Integer.toString(selectedBufferStart));
                                    bufferEndField.setText(Integer.toString(selectedBufferEnd));

                                    selectedBufferIndices.clear();

                                    for(int i=0; i<buffersCollection.getTotalDatasets(); i++){
                                        selectedBufferIndices.add(true);
                                    }
                                }

                                Scatter.WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());
                                Scatter.updateProp();

                            } catch (InterruptedException | ExecutionException ex) {
                                ex.printStackTrace();
                            }

                        }
                    }.start();
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

    public void printDimensions(){
        System.out.println("ContentPane " + contentPane.getSize().getWidth() + " x " + contentPane.getSize().getHeight());
    }

    private JPanel getSamplesPanel() { return samplesPanel;}
    private JPanel getBuffersPanel() { return BuffersPanel;}

    class SampleBufferListRenderer extends JCheckBox implements ListCellRenderer {

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {

            setEnabled(list.isEnabled());
            setSelected(((SampleBufferElement) value).isSelected());
            //setFont(list.getFont());
            setFont( new Font ("Sanserif", Font.PLAIN, 12));
            setBackground(list.getBackground());
            setForeground(((SampleBufferElement) value).getColor());
            setText(value.toString());

            if (isSelected) {
                setBackground(Constants.LightBlueGray);
            }

            return this;
        }
    }

    private void setupSamplesChartPanel(){
        this.samplesCollection = new Collection("samples");
        samplesPlot = new XYPlot(samplesCollection, Scatter.WORKING_DIRECTORY, false);
        samplesPlot.setTitle("Samples");
        samplesPlot.setRangeAxisTitle("Intensity");
        samplesPlot.setDomainAxisTitle("q");
        SampleIofQPanel.add(samplesPlot.getChartPanel());

        this.buffersCollection = new Collection("buffers");
        buffersPlot = new XYPlot(buffersCollection, Scatter.WORKING_DIRECTORY, true);
        buffersPlot.setTitle("Buffers");
        buffersPlot.setRangeAxisTitle("Intensity");
        buffersPlot.setDomainAxisTitle("q");

        bufferIofQPanel.add(buffersPlot.getChartPanel());

        BufferSimPanel.add(buffersSimPlot.getChartPanel());
        sampleSimPanel.add(samplesSimPlot.getChartPanel());

        // add Mouse listener to chart
        samplesSimPlot.getChartPanel().addMouseListener(new MouseMarker(this, samplesSimPlot.getChartPanel(), selectedIndices));
        buffersSimPlot.getChartPanel().addMouseListener(new MouseMarker(this, buffersSimPlot.getChartPanel(), selectedBufferIndices));
    }


    private void updateSamplesPlot(){
        this.resize();
        samplesPlot.updateChart();
        updateSampleSimPlot();
        contentPane.validate();
    }

    private void updateBuffersPlot(){
        this.resize();
        buffersPlot.updateChart();
        updateBuffersSimPlot();
        contentPane.validate();
    }

    private int interpolate(int startValue, int endValue, int stepNumber, int lastStepNumber) {
        return (endValue - startValue) * stepNumber / lastStepNumber + startValue;
    }

    private void resize(){
        int height = (int)(contentPane.getHeight()*0.25);
        SampleIofQPanel.setPreferredSize(new Dimension(height, height));
        bufferIofQPanel.setPreferredSize(new Dimension(height, height));
        sampleSimPanel.setPreferredSize(new Dimension(height, height));
        BufferSimPanel.setPreferredSize(new Dimension(height, height));
    }


    private void updateSampleSimPlot(){

        /*
         * using first frame as reference, calculate residuals
         */
        XYSeries ref = samplesCollection.getDataset(0).getAllData();
        int totalInref = ref.getItemCount();
        ArrayList<Double> residuals= new ArrayList<>();

        double qmin = Double.parseDouble(qminField.getText());
        int startAt = 0;
        for(; startAt<totalInref; startAt++){
            if (ref.getX(startAt).doubleValue() >= qmin){
                break;
            }
        }

        double qmax = Double.parseDouble(qmaxField.getText());
        for(int i=startAt; i<totalInref; i++){
            residuals.add(0.0d);
            if (ref.getX(i).doubleValue() >= qmax){
                totalInref = i;
                break;
            }
        }

        XYSeries sim = new XYSeries("sim");

        for(int i=1; i<samplesCollection.getTotalDatasets(); i++){
            XYSeries target = samplesCollection.getDataset(i).getAllData();
            int count = 0;
            for(int r=startAt; r<totalInref; r++){
                XYDataItem item = ref.getDataItem(r);
                int index = target.indexOf(item.getX());
                if (index > -1){
                    residuals.set(count, target.getY(index).doubleValue() - item.getY().doubleValue());
                    count+=1;
                }
            }
            sim.add(i, calculateDurbinWatson(residuals, count));
        }

        samplesSimPlot.update(sim);
    }



    private void updateBuffersSimPlot(){
        /*
         * using first frame as reference, calculate residuals
         */
        XYSeries ref = buffersCollection.getDataset(0).getAllData();
        int totalInref = ref.getItemCount();
        ArrayList<Double> residuals= new ArrayList<>();

        double qmin = Double.parseDouble(qminField.getText());
        int startAt = 0;
        for(; startAt<totalInref; startAt++){
            if (ref.getX(startAt).doubleValue() >= qmin){
                break;
            }
        }

        double qmax = Double.parseDouble(qmaxField.getText());
        for(int i=startAt; i<totalInref; i++){
            residuals.add(0.0d);
            if (ref.getX(i).doubleValue() >= qmax){
                totalInref = i;
                break;
            }
        }

        XYSeries sim = new XYSeries("sim");
        sim.add(0,0);
        for(int i=1; i<buffersCollection.getTotalDatasets(); i++){
            XYSeries target = buffersCollection.getDataset(i).getAllData();
            int count = 0;
            for(int r=startAt; r<totalInref; r++){
                XYDataItem item = ref.getDataItem(r);
                int index = target.indexOf(item.getX());
                if (index > -1){
                    residuals.set(count, target.getY(index).doubleValue() - item.getY().doubleValue());
                    count+=1;
                }
            }
            sim.add(i, calculateDurbinWatson(residuals, count));
        }

        buffersSimPlot.update(sim);
    }

    /**
     * d = 2 means no autocorrelation
     * d-value should always lie between 0 and 4
     */
    private double calculateDurbinWatson(ArrayList<Double> testSeries, int totalResiduals){

        double numerator=0, value, diff;
        double denominator = testSeries.get(0)*testSeries.get(0);
        //int totalResiduals = testSeries.size();

        for(int i=1; i<totalResiduals; i++){
            value = testSeries.get(i);
            diff = value - testSeries.get(i-1); // x_(t) - x_(t-1)
            numerator += diff*diff;
            denominator += value*value; // sum of (x_t)^2
        }

        value = numerator/denominator;

//        if ((value > 1.9 && value < 2.1)){
//            value = 0;
//        } else {
//            value = (Math.abs(value-2.0));
//        }

        return value - 2;
    }


    public class DataChart {
        XYSeriesCollection plotCollection;
        JFreeChart chart;
        ChartPanel chartPanel;
        private String name;

        double pt = 5;
        float strokeit = 1.1f;

        public DataChart(String name){
            this.name  = name;
            plotCollection = new XYSeriesCollection();
            chart = ChartFactory.createXYLineChart(
                    name,            // chart title
                    "frame",                 // domain axis label
                    "similarity",                 // range axis label
                    plotCollection,                   // data
                    PlotOrientation.VERTICAL,
                    false,                    // include legend
                    true,                     // toolTip
                    false
            );

            chart.getXYPlot().setBackgroundAlpha(0.0f);
            NumberAxis domainAxis = new NumberAxis("");
            String quote = "frame";
//            domainAxis.setLabelFont(Constants.BOLD_16);
            domainAxis.setTickLabelFont(Constants.FONT_12);
            domainAxis.setLabel(quote);
            chart.getXYPlot().setDomainAxis(domainAxis);
            chart.getTitle().setVisible(false);

            NumberAxis rangeAxis = new NumberAxis("");
            quote = "similarity";
            rangeAxis.setLabel(quote);
            rangeAxis.setAutoRange(true);
            //rangeAxis.setAutoRangeIncludesZero(false);
            chart.getXYPlot().setRangeAxis(rangeAxis);

            XYLineAndShapeRenderer baseRenderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
            double pt = 2;
            double offset = -0.5*pt;
            baseRenderer.setBaseLinesVisible(false);
            baseRenderer.setBaseShapesVisible(true);
            baseRenderer.setBaseShape(new Ellipse2D.Double(offset, offset, pt, pt));
            baseRenderer.setBaseOutlineStroke(new BasicStroke(1.1f));
            baseRenderer.setBaseShapesFilled(false);


            chartPanel = new ChartPanel(chart){
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

            chartPanel.getChart().getXYPlot().getRenderer(0).setBaseToolTipGenerator(new XYToolTipGenerator() {
                @Override
                public String generateToolTip(XYDataset xyDataset, int i, int i2) {
                    return (String) "frame_"+i2;
                }
            });

            chartPanel.setMouseZoomable(false);
            chartPanel.setHorizontalAxisTrace(true);
            chartPanel.setVerticalAxisTrace(false);
        }

        public ChartPanel getChartPanel(){
            return chartPanel;
        }

        public void update(XYSeries data){
            plotCollection.removeAllSeries();
            plotCollection.addSeries(data);


            XYLineAndShapeRenderer baseRenderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
            double offset = -0.5*pt;
            baseRenderer.setSeriesPaint(0, new Color(0,0,0));
            baseRenderer.setSeriesShapesFilled(0, false);
            baseRenderer.setSeriesLinesVisible(0, false);
            baseRenderer.setSeriesShape(0, new Ellipse2D.Double(offset, offset, pt, pt));
            baseRenderer.setSeriesOutlineStroke(0, new BasicStroke(strokeit));
        }

        public boolean checkName(String name) {
            return (this.name.equals(name));
        };
        public void clear(){
            plotCollection.removeAllSeries();
        }
    }


    private final static class MouseMarker extends MouseAdapter {
        private Marker marker;
        private Double markerStart = Double.NaN;
        private Double markerEnd = Double.NaN;
        private final org.jfree.chart.plot.XYPlot plot;
        private final JFreeChart chart;
        private  final ChartPanel panel;
        private ArrayList<Boolean> samplesListSelected;
        private Subtract sub;

        public MouseMarker(Subtract sub, ChartPanel panel, ArrayList<Boolean> samplesList) {
            this.sub = sub;
            this.samplesListSelected = samplesList;
            this.panel = panel;
            this.chart = panel.getChart();
            this.plot = (org.jfree.chart.plot.XYPlot) chart.getPlot();
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

                    if (chart.getTitle().getText().equals("samples")){
                        selectedStart = (markerStart.intValue() < 1) ? 0 : markerStart.intValue();
                        int stopAt = (int)Math.floor(markerEnd);
                        if (stopAt > (samplesListSelected.size()-1)){
                            stopAt = (samplesListSelected.size()-1);
                        }
                        selectedEnd = stopAt;
                        sub.updateSimPlot();
                    } else {
                        selectedBufferStart = (markerStart.intValue() < 1) ? 0 : markerStart.intValue();
                        int stopAt = (int)Math.floor(markerEnd);
                        if (stopAt > (samplesListSelected.size()-1)){
                            stopAt = (samplesListSelected.size()-1);
                        }
                        selectedBufferEnd = stopAt;
                        sub.updateBufferPlot();
                    }
                }
            }
        }

        private Double getPosition(MouseEvent e){
            Point2D p = panel.translateScreenToJava2D( e.getPoint());
            Rectangle2D plotArea = panel.getScreenDataArea();
            org.jfree.chart.plot.XYPlot plot = (org.jfree.chart.plot.XYPlot) chart.getPlot();
            //int mouseX = e.getX();
            // int onscreen = e.getXOnScreen();
            //System.out.println("x = " + mouseX + " onscreen " + onscreen);
            return plot.getDomainAxis().java2DToValue(p.getX(), plotArea, plot.getDomainAxisEdge());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            markerEnd = getPosition(e);

            int size = samplesListSelected.size();
            int trueStart = markerStart.intValue();
            int trueEnd = markerEnd.intValue();

            // set everything within range to true, everything else false
            for(int i=0; i<size; i++){
                // sampleFilesModel.get(i).isSelected()
                if ((i < trueStart) || (i > trueEnd)){
                    samplesListSelected.set(i, false);
                } else {
                    samplesListSelected.set(i, true);
                }
            }

            updateMarker();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            markerStart = getPosition(e);
            // if key pressed
            markerStart = Double.valueOf(Math.floor(getPosition(e)));
        }

        public void clear(){
            plot.removeDomainMarker(marker, Layer.BACKGROUND);
            markerStart = Double.NaN;
            markerEnd = Double.NaN;
        }
    }

    private void updateSimPlot(){
        sampleStarttextField.setText(Integer.toString(selectedStart));
        sampleEndtextField.setText(Integer.toString(selectedEnd));
        // replot
        samplesPlot.updateVisibility(selectedIndices);

        for(int i=0; i<selectedStart; i++){
            DataFileElement item = (DataFileElement) samplesList.getModel().getElementAt(i);
            item.setSelected(false);
            sampleFilesModel.get(i).setSelected(false);
            samplesList.repaint(samplesList.getCellBounds(i, i));
        }

        for(int i=selectedStart; i<=selectedEnd; i++){
            DataFileElement item = (DataFileElement) samplesList.getModel().getElementAt(i);
            item.setSelected(true);
            sampleFilesModel.get(i).setSelected(true);
            samplesList.repaint(samplesList.getCellBounds(i, i));
        }

        for(int i=(selectedEnd+1); i<sampleFilesModel.getSize(); i++){
            DataFileElement item = (DataFileElement) samplesList.getModel().getElementAt(i);
            item.setSelected(false);
            sampleFilesModel.get(i).setSelected(false);
            samplesList.repaint(samplesList.getCellBounds(i, i));
        }
    }


    private void updateBufferPlot(){
        bufferStartField.setText(Integer.toString(selectedBufferStart));
        bufferEndField.setText(Integer.toString(selectedBufferEnd));
        // replot
        //samplesPlot.updateVisibility(selectedIndices);
        buffersPlot.updateVisibility(selectedBufferIndices);

        for(int i=0; i<selectedBufferStart; i++){
            DataFileElement item = (DataFileElement) buffersList.getModel().getElementAt(i);
            item.setSelected(false);
            bufferFilesModel.get(i).setSelected(false);
            buffersList.repaint(buffersList.getCellBounds(i, i));
        }

        for(int i=selectedBufferStart; i<=selectedBufferEnd; i++){
            DataFileElement item = (DataFileElement) buffersList.getModel().getElementAt(i);
            item.setSelected(true);
            bufferFilesModel.get(i).setSelected(true);
            buffersList.repaint(buffersList.getCellBounds(i, i));
        }

        for(int i=(selectedBufferEnd+1); i<bufferFilesModel.getSize(); i++){
            DataFileElement item = (DataFileElement) buffersList.getModel().getElementAt(i);
            item.setSelected(false);
            bufferFilesModel.get(i).setSelected(false);
            buffersList.repaint(buffersList.getCellBounds(i, i));
        }
    }


    private boolean isNumber( String input ) {
        try {
            Double.parseDouble(input);
            return true;
        }
        catch( Exception e) {
            return false;
        }
    }

    private void setSampleSelectedStartEnd(){
        for(int i=0; i<selectedStart; i++){
            selectedIndices.set(i, false);
        }
        for(int i=selectedStart; i<selectedEnd; i++){
            selectedIndices.set(i, true);
        }
        selectedIndices.set(selectedEnd, true);
        for(int i=selectedEnd+1; i<selectedIndices.size(); i++){
            selectedIndices.set(i, false);
        }

//        ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)x;

        ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerStart = (double)selectedStart;
        ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)selectedEnd;
        ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).updateMarker();
    }


    private void setBufferSelectedStartEnd(){

        for(int i=0; i<selectedBufferStart; i++){
            selectedBufferIndices.set(i, false);
        }
        for(int i=selectedBufferStart; i<selectedBufferEnd; i++){
            selectedBufferIndices.set(i, true);
        }
        selectedBufferIndices.set(selectedBufferEnd, true);
        for(int i=selectedBufferEnd+1; i<selectedBufferIndices.size(); i++){
            selectedBufferIndices.set(i, false);
        }

//        ((MouseMarker)samplesSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)x;

        ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).markerStart = (double)selectedBufferStart;
        ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).markerEnd = (double)selectedBufferEnd;
        ((MouseMarker)buffersSimPlot.getChartPanel().getMouseListeners()[2]).updateMarker();
    }
}
