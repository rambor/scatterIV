package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;

/**
 * Created by robertrambo on 17/01/2016.
 */
public class DoubleXYPlot {

    JFreeChart chart;
    public XYPlot plot;
    public boolean crosshair = true;

    private XYLineAndShapeRenderer leftRenderer = new XYLineAndShapeRenderer();
    private XYLineAndShapeRenderer rightRenderer = new XYLineAndShapeRenderer();
    private XYSeriesCollection izerosCollection = new XYSeriesCollection();
    private XYSeriesCollection rgsCollection = new XYSeriesCollection();

    private ChartFrame frame = new ChartFrame("SC\u212BTTER \u2263 PLOT", chart);
    private JFrame f = new JFrame("SC\u212BTTER \u2263 PLOT");

    private Container content = f.getContentPane();

    private double upperLeft, upperRight;
    private double lowerLeft, lowerRight;
    private String workingDirectoryName;

    public DoubleXYPlot(String workingDirectoryName){
        this.workingDirectoryName = workingDirectoryName;

    }

    public void makePlot(Collection collection) {
        //
        // previously miniCollection was being plotted
        // this is really the active datasets,
        // in the constructor for collection, miniCollection is derived from dataset (same pointer)
        //

        izerosCollection = new XYSeriesCollection();
        rgsCollection = new XYSeriesCollection();
        XYSeries izeros = new XYSeries("I Zero");
        XYSeries rgs = new XYSeries("Rg");

        int total = collection.getDatasets().size();

        double tempUpper;
        upperLeft = 0;
        lowerLeft = 10000000;
        upperRight = 0;
        lowerRight = 10000000;

        for (int i=0; i<total; i++){
            if (collection.getDataset(i).getInUse()){
                tempUpper = collection.getDataset(i).getGuinierIzero();
                izeros.add(i+1, tempUpper);

                if (tempUpper > upperLeft){
                    upperLeft = tempUpper;
                }

                if (tempUpper < lowerLeft){
                    lowerLeft = tempUpper;
                }

                tempUpper = collection.getDataset(i).getGuinierRg();
                rgs.add(i+1, tempUpper);

                if (tempUpper > upperRight){
                    upperRight = tempUpper;
                }

                if (tempUpper < lowerRight){
                    lowerRight = tempUpper;
                }
            }
        }

        izerosCollection.addSeries(izeros);
        rgsCollection.addSeries(rgs);

        chart = ChartFactory.createXYLineChart(
                "Scatter Plot",                // chart title
                "Number",                        // domain axis label
                "I(0)",                // range axis label
                izerosCollection,           // data
                PlotOrientation.VERTICAL,
                false,                      // include legend
                true,
                false
        );

        chart.setTitle("SC\u212BTTER \u2263 Plot");
        chart.getTitle().setFont(new java.awt.Font("Times", 1, 20));
        chart.getTitle().setPaint(Constants.SteelBlue);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setMargin(10, 10, 4, 0);
        chart.getTitle().setVisible(false);
        chart.setBorderVisible(false);

        ChartPanel chartPanel = new ChartPanel(chart);
/*
        ChartPanel chartPanel = new ChartPanel(chart){
            @Override
            public void restoreAutoBounds(){
                super.restoreAutoDomainBounds();
                super.restoreAutoRangeBounds();
                super.getChart().getXYPlot().getRangeAxis().setAutoRange(false);

                int seriesCount = super.getChart().getXYPlot().getDataset(0).getSeriesCount();
                int maxIndex;
                double min = 100000000;
                double max = -10;
                double minX = 10;
                double maxX = 0;
                double tempYMin;
                double tempYmax;
                double tempXMin;
                boolean isVisible;

                for (int i=0; i < seriesCount; i++){
                    //check if visible, if visible, get min and max I value
                    // renderer 0 is merged data
                    // renderer 1 is plotted data
                    isVisible = super.getChart().getXYPlot().getRenderer(0).isSeriesVisible(i);

                    if (isVisible){
                        maxIndex = super.getChart().getXYPlot().getDataset(0).getItemCount(i)-3;
                        tempYmax = super.getChart().getXYPlot().getDataset(0).getYValue(i, 0);

                        if (tempYmax > max){
                            max = tempYmax;
                        }

                        for (int j=0; j< maxIndex;j++){
                            tempYMin = super.getChart().getXYPlot().getDataset(0).getYValue(i, j);
                            tempXMin = super.getChart().getXYPlot().getDataset(0).getXValue(i, j);
                            if (tempYMin < min){
                                min = tempYMin;
                            }

                            if (tempXMin < minX) {
                                minX = tempXMin;
                            }
                            if (tempXMin > maxX) {
                                maxX = tempXMin;
                            }

                        }
                    }
                }

                super.getChart().getXYPlot().getRangeAxis().setRange(min-Math.abs(min*0.1), max+Math.abs(0.25*max));
                super.getChart().getXYPlot().getDomainAxis().setRange(minX-Math.abs(minX*0.2),maxX+Math.abs(0.1*maxX));
            }
        };
     */

        plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("File Number");
        final NumberAxis rangeAxisLeft = new NumberAxis("Left");
        final NumberAxis rangeAxisRight = new NumberAxis("Right");
        String quote = "Number";
        domainAxis.setLabel(quote);
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        quote = "I(0)";

        rangeAxisLeft.setLabel(quote);
        rangeAxisLeft.setLabelFont(new Font("Times", Font.BOLD, 20));
        rangeAxisLeft.setLabelPaint(new Color(255, 153, 51));
        rangeAxisLeft.setAutoRange(false);
        rangeAxisLeft.setRange(lowerLeft-lowerLeft*0.03, upperLeft+0.1*upperLeft);
        rangeAxisLeft.setAutoRangeStickyZero(false);

        String quoteR = "Rg Å";
        rangeAxisRight.setLabel(quoteR);
        rangeAxisRight.setLabelFont(new Font("Times", Font.BOLD, 20));
        rangeAxisRight.setLabelPaint(new Color(51, 153, 255));
        rangeAxisRight.setAutoRange(false);
        rangeAxisRight.setRange(lowerRight-lowerRight*0.03, upperRight+0.1*upperRight);
        rangeAxisRight.setAutoRangeStickyZero(false);

        domainAxis.setAutoRangeStickyZero(false);

        plot.setDomainAxis(0, domainAxis);
        plot.setRangeAxis(0, rangeAxisLeft);
        plot.setRangeAxis(1, rangeAxisRight);

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);

        leftRenderer = (XYLineAndShapeRenderer) plot.getRenderer();

        plot.setDataset(0, izerosCollection);
        plot.setRenderer(0,leftRenderer);
        plot.setDataset(1,rgsCollection);
        plot.setRenderer(1, rightRenderer);       //render as a line

        plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis
        plot.mapDatasetToRangeAxis(1, 1); //2nd dataset to 2nd y-axis

        leftRenderer.setBaseShapesVisible(true);
        leftRenderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
        leftRenderer.setSeriesLinesVisible(0, false);
        leftRenderer.setSeriesPaint(0, new Color(255, 153, 51));
        leftRenderer.setSeriesVisible(0, true);
        leftRenderer.setSeriesOutlineStroke(0, new BasicStroke(2));
        leftRenderer.setSeriesOutlinePaint(0, Color.BLACK);

        rightRenderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
        rightRenderer.setSeriesLinesVisible(0, false);
        rightRenderer.setSeriesPaint(0, new Color(51, 153, 255));
        rightRenderer.setSeriesShapesFilled(0, true);
        rightRenderer.setSeriesVisible(0, true);
        rightRenderer.setSeriesOutlineStroke(0, new BasicStroke(2));


        JPopupMenu popup = chartPanel.getPopupMenu();
        popup.add(new JMenuItem(new AbstractAction("Toggle Crosshair") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                if (crosshair){
                    chart.getXYPlot().setDomainCrosshairVisible(false);
                    chart.getXYPlot().setRangeCrosshairVisible(false);
                    crosshair = false;
                } else {
                    chart.getXYPlot().setDomainCrosshairVisible(true);
                    chart.getXYPlot().setRangeCrosshairVisible(true);
                    crosshair = true;
                }
            }
        }));

        plot.setDomainZeroBaselineVisible(false);
        chartPanel.setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        frame.getContentPane().add(chartPanel);
        frame.getChartPanel().setDisplayToolTips(true);
        frame.pack();

        f.setSize(688, 455);
        content.add(frame.getChartPanel());
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void makeXYPlot(XYSeries data) {
        //
        // previously miniCollection was being plotted
        // this is really the active datasets,
        // in the constructor for collection, miniCollection is derived from dataset (same pointer)
        //

        izerosCollection = new XYSeriesCollection();
        izerosCollection.addSeries(data);

        chart = ChartFactory.createXYLineChart(
                "",                // chart title
                "q-value",                        // domain axis label
                "relative error",                // range axis label
                izerosCollection,           // data
                PlotOrientation.VERTICAL,
                false,                      // include legend
                true,
                false
        );

        chart.setTitle("SC\u212BTTER \u2263 I/sigma Plot");
        chart.getTitle().setFont(new java.awt.Font("Times", 1, 20));
        chart.getTitle().setPaint(Constants.SteelBlue);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setMargin(10, 10, 4, 0);
        chart.setBorderVisible(false);

        ChartPanel chartPanel = new ChartPanel(chart);

        plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("File Number");
        final NumberAxis rangeAxisLeft = new NumberAxis("Left");
        String quote = "q-values";
        domainAxis.setLabel(quote);
        quote = "I/sigma";

        rangeAxisLeft.setLabel(quote);
        rangeAxisLeft.setLabelFont(new Font("Times", Font.BOLD, 20));
        rangeAxisLeft.setLabelPaint(new Color(255, 153, 51));
        rangeAxisLeft.setAutoRange(true);
        //rangeAxisLeft.setRange(lowerLeft-lowerLeft*0.03, upperLeft+0.1*upperLeft);
        rangeAxisLeft.setAutoRangeStickyZero(false);

        domainAxis.setAutoRangeStickyZero(false);

        plot.setDomainAxis(0, domainAxis);
        plot.setRangeAxis(0, rangeAxisLeft);

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);

        leftRenderer = (XYLineAndShapeRenderer) plot.getRenderer();

        plot.setRenderer(0,leftRenderer);

        plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis

        leftRenderer.setBaseShapesVisible(true);
        leftRenderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
        leftRenderer.setSeriesLinesVisible(0, false);
        leftRenderer.setSeriesPaint(0, Color.gray);
        leftRenderer.setSeriesShapesFilled(0, false);
        leftRenderer.setSeriesVisible(0, true);
        leftRenderer.setSeriesOutlineStroke(0, new BasicStroke(2));
        leftRenderer.setSeriesOutlinePaint(0, Color.BLACK);


        JPopupMenu popup = chartPanel.getPopupMenu();
        popup.add(new JMenuItem(new AbstractAction("Toggle Crosshair") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                if (crosshair){
                    chart.getXYPlot().setDomainCrosshairVisible(false);
                    chart.getXYPlot().setRangeCrosshairVisible(false);
                    crosshair = false;
                } else {
                    chart.getXYPlot().setDomainCrosshairVisible(true);
                    chart.getXYPlot().setRangeCrosshairVisible(true);
                    crosshair = true;
                }
            }
        }));

        plot.setDomainZeroBaselineVisible(false);
        chartPanel.setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        frame.getContentPane().add(chartPanel);
        frame.getChartPanel().setDisplayToolTips(true);
        frame.pack();

        f.setSize(688, 455);
        content.add(frame.getChartPanel());
        f.setVisible(true);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

}
