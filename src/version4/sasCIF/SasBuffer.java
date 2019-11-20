package version4.sasCIF;

import java.util.ArrayList;

/**
 * Created by robertrambo on 09/02/2017.
 */
public class SasBuffer {


    private ArrayList<SasBufferComponent> components;
    private float pH = 7.0f;
    private String comment, name, salt, other;

    public SasBuffer(){
        comment = "no details specified";
        name = "water";
    }

    public ArrayList<SasBufferComponent> getComponents() {
        return components;
    }

    public void setComponents(ArrayList<SasBufferComponent> components) {
        this.components = components;
    }

    public float getpH() {
        return pH;
    }

    public void setpH(float pH) {
        this.pH = pH;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getOther() {
        return other;
    }

    public void setOther(String other) {
        this.other = other;
    }
}
