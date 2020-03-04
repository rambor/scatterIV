package version4.sasCIF;

public class SasDetc {
    private String name; // Eiger 4M
    private float detector_read_out_dead_time;
    private float dist_spec_to_detc;
    private String dead_time_units = "seconds";

    public SasDetc(){
        name = "detector";
        detector_read_out_dead_time = 0.1f;
    }

    public SasDetc(SasDetc detc){
        this.name = detc.name;
        this.detector_read_out_dead_time = detc.detector_read_out_dead_time;
        this.dist_spec_to_detc = detc.dist_spec_to_detc;
        this.dead_time_units = detc.dead_time_units;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getDetector_read_out_dead_time() {
        return detector_read_out_dead_time;
    }

    public void setDetector_read_out_dead_time(float detector_read_out_dead_time) {
        this.detector_read_out_dead_time = detector_read_out_dead_time;
    }

    public String getDead_time_units() {
        return dead_time_units;
    }

    public void setDead_time_units(String dead_time_units) {
        this.dead_time_units = dead_time_units;
    }

    public float getDist_spec_to_detc() {
        return dist_spec_to_detc;
    }

    public void setDist_spec_to_detc(float dist_spec_to_detc) {
        this.dist_spec_to_detc = dist_spec_to_detc;
    }
}
