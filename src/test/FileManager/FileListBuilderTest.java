package FileManager;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class FileListBuilderTest {

    /*
     * throw exception if file is empty
     */
    @Test
    public void constructorTest() throws Exception {
        String filename = "src/test/testData/sec/23543_BSA_0p6_mg_per_ml_11.dat";
        File files[] = {new File(filename)};
        WorkingDirectory WORKING_DIRECTORY = new WorkingDirectory();
        FileListBuilder builder = new FileListBuilder(files[0], WORKING_DIRECTORY.getWorkingDirectory());

        File[] temp = builder.getFoundFiles();

        Assert.assertEquals("Message file coutn mismatch", 794, temp.length);
    }


    @Test
    public void getFoundFiles() {
    }
}