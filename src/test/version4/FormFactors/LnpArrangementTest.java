package version4.FormFactors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Posterior probabilities over cargo arrangements.
 *
 * <p>The tests that matter here are the ones that stop the method being confident and wrong. Three
 * separate mistakes during development each produced a clean-looking answer that was false, and each has a
 * test below: counting oversampled points as independent observations, letting candidates be fitted with
 * different amounts of effort, and giving a cargo term a free shape so it could absorb whatever the
 * envelope failed to describe.
 */
public class LnpArrangementTest {

    /** a core-and-shells curve with nothing inside it */
    private static double[][] emptyParticle(long seed) {
        LnpModel m = new LnpModel(3, false);
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1e6; p[LnpModel.R0] = 220; p[LnpModel.SIGMA] = 26;
        return LnpTestData.fromModel(m, p, 0.007, 0.41, 420, seed);
    }

    /** the same particle with a mesophase peak in it */
    private static double[][] loadedParticle(long seed) {
        LnpModel m = new LnpModel(3, true);
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1e6; p[LnpModel.R0] = 220; p[LnpModel.SIGMA] = 26;
        p[LnpModel.PEAK_Q] = 0.127; p[LnpModel.PEAK_W] = 0.012;
        p[LnpModel.PEAK_A] = 0.35 * m.intensity(p, new double[]{0.127})[0];
        return LnpTestData.fromModel(m, p, 0.007, 0.41, 420, seed);
    }

    private static LnpArrangement.Candidate of(LnpArrangement.Result r, LnpArrangement.Cargo c) {
        for (LnpArrangement.Candidate k : r.candidates) if (k.cargo == c) return k;
        throw new AssertionError("no candidate for " + c);
    }

    /**
     * The control. A method that cannot say "nothing in there" about an empty particle is worse than no
     * method, because every answer it gives afterwards is suspect.
     */
    @Test
    public void anEmptyParticleIsReportedAsEmpty() {
        double[][] d = emptyParticle(3L);
        LnpArrangement.Result r = LnpArrangement.analyse(d[0], d[1], d[2], 580, 0, 0);
        assertEquals("no cargo should win on a particle with none",
                LnpArrangement.Cargo.NONE, r.best().cargo);
        assertTrue("and it should not be certain about it: " + r.best().posterior,
                r.best().posterior > 0.4 && r.best().posterior < 0.995);
    }

    /** and a particle with a mesophase in it is not reported as empty */
    @Test
    public void aLoadedParticleIsNotReportedAsEmpty() {
        double[][] d = loadedParticle(4L);
        LnpArrangement.Result r = LnpArrangement.analyse(d[0], d[1], d[2], 597, 3800, 2);
        assertEquals(LnpArrangement.Cargo.QUASI_PERIODIC, r.best().cargo);
        assertTrue("a real peak should be decisive", r.best().posterior > 0.9);
        assertTrue("and no cargo should be far behind",
                of(r, LnpArrangement.Cargo.NONE).deltaBic > 20);
    }

    /**
     * The count of independent observations is the Shannon number, not the number of recorded points. With
     * the raw count a 5% difference in fit over a thousand oversampled points becomes decisive evidence,
     * and the empty control above returns the wrong answer at a posterior of 1.0000.
     */
    @Test
    public void confidenceComesFromChannelsNotPoints() {
        double[][] d = emptyParticle(5L);
        LnpArrangement.Result r = LnpArrangement.analyse(d[0], d[1], d[2], 580, 0, 0);
        assertTrue("far fewer channels than points: " + r.channels + " vs " + d[0].length,
                r.channels < d[0].length / 4.0);
        assertEquals("channels are D_max * q_max / pi", 580 * d[0][d[0].length - 1] / Math.PI,
                r.channels, 1.0);
    }

    /** measuring the same sample more finely must not make the answer more certain */
    @Test
    public void oversamplingDoesNotManufactureConfidence() {
        LnpModel m = new LnpModel(3, false);
        double[] p = m.templateDefaults();
        p[LnpModel.SCALE] = 1e6; p[LnpModel.R0] = 220; p[LnpModel.SIGMA] = 26;
        double[][] coarse = LnpTestData.fromModel(m, p, 0.007, 0.41, 300, 9L);
        double[][] fine = LnpTestData.fromModel(m, p, 0.007, 0.41, 1200, 9L);

        double pc = LnpArrangement.analyse(coarse[0], coarse[1], coarse[2], 580, 0, 0).best().posterior;
        double pf = LnpArrangement.analyse(fine[0], fine[1], fine[2], 580, 0, 0).best().posterior;
        assertTrue("four times the points must not sharpen the posterior much: " + pc + " -> " + pf,
                Math.abs(pf - pc) < 0.25);
    }

    /** every candidate must be fitted the same way, or the difference in effort reads as evidence */
    @Test
    public void everyCandidateGetsTheSameNumberOfParameters() {
        double[][] d = emptyParticle(6L);
        LnpArrangement.Result r = LnpArrangement.analyse(d[0], d[1], d[2], 580, 3800, 2);
        int envelope = of(r, LnpArrangement.Cargo.NONE).parameters;
        for (LnpArrangement.Candidate c : r.candidates)
            assertEquals(c.cargo + " should cost its envelope plus its own terms",
                    envelope + c.cargo.extra, c.parameters);
    }

    /** the cargo length fixes the shape of the condensed and free terms, leaving only the amount free */
    @Test
    public void declaredCargoFixesTheCargoShape() {
        double[][] d = loadedParticle(7L);
        LnpArrangement.Result small = LnpArrangement.analyse(d[0], d[1], d[2], 597, 1000, 1);
        LnpArrangement.Result big = LnpArrangement.analyse(d[0], d[1], d[2], 597, 8000, 1);
        double rs = of(small, LnpArrangement.Cargo.COMPACT).extra[1];
        double rb = of(big, LnpArrangement.Cargo.COMPACT).extra[1];
        assertTrue("eight times the cargo must be a bigger condensed body: " + rs + " vs " + rb, rb > rs);
        assertEquals("and it should scale as the cube root of the volume", Math.cbrt(8.0), rb / rs, 0.15);
    }

    /** an arrangement that cannot hold the declared cargo is excluded outright, whatever it fits like */
    @Test
    public void geometryExcludesWhatCannotFit() {
        double[][] d = loadedParticle(8L);
        // far more cargo than a 22 nm particle could hold on a shell
        LnpArrangement.Result r = LnpArrangement.analyse(d[0], d[1], d[2], 597, 200000, 4);
        assertTrue("a surface cannot hold 200 kb of chain",
                of(r, LnpArrangement.Cargo.SHELL_BOUND).impossible);
        assertEquals("an excluded candidate has no posterior", 0.0,
                of(r, LnpArrangement.Cargo.SHELL_BOUND).posterior, 1e-12);
        assertFalse("and it must not be reported as the answer", r.best().impossible);
    }

    /** the verdict says what was ruled out, and admits the one thing the method cannot do */
    @Test
    public void theVerdictNamesItsOwnLimit() {
        double[][] d = loadedParticle(9L);
        String v = LnpArrangement.analyse(d[0], d[1], d[2], 597, 3800, 2).verdict();
        assertTrue("it should report exclusions: " + v, v.contains("Ruled out"));
        assertTrue("and say a repeat is not a shape: " + v,
                v.contains("indistinguishable") || v.contains("not a shape"));
    }

    /** every candidate has a picture, and it fits inside the space it is given */
    @Test
    public void everyCandidateCanBeDrawn() {
        System.setProperty("java.awt.headless", "true");
        for (LnpArrangement.Cargo c : LnpArrangement.Cargo.values()) {
            LnpCargoCartoon art = new LnpCargoCartoon(c);
            art.setEnvelope(218, 16, 3, 40);
            art.setCargo(64.5, 49.8);
            art.setVerdict(0.5, false, false);   // the winner's border is meant to touch the edge
            art.setSize(150, 172);
            art.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
            java.awt.image.BufferedImage img =
                    new java.awt.image.BufferedImage(150, 172, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            art.paint(g);
            g.dispose();
            // the unencapsulated case draws outside the particle and used to run off its own panel
            int painted = 0;
            for (int x = 0; x < 150; x++) for (int y = 0; y < 172; y++)
                if ((img.getRGB(x, y) >>> 24) != 0) painted++;
            assertTrue(c + " should draw something", painted > 400);
            for (int y = 0; y < 172; y++)
                assertEquals(c + " must not touch the left edge", 0, img.getRGB(0, y) >>> 24);
            for (int y = 0; y < 172; y++)
                assertEquals(c + " must not touch the right edge", 0, img.getRGB(149, y) >>> 24);
        }
    }

    /** thinning preserves the curve rather than discarding most of it */
    @Test
    public void thinningAveragesRatherThanDrops() {
        double[][] d = emptyParticle(10L);
        double[][] t = LnpArrangement.thinTo(d[0], d[1], d[2], 120);
        assertTrue("thinned to roughly the requested size", t[0].length <= 125 && t[0].length > 60);
        assertEquals("the q range is kept", d[0][0], t[0][0], d[0][0] * 0.2);
        assertEquals("including the far end", d[0][d[0].length - 1], t[0][t[0].length - 1],
                d[0][d[0].length - 1] * 0.2);
        double before = d[2][d[2].length / 2] / d[1][d[1].length / 2];
        double after = t[2][t[2].length / 2] / t[1][t[1].length / 2];
        assertTrue("averaging points must shrink their error, not keep it: " + before + " -> " + after,
                after < before);
    }
}
