package version4.sasCIF;

import version4.Constants;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Pattern;

public class SasObjectForm extends JDialog{
    private JTabbedPane tabbedPane1;
    private JTextField sas_beam_instrument_name;
    private JCheckBox neutronsCheckBox;
    private JCheckBox xRaysCheckBox;
    private JTextArea sas_sample_details_text;
    private JTextArea sas_buffer_comment;
    private JTextField sas_buffer_other;
    private JTextField sas_buffer_salt;
    private JTextField sas_buffer_name;
    private JTextField sas_buffer_pH;
    private JTextField sas_sample_calibration_details;
    private JTextField sas_sample_temperature;
    private JTextField sas_beam_wavelength;
    private JComboBox comboBoxMomentumTransfer;
    private JCheckBox relativeCheckBox;
    private JCheckBox absoluteCheckBox;
    private JTextField sas_scan_type;
    private JTextField sas_scan_measurement_date;
    private JTextField sas_sample_sec_column;
    private JTextField sas_sample_sec_flow_rate;
    private JPanel SECPanel;
    private JTextField sas_beam_wavelength_units;
    private Pattern dataFormat = Pattern.compile("(ev|keV|Angstrom)", Pattern.CASE_INSENSITIVE);

    private JTextField sas_beam_sample_to_detector_distance;
    private JLabel sas_detc_detector_name;
    private JButton cancelButton;
    private JButton updateButton;
    private JPanel contentPane;
    private JTextField sas_beam_flux;
    private JComboBox sas_beam_sourceBox;
    private JLabel warningLabel;
    private JLabel sas_details_thickness;
    private JLabel sas_sample_details_label;
    private JTextField sas_sample_thickness;
    private String sas_scan_intensity_units;
    private JTextField sas_scan_exposure_time;
    private JTextField sas_scan_filename;
    private JLabel bufferDetailsLabel;
    private JLabel sas_sample_sec_columnLabel;
    private JTextField sas_detc_name;
    private JTextField sas_detc_dead_time;
    private JTextArea sas_results_comments;
    private JLabel SummaryComments;
    private JLabel rgReciLabel;
    private JLabel reciRgValue;
    private JLabel realRgValue;
    private JLabel vcValue;
    private JLabel volumeReciValue;
    private JLabel massValue;
    private JLabel peValue;
    private JPanel sasResultsPanel;
    private JLabel errorRgReciLabel;
    private JLabel errorRealRgLabel;
    private JLabel vcUnitsLabel;
    private JLabel peErrorLabel;
    private JLabel volumeRealLabel;
    private JLabel reciVolumeUnitsLabel;
    private JLabel volumeRealUnitsLabel;
    private JLabel dmaxValue;
    private String sas_beam_radiation_type;
    private SasObject sasObject;
    private boolean isSEC = false;

    public SasObjectForm(SasObject object, boolean isSEC){
        sasObject = object;
        this.isSEC = isSEC;

        if (!isSEC){
            bufferDetailsLabel.setText("Buffer Details");
            SECPanel.setVisible(false);
        }

        // show sasResult tab
        if (sasObject.isResultSet()){
            tabbedPane1.setEnabledAt(3,true);
            vcUnitsLabel.setText("Angstrom\u00B2");
            reciVolumeUnitsLabel.setText("Angstrom\u00B3");
            volumeRealUnitsLabel.setText("Angstrom\u00B3");

            setupSasResults();
        } else {
            tabbedPane1.setEnabledAt(3, false);
        }

        sas_sample_details_text.setLineWrap(true);
        sas_sample_details_text.setWrapStyleWord(true);
        sas_buffer_comment.setLineWrap(true);
        sas_buffer_comment.setWrapStyleWord(true);

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(updateButton);

        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onUpdate();
            }
        });

        sas_beam_wavelength_units.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checktext();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checktext();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checktext();
            }

            protected void checktext(){
                String input = sas_beam_wavelength_units.getText().toLowerCase();

                if (input.equals("ev") || input.equals("kev") || input.equals("angstroms") || input.equals("nm")){
                    warningLabel.setText("");
                } else {
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("must be ev, keV, Angstroms or nm");
                }
            }
        });


        sas_scan_measurement_date.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checktext();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checktext();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checktext();
            }

            protected void checktext(){

                if (!validateDate(sas_scan_measurement_date.getText())){
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Format must be YYYY-MM-DD");
                    return;
                }
            }
        });



        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);


        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        xRaysCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (xRaysCheckBox.isSelected()){
                    neutronsCheckBox.setSelected(false);
                    sas_beam_radiation_type = "xray";
                }
            }
        });

        neutronsCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (neutronsCheckBox.isSelected()){
                    xRaysCheckBox.setSelected(false);
                    sas_beam_radiation_type = "neutron";
                }
            }
        });

        relativeCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (relativeCheckBox.isSelected()){
                    absoluteCheckBox.setSelected(false);
                    sas_scan_intensity_units = "relative";
                }
            }
        });

        absoluteCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (absoluteCheckBox.isSelected()){
                    relativeCheckBox.setSelected(false);
                    sas_scan_intensity_units = "absolute";
                }
            }
        });


        this.setupSasScan();
        this.setupSampleDetails();

        sas_buffer_pH.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                float x = 0;
                try {
                    x = Float.parseFloat(sas_buffer_pH.getText());
                    //update mouse marker
                    if(x <= 0){
                        warningLabel.setForeground(Color.RED);
                        warningLabel.setText("Invalid Input, setting to 7");
                        x=7.0f;
                    }

                    sas_buffer_pH.setText(String.format("%.2f", x));

                } catch (NumberFormatException ee) {

                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input, setting to 7");
                    sas_buffer_pH.setText(String.format("%.2f", 7.00));
                }
            }
        });

        sas_sample_temperature.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                try {
                    float x = Float.parseFloat(sas_sample_temperature.getText());
                    //update mouse marker
                    if(x <= 0){
                        warningLabel.setForeground(Color.RED);
                        warningLabel.setText("Invalid Input, setting to 25");
                        x=25.0f;
                    }

                    sas_sample_temperature.setText(String.format("%.1f", x));

                } catch (NumberFormatException ee) {

                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input, setting to 25");
                    sas_sample_temperature.setText(String.format("%.1f", 25.00));
                }
            }
        });


        sas_beam_wavelength.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                try {
                    float x = Float.parseFloat(sas_beam_wavelength.getText());
                    //update mouse marker
                    if(x <= 0){
                        warningLabel.setForeground(Color.RED);
                        warningLabel.setText("Invalid Input, setting to 12.4 keV");
                        x=12.4f;
                    }

                    sas_beam_wavelength.setText(String.format("%.3f", x));

                } catch (NumberFormatException ee) {

                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input, setting to 12.4 keV");
                    sas_beam_wavelength.setText(String.format("%.1f", 12.4));
                }
            }
        });

        sas_beam_wavelength_units.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                String x = sas_beam_wavelength_units.getText();
                if (!dataFormat.matcher(x).matches()){
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input, setting to 12.4 keV");
                    x = "";
                }
                sas_beam_wavelength_units.setText(x);
            }
        });

        sas_beam_flux.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                String temp = sas_beam_flux.getText();

                if (temp.contains("10^")){
                    String temp1 = temp.replace("10^", "1E");
                    temp = temp1;
                }

                try {
                    double x = Double.parseDouble(temp);
                    //update mouse marker
                    if(x <= 0){
                        warningLabel.setForeground(Color.RED);
                        warningLabel.setText("Invalid Input");
                        sas_beam_flux.setText("");
                    }

                    sas_beam_flux.setText(String.format("%.2E", x));

                } catch (NumberFormatException ee) {

                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input");
                    sas_beam_flux.setText("");
                }
            }
        });

        sas_beam_sample_to_detector_distance.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                try {
                    double x = Double.parseDouble(sas_beam_sample_to_detector_distance.getText());
                    //update mouse marker
                    if(x <= 0){
                        warningLabel.setForeground(Color.RED);
                        warningLabel.setText("Invalid Input");
                        sas_beam_sample_to_detector_distance.setText("");
                    }

                    sas_beam_sample_to_detector_distance.setText(String.format("%.3f", x));

                } catch (NumberFormatException ee) {
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input");
                    sas_beam_sample_to_detector_distance.setText("");
                }
            }
        });

        sas_scan_exposure_time.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                try {
                    float x = Float.parseFloat(sas_scan_exposure_time.getText());
                    //update mouse marker
                    if(x <= 0){
                        warningLabel.setForeground(Color.RED);
                        warningLabel.setText("Invalid Input, must be greater than 0, in seconds");
                        sas_scan_exposure_time.setText("");
                    }

                    sas_scan_exposure_time.setText(String.format("%.3f", x));

                } catch (NumberFormatException ee) {
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input, must be greater than 0, in seconds");
                    sas_scan_exposure_time.setText("");
                }

            }
        });

        sas_scan_measurement_date.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                if (!validateDate(sas_scan_measurement_date.getText())){
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Format must be YYYY-MM-DD");
                    sas_scan_measurement_date.setText("");
                    return;
                }
            }
        });

        sas_sample_sec_flow_rate.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                try {
                    Float value = Float.parseFloat(sas_sample_sec_flow_rate.getText());
                    if (value > 0){
                        sas_sample_sec_flow_rate.setText(String.format("%.3f", value));
                    } else {
                        throw new NumberFormatException("too small");
                    }

                } catch (NumberFormatException ee) {
                    warningLabel.setForeground(Color.RED);
                    warningLabel.setText("Invalid Input, must be greater than 0");
                    sas_sample_sec_flow_rate.setText("");
                }
            }
        });

        this.loadPriorInfo();
    }

    private void onCancel() {
         // add your code here if necessary
        dispose();
    }

    // save all details to sasObject
    private void onUpdate(){
        /*
         SAS Beam tab
         */
        sasObject.getSasBeam().setInstrument_name(sas_beam_instrument_name.getText().replaceAll("(?:\\n|\\r)", "").trim());
        sasObject.getSasBeam().setType_of_source(sas_beam_sourceBox.getSelectedItem().toString().toLowerCase());

        if (neutronsCheckBox.isSelected()){
            sasObject.getSasBeam().setRadiation_type("neutrons");
        } else {
            sasObject.getSasBeam().setRadiation_type("xrays");
        }

        try{
            double lambda = Double.parseDouble(sas_beam_wavelength.getText());
            if (lambda > 0){
                sasObject.getSasBeam().setRadiation_wavelength(lambda);
                sasObject.getSasBeam().setUnits(sas_beam_wavelength_units.getText());
            }
        } catch (NumberFormatException ee) {

        }

        try {
            double flux = Double.parseDouble(sas_beam_flux.getText());
            if (flux > 0){
                sasObject.getSasBeam().setFlux(flux);
            }
        } catch (NumberFormatException ee) {

        }



        try{
            double stod = Double.parseDouble(sas_beam_sample_to_detector_distance.getText());
            if (stod > 0){
                sasObject.getSasBeam().setSample_to_detector_distance(stod);
            }
        } catch (NumberFormatException ee) {

        }

        sasObject.getSasDetc().setName(sas_detc_name.getText().replaceAll("(?:\\n|\\r)", "").trim());

        try{
            float stod = Float.parseFloat(sas_detc_dead_time.getText());
            if (stod > 0){
                sasObject.getSasDetc().setDetector_read_out_dead_time(stod);
            }
        } catch (NumberFormatException ee) {

        }

        /*
         update SAS Details
         */
        SasSample sasSample = sasObject.getSasSample();
        sasSample.setDetails(sas_sample_details_text.getText().replaceAll("(?:\\n|\\r)", " ").trim());
        try{
            sasSample.setThickness(Float.parseFloat(sas_sample_thickness.getText()));
        } catch (NumberFormatException ee) {

        }

        sasSample.setCell_temperature(Float.parseFloat(sas_sample_temperature.getText()));
        sasSample.setCalibration_details(sas_sample_calibration_details.getText().replaceAll("(?:\\n|\\r)", " ").trim());

        /*
         update SAS Buffer
         */
        SasBuffer sasBuffer = sasObject.getSasBuffer();
        try{
            sasBuffer.setpH(Float.parseFloat(sas_buffer_pH.getText()));
        } catch (NumberFormatException ee) {

        }

        sasBuffer.setName(sas_buffer_name.getText().replaceAll("(?:\\n|\\r)", "").trim());
        sasBuffer.setComment(sas_buffer_comment.getText().replaceAll("(?:\\n|\\r)", " ").trim());
        sasBuffer.setSalt(sas_buffer_salt.getText());
        sasBuffer.setOther(sas_buffer_other.getText().replaceAll("(?:\\n|\\r)", " ").trim());

        /*
         * update SAS details
         */
        SasScan sascan = sasObject.getSasScan();
        sascan.setType(sas_scan_type.getText());
        try{
            sascan.setExposure_time(Float.parseFloat(sas_scan_exposure_time.getText()));
        } catch (NumberFormatException ee) {

        }

        sascan.setMomentum_transfer_units((String)comboBoxMomentumTransfer.getSelectedItem());
        sascan.setScan_name(sas_scan_filename.getText());

        if (relativeCheckBox.isSelected()){
            sascan.setIntensity_units("relative");
        } else {
            sascan.setIntensity_units("absolute");
        }

        // should have some validator for date
        if (sas_scan_measurement_date.getText().length() > 3){
            sascan.setMeasurement_date(sas_scan_measurement_date.getText());
        }

        if (isSEC){
            try{ // parseFloat throws exception use to catch if properly formatted
                sasSample.setSec_flow_rate(Float.parseFloat(sas_sample_sec_flow_rate.getText()));
            } catch (NumberFormatException ee) {
                warningLabel.setText("SEC flow rate must be a number");
            }

            try{
                if (sas_sample_sec_column.getText().length() > 1){
                    sasSample.setSec_column(sas_sample_sec_column.getText().replaceAll("(?:\\n|\\r)", "").trim());
                }
            } catch (NullPointerException ee){
                System.out.println("SAS SAMPLE NULL " + ee.getMessage());
            }
        }

        if (sasObject.isResultSet()){
            sasObject.getSasResult().setComments(sas_results_comments.getText().replaceAll("(?:\\n|\\r)", " ").trim());
        }

        dispose();
    }

    private void setupSampleDetails(){
        SasSample sasSample = sasObject.getSasSample();
        sas_sample_details_text.setText(sasSample.getDetails());
        sas_sample_thickness.setText(String.format("%.3f", sasSample.getThickness()));
        sas_sample_temperature.setText(String.format("%.1f", sasSample.getCell_temperature()));
        sas_sample_calibration_details.setText(sasSample.getCalibration_details());
        if (isSEC){
            sas_sample_sec_flow_rate.setText(String.format("%.2f", sasSample.getSec_flow_rate()));
            sas_sample_sec_column.setText(sasSample.getSec_column());
        }
    }

    private void setupSasResults(){

        SasResult sasResult = sasObject.getSasResult();
        sas_results_comments.setLineWrap(true);
        sas_results_comments.setWrapStyleWord(true);

        sas_results_comments.setText(sasResult.getComments());
        reciRgValue.setText(Constants.ThreeDecPlace.format(sasResult.getRg_from_guinier()) + " \u00B1 " + Constants.ThreeDecPlace.format(sasResult.getRg_from_guinier_error()));

        if (sasResult.getRg_from_PR() > 0){
            realRgValue.setText(Constants.ThreeDecPlace.format(sasResult.getRg_from_PR())+ " \u00B1 " + Constants.ThreeDecPlace.format(sasResult.getRg_from_pr_error()));
        } else {
            realRgValue.setText("-");
        }

        if (sasResult.getVolume_of_correlation_from_guinier() > 0){
            vcValue.setText(Constants.OneDecPlace.format(sasResult.getVolume_of_correlation_from_guinier()));
        } else {
            vcValue.setText("-");
        }

        if (sasResult.getPorod_volume() > 0){
            volumeReciValue.setText(Integer.toString(sasResult.getPorod_volume()));
        } else {
            volumeReciValue.setText("-");
        }

//        volumeReciValue.setText();
        peValue.setText(Constants.OneDecPlace.format(sasResult.getPorod_exponenet()) + " \u00B1 " + Constants.OneDecPlace.format(sasResult.getPorod_exponent_error()));
//        String vol = "<html><p><b>" + Constants.Scientific1.format(dataset.getPorodVolume()) + "</b></p><p>" + Constants.Scientific1.format(dataset.getPorodVolumeReal()) + "</p></html>";

        if (sasResult.getDmax() > 0){
            dmaxValue.setText(Constants.OneDecPlace.format(sasResult.getDmax()));
        } else {
            dmaxValue.setText("-");
        }

    }

    private void setupSasScan(){

        SasScan sasScan = sasObject.getSasScan();
        if (sasScan.getIntensity_units().length() > 2){
            String typeof = sasScan.getIntensity_units().toLowerCase();
            int totalcombo = comboBoxMomentumTransfer.getItemCount();
            ComboBoxModel model = comboBoxMomentumTransfer.getModel();
            int setit = 0;
            for(int i=0;i<totalcombo;i++) {
                String element = ((String)model.getElementAt(i)).toLowerCase();
                if(element.equals(typeof)){
                    setit = i;
                    break;
                }
            }
            comboBoxMomentumTransfer.setSelectedIndex(setit);
        }

        sas_scan_type.setText(sasScan.getType());
        sas_scan_exposure_time.setText(String.format("%.3f", sasScan.getExposure_time()));
        sas_scan_filename.setText(sasScan.getScan_name());

        String iofq_units = sasScan.getIntensity_units();

        if (iofq_units.equals("absolute")){
            absoluteCheckBox.setSelected(true);
        } else {
            relativeCheckBox.setSelected(true);
        }
    }


    private void loadPriorInfo(){

        SasBeam sasBeam = sasObject.getSasBeam();
        sas_beam_instrument_name.setText(sasBeam.getInstrument_name());
        sas_beam_wavelength.setText(String.format("%.2f", sasBeam.getRadiation_wavelength()));
        sas_beam_wavelength_units.setText(sasBeam.getUnits());

        if (sasBeam.getFlux() == null){
            sas_beam_flux.setText("");
        } else {
            sas_beam_flux.setText(String.format("%.2E", sasBeam.getFlux()));
        }

        sas_beam_sample_to_detector_distance.setText(String.format("%.2f", sasBeam.getSample_to_detector_distance()));

        if (sasBeam.getType_of_source().length() > 2){
            String typeof = sasBeam.getType_of_source().toLowerCase();
            int totalcombo = sas_beam_sourceBox.getItemCount();
            ComboBoxModel model = sas_beam_sourceBox.getModel();
            int setit = 0;
            for(int i=0;i<totalcombo;i++) {
                String element = model.getElementAt(i).toString().toLowerCase();
                if(element.equals(typeof)){
                    setit = i;
                    break;
                }
            }
            sas_beam_sourceBox.setSelectedIndex(setit);
        }


        String temp = sasBeam.getRadiation_type().toLowerCase();
        if (temp.equals("neutrons")){
            neutronsCheckBox.setSelected(true);
            xRaysCheckBox.setSelected(false);
        } else {
            neutronsCheckBox.setSelected(false);
            xRaysCheckBox.setSelected(true);
        }

        sas_detc_name.setText(sasObject.getSasDetc().getName());
        sas_detc_dead_time.setText(String.format("%.2f", sasObject.getSasDetc().getDetector_read_out_dead_time()));

        // sample details
        sas_sample_details_text.setText(sasObject.getSasSample().getDetails());
        sas_sample_thickness.setText(String.format("%.3f",sasObject.getSasSample().getThickness()));
        sas_sample_temperature.setText(String.format("%.1f", sasObject.getSasSample().getCell_temperature()));
        sas_sample_calibration_details.setText(sasObject.getSasSample().getCalibration_details());

         //update SAS Buffer
        SasBuffer sasBuffer = sasObject.getSasBuffer();
        try{
            sas_buffer_pH.setText(String.format("%.2f", sasBuffer.getpH()));
        } catch (NumberFormatException ee) {

        }
        sas_buffer_name.setText(sasBuffer.getName());
        sas_buffer_comment.setText(sasBuffer.getComment());
        sas_buffer_salt.setText(sasBuffer.getSalt());
        sas_buffer_other.setText(sasBuffer.getOther());

        //scan details
        sas_scan_type.setText(sasObject.getSasScan().getType());
        try{
            sas_scan_exposure_time.setText(String.format("%.2f", sasObject.getSasScan().getExposure_time()));
        } catch (NumberFormatException ee) {

        }
        sas_scan_filename.setText(sasObject.getSasScan().getScan_name());

        String qunits = sasObject.getSasScan().getMomentum_transfer_units().toUpperCase();
        int totalcombo = comboBoxMomentumTransfer.getItemCount();
        ComboBoxModel model = comboBoxMomentumTransfer.getModel();
        int setit = 0;
        for(int i=0;i<totalcombo;i++) {
            String element = model.getElementAt(i).toString().toUpperCase();
            if(element.equals(qunits)){
                setit = i;
                break;
            }
        }
        comboBoxMomentumTransfer.setSelectedIndex(setit);

        temp = sasObject.getSasScan().getIntensity_units().toLowerCase();
        if (temp.equals("relative")){
            relativeCheckBox.setSelected(true);
            absoluteCheckBox.setSelected(false);
        } else {
            relativeCheckBox.setSelected(false);
            absoluteCheckBox.setSelected(true);
        }

        sas_scan_measurement_date.setText(sasObject.getSasScan().getMeasurement_date());

    }

    boolean validateDate(String toCheck){
        boolean flag = false;
        Pattern dateFormat = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

        if (dateFormat.matcher(toCheck).matches() && (toCheck.length() > 5) && toCheck.contains("-")){
                String[] row = toCheck.split("-"); // CSV files could have a ", "
                int middlevalue  = Integer.parseInt(row[1]);
                int lastvalue  = Integer.parseInt(row[2]);
                if (middlevalue <= 12 && middlevalue > 0 && lastvalue > 0 && lastvalue < 32){
                    flag = true;
                }
        }

        // should check middle number is between 1 and 12
        return flag;
    }
}
