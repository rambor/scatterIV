package version4.FormFactors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive cartoon of the current model for the Model section: a quarter cross-section of the particle, one
 * coloured band per layer. Thin layers are widened so they can be clicked (the cartoon is schematic, the numbers are
 * not). Hover shows a layer's values; a click opens an editor for its thickness, density and whether each is fitted;
 * dragging a boundary resizes that layer. Every change is reported to a listener, which updates the parameter table
 * and recalculates the model curve.
 */
final class LnpModelEditor extends JComponent {

    /** what the editor reads and writes; implemented by the panel */
    interface Host {
        LnpModel model();
        double[] params();
        boolean[] fixed();
        double[] errors();
        /** a parameter value or its fit flag changed; {@code done} = end of a drag / editor closed (recalculate now) */
        void changed(boolean done);
        String paramName(int k);
    }

    private final Host host;
    // geometry of the last paint: band boundaries in px from the centre (index 0 = core edge), centre point
    private double[] edgePx = new double[0];
    private double cx, cy;
    private int hoverLayer = -1, dragEdge = -1;
    private double dragStartPx, dragStartVal;

    private static final Color INK = new Color(0x24292f), MUTED = new Color(0x6e7781);
    static final Color[] LNP_COLORS = {new Color(0xf7d892), new Color(0xf0b44c), new Color(0x2f9e72), new Color(0xb9dcf3)};
    static final Color[] VES_COLORS = {new Color(0xdcecf8), new Color(0x2f9e72), new Color(0xf0b44c), new Color(0x2f9e72), new Color(0xb9dcf3)};

    LnpModelEditor(Host host) {
        this.host = host;
        setPreferredSize(new Dimension(380, 250));
        setToolTipText("");
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int edge = edgeAt(e.getPoint());
                setCursor(edge >= 0 ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : layerAt(e.getPoint()) >= 0
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                int l = layerAt(e.getPoint());
                if (l != hoverLayer) { hoverLayer = l; repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { hoverLayer = -1; repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                dragEdge = edgeAt(e.getPoint());
                if (dragEdge >= 0) {
                    dragStartPx = dist(e.getPoint());
                    dragStartVal = host.params()[edgeParam(dragEdge)];
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragEdge < 0) return;
                int p = edgeParam(dragEdge);
                double width = dragEdge == 0 ? edgePx[0] : edgePx[dragEdge] - edgePx[dragEdge - 1];
                double perPx = dragStartVal / Math.max(6, width);           // A per px of this band
                double v = dragStartVal + (dist(e.getPoint()) - dragStartPx) * perPx;
                host.params()[p] = Math.max(p == LnpModel.R0 ? 10 : 1, Math.round(v * 10) / 10.0);
                host.errors()[p] = Double.NaN;
                host.changed(false);
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (dragEdge >= 0) { dragEdge = -1; host.changed(true); return; }
            }
            @Override public void mouseClicked(MouseEvent e) {
                int l = layerAt(e.getPoint());
                if (l >= 0 && edgeAt(e.getPoint()) < 0) edit(l, e.getPoint());
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** parameter changed by dragging boundary i (0 = core radius, k = thickness of shell k) */
    private static int edgeParam(int i) { return i == 0 ? LnpModel.R0 : LnpModel.tIndex(i); }

    private double dist(Point p) { return Math.hypot(p.x - cx, cy - p.y); }

    private int layerAt(Point p) {
        double r = dist(p);
        if (p.x < cx - 2 || p.y > cy + 2) return -1;   // outside the quarter
        for (int i = 0; i < edgePx.length; i++) if (r <= edgePx[i]) return i;
        return -1;
    }

    private int edgeAt(Point p) {
        if (p.x < cx - 2 || p.y > cy + 2) return -1;
        double r = dist(p);
        for (int i = 0; i < edgePx.length; i++) if (Math.abs(r - edgePx[i]) <= 4) return i;
        return -1;
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        LnpModel m = host.model();
        double[] p = host.params();
        boolean[] fx = host.fixed();
        String[] names = m.layerNames();
        int n = m.shells + 1;
        Color[] col = m.isVesicle() ? VES_COLORS : LNP_COLORS;

        // schematic radii: the core gets ~55 % of the radius; each shell is at least 13 px wide and otherwise
        // proportional to its thickness relative to the core radius
        double avail = Math.min(w * 0.58, h - 30);
        double rCore = p[LnpModel.R0];
        double[] t = new double[n];
        for (int k = 1; k < n; k++) t[k] = p[LnpModel.tIndex(k)];
        double shellTot = 0;
        for (int k = 1; k < n; k++) shellTot += t[k];
        double corePx = avail * (n == 1 ? 0.95 : 0.55);
        double rest = avail - corePx;
        edgePx = new double[n];
        edgePx[0] = corePx;
        for (int k = 1; k < n; k++) {
            double share = shellTot > 0 ? t[k] / shellTot : 1.0 / (n - 1);
            edgePx[k] = edgePx[k - 1] + Math.max(13, rest * share);
        }
        double scaleBack = avail / edgePx[n - 1];
        for (int k = 0; k < n; k++) edgePx[k] *= scaleBack;
        cx = 14;
        cy = h - 16;

        // bands, outside in
        for (int k = n - 1; k >= 0; k--) {
            Color c = k == 0 && !m.isVesicle() ? coreColor(p[LnpModel.RHO_C]) : col[Math.min(k, col.length - 1)];
            if (k == hoverLayer) c = c.darker();
            g.setColor(c);
            g.fill(quarter(edgePx[k]));
        }
        // core interior: RNA mesophase dots when the peak is part of the model
        if (m.peak) {
            Shape clip = g.getClip();
            g.clip(quarter(edgePx[0] - 4));
            g.setColor(new Color(0x8e44ad));
            double step = 15;
            for (double yy = cy - step / 2; yy > cy - edgePx[0]; yy -= step * 0.87)
                for (double xx = cx + ((int) ((cy - yy) / (step * 0.87)) % 2) * step / 2 + 6; xx < cx + edgePx[0]; xx += step)
                    g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6));
            g.setClip(clip);
        }
        // PEG brushes
        int peg = names.length - 1;
        if (names[peg].contains("PEG")) {
            g.setColor(new Color(0x6aaed6));
            g.setStroke(new BasicStroke(1f));
            for (int i = 1; i < 10; i++) {
                double a = Math.PI / 2 * i / 10;
                double r0 = edgePx[peg - 1], r1 = edgePx[peg];
                java.awt.geom.Path2D path = new java.awt.geom.Path2D.Double();
                for (int s = 0; s <= 8; s++) {
                    double rr = r0 + (r1 - r0) * 0.9 * s / 8, aa = a + 0.035 * Math.sin(s * 1.7);
                    double x = cx + rr * Math.cos(aa), y = cy - rr * Math.sin(aa);
                    if (s == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                g.draw(path);
            }
        }
        // boundaries: solid when the thickness (radius) is fitted, dashed when fixed
        for (int k = 0; k < n; k++) {
            int pp = edgeParam(k);
            g.setColor(k == dragEdge ? new Color(0x2563eb) : new Color(0, 0, 0, 120));
            g.setStroke(fx[pp] ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 3}, 0)
                    : new BasicStroke(k == dragEdge ? 2.2f : 1.4f));
            g.draw(new Arc2D.Double(cx - edgePx[k], cy - edgePx[k], 2 * edgePx[k], 2 * edgePx[k], 0, 90, Arc2D.OPEN));
        }
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(cx, cy, cx + edgePx[n - 1] + 4, cy));
        g.draw(new Line2D.Double(cx, cy, cx, cy - edgePx[n - 1] - 4));

        // labels to the right, with leader lines to the band
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm = g.getFontMetrics();
        double lx = cx + avail + 18, rowH = Math.min(40, (h - 20) / (double) n);
        for (int k = n - 1; k >= 0; k--) {
            int row = n - 1 - k;
            double ly = 14 + row * rowH;
            double mid = k == 0 ? edgePx[0] * 0.6 : 0.5 * (edgePx[k] + edgePx[k - 1]);
            double ang = Math.toRadians(70 - 55.0 * row / Math.max(1, n - 1));
            double ax = cx + mid * Math.cos(ang), ay = cy - mid * Math.sin(ang);
            g.setColor(new Color(0x8c959f));
            g.draw(new Line2D.Double(ax, ay, lx - 4, ly + 4));
            g.fill(new Ellipse2D.Double(ax - 2, ay - 2, 4, 4));
            g.setColor(k == hoverLayer ? new Color(0x2563eb) : INK);
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            g.drawString(names[k], (float) lx, (float) ly);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(MUTED);
            String size = k == 0 ? String.format("R₀ %.1f nm  σ %.1f", p[LnpModel.R0] / 10, p[LnpModel.SIGMA] / 10)
                    : String.format("%.1f nm%s", p[LnpModel.tIndex(k)] / 10, fx[LnpModel.tIndex(k)] ? " (fixed)" : "");
            String dens = String.format("Δρ %+.3f%s", p[LnpModel.rhoIndex(k)], fx[LnpModel.rhoIndex(k)] ? " (fixed)" : "");
            g.drawString(size, (float) lx, (float) ly + fm.getHeight() - 1);
            if (rowH >= 30) g.drawString(dens, (float) lx, (float) ly + 2 * fm.getHeight() - 2);
        }
        g.setColor(MUTED);
        g.setFont(new Font("SansSerif", Font.ITALIC, 10));
        g.drawString("Click a layer to edit · drag a boundary to resize · schematic, not to scale", 4, h - 2);
        g.dispose();
    }

    private Shape quarter(double r) {
        return new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 0, 90, Arc2D.PIE);
    }

    private static Color coreColor(double rho) { return LnpCartoon.densityColor(rho).equals(Color.WHITE) ? LNP_COLORS[0] : blend(LNP_COLORS[0], LnpCartoon.densityColor(rho), 0.35); }

    private static Color blend(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + t * (b.getRed() - a.getRed())), (int) (a.getGreen() + t * (b.getGreen() - a.getGreen())),
                (int) (a.getBlue() + t * (b.getBlue() - a.getBlue())));
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int edge = edgeAt(e.getPoint());
        LnpModel m = host.model();
        if (edge >= 0) return "Drag to change " + host.paramName(edgeParam(edge));
        int l = layerAt(e.getPoint());
        if (l < 0) return null;
        StringBuilder sb = new StringBuilder("<html><b>").append(m.layerNames()[l]).append("</b>");
        for (int k : layerParams(m, l)) sb.append("<br>").append(describe(k));
        return sb.append("<br><i>click to edit</i></html>").toString();
    }

    private String describe(int k) {
        double[] p = host.params(), e = host.errors();
        boolean f = host.fixed()[k];
        String err = !f && !Double.isNaN(e[k]) ? " ± " + Fields.fmt(e[k]) : "";
        return host.paramName(k) + ": " + Fields.fmt(p[k]) + err + " " + LnpModel.UNITS[k] + (f ? " (fixed)" : "");
    }

    /** parameters that belong to layer l (core also carries size spread and the RNA peak) */
    static List<Integer> layerParams(LnpModel m, int l) {
        List<Integer> ks = new ArrayList<>();
        if (l == 0) {
            ks.add(LnpModel.R0);
            ks.add(LnpModel.SIGMA);
            ks.add(LnpModel.RHO_C);
            if (m.peak) { ks.add(LnpModel.PEAK_Q); ks.add(LnpModel.PEAK_W); ks.add(LnpModel.PEAK_A); }
        } else {
            ks.add(LnpModel.tIndex(l));
            ks.add(LnpModel.rhoIndex(l));
        }
        return ks;
    }

    // ------------------------------------------------------------------ editor popup

    private void edit(int layer, Point at) {
        LnpModel m = host.model();
        List<Integer> ks = layerParams(m, layer);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 10, 8, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridy = 0;
        c.gridx = 0;
        c.gridwidth = 4;
        JLabel title = new JLabel(m.layerNames()[layer]);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        form.add(title, c);
        c.gridwidth = 1;
        List<JTextField> fields = new ArrayList<>();
        List<JCheckBox> fits = new ArrayList<>();
        JTextField dField = null;
        for (int k : ks) {
            c.gridy++;
            c.gridx = 0;
            form.add(new JLabel(host.paramName(k)), c);
            JTextField tf = Fields.num(Fields.fmt(host.params()[k]));
            tf.setColumns(8);
            c.gridx = 1;
            form.add(tf, c);
            c.gridx = 2;
            form.add(new JLabel(LnpModel.UNITS[k]), c);
            JCheckBox fit = new JCheckBox("fit", !host.fixed()[k]);
            fit.setToolTipText("Refine this parameter when you press Fit; unticked = held at this value");
            c.gridx = 3;
            form.add(fit, c);
            tf.setToolTipText(LnpModel.TIPS[k]);
            fields.add(tf);
            fits.add(fit);
            if (k == LnpModel.PEAK_Q) {
                // the repeat distance is easier to think in than q*
                c.gridy++;
                c.gridx = 0;
                form.add(new JLabel("  = repeat distance d"), c);
                dField = Fields.num(String.format("%.2f", 2 * Math.PI / host.params()[k] / 10));
                dField.setColumns(8);
                c.gridx = 1;
                form.add(dField, c);
                c.gridx = 2;
                form.add(new JLabel("nm"), c);
                final JTextField qf = tf, df = dField;
                df.addActionListener(ev -> {
                    try { qf.setText(String.format("%.5f", 2 * Math.PI / (Double.parseDouble(df.getText().trim()) * 10))); }
                    catch (NumberFormatException ignored) { }
                });
            }
        }
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 4;
        JLabel hint = new JLabel("<html><div style='width:260px'>" + layerHint(m, layer) + "</div></html>");
        hint.setForeground(MUTED);
        form.add(hint, c);

        int ok = JOptionPane.showConfirmDialog(this, form, "Edit layer", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;
        if (dField != null) {
            try {
                double d = Double.parseDouble(dField.getText().trim());
                double qNow = Double.parseDouble(fields.get(ks.indexOf(LnpModel.PEAK_Q)).getText().trim());
                if (Math.abs(2 * Math.PI / (d * 10) - qNow) > 1e-4 * qNow && Math.abs(2 * Math.PI / qNow / 10 - d) > 0.005)
                    fields.get(ks.indexOf(LnpModel.PEAK_Q)).setText(String.valueOf(2 * Math.PI / (d * 10)));
            } catch (NumberFormatException ignored) { }
        }
        List<String> rejected = new ArrayList<>(), zeroed = new ArrayList<>();
        for (int i = 0; i < ks.size(); i++) {
            int k = ks.get(i);
            try {
                double v = Double.parseDouble(fields.get(i).getText().trim());
                // zero thickness removes the layer, which a sample with no PEG-lipid needs and the vesicle
                // architecture offers no other way to express; it is only unidentifiable while being fitted, so
                // the layer is held fixed along with its density
                if (LnpModel.isThickness(k) && v == 0) {
                    host.params()[k] = 0;
                    host.fixed()[k] = true;
                    host.errors()[k] = Double.NaN;
                    fits.get(i).setSelected(false);
                    zeroed.add(host.paramName(k));
                    continue;
                }
                if (LnpModel.POSITIVE[k] && !(v > 0)) {
                    rejected.add(host.paramName(k) + " must be greater than 0, or exactly 0 to remove the layer. "
                            + "Kept " + Fields.fmt(host.params()[k]) + ".");
                    continue;
                }
                if (v != host.params()[k]) { host.params()[k] = v; host.errors()[k] = Double.NaN; }
            } catch (NumberFormatException ignored) { }
            host.fixed()[k] = !fits.get(i).isSelected();
        }
        if (!zeroed.isEmpty())
            JOptionPane.showMessageDialog(this, "Removed from the model (thickness 0, held fixed):\n  "
                            + String.join("\n  ", zeroed), "Layer removed", JOptionPane.INFORMATION_MESSAGE);
        if (!rejected.isEmpty())
            JOptionPane.showMessageDialog(this, String.join("\n", rejected), "Value not applied", JOptionPane.WARNING_MESSAGE);
        host.changed(true);
        repaint();
    }

    private static String layerHint(LnpModel m, int layer) {
        String n = m.layerNames()[layer];
        if (layer == 0 && m.isVesicle()) return "Aqueous interior of the vesicle; its density equals the buffer.";
        if (layer == 0) return "Δρ is the electron density relative to the buffer (e/Å³). σ sets the size spread; σ/R₀ above ~0.2 washes out the oscillations."
                + (m.peak ? " q* and w describe the internal RNA–lipid peak; d = 2π/q*." : "");
        if (n.contains("PEG")) return "Hydrated PEG corona: thick but faint. Usually held fixed (PEG2000 ≈ 3.4–4 nm).";
        if (n.contains("head")) return "Phosphocholine headgroups: thin and denser than the buffer (about +0.08 e/Å³).";
        if (n.contains("chain") || n.contains("lipid")) return "Hydrocarbon chains (with cholesterol): less dense than the buffer. One density is the reference of the scale and stays fixed.";
        return "";
    }
}
