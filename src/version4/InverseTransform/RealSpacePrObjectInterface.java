package version4.InverseTransform;

import org.jfree.data.xy.XYSeries;

interface RealSpacePrObjectInterface {

    XYSeries getPrDistribution();
    XYSeries getIqcalc();
    XYSeries getqIqCalc();
    double getRg();
    double getRAverage();
    int getTotalInDistribution();
    double getRgError();
    double getIZero();
    double getIZeroError();
    double getStandardizedLocation();
    double getStandardizedScale();
    double calculateQIQ(double qvalue);

    double calculateIQ(double qvalue);
    double[] getCoefficients();
    double calculatePofRAtR(double r_value, double scale);
    double getChiEstimate();
    double getKurtosisEstimate(int rounds);
    void estimateErrors(XYSeries fittedqIq);
    void setNonStandardizedData(XYSeries nonStandardizedData);
    void createNonStandardizedData();
    double calculateMedianResidual(XYSeries series);
    double calculateChiFromDataset(XYSeries series, XYSeries error);

    double extrapolateToIofQ(double qvalue);

    void normalizeDistribution();

    String getHeader(double scale);
    double getArea();
    int getTotalFittedCoefficients();

    String getModelUsed();
}
