package version4.Modeller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by robertrambo on 02/02/2016.
 */
public class Damaver implements Runnable {


    private String workingDirectory;
    int isWindows;
    private String atsasDir;
    private String symm;
    DammiNFManager currentManager;

    public Damaver(String s,  String atsasDir, String symm, DammiNFManager object){
        this.workingDirectory = s;
        this.atsasDir = atsasDir;
        this.symm = symm;
        currentManager = object;

        String os = System.getProperty("os.name");
        isWindows = os.indexOf("Win");
    }

    @Override
    public void run() {

        System.out.println(Thread.currentThread().getName()+ " Starting DAMAVER ");
        currentManager.fireModelStarted(0);

        ProcessBuilder pr;

        String command = "";

        if (isWindows >= 0){
           // command = atsasDir+"\\damaver.exe";
            command = atsasDir+"\\damaver.exe";
            pr = new ProcessBuilder("cmd.exe", "/C", command + " -s " +  symm +  " -a *-1.pdb");
        } else {
            command = atsasDir+"/damaver";
            pr = new ProcessBuilder("bash", "-c", command + " -s " + symm + " -a *-1.pdb");

        }

        System.out.println("DAMAVER COMMAND " + command);
        //damaver -a -s P1 *-1.pdb
        System.out.println("FROM COMMAND LIST " + pr.command().get(0));
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

        currentManager.fireModelCompleted("Completed DAMAVER", 0);
    }

}