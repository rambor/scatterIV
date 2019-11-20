package version4.Modeller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by robertrambo on 02/02/2016.
 */
public class Supcomb implements Runnable {

    private String workingDirectory;
    int isWindows;
    private String atsasDir;
    DammiNFManager currentManager;
    private File pdbModel;

    public Supcomb(String s,  String atsasDir, DammiNFManager object, File targetFile){
        this.workingDirectory = s;
        this.atsasDir = atsasDir;
        currentManager = object;
        pdbModel = targetFile;

        String os = System.getProperty("os.name");
        isWindows = os.indexOf("Win");
    }

    @Override
    public void run() {

        System.out.println(Thread.currentThread().getName()+ " Starting SUPCOMB ");
        currentManager.fireModelStarted(0);

        ProcessBuilder pr;

        String command = "";

        if (isWindows >= 0){
            command = atsasDir+"\\supcomb.exe";
            pr = new ProcessBuilder("cmd.exe", "/C", command + " "+ this.pdbModel.getName() +" damfilt.pdb");
        } else {
            command = atsasDir+"/supcomb";
            pr = new ProcessBuilder("bash", "-c", command + " "+ this.pdbModel.getName() +" damfilt.pdb");
        }

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

        currentManager.fireModelCompleted("Completed SUPCOMB", 0);
    }

}