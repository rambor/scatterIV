package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_FDRM;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;

import version4.Functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class MooreTransform extends IndirectFT {

    private int cols;
    private double del_r;
    double[] r_vector;
    double[] r_vector_size_for_fitting;
    int r_vector_size;
    int multiplesOfShannonNumber = 1;
    boolean useL1 = false;

    // Dataset should be standardized and in form of [q, q*I(q)]
    public MooreTransform(XYSeries dataset, XYSeries errors, double dmax, double qmax, double lambda, boolean useL1, boolean includeBackground) {
        super(dataset, errors, dmax, qmax, lambda, includeBackground);
        this.useL1 = useL1;
        this.createDesignMatrix(this.data);
        if (this.useL1){
            //this.includeBackground = true;
            if (this.includeBackground){
                this.setModelUsed("MOORE L1-NORM PR SMOOTH with BKGRND");
                System.out.println(this.getModelUsed());
            } else {
                this.setModelUsed("MOORE L1-NORM PR SMOOTH");
            }
            this.moore_pr_L1();
        } else { // must always use background when doing second derivative L1
            this.moore_coeffs_L1();
            this.setModelUsed("MOORE L1-NORM COEFFICIENTS");
        }
    }


    public MooreTransform(
            XYSeries scaledqIqdataset,
            XYSeries scaledqIqErrors,
            double dmax,
            double qmax,
            double lambda,
            boolean useL1,
            boolean includeBackground,
            double stdmin,
            double stdscale){

        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, includeBackground, stdmin, stdscale);

        this.useL1 = true;

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

        if (useL1){
            //this.includeBackground = true;
            if (this.includeBackground){
                this.setModelUsed("MOORE L1-NORM PR SMOOTH with BKGRND");
            } else {
                this.setModelUsed("MOORE L1-NORM PR SMOOTH");
            }

            this.moore_pr_L1();
        } else { // must always use background when doing second derivative L1
            this.moore_coeffs_L1();
            this.setModelUsed("MOORE L1-NORM COEFFS");
        }
    }


    /**
     * Use if priors are available
     *
     * @param scaledqIqdataset
     * @param scaledqIqErrors
     * @param priors
     * @param dmax
     * @param qmax
     * @param lambda
     * @param useL1
     * @param includeBackground
     * @param stdmin
     * @param stdscale
     */
    public MooreTransform(
            XYSeries scaledqIqdataset,
            XYSeries scaledqIqErrors,
            double[] priors,
            double dmax,
            double qmax,
            double lambda,
            boolean useL1,
            boolean includeBackground,
            double stdmin,
            double stdscale){

        super(scaledqIqdataset, scaledqIqErrors, dmax, qmax, lambda, includeBackground, stdmin, stdscale);

        this.useL1 = true;
        this.prior_coefficients = priors.clone();
        priorExists = true;

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

        if (useL1){
            //this.includeBackground = true;
            if (this.includeBackground){
                this.setModelUsed("MOORE L1-NORM PR SMOOTH with BKGRND");
            } else {
                this.setModelUsed("MOORE L1-NORM PR SMOOTH");
            }
            this.moore_pr_L1();
        } else { // must always use background when doing second derivative L1
            this.moore_coeffs_L1();
            this.setModelUsed("MOORE L1-NORM COEFFS");
        }

    }


    /**
     *
     * @param datasetInuse
     */
    public void createDesignMatrix(XYSeries datasetInuse){

        ns = (int) Math.floor(qmax*dmax*INV_PI);  //
        coeffs_size = this.includeBackground ? ns + 1 : ns;   //+1 for constant background, +1 to include dmax in r_vector list
        r_vector_size_for_fitting = new double[ns];

        rows = datasetInuse.getItemCount();    // rows
        cols = coeffs_size;                    // columns

        r_vector_size = (multiplesOfShannonNumber*ns) - 1; //
        del_r = dmax/(ns*multiplesOfShannonNumber); // for calculating P(r) at specified r-values or pddr

        // if I think I can squeeze out one more Shannon Number, then I need to define del_r by dmax/ns+1
        //double del_r = dmax/(double)ns;
        r_vector = new double[r_vector_size];

        for(int i=0; i < r_vector_size; i++){ // last bin should be dmax
            r_vector[i] = (i+1)*del_r;
        }

        for(int i=0; i < ns; i++){ // last bin should be dmax
            r_vector_size_for_fitting[i] = (i+0.5)*del_r;
        }

        /*
         * create A matrix (design Matrix)
         */
        a_matrix = new SimpleMatrix(rows, coeffs_size);
        /*
         * y_vector is q*I(q) data
         */
        y_vector = new SimpleMatrix(rows,1);
        /*
         * am_vector contains the unknown parameters
         */
        double qd2, qd;
       // double pi_d = Math.PI*dmax;
       // double two_inv_pi_pi_d = TWO_INV_PI*pi_d;

        if (!includeBackground){ // no constant background

            for(int row=0; row < rows; row++){ //rows, length is size of data
                XYDataItem tempData = datasetInuse.getDataItem(row);
                qd = tempData.getXValue()*dmax;
                qd2 = qd*qd;

                for(int col=0; col < coeffs_size; col++){
                    a_matrix.set(row, col, dmax_PI_TWO_INV_PI * (col+1) * FastMath.pow(-1.0, col + 2) * FastMath.sin(qd) / (n_pi_squared[col+1] - qd2));
                }
                y_vector.set(row,0,tempData.getYValue()); //set data vector
            }
        } else { // constant background

            for(int r=0; r<rows; r++){ //rows, length is size of data

                XYDataItem tempData = datasetInuse.getDataItem(r);
                qd = tempData.getXValue()*dmax;
                qd2 = qd*qd;

                for(int c=0; c < coeffs_size; c++){
                    if (c == 0){
                        //a_matrix.set(r, 0, tempData.getXValue()); // constant background term
                        a_matrix.set(r, 0, 1);
                    } else {
                        a_matrix.set(r, c, dmax_PI_TWO_INV_PI * c * FastMath.pow(-1.0, c + 1) * FastMath.sin(qd) / (n_pi_squared[c] - qd2));
                    }
                }
                y_vector.set(r,0,tempData.getYValue()); //set data vector
            }
        }
    }


    /**
     * initialize Coefficient vector am for A*am_vector = y_vector
     *
     */
    private void initializeCoefficientVector(){



        am_vector = new SimpleMatrix(coeffs_size,1);  // am is 0 column

        if (priorExists){

            System.out.println("PRIORS " + coeffs_size + " = " + prior_coefficients.length);
            // if no background, coefficients is always +1 to am_vector
            if (includeBackground){
                //System.out.println("Has background");
                for (int i=0; i < prior_coefficients.length; i++){
                    am_vector.set(i, 0, prior_coefficients[i]);
                    System.out.println(i + " " + prior_coefficients[i]);
                }
            } else { // no background
                for (int i=1; i < prior_coefficients.length; i++){
                    am_vector.set(i-1, 0, prior_coefficients[i]);
                    System.out.println(i + " " + prior_coefficients[i]);
                }
            }


        } else {
            if (!includeBackground){ // no constant background
                for (int i=0; i < coeffs_size; i++){
                    am_vector.set(i, 0, 0);
                }
            } else { // constant background
                am_vector.set(0,0,0); // set background constant, initial guess could be Gaussian
                for (int i=1; i < coeffs_size; i++){
                    am_vector.set(i, 0, 0.0000000000001);
                }
            }
        }

    }



    /**
     *
     *
     */
    public void moore_coeffs_L1(){

        // initialize coefficient vector
        this.initializeCoefficientVector();

        int u_size = cols;
        int hessian_size = cols*2;
        double twoColsMu = 2.0*cols*MU;
        double s = Double.POSITIVE_INFINITY;

        double t0 = Math.min(Math.max(1, 1.0/lambda), 2*cols/0.001);
        double pitr = 0, pflg = 0, gap;

        double t_constant= t0;
        double tau = 0.01, inv_t;

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
        CommonOps_FDRM.fill(utemp, 1);
        SimpleMatrix u = SimpleMatrix.wrap(utemp);
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
            //maxAtnu = inf_norm(a_transpose.mult(nu));
            maxAtnu = min(a_transpose.mult(nu));

            if (maxAtnu < -lambda){
                nu = nu.scale(lambda/(-maxAtnu));
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

            //System.out.println("GAP: " + gap + " : " + " | ratio " + gap/dobj + " reltol " + reltol);
            //------------------------------------------------------------
            //       Shall we Stop?
            //------------------------------------------------------------
            if (gap/dobj < reltol) {
                status = "SOLVED : " + ntiter + " ratio " + (gap/dobj) +  " < " + reltol + " GAP: " + gap + " step " + s + " PITR " + pitr;
                //status = "SOLVED : " + " | ratio " + gap/dobj + " < reltol " + reltol + " at " + ntiter;
                //System.out.println(status);
                break calculationLoop;
            }

            //------------------------------------------------------------
            //       UPDATE t
            //------------------------------------------------------------
            if (s >= 0.5 && gap > 0){
                t_constant = Math.max(Math.min(twoColsMu/gap, MU*t_constant), t_constant);
            }
            inv_t = 1.0/t_constant;

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
            //System.out.println("NTITER " + ntiter + " t: " + t_constant + " " + logfSum + " logfSum*inv_t " + (logfSum*inv_t));
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
                        //System.out.println("Breaking BackTrackLoop");
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
        coeffs_size = coefficients.length;

        // calculate residuals
//        residuals = new XYSeries("residuals");
//        for(int i=0; i < rows; i++){
//            XYDataItem item = data.getDataItem(i); // isn't quite correct, using data to get q-values, should be based on input for making design matrix
//            residuals.add(item.getX(), tempResiduals.get(i,0));
//        }
    }


    /**
     * performs L1-norm minimization of second derivative of Pr distribution
     *
     */
    public void moore_pr_L1(){

        // initialize coefficient vector
        this.initializeCoefficientVector();

        double inv_d = 1.0/dmax;
        double s = Double.POSITIVE_INFINITY;
        double twoCoeffsMU = 2.0*coeffs_size*MU;
        int u_size = r_vector_size + 1;
        int hessian_size = coeffs_size + u_size;

        double t0 = Math.min(Math.max(1, 1.0/lambda), coeffs_size/0.001);
        double pitr = 0, pflg = 0, gap;
        double t = t0;

        //Hessian and Preconditioner
        SimpleMatrix d1 = new SimpleMatrix(coeffs_size, coeffs_size);
        SimpleMatrix d2 = new SimpleMatrix(coeffs_size, u_size);
        SimpleMatrix d3;

        SimpleMatrix hessian;

        FMatrixRMaj dxutemp = new FMatrixRMaj(hessian_size,1);
        CommonOps_FDRM.fill(dxutemp,1);
        SimpleMatrix dxu = SimpleMatrix.wrap(dxutemp.copy());

        double[][] n_onesArray = new double[coeffs_size][1];

        for (double[] row : n_onesArray) {
            Arrays.fill(row, 1);
        }

        ArrayList<SimpleMatrix> answers;

        /*
         * initialize u vector with 1's
         * size must include a_o and r_limit
         */
        FMatrixRMaj utemp = new FMatrixRMaj(u_size,1);
        CommonOps_FDRM.fill(utemp,1);
        SimpleMatrix u = SimpleMatrix.wrap(utemp);


        SimpleMatrix p_dd_r_of_am;
        SimpleMatrix p_dd_r_new;
        SimpleMatrix z;  // = new SimpleMatrix(m,1);
        SimpleMatrix new_z;  // = new SimpleMatrix(m,1);
        SimpleMatrix nu; // = new SimpleMatrix(m,1);
        SimpleMatrix p_u_r2;
        SimpleMatrix p_am_r2;

        //
        // Initialize and guess am
        //
        double inv_t;

        SimpleMatrix a_transpose = a_matrix.transpose();
        SimpleMatrix gradphi0;
        SimpleMatrix gradux = new SimpleMatrix(hessian_size,1);

        //SimpleMatrix diagAtA;
        SimpleMatrix laplacian = a_transpose.mult(a_matrix).scale(2.0);
        SimpleMatrix dx;// = new SimpleMatrix(n,1);
        SimpleMatrix du;// = new SimpleMatrix(u_size,1);

        /*
         * BackTrack Line Search
         */
        SimpleMatrix new_u = new SimpleMatrix(coeffs_size,1);
        SimpleMatrix new_x = new SimpleMatrix(u_size,1);

        SimpleMatrix f;
        f = new SimpleMatrix(u_size*2,1);
        for (int i=0; i < u_size*2; i++){
            f.set(i,0,-1);
        }
        SimpleMatrix new_f = new SimpleMatrix(u_size*2,1);

        int lsiter, r_locale;
        double maxAtnu;
        double normL1;
        double[] d3Array = new double[u_size];
        double phi, new_phi, logfSum, gdx, normg, pcgtol;
        double am_value, u_value, invdiff2, cnir_row, cnir_col, value_at_g1, value_at_g2, sum, diff, value_at_d1, invdiff;


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

            p_dd_r_of_am = p_dd_r(am_vector, r_vector, inv_d);

            //normL1 = normL1(p_dd_r_of_am) + 0.00000001*Math.abs(am.get(0,0));
            normL1 = normL1(p_dd_r_of_am);  // L1 norm of the second derivative of P(r)

            pobj = (z.transpose().mult(z)).get(0,0) + (lambda * normL1);
            /*
             *  dobj  =  max(-0.25*nu'*nu-nu'*y,dobj);
             */
            dobj = Math.max(( (nu.transpose().mult(nu)).get(0,0)*(-0.25) - ((nu.transpose().mult(y_vector))).get(0,0) ), dobj);
            gap   =  pobj - dobj; // should always be non-negative

            //------------------------------------------------------------
            //       Shall we Stop?
            //------------------------------------------------------------
            //System.out.println(ntiter + " : " + gap +" GAP  ratio " + (gap/dobj ) + " " + pobj + " dobj " + dobj);
            if (gap/dobj < reltol) {
                status = "SOLVED : " + ntiter + " ratio " + (gap/dobj) +  " < " + reltol + " GAP: " + gap + " step " + s + " PITR " + pitr;
                //System.out.println(status);
                break calculationLoop;
            }

            //------------------------------------------------------------
            //       UPDATE t
            //------------------------------------------------------------

            if (s >= 0.5 && gap > 0){
                t = Math.max(Math.min(twoCoeffsMU/gap, MU*t), t);
            }
            inv_t = 1.0/t;

            //------------------------------------------------------------
            //      CALCULATE NEWTON STEP
            //------------------------------------------------------------
            //
            // gradphi = [At*(z*2)-(q1-q2)/t; lambda*ones(n,1)-(q1+q2)/t];
            // p_am_r = p_dd_r_of_am.extractVector(false,0);
            p_am_r2 = p_dd_r_of_am.elementMult(p_dd_r_of_am);
            p_u_r2 = u.elementMult(u);

            /*
             * Partitioned Matrix terms for Hessian
             * D1: n x n
             * D2: n x u_size
             */
            for(int row=0; row < u_size; row++){

                for(int col=0; col < u_size; col++){

                    if(row==0 && col==0){ // background term ,should have the effect of minimizing its absolute value

                        am_value = am_vector.get(0,0);
                        u_value = u.get(0,0);
                        invdiff = 1.0/(p_u_r2.get(0,0) - am_value*am_value);
                        invdiff2 = invdiff*invdiff*inv_t;

                        d1.set(0,0, 2*(u_value*u_value + am_value*am_value)*invdiff2);
                        d2.set(0,0, -4*u_value*am_value*invdiff2);

                        //d1.set(0,0,0);
                        //d2.set(0,0,0);

                    } else if (row==0) { //assemble nxn d1 matrix

                        if (col < coeffs_size){
                            d1.set(0,col, 0);
                        }

                        d2.set(0,col, 0);

                    } else if (col==0){

                        if (row < coeffs_size) {
                            d1.set(row,0, 0);
                            d2.set(row,0, 0);
                        }

                    } else if (row > 0 && col > 0) {

                        if ((col < coeffs_size) && (row < coeffs_size)) { // d1 matrix
                            //assemble nxn d1 matrix of mixed partials
                            value_at_d1 = 0.0;
                            // first element is gradient of a_o which does not depend on

                            for(int r=0; r < r_vector_size; r++){
                                // first element of r_vector is nonzero
                                cnir_row = c_ni_r(row, r_vector[r], inv_d); // first derivative of p"(r) with respect to coefficients
                                cnir_col = c_ni_r(col, r_vector[r], inv_d); // first derivative of p"(r) with respect to coefficients

                                diff = p_u_r2.get(r+1,0) - p_am_r2.get(r,0);
                                invdiff2 = 1.0/(diff*diff);

                                sum = p_u_r2.get(r+1,0) + p_am_r2.get(r,0);
                                value_at_d1 += 2.0*cnir_row*cnir_col*sum*invdiff2;
                            }

                            r_locale = row-1; // u_vector and r_vector are not indexed the same.
                            diff = p_u_r2.get(row,0) - p_am_r2.get(r_locale,0);
                            invdiff2 = 1.0/(diff*diff);

                            d1.set(row,col, value_at_d1*inv_t); // d1 only indexes to row length (coeffs)
                            //
                            d2.set(row,col, -4.0*u.get(col,0)*p_dd_r_of_am.get(r_locale,0)*c_ni_r(row, r_vector[r_locale], inv_d)*invdiff2*inv_t);

                        } else {
                           /*
                            * u_vector is r_limit + 1
                            * r_vector is r_limit
                            * +1 is from a_0 term (constant background)
                            */
                            r_locale = row - 1; // u_vector and r_vector are not indexed the same.
                            diff = p_u_r2.get(row,0) - p_am_r2.get(r_locale,0);
                            invdiff2 = 1.0/(diff*diff);

                            if (row < coeffs_size){
                                d2.set(row, col, -4*c_ni_r(row, r_vector[r_locale], inv_d)*p_dd_r_of_am.get(r_locale,0)*u.get(col,0)*invdiff2*inv_t);
                            }
                        }
                    }
                }

                /*
                 * D3 Matrix
                 * D3: u_size x u_size
                 */
                if(row==0) {
                    /*
                     * constant background term, first element of a_m vector and u vector
                     */
                    am_value = am_vector.get(0,0);
                    u_value = u.get(0,0);
                    invdiff = 1/(u_value*u_value - am_value*am_value);
                    invdiff2 = invdiff*invdiff;
                    d3Array[0] = 2*(u_value*u_value+am_value*am_value)*invdiff2*inv_t;
                } else {
                   /*
                    * u_vector is r_limit + 1
                    * r_vector is r_limit
                    * +1 is from a_0 term (constant background)
                    */
                    r_locale = row-1; // u_vector and r_vector are not indexed the same.
                    diff = p_u_r2.get(row,0) - p_am_r2.get(r_locale,0);
                    sum = p_u_r2.get(row,0) + p_am_r2.get(r_locale,0);

                    invdiff2 = 1/(diff*diff)*inv_t;
                    d3Array[row] = 2*sum*invdiff2;
                }
            }

            d3 = SimpleMatrix.diag(d3Array);

            // gradient
            gradphi0 = a_transpose.mult(z.scale(2.0));

            for (int row=0; row<u_size; row++){

                value_at_g1 = 0.0;
                if (row < 1) {

                    am_value = am_vector.get(0,0);
                    u_value = u.get(0,0);
                    invdiff = 1.0/(p_u_r2.get(0,0) - am_value*am_value)*inv_t;
                    value_at_g1 = 2.0*am_value*invdiff;

                    gradux.set(row, 0, gradphi0.get(row,0) + value_at_g1);
                    value_at_g2 = -2.0*u_value*invdiff;

                } else {

                    if (row < coeffs_size){

                        for(int r=0; r < r_vector_size; r++){
                            cnir_row = c_ni_r(row, r_vector[r], inv_d);
                            diff = p_u_r2.get(r+1,0) - p_am_r2.get(r,0);
                            invdiff2 = 1.0/(diff*diff);

                            sum = p_u_r2.get(r+1,0) + p_am_r2.get(r,0);
                            value_at_g1 += 2.0*cnir_row*sum*invdiff2;
                        }

                        gradux.set(row, 0, gradphi0.get(row,0) + value_at_g1*inv_t);
                    }

                    diff = p_u_r2.get(row,0) - p_am_r2.get(row-1,0);
                    u_value = u.get(row,0);

                    value_at_g2 = -2.0*u_value/diff;
                }

                gradux.set(row+coeffs_size,0, lambda + value_at_g2*inv_t);
            }

            normg = gradux.normF();
            pcgtol = Math.min(0.1, eta*gap/Math.min(1,normg));

            if (ntiter != 0 && pitr == 0){
                pcgtol = 0.1*pcgtol;
            }

            hessian = hessphi(laplacian, d1, d2, d3);
            /*
             *
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
            gdx = gradux.transpose().mult(dxu).get(0,0);


            System.out.println(am_vector.getNumElements() + " " + coeffs_size);


            backtrackLoop:
            for (lsiter=0; lsiter < max_ls_iter; lsiter++){
                /*
                 * using new_x, calculate new p_dd_r
                 */
                new_x = am_vector.plus(dx.scale(s));
                p_dd_r_new = p_dd_r(new_x, r_vector, inv_d);
                new_u = u.plus(du.scale(s));

                // set background term, ff=0
                am_value = new_x.get(0,0);
                u_value = new_u.get(0,0);
                new_f.set(0,0, am_value - u_value);
                new_f.set(u_size,0, -am_value - u_value);


                for(int ff=1; ff<u_size; ff++){
//                    if (ff<1){ // background term
//                        am_value = new_x.get(0,0);
//                        u_value = new_u.get(0,0);
//                        new_f.set(0,0, am_value - u_value);
//                        new_f.set(u_size,0, -am_value - u_value);
//                    } else {
                        am_value = p_dd_r_new.get(ff-1,0);
                        u_value = new_u.get(ff,0);
                        new_f.set(ff,0, am_value - u_value);
                        new_f.set(u_size+ff,0, -am_value - u_value);
//                    }
                }

                if (max(new_f) < 0){

                    new_z = (a_matrix.mult(new_x)).minus(y_vector);
                    logfSum = 0.0;

                    for(int fi=0; fi<new_f.numRows(); fi++){
                        logfSum+=FastMath.log(-1*new_f.get(fi));
                    }

                    new_phi = (new_z.transpose().mult(new_z)).get(0,0)+lambda*new_u.elementSum()-logfSum*inv_t;

                    if (new_phi-phi <= alpha*s*gdx){
                        break backtrackLoop;
                    }
                }
                s = beta*s;
            } // end backtrack loop

            if (lsiter == max_ls_iter){
               // System.out.println("Max LS iteration ");
                break calculationLoop;
            }

            f.set(new_f);
            am_vector.set(new_x);
            u.set(new_u);
        }

        coefficients = new double[coeffs_size];


        for (int j=0; j < coeffs_size; j++){
            coefficients[j] = am_vector.get(j,0);
        }

        totalCoefficients = coefficients.length;
        this.setPrDistribution();
        this.calculateIzeroRg();

        // I_calc based standardized data
//        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        coeffs_size = coefficients.length;

//        // calculate residuals
//        residuals = new XYSeries("residuals");
//        for(int i=0; i < rows; i++){
//            XYDataItem item = data.getDataItem(i); // isn't quite correct, using data to get q-values, should be based on input for making design matrix
//            residuals.add(item.getX(), tempResiduals.get(i,0));
//        }
    }


    @Override
    void calculateIzeroRg() {
        double i_zero = 0;
        double partial_rg = 0;
        double rsum = 0;
        double negativeOnePower, am_inv_pow_3;

        double am;
        //double pi_sq = 9.869604401089358;
        int sizeOf = coefficients.length;

        for (int i = 1; i < sizeOf; i++) {
            am = coefficients[i];
            negativeOnePower = Math.pow(-1, (i+1));
            am_inv_pow_3 = am/Math.pow(i,3);

            i_zero = i_zero + am/(i)*negativeOnePower;
            partial_rg = partial_rg + am_inv_pow_3*(n_pi_squared[i] - 6)*negativeOnePower;
            rsum = rsum + am_inv_pow_3*((n_pi_squared[i] - 2)*negativeOnePower - 2 );
        }

        double dmax2 = dmax*dmax;
        double dmax3 = dmax2*dmax;
        double dmax4 = dmax2*dmax2;
//        double inv_pi_cube = 1.0/(pi_sq*Math.PI);
//        double inv_pi_fourth = inv_pi_cube/Math.PI;
        double inv_pi_fourth = 1.0/(n_pi_squared[1]*n_pi_squared[1]);

        izero = standardizedScale *(TWO_INV_PI*i_zero*dmax2/Math.PI + coefficients[0] + standardizedMin);

        double izero_temp = (TWO_INV_PI*i_zero*dmax2/Math.PI + coefficients[0]);
        //double izero_temp = (twodivPi*i_zero*dmax2/Math.PI + mooreCoefficients[0]);

        rg = Math.sqrt(2*dmax4*inv_pi_fourth/izero_temp*partial_rg)*0.7071067811865475; // 1/Math.sqrt(2);
        rAverage = 2*dmax3*inv_pi_fourth/izero_temp*rsum;

    }



    @Override
    public void setPrDistribution(){
        prDistribution = new XYSeries("PRDistribution");
        double totalPrPoints = (Math.ceil(qmax*dmax/Math.PI)*3); // divide dmax in ns*3 bins
        totalInDistribution = (int)totalPrPoints;
        double deltar = dmax/totalPrPoints;

        double resultM;
        double invtwopi2 = standardizedScale/(2.0*n_pi_squared[1]);
        double pi_dmax_r;
        double r_value;
        negativeValuesInModel = false;
        prDistribution.add(0.0d, 0.0d);

        for (int j=1; j < totalInDistribution; j++){

            r_value = j*deltar;
            pi_dmax_r = PI_INV_DMAX*r_value;
            resultM = 0;

            for(int i=1; i < totalCoefficients; i++){
                resultM += coefficients[i]*FastMath.sin(pi_dmax_r*i);
            }

            prDistribution.add(r_value, invtwopi2 * r_value * resultM);

            if (resultM < 0){
                negativeValuesInModel = true;
            }
        }

        prDistribution.add(dmax,0);

        // set distribution for fitting
        int size = r_vector_size_for_fitting.length;
        prDistributionForFitting = new XYSeries("output");

        for(int i=0; i < size; i++){ // last bin should be dmax

            r_value = r_vector_size_for_fitting[i];
            pi_dmax_r = PI_INV_DMAX*r_value;
            resultM = 0;

            for(int k=1; k < totalCoefficients; k++){
                resultM += coefficients[k]*FastMath.sin(pi_dmax_r*k);
            }

            prDistributionForFitting.add(r_value, invtwopi2 * r_value * resultM);
        }

        System.out.println("FINAL ");
        for(int k=0; k < totalCoefficients; k++){
            System.out.println(k + " " + coefficients[k]);
        }


    }


    private void setHeaderDetails(){
        this.description  = String.format("REMARK 265  P(r) DISTRIBUTION OBTAINED AS INDIRECT FOURIER TRANSFORM OF I(q) %n");
        this.description += String.format("REMARK 265  COEFFICIENTS ARE THE MOORE COEFFICIENTS FROM SINE INTEGRAL TRANSFORM %n");
        this.description  = String.format("REMARK 265  PERFORMED WITH L1-NORM REGULARIZATION %n");
        this.description  = String.format("REMARK 265  REGULARIZATION => minimize|ABS_VALUE SECOND DERIVATIVEE PR|  %n");
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

        this.description += String.format("REMARK 265  MOORE COEFFICIENTS (UNSCALED)%n");
        for (int i=1; i<totalCoefficients;i++){
            this.description +=  String.format("REMARK 265                        m_(%2d) : %.3E %n", i, coefficients[i]);
        }
    }


    /**
     * returns non-standardized q*I(q) calculated at specified q-value
     * @param qvalue
     * @return
     */
    @Override
    public double calculateQIQ(double qvalue) {

        double dmaxq = dmax * qvalue;
        double resultM = coefficients[0]; // if coefficients is 0, means no background

        for(int i=1; i < totalCoefficients; i++){
            //resultM = resultM + coefficients[i]*dmaxPi*i*FastMath.pow(-1,i+1)*FastMath.sin(dmaxq)/(Constants.PI_2*i*i - dmaxq*dmaxq);
            resultM = resultM + coefficients[i]*dmax_PI_TWO_INV_PI*i*FastMath.pow(-1,i+1)*FastMath.sin(dmaxq)/(n_pi_squared[i] - dmaxq*dmaxq);
        }

        return resultM*standardizedScale + standardizedMin;
    }

    @Override
    public double calculateIQ(double qvalue) {
        return this.calculateQIQ(qvalue)/qvalue;
    }


    @Override
    public double calculatePofRAtR(double r_value, double scale){

        double pi_dmax_r = Math.PI*r_value/dmax;
        double resultM = 0;

        for(int i=1; i < totalCoefficients; i++){
            resultM += coefficients[i]*FastMath.sin(pi_dmax_r*i);
        }

        return (inv2PI2*standardizedScale) * r_value * resultM*scale;
    }

    @Override
    public void estimateErrors(XYSeries fittedqIq){

        XYSeries tempPrFit = new XYSeries("for fit");

        try {
            tempPrFit = prDistributionForFitting.createCopy(0, prDistributionForFitting.getItemCount()-1);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        double[] oldCoefficients = new double[totalCoefficients];
        for(int i=0; i<totalCoefficients; i++){ // copy old coefficients temporarily
            oldCoefficients[i] = coefficients[i]; // will be over written in the estimate
        }

        double dmax2 = dmax*dmax;
        double dmax4 = dmax2*dmax2;
        double pi_sq = 9.869604401089358;
        double inv_pi = 1.0/Math.PI;
        double inv_pi_cube = 1.0/(pi_sq*Math.PI);
        double inv_pi_fourth = inv_pi_cube*inv_pi;
        double dmax4_inv_pi_fourth = 2*dmax4*inv_pi_fourth;
        double twodivPi = 2.0*inv_pi;
        double pi_k_2, am, am3, minus1;

        int size = fittedqIq.getItemCount();
        double upperq = fittedqIq.getMaxX();
        double bins = totalCoefficients-1;
        double delta_q = upperq/bins;
        double samplingLimit;

        XYDataItem tempData;
        ArrayList<double[]> results;
        int totalRuns = 31;
        double[] rgValues = new double[totalRuns];
        double[] izeroValues = new double[totalRuns];
        double tempIzero, partial_rg, rsum;

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

                    samplingLimit = (1.0 + randomGenerator.nextInt(17))/100.0;  // return a random percent up to ...

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
                        randomSeries.add(tempData.getXValue(), (tempData.getYValue()/tempData.getXValue()-standardizedMin)*invStdev*tempData.getXValue());
                    }
                } // end of checking if bin is empty
            }

            // calculate PofR
            this.createDesignMatrix(randomSeries);

            if (useL1){
                this.includeBackground = true;
                this.moore_pr_L1();
            } else { // must always use background when doing second derivative L1
                this.moore_coeffs_L1();
            }

            // calculate Izero and Rg
            tempIzero = 0;
            partial_rg = 0;
            rsum = 0;

            for (int k = 1; k < bins+1; k++) {
                am = coefficients[k];
                minus1 = FastMath.pow(-1, (k+1));

                tempIzero = tempIzero + am/(k)*minus1;
                pi_k_2 = pi_sq*k*k;
                am3 = am/(double)(k*k*k);

                partial_rg = partial_rg + am3*(pi_k_2 - 6)*minus1;
                rsum = rsum + am3*((pi_k_2 - 2)*minus1 - 2 );
            }

            //tempIzero = twodivPi*tempIzero*dmax2*inv_pi + results.get(0)[0];
            //tempIzero = standardizationStDev*(twodivPi*tempIzero*dmax2*inv_pi + mooreCoefficients[0]) + standardizationMean;
            double izero_temp = (twodivPi*tempIzero*dmax2/Math.PI + coefficients[0]);
            rgValues[i] = Math.sqrt(dmax4_inv_pi_fourth/izero_temp*partial_rg)*0.7071067811865475;
            izeroValues[i] = standardizedScale*(twodivPi*tempIzero*dmax2*inv_pi + coefficients[0]) + standardizedMin;
        }

        DescriptiveStatistics rgStat = new DescriptiveStatistics(rgValues);
        DescriptiveStatistics izeroStat = new DescriptiveStatistics(izeroValues);

        rgError = rgStat.getStandardDeviation()/rgStat.getMean();
        iZeroError = izeroStat.getStandardDeviation()/izeroStat.getMean();

        for(int i=0; i<totalCoefficients; i++){ // copy back the coefficients
            coefficients[i] = oldCoefficients[i];
        }

        prDistributionForFitting.clear();
        for(int i=0; i<tempPrFit.getItemCount(); i++){
            prDistributionForFitting.add(tempPrFit.getDataItem(i));
        }

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


}
