package version4.tableModels;

import FileManager.WorkingDirectory;
import org.jfree.data.xy.XYSeries;
import version4.*;
import version4.ReportPDF.Report;
import version4.plots.PlotManualGuinier;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

public class AnalysisTable {

    private static JTable analysisTable;
    private static AnalysisTable singleton = new AnalysisTable();
    private static Collection collectionSelected;
    private static WorkingDirectory WORKING_DIRECTORY;
    private static Color mainBackgroundColor = Color.lightGray;

    /*
     private constructor
     */
    private AnalysisTable(){

    }
    /* Static 'instance' method */
    public static AnalysisTable getInstance( ) {
        return singleton;
    }

    public void setTableModel(JLabel status, WorkingDirectory wkd, Collection cs, Color mainBackgroundColor){
        collectionSelected = cs;
        WORKING_DIRECTORY = wkd;
        analysisTable = new JTable(new AnalysisModel(cs, status, wkd));
        //analysisTable.setDragEnabled(true);
        this.mainBackgroundColor = mainBackgroundColor;

        TableColumnModel tcm = analysisTable.getColumnModel();

        TableColumn tc = tcm.getColumn(4);
        tc.setCellEditor(new SpinnerEditor());

        tc = tcm.getColumn(5);
        tc.setCellEditor(new SpinnerEditor());

        tc = tcm.getColumn(13);
        tc.setCellEditor(new ButtonEditorRenderer());
        tc.setCellRenderer(new ButtonEditorRenderer());

        analysisTable.setRowHeight(30);
        analysisTable.setBackground(Color.WHITE);
        analysisTable.getColumnModel().getColumn(0).setPreferredWidth(10);
        analysisTable.getColumnModel().getColumn(1).setPreferredWidth(30);
        analysisTable.getColumnModel().getColumn(2).setPreferredWidth(35);
        analysisTable.getColumnModel().getColumn(3).setPreferredWidth(200);
        analysisTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        analysisTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        //analysisTable.getColumnModel().getColumn(6).setPreferredWidth(35);

        tc = analysisTable.getColumnModel().getColumn(2);
        tc.setCellEditor(new CheckBoxCellEditorRenderer(collectionSelected));
        tc.setCellRenderer(new CheckBoxCellEditorRenderer(collectionSelected));

        tc = analysisTable.getColumnModel().getColumn(0);
        tc.setCellEditor(new ColorEditor(collectionSelected));
        tc.setCellRenderer(new ColorRenderer(true));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment( JLabel.CENTER );
        rightRenderer.setHorizontalAlignment( JLabel.RIGHT );
        leftRenderer.setHorizontalAlignment( JLabel.LEFT );

        analysisTable.getColumnModel().getColumn(1).setCellRenderer( rightRenderer );
        analysisTable.getColumnModel().getColumn(4).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(5).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(7).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(8).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(9).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(10).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(11).setCellRenderer( centerRenderer );
        analysisTable.getColumnModel().getColumn(12).setCellRenderer(centerRenderer);

        JTableHeader header = analysisTable.getTableHeader();
        header.setDefaultRenderer(new HeaderRenderer(analysisTable));
        header.setBackground(this.mainBackgroundColor);
        header.setForeground(Color.white);
        header.setFont(new Font("Century Gothic", Font.PLAIN, 10));
        header.setPreferredSize(new Dimension(-1, 26));
        //header.setBorder(BorderFactory.createMatteBorder(1,1,1,1,Color.cyan));



        // file chooser for loading files into collection
        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Select All");
        deleteItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int total = collectionSelected.getDatasets().size();
                for(int i=0; i<total; i++){
                    collectionSelected.getDataset(i).setInUse(true);
                }
                ((AnalysisModel)analysisTable.getModel()).fireTableDataChanged();
            }
        });
        popupMenu.add(deleteItem);

        popupMenu.add(new JMenuItem(new AbstractAction("Select Highlighted") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                int[] rowIndex = analysisTable.getSelectedRows();

                //int endat = collectionSelected.getTotalDatasets();
                //System.out.println("Selecting Highlighted " + endat);

                int total = collectionSelected.getDatasets().size();
                for(int i=0; i<total; i++){
                    collectionSelected.getDataset(i).setInUse(false);
                }

                total = rowIndex.length;
                for(int i=0; i<total; i++){
                    collectionSelected.getDataset(rowIndex[i]).setInUse(true);
                }
                ((AnalysisModel)analysisTable.getModel()).fireTableDataChanged();
            }
        }));



        // add mouse functions, remove, select all, select none
        popupMenu.add(new JMenuItem(new AbstractAction("Create Report from Single Dataset") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                // get highlighted/moused over
                // open new object/window for estimating mass using several methods
                int index = analysisTable.getSelectedRow();
                if (index > -1){

                    Notes temp = new Notes(collectionSelected.getDataset(index), WORKING_DIRECTORY);
                    temp.pack();
                    temp.setVisible(true);

                    Report tempreport = new Report(collectionSelected.getDataset(index), WORKING_DIRECTORY);

                } else {
                    status.setText("Highlight only a single file");
                }
            }
        }));

        popupMenu.add(new JMenuItem(new AbstractAction("DeSelect Highlighted") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                int[] rowIndex = analysisTable.getSelectedRows();

                int total = rowIndex.length;
                for(int i=0; i<total; i++){
                    collectionSelected.getDataset(rowIndex[i]).setInUse(false);
                }
                ((AnalysisModel)analysisTable.getModel()).fireTableDataChanged();
            }
        }));


        popupMenu.add(new JMenuItem(new AbstractAction("Remove") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.

                int[] rowIndex = analysisTable.getSelectedRows();
                int start = rowIndex.length - 1;

                for(int j = start; j >= 0; j--){ // count in reverse and remove selected dataset
                    int index = rowIndex[j];
                    collectionSelected.removeDataset(index);
                    ((AnalysisModel)analysisTable.getModel()).remove(index);
                }


                //update collection ids
                int total = collectionSelected.getDatasets().size();
                for(int h=0; h<total; h++){
                    collectionSelected.getDataset(h).setId(h);
                }

                ((AnalysisModel)analysisTable.getModel()).fireTableDataChanged();
            }
        }));


        popupMenu.add(new JMenuItem(new AbstractAction("Structure Factor Test") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.

                int index = analysisTable.getSelectedRow();

                if (index > -1){

                    // select dataset with form factor
                    StructureFactorTest temp = new StructureFactorTest(collectionSelected, collectionSelected.getDataset(index), WORKING_DIRECTORY);
                    temp.pack();
                    temp.setVisible(true);
                }
            }
        }));

        popupMenu.add(new JMenuItem(new AbstractAction("FIND DMAX"){
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                int index = analysisTable.getSelectedRow();

                if (index > -1){
                    // set qmax from spinner
                    Dataset tempDataset = collectionSelected.getDataset(index);
                    //System.out.println("Dmax set " + tempDataset.getAllData().getItemCount() + " NAN :: " + tempDataset.getEnd());
                    double qmax = tempDataset.getAllData().getX(tempDataset.getEnd()-1).doubleValue();
                    FindDmax tt = new FindDmax(collectionSelected.getDataset(index).getRealSpaceModel(), qmax, WORKING_DIRECTORY);
                }
            }
        }));

        popupMenu.add(new JMenuItem(new AbstractAction("Similarity Test") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.

                int total = 0;
                for(int i=0; i<collectionSelected.getTotalDatasets(); i++){
                    if (collectionSelected.getDataset(i).getInUse()){
                        total+=1;
                    }
                }

                if (total > 2){
                    // select dataset with form factor
                    try {
                        RatioSimilarityTest temp = new RatioSimilarityTest(collectionSelected, 0.011, 0.178);
                        temp.makePlot();
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                } else {
                    status.setText("Please select more than two datasets");
                }

            }
        }));

        analysisTable.setComponentPopupMenu(popupMenu);

    }

    public AnalysisModel getModel(){
        return (AnalysisModel) analysisTable.getModel();
    }

    public JTable getTable(){
        return analysisTable;
    }

    public class ButtonEditorRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {
        JButton button;
        private int rowID;
        private int colID;

        public ButtonEditorRenderer(){
            this.button = new JButton();
            button.addActionListener(this);
            button.setMaximumSize(new Dimension(10,10));
            button.setPreferredSize(new Dimension(10,10));
            button.setText("G");
            button.setFont(new Font("Verdana", Font.BOLD, 12));
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
            if (this.colID == 13) {
                this.button.setBackground(Color.WHITE);
                this.button.setForeground(Color.GREEN);

                PlotManualGuinier manualGuinierPlot = new PlotManualGuinier("Guinier Plot", collectionSelected.getDataset(rowID), WORKING_DIRECTORY.getWorkingDirectory());
                manualGuinierPlot.plot((AnalysisModel)analysisTable.getModel());
            }
        }

        @Override
        public Object getCellEditorValue() {
            return button.isSelected();
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


    public class SpinnerEditor extends DefaultCellEditor implements ChangeListener {
        private JSpinner spinner;
        JSpinner.DefaultEditor editor;
        JTextField textField;
        boolean valueSet;
        private int rowID;
        private int colID;
        private int priorValue;
        private MiniPlots mini;

        // Initializes the spinner - Constructor.
        public SpinnerEditor() {
            super(new JTextField());
            spinner = new JSpinner();
            editor = ((JSpinner.DefaultEditor)spinner.getEditor());
            textField = editor.getTextField();

            textField.addFocusListener( new FocusListener() {
                public void focusGained( FocusEvent fe ) {
                    System.err.println("Got focus");

                }
                public void focusLost( FocusEvent fe ) {
                    System.out.println("FocusLost " + collectionSelected.getDataset(rowID).getData().getX(0) + " | value " + spinner.getValue());
                }
            });

            textField.addActionListener( new ActionListener() {
                public void actionPerformed( ActionEvent ae ) {
                    stopCellEditing();
                }
            });

            spinner.addChangeListener((ChangeListener) this);
        }

        public void stateChanged(ChangeEvent e){
            Dataset dataset = collectionSelected.getDataset(rowID);
            int temp = (Integer)this.spinner.getValue();
            int current = (Integer) this.spinner.getValue() - 1;
            int limit;

            if (this.colID == 4){

                double test = dataset.getData().getMaxX(); // plotted data
                int valueOfSpinner = (Integer)this.spinner.getValue();
                //
                if ((valueOfSpinner < 1) || valueOfSpinner >= dataset.getData().getItemCount() || ( dataset.getOriginalLog10Data().getX( valueOfSpinner ).doubleValue() >= test)){
                    this.spinner.setValue(1);
                    this.priorValue = 1;
                    dataset.setStart(1);
                } else {
                    //moving up or down?
                    int direction = valueOfSpinner - dataset.getStart();
                    if (direction > 0) {
                        if (direction == 1) {
                            dataset.getData().remove(0);
                            // check other plots and update
                        } else {
                            limit = valueOfSpinner - this.priorValue;
                            // keep removing first point
                            // current is the last point
                            XYSeries currentData = dataset.getData();
                            currentData.delete(0, limit);
                        }

                    } else if (direction < 0){

                        if (direction == -1) {
                            dataset.getData().add(dataset.getScaledLog10DataItemAt(current));
                        } else {
                            // keep adding points up until currentValue
                            int start = current;
                            XYSeries tempData = dataset.getData();
                            int indexInOriginal = dataset.getOriginalLog10Data().indexOf(tempData.getX(0));

                            for (int i = start; i< indexInOriginal; i++){
                                tempData.add(dataset.getScaledLog10DataItemAt(i));
                            }
                        }
                    }
                    this.priorValue = valueOfSpinner;
                    dataset.setStart(temp);
                }

            } else if (colID == 5) {
                limit = dataset.getOriginalLog10Data().getItemCount();
                if ((Integer)this.spinner.getValue() >= limit){
                    this.spinner.setValue(limit);
                    this.priorValue = limit;

                    XYSeries tempData = dataset.getData();
                    tempData.clear();
                    for(int i = (dataset.getStart()-1); i < limit; i++){
                        tempData.add(dataset.getScaledLog10DataItemAt(i));
                    }

                    dataset.setEnd(limit);

                } else {
                    int direction = temp - dataset.getEnd();
                    //moving up or down?
                    if (direction < 0) {
                        if (direction == -1) {
                            //remove last point
                            dataset.getData().remove(dataset.getData().getItemCount()-1);
                        } else {
                            limit = (Integer) temp - dataset.getEnd();
                            //current is the last point
                            int start = dataset.getData().getItemCount() - 1;
                            int stop = start + limit;
                            // keep removing last point
                            dataset.getData().delete(stop, start);
                        }

                    } else if (direction > 0){
                        if (direction == 1) {
                            dataset.getData().add(dataset.getScaledLog10DataItemAt(current));
                        } else {
                            // keep adding points up until currentValue
                            //Dataset tempDataset = collectionSelected.getDataset(rowID);
                            //XYSeries tempData = tempDataset.getData();
                            XYSeries tempData = dataset.getData();
                  //          int last = current;
                  //          double lastPlottedValue = tempData.getMaxX();
                  //          int indexOfLastPlottedValue = tempData.indexOf(lastPlottedValue) + 1;
                            tempData.clear();
                            for(int i = (dataset.getStart()-1); i < temp; i++){
                                tempData.add(dataset.getScaledLog10DataItemAt(i));
                            }
                        }
                    }
                    this.priorValue = temp;
                    dataset.setEnd(temp);
                }
            }
            mini.updatePlots(dataset); // 3 mini plots on top
        }

        @Override
        public void addCellEditorListener(CellEditorListener l) {
            super.addCellEditorListener(l);    //To change body of overridden methods use File | Settings | File Templates.
        }

        // Prepares the spinner component and returns it.
        public Component getTableCellEditorComponent( JTable table, Object value, boolean isSelected, int row, int column) {

            rowID = row;
            colID = column;

            if (colID == 4){
                priorValue = collectionSelected.getDataset(rowID).getStart();
            } else if (colID == 5){
                priorValue = collectionSelected.getDataset(rowID).getEnd();
            }

            spinner.setValue(priorValue);

            SwingUtilities.invokeLater( new Runnable() {
                public void run() {
                    textField.requestFocus();
                }
            });
            return spinner;
        }

        public boolean isCellEditable( EventObject eo )
        {
            //System.err.println("isCellEditable");
            if ( eo instanceof KeyEvent ) {
                KeyEvent ke = (KeyEvent)eo;
                System.err.println("key event: "+ke.getKeyChar());
                textField.setText(String.valueOf(ke.getKeyChar()));
                //textField.select(1,1);
                //textField.setCaretPosition(1);
                //textField.moveCaretPosition(1);
                valueSet = true;
            } else {
                valueSet = false;
            }
            return true;
        }

        // Returns the spinners current value.
        public Object getCellEditorValue() {
            return spinner.getValue();
        }

        public boolean stopCellEditing() {
            System.err.println("Stopping edit");
            try {
                editor.commitEdit();
                //spinner.commitEdit();
            } catch ( java.text.ParseException e ) {
                JOptionPane.showMessageDialog(null,
                        "Invalid value, discarding.");
            }
            return super.stopCellEditing();
        }

        public void setMiniPlot(MiniPlots plot){
            this.mini = plot;
        }

    } // end of spinnerEditor

    private static class HeaderRenderer implements TableCellRenderer {

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
}
