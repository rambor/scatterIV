package version4.InverseTransform;

import net.jafama.FastMath;
import org.apache.commons.math3.analysis.function.Constant;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import org.ejml.simple.SimpleMatrix;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Constants;
import version4.Functions;
import version4.Interpolator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public abstract class IndirectFT implements RealSpacePrObjectInterface, Cloneable {

    final double[] n_pi_squared = {
            0.000,
            9.869604401089358,
            39.47841760435743,
            88.82643960980423,
            157.91367041742973,
            246.74011002723395,
            355.3057584392169,
            483.6106156533785,
            631.6546816697189,
            799.437956488238,
            986.9604401089358,
            1194.2221325318121,
            1421.2230337568676,
            1667.9631437841015,
            1934.442462613514,
            2220.6609902451055,
            2526.6187266788756,
            2852.3156719148246,
            3197.751825952952,
            3562.9271887932578,
            3947.8417604357433,
            4352.495540880407,
            4776.8885301272485,
            5221.0207281762705,
            5684.89213502747,
            6168.502750680849,
            6671.852575136406,
            7194.941608394141,
            7737.769850454056,
            8300.33730131615,
            8882.643960980422,
            9484.689829446874,
            10106.474906715503,
            10747.99919278631,
            11409.262687659299,
            12090.265391334464,
            12791.007303811808,
            13511.488425091331,
            14251.708755173031,
            15011.668294056912,
            15791.367041742973,
            16590.80499823121,
            17409.98216352163,
            18248.898537614223,
            19107.554120508994,
            19985.948912205953,
            20884.082912705082,
            21801.95612200639,
            22739.56854010988,
            23696.920167015545,
            24674.011002723397,
            25670.841047233418,
            26687.410300545624,
            27723.718762660006,
            28779.766433576566,
            29855.55331329531,
            30951.079401816223,
            32066.344699139325,
            33201.3492052646,
            34356.09292019206,
            35530.57584392169,
            36724.7979764535,
            37938.759317787495,
            39172.45986792366,
            40425.89962686201,
            41699.078594602535,
            42991.99677114524,
            44304.65415649013,
            45637.050750637194,
            46989.186553586434,
            48361.061565337855,
            49752.67578589146,
            51164.02921524723,
            52595.12185340518,
            54045.953700365324,
            55516.52475612764,
            57006.835020692124,
            58516.8844940588,
            60046.67317622765,
            61596.201067198686,
            63165.46816697189,
            64754.47447554727,
            66363.21999292485,
            67991.70471910459,
            69639.92865408651,
            71307.8917978706,
            72995.59415045689,
            74703.03571184535,
            76430.21648203598,
            78177.13646102881,
            79943.79564882381,
            81730.19404542097,
            83536.33165082033,
            85362.20846502185,
            87207.82448802557,
            89073.17971983146,
            90958.27416043953,
            92863.10780984977,
            94787.68066806218,
            96731.9927350768,
            98696.04401089359,
            100679.83449551254,
            102683.36418893367,
            104706.633091157,
            106749.6412021825,
            108812.38852201017,
            110894.87505064002,
            112997.10078807206,
            115119.06573430626,
            117260.76988934266,
            119422.21325318124,
            121603.39582582198,
            123804.3176072649,
            126024.97859751001,
            128265.3787965573,
            130525.51820440678,
            132805.3968210584,
            135105.01464651222,
            137424.37168076824,
            139763.4679238264,
            142122.30337568675,
            144500.87803634928,
            146899.191905814,
            149317.2449840809,
            151755.03727114998,
            154212.5687670212,
            156689.83947169463,
            159186.84938517027,
            161703.59850744804,
            164240.086838528,
            166796.31437841014,
            169372.2811270945,
            171967.98708458096,
            174583.43225086966,
            177218.6166259605,
            179873.54020985356,
            182548.20300254878,
            185242.60500404617,
            187956.74621434574,
            190690.62663344748,
            193444.24626135142,
            196217.6050980575,
            199010.70314356583,
            201823.54039787626,
            204656.11686098893,
            207508.43253290374,
            210380.48741362072,
            213272.28150313994,
            216183.8148014613,
            219115.08730858483,
            222066.09902451056,
            225036.8499492384,
            228027.3400827685,
            231037.56942510078,
            234067.5379762352,
            237117.24573617184,
            240186.6927049106,
            243275.87888245154,
            246384.80426879475,
            249513.46886394004,
            252661.87266788757,
            255830.01568063724,
            259017.89790218908,
            262225.51933254313,
            265452.8799716994,
            268699.97981965775,
            271966.81887641834,
            275253.3971419811,
            278559.71461634606,
            281885.7712995132,
            285231.5671914824,
            288597.10229225387,
            291982.37660182756,
            295387.3901202034,
            298812.1428473814,
            302256.6347833615,
            305720.8659281439,
            309204.8362817285,
            312708.54584411526,
            316231.9946153041,
            319775.18259529525,
            323338.10978408845,
            326920.7761816839,
            330523.1817880815,
            334145.3266032813,
            337787.21062728326,
            341448.8338600874,
            345130.1963016938,
            348831.29795210226,
            352552.13881131297,
            356292.71887932584,
            360053.0381561409,
            363833.0966417581,
            367632.89433617744,
            371452.43123939907,
            375291.7073514228,
            379150.7226722487,
            383029.4772018768,
            386927.9709403072,
            390846.2038875397,
            394784.17604357435,
            398741.88740841113,
            402719.33798205014,
            406716.5277644914,
            410733.4567557347,
            414770.1249557802,
            418826.532364628,
            422902.6789822778,
            426998.56480873,
            431114.1898439843,
            435249.55408804066,
            439404.6575408993,
            443579.5002025601,
            447774.08207302314,
            451988.4031522882,
            456222.46344035555,
            460476.26293722505,
            464749.8016428967,
            469043.0795573706,
            473356.09668064676,
            477688.85301272495,
            482041.3485536053,
            486413.5833032879,
            490805.5572617727,
            495217.2704290596,
            499648.7228051487,
            504099.91439004004,
            508570.84518373344,
            513061.5151862292,
            517571.924397527,
            522102.0728176271,
            526651.9604465293,
            531221.5872842337,
            535810.9533307402,
            540420.0585860489,
            545048.9030501598,
            549697.486723073,
            554365.8096047881,
            559053.8716953056,
            563761.6729946252,
            568489.213502747,
            573236.493219671,
            578003.5121453971,
            582790.2702799255,
            587596.767623256,
            592423.0041753887,
            597268.9799363236,
            602134.6949060606,
            607020.1490845999,
            611925.3424719413,
            616850.2750680848
    };

    public double[] coefficients, prior_coefficients; // 1 size larger than am_vector, holds the background term at [0]

    public final double qmax, dmax, lambda;
    public final double inv2PI2 = 1.0/(2*Math.PI*Math.PI);
    public final double INV_PI = 1.0/Math.PI;
    public final double TWO_INV_PI = 2.0/Math.PI;
    public XYSeries nonData; // non-standardized data, not used for fitting
    public XYSeries data, residuals, errors, standardVariance; // should be qI(q) dataset ( non-standardized)
    public double standardizedMin;
    public double standardizedScale;
    public boolean includeBackground = true;
    public double rg, izero, rAverage, sphericalRg;
    public double rgError, iZeroError, rAverageError;
    public int totalInDistribution, totalCoefficients, rows;
    public XYSeries prDistribution;
    public XYSeries sphericalDistribution;
    public XYSeries sphericalDistributionFine;
    public XYSeries prDistributionForFitting; //used by IkeTama for Modeling, this is the Shannon Limited PrDistribution
    public boolean negativeValuesInModel;
    public PolynomialSplineFunction splineFunction;
    public String description="";
    public double sphericalVolume;
    public double degreesOfFreedom, distribution_score;
    public double n_chipoints, aic, prScore;

    /*
     * LINE SEARCH PARAMETERS
     */
    public double alpha = 0.01;            // minimum fraction of decrease in the objective
    public double beta  = 0.17;             // stepsize decrease factor
    public int max_ls_iter = 400;          // maximum backtracking line search iteration
    /*
     * Interior Point Parameters
     */
    public int MU = 2;                    // updating parameter of t
    public int max_nt_iter = 500;         // maximum IPM (Newton) iteration
    public double reltol = 0.0001;
    public double eta = 0.001;
    public double tau = 0.01;
    public double bin_width;
    public int pcgmaxi = 5000;
    public double dobj = Double.NEGATIVE_INFINITY;
    public double pobj = Double.POSITIVE_INFINITY;
    public final double dmax_PI_TWO_INV_PI;
    public final double PI_INV_DMAX;
    public String status;
    public int ns, coeffs_size;
    public SimpleMatrix a_matrix;
    public SimpleMatrix y_vector;
    public SimpleMatrix am_vector;
    private String modelUsed;
    public double area;
    public boolean priorExists=false;
//    public double finalScore;

    public IndirectFT(XYSeries nonStandardizedData, XYSeries errors, double dmax, double qmax, double lambda, boolean includeBackground){

        try {
            this.nonData = nonStandardizedData.createCopy(0, nonStandardizedData.getItemCount()-1);
            this.errors = errors.createCopy(0, errors.getItemCount()-1);; // must be transformed back to unscaled (see createNonStandardizedData)
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        this.lambda = lambda;
        this.qmax = qmax;
        this.dmax = dmax;
        this.includeBackground = includeBackground;
        this.dmax_PI_TWO_INV_PI = dmax * Math.PI * TWO_INV_PI;
        this.PI_INV_DMAX = Math.PI/dmax;
        this.standardizeData();
    }


    public IndirectFT(XYSeries standardizedData, XYSeries scaledqIqerrors, double dmax, double qmax, double lambda, boolean includeBackground, double standardizationMin, double standardizationStDev){

        try {
            this.data = standardizedData.createCopy(0, standardizedData.getItemCount()-1);
            this.errors = scaledqIqerrors.createCopy(0, standardizedData.getItemCount()-1);; // must be transformed back to unscaled (see createNonStandardizedData)
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        this.qmax = qmax;
        this.dmax = dmax;
        this.lambda = lambda;
        this.includeBackground = includeBackground;
        this.dmax_PI_TWO_INV_PI = dmax * Math.PI * TWO_INV_PI;
        this.PI_INV_DMAX = Math.PI/dmax;
        standardizedMin = standardizationMin;
        standardizedScale = standardizationStDev;
        this.createNonStandardizedData();
    }


    /**
     * copy constructor
     * @param original
     */
    public IndirectFT(IndirectFT original){

        try {
            this.data = original.data.createCopy(0, original.data.getItemCount()-1);
            this.errors = original.errors.createCopy(0, original.errors.getItemCount()-1);
            this.nonData = original.nonData.createCopy(0, original.nonData.getItemCount()-1);

            if (original.residuals instanceof XYSeries){
                this.residuals = original.residuals.createCopy(0, original.residuals.getItemCount()-1);
            }

            this.standardVariance = original.standardVariance.createCopy(0, original.standardVariance.getItemCount()-1);
            this.prDistribution = original.prDistribution.createCopy(0, original.prDistribution.getItemCount()-1);



            if (original.sphericalDistribution instanceof XYSeries){
                this.sphericalDistribution = original.sphericalDistribution.createCopy(0, original.sphericalDistribution.getItemCount()-1);
            }

            if (original.sphericalDistributionFine instanceof XYSeries){
                this.sphericalDistributionFine = original.sphericalDistributionFine.createCopy(0, original.sphericalDistributionFine.getItemCount()-1);
            }

            this.prDistributionForFitting = original.prDistributionForFitting.createCopy(0, original.prDistributionForFitting.getItemCount()-1);        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }



        coefficients = original.coefficients.clone();

        if (original.prior_coefficients instanceof double[] ){ // when switching IFT models, priors may not exist
            prior_coefficients = original.prior_coefficients.clone();
        }



        this.qmax = original.qmax;
        this.dmax = original.dmax;
        this.lambda = original.lambda;
        this.includeBackground = includeBackground;
        this.dmax_PI_TWO_INV_PI = dmax * Math.PI * TWO_INV_PI;
        this.PI_INV_DMAX = Math.PI/dmax;
        standardizedMin = original.standardizedMin;
        standardizedScale = original.standardizedScale;
        includeBackground = original.includeBackground;
        this.rg = original.rg;
        this.izero = original.izero;
        this.rAverage =  original.rAverage;
        this.sphericalRg = original.sphericalRg;
        this.rgError = original.rgError;
        this.iZeroError = original.iZeroError;
        this.rAverageError = original.rAverageError;
        this.totalInDistribution = original.totalInDistribution;
        this.totalCoefficients = original.totalCoefficients;
        this.rows = original.rows;
        this.negativeValuesInModel = original.negativeValuesInModel;
        this.description = original.description;
        this.sphericalVolume = original.sphericalVolume;
        this.degreesOfFreedom = original.degreesOfFreedom;
        this.distribution_score = original.distribution_score;
        this.n_chipoints = original.n_chipoints;
        this.aic = original.aic;
        this.prScore = original.prScore;

        if (totalInDistribution > 0){
            double[] totalrvalues = new double[totalInDistribution];
            double[] totalPrvalues = new double[totalInDistribution];

            // coefficients start at r_1 > 0 and ends at midpoint of last bin which is less dmax
            for(int i=0; i < totalInDistribution; i++){
                XYDataItem item = this.prDistribution.getDataItem(i);
                totalPrvalues[i] = item.getYValue();
                totalrvalues[i] = item.getXValue();
            }

            SplineInterpolator spline = new SplineInterpolator();
            this.splineFunction = spline.interpolate(totalrvalues, totalPrvalues);
        }


        this.alpha = original.alpha;
        this.beta = original.beta;
        this.max_ls_iter = original.max_ls_iter;



        this.bin_width = original.bin_width;
        this.status = original.status;

        this.coeffs_size = original.coeffs_size; // Shannon_number + 1 (if background included)
        this.ns = original.ns;

        this.a_matrix = original.a_matrix.copy();
        this.y_vector = original.y_vector.copy();
        this.am_vector = original.am_vector.copy();
        this.modelUsed = original.modelUsed;
        this.area = original.area;
        this.priorExists = original.priorExists;
    }





    @Override
    public double getStandardizedScale(){
        return standardizedScale;
    }

    @Override
    public double getStandardizedLocation(){
        return standardizedMin;
    }

    abstract void calculateIzeroRg();
    abstract void setPrDistribution();

    /**
     * implementing classes should calculate and set area
     * @return
     */
    @Override
    public double getArea(){
        return area;
    }

    @Override
    public double[] getCoefficients(){
        return coefficients;
    }

    /**
     * use MonteCarlo esque method for estimating errors through sampling dataset
     * @param fittedData
     */
    public abstract void estimateErrors(XYSeries fittedData);

    abstract void createDesignMatrix(XYSeries series);

    @Override
    public int getTotalInDistribution(){
        return totalInDistribution;
    }

    @Override
    public XYSeries getPrDistribution() {
        return prDistribution;
    }

    @Override
    public double getRg() {
        return rg;
    }

    @Override
    public double getRgError() {
        return rgError;
    }

    @Override
    public double getIZero() {
        return izero;
    }

    @Override
    public double getIZeroError() {
        return iZeroError;
    }

    @Override
    public double getRAverage(){
        return rAverage;
    }

    @Override
    public XYSeries getIqcalc() {
        return null;
    }

    @Override
    public XYSeries getqIqCalc() {
        return null;
    }

    public double normL1(SimpleMatrix vec){
        double sum = 0;
        int size = vec.getNumElements();

        for(int i=0; i<size; i++){
            sum += Math.abs(vec.get(i));
        }
        return sum;
    }

    public SimpleMatrix hessphi_coeffs_positivity_constrained(SimpleMatrix ata, SimpleMatrix d1, int coeffs_size) {

        SimpleMatrix hessian = new SimpleMatrix(coeffs_size, coeffs_size);

        SimpleMatrix t_ata;
        t_ata =  ata.plus(d1);

        for (int r=0; r < coeffs_size; r++){

            for(int c=0; c < coeffs_size; c++){
                hessian.set(r,c, t_ata.get(r,c));
            }

        }
        return hessian;
    }


    /**
     * Return maximum absolute value in vector set
     * @param vec
     * @return
     */
    public double inf_norm(SimpleMatrix vec){
        int sizeof = vec.getNumElements();
        double maxi = Math.abs(vec.get(0));
        double current;
        for(int i=1; i<sizeof; i++){
            current = Math.abs(vec.get(i));
            if (current > maxi){
                maxi = current;
            }
        }
        return maxi;
    }


    public double min(SimpleMatrix vec){

        double initial = vec.get(0);
        int length = vec.getNumElements();
        double current;

        for (int i=1; i<length; i++){
            current = vec.get(i);
            if (current < initial){
                initial = current;
            }
        }

        return initial;
    }

    public double getBin_width(){ return bin_width;}

    public double max(SimpleMatrix vec){
        double initial = vec.get(0);
        int length = vec.getNumElements();
        double current;

        for (int i=1; i<length; i++){
            current = vec.get(i);
            if (current > initial){
                initial = current;
            }
        }

        return initial;
    }

    public ArrayList<SimpleMatrix> linearPCGPositiveOnly(SimpleMatrix designMatrix, SimpleMatrix bMatrix, SimpleMatrix initial, SimpleMatrix d1, double pcgtol, int pcgmaxi){

        ArrayList<SimpleMatrix> returnElements = new ArrayList<SimpleMatrix>();

        int i=0;

        SimpleMatrix r_matrix = bMatrix.minus(designMatrix.mult(initial)); // residual
        SimpleMatrix r_plus_1;
        SimpleMatrix preConditioner = designMatrix.copy();

        int cols = d1.numCols();
        /*
         * P = M
         * Preconditioner, P is Hessian with first block set to:
         * M = 2*diagATA + D1
         *
         */
        for(int row=0; row < cols; row++){

            for(int col=0; col< cols; col++){

                if (row == col){
                    //preConditioner.set(row,row,2*designMatrix.get(row,row)+d1.get(row,row));
                    preConditioner.set(row, row, (2+d1.get(row,row)));
                }

                if (row != col){ // preserve diagonal entries and just add D1
                    //preConditioner.set(row, col, d1.get(row, col));
                    preConditioner.set(row, col, 0);
                }
            }
        }

        SimpleMatrix invertM = preConditioner.invert();
        SimpleMatrix z = invertM.mult(r_matrix); // search direction
        //SimpleMatrix z = invertM.mult(initial); // search direction
        SimpleMatrix p = z.copy();

        SimpleMatrix q;

        SimpleMatrix delx_vector = initial.copy();
        int xu_size = delx_vector.getNumElements();

        SimpleMatrix z_plus_1 ;//= new SimpleMatrix(xu_size,1);
        SimpleMatrix x_vector = new SimpleMatrix(cols, 1);

        double alpha;
        // stopping criteria
        double rkTzk = r_matrix.transpose().mult(z).get(0);

        double beta;
        //double stop = rkTzk*pcgtol*pcgtol;
       //double stop = pcgtol;

        //System.out.println("PCG rkTzk " + rkTzk + " " + stop);
double magB = 1.0/bMatrix.normF();
double topB = 1000;

        //while( (i < pcgmaxi) && (rkTzk > stop) ){
            while( (i < pcgmaxi) && topB > pcgtol ){

            q = designMatrix.mult(p);
            alpha = rkTzk/(p.transpose().mult(q)).get(0);

            // invert each element of dTq;
            delx_vector = delx_vector.plus(p.scale(alpha));

//            if (i < 50){
//                r_plus_1 = bMatrix.minus(designMatrix.mult(delx_vector));
//            } else {
//                r_plus_1 = r_matrix.minus(q.scale(alpha)); // r_k+1 = r_k - alpha_k * A*p_k
//            }
            r_plus_1 = r_matrix.minus(q.scale(alpha)); // r_k+1 = r_k - alpha_k * A*p_k

            z_plus_1 = invertM.mult(r_plus_1);

            //Polak-Ribiere Beta
            beta = z_plus_1.transpose().mult(r_plus_1.minus(r_matrix)).get(0,0)/(z.transpose().mult(r_matrix)).get(0,0);
            //Fletcher Reeves
            //beta = z_plus_1.transpose().mult(r_plus_1).get(0,0)*(z.transpose().mult(r)).get(0,0);
            /*
             * k => k + 1
             */
            p = z_plus_1.plus(p.scale(beta));
            r_matrix = r_plus_1.copy();
            rkTzk = r_matrix.transpose().mult(z_plus_1).get(0);
            z = z_plus_1.copy();

            topB = (bMatrix.minus(designMatrix.mult(delx_vector))).normF()*magB;

            i++;
        }

        //System.out.println("PCG STOP: " + stop + " > " + rkTzk + " at " + i);

        for(int ii=0; ii < xu_size; ii++){
            x_vector.set(ii, 0, delx_vector.get(ii));
        }

        // set final iterate number
        SimpleMatrix pitr = new SimpleMatrix(1,1);
        pitr.set(0,0,i);

        returnElements.add(x_vector);  // 0
        returnElements.add(delx_vector); // 1
        returnElements.add(pitr);      // 2
        return returnElements;
    }


    public SimpleMatrix hessphi_coeffs(SimpleMatrix ata, SimpleMatrix d1, SimpleMatrix d2, int coeffs_size) {

        int n = ata.numCols();

        SimpleMatrix hessian = new SimpleMatrix(2*coeffs_size,2*coeffs_size);
        SimpleMatrix t_ata;
        t_ata =  ata.plus(d1);

        double d2_value;

        for (int r=0; r < coeffs_size; r++){

            for(int c=0; c < coeffs_size; c++){
                hessian.set(r,c,t_ata.get(r,c));
            }

            d2_value = d2.get(r,r);
            hessian.set(r+n, r, d2_value);   //D2(transpose) lower-left
            hessian.set(r, r+n, d2_value);   //D2 upper-right
            hessian.set(r+n, r+n, d1.get(r,r)); // lower-right
        }
        return hessian;
    }

    /**
     *
     * @param designMatrix  Hessian
     * @param bMatrix  -gradient
     * @param initial dxu
     * @param d1
     * @param pcgtol
     * @param pcgmaxi
     * @param tauT
     * @return
     */
    public ArrayList<SimpleMatrix> linearPCG(SimpleMatrix designMatrix, SimpleMatrix bMatrix, SimpleMatrix initial, SimpleMatrix d1, double pcgtol, int pcgmaxi, double tauT){

        ArrayList<SimpleMatrix> returnElements = new ArrayList<SimpleMatrix>();
        SimpleMatrix r = bMatrix.minus(designMatrix.mult(initial)); // residual
        SimpleMatrix r_plus_1;
        SimpleMatrix preConditioner = designMatrix.copy();

        int n = d1.numCols();
        /*
         * P = M  D2
         *     D2 D3
         * Preconditioner, P is Hessian with first block set to:
         * M = 2*diagATA + D1
         *
         */
        //SimpleMatrix d3 = new SimpleMatrix(n,n);
        for(int row=0; row < n; row++){
            for(int col=0; col< n; col++){
                if (row == col){
                    //preConditioner.set(row,row, designMatrix.get(row,row));
                    preConditioner.set(row,row,2*tauT+d1.get(row,row));
                    // d3.set(row,row,2+d1.get(row,row));
                }
                if (row != col){ // preserve diagonal entries and just add D1
                    preConditioner.set(row, col, 0);
                    //System.out.println("d1 " + row + " " + d1.get(row, col));
                    //preConditioner.set(row, col, d1.get(row, col));
                }
            }
        }


        /*
        // Boyd Inversion Start
        SimpleMatrix d1d3minusd22 = d1.mult(d3).minus(d2.elementMult(d2));
        SimpleMatrix p1 = new SimpleMatrix(n,n);
        SimpleMatrix p2 = new SimpleMatrix(n,n);
        SimpleMatrix p3 = new SimpleMatrix(n,n);

        for(int row=0; row < n; row++){
             p1.set(row,row, d1.get(row,row)/d1d3minusd22.get(row,row));
             p2.set(row,row, d2.get(row,row)/d1d3minusd22.get(row,row));
             p3.set(row,row, d3.get(row,row)/d1d3minusd22.get(row,row));
        }
        // Boyd Inversion END
        */
        SimpleMatrix invertM = preConditioner.invert();
        SimpleMatrix z = invertM.mult(r); // search direction
        SimpleMatrix p = z.copy();

        SimpleMatrix q;

        SimpleMatrix xu_vector = initial.copy();
        int xu_size = xu_vector.getNumElements();

        SimpleMatrix z_plus_1 ;//= new SimpleMatrix(xu_size,1);

        SimpleMatrix x_vector = new SimpleMatrix(n, 1);
        SimpleMatrix u_vector = new SimpleMatrix(xu_size-n, 1);

        double alpha;
        // stopping criteria
        double rkTzk = r.transpose().mult(z).get(0);

        double beta;
        //double stop = rkTzk*pcgtol*pcgtol;
        //System.out.println("PCG rkTzk " + rkTzk + " " + stop);
        double magB = 1.0/bMatrix.normF();
        double topB = 1000;

        int i=0;
        while( (i < pcgmaxi) && (topB > pcgtol) ){

            q = designMatrix.mult(p);
            alpha = rkTzk/(p.transpose().mult(q)).get(0);

            // invert each element of dTq;
            xu_vector = xu_vector.plus(p.scale(alpha));

            if (i < 50){
                r_plus_1 = bMatrix.minus(designMatrix.mult(xu_vector));
            } else {
                r_plus_1 = r.minus(q.scale(alpha)); // r_k+1 = r_k - alpha_k * A*p_k
            }

            z_plus_1 = invertM.mult(r_plus_1);

            //Polak-Ribiere Beta
            beta = z_plus_1.transpose().mult(r_plus_1.minus(r)).get(0,0)/(z.transpose().mult(r)).get(0,0);
            //Fletcher Reeves
            //beta = z_plus_1.transpose().mult(r_plus_1).get(0,0)*(z.transpose().mult(r)).get(0,0);

            /*
             * k => k + 1
             */
            p = z_plus_1.plus(p.scale(beta));
            r = r_plus_1.copy();
            rkTzk = r.transpose().mult(z_plus_1).get(0);
            z = z_plus_1.copy();
            topB = (bMatrix.minus(designMatrix.mult(xu_vector))).normF()*magB;
            i++;
        }

        // System.out.println("PCG STOP: " + stop + " > " + rkTzk + " at " + i);

        for(int ii=0; ii<(xu_size-n); ii++){

            if (ii<n){
                x_vector.set(ii,0,xu_vector.get(ii));
            }

            u_vector.set(ii,0,xu_vector.get(ii+n));
        }

        // set final iterate number
        SimpleMatrix pitr = new SimpleMatrix(1,1);
        pitr.set(0,0,i);

        returnElements.add(x_vector);  // 0
        returnElements.add(u_vector);  // 1
        returnElements.add(xu_vector); // 2
        returnElements.add(pitr);      // 3
        return returnElements;
    }

    /**
     * value of second derivative calculated at points in r_vector
     * @param am Moore coefficients, assume am[0] is constant backgroud
     * @param r_vector set of equally distributions points along dmax
     * @param inv_d 1/dmax
     * @return
     */
    public SimpleMatrix p_dd_r(SimpleMatrix am, double[] r_vector, double inv_d){

        double r_value, pi_r_n_inv_d, pi_r_inv_d, cos_pi_r_n_inv_d, sin_pi_r_n_inv_d;
        double pi_inv_d = Math.PI*inv_d;
        double a_i;

        int r_limit = r_vector.length;

        SimpleMatrix p_dd_r = new SimpleMatrix(r_limit,1);

        int coeffs_size = am.getNumElements();

        double a_i_sum, pi_inv_d_n;

        for (int r=0; r < r_limit; r++){
            r_value = r_vector[r];
            pi_r_inv_d = pi_inv_d*r_value;
            a_i_sum = 0;

            for(int n=1; n < coeffs_size; n++){
                a_i = am.get(n,0);

                pi_inv_d_n = pi_inv_d*n;
                pi_r_n_inv_d = pi_r_inv_d*n;

                cos_pi_r_n_inv_d = FastMath.cos(pi_r_n_inv_d);
                sin_pi_r_n_inv_d = FastMath.sin(pi_r_n_inv_d); // if r_value is ZERO, sine is ZERO

                a_i_sum += 2*a_i*pi_inv_d_n*cos_pi_r_n_inv_d - r_value*a_i*pi_inv_d_n*pi_inv_d_n*sin_pi_r_n_inv_d;
            }

            p_dd_r.set(r,0, a_i_sum*0.5*inv_d);
        }

        return p_dd_r;
    }


    /**
     * 1st derivative of P"(r) with respect to the Moore coefficients
     * @param n_i
     * @param r
     * @param inv_d
     * @return
     */
    public double c_ni_r(int n_i, double r, double inv_d){
        double inv_d_pi_n = inv_d*Math.PI*n_i;
        double theta = r*inv_d_pi_n;
        double cir;
        //cir = inv_d*ni*FastMath.cos(theta) - Math.PI*r*0.5*inv_d*inv_d*ni*ni*FastMath.sin(theta);
        cir = 2.0*inv_d_pi_n*FastMath.cos(theta) - r*inv_d_pi_n*inv_d_pi_n*FastMath.sin(theta);
        return 0.5*inv_d*cir;
    }



    public SimpleMatrix hessphi(SimpleMatrix ata, SimpleMatrix d1, SimpleMatrix d2, SimpleMatrix d3){

        int h = d2.numCols();
        int n = ata.numCols();

        SimpleMatrix hessian = new SimpleMatrix(n+h,n+h);

        SimpleMatrix t_ata;
        t_ata =  ata.plus(d1); //ata and d1 are not modified

        double d2_value;

        for (int r=0; r < h; r++){

            for(int c=0; c < h; c++){

                if (r<n && c<n) {
                    d2_value = d2.get(r,c);
                    hessian.set(r,c,t_ata.get(r,c));    // ATA + D1
                    hessian.set(c+n, r, d2_value);      // D2(transpose) lower-left
                    hessian.set(r, c+n, d2_value);      // D2 upper-right
                } else if (r<n && c >=n) {
                    // d2 nxh
                    d2_value = d2.get(r,c);
                    hessian.set(c+n, r, d2_value);   //D2 (transpose) lower-left
                    hessian.set(r, c+n, d2_value);   //D2 upper-right
                }

                hessian.set(r+n, c+n, d3.get(r,c));
            }
        }
        //hessian.print();
        return hessian;
    }


    /**
     *
     * standardize data
     *
     * min-max normalization
     *
     */
    private void standardizeData(){
        XYDataItem tempData;
        data = new XYSeries("Standardized data");
        standardVariance = new XYSeries("standardized error");

        int totalItems = nonData.getItemCount();

        double sum = 0, minavg=10000000, tempminavg;
        int windowSize = (int)(0.07*totalItems);
        double invwindow = 1.0/(double)windowSize;
        for(int r=0; r<(totalItems-windowSize); r++){
            tempminavg = 0;
            for (int w=0; w<windowSize; w++){
                tempminavg += nonData.getDataItem(r).getYValue();
            }

            if (tempminavg < minavg){
                minavg = tempminavg;
            }
        }

        standardizedMin = minavg*invwindow;//nonData.getMinY();
        standardizedScale = Math.abs(nonData.getMaxY() - standardizedMin);
        double invstdev = 1.0/standardizedScale;

        double temperrorvalue;

        for(int r=0; r<totalItems; r++){
            tempData = nonData.getDataItem(r);
            data.add(tempData.getX(), (tempData.getYValue() - standardizedMin)*invstdev);
            temperrorvalue = errors.getY(r).doubleValue()*tempData.getXValue()*invstdev; // q*sigma/scale
            standardVariance.add(tempData.getX(), temperrorvalue*temperrorvalue); // get residual for this q-value
        }
    }


    /**
     * calculate residuals from current model and data
     */
    public void makeResiduals(){
        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        residuals = new XYSeries("residuals");
        for(int i=0; i < rows; i++){
            //double diff = tempResiduals.get(i,0);
            residuals.add(data.getDataItem(i).getX(), tempResiduals.get(i,0));
        }
    }

    /**
     * estimate chi-squared by evaluating the function at the points of the cardinal series
     * errors must be standardized
     *
     * @return
     */
    @Override
    public double getChiEstimate(){
        // how well does the fit estimate the cardinal series (shannon points)
        // how many points determined by Shannon Information Theory => ns
        double chi=0;
        double diffTolerance = 0.000001;
        double inv_card;
        final double pi_inv_dmax = Math.PI/dmax; // cardinal points are at increments of PI/dmax
        double error_value=0;
        // Number of Shannon bins, excludes the a_o
        int bins = ns, count=0, total = data.getItemCount();
        // ns is over-estimated as it includes a q-value that is not actually measured
        // so, how to calculate chi?
        // SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector);
        // calculate residuals
        makeResiduals();

        n_chipoints = 0; //1.0/(double)(bins-1);
        double cardinal, test_q = 0, diff;
        XYDataItem priorValue, postValue;
        // see if q-value exists but to what significant figure?
        // if low-q is truncated and is missing must exclude from calculation
        double residuals_sum = 0;
        double residuals_sum_squared = 0;
        ArrayList<Double> values = new ArrayList<>();

        /*
         * total points in chi calculation
         *
         *  first => is near 0.5*PI/dmax
         * second => 0.5*PI/dmax + PI/dmax
         *  third => 0.5*PI/dmax + 2PI/dmax
         *   last => if possible is nPI/dmax + 0.5*PI/dmax
         *
         *   shift points and adding one, so total bins should be
         *   total bins = ns + 1
         *
         */
        final double delta_q = Math.PI/(dmax*6.0d);
        //define q-values for calculating chi2
        int startIndex = 1;
        double maxQ = data.getMaxX();
        ArrayList<Integer> chiIndices = new ArrayList<>();

        startIndex = (int)Math.floor(data.getMinX()/delta_q); //check first point
        if ( (startIndex & 1) == 0){ // if even, starting index is greater than startIndex + 1
            startIndex += 1;
        } else { // if odd
            startIndex += 2;
        }

        double  before, after;//
        int startAt = 0;
        chiIndices.add(0);
        while (startIndex*delta_q < maxQ){

            if ( (startIndex & 1) != 0){
                double shannon_q = startIndex*delta_q;
                for(int i = startAt; startAt<total; i++){
                    XYDataItem xyitem = data.getDataItem(i);
                    if (xyitem.getXValue() > shannon_q){ // find first value greater than shannon_q

                        before = shannon_q - data.getX(i-1).doubleValue();
                        after = xyitem.getXValue() - shannon_q;
                        if (before < after){
                            chiIndices.add(i-1);
                        } else {
                            chiIndices.add(i);
                        }
                        startAt = i;
                        break;
                    }
                }
            }
            startIndex++;
        }


        for(int i=0; i<chiIndices.size();i++){
            error_value = 1.0/(standardVariance.getY(chiIndices.get(i)).doubleValue());
            diff = residuals.getY(chiIndices.get(i)).doubleValue();
            residuals_sum_squared += diff*diff;
            residuals_sum += diff;
            chi += (diff*diff)*(error_value);
            n_chipoints += 1.0;
        }


        for (int i=1; i <= -bins && i*pi_inv_dmax <= qmax; i++){

            cardinal = i*pi_inv_dmax; // <= q-value
            inv_card = 1.0/cardinal;
            // what happens if a bin is empty?
            searchLoop:
            while ( (test_q < cardinal) && (count < total) ){
                test_q = data.getX(count).doubleValue();
                // find first q-value >= shannon cardinal value
                if (test_q >= cardinal){
                    break searchLoop;
                }
                count++;
            }

            // if either differences is less than 0.1% use the measured value
            if (count > 0 && count < total){
                priorValue = data.getDataItem(count-1);
                postValue = data.getDataItem(count);
                if ((cardinal - priorValue.getXValue())*inv_card < diffTolerance) {// check if difference is smaller pre-cardinal
                    System.out.println(i + " PRE ");
                    error_value = 1.0/(standardVariance.getY(count-1).doubleValue());
                    diff = residuals.getY(count - 1).doubleValue();
                    residuals_sum_squared += diff*diff;
                    residuals_sum += diff;
                    chi += (diff*diff)*(error_value);
                    n_chipoints += 1.0;
                    values.add(diff);
                } else if ( Math.abs(postValue.getXValue() - cardinal)*inv_card < diffTolerance) {// check if difference is smaller post-cardinal
                    //error_value = 1.0/errors.getY(count).doubleValue()*inv_card*standardizedScale;
                    System.out.println(i + " POST ");
                    error_value = 1.0/(standardVariance.getY(count).doubleValue());
                    diff = residuals.getY(count).doubleValue();
                    chi += (diff*diff)*(error_value);
                    residuals_sum_squared += diff*diff;
                    residuals_sum += diff;
                    n_chipoints += 1.0;
                    values.add(diff);
                } else { // if difference is greater than 0.1% interpolate and also the cardinal is bounded

                    Interpolator tempI = new Interpolator(nonData, errors, cardinal); // interpolate intensity
                    diff = tempI.interpolatedValue - this.calculateQIQ(cardinal);

                    residuals_sum_squared += diff*diff;
                    residuals_sum += diff;
                    values.add(diff);

                    chi += (diff*diff)/(tempI.stderror * tempI.stderror*cardinal*cardinal);

                    System.out.println("Interpolated " + cardinal + " diff " + diff + " diff2: " + diff*diff + " sigma " + tempI.stderror + " CHI " + chi);
                    n_chipoints += 1.0;
                }
                System.out.println("          => " + cardinal + " diff " + diff + " diff2: " + diff*diff + " sigma " + error_value + " CHI " + chi + " count " + count + " " + total);
            }
        }

        double residualsMean = residuals_sum/n_chipoints;
        double residuals_variance = residuals_sum_squared/n_chipoints - residualsMean*residualsMean;
        int aic_k = ns + 2;

        //chi*=1.0/(double)n_chipoints;
        aic = 2.0*aic_k + chi + (2*aic_k*aic_k + 2*aic_k)/(n_chipoints - aic_k - 1);
//        System.out.println(n_chipoints + " " + ns + " " + n_chipoints/ns + " " + (n_chipoints-2.0)/ns);
//        System.out.println(dmax + " AIC " + aic + " chi " +chi/(double)(n_chipoints-2.0) + " " + n_chipoints + " " + chi/(double)aic_k + " :: " + chiIndices.size());

        double delta = ns - n_chipoints;  // if no missing bin, should equal zero

        // estimate decorellated uncertainties for the cardinal points
//        int totalerrors = errors.getItemCount();
//        SimpleMatrix stdev = new SimpleMatrix(errors.getItemCount(), 1); // n x 1
//        for(int i=0; i<totalerrors; i++){
//            stdev.set(i,0, errors.getY(i).doubleValue());
//        }

//        SimpleMatrix covariance = stdev.mult(stdev.transpose()); // n x n
//        // perform SVD
//        DenseMatrix64F tempForSVD = covariance.getMatrix();
//        SingularValueDecomposition<DenseMatrix64F> svd = DecompositionFactory.svd(totalerrors, totalerrors, true, true, false);
//        // A = U*W*V_t
//        try {
//            svd.decompose(tempForSVD);
//        } catch (Exception e){
//            System.out.println("Matrix inversion exception in svdReduce ");
//        }
//
//        double[] sing = svd.getSingularValues();
//        for(int i=0; i<coeffs_size+2; i++){
//            System.out.println(i + " SVD " + sing[i]);
//        }

        //System.out.println("CHI ESTIMATE count " + residuals.getItemCount());
        //degreesOfFreedom = (includeBackground) ? (totalCoefficients - delta - 1) : (totalCoefficients - 1.0 - delta);
        degreesOfFreedom = n_chipoints - 2;
        //System.out.println("TC " + totalCoefficients + " delta " + chi);
        //return aic;
        return chi*1.0/degreesOfFreedom;
    }


    /**
     * Use Durbin-Watson test for normality (randomness)
     * Number is reported as the median from random subselection
     * @param rounds
     * @return
     */
    @Override
    public double getKurtosisEstimate(int rounds){  // calculated from the residuals
         /*
         * Divide scattering curve into Shannon bins
         * Determine kurtosis from a random sample of the ratio of I_calc to I_obs based on the binning
         * Take max of the kurtosis set
         */
        //Random newRandom = new Random();
        int total = residuals.getItemCount(); // fittedqIq is scaled for fitting
        //double[] ratio = new double[total];
        ArrayList<Double> test_residuals = new ArrayList<Double>();

        /*
         * bin the ratio
         * qmax*dmax/PI
         *
         */
        double kurtosis_sum = 0;
        double[] kurtosises = new double[rounds];
        // calculate kurtosis
        double qmin = residuals.getMinX();
        double bins = ns*2.0;
        double delta_q = (residuals.getMaxX()-qmin)/bins;

        double samplingLimit, lowerLimit;
        Random randomGenerator = new Random();
        int[] randomNumbers;

        double numerator=0, value, diff;
        double denominator;// = residuals.getY(0).doubleValue()*residuals.getY(0).doubleValue();

        for (int i=0; i<rounds; i++){
            // for each round, get a random set of values from ratio
            int startbb = 0, upperbb = 0;
            test_residuals.clear();

            // same percent out of each bin
            //samplingLimit = (0.5 + randomGenerator.nextInt(12)) / 100.0;  // return a random percent up to 12%
            samplingLimit = 0.5;  // return a random percent up to 12%

            for (int b=1; b < bins; b++) {
                // find upper q in bin
                // SAMPLE randomly per bin
                lowerLimit = (delta_q * b + qmin);

                binloop:
                for (int j = startbb; j < total; j++) {
                    if (residuals.getX(j).doubleValue() >= lowerLimit) {
                        upperbb = j;
                        break binloop;
                    }
                }

                // grab indices inbetween startbb and upperbb
                //System.out.println("bin " + b + " " + (upperbb - startbb));
                randomNumbers = Functions.randomIntegersBounded(startbb, upperbb, samplingLimit);
                startbb = upperbb;

                for(int h=0; h < randomNumbers.length; h++){
                    //test_residuals.add(ratio[randomNumbers[h]]);
                    test_residuals.add(residuals.getY(randomNumbers[h]).doubleValue());
                }
            }

            //kurtosises[i] = StatMethods.kurtosis(test_residuals);
            /*
             * Durbin Watson estimate of randomness
             * d = Sum( e_t - e_[t-1])^2/Sum( e_t^2 )
             */
            numerator = 0;
            denominator = test_residuals.get(0)*test_residuals.get(0);
            for (int j=1; j<test_residuals.size(); j++){
                value = test_residuals.get(j);
                diff = value - test_residuals.get(j-1); // x_(t) - x_(t-1)
                numerator += diff*diff;
                denominator += value*value; // sum of (x_t)^2
            }

            kurtosises[i] = numerator/denominator;
            kurtosis_sum += kurtosises[i];
            //System.out.println(i + " KURT : " + kurtosises[i] + " SL : " + samplingLimit);
        }

        Arrays.sort(kurtosises);
        //return Math.abs(kurtosises[rounds-1]- kurtosises[0]);
        /*
         * take median from the estimate
         */

        /*
         * total kurtosis
         */
        numerator = 0;
        denominator = residuals.getY(0).doubleValue()*residuals.getY(0).doubleValue();
        for (int j=1; j<residuals.getItemCount(); j++){
            value = residuals.getY(j).doubleValue();
            diff = value - residuals.getY(j-1).doubleValue(); // x_(t) - x_(t-1)
            numerator += diff*diff;
            denominator += value*value; // sum of (x_t)^2
        }
        return Math.abs(2-numerator/denominator);
    }


    @Override
    public int getTotalFittedCoefficients(){
        return includeBackground ? totalCoefficients : totalCoefficients -1 ;
    }



    /**
     * Dataset must be standardized and transformed as q*Iq
     * @param dataset
     * @return
     */
    @Override
    public double calculateMedianResidual(XYSeries dataset){

        List<Double> residualsList = new ArrayList<Double>();
        int dataLimit = dataset.getItemCount();
        double resi;

        this.createDesignMatrix(dataset); // this creates a_matrix and y_vector

        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector); // this would resize rows and residuals via a_matrix and y_vector

        for (int i = 0; i < dataLimit; i++){
            resi = tempResiduals.get(i,0);
            residualsList.add(resi*resi);
        } //
        // need to resetDesignMatrix so that a_matrix and y_vector revert back to the original dataset
        return Statistics.calculateMedian(residualsList);
    }


    /**
     * Dataset must be standardized and transformed as q*Iq
     * @param tempDataset
     * @return
     */
    @Override
    public double calculateChiFromDataset(XYSeries tempDataset, XYSeries tempErrors){

        List<Double> residualsList = new ArrayList<Double>();
        int dataLimit = tempDataset.getItemCount();
        double resi;

        XYSeries fitme = new XYSeries("FITME");
        XYSeries errorsInUse = new XYSeries("errors");

        final double delta_q = Math.PI/(dmax*6.0d);
        //define q-values for calculating chi2
        double maxQ = tempDataset.getMaxX();
        ArrayList<Integer> chiIndices = new ArrayList<>();

        int startIndex = (int)Math.floor(tempDataset.getMinX()/delta_q); //check first point
        if ( (startIndex & 1) == 0){ // if even, starting index is greater than startIndex + 1
            startIndex += 1;
        } else { // if odd
            startIndex += 2;
        }
        int degreesOfFreedomcorrection = startIndex-1;

        double shannon_q = startIndex*delta_q;
        int startAt = 0;
        chiIndices.add(0);
        while (startIndex*delta_q < maxQ){
            if ( (startIndex & 1) != 0){
                shannon_q = startIndex*delta_q;
                for(int i = startAt; startAt<dataLimit; i++){
                    if (tempDataset.getX(i).doubleValue() > shannon_q){ // find first value greater than shannon_q
                        chiIndices.add(i);
                        startAt = i;
                        break;
                    }
                }
            }
            startIndex++;
        }

        chiIndices.add(dataLimit-1);


        for(int i=0; i<chiIndices.size();i++){
            fitme.add(tempDataset.getDataItem(chiIndices.get(i)));
            XYDataItem item = tempErrors.getDataItem(chiIndices.get(i));
            double value = item.getYValue();
            errorsInUse.add(item.getX(),value*value);
        }


        /*
         * createDesignMatrix with fitme will make:
         * a_matrix and y_vector
         * overwwrites any exisiting a_matrix or y_vector
         */
        this.createDesignMatrix(fitme); // this creates a_matrix and y_vector

        SimpleMatrix tempResiduals = a_matrix.mult(am_vector).minus(y_vector); // this would resize rows and residuals via a_matrix and y_vector

        double sum=0;
        for (int i = 0; i < fitme.getItemCount(); i++){
            resi = tempResiduals.get(i,0);
            sum += resi*resi/errorsInUse.getY(i).doubleValue();
        } //
        //System.out.println(fitme.getItemCount() + " sum " + sum/(double)fitme.getItemCount() );
        // need to resetDesignMatrix so that a_matrix and y_vector revert back to the original dataset
        return sum/((double)fitme.getItemCount() + 2);
    }

    @Override
    public String getModelUsed(){ return this.modelUsed; }

    @Override
    public void setNonStandardizedData(XYSeries nonStandardizedData){
        this.nonData = nonStandardizedData;
    }

    @Override
    public void createNonStandardizedData(){
        nonData = new XYSeries("nonstandard");
        for (int i=0; i<this.data.getItemCount(); i++){
            XYDataItem item = this.data.getDataItem(i);
            nonData.add(item.getX(), item.getYValue()*standardizedScale+standardizedMin);
            errors.updateByIndex(i, errors.getY(i).doubleValue()*standardizedScale/item.getXValue());
        }
    }

    @Override
    public String getHeader(double scale){

        String output = String.format("REMARK 265 %n");
        output += String.format("REMARK 265 EXPERIMENTAL REAL SPACE FILE %n");
        output += String.format("REMARK 265    P(r)-DISTRIBUTION BASED ON : %s %n", modelUsed);
        output += description;

        output += String.format("REMARK 265 %n");
        output += String.format("REMARK 265  BIN COEFFICIENTS (UNSCALED)%n");
        output += String.format("REMARK 265 %n");

        output += getPrDistributionForFitting();

        output += String.format("REMARK 265 %n");
        output += String.format("REMARK 265  SCALED P(r) DISTRIBUTION %n");
        output += String.format("REMARK 265      SCALE : %.3E %n", scale);
        output += String.format("REMARK 265    COLUMNS : r, P(r), error%n");
        output += String.format("REMARK 265          r : defined in Angstroms%n");

        double incr = Math.PI/qmax/3.0;
        incr = (incr < 3.1 ) ? 3.1 : incr;

        output += String.format( Constants.Scientific1dot4e2.format(0) + "\t" + Constants.Scientific1dot2e1.format(0) + "\t 0.00 "+ "\n");
        for (int r = 1; r*incr < dmax; r++){
            double r_incr = r*incr;
            output += String.format( Constants.Scientific1dot4e2.format(r_incr) + "\t" + Constants.Scientific1dot2e1.format(this.calculatePofRAtR(r_incr, scale)) + "\t 0.00 "+ "\n");
        }
        output += String.format( Constants.Scientific1dot4e2.format(dmax) + "\t" + Constants.Scientific1dot2e1.format(0) + "\t 0.00 "+ "\n");

        return output;
    }

    public void setModelUsed(String text){
        this.modelUsed = text;
    }

    /**
     * Required for structural modeling using Iketama
     * @return
     */
    private String getPrDistributionForFitting(){

        String temp = String.format("REMARK 265 P(R) DISTRIBUTION BINNED USING SHANNON NUMBER %n");
              temp += String.format("REMARK 265 R-values REPRESENT THE MID-POINT OF EACH BIN %n");
              temp += String.format("REMARK 265 BIN HEIGHT REPRESENTS THE VALUE OF P(R-value) %n");
              temp += String.format("REMARK 265        BIN            R-value : BIN HEIGHT %n");
              temp += String.format("REMARK 265 %n");

        int total = prDistributionForFitting.getItemCount();

        for(int i=0; i<total; i++){
            XYDataItem item = prDistributionForFitting.getDataItem(i);
            int index = i+1;
            temp +=  String.format("REMARK 265       BIN_%-2d          %9.3f : %.5E %n", index, item.getXValue(), item.getYValue());
        }

        temp += String.format("REMARK 265 %n");
        temp += String.format("REMARK 265            TOTAL SHANNON BINS : %d %n", total);
        return temp;
    }

    public String getIFTParametersCIFFormat(int id){
        int total = prDistributionForFitting.getItemCount();
        String tempHeader =  String.format("# REMARK 265 P(r) histogram details for modeling %n");
        tempHeader += String.format("_sas_p_of_R_details.Shannon_number %d\n", ns);
        tempHeader += String.format("_sas_p_of_R_details.bin_width %.3f\n", bin_width);
        tempHeader += String.format("_sas_p_of_R_details.r_average %.3f\n", rAverage);
        tempHeader += String.format("loop_%n");
        tempHeader += String.format("_sas_p_of_R.id %n");
        tempHeader += String.format("_sas_p_of_R.ordinal %n");
        tempHeader += String.format("_sas_p_of_R.bin %n");
        tempHeader += String.format("_sas_p_of_R.bin_height %n");
        for(int i=0; i<total; i++){
            XYDataItem item = prDistributionForFitting.getDataItem(i);
            int index = i+1;
            tempHeader +=  String.format("%-2d %-2d %.3f %.4E %n", id, index, item.getXValue(), item.getYValue());
        }
        tempHeader += "# \n";
        return tempHeader;
    }

    public String getPrDistributionForFittingCIFFormat(int id){
        int total = prDistributionForFitting.getItemCount();
        String tempHeader =  String.format("# REMARK 265 P(r) histogram details for modeling %n");
        tempHeader += String.format("_sas_p_of_R_details.Shannon_number %d\n", ns);
        tempHeader += String.format("_sas_p_of_R_details.bin_width %.3f\n", bin_width);
        tempHeader += String.format("_sas_p_of_R_details.r_average %.3f\n", rAverage);
        tempHeader += String.format("loop_%n");
        tempHeader += String.format("_sas_p_of_R.id %n");
        tempHeader += String.format("_sas_p_of_R.ordinal %n");
        tempHeader += String.format("_sas_p_of_R.bin %n");
        tempHeader += String.format("_sas_p_of_R.bin_height %n");
        for(int i=0; i<total; i++){
            XYDataItem item = prDistributionForFitting.getDataItem(i);
            int index = i+1;
            tempHeader +=  String.format("%-2d %-2d %.3f %.4E %n", id, index, item.getXValue(), item.getYValue());
        }
        tempHeader += "# \n";
        return tempHeader;
    }


    public String getPrDistributionForPlottingCIFFormat(int id){
        String tempHeader = String.format("# REMARK 265 P(r)-distribution for plotting %n");
        tempHeader += String.format("loop_%n");
        tempHeader += String.format("_sas_p_of_R.id%n");
        tempHeader += String.format("_sas_p_of_R.ordinal%n");
        tempHeader += String.format("_sas_p_of_R.R%n");
        tempHeader += String.format("_sas_p_of_R.P%n");
        tempHeader += String.format("_sas_p_of_R.P_error%n");

        for (int n=0; n < prDistribution.getItemCount(); n++) {
            XYDataItem item = prDistribution.getDataItem(n);
            int index = n+1;
            tempHeader += String.format("%-2d %-3d %.3E %.4E ? %n", id, index, item.getXValue(), item.getYValue());
        }
        tempHeader += "# \n";
        return tempHeader;
    }


    /**
     * Pr-distribution series must be calculated first, will through an error
     *
     */
    public void calculateSphericalCalibration(){
        sphericalDistribution = new XYSeries("Spherical Distribution");
        sphericalDistributionFine = new XYSeries("FINE");

        double radius = dmax*0.5d;
        double radiusCubed = radius*radius*radius;
        double invR3 = 1.0/radiusCubed;
        double constantA = 3.0*invR3;
        double constantB = 9.0/4*invR3/radius;
        double constantC = 3.0/16.0*invR3*invR3;

        sphericalVolume = 4.0/3.0*Math.PI*radiusCubed;
        sphericalDistribution.add(0,0);

        double delta = prDistribution.getX(2).doubleValue() - prDistribution.getX(1).doubleValue();
        double increment = delta/2.0d;
        double rat = 0.25*delta;
        while(rat < dmax){
            double rsquared = rat*rat;
            double rcubed = rsquared*rat;
            sphericalDistributionFine.add(rat, sphericalVolume*(constantA*rsquared - constantB*rcubed + constantC*rsquared*rcubed));
            rat += increment;
        }

        int last = totalInDistribution-1;
        double sum = 0;
        for(int i=1; i<last; i++){
            XYDataItem value = prDistribution.getDataItem(i);
            double rvalue = value.getXValue(); // trapezoid rule for area calculation
            double rsquared = rvalue*rvalue;
            double rcubed = rsquared*rvalue;

            sphericalDistribution.add(value.getX(), sphericalVolume*(constantA*rsquared - constantB*rcubed + constantC*rsquared*rcubed));

            sum +=(constantA*rsquared - constantB*rcubed + constantC*rsquared*rcubed)*delta;
        }

        sphericalDistribution.add(dmax, 0);

        sphericalRg = Math.sqrt((dmax*dmax*0.25)*3.0/5.0d);
//        System.out.println("Spherical VOlume " + sphericalVolume + " :: " + dmax + " Rg :: " + sphericalRg);
//        System.out.println("distribution sizes " + sphericalDistribution.getItemCount() + " == " + prDistribution.getItemCount());
    }




    /**
     *
     * Using spherical distribution, scale P(r)-distribution to the smallest value that provides only positive differences
     * which scale factor produces the largest non-negative difference in areas
     *
     */
    public void calibratePrDistribution(){

        double scalefactor=1, keptScalefactor = 1;
        int last = totalInDistribution-1;
        double keptsum = Double.POSITIVE_INFINITY;
        double delta = sphericalDistribution.getX(2).doubleValue() - sphericalDistribution.getX(1).doubleValue();

        int lastIn = sphericalDistributionFine.getItemCount()-1;

        for(int i=0; i<0*lastIn; i++){
            XYDataItem item = sphericalDistributionFine.getDataItem(i);
            scalefactor = item.getYValue()/calculatePofRAtR(item.getXValue(), 1);
            boolean pleaseCOntinue = true;
            double diffsum = 0;

            for(int j=0; j<last; j++){ // go through each trapezoid and calculate area
                XYDataItem inItem = sphericalDistributionFine.getDataItem(j);
                double diff = inItem.getYValue() - scalefactor*calculatePofRAtR(inItem.getXValue(), 1);

                if (diff < 0){
                    pleaseCOntinue = false;
                    break;
                }
                diffsum+=diff;
            }

            if(pleaseCOntinue && diffsum < keptsum){
                keptsum = diffsum;
                keptScalefactor = scalefactor;
                //System.out.println("kept factor " + i + " " + scalefactor + " => " + keptsum);
            }
        }


        for(int i=1; i<last; i++){

            scalefactor = sphericalDistribution.getY(i).doubleValue()/prDistribution.getY(i).doubleValue();
            boolean pleaseCOntinue = true;
            double diffsum = 0;

            for(int j=1; j<last; j++){ // go through each trapezoid and calculate area

                double diff = sphericalDistribution.getY(j).doubleValue() - scalefactor*prDistribution.getY(j).doubleValue();

                if (diff < 0){
                    pleaseCOntinue = false;
                    break;
                }
                diffsum+=diff;
            }

            if(pleaseCOntinue && diffsum < keptsum){
                keptsum = diffsum;
                keptScalefactor = scalefactor;
                //System.out.println("kept factor " + i + " " + scalefactor + " => " + keptsum);
            }
        }

        // need two corrections, one is rescaling based on size and the other is shape
        double correctedVolume = sphericalVolume - keptsum*delta;
//        System.out.println(sphericalVolume +  " => Corrected Volume : " + correctedVolume*this.rg/sphericalRg);

//        for(int i=0; i<totalInDistribution; i++){
//            System.out.println(sphericalDistribution.getX(i) + " " + sphericalDistribution.getY(i).doubleValue() + " " + keptScalefactor*prDistribution.getY(i).doubleValue());
//        }
    }

    /**
     * provide a quality score of the Pr-distribution using second derivative and tangent at dmax
     * @param del_r
     * @return
     */
    public double scoreDistribution(double del_r){
        totalInDistribution = prDistribution.getItemCount();
        double delta = prDistribution.getX(2).doubleValue() - prDistribution.getX(1).doubleValue();
        /*
         * assess smoothness
         */
        double diff, diff_sum=0, diff_abs_sum = 0;
        int negativity = 0;
        double max = 0;
        int indexOfMax = 0;

        double value = prDistribution.getY(1).doubleValue();
        double normalizationSum = Math.abs(value);
        int mid = totalInDistribution/2-1;
        for(int r=2; r<(totalInDistribution-2); r++){
            value = prDistribution.getY(r).doubleValue();
            normalizationSum += Math.abs(value);
            if (value > max){
                max = value;
                indexOfMax=r;
            }
            if (value < 0 && r > mid){
                //diff_sum = 1000;
                negativity += 1;
                //break;
            }
            diff = (prDistribution.getY(r+1).doubleValue() - 2.0*value + prDistribution.getY(r-1).doubleValue());
            diff_abs_sum += Math.abs(diff);
            diff_sum += diff*diff;
        }

        value = prDistribution.getY(totalInDistribution-1).doubleValue();
        normalizationSum += Math.abs(value);

        double delta2 = delta*delta;
        diff_sum *= 1.0/(delta2*delta2); // squared value from above
        diff_abs_sum *= 1.0/delta2;
        /*
         * spacing between r_0 and r_1 and r_(n-2) and dmax are not event
         */
        double p1 = 2*(-(1.5*delta)*prDistribution.getY(1).doubleValue() + 0.5*delta*prDistribution.getY(2).doubleValue())/(0.5*delta*delta*(0.5*delta+delta));
        double p2 = 2*(-(1.5*delta)*prDistribution.getY(totalInDistribution-2).doubleValue() + 0.5*delta*prDistribution.getY(totalInDistribution-3).doubleValue())/(0.5*delta*delta*(0.5*delta+delta));
        double p3 = (prDistribution.getY(totalInDistribution-2).doubleValue() - 2.0*prDistribution.getY(totalInDistribution-3).doubleValue() + prDistribution.getY(totalInDistribution-4).doubleValue());

        negativity += (prDistribution.getY(totalInDistribution-2).doubleValue() < 0) ? 1 : 0;

        diff_sum += p1*p1;
        diff_sum += p2*p2;
        diff_abs_sum += (Math.abs(p1) + Math.abs(p2));

        normalizationSum = 1.0/(normalizationSum*delta);

        p3 *= normalizationSum/(delta*delta);
        p2 *= normalizationSum;
        p1 *= normalizationSum;

        diff_abs_sum *= normalizationSum;

        //diff_sum *= 1.0/(double)(totalInDistribution-2)*normalizationSum*normalizationSum;
        diff_sum = 1.0/(double)(totalInDistribution-2)*diff_abs_sum;
        //System.out.println("DIFF SUM " + diff_sum);

        /*
         * assess the finish near dmax
         */
        double slopeScore = 1; // default value
        double slope5diff = 0;
        int totalAfterPeak = totalInDistribution - indexOfMax - 2;
        XYDataItem item = prDistribution.getDataItem(totalInDistribution-3);
        double x3 = dmax - item.getXValue();
        double y3 = Math.abs(item.getYValue()*normalizationSum);
        double a31 = Math.atan(y3/x3);
        // point 2
        item = prDistribution.getDataItem(totalInDistribution-2);
        double x2 = dmax - item.getXValue();
        double y2 = Math.abs(item.getYValue()*normalizationSum);
        // point -3 to -2
        double diffy3y2 = Math.abs(y3-y2);
        double a32 = Math.atan(diffy3y2/(x3-x2));

        double a21 = Math.atan(y2/x2);

        slopeScore = (Math.toDegrees(a31) + Math.toDegrees(a32) + Math.toDegrees(a21));

        XYDataItem item4 = prDistribution.getDataItem(totalInDistribution-4);
        double x4 = dmax - item4.getXValue(); // translate coordinates to origin
        double y4 = Math.abs(item4.getYValue()*normalizationSum);

        distribution_score = 0;

        if (totalAfterPeak > 4 ){
            // point -4 to -2
            double diffy4y2 = Math.abs(y2-y4);
            double a42 = Math.atan(diffy4y2/(x4-x2));
            // point -4 to -3
            double diffy4y3 = Math.abs(y4-y3);
            double a34 = Math.atan(diffy4y3/(x4-x3));

            //System.out.println("Angles " + Math.toDegrees(a31) + " " + Math.toDegrees(a32) + " " + Math.toDegrees(a21) + " " + Math.toDegrees(Math.atan(y4/x4)));
            slopeScore += Math.toDegrees(Math.atan(y4/x4));//Math.toDegrees(a42) + Math.toDegrees(a34));

            double areaT = (x4-x3)*(y4+y3);
            areaT += (x3-x2)*(y3+y2);
            areaT += x2*y2;

                item = prDistribution.getDataItem(totalInDistribution-5);
                double x5 = dmax - item.getXValue(); // translate coordinates to origin
                double y5 = Math.abs(item.getYValue())*normalizationSum;

                double slope5 = -y5/x5;
                double intercept = -dmax*slope5;
                double tempy4 = item4.getXValue()*slope5 + intercept;


                double aread5 = x5*y5;
                areaT += (x5-x4)*(y5+y4);
                double diff1 = Math.abs(areaT-aread5)/aread5;
                //System.out.println("5 diff1 " + diff1 + " " + ((x5-x4)*(y5+y4)) + " " + ((x4-x3)*(y4+y3)) + " " +((x3-x2)*(y3+y2)) + " " + x2*y2);
                slope5diff = 1.0/diff1;

            if (tempy4 < y4){
                slope5diff += 10*(1-tempy4/y4);
            }

        } else {

            if (totalAfterPeak == 4){
                double aread4 = x3*y3;
                double areaT = (x3-x2)*(y2+y3);
                areaT += x2*y2;
                double diff1 = Math.abs(areaT-aread4)/aread4;

                slope5diff = 1.0/diff1;

            } else {
                double aread4 = x3*y3;
                double areaT = (x3-x2)*(y2+y3);
                areaT += x2*y2;
                double diff1 = Math.abs(areaT-aread4)/aread4;

                slope5diff = 1.0/diff1;
            }
        }


        /*
         * check for oscillations at the Ns nodes
         * prDistributionForFitting does not include r=0 or r=dmax
         */
        indexOfMax = 0;
        int totalInNsDistribution = prDistributionForFitting.getItemCount();
        max = prDistributionForFitting.getY(0).doubleValue();
        for(int r=1; r < totalInNsDistribution; r++){
            value = prDistributionForFitting.getY(r).doubleValue();
            if (value > max){
                max = value;
                indexOfMax=r;
            }
        }

        /*
         * penalize bulge
         */
        double pointns2 = prDistributionForFitting.getY(totalInNsDistribution-1).doubleValue();
        double pointns3 = prDistributionForFitting.getY(totalInNsDistribution-2).doubleValue();
        double pointns4 = prDistributionForFitting.getY(totalInNsDistribution-3).doubleValue();
        if (pointns2 > pointns3){
            double diffit = pointns2 - pointns3;
            distribution_score += Math.abs(diffit/pointns3);
        }

        if (pointns2 > pointns4){
            double diffit = pointns2 - pointns4;
            distribution_score += Math.abs(diffit/pointns4);
        }

        if (pointns3 > pointns4){
            double diffit = pointns3 - pointns4;
            distribution_score += Math.abs(diffit/(pointns4-pointns2));
        }

        /*
         * in the paper we index with P(last) => dmax
         * in prDistributinoForFitting, dmax is not included
         * Oscillations only looks at the even points, see paper
         */
        int even = 2;
        final int totalAfter = (totalInNsDistribution - indexOfMax - 1);
        double factor = 1.0d;
        boolean continueOn = true;
        int oscCount = 0;
        // super penalty if oscillation is reversed
        double ns2 = prDistributionForFitting.getY((totalInNsDistribution-even)+1).doubleValue();
        if (ns2 < 0){
            even = 1;
            ns2 = prDistributionForFitting.getY((totalInNsDistribution-even)).doubleValue();
            double ns3 = prDistributionForFitting.getY((totalInNsDistribution-even)-1).doubleValue();

            if (ns2 < ns3){ // check for a valley at ns2
                double diffIt = ns3-ns2;
                distribution_score += 100*Math.abs(diffIt/ns3)*factor;
                factor *= 3;
                oscCount += 1;
            } else {
                continueOn = false;
            }
            even += 2;

            while (even < totalAfter && continueOn){ // -1, -3 are valleys

                ns2 = prDistributionForFitting.getY((totalInNsDistribution-even)+1).doubleValue();
                ns3 = prDistributionForFitting.getY(totalInNsDistribution-even).doubleValue();
                double ns4 = prDistributionForFitting.getY((totalInNsDistribution-even)-1).doubleValue();

                if (ns2 > ns3 && ns4 > ns3){
                    double diffIt = Math.min((ns2-ns3), (ns4-ns3));
                    distribution_score += 100*Math.abs(diffIt/ns3)*factor;
                    factor *= 3;
                    oscCount += 1;
                } else {
                    continueOn = false;
                }
                even += 2;
            }
        } else {
            while (even < totalAfter && continueOn){
                ns2 = prDistributionForFitting.getY((totalInNsDistribution-even)+1).doubleValue();
                double ns3 = prDistributionForFitting.getY(totalInNsDistribution-even).doubleValue();
                double ns4 = prDistributionForFitting.getY((totalInNsDistribution-even)-1).doubleValue();

                if (ns2 > ns3 && ns3 < ns4){
                    double diffIt = Math.max((ns2-ns3), (ns4-ns3));
                    distribution_score += Math.abs(diffIt/ns3)*factor;
                    factor *= 3;
                    oscCount += 1;
                } else {
                    continueOn = false;
                }
                even += 2;
            }
        }

        /*
         * for samples with few shannon points, like 4 it may not be possible to evaluate the above accurately)
         */
        //distribution_score = (distribution_score == 0 && (pointn2 < 0 || pointn3 < 0 )) ? 1 : distribution_score;
        diff_sum = 1.0/Math.abs(Math.log10(diff_sum));

        // negativity penality
        //System.out.println("Negativity " + negativity + " " + Math.pow(7,negativity)*1.933 + " OSC " + oscCount + " " + distribution_score);
        if (negativity > 0 && prDistributionForFitting.getY(totalInNsDistribution-1).doubleValue() < 0){
            distribution_score += Math.pow(7,negativity)*19.33;
        } else if (negativity > 0) {
            distribution_score += Math.pow(7,negativity)*1.933;
        }

        // if background is not necessary, first value gets depressed
        if (prDistribution.getX(1).doubleValue() < 0){
            distribution_score += Math.pow(7,negativity)*19.33;
        }


        if (p2 < 0){ // convex/concave
            diff_sum += 13.1*Math.abs(p2/p3);
        } else { // concave with small value
            diff_sum -= diff_sum*(p2/diff_abs_sum);
        }

        //System.out.println("slope5diff " +slope5diff);
//        if (prDistribution.getDataItem(totalInDistribution-2).getYValue() < 0){
//            slope5diff = 1.0/0.1;
//        }

        //slope5diff=0;
        //slopeScore = (slopeScore==1) ? 0 : slopeScore;// inverse because the angle is obtuse
        //prScore = (distribution_score + diff_sum) + 500*slopeScore + 50*slope5diff;
        prScore = distribution_score + (10*diff_sum) + 2*slope5diff;
        //System.out.println(dmax + " " + prScore + " " + distribution_score + " " + diff_sum + " " + slopeScore + " 5diff " + slope5diff + "  " + diff_abs_sum);
        return prScore;
    }

    public double getPrScore() { return prScore;}
    /*
     * use in refining since dmax is fixed
     *
     */
    public void setPrior(double[] coefficients){
        prior_coefficients = coefficients.clone();
        priorExists = true;
    }


    public double getShannonNumber(){
        return ns;
    }


    @Override
    public double extrapolateToIofQ(double q) {

        //double intensity = Math.log(this.izero) - (this.rg*this.rg)/3.0*q*q;
        double slope = rg*rg/3.0;
        int total = nonData.getItemCount();

        XYDataItem startItem = nonData.getDataItem(0);
        double limit = 1.05/this.rg;
        int nnextItem = 0;
        while(startItem.getXValue() < limit){
            nnextItem+=1;
            startItem = nonData.getDataItem(nnextItem);
        }
        // use all points up to limit for Guinier extrapolation

        if (nnextItem > 1){
            double xvals;
            double yvals = 0;


            for(int i=0; i<nnextItem; i++){
                startItem = nonData.getDataItem(i);

                xvals = startItem.getXValue()*startItem.getXValue();

                yvals += Math.log(startItem.getYValue()/startItem.getXValue()) + slope*xvals;
                //System.out.println(i + " "+startItem.getX() + " " + (Math.log(startItem.getYValue()/startItem.getXValue()) + slope*xvals) + " :: " + this.rg);
            }

            double tizero = Math.exp(yvals/(double)nnextItem);
            double weight = Math.exp(-(izero-tizero)/izero);

            double lnintensity = (izero+weight*tizero)/(1.0d+weight)*Math.exp(-slope*q*q);
            return lnintensity;

        } else {

            return izero*Math.exp(-slope*q*q);
            //return (Math.exp(lnintensity)+ coefficients[0])*standardizedScale + standardizedMin;
        }

    }
}
