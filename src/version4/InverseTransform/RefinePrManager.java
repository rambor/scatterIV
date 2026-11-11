package version4.InverseTransform;

import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.RealSpace;
import version4.RefinedPlot;
import version4.Ui;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Robust refinement of P(r): least median of squares over random subsets of each Shannon bin,
 * then rejection of points with |(obs - model)/sigma| above the cutoff and a final fit.
 *
 * Threading: doInBackground only computes. Everything that touches Swing or the dataset's
 * series (which charts listen to) - status text, progress bar, the refined sets, the final model
 * and the RefinedPlot - happens in done(), on the Event Dispatch Thread. Callers either wait for
 * getIsFinished() (wait/notify, as before) or, better, pass a callback with setOnFinished(),
 * which also runs on the EDT after the dataset has been updated.
 */
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

    private XYSeries averagedPr;
    private XYSeries standardDeviationsPr;


    private double standardizedMin;
    private double standardizedScale;
    private AtomicInteger counter = new AtomicInteger(0);
    private AtomicBoolean isNewModel;
    private double median;

    /** computed in the background, applied to the dataset in done() */
    private volatile Outcome outcome;
    private Runnable onFinished;

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
        // the current fit is the first candidate: if no random subset beats it, outliers are
        // still rejected against it
        this.keptIFTModel = dataset.getIndirectFTModel();
        this.median = weightedMedianResidual(keptIFTModel, standardizedSeries, scaled_q_times_Iq_Errors);
        System.out.println("start med " + this.median);

        averagedPr = new XYSeries("Averaged PR");
        standardDeviationsPr = new XYSeries("sigma PR");

        for (int i= 0; i<dataset.getPrDistribution().getItemCount(); i++){
            averagedPr.add(dataset.getPrDistribution().getDataItem(i));
            standardDeviationsPr.add(dataset.getPrDistribution().getDataItem(i));
        }
    }


    @Override
    protected Void doInBackground() throws Exception {

        notifyUser(String.format("Starting median residual :: %.5E", median));

        //bar.setMaximum(100);
        /*
         * optimize lambda - find lambda with best PrScore
         */

        final double upperLambda = 300*lambda;
        double startLamba = lambda;
        //double keptlamba;
        double dellamba = (upperLambda - startLamba)/101;
        double del_r = Math.PI/upperq;

        double initialScore = dataset.getIndirectFTModel().scoreDistribution();
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
                tempIFT = new SVD(this.standardizedSeries, scaled_q_times_Iq_Errors, dmax, upperq, startLamba, standardizedMin, standardizedScale);
            } else {
                tempIFT = new SVD(this.standardizedSeries, scaled_q_times_Iq_Errors, dmax, upperq, startLamba, standardizedMin, standardizedScale);
            }
            double prscore = tempIFT.scoreDistribution();
            if (prscore < initialScore){
                //keptlamba = startLamba;
                initialScore = prscore;
                lambda = startLamba;
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
            Ui.resetProgress(bar);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException " + e.getMessage());
            notifyUser("Failed settings limits, exceeded thread time");
        }

        //update realspaceModel
        //dataset.setIndirectFTModel(keptIFTModel);

        notifyUser("Finished Refining");

        if (!isNewModel.get()) {
            notifyUser("No random subset fitted better than the current model; rejecting against the current model");
        }
        outcome = computeKeptSeries();   // pure computation; applied in done()
        return null;
    }

    /**
     * On the EDT: apply the result to the dataset (its series drive the charts), show the
     * RefinedPlot, then release anyone waiting and run the callback.
     */
    @Override
    protected void done() {
        try {
            get();                                   // rethrows anything that failed in the background
            if (outcome != null) applyOutcome(outcome);
            else notifyUser("Refinement finished: too few points kept for a final fit, model unchanged");
        } catch (java.util.concurrent.CancellationException e) {
            notifyUser("Refinement cancelled");
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            cause.printStackTrace();
            notifyUser("Refinement failed: " + cause);
        } catch (RuntimeException e) {               // while applying the result
            e.printStackTrace();
            notifyUser("Refinement failed: " + e);
        } finally {
            Ui.resetProgress(bar);
            synchronized (this) {
                isFinished = true;
                notifyAll();
            }
            if (onFinished != null) onFinished.run();
        }
    }

    /** Runs on the EDT when refinement has finished and the dataset is updated (also after a failure). */
    public void setOnFinished(Runnable callback) {
        this.onFinished = callback;
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
        Ui.setValue(this.bar, 0);
        Ui.setStringPainted(this.bar, true);
        Ui.setMaximum(this.bar, 100);
        this.statusLabel = prStatusLabel;
        useLabels = true;
    }

    public boolean getIsFinished() {
        return isFinished;
    }

    void updateText(final String text){
        Ui.setText(statusLabel, text);
    };

    /** Status text from any thread (queued onto the EDT). */
    private void notifyUser(final String text){
        if (useLabels){
            Ui.setText(statusLabel, text);
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

            // sized generously: the coefficient count depends on dmax*qmax (+ background)
            int maxCoefficients = Math.max(bins + 2, priors.length) + 8;
            double[] averages = new double[maxCoefficients];
            double[] averagesSQ = new double[maxCoefficients];

            double invC = 1;
            int count = 1;
            int randomSeriesSize = 0;

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

                randomSeriesSize += randomSeries.getItemCount();
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
                    tempIFT = new SVD(randomSeries, randomErrorSeries, dmax, upperq, lambda, standardizedMin, standardizedScale);
                } else {
                    tempIFT = new SVD(randomSeries, randomErrorSeries, dmax, upperq, lambda, standardizedMin, standardizedScale);
                }

                // Calculate Residuals
                // median of squared normalised residuals ((obs - model)/sigma)^2 over all points
                tempMedian = weightedMedianResidual(tempIFT, activeSet, errorActiveSet);

                for(int h=0; h < Math.min(tempIFT.totalCoefficients, maxCoefficients); h++){
                    averages[h] += tempIFT.coefficients[h];
                    averagesSQ[h] += tempIFT.coefficients[h]*tempIFT.coefficients[h];
                }
                count = Math.min(tempIFT.totalCoefficients, maxCoefficients);

                if (tempMedian < getMedian()){ // minimize on medium residual in activeset
                    // keep current values
                    notifyUser(String.format("New median residual %1.5E < %1.4E at round %d", tempMedian, median, i));
                    setIFTObject(tempIFT, tempMedian);
                }
                this.increment();
            }

            // calculate stats
            invC = 1.0/(double)totalRuns;
            for(int h=0; h < count; h++){
                averages[h] *= invC;
                averagesSQ[h] *= invC;
            }

            System.out.println(" Variations " + numberOfCPUs + " " + totalRuns + " " + randomSeriesSize*invC );
            for(int h=0; h < Math.min(count, averagedPr.getItemCount()); h++){
                double stdev = Math.sqrt(Math.max(0, averagesSQ[h] - averages[h] * averages[h]));
                System.out.println(String.format("%d %.2f %.4f %.4f %.3f", h, averagedPr.getX(h).doubleValue(), averages[h], stdev, stdev/averages[h]));
//                System.out.println(h + " " +  count + " " + averagedPr.getX(h).doubleValue() + " " + averages[h] + " " +  Math.sqrt(averagesSQ[h] - averages[h] * averages[h]));
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
                Ui.setValue(bar, (int)(currentCount*invRefineMentRounds*100));
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


    /** What the refinement produced; built in the background, applied on the EDT. */
    private static final class Outcome {
        XYSeries keptSeries, keptErrorSeries;
        IndirectFT model;
        double[] calcQ, calcLogI;          // log10 I(q) of the final model at the kept q with I > 0
        int totalRejected, total;
    }

    /**
     * Rejects points against the best model and fits the kept points. No Swing and no changes to
     * the dataset here (runs in the background). Returns null if too few points are kept.
     */
    private Outcome computeKeptSeries(){
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

        /*
         * Residuals are normalised by each point's error, r_i = (obs - model)/sigma_i, so the
         * cutoff means the same number of sigma at every q. Unweighted residuals of q*I(q) are
         * largest wherever q*sigma(q) is largest, so they rejected ordinary points there and
         * missed outliers where the errors are small.
         * Points without a usable error are kept and do not enter the scale estimates.
         */
        double[] normalised = new double[size];
        for (int i=0; i < size; i++){
            tempData = standardizedSeries.getDataItem(i);
            xObsValue = tempData.getXValue();
            yObsValue = tempData.getYValue(); // standardized in form of qIq
            // data has been standardized, so no adjustment necessary before residual calculation.
            residual = yObsValue - (keptIFTModel.calculateQIQ(xObsValue)-standardizedMin)*invStd;
            double sigma = scaled_q_times_Iq_Errors.getY(i).doubleValue();
            normalised[i] = (sigma > 0 && Double.isFinite(sigma)) ? residual/sigma : Double.NaN;

            // first pass: robust (LMS) scale s_o
            if (!Double.isNaN(normalised[i]) && Math.abs(normalised[i]/this.getS_o()) < rejectionCutOff){
                residualsList.add(normalised[i]*normalised[i]);
            }
        }

        // second pass: scale from the points within the first cutoff (reweighted least squares)
        int sizeResiduals = residualsList.size();
        double sigmaM = (sizeResiduals > bins)
                ? 1.0/Math.sqrt(Statistics.calculateMean(residualsList)*sizeResiduals/(double)(sizeResiduals - bins))
                : 1.0/this.getS_o();

        XYSeries keptqIq = new XYSeries("keptqIq-"+dataset.getId());
        // Select final values for fitting
        for (int i=0; i < size; i++){
            tempData = standardizedSeries.getDataItem(i);
            xObsValue = tempData.getXValue();

            if (Double.isNaN(normalised[i]) || Math.abs(normalised[i]*sigmaM) < rejectionCutOff){
                keptSeries.add(dataset.getfittedIq().getDataItem(i)); // unscaled data
                keptqIq.add(tempData);
                keptErrorSeries.add(xObsValue, scaled_q_times_Iq_Errors.getY(i).doubleValue());
            }
        }

        /*
         * if keptqIq > bin*2 , then calculate PofR
         * otherwise leave the model as it is
         */
        if (keptqIq.getItemCount() <= 2*bins) return null;

        IndirectFT tempIFT;
        /*
         * these constructors assume already standardized datasets
         */
        if (useMoore){ // Moore method
            tempIFT = new MooreTransformApache(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
        } else if (useL1 && !useLegendre && !useL2 && !useSVD){ // SVD with regularization
            tempIFT = new SineIntegralTransform(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, useBackgroundInFit, positiveOnly, standardizedMin, standardizedScale);
        } else if (useLegendre && !useL2 && !useSVD) {
            tempIFT = new LegendreTransform(keptqIq, keptErrorSeries, keptIFTModel.getCoefficients(), dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
        } else if (useL2 && !useSVD) {  // use Glatter like regularization
            tempIFT = new DirectSineIntegralTransform(keptqIq, keptErrorSeries, dmax, upperq, lambda, standardizedMin, standardizedScale, useBackgroundInFit);
        } else {  // SVD
            tempIFT = new SVD(keptqIq, keptErrorSeries, dmax, upperq, lambda, standardizedMin, standardizedScale);
        }

        Outcome o = new Outcome();
        o.keptSeries = keptSeries;
        o.keptErrorSeries = keptErrorSeries;
        o.model = tempIFT;
        o.total = size;
        o.totalRejected = size - keptSeries.getItemCount();

        // model intensity at the kept points (requires (q, I(q)), not q*I(q)); only positive values
        int totalKept = keptSeries.getItemCount();
        double[] cq = new double[totalKept], ci = new double[totalKept];
        int n = 0;
        for (int i=0; i < totalKept; i++){
            tempData = keptSeries.getDataItem(i);
            if (tempData.getYValue() > 0) {
                xObsValue = tempData.getXValue();
                double iq = tempIFT.calculateIQ(xObsValue);
                if (iq > 0) {
                    cq[n] = xObsValue;
                    ci[n] = Math.log10(iq); //plotted data is unscaled, as is from Analysis
                    n++;
                }
            }
        }
        o.calcQ = Arrays.copyOf(cq, n);
        o.calcLogI = Arrays.copyOf(ci, n);
        return o;
    }

    /** On the EDT: put the refined sets and the final model into the dataset and show the plot. */
    private void applyOutcome(Outcome o){
        dataset.updatedRefinedSets(o.keptSeries, o.keptErrorSeries);
        // for chi_estimate calculations, nonStandardized datasets must be specified
        dataset.setIndirectFTModel(o.model); // also calculates chi2 using data passed in on the instance
        dataset.setPrDistribution(o.model.getPrDistributionNormalizedToVolume());
        dataset.calculateIntensityFromModel(true);

        XYSeries calc = dataset.getCalcIq();
        calc.setNotify(false);                  // one chart update instead of one per point
        calc.clear();
        for (int i=0; i < o.calcQ.length; i++) calc.add(o.calcQ[i], o.calcLogI[i]);
        calc.setNotify(true);

        double percentRejected = (double)o.totalRejected/(double)o.total*100;
        String msg = String.format("Rejected %d points (%.1f %%) using cutoff: %.4f => files written to working directory",
                o.totalRejected, percentRejected, rejectionCutOff);
        notifyUser(msg);

        RefinedPlot refinedPlot = new RefinedPlot(dataset);
        refinedPlot.makePlot(msg);
    }


    /**
     * Median of the squared normalised residuals ((obs - model)/sigma)^2 of a model against
     * standardized q*I(q) data and its errors (same order, same points). Points without a usable
     * error are skipped. Thread safe: only reads the model.
     */
    private double weightedMedianResidual(IndirectFT model, XYSeries data, XYSeries errors){
        double invStd = 1.0/standardizedScale;
        int n = data.getItemCount();
        double[] values = new double[n];
        int count = 0;
        for (int i=0; i < n; i++){
            double x = data.getX(i).doubleValue();
            double sigma = errors.getY(i).doubleValue();
            if (!(sigma > 0) || !Double.isFinite(sigma)) continue;
            double r = (data.getY(i).doubleValue() - (model.calculateQIQ(x) - standardizedMin)*invStd)/sigma;
            if (Double.isFinite(r)) values[count++] = r*r;
        }
        if (count == 0) return Double.POSITIVE_INFINITY;
        Arrays.sort(values, 0, count);
        return (count % 2 == 1) ? values[count/2] : 0.5*(values[count/2 - 1] + values[count/2]);
    }

    private synchronized double getS_o() {
        return this.s_o;
    }
}
