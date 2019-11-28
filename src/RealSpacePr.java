import FileManager.FileObject;
import FileManager.WorkingDirectory;
import version4.*;
import version4.InverseTransform.RefinePrManager;


import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.regex.Pattern;

public class RealSpacePr {
    private JPanel contentPane;
    private JPanel lowerPrPanel;
    private JPanel menuBarPrPanel1;
    private JComboBox lambdaBox;
    private JCheckBox excludeBackgroundInFitCheckBox;
    private JCheckBox positiveOnlyCheckBox;
    private JCheckBox showBinsCheckBox;
    private JCheckBox qIQCheckBox;
    private JCheckBox l1NormCheckBox;
    private JCheckBox mooreCheckBox;
    private JCheckBox legendreCheckBox;
    private JCheckBox l2NormCheckBox;
    private JCheckBox SPICheckBox;
    private JScrollPane prScrollPane;
    private JPanel prDistribution;
    private JPanel prIntensity;
    private JLabel prStatusLabel;
    private JLabel prLambdaLabel;
    private JLabel methodInUseLabel;
    private JPanel plotsPanel;
    private JSplitPane prSplitPane;
    private Collection collectionSelected;
    private WorkingDirectory WORKING_DIRECTORY;
    private JLabel status;
    private JProgressBar progressBar;

    public static JTable prTable;
    public static PrModel prModel;

    private JComboBox refinementRoundsBox, rejectionCutOffBox, cBox;

    private int cpuCores;
    private double[] defaultLambdaValues = new double[]{0, 0.000001, 0.000003, 0.000007, 0.00001, 0.00007,  0.0001, 0.0007, 0.001, 0.007, 0.01, 0.07, 0.1, 0.7, 1, 3, 7, 10, 37};

    public RealSpacePr(WorkingDirectory wkd, JProgressBar progressBar, JLabel status, DoubleValue defaultDmax, JComboBox rejectionBox, JComboBox cBox, JComboBox refinmentRoundsBox) {

        collectionSelected = Scatter.collectionSelected;
        this.progressBar = progressBar;
        this.status = status;
        cpuCores = Runtime.getRuntime().availableProcessors();

        //populate the regularization parameter drop down box first
        Double[] finalArray = new Double[defaultLambdaValues.length];
        for(int i=0; i<defaultLambdaValues.length; i++){
            finalArray[i] = defaultLambdaValues[i];
        }
        lambdaBox.setModel(new DefaultComboBoxModel(finalArray));
        lambdaBox.setSelectedIndex(2);

        prLambdaLabel.setText("  \u03b1");
        prLambdaLabel.setForeground(Color.white);

        mooreCheckBox.setSelected(false);
        l1NormCheckBox.setSelected(false);
        legendreCheckBox.setSelected(false);
        l2NormCheckBox.setSelected(false);
        SPICheckBox.setSelected(true);
        excludeBackgroundInFitCheckBox.setSelected(false);
        positiveOnlyCheckBox.setSelected(false);


        prTable = new JTable(new PrModel(
                this.status,
                WORKING_DIRECTORY,
                lambdaBox,
                defaultDmax,
                cBox,
                mooreCheckBox,
                l1NormCheckBox,
                legendreCheckBox,
                l2NormCheckBox,
                SPICheckBox,
                excludeBackgroundInFitCheckBox,
                positiveOnlyCheckBox
        ));

        this.refinementRoundsBox = refinmentRoundsBox;
        this.rejectionCutOffBox = rejectionBox;
        this.cBox = cBox;

        // file chooser for loading files into collection
        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem("Find DMAX");
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int index = prTable.getSelectedRow();

                if (index > -1){
                    // set qmax from spinner
                    PrModel prmodel = (PrModel)prTable.getModel();
                    Dataset tempDataset = collectionSelected.getDataset(prmodel.getDataset(index).getId());
                    double qmax = tempDataset.getAllData().getX(tempDataset.getEnd()-1).doubleValue();
                    new FindDmax(collectionSelected.getDataset(index).getRealSpaceModel(), qmax, WORKING_DIRECTORY);
                }
            }
        });
        popupMenu.add(menuItem);
        prTable.setComponentPopupMenu(popupMenu);

        l1NormCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                positiveOnlyCheckBox.setSelected(false);

                if (l1NormCheckBox.isSelected()){
                    positiveOnlyCheckBox.setSelected(true);

                    mooreCheckBox.setSelected(false);
                    legendreCheckBox.setSelected(false);
                    SPICheckBox.setSelected(false);
                    l2NormCheckBox.setSelected(false);

                    if (excludeBackgroundInFitCheckBox.isSelected()){
                        methodInUseLabel.setText("Direct Inverse FT Method with constant background");
                    } else {
                        methodInUseLabel.setText("Direct Inverse FT Method no background ");
                    }

                    lambdaBox.removeAllItems();
                    Double[] finalArray = new Double[defaultLambdaValues.length];
                    for(int i=0; i<defaultLambdaValues.length; i++){
                        finalArray[i] = new Double(defaultLambdaValues[i]);
                    }
                    lambdaBox.setModel(new DefaultComboBoxModel(finalArray));
                    lambdaBox.setSelectedIndex(2);

                } else {

                    if (mooreCheckBox.isSelected()){
                        methodInUseLabel.setText("Moore Method L1-Norm 2nd Derivative with constant background");
                    } else {
                        if (excludeBackgroundInFitCheckBox.isSelected()){
                            methodInUseLabel.setText("Moore Method L1-Norm Coefficients with constant background");
                        } else {
                            methodInUseLabel.setText("Moore Method L1-Norm Coefficients no background ");
                        }
                    }

                    lambdaBox.setSelectedIndex(2);
                }
            }
        });

        SPICheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                positiveOnlyCheckBox.setSelected(false);

                if (SPICheckBox.isSelected()){

                    l1NormCheckBox.setSelected(false);
                    legendreCheckBox.setSelected(false);
                    l2NormCheckBox.setSelected(false);
                    mooreCheckBox.setSelected(false);
                    positiveOnlyCheckBox.setSelected(false);

                    lambdaBox.setSelectedIndex(0);

                    if (excludeBackgroundInFitCheckBox.isSelected()){
                        methodInUseLabel.setText("SVD Direct Inv FT with constant background");
                    } else {
                        methodInUseLabel.setText("SVD Direct Inv FT no background ");
                    }

                } else {

                    l1NormCheckBox.setSelected(true);

                    for(ActionListener a: l1NormCheckBox.getActionListeners()) {
                        a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null) {
                            //Nothing need go here, the actionPerformed method (with the
                            //above arguments) will trigger the respective listener
                        });
                    }
                }

            }
        });

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment( JLabel.CENTER );

        prModel = (PrModel) prTable.getModel();

        prModel.setBars(progressBar, status, prStatusLabel);
        TableColumnModel pcm = prTable.getColumnModel();

        TableColumn pc = pcm.getColumn(4);
        pc.setCellEditor(new PrSpinnerEditor(prModel, status, qIQCheckBox, excludeBackgroundInFitCheckBox, positiveOnlyCheckBox));
        pc = pcm.getColumn(5);
        pc.setCellEditor(new PrSpinnerEditor(prModel, status, qIQCheckBox, excludeBackgroundInFitCheckBox, positiveOnlyCheckBox));
        pc = pcm.getColumn(9);
        pc.setCellEditor(new PrSpinnerEditor(prModel, status, qIQCheckBox, excludeBackgroundInFitCheckBox, positiveOnlyCheckBox));

        pc = pcm.getColumn(2);
        pc.setCellEditor(new CheckBoxCellEditorRenderer());
        pc.setCellRenderer(new CheckBoxCellEditorRenderer());

        prTable.getColumnModel().getColumn(0).setPreferredWidth(10);
        prTable.getColumnModel().getColumn(1).setPreferredWidth(30);
        prTable.getColumnModel().getColumn(2).setPreferredWidth(35);
        prTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        JTableHeader pheader = prTable.getTableHeader();
        pheader.setDefaultRenderer(new HeaderRenderer(prTable));

        pc = prTable.getColumnModel().getColumn(0);
        pc.setCellEditor(new ColorEditor());
        pc.setCellRenderer(new ColorRenderer(true));

        pc = pcm.getColumn(12);  //Norm
        pc.setCellEditor(new PrButtonEditorRenderer("Norm"));
        pc.setCellRenderer(new PrButtonEditorRenderer("Norm"));
        pc = pcm.getColumn(13);  //Norm
        pc.setCellEditor(new PrButtonEditorRenderer("SEARCH"));
        pc.setCellRenderer(new PrButtonEditorRenderer("SEARCH"));
        pc = pcm.getColumn(14);  //Refine
        pc.setCellEditor(new PrButtonEditorRenderer("Refine"));
        pc.setCellRenderer(new PrButtonEditorRenderer("Refine"));
        pc = pcm.getColumn(15);  //toFile
        pc.setCellEditor(new PrButtonEditorRenderer("2File"));
        pc.setCellRenderer(new PrButtonEditorRenderer("2File"));

        prTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        prTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        prTable.getColumnModel().getColumn(6).setPreferredWidth(170); // I(0)
        prTable.getColumnModel().getColumn(7).setPreferredWidth(160); // Rg

        prTable.getColumnModel().getColumn(10).setPreferredWidth(50); // chi sk2
        prTable.getColumnModel().getColumn(10).setMinWidth(50); // chi sk2

        prTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer); // r_ave
        prTable.getColumnModel().getColumn(5).setCellRenderer(centerRenderer); //
        prTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        prTable.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);
        prTable.getColumnModel().getColumn(8).setCellRenderer(centerRenderer); // r_ave
        prTable.getColumnModel().getColumn(9).setCellRenderer(centerRenderer); // dmax
        prTable.getColumnModel().getColumn(10).setCellRenderer(centerRenderer); // dmax

        prScrollPane.add(prTable);
        prScrollPane.setViewportView(prTable);
        prScrollPane.validate();

        //prPanel.add(prScrollPane); // add pane to panel?,
        prTable.setFillsViewportHeight(false);
        prTable.setRowHeight(32);
        prScrollPane.setOpaque(true);

        WORKING_DIRECTORY = wkd;
        //setContentPane(contentPane);
        qIQCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                IofQPofRPlot iofqPofRplot = IofQPofRPlot.getInstance();
                if (qIQCheckBox.isSelected()){
                    iofqPofRplot.changeToQIQ();
                } else {
                    iofqPofRplot.changeToIQ();
                }
            }
        });

        showBinsCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                PofRPlot pofRplot = PofRPlot.getInstance();

                if (showBinsCheckBox.isSelected()){
                    pofRplot.showBins(true);
                } else {
                    pofRplot.showBins(false);
                }
            }
        });


        positiveOnlyCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                mooreCheckBox.setSelected(false);
                legendreCheckBox.setSelected(false);
                l2NormCheckBox.setSelected(false);
                SPICheckBox.setSelected(false);
                l1NormCheckBox.setSelected(true);

                if (positiveOnlyCheckBox.isSelected()){

                    if(excludeBackgroundInFitCheckBox.isSelected()){
                        methodInUseLabel.setText("L1-NORM DIFT Positive Only with Constant Background");
                    } else {
                        methodInUseLabel.setText("L1-NORM DIFT Positive Only No Background");
                    }
                } else {

                    if(excludeBackgroundInFitCheckBox.isSelected()){
                        methodInUseLabel.setText("L1-NORM DIFT with Constant Background");
                    } else {
                        methodInUseLabel.setText("L1-NORM DIFT No Background");
                    }
                }
            }
        });

        legendreCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                excludeBackgroundInFitCheckBox.setSelected(false);
                excludeBackgroundInFitCheckBox.setForeground(Color.WHITE);

                mooreCheckBox.setSelected(false);

                positiveOnlyCheckBox.setSelected(false);

                l2NormCheckBox.setSelected(false);

                if (legendreCheckBox.isSelected()){

                    l1NormCheckBox.setSelected(false);
                    SPICheckBox.setSelected(false);
                    legendreCheckBox.setSelected(true);

                    lambdaBox.setSelectedIndex(1);
                    methodInUseLabel.setText("Legendre Distribution Estimation");

                    lambdaBox.removeAllItems();

                    double[] arrayvalues = new double[]{0, 0.0001, 0.1, 0.7, 1, 3, 7, 11, 31, 70, 93, 100, 130, 157, 231, 337, 431, 557, 637, 759, 931, 1017, 3011};
                    Double[] finalArray = new Double[arrayvalues.length];
                    for(int i=0; i<arrayvalues.length; i++){
                        finalArray[i] = new Double(arrayvalues[i]);
                    }

                    lambdaBox.setModel(new DefaultComboBoxModel(finalArray));
                    lambdaBox.setSelectedIndex(8);

                } else {

                    l1NormCheckBox.setSelected(true);
                    positiveOnlyCheckBox.setSelected(true);

                    for(ActionListener a: l1NormCheckBox.getActionListeners()) {
                        a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null) {
                            //Nothing need go here, the actionPerformed method (with the
                            //above arguments) will trigger the respective listener
                        });
                    }
                }
            }
        });

        l2NormCheckBox.addActionListener(new ActionListener() { // L2-norm method
            @Override
            public void actionPerformed(ActionEvent e) {

                // excludeBackgroundInFitCheckBox.setSelected(false);
                legendreCheckBox.setSelected(false);
                mooreCheckBox.setSelected(false);
                l1NormCheckBox.setSelected(false);
                SPICheckBox.setSelected(false);

                if (l2NormCheckBox.isSelected()){

                    methodInUseLabel.setText("L2-Norm with smoothness");

                    positiveOnlyCheckBox.setSelected(false);

                    lambdaBox.removeAllItems();
                    double[] arrayvalues = new double[]{0, 0.01, 0.1, 0.7, 1, 3, 7, 11, 31, 70, 93, 100, 130, 157, 231, 337, 431, 557, 637, 759, 931, 1017, 1311, 3103};
                    Double[] finalArray = new Double[arrayvalues.length];
                    for(int i=0; i<arrayvalues.length; i++){
                        finalArray[i] = new Double(arrayvalues[i]);
                    }
                    lambdaBox.setModel(new DefaultComboBoxModel(finalArray));

                    lambdaBox.setSelectedIndex(11);
                } else {

                    l1NormCheckBox.setSelected(true);
                    positiveOnlyCheckBox.setSelected(true);

                    for(ActionListener a: l1NormCheckBox.getActionListeners()) {
                        a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null) {
                            //Nothing need go here, the actionPerformed method (with the
                            //above arguments) will trigger the respective listener
                        });
                    }
                }

            }
        });

        excludeBackgroundInFitCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (!excludeBackgroundInFitCheckBox.isSelected() && mooreCheckBox.isSelected()){
                    prStatusLabel.setText("Constant Background always in use with Moore Method");
                    excludeBackgroundInFitCheckBox.setSelected(true);
                    excludeBackgroundInFitCheckBox.setForeground(Color.red);
                } else if (excludeBackgroundInFitCheckBox.isSelected()){

                    excludeBackgroundInFitCheckBox.setForeground(Color.red);

                    if(SPICheckBox.isSelected()){
                        SPICheckBox.setSelected(false);
                        l1NormCheckBox.setSelected(true);
                        prStatusLabel.setText("bckgrnd not available with SPI");
                    } else {
                        prStatusLabel.setText("Constant Background added");
                    }
                } else { // no background only available on DirectFT and moore coefficient L1
                    prStatusLabel.setText("No Constant Background");
                    excludeBackgroundInFitCheckBox.setForeground(Color.black);
                }
            }
        });

        mooreCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                positiveOnlyCheckBox.setSelected(false);

                if (mooreCheckBox.isSelected()){

                    l1NormCheckBox.setSelected(false);
                    legendreCheckBox.setSelected(false);
                    l2NormCheckBox.setSelected(false);
                    SPICheckBox.setSelected(false);
//                    excludeBackgroundInFitCheckBox.setSelected(true);
//                    excludeBackgroundInFitCheckBox.setForeground(Color.red);
                    methodInUseLabel.setText("Moore Method L1-Norm 2nd Derivative with constant background");

                    double[] arrayvalues = new double[]{0, 0.001, 0.1, 0.7, 3, 11, 31, 70, 93, 100, 331, 557, 637, 759, 931, 1017, 3011};
                    Double[] finalArray = new Double[arrayvalues.length];
                    for(int i=0; i<arrayvalues.length; i++){
                        finalArray[i] = new Double(arrayvalues[i]);
                    }

                    lambdaBox.setModel(new DefaultComboBoxModel(finalArray));
                    lambdaBox.setSelectedIndex(4);

                } else {

                    l1NormCheckBox.setSelected(true);

                    for(ActionListener a: l1NormCheckBox.getActionListeners()) {
                        a.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null) {
                            //Nothing need go here, the actionPerformed method (with the
                            //above arguments) will trigger the respective listener
                        });
                    }
                }

            }
        });
    }


    public JPanel getPanel(){

        qIQCheckBox.setSelected(false);

        prTable.clearSelection();
        if (prTable.isEditing()){
            prTable.getCellEditor().stopCellEditing();
            prTable.validate();
        }


        for(int i=0; i<collectionSelected.getTotalDatasets(); i++){
            if (collectionSelected.getDataset(i).getInUse()){
                collectionSelected.getDataset(i).getRealSpaceModel().setSelected(true);
            } else {
                collectionSelected.getDataset(i).getRealSpaceModel().setSelected(false);
            }
        }


        prModel.clear();
        prModel.addDatasetsFromCollection(collectionSelected); // add datasets and do initial Pr-estimation
        prModel.fireTableDataChanged();

        //System.out.println("ContentPane " + contentPane.getSize().getWidth() + " x " + contentPane.getSize().getHeight());

//        plotsPanel.setSize(new Dimension(-1,(int)(contentPane.getSize().getHeight()*2.0/3.0)));
        prSplitPane.setPreferredSize(new Dimension(-1,(int)(contentPane.getSize().getHeight()*2.0/3.0)));
        IofQPofRPlot iofqPofRplot = IofQPofRPlot.getInstance();
        iofqPofRplot.clear();
        iofqPofRplot.plot(collectionSelected, WORKING_DIRECTORY, prIntensity, qIQCheckBox.isSelected());

        PofRPlot pofRplot = PofRPlot.getInstance();
        pofRplot.clear();
        pofRplot.plot(collectionSelected, WORKING_DIRECTORY, prDistribution);


        return contentPane;
    }

    public class CheckBoxCellEditorRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {

        private JCheckBox checkBox;

        public CheckBoxCellEditorRenderer() {
            this.checkBox = new JCheckBox();
            checkBox.addActionListener(this);
            checkBox.setOpaque(false);
            checkBox.setBackground(Color.WHITE);
            checkBox.setMaximumSize(new Dimension(30,30));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

            checkBox.setSelected(Boolean.TRUE.equals(value));
            return checkBox;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {

                PrModel temp = (PrModel)table.getModel();
                RealSpace tempReal = temp.getDataset(row);
                tempReal.setSelected(!(Boolean)value);

                PofRPlot pofRPlot = PofRPlot.getInstance();
                pofRPlot.changeVisible(row, tempReal.getSelected());
                IofQPofRPlot iofQPofRPlot = IofQPofRPlot.getInstance();
                iofQPofRPlot.changeVisible(row, tempReal.getSelected());

            checkBox.setSelected(Boolean.TRUE.equals(value));
            return checkBox;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            stopCellEditing();
        }

        @Override
        public Object getCellEditorValue() {
            return checkBox.isSelected();
        }
    }


    class PrButtonEditorRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
        JButton button;
        private int rowID;
        private int colID;
        private String name;


        public PrButtonEditorRenderer(String name){
            this.button = new JButton();
            this.button.addActionListener(this);

            if (name == "Norm"){
                this.button.setText("N");
                this.button.setToolTipText("Normalize");
            } else if (name == "Refine"){
                this.button.setText("Refine");
                this.button.setToolTipText("Refine P(r) model");
            } else if (name == "2File"){
                this.button.setText("2File");
                this.button.setToolTipText("Write to File");
            } else if (name == "SEARCH"){
                this.button.setText("S(q)");
                this.button.setToolTipText("Structure Factor test");
                //Icon warnIcon = new ImageIcon("src/dmax_logo.png");
                //this.button = new JButton(warnIcon);
            }

            this.name = name;
            this.button.setFont(new Font("Verdana", Font.BOLD, 10));
            this.button.setBorderPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            button.setSelected(Boolean.TRUE.equals(value));
            this.button.setForeground(Color.BLACK);
            return button;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            button.setSelected(Boolean.TRUE.equals(value));
            rowID = row;
            colID = column;
            return button;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (this.colID == 12) {
                this.button.setBackground(Color.WHITE);
                this.button.setForeground(Color.GREEN);
                //normalize - divide P(r) by I-Zero
                //double invIzero = 1.0/prModel.getDataset(rowID).getIzero();
                double invIzero = 1.0/prModel.getDataset(rowID).integrateDistribution();
                //System.out.println("IZERO " + prModel.getDataset(rowID).getIzero());
                //
                // normalize to volume?
                // integrated area should be volume of particle
                //
                if (prModel.getDataset(rowID).getVolume() > 1){
                    invIzero *= prModel.getDataset(rowID).getVolume();
                    String ff = String.format("Normalized to Volume : %.0f", prModel.getDataset(rowID).getVolume());
                    prStatusLabel.setText(ff);
                } else {
                    invIzero = 1;
                    prStatusLabel.setText("Please determine Volume first to normalize");
                }
                //System.out.println(rowID + " => InvIzero " + invIzero);
                prModel.getDataset(rowID).setScale((float) invIzero);


            } else if (this.colID == 13){ // dmax search

                this.button.setBackground(Color.WHITE);
                this.button.setForeground(Color.GREEN);
                status.setText("");

                Thread refineIt = new Thread(){
                    public void run() {
                        StructureFactor SF = new StructureFactor(collectionSelected.getDataset(prModel.getDataset(rowID).getId()), WORKING_DIRECTORY.getWorkingDirectory());
                        SF.createPlot();
                    }

                };

                refineIt.start();

            } else if (this.colID == 14){
                //refine_Pr
                this.button.setBackground(Color.WHITE);
                this.button.setForeground(Color.GREEN);
                status.setText("");
                prStatusLabel.setText("Starting refinement of " + prModel.getDataset(rowID).getFilename());
                // launch a thread

                Thread refineIt = new Thread(){
                    public void run() {

                        final RefinePrManager refinePrMe = new RefinePrManager(
                                prModel.getDataset(rowID),
                                cpuCores,
                                Integer.parseInt(refinementRoundsBox.getSelectedItem().toString()),
                                Double.parseDouble(rejectionCutOffBox.getSelectedItem().toString()),
                                Double.parseDouble(lambdaBox.getSelectedItem().toString()),
                                Integer.parseInt(cBox.getSelectedItem().toString()),
                                mooreCheckBox.isSelected(),   //Moore
                                l1NormCheckBox.isSelected(),   // L1-norm indirect SVD
                                legendreCheckBox.isSelected(), // Legendre
                                l2NormCheckBox.isSelected(),           // L2 norm
                                SPICheckBox.isSelected(),                   // SVD
                                excludeBackgroundInFitCheckBox.isSelected(),
                                positiveOnlyCheckBox.isSelected());

                        prStatusLabel.setText("");
                        refinePrMe.setBar(progressBar, prStatusLabel);
                        refinePrMe.execute();

                        synchronized (refinePrMe) {
                            if (!refinePrMe.getIsFinished()) {
                                try {
                                    refinePrMe.wait();
                                } catch (InterruptedException ee) {
                                    // handle it somehow
                                    System.out.println("Catch " + ee.getMessage());
                                }
                            }
                        }

                        FileObject tempFile = new FileObject(new File(WORKING_DIRECTORY.getWorkingDirectory()));

                        String newname = tempFile.writePRFile(collectionSelected.getDataset(prModel.getDataset(rowID).getId()),
                                prStatusLabel,
                                collectionSelected.getDataset(prModel.getDataset(rowID).getId()).getFileName(),
                                WORKING_DIRECTORY.getWorkingDirectory(),
                                true
                        );

                        prStatusLabel.setText("Files written to " + WORKING_DIRECTORY.getWorkingDirectory() + ", ready to run DAMMIN/F");

                        runDatGnom(newname, collectionSelected.getDataset(prModel.getDataset(rowID).getId()).getRealRg());
                        // run gnom
                        prModel.fireTableDataChanged();
                    }

                };

                refineIt.start();

            } else if (this.colID == 15){
                // write Pr and Iq distributions toFile
                // create new instance of save and pass through datasets
                // get and return directory and filename
                SavePr tempSave = new SavePr();

                if (tempSave.getFileName().length() > 2){
                    FileObject tempFile = new FileObject(tempSave.getCurrentDir());
                    String newname = tempFile.writePRFile(collectionSelected.getDataset(prModel.getDataset(rowID).getId()),
                            prStatusLabel,
                            tempSave.getFileName(),
                            tempSave.getCurrentDir().getAbsolutePath(),
                            false
                    );

                    status.setText("Files written to " + WORKING_DIRECTORY.getWorkingDirectory() + ", ready to run DAMMIN/F");
                    runDatGnom(newname, collectionSelected.getDataset(prModel.getDataset(rowID).getId()).getRealRg());
                }
            }
        }

        @Override
        public Object getCellEditorValue() {
            return button.isSelected();
        }
    }

    class SavePr {
        private String name;
        private File currentDir;

        public SavePr(){
            JFileChooser c = new JFileChooser(WORKING_DIRECTORY.getWorkingDirectory());
            JTextField filename = new JTextField(), dir = new JTextField();
            // "Save" dialog:

            int rVal = c.showSaveDialog(contentPane);

            if (rVal == JFileChooser.APPROVE_OPTION) {
                filename.setText(c.getSelectedFile().getName());
                this.setFileName(filename.getText());
                dir.setText(c.getCurrentDirectory().toString());
                this.setCurrentDir(c.getCurrentDirectory());
            }

            if (rVal == JFileChooser.CANCEL_OPTION) {
                status.setText("Save Cancelled");
                filename.setText("");
                dir.setText("");
            }
        }


        private void setFileName(String text){
            this.name = text;
            System.out.println("Setting name to " + text);
        }

        private void setCurrentDir(File text){
            this.currentDir = text;
        }

        public String getFileName(){
            return this.name;
        }

        public File getCurrentDir(){
            return this.currentDir;
        }
    }


    class ColorEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
        Color currentColor;
        JComboBox pointSizes;
        JComboBox thickBox;
        JButton button;
        JColorChooser colorChooser;
        JDialog dialog;
        String tableModel;
        int data_row;
        protected static final String EDIT = "edit";

        public ColorEditor() {
            //Set up the editor (from the table's point of view),
            //which is a button.
            //This button brings up the color chooser dialog,
            //which is the editor from the user's point of view.
            button = new JButton();
            button.setActionCommand(EDIT);
            button.addActionListener(this);
            button.setBorderPainted(false);

            //Set up the dialog that the button brings up.
            colorChooser = new JColorChooser();
            dialog = JColorChooser.createDialog(button,
                    "Pick a Color or Change Size",
                    true,  //modal
                    colorChooser,
                    this,  //OK button handler
                    null); //no CANCEL button handler
        }

        /**
         * Handles events from the editor button and from
         * the dialog's OK button.
         */
        public void actionPerformed(ActionEvent e) {
            if (EDIT.equals(e.getActionCommand())) {
                //The user has clicked the cell, so
                //bring up the dialog.
                button.setBackground(currentColor);
                colorChooser.setColor(currentColor);
                JPanel preview = new JPanel();

                JLabel pointTitle = new JLabel("Point Size");
                String[] sizes = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "15", "17", "19", "21"};
                pointSizes = new JComboBox(sizes);
                preview.add(pointTitle);

                JLabel thicknessTitle = new JLabel(" | Line Stroke");
                String[] thicknesses = {"0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0"};
                thickBox = new JComboBox(thicknesses);
                preview.add(thicknessTitle);

                if (tableModel.contains("Analysis")){
                    Dataset temp = collectionSelected.getDataset(data_row);
                    int index=0;
                    for (int i=0; i<sizes.length; i++){
                        if (temp.getPointSize() == Integer.parseInt(sizes[i])){
                            index = i;
                        }
                    }

                    pointSizes.setSelectedIndex(index);
                    preview.add(pointSizes);

                    index=0;
                    for (int i=0; i<thicknesses.length; i++){
                        if (temp.getStroke().getLineWidth() == Float.parseFloat(thicknesses[i])){
                            index = i;
                        }
                    }

                    thickBox.setSelectedIndex(index);
                    preview.add(thickBox);

                } else if (tableModel.contains("Pr")){

                    RealSpace temp = prModel.getDataset(data_row);
                    int index=0;
                    for (int i=0; i<sizes.length; i++){
                        if (temp.getPointSize() == Integer.parseInt(sizes[i])){
                            index = i;
                        }
                    }

                    pointSizes.setSelectedIndex(index);
                    preview.add(pointSizes);

                    index=0;
                    for (int i=0; i<thicknesses.length; i++){
                        if (temp.getStroke().getLineWidth() == Float.parseFloat(thicknesses[i])){
                            index = i;
                        }
                    }

                    thickBox.setSelectedIndex(index);
                    preview.add(thickBox);
                }

                colorChooser.setPreviewPanel(preview);
                dialog.setVisible(true);

                //Make the renderer reappear.
                fireEditingStopped();

            } else { //User pressed dialog's "OK" button.
                currentColor = colorChooser.getColor();
            }
        }

        //Implement the one CellEditor method that AbstractCellEditor doesn't.
        public Object getCellEditorValue() {
            int thickIndex = thickBox.getSelectedIndex();
            int pointIndex = pointSizes.getSelectedIndex();

            float thickness;
            int pointSize;

            thickness = Float.parseFloat((String) thickBox.getSelectedItem());
            pointSize = Integer.parseInt( (String)pointSizes.getSelectedItem());
            Symbol newInfo = new Symbol(currentColor, thickness, pointSize);

            PofRPlot.getInstance().changeColor(data_row, currentColor, thickness);
            IofQPofRPlot.getInstance().changeColor(data_row, currentColor, thickness, pointSize);

            return newInfo;
            //return currentColor;
        }

        //Implement the one method defined by TableCellEditor.
        public Component getTableCellEditorComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     int row,
                                                     int column) {


//            if (table.getModel().getClass() == AnalysisModel.class){
//                System.out.println("Analysis Table Model" + table.getModel().getClass() + " | ");
//            } else if (table.getModel().getClass() == PrModel.class){
//                System.out.println("Pr Table Model" + table.getModel().getClass() + " | ");
//            }

            tableModel = table.getModel().getClass().toString();
            currentColor = (Color)value;
            data_row = row;
            return button;
        }
    }


    /*
     * ColorRenderer.java (compiles with releases 1.2, 1.3, and 1.4) is used by
     * TableDialogEditDemo.java.
     */
    class ColorRenderer extends JLabel implements TableCellRenderer {
        Border unselectedBorder = null;
        Border selectedBorder = null;
        boolean isBordered = true;

        public ColorRenderer(boolean isBordered) {
            this.isBordered = isBordered;
            setOpaque(true); //MUST do this for background to show up.
        }


        public Component getTableCellRendererComponent(JTable table, Object color,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {

            Color newColor = (Color)color;
            setBackground(newColor);
            if (isBordered) {
                if (isSelected) {
                    if (selectedBorder == null) {
                        selectedBorder = BorderFactory.createMatteBorder(2,5,2,5,
                                table.getSelectionBackground());
                    }
                    setBorder(selectedBorder);
                } else {
                    if (unselectedBorder == null) {
                        unselectedBorder = BorderFactory.createMatteBorder(2,5,2,5,
                                table.getBackground());
                    }
                    setBorder(unselectedBorder);
                }
            }


            setToolTipText("RGB value: " + newColor.getRed() + ", "
                    + newColor.getGreen() + ", "
                    + newColor.getBlue());

            return this;
        }
    }


    private class HeaderRenderer implements TableCellRenderer {

        DefaultTableCellRenderer renderer;

        public HeaderRenderer(JTable table) {
            renderer = (DefaultTableCellRenderer)
                    table.getTableHeader().getDefaultRenderer();
            renderer.setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            return renderer.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
        }
    }

    private void runDatGnom(String dat_file_name, double rg){
        // run datgnom
        String os = System.getProperty("os.name");
        String datgnom = "";
        Runtime rt = Runtime.getRuntime();

        if (os.indexOf("win") >=0 ){
            datgnom = "datgnom.exe";
        } else {
            datgnom = "datgnom";
        }

        String[] base_name = dat_file_name.split("\\.");
        Settings setit = Settings.getInstance();
        String atsas = setit.getATSASDir();

        try {
            System.out.println("Running datgnom: " + atsas+"/"+datgnom);

            ProcessBuilder pr = new ProcessBuilder(atsas+"/"+datgnom, "-r", Constants.Scientific1dot3e1.format(rg), "-o", base_name[0]+"_dg.out", WORKING_DIRECTORY.getWorkingDirectory()+ "/" + dat_file_name);
            pr.directory(new File(WORKING_DIRECTORY.getWorkingDirectory()));
            Process ps = pr.start();

//            BufferedReader input = new BufferedReader(new InputStreamReader(ps.getInputStream()));
//            String line=null;
//            String gnomdmax = "";
//            while((line=input.readLine()) != null) {
//
//                System.out.println(line);
//                String trimmed = line.trim();
//                String[] row = trimmed.split("[\\s\\t]+"); // CSV files could have a ", "
//                if (row[0].equals("dmax:") || row[0].equals("dmax")){
//                    gnomdmax = row[1];
//                }
//            }


            System.out.println("Finished datgnom: file " + base_name[0] + "_dg.out");

            File datFile = new File(WORKING_DIRECTORY.getWorkingDirectory() + "/"+base_name[0]+"_dg.out");
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(datFile)));
            String strLine;
            Pattern separator = Pattern.compile("[\\s\\t]+");

            double dmaxGnom = 0;
            while ((strLine = br.readLine()) != null) {
                String newString = strLine.replaceAll( "[\\s\\t]+", " " );
                String trimmed = newString.trim();
                if (trimmed.contains("Maximum") && trimmed.contains("characteristic") && trimmed.contains("size:")){
                    String[] row = separator.split(trimmed);
                    dmaxGnom = Double.parseDouble(row[3]);
                    break;
                }
            }

            String outlabel = (dmaxGnom > 1) ? String.format("=> autoGNOM DMAX :: %.2f", dmaxGnom) :  "autoGNOM did not run :: check settings";
            prStatusLabel.setText(outlabel);

            Modeling modeler = Modeling.getInstance();
            modeler.setOutFileLabel(WORKING_DIRECTORY.getWorkingDirectory() + "/"+ base_name[0] + "_dg.out");

            modeler.setWorkingDir(WORKING_DIRECTORY.getWorkingDirectory());

        } catch (IOException e) {
            System.out.println("Problem running datgnom from " + atsas);
            status.setText("Problem running datgnom from " + atsas);
            System.out.println(e.toString());
            e.printStackTrace();
        }
    }

}

