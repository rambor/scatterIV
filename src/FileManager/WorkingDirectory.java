package FileManager;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Vector;

public class WorkingDirectory {
    //
    protected String workingDirectory="";

    // the collection of objects listening for property changes
    protected Vector propChangeListeners = new Vector();

    // the constructors
    public WorkingDirectory(String text) {
        this();
        workingDirectory = text;
    }

    public WorkingDirectory() {
    }

    // the get method for property CurrentTemperature
    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String temp){
        this.workingDirectory = temp;
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
        PropertyChangeEvent evt = new PropertyChangeEvent(this, "WorkingDirectory", null, new String(workingDirectory));
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
