package version4;


import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import version4.tableModels.AnalysisModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Created by robertrambo on 12/01/2016.
 */
public class VolumePlot {

    static JFreeChart chart;
    XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
    private Dataset dataset;
    private Collection inUseCollection;

    XYSeries volumeKratky1st;
    XYSeries volumeKratky2nd;
    XYSeries powerLaw1st;
    XYSeries powerLaw2nd;
    XYSeries volumePorod1st;
    XYSeries volumePorod2nd;
    XYSeries volumePorod3rd;
    XYSeries volumeInvariant1st;
    XYSeries volumeInvariant2nd;
    XYSeries tempSeries1;
    XYSeries tempSeries2;
    XYSeries calcPorod;
    XYSeries volumePorodLine;
    XYSeries powerLawSeries;
    XYSeries powerLawTopSeries;

    private XYSeriesCollection volumePorodCollection;
    private XYSeriesCollection volumeKratkyCollection;
    private XYSeriesCollection volumePowerLawCollection;

    private JLabel oldQ;
    private JLabel oldV;
    private JLabel exponentV;
    private JLabel porodVolumeExponent;

    private Number firstPowerLaw, lastPowerLaw;

    private String workingDirectoryName;


    public static ChartFrame frame = new ChartFrame("SC\u212BTTER \u2263 q \u00D7 PARTICLE CHARACTERIZATION", chart);
    static JFrame volumePFrame = new JFrame("SC\u212BTTER \u2263 PARTICLE CHARACTERIZATION");

    XYLineAndShapeRenderer renderer1;
    private AnalysisModel analModel;
    public boolean crosshair = true;

    /* A private Constructor prevents any other
     * class from instantiating.
     */
    public VolumePlot(Dataset data, String workingDirectoryName, AnalysisModel aModel) {
        this.dataset = data;
        this.workingDirectoryName = workingDirectoryName;
        this.analModel = aModel;

        volumePorodCollection = new XYSeriesCollection();
        volumeKratkyCollection = new XYSeriesCollection();
        volumePowerLawCollection = new XYSeriesCollection();


        JPopupMenu popup = frame.getChartPanel().getPopupMenu();
        popup.add(new JMenuItem(new AbstractAction("Toggle Crosshair") {
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
        }));
    }

    public void plot() {

        volumeKratky1st = new XYSeries("volumeKratky1st");
        volumeKratky2nd = new XYSeries("volumeKratky2nd");
        powerLaw1st = new XYSeries("powerlaw1st");
        powerLaw2nd = new XYSeries("powerlaw2nd");
        volumePorod1st = new XYSeries("volumePorod1st");
        volumePorod2nd = new XYSeries("volumePorod2nd");
        volumePorod3rd = new XYSeries("volumePorod3rd");
        volumeInvariant1st = new XYSeries("volumeInvariant1st");
        volumeInvariant2nd = new XYSeries("volumeInvariant2nd");
        tempSeries1 = new XYSeries("tempSeries1");
        tempSeries2 = new XYSeries("tempSeries2");
        calcPorod = new XYSeries("Porod Calc");
        volumePorodLine = new XYSeries("Porod Volume");
        powerLawSeries = new XYSeries("Power Law");
        powerLawTopSeries = new XYSeries("Power Law");

        int endPoint = dataset.getOriginalPositiveOnlyData().getItemCount() - 1;
        int qmaxEndPoint = endPoint/2;

        if (dataset.getPorodVolumeQmax() > 0.001){
            for(int i=0; i<dataset.getOriginalPositiveOnlyData().getItemCount(); i++){
                if(dataset.getOriginalPositiveOnlyData().getX(i).doubleValue() > dataset.getPorodVolumeQmax()){
                    qmaxEndPoint = i;
                    break;
                }
            }
        }

        int startPoint =0;
        double[] volumePorodX = new double[qmaxEndPoint];
        double[] volumePorodY = new double[qmaxEndPoint];
        double[] powerLawX = new double[qmaxEndPoint];
        double[] powerLawY = new double[qmaxEndPoint];

        XYDataItem tempXY;
        XYSeries posOnly = dataset.getOriginalPositiveOnlyData();
        double q4, tempX, tempY, q4y, q2y, logq, logI;

        for (int i = startPoint; i < endPoint; i++){
            if (i < qmaxEndPoint) {
                tempXY = posOnly.getDataItem(i);

                tempX = tempXY.getXValue();
                logq = Math.log(tempX);

                tempY = tempXY.getYValue();
                logI = Math.log(tempY);

                q4=tempX*tempX*tempX*tempX;
                q4y = q4*tempXY.getYValue();
                q2y = tempX*tempX*tempY;

                volumeKratky1st.add(tempX, q2y);
                //volumeKratky1st.add(logq, logI);

                volumePorod1st.add(q4, q4y);
                volumePorod2nd.add(q4, q4y);
                volumePorod3rd.add(q4, q4y);
                volumeKratky2nd.add(tempX, q2y);
                tempSeries1.add(tempX, tempY);
                volumePorodX[i] = q4;
                volumePorodY[i] = q4y;

                powerLaw1st.add(logq, logI);
                powerLaw2nd.add(logq, logI);
                powerLawX[i] = logq;
                powerLawY[i] = logI;
            } else if (i > qmaxEndPoint) {
                //2nd half used for fitting against model functions
                tempXY = posOnly.getDataItem(i);

                tempX = tempXY.getXValue();
                tempY = tempXY.getYValue();

                q4=tempX*tempX*tempX*tempX;
                q4y = q4*tempXY.getYValue();
                q2y = tempX*tempX*tempY;

                volumeKratky2nd.add(tempX, q2y);
                volumePorod2nd.add(q4, q4y);
                tempSeries1.add(tempX, tempY);

                powerLaw2nd.add(Math.log(tempX), Math.log(tempY));
            }
        }

        firstPowerLaw = powerLaw1st.getX(5);
        lastPowerLaw = powerLaw1st.getMaxX();

//        volumeKratkyCollection.addSeries(volumeKratky1st);
//        volumeKratkyCollection.addSeries(volumeKratky2nd);
        volumeKratkyCollection.addSeries(powerLaw1st);
        volumeKratkyCollection.addSeries(powerLaw2nd);

        volumePorodCollection.addSeries(volumePorod1st); //red dots
        volumePorodCollection.addSeries(volumePorod3rd); //black dot

        double izeroError = 1.0;
        double rgError = 1.0;
        //Create Preliminary fit to line
        double[] tempArray = Functions.leastSquares(volumePorodX, volumePorodY);
        //Calculate predicted values
        int limit = volumePorodX.length;
        for(int i =0; i< limit; i++){
            calcPorod.add(volumePorodX[i], volumePorodX[i]*tempArray[0] + tempArray[1]);
        }

        //Create line for the plot which will be the third series in the plot
        volumePorodLine.clear();
        volumePorodLine.add(0, tempArray[1]);
        volumePorodLine.add(volumePorodX[volumePorodX.length-1], volumePorodX[volumePorodX.length-1]*tempArray[0] + tempArray[1]);
        volumePorodCollection.addSeries(volumePorodLine);

        //returns XYSeries upto endpoint of input XYSeries
        tempSeries2 = Functions.porodInvariant(tempSeries1, dataset.getGuinierIzero(), izeroError, dataset.getGuinierRg(), rgError);

        //split into half
        endPoint = tempSeries2.getItemCount();
        for (int i = 0; i < endPoint; i++){
            if (i < qmaxEndPoint) {
                volumeInvariant1st.add(tempSeries2.getX(i), tempSeries2.getY(i));
            } else {
                volumeInvariant2nd.add(tempSeries2.getX(i), tempSeries2.getY(i));
            }
        }

        // Calculate preliminary Q and volume using first element q_un = volumeKratky2nd[0]
        // Q is calculated from volumeKratky1st
        //
        double porodInvariantOld = tempArray[1]/volumeKratky2nd.getMinX() + volumeInvariant1st.getMaxY();
        int volumeOld = (int)(Constants.TWO_PI_2*dataset.getGuinierIzero()/porodInvariantOld);
        //powerlaw line
        tempArray = Functions.leastSquares(powerLawX,powerLawY);

        //Calculate residuals
        powerLawSeries.clear();
        powerLawTopSeries.clear();
        for (int i =0; i< powerLawX.length; i++){
            powerLawSeries.add(Math.exp(powerLawX[i]), (powerLawY[i] - (powerLawX[i]*tempArray[0] + tempArray[1])));
        }

        powerLawTopSeries.add(firstPowerLaw, (firstPowerLaw.doubleValue()*tempArray[0] + tempArray[1]));
        powerLawTopSeries.add(lastPowerLaw, (lastPowerLaw.doubleValue()*tempArray[0] + tempArray[1]));

        volumeKratkyCollection.addSeries(powerLawTopSeries);
        volumePowerLawCollection.addSeries(powerLawSeries);

        double porodExponent = -1.0*tempArray[0];

        //Setting Layout Manager
        volumePFrame = new JFrame("SC\u212BTTER \u2263 Particle Characterization");
        GridLayout volumeLayout = new GridLayout(2,2);
        volumeLayout.preferredLayoutSize(volumePFrame);

        volumePFrame.setMaximumSize(new Dimension(850,800));
        volumePFrame.setPreferredSize(new Dimension(850,800));
        volumePFrame.setLayout(volumeLayout);
        GridBagConstraints constraint = new GridBagConstraints();

        JPanel volumeTabs = new JPanel(new GridBagLayout());
        volumeTabs.setSize(390, 300);
        volumeTabs.setBackground(Color.white);

        JLabel headerItem1Va = new JLabel();
        JLabel headerItem1Vb = new JLabel();
        JLabel headerItem1Vc = new JLabel();
        headerItem1Va.setText("Use arrows to manually define linear region in Porod Plot (left).");
        headerItem1Vb.setText("<html>Fit determines the Porod Invariant, Q, and Exponent, P. <ul><li>Slope of the line must be positive<li>Distribution of residuals should be unbiased<li>Must be repeated if I(zero) changes</html>");
        headerItem1Vc.setText("<html>For more information, please see:<ul><li>Glatter O and Kratky O. <i>Small Angle X-ray Scattering</i>. (1982):28 <li>Fiegn LA and Svergun DI. <i>Structure Analysis by SAX/NS</i>. (1987):76-81<li>Rambo RP and Tainer JA. <i>Biopolymers</i>. (2011):559-571 </ul></html>");
        headerItem1Va.setFont(new Font("Tahoma",Font.PLAIN, 12));
        headerItem1Vb.setFont(new Font("Tahoma",Font.PLAIN, 12));
        headerItem1Vc.setFont(new Font("Tahoma",Font.PLAIN, 12));

        Dimension spinnerWideDimension = new Dimension(80,30);
        Dimension volDim = new Dimension(70,40);

        JLabel headerItem1V = new JLabel();
        headerItem1V.setText("Start");
        headerItem1V.setPreferredSize(spinnerWideDimension);
        headerItem1V.setHorizontalAlignment(JLabel.CENTER);

        JLabel headerItem1VT = new JLabel();
        headerItem1VT.setText("End");
        headerItem1VT.setPreferredSize(spinnerWideDimension);
        headerItem1VT.setHorizontalAlignment(JLabel.CENTER);

        JLabel headerItem2V = new JLabel();
        headerItem2V.setText("Q");
        headerItem2V.setPreferredSize(volDim);
        headerItem2V.setHorizontalAlignment(JLabel.CENTER);

        JLabel headerItem3V = new JLabel();
        String quote = "V (\u212b\u00B3)";
        headerItem3V.setText(quote);
        headerItem3V.setPreferredSize(volDim);
        headerItem3V.setHorizontalAlignment(JLabel.CENTER);

        JLabel headerItem4V = new JLabel();
        JLabel headerItem5V = new JLabel();
        JLabel headerItem6V = new JLabel();
        JLabel headerItem7V = new JLabel();
        JLabel headerItem8V = new JLabel();
        JLabel headerItem9V = new JLabel();
        headerItem4V.setText("P");
        headerItem4V.setPreferredSize(volDim);
        headerItem4V.setHorizontalAlignment(JLabel.CENTER);
        headerItem5V.setText("");
        headerItem5V.setPreferredSize(volDim);
        headerItem5V.setHorizontalAlignment(JLabel.CENTER);
        headerItem6V.setText("");
        headerItem6V.setPreferredSize(volDim);
        headerItem6V.setHorizontalAlignment(JLabel.CENTER);
        headerItem7V.setText("");
        headerItem7V.setPreferredSize(volDim);
        headerItem7V.setHorizontalAlignment(JLabel.CENTER);
        headerItem8V.setText("");
        headerItem9V.setText("");

        JLabel headerItem17V = new JLabel("");

        oldQ = new JLabel();
        oldV = new JLabel();
        exponentV = new JLabel();
        porodVolumeExponent = new JLabel();

        oldQ.setHorizontalAlignment(JLabel.CENTER);
        oldV.setHorizontalAlignment(JLabel.CENTER);
        exponentV.setHorizontalAlignment(JLabel.CENTER);
        porodVolumeExponent.setHorizontalAlignment(JLabel.CENTER);

        JLabel surfaceVolume = new JLabel();
        JLabel averageV = new JLabel();

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 0;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem1V, constraint);

        constraint.fill = GridBagConstraints.HORIZONTAL;  //Spinner Kratky
        constraint.gridx = 1;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem1VT, constraint);  //Q text

        constraint.fill = GridBagConstraints.HORIZONTAL;  //Spinner Kratky
        constraint.gridx = 2;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem2V, constraint);  //Q text

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 3;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem3V, constraint);  //V text

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 4;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem4V, constraint);  //Spinner low

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 5;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem5V, constraint);  //Spinner hi

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 6;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem6V, constraint);  //V text

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 7;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem7V, constraint);  //Q text

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 8;
        constraint.gridy = 0;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem9V, constraint); //BLANK text

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 0;
        constraint.gridy = 1;

        final ScatterSpinner jSpinnerVolK = new ScatterSpinner(1, dataset.getId());
        final ScatterSpinner jSpinnerVolKHi = new ScatterSpinner(volumePorod1st.getItemCount(), dataset.getId());

        jSpinnerVolK.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSpinnerVolKStateChanged(jSpinnerVolK);
            }
        });

        jSpinnerVolKHi.addChangeListener(new javax.swing.event.ChangeListener() {
            @Override
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jSpinnerVolKhiStateChanged(jSpinnerVolKHi);
            }
        });

        jSpinnerVolK.setPreferredSize(new Dimension(50, 30));
        jSpinnerVolK.setValue(1);
        volumeTabs.add(jSpinnerVolK, constraint); //Spinner

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 1;
        constraint.gridy = 1;

        jSpinnerVolKHi.setPreferredSize(new Dimension(50, 30));
        jSpinnerVolKHi.setValue(volumePorod1st.getItemCount());
        volumeTabs.add(jSpinnerVolKHi, constraint); //Spinner

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 2;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        oldQ.setText(Constants.ThreeDecPlace.format(porodInvariantOld));
        volumeTabs.add(oldQ, constraint); //Text Q from calculation

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 3;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        oldV.setText(Integer.toString(volumeOld));
        volumeTabs.add(oldV, constraint); //Text V from calculation

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 4;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        porodVolumeExponent.setText(Constants.OneDecPlace.format(porodExponent));
        volumeTabs.add(porodVolumeExponent, constraint); //Porod Exponent
        //  exponentV
        //  porodVolumeExponent
        //  surfaceVolume
        //  averageV
        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 5;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        volumeTabs.add(exponentV, constraint); //Volume from Power-law

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 6;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        volumeTabs.add(surfaceVolume, constraint); // Surface-to-Volume ratio

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 7;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        volumeTabs.add(averageV, constraint); //Average volume

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 8;
        constraint.gridy = 1;
        constraint.ipadx = 20;
        volumeTabs.add(headerItem17V, constraint);  //BLANK text

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 0;
        constraint.gridy = 2;
        constraint.ipady = 20;
        constraint.ipadx = 20;
        constraint.gridwidth = 9;
        volumeTabs.add(headerItem1Va, constraint);

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 0;
        constraint.gridy = 3;
        constraint.ipady = 10;
        constraint.ipadx = 20;
        constraint.gridwidth = 9;
        volumeTabs.add(headerItem1Vb, constraint);

        constraint.fill = GridBagConstraints.HORIZONTAL;
        constraint.gridx = 0;
        constraint.gridy = 4;
        constraint.ipady = 10;
        constraint.ipadx = 20;
        constraint.gridwidth = 9;
        volumeTabs.add(headerItem1Vc, constraint);
        volumePFrame.getContentPane().add(plotVolumeKratky(volumeKratkyCollection, volumeKratky2nd.getMaxX()).getChartPanel());
        volumePFrame.getContentPane().add(plotVolumeInvariant(volumePowerLawCollection, powerLawSeries.getMaxX()).getChartPanel());
        volumePFrame.getContentPane().add(plotVolumePorod(volumePorodCollection, volumeKratky1st.getMaxX()).getChartPanel());
        volumePFrame.getContentPane().add(volumeTabs);
        volumePFrame.pack();
        volumePFrame.setVisible(true);
    }

    public boolean isVisible() {
        return volumePFrame.isVisible();
    }

    public void changeVisibleSeries(int index, boolean flag) {

        boolean isVisible = renderer1.isSeriesVisible(index);

        if (isVisible) {
            renderer1.setSeriesVisible(index, flag);
        } else {
            Dataset temp = inUseCollection.getDataset(index);
            temp.scalePlottedQIQData();
            renderer1.setSeriesVisible(index, flag);
        }
    }

    private void jSpinnerVolKStateChanged(ScatterSpinner tempSpinner) {
        if (!volumePFrame.isVisible()){
            return;
        }
        spinnerVolumeKratky(tempSpinner, "low");
    }


    private void jSpinnerVolKhiStateChanged(ScatterSpinner tempSpinner) {
        if (!volumePFrame.isVisible()){
            return;
        }
        spinnerVolumeKratky(tempSpinner, "high");
    }

    private void spinnerVolumeKratky(ScatterSpinner spinner, String type){
        //given the selected file
        //Determine the spinner range
        //Calculate linear fit to the define region from Porod Plot

        int current = Integer.parseInt(spinner.getValue().toString());
        int result;
        int diff;
        int fIndex = spinner.getID();

        if (current == 0 && type.equals("low")){
            spinner.setValue(1);
            return;
        } else if (current == 0 && type.equals("high")) {
            spinner.setValue(volumePorod1st.getItemCount());
            return;
        }
/*
        if ((current >= Integer.parseInt(jSpinnerVolKhi.getValue().toString())) && type.equals("low")){
            jSpinnerVolK.setValue(1);
            //recalculate the curve
        }
*/
        if (current < 1 && type.equals("low")){
            spinner.setValue(spinner.getPriorIndex());
            return;
        } else if (((current > (volumePorod2nd.getItemCount())) && type.equals("high")) || (current == 0 && type.equals("high"))) {
            spinner.setValue(spinner.getPriorIndex());
            return;
        }

        //Under renovation
        if (type.equals("low")) {
            result = (Integer)spinner.getValue();
            int lowKratky = spinner.getPriorIndex();
            diff = lowKratky - result;
            if (diff == 1) { //if diff > 0, add point to start
                int index = result-1;
                volumePorod1st.add(volumePorod2nd.getX(index), volumePorod2nd.getY(index));
                powerLaw1st.add(powerLaw2nd.getX(index), powerLaw2nd.getY(index));
            } else if (diff == -1) {
                int index = volumePorod1st.indexOf(volumePorod2nd.getX(result-2));
                //remove point from start (previous value)
                volumePorod1st.remove(index);
                powerLaw1st.remove(index);
            } else if (diff > 1) { //if user types in a number in spinner, must truncate or add to/from that number
                for (int i =lowKratky; (i > result || i == 0); i--){
                    volumePorod1st.add(volumePorod2nd.getX(i), volumePorod2nd.getY(i));
                    powerLaw1st.add(powerLaw2nd.getX(i), powerLaw2nd.getY(i));
                }
            } else if (diff < 1) { //remove data
                for (int i = (lowKratky); i < result; i++){
                    volumePorod1st.remove(0);
                    powerLaw1st.remove(0);
                }
            }
            spinner.setPriorIndex(result);

        } else if (type.equals("high")) {
            result = (Integer)spinner.getValue();
            int hiKratky = spinner.getPriorIndex();
            diff = hiKratky - result;
            if (diff == 1) { //if diff > 0, remove point from end
                int index = volumePorod1st.indexOf(volumePorod2nd.getX(result));
                int index2 = volumeKratky1st.indexOf(volumeKratky2nd.getX(result));
                int index3 = volumePorod3rd.indexOf(volumePorod2nd.getX(result));
                volumePorod1st.remove(index);
                powerLaw1st.remove(index);
                volumeKratky1st.remove(index2);
                volumePorod3rd.remove(index3);
                spinner.setPriorIndex(result);
            } else if (diff == -1) {
                //Add point to end
                int index = (Integer)spinner.getValue() - 1;
                volumePorod1st.add(volumePorod2nd.getDataItem(index));
                powerLaw1st.add(powerLaw2nd.getDataItem(index));
                volumeKratky1st.add(volumeKratky2nd.getDataItem(index));
                volumePorod3rd.add(volumePorod2nd.getDataItem(index));
                spinner.setPriorIndex(result);
            } else if (diff > 1) {
                // result is where I am going
                // diff is how much
                for (int i=0; i < diff; i++){
                    volumePorod1st.remove(volumePorod1st.getItemCount()-1);
                    powerLaw1st.remove(powerLaw1st.getItemCount()-1);
                    volumeKratky1st.remove(volumeKratky1st.getItemCount()-1);
                    volumePorod3rd.remove(volumePorod3rd.getItemCount()-1);
                }
                spinner.setPriorIndex(result);

            } else if (diff < 1) { // add points to end
                for (int i=0; i < Math.abs(diff); i++){
                    volumePorod1st.add(volumePorod2nd.getDataItem(hiKratky+i));
                    powerLaw1st.add(powerLaw2nd.getDataItem(hiKratky+i));
                    volumeKratky1st.add(volumeKratky2nd.getDataItem(hiKratky+i));
                    volumePorod3rd.add(volumePorod2nd.getDataItem(hiKratky+i));
                }
                spinner.setPriorIndex(result);
                //hiKratky = Integer.parseInt(jSpinnerVolKhi.getValue().toString());
            }
            //if user types in a number in spinner, must truncate from that number
        }
        //calculate new slope and intercept for fit
        //Create Preliminary fit to line
        double[] volumePorodX = new double[volumePorod1st.getItemCount()];
        double[] volumePorodY = new double[volumePorod1st.getItemCount()];
        double[] powerLawX = new double[powerLaw1st.getItemCount()];
        double[] powerLawY = new double[powerLaw1st.getItemCount()];

        //Calculate oldQ, oldV
        int limit = volumePorodX.length;
        for(int i =0; i < limit; i++){
            volumePorodX[i] = volumePorod1st.getX(i).doubleValue();
            volumePorodY[i] = volumePorod1st.getY(i).doubleValue();
            powerLawX[i] = powerLaw1st.getX(i).doubleValue();
            powerLawY[i] = powerLaw1st.getY(i).doubleValue();
        }

        double[] tempArray = Functions.leastSquares(volumePorodX, volumePorodY);

        volumePorodLine.clear();
        // Add first and last points
        volumePorodLine.add(0, tempArray[1]);
        volumePorodLine.add(volumePorodX[volumePorodX.length-1], volumePorodX[volumePorodX.length-1]*tempArray[0] + tempArray[1]);

        int index = volumeKratky2nd.indexOf(volumeKratky1st.getMaxX()) + 1;
        //subtract tempArray[0] from intensity and integrate
        tempSeries1.clear();
        XYDataItem tempXY;


        XYSeries posOnly = dataset.getOriginalPositiveOnlyData();
        for (int m =0;m < index; m++){
            tempXY = posOnly.getDataItem(m);
            tempSeries1.add(tempXY.getXValue(), tempXY.getYValue() - tempArray[0]);
        }

        double izero = dataset.getGuinierIzero();
        double rg = dataset.getGuinierRg();

        XYSeries area = Functions.porodInvariant(tempSeries1, izero, 0.0, rg, 0.0);
        double porodInvariantOld = tempArray[1]/volumeKratky2nd.getX(index).doubleValue() + area.getY(area.getItemCount()-1).doubleValue();

        int volumeOld = (int)(Constants.TWO_PI_2*izero/porodInvariantOld);
        oldQ.setText(Constants.ThreeDecPlace.format(porodInvariantOld));
        oldV.setText(Integer.toString(volumeOld)); //Old method for determining correction to Q
        //Update jLabels and collection

        dataset.setPorodVolume(volumeOld);
        dataset.setInvariantQ(porodInvariantOld);

        if (dataset.getRealRg() > 0){
            dataset.setPorodVolumeReal((int)(Constants.TWO_PI_2*dataset.getRealIzero()/porodInvariantOld));
        }
        //porodVolumeList.get(fIndex).setText(scientific1dot2e1.format(volumeOld));
        //Methods.updatePorodInvariant(fIndex, porodInvariantOld, resultsVolumeGuinier.get(fIndex), resultsVolumeReal.get(fIndex), porodVolumeList.get(fIndex));
        //powerlaw line
        //Calculate residuals
        tempArray = Functions.leastSquares(powerLawX,powerLawY);
        powerLawSeries.clear();
        powerLawTopSeries.clear();
        volumePowerLawCollection.removeAllSeries();

        for (int i =0; i< powerLawX.length; i++){
            powerLawSeries.add(Math.exp(powerLawX[i]), (powerLawY[i] - (powerLawX[i]*tempArray[0] + tempArray[1])));
        }
        powerLawTopSeries.add(firstPowerLaw, (firstPowerLaw.doubleValue()*tempArray[0] + tempArray[1]));
        powerLawTopSeries.add(lastPowerLaw, (lastPowerLaw.doubleValue()*tempArray[0] + tempArray[1]));

        //  Methods.updatePx(collectionSelected.getDataset(fIndex), porodExponentLabelList.get(fIndex), resultsPx.get(fIndex), tempArray[0], tempArray[2]);
        volumePowerLawCollection.addSeries(powerLawSeries);

        //double stov = Math.PI*(Math.exp(tempArray[1])/porodInvariantOld);
        //status.setText("S-to-V estimate: " + fourDecPlac.format(stov));

        porodVolumeExponent.setText(Constants.OneDecPlace.format(-1.0*tempArray[0]));
        dataset.setPorodExponent(-1.0 * tempArray[0]);
        dataset.setPorodExponentError(tempArray[2]);

        // fire table data changed
        analModel.fireTableDataChanged();
    }

    private ChartFrame plotVolumePorod(XYSeriesCollection volumeset, double maxq){
        JFreeChart volumePorodChart;
        volumePorodChart = ChartFactory.createXYLineChart(
                "Porod-Debye Plot",                     // chart title
                "q^4",                    // domain axis label
                "q4 * I(q)",                // range axis label
                volumeset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        final XYPlot plot = volumePorodChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("");
        volumePorodChart.getTitle().setFont(new java.awt.Font("Tahoma", 1, 18));

        domainAxis.getAutoRangeIncludesZero();

        String quote = "q\u2074 (\u212B \u207B\u2074)";
        domainAxis.setLabel(quote);
        quote = "q\u2074 \u00D7 I(q)";
        rangeAxis.setLabel(quote);
        rangeAxis.setLabelFont(Constants.BOLD_16);

        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setRangeGridlinePaint(Color.black);
        plot.setDomainGridlinePaint(Color.black);

        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();
        plot.getRangeAxis(0).setTickLabelsVisible(false);

        // need to turn off zoom

        plot.setDataset(0, volumeset);
        plot.setRenderer(0, dotRend);

        //First Dataset
        dotRend.setSeriesShape(0, Constants.Ellipse4);
        dotRend.setSeriesLinesVisible(0, false);
        dotRend.setSeriesShapesFilled(0, false);
        dotRend.setSeriesShapesVisible(0, true);
        //Second DataSet
        dotRend.setSeriesShape(1,Constants.Ellipse4);
        dotRend.setSeriesLinesVisible(1, false);
        dotRend.setSeriesShapesFilled(1, false);
        dotRend.setSeriesShapesVisible(1, true);

        dotRend.setSeriesLinesVisible(2, true);
        dotRend.setSeriesStroke(2, new BasicStroke(2));
        dotRend.setSeriesShapesFilled(2, false);
        dotRend.setSeriesShapesVisible(2, false);

        dotRend.setSeriesPaint(2, Constants.DodgerBlue);
        dotRend.setSeriesPaint(0, Constants.DarkGray);
        dotRend.setSeriesPaint(1, Constants.MediumRed);

        ChartFrame plotVolumePorodFrame = new ChartFrame("Porod-Debye", volumePorodChart);
        plotVolumePorodFrame.getChartPanel().setChart(volumePorodChart);
        plotVolumePorodFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        plotVolumePorodFrame.pack();
        plotVolumePorodFrame.getChartPanel().setMouseZoomable(false);
        return plotVolumePorodFrame;
    }

    private ChartFrame plotVolumeInvariant(XYSeriesCollection volumeset, double maxq){
        JFreeChart volumeInvariantChart;
        volumeInvariantChart = ChartFactory.createXYLineChart(
                "Porod Exponent Power Law Fit",                     // chart title
                "q",                    // domain axis label
                "residual (ln[I(q)] - model)",                // range axis label
                //"Q(r)",                // range axis label
                volumeset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );
        volumeInvariantChart.getTitle().setFont(new java.awt.Font("Tahoma", 1, 18));
        volumeInvariantChart.getTitle().setPaint(Constants.SteelBlue);
        volumeInvariantChart.getTitle().setMargin(10, 0, 0, 0);
        final XYPlot plot = volumeInvariantChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("q");
        final NumberAxis rangeAxis = new NumberAxis("residuals");

        rangeAxis.setLabelFont(Constants.BOLD_16);

        //Need two renders, one for data and one for fit (spline)
        domainAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);
        String quote = "q (\u212B \u207B\u00B9)";
        domainAxis.setLabel(quote);

        //new Paint background = ;
        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setOutlineVisible(false);

        XYSplineRenderer splineRend = new XYSplineRenderer();
        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();

        plot.setDataset(0, volumeset);
        plot.setRenderer(0, dotRend);

        dotRend.setSeriesShape(0, Constants.Ellipse4);
        dotRend.setSeriesLinesVisible(0, false);
        dotRend.setSeriesShapesFilled(0, false);
        dotRend.setSeriesShapesVisible(0, true);

        dotRend.setSeriesShape(1, Constants.Ellipse4);
        dotRend.setSeriesLinesVisible(1, false);
        dotRend.setSeriesShapesFilled(1, false);
        dotRend.setSeriesShapesVisible(1, true);

        dotRend.setSeriesPaint(0, Constants.DarkGray);
        dotRend.setSeriesPaint(1, Constants.MediumRed);

        ChartFrame plotVolumeInvariantFrame = new ChartFrame("Invariant", volumeInvariantChart);
        plotVolumeInvariantFrame.getChartPanel().setChart(volumeInvariantChart);
        plotVolumeInvariantFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        plotVolumeInvariantFrame.pack();

        return plotVolumeInvariantFrame;
    }

    private ChartFrame plotVolumeKratky(XYSeriesCollection volumeset, double maxq){
        JFreeChart volumeKratkyChart;
        volumeKratkyChart = ChartFactory.createXYLineChart(
                "Power-law Plot",                     // chart title
                "ln[q]",                    // domain axis label
                "ln[I(q)]",                // range axis label
                volumeset,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        double upper = volumeset.getSeries(1).getMaxY() + volumeset.getSeries(1).getMaxY()*0.3;

        volumeKratkyChart.getTitle().setFont(new java.awt.Font("Tahoma", 1, 18));
        volumeKratkyChart.getTitle().setMargin(10, 0, 0, 0);

        final XYPlot plot = volumeKratkyChart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("");
        String quote = "ln[q (\u212B \u207B\u00B9)]";
        domainAxis.setLabel(quote);
        quote = "ln[I(q)]";
        rangeAxis.setLabel(quote);
        rangeAxis.setLabelFont(Constants.BOLD_16);
        rangeAxis.setUpperBound(upper);

        //domainAxis.setRange(0, 0.25);
        domainAxis.setAutoRange(true);
        domainAxis.setAutoRangeStickyZero(false);
        domainAxis.setAutoRangeIncludesZero(false);

        plot.setBackgroundPaint(null);
        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setDomainCrosshairVisible(true);
        plot.setRangeCrosshairVisible(true);
        plot.setOutlineVisible(false);


        XYSplineRenderer splineRend = new XYSplineRenderer();
        XYLineAndShapeRenderer dotRend = new XYLineAndShapeRenderer();

        plot.getRangeAxis(0).setTickLabelsVisible(false);
        plot.getRangeAxis(0).setAutoRange(true);
        plot.getDomainAxis(0).setAutoRange(true);

        // need to turn off zoom

        plot.setDataset(0, volumeset);
        plot.setRenderer(0, dotRend);

        dotRend.setSeriesShape(0, Constants.Ellipse4);
        dotRend.setSeriesLinesVisible(0, false);
        dotRend.setSeriesShapesFilled(0, false);
        dotRend.setSeriesShapesVisible(0, true);

        dotRend.setSeriesShape(1, Constants.Ellipse4);
        dotRend.setSeriesLinesVisible(1, false);
        dotRend.setSeriesShapesFilled(1, false);
        dotRend.setSeriesShapesVisible(1, true);

        dotRend.setSeriesLinesVisible(2, true);
        dotRend.setSeriesStroke(2, new BasicStroke(2));
        dotRend.setSeriesShapesFilled(2, false);
        dotRend.setSeriesShapesVisible(2, false);


        dotRend.setSeriesPaint(0, Constants.DarkGray);
        dotRend.setSeriesPaint(1, Constants.MediumRed);
        dotRend.setSeriesPaint(2, Constants.DodgerBlue);

        volumeKratkyChart.setBorderVisible(false);

        ChartFrame plotVolumeKratkyFrame = new ChartFrame("Power-law", volumeKratkyChart);
        plotVolumeKratkyFrame.getChartPanel().setChart(volumeKratkyChart);
        plotVolumeKratkyFrame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        plotVolumeKratkyFrame.pack();
        plotVolumeKratkyFrame.getChartPanel().setMouseZoomable(true);

        return plotVolumeKratkyFrame;
    }

    class ScatterSpinner extends JSpinner {
        public int priorIndex;
        public int collectionID;

        public ScatterSpinner(int current, int id){
            priorIndex = current;
            collectionID = id;
        }

        public void setPriorIndex(int value){
            priorIndex = value;
        }

        public int getID(){
            return collectionID;
        }

        public int getPriorIndex(){
            return priorIndex;
        }
    }
}
