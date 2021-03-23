package version4.plots;

import FileManager.WorkingDirectory;
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
import version4.Collection;
import version4.Constants;
import version4.Dataset;
import version4.GetValueDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public abstract class ScatterPlot implements ScatterPlotInterface, PropertyChangeListener {

    public Point locationOfWindow;
    public NumberAxis domainAxis;
    public NumberAxis rangeAxis;
    public JFreeChart chart;
    public ChartFrame frame;
    public ChartPanel chartPanel;
    public XYPlot plot;
    public XYSeriesCollection plottedDatasets;
    public XYLineAndShapeRenderer baseRenderer;
    public boolean crosshair = true;
    public boolean visible = false;

    double upper, dupper;
    double lower, dlower;

    JMenuItem toggler;
    JMenuItem setUpperLimitDomain;
    JMenuItem setLowerLimitRange;
    String dialogTitle, upperLabelText, lowerRangeLabelText;

    WorkingDirectory WORKING_DIRECTORY;
    Collection collection;

    public ScatterPlot(Collection collection, WorkingDirectory wkd){
        this.collection = collection;
        this.WORKING_DIRECTORY = wkd;
        plottedDatasets = new XYSeriesCollection();
        dialogTitle = "Scatter Plot";
        upperLabelText = "Set q-max";
        lowerRangeLabelText = "Set min log[I(q)]";

        chart = ChartFactory.createXYLineChart(
                "",                          // chart title
                "q",                             // domain axis label
                "log[I(q)]",                     // range axis label
                plottedDatasets,                 // data
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

        plot = chart.getXYPlot();

        toggler = new JMenuItem(new AbstractAction("Toggle Crosshair") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                if (crosshair) {
                    chart.getXYPlot().setDomainCrosshairVisible(false);
                    chart.getXYPlot().setRangeCrosshairVisible(false);
                    crosshair = false;
                } else {
                    chart.getXYPlot().setDomainCrosshairVisible(true);
                    chart.getXYPlot().setRangeCrosshairVisible(true);
                    crosshair = true;
                }
            }
        });

        setUpperLimitDomain = new JMenuItem(new AbstractAction("set MAX x-axis") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                GetValueDialog dialog = new GetValueDialog(dialogTitle, upperLabelText, chart, "upperDomain");
                dialog.pack();
                dialog.setVisible(true);
                //chart.getXYPlot().getDomainAxis().setUpperBound(9);
            }
        });

        setLowerLimitRange = new JMenuItem(new AbstractAction("set MIN y-axis") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                GetValueDialog dialog = new GetValueDialog(dialogTitle, lowerRangeLabelText, chart, "lowerRange");
                dialog.pack();
                dialog.setVisible(true);
                //chart.getXYPlot().getDomainAxis().setUpperBound(9);
            }
        });
        this.setChartPanel();

        domainAxis = new NumberAxis("");
        rangeAxis = new NumberAxis("");
        domainAxis.setAxisLineStroke(new BasicStroke(1.4f));
        domainAxis.setTickMarkStroke(new BasicStroke(1.4f));
        rangeAxis.setAxisLineStroke(new BasicStroke(1.4f));
        rangeAxis.setTickMarkStroke(new BasicStroke(1.4f));
    }

    public void setChartPanel(){
        chartPanel = new ChartPanel(chart){
            @Override
            public void restoreAutoBounds(){
                super.restoreAutoDomainBounds();
                super.restoreAutoRangeBounds();
                super.getChart().getXYPlot().getRangeAxis().setAutoRange(false);

                int seriesCount = super.getChart().getXYPlot().getDataset(1).getSeriesCount();
                int maxIndex;
                double min = 10;
                double max = -100;
                double minX = 10;
                double maxX = 0;
                double tempYMin;
                double tempYmax;
                double tempXMin;
                boolean isVisible;

                for (int i=0; i < seriesCount; i++){
                    // check if visible, if visible, get min and max I value
                    // renderer 0 is merged data
                    isVisible = super.getChart().getXYPlot().getRenderer(1).isSeriesVisible(i);

                    if (isVisible){
                        maxIndex = super.getChart().getXYPlot().getDataset(1).getItemCount(i)-3;
                        tempYmax = super.getChart().getXYPlot().getDataset(1).getYValue(i, 0);

                        if (tempYmax > max){
                            max = tempYmax;
                        }

                        for (int j=0; j< maxIndex;j++){
                            tempYMin = super.getChart().getXYPlot().getDataset(1).getYValue(i, j);
                            tempXMin = super.getChart().getXYPlot().getDataset(1).getXValue(i, j);
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

                super.getChart().getXYPlot().getRangeAxis().setRange(min-Math.abs(min*0.1), max+Math.abs(0.1*max));
                super.getChart().getXYPlot().getDomainAxis().setRange(minX-Math.abs(minX*0.2),maxX+Math.abs(0.1*maxX));
            }
        };
    }

    @Override
    public boolean isVisible() {
        return frame.isVisible();
    }

    @Override
    public void setNotify(boolean state) {
        frame.getChartPanel().getChart().setNotify(state);
    }

    @Override
    public void changeColor(int id, Color newColor, float thickness, int pointsize){
        baseRenderer.setSeriesPaint(id, newColor);

        double offset = -0.5*pointsize;
        baseRenderer.setSeriesShape(id, new Ellipse2D.Double(offset, offset, pointsize, pointsize));
        baseRenderer.setSeriesOutlineStroke(id, new BasicStroke(thickness));
    }

    @Override
    public void setPlotScales(int totalSets){
        lower = Double.POSITIVE_INFINITY;
        upper = Double.NEGATIVE_INFINITY;

        double tempMin, tempMax;
        dupper = Double.NEGATIVE_INFINITY;
        dlower = Double.POSITIVE_INFINITY;
        for (int i=0; i < plottedDatasets.getSeriesCount(); i++){
            XYSeries tempData = plottedDatasets.getSeries(i);

            tempMax = tempData.getMaxX();
            tempMin = tempData.getMinX();

                if (tempMax > dupper){
                    dupper = tempMax;
                }
                if (tempMin < dlower){
                    dlower = tempMin;
                }


                if (tempData.getMaxY() > upper){
                    upper = tempData.getMaxY();
                }
                if (tempData.getMinY() < lower){
                    lower = tempData.getMinY();
                }
        }

        double upperlimit = (upper < 0) ? (upper - upper*0.1) : (upper + 0.1*upper);
        double lowerlimit = (lower < 0) ? (lower + lower*0.05) : (lower - 0.05*lower);

        rangeAxis.setRange(lowerlimit, upperlimit);
        rangeAxis.setUpperBound(upperlimit);
        rangeAxis.setLowerBound(lowerlimit);
        domainAxis.setRange(dlower - 0.02*dlower, dupper+0.02*dupper);
    }
}
