package version4;

import FileManager.FileObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Saving a dataset to its own file or a new one (the Analysis tab's Save File).
 *
 * <ul>
 *   <li>the whole curve is written (every point, not just the selected q-range), with the header and
 *       the JSON line carrying the details and results;</li>
 *   <li>replacing a file that Scatter did not write (e.g. from a beamline pipeline) first keeps the
 *       original beside it as {@code name.orig.dat}, once; later saves just replace;</li>
 *   <li>afterwards the file is the dataset's own: it is what Save File offers next time, and the
 *       dataset counts as saved.</li>
 * </ul>
 * Asking before replacing is the caller's job ({@link #exists}).
 */
public final class DatasetSaver {

    private DatasetSaver() { }

    /** what a save did */
    public static final class Outcome {
        public final File written;
        /** the original kept before it was replaced, or null */
        public final File backup;
        public final boolean replaced;

        Outcome(File written, File backup, boolean replaced) {
            this.written = written;
            this.backup = backup;
            this.replaced = replaced;
        }

        /** "sample.dat saved (replaced; original kept as sample.orig.dat)" */
        public String describe() {
            String s = written.getName() + " saved in " + written.getParent();
            if (replaced) s += backup != null ? " (replaced; original kept as " + backup.getName() + ")" : " (replaced)";
            return s;
        }
    }

    /** the file Save File offers: the dataset's own, else its name in {@code defaultDir} */
    public static File suggestedFile(Dataset d, File defaultDir) {
        File own = d.getSourceFile();
        if (own != null && own.getParentFile() != null && own.getParentFile().isDirectory()) {
            return datFile(own.getParentFile(), baseName(own.getName()));
        }
        return datFile(defaultDir, baseName(d.getFileName()));
    }

    /** {@code dir/base.dat} */
    public static File datFile(File dir, String base) {
        return new File(dir, base + ".dat");
    }

    /** "sample" for "sample.dat", "sample.txt", "sample" (other extensions are dropped; Scatter writes .dat) */
    public static String baseName(String fileName) {
        String n = fileName == null ? "" : fileName.trim();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return n.isEmpty() ? "dataset" : n;
    }

    public static boolean exists(File target) { return target != null && target.isFile(); }

    /** does the file's header say Scatter wrote it? */
    public static boolean writtenByScatter(File f) {
        if (!exists(f)) return false;
        try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.ISO_8859_1)) {
            String line;
            for (int i = 0; i < 40 && (line = r.readLine()) != null; i++) {
                String u = line.toUpperCase(Locale.ROOT);
                if (u.contains("DATA REDUCTION SOFTWARE : SCATTER") || u.contains("DATA ANALYSIS SOFTWARE : SCATTER")) return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** where the original of {@code target} is kept: name.orig.dat beside it */
    public static File backupFile(File target) {
        return new File(target.getParentFile(), baseName(target.getName()) + ".orig.dat");
    }

    /**
     * Write the whole dataset to {@code target} (its extension is set to .dat), keeping the original
     * first if {@code target} exists, was not written by Scatter and has no backup yet.
     */
    public static Outcome save(Dataset d, File target, String version) throws IOException {
        File dir = target.getAbsoluteFile().getParentFile();
        String base = baseName(target.getName());
        File out = datFile(dir, base);
        boolean replacing = exists(out);
        File backup = null;
        if (replacing && !writtenByScatter(out)) {
            File b = backupFile(out);
            if (!b.exists()) {
                Files.copy(out.toPath(), b.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                backup = b;
            }
        }
        d.setFileName(base);                       // the dataset takes the file's name
        new FileObject(dir, version).writeSAXSFile(base, d);
        if (!out.isFile() || d.hasUnsavedChangesNow()) throw new IOException("could not write " + out);
        return new Outcome(out, backup, replacing);
    }
}
