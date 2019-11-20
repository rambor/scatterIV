package version4.SEC;

import org.apache.commons.codec.digest.DigestUtils;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.AutoRg;
import version4.Functions;

import javax.swing.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;

public class SECRebuilder  extends SwingWorker<Void, Integer> {

    private SECFile secFile;
    private SortedSet<Integer> newBuffers;
    private double threshold;
    private JProgressBar bar;
    private JLabel status;
    private int ignoreThis;

    public SECRebuilder(SECFile secFile, SortedSet<Integer>  newBuffers, double thres, int ignorethis, JProgressBar bar, JLabel status ){
        this.secFile = secFile;
        this.newBuffers = newBuffers;
        this.threshold = thres;
        this.ignoreThis = ignorethis;
        this.bar = bar;
        this.status = status;
    }

    @Override
    protected Void doInBackground() throws Exception {
        // assemble the indices
        status.setText("Recalculating SEC File using new buffers, please wait");
        bar.setVisible(true);
        bar.setIndeterminate(true);
        bar.setStringPainted(false);

        int totalq = secFile.getTotalQValues();
        ArrayList<Double> averagedBuffer = new ArrayList<>(totalq);
        ArrayList<Double> averagedError = new ArrayList<>(totalq);

        for(int i=0; i<totalq; i++){
            averagedBuffer.add(0.0d);
            averagedError.add(0.0d);
        }

        status.setText("Averaging buffers :: " + newBuffers.size());
        double var, sigma;
        for(Integer bufIndex : newBuffers){
            ArrayList<Double> values = secFile.getUnSubtractedFrameAt(bufIndex);
            ArrayList<Double> sigmas = secFile.getUnSubtractedErrorFrameAt(bufIndex);

            for(int i=0; i<totalq; i++){
                sigma = 1.0/sigmas.get(i);
                var = sigma*sigma;
                averagedBuffer.set(i, averagedBuffer.get(i) + values.get(i)*var);
                averagedError.set(i, averagedError.get(i) + var);
            }
        }
        status.setText("Finalizing new buffer average");
        // finalize the buffers
        for(int i=0; i<totalq; i++){
            double base = averagedBuffer.get(i);
            var = averagedError.get(i);
            averagedBuffer.set(i, base/var);
            averagedError.set(i, 1.0/Math.sqrt(var));
        }

        // assembled averaged buffer and associated error
        StringBuilder tempOutAvgBuff = new StringBuilder(totalq*11);
        StringBuilder tempOutAvgBuffError = new StringBuilder(totalq*11);
        for(int i=0; i<totalq; i++){
            tempOutAvgBuff.append(SECBuilder.convertIntensityToString(averagedBuffer.get(i)));
            tempOutAvgBuffError.append(SECBuilder.convertIntensityToString(averagedError.get(i)));
        }

        /*
         * calculate
         * 1. signal
         * 2. resubtract
         * 3. autoRg
         *
         */
        secFile.updateBufferIndices(newBuffers); // always a fixed size
//        secFile.updateAveragedBuffer(tempOutAvgBuff.toString(), tempOutAvgBuffError.toString());


        ArrayList<Double> qvalues = secFile.getQvalues();
        XYSeries buffer = new XYSeries("buffer");
        XYSeries bufferError = new XYSeries("bufferError");

        for(int q=0; q<totalq; q++){
            buffer.add(qvalues.get(q), averagedBuffer.get(q));
            bufferError.add(qvalues.get(q), averagedError.get(q));
        }

        XYSeries ratio = new XYSeries("ratio");
        XYSeries qIqData = new XYSeries("qiQ");
        XYSeries tempData, tempDataError;
        XYDataItem tempXY;
        ArrayList<Double> target, targetError;
        ArrayList<Signals> signals = new ArrayList<>();

        double noSignal = (qvalues.get(totalq-1) - qvalues.get(0))*(totalq+1)/(double)totalq;

        bar.setIndeterminate(false);
        bar.setStringPainted(true);
        bar.setMaximum(secFile.getTotalFrames());
        bar.setValue(0);
        status.setText("Recalculating subtracted curves, Rg, IZero and signals");

        ArrayList<String> sub = new ArrayList<>();
        ArrayList<String> subErr = new ArrayList<>();

        for(int i=0; i < secFile.getTotalFrames(); i++){ // iterate over all frames
            ratio.clear();
            int frameIndex = secFile.getFrameByIndex(i);
            tempData = secFile.getUnsubtractedXYSeries(frameIndex);
            target = secFile.getUnSubtractedFrameAt(frameIndex);
            targetError = secFile.getUnSubtractedErrorFrameAt(frameIndex);
            // create XY Series using ratio to buffer then integrate
            for(int q=0; q<totalq; q++){
                tempXY = tempData.getDataItem(q);
                ratio.add(tempXY.getXValue(), tempXY.getYValue()/averagedBuffer.get(q));
            }

            boolean isSignal = false;
            if (!newBuffers.contains(frameIndex)){
                isSignal = true;
            }
            // if number of points is less than 100, skip
            // integrate
            double area = Functions.trapezoid_integrate(ratio);

            // index, signal, Rg, I(0), integral qIq
            ArrayList<XYSeries> subtraction = subtract(target, targetError, averagedBuffer, averagedError);

            XYSeries subtracted = subtraction.get(0);
            XYSeries suberrors = subtraction.get(1);

            if (isSignal && area/noSignal > threshold){
                AutoRg temp = new AutoRg(subtracted, ignoreThis);
                signals.add(new Signals(frameIndex, area/noSignal, temp.getI_zero(), temp.getRg()));
            } else {
                signals.add(new Signals(frameIndex, area/noSignal, 0, 0));
            }

            if (isSignal){
                signals.get(i).setIsBuffer(false);
            }
            // add qIq integration
            qIqData.clear();
            // create XY Series using ratio to buffer then integrate
            for(int q=0; q < totalq; q++){
                tempXY = subtracted.getDataItem(q);
                qIqData.add(tempXY.getX(), tempXY.getYValue()*tempXY.getXValue());
            }

            area = Functions.trapezoid_integrate(qIqData);
            signals.get(i).setTotal_qIq(area);
            /*
             * write subtracted frames
             */
            StringBuilder tempOutSub = new StringBuilder(totalq*11);
            StringBuilder tempOutSubError = new StringBuilder(totalq*11);

            for(int xy=0; xy<subtracted.getItemCount(); xy++){
                tempOutSub.append(SECBuilder.convertIntensityToString(subtracted.getY(xy).doubleValue()));
                tempOutSubError.append(SECBuilder.convertIntensityToString(suberrors.getY(xy).doubleValue()));
            }

//            sub.add(tempOutSub.toString());
//            subErr.add(tempOutSubError.toString());
            secFile.updateSubtractedFrame(frameIndex, tempOutSub.toString(), tempOutSubError.toString());

            publish(i);
        }

        status.setText("Updating SECFILE ...");
        // update signals, etc in SECFILE
        int totalDataSetsInCollection = secFile.getTotalFrames();
        StringBuilder signalLine = new StringBuilder(totalDataSetsInCollection*11);
        StringBuilder izeroLine = new StringBuilder(totalDataSetsInCollection*11);
        StringBuilder rgLine = new StringBuilder(totalDataSetsInCollection*9);
        StringBuilder total_qIqLine = new StringBuilder(totalDataSetsInCollection*11);

        for (Signals signal : signals) {
            signalLine.append(SECBuilder.convertSignalToString(signal.getSignal()));
            izeroLine.append(SECBuilder.convertDoubleTo3EString(signal.getIzero()));
            rgLine.append(SECBuilder.convertDoubleTo3EString(signal.getRg()));
            total_qIqLine.append(SECBuilder.convertqIqToString(signal.getTotal_qIq()));
        }

        secFile.updateSignalLine(signalLine.toString());
        secFile.updateIZeroLine(izeroLine.toString());
        secFile.updateRgLine(rgLine.toString());
        secFile.updateqIqLine(total_qIqLine.toString());



        status.setText("Finished");
        bar.setIndeterminate(false);
        bar.setVisible(false);
        return null;
    }


    private ArrayList<XYSeries> subtract(ArrayList<Double> sample, ArrayList<Double> sampleError, ArrayList<Double> buffer, ArrayList<Double> bufferError){
        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        // for each element in sample collection, do subtraction
        XYSeries subData;
        XYSeries subError;
        double tempDataItem;

        double qValue, yValue, eValue;

        subData = new XYSeries("subtracted");
        subError = new XYSeries("errorSubtracted");
        //Subtract and add to new data

        ArrayList<Double> qvalues = secFile.getQvalues();

        int totalq =sample.size();

        for(int q=0; q<totalq; q++){
            tempDataItem = sample.get(q);
            /*
             * check to see if in buffer
             */
            qValue = qvalues.get(q);
            yValue = sampleError.get(q);

            subData.add(qValue, tempDataItem - buffer.get(q) );
            eValue = bufferError.get(q);
            subError.add(qValue, Math.sqrt(yValue*yValue + eValue*eValue));
        }

        returnMe.add(subData);
        returnMe.add(subError);

        return returnMe;
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        bar.setValue(i);
        super.process(chunks);
    }
}
