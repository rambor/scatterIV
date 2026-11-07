package version4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;

import static org.junit.Assert.*;

public class AppConfigTest {

    private Path root;
    private String oldUserDir, oldConfigDir, oldOs, oldHome;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory("appconfig");
        oldUserDir = System.getProperty("user.dir");
        oldConfigDir = System.getProperty("scatter.configDir");
        oldOs = System.getProperty("os.name");
        oldHome = System.getProperty("user.home");
    }

    @After
    public void tearDown() throws IOException {
        restore("user.dir", oldUserDir);
        restore("scatter.configDir", oldConfigDir);
        restore("os.name", oldOs);
        restore("user.home", oldHome);
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }

    @Test
    public void platformFolders() {
        System.clearProperty("scatter.configDir");
        System.setProperty("user.home", "/Users/someone");
        System.setProperty("os.name", "Mac OS X");
        assertEquals("/Users/someone/Library/Application Support/Scatter/scatter.config",
                AppConfig.configFile().toString().replace('\\', '/'));
        System.setProperty("os.name", "Linux");
        assertTrue(AppConfig.configFile().toString().replace('\\', '/').endsWith("/scatter/scatter.config"));
    }

    @Test
    public void saveThenLoad() throws IOException {
        System.setProperty("scatter.configDir", root.resolve("new folder").toString());
        Properties p = new Properties();
        p.setProperty("workingDirectory", "/data/runs");
        p.setProperty("theme", "light");
        AppConfig.save(p);

        assertTrue(Files.isRegularFile(root.resolve("new folder").resolve("scatter.config")));
        Properties loaded = AppConfig.load();
        assertEquals("/data/runs", loaded.getProperty("workingDirectory"));
        assertEquals("light", loaded.getProperty("theme"));
        try (java.util.stream.Stream<Path> files = Files.list(root.resolve("new folder"))) {
            assertEquals("no temp files left", 1, files.count());
        }
    }

    @Test
    public void oldConfigInCurrentDirectoryIsCopiedOnce() throws IOException {
        Path project = Files.createDirectories(root.resolve("project"));
        Files.write(project.resolve("scatter.config"), "workingDirectory=/old/place\ntheme=dark\n".getBytes());
        System.setProperty("user.dir", project.toString());
        System.setProperty("scatter.configDir", root.resolve("settings").toString());

        assertEquals("/old/place", AppConfig.load().getProperty("workingDirectory"));
        assertTrue(Files.exists(root.resolve("settings").resolve("scatter.config")));

        // later changes go to the new file; the old one is not copied again
        Properties p = new Properties();
        p.setProperty("workingDirectory", "/new/place");
        AppConfig.save(p);
        assertEquals("/new/place", AppConfig.load().getProperty("workingDirectory"));
        assertTrue("old file left untouched", new String(Files.readAllBytes(project.resolve("scatter.config"))).contains("/old/place"));
    }

    @Test
    public void noSettingsYetGivesEmptyProperties() {
        System.setProperty("user.dir", root.toString());
        System.setProperty("scatter.configDir", root.resolve("empty").toString());
        assertTrue(AppConfig.load().isEmpty());
    }
}
