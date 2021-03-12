package version4.ConvolutionFunctions;

import com.matrix.ColMajorCell;
import org.apache.commons.io.FilenameUtils;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.data.xy.XYSeries;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class Derivative {

    XYSeries saxs_trace;
    XYSeries uv_trace;
    double[] saxs_differences;
    double[] saxs_smoothed;
    double[] uv_differences;
    double[] uv_smoothed;
    int shift = 5;
    String saxs_filename;
    String uv_filename;

    public Derivative(XYSeries saxs_signal, XYSeries uv_signal, String saxs_name, String UV_name){
        saxs_trace = saxs_signal;
        uv_trace = uv_signal;

        saxs_filename = FilenameUtils.removeExtension(saxs_name);
        uv_filename = FilenameUtils.removeExtension(UV_name);

        saxs_differences = new double[saxs_signal.getItemCount() - 4]; // remove 2x2 from each end due to finite differences calculations
        uv_differences = new double[uv_signal.getItemCount() - 4]; // remove 2x2 from each end due to finite differences calculations

        this.setCentralDifferencesSAXS();
        this.setCentralDifferencesUV();
    }


    private void setCentralDifferencesSAXS(){
        // rescale data so that each has maximum of 1
        double[] values = new double[saxs_trace.getItemCount()];

        double element, sum=0.0, sum2=0.0;
        for(int i=0; i<saxs_trace.getItemCount(); i++){
            values[i] = saxs_trace.getY(i).doubleValue();
        }

        // calculate derivative
        double inv_h = 1.0/(saxs_trace.getMaxX() - saxs_trace.getMinX())/(saxs_trace.getItemCount()-1);
        int count=0;
        for(int i=2; i<saxs_trace.getItemCount()-2; i++){
            element = (values[i - 2] - 8*values[i - 1] + 8*values[i + 1] - values[i + 2])*inv_h;
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
        }

        int delta = 2 + (shift-1)/2;;
        String filename = saxs_filename + "_deriv.txt";
        System.out.println("saxs " + saxs_smoothed.length + " " + saxs_trace.getItemCount());

        try {
            FileWriter fstream = new FileWriter(filename);
            BufferedWriter out = new BufferedWriter(fstream);
            for (int i=0; i<saxs_smoothed.length; i++){
                out.write( String.format("%.5e %.4e %n", saxs_trace.getX(i + delta).doubleValue(), saxs_smoothed[i]));
//                System.out.println(i + " " + saxs_trace.getX(i + delta).doubleValue() + " " + saxs_smoothed[i]);
            }

            out.close();
            fstream.close();
            System.exit(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void setCentralDifferencesUV(){

        double element, sum=0.0, sum2=0.0;
        int count=0;

        // uv signal -> rescale so that max is 1.0
        double[] values = new double[uv_trace.getItemCount()];
        for(int i=0; i<uv_trace.getItemCount(); i++){
            values[i] = uv_trace.getY(i).doubleValue();
        }

        double inv_h = 1.0/(uv_trace.getMaxX() - uv_trace.getMinX())/(uv_trace.getItemCount()-1);
        count=0;
        sum = 0.0d;
        sum2 = 0.0d;
        for(int i=2; i<uv_trace.getItemCount()-2; i++){
            element = (values[i - 2] - 8*values[i - 1] + 8*values[i + 1] - values[i + 2])*inv_h;
            uv_differences[count] = element;
            sum += element;
            sum2 += element*element;
            count++;
        }

        // standardize UV data
        double average = sum/(double)count;
        double ave2 = sum2/(double)count;
        double stdev = Math.sqrt(ave2 - average*average);

        for(int i=0; i<count; i++){ // standardize data
            uv_differences[i] = (uv_differences[i] - average)/stdev;
        }

        // perform averaging to smooth data
        int start_at = (shift-1)/2; // shift is required due to averaging window
        final double inv = 1.0/(double)shift;

        uv_smoothed =new double[uv_differences.length-(shift-1)];
        for(int i=start_at; i<uv_differences.length - start_at; i++){
            sum = 0.0;
            for(int j=0; j<shift; j++){
                sum += uv_differences[i+j -start_at];
            }
            uv_smoothed[i-start_at] = sum*inv;
            //System.out.println(i + " " + uv_smoothed[i-start_at]);
        }

        int delta = 2 + (shift-1)/2;;
        String filename = uv_filename + "_deriv.txt";
        try {
            FileWriter fstream = new FileWriter(filename);
            BufferedWriter out = new BufferedWriter(fstream);
            for (int i=0; i<uv_smoothed.length; i++){
                out.write( String.format("%.3e %.3e %n", uv_trace.getX(i + delta).doubleValue(), uv_smoothed[i]));
            }
            out.close();
            fstream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
