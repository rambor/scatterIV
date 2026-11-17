package version4.FormFactors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Cartoon of a fitted helix, drawn to scale: the strands (shaded tubes, or spheres for subunit helices) seen from the
 * side with the helix axis horizontal, with the diameter 2r and the pitch marked. Text lines below the drawing give
 * the fitted values. Paints into any Graphics2D, so the same drawing goes to the screen, a PNG or an SVG.
 */
public final class HelixCartoon extends JComponent {

    /** what to draw; lengths in Angstrom */
    public static final class Spec {
        public double radius, pitch, strandRadius;
        public int strands = 1;
        /** double helix: angle between the strands (degrees); 180 = symmetric */
        public double strandAngle = 180;
        /** subunit helix: rise and twist per subunit, subunit radius; pitch = 360 rise / |twist| */
        public boolean subunits;
        public double rise, twist, subunitRadius;
        /** ellipsoidal subunits (a, a, c), drawn with c along the radial direction */
        public boolean ellipsoids;
        public double ellA, ellC;
        /** pitch not determined by the data (no layer line inside the fitted q range): drawn, but labelled as such */
        public boolean pitchUndetermined;
        /** text shown under the drawing (fitted values) */
        public List<String> notes = new ArrayList<>();
        public String title = "";
    }

    private static final Color AXIS = new Color(120, 120, 120), TEXT = new Color(40, 40, 40), DIM = new Color(200, 70, 40);
    private static final Color[] STRAND = {new Color(56, 120, 200), new Color(220, 140, 40), new Color(80, 160, 90),
            new Color(170, 80, 160), new Color(70, 170, 180), new Color(190, 80, 80)};
    private Spec spec;

    public HelixCartoon() {
        setBackground(Color.WHITE);
        setOpaque(true);
        setPreferredSize(new Dimension(900, 560));
    }

    public void setSpec(Spec s) {
        this.spec = s;
        repaint();
    }

    public Spec getSpec() { return spec; }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (spec != null) draw(g, spec, getWidth(), getHeight());
        g.dispose();
    }

    /** draws the cartoon into a width x height area at the origin */
    public static void draw(Graphics2D g, Spec s, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        double pitch = s.subunits ? 360.0 * s.rise / Math.max(1e-9, Math.abs(s.twist)) : s.pitch;
        double thick = s.ellipsoids ? Math.max(s.ellA, s.ellC) : s.subunits ? s.subunitRadius : s.strandRadius;
        double r = Math.max(s.radius, 0);
        boolean noPitch = !(pitch > 0) || Double.isInfinite(pitch) || pitch > 1e6;
        // at least 3 turns, and long enough that the drawing is wider than it is tall
        double turns = noPitch ? 0 : Math.max(3, Math.ceil(1.8 * 2 * (r + thick) / pitch));
        turns = Math.min(turns, 40);
        double shownLength = noPitch ? Math.max(4 * (r + thick), 50) : turns * pitch;
        if (s.subunits) shownLength = Math.max(shownLength, 14 * s.rise);      // enough subunits to see the lattice

        // layout: title on top, notes below, drawing in between with room for dimension marks
        // text scales with the image: about 19 pt on a 1000 px wide figure, never below 15 pt
        int base = Math.max(15, Math.round(width / 52f)), lineH = Math.round(base * 1.45f);
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, base + 3), font = new Font(Font.SANS_SERIF, Font.PLAIN, base);
        // notes wrapped to the width
        List<String> lines = new ArrayList<>();
        FontMetrics fm = g.getFontMetrics(font);
        for (String note : s.notes) {
            StringBuilder cur = new StringBuilder();
            for (String word : note.split(" ")) {
                String trial = cur.length() == 0 ? word : cur + " " + word;
                if (fm.stringWidth(trial) > width - 24 && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder("    " + word);
                } else {
                    cur = new StringBuilder(trial);
                }
            }
            lines.add(cur.toString());
        }
        int noteLines = lines.size();
        int top = 2 * base + 30, bottom = lineH / 2 + 8 + lineH * noteLines, left = 9 * base, right = 30;
        double availW = width - left - right, availH = height - top - bottom - 50;
        double halfH = r + Math.max(thick, 0.02 * shownLength);
        double scale = Math.min(availW / (shownLength + 2 * thick), availH / (2 * halfH));
        if (!(scale > 0)) return;
        double x0 = left + thick * scale + (availW - (shownLength + 2 * thick) * scale) / 2;
        double yAxis = top + 30 + availH / 2;

        g.setColor(TEXT);
        g.setFont(titleFont);
        g.drawString(s.title == null ? "" : s.title, 12, base + 10);

        // helix axis
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
        g.draw(new Line2D.Double(x0 - 15, yAxis, x0 + shownLength * scale + 15, yAxis));

        double[] psi = strandAzimuths(s);
        double tubePx = Math.max(2.0, 2 * thick * scale);
        if (s.subunits) drawSubunits(g, s, psi, x0, yAxis, scale, shownLength);
        else drawTubes(g, psi, r, pitch, noPitch, x0, yAxis, scale, shownLength, tubePx);

        // dimension marks
        g.setFont(font.deriveFont(Font.BOLD));
        g.setStroke(new BasicStroke(2f));
        g.setColor(DIM);
        // diameter 2r at the left
        double xd = x0 - (s.subunits ? Math.max(thick, 0.3 * s.rise) : thick) * scale - 22,
                yTop = yAxis - r * scale, yBot = yAxis + r * scale;
        if (r * scale > 6) {
            arrow(g, xd, yAxis, xd, yTop);
            arrow(g, xd, yAxis, xd, yBot);
            g.setStroke(new BasicStroke(0.8f));
            g.draw(new Line2D.Double(xd - 5, yTop, x0 - 4, yTop));
            g.draw(new Line2D.Double(xd - 5, yBot, x0 - 4, yBot));
            g.setStroke(new BasicStroke(2f));
            String t = String.format("2r = %s Å", fmt(2 * r));
            int tw = g.getFontMetrics().stringWidth(t);
            g.drawString(t, (float) Math.max(4, xd - tw - 8), (float) (yAxis + base / 3.0));
        }
        // pitch above the helix, between two crests of strand 0 (crest where the strand is at +r on screen, y = -r)
        if (!noPitch) {
            double yp = yAxis - halfH * scale - base;
            double crest = crestOffset(psi[0], pitch);
            double xa = x0 + crest * scale, xb = xa + pitch * scale;
            arrow(g, (xa + xb) / 2, yp, xa, yp);
            arrow(g, (xa + xb) / 2, yp, xb, yp);
            g.setStroke(new BasicStroke(0.8f));
            g.draw(new Line2D.Double(xa, yp - 5, xa, yAxis - r * scale));
            g.draw(new Line2D.Double(xb, yp - 5, xb, yAxis - r * scale));
            String t = s.pitchUndetermined ? String.format("pitch %s Å (drawn; not determined)", fmt(pitch))
                    : String.format("pitch = %s Å", fmt(pitch));
            int tw = g.getFontMetrics().stringWidth(t);
            g.drawString(t, (float) ((xa + xb - tw) / 2), (float) (yp - 6));
        }

        // notes
        g.setColor(TEXT);
        g.setFont(font);
        float yn = (float) (height - bottom + lineH);
        for (String n : lines) {
            g.drawString(n, 12, yn);
            yn += lineH;
        }
    }

    /**
     * Spec from a finished fit: the helix geometry that was used (with the best value of a scanned pitch or radius)
     * and, for infinitely long helices, the fitted radius distribution (mass-weighted mean and peaks); for finite
     * filaments the mass-weighted median length. Null if the family is not a helix.
     */
    public static Spec fromFit(ShapeFamily family, LangevinSettings set, LangevinResult r, ParameterProfile.Profile scan) {
        if (family != ShapeFamily.HELIX && family != ShapeFamily.HELIX_SUBUNITS && family != ShapeFamily.HELIX_INFINITE
                && family != ShapeFamily.HELIX_INFINITE_ELLIPSOIDS) return null;
        boolean ell = family == ShapeFamily.HELIX_INFINITE_ELLIPSOIDS;
        Spec c = new Spec();
        c.radius = set.helixRadius;
        c.pitch = set.helixPitch;
        c.strands = set.helixStrands;
        c.strandAngle = set.helixStrandAngle;
        c.strandRadius = set.helixStrandRadius;
        c.subunits = family == ShapeFamily.HELIX_SUBUNITS || ell;
        c.rise = set.helixRise;
        c.twist = set.helixTwist;
        c.subunitRadius = set.helixSubunitRadius;
        double perTurn = set.helixSubunitsPerTurn;
        String pitchNote = "", radiusNote = "";
        if (scan != null && scan.best != null) {
            String range = "";
            if (Double.isFinite(scan.lo68) && Double.isFinite(scan.hi68)) {
                if (scan.lo68Open && scan.hi68Open) range = " (68 %: not bounded by the scan)";
                else if (scan.lo68Open) range = String.format(" (68 %%: ≤ %s Å, lower end open)", fmt(scan.hi68));
                else if (scan.hi68Open) range = String.format(" (68 %%: ≥ %s Å, upper end open)", fmt(scan.lo68));
                else range = String.format(" (68 %%: %s-%s Å)", fmt(scan.lo68), fmt(scan.hi68));
            }
            if (scan.parameter.startsWith("pitch")) { c.pitch = scan.bestValue; pitchNote = ", best of the scan" + range; }
            else if (scan.parameter.startsWith("per_turn")) { perTurn = scan.bestValue; pitchNote = "per_turn" + range.replace(" Å", ""); }
            else { c.radius = scan.bestValue; radiusNote = ", best of the scan" + range; }
        }
        double[] x = r.binCenters[0], w = r.volumeDensity[0];      // volume weighting = mass weighting here
        double sum = 0, mean = 0, max = 0;
        for (int i = 0; i < x.length; i++) { sum += w[i]; mean += w[i] * x[i]; max = Math.max(max, w[i]); }
        mean = sum > 0 ? mean / sum : Double.NaN;
        StringBuilder peaks = new StringBuilder();
        for (int i = 0; i < x.length; i++) {
            boolean up = i == 0 || w[i] > w[i - 1], down = i == x.length - 1 || w[i] >= w[i + 1];
            if (up && down && w[i] > 0.15 * max) peaks.append(peaks.length() == 0 ? "" : ", ").append(fmt(x[i]));
        }
        double median = Double.NaN, acc = 0;
        for (int i = 0; i < x.length && sum > 0; i++) { acc += w[i]; if (acc >= sum / 2) { median = x[i]; break; } }

        if (family == ShapeFamily.HELIX_INFINITE || ell) {
            c.radius = mean;
            c.title = ell ? "Helix of ellipsoidal subunits (infinitely long): fitted radius distribution"
                    : "Helix (infinitely long): fitted radius distribution";
            c.notes.add(String.format("Helix radius r = %s Å (mass-weighted mean); distribution peaks at %s Å", fmt(mean), peaks));
            c.notes.add("Length: longer than the q range resolves (treated as infinite)");
        } else {
            c.title = c.subunits ? "Helical filament of subunits" : "Helix";
            c.notes.add(String.format("Helix radius r = %s Å%s", fmt(c.radius), radiusNote.isEmpty() ? " (fixed)" : radiusNote));
            c.notes.add(String.format("Filament length L: mass-weighted median %s Å, mean %s Å", fmt(median), fmt(mean)));
        }
        if (ell) {
            c.ellipsoids = true;
            c.ellA = set.helixEllipsoidA;
            c.ellC = set.helixEllipsoidC;
            c.rise = c.pitch / perTurn;
            c.twist = 360 / perTurn;
            boolean perTurnScanned = pitchNote.startsWith("per_turn");
            String perTurnNote = perTurnScanned ? ", best of the scan" + pitchNote.substring("per_turn".length()) : " (fixed)";
            if (perTurnScanned) pitchNote = "";
            c.notes.add(String.format("Lattice: pitch %s Å%s; %s subunits per turn per strand%s (rise %s Å)",
                    fmt(c.pitch), pitchNote.isEmpty() ? " (fixed)" : pitchNote, fmt(perTurn), perTurnNote, fmt(c.rise)));
            double v = HelicalLattice.ellipsoidVolume(c.ellA, c.ellC);
            String prior = Double.isFinite(set.helixUnitVolume)
                    ? String.format("; %.0f %% of the expected %.3g Å³", 100 * v / set.helixUnitVolume, set.helixUnitVolume) : "";
            c.notes.add(String.format("Subunit: ellipsoid a = %s Å, c = %s Å, volume %.3g Å³%s (drawn radially; orientation not fitted)",
                    fmt(c.ellA), fmt(c.ellC), v, prior));
        } else if (c.subunits) {
            double pitch = 360 * c.rise / Math.max(1e-9, Math.abs(c.twist));
            c.notes.add(String.format("Rise %s Å, twist %s° per subunit → pitch %s Å, %s subunits per turn; subunit radius %s Å",
                    fmt(c.rise), fmt(c.twist), fmt(pitch), fmt(360 / Math.max(1e-9, Math.abs(c.twist))), fmt(c.subunitRadius)));
        } else {
            // with n symmetric strands the first layer line is at q = n 2 pi / pitch (k = n); the strand angle of an
            // asymmetric double helix lets k = 1 through
            int firstK = c.strands == 2 && c.strandAngle != 180 ? 1 : c.strands;
            double qFirst = firstK * 2 * Math.PI / c.pitch, qTop = r.q.length > 0 ? r.q[r.q.length - 1] : Double.NaN;
            if (pitchNote.isEmpty() && qFirst > qTop) {
                c.pitchUndetermined = true;
                c.notes.add(String.format("Pitch not determined: the first layer line (q = %.3f Å⁻¹) lies beyond the fitted q max "
                        + "%.3f Å⁻¹, so any pitch below %s Å fits the same", qFirst, qTop, fmt(firstK * 2 * Math.PI / qTop)));
            } else {
                c.notes.add(String.format("Pitch = %s Å%s", fmt(c.pitch), pitchNote.isEmpty() ? " (fixed)" : pitchNote));
            }
        }
        String strands = c.strands == 1 ? "1 strand" : c.strands + " strands"
                + (c.strands == 2 && c.strandAngle != 180 && !c.subunits ? String.format(" (φ = %s°)", fmt(c.strandAngle)) : "");
        if (c.pitchUndetermined) strands += " (the strand count is not determined either: it only shows in the layer lines)";
        c.notes.add(c.subunits ? strands : String.format("%s, strand radius %s Å", strands, fmt(c.strandRadius)));
        c.notes.add(String.format("Fit: reduced χ² %.2f", r.chi2Reduced));
        return c;
    }

    /** writes the cartoon as PNG or SVG (by extension) */
    public static void write(Spec s, java.io.File file, int width, int height) throws java.io.IOException {
        String n = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".svg")) {
            org.jfree.graphics2d.svg.SVGGraphics2D g = new org.jfree.graphics2d.svg.SVGGraphics2D(width, height);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            draw(g, s, width, height);
            org.jfree.graphics2d.svg.SVGUtils.writeToSVG(file, g.getSVGElement());
        } else {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            draw(g, s, width, height);
            g.dispose();
            javax.imageio.ImageIO.write(im, "png", file);
        }
    }

    static double[] strandAzimuths(Spec s) {
        int n = Math.max(1, s.strands);
        if (!s.subunits && n == 2 && s.strandAngle > 0 && s.strandAngle < 360)
            return new double[]{0, Math.toRadians(s.strandAngle)};
        double[] p = new double[n];
        for (int k = 0; k < n; k++) p[k] = 2 * Math.PI * k / n;
        return p;
    }

    /** axial position of the first crest (strand at the top of the drawing) of a strand with azimuth psi */
    private static double crestOffset(double psi, double pitch) {
        // screen y = -r cos(2 pi z / pitch + psi): top (y = -r) where the phase is 0 mod 2 pi
        double z = -psi / (2 * Math.PI) * pitch;
        z %= pitch;
        if (z < 0) z += pitch;
        return z;
    }

    /**
     * Continuous strands as tubes: each strand is split into runs behind (depth < 0) and in front of the axis; all
     * back runs are drawn first (darker), then the front runs, each as one smooth path with an outline.
     */
    private static void drawTubes(Graphics2D g, double[] psi, double r, double pitch, boolean noPitch, double x0, double yAxis,
                                  double scale, double length, double tubePx) {
        int segs = 1200;
        List<Object[]> back = new ArrayList<>(), front = new ArrayList<>();   // {Path2D, strand}
        for (int s = 0; s < psi.length; s++) {
            Path2D path = null;
            boolean pathFront = false;
            for (int i = 0; i <= segs; i++) {
                double z = length * i / segs;
                double ph = (noPitch ? 0 : 2 * Math.PI * z / pitch) + psi[s];
                double x = x0 + z * scale, y = yAxis - r * Math.cos(ph) * scale;
                boolean isFront = -Math.sin(ph) >= 0;
                if (path == null || isFront != pathFront) {
                    if (path != null) {
                        path.lineTo(x, y);                       // close the gap at the crossing
                        (pathFront ? front : back).add(new Object[]{path, s});
                    }
                    path = new Path2D.Double();
                    path.moveTo(x, y);
                    pathFront = isFront;
                } else {
                    path.lineTo(x, y);
                }
            }
            if (path != null) (pathFront ? front : back).add(new Object[]{path, s});
        }
        BasicStroke outline = new BasicStroke((float) tubePx + 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke body = new BasicStroke((float) tubePx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke shine = new BasicStroke((float) Math.max(1, tubePx * 0.25), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        for (int pass = 0; pass < 2; pass++) {
            List<Object[]> runs = pass == 0 ? back : front;
            for (Object[] run : runs) {
                Path2D path = (Path2D) run[0];
                Color base = STRAND[(Integer) run[1] % STRAND.length];
                Color c = pass == 0 ? shade(base, 0.62) : base;
                g.setStroke(outline);
                g.setColor(shade(c, 0.55));
                g.draw(path);
                g.setStroke(body);
                g.setColor(c);
                g.draw(path);
                if (pass == 1 && tubePx > 6) {                   // a highlight along the front runs
                    g.setStroke(shine);
                    g.setColor(brighten(c, 0.35));
                    g.draw(AffineTransform.getTranslateInstance(0, -tubePx * 0.18).createTransformedShape(path));
                }
            }
        }
    }

    /** subunit helix: spheres, back to front */
    private static void drawSubunits(Graphics2D g, Spec s, double[] psi, double x0, double yAxis, double scale, double length) {
        int n = (int) Math.floor(length / s.rise) + 1;
        double tw = Math.toRadians(s.twist), a = Math.max(s.ellipsoids ? s.ellA : s.subunitRadius, 0.3 * s.rise);
        List<double[]> balls = new ArrayList<>();
        for (int st = 0; st < psi.length; st++)
            for (int i = 0; i < n; i++) {
                double z = i * s.rise, ph = i * tw + psi[st];
                balls.add(new double[]{-s.radius * Math.sin(ph), st, x0 + z * scale, yAxis - s.radius * Math.cos(ph) * scale, Math.cos(ph)});
            }
        balls.sort((p, q) -> Double.compare(p[0], q[0]));
        double rp = Math.max(2, a * scale), maxDepth = Math.max(s.radius, 1e-9);
        for (double[] b : balls) {
            double t = 0.5 + 0.5 * b[0] / maxDepth;
            Color base = shade(STRAND[(int) b[1] % STRAND.length], 0.45 + 0.55 * t);
            double rx = rp, ry = rp;
            if (s.ellipsoids) {
                // c along the radial direction: its projection on the screen is vertical, scaled by |cos(phase)|
                double cosPh = Math.abs(b[4]);
                rx = Math.max(2, s.ellA * scale);
                double sinPh = Math.sqrt(Math.max(0, 1 - cosPh * cosPh));
                ry = Math.max(2, Math.sqrt(Math.pow(s.ellC * cosPh, 2) + Math.pow(s.ellA * sinPh, 2)) * scale);
            }
            Point2D c = new Point2D.Double(b[2] - 0.35 * rx, b[3] - 0.35 * ry);
            g.setPaint(new RadialGradientPaint(c, (float) (1.4 * Math.max(rx, ry)), new float[]{0f, 1f},
                    new Color[]{brighten(base, 0.45), shade(base, 0.7)}));
            g.fill(new Ellipse2D.Double(b[2] - rx, b[3] - ry, 2 * rx, 2 * ry));
            g.setColor(shade(base, 0.55));
            g.setStroke(new BasicStroke(0.8f));
            g.draw(new Ellipse2D.Double(b[2] - rx, b[3] - ry, 2 * rx, 2 * ry));
        }
    }

    private static void arrow(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        double ang = Math.atan2(y2 - y1, x2 - x1), len = 11, w = 0.4;
        Path2D head = new Path2D.Double();
        head.moveTo(x2, y2);
        head.lineTo(x2 - len * Math.cos(ang - w), y2 - len * Math.sin(ang - w));
        head.lineTo(x2 - len * Math.cos(ang + w), y2 - len * Math.sin(ang + w));
        head.closePath();
        g.fill(head);
    }

    private static Color shade(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    private static Color brighten(Color c, double f) {
        return new Color(clamp(c.getRed() + (255 - c.getRed()) * f), clamp(c.getGreen() + (255 - c.getGreen()) * f),
                clamp(c.getBlue() + (255 - c.getBlue()) * f));
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 0.05 || Math.abs(v) >= 100) return String.format("%.0f", v);
        return String.format("%.1f", v);
    }
}
