package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

public class StructureFactor {
    private JSpinner lowQSpinner;
    private JSpinner highQSpinner;
    private JComboBox comboBox1;
    private JPanel JPlotPanel;
    private JPanel panel1;

    private Dataset structureFactorDataset;
    private Dataset formFactorDataset;
    private XYSeries calculateIntensities;
    private XYSeries structureFactorData;
    private XYSeries plottedDataForFitting;
    private XYSeries plottedDataForFittingError;
    private JFreeChart chart;
    private String workingDirectoryName;
    public ChartFrame frame;
    public ChartPanel chartPanel;
    private final int maxHighQIndex;
    private int currentLowQIndex;
    /**
     * use P(r)-distribution to calculate intensities over q-range
     * Input dataset assumes to be both form factor and structure factor
     *
     * @param dataset
     */
    public StructureFactor(Dataset dataset, String workingDirectoryName){
        this.structureFactorDataset = dataset;
        this.formFactorDataset = dataset;
        this.calculateIntensitiesFromModel();
        this.workingDirectoryName = workingDirectoryName;

        lowQSpinner.setValue(1);
        currentLowQIndex = 1;
        highQSpinner.setValue(this.structureFactorData.getItemCount());
        maxHighQIndex = this.structureFactorData.getItemCount();

        lowQSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {

                int valueOfSpinner = (Integer)lowQSpinner.getValue();

                if (((Integer)lowQSpinner.getValue() < 1)){
                    currentLowQIndex = 1;
                    lowQSpinner.setValue(1);
                } else {
                    //moving up or down?
                    int direction = valueOfSpinner - currentLowQIndex;
                    if (direction > 0) { // remove point

                        for(int i=0; i<direction; i++){
                            plottedDataForFitting.remove(0);
                        }

                    } else if (direction < 0){ // add point

                        direction = Math.abs(direction);
                        int addIt = currentLowQIndex;
                        for(int i=0; i<direction; i++){
                            addIt -= 1;
                            plottedDataForFitting.add(structureFactorData.getDataItem(addIt));
                        }

                    }
                    currentLowQIndex = valueOfSpinner;
                }
            }
        });


        highQSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {

                if (((Integer)highQSpinner.getValue() > maxHighQIndex)){

                    highQSpinner.setValue(maxHighQIndex);
                } else {

                    int valueOfSpinner = (Integer)highQSpinner.getValue();

                    //moving up or down?
                    int currentIndex = structureFactorData.indexOf(plottedDataForFitting.getX(plottedDataForFitting.getItemCount()-1));

                    int direction = valueOfSpinner - (currentIndex+1);
                    if (direction > 0) { // add point
                        int addIt = currentIndex+1;
                        for(int i=0; i<direction; i++){
                            plottedDataForFitting.add(structureFactorData.getDataItem(addIt));
                            addIt += 1;
                        }

                    } else if (direction < 0){ // remove point
                        direction = Math.abs(direction);
                        for(int i=0; i<direction; i++){
                            plottedDataForFitting.remove(plottedDataForFitting.getItemCount()-1);
                        }
                    }

                }
            }
        });
    }


    /**
     * manually specify dataset containing structure factor and form factor separately
     * @param structureFactorDataset
     * @param formFactorDataset
     * @param workingDirectoryName
     */
    public StructureFactor(Dataset structureFactorDataset, Dataset formFactorDataset, String workingDirectoryName){
        this.structureFactorDataset = structureFactorDataset;
        this.formFactorDataset = formFactorDataset;
        this.calculateIntensitiesFromModel();
        this.workingDirectoryName = workingDirectoryName;

        lowQSpinner.setValue(1);
        currentLowQIndex = 1;
        highQSpinner.setValue(this.structureFactorData.getItemCount());
        maxHighQIndex = this.structureFactorData.getItemCount();

        lowQSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {

                int valueOfSpinner = (Integer)lowQSpinner.getValue();

                if (((Integer)lowQSpinner.getValue() < 1)){
                    currentLowQIndex = 1;
                    lowQSpinner.setValue(1);
                } else {
                    //moving up or down?
                    int direction = valueOfSpinner - currentLowQIndex;
                    if (direction > 0) { // remove point

                        for(int i=0; i<direction; i++){
                            plottedDataForFitting.remove(0);
                        }

                    } else if (direction < 0){ // add point

                        direction = Math.abs(direction);
                        int addIt = currentLowQIndex;
                        for(int i=0; i<direction; i++){
                            addIt -= 1;
                            plottedDataForFitting.add(structureFactorData.getDataItem(addIt));
                        }

                    }
                    currentLowQIndex = valueOfSpinner;
                }
            }
        });


        highQSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {

                if (((Integer)highQSpinner.getValue() > maxHighQIndex)){

                    highQSpinner.setValue(maxHighQIndex);
                } else {

                    int valueOfSpinner = (Integer)highQSpinner.getValue();

                    //moving up or down?
                    int currentIndex = structureFactorData.indexOf(plottedDataForFitting.getX(plottedDataForFitting.getItemCount()-1));

                    int direction = valueOfSpinner - (currentIndex+1);
                    if (direction > 0) { // add point
                        int addIt = currentIndex+1;
                        for(int i=0; i<direction; i++){
                            plottedDataForFitting.add(structureFactorData.getDataItem(addIt));
                            addIt += 1;
                        }

                    } else if (direction < 0){ // remove point
                        direction = Math.abs(direction);
                        for(int i=0; i<direction; i++){
                            plottedDataForFitting.remove(plottedDataForFitting.getItemCount()-1);
                        }
                    }

                }
            }
        });
    }


    /**
     * calculate Intensities from Pr-Model and perform the Structure Factor division
     */
    private void calculateIntensitiesFromModel(){

        XYSeries allData = structureFactorDataset.getAllData();
        RealSpace realspace = formFactorDataset.getRealSpaceModel();
        int totalInAllData = allData.getItemCount();

        calculateIntensities = new XYSeries("Calculated");
        structureFactorData = new XYSeries("StructureFactor");
        plottedDataForFitting  = new XYSeries("plottedSFData");
        plottedDataForFittingError  = new XYSeries("Error");
        XYDataItem tempXY;

        double qmaxLimit = realspace.getQmax();

        double value;
        for(int i=0; i<totalInAllData; i++){
            tempXY = allData.getDataItem(i);
            if (tempXY.getXValue() < qmaxLimit){
                calculateIntensities.add(tempXY.getXValue(), realspace.getICalcAtQ(tempXY.getXValue()));
                value = 1.0/calculateIntensities.getY(i).doubleValue();
                structureFactorData.add(tempXY.getXValue(), tempXY.getYValue()*value);
                plottedDataForFitting.add(structureFactorData.getDataItem(i));
                plottedDataForFittingError.add(tempXY.getX(), structureFactorDataset.getAllDataError().getY(i).doubleValue()*value);
            } else {
                break;
            }
        }

        // perform division
    }

    public void createPlot(){
        chart = ChartFactory.createXYLineChart(
                "",                         // chart title
                "q",                   // domain axis label
                "S(q)",                // range axis label
                new XYSeriesCollection(plottedDataForFitting),                 // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );


        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q");
        final NumberAxis rangeAxis = new NumberAxis("S(q)");

        String quote = "q, \u212B \u207B\u00B9";
        domainAxis.setLabel(quote);
        domainAxis.setAutoRangeStickyZero(true);
        domainAxis.setLabelFont(Constants.BOLD_16);
        domainAxis.setTickLabelFont(Constants.FONT_12);

        //domainAxis.setAxisLineStroke(new BasicStroke(16));
        //domainAxis.setAxisLinePaint(Color.BLACK);
        //domainAxis.setAxisLineVisible(true);

        quote = "S(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRangeStickyZero(true);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setTickLabelFont(Constants.FONT_12);

        chart.getLegend().setVisible(false);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);

        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setBackgroundAlpha(0.0f);
        plot.setOutlineVisible(false);

        chartPanel = new ChartPanel(chart);

        chartPanel.getPopupMenu().add(new JMenuItem(new AbstractAction("Export Plotted Data") {
            @Override
            public void actionPerformed(ActionEvent e) {
                //To change body of implemented methods use File | Settings | File Templates.
                ExportData temp = new ExportData(plottedDataForFitting, plottedDataForFittingError, workingDirectoryName, "StructureFactor");
                temp.pack();
                temp.setVisible(true);
            }
        }));


        JPlotPanel.removeAll();
        JPlotPanel.add(chartPanel);

        frame = new ChartFrame("S(q) PLOT", chart);
        frame.setContentPane(this.panel1);
        frame.setPreferredSize(new Dimension(800,600));
        frame.getChartPanel().setDisplayToolTips(true);
        frame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
