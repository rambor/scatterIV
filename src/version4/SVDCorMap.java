package version4;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.xy.*;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import version4.plots.DWSimilarityPlot;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class SVDCorMap extends SwingWorker<String, Object> {

    private JPanel panel1;
    private JPanel plotPanel;
    private JFreeChart chart;

    private double qmin;
    private double qmax;
    private XYSeriesCollection datasets;
    private int totalSets;
    private XYSeries singularValuesSeries;
    private XYSeriesCollection plotMe;
    private DefaultXYZDataset corMap = new DefaultXYZDataset();
    private int eightypercent;
    private int ninetypercent;
    private double ninetypercentValue;
    private double eightypercentValue;
    private DMatrixRMaj c_matrix;
    private int startIndex;

    public SVDCorMap(double qmin, double qmax, XYSeriesCollection datasets, int startIndex) {
        this.qmin = qmin;
        this.qmax = qmax;
        this.datasets = datasets;
        totalSets = datasets.getSeriesCount();
        this.startIndex = startIndex;
    }

    @Override
    protected String doInBackground() throws Exception {
        // create mean subtracted sets
        XYSeriesCollection meanCenteredSets = new XYSeriesCollection();
        XYDataItem tempItem;

        // need a collection of common q-values for column_wise
        XYSeries tempSeries = datasets.getSeries(0);
        ArrayList<Number> qvalues = new ArrayList<>();
        int totalInSeries = tempSeries.getItemCount();
        for(int j=0; j<totalInSeries; j++){
            tempItem = tempSeries.getDataItem(j);
            if (tempItem.getXValue() >= qmin && tempItem.getXValue() <= qmax){
                qvalues.add(tempItem.getX());
            }
        }
        // now check the other series that are remaining
        // for each q-value, check if in each dataset
        ArrayList<Number> qs_to_use = new ArrayList<>();
        for (Number num : qvalues) {
            int qcount=1;
            for (int i=1; i<totalSets; i++){
                tempSeries = datasets.getSeries(i);
                if (tempSeries.indexOf(num) > -1 ){
                    qcount++;
                }
            }

            if (qcount == totalSets){
                qs_to_use.add(num);
            }
        }

        ArrayList<Double> averages = new ArrayList<Double>();
        for (Number num : qs_to_use) {
            double qcount=0.0;
            double sum = 0.0;
            for (int i=1; i<totalSets; i++){
                tempSeries = datasets.getSeries(i);
                int index = tempSeries.indexOf(num);
                if (index > -1 ){
                    sum += tempSeries.getY(index).doubleValue();
                    qcount+=1.0;
                }
            }
            averages.add(sum/qcount);
        }

        // center the data
        for(int i=0; i<totalSets; i++){
            // center the data
            meanCenteredSets.addSeries(new XYSeries("mean centered " + Integer.toString(i)));
            XYSeries meanSeries = meanCenteredSets.getSeries(i);
            tempSeries = datasets.getSeries(i);

            for (int j=0; j< qs_to_use.size(); j++) {
                int index = tempSeries.indexOf(qs_to_use.get(j));
                if (index > -1 ){
                    tempItem = tempSeries.getDataItem(index);
                    meanSeries.add(tempItem.getXValue(), (tempItem.getYValue() - averages.get(j)));
                }
            }
        }

        // create covariance matrix
        //final int rows = meanCenteredSets.getSeries(0).getItemCount();
        final int rows = totalSets;
        final int cols = meanCenteredSets.getSeries(0).getItemCount();
        //DMatrixRMaj matrixA = new DMatrixRMaj(rows, totalSets);
        DMatrixRMaj matrixA = new DMatrixRMaj(totalSets, cols);

        // fill first column and use q-values as standard for subsequent rows
        XYSeries refSeries = meanCenteredSets.getSeries(0);
        for(int j=0; j<cols; j++){
            tempItem = refSeries.getDataItem(j);
            matrixA.set(0, j, tempItem.getYValue()); // could try q*I(q) also
        }

        // add remaining sets but check their q-values are in reference
        /*
         * columns represent different datasets
         * rows represent different q values
         */
//        int row = 1;
        for(int s=1; s<totalSets; s++){
             tempSeries = meanCenteredSets.getSeries(s);
            for(int j=0; j<cols; j++){
//                int indexOf = tempSeries.indexOf(refSeries.getX(j));
//                if (indexOf > -1){
                    matrixA.set(s, j, tempSeries.getDataItem(j).getYValue());
//                } else { // interpolate
//                    Double[] results = Functions.interpolate(tempSeries, refSeries.getX(j).doubleValue(), 1);
//                    matrixA.set(row, j, results[1]);
//                    System.out.println("Not found! Interpolating q : " + refSeries.getX(j));
//                }
            }
//            row++;
        }

        c_matrix = new DMatrixRMaj(totalSets, totalSets);

        int totalCalc = totalSets-1;
        c_matrix.set(totalCalc,totalCalc,2);
        DMatrixRMaj subMatrixA = new DMatrixRMaj(2, cols);;
        for(int i=0; i<totalCalc; i++){

            c_matrix.set(i,i,2);

            for(int j=i+1; j<totalSets; j++){
                //perform SVD on subMatrix
                int length = j - i + 1;
                subMatrixA.reshape(length, cols);
                // extract start_row, end_row +1, start_col, end_col_1
                CommonOps_DDRM.extract(matrixA,i,j+1, 0,rows, subMatrixA, 0,0);
                SingularValueDecomposition_F64<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(length, rows, false, false, false);
                // A = U*W*V_t
                try {
                    svd.decompose(subMatrixA);
                } catch (Exception e){
                    System.out.println("Matrix inversion exception in svdReduce ");
                }

                double[]  W = svd.getSingularValues();
                //DMatrixRMaj W = svd.getW(null);
                double firstss = W[0];

                double sigma_sum = 0.0;
                for(int s=0; s < length; s++){
                    sigma_sum += W[s];
                }
//                c_matrix.set(i,j,2);
//                c_matrix.set(j,i,firstss/sigma_sum);
                c_matrix.set(i,j,firstss/sigma_sum);
                c_matrix.set(j,i,2);
            }
        }

        corMap = new DefaultXYZDataset();

        for(int c=0; c<totalSets; c++){
            int currentIndex = startIndex + c;
            double[][] dataset = new double[3][totalSets];
            for (int j=0; j<totalSets; j++) { // column
                dataset[0][j] = currentIndex*1.0d;     // x-coordinate
                dataset[1][j] = (startIndex + j)*1.0d; // y-coordinate
//                dataset[0][j] = (startIndex + j)*1.0d;     // x-coordinate
//                dataset[1][j] = currentIndex*1.0d; // y-coordinate
                dataset[2][j] = c_matrix.get(c,j); // this is what adds color (scale)
            }
            corMap.addSeries("Series_" + c, dataset);
        }

        //c_matrix.print();

        return null;
    }


    public void createPlot(){

        NumberAxis xAxis = new NumberAxis("frame");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        xAxis.setRange(startIndex, startIndex+c_matrix.numRows);
        xAxis.setAutoRangeIncludesZero(false);

        NumberAxis yAxis = new NumberAxis("frame");
        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        yAxis.setRange(startIndex, startIndex+c_matrix.numRows);
        yAxis.setAutoRangeIncludesZero(false);

        double minZ=0.0;
        for(int i=0; i<corMap.getSeriesCount(); i++){
            for(int j=0; j<corMap.getItemCount(i); j++){
                if (corMap.getZValue(i,j) > minZ){
                    minZ = corMap.getZValue(i,j);
                }
            }
        }

        XYPlot plot = new XYPlot(corMap, xAxis, yAxis, null);
        XYBlockRenderer rlockRenderer = new XYBlockRenderer();

        // max for DurbinWatson is 4
        SVDCorMap.SpectrumPaintScale ps = new SVDCorMap.SpectrumPaintScale(0, 1);

        rlockRenderer.setPaintScale(ps);
        rlockRenderer.setBlockHeight(1.0f);
        rlockRenderer.setBlockWidth(1.0f);

        plot.setRenderer(rlockRenderer);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        NumberAxis scaleAxis = new NumberAxis("Scale");
        scaleAxis.setAxisLinePaint(Color.white);
        scaleAxis.setTickMarkPaint(Color.white);

        chart = new JFreeChart(
                "CorMap | q-range : " + Constants.ThreeDecPlace.format(qmin) + " to " + Constants.ThreeDecPlace.format(qmax) ,
                JFreeChart.DEFAULT_TITLE_FONT,
                plot,
                false
        );

        PaintScaleLegend legend = new PaintScaleLegend(ps, scaleAxis);
        legend.setSubdivisionCount(128);
        legend.setAxisLocation(AxisLocation.TOP_OR_RIGHT);
        legend.setPadding(new RectangleInsets(10, 10, 10, 10));
        legend.setStripWidth(20);
        legend.setPosition(RectangleEdge.RIGHT);
        legend.setBackgroundPaint(Color.WHITE);
        chart.addSubtitle(legend);
        chart.setBackgroundPaint(Color.white);

        ChartPanel outPanel = new ChartPanel(chart);
        // selectedRegionsChartPanel.setMouseZoomable(false);
        outPanel.setHorizontalAxisTrace(true);
        outPanel.setVerticalAxisTrace(true);
        outPanel.setDisplayToolTips(true);

        outPanel.addChartMouseListener(new ChartMouseListener() {

            @Override
            public void chartMouseClicked(ChartMouseEvent chartMouseEvent) {
                ChartEntity ce = chartMouseEvent.getEntity();
                if (ce instanceof XYItemEntity){
                    XYItemEntity e = (XYItemEntity) ce;
                    XYDataset d = e.getDataset();
                    int series = e.getSeriesIndex();
                    int index = e.getItem();

                    Number trueStart = d.getX(series, index);
                    Number trueEnd = d.getY(series,index);

                    if (trueEnd.doubleValue() > trueStart.doubleValue()){
                        chartMouseEvent.getChart().setTitle("Selected frames : " + Integer.toString((int)d.getX(series, index).intValue()) + " to " + Integer.toString((int)d.getY(series, index).intValue()));
                        // update markers on upperplot
                    } else {
                        chartMouseEvent.getChart().setTitle("Selected frames : " + Integer.toString((int)d.getY(series, index).intValue()) + " to " + Integer.toString((int)d.getX(series, index).intValue()));
                    }
                }
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent chartMouseEvent) {

            }
        });


        plotPanel.add(outPanel);

        JFrame frame = new JFrame("Plot");
        frame.setContentPane(this.panel1);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    }

    public static class SpectrumPaintScale implements PaintScale {
        //        private static final float H1 = 0f;
//        private static final float H2 = 1f;
        private final double lowerBound;
        private final double upperBound;

        public SpectrumPaintScale(double lowerBound, double upperBound) {
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            // 2/2 should be red => HSB(0,1,1)
            // 0/2 should be blue => HSB(200,1,1)
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

            float saturation = 1f;

            // float scaledH = H1 + scaledValue * (H2 - H1);
            float scaledH =  (float)(value/getUpperBound());
            //float scaledH =  (float)((value-getLowerBound())/(getUpperBound()-getLowerBound()));

            // HSB white is s: 0, b: 1
            if (value == getLowerBound() || value == getUpperBound()){
                saturation = 0;
                //scaledH = H1 + (float) (getLowerBound() / (getUpperBound() - getLowerBound())) * (H2 - H1);
                scaledH = 0;
            } else if (value < getLowerBound() || value > getUpperBound()){
                saturation = 0;
                scaledH = 0;
            }

            Color minColor = Color.red;
            Color maxColor = Color.blue;


                float[] startHSB = Color.RGBtoHSB(minColor.getRed(), minColor.getGreen(), minColor.getBlue(), null);
                float[] endHSB = Color.RGBtoHSB(maxColor.getRed(), maxColor.getGreen(), maxColor.getBlue(), null);

                float brightness = (startHSB[2] + endHSB[2]) / 2;
                saturation = (startHSB[1] + endHSB[1]) / 2;

                if (value > getUpperBound()){
                    saturation = 0.0f;
                    brightness = 1.0f;
                }


                float hueMax = 0;
                float hueMin = 0;
                // if (startHSB[0] > endHSB[0]) {
                hueMax = startHSB[0];// w w w  .  java2  s. com
                hueMin = endHSB[0];
                // } else {
                // hueMin = startHSB[0];
                // hueMax = endHSB[0];
                // }

                float hue = ((hueMax - hueMin) * (1.0F - scaledH)) + hueMin;

                return Color.getHSBColor(hue, saturation, brightness);

            //return Color.getHSBColor(scaledH, saturation, 1f);
        }
    }
}



