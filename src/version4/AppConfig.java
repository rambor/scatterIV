package version4;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Properties;

/**
 * Where Scatter keeps its settings file ({@value #FILE_NAME}).
 *
 * <p>Scatter used to read and write scatter.config in the current directory. An installed app is started from a
 * folder it cannot write to ("/" on macOS, Program Files on Windows), so settings now live in the user's own
 * application-data folder:</p>
 * <ul>
 *   <li>macOS: {@code ~/Library/Application Support/Scatter}</li>
 *   <li>Windows: {@code %APPDATA%\Scatter}</li>
 *   <li>Linux: {@code $XDG_CONFIG_HOME/scatter}, or {@code ~/.config/scatter}</li>
 * </ul>
 * {@code -Dscatter.configDir=/some/folder} overrides this.
 *
 * <p>If there is no settings file there yet but an old scatter.config exists in the current directory, it is copied
 * across once, so existing settings are kept.</p>
 */
public final class AppConfig {

    public static final String FILE_NAME = "scatter.config";

    private AppConfig() {}

    /** Folder for Scatter's settings (not created by this call). */
    public static Path configDirectory() {
        String override = System.getProperty("scatter.configDir");
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim());
        }

        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "Scatter");
        }
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return (appData != null && !appData.isEmpty())
                    ? Paths.get(appData, "Scatter")
                    : Paths.get(home, "AppData", "Roaming", "Scatter");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return (xdg != null && !xdg.isEmpty()) ? Paths.get(xdg, "scatter") : Paths.get(home, ".config", "scatter");
    }

    /** The settings file. */
    public static Path configFile() {
        return configDirectory().resolve(FILE_NAME);
    }

    /** The old location: scatter.config in the current directory. */
    static Path legacyConfigFile() {
        return Paths.get(System.getProperty("user.dir")).resolve(FILE_NAME);
    }

    /**
     * Load the settings. Returns empty properties if there are none yet.
     * An old scatter.config in the current directory is copied to the new location the first time.
     */
    public static Properties load() {
        Properties prop = new Properties();
        Path file = configFile();
        migrateLegacy(file);
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                prop.load(in);
            } catch (IOException e) {
                System.out.println("AppConfig :: could not read " + file + ": " + e.getMessage());
            }
        }
        return prop;
    }

    private static void migrateLegacy(Path file) {
        Path legacy = legacyConfigFile();
        try {
            if (!Files.exists(file) && Files.isRegularFile(legacy) && !legacy.toAbsolutePath().equals(file.toAbsolutePath())) {
                Files.createDirectories(file.getParent());
                Files.copy(legacy, file);
                System.out.println("AppConfig :: copied settings from " + legacy + " to " + file);
            }
        } catch (IOException | SecurityException e) {
            System.out.println("AppConfig :: could not copy old settings from " + legacy + ": " + e.getMessage());
        }
    }

    /**
     * Save the settings, creating the folder if needed. Written to a temporary file first and then moved into
     * place, so a crash never leaves a half-written settings file.
     */
    public static void save(Properties prop) throws IOException {
        Path file = configFile();
        Path dir = file.getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, FILE_NAME, ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                prop.store(out, "Scatter settings");
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
