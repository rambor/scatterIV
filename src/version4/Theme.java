package version4;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * Look-and-feel for Scatter.
 *
 * <p>Uses FlatLaf (https://www.formdev.com/flatlaf/) when its jar is on the classpath (put
 * {@code flatlaf-x.y.jar} in {@code lib/}). FlatLaf is loaded by reflection, so Scatter still builds and runs
 * without it, falling back to the platform look-and-feel.</p>
 *
 * Themes, set with {@code theme=...} in scatter.config (see AppConfig) or {@code -Dscatter.theme=...}:
 * <ul>
 *   <li>{@code light} (default): FlatLaf light</li>
 *   <li>{@code dark}: FlatLaf dark. Several tables and plot windows have a fixed white background, so parts of
 *       Scatter look mixed in this theme</li>
 *   <li>{@code system}: the operating system's own look-and-feel</li>
 *   <li>{@code off}: Java's default (Metal), i.e. no change</li>
 * </ul>
 * On macOS the FlatLaf "Mac" variants are used when available.
 *
 * Must be called before any window is created.
 */
public final class Theme {

    public static final String DEFAULT = "light";

    private static String applied = "off";

    private Theme() {}

    /** Name of the theme actually in use ("dark", "light", "system" or "off"). */
    public static String getApplied() { return applied; }

    /**
     * Apply the requested theme.
     * @param requested "dark", "light", "system" or "off"; null or unknown means {@value #DEFAULT}
     * @return the theme actually applied
     */
    public static String apply(String requested) {
        String theme = (requested == null) ? DEFAULT : requested.trim().toLowerCase();
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        if (mac) {
            System.setProperty("apple.laf.useScreenMenuBar", "true"); // menu bar at the top of the screen
        }

        switch (theme) {
            case "off":
                applied = "off";
                return applied;
            case "system":
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    applied = "system";
                } catch (Exception e) {
                    System.out.println("Theme :: could not use system look-and-feel: " + e.getMessage());
                    applied = "off";
                }
                return applied;
            case "light":
            case "dark":
                break;
            default:
                theme = DEFAULT;
        }

        boolean dark = theme.equals("dark");
        String[] candidates = mac
                ? new String[]{dark ? "com.formdev.flatlaf.themes.FlatMacDarkLaf" : "com.formdev.flatlaf.themes.FlatMacLightLaf",
                               dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf"}
                : new String[]{dark ? "com.formdev.flatlaf.FlatDarkLaf" : "com.formdev.flatlaf.FlatLightLaf"};

        for (String className : candidates) {
            if (setupFlatLaf(className)) {
                applied = theme;
                System.out.println("Theme :: " + className);
                return applied;
            }
        }

        System.out.println("Theme :: FlatLaf not found (add flatlaf-x.y.jar to lib/); using default look-and-feel");
        applied = "off";
        return applied;
    }

    private static boolean setupFlatLaf(String className) {
        try {
            Class<?> laf = Class.forName(className);
            Method setup = laf.getMethod("setup");
            Object ok = setup.invoke(null);
            return !(ok instanceof Boolean) || (Boolean) ok;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("Theme :: " + className + " failed: " + e);
            return false;
        }
    }
}
