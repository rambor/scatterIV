package version4.FormFactors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Indexing by eye, after Fig. 4A of Caselli, Conti, De Santis &amp; Berti (Adv. Colloid Interface Sci. 327, 103156
 * (2024)): the measured curve with the allowed reflections of a chosen phase drawn on it as labelled sticks, and a
 * sketch of that phase beside them. Dragging the sticks (or turning the wheel over them) changes the lattice
 * parameter, so the phase is assigned by sliding its pattern onto the peaks; "Snap to peaks" then refines the
 * parameter by least squares against the detected peaks.
 */
final class LnpIndexer extends JPanel {

    interface Host {
        /** {q, I, sigma} of the current dataset, or null */
        double[][] data();
        /** peaks found by the assessment, or an empty list */
        List<LnpAssessment.Peak> peaks();
        /** called when the user accepts: sets up the lattice fit with this phase and parameter */
        void accept(LnpLattice.Lattice lattice, double a);
        double lipidLength();
    }

    private final Host host;
    private final Plot plot = new Plot();
    private final LnpPhaseInset inset = new LnpPhaseInset();
    private final LnpIndexCheck check = new LnpIndexCheck();
    private final JComboBox<LnpLattice.Lattice> phaseBox = new JComboBox<>(LnpLattice.LATTICES);
    private final JTextField aField = Fields.num("100");
    private final JLabel readout = new JLabel(" ");
    private LnpLattice.Lattice lattice = LnpLattice.LATTICES[0];
    private double a = 100;

    LnpIndexer(Host host) {
        super(new BorderLayout(6, 6));
        this.host = host;
        phaseBox.addActionListener(e -> {
            lattice = (LnpLattice.Lattice) phaseBox.getSelectedItem();
            update();
        });
        aField.setColumns(7);
        aField.addActionListener(e -> {
            try { setA(Double.parseDouble(aField.getText().trim())); } catch (NumberFormatException ignored) { }
        });
        JButton snap = new JButton("Snap to peaks"), use = new JButton("Use in the lattice fit"), auto = new JButton("Best phase");
        snap.setToolTipText("Refine the lattice parameter so the sticks sit on the detected peaks");
        snap.addActionListener(e -> snapToPeaks());
        auto.setToolTipText("Try every phase against the detected peaks and pick the one that explains them best");
        auto.addActionListener(e -> bestPhase());
        use.addActionListener(e -> host.accept(lattice, a));
        JPanel bar = Fields.flow(new JLabel("Phase"), phaseBox, new JLabel("a (Å)"), aField, snap, auto, use);
        bar.setBorder(new EmptyBorder(2, 4, 2, 4));
        JPanel north = new JPanel(new BorderLayout());
        JLabel help = Fields.wrappingLabel(north, "<span style='color:#6e7781'>Drag the sticks sideways (or scroll over the plot) "
                + "to slide the pattern of this phase onto the peaks; every reflection moves together because they share one lattice "
                + "parameter. The sketch on the right is that phase at the current parameter.</span>");
        help.setBorder(new EmptyBorder(0, 6, 4, 6));
        north.add(bar, BorderLayout.NORTH);
        north.add(help, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(plot, BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout());
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(inset);
        stack.add(check);
        right.add(stack, BorderLayout.NORTH);
        readout.setVerticalAlignment(SwingConstants.TOP);
        readout.setBorder(new EmptyBorder(6, 6, 6, 6));
        JScrollPane rs = new JScrollPane(readout, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        rs.setBorder(null);
        right.add(rs, BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(250, 100));
        add(right, BorderLayout.EAST);
        update();
    }

    /** Called when the indexer becomes visible, so it picks up a change of dataset or a new peak list. */
    void refresh() {
        update();
    }

    void setPhase(LnpLattice.Lattice l, double a) {
        lattice = l;
        phaseBox.setSelectedItem(l);
        setA(a);
    }

    private void setA(double v) {
        if (!(v > 1)) return;
        a = v;
        aField.setText(String.format("%.1f", a));
        update();
    }

    /** least-squares refinement of a against the detected peaks that each reflection can claim */
    private void snapToPeaks() {
        List<LnpAssessment.Peak> pk = host.peaks();
        if (pk.isEmpty()) { readout.setText("<html><i>No peaks were detected: run the assessment first.</i></html>"); return; }
        double num = 0, den = 0;
        int used = 0;
        for (double m : lattice.m) {
            double qc = 2 * Math.PI * m / a;
            LnpAssessment.Peak best = null;
            for (LnpAssessment.Peak p : pk)
                if (Math.abs(p.q - qc) / qc < 0.06 && (best == null || Math.abs(p.q - qc) < Math.abs(best.q - qc))) best = p;
            if (best == null) continue;
            // a = 2 pi m / q, weighted by the peak's significance
            double w = Math.max(1, best.significance);
            num += w * 2 * Math.PI * m / best.q;
            den += w;
            used++;
        }
        if (used == 0) { readout.setText("<html><i>No peak is close to a reflection of this phase. Slide the pattern nearer first.</i></html>"); return; }
        setA(num / den);
        readout.setText(readout.getText().replace("</html>", "") + "<br><b>Snapped using " + used + " peak(s).</b></html>");
    }

    /** scores every phase by how well its reflections land on the detected peaks */
    private void bestPhase() {
        List<LnpAssessment.Peak> pk = host.peaks();
        if (pk.size() < 2) { readout.setText("<html><i>At least two peaks are needed. Run the assessment first.</i></html>"); return; }
        LnpLattice.Lattice bestL = null;
        double bestA = a, bestScore = Double.POSITIVE_INFINITY;
        LnpAssessment.Peak first = pk.get(0);
        for (LnpAssessment.Peak p : pk) if (p.area > first.area) first = p;
        for (LnpLattice.Lattice l : LnpLattice.LATTICES)
            for (int k = 0; k < Math.min(3, l.m.length); k++) {
                double aTry = 2 * Math.PI * l.m[k] / first.q;
                // mean relative distance of each peak from its nearest reflection, plus a penalty for peaks missed
                double err = 0;
                int matched = 0;
                for (LnpAssessment.Peak p : pk) {
                    double best = Double.POSITIVE_INFINITY;
                    for (double m : l.m) best = Math.min(best, Math.abs(p.q - 2 * Math.PI * m / aTry) / p.q);
                    if (best < 0.04) { err += best; matched++; }
                }
                if (matched < 2) continue;
                double score = err / matched + 0.05 * (pk.size() - matched);
                if (score < bestScore) { bestScore = score; bestL = l; bestA = aTry; }
            }
        if (bestL == null) { readout.setText("<html><i>No phase lines up with these peaks.</i></html>"); return; }
        lattice = bestL;
        phaseBox.setSelectedItem(bestL);
        setA(bestA);
    }

    private void update() {
        inset.set(lattice, a, host.lipidLength());
        check.set(lattice, a, host.peaks(), 0.03);
        StringBuilder sb = new StringBuilder("<html><b>Reflections</b><br><table cellpadding=1>");
        double[][] d = host.data();
        double qmax = d == null ? 0.5 : d[0][d[0].length - 1];
        List<LnpAssessment.Peak> pk = host.peaks();
        for (int k = 0; k < lattice.m.length; k++) {
            double q = 2 * Math.PI * lattice.m[k] / a;
            if (q > qmax) break;
            String mark = "";
            for (LnpAssessment.Peak p : pk) if (Math.abs(p.q - q) / q < 0.03) mark = " <font color='#16a34a'>●</font>";
            sb.append(String.format("<tr><td>%s</td><td>%.4f</td><td>%.1f Å</td><td>%s</td></tr>",
                    lattice.hkl[k], q, 2 * Math.PI / q, mark));
        }
        sb.append("</table>");
        if (!pk.isEmpty()) {
            int hit = 0;
            for (LnpAssessment.Peak p : pk) {
                for (double m : lattice.m) if (Math.abs(p.q - 2 * Math.PI * m / a) / p.q < 0.03) { hit++; break; }
            }
            sb.append(String.format("<br>%d of %d detected peaks are explained.", hit, pk.size()));
        }
        LnpIndexCheck.Fit cf = check.fit();
        if (cf != null && cf.points.size() >= 3)
            sb.append(cf.aSpread / cf.a > 0.02
                    ? String.format("<br><font color='#dc2626'>The reflections are not collinear: %s implies a = %.1f Å.</font>",
                    cf.worstHkl, cf.a + (cf.worst))
                    : String.format("<br><font color='#16a34a'>Collinear through the origin: a = %.1f ± %.1f Å.</font>", cf.a, cf.aSpread));
        double rw = lattice.lamellar ? a - 2 * host.lipidLength()
                : lattice.hexagonal ? a / 2 - host.lipidLength() : cubicChannel();
        if (rw > 0) sb.append(String.format("<br><br>%s ≈ %.1f nm", lattice.lamellar ? "Water layer" : "Channel radius", rw / 10));
        sb.append("</html>");
        readout.setText(sb.toString());
        plot.repaint();
    }

    private double cubicChannel() {
        double a0 = 1.919, chi = -2;
        if (lattice.symmetry.startsWith("Im3m")) { a0 = 2.345; chi = -4; }
        else if (lattice.symmetry.startsWith("Ia3d")) { a0 = 3.091; chi = -8; }
        else if (!lattice.cubic) return Double.NaN;
        return a * Math.sqrt(-a0 / (2 * Math.PI * chi)) - host.lipidLength();
    }

    // ------------------------------------------------------------------ the plot

    private final class Plot extends JComponent {
        private double lx0, lx1, ly0, ly1;   // log10 ranges actually drawn
        private Rectangle box = new Rectangle();
        private boolean dragging;
        private double dragA, dragQ;

        Plot() {
            setPreferredSize(new Dimension(620, 330));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (!box.contains(e.getPoint())) return;
                    dragging = true;
                    dragA = a;
                    dragQ = qAt(e.getX());
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!dragging || dragQ <= 0) return;
                    double q = qAt(e.getX());
                    if (q > 0) setA(Math.max(5, dragA * dragQ / q));
                }
                @Override public void mouseReleased(MouseEvent e) { dragging = false; }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    setA(Math.max(5, a * Math.pow(1.01, e.getWheelRotation())));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
            setToolTipText("Drag sideways to slide the pattern; scroll to adjust finely");
        }

        double qAt(int px) {
            if (box.width <= 0) return -1;
            double f = (px - box.x) / (double) box.width;
            return Math.pow(10, lx0 + f * (lx1 - lx0));
        }

        int xOf(double q) { return (int) Math.round(box.x + box.width * (Math.log10(q) - lx0) / (lx1 - lx0)); }

        int yOf(double i) { return (int) Math.round(box.y + box.height * (1 - (Math.log10(i) - ly0) / (ly1 - ly0))); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            double[][] d = host.data();
            box = new Rectangle(52, 10, getWidth() - 66, getHeight() - 46);
            if (d == null || d[0].length < 2) {
                g.setColor(LnpCartoon.MUTED);
                g.drawString("Select a dataset", 60, getHeight() / 2);
                g.dispose();
                return;
            }
            double[] q = d[0], I = d[1];
            lx0 = Math.log10(q[0]);
            lx1 = Math.log10(q[q.length - 1]);
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (double v : I) if (v > 0) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            ly0 = Math.log10(lo);
            ly1 = Math.log10(hi);
            g.setColor(new Color(0xf6f8fa));
            g.fill(box);
            // axes
            g.setColor(new Color(0xd0d7de));
            g.draw(box);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (double e = Math.ceil(lx0); e <= lx1; e++) {
                int x = xOf(Math.pow(10, e));
                g.setColor(new Color(0xe6eaef));
                g.drawLine(x, box.y, x, box.y + box.height);
                g.setColor(LnpCartoon.MUTED);
                g.drawString(String.format("%.3g", Math.pow(10, e)), x - 10, box.y + box.height + 14);
            }
            g.setColor(LnpCartoon.MUTED);
            g.drawString("q (Å⁻¹)", box.x + box.width / 2 - 18, getHeight() - 6);
            g.drawString("I(q)", 6, box.y + 12);
            // data
            g.setColor(new Color(0x6e7781));
            Path2D p = new Path2D.Double();
            boolean started = false;
            for (int j = 0; j < q.length; j++) {
                if (!(I[j] > 0)) continue;
                int x = xOf(q[j]), y = yOf(I[j]);
                if (!started) { p.moveTo(x, y); started = true; } else p.lineTo(x, y);
            }
            g.setStroke(new BasicStroke(1.2f));
            g.draw(p);
            // detected peaks
            g.setColor(new Color(0x16a34a));
            for (LnpAssessment.Peak pk : host.peaks()) {
                int x = xOf(pk.q);
                g.fill(new java.awt.geom.Ellipse2D.Double(x - 3, box.y + box.height - 6, 6, 6));
            }
            // the sticks of the chosen phase
            Shape clip = g.getClip();
            g.clip(box);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            int[] labelX = new int[6];
            java.util.Arrays.fill(labelX, Integer.MIN_VALUE / 2);
            for (int k = 0; k < lattice.m.length; k++) {
                double qc = 2 * Math.PI * lattice.m[k] / a;
                if (qc < Math.pow(10, lx0) || qc > Math.pow(10, lx1)) continue;
                int x = xOf(qc);
                boolean onPeak = false;
                for (LnpAssessment.Peak pk : host.peaks()) if (Math.abs(pk.q - qc) / qc < 0.03) onPeak = true;
                g.setColor(onPeak ? new Color(0x16a34a) : new Color(0xdc2626));
                g.setStroke(new BasicStroke(onPeak ? 2.2f : 1.4f));
                // stagger the labels down the stick so that closely spaced high orders stay readable
                int w = g.getFontMetrics().stringWidth(lattice.hkl[k]) + 4;
                int row = 0;
                while (row < 6 && labelX[row] > x - w) row++;
                if (row == 6) row = 0;
                labelX[row] = x;
                int top = box.y + 8 + row * 13;
                g.draw(new Line2D.Double(x, top, x, box.y + box.height));
                g.drawString(lattice.hkl[k], x + 2, top + 10);
            }
            g.setClip(clip);
            g.setColor(LnpCartoon.INK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(String.format("%s   a = %.1f Å (%.2f nm)", lattice.symmetry, a, a / 10), box.x + 8, box.y + box.height - 8);
            g.dispose();
        }
    }
}
