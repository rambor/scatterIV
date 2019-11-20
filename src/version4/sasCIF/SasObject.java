package version4.sasCIF;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import version4.SEC.SecFormat;

import java.io.IOException;

public class SasObject implements Hidable {

    private SasBeam sasBeam;
    private SasBuffer sasBuffer;
    private SasScan sasScan;
    private SasDetc sasDetc;
    private SasSample sasSample;
    private SecFormat secFormat;

    public SasObject(){
        sasBeam = new SasBeam();
        sasBuffer = new SasBuffer();
        sasScan = new SasScan();
        sasDetc = new SasDetc();
        sasSample = new SasSample();
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

            nameNode = root.path("sec_format");
            if (!nameNode.isMissingNode()) {        // if "name" node is exist
                secFormat = mapper.treeToValue(nameNode, SecFormat.class);
            } else {
                secFormat = new SecFormat();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setSecFormat(SecFormat format){ this.secFormat = format;}

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

    @JsonIgnore
    public String getJSONString(){
        return "";
    }


    @Override
    public boolean isHidden() {
        return false;
    }
}
