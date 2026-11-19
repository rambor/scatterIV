package version4.FormFactors;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picture of a lipid nanoparticle built only from what the SAXS analysis supports, drawn to scale.
 *
 * <ul>
 *   <li><b>Cross-section</b>: shells from the core / multi-shell fit (radii and density contrast), the size spread as
 *       faint rings at R &plusmn; sigma, and the core interior from the peaks: concentric bilayers (lamellar, repeat d),
 *       hexagonally packed water/RNA channels (inverse hexagonal, lattice a), a short-range-ordered packing with
 *       spacing d and correlation length 2&pi;/FWHM (broad mRNA peak), or two coexisting lattices. Parameters that were
 *       assumed (held fixed) or are poorly determined are drawn dashed and labelled as such.</li>
 *   <li><b>Simulated cryo-EM</b>: projected electron density of a field of particles whose radii follow the fitted
 *       distribution and whose internal lattices are randomly oriented, for side-by-side comparison with micrographs
 *       (inverse-hexagonal tubes appear as hexagons end-on and straight lines side-on, Pattipeiluhu et al. 2024).</li>
 * </ul>
 */
public final class LnpCartoon extends JComponent {

    public enum Interior {
        UNIFORM("Uniform core"), MESOPHASE("Short-range RNA–lipid packing"), LAMELLAR("Lamellar (onion)"),
        HEXAGONAL("Inverse hexagonal (HII)"), CUBIC("Bicontinuous cubic"), COEXIST("Two coexisting lattices");
        final String label;
        Interior(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public enum View {
        SECTION("Whole particle"), CLOSE_UP("Molecular close-up"), CRYO_EM("Simulated cryo-EM");
        final String label;
        View(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    /** a layer of the mean particle, innermost first */
    public static final class Layer {
        public String name;
        public double outer;          // outer radius (A)
        public double rho;            // contrast vs buffer (e/A^3)
        public String thickness;      // label text, e.g. "1.9 ± 0.3 nm"
        public boolean uncertain;     // assumed or poorly determined: dashed
    }

    /** everything the picture needs; built by the panel from the fit and the assessment */
    public static final class Spec {
        public final List<Layer> layers = new ArrayList<>();   // [0] = core
        public double r0, sigma;
        public String coreLabel = "";
        public Interior interior = Interior.UNIFORM;
        public String interiorNote = "";                         // why this interior was chosen
        public double spacing = 50;                              // d (lamellar, mesophase) or a (HII, cubic), A
        public double spacing2 = 0;                              // second lattice (coexistence), A
        public double correlation = Double.POSITIVE_INFINITY;    // ordered domain size 2 pi / FWHM, A
        public boolean cargo = true;                             // nucleic acid in the aqueous compartments
        public boolean interiorUncertain;                        // e.g. one peak: lamellar or HII cannot be told apart
        public String symmetry = "";                             // cubic space group (Pn3m, Im3m, Ia3d) for channel size
        /** coexistence: phase of the centre (spacing) and of the periphery (spacing2) */
        public Interior centre = Interior.HEXAGONAL, periphery = Interior.HEXAGONAL;
        public String symmetry2 = "";
        /** lipid monolayer (chain) length l, sets water-channel and water-layer sizes from the lattice */
        public double lipidLength = 17;
        /** cargo for the molecular view: 0 none, 1 siRNA duplex, 2 mRNA / long single strand */
        public int cargoKind = 2;
        public double cargoLength = 4284;   // bp (siRNA) or nt
        public double ionizableFraction = 0.5;
        /** bilayer vesicle (Fig. 5d): water core, two leaflets */
        public boolean vesicle;
        /**
         * Morphology populations from {@link LnpPopulations}: {kind ordinal, number fraction, size (A), sigmaRel,
         * bleb ratio}. Empty = one population (the fitted particle).
         */
        public final List<double[]> population = new ArrayList<>();
        public String title = "";
    }

    // palette (light, print friendly; tails warm, headgroups green, PEG blue, nucleic acid purple)
    static final Color BUFFER = new Color(0xf3f7fb), TAILS = new Color(0xf0b44c), MATRIX = new Color(0xf7d892),
            HEAD = new Color(0x2f9e72), PEG = new Color(0xb9dcf3), PEG_LINE = new Color(0x6aaed6),
            RNA = new Color(0x8e44ad), WATER = new Color(0xcfe6f7), INK = new Color(0x24292f), MUTED = new Color(0x6e7781),
            SPREAD = new Color(0x9aa5b1);

    private Spec spec;
    private View view = View.SECTION;
    private long seed = 7;
    private BufferedImage emCache;
    private int emW, emH;

    public LnpCartoon() {
        setPreferredSize(new Dimension(700, 520));
        setOpaque(true);
        setBackground(Color.WHITE);
    }

    public void setSpec(Spec s) { spec = s; emCache = null; repaint(); }
    public Spec getSpec() { return spec; }
    public void setView(View v) { view = v; repaint(); }
    public View getView() { return view; }
    /** new random arrangement of the simulated field / disordered packing */
    public void reshuffle() { seed++; emCache = null; repaint(); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
        render(g, getWidth(), getHeight());
        g.dispose();
    }

    /** draws the current view into any Graphics2D (screen, PNG or SVG) */
    public void render(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (spec == null || spec.layers.isEmpty()) {
            g.setColor(MUTED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            String t = "Fit a model (or press Calculate) to draw the particle";
            g.drawString(t, (w - g.getFontMetrics().stringWidth(t)) / 2, h / 2);
            return;
        }
        if (view == View.SECTION) renderSection(g, w, h);
        else if (view == View.CLOSE_UP) renderCloseUp(g, w, h);
        else renderEm(g, w, h);
    }

    // ================================================================== cross-section

    private void renderSection(Graphics2D g, int w, int h) {
        Spec s = spec;
        double rOut = s.layers.get(s.layers.size() - 1).outer;
        double rMax = rOut + 1.2 * s.sigma;
        int legendW = Math.min(300, (int) (w * 0.38));
        double drawW = w - legendW - 30, drawH = h - 70;
        double scale = Math.min(drawW, drawH) / (2 * rMax);   // px per A
        double cx = 20 + drawW / 2, cy = 40 + drawH / 2;

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(INK);
        g.drawString(s.title.isEmpty() ? "Particle model (cross-section, to scale)" : s.title, 14, 22);

        // buffer disc behind the particle
        g.setColor(BUFFER);
        g.fill(circle(cx, cy, rMax * scale * 1.02));

        // layers from the outside in
        for (int k = s.layers.size() - 1; k >= 1; k--) {
            Layer L = s.layers.get(k);
            double inner = s.layers.get(k - 1).outer;
            Area ring = new Area(circle(cx, cy, L.outer * scale));
            ring.subtract(new Area(circle(cx, cy, inner * scale)));
            g.setColor(layerColor(L, k));
            g.fill(ring);
            if (L.name.toLowerCase().contains("peg")) drawBrushes(g, cx, cy, inner * scale, L.outer * scale);
        }
        // core
        Layer core = s.layers.get(0);
        Shape coreShape = circle(cx, cy, core.outer * scale);
        g.setColor(s.interior == Interior.UNIFORM ? densityColor(core.rho) : MATRIX);
        g.fill(coreShape);
        Shape oldClip = g.getClip();
        g.clip(coreShape);
        drawInterior(g, cx, cy, core.outer, scale, new Random(seed));
        g.setClip(oldClip);

        // boundaries: solid when determined, dashed when assumed / poorly determined
        for (int k = 0; k < s.layers.size(); k++) {
            Layer L = s.layers.get(k);
            g.setColor(new Color(0, 0, 0, 90));
            g.setStroke(L.uncertain ? dashed(1.1f) : new BasicStroke(1.1f));
            g.draw(circle(cx, cy, L.outer * scale));
        }
        // size spread: outer radius +- sigma
        if (s.sigma > 0) {
            g.setColor(SPREAD);
            g.setStroke(dashed(1f));
            g.draw(circle(cx, cy, (rOut - s.sigma) * scale));
            g.draw(circle(cx, cy, (rOut + s.sigma) * scale));
        }

        // labels with leader lines on the right
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        double lx = w - legendW + 6;
        List<String[]> rows = new ArrayList<>();
        List<Double> radii = new ArrayList<>();
        for (int k = s.layers.size() - 1; k >= 0; k--) {
            Layer L = s.layers.get(k);
            double mid = k == 0 ? 0.55 * L.outer : 0.5 * (L.outer + s.layers.get(k - 1).outer);
            rows.add(new String[]{L.name, k == 0 ? s.coreLabel : L.thickness, L.uncertain ? "u" : ""});
            radii.add(mid);
        }
        if (s.sigma > 0) {
            rows.add(new String[]{"size spread", String.format("outer radius ± σ (%.1f nm)", s.sigma / 10), ""});
            radii.add(rOut + s.sigma);
        }
        double rowH = 2.25 * fm.getHeight();
        double y0 = cy - rowH * rows.size() / 2.0 + fm.getAscent();
        for (int i = 0; i < rows.size(); i++) {
            double ty = y0 + i * rowH;
            double ang = Math.toRadians(-38 + 76.0 * i / Math.max(1, rows.size() - 1));
            double rr = radii.get(i) * scale;
            double px = cx + rr * Math.cos(ang), py = cy + rr * Math.sin(ang);
            g.setColor(new Color(0x8c959f));
            g.setStroke(new BasicStroke(0.8f));
            g.draw(new Line2D.Double(px, py, lx - 6, ty - fm.getAscent() / 2.0));
            g.fill(circle(px, py, 2));
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(rows.get(i)[0], (float) lx, (float) ty);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(rows.get(i)[2].isEmpty() ? INK : MUTED);
            g.drawString(rows.get(i)[1], (float) lx, (float) (ty + fm.getHeight()));
        }

        // interior caption and scale bar
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(INK);
        String cap = "Core interior: " + interiorText();
        g.drawString(cap, 14, h - 34);
        if (!s.interiorNote.isEmpty()) {
            g.setColor(MUTED);
            g.drawString(s.interiorNote, 14, h - 16);
        }
        scaleBar(g, scale, w - legendW - 20, h - 22);
    }

    private static String shortName(Interior k, String sym) {
        switch (k) {
            case LAMELLAR: return "lamellar";
            case CUBIC: return sym == null || sym.isEmpty() ? "cubic" : sym;
            default: return "HII";
        }
    }

    private String interiorText() {
        Spec s = spec;
        switch (s.interior) {
            case LAMELLAR: return String.format("concentric bilayers, repeat d = %.1f nm%s", s.spacing / 10, s.interiorUncertain ? " (one peak: lamellar or HII)" : "");
            case HEXAGONAL: return String.format("inverse hexagonal, a = %.1f nm, channels ≈ %.1f nm wide%s", s.spacing / 10,
                    2 * hexChannelRadius(s.spacing) / 10, s.interiorUncertain ? " (one peak: lamellar or HII)" : "");
            case CUBIC: return String.format("bicontinuous cubic %s, a = %.1f nm, channels ≈ %.1f nm wide (2D schematic)",
                    s.symmetry, s.spacing / 10, 2 * cubicChannelRadius(s.spacing, s.symmetry) / 10);
            case MESOPHASE: return String.format("disordered inverse hexagonal, cylinders %.1f nm apart, ordered over ≈ %.0f nm",
                    2 * s.spacing / Math.sqrt(3) / 10, s.correlation / 10);
            case COEXIST: return String.format("%s a = %.1f nm (centre) + %s a = %.1f nm (periphery); arrangement schematic",
                    shortName(s.centre, s.symmetry), s.spacing / 10, shortName(s.periphery, s.symmetry2), s.spacing2 / 10);
            default: return "no internal order resolved";
        }
    }

    /** core interior, clipped to the core circle by the caller; lengths in A, scale px/A */
    private void drawInterior(Graphics2D g, double cx, double cy, double rc, double scale, Random rnd) {
        Spec s = spec;
        switch (s.interior) {
            case LAMELLAR: case HEXAGONAL: case CUBIC:
                drawPhase(g, s.interior, s.spacing, s.symmetry, cx, cy, rc, 0, scale, rnd, s.interiorUncertain);
                break;
            case COEXIST: {
                // Pattipeiluhu et al. 2024 find the larger lattice (inverse micelles) at the periphery: draw it outside
                double split = Math.max(rc - 2.2 * Math.max(s.spacing, s.spacing2), 0.5 * rc);
                drawPhase(g, s.periphery, s.spacing2, s.symmetry2, cx, cy, rc, split, scale, rnd, false);
                drawPhase(g, s.centre, s.spacing, s.symmetry, cx, cy, split, 0, scale, rnd, false);
                g.setColor(new Color(0, 0, 0, 70));
                g.setStroke(dashed(1f));
                g.draw(circle(cx, cy, split * scale));
                break;
            }
            case MESOPHASE:
                disorderedHex(g, cx, cy, rc, scale, rnd);
                break;
            default:
                break;
        }
    }

    /** water / water-RNA channel radius from the lattice (inverse hexagonal: touching monolayers of length l) */
    double hexChannelRadius(double a) { return Math.max(0.15 * a, Math.min(0.45 * a, a / 2 - spec.lipidLength)); }

    /**
     * Bicontinuous cubic water-channel radius r_w = a sqrt(-A0 / (2 pi chi)) - l, with the surface area A0 and Euler
     * characteristic chi of the minimal surface (Pn3m 1.919/-2, Im3m 2.345/-4, Ia3d 3.091/-8). For a = 127 A, Ia3d and
     * l = 15 A this gives a channel diameter of ~33 A as in Zheng et al. (PNAS 2023).
     */
    double cubicChannelRadius(double a, String sym) {
        double a0 = 1.919, chi = -2;
        if (sym != null && sym.contains("Im3m")) { a0 = 2.345; chi = -4; }
        else if (sym != null && sym.contains("Ia3d")) { a0 = 3.091; chi = -8; }
        return Math.max(0.06 * a, a * Math.sqrt(-a0 / (2 * Math.PI * chi)) - spec.lipidLength);
    }

    /** one phase in the annulus rIn..rOut (the matrix around the aqueous compartments is lipid) */
    private void drawPhase(Graphics2D g, Interior kind, double a, String sym, double cx, double cy, double rOut, double rIn,
                           double scale, Random rnd, boolean uncertain) {
        Spec s = spec;
        Color aq = s.cargo ? RNA : WATER;
        Shape clip = g.getClip();
        Area ann = new Area(circle(cx, cy, rOut * scale));
        if (rIn > 0) ann.subtract(new Area(circle(cx, cy, rIn * scale)));
        g.clip(ann);
        g.setColor(MATRIX);
        g.fill(ann);
        switch (kind) {
            case LAMELLAR: {
                // repeat d = bilayer (2 l) + aqueous / RNA layer, concentric
                double bil = Math.min(0.8 * a, 2 * s.lipidLength), wAq = a - bil;
                Stroke st = uncertain ? dashed(1f) : new BasicStroke(0.8f);
                for (double r = rOut; r > Math.max(rIn, 0.3 * a); r -= a) {
                    Area aqL = new Area(circle(cx, cy, r * scale));
                    aqL.subtract(new Area(circle(cx, cy, Math.max(r - wAq, 0) * scale)));
                    g.setColor(withAlpha(aq, s.cargo ? 190 : 255));
                    g.fill(aqL);
                    Area b = new Area(circle(cx, cy, Math.max(r - wAq, 0) * scale));
                    b.subtract(new Area(circle(cx, cy, Math.max(r - a, 0) * scale)));
                    g.setColor(TAILS);
                    g.fill(b);
                    g.setColor(new Color(0, 0, 0, 45));
                    g.setStroke(st);
                    g.draw(circle(cx, cy, Math.max(r - wAq, 0) * scale));
                }
                break;
            }
            case HEXAGONAL:
                hexChannels(g, cx, cy, rOut, rIn, scale, a, hexChannelRadius(a), aq, rnd.nextDouble() * Math.PI / 3, uncertain);
                break;
            case CUBIC: {
                // 2D impression of a bicontinuous network: channel nodes on a square lattice (a/2) joined by struts
                double rw = cubicChannelRadius(a, sym), step = a / 2;
                g.setColor(withAlpha(aq, 200));
                g.setStroke(new BasicStroke((float) Math.max(1, 1.2 * rw * scale), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (double x = -rOut; x <= rOut; x += step)
                    for (double y = -rOut; y <= rOut; y += step) {
                        boolean even = ((int) Math.round((x + rOut) / step) + (int) Math.round((y + rOut) / step)) % 2 == 0;
                        if (even) g.draw(new Line2D.Double(cx + x * scale, cy + y * scale, cx + (x + step) * scale, cy + (y + step) * scale));
                        else g.draw(new Line2D.Double(cx + x * scale, cy + (y + step) * scale, cx + (x + step) * scale, cy + y * scale));
                    }
                break;
            }
            default:
                break;
        }
        g.setClip(clip);
    }

    /** hexagonal lattice of channels (lattice a, channel radius rw) between radii rIn and rOut */
    private void hexChannels(Graphics2D g, double cx, double cy, double rOut, double rIn, double scale, double a, double rw,
                             Color fill, double rot, boolean uncertain) {
        double c = Math.cos(rot), sn = Math.sin(rot);
        int n = (int) Math.ceil(rOut / a) + 2;
        for (int i = -n; i <= n; i++)
            for (int j = -n; j <= n; j++) {
                double x = a * (i + 0.5 * j), y = a * (Math.sqrt(3) / 2 * j);
                double xr = c * x - sn * y, yr = sn * x + c * y;
                double r = Math.hypot(xr, yr);
                if (r > rOut + rw || r < rIn - rw) continue;
                g.setColor(TAILS);
                g.fill(circle(cx + xr * scale, cy + yr * scale, (rw + 0.35 * spec.lipidLength) * scale));
                g.setColor(fill);
                g.fill(circle(cx + xr * scale, cy + yr * scale, rw * scale));
                if (uncertain) {
                    g.setColor(new Color(0, 0, 0, 60));
                    g.setStroke(dashed(0.8f));
                    g.draw(circle(cx + xr * scale, cy + yr * scale, rw * scale));
                }
            }
    }

    /**
     * Disordered inverse hexagonal core (Yanez Arteta et al. 2018): small hexagonal domains of water/RNA cylinders with
     * spacing a = 2d/sqrt3, random orientation per domain, domain size ~ the correlation length 2 pi / FWHM.
     */
    private void disorderedHex(Graphics2D g, double cx, double cy, double rc, double scale, Random rnd) {
        Spec s = spec;
        double a = 2 * s.spacing / Math.sqrt(3);
        double dom = Double.isInfinite(s.correlation) ? 4 * a : Math.max(1.5 * a, s.correlation);
        List<double[]> centres = poisson2d(rc + dom / 2, 0.8 * dom, rnd);
        double[] rot = new double[centres.size()], ox = new double[centres.size()], oy = new double[centres.size()];
        for (int i = 0; i < centres.size(); i++) { rot[i] = rnd.nextDouble() * Math.PI / 3; ox[i] = rnd.nextDouble() * a; oy[i] = rnd.nextDouble() * a; }
        double rw = hexChannelRadius(a);
        List<double[]> pts = new ArrayList<>();
        int n = (int) Math.ceil(rc / a) + 2;
        for (int k = 0; k < centres.size(); k++) {
            double c = Math.cos(rot[k]), sn = Math.sin(rot[k]);
            for (int i = -n; i <= n; i++)
                for (int j = -n; j <= n; j++) {
                    double x = a * (i + 0.5 * j) + ox[k], y = a * (Math.sqrt(3) / 2 * j) + oy[k];
                    double xr = c * x - sn * y, yr = sn * x + c * y;
                    if (xr * xr + yr * yr > rc * rc) continue;
                    // keep the point only inside this domain's Voronoi cell
                    int best = 0;
                    double bd = Double.POSITIVE_INFINITY;
                    for (int m = 0; m < centres.size(); m++) {
                        double dd = Math.hypot(xr - centres.get(m)[0], yr - centres.get(m)[1]);
                        if (dd < bd) { bd = dd; best = m; }
                    }
                    if (best != k) continue;
                    boolean ok = true;
                    for (double[] p : pts) if (Math.hypot(p[0] - xr, p[1] - yr) < 0.8 * a) { ok = false; break; }
                    if (ok) pts.add(new double[]{xr, yr});
                }
        }
        Color aq = s.cargo ? RNA : WATER;
        for (double[] p : pts) {
            g.setColor(TAILS);
            g.fill(circle(cx + p[0] * scale, cy + p[1] * scale, (rw + 0.35 * s.lipidLength) * scale));
        }
        for (double[] p : pts) {
            g.setColor(withAlpha(aq, 220));
            g.fill(circle(cx + p[0] * scale, cy + p[1] * scale, rw * scale));
        }
    }

    /** short radial strokes for the PEG brush */
    private void drawBrushes(Graphics2D g, double cx, double cy, double rIn, double rOut) {
        int n = (int) Math.max(24, 2 * Math.PI * rIn / 7);
        g.setColor(PEG_LINE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double len = (rOut - rIn) * (0.7 + 0.25 * Math.sin(i * 2.3));
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            int seg = 6;
            for (int k = 0; k <= seg; k++) {
                double r = rIn + len * k / seg, wig = 0.08 * Math.sin(k * 1.9 + i);
                double x = cx + r * Math.cos(a + wig * (rOut - rIn) / Math.max(r, 1)), y = cy + r * Math.sin(a + wig * (rOut - rIn) / Math.max(r, 1));
                if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            g.draw(p);
        }
    }

    private static Color layerColor(Layer L, int k) {
        String n = L.name.toLowerCase();
        if (n.contains("peg") || n.contains("corona")) return PEG;
        if (n.contains("head")) return HEAD;
        if (n.contains("lipid") || n.contains("shell") || n.contains("chain")) return L.rho < 0 ? TAILS : densityColor(L.rho);
        if (n.contains("water")) return WATER;
        return densityColor(L.rho);
    }

    /** contrast colour: below buffer warm (lipid-like), above buffer cool (headgroup / RNA-like) */
    static Color densityColor(double rho) {
        double t = Math.max(-1, Math.min(1, rho / 0.06));
        if (t < 0) return blend(new Color(0xfbeed2), TAILS, -t);
        return blend(new Color(0xe8f1f8), new Color(0x7fa7d6), t);
    }

    // ================================================================== molecular close-up

    // particle frame -> screen for the close-up
    private double cuX0, cuY0, cuScale;

    private double sx(double X) { return cuX0 + X * cuScale; }
    private double sy(double Y) { return cuY0 - Y * cuScale; }

    /**
     * To-scale close-up of the top edge of the mean particle in the usual schematic style: lipids as head + two tails,
     * PEG as coils, nucleic acid as a double helix (siRNA, 0.28 nm per bp, 2.3 nm wide) or a single strand (mRNA).
     */
    private void renderCloseUp(Graphics2D g, int w, int h) {
        Spec s = spec;
        Random rnd = new Random(seed);
        int nl = s.layers.size();
        double rOut = s.layers.get(nl - 1).outer, r0 = s.layers.get(0).outer;
        double rHead = nl >= 3 ? s.layers.get(2).outer : nl == 2 ? s.layers.get(1).outer : r0;   // outer edge of headgroups
        double tTail = nl >= 2 ? s.layers.get(1).outer - r0 : s.lipidLength;
        double tHead = nl >= 3 ? s.layers.get(2).outer - s.layers.get(1).outer : 4;
        double interiorDepth = Math.min(r0, Math.max(200, 3.2 * Math.max(s.spacing, s.spacing2)));
        double top = rOut + 25, bottom = r0 - interiorDepth;
        int legendW = Math.min(260, (int) (w * 0.3));
        double boxW = w - legendW - 30, boxH = h - 80;
        cuScale = boxH / (top - bottom);
        double halfW = boxW / 2 / cuScale;
        cuX0 = 15 + boxW / 2;
        cuY0 = 40 + top * cuScale;
        Shape box = new Rectangle2D.Double(15, 40, boxW, boxH);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(INK);
        g.drawString("Molecular close-up of the particle edge (to scale)", 14, 22);
        Shape oldClip = g.getClip();
        g.clip(box);
        g.setColor(BUFFER);
        g.fill(box);
        // particle body behind the glyphs
        g.setColor(new Color(0xfdf3df));
        g.fill(circleScreen(0, 0, r0));

        // ---- interior
        drawCloseUpInterior(g, r0, bottom, halfW, rnd);

        // ---- outer monolayer: heads at the headgroup layer, tails pointing into the particle
        double headR = Math.max(2.2, Math.min(4.5, 0.6 * tHead + 1.8));
        double headCentre = rHead - headR;
        double spacing = 8.0;                                     // ~0.6 nm^2 per lipid
        int n = (int) Math.ceil(2 * halfW / spacing) + 4;
        double th0 = -(halfW + 20) / headCentre;
        double dth = spacing / headCentre;
        List<double[]> anchors = new ArrayList<>();
        for (int i = 0; i < n + 6; i++) {
            double th = th0 + i * dth;
            double X = headCentre * Math.sin(th), Y = headCentre * Math.cos(th);
            boolean ionizable = rnd.nextDouble() < s.ionizableFraction * 0.5;   // the surface is enriched in DSPC (Arteta 2018)
            lipid(g, X, Y, -Math.sin(th), -Math.cos(th), tTail + tHead - headR, headR, ionizable);
            if (i % 4 == 1) anchors.add(new double[]{rHead * Math.sin(th), rHead * Math.cos(th), th});
        }
        // ---- PEG coils on a fraction of the surface lipids
        double tPeg = rOut - rHead;
        if (nl >= 4 && tPeg > 5) {
            g.setStroke(new BasicStroke((float) Math.max(1, 1.6 * cuScale / 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(PEG_LINE);
            for (double[] an : anchors) {
                java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
                double ux = Math.sin(an[2]), uy = Math.cos(an[2]), vx = uy, vy = -ux;
                p.moveTo(sx(an[0]), sy(an[1]));
                int seg = 40;
                double ph = rnd.nextDouble() * 6.28;
                for (int k = 1; k <= seg; k++) {
                    double t = (double) k / seg;
                    double along = tPeg * 0.95 * t, lat = 0.22 * tPeg * Math.sin(5 * Math.PI * t + ph) * Math.sqrt(t);
                    double X = an[0] + ux * along + vx * lat, Y = an[1] + uy * along + vy * lat;
                    p.lineTo(sx(X), sy(Y));
                }
                g.draw(p);
            }
        }
        g.setClip(oldClip);
        g.setColor(new Color(0xd0d7de));
        g.setStroke(new BasicStroke(1f));
        g.draw(box);

        // ---- labels at their radii (right of the box)
        double lx = 15 + boxW + 12;
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        List<Object[]> labels = new ArrayList<>();
        if (nl >= 4) labels.add(new Object[]{0.5 * (rHead + rOut), "PEG corona", s.layers.get(3).thickness});
        if (nl >= 3) labels.add(new Object[]{rHead - tHead / 2, "headgroups", s.layers.get(2).thickness});
        if (nl >= 2) labels.add(new Object[]{r0 + tTail / 2, "lipid tails", s.layers.get(1).thickness});
        labels.add(new Object[]{r0 - Math.min(interiorDepth * 0.4, 80), "core", shortInterior()});
        double lastY = -1e9;
        for (Object[] L : labels) {
            double y = Math.max(sy((Double) L[0]), lastY + 34);
            lastY = y;
            g.setColor(new Color(0x8c959f));
            g.draw(new Line2D.Double(15 + boxW, sy((Double) L[0]), lx - 3, y - 4));
            g.setColor(INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString((String) L[1], (float) lx, (float) y);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(MUTED);
            g.drawString((String) L[2], (float) lx, (float) y + 14);
        }
        // legend
        double ly = h - 34;
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        double lgx = 18;
        lipidScreen(g, lgx + 6, ly - 12, 0, 1, 16, 4, true);
        g.setColor(INK);
        g.drawString("ionizable lipid", (float) (lgx + 16), (float) ly - 4);
        lipidScreen(g, lgx + 116, ly - 12, 0, 1, 16, 4, false);
        g.setColor(INK);
        g.drawString("helper lipid / cholesterol", (float) (lgx + 126), (float) ly - 4);
        if (s.cargoKind > 0) {
            double cxl = lgx + 300;
            if (s.cargoKind == 1) helixScreen(g, cxl, ly - 8, 34, 9, 0);
            else strandScreen(g, cxl, ly - 8, 34, 0, new Random(3));
            g.setColor(INK);
            g.drawString(s.cargoKind == 1 ? String.format("siRNA (%.0f bp, %.1f nm)", s.cargoLength, 0.28 * s.cargoLength)
                    : String.format("mRNA (%.0f nt)", s.cargoLength), (float) (cxl + 40), (float) ly - 4);
        }
        g.setColor(MUTED);
        g.drawString("Positions and spacings from the SAXS fit; individual molecules are illustrative.", 18, h - 12);
        scaleBar(g, cuScale, 15 + boxW - 10, 40 + boxH - 12);
    }

    private String shortInterior() {
        Spec s = spec;
        switch (s.interior) {
            case LAMELLAR: return String.format("lamellar, d = %.1f nm", s.spacing / 10);
            case HEXAGONAL: return String.format("HII, a = %.1f nm", s.spacing / 10);
            case CUBIC: return String.format("cubic %s, a = %.1f nm", s.symmetry, s.spacing / 10);
            case MESOPHASE: return String.format("disordered HII, %.1f nm", 2 * s.spacing / Math.sqrt(3) / 10);
            case COEXIST: return "two coexisting lattices";
            default: return "amorphous lipid core";
        }
    }

    private void drawCloseUpInterior(Graphics2D g, double r0, double bottom, double halfW, Random rnd) {
        Spec s = spec;
        double l = s.lipidLength, headR = 3.0;
        Interior kind = s.interior;
        double a = s.spacing;
        if (kind == Interior.COEXIST) { kind = s.periphery; a = s.spacing2; }
        switch (kind) {
            case LAMELLAR: {
                double wAq = Math.max(0.2 * a, a - 2 * l);
                double r = r0;
                while (r - l > bottom) {
                    // inner leaflet of the bilayer above: heads at r - l facing the aqueous layer, tails outward
                    ringOfLipids(g, r - l + headR, 1, l - headR, headR, halfW, rnd);
                    double mid = r - l - wAq / 2;
                    g.setColor(withAlpha(s.cargoKind > 0 ? RNA : WATER, 35));
                    Area aq = new Area(circleScreen(0, 0, r - l));
                    aq.subtract(new Area(circleScreen(0, 0, r - l - wAq)));
                    g.fill(aq);
                    cargoAlongArc(g, mid, halfW, wAq, rnd);
                    // outer leaflet of the next bilayer: heads at r - l - wAq, tails inward
                    ringOfLipids(g, r - l - wAq - headR, -1, l - headR, headR, halfW, rnd);
                    r = r - 2 * l - wAq;
                }
                break;
            }
            case HEXAGONAL: case CUBIC: case MESOPHASE: {
                double latt = kind == Interior.MESOPHASE ? 2 * a / Math.sqrt(3) : kind == Interior.CUBIC ? a / Math.sqrt(2) : a;
                double rw = kind == Interior.CUBIC ? cubicChannelRadius(a, s.symmetry) : hexChannelRadius(latt);
                List<double[]> pts = new ArrayList<>();
                if (kind == Interior.MESOPHASE) {
                    // domains with random orientation, as in disorderedHex
                    double dom = Double.isInfinite(s.correlation) ? 4 * latt : Math.max(1.5 * latt, s.correlation);
                    for (double cxD = -halfW - dom; cxD < halfW + dom; cxD += dom)
                        for (double cyD = bottom - dom; cyD < r0 + dom; cyD += dom) {
                            double rot = rnd.nextDouble() * Math.PI / 3;
                            addHexPoints(pts, cxD, cyD, dom / 2, latt, rot, 0.85 * latt);
                        }
                } else addHexPoints(pts, 0, r0 - rw - l, Math.max(halfW, r0 - bottom) + latt, latt, 0.2, 0.9 * latt);
                for (double[] p : pts) {
                    double rr = Math.hypot(p[0], p[1]);
                    if (rr > r0 - rw - 0.6 * l || p[1] < bottom - latt || Math.abs(p[0]) > halfW + latt) continue;
                    invertedMicelle(g, p[0], p[1], rw, l * 0.85, headR, rnd);
                }
                break;
            }
            default: {
                // amorphous core: randomly oriented lipids (oil-like), no long-range order
                int n = (int) (2 * halfW * (r0 - bottom) / 90);
                for (int i = 0; i < n; i++) {
                    double X = (2 * rnd.nextDouble() - 1) * halfW, Y = bottom + rnd.nextDouble() * (r0 - bottom);
                    if (Math.hypot(X, Y) > r0 - 4) continue;
                    double ang = rnd.nextDouble() * 2 * Math.PI;
                    lipid(g, X, Y, Math.cos(ang), Math.sin(ang), l * 0.8, 2.6, rnd.nextDouble() < s.ionizableFraction);
                }
            }
        }
    }

    private static void addHexPoints(List<double[]> pts, double cx, double cy, double half, double a, double rot, double dmin) {
        double c = Math.cos(rot), sn = Math.sin(rot);
        int n = (int) Math.ceil(half / a) + 1;
        for (int i = -n; i <= n; i++)
            for (int j = -n; j <= n; j++) {
                double x = a * (i + 0.5 * j), y = a * Math.sqrt(3) / 2 * j;
                if (Math.abs(x) > half || Math.abs(y) > half) continue;
                double X = cx + c * x - sn * y, Y = cy + sn * x + c * y;
                boolean ok = true;
                for (double[] p : pts) if (Math.hypot(p[0] - X, p[1] - Y) < dmin) { ok = false; break; }
                if (ok) pts.add(new double[]{X, Y});
            }
    }

    /** concentric ring of lipids at radius rHeadCentre; dir +1 = tails outward, -1 = tails inward */
    private void ringOfLipids(Graphics2D g, double rHeadCentre, int dir, double tail, double headR, double halfW, Random rnd) {
        if (rHeadCentre <= 0) return;
        double dth = 8.0 / rHeadCentre, span = (halfW + 30) / rHeadCentre;
        for (double th = -span; th <= span; th += dth) {
            double X = rHeadCentre * Math.sin(th), Y = rHeadCentre * Math.cos(th);
            lipid(g, X, Y, dir * Math.sin(th), dir * Math.cos(th), tail, headR, rnd.nextDouble() < spec.ionizableFraction);
        }
    }

    /** inverted micelle / channel: heads point into the aqueous channel (radius rw), tails outward; cargo inside */
    private void invertedMicelle(Graphics2D g, double X, double Y, double rw, double tail, double headR, Random rnd) {
        Spec s = spec;
        g.setColor(withAlpha(s.cargoKind > 0 ? RNA : WATER, 30));
        g.fill(circleScreen(X, Y, rw));
        double rc = rw + headR;
        int n = Math.max(6, (int) (2 * Math.PI * rc / 8.0));
        double off = rnd.nextDouble();
        for (int i = 0; i < n; i++) {
            double th = 2 * Math.PI * (i + off) / n;
            double ux = Math.cos(th), uy = Math.sin(th);
            lipid(g, X + rc * ux, Y + rc * uy, ux, uy, tail, headR, rnd.nextDouble() < s.ionizableFraction);
        }
        // cargo seen end-on in the channel: duplex cross-section (2.3 nm) or a strand loop
        if (s.cargoKind == 1) {
            double rd = Math.min(11.5, rw * 0.9);
            g.setColor(withAlpha(RNA, 200));
            g.fill(circleScreen(X, Y, rd));
            g.setColor(new Color(0x5b2c6f));
            g.setStroke(new BasicStroke((float) Math.max(0.8, 0.9 * cuScale / 3)));
            g.draw(circleScreen(X, Y, rd));
        } else if (s.cargoKind == 2) {
            g.setColor(RNA);
            g.setStroke(new BasicStroke((float) Math.max(1, 3.5 * cuScale / 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            double rr = Math.min(rw * 0.6, 9);
            double ph = rnd.nextDouble() * 6.28;
            for (int k = 0; k <= 30; k++) {
                double t = 2 * Math.PI * k / 30, rad = rr * (0.7 + 0.3 * Math.sin(3 * t + ph));
                double px = sx(X + rad * Math.cos(t)), py = sy(Y + rad * Math.sin(t));
                if (k == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
            g.draw(p);
        }
    }

    /** cargo lying in an aqueous layer at radius r (lamellar) */
    private void cargoAlongArc(Graphics2D g, double r, double halfW, double width, Random rnd) {
        Spec s = spec;
        if (s.cargoKind == 0 || r <= 0) return;
        if (s.cargoKind == 1) {
            double len = 2.8 * s.cargoLength, gap = len * 0.35;
            double th = -(halfW + 20) / r + rnd.nextDouble() * (len + gap) / r;
            while (th * r < halfW + 20) {
                double th2 = th + len / r;
                double mid = 0.5 * (th + th2);
                double X = r * Math.sin(mid), Y = r * Math.cos(mid);
                double ang = -mid;   // tangent direction on screen
                helixScreen(g, sx(X), sy(Y), len * cuScale, Math.min(23, 0.9 * width) * cuScale, ang);
                th = th2 + gap / r;
            }
        } else {
            // a long single strand meandering along the layer
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            double span = (halfW + 20) / r;
            int seg = (int) (2 * span * r / 3);
            double ph = rnd.nextDouble() * 6.28, amp = Math.min(0.3 * width, 8);
            for (int k = 0; k <= seg; k++) {
                double th = -span + 2 * span * k / seg;
                double rr = r + amp * Math.sin(th * r / 9 + ph) + 0.4 * amp * Math.sin(th * r / 3.7);
                double X = rr * Math.sin(th), Y = rr * Math.cos(th);
                if (k == 0) p.moveTo(sx(X), sy(Y)); else p.lineTo(sx(X), sy(Y));
            }
            g.setColor(RNA);
            g.setStroke(new BasicStroke((float) Math.max(1, 3.5 * cuScale / 3), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(p);
        }
    }

    /** lipid at head centre (X, Y) (particle frame, A), tails along (ux, uy) */
    private void lipid(Graphics2D g, double X, double Y, double ux, double uy, double tail, double headR, boolean ionizable) {
        double norm = Math.hypot(ux, uy);
        ux /= norm; uy /= norm;
        lipidScreen(g, sx(X), sy(Y), ux, -uy, tail * cuScale, headR * cuScale, ionizable);
    }

    /** lipid glyph in screen coordinates: head circle and two slightly wavy tails along (ux, uy) */
    private static void lipidScreen(Graphics2D g, double x, double y, double ux, double uy, double tail, double head, boolean dark) {
        double vx = -uy, vy = ux;
        g.setStroke(new BasicStroke((float) Math.max(0.6, head * 0.28), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x7a6a55));
        for (int side = -1; side <= 1; side += 2) {
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            double bx = x + ux * head * 0.9 + vx * side * head * 0.45, by = y + uy * head * 0.9 + vy * side * head * 0.45;
            p.moveTo(bx, by);
            int seg = 5;
            for (int k = 1; k <= seg; k++) {
                double t = (double) k / seg, wig = 0.18 * head * Math.sin(k * 2.2 + side);
                p.lineTo(bx + ux * tail * t + vx * wig, by + uy * tail * t + vy * wig);
            }
            g.draw(p);
        }
        Ellipse2D c = new Ellipse2D.Double(x - head, y - head, 2 * head, 2 * head);
        g.setColor(dark ? new Color(0x2b2b2b) : Color.WHITE);
        g.fill(c);
        g.setColor(new Color(0x2b2b2b));
        g.setStroke(new BasicStroke((float) Math.max(0.6, head * 0.22)));
        g.draw(c);
    }

    /** double helix (A-form RNA duplex) centred at (x, y), length and width in px, angle in radians */
    private static void helixScreen(Graphics2D g, double x, double y, double len, double wid, double ang) {
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(ang);
        double period = Math.max(6, len / Math.max(1, len / (wid * 1.3)));
        g.setStroke(new BasicStroke((float) Math.max(0.8, wid * 0.12), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int strand = 0; strand < 2; strand++) {
            java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
            for (int k = 0; k <= 60; k++) {
                double t = -len / 2 + len * k / 60;
                double yy = 0.5 * wid * Math.sin(2 * Math.PI * t / period + strand * Math.PI);
                if (k == 0) p.moveTo(t, yy); else p.lineTo(t, yy);
            }
            g.setColor(strand == 0 ? RNA : new Color(0xb57edc));
            g.draw(p);
        }
        g.setColor(new Color(0xb57edc));
        g.setStroke(new BasicStroke((float) Math.max(0.5, wid * 0.06)));
        for (double t = -len / 2 + period / 4; t < len / 2; t += period / 4)
            g.draw(new Line2D.Double(t, -0.5 * wid * Math.sin(2 * Math.PI * t / period), t, 0.5 * wid * Math.sin(2 * Math.PI * t / period)));
        g.setTransform(old);
    }

    private static void strandScreen(Graphics2D g, double x, double y, double len, double ang, Random rnd) {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        for (int k = 0; k <= 30; k++) {
            double t = -len / 2 + len * k / 30;
            double yy = 3 * Math.sin(t / 3.0) + 1.5 * Math.sin(t / 1.3);
            if (k == 0) p.moveTo(x + t, y + yy); else p.lineTo(x + t, y + yy);
        }
        g.setColor(RNA);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    private Ellipse2D circleScreen(double X, double Y, double r) {
        return new Ellipse2D.Double(sx(X) - r * cuScale, sy(Y) - r * cuScale, 2 * r * cuScale, 2 * r * cuScale);
    }

    // ================================================================== simulated cryo-EM field

    private void renderEm(Graphics2D g, int w, int h) {
        int iw = Math.max(200, w - 20), ih = Math.max(150, h - 70);
        if (emCache == null || emW != iw || emH != ih) {
            emCache = simulateField(iw, ih, new Random(seed));
            emW = iw; emH = ih;
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(INK);
        g.drawString("Simulated cryo-EM projection (from the SAXS model)", 14, 22);
        g.drawImage(emCache, 10, 34, null);
        if (!spec.population.isEmpty()) drawClassOverlay(g, 10, 34);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(MUTED);
        g.drawString("Particle radii drawn from the fitted distribution; internal lattices randomly oriented. Dark = electron dense.",
                14, h - 16);
        scaleBar(g, emScale, w - 30, 34 + ih - 14, Color.WHITE);
    }

    private double emScale;   // px per A of the cached field
    double emScaleValue() { return emScale; }
    List<double[]> emParticleList() { return emParticles; }

    /** particles of the cached field: {x, y, r px, kind[, x2, y2, r2 px for the bleb compartment]} */
    private final List<double[]> emParticles = new ArrayList<>();

    /** class-coloured outlines with ID labels and a legend, as in classified cryo-EM images */
    private void drawClassOverlay(Graphics2D g, int ox, int oy) {
        Font f = new Font("SansSerif", Font.BOLD, 11);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int id = 1;
        for (double[] p : emParticles) {
            LnpPopulations.Kind K = LnpPopulations.Kind.values()[(int) p[3]];
            Color c = K.color;
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
            g.fill(circle(ox + p[0], oy + p[1], p[2]));
            if (p.length > 4) g.fill(circle(ox + p[4], oy + p[5], p[6]));
            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.draw(circle(ox + p[0], oy + p[1], p[2]));
            if (p.length > 4) g.draw(circle(ox + p[4], oy + p[5], p[6]));
            String t = "ID:" + id++;
            double lx = ox + p[0] - fm.stringWidth(t) / 2.0, ly = oy + p[1] - p[2] - 6;
            g.setColor(c);
            g.fill(new java.awt.geom.RoundRectangle2D.Double(lx - 4, ly - fm.getAscent(), fm.stringWidth(t) + 8, fm.getHeight(), 6, 6));
            g.setColor(Color.WHITE);
            g.drawString(t, (float) lx, (float) ly - 1);
        }
        // legend with the SAXS number fractions
        int lx = ox + 10, ly = oy + 10, row = 18;
        int h = row * spec.population.size() + 26;
        g.setColor(new Color(255, 255, 255, 225));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(lx, ly, 250, h, 10, 10));
        g.setColor(INK);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("SAXS populations (number %)", lx + 10, ly + 17);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int i = 0;
        for (double[] pp : spec.population) {
            LnpPopulations.Kind K = LnpPopulations.Kind.values()[(int) pp[0]];
            int yy = ly + 26 + row * i++;
            g.setColor(K.color);
            g.fill(new Rectangle2D.Double(lx + 10, yy + 3, 12, 12));
            g.setColor(INK);
            g.drawString(K == LnpPopulations.Kind.AGGREGATE ? K.label + " (intensity only)" : K == LnpPopulations.Kind.FREE_RNA ? K.label
                    : String.format("%s  %.0f %%", K.label, 100 * pp[1]), lx + 28, yy + 14);
        }
    }

    /** projected electron density of several particles, darker = denser, with mild blur and noise */
    BufferedImage simulateField(int iw, int ih, Random rnd) {
        Spec s = spec;
        double rOut = s.layers.get(s.layers.size() - 1).outer;
        double rLarge = rOut + 2 * s.sigma;
        double scale = Math.min(iw, ih) / (2 * rLarge * 2.6);   // ~ 2.6 particle diameters across the short side
        emScale = scale;
        double[] proj = new double[iw * ih];
        // place particles without overlap; with populations each particle draws its class by number fraction
        List<double[]> placed = new ArrayList<>();   // {x, y, rc, footprint radius, kind, ratio, dirAngle}
        emParticles.clear();
        double[] cum = null;
        if (!s.population.isEmpty()) {
            cum = new double[s.population.size()];
            double t = 0;
            for (int i = 0; i < cum.length; i++) { t += s.population.get(i)[1]; cum[i] = t; }
            for (int i = 0; i < cum.length; i++) cum[i] /= t;
        }
        double shellT = rOut - s.r0;
        int target = (int) Math.max(5, Math.min(20, (iw * ih) / Math.pow(2 * rOut * scale * 1.4, 2)));
        for (int tries = 0; placed.size() < target && tries < 4000; tries++) {
            int kind = 0;
            double size = s.r0, sig = s.sigma / s.r0, ratio = 0;
            if (cum != null) {
                double u = rnd.nextDouble();
                int i = 0;
                while (i < cum.length - 1 && u > cum[i]) i++;
                double[] pp = s.population.get(i);
                kind = (int) pp[0]; size = pp[2]; sig = pp[3]; ratio = pp[4];
            }
            LnpPopulations.Kind K = LnpPopulations.Kind.values()[kind];
            if (K == LnpPopulations.Kind.AGGREGATE || K == LnpPopulations.Kind.MICELLE || K == LnpPopulations.Kind.FREE_RNA) continue;   // drawn separately
            double rc = size * (1 + sig * rnd.nextGaussian());
            if (rc < 0.4 * size) continue;
            double foot, ang = rnd.nextDouble() * 2 * Math.PI;
            switch (K) {
                case VESICLE: foot = rc + 46; break;
                case BLEB: {
                    double rl = rc + shellT, rv = ratio * rl + 46;
                    foot = 0.5 * (0.9 * (rl + rv) + rl + rv);
                    break;
                }
                default: foot = rc + shellT;
            }
            double x = foot * scale + rnd.nextDouble() * (iw - 2 * foot * scale), y = foot * scale + rnd.nextDouble() * (ih - 2 * foot * scale);
            boolean ok = x > 0 && y > 0;
            for (double[] p : placed) if (Math.hypot(p[0] - x, p[1] - y) < (p[3] + foot) * scale * 1.06) { ok = false; break; }
            if (ok) placed.add(new double[]{x, y, rc, foot, kind, ratio, ang});
        }
        for (double[] p : placed) {
            LnpPopulations.Kind K = LnpPopulations.Kind.values()[(int) p[4]];
            double x = p[0], y = p[1], rc = p[2];
            switch (K) {
                case EMPTY:
                    projectParticle(proj, iw, ih, x, y, rc, scale, rnd, Interior.UNIFORM, -0.005);
                    emParticles.add(new double[]{x, y, (rc + shellT) * scale, p[4]});
                    break;
                case VESICLE:
                    projectVesicle(proj, iw, ih, x, y, rc, scale);
                    emParticles.add(new double[]{x, y, (rc + 46) * scale, p[4]});
                    break;
                case BLEB: {
                    double rl = rc + shellT, rv = p[5] * rl + 46, D = 0.9 * (rl + rv);
                    // footprint centre (x, y) is the middle of the lobe-to-compartment extent along the axis
                    double back = 0.5 * (D + rv - rl);
                    double cxl = x - Math.cos(p[6]) * back * scale, cyl = y - Math.sin(p[6]) * back * scale;
                    double cxv = cxl + Math.cos(p[6]) * D * scale, cyv = cyl + Math.sin(p[6]) * D * scale;
                    projectVesicle(proj, iw, ih, cxv, cyv, rv - 46, scale);
                    projectParticle(proj, iw, ih, cxl, cyl, rc, scale, rnd);
                    emParticles.add(new double[]{cxl, cyl, rl * scale, p[4], cxv, cyv, rv * scale});
                    break;
                }
                default:
                    projectParticle(proj, iw, ih, x, y, rc, scale, rnd);
                    emParticles.add(new double[]{x, y, (rc + shellT) * scale, p[4]});
            }
        }
        // free micelles: many small dots in the background
        if (cum != null) for (double[] pp : s.population) {
            if ((int) pp[0] != LnpPopulations.Kind.MICELLE.ordinal()) continue;
            int n = (int) Math.min(400, 3 * pp[1] * 100);
            for (int i = 0; i < n; i++) {
                double x = rnd.nextDouble() * iw, y = rnd.nextDouble() * ih, rm = pp[2];
                projectBall(proj, iw, ih, x, y, rm, scale, -0.03);
            }
        }

        // blur (resolution ~ 6 A), normalise, noise
        proj = blur(proj, iw, ih, Math.max(0.6, 6 * scale));
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double v : proj) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        double span = hi - lo == 0 ? 1 : hi - lo;
        for (int i = 0; i < proj.length; i++) {
            double v = (proj[i] - lo) / span;                 // 0..1, 1 = densest
            double gray = 0.78 - 0.62 * v + 0.035 * rnd.nextGaussian();
            int c = (int) Math.round(255 * Math.max(0, Math.min(1, gray)));
            img.setRGB(i % iw, i / iw, (c << 16) | (c << 8) | c);
        }
        return img;
    }

    /** adds the projected contrast of one particle with core radius rc at pixel (px, py) */
    private void projectParticle(double[] proj, int iw, int ih, double px, double py, double rc, double scale, Random rnd) {
        projectParticle(proj, iw, ih, px, py, rc, scale, rnd, null, 0);
    }

    /** @param interiorOverride null = the spec's interior; coreShift added to the core contrast (empty particles) */
    private void projectParticle(double[] proj, int iw, int ih, double px, double py, double rc, double scale, Random rnd,
                                 Interior interiorOverride, double coreShift) {
        Spec s = spec;
        // shells: radial step profile, projected analytically: sum_k drho_k 2 sqrt(r_k^2 - b^2)
        int nl = s.layers.size();
        double[] r = new double[nl], drho = new double[nl];
        double shift = rc - s.layers.get(0).outer;
        for (int k = 0; k < nl; k++) {
            r[k] = s.layers.get(k).outer + shift;
            double inner = s.layers.get(k).rho + (k == 0 ? coreShift : 0), outer = k + 1 < nl ? s.layers.get(k + 1).rho : 0;
            drho[k] = inner - outer;
        }
        double ro = r[nl - 1];
        int x0 = (int) Math.max(0, px - ro * scale - 2), x1 = (int) Math.min(iw - 1, px + ro * scale + 2);
        int y0 = (int) Math.max(0, py - ro * scale - 2), y1 = (int) Math.min(ih - 1, py + ro * scale + 2);
        // internal contrast: aqueous/RNA compartments vs lipid matrix (scaled to the headgroup contrast)
        double dIn = s.cargo ? 0.05 : -0.03;
        Interior in = interiorOverride != null ? interiorOverride : s.interior;
        double a = s.spacing;
        double rot = rnd.nextDouble() * Math.PI;
        boolean endOn = rnd.nextBoolean();
        double c = Math.cos(rot), sn = Math.sin(rot);
        List<double[]> balls = null;
        if (in == Interior.MESOPHASE || in == Interior.CUBIC || in == Interior.COEXIST) {
            double sp = in == Interior.COEXIST ? Math.max(s.spacing, s.spacing2) : a;
            balls = poisson3d(rc, (in == Interior.MESOPHASE ? 0.9 : 1.0) * sp, rnd);
        }
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double bx = (x - px) / scale, by = (y - py) / scale, b = Math.hypot(bx, by);
                if (b >= ro) continue;
                double v = 0;
                for (int k = 0; k < nl; k++) if (b < r[k]) v += drho[k] * 2 * Math.sqrt(r[k] * r[k] - b * b);
                if (b < rc) {
                    double chord = 2 * Math.sqrt(rc * rc - b * b);
                    double u = c * bx + sn * by, w2 = -sn * bx + c * by;   // lattice frame
                    switch (in) {
                        case LAMELLAR: {
                            // onion of spheres: projected thickness of the aqueous shells is sum over shells of chords
                            double wAq = a - Math.min(0.8 * a, 2 * s.lipidLength), sum = 0;
                            for (double rr = rc; rr > 0.3 * a; rr -= a) sum += chordRing(b, Math.max(rr - wAq, 0), rr);
                            v += dIn * sum;
                            break;
                        }
                        case HEXAGONAL: {
                            double rw = hexChannelRadius(a);
                            if (endOn) {   // tubes along the beam: hexagons of dots
                                double dd = hexDistance(u, w2, a);
                                if (dd < rw) v += dIn * chord;
                            } else {       // tubes across the beam: straight lines with spacing sqrt3 a / 2
                                double d10 = Math.sqrt(3) / 2 * a;
                                double m = ((w2 % d10) + d10) % d10;
                                double off = Math.min(m, d10 - m);
                                if (off < rw) v += dIn * chord * (2 * Math.sqrt(rw * rw - off * off)) / d10;
                            }
                            break;
                        }
                        default:
                            break;   // ball-like compartments are added below, one ball at a time
                    }
                }
                proj[y * iw + x] += v;
            }
        if (balls != null) {
            double sp = in == Interior.COEXIST ? Math.max(s.spacing, s.spacing2) : a;
            double rb = in == Interior.CUBIC ? cubicChannelRadius(a, s.symmetry)
                    : hexChannelRadius(in == Interior.MESOPHASE ? 2 * a / Math.sqrt(3) : sp);
            for (double[] p : balls) {
                double bxc = px + p[0] * scale, byc = py + p[1] * scale, rpx = rb * scale;
                int xa = (int) Math.max(0, bxc - rpx - 1), xb = (int) Math.min(iw - 1, bxc + rpx + 1);
                int ya = (int) Math.max(0, byc - rpx - 1), yb = (int) Math.min(ih - 1, byc + rpx + 1);
                for (int y = ya; y <= yb; y++)
                    for (int x = xa; x <= xb; x++) {
                        double dx = (x - bxc) / scale, dy = (y - byc) / scale, d2 = dx * dx + dy * dy;
                        if (d2 < rb * rb) proj[y * iw + x] += dIn * 2 * Math.sqrt(rb * rb - d2);
                    }
            }
        }
    }

    /** unilamellar vesicle: water core rw, head 8 A (+0.085), tails 30 A (-0.035), head 8 A */
    private void projectVesicle(double[] proj, int iw, int ih, double px, double py, double rw, double scale) {
        double[] r = {rw, rw + 8, rw + 38, rw + 46};
        double[] rho = {0, 0.085, -0.035, 0.085};
        double ro = r[3];
        int x0 = (int) Math.max(0, px - ro * scale - 2), x1 = (int) Math.min(iw - 1, px + ro * scale + 2);
        int y0 = (int) Math.max(0, py - ro * scale - 2), y1 = (int) Math.min(ih - 1, py + ro * scale + 2);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double b = Math.hypot((x - px) / scale, (y - py) / scale);
                if (b >= ro) continue;
                double v = 0;
                for (int k = 1; k < 4; k++) v += rho[k] * chordRing(b, r[k - 1], r[k]);
                proj[y * iw + x] += v;
            }
    }

    private void projectBall(double[] proj, int iw, int ih, double px, double py, double rb, double scale, double rho) {
        int x0 = (int) Math.max(0, px - rb * scale - 1), x1 = (int) Math.min(iw - 1, px + rb * scale + 1);
        int y0 = (int) Math.max(0, py - rb * scale - 1), y1 = (int) Math.min(ih - 1, py + rb * scale + 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                double dx = (x - px) / scale, dy = (y - py) / scale, d2 = dx * dx + dy * dy;
                if (d2 < rb * rb) proj[y * iw + x] += rho * 2 * Math.sqrt(rb * rb - d2);
            }
    }

    /** projected length through the shell rin..rout at impact parameter b */
    private static double chordRing(double b, double rin, double rout) {
        double o = b < rout ? 2 * Math.sqrt(rout * rout - b * b) : 0;
        double i = b < rin ? 2 * Math.sqrt(rin * rin - b * b) : 0;
        return o - i;
    }

    /** distance from (x, y) to the nearest node of a hexagonal lattice with spacing a */
    private static double hexDistance(double x, double y, double a) {
        double h = Math.sqrt(3) / 2 * a;
        double j = Math.rint(y / h);
        double best = Double.POSITIVE_INFINITY;
        for (double jj = j - 1; jj <= j + 1; jj++) {
            double xs = x - 0.5 * a * jj;
            double i = Math.rint(xs / a);
            best = Math.min(best, Math.hypot(xs - i * a, y - jj * h));
        }
        return best;
    }

    // ================================================================== helpers

    static List<double[]> poisson2d(double rc, double dmin, Random rnd) {
        List<double[]> pts = new ArrayList<>();
        int target = (int) (0.75 * Math.PI * rc * rc / (dmin * dmin));
        for (int t = 0; t < 40 * target + 200 && pts.size() < target; t++) {
            double x = (2 * rnd.nextDouble() - 1) * rc, y = (2 * rnd.nextDouble() - 1) * rc;
            if (x * x + y * y > rc * rc) continue;
            boolean ok = true;
            for (double[] p : pts) if ((p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) < dmin * dmin) { ok = false; break; }
            if (ok) pts.add(new double[]{x, y});
        }
        return pts;
    }

    static List<double[]> poisson3d(double rc, double dmin, Random rnd) {
        List<double[]> pts = new ArrayList<>();
        int target = (int) Math.min(6000, 0.55 * rc * rc * rc / (dmin * dmin * dmin) * 4.19);
        int g = (int) Math.ceil(2 * rc / dmin) + 1;
        java.util.Map<Long, List<double[]>> grid = new java.util.HashMap<>();
        for (int t = 0; t < 30 * target + 200 && pts.size() < target; t++) {
            double x = (2 * rnd.nextDouble() - 1) * rc, y = (2 * rnd.nextDouble() - 1) * rc, z = (2 * rnd.nextDouble() - 1) * rc;
            if (x * x + y * y + z * z > rc * rc) continue;
            int ix = (int) ((x + rc) / dmin), iy = (int) ((y + rc) / dmin), iz = (int) ((z + rc) / dmin);
            boolean ok = true;
            for (int a = ix - 1; a <= ix + 1 && ok; a++)
                for (int b = iy - 1; b <= iy + 1 && ok; b++)
                    for (int c = iz - 1; c <= iz + 1 && ok; c++) {
                        List<double[]> cell = grid.get(((long) a * g + b) * g + c);
                        if (cell == null) continue;
                        for (double[] p : cell) {
                            double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
                            if (dx * dx + dy * dy + dz * dz < dmin * dmin) { ok = false; break; }
                        }
                    }
            if (!ok) continue;
            double[] p = {x, y, z};
            pts.add(p);
            grid.computeIfAbsent(((long) ix * g + iy) * g + iz, k -> new ArrayList<>()).add(p);
        }
        return pts;
    }

    private static double[] blur(double[] img, int w, int h, double sigma) {
        int rad = (int) Math.ceil(2.5 * sigma);
        double[] k = new double[2 * rad + 1];
        double sum = 0;
        for (int i = -rad; i <= rad; i++) { k[i + rad] = Math.exp(-0.5 * i * i / (sigma * sigma)); sum += k[i + rad]; }
        for (int i = 0; i < k.length; i++) k[i] /= sum;
        double[] tmp = new double[img.length], out = new double[img.length];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = 0;
                for (int i = -rad; i <= rad; i++) v += k[i + rad] * img[y * w + Math.max(0, Math.min(w - 1, x + i))];
                tmp[y * w + x] = v;
            }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = 0;
                for (int i = -rad; i <= rad; i++) v += k[i + rad] * tmp[Math.max(0, Math.min(h - 1, y + i)) * w + x];
                out[y * w + x] = v;
            }
        return out;
    }

    private void scaleBar(Graphics2D g, double scale, double right, double y) { scaleBar(g, scale, right, y, INK); }

    private void scaleBar(Graphics2D g, double scale, double right, double y, Color col) {
        double[] nice = {10, 20, 50, 100, 200, 500};
        double len = nice[0];
        for (double n : nice) if (n * scale < 140) len = n;   // A
        double px = len * scale;
        g.setColor(col);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Line2D.Double(right - px, y, right, y));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        String t = String.format("%.0f nm", len / 10);
        g.drawString(t, (float) (right - px / 2 - g.getFontMetrics().stringWidth(t) / 2.0), (float) (y - 6));
    }

    private static Ellipse2D circle(double cx, double cy, double r) { return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r); }

    private static Stroke dashed(float w) {
        return new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0);
    }

    private static Color withAlpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    private static Color blend(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + t * (b.getRed() - a.getRed())), (int) (a.getGreen() + t * (b.getGreen() - a.getGreen())),
                (int) (a.getBlue() + t * (b.getBlue() - a.getBlue())));
    }

    // ================================================================== export

    public void savePng(java.io.File f, int w, int h) throws java.io.IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        render(g, w, h);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", f);
    }

    /**
     * SVG of the vector views. The simulated micrograph is a bitmap: jfreesvg embeds bitmaps through javax.xml.bind,
     * missing from Java 11+, so that view is written as PNG next to the requested name. Returns the file written.
     */
    public java.io.File saveSvgOrPng(java.io.File f, int w, int h) throws java.io.IOException {
        if (view == View.CRYO_EM) {
            java.io.File png = new java.io.File(f.getParentFile(), f.getName().replaceAll("(?i)\\.svg$", "") + ".png");
            savePng(png, w, h);
            return png;
        }
        saveSvg(f, w, h);
        return f;
    }

    public void saveSvg(java.io.File f, int w, int h) throws java.io.IOException {
        org.jfree.graphics2d.svg.SVGGraphics2D g = new org.jfree.graphics2d.svg.SVGGraphics2D(w, h);
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(0, 0, w, h));
        render(g, w, h);
        org.jfree.graphics2d.svg.SVGUtils.writeToSVG(f, g.getSVGElement());
    }
}
