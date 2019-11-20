package version4;

import FileManager.WorkingDirectory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class StructureFactorTest extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox comboBox1;
    private JLabel SFLabel;
    private JLabel statusLabel;
    private Collection inUse;
    private Dataset datasetStructureFactor;
    private Dataset datasetFormFactor;
    private WorkingDirectory workingDirectory;

    public StructureFactorTest(Collection collectionSelected, Dataset datasetWithStructureFactor, WorkingDirectory workingDirectory) {

        this.datasetStructureFactor = datasetWithStructureFactor;
        this.inUse = collectionSelected;
        SFLabel.setText(datasetWithStructureFactor.getFileName());
        this.workingDirectory = workingDirectory;

        comboBox1.setModel(new FComboBoxModel(collectionSelected));
        comboBox1.setRenderer(new CECellRenderer());

        comboBox1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                JComboBox cb = (JComboBox)e.getSource();
                Dataset tempDataset = (Dataset) cb.getSelectedItem();

                if (tempDataset.getRealSpaceModel().getTotalFittedCoefficients() < 2){
                    statusLabel.setText("Must select dataset with P(r)-distribution");
                    statusLabel.setForeground(Color.red);
                } else {
                    datasetFormFactor = tempDataset;
                    statusLabel.setText("");
                }
            }
        });

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

    private void onOK() {
        // add your code here
        Thread refineIt = new Thread(){
            public void run() {

                StructureFactor SF = new StructureFactor(
                        datasetStructureFactor,
                        datasetFormFactor,
                        workingDirectory.getWorkingDirectory());
                SF.createPlot();
            }

        };

        refineIt.start();

        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }



//    public static void main(String[] args) {
//        StructureFactorTest dialog = new StructureFactorTest();
//        dialog.pack();
//        dialog.setVisible(true);
//        System.exit(0);
//    }



    class FComboBoxModel extends AbstractListModel implements ComboBoxModel {

        private Object selectedItem;
        private Collection collection;

        public FComboBoxModel(Collection collectioninUse){
            this.collection = collectioninUse;
        }

        public void setSelectedItem(Object anItem) {
            selectedItem = anItem; // to select and register an
        } // item from the pull-down list

        // Methods implemented from the interface ComboBoxModel
        public Object getSelectedItem() {
            return selectedItem; // to add the selection to the combo box
        }

        public int getSize(){
            return collection.getTotalDatasets();
        }

        public Object getElementAt(int i){
            return collection.getDataset(i);
        }

        public void updateCollection(Collection collectionInUse){
            this.collection = collectionInUse;
        }
    }


    class CECellRenderer implements ListCellRenderer {

        protected DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {


            JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, index,
                    isSelected, cellHasFocus);

            if (value instanceof Dataset) {
//            renderer.setBackground((Color) value);
                renderer.setText((((Dataset) value).getId()+1) + " - " + ((Dataset) value).getFileName());
            }

            return renderer;
        }

    }
}
