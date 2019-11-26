package FileManager;


import version4.LogIt;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Created by robertrambo on 05/01/2016.
 */
public class DataLine {
    private double qvalue;
    private double intensity;
    private double error;
    private double qerror=0;
    private boolean isData=false;
    private Locale loc = Locale.getDefault(Locale.Category.FORMAT);
    private boolean isUSUK = false;
    //private Pattern dataFormat = Pattern.compile("(-?[0-9].[0-9]+[Ee][+-]?[0-9]+)|(-?[0-9]+.[0-9]+)");
    private Pattern dataFormat = Pattern.compile("(-?[0-9](.|,)[0-9]+[Ee][+-]?[0-9]+)|(-?[0-9]+(.|,)[0-9]+)");
    private Pattern separator = Pattern.compile("\\s|;|,\\s|,");
    /**
     * default for X-ray or Neutrons (3 columns for X-rays, 4 columns for Neutrons
     * from J. Doutch, SANS data is q, I, dI, dq -> d is error
     * @param line
     */
    public DataLine (String line) throws Exception {

        String newString = line.replaceAll( "[\\s\\t]+", " " );
        String trimmed = newString.trim();
        String[] row = separator.split(trimmed);

        // 0,001 1,01076E2 0,01 <- no periods
        // vs
        // 0.001,1.01076E2,0.01 <- mixed format with commas
        // vs
        // 0.001 1.101762E2 0.01

        if (loc.toString().equals("en_GB") || loc.toString().equals("en_US")){
            isUSUK = true;
        }


        if (checkline(line)){
            if (row[0].contains(",") && row[1].contains(",")){ // convert
                LogIt.log(Level.INFO,"Number contains a comma in first column, convert format :: " + line );
                //System.out.println("Number contains a comma in first column, convert format " + line);

                qvalue = convertToUS(row[0]);
                intensity = convertToUS(row[1]);
                if ((row.length == 3 && isNumeric(row[2])) || (row.length == 4 && isNumeric(row[2]))) {
                    error = convertToUS(row[2]);
                } else {
                    error = 1;
                }

            } else {
                qvalue = Double.parseDouble(row[0]);
                intensity = Double.parseDouble(row[1]);
                if ((row.length == 3 && isNumeric(row[2])) || (row.length == 4 && isNumeric(row[2]))) {
                    error = Double.parseDouble(row[2]);
                } else {
                    error = 1;
                }

                if ((row.length == 4 && isNumeric(row[3]))){ // if 4 columns, assume SANS and 4th column is q_error
                    qerror = Double.parseDouble(row[3]);
                }
            }
            isData = true;
        } else {
            LogIt.log(Level.WARNING, "NON DATA FORMAT :: " + line);
        }
    }


    public DataLine (String line, String type){
        String newString = line.replaceAll( "[\\s\\t]+", " " );
        String trimmed = newString.trim();
        String[] row = separator.split(trimmed);

        if (type.equals("fit")){ // switch columns

        }

    }

    /**
     * Generic constructor for supplying parameters directly, usually reserve (0,0,0,false) as special case
     * @param nqvalue
     * @param nintensity
     * @param nerror
     * @param nisData
     */
    public DataLine (double nqvalue, double nintensity, double nerror, boolean nisData){
        qvalue = nqvalue;
        intensity = nintensity;
        error = nerror;
        isData = nisData;
    }


    private boolean checkline(String line){
        boolean test = false;

        String newString = line.replaceAll( "[\\s\\t]+", " " );
        String trimmed = newString.trim();
        String[] row = trimmed.split("\\s|;|,\\s|,"); // CSV files could have a ", "

        if (row.length > 1 && !dataFormat.matcher(row[1]).matches()){
            return false;
        } else if ((!trimmed.contains("#") &&
                (row[0].length() > 2) &&
                !row[0].matches("^[()\"#:_\\/\\'a-zA-Z$%*!]+$") &&
                dataFormat.matcher(row[0]).matches() &&
                dataFormat.matcher(row[1]).matches() &&
                !isZero(row[0]) &&                               // no zero q values
//                !isZero(row[1]) &&                               // no zero I(q) values
                isNumeric(row[0]) &&                             // check that value can be parsed as Double
                isNumeric(row[1])                                // check that value can be parsed as Double
        )) {
            return true;
        }

        return test;
    }


    /**
     * new to test if the input string is a 0
     * @param str
     * @return
     */
    private boolean isZero(String str){
        // 0.000 or 0,000 or 0.00E0
        if (hasOnlyComma(str) && !isUSUK){

            if (convertToUS(str) <= 0){

                return true;
            }
        } else if (isUSUK) {

            if (Float.parseFloat(str) == 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * convert comma delimited decimal to US standard
     * @param str
     * @return
     */
    private double convertToUS(String str){
        //NumberFormat format = NumberFormat.getInstance(new Locale("es", "ES"));
        NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
        Number number = null;
        // data may contain only a decimal point eventhough it is on non-ideal key board

        try {
            number = format.parse(str);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        System.out.println("FormatParser : " + str + " => " + number + " parsed => " + number.doubleValue());
        return number.doubleValue();
    }


    private boolean isNumeric(String str) {

        if (hasOnlyComma(str) && !isUSUK){
            NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
            Number number = null;
            try {
                number = format.parse(str);
            } catch (ParseException e) {
                return false;
            }
        } else {
            try {
                double d = Double.parseDouble(str);
            } catch(NumberFormatException nfe) {
                System.out.println("FAILED " + str);
                return false;
            }
        }

        return true;
    }

    private boolean hasOnlyComma(String str) {

        if (str.contains(",") && !str.contains(".")) {
            return true;
        }
        return false;
    }




    //get Methods
    public double getq() {
        return qvalue;
    }

    public double getI() {
        return intensity;
    }

    public double getE() {
        return error;
    }

    public void setE(double nerror) {
        error = nerror;
    }

    public boolean getTest(){
        return isData;
    }

    public double getQerror(){
        return qerror;
    }

    public boolean getIsData() { return isData;}
}
