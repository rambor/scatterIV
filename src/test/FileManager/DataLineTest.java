package FileManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.xml.crypto.Data;
import java.io.*;
import java.sql.SQLOutput;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class DataLineTest {

    ArrayList<DataLine> dataLines;
    ArrayList<String> neutronLines;

    @Before
    public void setUp() throws Exception {
        dataLines = new ArrayList<>();
        String currentDirectory = System.getProperty("user.dir");
        String filename = "src/test/testData/good_SAXS.dat";
        String sansname = "src/test/testData/sans.dat";

        try {

            BufferedReader br = new BufferedReader(new FileReader(filename));
            String strLine;

            try{
                while ((strLine = br.readLine()) != null) {
                    try {
                        //DataLine dataPoint = new DataLine(strLine);
                        dataLines.add(new DataLine(strLine));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch (IOException ex) {
                System.err.println("File Index out of bounds");
            }

            br.close();

            br = new BufferedReader(new FileReader(sansname));
            neutronLines = new ArrayList<>();

            try{
                while ((strLine = br.readLine()) != null) {
                    try {
                        //DataLine dataPoint = new DataLine(strLine);
                        neutronLines.add(strLine);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch (IOException ex) {
                System.err.println("File Index out of bounds");
            }

            br.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }


    @Test
    public void parse3ColumnCSVSAXS() throws Exception {
        String testLine = "0.001,1.01076E2,0.01";

        DataLine values = new DataLine(testLine);
        Assert.assertEquals(0.001, values.getq(), 0.0001);
        Assert.assertEquals(1.01076E2, values.getI(), 0.001);

        testLine = "0.001, 1.01076E2, 0.01";

        values = new DataLine(testLine);
        Assert.assertEquals(0.001, values.getq(), 0.0001);
        Assert.assertEquals(1.01076E2, values.getI(), 0.001);
    }

    @Test
    public void parse4ColumnSAXSFIT(){

    }

    @Test
    public void parse4ColumnSANS() {
        int total = neutronLines.size();
        ArrayList<DataLine> sansLines = new ArrayList<>();
        double firstI = Double.parseDouble("6.220849e+00");
        double lastI = Double.parseDouble("1.007839e-01");

        for(int i=0; i<total; i++ ){
            try {
                sansLines.add(new DataLine(neutronLines.get(i)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Assert.assertEquals(0.00416, sansLines.get(0).getq(), 0.00001);
        Assert.assertEquals(firstI, sansLines.get(0).getI(), 0.000001);
        Assert.assertEquals(6.988401e-04, sansLines.get(0).getQerror(), 0.000001);

        Assert.assertEquals(0.72191, sansLines.get(sansLines.size()-1).getq(), 0.00001);
        Assert.assertEquals(lastI, sansLines.get(sansLines.size()-1).getI(), 0.000001);
        Assert.assertEquals(1.991133e-02, sansLines.get(sansLines.size()-1).getQerror(), 0.000001);
    }

    @Test
    public void partseBRML(){

    }

    @Test
    public void getq() {
        double firstq = dataLines.get(0).getq();
        double lastq = dataLines.get(dataLines.size()-1).getq();
        // 0.0114352
        Assert.assertEquals(1.14352E-02, firstq, 0.0000001);
        Assert.assertEquals(3.71159E-01, lastq, 0.0000001);
    }

    @Test
    public void getI() {
        double firstq = dataLines.get(0).getI();
        double lastq = dataLines.get(dataLines.size()-1).getI();
        double actualLastI = Double.parseDouble("2.91866E-03");
        // 0.0114352
        Assert.assertEquals(6.1976, firstq, 0.0001);
        Assert.assertEquals(actualLastI, lastq, 0.0000001);
    }


    @Test
    public void setE() {
        DataLine obj = new DataLine(0,0,0,false);
        obj.setE(0.098356183);
        Assert.assertEquals(0.098356183, obj.getE(), 0.000000001);
    }

    @Test
    public void getTest() {
        DataLine obj = new DataLine(0,0,0,false);
        Assert.assertFalse(obj.getTest());
    }
}