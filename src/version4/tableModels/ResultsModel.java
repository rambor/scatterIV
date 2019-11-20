package version4.tableModels;

import version4.Constants;
import version4.Dataset;

import javax.swing.table.AbstractTableModel;
import java.util.LinkedList;

/**
 * Created by robertrambo on 18/01/2016.
 */
public class ResultsModel extends AbstractTableModel {

    private final LinkedList<Dataset> datalist;

    private String[] columnNames = new String[]{"", "", "I(0)", "real", "<html>R<sub>g</sub></html>", "real", "<html>V<sub>c</sub></html>", "Volume", "MW 1.07", "MW 1.1","Protein", "RNA", "r", "<html>d<sub>max</sub></html>", "<html>R<sub>c</sub></html>", "<html>P<sub>x</sub></html>"};

    public ResultsModel(){
        datalist = new LinkedList<Dataset>();
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
    public Object getValueAt(int row, int col) {
        Dataset dataset = (Dataset) datalist.get(row);
        double q;
        int index;
        // anytime row is clicked, this method is executed
        switch (col){
            case 0:
                return row+1;
            case 1:
                return dataset.getFileName();
            case 2:
                return Constants.Scientific1.format(dataset.getGuinierIzero());
            case 3:
                return Constants.Scientific1.format(dataset.getRealIzero());
            case 4: //spinner
                return Constants.TwoDecPlace.format(dataset.getGuinierRg());
            case 5: //spinner
                return Constants.TwoDecPlace.format(dataset.getRealRg());
            case 6: //Fit file - rendered as a checkbox
                return Constants.OneDecPlace.format(dataset.getVC());
            case 7:
                String vol = "<html><p><b>" + Constants.Scientific1.format(dataset.getPorodVolume()) + "</b></p><p>" + Constants.Scientific1.format(dataset.getPorodVolumeReal()) + "</p></html>";
                return vol;
            //return (int)dataset.getPorodVolume();
            case 8:
                String mass11 = "<html><p><b>" +  Constants.Scientific1.format(dataset.getPorodVolumeMass1p1()) + "</b></p><p>" + Constants.Scientific1.format(dataset.getPorodVolumeRealMass1p1()) + "</p></html>";
                return mass11;
            case 9:
                String mass137 = "<html><p><b>" + Constants.Scientific1.format(dataset.getPorodVolumeMass1p37()) + "</b></p><p>" + Constants.Scientific1.format(dataset.getPorodVolumeRealMass1p37()) + "</p></html>";
                return mass137;
            case 10:
                String massP = "<html><p><b>" + Constants.Scientific1.format(dataset.getMassProtein()) + "</b></p><p>" + Constants.Scientific1.format(dataset.getMassProteinReal()) + "</p></html>";
                //return scientific.format(dataset.getMassProtein());
                return massP;
            case 11:
                String massR = "<html><p><b>" + Constants.Scientific1.format(dataset.getMassRna()) + "</b></p><p>" + Constants.Scientific1.format(dataset.getMassRnaReal()) + "</p></html>";
                return massR;
            case 12:
                return Constants.OneDecPlace.format(dataset.getAverageR());
            case 13:
                return (int)dataset.getDmax();
            case 14:
                return Constants.TwoDecPlace.format(dataset.getRc());
            case 15:
                return Constants.OneDecPlace.format(dataset.getPorodExponent());
            default:
                return null;
        }

    }

    public void addDataset(Dataset dataset){
        datalist.add(dataset);
        fireTableRowsInserted(datalist.size()-1, datalist.size()-1);
        //fireTableDataChanged();
    }

    public LinkedList<Dataset> getDatalist(){
        return datalist;
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
        return false;
    }
}