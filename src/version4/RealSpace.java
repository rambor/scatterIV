package version4;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import net.jafama.FastMath;

import java.awt.*;
import java.util.*;
import version4.InverseTransform.*;

/**
 * Created by robertrambo on 05/01/2016.
 */
public class RealSpace {

    private IndirectFT indirectFTModel;
    private String filename;
    private boolean selected;
    private int id;
    private int startAt;
    private int stopAt;

    private XYSeries allData;       // use for actual fit and setting spinners
    private XYSeries errorAllData;  // use for actual fit and setting spinners

    private XYSeries originalqIq;         // range of data used for the actual fit, may contain negative values
    private XYSeries fittedqIq;           // range of data used for the actual fit, may contain negative values
    private XYSeries fittedIq;           // range of data used for the actual fit, may contain negative values
    private XYSeries fittedError;        //

    private XYSeries refinedIq;           // range of data used for the actual fit, may contain negative values
    private XYSeries refinedError;        //

    private XYSeries calcIq;          // log of calculate I(q), matches logData
    private XYSeries calcqIq;         // log of calculate I(q), matches logData
    private XYSeries logData;         // only for plotting
    private XYSeries prDistribution;
    //private XYSeries refinedPrDistribution;

    private double analysisToPrScaleFactor;
    private double izero;
    private double rg;

    private double dmax;
    private double qmax;
    private double raverage;
    private double chi2;
    private float scale=1.0f;
    private Color color;
    private boolean baseShapeFilled;
    private int pointSize;
    private BasicStroke stroke;
    private double kurtosis = 0;
    private double l1_norm = 0;
    private double kurt_l1_sum;
    private double totalScore=0;
    private Dataset dataset;
    private int lowerQIndexLimit=0;
    private final int maxCount;
    private boolean negativeValuesInModel=false;
    private double standardizationMean;
    private double standardizationStDev;

    // rescale the data when loading analysisModel
    public RealSpace(Dataset dataset){
        this.dataset = dataset;
        this.filename = dataset.getFileName();
        this.id = dataset.getId();
        this.dmax = dataset.getDmax();
        selected = true;
        analysisToPrScaleFactor = 1;
        int totalAllData = dataset.getAllData().getItemCount();

        /*
         * these data are never altered
         */
        allData = dataset.getAllData();
        errorAllData = dataset.getAllDataError();
//        try {
//            allData = dataset.getAllData().createCopy(0, totalAllData-1);
//        } catch (CloneNotSupportedException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
//
//        try {
//            errorAllData = dataset.getAllDataError().createCopy(0, totalAllData-1);
//        } catch (CloneNotSupportedException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }

        // need to rescale allData for fitting?
        double digits = Math.log10(averageIntensity(allData));

        originalqIq = new XYSeries("orignal qIq data");

        for(int i=0; i<totalAllData; i++){
            XYDataItem tempItem = allData.getDataItem(i);
            originalqIq.add(tempItem.getX(), tempItem.getYValue()*tempItem.getXValue());
        }


        fittedIq = new XYSeries(Integer.toString(this.id) + " Iq-" + filename);
        fittedqIq = new XYSeries(Integer.toString(this.id) + " qIq-" + filename);
        fittedError = new XYSeries(Integer.toString(this.id) + " fittedError-" + filename);

        logData = new XYSeries(Integer.toString(this.id) + " log-" + filename);
        calcIq = new XYSeries(Integer.toString(this.id) + " calc-" + filename);
        calcqIq = new XYSeries(Integer.toString(this.id) + " calc-" + filename);

        refinedIq = new XYSeries(Integer.toString(this.id) + " refined-" + filename);
        refinedError = new XYSeries(Integer.toString(this.id) + " refined-" + filename);

        prDistribution = new XYSeries(Integer.toString(this.id) + " Pr-" + filename);
        this.color = dataset.getColor();

        pointSize = dataset.getPointSize();
        stroke = dataset.getStroke();
        scale = 1.0f;
        baseShapeFilled = false;
        //for spinners
        //this.startAt = dataset.getStart(); // spinner value in reference to non-negative data
        int startAll = dataset.getStart() - 1;
        //int startAll = dataset.getAllData().indexOf(dataset.getData().getMinX());
        int stopAll = dataset.getAllData().indexOf(dataset.getData().getMaxX());

        double xValue;
        double yValue;

        // transform all the data in allData
        //check for negative values at start
        double qlimit = 0.05;

        XYDataItem temp;
        int ii=startAll, lastNegative=-1;
        temp = allData.getDataItem(ii);
        while (temp.getXValue() < qlimit && ii < stopAll){
            if (temp.getYValue() < 0){
                lastNegative = ii;
            }
            ii++;
            temp = allData.getDataItem(ii);
        }

        lowerQIndexLimit = (lastNegative < 0) ? startAll : lastNegative+1;

        for(int i=lowerQIndexLimit; i < totalAllData; i++){
            temp = allData.getDataItem(i);
            xValue = temp.getXValue();
            yValue = temp.getYValue();

            if (temp.getYValue() > 0){
                logData.add(xValue, Math.log10(yValue)); // plotted data
            }

            fittedIq.add(temp);
            fittedError.add(errorAllData.getDataItem(i));
            fittedqIq.add(originalqIq.getDataItem(i));   // fitted data, spinners will add and remove from this XYSeries
        }

        lowerQIndexLimit++; // increment to set to value for spinner, fixed for the real-space model
        // spinners in real-space Tab are in reference to allData
        startAt=lowerQIndexLimit;
        stopAt=originalqIq.getItemCount();
        maxCount = originalqIq.getItemCount();
    }

    /**
     * max value of original dataset
     * @return
     */
    public int getMaxCount(){ return maxCount;}

    public double getGuinierRg(){
        return dataset.getGuinierRg();
    }

    public double getGuinierIzero(){
        return dataset.getGuinierIzero();
    }

    public int getLowerQIndexLimit(){ return lowerQIndexLimit;}

    public boolean isPDB(){
        return this.dataset.getIsPDB();
    }

    /**
     * shoudl match spinner index
     * @param i
     */
    public void setStart(int i){
        startAt = i;
    }

    /**
     * set start value of the spinner
     * @return
     */
    public int getStart(){
        return startAt;
    }

    public void setStop(int i){
        stopAt = i;
    }

    public int getStop(){
        return stopAt;
    }

    public double getDmax(){
        return dmax;
    }

    public void setDmax(double d){
        this.dataset.setDmax(d);
        dmax = d;
    }

    public int getId(){
        return dataset.getId();
    }

    public double getIzero(){
        return (indirectFTModel == null) ? 0 : indirectFTModel.getIZero();
    }


    public double integrateDistribution(){
        int sizeof = this.prDistribution.getItemCount()-1;
        double del_r = this.prDistribution.getX(2).doubleValue() - this.prDistribution.getX(1).doubleValue();
        double area = 0;
        for(int i=1; i<sizeof; i++){
            area += this.getPrDistribution().getY(i).doubleValue()*del_r;
        }
        return area;
    }

    public void setIzero(double j){
        izero = j;
    }

    public double getRg(){
        return rg;
    }

    public void setRg(double j){
        rg = j;
    }

    public double getRaverage(){
        return raverage;
    }

    public void setRaverage(float j){
        raverage = j;
    }

    public double getChi2(){
        return chi2;
    }


    /**
     * Returns total Score of the Pr distribution
     * Chi2 and Kurtosis (DW) must be calculated first
     *
     * @param totalPointsInUse
     * @return
     */
    public double setTotalScore(int totalPointsInUse){

        try{
            double ns = this.getIndirectFTModel().getShannonNumber();
            double kvalue = ns + 1 + 1 + 1; // lambda, dmax and noise
            double aic = 2.0*kvalue + ns*Math.log(chi2) + (2.0*kvalue*kvalue + 2*kvalue)/(totalPointsInUse - kvalue -1);
            double scoreIt = this.getIndirectFTModel().getPrScore();
//            System.out.println(dmax + " chi2 " + chi2  + " AIC: " + aic + " DW " + kurtosis + " PSI " + scoreIt + " " +(0.1*aic + 993.1*kurtosis + 3*this.getIndirectFTModel().getPrScore()));
            //double sum = (0.1*aic + 993.1*kurtosis + 3*scoreIt);
//        totalScore = (Math.log10((0.93*aic + 39.1d*kurtosis + 37.0d*scoreIt)));
            totalScore = (Math.log10(0.1*aic + (4.0d*kurtosis + scoreIt)));

        } catch (java.lang.NullPointerException exception){
           // System.out.println("PR NULL Exception :: possible pdb :: " + exception.getMessage());
            totalScore = 1;
        }

        //System.out.println(dmax + " " + String.format("%.7f",totalScore) + " " + (aic) + " " + (kurtosis) + " " + scoreIt);
        return totalScore;
    }

    public float getScale(){
        return scale;
    }

    public void setScale(float j){
        float oldscale = (float) (1.0/this.scale);
        this.scale = j;
        /*
         * scale Pr distribution
         */
        int sizeof = this.prDistribution.getItemCount();
        XYDataItem tempData;
        for(int i=0; i<sizeof; i++){
            tempData = this.getPrDistribution().getDataItem(i);
            this.prDistribution.updateByIndex(i, tempData.getYValue()*this.scale);
        }
    }

    public void setPointSize(int size){
        pointSize = size;
    }

    public Color getColor(){
        return color;
    }

    public void setColor(Color color){
        this.color = color;
    }


    public double getVolume(){
        double value = 1;
        if (dataset.getPorodVolume() > 0 ){
            value =(double)dataset.getPorodVolume();
        }

        if (dataset.getPorodVolumeReal() > 0) {
            value =(double)dataset.getPorodVolumeReal();
        }
        return value;
    }

    public int getPointSize(){
        return pointSize;
    }

    public void setStroke(float size){
        stroke = new BasicStroke(size);
    }

    public BasicStroke getStroke(){
        return stroke;
    }

    public void setBaseShapeFilled(boolean what){
        baseShapeFilled = what;
    }

    public boolean getBaseShapeFilled(){
        return baseShapeFilled;
    }

    public void setSelected(boolean what){
        selected = what;
    }

    public void setCalcIq(XYSeries data){
        this.calcIq = data;
    }

    public void setPrDistribution(XYSeries data){
        prDistribution.clear();
        int totalToSet = data.getItemCount();
        for (int i=0; i<totalToSet; i++){
            XYDataItem item = data.getDataItem(i);
            prDistribution.add(item.getX(), item.getYValue()*scale);
            //prDistribution.add(data.getDataItem(i));
        }
    }

    public boolean getSelected(){
        return selected;
    }

    public String getFilename(){
        return filename;
    }

    public XYSeries getAllData(){
        return allData;
    }

    public XYSeries getCalcIq(){
        return calcIq;
    }


    public XYSeries getErrorAllData(){
        return errorAllData;
    }

    /**
     * return range of data used for fitting, includes negative Intensities
     * @return
     */
    public XYSeries getfittedIq(){

        try {
            fittedIq = allData.createCopy(startAt-1, stopAt-1);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return fittedIq;
    }

    /**
     * fittedqIq is actively managed by spinners, so should be always up-to-date
     * @return
     */
    public XYSeries getfittedqIq(){
        return fittedqIq;
    }

    public XYSeries getfittedError(){
        try {
            fittedError = errorAllData.createCopy(startAt-1, stopAt-1);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return fittedError;
    }

    public XYSeries getLogData(){
        return logData;
    }

    public XYSeries getPrDistribution(){
        return prDistribution;
    }

    public double getAnalysisToPrScaleFactor(){
        return analysisToPrScaleFactor;
    }

    public double getKurt_l1_sum() {
        return kurtosis;
        //return kurt_l1_sum;
    }

    public double[] getCoefficients() {
        return indirectFTModel.getCoefficients();
    }



    /**
     *
     * Returns qmax used in IFT
     * @return
     */
    public double getQmax(){
        return qmax;
    }

    public void setQmax(double value ){
        this.qmax = value;
    }

    public double getArea(){return indirectFTModel.getArea();}


    /**
     * Calculate P(r) at specified r value
     *
     * @param r_value
     * @return scaled value
     */
    public double calculatePofRAtR(double r_value){
        return indirectFTModel.calculatePofRAtR(r_value, scale);
    }


    /**
     *
     * @param isqIq
     */
    public void calculateIntensityFromModel(boolean isqIq){

        if (!this.isPDB()){
            if (isqIq) {
                this.calculateQIQ();
            } else {
                this.calculateIofQ();
            }
        }
    }

    /**
     * calculate QIQ for plotting, includes negative values
     */
    public void calculateQIQ(){
        calcqIq.clear();

        XYDataItem temp;
        //iterate over each q value and calculate I(q)
        int startHere = startAt -1;
        if (this.isPDB()){

        } else {
            for (int j=startHere; j<stopAt; j++){
                temp = allData.getDataItem(j); // allData gives q-value
                calcqIq.add(temp.getX(), this.indirectFTModel.calculateQIQ(temp.getXValue()));
            }
        }
    }

    /**
     *
     * @return calcqIq XYSeries
     */
    public XYSeries getCalcqIq(){
        return calcqIq;
    }

    /**
     * @return XYSeries of log10 Intensities
     */
    public void calculateIofQ(){
        calcIq.clear();
        double iofq;
        XYDataItem temp;
        //iterate over each q value and calculate I(q)
        int startHere = startAt -1;

        if (this.isPDB()){

        } else {

            for (int j = startHere; j < stopAt; j++) {
                temp = allData.getDataItem(j);
                iofq = this.indirectFTModel.calculateIQ(temp.getXValue());

                if (iofq > 0) {
                    calcIq.add(temp.getXValue(), FastMath.log10(iofq));
                }
            }
        }
    }


    public double getICalcAtQ(double q){
        return this.indirectFTModel.calculateIQ(q);
        //return this.indirectFTModel.calculateIQDirect(q);
    }

    public double extrapolateToLowQ(double q){

        double intensity = Math.log(this.izero) - (this.rg*this.rg)/3.0*q*q;
        return Math.exp(intensity);
    }

    /**
     * calculate 2nd derivative at r-values determined by ShannonNumber + 1
     * @param multiple
     * @return
     */
    public double l1_norm_pddr(int multiple){
        double inv_d = 1.0/this.dmax;
        double r_value, pi_r_n_inv_d, pi_r_inv_d, cos_pi_r_n_inv_d, sin_pi_r_n_inv_d;
        double pi_inv_d = Math.PI*inv_d;
        double n_pi_inv_d;
        double inv_2d = inv_d*0.5;
        double a_i;

        //int r_limit = r_vector.length;
        int coeffs_size = this.getTotalFittedCoefficients();

        double a_i_sum, product, l1_norm = 0.0;

        double shannon = Math.ceil(multiple*1.0/Math.PI*this.dmax*this.fittedqIq.getMaxX());
        double delta_r = this.dmax/shannon;
        double[] r_vector = new double[(int)shannon + 1];

        int k=0;
        while (k*delta_r < this.dmax){
            r_vector[k] = k*delta_r;
            k += 1;
        }
        r_vector[(int)shannon] = this.dmax;

        int r_limit = (int)shannon + 1;

        double costerm, sinterm;
// generic function to return 2nd derivative from IndirectFT class
//        for (int r=0; r < r_limit; r++){
//            r_value = r_vector[r];
//            pi_r_inv_d = pi_inv_d*r_value;
//
//            costerm = 0;
//            sinterm = 0;
//
//            for(int n=1; n < coeffs_size; n++){
//
//                a_i = this.mooreCoefficients[n];
//                n_pi_inv_d = pi_inv_d*n;
//
//                pi_r_n_inv_d = pi_r_inv_d*n;
//
//                costerm += a_i*n_pi_inv_d*Math.cos(pi_r_n_inv_d);
//                sinterm += a_i*n_pi_inv_d*n_pi_inv_d*Math.sin(pi_r_n_inv_d);
//            }
//
//            l1_norm += Math.abs(2*costerm - r_value*sinterm);
//        }

        //System.out.println("l1_norm pddr " + (l1_norm/shannon));
        return l1_norm/shannon;
    }

    public double max_kurtosis_shannon_sampled(){
        return kurtosis;
    }



    public void decrementLow(int spinnerValue){

        Number currentQ = fittedqIq.getX(0);

        int total = spinnerValue - this.startAt;

        if (total == 1){
            if (currentQ.equals(logData.getX(0))){
                fittedqIq.remove(0);
                logData.remove(0);
            } else {
                // find where in logData is currentQ
                int stopHere = logData.indexOf(currentQ);
                fittedqIq.remove(0);
                logData.delete(0, stopHere);
            }
        } else {

            // spinner value displays the first or last value plotted
            // if spinnervalue is 10, we are showing the value in array position 9
            int stopHere = logData.indexOf(allData.getX(spinnerValue-1));

            int totalminus = total-1;
            fittedqIq.delete(0,totalminus);
            logData.delete(0,stopHere - 1);
            //int totalminus = total-1;
            //fittedqIq.delete(0,totalminus);
            //logData.delete(0,stopHere);
        }
    }


    /**
     * use this to sync the start and stop points of fittedqIq to data in Analysis tab
     */
    public void resetStartStop(){
        // start should be the low-q value
        // spinner in Analysis tab follows non-negative values
        // spinner in Pr follows originalqIq but limited by lowerQIndex

        int tempStart = originalqIq.indexOf(this.dataset.getData().getMinX()) + 1;

        if (tempStart < lowerQIndexLimit){
            this.startAt = lowerQIndexLimit;
        } else {
            this.startAt = tempStart;
        }

        this.stopAt = originalqIq.indexOf(this.dataset.getData().getMaxX());

        logData.clear();
        fittedIq.clear();
        fittedqIq.clear();
        fittedError.clear();

        XYDataItem temp;
        for(int i=startAt-1; i < stopAt; i++){
            temp = allData.getDataItem(i);

            if (temp.getYValue() > 0){
                logData.add(temp.getXValue(), Math.log10(temp.getYValue()));
            }

            fittedIq.add(temp);
            fittedError.add(errorAllData.getDataItem(i));
            fittedqIq.add(originalqIq.getDataItem(i));   // fitted data, spinners will add and remove from this XYSeries
        }
    }


    public void incrementLow(int target){

        // add point to fittedqiq
        int diff = this.getStart() - target;

        if (diff == 1){
            XYDataItem tempXY = originalqIq.getDataItem(target-1);
            fittedqIq.add(tempXY);

            if (tempXY.getYValue() > 0){
                logData.add(tempXY.getX(), Math.log10(allData.getY(target-1).doubleValue()));
            }
        } else { // add more than one point
            int indexStart = this.getStart()-1;

            for (int i=0; i<diff && indexStart >= 0; i++){
                indexStart--;
                XYDataItem tempXY = originalqIq.getDataItem(indexStart);

                fittedqIq.add(tempXY);
                if (tempXY.getYValue() > 0) {
                    logData.add(tempXY.getX(), Math.log10(allData.getY(indexStart).doubleValue()));
                }
            }
        }
        this.startAt = target;
    }


    public void incrementHigh(int target){

        // add point to fittedqiq
        int diff = target - this.getStop();

        if (diff == 1){
            XYDataItem tempXY = originalqIq.getDataItem(target-1);
            fittedqIq.add(tempXY);

            if (tempXY.getYValue() > 0){
                logData.add(tempXY.getX(), Math.log10(allData.getY(target-1).doubleValue()));
            }
        } else { // add more than one point
            int indexStart = this.getStop();
            int maxCount = originalqIq.getItemCount();
            int limit = (target > maxCount) ? maxCount : (target);

            for (int i=indexStart; i < limit; i++){
                XYDataItem tempXY = originalqIq.getDataItem(i);
                fittedqIq.add(tempXY);

                if (tempXY.getYValue() > 0) {
                    logData.add(tempXY.getX(), Math.log10(allData.getY(i).doubleValue()));
                }
            }

            target = limit;
        }
        this.stopAt = target;
    }


    public void decrementHigh(int spinnerValue){

        int total = this.stopAt - spinnerValue;

        if (total == 1){
            Number currentQ = fittedqIq.getMaxX();
            if (currentQ.equals(logData.getMaxX())){
                fittedqIq.remove(fittedqIq.getItemCount()-1);
                logData.remove(logData.getItemCount()-1);
            }
        } else {

            int maxFittedqiq = fittedqIq.getItemCount();

            int delta = this.stopAt - spinnerValue;
            // spinnerValue is in reference to complete original data
            fittedqIq.delete(maxFittedqiq - delta, maxFittedqiq-1);

            double lastValue = fittedqIq.getMaxX();
            if (logData.getMaxX() > lastValue) { // if true, keep deleting until logData.MaxX <= fittedqIq.maxX
                int last = logData.getItemCount();
                while (logData.getMaxX() > lastValue){
                    last--;
                    logData.remove(last);
                }
            }
        }

        this.stopAt = spinnerValue;
    }

    /**
     * should be unscaled data, as is
     * @param data
     * @param error
     */
    public void updatedRefinedSets(XYSeries data, XYSeries error){
        int total = data.getItemCount();
        refinedIq.clear();
        refinedError.clear();
        for (int i=0; i<total; i++){
            refinedIq.add(data.getDataItem(i));
            refinedError.add(error.getDataItem(i));
        }
    }

    public XYSeries getRefinedqIData(){
        return refinedIq;
    }

    /**
     * assumes I(q) and not q*I(q)
     * Estimates chi-square at the cardinal series
     * @throws Exception
     */
    public void chi_estimate() {
        chi2=this.indirectFTModel.getChiEstimate();
    }

    /**
     * Total fitted coefficients that excludes the background term (based on Information Theory)
     * @return
     */
    public int getTotalFittedCoefficients(){ return this.indirectFTModel.getTotalFittedCoefficients();}

    private double averageIntensity(XYSeries data){
        int total = data.getItemCount();
        double sum=0;
        for (int i=0; i<total; i++){
            sum+=data.getY(i).doubleValue();
        }
        return sum/(double)total;
    }


    public double getReciprocalSpaceScaleFactor(){ return dataset.getScaleFactor();}

    public void estimateErrors(){
        indirectFTModel.estimateErrors(this.getfittedqIq());
        //System.out.println("ERRORS Rg: " + this.dataset.getRealRgSigma() + " IZERO: " + this.dataset.getRealIzeroSigma());
        //this.dataset.updateRealSpaceErrors(rgStat.getStandardDeviation()/rgStat.getMean(), izeroStat.getStandardDeviation()/izeroStat.getMean());
    }




    private double integralTransform(double rvalue, XYSeries extrapolatedQIqSeries){

        // create XYSeries
        XYSeries transformedSeries = new XYSeries("Sine Transformed");
        XYDataItem tempqIqDataItem;

        for (int i=0; i< extrapolatedQIqSeries.getItemCount(); i++){
            tempqIqDataItem = extrapolatedQIqSeries.getDataItem(i);
            transformedSeries.add(tempqIqDataItem.getX(), tempqIqDataItem.getYValue()*Math.sin(tempqIqDataItem.getXValue()*rvalue));
        }

        // trapezoid rule integration
        return Functions.trapezoid_integrate(transformedSeries);
    }

    /**
     * Use forward difference approximation to 2nd order to approximate derivative of P(r)-distribution
     * @param rvalue
     * @param extrapolatedQIqSeries
     * @return
     */
    private double finiteDifferencesDerivative(double rvalue, XYSeries extrapolatedQIqSeries){
        double h_increment = 0.7;

        // Centered
        //double sumIt = -integralTransform(rvalue+2*h_increment, extrapolatedQIqSeries) + 8*integralTransform(rvalue+h_increment, extrapolatedQIqSeries) - 8*integralTransform(rvalue-h_increment, extrapolatedQIqSeries) + integralTransform(rvalue-2*h_increment, extrapolatedQIqSeries);
        // Forward
        // double sumIt = -integralTransform(rvalue+2*h_increment, extrapolatedQIqSeries) + 4*integralTransform(rvalue+h_increment, extrapolatedQIqSeries) - 3*integralTransform(rvalue, extrapolatedQIqSeries);
        // Reverse
        double sumIt = 3*integralTransform(rvalue, extrapolatedQIqSeries) - 4*integralTransform(rvalue - h_increment, extrapolatedQIqSeries) + integralTransform(rvalue - 2*h_increment, extrapolatedQIqSeries);
        //return sumIt/(12*h_increment);
        return sumIt/(2*h_increment);
    }


    public void setStandardizationMean(double value, double stdev){
        this.standardizationMean = value;
        this.standardizationStDev = Math.abs(stdev);
//        System.out.println("Standardized Mean " + value + " std scale " + stdev);
    }

    public double getStandardizationMean(){
        return this.standardizationMean;
    }

    public double getStandardizationStDev(){
        return this.standardizationStDev;
    }

    public void setIndirectFTModel(IndirectFT model){

        //this.indirectFTModel = new IndirectFT(model);

        if (model instanceof MooreTransformApache){
            this.indirectFTModel = new MooreTransformApache((MooreTransformApache)model);
        } else if(model instanceof DirectSineIntegralTransform){
            this.indirectFTModel = new DirectSineIntegralTransform((DirectSineIntegralTransform) model);
        } else if (model instanceof LegendreTransform){
            this.indirectFTModel = new LegendreTransform((LegendreTransform)model);
        } else if(model instanceof SVD){
            this.indirectFTModel = new SVD((SVD)model);
        } else if(model instanceof SineIntegralTransform){
            this.indirectFTModel = new SineIntegralTransform((SineIntegralTransform)model);
        }

        this.rg = (model.getRg() > 0) ? model.getRg() : this.rg ;
        this.izero = model.getIZero();
        this.raverage = (model.rAverage > 0) ? model.rAverage : this.raverage ;
        this.chi2 = this.indirectFTModel.getChiEstimate();
        this.kurtosis = this.indirectFTModel.getKurtosisEstimate(0);

        if (this.dataset.getInvariantQ() > 0){
            this.dataset.setPorodVolumeReal((int)(Constants.TWO_PI_2*dataset.getRealIzero()/this.dataset.getInvariantQ()));
        }

        // set molecular mass usingn Vc - must use real space data range
    }

    private void calculateVCMW(){
        // using i-zero, Rg calculate extrapolated datasets


    }

    public IndirectFT getIndirectFTModel(){
        return this.indirectFTModel;
    }


    public XYSeries getOriginalqIq() { return this.originalqIq;}

    public double getTotalScore() {
        return totalScore;
    }
}
