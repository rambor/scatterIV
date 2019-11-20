package version4;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import version4.tableModels.DataFileElement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

/**
 * Created by robertrambo on 14/01/2016.
 */
public class ComplexPlot {

    private JFreeChart chart;
    private JFreeChart residualsChart;
    private JFreeChart combChart;
    private String workingDirectoryName;
    private XYSeriesCollection selectedDataCollection = new XYSeriesCollection();
    private XYSeriesCollection residualsCollection = new XYSeriesCollection();
    private final Collection collection;  // reference to collectionSelected from Scatter
    private Dataset complex;

    private CombinedDomainXYPlot combinedPlot;
    private ChartFrame frame;

    //private ChartPanel chartPanel = new ChartPanel(chart);
    //private ChartFrame residualsFrame = new ChartFrame("Residuals", residualsChart);

    private XYLineAndShapeRenderer renderer1;
    private XYLineAndShapeRenderer renderer2;

    private final JLabel status;

    public ComplexPlot(Collection collection, String dir, JLabel mainStatus) {
        this.collection = collection;
        this.workingDirectoryName = dir;
        this.status = mainStatus;
        // make pop-up JFrame
        JFrame complexFrame = new JFrame("Complex Formation ?");
// return
        int limit = collection.getDatasets().size();
        String[] names = new String[limit];

        for(int i=0; i < limit; i++){
            names[i] = collection.getDataset(i).getFileName();
        }

        JPanel contents = new JPanel();
        contents.setLayout(new BoxLayout(contents, BoxLayout.PAGE_AXIS));
        contents.setAlignmentX(Component.LEFT_ALIGNMENT);


        //JPanel header1 = new JPanel();
        JLabel headerText = new JLabel("Select SAXS data corresponding to the complex: ");
        Font font1 = headerText.getFont();
        Font boldFont1 = new Font(font1.getFontName(), Font.BOLD, 14);
        headerText.setFont(boldFont1);
        //header1.add(headerText);
        contents.add(headerText);

        final JComboBox complexData = new JComboBox(names);
        JPanel comboBoxPanel = new JPanel();
        complexData.setEditable(false);
        comboBoxPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboBoxPanel.add(complexData);

        contents.add(comboBoxPanel);

        JPanel header2 = new JPanel();
        JLabel header2Text = new JLabel("Select the datasets that represents the monomeric species");
        Font font = header2Text.getFont();
        Font boldFont = new Font(font.getFontName(), Font.BOLD, 14);
        header2Text.setFont(boldFont);
        header2Text.setAlignmentX(Component.LEFT_ALIGNMENT);
        contents.add(header2Text);

        JLabel infoText = new JLabel();
        infoText.setText("<html>Complex formation can be inferred by determining the best linear combination of the monomeric SAXS curves to explain the complex SAXS curve. A nearly perfect fit means no complex formation.</html>");
        Dimension dim = new Dimension(420,70);
        infoText.setMinimumSize(dim);
        infoText.setPreferredSize(dim);
        infoText.setMaximumSize(dim);
        //infoText.setBorder(BorderFactory.createLineBorder(Color.black));
        infoText.setAlignmentX(Component.LEFT_ALIGNMENT);
        contents.add(infoText);

        // create list of files with checkboxes
        final JList complexFilesList = new JList();
        complexFilesList.setCellRenderer(new DataFilesListRenderer());
        complexFilesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        DefaultListModel<DataFileElement> complexFilesModel = new DefaultListModel<DataFileElement>();

        // update dataFilesList in dataFilesPanel;
        // rebuild dataFilesPanel from collection.get(i)

        for(int i=0; i< collection.getDatasets().size(); i++){
            String name = collection.getDataset(i).getFileName();
            complexFilesModel.addElement(new DataFileElement(name, i));
        }

        complexFilesList.setModel(complexFilesModel);
        contents.add(new JScrollPane(complexFilesList));

        //Create button
        JButton calculate = new JButton("Calculate");

        calculate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // call function for calculating
                complexCalculate(complexFilesList, complexData);
            }
        });

        contents.add(calculate);

        // toggles selected Files in
        complexFilesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                super.mouseClicked(event);
                JList list = (JList) event.getSource();
                // Get index of clicked item
                int index = list.locationToIndex(event.getPoint());
                // Toggle selected state
                DataFileElement item = (DataFileElement) list.getModel().getElementAt(index);
                // Repaint cell
                item.setSelected(! item.isSelected());
                list.repaint(list.getCellBounds(index,index));
            }
        });

        complexFrame.getContentPane().add(contents);
        complexFrame.pack();
        complexFrame.setVisible(true);
    }

    private ChartPanel createPlot() {

        chart = ChartFactory.createXYLineChart(
                "SC\u212BTTER \u2263 Guinier fit",                // chart title
                "",                    // domain axis label
                "ln I(q)",                  // range axis label
                selectedDataCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );


        residualsChart = ChartFactory.createXYLineChart(
                "Residuals",                // chart title
                "",                    // domain axis label
                "residuals",                  // range axis label
                residualsCollection,               // data
                PlotOrientation.VERTICAL,
                true,                     // include legend
                true,
                false
        );

        final XYPlot residuals = residualsChart.getXYPlot();
        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("log [I(q)]");


        Font fnt = new Font("SansSerif", Font.BOLD, 15);
        domainAxis.setLabelFont(fnt);
        rangeAxis.setLabelFont(fnt);
        domainAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);
        //String quote = "q (\u212B \u207B)";
        String quote = "q (\u212B\u207B\u00B9)";
        domainAxis.setLabel(quote);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        residuals.setDomainAxis(domainAxis);
        plot.setBackgroundPaint(null);
        residuals.setBackgroundPaint(null);
        renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(false);

        renderer1.setSeriesShapesVisible(0, true);
        renderer1.setSeriesLinesVisible(0, false);
        renderer1.setSeriesShapesFilled(0, false);
        renderer1.setSeriesShape(0, new Ellipse2D.Double(0, 0, 4.0, 4.0));
        renderer1.setSeriesPaint(0, Color.RED);
        //renderer1.setSeriesStroke(0, stroke);

        // Series
        renderer1.setSeriesShapesVisible(1, true);
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesShapesFilled(1, false);
        renderer1.setSeriesPaint(1, complex.getColor());
        renderer1.setSeriesShape(1, new Ellipse2D.Double(0, 0, 8.0, 8.0));
        renderer1.setSeriesOutlineStroke(1, complex.getStroke());

        // renderer1.setBaseToolTipGenerator(new StandardXYToolTipGenerator());

        renderer2 = (XYLineAndShapeRenderer) residuals.getRenderer();
        renderer2.setBaseShapesVisible(true);
        renderer2.setBaseShapesFilled(false);

//        renderer2.setSeriesShapesVisible(1, true);
//        renderer2.setSeriesShapesVisible(1, false);
//        renderer2.setSeriesPaint(1, Color.red);
//        renderer2.setSeriesStroke(1, stroke);
        renderer2.setSeriesLinesVisible(0, false);
        renderer2.setSeriesPaint(0, Color.BLACK);
        renderer2.setSeriesShape(0, new Ellipse2D.Double(0, 0, 8.0, 8.0));

        plot.getAnnotations().size();

        //plot.setBackgroundAlpha(0.0f);

        combinedPlot = new CombinedDomainXYPlot(new NumberAxis("2"));
        combinedPlot.setDomainAxis(domainAxis);
        combinedPlot.setGap(10.0);
        combinedPlot.add(plot, 1);
        combinedPlot.add(residuals, 1);
        combinedPlot.setOrientation(PlotOrientation.VERTICAL);

        combChart = new JFreeChart("Complexation Determination Plot", JFreeChart.DEFAULT_TITLE_FONT, combinedPlot, true);

        combChart.removeLegend();
        combChart.setBackgroundPaint(Color.WHITE);

        frame = new ChartFrame("SC\u212BTTER \u2263 COMPLEXATION Plot", chart);
        frame.setBackground(Color.WHITE);

        frame.getChartPanel().setSize(600, 400);
        frame.getChartPanel().setChart(combChart);
        frame.getChartPanel().setDisplayToolTips(true);
        frame.getChartPanel().setDefaultDirectoryForSaveAs(new File(workingDirectoryName));
        frame.pack();
        return frame.getChartPanel();
    }

    
    private void complexCalculate(JList list, JComboBox box){

        int selected = box.getSelectedIndex();
        complex = collection.getDataset(selected);
        selectedDataCollection = new XYSeriesCollection();
        residualsCollection = new XYSeriesCollection();
        XYSeriesCollection sourceSeries = new XYSeriesCollection();
        XYSeriesCollection errorSeries = new XYSeriesCollection();
        ArrayList<String> filenames = new ArrayList<>();

        XYSeries originalComplex = new XYSeries(complex.getFileName());
        XYSeries originalError = new XYSeries(complex.getFileName());

        int startPt = complex.getStart()-1;
        int endPt = complex.getEnd();
        int rounds = 3000;
        XYDataItem tempXY;

        //int[] indices = list.getSelectedIndices();
        Hashtable<Double, double[]> numbers = new Hashtable<>();
        int total = list.getModel().getSize();

        for (int i=0; i<total; i++){
            DataFileElement m = (DataFileElement) list.getModel().getElementAt(i);
            // get selected Datasets
            if (m.isSelected()){
                if (m.getCollection_id() == selected){
                    status.setText("Can not select the same file twice (see drop down)");
                    return;
                } else {
                    // using all data we ignore negative intensities
                    sourceSeries.addSeries(collection.getDataset(m.getCollection_id()).getOriginalPositiveOnlyData());
                    errorSeries.addSeries(collection.getDataset(m.getCollection_id()).getOriginalPositiveOnlyError());
                    filenames.add(collection.getDataset(m.getCollection_id()).getFileName());
                }
            }
        }

        double xValue;
        int locale;
        for(int i=startPt; i< endPt; i++){
            tempXY = complex.getOriginalPositiveOnlyDataItem(i);
            xValue = tempXY.getXValue();
            originalComplex.add(tempXY);
            originalError.add(complex.getOriginalPositiveOnlyError().getDataItem(i));
            numbers.put(xValue, new double[sourceSeries.getSeriesCount()+1]);
            numbers.get(xValue)[0] = tempXY.getYValue();
            for(int j=0; j< sourceSeries.getSeriesCount(); j++){
                locale = sourceSeries.getSeries(j).indexOf(tempXY.getXValue());
                if (locale >= 0){
                    numbers.get(xValue)[j+1] = sourceSeries.getSeries(j).getY(locale).doubleValue();
                } else {
                    // interpolate value
                    // make sure reference q values is greater than first two or last two points in sourceSeries
                    XYSeries tempSeries = sourceSeries.getSeries(j);
                    XYSeries tempError = errorSeries.getSeries(j);
                    if ( (xValue > tempSeries.getX(1).doubleValue()) || (xValue < tempSeries.getX(tempSeries.getItemCount()-2).doubleValue()) ){
                        Double[] results =  Functions.interpolateOriginal(tempSeries, tempError, xValue);
                        numbers.get(xValue)[j+1] = results[1];
                    }
                }
            }
        }

        //int rows = originalComplex.getItemCount();

        double percent;
        boolean broken;
        int totalPoints = originalComplex.getItemCount();
        int random_length, subsample;
        int totalSource = sourceSeries.getSeriesCount();
        ArrayList<double[]> designMatrix = new ArrayList<>();
        double[] keptparams, row;
        double obs, partial, test_residual, current_residual;
        double[] residuals = new double[totalPoints];

        current_residual = 10000;
        keptparams = new double[totalSource+1];

        for(int i=0; i < rounds; i++){

            //build randomly selected subset from originalComplex
            percent = (1.0 + (Math.random()*6.0))/10.0; // randomly select number between 10 and 70%
            subsample = (int)(totalPoints*percent);
            int[] random_indices = Functions.rand_n(subsample, totalPoints);
            broken = false;
            random_length = random_indices.length;

            double[] y_random = new double[random_length];


            XYSeries randomSeries = new XYSeries("Random");
            for (int j=0; j<random_length; j++){
                tempXY = originalComplex.getDataItem(random_indices[j]);

                randomSeries.add(tempXY);  // keeps the values sorted by x_value
            }

            designMatrix.clear();


            // go through each
            designMatrixLoop:
            for (int j=0; j<random_length; j++){

                designMatrix.add(new double[totalSource]);
                tempXY = randomSeries.getDataItem(j);
                xValue = tempXY.getXValue();
                y_random[j] = tempXY.getYValue();

                for (int k=1; k<=totalSource; k++){
                    //grab y-value from

                    designMatrix.get(j)[k-1] = numbers.get(xValue)[k];

                    if (numbers.get(xValue)[k] == 0.0){
                        status.setText("Resampling : insufficient q-value at round " + i);
                        broken = true;
                        break designMatrixLoop;
                    }
                }
            }

            double[] params;

            // if not broken, proceed with fit
            if (!broken) {
                params = Functions.complexation(y_random, designMatrix);
                int totalCoeffs = params.length;

                for (int j=0; j < totalPoints; j++){
                    xValue = originalComplex.getX(j).doubleValue();
                    row = numbers.get(xValue);
                    obs = row[0];
                    partial = 0.0;
                    for (int k=0; k < totalCoeffs-1; k++){
                        partial += params[k]*row[k+1];
                    }
                    partial += params[totalCoeffs-1]; //constant offset
                    residuals[j] = Math.abs(obs - partial);
                }

                Arrays.sort(residuals);
                test_residual = Functions.median(residuals);
                // calculate residuals
                if (test_residual < current_residual){
                    current_residual = test_residual;
                    keptparams = params;
                }

            }

        } //end of rounds

        //Build XYSeries for plotting
        XYSeries calculated = new XYSeries("Calculated");
        XYSeries observed = new XYSeries("Observed");
        XYSeries ratio = new XYSeries("Ratio");
        double logMe, ratioMe;
        for (int j=0; j < totalPoints; j++){
            xValue = originalComplex.getX(j).doubleValue();
            row = numbers.get(xValue);
            obs = row[0];
            partial = 0.0;
            broken = false;

            for (int k=0; k < totalSource; k++){
                partial += keptparams[k]*row[k+1];
                if (row[k+1] == 0.0){
                    broken = true;
                    break;
                }
            }

            if (!broken){
                partial += keptparams[totalSource]; //constant offset
                logMe = Math.log10(partial);
                calculated.add(xValue, logMe);

                observed.add(xValue, Math.log10(obs));

                ratioMe = obs/partial;
                ratio.add(xValue, ratioMe);
            }
        }

        selectedDataCollection.addSeries(calculated);
        selectedDataCollection.addSeries(observed);
        residualsCollection.addSeries(ratio);
        // calculate Kurtosis



        //create the chart
        JFrame complexFrame = new JFrame("Complexation");
        JPanel textPanel = new JPanel();
        JLabel textLabel =new JLabel("");

        //ComplexPlot complexationPlot = new ComplexPlot(complex.getFileName());
        ChartPanel complexationPlot = this.createPlot();

        complexFrame.getContentPane().add(complexationPlot, BorderLayout.CENTER );
        //chartPanel.add(complexationPlot.frame.getChartPanel());
        //complexFrame.getContentPane().add(chartPanel);

        String paramline = "<html><p>Optimized scaling coefficients for fitting the input complex curve</p><table>";
        for (int i =0; i< keptparams.length; i++){
            if (i < sourceSeries.getSeriesCount()){
                paramline += "<tr><td>" + filenames.get(i) + " </td<td> " + Constants.Scientific2.format(keptparams[i]) + "</td<td>";
            } else {
                paramline += "<tr><td>Offset </td<td> " + Constants.Scientific2.format(keptparams[i]) + "</td<td>";
            }
        }
        paramline += "</table></html>";

        textLabel.setText(paramline);
        textLabel.setBackground(Color.WHITE);
        complexFrame.getContentPane().setBackground(Color.WHITE);

        textPanel.add(textLabel);
        complexFrame.getContentPane().add(textLabel, BorderLayout.PAGE_END);

        complexFrame.pack();
        complexFrame.setVisible(true);
    }    

}