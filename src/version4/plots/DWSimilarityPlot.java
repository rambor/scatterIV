package version4.plots;

import org.ejml.data.DMatrix;
import org.ejml.data.DMatrixD1;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;
import org.junit.Assert;
import version4.Constants;
import version4.SEC.SECFile;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DWSimilarityPlot  extends SwingWorker<Void, Integer> {

    private int startIndex, endIndex;
    private JFreeChart chart;
    private double qmin, qmax;
    private DefaultXYZDataset residualDataset;
    private SECFile secfile;
    private int totalDatasetsInUse;

    private JProgressBar progressBar;
    private JLabel status;

    public DWSimilarityPlot(int startFrameIndex, int endFrameIndex, double qmin, double qmax, SECFile secfile, JLabel status, JProgressBar bar) {
            /*
             * get indices of interest and cast each to double using parseDouble
             */
            this.startIndex = startFrameIndex;
            this.endIndex = endFrameIndex+1;
            this.totalDatasetsInUse = endIndex - startIndex;
            this.qmin = qmin;
            this.qmax = qmax;
            this.secfile = secfile;

            this.progressBar = bar;
            this.status = status;
    }

    @Override
    protected Void doInBackground() throws Exception {
        // assemble the frames for subtraction

        // translate the qmin and qmax to indices to load from SECFILE
        int startqAt = secfile.getClosestIndex(qmin); //inclusive of startq
        int endqAt = secfile.getClosestIndex(qmax);  // exclusive of endq

        int totalq = endqAt - startqAt;
        int total = endIndex - startIndex;

        DMatrixRMaj data = new DMatrixRMaj(total, totalq);
        DMatrixRMaj errors = new DMatrixRMaj(total, totalq);

        // populate matrix
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(false);
        status.setText("Assembling Data Matrix");

        int row=0;
        for(int i=startIndex; i<endIndex; i++){
            ArrayList<Double> frame = secfile.getSubtractedFrameAt(i);
            ArrayList<Double> eframe = secfile.getSubtractedErrorAtFrame(i);
            int column=0;
            for(int r=startqAt; r<endqAt; r++){
                data.add(row, column, frame.get(r)); // row major, row is the entire frame between q-min and q-max
                errors.add(row, column, eframe.get(r)); // row major, row is the entire frame between q-min and q-max
                column++;
            }
            row++;
        }

        /*
         * calculate residuals
         */
        status.setText("Calculating Durbin-Watson score");
        DMatrixRMaj ref = new DMatrixRMaj(1,totalq);
        DMatrixRMaj tar = new DMatrixRMaj(1,totalq);
        DMatrixRMaj tarE = new DMatrixRMaj(1,totalq);
        DMatrixRMaj residual = new DMatrixRMaj(1,totalq);

        double dw,valueE, tarI, invE;
        residualDataset = new DefaultXYZDataset();

        for(int i=0; i<totalDatasetsInUse; i++){
            double[][] dataset = new double[3][totalDatasetsInUse];
            int currentIndex = startIndex + i;
            for (int j = 0; j < totalDatasetsInUse; j++) { // column
                dataset[0][j] = currentIndex*1.0d;     // x-coordinate
                dataset[1][j] = (startIndex + j)*1.0d; // y-coordinate
                dataset[2][j] = 0; // this is what adds color (scale)
            }

            CommonOps_DDRM.extractRow(data, i, ref);

            int next=i+1;
            for(int j=next; j<totalDatasetsInUse; j++){ // calculate residual
                CommonOps_DDRM.extractRow(data, j, tar);
                /*
                 * need to scale to reference
                 */
                CommonOps_DDRM.extractRow(errors, j, tarE);
                double scale_numerator=0, scale_denominator=0;
                for(int q=0; q<totalq; q++){
                    valueE = 1.0/(tarE.get(0,q));
                    invE = valueE*valueE;
                    tarI = tar.get(0,q);
                    scale_numerator += tarI*ref.get(0,q)*invE;
                    scale_denominator += tarI*tarI*invE;
                }

                double scaleFactor = scale_numerator/scale_denominator;
                CommonOps_DDRM.scale(scaleFactor, tar);
                CommonOps_DDRM.subtract(ref, tar, residual); // make residual
                // calculate DW statistic
                dw = calculateDurbinWatson(residual);
                dataset[2][j] = dw;
            }
            residualDataset.addSeries("Series_" + i, dataset);
        }

        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(false);
        return null;
    }

    public void printDataSize(){
        System.out.println("TOTAL :: " + residualDataset.getSeriesCount());
    }

    /**
     * d = 2 means no autocorrelation
     * d-value should always lie between 0 and 4
     * use the absolute value so that the reporting function is U shaped liked a squared value
     */
    private double calculateDurbinWatson(DMatrixD1 testSeries){

        int total = testSeries.numCols;
        double value = testSeries.get(0,0);
        double numerator=0, diff;
        double denominator = value*value;

        for(int i=1; i< total; i++){
            value = testSeries.get(0,i);
            diff = value - testSeries.get(0,i-1); // x_(t) - x_(t-1)
            numerator += diff*diff;
            denominator += value*value; // sum of (x_t)^2
        }

        value = (numerator/denominator);

        if ((value > 1.9 && value < 2.1)){
            value = 0;
        } else {
            value = Math.abs(value-2.0);
        }

        return value + 2;
    }

    public int getTotalFrames(){ return secfile.getTotalFrames();}


    public XYPlot createPlot(){

        NumberAxis xAxis = new NumberAxis("frame");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        xAxis.setRange(startIndex, endIndex);
        xAxis.setAutoRangeIncludesZero(false);

        NumberAxis yAxis = new NumberAxis("frame");
        yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        yAxis.setRange(startIndex, endIndex);
        yAxis.setAutoRangeIncludesZero(false);

        double minZ=0.0;
        for(int i=0; i<residualDataset.getSeriesCount(); i++){
            for(int j=0; j<residualDataset.getItemCount(i); j++){
                if (residualDataset.getZValue(i,j) > minZ){
                    minZ = residualDataset.getZValue(i,j);
                }
            }
        }

        XYPlot plot = new XYPlot(residualDataset, xAxis, yAxis, null);
        XYBlockRenderer rlockRenderer = new XYBlockRenderer();

        // max for DurbinWatson is 4
        SpectrumPaintScale ps = new SpectrumPaintScale(0, 4);

        rlockRenderer.setPaintScale(ps);
        rlockRenderer.setBlockHeight(1.0f);
        rlockRenderer.setBlockWidth(1.0f);

        plot.setRenderer(rlockRenderer);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);

        NumberAxis scaleAxis = new NumberAxis("Durbin-Watson Scale");
        scaleAxis.setAxisLinePaint(Color.white);
        scaleAxis.setTickMarkPaint(Color.white);

        chart = new JFreeChart(
                "Residuals Similarity Plot | range : " + Constants.ThreeDecPlace.format(qmin) + " to " + Constants.ThreeDecPlace.format(qmax) ,
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

        return chart.getXYPlot();
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

            float saturation =1f;

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

            return Color.getHSBColor(scaledH, saturation, 1f);
        }
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        progressBar.setValue(i);
        super.process(chunks);
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
}
