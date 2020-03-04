package version4.sasCIF;

/**
 * Created by robertrambo on 09/02/2017.
 */
public class SasBufferComponent {

    public String unit;
    public float concentration;
    public String name;
    public String chemicalFormula;

    public SasBufferComponent(){

    }

    public SasBufferComponent(SasBufferComponent comp){
        this.unit = comp.unit;
        this.concentration = comp.concentration;
        this.name = comp.name;
        this.chemicalFormula = comp.chemicalFormula;
    }
}
