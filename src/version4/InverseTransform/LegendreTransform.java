package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.DifferentiableMultivariateVectorFunction;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.MultivariateMatrixFunction;
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
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Constants;
import version4.Functions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LegendreTransform extends IndirectFT {

    private double del_r;
    double[] r_vector;
    double[] regularization_r_vector;
    double[] target; // data
    double[] invVariance; // data
    double[] qvalues; // data
    int r_vector_size;
    public final double invDmax;
    private RealMatrix designMatrix; // assume this is the design matrix
    PolynomialFunction[] functions;



    public LegendreTransform(
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
        this.setModelUsed("Legendre L2-NORM");
    }


    public LegendreTransform(
            XYSeries scaledqIqData,
            XYSeries scaledqIqErrors,
            double dmax,
            double qmax,
            double lambda,
            double stdmin,
            double stdscale,
            boolean background){

        super(scaledqIqData, scaledqIqErrors, dmax, qmax, lambda, background, stdmin, stdscale);
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

        this.setModelUsed("Legendre L2-NORM");
    }

    public LegendreTransform(
            XYSeries scaledqIqData,
            XYSeries scaledqIqErrors,
            double[] priors,
            double dmax,
            double qmax,
            double lambda,
            double stdmin,
            double stdscale,
            boolean background){

        super(scaledqIqData, scaledqIqErrors, dmax, qmax, lambda, background, stdmin, stdscale);
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

        this.setModelUsed("Legendre L2-NORM");
    }


    public LegendreTransform(LegendreTransform original){
        super(original);

        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();
        this.target = original.target.clone();
        this.invVariance = original.invVariance;
        this.qvalues = original.qvalues;
        this.r_vector_size = original.r_vector_size;
        this.designMatrix = original.designMatrix.copy();
        this.qvalues = original.qvalues.clone();
        this.target = original.target.clone();
        this.invVariance = original.invVariance.clone();

        this.invDmax = original.invDmax;

         /*
         * set the Legendre Polynomials
         */
        functions = new PolynomialFunction[coeffs_size + 1];
        functions[0] = PolynomialsUtils.createLegendrePolynomial(0);;
        for(int i=1; i < (coeffs_size+1); i++){ // calculate at midpoints
            functions[i] = PolynomialsUtils.createLegendrePolynomial(i); // try odd numbered
        }
    }


    /*
     * datasetInUse does not have to be standardized
     */
    public void createDesignMatrix(XYSeries datasetInuse){

        ns = ((int) (Math.ceil(qmax*dmax*INV_PI) ) )  ;  //

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
        bin_width = del_r;
        // if I think I can squeeze out one more Shannon Number, then I need to define del_r by dmax/ns+1
        r_vector = new double[r_vector_size];

        for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
            r_vector[i] = (0.5 + i)*del_r; // dmax is not represented in this set
        }

        /*
         * calculate r-values for smoothness calculation
         */
        int tempSize = r_vector_size*2-1;
        double temp_del_r = del_r*0.5;
        regularization_r_vector = new double[tempSize];

        for(int i=0; i < tempSize; i++){ // calculate at midpoints
            regularization_r_vector[i] = (1 + i)*temp_del_r; // dmax is not represented in this set
        }

        /*
         * set the Legendre Polynomials
         */
        functions = new PolynomialFunction[coeffs_size + 1];
        functions[0] = PolynomialsUtils.createLegendrePolynomial(0);;
        for(int i=1; i < (coeffs_size+1); i++){ // calculate at midpoints
            functions[i] = PolynomialsUtils.createLegendrePolynomial(i); // try odd numbered
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

        if (!includeBackground) { // no constant background

            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);

                double[] sinc = new double[r_vector_size];
                for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                    double r_value = r_vector[i]; // dmax is not represented in this set
                    sinc[i] = FastMath.sin(r_value*tempData.getXValue()) / r_value;
                }

                double sumSinc=0;
                for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                    sumSinc += sinc[i];
                }

                a_matrix.set(row, 0, sumSinc);
                designMatrix.setEntry(row, 0, sumSinc);

                for(int col=1; col < coeffs_size; col++){
                    // sum over sinc multiplied by Legendre
                    sumSinc=0;
                    for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                        double r_value = r_vector[i]; // dmax is not represented in this set
                        sumSinc += sinc[i]*functions[col].value((2*r_value-dmax)*invDmax);
                    }

                    a_matrix.set(row, col, sumSinc);
                    designMatrix.setEntry(row, col, sumSinc);
                }

                y_vector.set(row,0, tempData.getYValue()); //set data vector
                target[row] = tempData.getYValue();
                qvalues[row] = tempData.getXValue();
            }

        } else {

            for(int row=0; row < rows; row++){ //rows, length is size of data

                XYDataItem tempData = datasetInuse.getDataItem(row);

                double[] sinc = new double[r_vector_size];
                for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                    double r_value = r_vector[i]; // dmax is not represented in this set
                    sinc[i] = FastMath.sin(r_value*tempData.getXValue()) / r_value;
                }

                double sumSinc=0;
                for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                    sumSinc += sinc[i];
                }

                // set background term in matrix
                a_matrix.set(row, 0, 1);
                designMatrix.setEntry(row, 0, 1);
//                a_matrix.set(row, 0, tempData.getXValue());
//                designMatrix.setEntry(row, 0, tempData.getXValue());

                // start of the Legendre terms
                a_matrix.set(row, 1, sumSinc);
                designMatrix.setEntry(row, 1, sumSinc);

                for(int col=2; col < coeffs_size; col++){
                    // sum over sinc multiplied by Legendre
                    sumSinc=0;
                    for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                        double r_value = r_vector[i]; // dmax is not represented in this set
                        sumSinc += sinc[i]*functions[col-1].value((2*r_value-dmax)*invDmax);
                    }

                    a_matrix.set(row, col, sumSinc);
                    designMatrix.setEntry(row, col, sumSinc);
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
    public void solve(){

        LinearProblem problem = new LinearProblem(
                designMatrix,
                functions,
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
                    //guess[0] *= 0.1;
                } else {
                    double guessSum = 0;
                    int window = (int)(totalSubset*0.1);
                    for(int n=(totalSubset-window); n<totalSubset; n++){
                        guessSum += target[n];
                    }

                    guess[0] = guessSum/(double)window*0.1;

                    for(int i=1; i < coeffs_size; i++){
                        double diff = (r_vector[i-1] - midpoint);
                        //guess[i] = prefactor*Math.exp( -(diff*diff)*invTwoVar);
                        guess[i] = 0.71;
                    }
                    guess[1] = 0.9;
                }

            } else {

                if (priorExists){
                    for(int i=0; i < coeffs_size; i++){
                        guess[i] = prior_coefficients[i+1];
                    }
                } else {
                    for(int i=0; i < coeffs_size; i++){
                        double diff = (r_vector[i] - midpoint);
                        guess[i] = 0.13;
                    }
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
           // finalScore = optimum.getValue()/(double)rows;

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

            //System.out.println("Scaled Backgnd " +  (coefficients[0]*standardizedScale + standardizedMin));
            totalCoefficients = coefficients.length;
            this.setPrDistribution();
            this.calculateIzeroRg();
            //this.calibratePrDistribution();
            //System.out.println("SCORE " + this.scoreDistribution(this.del_r));
        } catch (TooManyEvaluationsException ex) {
            System.out.println("TOO Few Evaluations");
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
            tempRg2Sum += rvalue*rvalue*item.getYValue();
            tempRgSum += item.getYValue(); // width x height => area
            xaverage += rvalue*item.getYValue();
        }

//        tempRg2Sum *= del_r;
//        tempRgSum *= del_r; // width x height => area
//        xaverage *= del_r;

        rg = Math.sqrt(0.5*tempRg2Sum/tempRgSum);

//        double cosum = 2*coefficients[1] + coefficients[2] + 5/4*coefficients[4] + 3/2*coefficients[4];
//        cosum *= 2/dmax;

//        izero = tempRgSum*standardizedScale + standardizedMin;
        izero = (tempRgSum + coefficients[0])*standardizedScale + standardizedMin;
//        System.out.println(" IZERO A " + ((tempRgSum + coefficients[0])*standardizedScale + standardizedMin) + " " + izero);
//        System.out.println(" IZERO B " + cosum + " " + (cosum*standardizedScale + standardizedMin));
//        System.out.println("IZERO :: " + (coefficients[1]*2*standardizedScale + standardizedMin) + " " + (coefficients[1]*2));
        rAverage = xaverage/tempRgSum;
        area = tempRgSum;
    }



    @Override
    void setPrDistribution(){

        prDistribution = new XYSeries("PRDistribution");
        prDistributionForFitting = new XYSeries("OutputPrDistribution");

        //int temp_r_vector_size = 2*r_vector_size - 1; // no background implies coeffs_size == ns
        int temp_r_vector_size = r_vector_size; // no background implies coeffs_size == ns
        double temp_del_r = del_r;
        bin_width = del_r;
        double[] temp_r_vector = new double[temp_r_vector_size];

        for(int i=0; i < temp_r_vector_size; i++){ // calculate at midpoints
            temp_r_vector[i] = (0.5 + i)*temp_del_r; // dmax is not represented in this set
        }

        // System.out.println("Dmax " + dmax + " > " + temp_r_vector[temp_r_vector_size-1]);
        totalInDistribution = temp_r_vector_size+2;

        for(int i=0; i<totalInDistribution; i++){ // values in r_vector represent the midpoint or increments of (i+0.5)*del_r

            if ( i == 0 ) { // interleaved r-value (even)
                prDistribution.add(0, 0);
            } else if (i == (totalInDistribution-1)) {
                prDistribution.add(dmax, 0);
            } else { // odd

                int index = i-1;
                double pr=coefficients[1];
                for (int a=2; a<totalCoefficients; a++){
                    pr += coefficients[a]*functions[a-1].value((2*temp_r_vector[index]-dmax)*invDmax);
                }

                prDistribution.add(temp_r_vector[index], pr);
 //               prDistributionForFitting.add(temp_r_vector[index], pr);
            }
        }


        totalInDistribution = prDistribution.getItemCount();

        // populate prDistributionForFitting
        double width = dmax/(double)ns;

        double rvalue =  0.5*width;
        while (rvalue < dmax){

            double pr=coefficients[1];
            for (int a=2; a<totalCoefficients; a++){
                pr += coefficients[a]*functions[a-1].value((2*rvalue-dmax)*invDmax);
            }
            prDistributionForFitting.add(rvalue, pr);
            rvalue += width;
        }

        scoreDistribution(del_r);
        setHeaderDetails();
//        this.calculateSphericalCalibration(); // sets the distribution for calibration
        setSplineFunction();
    }


    private void setHeaderDetails(){
        this.description  = String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS INDIRECT INVERSE FOURIER TRANSFORM OF I(q) %n");
        this.description  = String.format("REMARK 265  P(r) APPROXIMATED AS A LEGENDRE POLYNOMIAL %n");
        this.description += String.format("REMARK 265  COEFFICIENTS ARE LEGENDRE WEIGHTS FOR EACH POLYNOMIAL %n");
        this.description += String.format("REMARK 265 %n");
        this.description += String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", (dmax/(double)ns));
        this.description += String.format("REMARK 265            DISTRIBUTION SCORE : %.4f %n", prScore);
        this.description += String.format("REMARK 265 %n");
        if (!includeBackground){
            this.description += String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n");
        } else {
            this.description += String.format("REMARK 265        CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]);
            this.description += String.format("REMARK 265            SCALED  s*m(0) + min : %.4E %n", (coefficients[0]*standardizedScale + standardizedMin));
        }
        this.description += String.format("REMARK 265  LEGENDRE COEFFICIENTS (UNSCALED)%n");
        for (int i=1; i<totalCoefficients;i++){
            this.description +=  String.format("REMARK 265                        l_(%2d) : %.3E %n", i-1, coefficients[i]);
        }
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

    /**
     * returns unscaled Intensity data where scaled refers to standardized data
     * Using the trapezoid rule, should actually integrate the function and determine the recursion relation - expect errors in extrapolation?
     * @param qvalue
     * @return
     */
    @Override
    public double calculateQIQ(double qvalue) {

        double sum = coefficients[0];//*qvalue; // background term

        double[] sinc = new double[r_vector_size];
        for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
            double r_value = r_vector[i]; // dmax is not represented in this set
            sinc[i] = FastMath.sin(r_value*qvalue) / r_value;
        }
        double sumSinc=0;
        for(int i=0; i < r_vector_size; i++){ // Legendre at k=0 is 1 (so sum th
            sumSinc += sinc[i];
        }

        sum += coefficients[1]*sumSinc;
        for (int a=2; a<totalCoefficients; a++){

            sumSinc=0;
            for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
                double r_value = r_vector[i]; // dmax is not represented in this set
                //sumSinc += sinc[i]*functions[a].value((2*r_value-dmax)*invDmax);
                sumSinc += sinc[i]*functions[a-1].value((2*r_value-dmax)*invDmax);
            }
            sum += coefficients[a]*sumSinc;
        }

//
//        for(int i=0; i < r_vector_size; i++){ // calculate at midpoints
//            r_vector[i] = (0.5 + i)*del_r; // dmax is not represented in this set
//        }

//        double temp_del_r = 0.5*del_r;
//        double startr = temp_del_r*0.5;
//        while(startr < dmax){
//            double p_at_r = coefficients[1];
//            for (int a=2; a<totalCoefficients; a++){
//                p_at_r += coefficients[a]*functions[a-1].value((2*startr-dmax)*invDmax);
//            }
//            sum += FastMath.sin(startr*qvalue) / startr * p_at_r;
//            System.out.println(startr + " " + p_at_r);
//            startr+= temp_del_r;
//        }

//        System.out.println("ratio " + del_r/temp_del_r);
        // perform the integration
        //System.out.println((coefficients[1]) + " " + (coefficients[1]*standardizedScale+ standardizedMin) + " -- " + (coefficients[1]*standardizedScale));

//        System.out.println(" Izero :: " + (coefficients[1]*dmax*standardizedScale + standardizedMin));
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

            this.solve();

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
        final double weight;
        final boolean useBackground;
        final PolynomialFunction[] functions;
        final double[] legendreTable;
        final int totalP, totalRvalues;
        final int lastRvalue;

        public LinearProblem(RealMatrix designMatrix, PolynomialFunction[] functions, double[] qvalues, double[] target, double[] invVar, double[] regularrvector, double dmax, double weight, boolean useBackGround) {

            this.factors = designMatrix; // first term will be background if included
            this.functions = functions;
            this.qvalues = qvalues;
            this.obs  = target;
            this.invVariance = invVar;

            //double val = regularrvector[2] - regularrvector[1]; // delta_r
            this.weight = weight;///(val*val);
            this.rvalues = regularrvector;
            this.totalRvalues = rvalues.length;
            lastRvalue = totalRvalues-1;

            this.totalP = designMatrix.getColumnDimension();

            this.totalqvalues = qvalues.length;

            this.dmax = dmax;
            this.invDmax = 1.0/dmax;

            this.useBackground = useBackGround;

             // with background this will be too large first value in totalP is background
            int count=0;

            /*
             * create lookup table
             */
            if (this.useBackground){
                this.legendreTable = new double[(totalP-1)*totalRvalues]; // remove count due to background term
                for (int r=0; r<totalRvalues; r++){
                    legendreTable[count] = 1;
                    count+=1;
                    for(int index=1; index<(totalP-1); index++){
                        legendreTable[count] = (functions[index].value((2*rvalues[r]-dmax)*invDmax)) ;
                        count+=1;
                    }
                }

            } else {
                this.legendreTable = new double[totalP*totalRvalues];
                for (int r=0; r<totalRvalues; r++){
                    legendreTable[count] = 1;
                    count+=1;
                    for(int index=1; index<totalP; index++){
                        legendreTable[count] = (functions[index].value((2*rvalues[r]-dmax)*invDmax)) ;
                        count+=1;
                    }
                }
            }
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
                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P_(i)|^2
                         */
//                        double diff;
//                        sum=0;
//                        int tableCount=0;
//                        for (int r=0; r<totalRvalues; r++){
//                            diff = point[1];
//                            tableCount+=2;
//                            for(int index=2; index<totalP; index++){
////                                diff += point[index]*(functions[index-1].value((2*rvalues[r]-dmax)*invDmax)) ;
//                                diff += point[index]*(legendreTable[tableCount]);
//                                tableCount+=1;
//                            }
//                            sum+= diff*diff;
//                        }

                        /*
                         * minimize gradietn of SUM + |P_(i+1) - P_(i)|^2
                         */
                        double diff;
                        sum=0;
                        int stop = totalRvalues-1;
//                        int counter = 0;
//                        for (int r=0; r<stop; r++){
//                            diff = 0;
//                            for(int index=1; index<(totalP-1); index++){
//                               // System.out.println(index + " -> " + functions[index].value((2*rvalues[r+1]-dmax)*invDmax) + " " + legendreTable[(totalP-1)*(r+1)+index]);
//                                diff += point[index+1]*(legendreTable[(totalP-1)*(r+1)+index] - legendreTable[(totalP-1)*r+index]) ;
//                               // diff += point[index]*(functions[index].value((2*rvalues[r+1]-dmax)*invDmax) - functions[index].value((2*rvalues[r]-dmax)*invDmax)) ;
//                                counter+=1;
//                            }
//                            sum+= diff*diff;
//                        }

                        /*
                         * minimize gradietn of SUM |P_i - 0.5*( P_(i-1)+P_(i+1) )|^2 + 0.5*P_i^2 + 0.5*P_N^2
                         */
                        //int stop = totalRvalues-1
                        for (int r=1; r<stop; r++){
                            diff = 0;
                            for(int index=2; index<(totalP-1); index++){
                                diff += point[index+1]*(legendreTable[(totalP-1)*r+index] - 0.5*(legendreTable[(totalP-1)*(r-1)+index] +  legendreTable[(totalP-1)*(r+1)+index])) ;
                            }
                            sum+= diff*diff;
                        }
                        // add the last two terms
                        //diff = 0;
                        diff = point[1];
                        for(int index=1; index<(totalP-1); index++){
                            diff += point[index+1]*(legendreTable[index]) ;
                        }
                        sum += 0.5*diff*diff;

                        //diff = 0;
                        diff = point[1];
                        for(int index=1; index<(totalP-1); index++){
                            diff += point[index+1]*(legendreTable[(totalP-1)*stop+index]) ;
                        }
                        sum += 0.5*diff*diff;

                    } else {
                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P_(i+1) - P_(i)|^2
                         */
                        double diff;
                        sum=0;
                        int stop = totalRvalues-1;
//                        int tableCount=0;
//                        for (int r=0; r<stop; r++){
//                            diff = 0;
//                            for(int index=1; index<totalP; index++){
//                                // totalRvalues*(r+1)+index
//                                diff += point[index]*(legendreTable[totalRvalues*(r+1)+index] - legendreTable[totalRvalues*r+index]);
//                                //diff += point[index]*(functions[index].value((2*rvalues[r+1]-dmax)*invDmax) - functions[index].value((2*rvalues[r]-dmax)*invDmax)) ;
//                                tableCount +=1;
//                            }
//                            sum+= diff*diff;
//                        }


                        /*
                         * minimize gradietn of SUM |P_i - 0.5*( P_(i-1)+P_(i+1) )|^2 + 0.5*P_i^2 + 0.5*P_N^2
                         */
                        //int stop = totalRvalues-1
                        for (int r=1; r<stop; r++){
                            diff = 0;
                            for(int index=1; index< totalP; index++){
                                diff += point[index]*(legendreTable[totalP*r+index] - 0.5*(legendreTable[totalP*(r-1)+index] +  legendreTable[totalP*(r+1)+index])) ;
                            }
                            sum+= diff*diff;
                        }
                        // add the last two terms, r => r[0]
                        diff = point[0];
                        for(int index=1; index < totalP; index++){
                            diff += point[index]*legendreTable[index];
                        }
                        sum += 0.5*diff*diff;

                        diff = point[0]; // r => r[last]
                        for(int index=1; index < totalP; index++){
                            diff += point[index]*legendreTable[totalP*stop+index];
                        }
                        sum += 0.5*diff*diff;


                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P_(i)|^2
                         */
//                        double diff;
//                        sum=0;
//                        int tableCount = 0;
//
//                        for (int r=0; r<totalRvalues; r++){
//                            diff = point[0]; // 1st element constant value
//                            tableCount+=1;
//                            for(int index=1; index<totalP; index++){
//                                diff += point[index]*legendreTable[tableCount];
//                                tableCount +=1;
//                            }
//                            sum+= diff*diff;
//                        }

                    }
                    // add last term to sum
                    //System.out.println("chi " + (invN*chi2) + " " + sum + " " + ((invN*chi2) + weight*invP*sum));
                    return chi2 + weight*sum;
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

                    /*
                     * Calculate Residuals as (qI_obs - qI_calc)/invVariance[q]
                     */
                    double residualSum = 0;
                    //double qsum = 0;
                    for (int q = 0; q < calcLength; ++q) {
                        residualsInvVar[q] = (obs[q]-calc[q])*invVariance[q];
                        residualSum += residualsInvVar[q];
                       // qsum+=factors.getEntry(q,0)*residualsInvVar[q];
                    }


                    if (useBackground){
                        // add first term (background)
                        del_p[0] = -2*residualSum; // background term
//                        del_p[0] = -2*qsum; // background term

                        for(int p=1; p < totalP; p++){
                            double sum = 0;
                            for (int q = 0; q < calcLength; ++q) {
                                sum += residualsInvVar[q]*factors.getEntry(q,p);
                            }
                            del_p[p] = -2*sum;
                        }


                        double[] currentDistribution = new double[totalRvalues];
                        int tablCount=0;
                        for(int r=0; r<totalRvalues; r++){
                            double value = point[1];
                            tablCount+=1;
                            for(int index=1; index<(totalP-1); index++){
//                                value += point[index]*(functions[index-1].value((2*rvalues[r]-dmax)*invDmax)) ;
                                value += point[index+1]*legendreTable[tablCount];
                                //System.out.println(functions[index].value((2*rvalues[r]-dmax)*invDmax) + " " + (legendreTable[tablCount]));
                                tablCount+=1;
                            }
                            currentDistribution[r] = value;
                        }

                        double val;

                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P[r]|^2
                         * index = 0 is background in P
                         */
//                        for(int index=1; index<totalP; index++){ // for a given coefficient, must sum over each difference
//
//                            double diff = 0;
//                            for (int r=0; r<totalRvalues; r++){
//                                diff += (currentDistribution[r])*(functions[index-1].value((2*rvalues[r]-dmax)*invDmax)) ;
//                            }
//                            // add first and last term
//                            del_p[index] += 2*weight*diff;
//                        }


                        /*
                         * minimizine on Sum of |P[r_i+1] - P[r]|^2
                         */
//                        for(int index=1; index<(totalP-1); index++){ // for a given coefficient, must sum over each difference
//                            val = 0;
//                            for(int r=0; r<(totalRvalues-1); r++){
//                                //val += 2*(currentDistribution[r+1] - currentDistribution[r])*(legendreTable[totalRvalues*(r+1)+index] - legendreTable[totalRvalues*r+index]);
//                                val += 2*(currentDistribution[r+1] - currentDistribution[r])*(legendreTable[(totalP-1)*(r+1)+index] - legendreTable[(totalP-1)*r+index]);
//                            }
//                            del_p[index] += weight*val;
//                        }


                        val = 0;
                        del_p[1] += weight*(currentDistribution[0] + currentDistribution[totalRvalues-1]);


                        for(int index=2; index<(totalP-1); index++){ // for a given coefficient, must sum over each difference
                            val = 0;
                            for(int r=1; r<(totalRvalues-1); r++){
                                val += (currentDistribution[r] - 0.5*(currentDistribution[r+1] + currentDistribution[r-1]))*(legendreTable[(totalP-1)*r+index] - 0.5*(legendreTable[(totalP-1)*(r+1)+index] + legendreTable[(totalP-1)*(r-1)+index]));
                            }
                            del_p[index] += weight*(2*val + currentDistribution[0]*legendreTable[index] + currentDistribution[totalRvalues-1]*(legendreTable[(totalP-1)*(totalRvalues-1)+index]));
                        }



                    } else {
                        /*
                         * minimize gradient of Sum (qI_obs - qI_calc)^2 + |P[r]|^2
                         */
                        for(int p=0; p < totalP; p++){
                            double sum = 0;
                            for (int q = 0; q < calcLength; ++q) {
                                sum += residualsInvVar[q]*factors.getEntry(q,p);
                            }
                            del_p[p] = -2*sum;
                        }

                        // add second term to each del_p
                        // calculate P(r) for each r-value

                        int tableCount=0;

                        double[] currentDistribution = new double[totalRvalues];
                        for(int r=0; r<totalRvalues; r++){
                            double value = point[0];
                            tableCount+=1;
                            for(int index=1; index<totalP; index++){
//                                value += point[index]*(functions[index].value((2*rvalues[r]-dmax)*invDmax)) ;
                                value += point[index]*legendreTable[tableCount];
                                tableCount+=1;
                            }
                            currentDistribution[r] = value;
                        }

                        final double tweight = 2*weight;
                        /*
                         * gradient of |P[r]|^2
                         */

//                        for(int index=0; index<totalP; index++){ // for a given coefficient, must sum over each difference
//
//                            double diff = 0;
//                            for (int r=0; r<totalRvalues; r++){
//                                //diff += (currentDistribution[r])*(functions[index].value((2*rvalues[r]-dmax)*invDmax)) ;
//                                diff += (currentDistribution[r])*legendreTable[totalRvalues*r+index];
//                            }
//                            // add first and last term
//                            del_p[index] += tweight*diff;
//                        }

                        /*
                         * gradient of |P[r_i+1] - P[r]|^2
                         * no background term
                         */
//                        double val;
//                        for(int index=1; index<totalP; index++){ // for a given coefficient, must sum over each difference
//                            val = 0;
//                            for(int r=0; r<(totalRvalues-1); r++){
////                                val += (currentDistribution[r+1] - currentDistribution[r])*(legendreTable[totalRvalues*(r+1)+index] - legendreTable[totalRvalues*r+index]);
//                                val += (currentDistribution[r+1] - currentDistribution[r])*(legendreTable[totalP*(r+1)+index] - legendreTable[totalP*r+index]);
//                            }
//                            del_p[index] += tweight*val;
//                        }


                        double val;
                        for(int index=0; index<totalP; index++){ // for a given coefficient, must sum over each difference
                            val = 0;
                            for(int r=1; r<(totalRvalues-1); r++){
                                val += (currentDistribution[r] - 0.5*(currentDistribution[r+1] + currentDistribution[r-1]))*(legendreTable[totalP*r+index] - 0.5*(legendreTable[totalP*(r+1)+index] + legendreTable[totalP*(r-1)+index]));
                            }
                            del_p[index] += weight*(2*val + currentDistribution[0]*legendreTable[index] + currentDistribution[totalRvalues-1]*(legendreTable[totalP*(totalRvalues-1)+index]));
                        }

                    }

                    return del_p;
                }
            });
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

}
