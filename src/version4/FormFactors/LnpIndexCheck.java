package version4.FormFactors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The linearity test for an indexing, after the inset of Fig. 2C of Caselli, Conti, De Santis &amp; Berti (Adv.
 * Colloid Interface Sci. 327, 103156 (2024)): the index multiplier of each reflection plotted against the measured
 * peak position. For a cubic phase that multiplier is (h<sup>2</sup>+k<sup>2</sup>+l<sup>2</sup>)<sup>1/2</sup>, for a
 * hexagonal one (2/&radic;3)(h<sup>2</sup>+hk+k<sup>2</sup>)<sup>1/2</sup>, and for a lamellar one simply the order h.
 *
 * <p>A correct assignment puts every point on one straight line through the origin, whose slope is the lattice
 * parameter over 2&pi;. That is a stricter test than looking at the sticks: a wrong space group can put several
 * reflections close to real peaks, but it cannot usually do so while keeping them collinear and the intercept at
 * zero. The scatter of the points is quoted as the spread in the lattice parameter each peak implies on its own, so
 * a single outlying reflection &mdash; often a peak from a second phase &mdash; can be seen rather than averaged in.
 */
final class LnpIndexCheck extends JComponent {

    /** one measured peak matched to an allowed reflection */
    static final class Point {
        final String hkl;
        final double q, m, aFromThis;
        Point(String hkl, double q, double m) {
            this.hkl = hkl;
            this.q = q;
            this.m = m;
            this.aFromThis = 2 * Math.PI * m / q;
        }
    }

    /** the fitted line and its quality */
    static final class Fit {
        final List<Point> points = new ArrayList<>();
        double slope;          // m per Angstrom^-1, so a = 2 pi slope
        double a;              // lattice parameter from the slope alone
        double aSpread;        // standard deviation of the per-peak lattice parameters
        double r2 = Double.NaN;
        double worst;          // largest |a_i - a| in Angstrom
        String worstHkl = "";
        /**
         * Allowed reflections lying between the first and last matched peak for which no peak was found. Linearity
         * cannot separate two space groups that allow the same reflections -- the peaks of Im3m are all allowed in
         * Pn3m as well -- so what decides between them is which predicted reflections are absent.
         */
        final List<String> absent = new ArrayList<>();
    }

    private Fit fit;
    private LnpLattice.Lattice lattice;

    LnpIndexCheck() {
        setPreferredSize(new Dimension(230, 200));
        setOpaque(true);
        setToolTipText("The index multiplier of each matched reflection against its measured q. A correct assignment "
                + "is a straight line through the origin whose slope gives the lattice parameter.");
    }

    /**
     * Matches the detected peaks to the allowed reflections of this lattice at this parameter and fits the line.
     *
     * @param tol fractional tolerance for calling a peak and a reflection the same thing
     */
    void set(LnpLattice.Lattice l, double a, List<LnpAssessment.Peak> peaks, double tol) {
        lattice = l;
        fit = null;
        if (l == null || !(a > 0) || peaks == null || peaks.isEmpty()) { repaint(); return; }
        Fit f = new Fit();
        for (int i = 0; i < l.m.length; i++) {
            double qc = 2 * Math.PI * l.m[i] / a;
            LnpAssessment.Peak best = null;
            for (LnpAssessment.Peak p : peaks)
                if (Math.abs(p.q - qc) / qc < tol && (best == null || Math.abs(p.q - qc) < Math.abs(best.q - qc))) best = p;
            if (best != null) f.points.add(new Point(l.hkl[i], best.q, l.m[i]));
        }
        if (f.points.size() < 2) { repaint(); return; }
        // slope through the origin, then the spread of the parameter each peak implies on its own
        double sxy = 0, sxx = 0, mean = 0;
        for (Point p : f.points) { sxy += p.q * p.m; sxx += p.q * p.q; mean += p.aFromThis; }
        f.slope = sxy / sxx;
        f.a = 2 * Math.PI * f.slope;
        mean /= f.points.size();
        double var = 0;
        for (Point p : f.points) var += (p.aFromThis - mean) * (p.aFromThis - mean);
        f.aSpread = f.points.size() > 1 ? Math.sqrt(var / (f.points.size() - 1)) : 0;
        double ssRes = 0, ssTot = 0, mBar = 0;
        for (Point p : f.points) mBar += p.m;
        mBar /= f.points.size();
        for (Point p : f.points) {
            double r = p.m - f.slope * p.q;
            ssRes += r * r;
            ssTot += (p.m - mBar) * (p.m - mBar);
        }
        f.r2 = ssTot > 0 ? 1 - ssRes / ssTot : Double.NaN;
        for (Point p : f.points) {
            double dev = Math.abs(p.aFromThis - f.a);
            if (dev > f.worst) { f.worst = dev; f.worstHkl = p.hkl; }
        }
        double qFirst = Double.POSITIVE_INFINITY, qLast = 0;
        for (Point p : f.points) { qFirst = Math.min(qFirst, p.q); qLast = Math.max(qLast, p.q); }
        for (int i = 0; i < l.m.length; i++) {
            double qc = 2 * Math.PI * l.m[i] / f.a;
            if (qc < qFirst * 0.99 || qc > qLast * 1.01) continue;
            boolean seen = false;
            for (Point p : f.points) if (p.hkl.equals(l.hkl[i])) { seen = true; break; }
            if (!seen) f.absent.add(l.hkl[i]);
        }
        fit = f;
        repaint();
    }

    Fit fit() { return fit; }

    /** The text form, for the lattice-fit report and the saved results. */
    static String describe(Fit f, LnpLattice.Lattice l) {
        if (f == null || f.points.size() < 2) return "  Indexing check: fewer than two reflections matched, so there is no line to fit.\n";
        StringBuilder sb = new StringBuilder();
        sb.append("  INDEXING CHECK (index multiplier against measured q; Fig. 2C inset of Caselli et al. 2024)\n");
        sb.append(String.format("    %-10s %10s %10s %12s%n", "hkl", "q (Å⁻¹)", "multiplier", "a from it (Å)"));
        for (Point p : f.points)
            sb.append(String.format("    %-10s %10.4f %10.4f %12.1f%n", p.hkl, p.q, p.m, p.aFromThis));
        sb.append(String.format("    Slope gives a = %.1f Å; the individual peaks spread by ±%.1f Å (R² = %.5f).%n",
                f.a, f.aSpread, f.r2));
        if (f.points.size() >= 3 && f.aSpread / f.a > 0.02)
            sb.append(String.format("    ! The points are not collinear to within 2%%: %s is the furthest off, by %.1f Å.%n"
                    + "      Either the assignment is wrong or that peak belongs to a second phase.%n", f.worstHkl, f.worst));
        else if (f.points.size() >= 3)
            sb.append("    The points are collinear through the origin, so the assignment is self-consistent.\n");
        else
            sb.append("    Two reflections define a line on their own, so this test says little; look for a third order.\n");
        if (l != null && l.cubic && f.points.size() >= 3)
            sb.append("    For a cubic phase the multiplier is √(h²+k²+l²), so the slope is a/2π.\n");
        if (!f.absent.isEmpty()) {
            sb.append("    ! Allowed but not observed inside the same q range: ").append(String.join(", ", f.absent)).append('\n');
            sb.append("      Linearity cannot separate two space groups that allow the same reflections, so an absent\n"
                    + "      reflection is what argues against this assignment. Check whether a phase without it fits\n"
                    + "      (the whole-pattern fit ranks them), or whether the reflection is too weak to detect here.\n");
        }
        return sb.toString();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(LnpCartoon.INK);
        g.drawString("Indexing check", 8, 14);
        if (fit == null || fit.points.size() < 2) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(LnpCartoon.MUTED);
            g.drawString("needs two matched", 8, 32);
            g.drawString("reflections", 8, 44);
            g.dispose();
            return;
        }
        int left = 34, right = w - 10, top = 22, bottom = h - 30;
        double qmax = 0, mmax = 0;
        for (Point p : fit.points) { qmax = Math.max(qmax, p.q); mmax = Math.max(mmax, p.m); }
        qmax *= 1.15;
        mmax *= 1.15;
        g.setColor(new Color(0xd5d9de));
        g.draw(new Line2D.Double(left, bottom, right, bottom));
        g.draw(new Line2D.Double(left, top, left, bottom));
        // the fitted line through the origin
        double xEnd = right, yEnd = bottom - (fit.slope * qmax / mmax) * (bottom - top);
        if (yEnd < top) {   // clip to the top of the box
            xEnd = left + (bottom - top) / ((fit.slope * qmax / mmax) * (bottom - top)) * (right - left);
            yEnd = top;
        }
        g.setColor(LnpCartoon.MUTED);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
        g.draw(new Line2D.Double(left, bottom, xEnd, yEnd));
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        for (Point p : fit.points) {
            double x = left + p.q / qmax * (right - left), y = bottom - p.m / mmax * (bottom - top);
            boolean off = Math.abs(p.aFromThis - fit.a) / fit.a > 0.02;
            g.setColor(off ? new Color(0xdc2626) : new Color(0x2563eb));
            g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
            g.setColor(LnpCartoon.MUTED);
            g.drawString(p.hkl, (float) (x + 5), (float) (y + 3));
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(LnpCartoon.MUTED);
        g.drawString("q (Å⁻¹)", right - 38, bottom + 12);
        g.rotate(-Math.PI / 2, 12, bottom);
        g.drawString(lattice != null && lattice.lamellar ? "order h" : "√N", 12, bottom);
        g.rotate(Math.PI / 2, 12, bottom);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(LnpCartoon.INK);
        g.drawString(String.format("a = %.1f ± %.1f Å", fit.a, fit.aSpread), 8, bottom + 24);
        if (!fit.absent.isEmpty()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g.setColor(new Color(0xdc2626));
            g.drawString("missing: " + String.join(" ", fit.absent), 8, bottom + 34);
        }
        g.dispose();
    }
}
