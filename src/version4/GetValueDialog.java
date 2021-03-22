package version4;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import version4.plots.XYPlot;

import javax.swing.*;
import java.awt.event.*;

public class GetValueDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField inputField;
    private JLabel inputLabel;
    private JLabel inputTitleLabel;
    private Double newValue;
    private JFreeChart chart;
    private String type;

    public GetValueDialog(String title, String labelText, JFreeChart chart, String type) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        inputTitleLabel.setText(title);
        inputLabel.setText(labelText);
        this.chart = chart;
        this.type = type;

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
        try{
            newValue = Double.parseDouble(inputField.getText());

                if (type.equals("upperDomain")){
                    this.chart.getXYPlot().getDomainAxis().setUpperBound(newValue);
                } else if (type.equals("lowerRange")){
                    this.chart.getXYPlot().getRangeAxis().setLowerBound(newValue);
                }

            dispose();
        } catch (NumberFormatException e){
            inputTitleLabel.setText("Use a number, please check");
        }
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

}
