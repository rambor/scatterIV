import FileManager.WorkingDirectory;
import version4.DoubleValue;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

public class Settings extends JDialog implements ChangeListener, PropertyChangeListener {

    private JPanel contentPane;
    private  JButton atsasDirButton;
    private  JLabel atsasDirLabel;
    private  JButton workingDirButton;
    private  JLabel workingDirLabel;
    private  JTextField qmaxLimitField;
    private  JTextField qminLimitField;
    private  JTextField qmaxForPDBText;
    private  JCheckBox excludeWatersFromInputCheckBox;
    private  JComboBox refinementRoundsBox;
    private  JComboBox rejectionCutOffBox;
    private  JTextField defaultDmaxField;
    private  JTextField dmaxSearchMin;
    private  JTextField dmaxSearchMax;
    private  JComboBox cBox;
    private static WorkingDirectory workingDirectory;
    private static DoubleValue dmaxSearchMinValue;
    private static DoubleValue dmaxSearchMaxValue;
    private static DoubleValue defaultDmax;
    private static Settings singleton = new Settings();
    private static JLabel status;
    private static String atsasDirectory;

    private Settings(){
        dmaxSearchMinValue = new DoubleValue(37);
        dmaxSearchMaxValue = new DoubleValue(176);
        defaultDmax = new DoubleValue(97);

        dmaxSearchMin.setText(String.format("%.1f", dmaxSearchMinValue.getValue()));
        dmaxSearchMax.setText(String.format("%.1f", dmaxSearchMaxValue.getValue()));

        defaultDmaxField.setText(String.format("%.1f", defaultDmax.getValue()));
        rejectionCutOffBox.setSelectedIndex(3);
        refinementRoundsBox.setSelectedIndex(2);

        workingDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Set working directory
                File theCWD = new File(Main.WORKING_DIRECTORY.getWorkingDirectory());

                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select Directory");

                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION){

                    if (chooser.getSelectedFile().isDirectory()){
                        Main.WORKING_DIRECTORY.setWorkingDirectory(chooser.getSelectedFile().toString());
                    } else {
                        Main.WORKING_DIRECTORY.setWorkingDirectory(chooser.getCurrentDirectory().toString());
                    }

                    workingDirLabel.setText(Main.WORKING_DIRECTORY.getWorkingDirectory());
                    //workingDirLabel.setText(chooser.getSelectedFile().getName()+"/");
                    Main.updateProp();
                }
            }
        });


        setContentPane(contentPane);
        setModal(true);


        qmaxForPDBText.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                double x = 0;
                try {
                    x = Double.parseDouble(qmaxForPDBText.getText());
                } catch (NumberFormatException ee) {
                    status.setForeground(Color.RED);
                    qmaxForPDBText.setText("0.38");
                }
            }
        });

        qminLimitField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                double x = 0;
                try {
                    x = Double.parseDouble(qminLimitField.getText());

                    if (x > Double.parseDouble(qmaxLimitField.getText())){
                        qminLimitField.setText("0.02");
                        status.setForeground(Color.RED);
                        status.setText("qmin must be less than :: " + qmaxLimitField.getText());
                    }

                } catch (NumberFormatException ee) {
                    qminLimitField.setText("0.02");
                }
            }
        });

        qmaxLimitField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                double x = 0;
                try {
                    x = Double.parseDouble(qmaxLimitField.getText());

                    if (x < Double.parseDouble(qminLimitField.getText())){
                        qmaxLimitField.setText("0.14");
                        status.setForeground(Color.RED);
                        status.setText("qmax must be greater than :: " + qminLimitField.getText());
                    }
                } catch (NumberFormatException ee) {
                    qmaxLimitField.setText("0.14");
                }
            }
        });

        dmaxSearchMin.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                double x = 0;
                try {
                    x = Double.parseDouble(dmaxSearchMin.getText());

                    if (x > Double.parseDouble(dmaxSearchMax.getText())){

                        double value = Double.parseDouble(dmaxSearchMax.getText());
                        x = (value - 0.1*value);
                        dmaxSearchMin.setText(String.format("%.1f", x));
                        status.setForeground(Color.RED);
                        status.setText("min must be greater than :: " + dmaxSearchMax.getText());
                    }

                    dmaxSearchMinValue.setValue(x);

                } catch (NumberFormatException ee) {
                    dmaxSearchMin.setText("37");
                    dmaxSearchMinValue.setValue(37.0);
                    dmaxSearchMax.setText("176");
                    dmaxSearchMaxValue.setValue(176.0);
                }
            }
        });

        dmaxSearchMax.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                double x = 0;
                try {
                    x = Double.parseDouble(dmaxSearchMax.getText());

                    if (x < Double.parseDouble(dmaxSearchMin.getText())){

                        double value = Double.parseDouble(dmaxSearchMin.getText());
                        x = (value + 0.5*value);
                        dmaxSearchMax.setText(String.format("%.1f", x));
                        status.setForeground(Color.RED);
                        status.setText("max must be greater than :: " + dmaxSearchMin.getText());
                    }

                    dmaxSearchMaxValue.setValue(x);

                } catch (NumberFormatException ee) {
                    dmaxSearchMin.setText("37");
                    dmaxSearchMinValue.setValue(37.0);
                    dmaxSearchMax.setText("176");
                    dmaxSearchMaxValue.setValue(176.0);
                }
            }
        });


        qmaxForPDBText.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);

                try {
                    double x = Double.parseDouble(qmaxForPDBText.getText());

                    if (x > 2){
                        x = 0.4;
                        qmaxForPDBText.setText(String.format("%.1f", x));
                        status.setForeground(Color.RED);
                        status.setText("q-max too large!");
                    }

                    dmaxSearchMaxValue.setValue(x);

                } catch (NumberFormatException ee) {
                    qmaxForPDBText.setText(String.format("%.1f", 0.4));
                }

            }
        });


        defaultDmaxField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                try {
                    double x = Double.parseDouble(defaultDmaxField.getText());

                    if (x < 10){
                        x = 97;
                        defaultDmaxField.setText(String.format("%.1f", x));
                        status.setForeground(Color.RED);
                        status.setText("d-max too small!");
                    }

                    defaultDmax.setValue(x);

                } catch (NumberFormatException ee) {
                    defaultDmaxField.setText(String.format("%.1f", 97.0));
                    defaultDmax.setValue(97);
                }

            }
        });

        workingDirLabel.setText(Main.WORKING_DIRECTORY.getWorkingDirectory());

        atsasDirButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Set working directory
                File theCWD = new File(System.getProperty("user.dir"));
                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select Directory");

                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION){
                    if (chooser.getSelectedFile().isDirectory()){
                        atsasDirectory = chooser.getSelectedFile().toString();
                    } else {
                        atsasDirectory = chooser.getCurrentDirectory().toString();
                    }
                    atsasDirLabel.setText(atsasDirectory);
                    Main.updateProp();
                }
            }
        });
    }

    /* Static 'instance' method */
    public static Settings getInstance( ) {
        return singleton;
    }

    public static void setFields(WorkingDirectory wd, JLabel tstatus){
        workingDirectory = wd;
        workingDirectory.addPropertyChangeListener(singleton);
        status = tstatus;
    }

//    public Settings(WorkingDirectory wd, JLabel status) {

//        workingDirectory = wd;
//        workingDirectory.addPropertyChangeListener(this);

//        dmaxSearchMinValue = new DoubleValue(37);
//        dmaxSearchMaxValue = new DoubleValue(176);
//        defaultDmax = new DoubleValue(97);
//
//        dmaxSearchMin.setText(String.format("%.1f", dmaxSearchMinValue.getValue()));
//        dmaxSearchMax.setText(String.format("%.1f", dmaxSearchMaxValue.getValue()));
//
//        defaultDmaxField.setText(String.format("%.1f", defaultDmax.getValue()));
//        rejectionCutOffBox.setSelectedIndex(3);
//        refinementRoundsBox.setSelectedIndex(2);
//
//        workingDirButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                //Set working directory
//                File theCWD = new File(Main.WORKING_DIRECTORY.getWorkingDirectory());
//
//                JFileChooser chooser = new JFileChooser(theCWD);
//                chooser.setDialogTitle("Select Directory");
//
//                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
//                chooser.setAcceptAllFileFilterUsed(false);
//
//                if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION){
//
//                    if (chooser.getSelectedFile().isDirectory()){
//                        Main.WORKING_DIRECTORY.setWorkingDirectory(chooser.getSelectedFile().toString());
//                    } else {
//                        Main.WORKING_DIRECTORY.setWorkingDirectory(chooser.getCurrentDirectory().toString());
//                    }
//
//                    workingDirLabel.setText(Main.WORKING_DIRECTORY.getWorkingDirectory());
//                    //workingDirLabel.setText(chooser.getSelectedFile().getName()+"/");
//                    Main.updateProp();
//                }
//            }
//        });
//
//        workingDirLabel.setText(Main.WORKING_DIRECTORY.getWorkingDirectory());
//        setContentPane(contentPane);
//        setModal(true);
//
//
//        qmaxForPDBText.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                double x = 0;
//                try {
//                    x = Double.parseDouble(qmaxForPDBText.getText());
//                } catch (NumberFormatException ee) {
//                    status.setForeground(Color.RED);
//                    qmaxForPDBText.setText("0.38");
//                }
//            }
//        });
//
//        qminLimitField.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                double x = 0;
//                try {
//                    x = Double.parseDouble(qminLimitField.getText());
//
//                    if (x > Double.parseDouble(qmaxLimitField.getText())){
//                        qminLimitField.setText("0.02");
//                        status.setForeground(Color.RED);
//                        status.setText("qmin must be less than :: " + qmaxLimitField.getText());
//                    }
//
//                } catch (NumberFormatException ee) {
//                    qminLimitField.setText("0.02");
//                }
//            }
//        });
//
//        qmaxLimitField.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                double x = 0;
//                try {
//                    x = Double.parseDouble(qmaxLimitField.getText());
//
//                    if (x < Double.parseDouble(qminLimitField.getText())){
//                        qmaxLimitField.setText("0.14");
//                        status.setForeground(Color.RED);
//                        status.setText("qmax must be greater than :: " + qminLimitField.getText());
//                    }
//                } catch (NumberFormatException ee) {
//                    qmaxLimitField.setText("0.14");
//                }
//            }
//        });
//
//        dmaxSearchMin.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                double x = 0;
//                try {
//                    x = Double.parseDouble(dmaxSearchMin.getText());
//
//                    if (x > Double.parseDouble(dmaxSearchMax.getText())){
//
//                        double value = Double.parseDouble(dmaxSearchMax.getText());
//                        x = (value - 0.1*value);
//                        dmaxSearchMin.setText(String.format("%.1f", x));
//                        status.setForeground(Color.RED);
//                        status.setText("min must be greater than :: " + dmaxSearchMax.getText());
//                    }
//
//                    dmaxSearchMinValue.setValue(x);
//
//                } catch (NumberFormatException ee) {
//                    dmaxSearchMin.setText("37");
//                    dmaxSearchMinValue.setValue(37.0);
//                    dmaxSearchMax.setText("176");
//                    dmaxSearchMaxValue.setValue(176.0);
//                }
//            }
//        });
//
//        dmaxSearchMax.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                double x = 0;
//                try {
//                    x = Double.parseDouble(dmaxSearchMax.getText());
//
//                    if (x < Double.parseDouble(dmaxSearchMin.getText())){
//
//                        double value = Double.parseDouble(dmaxSearchMin.getText());
//                        x = (value + 0.5*value);
//                        dmaxSearchMax.setText(String.format("%.1f", x));
//                        status.setForeground(Color.RED);
//                        status.setText("max must be greater than :: " + dmaxSearchMin.getText());
//                    }
//
//                    dmaxSearchMaxValue.setValue(x);
//
//                } catch (NumberFormatException ee) {
//                    dmaxSearchMin.setText("37");
//                    dmaxSearchMinValue.setValue(37.0);
//                    dmaxSearchMax.setText("176");
//                    dmaxSearchMaxValue.setValue(176.0);
//                }
//            }
//        });
//
//
//        qmaxForPDBText.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//
//                try {
//                    double x = Double.parseDouble(qmaxForPDBText.getText());
//
//                    if (x > 2){
//                        x = 0.4;
//                        qmaxForPDBText.setText(String.format("%.1f", x));
//                        status.setForeground(Color.RED);
//                        status.setText("q-max too large!");
//                    }
//
//                    dmaxSearchMaxValue.setValue(x);
//
//                } catch (NumberFormatException ee) {
//                    qmaxForPDBText.setText(String.format("%.1f", 0.4));
//                }
//
//            }
//        });
//
//
//        defaultDmaxField.addFocusListener(new FocusAdapter() {
//            @Override
//            public void focusLost(FocusEvent e) {
//                super.focusLost(e);
//                try {
//                    double x = Double.parseDouble(defaultDmaxField.getText());
//
//                    if (x < 10){
//                        x = 97;
//                        defaultDmaxField.setText(String.format("%.1f", x));
//                        status.setForeground(Color.RED);
//                        status.setText("d-max too small!");
//                    }
//
//                    defaultDmax.setValue(x);
//
//                } catch (NumberFormatException ee) {
//                    defaultDmaxField.setText(String.format("%.1f", 97.0));
//                    defaultDmax.setValue(97);
//                }
//
//            }
//        });
//    }

    /*
     * check if file is proper format for SEC, if single dat file, then search directory and build list
     */
    public JPanel getPanel(){
        return contentPane;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if ( evt.getPropertyName() == "WorkingDirectory") {
            WorkingDirectory t = (WorkingDirectory) evt.getSource();
            // get the new value object
            String o = (String)evt.getNewValue();
            workingDirLabel.setText(o);
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {

    }

    public double getQminLimit(){
        return Double.parseDouble(qminLimitField.getText());
    }

    public double getQmaxLimit(){
        return Double.parseDouble(qmaxLimitField.getText());
    }

    public JComboBox getRefinementRoundsBox() {
        return refinementRoundsBox;
    }

    public JComboBox getRejectionCutOffBox() {
        return rejectionCutOffBox;
    }

    public JComboBox getcBox() {
        return cBox;
    }

    public static DoubleValue getDmaxSearchMinValue() {
        return dmaxSearchMinValue;
    }

    public static DoubleValue getDmaxSearchMaxValue() {
        return dmaxSearchMaxValue;
    }

    public static DoubleValue getDefaultDmax() {
        return defaultDmax;
    }

    public static String getATSASDir() { return atsasDirectory; }
}
