package version4.plots;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.xy.XYErrorRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import version4.Collection;
import version4.Constants;
import version4.Dataset;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.beans.PropertyChangeEvent;
import java.io.File;

public class ErrorsPlot extends ScatterPlot {
    private final ValueMarker lineAtZero = new ValueMarker(0, Color.BLACK, new BasicStroke(1.2f));
    YIntervalSeriesCollection plottedYDatasets;
    private XYErrorRenderer baseRenderer;

    public ErrorsPlot(Collection collection, WorkingDirectory wkd) {
        super(collection, wkd);
        dialogTitle="Errors Plot";
        upperLabelText="Upper Bound Limit X-axis";
        lowerRangeLabelText="Lower Bound Limit Y-axis";

        this.plottedYDatasets = new YIntervalSeriesCollection();
        chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "",                // domain axis label
                "",                // range axis label
                this.plottedYDatasets,             // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        chart.getXYPlot().setDomainCrosshairVisible(false);
        chart.getXYPlot().setRangeCrosshairVisible(false);
        chart.setTitle("");
        chart.getTitle().setFont(new java.awt.Font("Times", 1, 20));
        chart.getTitle().setPaint(Constants.SteelBlue);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setMargin(10, 10, 4, 0);
        chart.setBorderVisible(false);

        this.setChartPanel();

        plot = chart.getXYPlot();

        locationOfWindow = new Point(320,320);
        frame = new ChartFrame("SC\u212BTTER \u2263 Total Scattered Intensity with Measurement Errors in I(q) PLOT", chart);
        JPopupMenu popup = frame.getChartPanel().getPopupMenu();
        popup.add(toggler);
        popup.add(setUpperLimitDomain);
        popup.add(setLowerLimitRange);

        String quote = "q, \u212B \u207B\u00B9";
        domainAxis.setLabelFont(Constants.BOLD_16);
        domainAxis.setTickLabelFont(Constants.FONT_12);
        domainAxis.setLabel(quote);

        quote = "q \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRange(false);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setTickLabelFont(Constants.FONT_12);

        rangeAxis.setAutoRangeStickyZero(false);
        domainAxis.setAutoRangeIncludesZero(true);

//        plot.setDomainAxis(domainAxis);
//        plot.setRangeAxis(rangeAxis);
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setOutlineVisible(false);

        plot.addRangeMarker(lineAtZero);

        baseRenderer = new XYErrorRenderer();
        baseRenderer.setBaseLinesVisible(false);
        baseRenderer.setBaseShapesVisible(true);
        baseRenderer.setErrorPaint(Color.GRAY);
//        baseRenderer.setErrorPaint(new GradientPaint(1.0f, 2.0f, Constants.RedGray, 3.0f, 4.0f,
//                Constants.RedGray));
        baseRenderer.setErrorStroke(new BasicStroke(1.4f));
        plot.setRenderer(baseRenderer);
    }



    @Override
    public void plot() {

        if (frame.isVisible()){
            frame.dispose();
        }

        int totalSets = collection.getTotalDatasets();

        this.plottedYDatasets.removeAllSeries();

        for (int i = 0; i < totalSets; i++){
            Dataset temp = collection.getDataset(i);
            temp.addPropertyChangeListener(this);

            if (temp.getInUse()){
                //temp.scalePlottedLogErrorData();
                temp.scalePlottedErrorData();
                this.plottedYDatasets.addSeries(temp.getPlottedErrors());
            }
        }

        lower = Double.POSITIVE_INFINITY;
        upper = Double.NEGATIVE_INFINITY;

        double tempMin, tempMax;
        dupper = Double.NEGATIVE_INFINITY;
        dlower = Double.POSITIVE_INFINITY;
        for (int i=0; i < plottedYDatasets.getSeriesCount(); i++){
            YIntervalSeries tempData = plottedYDatasets.getSeries(i);
            int totalIn = tempData.getItemCount();
            tempMin = tempData.getX(0).doubleValue();
            tempMax = tempData.getX(totalIn-1).doubleValue();

            if (tempMax > dupper){
                dupper = tempMax;
            }
            if (tempMin < dlower){
                dlower = tempMin;
            }

            for(int j=0; j<totalIn; j++){
                if (tempData.getYValue(j) > upper){
                    upper = tempData.getYValue(j);
                }

                if (tempData.getYValue(j) < lower){
                    lower = tempData.getYValue(j);
                }
            }
        }

        double upperlimit = (upper < 0) ? (upper - upper*0.1) : (upper + 0.1*upper);
        double lowerlimit = (lower < 0) ? (lower + lower*0.05) : (lower - 0.05*lower);

        rangeAxis.setRange(lowerlimit, upperlimit);
        rangeAxis.setUpperBound(upperlimit);
        rangeAxis.setLowerBound(lowerlimit);
        domainAxis.setRange(0, dupper+0.02*dupper);


        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        updateRenderer();

        frame.setLocation(locationOfWindow);
        frame.getChartPanel().setChart(chartPanel.getChart());
        frame.getChartPanel().setDefaultDirectoryForSaveAs(new File(WORKING_DIRECTORY.getWorkingDirectory()));
        frame.getChartPanel().setDisplayToolTips(false);
        frame.pack();

        frame.setMinimumSize(new Dimension(640,480));
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                locationOfWindow = frame.getLocation();
            }
        });
        frame.setVisible(true);
    }

    private void updateRenderer(){
        //set dot size for all series
        double offset;
        int count = 0;
        for (int i=0; i < collection.getDatasets().size(); i++){
            Dataset tempData = collection.getDataset(i);
            if (tempData.getInUse()){
                offset = -0.5*tempData.getPointSize();
                baseRenderer.setSeriesShape(count, new Ellipse2D.Double(offset, offset, tempData.getPointSize(), tempData.getPointSize()));
                baseRenderer.setSeriesLinesVisible(count, false);
                baseRenderer.setSeriesPaint(count, tempData.getColor());
                baseRenderer.setSeriesShapesFilled(count, tempData.getBaseShapeFilled());
                baseRenderer.setSeriesVisible(count, tempData.getInUse());
                float tf = tempData.getStroke().getLineWidth();
                baseRenderer.setSeriesOutlineStroke(count, new BasicStroke(tf*1.5f));
                count++;
            }
        }
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if (frame.isVisible() && evt.getPropertyName() == "scalefactor") {
            Dataset dataset = (Dataset) evt.getSource();
            // get the new value object
            dataset.scalePlottedErrorData();
            // rebuild plots
        }
    }
}
