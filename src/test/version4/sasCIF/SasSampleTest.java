package version4.sasCIF;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SasSampleTest {

    static SasSample sasSample;

    @BeforeClass
    public static void setup() {
        sasSample = new SasSample();
    }


    @Test
    public void getSec_column() {
        Assert.assertTrue(sasSample.getSec_column() == null);
    }

//    @Test
//    public void getSec_flow_rate() {
//        System.out.println("Getting ");
//        Assert.assertTrue(sasSample.getSec_flow_rate() == null);
//    }

    @Test
    public void getSec_flow_rate_units() {
        Assert.assertTrue(sasSample.getSec_flow_rate_units() != null);
    }

    /**
     * Test that if attributes are not set in SASSample, they are not written to a json string - specific for SEC SAS parameters
     * @throws IOException
     */
    @Test
    public void jsonSECAttributes() throws IOException {

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

        // creating string from SasSample object
        String sasObjectString = mapper.writeValueAsString(sasSample);

        ObjectMapper mapperIt = new ObjectMapper();
        JsonNode root = mapperIt.readTree(sasObjectString);

        Assert.assertFalse(root.has("sec_column"));
        Assert.assertFalse(root.has("sec_flow_rate"));
        Assert.assertFalse(root.has("sec_flow_rate_units"));
        // convert string to new json object and check for attributes

        sasSample.setSec_column("Shodex");
        sasSample.setSec_flow_rate_units("ml per min");
        sasSample.setSec_flow_rate(0.1f);

        sasObjectString = mapper.writeValueAsString(sasSample);

        mapperIt = new ObjectMapper();
        root = mapperIt.readTree(sasObjectString);

        Assert.assertTrue(root.has("sec_column"));
        Assert.assertTrue(root.has("sec_flow_rate"));
        Assert.assertTrue(root.has("sec_flow_rate_units"));
    }
}