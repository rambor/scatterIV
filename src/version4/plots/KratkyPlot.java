package version4.plots;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
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

public class KratkyPlot extends ScatterPlot {
    private final ValueMarker lineAtZero = new ValueMarker(0, Color.BLACK, new BasicStroke(1.0f));
    public KratkyPlot(Collection collection, WorkingDirectory wkd) {
        super(collection, wkd);
        dialogTitle="Kratky Plot";
        upperLabelText="Upper Bound Limit X-axis";
        lowerRangeLabelText="Lower Bound Limit Y-axis";

        locationOfWindow = new Point(225,300);
        frame = new ChartFrame("SC\u212BTTER \u2263 Kratky PLOT", chart);
        JPopupMenu popup = frame.getChartPanel().getPopupMenu();
        popup.add(toggler);
        popup.add(setUpperLimitDomain);
        popup.add(setLowerLimitRange);

        String quote = "q, \u212B \u207B\u00B9";
        domainAxis.setLabelFont(Constants.BOLD_16);
        domainAxis.setTickLabelFont(Constants.FONT_12);
        domainAxis.setLabel(quote);

        quote = "q\u00B2 \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRange(false);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setTickLabelFont(Constants.FONT_12);

        rangeAxis.setAutoRangeStickyZero(false);
        domainAxis.setAutoRangeIncludesZero(true);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
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
            if (temp.getInUse()){
                temp.scalePlottedKratkyData();
                plottedDatasets.addSeries(temp.getPlottedKratkyDataSeries());  // should add an empty Series
            }
        }

        setPlotScales(totalSets);
        plot.getRangeAxis().setRange(lower, upper);
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
        Object source = evt.getSource();
        if (evt.getPropertyName() == "mainCollection") {
            // update plot
            int totalSets = collection.getTotalDatasets();

            if (frame.isVisible()){
                XYPlot plot = frame.getChartPanel().getChart().getXYPlot();
                frame.getChartPanel().getChart().setNotify(false);

                plottedDatasets.removeAllSeries();
                for (int i = 0; i < totalSets; i++){
                    Dataset temp = collection.getDataset(i);
                    if (temp.getInUse()){
                        temp.scalePlottedKratkyData();
                        plottedDatasets.addSeries(temp.getPlottedKratkyDataSeries());  // should add an empty Series
                    }
                }
                setPlotScales(totalSets);
                updateRenderer();
                plot.getRangeAxis().setAutoRange(true);
                frame.getChartPanel().getChart().setNotify(true);
            }
        }
    }
}
