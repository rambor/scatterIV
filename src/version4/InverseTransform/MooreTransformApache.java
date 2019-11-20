package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

public class MooreTransformApache extends IndirectFT implements Cloneable {

    private int cols;
    private double del_r;
    double[] r_vector;
    double[] regularization_r_vector;
    double[] target; // data
    double[] invVariance; // data
    double[] qvalues; // data

    public double invDmax;

    private RealMatrix designMatrix; // assume this is the design matrix
    int r_vector_size;


    public MooreTransformApache(
            XYSeries dataset,
            XYSeries untransformedErrors,
            double dmax,
            double qmax,
            double lambda,
            boolean includeBackground) {
        super(dataset, untransformedErrors, dmax, qmax, lambda, includeBackground);

        this.invDmax = 1.0/dmax;
        this.invertStandardVariance();
        this.createDesignMatrix(this.data);
        this.solve();
        this.setModelUsed("Moore L2-NORM");
    }

    public MooreTransformApache(
            XYSeries scaledqIqData,
            XYSeries scaledqIqErrors,
            double dmax,
            double qmax,
            double lambda,
            double stdmin,
            double stdscale,
            boolean useBackground){

        super(scaledqIqData, scaledqIqErrors, dmax, qmax, lambda, useBackground, stdmin, stdscale);
        // data is standardized along with errors (standard variance)
        this.invDmax = 1.0/dmax;

//        this.standardizeErrors(); // extract variances from errors
        standardVariance = new XYSeries("standardized error");
        int totalItems = scaledqIqData.getItemCount();
        invVariance = new double[totalItems];
        double temperrorvalue;

        for(int r=0; r<totalItems; r++){
            XYDataItem tempData = scaledqIqData.getDataItem(r);
            temperrorvalue = scaledqIqErrors.getY(r).doubleValue();//q*timeserror/scale
            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // variance q_times_Iq_scaled
            invVariance[r] = 1.0/(temperrorvalue*temperrorvalue);
        }

        this.createDesignMatrix(scaledqIqData);
        this.solve();

        this.setModelUsed("Moore L2-NORM");
    }


    public MooreTransformApache(
            XYSeries scaledqIqData,
            XYSeries scaledqIqErrors,
            double[] priors,
            double dmax,
            double qmax,
            double lambda,
            double stdmin,
            double stdscale,
            boolean useBackground){

        super(scaledqIqData, scaledqIqErrors, dmax, qmax, lambda, useBackground, stdmin, stdscale);
        // data is standardized along with errors (standard variance)
        this.invDmax = 1.0/dmax;
        this.prior_coefficients = priors.clone();
        priorExists = true;

//        this.standardizeErrors(); // extract variances from errors
        standardVariance = new XYSeries("standardized error");
        int totalItems = scaledqIqData.getItemCount();
        invVariance = new double[totalItems];
        double temperrorvalue;

        for(int r=0; r<totalItems; r++){
            XYDataItem tempData = scaledqIqData.getDataItem(r);
            temperrorvalue = scaledqIqErrors.getY(r).doubleValue();//q*timeserror/scale
            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // variance q_times_Iq_scaled
            invVariance[r] = 1.0/(temperrorvalue*temperrorvalue);
        }

        this.createDesignMatrix(scaledqIqData);
        this.solve();

        this.setModelUsed("Moore L2-NORM");
    }



    /*
     *
     * copy constructor of Moore
     */
    public MooreTransformApache (MooreTransformApache original){
                super(original);

        this.cols = original.cols;
        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();

        if (original.regularization_r_vector != null){
            this.regularization_r_vector = original.regularization_r_vector.clone();
        }

        this.target = original.target.clone();
        this.invVariance = original.invVariance;
        this.qvalues = original.qvalues;
        this.invDmax = original.invDmax;
        this.r_vector_size = original.r_vector_size;
        this.designMatrix = original.designMatrix.copy();

    }

    public Object clone(){
        return new MooreTransformApache(this);
    }


    /*
     * precalculate standardVariance
     * used in the Apache Solver
     *
     */
    private void invertStandardVariance(){
        int totalItems = standardVariance.getItemCount();
        invVariance = new double[totalItems];

        for(int r=0; r<totalItems; r++){
            invVariance[r] = 1.0/standardVariance.getY(r).doubleValue();
        }
    }

    @Override
    void calculateIzeroRg() {
        double i_zero = 0;
        double partial_rg = 0;
        double rsum = 0;
        double negativeOnePower, am_inv_pow_3;

        double am;
        //double pi_sq = 9.869604401089358;
        int sizeOf = coefficients.length;

        for (int i = 1; i < sizeOf; i++) {
            am = coefficients[i];
            negativeOnePower = Math.pow(-1, (i+1));
            am_inv_pow_3 = am/Math.pow(i,3);

            i_zero = i_zero + am/(i)*negativeOnePower;
            partial_rg = partial_rg + am_inv_pow_3*(n_pi_squared[i] - 6)*negativeOnePower;
            rsum = rsum + am_inv_pow_3*((n_pi_squared[i] - 2)*negativeOnePower - 2 );
        }

        double dmax2 = dmax*dmax;
        double dmax3 = dmax2*dmax;
        double dmax4 = dmax2*dmax2;
//        double inv_pi_cube = 1.0/(pi_sq*Math.PI);
//        double inv_pi_fourth = inv_pi_cube/Math.PI;
        double inv_pi_fourth = 1.0/(n_pi_squared[1]*n_pi_squared[1]);

        izero = standardizedScale*(TWO_INV_PI*i_zero*dmax2/Math.PI + coefficients[0]) + standardizedMin;

        double izero_temp = (TWO_INV_PI*i_zero*dmax2/Math.PI + coefficients[0]);
        //double izero_temp = (twodivPi*i_zero*dmax2/Math.PI + mooreCoefficients[0]);

        rg = Math.sqrt(2*dmax4*inv_pi_fourth/izero_temp*partial_rg)*0.7071067811865475; // 1/Math.sqrt(2);
        rAverage = 2*dmax3*inv_pi_fourth/izero_temp*rsum;
    }

    @Override
    void setPrDistribution() {
        prDistribution = new XYSeries("PRDistribution");

        int divisor = 3;
        r_vector_size = ns*divisor-1; // no background implies coeffs_size == ns
        del_r = dmax/(double)(ns*divisor);

        double nsdel_r = dmax/(double)(ns);
//        double totalPrPoints = (Math.ceil(qmax*dmax/Math.PI)*3); // divide dmax in ns*3 bins
        //System.out.println("total pts in interpolation " + totalPrPoints);
//        totalInDistribution = (int)totalPrPoints;
//        double deltar = dmax/totalPrPoints;
        totalInDistribution = r_vector_size;

        double resultM;
        double invtwopi2 = standardizedScale/(2.0*n_pi_squared[1]);
        double pi_dmax_r;
        double r_value;
        negativeValuesInModel = false;
        prDistribution.add(0.0d, 0.0d);

        for (int j=0; j < totalInDistribution; j++){

            r_value = (j+1)*del_r;
            pi_dmax_r = PI_INV_DMAX*r_value;
            resultM = 0;

            for(int i=1; i < totalCoefficients; i++){
                resultM += coefficients[i]*FastMath.sin(pi_dmax_r*i);
            }

            prDistribution.add(r_value, invtwopi2 * r_value * resultM*standardizedScale);

            if (resultM < 0){
                negativeValuesInModel = true;
            }
        }

        prDistribution.add(dmax,0);


        // do Shannon point distribution calculations
        // set distribution for fitting
        int size = r_vector.length;
        prDistributionForFitting = new XYSeries("output");

        double temp_del_r = dmax/(double)ns;
        double startAt = temp_del_r*0.5;
        while (startAt < dmax){
            r_value = startAt;
            pi_dmax_r = PI_INV_DMAX*r_value;
            resultM = 0;

            for(int k=1; k < totalCoefficients; k++){
                resultM += coefficients[k]*FastMath.sin(pi_dmax_r*k);
            }

            prDistributionForFitting.add(r_value, invtwopi2 * r_value * resultM);
            startAt += temp_del_r;
        }

        scoreDistribution(del_r);
        prScore *= 10;
        setHeaderDetails();
    }

    private void setHeaderDetails(){
        this.description  = String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS INDIRECT INVERSE FOURIER TRANSFORM OF I(q) %n");
        this.description  = String.format("REMARK 265  P(r) APPROXIMATED USING FOURIER SINE SERIES %n");
        this.description += String.format("REMARK 265  COEFFICIENTS ARE MOORE WEIGHTS FOR EACH SINE FUNCTION %n");
        this.description += String.format("REMARK 265 %n");
        this.description += String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", (dmax/(double)ns));
        this.description += String.format("REMARK 265            DISTRIBUTION SCORE : %.4f %n", prScore);
        this.description += String.format("REMARK 265 %n");
        if (!includeBackground){
            this.description += String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n");
        } else {
            this.description += String.format("REMARK 265      CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]);
            this.description += String.format("REMARK 265          SCALED  s*m(0) + min : %.4E %n", (coefficients[0]*standardizedScale + standardizedMin));
        }
        this.description += String.format("REMARK 265  MOORE COEFFICIENTS (UNSCALED)%n");
        for (int i=1; i<totalCoefficients;i++){
            this.description +=  String.format("REMARK 265                        m_(%2d) : %.3E %n", i, coefficients[i]);
        }
    }


    @Override
    public double calculateQIQ(double qvalue) {
        double dmaxq = dmax * qvalue;
        double dmaxq2 = dmaxq*dmaxq;
        double resultM = coefficients[0]; // if coefficients is 0, means no background

        for(int i=1; i < totalCoefficients; i++){
            //resultM = resultM + coefficients[i]*dmaxPi*i*FastMath.pow(-1,i+1)*FastMath.sin(dmaxq)/(Constants.PI_2*i*i - dmaxq*dmaxq);
            resultM = resultM + coefficients[i]*dmax_PI_TWO_INV_PI*i*FastMath.pow(-1,i+1)*FastMath.sin(dmaxq)/(n_pi_squared[i] - dmaxq2);
        }

        return resultM*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
            return this.calculateQIQ(qvalue)/qvalue;
    }

    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        double pi_dmax_r = Math.PI*r_value/dmax;
        double resultM = 0;

        for(int i=1; i < totalCoefficients; i++){
            resultM += coefficients[i]*FastMath.sin(pi_dmax_r*i);
        }

        return (inv2PI2*standardizedScale) * r_value * resultM*scale;
    }


    @Override
    public void estimateErrors(XYSeries fittedData) {

    }

    @Override
    public void normalizeDistribution() {
        double sum=0;
        for(int i=0; i < totalInDistribution; i++){
            XYDataItem item = prDistribution.getDataItem(i);
            sum += item.getYValue();
        }

        sum *= del_r; //area
        double invSum = 1.0/sum;

        for(int i=0; i < totalInDistribution; i++){
            XYDataItem item = prDistribution.getDataItem(i);
            prDistribution.updateByIndex(i, item.getYValue()*invSum);
        }
    }

    @Override
    void createDesignMatrix(XYSeries datasetInuse) {

        ns = (int) Math.ceil(qmax*dmax*INV_PI) ;  //
        coeffs_size = this.includeBackground ? ns + 1 : ns;   //+1 for constant background, +1 to include dmax in r_vector list

        //System.out.println("total coefficients " + coeffs_size);

        int divisor = 3;
        r_vector_size = ns*divisor-1; // no background implies coeffs_size == ns
        del_r = dmax/(double)(ns*divisor);

        rows = datasetInuse.getItemCount();    // rows
        cols = coeffs_size;                    // columns

        // if I think I can squeeze out one more Shannon Number, then I need to define del_r by dmax/ns+1
        r_vector = new double[r_vector_size];
        double[] sign = new double[ns+3];

        for(int i=0; i < r_vector_size; i++){ // last bin should be dmax
            r_vector[i] = (i+1)*del_r;
        }

        for(int i=0; i < sign.length; i++){ // last bin should be dmax
            sign[i] = FastMath.pow(-1.0, i+1);
        }

        /*
         * create A matrix (design Matrix)
         */
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        designMatrix = new BlockRealMatrix(rows, coeffs_size);
        /*
         * y_vector is q*I(q) data
         */
        y_vector = new SimpleMatrix(rows,1);
        qvalues= new double[rows];
        target = new double[rows];
        /*
         * am_vector contains the unknown parameters
         */
        double qd2, qd, calc;
        // double pi_d = Math.PI*dmax;
        // double two_inv_pi_pi_d = TWO_INV_PI*pi_d;

        if (!includeBackground){ // no constant background
            System.out.println("no background ");
            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);
                qd = tempData.getXValue()*dmax;
                qd2 = qd*qd;

                for(int col=0; col < coeffs_size; col++){
                   // calc = dmax_PI_TWO_INV_PI * (col+1) * FastMath.pow(-1.0, col + 2) * FastMath.sin(qd) / (n_pi_squared[col+1] - qd2);
                    calc = dmax_PI_TWO_INV_PI * (col+1) * sign[col+1] * FastMath.sin(qd) / (n_pi_squared[col+1] - qd2);
                    a_matrix.set(row, col, calc);
                    designMatrix.setEntry(row, col, calc);
                }
                y_vector.set(row,0,tempData.getYValue()); //set data vector
                qvalues[row] = tempData.getXValue();
                target[row] = tempData.getYValue();
            }
        } else { // constant background

            for(int r=0; r<rows; r++){ //rows, length is size of data

                XYDataItem tempData = datasetInuse.getDataItem(r);
                qd = tempData.getXValue()*dmax;
                qd2 = qd*qd;

                for(int c=0; c < coeffs_size; c++){
                    if (c == 0){
                       // a_matrix.set(r, 0, tempData.getXValue()); // constant background term
                       // designMatrix.setEntry(r, 0, tempData.getXValue());
                        a_matrix.set(r, 0, 1);
                        designMatrix.setEntry(r, 0, 1);
                    } else {
//                        calc = dmax_PI_TWO_INV_PI * c * FastMath.pow(-1.0, c + 1) * FastMath.sin(qd) / (n_pi_squared[c] - qd2);
                        calc = dmax_PI_TWO_INV_PI * c * sign[c] * FastMath.sin(qd) / (n_pi_squared[c] - qd2);
                        a_matrix.set(r, c, calc);
                        designMatrix.setEntry(r, c, calc);
                    }
                }
                y_vector.set(r,0,tempData.getYValue()); //set data vector
                qvalues[r] = tempData.getXValue();
                target[r] = tempData.getYValue();
            }
        }
    }




    /**
     * Minimize on the absolute value of the coefficients, coeficients can be positive or negative
     * Can include background or not
     */
    public void solve(){

        LinearProblem problem = new LinearProblem(
                designMatrix,
                qvalues,
                target,
                invVariance,
                r_vector,
                dmax,
                lambda,
                includeBackground);
        try{
            NonLinearConjugateGradientOptimizer optimizer = new NonLinearConjugateGradientOptimizer(
                    NonLinearConjugateGradientOptimizer.Formula.FLETCHER_REEVES,
                    new SimpleValueChecker(1e-6, 1e-6)
            );

            double[] guess = new double[coeffs_size]; // approximate using Gaussian
            double sigma = dmax/6.0; // 0 to dmax should be 6 sigma
            double twoVar = 2.0*sigma*sigma;
            double invTwoVar = 1.0/twoVar;
            double prefactor = 1.0/Math.sqrt(Math.PI*twoVar);
            double midpoint = dmax/2.0; // midpoint is mu (average)
            int totalSubset = target.length;
            if (includeBackground){

                if (priorExists){
                    guess = prior_coefficients.clone();
                    if (coeffs_size != prior_coefficients.length){
                        System.out.println("SIZES do not match " + coeffs_size + " != " + prior_coefficients.length);
                    }
                } else {
                    double guessSum = 0;
                    int window = (int)(totalSubset*0.1);
                    for(int n=(totalSubset-window); n<totalSubset; n++){
                        guessSum += target[n];
                    }

                    guess[0] = guessSum/(double)window;
                    double tempinv = 1.0/(Math.PI*dmax);

                    int width = totalSubset/(coeffs_size-1);
                    int half = width/2;

                    for(int i=1; i < coeffs_size; i++){
                        guess[i] = invDmax*Math.pow(-1, i+1)*target[width*(i-1) + half]/(double)i;
                    }
                }
            } else {

                int width = totalSubset/(coeffs_size);
                int half = width/2;

                for(int i=0; i < coeffs_size; i++){
                    guess[i] = invDmax*Math.pow(-1, i+2)*target[width*i + half]/(double)(i+1);
                }
            }

            PointValuePair optimum = optimizer.optimize(new MaxEval(20000),
                    problem.getObjectiveFunction(),
                    problem.getObjectiveFunctionGradient(),
                    GoalType.MINIMIZE,
                    new InitialGuess(guess));

            // initialize coefficient vector

            /*
             * standardize to number of datapoints
             */
            //finalScore = optimum.getValue()/(double)rows;

            int totalCoeffs = includeBackground ? coeffs_size : coeffs_size+1;
            coefficients = new double[totalCoeffs];
            am_vector = new SimpleMatrix(coeffs_size,1);  // am is 0 column

            if (!includeBackground){
                coefficients[0] = 0; // set background to 0
                for (int j=0; j < coeffs_size; j++){
                    coefficients[j+1] = optimum.getPoint()[j];
                    am_vector.set(j, 0, optimum.getPoint()[j]);
                }
            } else { // has background
                for (int j=0; j < coeffs_size; j++){
                    coefficients[j] = optimum.getPoint()[j]; // background
                    am_vector.set(j, 0, optimum.getPoint()[j]);
                }
            }

            totalCoefficients = coefficients.length;
            this.setPrDistribution();
            this.calculateIzeroRg();
            //this.calibratePrDistribution();
        } catch (TooManyEvaluationsException ex) {
            System.out.println("TOO Few Evaluations");
        } catch (Exception e) {
            System.out.println("Exception occurred " + e.getMessage());
        }
    }

    private static class LinearProblem {

        final RealMatrix matrix; // assume this is the design matrix
        final double[] obs; // data
        final double[] invVariance;

        final double dmax, invDmax;
        final int totalqvalues;
        final double[] qvalues;
        final double[] rvalues;
        final double weight;
        final boolean useBackground;
        final int totalP, totalRvalues;
        final double invPID;

        public LinearProblem(RealMatrix designMatrix, double[] qvalues, double[] target, double[] invVar, double[] regularrvector, double dmax, double weight, boolean useBackGround) {

            this.matrix = designMatrix;
            this.qvalues = qvalues;
            this.obs  = target;
            this.invVariance = invVar;
            this.weight = 10000*weight;
            this.rvalues = regularrvector;
            this.totalRvalues = rvalues.length;

            this.totalP = designMatrix.getColumnDimension();
            this.totalqvalues = qvalues.length;

            this.dmax = dmax;
            this.invDmax = 1.0/dmax;
            this.invPID = Math.PI*this.invDmax;

            this.useBackground = useBackGround;
        }


        /*
         * return the chi^2 and regularized second derivative calculated using finite differences?
         *
         */
        public ObjectiveFunction getObjectiveFunction() {

            return new ObjectiveFunction(new MultivariateFunction() {
                public double value(double[] point) {
                    // point comes in as a vector and is multiplied by blockmatrix
                    double seconddiff=0;
                    double chi2=0;
                    double residual;
                    double[] calc = matrix.operate(point);

                    for (int i = 0; i < calc.length; ++i) {
                        residual = obs[i] - calc[i];
                        chi2 += residual*residual*invVariance[i];
                    }

                    double npird, doublePrime=0, an_n, tempsum;
                    double doublePrimeSine, doublePrimeCosine;
                    // calculate second derivative at each point
                    // background is constant, makes no contribution to smoothness of P(r) distribution
                    if (useBackground){

                        for(int r=0; r<totalRvalues; r++){
                            double rvalue = rvalues[r];
                            doublePrimeSine=0;
                            doublePrimeCosine=0;

                            for(int n=1; n<totalP; n++){
                                npird = invPID*n*rvalue;
                                an_n = point[n]*n;
                                doublePrimeSine += an_n*n*FastMath.sin(npird);
                                doublePrimeCosine += an_n*FastMath.cos(npird);
                            }
                            tempsum = 2*invPID*doublePrimeCosine - invPID*invPID*rvalue*doublePrimeSine;
                            doublePrime += tempsum*tempsum;
                        }

                    } else {
                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P_(i+1) - P_(i)|^2
                         */
//                        double[] pr = new double[totalRvalues];
//                        double eightPI = 8*Math.PI, rvalue, rpi, sumIt;
//                        for(int r=0; r<totalRvalues; r++){
//                            rvalue = rvalues[r];
//                            rpi = rvalue*Math.PI*invDmax;
//                            sumIt = 0;
//                            for(int n=0; n<totalP; n++){
//                                sumIt += point[n]*Math.sin((n+1)*rpi);
//                            }
//                            pr[r] = eightPI*sumIt*rvalue;
//                        }
//
//                        doublePrime = pr[0]*pr[0] + pr[totalRvalues-1]*pr[totalRvalues-1];
//
//                        for(int r=1; r<totalRvalues; r++){
//                            sumIt = pr[r] - pr[r-1];
//                            doublePrime += sumIt*sumIt;
//                        }


                        /*
                         * minimize second derivative
                         */
                        for(int r=0; r<totalRvalues; r++){
                            double rvalue = rvalues[r];
                            doublePrimeSine=0;
                            doublePrimeCosine=0;

                            for(int n=0; n<totalP; n++){
                                npird = invPID*(n+1)*rvalue;
                                an_n = point[n]*(n+1);
                                doublePrimeSine += an_n*(n+1)*FastMath.sin(npird);
                                doublePrimeCosine += an_n*FastMath.cos(npird);
                            }
                            tempsum = 2*invPID*doublePrimeCosine - invPID*invPID*rvalue*doublePrimeSine;
                            doublePrime += tempsum*tempsum;
                        }


                    }
                    // add last term to sum
                    // System.out.println("chi " + chi2 + " " + doublePrime + " w " + (weight*doublePrime));
                    return chi2 + weight*doublePrime;
                }
            });
        }


        /**
         * return gradient of the parameters
         * return vector is equal to the number of parameters
         * @return
         */
        public ObjectiveFunctionGradient getObjectiveFunctionGradient() {
            return new ObjectiveFunctionGradient(new MultivariateVectorFunction() {
                public double[] value(double[] point) {
                    // difference betweens
                    int total_params = matrix.getColumnDimension();
                    double[] del_p = new double[total_params];
                    double[] calc = matrix.operate(point);
                    int calcLength = calc.length;
                    double[] residualsInvVar = new double[calcLength];

                    /*
                     * Calculate Residuals as (qI_obs - qI_calc)/invVariance[q]
                     */
                    double residualSum = 0;
                    for (int q = 0; q < calcLength; ++q) {
                        residualsInvVar[q] = (obs[q]-calc[q])*invVariance[q];
                        residualSum += residualsInvVar[q];
                    }
                    double npird, npid, an_n;
                    double doublePrimeSine, doublePrimeCosine;

                    if (useBackground){
                        /*
                         * d/d_a_i => 2*SUM p"(r_i)*[cos-term - r*sin-term]
                         *
                         * calculate second derivative sum at each r-value
                         */
                        double[] doublePrimeR = new double[totalRvalues];
                        for(int r=0; r<totalRvalues; r++){
                            double rvalue = rvalues[r];
                            doublePrimeSine=0;
                            doublePrimeCosine=0;

                            for(int n=1; n<totalP; n++){
                                npird = invPID*n*rvalue;
                                an_n = point[n]*n;
                                doublePrimeSine += an_n*n*FastMath.sin(npird);
                                doublePrimeCosine += an_n*FastMath.cos(npird);
                            }
                            doublePrimeR[r] = 2*invPID*doublePrimeCosine - invPID*invPID*rvalue*doublePrimeSine;
                        }

                        // set gradient for each coefficient
                        del_p[0] = -2*residualSum; // background term
                        for(int p=1; p < totalP; p++){
                            double sum = 0;
                            for (int q = 0; q < calcLength; ++q) {
                                sum += residualsInvVar[q]*matrix.getEntry(q,p);
                            }
                            del_p[p] = -2*sum;
                        }

                        double doubleprimer;
                        for(int n=1; n<totalP; n++){

                            doublePrimeSine=0;
                            doublePrimeCosine=0;
                            npid = n*invPID;
                            for(int r=0; r<totalRvalues; r++){
                                double rvalue = rvalues[r];
                                npird = npid*rvalue;
                                doubleprimer = 2*doublePrimeR[r];
                                doublePrimeCosine += doubleprimer*FastMath.cos(npird);
                                doublePrimeSine += doubleprimer*rvalue*FastMath.sin(npird);
                            }
                            del_p[n] += weight*(2*npid*doublePrimeCosine - npid*npid*doublePrimeSine);
                        }

                    } else { // no Background term

                        for(int p=0; p < totalP; p++){
                            double sum = 0;
                            for (int q = 0; q < calcLength; ++q) {
                                sum += residualsInvVar[q]*matrix.getEntry(q,p);
                            }
                            del_p[p] = -2*sum;
                        }


                        double[] doublePrimeR = new double[totalRvalues];
                        for(int r=0; r<totalRvalues; r++){
                            double rvalue = rvalues[r];
                            doublePrimeSine=0;
                            doublePrimeCosine=0;

                            for(int n=0; n<totalP; n++){
                                npird = invPID*(n+1)*rvalue;
                                an_n = point[n]*(n+1);
                                doublePrimeSine += an_n*(n+1)*FastMath.sin(npird);
                                doublePrimeCosine += an_n*FastMath.cos(npird);
                            }
                            doublePrimeR[r] = 2*invPID*doublePrimeCosine - invPID*invPID*rvalue*doublePrimeSine;
                        }
                        // set gradient for each coefficient
                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |p"(r)|^2
                         *
                         * add the regularization part
                         */
                        double doubleprimer;
                        for(int n=0; n<totalP; n++){

                            doublePrimeSine=0;
                            doublePrimeCosine=0;
                            npid = (n+1)*invPID;
                            for(int r=0; r<totalRvalues; r++){
                                double rvalue = rvalues[r];
                                npird = npid*rvalue;
                                doubleprimer = 2*doublePrimeR[r];
                                doublePrimeCosine += doubleprimer*FastMath.cos(npird);
                                doublePrimeSine += doubleprimer*rvalue*FastMath.sin(npird);
                            }
                            del_p[n] += weight*(2*npid*doublePrimeCosine - npid*npid*doublePrimeSine);
                        }

                    }

                    return del_p;
                }
            });
        }
    }
}
