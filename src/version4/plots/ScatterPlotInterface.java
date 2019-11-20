package version4.plots;

import java.awt.*;

interface ScatterPlotInterface {

    boolean isVisible();
    void setNotify(boolean state);

    void changeColor(int id, Color newColor, float thickness, int pointsize);

    void setPlotScales(int total);

    void plot();
}
