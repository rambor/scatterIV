package version4;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Vector;

/**
 * Created by robertrambo on 25/01/2016.
 */
public class DoubleValue {

    protected double value=0;

    // the collection of objects listening for property changes
    protected Vector propChangeListeners = new Vector();

    // the constructors
    public DoubleValue(double value) {
        this();
        this.value = value;
    }

    public DoubleValue() {
    }

    // the get method for property CurrentTemperature
    public double getValue() {
        return value;
    }

    public void setValue(double value){
        this.value = value;
        this.notifyDirectoryChange();
    }

    // add a property change listener
    public synchronized void addPropertyChangeListener(PropertyChangeListener l) {
        // add a listener if it is not already registered
        if (!propChangeListeners.contains(l)) {
            propChangeListeners.addElement(l);
        }
    }

    // remove a property change listener
    public synchronized void removePropertyChangeListener(PropertyChangeListener l) {
        // remove it if it is registered
        if (propChangeListeners.contains(l)) {
            propChangeListeners.removeElement(l);
        }
    }

    // notify listening objects of property changes
    protected void notifyDirectoryChange() {
        // create the event object
        PropertyChangeEvent evt = new PropertyChangeEvent(this, "DoubleValue", null, new Double(value));
        // make a copy of the listener object vector so that it cannot
        // be changed while we are firing events
        Vector v;
        synchronized(this) {
            v = (Vector) propChangeListeners.clone();
        }

        // fire the event to all listeners
        int cnt = v.size();
        for (int i = 0; i < cnt; i++) {
            PropertyChangeListener client = (PropertyChangeListener)v.elementAt(i);
            client.propertyChange(evt);
        }
    }

}