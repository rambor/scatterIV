package version4;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;

public class MSSXPlot {

    private static JFreeChart chart;
    private static ChartFrame frame = new ChartFrame("", chart);
    private ChartPanel outPanel;
    private  boolean crosshair = true;

    public static double selectedBaseLineStart, selectedBaseLineEnd;

    private XYSeriesCollection signalPlotSplineCollection;
    private XYSeriesCollection tracesPlotCollection = new XYSeriesCollection();
    private XYSeriesCollection izeroPlotCollection = new XYSeriesCollection();

    private XYSplineRenderer splineRend = new XYSplineRenderer();
    private XYLineAndShapeRenderer renderer1 = new XYSplineRenderer();

    private static MSSXPlot singleton = new MSSXPlot( );

    private BasicStroke axisStroke = new BasicStroke(1.5f);
    private Color axisColor = Color.black;
    private static JTextField lowerLabel, upperLabel;

    private MSSXPlot(){
        signalPlotSplineCollection = new XYSeriesCollection();
        tracesPlotCollection = new XYSeriesCollection();
    }

    /* Static 'instance' method */
    public static MSSXPlot getInstance() {
        return singleton;
    }


    public void setCalibrationDataSets(XYSeries saxs_signal, XYSeries absorbance){

        if (signalPlotSplineCollection.getSeriesCount() > 0){
            signalPlotSplineCollection.removeAllSeries();
            tracesPlotCollection.removeAllSeries();
            signalPlotSplineCollection.addSeries(saxs_signal);
            tracesPlotCollection.addSeries(absorbance);
            System.out.println("not empty");
        } else {
            signalPlotSplineCollection.addSeries(saxs_signal);
            tracesPlotCollection.addSeries(absorbance);
            System.out.println("empty");
        }

        for (int i=0; i < signalPlotSplineCollection.getSeries(0).getItemCount(); i++){
            double delta = -1.0d*0.5;
            double pointSize = 1.0d;
            splineRend.setSeriesShape(i, new Ellipse2D.Double(delta, delta, pointSize, pointSize));
            splineRend.setSeriesStroke(i, new BasicStroke(2.0f));
            splineRend.setSeriesPaint(i, Color.RED); // make color slight darker

//            renderer1.setSeriesPaint(i, splineRend.getSeriesPaint(i));
            renderer1.setSeriesPaint(i, Color.GRAY); // SAS signal
            renderer1.setSeriesShapesVisible(i,true);
            renderer1.setSeriesLinesVisible(i, false);
            renderer1.setSeriesStroke(i, new BasicStroke(1.0f));
            renderer1.setSeriesShape(i, new Ellipse2D.Double(1.0f, 1.0f, 2.0f, 2.0f));
        }
    }

    public void addDataSet(XYSeries signal, XYSeries absorbance){
        signalPlotSplineCollection.addSeries(signal);
        tracesPlotCollection.addSeries(absorbance);
    }

    public void plot(WorkingDirectory workingDirectory, JPanel panelForPlot) {

        signalPlotSplineCollection = new XYSeriesCollection();
        tracesPlotCollection = new XYSeriesCollection();

        chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "time",                        // domain axis label
                "SAS Signal",                // range axis label
                signalPlotSplineCollection,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        final XYPlot plot = chart.getXYPlot();

        final NumberAxis domainAxis = new NumberAxis("time");
        final NumberAxis rangeAxis = new NumberAxis("SAS Signal");
        domainAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRange(true);
        rangeAxis.setAutoRange(true);
        rangeAxis.setAxisLineVisible(true);

        rangeAxis.setAxisLineStroke(axisStroke);
        domainAxis.setAxisLineStroke(axisStroke);

        rangeAxis.setTickMarkStroke(axisStroke);
        domainAxis.setTickMarkStroke(axisStroke);

        rangeAxis.setAxisLinePaint(axisColor);
        domainAxis.setAxisLinePaint(axisColor);
        rangeAxis.setTickMarkPaint(axisColor);
        domainAxis.setTickMarkPaint(axisColor);

        //org.jfree.data.Range domainBounds = dataset.getDomainBounds(true);
        //org.jfree.data.Range rangeBounds = dataset.getRangeBounds(true);

        outPanel = new ChartPanel(chart){
            @Override
            public void restoreAutoBounds(){
                super.restoreAutoDomainBounds();
                super.restoreAutoRangeBounds();
                super.getChart().getXYPlot().getRangeAxis().setAutoRange(false);

                int seriesCount = super.getChart().getXYPlot().getDataset(0).getSeriesCount();

                int size, size2;
                double max = Double.NEGATIVE_INFINITY;
                double minY = Double.POSITIVE_INFINITY;

                double x_max = 0;
                double tempYMax, tempXMax, tempXMax2;
                boolean isVisible = false;
                for (int i=0; i < seriesCount; i++){
                    // check if visible, if visible, get min and max I value
                    // isVisible = super.getChart().getXYPlot().getRenderer().isSeriesVisible(i);
                    isVisible = super.getChart().getXYPlot().getRenderer(0).isSeriesVisible(i);
                    if (isVisible){
                        size = super.getChart().getXYPlot().getDataset(0).getItemCount(i);
                        size2 = super.getChart().getXYPlot().getDataset(1).getItemCount(i);

                        for(int j =1; j < size; j++) {
                            tempYMax = super.getChart().getXYPlot().getDataset(0).getYValue(i, j);
                            if (tempYMax > max ){
                                max =  tempYMax;
                            }
                            if (tempYMax < minY){
                                minY = tempYMax;
                            }
                        }

                        for(int j =1; j < size2; j++) {
                            tempYMax = super.getChart().getXYPlot().getDataset(1).getYValue(i, j);
                            if (tempYMax > max ){
                                max =  tempYMax;
                            }
                            if (tempYMax < minY){
                                minY = tempYMax;
                            }
                        }

                        tempXMax = super.getChart().getXYPlot().getDataset(0).getXValue(i, size-1);
                        tempXMax2 = super.getChart().getXYPlot().getDataset(1).getXValue(i, size2-2);

                        x_max = Math.max(tempXMax, tempXMax2);
                    }
                }

                super.getChart().getXYPlot().getRangeAxis().setRange(minY, max+0.1*max);
                super.getChart().getXYPlot().getDomainAxis().setRange(0, x_max);
            }
        };

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setBackgroundAlpha(0.0f);

        plot.setDomainCrosshairLockedOnData(true);
        //plot.setBackgroundAlpha(0.0f);
        plot.setRangeZeroBaselineVisible(true);

        //make crosshair visible
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);

        splineRend = new XYSplineRenderer();
        //splineRend = new Monotonic(9);
        //barRend = new XYBarRenderer();
        splineRend.setBaseShapesVisible(false);

        renderer1 = new XYSplineRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseLinesVisible(false);
        renderer1.setBaseStroke(new BasicStroke(1.0f));
        renderer1.setBaseShape(new Ellipse2D.Double(0.5f, 0.5f, 1.0f, 1.0f));

        plot.setDataset(0, signalPlotSplineCollection);  //Moore Function
        plot.setRenderer(0, renderer1);       //render as a line
        //plot.setRenderer(0, barRend);

        plot.setDataset(1, tracesPlotCollection); //PDB data
        plot.setRenderer(1, splineRend);

        ValueMarker mark = new ValueMarker(0, Color.BLACK, new BasicStroke(2));
        plot.addRangeMarker(mark);

        JPopupMenu popup = outPanel.getPopupMenu();
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


        popup.add(new JMenuItem(new AbstractAction("Increase Axis stroke") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                XYPlot plot = chart.getXYPlot();
                BasicStroke temp = (BasicStroke)plot.getDomainAxis().getAxisLineStroke();
                float value = axisStroke.getLineWidth() + 0.5f;
                axisStroke = new BasicStroke(value);
                plot.getDomainAxis().setAxisLineStroke(new BasicStroke(value));
                plot.getRangeAxis().setAxisLineStroke(new BasicStroke(value));
                plot.getDomainAxis().setTickMarkStroke(new BasicStroke(value));
                plot.getRangeAxis().setTickMarkStroke(new BasicStroke(value));
            }
        }));

        popup.add(new JMenuItem(new AbstractAction("Decrease Axis stroke") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                XYPlot plot = chart.getXYPlot();
                BasicStroke temp = (BasicStroke)plot.getDomainAxis().getAxisLineStroke();
                float value = axisStroke.getLineWidth() + 0.5f;
                axisStroke = new BasicStroke(value);
                if (value <=0 )
                    value = 0.5f;
                plot.getDomainAxis().setAxisLineStroke(new BasicStroke(value));
                plot.getRangeAxis().setAxisLineStroke(new BasicStroke(value));
                plot.getDomainAxis().setTickMarkStroke(new BasicStroke(value));
                plot.getRangeAxis().setTickMarkStroke(new BasicStroke(value));
            }
        }));

//        signalChartPanel.setRangeZoomable(false);
//        signalChartPanel.setDomainZoomable(false);
//        signalChartPanel.setMouseZoomable(false);
        outPanel.setHorizontalAxisTrace(true);

        System.out.println("mouse listeneers " + outPanel.getMouseListeners().length);
        outPanel.addMouseListener(new MouseMarker(outPanel));

        outPanel.setDefaultDirectoryForSaveAs(new File(workingDirectory.getWorkingDirectory()));
        //outPanel.getChart().setBorderVisible(false);
        outPanel.getChart().getXYPlot().setOutlineVisible(false);
        panelForPlot.removeAll();
        panelForPlot.add(outPanel);
    }

    public void changeVisible(int index, boolean state){
        chart.getXYPlot().getRenderer(0).setSeriesVisible(index, state);
    }

    public void clear(){
        this.signalPlotSplineCollection.removeAllSeries();
        this.tracesPlotCollection.removeAllSeries();
    }

    /*
     * keeps current calibration files in place
     */
    public void clearSamples(){
        int total = this.signalPlotSplineCollection.getSeriesCount()-1;
        for(int i=total; i<0; i--){
            this.signalPlotSplineCollection.removeSeries(i);
            this.tracesPlotCollection.removeSeries(i);
        }
    }


    public void changeColor(int id, Color newColor, float thickness){
        splineRend.setSeriesPaint(id, newColor);
        chart.getXYPlot().getRenderer(0).setSeriesStroke(id, new BasicStroke(thickness));
    }


    private final static class MouseMarker extends MouseAdapter {
        private Marker marker;
        private Double markerStart = Double.NaN;
        private Double markerEnd = Double.NaN;
        private final XYPlot plot;
        private final JFreeChart chart;
        private  final ChartPanel panel;

        public MouseMarker(ChartPanel panel) {
            this.panel = panel;
            this.chart = panel.getChart();
            this.plot = (XYPlot) chart.getPlot();
        }

        private void updateMarker(){
            if (marker != null){
                plot.removeDomainMarker(marker, Layer.BACKGROUND);
            }

            if (!( markerStart.isNaN() && markerEnd.isNaN())){
                if ( markerEnd > markerStart){
                    marker = new IntervalMarker(markerStart, markerEnd);
                    marker.setPaint(new Color(0xDD, 0xFF, 0xDD, 0x80));
                    marker.setAlpha(0.5f);
                    plot.addDomainMarker(marker,Layer.BACKGROUND);

                    selectedBaseLineStart = markerStart.doubleValue();
                    selectedBaseLineEnd = markerEnd.doubleValue();

//                    lowerLabel.setText(String.format("%.2f", selectedBaseLineStart));
//                    upperLabel.setText(String.format("%.2f", selectedBaseLineEnd));
                }
            }
        }

        private Double getPosition(MouseEvent e){
            Point2D p = panel.translateScreenToJava2D( e.getPoint());
            Rectangle2D plotArea = panel.getScreenDataArea();
            XYPlot plot = (XYPlot) chart.getPlot();
            // int mouseX = e.getX();
            // int onscreen = e.getXOnScreen();
            // System.out.println("x = " + mouseX + " onscreen " + onscreen);
            return plot.getDomainAxis().java2DToValue(p.getX(), plotArea, plot.getDomainAxisEdge());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            markerEnd = getPosition(e);
            // set everything within range to true, everything else false
            updateMarker();
            lowerLabel.setText(String.format("%.2f", selectedBaseLineStart));
            upperLabel.setText(String.format("%.2f", selectedBaseLineEnd));
        }

        @Override
        public void mousePressed(MouseEvent e) {
            markerStart = getPosition(e);
            // if key pressed
        }

        public void setMarkers(double low, double high){
            this.markerStart = low;
            this.markerEnd = high;
            this.updateMarker();
        }

    }

    public static double getSelectedBaseLineStart() {
        return selectedBaseLineStart;
    }

    public static double getSelectedBaseLineEnd() {
        return selectedBaseLineEnd;
    }

    public void resetZoom(){

    }

    public void setLabelsToUpdate(JTextField lower, JTextField upper){
        lowerLabel = lower;
        upperLabel= upper;
    }

    public void updateMarkers(double lower, double upper){
        MouseMarker markers = (MouseMarker) outPanel.getMouseListeners()[2];
        markers.setMarkers(lower, upper);
    }
}
