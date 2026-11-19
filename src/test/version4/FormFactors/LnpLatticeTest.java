package version4.FormFactors;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Bragg indexing and the mesophase geometry derived from it, checked against published values.
 */
public class LnpLatticeTest {

    private static final LnpLattice.Shape SHAPE = LnpLattice.Shape.values()[0];

    /**
     * Jin et al. (2025) report a monoolein cubosome with an Im3m phase of a = 174.0 Angstrom, a water-channel
     * radius of 35.1 Angstrom for a monolayer length of 18.1 Angstrom, and a domain of about 550 Angstrom.
     */
    @Test
    public void identifiesAndFitsAnIm3mCubosome() {
        LnpLattice.Lattice im3m = LnpLattice.byName("Im3m");
        assertNotNull("Im3m should be in the lattice table", im3m);
        double[][] d = LnpTestData.bragg(im3m, 174.0, 550, 6, 0.02, 0.30, 900, 7);

        List<LnpLattice.Result> ranked = LnpLattice.identify(d[0], d[1], d[2], 0.02, 0.30, SHAPE, Double.NaN, 0, 4);
        assertFalse("identify() should return candidates", ranked.isEmpty());
        assertTrue("the best candidate should be Im3m but was " + ranked.get(0).lattice.symmetry,
                ranked.get(0).lattice.symmetry.startsWith("Im3m"));

        LnpLattice.Result r = LnpLattice.fit(d[0], d[1], d[2], im3m, ranked.get(0).a, 0.02, 0.30, SHAPE, Double.NaN, 0, Double.NaN);
        assertEquals("lattice parameter", 174.0, r.a, 2.0);
        assertEquals("water-channel radius at a monolayer length of 18.1 Å", 35.1, r.channelRadius(18.1), 3.0);
        assertEquals("domain size from the peak width", 550, r.domain, 120);
        assertTrue("a fit to its own generating model should be close to reduced chi2 of 1, was " + r.chi2Reduced,
                r.chi2Reduced < 5);
    }

    /**
     * The three bicontinuous cubic phases that share one minimal surface stand in fixed ratios,
     * Pn3m : Im3m : Ia3d = 1 : 1.279 : 1.576 (Marsh, Handbook of Lipid Bilayers, ch. 14). Scatter uses these to
     * guess the second lattice parameter when it tests for coexistence, so they have to be right.
     */
    @Test
    public void bonnetRatiosMatchTheHandbook() {
        double pn3m = LnpLattice.bonnetRatio(LnpLattice.byName("Pn3m"));
        assertEquals("Im3m / Pn3m", 1.279, LnpLattice.bonnetRatio(LnpLattice.byName("Im3m")) / pn3m, 0.005);
        assertEquals("Ia3d / Pn3m", 1.576, LnpLattice.bonnetRatio(LnpLattice.byName("Ia3d")) / pn3m, 0.005);
    }

    /** Every lattice in the table must have one Miller index per allowed reflection, in increasing order. */
    @Test
    public void everyLatticeIsWellFormed() {
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) {
            assertEquals(l.symmetry + ": one hkl label per reflection", l.hkl.length, l.m.length);
            assertTrue(l.symmetry + ": needs at least two reflections to index with", l.m.length >= 2);
            for (int i = 1; i < l.m.length; i++)
                assertTrue(l.symmetry + ": reflections must increase, " + l.hkl[i - 1] + " then " + l.hkl[i],
                        l.m[i] > l.m[i - 1]);
            assertTrue(l.symmetry + ": exactly one kind of lattice",
                    (l.cubic ? 1 : 0) + (l.hexagonal ? 1 : 0) + (l.lamellar ? 1 : 0) == 1);
        }
    }

    /**
     * The sponge (L3) phase of Fig. 1G of Caselli et al. (2024) has no lattice, so it is fitted with the
     * Teubner-Strey form instead. Both length scales must come back from a curve generated at known values.
     */
    @Test
    public void spongePhaseRecoversRepeatAndCorrelationLength() {
        double[][] d = LnpTestData.sponge(150, 90, 0.0002, 0.008, 0.35, 400, 3);
        LnpLattice.Sponge sp = LnpLattice.fitSponge(d[0], d[1], d[2], 0.008, 0.35);
        assertEquals("quasi-periodic repeat d", 150, sp.d, 5);
        assertEquals("correlation length xi", 90, sp.xi, 6);
        assertTrue("a structured sponge has a negative amphiphilicity factor, was " + sp.amphiphilicity,
                sp.amphiphilicity < 0);
        assertEquals("reduced chi2 against its own generating model", 1.0, sp.chi2Reduced, 0.5);
    }

    /**
     * Water-channel geometry, the output a formulator reads off a cubosome fit. Mendozza et al. (Nanoscale 10,
     * 12,2018) give the relations used here, for glyceryl monooleate with a monolayer length of 17 Angstrom over
     * 25-50 C: for a cubic phase r_w = sqrt(-A0 / 2 pi chi) a - l (eq. S2) and phi_w = 1 - 2 A0 (l/a)
     * - (4/3) pi chi (l/a)^3 (eq. S3). Fully hydrated monoolein Pn3m sits near a = 100 Angstrom, which should
     * give a channel radius of about 22 Angstrom and just under 40 % water.
     */
    @Test
    public void cubicWaterChannelMatchesTheMonooleinValues() {
        LnpLattice.Result r = new LnpLattice.Result();
        r.lattice = LnpLattice.byName("Pn3m");
        r.a = 100;
        assertEquals("channel radius of fully hydrated monoolein Pn3m", 22.1, r.channelRadius(17), 0.5);
        assertEquals("water volume fraction", 0.39, r.waterFraction(17), 0.02);

        // the three cubic phases at one lattice parameter order by their minimal surface
        LnpLattice.Result im3m = new LnpLattice.Result();
        im3m.lattice = LnpLattice.byName("Im3m");
        im3m.a = 100;
        LnpLattice.Result ia3d = new LnpLattice.Result();
        ia3d.lattice = LnpLattice.byName("Ia3d");
        ia3d.a = 100;
        assertTrue("Pn3m holds the widest channel at a given a",
                r.channelRadius(17) > im3m.channelRadius(17) && im3m.channelRadius(17) > ia3d.channelRadius(17));
    }

    /**
     * The inverse hexagonal phase does not have a channel radius of a/2 - l: the cylinders sit on a hexagonal
     * lattice, and Reese et al. (2015), used as eq. S4 of Mendozza et al., give r_w = (0.5256 a - l) / 0.994.
     * The difference is about 17 % at a = 50 Angstrom, which matters for a loading capacity.
     */
    @Test
    public void hexagonalWaterChannelUsesTheHexagonalGeometry() {
        LnpLattice.Lattice hex = null;
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) if (l.hexagonal) { hex = l; break; }
        assertNotNull(hex);
        LnpLattice.Result r = new LnpLattice.Result();
        r.lattice = hex;
        r.a = 50;
        assertEquals("Reese et al. geometry", (0.5256 * 50 - 17) / 0.994, r.channelRadius(17), 1e-6);
        assertNotEquals("and not the naive half-spacing", 50 / 2.0 - 17, r.channelRadius(17), 0.5);
        double rw = r.channelRadius(17);
        assertEquals("water fraction of one cylinder per hexagonal cell",
                2 * Math.PI * rw * rw / (Math.sqrt(3) * 50 * 50), r.waterFraction(17), 1e-9);
    }

    /** Geometry that cannot be satisfied must report nothing rather than a negative or absurd channel. */
    @Test
    public void impossibleGeometryGivesNoWaterFraction() {
        LnpLattice.Result r = new LnpLattice.Result();
        r.lattice = LnpLattice.byName("Pn3m");
        r.a = 30;                                     // far too small for a 17 Å monolayer
        assertTrue("a water fraction here would be meaningless", Double.isNaN(r.waterFraction(17)));

        LnpLattice.Result fd3m = new LnpLattice.Result();
        fd3m.lattice = LnpLattice.byName("Fd3m");
        fd3m.a = 150;
        assertTrue("Fd3m micelles are discrete, so there is no channel", Double.isNaN(fd3m.channelRadius(17)));
        assertTrue("and no channel fraction", Double.isNaN(fd3m.waterFraction(17)));
    }

    /**
     * Two phases of the same symmetry, which is what a pair of coexisting lamellar phases is. The second lattice
     * has to start from its own parameter: begun at the first's it refits the same reflections, so the second
     * phase has to be carried over from the indexing rather than guessed from the first.
     */
    @Test
    public void twoLamellarPhasesAreResolvedWhenEachStartsFromItsOwnParameter() {
        LnpLattice.Lattice lam = null;
        for (LnpLattice.Lattice l : LnpLattice.LATTICES) if (l.lamellar) { lam = l; break; }
        assertNotNull(lam);

        // d = 55.8 Å and 46.8 Å, the two repeats measured in a real lipid dispersion
        int n = 1400;
        double[] q = new double[n], I = new double[n], s = new double[n];
        java.util.Random rnd = new java.util.Random(21);
        double[][] phases = {{55.8, 1.0}, {46.8, 0.8}};
        for (int i = 0; i < n; i++) {
            q[i] = 0.06 + (0.42 - 0.06) * i / (n - 1.0);
            double v = 0.6 * Math.pow(q[i] / 0.06, -1.0) + 0.02;
            for (double[] ph : phases)
                for (int order = 1; order <= 3; order++) {
                    double qc = 2 * Math.PI * order / ph[0];
                    v += ph[1] * 0.6 / (order * order) * Math.exp(-0.5 * Math.pow((q[i] - qc) / 0.0035, 2));
                }
            s[i] = 0.02 * v;
            I[i] = v + s[i] * rnd.nextGaussian();
        }

        LnpLattice.Shape shape = LnpLattice.Shape.values()[0];
        LnpLattice.Result pair = LnpLattice.fitPair(q, I, s, lam, 55.8, lam, 46.8, 0.06, 0.42, shape, Double.NaN, 0, Double.NaN);
        assertNotNull("a second phase should be fitted", pair.second);
        double a1 = pair.a, a2 = pair.second.a;
        assertEquals("first repeat", 55.8, Math.max(a1, a2), 1.0);
        assertEquals("second repeat", 46.8, Math.min(a1, a2), 1.0);
        assertTrue("both phases should carry real weight",
                pair.phaseShare > 0.1 && pair.second.phaseShare > 0.1);

        LnpLattice.Result single = LnpLattice.fit(q, I, s, lam, 55.8, 0.06, 0.42, shape, Double.NaN, 0, Double.NaN);
        assertTrue("two phases should beat one on this pattern", pair.chi2 < single.chi2);
    }

    /** A window with too few points cannot be fitted, and must say so rather than returning nonsense. */
    @Test(expected = IllegalArgumentException.class)
    public void spongeRefusesATinyWindow() {
        double[][] d = LnpTestData.sponge(150, 90, 0.0002, 0.008, 0.35, 400, 3);
        LnpLattice.fitSponge(d[0], d[1], d[2], 0.30, 0.32);
    }
}
