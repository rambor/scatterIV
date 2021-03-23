package version4.plots;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
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

public class qIqPlot extends ScatterPlot {

    private final ValueMarker lineAtZero = new ValueMarker(0, Color.BLACK, new BasicStroke(1.2f));
//    private final ValueMarker lineAtZero = new ValueMarker(0, Color.BLACK, new BasicStroke(
//            1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
//            10.0f, new float[] {8.0f, 8.0f}, 0.0f
//    ));

    public qIqPlot(Collection collection, WorkingDirectory wkd) {
        super(collection, wkd);
        dialogTitle="Total Scattered Intensity Plot";
        upperLabelText="Upper Bound Limit X-axis";
        lowerRangeLabelText="Lower Bound Limit Y-axis";

        locationOfWindow = new Point(300,300);
        frame = new ChartFrame("SC\u212BTTER \u2263 Total Scattered Intensity PLOT", chart);
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

        baseRenderer = (XYLineAndShapeRenderer) plot.getRenderer();
        baseRenderer.setBaseShapesVisible(true);
    }


    @Override
    public void plot() {
        if (frame.isVisible()){
            frame.dispose();
        }

        int totalSets = collection.getTotalDatasets();

        plottedDatasets.removeAllSeries();

        for (int i = 0; i < totalSets; i++){
            Dataset temp = collection.getDataset(i);
            if (temp.getInUse()){ // should only rescale if scale factor changed
                temp.scalePlottedQIQData();
                temp.addPropertyChangeListener(this);
                plottedDatasets.addSeries(temp.getPlottedQIQDataSeries());
            }
        }

        setPlotScales(totalSets);
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
        int count=0;
        for (int i=0; i < collection.getDatasets().size(); i++){
            Dataset tempData = collection.getDataset(i);
            if (tempData.getInUse()){
                offset = -0.5*tempData.getPointSize();
                baseRenderer.setSeriesShape(count, new Ellipse2D.Double(offset, offset, tempData.getPointSize(), tempData.getPointSize()));
                baseRenderer.setSeriesLinesVisible(count, false);
                baseRenderer.setSeriesPaint(count, tempData.getColor());
                baseRenderer.setSeriesShapesFilled(count, tempData.getBaseShapeFilled());
                baseRenderer.setSeriesVisible(count, tempData.getInUse());
                baseRenderer.setSeriesOutlineStroke(count, tempData.getStroke());
                count++;
            }
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if (frame.isVisible() && evt.getPropertyName() == "scalefactor") {
            Dataset dataset = (Dataset) evt.getSource();
            // get the new value object
            frame.getChartPanel().getChart().setNotify(false);
            //dataset.reScalePlottedQIQData();
            dataset.scalePlottedQIQData();
            // rebuild plots
            frame.getChartPanel().getChart().setNotify(true);

        } else if (frame.isVisible() && evt.getPropertyName() == "startIndex"){
            Dataset dataset = (Dataset) evt.getSource();
            // get the new value object
            frame.getChartPanel().getChart().setNotify(false);
            dataset.reStartPlottedQIQ((int)evt.getOldValue(), (int)evt.getNewValue());
            // rebuild plots
            frame.getChartPanel().getChart().setNotify(true);
        } else if (frame.isVisible() && evt.getPropertyName() == "endIndex"){
            Dataset dataset = (Dataset) evt.getSource();
            // get the new value object
            frame.getChartPanel().getChart().setNotify(false);
            dataset.reEndPlottedQIQ((int)evt.getOldValue(), (int)evt.getNewValue());
            // rebuild plots
            frame.getChartPanel().getChart().setNotify(true);
        }
    }
}
