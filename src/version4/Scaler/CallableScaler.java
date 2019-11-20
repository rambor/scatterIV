package version4.Scaler;

import org.jfree.data.xy.XYSeries;
import version4.Dataset;

import java.util.concurrent.Callable;

/**
 * Created by xos81802 on 26/07/2017.
 */
public class CallableScaler implements Callable<Scaler> {

    private XYSeries reference, target, targetErrorSet;
    private Dataset targetDataset;
    private double lower, upper;
    private int startRefIndex, endRefIndex, startTarIndex, endTarIndex;

    public CallableScaler(
            XYSeries reference,
            Dataset targetDataset,
            int startRefIndex,
            int endRefIndex,
            double lower,
            double upper
    ){

        this.targetDataset = targetDataset;
        this.reference = new XYSeries("reference");
        this.target = new XYSeries("target");;
        this.targetErrorSet = new XYSeries("targetErrorSet");

        synchronized (reference){
            int total = reference.getItemCount();
            for(int i=0; i<total; i++){
                this.reference.add(reference.getDataItem(i));
            }
        }

        XYSeries tempTarget = targetDataset.getOriginalPositiveOnlyData();
        synchronized (tempTarget){
            int total = tempTarget.getItemCount();
            for(int i=0; i<total; i++){
                this.target.add(tempTarget.getDataItem(i));
            }
        }

        XYSeries tempTargetError = targetDataset.getOriginalPositiveOnlyError();
        synchronized (tempTargetError){
            int total = tempTargetError.getItemCount();
            for(int i=0; i<total; i++){
                this.targetErrorSet.add(tempTargetError.getDataItem(i));
            }
        }

        this.startRefIndex = startRefIndex;
        this.startTarIndex = targetDataset.getStart()-1;

        this.endRefIndex = endRefIndex;
        this.endTarIndex = targetDataset.getEnd()-1;

        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public Scaler call() throws Exception {

        Scaler temp = new Scaler(
                reference,
                target,
                targetErrorSet,
                startRefIndex,
                endRefIndex,
                startTarIndex,
                endTarIndex,
                lower,
                upper);

        targetDataset.setScaleFactor(1.0/temp.scale());
//        targetDataset.scalePlottedLog10IntensityData();
        //System.out.println("Target ID " + targetDataset.getId() + " => " +  temp.getScaleFactor());
        return temp;
    }
}
