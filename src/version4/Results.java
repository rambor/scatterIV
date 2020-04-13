package version4;

import version4.tableModels.ResultsModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;

public class Results extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel resultsPanel;
    private ResultsModel resultsModel;

    public Results(Collection collection) {

        JTable resultsTable;
        resultsTable = new JTable(new ResultsModel()); // create table
        JTableHeader resultsHeader = resultsTable.getTableHeader(); // create header and render
        resultsHeader.setDefaultRenderer(new HeaderRenderer(resultsTable));
        resultsModel = (ResultsModel) resultsTable.getModel(); // make resultsModel from Table
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment( JLabel.CENTER );
        rightRenderer.setHorizontalAlignment( JLabel.RIGHT );
        leftRenderer.setHorizontalAlignment( JLabel.LEFT );

        resultsTable.setRowHeight(30);
        resultsTable.setBackground(Color.WHITE);
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(10); // file row
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(250); // filename
        resultsTable.getColumnModel().getColumn(1).setCellRenderer( leftRenderer );
        resultsTable.getColumnModel().getColumn(2).setCellRenderer( centerRenderer );
        resultsTable.getColumnModel().getColumn(3).setCellRenderer( centerRenderer );
        resultsTable.getColumnModel().getColumn(4).setCellRenderer( centerRenderer );
        resultsTable.getColumnModel().getColumn(5).setCellRenderer( centerRenderer );
        resultsTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(8).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(9).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(10).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(11).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(12).setCellRenderer(centerRenderer);
        resultsTable.getColumnModel().getColumn(13).setCellRenderer(centerRenderer);

        JScrollPane resultsList = new JScrollPane(resultsTable);
        resultsPanel.add(resultsList);
        resultsTable.setFillsViewportHeight(false);
        resultsList.setOpaque(true);
        resultsPanel.setOpaque(true);

        int total = collection.getTotalDatasets();
        for(int i=0; i<total; i++){
            if (collection.getDataset(i).getInUse()){
                resultsModel.addDataset(collection.getDataset(i));
            }
        }

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
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    private class HeaderRenderer implements TableCellRenderer {

        DefaultTableCellRenderer renderer;

        public HeaderRenderer(JTable table) {
            renderer = (DefaultTableCellRenderer)
                    table.getTableHeader().getDefaultRenderer();
            renderer.setHorizontalAlignment(JLabel.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            return renderer.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, col);
        }
    }
}
