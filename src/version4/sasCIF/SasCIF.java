package version4.sasCIF;

import version4.Collection;
import version4.Dataset;

import java.io.BufferedWriter;
import java.io.FileWriter;

public class SasCIF {

    String filename;
    Collection collection;
    Dataset dataset;
    Boolean includeRefined = false;

    /**
     * Create a SASCIF file and write
     * Use if including P(r) distribution in SASCIF file
     *
     * @param filename
     * @param dataset
     * @param includeRefined
     */
    public SasCIF(String filename, Dataset dataset, Boolean includeRefined){

        this.filename = filename;
        this.dataset = dataset;
        this.includeRefined = includeRefined;

        FileWriter fstream;
        int resultId = 1;

        try{ // create P(r) file
            // Create file
            fstream = new FileWriter(filename);
            BufferedWriter out = new BufferedWriter(fstream);

            SasDetails details = new SasDetails(dataset);
            out.write(details.getText());

            SasResult results = new SasResult(dataset);
            out.write(results.getTextForOutput(resultId));

            SasPofr pofr = new SasPofr(dataset);
            out.write(pofr.getTextOfFittedDataForOutput(resultId));

            SasScan sasscan = new SasScan(dataset, resultId);
            //out.write();

            SasIntensities iofq = new SasIntensities(dataset);
            out.write(iofq.getTextOfFittedDataForOutput(includeRefined));
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }

    }


    /**
     * Use for averaging
     *
     * @param collection
     */
    public SasCIF(Collection collection){

    }



}
