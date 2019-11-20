import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Vector;

public class Modeling {


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

    public Modeling(){

        int cpuCores = Runtime.getRuntime().availableProcessors();
        Vector comboBoxItemsCPU = new Vector();
        for (int i=1; i<=cpuCores; i++){
            comboBoxItemsCPU.add(i);
        }

        final DefaultComboBoxModel cpuModel = new DefaultComboBoxModel(comboBoxItemsCPU);
        cpuBox.setModel(cpuModel);


        selectOutFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //Set GNOM out file
                File theCWD = new File(Main.WORKING_DIRECTORY.getWorkingDirectory());
                JFileChooser chooser = new JFileChooser(theCWD);
                chooser.setDialogTitle("Select File");

                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(contentPanel) == JFileChooser.APPROVE_OPTION){
                    outFileLabel.setText(chooser.getSelectedFile().toString());
                }
            }
        });
    }
}
