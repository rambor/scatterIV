package version4.tableModels;

import FileManager.WorkingDirectory;
import version4.Collection;
import version4.Dataset;
import version4.LogIt;
import version4.Symbol;
import version4.plots.PlotData;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.logging.Level;

/**
 * Created by robertrambo on 11/01/2016.
 */
public class AnalysisModel extends AbstractTableModel implements ChangeListener, PropertyChangeListener {

    private final LinkedList<Dataset> datalist;
    private WorkingDirectory currentWorkingDirectory;
    private JLabel status;
    private Collection collection;

    DecimalFormat oneDecPlac = new DecimalFormat("0.0");
    DecimalFormat fourDecPlac = new DecimalFormat("0.0000");
    DecimalFormat scientific = new DecimalFormat("0.00E0");
    DecimalFormat twoOneFormat = new DecimalFormat("00.0");

    private String[] columnNames = new String[]{"", "", "", "", "start", "end", "I(0)", "<html>R<sub>g</sub></html>", "<html>d<sub>max</sub></html>", "<html>R<sub>c</sub></html>", "<html>P<sub>x</sub></html>", "<html>V<sub>p</sub></html>", "Scale", "G"};

    public AnalysisModel(Collection cs, JLabel status, WorkingDirectory cwd){
        this.status = status;
        this.currentWorkingDirectory = cwd;
        currentWorkingDirectory.addPropertyChangeListener(this);
        datalist = new LinkedList<>();
        this.collection = cs;
    }

    public int getRowCount() {
        return datalist.size();
    }

    public void remove(int row){
        datalist.remove(row);
        this.fireTableRowsDeleted(row, row);
    }

    public void clear(){
        datalist.clear();
        this.fireTableDataChanged();
    }

    @Override
    public String getColumnName(int col) {
        return columnNames[col];
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setValueAt(Object obj, int row, int col){
        Dataset dataset = datalist.get(row);

        if (col == 12) {
            if (!isNumber((String) obj)) {
                status.setText("Scale factor " + (dataset.getId() + 1) + " is not a number. Enter a number.");
                return;
            } else {
                status.setText("*");
            }

            dataset.setScaleFactor(Double.parseDouble((String) obj)); // what should this trigger?
            dataset.scalePlottedLog10IntensityData();

        } else if (col == 3) {
            LogIt.log(Level.INFO, "Copying " + dataset.getFileName()+ " to " + (String)obj);
            dataset.copyAndRenameDataset((String)obj, currentWorkingDirectory.getWorkingDirectory());
            status.setText("Renamed(copied) original file");
        } else if (col == 4){
            dataset.setStart((Integer)obj);
        } else if (col == 5){
            dataset.setEnd((Integer)obj);
        } else if (col ==0) {
            Symbol temp = (Symbol)obj;
            dataset.setColor(temp.getColor());
            dataset.setStroke(temp.getStroke());
            dataset.setPointSize(temp.getPointSize());
            // rebuild plot
        }
        fireTableCellUpdated(row, col);
    }

    @Override
    public Object getValueAt(int row, int col) {

        Dataset dataset = (Dataset) datalist.get(row);
        int index;
        // anytime row is clicked, this method is executed
        switch (col){
            case 0: //color
                return dataset.getColor();
            case 1:
                return row+1;
            case 2: // rendered as a checkbox
                return dataset.getInUse();
            case 3:
                return dataset.getFileName();
            case 4: //spinner
                index = dataset.getStart();
                return index;
            case 5: //spinner
                index = dataset.getEnd();
                return index;
            case 6:
                return scientific.format(dataset.getGuinierIzero());
            case 7:
                return twoOneFormat.format(dataset.getGuinierRg());
            case 8:
                return (int)dataset.getDmax();
            case 9:
                return twoOneFormat.format(dataset.getRc());
            case 10:
                return oneDecPlac.format(dataset.getPorodExponent());
            case 11:
                return (int)dataset.getPorodVolume();
            case 12:
                return scientific.format(dataset.getScaleFactor());
            case 13:  // Manual Guinier button
                return true;
            default:
                return null;
        }
    }

    public void addDataset(Dataset dataset){
        datalist.add(dataset);
        fireTableRowsInserted(datalist.size()-1, datalist.size()-1);
        //fireTableDataChanged();
    }

    /*
     * JTable uses this method to determine the default renderer/
     * editor for each cell.  If we didn't implement this method,
     * then the last column would contain text ("true"/"false"),
     * rather than a check box.
     */
    public boolean isCellEditable(int row, int col) {
        //Note that the data/cell address is constant,
        //no matter where the cell appears onscreen.
        //editable 0,2,4,5,6,13,14
        if (col==0 || col==2 || col==3 || col==4 || col==5 || col==12 || col==13) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        //To change body of implemented methods use File | Settings | File Templates.
        //System.out.println("AnalysisModel: " + e);
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


    public void propertyChange(PropertyChangeEvent evt) {
        // determine if the property of the temperature
        // object is the one that changed
        if (evt.getSource() == currentWorkingDirectory && evt.getPropertyName() == "WorkingDirectory") {
            WorkingDirectory t = (WorkingDirectory) evt.getSource();
            // get the new value object
            Object o = evt.getNewValue();

            String newCWD;
            //System.out.println("Change in CWD :: " + currentWorkingDirectory.getWorkingDirectory());
            if (o == null) {
                // go back to the object to get the temperature
                newCWD = t.getWorkingDirectory();
            } else {
                // get the new temperature value
                newCWD = ((String)o).toString();
            }
        } else if (evt.getPropertyName() == "mainCollection"){

            if (datalist.size() < collection.getTotalDatasets()){ // need to add
                this.addDataset(collection.getLast());
            } else if (datalist.size() > collection.getTotalDatasets()){ // need to remove

            }
        }
    }

}
