package version4.SEC;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import version4.sasCIF.SasObject;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Reading, editing and writing gzip-compressed *.sec files. */
public class SecGzipTest {

    private Path dir;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("secgz");
    }

    @After
    public void tearDown() throws IOException {
        try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private Path plainRun() throws IOException {
        Path file = dir.resolve("run.sec");
        SecFileWriter.write(file, new SasObject(), SecFileIOTest.process(SecFileIOTest.syntheticRun()), null);
        return file;
    }

    private Path compressedRun() throws IOException {
        Path plain = plainRun();
        Path gz = dir.resolve("run.sec.gz");
        SECFile sec = new SECFile(plain.toFile());
        sec.exportCopy(gz.toFile(), true);
        sec.closeFile();
        return gz;
    }

    private static byte[] gunzip(Path gz) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(gz))) {
            return in.readAllBytes();
        }
    }

    @Test
    public void compressedCopyIsStandardGzipOfTheSameFile() throws IOException {
        Path plain = plainRun();
        Path gz = dir.resolve("copy.sec.gz");
        SECFile sec = new SECFile(plain.toFile());
        sec.exportCopy(gz.toFile(), true);
        sec.closeFile();

        assertTrue(SecGzip.isGzip(gz));
        assertFalse(SecGzip.isGzip(plain));
        assertArrayEquals(Files.readAllBytes(plain), gunzip(gz));
        assertTrue("should be much smaller", Files.size(gz) * 3 < Files.size(plain));
    }

    @Test
    public void opensCompressedFileLikeAPlainOne() throws IOException {
        Path gz = compressedRun();
        SECFile plain = new SECFile(dir.resolve("run.sec").toFile());
        SECFile sec = new SECFile(gz.toFile());
        try {
            assertTrue(sec.isCompressed());
            assertFalse(plain.isCompressed());
            assertEquals("run.sec.gz", sec.getFilename());
            assertEquals("run", sec.getFilebase());
            assertEquals(gz.toString(), sec.getAbsolutePath());
            assertEquals(plain.getTotalFrames(), sec.getTotalFrames());
            assertEquals(plain.getQvalues(), sec.getQvalues());
            assertEquals(plain.getSubtractedFrameAt(62), sec.getSubtractedFrameAt(62));
            assertEquals(plain.getRgbyIndex(62), sec.getRgbyIndex(62), 0.0);
            assertEquals(Collections.emptyList(), sec.validate(null));
        } finally {
            plain.closeFile();
            Path temp = sec.getWorkingPath();
            sec.closeFile();
            assertFalse("temporary copy should be removed on close", Files.exists(temp));
        }
    }

    @Test
    public void editsAreWrittenBackToTheGzFile() throws IOException {
        Path gz = compressedRun();
        SECFile sec = new SECFile(gz.toFile());
        sec.getSasObject().getSasBuffer().setComment("edited µ");
        sec.updateHeader();
        sec.updateBufferIndices(new TreeSet<>(Arrays.asList(1, 2, 3)));
        sec.closeFile();

        assertTrue(SecGzip.isGzip(gz));
        SECFile reopened = new SECFile(gz.toFile());
        assertEquals("edited µ", reopened.getSasObject().getSasBuffer().getComment());
        assertEquals(3, reopened.getBufferCount());
        assertEquals(Collections.emptyList(), reopened.validate(null));
        reopened.closeFile();
    }

    @Test
    public void builderWritesCompressedWhenNameEndsInGz() throws Exception {
        SECBuilder builder = new SECBuilder(SecFileIOTest.syntheticRun(), new JLabel(), new JProgressBar(),
                "made.sec.gz", dir.toString(), 1.004);
        assertTrue(builder.isCompressOutput());
        builder.doInBackground();

        Path out = dir.resolve("made.sec.gz");
        assertEquals(out.toString(), builder.getOutputname());
        assertTrue(SecGzip.isGzip(out));
        assertFalse("no plain file left behind", Files.exists(dir.resolve("made.sec")));
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            assertEquals("no temp files left behind", 0, files.filter(p -> p.toString().endsWith(".tmp")).count());
        }
        SECFile sec = new SECFile(out.toFile());
        assertEquals(120, sec.getTotalFrames());
        sec.closeFile();
    }

    @Test
    public void rebuildAndGuinierUpdateKeepCompression() throws Exception {
        Path gz = compressedRun();

        // threshold change: Rg/I(0) updated inside the .gz
        SECFile sec = new SECFile(gz.toFile());
        SECBuilder guinier = new SECBuilder(sec, new JLabel(), new JProgressBar(), dir.toString(), 1.2, 0);
        sec.closeFile();
        guinier.doInBackground();
        SECFile updated = new SECFile(gz.toFile());
        assertTrue(updated.isCompressed());
        assertEquals(1.2, updated.getThreshold(), 0.0);
        updated.closeFile();

        // new buffer selection: rebuilt file is compressed too
        SECFile again = new SECFile(gz.toFile());
        TreeSet<Integer> buffers = new TreeSet<>();
        for (int i = 0; i < 30; i++) buffers.add(i);
        SECBuilder rebuild = new SECBuilder(again, buffers, new JLabel(), new JProgressBar(), dir.toString(), 1.004);
        again.closeFile();
        rebuild.doInBackground();
        assertEquals(gz.toString(), rebuild.getOutputname());
        SECFile rebuilt = new SECFile(gz.toFile());
        assertTrue(rebuilt.isCompressed());
        assertEquals(30, rebuilt.getBufferCount());
        rebuilt.closeFile();
    }

    @Test(expected = IOException.class)
    public void cannotExportOverTheOpenFile() throws IOException {
        Path plain = plainRun();
        SECFile sec = new SECFile(plain.toFile());
        try {
            sec.exportCopy(plain.toFile(), false);
        } finally {
            sec.closeFile();
        }
    }

    @Test
    public void recognisesSecFileNames() {
        assertTrue(SECFile.isSecFileName("run.sec"));
        assertTrue(SECFile.isSecFileName("RUN.SEC.GZ"));
        assertFalse(SECFile.isSecFileName("frame_0001.dat"));
        assertFalse(SECFile.isSecFileName("notes.gz"));
    }
}
