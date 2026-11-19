package version4.FormFactors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * SAXS &harr; cryo-EM correlation. The user loads a micrograph (PNG/JPEG/TIFF or MRC) and
 * <ul>
 *   <li>boxes an ordered region: its Fourier transform gives the lattice spacings seen by EM, shown against the
 *       spacings the SAXS analysis predicts (rings on the power spectrum and lines on its radial profile) &ndash;
 *       the same comparison Pattipeiluhu et al. (2024) made with FFTs of cryo-EM regions;</li>
 *   <li>tags particles (drag a circle, choose a class): EM class shares and diameters are set against the SAXS
 *       population fractions and size distribution, as Hammel et al. (2023) assigned SAXS phases with cryo-EM.</li>
 * </ul>
 */
final class LnpEmPanel extends JPanel {

    /** what the panel needs from the SAXS side */
    interface SaxsSide {
        /** characteristic spacings {label, d in A} from the assessment / fit / phase decomposition */
        List<Object[]> spacings();
        /** population mixture, or null */
        LnpPopulations.Result populations();
        /** fitted single-population size: {mean outer diameter nm, sd nm}, or null */
        double[] sizeDistribution();
        Supplier<String> workingDirectory();
    }

    enum EmClass {
        SOLID("Solid core", LnpPopulations.Kind.LOADED), EMPTY("Empty / low density", LnpPopulations.Kind.EMPTY),
        BLEB("Bleb / bi-phasic", LnpPopulations.Kind.BLEB), VESICLE("Vesicle", LnpPopulations.Kind.VESICLE),
        MULTI("Multilamellar (Lα)", null), HII("Ordered HII", null), DISORDERED("Disordered core", null), OTHER("Other", null);
        final String label;
        final LnpPopulations.Kind saxs;
        EmClass(String l, LnpPopulations.Kind k) { label = l; saxs = k; }
        @Override public String toString() { return label; }
        Color color() {
            switch (this) {
                case MULTI: return new Color(0x2563eb);
                case HII: return new Color(0xdc2626);
                case DISORDERED: return new Color(0x8e44ad);
                case OTHER: return new Color(0x6e7781);
                default: return saxs.color;
            }
        }
    }

    static final class Tag {
        double x, y, r;   // image pixels
        EmClass cls;
        Tag(double x, double y, double r, EmClass c) { this.x = x; this.y = y; this.r = r; cls = c; }
    }

    private final SaxsSide saxs;
    private float[] pix;          // image values
    private int iw, ih;
    private BufferedImage view;   // contrast-stretched
    private String imageName = "";
    private final JTextField pixelSize = Fields.num("");
    private final JComboBox<EmClass> classBox = new JComboBox<>(EmClass.values());
    private final JToggleButton tagMode = new JToggleButton("Tag particles", true), fftMode = new JToggleButton("FFT of a region");
    private final List<Tag> tags = new ArrayList<>();
    private Rectangle fftBox;
    private final ImageView imageView = new ImageView();
    private final JLabel fftImage = new JLabel("", SwingConstants.CENTER);
    private final JPanel profilePanel = new JPanel(new BorderLayout()), compareChart = new JPanel(new BorderLayout());
    private final JTextArea text = new JTextArea();
    private double[] emPeaksD = new double[0];

    LnpEmPanel(SaxsSide saxs) {
        super(new BorderLayout());
        this.saxs = saxs;
        JButton load = new JButton("Load micrograph…"), clear = new JButton("Clear tags"), export = new JButton("Export CSV…");
        load.addActionListener(e -> loadImage());
        clear.addActionListener(e -> { tags.clear(); imageView.repaint(); updateSummary(); });
        export.addActionListener(e -> exportCsv());
        ButtonGroup bg = new ButtonGroup();
        bg.add(tagMode);
        bg.add(fftMode);
        pixelSize.setColumns(6);
        pixelSize.setToolTipText("Pixel size of the micrograph (Å per pixel); read from MRC headers");
        pixelSize.addActionListener(e -> { updateFft(); updateSummary(); });
        JPanel bar = Fields.flow(load, new JLabel("pixel size (Å/px)"), pixelSize, tagMode, fftMode, new JLabel("class"), classBox, clear, export);
        bar.setBorder(new EmptyBorder(4, 4, 2, 4));
        JPanel north = new JPanel(new BorderLayout());
        JLabel help = Fields.wrappingLabel(north, "<span style='color:#6e7781'>Tag particles: drag from the centre outwards; "
                + "right-click a tag to remove it. FFT: drag a box over an ordered region; its spacings are compared with the SAXS "
                + "spacings (coloured rings). EM sees individual particles in thin ice, SAXS the whole sample: expect agreement "
                + "on spacings, rougher agreement on fractions.</span>");
        help.setBorder(new EmptyBorder(0, 6, 4, 6));
        north.add(bar, BorderLayout.NORTH);
        north.add(help, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        fftImage.setPreferredSize(new Dimension(300, 300));
        fftImage.setBorder(BorderFactory.createTitledBorder("Power spectrum of the box"));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JPanel right = new JPanel(new GridLayout(2, 1, 4, 4));
        JPanel fftRow = new JPanel(new BorderLayout(4, 4));
        fftRow.add(fftImage, BorderLayout.WEST);
        fftRow.add(profilePanel, BorderLayout.CENTER);
        right.add(fftRow);
        JSplitPane lower = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(text), compareChart);
        lower.setResizeWeight(0.55);
        right.add(lower);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imageView, right);
        split.setResizeWeight(0.45);
        add(split, BorderLayout.CENTER);
        profilePanel.add(LnpCharts.panelFor(FormFactorCharts.placeholder("Box an ordered region (FFT mode)")), BorderLayout.CENTER);
        compareChart.add(LnpCharts.panelFor(FormFactorCharts.placeholder("Tag particles to compare sizes")), BorderLayout.CENTER);
        updateSummary();
    }

    double px() {
        try { return Double.parseDouble(pixelSize.getText().trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    // ------------------------------------------------------------------ loading

    private void loadImage() {
        JFileChooser fc = new JFileChooser(saxs.workingDirectory().get());
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            load(fc.getSelectedFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not read the image: " + ex.getMessage());
        }
    }

    void load(File f) throws IOException {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".mrc") || n.endsWith(".mrcs") || n.endsWith(".map")) readMrc(f);
        else {
            BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img == null) throw new IOException("format not supported (use PNG, JPEG, TIFF or MRC)");
            iw = img.getWidth();
            ih = img.getHeight();
            pix = new float[iw * ih];
            java.awt.image.Raster r = img.getRaster();
            int bands = r.getNumBands();
            for (int y = 0; y < ih; y++)
                for (int x = 0; x < iw; x++) {
                    double v = 0;
                    for (int b = 0; b < Math.min(3, bands); b++) v += r.getSampleDouble(x, y, b);
                    pix[y * iw + x] = (float) (v / Math.min(3, bands));
                }
        }
        imageName = f.getName();
        tags.clear();
        fftBox = null;
        view = stretch(pix, iw, ih);
        imageView.revalidate();
        imageView.repaint();
        updateSummary();
    }

    /** MRC/CCP4: first section of modes 0, 1, 2, 6; pixel size from the cell dimensions */
    private void readMrc(File f) throws IOException {
        byte[] all = Files.readAllBytes(f.toPath());
        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        int nx = b.getInt(0), ny = b.getInt(4), mode = b.getInt(12), mx = b.getInt(28);
        if (nx <= 0 || nx > 100000 || ny <= 0 || ny > 100000) {   // big-endian file
            b.order(ByteOrder.BIG_ENDIAN);
            nx = b.getInt(0); ny = b.getInt(4); mode = b.getInt(12); mx = b.getInt(28);
        }
        float cellX = b.getFloat(40);
        int ext = b.getInt(92);
        int off = 1024 + Math.max(0, ext);
        iw = nx;
        ih = ny;
        pix = new float[nx * ny];
        for (int i = 0; i < nx * ny; i++) {
            switch (mode) {
                case 0: pix[i] = all[off + i]; break;
                case 1: pix[i] = b.getShort(off + 2 * i); break;
                case 2: pix[i] = b.getFloat(off + 4 * i); break;
                case 6: pix[i] = b.getShort(off + 2 * i) & 0xffff; break;
                default: throw new IOException("MRC mode " + mode + " not supported");
            }
        }
        if (mx > 0 && cellX > 0) pixelSize.setText(String.format("%.4f", cellX / mx));
    }

    /** grey image with a 0.5-99.5 % percentile stretch */
    static BufferedImage stretch(float[] p, int w, int h) {
        float[] s = p.clone();
        java.util.Arrays.sort(s);
        float lo = s[(int) (0.005 * (s.length - 1))], hi = s[(int) (0.995 * (s.length - 1))];
        if (hi <= lo) hi = lo + 1;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < p.length; i++) {
            int c = (int) Math.max(0, Math.min(255, 255 * (p[i] - lo) / (hi - lo)));
            img.setRGB(i % w, i / w, (c << 16) | (c << 8) | c);
        }
        return img;
    }

    // ------------------------------------------------------------------ image view with tags

    private final class ImageView extends JComponent {
        double scale = 1, ox, oy;
        Point press;
        Point drag;

        ImageView() {
            setPreferredSize(new Dimension(560, 560));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (view == null) return;
                    if (SwingUtilities.isRightMouseButton(e)) {
                        double[] ip = toImage(e.getPoint());
                        for (int i = tags.size() - 1; i >= 0; i--)
                            if (Math.hypot(tags.get(i).x - ip[0], tags.get(i).y - ip[1]) <= tags.get(i).r) { tags.remove(i); break; }
                        repaint();
                        updateSummary();
                        return;
                    }
                    press = e.getPoint();
                }
                @Override public void mouseDragged(MouseEvent e) { if (press != null) { drag = e.getPoint(); repaint(); } }
                @Override public void mouseReleased(MouseEvent e) {
                    if (press == null || drag == null) { press = null; return; }
                    double[] a = toImage(press), b = toImage(drag);
                    if (tagMode.isSelected()) {
                        double r = Math.hypot(b[0] - a[0], b[1] - a[1]);
                        if (r > 3) tags.add(new Tag(a[0], a[1], r, (EmClass) classBox.getSelectedItem()));
                        updateSummary();
                    } else {
                        int x0 = (int) Math.max(0, Math.min(a[0], b[0])), y0 = (int) Math.max(0, Math.min(a[1], b[1]));
                        int x1 = (int) Math.min(iw, Math.max(a[0], b[0])), y1 = (int) Math.min(ih, Math.max(a[1], b[1]));
                        if (x1 - x0 >= 32 && y1 - y0 >= 32) { fftBox = new Rectangle(x0, y0, x1 - x0, y1 - y0); updateFft(); }
                    }
                    press = drag = null;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        double[] toImage(Point p) { return new double[]{(p.x - ox) / scale, (p.y - oy) / scale}; }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x2b2b2b));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (view == null) {
                g.setColor(Color.LIGHT_GRAY);
                g.drawString("Load a cryo-EM micrograph (PNG, JPEG, TIFF or MRC)", 20, getHeight() / 2);
                g.dispose();
                return;
            }
            scale = Math.min(getWidth() / (double) iw, getHeight() / (double) ih);
            ox = (getWidth() - iw * scale) / 2;
            oy = (getHeight() - ih * scale) / 2;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(view, (int) ox, (int) oy, (int) (iw * scale), (int) (ih * scale), null);
            Font f = new Font("SansSerif", Font.BOLD, 11);
            g.setFont(f);
            int id = 1;
            for (Tag t : tags) {
                Color c = t.cls.color();
                double cx = ox + t.x * scale, cy = oy + t.y * scale, r = t.r * scale;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
                g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g.setColor(c);
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                String lab = "ID:" + id++;
                int tw = g.getFontMetrics().stringWidth(lab);
                g.fill(new java.awt.geom.RoundRectangle2D.Double(cx - tw / 2.0 - 4, cy - r - 16, tw + 8, 14, 6, 6));
                g.setColor(Color.WHITE);
                g.drawString(lab, (float) (cx - tw / 2.0), (float) (cy - r - 5));
            }
            if (fftBox != null) {
                g.setColor(new Color(0xfacc15));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{6, 4}, 0));
                g.draw(new Rectangle2D.Double(ox + fftBox.x * scale, oy + fftBox.y * scale, fftBox.width * scale, fftBox.height * scale));
            }
            if (press != null && drag != null) {
                g.setColor(Color.YELLOW);
                g.setStroke(new BasicStroke(1.5f));
                if (tagMode.isSelected()) {
                    double r = press.distance(drag);
                    g.draw(new Ellipse2D.Double(press.x - r, press.y - r, 2 * r, 2 * r));
                } else g.draw(new Rectangle(Math.min(press.x, drag.x), Math.min(press.y, drag.y), Math.abs(drag.x - press.x), Math.abs(drag.y - press.y)));
            }
            double p = px();
            if (!Double.isNaN(p) && p > 0) {   // 50 nm scale bar
                double len = 500 / p * scale;
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(3f));
                g.drawLine((int) (ox + iw * scale - len - 12), (int) (oy + ih * scale - 14), (int) (ox + iw * scale - 12), (int) (oy + ih * scale - 14));
                g.drawString("50 nm", (float) (ox + iw * scale - len / 2 - 30), (float) (oy + ih * scale - 20));
            }
            g.setColor(Color.LIGHT_GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(imageName, 6, 14);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ FFT of a region

    private void updateFft() {
        if (fftBox == null || pix == null) return;
        double p = px();
        int side = Math.min(fftBox.width, fftBox.height);
        int n = 64;
        while (n * 2 <= Math.min(1024, side)) n *= 2;
        // crop the central n x n of the box, remove the mean, Hann window
        int x0 = fftBox.x + (fftBox.width - n) / 2, y0 = fftBox.y + (fftBox.height - n) / 2;
        double[] re = new double[n * n], im = new double[n * n];
        double mean = 0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) mean += pix[(y0 + y) * iw + x0 + x];
        mean /= n * n;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double w = (0.5 - 0.5 * Math.cos(2 * Math.PI * x / (n - 1))) * (0.5 - 0.5 * Math.cos(2 * Math.PI * y / (n - 1)));
                re[y * n + x] = (pix[(y0 + y) * iw + x0 + x] - mean) * w;
            }
        fft2(re, im, n);
        double[] pow = new double[n * n];
        for (int i = 0; i < n * n; i++) pow[i] = re[i] * re[i] + im[i] * im[i];
        // radial average
        int kmax = n / 2;
        double[] prof = new double[kmax];
        int[] cnt = new int[kmax];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int kx = x <= n / 2 ? x : x - n, ky = y <= n / 2 ? y : y - n;
                int k = (int) Math.round(Math.hypot(kx, ky));
                if (k < kmax) { prof[k] += pow[y * n + x]; cnt[k]++; }
            }
        for (int k = 0; k < kmax; k++) prof[k] = cnt[k] > 0 ? prof[k] / cnt[k] : 0;
        // power spectrum image (log, centred) with SAXS rings
        int disp = 300;
        BufferedImage img = new BufferedImage(disp, disp, BufferedImage.TYPE_INT_RGB);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        double[] lg = new double[n * n];
        for (int i = 0; i < n * n; i++) { lg[i] = Math.log(pow[i] + 1e-12); }
        double[] sorted = lg.clone();
        java.util.Arrays.sort(sorted);
        lo = sorted[(int) (0.05 * sorted.length)];
        hi = sorted[(int) (0.999 * (sorted.length - 1))];
        for (int y = 0; y < disp; y++)
            for (int x = 0; x < disp; x++) {
                int fx = ((x * n / disp) + n / 2) % n, fy = ((y * n / disp) + n / 2) % n;
                int c = (int) Math.max(0, Math.min(255, 255 * (lg[fy * n + fx] - lo) / (hi - lo)));
                img.setRGB(x, y, (c << 16) | (c << 8) | c);
            }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<Object[]> sp = saxs.spacings();
        Color[] cols = {new Color(0xdc2626), new Color(0x2563eb), new Color(0x16a34a), new Color(0xd97706), new Color(0x8e44ad), new Color(0x0891b2)};
        if (!Double.isNaN(p) && p > 0) {
            int i = 0;
            for (Object[] s : sp) {
                double d = (Double) s[1], k = n * p / d, r = k * disp / (double) n;
                if (k >= kmax) continue;
                g.setColor(cols[i++ % cols.length]);
                g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{5, 4}, 0));
                g.draw(new Ellipse2D.Double(disp / 2.0 - r, disp / 2.0 - r, 2 * r, 2 * r));
            }
        }
        g.dispose();
        fftImage.setIcon(new ImageIcon(img));

        // radial profile vs d, EM peaks, SAXS lines
        XYSeries s = new XYSeries("EM power spectrum (radial average)", false, true);
        List<double[]> peaks = new ArrayList<>();
        if (!Double.isNaN(p) && p > 0) {
            for (int k = 2; k < kmax; k++) s.add(n * p / k, Math.max(prof[k], 1e-12));
            // peaks: local maxima above a log-log linear background
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            int m = 0;
            for (int k = 3; k < kmax - 1; k++) { double xx = Math.log(k), yy = Math.log(prof[k] + 1e-30); sx += xx; sy += yy; sxx += xx * xx; sxy += xx * yy; m++; }
            double sl = (m * sxy - sx * sy) / (m * sxx - sx * sx), ic = (sy - sl * sx) / m;
            double[] ex = new double[kmax];
            for (int k = 3; k < kmax - 1; k++) ex[k] = Math.log(prof[k] + 1e-30) - (ic + sl * Math.log(k));
            double sd = 0;
            for (int k = 3; k < kmax - 1; k++) sd += ex[k] * ex[k];
            sd = Math.sqrt(sd / Math.max(1, m));
            for (int k = 4; k < kmax - 2; k++)
                if (ex[k] > ex[k - 1] && ex[k] >= ex[k + 1] && ex[k] > 1.5 * sd && ex[k] > ex[k - 2] && ex[k] >= ex[k + 2]) {
                    // parabolic refinement
                    double den = ex[k - 1] - 2 * ex[k] + ex[k + 1], kk = k + (den < 0 ? 0.5 * (ex[k - 1] - ex[k + 1]) / den : 0);
                    peaks.add(new double[]{n * p / kk, ex[k] / sd});
                }
            peaks.sort((a, b) -> Double.compare(b[1], a[1]));
            if (peaks.size() > 6) peaks = new ArrayList<>(peaks.subList(0, 6));
        }
        emPeaksD = new double[peaks.size()];
        for (int i = 0; i < peaks.size(); i++) emPeaksD[i] = peaks.get(i)[0];
        XYPlot plot;
        LogAxis y = new LogAxis("power");
        y.setTickLabelsVisible(false);
        NumberAxis x = new NumberAxis("d spacing (Å)");
        x.setInverted(true);
        x.setRange(Math.max(2 * (Double.isNaN(p) ? 1 : p), 15), Math.min(300, n * (Double.isNaN(p) ? 1 : p) / 2));
        XYLineAndShapeRenderer rr = new XYLineAndShapeRenderer(true, false);
        rr.setSeriesPaint(0, new Color(0x24292f));
        plot = new XYPlot(new XYSeriesCollection(s), x, y, rr);
        FormFactorCharts.style(plot);
        int i = 0;
        for (Object[] sp1 : sp) {
            ValueMarker vm = new ValueMarker((Double) sp1[1], cols[i++ % cols.length], new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{5, 4}, 0));
            plot.addDomainMarker(vm);   // labels are in the comparison table below (they overlap here)
        }
        for (double[] pk : peaks) plot.addDomainMarker(new ValueMarker(pk[0], new Color(0xfacc15), new BasicStroke(2f)));
        JFreeChart ch = new JFreeChart(Double.isNaN(p) ? "Enter the pixel size" : "EM spacings (yellow) vs SAXS (dashed)", FormFactorCharts.TITLE, plot, false);
        FormFactorCharts.finish(ch);
        profilePanel.removeAll();
        profilePanel.add(LnpCharts.panelFor(ch), BorderLayout.CENTER);
        profilePanel.revalidate();
        updateSummary();
    }

    /** in-place 2D FFT (n a power of two) */
    static void fft2(double[] re, double[] im, int n) {
        double[] r = new double[n], i = new double[n];
        for (int y = 0; y < n; y++) {
            System.arraycopy(re, y * n, r, 0, n);
            System.arraycopy(im, y * n, i, 0, n);
            fft(r, i);
            System.arraycopy(r, 0, re, y * n, n);
            System.arraycopy(i, 0, im, y * n, n);
        }
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) { r[y] = re[y * n + x]; i[y] = im[y * n + x]; }
            fft(r, i);
            for (int y = 0; y < n; y++) { re[y * n + x] = r[y]; im[y * n + x] = i[y]; }
        }
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double tr = re[b] * cr - im[b] * ci, ti = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - tr; im[b] = im[a] - ti;
                    re[a] += tr; im[a] += ti;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }

    // ------------------------------------------------------------------ comparison

    void updateSummary() {
        StringBuilder sb = new StringBuilder("SAXS ↔ cryo-EM\n\n");
        double p = px();
        List<Object[]> sp = saxs.spacings();
        sb.append("1. Spacings\n");
        if (sp.isEmpty()) sb.append("   No SAXS spacings yet: run the assessment (and the phase decomposition) first.\n");
        if (emPeaksD.length == 0) sb.append(fftBox == null ? "   Box an ordered region in FFT mode to measure EM spacings.\n" : "   No clear periodicity in the boxed region.\n");
        for (Object[] s : sp) {
            double d = (Double) s[1];
            double best = Double.NaN;
            for (double e : emPeaksD) if (Double.isNaN(best) || Math.abs(e - d) < Math.abs(best - d)) best = e;
            if (Double.isNaN(best) || Math.abs(best - d) / d > 0.12)
                sb.append(String.format("   SAXS %-14s d = %6.1f Å   %s%n", s[0], d, emPeaksD.length == 0 ? "" : "not resolved in this EM region"));
            else {
                double dev = 100 * (best - d) / d;
                sb.append(String.format("   SAXS %-14s d = %6.1f Å   EM %6.1f Å   %+5.1f %%  %s%n", s[0], d, best, dev,
                        Math.abs(dev) < 5 ? "agree" : Math.abs(dev) < 12 ? "roughly agree" : "no match"));
            }
        }
        for (double e : emPeaksD) {
            boolean matched = false;
            for (Object[] s : sp) if (Math.abs(e - (Double) s[1]) / (Double) s[1] < 0.12) matched = true;
            if (!matched) sb.append(String.format("   EM spacing %.1f Å has no SAXS counterpart%n", e));
        }

        sb.append("\n2. Particle classes and sizes\n");
        if (tags.isEmpty()) sb.append("   Tag particles to compare with the SAXS populations.\n");
        else if (Double.isNaN(p) || p <= 0) sb.append("   Enter the pixel size to convert diameters to nm.\n");
        LnpPopulations.Result pop = saxs.populations();
        int total = tags.size();
        if (total > 0) {
            sb.append(String.format("   %-22s %6s %7s %16s %14s%n", "class", "EM n", "EM %", "EM diam. (nm)", "SAXS"));
            for (EmClass c : EmClass.values()) {
                List<Double> ds = new ArrayList<>();
                for (Tag t : tags) if (t.cls == c) ds.add(2 * t.r * p / 10);
                if (ds.isEmpty()) continue;
                double m = 0, v = 0;
                for (double d : ds) m += d;
                m /= ds.size();
                for (double d : ds) v += (d - m) * (d - m);
                double sdv = ds.size() > 1 ? Math.sqrt(v / (ds.size() - 1)) : 0;
                String saxsTxt = "–";
                if (pop != null && c.saxs != null) {
                    LnpPopulations.Component sc = pop.get(c.saxs);
                    saxsTxt = sc == null || Double.isNaN(sc.numberFraction) ? "not detected"
                            : String.format("%.0f %%, %.0f nm", 100 * sc.numberFraction, sc.diameter);
                }
                sb.append(String.format("   %-22s %6d %6.0f %% %8.1f ± %-5.1f %14s%n", c.label, ds.size(), 100.0 * ds.size() / total, m, sdv, saxsTxt));
            }
            sb.append(String.format("   (%d particles tagged; with fewer than ~100 per class the EM fractions carry ±%.0f %% counting error)%n",
                    total, 100 / Math.sqrt(Math.max(1, total))));
        }
        sb.append("\nEM counts single particles in a thin film (blotting can sort sizes), SAXS weighs the whole sample\n"
                + "by intensity; spacings should agree closely, fractions and sizes within the uncertainties.\n");
        text.setText(sb.toString());
        text.setCaretPosition(0);
        updateSizeChart();
    }

    private void updateSizeChart() {
        double p = px();
        if (tags.isEmpty() || Double.isNaN(p)) return;
        XYSeriesCollection c = new XYSeriesCollection();
        XYSeries em = new XYSeries("EM (tagged, %)", false, true);
        double dmax = 0;
        for (Tag t : tags) dmax = Math.max(dmax, 2 * t.r * p / 10);
        double bin = Math.max(2, Math.ceil(dmax / 25));
        int nb = (int) Math.ceil(dmax / bin) + 2;
        double[] h = new double[nb];
        for (Tag t : tags) h[(int) Math.min(nb - 1, 2 * t.r * p / 10 / bin)]++;
        for (int i = 0; i < nb; i++) { em.add(i * bin, 100 * h[i] / tags.size()); em.add((i + 1) * bin, 100 * h[i] / tags.size()); }
        c.addSeries(em);
        double[] sd = saxs.sizeDistribution();
        if (sd != null) {
            XYSeries sx = new XYSeries("SAXS fit (%, same bins)", false, true);
            for (double d = 0; d <= nb * bin; d += bin / 4) {
                double z = (d - sd[0]) / sd[1];
                sx.add(d, 100 * bin * Math.exp(-0.5 * z * z) / (sd[1] * Math.sqrt(2 * Math.PI)));
            }
            c.addSeries(sx);
        }
        XYLineAndShapeRenderer r = new XYLineAndShapeRenderer(true, false);
        r.setSeriesPaint(0, new Color(0xd97706));
        r.setSeriesStroke(0, new BasicStroke(2f));
        r.setSeriesPaint(1, FormFactorCharts.FIT);
        r.setSeriesStroke(1, new BasicStroke(2f));
        NumberAxis x = new NumberAxis("particle diameter (nm)"), y = new NumberAxis("% of particles");
        XYPlot plot = new XYPlot(c, x, y, r);
        FormFactorCharts.style(plot);
        JFreeChart ch = new JFreeChart("Size: EM vs SAXS", FormFactorCharts.TITLE, plot, true);
        FormFactorCharts.finish(ch);
        compareChart.removeAll();
        compareChart.add(LnpCharts.panelFor(ch), BorderLayout.CENTER);
        compareChart.revalidate();
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser(saxs.workingDirectory().get());
        fc.setSelectedFile(new File(imageName.replaceAll("\\.[A-Za-z0-9]+$", "") + "_em_tags.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (PrintWriter w = new PrintWriter(fc.getSelectedFile(), "UTF-8")) {
            w.println("# " + imageName + ", pixel size " + pixelSize.getText() + " A/px");
            for (String line : text.getText().split("\n")) w.println("# " + line);
            w.println("id,class,x_px,y_px,diameter_nm");
            int id = 1;
            for (Tag t : tags) w.printf("%d,%s,%.1f,%.1f,%.2f%n", id++, t.cls.label, t.x, t.y, 2 * t.r * px() / 10);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage());
        }
    }

    // test hooks
    void addTag(double x, double y, double r, EmClass c) { tags.add(new Tag(x, y, r, c)); imageView.repaint(); updateSummary(); }
    void setFftBox(Rectangle r) { fftBox = r; updateFft(); imageView.repaint(); }
    void setPixelSize(double p) { pixelSize.setText(String.format("%.3f", p)); }
    double[] emPeaks() { return emPeaksD; }
}
