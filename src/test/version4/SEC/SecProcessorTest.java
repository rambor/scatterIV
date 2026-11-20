package version4.SEC;

import org.jfree.data.xy.XYSeries;
import org.junit.Test;
import version4.Collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.Assert.*;
import static version4.SEC.SecRunFixture.*;

/**
 * SecProcessor (the engine behind SECTool) on simulated runs with a known answer: run checks, common
 * q-values, buffer selection and averaging, subtraction, signal, Guinier analysis and frames not in use.
 */
public class SecProcessorTest {

    private static final double THRESHOLD = 1.01;

    // ---------------------------------------------------------------- run length

    @Test
    public void runsShorterThanTheMinimumAreRejected() {
        Collection shortRun = run(SecProcessor.MIN_FRAMES - 1, f -> 0, 1);
        try {
            SecProcessor.checkRunLength(shortRun);
            fail("a run of " + (SecProcessor.MIN_FRAMES - 1) + " frames should be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("too short"));
        }
        SecProcessor.checkRunLength(run(SecProcessor.MIN_FRAMES, f -> 0, 1));
    }

    @Test
    public void framesNotInUseDoNotCountTowardsTheRunLength() {
        Collection c = run(SecProcessor.MIN_FRAMES, f -> 0, 1);
        c.getDataset(10).setInUse(false);
        assertEquals(SecProcessor.MIN_FRAMES - 1, SecProcessor.framesInUse(c));
        try {
            SecProcessor.checkRunLength(c);
            fail("one frame short once a frame is unticked");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // ---------------------------------------------------------------- common q-values

    private static void dropPoints(Collection c, int frame, int from, int count) {
        XYSeries d = c.getDataset(frame).getAllData(), e = c.getDataset(frame).getAllDataError();
        d.delete(from, from + count - 1);
        e.delete(from, from + count - 1);
    }

    @Test
    public void qValuesMissingFromAFrameAreDroppedAndReported() {
        Collection c = run();
        dropPoints(c, 50, 100, 5);
        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(c);
        assertEquals(TOTAL_Q - 5, common.qvalues.size());
        assertTrue(common.isAcceptable());
        assertEquals(Integer.valueOf(5), common.framesMissingQ.get(c.getDataset(50).getFileName()));
        assertFalse("the missing q is not used", common.qvalues.contains(q(102)));
    }

    @Test
    public void framesNotInUseDoNotLimitTheCommonQValues() {
        Collection c = run();
        dropPoints(c, 50, 100, 5);
        c.getDataset(50).setInUse(false);
        assertEquals(TOTAL_Q, SecProcessor.findCommonQValues(c).qvalues.size());
    }

    @Test
    public void tooFewCommonQValuesIsNotAcceptableAndNamesTheFrame() {
        Collection c = run();
        dropPoints(c, 70, 20, 70);
        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(c);
        assertTrue(common.fractionInCommon() < SecProcessor.MIN_COMMON_Q_FRACTION);
        assertFalse(common.isAcceptable());
        assertTrue(common.describeProblem(), common.describeProblem().contains(c.getDataset(70).getFileName()));
    }

    // ---------------------------------------------------------------- buffer selection

    @Test
    public void estimatedBufferAvoidsThePeakAndTheEndOfTheRun() {
        SecProcessor p = processor(run(), THRESHOLD);
        p.estimateBufferFrames();
        SortedSet<Integer> buffers = p.getBufferFrames();
        assertTrue("at least " + SecProcessor.MIN_BUFFER_FRAMES + " buffer frames: " + buffers,
                buffers.size() >= SecProcessor.MIN_BUFFER_FRAMES);
        for (int f : buffers) {
            assertTrue("frame " + f + " carries sample (c = " + concentration(f) + ")", concentration(f) < 0.01 * PEAK_HEIGHT);
            assertTrue("frame " + f + " is in the end of the run", f <= SecProcessor.lastBufferCandidate(FRAMES));
        }
    }

    @Test
    public void estimatedBufferNeverUsesFramesNotInUse() {
        Collection c = run();
        for (int f = 0; f < FRAMES; f += 3) c.getDataset(f).setInUse(false);
        SecProcessor p = processor(c, THRESHOLD);
        p.estimateBufferFrames();
        for (int f : p.getBufferFrames()) assertTrue("frame " + f + " is not in use", c.getDataset(f).getInUse());
    }

    @Test
    public void lastBufferCandidateLeavesOutTheLastWindowAndOneFrame() {
        assertEquals(200 - SecProcessor.WINDOW - 2, SecProcessor.lastBufferCandidate(200));
    }

    @Test
    public void averagedBufferMatchesTheTrueBufferWithinItsErrors() {
        SecProcessor p = processor(run(), THRESHOLD);
        p.estimateBufferFrames();
        int n = p.getBufferFrames().size();
        XYSeries b = p.getAveragedBuffer(), e = p.getAveragedBufferError();
        assertEquals(TOTAL_Q, b.getItemCount());
        double chi = 0;
        for (int k = 0; k < b.getItemCount(); k++) {
            double q = b.getX(k).doubleValue();
            assertEquals("error of the mean at q " + q, sigma(q) / Math.sqrt(n), e.getY(k).doubleValue(), 1e-9);
            double z = (b.getY(k).doubleValue() - buffer(q)) / e.getY(k).doubleValue();
            chi += z * z;
        }
        assertEquals("chi2 per point", 1.0, chi / TOTAL_Q, 0.35);
    }

    @Test
    public void chosenBufferFramesAreUsedExactly() {
        Collection c = run();
        SecProcessor p = processor(c, THRESHOLD);
        SortedSet<Integer> chosen = new TreeSet<>(Arrays.asList(5, 6, 7, 20, 21, 60));
        p.useBufferFrames(chosen);
        assertEquals(chosen, p.getBufferFrames());
        // the average is the plain mean (equal errors) of exactly those frames
        XYSeries b = p.getAveragedBuffer();
        for (int k = 0; k < TOTAL_Q; k += 13) {
            double mean = 0;
            for (int f : chosen) mean += c.getDataset(f).getAllData().getY(k).doubleValue();
            mean /= chosen.size();
            assertEquals(mean, b.getY(k).doubleValue(), 1e-9);
        }
        SecData d = p.process();
        for (int f = 0; f < FRAMES; f++) assertEquals("buffer flag of frame " + f, chosen.contains(f), d.isBuffer(f));
    }

    // ---------------------------------------------------------------- averaging

    @Test
    public void averageIsWeightedByInverseVariance() {
        XYSeries a = new XYSeries("a"), ae = new XYSeries("ae"), b = new XYSeries("b"), be = new XYSeries("be");
        List<Number> q = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            q.add(0.01 * (k + 1));
            a.add(0.01 * (k + 1), 10.0);
            ae.add(0.01 * (k + 1), 1.0);
            b.add(0.01 * (k + 1), 20.0);
            be.add(0.01 * (k + 1), 2.0);
        }
        XYSeries out = new XYSeries("o"), outE = new XYSeries("oe");
        SecProcessor.averageWithoutScaling(q, Arrays.asList(a, b), Arrays.asList(ae, be), out, outE);
        // weights 1 and 1/4: (10 + 20/4) / 1.25 = 12
        assertEquals(12.0, out.getY(0).doubleValue(), 1e-12);
        assertEquals(1 / Math.sqrt(1.25), outE.getY(0).doubleValue(), 1e-12);
    }

    @Test
    public void averageUsesOnlyTheCurvesThatHaveTheQValue() {
        XYSeries a = new XYSeries("a"), ae = new XYSeries("ae"), b = new XYSeries("b"), be = new XYSeries("be");
        List<Number> q = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            double qq = 0.01 * (k + 1);
            q.add(qq);
            a.add(qq, 10.0);
            ae.add(qq, 1.0);
            if (k != 2) { b.add(qq, 20.0); be.add(qq, 1.0); }
        }
        XYSeries out = new XYSeries("o"), outE = new XYSeries("oe");
        SecProcessor.averageWithoutScaling(q, Arrays.asList(a, b), Arrays.asList(ae, be), out, outE);
        assertEquals(15.0, out.getY(0).doubleValue(), 1e-12);
        assertEquals("only the first curve has q = 0.03", 10.0, out.getY(2).doubleValue(), 1e-12);
        assertEquals(1.0, outE.getY(2).doubleValue(), 1e-12);
    }

    // ---------------------------------------------------------------- signal, subtraction, Guinier

    private static SecData processed() {
        SecProcessor p = processor(run(), THRESHOLD);
        p.estimateBufferFrames();
        return p.process();
    }

    @Test
    public void signalFollowsTheElutionProfile() {
        SecData d = processed();
        int peak = 0;
        for (int f = 0; f < FRAMES; f++) {
            assertEquals("signal of frame " + f, expectedSignal(concentration(f)), d.getSignal(f), 0.002);
            if (d.getSignal(f) > d.getSignal(peak)) peak = f;
        }
        assertEquals("signal peaks at the elution peak", PEAK_FRAME, peak, 2);
    }

    @Test
    public void subtractedPeakFrameIsTheParticleScattering() {
        SecData d = processed();
        int f = (int) PEAK_FRAME;
        double[] sub = d.subtracted.get(f), err = d.subtractedErrors.get(f), q = d.getQvalues();
        double chi = 0;
        for (int k = 0; k < q.length; k++) {
            double z = (sub[k] - concentration(f) * particle(q[k])) / err[k];
            chi += z * z;
        }
        assertEquals("chi2 per point", 1.0, chi / q.length, 0.35);
    }

    @Test
    public void rgAndIzeroAreRecoveredAcrossThePeak() {
        SecData d = processed();
        List<Double> rgs = new ArrayList<>();
        for (int f = 0; f < FRAMES; f++) {
            if (concentration(f) < 0.25 * PEAK_HEIGHT) continue;
            assertEquals("Rg of frame " + f, RG, d.getRg(f), 2.0);
            assertEquals("I(0) of frame " + f, concentration(f), d.getIzero(f), 0.04 * concentration(f));
            rgs.add(d.getRg(f));
        }
        rgs.sort(Double::compare);
        assertEquals("median Rg", RG, rgs.get(rgs.size() / 2), 0.7);
    }

    @Test
    public void framesBelowTheThresholdAndBufferFramesHaveNoGuinierValues() {
        SecData d = processed();
        for (int f = 0; f < FRAMES; f++) {
            if (d.getSignal(f) <= THRESHOLD || d.isBuffer(f)) {
                assertEquals("Rg of frame " + f, 0, d.getRg(f), 0);
                assertEquals("I(0) of frame " + f, 0, d.getIzero(f), 0);
            }
        }
    }

    @Test
    public void integratedQIqFollowsTheConcentration() {
        SecData d = processed();
        // integral of q * c * P(q) over the grid
        double unit = 0;
        double[] q = d.getQvalues();
        for (int k = 1; k < q.length; k++)
            unit += 0.5 * (q[k] * particle(q[k]) + q[k - 1] * particle(q[k - 1])) * (q[k] - q[k - 1]);
        for (int f = 100; f <= 160; f += 5)
            assertEquals("qIq of frame " + f, concentration(f) * unit, d.integratedQIq[f], 0.02 * PEAK_HEIGHT * unit);
    }

    @Test
    public void framesNotInUseKeepTheirDataButGetNoResults() {
        Collection c = run();
        int f = (int) PEAK_FRAME;
        c.getDataset(f).setInUse(false);
        SecProcessor p = processor(c, THRESHOLD);
        p.estimateBufferFrames();
        SecData d = p.process();
        assertEquals("neutral signal", 1.0, d.getSignal(f), 0);
        assertEquals(0, d.getRg(f), 0);
        assertFalse(d.isBuffer(f));
        assertNull("no subtracted data written", d.subtracted.get(f));
        assertNotNull("unsubtracted data kept", d.unsubtracted.get(f));
        assertEquals("its neighbours are analysed", RG, d.getRg(f + 1), 2.0);
    }

    @Test
    public void averagedBufferIsStoredWithTheResults() {
        SecProcessor p = processor(run(), THRESHOLD);
        p.estimateBufferFrames();
        SecData d = p.process();
        for (int k = 0; k < TOTAL_Q; k++) {
            assertEquals(p.getAveragedBuffer().getY(k).doubleValue(), d.averagedBuffer[k], 0);
            assertEquals(p.getAveragedBufferError().getY(k).doubleValue(), d.averagedBufferErrors[k], 0);
        }
        assertEquals(THRESHOLD, d.getThreshold(), 0);
    }

    @Test(expected = IllegalStateException.class)
    public void processingNeedsTheBufferFirst() {
        processor(run(), THRESHOLD).process();
    }

    @Test
    public void guinierOnlyResultsAreFiledByFrameNumber() {
        // the Guinier-only update runs AutoRg on subtracted frames whose id is the frame number
        Collection subtracted = new Collection("subtracted");
        for (int f : new int[]{120, 130, 140}) {
            XYSeries d = new XYSeries("s" + f), e = new XYSeries("e" + f);
            for (int k = 0; k < TOTAL_Q; k++) {
                d.add(q(k), concentration(f) * particle(q(k)));
                e.add(q(k), 1e-4);
            }
            subtracted.createDataset(d, e, f);
        }
        SecProcessor.GuinierResults r = SecProcessor.autoRg(subtracted, FRAMES, 0, null);
        assertEquals(FRAMES, r.rg.length);
        for (int f : new int[]{120, 130, 140}) {
            assertEquals(RG, r.rg[f], 1.0);
            assertEquals(concentration(f), r.izero[f], 0.02 * concentration(f));
        }
        assertEquals("not computed", 0, r.rg[0], 0);
    }

    // ---------------------------------------------------------------- statistics

    @Test
    public void durbinWatsonDistinguishesNoiseFromSmoothResiduals() {
        java.util.Random r = new java.util.Random(3);
        double[] noise = new double[2000], smooth = new double[2000], alternating = new double[2000];
        for (int i = 0; i < 2000; i++) {
            noise[i] = r.nextGaussian();
            smooth[i] = Math.sin(i / 100.0);
            alternating[i] = (i % 2 == 0) ? 1 : -1;
        }
        assertEquals("white noise", 2.0, SecProcessor.durbinWatson(noise, 2000), 0.15);
        assertEquals("smooth", 0.0, SecProcessor.durbinWatson(smooth, 2000), 0.01);
        assertEquals("alternating", 4.0, SecProcessor.durbinWatson(alternating, 2000), 0.01);
    }

    @Test
    public void durbinWatsonSpreadIsSmallForIdenticalFramesAndNaNForOne() {
        Collection c = run(5, f -> 0, 1);
        ArrayList<XYSeries> same = new ArrayList<>();
        for (int i = 0; i < 4; i++) same.add(c.getDataset(0).getAllData());
        same.set(0, c.getDataset(1).getAllData());
        // three identical frames compared with a fourth: identical residuals, zero spread
        assertEquals(0, SecProcessor.durbinWatsonSpread(same, 0, TOTAL_Q - 1), 1e-12);
        ArrayList<XYSeries> one = new ArrayList<>();
        one.add(c.getDataset(0).getAllData());
        assertTrue(Double.isNaN(SecProcessor.durbinWatsonSpread(one, 0, TOTAL_Q - 1)));
    }
}
