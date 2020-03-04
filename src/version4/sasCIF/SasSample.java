package version4.sasCIF;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class SasSample implements Hidable{

    private String details;
    private float cell_temperature; // should be kelvin, units specified in details
    private String calibration_details; // calibrated off of water?
    private float thickness; // should be in mm, units specified in details
    // optional attributes below
    private String sec_column;
    private Float sec_flow_rate;
    private String sec_flow_rate_units = "ml per minute";

    public SasSample(){
        cell_temperature = 273.0f + 25.0f;
        calibration_details = "water";
    }

    public SasSample(SasSample sample){
        this.details = sample.details;
        this.cell_temperature = sample.cell_temperature;
        this.calibration_details = sample.calibration_details;
        this.thickness = sample.thickness;
        this.sec_column = sample.sec_column;
        this.sec_flow_rate = sample.sec_flow_rate;
        this.sec_flow_rate_units = sample.sec_flow_rate_units;
    }


    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public float getCell_temperature() {
        return cell_temperature;
    }

    public void setCell_temperature(float cell_temperature) {
        this.cell_temperature = cell_temperature;
    }

    public String getCalibration_details() {
        return calibration_details;
    }

    public void setCalibration_details(String calibration_details) {
        this.calibration_details = calibration_details;
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }

    @JsonIgnoreProperties("hidden")
    public String getSec_column() {
        return sec_column;
    }

    public void setSec_column(String sec_column) {
        this.sec_column = sec_column;
    }

    @JsonIgnoreProperties("hidden")
    public Float getSec_flow_rate() {
        return sec_flow_rate;
    }

    public void setSec_flow_rate(float sec_flow_rate) {
        this.sec_flow_rate = sec_flow_rate;
    }

    @JsonIgnoreProperties("hidden")
    public String getSec_flow_rate_units() {
        return sec_flow_rate_units;
    }

    public void setSec_flow_rate_units(String sec_flow_rate_units) {
        this.sec_flow_rate_units = sec_flow_rate_units;
    }


    @Override
    public boolean isHidden() {
        return false;
    }
}
