package version4.InverseTransform;

import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.RealSpace;
import version4.RefinedPlot;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RefinePrManager extends SwingWorker<Void, Void> {

    private int numberOfCPUs;
    private RealSpace dataset;
    private int refinementRounds;
    private double invRefineMentRounds;
    private int roundsPerCPU;
    private int bins;
    private IndirectFT keptIFTModel;
    private double rejectionCutOff;
    private double lambda;
    private double s_o;
    private double dmax;

    private boolean useLegendre = false;

    private double pi_invDmax;
    private double upperq;
    private final boolean useL1;
    private final double[] priorCoefficients;
    private final boolean useMoore;
    private final boolean useSVD;
    private final boolean useL2;
    private JProgressBar bar;
    private final boolean useBackgroundInFit;
    private final boolean positiveOnly;
    private JLabel statusLabel;
    private boolean useLabels = false;

    private boolean isFinished = false;

    private int cBoxValue;
    private XYSeries keptSeries;
    private XYSeries keptErrorSeries;
    private XYSeries standardizedSeries;
    private XYSeries scaled_q_times_Iq_Errors;
    private double standardizedMin;
    private double standardizedScale;
    private AtomicInteger counter = new AtomicInteger(0);
    private AtomicBoolean isNewModel;
    private double median;

    public RefinePrManager(RealSpace dataset,
                           int numberOfCPUs,
                           int refinementRounds,
                           double rejectionCutOff,
                           double lambda,
                           int cBoxValue,
                           boolean useMoore,
                           boolean useL1,
                           boolean useLegendre,
                           boolean useL2,
                           boolean useSVD,
                           boolean includeBackground,
                           boolean positiveOnly){

        this.numberOfCPUs = numberOfCPUs;
        this.dataset = dataset;

        this.priorCoefficients = dataset.getCoefficients().clone();

        this.refinementRounds = refinementRounds;
        this.invRefineMentRounds = 1.0/(double)refinementRounds;

        this.isNewModel = new AtomicBoolean(false);

        this.roundsPerCPU = (int)(this.refinementRounds/(double)this.numberOfCPUs);
        this.rejectionCutOff = rejectionCutOff;

        this.cBoxValue = cBoxValue;
        this.lambda = lambda;

        this.useMoore = useMoore;
        this.useL1 = useL1;
        this.useLegendre = useLegendre;
        this.useL2 = useL2;
        this.useSVD = useSVD;

        this.useBackgroundInFit = includeBackground;
        this.positiveOnly = positiveOnly;

        this.upperq = dataset.getfittedqIq().getMaxX();
        this.bins = dataset.getTotalFittedCoefficients();
        this.dmax = dataset.getDmax();

        this.pi_invDmax = Math.PI/dmax;

        this.standardizedSeries = new XYSeries("Standard set");
        this.scaled_q_times_Iq_Errors = new XYSeries("fitted errors");
        this.standardizeData();

        //dataset.setStandardizationMean(this.standardizedMin, this.standardizedScale);
        this.median = dataset.getIndirectFTModel().calculateMedianResidual(standardizedSeries);
        System.out.println("start med " + this.median);
    }


    @Override
    protected Void doInBackground() throws Exception {

        if (useLabels){
            statusLabel.setText(String.format("Starting median residual :: %.5E", median));
        }

        //bar.setMaximum(100);
        /*
         * optimize lambda - find lambda with best PrScore
         */

        final double upperLambda = 300*lambda;
        double startLamba = lambda;
        double keptlamba = lambda;
        double dellamba = (upperLambda - startLamba)/101;
        double del_r = Math.PI/upperq;

        double initialScore = dataset.getIndirectFTModel().scoreDistribution(del_r);
        //System.out.println("PRIOR [0] " + priorCoefficients[0]);
        while (startLamba < upperLambda){
            IndirectFT tempIFT;

            if (useMoore){
                //tempIFT = new MooreTransform(this.standardizedSeries, scaled_q_times_Iq_Errors, priorCoefficients, dmax, upperq, startLamba, true, useBackgroundInFit, standardizedMin, standardizedScale);
                tempIFT = new MooreTransformApache(this.standardizedSeries, scaled_q_times_Iq_Errors, this.priorCoefficients, dmax, upperq, startLamba, standardizedMin, standardizedScale, useBackgroundInFit);
            } else if (useL1 && !useLegendre && !useL2 && !useSVD){
                tempIFT = new SineIntegralTransform(this.standardizedSeries, scaled_q_times_Iq_Errors, this.priorCoefficients, dmax, upperq, startLamba, useBackgroundInFit, positiveOnly, standardizedMin, standardizedScale);
            } else if (useLegendre && !useL2 && !useSVD) {
                tempIFT = new LegendreTransform(this.standardizedSeries, scaled_q_times_Iq_Errors, priorCoefficients, dmax, upperq, startLamba, standardizedMin, standardizedScale, useBackgroundInFit);
            } else if (useL2 && !useSVD) {  // use Moore Method
                tempIFT = new DirectSineIntegralTransform(this.standardizedSeries, scaled_q_times_Iq_Errors, dmax, upperq, startLamba, standardizedMin, standardizedScale, useBackgroundInFit);
            } else if (useSVD) {  // use Moore Method
                tempIFT = new SVD(this.standardizedSeries, scaled_q_times_Iq_Errors, dmax, upperq, startLamba, useBackgroundInFit, standardizedMin, standardizedScale);
            } else {
                tempIFT = new SVD(this.standardizedSeries, scaled_q_times_Iq_Errors, dmax, upperq, startLamba, useBackgroundInFit, standardizedMin, standardizedScale);
            }
            double prscore = tempIFT.scoreDistribution(del_r);
            if (prscore < initialScore){
                keptlamba = startLamba;
                initialScore = prscore;
                lambda = keptlamba;
            }
            startLamba += dellamba;
        }


        ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfCPUs);
        for (int i=0; i < numberOfCPUs; i++){
            Runnable bounder = new RefinePrManager.Refiner(
                    this.standardizedSeries,
                    this.scaled_q_times_Iq_Errors,
                    priorCoefficients,
                    roundsPerCPU,
                    statusLabel);
            executor.execute(bounder);
        }

        executor.shutdown();

        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            bar.setValue(0);
            bar.setStringPainted(false);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException " + e.getMessage());
            notifyUser("Failed settings limits, exceeded thread time");
        }

        //update realspaceModel
        //dataset.setIndirectFTModel(keptIFTModel);

        notifyUser("Finished Refining");

        if (isNewModel.get()){

            createKeptSeries();

        } else { // update refined sets with same datasets if no new model found;

            int size = dataset.getfittedqIq().getItemCount();
            keptErrorSeries = new XYSeries("same model");
            XYDataItem item;

            for (int i=0; i < size; i++){
                item = dataset.getfittedqIq().getDataItem(i);
                keptErrorSeries.add(item.getX(), dataset.getErrorAllData().getY(dataset.getErrorAllData().indexOf(item.getX())));
            }

            dataset.updatedRefinedSets(dataset.getfittedIq(), keptErrorSeries);
            updateText(String.format("Refinement failed, no improvement try increase number of rounds"));
        }


        synchronized (this) {
            isFinished = true;
            notifyAll();
        }
        notifyUser("Final Kept Series Created");

        return null;
    }

    /**
     *
     * @return
     */
    public synchronized double getMedian() {
        return this.median;
    }

    private synchronized void setMedian(double value) {
        median = value;
    }

    private synchronized void setIFTObject(IndirectFT iftObject, double value) {
        isNewModel.set(true);
        keptIFTModel = iftObject;
        setMedian(value);
    }

    public void setBar(JProgressBar bar, JLabel prStatusLabel){
        this.bar = bar;
        this.bar.setValue(0);
        this.bar.setStringPainted(true);
        this.bar.setMaximum(100);
        this.statusLabel = prStatusLabel;
        useLabels = true;
    }

    public boolean getIsFinished() {
        return isFinished;
    }

    void updateText(final String text){
        statusLabel.setText(text);
    };

    private synchronized void notifyUser(final String text){
        if (useLabels){
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateText(text);
                }
            });
        } else {
            System.out.println(text);
        }
    }

    /**
     *
     * standardize data
     */
    private void standardizeData(){
        XYDataItem tempData, tempError;
        XYSeries fitteddqIqRegion = dataset.getfittedqIq();

        int totalItems = fitteddqIqRegion.getItemCount();
        standardizedMin = dataset.getStandardizationMean();
        standardizedScale = dataset.getStandardizationStDev();
        double invstdev = 1.0/standardizedScale;

        for(int r=0; r<totalItems; r++){
            tempData = fitteddqIqRegion.getDataItem(r);
            //scaled_q_times_Iq_Errors.add(dataset.getErrorAllData().getDataItem(dataset.getErrorAllData().indexOf(tempData.getX())));
            tempError = dataset.getErrorAllData().getDataItem(dataset.getErrorAllData().indexOf(tempData.getX()));
            scaled_q_times_Iq_Errors.add(tempData.getX(), tempError.getYValue()*tempError.getXValue()*invstdev);  // q*sigma/scale
            standardizedSeries.add(tempData.getX(), (tempData.getYValue() - standardizedMin)*invstdev);
        }
    }



    public class Refiner implements Runnable {

        private XYSeries activeSet;
        private XYSeries errorActiveSet;
        private XYSeries relativeErrorSeries;
        private int totalRuns;

        private double[] priors;
        private ArrayList<Integer> countsPerBin;
        private ArrayList<Double> averageStoNPerBin;

        private JLabel status;

        public Refiner(XYSeries qIq, XYSeries qIqErrorScaled, double[] priors, int totalruns, JLabel status){

            int totalItems = qIq.getItemCount() ;
            // create standardized dataset
            try {
                activeSet = qIq.createCopy(0, totalItems-1); // possibly scaled by rescaleFactor
                this.priors = priors.clone();
                //errorActiveSet = allError.createCopy(0, totalItems-1); // possibly scaled by rescaleFactor
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }

            //
            errorActiveSet = new XYSeries("Error qIq");
            relativeErrorSeries = new XYSeries("Percent Error qIq"); // percent Error

            this.totalRuns = totalruns;

            for (int i=0; i<totalItems; i++){
                XYDataItem tempData = qIqErrorScaled.getDataItem(i);
                errorActiveSet.add(tempData.getXValue(), tempData.getYValue()); // should be transformed as q*I(q)/scale
                relativeErrorSeries.add(tempData.getXValue(), Math.abs(activeSet.getY(i).doubleValue() / (tempData.getYValue())));
            }

            countsPerBin = new ArrayList<>();
            averageStoNPerBin = new ArrayList<>();
            this.averageSignalToNoisePerBin();

            this.status = status;
        }


        @Override
        public void run() {

            int startbb, upperbb, samplingNumber, locale;
            double samplingLimit, tempMedian;

            Random randomGenerator = new Random();
            int[] randomNumbers;

            int size = activeSet.getItemCount();
            double half_delta_ns = 0.5*pi_invDmax;

            XYSeries randomSeries = new XYSeries("Random-");
            XYSeries randomErrorSeries = new XYSeries("Random-");
            //System.out.println("TOTAL RUNS " + totalRuns);
            for (int i=0; i < totalRuns; i++){

                randomSeries.clear();
                randomErrorSeries.clear();

                // randomly grab from each bin
                startbb = 0;
                upperbb = 0;

                for (int b=1; b <= bins; b++){
                    // find upper q in bin
                    // if s-to-n of bin < 1.5, sample more points in bin
                    // if countPerBin < 5, randomly pick 1
                    // what if countPerBin = 0?
                    if (countsPerBin.get(b-1) > 0){

                        if (averageStoNPerBin.get(b-1) < 1.25){ // increase more points if S-to-N is less than
                            samplingNumber = 23;
                        } else {
                            samplingNumber = 23;
                        }

                        samplingLimit = (1.0 + randomGenerator.nextInt(samplingNumber))/100.0;  // return a random percent up to ...

                        binloop:
                        for (int bb=startbb; bb < size; bb++){
                            // b*pi_invDmax is cardinal point in reciprocal space
                            if (activeSet.getX(bb).doubleValue() >= (half_delta_ns + b*pi_invDmax) ){ // what happens on the last bin?
                                upperbb = bb;
                                break binloop;
                            } else {
                                upperbb = size-1;
                            }
                        }

                        // grab indices inbetween startbb and upperbb
                        randomNumbers = this.randomIntegersBounded(startbb, upperbb, samplingLimit);
                        //System.out.println("bin : " + b + " => startbb " + startbb + " upper " + upperbb + " " + size + " " + samplingLimit + " length " + randomNumbers.length);

                        startbb = upperbb;

                        for(int h=0; h < randomNumbers.length; h++){
                            locale = randomNumbers[h];
                            randomSeries.add(activeSet.getDataItem(locale));
                            randomErrorSeries.add(errorActiveSet.getDataItem(locale));
                        }
                    } // end of checking if bin is empty
                }

                // calculate PofR using standardized dataset
                IndirectFT tempIFT;

                if (useMoore){
                    //tempIFT = new MooreTransform(randomSeries, randomErrorSeries, priors, dmax, upperq, lambda, true, useBackgroundInFit, standardizedMin, standardizedScale);
                    tempIFT = new MooreTransformApache(randomSeries, randomErrorSeries, priors, dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
                } else if (useL1 && !useLegendre && !useL2 && !useSVD){
                    tempIFT = new SineIntegralTransform(randomSeries, randomErrorSeries, priors, dmax, upperq, lambda, useBackgroundInFit, positiveOnly, standardizedMin, standardizedScale);
                } else if (useLegendre && !useL2 && !useSVD) {
                    tempIFT = new LegendreTransform(randomSeries, randomErrorSeries, priors, dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
                } else if (useL2 && !useSVD) {  // use Moore Method
                    tempIFT = new DirectSineIntegralTransform(randomSeries, randomErrorSeries, dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
                } else if (useSVD) {  // use Moore Method
                    tempIFT = new SVD(randomSeries, randomErrorSeries, dmax, upperq, lambda, useBackgroundInFit, standardizedMin, standardizedScale);
                } else {
                    tempIFT = new SVD(randomSeries, randomErrorSeries, dmax, upperq, lambda, useBackgroundInFit, standardizedMin, standardizedScale);
                }

                // Calculate Residuals
                tempMedian = tempIFT.calculateMedianResidual(activeSet); // squared residual using standardized data

                if (tempMedian < getMedian()){ // minimize on medium residual in activeset
                    // keep current values
                    notifyUser(String.format("New median residual %1.5E < %1.4E at round %d", tempMedian, median, i));
                    setIFTObject(tempIFT, tempMedian);
                }
                this.increment();
            }
        }


        /**
         *
         */
        private void averageSignalToNoisePerBin(){
            // for each bin determine average signal to noise
            int startbb = 0;
            double sumError, sumCount, tempCurrent, upperBound;
            int size = relativeErrorSeries.getItemCount();

            for (int b=1; b <= bins; b++) {
                sumError = 0;
                sumCount = 0;
                upperBound = b*pi_invDmax + 0.5*pi_invDmax;

                binloop:
                for (int bb=startbb; bb < size; bb++){

                    tempCurrent = relativeErrorSeries.getX(bb).doubleValue();

                    //if ( (tempCurrent >= (delta_q*(b-1)) ) && (tempCurrent < upperBound) ){
                    if ( (tempCurrent < upperBound) ){
                        sumError += relativeErrorSeries.getY(bb).doubleValue();
                        sumCount += 1.0;
                    } else if (tempCurrent >= upperBound ) {
                        startbb = bb;
                        break binloop;
                    }
                }

                // you want a better algorithm, by a bad computer
                // greens theorem surface integral
                countsPerBin.add((int)sumCount);
                if (sumCount > 0){
                    averageStoNPerBin.add(sumError/sumCount);
                } else {
                    averageStoNPerBin.add(0.0d);
                }
                //System.out.println("Signal-to-Noise per Bin " + b + " " +  averageStoNPerBin.get(b-1) + " " + countsPerBin.get(b-1));
            }
        }


        private synchronized void increment() {
            int currentCount = counter.incrementAndGet();

            if (currentCount%100==0){
                bar.setValue((int)(currentCount*invRefineMentRounds*100));
            }
        }

        public AtomicInteger getValue() {
            return counter;
        }

        /**
         *
         * @param lower int
         * @param upper int
         * @param percent double
         * @return
         */
        private int[] randomIntegersBounded(int lower, int upper, double percent){
            List<Integer> numbers = new ArrayList<Integer>();

            int total = 0;
            for(int i = lower; i < upper; i++) {
                numbers.add(i);
                total++;
            }

            // Shuffle them
            Collections.shuffle(numbers);
            int count = (int)Math.ceil(total*percent);
            // Pick count items.
            List<Integer> tempNumbers = numbers.subList(0, count);
            int[] values = new int[count];

            for(int i=0; i < count; i++){
                values[i] = tempNumbers.get(i);
            }

            Arrays.sort(values);
            return values;
        }
    } // end of runnable class


    public void createKeptSeries(){
        // go over selected values in active set
        keptSeries=new XYSeries("reduced refinedset");
        keptErrorSeries=new XYSeries("reduced refinedset");

        double xObsValue, yObsValue;
        double residual;
        List residualsList = new ArrayList();

        XYDataItem tempData;

        double invStd = 1.0/standardizedScale;

        int size = standardizedSeries.getItemCount();
        s_o = 1.4826*(1.0 + 5.0/(size - keptIFTModel.getTotalFittedCoefficients() - 1))*Math.sqrt(getMedian());

        for (int i=0; i < size; i++){
            tempData = standardizedSeries.getDataItem(i);
            xObsValue = tempData.getXValue();
            yObsValue = tempData.getYValue(); // standardized in form of qIq
            // data has been standardized, so no adjustment necessary before residual calculation.
            residual = yObsValue - (keptIFTModel.calculateQIQ(xObsValue)-standardizedMin)*invStd;

            if (Math.abs(residual/this.getS_o()) < rejectionCutOff){
                residualsList.add(residual*residual);
            }
        }

        int sizeResiduals = residualsList.size();
        double sigmaM = 1.0/Math.sqrt(Statistics.calculateMean(residualsList)*sizeResiduals/(sizeResiduals - bins));

        XYSeries keptqIq = new XYSeries("keptqIq-"+dataset.getId());
        XYSeries keptNonStandardizedqIq = new XYSeries("keptqIq-nonstd"+dataset.getId());
        // Select final values for fitting
        for (int i=0; i < size; i++){
            tempData = standardizedSeries.getDataItem(i);
            xObsValue = tempData.getXValue();
            yObsValue = tempData.getYValue();
            residual = yObsValue - (keptIFTModel.calculateQIQ(xObsValue)-standardizedMin)*invStd;

            if ( Math.abs(residual*sigmaM) < rejectionCutOff){
                keptSeries.add(dataset.getfittedIq().getDataItem(i)); // unscaled data
                keptqIq.add(tempData);
                keptNonStandardizedqIq.add(xObsValue, dataset.getfittedIq().getY(i).doubleValue()*xObsValue);
                keptErrorSeries.add(xObsValue, scaled_q_times_Iq_Errors.getY(i).doubleValue());
            }
        }


        /*
         * if keptqIq > bin*2 , then calculate PofR
         * otherwise use activeqIq
         */
        if (keptqIq.getItemCount() > 2*bins){ // do final fitting against kepSeries

            dataset.updatedRefinedSets(keptSeries, keptErrorSeries);
            dataset.getCalcIq().clear();
            IndirectFT tempIFT;
            /*
             * these constructuros asseme already standardized datasets
             */
            if (useMoore){ // Moore method
                //tempIFT = new MooreTransform(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, false,useBackgroundInFit, standardizedMin, standardizedScale);
                tempIFT = new MooreTransformApache(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
            } else if (useL1 && !useLegendre && !useL2 && !useSVD){ // SVD with regularization
                //tempIFT = new SineIntegralTransform(keptqIq, keptErrorSeries, dmax, upperq, lambda, useBackgroundInFit, positiveOnly, standardizedMin, standardizedScale);
                tempIFT = new SineIntegralTransform(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, useBackgroundInFit, positiveOnly, standardizedMin, standardizedScale);
            } else if (useLegendre && !useL2 && !useSVD) {
                tempIFT = new LegendreTransform(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
            } else if (useL2 && !useSVD) {  // use Glatter like regularization
                tempIFT = new DirectSineIntegralTransform(keptqIq, keptErrorSeries, dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
            } else if (useSVD) {  // use Moore Method
                tempIFT = new SVD(keptqIq, keptErrorSeries, dmax, upperq, lambda, useBackgroundInFit, standardizedMin, standardizedScale);
            } else {
                tempIFT = new SVD(keptqIq, keptErrorSeries, dmax, upperq, lambda, useBackgroundInFit, standardizedMin, standardizedScale);
            }

            // for chi_estimate calculations, nonStandardized datasets must be specified
            this.dataset.setIndirectFTModel(tempIFT); // also calculate chi2 using data passed in on the instance
            //this.dataset.updateIndirectFTModel(tempIFT);
            this.dataset.setPrDistribution(tempIFT.getPrDistribution());
            this.dataset.calculateIntensityFromModel(true);
            try {
                // requires (q, Iq) not (q, q*Iq)
                // need chi specific to keptSeries
                int totalKept = keptSeries.getItemCount();

                for(int i=0; i < totalKept;i++){

                    tempData = keptSeries.getDataItem(i);
                    //keptSeries.update(tempData.getX(), (tempData.getYValue()));
                    if (tempData.getYValue() > 0) { // only positive values
                        xObsValue = tempData.getXValue();
                        dataset.getCalcIq().add(xObsValue, Math.log10( tempIFT.calculateIQ(xObsValue) ) ); //plotted data is unscaled, as is from Analysis
                    }
                }
                //this.dataset.calculateIntensityFromModel(true);
                //this.dataset.calculateQIQ();
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            int totalrejected =  size - keptSeries.getItemCount();
            double percentRejected = (double)totalrejected/(double)size*100;
            notifyUser(String.format("Rejected %d points (%.1f %%) using cutoff: %.4f => files written to working directory", totalrejected, percentRejected, rejectionCutOff));

            RefinedPlot refinedPlot = new RefinedPlot(dataset);
            refinedPlot.makePlot(String.format("Rejected %d points (%.1f %%) using cutoff: %.4f => files written to working directory", totalrejected, percentRejected, rejectionCutOff));
        }
    }


    private synchronized double getS_o() {
        return this.s_o;
    }
}
