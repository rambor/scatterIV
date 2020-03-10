package version4.plots;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import version4.Constants;
import version4.Dataset;
import version4.tableModels.AnalysisModel;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;

/**
 * Created by robertrambo on 13/01/2016.
 */
public class GPAPlot extends PlotManualGuinier {

    private Dataset datasetInUse;
    private XYSeriesCollection gpaCollection;
    private XYSeriesCollection splineCollection;
    private XYSeriesCollection residualsCollection;
    private XYSeries completeSeries;
    private XYSeries fittedSeries;
    private XYSeries realSpaceSeries;
    private XYSeries idealPlottedSeries;
    private XYSeries idealSeries;
    private XYSeries residuals;


    private XYSplineRenderer splineRend = new XYSplineRenderer();
    private XYLineAndShapeRenderer renderer = new XYSplineRenderer();

    private double guinierRg;
    private double guinierIzero;

    private static double inv3 = 1.0/3.0;
    private static double sqrt3 = Math.sqrt(3.0);

    static JFrame jframe;
    public static ChartFrame chartFrame;
    public static ChartFrame residualsFrame;
    private static ValueMarker yMarker = new ValueMarker(-0.297);
    private static ValueMarker yResidualMarker = new ValueMarker(0);
    private static ValueMarker xMarker = new ValueMarker(1.5);

    public GPAPlot(String title, Dataset dataset, String workingDirectoryName){

        super(title + dataset.getFileName(), dataset, workingDirectoryName);

        jframe = new JFrame(title + dataset.getFileName());

        this.datasetInUse = this.getDatasetInUse();

        completeSeries = new XYSeries("All", false);
        fittedSeries = new XYSeries("Fitted", false);
        residuals = new XYSeries("Residuals", false);

        guinierRg = dataset.getGuinierRg();
        guinierIzero = dataset.getGuinierIzero();

        if (guinierRg == 0) {
            throw new IllegalArgumentException("Guinier Rg is 0, try manual Guinier first");
        }

        if (guinierIzero == 0) {
            throw new IllegalArgumentException("Guinier I(0) is 0, try manual Guinier first");
        }

        createIdealPlottedSeries();
        createTransformedSeries();
        // plot real-space?
        if (dataset.getRealRg() > 0){
            realSpaceSeries = new XYSeries("Real-Space");
        }
    }


    public void makePlot(final AnalysisModel analysisModel){

        analysisModel.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {

                // check if Rg or Izero changed
                if (e.getColumn() == 4 || (e.getColumn() == -1 && e.getFirstRow() == 0)){
                    // go through each dataset in series and see if scale factor was changed?
                    // if (e.getFirstRow() == datasetInUse.getId() && e.getLastRow() == datasetInUse.getId()){
                    //update series
                    guinierRg = datasetInUse.getGuinierRg();
                    guinierIzero = datasetInUse.getGuinierIzero();
                    //rebuild plotted datasets
                    updateResiduals();

                    transformSeries(datasetInUse.getStart(), datasetInUse.getIndexOfUpperGuinierFit(), gpaCollection.getSeries(0), datasetInUse.getOriginalPositiveOnlyData(), guinierRg, guinierIzero);
                    transformSeries(0, 0, gpaCollection.getSeries(1), datasetInUse.getOriginalPositiveOnlyData(), guinierRg, guinierIzero);
                    //                   }
                }
            }
        });

        super.plot(analysisModel);

        // create the plot and have the new plot updated
        // detect changes in analysisModel
        //SCÅTTER ≣ Guinier Peak Analysis
        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                // chart title
                "",                       // domain axis label
                "ln I(q)",                // range axis label
                gpaCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );

        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("");

        String xaxis = "(q·Rg)\u00B2";
        domainAxis.setLabel(xaxis);
        plot.setDomainAxis(domainAxis);

        String yaxis = "ln [(q·Rg) \u00B7 I(q)/I(0)]";
        rangeAxis.setLabel(yaxis);
        rangeAxis.setRange(-1.0, -0.2);
        plot.setRangeAxis(rangeAxis);

        plot.setBackgroundPaint(null);

        gpaCollection = new XYSeriesCollection();
        splineCollection = new XYSeriesCollection();

        splineCollection.addSeries(idealPlottedSeries);

        gpaCollection.addSeries(fittedSeries);   // 0
        gpaCollection.addSeries(completeSeries); // 1

        renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setBaseShapesVisible(true);
        renderer.setBaseShapesFilled(false);

        renderer.setSeriesLinesVisible(0, false);
        renderer.setSeriesShapesFilled(0, false);
        renderer.setSeriesStroke(0, datasetInUse.getStroke());
        renderer.setSeriesPaint(0, datasetInUse.getColor().darker());
        renderer.setSeriesLinesVisible(0, false);
        renderer.setSeriesShape(0, new Ellipse2D.Double(-5, -5, 10.0, 10.0));

        renderer.setSeriesLinesVisible(1, false);
        renderer.setSeriesPaint(1, Constants.RedGray);
        renderer.setSeriesLinesVisible(1, false);
        renderer.setSeriesShape(1, new Ellipse2D.Double(-5, -5, 10.0, 10.0));

        plot.setDataset(0, gpaCollection);
        plot.setRenderer(0, renderer);

        splineRend.setBaseShapesVisible(false);
        splineRend.setSeriesPaint(0, Color.red);
        splineRend.setSeriesStroke(0, new BasicStroke(
                2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f));

        plot.setDataset(1, splineCollection);
        plot.setRenderer(1, splineRend);

        xMarker.setStroke(new BasicStroke(
                2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f));
        yMarker.setStroke(new BasicStroke(
                2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f));

        plot.addDomainMarker(xMarker);
        plot.addRangeMarker(yMarker);


        updateResiduals();
        residualsCollection = new XYSeriesCollection();
        residualsCollection.addSeries(residuals);

        residualsChart = ChartFactory.createXYLineChart(
                "",                // chart title
                "",                    // domain axis label
                "",                  // range axis label
                residualsCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );

        yResidualMarker.setStroke(new BasicStroke(
                2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f));

        //residualsChart.setTitle(null);

        residualsChart.getXYPlot().addRangeMarker(yResidualMarker);
        residualsChart.getXYPlot().setBackgroundPaint(null);

        chartFrame = new ChartFrame("SC\u212BTTER \u2263 GPA PLOT", chart);
        chartFrame.getChartPanel().setChart(chart);
        chartFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(this.getWorkingDirectoryName()));
        //chartFrame.pack();

        residualsFrame = new ChartFrame("", residualsChart);
        residualsFrame.getChartPanel().setMaximumSize(new Dimension(640, 100));
        residualsFrame.getChartPanel().setPreferredSize(new Dimension(640, 100));
        residualsFrame.getChartPanel().getChart().getLegend().setVisible(false);

        //BorderLayout volumeLayout = new BorderLayout();
        jframe.getContentPane().setLayout(new BorderLayout());

        jframe.getContentPane().add(chartFrame.getChartPanel(), BorderLayout.PAGE_START);
        jframe.getContentPane().add(residualsFrame.getChartPanel(), BorderLayout.PAGE_END);
        jframe.getContentPane().getComponent(1).setMaximumSize(new Dimension(640,200));

        //jframe.setMinimumSize(new Dimension(640,480));
        jframe.setLocation(250,200);
        jframe.pack();
        jframe.setVisible(true);
    }

    private void updateResiduals(){

        int total = fittedSeries.getItemCount();
        residuals.clear();

        XYDataItem temp;
        for (int i=0; i<total; i++){
            temp = fittedSeries.getDataItem(i);
            residuals.add(temp.getX(), temp.getYValue() - gpaFunction(temp.getXValue()));
        }
    }

    private void createTransformedSeries(){

        // fitted series will terminate at qRg < 1.5
        // should terminate at same qlimit as Manual Guinier plot

        double upperLimit = 1.3/guinierRg;
        int count=0;
        for(int i=0; i<datasetInUse.getOriginalPositiveOnlyData().getItemCount(); i++){
            if (datasetInUse.getOriginalPositiveOnlyData().getX(i).doubleValue() > upperLimit){
                break;
            }
            count++;
        }

        transformSeries(datasetInUse.getStart(), count, fittedSeries, datasetInUse.getOriginalPositiveOnlyData(), guinierRg, guinierIzero);
        transformSeries(0, 0,completeSeries, datasetInUse.getOriginalPositiveOnlyData(), guinierRg, guinierIzero);

        if (realSpaceSeries instanceof XYSeries){
            //System.out.println("Instance of XYSeries");
            transformSeries(0, 0,realSpaceSeries, datasetInUse.getOriginalPositiveOnlyData(), datasetInUse.getRealRg(), datasetInUse.getRealIzero());
        } else {
            //System.out.println("Not an instance");
        }
    }


    private void transformSeries(int startAt, int endAtIndex, XYSeries transformed, XYSeries reference, double rg, double izero){

        double upperQLimit = sqrt3/rg;
        double invIzero = 1.0/izero;

        XYDataItem tempItem = reference.getDataItem(startAt);
        transformed.clear();

        int stopcount = (endAtIndex ==0) ? reference.getItemCount() : endAtIndex;
        int i=startAt;

        while(tempItem.getXValue() <= upperQLimit && i < stopcount){

            double qRg = tempItem.getXValue()*rg;
            transformed.add(qRg*qRg, Math.log(qRg*invIzero*tempItem.getYValue()));
            tempItem = reference.getDataItem(i);
            i++;
        }
    }


    private void createIdealPlottedSeries(){

        double minQRg = datasetInUse.getOriginalPositiveOnlyData().getMinX()*guinierRg;

        idealPlottedSeries = new XYSeries("Ideal");
        idealSeries = new XYSeries("Ideal");

        double start = 0.14958;
        double scale = 1.12;

        if (start > (minQRg*minQRg)){
            start = minQRg*minQRg;
        }

        double initial = start*scale;

        int count=0;
        while (initial < 3){
            idealSeries.add(initial, gpaFunction(initial));
            idealPlottedSeries.add(idealSeries.getDataItem(count));
            initial *= scale;
            count++;
        }
    }

    /**
     * ideadlize GPA function
     * @param value
     * @return function at q*rg
     */
    private double gpaFunction(double value){
        return 0.5*Math.log(value) - value*inv3;
    }

    //combo plot of data against idealized curve
    // domain of plot x-axis 0 to 3
    // range of plot y-axis -1 to -0.2
}
