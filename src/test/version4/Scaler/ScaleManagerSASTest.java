package version4.Scaler;

import org.junit.Assert;
import org.junit.Test;
import version4.SEC.SECFile;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class ScaleManagerSASTest {

    @Test
    public void setUpperLowerQLimits() throws IOException {
        String currentDirectory = System.getProperty("user.dir");
        String filename = "src/test/testData/BSA_sec.sec";
        SECFile sec = new SECFile(new File(filename));

        double low = 0.3333d;
        double high = 0.9999d;
        JLabel status = new JLabel();
        JProgressBar progressBar = new JProgressBar();

        ScaleManagerSAS temp = new ScaleManagerSAS(250, 270, sec, progressBar, status, true);
        temp.setUpperLowerQLimits(low, high);

        Assert.assertEquals(low, temp.getLower(), 0.0001);
    }

    @Test
    public void indexCheck(){

    }

    @Test
    public void doInBackground() throws IOException {
        String currentDirectory = System.getProperty("user.dir");
        String filename = "src/test/testData/BSA_sec.sec";
        SECFile sec = new SECFile(new File(filename));

        double low = 0.01d;
        double high = 0.219999d;
        JLabel status = new JLabel();
        JProgressBar progressBar = new JProgressBar();

        ScaleManagerSAS temp = new ScaleManagerSAS(293, 317, sec, progressBar, status, true);
        temp.setUpperLowerQLimits(low, high);

        temp.run();
    }

    @Test
    public void checkAgainstScat3() throws IOException {
        String currentDirectory = System.getProperty("user.dir");
        String filename = "src/test/testData/BSA_sec.sec";
        SECFile sec = new SECFile(new File(filename));

        double low = 0.01d;
        double high = 0.14d;
        JLabel status = new JLabel();
        JProgressBar progressBar = new JProgressBar();

        ScaleManagerSAS temp = new ScaleManagerSAS(295, 320, sec, progressBar, status, true);
        temp.setUpperLowerQLimits(low, high);
        temp.run();

        ArrayList<Double> sf = temp.getScaleFactors();

        System.out.println("Printing scale factors ");
        int count = 0;
        for(Double val : sf){
            System.out.println(count + " " + val);
            count++;
        }
    }
}