package com.logictory.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {
    private static final String DEFAULT_URL = System.getenv().getOrDefault(
            "LOGICTORY_DB_URL", "jdbc:mysql://localhost:3306/logictory?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
    );
    private static final String DEFAULT_USER = System.getenv().getOrDefault("LOGICTORY_DB_USER", "root");
    private static final String DEFAULT_PASSWORD = System.getenv().getOrDefault("LOGICTORY_DB_PASSWORD", "");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DEFAULT_URL, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    public static void initializeSchema() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schematics (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id INT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        data_json TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS online_levels (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        author_id INT NOT NULL,
                        name VARCHAR(100) NOT NULL,
                        level_json TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
        }
    }
}
