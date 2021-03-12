package version4;

import FileManager.WorkingDirectory;
import org.jfree.data.xy.XYSeries;
import version4.ConvolutionFunctions.Derivative;
import version4.MMass.CalibrationPeak;
import version4.tableModels.SECSAXSUVSignalDDTW;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class MSSX {


    private JPanel contentPane;
    private JButton SECSASFileButton;
    private JButton SECSASFileButton1;
    private JButton UVTraceFileButton;
    private JButton UVTraceFileButton1;
    private JButton calibrationAlignButton;
    private JButton setBaselineButton;
    private JButton loadAndPlotCalibrationButton;
    private JButton loadAndPlotSampleButton;
    private JButton setBaselineButton1;
    private JButton applyCalibrationButton;
    private JButton calibrateButton;
    private JTextField offsetField;
    private JTextField timeField;
    private JTextField mixingField;
    private JLabel calSecSASField;
    private JLabel calUVTraceField;
    private JPanel tracesPanel;
    private JPanel dataPanel;
    private JPanel sampleSECSAXSPanel;
    private JLabel uvTraceSampleLabel;
    private JLabel sampleSECSAXSLabel;
    private JTextField exposureTimeField;
    private JLabel exposureTime;
    private JTextField deadTimeField;
    private JTextField frameLowerField;
    private JTextField frameUpperField;
    private JCheckBox convertExposureDeadTimesCheckBox;
    private JCheckBox useDDTWCheckBox;
    private JLabel mainStatus;
    private JProgressBar progressBar;
    private WorkingDirectory workingDirectory;
    private SASSignalTrace calibrationData;
    private File secFileToLoad;
    private File csvFileToLoad;

    private ArrayList<SASSignalTrace> sasSignalAndTraces;

    public MSSX(JLabel status, JProgressBar mainProgressBar, WorkingDirectory workingDirectory) {

        mainStatus = status;
        progressBar = mainProgressBar;
        this.workingDirectory = workingDirectory;

        sasSignalAndTraces = new ArrayList<>();
        sasSignalAndTraces.add(new SASSignalTrace("Calibration"));
        calibrationData = sasSignalAndTraces.get(0);

        MSSXPlot.getInstance().plot(this.workingDirectory, tracesPanel);
        MSSXPlot.getInstance().setLabelsToUpdate(frameLowerField, frameUpperField);

        SECSASFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JFileChooser fc = new JFileChooser(workingDirectory.getWorkingDirectory());
                fc.setMultiSelectionEnabled(false);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION){
                    File file = fc.getSelectedFile();
                    String[] filename = file.getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    status.setText("Selecting file :: " + filename[0]);

                    if (ext.equals("sec")){
                        /*
                         * validate file
                         * grab JSON
                         * load signals and associated graphs
                         */
                        try {
                            calibrationData.setSECFile(file);
                            workingDirectory.setWorkingDirectory(fc.getCurrentDirectory().getAbsolutePath());
                            calSecSASField.setText(filename[0]);
                        } catch (IOException ee) {
                            ee.printStackTrace();
                        }
                    } else {
                        Toolkit.getDefaultToolkit().beep();
                        status.setText("Select *.sec file please!");
                    }
                }
            }
        });

        UVTraceFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(workingDirectory.getWorkingDirectory());
                fc.setMultiSelectionEnabled(false);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION){
                    File file = fc.getSelectedFile();
                    String[] filename = file.getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    status.setText("Selecting file :: " + filename[0]);

                    if (ext.equals("csv") || ext.equals("txt")) {
                        /*
                         * validate file
                         * grab JSON
                         * load signals and associated graphs
                         */
                        calibrationData.setAbsorbanceTrace(file);
                        workingDirectory.setWorkingDirectory(fc.getCurrentDirectory().getAbsolutePath());
                        calUVTraceField.setText(filename[0]);
                    } else {
                        Toolkit.getDefaultToolkit().beep();
                        status.setText("Select *.sec file please!");
                    }
                }
            }
        });


        SECSASFileButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(workingDirectory.getWorkingDirectory());
                fc.setMultiSelectionEnabled(false);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION){
                    File file = fc.getSelectedFile();
                    String[] filename = file.getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    status.setText("Selecting file :: " + filename[0]);

                    if (ext.equals("sec")){
                        /*
                         * validate file
                         * grab JSON
                         * load signals and associated graphs
                         */
                        secFileToLoad = fc.getSelectedFile();
                        sampleSECSAXSLabel.setText(filename[0]);
                    } else {
                        Toolkit.getDefaultToolkit().beep();
                        status.setText("Select *.sec file please!");
                    }
                }
            }
        });

        UVTraceFileButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser(workingDirectory.getWorkingDirectory());
                fc.setMultiSelectionEnabled(false);
                int option = fc.showOpenDialog(contentPane);

                if(option == JFileChooser.CANCEL_OPTION) {
                    status.setText("Nothing selected");
                    return;
                }

                if(option == JFileChooser.APPROVE_OPTION){
                    File file = fc.getSelectedFile();
                    String[] filename = file.getName().split("\\.(?=[^\\.]+$)");
                    String ext = filename[1];

                    status.setText("Selecting file :: " + filename[0]);

                    if (ext.equals("csv") || ext.equals("txt")){
                        /*
                         * validate file
                         * grab JSON
                         * load signals and associated graphs
                         */
                        csvFileToLoad = fc.getSelectedFile();
                        uvTraceSampleLabel.setText(filename[0]);
                    } else {
                        Toolkit.getDefaultToolkit().beep();
                        status.setText("Select *.csv or txt file please!");
                    }
                }
            }
        });



        loadAndPlotSampleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (csvFileToLoad.exists() && secFileToLoad.exists()){
                    String[] filename = csvFileToLoad.getName().split("\\.(?=[^\\.]+$)");
                    String csvname = filename[0];
                    filename = secFileToLoad.getName().split("\\.(?=[^\\.]+$)");

                    sasSignalAndTraces.add(new SASSignalTrace(filename + "_" + csvname));
                    SASSignalTrace temp = sasSignalAndTraces.get( sasSignalAndTraces.size()-1);

                    try {
                        temp.setSECFile(secFileToLoad);
                    } catch (IOException ex) {
                        status.setText("improper SEC FILE");
                        ex.printStackTrace();
                    }

                    temp.setAbsorbanceTrace(csvFileToLoad);
                    // extract data for plot


                }
            }
        });

        loadAndPlotCalibrationButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MSSXPlot mssxPlot = MSSXPlot.getInstance();
                double exptime = Double.parseDouble(exposureTimeField.getText());
                double deadtime = Double.parseDouble(deadTimeField.getText());

                if (convertExposureDeadTimesCheckBox.isSelected()){ // in seconds, convert to minutes
                    exptime = exptime/60.0;
                    deadtime = deadtime/60.0;
                }


                calibrationData.changeSASSignalDomain(exptime, deadtime);
                calibrationData.alignTraces();
                mssxPlot.setCalibrationDataSets(calibrationData.getSecSignalSeries(), calibrationData.getAbsorbanceTrace());
            }
        });



        setBaselineButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MSSXPlot tempPlot = MSSXPlot.getInstance();

                if ((tempPlot.getSelectedBaseLineStart() ==  tempPlot.getSelectedBaseLineEnd()) ||  (tempPlot.getSelectedBaseLineStart() > tempPlot.getSelectedBaseLineEnd()) ){
                    status.setText("Please select regions for baseline");
                    Toolkit.getDefaultToolkit().beep();
                } else {
                    calibrationData.alignBaseLines(tempPlot.getSelectedBaseLineStart(), tempPlot.getSelectedBaseLineEnd());
                }
            }
        });


        calibrateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // interpolate UV values to observed SAXS time points
                status.setText("Interpolating absorbance values to SEC-SAS domain");

//                if (Double.parseDouble(calibrationMassLabel.getText()) < 1){
//                    status.setText("Calibration Mass needs to be greater than 1 kDa");
//                    Toolkit.getDefaultToolkit().beep();
//                    return;
//                }
                new Derivative(calibrationData.getSecSignalSeries(), calibrationData.getAbsorbanceTrace(), calibrationData.getSECSAXSFileName(), calibrationData.getAbsorbanceSignalFileName());

                calibrationData.create_interpolation_set();

                double lowerLimit = Double.parseDouble(frameLowerField.getText());
                double upperLimit = Double.parseDouble(frameUpperField.getText());

                if (lowerLimit > 0 && lowerLimit < upperLimit){
                    XYSeries cal = calibrationData.getSecSignalSeries();
                    int total = cal.getItemCount();
                    int lowerIndex = 0;
                    for (int i=0; i<total; i++){
                        if (cal.getX(i).doubleValue() > lowerLimit){
                            lowerIndex = i-1;
                            break;
                        }
                    }

                    int upperIndex = total-1;
                    for (int i=upperIndex; i>1; i--){
                        if (cal.getX(i).doubleValue() < upperLimit){
                            upperIndex = i+1;
                            break;
                        }
                    }



                    SECSAXSUVSignalDDTW ccc = new SECSAXSUVSignalDDTW(calibrationData.getSecSignalSeries(), calibrationData.getInterpolatedAbsorbanceTrace(), 0, 0);
                    ccc.calculateTimeWarp();

//                    SECSaxsUvSignalCorrector corrector = new SECSaxsUvSignalCorrector(
//                            calibrationData.getSecSignalSeries(), // target
//                           // calibrationData.getAbsorbanceSignalSeries(),
//                            calibrationData.getInterpolatedAbsorbanceTrace(), // UV signal
//                            0.15,
//                            0.01,
//                            lowerIndex,
//                            upperIndex);
//
//                    corrector.convolveInputDataSets();

                    // update plotted data
//                    MSSXPlot mssxPlot = MSSXPlot.getInstance();
//                    mssxPlot.setCalibrationDataSets(calibrationData.getSecSignalSeries(), corrector.getConvolvedSignal());
                    //
                    calibrationData.normalizeAvailableIzeros(ccc.getAssigned_absorbances(), ccc.getIndices_in_use());

                    // create calibration panel, users selects peak and assigns molecular weight
//                    calibrationData.normalizeAvailableIzeros(corrector.getAmplitude(), corrector.getConvolvedSignal(), corrector.getRemapped_indices());

                    // open a popup window showing peak with I-zero correct values
                    new CalibrationPeak(calibrationData);

                } else {

                }

            }
        });

            //frameLowerField.setInputVerifier(new PassVerifier());

frameLowerField.getDocument().addDocumentListener(new DocumentListener() {
    @Override
    public void insertUpdate(DocumentEvent e) {
        checkIt();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        checkIt();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        checkIt();
    }

    public void checkIt(){

        if (frameUpperField.getText().length() > 0 && frameLowerField.getText().length() > 0) {
            Runnable doAssist = new Runnable(){
                @Override
                public void run(){
                    double upper = Double.parseDouble(frameUpperField.getText());
                    double lower = Double.parseDouble( frameLowerField.getText() );
                    if (lower < upper){
                        MSSXPlot.getInstance().updateMarkers(lower, upper);
                    }
                }
            };
            SwingUtilities.invokeLater(doAssist);
        } else {
            status.setText("Invalid range, please check your numbers");
            Toolkit.getDefaultToolkit().beep();
        }
    }

});

    }


    public JPanel getPanel(){
        return contentPane;
    }


//    class PassVerifier extends InputVerifier {
//        public boolean verify(JComponent input) {
//            JTextField tf = (JTextField) input;
//            double upper = Double.parseDouble(frameUpperField.getText());
//            double lower = Double.parseDouble( ((JTextField) input).getText() );
//
//            if (lower > upper){
//                mainStatus.setText(" Invalid entry, must be less than upper");
//                Toolkit.getDefaultToolkit().beep();
//                tf.setText("0");
//            }
//
//            return true;
//        }
//    }
}


