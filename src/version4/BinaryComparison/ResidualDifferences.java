package version4.BinaryComparison;


import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.BinaryComparison.BinaryComparisonModel;
import version4.Functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Created by xos81802 on 26/06/2017.
 */
public class ResidualDifferences extends BinaryComparisonModel {


    //private XYSeries residuals;
    private double variance, skewness;
    private double scaleFactor;
    private double durbinWatsonStatistic;
    private double ljungBoxStatistic;
    private double shapiroWilkStatistic;
    private int totalResiduals;
    private ArrayList<Double> sortedResiduals;
    private XYSeries binnedData;
    private XYSeries modelData;
    private XYSeries reverseSeries;
    private double invGaussianNormalizationFactor;
    private double inv2variance ;
    private HistogramDataset histogram;
    private double[] pvalue = {0.01, 0.02, 0.05, 0.1, 0.5, 0.9, 0.95, 0.98, 0.99};
    private double[] shapiroWilkPValueTable={0.929, 0.939, 0.947, 0.955, 0.974, 0.985, 0.988, 0.990, 0.991};


    private int lags; // should approximate the degrees of freedom of the data ??? // arbitrary bin size?

    private double[] shapiroWilkTable = {
            0.3770,0.2589,0.2271,0.2038,0.1851,0.1692,0.1553,0.1427,0.1312,0.1205,
            0.1105,0.1010,0.0919,0.0832,0.0748,0.0667,0.0588,0.0511,0.0436,0.0361,
            0.0288,0.0215,0.0143,0.0071
    };


    public ResidualDifferences(
            XYSeries referenceSet,
            XYSeries targetSet,
            XYSeries targetError,
            Number qmin,
            Number qmax,
            int lags,
            int refIndex,
            int tarIndex,
            int order) {

        super(referenceSet, targetSet, targetError, qmin, qmax, "residuals", refIndex, tarIndex, order);
        this.lags = lags;
        this.makeComparisonSeries();
        this.calculateComparisonStatistics();
        // perform some tests on the residuals
        // distribution should be random
        this.createBinnedData();
    }


    /**
     * d = 2 means no autocorrelation
     * d-value should always lie between 0 and 4
     */
    private void calculateDurbinWatson(){

        double numerator=0, value, diff;
        double denominator = testSeries.getY(0).doubleValue()*testSeries.getY(0).doubleValue();

        for(int i=1; i<totalResiduals; i++){
            value = testSeries.getY(i).doubleValue();
            diff = value - testSeries.getY(i-1).doubleValue(); // x_(t) - x_(t-1)
            numerator += diff*diff;
            denominator += value*value; // sum of (x_t)^2
        }

        durbinWatsonStatistic = numerator/denominator;
    }


    /**
     * Treat statistic as a vote that both tests must pass to suggest an acceptance?
     *
     * @return
     */
    @Override
    public double getStatistic(){
        // return ljungBoxStatistic;
        // return durbinWatsonStatistic;
        // shapiroWilkStatistic => 0.7 seems to suggest a good cutoff
        double value;

        if ((durbinWatsonStatistic > 1.9 && durbinWatsonStatistic < 2.1)){
            ////this.printTests("ORDER " + this.order);
            value = 0;
            //return 2.00;
        } else {
            value = (Math.abs(durbinWatsonStatistic-2.0));
            //return durbinWatsonStatistic;
        }

        return value + 2;
    }

    /**
     * Ljung-Box uses sample auto-correlation function
     * p_x(h) = gamma_x(h)/gamma_x(0)
     *
     */
    private void calculateLjungBoxTest(){

        double sum = 0, temp;
        double invGammeAtZero = 1.0/calculateAutoCorrelation(0);

        for(int k=1; k<lags; k++){
            temp = calculateAutoCorrelation(k)*invGammeAtZero;
            sum += temp*temp/(double)(totalResiduals-k);
        }

        ljungBoxStatistic = totalResiduals*(totalResiduals+2)*sum;
    }


    /**
     * Sample autoCovariance, if lag is 0, this is variance
     *
     * defined as:
     * E[(X_(t+h) - avgX_(t+h))*(X_(t) - avgX_(t)]
     *
     * In our case, avgX_(t+h) and avgX_(t) should be same if there is not correlation
     * If the input data is the residual of two curves, then avgX => zero
     *
     * @param lag
     * @return
     */
    private double calculateAutoCorrelation(int lag){
        double gammaSum = 0;
        int limit = totalResiduals - lag;
        for(int t=0; t<limit; t++){
            gammaSum += (testSeries.getY(t+lag).doubleValue()-location)*(testSeries.getY(t).doubleValue() - location);
        }

        return gammaSum*1.0/(double)totalResiduals;
    }

    @Override
    void printTests(String text){
        System.out.println(String.format("%s DW : %.5f LB : %.4E SW : %.5f", text, durbinWatsonStatistic, ljungBoxStatistic, shapiroWilkStatistic));
    }

    public String getSH(){ return String.format("%.4f", shapiroWilkStatistic);}
    public String getDW(){ return String.format("%.4f", durbinWatsonStatistic);}

    public double getLjungBoxStatistic(){ return ljungBoxStatistic;}
    public double getShapiroWilkStatistic(){ return shapiroWilkStatistic;}
    public double getDurbinWatsonStatistic(){ return durbinWatsonStatistic;}

    // Assume residuals are Gaussian, use Kolmogorov–Smirnov to test for normality
    private double calculateKolmogorovSmirnovTest(){
        return 0;
    }


    private void estimateShapiroWilksTest(int rounds){

        ArrayList<Double> estimates = new ArrayList<>(rounds);

        for(int i=0; i<rounds; i++){
            estimates.add(calculateShapiroWilksTest());
        }

        Collections.sort(estimates); // take median

        shapiroWilkStatistic = estimates.get((rounds-1)/2);
        // calculate probabilit y
        // System.out.println("SH " + shapiroWilkStatistic);
    }


    private double calculateShapiroWilksTest(){
        // divide into 11 bins, sample 5 from each bin and calculate
        double lqmin = testSeries.getMinX();
        double lqmax = testSeries.getMaxX();

        int bins = 11;
        int[] perBin = {4,6,6,5,4,4,4,4,4,4,4}; // odd number of elements => 49

        double increment = (lqmax-lqmin)/(double)bins;


        ArrayList<Double> keptResiduals = new ArrayList<>(49);
        int start=0;
        double qvalue;
        double lowerq = lqmin, upperq = lowerq+increment;

        for(int i=0; i<bins; i++){
            ArrayList<Double> selectFrom = new ArrayList<Double>();

            for(int j=start; j<totalResiduals; j++){
                qvalue = testSeries.getX(j).doubleValue();
                if(qvalue >= lowerq && qvalue < upperq){
                    selectFrom.add(testSeries.getY(j).doubleValue());
                } else {
                    start = j;
                    break;
                }
            }

            lowerq = upperq;
            upperq += increment;
            // randomly grab
            Collections.shuffle(selectFrom, new Random(System.nanoTime()));
            for (int m=0; m<perBin[i]; m++){
                keptResiduals.add(selectFrom.get(m).doubleValue());
            }
            selectFrom.clear();
        }

        // throw excepction if keptResiduals is not 49 in lenght
        int totalInKept = keptResiduals.size();
        Collections.sort(keptResiduals);

        /*
         * Sum-of-Squares statistic
         */
        double ss=0, temp;
        for(int s=0; s<totalInKept; s++){
            temp = keptResiduals.get(s) - location;
            //temp = keptResiduals.get(s); // should be zero mean if the two curves are identical
            ss+= temp*temp;
        }

        // calculate distance between extremes
        int limit = (totalInKept-1)/2;
        double b_factor=0;
        for(int s=0; s < limit; s++){
            //System.out.println(s + " | " + keptResiduals.get(totalInKept-1-s) + " " + keptResiduals.get(s));
            b_factor += shapiroWilkTable[s]*(keptResiduals.get(totalInKept-1-s) - keptResiduals.get(s));
        }

        return b_factor*b_factor/ss;
    }


    @Override
    void createBinnedData(){
        // binning
        ArrayList<Double> bins = new ArrayList<>();
        ArrayList<Integer> binned = new ArrayList<>();

        // Scott's Normal Reference Rule
        //final double binWidth = 3.5*Math.sqrt(variance)/Math.pow(totalResiduals,1.0/3.0d);
        final double binWidth = 0.25*Math.sqrt(variance);

        // move left for 10 bins
        final int startIndex = 17;
        binnedData = new XYSeries("Binned Data");
        modelData = new XYSeries("Modeled Data");
        double midpoint;
        int counted = 1;
        for(int binIndex=0; binIndex < startIndex; binIndex++){
            double lower = this.location - (startIndex-binIndex)*binWidth;
            double upper = lower + binWidth;

            int count=0;
            for(int j_index=0; j_index<totalResiduals; j_index++){
                double value = sortedResiduals.get(j_index);

                if (binIndex == 0 && (value < upper)){
                    count++;
                } else if (value >= lower && (value < upper)){
                    count++;
                }
            }
            bins.add(lower);
            binned.add(count);
            binnedData.add(counted, count);

            midpoint = lower+0.5*binWidth;
            modelData.add(midpoint, calculateModel(midpoint));
            counted++;
        }


        for(int binIndex=0; binIndex < startIndex; binIndex++){
            double lower = this.location + binWidth*binIndex;
            double upper = lower + binWidth;

            int count=0;
            for(int j_index=0; j_index<totalResiduals; j_index++){
                double value = sortedResiduals.get(j_index);

                if (binIndex == (startIndex-1) && (value > lower)){
                    count++;
                } else if (value >= lower && (value < upper)){
                    count++;
                }
            }
            bins.add(lower);
            binned.add(count);
            binnedData.add(counted, count);
            midpoint = lower+0.5*binWidth;
            modelData.add(midpoint,calculateModel(midpoint));
            counted++;
        }

        double invln2 = 1.0/Math.log(2);
        double sigmaG = Math.sqrt(6.0*(totalResiduals-2.0)/((totalResiduals+1.0)*(totalResiduals+3.0)));
        int doanes = (int)(1 + Math.log(totalResiduals)*invln2 + Math.log(1 + Math.abs(skewness)/sigmaG)*invln2);

        double[] histoDataArray = new double[totalResiduals];
        for(int j_index=0; j_index<totalResiduals; j_index++){
            histoDataArray[j_index] = sortedResiduals.get(j_index);
        }

        histogram = new HistogramDataset();
        histogram.setType(HistogramType.SCALE_AREA_TO_1);
        histogram.addSeries("Gauss", histoDataArray, doanes);

        // remove first and last points form binnedData
        // these bins will have too many counts since I put all the extremes in one block
        binnedData.remove(0);
        binnedData.remove(binnedData.getItemCount()-1);
    }

    public XYSeries getBinnedData(){
        return binnedData;
    }

    /**
     * calculate residual between reference and target series
     * A scale factor is determined, so that the data should have zero mean if target and reference are the same
     *
     */
    @Override
    void makeComparisonSeries() {

        int totalInReference = this.getReference().getItemCount();
        int startPt=0;
        int endPt=totalInReference-1;

        // find qmin and qmax in reference dataset
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
        XYSeries tempvalues = new XYSeries("tempvalues");
        // calculate scale factor that scales target series to reference series
        double valueE;
        double scale_numerator=0, scale_denominator=0;
        for (int q=startPt; q<=endPt; q++){
            refItem = this.getReference().getDataItem(q);
            double xValue = refItem.getXValue();
            locale = this.getTargetSeries().indexOf(refItem.getX());

            if (locale > -1){
                tarXY = this.getTargetSeries().getDataItem(locale);
                tempvalues.add(tarXY);
                // reference/target
                valueE = this.getTargetError().getY(locale).doubleValue();
                scale_numerator += tarXY.getYValue()*refItem.getYValue()/(valueE*valueE);
                scale_denominator += tarXY.getYValue()*tarXY.getYValue()/(valueE*valueE);
            } else { // interpolate

                if ( (xValue > this.getTargetSeries().getX(1).doubleValue()) || (xValue < this.getTargetSeries().getX(this.getTargetSeries().getItemCount()-2).doubleValue()) ){
                    Double[] results =  Functions.interpolateOriginal(this.getTargetSeries(), this.getTargetError(), xValue);
                    //target.add(xValue, results[1]);
                    tempvalues.add(refItem.getX(), results[1]);
                    scale_numerator += results[1]*refItem.getYValue()/(results[2]*results[2]);
                    scale_denominator += results[1]*results[1]/(results[2]*results[2]);
                }
            }
        }

        scaleFactor = scale_numerator/scale_denominator;

        sortedResiduals = new ArrayList<>();
        double diff, sum=0, sumqsquared=0, sumcubed=0;
        double counter=0;

        // subtract the scaled targetseries from reference
        for (int q=startPt; q<=endPt; q++){
            refItem = this.getReference().getDataItem(q);
            tarXY = tempvalues.getDataItem(tempvalues.indexOf(refItem.getX()));
            diff = (refItem.getYValue() - scaleFactor*tarXY.getYValue());
            //System.out.println(this.refIndex + " " + this.tarIndex + " " + refItem.getXValue() + " <=> " + tarXY.getXValue() + " " + tarXY.getYValue() + " diff " + diff);
            addToTestSeries(refItem.getX(), diff);
            sortedResiduals.add(diff);
            sum += diff;
            sumqsquared += diff*diff;
            sumcubed += diff*diff*diff;
            counter += 1.0;
        }

        //average = sum/counter;
        location = sum/counter; // => average
        variance = sumqsquared/counter - location*location;
        scale = variance;

        invGaussianNormalizationFactor = 1.0/Math.sqrt(2.0*Math.PI*variance);
        inv2variance = 1.0/(2.0*variance);
        skewness = (sumcubed/counter - 3*location*variance - location*location*location)/(variance*Math.sqrt(variance));

        totalResiduals = testSeries.getItemCount();

        Collections.sort(sortedResiduals);
    }

    @Override
    void calculateComparisonStatistics() {
        this.calculateDurbinWatson();
        this.calculateLjungBoxTest();
        this.estimateShapiroWilksTest(11);
    }

    @Override
    double calculateModel(double value){
        double diff = value - location;
        return invGaussianNormalizationFactor*Math.exp(-(diff*diff)*inv2variance);
    }


    public HistogramDataset getHistogram(){ return histogram;}
}
