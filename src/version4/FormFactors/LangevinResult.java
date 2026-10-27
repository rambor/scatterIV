package version4.FormFactors;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Output of a {@link LangevinFitter} run. All distributions are densities in ln(parameter),
 * i.e. d(fraction)/d ln p, normalised to unit area, averaged over replicas.
 */
public class LangevinResult {

    public String shapeLabel;
    public String[] parameterNames;
    public int dim;
    public int replicas;
    public int particles;

    // data and fit
    public double[] q, intensity, sigma;
    public double[] fit;          // mean of s*Ibar(q)+b over the sampling phase (and replicas)
    public double[] fitSpread;    // standard deviation of the fit over samples
    public double chi2Reduced;    // reduced chi^2 of the mean fit
    public double scale, background;
    public double[] temperature;  // temperature used while sampling, per replica
    public double[] chi2Min;      // best reduced chi^2 reached in the quench, per replica
    public double[] chi2Best;     // lowest instantaneous reduced chi^2 after the error floor is removed (profile scans)
    public double[] chi2Target;   // reduced chi^2 the temperature was tuned to, per replica

    // histograms, [param][bin]
    public double[][] binCenters; // real units (Angstrom)
    public double[][] binLnEdges; // ln units, length bins+1
    /** intensity-weighted (each particle = equal share of I(0)); this is what the particles sample directly */
    public double[][] intensityDensity, intensityDensityStd;
    /** volume-fraction weighted (re-weighted by 1/V) */
    public double[][] volumeDensity, volumeDensityStd;
    public double[][] numberDensity, numberDensityStd;
    /** pair histograms (volume-fraction weighted), keyed by pairs[k] = {i,j}; [k][bin_i][bin_j] */
    public int[][] pairs;
    public double[][][] pairDensity;

    // moments
    public double[] intensityMean, intensityStd, volumeMean, volumeStd, numberMean, numberStd;
    public double intensityAverageVolume; // <V>_I = <V^3>_n/<V^2>_n
    public double numberAverageVolume;   // <V>_n
    public double volumeAverageVolume;   // <V>_v = <V^2>_n / <V>_n

    // diagnostics (replica 0)
    public double[] traceChi2, traceTemperature;
    public double[][] finalParticles;    // [particle][param], real units
    public double elapsedSeconds;
    /** error calibration / rebinning report (null if switched off); q, intensity, sigma above are the prepared data */
    public DataPreparation.Report preparation;
    /** 1 - chi2 / chi2(zero model); close to 1 for any meaningful fit */
    public double fitExplainedFraction;
    /** discrete species (e.g. empty / filled cores); null when there is a single species */
    public String[] speciesNames;
    public double[] speciesIntensityFraction, speciesVolumeFraction, speciesNumberFraction, speciesNumberFractionStd;
    public double[][] speciesMeanParams;   // volume-weighted mean parameters per species [species][param]
    /** residual randomness: lag-1 autocorrelation, runs-test z (negative = too few sign changes), longest run and its p */
    public double residualLag1, runsZ, longestRunP;
    public int longestRun;
    /** KL(final particles || best K-mixture) for K = 1, 2, 3 (nats), and the fitted mixtures */
    public double[] modeKL;
    public String[] modeSummaries;
    /** non-null when the fit is degenerate */
    public String warning;
    /** fitted shape-prior components (null if no prior was used) */
    public String priorSummary;

    public String toRemarks() {
        StringBuilder sb = new StringBuilder();
        sb.append("REMARK 265\n");
        sb.append("REMARK 265 FORM FACTOR DISTRIBUTION : MEAN-FIELD LANGEVIN PARTICLE FLOW\n");
        sb.append(String.format("REMARK 265                         SHAPE : %s%n", shapeLabel));
        if (preparation != null) sb.append(preparation.toRemarks()).append("REMARK 265\n");
        sb.append(String.format("REMARK 265                     PARTICLES : %d%n", particles));
        sb.append(String.format("REMARK 265                      REPLICAS : %d%n", replicas));
        for (int r = 0; r < temperature.length; r++)
            sb.append(String.format("REMARK 265  REP %2d  T : %.4E  CHI2-MIN : %.4f  CHI2-TARGET : %.4f%n",
                    r, temperature[r], chi2Min[r], chi2Target[r]));
        if (warning != null) sb.append("REMARK 265 *** WARNING *** ").append(warning).append('\n');
        sb.append(String.format("REMARK 265      REDUCED CHI2 (MEAN FIT) : %.4f%n", chi2Reduced));
        sb.append(String.format("REMARK 265                  SCALE FACTOR : %.5E%n", scale));
        sb.append(String.format("REMARK 265                    BACKGROUND : %.5E%n", background));
        sb.append(String.format("REMARK 265          NUMBER-AVERAGE <V>_n : %.4E%n", numberAverageVolume));
        sb.append(String.format("REMARK 265          VOLUME-AVERAGE <V>_v : %.4E%n", volumeAverageVolume));
        sb.append("REMARK 265\n");
        sb.append(String.format("REMARK 265       INTENSITY-AVERAGE <V>_I : %.4E%n", intensityAverageVolume));
        sb.append("REMARK 265\n");
        sb.append("REMARK 265 PARAMETER    INT-MEAN  INT-STDEV   VOL-MEAN  VOL-STDEV   NUM-MEAN  NUM-STDEV\n");
        for (int k = 0; k < dim; k++) {
            sb.append(String.format("REMARK 265 %-11s %9.3f %10.3f %10.3f %10.3f %10.3f %10.3f%n",
                    parameterNames[k], intensityMean[k], intensityStd[k], volumeMean[k], volumeStd[k], numberMean[k], numberStd[k]));
        }
        if (speciesNames != null) {
            sb.append("REMARK 265\nREMARK 265 SPECIES            NUMBER-FRACTION  (+- REPL)  VOLUME-FRACTION  INTENSITY-FRACTION   MEAN PARAMS (VOL-WEIGHTED)\n");
            for (int k = 0; k < speciesNames.length; k++) {
                StringBuilder mp = new StringBuilder();
                for (int p = 0; p < dim; p++) mp.append(String.format(" %s=%.2f", parameterNames[p], speciesMeanParams[k][p]));
                sb.append(String.format("REMARK 265 %-16s %10.3f   (%.3f) %15.3f %18.3f  %s%n", speciesNames[k],
                        speciesNumberFraction[k], speciesNumberFractionStd[k], speciesVolumeFraction[k],
                        speciesIntensityFraction[k], mp));
            }
            sb.append("REMARK 265\n");
        }
        sb.append(String.format("REMARK 265 RESIDUALS  LAG-1 AUTOCORR %.2f   RUNS-TEST Z %.2f   LONGEST RUN %d (P = %.3f)%n",
                residualLag1, runsZ, longestRun, longestRunP));
        if (priorSummary != null) sb.append("REMARK 265\n").append(priorSummary);
        if (modeKL != null) {
            sb.append("REMARK 265\n");
            sb.append(String.format("REMARK 265 MODE DIAGNOSTIC  KL(particles || K-mixture), nats :  K=1 %.3f   K=2 %.3f   K=3 %.3f%n",
                    modeKL[0], modeKL[1], modeKL[2]));
            for (int k = 0; k < modeSummaries.length; k++)
                sb.append(String.format("REMARK 265  K=%d FIT:%n", k + 1)).append(modeSummaries[k]);
        }
        sb.append(String.format("REMARK 265%nREMARK 265 RUN TIME : %.1f s%n", elapsedSeconds));
        return sb.toString();
    }

    /** writes prefix_fit.csv, prefix_dist_<param>.csv, prefix_pair_i_j.csv, prefix_trace.csv, prefix_particles.csv */
    public List<Path> writeCsv(Path dir, String prefix) throws IOException {
        Files.createDirectories(dir);
        List<Path> written = new ArrayList<>();

        Path fitFile = dir.resolve(prefix + "_fit.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(fitFile))) {
            w.println("q,I,sigma,fit,fit_spread");
            for (int j = 0; j < q.length; j++)
                w.printf("%.6e,%.6e,%.6e,%.6e,%.6e%n", q[j], intensity[j], sigma[j], fit[j], fitSpread[j]);
        }
        written.add(fitFile);

        for (int k = 0; k < dim; k++) {
            Path f = dir.resolve(prefix + "_dist_" + k + ".csv");
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(f))) {
                w.println("param,lnlo,lnhi,int_density_ln,int_std,vol_density_ln,vol_std,num_density_ln,num_std");
                for (int b = 0; b < binCenters[k].length; b++) {
                    w.printf("%.5f,%.6f,%.6f,%.6e,%.6e,%.6e,%.6e,%.6e,%.6e%n", binCenters[k][b], binLnEdges[k][b], binLnEdges[k][b + 1],
                            intensityDensity[k][b], intensityDensityStd[k][b],
                            volumeDensity[k][b], volumeDensityStd[k][b], numberDensity[k][b], numberDensityStd[k][b]);
                }
            }
            written.add(f);
        }

        for (int p = 0; p < pairs.length; p++) {
            int i = pairs[p][0], j = pairs[p][1];
            Path f = dir.resolve(prefix + "_pair_" + i + "_" + j + ".csv");
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(f))) {
                double[][] h = pairDensity[p];
                for (int a = 0; a < h.length; a++) {
                    StringBuilder row = new StringBuilder();
                    for (int b = 0; b < h[a].length; b++) {
                        if (b > 0) row.append(',');
                        row.append(String.format("%.5e", h[a][b]));
                    }
                    w.println(row);
                }
            }
            written.add(f);
        }

        Path tr = dir.resolve(prefix + "_trace.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(tr))) {
            w.println("step,chi2red,temperature");
            for (int t = 0; t < traceChi2.length; t++) w.printf("%d,%.6e,%.6e%n", t, traceChi2[t], traceTemperature[t]);
        }
        written.add(tr);

        Path pf = dir.resolve(prefix + "_particles.csv");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(pf))) {
            w.println(String.join(",", parameterNames));
            for (double[] p : finalParticles) {
                StringBuilder row = new StringBuilder();
                for (int k = 0; k < p.length; k++) {
                    if (k > 0) row.append(',');
                    row.append(String.format("%.4f", p[k]));
                }
                w.println(row);
            }
        }
        written.add(pf);
        return written;
    }
}
