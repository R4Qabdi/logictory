package com.logictory.ui;

import com.logictory.auth.UserSession;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

/** Main menu content, embedded in the single application window. */
public final class MainMenuPanel extends JPanel {
    private final Runnable onFreeplay;
    private final Consumer<UserSession> onSessionChanged;
    private final Runnable onLocalLevels;
    private final Runnable onSavedSchematics;
    private final Runnable onOnlineLevels;
    private UserSession session;
    private final JLabel userStatusLabel = new JLabel();
    private JButton onlineLevelsBtn;
    private JButton schematicsBtn;
    private JButton accountBtn;

    public MainMenuPanel(UserSession session, Runnable onFreeplay, Consumer<UserSession> onSessionChanged) {
        this(session, onFreeplay, onSessionChanged, null, null);
    }

    public MainMenuPanel(UserSession session, Runnable onFreeplay, Consumer<UserSession> onSessionChanged,
                         Runnable onSavedSchematics, Runnable onOnlineLevels) {
        this(session, onFreeplay, onSessionChanged, null, onSavedSchematics, onOnlineLevels);
    }

    public MainMenuPanel(UserSession session, Runnable onFreeplay, Consumer<UserSession> onSessionChanged,
                         Runnable onLocalLevels, Runnable onSavedSchematics, Runnable onOnlineLevels) {
        this.session = session;
        this.onFreeplay = onFreeplay;
        this.onSessionChanged = onSessionChanged;
        this.onLocalLevels = onLocalLevels;
        this.onSavedSchematics = onSavedSchematics;
        this.onOnlineLevels = onOnlineLevels;

        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel card = new JPanel(new BorderLayout(16, 16));
        card.setPreferredSize(new Dimension(520, 500));
        card.setBorder(new EmptyBorder(28, 40, 28, 40));
        card.add(buildHeader(), BorderLayout.NORTH);
        card.add(buildMenu(), BorderLayout.CENTER);

        add(card);
        refreshSessionState();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridLayout(3, 1, 4, 4));

        JLabel title = new JLabel("LOGICTORY", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 34f));

        JLabel subtitle = new JLabel("Educational Logic Gate Tower Defense", SwingConstants.CENTER);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 14f));
        subtitle.setForeground(new Color(0x9aa0b0));

        userStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        userStatusLabel.setFont(userStatusLabel.getFont().deriveFont(Font.BOLD, 12f));

        header.add(title);
        header.add(subtitle);
        header.add(userStatusLabel);
        return header;
    }

    private JPanel buildMenu() {
        JPanel menuPanel = new JPanel(new GridLayout(6, 1, 10, 10));

        JButton playLocalBtn = new JButton("Play Local Campaign");
        playLocalBtn.setFont(playLocalBtn.getFont().deriveFont(Font.BOLD, 15f));
        playLocalBtn.addActionListener(e -> {
            if (onLocalLevels != null) {
                onLocalLevels.run();
            }
        });

        JButton freeplayBtn = new JButton("Freeplay (Sandbox)");
        freeplayBtn.setFont(freeplayBtn.getFont().deriveFont(Font.BOLD, 15f));
        freeplayBtn.addActionListener(e -> onFreeplay.run());

        onlineLevelsBtn = new JButton("Online Schematics");
        onlineLevelsBtn.setFont(onlineLevelsBtn.getFont().deriveFont(15f));
        onlineLevelsBtn.addActionListener(e -> {
            if (onOnlineLevels != null) {
                onOnlineLevels.run();
            }
        });

        schematicsBtn = new JButton("My Schematics (Upload / Save)");
        schematicsBtn.setFont(schematicsBtn.getFont().deriveFont(15f));
        schematicsBtn.addActionListener(e -> {
            if (onSavedSchematics != null) {
                onSavedSchematics.run();
            }
        });

        accountBtn = new JButton();
        accountBtn.setFont(accountBtn.getFont().deriveFont(15f));
        accountBtn.addActionListener(e -> switchAccount());

        JButton exitBtn = new JButton("Exit");
        exitBtn.setFont(exitBtn.getFont().deriveFont(15f));
        exitBtn.addActionListener(e -> System.exit(0));

        menuPanel.add(playLocalBtn);
        menuPanel.add(freeplayBtn);
        menuPanel.add(onlineLevelsBtn);
        menuPanel.add(schematicsBtn);
        menuPanel.add(accountBtn);
        menuPanel.add(exitBtn);
        return menuPanel;
    }

    private void refreshSessionState() {
        boolean isGuest = session.isGuest();
        if (isGuest) {
            userStatusLabel.setText("Guest Mode (Online features disabled)");
            userStatusLabel.setForeground(new Color(230, 165, 60));
            accountBtn.setText("Sign In / Register");
        } else {
            userStatusLabel.setText("Signed in as: " + session.getUsername());
            userStatusLabel.setForeground(new Color(95, 200, 120));
            accountBtn.setText("Logout");
        }

        // Guests can still browse community circuits and manage local schematics;
        // only publishing and database-backed saves require an account.
        onlineLevelsBtn.setEnabled(true);
        schematicsBtn.setEnabled(true);
        onlineLevelsBtn.setToolTipText(isGuest
                ? "Browse community schematics; sign in to publish."
                : "Browse and copy community schematics.");
        schematicsBtn.setToolTipText(isGuest
                ? "Manage local schematics; sign in to save to your account."
                : "Manage and publish your schematics.");
    }

    private void switchAccount() {
        Frame owner = SwingUtilities.getWindowAncestor(this) instanceof Frame frame ? frame : null;
        LoginDialog dialog = new LoginDialog(owner);
        UserSession newSession = dialog.showDialog();
        if (newSession != null) {
            this.session = newSession;
            onSessionChanged.accept(newSession);
            refreshSessionState();
        }
    }
}
