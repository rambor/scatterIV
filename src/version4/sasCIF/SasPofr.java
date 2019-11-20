package version4.sasCIF;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Dataset;

import java.util.Locale;

public class SasPofr {

    private Dataset dataset;

    public SasPofr(Dataset dataset){
        this.dataset = dataset;
    }

    /**
     * scan index of 0 refers to original SAXS data loaded into Scatter
     * scan index of 1 refers to trimmed data used for fitting
     * _su_ is standard uncertainty as defined by SASCIF
     * @return
     */
    public String getTextOfFittedDataForOutput(int resultId){
        String tempHeader = String.format("# %n");
        tempHeader += String.format("# REMARK 265 P(r) details %n");
        tempHeader += String.format("_sas_p_of_R_details.id 1 %n");
        tempHeader += String.format("_sas_p_of_R_details.Rmin 0 %n");
        tempHeader += String.format("_sas_p_of_R_details.Rmax %.2f %n", dataset.getDmax());
        tempHeader += String.format("_sas_p_of_R_details.number_of_points %d %n", dataset.getRealSpaceModel().getPrDistribution().getItemCount());
        tempHeader += String.format("_sas_p_of_R_details.p_of_R_point_max ? %n");
        tempHeader += String.format("_sas_p_of_R_details.p_of_R_point_min ? %n");
        tempHeader += String.format("_sas_p_of_R_details.qmin %.5f %n", dataset.getRealSpaceModel().getfittedqIq().getMinX());
        tempHeader += String.format("_sas_p_of_R_details.qmax %.5f %n", dataset.getRealSpaceModel().getfittedqIq().getMaxX());
        tempHeader += String.format("_sas_p_of_R_details.result_id %d %n", resultId);
        tempHeader += String.format("_sas_p_of_R_details.software_p_of_R Scatter %n");
        tempHeader += String.format("_sas_p_of_R_details.method %s %n", dataset.getRealSpaceModel().getIndirectFTModel().getModelUsed());

        tempHeader += dataset.getRealSpaceModel().getIndirectFTModel().getPrDistributionForFittingCIFFormat(1);
        tempHeader += dataset.getRealSpaceModel().getIndirectFTModel().getPrDistributionForPlottingCIFFormat(1);

        return tempHeader;
    }

    private int getDigits(double qvalue) {
        String toText = Double.toString(qvalue);
        int integerPlaces = toText.indexOf('.');
        int decimalPlaces;

        String[] temp = toText.split("\\.0*");
        decimalPlaces = (temp.length == 2) ? temp[1].length() : (toText.length() - integerPlaces -1);

        return decimalPlaces;
    }

    private String formattedQ(double qvalue, int numberOfDigits) {
        String numberToPrint ="";
        switch(numberOfDigits){
            case 7: numberToPrint = String.format(Locale.US, "%.6E", qvalue);
                break;
            case 8: numberToPrint = String.format(Locale.US, "%.7E", qvalue);
                break;
            case 9: numberToPrint = String.format(Locale.US, "%.8E", qvalue);
                break;
            case 10: numberToPrint = String.format(Locale.US, "%.9E", qvalue);
                break;
            case 11: numberToPrint = String.format(Locale.US,"%.10E", qvalue);
                break;
            case 12: numberToPrint = String.format(Locale.US, "%.11E", qvalue);
                break;
            case 13: numberToPrint = String.format(Locale.US, "%.12E", qvalue);
                break;
            case 14: numberToPrint = String.format(Locale.US, "%.13E", qvalue);
                break;
            default: numberToPrint = String.format(Locale.US,"%.6E", qvalue);
                break;
        }
        return numberToPrint;
    }
}
