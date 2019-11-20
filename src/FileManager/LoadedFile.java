package FileManager;

/**
 * Created by robertrambo on 05/01/2016.
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfree.data.general.SeriesException;
import org.jfree.data.xy.XYSeries;
import version4.LogIt;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;


/**
 * Read data from file, must be text file with 3 columns
 * LoadedFile parses input file and builds XY series for both data and error
 * LoadedFile decides on if input file is
 * 1. *.brml
 * 2. *.dat
 * 3. *.cif
 * And decides on how to load the data into the XYSeries
 * If data is SAXS q, I, error
 * If data is SANS q, error, I, error
 *
 * @author R. Rambo
 */
public class LoadedFile {

    public XYSeries allData;
    public XYSeries allDataError;
    private String ext;
    public String filebase;
    private Pattern dataFormat = Pattern.compile("(-?[0-9].[0-9]+[Ee][+-]?[0-9]+)|(-?[0-9]+.[0-9]+)");

    private String json ="";
    private boolean jsonPresent = false;

    private Locale loc = Locale.getDefault(Locale.Category.FORMAT);
    private boolean isUSUK = false;
    private boolean validFile = true;
    private DecimalFormat df = new DecimalFormat("#.#", new DecimalFormatSymbols(Locale.US));
    // add parameters for reading header from txt file
    // REMARK   INFO

    //Constructor
    public LoadedFile(File file, int index, boolean convert) throws Exception {

        if (loc.toString().equals("en_GB") || loc.toString().equals("en_US")){
            isUSUK = true;
        }

        //System.out.println("Default location " + Locale.getDefault(Locale.Category.FORMAT) + " isUSUK " + isUSUK);
        // get file base and extension
        String[] filename = file.getName().split("\\.(?=[^\\.]+$)");
        filebase = filename[0];
        ext = filename[1];
        String keyName = Integer.toString(index) + filebase; // helps keep file names unique so we can load same file multiple times

        allData = new XYSeries(keyName, false, false);
        allDataError = new XYSeries(keyName, false, false);
        double tempQValue;

        try {

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            //BufferedReader br = new BufferedReader(new FileReader(file));
            long filesize = file.length();

            if ( filesize > 0 && (ext.equals("dat") || ext.equals("int") || ext.equals("txt") || ext.equals("csv") || ext.equals("fit")) ) { //regular 3 column file space or tab delimited
                String strLine;
                //Read file line-by-line
                try {
                    DataLine dataPoints;
                    while ((strLine = br.readLine()) != null) {
                        try {
                            dataPoints = new DataLine(strLine);
                            if (dataPoints.getIsData()) {
                                tempQValue = (convert) ? dataPoints.getq()/10 : dataPoints.getq();
                                allData.add(tempQValue, dataPoints.getI() );
                                allDataError.add(tempQValue, dataPoints.getE() );
                            } else {
                                try{
                                    /*
                                     * check if line is a JSON string
                                     */
                                    ObjectMapper objectMapper = new ObjectMapper();
                                    JsonNode jsonNode = objectMapper.readTree(strLine.trim());
                                    jsonNode.isMissingNode();
                                    jsonPresent = true;
                                    json = strLine.trim();

                                } catch (JsonProcessingException ex){
                                    LogIt.log(Level.INFO, ex.getMessage());
                                }
                            }
                        } catch (Exception e) {

                            LogIt.log(Level.INFO, e.getMessage());
                        }
                    }

                    if (ext.equals("fit") && allData.getItemCount() > 0 ){
                        int totalIn = allData.getItemCount();
                        for(int i=0; i<totalIn; i++){
                            double actualValue = allDataError.getY(i).doubleValue();
                            double diff = actualValue - allData.getY(i).doubleValue();
                            allData.updateByIndex(i,actualValue);
                            allDataError.updateByIndex(i, Math.abs(diff)); // use the residual of the fit as the errors
                        }
                    }

                } catch (IOException ex) {
                    LogIt.log(Level.INFO, "File Index out of bounds");
//                    System.err.println("File Index out of bounds");
                }
            } else if (filesize > 0){
                validFile=false;
                String message = "Incorrect file extension : " + filename;
                br.close();
                throw new IllegalArgumentException(message);
            } else {
                br.close();
                validFile=false;
                throw new IllegalArgumentException("File Empty");
            }
            // might have a cansas format, open file and read contents
            br.close();

            if (allData.getItemCount() < 2){
                throw new IllegalArgumentException("File Empty :: nothing loaded ");
            }

        } catch (FileNotFoundException ex) {
            validFile=false;
            //System.err.println("Error: " + ex.getMessage());
            LogIt.log(Level.WARNING, ex.getMessage());
        }
    }

    public boolean isValid(){ return validFile;}

    public boolean hasJson(){ return jsonPresent;}
    public String getJSONString() { return json;}


}

