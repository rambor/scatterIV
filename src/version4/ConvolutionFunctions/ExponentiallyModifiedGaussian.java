package version4.ConvolutionFunctions;

import org.apache.commons.math3.special.Erf;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class ExponentiallyModifiedGaussian {
    double[] function;
    double[] exp_terms;
    double[] erfs;
    double normalization=0.0d;

    final int kernel_size;
    public ExponentiallyModifiedGaussian(int kernel_width){
        kernel_size = kernel_width;
        function = new double[kernel_size];
        exp_terms = new double[kernel_size];
        erfs = new double[kernel_size];
    }

    public void calculate(double sigma, double w_term, double t_shift){
        double exp_factor = 0.5*w_term*Math.exp(sigma*sigma*w_term*w_term/2.0);
        double sigma_factor = sigma*sigma*w_term;
        double inv_sigma_sqrt = 1.0/(sigma*Math.sqrt(2.0));
        double exp_calc, erf_calc, value;

        normalization = 0.0d;
        for(int i = 0; i< kernel_size; i++){
            double at_t = i - t_shift;

            exp_calc = Math.exp(-at_t*w_term);
            exp_terms[i] = exp_calc;

            erf_calc = Erf.erf((at_t - sigma_factor)*inv_sigma_sqrt);
            erfs[i] = erf_calc;

            value = exp_factor*exp_calc*(1.0d + erf_calc);
            normalization += value;
            function[i] = value;
        }
    }

    public double[] getConvolutionFunction() {
        return function;
    }

    public double[] getERFs() {
        return erfs;
    }

    public double[] getEXPs() {
        return exp_terms;
    }

    public double getNormalization() { return normalization;}

    public double[] convolveWithInput(double[] input_dataset, double baseline, double amplitude){

        int totalIn = input_dataset.length;
        double[] temp = new double[totalIn];

        int count, middle_pt = (kernel_size - 1)/2;
        double sum, inv_norm = 1.0/normalization;
        int stop_here = totalIn - middle_pt;

        for(int index=0; index<middle_pt; index++){
            sum=0;//first_val*(middle_pt - i);
            int start_at = middle_pt - index;

            for(int k=0; k < start_at; k++){
                sum += function[kernel_size - 1 - k]*baseline;
            }

            count=0;
            for(int k=start_at; k < kernel_size; k++){
                sum += function[kernel_size - 1 - k]*input_dataset[count];
                count += 1;
            }
            temp[index] = sum*amplitude*inv_norm;
        }


        for(int index=middle_pt; index<stop_here; index++){
            sum=0;
            int start_at = index - middle_pt; // start at 1/2 width kernel
            for(int k=0; k<kernel_size; k++){
                sum += function[kernel_size-k-1]*input_dataset[start_at+k];
            }

            temp[index] = sum*amplitude*inv_norm;
        }


        for(int index=stop_here; index<totalIn; index++){
            int start_at = index - middle_pt;
            int delta = totalIn - start_at;
            sum=0;

            for(int k=0; k < delta; k++){
                sum += function[kernel_size - 1 - k]*input_dataset[start_at+k];
            }

            for(int k=delta; k < kernel_size; k++){
                sum += function[kernel_size - 1 - k]*baseline;
            }

            temp[index] = sum*amplitude*inv_norm;
        }

        return temp;
    }

    public XYSeries convolveWithInput(XYSeries input_dataset, double baseline, double amplitude){

        XYSeries temp = new XYSeries("temp");

        int totalIn = input_dataset.getItemCount();

        int count, middle_pt = (kernel_size - 1)/2;
        double sum, inv_norm = 1.0/normalization;
        int stop_here = totalIn - middle_pt;

        for(int index=0; index<middle_pt; index++){
            sum=0;//first_val*(middle_pt - i);
            int start_at = middle_pt - index;

            for(int k=0; k < start_at; k++){
                sum += function[kernel_size - 1 - k]*baseline;
            }

            count=0;
            for(int k=start_at; k < kernel_size; k++){
                sum += function[kernel_size - 1 - k]*input_dataset.getY(count).doubleValue();
                count += 1;
            }
            temp.add(input_dataset.getDataItem(index).getX(), sum*amplitude*inv_norm);
        }

        for(int index=middle_pt; index<stop_here; index++){
            sum=0;
            int start_at = index - middle_pt; // start at 1/2 width kernel
            for(int k=0; k<kernel_size; k++){
                sum += function[kernel_size-k-1]*input_dataset.getY(start_at+k).doubleValue();
            }
            temp.add(input_dataset.getDataItem(index).getX(), sum*amplitude*inv_norm);
        }


        for(int index=stop_here; index<totalIn; index++){
            int start_at = index - middle_pt;
            int delta = totalIn - start_at;
            sum=0;

            for(int k=0; k < delta; k++){
                sum += function[kernel_size - 1 - k]*input_dataset.getY(start_at+k).doubleValue();
            }

            for(int k=delta; k < kernel_size; k++){
                sum += function[kernel_size - 1 - k]*baseline;
            }

            temp.add(input_dataset.getDataItem(index).getX(), sum*amplitude*inv_norm);
        }

        return temp;
    }

    public void writeConvolutionFunction(){
        String  name = "convolution";

            try {
                FileWriter fw = new FileWriter(name+".txt");
                BufferedWriter out = new BufferedWriter(fw);

                for(int i=0; i< function.length; i++){
                     out.write(String.format("%d %.4E %n", i, function[i]));
                }
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


    }
}
