package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_FDRM;
import org.ejml.simple.SimpleMatrix;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import version4.Functions;
import java.util.ArrayList;
import java.util.Random;

public class SineIntegralTransform extends IndirectFT {

    private double del_r;
    double[] r_vector;
    int r_vector_size;
    boolean positiveOnly;

    // Dataset should be standardized and in form of [q, q*I(q)]
    public SineIntegralTransform(XYSeries dataset, XYSeries errors, double dmax, double qmax, double lambda, boolean includeBackground, boolean positiveOnly) {
        super(dataset, errors, dmax, qmax, lambda, includeBackground);
        this.createDesignMatrix(this.data);
        this.positiveOnly = positiveOnly;

        if (positiveOnly){
            rambo_coeffs_L1_positive_only();
            if (includeBackground){
                this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY + BKGRND");
            } else {
                this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY");
            }
           // System.out.println(this.getModelUsed() + " BKG " + includeBackground);
        } else {
            this.rambo_coeffs_L1();
            if (includeBackground){
                this.setModelUsed("DIRECT L1-NORM + BKGRND");
            } else {
                this.setModelUsed("DIRECT L1-NORM ONLY");
            }

            // System.out.println(this.getModelUsed() + " BKG " + includeBackground);
        }
    }


    public SineIntegralTransform(
            XYSeries scaledqIqdataset,
            XYSeries scaledqIqErrors,
            double dmax,
            double qmax,
            double lambda,
            boolean includeBackground,
            boolean positiveOnly,
            double stdmin,
            double stdscale){

        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, includeBackground, stdmin, stdscale);

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
        this.positiveOnly = positiveOnly;
        if (positiveOnly){
            rambo_coeffs_L1_positive_only();
            if (includeBackground){
                this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY + BKGRND");
            } else {
                this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY");
            }
        } else {
            this.rambo_coeffs_L1();

            if (includeBackground){
                this.setModelUsed("DIRECT L1-NORM + BKGRND");
            } else {
                this.setModelUsed("DIRECT L1-NORM ONLY");
            }
        }
    }


    /**
     *
     * @param scaledqIqdataset
     * @param scaledqIqErrors
     * @param priors
     * @param dmax
     * @param qmax
     * @param lambda
     * @param includeBackground
     * @param positiveOnly
     * @param stdmin
     * @param stdscale
     */
    public SineIntegralTransform(
            XYSeries scaledqIqdataset,
            XYSeries scaledqIqErrors,
            double[] priors,
            double dmax,
            double qmax,
            double lambda,
            boolean includeBackground,
            boolean positiveOnly,
            double stdmin,
            double stdscale){

        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, includeBackground, stdmin, stdscale);
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

        this.prior_coefficients = priors.clone();
        priorExists = false;

        this.positiveOnly = positiveOnly;
        if (positiveOnly){
            rambo_coeffs_L1_positive_only();
            if (includeBackground){
                this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY + BKGRND");
            } else {
                this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY");
            }
        } else {
            this.rambo_coeffs_L1();

            if (includeBackground){
                this.setModelUsed("DIRECT L1-NORM + BKGRND");
            } else {
                this.setModelUsed("DIRECT L1-NORM ONLY");
            }
        }
    }


    /**
     * Datasets and errors are already standardized
     * @param dataset
     * @param errors
     * @param dmax
     * @param qmax
     * @param lambda
     * @param stdmin
     * @param stdscale
     * @param useBackground
     */
    public SineIntegralTransform(
            XYSeries dataset,
            XYSeries errors,
            double dmax,
            double qmax,
            double lambda,
            double stdmin,
            double stdscale,
            boolean useBackground){

        super(dataset, errors, dmax, qmax, lambda, useBackground, stdmin, stdscale);
        //this.standardizeErrors(); // extract variances from errors
        standardVariance = new XYSeries("standardized error");
        int totalItems = data.getItemCount();
        for(int r=0; r<totalItems; r++){
            XYDataItem tempData = data.getDataItem(r);
            double temperrorvalue = errors.getY(r).doubleValue(); // already in form of q*I(q)/scale
            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // q_times_Iq_scaled
        }

        this.createDesignMatrix(dataset);

        this.positiveOnly = true;
        if (positiveOnly){
            rambo_coeffs_L1_positive_only();
            this.setModelUsed("DIRECT L1-NORM POSITIVE ONLY");
        } else {
            this.rambo_coeffs_L1();
            this.setModelUsed("DIRECT L1-NORM");
            // System.out.println(this.getModelUsed() + " " + includeBackground);
        }

    }


    /**
     * Copy constructor.
     */
    public SineIntegralTransform(SineIntegralTransform toCopy) {
        super(toCopy);
        //this(toCopy.data, toCopy.errors, toCopy.dmax, toCopy.qmax, toCopy.lambda, toCopy.includeBackground, toCopy.positiveOnly, toCopy.standardizedMin, toCopy.standardizedScale);
        //nonData = new XYSeries("nonstandard");
//        for (int i=0; i<this.data.getItemCount(); i++){
//            XYDataItem item = this.data.getDataItem(i);
//            nonData.add(item.getX(), item.getYValue()*standardizedScale+standardizedMin);
//        }
        //any no defensive copies to be created here?
        //what are the mutable object fields?
        this.del_r = toCopy.del_r;
        this.r_vector = toCopy.r_vector.clone();
        this.positiveOnly = toCopy.positiveOnly;
        this.r_vector_size = toCopy.r_vector_size;
    }


    public void createDesignMatrix(XYSeries datasetInuse){
        ns = (int) Math.ceil(qmax*dmax*INV_PI) ;  //
        coeffs_size = this.includeBackground ? ns + 1 : ns;   //+1 for constant background, +1 to include dmax in r_vector list
        rows = datasetInuse.getItemCount();    // rows

        r_vector_size = ns; // no background implies coeffs_size == ns

        //del_r = Math.PI/qmax; // dmax is based del_r*ns
        del_r = dmax/(double)ns;
        bin_width = del_r;

        // if I think I can squeeze out one more Shannon Number, then I need to define del_r by dmax/ns+1
        //double del_r = dmax/(double)ns;
        r_vector = new double[r_vector_size];

        for(int i=0; i < r_vector_size; i++){ // last bin should be dmax
            r_vector[i] = (0.5 + i)*del_r; // dmax is not represented in this set
        }

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

//                for(int col=0; col < coeffs_size; col++){
//                    if (col == 0){ // constant background term
//                        //a_matrix.set(row, 0, tempData.getXValue());
//                        a_matrix.set(row, 0, 1);
//                    } else { // for col >= 1
//                        double r_value = r_vector[col-1];
//                        a_matrix.set(row, col, FastMath.sin(r_value*tempData.getXValue()) / r_value);
//                    }
//                }

                a_matrix.set(row, 0, 1);
                for(int col=1; col < coeffs_size; col++){
                    double r_value = r_vector[col-1];
                    a_matrix.set(row, col, FastMath.sin(r_value*tempData.getXValue()) / r_value);
                }

                y_vector.set(row,0,tempData.getYValue()); //set data vector
            }
        }
    }


    /**
     * initialize Coefficient vector am for A*am_vector = y_vector
     *
     */
    private void initializeCoefficientVector(){
        am_vector = new SimpleMatrix(coeffs_size,1);  // am is 0 column
        //Gaussian guess = new Gaussian(dmax*0.5, 0.2*dmax);
        double bk  = (y_vector.get(rows-1,0) + y_vector.get(rows-2,0) + y_vector.get(rows-3,0) + y_vector.get(rows-4,0) + y_vector.get(rows-5,0))/5.0;
        double initialValue = 0.3;

        if (positiveOnly){
            initialValue=0.1;
        }


        if (!includeBackground) { // no constant background
            for (int i=0; i < coeffs_size; i++){
                //am_vector.set(i, 0, guess.value(r_vector[i]));
                am_vector.set(i, 0, initialValue); // initialize coefficient vector a_m to zero
            }
        } else {
            //am_vector.set(0,0,0.000000001); // set background constant, initial guess could be Gaussian
            if (priorExists){
                for(int i=0; i < coeffs_size; i++){
                    am_vector.set(i, 0, prior_coefficients[i]);
                }
            } else {
                am_vector.set(0,0, bk); // set background constant, initial guess could be Gaussian
                for (int i=1; i < coeffs_size; i++){
                    //am_vector.set(i, 0, guess.value(r_vector[i-1]));
                    am_vector.set(i, 0, initialValue);
                }
            }
        }

    }



    /**
     * Minimize on the absolute value of the coefficients, coeficients can be positive or negative
     * Can include background or not
     */
    public void
    rambo_coeffs_L1(){

        // initialize coefficient vector
        this.initializeCoefficientVector();

        int cols = coeffs_size;                    // columns
        int u_size = cols;
        int hessian_size = cols*2;
        double twoColsMu = 2.0*cols*MU;
        double s = Double.POSITIVE_INFINITY;

        double t0 = Math.min(Math.max(1, 1.0/lambda), 2*cols/0.001);
        double pitr = 0, pflg = 0, gap;

        double t = t0;
        double tau = 0.01;

        //Hessian and preconditioner
        SimpleMatrix d1 = new SimpleMatrix(cols,cols);
        SimpleMatrix d2 = new SimpleMatrix(cols,cols);

        SimpleMatrix hessian;
        SimpleMatrix dxu = new SimpleMatrix(hessian_size,1);

        ArrayList<SimpleMatrix> answers;

        SimpleMatrix p_u_r2;
        SimpleMatrix p_am_r2;

        /*
         * initialize u vector with 1's
         * size must include a_o and r_limit
         */
        FMatrixRMaj utemp = new FMatrixRMaj(cols,1);
        CommonOps_FDRM.fill(utemp,1);
        SimpleMatrix u = SimpleMatrix.wrap(utemp);
        double inv_t;
        SimpleMatrix a_transpose = a_matrix.transpose();
        SimpleMatrix gradphi0;
        SimpleMatrix gradux = new SimpleMatrix(hessian_size,1);
        SimpleMatrix laplacian = (a_transpose.mult(a_matrix)).scale(2.0);
        SimpleMatrix dx;// = new SimpleMatrix(n,1);
        SimpleMatrix du;// = new SimpleMatrix(u_size,1);
        /*
         * Backtracking Line Search
         */
        SimpleMatrix z;
        SimpleMatrix nu;
        SimpleMatrix new_z;
        SimpleMatrix new_u = new SimpleMatrix(cols,1);
        SimpleMatrix new_x = new SimpleMatrix(cols,1);

        SimpleMatrix f;
        f = new SimpleMatrix(u_size*2,1);
        for (int i=0; i < u_size*2; i++){
            f.set(i,0,-1);
        }
        SimpleMatrix new_f = new SimpleMatrix(u_size*2,1);

        int lsiter;
        double maxAtnu;
        double normL1;

        double phi, new_phi, logfSum, gdx, normg, pcgtol;
        double am_value, u_value, invdiff2, invdiff;

        calculationLoop:
        for (int ntiter=0; ntiter < max_nt_iter; ntiter++){

            z = (a_matrix.mult(am_vector)).minus(y_vector);

            //------------------------------------------------------------
            // Calculate Duality Gap
            //------------------------------------------------------------
            nu = z.scale(2.0);

            // get max of At*nu
            // maxAtnu = max(vec);
            maxAtnu = inf_norm(a_transpose.mult(nu));

            if (maxAtnu > lambda){
                nu = nu.scale(lambda/(maxAtnu));
            }

            /*
             * calculate second derivative P(r) at specified r-values
             * length is the size of r_limit
             * ignore first element, a_0, of am vector (constant background)             *
             */
            normL1 = normL1(am_vector);
            pobj = (z.transpose().mult(z)).get(0,0) + (lambda * normL1);

            /*
             *  dobj  =  max(-0.25*nu'*nu-nu'*y,dobj);
             */
            dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y_vector))).get(0,0) ), dobj);

            // dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y))).get(0,0) ), dobj);
            gap   = pobj - dobj;
            //------------------------------------------------------------
            //       Shall we Stop?
            //------------------------------------------------------------
            if (gap/dobj < reltol ) {
                status = "SOLVED : " + ntiter + " ratio " + (gap/dobj) +  " < " + reltol + " GAP: " + gap + " step " + s + " PITR " + pitr;
                //System.out.println(status);
                break calculationLoop;
            }

            //------------------------------------------------------------
            //       UPDATE t
            //------------------------------------------------------------
            if (s >= 0.5 && gap > 0){
                t = Math.max(Math.min(twoColsMu/gap, MU*t), t);
            }
            inv_t = 1.0/t;

            //------------------------------------------------------------
            //      CALCULATE NEWTON STEP
            //------------------------------------------------------------
            //

            p_am_r2 = am_vector.elementMult(am_vector);
            p_u_r2 = u.elementMult(u);

            /*
             * Partitioned Matrix terms for Hessian
             * D1: n x n
             * D2: n x u_size
             */
            for(int row=0; row < u_size; row++){
                am_value = am_vector.get(row,0);
                u_value = u.get(row,0);

                invdiff = 1.0/(p_u_r2.get(row,0) - p_am_r2.get(row,0));
                invdiff2 = invdiff*invdiff*inv_t;

                d1.set(row,row, 2*(p_u_r2.get(row,0) + p_am_r2.get(row,0))*invdiff2);
                d2.set(row,row, -4*u_value*am_value*invdiff2);
            }

            /*
             * Gradient
             * gradphi = [At*(z*2)-(q1-q2)/t; lambda*ones(n,1)-(q1+q2)/t];
             */
            gradphi0 = a_transpose.mult(z.scale(2.0));

            for (int row=0; row < u_size; row++){
                am_value = am_vector.get(row,0);
                u_value = u.get(row,0);
                invdiff = 2*inv_t/(p_u_r2.get(row,0) - p_am_r2.get(row,0));

                gradux.set(row, 0, gradphi0.get(row,0) + am_value*invdiff);
                gradux.set(row+u_size, 0, lambda - u_value*invdiff);
            }

            normg = gradux.normF();
            pcgtol = Math.min(0.1, eta*gap/Math.min(1.0,normg));

            if (ntiter != 0 && pitr == 0){
                pcgtol = 0.1*pcgtol;
            }

            // laplacian = 2*ATA
            // d1 and d2 are scaled by 1/t
            hessian = hessphi_coeffs(laplacian, d1, d2, coeffs_size);

            /*
             *
             */
            answers = linearPCG(hessian, gradux.scale(-1.0), dxu, d1, pcgtol, pcgmaxi, tau);

            dx = answers.get(0);
            du = answers.get(1);
            dxu = answers.get(2);
            pitr = answers.get(3).get(0,0);

            /*
             *----------------------------------------------
             * Backtrack Line search
             *----------------------------------------------
             */
            logfSum = 0.0;
            for(int fi=0; fi < f.numRows(); fi++){
                logfSum+=FastMath.log(-1*f.get(fi));
            }

            phi = (z.transpose().mult(z)).get(0,0) + lambda*u.elementSum() - logfSum*inv_t;

            s=1.0;
            gdx = (gradux.transpose()).mult(dxu).get(0,0);

            backtrackLoop:
            for (lsiter=0; lsiter < max_ls_iter; lsiter++){

                new_x = am_vector.plus(dx.scale(s));
                new_u = u.plus(du.scale(s));

                for(int ff=0; ff < u_size; ff++){
                    am_value = new_x.get(ff,0);
                    u_value = new_u.get(ff,0);
                    new_f.set(ff, 0, am_value - u_value);
                    new_f.set(ff+u_size, 0, -am_value - u_value);
                }

                if (max(new_f) < 0){

                    new_z = (a_matrix.mult(new_x)).minus(y_vector);
                    logfSum = 0.0;

                    for(int fi=0; fi<new_f.getNumElements(); fi++){
                        logfSum += FastMath.log(-new_f.get(fi));
                    }

                    new_phi = (new_z.transpose().mult(new_z)).get(0,0) + lambda*new_u.elementSum()-logfSum*inv_t;

                    if (new_phi-phi <= alpha*s*gdx){
                       // System.out.println("Breaking BackTrackLoop " + lsiter + " " + max_ls_iter);
                        break backtrackLoop;
                    }
                }
                s = beta*s;
            } // end backtrack loop

            if (lsiter == max_ls_iter){
               //System.out.println("Max LS iteration: Failed");
                break calculationLoop;
            }

            f = new_f.copy();
            am_vector = new_x.copy();
            u = new_u.copy();

            // dxu = new SimpleMatrix(hessian_size,1);
        }


        int totalCoeffs = includeBackground ? coeffs_size: coeffs_size+1;
        coefficients = new double[totalCoeffs];

        if (!includeBackground){
            coefficients[0] = 0; // set background to 0
            for (int j=0; j < coeffs_size; j++){
                coefficients[j+1] = am_vector.get(j,0);
            }
        } else {
            for (int j=0; j < coeffs_size; j++){
                coefficients[j] = am_vector.get(j,0);
            }
        }


        // totalCoefficients = coefficients.length;
        // this.setPrDistribution();
        // I_calc based standardized data

//        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        totalCoefficients = coefficients.length;
        this.setPrDistribution();
        this.calculateIzeroRg();

//        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        coeffs_size = coefficients.length; // this resets the coefficients to possibly include background term as first element

        // calculate residuals
        //residuals = new XYSeries("residuals");
//        double sum =0;
//        for(int i=0; i < rows; i++){
//            XYDataItem item = data.getDataItem(i); // isn't quite correct, using data to get q-values, should be based on input for making design matrix
//            //residuals.add(item.getX(), tempResiduals.get(i,0));
//            double dif = tempResiduals.get(i,0);
//            sum +=  dif*dif;
//        }
        //System.out.println(dmax + " lambda : " + lambda +" => FINAL SUM " + sum);
    }

    /**
     * Design Matrix must include background term
     *
     * under construction, still needs some testing, may not be theoretically possible
     *
     * @return ArrayList<double[]> [coeffs] [r-values]
     */
    private void rambo_NonNegative_coeffs_L1_background(){

        // initialize coefficient vector
        this.initializeCoefficientVector();
        int cols = am_vector.numRows();
        int hessian_size = cols + 1;
        double twoColsMu = 2.0*cols*MU;
        double s = Double.POSITIVE_INFINITY;

        double t0 = Math.min(Math.max(1, 1.0/lambda), 2.0*cols/0.001);
        double pitr = 0, pflg = 0, gap;

        double t = t0;
        double tau = 0.01;

        //Hessian and preconditioner
        SimpleMatrix d1 = new SimpleMatrix(cols,cols);
        SimpleMatrix hessian;
        SimpleMatrix dxu = new SimpleMatrix(hessian_size,1);

        ArrayList<SimpleMatrix> answers;

        double u_o_squared, x_o_squared;
        SimpleMatrix p_am_r2;

        /*
         * initialize u vector with 1's
         * size must include a_o and r_limit
         */
        double u_vector=1;// = SimpleMatrix.wrap(utemp);
        double inv_t;
        SimpleMatrix a_transpose = a_matrix.transpose();
        SimpleMatrix gradphi0;
        SimpleMatrix gradux = new SimpleMatrix(hessian_size,1);
        SimpleMatrix laplacian = (a_transpose.mult(a_matrix)).scale(2.0);
        SimpleMatrix dx;// = new SimpleMatrix(n,1);
        double du;// = new SimpleMatrix(u_size,1);
        /*
         * Backtracking Line Search
         */
        SimpleMatrix z;
        SimpleMatrix nu;
        SimpleMatrix new_z;
        SimpleMatrix new_x = new SimpleMatrix(cols,1);

        SimpleMatrix f_vector;
        f_vector = new SimpleMatrix(hessian_size,1);
        for (int i=0; i < hessian_size; i++){
            f_vector.set(i,0,-1);
        }
        SimpleMatrix new_f = new SimpleMatrix(hessian_size,1);

        int lsiter;
        double maxAtnu;
        double normL1, x_o, uo_minus_xo_squared, d1_xo_uo, d2_xo_uo, inv_uo_minus_xo_squared;

        double phi, new_phi, logfSum, gdx, normg, pcgtol;
        double invdiff, sumNonNegativeX, new_u=1;

        calculationLoop:
        for (int ntiter=0; ntiter < max_nt_iter; ntiter++){

            z = (a_matrix.mult(am_vector)).minus(y_vector);

            //------------------------------------------------------------
            // Calculate Duality Gap
            //------------------------------------------------------------
            nu = z.scale(2.0);

            // get max of At*nu
            // maxAtnu = max(vec);
//            maxAtnu = inf_norm(a_transpose.mult(nu));
//            if (maxAtnu > lambda){
//                nu = nu.scale(lambda/(maxAtnu));
//            }

            maxAtnu = min(a_transpose.mult(nu));
            if (maxAtnu < -lambda){
                nu = nu.scale(lambda/(-maxAtnu));
            }
            /*
             * calculate second derivative P(r) at specified r-values
             * length is the size of r_limit
             * ignore first element, a_0, of am vector (constant background)             *
             */
            normL1 = Math.abs(am_vector.get(0,0));
            for(int i=1; i<cols; i++){
                normL1 += am_vector.get(i,0);
            }

            pobj = (z.transpose().mult(z)).get(0,0) + (lambda * normL1);

            /*
             *  dobj  =  max(-0.25*nu'*nu-nu'*y,dobj);
             */
            dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y_vector))).get(0,0) ), dobj);

            // dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y))).get(0,0) ), dobj);
            gap   = pobj - dobj;

            //------------------------------------------------------------
            //       Shall we Stop?
            //------------------------------------------------------------
            if (gap/Math.abs(dobj) < reltol) {
                status = "SOLVED : " + ntiter + " ratio " + (gap/dobj) +  " < " + reltol + " GAP: " + gap + " step " + s + " PITR " + pitr;
          //      System.out.println(status);
                break calculationLoop;
            }

            //------------------------------------------------------------
            //       UPDATE t
            //------------------------------------------------------------
            if (s >= 0.5){
                t = Math.max(Math.min(twoColsMu/gap, MU*t), t);
            }
            inv_t = 1.0/t;

            //------------------------------------------------------------
            //      CALCULATE NEWTON STEP
            //------------------------------------------------------------
            //
            x_o = am_vector.get(0,0);
            p_am_r2 = am_vector.elementMult(am_vector);
            u_o_squared = u_vector*u_vector;
            x_o_squared = x_o* x_o;
            uo_minus_xo_squared = 1.0/(u_vector*u_vector - x_o_squared);
            inv_uo_minus_xo_squared = uo_minus_xo_squared*uo_minus_xo_squared;
            d1_xo_uo = 2.0*(u_o_squared+x_o_squared)*inv_uo_minus_xo_squared;
            d2_xo_uo = -4.0*u_vector*x_o*inv_uo_minus_xo_squared;
            /*
             * Partitioned Matrix terms for Hessian
             * D1: n x n
             * D2: n x u_size
             */
            d1.set(0,0, d1_xo_uo*inv_t);
            for(int row=1; row < cols; row++){
                invdiff = 1.0/p_am_r2.get(row,0)*inv_t;
                d1.set(row,row, invdiff);
            }

            /*
             * Gradient
             * gradphi = [At*(z*2)-(q1-q2)/t; lambda*ones(n,1)-(q1+q2)/t];
             */
            gradphi0 = a_transpose.mult(z.scale(2.0));

            gradux.set(0, 0,   gradphi0.get(0,0) + 2.0*x_o*uo_minus_xo_squared*inv_t);
            for (int row=1; row < cols; row++){
                gradux.set(row, 0, gradphi0.get(row,0) + lambda - 1.0/am_vector.get(row,0)*inv_t);
            }
            gradux.set(hessian_size-1, 0, lambda - 2.0*u_vector*uo_minus_xo_squared*inv_t);

            normg = gradux.normF();
            pcgtol = Math.min(0.1, eta*gap/Math.min(1.0,normg));

            if (ntiter != 0 && pitr == 0){
                pcgtol = 0.1*pcgtol;
            }

            // laplacian = 2*ATA
            // d1 and d2 are scaled by 1/t
            // hessian = hessphi_coeffs(laplacian, d1, d2, coeffs_size);
            hessian = hessphi_coeffs_nonNegative_abs(laplacian, d1, d1_xo_uo, d2_xo_uo, cols);
            /*
             *
             */
            answers = linearPCG(hessian, gradux.scale(-1.0), dxu, d1, pcgtol, pcgmaxi, tau);

            dx = answers.get(0);
            du = answers.get(1).get(0,0);
            dxu = answers.get(2);
            pitr = answers.get(3).get(0,0);

            /*
             *----------------------------------------------
             * Backtrack Line search
             *----------------------------------------------
             */
            logfSum = 0.0;
            for(int fi=0; fi < f_vector.numRows(); fi++){
                logfSum+=FastMath.log(-1*f_vector.get(fi));
            }
            sumNonNegativeX=0;
            for(int i=1; i<cols; i++){ // exclude the background term since it is absolute value
                sumNonNegativeX += am_vector.get(i,0);
            }

            phi = (z.transpose().mult(z)).get(0,0) + lambda*u_vector + lambda*sumNonNegativeX - logfSum*inv_t;

            s=1.0;
            gdx = (gradux.transpose()).mult(dxu).get(0,0);

            backtrackLoop:
            for (lsiter=0; lsiter < max_ls_iter; lsiter++){

                new_x = am_vector.plus(dx.scale(s));
                new_u = u_vector+du*s;

                //update f
                new_f.set(0,0,new_x.get(0,0)-new_u);
                new_f.set(cols,0,-new_x.get(0,0)-new_u);
                for(int ff=1; ff < cols; ff++){
                    new_f.set(ff, 0, -new_x.get(ff,0));
                }

                if (max(new_f) < 0){ // means f is negative

                    new_z = (a_matrix.mult(new_x)).minus(y_vector);
                    logfSum = 0.0;
                    for(int fi=0; fi<new_f.getNumElements(); fi++){
                        logfSum += FastMath.log(-new_f.get(fi));
                    }

                    sumNonNegativeX=0;
                    for(int i=1; i<cols; i++){ // exclude the background term since it is absolute value
                        sumNonNegativeX += new_x.get(i,0);
                    }

                    new_phi = (new_z.transpose().mult(new_z)).get(0,0) + lambda*new_u + lambda*sumNonNegativeX - logfSum*inv_t;

                    if (new_phi-phi <= alpha*s*gdx){
                        //System.out.println("Breaking BackTrackLoop");
                        break backtrackLoop;
                    }
                }
                s = beta*s;
            } // end backtrack loop

            if (lsiter == max_ls_iter){
           //     System.out.println("Max LS iteration: Failed");
                break calculationLoop;
            }

            f_vector = new_f.copy();
            am_vector = new_x.copy();
            u_vector = new_u;

            // dxu = new SimpleMatrix(hessian_size,1);
        }

        coefficients = new double[coeffs_size];

            for (int j=0; j < coeffs_size; j++){
                coefficients[j] = am_vector.get(j,0);
            }


        // P(dmax) should be zero.  So, we introduce a mid point between second to Last value and dmax to hold value of the last bin

        this.setPrDistribution();
        this.calculateIzeroRg();
        // I_calc based standardized data
        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        coeffs_size = coefficients.length; // this resets the coefficients to possibly include background term as first element

        // calculate residuals
//        residuals = new XYSeries("residuals");
//        for(int i=0; i < rows; i++){
//            XYDataItem item = data.getDataItem(i); // isn't quite correct, using data to get q-values, should be based on input for making design matrix
//            residuals.add(item.getX(), tempResiduals.get(i,0));
//        }
    }

    private SimpleMatrix hessphi_coeffs_nonNegative_abs(SimpleMatrix laplacian, SimpleMatrix d1, double d1_xo_uo, double d2_xo_uo, int numColsInLaplacian) {
        int n = laplacian.numCols();

        SimpleMatrix hessian = new SimpleMatrix(numColsInLaplacian+1,numColsInLaplacian+1);
        SimpleMatrix t_ata;
        t_ata =  laplacian.plus(d1);

        hessian.set(numColsInLaplacian,0,d2_xo_uo);
        hessian.set(0,numColsInLaplacian,d2_xo_uo);
        hessian.set(numColsInLaplacian,numColsInLaplacian,d1_xo_uo);
        for (int r=0; r < n; r++){

            for(int c=0; c < n; c++){
                hessian.set(r,c,t_ata.get(r,c));
                //System.out.println(r + " x " + c + " => " + hessian.get(r,c));
            }
        }
        return hessian;
    }

    /**
     *
     * @return ArrayList<double[]> [coeffs] [r-values]
     */
    private void rambo_coeffs_L1_positive_only(){

        initializeCoefficientVector();

        double t0 = Math.min(Math.max(1, 1.0/lambda), coeffs_size/0.001);
        double pitr = 0, pflg = 0, gap;
        double inv_t, t = t0;
        double s = Double.POSITIVE_INFINITY;
        //Hessian and preconditioner
        SimpleMatrix d1 = new SimpleMatrix(coeffs_size, coeffs_size);
        SimpleMatrix hessian;
        ArrayList<SimpleMatrix> answers;
        SimpleMatrix p_am_r2;

        /*
         * initialize u vector with 1's
         * size must include a_o and r_limit
         */
        FMatrixRMaj utemp = new FMatrixRMaj(coeffs_size,1);
        CommonOps_FDRM.fill(utemp,1);

        SimpleMatrix a_transpose = a_matrix.transpose();
        SimpleMatrix gradphi0;
        SimpleMatrix gradx = new SimpleMatrix(coeffs_size,1);

        // for Preconditioner
        SimpleMatrix laplacian = (a_transpose.mult(a_matrix)).scale(2.0);

        SimpleMatrix dx = new SimpleMatrix(coeffs_size,1);

        /*
         * Backtracking Line Search
         */
        SimpleMatrix z;
        SimpleMatrix nu;
        SimpleMatrix new_z;
        SimpleMatrix new_x = new SimpleMatrix(coeffs_size,1);

        SimpleMatrix midf;
        midf = new SimpleMatrix(coeffs_size,1);
        for (int i=0; i < coeffs_size; i++){
            midf.set(i,0,-1*am_vector.get(i));
        }
        SimpleMatrix new_f = new SimpleMatrix(coeffs_size,1);

        int lsiter;
        double minAnu;
        double normL1, coeffsMu = (double)coeffs_size*MU;

        double phi, new_phi, logfSum, gdx, normg, pcgtol;
        double invdiff;

        calculationLoop:
        for (int ntiter=0; ntiter < max_nt_iter; ntiter++){

            z = (a_matrix.mult(am_vector)).minus(y_vector);

            //------------------------------------------------------------
            // Calculate Duality Gap
            //------------------------------------------------------------
            nu = z.scale(2.0);

            // get max of At*nu
            minAnu = min(a_transpose.mult(nu));
            if (minAnu < -lambda){
                nu = nu.scale(lambda/(-minAnu));
            }

            /*
             * calculate second derivative P(r) at specified r-values
             * length is the size of r_limit
             * ignore first element, a_0, of am vector (constant background)
             */
            //normL1 = normL1(am_vector);
            normL1 = am_vector.elementSum();
            pobj = (z.transpose().mult(z)).get(0,0) + (lambda * normL1);

            /*
             *  dobj  =  max(-0.25*nu'*nu-nu'*y,dobj);
             */
            dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y_vector))).get(0,0) ), dobj);

            // dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y))).get(0,0) ), dobj);
            gap   = pobj - dobj;

            //------------------------------------------------------------
            //       Shall we Stop?
            //------------------------------------------------------------
            //System.out.println(ntiter + " " + (gap/dobj) +  " GAP: " + gap + " : pobj " + pobj + " | dobj " + dobj + " step " + s + " PITR " + pitr);
            if (gap/Math.abs(dobj) < reltol) {
                status = ("Solved => GAP: " + gap + " : " + " | ratio " + gap/dobj + " reltol " + reltol + " " + pitr);
               // System.out.println(status);
                break calculationLoop;
            }

            //------------------------------------------------------------
            //       UPDATE t
            //------------------------------------------------------------
            if (s >= 0.5){
                t = Math.max(Math.min(coeffsMu/gap, MU*t), t);
            }
            inv_t = 1.0/t;

            //------------------------------------------------------------
            //      CALCULATE NEWTON STEP
            //------------------------------------------------------------
            //
            p_am_r2 = am_vector.elementMult(am_vector);

            /*
             * Partitioned Matrix terms for Hessian
             * D1: n x n
             */
            for(int row=0; row < coeffs_size; row++){
                invdiff = 1.0/p_am_r2.get(row,0)*inv_t;
                d1.set(row,row, invdiff);
            }

            /*
             * Gradient
             * gradphi = [At*(z*2) + lambda  - 1/t*1/x; lambda*ones(n,1)-(q1+q2)/t];
             */
            gradphi0 = a_transpose.mult(z.scale(2.0));

            for (int row=0; row < coeffs_size; row++){
                invdiff = inv_t/am_vector.get(row,0);
                gradx.set(row, 0, gradphi0.get(row,0) + lambda - invdiff);
            }

            normg = gradx.normF();
            pcgtol = Math.min(0.1, eta*gap/Math.min(1,normg));

            if (ntiter != 0 && pitr == 0){
                pcgtol = 0.1*pcgtol;
            }

            //diagxtx.plus(d1)
            hessian = hessphi_coeffs_positivity_constrained(laplacian, d1, coeffs_size);
            /*
             *
             */
            answers = linearPCGPositiveOnly(hessian, gradx.scale(-1.0), dx, d1, pcgtol, pcgmaxi);

            dx = answers.get(0);
            pitr = answers.get(2).get(0,0);

            /*
             *----------------------------------------------
             * Backtrack Line search
             *----------------------------------------------
             */
            logfSum = 0.0;
            for(int fi=0; fi < midf.numRows(); fi++){
                logfSum += FastMath.log(-midf.get(fi));
            }

            phi = (z.transpose().mult(z)).get(0,0) + lambda * am_vector.elementSum() - logfSum*inv_t;

            s=1.0;
            gdx = (gradx.transpose()).mult(dx).get(0,0);

            backtrackLoop:
            for (lsiter=0; lsiter < max_ls_iter; lsiter++){

                new_x = am_vector.plus(dx.scale(s));

                for(int ff=0; ff < coeffs_size; ff++){
                    new_f.set(ff, 0, -new_x.get(ff,0));
                }

                if (max(new_f) < 0){

                    new_z = (a_matrix.mult(new_x)).minus(y_vector);
                    logfSum = 0.0;

                    for(int fi=0; fi<new_f.getNumElements(); fi++){
                        logfSum += FastMath.log(-new_f.get(fi));
                    }

                    new_phi = (new_z.transpose().mult(new_z)).get(0,0)+lambda*new_x.elementSum()-logfSum*inv_t;

                    if (new_phi-phi <= alpha*s*gdx){
                        //System.out.println("Breaking BackTrackLoop");
                        break backtrackLoop;
                    }
                }

                s = beta*s;
            } // end backtrack loop

            if (lsiter == max_ls_iter){
                System.out.println("Max LS iteration: Failed");
                break calculationLoop;
            }

            midf = new_f.copy();
            am_vector = new_x.copy();
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


        // P(dmax) should be zero.  So, we introduce a mid point between second to Last value and dmax to hold value of the last bin

        this.setPrDistribution();
        this.calculateIzeroRg();
        // I_calc based standardized data
        //SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        coeffs_size = coefficients.length; // this resets the coefficients to possibly include background term as first element

        // calculate residuals
//        residuals = new XYSeries("residuals");
//        for(int i=0; i < rows; i++){
//            XYDataItem item = data.getDataItem(i); // isn't quite correct, using data to get q-values, should be based on input for making design matrix
//            residuals.add(item.getX(), tempResiduals.get(i,0));
//        }
    }


    @Override
    public double calculatePofRAtR(double r_value, double scale){
        return (inv2PI2*standardizedScale)*splineFunction.value(r_value)*scale;
    }

    @Override
    void calculateIzeroRg() {
        double tempRgSum = 0, tempRg2Sum=0, xaverage=0;
        //del_r = prDistribution.getX(2).doubleValue() - prDistribution.getX(1).doubleValue() ;

        XYDataItem item;
        for(int i=1; i<totalInDistribution-1; i++){ // exclude last two points?
            item = prDistribution.getDataItem(i);
            double rvalue = item.getXValue();
            tempRg2Sum += rvalue*rvalue*item.getYValue();
            tempRgSum += item.getYValue(); // width x height => area
            xaverage += rvalue*item.getYValue();
        }

        tempRg2Sum *= del_r;
        tempRgSum *= del_r; // width x height => area
        xaverage *= del_r;

        rg = Math.sqrt(0.5*tempRg2Sum/tempRgSum);

        double sum = coefficients[0];
        for(int j=0; j< r_vector_size; j++){
            sum +=  coefficients[j+1];
        }

        //izero = tempRgSum*standardizedScale+standardizedMin;
        izero = sum*standardizedScale+standardizedMin;
        rAverage = xaverage/tempRgSum;
        area = tempRgSum;
    }



    @Override
    void setPrDistribution(){
        prDistribution = new XYSeries("PRDistribution");
        prDistributionForFitting = new XYSeries("OutputPrDistribution");

        totalInDistribution = r_vector_size+2;

        prDistribution.add(0,0);

        for(int i=1; i<totalInDistribution-1; i++) { // values in r_vector represent the midpoint or increments of (i+0.5)*del_r
            int index = i-1;
            double value = coefficients[index+1];
            prDistribution.add(r_vector[index], value);
            prDistributionForFitting.add(r_vector[index], value);
        }
        prDistribution.add(dmax, 0);

        // calculate total Diff
        scoreDistribution(del_r);
        setHeaderDetails();
        setSplineFunction();
    }


    private void setHeaderDetails(){
        this.description  = String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS DIRECT INVERSE FOURIER TRANSFORM OF I(q) %n");
        this.description += String.format("REMARK 265  COEFFICIENTS ARE THE HISTOGRAM HEIGHTS WITH EQUAL BIN WIDTHS %n");
        this.description  = String.format("REMARK 265  PERFORMED WITH L1-NORM REGULARIZATION %n");
        this.description  = String.format("REMARK 265  REGULARIZATION => minimize|ABS_VALUE HISTOGRAM|  %n");
        if (positiveOnly){
            this.description  = String.format("REMARK 265  HISTOGRAM CONSTRAINED AS POSITIVE ONLY %n");
        }
        this.description += String.format("REMARK 265 %n");
        this.description  = String.format("REMARK 265                LAMBDA (WEIGHT) : %.2E %n", lambda);
        this.description += String.format("REMARK 265            BIN WIDTH (delta r) : %.4f %n", del_r);
        this.description += String.format("REMARK 265             DISTRIBUTION SCORE : %.4f %n", prScore);
        this.description += String.format("REMARK 265 %n");
        if (!includeBackground){
            this.description += String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n");
        } else {
            this.description += String.format("REMARK 265        CONSTANT BACKGROUND m(0) : %.4E %n", coefficients[0]);
        }

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
    public void normalizeDistribution(){
        double sum=0;
        for(int i=0; i < totalInDistribution; i++){
            XYDataItem item = prDistribution.getDataItem(i);
            sum += item.getYValue();
        }

        sum *= del_r; //area
        double invSum = 1.0/sum;

        for(int i=0; i < totalInDistribution; i++){
            XYDataItem item = prDistribution.getDataItem(i);
            prDistribution.updateByIndex(i, item.getYValue()*invSum);
        }
    }



    @Override
    public void estimateErrors(XYSeries fittedqIq){

        double[] oldCoefficients = new double[totalCoefficients];
        for(int i=0; i<totalCoefficients; i++){ // copy old coefficients temporarily
            oldCoefficients[i] = coefficients[i]; // will be over written in the estimate
        }

        XYSeries tempPr = new XYSeries("tempPr");
        XYSeries tempPrFit = new XYSeries("for fit");

        try {
            tempPrFit = prDistributionForFitting.createCopy(0, prDistributionForFitting.getItemCount()-1);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        for(int i=0; i<prDistribution.getItemCount(); i++){
            tempPr.add(prDistribution.getDataItem(i));
        }



        int size = fittedqIq.getItemCount();
        double upperq = fittedqIq.getMaxX();
        double bins = totalCoefficients-1;
        double delta_q = upperq/bins;
        double samplingLimit;

        //del_r = prDistribution.getX(2).doubleValue()  - prDistribution.getX(1).doubleValue() ;

        XYDataItem tempData;
        int totalRuns = 71;
        ArrayList<Double> rgValuesList = new ArrayList<>();
        ArrayList<Double> izeroValuesList = new ArrayList<>();


        ArrayList<Integer> countsPerBin = new ArrayList<>();
        int startbb = 0;

        // determine counts per bin to draw from
        // standardize data using specified mean and stdev
        double invStdev = 1.0/standardizedScale;

        for (int b=1; b <= bins; b++) {
            int sumCount = 0;
            double upperBound = delta_q*b;

            binloop:
            for (int bb=startbb; bb < size; bb++){
                double tempCurrent = fittedqIq.getX(bb).doubleValue();

                if ( (tempCurrent >= (delta_q*(b-1)) ) && (tempCurrent < upperBound) ){
                    sumCount += 1.0;
                } else if (tempCurrent >= upperBound ) {
                    startbb = bb;
                    break binloop;
                }
            }
            // you want a better algorithm, by a bad computer
            // greens theorem surface integral
            countsPerBin.add((int)sumCount);
        }

        XYSeries randomSeries = new XYSeries("Random-");

        int upperbb, locale;
        Random randomGenerator = new Random();
        int[] randomNumbers;

        for (int i=0; i < totalRuns; i++){

            randomSeries.clear();
            // randomly grab from each bin
            startbb = 0;
            upperbb = 0;

            for (int b=1; b <= bins; b++){
                // find upper q in bin
                // if s-to-n of bin < 1.5, sample more points in bin
                // if countPerBin < 5, randomly pick 1
                // what if countPerBin = 0?
                if (countsPerBin.get(b-1) > 0){

                    samplingLimit = (1.0 + randomGenerator.nextInt(46))/100.0;  // return a random percent up to ...
                    binloop:
                    for (int bb=startbb; bb < size; bb++){
                        if (fittedqIq.getX(bb).doubleValue() >= (delta_q*b) ){ // what happens on the last bin?
                            upperbb = bb;
                            break binloop;
                        }
                    }

                    // grab indices inbetween startbb and upperbb
                    randomNumbers = Functions.randomIntegersBounded(startbb, upperbb, samplingLimit);
                    startbb = upperbb;

                    // standardize the data
                    for(int h=0; h<randomNumbers.length; h++){
                        locale = randomNumbers[h];
                        tempData = fittedqIq.getDataItem(locale);
                        randomSeries.add(tempData.getXValue(), (tempData.getYValue()-standardizedMin)*invStdev);
                    }
                } // end of checking if bin is empty
            }

            // calculate PofR
            this.createDesignMatrix(randomSeries);

            if (positiveOnly){
                rambo_coeffs_L1_positive_only();
            } else {
                this.rambo_coeffs_L1();
            }

            // calculate Rg
            double tempRgSum = 0, tempRg2Sum=0, xaverage=0;
            XYDataItem item;
            for(int pr=1; pr < (totalInDistribution-2); pr++){

                item = prDistribution.getDataItem(pr);

                double rvalue = item.getXValue();
                tempRg2Sum += rvalue*rvalue*item.getYValue()*del_r;
                tempRgSum += item.getYValue()*del_r; // width x height => area
                xaverage += rvalue*item.getYValue()*del_r;
            }
            // IZERO estimate
            double sum = coefficients[0];
            for(int j=0; j< r_vector_size; j++){
                sum +=  coefficients[j+1];
            }

            //rgValues[i] = Math.sqrt(0.5*tempRg2Sum/tempRgSum);
            if (tempRg2Sum > 0){
                rgValuesList.add(Math.sqrt(0.5*tempRg2Sum/tempRgSum));
                izeroValuesList.add((sum + standardizedMin)*standardizedScale);
            }
        }
        //double[] rgValues = new double[totalRuns];
        int totalDoubles = rgValuesList.size();
        double[] izeroValues = new double[totalDoubles];
        double[] rgValues = new double[totalDoubles];
        for(int d=0; d<totalDoubles; d++){
            rgValues[d] = rgValuesList.get(d);
            izeroValues[d] = izeroValuesList.get(d);
        }

        DescriptiveStatistics rgStat = new DescriptiveStatistics(rgValues);
        DescriptiveStatistics izeroStat = new DescriptiveStatistics(izeroValues);

        rgError = rgStat.getStandardDeviation()/rgStat.getMean();
        iZeroError = izeroStat.getStandardDeviation()/izeroStat.getMean();

        //RESTORE COEFFICIENTS AND PR DISTRIBUTION
        for(int i=0; i<totalCoefficients; i++){ // copy back the coefficients
            coefficients[i] = oldCoefficients[i];
        }

        prDistribution.clear();
        for(int i=0; i<tempPr.getItemCount(); i++){
            prDistribution.add(tempPr.getDataItem(i));
        }

        prDistributionForFitting.clear();
        for(int i=0; i<tempPrFit.getItemCount(); i++){
            prDistributionForFitting.add(tempPrFit.getDataItem(i));
        }

        totalInDistribution = prDistribution.getItemCount();
        setSplineFunction();
    }


}
