package version4.plots;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
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
import java.util.ArrayList;

public class XYPlot extends ScatterPlot {

    public XYPlot(Collection collection, WorkingDirectory wkd, boolean useLog10axis){
        super(collection, wkd);

        locationOfWindow = new Point(100,100);
        chartPanel = new ChartPanel(chart);

        frame = new ChartFrame("", chart);
        JPopupMenu popup = frame.getChartPanel().getPopupMenu();

        //frame.getChartPanel().getChart().getXYPlot().getRangeAxis().setAxisLineStroke();
        popup.add(toggler);

        String quote = "";
//        domainAxis.setLabelFont(Constants.BOLD_16);
        domainAxis.setTickLabelFont(Constants.FONT_12);
        domainAxis.setLabel(quote);

        rangeAxis.setLabel("");
        rangeAxis.setAutoRange(false);
//        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setTickLabelFont(Constants.FONT_12);
        rangeAxis.setAutoRangeStickyZero(false);
        domainAxis.setAutoRangeStickyZero(false);

        if (useLog10axis){
            LogAxis yAxis = new LogAxis("");
            yAxis.setBase(10);
            yAxis.setLabel("");
            yAxis.setAutoRange(false);
            //yAxis.setLabelFont(Constants.BOLD_16);
            //yAxis.setTickLabelFont(Constants.FONT_12);
            yAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
            plot.setRangeAxis(yAxis);
        } else {
            plot.setRangeAxis(rangeAxis);
        }

        plot.setDomainAxis(domainAxis);

        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setOutlineVisible(false);

        baseRenderer = (XYLineAndShapeRenderer) plot.getRenderer();
        baseRenderer.setBaseShapesVisible(true);
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
//        if (evt.getPropertyName() == "mainCollection") {
//            // update plot
//            int totalSets = collection.getTotalDatasets();
//
//            if (frame.isVisible()){
//                org.jfree.chart.plot.XYPlot plot = frame.getChartPanel().getChart().getXYPlot();
//                frame.getChartPanel().getChart().setNotify(false);
//
//                plottedDatasets.removeAllSeries();
//                for (int i=0; i<totalSets; i++){
//                    plottedDatasets.addSeries(collection.getDataset(i).getData());
//                }
//
//                setPlotScales(totalSets);
//                updateRenderer();
//                plot.getRangeAxis().setAutoRange(true);
//                frame.getChartPanel().getChart().setNotify(true);
//            }
//        }
    }

    public void updateChart(){
        int totalSets = collection.getTotalDatasets();
        plottedDatasets.setNotify(false);
        plottedDatasets.removeAllSeries();
        for (int i=0; i<totalSets; i++){
            plottedDatasets.addSeries(collection.getDataset(i).getAllData());
        }

        setPlotScales(totalSets);
        plot.getRangeAxis().setRange(lower, upper);
        updateRenderer();
        plottedDatasets.setNotify(true);
    }

    public void updateVisibility(ArrayList<Boolean> selectedIndices){

        for (int i=0; i < collection.getDatasets().size(); i++){

            if (selectedIndices.get(i)){
                baseRenderer.setSeriesVisible(i, true);
            } else {
                baseRenderer.setSeriesVisible(i, false);
            }
        }

    }

    public void clear(){
        plottedDatasets.removeAllSeries();
        plottedDatasets.setNotify(true);
        chartPanel.validate();
    }

    public ChartPanel getChartPanel(){
        return chartPanel;
    }

    @Override
    public void plot() {
        if (frame.isVisible()){
            frame.dispose();
        }

        int totalSets = collection.getTotalDatasets();

        plottedDatasets.removeAllSeries();
        for (int i=0; i<totalSets; i++){
            plottedDatasets.addSeries(collection.getDataset(i).getAllData());
        }

        // add all the data to the plot only show the ones with checkboxes
        // get min and max for checked datasets
        setPlotScales(totalSets);
        plot.getRangeAxis().setRange(lower, upper);
        updateRenderer();

        frame.setMinimumSize(new Dimension(640,480));
        // Container content = frame.getContentPane();
        // content.add(frame.getChartPanel());
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                locationOfWindow = frame.getLocation();
            }
        });

        frame.setLocation(locationOfWindow);
        frame.getChartPanel().setChart(chartPanel.getChart());
        frame.getChartPanel().setDefaultDirectoryForSaveAs(new File(WORKING_DIRECTORY.getWorkingDirectory()));
        frame.getChartPanel().setDisplayToolTips(false);
        frame.pack();

        frame.setVisible(true);
    }

    public void setDomainAxisTitle(String title){
        domainAxis.setLabel(title);
    }

    public void setRangeAxisTitle(String title){
        rangeAxis.setLabel(title);
    }

    public void setTitle(String text){
        chart.setTitle(text);
        chart.getTitle().setFont(new Font("Century Gothic", Font.BOLD, 16));
        chart.getTitle().setPaint(new Color(51,61,73));
    }

    public void setTitleColor(Color newcolor){
        chart.getTitle().setPaint(newcolor);
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
