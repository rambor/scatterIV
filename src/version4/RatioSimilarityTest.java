package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.xy.*;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import version4.BinaryComparison.ResidualDifferences;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by xos81802 on 20/07/2017.
 */
public class RatioSimilarityTest {

    private Collection inUse;
    private double lowerQLimit, upperQLimit;
    private JPanel panel1;
    private JButton startButton;
    private JButton cancelButton;
    private JProgressBar progressBar1;
    private JPanel plotPanel;
    private JTextField qminField;
    private JTextField qmaxField2;
    private JLabel statusLabel;
    private JPanel topPlotPanel;
    private double maxResidualDataset, maxBottomDataset;
    private double minResidualDataset, minBottomDataset;
    private JPanel bottomPlotPanel;
    public ChartFrame frame;
    public ChartPanel chartPanel;
    private JFreeChart chart;
    //public ArrayList<Ratio> ratioModels;
    public ArrayList<ResidualDifferences> residualDifferencesModel;
    public ArrayList<XYTextAnnotation> residualsLabels;
    public ArrayList<Integer> frameIndices;
    private int totalDatasets, totalDatasetsInUse, totalToCalculate;
    private DefaultXYZDataset residualDataset, ratioDataset;
    private int lags = 12;
    private JFrame mainFrame = new JFrame("SimilarityTest");
    private ChartPanel topChartPanel;
    private ChartPanel bottomChartPanel;

    public RatioSimilarityTest(Collection collectionInUse, double qmin, double qmax) throws Exception {
        residualDifferencesModel = new ArrayList<>();
        inUse = collectionInUse;
        this.lowerQLimit = qmin;
        this.upperQLimit = qmax;
        qminField.setText(String.format(Locale.US, "%.3f", qmin));
        qmaxField2.setText(String.format(Locale.US, "%.3f", qmax));

        totalDatasets = inUse.getTotalDatasets();
        totalDatasetsInUse = 0;
        frameIndices = new ArrayList<>();

        for(int i=0; i<totalDatasets; i++){
            if (inUse.getDataset(i).getInUse()){
                frameIndices.add(inUse.getDataset(i).getId());
                totalDatasetsInUse += 1;
            }
        }

        setLowAndHighQ();

        totalToCalculate = totalDatasetsInUse*(totalDatasetsInUse-1)/2;
        progressBar1.setMaximum(totalToCalculate);
        progressBar1.setValue(0);
        progressBar1.setString("Calculating");

        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                calculateRatios();
            }
        });

        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });

        qminField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("HELLO");
            }
        });
    }

    // calculate pairwise ratio comparision for each in collection
    // make plot

    // find common low-q within limit
    // find common upper-q within limit
    private void setLowAndHighQ() throws Exception {

        int totalDatasets = inUse.getTotalDatasets();
        Dataset refDataset = inUse.getDataset(0);

        for(int i=0; i<totalDatasets; i++){
            refDataset = inUse.getDataset(i);
            if (refDataset.getInUse()){
                break;
            }
        }

        //set low-q
        int totalInRef = refDataset.getAllData().getItemCount();
        boolean allclear = false;
        int stopIndex=0;

        for(int i=0; i<totalInRef; i++){

            Number xvalue = refDataset.getAllData().getX(i);

            if (xvalue.doubleValue() >= lowerQLimit){ // check if xvalue is in all other datasets

                int startJ=1;
                boolean isOK = true;

                for(; startJ<totalDatasets; startJ++){
                    Dataset dataset = inUse.getDataset(startJ);

                    if (dataset.getInUse()){
                        if (dataset.getAllData().indexOf(xvalue) == -1){
                            isOK = false;
                            break; // go to next value
                        }
                    }
                }

                if (startJ == totalDatasets && isOK){ // made it to end
                    allclear =true;
                    stopIndex = i;
                    lowerQLimit = xvalue.doubleValue();
                    break;
                }
            }
        }


        if (!allclear){
                throw new Exception(" No common q-value found amongst selected datasets");
        }

        allclear = false;


        for(int i=(totalInRef-1); i>stopIndex; i--){

            Number xvalue = refDataset.getAllData().getX(i);

            if (xvalue.doubleValue() <= upperQLimit){ // check if xvalue is in all other datasets

                int startJ=1;
                boolean isOK = true;

                for(; startJ<totalDatasets; startJ++){
                    Dataset dataset = inUse.getDataset(startJ);

                    if (dataset.getInUse()){
                        if (dataset.getAllData().indexOf(xvalue) == -1){
                            isOK = false;
                            break; // go to next value
                        }
                    }
                }


                if (startJ == totalDatasets && isOK){ // made it to end
                    allclear =true;
                    upperQLimit = xvalue.doubleValue();
                    break;
                }
            }
        }

        if (!allclear){
            throw new Exception(" No common upper q-value found amongst selected datasets");
        }
    }


    // perform ratio calculation
    private void calculateRatios(){

        this.startButton.setEnabled(false);
        residualDifferencesModel = new ArrayList<>();
        lowerQLimit = Double.parseDouble(qminField.getText());
        upperQLimit = Double.parseDouble(qmaxField2.getText());

        SwingWorker<Boolean, Integer> worker = new SwingWorker<Boolean, Integer>(){

            @Override
            protected Boolean doInBackground() throws Exception {

//                ScheduledExecutorService ratioExecutor = Executors.newScheduledThreadPool(4);
//                List<Future<Ratio>> ratioFutures = new ArrayList<>();
//
//                int order=0;
//                for(int i=0; i<totalDatasets; i++){
//
//                    if (inUse.getDataset(i).getInUse()){
//                        XYSeries ref = inUse.getDataset(i).getAllData();
//                        int refIndex = inUse.getDataset(i).getId();
//
//                        int next = i+1;
//                        for(int j=next; j<totalDatasets; j++){
//
//                            Dataset tar = inUse.getDataset(j);
//                            if (tar.getInUse()){
//                                Future<Ratio> future = ratioExecutor.submit(new CallableRatio(
//                                        ref,
//                                        tar.getAllData(),
//                                        tar.getAllDataError(),
//                                        lowerQLimit,
//                                        upperQLimit,
//                                        refIndex,
//                                        tar.getId(),
//                                        order
//                                ));
//
//                                ratioFutures.add(future);
//                                order++;
//                            }
//                        }
//                    }
//                }
//
//
//                int completed=0;
//                for(Future<Ratio> fut : ratioFutures){
//                    try {
//                        // because Future.get() waits for task to get completed
//                        ratioModels.add(fut.get());
//                        //update progress bar
//                        completed++;
//                        publish(completed);
//                    } catch (InterruptedException | ExecutionException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                ratioExecutor.shutdown();
//
//                try {
//                    ratioExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }

                ScheduledExecutorService diffExecutor = Executors.newScheduledThreadPool(4);
                statusLabel.setText("Status : Calculating Residual Differences");
                progressBar1.setValue(0);
                List<Future<ResidualDifferences>> residualFutures = new ArrayList<>();

                // Residual Differences
                int order=0;
                for(int i=0; i<totalDatasets; i++){

                    if (inUse.getDataset(i).getInUse()){
                        XYSeries ref = inUse.getDataset(i).getAllData();
                        int refIndex = inUse.getDataset(i).getId();

                        List<XYDataItem> refIntensities = Collections.synchronizedList(new ArrayList<>(ref.getItemCount()));
                        // all models are initialized with same value
                        for(int j = 0; j< ref.getItemCount(); j++){
                            refIntensities.add(ref.getDataItem(j));
                        }
                        final List<XYDataItem> THREAD_SAFE_LIST=Collections.unmodifiableList(refIntensities);

                        int next = i+1;
                        for(int j=next; j<totalDatasets; j++){

                            Dataset tar = inUse.getDataset(j);
                            if (tar.getInUse()){
                                Future<ResidualDifferences> dfuture = diffExecutor.submit(new CallableResidualDifferences(
                                        THREAD_SAFE_LIST,
                                        tar.getAllData(),
                                        tar.getAllDataError(),
                                        lowerQLimit,
                                        upperQLimit,
                                        refIndex,
                                        tar.getId(),
                                        lags,
                                        order
                                ));

                                residualFutures.add(dfuture);
                                order++;
                            }
                        }
                    }
                }


                int completed=0;
                for(Future<ResidualDifferences> fut : residualFutures){
                    try {
                        // because Future.get() waits for task to get completed
                        residualDifferencesModel.add(fut.get());
                        //update progress bar
                        completed++;
                        publish(completed);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }

                diffExecutor.shutdown();

                try {
                    diffExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                Collections.sort(residualDifferencesModel, new Comparator<ResidualDifferences>() {
                    @Override public int compare(ResidualDifferences p1, ResidualDifferences p2) {
                        return p1.order - p2.order; // Ascending
                    }
                });

//                Collections.sort(ratioModels, new Comparator<Ratio>() {
//                    @Override public int compare(Ratio p1, Ratio p2) {
//                        return p1.order - p2.order; // Ascending
//                    }
//                });

                makeCharts();
                makeBottomChart();
                return true;
            }

            @Override
            protected void done() {
                boolean status;
                try {
                    // Retrieve the return value of doInBackground.
                    status = get();
                    if (status){
                        statusLabel.setText("Status : Finished");
                    }

                    progressBar1.setValue(0);
                    // sort ratioModels
                } catch (InterruptedException e) {
                    // This is thrown if the thread's interrupted.
                } catch (ExecutionException e) {
                    // This is thrown if we throw an exception
                    // from doInBackground.
                }
            }

            @Override
            protected void process(List<Integer> chunks) {
                // Here we receive the values that we publish().
                // They may come grouped in chunks.
                int mostRecentValue = chunks.get(chunks.size()-1);
                //statusLabel.setText(Integer.toString(mostRecentValue));
                progressBar1.setValue(mostRecentValue);
            }
        };

        worker.execute();

       // for(int i=0;i<ratioModels.size(); i++){
       //     ratioModels.get(i).printTests(Integer.toString(i));
       // }
        this.startButton.setEnabled(true);

//        plotPanel.removeAll();
//        plotPanel.add(chartPanel);

//        frame = new ChartFrame("S(q) PLOT", chart);
//        frame.setContentPane(this.panel1);
//        frame.setPreferredSize(new Dimension(800,600));
//        frame.getChartPanel().setDisplayToolTips(true);
//        //frame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
//        frame.pack();
//        frame.setVisible(true);
//        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }


    private void createChart(){
        chart = ChartFactory.createXYLineChart(
                "",                         // chart title
                "q",                   // domain axis label
                "S(q)",                // range axis label
                new XYSeriesCollection(new XYSeries("temp")),                 // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );
    }


    private void createBottomDataset(){
        ratioDataset = new DefaultXYZDataset();
        maxBottomDataset = -10;
        minBottomDataset = 1000;
        int N = totalDatasetsInUse;

        int index = 0;
        for (int i = 0; i < N; i++) { // row
            double[][] data = new double[3][N];

            for (int j = 0; j < N; j++) { // column
                data[0][j] = frameIndices.get(i)*1;
                data[1][j] = frameIndices.get(j)*1;
                data[2][j] = 0;
            }

            int nonZeroCol = i + 1;
            for(int j=nonZeroCol; j<N; j++){

                ResidualDifferences temp = residualDifferencesModel.get(index);
                //Ratio temp = ratioModels.get(index);
               //
                //double tempStat = temp.getDurbinWatsonStatistic();
                //double tempStat = temp.getShapiroWilkStatistic();
                double tempStat = temp.getLjungBoxStatistic();

//                if (tempStat >= 2.35 || tempStat < 1.65) {
//                    tempStat = 1.5;
//                }

                data[2][j] = tempStat;

                if (tempStat > maxBottomDataset){
                    maxBottomDataset = tempStat;
                }

                if (tempStat < minBottomDataset){
                    minBottomDataset = tempStat;
                }
                index++;
            }

            ratioDataset.addSeries("Series" + i, data);
        }
    }


    private void createResidualsDataset() {
        residualDataset = new DefaultXYZDataset();
        residualsLabels = new ArrayList<>();

        //int N = residualDifferencesModel.size();
        maxResidualDataset = 0;
        minResidualDataset = 100;
        int N = totalDatasetsInUse;

        int index = 0;
        for (int i = 0; i < N; i++) { // row
            double[][] data = new double[3][N];

            for (int j = 0; j < N; j++) { // column
//                data[0][j] = i*1;
//                data[1][j] = j*1;
                data[0][j] = frameIndices.get(i)*1;
                data[1][j] = frameIndices.get(j)*1;
                data[2][j] = 0;
            }

            int nonZeroCol = i + 1;
            for(int j=nonZeroCol; j<N; j++){

                ResidualDifferences temp = residualDifferencesModel.get(index);
                double tempStat = temp.getStatistic();

//                if (tempStat >= 2.35 || tempStat < 1.65) {
//                    tempStat = 1.5;
//                }

//                if (tempStat == 1){
//                    tempStat = 0.5;
//                } else {
//                    tempStat = 0.05;
//                }

//                if (tempStat < 0.77){
//                    tempStat = 0.7;
//                }

                data[2][j] = tempStat;
                System.out.println(" stat " + i + " " + j + " " + tempStat);
                residualsLabels.add(new XYTextAnnotation(String.format("%.2f", temp.getDurbinWatsonStatistic()), frameIndices.get(i), frameIndices.get(j)));

//                if (tempStat > 1.5){
//                    residualsLabels.get(index).setPaint(Color.white);
//                    residualsLabels.get(i).setFont(new Font("SansSerif", Font.BOLD, 9));
//
//                } else {
//                    residualsLabels.get(index).setPaint(Color.gray);
//                    residualsLabels.get(index).setFont(new Font("SansSerif", Font.PLAIN, 9));
//                }

                if (tempStat > maxResidualDataset){
                    maxResidualDataset = temp.getStatistic();
                }

                if (tempStat < minResidualDataset){
                    minResidualDataset = temp.getStatistic();
                }
                index++;
            }

            residualDataset.addSeries("Series" + i, data);
        }
    }



    private void makeBottomChart(){

        this.createBottomDataset();

        NumberAxis xAxis = new NumberAxis("frame");
        NumberAxis yAxis = new NumberAxis("frame");
        XYPlot plot = new XYPlot(ratioDataset, xAxis, yAxis, null);
        XYBlockRenderer r = new XYBlockRenderer();


        // max for DurbinWatson is 4
        SpectrumPaintScale ps = new SpectrumPaintScale(minBottomDataset, maxBottomDataset);
        r.setPaintScale(ps);
        r.setBlockHeight(1.0f);
        r.setBlockWidth(1.0f);
        plot.setRenderer(r);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        JFreeChart chart = new JFreeChart(
                "",
                JFreeChart.DEFAULT_TITLE_FONT,
                plot,
                false
        );

        NumberAxis scaleAxis = new NumberAxis("Ljung-Box Scale");
        scaleAxis.setAxisLinePaint(Color.white);
        scaleAxis.setTickMarkPaint(Color.white);

        PaintScaleLegend legend = new PaintScaleLegend(ps, scaleAxis);
        legend.setSubdivisionCount(128);
        legend.setAxisLocation(AxisLocation.TOP_OR_RIGHT);
        legend.setPadding(new RectangleInsets(10, 10, 10, 10));
        legend.setStripWidth(20);
        legend.setPosition(RectangleEdge.RIGHT);
        legend.setBackgroundPaint(Color.WHITE);
        chart.addSubtitle(legend);

        chart.setBackgroundPaint(Color.white);

       // ChartFrame topChartFrame  = new ChartFrame("Ratio", chart);

        bottomChartPanel = new ChartPanel(chart);

        bottomPlotPanel.removeAll();
        bottomPlotPanel.add(bottomChartPanel);

//        bottomPlotPanel.revalidate();
        panel1.revalidate();
//        mainFrame.revalidate();

    }

    /**
     * residuals chart
     * Durbin-Watson
     */
    private void makeCharts(){
        this.createResidualsDataset();

        NumberAxis xAxis = new NumberAxis("frame");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        NumberAxis yAxis = new NumberAxis("frame");
        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        XYPlot plot = new XYPlot(residualDataset, xAxis, yAxis, null);
        XYBlockRenderer r = new XYBlockRenderer();


//        for(int i=0; i<residualsLabels.size(); i++){
//            //residualsLabels.get(i).setPaint(Color.gray);
//            //residualsLabels.get(i).setFont(new Font("SansSerif", Font.PLAIN, 9));
//            plot.addAnnotation(residualsLabels.get(i));
//        }

        // max for DurbinWatson is 4
        //SpectrumPaintScale ps = new SpectrumPaintScale(1.5, 2.3);
        //SpectrumPaintScale ps = new SpectrumPaintScale(minResidualDataset - (0.07*minResidualDataset), maxResidualDataset+0.03*maxResidualDataset);
        SpectrumPaintScale ps = new SpectrumPaintScale(0, 4);

        r.setPaintScale(ps);
        r.setBlockHeight(1.0f);
        r.setBlockWidth(1.0f);
        plot.setRenderer(r);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

//        final Marker start = new ValueMarker(2,2);
//        start.setPaint(Color.green);
//        start.setLabel("Bid Start Price");
//        start.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);
//        start.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
//        plot.addRangeMarker(start);


        JFreeChart chart = new JFreeChart(
                "Residuals Similarity Plot",
                JFreeChart.DEFAULT_TITLE_FONT,
                plot,
                false
        );

        NumberAxis scaleAxis = new NumberAxis("Durbin-Watson Scale");
        scaleAxis.setAxisLinePaint(Color.white);
        scaleAxis.setTickMarkPaint(Color.white);

        PaintScaleLegend legend = new PaintScaleLegend(ps, scaleAxis);
        legend.setSubdivisionCount(128);
        legend.setAxisLocation(AxisLocation.TOP_OR_RIGHT);
        legend.setPadding(new RectangleInsets(10, 10, 10, 10));
        legend.setStripWidth(20);
        legend.setPosition(RectangleEdge.RIGHT);
        legend.setBackgroundPaint(Color.WHITE);
        chart.addSubtitle(legend);
        chart.setBackgroundPaint(Color.white);

        // ChartFrame topChartFrame  = new ChartFrame("Ratio", chart);

        topChartPanel = new ChartPanel(chart);

        topPlotPanel.removeAll();
        topPlotPanel.add(topChartPanel);
        //panel1.revalidate();
    }


    public void makePlot(){

        residualDifferencesModel.clear();

       // mainFrame.revalidate();
        mainFrame.setContentPane(this.panel1);
        mainFrame.setPreferredSize(new Dimension(800,600));
        mainFrame.pack();
        mainFrame.setVisible(true);
        mainFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    }


    private static class SpectrumPaintScale implements PaintScale {

        private static final float H1 = 0f;
        private static final float H2 = 1f;
        private final double lowerBound;
        private final double upperBound;

        public SpectrumPaintScale(double lowerBound, double upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        @Override
        public double getLowerBound() {
            return lowerBound;
        }

        @Override
        public double getUpperBound() {
            return upperBound;
        }

        @Override
        public Paint getPaint(double value) {
            float saturation =1f;
            float scaledValue = (float) (value / (getUpperBound() - getLowerBound()));
            float scaledH = H1 + scaledValue * (H2 - H1);

            // HSB white is s: 0, b: 1
            if (value <= getLowerBound() || value >= getUpperBound()){
                saturation = 0;
                //scaledH = H1 + (float) (getLowerBound() / (getUpperBound() - getLowerBound())) * (H2 - H1);
                scaledH = 0;
            }

            //Color tempColor = Color.getHSBColor(scaledH, saturation, 1f);
            return Color.getHSBColor(scaledH, saturation, 1f);
        }
    }

}




