package version4;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.special.Erf;
import org.jfree.chart.renderer.xy.DefaultXYItemRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.ConvolutionFunctions.ExponentiallyModifiedGaussian;

import java.awt.image.ConvolveOp;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class SECSaxsUvSignalCorrector {

    int total_saxs_signal;
    int startIndex;
    int endIndex;
    int peak_at;

    XYSeries signal;

    int lowerLimitIndex, upperLimitIndex;
    double[] x_values;
    double[] target;
    double[] input_values_all;
    int[] remapped_indices;
    int[] assigned_indices;

    double amplitude, sigma, w_term, t_term;
    double initial_amplitude_guess;
    final int total_to_fit;
    final int kernel_width;
    int signal_start_index, signal_end_index;

    /**
     *
     * @param saxs_signal target signal from SEC-SAXS
     * @param uv_signal aligned and baseline adjusted signal
     * @param sigma
     * @param time_constant
     */
    public SECSaxsUvSignalCorrector(XYSeries saxs_signal, XYSeries uv_signal, double sigma, double time_constant, int startIndex, int endIndex) {

        total_saxs_signal = saxs_signal.getItemCount();
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        total_to_fit = endIndex - startIndex + 1;

        x_values = new double[total_to_fit];
        input_values_all = new double[uv_signal.getItemCount()];
        remapped_indices = new int[uv_signal.getItemCount()];

        signal = uv_signal; // reference to input

        target = new double[total_to_fit];

        int kernel_temp = 91; //uv_signal.getItemCount()/3;

        if ( kernel_temp % 2 == 0 ){
            kernel_width = kernel_temp - 1;
        } else {
            kernel_width = kernel_temp;
        }

        System.out.println("total to fit " + total_to_fit + " " + uv_signal.getItemCount());

        int count=0;
        /*
         * first value of x_values is how much input data was shifted to be at zero
         * in the convolution, must remember this when applied to whole dataset
         */
        for(int i=startIndex; i<=endIndex; i++){
            XYDataItem item = saxs_signal.getDataItem(i);
            x_values[count] = item.getXValue();
            target[count] = item.getYValue();
            count++;
        }

        /*
         * we are storing values in an array
         * uv_signal is truncated due in the beginning and end due to interpolation
         * remapped_indices allows us to track back to the original correspondance in the saxs_signal
         * saxs_signal is arranged by frame number
         * given exposure time and dead time, frame number is converted to time
         * We loose the frame number mapping in the saxs_signal since series stores (time, signal)
         */
        for(int i=0; i<remapped_indices.length; i++){
            remapped_indices[i] = saxs_signal.indexOf(uv_signal.getX(i));
        }

        // find peak position in target
        peak_at = 0;
        double max = Double.NEGATIVE_INFINITY;
        for(int i=0; i<count; i++){
            double val = target[i];
            if (val > max){
                max = val;
                peak_at = i;
            }
        }

        System.out.println("Peak position :: " + peak_at);
//        assignInputValues(saxs_signal, uv_signal);
        fillInputValues(saxs_signal, uv_signal);

        this.amplitude = 0.1;//initial_amplitude_guess/5.0;
        this.sigma = 3.8;
        this.w_term = 0.2;
        this.t_term = kernel_width/3.0;

    }

    private void assignInputValues(XYSeries saxs_signal, XYSeries uv_signal){

        input_values_all = new double[uv_signal.getItemCount()];

        // find first value past startIndex
        XYDataItem item;
        for(int i=0; i < uv_signal.getItemCount(); i++){
            item = uv_signal.getDataItem(i);
            input_values_all[i] = item.getYValue();
        }

        double limit = saxs_signal.getX(startIndex).doubleValue();
        for(int i=0; i < uv_signal.getItemCount(); i++){
            item = uv_signal.getDataItem(i);
            if (item.getXValue() > limit){
                lowerLimitIndex = i-1;
                break;
            }
        }

        limit = saxs_signal.getX(endIndex).doubleValue();
        upperLimitIndex = uv_signal.getItemCount()-2;
        for(int i=lowerLimitIndex; i < uv_signal.getItemCount(); i++){
            item = uv_signal.getDataItem(i);
            if (item.getXValue() > limit){
                upperLimitIndex = i-1;
                break;
            }
        }

        // for each value in SAXS_signal, get indices in signal that surround point
        assigned_indices = new int[total_to_fit*2];

        assigned_indices[0] = lowerLimitIndex;
        assigned_indices[1] = lowerLimitIndex + 1;

        assigned_indices[assigned_indices.length-2] = upperLimitIndex;
        assigned_indices[assigned_indices.length-1] = upperLimitIndex + 1;

        int counter = 2, indexInUse = lowerLimitIndex;

        for(int i= 1+startIndex; i<endIndex; i++){
            limit = saxs_signal.getX(i).doubleValue();

            for(int j=indexInUse; j < uv_signal.getItemCount(); j++){
                item = uv_signal.getDataItem(j);
                if (item.getXValue() > limit){
                    assigned_indices[counter] = j-1;
                    assigned_indices[counter+1] = j;
                    indexInUse = j+1;
                    counter += 2;
                    break;
                }
            }
        }

    }

    /*
     * there are no checks on the bounds, assuming signal last_x_value >= saxs_last_x_value
     */
    private void fillInputValues(XYSeries saxs_signal, XYSeries uv_signal){

        signal_start_index = uv_signal.indexOf(saxs_signal.getX(startIndex));
        signal_end_index = uv_signal.indexOf(saxs_signal.getX(endIndex));

        //input_values_to_convolve = new double[signal_end_index - signal_start_index + 1];
        double max_amplitude = Double.NEGATIVE_INFINITY;


        for(int i=signal_start_index; i <= signal_end_index; i++) {
            XYDataItem item = uv_signal.getDataItem(i);

            if (item.getYValue() > max_amplitude){
                max_amplitude = item.getYValue();
            }
        }

        for(int i=0; i < uv_signal.getItemCount(); i++){
            input_values_all[i] = uv_signal.getY(i).doubleValue();
        }

        initial_amplitude_guess = 1;//saxs_signal.getY(max_index).doubleValue()/max_amplitude;
    }

    double getAmplitude(){ return this.amplitude;}
    double getW_coeff(){ return this.w_term; }
    double getOffset(){ return this.t_term; }
    double getSigma(){ return this.sigma; }
    double getKernelWidth() { return this.kernel_width; }

    public class NoDerivative implements MultivariateFunction {

        final double[] saxs_target;
        final int totalInTarget;
        final int kernel_width;
        final int midpoint;
        final int peak_position;
        final double transition_point;
        final double growth_rate;
        final ExponentiallyModifiedGaussian conv_function;
        final int constraint;

        /*
         * factors[0] = Amplitude
         * factors[1] = sigma
         * factors[2] = m_term
         * factors[3] = time_term
         * target - SEC SAXS
         * signal - UV
         */
        public NoDerivative(double[] saxs_target, int total_width, int peak_position) {
            this.saxs_target = saxs_target;
            this.totalInTarget = saxs_target.length;
            this.kernel_width = total_width;
            this.midpoint = (total_width-1)/2;
            this.peak_position = peak_position;
            conv_function = new ExponentiallyModifiedGaussian(this.kernel_width);
            growth_rate = 0.1507;
            transition_point = peak_position+2*peak_position;
            constraint = 12;
        }

        @Override
        public double value(double[] point) {
            double amp = point[0];
            double sigma_coeff = point[1];
            double w_coeff = point[2];
            double t_offset = point[3];

            conv_function.calculate(sigma_coeff, w_coeff, t_offset);
            double[] convolved = conv_function.convolveWithInput(input_values_all, 0, amp);

            double diff, sum=0.0d;
//            double weight;
//            double counter = 0;
//            for(int i=0; i< totalInTarget; i++){
//                diff = this.saxs_target[i] - convolved[signal_start_index + i];
//                weight = 1.0d - 1.0/(1.0 + Math.exp(-growth_rate*(counter - transition_point)));
//                System.out.println(i + " " + weight + " " + this.saxs_target[i]);
//                sum += weight*diff*diff;
//                counter+= 1.0d;
//            }

            int before_peak = (peak_position > constraint ) ?  6 : peak_position/2;

            for(int i=0; i< before_peak; i++){
                diff = this.saxs_target[i] - convolved[signal_start_index + i];
                sum += diff*diff;
            }

            int upto = peak_position + before_peak;

            for(int i=before_peak; i<upto; i++){
                diff = this.saxs_target[i] - convolved[signal_start_index + i];
                sum += 100*diff*diff;
            }

            for(int i=upto; i<totalInTarget; i++){
                diff = this.saxs_target[i] - convolved[signal_start_index + i];
                sum += diff*diff;
            }

            System.out.println(" SUM " + sum + " " + amp + " s " + sigma_coeff + " w " + w_coeff + " t " + t_offset );
            if (Double.isNaN(sum)){
                sum = Double.POSITIVE_INFINITY;
            }

            return sum;
        }
    }


    private class LinearProblem {

        final double[] saxs_target;
        final int totalInTarget;
        final int kernel_width;
        final int midpoint;
        final ExponentiallyModifiedGaussian conv_function;

        /*
         * factors[0] = Amplitude
         * factors[1] = sigma
         * factors[2] = m_term
         * factors[3] = time_term
         * target - SEC SAXS
         * signal - UV
         */
        public LinearProblem(double[] saxs_target, int total_width) {
            this.saxs_target = saxs_target;
            totalInTarget = saxs_target.length;
            this.kernel_width = total_width;
            this.midpoint = (total_width-1)/2;

            conv_function = new ExponentiallyModifiedGaussian(this.kernel_width);
        }


        public ObjectiveFunction getObjectiveFunction() {
            return new ObjectiveFunction(new MultivariateFunction() {
                @Override
                public double value(double[] point) {

                    double amp = point[0];
                    double sigma_coeff = point[1];
                    double w_coeff = point[2];
                    double t_offset = point[3];

                    conv_function.calculate(sigma_coeff, w_coeff, t_offset);
                    double[] convolved = conv_function.convolveWithInput(input_values_all, 0, amp);

                    double diff, sum=0.0d;

                    for(int i=startIndex; i<=endIndex; i++){
                        diff = saxs_target[i - startIndex] - convolved[signal_start_index + (i-startIndex)];
                        sum += diff*diff;
                    }

                    System.out.println(" SUM " + sum + " amp " + amp + " s " + sigma_coeff + " w " + w_coeff + " t " + t_offset);
                    return sum;
                }
            });
        }


        /*
         * calculate gradient for each factor in order:
         * A
         * sigma
         * m_coeff
         * time_coeff
         */
        public ObjectiveFunctionGradient getObjectiveFunctionGradient() {
            return new ObjectiveFunctionGradient(new MultivariateVectorFunction() {

                @Override
                public double[] value(double[] point) throws IllegalArgumentException {
                    double[] residuals = new double[totalInTarget];

                    int totalInSignal = input_values_all.length;

                    double amp = point[0], sigma_coeff = point[1], w_coeff = point[2], t_offset = point[3];
                    double baseline = 0.0d;
                    double exp_factor = Math.exp(sigma_coeff*sigma_coeff*w_coeff*w_coeff/2.0);
                    double sigma2_w = sigma_coeff*sigma_coeff*w_coeff;
                    double inv_sigma_sqrt = 1.0/(sigma_coeff*Math.sqrt(2.0));

                    // get convolution parameters
                    conv_function.calculate(sigma_coeff, w_coeff, t_offset);
                    double[] convolution = conv_function.getConvolutionFunction();
                    double[] convolved = conv_function.convolveWithInput(input_values_all,0.0d, amp);
                    double[] exp_terms = conv_function.getEXPs();
                    double[] erfs = conv_function.getERFs();
                    double inv_norm = 1.0/conv_function.getNormalization();

                    // calculate residual
                    // tail part
                    for(int i=0; i < totalInTarget; i++){
                        residuals[i] = saxs_target[i] - convolved[signal_start_index + i];
                    }

                    double sigma_w2 = sigma_coeff*w_coeff*w_coeff;

                    double sqrt_2PI = Math.sqrt(2.0*Math.PI);
                    double w_coef_inv_root_2PI = 2.0*w_coeff/sqrt_2PI;
                    double two_sigma_inv_2pi = 2.0*sigma_coeff/sqrt_2PI;
                    double two_inv_sigma2_inv_root2pi = 2.0/(sigma_coeff*sigma_coeff*sqrt_2PI);

                    double amp_sum=0.0, sigma_sum=0.0, w_sum=0.0, t_sum=0.0, temp_sum, temp_sigma_sum, temp_w_sum, temp_t_sum, residual;
                    double signal_at_k;

                    for(int index=0; index < totalInTarget; index++){

                        int signal_at = signal_start_index - midpoint + index;
                        int end_at = signal_at + kernel_width;

                        int g_count = kernel_width-1;
                        temp_sum=0.0d;
                        temp_sigma_sum = 0.0d;
                        temp_w_sum = 0.0d;
                        temp_t_sum = 0.0d;

                        double time_at = g_count - t_offset; // domain of convoluting function

                        // if any points in kernel exist outside domain of convolution function
                        for(; signal_at < 0; signal_at++){
                            double[] calc = calculateGradients(
                                    g_count,
                                    time_at,
                                    exp_terms, exp_factor, erfs,
                                    w_coeff, sigma_w2, two_inv_sigma2_inv_root2pi, w_coef_inv_root_2PI, inv_sigma_sqrt, sigma2_w, two_sigma_inv_2pi);

                            signal_at_k = baseline;
                            temp_sum += signal_at_k*convolution[g_count];  // amplitude
                            temp_sigma_sum += signal_at_k*calc[0];         // sigma
                            temp_w_sum += signal_at_k*calc[1];             // w_coefficient
                            temp_t_sum += signal_at_k*calc[2];
                            g_count--;
                            time_at--;
                            System.out.println("less than 0 " + index );
                        }

                        int plus_more = 0;
                        if (end_at >= totalInSignal){
                            plus_more = end_at - totalInSignal;
                            end_at = totalInSignal;
                        }

                        // full contained within kernel
                        for(; signal_at < end_at; signal_at++){
                            double[] calc = calculateGradients(
                                    g_count,
                                    time_at,
                                    exp_terms, exp_factor, erfs,
                                    w_coeff, sigma_w2, two_inv_sigma2_inv_root2pi, w_coef_inv_root_2PI, inv_sigma_sqrt, sigma2_w, two_sigma_inv_2pi);

                            signal_at_k = input_values_all[signal_at];
                            temp_sum += signal_at_k*convolution[g_count];  // amplitude
                            temp_sigma_sum += signal_at_k*calc[0];         // sigma
                            temp_w_sum += signal_at_k*calc[1];             // w_coefficient
                            temp_t_sum += signal_at_k*calc[2];
                            g_count--;
                            time_at--;
                        }


                        for(int k=0; k < plus_more; k++){
                            double[] calc = calculateGradients(
                                    g_count,
                                    time_at,
                                    exp_terms, exp_factor, erfs,
                                    w_coeff, sigma_w2, two_inv_sigma2_inv_root2pi, w_coef_inv_root_2PI, inv_sigma_sqrt, sigma2_w, two_sigma_inv_2pi);

                            signal_at_k = baseline;
                            temp_sum += signal_at_k*convolution[g_count];  // amplitude
                            temp_sigma_sum += signal_at_k*calc[0];         // sigma
                            temp_w_sum += signal_at_k*calc[1];             // w_coefficient
                            temp_t_sum += signal_at_k*calc[2];
                            g_count--;
                            time_at--;
                        }

                        if (g_count > -1){
                            System.out.println("g_count " + g_count);
                            System.exit(0);
                        }

                        residual = residuals[index];
                        amp_sum +=  residual*temp_sum;
                        sigma_sum += residual*temp_sigma_sum;
                        w_sum += residual*temp_w_sum;
                        t_sum += residual*temp_t_sum;
                    }

                    double[] grad_factors = new double[4];
                    grad_factors[0] = -2.0d*amp_sum*inv_norm;
                    grad_factors[1] = -2.0d*amp*sigma_sum*inv_norm;
                    grad_factors[2] = -2.0d*amp*w_sum*inv_norm;
                    grad_factors[3] = -2.0d*amp*t_sum*inv_norm;

                    //System.out.println("grad " + grad_factors[0] + " :: " + grad_factors[1] + " " + grad_factors[2] + " " + grad_factors[3]);
                    return grad_factors;
                }
            });
        }

        private double[] calculateGradients(int kernel_index,
                                            double time,
                                            double[] exp_terms,
                                            double exp_factor,
                                            double[] erfs,
                                            double w_coeff,
                                            double sigma_w2,
                                            double two_inv_sigma2_inv_root2pi,
                                            double w_coef_inv_root_2PI,
                                            double inv_sigma_sqrt,
                                            double sigma2_w,
                                            double two_sigma_inv_2pi) {

            double exp_term = exp_terms[kernel_index]*exp_factor;
            double erf_term = 1.0d + erfs[kernel_index];
            double erf_exp_term = (time - sigma2_w)*inv_sigma_sqrt;
            double derivative_erf = Math.exp(-erf_exp_term*erf_exp_term);

            double[] params = new double[3];
            // sigma
            params[0] = 0.5*exp_term*w_coeff*(sigma_w2*erf_term - (time*two_inv_sigma2_inv_root2pi + w_coef_inv_root_2PI)*derivative_erf);
            // w_coefficient
            params[1] = 0.5*exp_term*(erf_term + w_coeff*(-time + sigma2_w)*erf_term - w_coeff*two_sigma_inv_2pi*derivative_erf);
            // t_offset
            params[2] = 0.5*exp_term*w_coeff*(w_coeff*erf_term - inv_sigma_sqrt*derivative_erf);
            return params;
        }
    }

    public void convolveInputDataSets(){

//        final LinearProblem problem = new LinearProblem(
//                target,
//                kernel_width
//        );
//
//        final NonLinearConjugateGradientOptimizer optimizer= new NonLinearConjugateGradientOptimizer(
//                NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
//                new SimpleValueChecker(1e-9, 1e-9));
//
//        PointValuePair optimum = optimizer.optimize(new MaxEval(10000),
//                problem.getObjectiveFunction(),
//                problem.getObjectiveFunctionGradient(),
//                GoalType.MINIMIZE,
//                new InitialGuess(new double[] { this.amplitude, this.sigma, this.w_term, this.t_term}));
//
//        optimum.getValue();
//        double[] params = optimum.getPoint();

        final PowellOptimizer optim = new PowellOptimizer(1e-9, Math.ulp(1d));
        final PointValuePair result = optim.optimize(new MaxEval(10000),
                new ObjectiveFunction(new NoDerivative(
                        target,
                        kernel_width,
                        peak_at
                )),
                GoalType.MINIMIZE,
                new InitialGuess(new double[] { this.amplitude, this.sigma, this.w_term, this.t_term})
        );
        final double[] params = result.getPoint();

        this.amplitude = params[0];
        this.sigma = params[1];
        this.w_term = params[2];
        this.t_term = params[3];

        ExponentiallyModifiedGaussian convfunction = new ExponentiallyModifiedGaussian(kernel_width);
        convfunction.calculate(this.sigma, this.w_term, this.t_term);
        double[] convolved = convfunction.convolveWithInput(input_values_all, 0.0d, this.amplitude);

        for(int i=0;i<signal.getItemCount(); i++){
            signal.updateByIndex(i, convolved[i]);
        }

        try {
            String name = "calibration_convolved";
            FileWriter fw = new FileWriter(name+".txt");
            BufferedWriter out = new BufferedWriter(fw);

            out.write(String.format("# amplitude :: %.4E %n", this.amplitude));
            out.write(String.format("#     sigma :: %.4E %n", this.sigma));
            out.write(String.format("#   w_coeff :: %.4E %n", this.w_term));
            out.write(String.format("#    offset :: %.4E %n", this.t_term));
            out.write(String.format("# index, x-value, signal input, input convolved and scaled, input convolved %n"));
            out.write(String.format("# %s %n", name));

            for(int i=0; i<signal.getItemCount(); i++){
                Number xvl = signal.getX(i);
//                if (indexOf > 0){
                    out.write(String.format("%d %.4E  %.4E  %.4E  %.4E%n", i, xvl.doubleValue(), input_values_all[i], convolved[i], convolved[i]/this.amplitude));
//                out.write(String.format("%d %.4E  %.4E  %.4E%n", i, uv_signal.getX(i).doubleValue(), input_values_all[i], convolved[i]));
//                }
            }
            out.close();

//            name = "saxs";
//            fw = new FileWriter(name+".txt");
//            out = new BufferedWriter(fw);
//            for(int i=0; i<saxs_signal.getItemCount(); i++){
//                XYDataItem item = saxs_signal.getDataItem(i);
//                out.write(String.format("%d %.4E  %.4E%n", i, item.getXValue(), item.getYValue()));
//            }

            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //convfunction.writeConvolutionFunction();
    }

    public XYSeries getConvolvedSignal(){ return signal; }

    public int[] getRemapped_indices(){
        return remapped_indices;
    }

}


