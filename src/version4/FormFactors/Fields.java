package version4.FormFactors;

import javax.swing.*;
import java.awt.*;

/** small helpers for the parameter forms: numeric fields, validation and a two-column GridBag form */
final class Fields {

    private Fields() {}

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
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (Component c : cs) p.add(c);
        return p;
    }

    /** label / component rows in a GridBagLayout */
    static final class Form {
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
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(comp, c);
            if (tip != null) {
                l.setToolTipText(tip);
                comp.setToolTipText(tip);
                for (Component k : comp.getComponents()) if (k instanceof JComponent) ((JComponent) k).setToolTipText(tip);
            }
        }

        void full(JComponent comp) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.gridx = 0;
            c.gridwidth = 2;
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
