package version4;

import FileManager.FileObject;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import version4.ReportPDF.SubtractionReport;
import version4.Scaler.ScaleManager;
import version4.sasCIF.SasObject;

import javax.swing.*;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * Created by robertrambo on 18/01/2016.
 */
public class Subtraction extends SwingWorker<String, Integer> {

    private XYSeries medianBuffer;
    private XYSeries medianBufferError;
    private XYSeries averageBuffer;
    private XYSeries averageBufferError;

    private Collection samples;
    private Collection buffers;
    private Collection subtractedCollection;
    //private boolean subtractFromMedianBuffer = false;
    private boolean mergebyAverage = false;
    private boolean mergebyMedian = false;
    private boolean scaleBeforeMerging = false;
    private boolean autoRg = false;

    private JProgressBar bar;
    private JLabel status;
    private String name;
    private String cwd;
    private int cpus;
    private double cutoff, bins;
    private double sqmin, sqmax;
    private Collection returnCollection;
    private Collection returnSubtractedMergedCollection;
    private Collection collectionToUpdate;
    private String version="";

    public Subtraction(Collection buffers,
                       Collection samples,
                       double tqmin,
                       double tqmax,
                       boolean mergeByAverage,
                       boolean singles,
                       boolean scaleBefore,
                       int cpus,
                       boolean useAutoRg,
                       JLabel status,
                       final JProgressBar bar){
        this.samples = samples;
        this.buffers = buffers;

        if (buffers.getTotalDatasets() > 1){
            ArrayList<XYSeries> stuff = createMedianAverageXYSeries(buffers);
            try {
                medianBuffer = (XYSeries)stuff.get(0).clone();
                medianBufferError = (XYSeries)stuff.get(1).clone();
                averageBuffer = (XYSeries)stuff.get(2).clone();
                averageBufferError = (XYSeries)stuff.get(3).clone();

            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        } else { // reference to buffers data
            medianBuffer = buffers.getDataset(0).getAllData();
            medianBufferError = buffers.getDataset(0).getAllDataError();
            averageBuffer = buffers.getDataset(0).getAllData();
            averageBufferError = buffers.getDataset(0).getAllDataError();
        }

        this.status = status;
        this.bar = bar;
        this.autoRg = useAutoRg;

        if (!singles){
            this.mergebyAverage = mergeByAverage;
            this.mergebyMedian = !mergeByAverage;
        } else {
            this.mergebyAverage = false;
            this.mergebyMedian = false;
        }


        status.setText("Merging => average " + mergebyAverage + " median: " + mergebyMedian);

        this.scaleBeforeMerging = scaleBefore;
        this.cpus = cpus;

        // used for scaling
        this.sqmin = tqmin;
        this.sqmax = tqmax;
        this.version = version;
    }

    @Override
    protected String doInBackground() {

        returnCollection = new Collection("return");
        returnSubtractedMergedCollection = new Collection("return merged");

        bar.setStringPainted(true);
        bar.setIndeterminate(true);
        bar.setValue(0);

        // create subtracted set of files from average buffer
        subtractedCollection = new Collection("Subtracted");
        int total = samples.getTotalDatasets();
        bar.setMaximum(total);

        status.setText("Subtracting " + total + " files");

        /*
         * subtract each sample from the averaged buffer
         */
        for (int i = 0; i < total; i++) { // make the subtracted datasets using a fixed, averaged buffer

            if (samples.getDataset(i).getInUse()) {

                ArrayList<XYSeries> subtraction = subtract(samples.getDataset(i).getAllData(), samples.getDataset(i).getAllDataError(), averageBuffer, averageBufferError);
                //int newIndex = subtractedCollection.getDatasetCount();

                subtractedCollection.addDataset( new Dataset(
                        subtraction.get(0),  //data
                        subtraction.get(1),  //error
                        samples.getDataset(i).getFileName()+"_sub",
                        subtractedCollection.getTotalDatasets()));

                subtractedCollection.getLast().setId(samples.getDataset(i).getId());
            }
            publish(i);
        }

        publish(0);
        //bar.setIndeterminate(true);
        /*
         * after doing the subtraction, need to do the actual averaging
         */
        if (mergebyMedian){
            status.setText("MERGING BY MEDIAN");
            // merge subtracted set by taking median of the subtracted sets (subtracted set was made using median buffer)
            Dataset medianSetFromAveragedbuffer = medianSubtractedSets(subtractedCollection, "medianSample_averageBuffer");

            medianSetFromAveragedbuffer.initializeSasObject();
            medianSetFromAveragedbuffer.getSasObject().getSasSample().setDetails("Subtracted : med(Sample) - ave(buffer)");

            returnCollection.addDataset(medianSetFromAveragedbuffer);
            returnSubtractedMergedCollection.addDataset(medianSetFromAveragedbuffer);

            // subtract from average and (median buffer if count > 2)
            if (buffers.getTotalDatasets() > 2){ // do subtraction against median buffer

                status.setText("Creating median buffer set");
                subtractedCollection.removeAllDatasets();

                for (int i = 0; i < total; i++) {

                    if (samples.getDataset(i).getInUse() ) {

                        ArrayList<XYSeries> subtraction = subtract(samples.getDataset(i).getAllData(), samples.getDataset(i).getAllDataError(), medianBuffer, medianBufferError);
                        int newIndex = subtractedCollection.getTotalDatasets();

                        subtractedCollection.addDataset( new Dataset(
                                subtraction.get(0),       //data
                                subtraction.get(1),  //original
                                samples.getDataset(i).getFileName(),
                                newIndex));

                        subtractedCollection.getLast().setId(samples.getDataset(i).getId());

                    }
                    publish(i);
                }

                // create median subtracted median
                Dataset medianSetFromMedianbuffer = averagedSubtractedSets(subtractedCollection, "medianSample_medianBuffer");

                medianSetFromMedianbuffer.initializeSasObject();
                medianSetFromMedianbuffer.getSasObject().getSasSample().setDetails("Subtracted : med(Sample) - med(buffer)");
                medianSetFromMedianbuffer.setId(returnCollection.getTotalDatasets());

                returnCollection.addDataset(medianSetFromMedianbuffer);
                returnSubtractedMergedCollection.addDataset(medianSetFromMedianbuffer);
            }

        } else if (mergebyAverage) { // average the subtractedCollection (which was made from averaged buffer)

            Dataset averagedSetFromAveragedbuffer = averagedSubtractedSets(subtractedCollection, "average_average");
            averagedSetFromAveragedbuffer.initializeSasObject();
            averagedSetFromAveragedbuffer.getSasObject().getSasSample().setDetails("Subtracted : ave(Sample) - ave(buffer)");

            returnCollection.addDataset(averagedSetFromAveragedbuffer);
            returnSubtractedMergedCollection.addDataset(averagedSetFromAveragedbuffer);
            // subtract from average and (median buffer if count > 2)
            if (buffers.getTotalDatasets() > 2){ // do median buffer

                subtractedCollection.removeAllDatasets();

                for (int i = 0; i < total; i++) {

                    if (samples.getDataset(i).getInUse()) {


                        ArrayList<XYSeries> subtraction = subtract(samples.getDataset(i).getAllData(), samples.getDataset(i).getAllDataError(), medianBuffer, medianBufferError);
                        int newIndex = subtractedCollection.getTotalDatasets();

                        subtractedCollection.addDataset( new Dataset(
                                subtraction.get(0),       //data
                                subtraction.get(1),  //original
                                samples.getDataset(i).getFileName(),
                                newIndex ));

                        subtractedCollection.getLast().setId(samples.getDataset(i).getId());
//                        if (i == refId){
//                            scaleToID = subtractedCollection.getDatasetCount()-1; //sets refID relative to the new subtracted collection dataset that will get scaled
//                        }
                    }
                    publish(i);
                }

                // do average - mean set
                Dataset averagedSetFromMedianbuffer = averagedSubtractedSets(subtractedCollection, "average_median");

                // calculate the merge parameters
                // min and max and average
                averagedSetFromMedianbuffer.initializeSasObject();
                averagedSetFromMedianbuffer.getSasObject().getSasSample().setDetails("Subtracted : ave(Sample) - med(buffer)");

                averagedSetFromMedianbuffer.setId(returnCollection.getTotalDatasets());
                returnCollection.addDataset(averagedSetFromMedianbuffer);
                returnSubtractedMergedCollection.addDataset(averagedSetFromMedianbuffer);
            } // end median buffer subtraction

        } else { // subtract with no merging or scaling
            // only do average buffer?
            // subtractFromMedianSample();
            // subtractFromAveragedBuffer();
            int totalSubtracted = subtractedCollection.getTotalDatasets();

            for (int i=0; i<totalSubtracted; i++){
                Dataset datatemp = subtractedCollection.getDataset(i);
                datatemp.initializeSasObject();
                datatemp.getSasObject().getSasSample().setDetails("Subtracted : ave(Sample) - med(buffer)");
            }

            writeSinglesToFile(subtractedCollection);
            returnCollection = subtractedCollection;
        }
//        System.out.println("Subtracted Collection " + subtractedCollection.getTotalDatasets() );
        SubtractionReport tempReport = new SubtractionReport(subtractedCollection, cwd);
        tempReport.setSubtractedPlot();
        tempReport.writeReport(name);

        this.updateCollection();
        return null;
    }

    public void setCollectionToUpdate(Collection collection){
        collectionToUpdate = collection;
    }


    public Collection getReturnCollection(){
        return this.subtractedCollection;
    }


    public Collection getSubtractedMergedCollection(){
        return this.returnSubtractedMergedCollection;
    }

    /**
     * this appends the specified collection with the newly subtracted files
     */
    private void updateCollection(){

        int total = returnCollection.getTotalDatasets();
        int newIndex = collectionToUpdate.getTotalDatasets();
        for (int i=0; i<total; i++){

            Dataset temp = returnCollection.getDataset(i);

            collectionToUpdate.addDataset( new Dataset(
                    temp.getAllData(),       //data
                    temp.getAllDataError(),  //original
                    temp.getFileName(),
                    newIndex, autoRg ));

            newIndex++;
        }
    }


    /**
     * returns chart for embedding into PDF
     * @param doGuinier
     * @param title
     * @return
     */
    public JFreeChart makeLog10Chart(boolean doGuinier, String title){

        int totalSets = returnCollection.getTotalDatasets();
        //plottedDatasets = new XYSeriesCollection();  // spinners will always modify the plottedDataset series
        XYSeriesCollection plottedDatasets = new XYSeriesCollection();
        double qlower=10, qupper=-1, ilower = 10, iupper = -1;

        for (int i=0; i < totalSets; i++){
            if (returnCollection.getDataset(i).getInUse()){
                plottedDatasets.addSeries(returnCollection.getDataset(i).getData()); // positive only data

                Dataset tempData = returnCollection.getDataset(i);

                if (tempData.getAllData().getMinX() < qlower){
                    qlower = tempData.getAllData().getMinX();
                }

                if (tempData.getAllData().getMaxX() > qupper){
                    qupper = tempData.getAllData().getMaxX();
                }

                if (tempData.getData().getMinY() < ilower){
                    ilower = tempData.getData().getMinY();
                }

                if (tempData.getData().getMaxY() > iupper){
                    iupper = tempData.getData().getMaxY();
                }
            }
        }


        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "q",                             // domain axis label
                "log[I(q)]",                     // range axis label
                plottedDatasets,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        chart.getXYPlot().setDomainCrosshairVisible(false);
        chart.getXYPlot().setRangeCrosshairVisible(false);

        chart.setBorderVisible(false);
        chart.setTitle(title);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);

        //set dot size for all series
        double offset;
        int counted = 0;

        for (int i=0; i < totalSets; i++){

            if (returnCollection.getDataset(i).getInUse()){
                Dataset tempData = returnCollection.getDataset(i);

                offset = -0.5*tempData.getPointSize();
                renderer1.setSeriesShape(counted, new Ellipse2D.Double(offset, offset, tempData.getPointSize(), tempData.getPointSize()));
                renderer1.setSeriesLinesVisible(counted, false);
                renderer1.setSeriesPaint(counted, tempData.getColor());
                renderer1.setSeriesShapesFilled(counted, tempData.getBaseShapeFilled());
                renderer1.setSeriesVisible(counted, tempData.getInUse());
                renderer1.setSeriesOutlineStroke(counted, tempData.getStroke());
                counted++;
            }
        }

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);
        return chart;
    }

    public void setNameAndDirectory(String baseName, String workingDirectoryName){

        this.name = baseName;
        this.cwd = workingDirectoryName;
    }

    /**
     * No Scaling required use the datasets as is
     * 1. Create average and median representation of sample Collection
     * 2. Subtract from average and median representation of buffer
     * @return
     */
    private Collection subtractFromMedianSample(){

        ArrayList<XYSeries> sampleSeries = createMedianAverageXYSeries(samples);
        XYSeries sampleXY = sampleSeries.get(0);    //median
        XYSeries sampleError = sampleSeries.get(1); //median error

        Collection subtractedCollection = new Collection("subtracted collection");
        // do average buffer first
        ArrayList<XYSeries> subtraction = subtract(sampleXY, sampleError, averageBuffer, averageBufferError);

        //double[] izeroRg = Functions.calculateIzeroRg(subtraction.get(0), subtraction.get(1));
        subtractedCollection.addDataset( new Dataset(
                subtraction.get(0),       //data
                subtraction.get(1),
                name+"_from_averaged",
                0 ));

        subtractedCollection.getLast().initializeSasObject();
        subtractedCollection.getLast().getSasObject().getSasSample().setDetails("Subtracted from an averaged buffer set");

        FileObject dataToWrite = new FileObject(new File(cwd), version);
        dataToWrite.writeSAXSFile("med_"+name+"_from_averaged_buffer", subtractedCollection.getDataset(0));

        // subtract from averaged samples
        subtraction = subtract(sampleXY, sampleError, medianBuffer, medianBufferError);
        subtractedCollection.addDataset( new Dataset(
                subtraction.get(0),       //data
                subtraction.get(1),       //error
                name+"_from_median",
                1 ));

        subtractedCollection.getLast().initializeSasObject();
        subtractedCollection.getLast().getSasObject().getSasSample().setDetails("Subtracted from the median buffer set");

        dataToWrite.writeSAXSFile("med_"+name+"_from_median_buffer", subtractedCollection.getDataset(1));

        return subtractedCollection;
    }

    private Collection subtractFromAveragedBuffer(){

        ArrayList<XYSeries> sampleSeries = createMedianAverageXYSeries(samples);
        XYSeries sampleXY = sampleSeries.get(2);    //averaged
        XYSeries sampleError = sampleSeries.get(3); //averaged error

        Collection subtractedCollection = new Collection("Subtracted Collection");
        // do average buffer first
        ArrayList<XYSeries> subtraction = subtract(sampleXY, sampleError, averageBuffer, averageBufferError);

        //double[] izeroRg = Functions.calculateIzeroRg(subtraction.get(0), subtraction.get(1));
        subtractedCollection.addDataset( new Dataset(
                subtraction.get(0),       //data
                subtraction.get(1),
                name+"_from_averaged",
                0 ));

        subtractedCollection.getLast().initializeSasObject();
        subtractedCollection.getLast().getSasObject().getSasSample().setDetails("Subtracted from an averaged buffer set");

        FileObject dataToWrite = new FileObject(new File(cwd), version);
        dataToWrite.writeSAXSFile("ave_" + name+"_from_averaged_buffer", subtractedCollection.getDataset(0));

        // subtract from averaged samples
        subtraction = subtract(sampleXY, sampleError, medianBuffer, medianBufferError);
        subtractedCollection.addDataset( new Dataset(
                subtraction.get(0),       //data
                subtraction.get(1),       //error
                name+"_from_median",
                1, false ));

        subtractedCollection.getLast().initializeSasObject();
        subtractedCollection.getLast().getSasObject().getSasSample().setDetails("Subtracted from the median buffer set");

        dataToWrite.writeSAXSFile("ave_" + name+"_from_median_buffer", subtractedCollection.getDataset(1));

        return subtractedCollection;
    }

//    /**
//     * refID from the JComboBox
//     * @param refID
//     */
//    public void setRefID(int refID){ this.refId = refID;}

    public void setBinsAndCutoff(double bins, double cutoff){
        this.cutoff = cutoff;
        this.bins = bins;
    }


    public Dataset medianSubtractedSets(Collection subtractedCollection, String type){

        if (scaleBeforeMerging) {
            status.setText("scaling sets");

            ScaleManager scalings = new ScaleManager(
                    cpus,
                    subtractedCollection,
                    bar,
                    status);

            scalings.setUpperLowerQLimits(sqmin, sqmax);
            scalings.execute();

            try {
                scalings.get();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } catch (ExecutionException e1) {
                e1.printStackTrace();
            }
        }

        status.setText("Merging sets");
        String output_name;

        // createMedian
        ArrayList<XYSeries> sampleSeries = createMedianAverageXYSeries(subtractedCollection);
        //createMedianAverageXYSeries(samples);
        XYSeries sampleXY = sampleSeries.get(0);    //median
        XYSeries sampleError = sampleSeries.get(1); //median error

        sampleXY.setKey(type);
        sampleError.setKey(type);
        // merge curves using rejection criteria
        output_name = name + "_" + type;

        Dataset tempSingle = new Dataset(
                sampleXY,       //data
                sampleError,  //original
                output_name,
                0, false );

        FileObject dataToWrite = new FileObject(new File(cwd), version);
        dataToWrite.writeSAXSFile(output_name, tempSingle);

        return tempSingle;
    }


    /**
     * Take the subtracted set of curves, scale them (if requested) and then average with rejection
     * @param subtractedCollection
     * @param type
     * @return
     */
    public Dataset averagedSubtractedSets(Collection subtractedCollection, String type){

        if (scaleBeforeMerging) {
            status.setText("scaling sets");

            ScaleManager scalings = new ScaleManager(
                    cpus,
                    subtractedCollection,
                    bar,
                    status);

            scalings.setUpperLowerQLimits(sqmin, sqmax);
            scalings.execute();

            try {
                scalings.get();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } catch (ExecutionException e1) {
                e1.printStackTrace();
            }
        }

        status.setText("Merging by Averaging");

        String temp_name = Functions.sanitizeForFilename(name);

        ArrayList<XYSeries> finalSets;
        String output_name;
        finalSets = createRejectionDataSets(subtractedCollection);
        output_name = temp_name + "_" + type;

        // merge curves using rejection criteria
        // finalSets = createRejectionDataSets(sampleFilesModel, subtractedCollection);
        // write out merged curve

        Dataset tempSingle = new Dataset(
                finalSets.get(0),  //data
                finalSets.get(1),  //original
                output_name,
                0, false );

        FileObject dataToWrite = new FileObject(new File(cwd), version);
        dataToWrite.writeSAXSFile(output_name, tempSingle);

        /*
        tempCollection.addDataset(tempSingle);
        tempCollection.recalculateMinMaxQ();
        tempCollection.getDataset(0).setMaxq(finalSets.get(0).getMaxX());
        tempCollection.getDataset(0).setMinq(finalSets.get(0).getMinX());
        */
        return tempSingle;
    }

    private void writeSinglesToFile(Collection subtractedCollection){
        int total = subtractedCollection.getTotalDatasets();

        status.setText("writing each dataset to file");
        FileObject dataToWrite = new FileObject(new File(cwd), version);

        for (int i = 0; i < total; i++) {
            dataToWrite.writeSAXSFile(subtractedCollection.getDataset(i).getFileName(), subtractedCollection.getDataset(i));
        }
    }


    private ArrayList<XYSeries> subtract(XYSeries sample, XYSeries sampleError, XYSeries buffer, XYSeries bufferError){
        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        // for each element in sample collection, do subtraction

        XYSeries subData;
        XYSeries subError;
        XYDataItem tempDataItem;

        int tempTotal, indexOf;
        double qValue, yValue, eValue;

        tempTotal = sample.getItemCount();

        returnMe.add(new XYSeries("subtracted"));
        returnMe.add(new XYSeries("errorSubtracted"));
        subData = returnMe.get(0);//new XYSeries("subtracted");
        subError = returnMe.get(1);//new XYSeries("errorSubtracted");
        //Subtract and add to new data

        double maxQValueInBuffer = buffer.getMaxX();

        QLOOP:
        for(int q=0; q<tempTotal; q++){
            tempDataItem = sample.getDataItem(q);
            qValue = tempDataItem.getXValue();
            /*
             * check to see if in buffer
             */
            indexOf = buffer.indexOf(qValue);
            yValue = sampleError.getY(q).doubleValue();

            if (indexOf > -1){
                subData.add(qValue, tempDataItem.getYValue() - buffer.getY(indexOf).doubleValue() );

                eValue = bufferError.getY(indexOf).doubleValue();
                subError.add(qValue, Math.sqrt(yValue*yValue + eValue*eValue));

            } else { // interpolate
                // interpolation requires at least two values on left or right side of value of interest
                // if not, skip value
//                    count = 0;
//                    referenceQ = buffer.getX(count).doubleValue();
//                    find first value in reference greater than targetData.getX
//                    while (referenceQ < qValue) {
//                        count++;
//                        referenceQ = buffer.getX(count).doubleValue();
//                    }
//
//                    if (count < 2) {
//                       break QLOOP;
//                    }

                if (qValue < maxQValueInBuffer){
                    System.out.println("Interpolating Background Value at " + qValue + " >= " + maxQValueInBuffer);
                    Double[] results = Functions.interpolate(buffer, qValue);
                    Double[] sigmaResults = Functions.interpolateSigma(bufferError, qValue);
                    //returns unlogged data
                    eValue = sigmaResults[1];

                    subData.add(qValue, results[1]);
                    subError.add(qValue, Math.sqrt(yValue*yValue + eValue*eValue));
                }
            }
        }

//        returnMe.add(subData);
//        returnMe.add(subError);

        return returnMe;
    }


    private ArrayList<XYSeries> createMedianAverageXYSeries(Collection collection){
        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();

        returnMe.add(new XYSeries("medianAllData"));
        returnMe.add(new XYSeries("medianAllDataError"));
        returnMe.add(new XYSeries("average"));
        returnMe.add(new XYSeries("averageError"));
        // calculate Average and Median for set

        ArrayList<XYSeries> median_reduced_set = medianDatasets(collection);
        ArrayList<XYSeries> averaged = weightedAverageDatasets(collection);

//        String name = "median_set";
//        XYSeries medianAllData = returnMe.get(0);
//        XYSeries medianAllDataError = returnMe.get(1);
//
//        try {
//            medianAllData = (XYSeries) median_reduced_set.get(0).clone();
//            medianAllData.setKey(name);
//            medianAllDataError = (XYSeries) median_reduced_set.get(1).clone();
//            medianAllDataError.setKey(name);
//        } catch (CloneNotSupportedException e) {
//            e.printStackTrace();
//        }

//        returnMe.add(medianAllData);          // 0
//        returnMe.add(medianAllDataError);     // 1
        int total = averaged.get(0).getItemCount();
        XYSeries avg0 = returnMe.get(2);
        XYSeries avg1 = returnMe.get(3);

        for(int i=0; i<total; i++){
            avg0.add(averaged.get(0).getDataItem(i));
            avg1.add(averaged.get(1).getDataItem(i));
        }
//        returnMe.add(averaged.get(0));        // 2
//        returnMe.add(averaged.get(1));        // 3

        total = median_reduced_set.get(0).getItemCount();
        XYSeries med0 = returnMe.get(0);
        XYSeries med1 = returnMe.get(1);
        for(int i=0; i<total; i++){
            med0.add(median_reduced_set.get(0).getDataItem(i));
            med1.add(median_reduced_set.get(1).getDataItem(i));
        }

        return returnMe;
    }


    private ArrayList<XYSeries> createRejectionDataSets(Collection thisCollection){

        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        //int total = model.getSize();
        int total = thisCollection.getDatasets().size();
        int sizeOf = 0;

        double binPercent = bins/100.00;

        int totalObs;
        Dataset tempData;
        double xValue, yValue, median, s_n, value;
        XYDataItem tempDataItem;

        ArrayList<StandardObservations> standardObs = new ArrayList<>();
        StandardObservations tempSobs;

        ArrayList<Double> vector = new ArrayList<Double>();

        int totalObservations = 0;
        for(int i=0;i<total; i++){
            tempData =thisCollection.getDataset(i);

            if (tempData.getInUse()){
                sizeOf = tempData.getAllData().getItemCount();
                if (totalObservations < sizeOf){
                    totalObservations = sizeOf;
                }
            }
        }

        int bbins = (int)(binPercent*sizeOf);

        double qmin = thisCollection.getMinq();
        double qmax = thisCollection.getMaxq();

        double lower, upper, sigma;
        double incr = (qmax-qmin)/(double)bbins;

        HashMap<Double, Double> finalData = new HashMap<Double, Double>();
        HashMap<Double, Double> finalError = new HashMap<Double, Double>();

        // iterate over each bin
        lower = qmin;

        for (int b = 1; b <= bbins; b++){

            upper = qmin + b*incr;

            standardObs.clear();
            vector.clear();

            dataSetLoop:
            for(int i=0;i<total; i++){
                tempData =thisCollection.getDataset(i);

                if (tempData.getInUse()){
                    sizeOf = tempData.getAllData().getItemCount();
                    double tempScaleFactor = tempData.getScaleFactor();

                    for(int j=0; j<sizeOf; j++){
                        tempDataItem = tempData.getAllData().getDataItem(j);
                        xValue = tempDataItem.getXValue();
                        if ((xValue >= lower) && (xValue < upper)){
                            sigma = tempData.getAllDataError().getY(j).doubleValue()*tempScaleFactor;
                            yValue = tempDataItem.getYValue()*tempScaleFactor;
                            standardObs.add(new StandardObservations(xValue, yValue, sigma));
                            vector.add(yValue);
                        } else if (xValue > qmax){
                            break dataSetLoop;
                        }
                    }

                }

            } // end of datasetloop
            // calculate median for standardObs
            totalObs = standardObs.size();
            median = Statistics.calculateMedian(vector, true);

            s_n = 1.0/(1.1926*Functions.s_n(vector));

            for (int i=0; i<totalObs; i++){
                standardObs.get(i).setStandardizedObs(s_n, median);
            }

            // reject all data with standardizedObs > 2.5
            Iterator iter = standardObs.iterator();
//            int count=0;
            while (iter.hasNext()){
                tempSobs = (StandardObservations) iter.next();
                if (tempSobs.getStandardizedObs() > cutoff) {
                    iter.remove();
//                    count++;
                }
            }
            //System.out.println(totalObs + " Count " + count);
            // collect and average all common q values
            iter = standardObs.iterator();
            double var, sig;

            while (iter.hasNext()){
                tempSobs = (StandardObservations) iter.next();
                xValue = tempSobs.getQ();
                sig = 1.0/tempSobs.getSigma();
                var = sig*sig;

                if (finalData.containsKey(xValue)){
                    value = (finalData.get(xValue) + tempSobs.getObs()*var );

                    finalData.put(xValue, value);
                    finalError.put(xValue, (finalError.get(xValue) + var));
                } else {

                    finalData.put(xValue, tempSobs.getObs()*var);
                    finalError.put(xValue, var);
                }
            }

            lower = upper;
        }

        returnMe.add(new XYSeries("Pruned"));
        returnMe.add(new XYSeries("PrunedError"));

        XYSeries finalSeries = returnMe.get(0);//new XYSeries("Pruned");
        XYSeries errorSeries = returnMe.get(1);//new XYSeries("PrunedError");

        Iterator it = finalData.entrySet().iterator();
        double weighted;
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            weighted = (Double)pairs.getValue()/finalError.get(pairs.getKey());

            finalSeries.add((Double)pairs.getKey(), (Double)weighted );
            it.remove(); // avoids a ConcurrentModificationException
        }

        it = finalError.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            weighted = 1.0 / Math.sqrt((Double)pairs.getValue());

            errorSeries.add((Double)pairs.getKey(), (Double)weighted);
            it.remove(); // avoids a ConcurrentModificationException
        }

        // return finalSeries and errorSeries
//        returnMe.add(finalSeries);
//        returnMe.add(errorSeries);

        return returnMe;
    }

    @Override
    protected void done() {
        try {
            super.get();
            bar.setValue(0);
            bar.setStringPainted(false);
            bar.setIndeterminate(false);
            status.setText("FINISHED SUBTRACTION");
            //can call other gui update code here
        } catch (Throwable t) {
            //do something with the exception
        }
    }


    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        bar.setValue(i);
        super.process(chunks);
    }


    private String escape(String text){
        String temp = text.replaceAll("/", " per ");
        //temp = temp.replaceAll(" ", "_");
        return temp.replace("_", "\\_");
    }


    private ArrayList<XYSeries> medianDatasets(Collection thisCollection){
        // Median Set
        // Determine all open files
        // Need the master list of all unique q values
        // create set of unique q values from the collection using TreeSet
        TreeSet<Double> qList;
        qList = new TreeSet<Double>();

        HashMap<Double, ArrayList<Double>> intValuesHash = new HashMap<Double, ArrayList<Double>>();
        HashMap<Double, ArrayList<Double>> errValuesHash = new HashMap<Double, ArrayList<Double>>();

        int lowerQI;
        int upperQI;

        double qValue;
        double scaleF;

        Dataset tempDataSet;

        int limit = thisCollection.getDatasets().size();
        for (int i=0; i < limit; i++){
            // only checked boxes
            if (thisCollection.getDataset(i).getInUse()){
//                tempDataSet = new Dataset(thisCollection.getDataset(i));
                tempDataSet = thisCollection.getDataset(i);

                try {
                    // XYSeries series7 = (XYSeries) tempDataSet.getData().clone(); // plotted datset
                    XYSeries series8 = (XYSeries) tempDataSet.getAllData().clone();
                    XYSeries series9 = (XYSeries) tempDataSet.getAllDataError().clone();
                    //add q-values to qList
                    lowerQI = series8.indexOf(tempDataSet.getMinq());
                    upperQI = series8.indexOf(tempDataSet.getMaxq());
                    scaleF = tempDataSet.getScaleFactor();

                    for (int j = lowerQI; j <= upperQI; j++){
                        // make entry in qList if not present
                        // if present
                        qValue = series8.getX(j).doubleValue();
                        if (qList.add(qValue)) {
                            //if true, add entry to ArrayList of Intensity Values that will be used for median
                            intValuesHash.put(qValue, new ArrayList<Double>( Arrays.asList(series8.getY(j).doubleValue() * scaleF) ));
                            errValuesHash.put(qValue, new ArrayList<Double>( Arrays.asList(series9.getY(j).doubleValue() * scaleF) ));
                        } else {
                            // if already present, find the index
                            intValuesHash.get(qValue).add(series8.getY(j).doubleValue()*scaleF);
                            errValuesHash.get(qValue).add(series9.getY(j).doubleValue()*scaleF);
                        }
                    }

                } catch (CloneNotSupportedException ex) {
                    LogIt.log(Level.SEVERE, "From StatMethods: MedianDatasets");
                }
            }
        }

        int qListSize;

        double median_value;
        double err_value;
        double err_valueU;
        double err_valueL;
        double lowerq;
        double upperq;
        int middlePoint;

        ArrayList<Double> tempArrayList;
        ArrayList<Double> sortList;

        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        returnMe.add(new XYSeries("median_set_from_collection"));
        returnMe.add(new XYSeries("median_error_set_from_collection"));
        XYSeries medianSet = returnMe.get(0);//new XYSeries("median_set_from_collection");
        XYSeries medianErrorSet = returnMe.get(1);//new XYSeries("median_error_set_from_collection");

        for (Double qvalue_key: intValuesHash.keySet()){

            tempArrayList = intValuesHash.get(qvalue_key);
            qListSize = tempArrayList.size();

            if (qListSize == 1 ){  //
                median_value = tempArrayList.get(0);
                err_value = errValuesHash.get(qvalue_key).get(0);

                //   qieSeries.add(new DataLine(tempQ, median_value, err_value, true));
            } else if (qListSize % 2 != 0) {  // odd
                middlePoint = (qListSize -1)/2;

                sortList = new ArrayList(tempArrayList); //makes a copy
                Collections.sort(sortList);
                median_value = sortList.get(middlePoint);
                err_value = errValuesHash.get(qvalue_key).get(tempArrayList.indexOf(median_value));

            } else { // even
                median_value = Statistics.calculateMedian(tempArrayList);
                sortList = new ArrayList(tempArrayList); //makes a copy
                Collections.sort(sortList);

                upperQI = tempArrayList.size()/2;
                lowerQI = upperQI-1;
                upperq = sortList.get(upperQI);
                lowerq = sortList.get(lowerQI);
                // look up values and reset to un-sorted array
                upperQI = tempArrayList.indexOf(upperq);
                lowerQI = tempArrayList.indexOf(lowerq);
                // get corresponding error values from datasets
                err_valueU = errValuesHash.get(qvalue_key).get(upperQI);
                err_valueL = errValuesHash.get(qvalue_key).get(lowerQI);
                err_value = 0.5*(err_valueL + err_valueU); // propagated error is an average
            }

            medianSet.add((double)qvalue_key, median_value);
            medianErrorSet.add((double)qvalue_key, err_value);

            tempArrayList = null;
        }
        // write out new Median DataSet
        return returnMe;
    }


    private ArrayList<XYSeries> weightedAverageDatasets(Collection selected){

        int ref = 0;
        int limit = selected.getDatasets().size();

        // use last as reference
        for(int i=0; i< limit; i++){
            if (selected.getDataset(i).getInUse()){
                ref = i;
            }
        }

        //System.out.println("REFERENCE SET TO : " + selected.getDataset(ref).getFileName());

        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();

        Dataset reference = selected.getDataset(ref); // reference is the last frame
        //XYSeries tempRefData;
        //tempRefData = reference.getData(); // log10 data used for plotting
        //double referenceQmin = tempRefData.getMinX();
        //double referenceQmax = tempRefData.getMaxX();

        Number referenceQmin = findLeastCommonQvalue(selected);
        Number referenceQmax = findMaximumCommonQvalue(selected);

        double scale, lower, upper, referenceQ;
        int refIndex, count;

        returnMe.add(new XYSeries("Summed"));
        returnMe.add(new XYSeries("SummedError"));

        XYSeries summedSet = returnMe.get(0);//new XYSeries("Summed");
        XYSeries summedSetError = returnMe.get(1);//new XYSeries("SummedError");

        double sigma, var, targetMax;
        Number targetMin;
        scale = reference.getScaleFactor();

        // based on the plotted data, find index of qmin and qmax in allData
        int lowerQI = reference.getAllData().indexOf(referenceQmin);
        int upperQI = reference.getAllData().indexOf(referenceQmax);
        int lowerT, upperT;
        /*
         * within limits of plotted data
         */
        for (int i=lowerQI; i < upperQI; i++){
            XYDataItem tempRefItem = reference.getAllData().getDataItem(i);
            sigma = 1.0/(scale*reference.getAllDataError().getY(i).doubleValue());
            var = sigma*sigma;

            summedSet.add(tempRefItem.getXValue(), scale * tempRefItem.getYValue()*var);
            summedSetError.add(tempRefItem.getXValue(), var);
        }

        XYDataItem tempDataItem, tempSummedItem;

        for (int i=0; i < limit; i++) {
            if (selected.getDataset(i).getInUse() && i != ref){

                Dataset tempDataset = selected.getDataset(i);

                XYSeries targetData  = tempDataset.getAllData();
                XYSeries targetError = tempDataset.getAllDataError();

                // get plotted domain of the target dataset
                // if averaging from SEC - plotted data will not be set - returns null
                try{
                    targetMin = tempDataset.getData().getX(0);
                    targetMax = tempDataset.getData().getMaxX();
                } catch (java.lang.NullPointerException exception){
                    targetMin = tempDataset.getAllData().getX(0);
                    targetMax = tempDataset.getAllData().getMaxX();
                }

                lowerT = targetData.indexOf(targetMin);
                upperT = targetData.indexOf(targetMax);

                lower = Math.max(referenceQmin.doubleValue(), targetMin.doubleValue());
                upper = Math.min(referenceQmax.doubleValue(), targetMax);

                scale = tempDataset.getScaleFactor();
                // iterate of the plotted range but within AllData (includes negative intensities)
                for(int j = lowerT; j <= upperT; j++){
                    tempDataItem = targetData.getDataItem(j);
                    double target_q = tempDataItem.getXValue();

                    if (((target_q < lower) || (target_q > upper)) && (summedSet.indexOf(target_q) < 0)) {
                        /*
                         *  if q value is outside range of reference
                         *  .indexOf() < 0 means value is not found in XYSeries
                         */
                        sigma = 1.0/(scale*targetError.getY(j).doubleValue());
                        var = sigma*sigma;

                        summedSet.add(target_q, scale * tempDataItem.getYValue()*var);
                        summedSetError.add(target_q, var );

                    } else {
                        refIndex = summedSet.indexOf(tempDataItem.getX());
                        if (refIndex >= 0) {
                            /*
                             * average the signal - first sum
                             */
                            tempSummedItem = summedSet.getDataItem(refIndex);

                            sigma = 1.0/(scale*targetError.getY(j).doubleValue());
                            var = sigma*sigma;

                            summedSet.updateByIndex(refIndex, (tempSummedItem.getYValue() + scale * targetData.getY(j).doubleValue() * var));
                            summedSetError.updateByIndex(refIndex, (summedSetError.getY(refIndex).doubleValue() + var));

                        } else if (lower < target_q && (target_q < upper)) { // no more interpolating
                            /*
                             * interpolate
                             */
                            System.out.println("StatMethods: Interpolating Dataset " + i + " target_q => " + target_q);
                            count = 0;
                            referenceQ = 0.0;
                            // find first value in reference greater than targetData.getX
                            while (summedSet.getX(count).doubleValue() < target_q && (count < 3000)) {
                                referenceQ = summedSet.getX(count).doubleValue();
                                count++;
                            }
                            System.out.println("StatMethods: Interpolating count " + count + " ref_q => " + referenceQ);

                            Double[] results = Functions.interpolate(targetData, referenceQ);
                            Double[] sigmaResults = Functions.interpolateSigma(targetError, referenceQ);
                            sigma = 1.0/(sigmaResults[1]*scale);
                            var = sigma*sigma;

                            //returns unlogged data
                            refIndex = summedSet.indexOf(referenceQ);

                            tempSummedItem = summedSet.getDataItem(refIndex);

                            summedSet.updateByIndex(refIndex, (tempSummedItem.getYValue() + scale * results[1] * var));
                            summedSetError.updateByIndex(refIndex, (summedSetError.getY(refIndex).doubleValue() + var ));
                        }
                    }
                }
            }
        }

        //double inv_total_n; // = 1.0/(double)total_n;
        for(int i=0; i<summedSet.getItemCount(); i++){

            var = summedSetError.getY(i).doubleValue();

            if ((Double.isNaN(var)) || (var == Double.POSITIVE_INFINITY)) {
                summedSet.updateByIndex(i, 0.0);
                summedSetError.updateByIndex(i, 1);
            } else {
                summedSet.updateByIndex(i, summedSet.getY(i).doubleValue() / var);
                summedSetError.updateByIndex(i, 1.0 / Math.sqrt(var));
            }

        }

        return returnMe;
    }

    /**
     *  Find least common q-value based on q-min of plotted data?
     *
     */
    private Number findLeastCommonQvalue(Collection dataCollection){

        boolean isCommon;

        Dataset firstSet = dataCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = dataCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        Number minQvalueInCommon = 10;

        outerloop:
        for(int j=0; j < referenceData.getItemCount(); j++){

            if (referenceData.getY(j).doubleValue() > 0){
                refItem = referenceData.getDataItem(j); // is refItem found in all sets
                minQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for(; startAt < totalInSampleSet; startAt++) {

                    tempDataset = dataCollection.getDataset(startAt);
                    tempData = tempDataset.getAllData();
                    // check if refItem q-value is in tempData
                    // if true, check next value
                    if (tempData.indexOf(refItem.getX()) < 0 && (tempData.getY(startAt).doubleValue() > 0)) {
                        isCommon = false;
                        break innerloop;
                    }
                }

                if (startAt == totalInSampleSet && isCommon){
                    break outerloop;
                }
            }
        }

        return minQvalueInCommon;
    }

    /**
     *
     */
    private Number findMaximumCommonQvalue(Collection dataCollection){

        boolean isCommon;

        Dataset firstSet = dataCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = dataCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        Number maxQvalueInCommon = 0;

        outerloop:
        for(int j=(referenceData.getItemCount()-1); j > -1; j--){

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            if (refItem.getYValue() > 0){
                maxQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for(; startAt < totalInSampleSet; startAt++) {

                    tempDataset = dataCollection.getDataset(startAt);
                    tempData = tempDataset.getAllData();
                    // check if refItem q-value is in tempData
                    // if true, check next value
                    // not found returns -1 for indexOf
                    // startAt in tempData should return non-negative value
                    if (tempData.indexOf(refItem.getX()) < 0 && tempData.getY(startAt).doubleValue() > 0) {
                        isCommon = false;
                        break innerloop;
                    }
                }

                if (startAt == totalInSampleSet && isCommon){
                    break outerloop;
                }
            }
        }

        return maxQvalueInCommon;
    }
}
