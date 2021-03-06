package version4.sasCIF;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import version4.SEC.SecFormat;

import java.io.IOException;

public class SasObject implements Hidable {

    private SasBeam sasBeam;
    private SasBuffer sasBuffer;
    private SasScan sasScan;
    private SasDetc sasDetc;
    private SasSample sasSample;
    private SecFormat secFormat;
    private SasResult sasResult;

    /*
     * constructor does not initialize secFormat
     */
    public SasObject(){
        sasBeam = new SasBeam();
        sasBuffer = new SasBuffer();
        sasScan = new SasScan();
        sasDetc = new SasDetc();
        sasSample = new SasSample();

        //secFormat = new SecFormat();
        /*
         * SecFormat and SasResult are optional
         */
    }

    public SasObject(SasObject oldObject){
        this.sasBeam = new SasBeam(oldObject.getSasBeam());
        this.sasBuffer = new SasBuffer(oldObject.getSasBuffer());
        this.sasScan = new SasScan(oldObject.getSasScan());
        this.sasDetc = new SasDetc(oldObject.getSasDetc());
        this.sasSample = new SasSample(oldObject.getSasSample());

        // may or may not be present as secFormat is for SEC-SAXS data
        if (oldObject.secFormat != null){
            this.secFormat = new SecFormat(oldObject.getSecFormat());
        }
    }

    public SasObject(String jsonString){
        this.parseJSONString(jsonString);
    }

    /**
     * first element in file must be JSON string
     * UTF-8 will only use one byte per character
     */
    private void parseJSONString(String jsonString) {

        try {

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonString);
            // Get Name
            JsonNode nameNode = root.path("sas_beam");

            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                sasBeam = mapper.treeToValue(nameNode, SasBeam.class);
                System.out.println("JSON Object " + sasBeam.getRadiation_type());
                System.out.println("JSON Object " + sasBeam.getType_of_source());
            } else {
                sasBeam = new SasBeam();
            }

            nameNode = root.path("sas_buffer");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                sasBuffer = mapper.treeToValue(nameNode, SasBuffer.class);
            } else {
                sasBeam = new SasBeam();
            }

            nameNode = root.path("sas_scan");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                sasScan = mapper.treeToValue(nameNode, SasScan.class);
            } else {
                sasScan = new SasScan();
            }

            nameNode = root.path("sas_sample");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                sasSample = mapper.treeToValue(nameNode, SasSample.class);
                /*
                 * sec_column and sec_flow_rate are required fields
                 */
            } else {
                sasSample = new SasSample();
            }

            nameNode = root.path("sas_detc");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                sasDetc = mapper.treeToValue(nameNode, SasDetc.class);
            } else {
                sasDetc = new SasDetc();
            }

            nameNode = root.path("sas_result");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                sasResult = mapper.treeToValue(nameNode, SasResult.class);
            }

            nameNode = root.path("sec_format");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                secFormat = mapper.treeToValue(nameNode, SecFormat.class);
            }
//            } else {
//                secFormat = new SecFormat();
//            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSasBeam(SasBeam beam){ this.sasBeam = new SasBeam(beam);}
    public void setSasScan(SasScan scan){ this.sasScan = new SasScan(scan);}
    public void setSasBuffer(SasBuffer buff){ this.sasBuffer = new SasBuffer(buff);}
    public void setSasBeam(SasDetc detc){ this.sasDetc = new SasDetc(detc);}
    public void setSasSample(SasSample sample){this.sasSample = new SasSample(sample);}

    public void setSecFormat(SecFormat format){ this.secFormat = format;}

    public void setSasResult(SasResult result){ this.sasResult = result;}

    @JsonIgnore
    public boolean isResultSet(){
        return (this.sasResult != null);
    }

    @JsonProperty("sas_beam")
    public SasBeam getSasBeam() {
        return sasBeam;
    }

    @JsonProperty("sas_buffer")
    public SasBuffer getSasBuffer() {
        return sasBuffer;
    }

    @JsonProperty("sas_scan")
    public SasScan getSasScan() {
        return sasScan;
    }

    @JsonProperty("sas_detc")
    public SasDetc getSasDetc() {
        return sasDetc;
    }

    @JsonProperty("sas_sample")
    public SasSample getSasSample() {
        return sasSample;
    }

    @JsonProperty("sec_format")
    @JsonIgnoreProperties("hidden")
    public SecFormat getSecFormat(){
        return secFormat;
    }

    @JsonProperty("sas_result")
    @JsonIgnoreProperties("hidden")
    public SasResult getSasResult(){
        return sasResult;
    }


    @JsonIgnore
    public String getJSONString(){
        return "";
    }


    @Override
    public boolean isHidden() {
        return false;
    }

}
