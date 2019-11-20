package version4;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class PDBAtomTest {

    static PDBAtom item;

    @BeforeClass
    public static void setup() {
        String line = "ATOM    390  OD2 ASP A  82     201.128  16.955  44.289  1.00191.97           O";
        item = new PDBAtom(line);
    }
    @Test
    public void getCoords() {
        double[] coords = item.getCoords();
        Assert.assertEquals(201.128, coords[0], 0.001);
        Assert.assertEquals(16.955, coords[1], 0.001);
        Assert.assertEquals(44.289, coords[2], 0.001);
    }

    @Test
    public void getAtomType() {
        String at = item.getAtomType();
        Assert.assertEquals("OD2", at);
    }

    @Test
    public void getAtomicNumber() {
        int at = item.getAtomicNumber();
        Assert.assertEquals(8, at);
    }


}