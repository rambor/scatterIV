package scatter.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * FrameFilter: which frames of a flow-cell exposure series agree, on simulated series with a known
 * answer - a disturbed start (meniscus), a worsening end (radiation damage, aggregation, capillary
 * fouling), single bad frames, empty frames, and the statistics underneath.
 */
public class FrameFilterTest {

    interface Signal { double at(int frame, double q); }

    static double sphere(double q) {
        double x = q * 30, f = 3 * (Math.sin(x) - x * Math.cos(x)) / (x * x * x);
        return 100 * f * f + 1.0;
    }

    /** aggregate-like excess at low q, growing with amount */
    static double aggregate(double q, double amount) {
        return amount * Math.exp(-q * q * 100 * 100 / 3);
    }

    static List<Curve> series(int frames, Signal s, long seed) {
        Random r = new Random(seed);
        int n = 300;
        List<Curve> out = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            double[] q = new double[n], I = new double[n], sig = new double[n];
            for (int j = 0; j < n; j++) {
                q[j] = 0.01 + j * 0.29 / (n - 1);
                double v = s.at(f, q[j]);
                sig[j] = 0.01 * v;
                I[j] = v + sig[j] * r.nextGaussian();
            }
            out.add(new Curve("frame_" + f, q, I, sig));
        }
        return out;
    }

    static FrameFilter.Result filter(List<Curve> frames) {
        return FrameFilter.filter(frames, new FrameFilter.Options().qRange(0.01, 0.25).alpha(0.01));
    }

    static int[] range(int from, int to) {
        int[] r = new int[to - from + 1];
        for (int i = 0; i < r.length; i++) r[i] = from + i;
        return r;
    }

    // ---------------------------------------------------------------- clean data

    @Test
    public void cleanSeriesKeepsEveryFrame() {
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q), 1));
        assertArrayEquals(range(0, 29), r.getAccepted());
        assertEquals("reference starts at the first frame", 0, r.getAnchorStart());
        assertNull(r.getMessage());
    }

    @Test
    public void cleanSeriesKeepsEveryFrameForManySeeds() {
        // false alarms must be rare at alpha = 0.01 after the Holm correction
        int rejected = 0;
        for (long seed = 10; seed < 30; seed++) rejected += 30 - filter(series(30, (f, q) -> sphere(q), seed)).getAccepted().length;
        assertTrue("frames rejected in 20 clean series: " + rejected, rejected <= 3);
    }

    @Test
    public void medianReferenceAlsoKeepsACleanSeries() {
        FrameFilter.Options o = new FrameFilter.Options().qRange(0.01, 0.25);
        o.reference = FrameFilter.Reference.MEDIAN;
        FrameFilter.Result r = FrameFilter.filter(series(20, (f, q) -> sphere(q), 2), o);
        assertEquals(20, r.getAccepted().length);
    }

    // ---------------------------------------------------------------- the start of the series

    @Test
    public void meniscusFramesAtTheStartAreCutAsLeading() {
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) * (f == 0 ? 0.85 : f == 1 ? 0.95 : 1), 3));
        assertEquals(FrameFilter.Reason.LEADING, r.getFrames().get(0).reason);
        assertEquals(FrameFilter.Reason.LEADING, r.getFrames().get(1).reason);
        assertArrayEquals(range(2, 29), r.getAccepted());
        assertEquals("reference after the meniscus", 2, r.getAnchorStart());
    }

    @Test
    public void meniscusWithExtraLowQScatteringIsCut() {
        // bubbles / mixing at the meniscus: ~7 % extra at the lowest q, gone by q = 0.03
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) + (f < 2 ? aggregate(q, 10) : 0), 4));
        assertEquals(FrameFilter.Reason.LEADING, r.getFrames().get(0).reason);
        assertEquals(FrameFilter.Reason.LEADING, r.getFrames().get(1).reason);
        assertEquals(2, r.getAccepted()[0]);
        assertTrue("the rest kept: " + r.getAccepted().length, r.getAccepted().length >= 27);
    }

    // ---------------------------------------------------------------- the end of the series

    @Test
    public void radiationDamageAtTheEndIsCutAsTrailing() {
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) + (f >= 22 ? aggregate(q, 1.5 * (f - 21)) : 0), 5));
        int[] acc = r.getAccepted();
        assertEquals("starts at the first frame", 0, acc[0]);
        int last = acc[acc.length - 1];
        assertTrue("damaged end removed (last kept " + last + ")", last >= 21 && last < 27);
        for (int f = last + 1; f < 30; f++) assertEquals(FrameFilter.Reason.TRAILING, r.getFrames().get(f).reason);
    }

    @Test
    public void fastDamageThatDominatesTheSeriesStillKeepsTheEarlyFrames() {
        // damage from frame 8 on, growing: most frames are damaged, so a median reference would be the
        // damaged state and keep the wrong frames
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) + (f >= 8 ? aggregate(q, 3 + 0.5 * (f - 8)) : 0), 6));
        int[] acc = r.getAccepted();
        assertEquals(0, r.getAnchorStart());
        assertEquals(0, acc[0]);
        int last = acc[acc.length - 1];
        assertTrue("cut soon after the onset (last kept " + last + ")", last >= 7 && last <= 11);

        FrameFilter.Options o = new FrameFilter.Options().qRange(0.01, 0.25);
        o.reference = FrameFilter.Reference.MEDIAN;
        FrameFilter.Result median = FrameFilter.filter(series(30, (f, q) -> sphere(q) + (f >= 8 ? aggregate(q, 3 + 0.5 * (f - 8)) : 0), 6), o);
        assertNotEquals("the median reference keeps different frames", Arrays.toString(acc), Arrays.toString(median.getAccepted()));
    }

    @Test
    public void slowCapillaryFoulingIsCutOnceItHasBuiltUp() {
        // deposit on the window: a growing, smooth extra background in every later frame
        // there is no clean plateau when the drift starts at once: what matters is that the drifting
        // frames are not all merged and the user is told
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) + 0.004 * f * (1 + 0.3 / (q * 30)), 7));
        int[] acc = r.getAccepted();
        assertTrue("drift cut (kept " + acc.length + ")", acc.length < 15);
        assertTrue("a stretch of neighbours kept (kept " + acc.length + ")", acc.length >= 4);
        assertEquals("contiguous", acc.length - 1, acc[acc.length - 1] - acc[0]);
        assertNotNull("the user is warned", r.getMessage());
    }

    // ---------------------------------------------------------------- single bad frames

    @Test
    public void aSingleBadFrameInsideTheRunIsAnOutlierAndTheRunContinues() {
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) * (f == 12 ? 1.1 : 1), 8));
        assertEquals(FrameFilter.Reason.OUTLIER, r.getFrames().get(12).reason);
        assertEquals(29, r.getAccepted().length);
        assertEquals(29, r.getAccepted()[28]);
    }

    @Test
    public void twoBadFramesInARowEndTheRun() {
        // with maxGap = 1 a gap of two ends the accepted stretch
        FrameFilter.Result r = filter(series(30, (f, q) -> sphere(q) * (f == 20 || f == 21 ? 1.1 : 1), 9));
        int[] acc = r.getAccepted();
        assertEquals(19, acc[acc.length - 1]);
    }

    @Test
    public void largerGapAllowanceBridgesTwoBadFrames() {
        FrameFilter.Options o = new FrameFilter.Options().qRange(0.01, 0.25).maxGap(2);
        FrameFilter.Result r = FrameFilter.filter(series(30, (f, q) -> sphere(q) * (f == 20 || f == 21 ? 1.1 : 1), 9), o);
        assertEquals(28, r.getAccepted().length);
        assertEquals(FrameFilter.Reason.OUTLIER, r.getFrames().get(20).reason);
    }

    // ---------------------------------------------------------------- awkward inputs

    @Test
    public void aNearlyEmptyFrameIsSetAsideAndTheOthersAreStillCompared() {
        List<Curve> frames = series(12, (f, q) -> sphere(q) * (f == 11 ? 1.2 : 1), 10);
        Curve c = frames.get(0);
        frames.set(0, new Curve("dark", new double[]{c.q(50), c.q(150)}, new double[]{1e-4, 1e-4}, new double[]{1e-4, 1e-4}));
        FrameFilter.Result r = filter(frames);
        assertEquals(FrameFilter.Reason.INCOMPLETE, r.getFrames().get(0).reason);
        assertNotEquals("the comparison still ran", FrameFilter.Reason.ACCEPTED, r.getFrames().get(11).reason);
        assertArrayEquals(range(1, 10), r.getAccepted());
        assertEquals(1, r.count(FrameFilter.Reason.INCOMPLETE));
    }

    @Test
    public void aQRangeWithoutDataKeepsEveryFrameAndSaysWhy() {
        List<Curve> frames = series(10, (f, q) -> sphere(q) * (f == 0 ? 0.5 : 1), 11);
        FrameFilter.Result r = FrameFilter.filter(frames, new FrameFilter.Options().qRange(0.7, 3.4));
        assertEquals(10, r.getAccepted().length);
        assertNotNull(r.getMessage());
        assertTrue(r.getMessage(), r.getMessage().contains("too few common q points"));
    }

    @Test
    public void tooFewFramesAreAllKept() {
        FrameFilter.Result r = filter(series(2, (f, q) -> sphere(q) * (f == 0 ? 0.5 : 1), 12));
        assertEquals(2, r.getAccepted().length);
        assertTrue(r.getMessage(), r.getMessage().contains("too few frames"));
    }

    @Test
    public void differencesOutsideTheQRangeAreIgnored() {
        // frame 5 differs only above q = 0.2
        List<Curve> frames = series(15, (f, q) -> sphere(q) + (f == 5 && q > 0.2 ? 0.3 : 0), 13);
        FrameFilter.Result low = FrameFilter.filter(frames, new FrameFilter.Options().qRange(0.01, 0.18));
        FrameFilter.Result all = FrameFilter.filter(frames, new FrameFilter.Options().qRange(0.01, 0.3));
        assertEquals(15, low.getAccepted().length);
        assertNotEquals(FrameFilter.Reason.ACCEPTED, all.getFrames().get(5).reason);
    }

    @Test
    public void framesOnSlightlyDifferentGridsAreMatchedWithinTheTolerance() {
        List<Curve> frames = series(10, (f, q) -> sphere(q), 14);
        Curve c = frames.get(3);
        double[] q = new double[c.size()], I = new double[c.size()], s = new double[c.size()];
        // q calibration differing in the 5th digit (the tolerance is relative, 1e-4)
        for (int j = 0; j < c.size(); j++) { q[j] = c.q(j) * (1 + 3e-5); I[j] = c.i(j); s[j] = c.sigma(j); }
        frames.set(3, new Curve("shifted", q, I, s));
        FrameFilter.Result r = filter(frames);
        assertEquals(10, r.getAccepted().length);
        assertTrue("common grid kept the points: " + r.getPoints(), r.getPoints() > 200);
    }

    @Test
    public void framesKeepTheirIndexNameAndStatistics() {
        FrameFilter.Result r = filter(series(8, (f, q) -> sphere(q), 15));
        for (int f = 0; f < 8; f++) {
            FrameFilter.Frame fr = r.getFrames().get(f);
            assertEquals(f, fr.index);
            assertEquals("frame_" + f, fr.name);
            assertTrue(fr.points > 200);
            assertTrue(fr.longestRun > 0 && fr.longestRun <= fr.points);
            assertTrue(fr.adjustedP >= fr.pValue - 1e-15);
        }
    }

    // ---------------------------------------------------------------- statistics

    @Test
    public void runProbabilityHasTheRightLimits() {
        assertEquals(1.0, FrameFilter.probabilityOfRunAtLeast(100, 0), 1e-12);
        assertEquals(1.0, FrameFilter.probabilityOfRunAtLeast(100, 1), 1e-12);
        assertEquals(0.0, FrameFilter.probabilityOfRunAtLeast(10, 11), 1e-12);
        // two fair coin tosses: P(run >= 2) = P(HH or TT) = 1/2
        assertEquals(0.5, FrameFilter.probabilityOfRunAtLeast(2, 2), 1e-12);
        double previous = 1;
        for (int c = 1; c <= 30; c++) {
            double p = FrameFilter.probabilityOfRunAtLeast(300, c);
            assertTrue("decreasing in c", p <= previous + 1e-15);
            previous = p;
        }
    }

    @Test
    public void runProbabilityMatchesSimulation() {
        Random r = new Random(16);
        int n = 50, c = 7, hits = 0, trials = 40000;
        for (int t = 0; t < trials; t++) {
            boolean[] s = new boolean[n];
            for (int i = 0; i < n; i++) s[i] = r.nextBoolean();
            if (FrameFilter.longestRun(s, n) >= c) hits++;
        }
        assertEquals((double) hits / trials, FrameFilter.probabilityOfRunAtLeast(n, c), 0.01);
    }

    @Test
    public void longestRunCountsSameSignStretches() {
        boolean[] s = {true, true, false, false, false, true, false};
        assertEquals(3, FrameFilter.longestRun(s, s.length));
        assertEquals(2, FrameFilter.longestRun(s, 2));
    }

    @Test
    public void holmAdjustmentIsMonotoneAndNeverBelowTheRawValue() {
        double[] p = {0.04, 0.001, 0.5, 0.01, 0.03};
        double[] adj = FrameFilter.holm(p);
        for (int i = 0; i < p.length; i++) {
            assertTrue(adj[i] >= p[i]);
            assertTrue(adj[i] <= 1.0);
        }
        // smallest p times m
        assertEquals(0.005, adj[1], 1e-12);
        // order of the raw values is kept
        Integer[] order = {0, 1, 2, 3, 4};
        Arrays.sort(order, (a, b) -> Double.compare(p[a], p[b]));
        for (int k = 1; k < order.length; k++) assertTrue(adj[order[k]] >= adj[order[k - 1]]);
    }

    @Test
    public void selectionKeepsTheLongestStretchWithSmallGaps() {
        boolean T = true, F = false;
        FrameFilter.Reason[] r = FrameFilter.select(new boolean[]{F, T, T, F, T, T, F, F, T}, 1);
        assertEquals(FrameFilter.Reason.LEADING, r[0]);
        assertEquals(FrameFilter.Reason.ACCEPTED, r[1]);
        assertEquals(FrameFilter.Reason.OUTLIER, r[3]);
        assertEquals(FrameFilter.Reason.ACCEPTED, r[5]);
        assertEquals(FrameFilter.Reason.TRAILING, r[8]);
    }
}
