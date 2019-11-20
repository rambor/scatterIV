package version4.InverseTransform;

import version4.RealSpace;


public class IFTObject implements Runnable {

    private double qmax, dmax, lambda=0.001;

    private boolean useMoore;
    private boolean useDirectFT = false;
    private boolean useLegendre = false;
    private boolean useL2 = false;
    private boolean useSVD = false;
    private RealSpace dataset;
    private int cBoxValue=2;
    private boolean includeBackground = false;
    private boolean positiveOnly;

    public IFTObject(
            RealSpace dataset,
            double lambda,
            boolean useMoore,       // Moore function
            int cBoxValue,
            boolean useDirectFt, // L1-norm
            boolean useLegendre, // Legendre
            boolean useL2,       // L2-norm second derivative
            boolean useSVD,      // SVD
            boolean includeBackground,
            boolean positiveOnly){

        // create real space object for each dataset in use
        // XYSeries data, double dmax, double lambda, double qmax
        this.dataset = dataset;
        this.qmax = dataset.getLogData().getMaxX();
        dataset.setQmax(this.qmax); // need to specify qmax used in IFT determination
        this.dmax = dataset.getDmax();
        this.lambda = lambda;
        this.useMoore = useMoore;

        // after mouse click on spinner, this constructor is made => do we do standardization within constructor?
        this.cBoxValue = cBoxValue;
        this.useDirectFT = useDirectFt;      // default is true for Scatter => rambo method
        this.useLegendre = useLegendre;
        this.useL2 = useL2;
        this.useSVD = useSVD;
        this.includeBackground = includeBackground; //
        this.positiveOnly = positiveOnly;
    }

    @Override
    public void run() {
        IndirectFT tempIFT;

        if (useMoore){
            tempIFT = new MooreTransformApache(dataset.getfittedqIq(), dataset.getfittedError(), dmax, qmax, lambda, includeBackground);
            //tempIFT = new MooreTransform(dataset.getfittedqIq(), dataset.getfittedError(), dmax, qmax, lambda, useMoore, includeBackground);

        } else if (useDirectFT && !useLegendre && !useL2 && !useSVD){
            // L1-norm
            tempIFT = new SineIntegralTransform(dataset.getfittedqIq(), dataset.getfittedError(), dmax, qmax, lambda, includeBackground, positiveOnly);

        } else if (useLegendre && !useL2 && !useSVD) {

            // orthonal basis set with
            tempIFT = new LegendreTransform(
                    dataset.getfittedqIq(),
                    dataset.getfittedError(),
                    dmax,
                    qmax,
                    lambda,
                    includeBackground);

        } else if (useL2 && !useSVD) {  // use Laguerre

            tempIFT = new DirectSineIntegralTransform(dataset.getfittedqIq(),
                    dataset.getfittedError(),
                    dmax,
                    qmax,
                    lambda,
                    includeBackground);

            //tempIFT = new LaguerreTransform(rave , rg, dataset.getfittedqIq(), dataset.getfittedError(), dmax, qmax, lambda, 1);

        } else if (useSVD) {  // use Laguerre

            tempIFT = new SVD(dataset.getfittedqIq(),
                    dataset.getfittedError(),
                    dmax,
                    qmax,
                    includeBackground);

        } else  {  // use Moore Method
            tempIFT = new MooreTransformApache(dataset.getfittedqIq(), dataset.getfittedError(), dmax, qmax, lambda, includeBackground);
            //tempIFT = new MooreTransform(dataset.getfittedqIq(), dataset.getfittedError(), dmax, qmax, lambda, useMoore, includeBackground);

        }

        this.dataset.setStandardizationMean(tempIFT.getStandardizedLocation(), tempIFT.getStandardizedScale());
        this.dataset.setPrDistribution(tempIFT.getPrDistribution());
        this.dataset.setIndirectFTModel(tempIFT);
    }

}
