package version4.ReportPDF;


import FileManager.WorkingDirectory;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleInsets;
import rst.pdfbox.layout.elements.*;
import rst.pdfbox.layout.elements.Frame;
import rst.pdfbox.layout.elements.render.ColumnLayout;
import rst.pdfbox.layout.elements.render.RenderContext;
import rst.pdfbox.layout.elements.render.RenderListener;
import rst.pdfbox.layout.elements.render.VerticalLayoutHint;
import rst.pdfbox.layout.shape.Rect;
import rst.pdfbox.layout.shape.Stroke;
import rst.pdfbox.layout.text.*;
import version4.Collection;
import version4.Dataset;
import version4.Functions;
import version4.Constants;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class Report {

    private Collection inUseCollection;
    private int totalInCollection;
    private final float imageWidth = 270;
    private final float imageHeight = 240;
    private PDFont pdfont = PDType1Font.COURIER;
    private String legendB;
    private String legendNote;
    private String legendTitle="";
    private boolean includeIzeroRgPlot;
    /**
     * create report using selected files in Collection
     * @param collection
     * @param workingDirectory
     */
    public Report(Collection collection, WorkingDirectory workingDirectory, String filename, String titleOf, String info){
        inUseCollection = collection;
        totalInCollection = inUseCollection.getTotalDatasets();
        this.setLegendNote(info);

        boolean addIzeroRgPlot = true;
        if (collection.getTotalDatasets() == 1){
            addIzeroRgPlot = false;
        }

        Document document = buildBasePlotsFromCollection(titleOf, addIzeroRgPlot);

        //Document document = new Document(30, 30, 40, 60);
//        document.add(ControlElement.NEWPAGE);
//        document.add(new VerticalSpacer(20));

//        try {
//            Paragraph paragraph = new Paragraph();
//            paragraph.addText("Notes", 10, PDType1Font.TIMES_BOLD);
//            document.add(paragraph);
//
//            Paragraph notes = new Paragraph();
//            notes.addMarkup(info, 10, BaseFont.Times);
//            document.add(notes);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        document.add(ControlElement.NEWPAGE);
        addDetails(document);

        final OutputStream outputStream;

        try {
            outputStream = new FileOutputStream(workingDirectory.getWorkingDirectory()+"/"+ Functions.sanitizeForFilename(filename)+".pdf");
            document.save(outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * create report using single selected Dataset
     * @param dataset
     * @param workingDirectory
     */
    public Report(Dataset dataset, WorkingDirectory workingDirectory){
        inUseCollection = new Collection("report");
        inUseCollection.addDataset(dataset);
        inUseCollection.getDataset(0).setInUse(true);
        totalInCollection = 1;

        this.setLegendNote(dataset.getExperimentalNotes());

        boolean useIt = false;
        Document document;
        if (dataset.getExperimentalNoteTitle().length() > 1){
            legendTitle = escape(dataset.getExperimentalNoteTitle());
            useIt=true;
        }

        if (useIt){ // set the title of the page at top
            document = buildBasePlotsFromCollection(legendTitle, false);
        } else {
            document = buildBasePlotsFromCollection(dataset.getFileName(), false);
        }


        document.add(ControlElement.NEWPAGE);
        document.add(new VerticalSpacer(40));

        ImageElement residualsGuinier = new ImageElement(createGuinierResidualsPlotFromDataset(dataset));
        residualsGuinier.setWidth(imageWidth);
        residualsGuinier.setHeight(imageHeight);
        document.add(residualsGuinier, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));


        ImageElement guinier = new ImageElement(createGuinierPlotFromDataset(dataset));
        guinier.setWidth(imageWidth);
        guinier.setHeight(imageHeight);
        document.add(guinier, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));

        // add table
        document.add(new VerticalSpacer(300));

        try {
            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup(getDataSummaryTopHeader(), 10, BaseFont.Courier);
            Frame headerFrame = new Frame(tempparagraph, (float)(document.getPageWidth()/1.2), 12f);
            headerFrame.setShape(new Rect());
            headerFrame.setBorder(Color.WHITE, new rst.pdfbox.layout.shape.Stroke(0.5f));
            headerFrame.setPadding(0, 0, 0, 0);
            document.add(headerFrame, VerticalLayoutHint.CENTER);

            Frame lineFrame = new Frame(new Paragraph(), (float)(document.getPageWidth()/1.2), 1f);
            lineFrame.setShape(new Rect());
            lineFrame.setBorder(Color.black, new rst.pdfbox.layout.shape.Stroke(0.5f));
            lineFrame.setPadding(0, 0, 0, 0);
            document.add(lineFrame, VerticalLayoutHint.CENTER);

            ArrayList<String> rows = this.getSummaryTableRowsFromDataset(dataset);
            int totalrows = rows.size();
            for(int i=0; i<totalrows; i++){
                Paragraph tempparag = new Paragraph();
                tempparag.addMarkup(rows.get(i), 10, BaseFont.Courier);
                Frame frame = new Frame(tempparag, (float)(document.getPageWidth()/1.2), 12f);
                frame.setShape(new Rect());
                frame.setBorder(Color.white, new rst.pdfbox.layout.shape.Stroke(0.5f));
                frame.setPadding(0, 0, 0, 0);
                document.add(frame, VerticalLayoutHint.CENTER);
            }

            document.add(lineFrame, VerticalLayoutHint.CENTER);
            String legend = "All values are reported in non-SI units of Angstroms (A). For biological particles, Porod exponent can only be within 2 and 4 inclusive. Bin-width is the effective, real-space resolution of the P(r)-distribution calculated as d{_}max{_} divided by Shannon number, N_s_.";
            Paragraph legendP = new Paragraph();
            legendP.setMaxWidth((float)(document.getPageWidth()/1.2));
            legendP.addMarkup(legend,10, BaseFont.Times);
            legendP.setAlignment(Alignment.Justify);
            document.add(legendP, VerticalLayoutHint.CENTER);

        } catch (IOException e) {
            e.printStackTrace();
        }

        document.add(ControlElement.NEWPAGE);
        addDetails(document);

        final OutputStream outputStream;

        try {
            outputStream = new FileOutputStream(workingDirectory.getWorkingDirectory()+"/"+Functions.sanitizeForFilename(dataset.getFileName())+".pdf");
            document.save(outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Document buildBasePlotsFromCollection(String titleOf, boolean addIzeroRgPlot){
        // make scaled log10 plot

        // make dimensionless kratky plot

        // make q*I(q) plot

        // make P(r)-plot

        Document document = new Document(30, 30, 40, 60);
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


        try {
            Paragraph title = new Paragraph();
            title.addMarkup(escape(titleOf), 10, BaseFont.Times);
            document.add(title, VerticalLayoutHint.CENTER);
            document.add(new VerticalSpacer(5));
        } catch (IOException e) {
            e.printStackTrace();
        }


        BufferedImage log10 = createLog10ChartFromCollection();
        BufferedImage kratky = createKratklyPlotFromCollection();
        BufferedImage qIq = createqIqPlotFromCollection();

        // Make Log10 Plot
        ImageElement imageLog10 = new ImageElement(log10);
        imageLog10.setWidth(imageWidth);
        imageLog10.setHeight(imageHeight);
        document.add(imageLog10, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));

        // Make Kratky Plot
        ImageElement imageKratky = new ImageElement(kratky);
        imageKratky.setWidth(imageWidth);
        imageKratky.setHeight(imageHeight);
        document.add(imageKratky, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));

        // Make qIq Plot
        document.add(new VerticalSpacer(240));
        ImageElement imageqIqPlot = new ImageElement(qIq);
        imageqIqPlot.setWidth(imageWidth);
        imageqIqPlot.setHeight(imageHeight);
        document.add(imageqIqPlot, new VerticalLayoutHint(Alignment.Left, 0, 0 , 0, 0, true));


        includeIzeroRgPlot = false;
        if (addIzeroRgPlot){
            includeIzeroRgPlot = true;
            ImageElement izerorg = new ImageElement(createIZeroRgPlotFromCollection());
            izerorg.setWidth(imageWidth);
            izerorg.setHeight(imageHeight);
            document.add(izerorg, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));
        }

        // add Pr distribution plot if single
        if (inUseCollection.getTotalDatasets() == 1 && !addIzeroRgPlot && inUseCollection.getDataset(0).getRealRg() > 0){
            ImageElement prplot = new ImageElement(createPrPlotFromDataset(inUseCollection.getDataset(0)));
            prplot.setWidth(imageWidth);
            prplot.setHeight(imageHeight);
            document.add(prplot, new VerticalLayoutHint(Alignment.Right, 0, 0 , 0, 0, true));
        }

        // add table of results (color text by data color)
        document.add(new VerticalSpacer(240));

        // make figure legend

        boolean usePr = false;
        if (inUseCollection.getDataset(0).getRealRg() > 0){
            usePr = true;
        }
        try { // make figure legend
            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup(getLegendDataset(usePr), 9, BaseFont.Times);
            tempparagraph.setMaxWidth((float)(document.getPageWidth()/1.2));
            tempparagraph.setAlignment(Alignment.Justify);
            document.add(tempparagraph, VerticalLayoutHint.CENTER);
        } catch (IOException e) {
            e.printStackTrace();
        }


        return document;
    }

    private void addDetails(Document document){

        ArrayList<String> header = getHeader();
        document.add(new ColumnLayout(1, 5));

        // details table
        try {
            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup(getHeaderString(), 8, BaseFont.Courier);
            Frame headerFrame = new Frame(tempparagraph, document.getPageWidth(), 10f);
            headerFrame.setShape(new Rect());
            headerFrame.setBorder(Color.WHITE, new rst.pdfbox.layout.shape.Stroke(0.5f));
            headerFrame.setPadding(0, 0, 0, 0);
            //headerFrame.setMargin(40, 40, 5, 0);
            document.add(headerFrame, VerticalLayoutHint.CENTER);

            Frame lineFrame = new Frame(new Paragraph(), (float)(document.getPageWidth()), 1f);
            lineFrame.setShape(new Rect());
            lineFrame.setBorder(Color.black, new rst.pdfbox.layout.shape.Stroke(0.5f));
            lineFrame.setPadding(0, 0, 0, 0);
            document.add(lineFrame, VerticalLayoutHint.CENTER);

            for(int i=0;i<totalInCollection; i++){
                if (inUseCollection.getDataset(i).getInUse()){
                    document.add(this.makeRow(inUseCollection.getDataset(i), document.getPageWidth()), VerticalLayoutHint.CENTER);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            document.add(new VerticalSpacer(20));
            Paragraph paragraph = new Paragraph();
            paragraph.addText("Additional Details", 10, PDType1Font.TIMES_ROMAN);
            document.add(paragraph);

            Paragraph tempparagraph = new Paragraph();
            tempparagraph.addMarkup(getAdditionalDetailsString(), 8, BaseFont.Courier);
            Frame headerFrame = new Frame(tempparagraph, document.getPageWidth(), 10f);
            headerFrame.setShape(new Rect());
            headerFrame.setBorder(Color.WHITE, new rst.pdfbox.layout.shape.Stroke(0.5f));
            headerFrame.setPadding(0, 0, 0, 0);
            //headerFrame.setMargin(40, 40, 5, 0);
            document.add(headerFrame, VerticalLayoutHint.CENTER);

            Frame lineFrame = new Frame(new Paragraph(), (float)(document.getPageWidth()), 1f);
            lineFrame.setShape(new Rect());
            lineFrame.setBorder(Color.black, new rst.pdfbox.layout.shape.Stroke(0.5f));
            lineFrame.setPadding(0, 0, 0, 0);
            document.add(lineFrame, VerticalLayoutHint.CENTER);

            for(int i=0;i<totalInCollection; i++){
                if (inUseCollection.getDataset(i).getInUse()){
                    document.add(this.makeAdditionalDetailsRow(inUseCollection.getDataset(i), document.getPageWidth()), VerticalLayoutHint.CENTER);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BufferedImage createGuinierPlotFromDataset(Dataset dataset){
        XYSeriesCollection guinierCollection = new XYSeriesCollection();
        guinierCollection.addSeries(new XYSeries("GUINIER MODEL LINE"));
        // create residual series and line fits
        XYSeries dataInUse = new XYSeries("DataInuse");
        XYSeries tempG = dataset.getGuinierData();
        for(int i=dataset.getIndexOfLowerGuinierFit(); i<dataset.getIndexOfUpperGuinierFit(); i++){
            dataInUse.add(tempG.getDataItem(i));
        }
        double rg = dataset.getGuinierRg();
        double slope = -rg*rg/3.0;
        double intercept = Math.log(dataset.getGuinierIzero());

        guinierCollection.getSeries(0).add(dataInUse.getMinX(), slope*dataInUse.getMinX()+intercept);
        guinierCollection.getSeries(0).add(dataInUse.getMaxX(), slope*dataInUse.getMaxX()+intercept);
        guinierCollection.addSeries(dataInUse);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Guinier fit",                // chart title
                "",                       // domain axis label
                "ln I(q)",                // range axis label
                guinierCollection,               // data
                PlotOrientation.VERTICAL,
                false,                     // include legend
                true,
                false
        );


        final XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("ln [I(q)]");

        Font fnt = new Font("SansSerif", Font.BOLD, 24);
        domainAxis.setLabelFont(fnt);
        rangeAxis.setLabelFont(fnt);
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 20));
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 20));
        domainAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);
        String quote = "q\u00B2 (\u212B \u207B\u00B2)";
        domainAxis.setLabel(quote);

        plot.setDomainAxis(domainAxis);
        plot.setRangeAxis(rangeAxis);
        plot.setBackgroundPaint(null);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);
        renderer1.setBaseShapesFilled(false);
        renderer1.setSeriesLinesVisible(1, false);
        renderer1.setSeriesShapesVisible(0, true);
        renderer1.setSeriesShapesVisible(0, false);
        renderer1.setSeriesPaint(0, Color.red);
        renderer1.setSeriesStroke(0, dataset.getStroke());

        renderer1.setSeriesPaint(1, dataset.getColor());
        renderer1.setSeriesShape(1, new Ellipse2D.Double(-4, -4, 8.0, 8.0));
        renderer1.setSeriesOutlinePaint(1, dataset.getColor());
        renderer1.setSeriesOutlineStroke(1, dataset.getStroke());

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }

    private BufferedImage createGuinierResidualsPlotFromDataset(Dataset dataset){

        // rebuild residuals dataset
        XYSeriesCollection residualsDataset = new XYSeriesCollection();
        residualsDataset.addSeries(new XYSeries("residuals"));
        residualsDataset.addSeries(new XYSeries("line"));

        XYSeries dataInUse = new XYSeries("DataInuse");
        XYSeries tempG = dataset.getGuinierData();
        for(int i=dataset.getIndexOfLowerGuinierFit(); i<dataset.getIndexOfUpperGuinierFit(); i++){
            dataInUse.add(tempG.getDataItem(i));
        }

        int itemCount = dataInUse.getItemCount();

        double rg = dataset.getGuinierRg();
        double slope = -rg*rg/3.0;
        double intercept = Math.log(dataset.getGuinierIzero());

        for (int v=0; v< itemCount; v++) {
            XYDataItem item = dataInUse.getDataItem(v);
            residualsDataset.getSeries(0).add(item.getX(),item.getY().doubleValue()-(slope*item.getX().doubleValue()+intercept));
        }

        residualsDataset.getSeries(1).add(dataInUse.getMinX(), 0);
        residualsDataset.getSeries(1).add(dataInUse.getMaxX(), 0);

        // add to chart

        JFreeChart residualsChart = ChartFactory.createXYLineChart(
                "Residuals",                // chart title
                "",                    // domain axis label
                "residuals",                  // range axis label
                residualsDataset,               // data
                PlotOrientation.VERTICAL,
                false,                     // include legend
                true,
                false
        );

        final XYPlot residuals = residualsChart.getXYPlot();

        final NumberAxis domainAxis = new NumberAxis("");
        final NumberAxis rangeAxis = new NumberAxis("");

        Font fnt = new Font("SansSerif", Font.BOLD, 24);
        domainAxis.setLabelFont(fnt);
        rangeAxis.setLabelFont(fnt);

        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 20));
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 20));

        domainAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setAutoRangeIncludesZero(false);
        domainAxis.setAutoRangeStickyZero(false);

        String quote = "q\u00B2 (\u212B \u207B\u00B2)";
        domainAxis.setLabel(quote);
        residuals.setDomainAxis(domainAxis);
        residuals.setBackgroundPaint(null);

        XYLineAndShapeRenderer renderer2 = (XYLineAndShapeRenderer) residuals.getRenderer();
        renderer2.setBaseShapesVisible(true);
        renderer2.setBaseShapesFilled(false);
        renderer2.setSeriesLinesVisible(0, false);
        renderer2.setSeriesShapesVisible(1, true);
        renderer2.setSeriesShapesVisible(1, false);
        renderer2.setSeriesPaint(1, Color.red);
        renderer2.setSeriesStroke(1, dataset.getStroke());
        renderer2.setSeriesPaint(0, Color.BLACK);
        renderer2.setSeriesShape(0, new Ellipse2D.Double(-4, -4, 8.0, 8.0));


        return residualsChart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }


    private BufferedImage createPrPlotFromDataset(Dataset dataset){

        XYSeriesCollection splineCollection = new XYSeriesCollection();
        splineCollection.addSeries(dataset.getRealSpaceModel().getPrDistribution());
        double ilower = 0;
        double iupper = dataset.getRealSpaceModel().getPrDistribution().getMaxY();

        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "r",                        // domain axis label
                "P(r)",                // range axis label
                splineCollection,                 // data
                PlotOrientation.VERTICAL,
                false,                       // include legend
                false,
                false
        );

        final XYPlot plot = chart.getXYPlot();
        plot.setDomainAxis(getDomainAxis("r, \u212B"));
        plot.setRangeAxis(getRangeAxis("P(r)-distribution", ilower, iupper));

        chart.setTitle(getTitle("D"));

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setBackgroundAlpha(0.0f);

        plot.setDomainCrosshairLockedOnData(true);
        plot.setRangeZeroBaselineVisible(true);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYSplineRenderer splineRend = new XYSplineRenderer();
        splineRend.setBaseShapesVisible(false);
        splineRend.setSeriesStroke(0, new BasicStroke(2.6f));
        splineRend.setSeriesPaint(0, dataset.getColor().darker()); // make color slight darker

        plot.setDataset(0, splineCollection);  //Moore Function
        plot.setRenderer(0, splineRend);       //render as a line

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }


    private void setLegendNote(String text){
        this.legendNote = text;
    }


    private BufferedImage createIZeroRgPlotFromCollection(){

        //
        // previously miniCollection was being plotted
        // this is really the active datasets,
        // in the constructor for collection, miniCollection is derived from dataset (same pointer)
        //

        XYSeriesCollection izerosCollection = new XYSeriesCollection();
        XYSeriesCollection rgsCollection = new XYSeriesCollection();
        XYSeries izeros = new XYSeries("I Zero");
        XYSeries rgs = new XYSeries("Rg");


        double tempUpper;
        double upperLeft = 0;
        double lowerLeft = 10000000;
        double upperRight = 0;
        double lowerRight = 10000000;

        for (int i=0; i<totalInCollection; i++){
            Dataset tempData = inUseCollection.getDataset(i);

            if (tempData.getInUse() && tempData.getGuinierRg() > 0){
                tempUpper = tempData.getGuinierIzero();
                izeros.add(i+1, tempUpper); // follows index of the files selected

                if (tempUpper > upperLeft){
                    upperLeft = tempUpper;
                }

                if (tempUpper < lowerLeft){
                    lowerLeft = tempUpper;
                }

                tempUpper = tempData.getGuinierRg();
                rgs.add(i+1, tempUpper);

                if (tempUpper > upperRight){
                    upperRight = tempUpper;
                }

                if (tempUpper < lowerRight){
                    lowerRight = tempUpper;
                }
            }
        }

        izerosCollection.addSeries(izeros);
        rgsCollection.addSeries(rgs);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                // chart title
                "Number",                        // domain axis label
                "I(0)",                // range axis label
                izerosCollection,           // data
                PlotOrientation.VERTICAL,
                false,                      // include legend
                true,
                false
        );

        chart.setTitle(getTitle("D"));
        chart.setBorderVisible(false);

        XYPlot plot = chart.getXYPlot();
        final NumberAxis domainAxis = new NumberAxis("File Number");
        final NumberAxis rangeAxisLeft = new NumberAxis("Left");
        final NumberAxis rangeAxisRight = new NumberAxis("Right");


        String quote = "Number";
        domainAxis.setLabel(quote);
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        domainAxis.setLabelFont(new Font("Times", Font.BOLD, 28));
        domainAxis.setTickLabelFont(new Font("Times", Font.BOLD, 22));

        quote = "I(0)";

        rangeAxisLeft.setLabel(quote);
        rangeAxisLeft.setLabelFont(new Font("Times", Font.BOLD, 30));
        rangeAxisLeft.setTickLabelFont(new Font("Times", Font.BOLD, 22));
        rangeAxisLeft.setLabelPaint(new Color(255, 153, 51));
        rangeAxisLeft.setAutoRange(false);
        rangeAxisLeft.setRange(lowerLeft-lowerLeft*0.03, upperLeft+0.1*upperLeft);
        rangeAxisLeft.setAutoRangeStickyZero(false);

        String quoteR = "Rg Å";
        rangeAxisRight.setLabel(quoteR);
        rangeAxisRight.setLabelFont(new Font("Times", Font.BOLD, 30));
        rangeAxisRight.setTickLabelFont(new Font("Times", Font.BOLD, 22));
        rangeAxisRight.setLabelPaint(new Color(51, 153, 255));
        rangeAxisRight.setAutoRange(false);
        rangeAxisRight.setRange(lowerRight-lowerRight*0.03, upperRight+0.1*upperRight);
        rangeAxisRight.setAutoRangeStickyZero(false);

        domainAxis.setAutoRangeStickyZero(false);

        plot.setDomainAxis(0, domainAxis);
        plot.setRangeAxis(0, rangeAxisLeft);
        plot.setRangeAxis(1, rangeAxisRight);

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer leftRenderer = (XYLineAndShapeRenderer) plot.getRenderer();
        XYLineAndShapeRenderer rightRenderer = new XYLineAndShapeRenderer();

        plot.setDataset(0, izerosCollection);
        plot.setRenderer(0,leftRenderer);
        plot.setDataset(1,rgsCollection);
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
        plot.setShadowGenerator(null);
        chart.getRenderingHints().put(JFreeChart.KEY_SUPPRESS_SHADOW_GENERATION, Boolean.TRUE);
        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }


    private BufferedImage createqIqPlotFromCollection(){
        int totalSets = inUseCollection.getTotalDatasets();

        XYSeriesCollection plottedDatasets = new XYSeriesCollection();
        double qlower=10, qupper=-1, ilower = 10, iupper = -1;

        for (int i=0; i < totalSets; i++){
            Dataset tempData = inUseCollection.getDataset(i);
            if (tempData.getGuinierRg() > 0 && tempData.getInUse()){
                tempData.scalePlottedQIQData();
                plottedDatasets.addSeries(tempData.getPlottedQIQDataSeries()); // positive only data
            }
        }

        ilower = plottedDatasets.getRangeLowerBound(true);
        iupper = plottedDatasets.getRangeUpperBound(true);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "q",                             // domain axis label
                "",                     // range axis label
                plottedDatasets,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        chart.getXYPlot().setDomainCrosshairVisible(false);
        chart.getXYPlot().setRangeCrosshairVisible(false);

        String xaxisLabel = "q (\u212B\u207B\u00B9)";
        String yaxisLabel = "q\u00D7 I(q)";

        chart.setTitle(getTitle("C"));
        chart.setBorderVisible(false);


        XYPlot plot = chart.getXYPlot();

        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);
        ValueMarker marker = new ValueMarker(0);
        marker.setLabelAnchor(RectangleAnchor.RIGHT);
        marker.setLabelBackgroundColor(Color.white);
        marker.setLabelOffset(new RectangleInsets(0,0,0,-40));

//        marker.setLabel("0");
//        marker.setLabelFont(Constants.BOLD_18);
        marker.setStroke(new BasicStroke(2.0f));
        plot.addRangeMarker(marker);


        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);

        //set dot size for all series
        double offset;
        int counted = 0;

        for (int i=0; i < totalSets; i++){
            Dataset tempData = inUseCollection.getDataset(i);
            if (tempData.getInUse()){
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

        plot.setDomainAxis(getDomainAxis(xaxisLabel));
        plot.setRangeAxis(getRangeAxis(yaxisLabel, ilower, iupper));

//        NumberAxis tempAxis = (NumberAxis) plot.getRangeAxis();
//        tempAxis.setNumberFormatOverride(new DecimalFormat() {
//            public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
//                if (number == 0){
//                    return toAppendTo.append(0);
//                }
//                return toAppendTo.append("1");
//            }
//        });



        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);
        plot.setShadowGenerator(null);
        chart.getRenderingHints().put(JFreeChart.KEY_SUPPRESS_SHADOW_GENERATION, Boolean.TRUE);
        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }

    private BufferedImage createKratklyPlotFromCollection(){

        int totalSets = inUseCollection.getTotalDatasets();

        XYSeriesCollection plottedDatasets = new XYSeriesCollection();
        double qlower=10, qupper=-1, ilower = 10, iupper = -1;

        int rgCount = 0;
        int inUseCount =0;

        for (int i=0; i < totalSets; i++){
            if (inUseCollection.getDataset(i).getInUse()){
                double rg = inUseCollection.getDataset(i).getGuinierRg();
                if (rg > 0){
                    rgCount+=1;
                }
                inUseCount+=1;
            }
        }

        boolean useNormalized = false;
        if (((double)rgCount)/(double)inUseCount > 0.45){
            useNormalized = true;
        }

        if (useNormalized){
            for (int i=0; i < totalSets; i++){
                Dataset tempData = inUseCollection.getDataset(i);
                if (tempData.getGuinierRg() > 0 && tempData.getInUse()){
                    tempData.createNormalizedKratkyReciRgData();
                    plottedDatasets.addSeries(tempData.getNormalizedKratkyReciRgData()); // positive only data
                }
            }
            legendB = "*B.* Dimensionless Kratky plot. Cross-hair marks the Guinier-Kratky point (1.732, 1.1), the main peak position for globular particles.";
        } else {
            for (int i=0; i < totalSets; i++){
                Dataset tempData = inUseCollection.getDataset(i);
                if (tempData.getInUse()){
                    tempData.scalePlottedKratkyData();
                    plottedDatasets.addSeries(tempData.getPlottedKratkyDataSeries()); // positive only data
                }
            }
            legendB = "*B.* Kratky plot. Convergence at high scattering vectors suggests compactness whereas divergence or hyperbolic features away from baseline suggest flexibility in the thermodynamic state.";
        }

        ilower = plottedDatasets.getRangeLowerBound(true);

        if (useNormalized){
            iupper = 0;
            int totalInUse = plottedDatasets.getSeriesCount();
            for (int i=0; i < totalInUse; i++){
                XYSeries tempSeries = plottedDatasets.getSeries(i);
                for (int j=0; j< tempSeries.getItemCount(); j++){
                    XYDataItem item = tempSeries.getDataItem(j);
                    if (item.getYValue() > iupper){
                        iupper = item.getYValue();
                    }

                    if (item.getXValue() > 10){
                        break;
                    }
                }
            }
        } else {
            iupper = plottedDatasets.getRangeUpperBound(true);
        }


        JFreeChart chart = ChartFactory.createXYLineChart(
                "",                     // chart title
                "q",                             // domain axis label
                "",                     // range axis label
                plottedDatasets,                 // data
                PlotOrientation.VERTICAL,
                false,                           // include legend
                true,
                false
        );

        chart.getXYPlot().setDomainCrosshairVisible(false);
        chart.getXYPlot().setRangeCrosshairVisible(false);

        String xaxisLabel, yaxisLabel;
        if (useNormalized){
            xaxisLabel = "q\u2217Rg";
            yaxisLabel = "I(q)/I(0)\u2217(q\u2217Rg)\u00B2";
        } else {

            xaxisLabel = "q, \u212B \u207B\u00B9";
            yaxisLabel = "q\u00B2 \u00D7 I(q)";
        }

        chart.setTitle(getTitle("B"));
        chart.setBorderVisible(false);


        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);

        //set dot size for all series
        double offset;
        int counted = 0;

        if (useNormalized){
            for (int i=0; i < totalSets; i++){
                Dataset tempData = inUseCollection.getDataset(i);
                if (tempData.getGuinierRg() > 0 && tempData.getInUse()){
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
            plot.addDomainMarker(new ValueMarker(1.7320508));
            plot.addRangeMarker(new ValueMarker(1.1));

        } else {
            for (int i=0; i < totalSets; i++){
                Dataset tempData = inUseCollection.getDataset(i);
                if (tempData.getInUse()){
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
        }

        plot.setDomainAxis(getDomainAxis(xaxisLabel));
        plot.setRangeAxis(getRangeAxis(yaxisLabel, ilower, iupper));

        ValueMarker marker = new ValueMarker(0);
        marker.setLabelAnchor(RectangleAnchor.RIGHT);
        marker.setLabelBackgroundColor(Color.white);
        marker.setLabelOffset(new RectangleInsets(0,0,0,0));
//        marker.setLabel("0");
//        marker.setLabelFont(Constants.BOLD_18);
        marker.setStroke(new BasicStroke(2.0f));
        plot.addRangeMarker(marker);

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }


    private BufferedImage createLog10ChartFromCollection() {

        int totalSets = inUseCollection.getTotalDatasets();
        //plottedDatasets = new XYSeriesCollection();  // spinners will always modify the plottedDataset series
        XYSeriesCollection plottedDatasets = new XYSeriesCollection();
        double qlower=10, qupper=-1, ilower = Double.POSITIVE_INFINITY, iupper = Double.NEGATIVE_INFINITY;

        for (int i=0; i < totalSets; i++){
            if (inUseCollection.getDataset(i).getInUse()){
                plottedDatasets.addSeries(inUseCollection.getDataset(i).getData()); // positive only data

                Dataset tempData = inUseCollection.getDataset(i);

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
                "A",                     // chart title
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
        chart.setTitle(getTitle("A"));

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundAlpha(0.0f);
        plot.setDomainCrosshairLockedOnData(false);
        plot.setOutlineVisible(false);

        //make crosshair visible
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer renderer1 = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer1.setBaseShapesVisible(true);

        //set dot size for all series
        double offset;
        int counted = 0;

        for (int i=0; i < totalSets; i++){

            if (inUseCollection.getDataset(i).getInUse()){
                Dataset tempData = inUseCollection.getDataset(i);

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

        plot.setDomainAxis(getDomainAxis("q (\u212B\u207B\u00B9)"));
        plot.setRangeAxis(getRangeAxis("log10[I(q)]", ilower, iupper));

        plot.configureDomainAxes();
        plot.configureRangeAxes();
        plot.setDomainZeroBaselineVisible(false);

        return chart.createBufferedImage((int)imageWidth*3,(int)imageHeight*3);
    }





    private static NumberAxis getRangeAxis(String quote, double ilower, double iupper){
        final NumberAxis rangeAxis = new NumberAxis(quote);
        rangeAxis.setLabel(quote);
        rangeAxis.setAutoRange(true);
        rangeAxis.setLabelFont(Constants.FONT_BOLD_28);
        rangeAxis.setTickLabelFont(Constants.FONT_BOLD_28);
        rangeAxis.setTickLabelsVisible(false);
        rangeAxis.setRange(ilower-ilower*0.03, iupper+Math.abs(0.1*iupper));
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
        domainAxis.setAutoRangeStickyZero(false);
        domainAxis.setAxisLineStroke(new BasicStroke(3.0f));
        domainAxis.setTickMarkStroke(new BasicStroke(3.0f));

        return domainAxis;
    }

    private ArrayList<String > getHeader(){
        return new ArrayList<>(Arrays.asList(
                "Rg-Guinier",
                "error",
                "Rg-Real Space",
                "error",
                "I(zero)-Guinier",
                "error",
                "I(zero)-Real Space",
                "error",
                "Porod Volume",
                "Vc",
                "qmin - P(r)",
                "qmax - P(r)",
                "Shannon Points",
                "bin width",
                "filename"
        ));
    }

    private String getHeaderString(){
        // String temp = " Rg-Reci error  Rg-Real error  I[zero]-Reci error  I[zero]-Real error    Vp      dmax     filename ";

        String formatted = String.format(" %s  %s %s   %s %s  %s %s   %s %s %s %s",
                centerText("Rg-G", 7, ' '),      // xxxx.xx
                centerText("error", 5, ' '), // 12.22
                centerText("Rg-Real", 7, ' '),         // xxxx.xx
                centerText("error", 5, ' '),    // 12.22
                centerText("I[zero]-G", 9, ' '),   // 1.XXXE-04
                centerText("error", 7, ' '), // 1.XE-04
                centerText("I[zero]-R", 9, ' '), // 12.22
                centerText("error", 7, ' '), // 12.22
                centerText("Vp", 9, ' '), // 12.22
                centerText("dmax", 6, ' '), // 12.22
                centerText("filename", 9, ' ') // 12.22
        );

        return formatted;
    }

    private String getAdditionalDetailsString(){
        String formatted = String.format(" %s   %s  %s %s  %s  %s  %s %s  %s %s",
                centerText("PE", 7, ' '),
                centerText("error", 5, ' '),
                centerText("Vc-Reci", 9, ' '),
                centerText("Vc-Real", 9, ' '),
                centerText("dmax", 7, ' '),
                centerText("r<ave>", 6, ' '),
                centerText("qmin-Real", 9, ' '),
                centerText("qmax-Real", 9, ' '),
                centerText("SCORE", 6, ' '),
 //               centerText("Sk2", 5, ' '), // 12.22
                centerText("filename", 9, ' ')
        );

        return formatted;
    }

    private Frame makeAdditionalDetailsRow(Dataset dataset, float width)throws IOException {
        String pe = "";
        String peerror = "";
        if (dataset.getPorodVolume() > 0) {
            pe = String.format("%1.2f", (double)dataset.getPorodExponent());
            peerror = String.format("%1.2f", (double)dataset.getPorodExponentError());
        } else {
            pe = "nd";
            peerror = "nd";
        }

        String vc = (dataset.getVC() > 0) ? String.format("%1.3E", dataset.getVC()) : "nd";
        String vcreal = (dataset.getVCReal() > 0) ? String.format("%1.3E", dataset.getVCReal()) : "nd";
        String dmax = (dataset.getRealIzero() > 0) ? String.format("%6.1f", dataset.getRealSpaceModel().getDmax()) : "nd";
        String raverage = (dataset.getRealIzero() > 0) ? String.format("%.1f", dataset.getRealSpaceModel().getRaverage()) : "nd";
        String qmax = (dataset.getRealIzero() > 0) ? String.format("%.6f", dataset.getRealSpaceModel().getQmax()) : "nd";

        String qmin = (dataset.getRealIzero() > 0) ? String.format("%.6f", dataset.getRealSpaceModel().getfittedqIq().getMinX()) : "nd";
        String chi = (dataset.getRealIzero() > 0) ? String.format("%5.2f", dataset.getRealSpaceModel().getTotalScore()) : "nd";
//        String chi = (dataset.getRealIzero() > 0) ? String.format("%5.2f", dataset.getRealSpaceModel().getChi2()) : "nd";
//        String sk2 = (dataset.getRealIzero() > 0) ? String.format("%5.2f", dataset.getRealSpaceModel().getKurt_l1_sum()) : "nd";

        Color inUse = dataset.getColor();
        String hex = String.format("#%02x%02x%02x", inUse.getRed(), inUse.getGreen(), inUse.getBlue());

        Paragraph tempparagraph = new Paragraph();

        int lengthOfFilename = dataset.getFileName().length();
        String escaped = escape(dataset.getFileName());
        if (lengthOfFilename > 20){
            escaped = escape(dataset.getFileName().substring(0,20));
        }



        String formatted = String.format("{color:%s} %s +-%s  %s %s  %s  %s  %s %s  %s %s",
                hex,
                centerText(pe, 7, ' '),        // x.xx
                centerText(peerror, 5, ' '),   // 1.22
                centerText(vc, 9, ' '),        // 1.XXXE-04
                centerText(vcreal, 9, ' '),    // 12.22
                centerText(dmax, 7, ' '),      // 1000.5
                centerText(raverage, 6, ' '),  // 101.1
                centerText(qmin, 9, ' '),      // 12.22
                centerText(qmax, 9, ' '),      // 12.22
                centerText(chi, 6, ' '),       // 12.22
                //centerText(sk2, 5, ' '),       // 12.22
                escaped
        );


        tempparagraph.addMarkup(formatted, 8, BaseFont.Courier);

        //StyledText styled = new StyledText(formatted, 8.0f, pdfont, dataset.getColor());

        Frame frame = new Frame(tempparagraph, width, 10f);
        frame.setShape(new Rect());
        //frame.setBorder(dataset.getColor(), new Stroke(0.5f));
        frame.setBorder(Color.WHITE, new rst.pdfbox.layout.shape.Stroke(0.5f));
        frame.setPadding(0, 0, 0, 0);
        //frame.setMargin(40, 40, 5, 0);

        return frame;
    }

    private Frame makeRow(Dataset dataset, float width) throws IOException {

        // 100.00 => 7.2f
        if (dataset.getRealIzero() > 0 ){
            dataset.getRealSpaceModel().estimateErrors();
        }

        String index = String.format("%d", dataset.getId());

        String guinierRg = (dataset.getGuinierRg() > 0) ? String.format("%.2f", dataset.getGuinierRg()) : "nd";
        String guinierRgerror = (dataset.getGuinierRg() > 0) ? String.format("%.2f", dataset.getGuinierRgerror()): "nd";

        String realRg = (dataset.getRealRg() > 0) ? String.format("%.2f", dataset.getRealRg()) : "nd";
        String realRgerror = (dataset.getRealRg() > 0) ? String.format("%.2f", dataset.getRealRgSigma()) : "nd";

        String guinierIzero = (dataset.getGuinierIzero() > 0) ? String.format("%1.3E", dataset.getGuinierIzero()) : "nd";
        String guinierIzeroSigma = (dataset.getGuinierIzero() > 0) ? String.format("%1.1E", dataset.getGuinierIzeroError()) : "nd";

        String realIzero = (dataset.getRealIzero() > 0) ? String.format("%1.3E",dataset.getRealIzero()) : "nd";
        String realIzeroSigma = (dataset.getRealIzero() > 0) ?  String.format("%1.1E", dataset.getRealIzeroSigma()) : "nd";

        String vp = "";
        if (dataset.getPorodVolumeReal() > 0 ){
            vp = String.format("%1.3E", (double)dataset.getPorodVolumeReal());
        } else if (dataset.getPorodVolume() > 0) {
            vp = String.format("%1.3E", (double)dataset.getPorodVolume());
        } else {
            vp = "nd";
        }

        //String vc = (dataset.getVC() > 0) ? String.format("%1.4E", dataset.getVC()) : " nd ";
        //String qmax = (dataset.getRealIzero() > 0) ? String.format("%.6f", dataset.getRealSpaceModel().getQmax()) : "  nd  ";
        String dmax = (dataset.getRealIzero() > 0) ? String.format("%6.1f", dataset.getRealSpaceModel().getDmax()) : "  nd  ";

        Color inUse = dataset.getColor();
        String hex = String.format("#%02x%02x%02x", inUse.getRed(), inUse.getGreen(), inUse.getBlue());
        int lengthOfFilename = dataset.getFileName().length();
        String escaped = escape(dataset.getFileName());
        if (lengthOfFilename > 20){
            escaped = escape(dataset.getFileName().substring(0,20));
        }
        Paragraph tempparagraph = new Paragraph();

        String formatted = String.format("{color:%s}%s+-%s %s+-%s %s+-%s %s+-%s %s %s %s",
                hex,
                centerText(guinierRg, 7, ' '),      // xxxx.xx
                centerText(guinierRgerror, 6, ' '), // 12.22
                centerText(realRg, 7, ' '),         // xxxx.xx
                centerText(realRgerror, 6, ' '),    // 12.22
                centerText(guinierIzero, 9, ' '),   // 1.XXXE-04
                centerText(guinierIzeroSigma, 7, ' '), // 1.XE-04
                centerText(realIzero, 9, ' '), // 12.22
                centerText(realIzeroSigma, 7, ' '), // 12.22
                centerText(vp, 9, ' '), // 12.22
                centerText(dmax, 6, ' '), // 12.22
                escaped
        );

        tempparagraph.addMarkup(formatted, 8, BaseFont.Courier);

        Frame frame = new Frame(tempparagraph, width, 10f);
        frame.setShape(new Rect());
        frame.setBorder(Color.white, new Stroke(0.5f));
        frame.setPadding(0, 0, 0, 0);
        //frame.setMargin(40, 40, 5, 0);

        return frame;
    }

    private String escape(String text){
        String temp = text.replaceAll("\\n", "");
        temp = temp.replaceAll("\\r", "");
        return temp.replace("_", "\\_");
    }


    private String getDataSummaryTopHeader(){
        return String.format("%s %s %s %s", rightJustifyText(" ",16,' '), centerText("Reciprocal",20, ' '), centerText("Real Space",20, ' '), centerText("units",10, ' ') );
    }


    /**
     * Create Arraylist of elements to add to table
     * each element of list is a row
     *
     * @param dataset
     * @return
     */
    private ArrayList<String> getSummaryTableRowsFromDataset(Dataset dataset){
        // 16 wide - mimic 4 column table
        // format = "%16s "
        int columnWidthLabels=16;
        int columnWidthData=20;
        // if no Guinier region
        // Izero and Rg = 0;
        ArrayList<String> rows = new ArrayList<>();

        String qminGuinier=centerText("-",columnWidthData,' ');
        String qmaxGuinier=centerText("-",columnWidthData,' ');
        String qminReal=centerText("-",columnWidthData,' ');
        String qmaxReal=centerText("-",columnWidthData,' ');
        String guinierPoints=centerText("-",columnWidthData,' ');
        String realPoints=centerText("-",columnWidthData,' ');
        String reciRg=centerText("-",columnWidthData,' ');
        String realRg=centerText("-",columnWidthData,' ');
        String reciIzero=centerText("-",columnWidthData,' ');
        String realIzero=centerText("-",columnWidthData,' ');
        String reciVolume=centerText("-",columnWidthData,' ');
        String realVolume=centerText("-",columnWidthData,' ');

        String reciVc=centerText("-",columnWidthData,' ');
        String realVc=centerText("-",columnWidthData,' ');
        String porodExponent=centerText("-",columnWidthData,' ');
        String dmax=centerText("-",columnWidthData,' ');
        String binwidth=centerText("-",columnWidthData,' ');
        String shannon=centerText("-",columnWidthData,' ');
        String redundancy=centerText("-", columnWidthData, ' ');
        String r2=centerText("-", columnWidthData, ' ');
        String chi2_free=centerText("-", columnWidthData, ' ');
        String method=centerText("-", columnWidthData, ' ');
        String background=centerText("-", columnWidthData, ' ');


        if (dataset.getGuinierRg() > 0){

            qminGuinier = String.format("%.8f", Math.sqrt(dataset.getGuinierData().getX(dataset.getIndexOfLowerGuinierFit()).doubleValue()));
            qmaxGuinier = String.format("%.8f", Math.sqrt(dataset.getGuinierData().getX(dataset.getIndexOfUpperGuinierFit()).doubleValue()));

            guinierPoints = Integer.toString(dataset.getIndexOfUpperGuinierFit() - dataset.getIndexOfLowerGuinierFit());

            reciRg = String.format("%.2f +- %.2f",dataset.getGuinierRg(), dataset.getGuinierRgerror());
            // (%1.1E) is 9 characters
            // %1.1E is 7 characters
            reciIzero = String.format("%1.2E +- %1.1E",dataset.getGuinierIzero(), dataset.getGuinierIzeroError());
            r2 = String.format("%.2f", dataset.getGuinierCorrelationCoefficient());
            if (dataset.getPorodVolume() > 0){
                reciVolume = String.format("%d",(int)dataset.getPorodVolume());
                porodExponent = String.format("%.2f +- %.2f", dataset.getPorodExponent(), dataset.getPorodExponentError());
            }

            if (dataset.getVC() > 0){
                reciVc = String.format("%.2f",dataset.getVC());
            }
        }

        if (dataset.getRealIzero() > 0 ) {
            dataset.getRealSpaceModel().estimateErrors();
            qminReal = String.format("%.8f", dataset.getRealSpaceModel().getfittedqIq().getMinX());
            qmaxReal = String.format("%.8f", dataset.getRealSpaceModel().getQmax());
            realPoints = Integer.toString(dataset.getRealSpaceModel().getfittedqIq().getItemCount());

            realRg = String.format("%.2f +- %.2f",dataset.getRealRg(), dataset.getRealRgSigma());
            realIzero = String.format("%1.2E +- %1.1E",dataset.getRealIzero(), dataset.getRealIzeroSigma());

            chi2_free = String.format("%.2f", dataset.getRealSpaceModel().getTotalScore());
            if (dataset.getPorodVolumeReal() > 0){
                realVolume = String.format("%d",(int)dataset.getPorodVolumeReal());
            }

            if (dataset.getVCReal() > 0){
                realVc = String.format("%.2f", dataset.getVCReal());
            }

            dmax = String.format("%.1f", dataset.getDmax());
            binwidth = String.format("%.2f", Math.PI/dataset.getRealSpaceModel().getQmax());
            double tempShannon = dataset.getRealSpaceModel().getQmax()*dataset.getDmax()/Math.PI;
            shannon = String.format("%d", (int)Math.ceil(tempShannon));
            redundancy = String.format("%d",(int)Math.ceil(dataset.getRealSpaceModel().getfittedqIq().getItemCount()/tempShannon));
            method = String.format("%s", dataset.getRealSpaceModel().getIndirectFTModel().getModelUsed());

            background=String.format("%s", (dataset.getRealSpaceModel().getIndirectFTModel().includeBackground) ? "Yes" : "No");
        }

        rows.add(String.format("%s %s %s %s", rightJustifyText("q-min", columnWidthLabels, ' '), centerText(qminGuinier,columnWidthData, ' '), centerText(qminReal,columnWidthData, ' '), "A{^}-1{^}")); // qmin
        rows.add(String.format("%s %s %s %s", rightJustifyText("q-max", columnWidthLabels, ' '), centerText(qmaxGuinier,columnWidthData, ' '), centerText(qmaxReal,columnWidthData, ' '), "A{^}-1{^}")); // qmax
        rows.add(String.format("%s %s %s", rightJustifyText("points(min:max)", columnWidthLabels, ' '), centerText(guinierPoints,columnWidthData, ' '), centerText(realPoints,columnWidthData, ' '))); // npoints

        // 1234567890123456 78910
        //          ( 1.11)
        rows.add(String.format("%s %s %s %s", rightJustifyText("Rg", columnWidthLabels, ' '), centerText(reciRg,columnWidthData, ' '), centerText(realRg,columnWidthData, ' '), "A")); // rg
        rows.add(String.format("%s %s %s", rightJustifyText("I[zero]", columnWidthLabels, ' '), centerText(reciIzero,columnWidthData, ' '), centerText(realIzero,columnWidthData, ' '))); // izero

        rows.add(String.format("%s %s %s %s", rightJustifyText("Volume", columnWidthLabels, ' '), centerText(reciVolume,columnWidthData, ' '), centerText(realVolume,columnWidthData, ' '), "A{^}3{^}")); // volume
        rows.add(String.format("%s %s %s %s", rightJustifyText("Vc", columnWidthLabels, ' '), centerText(reciVc,columnWidthData, ' '), centerText(realVc,columnWidthData, ' '), "A{^}2{^}")); // Vc

        // score
        rows.add(String.format("%s %s %s", rightJustifyText(" ",16,' '), centerText("R^2",columnWidthData, ' '), centerText("Total Score",columnWidthData, ' '))); // header
        rows.add(String.format("%s %s %s", rightJustifyText("Score",16,' '), centerText(r2,columnWidthData, ' '), centerText(chi2_free, columnWidthData, ' '))); // fitting scores
        rows.add(String.format("%s %s %s", rightJustifyText("Method",16,' '), centerText(" ",columnWidthData, ' '), centerText(method, columnWidthData, ' '))); // fitting scores
        rows.add(String.format("%s %s %s", rightJustifyText("Background",16,' '), centerText(" ",columnWidthData, ' '), centerText(background, columnWidthData, ' '))); // background fitted?

        rows.add(String.format("%s %s ", rightJustifyText("Porod Exponent", columnWidthLabels, ' '), centerText(porodExponent,columnWidthData, ' '))); // porod exponent
        rows.add(String.format("%s %s %s %s", rightJustifyText("d-max", columnWidthLabels, ' '), centerText(dmax,columnWidthData, ' '), rightJustifyText(" ",20,' '), "A")); // dmax
        rows.add(String.format("%s %s %s %s", rightJustifyText("bin-width", columnWidthLabels, ' '), centerText(binwidth,columnWidthData, ' '), rightJustifyText(" ",20,' '), "A")); // binwidth
        rows.add(String.format("%s %s ", rightJustifyText("Ns", columnWidthLabels, ' '), centerText(shannon,columnWidthData, ' '))); // shannon
        rows.add(String.format("%s %s ", rightJustifyText("redundancy", columnWidthLabels, ' '), centerText(redundancy,columnWidthData, ' '))); // redundancy

        return rows;
    }

    /**
     *
     * @param s string to center
     * @param size width of new string with centred text
     * @param pad
     * @return
     */
    public static String centerText(String s, int size, char pad) {
        if (s == null || size <= s.length())
            return s;

        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < (size - s.length()) / 2; i++) {
            sb.append(pad);
        }
        sb.append(s);
        while (sb.length() < size) {
            sb.append(pad);
        }
        return sb.toString();
    }

    /**
     *
     * @param s string to center
     * @param size width of new string with centred text
     * @param pad
     * @return
     */
    public static String rightJustifyText(String s, int size, char pad) {
        if (s == null)
            System.out.println("NULL detected " + s.length());
        if (s == null || size <= s.length())
            return s;

        StringBuilder sb = new StringBuilder(size);

        for (int i = 0; i < (size - s.length()); i++) {
            sb.append(pad);
        }

        sb.append(s);
        return sb.toString();
    }

    private TextTitle getTitle(String text){
        TextTitle temp = new TextTitle();
        temp.setText(text);
        temp.setFont(new java.awt.Font("Times",  Font.BOLD, 42));
        temp.setPaint(Color.BLACK);
        temp.setTextAlignment(HorizontalAlignment.LEFT);
        temp.setHorizontalAlignment(HorizontalAlignment.LEFT);
        //temp.setMargin(10, 10, 4, 0);
        return temp;
    }

    private String getLegendDataset(Boolean includePr){

        String startLegend = "*Figure | *" + escape(legendNote);

        if (legendTitle.length() > 1){
            startLegend = "* " + legendTitle + " | *" + escape(legendNote);
        }

        String legendA = "*A.* Log{_}10{_} SAXS intensity versus scattering vector, _q_. Plotted range represents the positive only data within the specified _q_-range.";
        String legendC = "*C.* Total scattered intensity plot. Plot readily demonstrates negative intensities at high-_q_. Over-subtraction of background leads to significant negative intensities. Likewise, under-subtraction can be observed as an elevated baseline at high-_q_. Horizontal line is drawn at y=0. ";
        String legend = startLegend + " " + legendA + " " + legendB + " " + legendC;
        if (includePr){
            legend += " " + "*D.* Pair-distance, P(r), distribution function. Maximum dimension, _d{_}max{_}_, is the largest non-negative value that supports a smooth distribution function.";
        } else if (includeIzeroRgPlot){
            legend += " " + "*D.* Double-Y plot of Guinier I(0) and R{_}g{_} versus sample. Sample order and datasets are colored are indicated by the following table. ";
        }

        return legend;
    }
}
