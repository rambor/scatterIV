package version4.SEC;

import javax.swing.*;

/**
 * Forwards {@link ProgressListener} calls to a Swing status label and progress bar,
 * always on the Event Dispatch Thread. Either widget may be null.
 */
public class SwingProgressListener implements ProgressListener {

    private final JLabel status;
    private final JProgressBar bar;
    private int lastPercent = -1;

    public SwingProgressListener(JLabel status, JProgressBar bar) {
        this.status = status;
        this.bar = bar;
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    @Override
    public void message(String message) {
        if (status != null) onEdt(() -> status.setText(message));
    }

    @Override
    public void progress(int done, int total) {
        if (bar == null || total <= 0) return;
        // throttle: one EDT update per percent is plenty
        int percent = (int) (100L * done / total);
        if (percent == lastPercent && done != total) return;
        lastPercent = percent;
        onEdt(() -> {
            bar.setVisible(true);
            bar.setIndeterminate(false);
            bar.setStringPainted(true);
            bar.setMaximum(total);
            bar.setValue(done);
        });
    }

    @Override
    public void indeterminate(boolean on) {
        if (bar == null) return;
        lastPercent = -1;
        onEdt(() -> {
            bar.setVisible(true);
            bar.setStringPainted(!on && bar.isStringPainted());
            bar.setIndeterminate(on);
        });
    }

    @Override
    public void reset() {
        if (bar == null) return;
        lastPercent = -1;
        onEdt(() -> {
            bar.setIndeterminate(false);
            bar.setStringPainted(false);
            bar.setValue(0);
        });
    }
}
