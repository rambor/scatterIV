package version4.SEC;

import FileManager.LoadedFile;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class SECFileTest {

    private SECFile sec;

    @Test
    public void constructorTest() throws IOException {

        String currentDirectory = System.getProperty("user.dir");
        String filename = "src/test/testData/json.txt";

        File temp = new File(filename);

        sec = new SECFile(new File(filename));

        //assertThat(9, is(c.sum()));
    }

    @Test
    public void constructorTest2() throws IOException {

        String currentDirectory = System.getProperty("user.dir");
        String filename = "src/test/testData/BSA_0p6_mg_per_ml_11_comp.sec";
        sec = new SECFile(new File(filename));

        //assertThat(9, is(c.sum()));
    }

    @Test
    public void subtractionCheck() throws Exception {
        String filename = "src/test/testData/BSA_sec.sec";
        sec = new SECFile(new File(filename));

        File folder = new File("src/test/testData/subtraction");
        File[] listOfFiles = folder.listFiles();

        Arrays.sort(listOfFiles);
        ArrayList<Double> qValues = sec.getQvalues();
        int indexOf;

        for(int i=0; i<listOfFiles.length; i++){
            LoadedFile data = new LoadedFile(listOfFiles[i], i, false);
            ArrayList<Double> iValues = sec.getSubtractedFrameAt(256+i);
            ArrayList<Double> eValues = sec.getSubtractedErrorAtFrame(256+i);


            for(int q=0; q<qValues.size(); q++){
                indexOf = data.allData.indexOf(qValues.get(q));
                if( indexOf > -1){
                    String message = listOfFiles[i].getName() + " " + q + " :: " + qValues.get(q) + " delta :: " + Math.abs(data.allDataError.getY(indexOf).doubleValue());
                    Assert.assertEquals(message, data.allData.getY(indexOf).doubleValue(), iValues.get(q), Math.abs(data.allDataError.getY(indexOf).doubleValue()));
                    // expected - actual
                    message = " SECFILE " + eValues.get(q) + " scat3 " + data.allDataError.getY(indexOf);
                    Assert.assertEquals("Error :: "+message, data.allDataError.getY(indexOf).doubleValue(), eValues.get(q), 0.01);
                }
            }
        }

    }
}