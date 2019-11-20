package version4;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;

/**
 * Created by robertrambo on 28/01/2016.
 */
public class IofQPofRPlot {

    private JFreeChart chart;
    private ChartPanel outPanel;
    private ValueMarker yMarker = new ValueMarker(0.0);
    private boolean crosshair = true;
    private BasicStroke strokeAt2 = new BasicStroke(2.5f);
    private XYSeriesCollection splineCollection;
    private XYSeriesCollection scatterCollection;
    private Collection collectionInUse;
    private WorkingDirectory workingDirectory;

    private BasicStroke axisStroke = new BasicStroke(1.5f);
    private Color axisColor = Color.black;

    private XYSplineRenderer splineRend = new XYSplineRenderer();
    private XYLineAndShapeRenderer renderer1;

    private LegendTitle legend;
    private boolean showLegend = false;
    private static IofQPofRPlot singleton = new IofQPofRPlot( );

    private IofQPofRPlot(){
        splineCollection = new XYSeriesCollection();
        scatterCollection = new XYSeriesCollection();
    }

    /* Static 'instance' method */
    public static IofQPofRPlot getInstance() {
        return singleton;
    }

    public void plot(Collection collection, WorkingDirectory workingDirectory, JPanel panelForPlot, boolean useQIQ) {

        collectionInUse = collection;
        this.workingDirectory = workingDirectory;

        int totalC = collectionInUse.getTotalDatasets();

        for(int i=0;i<totalC; i++){
            if (collectionInUse.getDataset(i).getInUse()){
                scatterCollection.addSeries(collectionInUse.getDataset(i).getRealSpaceModel().getLogData());
                splineCollection.addSeries(collectionInUse.getDataset(i).getRealSpaceModel().getCalcIq());
            }
        }

        chart = ChartFactory.createXYLineChart(
                "",                         // chart title
                "q",                        // domain axis label
                "",                         // range axis label
                new XYSeriesCollection(),   // data
                PlotOrientation.VERTICAL,
                true,                      // include legend
                false,
                false
        );

        legend = chart.getLegend();
        //chart.removeLegend();
        chart.getLegend().setVisible(false);

        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q, \u212B \u207B\u00B9");
        final NumberAxis rangeAxis = new NumberAxis("log [I(q)]");
        domainAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRange(true);

        if (useQIQ){
            rangeAxis.setLabel("q × I(q)");
        } else {
            rangeAxis.setLabel("log [I(q)]");
        }


        yMarker.setPaint(Color.black);
        yMarker.setStroke(new BasicStroke(
                2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1.0f, new float[] {6.0f, 6.0f}, 0.0f
        ));
        chart.getXYPlot().addRangeMarker(yMarker);

        yMarker.setAlpha(0.0f);

        rangeAxis.setAutoRange(true);
        rangeAxis.setAutoRangeStickyZero(false);

        rangeAxis.setAxisLineStroke(axisStroke);
        domainAxis.setAxisLineStroke(axisStroke);

        rangeAxis.setTickMarkStroke(axisStroke);
        domainAxis.setTickMarkStroke(axisStroke);


        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairVisible(true); //make crosshair visible
        plot.setRangeCrosshairVisible(true);  //make crosshair visible
        //plot.setDomainZeroBaselineVisible(false);
        plot.setOutlineVisible(false);

        splineRend = new XYSplineRenderer();
        renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(false);

        plot.setDataset(0, splineCollection);  //Moore Function
        plot.setRenderer(0, splineRend);       //render as a line
        plot.setDataset(1, scatterCollection); //IofQ Data
        plot.setRenderer(1, renderer1);        //render as points
        splineRend.setBaseShapesVisible(false);


        int locale = 0;
        double delta;
        double pointSize;

        for (int i=0; i < collection.getDatasets().size(); i++){
            Dataset temp = collection.getDataset(i);
            if (temp.getInUse()) {
                delta = -temp.getPointSize()*0.5;
                pointSize = temp.getPointSize();
                renderer1.setSeriesShape(locale, new Ellipse2D.Double(delta, delta, pointSize, pointSize));
                renderer1.setSeriesShapesFilled(locale, temp.getBaseShapeFilled());
                renderer1.setSeriesLinesVisible(locale, false);
                renderer1.setSeriesPaint(locale, temp.getColor());
                renderer1.setSeriesOutlinePaint(locale, temp.getColor());
                renderer1.setSeriesOutlineStroke(locale, temp.getStroke());
                //renderer1.getLegendItem(1,locale).getLabel()

                splineRend.setSeriesStroke(locale, strokeAt2);
                splineRend.setSeriesPaint(locale, Color.RED); // make color slight darker

                splineRend.setSeriesVisibleInLegend(locale, Boolean.FALSE);
                locale++;
            }
        }

        outPanel = new ChartPanel(chart){
            @Override
            public void restoreAutoBounds(){
                super.restoreAutoDomainBounds();
                super.restoreAutoRangeBounds();
                super.getChart().getXYPlot().getRangeAxis().setAutoRange(false);
                int seriesCount = super.getChart().getXYPlot().getDataset(1).getSeriesCount();
                int maxIndex=0;
                double min = 100000;
                double max = -100000;
                double tempYMin;
                double tempYmax;
                boolean isVisible;

                for (int i=0; i < seriesCount; i++){
                    //check if visible, if visible, get min and max I value
                    isVisible = super.getChart().getXYPlot().getRenderer(1).isSeriesVisible(i);

                    tempYmax = super.getChart().getXYPlot().getDataset(1).getYValue(i, 0);

                    if (tempYmax > max){
                        max = tempYmax;
                    }
                    if (isVisible){
                        maxIndex = super.getChart().getXYPlot().getDataset(1).getItemCount(i)-3;
                        for (int j=0; j< maxIndex;j++){
                            tempYMin = super.getChart().getXYPlot().getDataset(1).getYValue(i, j);
                            if (tempYMin < min){
                                min = tempYMin;
                            }
                            if (tempYMin > max){
                                max = tempYMin;
                            }
                        }
                    }
                }

                if (max < 0){
                    max = 0;
                }

                super.getChart().getXYPlot().getRangeAxis().setRange(min - Math.abs(min*2.1), max + Math.abs(max*0.5));
            }
        };


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


        popup.add(new JMenuItem(new AbstractAction("Legend ?") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                if (!showLegend){
                    //chart.addLegend(legend);
                    chart.getLegend().setVisible(true);
                    showLegend = true;
                } else {
                    //chart.removeLegend();
                    chart.getLegend().setVisible(false);
                    showLegend = false;
                }

            }
        }));


        outPanel.getInsets().set(0, 0, 0, 0);
        outPanel.setDefaultDirectoryForSaveAs(new File(this.workingDirectory.getWorkingDirectory()));
        panelForPlot.removeAll();
        panelForPlot.add(outPanel);
    }

    public void changeVisible(int index, boolean state){
        chart.getXYPlot().getRenderer(0).setSeriesVisible(index, state);
        chart.getXYPlot().getRenderer(1).setSeriesVisible(index, state);
    }

    public void changeToQIQ(){

        // replace scatterCollection and spline collection series
        chart.getXYPlot().getRangeAxis().setLabel("q × I(q)");
        yMarker.setAlpha(1.0f);
        int totalC = collectionInUse.getTotalDatasets();

        scatterCollection.removeAllSeries();
        splineCollection.removeAllSeries();

        for(int i=0;i<totalC; i++){
            //System.out.println(i + " InUse ? " + collectionInUse.getDataset(i).getInUse());
            if (collectionInUse.getDataset(i).getInUse()){
                collectionInUse.getDataset(i).getRealSpaceModel().calculateQIQ();
                scatterCollection.addSeries(collectionInUse.getDataset(i).getRealSpaceModel().getfittedqIq());
                splineCollection.addSeries(collectionInUse.getDataset(i).getRealSpaceModel().getCalcqIq());
            }
        }

    }

    public void changeToIQ(){

        // replace scatterCollection and spline collection series
        int totalC = collectionInUse.getTotalDatasets();
        chart.getXYPlot().getRangeAxis().setLabel("log [I(q)]");
        scatterCollection.removeAllSeries();
        splineCollection.removeAllSeries();

        yMarker.setAlpha(0.0f);

        for(int i=0;i<totalC; i++){
            if (collectionInUse.getDataset(i).getInUse()){
                collectionInUse.getDataset(i).getRealSpaceModel().calculateIofQ();
                scatterCollection.addSeries(collectionInUse.getDataset(i).getRealSpaceModel().getLogData());
                splineCollection.addSeries(collectionInUse.getDataset(i).getRealSpaceModel().getCalcIq());
            }
        }
    }

    public void changeColor(int id, Color newColor, float thickness, int pointsize){
        double delta = -pointsize*0.5;
        chart.getXYPlot().getRenderer(1).setSeriesPaint(id, newColor);
        chart.getXYPlot().getRenderer(1).setSeriesShape(id, new Ellipse2D.Double(delta, delta, pointsize, pointsize));
        chart.getXYPlot().getRenderer(1).setSeriesOutlineStroke(id, new BasicStroke(thickness));
    }

    public void clear(){
        this.scatterCollection.removeAllSeries();
        this.splineCollection.removeAllSeries();
    }

    public boolean inUse(){
        if (this.scatterCollection.getSeriesCount() >0){
            return true;
        }
        return false;
    }

}
