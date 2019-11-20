package version4.sasCIF;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.junit.Assert;
import org.junit.Test;
import version4.SEC.SecFormat;


import java.io.IOException;

import static org.junit.Assert.*;

public class SasObjectTest {

    @Test
    public void setSecFormat() throws IOException {

        SasObject sasObject = new SasObject();

        /*
         * assemble the JSON string
         */
        ObjectMapper mapper = new ObjectMapper();

        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.registerModule(new SimpleModule() {
            @Override
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                context.addBeanSerializerModifier(new BeanSerializerModifier() {
                    @Override
                    public JsonSerializer<?> modifySerializer(
                            SerializationConfig config, BeanDescription desc, JsonSerializer<?> serializer) {
                        if (Hidable.class.isAssignableFrom(desc.getBeanClass())) {
                            return new HidableSerializer((JsonSerializer<Object>) serializer);
                        }
                        return serializer;
                    }
                });
            }
        });

        String sasObjectString = mapper.writeValueAsString(sasObject);

        ObjectMapper mapperIt = new ObjectMapper();
        JsonNode root = mapperIt.readTree(sasObjectString);
        // Get Name
        JsonNode nameNode = root.path("sec_format");

        Assert.assertTrue(nameNode.isMissingNode());

        /*
         * create a SecFormat object
         * add it to the base SasObject and see if it is converted to a JSON string
         */
        SecFormat secFormat = new SecFormat(101);
        secFormat.setTotal_momentum_transfer_vectors(69);
        secFormat.setFrame_index(4);
        secFormat.setSignal_index(5);
        secFormat.setIntegrated_qIq_index(6);
        secFormat.setRg_index(7);
        secFormat.setIzero_index(8);
        secFormat.setBackground_index(9);
        secFormat.setMomentum_transfer_vector_index(10);
        int totalUnSub = 117;
        int totalSub = 117;
        secFormat.setUnsubtracted_intensities_index(11);
        secFormat.setUnsubtracted_intensities_error_index(11+totalUnSub+1);
        secFormat.setSubtracted_intensities_index(11+2*totalUnSub+1);
        secFormat.setSubtracted_intensities_error_index(11+2*totalUnSub+totalSub+1);

        sasObject.setSecFormat(secFormat);
        sasObjectString = mapper.writeValueAsString(sasObject);
        root = mapperIt.readTree(sasObjectString);
        nameNode = root.path("sec_format");

        Assert.assertFalse(nameNode.isMissingNode());
    }
}