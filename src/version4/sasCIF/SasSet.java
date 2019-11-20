package version4.sasCIF;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Dataset;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class SasSet {

    private XYSeries masterSeries;
    private String tempHeader, dataHeader;
    private int totalDataColumns;
    private Dataset masterDataset;
    private ArrayList<String> masterList;
    private int totalInMasterList;
    private DecimalFormat formatter = new DecimalFormat("0.000000000");
    private ArrayList<String> filenames;

    public SasSet(Dataset masterDataset){

        this.masterDataset = masterDataset;
        this.masterSeries = masterDataset.getAllData();

        totalInMasterList = masterSeries.getItemCount();

        for(int i=0; i<totalInMasterList; i++){
            String qvalue = formatter.format(masterSeries.getX(i));
            masterList.add(String.format("%-6d %s %.5E %.5E", i+1, qvalue, masterSeries.getY(i), masterDataset.getAllDataError().getY(i)));
        }
        totalDataColumns = 1;
        filenames.add(masterDataset.getFileName());
    }


    private void setHeader(int id) {

        tempHeader = String.format("# %n");
        tempHeader += String.format("# REMARK 265 sas_set %n");
        tempHeader += String.format("_sas_set.id %d %n", id);
        tempHeader += String.format("_sas_set.total_data_columns %d %n", totalDataColumns);
        tempHeader += String.format("loop_ %n");
        tempHeader += String.format("_sas_set.ordinal %n");
        tempHeader += String.format("_sas_set.column %n");
        tempHeader += String.format("_sas_set.filename %n");
    }


    private void setDataHeader(){
        tempHeader = String.format("# %n");
        tempHeader += String.format("# REMARK 265 sas_set %n");
        tempHeader += String.format("_sas_set.sas_set_id %d %n");
        tempHeader += String.format("_sas_set.total_data_columns %d %n", totalDataColumns);
        tempHeader += String.format("loop_ %n");
        tempHeader += String.format("_sas_set.ordinal %n");
        tempHeader += String.format("_sas_set.column %n");
        tempHeader += String.format("_sas_set.filename %n");
    }


    public void addDataset(Dataset dataset){

        XYSeries tempSeries = dataset.getAllData();
        XYSeries tempErrorSeries = dataset.getAllDataError();

        for(int i=0; i<totalInMasterList; i++){
            XYDataItem item = tempSeries.getDataItem(i);

            int index = tempSeries.indexOf(item.getX());
            String text = masterList.get(i);

            if (index > -1){ // if not found in master,
                masterList.set(i, String.format("%s %.5E %.5E", text, tempSeries.getY(index), tempErrorSeries.getY(index)));
            } else {
                masterList.set(i, String.format("%s %s %s", text, "    .    ", "    .    "));
            }
        }

        filenames.add(dataset.getFileName());
        totalDataColumns++;
    }
}
