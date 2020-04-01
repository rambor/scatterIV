package version4;

import org.jfree.data.xy.XYSeries;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Created by robertrambo on 16/01/2016.
 */
public class Averager {

    private Collection collectionInUse;
    private XYSeries averaged;
    private XYSeries averagedError;

    public Averager(Collection collection){
        this.collectionInUse = collection;

        // should move StatMethods function to internal method for multi-threading
        ArrayList<XYSeries> results = StatMethods.weightedAverageDatasets(this.collectionInUse);

        try {
            averaged = results.get(0).createCopy(0, results.get(0).getItemCount()-1);
            averagedError = results.get(1).createCopy(0, results.get(0).getItemCount()-1);;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }


    public Averager(Collection collection, boolean useQminQmax){

        this.collectionInUse = collection;
        ArrayList<XYSeries> results = StatMethods.weightedAverageDatasetsWithinLimits(this.collectionInUse);

        try {
            averaged = results.get(0).createCopy(0, results.get(0).getItemCount()-1);
            averagedError = results.get(1).createCopy(0, results.get(0).getItemCount()-1);;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    public XYSeries getAveraged(){
        return averaged;
    }

    public XYSeries getAveragedError(){
        return averagedError;
    }

}