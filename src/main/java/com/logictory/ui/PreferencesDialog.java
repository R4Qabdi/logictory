package com.logictory.ui;

import com.logictory.prefs.AppPreferences;
import com.logictory.ui.freeplay.GatePainter;
import com.logictory.ui.freeplay.WireStyle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.function.Consumer;

/**
 * Preferences dialog. Every change is applied and persisted immediately, so
 * closing the dialog never loses an edit.
 */
public final class PreferencesDialog extends JDialog {
    private final Consumer<AppPreferences> onChange;
    private final JComboBox<WireStyle> wireStyle = new JComboBox<>(WireStyle.values());
    private final JCheckBox showGrid = new JCheckBox("Show grid");
    private final JCheckBox snapToGrid = new JCheckBox("Snap gates to grid");
    private boolean updating;

    public PreferencesDialog(Window owner, AppPreferences current, Consumer<AppPreferences> onChange) {
        super(owner, "Preferences", ModalityType.APPLICATION_MODAL);
        this.onChange = onChange;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        wireStyle.setSelectedItem(current.wireStyle());
        showGrid.setSelected(current.showGrid());
        snapToGrid.setSelected(current.snapToGrid());

        wireStyle.addActionListener(e -> notifyChange());
        showGrid.addActionListener(e -> notifyChange());
        snapToGrid.addActionListener(e -> notifyChange());

        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));

        JLabel title = new JLabel("Freeplay Preferences");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Wire style:"), gbc);
        gbc.gridx = 1;
        form.add(wireStyle, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        form.add(showGrid, gbc);

        gbc.gridy = 2;
        form.add(snapToGrid, gbc);

        gbc.gridy = 3;
        JLabel note = new JLabel("Changes are saved automatically.");
        note.setForeground(GatePainter.TEXT_DIM);
        note.setFont(note.getFont().deriveFont(Font.PLAIN, 11f));
        form.add(note, gbc);

        root.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());
        JButton reset = new JButton("Reset to Defaults");
        reset.addActionListener(e -> resetDefaults());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(close);
        buttons.add(reset, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        root.add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(close);
        return root;
    }

    private void resetDefaults() {
        AppPreferences defaults = AppPreferences.defaults();
        updating = true;
        wireStyle.setSelectedItem(defaults.wireStyle());
        showGrid.setSelected(defaults.showGrid());
        snapToGrid.setSelected(defaults.snapToGrid());
        updating = false;
        notifyChange();
    }

    private void notifyChange() {
        if (updating) {
            return;
        }
        onChange.accept(new AppPreferences(
                (WireStyle) wireStyle.getSelectedItem(),
                showGrid.isSelected(),
                snapToGrid.isSelected()));
    }
}
