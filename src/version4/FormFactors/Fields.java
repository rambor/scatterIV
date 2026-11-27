package version4.FormFactors;

import javax.swing.*;
import java.awt.*;

/** small helpers for the parameter forms: numeric fields, validation and a two-column GridBag form */
final class Fields {

    private Fields() {}

    private static final String WRAP_BODY = "lnp.wrapBody", WRAP_FITTED = "lnp.wrapFitted";

    /**
     * An explanatory label that re-wraps to the width it is actually given.
     *
     * <p>A pixel width in the style attribute is the usual way to make a JLabel wrap at all, but it fixes the
     * label's preferred width as well, so the panel holding it can never be narrower than whatever number was
     * typed. At 880 or 1000 px the sentence runs off the side of a window that is otherwise a perfectly
     * comfortable size, and the only way to read the end of it is to drag the frame wider. Rewriting that
     * number whenever the container is resized keeps the wrapping and removes the floor.
     *
     * @param tracks the container whose width the text should follow
     * @param body   the label's HTML, without the surrounding html and div tags
     */
    static JLabel wrappingLabel(JComponent tracks, String body) {
        JLabel label = new Wrapping(tracks);
        setWrappingText(label, tracks, body);
        return label;
    }

    /**
     * A label that wraps to its container without demanding more width than the container has.
     *
     * <p>Setting the CSS width makes the text wrap, but Swing then reports a preferred width about 1.3 times
     * that width anyway — measured at 1127 px for a declared 867, and in the same ratio at every window size,
     * so it is not one long unbreakable word. In a BoxLayout column that surplus demand becomes the tab's
     * minimum width, the panel is laid out wider than the window, and the ends of the sentences cannot be read
     * without dragging the frame out. Eight tabs did this.</p>
     *
     * <p>The text has already been wrapped to the CSS width, so the extra demand buys nothing and is refused.
     * Refusing it is safe in a way that tracking the container is not: the height still comes from the HTML
     * engine, measured at a width slightly narrower than the label will be given, so the label is at worst a
     * line too tall and never too short. And because the width returned is only ever the container's own, it
     * cannot feed back into the container's width the way a label that grows to match its parent does inside a
     * GridBagLayout — which is the other way this has gone wrong here before.</p>
     *
     * <p>Use this for any label that {@link #setWrappingText} is called on. A plain JLabel passed to that
     * method wraps but keeps the surplus demand, which is the whole defect.</p>
     */
    static final class Wrapping extends JLabel {
        private final JComponent tracks;

        Wrapping(JComponent tracks) { this.tracks = tracks; }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            int avail = tracks.getWidth();
            return avail > 0 && d.width > avail ? new Dimension(avail, d.height) : d;
        }

        /** a paragraph must never be what stops a panel being made narrower */
        @Override public Dimension getMinimumSize() {
            Dimension d = getPreferredSize();
            return new Dimension(Math.min(d.width, 180), d.height);
        }
    }

    /** the same, for a label whose text changes: it re-wraps both on resize and on each new message */
    static void setWrappingText(JLabel label, JComponent tracks, String body) {
        label.putClientProperty(WRAP_BODY, body);
        if (label.getClientProperty(WRAP_FITTED) == null) {
            label.putClientProperty(WRAP_FITTED, Boolean.TRUE);
            tracks.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    Object b = label.getClientProperty(WRAP_BODY);
                    if (b != null) label.setText(wrapped(b.toString(), tracks));
                }
            });
        }
        label.setText(wrapped(body, tracks));
    }

    /**
     * Wraps a body of HTML to the width the container currently has. The floor keeps the text from collapsing
     * into a column one word wide while the frame is being dragged narrow, and the margin leaves room for the
     * container's own insets; before the first layout the width is zero, so the floor is what is used then.
     */
    static String wrapped(String body, JComponent tracks) {
        int w = Math.max(240, tracks.getWidth() - 36);
        return "<html><div style='width:" + w + "px'>" + body + "</div></html>";
    }

    static JTextField num(String text) {
        JTextField f = new JTextField(text, 5);
        f.setHorizontalAlignment(JTextField.RIGHT);
        return f;
    }

    static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e7) return String.valueOf((long) v);
        return String.format("%.4g", v);
    }

    static double num(JTextField f, String name) {
        String t = f.getText().trim();
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            f.requestFocusInWindow();
            throw new IllegalArgumentException(name + ": '" + t + "' is not a number");
        }
    }

    static double pos(JTextField f, String name) {
        double v = num(f, name);
        if (!(v > 0)) throw new IllegalArgumentException(name + " must be > 0");
        return v;
    }

    static double nonNeg(JTextField f, String name) {
        double v = num(f, name);
        if (v < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return v;
    }

    static int posInt(JTextField f, String name) {
        double v = pos(f, name);
        if (v != Math.rint(v)) throw new IllegalArgumentException(name + " must be a whole number");
        return (int) v;
    }

    static void order(double lo, double hi, String name) {
        if (!(hi > lo)) throw new IllegalArgumentException(name + ": max must be larger than min");
    }

    static JPanel pair(JComponent a, JComponent b) {
        return flow(a, new JLabel("-"), b);
    }

    static JPanel flow(Component... cs) {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 0));
        for (Component c : cs) p.add(c);
        return p;
    }

    /**
     * A flow layout that asks for the height it will actually need once it has wrapped.
     *
     * <p>{@link FlowLayout} wraps its contents onto further lines when the container is too narrow, but
     * reports a preferred height of a single line regardless. Inside anything that honours a preferred
     * height — a BoxLayout column, a BorderLayout edge — the wrapped lines are then simply cut off. The
     * controls exist, report themselves visible, and cannot be seen or pressed. That has hidden the Edit
     * and Library buttons on the lipid row, the button that starts the cargo analysis, and a row of class
     * checkboxes, each time only on windows below some width nobody tested at.
     *
     * <p>This computes the height from the width it is being given, so the row grows downwards instead of
     * losing its tail. The approach is the widely used one: lay the children out in rows against the
     * target width and total the row heights.
     */
    static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) { super(align, hgap, vgap); }

        @Override public Dimension preferredLayoutSize(Container target) { return wrapped(target, true); }
        @Override public Dimension minimumLayoutSize(Container target) {
            Dimension d = wrapped(target, false);
            d.width -= getHgap() + 1;
            return d;
        }

        private Dimension wrapped(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int target_ = target.getWidth();
                // before the first layout there is no width to wrap against; fall back to one line
                if (target_ == 0) target_ = Integer.MAX_VALUE;
                int hgap = getHgap(), vgap = getVgap();
                Insets in = target.getInsets();
                int max = target_ - (in.left + in.right + hgap * 2);
                Dimension out = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (int i = 0; i < target.getComponentCount(); i++) {
                    Component m = target.getComponent(i);
                    if (!m.isVisible()) continue;
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                    if (rowW + d.width > max && rowW > 0) {
                        out.width = Math.max(out.width, rowW);
                        out.height += rowH + vgap;
                        rowW = 0; rowH = 0;
                    }
                    if (rowW != 0) rowW += hgap;
                    rowW += d.width;
                    rowH = Math.max(rowH, d.height);
                }
                out.width = Math.max(out.width, rowW);
                out.height += rowH;
                out.width += in.left + in.right + hgap * 2;
                out.height += in.top + in.bottom + vgap * 2;
                return out;
            }
        }
    }

    /** label / component rows in a GridBagLayout */
    static final class Form {
        /**
         * Widest a combo box may ask to be in a form row. The settings column is narrow, so a combo sized to its
         * longest entry would run past the edge of the panel and take its arrow with it, which is exactly what
         * makes a drop-down stop looking like one. Entries longer than this are clipped in the closed box and read
         * in full in the list and the tooltip.
         */
        private static final int MAX_COMBO = 175;

        private final JPanel panel;
        private int row = 0;

        Form() { this(new JPanel(new GridBagLayout())); }

        Form(JPanel p) {
            panel = p;
            if (!(p.getLayout() instanceof GridBagLayout)) p.setLayout(new GridBagLayout());
        }

        void row(String label, JComponent comp, String tip) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.insets = new Insets(2, 4, 2, 4);
            c.anchor = GridBagConstraints.LINE_START;
            c.gridx = 0;
            JLabel l = new JLabel(label);
            panel.add(l, c);
            // The control keeps its natural width and sits against the label; the slack goes to a filler column
            // on the right. Stretched across the whole panel a combo box or a text field reads as plain text, and
            // the user cannot see what is editable.
            c.gridx = 1;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            capWidth(comp);
            panel.add(comp, c);
            GridBagConstraints filler = new GridBagConstraints();
            filler.gridx = 2;
            filler.gridy = c.gridy;
            filler.weightx = 1;
            filler.fill = GridBagConstraints.HORIZONTAL;
            panel.add(Box.createHorizontalGlue(), filler);
            if (tip != null) {
                l.setToolTipText(tip);
                comp.setToolTipText(tip);
                for (Component k : comp.getComponents()) if (k instanceof JComponent) ((JComponent) k).setToolTipText(tip);
            }
        }

        /**
         * Keeps a combo box from setting the width of the whole form. Left to itself one sizes to its longest
         * entry, and a list of formulation names runs far wider than the labels beside it, which pushes the arrow
         * out of sight and makes the row look like a line of text. The full entry stays readable in the list and
         * in the tooltip.
         */
        private static void capWidth(JComponent comp) {
            if (comp instanceof JComboBox) {
                Dimension d = comp.getPreferredSize();
                if (d.width > MAX_COMBO) comp.setPreferredSize(new Dimension(MAX_COMBO, d.height));
                return;
            }
            for (Component k : comp.getComponents())       // a control the caller wrapped in a row of its own
                if (k instanceof JComponent) capWidth((JComponent) k);
        }

        void full(JComponent comp) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.gridx = 0;
            c.gridwidth = 3;        // spans the label, the control and the filler column
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(2, 2, 2, 2);
            panel.add(comp, c);
        }

        void note(String text) {
            JLabel l = new JLabel("<html><div style='width:260px'>" + text + "</div></html>");
            l.setForeground(new Color(0x6e7781));
            l.setFont(l.getFont().deriveFont(l.getFont().getSize2D() - 1f));
            full(l);
        }

        JPanel panel() {
            // push rows to the top
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.weighty = 1;
            panel.add(Box.createVerticalGlue(), c);
            return panel;
        }
    }
}
