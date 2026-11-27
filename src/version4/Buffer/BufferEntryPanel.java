package version4.Buffer;

import version4.sasCIF.SampleRecord;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One line for the whole buffer, with what Scatter makes of it shown underneath as the user types:
 * the components it recognized, the ionic strength, Debye length and the solvent's electron density,
 * and anything it could not read. The drop-down offers the buffers used before and the named buffers
 * (PBS, TBS, HBS and any the beamline added).
 */
public class BufferEntryPanel extends JPanel {

    private static final Color WARN = new Color(0xB3, 0x5C, 0x00);

    private final BufferLibrary library;
    private final RecentBuffers recent;
    private final Supplier<Double> defaultTemperatureC;
    private final JComboBox<String> line = new JComboBox<>();
    private final JLabel components = new JLabel(" ");
    private final JLabel results = new JLabel(" ");
    private final JLabel warnings = new JLabel(" ");
    private final Timer debounce;
    private final List<Consumer<BufferParser.Parsed>> listeners = new ArrayList<>();
    private BufferParser.Parsed parsed;
    private BufferCalculator.Result result;

    /**
     * @param defaultTemperatureC temperature (C) to use when the line gives none, e.g. from the
     *                            sample temperature field; may return null (25 C is used)
     */
    public BufferEntryPanel(BufferLibrary library, RecentBuffers recent, Supplier<Double> defaultTemperatureC) {
        super(new BorderLayout(4, 2));
        this.library = library;
        this.recent = recent;
        this.defaultTemperatureC = defaultTemperatureC == null ? () -> null : defaultTemperatureC;
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "Buffer: one line, e.g. 20 mM HEPES-NaOH pH 7.5, 150 mM NaCl, 5% glycerol",
                TitledBorder.LEFT, TitledBorder.TOP));

        line.setEditable(true);
        fillChoices();
        line.setToolTipText("<html>Type the buffer as you would on the tube, or pick one used before.<br>"
                + "Amounts: M, mM, uM, nM, mg/mL, %, %(v/v), %(w/v); \"Tris-HCl\", \"HEPES-KOH\", \"Na-HEPES\" give the titrant;<br>"
                + "named buffers: " + String.join(", ", library.presets().keySet()) + " (\"0.5x PBS\" scales them).</html>");
        add(line, BorderLayout.NORTH);

        JPanel info = new JPanel(new GridLayout(3, 1, 0, 1));
        components.setFont(components.getFont().deriveFont(Font.PLAIN, 11f));
        results.setFont(results.getFont().deriveFont(Font.BOLD, 11f));
        warnings.setFont(warnings.getFont().deriveFont(Font.PLAIN, 11f));
        warnings.setForeground(WARN);
        info.add(components);
        info.add(results);
        info.add(warnings);
        add(info, BorderLayout.CENTER);

        debounce = new Timer(250, e -> refresh());
        debounce.setRepeats(false);
        JTextComponent editor = (JTextComponent) line.getEditor().getEditorComponent();
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        line.addActionListener(e -> refresh());
        refresh();
    }

    private void fillChoices() {
        line.removeAllItems();
        if (recent != null) for (String s : recent.list()) line.addItem(s);
        for (String name : library.presets().keySet()) line.addItem(name);
        line.setSelectedItem("");
    }

    /** the line as typed */
    public String getText() {
        Object item = line.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    public void setText(String text) {
        line.getEditor().setItem(text == null ? "" : text);
        line.setSelectedItem(text == null ? "" : text);
        refresh();
    }

    /** called (on the Event Dispatch Thread) whenever the line has been read again */
    public void addChangeListener(Consumer<BufferParser.Parsed> l) { listeners.add(l); }

    public BufferParser.Parsed getParsed() {
        if (parsed == null || !parsed.text.equals(getText())) refresh();
        return parsed;
    }

    public BufferCalculator.Result getResult() {
        getParsed();
        return result;
    }

    /** the buffer for the sample record; also remembers the line for next time */
    public SampleRecord.Buffer toRecord(SampleRecord.Status status) {
        BufferParser.Parsed p = getParsed();
        if (recent != null && !p.isEmpty()) recent.add(p.text);
        return BufferRecords.toRecord(p, defaultTemperatureC.get(), status);
    }

    /** read the line again and update the readouts */
    public void refresh() {
        parsed = BufferParser.parse(getText(), library);
        result = BufferCalculator.compute(parsed, defaultTemperatureC.get());
        if (parsed.isEmpty()) {
            components.setText("No buffer entered: ionic strength, Debye length and contrast cannot be worked out.");
            results.setText(" ");
            warnings.setText(" ");
        } else {
            components.setText(html(describeItems()));
            results.setText(String.format(Locale.ROOT,
                    "<html>Ionic strength %.3g M &nbsp;&middot;&nbsp; Debye length %s &nbsp;&middot;&nbsp; "
                            + "buffer %.4f e/&Aring;&sup3; (I(0) &times; %.3f of that in water)</html>",
                    result.ionicStrength,
                    Double.isInfinite(result.debyeLengthA) ? "unbounded (no ions)" : String.format(Locale.ROOT, "%.1f &Aring;", result.debyeLengthA),
                    result.electronDensity, result.contrastRelativeToWater));
            List<String> w = new ArrayList<>(parsed.warnings);
            w.addAll(result.warnings);
            warnings.setText(w.isEmpty() ? " " : html(String.join("; ", w)));
        }
        for (Consumer<BufferParser.Parsed> l : listeners) l.accept(parsed);
    }

    private String describeItems() {
        List<String> parts = new ArrayList<>();
        for (BufferParser.Item it : parsed.items) {
            StringBuilder s = new StringBuilder(it.toString());
            for (BufferCalculator.CounterIon ci : result.counterIons) {
                if (it.chemical != null && ci.forBuffer.equals(it.chemical.name)) {
                    s.append(String.format(Locale.ROOT, " [+%.3g mM %s]", ci.molar * 1e3, ci.ion));
                }
            }
            parts.add(s.toString());
        }
        String ph = parsed.pH != null ? String.format(Locale.ROOT, "pH %.2f", parsed.pH)
                : (result.pHAssumed ? String.format(Locale.ROOT, "pH %.2f (assumed)", result.pH) : "no pH");
        parts.add(ph);
        parts.add(String.format(Locale.ROOT, "%.1f °C", result.temperatureC));
        return String.join(" · ", parts);
    }

    private static String html(String s) {
        return "<html>" + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>";
    }
}
