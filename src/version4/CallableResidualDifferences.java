package version4;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.BinaryComparison.ResidualDifferences;

import java.util.List;
import java.util.concurrent.Callable;

public class CallableResidualDifferences implements Callable<ResidualDifferences> {


    private int refIndex, tarIndex, lags, order;
    private Number qmin, qmax;
    private XYSeries refXY, tarXY, tarError;

    public CallableResidualDifferences(List<XYDataItem> referenceSet, XYSeries targetSet, XYSeries targetError, Number qmin, Number qmax, int refIndex, int tarIndex, int lags, int order){
        this.refIndex = refIndex;
        this.tarIndex = tarIndex;
        this.qmin = qmin;
        this.qmax = qmax;
        this.lags = lags;

        this.refXY = new XYSeries("ref");
        this.tarXY = new XYSeries( "tar");
        this.tarError = new XYSeries("err");

        this.order = order;
        synchronized (referenceSet){
            int total = referenceSet.size();
            for(int i=0; i<total; i++){
                refXY.add(referenceSet.get(i));
            }
        }

        synchronized (targetSet){
            int total = targetSet.getItemCount();
            for(int i=0; i<total; i++){
                tarXY.add(targetSet.getDataItem(i));
            }
        }

        synchronized (targetError){
            int total = targetError.getItemCount();
            for(int i=0; i<total; i++){
                tarError.add(targetError.getDataItem(i));
            }
        }
    }

    public int getOrder(){return order;}


    @Override
    public ResidualDifferences call() throws Exception {
        ResidualDifferences temp = new ResidualDifferences(refXY, tarXY, tarError, qmin,  qmax, lags, refIndex, tarIndex, order);
        //temp.printTests(String.format("RESID r: %d %d", refIndex, tarIndex));
//        String flag = " => FALSE";
//        if ((temp.getShapiroWilkStatistic() > 0.75) && (temp.getDurbinWatsonStatistic() > 1.5 && temp.getDurbinWatsonStatistic() < 2.5)){
//            flag = " => TRUE";
//        }
//        System.out.println(refIndex + " " + tarIndex + " SW: " + temp.getSH() + " DW: " + temp.getDW() + flag);
        return temp;
    }
}
