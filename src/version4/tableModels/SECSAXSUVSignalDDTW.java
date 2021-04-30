package version4.tableModels;

import com.dtw.TimeWarpInfo;
import com.dtw.WarpPath;
import com.matrix.ColMajorCell;
import com.timeseries.TimeSeries;
import com.util.DistanceFunctionFactory;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Constants;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SECSAXSUVSignalDDTW {

    XYSeries saxs_trace;
    XYSeries uv_trace;
    double[] saxs_differences;
    double[] saxs_smoothed;
    double[] uv_differences;
    double[] uv_smoothed;
    int[] indices_in_use;
    double[] assigned_absorbances;
    int shift = 5;
    int boundary;

    /*
     *
     */
    public SECSAXSUVSignalDDTW (XYSeries saxs_signal, XYSeries uv_signal, int startIndex, int endIndex) {

        saxs_trace = saxs_signal;
        uv_trace = uv_signal;

        boundary = 2;
        saxs_differences = new double[saxs_signal.getItemCount() - 2*boundary]; // remove 2x2 from each end due to finite differences calculations
        uv_differences = new double[uv_signal.getItemCount() - 2*boundary]; // remove 2x2 from each end due to finite differences calculations

        this.setCentralDifferences();
    }


    private void setCentralDifferences(){

        // rescale data so that each has maximum of 1
        int total = saxs_trace.getItemCount();
        double inv_maxy = 1.0/saxs_trace.getMaxY();

        double[] values = new double[saxs_trace.getItemCount()];

        double element, sum=0.0, sum2=0.0;
        for(int i=0; i<saxs_trace.getItemCount(); i++){
            values[i] = saxs_trace.getY(i).doubleValue();
        }

        // calculate derivative
        double inv_h = 1.0/(12*(saxs_trace.getMaxX() - saxs_trace.getMinX())/(saxs_trace.getItemCount()-1));
        int count=0;
        for(int i=boundary; i<saxs_trace.getItemCount()-boundary; i++){
            //saxs_differences[count] = (saxs.getY(i - 2).doubleValue() - 8*saxs.getY(i - 1).doubleValue() + 8*saxs.getY(i + 1).doubleValue() - saxs.getY(i + 2).doubleValue())*inv_h;
            element = (values[i - 2] - 8*values[i - 1] + 8*values[i + 1] - values[i + 2])*inv_h;
//            element = 0.5*((values[i] - values[i - 1]) + 0.5*(values[i+1] - values[i - 1]));
            saxs_differences[count] = element;
            sum += element;
            sum2 += element*element;
            count++;
        }

        double average = sum/(double)count;
        double ave2 = sum2/(double)count;
        double stdev = Math.sqrt(ave2 - average*average);

        for(int i=0; i<count; i++){ // standardize data
            saxs_differences[i] = (saxs_differences[i] - average)/stdev;
        }

        // uv signal -> rescale so that max is 1.0
        values = new double[uv_trace.getItemCount()];
        for(int i=0; i<uv_trace.getItemCount(); i++){
            values[i] = uv_trace.getY(i).doubleValue();
        }

        inv_h = 1.0/(12*(uv_trace.getMaxX() - uv_trace.getMinX())/(uv_trace.getItemCount()-1));
        count=0;
        sum = 0.0d;
        sum2 = 0.0d;
        for(int i=boundary; i<uv_trace.getItemCount()-boundary; i++){
//            uv_differences[count] = (uv.getY(i - 2).doubleValue() - 8*uv.getY(i - 1).doubleValue() + 8*uv.getY(i + 1).doubleValue() - uv.getY(i + 2).doubleValue())*inv_h;
            element = (values[i - 2] - 8*values[i - 1] + 8*values[i + 1] - values[i + 2])*inv_h;
//            element = 0.5*((values[i] - values[i - 1]) + 0.5*(values[i+1] - values[i - 1]));
            uv_differences[count] = element;
            sum += element;
            sum2 += element*element;
            count++;
        }

        // standardize UV data
        average = sum/(double)count;
        ave2 = sum2/(double)count;
        stdev = Math.sqrt(ave2 - average*average);

        for(int i=0; i<count; i++){ // standardize data
            uv_differences[i] = (uv_differences[i] - average)/stdev;
        }

        // perform averaging to smooth data
        int start_at = (shift-1)/2; // shift is required due to averaging window
        saxs_smoothed = new double[saxs_differences.length-(shift-1)];
        final double inv = 1.0/(double)shift;
        for(int i=start_at; i<saxs_differences.length - start_at; i++){
            sum = 0.0;
            for(int j=0; j<shift; j++){
                sum += saxs_differences[i+j - start_at];
            }
            saxs_smoothed[i-start_at] = sum*inv;
            //System.out.println(i + " " + saxs_smoothed[i-start_at]);
        }

        printArray("saxs_smooothed", saxs_smoothed);

        uv_smoothed =new double[uv_differences.length-(shift-1)];
        for(int i=start_at; i<uv_differences.length - start_at; i++){
            sum = 0.0;
            for(int j=0; j<shift; j++){
                sum += uv_differences[i+j -start_at];
            }
            uv_smoothed[i-start_at] = sum*inv;
            //System.out.println(i + " " + uv_smoothed[i-start_at]);
        }

        printArray("uv_smoothed", uv_smoothed);
    }


    public void calculateTimeWarp(){
        // keep saxs signal as outer loop
        TimeSeries saxs_series = new TimeSeries(saxs_smoothed);
        TimeSeries uv_series = new TimeSeries(uv_smoothed);

//        TimeSeries saxs_series = new TimeSeries(saxs_smoothed);
//        TimeSeries uv_series = new TimeSeries(uv_smoothed);

        System.out.println("ratio of uv to saxs :: " + Math.round(uv_smoothed.length/saxs_smoothed.length));
        int radius = 10;//2*Math.round(uv_smoothed.length/saxs_smoothed.length);
//        TimeWarpInfo info = com.dtw.FastDTW.getWarpInfoBetween(saxs_series, uv_series, radius, DistanceFunctionFactory.getDistFnByName("ManhattanDistance"));
        TimeWarpInfo info = com.dtw.FastDTW.getWarpInfoBetween(saxs_series, uv_series, radius, DistanceFunctionFactory.getDistFnByName("EuclideanDistance"));
//        TimeWarpInfo info = com.dtw.DTW.getWarpInfoBetween(saxs_series, uv_series, DistanceFunctionFactory.getDistFnByName("EuclideanDistance"));

        WarpPath path = info.getPath();
        int saxs_index, uv_index;
        XYDataItem saxs_item, uv_item;

        System.out.println("Warp Path:     " + info.getPath());
        System.out.println("saxs " + saxs_trace.getItemCount());
        // vertical shift
        final double vertical_offset = 2.5;
        FileWriter fstream;

        int delta = 2 + (shift-1)/2;;

        indices_in_use = new int[saxs_smoothed.length];
        assigned_absorbances = new double[saxs_smoothed.length];

        for(int i=0; i<saxs_smoothed.length; i++){
            ArrayList matchingIndices =  path.getMatchingIndexesForI(i);

            double sum = 0.0;
            for(int j=0; j<matchingIndices.size(); j++){
                sum += uv_trace.getY((int)matchingIndices.get(j) + delta).doubleValue();
            }

            indices_in_use[i] = i+delta;
            assigned_absorbances[i] = sum/(double)(matchingIndices.size());

            System.out.println((i+delta) + " " + assigned_absorbances[i] + " " + saxs_trace.getY(i+delta).doubleValue());
        }


        try {
            fstream = new FileWriter("ddtw.txt");
            BufferedWriter out = new BufferedWriter(fstream);
            for (int i=0; i<path.size(); i++){
                ColMajorCell cell = path.get(i);
                uv_index = cell.getRow();
                saxs_index = cell.getCol();

                saxs_item = saxs_trace.getDataItem(saxs_index + delta);
                uv_item = uv_trace.getDataItem(uv_index + delta);

                out.write( String.format("%.3e %.3e %n", saxs_item.getXValue(), (saxs_item.getYValue() + vertical_offset)));
                out.write( String.format("%.3e %.3e %n %n", uv_item.getXValue(), uv_item.getYValue()));
            }

            System.out.println("points " + uv_trace.getItemCount() + " " + saxs_trace.getItemCount());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public double[] getAssigned_absorbances(){ return assigned_absorbances;}
    public int[] getIndices_in_use(){ return indices_in_use;}

    private double getBaseline(XYSeries inputValues){
        double[] values = new double[inputValues.getItemCount()];

        for(int i=0; i<inputValues.getItemCount(); i++){
            values[i] = inputValues.getY(i).doubleValue();
        }

        double median = getMedian(values);

        double[] deviations = new double[inputValues.getItemCount()];

        for(int i=0; i<inputValues.getItemCount(); i++){
            deviations[i] = Math.abs(values[i] - median);
        }

        double mad = getMedian(deviations);

        double threshold = 1.0/(1.4826*mad);
        double testvalue, sum = 0.0;
        double count = 0.0;

        for(int i=0; i<inputValues.getItemCount(); i++){
            testvalue = deviations[i];
            if (testvalue*threshold < 2){
                sum += testvalue;
                count += 1.0d;
            }
        }

        return  sum/count;
    }

    private double getMedian(double[] values){

        int totalElements = values.length;
        Arrays.sort(values);

        if (totalElements % 2 == 0) {
            double sumOfMiddleElements = 0.5*(values[totalElements / 2] + values[totalElements / 2 - 1]);
            // calculate average of middle elements
            return 0.5*(values[totalElements / 2] + values[totalElements / 2 - 1]);
        } else {
            // get the middle element
            return values[totalElements / 2];
        }
    }


    private void printArray(String name, double[] values ){
        int length = values.length;
        try {
            FileWriter fstream = new FileWriter(name+"_derivative.txt");
            BufferedWriter out = new BufferedWriter(fstream);

            for (int i=0; i < length; i++){
                out.write( String.format("%d %.3e %n", i, values[i]));
            }
            out.close();
            fstream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
