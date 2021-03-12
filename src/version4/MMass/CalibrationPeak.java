package version4.MMass;

import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.Layer;
import version4.SASSignalTrace;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;

public class CalibrationPeak extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private static JFreeChart chart;
    private XYPlot massPlot;
    private XYSeriesCollection tracesCollection;
    private XYSplineRenderer splineRend = new XYSplineRenderer();
    private XYLineAndShapeRenderer selectedRegionRightRenderer;
    private XYSeriesCollection izeroCollection;
    private JTextField massField;
    private JTextField startFrameField;
    private JTextField endFrameField;
    private JPanel plotPanel;
    private JLabel massLabel;
    private JPanel mainPanel;
    private JLabel infoLabel;
    private JLabel varianceLabel;
    private double calibration_mass;
    private double k_constant = 1.0d;
    private boolean key_pressed = false;
    private ChartPanel chartPanel;

    private SASSignalTrace calibrationData;

    public CalibrationPeak(SASSignalTrace calibrationData) {

        this.calibrationData = calibrationData;

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onSet();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        plot();
        mainPanel.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
                    key_pressed = true;
                    chartPanel.setDomainZoomable(false);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                key_pressed = false;
                chartPanel.setDomainZoomable(true);
            }
        });


        massField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                warn();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                warn();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                warn();
            }

            public void warn() {
                try{

                } catch (NumberFormatException e){

                }
                if (Double.parseDouble(massField.getText()) <= 0){
                    infoLabel.setText("Mass should be larger than 0");
                } else {
                    infoLabel.setText("");
                }
            }

            public boolean check(){
                if (startFrameField.getText().length() < 2 || endFrameField.getText().length() < 2){
                    return false;
                }

                try {
                    int startValue = Integer.parseInt(startFrameField.getText());
                    int endValue = Integer.parseInt(endFrameField.getText());
                    if (startValue < endValue){
                        infoLabel.setText("");
                        return true;
                    }
                    infoLabel.setText("Start frame number should be smaller than End");
                    return false;
                } catch (NullPointerException e){
                    infoLabel.setText("Entry is not a number");
                    return false;
                }
            }
        });

        startFrameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                warn();
                if (check()){
                    update();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                warn();
                if (check()){
                    update();
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                warn();
                if (check()){
                    update();
                }
            }

            public void warn() {
                if (Integer.parseInt(startFrameField.getText())<=0){
                    JOptionPane.showMessageDialog(null,
                            "Error: Please enter number bigger than 0", "Error Message",
                            JOptionPane.ERROR_MESSAGE);
                }
            }

            public boolean check(){
                if (startFrameField.getText().length() < 2 || endFrameField.getText().length() < 2){
                    return false;
                }

                try {
                    int startValue = Integer.parseInt(startFrameField.getText());
                    int endValue = Integer.parseInt(endFrameField.getText());
                    if (startValue < endValue){
                        infoLabel.setText("");
                        return true;
                    }
                    infoLabel.setText("Start frame number should be smaller than End");
                    return false;
                } catch (NullPointerException e){
                    infoLabel.setText("Entry is not a number");
                    return false;
                }
            }

            public void update(){
                int startValue = Integer.parseInt(startFrameField.getText());
                int endValue = Integer.parseInt(endFrameField.getText());
                massPlot.clearDomainMarkers();
                Marker marker = new IntervalMarker(startValue, endValue);
                marker.setPaint(new Color(0xF4, 0xBB, 0xFF, 0x80));
                marker.setAlpha(0.5f);
                massPlot.addDomainMarker(marker, Layer.BACKGROUND);
                varianceLabel.setText(String.format("%.3f percent error", calculateError(startValue, endValue)));
            }
        });

        endFrameField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                warn();
                if (check()){
                    update();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                warn();
                if (check()){
                    update();
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                warn();
                if (check()){
                    update();
                }
            }

            public void warn() {
                if (Integer.parseInt(endFrameField.getText())<=0){
                    JOptionPane.showMessageDialog(null,
                            "Error: Please enter number bigger than 0", "Error Message",
                            JOptionPane.ERROR_MESSAGE);
                }
            }

            public boolean check(){
                if (startFrameField.getText().length() < 2 || endFrameField.getText().length() < 2){
                    return false;
                }

                try {
                    int startValue = Integer.parseInt(startFrameField.getText());
                    int endValue = Integer.parseInt(endFrameField.getText());
                    if (startValue < endValue){
                        infoLabel.setText("");
                        return true;
                    }
                    infoLabel.setText("Start frame number should be smaller than End");
                    return false;
                } catch (NullPointerException e){
                    infoLabel.setText("Entry is not a number");
                    return false;
                }
            }

            public void update(){
                int startValue = Integer.parseInt(startFrameField.getText());
                int endValue = Integer.parseInt(endFrameField.getText());
                massPlot.clearDomainMarkers();
                Marker marker = new IntervalMarker(startValue, endValue);
                marker.setPaint(new Color(0xF4, 0xBB, 0xFF, 0x80));
                marker.setAlpha(0.5f);
                massPlot.addDomainMarker(marker, Layer.BACKGROUND);
                varianceLabel.setText(String.format("%.3f percent error", calculateError(startValue, endValue)));
            }
        });

        JFrame frame = new JFrame("MSSX Plot");
        frame.setContentPane(this.contentPane);
        frame.setPreferredSize(new Dimension(800,600));
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void onSet() {
        // add your code here
        try {
            calibration_mass = Double.parseDouble(massField.getText());

            XYSeries izeroes = izeroCollection.getSeries(0);
            int startIndex = Integer.parseInt(startFrameField.getText());
            int endIndex = Integer.parseInt(endFrameField.getText()) - startIndex + 1;

            double sum = 0.0d;
            double count = 0.0d;
            k_constant = 0.0d;

            int startHere = izeroes.indexOf(startIndex);
            for(int i=startHere; i<endIndex+startHere; i++){
                sum += calibration_mass/izeroes.getY(i).doubleValue();
                count += 1.0d;
            }

            k_constant = sum/count;
            System.out.println("k_constant " + k_constant);

            for(int i=0; i<izeroes.getItemCount(); i++){
                izeroCollection.getSeries(0).updateByIndex(i, izeroes.getY(i).doubleValue()*k_constant);
            }

            massPlot.getRangeAxis(1).setLabel("Mass, kDa");

        } catch (NumberFormatException e) {
            massLabel.setText("Please provide a suitable mass > 0 in kDa");
            return;
        }
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public JPanel getPanel(){
        return contentPane;
    }

    public double calculateError(int startValue, int endValue){
        XYSeries items = calibrationData.getNormalizedIzeroes();
        double sum = 0.0d;
        double count = 0.0d;

        for(int i=startValue; i<endValue; i++){
            sum += items.getY(i).doubleValue();
            count+=1.0d;
        }

        return sum/count;
    }

    private void plot(){

        tracesCollection = new XYSeriesCollection();
        XYSeries tempSeries = calibrationData.getSecFile().getSignalSeries();
        XYSeries sasSignal = new XYSeries("SAS");

        for(int i=0; i<tempSeries.getItemCount(); i++){
            XYDataItem item = tempSeries.getDataItem(i);
            sasSignal.add(item.getX(), item.getYValue() - calibrationData.getBaseline_saxs());
        }
        tracesCollection.addSeries(sasSignal);

        izeroCollection = new XYSeriesCollection();
        tempSeries = calibrationData.getNormalizedIzeroes();
        XYSeries izeroes = new XYSeries("IIzeroes");
        for(int i=0; i<tempSeries.getItemCount(); i++){
            XYDataItem item = tempSeries.getDataItem(i);
            if (item.getYValue() > 0.0d){
                izeroes.add(item.getX(), item.getYValue());
            }
        }
        izeroCollection.addSeries(izeroes);

        chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "frame",                        // domain axis label
                "SAS Signal",                // range axis label
                tracesCollection,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        splineRend = new XYSplineRenderer();
        splineRend.setBaseShapesVisible(false);
        splineRend.setSeriesStroke(0, new BasicStroke(4.0f));
        splineRend.setSeriesPaint(0, Color.DARK_GRAY);

        selectedRegionRightRenderer = new XYLineAndShapeRenderer();
        double pointSize = 9.0;
        double negativePointSize = -0.5*pointSize;
        selectedRegionRightRenderer.setSeriesShape(0,new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
        selectedRegionRightRenderer.setSeriesShapesVisible(0, true);
        selectedRegionRightRenderer.setSeriesShapesFilled(0, true);
        selectedRegionRightRenderer.setSeriesPaint(0, Color.red);
        selectedRegionRightRenderer.setBaseShapesVisible(true);
        selectedRegionRightRenderer.setBaseLinesVisible(false);
//        selectedRegionRenderer.setSeriesPaint(count, new Color(0, 170, 255, 70));
//        selectedRegionRenderer.setSeriesOutlinePaint(count, outlineColor);

        String quoteR = "adjusted I(0)";
        NumberAxis rangeAxisRight;
        rangeAxisRight = new NumberAxis("adjusted I(0)");
        rangeAxisRight.setLabel(quoteR);
        rangeAxisRight.setLabelFont(new Font("Times", Font.BOLD, 16));
        rangeAxisRight.setLabelPaint(Color.red);
        rangeAxisRight.setAutoRange(true);
        rangeAxisRight.setAutoRangeIncludesZero(false);
        rangeAxisRight.setAutoRangeStickyZero(false);

        massPlot = chart.getXYPlot();
        massPlot.setDomainCrosshairVisible(false);
        massPlot.setRangeCrosshairVisible(false);
        massPlot.setDomainCrosshairLockedOnData(false);
        massPlot.setBackgroundAlpha(0.0f);
        massPlot.setOutlineVisible(false);

        massPlot.setDataset(0, tracesCollection);
        massPlot.setRenderer(0, splineRend);

        massPlot.setDataset(1, izeroCollection);
        massPlot.setRangeAxis(1, rangeAxisRight);
        massPlot.setRenderer(1, selectedRegionRightRenderer);       //render as a line

        massPlot.mapDatasetToRangeAxis(0, 0); //1st dataset to 2nd y-axis
        massPlot.mapDatasetToRangeAxis(1, 1); //2nd dataset to 2nd y-axis
        massPlot.setDomainCrosshairVisible(false);
        massPlot.setRangeCrosshairVisible(false);

        //massPlot.addDomainMarker(selectedDatasetMarker);
        ChartFrame chartframe = new ChartFrame("", chart); // chartpanel exists in frame

        chartPanel = chartframe.getChartPanel();
        chartPanel.addChartMouseListener(new ChartMouseListener() {

            @Override
            public void chartMouseClicked(ChartMouseEvent chartMouseEvent) {

                ChartEntity ce = chartMouseEvent.getEntity();

                if (ce instanceof XYItemEntity && key_pressed){
                    XYItemEntity e = (XYItemEntity) ce;
                    XYDataset d = e.getDataset();
                    int series = e.getSeriesIndex();
                    int index = e.getItem();

                    Number trueStart = d.getX(series, index);
                    Number trueEnd = d.getY(series,index);

                    if (trueEnd.doubleValue() > trueStart.doubleValue()){
                        System.out.println("Start " + trueStart.toString() + " END " + trueEnd.toString());
                    }
                }
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent chartMouseEvent) {

            }
        });

        plotPanel.add(chartPanel);
    }
}
