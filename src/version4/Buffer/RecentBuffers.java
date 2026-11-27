package version4.Buffer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import version4.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The buffers a user has entered, most recent first, kept in {@code recent_buffers.json} in Scatter's
 * settings folder so they are offered again next time. The same buffer written with different
 * spacing or case is kept once.
 */
public final class RecentBuffers {

    public static final String FILE = "recent_buffers.json";
    public static final int MAX = 20;

    private final Path file;
    private final List<String> entries = new ArrayList<>();

    private static RecentBuffers standard;

    /** the user's list, in the settings folder */
    public static synchronized RecentBuffers standard() {
        if (standard == null) standard = new RecentBuffers(AppConfig.configDirectory().resolve(FILE));
        return standard;
    }

    /** a list kept in {@code file} (read now if it exists) */
    public RecentBuffers(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            try {
                List<String> saved = new ObjectMapper().readValue(file.toFile(), new TypeReference<List<String>>() { });
                for (String s : saved) if (s != null && !s.trim().isEmpty() && entries.size() < MAX) entries.add(s.trim());
            } catch (IOException e) {
                // unreadable: start empty, it is rewritten on the next add
            }
        }
    }

    public synchronized List<String> list() { return Collections.unmodifiableList(new ArrayList<>(entries)); }

    /** put {@code text} first (moving it if it is already there) and save */
    public synchronized void add(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String t = text.trim().replaceAll("\\s+", " ");
        String key = key(t);
        entries.removeIf(e -> key(e).equals(key));
        entries.add(0, t);
        while (entries.size() > MAX) entries.remove(entries.size() - 1);
        save();
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replaceAll("[,;]+$", "");
    }

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), entries);
        } catch (IOException e) {
            // not fatal: the list just is not remembered
        }
    }
}
