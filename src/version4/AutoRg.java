package version4;

import org.apache.commons.math3.util.FastMath;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by robertrambo on 24/05/2017.
 */
public class AutoRg {

    private XYSeries qSquaredData;

    private XYSeries inputData;

    private double rg=0;
    private double i_zero=0;
    private double i_zero_error;  //Izero Error
    private double rg_error;  //Rg Error
    private double correlation_coefficient;
    private Number qminFinal;
    private Number qmaxFinal;
    private int keptStartAt=0;


    /**
     * Data must not be previously transformed
     * Should only contain positive values as we will take the log of the number
     * @param data full range of putative Guinier region
     * @param startAt initial guess, must be greater than 0
     */
    public AutoRg(XYSeries data, int startAt){
        inputData = data;
        this.autoRgTransformIt(startAt);
        this.autoRg();
    }


    /**
     * transforms data to q^2 vs ln I(q)
     * @param startAt index from spinner
     */
    private void autoRgTransformIt(int startAt){
        qSquaredData = new XYSeries("qq");

        int total = inputData.getItemCount();

        int starthere = startAt-1;
        XYDataItem tempItem;
        for (int i=starthere; i<total; i++){
            tempItem = inputData.getDataItem(i);
            if (tempItem.getYValue() > 0){  // no negative values
                qSquaredData.add(tempItem.getXValue()*tempItem.getXValue(), Math.log(tempItem.getYValue()));
            }
        }
    }


    /**
     * Failure results in Rg = 0;
     */
    private void autoRg() {
        //int last = data.getItemCount()-1;
        double temp_resi, tempMedian, median = Double.POSITIVE_INFINITY;
        double tempRg;

        XYDataItem lastItem, item;
//        ArrayList<Double> resList = new ArrayList<>();
        ArrayList<Double> rgList = new ArrayList<>();

        // calculate line between first and last points
        double lowerqlimitSquared = 0.15*0.15; // q2
        double qRgUp = 1.31*1.31;

        // find the index of the upper q-value that is less than lowerqlimit
        // in this case find the index for which q_i < 0.15
        int lastAtLimit = 0;
        while(qSquaredData.getX(lastAtLimit).doubleValue() < lowerqlimitSquared){
            lastAtLimit++;
        }
        int last = lastAtLimit;
        // get a rough estimate of Rg using data within qmax = 0.15;
        // only use the first and last points to define the line
        // shrinking window, try all pairs up to a limit
        int first = 1;
        XYDataItem tempDataItem = qSquaredData.getDataItem(first);
        double slope;
        double intercept;
        //ArrayList<Double> resList = new ArrayList<>(last);
        List resList = Arrays.asList(new Double[last+1]);
        // calculate a progressively shrinking line using only first and last point
        int rescount;
        int smallestWindow = 17;
        while( tempDataItem.getXValue() < lowerqlimitSquared && first < (lastAtLimit-10)){  // minimum line is defined by 7 points
            // fit line to first and last point, calculate Rg, determine gRg limit
            while (last > first+smallestWindow) {
                lastItem = qSquaredData.getDataItem(last);
                // calculate line using only first and last points
                slope = (lastItem.getYValue() - tempDataItem.getYValue())/(lastItem.getXValue() - tempDataItem.getXValue());
                intercept = lastItem.getYValue() - slope *lastItem.getXValue();
                tempRg = -3.0* slope;
                // use the line to calculate residuals
                if (tempRg > 0 && lastItem.getXValue()*tempRg < 0.95){  // means we have a reasonable limit
//                    resList.clear();
                    rescount =0;
                    for(int i=first; i<last; i++){
                        item = qSquaredData.getDataItem(i);
                        temp_resi = item.getYValue() - (slope *item.getXValue() + intercept);
//                        resList.add(temp_resi*temp_resi);
                        resList.set(rescount, temp_resi*temp_resi);
                        rescount++;
                    }
                    // get median
                    tempMedian = Statistics.calculateMedian(resList.subList(0,rescount), true);
                    //tempMedian = Statistics.calculateMedian(resList, true);
                    if (tempMedian < median){
                        //rgList.add(FastMath.sqrt(tempRg));
                        rgList.add(tempRg);
                        median = tempMedian;
                    }
                }
                last -= 5; // remove the last point
            }
            last = lastAtLimit;
            //
            first +=3; // set first to next point
            tempDataItem = qSquaredData.getDataItem(first);
            // could average the 3 neighboring points?
        }

        tempRg = FastMath.sqrt(Statistics.calculateMedian(rgList, true)); // rough estimate of Rg

        //create vector
        double xvalue;
        double c0, c1;

        double minResidual = Double.POSITIVE_INFINITY;
        double[] x_range;
        double[] y_range;
        double[] residuals3;
        //rg = Math.sqrt(-3.0*slope);
        double rg2 = tempRg*tempRg;

        int endAt = 0;
        int startAtLimit = 0;

        // how many points are within upperlimit?
        int itemCount = qSquaredData.getItemCount();
        for(int i=0; i < itemCount; i++){
            XYDataItem dat = qSquaredData.getDataItem(i);
            xvalue = dat.getXValue();

            if ((xvalue*rg2 <= qRgUp)) {
                endAt++;
            } else {
                break;
            }
        }

        int sizeOfArray = endAt - startAtLimit + 1;
        // perform least median square fitting
        double sumX=0, sumY=0, sumXY=0, sumXX=0, sumYY=0;

        double errorIntercept;
        double errorSlope;

        if (sizeOfArray > 5){ // using a window of fixed size (13 data points), fit repeated lines
            int window = 31;
            x_range = new double[window];
            y_range = new double[window];
            // double[] keptResiduals = new double[0];
            keptStartAt=startAtLimit;
            double keptSlope=0, keptIntercept=0;

            int upTO = startAtLimit + window;
            residuals3 = new double[endAt];
            double[] keptResiduals = new double[endAt];

            while(upTO < endAt){

                for (int i = 0; i < window; i++) {
                    XYDataItem dat = qSquaredData.getDataItem(i+startAtLimit);
                    x_range[i] = dat.getXValue();  // q^2
                    y_range[i] = dat.getYValue();  // ln(I(q))
                }

                double[] param3 = Functions.leastSquares(x_range, y_range);
                c1 = param3[0];
                c0 = param3[1];

                if (c1 < 0){ // slope has to be negative
                    for (int v = 0; v < endAt; v++) { // determine residuals for all points in qSquared up to endAt
                        XYDataItem dat = qSquaredData.getDataItem(v);
                        residuals3[v] = Math.pow((dat.getYValue() - (c1 * dat.getXValue() + c0)), 2);
                    }

                    Arrays.sort(residuals3);
                    double median_test = Functions.median(residuals3);

                    if (median_test < minResidual) {
                        minResidual = median_test;
                        System.arraycopy(residuals3, 0, keptResiduals, 0, endAt);
                        keptStartAt = startAtLimit;
                        keptSlope = c1;
                        keptIntercept = c0;
                    }
                }

                startAtLimit++;
                upTO = startAtLimit + window;
            }

            double s_o = 1.4826 * (1.0 + 5.0 / (endAt - 2 - 1)) * Math.sqrt(minResidual);
            double inv_s_o = 1.0/s_o;

            // create final dataset for final fit
            int count = 0;
            ArrayList<Integer> keepers = new ArrayList<Integer>();

            XYDataItem dataItem;
            for (int i = 0; i < endAt; i++) { // get the indices of the data items to keep
                dataItem = qSquaredData.getDataItem(i);
                //if (Math.abs((dataItem.getYValue() - (keptSlope * dataItem.getXValue() + keptIntercept))) * inv_sigma < 2.5) {
                if (Math.abs((dataItem.getYValue() - (keptSlope * dataItem.getXValue() + keptIntercept))) * inv_s_o < 2.5) {
                    // decide which ln[I(q)] values to keep
                    keepers.add(i);
                    count++;
                }
            }

            // determines values to keep for fitting to determine Rg and I(zero)
            double[] final_x = new double[count];
            double[] final_y = new double[count];
            XYSeries finalDataSetForFit = new XYSeries("FinalDataset");

            for (int i = 0; i < count; i++) {
                dataItem=qSquaredData.getDataItem(keepers.get(i));
                finalDataSetForFit.add(dataItem);
                final_x[i] = dataItem.getXValue();
                final_y[i] = dataItem.getYValue();
                sumX+=dataItem.getXValue();
                sumY+=dataItem.getYValue();
                sumXX+=dataItem.getXValue()*dataItem.getXValue();
                //final_w[i] = errors.getY(keep).doubleValue();
                sumYY+=dataItem.getYValue()*dataItem.getYValue();
                sumXY+=dataItem.getXValue()*dataItem.getYValue();
            }

            double[] param3 = Functions.leastSquares(final_x, final_y);
            slope = param3[0];
            intercept = param3[1];
            errorSlope = param3[2];
            errorIntercept = param3[3];

            if (slope < 0){
                rg = Math.sqrt(-3.0 * slope);
                i_zero = Math.exp(intercept);
                i_zero_error=i_zero* errorIntercept;  //Izero Error
                rg_error=1.5* errorSlope *Math.sqrt(1.0/3.0*1.0/rg);  //Rg Error
                correlation_coefficient = Math.abs(count*sumXY - sumX*sumY)/(Math.sqrt((count*sumXX-sumX*sumX)*(count*sumYY-sumY*sumY)));
            } else {
                rg = 0;
                i_zero = 0;
                i_zero_error = 0;
                rg_error = 0;
                correlation_coefficient = 0;
            }

            double startLowq = Math.sqrt(final_x[0]);
            for(int i=0; i<inputData.getItemCount(); i++){
                if (inputData.getX(i).doubleValue() >= startLowq){
                    qminFinal = inputData.getX(i);
                    keptStartAt = i;
                    break;
                }
            }

            startLowq = Math.sqrt(final_x[final_x.length-1]);
            for(int i=0; i<inputData.getItemCount(); i++){
                if (inputData.getX(i).doubleValue() >= startLowq){
                    qmaxFinal = inputData.getX(i);
                    break;
                }
            }

        } else {  // not enough points, so just fit a line to what we have
            // ignore first three points, take the next 5 and fit
            int arrayLimit = 7, arrayIndex=0;
            x_range = new double[arrayLimit];
            y_range = new double[arrayLimit];
            //w_range = new double[5];

            for (int i = 3; i < itemCount; i++) {
                XYDataItem dat = qSquaredData.getDataItem(i);
                xvalue = dat.getXValue(); // q^2
                //x^2
                if (arrayIndex < arrayLimit) {
                    // x_data[i] = Math.pow(xvalue, 2);
                    x_range[arrayIndex] = xvalue;  // q^2
                    // ln(y)
                    y_range[arrayIndex] = dat.getYValue();  // ln(I(q))
                    // ln(y)*error*y
                    // w_range[arrayIndex] = yvalue * errors.getY(i).doubleValue() * Math.exp(yvalue);
                    // original data
                    sumX+=dat.getXValue();
                    sumY+=dat.getYValue();
                    sumXX+=dat.getXValue()*dat.getXValue();
                    //final_w[i] = errors.getY(keep).doubleValue();
                    sumYY+=dat.getYValue()*dat.getYValue();
                    sumXY+=dat.getXValue()*dat.getYValue();
                    arrayIndex++;
                } else {
                    break;
                }
            }
            // fit line and use as default fit
            double[] param3 = Functions.leastSquares(x_range, y_range);
            slope = param3[0];
            intercept = param3[1];
            errorSlope = param3[2];
            errorIntercept = param3[3];

            if (slope < 0){
                rg = Math.sqrt(-3.0 * slope);
                i_zero = Math.exp(intercept);
                i_zero_error=i_zero* errorIntercept;  //Izero Error
                rg_error=1.5* errorSlope *Math.sqrt(1/3.0*1/rg);  //Rg Error
                correlation_coefficient = Math.abs(arrayIndex*sumXY - sumX*sumY)/(Math.sqrt((arrayIndex*sumXX-sumX*sumX)*(arrayIndex*sumYY-sumY*sumY)));
            } else { // if it fails, get a zero Rg
                rg = 0;
                i_zero = 1;
                i_zero_error = 1;
                rg_error = 100;
                correlation_coefficient = 0;
            }


            double startLowq = Math.sqrt(x_range[0]);
            for(int i=0; i<inputData.getItemCount(); i++){
                if (inputData.getX(i).doubleValue() >= startLowq){
                    qminFinal = inputData.getX(i);
                    break;
                }
            }

            startLowq = Math.sqrt(x_range[x_range.length-1]);
            for(int i=0; i<inputData.getItemCount(); i++){
                if (inputData.getX(i).doubleValue() >= startLowq){
                    qmaxFinal = inputData.getX(i);
                    break;
                }
            }
        }
    }

    /**
     * Failure of autoRg returns zero
     * @return
     */
    public double getRg(){
        return rg;
    }

    public double getI_zero() {
        return i_zero;
    }

    public double getRg_error() {
        return rg_error;
    }

    public double getI_zero_error() {
        return i_zero_error;
    }

    public double getCorrelation_coefficient(){
        return correlation_coefficient;
    }

    public int getStartIndex() { return keptStartAt; }

    public Number getQminFinal() {
        return qminFinal;
    }

    public Number getQmaxFinal(){
        return qmaxFinal;
    }
}
