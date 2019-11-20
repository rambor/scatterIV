package version4;

/**
 * Created by robertrambo on 05/01/2017.
 */
public class Bead {
    private double xpos, ypos, zpos;
    private int index;
    private double radius;
    private double volume;
    public Bead(int index, double xpos, double ypos, double zpos, double radius){
        this.xpos = xpos;
        this.ypos = ypos;
        this.zpos = zpos;
        this.radius = radius;
        this.volume = 4.0/3.0*Math.PI*radius*radius*radius;
        this.index = index;
    }

    public double getVolume(){
        return volume;
    }

    public double getSquaredDistance(double[] coords){
        double diffx = xpos-coords[0];
        double diffy = ypos-coords[1];
        double diffz = zpos-coords[2];
        return (diffx*diffx + diffy*diffy + diffz*diffz);
    }

    public double getSquaredDistance(Bead bead){
        double diffx = xpos-bead.xpos;
        double diffy = ypos-bead.ypos;
        double diffz = zpos-bead.zpos;
        return (diffx*diffx + diffy*diffy + diffz*diffz);
    }

    public String getPDBLine(int count){
        return String.format("%-6s%5d %4s%1s%3s %1s%4d%1s   %8.3f%8.3f%8.3f%6.2f%6.2f %n", "ATOM  ", count, "CA", " ", "GLY", "A", count, " ", xpos, ypos, zpos, 1.0, 1.0);
    }
}