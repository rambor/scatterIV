package version4.sasCIF;

import version4.Dataset;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SasScan {

    public Object set;
    private String tempHeader;
    private float exposure_time;
    private float dead_time;
    private String scan_name; // original filename of the scan on instrument
    private String intensity_units; // au or electrons per cm
    private String momentum_transfer_units;
    private String measurement_date; // YYYY-MM-DD
    private String type; // background, sample, detector, matrix, processed, sec

    public SasScan(){
        intensity_units = "au";
        momentum_transfer_units = "inverse Angstroms";

        type = "background";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        measurement_date = format.format(new Date());
        dead_time = 0.1f;
        exposure_time = 1.0f;
    }

//    public SasScan(Dataset dataset, int id){
//        this.dataset = dataset;
//        //this.setHeader( );
//    }

    public SasScan(SasScan scan){
        this.tempHeader = scan.tempHeader;
        this.exposure_time = scan.exposure_time;
        this.dead_time = scan.dead_time;
        this.scan_name = scan.scan_name;
        this.intensity_units = scan.intensity_units;
        this.momentum_transfer_units = scan.momentum_transfer_units;
        this.measurement_date = scan.measurement_date;
        this.type = scan.type;
    }


    private String getHeader(){
//        SasDetails details = dataset.getSasDetails();
        tempHeader = String.format("# %n");
        tempHeader += String.format("# REMARK 265 sas_scan %n");
        tempHeader += String.format("loop_ %n");
        tempHeader += String.format("_sas_scan.id %n");
        tempHeader += String.format("_sas_scan.calibration_factor ? %n");
        tempHeader += String.format("_sas_scan.exposure_time %.3f %n");
        tempHeader += String.format("_sas_scan.scan_name %s %n");
        tempHeader += String.format("_sas_scan.intensity_units %n");
        tempHeader += String.format("_sas_scan.measurement_date %n");
        tempHeader += String.format("_sas_scan.momentum_transfer_units %s %n");
        tempHeader += String.format("_sas_scan.number_of_frames %d %n");
        tempHeader += String.format("_sas_scan.result_id %d %n");
        tempHeader += String.format("_sas_scan.sample_id %n");
        tempHeader += String.format("_sas_scan.title %s %n");
        tempHeader += String.format("_sas_scan.type %n");
        return tempHeader;
    }

    public String getMomentum_transfer_units() {
        return momentum_transfer_units;
    }

    public void setMomentum_transfer_units(String momentum_transfer_units) {
        this.momentum_transfer_units = momentum_transfer_units;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIntensity_units() {
        return intensity_units;
    }

    public float getExposure_time() {
        return exposure_time;
    }

    public void setExposure_time(float exposure_time) {
        this.exposure_time = exposure_time;
    }

    public float getDead_time() {
        return dead_time;
    }

    public String getScan_name() {
        return scan_name;
    }

    public void setIntensity_units(String intensity_units) {
        this.intensity_units = intensity_units;
    }

    public void setScan_name(String scan_name) {
        this.scan_name = scan_name;
    }

    public String getMeasurement_date() {
        return measurement_date;
    }

    public void setMeasurement_date(String measurement_date) {
        this.measurement_date = measurement_date;
    }
}
