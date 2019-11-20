package version4;

/**
 * Created by robertrambo on 18/01/2016.
 */
public class StandardObservations {
    private double obs;
    private double q;
    private double sigma;
    private double standardObs;
    private double residual;

    public StandardObservations(double q, double obs, double sigma){
        this.q = q;
        this.obs = obs;
        this.sigma = sigma;
    }

    public void setStandardizedObs(double s_n, double median){
        this.residual = obs - median;
        this.standardObs = residual*s_n;
    }

    public double getStandardizedObs(){
        return standardObs;
    }

    public double getQ(){
        return q;
    }
    public double getObs(){
        return obs;
    }
    public double getSigma(){
        return sigma;
    }
}