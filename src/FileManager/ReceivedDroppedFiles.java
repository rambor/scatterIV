package FileManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import version4.LogIt;
import version4.tableModels.AnalysisModel;
import version4.Collection;
import version4.tableModels.DataFileElement;
import version4.tableModels.ResultsModel;
import version4.tableModels.SampleBufferElement;

/*
 * special class for the GUI, not to be used from command line
 *
 */
public class ReceivedDroppedFiles extends SwingWorker<Void, Integer> {

    private JLabel status;
    private boolean convertToAng;
    private boolean sortFiles;
    private boolean doGuinier;
    private JProgressBar bar;
    private File[] files;
    private Collection targetCollection;
    private String workingDirectoryName;
    private AbstractTableModel modelToUpdate;

    private DefaultListModel<SampleBufferElement> sampleBufferFilesModel;
    private DefaultListModel<DataFileElement> dataFilesModel;  // common for all files that are loaded and displayed as a Jlist

    private double qmax;
    private boolean exclude, shortened = false;


    public ReceivedDroppedFiles(File[] files, Collection targetCollection, AbstractTableModel modelToUpdate, JLabel status, boolean convertNMtoAng, boolean doGuinier, boolean sort, final JProgressBar bar, String workingDirectoryName){
        this.files = files;
        this.targetCollection = targetCollection;
        this.status = status;
        this.convertToAng = convertNMtoAng;
        this.doGuinier = doGuinier;
        this.bar = bar;
        this.sortFiles = sort;
        this.workingDirectoryName = workingDirectoryName;
        this.modelToUpdate = modelToUpdate;
    }


    public ReceivedDroppedFiles(File[] files, Collection targetCollection, DefaultListModel<SampleBufferElement> modelToUpdate, JLabel status, boolean convertNMtoAng, boolean sort, final JProgressBar bar, String workingDirectoryName){
        this.files = files;
        this.targetCollection = targetCollection;
        this.status = status;
        this.convertToAng = convertNMtoAng;
        this.doGuinier = false;
        this.shortened = true;
        this.bar = bar;
        this.sortFiles = sort;
        this.workingDirectoryName = workingDirectoryName;
        this.sampleBufferFilesModel = modelToUpdate;
    }


    @Override
    protected Void doInBackground() throws Exception {

        int totalFiles = files.length;

        bar.setMaximum(totalFiles);
        bar.setStringPainted(true);
        bar.setValue(0);

        //System.out.println("TOTAL FILES " + totalFiles);
        Comparator<File> fileComparator = new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                int n1 = extractNumber(o1.getName());
                int n2 = extractNumber(o2.getName());
                return n1 - n2;
            }

            private int extractNumber(String name) {
                int i = 0;
                try {
                    //int s = name.indexOf('_')+1;
                    int s = name.lastIndexOf('_')+1;
                    int e = name.lastIndexOf('.');
                    String number = name.substring(s, e);
                    i = Integer.parseInt(number);
                } catch(Exception e) {
                    i = 0; // if filename does not match the format
                    // then default to 0
                }
                return i;
            }
        };


        if (sortFiles && totalFiles > 1){ // sort the name of directories first and then proceed to loading files
            Arrays.sort(files, fileComparator);
        }

        for( int i = 0; i < totalFiles; i++ ) {
            // call File loader function
            // if true add loaded file object to collection
            if (files[i].isDirectory()){
                //int sizeOfCollection = targetCollection.getDatasetCount();
                File[] tempFiles = finder(files[i].getAbsolutePath());

                // sort
                if (sortFiles){
                    Arrays.sort(tempFiles, fileComparator);
                }

                for (int j=0; j < tempFiles.length; j++){
                    //temp = loadDroppedFile(tempFiles[j], targetCollection.getDatasetCount());
                    addToCollection(loadDroppedFile(tempFiles[j], targetCollection.getTotalDatasets()));

                }

            } else {

                String[] filename = files[i].getName().split("\\.(?=[^\\.]+$)");
                String ext = filename[1];
                String filebase = filename[0];
                if (ext.equals("pdb")){
                    // make Dataset from PDB file and add to collection
                    status.setText("Reading PDB file " + filebase + " => calculating P(r) - please wait ~ 1 min");
                    bar.setStringPainted(false);
                    bar.setIndeterminate(true);
                    PDBFile tempPDB = new PDBFile(files[i], qmax, exclude, workingDirectoryName);

                    int newIndex = targetCollection.getTotalDatasets();
                    targetCollection.createDataset(
                            tempPDB.getIcalc(),  //data
                            tempPDB.getError(),  //original
                            filebase,
                            true);

                    targetCollection.getDataset(newIndex).setIsPDB(tempPDB.getPrDistribution(), (int)tempPDB.getDmax(), tempPDB.getRg(), tempPDB.getIzero());
                    bar.setIndeterminate(false);
                } else {
                    //LoadedFile temp = loadDroppedFile(files[i], targetCollection.getDatasetCount());
                    addToCollection(loadDroppedFile(files[i], targetCollection.getTotalDatasets()));
                }
            }
            publish(i);
        }

        updateAbstractModel();
        //updateModels(panelIndex);
        //update collection ids
        int total = targetCollection.getTotalDatasets();
        for(int h=0; h<total; h++){
            targetCollection.getDataset(h).setId(h);
        }

        bar.setStringPainted(false);
        //bar.setVisible(false);
        System.gc();
        return null;
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        bar.setValue(i);
        super.process(chunks);
    }


//    @Override
//    protected void done() {
//        try {
//            //get();
//            bar.setValue(0);
//            bar.setStringPainted(false);
//            status.setText("FINISHED");
//
//            if (panelIndex == 96 ){
////                list.revalidate();
////                list.repaint();
////                list.validate();
////                list.updateUI();
//            }
//            System.out.println("Done from done");
//            get();
//        } catch (ExecutionException | InterruptedException e) {
//            e.printStackTrace();
//        }
//    }

    private void updateAbstractModel(){
        if (modelToUpdate instanceof AnalysisModel){
//            ((AnalysisModel) modelToUpdate).clear();
//            for (int i = 0; i < targetCollection.getTotalDatasets(); i++) {
//                ((AnalysisModel) modelToUpdate).addDataset(targetCollection.getDataset(i));
//            }
        } else if (sampleBufferFilesModel instanceof DefaultListModel){
            //sampleBufferFilesModel.clear();
            Color tempColor;
            for(int i=0; i< targetCollection.getTotalDatasets(); i++){
                String name = targetCollection.getDataset(i).getFileName() + "_" + i;
                tempColor = targetCollection.getDataset(i).getColor();
                sampleBufferFilesModel.addElement(new SampleBufferElement(name, i, tempColor, targetCollection.getDataset(i)));
            }
        }
    }


    private void addToCollection(LoadedFile tempFile){
        // how to update results? Use a results object
        // if new dataset is added, we will have to add a JLabel thing and rerender
        // if updating, we could probably just change the value of the object which will automatically update value

        if (tempFile.isValid()){ // do not add if it contains null intensities must throw exeption

            if (shortened){
                targetCollection.createBareDataset(tempFile);
            } else {
                targetCollection.createDataset(tempFile, doGuinier);
            }

            if (tempFile.hasJson()){
                targetCollection.getLast().setSasObject(tempFile.getJSONString());
            }

        } else {
            try {
                throw new Exception("Rejecting File, contains Null Intensities: " + tempFile.filebase);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    public File[] finder(String dirName){
        File dir = new File(dirName);
        //System.out.println("DIRECTORY NAME IN FINDER : " + dirName + " " + dir.listFiles().length);
        return dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename)
            { return filename.endsWith(".dat"); }
        } );
    }

    private LoadedFile loadDroppedFile(File file, int assignedfileIndex){

        // how to handle different file formats?
        // get file base and extension
        String[] currentFile;
        currentFile = file.getName().split("\\.(?=[^\\.]+$)");

        String ext = (currentFile.length > 2) ? currentFile[2] : currentFile[1];

        try {
            if (ext.equals("brml")) {
                status.setText("Bruker .brml  file detected ");
                File tempFile;
                tempFile = Bruker.makeTempDataFile(file, workingDirectoryName);
                return new LoadedFile(tempFile, assignedfileIndex, convertToAng);

            } else if (ext.equals("int") || ext.equals("dat") || ext.equals("fit") || ext.equals("Adat") || ext.equals("csv")) {
                return new LoadedFile(file, assignedfileIndex, convertToAng);
            } else {
                // throw exception - incorrect file format
                throw new Exception("Incorrect file format: Use either brml, dat, csv, Adat, or Bdat file formats: " + currentFile);
            }

        } catch (Exception ex) {
            status.setText(ex.getMessage());
            LogIt.log(Level.SEVERE, ex.getMessage());
        }
        //add to collection
        return null;
    }

    public void setPDBParams(boolean exclude, double qmax){
        this.exclude = exclude;
        this.qmax = qmax;
    }
}
