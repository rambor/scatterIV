package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Created by robertrambo on 13/01/2016.
 */
public class VcPlot {

    Collection inUse;
    String workingDirectoryName;
    int totalSets;

    private JFrame jframe = new JFrame("SC\u212BTTER \u2263 Volume-of-Correlation PLOT");

    public VcPlot(Collection data, String workingDirectoryName) {
        this.inUse = data;
        this.workingDirectoryName = workingDirectoryName;
        this.totalSets = data.getTotalDatasets();
    }

    public void plot(JLabel status){
        XYSeriesCollection qIqVcDatasets = new XYSeriesCollection();
        XYSeriesCollection qIqIntegratedDatasets = new XYSeriesCollection();

        XYSeries tempqIqData;

        XYDataItem tempqIqDataItem;

        ArrayList<JLabel> area2Label = new ArrayList<JLabel>();
        ArrayList<JLabel> area25Label = new ArrayList<JLabel>();
        ArrayList<JLabel> area3Label = new ArrayList<JLabel>();
        ArrayList<JLabel> fileNames = new ArrayList<JLabel>();

        List delq = new ArrayList();

        double izero, rg, tempArea, invtempArea;
        Random randomGenerator = new Random();

        Iterator<Dataset> sets = inUse.getDatasets().iterator();

        while (sets.hasNext()) {
            Dataset dataset = sets.next();

            if (dataset.getInUse()){

                tempqIqData = new XYSeries(dataset.getFileName());
                fileNames.add(new JLabel(dataset.getFileName()));

                if (dataset.getRealIzero() > 0){
                    izero = dataset.getRealIzero();
                    rg = dataset.getRealRg();
                } else if (dataset.getGuinierIzero() > 0) {
                    izero = dataset.getGuinierIzero();
                    rg = dataset.getGuinierRg();
                } else {
                    status.setText("Need to set Izero using Guinier or P(r) first");
                    return;
                }

                double scaleFactor = dataset.getScaleFactor();

                int itemCount = dataset.getAllData().getItemCount();
                int location = randomGenerator.nextInt(itemCount - (int)(itemCount*0.10));

                for (int j = location; j < (int)(itemCount*0.10 + location - 1); j++){
                    delq.add(Math.abs(dataset.getAllData().getX(j - 1).doubleValue() - dataset.getAllData().getX(j).doubleValue()));
                }

                double averageq = Statistics.calculateMean(delq);
                //Use deltaq to extrapolate

                qIqVcDatasets.addSeries(new XYSeries(dataset.getFileName()));

                double startq = dataset.getData().getX(0).doubleValue();
                //int start = dataset.getOriginalData().indexOf(dataset.getData().getX(0));
                int startAll = dataset.getAllData().indexOf(dataset.getData().getX(0));
                //int stop = dataset.getOriginalData().indexOf(dataset.getData().getX(0));

                double javerageq, javerageq2;
                double slope = rg*rg/3.0;
                int currentSeriesCount = qIqVcDatasets.getSeriesCount();

                // create extrapolation using Guinier estimates
                for (int j=0; j*averageq < startq; j++){
                    javerageq = j*averageq;
                    javerageq2 = (javerageq)*izero*Math.exp(-slope*javerageq*javerageq);
                    qIqVcDatasets.getSeries(currentSeriesCount-1).add(javerageq, javerageq2*scaleFactor);
                    tempqIqData.add(javerageq, javerageq2);
                }
                //
                // Using allData, integrate from startAll
                double xValue, xyValue;
                area2Label.add(new JLabel("-"));
                area25Label.add(new JLabel("-"));
                area3Label.add(new JLabel("-"));
                int label_index = area2Label.size() - 1;

                //dataset.clearPlottedQIQData();
                dataset.scalePlottedQIQData();

                allLoop:
                for (int j=startAll; j < dataset.getAllData().getItemCount(); j++){
                    tempqIqDataItem = dataset.getAllData().getDataItem(j);
                    //tempqIqDataItem = dataset.getPlottedQIQDataSeries().getDataItem(j);
                    xValue = tempqIqDataItem.getXValue();
                    xyValue = xValue*tempqIqDataItem.getYValue();

                    qIqVcDatasets.getSeries(currentSeriesCount-1).add(xValue, xyValue*scaleFactor);
                    tempqIqData.add(xValue, xyValue);

                    if ((xValue >= 0.2) && (xValue < 0.25)){
                        tempArea = Functions.trapezoid_integrate(tempqIqData);
                        //area2Label.set(label_index, new JLabel(scientific.format(tempArea)));
                        area2Label.get(label_index).setText(Constants.Scientific2.format(tempArea));
                    } else if ((xValue >= 0.25)&&(xValue < 0.3)){
                        tempArea = Functions.trapezoid_integrate(tempqIqData);
                        area25Label.get(label_index).setText(Constants.Scientific2.format(tempArea));

                        if (tempArea > 0) {
                            invtempArea = 1.0/tempArea;
                            dataset.setVC(dataset.getGuinierIzero()*invtempArea);
                            dataset.setVCReal(dataset.getRealIzero()*invtempArea);
                        }
                    } else if (xValue >= 0.30 && xValue <= 0.32){

                        tempArea = Functions.trapezoid_integrate(tempqIqData);
                        area3Label.get(label_index).setText(Constants.Scientific2.format(tempArea));

                        if (tempArea > 0) {
                            invtempArea = 1/tempArea;
                            dataset.setVC(dataset.getGuinierIzero()*invtempArea);
                            dataset.setVCReal(dataset.getRealIzero()*invtempArea);
                        }
                    }
                }

                /*
                 * calculate molecular mass
                 */
                double qr = Math.pow(dataset.getVCReal(),2)/dataset.getRealRg();
                double mass = qr/0.1231;
//                dataset.setMassProteinReal((int) Math.floor(mass));
//                dataset.setMassRnaReal((int)Math.pow(qr/0.00934, 0.808));

                qr = Math.pow(dataset.getVC(),2)/dataset.getGuinierRg();
                mass = qr/0.1231;
//                dataset.setMassProtein((int) Math.floor(mass));
//                dataset.setMassRna((int)Math.pow(qr/ 0.00934, 0.808));
                // table update (results tab)
                qIqIntegratedDatasets.addSeries(new XYSeries(dataset.getFileName()));
                XYSeries tempExtrapolated = Functions.qIqIntegral( qIqVcDatasets.getSeries(qIqVcDatasets.getSeriesCount()-1));

                for (int j=0; j < tempExtrapolated.getItemCount(); j++){
                    qIqIntegratedDatasets.getSeries(qIqIntegratedDatasets.getSeriesCount()-1).add(tempExtrapolated.getX(j), tempExtrapolated.getY(j).doubleValue()) ;
                }
            } // end if
        }

        JPanel vcPanel = new JPanel(new GridBagLayout());
        JPanel vcTabs = new JPanel(new GridBagLayout());

        GridBagConstraints vcpanels = new GridBagConstraints();

        vcPanel.setBackground(Color.WHITE);

        vcpanels.fill = GridBagConstraints.HORIZONTAL;
        vcpanels.gridx = 0;
        vcpanels.gridy = 0;
        vcPanel.add(plotqIqVc(qIqVcDatasets).getChartPanel(), vcpanels);
        vcpanels.fill = GridBagConstraints.HORIZONTAL;
        vcpanels.gridx = 1;
        vcpanels.gridy = 0;
        vcPanel.add(plotIntegratedVc(qIqIntegratedDatasets).getChartPanel(), vcpanels);

        GridBagConstraints vc = new GridBagConstraints();

        vcTabs.setBackground(Color.WHITE);

        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.gridx = 0;
        vc.gridy = 0;
        vc.anchor = GridBagConstraints.LINE_START;

        JLabel headerItemVc1 = new JLabel();
        JLabel headerItemVc2 = new JLabel();
        JLabel headerItemVc3 = new JLabel();
        JLabel headerItemVc4 = new JLabel();
        JLabel vcDescription = new JLabel();
        vcDescription.setBorder(new EmptyBorder(20,20,20,20));

        headerItemVc1.setText("<html>q<sub>max</sub>: 0.20</html>");
        headerItemVc1.setHorizontalAlignment(JLabel.CENTER);
        headerItemVc2.setText("<html>q<sub>max</sub>: 0.25</html>");
        headerItemVc2.setHorizontalAlignment(JLabel.CENTER);
        headerItemVc3.setText("<html>q<sub>max</sub>: 0.30</html>");
        headerItemVc3.setHorizontalAlignment(JLabel.CENTER);
        headerItemVc4.setText("<html>(Integrated up to q<sub>max</sub>)</html>");
        headerItemVc4.setHorizontalAlignment(JLabel.LEFT);
        vcDescription.setText("<html>Guinier extrapolated dataset (left) plotted as q &middot; I(q). The integral approaches a constant value at high q (right).<ul><li>Integral of q &middot; I(q) is proportional to the particle's correlation length, l<sub>c</sub>. <li>The ratio of I(0) to l<sub>c</sub> defines the Volume-per-Correlation length, V<sub>c</sub>.<li>Strong divergence at high q suggests poor buffer subtraction. <li>Ratio of V<sub>c</sub><sup>2</sup>/R<sub>g</sub> defines an invariant proportional to mass.</html>");
        vcDescription.setBackground(Color.WHITE);

        headerItemVc1.setSize(120, 30);
        headerItemVc1.setFont(new java.awt.Font("Times", Font.BOLD, 14));
        headerItemVc1.setForeground(Constants.DodgerBlue);
        vc.ipadx = 20;
        vcTabs.add(headerItemVc1, vc);

        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.gridx = 1;
        vc.gridy = 0;
        vc.ipadx = 20;
        headerItemVc2.setFont(new java.awt.Font("Times", Font.BOLD, 14));
        headerItemVc2.setForeground(Constants.DodgerBlue);
        headerItemVc2.setSize(120, 30);
        vcTabs.add(headerItemVc2, vc);

        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.gridx = 2;
        vc.gridy = 0;
        vc.ipadx = 20;
        headerItemVc3.setFont(new java.awt.Font("Times", Font.BOLD, 14));
        headerItemVc3.setForeground(Constants.DodgerBlue);
        headerItemVc3.setSize(120, 30);
        vcTabs.add(headerItemVc3, vc);

        vc.fill = GridBagConstraints.HORIZONTAL;
        vc.gridx = 3;
        vc.gridy = 0;
        vc.ipadx = 20;
        vc.anchor = GridBagConstraints.LINE_END;
        headerItemVc4.setFont(new java.awt.Font("Times", Font.BOLD, 14));
        headerItemVc4.setForeground(Constants.SteelBlue);
        headerItemVc4.setSize(120, 30);
        vcTabs.add(headerItemVc4, vc);
        //
        int k=0;
        for(int m =0; m < totalSets; m++){
            if (inUse.getDataset(m).getInUse()){
                vc.fill = GridBagConstraints.HORIZONTAL;
                vc.gridx = 0;
                vc.gridy = k+1;
                area2Label.get(k).setSize(80, 30);
                area2Label.get(k).setHorizontalAlignment(JLabel.CENTER);
                vcTabs.add(area2Label.get(k), vc);

                vc.fill = GridBagConstraints.HORIZONTAL;
                vc.gridx = 1;
                vc.gridy = k+1;

                area25Label.get(k).setSize(80, 30);
                area25Label.get(k).setHorizontalAlignment(JLabel.CENTER);
                vcTabs.add(area25Label.get(k), vc);

                vc.fill = GridBagConstraints.HORIZONTAL;
                vc.gridx = 2;
                vc.gridy = k+1;
                area3Label.get(k).setHorizontalAlignment(JLabel.CENTER);
                area3Label.get(k).setSize(80, 30);
                vcTabs.add(area3Label.get(k), vc);

                vc.fill = GridBagConstraints.HORIZONTAL;
                vc.gridx = 3;
                vc.gridy = k+1;
                vc.ipadx = 20;
                //ArrayList of Jlabels
                fileNames.get(k).setForeground(inUse.getDataset(m).getColor());
                fileNames.get(k).setFont(Constants.BOLD_16);
                vcTabs.add(fileNames.get(k), vc);
                k++;
            }
        }

        //Add bottom panel spanning two columns
        vcpanels.fill = GridBagConstraints.HORIZONTAL;
        vcpanels.gridx = 0;
        vcpanels.gridy = 1;
        vcpanels.gridwidth=2;
        vcPanel.add(vcTabs, vcpanels);


        vcpanels.fill = GridBagConstraints.HORIZONTAL;
        vcpanels.gridx = 0;
        vcpanels.gridy = 2;
        vcpanels.gridwidth=2;
        vcpanels.ipady = 20;
        vcPanel.add(vcDescription, vcpanels);

        jframe = new JFrame("SC\u212BTTER \u2263 Volume-of-Correlation");
        jframe.setBackground(Color.white);
        jframe.getContentPane().add(vcPanel);
        vcPanel.validate();
        vcPanel.repaint();
        jframe.pack();
        /*
        for (int i=0; i < limit; i++){
            if (checkBoxList.get(i).isSelected()){
                // all series are rendered false, turn to true for selected
                plotIntegratedVcChart.getXYPlot().getRenderer().setSeriesVisible(i, true);
                plotqIqVcChart.getXYPlot().getRenderer().setSeriesVisible(i, true);
            }
        }
        */
        jframe.setResizable(false);
        jframe.setVisible(true);
        jframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private ChartFrame plotIntegratedVc(XYSeriesCollection dataset){
        JFreeChart plotIntegratedVcChart;

        plotIntegratedVcChart = ChartFactory.createXYLineChart(
                "Integrated Area of q \u00D7 I(q)",                     // chart title
                "q",                        // domain axis label
                "Integral I(q)",                // range axis label
                dataset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        ChartFrame plotIntegratedVcFrame = new ChartFrame("Integrated Vc", plotIntegratedVcChart);
        plotIntegratedVcChart.getTitle().setFont(new java.awt.Font("Tahoma", 1, 14));
        plotIntegratedVcChart.getTitle().setPaint(Constants.SteelBlue);
        plotIntegratedVcChart.getTitle().setMargin(00, 0, 0, 0);

        final XYPlot plot = plotIntegratedVcChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("");
        String quote = "q (\u212B \u207B\u00B9)";
        domainAxis.setLabel(quote);
        quote = "\u222B q \u00D7 I(q) dq";
        rangeAxis.setLabel(quote);
        rangeAxis.setRange(0, dataset.getRangeUpperBound(true) + 0.1*dataset.getRangeUpperBound(true));

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setBackgroundPaint(null);
        XYSplineRenderer rendereriVc = new XYSplineRenderer();
        plot.setRenderer(0, rendereriVc);

        rendereriVc.setBaseLinesVisible(true);
        rendereriVc.setBaseShapesVisible(false);
        rendereriVc.setBaseShapesFilled(false);

        //set dot size for all series
        int count=0;
        for (int i=0; i < inUse.getDatasets().size(); i++){

            Dataset temp = inUse.getDataset(i);
            if (temp.getInUse()){
                rendereriVc.setSeriesLinesVisible(count, true);
                //rendereriVc.setSeriesShape(count, new Ellipse2D.Double(-3.0, -3.0, 3.0, 3.0));
                rendereriVc.setSeriesShapesFilled(count, false);
                rendereriVc.setSeriesVisible(count, true);
                rendereriVc.setSeriesPaint(count, temp.getColor());
                rendereriVc.setSeriesStroke(count, new BasicStroke(2));
                count++;
            }
        }

        plotIntegratedVcFrame.getChartPanel().setChart(plotIntegratedVcChart);
        plotIntegratedVcFrame.getChartPanel().setSize(390, 300);
        plotIntegratedVcFrame.getChartPanel().setPreferredSize(new Dimension(390,300));
        plotIntegratedVcFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        plotIntegratedVcFrame.pack();

        return plotIntegratedVcFrame;
    }

    private ChartFrame plotqIqVc(XYSeriesCollection dataset){
        JFreeChart plotqIqVcChart;

        plotqIqVcChart = ChartFactory.createXYLineChart(
                "Total Scattered Intensity",                     // chart title
                "q",                        // domain axis label
                "qI(q)",                // range axis label
                dataset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        ChartFrame plotqIqVcFrame = new ChartFrame("Integrated Vc", plotqIqVcChart);
        plotqIqVcChart.getTitle().setFont(new java.awt.Font("Tahoma", 1, 14));
        plotqIqVcChart.getTitle().setPaint(Constants.SteelBlue);
        plotqIqVcChart.getTitle().setMargin(0, 0, 0, 0);

        final XYPlot plot = plotqIqVcChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("");
        String quote = "q (\u212B \u207B\u00B9)";
        domainAxis.setLabel(quote);
        quote = "q \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setRange(0, dataset.getRangeUpperBound(true) + 0.1*dataset.getRangeUpperBound(true));
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setBackgroundPaint(null);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(true);

        //set dot size for all series

        //set dot size for all series
        int count=0;
        for (int i=0; i < inUse.getDatasets().size(); i++){
            Dataset temp = inUse.getDataset(i);
            if (temp.getInUse()){
                renderer1.setSeriesShape(count, new Ellipse2D.Double(-5.0, -5.0, 5.0, 5.0));
                renderer1.setSeriesLinesVisible(count, false);
                renderer1.setSeriesShapesFilled(count, false);
                renderer1.setSeriesVisible(count, true);
                renderer1.setSeriesPaint(count, temp.getColor());
                //renderer1.setSeriesStroke(count, new BasicStroke(2));
                count++;
            }
        }


        plotqIqVcFrame.getChartPanel().setChart(plotqIqVcChart);
        plotqIqVcFrame.getChartPanel().setSize(390, 300);
        plotqIqVcFrame.getChartPanel().setPreferredSize(new Dimension(390, 300));
        plotqIqVcFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        plotqIqVcFrame.pack();
        return plotqIqVcFrame;
    }
}