package version4.BinaryComparison;

import org.jfree.data.xy.XYSeries;

abstract class BinaryComparisonModel {

    public double location, scale;
    public XYSeries testSeries, modelData;
    public double qmax, qmin;
    private XYSeries reference;
    private XYSeries targetSeries;
    private XYSeries targetError;
    public int refIndex, tarIndex, order;

    public BinaryComparisonModel(XYSeries referenceSet, XYSeries targetSet, XYSeries targetError, Number qmin, Number qmax, String type, int refIndex, int tarIndex, int order){

        this.reference = referenceSet;
        this.targetSeries = targetSet;
        this.targetError = targetError;
        this.qmin = qmin.doubleValue();
        this.qmax = qmax.doubleValue();
        this.testSeries = new XYSeries(type);
        this.refIndex = refIndex;
        this.tarIndex = tarIndex;
        this.order = order;

    }

    abstract void makeComparisonSeries();

    abstract void calculateComparisonStatistics();
    abstract double calculateModel(double value);
    abstract void createBinnedData();
    abstract double getStatistic();
    abstract void printTests(String text);

    public XYSeries getModelData() {
        return modelData;
    }

    public XYSeries getTestSeries() {
        return testSeries;
    }
    public void addToTestSeries(Number x, double y){ testSeries.add(x, y);}

    public double getLocation(){return location;} //akin to average
    public double getScale(){return scale;}

    public XYSeries getReference(){ return reference;}
    public XYSeries getTargetSeries() {
        return targetSeries;
    }

    public XYSeries getTargetError() {
        return targetError;
    }

    public int getRefIndex(){ return refIndex;}
    public int getTarIndex(){ return tarIndex;}

}
