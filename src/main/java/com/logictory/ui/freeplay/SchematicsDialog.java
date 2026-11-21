package com.logictory.ui.freeplay;

import com.logictory.auth.UserSession;
import com.logictory.game.CircuitState;
import com.logictory.schematic.SchematicException;
import com.logictory.schematic.SchematicStore;
import com.logictory.schematic.SchematicSummary;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Browse, search, and copy saved and community schematics to the clipboard.
 * Saving a schematic requires a gate selection on the board.
 *
 * <p>All database/file access runs on worker threads; the dialog stays responsive
 * even when MySQL is slow or offline.</p>
 */
public final class SchematicsDialog extends JDialog {
    public static final int SAVED_TAB = 0;
    public static final int ONLINE_TAB = 1;
    private static final Color ERROR = new Color(0xd97777);

    private final CircuitCanvas canvas;
    private final Supplier<UserSession> sessionSupplier;
    private final SchematicStore store;

    private final JTextField searchField = new JTextField(20);
    private final DefaultListModel<SchematicSummary> savedModel = new DefaultListModel<>();
    private final DefaultListModel<SchematicSummary> onlineModel = new DefaultListModel<>();
    private final JList<SchematicSummary> savedList = new JList<>(savedModel);
    private final JList<SchematicSummary> onlineList = new JList<>(onlineModel);
    private final JLabel savedStatus = new JLabel(" ");
    private final JLabel onlineStatus = new JLabel(" ");
    private final JTabbedPane tabs = new JTabbedPane();

    private final JButton savedCopy = new JButton("Copy to Clipboard");
    private final JButton savedPublish = new JButton("Publish Online");
    private final JButton savedDelete = new JButton("Delete");
    private final JButton onlineCopy = new JButton("Copy to Clipboard");

    private List<SchematicSummary> savedAll = List.of();
    private List<SchematicSummary> onlineAll = List.of();

    private int savedRequest;
    private int onlineRequest;
    private String pendingSelection;

    public SchematicsDialog(Window owner, CircuitCanvas canvas,
                            Supplier<UserSession> sessionSupplier, SchematicStore store) {
        this(owner, canvas, sessionSupplier, store, SAVED_TAB);
    }

    public SchematicsDialog(Window owner, CircuitCanvas canvas,
                            Supplier<UserSession> sessionSupplier, SchematicStore store,
                            int initialTab) {
        super(owner, "Schematics", ModalityType.APPLICATION_MODAL);
        this.canvas = canvas;
        this.sessionSupplier = sessionSupplier;
        this.store = store;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        setPreferredSize(new Dimension(760, 520));
        setMinimumSize(new Dimension(620, 420));
        pack();
        setLocationRelativeTo(owner);
        if (initialTab == ONLINE_TAB) {
            tabs.setSelectedIndex(ONLINE_TAB);
        }

        refreshSaved();
        refreshOnline(false);
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        JLabel title = new JLabel("Schematics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JLabel searchLabel = new JLabel("Search by name:");
        searchLabel.setForeground(GatePainter.TEXT_DIM);
        searchField.putClientProperty("JTextField.placeholderText", "Type a schematic name");
        search.add(searchLabel);
        search.add(searchField);
        header.add(title, BorderLayout.WEST);
        header.add(search, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        savedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        savedList.setCellRenderer(new SchematicCellRenderer());
        onlineList.setCellRenderer(new SchematicCellRenderer());

        savedStatus.setForeground(GatePainter.TEXT_DIM);
        savedStatus.setFont(savedStatus.getFont().deriveFont(Font.PLAIN, 11f));
        onlineStatus.setForeground(GatePainter.TEXT_DIM);
        onlineStatus.setFont(onlineStatus.getFont().deriveFont(Font.PLAIN, 11f));

        tabs.addTab("Saved", buildSavedTab());
        tabs.addTab("Online", buildOnlineTab());
        root.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        JLabel hint = new JLabel("Save the selected gates, or the whole board when nothing is selected.");
        hint.setForeground(GatePainter.TEXT_DIM);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        footer.add(hint, BorderLayout.WEST);
        footer.add(close, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        savedList.addListSelectionListener(e -> updateSelectionButtons());
        onlineList.addListSelectionListener(e -> updateSelectionButtons());

        savedCopy.addActionListener(e -> copySelected(savedList, true));
        savedPublish.addActionListener(e -> publishSaved());
        savedDelete.addActionListener(e -> deleteSaved());
        onlineCopy.addActionListener(e -> copySelected(onlineList, false));

        getRootPane().setDefaultButton(close);
        return root;
    }

    private JPanel buildSavedTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 6, 6, 6));

        JScrollPane scroll = new JScrollPane(savedList);
        scroll.setBorder(BorderFactory.createLineBorder(GatePainter.BORDER));
        panel.add(savedStatus, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton saveSelection = new JButton("Save Schematic...");
        saveSelection.addActionListener(e -> saveSelection());
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshSaved());
        buttons.add(saveSelection);
        buttons.add(savedCopy);
        buttons.add(savedPublish);
        buttons.add(savedDelete);
        buttons.add(refresh);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildOnlineTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 6, 6, 6));

        JScrollPane scroll = new JScrollPane(onlineList);
        scroll.setBorder(BorderFactory.createLineBorder(GatePainter.BORDER));
        panel.add(onlineStatus, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshOnline(true));
        buttons.add(onlineCopy);
        buttons.add(refresh);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshSaved() {
        UserSession session = sessionSupplier.get();
        int request = ++savedRequest;
        savedStatus.setForeground(GatePainter.TEXT_DIM);
        savedStatus.setText("Loading saved schematics...");

        new SwingWorker<LoadResult<List<SchematicSummary>>, Void>() {
            @Override
            protected LoadResult<List<SchematicSummary>> doInBackground() {
                try {
                    return new LoadResult<>(store.listSavedSummaries(session), null);
                } catch (SchematicException e) {
                    try {
                        return new LoadResult<>(store.listLocalSummaries(), e.getMessage());
                    } catch (SchematicException localFailure) {
                        return new LoadResult<>(List.of(), e.getMessage());
                    }
                }
            }

            @Override
            protected void done() {
                if (request != savedRequest || isCancelled()) {
                    return;
                }
                try {
                    LoadResult<List<SchematicSummary>> result = get();
                    savedAll = result.value();
                    if (result.warning() == null) {
                        savedStatus.setForeground(GatePainter.TEXT_DIM);
                        savedStatus.setText(savedAll.isEmpty()
                                ? "No saved schematics yet."
                                : savedAll.size() + " saved schematic(s).");
                    } else {
                        savedStatus.setForeground(ERROR);
                        savedStatus.setText(savedAll.isEmpty()
                                ? result.warning()
                                : result.warning() + " Showing " + savedAll.size() + " local schematic(s).");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    savedAll = List.of();
                    savedStatus.setForeground(ERROR);
                    savedStatus.setText("Could not load saved schematics.");
                }
                applyFilter();
                if (pendingSelection != null) {
                    selectByName(savedList, savedModel, pendingSelection);
                    pendingSelection = null;
                }
            }
        }.execute();
    }

    private void refreshOnline(boolean force) {
        int request = ++onlineRequest;
        onlineStatus.setForeground(GatePainter.TEXT_DIM);
        onlineStatus.setText("Loading online schematics...");

        new SwingWorker<LoadResult<List<SchematicSummary>>, Void>() {
            @Override
            protected LoadResult<List<SchematicSummary>> doInBackground() {
                try {
                    return new LoadResult<>(store.listOnlineSummaries(force), null);
                } catch (SchematicException e) {
                    return new LoadResult<>(List.of(), e.getMessage());
                }
            }

            @Override
            protected void done() {
                if (request != onlineRequest || isCancelled()) {
                    return;
                }
                try {
                    LoadResult<List<SchematicSummary>> result = get();
                    onlineAll = result.value();
                    if (result.warning() == null) {
                        onlineStatus.setForeground(GatePainter.TEXT_DIM);
                        onlineStatus.setText(onlineAll.isEmpty()
                                ? "No online schematics found."
                                : onlineAll.size() + " online schematic(s).");
                    } else {
                        onlineStatus.setForeground(ERROR);
                        onlineStatus.setText(result.warning());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    onlineAll = List.of();
                    onlineStatus.setForeground(ERROR);
                    onlineStatus.setText("Could not load online schematics.");
                }
                applyFilter();
            }
        }.execute();
    }

    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        savedModel.clear();
        for (SchematicSummary schematic : savedAll) {
            if (matches(schematic, query)) {
                savedModel.addElement(schematic);
            }
        }
        onlineModel.clear();
        for (SchematicSummary schematic : onlineAll) {
            if (matches(schematic, query)) {
                onlineModel.addElement(schematic);
            }
        }
        updateSelectionButtons();
    }

    private static boolean matches(SchematicSummary schematic, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (schematic.name().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        return schematic.author() != null
                && schematic.author().toLowerCase(Locale.ROOT).contains(query);
    }

    private void updateSelectionButtons() {
        boolean savedSelected = savedList.getSelectedValue() != null;
        boolean onlineSelected = onlineList.getSelectedValue() != null;
        savedCopy.setEnabled(savedSelected);
        savedPublish.setEnabled(savedSelected);
        savedDelete.setEnabled(savedSelected);
        onlineCopy.setEnabled(onlineSelected);
    }

    private void saveSelection() {
        CircuitState state = canvas.schematicState();
        String name = JOptionPane.showInputDialog(this,
                "Name this schematic:", "Save Schematic", JOptionPane.QUESTION_MESSAGE);
        if (name == null) {
            return;
        }
        String cleanName = name.trim();
        UserSession session = sessionSupplier.get();
        savedStatus.setForeground(GatePainter.TEXT_DIM);
        savedStatus.setText("Saving \"" + cleanName + "\"...");
        tabs.setSelectedIndex(SAVED_TAB);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SchematicException {
                store.saveSaved(session, cleanName, state);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    pendingSelection = cleanName;
                    refreshSaved();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    savedStatus.setForeground(ERROR);
                    savedStatus.setText("Could not save the schematic.");
                    showError(cause == null ? "Could not save the schematic." : cause.getMessage());
                    refreshSaved();
                }
            }
        }.execute();
    }

    private void copySelected(JList<SchematicSummary> list, boolean saved) {
        SchematicSummary schematic = list.getSelectedValue();
        if (schematic == null) {
            return;
        }
        JLabel status = saved ? savedStatus : onlineStatus;
        status.setForeground(GatePainter.TEXT_DIM);
        status.setText("Loading \"" + schematic.name() + "\"...");
        UserSession session = sessionSupplier.get();

        new SwingWorker<CircuitState, Void>() {
            @Override
            protected CircuitState doInBackground() throws SchematicException {
                return saved ? store.loadSaved(session, schematic) : store.loadOnline(schematic);
            }

            @Override
            protected void done() {
                try {
                    canvas.copyState(get());
                    status.setForeground(GatePainter.TEXT_DIM);
                    status.setText("Copied \"" + schematic.name() + "\" to the clipboard.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    status.setForeground(ERROR);
                    status.setText(cause == null ? "Could not load the schematic." : cause.getMessage());
                    showError(cause == null ? "Could not load the schematic." : cause.getMessage());
                }
            }
        }.execute();
    }

    private void publishSaved() {
        SchematicSummary schematic = savedList.getSelectedValue();
        if (schematic == null) {
            return;
        }
        UserSession session = sessionSupplier.get();
        if (session.isGuest()) {
            JOptionPane.showMessageDialog(this,
                    "Sign in with an account to publish schematics online.",
                    "Publish Online", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        savedStatus.setForeground(GatePainter.TEXT_DIM);
        savedStatus.setText("Publishing \"" + schematic.name() + "\"...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SchematicException {
                CircuitState state = store.loadSaved(session, schematic);
                store.publish(session, schematic.name(), state);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    tabs.setSelectedIndex(ONLINE_TAB);
                    refreshOnline(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showError(cause == null ? "Could not publish the schematic." : cause.getMessage());
                    refreshSaved();
                }
            }
        }.execute();
    }

    private void deleteSaved() {
        SchematicSummary schematic = savedList.getSelectedValue();
        if (schematic == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete saved schematic \"" + schematic.name() + "\"?",
                "Delete Schematic", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        UserSession session = sessionSupplier.get();
        savedStatus.setForeground(GatePainter.TEXT_DIM);
        savedStatus.setText("Deleting \"" + schematic.name() + "\"...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SchematicException {
                store.deleteSaved(session, schematic);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    showError(cause == null ? "Could not delete the schematic." : cause.getMessage());
                }
                refreshSaved();
            }
        }.execute();
    }

    private void selectByName(JList<SchematicSummary> list, DefaultListModel<SchematicSummary> model, String name) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).name().equalsIgnoreCase(name)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Schematics", JOptionPane.ERROR_MESSAGE);
    }

    private record LoadResult<T>(T value, String warning) {
    }

    private static final class SchematicCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof SchematicSummary schematic) {
                String text = schematic.name();
                if (schematic.author() != null && !schematic.author().isBlank()) {
                    text += "   —   " + schematic.author();
                }
                if (schematic.local()) {
                    text += "   (local)";
                }
                setText(text);
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            }
            return this;
        }
    }
}
