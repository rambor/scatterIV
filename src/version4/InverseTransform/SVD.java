package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.ejml.data.SingularMatrixException;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;


public class SVD extends IndirectFT {

    private double del_r;
    double[] r_vector;
    int r_vector_size;


    // Dataset should be standardized and in form of [q, q*I(q)]
    public SVD(XYSeries dataset, XYSeries errors, double dmax, double qmax, boolean includeBackground) {
        super(dataset, errors, dmax, qmax, 0, includeBackground);
        this.createDesignMatrix(this.data);
        this.solve();
    }



    /**
     * Dataset and errors are already standardized
     *
     * @param scaledqIqdataset standardized data
     * @param scaledqIqErrors
     * @param dmax
     * @param qmax
     * @param lambda
     * @param includeBackground
     * @param stdmin
     * @param stdscale
     */
    public SVD(
            XYSeries scaledqIqdataset,
            XYSeries scaledqIqErrors,
            double dmax,
            double qmax,
            double lambda,
            boolean includeBackground,
            double stdmin,
            double stdscale){

        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, false, stdmin, stdscale);

        //this.standardizeErrors(); // extract variances from errors
        XYDataItem tempData;
        standardVariance = new XYSeries("standardized error");
        int totalItems = scaledqIqdataset.getItemCount();
        double temperrorvalue;

        for(int r=0; r<totalItems; r++){
            tempData = scaledqIqdataset.getDataItem(r);
            temperrorvalue = scaledqIqErrors.getY(r).doubleValue(); // already in form of q*I(q)/scale
            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // q_times_Iq_scaled
        }


        this.createDesignMatrix(scaledqIqdataset);
        this.solve();

        this.setModelUsed("DIRECT SVD");
    }

    /**
     * copy constructor
     */
    public SVD(SVD original){
        super(original);

        this.del_r = original.del_r;
        this.r_vector = original.r_vector.clone();
        this.r_vector_size = original.r_vector_size;

    }

    @Override
    void calculateIzeroRg() {
        double tempRgSum = 0, tempRg2Sum=0, xaverage=0;
        //del_r = prDistribution.getX(2).doubleValue() - prDistribution.getX(1).doubleValue() ;

        XYDataItem item;
        for(int i=1; i<totalInDistribution-1; i++){ // exclude last two points?
            item = prDistribution.getDataItem(i);
            double rvalue = item.getXValue();
            tempRg2Sum += rvalue*rvalue*item.getYValue()*del_r;
            tempRgSum += item.getYValue()*del_r; // width x height => area
            xaverage += rvalue*item.getYValue()*del_r;
        }

        rg = Math.sqrt(0.5*tempRg2Sum/tempRgSum);

        double sum = coefficients[0];
        for(int j=0; j< r_vector_size; j++){
            sum +=  coefficients[j+1];
        }
        //System.out.println("SUM " + sum + " tempRgSum " + tempRgSum);
        //izero = tempRgSum*standardizedScale+standardizedMin;
        izero = sum*standardizedScale+standardizedMin;
        rAverage = xaverage/tempRgSum;
        area = tempRgSum;
    }

    @Override
    void setPrDistribution() {
        prDistribution = new XYSeries("PRDistribution");
        prDistributionForFitting = new XYSeries("OutputPrDistribution");

        totalInDistribution = r_vector_size+2;

        for(int i=0; i<totalInDistribution; i++){ // values in r_vector represent the midpoint or increments of (i+0.5)*del_r

            if ( i == 0 ) { // interleaved r-value (even)
                prDistribution.add(0, 0);
                //prDistributionForFitting.add(0,0);
            } else if (i == (totalInDistribution-1)) {
                prDistribution.add(dmax, 0);
                //prDistributionForFitting.add(dmax,0);
            } else { // odd
                int index = i-1;
                double value = coefficients[index+1];
                prDistribution.add(r_vector[index], value);
                prDistributionForFitting.add(r_vector[index], value);
                // if using background, add to each term?
            }
        }

        // calculate total Diff
        scoreDistribution(del_r);
        setHeaderDetails();
        setSplineFunction();
    }


    private void setHeaderDetails(){
        this.description  = String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS DIRECT INVERSE FOURIER TRANSFORM OF I(q) %n");
        this.description  = String.format("REMARK 265  SHANNON LIMITED MOORE-PENROSE PSEUDO-INVERSE MATRIX INVERSION %n");
        this.description += String.format("REMARK 265  COEFFICIENTS ARE THE HISTOGRAM HEIGHTS WITH EQUAL BIN WIDTHS %n");
        this.description += String.format("REMARK 265 %n");
        this.description += String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", (dmax/(double)ns));
        this.description += String.format("REMARK 265            DISTRIBUTION SCORE : %.4f %n", prScore);
        this.description += String.format("REMARK 265 %n");
        this.description += String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n");
    }


    /**
     * call this function before outputing pr distribution
     */
    private void setSplineFunction(){

        double[] totalrvalues = new double[totalInDistribution];
        double[] totalPrvalues = new double[totalInDistribution];


        // coefficients start at r_1 > 0 and ends at midpoint of last bin which is less dmax
        for(int i=0; i < totalInDistribution; i++){
            XYDataItem item = prDistribution.getDataItem(i);
            totalPrvalues[i] = item.getYValue();
            totalrvalues[i] = item.getXValue();
        }

        SplineInterpolator spline = new SplineInterpolator();
        splineFunction = spline.interpolate(totalrvalues, totalPrvalues);
    }


    @Override
    public double calculateQIQ(double qvalue) {
        double sum = coefficients[0];
        double rvalue;
        for(int j=0; j< r_vector_size; j++){
            rvalue = r_vector[j];
            sum +=  coefficients[j+1]*FastMath.sin(rvalue*qvalue) / rvalue;
        }

        return sum*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return (this.calculateQIQ(qvalue))/qvalue;
    }


    @Override
    public double calculatePofRAtR(double r_value, double scale) {
        return (inv2PI2*standardizedScale)*splineFunction.value(r_value)*scale;
    }

    @Override
    public void estimateErrors(XYSeries fittedData) {

    }

    @Override
    public void normalizeDistribution() {

    }

    @Override
    public void createDesignMatrix(XYSeries datasetInuse){

        ns = (int) Math.ceil(qmax*dmax*INV_PI);  //

        coeffs_size = this.includeBackground ? ns + 1 : ns;   //+1 for constant background, +1 to include dmax in r_vector list
        rows = datasetInuse.getItemCount();    // rows

        r_vector_size = ns; // no background implies coeffs_size == ns

        //del_r = Math.PI/qmax; // dmax is based del_r*ns
        del_r = dmax/(double)(ns);
        bin_width = del_r;

        // if I think I can squeeze out one more Shannon Number, then I need to define del_r by dmax/ns+1
        //double del_r = dmax/(double)ns;
        r_vector = new double[r_vector_size];

        for(int i=0; i < r_vector_size; i++){ // last bin should be dmax
            //r_vector[i] = (i+1)*del_r;
            r_vector[i] = (0.5 + i)*del_r; // dmax is not represented in this set
        }
        // what happens if the last bin is midpoint is less than dmax?
        /*
         * create A matrix (design Matrix)
         */
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        /*
         * y_vector is q*I(q) data
         */
        y_vector = new SimpleMatrix(rows,1);

        if (!includeBackground) { // no constant background

            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);

                for(int col=0; col < coeffs_size; col++){
                    double r_value = r_vector[col];
                    a_matrix.set(row, col, FastMath.sin(r_value*tempData.getXValue()) / r_value);
                }
                y_vector.set(row,0,tempData.getYValue()); //set data vector
            }

        } else {
            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);

                for(int col=0; col < coeffs_size; col++){
                    if (col == 0){ // constant background term
                        //a_matrix.set(row, 0, tempData.getXValue());
                        a_matrix.set(row, 0, 1);
                    } else { // for col >= 1
                        double r_value = r_vector[col-1];
                        a_matrix.set(row, col, FastMath.sin(r_value*tempData.getXValue()) / r_value);
                    }
                }
                y_vector.set(row,0,tempData.getYValue()); //set data vector
            }
        }

    }


    void solve(){
        try {
            am_vector = a_matrix.solve(y_vector);

            SimpleMatrix m = a_matrix.pseudoInverse();

            SimpleMatrix diag =  a_matrix.svd().getW().diag();
            //diag.transpose().print();

//            double max = diag.get(0,0);
//            int totaltogo = diag.getNumElements();
//            ArrayList<Double> cond = new ArrayList<>();
//            System.out.println("condition number -- ");
//            for(int i=0; i<totaltogo; i++){
//                cond.add(max/diag.get(i));
//                System.out.println(i + " " + max/diag.get(i));
//            }


//            System.out.println("Second derivative -- ");
//
//            totaltogo = cond.size()-1;
//            for(int i=1; i<totaltogo; i++){
//                double deriv  = cond.get(i+1) -2*cond.get(i) + cond.get(i-1);
//                System.out.println((i+1) + "  " + deriv);
//            }


        } catch ( SingularMatrixException e ) {
            throw new IllegalArgumentException("Singular matrix");
        }

        totalCoefficients = includeBackground ? coeffs_size: coeffs_size + 1;

        coefficients = new double[totalCoefficients];

        if (!includeBackground){
            coefficients[0] = 0; // set background to 0
            for (int j=1; j < totalCoefficients; j++){
                coefficients[j] = am_vector.get(j-1,0);
                //System.out.println(j + " COEFFS " + coefficients[j]);
            }
        } else {
            for (int j=0; j < coeffs_size; j++){
                coefficients[j] = am_vector.get(j,0);
            }
        }

        this.setPrDistribution();
        this.calculateIzeroRg();
        // I_calc based standardized data
        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        coeffs_size = coefficients.length; // this resets the coefficients to possibly include background term as first element

        // calculate residuals MLE (maximum likelihood estimator)
//        finalScore = 0;
//        for(int i=0; i < rows; i++){
//            double diff = tempResiduals.get(i,0);
//            finalScore += diff*diff;
//        }
//        finalScore *= 1.0/(double)rows;
    }


}
