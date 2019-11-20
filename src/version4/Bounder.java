package version4;

import org.jfree.data.xy.XYSeries;

/**
 * Created by xos81802 on 04/08/2017.
 */
public class Bounder implements Runnable {

    private Dataset data;
    private int column;
    private int total;
    private double limit;
    //AnalysisModel analysisModel;

    public Bounder(Dataset data, int column, double limit){
        //this.analysisModel = analysisModel;
        this.data = data;
        this.total = data.getOriginalPositiveOnlyData().getItemCount();
        this.column = column;
        this.limit = limit;
    }

    @Override
    public void run() {
        XYSeries dataXY = data.getOriginalPositiveOnlyData();

        if (column == 4){
            // find q value that is >= limit
            int setLimit = 0;

            for(int j=0; j<total; j++){
                // sets the index to Original data (non-negative)
                if (dataXY.getX(j).doubleValue() >= limit){
                    setLimit = j+1;
                    break;
                }
            }

            //data.setStart(setLimit);
            data.lowBoundPlottedLog10IntensityData(setLimit);
            // analysisModel.setValueAt(setLimit, data.getId(), column);
        } else if (column == 5){
            int lowerBound = data.getStart();
            int setLimit = total;

            for(int j=(total-1); j>lowerBound; j--){
                if (dataXY.getX(j).doubleValue() <= limit){
                    setLimit = j;
                    break;
                }
            }
            //data.setEnd(setLimit);
            data.upperBoundPlottedLog10IntensityData(setLimit);
            //analysisModel.setValueAt(setLimit, data.getId(), column);
        }
        //data.scalePlottedLog10IntensityData();
        //analysisModel.fireTableDataChanged();
    }
}
