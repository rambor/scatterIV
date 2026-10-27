package FileManager;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import version4.LogIt;
import version4.tableModels.AnalysisModel;
import version4.Collection;
import version4.tableModels.DataFileElement;
import version4.tableModels.SampleBufferElement;

/*
 * special class for the GUI, not to be used from command line
 *
 */
public class ReceivedDroppedFiles extends SwingWorker<Void, Integer> {

    private final JLabel status;
    private final boolean convertToAng;
    private final boolean sortFiles;
    private final boolean doGuinier;
    private final JProgressBar bar;
    private File[] files;
    private final Collection targetCollection;
    private final String workingDirectoryName;
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

        if (files == null) {
            files = new File[0];
        }
        int totalFiles = files.length;

        bar.setMaximum(Math.max(1, totalFiles));
        bar.setStringPainted(true);
        bar.setValue(0);

        Comparator<File> fileComparator = new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Integer.compare(extractNumber(o1.getName()), extractNumber(o2.getName()));
            }

            private int extractNumber(String name) {
                try {
                    int s = name.lastIndexOf('_') + 1;
                    int e = name.lastIndexOf('.');
                    return Integer.parseInt(name.substring(s, e));
                } catch (Exception e) {
                    return 0; // filename does not match the format, default to 0
                }
            }
        };


        if (sortFiles && totalFiles > 1){ // sort the name of directories first and then proceed to loading files
            Arrays.sort(files, fileComparator);
        }

        int rejected = 0;
        for( int i = 0; i < totalFiles; i++ ) {
            if (files[i].isDirectory()){
                File[] tempFiles = finder(files[i].getAbsolutePath());

                if (sortFiles){
                    Arrays.sort(tempFiles, fileComparator);
                }

                for (File tempFile : tempFiles) {
                    if (!addToCollection(loadDroppedFile(tempFile, targetCollection.getTotalDatasets()))) {
                        rejected++;
                    }
                }

            } else {

                String filebase = baseName(files[i]);
                String ext = extension(files[i]);
                if (ext.equals("pdb")){
                    // make Dataset from PDB file and add to collection
                    status.setText("Reading PDB file " + filebase + " => calculating P(r) - please wait ~ 1 min");
                    bar.setStringPainted(false);
                    bar.setIndeterminate(true);
                    try {
                        PDBFile tempPDB = new PDBFile(files[i], qmax, exclude, workingDirectoryName);

                        int newIndex = targetCollection.getTotalDatasets();
                        targetCollection.createDataset(
                                tempPDB.getIcalc(),  //data
                                tempPDB.getError(),  //original
                                filebase,
                                true);

                        targetCollection.getDataset(newIndex).setIsPDB(tempPDB.getPrDistribution(), (int)tempPDB.getDmax(), tempPDB.getRg(), tempPDB.getIzero());
                    } catch (Exception ex) {
                        rejected++;
                        status.setText("Could not read PDB file " + files[i].getName() + ": " + ex.getMessage());
                        LogIt.log(Level.SEVERE, "Could not read PDB file " + files[i].getName() + ": " + ex.getMessage());
                    } finally {
                        bar.setIndeterminate(false);
                        bar.setStringPainted(true);
                    }
                } else {
                    if (!addToCollection(loadDroppedFile(files[i], targetCollection.getTotalDatasets()))) {
                        rejected++;
                    }
                }
            }
            publish(i + 1);
        }

        updateAbstractModel();

        //update collection ids
        int total = targetCollection.getTotalDatasets();
        for(int h=0; h<total; h++){
            targetCollection.getDataset(h).setId(h);
        }

        if (rejected > 0) {
            final String message = rejected + " file" + (rejected == 1 ? " was" : "s were") + " skipped (see log)";
            SwingUtilities.invokeLater(() -> status.setText(message));
        }

        bar.setStringPainted(false);
        return null;
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        bar.setValue(i);
        super.process(chunks);
    }


    /**
     * The list model mirrors the whole collection, so it is rebuilt from scratch here
     * (callers no longer need to clear it first). Swing models are only changed on the
     * Event Dispatch Thread.
     */
    private void updateAbstractModel() throws InterruptedException {
        if (modelToUpdate instanceof AnalysisModel){
            // AnalysisModel is updated elsewhere
        } else if (sampleBufferFilesModel != null){

            // snapshot the entries off the EDT, then swap them into the model on the EDT
            final List<SampleBufferElement> elements = new ArrayList<>();
            for(int i=0; i< targetCollection.getTotalDatasets(); i++){
                String name = targetCollection.getDataset(i).getFileName() + "_" + i;
                Color tempColor = targetCollection.getDataset(i).getColor();
                elements.add(new SampleBufferElement(name, i, tempColor, targetCollection.getDataset(i)));
            }

            Runnable rebuild = () -> {
                sampleBufferFilesModel.clear();
                for (SampleBufferElement element : elements) {
                    sampleBufferFilesModel.addElement(element);
                }
            };

            if (SwingUtilities.isEventDispatchThread()) {
                rebuild.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(rebuild);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException("Could not update the file list", e.getCause());
                }
            }
        }
    }


    /** @return true if the file was added to the collection */
    private boolean addToCollection(LoadedFile tempFile){

        if (tempFile == null) {
            return false; // could not be read; loadDroppedFile has already logged why
        }

        if (tempFile.isValid()){ // do not add if it contains null intensities

            if (shortened){
                targetCollection.createBareDataset(tempFile);
            } else {
                targetCollection.createDataset(tempFile, doGuinier);
            }

            if (tempFile.hasJson()){
                targetCollection.getLast().setSasObject(tempFile.getJSONString());
            }
            return true;

        } else {
            LogIt.log(Level.WARNING, "Rejecting File, contains Null Intensities: " + tempFile.filebase);
            return false;
        }
    }


    public File[] finder(String dirName){
        File dir = new File(dirName);
        File[] found = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                return filename.toLowerCase(Locale.ROOT).endsWith(".dat");
            }
        });
        return (found != null) ? found : new File[0]; // null if the folder can't be read
    }

    private LoadedFile loadDroppedFile(File file, int assignedfileIndex){

        String ext = extension(file);

        try {
            if (ext.equals("brml")) {
                status.setText("Bruker .brml  file detected ");
                File tempFile = Bruker.makeTempDataFile(file, workingDirectoryName);
                return new LoadedFile(tempFile, assignedfileIndex, convertToAng);

            } else if (ext.equals("int") || ext.equals("dat") || ext.equals("fit") || ext.equals("adat") || ext.equals("csv")) {
                return new LoadedFile(file, assignedfileIndex, convertToAng);
            } else {
                throw new Exception("Incorrect file format: Use either brml, dat, csv, Adat, or Bdat file formats: " + file.getName());
            }

        } catch (Exception ex) {
            status.setText(ex.getMessage());
            LogIt.log(Level.SEVERE, ex.getMessage());
        }
        return null;
    }

    /** Lower-case text after the last dot, or "" if there is none. */
    private static String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(0, dot) : name;
    }

    public void setPDBParams(boolean exclude, double qmax){
        this.exclude = exclude;
        this.qmax = qmax;
    }
}
