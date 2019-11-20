package version4.SEC;

import FileManager.DataLine;
import FileManager.FileListBuilder;
import FileManager.ReceivedDroppedFiles;
import FileManager.WorkingDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfree.data.xy.XYSeries;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import version4.Collection;
import version4.Dataset;
import version4.LogIt;
import version4.tableModels.SampleBufferElement;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class SECBuilderTest {

    static File[] sortedFiles;

    @BeforeClass
    public static void setup() throws Exception{
        String filename = "src/test/testData/sec/23543_BSA_0p6_mg_per_ml_11.dat";
        File files[] = {new File(filename)};
        WorkingDirectory WORKING_DIRECTORY = new WorkingDirectory();
        FileListBuilder builder = new FileListBuilder(files[0], WORKING_DIRECTORY.getWorkingDirectory());
        sortedFiles = builder.getFoundFiles();
    }

    @Test
    public void constructorTest() throws Exception {
        Assert.assertEquals("Total SEC files does not match", 794, sortedFiles.length);
        Collection collection = new Collection("test sec");
        DefaultListModel<SampleBufferElement> sampleFilesModel = new DefaultListModel<SampleBufferElement>();
        WorkingDirectory WORKING_DIRECTORY = new WorkingDirectory();
        JLabel status = new JLabel();
        JProgressBar progressBar = new JProgressBar();

        ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(sortedFiles,
                collection,
                sampleFilesModel,
                status,
                false,
                true,
                progressBar,
                WORKING_DIRECTORY.getWorkingDirectory());

        rec1.run();
        String name="";
        SECBuilder secObj = new SECBuilder(collection, status, progressBar, name, "",1.004d);

        secObj.doInBackground();
    }

    @Test
    public void writeToSECFileTest() throws IOException {
        String filename = "src/test/testData/BSA_0p6_mg_per_ml_11_comp.sec";
        SECFile sec = new SECFile(new File(filename));

        sec.getTotalFrames();
        SecFormat secObject = sec.getSasObject().getSecFormat();

        sec.getUnSubtractedErrorFrameAt(0);
        //sec.getUnSubtractedFrameAt(0);
       // sec.getSubtractedFrameAt(0);

    }

    @Test
    public void averageWithoutScaling() throws IOException {

        String filename = "src/test/testData/BSA_sec.sec";
        SECFile sec = new SECFile(new File(filename));

        ArrayList<XYSeries> keptData = new ArrayList<>();
        ArrayList<XYSeries> keptErrors = new ArrayList<>();
        ArrayList<Double> qvalues = sec.getQvalues();
        int totalq = qvalues.size();

        int startIndex = 50;
        int endIndex = 213;

        int count = 0;
        for(int i=startIndex; i<endIndex; i++){
            ArrayList<Double> frame = sec.getUnSubtractedFrameAt(i);
            ArrayList<Double> frameErr = sec.getUnSubtractedErrorFrameAt(i);
            keptData.add(new XYSeries("frame_" + i));
            keptErrors.add(new XYSeries("frame_" + i));
            for(int q=0; q<totalq; q++){
                keptData.get(count).add(qvalues.get(q), frame.get(q));
                keptErrors.get(count).add(qvalues.get(q), frameErr.get(q));
            }
            count +=1;
        }

        XYSeries buffer = new XYSeries("averaged buffer");
        XYSeries bufferError = new XYSeries("averaged buffer errors");

        Collection collection = new Collection("test sec");
        DefaultListModel<SampleBufferElement> sampleFilesModel = new DefaultListModel<SampleBufferElement>();
        WorkingDirectory WORKING_DIRECTORY = new WorkingDirectory();
        JLabel status = new JLabel();
        JProgressBar progressBar = new JProgressBar();

        ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(sortedFiles,
                collection,
                sampleFilesModel,
                status,
                false,
                true,
                progressBar,
                WORKING_DIRECTORY.getWorkingDirectory());

        rec1.run();
        String name="";
        SECBuilder secObj = new SECBuilder(collection, status, progressBar, name, "",1.004d);

        secObj.averageWithoutScaling(keptData, keptErrors, buffer, bufferError);

        String actualFile = "src/test/testData/bsa_11_buffer_frames_22807_22971.txt";
        BufferedReader br = new BufferedReader(new FileReader(new File(actualFile)));
        Pattern separator = Pattern.compile("\\s|;|,\\s|,");


        String strLine;
        ArrayList<Double> actualQ = new ArrayList<>();
        ArrayList<Double> actualI = new ArrayList<>();

        while ((strLine = br.readLine()) != null) {
            String newString = strLine.replaceAll( "[\\s\\t]+", " " );
            String trimmed = newString.trim();
            String[] row = separator.split(trimmed);

            actualQ.add(Double.parseDouble(row[1]));
            actualI.add(Double.parseDouble(row[2]));
        }

        for(int i=0; i<buffer.getItemCount(); i++){
            if (buffer.indexOf(actualQ.get(i)) > -1){
                String message = i + " QVAL " + actualQ.get(i);
                Assert.assertEquals(message, actualI.get(i), buffer.getY(i).doubleValue(), 0.001);
            }
        }


    }
}