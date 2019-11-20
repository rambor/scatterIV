package version4;

import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Locale;

public class ExportData extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField textField1;
    private XYSeriesCollection plottedData;
    private XYSeries singleXY, error;
    private String workingDirectoryName;
    private String prefix;

    public ExportData(XYSeriesCollection data, String dirname, String prefix) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        plottedData = data;
        workingDirectoryName = dirname;
        this.prefix = prefix;

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }


    public ExportData(XYSeries data, XYSeries error, String dirname, String prefix) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        singleXY = data;
        this.error = error;
        workingDirectoryName = dirname;
        this.prefix = prefix;

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOKWriteSingle();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private int getDigits(double qvalue) {
        String toText = Double.toString(qvalue);
        int integerPlaces = toText.indexOf('.');
        int decimalPlaces;

        String[] temp = toText.split("\\.0*");
        decimalPlaces = (temp.length == 2) ? temp[1].length() : (toText.length() - integerPlaces -1);

        return decimalPlaces;
    }


    /**
     *
     */
    private void onOKWriteSingle(){
        String myString = textField1.getText().replaceAll(" ", "_").toLowerCase();

        FileWriter fstream;
        int numberOfDigits;

        String base = myString+ "_"+(String)singleXY.getKey();
        int totalItems=singleXY.getItemCount();

        //estimate average number of digits to use
        numberOfDigits=0;
        for(int j=0; j < totalItems; j++){
            numberOfDigits += getDigits(singleXY.getX(j).doubleValue());
        }
        numberOfDigits /= totalItems;

        try{
            // Create file
            fstream = new FileWriter(workingDirectoryName+ "/" + base +"_" +prefix+ ".txt");
            BufferedWriter out = new BufferedWriter(fstream);
            out.write( String.format("# q Value Error %n"));

            for(int j=0; j < totalItems; j++){

                out.write( String.format("%s\t%s\t%s%n",
                        formattedQ(singleXY.getX(j).doubleValue(), numberOfDigits),
                        Constants.Scientific1dot5e2.format(singleXY.getY(j).doubleValue()),
                        Constants.Scientific1dot5e2.format(error.getY(j).doubleValue())
                ));
            }
            //Close the output stream
            out.close();
        }catch (Exception e){//Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }

        dispose();
    }


    private void onOK() {
        // add your code here
        // sanitize text file
        String myString = textField1.getText().replaceAll(" ", "_").toLowerCase();
        int totalinplot = plottedData.getSeriesCount();
        FileWriter fstream;
        int numberOfDigits;

        for (int i=0; i< totalinplot; i++){
            String base = myString+ "_"+(String)plottedData.getSeries(i).getKey();
            XYSeries tempSeries = plottedData.getSeries(i);
            int totalItems=tempSeries.getItemCount();

            //estimate average number of digits to use
            numberOfDigits=0;
            for(int j=0; j < totalItems; j++){
                numberOfDigits += getDigits(tempSeries.getX(j).doubleValue());
            }
            numberOfDigits /= totalItems;


            try{
                // Create file
                fstream = new FileWriter(workingDirectoryName+ "/" + base +"_" +prefix+ ".txt");
                BufferedWriter out = new BufferedWriter(fstream);
                for(int j=0; j < totalItems; j++){
                    //numberOfDigits = getDigits(tempSeries.getX(j).doubleValue());

                    out.write( String.format("%s\t%s%n",
                            formattedQ(tempSeries.getX(j).doubleValue(), numberOfDigits),
                            Constants.Scientific1dot5e2.format(tempSeries.getY(j).doubleValue())
                    ));
                }
                //Close the output stream
                out.close();
            }catch (Exception e){//Catch exception if any
                System.err.println("Error: " + e.getMessage());
            }
        }

        dispose();
    }

    private void onCancel() {
// add your code here if necessary
        dispose();
    }


    private String formattedQ(double qvalue, int numberOfDigits) {
        String numberToPrint ="";
        switch(numberOfDigits){
            case 7: numberToPrint = String.format(Locale.US, "%.6E", qvalue);
                break;
            case 8: numberToPrint = String.format(Locale.US, "%.7E", qvalue);
                break;
            case 9: numberToPrint = String.format(Locale.US, "%.8E", qvalue);
                break;
            case 10: numberToPrint = String.format(Locale.US, "%.9E", qvalue);
                break;
            case 11: numberToPrint = String.format(Locale.US,"%.10E", qvalue);
                break;
            case 12: numberToPrint = String.format(Locale.US, "%.11E", qvalue);
                break;
            case 13: numberToPrint = String.format(Locale.US, "%.12E", qvalue);
                break;
            case 14: numberToPrint = String.format(Locale.US, "%.13E", qvalue);
                break;
            default: numberToPrint = String.format(Locale.US,"%.6E", qvalue);
                break;
        }
        return numberToPrint;
    }
}
