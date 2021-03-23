package version4.plots;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.ui.HorizontalAlignment;
import version4.Collection;
import version4.Constants;
import version4.Dataset;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.beans.PropertyChangeEvent;
import java.io.File;

public class PlotData extends ScatterPlot {

    public PlotData(Collection collection, WorkingDirectory wkd) {
        super(collection, wkd);
        locationOfWindow = new Point(100,100);

        dialogTitle="LOG10 INTENSITY Plot";
        upperLabelText="Upper Bound Limit X-axis";
        lowerRangeLabelText="Lower Bound Limit Y-axis";

        frame = new ChartFrame("SC\u212BTTER \u2263 LOG10 INTENSITY PLOT", chart);
        JPopupMenu popup = frame.getChartPanel().getPopupMenu();

        //frame.getChartPanel().getChart().getXYPlot().getRangeAxis().setAxisLineStroke();
        popup.add(toggler);
        popup.add(setUpperLimitDomain);
        popup.add(setLowerLimitRange);

        String quote = "q (\u212B\u207B\u00B9)";
        domainAxis.setLabelFont(Constants.BOLD_16);
        domainAxis.setTickLabelFont(Constants.FONT_12);
        domainAxis.setLabel(quote);

        rangeAxis.setLabel("log[I(q)]");
        rangeAxis.setAutoRange(false);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setTickLabelFont(Constants.FONT_12);
        rangeAxis.setAutoRangeStickyZero(false);
        domainAxis.setAutoRangeStickyZero(false);

//        plot.setDomainAxis(domainAxis);
//        plot.setRangeAxis(rangeAxis);
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setOutlineVisible(false);

        baseRenderer = (XYLineAndShapeRenderer) plot.getRenderer();
        baseRenderer.setBaseShapesVisible(true);
    }

    @Override
    public void plot(){

        if (frame.isVisible()){
            frame.dispose();
        }

        int totalSets = collection.getTotalDatasets();

        plottedDatasets.removeAllSeries();
        for (int i=0; i<totalSets; i++){
            plottedDatasets.addSeries(collection.getDataset(i).getData());
        }


        // add all the data to the plot only show the ones with checkboxes
        // get min and max for checked datasets

        setPlotScales(totalSets);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        updateRenderer();

        frame.setLocation(locationOfWindow);
        frame.getChartPanel().setChart(chartPanel.getChart());
        frame.getChartPanel().setDefaultDirectoryForSaveAs(new File(WORKING_DIRECTORY.getWorkingDirectory()));
        frame.getChartPanel().setDisplayToolTips(false);
        frame.pack();

//        frame.addWindowListener(new WindowAdapter() {
//            public void WindowClosing(WindowEvent e) {
//                locationOfWindow = frame.getLocation();
//                frame.dispose();
//            }
//        });

        frame.setMinimumSize(new Dimension(640,480));
        // Container content = frame.getContentPane();
        // content.add(frame.getChartPanel());
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                locationOfWindow = frame.getLocation();
            }
        });
        frame.setVisible(true);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        Object source = evt.getSource();
        if (evt.getPropertyName() == "mainCollection") {
            // update plot
            int totalSets = collection.getTotalDatasets();

            if (frame.isVisible()){
                XYPlot plot = frame.getChartPanel().getChart().getXYPlot();
                frame.getChartPanel().getChart().setNotify(false);

                plottedDatasets.removeAllSeries();
                for (int i=0; i<totalSets; i++){
                    plottedDatasets.addSeries(collection.getDataset(i).getData());
                }
                setPlotScales(totalSets);
                updateRenderer();
                plot.getRangeAxis().setAutoRange(true);
                frame.getChartPanel().getChart().setNotify(true);
            }
        }
    }


    private void updateRenderer(){
        //set dot size for all series
        double offset;
        for (int i=0; i < collection.getDatasets().size(); i++){
            Dataset tempData = collection.getDataset(i);
            offset = -0.5*tempData.getPointSize();
            baseRenderer.setSeriesShape(i, new Ellipse2D.Double(offset, offset, tempData.getPointSize(), tempData.getPointSize()));
            baseRenderer.setSeriesLinesVisible(i, false);
            baseRenderer.setSeriesPaint(i, tempData.getColor());
            baseRenderer.setSeriesShapesFilled(i, tempData.getBaseShapeFilled());
            baseRenderer.setSeriesVisible(i, tempData.getInUse());
            baseRenderer.setSeriesOutlineStroke(i, tempData.getStroke());
        }
    }

}
