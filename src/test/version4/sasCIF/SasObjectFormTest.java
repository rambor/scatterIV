package version4.sasCIF;

import FileManager.LoadedFile;
import org.jfree.data.xy.XYDataItem;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import version4.Dataset;

import java.awt.GraphicsEnvironment;
import java.io.File;

import static org.junit.Assert.*;

public class SasObjectFormTest {

    @Test
    public void constructorTest() throws Exception {
        // the form is a JDialog, which needs a display. The LNP tests switch the shared test JVM to
        // headless (java.awt.headless=true) and that cannot be undone once AWT has started, so when
        // they run first this test is skipped rather than failed.
        Assume.assumeFalse("needs a display (headless JVM)", GraphicsEnvironment.isHeadless());
        String filename = "src/test/testData/json.txt";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);
        if (temp.hasJson()){
            Dataset tempDataset = new Dataset(temp.allData, temp.allDataError, temp.filebase, 0, false);
            tempDataset.setSasObject(temp.getJSONString());
            SasObjectForm objectForm = new SasObjectForm(tempDataset.getSasObject(), true);
        }
    }
}