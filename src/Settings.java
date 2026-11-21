import FileManager.WorkingDirectory;
import version4.DoubleValue;
import version4.SettingsValues;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The Settings tab. The values, their rules and how they are saved are in {@link SettingsValues};
 * this class only shows them and passes edits on. Every accepted edit is saved with the other settings
 * (see {@link Scatter#updateProp()}), so the values are the same the next time Scatter starts.
 */
public class Settings extends JDialog implements ChangeListener, PropertyChangeListener {

    private JPanel contentPane;
    private  JButton programsDirButton;
    private  JLabel programsDirLabel;
    private  JButton workingDirButton;
    private  JLabel workingDirLabel;
    private  JTextField qmaxLimitField;
    private  JTextField qminLimitField;
    private  JTextField qmaxForPDBText;
    private  JCheckBox excludeWatersFromInputCheckBox;
    private  JComboBox refinementRoundsBox;
    private  JComboBox rejectionCutOffBox;
    private  JComboBox cBox;
    private static WorkingDirectory workingDirectory;
    private static DoubleValue defaultDmax;
    private static Settings singleton;
    private static JLabel status;
    private static String programsDirectory;

    private final SettingsValues values = new SettingsValues();
    /** true while saved settings are being put into the fields, so that does not trigger a save */
    private boolean applying;

    private Settings(){
        defaultDmax = new DoubleValue(values.defaultDmax);
        showValues();

        workingDirButton.addActionListener(e -> {
            File theCWD = new File(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
            JFileChooser chooser = new JFileChooser(theCWD);
            chooser.setDialogTitle("Select Directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION){
                // the folder the user chose (this used to take the folder it is in, its parent)
                File chosen = chooser.getSelectedFile();
                String dir = (chosen != null && chosen.isDirectory()) ? chosen.toString() : chooser.getCurrentDirectory().toString();
                Scatter.WORKING_DIRECTORY.setWorkingDirectory(dir);
                workingDirLabel.setText(Scatter.WORKING_DIRECTORY.getWorkingDirectory());
                Scatter.updateProp();
            }
        });

        setContentPane(contentPane);
        setModal(true);

        onEdit(qminLimitField, values::checkQmin, v -> values.qmin = v, SettingsValues::formatQ);
        onEdit(qmaxLimitField, values::checkQmax, v -> values.qmax = v, SettingsValues::formatQ);
        onEdit(qmaxForPDBText, values::checkQmaxForPDB, v -> values.qmaxForPDB = v, SettingsValues::format1);

        excludeWatersFromInputCheckBox.addActionListener(e -> {
            values.excludeWaters = excludeWatersFromInputCheckBox.isSelected();
            save();
        });
        refinementRoundsBox.addActionListener(e -> { values.refinementRoundsIndex = refinementRoundsBox.getSelectedIndex(); save(); });
        rejectionCutOffBox.addActionListener(e -> { values.rejectionCutoffIndex = rejectionCutOffBox.getSelectedIndex(); save(); });
        cBox.addActionListener(e -> { values.cIndex = cBox.getSelectedIndex(); save(); });

        workingDirLabel.setText(Scatter.WORKING_DIRECTORY.getWorkingDirectory());

        programsDirButton.addActionListener(e -> {
            // folder with the modelling programs run by MODEL
            File start = (programsDirectory != null && new File(programsDirectory).isDirectory())
                    ? new File(programsDirectory) : new File(System.getProperty("user.home"));
            JFileChooser chooser = new JFileChooser(start);
            chooser.setDialogTitle("Select the modelling programs directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(contentPane) == JFileChooser.APPROVE_OPTION){
                if (chooser.getSelectedFile().isDirectory()){
                    programsDirectory = chooser.getSelectedFile().toString();
                } else {
                    programsDirectory = chooser.getCurrentDirectory().toString();
                }
                programsDirLabel.setText(programsDirectory);
                Scatter.updateProp();
            }
        });
    }

    /**
     * Check a field when the user leaves it or presses Enter: an accepted value is kept and saved,
     * anything else is replaced and the reason shown in the status bar.
     */
    private void onEdit(JTextField field, Function<String, SettingsValues.Checked> rule, Consumer<Double> set,
                        Function<Double, String> format) {
        Runnable check = () -> {
            SettingsValues.Checked c = rule.apply(field.getText());
            set.accept(c.value);
            if (!c.accepted()) {
                field.setText(format.apply(c.value));
                showStatus(c.message);
            }
            save();
        };
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { check.run(); }
        });
        field.addActionListener(e -> check.run());
    }

    private void showStatus(String message) {
        if (status == null) return;      // before setFields
        status.setForeground(Color.RED);
        status.setText(message);
    }

    private void save() {
        if (!applying) Scatter.updateProp();
    }

    /** Put the current values into the fields. */
    private void showValues() {
        applying = true;
        try {
            qminLimitField.setText(SettingsValues.formatQ(values.qmin));
            qmaxLimitField.setText(SettingsValues.formatQ(values.qmax));
            qmaxForPDBText.setText(SettingsValues.format1(values.qmaxForPDB));
            excludeWatersFromInputCheckBox.setSelected(values.excludeWaters);
            refinementRoundsBox.setSelectedIndex(values.refinementRoundsIndex);
            rejectionCutOffBox.setSelectedIndex(values.rejectionCutoffIndex);
            cBox.setSelectedIndex(values.cIndex);
            defaultDmax.setValue(values.defaultDmax);
        } finally {
            applying = false;
        }
    }

    /** The values saved last time (call on the Event Dispatch Thread, after {@link #setFields}). */
    public void applySaved(Properties saved) {
        SettingsValues loaded = SettingsValues.load(saved, refinementRoundsBox.getItemCount(),
                rejectionCutOffBox.getItemCount(), cBox.getItemCount());
        values.qmin = loaded.qmin;
        values.qmax = loaded.qmax;
        values.qmaxForPDB = loaded.qmaxForPDB;
        values.excludeWaters = loaded.excludeWaters;
        values.defaultDmax = loaded.defaultDmax;
        values.dmaxSearchMin = loaded.dmaxSearchMin;
        values.dmaxSearchMax = loaded.dmaxSearchMax;
        values.refinementRoundsIndex = loaded.refinementRoundsIndex;
        values.rejectionCutoffIndex = loaded.rejectionCutoffIndex;
        values.cIndex = loaded.cIndex;
        showValues();
    }

    /** Add the current values to the settings being saved. */
    public void store(Properties prop) {
        values.store(prop);
    }

    /* Static 'instance' method; created on first use, which must be on the Event Dispatch Thread */
    public static Settings getInstance( ) {
        if (singleton == null) singleton = new Settings();
        return singleton;
    }

    public static void setFields(WorkingDirectory wd, JLabel tstatus){
        workingDirectory = wd;
        workingDirectory.addPropertyChangeListener(getInstance());
        status = tstatus;
    }

    public JPanel getPanel(){
        return contentPane;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("WorkingDirectory".equals(evt.getPropertyName())) {
            workingDirLabel.setText((String) evt.getNewValue());
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {

    }

    public double getQminLimit(){ return values.qmin; }

    public double getQmaxLimit(){ return values.qmax; }

    /** Leave out water molecules when a PDB file is loaded. */
    public boolean isExcludeWaters() { return values.excludeWaters; }

    public JComboBox getRefinementRoundsBox() {
        return refinementRoundsBox;
    }

    public JComboBox getRejectionCutOffBox() {
        return rejectionCutOffBox;
    }

    public JComboBox getcBox() {
        return cBox;
    }


    public static DoubleValue getDefaultDmax() {
        return defaultDmax;
    }

    public String getProgramsDir() { return programsDirectory; }
    public void setProgramsDir(String value) {

        Path path = Paths.get(value);
        if (Files.isDirectory(path)){
            programsDirectory = value;
            programsDirLabel.setText(value);
        } else if (status != null) {
            status.setText("Programs directory does not exist :: " + value);
        }
    }

    public double getQmaxForPDB() {
        return values.qmaxForPDB;
    }
}
