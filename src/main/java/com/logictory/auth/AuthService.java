package com.logictory.auth;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AuthService {

    public UserSession register(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.length() < 4) {
            throw new IllegalArgumentException("Username cannot be empty and password must be at least 4 characters.");
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return UserSession.authenticated(keys.getInt(1), username.trim());
                }
            }
        }
        throw new SQLException("Failed to create user.");
    }

    public UserSession login(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null) {
            return null;
        }

        String sql = "SELECT id, username, password_hash FROM users WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    if (BCrypt.checkpw(password, storedHash)) {
                        return UserSession.authenticated(rs.getInt("id"), rs.getString("username"));
                    }
                }
            }
        }
        return null;
    }
}
