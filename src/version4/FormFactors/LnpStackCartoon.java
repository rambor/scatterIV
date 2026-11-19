package version4.FormFactors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A to-scale picture of what a lamellar fit means: stacked bilayers with the measured repeat distance, the
 * bilayer itself and the water between the bilayers marked on the drawing. Two phases are drawn side by side,
 * which is the point of it -- two repeat distances from one sample are hard to picture from a pair of numbers,
 * and a reader needs to see that both are stacks of the same bilayer holding different amounts of water.
 *
 * <p>Everything drawn comes from the fit: the repeat distance is the lattice parameter, the bilayer is twice the
 * lipid length the user entered, and the water layer is the difference. Where the lipid length is too large for
 * the repeat the drawing says so rather than showing a negative water gap.
 */
final class LnpStackCartoon extends JComponent {

    /** one lamellar phase to draw */
    static final class Phase {
        final String name;
        final double d;          // repeat distance, Angstrom
        final double share;      // fraction of the Bragg area, NaN when only one phase
        final double domain;     // ordered domain, Angstrom; NaN when not fitted
        /** head-to-head thickness from the order intensities; NaN = fall back on twice the lipid length */
        final double bilayer;
        Phase(String name, double d, double share, double domain, double bilayer) {
            this.name = name;
            this.d = d;
            this.share = share;
            this.domain = domain;
            this.bilayer = bilayer;
        }
    }

    private final List<Phase> phases = new ArrayList<>();
    private double lipidLength = 17;
    private String[] empty = {"Fit a lamellar phase and the stacking is drawn here to scale,",
            "with the repeat distance, the bilayer and the water between them."};

    LnpStackCartoon() {
        setPreferredSize(new Dimension(560, 360));
        setOpaque(true);
    }

    /** what to show when there is nothing to draw, so the panel explains itself rather than sitting blank */
    void setEmptyMessage(String... lines) {
        empty = lines;
        repaint();
    }

    void set(List<Phase> ph, double lipidLength) {
        phases.clear();
        if (ph != null) phases.addAll(ph);
        this.lipidLength = lipidLength;
        setToolTipText(phases.size() > 1
                ? "Two lamellar repeats were measured: the sample holds more than one stacking, drawn here to scale"
                : "The lamellar repeat measured, drawn to scale");
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        if (phases.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(LnpCartoon.MUTED);
            int y = 24;
            for (String line : empty)
                g.drawString(line, 12, y += 16);
            g.dispose();
            return;
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(LnpCartoon.INK);
        String title = phases.size() > 1
                ? "Two coexisting lamellar stackings, to scale"
                : "The lamellar stacking, to scale";
        g.drawString(title, 12, 18);

        if (phases.size() > 1) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(LnpCartoon.MUTED);
            g.drawString("One bilayer, two separations: the sample is not a single phase.", 12, 34);
        }

        int top = 62, bottom = h - 74;
        int columns = phases.size();
        int colWidth = (w - 24) / columns;
        // one scale for every column, so the two repeats can be compared by eye
        double largest = 0;
        for (Phase p : phases) largest = Math.max(largest, p.d);
        double scale = (bottom - top) / (3.2 * largest);        // about three repeats tall

        for (int i = 0; i < columns; i++) {
            drawColumn(g, phases.get(i), 12 + i * colWidth, colWidth, top, bottom, scale);
        }
        drawKey(g, 12, h - 10);
        g.dispose();
    }

    private void drawColumn(Graphics2D g, Phase p, int x0, int width, int top, int bottom, double scale) {
        int stackLeft = x0 + 12, stackWidth = Math.min(width - 150, 150);
        if (stackWidth < 60) stackWidth = Math.max(40, width - 90);
        double thickness = !Double.isNaN(p.bilayer) && p.bilayer > 0 ? p.bilayer : 2 * lipidLength;
        double dPx = p.d * scale;
        double bilayerPx = thickness * scale;
        boolean fits = thickness < p.d;
        double waterPx = Math.max(dPx - bilayerPx, 0);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(LnpCartoon.INK);
        g.drawString(p.name, x0, top - 14);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(LnpCartoon.MUTED);
        StringBuilder sub = new StringBuilder(String.format("d = %.2f nm", p.d / 10));
        if (!Double.isNaN(p.share)) sub.append(String.format("   ·   %.0f %% of the Bragg area", 100 * p.share));
        g.drawString(sub.toString(), x0, top - 1);

        // the stack: water, then bilayer, repeated from the bottom up
        Shape clip = g.getClip();
        g.clipRect(stackLeft - 1, top, stackWidth + 2, bottom - top);
        double y = bottom;
        int guard = 0;
        double firstBilayerTop = Double.NaN, firstBilayerBottom = Double.NaN, firstRepeatTop = Double.NaN;
        while (y > top - dPx && guard++ < 60 && dPx > 2) {
            double waterTop = y - waterPx;
            g.setColor(LnpCartoon.WATER);
            g.fill(new Rectangle2D.Double(stackLeft, waterTop, stackWidth, waterPx));
            double bilayerBottom = waterTop, bilayerTop = waterTop - bilayerPx;
            drawBilayer(g, stackLeft, stackWidth, bilayerTop, bilayerPx);
            if (Double.isNaN(firstBilayerTop) && bilayerTop > top + 12) {
                firstBilayerTop = bilayerTop;
                firstBilayerBottom = bilayerBottom;
                firstRepeatTop = bilayerTop - waterPx - 0;   // the next water layer begins the following repeat
            }
            y -= dPx;
        }
        g.setClip(clip);
        g.setColor(new Color(0xd0d7de));
        g.draw(new Rectangle2D.Double(stackLeft, top, stackWidth, bottom - top));

        // dimension arrows to the right of the stack
        int ax = stackLeft + stackWidth + 10;
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        if (!Double.isNaN(firstBilayerTop)) {
            double repeatTop = firstBilayerTop - waterPx;
            dimension(g, ax, repeatTop, firstBilayerBottom, String.format("d = %.2f nm", p.d / 10), LnpCartoon.INK);
            dimension(g, ax + 86, firstBilayerTop, firstBilayerBottom,
                    fits ? String.format("bilayer %.2f nm", thickness / 10) : "bilayer", new Color(0xb3701a));
            if (waterPx > 3)
                dimension(g, ax + 86, firstBilayerTop - waterPx, firstBilayerTop,
                        String.format("water %.2f nm", (p.d - thickness) / 10), new Color(0x2f6ea8));
        }

        // the numbers underneath
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(LnpCartoon.MUTED);
        int ty = bottom + 14;
        if (!fits) {
            g.setColor(new Color(0xb42318));
            g.drawString(String.format("A %.1f Å bilayer does not fit a %.1f Å repeat.", thickness, p.d), x0, ty);
            g.drawString("Check the composition before reading a water layer.", x0, ty + 13);
        } else {
            g.setColor(LnpCartoon.MUTED);
            if (!Double.isNaN(p.domain))
                g.drawString(String.format("ordered over %.0f Å ≈ %.0f repeats", p.domain, p.domain / p.d), x0, ty);
            g.drawString(!Double.isNaN(p.bilayer) && p.bilayer > 0
                            ? "bilayer from the order intensities" : "bilayer from the lipid length entered",
                    x0, ty + (Double.isNaN(p.domain) ? 0 : 13));
        }
    }

    /** headgroups out, chains in: the bilayer of the reference drawings, at the thickness the lipid length gives */
    private void drawBilayer(Graphics2D g, int x, int width, double topY, double thickness) {
        if (thickness <= 0) return;
        double headR = Math.max(1.5, Math.min(4.5, thickness * 0.16));
        g.setColor(LnpCartoon.TAILS);
        g.fill(new Rectangle2D.Double(x, topY, width, thickness));
        // chains, drawn as short strokes meeting in the middle
        g.setColor(new Color(0xd79a33));
        double mid = topY + thickness / 2;
        for (double cx = x + 3; cx < x + width - 2; cx += 5) {
            g.draw(new Line2D.Double(cx, topY + headR * 1.6, cx, mid - 1));
            g.draw(new Line2D.Double(cx, mid + 1, cx, topY + thickness - headR * 1.6));
        }
        g.setColor(LnpCartoon.HEAD);
        for (double cx = x + headR; cx < x + width; cx += headR * 2) {
            g.fill(new Ellipse2D.Double(cx - headR, topY, headR * 2, headR * 2));
            g.fill(new Ellipse2D.Double(cx - headR, topY + thickness - headR * 2, headR * 2, headR * 2));
        }
    }

    /** a vertical dimension arrow with its label */
    private void dimension(Graphics2D g, int x, double y1, double y2, String label, Color colour) {
        if (!(y2 > y1)) return;
        g.setColor(colour);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(x, y1, x, y2));
        arrow(g, x, y1, 1);
        arrow(g, x, y2, -1);
        g.drawString(label, x + 5, (float) ((y1 + y2) / 2 + 4));
    }

    private void arrow(Graphics2D g, double x, double y, int dir) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x - 3, y + 5 * dir);
        p.lineTo(x + 3, y + 5 * dir);
        p.closePath();
        g.fill(p);
    }

    private void drawKey(Graphics2D g, int x, int y) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        int cx = x;
        cx = keyItem(g, cx, y, LnpCartoon.HEAD, "headgroups");
        cx = keyItem(g, cx, y, LnpCartoon.TAILS, "chains (lipophilic)");
        keyItem(g, cx, y, LnpCartoon.WATER, "interlamellar water");
    }

    private int keyItem(Graphics2D g, int x, int y, Color c, String label) {
        g.setColor(c);
        g.fill(new Rectangle2D.Double(x, y - 8, 11, 11));
        g.setColor(new Color(0xd0d7de));
        g.draw(new Rectangle2D.Double(x, y - 8, 11, 11));
        g.setColor(LnpCartoon.MUTED);
        g.drawString(label, x + 15, y + 1);
        return x + 20 + g.getFontMetrics().stringWidth(label) + 14;
    }
}
