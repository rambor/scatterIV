package version4.plots;

import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import version4.AutoRg;
import version4.Dataset;
import version4.Functions;
import version4.tableModels.AnalysisModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Created by robertrambo on 10/01/2016.
 */
public class PlotManualGuinier extends ApplicationFrame implements ChartMouseListener, ActionListener {

    private JFreeChart chart;
    public JFreeChart residualsChart;
    public JFreeChart combChart;
    public int selectedID;
    private String workingDirectoryName;

    private AnalysisModel analysisModel;
    private JLabel manualLimits;
    private Dataset datasetInUse;
    private XYSeriesCollection guinierCollection = new XYSeriesCollection();
    private XYSeries plottedData;
    private XYSeriesCollection residualsDataset = new XYSeriesCollection();
    private XYSeries plottedResiduals;
    private XYSeries yIsZero;
    CombinedDomainXYPlot combinedPlot;

    JButton button = new JButton("Save");

    JPanel buttonPanel = new JPanel();
    private XYLineAndShapeRenderer renderer1;
    private XYLineAndShapeRenderer renderer2;

    DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.US);
    DecimalFormat df = new DecimalFormat("000.0000", otherSymbols);;

    public PlotManualGuinier(final String title, Dataset dataset, String workingDirectoryName) {
        super(title);
        datasetInUse = dataset;
        selectedID = datasetInUse.getId();
        this.workingDirectoryName = workingDirectoryName;
        button.setActionCommand("SAVE_PARAM");

        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.setBackground(Color.white);
        buttonPanel.add(button);
        button.setPreferredSize(new Dimension(150, 30));

        otherSymbols.setDecimalSeparator('.');
        otherSymbols.setGroupingSeparator(',');
    }


    public void plot(AnalysisModel analysisModel) {

        // using dataset, perform autoRg?
        this.analysisModel = analysisModel;
        XYSeries datasetData = datasetInUse.getGuinierData();

        double rg, izero;
        rg = datasetInUse.getGuinierRg();
        izero = datasetInUse.getGuinierIzero();

        if (datasetInUse.getGuinierRg() <= 0 && datasetInUse.getGuinierIzero() <= 0){ // try AutoRg if not set

            AutoRg tempArg = new AutoRg(datasetInUse.getOriginalPositiveOnlyData(), datasetInUse.getStart());

            datasetInUse.setGuinierParameters(tempArg.getRg(), tempArg.getRg_error(), tempArg.getI_zero(), tempArg.getI_zero_error(), tempArg.getCorrelation_coefficient());
            rg = datasetInUse.getGuinierRg();
            izero = datasetInUse.getGuinierIzero();
        }
        // use Rg to create XYSeries dataset
        // add to newDatasetCollection

        int itemCount = datasetInUse.getGuinierData().getItemCount();
        plottedData = new XYSeries(datasetInUse.getFileName());
        plottedResiduals = new XYSeries(datasetInUse.getFileName());
        yIsZero = new XYSeries("Y-axis at zero");

        XYDataItem tempData;
        double rg2 = rg*rg;
        double slope = rg2/(-3.0);
        double intercept = Math.log(izero);

        int length=0;
        int startAt = datasetInUse.getStart()-1;
        int rglimit = (rg > 0) ? itemCount : 10;

        for(int i=startAt; i<rglimit; i++){
            tempData = datasetData.getDataItem(i);
            if (tempData.getXValue()*rg2 <= 1.69){
                tempData = datasetData.getDataItem(i); // q^2, ln[q]
                plottedData.add(tempData);
                plottedResiduals.add(tempData.getX(), tempData.getYValue() - (slope*tempData.getXValue()+intercept));
                yIsZero.add(tempData.getX(), 0);
                length++;
            } else {
                break;
            }
        }

        guinierCollection.addSeries(new XYSeries("GUINIER MODEL LINE"));
        // create residual series and line fits

        guinierCollection.getSeries(0).add(plottedData.getMinX(), slope*plottedData.getMinX()+intercept);
        guinierCollection.getSeries(0).add(plottedData.getMaxX(), slope*plottedData.getMaxX()+intercept);

        guinierCollection.addSeries(plottedData);
        // add next series which is the data used in the fit
        residualsDataset.addSeries(plottedResiduals);
        residualsDataset.addSeries(yIsZero);

        button.addActionListener(this);

        chart = ChartFactory.createXYLineChart(
                "SC\u212BTTER \u2263 Guinier fit",                // chart title
                "",                       // domain axis label
                "ln I(q)",                // range axis label
                guinierCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );

        residualsChart = ChartFactory.createXYLineChart(
                "Residuals",                // chart title
                "",                    // domain axis label
                "residuals",                  // range axis label
                residualsDataset,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );

        final XYPlot residuals = residualsChart.getXYPlot();
        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("ln [I(q)]");

        Font fnt = new Font("SansSerif", Font.BOLD, 15);
        domainAxis.setLabelFont(fnt);
        rangeAxis.setLabelFont(fnt);
        domainAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);
        String quote = "q\u00B2 (\u212B \u207B\u00B2)";
        domainAxis.setLabel(quote);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        residuals.setDomainAxis(domainAxis);
        plot.setBackgroundPaint(null);
        residuals.setBackgroundPaint(null);

        renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(false);
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesShapesVisible(0, true);
        renderer1.setSeriesShapesVisible(0, false);
        renderer1.setSeriesPaint(0, Color.red);
        renderer1.setSeriesStroke(0, datasetInUse.getStroke());

        renderer1.setSeriesPaint(1, datasetInUse.getColor());
        renderer1.setSeriesShape(1, new Ellipse2D.Double(-4, -4, 8.0, 8.0));
        renderer1.setSeriesOutlinePaint(1, datasetInUse.getColor());
        renderer1.setSeriesOutlineStroke(1, datasetInUse.getStroke());


        renderer2 = (XYLineAndShapeRenderer) residuals.getRenderer();
        renderer2.setBaseShapesVisible(true);
        renderer2.setBaseShapesFilled(false);
        renderer2.setSeriesLinesVisible(0, false);
        renderer2.setSeriesShapesVisible(1, true);
        renderer2.setSeriesShapesVisible(1, false);
        renderer2.setSeriesPaint(1, Color.red);
        renderer2.setSeriesStroke(1, datasetInUse.getStroke());
        renderer2.setSeriesPaint(0, Color.BLACK);
        renderer2.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8.0, 8.0));

        plot.getAnnotations().size();

        combinedPlot = new CombinedDomainXYPlot(new NumberAxis("2"));
        combinedPlot.setDomainAxis(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.add(plot, 2);
        combinedPlot.add(residuals, 1);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);

        combChart = new JFreeChart("Guinier Fitting", JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);

        //  combChart.addSubtitle(qRgLimits);
        combChart.removeLegend();
        combChart.setBackgroundPaint(Color.WHITE);
        ChartPanel combChartPanel = new ChartPanel(combChart);

        final JFrame manualGuinierFrame = new JFrame("SC\u212BTTER \u2263 Guinier Plot");

        final ScatterSpinner spinnerGuinierL = new ScatterSpinner(datasetInUse.getStart(), selectedID);
        final ScatterSpinner spinnerGuinierH = new ScatterSpinner(length + datasetInUse.getStart(), selectedID);

        spinnerGuinierL.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent evt){
                if (manualGuinierFrame.isVisible()){
                    guinierSpinnerLChanged(spinnerGuinierL);
                }
            }
        });

        spinnerGuinierH.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent evt){
                if (manualGuinierFrame.isVisible()){
                    guinierSpinnerHChanged(spinnerGuinierH);
                }
            }
        });

        spinnerGuinierL.setPreferredSize(new Dimension(50,30));
        spinnerGuinierL.setValue(datasetInUse.getStart());
        spinnerGuinierH.setPreferredSize(new Dimension(50,30));
        spinnerGuinierH.setValue(length + datasetInUse.getStart()); //last value of original

        datasetInUse.setIndexOfLowerGuinierFit(datasetInUse.getStart()-1);
        datasetInUse.setIndexOfUpperGuinierFit(datasetInUse.getStart()-1 + length);

        JPanel manualGuinierPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        manualGuinierPanel.setBackground(Color.WHITE);

        JPanel spinnerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        spinnerPanel.add(spinnerGuinierL);
        spinnerPanel.add(spinnerGuinierH);
        spinnerPanel.setBackground(Color.WHITE);

        JLabel manualFile = new JLabel();
        manualFile.setText(datasetInUse.getFileName());
        manualFile.setForeground(datasetInUse.getColor());
        Font fileFont = manualFile.getFont();
        Font boldFileFont = new Font(fileFont.getFontName(), Font.BOLD, fileFont.getSize());
        manualFile.setFont(boldFileFont);
        manualGuinierPanel.add(manualFile);

        manualLimits = new JLabel();

        this.updateLimits(rg);

        manualGuinierPanel.add(manualLimits);
        manualGuinierPanel.add(spinnerPanel);

        BorderLayout guinierLayout = new BorderLayout();
        manualGuinierFrame.setLayout(guinierLayout);

        manualGuinierFrame.add(combChartPanel);

        manualGuinierFrame.add(manualGuinierPanel, BorderLayout.PAGE_END);
        manualGuinierFrame.setBackground(Color.WHITE);

        manualGuinierFrame.pack();
        manualGuinierFrame.setVisible(true);
        manualGuinierFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void updateLimits(double rg){
        String qRgLimits =
                "          q \u00D7 Rg Limits: " + df.format(Math.sqrt(plottedData.getMinX())*rg) + " to " + df.format(Math.sqrt(plottedData.getMaxX())*rg) + "     ";
        manualLimits.setText(qRgLimits);
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }

    public void eraseChart(){
        guinierCollection.removeAllSeries();
    }

    @Override
    public void chartMouseClicked(ChartMouseEvent arg0) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void chartMouseMoved(ChartMouseEvent arg0) {
        throw new UnsupportedOperationException("Not supported yet.");
    }


    private void replotGuinier(int itemCount){
        double[] x_data = new double[itemCount];
        double[] y_data = new double[itemCount];
        // guinierDataset: [0] is calculated, [1] original plotted data

        XYDataItem tempXY;
        XYSeries tempXYSeries = guinierCollection.getSeries(1);

        for (int i=0; i< itemCount; i++){
            tempXY = tempXYSeries.getDataItem(i);
            x_data[i] = tempXY.getXValue();
            y_data[i] = tempXY.getYValue();
        }

        double[] param3 = Functions.leastSquares(x_data, y_data);
        double c1 = param3[0];
        double c0 = param3[1];
        double[] residualsNew = new double[itemCount];

        // line
        guinierCollection.getSeries(0).clear();
        guinierCollection.getSeries(0).add(x_data[0], x_data[0]*c1 + c0);
        guinierCollection.getSeries(0).add(x_data[itemCount-1], x_data[itemCount-1]*c1 + c0);

        // rebuild residuals dataset
        residualsDataset.getSeries(0).clear();

        for (int v=0; v< itemCount; v++) {
            residualsNew[v]=y_data[v]-(c1*x_data[v]+c0);
            residualsDataset.getSeries(0).add(x_data[v],y_data[v]-(c1*x_data[v]+c0));
        }
        residualsDataset.getSeries(1).clear();
        residualsDataset.getSeries(1).add(x_data[0], 0);
        residualsDataset.getSeries(1).add(x_data[itemCount-1], 0);

        double rg = Math.sqrt(-3.0*c1);
        double i_zero = Math.exp(c0);
        double izeroError = i_zero*param3[3];
        double rgError = 1.5*param3[2]*Math.sqrt(1/3.0*1/rg);

        this.updateLimits(rg);

        datasetInUse.setGuinierParameters(rg, rgError, i_zero, izeroError, param3[4]);

        analysisModel.fireTableDataChanged();
        //resultsModel.fireTableDataChanged();
    }
//
//
    public void guinierSpinnerLChanged(ScatterSpinner tempSpinner){
        int rowID = tempSpinner.getID();
        int current = (Integer) tempSpinner.getValue() - 1;
        datasetInUse.setIndexOfLowerGuinierFit((Integer) tempSpinner.getValue());

        if (current < 0){
            tempSpinner.setValue(1);
            tempSpinner.setPriorIndex(1);

        } else {
            // add or remove values to guinierDataset
            int direction = (Integer) tempSpinner.getValue() - tempSpinner.getPriorIndex();
            int itemCount = 0;
            // if direction is negative, add point to start of GuinierSeries
            if (direction <= 0){
                if (direction == -1) {
                    itemCount = guinierCollection.getSeries(1).getItemCount() + 1;
                    guinierCollection.getSeries(1).add(datasetInUse.getGuinierData().getDataItem(current));
                    // for updating on Analysis table
                    // add Data
                    datasetInUse.getData().add(datasetInUse.getScaledLog10DataItemAt(current));
                } else {
                    // keep adding points until currentValue
                    int start = tempSpinner.getPriorIndex() - 2;
                    int stop = ((Integer) tempSpinner.getValue()).intValue() - 1;
                    //double log10, q, q2;
                    for(int i=start; i >= stop; i--){
                        //XYDataItem tempXY = collectionSelected.getDataset(tempSpinner.getID()).getOriginalData().getDataItem(i);
                        XYDataItem tempXY = datasetInUse.getGuinierData().getDataItem(i);
                        guinierCollection.getSeries(1).add(tempXY);
                        datasetInUse.getData().add(datasetInUse.getScaledLog10DataItemAt(current));
                    }

                    itemCount = guinierCollection.getSeries(1).getItemCount();

                    // analysis spinner method
                    // keep adding points up until currentValue
                }

            } else if (direction > 0){
                if (direction == 1){
                    guinierCollection.getSeries(1).remove(0);
                    // Analysis tab
                    datasetInUse.getData().remove(0);

                } else {
                    int limit = ((Integer) tempSpinner.getValue()).intValue() - tempSpinner.getPriorIndex();
                    for (int i = 0; i < limit; i++){
                        guinierCollection.getSeries(1).remove(0);
                        // Analysis tab
                        datasetInUse.getData().remove(0);
                    }
                }
                itemCount = guinierCollection.getSeries(1).getItemCount();
            }
            //
            //analysisModel
            //
            tempSpinner.setPriorIndex((Integer) tempSpinner.getValue());
            analysisModel.setValueAt((Integer) tempSpinner.getValue(), rowID, 4);
            //analysisModel.fireTableCellUpdated(tempSpinner.getID(),4);
            analysisModel.fireTableDataChanged();

            this.replotGuinier(itemCount);
        }
    }


    public void guinierSpinnerHChanged(ScatterSpinner tempSpinner){

        int current = (Integer) tempSpinner.getValue() - 1;
        datasetInUse.setIndexOfUpperGuinierFit((Integer) tempSpinner.getValue());

        int totalValues = datasetInUse.getGuinierData().getItemCount();

        if (current+1 >= totalValues){
            //tempSpinner.setValue(tempSpinner.getPriorIndex());
            tempSpinner.setValue(totalValues);
        }

        // add or remove values to guinierDataset
        int direction = (Integer) tempSpinner.getValue() - tempSpinner.getPriorIndex();
        int itemCount;
        // if direction is positive, add point to Guinier Series
        if (direction <= 0){
            // recalculate slope and intercept
            if (direction == -1){
                guinierCollection.getSeries(1).remove(guinierCollection.getSeries(1).getItemCount() - 1 );
            } else {
                // How many points to remove
                // priorValue - startSpinner
                // 21 - 13 => 8 + 1 == size of array
                // stopValue - startSpinner
                // 17 - 13
                int stop = ((Integer) tempSpinner.getValue()).intValue();
                int size = guinierCollection.getSeries(1).getItemCount();
                int start = tempSpinner.getPriorIndex() - size;
                XYDataItem tempXY;

                guinierCollection.getSeries(1).clear();
                for(int i=start; i< stop; i++){
                    tempXY = datasetInUse.getGuinierData().getDataItem(i);
                    guinierCollection.getSeries(1).add(tempXY);
                }
            }
        } else if (direction > 0){

            //XYDataItem tempXY;
            if (direction == 1){
                //tempXY = datasetInUse.getGuinierData().getDataItem(current);
                guinierCollection.getSeries(1).add(datasetInUse.getGuinierData().getDataItem(current));
            } else {
                int start = tempSpinner.getPriorIndex();
                int stop = current + 1;
                for (int i = start; i<stop; i++){
                    //tempXY = datasetInUse.getGuinierData().getDataItem(i);
                    guinierCollection.getSeries(1).add(datasetInUse.getGuinierData().getDataItem(i));
                }
            }

        }

        itemCount = guinierCollection.getSeries(1).getItemCount();
        tempSpinner.setPriorIndex((Integer) tempSpinner.getValue());
        replotGuinier(itemCount);
    }

    public Dataset getDatasetInUse(){
        return datasetInUse;
    }

    public String getWorkingDirectoryName(){
        return this.workingDirectoryName;
    }
    // create GPA plot ?

}