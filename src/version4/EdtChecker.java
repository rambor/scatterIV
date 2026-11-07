package version4;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Development aid: reports Swing components being changed off the Event Dispatch Thread.
 *
 * Start Scatter with {@code -Dscatter.checkEdt=true}. Each offending call site is printed once to the console
 * with the stack trace that led to it, e.g. {@code EDT violation #3 :: JLabel changed on thread "Thread-7"}.
 *
 * It works by replacing Swing's RepaintManager, which every component goes through when it is changed, so it
 * catches most (not all) violations. Leave it off in normal use.
 */
public final class EdtChecker extends RepaintManager {

    private final Set<String> reported = Collections.synchronizedSet(new HashSet<>());
    private int count;

    private EdtChecker() {}

    /** Install if the system property scatter.checkEdt is true. */
    public static void installIfRequested() {
        if (Boolean.getBoolean("scatter.checkEdt")) {
            RepaintManager.setCurrentManager(new EdtChecker());
            System.out.println("EdtChecker :: reporting Swing changes made off the Event Dispatch Thread");
        }
    }

    @Override
    public synchronized void addInvalidComponent(JComponent component) {
        check(component);
        super.addInvalidComponent(component);
    }

    @Override
    public void addDirtyRegion(JComponent component, int x, int y, int w, int h) {
        check(component);
        super.addDirtyRegion(component, x, y, w, h);
    }

    private void check(JComponent component) {
        if (SwingUtilities.isEventDispatchThread()) return;

        StackTraceElement[] stack = new Throwable().getStackTrace();
        // first frame outside Swing/AWT and this class identifies the call site
        String site = "unknown";
        for (StackTraceElement e : stack) {
            String c = e.getClassName();
            if (!c.startsWith("javax.swing.") && !c.startsWith("java.awt.") && !c.startsWith("sun.")
                    && !c.equals(EdtChecker.class.getName())) {
                site = e.toString();
                break;
            }
        }
        if (!reported.add(site)) return;

        StringBuilder sb = new StringBuilder();
        synchronized (this) { count++; sb.append("EDT violation #").append(count); }
        sb.append(" :: ").append(component.getClass().getSimpleName())
          .append(" changed on thread \"").append(Thread.currentThread().getName()).append("\" at ").append(site);
        for (StackTraceElement e : stack) {
            String c = e.getClassName();
            if (c.equals(EdtChecker.class.getName())) continue;
            sb.append("\n        at ").append(e);
        }
        System.err.println(sb);
    }
}
