package version4.plots;

import org.junit.Assert;
import org.junit.Test;
import version4.SEC.SECFile;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class DWSimilarityPlotTest {

    /*
     * throw exception if file is empty
     */
    @Test
    public void constructorTest() throws Exception {
        String filename = "src/test/testData/BSA_0p6_mg_per_ml_11_comp.sec";
        SECFile sec = new SECFile(new File(filename));
        DWSimilarityPlot tempDW = new DWSimilarityPlot(250, 350, 0.01, 0.27, sec, new JLabel("empty"), new JProgressBar());

        Assert.assertEquals(sec.getTotalFrames(), tempDW.getTotalFrames());
    }


    @Test
    public void doInBackground() throws IOException {

        String filename = "src/test/testData/BSA_0p6_mg_per_ml_11_comp.sec";
        SECFile sec = new SECFile(new File(filename));
        DWSimilarityPlot tempDW = new DWSimilarityPlot(250, 350, 0.01, 0.27, sec, new JLabel("empty"), new JProgressBar());

        try {
            tempDW.doInBackground();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}