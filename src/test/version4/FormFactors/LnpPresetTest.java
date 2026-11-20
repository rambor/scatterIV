package version4.FormFactors;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Choosing the bilayer-shell model by hand must set it up the same way as having it suggested.
 *
 * <p>The headgroup thicknesses start fixed at the 8 A template value. That is only a starting point: held
 * there, they cannot take up the real head size, and the misfit lands on the depth of the form-factor minima
 * instead, which is the one feature the model exists to reproduce. On real PC data the difference is a reduced
 * chi-squared near 20 against one near 7, from the same model on the same points.
 *
 * <p>The preparation that frees them used to run only when the assessment suggested the model and the user
 * accepted the suggestion. A user who read the assessment and chose the model from the dropdown themselves got
 * the unprepared version, and nothing said so.
 */
public class LnpPresetTest {

    @BeforeClass
    public static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static LnpPanel panel() {
        double[][] data = new double[3][200];
        for (int i = 0; i < 200; i++) {
            data[0][i] = 0.005 + 0.33 * i / 199.0;
            data[1][i] = 1.0 / (1 + i);
            data[2][i] = 0.02 * data[1][i];
        }
        return new LnpPanel(DatasetProvider.of(() -> 1, i -> "test", i -> data, i -> 0.0));
    }

    /**
     * A large, polydisperse single bilayer: the parameters fitted to real PC/cholesterol liposome data. The
     * particle radius is far outside the window, so what survives is the bilayer form factor — two broad lobes
     * with deep minima between them, which is the signature no filled-core model can reproduce.
     *
     * <p>The sampling matters: the lobe maximum has to be resolved well enough to register as a peak, and the
     * real file has 2568 points over this range. A few hundred is too coarse and the assessment sees nothing.
     */
    private static double[][] bilayerData() {
        LnpModel vesicle = new LnpModel(4, false);
        double[] p = vesicle.templateDefaults();
        p[LnpModel.SCALE] = 1;
        p[LnpModel.R0] = 1517; p[LnpModel.SIGMA] = 235;
        p[LnpModel.T1] = 21.2; p[LnpModel.T2] = 24.6; p[LnpModel.T3] = 14.0;
        p[LnpModel.T4] = 0; p[LnpModel.rhoIndex(4)] = 0;
        p[LnpModel.RHO_C] = 0; p[LnpModel.RHO_1] = 0.101;
        p[LnpModel.RHO_2] = -0.025; p[LnpModel.RHO_3] = 0.113;
        return LnpTestData.fromModel(vesicle, p, 0.005, 0.34, 900, 7L);
    }

    /** picking the vesicle from the dropdown frees the headgroup thicknesses */
    @Test
    public void choosingTheVesicleFreesTheHeadgroups() {
        LnpPanel p = panel();
        p.selectPreset(LnpPanel.Preset.VESICLE);
        assertFalse("inner headgroup thickness must be fitted", p.isFixed(LnpModel.T1));
        assertFalse("outer headgroup thickness must be fitted", p.isFixed(LnpModel.T3));
    }

    /** and it is still prepared after going away to another model and coming back */
    @Test
    public void itSurvivesASwitchAwayAndBack() {
        LnpPanel p = panel();
        p.selectPreset(LnpPanel.Preset.VESICLE);
        p.selectPreset(LnpPanel.Preset.EMPTY);
        p.selectPreset(LnpPanel.Preset.VESICLE);
        assertFalse("inner headgroup thickness must be fitted", p.isFixed(LnpModel.T1));
        assertFalse("outer headgroup thickness must be fitted", p.isFixed(LnpModel.T3));
    }

    /**
     * One density has to stay fixed, because the scale and the densities are otherwise degenerate: the fit can
     * multiply every contrast by a constant and divide the scale by it for an identical curve. Freeing the
     * headgroups must not have freed that reference as well.
     */
    @Test
    public void aDensityReferenceIsStillHeld() {
        LnpPanel p = panel();
        p.selectPreset(LnpPanel.Preset.VESICLE);
        assertTrue("the chain density is the reference of the density scale", p.isFixed(LnpModel.RHO_2));
        assertTrue("the water core is the buffer", p.isFixed(LnpModel.RHO_C));
    }

    /**
     * Running the assessment after choosing a filled-core model must put the warning under the dropdown.
     *
     * <p>This is the order a careful user works in — pick a model, look at the data, then fit — and it was the
     * one order in which the warning never appeared: the help text was written when the preset changed, while
     * there was still no assessment to consult, and nothing rewrote it afterwards. The result was a reduced
     * chi-squared in the hundreds with the panel showing a plain, reassuring description of the model.
     */
    @Test
    public void assessingAfterChoosingAFilledCoreStillWarns() {
        double[][] d = bilayerData();
        LnpPanel panel = new LnpPanel(DatasetProvider.of(() -> 1, i -> "bilayer", i -> d, i -> 0.0));
        panel.selectPreset(LnpPanel.Preset.EMPTY);          // the wrong model, chosen first
        assertFalse("nothing is known about the data yet, so there is nothing to warn about",
                panel.presetHelpText().contains("form-factor lobe"));

        panel.runAssessment();                               // and only now is the data looked at
        assertTrue("the assessment should see the bilayer lobe", panel.assessmentFoundFormFactorLobe());
        assertTrue("the help under the dropdown must now carry the warning: " + panel.presetHelpText(),
                panel.presetHelpText().contains("form-factor lobe"));
        assertTrue("and it must name the model to switch to",
                panel.presetHelpText().contains("Vesicle"));
    }

    /** switching to the right model clears the warning again */
    @Test
    public void theWarningGoesAwayOnTheRightModel() {
        double[][] d = bilayerData();
        LnpPanel panel = new LnpPanel(DatasetProvider.of(() -> 1, i -> "bilayer", i -> d, i -> 0.0));
        panel.selectPreset(LnpPanel.Preset.EMPTY);
        panel.runAssessment();
        panel.selectPreset(LnpPanel.Preset.VESICLE);
        assertFalse("the vesicle is the model being recommended, so it needs no warning: " + panel.presetHelpText(),
                panel.presetHelpText().contains("form-factor lobe"));
    }

    /**
     * The model selected when the panel opens is an mRNA-LNP, cargo and mesophase peak included. On bilayer
     * data that model fits far better than it has any right to: the peak component settles on the second
     * form-factor lobe, the curve then follows the data through both minima, and the fit reports an internal
     * spacing of about 51 A that nothing in the sample has. It is much more convincing than an honest failure,
     * so the report has to name it.
     */
    @Test
    public void aPeakSittingOnAFormFactorLobeIsCalledOut() {
        double[][] d = bilayerData();
        LnpPanel panel = new LnpPanel(DatasetProvider.of(() -> 1, i -> "bilayer", i -> d, i -> 0.0));
        panel.runAssessment();
        assertTrue("the data must read as a form-factor lobe for this warning to apply",
                panel.assessmentFoundFormFactorLobe());

        LnpModel mrna = new LnpModel(3, true);
        LnpFitter.Result r = new LnpFitter(mrna, d[0], d[1], d[2]).physicalConstraints(true)
                .fit(mrna.templateDefaults(), mrna.templateFixed(), true, () -> false);
        String report = panel.fitReport(r);

        assertTrue("the report must ask the user to check the peak:\n" + report,
                report.contains("CHECK THIS PEAK"));
        assertTrue("and it must name the model that explains those lobes honestly:\n" + report,
                report.contains("Vesicle"));
    }

    /** a peak fitted to data that really does have a reflection must not be second-guessed */
    @Test
    public void arealReflectionIsNotCalledAnArtefact() {
        double[][] d = LnpTestData.bragg(LnpLattice.byName("Lα"), 62.0, 600, 3, 0.004, 0.34, 900, 5L);
        LnpPanel panel = new LnpPanel(DatasetProvider.of(() -> 1, i -> "lamellar", i -> d, i -> 0.0));
        panel.runAssessment();
        if (panel.assessmentFoundFormFactorLobe()) return;   // not the case under test

        LnpModel mrna = new LnpModel(3, true);
        LnpFitter.Result r = new LnpFitter(mrna, d[0], d[1], d[2]).physicalConstraints(true)
                .fit(mrna.templateDefaults(), mrna.templateFixed(), false, () -> false);
        assertFalse("a clean reflection needs no such caveat",
                panel.fitReport(r).contains("CHECK THIS PEAK"));
    }

    /** the Cargo tab exists and can be shown without a fit having been run */
    @Test
    public void theCargoTabIsReachable() {
        LnpPanel p = panel();
        javax.swing.JTabbedPane tabs = null;
        java.util.ArrayDeque<java.awt.Component> todo = new java.util.ArrayDeque<>();
        todo.add(p);
        while (!todo.isEmpty()) {
            java.awt.Component c = todo.poll();
            if (c instanceof javax.swing.JTabbedPane && tabs == null) tabs = (javax.swing.JTabbedPane) c;
            if (c instanceof java.awt.Container)
                for (java.awt.Component k : ((java.awt.Container) c).getComponents()) todo.add(k);
        }
        assertNotNull("the panel should have tabs", tabs);
        boolean found = false;
        for (int i = 0; i < tabs.getTabCount(); i++) if ("Cargo".equals(tabs.getTitleAt(i))) found = true;
        assertTrue("a Cargo tab should be present", found);
    }

    /** the filled-core models are untouched by this: their templates stay as they were */
    @Test
    public void theFilledCoreModelsAreUnchanged() {
        LnpPanel p = panel();
        p.selectPreset(LnpPanel.Preset.MRNA);
        LnpModel m = new LnpModel(3, true);
        boolean[] template = m.templateFixed();
        for (int k = 0; k < LnpModel.NPAR; k++)
            if (m.uses(k) && !LnpModel.isDensity(k))
                assertEquals("parameter " + k + " should match the template", template[k], p.isFixed(k));
    }
}
