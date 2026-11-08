package version4;

import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.ImageIcon;

/**
 * Puts the ScÅtter icon on every window while the program runs:
 * the title bar and taskbar (Windows, Linux), the Alt-Tab switcher, and the
 * macOS Dock when started outside the installed app (e.g. from IntelliJ).
 *
 * The images are /icons/scatter_<size>.png (written by packaging/make_icon.py).
 * Call {@link #install()} once on the EDT before the first window is shown;
 * windows opened later, such as the plot frames, get the icon automatically.
 */
public final class AppIcon {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    private static List<Image> images;
    private static boolean installed;

    private AppIcon() {}

    /** All icon sizes, smallest first; empty if the images are not on the classpath. */
    public static synchronized List<Image> images() {
        if (images == null) {
            List<Image> list = new ArrayList<>();
            for (int n : SIZES) {
                URL url = AppIcon.class.getResource("/icons/scatter_" + n + ".png");
                if (url != null) list.add(new ImageIcon(url).getImage());
            }
            images = Collections.unmodifiableList(list);
        }
        return images;
    }

    /** Sets the icon on a window that has none of its own. */
    public static void apply(Window w) {
        List<Image> icons = images();
        if (icons.isEmpty() || w == null) return;
        if (w instanceof Frame && !((Frame) w).getIconImages().isEmpty()) return; // keep a custom icon
        w.setIconImages(icons);
    }

    /** Dock icon plus a listener that gives each newly opened frame the icon. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        List<Image> icons = images();
        if (icons.isEmpty()) {
            System.out.println("AppIcon: /icons/scatter_*.png not found on the classpath");
            return;
        }
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(icons.get(icons.size() - 1)); // macOS Dock
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // not available on this platform
        }
        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e.getID() == WindowEvent.WINDOW_OPENED && e.getSource() instanceof Frame) {
                    apply((Window) e.getSource());
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
        } catch (SecurityException ignored) {
            // windows then need apply() individually
        }
    }
}
