package version4.SEC;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.apache.commons.codec.digest.DigestUtils;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import version4.sasCIF.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.newInputStream;

public class SECFile {
    private static long LINES_TO_READ = 10_000_000;

    private String filename, filebase, absolutePath, parentPath;
    private RandomAccessFile file;
    private ArrayList<Long> lineNumbers; // location of where lines start based on byte length
    private ArrayList<Double> qvalues;
    private int totalQValues;

    //private MappedByteBuffer buffer;
    private FileChannel fileChannel;
    private Map<Integer, Long> linesAndLength;

    SecFormat secFormat;
    private ArrayList<Double> rgvalues;
    private ArrayList<Double> rgErrorValues;
    private ArrayList<Double> iZerovalues;
    private ArrayList<Double> iZeroErrorValues;

    private ArrayList<Integer> frame_indices;

    private SasObject sasObject;

    private XYSeries selectedBuffers;
    private XYSeriesCollection signalCollection;
    private XYSeries signalSeries; // calculated signal (integrated ratio of curve to background - total is number of frames
    private XYSeries frame;

    private int start_index_intensity;

    private final int totalFrames;
    private long new_line_byte_length = 1L;
    private String new_line_separator = System.lineSeparator();

    /*
     * what if SECFile contains only q-values and unsubtracted intensities?
     */
    public SECFile(File file) throws IOException {
        this.filename = file.getName();
        this.absolutePath = file.getAbsolutePath();
        this.parentPath = (file.getParent() == null) ? " " : file.getParent();

        filebase = file.getName().split("\\.(?=[^\\.]+$)")[0];

        this.lineNumbers = new ArrayList<>();
        this.streamWithLargeBuffer(file);
        this.determineNewLineCharacterByteSize(file);
        // get first element of the file, should be JSON string
        try {
            this.file = new RandomAccessFile(file, "rw");
            fileChannel = this.file.getChannel();
            this.file.seek(0); // measured in bytes
//            long currentPos = this.file.getFilePointer();
//            byte[] bytes = new byte[5];
//            this.file.read(bytes);
//            System.out.println(new String(bytes));
//            this.file.length();

            /*
             * starting at seek position 0, readline, get length and advance by +1, repeat until EOF
             * map line numbers to byte location
             */
//            int startIndex = 0;
//            while(startIndex < this.file.length()){
//                lineNumbers.add(startIndex);
//                this.file.seek(startIndex);
//                int tempLength = this.file.readLine().getBytes().length;
//                System.out.println("SStartIndex :: " + startIndex + " " + tempLength);
//                startIndex += tempLength + 1;
//            }

            /*
             * convert lines and length to location entries in memory mapped file
             * linesAndLength excludes extra bytes from newLine character
             * 1 byte in unix and mac os
             * 2 bytes in windows
             */
            long startIndex=0L;

            for (Map.Entry<Integer, Long> entry : linesAndLength.entrySet()) {
                lineNumbers.add(startIndex);
//                startIndex += entry.getValue() + (long)System.lineSeparator().getBytes().length;
                startIndex += entry.getValue() + new_line_byte_length;
            }

//            for(Integer index : lineNumbers){
//                this.file.seek(index);
//                System.out.println(index + " :: " + this.file.readLine());
//            }

            long startTime = System.currentTimeMillis();
            fileChannel = this.file.getChannel();
            //System.out.println("FileChannel size " + fileChannel.size());
            //CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            //Get direct byte buffer access using channel.map() operation
            //buffer.position(lineNumbers.get(4));
            //MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(9), linesAndLength.get(9));
            //CharBuffer charBuffer = Charset.forName("UTF-8").decode(buffer);
            //System.out.println(charBuffer.toString());

            long endTime = System.currentTimeMillis();
            System.out.println("mapped access " + (endTime - startTime) + " milliseocnds");
            this.parseJSONHeader();
            this.loadSignal();

            if (secFormat.getRg_index() > 0){
                this.extractRgValues();    // compulsory
                this.extractIzeroValues(); //
            }

            if(secFormat.getIzero_error_index() > 0){
                this.extractIzeroErrorValues();
            }

            if(secFormat.getRg_error_index() > 0){
                this.extractRgErrorValues();
            }

            frame = new XYSeries("frame");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        totalFrames = signalSeries.getItemCount(); // total frames in file should be same as size of signalseries
    }


    /**
     * first element in file must be JSON string
     * UTF-8 will only use one byte per character
     */
    private void parseJSONHeader() {

        try {
            file.seek(0);
            String header = file.readLine();
            sasObject = new SasObject(header);
            secFormat = sasObject.getSecFormat(); // need to throw exception

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(header);
            // Get Name
            JsonNode nameNode = root.path("sec_format");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                secFormat = mapper.treeToValue(nameNode, SecFormat.class);
            } else {

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public XYSeries getSeriesAtFrame(int frameIndex){
        int index = start_index_intensity + frameIndex;
        /*
         * grab data at index and repopulate frame replacing yvalues
         */
        return frame;
    }


    public double getRgbyIndex(int index){
        return rgvalues.get(index);
    }

    /*
     * null values are zero
     */
    public double getIzerobyIndex(int index){
        return iZerovalues.get(index);
    }

    public double getRgErrorbyIndex(int index){
        return rgErrorValues.get(index);
    }

    public double getIzeroErrorbyIndex(int index){
        return iZeroErrorValues.get(index);
    }

    public XYSeriesCollection getSignalCollection(){
        return signalCollection;
    }

    /*
     * x value is frame index starting from 0
     * y value is integrated signal
     */
    public XYSeries getSignalSeries(){
        return signalSeries;
    }

    public XYSeries getBackground(){
        return selectedBuffers;
    }

    public SasObject getSasObject() { return sasObject; }


    /**
     * Populate linesAndLengthTree
     * Parse file, for each line record line Number and length
     * Excludes the newline character at end of each line. (could be /r, /n, /r/n)
     *
     * @param fileToRead input SEC FILE
     */
    private void streamWithLargeBuffer(File fileToRead) {

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        int size = 8192 * 16;
        linesAndLength = new TreeMap<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(newInputStream(fileToRead.toPath()), decoder), size)) {

            AtomicInteger counter = new AtomicInteger(0);
            br.lines().limit(LINES_TO_READ).forEach(s -> {
                linesAndLength.put( counter.getAndIncrement(), (long)s.getBytes().length); // length of lines without new line characters
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void determineNewLineCharacterByteSize(File fileToRead){

        long lengthOfFirstLine = linesAndLength.get(0);
        try {
            RandomAccessFile tfile = new RandomAccessFile(fileToRead, "rw");
            FileChannel fileChannel = tfile.getChannel();
            /*
             * go to the end of the first line
             * if - first line is 10 long then new line character will be at position 10
             */
            tfile.seek(lengthOfFirstLine);

            byte[] bytes = new byte[5];
            tfile.read(bytes);
            String element_1 = String.format("%02X", bytes[0]);
            String element_2 = String.format("%02X", bytes[1]);

            /*
             * Windows new line is carriage_return followed by new_line
             * hexadecimal:
             * cariage_return => 0D
             * new_line => 0A
             */
            if (element_1.equals("0D") && (element_2.equals("0A"))){ // windows
                new_line_byte_length = 2L;
                byte[] new_line = {bytes[0], bytes[1]};
                new_line_separator = new String(new_line, StandardCharsets.UTF_8);
            } else if ( !element_1.equals("0A") ){
                // throw exception
                String out = "BYTE SEQUENCE AFTER FIRST LINE :: \n";
                for(int i=0; i<5; i++){
                    byte[] tempIn = new byte[1];
                    tempIn[0] = bytes[i];
                    out = new StringBuilder().append(out).append(String.format("%d %s %02X %n", i , new String(tempIn, StandardCharsets.UTF_8), tempIn[0])).toString();
                }

                throw new Exception("Improperly formatted or corrupted SEC file - carriage return not found " + out);
            } else {
                byte[] new_line = {bytes[0]};
                new_line_separator = new String(new_line, StandardCharsets.UTF_8);
            }

            fileChannel.close();
            tfile.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void loadSignal(){

        qvalues = new ArrayList<>();
        int qlineIndex = secFormat.getMomentum_transfer_vector_index();
        int flineIndex = secFormat.getFrame_index();
        int buffIndex = secFormat.getBackground_index();
        int signalIndex = secFormat.getSignal_index();
        MappedByteBuffer buffer = null;

        System.out.println("SECFile::loadSignal()");
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(flineIndex), linesAndLength.get(flineIndex));
            CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
            String[] fvalues = charBuffer.toString().split("\\s+"); // starts with checksum
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(signalIndex), linesAndLength.get(signalIndex));
            charBuffer = StandardCharsets.UTF_8.decode(buffer);
            String[] signals = charBuffer.toString().split("\\s+");

            // grab background
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(buffIndex), linesAndLength.get(buffIndex));
            charBuffer = StandardCharsets.UTF_8.decode(buffer);
            String[] bckgrnd = charBuffer.toString().split("\\s+");

            frame_indices = new ArrayList<>();
            signalCollection = new XYSeriesCollection();
            signalSeries = new XYSeries("Signal");
            selectedBuffers = new XYSeries("Selected Buffers");

            for(int i=1; i<fvalues.length;i++){

                signalCollection.addSeries(new XYSeries("frame " + (i-1)));
                String fIndex = fvalues[i];
                frame_indices.add(Integer.valueOf(fIndex));

                XYSeries last = signalCollection.getSeries(i-1);
                last.add(Double.parseDouble(fIndex), Double.parseDouble(signals[i]));
                signalSeries.add(last.getDataItem(0));

                if (Double.parseDouble(bckgrnd[i]) > 0){
                    selectedBuffers.add(last.getDataItem(0));
                }
            }

            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(qlineIndex), linesAndLength.get(qlineIndex));
            charBuffer = StandardCharsets.UTF_8.decode(buffer);
            String[] qvals = charBuffer.toString().split("\\s+");
            for(int i=1; i<qvals.length;i++){ // skip first value since it is the checksum
                qvalues.add(Double.valueOf(qvals[i]));
            }

            totalQValues = qvalues.size();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }


    public ArrayList<Double> getUnSubtractedErrorFrameAt(int index){
        int lookAt = secFormat.getUnsubtracted_intensities_error_index() + index;

        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(lookAt), linesAndLength.get(lookAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum

        if (index != Integer.parseInt(values[0])){
            System.out.println("major error " + index + " " + values[0]);
            String message = "Indices do not match - file corruption do checksum validation ";
            throw new IllegalArgumentException(message);
        }


        ArrayList<Double> intensities = new ArrayList<>();
        for(int i=2; i<values.length; i++){
            intensities.add(Double.valueOf(values[i]));
        }

        return (intensities);
    }


    private void extractRgErrorValues(){
        int rgAt = secFormat.getRg_error_index();
        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(rgAt), linesAndLength.get(rgAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum
        rgErrorValues = new ArrayList<>();
        for(int i=1; i<values.length; i++){
            try{
                double value = Double.valueOf(values[i]);
                rgErrorValues.add(value);
            } catch (NumberFormatException ee) {
                rgErrorValues.add(0.0);
            }
        }
    }


    private void extractRgValues(){
        int rgAt = secFormat.getRg_index();
        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(rgAt), linesAndLength.get(rgAt));
            CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
            String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum
            rgvalues = new ArrayList<>();
            for(int i=1; i<values.length; i++){
                try{
                    double value = Double.parseDouble(values[i]);
                    rgvalues.add(value);
                } catch (NumberFormatException ee) {
                    rgvalues.add(0.0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * extract Izero values from SecFile, values that are null will be set to 0.0
     */
    private void extractIzeroValues(){
        int izeroAt = secFormat.getIzero_index();
        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(izeroAt), linesAndLength.get(izeroAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum
        iZerovalues = new ArrayList<>();
        for(int i=1; i<values.length; i++){
            try{
                double value = Double.valueOf(values[i]);
                iZerovalues.add(value);
            } catch (NumberFormatException ee) {
                iZerovalues.add(0.0d);
            }
        }
    }


    private void extractIzeroErrorValues(){
        int izeroAt = secFormat.getIzero_error_index();
        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(izeroAt), linesAndLength.get(izeroAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum
        iZeroErrorValues = new ArrayList<>();
        for(int i=1; i<values.length; i++){
            try{
                double value = Double.parseDouble(values[i]);
                iZeroErrorValues.add(value);
            } catch (NumberFormatException ee) {
                iZeroErrorValues.add(0.0);
            }
        }
    }

    public ArrayList<Double> getUnSubtractedFrameAt(int index){

        int lookAt = secFormat.getUnsubtracted_intensities_index() + index;

        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(lookAt), linesAndLength.get(lookAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum

        if (index != Integer.parseInt(values[0])){
            System.out.println("major error " + index + " " + values[0]);
            String message = "Indices do not match - file corruption do checksum validation ";
            throw new IllegalArgumentException(message);
        }

        ArrayList<Double> intensities = new ArrayList<>();
        for(int i=2; i<values.length; i++){
            intensities.add(Double.valueOf(values[i]));
        }

        return (intensities);
    }

    /**
     *
     * @param index index of frame to retrieve
     * @return ArrayList of Doubles representing the recorded errors
     */
    public ArrayList<Double> getSubtractedErrorAtFrame(int index){
        int lookAt = secFormat.getSubtracted_intensities_error_index() + index;

        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(lookAt), linesAndLength.get(lookAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum

        if (index != Integer.parseInt(values[0])){
            System.out.println("major error " + index + " " + values[0]);
            String message = "Indices do not match - file corruption do checksum validation ";
            throw new IllegalArgumentException(message);
        }

        ArrayList<Double> errors = new ArrayList<>();
        for(int i=2; i<values.length; i++){
            errors.add(Double.parseDouble(values[i]));
        }

        return (errors);
    }

    public ArrayList<Double> getSubtractedFrameAt(int index){
        int lookAt = secFormat.getSubtracted_intensities_index() + index;

        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(lookAt), linesAndLength.get(lookAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum


        if (index != Integer.parseInt(values[0])){
            System.out.println("major error " + index + " " + values[0]);
            String message = "Indices do not match - file corruption do checksum validation ";
            throw new IllegalArgumentException(message);
        }

        ArrayList<Double> intensities = new ArrayList<>();
        for(int i=2; i<values.length; i++){
            intensities.add(Double.parseDouble(values[i]));
        }

        return (intensities);
    }

    public int getTotalQValues(){
        return qvalues.size();
    }

    public int getTotalFrames(){ return secFormat.getTotal_frames();}

    public double getThreshold(){ return secFormat.getThreshold();}

    public int getClosestIndex(double qvalue){

        int index = 0;
        double value=0;
        for(;index< totalQValues; index++){
            value = qvalues.get(index);
            if (qvalue < value){
                break;
            }
        }

        if ((value-qvalue) < (qvalue - qvalues.get(index-1))){
             return index;
        }
        return index-1;
    }



    public void updateBufferIndices(SortedSet<Integer> newBufferIndices){

        ArrayList<Integer> yesno = new ArrayList<>(totalFrames);

        for(int i=0; i<totalFrames; i++){
            yesno.add(0);
        }
        // clear old series
        selectedBuffers.clear();
        for(Integer newIndex : newBufferIndices){
            selectedBuffers.add(signalSeries.getDataItem(newIndex));
            yesno.set(newIndex, 1);
        }

        StringBuilder bufferLine = new StringBuilder(totalFrames*2);
        for(Integer isBuff : yesno){
            bufferLine.append(String.format(Locale.US, "%d ", isBuff));
        }

        String bufferLineMD5HEX = DigestUtils.md5Hex(bufferLine.toString()).toUpperCase();
        byte[] outputstring = (bufferLineMD5HEX + " " + bufferLine.toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        ByteBuffer outbuff = ByteBuffer.wrap(outputstring);

        int indexOfContentToBeReplaced = secFormat.getBackground_index(); // line that will be written over

        // length is determined by => s.getBytes().length
        if ((outputstring.length-System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
            /*
             * inserting into file
             * 1. write contents I want to keep after insertion point to temp file
             * 2. write content to original file
             * 3. transfer contents from temp back
             * 4. update lines and length for new point
             */
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
        } else {
            try {
                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    private void insertIntoFile(int indexOfContentToBeReplaced, ByteBuffer outbuff) {

        System.out.println("System:: " + System.getProperty("os.name"));
        if (System.getProperty("os.name").contains("Windows") || System.getProperty("os.name").contains("Windows")){
            // write new file
            // delete old file

        } else {
            try {
                int keepAfter = indexOfContentToBeReplaced + 1;
                long oldFileSize = this.file.length();
                long keepAfterHereInBytes = lineNumbers.get(keepAfter);

                File tfile = new File(filename+"~");

                RandomAccessFile rtemp = new RandomAccessFile(tfile, "rw");
                FileChannel tempChannel = rtemp.getChannel();

                fileChannel.transferTo(keepAfterHereInBytes, (oldFileSize-keepAfterHereInBytes), tempChannel);
                tempChannel.close();
                rtemp.close();
                // should be preceded by newline/carriage return
                long offset = lineNumbers.get(indexOfContentToBeReplaced) ;
                fileChannel.truncate(offset);
                fileChannel.write(outbuff, offset); // write new line
                rtemp = new RandomAccessFile(tfile, "r");
                tempChannel = rtemp.getChannel();
                long newOffset = offset + (long)(outbuff.array().length); // length of outbuff includes the new line character
                tempChannel.position(0L);
                fileChannel.transferFrom(tempChannel, newOffset, (oldFileSize-keepAfterHereInBytes));

                tempChannel.close();
                rtemp.close();
                tfile.delete();

                linesAndLength.replace(indexOfContentToBeReplaced, (long)outbuff.array().length - (long)System.lineSeparator().getBytes(StandardCharsets.UTF_8).length); // should be the length of lines without newline character

                long startIndex=0L;
                lineNumbers.clear();
                for (Map.Entry<Integer, Long> entry : linesAndLength.entrySet()) {
                    lineNumbers.add(startIndex);
                    startIndex += entry.getValue() + (long)(System.lineSeparator().getBytes(StandardCharsets.UTF_8).length);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * New line Separator adds an extra bit that is not counted by readline
     *
     * @param newLine String line to parse
     */
    public void updateIZeroLine(String newLine){

        int indexOfContentToBeReplaced = secFormat.getIzero_index(); // line that will be written over

        String newMD5HEX = DigestUtils.md5Hex(newLine).toUpperCase();
        ByteBuffer outbuff = ByteBuffer.wrap((newMD5HEX + " " + newLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        // length is determined by => s.getBytes().length
        if ((outbuff.array().length-System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
        } else {
            try {
                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    public void updateqIqLine(String newLine){

        int indexOfContentToBeReplaced = secFormat.getIntegrated_qIq_index(); // line that will be written over

        String newMD5HEX = DigestUtils.md5Hex(newLine).toUpperCase();
        ByteBuffer outbuff = ByteBuffer.wrap((newMD5HEX + " " + newLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        /*
         * linesAndLength excludes bytes of carriage return/new line
         * length of is determined by => s.getBytes().length
         */
        if ((outbuff.array().length-System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
        } else {
            try {
                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void updateRgLine(String newLine){

        int indexOfContentToBeReplaced = secFormat.getRg_index(); // line that will be written over

        String newMD5HEX = DigestUtils.md5Hex(newLine).toUpperCase();
        ByteBuffer outbuff = ByteBuffer.wrap((newMD5HEX + " " + newLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        /*
         * linesAndLength excludes bytes of carriage return/new line
         * length of is determined by => s.getBytes().length
         */
            if ((outbuff.array().length-System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
                insertIntoFile(indexOfContentToBeReplaced, outbuff);
            } else {
                try {
                    fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
    }


    public void updateSignalLine(String newLine){

        int indexOfContentToBeReplaced = secFormat.getSignal_index(); // line that will be written over

        String newMD5HEX = DigestUtils.md5Hex(newLine).toUpperCase();
        ByteBuffer outbuff = ByteBuffer.wrap((newMD5HEX + " " + newLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        /*
         * linesAndLength excludes bytes of carriage return/new line
         * length of is determined by => s.getBytes().length
         */
        if ((outbuff.array().length - System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
        } else {
            try {
                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateSubtractedFrame(int indexOfFrame, String newSub, String newSubSigmas) {

        int indexOfContentToBeReplaced = secFormat.getSubtracted_intensities_index()  + indexOfFrame; // line that will be written over

        String newMD5HEX = DigestUtils.md5Hex(newSub).toUpperCase();
        byte[] outputstring = (indexOfFrame + " " + newMD5HEX + " " + newSub + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        ByteBuffer outbuff = ByteBuffer.wrap(outputstring);

        /*
         * linesAndLength excludes bytes of carriage return/new line
         * length of is determined by => s.getBytes().length
         */
        if ((outputstring.length-System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
        } else {
            try {
                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        indexOfContentToBeReplaced = secFormat.getSubtracted_intensities_error_index() + indexOfFrame;
        String newSubSigmasMD5HEX = DigestUtils.md5Hex(newSubSigmas).toUpperCase();
        outputstring = (indexOfFrame + " " + newSubSigmasMD5HEX + " " + newSubSigmas + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        outbuff = ByteBuffer.wrap(outputstring);

        if ((outputstring.length-System.lineSeparator().getBytes(StandardCharsets.UTF_8).length) != linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
        } else {
            try {
                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void updateAveragedBuffer(String newaveragedBuffer, String newSigmas) {

        int indexOfContentToBeReplaced = secFormat.getAveraged_buffer_index(); // line that will be written over
        String avgBufferMD5HEX = DigestUtils.md5Hex(newaveragedBuffer).toUpperCase();
        ByteBuffer outbuff = ByteBuffer.wrap((avgBufferMD5HEX + " " + newaveragedBuffer + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));

        // length is determined by => s.getBytes().length
//        if ((outbuff.array().length-1) > linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
//        } else {
//            try {
//                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        String avgBuffererrorMD5HEX = DigestUtils.md5Hex(newSigmas).toUpperCase();
        outbuff = ByteBuffer.wrap((avgBuffererrorMD5HEX + " " + newSigmas + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        indexOfContentToBeReplaced = secFormat.getAveraged_buffer_error_index();

//        if (outbuff.toString().getBytes().length > linesAndLength.get(indexOfContentToBeReplaced) ){
            insertIntoFile(indexOfContentToBeReplaced, outbuff);
//        } else {
//            try {
//                fileChannel.write(outbuff, lineNumbers.get(indexOfContentToBeReplaced));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

    }

    public ArrayList<Double> getQvalues(){ return qvalues; }

    public int getFrameByIndex(int index) { return frame_indices.get(index);}


    public XYSeries getUnsubtractedXYSeries(int frameByIndex) {

        ArrayList<Double> data = this.getUnSubtractedFrameAt(frameByIndex);

        XYSeries tempXY = new XYSeries("temp"); // can load multple in graph as they will have same name/key
        for(int i=0; i<totalQValues; i++){
            tempXY.add(qvalues.get(i), data.get(i));
        }
        return tempXY;
    }

    public String getFilename() {
        return filename;
    }

    public int getBufferCount(){
        int lookAt = secFormat.getBackground_index();

        MappedByteBuffer buffer = null;
        try {
            buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, lineNumbers.get(lookAt), linesAndLength.get(lookAt));
        } catch (IOException e) {
            e.printStackTrace();
        }

        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);
        String[] values = charBuffer.toString().split("\\s+"); // starts with index followed by checksum

        int sum = 0;
        for(int i=1; i<values.length; i++){
            sum += Integer.parseInt(values[i]);
        }

        return (sum);
    }

    public String getFilebase() {
        return filebase;
    }

    public void closeFile(){
        try {
            fileChannel.close();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getSasObjectJSON(){
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

        String sasObjectString = null;
        try {
            sasObjectString = mapper.writeValueAsString(sasObject);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return sasObjectString;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public String getParentPath() {
        return parentPath;
    }

    public String getNew_line_separator(){ return new_line_separator; }
}
