package com.logictory.auth;

import com.logictory.game.Circuit;
import com.logictory.game.CircuitState;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import com.logictory.schematic.SchematicCodec;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

public class DatabaseConfig {
    private static final String DEFAULT_URL = System.getenv().getOrDefault(
            "LOGICTORY_DB_URL",
            "jdbc:mysql://localhost:3306/logictory"
                    + "?createDatabaseIfNotExist=true"
                    + "&useSSL=false"
                    + "&allowPublicKeyRetrieval=true"
                    + "&connectTimeout=3000"
                    + "&socketTimeout=10000"
    );
    private static final String DEFAULT_USER = System.getenv().getOrDefault("LOGICTORY_DB_USER", "root");
    private static final String DEFAULT_PASSWORD = System.getenv().getOrDefault("LOGICTORY_DB_PASSWORD", "");
    private static final String SEED_AUTHOR = "logictory_team";

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
                        is_public BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                    """);
            ensureSchematicsPublicColumn(conn);
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

            try {
                seedOnlineSamples(conn);
            } catch (SQLException | RuntimeException e) {
                // Sample content is optional: never prevent the app from starting.
                System.err.println("Warning: Could not seed example online schematics (" + e.getMessage() + ").");
            }
        }
    }

    private static void ensureSchematicsPublicColumn(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM schematics LIKE 'is_public'")) {
            if (!rs.next()) {
                stmt.execute("ALTER TABLE schematics ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE");
            }
        }
    }

    /**
     * Adds a small set of example circuits to the Online tab the first time the
     * database is created. Re-running is harmless: the samples are skipped once
     * the seed author owns at least one online schematic.
     */
    private static void seedOnlineSamples(Connection conn) throws SQLException {
        int authorId = ensureSeedAuthor(conn);

        try (PreparedStatement count = conn.prepareStatement(
                "SELECT COUNT(*) FROM schematics WHERE user_id = ? AND is_public = TRUE")) {
            count.setInt(1, authorId);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        List<SeedSchematic> samples = List.of(
                new SeedSchematic("Half Adder", halfAdder()),
                new SeedSchematic("Full Adder", fullAdder()),
                new SeedSchematic("2-to-1 Multiplexer", multiplexer()),
                new SeedSchematic("SR Latch", srLatch()),
                new SeedSchematic("NAND-only XOR", nandXor()));

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO schematics (user_id, name, data_json, is_public) VALUES (?, ?, ?, TRUE)")) {
            for (SeedSchematic sample : samples) {
                insert.setInt(1, authorId);
                insert.setString(2, sample.name());
                insert.setString(3, SchematicCodec.encode(sample.circuit()));
                insert.executeUpdate();
            }
        }
    }

    private static int ensureSeedAuthor(Connection conn) throws SQLException {
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT id FROM users WHERE username = ?")) {
            select.setString(1, SEED_AUTHOR);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        String unusablePassword = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt(10));
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, SEED_AUTHOR);
            insert.setString(2, unusablePassword);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        // Another process may have created the account between the SELECT and INSERT.
        try (PreparedStatement select = conn.prepareStatement(
                "SELECT id FROM users WHERE username = ?")) {
            select.setString(1, SEED_AUTHOR);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Could not create the seed author account.");
    }

    private static CircuitState halfAdder() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 60, 80);
        Gate b = circuit.addGate(GateType.INPUT, 60, 220);
        Gate xor = circuit.addGate(GateType.XOR, 300, 80);
        Gate and = circuit.addGate(GateType.AND, 300, 220);
        Gate sum = circuit.addGate(GateType.OUTPUT, 540, 80);
        Gate carry = circuit.addGate(GateType.OUTPUT, 540, 220);

        circuit.connect(a.id(), xor.id(), 0);
        circuit.connect(b.id(), xor.id(), 1);
        circuit.connect(a.id(), and.id(), 0);
        circuit.connect(b.id(), and.id(), 1);
        circuit.connect(xor.id(), sum.id(), 0);
        circuit.connect(and.id(), carry.id(), 0);
        return circuit.snapshot();
    }

    private static CircuitState fullAdder() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 40, 40);
        Gate b = circuit.addGate(GateType.INPUT, 40, 160);
        Gate carryIn = circuit.addGate(GateType.INPUT, 40, 280);
        Gate xor1 = circuit.addGate(GateType.XOR, 230, 40);
        Gate xor2 = circuit.addGate(GateType.XOR, 420, 160);
        Gate and1 = circuit.addGate(GateType.AND, 420, 40);
        Gate and2 = circuit.addGate(GateType.AND, 420, 280);
        Gate or = circuit.addGate(GateType.OR, 610, 160);
        Gate sum = circuit.addGate(GateType.OUTPUT, 800, 160);
        Gate carryOut = circuit.addGate(GateType.OUTPUT, 800, 40);

        circuit.connect(a.id(), xor1.id(), 0);
        circuit.connect(b.id(), xor1.id(), 1);
        circuit.connect(xor1.id(), xor2.id(), 0);
        circuit.connect(carryIn.id(), xor2.id(), 1);
        circuit.connect(a.id(), and1.id(), 0);
        circuit.connect(b.id(), and1.id(), 1);
        circuit.connect(xor1.id(), and2.id(), 0);
        circuit.connect(carryIn.id(), and2.id(), 1);
        circuit.connect(and1.id(), or.id(), 0);
        circuit.connect(and2.id(), or.id(), 1);
        circuit.connect(xor2.id(), sum.id(), 0);
        circuit.connect(or.id(), carryOut.id(), 0);
        return circuit.snapshot();
    }

    private static CircuitState multiplexer() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 40, 60);
        Gate b = circuit.addGate(GateType.INPUT, 40, 200);
        Gate select = circuit.addGate(GateType.INPUT, 40, 340);
        Gate not = circuit.addGate(GateType.NOT, 230, 340);
        Gate andA = circuit.addGate(GateType.AND, 420, 60);
        Gate andB = circuit.addGate(GateType.AND, 420, 200);
        Gate or = circuit.addGate(GateType.OR, 610, 130);
        Gate out = circuit.addGate(GateType.OUTPUT, 800, 130);

        circuit.connect(select.id(), not.id(), 0);
        circuit.connect(a.id(), andA.id(), 0);
        circuit.connect(not.id(), andA.id(), 1);
        circuit.connect(b.id(), andB.id(), 0);
        circuit.connect(select.id(), andB.id(), 1);
        circuit.connect(andA.id(), or.id(), 0);
        circuit.connect(andB.id(), or.id(), 1);
        circuit.connect(or.id(), out.id(), 0);
        return circuit.snapshot();
    }

    private static CircuitState srLatch() {
        Circuit circuit = new Circuit();
        Gate set = circuit.addGate(GateType.INPUT, 40, 80);
        Gate reset = circuit.addGate(GateType.INPUT, 40, 300);
        Gate norSet = circuit.addGate(GateType.NOR, 280, 80);
        Gate norReset = circuit.addGate(GateType.NOR, 280, 300);
        Gate q = circuit.addGate(GateType.OUTPUT, 540, 80);
        Gate qNot = circuit.addGate(GateType.OUTPUT, 540, 300);

        circuit.connect(set.id(), norSet.id(), 0);
        circuit.connect(norReset.id(), norSet.id(), 1);
        circuit.connect(reset.id(), norReset.id(), 0);
        circuit.connect(norSet.id(), norReset.id(), 1);
        circuit.connect(norSet.id(), q.id(), 0);
        circuit.connect(norReset.id(), qNot.id(), 0);
        return circuit.snapshot();
    }

    private static CircuitState nandXor() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 40, 80);
        Gate b = circuit.addGate(GateType.INPUT, 40, 240);
        Gate nand = circuit.addGate(GateType.NAND, 230, 160);
        Gate left = circuit.addGate(GateType.NAND, 420, 80);
        Gate right = circuit.addGate(GateType.NAND, 420, 240);
        Gate outputNand = circuit.addGate(GateType.NAND, 620, 160);
        Gate out = circuit.addGate(GateType.OUTPUT, 800, 160);

        circuit.connect(a.id(), nand.id(), 0);
        circuit.connect(b.id(), nand.id(), 1);
        circuit.connect(a.id(), left.id(), 0);
        circuit.connect(nand.id(), left.id(), 1);
        circuit.connect(b.id(), right.id(), 0);
        circuit.connect(nand.id(), right.id(), 1);
        circuit.connect(left.id(), outputNand.id(), 0);
        circuit.connect(right.id(), outputNand.id(), 1);
        circuit.connect(outputNand.id(), out.id(), 0);
        return circuit.snapshot();
    }

    private record SeedSchematic(String name, CircuitState circuit) {
    }
}
