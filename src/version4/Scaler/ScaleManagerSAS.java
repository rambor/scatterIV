package version4.Scaler;

import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYSeries;
import version4.SEC.SECFile;

import javax.swing.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScaleManagerSAS extends SwingWorker<Void, Integer> {

    private JProgressBar progressBar;
    private int cpus, startIndex, endIndex, refIndex;
    private JLabel label;
    private double lower=0, upper=0;
    private SECFile secFile;
    private int samplingRounds=1000;
    private ArrayList<Double> scaleFactors;
    private boolean mergeIt = true;
    private XYSeries merged, mergedErrors;
    private int qLowIndex, qMaxIndex;
    /**
     * create a scaled set of frames to frame with largest signal
     *
     * @param startIndex
     * @param endIndex
     * @param secFile
     * @param bar
     * @param label
     */
    public ScaleManagerSAS(int startIndex, int endIndex, SECFile secFile, JProgressBar bar, JLabel label, boolean mergeIt){
        this.startIndex = startIndex;
        this.endIndex = endIndex+1;
        this.secFile = secFile;
        this.label = label;
        this.progressBar = bar;
        this.mergeIt = mergeIt;
    }

    /**
     * set the q-window range used for scaling the data
     * should exclude particularly noisy regions.
     *
     * @param lowerq
     * @param upperq
     */
    public void setUpperLowerQLimits(double lowerq, double upperq){
        this.lower = lowerq;
        this.upper = upperq;
    }

    @Override
    protected Void doInBackground() {

        XYSeries signals = secFile.getSignalSeries();
        label.setText("Scaling frames");
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setMaximum(endIndex - startIndex + 1);


        refIndex = startIndex;
        double maxIt=0;
        /*
         find signal with strongest signal within selected indices
         */
        for(int i=startIndex; i<endIndex; i++){
            double temp = signals.getY(i).doubleValue();
            if (temp > maxIt){
                refIndex = i;
                maxIt = temp;
            }
        }


        // map qlimits to indices
        ArrayList<Double> qvalues = secFile.getQvalues();
        qLowIndex = 0;
        qMaxIndex = 0;

        if (lower == 0)
            lower = qvalues.get(0);

        if (upper == 0)
            upper = qvalues.get(qvalues.size()-1);

        for(int i=0; i<qvalues.size(); i++){
            if (qvalues.get(i) >= lower){
                qLowIndex = i;
                break;
            }
        }

        for(int i=qLowIndex; i<qvalues.size(); i++){
            if (qvalues.get(i) >= upper){
                qMaxIndex = i;
                break;
            }
        }

        int totalQ = qMaxIndex - qLowIndex + 1;

          // scale to peak
        ArrayList<Double> ref = secFile.getSubtractedFrameAt(refIndex);
        int index = startIndex;
        scaleFactors = new ArrayList<>();
        label.setText("Calculating scale factor using reference frame " + refIndex);

        long startTime = System.currentTimeMillis();
        int count=0;
        for(; index<refIndex; index++){
            // perform median based scaling
            ArrayList<Double> target = secFile.getSubtractedFrameAt(index);
            scaleFactors.add(scaleToRef(totalQ, ref, target));
            publish(count);
            count++;
        }
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("Total Time :: " + elapsedTime + " refINdex " + refIndex);

        scaleFactors.add(1.0d);
        count++;
        index++;

        for(; index < endIndex; index++){
            ArrayList<Double> target = secFile.getSubtractedFrameAt(index);
            scaleFactors.add(scaleToRef(totalQ, ref, target));
            publish(count);
            count++;
        }

        if (mergeIt){
            this.merge();
        }

        return null;
    }

    private void merge(){

        merged = new XYSeries("merged");
        mergedErrors = new XYSeries("errors");
        ArrayList<Double> qvalues = secFile.getQvalues();
        ArrayList<Double> target = secFile.getSubtractedFrameAt(startIndex);
        ArrayList<Double> tarErrors = secFile.getSubtractedErrorAtFrame(startIndex);

        ArrayList<Double> weightedISum = new ArrayList<>(qvalues.size());
        ArrayList<Double> weightedESum = new ArrayList<>(qvalues.size());

        double std, var, scale = scaleFactors.get(0);

        for(int i=0; i<qvalues.size(); i++){
            std = 1.0d/(tarErrors.get(i)*scale);
            var = std*std;
            weightedISum.add((target.get(i)*scale*var));
            weightedESum.add(var);
        }

        int next = startIndex+1;
        int count = 1;
        for(; next < endIndex; next++){
            target = secFile.getSubtractedFrameAt(next);
            tarErrors = secFile.getSubtractedErrorAtFrame(next);
            scale = scaleFactors.get(count);

            for(int i=0; i<qvalues.size(); i++){
                std = 1.0d/(tarErrors.get(i)*scale);
                var = std*std;

                weightedISum.set(i, weightedISum.get(i) + (target.get(i)*scale*var));
                weightedESum.set(i, weightedESum.get(i) + var);
            }
            count+=1;
        }

        /*
         * scale the weight sum to get the average
         */
        double qval;
        for(int i=0; i<qvalues.size(); i++){
            qval = qvalues.get(i);
            var = weightedESum.get(i);
            merged.add(qval, weightedISum.get(i)/var);
            mergedErrors.add(qval, 1.0/Math.sqrt(var));
        }
    }

    private int[] randomArray(int limit, int max){
        List<Integer> sequence = new ArrayList<Integer>();
        int[] values;

        //create ArrayList of integers upto max value
        for (int i = 0; i < max; i++){
            sequence.add(i);
        }

        values = new int[limit];

        for (int i = 0; i < limit; i++){
            int position = (int)(Math.random()*max);
            values[i] = sequence.get(position);
            sequence.remove(position);
            max = sequence.size();
        }
        Arrays.sort(values);
        return values;
    }




    private double scaleToRef(int totalQ, ArrayList<Double> ref, ArrayList<Double> target){
        int samplingLimit, minToUse = (int)Math.round(totalQ*0.045);   //minimum points 6.5% of total
        int[] randomNumbers;

        // scale to peak
        int getIndex;

        SimpleMatrix tempRef = new SimpleMatrix(totalQ,1);
        SimpleMatrix tempTarget =new SimpleMatrix(totalQ,1);
//        SimpleMatrix calcRef = new SimpleMatrix(totalQ,1);
//        SimpleMatrix calcTarget =new SimpleMatrix(totalQ,1);
        SimpleMatrix residualsVector;
        ArrayList<Double> residualsFromVector = new ArrayList<Double>();

        ArrayList<Integer> indices = new ArrayList<>();

        for (int j = 0; j < totalQ; j++) {
            // make sure selected Index is not the first 3 or last 3 in ref
            getIndex = qLowIndex + j;
            tempRef.set(j, 0, ref.get(getIndex));
            tempTarget.set(j, 0, target.get(getIndex));
            indices.add(getIndex);
        }


        double tempScale, medianValue=0, scaleFactor = 1.0;

        for (int round = 0; round < samplingRounds; round++) {
            samplingLimit = 7;//minToUse + (int)(Math.random()*((totalQ*0.5 - minToUse) + 1));
            Collections.shuffle(indices);

//            calcRef.reshape(samplingLimit, 1);
//            calcTarget.reshape(samplingLimit, 1);

            double scaleSum = 0.0;
            for (int j = 0; j < samplingLimit; j++) {
                // make sure selected Index is not the first 3 or last 3 in ref
//                getIndex = qLowIndex + randomNumbers[j];
                getIndex = indices.get(j);
//                calcRef.set(j, 0, ref.get(getIndex));
//                calcTarget.set(j, 0, target.get(getIndex));

                scaleSum += target.get(getIndex)/ref.get(getIndex); // could be weighted average
            }

//            try {
//                tempScale = (float) calcRef.solve(calcTarget).get(0,0);
//            } catch ( SingularMatrixException e ) {
//                tempScale = 1.0;
//            }
            tempScale = scaleSum/(double)samplingLimit;

            //calculate residuals over input range
//            residualsVector = tempTarget.scale(tempScale).minus(tempRef);
            residualsVector = tempRef.scale(tempScale).minus(tempTarget);

            residualsFromVector.clear();

            //Squared residual
            for (int r=0; r < residualsVector.numRows(); r++){
                residualsFromVector.add(Math.pow(residualsVector.get(r, 0),2));
            }

            double tempResidualval = Statistics.calculateMedian(residualsFromVector, true);
            if (round == 0) {
                medianValue = tempResidualval;
                scaleFactor = (float)tempScale;
            } else if (tempResidualval < medianValue ){
                medianValue = tempResidualval;
                scaleFactor = (float)tempScale;
            }
        }

        return 1.0/scaleFactor;
    }

    public XYSeries getMerged() {
        return merged;
    }

    public XYSeries getMergedErrors() {
        return mergedErrors;
    }

    public void setSamplingRounds(int samplingRounds) {
        this.samplingRounds = samplingRounds;
    }


    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        progressBar.setValue(i);
        super.process(chunks);
    }

    public double getLower() {
        return lower;
    }

    public double getUpper() {
        return upper;
    }

    public ArrayList<Double> getScaleFactors(){
        return scaleFactors;
    }
}


