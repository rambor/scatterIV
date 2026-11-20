package version4.FormFactors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/**
 * A cross-section of one way the cargo could sit inside the particle.
 *
 * <p>The arrangements are easy to name and hard to picture, and the difference between cargo condensed into
 * a ball and cargo threaded through the core at a fixed spacing is the whole question the Cargo tab asks.
 * A row of these next to the numbers lets a reader see what is being compared before reading what the
 * probabilities say about it.
 *
 * <p>Everything is drawn to the scale of the fitted envelope, so the pictures in a row are comparable with
 * one another and with the particle the fit actually found. The cargo is drawn from the declared length:
 * the condensed body is the size that much nucleic acid would occupy, and the strands of the quasi-periodic
 * case are drawn at the spacing the fit reports, not at a decorative one.
 */
final class LnpCargoCartoon extends JComponent {

    private final LnpArrangement.Cargo cargo;
    private double r0 = 218, t1 = 16, t2 = 3, t3 = 40;
    /** radius of the cargo if condensed, and the strand spacing if ordered, both in Angstrom */
    private double cargoRadius = 65, spacing = 49.8;
    private double posterior = Double.NaN;
    private boolean winner, excluded;

    LnpCargoCartoon(LnpArrangement.Cargo cargo) {
        this.cargo = cargo;
        setPreferredSize(new Dimension(150, 172));
        setToolTipText("<html><div style='width:240px'><b>" + cargo.label + "</b><br>"
                + cargo.description + "</div></html>");
    }

    void setEnvelope(double r0, double t1, double t2, double t3) {
        this.r0 = Math.max(r0, 20); this.t1 = t1; this.t2 = t2; this.t3 = t3;
    }

    void setCargo(double cargoRadius, double spacing) {
        this.cargoRadius = Math.max(cargoRadius, 4);
        if (spacing > 1) this.spacing = spacing;
    }

    void setVerdict(double posterior, boolean winner, boolean excluded) {
        this.posterior = posterior; this.winner = winner; this.excluded = excluded;
        repaint();
    }

    private static final Color CORE = new Color(0xf6efe2), SHELL = new Color(0xdfe7ef),
            CORONA = new Color(0xeef2f7), EDGE = new Color(0xc9d2dc), RNA = new Color(0xb3261e),
            WIN = new Color(0x1b7f5e), GREY = new Color(0x8b8b8b);

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int capt = 34;                               // room for the caption beneath
        double outer = r0 + t1 + t2 + t3;
        // the unencapsulated case draws cargo outside the particle, so the particle has to shrink to
        // leave room for it; otherwise the coil runs off the edge of its own panel
        double room = cargo == LnpArrangement.Cargo.FREE ? 0.33 : 0.46;
        double scale = (Math.min(w, h - capt) * room) / outer;
        double cx = w / 2.0, cy = (h - capt) / 2.0 + 4;

        if (winner) {
            g.setColor(new Color(0x1b7f5e));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 10, 10);
        }
        Composite was = g.getComposite();
        if (excluded) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.32f));

        disc(g, cx, cy, outer * scale, CORONA, EDGE);
        disc(g, cx, cy, (r0 + t1 + t2) * scale, SHELL, null);
        disc(g, cx, cy, r0 * scale, CORE, EDGE);

        g.setColor(RNA);
        switch (cargo) {
            case COMPACT:     drawCompact(g, cx, cy, scale); break;
            case SHELL_BOUND: drawOnShell(g, cx, cy, scale); break;
            case QUASI_PERIODIC: drawOrdered(g, cx, cy, scale); break;
            case FREE:        drawFree(g, cx, cy, scale, outer); break;
            default: break;                           // NONE draws no cargo, which is the point
        }
        g.setComposite(was);

        // caption
        g.setColor(excluded ? GREY : getForeground());
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        centred(g, cargo.label, cx, h - 20);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g.setColor(excluded ? GREY : (winner ? WIN : getForeground()));
        String tag = excluded ? "excluded"
                : Double.isNaN(posterior) ? ""
                : posterior > 0.999 ? "P > 0.999" : String.format("P = %.3f", posterior);
        centred(g, tag, cx, h - 6);
        g.dispose();
    }

    private void centred(Graphics2D g, String s, double cx, double y) {
        if (s == null || s.isEmpty()) return;
        int wid = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (float) (cx - wid / 2.0), (float) y);
    }

    private static void disc(Graphics2D g, double cx, double cy, double r, Color fill, Color edge) {
        Ellipse2D e = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(fill);
        g.fill(e);
        if (edge != null) { g.setColor(edge); g.setStroke(new BasicStroke(1f)); g.draw(e); }
    }

    /** cargo collapsed into compact bodies, drawn at the size the declared length would occupy */
    private void drawCompact(Graphics2D g, double cx, double cy, double scale) {
        double rr = cargoRadius * scale, off = Math.min(r0 * scale - rr, rr * 1.15);
        if (off < rr * 0.3) {
            g.fill(new Ellipse2D.Double(cx - rr, cy - rr, 2 * rr, 2 * rr));
            return;
        }
        g.fill(new Ellipse2D.Double(cx - off - rr, cy - rr, 2 * rr, 2 * rr));
        g.fill(new Ellipse2D.Double(cx + off - rr, cy - rr, 2 * rr, 2 * rr));
    }

    /** cargo lying against the inside of the monolayer */
    private void drawOnShell(Graphics2D g, double cx, double cy, double scale) {
        double r = (r0 - 9) * scale;
        for (int i = 0; i < 46; i++) {
            double a = 2 * Math.PI * i / 46.0;
            double x = cx + r * Math.cos(a), y = cy + r * Math.sin(a);
            g.fill(new Ellipse2D.Double(x - 1.9, y - 1.9, 3.8, 3.8));
        }
    }

    /**
     * The strands seen end-on, as a hexagonal array at the spacing the fit reports.
     *
     * <p>Drawn down the axis rather than across it, because that is the view that shows what the peak
     * actually measures: a single nearest-neighbour distance repeated in every direction. The array is
     * broken into two domains at a slight angle to each other and the positions carry a little disorder,
     * which is the honest picture — the peak is broad, and a broad peak means order that persists over a
     * few neighbours rather than across the whole particle.
     *
     * <p>It is worth not overselling the word. This is a liquid-crystalline packing with a short
     * correlation length, periodic within a domain. It is not a quasicrystal in Shechtman's sense: those
     * are aperiodic, ordered without any repeating cell at all, and show symmetries a lattice cannot have.
     */
    private void drawOrdered(Graphics2D g, double cx, double cy, double scale) {
        double d = spacing * scale, R = r0 * scale;
        if (d < 3) d = 3;
        double rad = Math.max(1.8, Math.min(d * 0.30, 4.6));
        double rowGap = d * Math.sqrt(3) / 2;
        int rows = (int) Math.ceil(2 * R / rowGap) + 2, cols = (int) Math.ceil(2 * R / d) + 2;
        for (int dom = 0; dom < 2; dom++) {
            double th = dom == 0 ? -0.09 : 0.34;              // the two domains, slightly misaligned
            double ct = Math.cos(th), st = Math.sin(th);
            for (int i = -rows; i <= rows; i++) {
                for (int j = -cols; j <= cols; j++) {
                    double x = j * d + (Math.abs(i % 2) == 1 ? d / 2 : 0);
                    double y = i * rowGap;
                    // a little positional disorder, deterministic so the picture does not shimmer
                    int hash = (i * 73856093) ^ (j * 19349663) ^ (dom * 83492791);
                    x += ((hash & 0xff) / 255.0 - 0.5) * d * 0.07;
                    y += (((hash >> 8) & 0xff) / 255.0 - 0.5) * d * 0.07;
                    double px = x * ct - y * st, py = x * st + y * ct;
                    if (px * px + py * py > (R - rad) * (R - rad)) continue;
                    boolean upper = py < px * 0.42 - R * 0.06;   // the domain boundary
                    if (upper != (dom == 0)) continue;
                    g.fill(new Ellipse2D.Double(cx + px - rad, cy + py - rad, 2 * rad, 2 * rad));
                }
            }
        }
    }

    /** cargo loose in the solution outside, which is what "not encapsulated" looks like */
    private void drawFree(Graphics2D g, double cx, double cy, double scale, double outer) {
        double R = outer * scale;
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        double a = -0.7, rad = R + 9;
        p.moveTo(cx + rad * Math.cos(a), cy + rad * Math.sin(a));
        for (int i = 1; i <= 58; i++) {
            double t = i / 58.0;
            double ang = a + t * 4.2;
            double rr = rad + 6 * Math.sin(t * 15) + 4 * Math.sin(t * 6.3);
            p.lineTo(cx + rr * Math.cos(ang), cy + rr * Math.sin(ang));
        }
        g.draw(p);
    }
}
