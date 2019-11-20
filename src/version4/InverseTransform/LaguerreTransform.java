package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.MultivariateVectorFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialsUtils;
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
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Functions;

import java.util.ArrayList;
import java.util.Random;

public class LaguerreTransform extends IndirectFT {

    private double del_r;
    double[] r_vector;
    double[] target; // data
    double[] invVariance; // data
    double[] qvalues; // data
    double[] laguerre_values;
    double[] beta_rkValues;
    double[] r_k_alphaValues;
    double[] exp_beta_rkValues;
    double betaGamma;

    double alpha, beta, initial_2nd_moment, maximum_raverage;
    int r_vector_size;
    public final double invDmax;
    private RealMatrix designMatrix; // assume this is the design matrix

    // Dataset should not be standardized but in the form  of [q, q*I(q)]

    /**
     * For a given Rg and raverage estimate alpha and beta
     * @param dataset
     * @param errors
     * @param dmax
     * @param qmax
     * @param lambda
     * @param cBoxValue
     */
    public LaguerreTransform(double r_ave_estimate, double rg_estimate, XYSeries dataset, XYSeries errors, double dmax, double qmax, double lambda) {
        super(dataset, errors, dmax, qmax, lambda, false);

        this.initial_2nd_moment = rg_estimate*rg_estimate*2;

        this.beta = (initial_2nd_moment - r_ave_estimate*r_ave_estimate)/(r_ave_estimate); //scale
        this.alpha = (r_ave_estimate*r_ave_estimate)/(initial_2nd_moment - r_ave_estimate*r_ave_estimate); //shape
        System.out.println("ALPHA (SHAPE) " + this.alpha + " | BETA " + this.beta);
        this.invDmax = 1.0/dmax;
        this.invertStandardVariance();
        this.createDesignMatrix(this.data);

        this.rambo_coeffs_L2();
        this.setModelUsed("Laguerre L2-NORM");
    }


    public LaguerreTransform(
            XYSeries dataset,
            XYSeries errors,
            double dmax,
            double qmax,
            double lambda,
            double stdscale){

        super(dataset, errors, dmax, qmax, lambda, false, 0, stdscale);
        // data is standardized along with errors (standard variance)
        this.invDmax = 1.0/dmax;

        this.standardizeErrors();
        this.createDesignMatrix(dataset);
        this.rambo_coeffs_L2();

        this.setModelUsed("Laguerre L2-NORM");
    }



    public void createDesignMatrix(XYSeries datasetInuse){
        //
        //
        ns = ((int) (Math.ceil(qmax*dmax*INV_PI) + 1 )) ;  //
        //ns = ((int) (Math.round(qmax*dmax*INV_PI) + 3 )) ;  //
        coeffs_size = this.includeBackground ? ns + 1 : ns;   //+1 for constant background, +1 to include dmax in r_vector list

        rows = datasetInuse.getItemCount();    // rows
        target = new double[rows];
        qvalues= new double[rows];

        //r_vector_size = 2*ns;
        r_vector_size = ns;

        /*
         * effective bin width of the Pr-distribution
         */
        del_r = dmax/(double)(r_vector_size);
        // if I think I can squeeze out one more Shannon Number, then I need to define del_r by dmax/ns+1
        r_vector = new double[r_vector_size];

        r_k_alphaValues = new double[r_vector_size];
        exp_beta_rkValues = new double[r_vector_size];
        beta_rkValues = new double[r_vector_size];

        for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
            r_vector[i] = (0.5 + i)*del_r; // dmax is not represented in this set
            double r_value = r_vector[i];
            r_k_alphaValues[i] = (Math.pow(r_value, alpha - 1));
            exp_beta_rkValues[i] = (Math.exp(-r_value/beta));
            beta_rkValues[i] = (r_value/beta);
        }

        laguerre_values = new double[r_vector_size*ns];

        for(int col=0; col < coeffs_size; col++){
            for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                laguerre_values[r_vector_size*col + i] = laguerre(col, beta_rkValues[i]);
            }
        }

        betaGamma = 1.0/(Math.pow(beta, alpha)*(Gamma.gamma(alpha)));

        /*
         * create A matrix (design Matrix)
         */
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        designMatrix = new BlockRealMatrix(rows, coeffs_size);
        /*
         * y_vector is q*I(q) data
         */
        y_vector = new SimpleMatrix(rows,1);

        if (!includeBackground) { // no constant background

            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);

                double[] sinc = new double[r_vector_size];
                for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                    double r_value = r_vector[i]; // dmax is not represented in this set
                    sinc[i] = FastMath.sin(r_value*tempData.getXValue())/r_value;
                }


                for(int col=0; col < coeffs_size; col++){
                    // sum over sinc multiplied by Legendre
                    double sumSinc=0.0d;
                    for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                       // sumSinc += sinc[i]*r_k_alphaValues[i]*exp_beta_rkValues[i]*laguerre(col, beta_rkValues[i]);
                        sumSinc += sinc[i]*r_k_alphaValues[i]*exp_beta_rkValues[i]*laguerre_values[r_vector_size*col + i];
                    }
                    sumSinc *= betaGamma;

                    a_matrix.set(row, col, sumSinc);
                    designMatrix.setEntry(row, col, sumSinc);
                }

                y_vector.set(row,0,tempData.getYValue()); //set data vector
                target[row] = tempData.getYValue();
                qvalues[row] = tempData.getXValue();
            }

        } else {
            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);

                for(int col=0; col < coeffs_size; col++){
                    if (col == 0){ // constant background term

                    } else { // for col >= 1

                    }
                }

                y_vector.set(row,0,tempData.getYValue()); //set data vector
                target[row] = tempData.getYValue();
                qvalues[row] = tempData.getXValue();
            }
        }
    }



    /**
     * Minimize on the absolute value of the coefficients, coeficients can be positive or negative
     * Can include background or not
     */
    public void rambo_coeffs_L2(){

        LaguerreTransform.LinearProblem problem = new LaguerreTransform.LinearProblem(
                designMatrix,
                r_k_alphaValues,
                exp_beta_rkValues,
                laguerre_values,
                qvalues,
                target,
                invVariance,
                r_vector,
                dmax,
                lambda,
                betaGamma,
                includeBackground);

        try{
            NonLinearConjugateGradientOptimizer optimizer = new NonLinearConjugateGradientOptimizer(
                    NonLinearConjugateGradientOptimizer.Formula.FLETCHER_REEVES,
                    new SimpleValueChecker(1e-5, 1e-5)
            );

            double[] guess = new double[coeffs_size]; // approximate using Gaussian
            double sigma = dmax/6.0; // 0 to dmax should be 6 sigma
            double twoVar = 2.0*sigma*sigma;
            double invTwoVar = 1.0/twoVar;
            double prefactor = 1.0/Math.sqrt(Math.PI*twoVar);
            double midpoint = dmax/2.0; // midpoint is mu (average)

            if (includeBackground){
                guess[0] = 0.0000000001;
                for(int i=1; i < coeffs_size; i++){
                    double diff = (r_vector[i-1] - midpoint);
                    guess[i] = prefactor*Math.exp( -(diff*diff)*invTwoVar);
                    //guess[i] = 1;
                }
            } else {
                for(int i=0; i < coeffs_size; i++){
//                    double diff = (r_vector[i] - midpoint);
//                    guess[i] = 100*prefactor*Math.exp( -(diff*diff)*invTwoVar);
//                    System.out.println(i + " guess " + guess[i] );
                    guess[i] = 0.3;
                }
            }

            PointValuePair optimum = optimizer.optimize(new MaxEval(10000),
                    problem.getObjectiveFunction(),
                    problem.getObjectiveFunctionGradient(),
                    GoalType.MINIMIZE,
                    new InitialGuess(guess));

            // initialize coefficient vector
            int totalCoeffs = includeBackground ? coeffs_size: coeffs_size+1;
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
                    coefficients[j] = optimum.getPoint()[j];
                    am_vector.set(j, 0, optimum.getPoint()[j]);
                }
            }

            totalCoefficients = coefficients.length;

            this.setPrDistribution();
            this.calculateIzeroRg();

        } catch (TooManyEvaluationsException ex) {
            System.out.println("TOO Many Evaluations - solution not found");
        } catch (Exception e) {
            System.out.println("Exception occurred " + e.getMessage());
        }
    }


    @Override
    public double calculatePofRAtR(double r_value, double scale){
        return (standardizedScale)*splineFunction.value(r_value)*scale;
    }

    @Override
    void calculateIzeroRg() {
        double tempRgSum = 0, tempRg2Sum=0, xaverage=0;

        XYDataItem item;
        for(int i=1; i<totalInDistribution-1; i++){ // exclude last point
            item = prDistribution.getDataItem(i);
            double rvalue = item.getXValue();
            tempRg2Sum += rvalue*rvalue*item.getYValue()*del_r;
            tempRgSum += item.getYValue()*del_r; // width x height => area
            xaverage += rvalue*item.getYValue()*del_r;
        }

        // trapezoid rule
        double trapesum=0;
        for(int i=0; i<totalInDistribution; i++){ // exclude last point
            trapesum += 2*prDistribution.getDataItem(i).getYValue();
        }

        trapesum *= dmax/(double)ns*0.5;

        rg = Math.sqrt(0.5*tempRg2Sum/tempRgSum);
//        for(int j=0; j< totalCoefficients; j++){
//            sum +=  coefficients[j+1];
//        }
        double sum=0;
        for (int j=0; j < coeffs_size; j++){
            sum += am_vector.get(j);
        }

        izero = tempRgSum*standardizedScale/del_r+standardizedMin;
        //System.out.println("TrapeSum " + trapesum*standardizedScale + " izero " + izero);
        rAverage = xaverage/tempRgSum;
        area = tempRgSum;
    }



    @Override
    void setPrDistribution(){

        prDistribution = new XYSeries("PRDistribution");
        prDistributionForFitting = new XYSeries("OutputPrDistribution");

        //int temp_r_vector_size = 2*r_vector_size - 1; // no background implies coeffs_size == ns
        bin_width = del_r;
        totalInDistribution = r_vector_size+2;

        for(int i=0; i<totalInDistribution; i++){ // values in r_vector represent the midpoint or increments of (i+0.5)*del_r

            if ( i == 0 ) { // interleaved r-value (even)
                prDistribution.add(0, 0);
            } else if (i == (totalInDistribution-1)) {
                prDistribution.add(dmax, 0);
            } else { // odd

                int index = i-1;

                double pr = 0;
                //double betaRkValue = beta_rkValues[index];
                for(int col=0; col < coeffs_size; col++){
                    // sum over sinc multiplied by Legendre
                    pr += am_vector.get(col)*laguerre_values[r_vector_size*col + index];
                        //pr += am_vector.get(col)*laguerre(col, betaRkValue);
                }

                pr *= betaGamma*r_k_alphaValues[index]*exp_beta_rkValues[index];

                prDistribution.add(r_vector[index], pr);
                prDistributionForFitting.add(r_vector[index], pr);
            }
        }

        totalInDistribution = prDistribution.getItemCount();

        this.description  = String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS DIRECT INVERSE FOURIER TRANSFORM OF I(q) %n");
        this.description += String.format("REMARK 265  COEFFICIENTS ARE THE HISTOGRAM HEIGHTS WITH EQUAL BIN WIDTHS %n");
        this.description += String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", del_r);
        setSplineFunction();
    }


    /**
     * call this function before outputing pr distribution
     */
    private void setSplineFunction(){

        double[] totalrvalues = new double[totalInDistribution];
        double[] totalPrvalues = new double[totalInDistribution];


        // coefficients start at r_1 > 0 and ends at midpoint of last bin which is less dmax
        for(int i=0; i < totalInDistribution; i++){
            XYDataItem item = prDistribution.getDataItem(i);
            totalPrvalues[i] = item.getYValue();
            totalrvalues[i] = item.getXValue();
        }

        SplineInterpolator spline = new SplineInterpolator();
        splineFunction = spline.interpolate(totalrvalues, totalPrvalues);
    }

    @Override
    public double calculateQIQ(double qvalue) {

        double sum = coefficients[0]*qvalue;

        double[] sinc = new double[r_vector_size];
        for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
            double r_value = r_vector[i]; // dmax is not represented in this set
            sinc[i] = FastMath.sin(r_value*qvalue)/r_value;
        }


        for(int col=0; col < coeffs_size; col++){
            // sum over sinc multiplied by Legendre
            double sumSinc=0.0d;
            for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                sumSinc += sinc[i]*r_k_alphaValues[i]*exp_beta_rkValues[i]*laguerre_values[r_vector_size*col + i];
            }
            sum += am_vector.get(col)*sumSinc;
        }

        sum *= betaGamma;

        return sum*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return (this.calculateQIQ(qvalue))/qvalue;
    }


    @Override
    public void estimateErrors(XYSeries fittedqIq){

        double[] oldCoefficients = new double[totalCoefficients];
        for(int i=0; i<totalCoefficients; i++){ // copy old coefficients temporarily
            oldCoefficients[i] = coefficients[i]; // will be over written in the estimate
        }

        XYSeries tempPr = new XYSeries("tempPr");
        XYSeries tempPrFit = new XYSeries("for fit");

        try {
            tempPrFit = prDistributionForFitting.createCopy(0, prDistributionForFitting.getItemCount()-1);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        for(int i=0; i<prDistribution.getItemCount(); i++){
            tempPr.add(prDistribution.getDataItem(i));
        }


        int size = fittedqIq.getItemCount();
        double upperq = fittedqIq.getMaxX();
        double bins = totalCoefficients-1;
        double delta_q = upperq/bins;
        double samplingLimit;

        //del_r = prDistribution.getX(2).doubleValue()  - prDistribution.getX(1).doubleValue() ;

        XYDataItem tempData;
        int totalRuns = 71;
        ArrayList<Double> rgValuesList = new ArrayList<>();
        ArrayList<Double> izeroValuesList = new ArrayList<>();


        ArrayList<Integer> countsPerBin = new ArrayList<>();
        int startbb = 0;

        // determine counts per bin to draw from
        // standardize data using specified mean and stdev
        double invStdev = 1.0/standardizedScale;

        for (int b=1; b <= bins; b++) {
            int sumCount = 0;
            double upperBound = delta_q*b;

            binloop:
            for (int bb=startbb; bb < size; bb++){
                double tempCurrent = fittedqIq.getX(bb).doubleValue();

                if ( (tempCurrent >= (delta_q*(b-1)) ) && (tempCurrent < upperBound) ){
                    sumCount += 1.0;
                } else if (tempCurrent >= upperBound ) {
                    startbb = bb;
                    break binloop;
                }
            }
            // you want a better algorithm, by a bad computer
            // greens theorem surface integral
            countsPerBin.add((int)sumCount);
        }

        XYSeries randomSeries = new XYSeries("Random-");

        int upperbb, locale;
        Random randomGenerator = new Random();
        int[] randomNumbers;

        for (int i=0; i < totalRuns; i++){

            randomSeries.clear();
            // randomly grab from each bin
            startbb = 0;
            upperbb = 0;

            for (int b=1; b <= bins; b++){
                // find upper q in bin
                // if s-to-n of bin < 1.5, sample more points in bin
                // if countPerBin < 5, randomly pick 1
                // what if countPerBin = 0?
                if (countsPerBin.get(b-1) > 0){

                    samplingLimit = (1.0 + randomGenerator.nextInt(46))/100.0;  // return a random percent up to ...
                    binloop:
                    for (int bb=startbb; bb < size; bb++){
                        if (fittedqIq.getX(bb).doubleValue() >= (delta_q*b) ){ // what happens on the last bin?
                            upperbb = bb;
                            break binloop;
                        }
                    }

                    // grab indices inbetween startbb and upperbb
                    randomNumbers = Functions.randomIntegersBounded(startbb, upperbb, samplingLimit);
                    startbb = upperbb;

                    // standardize the data
                    for(int h=0; h<randomNumbers.length; h++){
                        locale = randomNumbers[h];
                        tempData = fittedqIq.getDataItem(locale);
                        randomSeries.add(tempData.getXValue(), (tempData.getYValue()-standardizedMin)*invStdev);
                    }
                } // end of checking if bin is empty
            }

            // calculate PofR
            this.createDesignMatrix(randomSeries);

            this.rambo_coeffs_L2();

            // calculate Rg
            double tempRgSum = 0, tempRg2Sum=0, xaverage=0;
            XYDataItem item;
            for(int pr=1; pr < (totalInDistribution-2); pr++){

                item = prDistribution.getDataItem(pr);

                double rvalue = item.getXValue();
                tempRg2Sum += rvalue*rvalue*item.getYValue()*del_r;
                tempRgSum += item.getYValue()*del_r; // width x height => area
                xaverage += rvalue*item.getYValue()*del_r;
            }
            // IZERO estimate
            double sum = coefficients[0];
            for(int j=0; j< r_vector_size; j++){
                sum +=  coefficients[j+1];
            }

            //rgValues[i] = Math.sqrt(0.5*tempRg2Sum/tempRgSum);
            if (tempRg2Sum > 0){
                rgValuesList.add(Math.sqrt(0.5*tempRg2Sum/tempRgSum));
                izeroValuesList.add((sum + standardizedMin)*standardizedScale);
            }
        }
        //double[] rgValues = new double[totalRuns];
        int totalDoubles = rgValuesList.size();
        double[] izeroValues = new double[totalDoubles];
        double[] rgValues = new double[totalDoubles];
        for(int d=0; d<totalDoubles; d++){
            rgValues[d] = rgValuesList.get(d);
            izeroValues[d] = izeroValuesList.get(d);
        }

        DescriptiveStatistics rgStat = new DescriptiveStatistics(rgValues);
        DescriptiveStatistics izeroStat = new DescriptiveStatistics(izeroValues);

        rgError = rgStat.getStandardDeviation()/rgStat.getMean();
        iZeroError = izeroStat.getStandardDeviation()/izeroStat.getMean();

        //RESTORE COEFFICIENTS AND PR DISTRIBUTION
        for(int i=0; i<totalCoefficients; i++){ // copy back the coefficients
            coefficients[i] = oldCoefficients[i];
        }

        prDistribution.clear();
        for(int i=0; i<tempPr.getItemCount(); i++){
            prDistribution.add(tempPr.getDataItem(i));
        }

        prDistributionForFitting.clear();
        for(int i=0; i<tempPrFit.getItemCount(); i++){
            prDistributionForFitting.add(tempPrFit.getDataItem(i));
        }

        totalInDistribution = prDistribution.getItemCount();
        setSplineFunction();
    }

    private static class LinearProblem {

        final RealMatrix factors; // assume this is the design matrix
        final double[] obs; // data
        final double[] invVariance;

        final double dmax, invDmax;
        final int totalqvalues;
        final double[] qvalues;
        final double[] rvalues;
        final double[] r_k_alphaValues;
        final double[] exp_beta_rkValues;
        final double[] laguerre_values;
        final double weight;
        final boolean useBackground;
        final double invP, invN;
        final int totalP, totalRvalues, lastP;
        final int lastRvalue;
        final double betaGamma;

        public LinearProblem(RealMatrix designMatrix, double[] rkalpha, double[] expbetarkvalues, double[]laguerrefunction,  double[] qvalues, double[] target, double[] invVar, double[] regularrvector, double dmax, double weight, double betaGamma, boolean useBackGround) {

            this.factors = designMatrix; // first term will be background if included
            this.r_k_alphaValues = rkalpha;
            this.exp_beta_rkValues = expbetarkvalues;
            this.laguerre_values = laguerrefunction;

            this.qvalues = qvalues;
            this.obs  = target;
            this.invVariance = invVar;
            this.weight = 0.01*weight;
            this.rvalues = regularrvector;
            this.totalRvalues = rvalues.length;
            lastRvalue = totalRvalues-1;

            this.totalP = designMatrix.getColumnDimension();
            this.invP = 1.0/(double)totalP;

            this.lastP = totalP-1;
            this.totalqvalues = qvalues.length;
            this.invN = 1.0/(double)this.totalqvalues;

            this.dmax = dmax;
            this.invDmax = 1.0/dmax;
            this.betaGamma = betaGamma;
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
                    double[] calc = factors.operate(point);

                    for (int i = 0; i < calc.length; ++i) {
                        residual = obs[i] - calc[i];
                        chi2 += residual*residual*invVariance[i];
                    }

                    double sum=0;

                    // calculate second derivative at each point
                    // background is constant, makes no contribution to smoothness of P(r) distribution
                    if (useBackground){
                        // add first term

                    } else {
                        /*
                         * w_i : Laguerre weights
                         *
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |w_i|^2
                         */
//                        sum=0;
//                        for(int index=0; index<totalP; index++){
//                            double squared = point[index];
//                            sum += squared*squared;
//                        }

                        /*
                         * w_i : Laguerre weights
                         *
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P_(ri)|^2
                         */
                        sum=0;
                        for (int r=0; r<totalRvalues; r++){

                            double pr = 0;
                            for(int col=0; col < totalP; col++){
                                pr += point[col]*laguerre_values[totalRvalues*col + r];
                            }
                            pr *= betaGamma*r_k_alphaValues[r]*exp_beta_rkValues[r];
                            sum += pr*pr;
                        }
                    }
                    // add last term to sum
                    //System.out.println("chi " + (invN*chi2) + " " + sum + " " + ((invN*chi2) + weight*sum));
                    return invN*chi2 + weight*sum;
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
                    int total_params = factors.getColumnDimension();
                    double[] del_p = new double[total_params];

                    double[] calc = factors.operate(point);
                    int calcLength = calc.length;
                    double[] residualsInvVar = new double[calcLength];

                    for (int q = 0; q < calcLength; ++q) {
                        residualsInvVar[q] = (obs[q]-calc[q])*invVariance[q];
                    }


                    if (useBackground){
                        // add first term (background)


                    } else {

                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |x|^2
                         */
                        for(int p=0; p < totalP; p++){
                            double sum = 0;
                            for (int q = 0; q < calcLength; ++q) {
                                sum += residualsInvVar[q]*factors.getEntry(q,p);
                            }
                            del_p[p] = -2*sum*invN ;//+ weight*2*point[p];
                        }


                        double[] currentDistribution = new double[totalRvalues];
                        for (int r=0; r<totalRvalues; r++){
                            double pr = 0;
                            for(int col=0; col < totalP; col++){
                                pr += point[col]*laguerre_values[totalRvalues*col + r];
                            }
                            currentDistribution[r] = pr*betaGamma*r_k_alphaValues[r]*exp_beta_rkValues[r];
                        }


                        for(int index=0; index<totalP; index++){ // for a given coefficient, must sum over each difference

                            double value = 0;
                            for (int r=0; r<totalRvalues; r++){
                                value += currentDistribution[r]*laguerre_values[totalRvalues*index + r]*r_k_alphaValues[r]*exp_beta_rkValues[r] ;
                            }
                            // add first and last term
                            del_p[index] += 2*weight*value*betaGamma;
                        }
                    }

                    return del_p;
                }
            });
        }
    }


    private void standardizeErrors(){
        XYDataItem tempData;
        standardVariance = new XYSeries("standardized error");
        int totalItems = data.getItemCount();

        invVariance = new double[totalItems];

        double invstdev = 1.0/standardizedScale;
        double temperrorvalue;

        for(int r=0; r<totalItems; r++){
            tempData = data.getDataItem(r);
            temperrorvalue = errors.getY(r).doubleValue()*invstdev;

            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // get residual for this q-value
            invVariance[r] = 1.0/standardVariance.getY(r).doubleValue();
        }
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



    private double laguerre(int index, double input){

        switch (index){
            case 0:
                return 1;
            default :
                double top = index + (alpha - 1);
                double sum = 0;

                for (int i=0; i<(index+1); i++){
                    sum += Math.pow(-1, i)*binomialCoefficient(top, index - i)*Math.pow(input, i)/CombinatoricsUtils.factorialDouble(i);
                }

                return sum;
        }
    }

    @Override
    public void normalizeDistribution(){
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



    private double binomialCoefficient(double real, int k_integer){

        double value=1;
        for (int i=1; i<=k_integer; i++){
            value *= (real + 1 - i)/(double)i;
        }
        return value;
    }
}
