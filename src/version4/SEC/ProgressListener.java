package version4.SEC;

/**
 * UI-neutral progress reporting for long-running SEC work.
 *
 * The SEC engine classes (SecProcessor, SecFileWriter, SecFileEditor) only talk to this interface,
 * never to Swing or JavaFX widgets. A Swing front end wraps a JLabel/JProgressBar with
 * {@link SwingProgressListener}; a JavaFX front end can forward the same calls to
 * {@code Task.updateMessage}/{@code Task.updateProgress}.
 *
 * Implementations must be safe to call from a background thread.
 */
public interface ProgressListener {

    /** A short human-readable status message. */
    default void message(String message) {}

    /** Determinate progress: {@code done} out of {@code total} steps. */
    default void progress(int done, int total) {}

    /** Switch between indeterminate (busy) and determinate progress. */
    default void indeterminate(boolean on) {}

    /** Work finished (successfully or not); reset any progress display. */
    default void reset() {}

    ProgressListener NONE = new ProgressListener() {};

    static ProgressListener orNone(ProgressListener listener) {
        return listener == null ? NONE : listener;
    }
}
