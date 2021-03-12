package version4;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogarithmicAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class SVDCovariance extends SwingWorker<String, Object> {

    private JPanel contentPane;
    private JButton buttonOK;
    private JPanel SVDPlotPanel;
    private JLabel eightyPercentLabel;
    private JPanel labelsPanel;
    private JLabel ninetyPercentLabel;
    private JLabel qMinMaxLabel;
    private double qmin;
    private double qmax;
    private XYSeriesCollection datasets;
    private boolean column_wise = false;
    private int totalSets;
    private XYSeries singularValuesSeries;
    private XYSeriesCollection plotMe;
    private int eightypercent;
    private int ninetypercent;
    private double ninetypercentValue;
    private double eightypercentValue;


    public SVDCovariance(double qmin, double qmax, XYSeriesCollection datasets) {
        this.qmin = qmin;
        this.qmax = qmax;
        this.datasets = datasets;
        totalSets = datasets.getSeriesCount();
        column_wise = true;
        System.out.println("Total Sets SVDCovariance " + totalSets);
        //setContentPane(contentPane);
        //setModal(true);
        //getRootPane().setDefaultButton(buttonOK);

       // buttonOK.addActionListener(new ActionListener() {
       //     public void actionPerformed(ActionEvent e) {
       //         onOK();
       //     }
       // });
    }


    public SVDCovariance(double tempQmin, double tempQmax, XYSeriesCollection datasets, boolean findQminQmax) {

        this.datasets = datasets;

        if (findQminQmax){
            findLeastCommonQvalue(tempQmin);
            findMaximumCommonQvalue(tempQmax);
        }

        totalSets = datasets.getSeriesCount();
        column_wise = true;
        System.out.println("Total Sets SVDCovariance " + totalSets);
    }



    @Override
    protected String doInBackground() throws Exception {
        // create mean subtracted sets
        XYSeriesCollection meanCenteredSets = new XYSeriesCollection();
        XYDataItem tempItem;

        // need a collection of common q-values for column_wise
        if (column_wise){

            XYSeries tempSeries = datasets.getSeries(0);
            ArrayList<Number> qvalues = new ArrayList<>();
            int totalInSeries = tempSeries.getItemCount();
            for(int j=0; j<totalInSeries; j++){
                tempItem = tempSeries.getDataItem(j);
                if (tempItem.getXValue() >= qmin && tempItem.getXValue() <= qmax){
                    qvalues.add(tempItem.getX());
                }
            }
            // now check the other series that are remaining
            // for each q-value, check if in each dataset
            ArrayList<Number> qs_to_use = new ArrayList<>();
            for (Number num : qvalues) {
                int qcount=1;
                for (int i=1; i<totalSets; i++){
                    tempSeries = datasets.getSeries(i);
                    if (tempSeries.indexOf(num) > -1 ){
                        qcount++;
                    }
                }

                if (qcount == totalSets){
                    qs_to_use.add(num);
                }
            }

            ArrayList<Double> averages = new ArrayList<Double>();
            for (Number num : qs_to_use) {
                double qcount=0.0;
                double sum = 0.0;
                for (int i=1; i<totalSets; i++){
                    tempSeries = datasets.getSeries(i);
                    int index = tempSeries.indexOf(num);
                    if (index > -1 ){
                        sum += tempSeries.getY(index).doubleValue();
                        qcount+=1.0;
                    }
                }
                averages.add(sum/qcount);
            }

            // center the data
            for(int i=0; i<totalSets; i++){
                // center the data
                meanCenteredSets.addSeries(new XYSeries("mean centered " + Integer.toString(i)));
                XYSeries meanSeries = meanCenteredSets.getSeries(i);
                tempSeries = datasets.getSeries(i);

                for (int j=0; j< qs_to_use.size(); j++) {
                    int index = tempSeries.indexOf(qs_to_use.get(j));
                    if (index > -1 ){
                        tempItem = tempSeries.getDataItem(index);
                        meanSeries.add(tempItem.getXValue(), (tempItem.getYValue() - averages.get(j)));
                    }
                }
            }
        } else {
            for(int i=0; i<totalSets; i++){

                double sum=0;
                double count=0;

                XYSeries tempSeries = datasets.getSeries(i);
                int totalInSeries = tempSeries.getItemCount();

                for(int j=0; j<totalInSeries; j++){
                    tempItem = tempSeries.getDataItem(j);
                    if (tempItem.getXValue() >= qmin && tempItem.getXValue() <= qmax){
                        sum += tempItem.getYValue();
                        count += 1;
                    }
                }

                double invCount = 1.0/(double)count;
                double mean = sum*invCount;
                //System.out.println("Mean Centering " + i + " => "+ mean);
                // center the data
                meanCenteredSets.addSeries(new XYSeries("mean centered " + Integer.toString(i)));

                for(int j=0; j<totalInSeries; j++){
                    tempItem = tempSeries.getDataItem(j);
                    if (tempItem.getXValue() >= qmin && tempItem.getXValue() <= qmax){
                        meanCenteredSets.getSeries(i).add(tempItem.getXValue(), (tempItem.getYValue() - mean));
                    }
                }
            }
        }

        // create covariance matrix
        int rows = meanCenteredSets.getSeries(0).getItemCount();
        DMatrixRMaj matrixA = new DMatrixRMaj(rows, totalSets);
        //DenseMatrix64F matrixA = new DenseMatrix64F(rows, totalSets);
        //DenseMatrix64F matrixATranspose = new DenseMatrix64F(rows, totalSets);
        //DenseMatrix64F covariance = new DenseMatrix64F(rows, totalSets);

        int col=0;
        // fill first column and use q-values as standard for subsequent rows
        XYSeries refSeries = meanCenteredSets.getSeries(0);
        for(int j=0; j<rows; j++){
            tempItem = refSeries.getDataItem(j);
            matrixA.set(j, col, tempItem.getYValue()); // could try q*I(q) also
        }

        // add remaining sets but check their q-values are in reference
        col = 1;
        for(int s=1; s<totalSets; s++){

            XYSeries tempSeries = meanCenteredSets.getSeries(s);
            for(int j=0; j<rows; j++){
                int indexOf = tempSeries.indexOf(refSeries.getX(j));
                if (indexOf > -1){
                    matrixA.set(j, col, tempSeries.getDataItem(indexOf).getYValue());
                } else { // interpolate
                    Double[] results = Functions.interpolate(tempSeries, refSeries.getX(j).doubleValue());
                    matrixA.set(j, col, results[1]);
                    System.out.println("Not found! Interpolating q : " + refSeries.getX(j));
                }
            }
            col++;
        }

        //CommonOps.transpose(matrixA, matrixATranspose);
        //CommonOps.multInner(matrixA, covariance);
        SingularValueDecomposition_F64<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(rows, totalSets, true, true, false);

//        SingularValueDecomposition_F32<FMatrixRMaj> svd = DecompositionFactory_FDRM.svd(rows, trueCount, true, true, false);
//        SingularValueDecomposition<DenseMatrix64F> svd = DecompositionFactory.svd(rows, totalSets, true, true, false);
        // A = U*W*V_t
        try {
            svd.decompose(matrixA);
        } catch (Exception e){
            System.out.println("Matrix inversion exception in svdReduce ");
        }

       // DenseMatrix64F U = svd.getU(null,false);
        DMatrixRMaj W = svd.getW(null);
//        DenseMatrix64F W = svd.getW(null);
       // DenseMatrix64F V = svd.getV(null,false);
//        double firstss = W.get(0,0);


        System.out.println("/nSINGULAR VALUE => INDX    VALUE   RATIO");

        Double[] singularValues = new Double[totalSets];// = svd.getSingularValues(); // sould be sorted values
        for(int i=0; i < totalSets; i++){
            singularValues[i] = W.get(i,i);
//            System.out.println(" => " + i + " " + singularValues[i]);
        }

        Arrays.sort(singularValues, Collections.reverseOrder());

        double invSingularSquaredSum = 0;

        for (int i=0; i<singularValues.length; i++){
            double val = singularValues[i];
            invSingularSquaredSum += val*val;
        }

        invSingularSquaredSum = 1.0/invSingularSquaredSum;

        double tempSum=0;
        eightypercent=totalSets;
        ninetypercent=totalSets;
        boolean eightyFound=false;
        boolean ninetyFound=false;

        double svvalue;
        singularValuesSeries = new XYSeries("Singular Values");
//        int svd_index = 1;
        for(int i=0; i < (totalSets-1); i++){
            //svvalue = W.get(i,i);
            svvalue = singularValues[i];
            singularValuesSeries.add(i+1, svvalue);

            tempSum += svvalue*svvalue;

            if (!eightyFound && tempSum*invSingularSquaredSum > 0.8){
                eightyFound = true;
                eightypercentValue = tempSum*invSingularSquaredSum;
                System.out.println("80% => " + tempSum*invSingularSquaredSum);
                eightypercent=i;
            }

            if (!ninetyFound && tempSum*invSingularSquaredSum > 0.9){
                ninetyFound = true;
                ninetypercentValue = tempSum*invSingularSquaredSum;
                System.out.println("90% => " + tempSum*invSingularSquaredSum);
                ninetypercent=i;
            }
            //System.out.format("SINGULAR VALUE => %4d %8.4f  %8.6f\n", svd_index, Math.log10(W.get(i,i)), firstss/W.get(i,i));
//            svd_index++;
        }

        System.out.println("80% singular values contained within first " + eightypercent + " ( "+totalSets+" )");
        System.out.println("90% singular values contained within first " + ninetypercent + " ( "+totalSets+" )");

        // make a plot of singular values
        return null;
    }


    public void makePlot(){

        plotMe = new XYSeriesCollection();
        plotMe.addSeries(singularValuesSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "SVD Plot",            // chart title
                "index of singular value",                 // domain axis label
                "energy",                 // range axis label
                plotMe,
                PlotOrientation.VERTICAL,
                false,                    // include legend
                true,                     // toolTip
                false
        );

        final XYPlot plot = chart.getXYPlot();
    /*
      set domain and range axis
     */
        //final NumberAxis domainAxis = new NumberAxis("q, \u212B \u207B\u00B9");
        final NumberAxis domainAxis = new NumberAxis("");
        domainAxis.setAutoRange(true);
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        final NumberAxis rangeAxis = new NumberAxis("");
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRange(true);

        LogarithmicAxis yAxis = new LogarithmicAxis("intensity");
        yAxis.setAutoRange(true);

        plot.setRangeAxis(yAxis);
        plot.setDomainAxis(domainAxis);

        plot.setBackgroundAlpha(0.0f);
        plot.setOutlineVisible(false);

        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setBaseShapesVisible(true);
        renderer.setBaseLinesVisible(false);
        double pointsize = 12.0;
        renderer.setBaseShape(new Ellipse2D.Double(-0.5*pointsize, -0.5*pointsize, pointsize, pointsize));
        renderer.setSeriesShape(0, new Ellipse2D.Double(-0.5*pointsize, -0.5*pointsize, pointsize, pointsize));

        ChartPanel outPanel = new ChartPanel(chart);
        SVDPlotPanel.add(outPanel);

        if (eightypercent == ninetypercent){
            String tempnine = String.format("Greater than 90%% of the variance contained within %d singular value(s) : %.3f %% ", (ninetypercent+1), (ninetypercentValue*100));
            ninetyPercentLabel.setText(tempnine);
        } else {
            String tempeight = String.format("Greater than 80%% of the variance contained within %d singular value(s) : %.3f %% ", (eightypercent+1), (eightypercentValue*100));
            String tempnine = String.format("Greater than 90%% of the variance contained within %d singular value(s) : %.3f %% ", (ninetypercent+1), (ninetypercentValue*100));

            eightyPercentLabel.setText(tempeight);
            ninetyPercentLabel.setText(tempnine);
        }

        qMinMaxLabel.setText(String.format("RANGE : QMIN => %.4f QMAX => %.4f", qmin, qmax));

        JFrame frame = new JFrame("Plot");
        frame.setContentPane(this.contentPane);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    @Override
    protected void done() {
        try {
            super.get();
            System.out.println("done");
            //can call other gui update code here
        } catch (Throwable t) {
            //do something with the exception
        }
    }

    // private void onOK() {
// add your code here
   //     dispose();
   // }

//    public static void main(String[] args) {
//        SVDCovariance dialog = new SVDCovariance();
//        dialog.pack();
//        dialog.setVisible(true);
//        System.exit(0);
//    }

    /**
     *
     */
    private void findLeastCommonQvalue(double tempQmin){

        boolean isCommon;

        int totalInSampleSet = datasets.getSeriesCount();
        XYSeries referenceData = datasets.getSeries(0);

        int startHere=0;
        for(int i=0; i<referenceData.getItemCount(); i++){
            if (tempQmin >= referenceData.getX(i).doubleValue()){
                startHere=i;
                break;
            }
        }

        XYDataItem refItem;
        int startAt;
        Number minQvalueInCommon = 10;

        outerloop:
        for(int j=startHere; j < referenceData.getItemCount(); j++){

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            minQvalueInCommon = refItem.getX();
            isCommon = true;

            startAt = 1;
            innerloop:
            for(; startAt < totalInSampleSet; startAt++) {
                XYSeries tempData = datasets.getSeries(startAt);
                // check if refItem q-value is in tempData
                // if true, check next value
                if (tempData.indexOf(refItem.getX()) < 0) {
                    isCommon = false;
                    break innerloop;
                }
            }

            if (startAt == totalInSampleSet && isCommon){
                break outerloop;
            }
        }
        qmin = minQvalueInCommon.doubleValue();
    }


    /**
     *
     */
    private void findMaximumCommonQvalue(double tempQmax){

        boolean isCommon;
        int totalInSampleSet = datasets.getSeriesCount();
        XYSeries referenceData = datasets.getSeries(0);

        int startHere=referenceData.getItemCount()-1;
        for(int i=(referenceData.getItemCount()-1); i>0; i--){
            if (referenceData.getX(i).doubleValue() <= tempQmax){
                startHere=i;
                break;
            }
        }

        XYDataItem refItem;
        int startAt;
        Number maxQvalueInCommon = 0;

        outerloop:
        for(int j=startHere; j > -1; j--){

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            maxQvalueInCommon = refItem.getX();
            isCommon = true;

            startAt = 1;
            innerloop:
            for(; startAt < totalInSampleSet; startAt++) {
                XYSeries tempData = datasets.getSeries(startAt);
                // check if refItem q-value is in tempData
                // if true, check next value
                if (tempData.indexOf(refItem.getX()) < 0) {
                    isCommon = false;
                    break innerloop;
                }
            }

            if (startAt == totalInSampleSet && isCommon){
                break outerloop;
            }
        }

        qmax = maxQvalueInCommon.doubleValue();
    }


}
