package version4.Buffer;

import version4.sasCIF.SampleRecord;

import java.util.ArrayList;
import java.util.List;

/** Between the one-line buffer and the {@link SampleRecord} stored with the data. */
public final class BufferRecords {

    private BufferRecords() { }

    /**
     * The record's buffer for a parsed line: the text as typed, one component per item (library name,
     * amount, unit, titrant), pH, temperature (the line's own, else {@code defaultC}) and the computed
     * ionic strength, Debye length and electron density.
     */
    public static SampleRecord.Buffer toRecord(BufferParser.Parsed p, Double defaultC, SampleRecord.Status status) {
        SampleRecord.Buffer b = new SampleRecord.Buffer();
        b.text = p.text;
        for (BufferParser.Item it : p.items) {
            SampleRecord.Component c = new SampleRecord.Component();
            c.name = it.chemical != null ? it.chemical.name : it.name;
            c.concentration = it.amount;
            c.unit = it.unit;
            c.titrant = it.titrantText;
            b.components.add(c);
        }
        BufferCalculator.Result r = BufferCalculator.compute(p, defaultC);
        b.pH = p.pH;
        b.temperatureK = r.temperatureC + 273.15;
        if (!p.items.isEmpty()) {
            b.ionicStrengthM = r.ionicStrength;
            b.debyeLengthA = Double.isInfinite(r.debyeLengthA) ? null : r.debyeLengthA;
            b.electronDensity = r.electronDensity;
        }
        b.status = p.isEmpty() ? SampleRecord.Status.NONE : status;
        return b;
    }

    /** the line to show for a stored buffer: its text, else its components, else "" */
    public static String lineFor(SampleRecord.Buffer b) {
        if (b == null) return "";
        if (b.text != null && !b.text.trim().isEmpty()) return b.text.trim();
        List<String> parts = new ArrayList<>();
        for (SampleRecord.Component c : b.components) {          // in a form the parser reads back
            String part = (c.concentration != null ? BufferParser.num(c.concentration) + " " + (c.unit == null ? "" : c.unit) + " " : "")
                    + (c.name == null ? "" : c.name) + (c.titrant != null && !c.titrant.isEmpty() ? "-" + c.titrant : "");
            parts.add(part.replaceAll("\\s+", " ").trim());
        }
        String s = String.join(", ", parts);
        if (b.pH != null) s = s.isEmpty() ? "pH " + BufferParser.num(b.pH) : s + ", pH " + BufferParser.num(b.pH);
        return s;
    }
}
