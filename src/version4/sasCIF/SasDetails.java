package version4.sasCIF;

import version4.Dataset;

import java.util.Date;

public class SasDetails {


    private float thickness = 1.50f;
    private float temperature = 25; // celcius
    private float exposure_time = 3.0f;
    private String title;

    private String momentum_transfer_units;

    private int number_of_frames = 1;

    public SasDetails(){
    }

    public String getText(){
        String tempHeader = String.format("# %n");
        tempHeader += String.format("# REMARK 265 EXPERIMENTAL DETAILS %n");
        tempHeader += String.format("# UNITS  %n");
        tempHeader += String.format("#     thickness : millimeter %n");
        tempHeader += String.format("#   temperature : Centigrade %n");
        tempHeader += String.format("_sas_sample.title ? %n");

        String temp ="";//=dataset.getExperimentalNotes().replaceAll("\r\n", "\n");
        String[] arrayOfLines = temp.split("\n");
        if (arrayOfLines.length > 0 && temp.length() > 3){
            int total = arrayOfLines.length;
            for(int i=0;i<total;i++){
                tempHeader += String.format("_sas_sample.details %s %n", arrayOfLines[i].trim());
            }
        } else {
            temp = "NO INFORMATION PROVIDED";
            tempHeader += String.format("_sas_sample.details %s %n", temp);
        }

        tempHeader += String.format("_sas_sample.details %s %n", temp);
        tempHeader += String.format("_sas_sample.cell_temperature %.1f %n", temperature);
        tempHeader += String.format("_sas_sample.thickness %.2f %n", thickness);
        return tempHeader;
    }

    public void setInfo(){

    }

    public float getExposure_time(){
        return exposure_time;
    }

    public void setExposure_time(float time){ exposure_time = time;}

    public void setTitle(String title){ this.title = title;}
    public String getTitle(){ return title;}

    public int getNumber_of_frames() {
        return number_of_frames;
    }

    public void setNumber_of_frames(int number_of_frames) {
        this.number_of_frames = number_of_frames;
    }

    public String getMomentum_transfer_units() {
        return momentum_transfer_units;
    }

    public void setMomentum_transfer_units(String momentum_transfer_units) {
        this.momentum_transfer_units = momentum_transfer_units;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
}
