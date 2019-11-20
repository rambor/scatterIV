package version4.sasCIF;

import java.util.HashMap;

public abstract class SasBase {

    private String nameOfClass;
    private HashMap<String, String> attributes;

    public SasBase(String typeOf){
        nameOfClass = typeOf.toUpperCase();
    }

    public void addAttribute(String key, String value){
        attributes.put(key, value);
    }
}
