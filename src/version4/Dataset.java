package version4;

import FileManager.FileObject;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.YIntervalDataItem;
import org.jfree.data.xy.YIntervalSeries;
import version4.sasCIF.SasObject;

import javax.xml.crypto.Data;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Random;
import java.util.Vector;

public class Dataset {

    private final int totalCountInAllData;
    private final int totalCountInPositiveData;
    private int startAt;  // start of the nonNegativeData
    private int endAt;    // end of the nonNegativeData
    private int indexOfUpperGuinierFit; //belongs to positiveOnlyData
    private int indexOfLowerGuinierFit; // belongs to positiveOnlyData

    private SasObject sasObject = null;

    private String filename;
    private String originalFilename;

    double scaleFactor, log10ScaleFactor;

    private double maxI;
    private double minI;
    private double maxq;
    private double minq;

    private double rC = 0;
    private double rC_sigma = 0;
    private double dMax;
    private double porodExponent = 0;
    private double porodExponentError = 0;
    private double invariantQ;
    private double porodVolumeQmax;

    private double averageR;
    private double averageRSigma;
    private int porodVolume = 0;
    private int porodVolumeReal = 0;
    private int porodMass1p07 = 0;
    private int porodRealMass1p1 = 0;
    private int porodMass1p11 = 0;
    private int porodRealMass1p37 = 0;
    private int massProtein, massProteinSigma, massRna, massRnaSigma;
    private int massProteinReal,massProteinSigmaReal, massRnaReal,massRnaSigmaReal;

    private double vc, vcSigma, vcReal, vcSigmaReal;

    // GUI stuff
    private boolean inUse;
    private boolean fitFile;
    private Color color;
    private boolean baseShapeFilled;
    private int pointSize;
    private BasicStroke stroke;

    private boolean isPDB = false;
    private int id;

    private String experimentalNotes, experimentalNoteTitle, bufferComposition;

    private XYSeries plottedData;                // plotted log10 data
    private YIntervalSeries plottedErrors;    // plotted log10 errors data
    private XYSeries plottedKratkyData;          // plotted Kratky data
    private XYSeries plottedqIqData;             // plotted qIq data
    private XYSeries plottedPowerLaw;            // plotted log10 errors data

    private XYSeries powerLawData;          // derived from originalPositiveOnlyData

    private XYSeries normalizedKratkyReciprocalSpaceRgData;
    private XYSeries normalizedKratkyRealSpaceRgData;
    private XYSeries normalizedKratkyReciprocalSpaceVcData;
    private XYSeries normalizedKratkyRealSpaceVcData;

    private XYSeries originalLog10Data;         // not to be modified
    private final XYSeries originalPositiveOnlyData;  // not to be modified
    private final XYSeries originalPositiveOnlyError; // not to be modified
    private final XYSeries allData;              // not to be modified
    private final XYSeries allDataError;         // not to be modified

    private XYSeries kratkyData;            // derived from allData
    private XYSeries qIqData;               // derived from allData
    private YIntervalSeries allDataYError;  // not to be modified
    private XYSeries guinierData; // derived from originalPositiveOnlyData

    private Guinier guinierObject;

    private double prScaleFactor;
    private double realIZero_sigma, realRg_sigma;
    private RealSpace realSpace;


    /*
     Add property change listener for scale factor
     */

    protected Vector propChangeListeners = new Vector();

    public Dataset(XYSeries dat, XYSeries err, int id){
        totalCountInAllData = dat.getItemCount();
        totalCountInPositiveData = 0;
        filename = "noname";
        String tempName = filename + "-" + id;
        originalFilename = filename;
        scaleFactor=1.000;
        this.id = id;
        color = Color.black;
        inUse = true;
        experimentalNotes ="";
        experimentalNoteTitle="";
        bufferComposition ="";

        allData = new XYSeries(tempName);
        allDataError = new XYSeries(tempName);
        originalPositiveOnlyData = new XYSeries(tempName);
        originalPositiveOnlyError = new XYSeries(tempName);

        for(int i=0; i<totalCountInAllData; i++) {
            XYDataItem tempXY = dat.getDataItem(i);
            XYDataItem tempError = err.getDataItem(i);
            allData.add(tempXY);
            allDataError.add(tempError);
        }

        guinierObject = new Guinier(0,0,0,0,0);
    }


    /**
     * basic constructor, does not precalculate everything
     * @param dat
     * @param err
     * @param fileName
     * @param id
     */
    public Dataset(XYSeries dat, XYSeries err, String fileName, int id){
        totalCountInAllData = dat.getItemCount();
        filename = fileName;
        String tempName = fileName + "-" + id;
        originalFilename = fileName;

        scaleFactor=1.000;

        baseShapeFilled = false;
        pointSize = 6;
        this.setStroke(1.0f);
        this.id = id;

        Random rand=new Random();
        color = new Color(rand.nextInt(256),rand.nextInt(256),rand.nextInt(256));
        inUse = true;

        experimentalNotes ="";
        experimentalNoteTitle="";
        bufferComposition ="";

        allData = new XYSeries(tempName);
        allDataError = new XYSeries(tempName);

//        plottedData = new XYSeries(tempName);  // actual log10 data that is plotted


//        originalLog10Data = new XYSeries(tempName);
        originalPositiveOnlyData = new XYSeries(tempName);
        originalPositiveOnlyError = new XYSeries(tempName);
//        guinierData = new XYSeries(tempName); // derived from originalPositiveOnlyData
//        int logCount=0;
        for(int i=0; i<totalCountInAllData; i++) {
            XYDataItem tempXY = dat.getDataItem(i);
            XYDataItem tempError = err.getDataItem(i);

            allData.add(tempXY);
            allDataError.add(tempError);

            if (tempXY.getYValue() > 0){
//                double q = tempXY.getXValue();
//                double q2 = q*q;
                originalPositiveOnlyData.add(tempXY);
                originalPositiveOnlyError.add(tempError);
//                originalLog10Data.add(tempXY.getX(), Math.log10(tempXY.getYValue()));
//                plottedData.add(originalLog10Data.getDataItem(logCount));
//                guinierData.add(q2, Math.log(tempXY.getYValue()));
//                logCount++;
            }
        }

        maxI = this.originalPositiveOnlyData.getMaxY();  //log10 data
        minI = this.originalPositiveOnlyData.getMinY();  //log10 data
        maxq = this.originalPositiveOnlyData.getMaxX();  //
        minq = this.originalPositiveOnlyData.getMinX();  //

        this.startAt=1;  // with respect to positivelyOnlyData
        this.endAt = originalPositiveOnlyData.getItemCount();
        this.totalCountInPositiveData = originalPositiveOnlyData.getItemCount();
        guinierObject = new Guinier(0,0,0,0,0);
    }


    /**
     * Use this constructor for pre-calculating all the things for the plots, kratky, etc
     * do not use if loading data for SEC or Subtraction Panel
     *
     * @param dat current dataset
     * @param err sigma series
     * @param fileName name of the file
     */
    public Dataset(XYSeries dat, XYSeries err, String fileName, int id, boolean doGuinier) {

        this(dat, err, fileName, id);
        // if possible do preliminary analysis here
        if (doGuinier){
            this.calculateRg();
            startAt = indexOfLowerGuinierFit + 1;
        }

        String tempName = (String)allData.getKey();
        kratkyData = new XYSeries(tempName);            // derived from allData
        qIqData = new XYSeries(tempName);               // derived from allData
        powerLawData = new XYSeries(tempName);          // derived from originalPositiveOnlyData

        plottedData = new XYSeries(tempName, true, false);  // actual log10 data that is plotted
        plottedErrors = new YIntervalSeries(tempName, true, false);
        allDataYError = new YIntervalSeries(tempName);
        plottedKratkyData = new XYSeries(tempName);
        plottedqIqData = new XYSeries(tempName);
        plottedPowerLaw = new XYSeries(tempName);

        guinierData = new XYSeries(tempName); // derived from originalPositiveOnlyData
        originalLog10Data = new XYSeries(tempName);

        for(int i=0; i<totalCountInAllData; i++) {
            XYDataItem tempXY = allData.getDataItem(i);
            XYDataItem tempError = allDataError.getDataItem(i);
            double q = tempXY.getXValue();
            double q2 = q*q;
            //kratky and q*I(q)
            kratkyData.add(q, q2*tempXY.getYValue());  // should not be modified
            qIqData.add(q, q*tempXY.getYValue());      // should not be modified

            allDataYError.add(tempXY.getXValue(), tempXY.getYValue(), tempXY.getYValue()-tempError.getYValue(), tempXY.getYValue()+tempError.getYValue());
        }

        for(int i=0; i<originalPositiveOnlyData.getItemCount();i++){
            XYDataItem tempXY = originalPositiveOnlyData.getDataItem(i);
            double q = tempXY.getXValue();
            double q2 = q*q;
            double log10y = Math.log10(tempXY.getYValue());
            originalLog10Data.add(tempXY.getX(), log10y);
            double logy = Math.log(tempXY.getYValue());
            powerLawData.add(Math.log(q), logy );
            guinierData.add(q2, logy);
        }

        // since Guinier fitting might truncate low-q must use subset
        for(int i=(startAt-1); i<originalPositiveOnlyData.getItemCount();i++){
            plottedData.add(originalLog10Data.getDataItem(i));
        }

        normalizedKratkyReciprocalSpaceRgData = new XYSeries(tempName);  // derived from allData
        normalizedKratkyRealSpaceRgData = new XYSeries(tempName);  // derived from allData
        normalizedKratkyReciprocalSpaceVcData = new XYSeries(tempName);  // derived from allData
        normalizedKratkyRealSpaceVcData = new XYSeries(tempName);  // derived from allData

        this.realSpace = new RealSpace(this);
    }

    public void setStroke(float size){
        stroke = new BasicStroke(size);
    }

    public double getGuinierRg(){ return guinierObject.rg; }
    public double getGuinierRgerror(){ return guinierObject.rgerror; }
    public double getGuinierIzero(){ return guinierObject.izero; }
    public double getGuinierIzeroError(){ return guinierObject.izeroerror; }
    public double getGuinierCorrelationCoefficient(){ return guinierObject.cc; }

    private void calculateRg(){
        AutoRg tempRg = new AutoRg(this.originalPositiveOnlyData, startAt);
        if (tempRg.getRg() > 0){
            guinierObject = new Guinier(tempRg.getRg(), tempRg.getRg_error(), tempRg.getI_zero(), tempRg.getI_zero_error(), tempRg.getCorrelation_coefficient());
            indexOfLowerGuinierFit = this.originalPositiveOnlyData.indexOf(tempRg.getQminFinal());
            indexOfUpperGuinierFit = this.originalPositiveOnlyData.indexOf(tempRg.getQmaxFinal());
        }
    }

    public void setGuinierParameters(double rg, double rgerror, double izero, double izeroerror, double cc){
        guinierObject = new Guinier(rg, rgerror, izero, izeroerror, cc);
    }


    public void setIndexOfUpperGuinierFit(int index){
        this.indexOfUpperGuinierFit = index;
    }
    public int getIndexOfUpperGuinierFit(){
        return this.indexOfUpperGuinierFit;
    }

    public int getIndexOfLowerGuinierFit() {
        return indexOfLowerGuinierFit;
    }

    public void setIndexOfLowerGuinierFit(int indexOfLowerGuinierFit) {
        this.indexOfLowerGuinierFit = indexOfLowerGuinierFit;
    }



    public double getAverageR() {
        return realSpace.getRaverage();
    }

    class Guinier{
        public double rg, rgerror, izero, izeroerror, cc;

        public Guinier(double rg, double rgerror, double izero, double izeroerror, double cc) {
            this.rg = rg;
            this.rgerror = rgerror;
            this.izero = izero;
            this.izeroerror = izeroerror;
            this.cc = cc;
        }
    }


    /**
     * Returns unmodified XYSeries dataset, includes negative intensities, (q, I(q))
     * @return XYSeries parsed from file
     */
    public XYSeries getAllDataError(){
        return allDataError;
    }

    /**
     * Returns unmodified XYSeries dataset, includes negative intensities, (q, I(q))
     * @return XYSeries parsed from file
     */
    public XYSeries getAllData(){
        return allData;
    }

    /**
     * Returns XYSeries dataset used for plotting with y-axis on log10 scale
     * @return XYSeries of the dataset (plottedData)
     */
    public XYSeries getData(){
        return plottedData;
    }

    public void setInUse(boolean selected){
        inUse = selected;
    }

    public boolean getInUse(){
        return inUse;
    }

    /**
     * Sets ScaleFactor
     * @param factor
     */
    public void setScaleFactor(double factor){
        log10ScaleFactor = Math.log10(factor);
        scaleFactor=factor;
        this.notifyScaleChange();
        // rescale plottedData
        //this.scalePlottedLog10IntensityData();
    }

    public void setId(int value){
        id = value;
    }

    public int getId() {
        return id;
    }

    /**
     * Returns starting point of the series with respect to Positive Only Data
     * @return Start point
     */
    public int getStart(){
        return startAt;
    }

    /**
     * Returns end point of the series with respect to Positive Only Data
     * @return end point
     */
    public int getEnd(){
        return endAt;
    }

    public void setColor(Color randColor){
        color = randColor;
    }

    public Color getColor(){
        return color;
    }

    public void setPointSize(int size){
        pointSize = size;
    }

    public int getPointSize(){
        return pointSize;
    }

    public BasicStroke getStroke(){
        return stroke;
    }

    /**
     * Returns full path file name
     * @return full path of the file
     */
    public String getFileName(){
        return filename;
    }

    public boolean getIsPDB(){
        return this.isPDB;
    }

    /**
     *
     * @return Rc
     */
    public double getRc(){
        return rC;
    }

    /**
     *
     * @return Rc sigma
     */
    public double getRcSigma(){
        return rC_sigma;
    }

    /**
     *
     * @return  Dmax
     */
    public double getDmax(){
        return dMax;
    }

    /**
     *
     * @return Porod Exponent
     */
    public double getPorodExponent(){
        return porodExponent;
    }

    /**
     *
     * @return Porod Exponent
     */
    public double getPorodExponentError(){
        return porodExponentError;
    }

    /**
     *
     * @return Scale Factor
     */
    public double getScaleFactor(){
        return scaleFactor;
    }

    public int getPorodVolume(){
        return porodVolume;
    }

    public void copyAndRenameDataset(String newName, String cwd){
        String base = newName.replaceAll("\\W","_");
        this.appendExperimentalNotes("ORIGINAL FILE : " + this.filename);
        this.originalFilename = this.filename;
        this.setFileName(base);
        FileObject dataToWrite = new FileObject(new File(cwd));
        dataToWrite.writeSAXSFile(base, this);
    }

    public void appendExperimentalNotes(String text) {
        this.experimentalNotes = this.experimentalNotes + "\n"  + text;
    }

    /**
     * Returns full path file name
     */
    public void setFileName(String text){
        this.filename = text;
    }

    public RealSpace getRealSpaceModel(){
        return realSpace;
    }

    /**
     *
     * @return Izero of the series calculated from P(r)
     */
    public double getRealIzero(){
        return realSpace.getIzero();
    }

    /**
     *
     * @return standard error of guinier Izero calculated from P(r)
     */
    public double getRealIzeroSigma(){
        return realIZero_sigma;
    }

    /**
     * @return real Rg of the series calculated from P(r)
     */
    public double getRealRg(){
        return realSpace.getRg();
    }

    /**
     *
     * @return standard error of real rg calculated from P(r)
     */
    public double getRealRgSigma(){
        return realRg_sigma;
    }

    /**
     * Sets Dmax
     *
     */
    public void setDmax(double dmax){
        dMax=dmax;
    }

    public int getPorodVolumeReal(){
        return porodVolumeReal;
    }
    public double getVC(){
        return vc;
    }
    public double getVCSigma(){
        return vcSigma;
    }
    public int getMassProtein(){
        return massProtein;
    }
    public int getMassProteinSigma(){
        return massProteinSigma;
    }
    public int getMassRna(){
        return massRna;
    }
    public int getMassRnaSigma(){
        return massRnaSigma;
    }
    public double getVCReal(){
        return vcReal;
    }
    public double getVCSigmaReal(){
        return vcSigmaReal;
    }

    public void setPorodVolume(int porodV){
        porodVolume=porodV;
        this.calculatePorodMass();
    }


    public void calculatePorodMass(){
        porodMass1p07 = (int)(porodVolume*1.07/1.66);
        porodMass1p11 = (int)(porodVolume*1.1/1.66);

        if (porodVolumeReal > 0){
            porodRealMass1p1 = (int)(porodVolumeReal*1.07/1.66);
            porodRealMass1p37 = (int)(porodVolumeReal*1.1/1.66);
        }
    }


    public void setPorodVolumeReal(int porodVR){
        porodVolumeReal=porodVR;
        this.calculatePorodMass();
    }

    public int getPorodVolumeRealMass1p1(){return porodRealMass1p1;}
    public int getPorodVolumeRealMass1p37(){return porodRealMass1p37;}

    public int getPorodVolumeMass1p1(){return porodMass1p07;}
    public int getPorodVolumeMass1p37(){return porodMass1p11;}


    public int getMassProteinReal(){
        return massProteinReal;
    }
    public int getMassRnaReal(){
        return massRnaReal;
    }

    public String getExperimentalNotes(){
        return this.experimentalNotes;
    }
    public String getExperimentalNoteTitle() { return this.experimentalNoteTitle;}

    public void setBufferComposition(String text){
        this.bufferComposition = text;
    }

    public String getBufferComposition(){
        return this.bufferComposition;
    }



    /**
     * scales data
     */
    public void scalePlottedErrorData(){
        plottedErrors.clear();
        YIntervalDataItem temp;

        int startHere = startAt - 1;

        int startINdex = allData.indexOf(plottedData.getX(startHere));
        int endIndex = allData.indexOf(plottedData.getMaxX());

        double qvalue;

        if (scaleFactor != 1){
            for (int i = startINdex; i <= endIndex; i++){
                temp = (YIntervalDataItem) allDataYError.getDataItem(i);
                qvalue = temp.getX();
                plottedErrors.add(temp.getX(), qvalue*temp.getYValue()*scaleFactor, qvalue*temp.getYLowValue()*scaleFactor, qvalue*temp.getYHighValue()*scaleFactor);
            }
        } else {
            for (int i = startINdex; i < endIndex; i++){
                temp = (YIntervalDataItem) allDataYError.getDataItem(i);
                qvalue = temp.getX();
                plottedErrors.add(temp.getX(), qvalue*temp.getYValue(), qvalue*temp.getYLowValue(), qvalue*temp.getYHighValue());
            }
        }
    }


    public YIntervalSeries getPlottedErrors(){ return plottedErrors;}


    /**
     *
     */
    public synchronized void scalePlottedLog10IntensityData(){

        int endOf = plottedData.getItemCount();
        int startHere = this.startAt - 1 ;

        plottedData.setNotify(false);
        if (scaleFactor != 1){

            for (int i = 0; i < endOf; i++){
                plottedData.updateByIndex(i, originalLog10Data.getY(i +startHere).doubleValue() + log10ScaleFactor);
            }

        } else {

            for (int i = 0; i< endOf; i++){
                plottedData.updateByIndex(i, originalLog10Data.getY(i + startHere));
            }

        }
        plottedData.setNotify(true);
    }


    /**
     * Sets starting point of the series
     *
     */
    public void setStart(int st){
        this.notifyStartIndexChanged(st);
        this.startAt=st;
    }


    /**
     * Sets end point of the series
     *
     */
    public void setEnd(int en){

        if (en > this.totalCountInPositiveData){
            System.out.println("WARNING EN " + en + " > " + this.totalCountInPositiveData);
        }

        this.notifyEndIndexChanged(en);
        this.endAt=en;
    }


    public double getMaxI(){
        return maxI;
    }

    public double getMinI(){
        return minI;
    }

    public double getMaxq(){
        return maxq;
    }

    public double getMinq(){
        return minq;
    }

    public void setIsPDB(XYSeries pofrDistribution, float dmax, double rg, double izero){
        this.realSpace.setPrDistribution(pofrDistribution);
        this.realSpace.setDmax(dmax);
        this.isPDB = true;
        this.realSpace.setRg(rg);
        this.realSpace.setIzero(izero);
    }

    /**
     * Returns dataItem of log10Data that is scaled
     * @return XYDataItem
     */
    public XYDataItem getScaledLog10DataItemAt(int index){
        XYDataItem temp = originalLog10Data.getDataItem(index);
        Number qvalue = temp.getX();
        return new XYDataItem(qvalue, temp.getYValue() + this.log10ScaleFactor);
    }

    /**
     * Returns unmodified XYSeries dataset (q,log10(I))
     * @return XYSeries parsed from file
     */
    public XYSeries getOriginalLog10Data(){
        return originalLog10Data;
    }

    public XYSeries getPlottedKratkyDataSeries(){
        return plottedKratkyData;
    }

    public boolean getBaseShapeFilled(){
        return baseShapeFilled;
    }


    /**
     * scales Kratky data if visible
     */
    public void scalePlottedKratkyData(){
        plottedKratkyData.clear();
        XYDataItem temp;

        int endHere = qIqData.indexOf(plottedData.getX(plottedData.getItemCount()-1));
        int startHere = qIqData.indexOf(plottedData.getX(0));

        if (scaleFactor == 1){
            for (int i = startHere; i<endHere; i++){
                plottedKratkyData.add(kratkyData.getDataItem(i));
            }
        } else {
            for (int i = startHere; i<endHere; i++){
                temp = kratkyData.getDataItem(i);
                plottedKratkyData.add(temp.getX(), temp.getYValue()*scaleFactor);
            }
        }
    }


    /**
     * Reciprocal Space Dimensionless Kratky Plot
     * @return XYSeries for normalized Kratky plot
     */
    public XYSeries getNormalizedKratkyReciRgData(){
        return normalizedKratkyReciprocalSpaceRgData;
    }

    public void createNormalizedKratkyReciRgData(){

        normalizedKratkyReciprocalSpaceRgData.clear();

        XYDataItem temp;
        // if rg or izero are not set or less than zero, need to compensate without throwing exception
        double rg = (guinierObject.izero > 0) ? guinierObject.rg : 1;
        double iz = (guinierObject.izero > 0) ? guinierObject.izero : 1;

        double rg2 = rg*rg/iz;

        int startHere = startAt - 1;

        for (int i = startHere; i < endAt; i++){
            temp = kratkyData.getDataItem(i);
            normalizedKratkyReciprocalSpaceRgData.add(temp.getXValue()*rg, temp.getYValue()*rg2);
        }
    }

    /**
     * Reciprocal Space Dimensionless Kratky Plot
     * @return XYSeries for normalized Kratky plot
     */
    public XYSeries getNormalizedKratkyRealSpaceRgData(){
        return normalizedKratkyRealSpaceRgData;
    }

    public void createNormalizedRealSpaceKratky(){

        normalizedKratkyRealSpaceRgData.clear();

        XYDataItem temp;
        // if rg or izero are not set or less than zero, need to compensate without throwing exception
        double rg = (guinierObject.izero > 0) ? guinierObject.rg : 1;
        double iz = (guinierObject.izero > 0) ? guinierObject.izero : 1;

        double rg2 = rg*rg/iz;

        int startHere = startAt - 1;

        for (int i = startHere; i < endAt; i++){
            temp = kratkyData.getDataItem(i);
            normalizedKratkyRealSpaceRgData.add(temp.getXValue()*rg, temp.getYValue()*rg2);
        }
    }

    public XYSeries getPlottedPowerLaw(){
        return plottedPowerLaw;
    }


    /**
     * rescales the data using natural logarithm
     */
    public void scalePlottedPowerLaw(){
        plottedPowerLaw.clear();
        XYDataItem temp;

        int endHere = powerLawData.indexOf(Math.log(plottedData.getX(plottedData.getItemCount()-1).doubleValue()));
        int startHere = powerLawData.indexOf(Math.log(plottedData.getX(0).doubleValue()));

        if (scaleFactor != 1){
            for (int i = startHere; i < endHere; i++){
                temp = powerLawData.getDataItem(i);
                plottedPowerLaw.add(temp.getX(), temp.getYValue() + Math.log(scaleFactor));
            }
        } else {

            for (int i = startHere; i < endHere; i++){
                plottedPowerLaw.add(powerLawData.getDataItem(i));
            }
        }
    }

    public void rescalePlottedPowerLaw(){
        int startHere = powerLawData.indexOf(Math.log(plottedData.getX(0).doubleValue()));
        int totalPlotted = plottedPowerLaw.getItemCount();

        if (scaleFactor != 1){
            double dSF = Math.log(scaleFactor);
            for (int i = 0; i < totalPlotted; i++){
                plottedPowerLaw.updateByIndex(i, powerLawData.getDataItem(startHere + i).getYValue() + dSF);
            }
        } else {
            for (int i = 0; i < totalPlotted; i++){
                plottedPowerLaw.updateByIndex(i, powerLawData.getDataItem(startHere + i).getY());
            }
        }
    }



    /**
     * scales data if visible
     * will show negative values up to the plotted q-max, if negative values are past, won't know
     */
    public void scalePlottedQIQData(){
        plottedqIqData.clear();
        XYDataItem temp;

        int endHere = qIqData.indexOf(plottedData.getX(plottedData.getItemCount()-1));
        int startHere = qIqData.indexOf(plottedData.getX(0));

        if (scaleFactor != 1){
            for (int i = startHere; i<endHere; i++){
                temp = qIqData.getDataItem(i);
                plottedqIqData.add(temp.getX(), temp.getYValue()*scaleFactor);
            }
        } else {
            for (int i = startHere; i<endHere; i++){
                plottedqIqData.add(qIqData.getDataItem(i));
            }
        }
    }


    /**
     * scales data if visible
     * will show negative values up to the plotted q-max, if negative values are past, won't know
     */
    public void reScalePlottedQIQData(){

        int startHere = qIqData.indexOf(plottedData.getX(0));
        int endHere = qIqData.indexOf(plottedData.getX(plottedData.getItemCount()-1));
        int totalPlotted = plottedqIqData.getItemCount();

        System.out.println("qIqData :: " + qIqData.getItemCount() +  " total plotted " + totalPlotted  + " starthere " + startHere );

        if (scaleFactor != 1){
            for (int i = 0; i<totalPlotted; i++){
                plottedqIqData.updateByIndex(i, qIqData.getDataItem(startHere + i).getYValue()*scaleFactor);
            }
        } else {
            for (int i = 0; i<totalPlotted; i++){
                plottedqIqData.updateByIndex(i, qIqData.getDataItem(startHere + i).getY());
            }
        }
    }

    /**
     * nothing prevents oldstart from being set to zero, needs to be caught
     * @param oldstart
     * @param newstart
     */
    public void reStartPlottedQIQ(int oldstart, int newstart){
        if (newstart > oldstart){ //remove
            for(; oldstart<newstart; oldstart++){
                plottedqIqData.remove(0);
            }
        } else if (newstart < oldstart){ // add
            int countdown = oldstart - 1 - 1;
            int stopDown = newstart - 1;
            XYDataItem item;
            for (int i = countdown; i>=stopDown; i--){
                item = qIqData.getDataItem(i);
                plottedqIqData.add(item.getX(), item.getYValue()*scaleFactor);
            }
        }
    }

    /**
     * nothing prevents oldstart from being set to zero, needs to be caught
     * @param oldEnd
     * @param newEnd
     */
    public void reEndPlottedQIQ(int oldEnd, int newEnd){
        plottedqIqData.setNotify(false);
        int lastIndex = plottedqIqData.getItemCount()-1;

        if (newEnd > oldEnd){ // add
            int remappedIndex = qIqData.indexOf(plottedqIqData.getX(lastIndex));
            XYDataItem item;
            if (newEnd >= totalCountInPositiveData){
                newEnd = totalCountInPositiveData-1;
            }

            int stopIndex = qIqData.indexOf(originalLog10Data.getX(newEnd)) + 1;

            for (int i=remappedIndex; i<stopIndex; i++){
                item = qIqData.getDataItem(i);
                plottedqIqData.add(item.getX(), item.getYValue()*scaleFactor);
            }

        } else if (newEnd < oldEnd){ // remove
            double stopq = originalLog10Data.getX(newEnd-1).doubleValue();

           // int remappedIndex = plottedqIqData.indexOf(originalLog10Data.getX(newEnd-1));
           // plottedqIqData.delete(remappedIndex, lastIndex);
            double currentq = plottedqIqData.getX(lastIndex).doubleValue();

            while( currentq > stopq ){
                plottedqIqData.remove(lastIndex);
                lastIndex--;
                currentq = plottedqIqData.getX(lastIndex).doubleValue();
            }
        }
        plottedqIqData.setNotify(true);
    }


    /**
     * Returns unmodified XYSeries as q^2, ln[I(q)]
     * @return XYSeries
     */
    public XYSeries getGuinierData(){
        return guinierData;
    }

    /**
     * Returns original XYSeries error (q,sigma) of positive only intensity data
     * @return Unmodified XYSeries with sigma vs q
     */
    public XYSeries getOriginalPositiveOnlyError(){
        return originalPositiveOnlyError;
    }

    public XYSeries getPlottedQIQDataSeries(){
        return plottedqIqData;
    }

    public YIntervalSeries getAllDataYError(){ return allDataYError;}

    public XYSeries getOriginalPositiveOnlyData(){
        return originalPositiveOnlyData;
    }
    public XYDataItem getOriginalPositiveOnlyDataItem(int index){ return originalPositiveOnlyData.getDataItem(index);}

    public void setPorodVolumeQmax(double porodVolumeQmax) {
        this.porodVolumeQmax = porodVolumeQmax;
    }
    public double getPorodVolumeQmax() {
        return porodVolumeQmax;
    }

    public void setVC(double vC){
        vc=vC;
    }

    public void setVCSigma(double vcS){
        vcSigma=vcS;
    }

    public void setVCReal(double vcr){
        vcReal=vcr;
    }


    /**
     * Sets Porod Exponent
     *
     */
    public void setPorodExponent(double porodExp){
        porodExponent=porodExp;
    }
    /**
     * Sets Porod Exponent
     *
     */
    public void setPorodExponentError(double porodExpError){
        porodExponentError=porodExpError;
    }

    public void setInvariantQ(double invariant){
        invariantQ=invariant;
    }

    public void setSasObject(String jsonString){
        sasObject = new SasObject(jsonString);
    }

    public SasObject getSasObject(){ return sasObject;}

    public void initializeSasObject(){
        sasObject = new SasObject();
    }

    public XYSeries getQIQData(){ return qIqData;}
    public XYDataItem getQIQDataItem(int index){ return qIqData.getDataItem(index);}
    public XYDataItem getKratkyItem(int index){
        return kratkyData.getDataItem(index);
    }


    public void setPlottedDataNotify(boolean value){
        plottedData.setNotify(value);
    }

    public void lowBoundPlottedLog10IntensityData(int newStart){

        if (newStart < startAt){ // addValues
            int startHere = this.startAt - 1; // current location in originalLog10Data
            int limit = startAt-newStart;


            for(int i=0; i<limit && startHere > -1; i++){
                startHere--;
                XYDataItem temp = originalLog10Data.getDataItem(startHere);
                plottedData.addOrUpdate(temp.getX(), temp.getYValue() + log10ScaleFactor);
            }

        } else if (newStart > startAt){ //remove values

            int limit = newStart - this.startAt;
            for(int i=0; i < limit; i++){
                plottedData.remove(0);
            }
            //plottedData.delete(0, (limit-1));
        }
//            plottedData.setNotify(true);
        this.startAt = newStart;
    }

    public void upperBoundPlottedLog10IntensityData(int newEnd){

        if (newEnd > endAt){ // addValues
            int startHere = this.endAt; // upper bound in originalLog10 exclusive
            int limit = newEnd - this.endAt;
            int upper = originalLog10Data.getItemCount();

            for(int i=0; i<limit && startHere < upper; i++){
                XYDataItem temp = originalLog10Data.getDataItem(startHere);
                plottedData.addOrUpdate(temp.getX(), temp.getYValue() + log10ScaleFactor);
                startHere++;
            }

        } else if (newEnd < endAt){ //remove values

            int limit = this.endAt - newEnd;
            int lastValue = plottedData.getItemCount();

            for(int i=0; i<limit; i++){ // remove the last point
                lastValue--;
                plottedData.remove(lastValue);
            }
        }
        this.endAt = newEnd;
    }



    public void setExperimentalNotes(String text){
        this.experimentalNotes = text;
    }

    public void setNoteTitle(String text){
        this.experimentalNoteTitle = text;
    }

    public void setAverageInfo(Collection collection){
        int totalCollection = collection.getTotalDatasets();
        this.experimentalNotes += "AVERAGED FROM\n";
        int count=0;
        for (int i=0; i < totalCollection; i++){
            if (collection.getDataset(i).getInUse()){
                count++;
                this.experimentalNotes += String.format("\tFILE %4d : %s%n", count, collection.getDataset(i).getFileName());
            }
        }
    }

    // add a property change listener
    public synchronized void addPropertyChangeListener(PropertyChangeListener l) {
        // add a listener if it is not already registered
        if (!propChangeListeners.contains(l)) {
            propChangeListeners.addElement(l);
        }

    }

    // remove a property change listener
    public synchronized void removePropertyChangeListener(PropertyChangeListener l) {
        // remove it if it is registered
        if (propChangeListeners.contains(l)) {
            propChangeListeners.removeElement(l);
        }
    }

    // notify listening objects of property changes
    protected void notifyScaleChange() {
        // create the event object
        PropertyChangeEvent evt = new PropertyChangeEvent(this, "scalefactor", 1.0d, scaleFactor);
        // make a copy of the listener object vector so that it cannot
        // be changed while we are firing events
        Vector v;
        synchronized(this) {
            v = (Vector) propChangeListeners.clone();
        }

        // fire the event to all listeners
        int cnt = v.size();
        for (int i = 0; i < cnt; i++) {
            PropertyChangeListener client = (PropertyChangeListener)v.elementAt(i);
            client.propertyChange(evt);
        }
    }

    protected void notifyEndIndexChanged(int newvalue){
        // create the event object
        PropertyChangeEvent evt = new PropertyChangeEvent(this, "endIndex", endAt, newvalue);
        // make a copy of the listener object vector so that it cannot
        // be changed while we are firing events
        Vector v;
        synchronized(this) {
            v = (Vector) propChangeListeners.clone();
        }

        // fire the event to all listeners
        int cnt = v.size();
        for (int i = 0; i < cnt; i++) {
            PropertyChangeListener client = (PropertyChangeListener)v.elementAt(i);
            client.propertyChange(evt);
        }
    }


    protected void notifyStartIndexChanged(int newvalue){
        // create the event object
        PropertyChangeEvent evt = new PropertyChangeEvent(this, "startIndex", startAt, newvalue);
        // make a copy of the listener object vector so that it cannot
        // be changed while we are firing events
        Vector v;
        synchronized(this) {
            v = (Vector) propChangeListeners.clone();
        }

        // fire the event to all listeners
        int cnt = v.size();
        for (int i = 0; i < cnt; i++) {
            PropertyChangeListener client = (PropertyChangeListener)v.elementAt(i);
            client.propertyChange(evt);
        }
    }

    /**
     * nothing prevents oldstart from being set to zero, needs to be caught
     * @param oldstart
     * @param newstart
     */
    public void reStartPlottedPowerLaw(int oldstart, int newstart){
        if (newstart > oldstart){ //remove
            for(; oldstart<newstart; oldstart++){
                plottedPowerLaw.remove(0);
            }
        } else if (newstart < oldstart){ // add
            int countdown = oldstart - 1 - 1;
            int stopDown = newstart - 1;
            XYDataItem item;
            double dSF = Math.log(scaleFactor);

            for (int i = countdown; i>=stopDown; i--){
                item = powerLawData.getDataItem(i);
                plottedPowerLaw.add(item.getX(), item.getYValue() + dSF);
            }
        }
    }

    /**
     * nothing prevents oldstart from being set to zero, needs to be caught
     * @param oldEnd
     * @param newEnd
     */
    public void reEndPlottedPowerLaw(int oldEnd, int newEnd){
        plottedPowerLaw.setNotify(false);
        int lastIndex = plottedPowerLaw.getItemCount()-1;

        if (newEnd >= totalCountInPositiveData){
            newEnd = totalCountInPositiveData-1;
        }

        if (newEnd > oldEnd){ // add
            int remappedIndex = powerLawData.indexOf(plottedPowerLaw.getX(lastIndex));
            XYDataItem item;
            int stopIndex = powerLawData.indexOf( Math.log(originalPositiveOnlyData.getX(newEnd).doubleValue()) ) + 1;
            double dSF = Math.log(scaleFactor);

            for (int i=remappedIndex; i<stopIndex; i++){
                item = powerLawData.getDataItem(i);
                plottedPowerLaw.add(item.getX(), item.getYValue() + dSF);
            }

        } else if (newEnd < oldEnd){ // remove
            double stopq = Math.log(originalPositiveOnlyData.getX(newEnd-1).doubleValue());
            double currentq = plottedPowerLaw.getX(lastIndex).doubleValue();

            while( currentq > stopq ){
                plottedPowerLaw.remove(lastIndex);
                lastIndex--;
                currentq = plottedPowerLaw.getX(lastIndex).doubleValue();
            }
        }
        plottedPowerLaw.setNotify(true);
    }

    public void setrC(double rC) {
        this.rC = rC;
    }

    public void setrC_sigma(double rC_sigma) {
        this.rC_sigma = rC_sigma;
    }
}

