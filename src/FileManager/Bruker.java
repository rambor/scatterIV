package FileManager;


import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by robertrambo on 05/01/2016.
 */

public class Bruker {

    public static DecimalFormat scientific1dot5e2;

    {
        scientific1dot5e2 = new DecimalFormat("0.00000E00");
    }

    public static File makeTempDataFile(File file, String cwd){
        File temp = null;

        double averageWavelength=0;
        double twoPi=2.0*Math.PI;
        float tempWave;

        String[] currentFile = file.getName().split("\\.(?=[^\\.]+$)");
        Pattern rawDataText = Pattern.compile("RawData",  Pattern.CASE_INSENSITIVE);
        Pattern datum = Pattern.compile("Datum>");
        Pattern wavelength = Pattern.compile("\\<WaveLengthAverage");
        String filebase = currentFile[0];
        String ext = currentFile[1];
        String brukerLine;
        Enumeration entries;

        ZipFile zipFile;
        try {
            zipFile = new ZipFile(file);
            entries = zipFile.entries();

            InputStream input = null;
            OutputStream outputStream = null;

            while(entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry)entries.nextElement();

                ArrayList<Double> q_value = new ArrayList<Double>();
                ArrayList<Double> iq_value = new ArrayList<Double>();
                ArrayList<Double>  e_value = new ArrayList<Double>();

                if (rawDataText.matcher(entry.getName()).find()){
                    input = zipFile.getInputStream(entry);
                    // convert to file and make a LoadedFile object
                    // write the inputStream to a FileOutputStream
                    outputStream = new FileOutputStream(new File("tempBruker.brml"));
                    BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));
                    // need wavelength and theta values to calculate q
                    while ((brukerLine = br.readLine()) != null) {
                        if (datum.matcher(brukerLine).find()) {
                            String[] columns = brukerLine.replaceAll("\\s|\\t+","").split(",|<Datum>|</Datum>");
                            q_value.add(Double.parseDouble(columns[4]));
                            iq_value.add(Double.parseDouble(columns[5]));
                            e_value.add(1.0d);
                        }
                        if (wavelength.matcher(brukerLine).find()){
                            tempWave = Float.parseFloat(brukerLine.split("[<A-Za-z=\\s\"]+")[2]);
                            averageWavelength = (tempWave > 0) ? twoPi/tempWave: 0;
                        }
                    }

                    for (int q = 0; q < q_value.size(); q++){
                        q_value.set(q, q_value.get(q)*averageWavelength);
                    }
                    //write to file and then call loadedfile
                    String tempBrukerName = entry.getName().split("/")[1];
                    String outFileName = tempBrukerName.replaceAll(".xml", ".dat");

                    FileWriter fw = new FileWriter(cwd+ "/"+outFileName);
                    BufferedWriter out = new BufferedWriter(fw);

                    for (int n=0; n < q_value.size(); n++) {
                        out.write(scientific1dot5e2.format(q_value.get(n)) + " " + scientific1dot5e2.format(iq_value.get(n)) + " " + scientific1dot5e2.format(e_value.get(n)) + "\n");
                    }

                    //close the output stream
                    out.close();
                    temp = new File(cwd+ "/"+outFileName);


                } // end if
            } // end while

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return temp;
    }
}