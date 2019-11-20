import FileManager.FileObject;
import FileManager.ReceivedDroppedFiles;
import FileManager.WorkingDirectory;
import org.jfree.data.xy.XYSeriesCollection;
import version4.*;
import version4.ReportPDF.Report;
import version4.Scaler.ScaleManager;
import version4.plots.*;
import version4.tableModels.AnalysisModel;
import version4.tableModels.AnalysisTable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Analysis extends JDialog {
    private JPanel contentPane;
    private JPanel leftMiniPanel;
    private JPanel middleMiniPanel;
    private JPanel topMiniPanel;
    private JPanel analysisButtonsPanel;
    private JPanel basicsPanel;
    private JPanel log10Panel;
    private JPanel normalizedKratkyPanel;
    private JPanel gpaPanel;
    private JLabel qIqLabel;
    private JPanel powerlawPanel;
    private JPanel kratkyPanel;
    private JPanel qIqPanel;
    private JPanel porodDebyePanel;
    private JPanel vcPanel;
    private JPanel volumePanel;
    private JPanel rcPanel;
    private JLabel errorsLabel;
    private JPanel middlePanel;
    private JLabel rcLabel;
    private JLabel log10Label;
    private JLabel nKratkyLabel;
    private JLabel gpaLabel;
    private JLabel volumeLabel;
    private JLabel vcLabel;
    private JLabel pdLabel;
    private JLabel kratkyLabel;
    private JLabel powerlawLabel;
    private JPanel errorsPanel;
    private JPanel izeroRgPanel;
    private JLabel izeroRglabel;
    private JPanel complexPanel;
    private JLabel complexLabel;
    private JLabel ratioLabel;
    private JPanel ratioPanel;
    private JPanel dataPanel;
    private JLabel loadFileLabel;
    private JPanel LoadFilePanel;
    private JPanel saveFilePanel;
    private JPanel scalePanel;
    private JPanel scaleMergePanel;
    private JPanel scaleToIzero;
    private JLabel averageLabel;
    private JPanel averagePanel;
    private JLabel medianLabel;
    private JLabel scaletoIzeroLabel;
    private JLabel scaleMergeLabel;
    private JPanel medianPanel;
    private JLabel scaleLabel;
    private JLabel saveFileLabel;
    private JPanel pdfPanel;
    private JLabel pdfLabel;
    private JLabel svdLabel;
    private JPanel SVDPanel;
    private JButton buttonOK;
    private JLabel status;

    private MiniPlots analysisMiniPlots;

    private ArrayList<Button> buttons;

    private Color mainBackgroundColor;
    private Color highlight1;
    private Color highlight2;
    private Color highlight3;
    private Color highlight4;
    private Color highlight5;

    private JProgressBar mainProgressBar;


    private PlotData log10Plot;
    private ErrorsPlot errorPlot;
    private qIqPlot qIqPlot;
    private PowerLawPlot powerLawPlot;
    private NormalizedKratkyPlot normalizedGuinierKratkyPlot;
    private RealSpaceNormalizedKratkyPlot normalizedRealKratkyPlot;
    private KratkyPlot kratkyPlot;
    private Collection collectionSelected;
    private WorkingDirectory WORKING_DIRECTORY;
    /**
     * make singleton?
     */
    public Analysis(WorkingDirectory wkd, JLabel status, JProgressBar bar) {
        collectionSelected = Main.collectionSelected;
        this.status = status;
        this.mainProgressBar = bar;

        WORKING_DIRECTORY = wkd;
        setContentPane(contentPane);

        analysisMiniPlots = MiniPlots.getInstance();
        analysisMiniPlots.setChartPanels(leftMiniPanel, middleMiniPanel, topMiniPanel);

        mainBackgroundColor = basicsPanel.getBackground();
        int totalComp = basicsPanel.getComponents().length;
        for(int i=0; i<totalComp; i++){
            basicsPanel.getComponent(i).setBackground(mainBackgroundColor);
        }

        qIqLabel.setText("q\u2022I(q) Plot");
        rcLabel.setText("R\u1D6A Analysis");

        normalizedKratkyPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));
        vcPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));
        powerlawPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));
        ratioPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));
        scaleToIzero.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));
        medianPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));
        saveFilePanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.WHITE));

        Color tc = errorsLabel.getForeground().brighter();
        rcPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, tc));

        highlight1 = new Color(198,248,255);
        highlight2 = kratkyLabel.getForeground();
        highlight3 = errorsLabel.getForeground();
        highlight4 = scaleLabel.getForeground();
        highlight5 = loadFileLabel.getForeground();

        buttons = new ArrayList<>();
        buttons.add(new Button(log10Panel, log10Label,0, highlight1));
        buttons.add(new Button(normalizedKratkyPanel, nKratkyLabel,1, highlight1));
        buttons.add(new Button(gpaPanel, gpaLabel,2, highlight1));
        buttons.add(new Button(volumePanel, volumeLabel,3, highlight1));
        buttons.add(new Button(vcPanel, vcLabel,4, highlight1));
        buttons.add(new Button(porodDebyePanel, pdLabel,5, highlight1));
        buttons.add(new Button(kratkyPanel, kratkyLabel,6, highlight2));
        buttons.add(new Button(powerlawPanel, powerlawLabel,7, highlight2));
        buttons.add(new Button(qIqPanel, qIqLabel,8, highlight2));
        buttons.add(new Button(errorsPanel, errorsLabel,9, highlight3));
        buttons.add(new Button(rcPanel, rcLabel,10, highlight3));
        buttons.add(new Button(izeroRgPanel, izeroRglabel,11, highlight3));
        buttons.add(new Button(complexPanel, complexLabel,12, highlight3));
        buttons.add(new Button(ratioPanel, ratioLabel,13, highlight3));
        buttons.add(new Button(SVDPanel, svdLabel,22, highlight3));

        buttons.add(new Button(scalePanel, scaleLabel,14, highlight4));
        buttons.add(new Button(scaleToIzero, scaletoIzeroLabel,15, highlight4));
        buttons.add(new Button(scaleMergePanel, scaleMergeLabel,16, highlight4));
        buttons.add(new Button(averagePanel, averageLabel,17, highlight4));
        buttons.add(new Button(medianPanel, medianLabel,18, highlight4));


        buttons.add(new Button(LoadFilePanel, loadFileLabel,19, highlight5));
        buttons.add(new Button(saveFilePanel, saveFileLabel,20, highlight5));
        buttons.add(new Button(pdfPanel, pdfLabel,21, highlight5));

        log10Plot = new PlotData(collectionSelected, WORKING_DIRECTORY);
        errorPlot = new ErrorsPlot(collectionSelected, WORKING_DIRECTORY);
        qIqPlot = new qIqPlot(collectionSelected, WORKING_DIRECTORY);
        kratkyPlot = new KratkyPlot(collectionSelected, WORKING_DIRECTORY);
        powerLawPlot = new PowerLawPlot(collectionSelected, WORKING_DIRECTORY);
        normalizedGuinierKratkyPlot = new NormalizedKratkyPlot(collectionSelected, WORKING_DIRECTORY);
        normalizedRealKratkyPlot = new RealSpaceNormalizedKratkyPlot(collectionSelected, WORKING_DIRECTORY);

        collectionSelected.addPropertyChangeListener(log10Plot);
        collectionSelected.addPropertyChangeListener(errorPlot);
        collectionSelected.addPropertyChangeListener(qIqPlot);
        collectionSelected.addPropertyChangeListener(kratkyPlot);
        collectionSelected.addPropertyChangeListener(powerLawPlot);
        collectionSelected.addPropertyChangeListener(normalizedGuinierKratkyPlot);
        collectionSelected.addPropertyChangeListener(normalizedRealKratkyPlot);

        log10Panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    log10Plot.plot();
                } else {
                    alertNone();
                }
            }
        });

        errorsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    errorPlot.plot();
                } else {
                    alertNone();
                }
            }
        });

        qIqPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    qIqPlot.plot();
                } else {
                    alertNone();
                }
            }
        });

        powerlawPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    powerLawPlot.plot();
                } else {
                    alertNone();
                }
            }
        });


        kratkyPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                kratkyPlot.plot();
            }
        });


        normalizedKratkyPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                normalizedGuinierKratkyPlot.plot();
                boolean doReal = false;
                for (int i=0; i<collectionSelected.getTotalDatasets(); i++){
                    Dataset temp = collectionSelected.getDataset(i);
                    if (temp.getRealIzero() > 0 && temp.getRealRg() > 0){
                        doReal = true;
                        break;
                    }
                }

                if (doReal){
                    normalizedRealKratkyPlot.plot();
                }
            }
        });


        gpaPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                int id = collectionSelected.getSelected();

                if (id < 0){
                    status.setText("Please select only one dataset");
                    return;
                }

                if (collectionSelected.getDataset(id).getGuinierRg() <= 0 && collectionSelected.getDataset(id).getGuinierIzero() <= 0){
                    status.setText("Perform Manual Guinier first, auto-Rg failed");
                    return;
                }

                GPAPlot gpaPlot = new GPAPlot("SC\u212BTTER \u2263 GUINIER PEAK ANALYSIS ", collectionSelected.getDataset(id), WORKING_DIRECTORY.getWorkingDirectory());
                gpaPlot.makePlot(Main.analysisTable.getModel());
            }
        });


        /*
         * set background of all components in middle panel
         */
        middlePanel.setBackground(mainBackgroundColor);
        totalComp = middlePanel.getComponents().length;
        for(int i=0; i<totalComp; i++){
            middlePanel.getComponent(i).setBackground(mainBackgroundColor);
        }


        porodDebyePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    FlexibilityPlots flexplot = new FlexibilityPlots(collectionSelected, WORKING_DIRECTORY);
                    flexplot.makePlot();
                } else {
                    alertNone();
                }
            }
        });


        volumePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                if (collectionSelected.getTotalSelected() == 1){
                    int id = collectionSelected.getSelected();
                    VolumePlot tempPlot = new VolumePlot(collectionSelected.getDataset(id), WORKING_DIRECTORY.getWorkingDirectory(), Main.analysisTable.getModel());
                    tempPlot.plot();
                } else if (collectionSelected.getTotalSelected() > 1) {
                    alertTooMany();
                } else {
                    alertNone();
                }
            }
        });


        vcPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    VcPlot tempPlot = new VcPlot(collectionSelected, WORKING_DIRECTORY.getWorkingDirectory());
                    tempPlot.plot(status);
                } else {
                    alertNone();
                }
            }
        });


        rcPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() == 1){
                    int selected = 0;
                    int limit = collectionSelected.getTotalDatasets();

                    for (int i=0; i<limit; i++){
                        if (collectionSelected.getDataset(i).getInUse()){
                            selected = i;
                            break;
                        }
                    }

                    RcXSectionalPlot tempPlot = new RcXSectionalPlot(collectionSelected.getDataset(selected), WORKING_DIRECTORY.getWorkingDirectory());
                    tempPlot.createPlots();

                } else {
                    alertTooMany();
                }
            }
        });


        scalePanel.addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {

                if (collectionSelected.getTotalSelected() > 1){
                    new Thread(){
                        public void run() {

                            if (log10Plot.isVisible()){
                                log10Plot.setNotify(false);
                            }

                            int cpuCores = Runtime.getRuntime().availableProcessors();
                            ScaleManager scalings = new ScaleManager(
                                    cpuCores,
                                    collectionSelected,
                                    mainProgressBar,
                                    status);

                            Settings setit = Settings.getInstance();
                            scalings.setUpperLowerQLimits(setit.getQminLimit(), setit.getQmaxLimit());
                            scalings.execute();

                            try {
                                scalings.get();

                                // rescale plotted data
                                for(int i=0; i<collectionSelected.getTotalDatasets(); i++){
                                    if (collectionSelected.getDataset(i).getInUse()){
                                        collectionSelected.getDataset(i).scalePlottedLog10IntensityData();
                                    }
                                }

                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            } catch (ExecutionException e1) {
                                e1.printStackTrace();
                            }

                            if (log10Plot.isVisible()){
                                log10Plot.setNotify(true);
                            }

                            mainProgressBar.setValue(0);
                            mainProgressBar.setStringPainted(false);

                            ((AnalysisModel)Main.analysisTable.getModel()).fireTableDataChanged();
                        }
                    }.start();
                } else {
                    alertNone();
                }

            }
        });


        SVDPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){
                    // need to determine number of frames selected
                    int inUseCount=0;
                    XYSeriesCollection tempCollection = new XYSeriesCollection();
                    double qmin=1000000;
                    double qmax=-100000;
                    double tempMax, tempMin;

                    for(int i=0; i<collectionSelected.getTotalDatasets(); i++){
                        Dataset tempData = collectionSelected.getDataset(i);
                        if (tempData.getInUse()){
                            tempCollection.addSeries(tempData.getAllData());
                            //tempCollection.addSeries(tempData.getData());
                            tempMin = tempData.getData().getMinX();
                            tempMax = tempData.getData().getMaxX();

                            if (tempMin < qmin){
                                qmin = tempMin;
                            }

                            if (tempMax > qmax){
                                qmax = tempMax;
                            }
                            inUseCount++;
                        }
                    }

                    System.out.println("tempcollection " + tempCollection.getSeriesCount());
                    if (inUseCount < 3){
                        status.setText("Too few frames, 3 or more!");
                        return;
                    }

                    // set common q-values (max and min)
                    final double finalQmin = qmin;
                    final double finalQmax = qmax;

                    Thread makeIt = new Thread(){
                        public void run() {

                            final SVDCovariance svd = new SVDCovariance(finalQmin, finalQmax, tempCollection);
                            svd.execute();

                            try {
                                svd.get();
                                svd.makePlot();
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            } catch (ExecutionException e1) {
                                e1.printStackTrace();
                            }
                        }
                    };

                    makeIt.start();

                } else {
                    alertNone();
                }
            }
        });


        izeroRgPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 1){

                    DoubleXYPlot izeroRgPlot = new DoubleXYPlot(WORKING_DIRECTORY.getWorkingDirectory());
                    izeroRgPlot.makePlot(collectionSelected);

                } else {
                    alertNone();
                }
            }
        });

        ratioPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 1){
                    BinaryComparisonPlot ratioPlot = new BinaryComparisonPlot(collectionSelected, WORKING_DIRECTORY.getWorkingDirectory());
                    ratioPlot.makePlot();
                } else {
                    alertNone();
                }
            }
        });


        complexPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 1){

                    new ComplexPlot(collectionSelected, WORKING_DIRECTORY.getWorkingDirectory(), status);

                } else {
                    alertNone();
                }
            }
        });


        scaleToIzero.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 1){

                    if (collectionSelected.getTotalSelected() < 2){
                        status.setText("Select more than one file!");
                        return;
                    }

                    double maxIzero = Double.NEGATIVE_INFINITY;
                    int ref=0;
                    for(int i=0; i<collectionSelected.getDatasets().size(); i++){
                        Dataset idataset = collectionSelected.getDataset(i);
                        if (idataset.getInUse() && idataset.getGuinierIzero() > maxIzero){
                            maxIzero = idataset.getGuinierIzero();
                            ref = i;
                        }
                    }

                    collectionSelected.getDataset(ref).setScaleFactor(1.00d);
                    collectionSelected.getDataset(ref).scalePlottedLog10IntensityData();

                    double referenceScale = collectionSelected.getDataset(ref).getGuinierIzero();
                    double newScale;
                    for(int i=0; i<collectionSelected.getTotalDatasets(); i++){
                        Dataset tempData = collectionSelected.getDataset(i);
                        if (tempData.getInUse() && i != ref){
                            newScale = referenceScale/tempData.getGuinierIzero();
                            tempData.setScaleFactor(newScale);
                            tempData.scalePlottedLog10IntensityData();
                        }
                    }

                    // update
                    ((AnalysisModel)Main.analysisTable.getModel()).fireTableDataChanged();

                } else {
                    alertNone();
                }
            }
        });


        pdfPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 0){

                    Notes temp = new Notes(collectionSelected, WORKING_DIRECTORY);
                    temp.pack();
                    temp.setVisible(true);
                    Report report = new Report(collectionSelected, WORKING_DIRECTORY, temp.getFilename(),"Summary of Selected Datasets", temp.getText());

                } else {
                    alertNone();
                }
            }
        });


        LoadFilePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                JFileChooser fc = new JFileChooser(WORKING_DIRECTORY.getWorkingDirectory());
                fc.setMultiSelectionEnabled(true);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION){
                    File[] files = fc.getSelectedFiles();
                    status.setText("Selected file(s) :: " + files.length);

                    if (files.length > 0){
                        WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());
                        Main.updateProp();
                        new Thread() {
                            public void run() {

                                try {

                                    ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(
                                            files,
                                            collectionSelected,
                                            Main.analysisTable.getModel(),
                                            status,
                                            Main.convertNmToAngstrom,
                                            Main.useAutoRg,
                                            false,
                                            mainProgressBar,
                                            WORKING_DIRECTORY.getWorkingDirectory());

                                    rec1.run();
                                    rec1.get();
                                } catch (InterruptedException e1) {
                                    e1.printStackTrace();
                                } catch (ExecutionException e1) {
                                    e1.printStackTrace();
                                }

                            }
                        }.start();
                    }
                }
                ((AnalysisModel)Main.analysisTable.getModel()).fireTableDataChanged();
            }
        });

        scaleMergePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 1){

                    Thread tempThread  = new Thread(){
                        public void run() {

                            if (log10Plot.isVisible()){
                                log10Plot.setNotify(false);
                            }

                            int cpuCores = Runtime.getRuntime().availableProcessors();
                            ScaleManager scalings = new ScaleManager(
                                    cpuCores,
                                    collectionSelected,
                                    mainProgressBar,
                                    status);

                            Settings setit = Settings.getInstance();
                            scalings.setUpperLowerQLimits(setit.getQminLimit(), setit.getQmaxLimit());
                            scalings.execute();

                            try {
                                scalings.get();
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            } catch (ExecutionException e1) {
                                e1.printStackTrace();
                            }

                            if (log10Plot.isVisible()){
                                log10Plot.setNotify(true);
                            }

                            mainProgressBar.setValue(0);
                            mainProgressBar.setStringPainted(false);

                            Averager tempAverage = new Averager(collectionSelected, true);

                            JFileChooser fc = new JFileChooser(WORKING_DIRECTORY.getWorkingDirectory());
                            int option = fc.showSaveDialog(contentPane);
                            //set directory to default directory from Settings tab

                            if(option == JFileChooser.APPROVE_OPTION){
                                // remove dataset and write to file
                                // make merged data show on top of other datasets
                                String cleaned = cleanUpFileName(fc.getSelectedFile().getName());

                                if(fc.getSelectedFile()!=null){

                                    WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());

                                    FileObject dataToWrite = new FileObject(fc.getCurrentDirectory(), Main.version);

                                    //close the output stream
                                    collectionSelected.createDataset(tempAverage.getAveraged(), tempAverage.getAveragedError(), cleaned, true);
                                    Dataset tempDataset = collectionSelected.getLast();

                                    // update notes info
                                    tempDataset.setAverageInfo(collectionSelected);
                                    dataToWrite.writeSAXSFile(cleaned, tempDataset);
                                    status.setText(cleaned + ".dat written to "+fc.getCurrentDirectory());

                                    tempDataset.setColor(Color.red);

                                    if (log10Plot.visible){
                                        log10Plot.plot();
                                    }

                                    ((AnalysisModel)Main.analysisTable.getModel()).fireTableDataChanged();
                                }
                            }

                        }
                    };

                    tempThread.start();

                } else {
                    alertNone();
                }
            }
        });




        averagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                if (collectionSelected.getTotalSelected() > 1){

                    Averager tempAverage = new Averager(collectionSelected);

                    JFileChooser fc = new JFileChooser(WORKING_DIRECTORY.getWorkingDirectory());
                    int option = fc.showSaveDialog(contentPane);
                    //set directory to default directory from Settings tab

                    if(option == JFileChooser.CANCEL_OPTION) {
                        return;
                    }

                    if(option == JFileChooser.APPROVE_OPTION){
                        // make merged data show on top of other datasets
                        String cleaned = cleanUpFileName(fc.getSelectedFile().getName());

                        if(fc.getSelectedFile()!=null){

                            WORKING_DIRECTORY.setWorkingDirectory(fc.getCurrentDirectory().toString());

                            FileObject dataToWrite = new FileObject(fc.getCurrentDirectory(), Main.version);

                            collectionSelected.createDataset(tempAverage.getAveraged(), tempAverage.getAveragedError(), cleaned, true);
                            Dataset tempDataset = collectionSelected.getLast();
                            // update notes info
                            tempDataset.setAverageInfo(collectionSelected);
                            dataToWrite.writeSAXSFile(cleaned, tempDataset);

                            //close the output stream
                            status.setText(cleaned + ".dat written to "+fc.getCurrentDirectory());

                            collectionSelected.getLast().setColor(Color.red);

                            if (log10Plot.visible){
                                log10Plot.plot();
                            }

                            ((AnalysisModel)Main.analysisTable.getModel()).fireTableDataChanged();
                        }
                    }

                } else {
                    alertNone();
                }
            }
        });

        setModal(true);
    }

    /**
     * Actions performed by clicking on header
     * @param analysisTable
     */
    public void setDropDownItems(JTable analysisTable, JProgressBar mainProgressBar){

        int cpuCores = Runtime.getRuntime().availableProcessors();
        analysisTable.getTableHeader().addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent mouseEvent){
                int index = analysisTable.columnAtPoint(mouseEvent.getPoint());
                if (index == 2 ){ // invert selection

                    int total = collectionSelected.getDatasets().size();
                    for(int i=0; i<total; i++){
                        collectionSelected.getDataset(i).setInUse(!collectionSelected.getDataset(i).getInUse());
                    }

                    ((AnalysisModel)analysisTable.getModel()).fireTableDataChanged();

                } else if (index == 4) {
                    // add function to replot data given qmin or qmax when user selects columns 4 or 5
                    final String inputValue = JOptionPane.showInputDialog("Please input a minimum q value");

                    if (inputValue != null && isQValue(inputValue)){

                        if (log10Plot.isVisible()){
                            log10Plot.setNotify(false);
                        }

                        final LowerUpperBoundManager boundLower = new LowerUpperBoundManager(
                                cpuCores,
                                collectionSelected,
                                mainProgressBar,
                                status, 4, Double.parseDouble(inputValue));

                        boundLower.setJList(analysisTable);
                        // boundLower.boundNow(4, Double.parseDouble(inputValue));
                        Thread tempT = new Thread(boundLower);
                        tempT.start();

                        if (log10Plot.isVisible()){
                            //update plot
                            //log10IntensityPlot.updatePlot();
                            log10Plot.setNotify(true);
                        }
                    }

                } else if (index == 5) {

                    final String inputValue = JOptionPane.showInputDialog("Please input a maximum q value");

                    if (inputValue != null && isQValue(inputValue)){

                        if (log10Plot.isVisible()){
                            log10Plot.setNotify(false);
                        }

                        final LowerUpperBoundManager boundLower = new LowerUpperBoundManager(
                                cpuCores,
                                collectionSelected,
                                mainProgressBar,
                                status, 5, Double.parseDouble(inputValue));

                        // boundLower.boundNow(5, Double.parseDouble(inputValue));
                        Thread tempT = new Thread(boundLower);
                        tempT.start();

                        if (log10Plot.isVisible()){
                            log10Plot.setNotify(true);
                        }
                        //log10IntensityPlot.plot(collectionSelected, WORKING_DIRECTORY_NAME);
                        // analysisModel.fireTableDataChanged();
                    }
                }
                ((AnalysisModel)analysisTable.getModel()).fireTableDataChanged();
                // check if key is depresed
            }
        });
    }

    public Color getMainBackgroundColor(){ return mainBackgroundColor;}
    public JPanel getPanel(){
        return contentPane;
    }
    public JPanel getDataPanel() { return dataPanel;}

    public void setTableModel(){
        TableColumnModel tcm = Main.analysisTable.getTable().getColumnModel();
        TableColumn tablecolumn = tcm.getColumn(4);
        AnalysisTable.SpinnerEditor temp = (AnalysisTable.SpinnerEditor) tablecolumn.getCellEditor();
        temp.setMiniPlot(analysisMiniPlots);
        temp = (AnalysisTable.SpinnerEditor) tcm.getColumn(5).getCellEditor();
        temp.setMiniPlot(analysisMiniPlots);

        Main.analysisTable.getTable().addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // updateMiniPlots
                updateMiniPlots(Main.analysisTable.getTable().rowAtPoint(e.getPoint()));
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
    }

    private void alertNone(){
        Font ff = status.getFont();
        status.setFont(ff.deriveFont(ff.getStyle() | Font.BOLD));
        status.setText(":: TOO FEW or NO DATASETS SELECTED!");
        status.setForeground(Color.RED);
        Toolkit.getDefaultToolkit().beep();
    }


    private void alertTooMany(){
        Font ff = status.getFont();
        status.setFont(ff.deriveFont(ff.getStyle() | Font.BOLD));
        status.setText(":: Too many selected, choose 1!");
        status.setForeground(Color.RED);
        Toolkit.getDefaultToolkit().beep();
    }

    private void updateMiniPlots(int i) {
        analysisMiniPlots.updatePlots(collectionSelected.getDataset(i));
    }

    public static boolean isQValue(String str)
    {
        try {
            double d = Double.parseDouble(str);
            if ((d<0.0001) || (d>3)){
                throw new NumberFormatException();
            }
        } catch(NumberFormatException nfe) {
            System.out.println("Number is not a proper q-value: 0.0001 < q < 3");
            return false;
        }
        return true;
    }


    private String cleanUpFileName(String fileName){
        String name;
        // remove the dot
        Pattern dot = Pattern.compile(".");
        Matcher expression = dot.matcher(fileName);

        if (expression.find()){
            String[] elements;
            elements = fileName.split("\\.");
            name = elements[0];
        } else {
            name = fileName;
        }

        return name;
    }
}
