package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * Created by robertrambo on 14/01/2016.
 */
public class RcXSectionalPlot {

    private JFreeChart chart;
    private JFreeChart residualsChart;
    private CombinedDomainXYPlot combinedPlot;

    private JLabel manualFile;
    private JLabel manualRcLimits;
    private JPanel fittedPanel;
    private JPanel RcPanel;
    private JPanel DetailsPanel;
    private JPanel lowerPanel;
    private JSpinner spinnerLow;
    private JSpinner spinnerHigh;
    private JLabel valueLabel;
    private Dataset dataset;
    private String workingDirectoryName;

    private int maxCount;

    private double rc, i_zero, rcError;
    private Stroke stroke;

    private static XYSeriesCollection rcDatasetCollection;
    private static XYSeriesCollection residualsRcDatasetCollection;
    private XYSeries calcPoints;
    private XYSeries original;
    private int priorIndex;

    private XYLineAndShapeRenderer renderer1;
    private XYLineAndShapeRenderer renderer2;
    private Color datasetColor;


    public RcXSectionalPlot(Dataset data, String dir){

        dataset = data;
        rcDatasetCollection = new XYSeriesCollection();
        residualsRcDatasetCollection = new XYSeriesCollection();
        workingDirectoryName = dir;
        stroke = dataset.getStroke();
        datasetColor = dataset.getColor();

        this.createDatasets();

        RcPanel.setBackground(dataset.getColor());
        spinnerHigh.setValue(maxCount);

        String filenametruncated = (dataset.getFileName().length() > 23) ? dataset.getFileName().substring(0, 23)+ "..." : dataset.getFileName();
        manualFile.setText(filenametruncated);


        manualFile.setForeground(dataset.getColor());
        Font fileFont = manualFile.getFont();
        Font boldFileFont = new Font(fileFont.getFontName(), Font.BOLD, fileFont.getSize());
        manualFile.setFont(boldFileFont);

        spinnerLow.setValue(1);
        //spinnerRcH.setValue(maxCount); //last value of original

        spinnerLow.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent evt){
                rcSpinnerLChanged();
            }
        });

        spinnerHigh.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent evt){
                rcSpinnerHChanged();
            }
        });
    }

    public void createPlots(){
        JFrame frame = new JFrame("Cross-Sectional Rg Plot");

        chart = ChartFactory.createXYLineChart(
                "",                // chart title
                "",                    // domain axis label
                "ln I(q)",                  // range axis label
                rcDatasetCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );


        residualsChart = ChartFactory.createXYLineChart(
                "",                // chart title
                "",                    // domain axis label
                "residuals",                  // range axis label
                residualsRcDatasetCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );

        final XYPlot residuals = residualsChart.getXYPlot();
        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("ln [I(q)]");
        residualsChart.removeLegend();

        Font fnt = new Font("SansSerif", Font.BOLD, 15);
        domainAxis.setLabelFont(fnt);
        rangeAxis.setLabelFont(fnt);
        domainAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);
        String quote = "q\u00B2 (\u212B \u207B\u00B2)";
        domainAxis.setLabel(quote);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        residuals.setDomainAxis(domainAxis);
        plot.setBackgroundPaint(null);
        residuals.setBackgroundPaint(null);
        renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(false);
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesShapesVisible(0, true);
        renderer1.setSeriesShapesVisible(0, false);
        renderer1.setSeriesPaint(0, Color.red);
        renderer1.setSeriesStroke(0, stroke);

        renderer1.setSeriesPaint(1, datasetColor);
        renderer1.setSeriesShape(1, new Ellipse2D.Double(-4, -4, 8.0, 8.0));
        renderer1.setSeriesOutlinePaint(1, datasetColor);
        renderer1.setSeriesOutlineStroke(1, stroke);


        renderer2 = (XYLineAndShapeRenderer) residuals.getRenderer();
        renderer2.setBaseShapesVisible(true);
        renderer2.setBaseShapesFilled(false);
        renderer2.setSeriesLinesVisible(0, false);
        renderer2.setSeriesShapesVisible(1, true);
        renderer2.setSeriesShapesVisible(1, false);
        renderer2.setSeriesPaint(1, Color.red);
        renderer2.setSeriesStroke(1, stroke);
        renderer2.setSeriesPaint(0, Color.BLACK);
        renderer2.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8.0, 8.0));


       // chart.removeLegend();
       // ChartFrame chartframe = new ChartFrame("", chart);
       // ChartFrame chartframeR = new ChartFrame("", residualsChart);
       // fittedPanel.add(chartframe.getContentPane());
       // residualsPanel.add(chartframeR.getContentPane());

        combinedPlot = new CombinedDomainXYPlot(new NumberAxis("2"));
        combinedPlot.setDomainAxis(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.add(plot, 1);
        combinedPlot.add(residuals, 1);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);

        JFreeChart combChart = new JFreeChart("", JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);
        combChart.removeLegend();
        combChart.setBackgroundPaint(Color.WHITE);
        ChartFrame chartframe = new ChartFrame("", combChart);
        fittedPanel.add(chartframe.getContentPane());
        updateLimits(original.getMinX(), original.getMaxX());

        frame.setContentPane(this.RcPanel);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void updateLimits(double first, double last){
        String qRgLimits =
                "   q\u00D7Rc Limits :: " + Constants.ThreeDecPlace.format(Math.sqrt(first)*rc) + " to " + Constants.ThreeDecPlace.format(Math.sqrt(last)*rc) + "     ";
        manualRcLimits.setText(qRgLimits);
    }

    private void createDatasets(){
        double slope = 0.0, intercept = 0.0, errorSlope = 0.0, errorIntercept = 0.0;
        calcPoints = new XYSeries(dataset.getFileName());
        original = new XYSeries(dataset.getFileName()+"_original");
        //XYSeries errors = new XYSeries(dataset.getFileName()+"_error");
        XYSeries residuals = new XYSeries(dataset.getFileName());
        XYSeries yIsZero = new XYSeries("zero");
        XYSeries tempData = dataset.getOriginalPositiveOnlyData();
        XYSeries error = dataset.getOriginalPositiveOnlyError();

        maxCount = tempData.getItemCount();

        double[] x_data = new double[maxCount];
        double[] y_data = new double[maxCount];
        double[] w_data = new double[maxCount];

        double xvalue, yvalue, tempy;

        //transform data for x-sectional Rc
        for(int i=0; i < maxCount; i++){
            double tempx = tempData.getX(i).doubleValue();
            xvalue = tempx*tempx;
            //xvalue=Math.pow(tempData.getX(i).doubleValue(), 2);
            tempy = tempData.getY(i).doubleValue();
            //q^2
            x_data[i]=xvalue;
            //ln(q*I)
            yvalue=Math.log(tempy*tempx);
            y_data[i]=yvalue;
            //ln(y)*error*y
            w_data[i]=yvalue*error.getY(i).doubleValue()*tempy;
            original.add(xvalue, yvalue);
        }
        //perform least squares fit
        //Preliminary fit
        double[] par = Functions.leastSquares(x_data, y_data);
        double m = par[0];
        double b = par[1];

        rc = Math.sqrt(-2.0*m);
        i_zero = Math.exp(b);
        //Calculate line using first and last points for plotting

        calcPoints.add(x_data[0], m*x_data[0]+b);
        calcPoints.add(x_data[maxCount-1], m*x_data[maxCount-1]+b);

        rcDatasetCollection.addSeries(calcPoints);
        rcDatasetCollection.addSeries(original);

        for (int v=0; v < maxCount; v++) {
            residuals.add(x_data[v], y_data[v]-(m*x_data[v]-b));
            yIsZero.add(x_data[v], 0.0);
        }

        residualsRcDatasetCollection.addSeries(residuals);
        residualsRcDatasetCollection.addSeries(yIsZero);
    }

    private void setPriorIndex(int index){
        this.priorIndex = index;
    }

    private int getPriorIndex(){
        return priorIndex;
    }

    public void rcSpinnerLChanged(){

        int current = (Integer) spinnerLow.getValue() - 1;

        if (current < 0){
            spinnerLow.setValue(1);
            ((ScatterSpinner)spinnerLow).setPriorIndex(1);
        } else {
            // add or remove values to guinierDataset
            int direction = (Integer) spinnerLow.getValue() - ((ScatterSpinner)spinnerLow).getPriorIndex();
            int itemCount = 0;
            // if direction is negative, add point to start of GuinierSeries
            if (direction < 0){
                if (direction == -1) {
                    XYDataItem tempXY = dataset.getOriginalPositiveOnlyData().getDataItem(current);
                    double q2 = tempXY.getXValue()*tempXY.getXValue();
                    double lnI = Math.log(tempXY.getXValue()*tempXY.getYValue());
                    original.add(q2, lnI);
                } else {
                    // keep adding points upuntil currentValue
                    int start = ((ScatterSpinner)spinnerLow).getPriorIndex() - 2;
                    int stop = ((Integer) spinnerLow.getValue()).intValue() - 1;
                    for(int i=start; i >= stop; i--){
                        XYDataItem tempXY = dataset.getOriginalPositiveOnlyData().getDataItem(i);
                        double q2 = tempXY.getXValue()*tempXY.getXValue();
                        double lnI = Math.log(tempXY.getXValue()*tempXY.getYValue());
                        original.add(q2, lnI);
                    }

                }
                itemCount = original.getItemCount();
            } else if (direction > 0){
                if (direction == 1){
                    original.remove(0);
                } else {
                    int limit = ((Integer) spinnerLow.getValue()).intValue() - ((ScatterSpinner)spinnerLow).getPriorIndex();
                    for (int i = 0; i < limit; i++){
                        original.remove(0);
                    }
                }
                itemCount = original.getItemCount();
            }

            maxCount = itemCount;
            ((ScatterSpinner)spinnerLow).setPriorIndex((Integer) spinnerLow.getValue());
            replotRc();
        }
    }

    public void rcSpinnerHChanged(){

        int current = (Integer) spinnerHigh.getValue() - 1;
        int totalValues = dataset.getOriginalPositiveOnlyData().getItemCount();
        if (current+1 >= totalValues){
            spinnerHigh.setValue(totalValues);
        }

        // add or remove values to guinierDataset
        int direction = (Integer) spinnerHigh.getValue() - ((ScatterSpinner)spinnerHigh).getPriorIndex();
        int itemCount;
        // if direction is positive, add point to Guinier Series
        if (direction < 0){
            // recalculate slope and intercept
            if (direction == -1){
                original.remove(maxCount - 1 );
            } else {
                //How many points to remove
                // priorValue - startSpinner
                // 21 - 13 => 8 + 1 == size of array
                // stopValue - startSpinner
                // 17 - 13
                int stop = ((Integer) spinnerHigh.getValue()).intValue();
                //int size = original.getItemCount();
                int start = ((ScatterSpinner)spinnerHigh).getPriorIndex() - maxCount;
                XYDataItem tempXY;
                double q2, lnI;
                original.clear();
                for(int i=start; i< stop; i++){
                    tempXY = dataset.getOriginalPositiveOnlyData().getDataItem(i);
                    q2 = tempXY.getXValue()*tempXY.getXValue();
                    lnI = Math.log(tempXY.getYValue()*tempXY.getXValue());
                    original.add(q2, lnI);
                }
            }
        } else if (direction > 0){
            double q2, lnI;
            XYDataItem tempXY;
            if (direction == 1){
                tempXY = dataset.getOriginalPositiveOnlyData().getDataItem(current);
                q2 = tempXY.getXValue()*tempXY.getXValue();
                lnI = Math.log(tempXY.getYValue()*tempXY.getXValue());
                original.add(q2, lnI);
            } else {
                int start = ((ScatterSpinner)spinnerHigh).getPriorIndex();
                int stop = current + 1;
                for (int i = start; i<stop; i++){
                    tempXY = dataset.getOriginalPositiveOnlyData().getDataItem(i);
                    q2 = tempXY.getXValue()*tempXY.getXValue();
                    lnI = Math.log(tempXY.getYValue()*tempXY.getXValue());
                    original.add(q2, lnI);
                }
            }
        }

        maxCount = original.getItemCount();
        ((ScatterSpinner)spinnerHigh).setPriorIndex((Integer) spinnerHigh.getValue());
        replotRc();
    }

    private void replotRc(){
        double[] x_data = new double[maxCount];
        double[] y_data = new double[maxCount];

        XYDataItem tempXY;
        XYSeries tempXYSeries = rcDatasetCollection.getSeries(1);
        for (int i=0; i< maxCount; i++){
            tempXY = tempXYSeries.getDataItem(i);
            x_data[i] = tempXY.getXValue();
            y_data[i] = tempXY.getYValue();
        }

        double[] param3 = Functions.leastSquares(x_data, y_data);
        double c1 = param3[0];
        double c0 = param3[1];
        double[] residualsNew = new double[maxCount];
        // line
        rcDatasetCollection.getSeries(0).clear();
        rcDatasetCollection.getSeries(0).add(x_data[0], x_data[0]*c1 + c0);
        rcDatasetCollection.getSeries(0).add(x_data[maxCount-1], x_data[maxCount-1]*c1 + c0);

        // rebuild residuals dataset
        residualsRcDatasetCollection.getSeries(0).clear();

        for (int v=0; v< maxCount; v++) {
            residualsNew[v]=y_data[v]-(c1*x_data[v]+c0);
            residualsRcDatasetCollection.getSeries(0).add(x_data[v],y_data[v]-(c1*x_data[v]+c0));
        }
        residualsRcDatasetCollection.getSeries(1).clear();
        residualsRcDatasetCollection.getSeries(1).add(x_data[0], 0);
        residualsRcDatasetCollection.getSeries(1).add(x_data[maxCount-1], 0);
        rc = Math.sqrt(-3.0*c1);
        rcError = 1.5*param3[2]*Math.sqrt(1/3.0*1/rc);

        valueLabel.setText(String.format("Rc :: %.2f", rc));
        updateLimits(original.getMinX(), original.getMaxX());

        dataset.setrC(rc);
        dataset.setrC_sigma(rcError);
    }


    private void createUIComponents() {

        // TODO: place custom component creation code here
        spinnerLow = new ScatterSpinner(1, dataset.getId());
        spinnerHigh = new ScatterSpinner(dataset.getOriginalPositiveOnlyData().getItemCount(), dataset.getId());
    }

    class ScatterSpinner extends JSpinner {
        public int priorIndex;
        public int collectionID;

        public ScatterSpinner(int current, int id){
            priorIndex = current;
            collectionID = id;
        }

        public void setPriorIndex(int value){
            priorIndex = value;
        }

        public int getID(){
            return collectionID;
        }

        public int getPriorIndex(){
            return priorIndex;
        }
    }
}


