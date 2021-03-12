package version4;

import FileManager.CSVFile;
import com.fasterxml.jackson.core.JsonToken;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.SEC.SECFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;

public class SASSignalTrace {

    private String name;
    private SECFile secFile;
    private CSVFile absorbanceSignalFile;
    private XYSeries signalSeries;
    private XYSeries originalSignalSeries;
    private XYSeries originalAbsorbanceSeries;
    private XYSeries absorbanceTrace;
    private XYSeries interpolatedAbsorbanceTrace;
    private XYSeries normalizedIzeroes;
    private double exposure_time, dead_time, baseline_absorbance, baseline_saxs, delay_shift;
    private double mod_max_at_x, max_at_x, max_at_y, max_abs_y, max_abs_at_x;



    public SASSignalTrace(String name){
        this.name = name;
        exposure_time = 0.0;
        dead_time = 0.0;
    }

    /**
     * creates reference to original Signal series in secFile.
     * x-value is frame index
     * y-value is integrated signal
     *
     * @param secFileToLoad
     * @throws IOException
     */
    public void setSECFile(File secFileToLoad) throws IOException {
        secFile = new SECFile(secFileToLoad);
        // set signal plot
        originalSignalSeries = secFile.getSignalSeries();
        signalSeries = new XYSeries(name+"_sas_signal");
        max_at_y = Double.NEGATIVE_INFINITY;

        XYDataItem item;
        for(int i=0; i<originalSignalSeries.getItemCount(); i++){
            signalSeries.add(originalSignalSeries.getDataItem(i));
            item = originalSignalSeries.getDataItem(i);

            if (item.getYValue() > max_at_y){
                max_at_x = item.getXValue();
                max_at_y = item.getYValue();
            }
        }
    }

    /**
     * x-value is frame index
     * y-value is integrated signal
     *
     * @return
     */
    public XYSeries getSecSignalSeries(){
        return signalSeries;
    }

    public XYSeries getAbsorbanceTrace(){
        return absorbanceTrace;
    }

    public void setAbsorbanceTrace(File csvFileToLoad){
        absorbanceSignalFile = new CSVFile(csvFileToLoad);
        absorbanceTrace = new XYSeries(name+"_absorbance");
        //loadAbsorbanceTraceFromFile();
    }

    public String getAbsorbanceSignalFileName(){ return absorbanceSignalFile.getFilename();}
    public String getSECSAXSFileName(){ return secFile.getFilename(); }

    private void loadAbsorbanceTraceFromFile(){

        absorbanceTrace.clear();
        XYSeries data = absorbanceSignalFile.getData();
        XYDataItem item;
        max_abs_y = Double.NEGATIVE_INFINITY;

        for(int i=0; i< data.getItemCount(); i++){
            item = data.getDataItem(i);
            if (item.getYValue() > max_abs_y){
                max_abs_at_x = item.getXValue();
                max_abs_y = item.getYValue();
            }
        }

        for(int i=0; i< data.getItemCount(); i++){
            item = data.getDataItem(i);
            absorbanceTrace.add(item.getX().doubleValue(), item.getYValue()/max_abs_y*max_at_y);
        }
    }


    public void changeSASSignalDomain(double exposure_time, double dead_time){
        this.exposure_time = exposure_time;
        this.dead_time = dead_time;
        double adjust = exposure_time + dead_time;
        signalSeries.clear();
        for(int i=0; i< originalSignalSeries.getItemCount(); i++){
            XYDataItem item = originalSignalSeries.getDataItem(i);
            signalSeries.add(item.getXValue()*adjust, item.getYValue());
        }
        mod_max_at_x = max_at_x * adjust;
    }


    public void alignTraces(){
        try {

            loadAbsorbanceTraceFromFile();
            XYSeries temp = absorbanceTrace.createCopy(0, absorbanceTrace.getItemCount()-1);

            XYDataItem item;
            absorbanceTrace.clear();

            delay_shift = mod_max_at_x-max_abs_at_x;
            System.out.println("Delay time shift :: " + delay_shift);
            for(int i=0; i < temp.getItemCount(); i++){
                item = temp.getDataItem(i);
                absorbanceTrace.add(item.getX().doubleValue() + delay_shift, item.getYValue() );
            }

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
    }

    /**
     * startIndex and endIndex is actual value from the domain (not indices)
     *
     * @param startIndex
     * @param endIndex
     */
    public void alignBaseLines(double startIndex, double endIndex){

        System.out.println(startIndex + " :: " + endIndex);

        ArrayList<Double> absorbanceValues = new ArrayList<>();
        ArrayList<Double> saxsValues = new ArrayList<>();

        double sumAbsY=0, sumSASY=0;
        double countAbs = 0.0f, countSAS = 0.0f;
        int counter=0;
        XYDataItem item;
        for(int i=0; i<absorbanceTrace.getItemCount(); i++){
            item = absorbanceTrace.getDataItem(i);
            if (item.getXValue() > startIndex && item.getXValue() < endIndex){
                sumAbsY += item.getYValue();
                absorbanceValues.add(item.getYValue());
                countAbs += 1.0d;
            } else if (item.getXValue() > endIndex){
                break;
            }
        }


        for(int i=0; i<signalSeries.getItemCount(); i++){
            item = signalSeries.getDataItem(i);
            if (item.getXValue() > startIndex && item.getXValue() < endIndex){
                sumSASY += item.getYValue();
                saxsValues.add(item.getYValue());
                countSAS += 1.0d;
            } else if (item.getXValue() > endIndex){
                break;
            }
        }

        // baseline shift
        Collections.sort(saxsValues);
        Collections.sort(absorbanceValues);
        baseline_absorbance = Functions.median(absorbanceValues);
        baseline_saxs = Functions.median(saxsValues);
        //double diff = sumSASY/countSAS - sumAbsY/countAbs;
        double diff = baseline_saxs - baseline_absorbance;

        for(int i=0; i<absorbanceTrace.getItemCount(); i++){
            item = absorbanceTrace.getDataItem(i);
            absorbanceTrace.updateByIndex(i, item.getYValue() - baseline_absorbance);
        }

        for(int i=0; i<signalSeries.getItemCount(); i++){
            item = signalSeries.getDataItem(i);
            signalSeries.updateByIndex(i, item.getYValue() - baseline_saxs);
        }


        //max_abs_y += diff;
        max_abs_y -= baseline_absorbance;
    }

    public void create_interpolation_set(){

        // first and last interpolated values of SAS-SEC should be bordered by at least 4 points
        int start_index=1;
        int start_abs_index=0;
        double startx=signalSeries.getX(start_index).doubleValue();

        // since I am interpolating UV signal to the SAXS signal, I need to insure there are at least 4 points
        // on left (beginning) side of first interpolated point due to Kriging algorithm
        label:
        while(start_index < signalSeries.getItemCount()){
            for(int i=0; i<absorbanceTrace.getItemCount(); i++){
                XYDataItem item = absorbanceTrace.getDataItem(i);
                if (item.getXValue() > startx && i > 3){
                    start_abs_index = i;
                    break label;
                } else if (item.getXValue() > startx && i == 0){
                    break;
                }
            }

            start_index++;
            startx = signalSeries.getX(start_index).doubleValue();
        }

        // since I am interpolating UV signal to the SAXS signal, I need to insure there are at least 4 points
        // on right side of last interpolated point due to Kriging algorithm
        int end_index = signalSeries.getItemCount()-1;
        final int abs_end = absorbanceTrace.getItemCount()-1;
        int end_abs_index = abs_end;
        double end_x = signalSeries.getX(end_index).doubleValue();

        label2:
        while(end_index > 1){
            for(int i=abs_end; i>0; i--){
                XYDataItem item = absorbanceTrace.getDataItem(i);
                if (item.getXValue() < end_x && (abs_end - i) > 3){
                    end_abs_index = i;
                    break label2;
                }
            }

            end_index--;
            end_x = signalSeries.getX(end_index).doubleValue();
        }

        System.out.println(start_abs_index + " ABS " + end_abs_index + " " + abs_end);
        // now for each value from start_index to end_index interpolate absorbance
        start_abs_index -= 3;
        int window = 6;
        double[] xvals = new double[window];
        double[] yvals = new double[window];

        XYDataItem item, abs_item;
        interpolatedAbsorbanceTrace = new XYSeries("Interpolated");

        for(int i=start_index; i<end_index; i++){

            item = signalSeries.getDataItem(i);
            double val_x = item.getXValue();

            for(int j=0; j<window; j++){
                abs_item = absorbanceTrace.getDataItem(j+start_abs_index);
                xvals[j] = abs_item.getXValue();
                yvals[j] = abs_item.getYValue();
            }

            Double[] interpolated_y = Functions.interpolate(xvals, yvals, val_x);
            interpolatedAbsorbanceTrace.add(item.getX(), interpolated_y[1]);

            start_abs_index += 1;
            //increase start_abs_index until next value is reached
            for(int m=start_abs_index; m<abs_end; m++){
                if (absorbanceTrace.getX(m).doubleValue() > signalSeries.getX(i+1).doubleValue()){
                    start_abs_index = m - 3;
                    break;
                }
            }
        }
    }

    public double getBaselineAbsorbance(){return baseline_absorbance;}

    public XYSeries getInterpolatedAbsorbanceTrace(){ return interpolatedAbsorbanceTrace;}

    /**
     * convolved x-values is not frame number but time
     * @param amp
     * @param convolved_values
     */
    public void normalizeAvailableIzeros(double amp, XYSeries convolved_values, int[] indices_in_use){

        double calc;

        int total = secFile.getTotalFrames();
        XYSeries signalSeries = secFile.getSignalSeries();

        double invAmp = 1.0/amp;
        for(int i=0; i<indices_in_use.length; i++){
            calc = secFile.getIzerobyIndex(indices_in_use[i])/(convolved_values.getY(i).doubleValue()*invAmp);
            System.out.println(i + " " + calc + " " + convolved_values.getY(i) + " " +  secFile.getIzerobyIndex(indices_in_use[i]));
        }

        System.out.println("Totals " + total + " " + signalSeries.getItemCount());
    }


    /**
     * convolved x-values is not frame number but time
     * @param convolved_values
     */
    public void normalizeAvailableIzeros(double[] convolved_values, int[] indices_in_use){

        double calc;

        int in_use;
        XYSeries signalSeries = secFile.getSignalSeries();
        normalizedIzeroes = new XYSeries("normalized Izeroes");

        for(int i=0; i<indices_in_use.length; i++){
            in_use = indices_in_use[i];
            calc = secFile.getIzerobyIndex(in_use)/convolved_values[i];
            normalizedIzeroes.add(in_use, calc);
            //System.out.println(i + " " + calc + " " + convolved_values[i] + " " +  secFile.getIzerobyIndex(indices_in_use[i]) + " " + signalSeries.getY(indices_in_use[i]) + " " + calc2);
        }
    }


    public SECFile getSecFile() {
        return secFile;
    }

    public XYSeries getNormalizedIzeroes(){
        return normalizedIzeroes;
    }

    public double getBaseline_saxs(){
        return baseline_saxs;
    }
}
