package version4.plots;


import javax.swing.*;

/**
 * Created by robertrambo on 10/01/2016.
 */
class ScatterSpinner extends JSpinner {
    public int priorIndex;
    public int collectionID;

    public ScatterSpinner(int current, int id){
        priorIndex = current;
        collectionID = id;
    }

    public void setPriorIndex(int value){
        priorIndex = value;
    }

    public int getID(){
        return collectionID;
    }

    public int getPriorIndex(){
        return priorIndex;
    }
}