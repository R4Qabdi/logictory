package com.logictory.ui.freeplay;

import com.logictory.auth.UserSession;
import com.logictory.game.Circuit;
import com.logictory.game.CircuitState;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import com.logictory.prefs.AppPreferences;
import com.logictory.prefs.PreferencesStore;
import com.logictory.schematic.SchematicException;
import com.logictory.schematic.SchematicStore;
import com.logictory.ui.PreferencesDialog;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** Freeplay board embedded in the main window: palette on the left, board in the centre. */
public final class FreeplayPanel extends JPanel {
    private final Circuit circuit = new Circuit();
    private final CircuitCanvas canvas = new CircuitCanvas(circuit);
    private final JLabel statusLabel = new JLabel(" ");
    private final Runnable onBack;
    private final Supplier<UserSession> sessionSupplier;
    private final PreferencesStore preferencesStore = new PreferencesStore();
    private final SchematicStore schematicStore = new SchematicStore();

    private AppPreferences preferences;
    private JMenuBar menuBar;
    private JComboBox<WireStyle> wireStyleCombo;
    private JCheckBoxMenuItem showGridItem;
    private JCheckBoxMenuItem snapToGridItem;
    private JButton undoButton;
    private JButton redoButton;
    private JButton selectAllButton;
    private JButton copyButton;
    private JButton cutButton;
    private JButton pasteButton;
    private JMenuItem undoMenuItem;
    private JMenuItem redoMenuItem;
    private JMenuItem selectAllMenuItem;
    private JMenuItem copyMenuItem;
    private JMenuItem cutMenuItem;
    private JMenuItem pasteMenuItem;
    private JMenuItem deleteMenuItem;
    private boolean syncingPreferences;

    public FreeplayPanel(Runnable onBack) {
        this(onBack, UserSession::guest);
    }

    public FreeplayPanel(Runnable onBack, Supplier<UserSession> sessionSupplier) {
        this.onBack = onBack;
        this.sessionSupplier = sessionSupplier;
        this.preferences = preferencesStore.load();
        this.canvas.applyPreferences(preferences);
        setLayout(new BorderLayout());

        menuBar = buildMenuBar();

        GatePalette palette = new GatePalette(canvas);
        JScrollPane paletteScroll = new JScrollPane(palette);
        paletteScroll.setBorder(null);
        paletteScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        paletteScroll.getViewport().setBackground(palette.getBackground());
        paletteScroll.getVerticalScrollBar().setUnitIncrement(16);

        add(buildToolbar(), BorderLayout.NORTH);
        add(paletteScroll, BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        canvas.setStatusSink(statusLabel::setText);
        seedDemo();
        canvas.refreshSimulation();
        canvas.setEditStateListener(this::updateEditState);
        syncPreferenceControls();
    }

    public JMenuBar menuBar() {
        return menuBar;
    }

    public void fitView() {
        canvas.fitView();
    }

    public void focusCanvas() {
        canvas.focusCanvas();
    }

    // ---------------------------------------------------------------- menus

    private JMenuBar buildMenuBar() {
        int mask = menuShortcutMask();
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(menuItem("Back to Menu", KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, onBack));
        file.addSeparator();
        file.add(menuItem("Save Schematic...", KeyEvent.VK_T, mask, this::saveSchematic));
        file.add(menuItem("Open Schematics...", KeyEvent.VK_O, mask, this::showSchematics));
        file.addSeparator();
        file.add(menuItem("Exit", KeyEvent.VK_Q, mask, () -> System.exit(0)));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        undoMenuItem = menuItem("Undo", KeyEvent.VK_Z, mask, canvas::undo);
        redoMenuItem = menuItem("Redo", KeyEvent.VK_Y, mask, canvas::redo);
        cutMenuItem = menuItem("Cut", KeyEvent.VK_X, mask, canvas::cutSelection);
        copyMenuItem = menuItem("Copy", KeyEvent.VK_C, mask, canvas::copySelection);
        pasteMenuItem = menuItem("Paste", KeyEvent.VK_V, mask, canvas::pasteClipboard);
        selectAllMenuItem = menuItem("Select All", KeyEvent.VK_A, mask, canvas::selectAll);
        deleteMenuItem = menuItem("Delete", KeyEvent.VK_DELETE, 0, canvas::deleteSelection);
        edit.add(undoMenuItem);
        edit.add(redoMenuItem);
        edit.addSeparator();
        edit.add(cutMenuItem);
        edit.add(copyMenuItem);
        edit.add(pasteMenuItem);
        edit.add(selectAllMenuItem);
        edit.add(deleteMenuItem);
        edit.addSeparator();
        edit.add(menuItem("Preferences...", KeyEvent.VK_COMMA, mask, this::showPreferences));
        bar.add(edit);

        JMenu view = new JMenu("View");
        view.add(menuItem("Fit View", KeyEvent.VK_0, mask, canvas::fitView));
        view.add(menuItem("Reset View", 0, 0, canvas::resetView));
        view.add(menuItem("Zoom In", KeyEvent.VK_EQUALS, mask, canvas::zoomIn));
        view.add(menuItem("Zoom Out", KeyEvent.VK_MINUS, mask, canvas::zoomOut));
        view.addSeparator();

        showGridItem = new JCheckBoxMenuItem("Show Grid", preferences.showGrid());
        showGridItem.addActionListener(e -> {
            if (!syncingPreferences) {
                updatePreferences(new AppPreferences(
                        preferences.wireStyle(), showGridItem.isSelected(), preferences.snapToGrid()));
            }
        });
        snapToGridItem = new JCheckBoxMenuItem("Snap to Grid", preferences.snapToGrid());
        snapToGridItem.addActionListener(e -> {
            if (!syncingPreferences) {
                updatePreferences(new AppPreferences(
                        preferences.wireStyle(), preferences.showGrid(), snapToGridItem.isSelected()));
            }
        });
        view.add(showGridItem);
        view.add(snapToGridItem);
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.add(menuItem("Controls & Shortcuts", 0, 0, this::showShortcuts));
        help.add(menuItem("About Logictory", 0, 0, this::showAbout));
        bar.add(help);

        return bar;
    }

    private static JMenuItem menuItem(String text, int keyCode, int modifiers, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (keyCode != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifiers));
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private static int menuShortcutMask() {
        try {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException e) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    // ---------------------------------------------------------------- preferences

    private void showPreferences() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new PreferencesDialog(owner, preferences, this::updatePreferences).setVisible(true);
    }

    private void updatePreferences(AppPreferences next) {
        preferences = next;
        canvas.applyPreferences(preferences);
        syncPreferenceControls();
        try {
            preferencesStore.save(preferences);
        } catch (IOException e) {
            statusLabel.setText("Could not save preferences: " + e.getMessage());
        }
    }

    private void syncPreferenceControls() {
        if (wireStyleCombo == null) {
            return;
        }
        syncingPreferences = true;
        try {
            wireStyleCombo.setSelectedItem(preferences.wireStyle());
            if (showGridItem != null) {
                showGridItem.setSelected(preferences.showGrid());
            }
            if (snapToGridItem != null) {
                snapToGridItem.setSelected(preferences.snapToGrid());
            }
        } finally {
            syncingPreferences = false;
        }
    }

    // ---------------------------------------------------------------- schematics

    private void saveSchematic() {
        CircuitState state = canvas.schematicState();
        String name = JOptionPane.showInputDialog(this,
                "Name this schematic:", "Save Schematic", JOptionPane.QUESTION_MESSAGE);
        if (name == null) {
            return;
        }
        UserSession session = sessionSupplier.get();
        String cleanName = name.trim();
        statusLabel.setText("Saving \"" + cleanName + "\"...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SchematicException {
                schematicStore.saveSaved(session, cleanName, state);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText(" ");
                    JOptionPane.showMessageDialog(FreeplayPanel.this,
                            "Saved \"" + cleanName + "\" "
                                    + (session.isGuest() ? "locally." : "to your account."),
                            "Save Schematic", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    statusLabel.setText(" ");
                    showError("Save Schematic",
                            cause == null ? "Could not save the schematic." : cause.getMessage());
                }
            }
        }.execute();
    }

    private void showSchematics() {
        showSchematics(SchematicsDialog.SAVED_TAB);
    }

    public void showSchematics(int initialTab) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new SchematicsDialog(owner, canvas, sessionSupplier, schematicStore, initialTab).setVisible(true);
    }

    // ---------------------------------------------------------------- help

    private void showShortcuts() {
        JOptionPane.showMessageDialog(this, """
                <html><b>Board controls</b><br>
                Click INPUT to toggle it<br>
                Drag output → input to wire<br>
                Drag empty space to select; Shift/Ctrl-click to add or remove<br>
                Drag a selected gate to move the selection<br>
                Hold Space and drag (or middle-drag) = pan; wheel = zoom<br><br>
                <b>Shortcuts</b><br>
                Ctrl+Z = undo; Ctrl+Y / Ctrl+Shift+Z = redo<br>
                Ctrl+C / Ctrl+X / Ctrl+V = copy / cut / paste<br>
                Ctrl+A = select all; Delete = remove<br>
                Ctrl+T = save selected gates as a schematic<br>
                Ctrl+O = open the schematics browser</html>
                """, "Controls & Shortcuts", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this, """
                <html><b>Logictory</b><br>
                A logic-circuit sandbox.<br>
                Build, wire, simulate and share circuits.</html>
                """, "About Logictory", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- toolbar

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(0, 6));
        bar.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel title = new JLabel("FREEPLAY SANDBOX");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        left.add(title);

        JLabel hint = new JLabel("Drag gates from the palette  •  click INPUT to toggle  •  drag output → input to wire  •  drag empty space to select");
        hint.setForeground(GatePainter.TEXT_DIM);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton schematics = new JButton("Schematics");
        schematics.setToolTipText("Browse saved and online schematics");
        schematics.setFocusable(false);
        schematics.addActionListener(e -> showSchematics());
        right.add(schematics);

        JLabel wireLabel = new JLabel("Wire:");
        wireLabel.setForeground(GatePainter.TEXT_DIM);
        wireLabel.setFont(wireLabel.getFont().deriveFont(Font.PLAIN, 12f));
        wireStyleCombo = new JComboBox<>(WireStyle.values());
        wireStyleCombo.setSelectedItem(preferences.wireStyle());
        wireStyleCombo.addActionListener(e -> {
            if (syncingPreferences) {
                return;
            }
            WireStyle style = (WireStyle) wireStyleCombo.getSelectedItem();
            if (style != null && style != preferences.wireStyle()) {
                updatePreferences(new AppPreferences(
                        style, preferences.showGrid(), preferences.snapToGrid()));
            }
        });
        right.add(wireLabel);
        right.add(wireStyleCombo);

        JButton fitView = new JButton("Fit View");
        fitView.setFocusable(false);
        fitView.addActionListener(e -> canvas.fitView());
        right.add(fitView);

        JButton clear = new JButton("Clear Board");
        clear.setFocusable(false);
        clear.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this, "Remove all gates and wires?",
                    "Clear Board", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                canvas.clearBoard();
            }
        });
        right.add(clear);

        top.add(left, BorderLayout.WEST);
        top.add(hint, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        JPanel edit = new JPanel(new BorderLayout(12, 0));
        JPanel editButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel editLabel = new JLabel("EDIT");
        editLabel.setForeground(GatePainter.TEXT_DIM);
        editLabel.setFont(editLabel.getFont().deriveFont(Font.BOLD, 11f));
        editButtons.add(editLabel);

        undoButton = editButton("Undo", "Undo last change (Ctrl+Z)", canvas::undo);
        redoButton = editButton("Redo", "Redo last change (Ctrl+Y or Ctrl+Shift+Z)", canvas::redo);
        selectAllButton = editButton("Select All", "Select every gate and wire (Ctrl+A)", canvas::selectAll);
        copyButton = editButton("Copy", "Copy selected gates (Ctrl+C)", canvas::copySelection);
        cutButton = editButton("Cut", "Cut selected gates (Ctrl+X)", canvas::cutSelection);
        pasteButton = editButton("Paste", "Paste copied gates (Ctrl+V)", canvas::pasteClipboard);
        editButtons.add(undoButton);
        editButtons.add(redoButton);
        editButtons.add(selectAllButton);
        editButtons.add(copyButton);
        editButtons.add(cutButton);
        editButtons.add(pasteButton);

        JLabel shortcuts = new JLabel("Ctrl+Z/Y undo  •  Ctrl+C/X/V copy  •  Ctrl+A select");
        shortcuts.setForeground(GatePainter.TEXT_DIM);
        shortcuts.setFont(shortcuts.getFont().deriveFont(Font.PLAIN, 11f));

        edit.add(editButtons, BorderLayout.WEST);
        edit.add(shortcuts, BorderLayout.EAST);

        bar.add(top, BorderLayout.NORTH);
        bar.add(edit, BorderLayout.SOUTH);
        return bar;
    }

    private static JButton editButton(String text, String tooltip, Runnable action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void updateEditState() {
        boolean canUndo = canvas.canUndo();
        boolean canRedo = canvas.canRedo();
        boolean hasItems = !circuit.gates().isEmpty() || !circuit.wires().isEmpty();
        boolean hasGateSelection = canvas.hasGateSelection();
        boolean hasSelection = canvas.hasSelection();
        boolean hasClipboard = canvas.hasClipboard();

        if (undoButton != null) {
            undoButton.setEnabled(canUndo);
            redoButton.setEnabled(canRedo);
            selectAllButton.setEnabled(hasItems);
            copyButton.setEnabled(hasGateSelection);
            cutButton.setEnabled(hasSelection);
            pasteButton.setEnabled(hasClipboard);
        }
        if (undoMenuItem != null) {
            undoMenuItem.setEnabled(canUndo);
            redoMenuItem.setEnabled(canRedo);
            selectAllMenuItem.setEnabled(hasItems);
            copyMenuItem.setEnabled(hasGateSelection);
            cutMenuItem.setEnabled(hasSelection);
            pasteMenuItem.setEnabled(hasClipboard);
            deleteMenuItem.setEnabled(hasSelection);
        }
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBorder(new EmptyBorder(6, 14, 8, 14));

        statusLabel.setForeground(GatePainter.TEXT_DIM);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        JLabel keys = new JLabel("Delete = remove  •  drag empty space = select  •  wheel = zoom  •  hold space + drag = pan");
        keys.setForeground(GatePainter.TEXT_DIM);
        keys.setFont(keys.getFont().deriveFont(Font.PLAIN, 11f));

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(keys, BorderLayout.EAST);
        return bar;
    }

    /** Half-adder example so the board is not empty on first open. */
    private void seedDemo() {
        Gate a = circuit.addGate(GateType.INPUT, 80, 100);
        Gate b = circuit.addGate(GateType.INPUT, 80, 240);
        Gate xor = circuit.addGate(GateType.XOR, 300, 100);
        Gate and = circuit.addGate(GateType.AND, 300, 240);
        Gate sum = circuit.addGate(GateType.OUTPUT, 520, 100);
        Gate carry = circuit.addGate(GateType.OUTPUT, 520, 240);

        circuit.connect(a.id(), xor.id(), 0);
        circuit.connect(b.id(), xor.id(), 1);
        circuit.connect(a.id(), and.id(), 0);
        circuit.connect(b.id(), and.id(), 1);
        circuit.connect(xor.id(), sum.id(), 0);
        circuit.connect(and.id(), carry.id(), 0);
    }
}
