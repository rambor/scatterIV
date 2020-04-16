package version4;

import FileManager.LoadedFile;
import org.jfree.data.xy.XYSeries;
import version4.Dataset;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

/**
 * Created by robertrambo on 05/01/2016.
 */
public class Collection {
    //collection of datasets
    private ArrayList<Dataset> datasets;
    private int panelID;
    private final String collectionName;
    private int totalDatasets=0;
    private String note;
    private double maxI;
    private double minI;
    private double minq;
    private double maxq;

    public Random rand;
    private String WORKING_DIRECTORY_NAME;

    protected Vector propChangeListeners = new Vector();

    // constructor
    public Collection(String name){
        collectionName = name;
        datasets = new ArrayList<>();
        rand = new Random();
        maxI = Double.NEGATIVE_INFINITY;
        minI = Double.POSITIVE_INFINITY;
        maxq = 0.0;
        minq = 1.0;
        // the collection of objects listening for property changes
    }


    public void setPanelID(int id){
        this.panelID = id;
    }

    /**
     * Returns dataset at specific index
     * @return Dataset object
     */
    public Dataset getDataset(int index){
        return datasets.get(index);
    }

    /**
     * Returns datasets
     * @return ArrayList of Dataset objects
     */
    public ArrayList<Dataset> getDatasets(){
        return datasets;
    }

    public int getTotalDatasets(){ return totalDatasets;}

    /**
     *
     * @return the number of selected datasets in use
     */
    public int getTotalSelected(){
        int selected=0;

        for(int i=0; i<this.getDatasets().size(); i++){
            if (this.getDataset(i).getInUse()){
                selected++;
            }
        }
        return selected;
    }

    public int getTotalNotSelected(){
        int notSelected=0;

        for(int i=0; i< datasets.size(); i++){
            if (!(datasets.get(i).getInUse())){
                notSelected++;
            }
        }
        return notSelected;
    }

    public void createDataset(XYSeries intensities, XYSeries errors){
        int newIndex = datasets.size();
        datasets.add(new Dataset(
                intensities,       //data
                errors,  //original
                newIndex));

        totalDatasets = datasets.size();

        Dataset dat = datasets.get(totalDatasets-1);
        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }

        this.notifyDataSetsChange();
    }

    public void createDataset(XYSeries intensities, XYSeries errors, int id){

        datasets.add(new Dataset(
                intensities,       //data
                errors,  //original
                id));

        totalDatasets = datasets.size();

        Dataset dat = datasets.get(totalDatasets-1);
        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }

        this.notifyDataSetsChange();
    }

    public void createDataset(XYSeries intensities, XYSeries errors, String filename, boolean guinier){
        int newIndex = datasets.size();
        datasets.add(new Dataset(
                intensities,       //data
                errors,  //original
                filename,
                newIndex, guinier));

        totalDatasets = datasets.size();

        Dataset dat = datasets.get(totalDatasets-1);
        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }

        this.notifyDataSetsChange();
    }

    public double getMaxI() {return maxI;}
    public double getMinI() {return minI;}

    public void createDataset(LoadedFile loaded, boolean guinier){

        int newIndex = datasets.size();

        datasets.add(new Dataset(
                loaded.allData,       //data
                loaded.allDataError,  //original
                loaded.filebase,
                newIndex, guinier));

        totalDatasets = datasets.size();

        Dataset dat = datasets.get(totalDatasets-1);
        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }

        this.notifyDataSetsChange();
    }


    public void createBareDataset(LoadedFile loaded){

        int newIndex = datasets.size();

        datasets.add(new Dataset(
                loaded.allData,       //data
                loaded.allDataError,  //original
                loaded.filebase,
                newIndex));

        totalDatasets = datasets.size();

        Dataset dat = datasets.get(totalDatasets-1);
        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }

        this.notifyDataSetsChange();
    }


    /**
     * use this to avoid notification of changes
     *
     * @param loaded
     */
    public void createBareDatasetAPI(LoadedFile loaded){

        int newIndex = datasets.size();

        datasets.add(new Dataset(
                loaded.allData,       //data
                loaded.allDataError,  //original
                loaded.filebase,
                newIndex));

        totalDatasets = datasets.size();

        Dataset dat = datasets.get(totalDatasets-1);
        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }
    }

    /**
     * Removes dataset at specific index
     * @param index Index of a Dataset to be removed
     */
    public void removeDataset(int index) {
        datasets.remove(datasets.get(index));
        /*
         * renumber datasets
         */
        this.totalDatasets = datasets.size();
        for(int i=index; i<totalDatasets; i++){
            datasets.get(i).setId(i);
        }
    }

    public void removeAllDatasets(){
        datasets.clear();
        totalDatasets = 0;
        maxI = Double.NEGATIVE_INFINITY;
        minI = Double.POSITIVE_INFINITY;
        maxq = 0.0;
        minq = 1.0;
    }


    // add a property change listener
    public synchronized void addPropertyChangeListener(PropertyChangeListener l) {
        // add a listener if it is not already registered
        if (!propChangeListeners.contains(l)) {
            propChangeListeners.addElement(l);
        }
    }
    // notify listening objects of property changes
    protected void notifyDataSetsChange() {
        // create the event object
        PropertyChangeEvent evt = new PropertyChangeEvent(this, collectionName, null, datasets);
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


    /**
     *
     * @return returns datalist ID of singly selected dataset
     */
    public int getSelected(){
        int selected = -1;

        if (this.getTotalSelected() == 1){
            for(int i=0; i < totalDatasets; i++){
                if (this.getDataset(i).getInUse()){
                    selected = i;
                    break;
                }
            }
        }
        return selected;
    }

    public double getMinq(){
        return minq;
    }

    public double getMaxq(){
        return maxq;
    }

    public Dataset getLast(){
        return this.getDataset(totalDatasets-1);
    }

    /**
     * Adds dataset to collection - note this is adding dataset as a reference
     * @param dat Dataset to be added
     */
    public void addDataset(Dataset dat){
        // do color assignment when adding to collection
        //float r,g,b;
        //r = rand.nextFloat();
        //g = rand.nextFloat();
        //b = rand.nextFloat();
        //dat.setColor(new Color(r,g,b));
        datasets.add(dat);
        totalDatasets = datasets.size();

        // reset max and min values for collection

        if (dat.getMaxI() > this.maxI ) {
            this.maxI = dat.getMaxI();
        }

        if (dat.getMinI() < this.minI ) {
            this.minI = dat.getMinI();
        }
        if (dat.getMaxq() > this.maxq ) {
            this.maxq = dat.getMaxq();
        }
        if (dat.getMinq() < this.minq ) {
            this.minq = dat.getMinq();
        }

        this.notifyDataSetsChange();
    }
}
