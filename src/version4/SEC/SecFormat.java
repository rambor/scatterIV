package version4.SEC;

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
import java.util.ArrayList;

public class SecFormat {

    private int total_momentum_transfer_vectors;
    private int total_frames;
    private int frame_index;
    private int signal_index;
    private int rg_index;
    private int izero_index;
    private int integrated_qIq_index;
    private int rg_error_index;
    private int izero_error_index;

    private int background_index;
    private int momentum_transfer_vector_index;
    private Integer momentum_transfer_vector_error_index;
    private int unsubtracted_intensities_index;
    private int unsubtracted_intensities_error_index;
    private int subtracted_intensities_index;
    private int subtracted_intensities_error_index;
    private Integer averaged_buffer_index;
    private Integer averaged_buffer_error_index;
    private double threshold;

    public SecFormat(){

    }

    public SecFormat(int frames){
        this.total_frames = frames;
    }

    public SecFormat(SecFormat old){
        this.total_momentum_transfer_vectors = old.total_momentum_transfer_vectors;
        this.total_frames = old.total_frames;
        this.frame_index = old.frame_index;
        this.signal_index = old.signal_index;
        this.rg_index = old.rg_index;
        this.izero_index = old.izero_index;
        this.integrated_qIq_index = old.integrated_qIq_index;
        this.rg_error_index = old.rg_error_index;
        this.izero_error_index = old.izero_error_index;
        this.background_index = old.background_index;
        this.momentum_transfer_vector_index = old.momentum_transfer_vector_index;
        this.momentum_transfer_vector_error_index = old.momentum_transfer_vector_error_index;
        this.unsubtracted_intensities_index = old.unsubtracted_intensities_index;
        this.unsubtracted_intensities_error_index = old.unsubtracted_intensities_error_index;
        this.subtracted_intensities_index = old.subtracted_intensities_index;
        this.subtracted_intensities_error_index = old.subtracted_intensities_error_index;
        this.averaged_buffer_index = old.averaged_buffer_index;
        this.averaged_buffer_error_index = old.averaged_buffer_error_index;
        this.threshold = old.threshold;
    }

    public int getTotal_momentum_transfer_vectors() {
        return total_momentum_transfer_vectors;
    }

    public void setTotal_momentum_transfer_vectors(int total_momentum_transfer_vectors) {
        this.total_momentum_transfer_vectors = total_momentum_transfer_vectors;
    }

    public int getTotal_frames() {
        return total_frames;
    }

    public void setTotal_frames(int total_frames) {
        this.total_frames = total_frames;
    }

    public int getMomentum_transfer_vector_index() {
        return momentum_transfer_vector_index;
    }

    public void setMomentum_transfer_vector_index(int momentum_transfer_vector_index) {
        this.momentum_transfer_vector_index = momentum_transfer_vector_index;
    }

    @JsonSerialize(using = CustomIntegerSerializer.class)
    public Integer getMomentum_transfer_vector_error_index() {
        return momentum_transfer_vector_error_index;
    }

    @JsonDeserialize(using = OptimizedIntegerDeserializer.class)
    public void setMomentum_transfer_vector_error_index(Integer momentum_transfer_vector_error_index) {
        this.momentum_transfer_vector_error_index = momentum_transfer_vector_error_index;
    }

    @JsonSerialize(using = CustomIntegerSerializer.class)
    public Integer getAveraged_buffer_index() {
        return averaged_buffer_index;
    }

    @JsonDeserialize(using = OptimizedIntegerDeserializer.class)
    public void setAveraged_buffer_index(Integer averaged_buffer_index) {
        this.averaged_buffer_index = averaged_buffer_index;
    }

    @JsonSerialize(using = CustomIntegerSerializer.class)
    public Integer getAveraged_buffer_error_index() {
        return averaged_buffer_error_index;
    }

    @JsonDeserialize(using = OptimizedIntegerDeserializer.class)
    public void setAveraged_buffer_error_index(Integer averaged_buffer_error_index) {
        this.averaged_buffer_error_index = averaged_buffer_error_index;
    }

    public int getUnsubtracted_intensities_index() {
        return unsubtracted_intensities_index;
    }

    public void setUnsubtracted_intensities_index(int unsubtracted_intensities_index) {
        this.unsubtracted_intensities_index = unsubtracted_intensities_index;
    }

    public int getUnsubtracted_intensities_error_index() {
        return unsubtracted_intensities_error_index;
    }

    public void setUnsubtracted_intensities_error_index(int unsubtracted_intensities_error_index) {
        this.unsubtracted_intensities_error_index = unsubtracted_intensities_error_index;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public int getSignal_index() {
        return signal_index;
    }

    public void setSignal_index(int signal_index) {
        this.signal_index = signal_index;
    }

    public int getRg_index() {
        return rg_index;
    }

    public void setRg_index(int rg_index) {
        this.rg_index = rg_index;
    }

    public int getRg_error_index(){ return rg_error_index;}

    public void setRg_error_index(int index) {
        this.rg_error_index = index;
    }

    public int getIzero_index() {
        return izero_index;
    }

    public void setIzero_index(int izero_index) {
        this.izero_index = izero_index;
    }

    public int getIzero_error_index(){ return izero_error_index;}

    public void setIzero_error_index(int index) {
        this.izero_error_index = index;
    }


    public int getIntegrated_qIq_index() {
        return integrated_qIq_index;
    }

    public void setIntegrated_qIq_index(int integrated_qIq_index) {
        this.integrated_qIq_index = integrated_qIq_index;
    }

    public int getSubtracted_intensities_index() {
        return subtracted_intensities_index;
    }

    public void setSubtracted_intensities_index(int subtracted_intensities_index) {
        this.subtracted_intensities_index = subtracted_intensities_index;
    }

    public int getSubtracted_intensities_error_index() {
        return subtracted_intensities_error_index;
    }

    public void setSubtracted_intensities_error_index(int subtracted_intensities_error_index) {
        this.subtracted_intensities_error_index = subtracted_intensities_error_index;
    }

    public int getBackground_index() {
        return background_index;
    }

    public void setBackground_index(int background_index) {
        this.background_index = background_index;
    }

    public int getFrame_index() {
        return frame_index;
    }

    public void setFrame_index(int frame_index) {
        this.frame_index = frame_index;
    }


    /*
     * convert null or 0 values to "." for writing to file
     */
    public static class CustomIntegerSerializer extends StdSerializer<Integer> {

        public CustomIntegerSerializer() {
            this(null);
        }

        public CustomIntegerSerializer(Class<Integer> t) {
            super(t);
        }

        @Override
        public void serialize(Integer value, JsonGenerator gen, SerializerProvider arg2) throws IOException, JsonProcessingException {
            if (value == null || value == 0){
                gen.writeString(".");
            } else {
                gen.writeString(String.valueOf(value));
            }
        }
    }


    public static class OptimizedIntegerDeserializer extends JsonDeserializer<Integer> {

        @Override
        public Integer deserialize(JsonParser jsonParser,
                                  DeserializationContext deserializationContext) throws
                IOException, JsonProcessingException {

            String text = jsonParser.getText();
            if(".".equals(text)) return null;
            return Integer.parseInt(text);
        }
    }
}
