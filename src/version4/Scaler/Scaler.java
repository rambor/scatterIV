package version4.Scaler;

import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by xos81802 on 26/07/2017.
 */
public class Scaler {

    private XYSeries referenceSet;
    private XYSeries targetSet;
    private XYSeries targetErrorSet;
    private double lower, upper;
    private double scaleFactor;

    private int startTarIndex, endTarIndex, startRefIndex, endRefIndex;
    //private ScaleManager manager;

    public Scaler(
            XYSeries reference,
            XYSeries target,
            XYSeries targetErrorSet,
            int startRefIndex, // refers to what is plotted
            int endRefIndex,   // refers to what is plotted
            int startTarIndex, // refers to what is plotted
            int endTarIndex,   // refers to what is plotted
            double lower,
            double upper) {

        this.referenceSet = reference;
        this.targetSet = target;
        this.targetErrorSet = targetErrorSet;

        this.startRefIndex = startRefIndex;
        this.endRefIndex = endRefIndex;
        this.startTarIndex = startTarIndex;
        this.endTarIndex = endTarIndex;

        this.lower = lower;
        this.upper = upper;

        //this.manager = manager;
    }

    /**
     * using reference dataset as a reference, go through each q-value in target and make sure it matches
     * interpolate values when necessary
     *
     * after interpolating, determine scale factor that minimizes squared residual
     *
     */
    public double scale(){

        XYSeries scaleRef = new XYSeries("ref");
        XYSeries scaleTemp = new XYSeries("temp");
        XYDataItem refItem;

        int ndLast = targetSet.getItemCount() - 4;

        // need to determine lower q-value and upper q-value range that is common to both datasets
        double startRefq = referenceSet.getX(startRefIndex).doubleValue();
        double endRefq = referenceSet.getX(endRefIndex).doubleValue();

        // if range is specified, need to truncate data
        double startTargetq = targetSet.getX(startTarIndex).doubleValue();
        double endTargetq = targetSet.getX(endTarIndex).doubleValue();

        double lowerq = Math.max(startRefq, startTargetq);
        double upperq = Math.min(endRefq, endTargetq);

        if (lower > lowerq){
            lowerq = lower;
        }

        if (upper < upperq){
            upperq = upper;
        }


        double refXValue;
        int ssIndexOf;

        double targetStart = targetSet.getX(1).doubleValue();
        double targetEnd = targetSet.getX(ndLast).doubleValue();

        //Build common set of q values against ref DataSet
        for (int i = startRefIndex; i <= endRefIndex; i++){

            // Is the ref q value given by i in Target and within selected range
            refItem = referenceSet.getDataItem(i);
            refXValue = refItem.getXValue();

            if ((refXValue >= lowerq) && (refXValue <= upperq)  ) { // i must be in overlapping region
                //Is the ref q-vale in target?
                //If it is not, then we need to determine if we have to interpolate
                ssIndexOf = targetSet.indexOf(refXValue);
                if (ssIndexOf > -1) { //

                    scaleRef.add(refItem);
                    scaleTemp.add(targetSet.getDataItem(ssIndexOf));

                } else {
                    // if reference q-value is not in target, interpolate its value
                    // make sure the requested value is not in the first two or last two
                    if ((refXValue > targetStart) && (refXValue < targetEnd)){
                        /*
                         * interpolateOriginal does not requires log10 data
                         */
                        Double[] results =  interpolateOriginal(targetSet, refXValue);
                        Double[] sigmaResults = interpolateSigma(targetErrorSet, refXValue);

                        if (!results[1].isNaN()){
                            scaleRef.add(refItem);
                            scaleTemp.add(refXValue, results[1]);
                        }
                    }
                }
            }
        }

        scaleData(scaleTemp, scaleRef); //scale uses
        return scaleFactor;
    }

    public double getScaleFactor(){
        return scaleFactor;
    }

    /**
     *
     * @param data
     * @param point
     * @return
     */
    private Double[] interpolateOriginal(XYSeries data, double point){
        //Kriging interpolation, use input log10 data
        SimpleMatrix one = new SimpleMatrix(6, 1);
        int [] z = new int[6];
        int index=0;
        //loop over data to find the smalllest q rather than than point

        for (int i=0; i< data.getItemCount()-1; i++) {
            if (data.getX(i).doubleValue() > point){
                index = i;
                break;
            }
        }

        if (index <=1){
            for (int k=0; k<6; k++){
                z[k]=index+k;
            }
        } else if (index >= data.getItemCount()-3){
            z[0]=data.getItemCount()-6;
            z[1]=data.getItemCount()-5;
            z[2]=data.getItemCount()-4;
            z[3]=data.getItemCount()-3;
            z[4]=data.getItemCount()-2;
            z[5]=data.getItemCount()-1;
        } else { //Decrement index by -2 to have at least two points before the interpolated q value
            for (int k=-2; k<4; k++){
                z[k+2]=index+k;
            }
        }

        double scale = data.getX(z[5]).doubleValue() - data.getX(z[0]).doubleValue();

        SimpleMatrix c_m = new SimpleMatrix(6,6);
        //this might be (1,6)
        SimpleMatrix z_m = new SimpleMatrix(6,1);
        for (int m=0; m<6; m++){
            one.set(m, 0, 1);
            double anchor = data.getX(z[m]).doubleValue();
            //Use anti-log data
            z_m.set(m, 0, data.getY(z[m]).doubleValue());
            for (int n=0; n<6; n++){
                c_m.set(m, n, 0.96*Math.exp(-1*(Math.pow(( (anchor - data.getX(z[n]).doubleValue())/scale),2))));
            }
        }
        SimpleMatrix d_m = new SimpleMatrix(6,1);
        for (int m=0; m < 6; m++){
            d_m.set(m, 0, 0.96*Math.exp( -1*(Math.pow(((point-data.getX(z[m]).doubleValue())/scale),2))));
        }

        double mu = ((one.transpose().mult(c_m.invert())).mult(z_m).get(0))/(one.transpose().mult(c_m.invert().mult(one))).get(0);

        Double[] resultS = new Double[3];
        resultS[0]=point;
        resultS[1]=mu+(d_m.transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(mu)))).get(0);

        double sigma_2 = ((z_m.minus(one.scale(resultS[1]))).transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(resultS[1])))).get(0)/6;
        double sigma_d = ((d_m.transpose().mult(c_m.invert())).mult(d_m)).get(0);
        double tmp1 = sigma_2*(1-sigma_d);
        resultS[2]=Math.sqrt(tmp1);

        return resultS;
    }


    /**
     *
     * @param data
     * @param point
     * @return
     */
    public Double[] interpolateSigma(XYSeries data, double point){
        //Kriging interpolation,
        SimpleMatrix one = new SimpleMatrix(6, 1);
        int [] z = new int[6];
        int index=0;
        //loop over data to find the smallles q grather than than point

        for (int i=0; i< data.getItemCount()-1; i++) {
            if (data.getX(i).doubleValue()>point){
                index = i;
                break;
            }
        }

        if (index <=1){
            for (int k=0; k<6; k++){
                z[k]=index+k;
            }
        } else if (index>=data.getItemCount()-3){
            z[0]=data.getItemCount()-6;
            z[1]=data.getItemCount()-5;
            z[2]=data.getItemCount()-4;
            z[3]=data.getItemCount()-3;
            z[4]=data.getItemCount()-2;
            z[5]=data.getItemCount()-1;
        } else {
            for (int k=-2; k<4; k++){
                z[k+2]=index+k;
            }
        }

        double scale = data.getX(z[5]).doubleValue()-data.getX(z[0]).doubleValue();

        SimpleMatrix c_m = new SimpleMatrix(6,6);
        //this might be (1,6)
        SimpleMatrix z_m = new SimpleMatrix(6,1);
        for (int m=0; m<6; m++){
            one.set(m, 0, 1);
            double anchor = data.getX(z[m]).doubleValue();
            //delog data
            z_m.set(m, 0, data.getY(z[m]).doubleValue());
            for (int n=0; n<6; n++){
                c_m.set(m, n, 0.96*Math.exp(-1*(Math.pow(((anchor-data.getX(z[n]).doubleValue())/scale),2))));

            }
        }

        SimpleMatrix d_m = new SimpleMatrix(6,1);
        for (int m=0; m < 6; m++){
            d_m.set(m, 0, 0.96*Math.exp( -1*(Math.pow(((point-data.getX(z[m]).doubleValue())/scale),2))));
        }

        double mu = ((one.transpose().mult(c_m.invert())).mult(z_m).get(0))/(one.transpose().mult(c_m.invert().mult(one))).get(0);

        Double[] resultD = new Double[3];
        resultD[0]=point;
        resultD[1]=mu+(d_m.transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(mu)))).get(0);

        double sigma_2 = ((z_m.minus(one.scale(resultD[1]))).transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(resultD[1])))).get(0)/6;
        double sigma_d = ((d_m.transpose().mult(c_m.invert())).mult(d_m)).get(0);
        double tmp1 = sigma_2*(1-sigma_d);
        resultD[2]=Math.sqrt(tmp1);

        return resultD;
    }


    /**
     *
     * @param target
     * @param ref
     * @return
     */
    private void scaleData(XYSeries target, XYSeries ref){

        SimpleMatrix tempRef = new SimpleMatrix(1,1);
        SimpleMatrix tempTarget =new SimpleMatrix(1,1);
        SimpleMatrix residualsVector;
        ArrayList<Double> residualsFromVector = new ArrayList<Double>();

        int samplingLimit;
        int min = (int)Math.round(ref.getItemCount()*0.045);   //minimum points 6.5% of total
        int size = ref.getItemCount();
        int[] randomNumbers;

        scaleFactor = 1.0;
        double medianValue=0;
        float tempScale = 1;
        int samplingRounds = 2000; // sets sampling maximum
        /*
         * For each round, pick a random set of integers from size of ref dataset
         */
        for (int i = 0; i < samplingRounds; i++) {
            //pick random integer
            samplingLimit = min + (int)(Math.random()*((size - min) + 1));
            randomNumbers = randomArray(samplingLimit, size);

            tempRef.reshape(samplingLimit, 1);
            tempTarget.reshape(samplingLimit, 1);

            try {
                for (int j = 0; j < samplingLimit; j++) {
                    // make sure selected Index is not the first 3 or last 3 in ref
                    tempRef.set(j, 0, ref.getY(randomNumbers[j]).doubleValue());
                    // determine if current q value is present in Target, is so add it, if not interpolate
                    // if (target.indexOf(ref.getX(randomNumbers[j])) > -1) {
                    tempTarget.set(j, 0, target.getY(target.indexOf(ref.getX(randomNumbers[j]))).doubleValue());
                }
            } catch (Exception e) {

            }

            // Need an exception handler for the solver, get NaN or Inf sometimes
            // For Ax=b
            // A.solve(b) => ref*scale = target
            try {
                tempScale = (float) tempRef.solve(tempTarget).get(0,0);
            } catch ( SingularMatrixException e ) {
                scaleFactor = 1.0;
            }

            //calculate residuals
            residualsVector = tempRef.scale(tempScale).minus(tempTarget);
            residualsFromVector.clear();
            //Squared residual
            for (int r=0; r < residualsVector.numRows(); r++){
                residualsFromVector.add(Math.pow(residualsVector.get(r, 0),2));
            }

            if (i == 0) {
                medianValue = Statistics.calculateMedian(residualsFromVector, true);
                scaleFactor = (float)tempScale;
            } else if (Statistics.calculateMedian(residualsFromVector, true) < medianValue ){
                medianValue = Statistics.calculateMedian(residualsFromVector, true);
                scaleFactor = (float)tempScale;
            }
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

}
