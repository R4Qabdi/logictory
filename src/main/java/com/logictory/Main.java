package com.logictory;

import com.formdev.flatlaf.FlatDarkLaf;
import com.logictory.auth.DatabaseConfig;
import com.logictory.auth.UserSession;
import com.logictory.ui.AppFrame;

import javax.swing.*;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        FlatDarkLaf.setup();

        try {
            DatabaseConfig.initializeSchema();
        } catch (SQLException e) {
            System.err.println("Warning: Could not initialize MySQL schema (" + e.getMessage() + "). Guest mode remains available.");
        }

        SwingUtilities.invokeLater(() -> {
            // Start directly on the main menu as a guest; sign-in is available from there.
            UserSession session = UserSession.guest();
            new AppFrame(session).setVisible(true);
        });
    }
}
