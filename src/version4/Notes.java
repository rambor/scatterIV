package version4;

import FileManager.WorkingDirectory;

import javax.swing.*;
import java.awt.event.*;

public class Notes extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextArea notesArea;
    private JLabel notesLabel;
    private JPanel bottomJPanel;
    private JTextField filenameField;
    private String filename;
    private JLabel saveToDirectoryLabel;
    private JTextField titleField;
    private Dataset dataset;

    public Notes(Dataset data, WorkingDirectory workingDirectory) {
        dataset = data;
        notesLabel.setText("Experimental Note for : " + dataset.getFileName());
        this.notesArea.setText(dataset.getExperimentalNotes());

        filenameField.setText(data.getFileName()+".pdf");
        filenameField.setEnabled(false);

        saveToDirectoryLabel.setText("SAVE TO: " +workingDirectory.getWorkingDirectory());

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

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

        this.setLocation(400,400);
    }

    public Notes(Collection collectionInuse, WorkingDirectory workingDirectory) {

        notesLabel.setText("Experimental Note for " + collectionInuse.getTotalSelected() + " selected datasets");
        filenameField.setText("report.pdf");

        saveToDirectoryLabel.setText("SAVE TO: " + workingDirectory.getWorkingDirectory());

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setText();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
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

        this.setLocation(400,400);
    }

    private void setText(){
        dispose();
    }

    private void onOK() {

        dataset.setExperimentalNotes(this.notesArea.getText());
        dataset.setNoteTitle(this.titleField.getText());

        dispose();
    }

    public String getText(){
        return notesArea.getText();
    }


    public String getFilename(){
        filename = filenameField.getText();
        int length = filename.length();

        if (filename.substring(length-4, length).equals(".pdf")){
            filename = filename.substring(0, length-4);
        }

        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private void onCancel() {
// add your code here if necessary
        dispose();
    }

}