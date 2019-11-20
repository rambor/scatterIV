package version4;

import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYSeries;

public class Interpolator {

    public double point;
    public double interpolatedValue;
    public double stderror;
    private XYSeries data;
    private XYSeries error;

    public Interpolator(XYSeries data, XYSeries error, double point){
        //Kriging interpolation, use input log10 data
        this.data = data;
        this.error = error;
        this.point = point;

        this.interpolate();
    }



    private void interpolate(){
        int [] z = new int[6];
        int index=0;
        //loop over data to find the smalllest q rather than the point

        for (int i=0; i< data.getItemCount(); i++) {
            if (data.getX(i).doubleValue() > point){
                index = i;
                break;
            }
        }

        // if index is last point, need to backoff to estimate
        if (index == data.getItemCount()-1){
            index = data.getItemCount()-3;
        }


        if (index <=1){
            for (int k=0; k<6; k++){
                z[k]=index+k;
            }
        } else if (index >= data.getItemCount()-3){
            z[0]=data.getItemCount()-6;
            z[1]=data.getItemCount()-5;
            z[2]=data.getItemCount()-4;
            z[3]=data.getItemCount()-3;
            z[4]=data.getItemCount()-2;
            z[5]=data.getItemCount()-1;
        } else { //Decrement index by -2 to have at least two points before the interpolated q value
            for (int k=-2; k<4; k++){
                z[k+2]=index+k;
            }
        }



        double scale = data.getX(z[5]).doubleValue() - data.getX(z[0]).doubleValue();
        double stdev_esitmate = 0;

        SimpleMatrix c_m = new SimpleMatrix(6,6);
        //this might be (1,6)
        SimpleMatrix z_m = new SimpleMatrix(6,1);
        SimpleMatrix one = new SimpleMatrix(6, 1);
        for (int m=0; m<6; m++){
            one.set(m, 0, 1);
            double anchor = data.getX(z[m]).doubleValue();
            //Use anti-log data
            z_m.set(m, 0, data.getY(z[m]).doubleValue());
            stdev_esitmate += error.getY(z[m]).doubleValue();

            for (int n=0; n<6; n++){
                c_m.set(m, n, 0.96*Math.exp(-1*(Math.pow(( (anchor - data.getX(z[n]).doubleValue())/scale),2))));

            }
        }

        SimpleMatrix d_m = new SimpleMatrix(6,1);
        for (int m=0; m < 6; m++){
            d_m.set(m, 0, 0.96*Math.exp( -1*(Math.pow(((point-data.getX(z[m]).doubleValue())/scale),2))));
        }

        double mu = ((one.transpose().mult(c_m.invert())).mult(z_m).get(0))/(one.transpose().mult(c_m.invert().mult(one))).get(0);

        interpolatedValue = mu+(d_m.transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(mu)))).get(0);

//        double sigma_2 = ((z_m.minus(one.scale(interpolatedValue))).transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(interpolatedValue)))).get(0)/6;
//        double sigma_d = ((d_m.transpose().mult(c_m.invert())).mult(d_m)).get(0);
//        double tmp1 = sigma_2*(1-sigma_d);

        stderror = stdev_esitmate/6.0;
    }
}
