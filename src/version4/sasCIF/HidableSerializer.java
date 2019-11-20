package version4.sasCIF;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;


public class HidableSerializer extends JsonSerializer<Hidable> {

    private JsonSerializer<Object> defaultSerializer;

    public HidableSerializer(JsonSerializer<Object> serializer) {
        defaultSerializer = serializer;
    }

    @Override
    public void serialize(Hidable value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonProcessingException {
        if (value.isHidden())
            return;
        defaultSerializer.serialize(value, jgen, provider);
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Hidable value) {
        return (value == null || value.isHidden());
    }
}