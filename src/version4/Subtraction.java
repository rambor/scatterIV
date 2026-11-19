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
    private int buffersInUse;

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

        int buffersInUse = 0, lastInUse = 0;
        for (int i = 0; i < buffers.getTotalDatasets(); i++) {
            if (buffers.getDataset(i).getInUse()) { buffersInUse++; lastInUse = i; }
        }
        this.buffersInUse = buffersInUse;
        if (buffersInUse > 1){
            ArrayList<XYSeries> stuff = createMedianAverageXYSeries(buffers);
            try {
                medianBuffer = (XYSeries)stuff.get(0).clone();
                medianBufferError = (XYSeries)stuff.get(1).clone();
                averageBuffer = (XYSeries)stuff.get(2).clone();
                averageBufferError = (XYSeries)stuff.get(3).clone();

            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        } else { // the one buffer in use (not necessarily the first loaded)
            medianBuffer = buffers.getDataset(lastInUse).getAllData();
            medianBufferError = buffers.getDataset(lastInUse).getAllDataError();
            averageBuffer = buffers.getDataset(lastInUse).getAllData();
            averageBufferError = buffers.getDataset(lastInUse).getAllDataError();
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


        setStatus("Merging => average " + mergebyAverage + " median: " + mergebyMedian);

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

        onBar(() -> bar.setStringPainted(true));
        onBar(() -> bar.setIndeterminate(true));
        onBar(() -> bar.setValue(0));

        // create subtracted set of files from average buffer
        subtractedCollection = new Collection("Subtracted");
        int total = samples.getTotalDatasets();
        onBar(() -> bar.setMaximum(total));

        setStatus("Subtracting " + total + " files");

        /*
         * subtract each sample from the averaged buffer
         */
        for (int i = 0; i < total; i++) { // make the subtracted datasets using a fixed, averaged buffer

            if (samples.getDataset(i).getInUse()) {

                ArrayList<XYSeries> subtraction = subtract(samples.getDataset(i).getAllData(), samples.getDataset(i).getAllDataError(), averageBuffer, averageBufferError, singlesOnly());
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
            setStatus("MERGING BY MEDIAN");
            // merge subtracted set by taking median of the subtracted sets (subtracted set was made using median buffer)
            Dataset medianSetFromAveragedbuffer = medianSubtractedSets(subtractedCollection, "medianSample_averageBuffer", averageBufferError);

            medianSetFromAveragedbuffer.initializeSasObject();
            medianSetFromAveragedbuffer.getSasObject().getSasSample().setDetails("Subtracted : med(Sample) - ave(buffer)");

            returnCollection.addDataset(medianSetFromAveragedbuffer);
            returnSubtractedMergedCollection.addDataset(medianSetFromAveragedbuffer);

            // subtract from average and (median buffer if count > 2)
            if (buffersInUse > 2){ // do subtraction against median buffer

                setStatus("Creating median buffer set");
                subtractedCollection.removeAllDatasets();

                for (int i = 0; i < total; i++) {

                    if (samples.getDataset(i).getInUse() ) {

                        ArrayList<XYSeries> subtraction = subtract(samples.getDataset(i).getAllData(), samples.getDataset(i).getAllDataError(), medianBuffer, medianBufferError, false);
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

                // median of the samples, subtracted from the median buffer
                Dataset medianSetFromMedianbuffer = medianSubtractedSets(subtractedCollection, "medianSample_medianBuffer", medianBufferError);

                medianSetFromMedianbuffer.initializeSasObject();
                medianSetFromMedianbuffer.getSasObject().getSasSample().setDetails("Subtracted : med(Sample) - med(buffer)");
                medianSetFromMedianbuffer.setId(returnCollection.getTotalDatasets());

                returnCollection.addDataset(medianSetFromMedianbuffer);
                returnSubtractedMergedCollection.addDataset(medianSetFromMedianbuffer);
            }

        } else if (mergebyAverage) { // average the subtractedCollection (which was made from averaged buffer)

            Dataset averagedSetFromAveragedbuffer = averagedSubtractedSets(subtractedCollection, "average_average", averageBufferError);
            averagedSetFromAveragedbuffer.initializeSasObject();
            averagedSetFromAveragedbuffer.getSasObject().getSasSample().setDetails("Subtracted : ave(Sample) - ave(buffer)");

            returnCollection.addDataset(averagedSetFromAveragedbuffer);
            returnSubtractedMergedCollection.addDataset(averagedSetFromAveragedbuffer);
            // subtract from average and (median buffer if count > 2)
            if (buffersInUse > 2){ // do median buffer

                subtractedCollection.removeAllDatasets();

                for (int i = 0; i < total; i++) {

                    if (samples.getDataset(i).getInUse()) {


                        ArrayList<XYSeries> subtraction = subtract(samples.getDataset(i).getAllData(), samples.getDataset(i).getAllDataError(), medianBuffer, medianBufferError, false);
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
                Dataset averagedSetFromMedianbuffer = averagedSubtractedSets(subtractedCollection, "average_median", medianBufferError);

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
                datatemp.getSasObject().getSasSample().setDetails("Subtracted : Sample - ave(buffer)");
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

    /** Swing components may only be changed on the Event Dispatch Thread; this worker runs elsewhere */
    private void setStatus(String text) {
        SwingUtilities.invokeLater(() -> status.setText(text));
    }

    private void onBar(Runnable change) {
        SwingUtilities.invokeLater(change);
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
        ArrayList<XYSeries> subtraction = subtract(sampleXY, sampleError, averageBuffer, averageBufferError, true);

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
        subtraction = subtract(sampleXY, sampleError, medianBuffer, medianBufferError, true);
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
        ArrayList<XYSeries> subtraction = subtract(sampleXY, sampleError, averageBuffer, averageBufferError, true);

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
        subtraction = subtract(sampleXY, sampleError, medianBuffer, medianBufferError, true);
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


    /** true when each frame is written on its own (no merging): its buffer error goes in at subtraction */
    private boolean singlesOnly() { return !mergebyAverage && !mergebyMedian; }

    /**
     * The frames to be merged are subtracted WITHOUT the buffer error: all of them share the same
     * buffer, so its error is not reduced by merging and is added once to the merged curve here.
     * (Adding it to every frame and then averaging would shrink it by the number of frames.)
     */
    private void addBufferError(XYSeries mergedError, Collection merged, XYSeries bufferError) {
        double scale = 0;
        int n = 0;
        for (int i = 0; i < merged.getTotalDatasets(); i++) {
            if (merged.getDataset(i).getInUse()) { scale += merged.getDataset(i).getScaleFactor(); n++; }
        }
        scale = n > 0 ? scale / n : 1.0;
        for (int i = 0; i < mergedError.getItemCount(); i++) {
            double q = mergedError.getX(i).doubleValue();
            int k = bufferError.indexOf(q);
            double eb = k >= 0 ? bufferError.getY(k).doubleValue()
                    : (q > bufferError.getMinX() && q < bufferError.getMaxX() ? Functions.interpolateSigma(bufferError, q)[1] : Double.NaN);
            if (!Double.isFinite(eb)) continue;
            eb *= scale;
            double es = mergedError.getY(i).doubleValue();
            mergedError.updateByIndex(i, Math.sqrt(es * es + eb * eb));
        }
    }

    public Dataset medianSubtractedSets(Collection subtractedCollection, String type, XYSeries bufferError){

        if (scaleBeforeMerging) {
            setStatus("scaling sets");

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

        setStatus("Merging sets");
        String output_name;

        // createMedian
        ArrayList<XYSeries> sampleSeries = createMedianAverageXYSeries(subtractedCollection);
        //createMedianAverageXYSeries(samples);
        XYSeries sampleXY = sampleSeries.get(0);    //median
        XYSeries sampleError = sampleSeries.get(1); //median error
        addBufferError(sampleError, subtractedCollection, bufferError);

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
    public Dataset averagedSubtractedSets(Collection subtractedCollection, String type, XYSeries bufferError){

        if (scaleBeforeMerging) {
            setStatus("scaling sets");

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

        setStatus("Merging by Averaging");

        String temp_name = Functions.sanitizeForFilename(name);

        ArrayList<XYSeries> finalSets;
        String output_name;
        finalSets = createRejectionDataSets(subtractedCollection);
        addBufferError(finalSets.get(1), subtractedCollection, bufferError);
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

        setStatus("writing each dataset to file");
        FileObject dataToWrite = new FileObject(new File(cwd), version);

        for (int i = 0; i < total; i++) {
            dataToWrite.writeSAXSFile(subtractedCollection.getDataset(i).getFileName(), subtractedCollection.getDataset(i));
        }
    }


    /**
     * sample - buffer, the buffer interpolated where its q grid differs.
     * @param withBufferError add the buffer error in quadrature (single frames); for frames that are merged
     *                        afterwards it is added once to the merged curve instead (see addBufferError)
     */
    private ArrayList<XYSeries> subtract(XYSeries sample, XYSeries sampleError, XYSeries buffer, XYSeries bufferError,
                                         boolean withBufferError){
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

                eValue = withBufferError ? bufferError.getY(indexOf).doubleValue() : 0.0;
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

                if (qValue > buffer.getMinX() && qValue < maxQValueInBuffer){
                    Double[] results = Functions.interpolate(buffer, qValue);
                    Double[] sigmaResults = Functions.interpolateSigma(bufferError, qValue);
                    //returns unlogged data
                    eValue = withBufferError ? sigmaResults[1] : 0.0;

                    // sample minus the interpolated buffer (previously the buffer value itself was stored)
                    subData.add(qValue, tempDataItem.getYValue() - results[1]);
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


    /**
     * Merge the in-use curves by a weighted average with outlier rejection.
     *
     * At every q the frames are compared with their median at that q, in units of each frame's own
     * error: z = (I_f(q) - median_q) / sigma_f(q). The z values of all q in a bin are pooled to get a
     * robust centre (median) and spread (1.1926 Sn), and points with |z - centre| > cutoff * spread are
     * rejected (both directions). Working with deviations from the per-q median keeps the slope of
     * I(q) inside a bin out of the test; the bins only pool enough points for a stable spread.
     * The kept points are averaged with 1/sigma^2 weights.
     */
    private ArrayList<XYSeries> createRejectionDataSets(Collection thisCollection){

        // q -> {I, sigma} of every in-use frame (scale factors applied)
        TreeMap<Double, ArrayList<double[]>> byQ = new TreeMap<>();
        int total = thisCollection.getDatasets().size();
        for (int i = 0; i < total; i++) {
            Dataset d = thisCollection.getDataset(i);
            if (!d.getInUse()) continue;
            double scale = d.getScaleFactor();
            XYSeries data = d.getAllData(), err = d.getAllDataError();
            for (int j = 0; j < data.getItemCount(); j++) {
                double sigma = err.getY(j).doubleValue() * scale;
                double y = data.getY(j).doubleValue() * scale;
                if (!(sigma > 0) || Double.isNaN(y)) continue;
                byQ.computeIfAbsent(data.getX(j).doubleValue(), k -> new ArrayList<>()).add(new double[]{y, sigma});
            }
        }

        ArrayList<XYSeries> returnMe = new ArrayList<>();
        returnMe.add(new XYSeries("Pruned"));
        returnMe.add(new XYSeries("PrunedError"));
        XYSeries finalSeries = returnMe.get(0);
        XYSeries errorSeries = returnMe.get(1);
        if (byQ.isEmpty()) return returnMe;

        Double[] qs = byQ.keySet().toArray(new Double[0]);
        int nq = qs.length;
        // per-q deviations from the median, in error units
        double[][] z = new double[nq][];
        for (int k = 0; k < nq; k++) {
            ArrayList<double[]> obs = byQ.get(qs[k]);
            ArrayList<Double> ys = new ArrayList<>();
            for (double[] o : obs) ys.add(o[0]);
            double median = Statistics.calculateMedian(ys, true);
            z[k] = new double[obs.size()];
            for (int f = 0; f < obs.size(); f++) z[k][f] = (obs.get(f)[0] - median) / obs.get(f)[1];
        }

        // bins: bins % of the q points, at least 1, each pooling a few q values
        int bbins = Math.max(1, Math.min(nq, (int) Math.round(bins / 100.0 * nq)));
        for (int b = 0; b < bbins; b++) {
            int from = (int) ((long) b * nq / bbins), to = (int) ((long) (b + 1) * nq / bbins);
            ArrayList<Double> pooled = new ArrayList<>();
            for (int k = from; k < to; k++) for (double v : z[k]) pooled.add(v);

            double centre = 0, spread = 0;
            if (pooled.size() >= 3) {
                centre = Statistics.calculateMedian(pooled, true);
                spread = 1.1926 * Functions.s_n(pooled);
            }
            boolean reject = spread > 0 && Double.isFinite(spread);

            for (int k = from; k < to; k++) {
                ArrayList<double[]> obs = byQ.get(qs[k]);
                double sum = 0, weights = 0;
                for (int f = 0; f < obs.size(); f++) {
                    if (reject && Math.abs(z[k][f] - centre) > cutoff * spread) continue;
                    double w = 1.0 / (obs.get(f)[1] * obs.get(f)[1]);
                    sum += obs.get(f)[0] * w;
                    weights += w;
                }
                if (weights > 0) {
                    finalSeries.add((double) qs[k], sum / weights);
                    errorSeries.add((double) qs[k], 1.0 / Math.sqrt(weights));
                }
            }
        }
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


    /**
     * Median of the in-use frames at every q (all points, including negative intensities, which are
     * common for subtracted WAXS frames); returns {median, median error}.
     */
    private ArrayList<XYSeries> medianDatasets(Collection thisCollection){
        TreeMap<Double, ArrayList<double[]>> byQ = pointsByQ(thisCollection);
        ArrayList<XYSeries> returnMe = new ArrayList<>();
        returnMe.add(new XYSeries("median_set_from_collection"));
        returnMe.add(new XYSeries("median_error_set_from_collection"));
        for (Map.Entry<Double, ArrayList<double[]>> e : byQ.entrySet()) {
            ArrayList<Double> ys = new ArrayList<>(), es = new ArrayList<>();
            for (double[] p : e.getValue()) { ys.add(p[0]); es.add(p[1]); }
            int n = ys.size();
            double med = Statistics.calculateMedian(ys, true);
            double err = n == 1 ? es.get(0) : Math.sqrt(Math.PI / 2) * Statistics.calculateMedian(es, true) / Math.sqrt(n);
            returnMe.get(0).add((double) e.getKey(), med);
            returnMe.get(1).add((double) e.getKey(), err);
        }
        return returnMe;
    }

    /** Inverse-variance weighted mean of the in-use frames at every q; returns {mean, error}. */
    private ArrayList<XYSeries> weightedAverageDatasets(Collection selected){
        TreeMap<Double, ArrayList<double[]>> byQ = pointsByQ(selected);
        ArrayList<XYSeries> returnMe = new ArrayList<>();
        returnMe.add(new XYSeries("Summed"));
        returnMe.add(new XYSeries("SummedError"));
        for (Map.Entry<Double, ArrayList<double[]>> e : byQ.entrySet()) {
            double sum = 0, weights = 0;
            for (double[] p : e.getValue()) {
                double w = 1.0 / (p[1] * p[1]);
                sum += p[0] * w;
                weights += w;
            }
            if (weights > 0) {
                returnMe.get(0).add((double) e.getKey(), sum / weights);
                returnMe.get(1).add((double) e.getKey(), 1.0 / Math.sqrt(weights));
            }
        }
        return returnMe;
    }

    /**
     * q -> {I, sigma} of every in-use frame, scale factors applied. Built from all the data, not from the
     * positive-only range: a subtracted frame can be negative almost everywhere (WAXS), and looking
     * its q limits up from the positive points is what failed before (Index -1063 out of bounds).
     */
    private static TreeMap<Double, ArrayList<double[]>> pointsByQ(Collection c) {
        TreeMap<Double, ArrayList<double[]>> byQ = new TreeMap<>();
        for (int i = 0; i < c.getTotalDatasets(); i++) {
            Dataset d = c.getDataset(i);
            if (!d.getInUse()) continue;
            double scale = d.getScaleFactor();
            XYSeries data = d.getAllData(), err = d.getAllDataError();
            for (int j = 0; j < data.getItemCount(); j++) {
                double y = data.getY(j).doubleValue() * scale;
                double s = (j < err.getItemCount() ? err.getY(j).doubleValue() : Double.NaN) * scale;
                if (Double.isNaN(y) || !(s > 0)) continue;
                byQ.computeIfAbsent(data.getX(j).doubleValue(), k -> new ArrayList<>()).add(new double[]{y, s});
            }
        }
        return byQ;
    }
}
