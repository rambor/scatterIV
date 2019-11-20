package version4.tableModels;

import version4.Collection;
import version4.Dataset;
import version4.RealSpace;
import version4.Symbol;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ColorEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {
    Color currentColor;
    JComboBox pointSizes;
    JComboBox thickBox;
    JButton button;
    JColorChooser colorChooser;
    JDialog dialog;
    String tableModel;
    Collection collectionSelected;
    int data_row;
    protected static final String EDIT = "edit";

    public ColorEditor(Collection collection) {
        //Set up the editor (from the table's point of view),
        //which is a button.
        //This button brings up the color chooser dialog,
        //which is the editor from the user's point of view.
        button = new JButton();
        button.setActionCommand(EDIT);
        button.addActionListener(this);
        button.setBorderPainted(false);
        collectionSelected=collection;

        //Set up the dialog that the button brings up.
        colorChooser = new JColorChooser();
        dialog = JColorChooser.createDialog(button,
                "Pick a Color or Change Size",
                true,  //modal
                colorChooser,
                this,  //OK button handler
                null); //no CANCEL button handler
    }

    /**
     * Handles events from the editor button and from
     * the dialog's OK button.
     */
    public void actionPerformed(ActionEvent e) {
        if (EDIT.equals(e.getActionCommand())) {
            //The user has clicked the cell, so
            //bring up the dialog.
            button.setBackground(currentColor);
            colorChooser.setColor(currentColor);
            JPanel preview = new JPanel();

            JLabel pointTitle = new JLabel("Point Size");
            String[] sizes = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "15", "17", "19", "21"};
            pointSizes = new JComboBox(sizes);
            preview.add(pointTitle);

            JLabel thicknessTitle = new JLabel(" | Line Stroke");
            String[] thicknesses = {"0.5", "1.0", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0"};
            thickBox = new JComboBox(thicknesses);
            preview.add(thicknessTitle);

            System.out.println("ColorEditor :: " + collectionSelected.getTotalDatasets() + " " + data_row);
                Dataset temp = collectionSelected.getDataset(data_row);
                int index=0;
                for (int i=0; i<sizes.length; i++){
                    if (temp.getPointSize() == Integer.parseInt(sizes[i])){
                        index = i;
                    }
                }

                pointSizes.setSelectedIndex(index);
                preview.add(pointSizes);

                index=0;
                for (int i=0; i<thicknesses.length; i++){
                    if (temp.getStroke().getLineWidth() == Float.parseFloat(thicknesses[i])){
                        index = i;
                    }
                }

                thickBox.setSelectedIndex(index);
                preview.add(thickBox);


            colorChooser.setPreviewPanel(preview);
            dialog.setVisible(true);

            //Make the renderer reappear.
            fireEditingStopped();

        } else { //User pressed dialog's "OK" button.
            currentColor = colorChooser.getColor();
        }
    }

    //Implement the one CellEditor method that AbstractCellEditor doesn't.
    public Object getCellEditorValue() {
//        int thickIndex = thickBox.getSelectedIndex();
//        int pointIndex = pointSizes.getSelectedIndex();

        float thickness = Float.parseFloat((String) thickBox.getSelectedItem());
        int pointSize = Integer.parseInt( (String)pointSizes.getSelectedItem());
        Symbol newInfo = new Symbol(currentColor, thickness, pointSize);
//        reColorPlots(data_row, currentColor, thickness, pointSize);
        return newInfo;
        //return currentColor;
    }

    //Implement the one method defined by TableCellEditor.
    public Component getTableCellEditorComponent(JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 int row,
                                                 int column) {


//            if (table.getModel().getClass() == AnalysisModel.class){
//                System.out.println("Analysis Table Model" + table.getModel().getClass() + " | ");
//            } else if (table.getModel().getClass() == PrModel.class){
//                System.out.println("Pr Table Model" + table.getModel().getClass() + " | ");
//            }

        tableModel = table.getModel().getClass().toString();
        currentColor = (Color)value;
        data_row = row;
        return button;
    }
}
