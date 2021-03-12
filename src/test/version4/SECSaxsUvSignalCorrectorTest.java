package version4;

import org.junit.Before;
import org.junit.Test;
import version4.ConvolutionFunctions.ExponentiallyModifiedGaussian;

import static org.junit.Assert.*;

public class SECSaxsUvSignalCorrectorTest {

    @Before
    public void setUp() throws Exception {

        SECSaxsUvSignalCorrector temp;

    }


    @Test
    public void calculate() {
        int lengthOf = 501;
        double fake_amp = 3.1;
        double mu = (double)lengthOf/3.0;
        double sigma_test = 12.1;
        double[] base_signal = new double[lengthOf];


        int kernel_width = 101;
        ExponentiallyModifiedGaussian tempexp = new ExponentiallyModifiedGaussian(kernel_width);
        tempexp.calculate(3.2, 0.0052, (kernel_width-1)/2.0);
        double[] conv = tempexp.getConvolutionFunction();
        double inv_norm = 1.0d/tempexp.getNormalization();

        int midpoint = (kernel_width-1)/2;
        System.out.println("MU " + mu);
        // simple gaussian
        for(int i=0; i<lengthOf; i++){
            base_signal[i] = fake_amp/(sigma_test*Math.sqrt(2.0*Math.PI))*Math.exp(-0.5*(i-mu)*(i-mu)/(sigma_test*sigma_test)) + 1.0d;
        }

        System.out.println("print convolution");
        for(int i=0; i<lengthOf; i++){

            double temp_sum=0.0;
            int start_at = i - midpoint; // midpoint is always greater than index

            if (i < midpoint){

                int counter = 0;
                for(int j= start_at; j<0; j++){
                    temp_sum += 1.0*conv[kernel_width-1-counter];
                    counter++;
                }

                int indexer = 0;
                for(; counter<kernel_width; counter++){
                    temp_sum += conv[kernel_width-1-counter]*base_signal[indexer];
                    indexer++;
                }

            } else if (i < (lengthOf - midpoint)) {

                for(int j= 0; j<kernel_width; j++){
                    temp_sum += base_signal[start_at + j]*conv[kernel_width-1-j];
                }

            } else {
                int end_at = lengthOf - (i - midpoint);
                int counter = 0;
                for(int j= 0; j<end_at; j++){
                    temp_sum += base_signal[start_at + j]*conv[kernel_width-1-j];
                    counter++;
                }

                for(; counter<kernel_width; counter++){
                    temp_sum += 1.0*conv[kernel_width-1-counter];
                }
            }

            System.out.println(i + " " + base_signal[i] + " " + temp_sum*inv_norm);
        }

        // fit input gaussian


    }
}