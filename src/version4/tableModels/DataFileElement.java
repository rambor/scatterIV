package version4.tableModels;

/**
 * Created by robertrambo on 05/01/2016.
 */
public class DataFileElement {
    private String filename;
    private String fullpath;
    private int collection_id;
    public boolean isSelected = false;

    //Constructor
    public DataFileElement(String name, int pos){
        //make jpanel with checkbox and filename
        this.filename = name;
        // position in ArrayList
        this.collection_id = pos;

    }

    public boolean isSelected(){
        return isSelected;
    }

    public int getCollection_id(){
        return collection_id;
    }

    public void setSelected(boolean isSelected){
        this.isSelected = isSelected;
    }

    public boolean getSelected(){
        return this.isSelected;
    }

    public void setFullpath(String path){
        fullpath = path;
    }

    public String getFilename(){
        return filename;
    }

    public String getFullpath(){
        return fullpath;
    }

    public String toString(){
        return filename;
    }
}