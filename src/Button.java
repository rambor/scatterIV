import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class Button {

    private JPanel backgroundPanel;
    private JLabel label;
    final private Color labelBaseColor;
    private Color baseColor;
    private Color highlightColor;
    private int index;

    public Button(JPanel bckpanel, JLabel label, int index, Color highlightColor) {
        this.backgroundPanel = bckpanel;
        this.label = label;
        labelBaseColor = label.getForeground();
        this.highlightColor = highlightColor;
        this.index = index;

        this.backgroundPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                highlight(highlightColor);
            }
        });

        this.backgroundPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                dehighlight();
            }
        });


        this.backgroundPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                clicked();
            }

            @Override
            public void mouseReleased(MouseEvent e){
                super.mouseReleased(e);
                dehighlight();
            }
        });

    }

    public void changeBackgroundColor(Color color){
        backgroundPanel.setBackground(color);
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

    public void clicked(){
        label.setForeground(Color.RED);
        this.changeBackgroundColor(Color.WHITE);
    }


    public int getIndex(){ return index;}

}
