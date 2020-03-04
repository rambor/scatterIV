package version4.sasCIF;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.HashMap;

public class SasBeam extends SasBase {

    private String instrument_name = "mybeamline";
    private String type_of_source = "synchrotron";
    private String radiation_type ="XRAY";

    private double radiation_wavelength = 12400;

    @JsonDeserialize(using = OptimizedDoubleDeserializer.class)
    private Double flux;

    private String units="ev";

    @JsonDeserialize(using = OptimizedDoubleDeserializer.class)
    private Double sample_to_detector_distance=0.0d;

    public SasBeam(){
        super("sasbeam");
    }

    public SasBeam(SasBeam oldbeam){
        super("sasbeam");
        this.instrument_name = oldbeam.instrument_name;
        this.type_of_source = oldbeam.type_of_source;
        this.radiation_type = oldbeam.radiation_type;
        this.radiation_wavelength = oldbeam.radiation_wavelength;
        this.flux = oldbeam.flux;
        this.units = oldbeam.units;
        this.sample_to_detector_distance = oldbeam.sample_to_detector_distance;
        for (HashMap.Entry<String, String> entry : oldbeam.attributes.entrySet()) {
            this.attributes.put(entry.getKey(), entry.getValue());
        }
    }

    public String getInstrument_name() {
        return instrument_name;
    }

    public void setInstrument_name(String instrument_name) {
        this.instrument_name = instrument_name;
    }

    public String getType_of_source() {
        return type_of_source;
    }

    public void setType_of_source(String type_of_source) {
        this.type_of_source = type_of_source;
    }

    /**
     * units of eV
     * @param radiation_wavelength
     */
    public void setRadiation_wavelength(double radiation_wavelength) {
        this.radiation_wavelength = radiation_wavelength;
    }

    /**
     * units of photons per second
     * @param flux
     */
    public void setFlux(double flux) {
        this.flux = flux;
    }

    public void setSample_to_detector_distance(double dis) {
        this.sample_to_detector_distance = dis;
    }

    public double getRadiation_wavelength() {
        return radiation_wavelength;
    }

    @JsonSerialize(using = CustomDoubleSerializer.class)
    public Double getFlux() {
        return flux;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }

    @JsonSerialize(using = CustomDoubleSerializer.class)
    public Double getSample_to_detector_distance() {
        return sample_to_detector_distance;
    }

    public String getRadiation_type() {
        return radiation_type;
    }

    public void setRadiation_type(String radiation_type) {
        this.radiation_type = radiation_type;
    }


    public static class CustomDoubleSerializer extends StdSerializer<Double> {

        public CustomDoubleSerializer() {
            this(null);
        }

        public CustomDoubleSerializer(Class<Double> t) {
            super(t);
        }

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider arg2) throws IOException, JsonProcessingException {
            if (value == null || value == 0){
                gen.writeString(".");
            } else {
                gen.writeString(String.valueOf(value));
            }
        }
    }


    public static class OptimizedDoubleDeserializer extends JsonDeserializer<Double> {

        @Override
        public Double deserialize(JsonParser jsonParser,
                                   DeserializationContext deserializationContext) throws
                IOException, JsonProcessingException {

            String text = jsonParser.getText();
            if(".".equals(text)) return 0.0d;
            return Double.parseDouble(text);
        }
    }
}
