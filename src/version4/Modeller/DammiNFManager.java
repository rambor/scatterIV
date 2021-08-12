package version4.Modeller;

import version4.MessageConsole;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by robertrambo on 02/02/2016.
 */
public class DammiNFManager {

    List listeners = new ArrayList();

    int numberOfCPUs=1;
    int numberOfRuns=3;
    String gnomFile;
    boolean isDammin;
    boolean damRefine = false;
    String atsasDir;
    String workingDir;
    DefaultListModel listModel;
    String symm;
    String mode="S";
    String damstartFile;
    private File supcombFile;

    public DammiNFManager(int numberOfCPUs, String filename, boolean ifDammin, int numberOfRuns, String symm, String atsasDir, String workingDir, DefaultListModel listModel, boolean fast, String damstartFile, boolean damStartStatus, boolean damRefine){
        this.numberOfCPUs = numberOfCPUs;
        this.numberOfRuns = numberOfRuns;
        this.gnomFile = filename;
        this.isDammin = ifDammin;
        this.atsasDir = atsasDir;
        this.workingDir = workingDir;
        this.listModel = listModel;
        this.symm = symm;
        this.damstartFile = damstartFile;

        supcombFile = new File("");
        System.out.println("File exists: " + supcombFile.exists());

        if (damRefine && damStartStatus && this.isDammin){
            this.damRefine = true;
        }

        if (fast){
            mode = "F";
        }

    }

    public void modelNow(JTextPane damTextPane)  {

        listModel.clear();
        // create dam directory
        File newDir;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");


        MessageConsole dx = new MessageConsole(damTextPane);
        dx.redirectOut();


        if (isDammin) {
            newDir = new File(workingDir + "/damn_"+symm);
        } else {
            newDir = new File(workingDir + "/damf_"+symm);
        }

        // throw excpetion if I can't make file

        if (!(newDir.exists() && newDir.isDirectory())) {
            boolean testDir = newDir.mkdir();
            if (!testDir){
                try {
                    throw new DamminRunException("CANNOT_CREATE_DAM_DIRECTORY " + workingDir + " " + testDir);
                } catch (DamminRunException e) {
                    System.out.println(e.getErrorMessage());
                    e.printStackTrace();
//                    info.redirectOut();
                    return;
                }
            }
        } else { // rename directory
            Date now = new Date();
            newDir.renameTo(new File(workingDir + "/damf_"+symm+"_"+sdf.format(now)));
            if (!newDir.mkdir()){
                try {
                    throw new DamminRunException("CANNOT_CREATE_DAM_DIRECTORY " + workingDir + "/damf_"+symm );
                } catch (DamminRunException e) {
                    System.out.println(e.getErrorMessage());
                    e.printStackTrace();
//                    info.redirectOut();
                    return;
                }
            }
        }


        //ExecutorService executor = Executors.newFixedThreadPool(numberOfCPUs);
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfCPUs);

        File selectedFile = new File(gnomFile);
        File newGnom = new File(newDir.toPath()+"/" + selectedFile.getName());

        try {
            Files.copy(selectedFile.toPath(), newGnom.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // if refining, copy damstart file to new directory also
            if (damRefine){
                Files.copy((new File(damstartFile)).toPath(), (new File(newDir.toPath()+"/damstart.pdb")).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {

            e.printStackTrace();
        }

        if (newGnom.exists()){
            damTextPane.setText("Starting Runs");

            for (int i=0; i < numberOfRuns; i++) {
                // create DAMMIN/F runs
                Runnable modeler = new DammiNF(newGnom.getAbsolutePath(), isDammin, atsasDir, newDir.getAbsolutePath(), i, symm, mode, this);
                if (i ==0){
                    executor.execute(modeler);
                } else {
                    executor.schedule(modeler, i*1000, TimeUnit.MILLISECONDS); // need a delay in execution so random seeds are different
                }
            }

            executor.shutdown();
            while (!executor.isTerminated()) {
            }

            System.out.println("Finished all Models");

        } else {
            try {
                throw new DamminRunException("CANNOT_FIND_GNOM_FILE");
            } catch (DamminRunException e) {
                System.out.println(e.getErrorMessage());
                e.printStackTrace();
//                info.redirectOut();
            }
        }

        // start averaging
        this.listModel.addElement("Starting Damaver, please wait");

        // damaver -a -s P1 *-1.pdb
        Runnable averager = new Damaver(newDir.getAbsolutePath(), atsasDir, symm, this);
        averager.run();

        // check if supcomb and model supplied
        if (this.supcombFile.exists()){
            File newSup = new File(newDir.toPath()+"/" +this.supcombFile.getName());
            try {
                Files.copy(this.supcombFile.toPath(), newSup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Runnable aligner = new Supcomb(newDir.getAbsolutePath(), atsasDir, this, newSup);
                aligner.run();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        info.redirectOut();
    }

    public void setSupcombFile(File file){
        this.supcombFile = file;
    }


    // update text area when thread completes
    public void fireModelCompleted(String threadName, int processID){
        System.out.println("Thread " + threadName + " completed. Ending run " + processID);
        this.listModel.addElement("Finished Run " + processID);
    }

    // update text area when thread completes
    public void fireModelStarted(int processID){
        this.listModel.addElement("Started Run " + processID);
    }
}
