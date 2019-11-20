package version4.plots;

import FileManager.WorkingDirectory;
import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.ui.Layer;
import version4.*;
import version4.tableModels.SampleBufferElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

/**
 * Created by robertrambo on 18/01/2016.
 */
public class SignalPlot extends SwingWorker<Void, Integer> {

    private JFreeChart chart;
    private ChartFrame frame;
    //private ChartPanel chartPanel;

    private XYLineAndShapeRenderer renderer1;
    private XYLineAndShapeRenderer rightRenderer;
    public JList samplesList;
    private JLabel status;
    private double noSignal = 0.2;

    private Collection samplesCollection;
    private Collection buffersCollection;
    private XYSeriesCollection plotMe;
    private XYSeriesCollection plotRg;
    private int firstFrame;
    private int lastFrame;

    private Number minQvalueInCommon = 0;
    private Number maxQvalueInCommon = 0;

    private JProgressBar mainStatus;
    private XYSeries buffer;
    private XYSeries bufferError;
    private boolean useRg = false;
    private double threshold, earlyUpperQmaxLimit = 0.25;
    private int startAtPoint, totalInSamples;

    private boolean useqIq = false;
    private WorkingDirectory workingDirectory;


    public SignalPlot(Collection sampleCollection, Collection bufferCollection, JLabel status, boolean useRg, JProgressBar bar, double threshold) {
        this.samplesCollection = sampleCollection;
        totalInSamples = samplesCollection.getTotalDatasets();
        this.buffersCollection = bufferCollection;
        plotMe = new XYSeriesCollection();
        plotRg = new XYSeriesCollection();

        this.useRg = useRg;
        mainStatus = bar;
        this.status = status;
        this.threshold = threshold;
    }

    /**
     * This constructor assumes data is already subtracted
     *
     * @param sampleCollection Subtracted Dataset
     * @param status           JLabel for sending messages to main
     * @param useRg            Boolean to do autoRg
     * @param bar              progressbar for calculation from main
     * @param threshold        cutoff for AutoRG
     */
    public SignalPlot(Collection sampleCollection, JLabel status, boolean useRg, JProgressBar bar, double threshold) {
        this.samplesCollection = sampleCollection;
        this.buffersCollection = new Collection("buffers");
        plotMe = new XYSeriesCollection();
        plotRg = new XYSeriesCollection();

        useqIq = true;
        this.useRg = useRg;
        mainStatus = bar;
        this.status = status;
        this.threshold = threshold;
    }


    public SignalPlot(Collection sampleCollection, Collection bufferCollection, JLabel status, JProgressBar bar) {
        this.samplesCollection = sampleCollection;
        this.buffersCollection = bufferCollection;
        plotMe = new XYSeriesCollection();

        this.useRg = false;
        mainStatus = bar;
        this.status = status;
        this.threshold = 0.23;
    }


    /**
     * create data for signal plot
     *
     * @return
     */
    public XYSeriesCollection createSignalPlotData() {
        this.makeBuffer();
        this.makeSamples();
        return plotMe;
    }


    public void setPointsToExclude(int value) {
        startAtPoint = value;
        System.out.println("Number of points to exclude : " + value);
    }


    private void writeData() {
        try {
            if (plotMe.getSeriesCount() == 0) {
                throw new Exception("Not enough points in ratio, check q-vales - must match: ");
            }
        } catch (Exception ex) {
            status.setText("Error " + ex.getMessage().toString());
        }

        // write signal plot to file
        ArrayList<String> linesToFile = new ArrayList<String>();
        linesToFile.add("# REMARK file_index integrated_area_ratio file_name");
        // create plot using plotMe

        if (useRg) {
            linesToFile.set(0, "# REMARK file_index integrated_area_ratio R_g file_name");
        }

        // go over each series and write out
        for (int w = 0; w < plotMe.getSeriesCount(); w++) {
            XYSeries tempSeries = plotMe.getSeries(w);
            if (useRg) {

                XYSeries tempRgSeries = plotRg.getSeries(w);
                linesToFile.add(String.format("%d %.5f %.3f %s", w, tempSeries.getY(0).doubleValue(), tempRgSeries.getY(0).doubleValue(), tempSeries.getKey()));

            } else {
                linesToFile.add(String.format("%d %.5f %s", w, tempSeries.getY(0).doubleValue(), tempSeries.getKey()));
            }
        }

        Functions.writeLinesToFile(linesToFile, "signal_plot.txt", workingDirectory.getWorkingDirectory());
    }


    /**
     * use if samplesCollection contains subtracted data
     */
    private void makeSamplesFromSubtractedData() {

        Dataset tempDataset;
        XYDataItem tempXY;

        XYSeries qIqData = new XYSeries("ratio"), tempData;
        XYSeries errorOfUse = new XYSeries("errorset");

        totalInSamples = samplesCollection.getTotalDatasets();
        int totalXY;
        int seriesCount = 0;
        double area;
        //double[] izeroRg;

        //System.out.println("FIRST FRAME : " + firstFrame + " LAST FRAME : " + lastFrame);

        if (lastFrame <= 0 || lastFrame <= firstFrame) {
            lastFrame = totalInSamples;
        }

        for (int i = 0; i < totalInSamples; i++) {

            if (i >= firstFrame && i < lastFrame) {
                tempDataset = samplesCollection.getDataset(i);
                qIqData.clear();
                tempData = tempDataset.getAllData();
                totalXY = tempData.getItemCount();

                // create XY Series using ratio to buffer then integrate
                for (int q = startAtPoint; q < totalXY; q++) {
                    tempXY = tempData.getDataItem(q);
                    qIqData.add(tempXY.getX(), tempXY.getYValue() * tempXY.getXValue());
                }

                // if number of points is less than 100, skip
                if (qIqData.getItemCount() > 100) {
                    // integrate
                    Dataset dataInUse = samplesCollection.getDataset(i);
                    plotMe.addSeries(new XYSeries(dataInUse.getFileName()));
                    area = Functions.trapezoid_integrate(qIqData);
                    plotMe.getSeries(seriesCount).add(i, area);

                    if (useRg) { // make double plot if checked

                        status.setText("AUTO-Rg FOR : " + dataInUse.getFileName());
                        plotRg.addSeries(new XYSeries(dataInUse.getFileName()));
                        if (area > threshold) {
                            AutoRg tempRg = new AutoRg(tempData, startAtPoint + 1);

                            plotRg.getSeries(seriesCount).add(i, tempRg.getRg());
                        } else {
                            plotRg.getSeries(seriesCount).add(i, 0);
                        }
                    }
                    seriesCount++;
                }
            }

            publish(i);
            //mainStatus.setValue((int) (i / (double) total * 100));
        }
    }


    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size() - 1);
        //mainStatus.setValue((int) (i / (double) totalInSamples * 100));
        mainStatus.setValue(i);
        super.process(chunks);
    }


    /**
     * creates XYSeries collections used for plotting
     */
    private void makeSamples() {
        Dataset tempDataset;
        XYDataItem tempXY;

        XYSeries ratio = new XYSeries("ratio"), tempData;

        int total = samplesCollection.getTotalDatasets();

        int totalXY, bufferIndex;
        int seriesCount = 0;
        double area;

        //System.out.println("FIRST FRAME : " + firstFrame + " LAST FRAME : " + lastFrame);

        if (lastFrame <= 0 || lastFrame <= firstFrame) {
            lastFrame = total;
        }

        for (int i = 0; i < total; i++) { // iterate over all frames

            if (i >= firstFrame && i < lastFrame) { // only use those within selected range
                tempDataset = samplesCollection.getDataset(i);
                ratio.clear();
                tempData = tempDataset.getAllData();
                totalXY = tempData.getItemCount();

                // create XY Series using ratio to buffer then integrate
                for (int q = 0; q < totalXY; q++) {
                    tempXY = tempData.getDataItem(q);
                    bufferIndex = buffer.indexOf(tempXY.getX());
                    if (bufferIndex >= 0) {
                        ratio.add(tempXY.getX(), tempXY.getYValue() / buffer.getY(bufferIndex).doubleValue());
                        if (tempXY.getXValue() > earlyUpperQmaxLimit) { // qmax is set to 0.25;
                            break;
                        }
                    }
                }

                // if number of points is less than 100, skip
                if (ratio.getItemCount() > 100) {
                    // integrate
                    Dataset dataInUse = samplesCollection.getDataset(i);
                    plotMe.addSeries(new XYSeries(dataInUse.getFileName()));
                    area = Functions.trapezoid_integrate(ratio);
                    plotMe.getSeries(seriesCount).add(i, area);

                    if (useRg) { // make double plot if checked

                        status.setText("AUTO-Rg FOR : " + dataInUse.getFileName());
                        plotRg.addSeries(new XYSeries(dataInUse.getFileName()));
                        if (area > threshold) {
                            ArrayList<XYSeries> subtraction = subtract(dataInUse.getAllData(), dataInUse.getAllDataError(), buffer, bufferError);
                            AutoRg temp = new AutoRg(subtraction.get(0), startAtPoint + 1);
                            plotRg.getSeries(seriesCount).add(i, temp.getRg());
                        } else {
                            plotRg.getSeries(seriesCount).add(i, 0);
                        }

                    }

                    seriesCount++;

                } else {
                    System.out.println("TOO FEW IN RATIO : Frame => " + i);
                }

            }
            publish(i);
        }
    }

    /**
     * creates XYSeries collections used for plotting
     * Does not do integrated ratio for signal
     * calculate signal as a sum of differences at key q-values
     */
    private void makeSamplesAlternate() {
        Dataset tempDataset;
        XYSeries tempData;
        XYDataItem tempXY, tempXYb;
        int total = samplesCollection.getTotalDatasets();

        int totalXY, bufferIndex, qIndex;
        int seriesCount = 0;

        //System.out.println("FIRST FRAME : " + firstFrame + " LAST FRAME : " + lastFrame);

        if (lastFrame <= 0 || lastFrame <= firstFrame) {
            lastFrame = total;
        }

        /*
         * pick points to calculate difference from background
         * 0.01 to 0.02
         * 0.03 to 0.04
         * 0.06 to 0.07
         * 0.1 to 0.12
         */
        ArrayList<Double> lowerLimit = new ArrayList<>();
        ArrayList<Double> upperLimit = new ArrayList<>();
        lowerLimit.add(0.01);
        lowerLimit.add(0.03);
        lowerLimit.add(0.06);
        lowerLimit.add(0.10);

        upperLimit.add(0.02);
        upperLimit.add(0.04);
        upperLimit.add(0.07);
        upperLimit.add(0.12);
        /*
         * assemble buffer points
         */
        int bufferSize = buffer.getItemCount();

        for (int i = 0; i < total; i++) { // iterate over all frames

            if (i >= firstFrame && i < lastFrame) { // only use those within selected range
                tempDataset = samplesCollection.getDataset(i);
                tempData = tempDataset.getAllData();
                totalXY = tempData.getItemCount();

                // create XY Series using ratio to buffer then integrate
                double signal = 0.0d;
                int startIndex = 0;
                for (int a = 0; a < 4; a++) {
                    double lower = lowerLimit.get(a);
                    double upper = upperLimit.get(a);

                    for (int m = startIndex; m < bufferSize; m++) {
                        tempXYb = buffer.getDataItem(m);
                        if (tempXYb.getXValue() > lower && tempXYb.getXValue() < upper) { // check if found in data

                            qIndex = tempData.indexOf(tempXYb.getX());

                            if (qIndex >= 0) {
                                signal += tempData.getY(qIndex).doubleValue() - tempXYb.getYValue();
                                startIndex = m + 1;
                                break;
                            }
                        }
                    }
                }

                // if number of points is less than 100, skip
                // integrate
                plotMe.addSeries(new XYSeries(tempDataset.getFileName()));
                plotMe.getSeries(seriesCount).add(i, signal);

                if (useRg) { // make double plot if checked
                    status.setText("AUTO-Rg FOR : " + tempDataset.getFileName());
                    plotRg.addSeries(new XYSeries(tempDataset.getFileName()));
                    if (signal > threshold) {
                        ArrayList<XYSeries> subtraction = subtract(tempDataset.getAllData(), tempDataset.getAllDataError(), buffer, bufferError);

                        AutoRg temp = new AutoRg(subtraction.get(0), startAtPoint + 1);
                        plotRg.getSeries(seriesCount).add(i, temp.getRg());
                    } else {
                        plotRg.getSeries(seriesCount).add(i, 0);
                    }
                }

                seriesCount++;


            }
            publish(i);
        }
    }


    private void makeBuffer() {


        try {
            //total = buffersCollection.getDatasetCount();
            int select = buffersCollection.getTotalSelected();

            if (select == 1) {
                buffer = buffersCollection.getLast().getAllData();
                bufferError = buffersCollection.getLast().getAllDataError();
            } else if (select > 1) {
                ArrayList<XYSeries> stuff = createMedianAverageXYSeries(buffersCollection);
                buffer = stuff.get(0);
                bufferError = stuff.get(1);
            } else {
                throw new Exception("Must have at least one buffer selected: ");
            }
        } catch (Exception ex) {
            status.setText("Must have at least one buffer" + ex.getMessage().toString());
        }
    }

    /**
     * create median and average dataset only on the qmin and max that are in common
     * all data contains all positive values.
     *
     * @param collection
     * @return
     */
    private ArrayList<XYSeries> createMedianAverageXYSeries(Collection collection) {
        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();

        // calculate Average and Median for set

        ArrayList<XYSeries> median_reduced_set = StatMethods.medianDatasets(collection);
        ArrayList<XYSeries> averaged = StatMethods.weightedAverageDatasets(collection);

        String name = "median_set";

        XYSeries medianAllData = null;
        XYSeries medianAllDataError = null;

        try {
            medianAllData = (XYSeries) median_reduced_set.get(0).clone();
            medianAllData.setKey(name);
            medianAllDataError = (XYSeries) median_reduced_set.get(1).clone();
            medianAllDataError.setKey(name);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        returnMe.add(medianAllData);          // 0
        returnMe.add(medianAllDataError);     // 1
        returnMe.add(averaged.get(0));        // 2
        returnMe.add(averaged.get(1));        // 3

        return returnMe;
    }


    private ArrayList<XYSeries> subtract(XYSeries sample, XYSeries sampleError, XYSeries buffer, XYSeries bufferError) {
        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        // for each element in sample collection, do subtraction

        XYSeries subData;
        XYSeries subError;
        XYDataItem tempDataItem;

        int tempTotal, indexOf;
        double qValue, yValue, eValue;

        tempTotal = sample.getItemCount();

        subData = new XYSeries("subtracted");
        subError = new XYSeries("errorSubtracted");
        //Subtract and add to new data
        double maxQValueInBuffer = buffer.getMaxX();

        QLOOP:
        for (int q = 0; q < tempTotal; q++) {
            tempDataItem = sample.getDataItem(q);
            qValue = tempDataItem.getXValue();
            /*
             * check to see if in buffer
             */
            indexOf = buffer.indexOf(qValue);
            yValue = sampleError.getY(q).doubleValue();

            if (indexOf > -1) {
                subData.add(qValue, tempDataItem.getYValue() - buffer.getY(indexOf).doubleValue());

                eValue = bufferError.getY(indexOf).doubleValue();
                subError.add(qValue, Math.sqrt(yValue * yValue + eValue * eValue));

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
                if (qValue < maxQValueInBuffer) {
                    System.out.println("Interpolating Value at " + qValue);
                    Double[] results = Functions.interpolate(buffer, qValue, 1);
                    Double[] sigmaResults = Functions.interpolateSigma(bufferError, qValue);
                    //returns unlogged data
                    eValue = sigmaResults[1];

                    subData.add(qValue, results[1]);
                    subError.add(qValue, Math.sqrt(yValue * yValue + eValue * eValue));
                }

            }
        }

        returnMe.add(subData);
        returnMe.add(subError);

        return returnMe;
    }

    /**
     * Set list of files to be used by MouseOver event
     *
     * @param list
     */
    public void setSampleJList(JList list) {
        this.samplesList = list;
    }

    public ChartFrame getChartFrame() {
        return frame;
    }


    public void makePlot() {

        chart = ChartFactory.createXYLineChart(
                "Signal Plot",            // chart title
                "sample",                 // domain axis label
                "signal",                 // range axis label
                plotMe,                   // data
                PlotOrientation.VERTICAL,
                false,                    // include legend
                true,                     // toolTip
                false
        );

        ChartPanel chartPanel = new ChartPanel(chart) {
            @Override
            public void restoreAutoBounds() {
                super.restoreAutoDomainBounds();
                super.restoreAutoRangeBounds();
                super.getChart().getXYPlot().getRangeAxis().setAutoRange(false);
                int seriesCount = super.getChart().getXYPlot().getDataset(0).getSeriesCount();

                double min = 1000;
                double max = -10;
                double minX = 10;
                double maxX = 0;
                double tempYMin;
                double tempXMin;

                for (int i = 0; i < seriesCount; i++) {
                    tempYMin = super.getChart().getXYPlot().getDataset(0).getYValue(i, 0);
                    tempXMin = super.getChart().getXYPlot().getDataset(0).getXValue(i, 0);

                    if (tempYMin < min) {
                        min = tempYMin;
                    }
                    if (tempYMin > max) {
                        max = tempYMin;
                    }
                    if (tempXMin < minX) {
                        minX = tempXMin;
                    }
                    if (tempXMin > maxX) {
                        maxX = tempXMin;
                    }
                }
                super.getChart().getXYPlot().getRangeAxis().setRange(min - Math.abs(min * 0.02), max + Math.abs(0.02 * max));
                super.getChart().getXYPlot().getDomainAxis().setRange(minX - Math.abs(minX * 0.02), maxX + Math.abs(0.02 * maxX));
            }
        };

        chart.setTitle("");

        final XYPlot plot = chart.getXYPlot();
    /*
      set domain and range axis
     */
        final NumberAxis domainAxis = new NumberAxis("Sample ID");
        final NumberAxis rangeAxis = new NumberAxis("Integral of Ratio to Background");
        if (useqIq) {
            rangeAxis.setLabel("Integral of q*I(q)");
        }
        final NumberAxis rangeAxisRight = new NumberAxis("Rg");

        rangeAxis.setRange(plotMe.getRangeLowerBound(true) - 0.01 * plotMe.getRangeLowerBound(true), plotMe.getRangeUpperBound(true) + 0.02 * plotMe.getRangeUpperBound(true));
        rangeAxis.setAutoRangeIncludesZero(false);

        plot.setRangeAxis(rangeAxis);

        plot.setDataset(0, plotMe);
        plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis
        double negativePointSize, pointSize;

        if (!(plotRg == null) && (plotRg.getSeriesCount() > 0)) {

            String quoteR = "Rg Å";
            rangeAxisRight.setLabel(quoteR);
            rangeAxisRight.setLabelFont(new Font("Times", Font.BOLD, 20));
            rangeAxisRight.setLabelPaint(new Color(51, 153, 255));
            rangeAxisRight.setAutoRange(true);
            rangeAxisRight.setAutoRangeIncludesZero(false);
            rangeAxisRight.setAutoRangeStickyZero(false);

            //double minRg = plotRg.getRangeLowerBound(true);
            if (plotRg.getRangeUpperBound(true) > 0) {
                rangeAxisRight.setRange(0, plotRg.getRangeUpperBound(true) + 0.02 * plotRg.getRangeUpperBound(true));
            } else {
                rangeAxisRight.setRange(-1, 10);
            }

            rightRenderer = new XYLineAndShapeRenderer();

            plot.setDataset(1, plotRg);
            plot.mapDatasetToRangeAxis(1, 1); //2nd dataset to 2nd y-axis
            plot.setRangeAxis(1, rangeAxisRight);

            for (int i = 0; i < plotRg.getSeriesCount(); i++) {
                // go over each series
                pointSize = 9;
                negativePointSize = -0.5 * pointSize;
                rightRenderer.setSeriesShape(i, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
                rightRenderer.setSeriesLinesVisible(i, false);
                rightRenderer.setSeriesPaint(i, new Color(51, 153, 255));
                rightRenderer.setSeriesShapesFilled(i, true);
                rightRenderer.setSeriesOutlinePaint(i, new Color(51, 153, 255));
                rightRenderer.setSeriesOutlineStroke(i, new BasicStroke(1.0f));
            }

            plot.setRenderer(1, rightRenderer);       //render as a line
        }

        plot.setBackgroundAlpha(0.0f);
        plot.setOutlineVisible(false);

        renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShape(new Ellipse2D.Double(-0.5 * 6, -0.5 * 6.0, 6, 6));

        Color fillColor = new Color(255, 140, 0, 70);
        Color outlineColor = new Color(255, 127, 80, 100);

        for (int i = 0; i < plotMe.getSeriesCount(); i++) {
            // go over each series
            pointSize = 9;
            negativePointSize = -0.5 * pointSize;
            renderer1.setSeriesShape(i, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
            renderer1.setSeriesLinesVisible(i, false);
            renderer1.setSeriesPaint(i, fillColor);
            //renderer1.setSeriesPaint(i, new Color(36, 46, 54));
            renderer1.setSeriesShapesFilled(i, true);
            //renderer1.setSeriesOutlinePaint(i, new Color(36, 46, 54));
            renderer1.setSeriesOutlinePaint(i, outlineColor);
            renderer1.setSeriesOutlineStroke(i, new BasicStroke(2.0f));
        }

        frame = new ChartFrame("SC\u212BTTER \u2263 SIGNAL PLOT", chart);
        frame.getChartPanel().setDisplayToolTips(true);

        frame.getChartPanel().getChart().getXYPlot().getRenderer(0).setBaseToolTipGenerator(new XYToolTipGenerator() {
            @Override
            public String generateToolTip(XYDataset xyDataset, int i, int i2) {
                return (String) xyDataset.getSeriesKey(i);
            }
        });

        frame.getChartPanel().setRangeZoomable(false);
        frame.getChartPanel().setDomainZoomable(false);
        frame.getChartPanel().setMouseZoomable(false);
        frame.getChartPanel().setHorizontalAxisTrace(true);

        // add mouse listener for getting values
        // frame.getChartPanel().addKeyListener();
        frame.getChartPanel().addMouseListener(new MouseMarker(frame.getChartPanel(), samplesList));

        frame.getChartPanel().addChartMouseListener(new ChartMouseListener() {
            private Double markerStart = Double.NaN;
            private Double markerEnd = Double.NaN;

            @Override
            public void chartMouseClicked(ChartMouseEvent chartMouseEvent) {
                System.out.println("Setting frame min ");
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent chartMouseEvent) {

            }
        });

        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setVisible(true);
    }

    @Override
    protected Void doInBackground() throws Exception {

        int select = buffersCollection.getTotalSelected();
        int sampleSize = samplesCollection.getTotalSelected();

        if (select > 0) {
            status.setText("compiling buffers");
            this.makeBuffer();
        } else if (sampleSize > 2 && !useqIq) {
            // if buffer size is zero, estimate background from samples
            // make signal plot from q*Iq
            this.estimateBackgroundFrames();
            // make a Runnable class that operates on windows of the data and updates treeset
        }

        status.setText("compiling samples");

        mainStatus.setValue(0);
        mainStatus.setMaximum(totalInSamples);
        mainStatus.setStringPainted(true);
        mainStatus.setString("Processing");

        if (useqIq) {
            this.makeSamplesFromSubtractedData();
        } else {
            this.makeSamples();
            //this.makeSamplesAlternate();
        }

        Toolkit.getDefaultToolkit().beep();
        mainStatus.setMinimum(0);
        mainStatus.setStringPainted(false);
        mainStatus.setValue(0);

        status.setText("Writing Signal Plot");
        this.writeData();

        status.setText("Making Plot");
        this.makePlot();
        mainStatus.setValue(0);
        mainStatus.setIndeterminate(false);
        status.setText("");

        return null;
    }


    @Override
    protected void done() {
        // make similarity plot
        try {
            get();
            status.setText("FINISHED");
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * estimate frames to be used as background and set
     * find large continuous sets of frames to be background
     * Signal will have a I(qmin)
     */
    private void estimateBackgroundFrames() {
        mainStatus.setIndeterminate(true);
        mainStatus.setStringPainted(true);
        mainStatus.setString("Estimating Background Frames");

        int total = samplesCollection.getTotalDatasets();

        if (!this.is0p3qmaxPossible()) {
            this.findMaximumCommonQvalue();
        }

        //if (minQvalueInCommon.doubleValue() == 0){
        this.findLeastCommonQvalue();
        //}

        int upperIndex = samplesCollection.getDataset(0).getAllData().indexOf(maxQvalueInCommon);
        int lowerIndex = samplesCollection.getDataset(0).getAllData().indexOf(minQvalueInCommon);

        int totalN = upperIndex - lowerIndex + 1;

        earlyUpperQmaxLimit = maxQvalueInCommon.doubleValue();
        noSignal = (maxQvalueInCommon.doubleValue() - minQvalueInCommon.doubleValue()) * (totalN + 1) / (double) totalN;

        // set window
        int window = 7;
        //
        TreeSet<Integer> keepers = new TreeSet<Integer>();
        /*
         * test if frames in the window are within the background defined by noSignal
         * might be more accurate to do a first pass, estimate the background using the median
         * then go back and assign
         *
         * could be multi-threaded
         */
        for (int w = window; w < (total - window); w++) {
            // calculate average curve in window and do ratio integral
            Collection collectionWindow = new Collection("window");

            for (int m = (w - window); m < w; m++) {
                collectionWindow.addDataset(samplesCollection.getDataset(m));
            }

            if (calculateAverageAndVarianceOfAllPairWiseRatiosInWindow(collectionWindow, noSignal, lowerIndex, upperIndex)) {
                for (int m = (w - window); m < w; m++) {
                    keepers.add(m);
                }
            }
        }
        //System.out.println("Preliminary baseline set size : " + keepers.size());

        Iterator<Integer> iterator = keepers.iterator();
        Collection keptCollection = new Collection("");

        //int totalInKept = keepers.size();
        //int count=1;
        // Displaying the Tree set data
        while (iterator.hasNext()) {
            Dataset tempDataset = samplesCollection.getDataset(iterator.next());
            keptCollection.addDataset(tempDataset);
            // System.out.println(count + "(" + totalInKept + ")" + " => Kept as buffer : " + tempDataset.getFileName());
            // count++;
        }

//        System.out.println(" => Calculating average for baseline ");
        Averager averagedEstimatedBackground = new Averager(keptCollection);

        averagedEstimatedBackground.getAveraged();
        buffer = averagedEstimatedBackground.getAveraged();
        bufferError = averagedEstimatedBackground.getAveragedError();
        // create trace using averagedEstimatedBackground
        mainStatus.setIndeterminate(false);
        mainStatus.setStringPainted(false);
    }


    /**
     * estimating background frames by calculating ratio of all datasets in collection
     * no signal is the integral of a line from qmin-qmax (ideal)
     *
     * @param collection
     * @param ideal
     * @param lowerIndex
     * @param upperIndex
     * @return
     */
    private boolean calculateAverageAndVarianceOfAllPairWiseRatiosInWindow(Collection collection, double ideal, int lowerIndex, int upperIndex) {

        boolean keep = false;

        int windowSize = collection.getTotalDatasets();
        double[] values = new double[windowSize * (windowSize - 1) / 2];

        int firstLimit = windowSize - 1;
        int totalXY, foundIndex;
        Dataset refDataset, targetDataset;

        XYSeries ratio = new XYSeries("ratio"), tempData, refData;
        XYDataItem refXY;

        int count = 0;
        for (int i = 0; i < firstLimit; i++) {

            refDataset = collection.getDataset(i);
            refData = refDataset.getAllData();
            totalXY = refData.getItemCount();

            for (int j = (i + 1); j < windowSize; j++) {
                targetDataset = collection.getDataset(j);
                tempData = targetDataset.getAllData();
                ratio.clear();
                // go through each q-value in reference
                for (int q = lowerIndex; q < (upperIndex + 1); q++) {
                    //for (int q = 0; q < totalXY; q++) {
                    refXY = refData.getDataItem(q);
                    foundIndex = tempData.indexOf(refXY.getX());
                    if (foundIndex > -1) {
                        ratio.add(refXY.getX(), refXY.getYValue() / tempData.getY(foundIndex).doubleValue());
                    }
                }

                values[count] = (Functions.trapezoid_integrate(ratio));
                count++;
            }
        }

        double average = StatUtils.mean(values);
        double variance = StatUtils.variance(values);
        //System.out.println("ideal " + ideal + " " + average + " " + Math.abs(ideal-average)/Math.sqrt(variance));
        double stat = Math.abs(ideal - average) / Math.sqrt(variance);
        if (stat < 0.6) {
            return true;
        }

        return keep;
    }


    /**
     *
     */
    private void findLeastCommonQvalue() {

        boolean isCommon;

        Dataset firstSet = samplesCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = samplesCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        minQvalueInCommon = 10;

        outerloop:
        for (int j = 0; j < referenceData.getItemCount(); j++) {

            if (referenceData.getY(j).doubleValue() > 0) {
                refItem = referenceData.getDataItem(j); // is refItem found in all sets
                minQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for (; startAt < totalInSampleSet; startAt++) {

                    tempDataset = samplesCollection.getDataset(startAt);
                    tempData = tempDataset.getAllData();
                    // check if refItem q-value is in tempData
                    // if true, check next value
                    if (tempData.indexOf(refItem.getX()) < 0 && (tempData.getY(startAt).doubleValue() > 0)) {
                        isCommon = false;
                        break innerloop;
                    }
                }

                if (startAt == totalInSampleSet && isCommon) {
                    break outerloop;
                }
            }
        }

        //System.out.println("Minimum Common q-value : " + minQvalueInCommon);
    }


    private boolean is0p3qmaxPossible() {

        boolean isCommon = false;
        boolean isYes = false;

        Dataset firstSet = samplesCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = samplesCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        maxQvalueInCommon = 0;

        outerloop:
        for (int j = (referenceData.getItemCount() - 1); j > -1; j--) { // start from end

            refItem = referenceData.getDataItem(j); // is refItem found in all sets

            if (refItem.getYValue() > 0 && refItem.getXValue() < 0.29) {
                maxQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for (; startAt < totalInSampleSet; startAt++) { // for each dataset in Collection, check

                    tempDataset = samplesCollection.getDataset(startAt);
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

                if (startAt == totalInSampleSet && isCommon) {
                    break outerloop;
                }
            }
        }

        isYes = isCommon;

        return isYes;
    }

    /**
     *
     */
    private void findMaximumCommonQvalue() {

        boolean isCommon;

        Dataset firstSet = samplesCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = samplesCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        maxQvalueInCommon = 0;

        outerloop:
        for (int j = (referenceData.getItemCount() - 1); j > -1; j--) {

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            if (refItem.getYValue() > 0) {
                maxQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for (; startAt < totalInSampleSet; startAt++) {

                    tempDataset = samplesCollection.getDataset(startAt);
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

                if (startAt == totalInSampleSet && isCommon) {
                    break outerloop;
                }
            }
        }

        // System.out.println("Maximum Common q-value : " + maxQvalueInCommon);
    }

    public void setMinQvalueInCommon(double value) {
        this.minQvalueInCommon = value;
    }

    public void setMaxQvalueInCommon(double value) {
        this.maxQvalueInCommon = value;
    }


    public void setFirstLastFrame(int firstFrame, int lastFrame) {
        this.firstFrame = firstFrame;
        this.lastFrame = lastFrame;

    }


    public void updateMarkers(Double startValue, Double endValue) {
        frame.getChartPanel().getChart().getXYPlot().clearDomainMarkers();
        Marker marker = new IntervalMarker(startValue, endValue);
        marker.setPaint(new Color(0xDD, 0xFF, 0xDD, 0x80));
        marker.setAlpha(0.5f);
        frame.getChartPanel().getChart().getXYPlot().addDomainMarker(marker, Layer.BACKGROUND);
    }

    private final static class MouseMarker extends MouseAdapter {
        private Marker marker;
        private Double markerStart = Double.NaN;
        private Double markerEnd = Double.NaN;
        private final XYPlot plot;
        private final JFreeChart chart;
        private final ChartPanel panel;
        private JList samplesList;


        public MouseMarker(ChartPanel panel, JList samplesList) {
            this.samplesList = samplesList;
            this.panel = panel;
            this.chart = panel.getChart();
            this.plot = (XYPlot) chart.getPlot();
        }

        private void updateMarker() {
            if (marker != null) {
                plot.removeDomainMarker(marker, Layer.BACKGROUND);
            }
            if (!(markerStart.isNaN() && markerEnd.isNaN())) {
                if (markerEnd > markerStart) {
                    marker = new IntervalMarker(markerStart, markerEnd);
                    marker.setPaint(new Color(0xDD, 0xFF, 0xDD, 0x80));
                    marker.setAlpha(0.5f);
                    plot.addDomainMarker(marker, Layer.BACKGROUND);
                }
            }
        }

        private Double getPosition(MouseEvent e) {
            Point2D p = panel.translateScreenToJava2D(e.getPoint());
            Rectangle2D plotArea = panel.getScreenDataArea();
            XYPlot plot = (XYPlot) chart.getPlot();
            // int mouseX = e.getX();
            // int onscreen = e.getXOnScreen();
            // System.out.println("x = " + mouseX + " onscreen " + onscreen);
            return plot.getDomainAxis().java2DToValue(p.getX(), plotArea, plot.getDomainAxisEdge());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            markerEnd = getPosition(e);

            int size = samplesList.getModel().getSize();
            int trueStart = markerStart.intValue();
            int trueEnd = markerEnd.intValue();

            // set everything within range to true, everything else false
            for (int i = 0; i < size; i++) {
                // sampleFilesModel.get(i).isSelected()
                if ((i < trueStart) || (i > trueEnd)) {
                    ((SampleBufferElement) samplesList.getModel().getElementAt(i)).setSelected(false);
                } else {
                    ((SampleBufferElement) samplesList.getModel().getElementAt(i)).setSelected(true);
                }
            }
            samplesList.repaint();
            updateMarker();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            markerStart = getPosition(e);
            // if key pressed
        }

    }

    public JFreeChart getChart(String title) {
        chart.setTitle(title);
        chart.getTitle().setFont(new java.awt.Font("Times", Font.BOLD, 42));
        chart.getTitle().setPaint(Color.BLACK);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);

        // reset colors for datasets in chart
//        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer(0);
//        int totalSeries = chart.getXYPlot().getSeriesCount();

        return chart;
    }


    public void terminateFrame() {
        try {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
        } catch (Exception ex) {
            System.out.println("Exception: " + ex.getMessage());
        }

    }

    public XYSeriesCollection getPlotMe() {
        return plotMe;
    }

    public ChartPanel getChartPanel() {
        return frame.getChartPanel();
    }

    public double getThreshold() {
        return noSignal;
    }

    public void setWorkingDirectory(WorkingDirectory wkd){
        this.workingDirectory = wkd;
    }
}