package version4;

import version4.InverseTransform.IFTObject;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.EventObject;

/**
 * Created by robertrambo on 25/01/2016.
 */
public class PrSpinnerEditor extends DefaultCellEditor implements ChangeListener {
    JSpinner spinner;
    JSpinner.DefaultEditor editor;
    JTextField textField;
    boolean valueSet;
    private int rowID;
    private PrModel prModel;
    private int colID;
    private int priorValue;
    private int lastValue;

    private double dPriorValue;

    private JLabel status;
    private JCheckBox qIqFit;
    private JCheckBox backgroundCheckBox;
    private JCheckBox positiveOnly;

    // Initializes the spinner.
    public PrSpinnerEditor(PrModel prModel,
                           JLabel status,
                           JCheckBox qIqCheckBox,
                           JCheckBox excludeBackground,
                           JCheckBox positiveOnly) {
        super(new JTextField());
        spinner = new JSpinner();

        this.backgroundCheckBox = excludeBackground;
        this.positiveOnly = positiveOnly;

        this.prModel = prModel;

        this.status = status;
        this.qIqFit = qIqCheckBox;

        editor = ((JSpinner.DefaultEditor)spinner.getEditor());
        textField = editor.getTextField();

//        textField.addFocusListener( new FocusListener() {
//            public void focusGained( FocusEvent fe ) {
//
//            }
//
//            public void focusLost( FocusEvent fe ) {
//                //System.out.println("FocusLost " + collectionSelected.getDataset(rowID).getData().getX(0) + " | value " + spinner.getValue());
//            }
//        });

        textField.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent ae ) {
                stopCellEditing();
            }
        });

        spinner.addChangeListener((ChangeListener) this);
    }


    public void stateChanged(ChangeEvent e){

        RealSpace prDataset = prModel.getDataset(rowID);

        int oldStart = prDataset.getStart();
        int oldStop = prDataset.getStop();

        int limit;

        if (this.colID == 4){ // lower(start) spinner

            int valueOfSpinner = (Integer)this.spinner.getValue();
            int temp = (Integer)this.spinner.getValue();

            if (((Integer)this.spinner.getValue() < prDataset.getLowerQIndexLimit()) || (valueOfSpinner > oldStop)){
                this.spinner.setValue(prDataset.getLowerQIndexLimit());
                this.priorValue = prDataset.getLowerQIndexLimit();
                System.out.println("top");
            } else {
                //moving up or down?
                int direction = valueOfSpinner - oldStart;
                if (direction > 0) {
                    prDataset.decrementLow(valueOfSpinner);
                } else if (direction < 0){
                    prDataset.incrementLow(valueOfSpinner);
                }
                this.priorValue = temp;
            }

            prDataset.setStart(valueOfSpinner);
            updateModel(prDataset);
        } else if (colID == 5) {
            int temp = (Integer)this.spinner.getValue();
            limit = prDataset.getMaxCount();
            int valueOfSpinner = (Integer)this.spinner.getValue();

            if (valueOfSpinner >= limit){
                this.spinner.setValue(prDataset.getStop());
            } else {
                //moving up or down?
                int direction = valueOfSpinner - oldStop;

                if (direction < 0) {
                    prDataset.decrementHigh(valueOfSpinner);
                } else if (direction > 0){
                    prDataset.incrementHigh(valueOfSpinner);
                }
                this.priorValue = temp;
            }

            updateModel(prDataset);
        } else if (colID==9){

            double temp = (Double)this.spinner.getValue();
            prDataset.setDmax((float)temp);
            status.setText("Finished: d_max set to " + prDataset.getDmax());
            updateModel(prDataset);
        }

        //recalculate P(r) distributions
        status.setText("Analyzing, please wait");
        // calculate new Fit
        prModel.fireTableDataChanged();
    }

    private void updateModel(RealSpace prDataset){
        IFTObject tempPr = new IFTObject(
                prDataset,
                prModel.getLambdaBoxSelectedItem(),
                prModel.getUseMoore(),
                prModel.getCBoxSelectedItem(),
                prModel.getUseDirectFT(),
                prModel.getUseLegendre(),
                prModel.getUseL2(),
                prModel.getUseSVD(),
                backgroundCheckBox.isSelected(),
                positiveOnly.isSelected()
        );

        tempPr.run();
        prDataset.calculateIntensityFromModel(qIqFit.isSelected());
    }

    // Prepares the spinner component and returns it.
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {

        colID = column;
        rowID = row;

        lastValue = prModel.getDataset(rowID).getAllData().getItemCount();

        if (colID == 4) {
            priorValue = prModel.getDataset(rowID).getStart();
            spinner.setModel(new SpinnerNumberModel(priorValue, 1, lastValue, 10));
        } else if (colID == 5) {
            priorValue = prModel.getDataset(rowID).getStop();
            spinner.setModel(new SpinnerNumberModel(priorValue, 1, lastValue, 10));
        } else if (colID == 9) {
            dPriorValue = prModel.getDataset(rowID).getDmax();
            spinner.setModel(new SpinnerNumberModel(dPriorValue, 10, 1000, 0.5));
            //spinner.setValue(dPriorValue);
        }

        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                textField.requestFocus();
            }
        });

        return spinner;
    }

    /**
     *
     * @param eo
     * @return
     */
    public boolean isCellEditable( EventObject eo ) {

        if ( eo instanceof KeyEvent) {

            KeyEvent ke = (KeyEvent)eo;
            System.err.println("key event: "+ke.getKeyChar());
            textField.setText(String.valueOf(ke.getKeyChar()));
            valueSet = true;

        } else {
            valueSet = false;
        }
        return true;
    }

    // Returns the spinners current value.
    public Object getCellEditorValue() {
        return spinner.getValue();
    }

    public boolean stopCellEditing() {

        System.err.println("Stopping edit");
        try {

            editor.commitEdit();
            spinner.commitEdit();

        } catch ( java.text.ParseException e ) {
            JOptionPane.showMessageDialog(null,
                    "Invalid value, discarding.");
        }
        return super.stopCellEditing();
    }
} // end of PrSpinnerEditor
