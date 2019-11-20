import FileManager.WorkingDirectory;
import version4.Modeller.DammiNFManager;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Vector;

public class Modeling implements ChangeListener, PropertyChangeListener {


    private JButton selectOutFileButton;
    private JButton VIEWOutFILEButton;
    private JLabel outFileLabel;
    private JComboBox runsComboBox;
    private JRadioButton damminRadioButton;
    private JRadioButton dammifRadioButton;
    private JComboBox symmetryBox;
    private JRadioButton fastRadioButton;
    private JRadioButton slowRadioButton;
    private JComboBox cpuBox;
    private JCheckBox damRefineCheckBox;
    private JCheckBox alignPDBModelRunsCheckBox;
    private JButton STARTButton;
    private JPanel contentPanel;
    private JList completedModelingList;
    private JTextPane modelingOutputPane;
    private JButton SELECTPDBButton;
    private JLabel damStartLabel;
    private JButton SELECTDAMSTARTButton;
    private JLabel supcombLabel;
    private JLabel workingDirLabel;
    private JButton cwdButton;
    private static JLabel status;
    private static WorkingDirectory workingDirectory;

    private static boolean damstartStatus = false;
    private static File supcombFile;
    private static Modeling singleton = new Modeling();

    private DefaultListModel<String> damminfModelsModel;

    private Modeling(){

        int cpuCores = Runtime.getRuntime().availableProcessors();
        Vector comboBoxItemsCPU = new Vector();
        for (int i=1; i<=cpuCores; i++){
            comboBoxItemsCPU.add(i);
        }

        final DefaultComboBoxModel cpuModel = new DefaultComboBoxModel(comboBoxItemsCPU);
        cpuBox.setModel(cpuModel);

        damminfModelsModel = new DefaultListModel<String>();
        completedModelingList.setModel(damminfModelsModel);
        dammifRadioButton.setSelected(true);

        selectOutFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Set GNOM out file
                File theCWD = new File(workingDirectory.getWorkingDirectory());
                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select File");

                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(contentPanel) == JFileChooser.APPROVE_OPTION){
                    workingDirectory.setWorkingDirectory(chooser.getCurrentDirectory().toString());
                    workingDirLabel.setText("in :: " + workingDirectory.getWorkingDirectory());
                    String nameit = (chooser.getSelectedFile().toString().length() > 30) ? chooser.getSelectedFile().getName() : chooser.getSelectedFile().toString();
                    outFileLabel.setText(nameit);
                }
            }
        });


        damminRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (damminRadioButton.isSelected()){
                    dammifRadioButton.setSelected(false);
                } else {
                    dammifRadioButton.setSelected(true);
                }
            }
        });

        dammifRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (dammifRadioButton.isSelected()){
                    damminRadioButton.setSelected(false);
                } else {
                    damminRadioButton.setSelected(true);
                }
            }
        });

        slowRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (slowRadioButton.isSelected()){
                    fastRadioButton.setSelected(false);
                } else {
                    fastRadioButton.setSelected(true);
                }
            }
        });

        fastRadioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (fastRadioButton.isSelected()){
                    slowRadioButton.setSelected(false);
                } else {
                    slowRadioButton.setSelected(true);
                }
            }
        });

        SELECTDAMSTARTButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Set GNOM out file
                File theCWD = new File(workingDirectory.getWorkingDirectory());
                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select File");

                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(contentPanel) == JFileChooser.APPROVE_OPTION){

                   // damStartLabel.setText(chooser.getSelectedFile().toString());
                    damstartStatus = true;
                    int test = chooser.getSelectedFile().toString().indexOf("damstart.pdb");
                    //workingDirectory.setWorkingDirectory(chooser.getCurrentDirectory().toString());

                    if (test < 0){
                        damRefineCheckBox.setSelected(false);
                        damStartLabel.setText("Incorrect File");
                        damStartLabel.setForeground(Color.RED);
                        status.setText("Improper damstart file, must be damstart.pdb");
                        damstartStatus = false;
                    }
                }
            }
        });


        SELECTPDBButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                File theCWD = new File(workingDirectory.getWorkingDirectory());
                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select File");

                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setAcceptAllFileFilterUsed(false);

                supcombLabel.setFont(new Font("Arial", Font.BOLD, 12));

                if (chooser.showOpenDialog(contentPanel) == JFileChooser.APPROVE_OPTION) {

                    int test = chooser.getSelectedFile().toString().indexOf(".pdb");

                    if (test < 0) {
                        status.setText("Improper PDB extension");
                    } else {
                        // create PofR file for plotting
                        try {
                            supcombLabel.setText(chooser.getSelectedFile().getName());
                            supcombFile = new File(chooser.getSelectedFile().getAbsolutePath());
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
        });

        STARTButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                new Thread(){
                    public void run() {


                        String filename = outFileLabel.getText();
                        String damstartFile = damStartLabel.getText();
                        boolean damminOrIf = damminRadioButton.isSelected();
                        boolean fastOrSlow = fastRadioButton.isSelected();
                        Settings setIt = Settings.getInstance();

                        DammiNFManager tempModeling = new DammiNFManager(
                                Integer.valueOf(cpuBox.getSelectedItem().toString()),
                                filename,
                                damminOrIf,
                                Integer.valueOf(runsComboBox.getSelectedItem().toString()),
                                symmetryBox.getSelectedItem().toString(),
                                Settings.getATSASDir(),
                                workingDirectory.getWorkingDirectory(),
                                damminfModelsModel,
                                fastOrSlow,
                                damstartFile,
                                damstartStatus,
                                damRefineCheckBox.isSelected());

//                        if (alignPDBModelRunsCheckBox.isSelected() && supcombFile.exists()){
//                            tempModeling.setSupcombFile(supcombFile);
//                        }

                        tempModeling.modelNow(modelingOutputPane);

                    }
                }.start();
            }
        });
    }

    /* Static 'instance' method */
    public static Modeling getInstance( ) {
        return singleton;
    }

    /*
     * check if file is proper format for SEC, if single dat file, then search directory and build list
     */
    public JPanel getPanel(){
        return contentPanel;
    }

    public static void setFields(WorkingDirectory wd, JLabel tstatus){
        workingDirectory = wd;
        //workingDirectory.addPropertyChangeListener(singleton);
        status = tstatus;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        if ( evt.getPropertyName() == "WorkingDirectory") {
            WorkingDirectory t = (WorkingDirectory) evt.getSource();
            // get the new value object
//            Object o = evt.getNewValue();
//            workingDirLabel.setText(workingDirectory.getWorkingDirectory());
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {

    }
}
