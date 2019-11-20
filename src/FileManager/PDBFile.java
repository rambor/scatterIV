package FileManager;

import org.apache.commons.math3.util.FastMath;
import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import quickhull3d.Point3d;
import quickhull3d.QuickHull3D;
import version4.Bead;
import version4.Functions;
import version4.PDBAtom;

import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * Created by robertrambo on 08/02/2016.
 */
public class PDBFile {
    private int dmax;
    private int totalAtomsRead, totalAtoms;
    private double qmax, qmin = 0.005, delta_q, qrmin;
    private double qrIncrement, invQRIncrement;
    private int pr_bins, totalCounts, totalQValues;
    private double delta_r;
    private double[] distances;
    private boolean useWaters = false;
    private XYSeries pdbdata;
    private XYSeries high_res_pr_data;
    private XYSeries icalc;
    private XYSeries error;
    private ArrayList<PDBAtom> atoms;
    private ArrayList<PDBAtom> centeredAtoms;
    private ArrayList<PDBAtom> workingCoords;
    private int[] workingAtomicNumbers;
    private String filename;
    private String currentWorkingDirectory;
    private double[] mooreCoefficients;
    private double izero, rg, rmin;
    private double[] formfactors;
    private double[] sineTable;
    private int sizeOfSineTable;
    double[] asfs;
    List<Integer> atomicList;

    private double highresWidth = 2.79d;

    /*
     * atomic scattering form factors
     */
    private double[][] asf_coeffs = {   //a1 a2 a3 a4 a5 c b1 b2 b3 b4 b5
            {},
            {0.413048,   0.294953,   0.187491,   0.080701,   0.023736,   0.000049,  15.569946,  32.398468,   5.711404,  61.889874,   1.334118}, //H
            {0.732354,   0.753896,   0.283819,   0.190003,   0.039139,   0.000487,  11.553918,   4.595831,   1.546299,  26.463964,   0.377523}, //He
            {0.974637,   0.158472,   0.811855,   0.262416,   0.790108,   0.002542,   4.334946,   0.342451,  97.102966, 201.363831,   1.409234}, //Li
            {1.533712,   0.638283,   0.601052,   0.106139,   1.118414,   0.002511,  42.662079,   0.595420,  99.106499,   0.151340,   1.843093}, //Be
            {2.085185,   1.064580,   1.062788,   0.140515,   0.641784,   0.003823,  23.494068,   1.137894,  61.238976,   0.114886,   0.399036}, //B
            {2.657506,   1.078079,   1.490909,  -4.241070,   0.713791,   4.297983,  14.780758,   0.776775,  42.086842,  -0.000294,   0.239535}, //C
            {11.893780,   3.277479,   1.858092,   0.858927,   0.912985,  -11.80490,   0.000158,  10.232723,  30.344690,   0.656065,   0.217287},//N
            {2.960427,   2.508818,   0.637853,   0.722838,   1.142756,   0.027014,  14.182259,   5.936858,   0.112726,  34.958481,   0.390240}, //O
            {3.511943,   2.772244,   0.678385,   0.915159,   1.089261,   0.032557,  10.687859,   4.380466,   0.093982,  27.255203,   0.313066}, //F
            {4.183749,   2.905726,   0.520513,   1.135641,   1.228065,   0.025576,   8.175457,   3.252536,   0.063295,  21.813910,   0.224952}, //Ne
            {4.910127,   3.081783,   1.262067,   1.098938,   0.560991,   0.079712,   3.281434,   9.119178,   0.102763, 132.013947,   0.405878}, //Na
            {4.708971,   1.194814,   1.558157,   1.170413,   3.239403,   0.126842,   4.875207, 108.506081,   0.111516,  48.292408,   1.928171}, //Mg
            {4.730796,   2.313951,   1.541980,   1.117564,   3.154754,   0.139509,   3.628931,  43.051167,   0.095960, 108.932388,   1.555918}, //Al
            {5.275329,   3.191038,   1.511514,   1.356849,   2.519114,   0.145073,   2.631338,  33.730728,   0.081119,  86.288643,   1.170087}, //Si
            {1.950541,   4.146930,   1.494560,   1.522042,   5.729711,   0.155233,   0.908139,  27.044952,   0.071280,  67.520187,   1.981173}, //P
            {6.372157,   5.154568,   1.473732,   1.635073,   1.209372,   0.154722,   1.514347,  22.092527,   0.061373,  55.445175,   0.646925}, //S
            {1.446071,   6.870609,   6.151801,   1.750347,   0.634168,   0.146773,   0.052357,   1.193165,  18.343416,  46.398396,   0.401005},
            {7.188004,   6.638454,   0.454180,   1.929593,   1.523654,   0.265954,   0.956221,  15.339877,  15.339862,  39.043823,   0.062409},
            {8.163991,   7.146945,   1.070140,   0.877316,   1.486434,   0.253614,  12.816323,   0.808945, 210.327011,  39.597652,   0.052821},
            {8.593655,   1.477324,   1.436254,   1.182839,   7.113258,   0.196255,  10.460644,   0.041891,  81.390381, 169.847839,   0.688098},
            {1.476566,   1.487278,   1.600187,   9.177463,   7.099750,   0.157765,  53.131023,   0.035325, 137.319489,   9.098031,   0.602102},
            {9.818524,   1.522646,   1.703101,   1.768774,   7.082555,   0.102473,   8.001879,   0.029763,  39.885422, 120.157997,   0.532405},
            {10.473575,   1.547881,   1.986381,   1.865616,   7.056250,   0.067744,   7.081940,   0.026040,  31.909672, 108.022842,   0.474882},
            {11.007069,   1.555477,   2.985293,   1.347855,   7.034779,   0.065510,   6.366281,   0.023987,  23.244839, 105.774498,   0.429369},
            {11.709542,   1.733414,   2.673141,   2.023368,   7.003180,  -0.147293,   5.597120,   0.017800,  21.788420,  89.517914,   0.383054},
            {12.311098,   1.876623,   3.066177,   2.070451,   6.975185,  -0.304931,   5.009415,   0.014461,  18.743040,  82.767876,   0.346506},
            {12.914510,   2.481908,   3.466894,   2.106351,   6.960892,  -0.936572,   4.507138,   0.009126,  16.438129,  76.987320,   0.314418},
            {13.521865,   6.947285,   3.866028,   2.135900,   4.284731,  -2.762697,   4.077277,   0.286763,  14.622634,  71.966080,   0.004437},
            {14.014192,   4.784577,   5.056806,   1.457971,   6.932996,  -3.254477,   3.738280,   0.003744,  13.034982,  72.554794,   0.265666},
            {14.741002,   6.907748,   4.642337,   2.191766,  38.424042, -36.915829,   3.388232,   0.243315,  11.903689,  63.312130,   0.000397},
            {15.758946,   6.841123,   4.121016,   2.714681,   2.395246,  -0.847395,   3.121754,   0.226057,  12.482196,  66.203621,   0.007238},
            {16.540613,   1.567900,   3.727829,   3.345098,   6.785079,   0.018726,   2.866618,   0.012198,  13.432163,  58.866047,   0.210974},
            {17.025642,   4.503441,   3.715904,   3.937200,   6.790175,  -2.984117,   2.597739,   0.003012,  14.272119,  50.437996,   0.193015},
            {17.354071,   4.653248,   4.259489,   4.136455,   6.749163,  -3.160982,   2.349787,   0.002550,  15.579460,  45.181202,   0.177432},
            {17.550570,   5.411882,   3.937180,   3.880645,   6.707793,  -2.492088,   2.119226,  16.557184,   0.002481,  42.164009,   0.162121},
            {17.655279,   6.848105,   4.171004,   3.446760,   6.685200,  -2.810592,   1.908231,  16.606236,   0.001598,  39.917473,   0.146896},
            {8.123134,   2.138042,   6.761702,   1.156051,  17.679546,   1.139548,  15.142385,  33.542667,   0.129372, 224.132507,   1.713368},
            {17.730219,   9.795867,   6.099763,   2.620025,   0.600053,   1.140251,   1.563060,  14.310868,   0.120574, 135.771317,   0.120574},
            {17.792040,  10.253252,   5.714949,   3.170516,   0.918251,   1.131787,   1.429691,  13.132816,   0.112173, 108.197029,   0.112173},
            {17.859772,  10.911038,   5.821115,   3.512513,   0.746965,   1.124859,   1.310692,  12.319285,   0.104353,  91.777542,   0.104353},
            {17.958399,  12.063054,   5.007015,   3.287667,   1.531019,   1.123452,   1.211590,  12.246687,   0.098615,  75.011948,   0.098615},
            {6.236218,  17.987711,  12.973127,   3.451426,   0.210899,   1.108770,   0.090780,   1.108310,  11.468720,  66.684151,   0.090780},
            {17.840963,   3.428236,   1.373012,  12.947364,   6.335469,   1.074784,   1.005729,  41.901382, 119.320541,   9.781542,   0.083391},
            {6.271624,  17.906738,  14.123269,   3.746008,   0.908235,   1.043992,   0.077040,   0.928222,   9.555345,  35.860680, 123.552246},
            {6.216648,  17.919739,   3.854252,   0.840326,  15.173498,   0.995452,   0.070789,   0.856121,  33.889484, 121.686691,   9.029517},
            {6.121511,   4.784063,  16.631683,   4.318258,  13.246773,   0.883099,   0.062549,   0.784031,   8.751391,  34.489983,   0.784031},
            {6.073874,  17.155437,   4.173344,   0.852238,  17.988686,   0.756603,   0.055333,   7.896512,  28.443739, 110.376106,   0.716809},
            {6.080986,  18.019468,   4.018197,   1.303510,  17.974669,   0.603504,   0.048990,   7.273646,  29.119284,  95.831207,   0.661231},
            {6.196477,  18.816183,   4.050479,   1.638929,  17.962912,   0.333097,   0.042072,   6.695665,  31.009790, 103.284348,   0.610714},
            {19.325171,   6.281571,   4.498866,   1.856934,  17.917318,   0.119024,   6.118104,   0.036915,  32.529045,  95.037186,   0.565651},
            {5.394956,   6.549570,  19.650681,   1.827820,  17.867832,  -0.290506,  33.326523,   0.030974,   5.564929,  87.130966,   0.523992},
            {6.660302,   6.940756,  19.847015,   1.557175,  17.802427,  -0.806668,  33.031654,   0.025750,   5.065547,  84.101616,   0.487660},
            {19.884502,   6.736593,   8.110516,   1.170953,  17.548716,  -0.448811,   4.628591,   0.027754,  31.849096,  84.406387,   0.463550},
            {19.978920,  11.774945,   9.332182,   1.244749,  17.737501,  -6.065902,   4.143356,   0.010142,  28.796200,  75.280685,   0.413616},
            {17.418674,   8.314444,  10.323193,   1.383834,  19.876251,  -2.322802,   0.399828,   0.016872,  25.605827, 233.339676,   3.826915},
            {19.747343,  17.368477,  10.465718,   2.592602,  11.003653,  -5.183497,   3.481823,   0.371224,  21.226641, 173.834274,   0.010719},
            {19.966019,  27.329655,  11.018425,   3.086696,  17.335455, -21.745489,   3.197408,   0.003446,  19.955492, 141.381973,   0.341817},
            {17.355122,  43.988499,  20.546650,   3.130670,  11.353665, -38.386017,   0.328369,   0.002047,   3.088196, 134.907654,  18.832960},
            {21.551311,  17.161730,  11.903859,   2.679103,   9.564197,  -3.871068,   2.995675,   0.312491,  17.716705, 152.192825,   0.010468},
            {17.331244,  62.783924,  12.160097,   2.663483,  22.239950, -57.189842,   0.300269,   0.001320,  17.026001, 148.748993,   2.910268},
            {17.286388,  51.560162,  12.478557,   2.675515,  22.960947, -45.973682,   0.286620,   0.001550,  16.223755, 143.984512,   2.796480},
            {23.700363,  23.072214,  12.777782,   2.684217,  17.204367, -17.452166,   2.689539,   0.003491,  15.495437, 139.862473,   0.274536},
            {17.186195,  37.156837,  13.103387,   2.707246,  24.419271, -31.586687,   0.261678,   0.001995,  14.787360, 134.816299,   2.581883},
            {24.898117,  17.104952,  13.222581,   3.266152,  48.995213, -43.505684,   2.435028,   0.246961,  13.996325, 110.863091,   0.001383},
            {25.910013,  32.344139,  13.765117,   2.751404,  17.064405, -26.851971,   2.373912,   0.002034,  13.481969, 125.836510,   0.236916},
            {26.671785,  88.687576,  14.065445,   2.768497,  17.067781, -83.279831,   2.282593,   0.000665,  12.920230, 121.937187,   0.225531},
            {27.150190,  16.999819,  14.059334,   3.386979,  46.546471, -41.165253,   2.169660,   0.215414,  12.213148, 100.506783,   0.001211},
            {28.174887,  82.493271,  14.624002,   2.802756,  17.018515, -77.135223,   2.120995,   0.000640,  11.915256, 114.529938,   0.207519},
            {28.925894,  76.173798,  14.904704,   2.814812,  16.998117, -70.839813,   2.046203,   0.000656,  11.465375, 111.411980,   0.199376},
            {29.676760,  65.624069,  15.160854,   2.830288,  16.997850, -60.313812,   1.977630,   0.000720,  11.044622, 108.139153,   0.192110},
            {30.122866,  15.099346,  56.314899,   3.540980,  16.943729, -51.049416,   1.883090,  10.342764,   0.000780,  89.559250,   0.183849},
            {30.617033,  15.145351,  54.933548,   4.096253,  16.896156, -49.719837,   1.795613,   9.934469,   0.000739,  76.189705,   0.175914},
            {31.066359,  15.341823,  49.278297,   4.577665,  16.828321, -44.119026,   1.708732,   9.618455,   0.000760,  66.346199,   0.168002},
            {31.507900,  15.682498,  37.960129,   4.885509,  16.792112, -32.864574,   1.629485,   9.446448,   0.000898,  59.980675,   0.160798},
            {31.888456,  16.117104,  42.390297,   5.211669,  16.767591, -37.412682,   1.549238,   9.233474,   0.000689,  54.516373,   0.152815},
            {32.210297,  16.678440,  48.559906,   5.455839,  16.735533, -43.677956,   1.473531,   9.049695,   0.000519,  50.210201,   0.145771},
            {32.004436,   1.975454,  17.070105,  15.939454,   5.990003,   4.018893,   1.353767,  81.014175,   0.128093,   7.661196,  26.659403},
            {31.273891,  18.445440,  17.063745,   5.555933,   1.575270,   4.050394,   1.316992,   8.797154,   0.124741,  40.177994,   1.316997},
            {16.777390,  19.317156,  32.979683,   5.595453,  10.576854,  -6.279078,   0.122737,   8.621570,   1.256902,  38.008820,   0.000601},
            {16.839890,  20.023823,  28.428564,   5.881564,   4.714706,   4.076478,   0.115905,   8.256927,   1.195250,  39.247227,   1.195250},
            {16.630795,  19.386616,  32.808571,   1.747191,   6.356862,   4.066939,   0.110704,   7.181401,   1.119730,  90.660263,  26.014978},
            {16.419567,  32.738590,   6.530247,   2.342742,  19.916475,   4.049824,   0.105499,   1.055049,  25.025890,  80.906593,   6.664449},
            {16.282274,  32.725136,   6.678302,   2.694750,  20.576559,   4.040914,   0.101180,   1.002287,  25.714146,  77.057549,   6.291882},
            {16.289164,  32.807171,  21.095163,   2.505901,   7.254589,   4.046556,   0.098121,   0.966265,   6.046622,  76.598068,  28.096128},
            {16.011461,  32.615547,   8.113899,   2.884082,  21.377867,   3.995684,   0.092639,   0.904416,  26.543257,  68.372963,   5.499512},
            {16.070229,  32.641106,  21.489658,   2.299218,   9.480184,   4.020977,   0.090437,   0.876409,   5.239687,  69.188477,  27.632641},
            {16.007385,  32.663830,  21.594351,   1.598497,  11.121192,   4.003472,   0.087031,   0.840187,   4.954467, 199.805801,  26.905106},
            {32.563690,  21.396671,  11.298093,   2.834688,  15.914965,   3.981773,   0.801980,   4.590666,  22.758972, 160.404388,   0.083544},
            {15.914053,  32.535042,  21.553976,  11.433394,   3.612409,   3.939212,   0.080511,   0.770669,   4.352206,  21.381622, 130.500748},
            {15.784024,  32.454899,  21.849222,   4.239077,  11.736191,   3.922533,   0.077067,   0.735137,   4.097976, 109.464111,  20.512138},
            {32.740208,  21.973675,  12.957398,   3.683832,  15.744058,   3.886066,   0.709545,   4.050881,  19.231543, 117.255005,   0.074040},
            {15.679275,  32.824306,  13.660459,   3.687261,  22.279434,   3.854444,   0.071206,   0.681177,  18.236156, 112.500038,   3.930325},
            {32.999901,  22.638077,  14.219973,   3.672950,  15.683245,   3.769391,   0.657086,   3.854918,  17.435474, 109.464485,   0.068033},
            {33.281178,  23.148544,  15.153755,   3.031492,  15.704215,   3.664200,   0.634999,   3.856168,  16.849735, 121.292038,   0.064857},
            {33.435162,  23.657259,  15.576339,   3.027023,  15.746100,   3.541160,   0.612785,   3.792942,  16.195778, 117.757004,   0.061755},
            {15.804837,  33.480801,  24.150198,   3.655563,  15.499866,   3.390840,   0.058619,   0.590160,   3.674720, 100.736191,  15.408296},
            {15.889072,  33.625286,  24.710381,   3.707139,  15.839268,   3.213169,   0.055503,   0.569571,   3.615472,  97.694786,  14.754303},
            {33.794075,  25.467693,  16.048487,   3.657525,  16.008982,   3.005326,   0.550447,   3.581973,  14.357388,  96.064972,   0.052450}, //98, Einsteinium
            {1.6607,      1.6277,      3.7734,    2.7903,     0,          0.1444,     0.3042,     5.1864,    12.7450,    30.7880,     0       }  //HOH, 99th element
    };

    public PDBFile(File selectedFile, double qmax, boolean exclude, String workingDirectoryName){
        filename = selectedFile.getName();
        FileInputStream fstream = null;
        this.qmax = qmax;
        delta_q = (qmax - qmin)/103.0;
        qmin -= delta_q;

        sizeOfSineTable = 97;
        sineTable = new double[sizeOfSineTable];

        this.useWaters = !exclude;
        icalc = new XYSeries(filename);

        this.currentWorkingDirectory = workingDirectoryName;

        atoms = new ArrayList<>();
        System.out.println("Reading PDB file : " + selectedFile.getName());
        if (selectedFile.length() == 0){
            try {
                throw new Exception("File PDB Empty");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            fstream = new FileInputStream(selectedFile);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            // need the lines that contain ATOM
            // arraylist of x, y, z

            try {
                while ((strLine = br.readLine()) != null) {

                    if (strLine.matches("^ATOM.*") || (strLine.matches("^HETATM.*HOH.*") ) ){
                        atoms.add(new PDBAtom(strLine));
                    }
                }
                totalAtomsRead = atoms.size();
                System.out.println("Total atoms read " + totalAtomsRead);
            } catch (IOException e) {
                e.printStackTrace();
            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        calculatePofR();
        this.convertPofRToIntensities();

        //this.centerCoordinates();
        //this.convertToBeadModel(delta_r*0.5);
    }


    private void calculatePofR() {

        if ((totalAtomsRead < 10)) {
            System.err.println("Specify Proper PDB file ");
        } else {
            // create working set
            int maxAtomicNumber = 0;
            atomicList = new ArrayList<Integer>();
            workingCoords = new ArrayList<>();
            for(int i=0;i<totalAtomsRead; i++){
                PDBAtom tempAtom = atoms.get(i);
                if (useWaters){
                    workingCoords.add(tempAtom);
                    int atomicNumber=tempAtom.getAtomicNumber();
                    if (!atomicList.contains(atomicNumber)){
                        atomicList.add(atomicNumber);
                        if (atomicNumber > maxAtomicNumber){
                            maxAtomicNumber =atomicNumber;
                        }
                    }
                } else {
                    if (!tempAtom.isWater()){
                        workingCoords.add(tempAtom);
                        int atomicNumber=tempAtom.getAtomicNumber();
                        if (!atomicList.contains(atomicNumber)){
                            atomicList.add(atomicNumber);
                            if (atomicNumber > maxAtomicNumber){
                                maxAtomicNumber =atomicNumber;
                            }
                        }
                    }
                }
            }
            asfs = new double[maxAtomicNumber+1];

            // determine dmax of pdb file
            totalAtoms = workingCoords.size();
            workingAtomicNumbers = new int[totalAtoms];

            dmax = dmaxFromPDB(workingCoords, totalAtoms);
            // calculate PofR from workingAtoms
            // use resolution to determine number of Shannon bins
            delta_r = Math.PI/qmax;

            int tbins = (int)Math.floor(dmax/delta_r); // if dmax is not within bins, add one more
            if (tbins*delta_r < dmax){
                tbins++;
            }
            pr_bins = tbins;
            //pr_bins = (int)Math.ceil((dmax / delta_r));
//            pr_bins = (int)Math.ceil(qmax*dmax/Math.PI) + 1;
//            delta_r = dmax/(double)pr_bins;
            System.out.println("QMAX : " + qmax + " del_r => " + delta_r);

            double ns_dmax = pr_bins*delta_r; // dmax corresponding to number of Shannon Bins
            System.out.println("DMAX : " + dmax + " NS DMAX : " + ns_dmax);

            //long total = ((long)totalAtoms*(totalAtoms-1))/2;
            //distances = new double[ (totalAtoms*(totalAtoms-1))/2 ] ;
            // should bin based on ns_dmax
            int high_res_pr_bins = (int)Math.ceil((double)dmax/highresWidth);

            double inv_delta = 1.0/delta_r;
//            double radius = delta_r*0.5;
            // implies dmax/high_res_bin_width
            // int totalBinsHiRes = (int)Math.ceil(dmax/high_res_pr_bins);
            double inv_high_res_delta = 1.0/highresWidth;

            int[] histo = new int[pr_bins];
            int[] highResHisto = new int[high_res_pr_bins];

            double[] atom, atom2;
            double refx, refy, refz, difx, dify, difz, distance;
            int startIndex, bin;
            double dmax_temp=0;
            double twoPI = 2.0*Math.PI;

            rmin = dmax;
//            Random rand = new Random();
            PDBAtom pdbatom1, pdbatom2;
            int distanceCount =0;
            for (int i = 0; i < totalAtoms; i++) { // n*(n-1)/2 distances
                pdbatom1 = workingCoords.get(i);
                workingAtomicNumbers[i] = pdbatom1.getAtomicNumber();
                atom = pdbatom1.getCoords(); // use different weights for atom type here
                refx = atom[0];
                refy = atom[1];
                refz = atom[2];

                int atomicNumber1 = pdbatom1.getAtomicNumber();
                startIndex = i + 1;

                while (startIndex < totalAtoms) {
                    atom2 = workingCoords.get(startIndex).getCoords();  // use different weights for atom type here
                    difx = refx - atom2[0];
                    dify = refy - atom2[1];
                    difz = refz - atom2[2];

                    distance = FastMath.sqrt(difx * difx + dify * dify + difz * difz);
                    if (distance < rmin){
                        rmin = distance;
                    }
                    // lower < value <= upper
//                    distances[distanceCount] = (float)distance;
                    distanceCount++;
                    double ratio = distance*inv_delta;
                    double floor = Math.floor(ratio);
                    if ((ratio-floor) <= Math.ulp(floor)){
                        bin = (int)(floor) - 1;
                    } else {
                        bin = (int) Math.floor(distance * inv_delta); // casting to int is equivalent to floor
                    }
                    //histo[(bin >= pr_bins) ? bin - 1 : bin] += 1;
                    histo[bin] += 1;

                    // which bin high res?
                    bin = (int) Math.floor(distance * inv_high_res_delta); // casting to int is equivalent to floor
                    //highResHisto[(bin >= high_res_pr_bins) ? bin - 1 : bin] += 1;
                    highResHisto[bin] += 1;
                    startIndex++;
                    if (distance > dmax_temp){
                        dmax_temp = distance;
                    }
                }
            }

            System.out.println(String.format("FINISHED DISTANCES => dmax_temp : %.3f", dmax_temp));
            ArrayList<String> output = new ArrayList();
            output.add(String.format("REMARK 265                          DMAX : %d (Angstroms)%n", dmax));
            output.add(String.format("REMARK 265   RESOLUTION LIMIT       QMAX : %.6f (Angstroms^-1)%n", qmax));
            output.add(String.format("REMARK 265   RESOLUTION LIMIT  BIN WIDTH : %.2f (Angstroms)%n", delta_r));
            output.add(String.format("REMARK 265 %n"));
            output.add(String.format("REMARK 265           BIN WIDTH (delta r) : %.4f %n", delta_r));
            output.add(String.format("REMARK 265 %n"));
            output.add(String.format("REMARK 265  BIN COEFFICIENTS (UNSCALED) %n"));
            output.add(String.format("REMARK 265 %n"));
            output.add(String.format("REMARK 265      CONSTANT BACKGROUND EXCLUDED FROM FIT %n"));
            output.add(String.format("REMARK 265      CONSTANT BACKGROUND m(0) : 0.000E+00 %n"));
            output.add(String.format("REMARK 265 %n"));
            output.add(String.format("REMARK 265 P(R) DISTRIBUTION BINNED USING SHANNON NUMBER %n"));
            output.add(String.format("REMARK 265 R-values REPRESENT THE MID-POINT OF EACH BIN %n"));
            output.add(String.format("REMARK 265 BIN HEIGHT REPRESENTS THE VALUE OF P(R-value) %n"));
            output.add(String.format("REMARK 265        BIN            R-value : BIN HEIGHT %n"));
            output.add(String.format("REMARK 265 %n"));

            // prepare final distributions
            pdbdata = new XYSeries(filename);
            high_res_pr_data = new XYSeries(filename);
            pdbdata.add(0,0); // [r = 0 => P(0) = 0]

            totalCounts = 0;
            double temparea = 0;
            for (int i = 0; i < pr_bins; i++) {
                pdbdata.add((i + 0.5) * delta_r, histo[i]);  // middle position of the histogram bin
                int index = i+1;
                output.add(String.format("REMARK 265       BIN_%-2d          %9.3f : %d %n", index, ((i + 0.5) * delta_r), histo[i]));
                temparea += delta_r*histo[i];
                totalCounts += histo[i];
            }

            output.add(String.format("REMARK 265 %n"));
            output.add(String.format("REMARK 265            TOTAL SHANNON BINS : %d %n", pr_bins));

            pdbdata.add(ns_dmax, 0);
            //pdbdata.add(dmax, 0); // [r=dmax => P(dmax)=0]

            // fill high resolution bins
            //System.out.println("PRINTING HIGH RESOLUTION PR DISTRIBUTION");
            // System.out.println(String.format("%5.2f 0 0", 0.0));
            high_res_pr_data.add(0, 0);
            for (int i = 0; i < high_res_pr_bins; i++) {
                high_res_pr_data.add((i + 0.5) * highresWidth, highResHisto[i]);  // middle position of the histogram bin
                XYDataItem tempItem = high_res_pr_data.getDataItem(i+1);
                //    System.out.println(String.format("%5.2f %.5E 0", tempItem.getXValue(), tempItem.getYValue()));
            }

            high_res_pr_data.add(highresWidth*high_res_pr_bins, 0);
            //System.out.println(String.format("%5.2f 0 0", highresWidth*high_res_pr_bins));
            //System.out.println("END");

            int all = pdbdata.getItemCount();
            // r, P(r)
            izero = Functions.trapezoid_integrate(pdbdata);
            izero = temparea; // should equal number of electrons squared

            double invarea = 1.0 / izero, rvalue;
            XYSeries secondMoment = new XYSeries("second");
            for (int i = 0; i < all; i++) {
                XYDataItem tempItem = pdbdata.getDataItem(i);
                rvalue = tempItem.getXValue();
                secondMoment.add(rvalue, rvalue*rvalue*tempItem.getYValue());  // middle position of the histogram bin
            }

            rg = Math.sqrt(0.5*Functions.trapezoid_integrate(secondMoment)*invarea);
            // normalize Pr distribution so area is equal to one
            for (int i = 0; i < pdbdata.getItemCount(); i++) {
                pdbdata.updateByIndex(i, pdbdata.getY(i).doubleValue() / totalCounts);
            }

//            System.out.println("POFR POINTS FROM PDB MODEL (UNSCALED) : " + all + " > " + pr_bins);
//            for (int i = 0; i < all; i++) {
//                System.out.println(String.format("%5.2f %.5E", pdbdata.getX(i).doubleValue(), pdbdata.getY(i).doubleValue()));
//            }
//            System.out.println("END OF POFR POINTS");
            // calculateMooreCoefficients(pr_bins, ns_dmax);
            // calcualte p(r) distribution to the specified resolution
            String nameBin =  String.format("prbins_%.2f", qmax);
            writeToFile(output, nameBin, "txt");
        }
        System.out.println("FINISHED Distances--");
    }

    /**
     * convert to I(q) using Integral of P(r) * sin(qr)/qr dr
     */
    private void convertPofRToIntensities(){
        double q_at = qmin, qr;
        double sum;
        //int totalr = pdbdata.getItemCount();
        int totalr = high_res_pr_data.getItemCount();
        //double constant = 0.5*dmax/(totalr-1); // first bin is just 0,0
        double constant = delta_r;
        XYDataItem tempItem;

        long start_time = System.nanoTime();
        while (q_at < qmax){  // integrate using trapezoid rule
            q_at += delta_q;
            // for a given q, integrate Debye function from r=0 to r=dmax
            // first element of pdbdata is r=0 and last is r=dmax;
            // r is defined as integer multiples of Math.PI/qmax;
            sum = 0;
            for(int i=1; i<totalr; i++){
//                tempItem = pdbdata.getDataItem(i); // [r-value, bin-height]
                tempItem = high_res_pr_data.getDataItem(i);
                qr = tempItem.getXValue()*q_at;
                sum += ( tempItem.getYValue()* FastMath.sin(qr)/qr);
            }

//            double qradius = q_at*delta_r*0.5; // q*sphere_radius
//            double fq = 3.0/qradius*(FastMath.sin(qradius)/(qradius*qradius) - FastMath.cos(qradius)/qradius);
//            icalc.add(q_at, fq*fq*((totalr-1) + 2*sum));
//            icalc.add(q_at, ((totalr-1) + 2*sum));
            icalc.add(q_at, sum);
//            icalc.add(q_at, debye(q_at));

        }
        System.out.println("TIME : " + (System.nanoTime()-start_time));
        //System.out.println("END");

        int totalcalc = icalc.getItemCount();
        error = new XYSeries("error for " + filename);
//        Random rand = new Random();

        for(int i=0; i<totalcalc; i++){
            XYDataItem temp = icalc.getDataItem(i);
            double value = simulateIoverSigmaNoise(temp.getXValue(), temp.getYValue());
            double noise = temp.getYValue()/value;
            error.add(temp.getX(), noise);
            //icalc.updateByIndex(i, rand.nextGaussian()*noise + temp.getYValue());
        }
        //System.out.println("Intensities Calculated from PDB");
    }

    /**
     * Given a collection of atoms return largest dimension
     * @param randomAtoms ArrayList<double[3]>
     * @param total total number of atoms in randomAtoms
     * @return
     */
    private int dmaxFromPDB(ArrayList<PDBAtom> randomAtoms, int total) {

        Point3d[] points = new Point3d[total];
        double[] atom;

        for(int i=0; i<total; i++){
            atom = randomAtoms.get(i).getCoords();
            points[i] = new Point3d(atom[0], atom[1], atom[2]);
        }

        QuickHull3D hull = new QuickHull3D();
        hull.build(points);
        Point3d[] vertices = hull.getVertices();
        int totalVertices = vertices.length;

        int startIndex;
        double max=0, distance, refx, refy, refz, difx, dify, difz;

        for (int i=0; i<totalVertices; i++){
            Point3d pnt = vertices[i];
            // calculate dMax
            refx = pnt.x;
            refy = pnt.y;
            refz = pnt.z;
            startIndex=(i+1);

            while(startIndex<totalVertices){
                Point3d pnt2 = vertices[startIndex];
                difx = refx - pnt2.x;
                dify = refy - pnt2.y;
                difz = refz - pnt2.z;

                distance = difx*difx + dify*dify + difz*difz;

                if (distance > max){
                    max = distance;
                }
                startIndex++;
            }
            //System.out.printf("%-6s%5d %4s %3s %1s%4d    %8.3f%8.3f%8.3f\n", "ATOM", (i+1), "CA ", "GLY", "A",(i+1),pnt.x, pnt.y, pnt.z);
        }

        return (int)Math.ceil(Math.sqrt(max));
    }

    public XYSeries getIcalc(){
        return icalc;
    }

    public XYSeries getError(){
        return error;
    }

    public XYSeries getPrDistribution(){
        return pdbdata;
    }

    public double getDmax(){
        return dmax;
    }

    public double getRg(){
        return rg;
    }

    public double getIzero(){
        return izero;
    }


    public void centerCoordinates(){
        ArrayList<PDBAtom> workingCoords = new ArrayList<>();

        for(int i=0;i<totalAtomsRead; i++){
            PDBAtom tempAtom = atoms.get(i);
            if (useWaters){
                workingCoords.add(tempAtom);
            } else {
                if (!tempAtom.isWater()){
                    workingCoords.add(tempAtom);
                }
            }
        }

        int totalWorkingAtoms = workingCoords.size();
        double aveX=0, aveY=0, aveZ=0;

        for(int i=0;i<totalWorkingAtoms; i++){
            double[] coords = workingCoords.get(i).getCoords();
            aveX+= coords[0];
            aveY+= coords[1];
            aveZ+= coords[2];
        }

        double invTotal = 1.0/(double)totalWorkingAtoms;
        aveX *= invTotal;
        aveY *= invTotal;
        aveZ *= invTotal;

        // center coordinates
        centeredAtoms = new ArrayList<>();
        for(int i=0; i<totalWorkingAtoms; i++){
            centeredAtoms.add(new PDBAtom(workingCoords.get(i)));
            double[] coords = centeredAtoms.get(i).getCoords();
            coords[0] -= aveX;
            coords[1] -= aveY;
            coords[2] -= aveZ;
            centeredAtoms.get(i).setCoords(coords);
        }

        // write out centered coordinates
        ArrayList<String > lines = new ArrayList<>();
        for(int i=0; i<totalWorkingAtoms; i++){
            lines.add(centeredAtoms.get(i).getPDBline());
        }
        lines.add(String.format("END %n"));
        writeToFile(lines, "centeredPDB", "pdb");
    }



    //make HCP lattice
    public void convertToBeadModel(double bead_radius){
        System.out.println("CONVERTING TO BEAD MODEL");
        double volume = 4.0/3.0*Math.PI*bead_radius*bead_radius*bead_radius;
        double radius = dmax*0.5;
        double squaredBeadRadius = 4*bead_radius*bead_radius;
        double limit = radius + radius*0.23;
        double inv_bead_radius = 1.0/bead_radius;
        double invsqrt6 = 1.0/Math.sqrt(6), inv3 = 1.0/3.0d, sqrt6 = Math.sqrt(6), sqrt3=Math.sqrt(3);

        int klimit = (int) (limit*inv_bead_radius*3.0/2.0*invsqrt6);
        int count=0;

        double distance;
        //float * pconvertXYZ = NULL;
        // positive first
        ArrayList<Bead> beads = new ArrayList<>();

        for (int k=-klimit; k<=klimit; k++){
            // for each k index over i and j
            double dz = 2.0*inv3*sqrt6*k;

            if (dz*bead_radius > limit){
                break;
            }

            double inv3kmod2 = inv3*(k%2);

            for(int j=-klimit; j<=klimit; j++){
                double dy = sqrt3*(j + inv3kmod2);

                if (dy*bead_radius <= limit){
                    float jkmod2 = (j+k)%2;

                    for(int i=-klimit; i<=klimit; i++){
                        // compute distance from center
                        double dx = 2*i + jkmod2;

                        distance = bead_radius*Math.sqrt(dx*dx + dy*dy + dz*dz);

                        if (distance <= limit){
                            // add bead to vector
                            beads.add(new Bead(count, dx*bead_radius, dy*bead_radius, dz*bead_radius, bead_radius));
                            count++;
                        }
                    } // end of i loop
                }
            } // end of j loop
        } // end of k loop

        // make overlapping model
        int totalAtoms = centeredAtoms.size();
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<Bead> keepers = new ArrayList<>();

        int totalBeads = beads.size();
        int swapIndex = totalBeads -1;
        count=1;
        for(int atom=0; atom<totalAtoms; atom++){
            PDBAtom temp = centeredAtoms.get(atom);
            // if atom is within radius of a bead,keep bead
            //int totalBeads = beads.size();
            //ArrayList<Bead> removeThese = new ArrayList<>();
            findLoop: // find all beads within bead_radius of atom
            for(int b=0; b<totalBeads; b++){
                Bead tempbead = beads.get(b);
                if (tempbead.getSquaredDistance(temp.getCoords()) <= squaredBeadRadius){
                    lines.add(tempbead.getPDBLine(count));
                    keepers.add(tempbead);
                    //beads.remove(b);
                    //removeThese.add(tempbead);
                    count++;
                    //break findLoop;
                    Collections.swap(beads, b, swapIndex);
                    swapIndex--;
                    totalBeads--;
                }
            }
            //beads.removeAll(removeThese);
        }

        totalAtoms = keepers.size();
        // calculate dmax
        double max = 0;
        for(int i=0; i<totalAtoms; i++){
            int next = i+1;
            Bead temp1 = keepers.get(i);
            for(; next<totalAtoms; next++){
                Bead temp2 = keepers.get(next);
                double dis = temp1.getSquaredDistance(temp2);
                if (dis > max){
                    max = dis;
                }
            }
        }
        max = Math.sqrt(max);
        System.out.println("FINISHED BEAD MODEL");

        ArrayList<String> output = new ArrayList();
        output.add(String.format("REMARK 265            BEAD RADIUS : %.1f %n", bead_radius));
        output.add(String.format("REMARK 265            BEAD VOLUME : %.1f %n", volume));
        output.add(String.format("REMARK 265           TOTAL VOLUME : %.1f %n", volume*lines.size()));
        output.add(String.format("REMARK 265  DMAX CENTER-TO-CENTER : %.1f %n", max));
        output.add(String.format("REMARK 265      DMAX EDGE-TO-EDGE : %.1f %n", max+2*bead_radius));
        output.addAll(lines);
        output.add(String.format("END %n"));
        writeToFile(output, "bead_model_"+String.format("%.2f", bead_radius), "pdb");
    }

    public void writeToFile(ArrayList<String> lines, String name, String ext){

        try {
            // Create file
            FileWriter fstream = new FileWriter(currentWorkingDirectory + "/"+name+"."+ext);
            BufferedWriter out = new BufferedWriter(fstream);
            int total = lines.size();
            for(int i=0; i<total; i++){
                out.write(lines.get(i));
            }
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }


    /**
     * Using the histogram, calculate the Moore Coefficients to the Shannon Limit
     * @param totalShannonPoints
     */
    private void calculateMooreCoefficients(int totalShannonPoints, double ns_dmax){
        // pr_bins - does not include 0 and dmax
        double invDmax = Math.PI/dmax, sum, qr, rvalue;
        double invConstant = 0.5/dmax;

        int totalBins = high_res_pr_data.getItemCount();
        mooreCoefficients = new double[totalShannonPoints];  // estimate Moore Coefficients from resolution of the P(r)distribution
        //double constant = 0.5/(double)(totalBins-2)*dmax;
        for(int i=0; i < totalShannonPoints; i++){
            // set the cardinal point
            double qvalue = (i+1)*invDmax; // Shannon points are fixed, so to truncate resolution, just truncate Moore function to requisite resolution
            sum = 0;
            // for a given q, integrate Debye function from r=0 to r=dmax
            // skip first one since it is r=0, area of histogram (binned) data
            for(int j=1; j<(totalBins-1); j++){ // exclude end points where P(r) = 0

                XYDataItem tempItem = high_res_pr_data.getDataItem(j);
                //rvalue = highresWidth*((j-1)+0.5);  // using midpoint ensures Moore coefficients are all positive
                rvalue = highresWidth*j; // get proper distribution but it is shift by a few Angstroms, like 1.8
                //rvalue = tempItem.getXValue();
                //rvalue = highresWidth*(j-1); // at far edge

                qr = rvalue*qvalue;

                if (rvalue == 0){
                    sum += tempItem.getYValue(); // sinc(0) = 1
                } else { // highres is limited to dmax of the molecule
                    sum+=tempItem.getYValue()*Math.sin(qr)/qr;
                }

                //sum += tempItem.getYValue()*Math.sin(qr)/rvalue;
                //sum += highresWidth*tempItem.getYValue()*Math.sin(qr)/rvalue;
            }

            //mooreCoefficients[i] = qvalue*sum/(double)(i+1)*Math.pow(-1, i+2);
            mooreCoefficients[i] = qvalue*sum;
            System.out.println(i + " Moore " + mooreCoefficients[i] + " " + qvalue + " " + sum);
        }

        System.out.println("SYNTHETIC MOORE");
        // excludes background term
        // compare to low-resolution approximation
        // calculate P(r) using Moore Coefficients at r-values in pdbdata
        double resultM, r_value, pi_dmax_r, inv_2d = 0.5/dmax, r_value_moore;
        for (int j=1; j < pdbdata.getItemCount()-1; j++){

            r_value = pdbdata.getX(j).doubleValue();
            r_value_moore = r_value;
            //r_value_moore = delta_r*j; // delta_r based on lower resolution set by pdbdata => ns_dmax
            //pi_dmax_r = invDmax*r_value;

            //if (r_value_moore > dmax){
            //    r_value_moore = dmax;
            //}

            //pi_dmax_r = Math.PI/dmax*r_value_moore; // have to calculate it at the bin_width
            pi_dmax_r = Math.PI/ns_dmax*r_value_moore; // have to calculate it at the bin_width
            resultM = 0;

            for(int m=0; m < totalShannonPoints; m++){
                resultM += mooreCoefficients[m]*Math.sin(pi_dmax_r*(m+1));
            }

            double value = integrateMoore(delta_r*(j-1), delta_r*j); // equilvalent to midpoint
            //System.out.println(r_value + " " + value + " " + pdbdata.getY(j));
            //System.out.println(r_value + " " + (inv_2d * r_value* resultM) + " " + pdbdata.getY(j));
            //System.out.println(r_value + " " + value + " " + " " + pdbdata.getY(j));
            // calculation of the Moore at midpoint of bin is equivalent to the integration between lower and upper
            System.out.println(r_value + " " + value + " " + r_value_moore + " " + (inv_2d * r_value_moore* resultM) + " "  + " " + pdbdata.getY(j));
        }

        System.out.println(dmax + " " + 0.0 + " " + 0.0);

        String newLine = "REMARK 265 \n";
        newLine += "REMARK 265  MOORE COEFFICIENTS (UNSCALED)\n";
        newLine += String.format("REMARK 265      CONSTANT BACKGROUND m(0) : %.3E %n", 0.0);
        for (int i=0; i< totalShannonPoints;i++){
            newLine += String.format("REMARK 265                        m_(%2d) : %.3E %n", (i+1), mooreCoefficients[i]);
        }
        System.out.println(newLine);
    }


    private double integrateMoore(double lower, double upper){

        double lowerSum=0, upperSum=0, inva;
        for(int i=0; i< mooreCoefficients.length; i++){
            int n = i + 1;
            inva = dmax/(Math.PI*n);
            // integrate between lower and upper
            lowerSum+= mooreCoefficients[i]*(-inva*(lower*Math.cos(lower*n*Math.PI/dmax) - inva*Math.sin(lower*n*Math.PI/dmax)));
            upperSum+= mooreCoefficients[i]*(-inva*(upper*Math.cos(upper*n*Math.PI/dmax) - inva*Math.sin(upper*n*Math.PI/dmax)));
        }
        return (upperSum - lowerSum);
    }


    /**
     * returns atomic scattering form facgtor at a q-value (4*PI*sin(theta/lambda)
     * @param q
     * @param atomicNumber
     * @return
     */
    private double asf(double q, int atomicNumber){

        double[] atom = asf_coeffs[atomicNumber];
        double qval = q/4.0d/Math.PI;
        double q_squared = qval*qval;

        return atom[0]*FastMath.exp(-atom[6]*q_squared) + atom[1]*FastMath.exp(-atom[7]*q_squared) + atom[2]*FastMath.exp(-atom[8]*q_squared) + atom[3]*FastMath.exp(-atom[9]*q_squared) + atom[4]*FastMath.exp(-atom[10]*q_squared) + atom[5];
    }



    /**
     * Debye formula, distances must be precalculated.
     * Atomic Scattering form factors are in use.
     *
     * @param qvalue
     * @return
     */
    private double debye(double qvalue){

        double distance;
        //populate asfs
        Arrays.fill(asfs, 0.0d);
        for(int i : atomicList){
            if (i > 0){
                asfs[i] = asf(qvalue, i);
            }
        }

        double intensity_sum = 0;
        double tempBW = dmax/(2*pr_bins);
        double bin;

        makeSinTable(qvalue);

        for (int i = 0; i < totalAtoms; i++) { // n*(n-1)/2 distances
            int row2 = i*totalAtoms - (i*(i+1)/2) - i;
            int atomicNumber1 = workingAtomicNumbers[i];

            double asf1 = asfs[atomicNumber1];
            double subSum=0;
            intensity_sum += asf1*asf1;
            //int col = i + 1;
            for(int col = i+1; col<totalAtoms; col++){
                distance = distances[row2 + col - 1];
                double qr = qvalue*distance;
                subSum += asfs[workingAtomicNumbers[col]]*getSine(qr)/qr;
            }

            intensity_sum += 2*asf1*subSum;
        }

        return intensity_sum;
    }


    /**
     * returns expect I over sigma for given q-value assuming sigmoidal relationship
     * halfway point is 0.1
     * slope of transition is 5.1
     *
     * @param qvalue
     * @param intensity
     * @return
     */
    private double simulateIoverSigmaNoise(double qvalue, double intensity){
        double top = 140;
        double bottom = 0.9;

        return (top + (bottom-top)/(1.0d+Math.pow(0.1d/qvalue, 5.1d)));
    }


    private void makeSinTable(double qvalue){
        qrmin = qvalue*rmin;
        double max = qvalue*dmax;
        qrIncrement = (max-qrmin)/(double)(sizeOfSineTable-1);

        invQRIncrement = 1.0/qrIncrement;

        double startqr = qrmin;
        for(int i=0; i<sizeOfSineTable; i++){
            sineTable[i] = FastMath.sin(startqr);
            startqr += qrIncrement;
        }
    }

    private double getSine(double value){
        int index = (int)Math.floor(value*invQRIncrement);

        if ((index+1) >= sizeOfSineTable){
            return FastMath.sin(value);
        } else {
            double before = sineTable[index];
            double after = sineTable[index+1];
            return (before + (after-before)*invQRIncrement*(value - qrmin - index*qrIncrement));
        }

    }

}

