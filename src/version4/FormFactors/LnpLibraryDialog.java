package version4.FormFactors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Lipid library manager: built-in lipids (read only) and the user's private ones (add, edit, duplicate, delete,
 * import, export), plus the saved formulations. Private entries are stored by {@link LnpLipidLibrary}.
 */
final class LnpLibraryDialog {

    private LnpLibraryDialog() { }

    // ================================================================== lipid editor

    /**
     * Form for one lipid. Electrons come from the molecular formula (or are typed in); the volume is typed in or
     * computed from the molecular weight and a mass density.
     * @param start  lipid to edit or copy from (null = empty form)
     * @param copy   true: start is a template, the result is a new private lipid
     * @return the new lipid, or null if cancelled
     */
    static LnpComposition.Lipid editLipid(Component parent, LnpComposition.Lipid start, boolean copy) {
        JTextField name = new JTextField(start == null ? "" : copy ? start.name + " (copy)" : start.name, 22);
        JComboBox<LnpComposition.Role> role = new JComboBox<>(LnpComposition.Role.values());
        JTextField formula = new JTextField(start == null ? "" : start.formula, 22);
        JTextField electrons = Fields.num(start == null ? "" : Fields.fmt(start.electrons));
        JTextField mw = Fields.num(start == null || Double.isNaN(start.mw) ? "" : String.format("%.1f", start.mw));
        JRadioButton byVolume = new JRadioButton("Molecular volume (Å³)"), byDensity = new JRadioButton("From mass density (g/cm³)");
        ButtonGroup bg = new ButtonGroup();
        bg.add(byVolume);
        bg.add(byDensity);
        JTextField volume = Fields.num(start == null ? "" : String.format("%.0f", start.volume));
        JTextField density = Fields.num("");
        JTextField pegMw = Fields.num(start == null || start.pegUnits == 0 ? "2000" : String.format("%.0f", start.pegUnits * 44.05));
        JComboBox<String> phase = new JComboBox<>(new String[]{"(none / not known)", "lamellar", "HII", "cubic"});
        JTextField notes = new JTextField(start == null ? "" : start.notes, 22);
        JLabel formulaInfo = new JLabel(" "), summary = new JLabel(" ");
        formulaInfo.setForeground(new Color(0x6e7781));
        if (start != null) {
            role.setSelectedItem(start.role);
            phase.setSelectedItem(start.propensity.isEmpty() ? "(none / not known)" : start.propensity);
        } else role.setSelectedItem(LnpComposition.Role.IONIZABLE);
        byVolume.setSelected(start != null);
        byDensity.setSelected(start == null);
        density.setText(String.format("%.2f", LnpComposition.typicalDensity((LnpComposition.Role) role.getSelectedItem())));

        JLabel pegHint = new JLabel("Da  (PEG chain only)");
        JPanel pegRow = Fields.flow(pegMw, pegHint);
        Runnable refresh = () -> {
            LnpComposition.Role r = (LnpComposition.Role) role.getSelectedItem();
            pegMw.setEnabled(r == LnpComposition.Role.PEG);
            pegHint.setText(r == LnpComposition.Role.PEG ? "Da  (PEG chain only)" : "only for PEG-lipids");
            String f = formula.getText().trim();
            if (!f.isEmpty()) {
                try {
                    double[] em = LnpComposition.parseFormula(f);
                    formulaInfo.setText(String.format("→ %.0f electrons, MW %.1f Da", em[0], em[1]));
                    formulaInfo.setForeground(new Color(0x16a34a));
                    electrons.setText(String.format("%.0f", em[0]));
                    mw.setText(String.format("%.1f", em[1]));
                } catch (IllegalArgumentException ex) {
                    formulaInfo.setText(ex.getMessage());
                    formulaInfo.setForeground(new Color(0xdc2626));
                }
            } else {
                formulaInfo.setText("Formula not given: enter electrons and MW yourself");
                formulaInfo.setForeground(new Color(0x6e7781));
            }
            volume.setEnabled(byVolume.isSelected());
            density.setEnabled(byDensity.isSelected());
            double e = parse(electrons), v = currentVolume(byDensity.isSelected(), volume, density, mw);
            if (byDensity.isSelected() && v > 0) volume.setText(String.format("%.0f", v));
            summary.setText(e > 0 && v > 0
                    ? String.format("<html>Mean electron density <b>%.3f e/Å³</b> (typical lipids 0.29–0.36; water 0.334)</html>", e / v)
                    : "<html>Enter electrons and a volume to see the electron density</html>");
        };
        DocumentListener dl = new DocumentListener() {
            void go() { SwingUtilities.invokeLater(refresh); }
            @Override public void insertUpdate(DocumentEvent e) { go(); }
            @Override public void removeUpdate(DocumentEvent e) { go(); }
            @Override public void changedUpdate(DocumentEvent e) { go(); }
        };
        formula.getDocument().addDocumentListener(dl);
        density.getDocument().addDocumentListener(dl);
        volume.getDocument().addDocumentListener(new DocumentListener() {   // electron density only, no loops
            void go() { SwingUtilities.invokeLater(() -> {
                double e = parse(electrons), v = parse(volume);
                if (e > 0 && v > 0) summary.setText(String.format("<html>Mean electron density <b>%.3f e/Å³</b> (typical lipids 0.29–0.36; water 0.334)</html>", e / v));
            }); }
            @Override public void insertUpdate(DocumentEvent e) { go(); }
            @Override public void removeUpdate(DocumentEvent e) { go(); }
            @Override public void changedUpdate(DocumentEvent e) { go(); }
        });
        role.addActionListener(e -> {
            if (byDensity.isSelected()) density.setText(String.format("%.2f", LnpComposition.typicalDensity((LnpComposition.Role) role.getSelectedItem())));
            refresh.run();
        });
        byVolume.addActionListener(e -> refresh.run());
        byDensity.addActionListener(e -> refresh.run());

        Fields.Form f = new Fields.Form();
        f.row("Name", name, "Your name or code for the lipid; must differ from the built-in lipids");
        f.row("Role", role, "IONIZABLE: the amine lipid binding the RNA. HELPER: phospholipid / monoolein. STEROL: cholesterol and analogues. PEG: PEG-lipid");
        f.row("Molecular formula", formula, "e.g. C48H95NO5. Brackets and counts allowed: (C2H4O)45. Charge as a trailing + or -. For a PEG-lipid give the lipid anchor only");
        f.full(formulaInfo);
        f.row("Electrons per molecule", electrons, "Filled from the formula; type it in if you prefer not to enter the structure");
        f.row("Molecular weight (Da)", mw, "Filled from the formula; needed only for the volume-from-density option");
        f.full(Fields.flow(byVolume, volume));
        f.full(Fields.flow(byDensity, density));
        f.note("Molecular volume sets the electron density. If you have a measured density (or a simulation value) use it; "
                + "otherwise the typical liquid-state value for the role is filled in (0.92 for ionizable lipids, 1.00 for phospholipids, 1.03 for sterols).");
        f.row("PEG molecular weight", pegRow, "Length of the PEG chain, e.g. 2000 for PEG2000 (45 ethylene-oxide units)");
        f.row("Preferred phase", phase, "Phase the lipid forms on its own, if known: lamellar (cylindrical, e.g. DSPC), HII (cone-shaped, e.g. DOPE), cubic (e.g. monoolein)");
        f.row("Notes", notes, "Free text, e.g. batch, source of the density");
        f.full(summary);
        JPanel form = f.panel();
        form.setBorder(new EmptyBorder(6, 6, 6, 6));
        refresh.run();

        while (true) {
            int ok = JOptionPane.showConfirmDialog(parent, form, start == null || copy ? "New private lipid" : "Edit private lipid",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return null;
            LnpComposition.Role r = (LnpComposition.Role) role.getSelectedItem();
            double v = currentVolume(byDensity.isSelected(), volume, density, mw);
            int units = r == LnpComposition.Role.PEG ? (int) Math.round(parse(pegMw) / 44.05) : 0;
            String ph = phase.getSelectedIndex() == 0 ? "" : (String) phase.getSelectedItem();
            String nt = notes.getText().trim();
            if (byDensity.isSelected() && parse(density) > 0)
                nt = (nt.isEmpty() ? "" : nt + "; ") + String.format("volume from density %.3f g/cm³", parse(density));
            LnpComposition.Lipid l = new LnpComposition.Lipid(name.getText().trim(), r, parse(electrons), v, units, ph,
                    formula.getText().trim(), parse(mw) > 0 ? parse(mw) : Double.NaN, false, nt);
            String replacing = start != null && !copy && !start.builtIn ? start.name : null;
            List<String> problems = LnpLipidLibrary.validate(l, replacing);
            if (replacing == null && LnpLipidLibrary.get().lipid(l.name) != null) problems.add("A private lipid called '" + l.name + "' already exists.");
            List<String> errors = new java.util.ArrayList<>(), warnings = new java.util.ArrayList<>();
            for (String p : problems) (p.startsWith("!") ? warnings : errors).add(p.replaceFirst("^!", ""));
            if (!errors.isEmpty()) {
                JOptionPane.showMessageDialog(parent, String.join("\n", errors), "Please check", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            if (!warnings.isEmpty() && JOptionPane.showConfirmDialog(parent, String.join("\n", warnings) + "\n\nSave anyway?",
                    "Unusual value", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) continue;
            try {
                LnpLipidLibrary.get().put(l, replacing);
                return l;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, "Could not save the library:\n" + ex.getMessage(), "Lipid library", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }
    }

    private static double currentVolume(boolean fromDensity, JTextField volume, JTextField density, JTextField mw) {
        if (!fromDensity) return parse(volume);
        double d = parse(density), m = parse(mw);
        return d > 0 && m > 0 ? LnpComposition.volumeFromDensity(m, d) : Double.NaN;
    }

    private static double parse(JTextField f) {
        try { return Double.parseDouble(f.getText().trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    // ================================================================== library manager

    /** shows the library; returns true if anything changed (so the panel can refresh its menus) */
    static boolean manage(Component parent) {
        final boolean[] changed = {false};
        LibraryModel model = new LibraryModel();
        JTable table = new JTable(model);
        table.setRowHeight(table.getRowHeight() + 4);
        table.setAutoCreateRowSorter(true);
        DefaultTableCellRenderer greyBuiltIn = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                LnpComposition.Lipid l = model.rows.get(t.convertRowIndexToModel(row));
                if (!sel) c.setForeground(l.builtIn ? new Color(0x6e7781) : new Color(0x1f2328));
                if (c instanceof JComponent) ((JComponent) c).setToolTipText(l.notes.isEmpty() ? null : l.notes);
                return c;
            }
        };
        for (int c = 0; c < model.getColumnCount(); c++) table.getColumnModel().getColumn(c).setCellRenderer(greyBuiltIn);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);

        DefaultListModel<String> forms = new DefaultListModel<>();
        Runnable reloadForms = () -> { forms.clear(); for (String n : LnpLipidLibrary.get().formulationNames()) forms.addElement(n); };
        reloadForms.run();
        JList<String> formList = new JList<>(forms);

        JButton add = new JButton("New lipid…"), edit = new JButton("Edit…"), dup = new JButton("Duplicate as private…"),
                del = new JButton("Delete"), imp = new JButton("Import…"), exp = new JButton("Export…"), delForm = new JButton("Delete formulation");
        add.addActionListener(e -> { if (editLipid(parent, null, false) != null) { model.reload(); changed[0] = true; } });
        edit.addActionListener(e -> {
            LnpComposition.Lipid l = selected(table, model);
            if (l == null) return;
            if (l.builtIn) { JOptionPane.showMessageDialog(parent, "Built-in lipids cannot be changed. Use 'Duplicate as private…' to start from it."); return; }
            if (editLipid(parent, l, false) != null) { model.reload(); changed[0] = true; }
        });
        dup.addActionListener(e -> {
            LnpComposition.Lipid l = selected(table, model);
            if (l != null && editLipid(parent, l, true) != null) { model.reload(); changed[0] = true; }
        });
        del.addActionListener(e -> {
            LnpComposition.Lipid l = selected(table, model);
            if (l == null || l.builtIn) return;
            List<String> used = LnpLipidLibrary.get().usedIn(l.name);
            String msg = "Delete '" + l.name + "' from your private library?"
                    + (used.isEmpty() ? "" : "\nIt is used in: " + String.join(", ", used) + " (those formulations will skip it).");
            if (JOptionPane.showConfirmDialog(parent, msg, "Delete lipid", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            try { LnpLipidLibrary.get().remove(l.name); model.reload(); changed[0] = true; }
            catch (IOException ex) { JOptionPane.showMessageDialog(parent, ex.getMessage()); }
        });
        delForm.addActionListener(e -> {
            String n = formList.getSelectedValue();
            if (n == null) return;
            try { LnpLipidLibrary.get().removeFormulation(n); reloadForms.run(); changed[0] = true; }
            catch (IOException ex) { JOptionPane.showMessageDialog(parent, ex.getMessage()); }
        });
        exp.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("lnp_lipid_library_export.json"));
            if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
            int withForms = JOptionPane.showConfirmDialog(parent, "Include your saved formulations?", "Export", JOptionPane.YES_NO_OPTION);
            try {
                LnpLipidLibrary.get().exportTo(fc.getSelectedFile(), withForms == JOptionPane.YES_OPTION);
                JOptionPane.showMessageDialog(parent, "Exported to " + fc.getSelectedFile());
            } catch (IOException ex) { JOptionPane.showMessageDialog(parent, "Export failed: " + ex.getMessage()); }
        });
        imp.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
            Object[] opts = {"Keep mine", "Replace with imported", "Cancel"};
            int ch = JOptionPane.showOptionDialog(parent, "If a lipid or formulation with the same name exists:", "Import",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
            if (ch < 0 || ch == 2) return;
            try {
                int[] n = LnpLipidLibrary.get().importFrom(fc.getSelectedFile(), ch == 1);
                model.reload(); reloadForms.run(); changed[0] = true;
                JOptionPane.showMessageDialog(parent, String.format("Imported %d lipid(s) and %d formulation(s); %d entr%s skipped "
                        + "(same name, built-in name, or incomplete).", n[0], n[1], n[2], n[2] == 1 ? "y" : "ies"));
            } catch (IOException ex) { JOptionPane.showMessageDialog(parent, "Import failed: " + ex.getMessage()); }
        });

        JPanel p = new JPanel(new BorderLayout(6, 6));
        JScrollPane ts = new JScrollPane(table);
        ts.setPreferredSize(new Dimension(820, 320));
        JPanel lipidsPane = new JPanel(new BorderLayout(4, 4));
        lipidsPane.add(ts, BorderLayout.CENTER);
        lipidsPane.add(Fields.flow(add, edit, dup, del, imp, exp), BorderLayout.SOUTH);
        JPanel formsPane = new JPanel(new BorderLayout(4, 4));
        formsPane.add(new JScrollPane(formList), BorderLayout.CENTER);
        formsPane.add(Fields.flow(delForm, new JLabel("Save formulations from the lipid table (Edit… next to Lipids)")), BorderLayout.SOUTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Lipids", lipidsPane);
        tabs.addTab("Saved formulations", formsPane);
        p.add(tabs, BorderLayout.CENTER);
        LnpLipidLibrary lib = LnpLipidLibrary.get();
        JLabel where = new JLabel("<html>Private lipids are stored only on this computer, in <code>" + lib.file().getAbsolutePath()
                + "</code>. Share them with colleagues with Export / Import." + (lib.loadError != null
                ? "<br><font color='#dc2626'>The library file could not be read: " + lib.loadError + ". It will not be overwritten.</font>" : "") + "</html>");
        where.setForeground(new Color(0x6e7781));
        p.add(where, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(parent, p, "Lipid library", JOptionPane.PLAIN_MESSAGE);
        return changed[0];
    }

    private static LnpComposition.Lipid selected(JTable t, LibraryModel m) {
        int r = t.getSelectedRow();
        return r < 0 ? null : m.rows.get(t.convertRowIndexToModel(r));
    }

    private static final class LibraryModel extends AbstractTableModel {
        List<LnpComposition.Lipid> rows = LnpComposition.allLipids();
        final String[] cols = {"Name", "Role", "Formula", "Electrons", "Volume (Å³)", "e/Å³", "Phase", "Source"};
        void reload() { rows = LnpComposition.allLipids(); fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            LnpComposition.Lipid l = rows.get(r);
            switch (c) {
                case 0: return l.name;
                case 1: return l.role.name().toLowerCase() + (l.pegUnits > 0 ? " (" + l.pegUnits + " EO)" : "");
                case 2: return l.formula;
                case 3: return String.format("%.0f", l.electrons);
                case 4: return String.format("%.0f", l.volume);
                case 5: return String.format("%.3f", l.density());
                case 6: return l.propensity;
                default: return l.builtIn ? "built-in" : "private";
            }
        }
    }
}
