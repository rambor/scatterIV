package version4;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Created by robertrambo on 11/01/2016.
 */
public final class Constants {


    public static final DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols(Locale.US);

    private Constants(){
        otherSymbols.setDecimalSeparator('.');
        otherSymbols.setGroupingSeparator(',');
    }


    public static final Color SteelBlue = new Color(110,123,139);
    public static final Color IndianRed = new Color(255,106,106);
    public static final Color DodgerBlue = new Color(30,144,255);
    public static final Color DarkGray = new Color(72,72,72);
    public static final Color MediumRed = new Color(255, 102, 102);
    public static final Color RedGray = new Color(202, 184, 188);
    public static final Color LightBlueGray = new Color(210, 210, 255);
    //public static final Color RedGray = new Color(202, 184, 188);

    public static final double INV_PI = 1.0/Math.PI;
    public static final double TWO_PI_2 = (2.0*Math.PI*Math.PI);
    public static final double PI_2 = Math.PI*Math.PI;
    public static final double TWO_DIV_PI = 2.0/Math.PI;
    public static final double HALF_PI = Math.PI*0.50d;

    //FONTS
    public static final BasicStroke stroke3 = new BasicStroke(3.0f);

    public static final Font BOLD_12 = new Font("Dialog", Font.BOLD, 12);
    public static final Font FONT_12 = new Font("Dialog", Font.PLAIN, 12);
    public static final Font FONT_10 = new Font("Dialog", Font.PLAIN, 10);
    public static final Font FONT_8 = new Font("Dialog", Font.PLAIN, 8);
    public static final Font BOLD_16 = new Font("Dialog", Font.BOLD, 16);
    public static final Font FONT_16 = new Font("Dialog", Font.PLAIN, 16);
    public static final Font FONT_BOLD_18 = new Font("Dialog", Font.BOLD, 18);
    public static final Font BOLD_18 = new Font("Dialog", Font.PLAIN, 18);
    public static final Font FONT_BOLD_20 = new Font("Dialog", Font.BOLD, 20);
    public static final Font FONT_BOLD_28 = new Font("Dialog", Font.BOLD, 28);
    public static final Font BOLD_20 = new Font("Dialog", Font.PLAIN, 20);

    public static final DecimalFormat OneDecPlace = new DecimalFormat("0.0", otherSymbols); // = new DecimalFormat("0.0");
    public static final DecimalFormat TwoDecPlace = new DecimalFormat("0.00", otherSymbols);
    public static final DecimalFormat ThreeDecPlace = new DecimalFormat("0.000", otherSymbols);
    public static final DecimalFormat FourDecPlace = new DecimalFormat("0.0000", otherSymbols);
    public static final DecimalFormat Scientific1 = new DecimalFormat("0.#E0", otherSymbols);
    public static final DecimalFormat Scientific2 = new DecimalFormat("0.00E0", otherSymbols);
    public static final DecimalFormat Scientific1dot5e2 = new DecimalFormat("0.00000E00", otherSymbols);
    public static final DecimalFormat Scientific1dot4e2 = new DecimalFormat("0.0000E00", otherSymbols);
    public static final DecimalFormat Scientific1dot3e1 = new DecimalFormat("0.000E0", otherSymbols);
    public static final DecimalFormat Scientific1dot2e1 = new DecimalFormat("0.00E0", otherSymbols);

    public static final DecimalFormat df = new DecimalFormat("0.0000", otherSymbols);

    public static final Shape Ellipse4 = new Ellipse2D.Double(-2.0, -2.0, 4.0, 4.0);
    public static final Shape Ellipse6 = new Ellipse2D.Double(-3.0, -3.0, 6.0, 6.0);
    public static final Shape Ellipse8 = new Ellipse2D.Double(-4.0, -4.0, 8.0, 8.0);


}
