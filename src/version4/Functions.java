package version4;

import org.apache.commons.math3.util.FastMath;

import org.ejml.data.FMatrixRMaj;
import org.ejml.dense.row.CommonOps_FDRM;
import org.ejml.dense.row.factory.DecompositionFactory_FDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F32;
import org.ejml.simple.SimpleMatrix;
import org.jfree.data.statistics.Statistics;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Created by robertrambo on 05/01/2016.
 */
public class Functions {


    private static List<Integer> sequence = new ArrayList<>();
    private static Random generator = new Random();
    private static double[] n_pi_squared = {
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

    static final public double INV_PI = 1.0/Math.PI;

    /**
     *
     * @param lower int (inclusive of)
     * @param upper int (exclusive of)
     * @param percent double
     * @return array of random integers within bound
     */
    public static int[] randomIntegersBounded(int lower, int upper, double percent){


        List<Integer> numbers = new ArrayList<>();

        int total = 0;
        for(int i = lower; i < upper; i++) {
            numbers.add(i);
            total++;
        }

        // Shuffle them
        Collections.shuffle(numbers);
        int count = (int)Math.ceil(total*percent);
        // Pick count items.
        List<Integer> tempNumbers = numbers.subList(0, count);
        int[] values = new int[count];

        for(int i=0; i < count; i++){
            values[i] = tempNumbers.get(i);
        }

        Arrays.sort(values);
        return values;
    }




    /**
     * Calculate Chi-free for Moore fit
     * @param data (XYSeries)
     * @param dmax as integer
     * @return chi_free as float
     */
    public static float chi_free(XYSeries data, XYSeries fit, XYSeries error, int dmax, int rounds) throws Exception{
        float chifree;

        if (data.getItemCount() != fit.getItemCount() && data.getItemCount() != error.getItemCount() ){
            //throw exception
            System.out.println(data.getItemCount() + " " + fit.getItemCount());
            throw new Exception("data and fit XYSeries are not the same size");
        }

        double qmax;
        //qmin = data.getX(0).doubleValue();
        qmax = data.getMaxX();
        int bins = (int)(Math.round(qmax*dmax/Math.PI));


        double delR = qmax/bins;

        //Create ArrayList (2-d) that holds the indices of the data points
        ArrayList<ArrayList<Integer>> binnedData;
        binnedData = new ArrayList<ArrayList<Integer>>();

        int totalValues = data.getItemCount();
        XYDataItem dataXY;

        int whichBin, current = -1, bincount = -1;

        for(int i = 0; i < totalValues; i++){
            dataXY = data.getDataItem(i);
            whichBin = (int) (dataXY.getXValue()/delR);

            if (current == whichBin){
                binnedData.get(bincount).add(i);
            } else {
                binnedData.add(new ArrayList<Integer>( Arrays.asList(i)) );
                current = whichBin;
                bincount++;
            }
        }

        int[] sizes = new int[bins];

        for(int i = 0; i < bins; i++){
            sizes[i] = binnedData.get(i).size();
        }

        float[] chifreeArray;
        float sigma, difference;
        int randomSpot, index;
        chifreeArray = new float[rounds];

        Random wheel = new Random();

        for (int i =0; i < rounds; i++){
            chifree = 0;

            for (int j =0; j < bins; j++){
                //randomly pick 1 element from each bin
                randomSpot = wheel.nextInt(sizes[j]);
                index = binnedData.get(j).get(randomSpot);
                sigma = error.getY(index).floatValue();
                difference = data.getY(index).floatValue() - fit.getY(index).floatValue();
                chifree += difference*difference/(sigma*sigma);
            }
            chifreeArray[i] = (float) chifree;
        }
      /*
      float chisquare = 0;
      for (int i=0; i < data.getItemCount(); i++){
          sigma = error.getY(i).floatValue();
          difference = data.getY(i).floatValue() - fit.getY(i).floatValue();
          chisquare = chisquare + difference*difference/(sigma*sigma);
      }

      chisquare = (float) (1.0/(data.getItemCount() - bins))*chisquare;
      */
        Arrays.sort(chifreeArray);

        chifree = chifreeArray[(int)(rounds*0.5)]*(1/(float)(bins-2));

        return chifree;
    }

    public static XYSeries createPofR(int dmax, ArrayList<double[]> results, int locale, double scale){
        XYSeries returnSeries = new XYSeries("Pr distribution-"+locale);
        /*
         * create P(r) plot
         */
        int r_limit = results.get(1).length;
        int n = results.get(0).length;

        double[] am = results.get(0);
        double[] r_vector = results.get(1);
        double resultM;
        double inv_d = 1.0/dmax;
        double pi_dmax = Math.PI*inv_d;
        double inv_2d = 0.5*inv_d;
        double pi_dmax_r;

        returnSeries.add(0.0d, 0.0d);

        for (int j=0; j < r_limit; j++){
            pi_dmax_r = pi_dmax*r_vector[j];
            resultM = 0;

            for(int i=1; i < n; i++){
                resultM += am[i]*Math.sin(pi_dmax_r*i);
            }
            returnSeries.add(r_vector[j], inv_2d * r_vector[j] * resultM*scale);
        }

        returnSeries.add(dmax,0);
        return returnSeries;
    }



    /**
     * Calculates ChiFree and ChiSquare from Fit Files (CRYSOL or FOXS).
     * @param data XYSeries (q and I_exp(q))
     * @param fit XYSeries (q and I_calc(q))
     * @param error XYSeries (q and sigma(q))
     * @return Array of floats chiFree[0], chiSquare[1]
     */
    public static float[] chiValues(XYSeries data, XYSeries fit, XYSeries error, int dmax, int rounds) throws Exception {
        float[] chiValues={0.0f, 0.0f};
//       double qmax;
//       qmax = error.getMaxX();
//       int bins = (int)(Math.round(qmax*dmax/Math.PI));

        float chifree, sigma, difference;
        chifree = chi_free(data, fit, error, dmax, rounds);
        chiValues[0] = chifree;

        float chisquare = 0;
        for (int i=0; i < data.getItemCount(); i++){
            sigma = error.getY(i).floatValue();
            difference = data.getY(i).floatValue() - fit.getY(i).floatValue();
            chisquare = chisquare + difference*difference/(sigma*sigma);
        }

        chisquare = ((float)(1.0/(data.getItemCount() - 3)))*chisquare;
        chiValues[1] = chisquare;
        return chiValues;
    }


    /**
     * Calculates median of an array. If even number of elements it returns average of the two numbers
     * @param array Array of Doubles to calculate median
     * @return Median value calculated from array elements
     */
    public static double median(double[] array) {
        int index;
        double medianResult;
        medianResult = 0.0;

        int length = array.length;
        //if even number of elements
        if (length%2==0) {
            index = length/2;
            medianResult=(array[index]+array[index-1])*0.5;
        } else {
            index = length/2;
            medianResult=array[index];
        }
        return medianResult;
    }

    /**
     * Calculates the residual using observations (XYSeries) and calculated (XYseries)
     * and returns ArrayList containing the residuals.
     * Each dataset must be already scaled
     * @param data Observed data
     * @param calc Calculated data
     * @return Residuals using observed and calculated XYSeries as ArrayList
     */
    public static ArrayList<Double> residuals(XYSeries data, XYSeries calc){
        ArrayList<Double> result = new ArrayList<Double>();
        int limit = data.getItemCount();
        for (int i=0; i<limit; i++){
            result.add(data.getY(i).doubleValue() - calc.getY(i).doubleValue());
        }
        return result;
    }

    /**
     * Calculates the absolute value of the residual using
     * observations (XYSeries) and calculated (XYseries)
     * and returns ArrayList containing the residuals.
     * Each dataset must be already scaled
     * @param data Observed data
     * @param calc Calculated data
     * @return Absolute value of residuals using observed and calculated XYSeries as ArrayList
     */
    public static ArrayList<Double> residualsAbs(XYSeries data, XYSeries calc){
        ArrayList<Double> result = new ArrayList<Double>();
        int limit = data.getItemCount();
        for (int i=0; i<limit; i++){
            result.add(Math.abs(data.getY(i).doubleValue() - calc.getY(i).doubleValue()));
        }
        return result;
    }

    public static ArrayList<Double> saxs_invariants_L(ArrayList<Double> coefficients, int dmax){
        ArrayList<Double> results = new ArrayList<Double>();
        /*
         * Calculate invariants
         */
        double i_zero = 0;
        double partial_rg = 0;
        double rsum = 0;
        double rg, average_r;
        double am;
        double pi_sq = n_pi_square(1);
        int sizeOf = coefficients.size();

        for (int i = 0; i < sizeOf; i++) {

            if (i != 0) {
                am = coefficients.get(i);
                i_zero = i_zero + am/(i)*Math.pow(-1,(i+1));
                partial_rg = partial_rg + am/Math.pow(i,3)*(Math.pow(Math.PI*i, 2) - 6)*Math.pow(-1, (i+1));
                rsum = rsum + am/Math.pow(i,3)*((Math.pow(Math.PI*i, 2) - 2)*Math.pow(-1, (i+1)) - 2 );
            }
        }


        double dmax2 = dmax*dmax;
        double dmax3 = dmax2*dmax;
        double dmax4 = dmax2*dmax2;
        double inv_pi_cube = 1/(pi_sq*Math.PI);

        i_zero = i_zero*dmax2/Math.PI + coefficients.get(0);

        rg = Math.sqrt(dmax4*inv_pi_cube/i_zero*partial_rg)*0.7071067811865475; // 1/Math.sqrt(2);
        average_r = dmax3*inv_pi_cube/i_zero*rsum;

        results.add(i_zero);
        results.add(rg);
        results.add(average_r);

        return results;
    }

    public static ArrayList<Double> saxs_invariants(double[] coefficients, int dmax){
        ArrayList<Double> results = new ArrayList<Double>();
        /*
         * Calculate invariants
         */
        double i_zero = 0;
        double partial_rg = 0;
        double rsum = 0;
        double rg, average_r;
        double am;
        double pi_sq = n_pi_square(1);
        int sizeOf = coefficients.length;

        for (int i = 0; i < sizeOf; i++) {

            if (i != 0) {
                am = coefficients[i];
                i_zero = i_zero + am/(i)*Math.pow(-1,(i+1));
                partial_rg = partial_rg + am/Math.pow(i,3)*(Math.pow(Math.PI*i, 2) - 6)*Math.pow(-1, (i+1));
                rsum = rsum + am/Math.pow(i,3)*((Math.pow(Math.PI*i, 2) - 2)*Math.pow(-1, (i+1)) - 2 );
            }
        }


        double dmax2 = dmax*dmax;
        double dmax3 = dmax2*dmax;
        double dmax4 = dmax2*dmax2;
        double inv_pi_cube = 1/(pi_sq*Math.PI);

        i_zero = i_zero*dmax2/Math.PI + coefficients[0];

        rg = Math.sqrt(dmax4*inv_pi_cube/i_zero*partial_rg)*0.7071067811865475; // 1/Math.sqrt(2);
        average_r = dmax3*inv_pi_cube/i_zero*rsum;

        results.add(i_zero);
        results.add(rg);
        results.add(average_r);

        return results;
    }




    /**
     * Calculates Porod Invariant as a function of q
     * Needs to be modified to include experimental error propagation
     * @param data unscaled, original data truncated in the low q
     * @param iZero Izero
     * @param rg Rg
     * @param rgError Rg's standard deviation
     * @return XYSeris in form (q, Q(q))
     */
    public static XYSeries porodInvariant(XYSeries data, double iZero, double iZeroError, double rg, double rgError){

        double tsum = 0.0;

        int limit = data.getItemCount() - 1;
        ArrayList<Double> diff = new ArrayList<Double>();
        XYSeries extrapolated = new XYSeries("");
        XYSeries tempXYSeries = new XYSeries("");

        for (int i=0; i < limit; i++) {
            diff.add(data.getX(i+1).doubleValue() - data.getX(i).doubleValue());
        }
        double avgDiff = Statistics.calculateMedian(diff);
        //extrapolate
        double q = avgDiff;
        while (q < data.getMinX()){
            extrapolated.add(q, q*q*Math.exp(Math.log(iZero) - Math.pow(q,2)*(Math.pow(rg,2)/3)));
            q = q + avgDiff;
        }
        // Combine with data to get a complete, extrapolated dataset as q vs q^2*I(q)

        for (int i = 0; i < data.getItemCount(); i++){
            extrapolated.add(data.getX(i), Math.pow(data.getX(i).doubleValue(),2)*data.getY(i).doubleValue());
        }
        limit = extrapolated.getItemCount();
        /*
         * Perform trapezoidal rule
         */
        double tempsum = 0;
        for (int i = 0; i < limit; i++){
            if (i == limit -1){ //last point for trapezoid rule
                tempsum = tsum + extrapolated.getY(i).doubleValue();
                tempXYSeries.add(extrapolated.getX(i), extrapolated.getX(i).doubleValue()/(2.0*(i+1))*tempsum);
            } else {
                tempsum = tsum + extrapolated.getY(i).doubleValue();
                tempXYSeries.add(extrapolated.getX(i), extrapolated.getX(i).doubleValue()/(2.0*(i+1))*tempsum);
                tsum = tsum + 2.0*extrapolated.getY(i).doubleValue();
            }
        }

        return tempXYSeries;
    }

    /**
     *
     * @param data
     * @return
     */
    public static XYSeries qIqIntegral(XYSeries data){

        double tsum = 0.0;

        XYDataItem tempXY;
        int limit = data.getItemCount() - 1;
        XYSeries tempXYSeries = new XYSeries("");

        limit = data.getItemCount();
        /*
         * Perform trapezoidal rule
         */
        double tempsum;
        for (int i = 0; i < limit; i++){
            tempXY = data.getDataItem(i);
            if (i == limit -1){ //last point for trapezoid rule
                tempsum = tsum + tempXY.getYValue();
                tempXYSeries.add(tempXY.getX(), tempXY.getXValue()/(2.0*(i+1))*tempsum);

            } else {
                tempsum = tsum + tempXY.getYValue();
                tempXYSeries.add(tempXY.getX(), tempXY.getXValue()/(2.0*(i+1))*tempsum);
                tsum = tsum + 2.0*tempXY.getYValue();
            }
        }

        return tempXYSeries;
    }


    /**
     * Trapezoid integral approximation
     * @param data XYSeries curve you want to calculate an integral
     * @return integral value as double
     */
    public static double trapezoid_integrate (XYSeries data) {
        double sum=0.0;
        for (int i=1; i<data.getItemCount(); i++) {
            sum = sum + (data.getX(i).doubleValue() - data.getX(i-1).doubleValue()) * (data.getY(i).doubleValue()+data.getY(i-1).doubleValue());
        }

        return sum*0.5;
    }
    /**
     *
     * @param qValues q-values from dataset
     * @param bins bins estimate from Shannon number
     * @param dmaxValue largest dimension of particle
     * @return
     */
    public static SimpleMatrix createMatrix (XYSeries qValues, int bins, double dmaxValue) {
        //find matrix size
        int m = qValues.getItemCount();
        int n = bins + 1;
        double deltaR = dmaxValue/bins;
        double atR;

        SimpleMatrix dmaxMatrix = new SimpleMatrix(m,n);
        //populate matrix
        for (int i=0; i<n; i++) {
            atR = i*deltaR;
            for (int j=0; j<qValues.getItemCount(); j++) {

                if(i==0) {
                    dmaxMatrix.set(j,i,1);
                } else {
                    dmaxMatrix.set(j, i, (Math.sin(qValues.getX(j).doubleValue()*atR))/(qValues.getX(j).doubleValue()*atR));
                }
            }
        }
        return dmaxMatrix;
    }
    /**
     *
     * @param x_data
     * @param y_data
     * @return
     */
    public static double[] leastSquares(double[] x_data, double[] y_data){
        double[] parameters = new double[5];
        double sumX = 0.0;
        double sumY = 0.0;
        double sumXxY = 0.0;
        double sumXsq = 0.0;
        double sumYsq = 0.0;
        double n = x_data.length;
        double invN = 1.0/n;
        for (int i=0; i < n; i++) {
            sumXsq = sumXsq + (x_data[i] * x_data[i]);
            sumXxY = sumXxY + (x_data[i] * y_data[i]);
            sumX = sumX + x_data[i];
            sumY = sumY + y_data[i];
            sumYsq = sumYsq + Math.pow(y_data[i],2);
        }

        double sumX2 = Math.pow(sumX, 2);
        double m = ((n*sumXxY)-(sumX*sumY))/((n*sumXsq)- sumX2);
        double b = ((sumY*invN)-((m*sumX)*invN));
        double s_e_2 = 1/(n*(n-2))*(n*sumYsq - Math.pow(sumY,2) - Math.pow(m,2)*(n*sumXsq - sumX2));
        double s_m_2 = n*s_e_2/(n*sumXsq- sumX2);
        double s_b_2 =  s_m_2*invN*sumXsq;

        parameters[0]=m;
        parameters[1]=b;
        parameters[2]=Math.sqrt(s_m_2); //standard deviation m (slope)
        parameters[3]=Math.sqrt(s_b_2); //standard deviation b (intercept)
        parameters[4] = Math.abs(n*sumXxY - sumX*sumY)/(Math.sqrt((n*sumXsq-sumX*sumX)*(n*sumYsq-sumY*sumY)));
        return parameters;
    }
    /**
     * Extract sub-array from 0 to n from an array
     * @param array Array of Doubles you want to extract sub-array from
     * @param n Index of the last element of the new Array
     * @return Array of Doubles representing sub-array
     */
    public static double[] subarray(double[] array, int n) {

        double[] ar = new double[n];
        System.arraycopy(array, 0, ar, 0, n);
        return ar;
    }


    /**
     * Extract array specifying start and end positions
     * @param array
     * @param startn
     * @param endn
     * @return
     */
    public static double[] subSelectarray(double[] array, int startn, int endn) {
        int endPos = endn - startn + 1;
        double[] ar = new double[endPos];
        System.arraycopy(array, startn, ar, 0, endPos);
        return ar;
    }

    /**
     * Returns an array of random numbers sorted
     * @param limit Size of the returned Array
     * @param max Upper limit of the random number selection
     * @return Array of inttegers
     */
    public static int[] randomArray(int limit, int max){
        sequence.clear();
        //create ArrayList of integers upto max value
        for (int i = 0; i < max; i++){
            sequence.add(i);
        }

        int[] values = values = new int[limit];

        for (int i = 0; i < limit; i++){
            int position = (int)(Math.random()*max);
            values[i] = sequence.get(position);
            sequence.remove(position);
            max = sequence.size();
        }
        Arrays.sort(values);
        return values;
    }

    public static int[] randomIntegers(int dmax, double maxq, int size, double percent){

        int count = (int)(size*percent);
        int bins = (int)(Math.round(maxq*dmax/Math.PI));
        if (count < bins){
            count = bins;
        }

        List<Integer> numbers = new ArrayList<Integer>();
        for(int i = 0; i < size; i++) {
            numbers.add(i);
        }

        // Shuffle them
        Collections.shuffle(numbers);

        // Pick count items.
        List<Integer> tempNumbers = numbers.subList(0, count);
        int[] values = new int[count];
        for(int i=0; i<count; i++){
            values[i] = tempNumbers.get(i);
        }

        Arrays.sort(values);
        return values;
    }

    /**
     * Grabs a limited number of random elements from evenly binned dataset
     * @param dmax
     * @param maxq
     * @param size
     * @param percent
     * @return
     */
    public static int[] randomBinning(int dmax, double maxq, int size, double percent){
        /*
         * Grabs a limited number of random elements from evenly binned dataset
         */
        int bins = (int)(Math.round(maxq*dmax/Math.PI));
        int binSize = (int)(Math.round(size/bins));
        int position;

        List<Integer> sequenceB = new ArrayList<Integer>();

        int limit = (int)(Math.round(binSize*percent)); //Number of elements to grab from each bin
        System.out.println("     Elements per bin " + limit);
        if (limit == 0){limit = 2;}
        System.out.println("Elements per bin After " + limit);

        int[] values = new int[(int)Math.round(percent*size)];

        int index = 0;

        for (int i=0; i < bins; i++){
            sequenceB.clear();
            //populate sequence with appropriate numbers (size is now size/bins
            int start = (int)(i*binSize);
            int end = (int)((i+1)*binSize);

            for(int j=start; j < end; j++){
                sequenceB.add(j);
            }

            //How many to grab?  Say bins*0.20
            for (int h = 0; h < limit; h++){
                position = generator.nextInt(sequenceB.size());

                if (index < values.length){
                    values[index] = sequenceB.get(position);
                    sequenceB.remove(position);
                }
                index++;
            }
        }

        Arrays.sort(values);
        return values;
    }
    /**
     * Returns array of length n, with random numbers without repetition from 0 to max
     *@param n Length of the Array
     *@param max Upper limit of random number
     *@return Array of random integers 0 to max
     */
    public static int[] rand_n(int n, int max) {

        int[] random_set = new int[n];
        Random wheel = new Random();

        Set<Integer> holder = new HashSet<Integer>();

        int count=0;
        while(count<n){
            int m = wheel.nextInt( max );
            if (!holder.contains(m)){
                holder.add(m);
                random_set[count] = m;
                count++;
            }
        }

        Arrays.sort(random_set);
        return random_set;
    }

    /**
     *
     * @param data XYSeries non-log10 data
     * @param point double point to be determined
     * @param scaleFactor
     * @return
     */
    public static Double[] interpolate(XYSeries data, double point, double scaleFactor){
        //Kriging interpolation
        int [] z = new int[6];
        int index=0;
        //loop over data to find the smalllest q rather than than point

        for (int i=0; i< data.getItemCount()-1; i++) {
            if (data.getX(i).doubleValue()>point){
                index = i;
                break;
            }
        }

        if (index <=1){
            for (int k=0; k<6; k++){
                z[k]=index+k;
            }
        } else if (index>=data.getItemCount()-3){
            z[0]=data.getItemCount()-6;
            z[1]=data.getItemCount()-5;
            z[2]=data.getItemCount()-4;
            z[3]=data.getItemCount()-3;
            z[4]=data.getItemCount()-2;
            z[5]=data.getItemCount()-1;
        } else {
            for (int k=-2; k<4; k++){
                z[k+2]=index+k;
            }
        }

        double scale = data.getX(z[5]).doubleValue()-data.getX(z[0]).doubleValue();

        SimpleMatrix c_m = new SimpleMatrix(6,6);
        //this might be (1,6)
        SimpleMatrix z_m = new SimpleMatrix(6,1);
        SimpleMatrix one = new SimpleMatrix(6, 1);
        for (int m=0; m<6; m++){
            one.set(m, 0, 1);
            double anchor = data.getX(z[m]).doubleValue();
            //delog data
            z_m.set(m, 0, data.getY(z[m]).doubleValue());
            for (int n=0; n<6; n++){
                c_m.set(m, n, 0.96*Math.exp(-1*(Math.pow(((anchor-data.getX(z[n]).doubleValue())/scale),2))));

            }
        }
        SimpleMatrix d_m = new SimpleMatrix(6,1);
        for (int m=0; m<6; m++){
            d_m.set(m, 0, 0.96*Math.exp( -1*(Math.pow(((point-data.getX(z[m]).doubleValue())/scale),2))));
        }

        double mu = ((one.transpose().mult(c_m.invert())).mult(z_m).get(0))/(one.transpose().mult(c_m.invert().mult(one))).get(0);

        Double[] resultS = new Double[3];
        resultS[0]=point;
        resultS[1]=mu+(d_m.transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(mu)))).get(0);
        resultS[1]=resultS[1]*scaleFactor; //returning log10 data

        double sigma_2 = ((z_m.minus(one.scale(resultS[1]))).transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(resultS[1])))).get(0)/6;
        double sigma_d = ((d_m.transpose().mult(c_m.invert())).mult(d_m)).get(0);
        double tmp1 = sigma_2*(1-sigma_d);
        resultS[2]=Math.sqrt(tmp1);

        return resultS;

    }

    /**
     * transforms data to q^2 vs ln I(q)
     * @param data q-values and intensities of dataset
     * @param errors associated errors
     * @param startAt index from spinner
     * @return
     */
    public static double[] autoRgTransformIt(XYSeries data, XYSeries errors, int startAt){
        XYSeries qq = new XYSeries("qq");
        XYSeries error = new XYSeries("error");

        int total = data.getItemCount();
        int starthere = startAt-1;
        XYDataItem tempItem;
        for (int i=starthere; i<total; i++){
            tempItem = data.getDataItem(i);
            if (tempItem.getYValue() > 0){  // no negative values
                qq.add(tempItem.getXValue()*tempItem.getXValue(), Math.log(tempItem.getYValue()));
                error.add(errors.getDataItem(i));
            }
        }

        return autoRg(qq, error, 1);
    }

    /**
     * AutoRg algorithm for calculating I(zero) and Rg
     * @param data - XYSeries must be nonNegative as q^2, ln[I(q)]
     * @param errors - associated errors as XYSeries
     * @return array of doubles
     */
    public static double[] autoRg(XYSeries data, XYSeries errors, int startAt) {

        int first = startAt;
        //int last = data.getItemCount()-1;
        double slope, intercept, temp_resi, tempMedian, median = 100000;
        double tempRg, rg=0;

        XYDataItem tempDataItem = data.getDataItem(first);
        XYDataItem lastItem, item;
        ArrayList<Double> resList = new ArrayList<>();
        ArrayList<Double> rgList = new ArrayList<>();
        // calculate line between first and last points
        int lastAtLimit = 0;
        double lowerqlimit = 0.12*0.12;
        double qRgLow = 0.2*0.2;
        double qRgUp = 1.3*1.3;

        while(data.getX(lastAtLimit).doubleValue() < lowerqlimit){
            lastAtLimit++;
        }
        int last = lastAtLimit;

        while( tempDataItem.getXValue() < lowerqlimit && first < (lastAtLimit-10)){  // minimum line is defined by 5 points
            // fit line to first and last point, calculate Rg, determine gRg limit
            while (last > first+7) {
                lastItem = data.getDataItem(last);
                // calculate line
                slope = (lastItem.getYValue() - tempDataItem.getYValue())/(lastItem.getXValue() - tempDataItem.getXValue());
                intercept = lastItem.getYValue() - slope*lastItem.getXValue();
                tempRg = -3.0*slope;

                if (tempRg > 0 && lastItem.getXValue()*tempRg < qRgUp){  // means we have a reasonable limit
                    resList.clear();

                    for(int i=first; i<last; i++){
                        item = data.getDataItem(i);
                        temp_resi = item.getYValue() - (slope*item.getXValue() + intercept);
                        resList.add(temp_resi*temp_resi);
                    }
                    // get median
                    tempMedian = Statistics.calculateMedian(resList, true);
                    if (tempMedian < median){
                        rgList.add(FastMath.sqrt(tempRg));
                        median = tempMedian;
                    }
                }

                last--;
            }
            last = lastAtLimit;
            //
            first++;
            tempDataItem = data.getDataItem(first);
        }

        rg = Statistics.calculateMedian(rgList, true); // rough estimate of Rg

        double errorSlope = 0.0;
        double errorIntercept = 0.0;
        int itemCount = data.getItemCount();

        //create vector
        double xvalue, yvalue;

        int count=0;
        double c0, c1;

        double minResidual = 10000000000.0;
        double[] x_range;
        double[] y_range;
        //double[] w_range;
        double r2_coeff = 0.0;

        double[] residuals3;
        //rg = Math.sqrt(-3.0*slope);
        double rg2 = rg*rg;
        double i_zero;

        int endAt = 0;
        int startAtLimit = 0;
        // how many points are within upperlimit?
        for(int i=0; i < itemCount; i++){
            XYDataItem dat = data.getDataItem(i);
            xvalue = dat.getXValue();

            if (xvalue*rg2 <= qRgLow){
                startAtLimit++;
            }

            if ((xvalue*rg2 <= qRgUp)) {
                endAt++;
            } else {
                break;
            }
        }

        int sizeOfArray = endAt - startAtLimit + 1;
        // perform least median square fitting
        int arrayIndex = 0;

        double tempqmaxLimit, qmax13Limit =0;

//        long startTime = System.nanoTime();

        if (sizeOfArray > 5){
            int window = 13;
            x_range = new double[window];
            y_range = new double[window];
            double[] keptResiduals = new double[0];
            int keptStartAt=0;
            double keptSlope=0, keptIntercept=0;

            int upTO = startAtLimit + window;
            residuals3 = new double[endAt];
            keptResiduals = new double[endAt];

            while(upTO < endAt){

                for (int i = 0; i < window; i++) {
                    XYDataItem dat = data.getDataItem(i+startAtLimit);
                    x_range[i] = dat.getXValue();  // q^2
                    y_range[i] = dat.getYValue();  // ln(I(q))
                }

                double[] param3 = Functions.leastSquares(x_range, y_range);
                c1 = param3[0];
                c0 = param3[1];

                if (c1 < 0){ // slope has to be negative
                    //double tempRgat = Math.sqrt(-3*c1);
                    //tempqmaxLimit = 1.3/tempRgat;

                    //  if (tempqmaxLimit > qmax13Limit && (tempqmaxLimit*tempqmaxLimit) < x_range[window-1]){

                    for (int v = 0; v < endAt; v++) {
                        XYDataItem dat = data.getDataItem(v);
                        residuals3[v] = Math.pow((dat.getYValue() - (c1 * dat.getXValue() + c0)), 2);
                    }

                    Arrays.sort(residuals3);
                    double median_test = Functions.median(residuals3);

                    if (median_test < minResidual) {
                        minResidual = median_test;
                        System.arraycopy(residuals3, 0, keptResiduals, 0, endAt);
                        //keptStartAt = startAtLimit;
                        keptSlope = c1;
                        keptIntercept = c0;
                        //         qmax13Limit = tempqmaxLimit;
                    }
                    //   }

                }

                startAtLimit++;
                upTO = startAtLimit + window;
            }

            double s_o = 1.4826 * (1.0 + 5.0 / (endAt - 2 - 1)) * Math.sqrt(minResidual);
            double inv_s_o = 1.0/s_o;

            // create final dataset for final fit
            count = 0;
            ArrayList<Integer> keepers = new ArrayList<Integer>();

            XYDataItem dataItem;
            for (int i = 0; i < endAt; i++) {
                //residualAt = Math.pow((data.getY(i).doubleValue() - (keptSlope * data.getX(i).doubleValue() + keptIntercept)), 2);
                dataItem = data.getDataItem(i);
                //if (Math.abs((dataItem.getYValue() - (keptSlope * dataItem.getXValue() + keptIntercept))) * inv_sigma < 2.5) {
                if (Math.abs((dataItem.getYValue() - (keptSlope * dataItem.getXValue() + keptIntercept))) * inv_s_o < 2.0) {
                    // decide which ln[I(q)] values to keep
                    keepers.add(i);
                    count++;
                }
            }

            // determines values to keep for fitting to determine Rg and I(zero)
            double[] final_x = new double[count];
            double[] final_y = new double[count];
            //double[] final_w = new double[count];
            int keep;

            XYDataItem dat;
            for (int i = 0; i < count; i++) {
                keep = keepers.get(i);
                dat=data.getDataItem(keep);
                final_x[i] = dat.getXValue();
                final_y[i] = dat.getYValue();
                //final_w[i] = errors.getY(keep).doubleValue();
            }

            arrayIndex = count;

            double[] param3 = Functions.leastSquares(final_x, final_y);
            slope = param3[0];
            intercept = param3[1];
            errorSlope = param3[2];
            errorIntercept = param3[3];
            rg = Math.sqrt(-3.0 * slope);
            i_zero = Math.exp(intercept);

        } else {
            // ignore first three points, take the next 5 and fit
            int arrayLimit = 7;
            x_range = new double[arrayLimit];
            y_range = new double[arrayLimit];
            //w_range = new double[5];

            getDataLoop:
            for (int i = 3; i < itemCount; i++) {
                XYDataItem dat = data.getDataItem(i);
                xvalue = dat.getXValue(); // q^2
                yvalue = dat.getYValue();
                //x^2
                if (arrayIndex < arrayLimit) {
                    //x_data[i] = Math.pow(xvalue, 2);
                    x_range[arrayIndex] = xvalue;  // q^2
                    //ln(y)
                    y_range[arrayIndex] = yvalue;  // ln(I(q))
                    //ln(y)*error*y
                    //w_range[arrayIndex] = yvalue * errors.getY(i).doubleValue() * Math.exp(yvalue);
                    //original data
                    arrayIndex++;
                } else {
                    break getDataLoop;
                }
            }
            // fit line and use as default fit
            double[] param3 = Functions.leastSquares(x_range, y_range);
            slope = param3[0];
            intercept = param3[1];
            errorSlope = param3[2];
            errorIntercept = param3[3];

            rg = Math.sqrt(-3.0 * slope);
            i_zero = Math.exp(intercept);
        }

        //procedure for calculating Izero and Rg
        System.out.println("Determined Rg " + rg);
        double[] parameters = new double[6];

        if ((arrayIndex <= 7) || (Double.isNaN(rg) || (Double.isNaN(i_zero)))){
            System.out.println("AutoRg failed, too few points in Guinier Region: " + data.getKey());
            parameters[0]=0;
            parameters[1]=0;
            parameters[2]=0;
            parameters[3]=0;
            parameters[4]=0;
            parameters[5]=1; // percent rejected
        } else {
            parameters[0]=i_zero;
            parameters[1]=rg;
            parameters[2]=i_zero*errorIntercept;  //Izero Error
            parameters[3]=1.5*errorSlope*Math.sqrt(1/3.0*1/rg);  //Rg Error
            parameters[4]=r2_coeff;
            parameters[5]=(arrayIndex - count)/(double)arrayIndex; // percent rejected
        }

        return parameters;
    }



    /**
     *
     * @param yVector
     * @param designMatrix
     * @return
     */
    public static double[] complexation(double[] yVector, ArrayList<double[]> designMatrix){
        // number of elements in number of columns in designMatrix + 1 (1: constant offset)
        int cols = designMatrix.get(0).length;
        int rows = yVector.length;
        int constants = cols + 1;

        //LinearSolver solver = LinearSolverFactory.leastSquares(rows, constants);
        // Begin Solve Method from Moore paper
        FMatrixRMaj cmn = new FMatrixRMaj(rows, constants);
        FMatrixRMaj ym = new FMatrixRMaj(rows, 1);

//        SimpleMatrix cmn = new SimpleMatrix(rows, constants);
//        SimpleMatrix ym = new SimpleMatrix(rows,1);

        double[] params = new double[constants];
        //DenseMatrix64F coeffs = new DenseMatrix64F(constants,1);

        double[] column;
        for(int i=0; i<rows; i++){
            column = designMatrix.get(i);
            ym.set(i, (float)yVector[i]);

            for(int j=0; j<constants; j++){

                if (j < cols){
                    cmn.set(i,j,(float)column[j]);
                } else {
                    cmn.set(i,j,1);
                }
            }
        }

        FMatrixRMaj coeffs = new FMatrixRMaj(constants,1);

//        try {
//            SimpleMatrix coeffs = cmn.solve(ym);
//        } catch ( SingularMatrixException e ) {
//            throw new IllegalArgument("Singular matrix");
//        }

        if (!CommonOps_FDRM.solve(cmn,ym,coeffs)){
            System.out.println("Exception found with Matrix, trying again");
            for(int j=0; j< constants; j++){
                params[j] = 0.0;
            }
            return params;
        }
//
//        solver.solve(ym.getMatrix(), coeffs);

        for(int j=0; j< constants; j++){
            params[j] = coeffs.get(j,0);
        }

        return params;
    }

    /**
     *
     * @param data
     * @param point
     * @return
     */
    public static Double[] interpolateSigma(XYSeries data, double point){
        //Kriging interpolation,

        int [] z = new int[6];
        int index=0;
        //loop over data to find the smallles q grather than than point

        for (int i=0; i< data.getItemCount()-1; i++) {
            if (data.getX(i).doubleValue()>point){
                index = i;
                break;
            }
        }

        if (index <=1){
            for (int k=0; k<6; k++){
                z[k]=index+k;
            }
        } else if (index>=data.getItemCount()-3){
            z[0]=data.getItemCount()-6;
            z[1]=data.getItemCount()-5;
            z[2]=data.getItemCount()-4;
            z[3]=data.getItemCount()-3;
            z[4]=data.getItemCount()-2;
            z[5]=data.getItemCount()-1;
        } else {
            for (int k=-2; k<4; k++){
                z[k+2]=index+k;
            }
        }

        double scale = data.getX(z[5]).doubleValue()-data.getX(z[0]).doubleValue();

        SimpleMatrix c_m = new SimpleMatrix(6,6);
        //this might be (1,6)
        SimpleMatrix z_m = new SimpleMatrix(6,1);
        SimpleMatrix one = new SimpleMatrix(6, 1);
        for (int m=0; m<6; m++){
            one.set(m, 0, 1);
            double anchor = data.getX(z[m]).doubleValue();
            //delog data
            z_m.set(m, 0, data.getY(z[m]).doubleValue());
            for (int n=0; n<6; n++){
                c_m.set(m, n, 0.96*Math.exp(-1*(Math.pow(((anchor-data.getX(z[n]).doubleValue())/scale),2))));

            }
        }
        SimpleMatrix d_m = new SimpleMatrix(6,1);
        for (int m=0; m < 6; m++){
            d_m.set(m, 0, 0.96*Math.exp( -1*(Math.pow(((point-data.getX(z[m]).doubleValue())/scale),2))));
        }

        double mu = ((one.transpose().mult(c_m.invert())).mult(z_m).get(0))/(one.transpose().mult(c_m.invert().mult(one))).get(0);

        Double[] resultD = new Double[3];
        resultD[0]=point;
        resultD[1]=mu+(d_m.transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(mu)))).get(0);


        double sigma_2 = ((z_m.minus(one.scale(resultD[1]))).transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(resultD[1])))).get(0)/6;
        double sigma_d = ((d_m.transpose().mult(c_m.invert())).mult(d_m)).get(0);
        double tmp1 = sigma_2*(1-sigma_d);
        resultD[2]=Math.sqrt(tmp1);

        return resultD;

    }
    /**
     *
     * @param data
     * @param error
     * @param point
     * @return
     */
    public static Double[] interpolateOriginal(XYSeries data, XYSeries error, double point){
        //Kriging interpolation, use input log10 data
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

        Double[] resultS = new Double[3];
        resultS[0]=point;
        resultS[1]=mu+(d_m.transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(mu)))).get(0);

        double sigma_2 = ((z_m.minus(one.scale(resultS[1]))).transpose().mult(c_m.invert()).mult(z_m.minus(one.scale(resultS[1])))).get(0)/6;
        double sigma_d = ((d_m.transpose().mult(c_m.invert())).mult(d_m)).get(0);
        double tmp1 = sigma_2*(1-sigma_d);
        //resultS[2]=Math.sqrt(tmp1); // stdev
        resultS[2] = stdev_esitmate/6.0;

        return resultS;
    }


    public static LinkedHashMap sortHashMapByValuesD(HashMap passedMap) {
        List mapKeys = new ArrayList(passedMap.keySet());
        List mapValues = new ArrayList(passedMap.values());
        Collections.sort(mapValues, Collections.reverseOrder());
        Collections.sort(mapKeys);


        LinkedHashMap sortedMap =
                new LinkedHashMap();

        Iterator valueIt = mapValues.iterator();
        while (valueIt.hasNext()) {
            Object val = valueIt.next();
            Iterator keyIt = mapKeys.iterator();

            while (keyIt.hasNext()) {
                Object key = keyIt.next();
                String comp1 = passedMap.get(key).toString();
                String comp2 = val.toString();

                if (comp1.equals(comp2)){
                    passedMap.remove(key);
                    mapKeys.remove(key);
                    sortedMap.put((Integer)key, (Double)val);
                    break;
                }
            }

        }
        return sortedMap;
    }



    /*
    public static ArrayList<Double> svdReduce(Scatter.PrModel prModel, int total, int dmax, int selectedSize, int coefSize){
        ArrayList<Double> returnMe = new ArrayList<Double>();
        int rows = coefSize-1;
        int cols = selectedSize;

        // arrange in order of decreasing I(0) or a1 coefficient for SVD matrix
        //double[] order = new double[selectedSize];
        //int start=0;

        HashMap<Integer, Double> izeros = new HashMap<Integer, Double>();

        RealSpace temp;

        for(int i=0; i<total; i++){
            temp = prModel.getDataset(i);
            if (temp.getSelected()){
                izeros.put(i,temp.getIzero()); // key is index
            }
        }


        // Sort based on Izeros
        LinkedHashMap sorted = version4.Functions.sortHashMapByValuesD(izeros);
        DenseMatrix64F matrixA = new DenseMatrix64F(rows,cols);


        Iterator it = sorted.entrySet().iterator();
        int col = 0;
        while(it.hasNext()){
            Map.Entry pair = (Map.Entry)it.next();
            temp = prModel.getDataset((Integer)pair.getKey());
            //System.out.println("IZero : " + pair.getKey() + " " + pair.getValue());

            for(int r=0; r<rows; r++){
                matrixA.set(r, col, temp.getMooreCoefficients()[r+1]);
                //System.out.println(pair.getKey() + " " + (r+1) + " " + temp.getMooreCoefficients()[r+1]);
            }
            it.remove();
            col++;
        }

        SingularValueDecomposition<DenseMatrix64F> svd = DecompositionFactory.svd(rows, cols, true, true, false);

        try
        {
            svd.decompose(matrixA);
        } catch (Exception e){
            System.out.println("Matrix inversion exception in svdReduce ");
            return returnMe;
        }

        DenseMatrix64F U = svd.getU(null,false);
        DenseMatrix64F W = svd.getW(null);
        DenseMatrix64F V = svd.getV(null,false);


        System.out.println("U:");
        U.print();
        System.out.println("W");
        W.print();
//        System.out.println("V");
//        V.print();

        String output="REMARK Input Matrix of Moore Coefficients, A:\n";

        DecimalFormat formatter = new DecimalFormat("0.00000E0");
        for(int r=0; r<rows; r++){
            for(int c=0; c<cols; c++){
                //output += "\t" + formatter.format(matrixA.get(r,c));
                output += " " + String.format("%2.6e",matrixA.get(r,c));
            }
            output +="\n";
        }

        output += "\nREMARK A = U*S*V_t\n";
        output += "\nREMARK U Matrix ("+rows+"x"+rows+"):\n";

        rows = U.getNumCols();
        for(int r=0; r<rows; r++){
            for(int c=0; c<rows; c++){
                output += "\t" + formatter.format(U.get(r,c));
            }
            output +="\n";
        }

        rows = W.getNumRows();
        cols = W.getNumCols();
        output += "\nREMARK S Matrix ("+rows+"x"+cols+") Singular values:\n";

        for(int r=0; r<rows; r++){
            for(int c=0; c<cols; c++){
                output += "\t" + formatter.format(W.get(r, c));
            }
            output +="\n";
        }

        rows = V.getNumRows();
        cols = V.getNumCols();
        output += "\nREMARK V Matrix ("+rows+"x"+cols+") Singular values:\n";

        for(int r=0; r<rows; r++){
            for(int c=0; c<cols; c++){
                output += "\t" + formatter.format(V.get(r, c));
            }
            output +="\n";
        }

        FileWriter fw = null; //the true will append the new data
        try {
            fw = new FileWriter("SVDMatrix_elements.txt", true);
            fw.write(output+"\n"); //appends the string to the file
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        double[] mooreCoefs = new double[coefSize];
        for(int i=0; i<rows; i++){
            returnMe.add(U.get(i,0));
            mooreCoefs[i+1] = returnMe.get(i);
        }

        ArrayList<Double> invariants = version4.Functions.saxs_invariants(mooreCoefs, dmax);
        for(int i=0; i<invariants.size(); i++){
            // i_zero, rg, average_r
            System.out.println(i + " invariants\t" + invariants.get(i));
        }

        return returnMe;
    }
*/

    /**
     * Creates a Color object based on value and maximum for heat map like coloring
     * @param maximum
     * @param value
     * @return Color as a heat map
     */
    public static Color giveRGB(double maximum, double value){

        double ratio = 2 * value/maximum;

        int blue = (int)(Math.max(0, 255*(1 - ratio)));
        int red = (int)(Math.max(0, 255*(ratio - 1)));
        int green = 255 - blue - red;
        return new Color(red,green,blue);
    }

    /**
     * Creates a Color object based on value and maximum for heat map like coloring
     * @param maximum
     * @param value
     * @return Color as a heat map
     */
    public static Color giveTransRGB(double maximum, double value){

        double ratio = 2 * value/maximum;

        int blue = (int)(Math.max(0, 255*(1 - ratio)));
        int red = (int)(Math.max(0, 255*(ratio - 1)));
        int green = 255 - blue - red;
        return new Color(red,green,blue, (int)(80*value/maximum));
    }


    public static double s_n(ArrayList<Double> values){
        int sizeOf = values.size();
        double x_i;

        ArrayList<Double> diffs = new ArrayList<Double>();
        ArrayList<Double> medians = new ArrayList<Double>();

        for(int i=0; i<sizeOf; i++){
            x_i = values.get(i);
            diffs.clear();
            for(int j=0; j<sizeOf; j++){
                diffs.add(Math.abs(x_i - values.get(j)));
            }
            medians.add(Statistics.calculateMedian(diffs,true));
        }

        return Statistics.calculateMedian(medians, true);
    }

    /**
     * write generic ArrayList of Strings to file
     * @param lines
     * @param filename
     * @param workingDirectoryName
     * @return
     */
    public static boolean writeLinesToFile(ArrayList<String> lines, String filename, String workingDirectoryName){

        int total = lines.size();

        File targetDir = new File(workingDirectoryName);

        if (!(targetDir.exists() && targetDir.isDirectory())) {
            boolean testDir = targetDir.mkdir();
            if (!testDir){
                try {
                    throw new Exception("CANNOT_CREATE_TEST_DIRECTORY: " + workingDirectoryName);
                } catch (Exception e) {
                    System.out.println("SYSTEM ERROR " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }


        FileWriter fw = null;
        try {
            fw = new FileWriter(workingDirectoryName+"/"+filename);
            BufferedWriter out = new BufferedWriter(fw);

            for (int n=0; n < total; n++) {
                out.write(lines.get(n) + "\n");
            }
            out.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }



    /*
     * returns (n*PI) for 1 <= n <= 250
     */
    public static double n_pi_square(int n){
        double[] npiValues = {
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
        return npiValues[n];
    }

    public static XYSeriesCollection svdNoiseReduce(Collection dataCollection) {

        XYSeriesCollection returnMe = new XYSeriesCollection();

        int totalSets = dataCollection.getTotalDatasets();

        int firstIndex=0;
        Dataset firstDataSet = dataCollection.getDataset(firstIndex);

        double qmin = 0.0, tempqmin, qmax=0.0;

        // reinitialize incase first in collection is not in use
        firstLoop:
        for(int i=0; i<totalSets; i++){
            if (dataCollection.getDataset(i).getInUse()){
                firstDataSet = dataCollection.getDataset(i);
                firstIndex=i;
                break firstLoop;
            }
        }

        int trueCount=0;

        trueLoop:
        for(int i=0; i<totalSets; i++){
            if (dataCollection.getDataset(i).getInUse()){
                trueCount++;
            }
        }

        // find qmin in common to allDataset
        boolean flag = false;
        boolean flagX = false;

        int totalInFirst = firstDataSet.getAllData().getItemCount();
        XYSeries firstData = firstDataSet.getAllData();
        XYSeries tempData;

        int setCount;

        qminLoopCheck:
        for(int i=0; i<totalInFirst; i++){
            qmin = firstData.getX(i).doubleValue();
            setCount = 0;

            setLoop:
            for(int j=firstIndex; j<totalSets; j++){
                tempData = dataCollection.getDataset(j).getAllData();
                int totalInTemp = tempData.getItemCount();

                searchLoop:
                for(int w=0; w<totalInTemp; w++){
                    tempqmin = tempData.getX(w).doubleValue();
                    if (tempqmin == qmin){
                        setCount++;
                        break searchLoop;
                    } else if (tempqmin > qmin){
                        //break out of loop and move to next qmin in firstData
                        break setLoop;
                    }
                }
            }

            // IF I MAKE IT ALL THE WAY TO THE END OF THE SET WITH THE SAME QMIN BREAK LOOP
            if (trueCount == setCount) {
                flag = true;
                break qminLoopCheck;
            }
        }

        int firstMax = totalInFirst - 2;

        qmaxLoopCheck:
        for(int i=firstMax; i>1; i--){
            qmax = firstData.getX(i).doubleValue();

            setCount = 0;

            setLoop:
            for(int j=firstIndex; j<totalSets; j++){
                tempData = dataCollection.getDataset(j).getAllData();
                int totalInTemp = tempData.getItemCount()-1;

                searchLoop:
                for(int w=totalInTemp; w>1; w--){
                    tempqmin = tempData.getX(w).doubleValue();
                    if (tempqmin == qmax){
                        setCount++;
                        break searchLoop;
                    } else if (tempqmin < qmax){
                        //break out of loop and move to next qmin in firstData
                        break setLoop;
                    }
                }
            }

            // IF I MAKE IT ALL THE WAY TO THE END OF THE SET WITH THE SAME QMAX BREAK LOOP
            if (trueCount == setCount) {
                flagX = true;
                break qmaxLoopCheck;
            }
        }

        if (flag && flagX){
            //Assemble matrix for
            int rows=0;
            for(int i=0; i<totalInFirst; i++){
                tempqmin = firstData.getX(i).doubleValue();
                if (tempqmin >= qmin && tempqmin <= qmax){
                    rows++;
                }
            }

            FMatrixRMaj matrixA = new FMatrixRMaj(rows, trueCount);
            FMatrixRMaj variances = new FMatrixRMaj(rows,1);
//            DenseMatrix64F matrixA = new DenseMatrix64F(rows,trueCount);
//            DenseMatrix64F variances = new DenseMatrix64F(rows,1);

            double tempValue;
            XYSeries errorData;

            int col=0, row;

            for(int i=0; i<totalSets; i++){

                if (dataCollection.getDataset(i).getInUse()){

                    tempData = dataCollection.getDataset(i).getAllData();
                    errorData = dataCollection.getDataset(i).getAllDataError();

                    int totalInTemp = tempData.getItemCount();

                    row=0;
                    for(int w=0; w<totalInTemp; w++){

                        tempqmin = tempData.getX(w).doubleValue();
                        if (tempqmin >= qmin && tempqmin <= qmax){
                            matrixA.set(row, col, (float)tempData.getY(w).doubleValue());
                            tempValue = errorData.getY(w).doubleValue();
                            variances.set(row,0, (float)(variances.get(row,0) + 1.0d/(tempValue*tempValue)));
                            row++;
                        }
                    }
                    col++;
                }
            }
//FDRM
//            FMatrixRMaj U = new FMatrixRMaj(rows,rows);
//            FGrowArray sv = new FGrowArray();
//            FMatrixRMaj Vt = new FMatrixRMaj(trueCount,trueCount);

            //SingularOps_FDRM.singularValues(matrixA);
            //SingularOps_FDRM.svd(matrixA, U, W, V);

            SingularValueDecomposition_F32<FMatrixRMaj> svd = DecompositionFactory_FDRM.svd(rows, trueCount, true, true, false);

            try {
                svd.decompose(matrixA);
            } catch (Exception e){
                System.out.println("Matrix inversion exception in svdReduce ");
            }

            FMatrixRMaj U = svd.getU(null,false);
            FMatrixRMaj W = svd.getW(null);
            FMatrixRMaj V = svd.getV(null,false);

//            SimpleMatrix U = SimpleMatrix.wrap(svd.getU(null,false));
//            SimpleMatrix W = SimpleMatrix.wrap(svd.getW(null));
//            SimpleMatrix Vt = SimpleMatrix.wrap(svd.getV(null,true));

            /*
             SVD Averaging means only first singular value is significant
             */
//            double firstss = W.get(0,0);
//            int svd_index = 1;
           // System.out.println("SINGULAR VALUE => INDX    VALUE  RATIO\n");
            for(int i=0; i<trueCount; i++){
                // System.out.format("SINGULAR VALUE => %4d %8.4f  %8.6f\n", svd_index, W.get(i,i), firstss/W.get(i,i));
//                svd_index++;
                //System.out.println("Singular values : " +  + " => " + W.get(i,i) + " <=ratio to first => " + firstss/W.get(i,i));
                if (i > 0){
                    W.set(i,i,0);
                }
            }

            FMatrixRMaj s = new FMatrixRMaj(U.numRows, W.numCols);
            CommonOps_FDRM.mult(U, W, s);

//            DenseMatrix64F s = new DenseMatrix64F(U.getNumRows(), W.getNumCols());
//            CommonOps.mult(U,W,s);

            FMatrixRMaj vt = new FMatrixRMaj(V.getNumCols(), V.getNumRows());
            CommonOps_FDRM.transpose(V, vt);

            //DenseMatrix64F vt = new DenseMatrix64F(V.getNumCols(), V.getNumRows());
            //CommonOps.transpose(V, vt);

            FMatrixRMaj reduced = new FMatrixRMaj(s.numRows, V.numCols);
            CommonOps_FDRM.mult(s, vt, reduced);

//            DenseMatrix64F reduced = new DenseMatrix64F(s.getNumRows(), V.getNumRows());
//            CommonOps.mult(s, vt, reduced);

            rows=0;
            double average;
            double inv_col = 1.0/(double)trueCount;

            XYSeries returnI = new XYSeries("SVD reduced I(q");
            XYSeries returnE = new XYSeries("SVD reduced error");

            for(int i=0; i<totalInFirst; i++){
                tempqmin = firstData.getX(i).doubleValue();
                average = 0.0;
                if (tempqmin >= qmin && tempqmin <= qmax){

                    for(int c=0; c<trueCount; c++){
                        average += reduced.get(rows, c);
                    }
                    returnI.add(tempqmin, average*inv_col);
                    returnE.add(tempqmin, Math.sqrt(1.0/variances.get(rows,0)));
                    //System.out.println(tempqmin + " " + average*inv_col);
                    rows++;
                }
            }

            returnMe.addSeries(returnI);
            returnMe.addSeries(returnE);
        }

        System.out.println("FINISHED " + returnMe.getSeries(0).getItemCount() + " " + returnMe.getSeries(1).getItemCount());
        return returnMe;
    }


    /**
     *
     */
    public static Double findLeastCommonQvalue(Collection samplesCollection){

        Dataset firstSet = samplesCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = samplesCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        Number minQvalueInCommon = 10;

        for(int j=0; j < referenceData.getItemCount(); j++){

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            minQvalueInCommon = refItem.getX();
            boolean isCommon = true;

            startAt = 1;

            for(; startAt < totalInSampleSet; startAt++) {


                tempDataset = samplesCollection.getDataset(startAt);
                tempData = tempDataset.getAllData();
                // check if refItem q-value is in tempData
                // if true, check next value
                if (tempData.indexOf(refItem.getX()) < 0) {
                    isCommon = false;
                    break;
                }
            }

            if (startAt == totalInSampleSet && isCommon){
                break;
            }
        }

        System.out.println("Minimum Common q-value : " + minQvalueInCommon);
        return minQvalueInCommon.doubleValue();
    }


    /**
     *
     */
    public static Double findMaximumCommonQvalue(Collection samplesCollection){

        Dataset firstSet = samplesCollection.getDataset(0);
        Dataset tempDataset;
        int totalInSampleSet = samplesCollection.getTotalSelected();
        XYSeries referenceData = firstSet.getAllData(), tempData;
        XYDataItem refItem;
        int startAt;
        Number maxQvalueInCommon = 0;

        for(int j=(referenceData.getItemCount()-1); j > -1; j--){

            refItem = referenceData.getDataItem(j); // is refItem found in all sets
            maxQvalueInCommon = refItem.getX();
            boolean isCommon = true;

            startAt = 1;

            for(; startAt < totalInSampleSet; startAt++) {


                tempDataset = samplesCollection.getDataset(startAt);
                tempData = tempDataset.getAllData();
                // check if refItem q-value is in tempData
                // if true, check next value
                if (tempData.indexOf(refItem.getX()) < 0) {
                    isCommon = false;
                    break;
                }
            }

            if (startAt == totalInSampleSet && isCommon){
                break;
            }
        }

        System.out.println("Maximum Common q-value : " + maxQvalueInCommon);
        return maxQvalueInCommon.doubleValue();
    }

    public static String sanitizeForFilename(String text){
        String temp = text.replaceAll("/", "_per_");
        temp = temp.replaceAll(" ", "_");
        return temp;
    }

}

