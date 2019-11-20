package version4;

import FileManager.LoadedFile;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.YInterval;
import org.jfree.data.xy.YIntervalDataItem;
import org.jfree.data.xy.YIntervalSeries;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import version4.sasCIF.SasObjectForm;

import java.io.File;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class DatasetTest {

    static String filename = "src/test/testData/weak_data.dat";
    static Dataset weak;
    static Dataset guinier;
    @BeforeClass
    public static void setup() {
        LogIt.log(Level.INFO, "startup - creating Test Object from :: " + filename);
        LoadedFile temp = null;
        try {
            temp = new LoadedFile(new File(filename), 0, false);
        } catch (Exception e) {

            System.out.println(e.getMessage());
        }
        weak = new Dataset(temp.allData, temp.allDataError, temp.filebase,0);
        guinier = new Dataset(temp.allData, temp.allDataError, temp.filebase,0, true);
    }

    /**
     * return the base name without extensions
     */
    @Test
    public void getFileName() {
        Assert.assertEquals(weak.getFileName(), "weak_data");
    }


    @Test
    public void getGuinierRg() {
        double value = guinier.getGuinierRg();
        Assert.assertTrue(value > 28);
    }

    @Test
    public void getGuinierRgerror() {
        double value = guinier.getGuinierRgerror();
        Assert.assertTrue(value > 0.01);
    }

    @Test
    public void getGuinierIzero() {
        double value = guinier.getGuinierIzero();
        Assert.assertTrue(value > 0.001);
    }

    @Test
    public void getGuinierIzeroError() {
        double value = guinier.getGuinierRgerror();
        Assert.assertTrue(value > 0.0001);
    }

    @Test
    public void getAllDataError() {
        int total = weak.getAllDataError().getItemCount();
        Assert.assertEquals(2447,total);
    }

    @Test
    public void getAllData() {
        int total = weak.getAllData().getItemCount();
        Assert.assertEquals(2447,total);

    }

    @Test
    public void getData() {
        int total = weak.getData().getItemCount();
        Assert.assertTrue(total < 2447);
    }

    @Test
    public void scalePlottedErrorData() {

        int beforesize = guinier.getPlottedErrors().getItemCount();
        guinier.scalePlottedErrorData();
        int aftersize = guinier.getPlottedErrors().getItemCount();
        Assert.assertTrue(beforesize < aftersize);

        YIntervalSeries errors = guinier.getPlottedErrors();
        for(int i=0 ; i<errors.getItemCount(); i++){
            YIntervalDataItem err = (YIntervalDataItem)errors.getDataItem(i);
            XYDataItem item = guinier.getAllData().getDataItem(i);
            Assert.assertTrue(item.getYValue()*item.getXValue() < err.getYHighValue());
            Assert.assertTrue(item.getYValue()*item.getXValue() > err.getYLowValue());
        }
    }


    @Test
    public void getAllDataYError() {

        YIntervalSeries errors = guinier.getAllDataYError();
        for(int i=0 ; i<errors.getItemCount(); i++){
            YIntervalDataItem err = (YIntervalDataItem)errors.getDataItem(i);
            Assert.assertTrue(err.getYLowValue()< err.getYHighValue());
        }
    }


    @Test
    public void setSasObject() throws Exception {

        String filename = "src/test/testData/json.txt";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);
        if (temp.hasJson()){
            Dataset tempDataset = new Dataset(temp.allData, temp.allDataError, temp.filebase, 0, false);
            tempDataset.setSasObject(temp.getJSONString());

            Assert.assertEquals("Radiation Type", "XRAY", tempDataset.getSasObject().getSasBeam().getRadiation_type());
            Assert.assertEquals(12400, tempDataset.getSasObject().getSasBeam().getRadiation_wavelength(), 0.1);
        }

    }


    @Test
    public void getSasObject() throws Exception {
        Assert.assertFalse(weak.getSasObject() instanceof Object);

        String filename = "src/test/testData/json.txt";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);
        if (temp.hasJson()){
            Dataset tempDataset = new Dataset(temp.allData, temp.allDataError, temp.filebase, 0, false);
            tempDataset.setSasObject(temp.getJSONString());
            Assert.assertTrue(tempDataset.getSasObject() instanceof Object);
        }
    }
}