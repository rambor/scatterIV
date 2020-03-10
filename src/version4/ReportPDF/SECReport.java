package version4.ReportPDF;

import org.apache.commons.math3.optim.InitialGuess;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import rst.pdfbox.layout.elements.*;
import rst.pdfbox.layout.elements.render.RenderContext;
import rst.pdfbox.layout.elements.render.RenderListener;
import rst.pdfbox.layout.elements.render.VerticalLayoutHint;
import rst.pdfbox.layout.text.*;
import version4.Collection;
import version4.BinaryComparison.ResidualDifferences;
import version4.Dataset;
import version4.SEC.SECFile;
import version4.Constants;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class SECReport {

    private SECFile secFile;
    private String workingDirectory;
    boolean isMerged = false;
    private ArrayList<Double> scaleFactors;
    int startIndex, endIndex;
    private final float imageWidth = 270;
    private final float imageHeight = 240;
    private double pointSize = 6;
    private float stroke = 1.0f;
    private ArrayList<String> rgIzeroRows;
    private ArrayList<Color> plotColors;
    private XYSeries merged;
    private XYSeries median;
    private String signalLegend, log10OfAllText, rgIzeroText, durbinWatsonCompareText, legendE;

    public SECReport(int startIndex, int endIndex, SECFile secFile, String workingDirectory, boolean isMerged){
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.secFile = secFile;
        this.workingDirectory = workingDirectory;
        this.isMerged = isMerged;
        merged = new XYSeries("merged");
        median = new XYSeries("median");
    }

    /**
     *
     * @param titleText title of the PDF and filename
     */
    public void writeReport(String titleText) {

        Random rN = new Random();
        plotColors = new ArrayList<>();
        for (int i=startIndex; i < endIndex; i++){ // set color, covers entire range between start and end Indices
            plotColors.add(new Color(rN.nextInt(256),rN.nextInt(256),rN.nextInt(256)));
        }

        Document document = new Document(30, 30, 40, 60);
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

        // set Signal Plot at Top (Panel A)
        ImageElement signalPlotImage = new ImageElement(makeSignalPlot());
        signalPlotImage.setWidth(540);
        signalPlotImage.setHeight(240);
        document.add(signalPlotImage, new VerticalLayoutHint(Alignment.Center, 0, 0 , 0, 0, true));
        document.add(new VerticalSpacer(240));

        // Next left hand image are the subtracted SAXS curves (Panel B)
        BufferedImage iofqPlot;
//        if (isMerged){
            iofqPlot = makeLog10PlotSScaled("B");
//        } else { // unscaled
//
//        }

        // B. Overlay subtracted curves, log10
        ImageElement leftUpperImage = new ImageElement(iofqPlot);
        leftUpperImage.setWidth(imageWidth);
        leftUpperImage.setHeight(imageHeight);
        document.add(leftUpperImage, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));
//        iofqPlot.flush();
//
//        // C. DurbinWatson, ShapiroWilkes
        ImageElement rightUpperImage = new ImageElement(makeComparisonDatasets(27));
        rightUpperImage.setWidth(imageWidth);
        rightUpperImage.setHeight(imageHeight);
        document.add(rightUpperImage, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));

        document.add(new VerticalSpacer(240)); // add Rg Izero Double plot
//
//        // D. Rg Izero Plot
        ImageElement lowerLeftImage = new ImageElement(makeRgIzeroPlot());
        lowerLeftImage.setWidth(imageWidth);
        lowerLeftImage.setHeight(imageHeight);
        document.add(lowerLeftImage, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));
//
//        // E. Merged curve
        ImageElement lowerRightImage = new ImageElement(createMergedChart());
        lowerRightImage.setWidth(imageWidth);
        lowerRightImage.setHeight(imageHeight);
        document.add(lowerRightImage, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));

        document.add(ControlElement.NEWPAGE);

        try {
            String legend = signalLegend;
            legend += log10OfAllText + " " + durbinWatsonCompareText + " " + rgIzeroText + " " + legendE;

            Paragraph tempparagraph = new Paragraph();
            legend = "*Figure | " + escape(titleText) + ".* " + legend;
            tempparagraph.addMarkup(legend, 9, BaseFont.Times);
            tempparagraph.setMaxWidth((float)(document.getPageWidth()/1.2));
            tempparagraph.setAlignment(Alignment.Justify);
            document.add(tempparagraph, VerticalLayoutHint.CENTER);

        } catch (IOException e) {
            e.printStackTrace();
        }


        final OutputStream outputStream;

        try {
            String filename = sanitizeForFilename(titleText);
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

    public BufferedImage createMergedChart(){
        XYSeriesCollection plotThis = new XYSeriesCollection();

        int total = merged.getItemCount();
        plotThis.addSeries(new XYSeries("merged"));
        XYSeries tempS = plotThis.getSeries(0);
        for(int i=0; i<total; i++){
            XYDataItem item = merged.getDataItem(i);
            if (item.getYValue() > 0){
                tempS.add(item.getX(), Math.log10(item.getYValue()));
            }
        }
        plotThis.addSeries(new XYSeries("median"));
        tempS = plotThis.getSeries(1);
        total = median.getItemCount();
        for(int i=0; i<total; i++){
            XYDataItem item = median.getDataItem(i);
            if (item.getYValue() > 0){
                tempS.add(item.getX(), Math.log10(item.getYValue()));
            }
        }

        ArrayList<Color> pcolors = new ArrayList<>();
        pcolors.add(Color.gray);
        pcolors.add(Color.cyan);
        legendE = "*E.* Log_10_ intensity plot of subtracted and merged SAXS frames. Black represents averaged buffer frames subtracted from averaged sampled frames. Cyan represents median of the buffer frames subtracted from the averaged sample frames. Poor buffer subtraction leads to a displacement between the two curves at high-_q_";
        return makeLog10Plot(plotThis, pcolors, "E");
    }


    private BufferedImage makeSignalPlot(){
        signalLegend = "*A.* SEC-SAXS Signal Plot. Each point represents the integrated area of the ratio of the sample SAXS curve to the estimated background. The frames used as the buffer background are in gray with the average represented as the gray horizontal line. Elevation of the baseline after peak elution suggests capillary fouling. ";
        // plot both signal and selected buffer
        XYSeries signal = secFile.getSignalSeries();
        XYSeries buffers = secFile.getBackground();
        XYSeriesCollection plotMe = new XYSeriesCollection();
        plotMe.addSeries(buffers);
        plotMe.addSeries(signal);

        double averageIt = 0.0d;
        for(int b=0; b < buffers.getItemCount(); b++){
            averageIt += buffers.getY(b).doubleValue();
        }
        averageIt /= buffers.getItemCount();

        JFreeChart chart = ChartFactory.createXYLineChart(
                    "Signal Plot",               // chart title
                    "sample",                 // domain axis label
                    "signal",                 // range axis label
                    plotMe,                              // data
                    PlotOrientation.VERTICAL,
                    false,                     // include legend
                    true,                     // toolTip
                    false
            );

        chart.setTitle("A");
        chart.getTitle().setFont(new java.awt.Font("Times",  Font.BOLD, 42));
        chart.getTitle().setPaint(Color.BLACK);
        chart.getTitle().setTextAlignment(HorizontalAlignment.LEFT);
        chart.getTitle().setHorizontalAlignment(HorizontalAlignment.LEFT);

        final XYPlot plot = chart.getXYPlot();
            /*
             set domain and range axis
             */
        final NumberAxis domainAxis = new NumberAxis("frame number");
        final NumberAxis rangeAxis = new NumberAxis("Integral of Ratio to Background");

        domainAxis.setLabelFont(Constants.FONT_BOLD_20);
        domainAxis.setTickLabelFont(Constants.FONT_BOLD_20);
        rangeAxis.setLabelFont(Constants.FONT_BOLD_20);
        rangeAxis.setTickLabelFont(Constants.FONT_BOLD_20);
        rangeAxis.setRange(plotMe.getRangeLowerBound(true) - 0.01*plotMe.getRangeLowerBound(true), plotMe.getRangeUpperBound(true) + 0.02*plotMe.getRangeUpperBound(true));
        rangeAxis.setAutoRangeIncludesZero(false);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);

        plot.setDataset(0, plotMe);
        plot.mapDatasetToRangeAxis(0, 0);//1st dataset to 1st y-axis
        double negativePointSize, pointSize;

        plot.setBackgroundAlpha(0.0f);
        plot.setOutlineVisible(false);

        ValueMarker mark = new ValueMarker(averageIt, Color.GRAY, new BasicStroke(2), Color.GRAY, null, 1);
        plot.addRangeMarker(mark);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();

        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShape(new Ellipse2D.Double(-0.5*6, -0.5*6.0, 6, 6));

//        Color fillColor = new Color(255, 140, 0, 70);
        Color fillColor = new Color(0, 170, 255, 70);
        Color outlineColor = new Color(0, 180, 255, 70);

        // go over each series
        pointSize = 9;
        negativePointSize = -0.5*pointSize;

        renderer1.setSeriesShape(0, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
        renderer1.setSeriesLinesVisible(0, false);
        renderer1.setSeriesPaint(0, Color.DARK_GRAY);
        renderer1.setSeriesShapesFilled(0, true);
        renderer1.setSeriesOutlinePaint(0, outlineColor);
        renderer1.setSeriesOutlineStroke(0, new BasicStroke(2.0f));

        // set rendered for non-buffer frames
        renderer1.setSeriesShape(1, new Ellipse2D.Double(negativePointSize, negativePointSize, pointSize, pointSize));
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesPaint(1, fillColor);
        renderer1.setSeriesShapesFilled(1, true);
        renderer1.setSeriesOutlinePaint(1, outlineColor);
        renderer1.setSeriesOutlineStroke(1, new BasicStroke(2.0f));


        return chart.createBufferedImage(540*3,240*3);
    }
    /**
     * exclude any Rg or Izero points that are null (==0)
     *
     * @return
     */
    private BufferedImage makeRgIzeroPlot(){
        XYSeries guinierRgSeries = new XYSeries("Rg");
        XYSeries guinierIzeroSeries = new XYSeries("I(0)");

        boolean izeroError = (secFile.getSasObject().getSecFormat().getIzero_error_index() > 0) ? true : false;
        boolean rgError = (secFile.getSasObject().getSecFormat().getRg_error_index() > 0) ? true : false;

        int count = 0;
        for(int i = startIndex; i<endIndex; i++){
            double rg = secFile.getRgbyIndex(i);
            double iz = secFile.getIzerobyIndex(i);
            if (rg > 0 && iz > 0){
                guinierRgSeries.add(i, rg);
                guinierIzeroSeries.add(i, iz);

                double izeroerr = (izeroError) ? secFile.getIzeroErrorbyIndex(i) : 0.0d;
                double rgerr = (rgError) ? secFile.getRgErrorbyIndex(i) : 0.0d;

                String reciIzero = String.format("%1.2E +- %1.1E", iz, izeroerr);
                String reciRg = String.format("%.2f +- %.2f",rg, rgerr);
                Color pcolor = plotColors.get(count);
                String hex = String.format("#%02x%02x%02x", pcolor.getRed(), pcolor.getGreen(), pcolor.getBlue());

                // make table of values I(0) Rg filenames
                String fileNameToUse = "frame_" + i;
                int lengthOfFilename = fileNameToUse.length();
                if (lengthOfFilename > 20){
                    fileNameToUse = fileNameToUse.substring(0,20);
                }
                rgIzeroRows.add(String.format("{color:%s} %s %s %s %s", hex, Report.rightJustifyText(Integer.toString(i), 6, ' '), Report.centerText(reciIzero,20, ' '), Report.centerText(reciRg,20, ' '), fileNameToUse));
            }
            count++;
        }

        String difftitle = "D";
        rgIzeroText = "*D.* Double Y plot with I(0), orange, and R{_}g{_}, cyan, estimated from the Guinier region for each subtracted frame. For a single concentration measurement made over several frames, radiation damage will be observed as an increase in I(0) and R{_}g{_}. For SEC-SAXS, I(0) should change with the concentration of the particle during elution.";

        return makeDoublePlot(guinierIzeroSeries, guinierRgSeries, "I(0)", "Rg Å", difftitle).createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }

    private BufferedImage makeComparisonDatasets( int pointsToExclude){

        ArrayList<Double> qvalues = secFile.getQvalues();

        double qmin = qvalues.get(pointsToExclude);
        double qmax = 0.27;

        XYSeries referenceDataset = new XYSeries("reference");

        this.populateSubtractedXYSeries(pointsToExclude, qmax, referenceDataset, secFile.getSubtractedFrameAt(startIndex));

        // make ratio plot using same reference dataset
        XYSeries differenceSeriesA = new XYSeries("DifferenceSeriesA");
        XYSeries differenceSeriesB = new XYSeries("DifferenceSeriesB");

        int startAt = startIndex + 1;
        for(int i=startAt; i<endIndex; i++){
            XYSeries target = new XYSeries("target");
            XYSeries targetError = new XYSeries("targetError");

            this.populateSubtractedXYSeries(pointsToExclude, qmax, target, secFile.getSubtractedFrameAt(i));
            this.populateSubtractedXYSeries(pointsToExclude, qmax, targetError, secFile.getSubtractedErrorAtFrame(i));

            ResidualDifferences differences = new ResidualDifferences(referenceDataset,
                    target,
                    targetError,
                    qmin,
                    qmax,
                    12, startIndex, i, 0);

            differenceSeriesA.add(i, differences.getDurbinWatsonStatistic());
            differenceSeriesB.add(i, differences.getShapiroWilkStatistic());
        }

        durbinWatsonCompareText = "*C.* Durbin-Watson and Shapiro-Wilks tests examining the distribution of the residuals between two frames. In this case, comparisons are made in reference to the first frame. Radiation damage or lack of similarity can be observed as a trend in either statistic across the frame set. Likewise, similarity is demonstrated by a random distribution of the statistics.";
        String difftitle = "C";

        return makeDoublePlot(differenceSeriesA, differenceSeriesB, "Durbin-Watson", "Shapiro-Wilks", difftitle).createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
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

        System.out.println("UpperLeft " + upperLeft);

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

        plot.setDomainAxis(0, getDomainAxis("frame number"));
        plot.setRangeAxis(0, getRangeAxis(leftTitle, lowerLeft, upperLeft));
        plot.getRangeAxis().setRange(lowerLeft, upperLeft+ 0.05*upperLeft); // correct for the increase by getRangeAxis function
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


    private void populateSubtractedXYSeries(int pointsToExclude, double qmax, XYSeries series, ArrayList<Double> dataToAdd){
        double qvalue = secFile.getQvalues().get(pointsToExclude);
        for(int i=pointsToExclude; qvalue < qmax; i++){
            series.add(qvalue, dataToAdd.get(i));
            qvalue = secFile.getQvalues().get(i+1);
        }
    }

    private BufferedImage makeLog10Plot(XYSeriesCollection plottedDatasets, ArrayList<Color> colors, String title){

        double qlower=10, qupper=-1, ilower = 10, iupper = -1;
        int totalSets = plottedDatasets.getSeriesCount();

        for (int i=0; i < totalSets; i++){
            XYSeries ser = plottedDatasets.getSeries(i);
            if (ser.getMinX() < qlower){
                qlower = ser.getMinX();
            }

            if (ser.getMaxX() > qupper){
                qupper = ser.getMaxX();
            }

            if(ser.getMaxY() > iupper){
                iupper = ser.getMaxY();
            }

            if(ser.getMinY() < ilower){
                ilower = ser.getMinY();
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
        for (int i=0; i < totalSets; i++){
            offset = -0.5*pointSize;
            renderer1.setSeriesShape(i, new Ellipse2D.Double(offset, offset, pointSize, pointSize));
            renderer1.setSeriesLinesVisible(i, false);
            renderer1.setSeriesPaint(i, colors.get(i));
            renderer1.setSeriesShapesFilled(i, true);
            renderer1.setSeriesVisible(i, true);
            renderer1.setSeriesOutlineStroke(i, new BasicStroke(stroke));
        }

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }

    private BufferedImage makeLog10PlotSScaled(String title){

        log10OfAllText = "*B.* Overlay of SAXS curves of subtracted frames. Each frame is colored based on the following table.";

        ArrayList<Double> qvalues = secFile.getQvalues();
        ArrayList<Double> target = secFile.getSubtractedFrameAt(startIndex);
        ArrayList<Double> tarErrors = secFile.getSubtractedErrorAtFrame(startIndex);

        ArrayList<Double> weightedISum = new ArrayList<>(qvalues.size());
        ArrayList<Double> weightedESum = new ArrayList<>(qvalues.size());

        // get scalefactors
        // scale original data
        // put in XYCollection for plot
        XYSeriesCollection plottedDatasets = new XYSeriesCollection();
        double qvalue, log10value, value, std, var, scale = scaleFactors.get(0);
        double qlower=10, qupper=-1, ilower = Double.POSITIVE_INFINITY, iupper = Double.NEGATIVE_INFINITY;

        plottedDatasets.addSeries(new XYSeries(Integer.toString(startIndex)));
        XYSeries temp = plottedDatasets.getSeries(0);

        for(int i=0; i<qvalues.size(); i++){
            value = target.get(i).doubleValue()*scale;
            log10value = Math.log10(value);
            temp.add(qvalues.get(i).doubleValue(), log10value);
            std = 1.0d/(tarErrors.get(i)*scale);
            var = std*std;
            weightedISum.add((value*var));
            weightedESum.add(var);
            if (log10value > iupper){
                iupper = log10value;
            }

            if (log10value < ilower){
                ilower = log10value;
            }
        }

        // perform final scaling of each SAXS curve
        int count = 1;
        for(int next = startIndex+1; next < endIndex; next++){
            target = secFile.getSubtractedFrameAt(next);
            tarErrors = secFile.getSubtractedErrorAtFrame(next);
            scale = scaleFactors.get(count);

            plottedDatasets.addSeries(new XYSeries(Integer.toString(next)));
            temp = plottedDatasets.getSeries(count);

            for(int i=0; i<qvalues.size(); i++){
                std = 1.0d/(tarErrors.get(i)*scale);
                var = std*std;
                value = target.get(i).doubleValue()*scale;
                log10value = Math.log10(value);

                temp.add(qvalues.get(i).doubleValue(), log10value);

                // perform weighted average
                weightedISum.set(i, weightedISum.get(i) + (value*var));
                weightedESum.set(i, weightedESum.get(i) + var);
                if (log10value > iupper){
                    iupper = log10value;
                }
                if (log10value < ilower){
                    ilower = log10value;
                }
            }
            count+=1;
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
//        Random rN = new Random();
        rgIzeroRows = new ArrayList<>();
        rgIzeroRows.add(getRgDataSummaryTopHeader());

        for (int i=0; i < count; i++){ // set color, covers entire range between start and end Indices
            offset = -0.5*pointSize;
//            plotColors.add(new Color(rN.nextInt(256),rN.nextInt(256),rN.nextInt(256)));
            renderer1.setSeriesShape(i, new Ellipse2D.Double(offset, offset, pointSize, pointSize));
            renderer1.setSeriesLinesVisible(i, false);
            renderer1.setSeriesPaint(i, plotColors.get(i));
            renderer1.setSeriesShapesFilled(i, false);
            renderer1.setSeriesVisible(i, true);
            renderer1.setSeriesOutlineStroke(i, new BasicStroke(stroke));
            secFile.getRgbyIndex(startIndex+i);
        }

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }

    public void setScaleFactors(ArrayList<Double> scaleFactors){
        this.scaleFactors = scaleFactors;
    }

    private NumberAxis getRangeAxis(String quote, double ilower, double iupper){
        final NumberAxis rangeAxis = new NumberAxis(quote);
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRange(false);
        rangeAxis.setLabelFont(Constants.FONT_BOLD_20);
        rangeAxis.setTickLabelFont(Constants.FONT_BOLD_18);
        rangeAxis.setTickLabelsVisible(false);

        double upperbound = iupper+Math.abs(0.17*iupper);
        rangeAxis.setRange(ilower-ilower*0.03, upperbound);
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

    private String getRgDataSummaryTopHeader(){
        return String.format("%s %s %s", Report.rightJustifyText(" ",6,' '), Report.centerText("Guinier Izero",20, ' '), Report.centerText("Guinier Rg",20, ' '));
    }


    public String sanitizeForFilename(String text){
        String temp = text.replaceAll("/", "_per_");
        temp = temp.replaceAll(" ", "_");
        return temp;
    }


    public void setMergedAndMedian(XYSeries merged, XYSeries median){
        this.merged = merged;
        this.median = median;
    }

    private String escape(String text){
        String temp = text.replaceAll("/", " per ");
        //temp = temp.replaceAll(" ", "_");
        return temp.replace("_", "\\_");
    }
}
