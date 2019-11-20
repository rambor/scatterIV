package FileManager;

import org.jfree.data.xy.XYDataItem;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class LoadedFileTest {


    @Test(expected = IllegalArgumentException.class)
    public void constructorExceptionTest() throws Exception {
        String filename = "src/test/testData/dummy.txd";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);
    }

    /*
     * throw exception if file is empty
     */
    @Test(expected = IllegalArgumentException.class)
    public void constructorEmptyFileExceptionTest() throws Exception {
        String filename = "src/test/testData/dummy.txt";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);
    }

    @Test
    public void constructorTest() throws Exception {
        String filename = "src/test/testData/good_SAXS.dat";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);

        String fitfilename = "src/test/testData/good_SAXS.fit";
        LoadedFile fittemp = new LoadedFile(new File(fitfilename), 0, false);

        // compare the inputs - fit file should have intensities in the error
        int total = temp.allData.getItemCount();
        for(int i=0; i<total; i++){
            XYDataItem item1 = temp.allData.getDataItem(i);
            XYDataItem item1Error = temp.allDataError.getDataItem(i);

            XYDataItem item2Error = fittemp.allDataError.getDataItem(i); // item2error contains the difference between item1 and item1 error
            Assert.assertEquals(item2Error.getYValue(), Math.abs(item1Error.getYValue() - item1.getYValue()), 0.000001);
        }
    }

    @Test
    public void constructorTestWithRemarks() throws Exception {
        String filename = "src/test/testData/weak_data.dat";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);

        // compare the inputs - fit file should have intensities in the error
        int total = temp.allData.getItemCount();
        Assert.assertEquals(2447, total);
        Assert.assertEquals(3.5565714E-03, temp.allData.getX(0).doubleValue(), 0.0000000001);
        Assert.assertEquals(3.5565714E-03, temp.allData.getX(0).doubleValue(), 0.0000000001);
    }


    @Test
    public void constructorTestWithRemarksCheckFirstAndLast() throws Exception {
        String filename = "src/test/testData/weak_data.dat";
        LoadedFile temp = new LoadedFile(new File(filename), 0, false);

        // compare the inputs - fit file should have intensities in the error
        int total = temp.allData.getItemCount();
        Assert.assertEquals(3.5565714E-03, temp.allData.getX(0).doubleValue(), 0.0000000001);
        Assert.assertEquals(4.3982171E-01, temp.allData.getX(total-1).doubleValue(), 0.0000000001);
    }
}