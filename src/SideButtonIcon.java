import javax.swing.*;
import java.awt.*;

public class SideButtonIcon {
    private JPanel backgroundPanel;
    private JLabel iconLabel;
    private JLabel label;
    final private Color labelBaseColor;
    private Color baseColor;

    public SideButtonIcon(JPanel bckpanel, JLabel icon, JLabel label){
        this.backgroundPanel = bckpanel;
        this.iconLabel = icon;
        this.label = label;
        labelBaseColor = label.getForeground();
    }

    public void changeBackgroundColor(Color color){
        backgroundPanel.setBackground(color);
        iconLabel.setBackground(color);
        label.setBackground(color);
    }

    public void highlight(Color highlightColor){
        label.setForeground(backgroundPanel.getBackground());
        baseColor = backgroundPanel.getBackground();
        this.changeBackgroundColor(highlightColor);
    }

    public void dehighlight(){
        label.setForeground(labelBaseColor);
        this.changeBackgroundColor(baseColor);
    }

}
