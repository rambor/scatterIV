package version4.FormFactors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/**
 * Small sketch of a lipid mesophase at its fitted lattice parameter, drawn to scale, with the repeat distance that
 * the Bragg reflections measure marked on it. These are the insets of Fig. 4A of Caselli, Conti, De Santis &amp;
 * Berti, Adv. Colloid Interface Sci. 327, 103156 (2024): the phase sketch next to the indexed pattern.
 */
final class LnpPhaseInset extends JComponent {

    private LnpLattice.Lattice lattice;
    private double a = 100, lipidLength = 17;

    LnpPhaseInset() {
        setPreferredSize(new Dimension(230, 210));
        setOpaque(true);
    }

    void set(LnpLattice.Lattice l, double a, double lipidLength) {
        this.lattice = l;
        this.a = a;
        this.lipidLength = lipidLength;
        repaint();
    }

    LnpLattice.Lattice lattice() { return lattice; }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (lattice == null || !(a > 0)) { g.dispose(); return; }

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(LnpCartoon.INK);
        g.drawString(lattice.symmetry.split(",")[0], 8, 15);

        int top = 22, bottom = h - 30;
        int side = Math.min(w - 16, bottom - top);
        double x0 = (w - side) / 2.0, y0 = top + (bottom - top - side) / 2.0;
        Shape box = new Rectangle2D.Double(x0, y0, side, side);
        Shape clip = g.getClip();
        g.clip(box);
        // the sketch shows about two repeat distances across the box
        double scale = side / (2.2 * a);
        if (lattice.lamellar) drawLamellar(g, x0, y0, side, scale);
        else if (lattice.hexagonal) drawHexagonal(g, x0, y0, side, scale);
        else drawCubic(g, x0, y0, side, scale);
        g.setClip(clip);
        g.setColor(new Color(0xd0d7de));
        g.setStroke(new BasicStroke(1f));
        g.draw(box);

        // the measured repeat, drawn as an arrow with its label
        double len = (lattice.lamellar ? a : lattice.hexagonal ? a : a) * scale;
        double ay = bottom + 12;
        g.setColor(new Color(0xdc2626));
        g.setStroke(new BasicStroke(1.6f));
        double ax0 = x0 + side / 2.0 - len / 2, ax1 = ax0 + len;
        g.draw(new Line2D.Double(ax0, ay, ax1, ay));
        g.draw(new Line2D.Double(ax0, ay - 4, ax0, ay + 4));
        g.draw(new Line2D.Double(ax1, ay - 4, ax1, ay + 4));
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        String lab = String.format("a = %.1f nm", a / 10);
        if (lattice.lamellar) lab = String.format("d = %.1f nm", a / 10);
        int tw = g.getFontMetrics().stringWidth(lab);
        g.drawString(lab, (float) (x0 + side / 2.0 - tw / 2.0), (float) ay + 15);
        g.dispose();
    }

    /** stacked bilayers: the repeat is one bilayer plus one water layer */
    private void drawLamellar(Graphics2D g, double x0, double y0, double side, double scale) {
        double rep = a * scale, chains = Math.min(0.8 * a, 2 * lipidLength) * scale;
        for (double y = y0 - rep; y < y0 + side + rep; y += rep) {
            g.setColor(LnpCartoon.WATER);
            g.fill(new Rectangle2D.Double(x0, y, side, rep - chains));
            g.setColor(LnpCartoon.TAILS);
            g.fill(new Rectangle2D.Double(x0, y + rep - chains, side, chains));
            g.setColor(LnpCartoon.HEAD);
            g.fill(new Rectangle2D.Double(x0, y + rep - chains - 2, side, 2.5));
            g.fill(new Rectangle2D.Double(x0, y + rep - 2.5, side, 2.5));
        }
    }

    /** inverse hexagonal: water cylinders on a hexagonal lattice, seen end-on */
    private void drawHexagonal(Graphics2D g, double x0, double y0, double side, double scale) {
        g.setColor(LnpCartoon.TAILS);
        g.fill(new Rectangle2D.Double(x0, y0, side, side));
        double rw = Math.max(0.15 * a, Math.min(0.45 * a, a / 2 - lipidLength)) * scale;
        double step = a * scale, hstep = step * Math.sqrt(3) / 2;
        int row = 0;
        for (double y = y0 - step; y < y0 + side + step; y += hstep, row++)
            for (double x = x0 - step + (row % 2) * step / 2; x < x0 + side + step; x += step) {
                g.setColor(LnpCartoon.HEAD);
                g.fill(new Ellipse2D.Double(x - rw - 2.5, y - rw - 2.5, 2 * rw + 5, 2 * rw + 5));
                g.setColor(LnpCartoon.WATER);
                g.fill(new Ellipse2D.Double(x - rw, y - rw, 2 * rw, 2 * rw));
            }
        // the (10) spacing is the distance between rows of cylinders
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0));
        for (double y = y0; y < y0 + side; y += hstep) g.draw(new Line2D.Double(x0, y, x0 + side, y));
    }

    /** bicontinuous cubic: two interwoven water channel networks through a lipid bilayer */
    private void drawCubic(Graphics2D g, double x0, double y0, double side, double scale) {
        g.setColor(LnpCartoon.TAILS);
        g.fill(new Rectangle2D.Double(x0, y0, side, side));
        double rw = Math.max(0.06 * a, cubicRadius()) * scale;
        double step = a * scale / 2;
        // two networks, drawn in different shades, joined at alternating nodes
        for (int net = 0; net < 2; net++) {
            g.setColor(net == 0 ? LnpCartoon.WATER : new Color(0x9ecbe8));
            g.setStroke(new BasicStroke((float) Math.max(2, 1.6 * rw), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int i = 0;
            for (double x = x0 - step; x < x0 + side + step; x += step, i++) {
                int j = 0;
                for (double y = y0 - step; y < y0 + side + step; y += step, j++) {
                    if ((i + j) % 2 != net) continue;
                    g.draw(new Line2D.Double(x, y, x + step, y + step));
                    g.draw(new Line2D.Double(x, y + step, x + step, y));
                }
            }
        }
        g.setColor(new Color(0x2f9e72));
        g.setStroke(new BasicStroke(1f));
        for (double x = x0 - step; x < x0 + side + step; x += step)
            for (double y = y0 - step; y < y0 + side + step; y += step)
                g.draw(new Ellipse2D.Double(x - 1.5, y - 1.5, 3, 3));
    }

    private double cubicRadius() {
        double a0 = 1.919, chi = -2;
        if (lattice.symmetry.startsWith("Im3m")) { a0 = 2.345; chi = -4; }
        else if (lattice.symmetry.startsWith("Ia3d")) { a0 = 3.091; chi = -8; }
        return Math.max(0.05 * a, a * Math.sqrt(-a0 / (2 * Math.PI * chi)) - lipidLength);
    }
}
