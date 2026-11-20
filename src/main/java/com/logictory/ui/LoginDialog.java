package com.logictory.ui;

import com.logictory.auth.AuthService;
import com.logictory.auth.UserSession;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class LoginDialog extends JDialog {
    private final AuthService authService = new AuthService();
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel statusLabel = new JLabel(" ");
    private UserSession session;

    public LoginDialog(Frame owner) {
        super(owner, "Logictory - Sign In", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("Welcome to Logictory", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        form.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        form.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        statusLabel.setForeground(new Color(220, 90, 90));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        form.add(statusLabel, gbc);

        root.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 8, 8));
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");
        JButton guestBtn = new JButton("Play as Guest");
        JButton exitBtn = new JButton("Exit");

        loginBtn.addActionListener(e -> handleLogin());
        registerBtn.addActionListener(e -> handleRegister());
        guestBtn.addActionListener(e -> {
            session = UserSession.guest();
            dispose();
        });
        exitBtn.addActionListener(e -> {
            session = null;
            dispose();
        });

        buttons.add(loginBtn);
        buttons.add(registerBtn);
        buttons.add(guestBtn);
        buttons.add(exitBtn);

        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(loginBtn);
    }

    private void handleLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        try {
            UserSession result = authService.login(username, password);
            if (result != null) {
                session = result;
                dispose();
            } else {
                statusLabel.setText("Invalid username or password.");
            }
        } catch (SQLException ex) {
            statusLabel.setText("Database unavailable. Try Guest mode.");
        }
    }

    private void handleRegister() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        try {
            session = authService.register(username, password);
            dispose();
        } catch (IllegalArgumentException ex) {
            statusLabel.setText(ex.getMessage());
        } catch (SQLException ex) {
            statusLabel.setText("Registration failed (username may exist or DB offline).");
        }
    }

    public UserSession showDialog() {
        setVisible(true);
        return session;
    }
}
