package version4;

import javax.swing.*;

/**
 * Thread-safe updates of common Swing widgets.
 *
 * Swing components may only be touched on the Event Dispatch Thread. Background code (SwingWorker.doInBackground,
 * plain threads) calls these instead of e.g. {@code status.setText(...)}: on the EDT the change happens at once,
 * from any other thread it is queued onto the EDT. Null widgets are ignored.
 */
public final class Ui {

    private Ui() {}

    /** Run on the EDT: immediately if already there, otherwise later. */
    public static void run(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    public static void setText(JLabel label, String text) {
        if (label != null) run(() -> label.setText(text));
    }

    public static void setValue(JProgressBar bar, int value) {
        if (bar != null) run(() -> bar.setValue(value));
    }

    public static void setMaximum(JProgressBar bar, int max) {
        if (bar != null) run(() -> bar.setMaximum(max));
    }

    public static void setIndeterminate(JProgressBar bar, boolean on) {
        if (bar != null) run(() -> bar.setIndeterminate(on));
    }

    public static void setStringPainted(JProgressBar bar, boolean on) {
        if (bar != null) run(() -> bar.setStringPainted(on));
    }

    public static void setVisible(JComponent component, boolean visible) {
        if (component != null) run(() -> component.setVisible(visible));
    }

    /** Stop and clear a progress bar. */
    public static void resetProgress(JProgressBar bar) {
        if (bar != null) run(() -> {
            bar.setIndeterminate(false);
            bar.setStringPainted(false);
            bar.setValue(0);
        });
    }
}
