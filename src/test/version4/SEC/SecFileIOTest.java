package version4.SEC;

import org.jfree.data.xy.XYSeries;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import version4.Collection;
import version4.sasCIF.SasObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Round-trip and editing tests for the *.sec reader, writer and editor, on small synthetic runs.
 */
public class SecFileIOTest {

    private static final int FRAMES = 120;
    private static final int TOTAL_Q = 100;
    private static final double RG = 30.0;

    private Path dir;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("sectest");
    }

    @After
    public void tearDown() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Flat buffer with noise; frames 55-70 carry a Guinier-like particle peak. */
    static Collection syntheticRun() {
        return syntheticRun(FRAMES);
    }

    static Collection syntheticRun(int frames) {
        Random random = new Random(42);
        Collection collection = new Collection("synthetic");
        for (int f = 0; f < frames; f++) {
            double amplitude = (f >= 55 && f <= 70) ? 5.0 * Math.exp(-Math.pow((f - 62) / 4.0, 2)) : 0.0;
            XYSeries data = new XYSeries("f" + f);
            XYSeries err = new XYSeries("f" + f);
            for (int k = 0; k < TOTAL_Q; k++) {
                double q = 0.01 + k * 0.003;
                double buffer = 1.0 + 0.2 * Math.exp(-q * 10);
                double particle = amplitude * Math.exp(-q * q * RG * RG / 3.0);
                double sigma = 0.002 * buffer;
                data.add(q, buffer + particle + sigma * random.nextGaussian());
                err.add(q, sigma);
            }
            collection.createDataset(data, err);
        }
        return collection;
    }

    static SecData process(Collection collection) {
        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(collection);
        assertTrue(common.isAcceptable());
        SecProcessor processor = new SecProcessor(collection, common.qvalues, 1.004, 0, null);
        processor.estimateBufferFrames();
        return processor.process();
    }

    private Path writeSynthetic(String name) throws IOException {
        Path file = dir.resolve(name);
        SecFileWriter.write(file, new SasObject(), process(syntheticRun()), null);
        return file;
    }

    /** Turn a v2 file into what an older release on Windows would have written: CRLF, no padding, no version key. */
    private Path toLegacyCrlf(Path v2, String name) throws IOException {
        List<String> lines = Files.readAllLines(v2, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replaceAll("\\s+$", "");
            if (i == 0) {
                line = line.replace("{\"" + SecFileWriter.VERSION_KEY + "\":2,", "{");
            } else if (i >= 4) {
                line = line + " "; // v1 lines end with exactly one space
            }
            sb.append(line).append("\r\n");
        }
        Path legacy = dir.resolve(name);
        Files.write(legacy, sb.toString().getBytes(StandardCharsets.UTF_8));
        return legacy;
    }

    private static void assertIntact(Path file) throws IOException {
        try (SecFileReader reader = new SecFileReader(file)) {
            assertEquals("corrupted lines: ", Collections.emptyList(), reader.validate(null));
            SecFormat f = reader.getSecFormat();
            int frames = reader.readIntVector(f.getFrame_index()).length;
            // every frame line still carries its own frame number
            for (int i = 0; i < frames; i++) {
                reader.readFrame(f.getUnsubtracted_intensities_index(), i);
                reader.readFrame(f.getSubtracted_intensities_error_index(), i);
            }
        }
    }

    private static long countCrlf(byte[] bytes) {
        long n = 0;
        for (int i = 1; i < bytes.length; i++) if (bytes[i] == '\n' && bytes[i - 1] == '\r') n++;
        return n;
    }

    // ------------------------------------------------------------------ tests

    @Test
    public void writeThenReadRoundTrip() throws IOException {
        Path file = writeSynthetic("run.sec");
        SECFile sec = new SECFile(file.toFile());
        try {
            assertEquals(2, sec.getFormatVersion());
            assertEquals("\n", sec.getNew_line_separator());
            assertEquals(FRAMES, sec.getTotalFrames());
            assertEquals(FRAMES, sec.getSasObject().getSecFormat().getTotal_frames());
            assertEquals(TOTAL_Q, sec.getTotalQValues());

            // buffer flags: only frames actually used for the averaged buffer
            int buffers = sec.getBufferCount();
            assertTrue("buffers " + buffers, buffers >= SecProcessor.MIN_BUFFER_FRAMES);
            for (int f = 55; f <= 70; f++) {
                assertTrue("peak frame " + f + " flagged as buffer", sec.getBackground().indexOf((double) f) < 0);
            }

            // the tail of the run is never used as buffer
            int last = SecProcessor.lastBufferCandidate(FRAMES);
            for (int f = last + 1; f < FRAMES; f++) {
                assertTrue("tail frame " + f + " flagged as buffer", sec.getBackground().indexOf((double) f) < 0);
            }

            // Guinier analysis at the peak recovers Rg
            assertEquals(RG, sec.getRgbyIndex(62), 1.5);
            assertEquals(0.0, sec.getRgbyIndex(10), 0.0);

            // subtracted peak frame ~ particle scattering
            double q0 = sec.getQvalues().get(0);
            double expected = 5.0 * Math.exp(-q0 * q0 * RG * RG / 3.0);
            assertEquals(expected, sec.getSubtractedFrameAt(62).get(0), 0.05);
        } finally {
            sec.closeFile();
        }
        assertIntact(file);
    }

    @Test
    public void editableLinesArePaddedAndEditedInPlace() throws IOException {
        Path file = writeSynthetic("inplace.sec");
        long size = Files.size(file);

        SECFile sec = new SECFile(file.toFile());
        sec.updateBufferIndices(new TreeSet<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7)));
        StringBuilder rg = new StringBuilder();
        for (int i = 0; i < FRAMES; i++) rg.append(SecFileWriter.convertDoubleTo3EString(-12345.678 * i)); // widest values
        sec.updateRgLine(rg.toString());
        sec.closeFile();

        assertEquals("file should not have been rewritten", size, Files.size(file));
        SECFile reopened = new SECFile(file.toFile());
        assertEquals(8, reopened.getBufferCount());
        assertEquals(-12345.678 * 3, reopened.getRgbyIndex(3), 5.0); // %.3E keeps 4 significant figures
        reopened.closeFile();
        assertIntact(file);
    }

    @Test
    public void legacyCrlfFileGrowsWithoutCorruption() throws IOException {
        Path legacy = toLegacyCrlf(writeSynthetic("v2.sec"), "legacy.sec");

        boolean posix = legacy.getFileSystem().supportedFileAttributeViews().contains("posix");
        java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
        if (posix) Files.setPosixFilePermissions(legacy, perms);

        SECFile sec = new SECFile(legacy.toFile());
        assertEquals(1, sec.getFormatVersion());
        assertEquals("\r\n", sec.getNew_line_separator());
        ArrayList<Double> before = sec.getSubtractedFrameAt(FRAMES - 1);
        int lines = 13 + 4 * FRAMES + 2;

        // much longer than the existing line: forces a rewrite
        StringBuilder rg = new StringBuilder();
        for (int i = 0; i < FRAMES; i++) rg.append(SecFileWriter.convertSignalToString(-i - 0.5));
        sec.updateRgLine(rg.toString());
        // same length: in place
        sec.updateBufferIndices(new TreeSet<>(Collections.singletonList(3)));
        sec.closeFile();

        byte[] bytes = Files.readAllBytes(legacy);
        assertEquals("every line must still end in CRLF", lines, countCrlf(bytes));
        if (posix) assertEquals("rewrite must keep permissions", perms, Files.getPosixFilePermissions(legacy));
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            assertEquals("no temp files left behind", 0, files.filter(p -> p.toString().endsWith(".tmp")).count());
        }
        assertIntact(legacy);

        SECFile reopened = new SECFile(legacy.toFile());
        assertEquals(-10.5, reopened.getRgbyIndex(10), 1e-6);
        assertEquals(1, reopened.getBufferCount());
        assertEquals(before, reopened.getSubtractedFrameAt(FRAMES - 1));
        reopened.closeFile();
    }

    @Test
    public void lastLineCanBeReplaced() throws IOException {
        Path file = writeSynthetic("last.sec");
        SECFile sec = new SECFile(file.toFile());
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < TOTAL_Q; i++) values.append(SecFileWriter.convertSignalToString(i)); // longer than before
        sec.updateAveragedBuffer(values.toString(), values.toString());
        sec.closeFile();

        assertIntact(file);
        try (SecFileReader reader = new SecFileReader(file)) {
            double[] err = reader.readVector(reader.getSecFormat().getAveraged_buffer_error_index(), Double.NaN);
            assertEquals(TOTAL_Q, err.length);
            assertEquals(42.0, err[42], 0.0);
            assertEquals(13 + 4 * FRAMES + 2, reader.getLineCount());
        }
    }

    @Test
    public void nonAsciiHeaderKeepsOffsetsCorrect() throws IOException {
        Path file = writeSynthetic("utf8.sec");
        SECFile sec = new SECFile(file.toFile());
        sec.getSasObject().getSasBuffer().setComment("20 mM Tris, 150 mM NaCl, 5 µM ligand, Å-scale, 25 °C");
        sec.updateHeader();
        sec.closeFile();

        assertIntact(file);
        SECFile reopened = new SECFile(file.toFile());
        assertEquals("20 mM Tris, 150 mM NaCl, 5 µM ligand, Å-scale, 25 °C", reopened.getSasObject().getSasBuffer().getComment());
        assertEquals(FRAMES, reopened.getTotalFrames());
        reopened.closeFile();
    }

    @Test
    public void guinierUpdateOnLegacyFileWithoutErrorLinesKeepsHeader() throws IOException {
        // legacy layout without Rg/I(0) error lines: their index is 0, which used to overwrite the header
        Path legacy = toLegacyCrlf(writeSynthetic("v2.sec"), "noerr.sec");
        try (SecFileReader reader = new SecFileReader(legacy)) {
            SasObject header = reader.getSasObject();
            header.getSecFormat().setRg_error_index(0);
            header.getSecFormat().setIzero_error_index(0);
            SecProcessor.GuinierResults r = new SecProcessor.GuinierResults(FRAMES);
            r.rg[7] = 21.0;
            SECFile.writeGuinier(reader, header, 1.25, r);
        }
        SECFile sec = new SECFile(legacy.toFile());
        assertEquals(1.25, sec.getThreshold(), 0.0);
        assertEquals(21.0, sec.getRgbyIndex(7), 1e-9);
        sec.closeFile();
        assertIntact(legacy);
    }

    @Test
    public void detectsCorruptedLine() throws IOException {
        Path file = writeSynthetic("corrupt.sec");
        byte[] bytes = Files.readAllBytes(file);
        try (SecFileReader reader = new SecFileReader(file)) {
            int line = reader.getSecFormat().getSubtracted_intensities_index() + 5;
            int pos = (int) reader.getLineOffset(line) + reader.getLineLength(line) - 3;
            bytes[pos] = (byte) (bytes[pos] == '1' ? '2' : '1');
        }
        Files.write(file, bytes);
        try (SecFileReader reader = new SecFileReader(file)) {
            int line = reader.getSecFormat().getSubtracted_intensities_index() + 5;
            assertEquals(Collections.singletonList(line), reader.validate(null));
        }
    }

    @Test
    public void closestIndexIsClampedAndRoundsToNearest() throws IOException {
        Path file = writeSynthetic("closest.sec");
        SECFile sec = new SECFile(file.toFile());
        try {
            ArrayList<Double> q = sec.getQvalues();
            assertEquals(0, sec.getClosestIndex(-1.0));
            assertEquals(0, sec.getClosestIndex(q.get(0)));
            assertEquals(q.size() - 1, sec.getClosestIndex(10.0));
            assertEquals(5, sec.getClosestIndex(q.get(5)));
            assertEquals(5, sec.getClosestIndex(q.get(5) + 0.4 * (q.get(6) - q.get(5))));
            assertEquals(6, sec.getClosestIndex(q.get(5) + 0.6 * (q.get(6) - q.get(5))));
        } finally {
            sec.closeFile();
        }
    }

    @Test
    public void shortRunsAreRejected() throws IOException {
        Collection tooShort = syntheticRun(76);
        try {
            new SECBuilder(tooShort, null, null, "short", dir.toString(), 1.004);
            fail("a 76-frame run must be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("too short"));
        }
        // the engine enforces it too
        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(tooShort);
        try {
            new SecProcessor(tooShort, common.qvalues, 1.004, 0, null);
            fail("SecProcessor must reject a 76-frame run");
        } catch (IllegalArgumentException expected) {
            // ok
        }

        // frames not in use do not count
        Collection mixed = syntheticRun(SecProcessor.MIN_FRAMES + 1);
        mixed.getDataset(3).setInUse(false);
        mixed.getDataset(4).setInUse(false);
        try {
            new SECBuilder(mixed, null, null, "mixed", dir.toString(), 1.004);
            fail("run with only " + (SecProcessor.MIN_FRAMES - 1) + " frames in use must be rejected");
        } catch (IOException expected) {
            // ok
        }

        // the minimum itself is accepted
        new SECBuilder(syntheticRun(SecProcessor.MIN_FRAMES), null, null, "ok", dir.toString(), 1.004);
    }

    @Test
    public void manualBufferSelectionMayIncludeEndOfRun() {
        Collection run = syntheticRun();
        SecProcessor.CommonQ common = SecProcessor.findCommonQValues(run);
        SecProcessor processor = new SecProcessor(run, common.qvalues, 1.004, 0, null);
        TreeSet<Integer> manual = new TreeSet<>();
        for (int f = FRAMES - 20; f < FRAMES; f++) manual.add(f);
        processor.useBufferFrames(manual);
        SecData data = processor.process();
        for (int f = FRAMES - 20; f < FRAMES; f++) assertTrue("frame " + f, data.isBuffer(f));
        assertFalse(data.isBuffer(10));
    }

    @Test
    public void outputNameDropsRunNumbers() {
        assertEquals("BSA_0p6_mg_per_ml_11", SECBuilder.deriveOutputName("23543_BSA_0p6_mg_per_ml_11"));
        assertEquals("sample_A", SECBuilder.deriveOutputName("sample_A_00012"));
        assertEquals("sample", SECBuilder.deriveOutputName("sample"));
        assertEquals("12345", SECBuilder.deriveOutputName("12345"));
    }

    @Test
    public void olderReleasesCanParseVersion2Header() throws IOException {
        Path file = writeSynthetic("compat.sec");
        String header = Files.readAllLines(file, StandardCharsets.UTF_8).get(0);
        assertTrue(header.startsWith("{\"" + SecFileWriter.VERSION_KEY + "\":2,"));
        // the v1 parsing path: SasObject from the raw (padded) line
        SasObject parsed = new SasObject(header);
        assertNotNull(parsed.getSecFormat());
        assertEquals(FRAMES, parsed.getSecFormat().getTotal_frames());
    }

    @Test
    public void repositoryLegacyFileStillReads() throws IOException {
        File legacy = new File("src/test/testData/BSA_sec.sec");
        org.junit.Assume.assumeTrue(legacy.exists());
        SECFile sec = new SECFile(legacy);
        try {
            assertEquals(1, sec.getFormatVersion());
            assertEquals(793, sec.getTotalFrames());
            assertEquals(1647, sec.getTotalQValues());
            assertEquals(1647, sec.getSubtractedFrameAt(300).size());
            assertEquals(Collections.emptyList(), sec.validate(null));
        } finally {
            sec.closeFile();
        }
    }
}
