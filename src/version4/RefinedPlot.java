package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;

/**
 * Created by robertrambo on 13/02/2016.
 */
public class RefinedPlot {
    private JPanel panel1;
    private JPanel refinedPanel;
    private JPanel refinedInfoPanel;
    private JLabel rejectionInfo;
    private JLabel refinedTitleLabel;
    private JLabel shannonLabel;
    private RealSpace dataset;

    private JFreeChart chart;
    private ChartPanel outPanel;

    private boolean crosshair = true;
    private BasicStroke strokeAt2 = new BasicStroke(2.5f);
    private XYSeriesCollection splineCollection;
    private XYSeriesCollection scatterCollection;

    private XYSplineRenderer splineRend = new XYSplineRenderer();
    private XYLineAndShapeRenderer renderer1;

    private LegendTitle legend;
    private boolean showLegend = false;

    public RefinedPlot(RealSpace dataset){
        this.dataset = dataset;

        splineCollection = new XYSeriesCollection();
        scatterCollection = new XYSeriesCollection();
        // plot rejected set in red
        // add keptSeries and fittedqIq
        XYSeries qiq = new XYSeries("keptiqi");
        XYSeries reject = new XYSeries("rejected");


        XYSeries temp = dataset.getRefinedqIData();
        int totalKept = temp.getItemCount();
        XYDataItem tempItem;

        for(int i=0; i<totalKept; i++){ // take the kept series which is I(q) vs q and transform it
            tempItem = temp.getDataItem(i);
            qiq.add(tempItem.getX(), tempItem.getXValue()*tempItem.getYValue());
        }

        temp = dataset.getfittedqIq();
        for(int i=0; i < dataset.getfittedqIq().getItemCount(); i++){ // scaled data
            tempItem = temp.getDataItem(i);
            if (dataset.getRefinedqIData().indexOf(tempItem.getX()) < 0){ // true means rejected
                reject.add(tempItem.getX(), tempItem.getYValue());
            }
        }

        scatterCollection.addSeries(reject);
        scatterCollection.addSeries(qiq);

        dataset.calculateIntensityFromModel(true);  //

        splineCollection.addSeries(dataset.getCalcqIq());
    }


    public void makePlot(String output){ // qIq plot

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
        domainAxis.setLabelFont(Constants.BOLD_16);

        rangeAxis.setLabel("q × I(q)");
        rangeAxis.setLabelFont(Constants.BOLD_16);

        rangeAxis.setAutoRange(true);
        rangeAxis.setAutoRangeStickyZero(false);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairLockedOnData(true);
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairVisible(true);  //make crosshair visible
        plot.setRangeCrosshairVisible(true);   //make crosshair visible

        plot.setOutlineVisible(false);

        renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(false);

        plot.setDataset(0, splineCollection);   //Moore Function
        plot.setRenderer(0, splineRend);        //render as a line
        plot.setDataset(1, scatterCollection);  //IofQ Data
        plot.setRenderer(1, renderer1);         //render as points
        splineRend.setBaseShapesVisible(false);

        int n_index = 1;
        double invdmax = Math.PI/dataset.getDmax();
        double qmaxLimit = dataset.getfittedqIq().getMaxX();
        while(n_index*invdmax < qmaxLimit){
            Marker temp = new ValueMarker(n_index*invdmax);
            temp.setLabel(Integer.toString(n_index));
            temp.setPaint(Constants.DodgerBlue);
            temp.setLabelFont(Constants.FONT_12);
            temp.setLabelPaint(Constants.DodgerBlue);

            //plot.addDomainMarker(new ValueMarker(n_index*invdmax));
            plot.addDomainMarker(temp);

            n_index++;
        }

        double delta;
        double pointSize;

        delta = -dataset.getPointSize()*0.5*2;
        pointSize = dataset.getPointSize()*2;

        renderer1.setSeriesShape(0, new Ellipse2D.Double(delta*0.9, delta*0.9, pointSize*0.9, pointSize*0.9));
        renderer1.setSeriesShape(0, new Ellipse2D.Double(delta*0.9, delta*0.9, pointSize*0.9, pointSize*0.9));

        renderer1.setSeriesShapesFilled(0, true);
        renderer1.setSeriesLinesVisible(0, false);
        renderer1.setSeriesPaint(0, Color.red);
        renderer1.setSeriesOutlinePaint(0, Color.red);
        renderer1.setSeriesOutlineStroke(0, dataset.getStroke());

        renderer1.setSeriesShape(1, new Ellipse2D.Double(delta, delta, pointSize, pointSize));
        renderer1.setSeriesShapesFilled(1, dataset.getBaseShapeFilled());
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesPaint(1, dataset.getColor());
        renderer1.setSeriesOutlinePaint(1, dataset.getColor());
        renderer1.setSeriesOutlineStroke(1, dataset.getStroke());

        splineRend.setSeriesStroke(0, strokeAt2);
        splineRend.setSeriesPaint(0, Color.BLACK); // make color slight darker
        splineRend.setSeriesVisibleInLegend(0, Boolean.FALSE);


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
        //outPanel.setDefaultDirectoryForSaveAs(new File(this.workingDirectory.getWorkingDirectory()));
        refinedPanel.add(outPanel);

        refinedTitleLabel.setText("Cross-Validation Plot for P(r)-distribution");
        rejectionInfo.setText(output);
        shannonLabel.setText("Vertical markers denote cardinal points for the Shannon set");

        JFrame frame = new JFrame("Plot");
        frame.setContentPane(this.panel1);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    }

}
