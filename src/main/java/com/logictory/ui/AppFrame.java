package com.logictory.ui;

import com.logictory.auth.UserSession;
import com.logictory.ui.freeplay.FreeplayPanel;
import com.logictory.ui.freeplay.SchematicsDialog;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Toolkit;

/** The single application window; the menu and freeplay board are cards inside it. */
public final class AppFrame extends JFrame {
    private static final String MENU = "menu";
    private static final String LEVEL_SELECT = "level-select";
    private static final String FREEPLAY = "freeplay";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final FreeplayPanel freeplay;
    private UserSession session;
    private boolean freeplayShown;

    public AppFrame(UserSession session) {
        super("Logictory - Main Menu");
        this.session = session;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1180, Math.max(760, screen.width - 100)),
                Math.min(800, Math.max(560, screen.height - 120)));
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);

        MainMenuPanel menu = new MainMenuPanel(session, this::showFreeplay, this::setSession,
            this::showLevelSelect,
                () -> showFreeplay(SchematicsDialog.SAVED_TAB),
                () -> showFreeplay(SchematicsDialog.ONLINE_TAB));
        LocalLevelSelectPanel levelSelect = new LocalLevelSelectPanel(this::showMenu);
        freeplay = new FreeplayPanel(this::showMenu, () -> this.session);

        root.add(menu, MENU);
        root.add(levelSelect, LEVEL_SELECT);
        root.add(freeplay, FREEPLAY);
        setContentPane(root);
        showMenu();
    }

    private void setSession(UserSession session) {
        this.session = session;
    }

    public void showMenu() {
        setTitle("Logictory - Main Menu");
        setJMenuBar(null);
        cards.show(root, MENU);
    }

    public void showLevelSelect() {
        setTitle("Logictory - Local Campaign");
        setJMenuBar(null);
        cards.show(root, LEVEL_SELECT);
    }

    public void showFreeplay() {
        showFreeplay(-1);
    }

    /** Switches to the board and optionally opens the schematics browser on a tab. */
    private void showFreeplay(int schematicsTab) {
        setTitle("Logictory - Freeplay");
        setJMenuBar(freeplay.menuBar());
        cards.show(root, FREEPLAY);
        boolean firstShow = !freeplayShown;
        freeplayShown = true;
        SwingUtilities.invokeLater(() -> {
            if (firstShow) {
                freeplay.fitView();
            }
            freeplay.focusCanvas();
            if (schematicsTab >= 0) {
                freeplay.showSchematics(schematicsTab);
            }
        });
    }
}
