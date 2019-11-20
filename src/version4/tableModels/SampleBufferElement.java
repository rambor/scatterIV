package version4.tableModels;

import version4.Dataset;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Created by robertrambo on 18/01/2016.
 */
public class SampleBufferElement extends DataFileElement {

    public Color color;
    public Dataset dataset;
    private PropertyChangeListener booleanListener;

    public SampleBufferElement(String name, int pos, Color color, Dataset dataset) {
        super(name, pos);
        this.color = color;
        this.isSelected = true;
        this.dataset = dataset;
        //this.setSelected(true);
        booleanListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                //update collections for sample
                //System.out.println("I changed " + ((SampleBufferElement)evt.getSource()).getCollection_id());
            }
        };
    }

    public void setColor(Color color){
        this.color = color;
    }
    public Color getColor(){
        return this.color;
    }

    @Override
    public void setSelected(boolean b1){
        boolean oldValue = super.isSelected;
        super.setSelected(b1);
        dataset.setInUse(b1);
        firePropertyChange("selected", oldValue, b1);
    }

    private void firePropertyChange(String isSelected, Object oldValue, Object newValue) {
        booleanListener.propertyChange(new PropertyChangeEvent(this, isSelected, oldValue, newValue));
    }
}
