package version4.Modeller;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by robertrambo on 02/02/2016.
 */
public class DammiNF implements Runnable {

    private String filename;
    private String workingDirectory;
    private String atsasDir;
    private String command;
    private String symm;
    private String mode;
    boolean useDammin = false;
    int isWindows;
    int processNumber;
    DammiNFManager currentManager;

    public DammiNF(String s, boolean dammin, String atsasDir, String workingDirectory, int index, String symm, String mode, DammiNFManager object){
        this.filename=s;
        this.useDammin = dammin;
        this.atsasDir = atsasDir;
        this.workingDirectory = workingDirectory;
        this.symm = symm;
        processNumber = index+1;
        currentManager = object;

        String os = System.getProperty("os.name");

        isWindows = os.indexOf("Win");
        System.out.println("OS: " + os + " " + isWindows);
        this.mode = mode;
    }

    @Override
    public void run() {

        System.out.println(Thread.currentThread().getName()+ " Starting run " + processNumber);
        currentManager.fireModelStarted(processNumber);

        ProcessBuilder pr;
        String commandLine;

        if (useDammin){
            commandLine = processDammin();
            //pr = new ProcessBuilder(this.command +" "+ filename +  " /lo run_"+processNumber + " /sy " + symm + " /mo " +mode);
            //create answer file

            ArrayList<String> answers = new ArrayList<String>(9);
            int limit = (currentManager.damRefine) ? 10 : 9;

            for (int i=0; i<limit; i++){
                if (i==0){
                    answers.add(mode+"\n");
                } else if (i==1){
                    answers.add("run_"+processNumber+"\n");
                } else if (i==2){
                    answers.add(filename+"\n");
                } else if ((i==6) && (currentManager.damRefine)){
                    answers.add("damstart.pdb"+"\n");
                } else if (i==7){
                    answers.add(symm+"\n");
                } else {
                    answers.add("\n");
                }
            }

            File anstemp = new File(workingDirectory+"/run_"+processNumber+".ans");

            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(anstemp));
                for (int i = 0; i < limit; i++) {
                    out.write(answers.get(i));
                }
                out.close();
            } catch (IOException e) {

            }

            if (isWindows >= 0) {
                pr = new ProcessBuilder("cmd.exe", "/C", commandLine + " < " + workingDirectory + "/run_" + processNumber + ".ans");
            } else {
                pr = new ProcessBuilder("bash", "-c", commandLine + " < " + workingDirectory + "/run_" + processNumber + ".ans");
            }


        } else {
            commandLine = processDammif();
            pr = new ProcessBuilder(commandLine, "-m", mode, "-p", "run_"+processNumber, "-s", symm, filename);
        }

        System.out.println("DAMMIN/F COMMAND " + commandLine);

        pr.directory(new File(workingDirectory));
        Process ps = null;

        try {
            ps = pr.start();

            BufferedReader input = new BufferedReader(new InputStreamReader(ps.getInputStream()));
            String line=null;

            while((line=input.readLine()) != null) {
                System.out.println(line);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

        currentManager.fireModelCompleted(Thread.currentThread().getName(), processNumber);
        //System.out.println(Thread.currentThread().getName()+ " Ending run " + processNumber);
    }


    private String processDammin(){
        command = "";

        if (isWindows >= 0){
            command = atsasDir+"/dammin.exe";
            //command = "C:\\ATSAS\\dammin.exe";
        } else {
            command = atsasDir+"/dammin";
        }
        return command;
    }

    private String processDammif(){
        command = "";

        if (isWindows >= 0){
            command = atsasDir+"/dammif.exe";
        } else {
            command = atsasDir+"/dammif";
        }

        return command;
    }


}
