package version4.ReportPDF;


import org.jfree.chart.title.TextTitle;
import rst.pdfbox.layout.elements.*;
import rst.pdfbox.layout.elements.Frame;
import rst.pdfbox.layout.shape.Rect;
import rst.pdfbox.layout.shape.Stroke;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import rst.pdfbox.layout.elements.render.RenderContext;
import rst.pdfbox.layout.elements.render.RenderListener;
import rst.pdfbox.layout.elements.render.VerticalLayoutHint;
import rst.pdfbox.layout.text.*;
import version4.AutoRg;
import version4.Collection;
import version4.Dataset;
import version4.Functions;
import version4.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MergeReport {

    private static BufferedImage signalPlot;
    private static Document document;
    private static MergeReport singleton = new MergeReport( );
    private static BufferedImage iofqPlot;
    private static BufferedImage mergedIofqPlot;
    private static BufferedImage differencePlot;
    private static BufferedImage rgIzeroDoublePlot;
    private static final float imageWidth = 270;
    private static final float imageHeight = 240;
    private static boolean isHPLCLayout = false, isMerged=false;
    private static String workingDirectory;
    private static ArrayList<String> rgIzeroRows;
    private static String signalLegend, log10OfAllText, durbinWatsonCompareText, rgIzeroText, mergedText;
    /* A private Constructor prevents any other
     * class from instantiating.
     */
    private MergeReport(){
        rgIzeroRows = new ArrayList<>();
        document = new Document(30, 30, 40, 60);
    }

    /* Static 'instance' method */
    public static MergeReport getInstance( ) {
        return singleton;
    }

    public static void setWorkingDirectory(String wd){
        workingDirectory = wd;
    }

    /**
     * clear all variables
     */
    public static void clear(){
        rgIzeroRows.clear();
        isHPLCLayout = false;
        isMerged=false;
        document = new Document(30, 30, 40, 60);
    }

    /**
     * top level chart, should have title of "A" if present
     * @param chart
     */
    public static void setSignalPlot(JFreeChart chart){
        signalLegend = "*A.* SEC-SAXS Signal Plot. Each point represents the integrated area of the ratio of the sample SAXS curve to the estimated background. ";
        signalPlot = chart.createBufferedImage(540*3,240*3);
    }


    private static BufferedImage makeLog10Plot(Collection returnCollection, String title){

        int totalSets = returnCollection.getTotalDatasets();
        XYSeriesCollection plottedDatasets = new XYSeriesCollection();
        double qlower=10, qupper=-1, ilower = 10, iupper = -1;

        for (int i=0; i < totalSets; i++){
            if (returnCollection.getDataset(i).getInUse()){
                plottedDatasets.addSeries(returnCollection.getDataset(i).getData()); // positive only data

                Dataset tempData = returnCollection.getDataset(i);

                if (tempData.getAllData().getMinX() < qlower){
                    qlower = tempData.getAllData().getMinX();
                }

                if (tempData.getAllData().getMaxX() > qupper){
                    qupper = tempData.getAllData().getMaxX();
                }

                if (tempData.getData().getMinY() < ilower){
                    ilower = tempData.getData().getMinY();
                }

                if (tempData.getData().getMaxY() > iupper){
                    iupper = tempData.getData().getMaxY();
                }
            }
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

            if (returnCollection.getDataset(i).getInUse()){
                Dataset tempData = returnCollection.getDataset(i);

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

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }


    // make subplot of selected region that is selected
    // contain the log10 plot of all subtracted signals
    public static void setSubtractedPlot(Collection returnCollection){
        String title = "A";
        log10OfAllText = "*A.* Overlay of SAXS curves of subtracted frames. Each frame is colored based on the following table.";
        if (isHPLCLayout){
            log10OfAllText = "*B.* Overlay of SAXS curves of subtracted frames. Each frame is colored based on the following table.";
            title = "B";
        }
        iofqPlot = makeLog10Plot(returnCollection, title);
    }

    public static void setMerged(boolean value){
        isMerged = value;
    }


    // signal plot
    // log10 plot
    // ratio/rg plot
    public static void setComparisonPlot(Collection inUseCollection, int pointsToExclude, boolean doGuinier) {

        int totalInCollection = inUseCollection.getTotalDatasets();
        int referenceDatasetIndex=0;

        for(int i=0; i<totalInCollection; i++){
            if (inUseCollection.getDataset(i).getInUse()){
                referenceDatasetIndex = i;
                break;
            }
        }


        Dataset referenceDataset = inUseCollection.getDataset(referenceDatasetIndex);
        Number qmin = referenceDataset.getAllData().getX(pointsToExclude);

        XYSeries differenceSeriesA =  new XYSeries("DifferenceSeriesA");
        XYSeries differenceSeriesB = new XYSeries("DifferenceSeriesB");

        // make ratio plot using same reference dataset
        for(int i=referenceDatasetIndex+1; i<totalInCollection; i++){

            Dataset targetDataset = inUseCollection.getDataset(i);
//            if (inUseCollection.getDataset(i).getInUse()){
//                ResidualDifferences differences = new ResidualDifferences(referenceDataset.getAllData(),
//                        targetDataset.getAllData(),
//                        targetDataset.getAllDataError(),
//                        qmin,
//                        0.27,
//                        12, referenceDataset.getId(), targetDataset.getId(), 0);
//
//                differenceSeriesA.add(targetDataset.getId(), differences.getDurbinWatsonStatistic());
//                differenceSeriesB.add(targetDataset.getId(), differences.getShapiroWilkStatistic());
//            }
        }

        String difftitle = "B";
        durbinWatsonCompareText = "*B.* Durbin-Watson and Shapiro-Wilks tests examining the distribution of the residuals between two frames. In this case, comparisons are made in reference to the first frame. Radiation damage or lack of similarity can be observed as a trend in either statistic across the frame set. Likewise, similarity is demonstrated by a random distribution of the statistics.";

        if (isHPLCLayout){
            durbinWatsonCompareText = "*C.* Durbin-Watson and Shapiro-Wilks tests examining the distribution of the residuals between two frames. In this case, comparisons are made in reference to the first frame. Radiation damage or lack of similarity can be observed as a trend in either statistic across the frame set. Likewise, similarity is demonstrated by a random distribution of the statistics.";
            difftitle = "C";
        }

        differencePlot = makeDoublePlot(differenceSeriesA, differenceSeriesB, "Durbin-Watson", "Shapiro-Wilks", difftitle).createBufferedImage((int)imageWidth*3,(int)imageHeight*3);

        // make Rg-Izero plot

        // String hex = String.format("#%02x%02x%02x", inUse.getRed(), inUse.getGreen(), inUse.getBlue());
        if (doGuinier){
            rgIzeroRows = new ArrayList<>();
            rgIzeroRows.add(getRgDataSummaryTopHeader());

            XYSeries guinierRgSeries = new XYSeries("Rg");
            XYSeries guinierIzeroSeries = new XYSeries("I(0)");

            for(int i=referenceDatasetIndex; i<totalInCollection; i++){

                Dataset targetDataset = inUseCollection.getDataset(i);
                if (inUseCollection.getDataset(i).getInUse()){
                    AutoRg tempRg = new AutoRg(targetDataset.getOriginalPositiveOnlyData(), pointsToExclude);
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

            difftitle = "C";
            rgIzeroText = "*C.* Double Y plot with I(0), orange, and R{_}g{_}, cyan, estimated from the Guinier region for each subtracted frame. For a single concentration measurement made over several frames, radiation damage will be observed as an increase in I(0) and R{_}g{_}. For SEC-SAXS, I(0) should change with the concentration of the particle during elution.";
            if (isHPLCLayout){
                difftitle = "D";
                rgIzeroText = "*D.* Double Y plot with I(0), orange, and R{_}g{_}, cyan, estimated from the Guinier region for each subtracted frame. For a single concentration measurement made over several frames, radiation damage will be observed as an increase in I(0) and R{_}g{_}. For SEC-SAXS, I(0) should change with the concentration of the particle during elution.";
            }

            rgIzeroDoublePlot = makeDoublePlot(guinierIzeroSeries, guinierRgSeries, "I(0)", "Rg Å", difftitle).createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
        }
    }


    private static String getRgDataSummaryTopHeader(){
        return String.format("%s %s %s", Report.rightJustifyText(" ",6,' '), Report.centerText("Guinier Izero",20, ' '), Report.centerText("Guinier Rg",20, ' '));
    }


    public static void createMergedChart(Collection mergedCollection){
        isMerged = true;
        mergedCollection.getDataset(0).setColor(Color.darkGray); // average
        mergedCollection.getDataset(1).setColor(Color.cyan); // median

        String difftitle = "D";
        if (isHPLCLayout){
            difftitle = "E";
        }
        mergedIofqPlot = makeLog10Plot(mergedCollection, difftitle);
    }



    public static void writeReport(String titleText){

        document = new Document(30, 30, 40, 60);
        document.addRenderListener(new RenderListener() {

            @Override
            public void beforePage(RenderContext renderContext) {}

            @Override
            public void afterPage(RenderContext renderContext) throws IOException {
                String content = String.format("%s", renderContext.getPageIndex() + 1);
                TextFlow text = TextFlowUtil.createTextFlow(content, 11, PDType1Font.TIMES_ROMAN);

                float offset = renderContext.getPageFormat().getMarginLeft() + TextSequenceUtil.getOffset(text, renderContext.getWidth(), Alignment.Right);
                text.drawText(renderContext.getContentStream(), new Position(offset, 30), Alignment.Right, null);
            }
        });

//        try {
//            Paragraph title = new Paragraph();
//            title.addMarkup(escape(titleText), 10, BaseFont.Times);
//            document.add(title, VerticalLayoutHint.CENTER);
//            document.add(new VerticalSpacer(5));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        // add Signal Plot of trace
        if (isHPLCLayout){
            ImageElement signalPlotImage = new ImageElement(signalPlot);
            signalPlotImage.setWidth(540);
            signalPlotImage.setHeight(240);
            document.add(signalPlotImage, new VerticalLayoutHint(Alignment.Center, 0, 0 , 0, 0, true));
            document.add(new VerticalSpacer(240));

            signalPlot.flush();
        }


        ImageElement leftUpperImage = new ImageElement(iofqPlot);
        leftUpperImage.setWidth(imageWidth);
        leftUpperImage.setHeight(imageHeight);
        document.add(leftUpperImage, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));
        iofqPlot.flush();

        ImageElement rightUpperImage = new ImageElement(differencePlot);
        rightUpperImage.setWidth(imageWidth);
        rightUpperImage.setHeight(imageHeight);
        document.add(rightUpperImage, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));
        differencePlot.flush();

        document.add(new VerticalSpacer(240)); // add Rg Izero Double plot

        ImageElement lowerLeftImage = new ImageElement(rgIzeroDoublePlot);
        lowerLeftImage.setWidth(imageWidth);
        lowerLeftImage.setHeight(imageHeight);
        document.add(lowerLeftImage, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));
        rgIzeroDoublePlot.flush();

        String legendE ="";
        if (isMerged){
            ImageElement lowerRightImage = new ImageElement(mergedIofqPlot);
            lowerRightImage.setWidth(imageWidth);
            lowerRightImage.setHeight(imageHeight);
            document.add(lowerRightImage, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));
            legendE = "*E.* Log_10_ intensity plot of subtracted and merged SAXS frames. Black represents averaged buffer frames subtracted from averaged sampled frames. Cyan represents median of the buffer frames subtracted from the averaged sample frames. Poor buffer subtraction leads to a displacement between the two curves at high-_q_";
            mergedIofqPlot.flush();
        }


        if (isHPLCLayout){
            document.add(ControlElement.NEWPAGE);
        } else {
            document.add(new VerticalSpacer(240)); // add Rg Izero Double plot
        }

        try {
            String legend = (isHPLCLayout) ? signalLegend : "";
            legend += log10OfAllText + " " + durbinWatsonCompareText + " " + rgIzeroText + " " + legendE;

            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup("*Figure | " + escape(titleText) + ".* " + legend, 9, BaseFont.Times);
            tempparagraph.setMaxWidth((float)(document.getPageWidth()/1.2));
            tempparagraph.setAlignment(Alignment.Justify);
            document.add(tempparagraph, VerticalLayoutHint.CENTER);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // add Rg Izero Table
        try {
            document.add(new VerticalSpacer(20)); // add Rg Izero Double plot
            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup(rgIzeroRows.get(0), 10, BaseFont.Courier);
            Frame headerFrame = new Frame(tempparagraph, (float)(document.getPageWidth()/1.2), 12f);
            headerFrame.setShape(new Rect());
            headerFrame.setBorder(Color.WHITE, new Stroke(0.5f));
            headerFrame.setPadding(0, 0, 0, 0);
            document.add(headerFrame, VerticalLayoutHint.CENTER);

            Frame lineFrame = new Frame(new Paragraph(), (float)(document.getPageWidth()/1.2), 1f);
            lineFrame.setShape(new Rect());
            lineFrame.setBorder(Color.black, new Stroke(0.5f));
            lineFrame.setPadding(0, 0, 0, 0);
            document.add(lineFrame, VerticalLayoutHint.CENTER);

            int totalrows = rgIzeroRows.size();
            for(int i=1; i<totalrows; i++){
                Paragraph tempparag = new Paragraph();
                tempparag.addMarkup(rgIzeroRows.get(i), 10, BaseFont.Courier);
                rst.pdfbox.layout.elements.Frame frame = new Frame(tempparag, (float)(document.getPageWidth()/1.2), 10f);
                frame.setShape(new Rect());
                frame.setBorder(Color.white, new Stroke(0.5f));
                frame.setPadding(0, 0, 0, 0);
                document.add(frame, VerticalLayoutHint.CENTER);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        final OutputStream outputStream;

        try {
            String filename = Functions.sanitizeForFilename(titleText);
            String outputname = workingDirectory+"/"+filename+".pdf";

            Path path = Paths.get(outputname);
            Files.deleteIfExists(path);

            outputStream = new FileOutputStream(outputname);
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


    private static String escape(String text){
        String temp = text.replaceAll("/", " per ");
        //temp = temp.replaceAll(" ", "_");
        return temp.replace("_", "\\_");
    }


    private static NumberAxis getRangeAxis(String quote, double ilower, double iupper){
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

    private static NumberAxis getDomainAxis(String quote){
        final NumberAxis domainAxis = new NumberAxis(quote);
        domainAxis.setLabelFont(Constants.FONT_BOLD_20);
        domainAxis.setTickLabelFont(Constants.FONT_BOLD_20);
        domainAxis.setLabel(quote);
        domainAxis.setAutoRangeStickyZero(true);
        domainAxis.setAxisLineStroke(new BasicStroke(3.0f));
        domainAxis.setTickMarkStroke(new BasicStroke(3.0f));

        return domainAxis;
    }

    public static void setHPLCLayout(boolean flag){
        isHPLCLayout = flag;
    }


    private static JFreeChart makeDoublePlot(XYSeries leftSeries, XYSeries rightSeries, String leftTitle, String rightTitle, String title) {
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


}
