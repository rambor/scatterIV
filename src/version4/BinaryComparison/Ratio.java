package version4.BinaryComparison;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction;
import org.apache.commons.math3.analysis.solvers.NewtonRaphsonSolver;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.jfree.data.statistics.SimpleHistogramBin;
import org.jfree.data.statistics.SimpleHistogramDataset;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Functions;


import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by xos81802 on 22/06/2017.
 */
public class Ratio extends BinaryComparisonModel {
    //private XYSeries ratio;
    private ArrayList<Double> sortedRatio;
    private int totalInSorted;
    private double medianHLE;
    private double average;
    private double variance, skewness;
    private double location, scale, scale2, estimatedScale;
    private double ljungBoxStatistic, durbinWatsonStatistic, gurtlerHenzeStatistic;
    private int totalInRatio;
    private int lags; // should approximate the degrees of freedom of the data ??? // arbitrary bin size?
    private double minDiff, maxDiff;
    private double piterm;
    private HistogramDataset histogram;
    private SimpleHistogramDataset xybardataset;
    private XYSeries binnedData;
    private XYSeries modelData;
    private double peakHeight;


    /**
     * Ratio of two guassian processes is a Cauchy distribution
     * Cauchy has undefined mean and variance
     * Only possible to test for autocorrelation in the ratio
     *
     * @param referenceSet
     * @param targetSet
     * @param targetError
     * @param qmin
     * @param qmax
     */
    public Ratio(XYSeries referenceSet, XYSeries targetSet, XYSeries targetError, Number qmin, Number qmax, int refIndex, int tarIndex, int ratio) {

        super(referenceSet, targetSet, targetError, qmin, qmax, "ratio", refIndex, tarIndex, ratio);
        lags = 12;

        this.makeComparisonSeries();
        this.estimateCauchyScale();
        // this.testFunction();
        refineLocationAndScale(5);

        if (scale > 0){
            piterm = 1.0/(Math.PI*scale);
            scale2 = scale*scale;
        }

        this.calculateComparisonStatistics();
        //System.out.println("GH " + this.gurtlerHenzeStatistic + " : " + this.totalInRatio);
        this.createBinnedData();
    }


    /**
     * Perform alternating goodness of fit tests for determining Cauchy parameters
     * @param rounds
     */
    private void refineLocationAndScale(int rounds){

        for(int i=0; i<rounds; i++){
            this.solveForLocation();
            this.solveForScale();
        }
    }

    private void solveForLocation(){
        NewtonRaphsonSolver test = new NewtonRaphsonSolver(0.001);
        MLEEquationCauchyLocation tempFunc = new MLEEquationCauchyLocation(this.scale, this.sortedRatio);

        //location = test.solve(1000, tempFunc, this.medianHLE - this.scale, this.medianHLE + this.scale);
        try {
            location = test.solve(1000, tempFunc, this.medianHLE - this.scale, this.medianHLE + this.scale);
        } catch (TooManyEvaluationsException e) {
            System.out.println(e.getMessage());
            location = this.medianHLE;
        }

    }

    private void solveForScale(){
        NewtonRaphsonSolver test = new NewtonRaphsonSolver(0.001);
        MLEEquationCauchyScale tempFunc = new MLEEquationCauchyScale(this.location, this.sortedRatio);

        try {
            scale = test.solve(1000, tempFunc, this.scale - 0.1*this.scale, this.scale + 0.1*this.scale, this.scale);
        } catch (TooManyEvaluationsException e) {
            System.out.println(e.getMessage());
            scale = estimatedScale;
        }

    }


    // ratio of two random processes should fit a distribution (not Gaussian)
    public double getAverage(){return average;}
    public double getVariance(){return variance;}

    // calculate statistics of the ratio
    // no signal, should be flat line (randomly distributed)

    private DescriptiveStatistics averageByMAD(ArrayList<Double> values){

        int total = values.size();
        DescriptiveStatistics stats = new SynchronizedDescriptiveStatistics();

        for (int i=0; i<total; i++) {
            stats.addValue(values.get(i));
        }

        double median = stats.getPercentile(50);
        DescriptiveStatistics deviations = new SynchronizedDescriptiveStatistics();

        ArrayList<Double> testValues = new ArrayList<>(total);

        for (int i=0; i<total; i++){
            testValues.add(Math.abs(values.get(i) - median));
            deviations.addValue(testValues.get(i));
        }

        double mad = 1.4826*deviations.getPercentile(50);
        double invMAD = 1.0/mad;

        // create
        DescriptiveStatistics keptValues = new DescriptiveStatistics();

        for (int i=0; i<total; i++){
            if (testValues.get(i)*invMAD < 2.5 ){
                keptValues.addValue(values.get(i));
            }
        }

        return keptValues;
    }


    /**
     * d = 2 means no autocorrelation
     * d-value should always lie between 0 and 4
     */
    private void calculateDurbinWatson(){

        double numerator=0, value, diff;
        double denominator = testSeries.getY(0).doubleValue()*testSeries.getY(0).doubleValue();

        for(int i=1; i<totalInRatio; i++){
            value = testSeries.getY(i).doubleValue();
            diff = value - testSeries.getY(i-1).doubleValue();
            numerator += diff*diff;
            denominator += value*value;
        }

        durbinWatsonStatistic = numerator/denominator;
    }


    private void calculateLjungBoxTest(){

        double sum = 0, temp;
        double invGammeAtZero = 1.0/calculateAutoCorrelation(0);

        for(int k=1; k<lags; k++){
            temp = calculateAutoCorrelation(k)*invGammeAtZero;
            sum += temp*temp/(double)(totalInRatio-k);
        }

        ljungBoxStatistic = totalInRatio*(totalInRatio+2)*sum;
    }

    /**
     * As described in:
     *
     * Goodness-of-fit test for the cuachy distribution based ont he empirical characteristic function
     * Gurtler and Henze (1998) Ann. Inst. Statist. Math. Vol 52, No. 2, 267-286 (2000)
     *
     * They suggest using lambda of 5.
     *
     * @param lambda
     */
    private void calculateGurtlerHenzeTest(double lambda){
        double doubleSum=0, singleSum=0;
        double outside, diff;
        double inverseScale = 1.0/scale;
        double lambdaSquared = lambda*lambda, lambdaPlus1Squared = (1.0+lambda)*(1.0+lambda);

        for(int j=0; j<totalInRatio; j++){
            outside = (sortedRatio.get(j) - location)*inverseScale;
            singleSum += 1.0/(lambdaPlus1Squared + outside*outside);
            for(int k=0; k<totalInRatio; k++){
                diff = outside - (sortedRatio.get(k) - location)*inverseScale;
                doubleSum += 1.0/(lambdaSquared + diff*diff);
            }
        }

        gurtlerHenzeStatistic =2.0/(double)totalInRatio*lambda*doubleSum - 4.0*(1.0+lambda)*singleSum + 2.0*totalInRatio/(2.0+lambda);
    }


    /**
     * Sample autoCovariance, if lag is 0, this is variance
     * For the ratio (Cauchy distribution), the variance and mean are undefined
     * @param lag
     * @return
     */
    private double calculateAutoCorrelation(int lag){
        double gammaSum =0;
        int limit = totalInRatio - lag;
        for(int t=0; t<limit; t++){
            gammaSum += (testSeries.getY(t+lag).doubleValue()-average)*(testSeries.getY(t).doubleValue() - average);
        }

        return gammaSum*1.0/(double)totalInRatio;
    }


    @Override
    void printTests(String text){
        System.out.println(String.format("%s DW : %.5f LB : %.4E", text, durbinWatsonStatistic, ljungBoxStatistic));
    }


    private void estimateCauchyScale(){

        ArrayList<Double> values = new ArrayList<>(totalInRatio*totalInRatio);

        minDiff = 10000000;
        maxDiff = -10000000;
        double outside;
        for(int i=0; i<totalInRatio; i++){
            outside = sortedRatio.get(i) - medianHLE;
            for(int j=0; j<totalInRatio; j++){
                values.add(Math.log(Math.abs(outside*(sortedRatio.get(j) - medianHLE))));
            }

            double abs = Math.abs(outside);
            if (abs < minDiff){
                minDiff = abs;
            } else if (abs > maxDiff){
                maxDiff = abs;
            }
        }

        int locale = values.size();
        Collections.sort(values);
        double tempScale = 0;
        if ( (locale & 1) == 0 ) { // even
            int middle = (locale)/2;
            tempScale = 0.5*(values.get(middle-1) + values.get(middle));
        } else { // odd
            int middle = (locale-1)/2;
            tempScale = 0.5*(values.get(middle));
        }

        scale =  Math.exp(tempScale);
        estimatedScale = scale;
        //System.out.println("Estimated Scale " + scale + " " + tempScale);
    }

    @Override
    void createBinnedData(){
        // binning
        //
        double binWidth = this.scale * 0.91;
        // move left for 10 bins
        final int startIndex = 13;
        binnedData = new XYSeries("Binned Data");
        modelData = new XYSeries("Modeled Data");
        xybardataset = new SimpleHistogramDataset("Cauchy");
        peakHeight = 0;

        for(int binIndex=0; binIndex < startIndex; binIndex++){
            double lower = this.location - (startIndex-binIndex)*binWidth;
            double upper = lower + binWidth*0.999999;

            xybardataset.addBin(new SimpleHistogramBin(lower, upper, true, false));
            modelData.add((lower+0.5*binWidth), calculateModel(lower+0.5*binWidth));

            int count=0;
            for(int j_index=0; j_index<totalInSorted; j_index++){
                double value = sortedRatio.get(j_index);

                if (binIndex == 0 && (value < upper)){
                    xybardataset.addObservation(lower);
                    count++;
                } else if (value >= lower && (value < upper)){
                    xybardataset.addObservation(lower);
                    count++;
                }
            }
            binnedData.add(lower, count);
            if (count > peakHeight){
                peakHeight = count;
            }
        }

        for(int binIndex=0; binIndex < startIndex; binIndex++){
            double lower = this.location + binWidth*binIndex;
            double upper = lower + binWidth*0.999999;
            modelData.add((lower+0.5*binWidth), calculateModel(lower+0.5*binWidth));
            xybardataset.addBin(new SimpleHistogramBin(lower, upper, true, false));
            int count=0;
            for(int j_index=0; j_index<totalInSorted; j_index++){
                double value = sortedRatio.get(j_index);

                if (binIndex == (startIndex-1) && (value > lower)){
                    xybardataset.addObservation(lower);
                    count++;
                } else if (value >= lower && (value < upper)){
                    xybardataset.addObservation(lower);
                    count++;
                }
            }
            binnedData.add(lower, count);
            if (count > peakHeight){
                peakHeight = count;
            }
        }

        double invln2 = 1.0/Math.log(2);
        double sigmaG = Math.sqrt(6.0*(totalInSorted-2.0)/((totalInSorted+1.0)*(totalInSorted+3.0)));
        int doanes = (int)(1 + Math.log(totalInSorted)*invln2 + Math.log(1 + Math.abs(skewness)/sigmaG)*invln2);

        double[] histoDataArray = new double[totalInSorted];
        for(int j_index=0; j_index<totalInSorted; j_index++){
            histoDataArray[j_index] = sortedRatio.get(j_index);
        }

        histogram = new HistogramDataset();
        histogram.setType(HistogramType.SCALE_AREA_TO_1);
        histogram.addSeries("Cauchy", histoDataArray, 5*doanes);

        // remove first and last points form binnedData
        // these bins will have too many counts since I put all the extremes in one block
        binnedData.remove(0);
        binnedData.remove(binnedData.getItemCount()-1);
    }


    public HistogramDataset getHistogram(){ return histogram;}
    public SimpleHistogramDataset getSimpleHistogramDataset(){ return xybardataset;}
    public XYSeries getBinnedData(){ return binnedData;}
    public XYSeries getModelData(){ return modelData;}

    /**
     * HLE log estimate for variance
     *
     */
    private class CauchyEquation implements UnivariateDifferentiableFunction {
        private double scaleSq;
        private double middle;
        private int totalIn;

        private double invscalePI;

        public CauchyEquation(double scale, double middle) {
            this.invscalePI = scale/(Math.PI);
            this.scaleSq = scale*scale;
            this.middle = middle;
        }

        @Override
        public double value(double x) {
            return value(new DerivativeStructure(1,1,0, x)).getValue();
        }

        @Override
        public DerivativeStructure value(DerivativeStructure t)
                throws DimensionMismatchException {

            DerivativeStructure result = t.subtract(middle).pow(2).add(scaleSq).pow(-1).multiply(invscalePI);
            return result;
        }
    }

    /**
     * HLE log estimate for variance
     *
     */
    private class MLEEquationCauchyLocation implements UnivariateDifferentiableFunction {
        private double scaleSq;
        private ArrayList<Double> x_i;
        private int totalIn;

        public MLEEquationCauchyLocation(double scale, ArrayList<Double> x_i) {
            this.x_i = x_i;
            this.scaleSq = scale*scale;
            this.totalIn = x_i.size();
        }

        @Override
        public double value(double x_o) {
            return value(new DerivativeStructure(1,1,0, x_o)).getValue();
        }

        @Override
        public DerivativeStructure value(DerivativeStructure t)
                throws DimensionMismatchException { // minimize difference as a residual

            DerivativeStructure result = diff(t, 0).multiply(2).divide(diff(t,0).pow(2).add(scaleSq));

            for(int i=1; i<totalIn; i++){
                DerivativeStructure temp = diff(t,i);
                result = result.add(temp.multiply(2).divide(temp.pow(2).add(scaleSq)));
            }
            return result;
        }

        private DerivativeStructure diff(DerivativeStructure t, int index){
            DerivativeStructure x_i_value = new DerivativeStructure(1,1,x_i.get(index));
            return x_i_value.subtract(t);
        }
    }

    /**
     * HLE log estimate for scale
     * returns squared scale term.
     *
     */
    private class MLEEquationCauchyScale implements UnivariateDifferentiableFunction {
        private double locale;
        private ArrayList<Double> x_i;
        private int totalIn;
        private double lastTerm;

        public MLEEquationCauchyScale(double locale, ArrayList<Double> x_i) {
            this.x_i = x_i;
            this.locale = locale;
            this.totalIn = x_i.size();
            this.lastTerm = (double)totalIn*0.5;
        }

        @Override
        public double value(double x_o) {
            return value(new DerivativeStructure(1,1,0, x_o)).getValue();
        }

        @Override
        public DerivativeStructure value(DerivativeStructure t)
                throws DimensionMismatchException { // minimize difference as a residual

            double diff = x_i.get(0) - locale;
            DerivativeStructure denominator = t.pow(2).add(diff*diff).reciprocal();

            for(int i=1; i<totalIn; i++){
                diff = x_i.get(i) - locale;
                denominator = denominator.add(t.pow(2).add(diff*diff).reciprocal());
            }
            return t.pow(2).multiply(denominator).subtract(lastTerm); // returns squared scale
        }
    }

    /**
     * HLE log estimate for location
     *
     */
    private class MLEEquationSimple implements UnivariateDifferentiableFunction {
        private double scaleSq;
        private double[] x_i;
        private double[] y_i;
        private int totalIn;

        public MLEEquationSimple(double scale, double[] x_i, double[] y_i) {
            this.x_i = x_i;
            this.y_i = y_i;
            this.scaleSq = scale*scale;
            this.totalIn = x_i.length;

        }

        @Override
        public double value(double x_o) {
            return value(new DerivativeStructure(1,1,0, x_o)).getValue();
        }

        @Override
        public DerivativeStructure value(DerivativeStructure t)
                throws DimensionMismatchException {

            DerivativeStructure result = diff(t, 0).negate().add(y_i[0]).pow(2);
            for(int i=1; i<totalIn; i++){
                result.add(diff(t, i).negate().add(y_i[i]).pow(2));
            }
            return result;
        }

        private DerivativeStructure diff(DerivativeStructure x, int index){
            return x.negate().add(x_i[index]).pow(2);
        }
    }

    @Override
    void makeComparisonSeries() {

        int totalInReference = this.getReference().getItemCount();
        int startPt=0;
        int endPt=totalInReference-1;

        for(int m=0; m<totalInReference; m++){
            if (this.getReference().getX(m).doubleValue() >= qmin){
                startPt =m;
                break;
            }
        }

        for(int m=endPt; m>0; m--){
            if (this.getReference().getX(m).doubleValue() <= qmax){
                endPt=m;
                break;
            }
        }

        int locale;
        XYDataItem tarXY, refItem;


        sortedRatio = new ArrayList<>();
        // compute ratio within qmin and qmax
        totalInRatio=0;
        double sum=0, sumqsquared=0, value, cubed=0;
        for (int q=startPt; q<=endPt; q++){
            refItem = this.getReference().getDataItem(q);
            double xValue = refItem.getXValue();
            locale = this.getTargetSeries().indexOf(refItem.getX());

            if (locale > -1){
                tarXY = this.getTargetSeries().getDataItem(locale);
                // reference/target
                this.addToTestSeries(refItem.getX(), refItem.getYValue()/tarXY.getYValue());
                value = testSeries.getY(totalInRatio).doubleValue();
                sortedRatio.add(value);
                sum+=value;
                sumqsquared += value*value;
                cubed += value*value*value;
                totalInRatio++;
            } else { // interpolate

                if ( (xValue > this.getTargetSeries().getX(1).doubleValue()) || (xValue < this.getTargetSeries().getX(this.getTargetSeries().getItemCount()-2).doubleValue()) ){

                    Double[] results =  Functions.interpolateOriginal(this.getTargetSeries(), this.getTargetError(), xValue);
                    //target.add(xValue, results[1]);
                    this.addToTestSeries(refItem.getX(), refItem.getYValue()/results[1]);
                    value = testSeries.getY(totalInRatio).doubleValue();
                    sortedRatio.add(value);
                    sum+=value;
                    sumqsquared += value*value;
                    cubed += value*value*value;
                    totalInRatio++;
                }
            }
        }

        Collections.sort(sortedRatio);
        average = sum/(double)totalInRatio;
        variance = sumqsquared/(double)totalInRatio - average*average;
        totalInSorted = sortedRatio.size();
        skewness = (cubed/(double)totalInRatio - 3*average*variance - average*average*average)/(variance*Math.sqrt(variance));

        if ( (totalInSorted & 1) == 0 ) { // even
            int middle = (totalInSorted)/2;
            medianHLE = 0.5*(sortedRatio.get(middle-1) + sortedRatio.get(middle));  // estimator for location
        } else { // odd
            int middle = (totalInSorted-1)/2;
            medianHLE = 0.5*(sortedRatio.get(middle-1) + sortedRatio.get(middle+1));
        }
    }

    @Override
    double getStatistic(){
        //return ljungBoxStatistic;
        return durbinWatsonStatistic;
        //return gurtlerHenzeStatistic;
    }

    @Override
    void calculateComparisonStatistics() {
        // is the ratio of the distribution Cauchy?
        // do both a auto-correlaton test and distribution test
        //this.calculateDurbinWatson(); // auto-correlation test
        this.calculateLjungBoxTest();
       // this.calculateGurtlerHenzeTest(5.0);
    }

    @Override
    double calculateModel(double value){
        double diff = value - location;
        return piterm*scale2/((diff*diff)+scale2);
    }

    private void testFunction(){
        double[] xvalues = {1,2,3,4,5,6,7,8,9,10,11,12};
        double[] yvalues = {12.25, 6.25, 2.25, 0.25, 0.25, 2.25, 6.25, 12.25, 20.25, 30.25, 42.25, 56.25};

        NewtonRaphsonSolver test = new NewtonRaphsonSolver(0.0000001);
        //BrentSolver test = new BrentSolver();
        MLEEquationSimple tempFunc = new MLEEquationSimple(1.0, xvalues, yvalues);
        double found = test.solve(1000, tempFunc, 1, 7);
    }
}
