package version4.tableModels;

import version4.Collection;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CheckBoxCellEditorRenderer extends AbstractCellEditor implements TableCellRenderer, TableCellEditor, ActionListener {

    private JCheckBox checkBox;
    Collection collectionSelected;

    public CheckBoxCellEditorRenderer(Collection collection) {
        collectionSelected=collection;
        this.checkBox = new JCheckBox();
        checkBox.addActionListener(this);
        checkBox.setOpaque(false);
        checkBox.setBackground(Color.WHITE);
        checkBox.setMaximumSize(new Dimension(30,30));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

        checkBox.setSelected(Boolean.TRUE.equals(value));
        return checkBox;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {

            if (column == 2) {

                collectionSelected.getDataset(row).setInUse(!(Boolean)value);

//                if (log10IntensityPlot.isVisible()){
//                    log10IntensityPlot.changeVisibleSeries(row, collectionSelected.getDataset(row).getInUse());
//                }
//
//                if (kratky.isVisible()){
//                    kratky.changeVisibleSeries(row, collectionSelected.getDataset(row).getInUse());
//                }
//
//                if (qIqPlot.isVisible()){
//                    qIqPlot.changeVisibleSeries(row, collectionSelected.getDataset(row).getInUse());
//                }
//
//                if (errorPlot.isVisible()){
//                    errorPlot.changeVisibleSeries(row, collectionSelected.getDataset(row).getInUse());
//                }
//
//                if (powerLawPlot.isVisible()){
//                    powerLawPlot.changeVisibleSeries(row, collectionSelected.getDataset(row).getInUse());
//                }
            }

        checkBox.setSelected(Boolean.TRUE.equals(value));
        return checkBox;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        stopCellEditing();
    }

    @Override
    public Object getCellEditorValue() {
        return checkBox.isSelected();
    }
}