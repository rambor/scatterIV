package version4;

import FileManager.FileObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Datasets whose details or results have not been written to a file, and writing them all, for the
 * question Scatter asks when it is closed. Files are written to one folder as *.dat; an existing file
 * is never overwritten (a suffix is added instead), so the files a user loaded stay as they were.
 */
public final class UnsavedChanges {

    private UnsavedChanges() { }

    /** what {@link #saveAll} did */
    public static final class Result {
        public final List<File> written = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
    }

    /** the datasets in {@code c} with changes not yet in a file, in collection order */
    public static List<Dataset> unsaved(Collection c) {
        List<Dataset> out = new ArrayList<>();
        if (c == null) return out;
        for (Dataset d : c.getDatasets()) {
            if (d != null && d.hasUnsavedChangesNow()) out.add(d);
        }
        return out;
    }

    /** up to {@code max} names, one per line, then "... and N more" */
    public static String names(List<Dataset> list, int max) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < list.size() && i < max; i++) {
            if (i > 0) s.append('\n');
            s.append(list.get(i).getFileName());
        }
        if (list.size() > max) s.append("\n... and ").append(list.size() - max).append(" more");
        return s.toString();
    }

    /**
     * Base name (no ".dat") for {@code name} in {@code dir} that does not overwrite anything:
     * the name itself if free, else name_edited, name_edited2, ...
     */
    public static String freeBaseName(File dir, String name) {
        String base = (name == null || name.trim().isEmpty()) ? "dataset" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (base.toLowerCase().endsWith(".dat")) base = base.substring(0, base.length() - 4);
        if (!new File(dir, base + ".dat").exists()) return base;
        String candidate = base + "_edited";
        for (int i = 2; new File(dir, candidate + ".dat").exists(); i++) candidate = base + "_edited" + i;
        return candidate;
    }

    /** write every dataset in {@code list} to {@code dir} as *.dat (see {@link #freeBaseName}) */
    public static Result saveAll(List<Dataset> list, File dir, String version) {
        Result r = new Result();
        if (dir == null || !(dir.isDirectory() || dir.mkdirs())) {
            for (Dataset d : list) r.failed.add(d.getFileName() + ": folder " + dir + " cannot be used");
            return r;
        }
        FileObject writer = new FileObject(dir, version);
        for (Dataset d : list) {
            String base = freeBaseName(dir, d.getFileName());
            File target = new File(dir, base + ".dat");
            try {
                writer.writeSAXSFile(base, d);
            } catch (RuntimeException e) {
                r.failed.add(d.getFileName() + ": " + e.getMessage());
                continue;
            }
            if (target.isFile() && !d.hasUnsavedChangesNow()) r.written.add(target);
            else r.failed.add(d.getFileName() + ": could not write " + target);
        }
        return r;
    }
}
