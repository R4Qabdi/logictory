package com.logictory.ui;

import com.logictory.auth.UserSession;
import com.logictory.ui.freeplay.FreeplayPanel;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Toolkit;

/** The single application window; the menu and freeplay board are cards inside it. */
public final class AppFrame extends JFrame {
    private static final String MENU = "menu";
    private static final String FREEPLAY = "freeplay";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final FreeplayPanel freeplay;
    private boolean freeplayShown;

    public AppFrame(UserSession session) {
        super("Logictory - Main Menu");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1180, Math.max(760, screen.width - 100)),
                Math.min(800, Math.max(560, screen.height - 120)));
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);

        MainMenuPanel menu = new MainMenuPanel(session, this::showFreeplay);
        freeplay = new FreeplayPanel(this::showMenu);

        root.add(menu, MENU);
        root.add(freeplay, FREEPLAY);
        setContentPane(root);
        showMenu();
    }

    public void showMenu() {
        setTitle("Logictory - Main Menu");
        cards.show(root, MENU);
    }

    public void showFreeplay() {
        setTitle("Logictory - Freeplay");
        cards.show(root, FREEPLAY);
        if (!freeplayShown) {
            freeplayShown = true;
            SwingUtilities.invokeLater(freeplay::fitView);
        }
    }
}
