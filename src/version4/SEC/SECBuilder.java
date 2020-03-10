package version4.SEC;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.math3.stat.StatUtils;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import version4.*;
import version4.Collection;
import version4.sasCIF.Hidable;
import version4.sasCIF.HidableSerializer;
import version4.sasCIF.SasObject;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SECBuilder extends SwingWorker<Void, Integer> {

    private SecFormat secFormat;
    private SasObject sasObject;
    private String secfilename;
    private String parentPath;
    private Collection collection;
    private boolean isValid = false;
    private boolean updateOnly = false;
    private boolean backgroundPresent = false;
    private String notice;
    private ArrayList<Number> qvalues;
    private ArrayList<Signals> signals;
    private JLabel status;
    private JProgressBar progressBar;
    private int totalSelected;
    private int rgLineIndex, rgErrorLineIndex, izeroLineIndex, izeroErrorLineIndex;
    private int excludePoints = 13;

    private double noSignal;
    private double threshold, background_spread=0.2, min_spread = 10;

    private Number minQvalueInCommon = 0;
    private Number maxQvalueInCommon = 0;

    private XYSeries buffer;
    private XYSeries bufferError;
    private ArrayList<XYSeries> subtractedSets;
    private ArrayList<XYSeries> subtractedSetsErrors;
    private ArrayList keptBufferIndices;

    TreeSet<Integer> bufferRejects;
    TreeSet<Integer> keptBuffers;

    // index, signal, Rg, I(0), integral qIq
    private String outputname, workingDirectory;

    /**
     *
     * @param collection
     * @param status
     * @param bar
     * @param newSecfilename
     * @param workingDirectoryName
     * @param threshold could value should be > 1.003
     * @throws IOException
     */
    public SECBuilder(Collection collection, JLabel status, JProgressBar bar, String newSecfilename, String workingDirectoryName, double threshold) throws IOException {

        this.collection = collection;
        this.threshold = threshold;
        this.status = status;
        this.progressBar = bar;

        if (!validateQValues()){
            throw new IOException("Improper dataset for SEC SAXS :: " + notice);
        }

        int total = collection.getTotalDatasets();
        totalSelected = 0;
        for(int i=0; i < total; i++){
            if (collection.getDataset(i).getInUse()){
                totalSelected++;
            }
        }

        workingDirectory = workingDirectoryName;

        if (newSecfilename.length() == 0){ // try to make a filename from input collection

            String name = collection.getDataset(0).getFileName();
            String[] parts = name.split("[-_]+");
            String onlyDigits = "\\d+";

            if (parts[parts.length-1].matches(onlyDigits) && parts[0].matches(onlyDigits)){ // if first and last match digits
                // decide if the front increments or last?
                if (parts[parts.length-1].length() > parts[0].length()){ // assume last is the digit to follow
                    //outputname = name.split(parts[parts.length - 1])[0];
                    int stopat = name.length() - parts[parts.length - 1].length() - 1;
                    outputname = name.substring(0, stopat);
                } else { // 000001_filename_2.dat
                    outputname = name.substring(parts[0].length() + 1);
                }

            } else if ( parts[parts.length-1].matches(onlyDigits)) { // if only last matches digits
                // outputname = name.split(parts[parts.length - 1])[0];
                // 0123456789
                // abcdefghij
                int stopat = name.length() - parts[parts.length - 1].length() - 1;
                outputname = name.substring(0, stopat);

            } else if (parts[0].matches(onlyDigits) ) { // 00001_filename.dat if only last matches digits
                outputname = name.substring(parts[0].length() + 1);
            }
        } else {
            outputname = newSecfilename;
        }

        /*
         * populate and write intial file ::
         * estimate buffer frames
         * calculate signal plot
         * calculate subtracted curves
         * calculate izero/rg based on threshold
         * make SEC FORMATTTED FILE
         */
    }

    /**
     * recalculate SECFile from new
     *
     * @param oldsecfile
     */
    public SECBuilder(SECFile oldsecfile, SortedSet<Integer> bufferIndices, JLabel status, JProgressBar bar, String workingDirectoryName, double threshold) throws IOException{
        // build collection
        collection = new Collection("rebuild");
        backgroundPresent = true;

        int totalFrames = oldsecfile.getTotalFrames();
        XYSeries iofq = new XYSeries("");
        XYSeries eofq = new XYSeries("");

        int totalInFrame = oldsecfile.getQvalues().size();
        ArrayList<Double> oldqvalues = oldsecfile.getQvalues();
        ArrayList<Double> frame = oldsecfile.getUnSubtractedFrameAt(0);
        ArrayList<Double> error = oldsecfile.getUnSubtractedErrorFrameAt(0);
        qvalues = new ArrayList<>();

        for(int q=0; q<totalInFrame; q++){ // create dataset
            this.qvalues.add(oldqvalues.get(q));
        }

        minQvalueInCommon = this.qvalues.get(0);
        maxQvalueInCommon = this.qvalues.get(qvalues.size()-1);

        for(int i=0; i<totalInFrame; i++){
            // create datase
            Double qvalue = qvalues.get(i).doubleValue();
            iofq.add(qvalue, frame.get(i));
            eofq.add(qvalue, error.get(i));
        }

        collection.createDataset(iofq, eofq);

        for(int i=1; i<totalFrames; i++){
            frame = oldsecfile.getUnSubtractedFrameAt(i);
            error = oldsecfile.getUnSubtractedErrorFrameAt(i);
            // create dataset
            for(int q=0; q<totalInFrame; q++){
                // create dataset
                iofq.updateByIndex(q, frame.get(q));
                eofq.updateByIndex(q, error.get(q));
            }
            collection.createDataset(iofq, eofq);
        }

        this.threshold = threshold;
        this.status = status;
        this.progressBar = bar;

        int total = collection.getTotalDatasets();
        totalSelected = 0;
        for(int i=0; i < total; i++){
            if (collection.getDataset(i).getInUse()){
                totalSelected++;
            }
        }

        workingDirectory = workingDirectoryName;
        outputname = oldsecfile.getFilebase();
        setBackgroundFrames(bufferIndices);
        System.out.println("Finished");
    }

    /**
     * recalculate SEC File updating only Rg and Izero as threshold or excluded points were adjusted
     *
     * @param oldsecfile
     * @param status
     * @param bar
     * @param workingDirectoryName
     * @param threshold
     * @throws IOException
     */
    public SECBuilder(SECFile oldsecfile, JLabel status, JProgressBar bar, String workingDirectoryName, double threshold, int excludePoints) throws IOException{
        // build collection
        secfilename = oldsecfile.getAbsolutePath();
        parentPath = oldsecfile.getParentPath();

        sasObject = new SasObject(oldsecfile.getSasObject());
        sasObject.getSecFormat().setThreshold(threshold);

        this.excludePoints = excludePoints;
        collection = new Collection("rebuild");
        backgroundPresent = true; // not relevant, using background subtracted frames
        updateOnly = true;

        /*
         * need the lines of the things to update so make secFormat object
         */
        int totalFrames = oldsecfile.getTotalFrames();
        XYSeries signalSeries = oldsecfile.getSignalSeries();
        if (totalFrames != signalSeries.getItemCount()){
                // throw exceptioon
        }
        /*
         * format of file is :
         * JSON string describing data
         *  0 LINE 0 JSON STRING -> needs updating with new threshold
         *  1 # REMARK
         *  2 # REMARK
         *  3 # REMARK
         *  4 Frame indices
         *  5 SIGNAL
         *  6 integrated qIq
         *  7 Rg    -> needs updating with new values
         *  8 Izero -> needs updating with new values
         *  9 buffer
         * 10 qvalues
         * 11 unsubtracted
         * 12 .
         * 13 .
         *  . unsubtracted errors
         *  + subtracted
         *  + subtracted errors
         *  +
         *  +
         */

        XYSeries iofq = new XYSeries("");
        XYSeries eofq = new XYSeries("");

        int totalInFrame = oldsecfile.getQvalues().size();
        ArrayList<Double> oldqvalues = oldsecfile.getQvalues();
        ArrayList<Double> frame, error;

        qvalues = new ArrayList<>();

        for(int q=0; q<totalInFrame; q++){ // create dataset
            this.qvalues.add(oldqvalues.get(q));
        }

        minQvalueInCommon = this.qvalues.get(0);
        maxQvalueInCommon = this.qvalues.get(qvalues.size()-1);

        for(int i=0; i<totalInFrame; i++){
            // create dataset
            double qvalue = qvalues.get(i).doubleValue();
            iofq.add(qvalue, 0.0d);
            eofq.add(qvalue, 0.0d);
        }

        for(int i=0; i<totalFrames; i++){
            if (signalSeries.getY(i).doubleValue() >= threshold){
                frame = oldsecfile.getSubtractedFrameAt(i);
                error = oldsecfile.getSubtractedErrorAtFrame(i);

                for(int q=0; q<totalInFrame; q++){
                    // create dataset
                    iofq.updateByIndex(q, frame.get(q));
                    eofq.updateByIndex(q, error.get(q));
                }
                collection.createDataset(iofq, eofq, i);
            }
        }

        this.threshold = threshold;
        this.status = status;
        this.progressBar = bar;

        totalSelected = sasObject.getSecFormat().getTotal_frames();

        workingDirectory = workingDirectoryName;
        outputname = oldsecfile.getFilebase();
    }



    @Override
    protected Void doInBackground() throws Exception {

        status.setText("Estimating background frames, please wait");
        progressBar.setVisible(true);
        if (!backgroundPresent){
            /*
             * estimating background frames also sets noSignal limit
             */
            this.estimateBackgroundFrames();
        }

        /*
         * calculate ::
         * 1. signal plot
         * 2. subtracted cureve
         * 3. perform auto-Rg on subtracted curve
         *
         */
        signals = new ArrayList<>();
        //
        if (updateOnly){
            this.updateRgIzero();
        } else {
            this.makeSamples();
            // write file
            this.writeToSECFile();
        }

        return null;
    }


    /**
     * starting with first dataset, check that each q-value is found in all remaining.
     * create a list of the q-values that are in common
     * If common q-values is less than 60% return false and throw exception
     *
     * @return
     */
    private boolean validateQValues(){
        qvalues = new ArrayList<>();

        XYSeries baseSet = collection.getDataset(0).getAllData();
        int total = baseSet.getItemCount();

        for(int i=0; i<total; i++){
            XYDataItem baseItem = baseSet.getDataItem(i);
            boolean good = true;
            for(int d=1; d<collection.getTotalDatasets(); d++){
                Dataset testset = collection.getDataset(d);
                if (testset.getInUse() && testset.getAllData().indexOf(baseItem.getX()) < 0){
                        good = false;
                        break;
                }
            }

            // if I make it all the way, add it to q-values
            if (good){
                qvalues.add(baseItem.getX());
            }
        }

        if (qvalues.size()/(double)total < 0.6){
            notice = " too few values in common ";
            return false;
        }

        minQvalueInCommon = qvalues.get(0);
        maxQvalueInCommon = qvalues.get(qvalues.size()-1);

        return true;
    }


    private void writeToSECFile(){
        /*
         * extract JSON string from collection
         */
        int totalDataSetsInCollection = collection.getTotalDatasets();
        SasObject sasObject = null ;
//        secFormat = new SecFormat();
//        secFormat.setThreshold(threshold);
//        secFormat.setTotal_frames(totalDataSetsInCollection);
//        secFormat.setTotal_momentum_transfer_vectors(qvalues.size());

        for(int i=0; i<totalDataSetsInCollection; i++){
            Dataset data = collection.getDataset(i);
            if (data.getSasObject() instanceof Object){
                sasObject = data.getSasObject();
                break;
            }
        }

        if (sasObject == null){
            sasObject = new SasObject();
        }

        progressBar.setMaximum(totalDataSetsInCollection);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);

        FileWriter fw = null;

        try {
            outputname = workingDirectory +"/" +outputname+".sec";
            fw = new FileWriter( outputname);
            BufferedWriter out = new BufferedWriter(fw);

            // set initial capacity of of each line, however, capacity will be increased if exceeded
            StringBuilder fLine = new StringBuilder(totalDataSetsInCollection*3); // total number of frames, indexes from one to max
            StringBuilder signalLine = new StringBuilder(totalDataSetsInCollection*11);
            StringBuilder izeroLine = new StringBuilder(totalDataSetsInCollection*11);

            StringBuilder rgLine = new StringBuilder(totalDataSetsInCollection*9);

            StringBuilder rgErrorLine = new StringBuilder(totalDataSetsInCollection*9);
            StringBuilder izeroErrorLine = new StringBuilder(totalDataSetsInCollection*9);

            StringBuilder total_qIqLine = new StringBuilder(totalDataSetsInCollection*11);
            StringBuilder bufferLine = new StringBuilder(totalDataSetsInCollection*2);

            status.setText("Assembling signals");
            for(int i=0; i<totalDataSetsInCollection; i++){

                fLine.append(String.format(Locale.US, "%d ", i));

                boolean notFound = true;
                for (Signals signal : signals) {
                    if (signal.getId() == i) {
                        signalLine.append(convertSignalToString(signal.getSignal()));
                        izeroLine.append(convertDoubleTo3EString(signal.getIzero()));
                        rgLine.append(convertDoubleTo3EString(signal.getRg()));
                        rgErrorLine.append(convertDoubleTo3EString(signal.getRgError()));
                        izeroErrorLine.append(convertDoubleTo3EString(signal.getIzeroError()));
                        total_qIqLine.append(convertqIqToString(signal.getTotal_qIq()));
                        bufferLine.append(convertSingleBinaryToString(signal.getIsBuffer()));
                        notFound = false;
                        break;
                    }
                }

                if (notFound){
                    signalLine.append(convertSignalToString(1.0d));
                    izeroLine.append(convertDoubleTo3EString(0.0d));
                    rgLine.append(convertDoubleTo3EString(0.0d));
                    rgErrorLine.append(convertDoubleTo3EString(0.0d));
                    izeroErrorLine.append(convertDoubleTo3EString(0.0d));
                    total_qIqLine.append(convertqIqToString(0.0d));
                    bufferLine.append(convertSingleBinaryToString(1));
                }
                publish(i);
            }
            progressBar.setValue(0);

            String fLineMD5HEX = DigestUtils.md5Hex(fLine.toString()).toUpperCase();
            String signaLineMD5HEX = DigestUtils.md5Hex(signalLine.toString()).toUpperCase();
            String izeroLineMD5HEX = DigestUtils.md5Hex(izeroLine.toString()).toUpperCase();
            String rgLineMD5HEX = DigestUtils.md5Hex(rgLine.toString()).toUpperCase();
            String izeroErrorLineMD5HEX = DigestUtils.md5Hex(izeroErrorLine.toString()).toUpperCase();
            String rgLineErrorMD5HEX = DigestUtils.md5Hex(rgErrorLine.toString()).toUpperCase();
            String total_qIqLineMD5HEX = DigestUtils.md5Hex(total_qIqLine.toString()).toUpperCase();
            String bufferLineMD5HEX = DigestUtils.md5Hex(bufferLine.toString()).toUpperCase();

//            System.out.println("MD5 FLINE :: " + fLineMD5HEX);
//            fLine.append(System.lineSeparator());
//            fLineMD5HEX = DigestUtils.md5Hex(StringUtils.chomp(fLine.toString())).toUpperCase();
//            System.out.println("MD5 FLINE :: " + fLineMD5HEX);

            /*
             * each line will be line_number MD5_check_sum {data}
             * to recover data, split the line, take 2nd element and beyond
             *
             * assemble datalines for each frame
             * ignored frames are excluded ???
             */
            int totalq = qvalues.size();
            int digitsum = 0;
            for(Number qval : qvalues){
                int tdigit = getDigits(qval.doubleValue());
                if (tdigit > digitsum){
                    digitsum = tdigit;
                }
            }

            StringBuilder qline = new StringBuilder(totalq*(digitsum+5));
            for(Number qval : qvalues){
                qline.append(String.format("%s ", formattedQ(qval.doubleValue(), digitsum)));
            }
            String qlineMD5HEX = DigestUtils.md5Hex(qline.toString()).toUpperCase();

            ArrayList<String> subtractedErrorsOutput = new ArrayList<>();
            ArrayList<String> subtractedOutput = new ArrayList<>();
            ArrayList<String> unsubtractedErrorsOutput = new ArrayList<>();
            ArrayList<String> unsubtractedOutput = new ArrayList<>();

            //blanks
            StringBuilder blank = new StringBuilder(3*totalq);
            for(int i =0; i< totalq; i++){
                blank.append(" . ");
            }
            status.setText("Assembling subtracted and unsubtracted sets");

            for(int i=0; i<totalDataSetsInCollection; i++){

                boolean notFound = true;
                StringBuilder tempOutsub = new StringBuilder(totalq*11);
                StringBuilder tempOutsubError = new StringBuilder(totalq*11);

                for (Signals signal : signals) {
                    if (signal.getId() == i) {
                        XYSeries tempSub = subtractedSets.get(i); // should be inclusive of common q-values
                        XYSeries tempSubE = subtractedSetsErrors.get(i); // should be inclusive of common q-values

                        for(Number qval : qvalues){
                            int index = tempSub.indexOf(qval);
                            tempOutsub.append(convertIntensityToString(tempSub.getY(index).doubleValue()));
                            tempOutsubError.append(convertIntensityToString(tempSubE.getY(index).doubleValue()));
                        }
                        notFound = false;
                        break;
                    }
                }

                if (notFound){ // write out . for subtracted and unsubtracted for the excluded frame
                    tempOutsub.append(blank);
                    tempOutsubError.append(blank);
                }

                String datMD5HEX = DigestUtils.md5Hex(tempOutsub.toString()).toUpperCase();
                String errMD5HEX = DigestUtils.md5Hex(tempOutsubError.toString()).toUpperCase();
                subtractedOutput.add(i + " " + datMD5HEX+" "+tempOutsub.toString());
                subtractedErrorsOutput.add(i + " " + errMD5HEX+" "+tempOutsubError);

                XYSeries unsubAllData = collection.getDataset(i).getAllData();
                XYSeries unsubAllDataEr = collection.getDataset(i).getAllDataError();

                StringBuilder tempOutunsub = new StringBuilder(totalq*11);
                StringBuilder tempOutunsubError = new StringBuilder(totalq*11);

                for(Number qval : qvalues){ // qvalues found in common, no need to check
                    int index = unsubAllData.indexOf(qval);
                    tempOutunsub.append(convertIntensityToString(unsubAllData.getY(index).doubleValue()));
                    tempOutunsubError.append(convertIntensityToString(unsubAllDataEr.getY(index).doubleValue()));
                }

                datMD5HEX = DigestUtils.md5Hex(tempOutunsub.toString()).toUpperCase();
                errMD5HEX = DigestUtils.md5Hex(tempOutunsubError.toString()).toUpperCase();

                unsubtractedOutput.add(i + " " + datMD5HEX+" "+tempOutunsub.toString());            // prepend the checksum
                unsubtractedErrorsOutput.add(i + " " + errMD5HEX+" "+tempOutunsubError.toString()); // prepend the checksum

                publish(i);
            }

            // assembled averaged buffer and associated error
            StringBuilder tempOutAvgBuff = new StringBuilder(totalq*11);
            StringBuilder tempOutAvgBuffError = new StringBuilder(totalq*11);
            for(int i=0; i<totalq; i++){
                tempOutAvgBuff.append(convertIntensityToString(buffer.getY(i).doubleValue()));
                tempOutAvgBuffError.append(convertIntensityToString(bufferError.getY(i).doubleValue()));
            }
            String avgBufferMD5HEX = DigestUtils.md5Hex(tempOutAvgBuff.toString()).toUpperCase();
            String avgBuffererrorMD5HEX = DigestUtils.md5Hex(tempOutAvgBuffError.toString()).toUpperCase();

            /*
             * format of file is :
             * JSON string describing data
             *  0 LINE 0 JSON STRING
             *  1 # REMARK
             *  2 # REMARK
             *  3 # REMARK
             *  4 Frame indices
             *  5 SIGNAL
             *  6 integrated qIq
             *  7 Rg
             *  8 Izero
             *  9 buffer
             * 10 qvalues
             * 11 rg error
             * 12 izero error
             * 13 unsubtracted
             *  . unsubtracted errors
             *  + subtracted
             *  + subtracted errors
             *  +
             *  +
             */
            secFormat = new SecFormat(totalSelected);
            secFormat.setThreshold(threshold);
            secFormat.setTotal_momentum_transfer_vectors(qvalues.size());
            secFormat.setFrame_index(4);
            secFormat.setSignal_index(5);
            secFormat.setIntegrated_qIq_index(6);
            secFormat.setRg_index(7);
            secFormat.setIzero_index(8);
            secFormat.setBackground_index(9);
            secFormat.setMomentum_transfer_vector_index(10);

            secFormat.setRg_error_index(11);
            secFormat.setIzero_error_index(12);

            int startLastLine = 12 + 1;

            int totalUnSub = unsubtractedOutput.size();
            int totalSub = subtractedOutput.size();
            secFormat.setUnsubtracted_intensities_index(startLastLine);
            secFormat.setUnsubtracted_intensities_error_index(startLastLine+totalUnSub);
            secFormat.setSubtracted_intensities_index(startLastLine+2*totalUnSub);
            secFormat.setSubtracted_intensities_error_index(startLastLine+2*totalUnSub+totalSub);
            secFormat.setAveraged_buffer_index(startLastLine+2*totalUnSub+2*totalSub);
            secFormat.setAveraged_buffer_error_index(startLastLine+2*totalUnSub+2*totalSub+1);

            /*
             * assemble the JSON string
             */
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            mapper.registerModule(new SimpleModule() {
                @Override
                public void setupModule(SetupContext context) {
                    super.setupModule(context);
                    context.addBeanSerializerModifier(new BeanSerializerModifier() {
                        @Override
                        public JsonSerializer<?> modifySerializer(
                                SerializationConfig config, BeanDescription desc, JsonSerializer<?> serializer) {
                            if (Hidable.class.isAssignableFrom(desc.getBeanClass())) {
                                return new HidableSerializer((JsonSerializer<Object>) serializer);
                            }
                            return serializer;
                        }
                    });
                }
            });

            sasObject.setSecFormat(secFormat);
            String sasObjectString = mapper.writeValueAsString(sasObject);

            out.write(sasObjectString + System.lineSeparator());   // line 0
            out.write("# REMARK" + System.lineSeparator());        // line 1
            out.write("# REMARK" + System.lineSeparator());        // line 2
            out.write("# REMARK" + System.lineSeparator());        // line 3
            out.write(fLineMD5HEX + " " + fLine.toString() + System.lineSeparator());                  // line 4
            out.write(signaLineMD5HEX + " " + signalLine.toString() + System.lineSeparator());         // line 5
            out.write(total_qIqLineMD5HEX + " " + total_qIqLine.toString() + System.lineSeparator());  // line 6
            out.write(rgLineMD5HEX + " " + rgLine.toString() + System.lineSeparator());                // line 7
            out.write(izeroLineMD5HEX + " " + izeroLine.toString() + System.lineSeparator());          // line 8
            out.write(bufferLineMD5HEX + " " + bufferLine.toString() + System.lineSeparator());        // line 9
            out.write(qlineMD5HEX + " " + qline.toString() + System.lineSeparator());                  // line 10
            out.write(rgLineErrorMD5HEX + " " + rgErrorLine.toString() + System.lineSeparator());      // line 11
            out.write(izeroErrorLineMD5HEX + " " + izeroErrorLine.toString() + System.lineSeparator());// line 12

            for(String line : unsubtractedOutput){
                out.write(line + System.lineSeparator());
            }
            for(String line : unsubtractedErrorsOutput){
                out.write(line + System.lineSeparator());
            }
            for(String line : subtractedOutput){
                out.write(line + System.lineSeparator());
            }
            for(String line : subtractedErrorsOutput){
                out.write(line + System.lineSeparator());
            }

            // Append the averaged buffer
            out.write(avgBufferMD5HEX  + " " + tempOutAvgBuff.toString() + System.lineSeparator());
            out.write(avgBuffererrorMD5HEX  + " " + tempOutAvgBuffError.toString() + System.lineSeparator());

            out.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void updateRgIzero(){ // only calculate Rg/Izero for frames above threshold
        int totalFrames = sasObject.getSecFormat().getTotal_frames();
        for(int i=0; i<totalFrames; i++){
            signals.add(new Signals(i, 0, 0,0,0,0));
        }

        for(int i=0; i<collection.getTotalDatasets(); i++){
            Dataset tempData = collection.getDataset(i);
            AutoRg temp = new AutoRg(tempData.getAllData(), excludePoints);
            signals.set(tempData.getId(), new Signals(tempData.getId(), 0, temp.getI_zero(), temp.getRg(), temp.getI_zero_error(), temp.getRg_error()));
        }

        // prepare lines to write to file
        StringBuilder izeroLine = new StringBuilder(totalFrames*11);
        StringBuilder rgLine = new StringBuilder(totalFrames*9);
        StringBuilder rgErrorLine = new StringBuilder(totalFrames*9);
        StringBuilder izeroErrorLine = new StringBuilder(totalFrames*9);

        for (Signals signal : signals) {
                izeroLine.append(convertDoubleTo3EString(signal.getIzero()));
                rgLine.append(convertDoubleTo3EString(signal.getRg()));
                rgErrorLine.append(convertDoubleTo3EString(signal.getRgError()));
                izeroErrorLine.append(convertDoubleTo3EString(signal.getIzeroError()));
        }

        String izeroLineMD5HEX = DigestUtils.md5Hex(izeroLine.toString()).toUpperCase();
        String rgLineMD5HEX = DigestUtils.md5Hex(rgLine.toString()).toUpperCase();
        String izeroErrorLineMD5HEX = DigestUtils.md5Hex(izeroErrorLine.toString()).toUpperCase();
        String rgLineErrorMD5HEX = DigestUtils.md5Hex(rgErrorLine.toString()).toUpperCase();

        /*
         * assemble the JSON string
         */
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.registerModule(new SimpleModule() {
            @Override
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                context.addBeanSerializerModifier(new BeanSerializerModifier() {
                    @Override
                    public JsonSerializer<?> modifySerializer(
                            SerializationConfig config, BeanDescription desc, JsonSerializer<?> serializer) {
                        if (Hidable.class.isAssignableFrom(desc.getBeanClass())) {
                            return new HidableSerializer((JsonSerializer<Object>) serializer);
                        }
                        return serializer;
                    }
                });
            }
        });

        secFormat = sasObject.getSecFormat();

        try {
            String sasObjectString = mapper.writeValueAsString(sasObject);
            updateLineInSECFile(0, sasObjectString + System.lineSeparator());
            updateLineInSECFile(secFormat.getRg_index(), rgLineMD5HEX + " " + rgLine.toString() + System.lineSeparator());                // line 7
            updateLineInSECFile(secFormat.getIzero_index(), izeroLineMD5HEX + " " + izeroLine.toString() + System.lineSeparator());          // line 8
            updateLineInSECFile(secFormat.getRg_error_index(), rgLineErrorMD5HEX + " " + rgErrorLine.toString() + System.lineSeparator());      // line 11
            updateLineInSECFile(secFormat.getIzero_error_index(), izeroErrorLineMD5HEX + " " + izeroErrorLine.toString() + System.lineSeparator());// line 12

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    /**
     * for each dat file in assembled collection
     * perform :
     * 1. background subtraction
     * 2. signal calculation
     * 3. auto-Rg
     */
    private void makeSamples(){

        Dataset tempDataset;
        XYDataItem tempXY;

        XYSeries ratio = new XYSeries("ratio"), tempData;

        int totalInCollection = collection.getTotalDatasets();
        progressBar.setMaximum(totalInCollection);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        status.setText("Estimating signal and performing subtraction... please wait");
        int totalXY;
        int signalsCount=0;
        double area;

        XYSeries qIqData = new XYSeries("qIq");

        subtractedSets = new ArrayList<>();
        subtractedSetsErrors = new ArrayList<>();

        for(int i=0; i < totalInCollection; i++){ // iterate over all frames
                tempDataset = collection.getDataset(i);
                subtractedSets.add(new XYSeries("set_" + Integer.toString(i)));
                subtractedSetsErrors.add(new XYSeries("set_"+ Integer.toString(i)));

                if (tempDataset.getInUse()){ // if dataset in use, calculate ratio and then do subtraction
                    ratio.clear();
                    tempData = tempDataset.getAllData();

                    // create XY Series using ratio to buffer then integrate
                    for(Number qv : qvalues){
                        int index = tempData.indexOf(qv);
                        tempXY = tempData.getDataItem(index);
                        if (index > -1){
                            ratio.add(tempXY.getXValue(), tempXY.getYValue()/buffer.getY(buffer.indexOf(qv)).doubleValue());
                        }
                    }


                    int id = tempDataset.getId();
                    boolean isSignal = false;
                    if (!keptBuffers.contains(id)){
                        isSignal = true;
                    }

                    // if number of points is less than 100, skip
                        // integrate
                        area = Functions.trapezoid_integrate(ratio);

                        int row = tempDataset.getId(); // indexes to frames that are acceptable, may have dropped frames

                        // index, signal, Rg, I(0), integral qIq
                        ArrayList<XYSeries> subtraction = subtract(tempDataset.getAllData(), tempDataset.getAllDataError(), buffer, bufferError);
                        XYSeries subtracted = subtraction.get(0);

                        if (isSignal && area/noSignal > threshold){
                            AutoRg temp = new AutoRg(subtracted, excludePoints);
                            signals.add(new Signals(row, area/noSignal, temp.getI_zero(), temp.getRg(), temp.getI_zero_error(), temp.getRg_error()));
                        } else {
                            signals.add(new Signals(row, area/noSignal, 0, 0, 0, 0));
                        }
                        signalsCount++;

                        if (bufferRejects.contains(row)){ // if not a buffer set to false
                            signals.get(signalsCount-1).setIsBuffer(false);
                        }
                        // add qIq integration
                        qIqData.clear();
                        totalXY = subtracted.getItemCount();
                        // create XY Series using ratio to buffer then integrate
                        for(int q=0; q < totalXY; q++){
                            tempXY = subtracted.getDataItem(q);
                            qIqData.add(tempXY.getX(), tempXY.getYValue()*tempXY.getXValue());
                        }
                        area = Functions.trapezoid_integrate(qIqData);

                        signals.get(signalsCount-1).setTotal_qIq(area);
                        XYSeries suberrors = subtraction.get(1);
                    try {
                        subtractedSets.set(i, subtracted.createCopy(0,subtracted.getItemCount()-1));
                        subtractedSetsErrors.set(i, suberrors.createCopy(0, suberrors.getItemCount()-1));
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }
                }

            publish(i);
        }

        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        status.setText("Finished creating samples");
    }


    /**
     * Given a reference unsubtracted frame, if two frames are identical, the difference divided by the reference should sum to zero
     * @param collection
     * @param lowerIndex
     * @param upperIndex
     * @return
     */
    private boolean calculateAverageAndVarianceOfDWInWindow( ArrayList<XYSeries> collection, int lowerIndex, int upperIndex){

        int windowSize = collection.size();
        double[] values = new double[windowSize-1];
        int foundIndex;

        XYSeries tempData, refData;
        XYDataItem refXY;

        ArrayList<Double> residuals = new ArrayList<>();
        int tt = upperIndex-lowerIndex+1;
        for(int i=0; i<tt; i++){
            residuals.add(0.0d);
        }

        int count=0;
        double diff;
        for(int i=0; i<1; i++){

            refData = collection.get(i);

            for(int j=(i+1); j<windowSize; j++){

                tempData = collection.get(j);
                // go through each q-value in reference
                double sum = 0;
                tt=0;
                for (int q = lowerIndex; q < (upperIndex+1); q++) {
                    refXY = refData.getDataItem(q);
                    foundIndex = tempData.indexOf(refXY.getX());
                    if (foundIndex > -1 ) {
                        diff = (tempData.getY(foundIndex).doubleValue() - refXY.getYValue())/refXY.getYValue();
                        sum += diff;
                        residuals.set(tt,diff );
                        tt++;
                    }
                }
                // should sum to zero if the same and be greater than one if different
                //values[count] = sum*sum;
                values[count] = calculateDurbinWatson(residuals, tt);
                count++;
            }
        }

        // average and variance over all pairwise values
        double max = StatUtils.max(values);
        double min = StatUtils.min(values);
        double spread = max - min;
//        System.out.println("Max : min " + max + " " + min + " " + spread + " < :: " + background_spread);

        if (spread < min_spread){
            min_spread = spread;
        }

        if (spread < background_spread ){
            return true;
        }

        return false;
    }

    /**
     * update background based on prior selection
     * find large continuous sets of frames to be background
     * Signal will have a I(qmin)
     */
    private void setBackgroundFrames(SortedSet<Integer> bufferIndices) {
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(false);
        status.setText("estimating background frames");

        int total = collection.getTotalDatasets();
        int totalN = qvalues.size();

        noSignal = (maxQvalueInCommon.doubleValue() - minQvalueInCommon.doubleValue())*(totalN+1)/(double)totalN;
        // set window
        int window = 37;
        //
        //TreeSet<Integer> keepers = new TreeSet<Integer>();
        bufferRejects = new TreeSet<>();
        keptBuffers = new TreeSet<>();
        keptBufferIndices = new ArrayList();

        ArrayList<XYSeries> keptData = new ArrayList<>();
        ArrayList<XYSeries> keptErrors = new ArrayList<>();

        for(Integer kept : bufferIndices){
            keptBufferIndices.add(kept);
            keptBuffers.add(kept);
            Dataset tempDataset = collection.getDataset(kept);
            keptData.add(tempDataset.getAllData());
            keptErrors.add(tempDataset.getAllDataError());
        }

        for (int m=0; m < total; m++){
            Dataset dd = collection.getDataset(m);
            if (dd.getInUse() && !keptBuffers.contains(dd.getId())){
                bufferRejects.add(dd.getId());
            }
        }

        status.setText("Averaging background frames :: " + keptBufferIndices.size());
        buffer = new XYSeries("averaged buffer");
        bufferError = new XYSeries("averaged buffer errors");
        this.averageWithoutScaling(keptData, keptErrors, buffer, bufferError);

        /*
         * could refine at this point, take the average set of choosen frames and do ratio
         */
        // create trace using averagedEstimatedBackground
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(false);
        status.setText("Finished averaging buffer");
    }

    /**
     * estimate frames to be used as background and set
     * find large continuous sets of frames to be background
     * Signal will have a I(qmin)
     */
    private void estimateBackgroundFrames() {
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(false);
        status.setText("estimating background frames");

        final int totalInCollection = collection.getTotalDatasets();

//        if (!this.is0p3qmaxPossible()){
//            this.findMaximumCommonQvalue();
//        }
//     this.findLeastCommonQvalue(); // calculations are bounded between the lowest and highest common q-values

        final int lowerIndex = collection.getDataset(0).getAllData().indexOf(minQvalueInCommon);
        final int upperIndex = collection.getDataset(0).getAllData().indexOf(maxQvalueInCommon);

        int totalN = qvalues.size();

        noSignal = (maxQvalueInCommon.doubleValue() - minQvalueInCommon.doubleValue())*(totalN+1)/(double)totalN;
        // set window
        final int window = 37;
        //
        //TreeSet<Integer> keepers = new TreeSet<Integer>();
        bufferRejects = new TreeSet<>();
        keptBuffers = new TreeSet<>();
        /*
         * test if frames in the window are within the background defined by noSignal
         * might be more accurate to do a first pass, estimate the background using the median
         * then go back and assign
         *
         * could be multi-threaded
         */
        for (int w=window; w < (totalInCollection - window); w++){
            ArrayList<XYSeries> collectionWindow = new ArrayList<>();

            for (int m=(w - window); m < w; m++){
                Dataset dd = collection.getDataset(m);
                if (dd.getInUse()){
                    collectionWindow.add(dd.getAllData());
                }
            }
            // average frames in window?  Or take median?
            // baseline will produce the lowest signal
            // calculate minimum ratio value using this window
            if (calculateAverageAndVarianceOfDWInWindow(collectionWindow, lowerIndex, upperIndex)){
                for (int m=(w-window); m < w; m++){
                    Dataset dd = collection.getDataset(m);
                    if (dd.getInUse() && !bufferRejects.contains(dd.getId())){
                          //  keepers.add(dd.getId());
                        keptBuffers.add(dd.getId());
                    }
                }
            } else {
                for (int m=(w-window); m < w; m++){
                    Dataset dd = collection.getDataset(m);
                    if (dd.getInUse() && !keptBuffers.contains(dd.getId())){
                        bufferRejects.add(dd.getId());
                    }
                }
            }
        }

        if (keptBuffers.size() == 0){
            background_spread=min_spread;
            //System.out.println(bufferRejects.size() + " >  " + keptBuffers.size());
        }

        while (keptBuffers.size() < 6 && background_spread < 10){
            background_spread += 0.3*min_spread;
            bufferRejects.clear();
            keptBuffers.clear();
            status.setText(String.format("Estimating background...kept buffers is nil, adjusting tolerance :: %.4f", background_spread));
            for (int w=window; w < (totalInCollection - window); w++){
                ArrayList<XYSeries> collectionWindow = new ArrayList<>();

                for (int m=(w - window); m < w; m++){
                    Dataset dd = collection.getDataset(m);
                    if (dd.getInUse()){
                        collectionWindow.add(dd.getAllData());
                    }
                }
                // average frames in window?  Or take median?
                // baseline will produce the lowest signal
                // calculate minimum ratio value using this window
                if (calculateAverageAndVarianceOfDWInWindow(collectionWindow, lowerIndex, upperIndex)){
                    for (int m=(w-window); m < w; m++){
                        Dataset dd = collection.getDataset(m);
                        if (dd.getInUse() && !bufferRejects.contains(dd.getId())){
                            //  keepers.add(dd.getId());
                            keptBuffers.add(dd.getId());
                        }
                    }
                } else {
                    for (int m=(w-window); m < w; m++){
                        Dataset dd = collection.getDataset(m);
                        if (dd.getInUse() && !keptBuffers.contains(dd.getId())){
                            bufferRejects.add(dd.getId());
                        }
                    }
                }
            }
        }

        // incase it all fails, load/use the first set of frames as background
        if (background_spread >= 10 && keptBuffers.size() < 6){
            bufferRejects.clear();
            keptBuffers.clear();
            int tenPercent = (int)(totalInCollection*0.1)+1;
            for (int i=0; i<tenPercent; i++){
                Dataset dd = collection.getDataset(i);
                keptBuffers.add(dd.getId());
            }

            for (int i=tenPercent; i<totalInCollection; i++){
                Dataset dd = collection.getDataset(i);
                bufferRejects.add(dd.getId());
            }
        }

       // throw an exception if background spread exceeds limit

        keptBufferIndices = new ArrayList();

        ArrayList<XYSeries> keptData = new ArrayList<>();
        ArrayList<XYSeries> keptErrors = new ArrayList<>();

        for(Integer kept : keptBuffers){
            keptBufferIndices.add(kept);
            Dataset tempDataset = collection.getDataset(kept);
            keptData.add(tempDataset.getAllData());
            keptErrors.add(tempDataset.getAllDataError());
        }

        status.setText("Averaging background frames :: " + keptBufferIndices.size());
        buffer = new XYSeries("averaged buffer");
        bufferError = new XYSeries("averaged buffer errors");
        this.averageWithoutScaling(keptData, keptErrors, buffer, bufferError);

        /*
         * could refine at this point, take the average set of choosen frames and do ratio
         */
        // create trace using averagedEstimatedBackground
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(false);
        status.setText("Finished averaging buffer");
    }

    /**
     * very robost method for averaging, makes no assumptions about matching X-values as each value is checked
     * it will be a slower performing averager
     * the averaged file will be bound to the set of common qvalues
     *
     * @param data
     * @param errors
     * @param output
     * @param outputErrors
     */
    public void averageWithoutScaling(ArrayList<XYSeries> data, ArrayList<XYSeries> errors, XYSeries output, XYSeries outputErrors){

        XYSeries datasum = new XYSeries("averaged");
        XYSeries datasumerror = new XYSeries("averagederror");

        XYSeries tempset = data.get(0);
        XYSeries tempsetError = errors.get(0);
        double var, sigma;
        XYDataItem xyDataItem;
        for(Number qv : qvalues){
            int index = tempset.indexOf(qv);
            if (index > -1){
                xyDataItem = tempset.getDataItem(index);
                sigma = 1.0/tempsetError.getDataItem(index).getYValue();
                var = sigma*sigma;
                datasum.add(xyDataItem.getX(), xyDataItem.getYValue()*var);
                datasumerror.add(xyDataItem.getX(), var);
            }
        }


        int totalIn = datasum.getItemCount();
        // process in blocks of the arraylist data
        for(int d=1; d<data.size(); d++){
            tempset = data.get(d);
            tempsetError = errors.get(d);

            for(int i=0; i<totalIn; i++) {
                XYDataItem refItem = datasum.getDataItem(i);
                int index = tempset.indexOf(refItem.getX());
                if (index > -1){
                    double tderror = tempsetError.getDataItem(index).getYValue();
                    var = 1.0/(tderror * tderror);

                    datasum.updateByIndex(i, tempset.getY(index).doubleValue()*var + refItem.getYValue());
                    datasumerror.updateByIndex(i, var + datasumerror.getY(i).doubleValue());
                }
            }
        }


        // divide by total variance to get weighted average
        output.clear();
        outputErrors.clear();
        for(int i=0; i<totalIn; i++){
            XYDataItem base = datasum.getDataItem(i);
            var = datasumerror.getY(i).doubleValue();
            output.add(base.getX(), base.getYValue() / var);
            outputErrors.add(base.getX(), 1.0/Math.sqrt(var));
        }
    }




    private ArrayList<XYSeries> subtract(XYSeries sample, XYSeries sampleError, XYSeries buffer, XYSeries bufferError){
        ArrayList<XYSeries> returnMe = new ArrayList<XYSeries>();
        // for each element in sample collection, do subtraction

        XYSeries subData;
        XYSeries subError;
        XYDataItem tempDataItem;

        int tempTotal, indexOf;
        double qValue, yValue, eValue;

        tempTotal = sample.getItemCount();

        subData = new XYSeries("subtracted");
        subError = new XYSeries("errorSubtracted");
        //Subtract and add to new data
        double maxQValueInBuffer = buffer.getMaxX();

        QLOOP:
        for(int q=0; q<tempTotal; q++){
            tempDataItem = sample.getDataItem(q);
            qValue = tempDataItem.getXValue();
            /*
             * check to see if in buffer
             */
            indexOf = buffer.indexOf(qValue);
            yValue = sampleError.getY(q).doubleValue();

            if (indexOf > -1){
                subData.add(qValue, tempDataItem.getYValue() - buffer.getY(indexOf).doubleValue() );

                eValue = bufferError.getY(indexOf).doubleValue();
                subError.add(qValue, Math.sqrt(yValue*yValue + eValue*eValue));

            } else { // interpolate
                // interpolation requires at least two values on left or right side of value of interest
                if (qValue < maxQValueInBuffer) {
                    Double[] results = Functions.interpolate(buffer, qValue, 1);
                    Double[] sigmaResults = Functions.interpolateSigma(bufferError, qValue);
                    //returns unlogged data
                    eValue = sigmaResults[1];

                    subData.add(qValue, results[1]);
                    subError.add(qValue, Math.sqrt(yValue * yValue + eValue * eValue));
                }
            }
        }

        returnMe.add(subData);
        returnMe.add(subError);

        return returnMe;
    }

//    public class Signals{
//        private final int id;
//        private final double signal;
//        private final double izero;
//        private final double rg;
//        private final double rgError;
//        private final double izeroError;
//        private double total_qIq;
//        private int isBuffer=1;
//
//        public Signals(int id, double signal, double izero, double rg, double izeroerror, double rgerror){
//            this.id = id;
//            this.signal = signal;
//            this.izero = izero;
//            this.rg = rg;
//            if (Double.isNaN(rg)){
//                System.out.println(id + " NAN " + rg + " " + izero + " " + signal);
//            }
//            this.izeroError = izeroerror;
//            this.rgError = rgerror;
//        }
//        public void setTotal_qIq(double qid){ this.total_qIq = qid;}
//
//        public int getId() {
//            return id;
//        }
//
//        public double getSignal() {
//            return signal;
//        }
//
//        public double getIzero() {
//            return izero;
//        }
//        public double getIzeroError(){ return izeroError;}
//        public double getRg() {
//            return rg;
//        }
//        public double getRgError() {
//            return rgError;
//        }
//
//        public double getTotal_qIq() {
//            return total_qIq;
//        }
//
//        public void setIsBuffer(boolean value){ isBuffer = value ? 1 : 0; }
//
//        public int getIsBuffer() {
//            return isBuffer;
//        }
//    }

    public int getTotalQValues(){return qvalues.size();}

    private XYSeries averageSet(ArrayList<XYSeries> seriesSet, int lowerIndex, int upperIndex){

        XYSeries temp = new XYSeries("temp");
        int count=upperIndex-lowerIndex+1;

        count=0;
        for(int i=lowerIndex; i<=upperIndex; i++){
            temp.add(seriesSet.get(0).getDataItem(i));
            count++;
        }

        for(int s=1; s<seriesSet.size(); s++){
            XYSeries thisone = seriesSet.get(s);

            for(int i=0; i<count; i++){
                XYDataItem item = temp.getDataItem(i);
                temp.updateByIndex(i, item.getYValue() + thisone.getY(thisone.indexOf(item.getX())).doubleValue());
            }
        }
        // average it
        count = upperIndex - lowerIndex + 1;
        double inv = 1.0/(double)seriesSet.size();
        for(int i=0; i<count; i++){
            XYDataItem item = temp.getDataItem(i);
            temp.updateByIndex(i, item.getYValue()*inv);
        }

        return temp;
    }


    /**
     * d = 2 means no autocorrelation
     * d-value should always lie between 0 and 4
     */
    private double calculateDurbinWatson(ArrayList<Double> testSeries, int totalResiduals){

        double numerator=0, value, diff;
        double denominator = testSeries.get(0)*testSeries.get(0);
        //int totalResiduals = testSeries.size();

        for(int i=1; i<totalResiduals; i++){
            value = testSeries.get(i);
            diff = value - testSeries.get(i-1); // x_(t) - x_(t-1)
            numerator += diff*diff;
            denominator += value*value; // sum of (x_t)^2
        }

        return numerator/denominator;
    }


    /**
     * Determine number of non zero values so:
     * 0.00034566 => 5 non-zero values
     *
     * @param qvalue
     * @return
     */
    private int getDigits(double qvalue) {
        String toText = Double.toString(qvalue);
        int integerPlaces = toText.indexOf('.');
        int decimalPlaces;

        String[] temp = toText.split("\\.0*");
        decimalPlaces = (temp.length == 2) ? temp[1].length() : (toText.length() - integerPlaces -1);

        return decimalPlaces;
    }


    public String formattedQ(double qvalue, int numberOfDigits) {
        String numberToPrint ="";
        switch(numberOfDigits){
            case 7: numberToPrint = String.format(Locale.US, "%.6E", qvalue);
                break;
            case 8: numberToPrint = String.format(Locale.US, "%.7E", qvalue);
                break;
            case 9: numberToPrint = String.format(Locale.US, "%.8E", qvalue);
                break;
            case 10: numberToPrint = String.format(Locale.US, "%.9E", qvalue);
                break;
            case 11: numberToPrint = String.format(Locale.US,"%.10E", qvalue);
                break;
            case 12: numberToPrint = String.format(Locale.US, "%.11E", qvalue);
                break;
            case 13: numberToPrint = String.format(Locale.US, "%.12E", qvalue);
                break;
            case 14: numberToPrint = String.format(Locale.US, "%.13E", qvalue);
                break;
            default: numberToPrint = String.format(Locale.US,"%.6E", qvalue);
                break;
        }
        return numberToPrint;
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        progressBar.setValue(i);
        super.process(chunks);
    }

    public String getOutputname() {
        return outputname;
    }

    public void setExcludePoints(int value){ this.excludePoints = value; }
    /**
     *
     * @param value
     * @return
     */
    public static String convertSignalToString(double value){
        return String.format(Locale.US, "%.8E ", value);
    }

    /**
     * Use for Izero or Rg
     * @param value
     * @return formatted String
     */
    public static String convertDoubleTo3EString(double value){
        return String.format(Locale.US, "%.3E ", value);
    }

    /**
     *
     * @param value
     * @return
     */
    public static String convertqIqToString(double value){
        return String.format(Locale.US, "%.6E ", value);
    }

    public static String convertSingleBinaryToString(int value){
        return String.format(Locale.US, "%d ", value);
    }

    public static String convertIntensityToString(double value){
        return String.format(Locale.US, "%.4E ", value);
    }


    private void updateLineInSECFile(int lineToUpdate, String stringToWrite){
        try {
            int size = 8192 * 16;
            // only replacing first line (sasObjectString)
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(secfilename)), size);
            BufferedWriter bw = new BufferedWriter(new FileWriter(parentPath+"/temp.sec"));

            int is;
            for(int i=0; i<lineToUpdate; i++){ // readline() removes the terminator
                    bw.write(br.readLine() + System.lineSeparator());
            }
            bw.write(stringToWrite);
            br.readLine(); // read past the old line

            do {
                is = br.read();
                if (is != -1) {
                    bw.write((char) is);
                }
            } while (is != -1);

            bw.close();
            br.close();
            // overwrite original file
            File f1 = new File(parentPath+"/temp.sec");
            File f2 = new File(secfilename);
            f1.renameTo(f2);

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
