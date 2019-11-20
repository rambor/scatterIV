package version4.SEC;

public class Signals{
    private final int id;
    private final double signal;
    private final double izero;
    private final double rg;
    private double total_qIq;
    private int isBuffer=1;

    public Signals(int id, double signal, double izero, double rg){
        this.id = id;
        this.signal = signal;
        this.izero = izero;
        this.rg = rg;
    }
    public void setTotal_qIq(double qid){ this.total_qIq = qid;}

    public int getId() {
        return id;
    }

    public double getSignal() {
        return signal;
    }

    public double getIzero() {
        return izero;
    }

    public double getRg() {
        return rg;
    }

    public double getTotal_qIq() {
        return total_qIq;
    }

    public void setIsBuffer(boolean value){ isBuffer = value ? 1 : 0; }

    public int getIsBuffer() {
        return isBuffer;
    }
}