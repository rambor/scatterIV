package version4.sasCIF;

import version4.Dataset;

public class SasResult {

    private double dmax;
//    private double dmax_error;
    private int guinier_point_max, quinier_point_min;
    private double i0_from_guinier, i0_from_guinier_error;
    private double i0_from_pr;
    private double i0_from_pr_error;
    private int porod_volume, porod_volume_error;
    private double rg_from_guinier, rg_from_guinier_error;
    private double rg_from_PR, rg_from_pr_error;
    private double porod_exponenet, porod_exponent_error;
    private double volume_of_correlation_from_guinier, volume_of_correlation_from_real;

    // manually set using pop-up
    private String comments;
    private String length_units="Angstrom";

    private Dataset dataset;
    public SasResult(Dataset dataset){
        this.dataset = dataset;
        this.update();
    }

    public String getTextForOutput(int id){
        String tempHeader = String.format("# %n");
        tempHeader += String.format("_sas_result.id %d %n", id);
        tempHeader += String.format("_sas_result.experimental_MW ? %n");
        tempHeader += String.format("_sas_result.experimental_MW_error ? %n");

        if (dataset.getGuinierRg() > 0){
            tempHeader += String.format("_sas_result.reciprocal_space_I0 %.4E %n", dataset.getGuinierIzero());
            tempHeader += String.format("_sas_result.reciprocal_space_I0_error %.4E %n", dataset.getGuinierIzeroError());
            tempHeader += String.format("_sas_result.reciprocal_space_Rg %.2E %n", dataset.getGuinierRg());
            tempHeader += String.format("_sas_result.reciprocal_space_Rg_error %.2E %n", dataset.getGuinierRgerror());
        } else {
            tempHeader += String.format("_sas_result.reciprocal_space_I0 ? %n");
            tempHeader += String.format("_sas_result.reciprocal_space_I0_error ? %n");
            tempHeader += String.format("_sas_result.reciprocal_space_Rg ? %n");
            tempHeader += String.format("_sas_result.reciprocal_space_Rg_error ? %n");
        }


        if (dataset.getRealSpaceModel().getRg() > 0){
            tempHeader += String.format("_sas_result.real_space_I0 ? %n");
            tempHeader += String.format("_sas_result.real_space_I0_error ? %n");
            tempHeader += String.format("_sas_result.real_space_Rg ? %n");
            tempHeader += String.format("_sas_result.real_space_Rg_error ? %n");
            tempHeader += String.format("_sas_result.real_space_volume %n");
            tempHeader += String.format("_sas_result.real_space_volume_error ? %n");
        }


        if (dataset.getPorodVolume() > 0){
            tempHeader += String.format("_sas_result.reciprocal_space_volume %d %n", dataset.getPorodVolume());
            tempHeader += String.format("_sas_result.reciprocal_space_volume_error ? %n");
            tempHeader += String.format("_sas_result.porod_exponent %.2f %n", dataset.getPorodExponent());
            tempHeader += String.format("_sas_result.porod_exponent_error %.2f %n", dataset.getPorodExponentError());

            if (dataset.getGuinierRg() > 0){
                tempHeader += String.format("_sas_result.reciprocal_porod_volume_mass_1p11 %d %n", dataset.getPorodVolumeMass1p1() );
                tempHeader += String.format("_sas_result.reciprocal_porod_volume_mass_1p37 %d %n", dataset.getPorodVolumeMass1p37() );
            } else {
                tempHeader += String.format("_sas_result.reciprocal_porod_volume_mass_1p11 ? %n");
                tempHeader += String.format("_sas_result.reciprocal_porod_volume_mass_1p37 ? %n");
            }

            if (dataset.getRealRg() > 0){
                tempHeader += String.format("_sas_result.real_porod_volume_mass_1p11 %d %n", dataset.getPorodVolumeRealMass1p1() );
                tempHeader += String.format("_sas_result.real_porod_volume_mass_1p37 %d %n", dataset.getPorodVolumeRealMass1p37() );
            } else {
                tempHeader += String.format("_sas_result.real_porod_volume_mass_1p11 ? %n");
                tempHeader += String.format("_sas_result.real_porod_volume_mass_1p37 ? %n");
            }
        } else {
            tempHeader += String.format("_sas_result.reciprocal_space_volume ? %n");
            tempHeader += String.format("_sas_result.reciprocal_space_volume_error ? %n");
            tempHeader += String.format("_sas_result.porod_exponent ? %n");
            tempHeader += String.format("_sas_result.porod_exponent_error ? %n");
            tempHeader += String.format("_sas_result.reciprocal_porod_volume_mass_1p11 ? %n");
            tempHeader += String.format("_sas_result.reciprocal_porod_volume_mass_1p37 ? %n");
            tempHeader += String.format("_sas_result.real_porod_volume_mass_1p11 ? %n");
            tempHeader += String.format("_sas_result.real_porod_volume_mass_1p37 ? %n");
        }

        return tempHeader;
    }

    public double getDmax() {
        return dmax;
    }

    public void setDmax(double dmax) {
        this.dmax = dmax;
    }

//    public double getDmax_error() {
//        return dmax_error;
//    }
//
//    public void setDmax_error(double dmax_error) {
//        this.dmax_error = dmax_error;
//    }

    public int getGuinier_point_max() {
        return guinier_point_max;
    }

    public void setGuinier_point_max(int guinier_point_max) {
        this.guinier_point_max = guinier_point_max;
    }

    public int getQuinier_point_min() {
        return quinier_point_min;
    }

    public void setQuinier_point_min(int quinier_point_min) {
        this.quinier_point_min = quinier_point_min;
    }

    public double getI0_from_guinier() {
        return i0_from_guinier;
    }

    public void setI0_from_guinier(double i0_from_guinier) {
        this.i0_from_guinier = i0_from_guinier;
    }

    public double getI0_from_guinier_error() {
        return i0_from_guinier_error;
    }

    public void setI0_from_guinier_error(double i0_from_guinier_error) {
        this.i0_from_guinier_error = i0_from_guinier_error;
    }

    public double getI0_from_pr() {
        return i0_from_pr;
    }

    public void setI0_from_pr(double i0_from_pr) {
        this.i0_from_pr = i0_from_pr;
    }

    public double getI0_from_pr_error() {
        return i0_from_pr_error;
    }

    public void setI0_from_pr_error(double i0_from_pr_error) {
        this.i0_from_pr_error = i0_from_pr_error;
    }

    public int getPorod_volume() {
        return porod_volume;
    }

    public void setPorod_volume(int porod_volume) {
        this.porod_volume = porod_volume;
    }

    public double getPorod_volume_error() {
        return porod_volume_error;
    }

    public void setPorod_volume_error(int porod_volume_error) {
        this.porod_volume_error = porod_volume_error;
    }

    public double getRg_from_guinier() {
        return rg_from_guinier;
    }

    public void setRg_from_guinier(double rg_from_guinier) {
        this.rg_from_guinier = rg_from_guinier;
    }

    public double getRg_from_guinier_error() {
        return rg_from_guinier_error;
    }

    public void setRg_from_guinier_error(double rg_from_guinier_error) {
        this.rg_from_guinier_error = rg_from_guinier_error;
    }

    public double getRg_from_PR() {
        return rg_from_PR;
    }

    public void setRg_from_PR(double rg_from_PR) {
        this.rg_from_PR = rg_from_PR;
    }

    public double getRg_from_pr_error() {
        return rg_from_pr_error;
    }

    public void setRg_from_pr_error(double rg_from_pr_error) {
        this.rg_from_pr_error = rg_from_pr_error;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public double getPorod_exponenet() {
        return porod_exponenet;
    }

    public void setPorod_exponenet(double porod_exponenet) {
        this.porod_exponenet = porod_exponenet;
    }

    public double getPorod_exponent_error() {
        return porod_exponent_error;
    }

    public void setPorod_exponent_error(double porod_exponent_error) {
        this.porod_exponent_error = porod_exponent_error;
    }

    public String getLength_units() {
        return length_units;
    }

    public void setLength_units(String length_units) {
        this.length_units = length_units;
    }

    public double getVolume_of_correlation_from_guinier() {
        return volume_of_correlation_from_guinier;
    }

    public void setVolume_of_correlation_from_guinier(double volume_of_correlation_from_guinier) {
        this.volume_of_correlation_from_guinier = volume_of_correlation_from_guinier;
    }

    public double getVolume_of_correlation_from_real() {
        return volume_of_correlation_from_real;
    }

    public void setVolume_of_correlation_from_real(double volume_of_correlation_from_real) {
        this.volume_of_correlation_from_real = volume_of_correlation_from_real;
    }

    public void update(){
        if (dataset.getGuinierIzero() > 0){
            this.guinier_point_max = dataset.getIndexOfUpperGuinierFit();
            this.guinier_point_max = dataset.getIndexOfLowerGuinierFit();
            this.i0_from_guinier = dataset.getGuinierIzero();
            this.rg_from_guinier = dataset.getGuinierRg();
            this.i0_from_guinier_error = dataset.getGuinierIzeroError();
            this.rg_from_guinier_error = dataset.getGuinierRgerror();
        }

        if (dataset.getVC() > 0){
            this.volume_of_correlation_from_guinier = dataset.getVC();
        }

        if (dataset.getVCReal() > 0){
            this.volume_of_correlation_from_real = dataset.getVCReal();
        }
//        if (dataset.getExperimentalNotes().length() > 0){
//            this.comments = dataset.getExperimentalNotes();
//        }

        if (dataset.getDmax() > 1){
            this.dmax = dataset.getDmax();
            this.rg_from_PR = dataset.getRealRg();
            this.rg_from_pr_error = dataset.getRealRgSigma();
            this.i0_from_pr = dataset.getRealIzero();
            this.i0_from_pr_error = dataset.getRealIzeroSigma();
        }

        if (dataset.getPorodExponent() > 0){
            this.porod_exponenet = dataset.getPorodExponent();
            this.porod_exponent_error = dataset.getPorodExponentError();
        }

        if (dataset.getPorodVolume() > 0){
            this.porod_volume = dataset.getPorodVolume();
        }
    }
}
