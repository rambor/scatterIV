package version4.sasCIF;


import org.jfree.data.xy.XYSeries;
import version4.Dataset;

import java.util.Locale;

public class SasIntensities {

    private int scan_id;
    private float resolution_width;
    private float exposure_time;
    private Dataset dataset;

    public SasIntensities(Dataset dataset){
        this.dataset = dataset;
    }

    /**
     * scan index of 0 refers to original SAXS data loaded into Scatter
     * scan index of 1 refers to trimmed data used for fitting
     * _su_ is standard uncertainty as defined by SASCIF
     * @return
     */
    public String getTextOfFittedDataForOutput(boolean getRefined){
        String tempHeader = String.format("# %n");
        tempHeader += String.format("# REMARK 265 %n");
        tempHeader += String.format("# scan_id => 0 refers to original intensity dataset  %n");
        tempHeader += String.format("# scan_id => 1 refers to intensity dataset used for determining P(r)-distribution %n");
        tempHeader += String.format("loop_ %n");
        tempHeader += String.format("_sas_scan_intensity.id %n");
        tempHeader += String.format("_sas_scan_intensity.scan_id %n");
        tempHeader += String.format("_sas_scan_intensity.momentum_transfer %n");
        tempHeader += String.format("_sas_scan_intensity.intensity %n");
        tempHeader += String.format("_sas_scan_intensity.intensity_su_counting %n");
        tempHeader += String.format("_sas_scan_intensity.intensity_su_systematic %n");
        tempHeader += String.format("_sas_scan_intensity.exposure_time %n");
        tempHeader += String.format("_sas_scan_intensity.resolution_width %n");

        int totalOriginalData = dataset.getAllData().getItemCount();
        XYSeries allData = dataset.getAllData();
        XYSeries allDataError = dataset.getAllDataError();

        int numberOfDigits = 0;
        for (int n=0; n < totalOriginalData; n++) {
            int tempnumberOfDigits = getDigits(allData.getX(n).doubleValue());
            if (tempnumberOfDigits > numberOfDigits){
                numberOfDigits = tempnumberOfDigits;
            }
        }

        for (int n=0; n < totalOriginalData; n++) {
            int index = n+1;
            tempHeader += String.format("%d %d %s %.4E %.4E ? ? ? %n", index, 0, formattedQ(allData.getX(n).doubleValue(), numberOfDigits), allData.getY(n).doubleValue(), allDataError.getY(n).doubleValue());
        }

        if (getRefined){
            XYSeries fittedData = dataset.getRealSpaceModel().getfittedIq();
            XYSeries fittedError = dataset.getRealSpaceModel().getfittedError();
            for (int n=0; n < dataset.getRealSpaceModel().getfittedIq().getItemCount(); n++) {
                int index = n+1;
                tempHeader += String.format("%d %d %s %.4E %.4E ? ? ? %n", index, 1, formattedQ(fittedData.getX(n).doubleValue(), numberOfDigits), fittedData.getY(n).doubleValue(), fittedError.getY(n).doubleValue());
            }
            tempHeader += "#";
        }

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
