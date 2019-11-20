package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;

public class MiniPlots {
    private static JFreeChart qIqChart;
    private static XYPlot qIqPlot;
    private static ChartPanel qIqChartPanel;

    private static JFreeChart kratkyChart;
    private static XYPlot kratkyPlot;
    private static ChartPanel kratkyChartPanel;

    private static JFreeChart log10Chart;
    private static XYPlot log10Plot;
    private static ChartPanel log10ChartPanel;


    private static XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();

    private static XYSeriesCollection qIQCollection;
    private static XYSeriesCollection kratkyCollection;
    private static XYSeriesCollection plotLog10Collection;

    private static JPanel topPanel;
    private static JPanel midPanel;
    private static JPanel bottomPanel;


    private static MiniPlots singleton = new MiniPlots( );

    /* A private Constructor prevents any other
     * class from instantiating.
     */
    private MiniPlots(){
        // create charts
        qIQCollection = new XYSeriesCollection();
        qIQCollection.addSeries(new XYSeries(""));
        kratkyCollection = new XYSeriesCollection();
        kratkyCollection.addSeries(new XYSeries(""));
        plotLog10Collection = new XYSeriesCollection();
        plotLog10Collection.addSeries(new XYSeries(""));

        createQIQChart();
        createKratkyChart();
        createLogPlotChart();
    }

    /* Static 'instance' method */
    public static MiniPlots getInstance( ) {
        return singleton;
    }



    // create two plots q*Iq and normalized kratky
    public void updatePlots(Dataset dataset){

        // if rg is defined make dimensionless kratky
        // clear series
        qIQCollection.getSeries(0).clear();
        kratkyCollection.getSeries(0).clear();
        plotLog10Collection.getSeries(0).clear();

        // endAt and startAt are in reference to originalPositiveOnlyData
        // remap to all data.
        XYDataItem startItem = dataset.getOriginalPositiveOnlyDataItem(dataset.getStart()-1);
        XYDataItem endItem = dataset.getOriginalPositiveOnlyDataItem(dataset.getEnd() - 1);

        int startHere = dataset.getQIQData().indexOf(startItem.getXValue());
        int endAt  = dataset.getQIQData().indexOf(endItem.getXValue());

        for (int i = startHere; i<endAt; i++){
            qIQCollection.getSeries(0).add(dataset.getQIQDataItem(i));
            kratkyCollection.getSeries(0).add(dataset.getKratkyItem(i));
        }

        endAt = dataset.getData().getItemCount();
        for (int i=0; i<endAt; i++){
            plotLog10Collection.getSeries(0).add(dataset.getData().getDataItem(i));
        }

        //Color tempColor = dataset.getColor();

        qIqPlot.getRenderer(0).setSeriesPaint(0, dataset.getColor());
        kratkyPlot.getRenderer(0).setSeriesPaint(0, dataset.getColor());
        log10Plot.getRenderer(0).setSeriesPaint(0, dataset.getColor());
//        qIqPlot.getRenderer(0).setSeriesPaint(0, new Color(105,105,105, 85));
//        kratkyPlot.getRenderer(0).setSeriesPaint(0, new Color(105,105,105, 85));

        // if Rg is set do dimensionless kratky plot?
    }


    public void createLogPlotChart(){

        log10Chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "",                             // domain axis label
                "",                     // range axis label
                plotLog10Collection,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        log10Chart.getXYPlot().setDomainCrosshairVisible(false);
        log10Chart.getXYPlot().setRangeCrosshairVisible(false);
        log10Chart.setTitle("log10 Intensity Plot");
        log10Chart.getTitle().setFont(new java.awt.Font("SansSerif", 1, 12));
        log10Chart.getTitle().setPaint(new Color(105,105,105));

        final NumberAxis domainAxis = new NumberAxis("q");
        final NumberAxis rangeAxis = new NumberAxis("I(q)*q");

        String quote = "q, \u212B \u207B\u00B9";
        domainAxis.setLabel(quote);
        domainAxis.setAutoRangeStickyZero(true);

        quote = "log10 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRangeStickyZero(true);
        rangeAxis.setTickLabelsVisible(false);

//        qIqChart.getLegend().setVisible(false);
//
//        qIqChart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
//        qIqChart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
//        qIqChart.getTitle().setMargin(10, 10, 4, 0);
//        qIqChart.setBorderVisible(false);

        log10Plot = log10Chart.getXYPlot();
        log10Plot.setDomainAxis(domainAxis);
        log10Plot.setRangeAxis(rangeAxis);

        //LogarithmicAxis yAxis = new LogarithmicAxis("eigenvalues");
        //yAxis.setLabel(quote);
        //yAxis.setAutoRangeStickyZero(true);
        //yAxis.setTickLabelsVisible(false);

        log10Plot.setBackgroundAlpha(0.0f);
        log10Plot.setOutlineVisible(false);
        log10Plot.setDomainZeroBaselineVisible(true);
        log10Plot.setRangeZeroBaselineVisible(false);
        log10Plot.setRangeZeroBaselineStroke(new BasicStroke(
                1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f
        ));
        log10Plot.setRangeZeroBaselinePaint(new Color(169,169,169));
        log10ChartPanel=new ChartPanel(log10Chart);

    }

    public void createKratkyChart(){

        kratkyChart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "",                             // domain axis label
                "",                     // range axis label
                kratkyCollection,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        kratkyChart.getXYPlot().setDomainCrosshairVisible(false);
        kratkyChart.getXYPlot().setRangeCrosshairVisible(false);
        kratkyChart.setTitle("Kratky Plot");
        kratkyChart.getTitle().setFont(new java.awt.Font("SansSerif", 1, 12));
        kratkyChart.getTitle().setPaint(new Color(105,105,105));

        final NumberAxis domainAxis = new NumberAxis("q");
        final NumberAxis rangeAxis = new NumberAxis("I(q)*q");
        String quote = "q, \u212B \u207B\u00B9";
        domainAxis.setLabel(quote);
        domainAxis.setAutoRangeStickyZero(true);

        quote = "q\u00B2 \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRangeStickyZero(true);
        rangeAxis.setTickLabelsVisible(false);

//        qIqChart.getLegend().setVisible(false);
//
//        qIqChart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
//        qIqChart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
//        qIqChart.getTitle().setMargin(10, 10, 4, 0);
//        qIqChart.setBorderVisible(false);

        kratkyPlot = kratkyChart.getXYPlot();
        kratkyPlot.setDomainAxis(domainAxis);
        kratkyPlot.setRangeAxis(rangeAxis);
        kratkyPlot.setBackgroundAlpha(0.0f);
        kratkyPlot.setOutlineVisible(false);
        kratkyPlot.setDomainZeroBaselineVisible(true);
        kratkyPlot.setRangeZeroBaselineVisible(true);
        kratkyPlot.setRangeZeroBaselineStroke(new BasicStroke(
                1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f
        ));
        kratkyPlot.setRangeZeroBaselinePaint(new Color(169,169,169));
        kratkyChartPanel=new ChartPanel(kratkyChart);

    }

    public void createQIQChart(){

        qIqChart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "",                             // domain axis label
                "",                     // range axis label
                qIQCollection,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        qIqChart.getXYPlot().setDomainCrosshairVisible(false);
        qIqChart.getXYPlot().setRangeCrosshairVisible(false);
        qIqChart.setTitle("Total Intensity Plot");
        qIqChart.getTitle().setFont(new java.awt.Font("SansSerif", 1, 12));
        qIqChart.getTitle().setPaint(new Color(105,105,105));

        final NumberAxis domainAxis = new NumberAxis("q");
        final NumberAxis rangeAxis = new NumberAxis("I(q)*q");
        String quote = "q, \u212B \u207B\u00B9";
        domainAxis.setLabel(quote);
        domainAxis.setAutoRangeStickyZero(true);

//        domainAxis.setLabelFont(Constants.BOLD_16);
//        domainAxis.setTickLabelFont(Constants.FONT_12);
        quote = "q \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRangeStickyZero(true);
        rangeAxis.setTickLabelsVisible(false);


//        qIqChart.getLegend().setVisible(false);
//
//        qIqChart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
//        qIqChart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
//        qIqChart.getTitle().setMargin(10, 10, 4, 0);
//        qIqChart.setBorderVisible(false);

        qIqPlot = qIqChart.getXYPlot();
        qIqPlot.setDomainAxis(domainAxis);
        qIqPlot.setRangeAxis(rangeAxis);
        qIqPlot.setBackgroundAlpha(0.0f);
        qIqPlot.setOutlineVisible(false);

        qIqPlot.setDomainZeroBaselineVisible(true);
        qIqPlot.setRangeZeroBaselineVisible(true);
        qIqPlot.setRangeZeroBaselinePaint(new Color(169,169,169));
        qIqPlot.setRangeZeroBaselineStroke(new BasicStroke(
                1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f
        ));

        qIqChartPanel=new ChartPanel(qIqChart);

    }


    public void setChartPanels(JPanel qIqPanel, JPanel midpanel, JPanel bottomPanel){
        topPanel = qIqPanel;    //assign panels from Main
        midPanel = midpanel;
        this.bottomPanel = bottomPanel;

        topPanel.add(qIqChartPanel); // add charts to panels
        midPanel.add(log10ChartPanel);
        this.bottomPanel.add(kratkyChartPanel);
    }


    public void clearMiniPlots(){
        qIQCollection.getSeries(0).clear();
        kratkyCollection.getSeries(0).clear();
        plotLog10Collection.getSeries(0).clear();
    }
}
