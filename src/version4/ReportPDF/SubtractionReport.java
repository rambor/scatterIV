package version4.ReportPDF;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import rst.pdfbox.layout.elements.Document;
import rst.pdfbox.layout.elements.ImageElement;
import rst.pdfbox.layout.elements.Paragraph;
import rst.pdfbox.layout.elements.VerticalSpacer;
import rst.pdfbox.layout.elements.render.RenderContext;
import rst.pdfbox.layout.elements.render.RenderListener;
import rst.pdfbox.layout.elements.render.VerticalLayoutHint;
import rst.pdfbox.layout.text.*;
import version4.*;
import version4.BinaryComparison.ResidualDifferences;
import version4.Collection;
import version4.Constants;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.logging.Level;

public class SubtractionReport {

    private Document document;
    private Collection collectionInuse;
    private final float imageWidth = 270;
    private final float imageHeight = 240;

    private String signalLegend, log10OfAllText, durbinWatsonCompareText, rgIzeroText, mergedText;
    private String workingDirectory;
    private BufferedImage iofqPlot;
    private BufferedImage qIqPlot;
    private BufferedImage differencePlot;
    private BufferedImage rgIzeroDoublePlot;
    private XYSeries average;
    private XYSeries median;

    private ArrayList<String> rgIzeroRows;

    public SubtractionReport(Collection coll, String workingDirectory){
        collectionInuse = coll;
        this.workingDirectory = workingDirectory;

        // set colors
        float inv = 1.0f/255.0f;
        int totalIn = coll.getTotalDatasets();

        for(int i=0; i<totalIn; i++){
            // end 15 108 255 (dark blue)
            // start 242 214 245
            Dataset tempD = collectionInuse.getDataset(i);
            int r = interpolate(104, 255, i, totalIn);
            int g = interpolate(166, 93, i, totalIn);
            int b = interpolate(255, 135, i, totalIn);
            tempD.setColor(new Color(r*inv,g*inv,b*inv, 0.25f));
        }

        System.out.println("total in colledction " + collectionInuse.getTotalDatasets());
        median = medianDatasets(collectionInuse).get(0);
        average = weightedAverageDatasets(collectionInuse).get(0);
    }

    private int interpolate(int startValue, int endValue, int stepNumber, int lastStepNumber) {
        return (endValue - startValue) * stepNumber / lastStepNumber + startValue;
    }

    // make subplot of selected region that is selected
    // contain the log10 plot of all subtracted signals
    public void setSubtractedPlot(){
        String title = "A";
        log10OfAllText = "*A.* Overlay of SAXS curves of subtracted frames. Each frame is colored based on the following table.";
        this.makeLog10Plot(title);
        this.makeqIqPlot();
        log10OfAllText += " *B.* Total scattered intensity plot. Horizontal line marks zero, points below the line indicate negative intensities. Average (black) and median (red) of the individual subtracted datasets. " +
                "Separation between average and median indicates possible issues with buffer or radiation damage.";
        this.setComparisonPlot(5, true);
    }


    private void makeqIqPlot(){

        int totalSets = collectionInuse.getTotalDatasets();
        XYSeriesCollection plottedDatasets = new XYSeriesCollection();

        int totalInMedian = median.getItemCount();
        plottedDatasets.addSeries(new XYSeries("median qIq"));
        plottedDatasets.addSeries(new XYSeries("ave qIq"));
        XYSeries medSeries = plottedDatasets.getSeries(0);
        XYSeries aveSeries = plottedDatasets.getSeries(1);

        for(int i=0; i<totalInMedian; i++){
            XYDataItem item = median.getDataItem(i);
            medSeries.add(item.getXValue(), item.getYValue()*item.getXValue());
        }

        totalInMedian = average.getItemCount();
        for(int i=0; i<totalInMedian; i++){
            XYDataItem item = average.getDataItem(i);
            aveSeries.add(item.getXValue(), item.getYValue()*item.getXValue());
        }


        double qlower=10, qupper=-1, ilower = 10, iupper = -1;
        int addedSetCount = 2;

        for (int i=0; i < totalSets; i++){

            Dataset tempData = collectionInuse.getDataset(i);

            plottedDatasets.addSeries(new XYSeries(tempData.getFileName()));
            XYSeries tempSeries = plottedDatasets.getSeries(addedSetCount);

            XYSeries tempXY = tempData.getAllData();
            for(int ii=0; ii<tempData.getAllData().getItemCount(); ii++){
                XYDataItem item = tempXY.getDataItem(ii);
                tempSeries.add(item.getXValue(), item.getXValue()*item.getYValue());
            }

            if (tempData.getAllData().getMinX() < qlower){
                qlower = tempData.getAllData().getMinX();
            }

            if (tempData.getAllData().getMaxX() > qupper){
                qupper = tempData.getAllData().getMaxX();
            }

            if (tempSeries.getMinY() < ilower){
                ilower = tempSeries.getMinY();
            }

            if (tempSeries.getMaxY() > iupper){
                iupper = tempSeries.getMaxY();
            }

            addedSetCount++;
        }


        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "q",                             // domain axis label
                "log[I(q)]",                     // range axis label
                plottedDatasets,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );


        ValueMarker lineAtZero = new ValueMarker(0, Color.BLACK, new BasicStroke(1.0f));
        chart.getXYPlot().setDomainCrosshairVisible(false);
        chart.getXYPlot().setRangeCrosshairVisible(false);

        chart.setBorderVisible(false);
        chart.setTitle("B");
        chart.getTitle().setFont(new java.awt.Font("Times",  Font.BOLD, 42));
        chart.getTitle().setPaint(Color.BLACK);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);


        plot.setRangeAxis(getRangeAxis("q\u00D7 I(q)",ilower, iupper));
        plot.setDomainAxis(getDomainAxis("q (\u212B\u207B\u00B9)"));
        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);
        plot.addRangeMarker(lineAtZero);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);

        //set the median and average first
        double pointSize = collectionInuse.getDataset(0).getPointSize();
        double offset = -0.5*pointSize;
        BasicStroke bstroked = collectionInuse.getDataset(0).getStroke();

        renderer1.setSeriesShape(0, new Ellipse2D.Double(offset, offset, pointSize, pointSize));
        renderer1.setSeriesLinesVisible(0, false);
        renderer1.setSeriesPaint(0, Color.red);
        renderer1.setSeriesShapesFilled(0, true);
        renderer1.setSeriesVisible(0, true);
        renderer1.setSeriesOutlineStroke(0, bstroked);

        renderer1.setSeriesShape(1, new Ellipse2D.Double(offset, offset, pointSize, pointSize));
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesPaint(1, Color.black);
        renderer1.setSeriesShapesFilled(1, true);
        renderer1.setSeriesVisible(1, true);
        renderer1.setSeriesOutlineStroke(1, bstroked);

        for (int i=0; i < totalSets; i++){
            Dataset tempData = collectionInuse.getDataset(i);
            offset = -0.5*tempData.getPointSize();
            int nextIndex = i+2;
            renderer1.setSeriesShape(nextIndex, new Ellipse2D.Double(offset, offset, pointSize, pointSize));
            renderer1.setSeriesLinesVisible(nextIndex, false);
            renderer1.setSeriesPaint(nextIndex, tempData.getColor());
            renderer1.setSeriesShapesFilled(nextIndex, tempData.getBaseShapeFilled());
            renderer1.setSeriesVisible(nextIndex, tempData.getInUse());
            renderer1.setSeriesOutlineStroke(nextIndex, bstroked);
        }

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);

        qIqPlot = chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }


    private void makeLog10Plot(String title){

        int totalSets = collectionInuse.getTotalDatasets();

        XYSeriesCollection plottedDatasets = new XYSeriesCollection();

        double qlower=10, qupper=-1, ilower = 10, iupper = -1;
        int addedSetCount = 0;
        for (int i=0; i < totalSets; i++){

            Dataset tempData = collectionInuse.getDataset(i);
            plottedDatasets.addSeries(new XYSeries(tempData.getFileName()));

            XYSeries tempSeries = plottedDatasets.getSeries(addedSetCount);

            XYSeries tempXY = tempData.getAllData();
            for(int ii=0; ii<tempData.getAllData().getItemCount(); ii++){
                XYDataItem item = tempXY.getDataItem(ii);
                if (item.getY().doubleValue() > 0){
                    tempSeries.add(item.getXValue(), Math.log10(item.getYValue()));
                }
            }

            if (tempData.getAllData().getMinX() < qlower){
                qlower = tempData.getAllData().getMinX();
            }

            if (tempData.getAllData().getMaxX() > qupper){
                qupper = tempData.getAllData().getMaxX();
            }

            if (tempSeries.getMinY() < ilower){
                ilower = tempSeries.getMinY();
            }

            if (tempSeries.getMaxY() > iupper){
                iupper = tempSeries.getMaxY();
            }
            addedSetCount++;
        }


        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "q",                             // domain axis label
                "log[I(q)]",                     // range axis label
                plottedDatasets,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        chart.getXYPlot().setDomainCrosshairVisible(false);
        chart.getXYPlot().setRangeCrosshairVisible(false);

        chart.setBorderVisible(false);
        chart.setTitle(title);
        chart.getTitle().setFont(new java.awt.Font("Times",  Font.BOLD, 42));
        chart.getTitle().setPaint(Color.BLACK);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);

        plot.setRangeAxis(getRangeAxis("log10[I(q)]",ilower, iupper));
        plot.setDomainAxis(getDomainAxis("q (\u212B\u207B\u00B9)"));
        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);

        //set dot size for all series
        double offset;
        int counted = 0;

        for (int i=0; i < totalSets; i++){

            if (collectionInuse.getDataset(i).getInUse()){
                Dataset tempData = collectionInuse.getDataset(i);

                offset = -0.5*tempData.getPointSize();
                renderer1.setSeriesShape(counted, new Ellipse2D.Double(offset, offset, tempData.getPointSize(), tempData.getPointSize()));
                renderer1.setSeriesLinesVisible(counted, false);
                renderer1.setSeriesPaint(counted, tempData.getColor());
                renderer1.setSeriesShapesFilled(counted, tempData.getBaseShapeFilled());
                renderer1.setSeriesVisible(counted, tempData.getInUse());
                renderer1.setSeriesOutlineStroke(counted, tempData.getStroke());
                counted++;
            }
        }

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);

        iofqPlot = chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }



    private void setComparisonPlot(int pointsToExclude, boolean doGuinier) {

        int totalInCollection = collectionInuse.getTotalDatasets();

        Dataset referenceDataset = collectionInuse.getDataset(0);
        Number qmin = referenceDataset.getAllData().getX(pointsToExclude);

        XYSeries differenceSeriesA = new XYSeries("DifferenceSeriesA");
        XYSeries differenceSeriesB = new XYSeries("DifferenceSeriesB");

        // make ratio plot using same reference dataset
        for(int i=1; i<totalInCollection; i++){

            Dataset targetDataset = collectionInuse.getDataset(i);
            if (targetDataset.getInUse()){
                ResidualDifferences differences = new ResidualDifferences(referenceDataset.getAllData(),
                        targetDataset.getAllData(),
                        targetDataset.getAllDataError(),
                        qmin,
                        0.27,
                        12, referenceDataset.getId(), targetDataset.getId(), 0);

                differenceSeriesA.add(targetDataset.getId(), differences.getDurbinWatsonStatistic());
                differenceSeriesB.add(targetDataset.getId(), differences.getShapiroWilkStatistic());
            }
        }

        String difftitle = "C";
        durbinWatsonCompareText = "*C.* Durbin-Watson and Shapiro-Wilks tests examining the distribution of the residuals between two frames. In this case, comparisons are made in reference to the first frame. Radiation damage or lack of similarity can be observed as a trend in either statistic across the frame set. Likewise, similarity is demonstrated by a random distribution of the statistics.";

        differencePlot = makeDoublePlot(differenceSeriesA, differenceSeriesB, "Durbin-Watson", "Shapiro-Wilks", difftitle).createBufferedImage((int)imageWidth*3,(int)imageHeight*3);

        // make Rg-Izero plot
        if (doGuinier){
            rgIzeroRows = new ArrayList<>();
            rgIzeroRows.add(getRgDataSummaryTopHeader());

            XYSeries guinierRgSeries = new XYSeries("Rg");
            XYSeries guinierIzeroSeries = new XYSeries("I(0)");

            for(int i=0; i<totalInCollection; i++){

                Dataset targetDataset = collectionInuse.getDataset(i);
                if (targetDataset.getInUse()){

                    AutoRg tempRg = new AutoRg(targetDataset.getAllData(), pointsToExclude);
                    // only add non-Zero values if autoRg is successful
                    if (tempRg.getRg() > 0){
                        guinierIzeroSeries.add(targetDataset.getId(),tempRg.getI_zero());
                        guinierRgSeries.add(targetDataset.getId(),tempRg.getRg());
                    }

                    String reciIzero = String.format("%1.2E +- %1.1E",tempRg.getI_zero(), tempRg.getI_zero_error());
                    String reciRg = String.format("%.2f +- %.2f",tempRg.getRg(), tempRg.getRg_error());
                    String hex = String.format("#%02x%02x%02x", targetDataset.getColor().getRed(), targetDataset.getColor().getGreen(), targetDataset.getColor().getBlue());
                    // make table of values I(0) Rg filenames
                    int lengthOfFilename = targetDataset.getFileName().length();
                    String escaped = escape(targetDataset.getFileName());
                    if (lengthOfFilename > 20){
                        escaped = escape(targetDataset.getFileName().substring(0,20));
                    }
                    rgIzeroRows.add(String.format("{color:%s} %s %s %s %s", hex, Report.rightJustifyText(Integer.toString(targetDataset.getId()), 6, ' '), Report.centerText(reciIzero,20, ' '), Report.centerText(reciRg,20, ' '), escaped)); // qmin
                }
            }

            difftitle = "D";
            rgIzeroText = "*D.* Double Y plot with I(0), orange, and R{_}g{_}, cyan, estimated from the Guinier region for each subtracted frame. For a single concentration measurement made over several frames, radiation damage will be observed as an increase in I(0) and R{_}g{_}.";

            rgIzeroDoublePlot = makeDoublePlot(guinierIzeroSeries, guinierRgSeries, "I(0)", "Rg Å", difftitle).createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
        }
    }

    private NumberAxis getRangeAxis(String quote, double ilower, double iupper){
        final NumberAxis rangeAxis = new NumberAxis(quote);
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRange(false);
        rangeAxis.setLabelFont(Constants.FONT_BOLD_20);
        rangeAxis.setTickLabelFont(Constants.FONT_BOLD_18);
        rangeAxis.setTickLabelsVisible(false);
        rangeAxis.setRange(ilower-ilower*0.03, iupper+0.1*iupper);
        rangeAxis.setAutoRangeStickyZero(false);
        rangeAxis.setAxisLineStroke(new BasicStroke(3.0f));
        rangeAxis.setTickLabelPaint(Color.BLACK);

        return rangeAxis;
    }


    private NumberAxis getDomainAxis(String quote){
        final NumberAxis domainAxis = new NumberAxis(quote);
        domainAxis.setLabelFont(Constants.FONT_BOLD_20);
        domainAxis.setTickLabelFont(Constants.FONT_BOLD_20);
        domainAxis.setLabel(quote);
        domainAxis.setAutoRangeStickyZero(true);
        domainAxis.setAxisLineStroke(new BasicStroke(3.0f));
        domainAxis.setTickMarkStroke(new BasicStroke(3.0f));

        return domainAxis;
    }

    /**
     *
     * @param titleText title of the PDF and filename
     */
    public void writeReport(String titleText) {

        document = new Document(30, 30, 40, 60);
        document.addRenderListener(new RenderListener() {

            @Override
            public void beforePage(RenderContext renderContext) {
            }

            @Override
            public void afterPage(RenderContext renderContext) throws IOException {
                String content = String.format("%s", renderContext.getPageIndex() + 1);
                TextFlow text = TextFlowUtil.createTextFlow(content, 11, PDType1Font.TIMES_ROMAN);

                float offset = renderContext.getPageFormat().getMarginLeft() + TextSequenceUtil.getOffset(text, renderContext.getWidth(), Alignment.Right);
                text.drawText(renderContext.getContentStream(), new Position(offset, 30), Alignment.Right, null);
            }
        });


        ImageElement leftUpperImage = new ImageElement(iofqPlot);
        leftUpperImage.setWidth(imageWidth);
        leftUpperImage.setHeight(imageHeight);
        document.add(leftUpperImage, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));
        iofqPlot.flush();

        ImageElement rightUpperImage = new ImageElement(qIqPlot);
        rightUpperImage.setWidth(imageWidth);
        rightUpperImage.setHeight(imageHeight);
        document.add(rightUpperImage, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));
        qIqPlot.flush();

        document.add(new VerticalSpacer(240)); // add Rg Izero Double plot

        ImageElement leftLowerImage = new ImageElement(differencePlot);
        leftLowerImage.setWidth(imageWidth);
        leftLowerImage.setHeight(imageHeight);
        document.add(leftLowerImage, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));
        differencePlot.flush();

        ImageElement rightLowerImage = new ImageElement(rgIzeroDoublePlot);
        rightLowerImage.setWidth(imageWidth);
        rightLowerImage.setHeight(imageHeight);
        document.add(rightLowerImage, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));
        rgIzeroDoublePlot.flush();

        document.add(new VerticalSpacer(240)); // add Rg Izero Double plot

        try {

            String legend = log10OfAllText + " " + durbinWatsonCompareText + " " + rgIzeroText;

            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup("*Figure | " + titleText + ".* " + legend, 9, BaseFont.Times);
            tempparagraph.setMaxWidth((float)(document.getPageWidth()/1.2));
            tempparagraph.setAlignment(Alignment.Justify);
            document.add(tempparagraph, VerticalLayoutHint.CENTER);

        } catch (IOException e) {
            e.printStackTrace();
        }

        final OutputStream outputStream;

        try {
            String filename = Functions.sanitizeForFilename(titleText);
            outputStream = new FileOutputStream(workingDirectory+"/"+filename+".pdf");
            document.save(outputStream);
            document = null;
            outputStream.close();
            System.gc();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String escape(String text){
        String temp = text.replaceAll("/", " per ");
        //temp = temp.replaceAll(" ", "_");
        return temp.replace("_", "\\_");
    }

    /*
     * Header for
     */
    private String getRgDataSummaryTopHeader(){
        return String.format("%s %s %s", Report.rightJustifyText(" ",6,' '), Report.centerText("Guinier Izero",20, ' '), Report.centerText("Guinier Rg",20, ' '));
    }


    private JFreeChart makeDoublePlot(XYSeries leftSeries, XYSeries rightSeries, String leftTitle, String rightTitle, String title) {
        //
        // previously miniCollection was being plotted
        // this is really the active datasets,
        // in the constructor for collection, miniCollection is derived from dataset (same pointer)
        //
        XYSeriesCollection leftCollection = new XYSeriesCollection();
        XYSeriesCollection rightCollection = new XYSeriesCollection();

        double upperLeft = leftSeries.getMaxY();
        double lowerLeft = leftSeries.getMinY();
        double upperRight = rightSeries.getMaxY();
        double lowerRight = rightSeries.getMinY();

        leftCollection.addSeries(leftSeries);
        rightCollection.addSeries(rightSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                // chart title
                "",                        // domain axis label
                "",                // range axis label
                leftCollection,           // data
                PlotOrientation.VERTICAL,
                false,                      // include legend
                true,
                false
        );

        chart.setTitle(title);

        chart.getTitle().setFont(new java.awt.Font("Times", 1, 42));
        chart.getTitle().setPaint(Color.black);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setMargin(10, 10, 4, 0);
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();

        plot.setDomainAxis(0, getDomainAxis("Sample Number"));
        plot.setRangeAxis(0, getRangeAxis(leftTitle, lowerLeft, upperLeft));
        plot.setRangeAxis(1, getRangeAxis(rightTitle, lowerRight, upperRight));

        plot.getRangeAxis(0).setLabelPaint(new Color(255, 153, 51));
        plot.getRangeAxis(1).setLabelPaint(new Color(51, 153, 255));
        plot.getRangeAxis(0).setTickLabelsVisible(true);
        plot.getRangeAxis(1).setTickLabelsVisible(true);

        // integer only ticks
        plot.getDomainAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        plot.getDomainAxis().setAutoRange(true);

        plot.getDomainAxis().setRange(leftSeries.getMinX(), leftSeries.getMaxX());

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setBackgroundAlpha(0.0f);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer leftRenderer = (XYLineAndShapeRenderer) plot.getRenderer(0);
        XYLineAndShapeRenderer rightRenderer = new XYLineAndShapeRenderer();

        plot.setDataset(0, leftCollection);
        plot.setRenderer(0,leftRenderer);

        plot.setDataset(1,rightCollection);
        plot.setRenderer(1, rightRenderer);       //render as a line

        plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis
        plot.mapDatasetToRangeAxis(1, 1); //2nd dataset to 2nd y-axis

        leftRenderer.setBaseShapesVisible(true);
        leftRenderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
        leftRenderer.setSeriesLinesVisible(0, false);
        leftRenderer.setSeriesPaint(0, new Color(255, 153, 51));
        leftRenderer.setSeriesVisible(0, true);
        leftRenderer.setSeriesOutlineStroke(0, new BasicStroke(2));
        leftRenderer.setSeriesOutlinePaint(0, Color.BLACK);

        rightRenderer.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8, 8));
        rightRenderer.setSeriesLinesVisible(0, false);
        rightRenderer.setSeriesPaint(0, new Color(51, 153, 255));
        rightRenderer.setSeriesShapesFilled(0, true);
        rightRenderer.setSeriesVisible(0, true);
        rightRenderer.setSeriesOutlineStroke(0, new BasicStroke(2));

        plot.setDomainZeroBaselineVisible(false);

        return chart;
    }


    private ArrayList<XYSeries> medianDatasets(Collection thisCollection){
        // Median Set
        // Determine all open files
        // Need the master list of all unique q values
        // create set of unique q values from the collection using TreeSet
        TreeSet<Double> qList;
        qList = new TreeSet<Double>();

        HashMap<Double, ArrayList<Double>> intValuesHash = new HashMap<Double, ArrayList<Double>>();
        HashMap<Double, ArrayList<Double>> errValuesHash = new HashMap<Double, ArrayList<Double>>();

        int lowerQI;
        int upperQI;

        double qValue;
        double scaleF;

        Dataset tempDataSet;

        int limit = thisCollection.getDatasets().size();
        for (int i=0; i < limit; i++){
            // only checked boxes
            if (thisCollection.getDataset(i).getInUse()){
//                tempDataSet = new Dataset(thisCollection.getDataset(i));
                tempDataSet = thisCollection.getDataset(i);

                try {
                    // XYSeries series7 = (XYSeries) tempDataSet.getData().clone(); // plotted datset
                    XYSeries series8 = (XYSeries) tempDataSet.getAllData().clone();
                    XYSeries series9 = (XYSeries) tempDataSet.getAllDataError().clone();
                    //add q-values to qList
                    lowerQI = series8.indexOf(tempDataSet.getMinq());
                    upperQI = series8.indexOf(tempDataSet.getMaxq());
                    scaleF = tempDataSet.getScaleFactor();
                    System.out.println(i + " lowerQI " + lowerQI + " " + upperQI +  " " + limit + " tempDataSet.getMinq() " + tempDataSet.getMinq());

                    for (int j = lowerQI; j <= upperQI; j++){
                        // make entry in qList if not present
                        // if present
                        qValue = series8.getX(j).doubleValue();
                        if (qList.add(qValue)) {
                            //if true, add entry to ArrayList of Intensity Values that will be used for median
                            intValuesHash.put(qValue, new ArrayList<Double>( Arrays.asList(series8.getY(j).doubleValue() * scaleF) ));
                            errValuesHash.put(qValue, new ArrayList<Double>( Arrays.asList(series9.getY(j).doubleValue() * scaleF) ));
                        } else {
                            // if already present, find the index
                            intValuesHash.get(qValue).add(series8.getY(j).doubleValue()*scaleF);
                            errValuesHash.get(qValue).add(series9.getY(j).doubleValue()*scaleF);
                        }
                    }

                } catch (CloneNotSupportedException ex) {
                    LogIt.log(Level.SEVERE, "From StatMethods: MedianDatasets");
                }
            }
        }


        int qListSize;

        double median_value;
        double err_value;
        double err_valueU;
        double err_valueL;
        double lowerq;
        double upperq;
        int middlePoint;

        ArrayList<Double> tempArrayList;
        ArrayList<Double> sortList;

        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        returnMe.add(new XYSeries("median_set_from_collection"));
        returnMe.add(new XYSeries("median_error_set_from_collection"));
        XYSeries medianSet = returnMe.get(0);//new XYSeries("median_set_from_collection");
        XYSeries medianErrorSet = returnMe.get(1);//new XYSeries("median_error_set_from_collection");

        for (Double qvalue_key: intValuesHash.keySet()){

            tempArrayList = intValuesHash.get(qvalue_key);
            qListSize = tempArrayList.size();

            if (qListSize == 1 ){  //
                median_value = tempArrayList.get(0);
                err_value = errValuesHash.get(qvalue_key).get(0);

                //   qieSeries.add(new DataLine(tempQ, median_value, err_value, true));
            } else if (qListSize % 2 != 0) {  // odd
                middlePoint = (qListSize -1)/2;

                sortList = new ArrayList(tempArrayList); //makes a copy
                Collections.sort(sortList);
                median_value = sortList.get(middlePoint);
                err_value = errValuesHash.get(qvalue_key).get(tempArrayList.indexOf(median_value));

            } else { // even
                median_value = Statistics.calculateMedian(tempArrayList);
                sortList = new ArrayList(tempArrayList); //makes a copy
                Collections.sort(sortList);

                upperQI = tempArrayList.size()/2;
                lowerQI = upperQI-1;
                upperq = sortList.get(upperQI);
                lowerq = sortList.get(lowerQI);
                // look up values and reset to un-sorted array
                upperQI = tempArrayList.indexOf(upperq);
                lowerQI = tempArrayList.indexOf(lowerq);
                // get corresponding error values from datasets
                err_valueU = errValuesHash.get(qvalue_key).get(upperQI);
                err_valueL = errValuesHash.get(qvalue_key).get(lowerQI);
                err_value = 0.5*(err_valueL + err_valueU); // propagated error is an average
            }

            medianSet.add((double)qvalue_key, median_value);
            medianErrorSet.add((double)qvalue_key, err_value);

            tempArrayList = null;
        }

        // write out new Median DataSet
        return returnMe;
    }


    private ArrayList<XYSeries> weightedAverageDatasets(Collection selected){

        int ref = 0;
        int limit = selected.getDatasets().size();

        // use last as reference
        for(int i=0; i< limit; i++){
            if (selected.getDataset(i).getInUse()){
                ref = i;
            }
        }

        //System.out.println("REFERENCE SET TO : " + selected.getDataset(ref).getFileName());

        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();

        Dataset reference = selected.getDataset(ref); // reference is the last frame
        //XYSeries tempRefData;
        //tempRefData = reference.getData(); // log10 data used for plotting
        //double referenceQmin = tempRefData.getMinX();
        //double referenceQmax = tempRefData.getMaxX();

        Number referenceQmin = findLeastCommonQvalue(selected);
        Number referenceQmax = findMaximumCommonQvalue(selected);

        double scale, lower, upper, referenceQ;
        int refIndex, count;

        returnMe.add(new XYSeries("Summed"));
        returnMe.add(new XYSeries("SummedError"));

        XYSeries summedSet = returnMe.get(0);//new XYSeries("Summed");
        XYSeries summedSetError = returnMe.get(1);//new XYSeries("SummedError");

        double sigma, var, targetMax;
        Number targetMin;
        scale = reference.getScaleFactor();

        // based on the plotted data, find index of qmin and qmax in allData
        int lowerQI = reference.getAllData().indexOf(referenceQmin);
        int upperQI = reference.getAllData().indexOf(referenceQmax);
        int lowerT, upperT;
        /*
         * within limits of plotted data
         */
        for (int i=lowerQI; i < upperQI; i++){
            XYDataItem tempRefItem = reference.getAllData().getDataItem(i);
            sigma = 1.0/(scale*reference.getAllDataError().getY(i).doubleValue());
            var = sigma*sigma;

            summedSet.add(tempRefItem.getXValue(), scale * tempRefItem.getYValue()*var);
            summedSetError.add(tempRefItem.getXValue(), var);
        }

        XYDataItem tempDataItem, tempSummedItem;

        for (int i=0; i < limit; i++) {
            if (selected.getDataset(i).getInUse() && i != ref){

                Dataset tempDataset = selected.getDataset(i);

                XYSeries targetData  = tempDataset.getAllData();
                XYSeries targetError = tempDataset.getAllDataError();

                // get plotted domain of the target dataset
                // if averaging from SEC - plotted data will not be set - returns null
                try{
                    targetMin = tempDataset.getData().getX(0);
                    targetMax = tempDataset.getData().getMaxX();
                } catch (java.lang.NullPointerException exception){
                    targetMin = tempDataset.getAllData().getX(0);
                    targetMax = tempDataset.getAllData().getMaxX();
                }

                lowerT = targetData.indexOf(targetMin);
                upperT = targetData.indexOf(targetMax);

                lower = Math.max(referenceQmin.doubleValue(), targetMin.doubleValue());
                upper = Math.min(referenceQmax.doubleValue(), targetMax);

                scale = tempDataset.getScaleFactor();
                // iterate of the plotted range but within AllData (includes negative intensities)
                for(int j = lowerT; j <= upperT; j++){
                    tempDataItem = targetData.getDataItem(j);
                    double target_q = tempDataItem.getXValue();

                    if (((target_q < lower) || (target_q > upper)) && (summedSet.indexOf(target_q) < 0)) {
                        /*
                         *  if q value is outside range of reference
                         *  .indexOf() < 0 means value is not found in XYSeries
                         */
                        sigma = 1.0/(scale*targetError.getY(j).doubleValue());
                        var = sigma*sigma;

                        summedSet.add(target_q, scale * tempDataItem.getYValue()*var);
                        summedSetError.add(target_q, var );

                    } else {
                        refIndex = summedSet.indexOf(tempDataItem.getX());
                        if (refIndex >= 0) {
                            /*
                             * average the signal - first sum
                             */
                            tempSummedItem = summedSet.getDataItem(refIndex);

                            sigma = 1.0/(scale*targetError.getY(j).doubleValue());
                            var = sigma*sigma;

                            summedSet.updateByIndex(refIndex, (tempSummedItem.getYValue() + scale * targetData.getY(j).doubleValue() * var));
                            summedSetError.updateByIndex(refIndex, (summedSetError.getY(refIndex).doubleValue() + var));

                        } else if (lower < target_q && (target_q < upper)) { // no more interpolating
                            /*
                             * interpolate
                             */
                            System.out.println("StatMethods: Interpolating Dataset " + i + " target_q => " + target_q);
                            count = 0;
                            referenceQ = 0.0;
                            // find first value in reference greater than targetData.getX
                            while (summedSet.getX(count).doubleValue() < target_q && (count < 3000)) {
                                referenceQ = summedSet.getX(count).doubleValue();
                                count++;
                            }
                            System.out.println("StatMethods: Interpolating count " + count + " ref_q => " + referenceQ);

                            Double[] results = Functions.interpolate(targetData, referenceQ);
                            Double[] sigmaResults = Functions.interpolateSigma(targetError, referenceQ);
                            sigma = 1.0/(sigmaResults[1]*scale);
                            var = sigma*sigma;

                            //returns unlogged data
                            refIndex = summedSet.indexOf(referenceQ);

                            tempSummedItem = summedSet.getDataItem(refIndex);

                            summedSet.updateByIndex(refIndex, (tempSummedItem.getYValue() + scale * results[1] * var));
                            summedSetError.updateByIndex(refIndex, (summedSetError.getY(refIndex).doubleValue() + var ));
                        }
                    }
                }
            }
        }

        //double inv_total_n; // = 1.0/(double)total_n;
        for(int i=0; i<summedSet.getItemCount(); i++){

            var = summedSetError.getY(i).doubleValue();

            if ((Double.isNaN(var)) || (var == Double.POSITIVE_INFINITY)) {
                summedSet.updateByIndex(i, 0.0);
                summedSetError.updateByIndex(i, 1);
            } else {
                summedSet.updateByIndex(i, summedSet.getY(i).doubleValue() / var);
                summedSetError.updateByIndex(i, 1.0 / Math.sqrt(var));
            }

        }

        return returnMe;
    }

    /**
     *  Find least common q-value based on q-min of plotted data?
     *
     */
    private Number findLeastCommonQvalue(Collection dataCollection){

        boolean isCommon;

        Dataset firstSet = dataCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = dataCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        Number minQvalueInCommon = 10;

        outerloop:
        for(int j=0; j < referenceData.getItemCount(); j++){

            if (referenceData.getY(j).doubleValue() > 0){
                refItem = referenceData.getDataItem(j); // is refItem found in all sets
                minQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for(; startAt < totalInSampleSet; startAt++) {

                    tempDataset = dataCollection.getDataset(startAt);
                    tempData = tempDataset.getAllData();
                    // check if refItem q-value is in tempData
                    // if true, check next value
                    if (tempData.indexOf(refItem.getX()) < 0 && (tempData.getY(startAt).doubleValue() > 0)) {
                        isCommon = false;
                        break innerloop;
                    }
                }

                if (startAt == totalInSampleSet && isCommon){
                    break outerloop;
                }
            }
        }

        return minQvalueInCommon;
    }

    /**
     *
     */
    private Number findMaximumCommonQvalue(Collection dataCollection){

        boolean isCommon;

        Dataset firstSet = dataCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = dataCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        Number maxQvalueInCommon = 0;

        outerloop:
        for(int j=(referenceData.getItemCount()-1); j > -1; j--){

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            if (refItem.getYValue() > 0){
                maxQvalueInCommon = refItem.getX();
                isCommon = true;

                startAt = 1;
                innerloop:
                for(; startAt < totalInSampleSet; startAt++) {

                    tempDataset = dataCollection.getDataset(startAt);
                    tempData = tempDataset.getAllData();
                    // check if refItem q-value is in tempData
                    // if true, check next value
                    // not found returns -1 for indexOf
                    // startAt in tempData should return non-negative value
                    if (tempData.indexOf(refItem.getX()) < 0 && tempData.getY(startAt).doubleValue() > 0) {
                        isCommon = false;
                        break innerloop;
                    }
                }

                if (startAt == totalInSampleSet && isCommon){
                    break outerloop;
                }
            }
        }

        return maxQvalueInCommon;
    }

}


