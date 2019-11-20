package version4;


import java.awt.*;

/**
 * Created by robertrambo on 11/01/2016.
 */
public class Symbol {
    private Color currentColor;
    private float stroke;
    private int pointSize;

    public Symbol(Color selected, float weight, int size){
        currentColor = selected;
        stroke = weight;
        pointSize = size;
    }

    public Color getColor(){
        return currentColor;
    }

    public float getStroke(){
        return stroke;
    }

    public int getPointSize(){
        return pointSize;
    }
}