package version4;

import version4.tableModels.DataFileElement;

import javax.swing.*;
import java.awt.*;

/**
 * Created by robertrambo on 14/01/2016.
 */
public class DataFilesListRenderer extends JCheckBox implements ListCellRenderer {
    Color setColor;
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus) {
        setEnabled(list.isEnabled());
        setSelected(((DataFileElement)value).isSelected());
        setFont(list.getFont());
        setBackground(list.getBackground());
        setForeground(list.getForeground());
        setText(value.toString());
        return this;
    }
}
