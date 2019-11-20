package version4;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;

/**
 * Created by robertrambo on 28/01/2016.
 */
public class PofRPlot {
    private static JFreeChart chart;
    private static ChartFrame frame = new ChartFrame("", chart);
    private ChartPanel outPanel;
    private  boolean crosshair = true;

    private XYSeriesCollection plottedCollection;
    private XYSeriesCollection splineCollection = new XYSeriesCollection();
    private XYSeriesCollection pdbCollection = new XYSeriesCollection();

    private XYSplineRenderer splineRend = new XYSplineRenderer();
    //    private XYBarRenderer barRend = new XYBarRenderer();
//    private Monotonic splineRend = new Monotonic();
    private XYLineAndShapeRenderer renderer1 = new XYSplineRenderer();

    private static PofRPlot singleton = new PofRPlot( );

    private BasicStroke axisStroke = new BasicStroke(1.5f);
    private Color axisColor = Color.black;

    private PofRPlot(){

        plottedCollection = new XYSeriesCollection();
        splineCollection = new XYSeriesCollection();
    }

    /* Static 'instance' method */
    public static PofRPlot getInstance() {
        return singleton;
    }

    public void plot(Collection collection, WorkingDirectory workingDirectory, JPanel panelForPlot) {

        plottedCollection = new XYSeriesCollection();
        splineCollection = new XYSeriesCollection();
        //collectionInUse = collection;

        int totalC = collection.getTotalDatasets();

        for (int i=0; i<totalC; i++){
            if (collection.getDataset(i).getInUse()){
                splineCollection.addSeries(collection.getDataset(i).getRealSpaceModel().getPrDistribution());
                //System.out.println(i + " TOTAL IN PR " +collection.getDataset(i).getRealSpaceModel().getPrDistribution().getItemCount());
            }
        }

        pdbCollection.removeAllSeries();

        chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "r",                        // domain axis label
                "P(r)",                // range axis label
                plottedCollection,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        final XYPlot plot = chart.getXYPlot();

        final NumberAxis domainAxis = new NumberAxis("r, \u212B");
        final NumberAxis rangeAxis = new NumberAxis("P(r)");
        domainAxis.setAutoRangeIncludesZero(true);
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

                int size;
                double min = 1000;
                double max = -10;
                double dmax = 0;
                double tempMax;
                double tempdMax;
                boolean isVisible = false;
                for (int i=0; i < seriesCount; i++){
                    //check if visible, if visible, get min and max I value
                    //isVisible = super.getChart().getXYPlot().getRenderer().isSeriesVisible(i);
                    isVisible = super.getChart().getXYPlot().getRenderer(0).isSeriesVisible(i);
                    if (isVisible){
                        size = super.getChart().getXYPlot().getDataset(0).getItemCount(i);

                        for(int j =1; j < size; j++) {
                            tempMax = super.getChart().getXYPlot().getDataset(0).getYValue(i, j);
                            tempdMax = super.getChart().getXYPlot().getDataset(0).getXValue(i, j);
                            if (tempMax > max){
                                max = tempMax;
                            }

                            if (tempdMax > dmax){
                                dmax = tempdMax;
                            }

                            if (tempMax<min){
                                min=tempMax;
                            }
                        }
                    }
                }

                min = (min < 0) ? (min + 0.1*min) : (-0.1*min);

                super.getChart().getXYPlot().getRangeAxis().setRange(min, max+0.1*max);
                super.getChart().getXYPlot().getDomainAxis().setRange(0, dmax+2);
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

        renderer1 = new XYSplineRenderer();

        splineRend.setBaseShapesVisible(false);
        renderer1.setBaseShapesVisible(true);

        renderer1.setBaseStroke(new BasicStroke(2.0f));

        // renderer1.setBaseLinesVisible(false);
        int locale = 0;
        //double negativePointSize;
        for (int i=0; i < collection.getDatasets().size(); i++){
            Dataset temp = collection.getDataset(i);
            if (temp.getInUse()) {
                //splineRend.setSeriesOutlineStroke();
                double delta = -temp.getPointSize()*0.5;
                double pointSize = temp.getPointSize();
                splineRend.setSeriesShape(locale, new Ellipse2D.Double(delta, delta, pointSize, pointSize));
                splineRend.setSeriesStroke(locale, temp.getStroke());
                splineRend.setSeriesPaint(locale, temp.getColor().darker()); // make color slight darker
                //   chart.getXYPlot().getRenderer(0).setSeriesVisible(locale, true);
                locale++;
            }
        }

//        int precision = splineRend.getPrecision();
//        System.out.println("PRECISION " + precision);
//        splineRend.setPrecision(precision*3);

        plot.setDataset(0, splineCollection);  //Moore Function
        plot.setRenderer(0, splineRend);       //render as a line
        //plot.setRenderer(0, barRend);

        plot.setDataset(1, pdbCollection); //PDB data
        plot.setRenderer(1, renderer1);

        ValueMarker mark = new ValueMarker(0, Color.BLACK, new BasicStroke(2));
        plot.addRangeMarker(mark);

        // plot.addRangeMarker(mark);
        // plot.addDomainMarker(mark);

        //frame.getChartPanel().setBorder(b);
        //frame.getContentPane().add(chartPanel);
        //frame.getChartPanel().setChart(chart);
        //cPanel.setPreferredSize( new Dimension(-1,380) );

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


        outPanel.setDefaultDirectoryForSaveAs(new File(workingDirectory.getWorkingDirectory()));
        //outPanel.getChart().setBorderVisible(false);
        outPanel.getChart().getXYPlot().setOutlineVisible(false);
        panelForPlot.removeAll();
        panelForPlot.add(outPanel);
    }

    public void showBins(boolean state){
        this.splineRend.setBaseShapesVisible(state);
    }

    public void changeVisible(int index, boolean state){
        chart.getXYPlot().getRenderer(0).setSeriesVisible(index, state);
    }

    public void clear(){
        this.pdbCollection.removeAllSeries();
        this.splineCollection.removeAllSeries();
    }

    public boolean inUse(){
        if (splineCollection.getSeriesCount() > 0){
            return true;
        }
        return false;
    }

    public void setPDBPofR(XYSeries pdbData){
        this.pdbCollection.removeAllSeries();
        this.pdbCollection.addSeries(pdbData);
    }

    public void removePDB(){
        this.pdbCollection.removeAllSeries();
    }

    public void changeColor(int id, Color newColor, float thickness){
        splineRend.setSeriesPaint(id, newColor);
        chart.getXYPlot().getRenderer(0).setSeriesStroke(id, new BasicStroke(thickness));
    }

}