package FileManager;

import org.jfree.data.xy.XYSeries;
import version4.LogIt;

import java.io.*;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class CSVFile {
    private Pattern dataFormat = Pattern.compile("(-?[0-9](.|,)[0-9]+[Ee][+-]?[0-9]+)|(-?[0-9]+(.|,)[0-9]+)");
    private boolean isUSUK = false;
    private XYSeries data;
    private Locale loc = Locale.getDefault(Locale.Category.FORMAT);
    private String filename;

    public CSVFile(File file){

        if (loc.toString().equals("en_GB") || loc.toString().equals("en_US")){
            isUSUK = true;
        }

        filename = file.getName();

        data = new XYSeries("data");
        Pattern separator = Pattern.compile("\\s|;|,\\s|,");

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String strLine;

            while ((strLine = br.readLine()) != null) {
                if (checkline(strLine)){
                    String newString = strLine.replaceAll( "[\\s\\t]+", " " );
                    String trimmed = newString.trim();
                    String[] row = separator.split(trimmed);

                    if (row[0].contains(",") && row[1].contains(",")){ // convert
                        LogIt.log(Level.INFO,"Number contains a comma in first column, convert format :: " + strLine );
                        //System.out.println("Number contains a comma in first column, convert format " + line);
                        data.add(convertToUS(row[0]), convertToUS(row[1]));
                    } else {
                        data.add(Double.parseDouble(row[0]), Double.parseDouble(row[1]));
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    private boolean checkline(String line){

        String newString = line.replaceAll( "[\\s\\t]+", " " );
        String trimmed = newString.trim();
        String[] row = trimmed.split("\\s|;|,\\s|,"); // CSV files could have a ", "

        if ( row.length > 1 && (!dataFormat.matcher(row[1]).matches() )) {
            return false;
        } else if ((!trimmed.contains("#") &&
                (row[0].length() > 2) &&
                !row[0].matches("^[()\"#:_\\/\\'a-zA-Z$%*!]+$") &&
                dataFormat.matcher(row[0]).matches() &&
                dataFormat.matcher(row[1]).matches() &&
                row[1].chars().filter(ch -> ch == '.').count() < 2 && // only 1 or 0 periods
                isNumeric(row[0]) &&                             // check that value can be parsed as Double
                isNumeric(row[1])                                // check that value can be parsed as Double
        )) {
            return true;
        }

        return false;
    }



    private boolean isNumeric(String str) {

        if (hasOnlyComma(str) && !isUSUK){
            NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
            try {
                format.parse(str);
            } catch (ParseException e) {
                return false;
            }
        } else {
            try {
                Double.parseDouble(str);
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

    public XYSeries getData(){
        return data;
    }

    public String getFilename(){ return filename;}
}
