package version4;

import FileManager.WorkingDirectory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.Random;

/**
 * Created by robertrambo on 16/02/2016.
 */
public class FlexibilityPlots implements ChangeListener {
    private JPanel panel1;
    private JSlider zoomSlider;
    private JLabel qmaxLabel;
    private JPanel porodPanel;
    private JPanel porodDebyePanel;
    private JPanel sibylsPanel;
    private JPanel kratkyPanel;
    private WorkingDirectory workingDirectoryName;
    private Collection collection;

    private final XYSeriesCollection porodSeries;
    private final XYSeriesCollection porodDebyeSeries;
    private final XYSeriesCollection kratkyDebyeSeries;
    private final XYSeriesCollection sibylsSeries;

    private JFreeChart porodChart;
    private JFreeChart porodDebyeChart;
    private JFreeChart kratkyDebyeChart;
    private JFreeChart sibylsChart;

    private double delta_q;

    public FlexibilityPlots(Collection collection, WorkingDirectory dir){
        this.collection = collection;
        this.workingDirectoryName = dir;

        porodSeries = new XYSeriesCollection();
        porodDebyeSeries  = new XYSeriesCollection();
        kratkyDebyeSeries = new XYSeriesCollection();
        sibylsSeries = new XYSeriesCollection();

        this.populateSeries();

        zoomSlider.addChangeListener(this);
    }

    private void populateSeries(){
        XYDataItem tempData;
        int sizeOf=0;
        int count = 0;
        delta_q = 0.0;
        double q, q2, q3, q4, i_of_q, q4_i_of_q;
        double delta_q_sum;
        delta_q_sum = 0.0;

        Random spot = new Random();

        for (int i = 0; i < collection.getDatasets().size(); i++){
            Dataset temp = collection.getDataset(i);
            if (temp.getInUse()){
                //
                // use q-values in active dataset
                // that way the intensities are always already scaled and set is truncated
                //
                porodSeries.addSeries(new XYSeries(temp.getFileName()));
                porodDebyeSeries.addSeries(new XYSeries(temp.getFileName()));
                kratkyDebyeSeries.addSeries(new XYSeries(temp.getFileName()));
                sibylsSeries.addSeries(new XYSeries(temp.getFileName()));

                sizeOf = temp.getData().getItemCount();

                for(int j = 0; j < sizeOf; j++){
                    tempData = temp.getData().getDataItem(j);
                    q = tempData.getXValue();
                    q2 = q*q;
                    q3 = q*q2;
                    q4 = q2*q2;
                    i_of_q =Math.pow(10,tempData.getYValue());
                    q4_i_of_q = q4*i_of_q;
                    porodSeries.getSeries(count).add(q, q4_i_of_q);
                    porodDebyeSeries.getSeries(count).add(q4,q4_i_of_q);
                    kratkyDebyeSeries.getSeries(count).add(q2,q2*i_of_q);
                    sibylsSeries.getSeries(count).add(q3, q3*i_of_q);
                }
                // delta_q
                // randomly pick three or four locations in the series
                // calculate delta q and then average
                int randomSpot;
                double diffs=0.0;

                for(int h=0;h<4;h++){
                    randomSpot = spot.nextInt(sizeOf-1) + 1;
                    diffs = diffs + (temp.getData().getX(randomSpot).doubleValue() - temp.getData().getX(randomSpot-1).doubleValue());
                }

                delta_q_sum = delta_q_sum + (diffs*0.25);
                count++;
            }
        }

        delta_q = delta_q_sum*(1.0/count);
        int maxValue = (int)(collection.getMaxq()/delta_q)+1;
        //zoomSlider = new JSlider(JSlider.HORIZONTAL, 0, 15, 10);
        zoomSlider.setMaximum(maxValue);
        zoomSlider.setValue(maxValue);
        zoomSlider.setMinimum(0);
        //zoomSlider.setBorder(new EmptyBorder(7,7,7,7));
    }

    public void makePlot(){

        porodPanel.add(plotPorod(porodSeries).getChartPanel());
        porodDebyePanel.add(plotPorodDebye(porodDebyeSeries).getChartPanel());
        kratkyPanel.add(plotKratkyDebye(kratkyDebyeSeries).getChartPanel());
        sibylsPanel.add(plotSibyls(sibylsSeries).getChartPanel());

        JFrame frame = new JFrame("Flexibility Plots");
        frame.setContentPane(this.panel1);
        frame.setPreferredSize(new Dimension(800,600));
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public ChartFrame plotPorod(XYSeriesCollection dataset) {
        porodChart = ChartFactory.createXYLineChart(
                "Porod Plot",                     // chart title
                "q",                    // domain axis label
                "q4 * I(q)",                // range axis label
                dataset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );
        int limit = collection.getDatasets().size();
        final XYPlot plot = porodChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q");
        final NumberAxis rangeAxis = new NumberAxis("q^4*I(q)");
        String quote = "q (\u212B \u207B\u00B9)";
        domainAxis.setLabel(quote);
        domainAxis.setLabelFont(Constants.BOLD_16);
        quote = "q\u2074 \u00D7 I(q)";
        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setLabel(quote);

        porodChart.getTitle().setFont(new java.awt.Font("Tahoma", Font.BOLD, 16));
        porodChart.getTitle().setPaint(Color.black);
        porodChart.getTitle().setMargin(8, 0, 0, 0);

        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setRangeGridlinePaint(Color.black);
        plot.setDomainGridlinePaint(Color.black);
        plot.getRangeAxis(0).setTickLabelsVisible(false);
        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();
        dotRend.setBaseLinesVisible(false);
        dotRend.setBaseShapesFilled(false);
        dotRend.setBaseShapesVisible(true);

        // seriesCollection < collection if I unclick a middle dataset
        int locale=0;
        double negativePointSize;
        for(int i=0; i < limit; i++){
            Dataset temp = collection.getDataset(i);
            if(temp.getInUse()){
                negativePointSize = -1*temp.getPointSize();
                dotRend.setSeriesShape(locale, new Ellipse2D.Double(negativePointSize, negativePointSize, temp.getPointSize(), temp.getPointSize()));
                dotRend.setSeriesShapesFilled(locale,temp.getBaseShapeFilled());
                dotRend.setSeriesLinesVisible(locale, false);
                dotRend.setSeriesPaint(locale, temp.getColor());

                locale++;
            }
        }

        plot.setDataset(0, dataset);
        plot.setRenderer(0,dotRend);

        ChartFrame porodFrame = new ChartFrame("Porod-Debye", porodChart);
        porodFrame.getChartPanel().setChart(porodChart);
        porodFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName.getWorkingDirectory()));
        porodFrame.pack();
        porodFrame.getChartPanel().setMouseZoomable(true);
        return porodFrame;
    }

    private ChartFrame plotPorodDebye(XYSeriesCollection dataset) {
        porodDebyeChart = ChartFactory.createXYLineChart(
                "Porod-Debye Plot",                     // chart title
                "",                    // domain axis label
                "",                // range axis label
                dataset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );
        int limit = collection.getDatasets().size();
        final XYPlot plot = porodDebyeChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q^4");
        final NumberAxis rangeAxis = new NumberAxis("q^4*I(q)");
        String quote = "q\u2074 (\u212B \u207B\u00B9)\u2074";
        domainAxis.setLabel(quote);
        domainAxis.setLabelFont(Constants.BOLD_16);
        quote = "q\u2074 \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        porodDebyeChart.getTitle().setFont(new java.awt.Font("Tahoma", Font.BOLD, 16));
        porodDebyeChart.getTitle().setPaint(Color.black);
        porodDebyeChart.getTitle().setMargin(8, 0, 0, 0);

        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        plot.getRangeAxis(0).setTickLabelsVisible(false);
        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();
        plot.setDataset(0, dataset);
        plot.setRenderer(0,dotRend);
        plot.setOutlineVisible(false);

        dotRend.setBaseLinesVisible(false);
        dotRend.setBaseShapesFilled(false);
        dotRend.setBaseShapesVisible(true);

        int locale=0;
        double negativePointSize;
        for(int i=0; i < limit; i++){
            Dataset temp = collection.getDataset(i);
            if(temp.getInUse()){
                negativePointSize = -1*temp.getPointSize();
                dotRend.setSeriesShape(locale, new Ellipse2D.Double(negativePointSize, negativePointSize, temp.getPointSize(), temp.getPointSize()));
                dotRend.setSeriesShapesFilled(locale,temp.getBaseShapeFilled());
                dotRend.setSeriesLinesVisible(locale, false);
                dotRend.setSeriesPaint(locale, temp.getColor());

                locale++;
            }
        }

        ChartFrame porodDebyeFrame = new ChartFrame("Porod-Debye", porodDebyeChart);
        porodDebyeFrame.getChartPanel().setChart(porodDebyeChart);
        porodDebyeFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName.getWorkingDirectory()));
        porodDebyeFrame.pack();
        porodDebyeFrame.getChartPanel().setMouseZoomable(true);
        return porodDebyeFrame;
    }

    private ChartFrame plotKratkyDebye(XYSeriesCollection dataset) {
        kratkyDebyeChart = ChartFactory.createXYLineChart(
                "Kratky-Debye Plot",                     // chart title
                "",                    // domain axis label
                "",                // range axis label
                dataset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );
        //int limit = dataset.getSeriesCount();
        int limit = collection.getDatasets().size();
        final XYPlot plot = kratkyDebyeChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q^2");
        final NumberAxis rangeAxis = new NumberAxis("q^2*I(q)");
        String quote = "q\u00B2 (\u212B \u207B\u00B9)\u00B2";
        domainAxis.setLabel(quote);
        domainAxis.setLabelFont(Constants.BOLD_16);
        quote = "q\u00B2 \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        kratkyDebyeChart.getTitle().setFont(new java.awt.Font("Tahoma", Font.BOLD, 16));
        kratkyDebyeChart.getTitle().setPaint(Color.black);
        kratkyDebyeChart.getTitle().setMargin(8, 0, 0, 0);

        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        plot.getRangeAxis(0).setTickLabelsVisible(false);
        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();
        plot.setDataset(0, dataset);
        plot.setRenderer(0,dotRend);
        plot.setOutlineVisible(false);

        dotRend.setBaseLinesVisible(false);
        dotRend.setBaseShapesFilled(false);
        dotRend.setBaseShapesVisible(true);

        int locale=0;
        double negativePointSize;
        for(int i=0; i < limit; i++){
            Dataset temp = collection.getDataset(i);
            if(temp.getInUse()){
                negativePointSize = -1*temp.getPointSize();
                dotRend.setSeriesShape(locale, new Ellipse2D.Double(negativePointSize, negativePointSize, temp.getPointSize(), temp.getPointSize()));
                dotRend.setSeriesShapesFilled(locale,temp.getBaseShapeFilled());
                dotRend.setSeriesLinesVisible(locale, false);
                dotRend.setSeriesPaint(locale, temp.getColor());

                locale++;
            }
        }

        ChartFrame kratkyDebyeFrame = new ChartFrame("Porod-Debye", kratkyDebyeChart);
        kratkyDebyeFrame.getChartPanel().setChart(kratkyDebyeChart);
        kratkyDebyeFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName.getWorkingDirectory()));
        kratkyDebyeFrame.pack();
        kratkyDebyeFrame.getChartPanel().setMouseZoomable(true);
        return kratkyDebyeFrame;
    }

    private ChartFrame plotSibyls(XYSeriesCollection dataset) {
        sibylsChart = ChartFactory.createXYLineChart(
                "SIBYLS Plot",                     // chart title
                "",                    // domain axis label
                "",                // range axis label
                dataset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );
        //int limit = collection.getSeriesCount();
        int limit = collection.getDatasets().size();
        final XYPlot plot = sibylsChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q^3");
        final NumberAxis rangeAxis = new NumberAxis("q^3*I(q)");
        String quote = "q\u00B3 (\u212B \u207B\u00B9)\u00B3";
        domainAxis.setLabel(quote);
        domainAxis.setLabelFont(Constants.BOLD_16);
        quote = "q\u00B3 \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        sibylsChart.getTitle().setFont(new java.awt.Font("Tahoma", Font.BOLD, 16));
        sibylsChart.getTitle().setPaint(Color.black);
        sibylsChart.getTitle().setMargin(8, 0, 0, 0);

        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinesVisible(false);
        plot.getRangeAxis(0).setTickLabelsVisible(false);
        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();
        plot.setDataset(0, dataset);
        plot.setRenderer(0,dotRend);
        plot.setOutlineVisible(false);

        dotRend.setBaseLinesVisible(false);
        dotRend.setBaseShapesFilled(false);
        dotRend.setBaseShapesVisible(true);

        int locale=0;
        double negativePointSize;
        for(int i=0; i < limit; i++){
            Dataset temp = collection.getDataset(i);

            if(temp.getInUse()){
                negativePointSize = -1*temp.getPointSize();
                dotRend.setSeriesShape(locale, new Ellipse2D.Double(negativePointSize, negativePointSize, temp.getPointSize(), temp.getPointSize()));
                dotRend.setSeriesShapesFilled(locale,temp.getBaseShapeFilled());
                dotRend.setSeriesLinesVisible(locale, false);
                dotRend.setSeriesPaint(locale, temp.getColor());

                locale++;
            }

        }

        ChartFrame sibylsFrame = new ChartFrame("Porod-Debye", sibylsChart);
        sibylsFrame.getChartPanel().setChart(sibylsChart);
        sibylsFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName.getWorkingDirectory()));
        sibylsFrame.pack();
        sibylsFrame.getChartPanel().setMouseZoomable(true);
        return sibylsFrame;
    }

    @Override
    public void stateChanged(ChangeEvent e) {

        JSlider source = (JSlider)e.getSource();

        double currentQ = delta_q*source.getValue();
        // on initialization, currentQ is not set and zoomSlider.getValue() returns 10
        if (currentQ <= 0.0) {
            currentQ = delta_q * 5;
        }

        double ymax = 0.0;
        double ymaxKratky=0.0;
        double ymaxSibyls=0.0;
        double currentY;
        int currCount;
        int seriesSize;
        int ymaxIndex = 0;
        int ymaxSeries = 0;

        // go through each series and determine ymax within the range
        int dataSetCount = porodSeries.getSeriesCount();

        for (int i=0; i < dataSetCount; i++){
            // iterate over values until
            currCount = 1;

            seriesSize = porodSeries.getSeries(i).getItemCount();
            while ((currCount <  seriesSize)&&(porodSeries.getSeries(i).getX(currCount).doubleValue() < currentQ)){
                currentY = porodSeries.getSeries(i).getY(currCount).doubleValue();
                //currentY = porodChart.getXYPlot().getDataset(i).getYValue(i, currCount);
                if (ymax < currentY){
                    ymax = currentY;
                    ymaxSeries = i;        // determine which series contains max Y value
                    ymaxIndex = currCount; // determine index of max Y value
                }
                currCount++;
            }
            // determine max value in kratky
            for (int k = 0; k < currCount; k++){
                currentY = kratkyDebyeSeries.getSeries(i).getY(k).doubleValue();
                //currentY = kratkyDebyeChart.getXYPlot().getDataset(i).getYValue(i,k);
                if (ymaxKratky < currentY){
                    ymaxKratky = currentY;
                }
            }

            for (int k = 0; k < currCount; k++){
                currentY = sibylsSeries.getSeries(i).getY(k).doubleValue();
                //currentY = sibylsChart.getXYPlot().getDataset(i).getYValue(i,k);
                if (ymaxSibyls < currentY){
                    ymaxSibyls = currentY;
                }
            }
        }
        float porodMax = (float)porodSeries.getSeries(ymaxSeries).getY(ymaxIndex).doubleValue();
        float porodDebyeMax = (float)porodDebyeSeries.getSeries(ymaxSeries).getY(ymaxIndex).doubleValue();

        float qmax2 = (float)(currentQ*currentQ);
        float qmax4 = qmax2*qmax2;

        qmaxLabel.setText(String.format("q_max : %.4f ", currentQ) );

        for (int i = 0; i < collection.getDatasets().size(); i++) {
            Dataset temp = collection.getDataset(i);
            if (temp.getInUse()) {
                temp.setPorodVolumeQmax(currentQ);
            }
        }


        porodChart.getXYPlot().getDomainAxis().setUpperBound(currentQ);
        porodChart.getXYPlot().getRangeAxis().setUpperBound(porodMax + 0.1*porodMax);

        porodDebyeChart.getXYPlot().getDomainAxis().setUpperBound(qmax4);
        porodDebyeChart.getXYPlot().getRangeAxis().setUpperBound(porodDebyeMax + 0.1*porodDebyeMax);

        kratkyDebyeChart.getXYPlot().getDomainAxis().setUpperBound(qmax2);
        kratkyDebyeChart.getXYPlot().getRangeAxis().setUpperBound(ymaxKratky + 0.1*ymaxKratky);

        sibylsChart.getXYPlot().getDomainAxis().setUpperBound(qmax2 * currentQ);
        sibylsChart.getXYPlot().getRangeAxis().setUpperBound(ymaxSibyls + 0.1 * ymaxSibyls);



    }
}
