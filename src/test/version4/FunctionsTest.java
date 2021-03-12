package version4;

import org.apache.commons.math3.util.MathArrays;
import org.jfree.data.xy.XYSeries;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class FunctionsTest {


    @Test
    public void interpolateTest() {
        // XYSeries data, double point, double scaleFactor
        System.out.println("Interpolating");
        XYSeries data = new XYSeries("");
        data.add(0,1);
        data.add(1,1);
        data.add(2,1);
        data.add(3,2);
        data.add(4,2);
        data.add(5,1);
        data.add(6,1);
        data.add(7,1);
        data.add(8,1);
        Double[] values = Functions.interpolate(data, 3.5);
        Assert.assertEquals(2.0, values[1], 0.23);
    }

    @Test
    public void interpolateArrayTest() {
        // XYSeries data, double point, double scaleFactor
        double xvals[] ={0,1,2,3,4,5,6,7,8};
        double yvals[] ={1,1,1,2,2,1,1,1,1};
        Double[] values = Functions.interpolate(xvals, yvals, 3.5);
        Assert.assertEquals(2.0, values[1], 0.23);
    }

}