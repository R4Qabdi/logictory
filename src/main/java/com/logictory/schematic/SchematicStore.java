package com.logictory.schematic;

import com.logictory.auth.DatabaseConfig;
import com.logictory.auth.UserSession;
import com.logictory.game.CircuitState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Persists saved schematics and reads community schematics.
 *
 * <p>Signed-in users store both saved ({@code is_public = FALSE}) and published
 * online ({@code is_public = TRUE}) schematics in the {@code schematics} table;
 * guests use files under {@code ~/.logictory/schematics} so saving still works
 * without MySQL.</p>
 *
 * <p>Listing methods only fetch metadata. Circuit data is decoded by
 * {@link #loadSaved} / {@link #loadOnline} when the user actually copies or
 * publishes an item, which keeps the browser responsive on large databases.</p>
 */
public final class SchematicStore {
    private static final long ONLINE_CACHE_TTL_MS = 15_000;

    private final Path localDirectory;
    private volatile List<SchematicSummary> onlineCache;
    private volatile long onlineCacheAt;

    public SchematicStore() {
        this(Path.of(System.getProperty("user.home"), ".logictory", "schematics"));
    }

    public SchematicStore(Path localDirectory) {
        this.localDirectory = localDirectory;
    }

    // ---------------------------------------------------------------- listing

    public List<Schematic> listSaved(UserSession session) throws SchematicException {
        List<Schematic> result = new ArrayList<>();
        for (SchematicSummary summary : listSavedSummaries(session)) {
            result.add(new Schematic(summary.key(), summary.name(), summary.author(),
                    loadSaved(session, summary), summary.local()));
        }
        return result;
    }

    public List<SchematicSummary> listSavedSummaries(UserSession session) throws SchematicException {
        List<SchematicSummary> result = new ArrayList<>();
        if (!session.isGuest()) {
            try {
                result.addAll(listSavedSummariesFromDatabase(session));
            } catch (SQLException e) {
                throw new SchematicException("Saved schematics are unavailable (database offline).", e);
            }
        }
        // Local schematics saved while playing as a guest stay visible after signing in.
        result.addAll(listLocalSummaries());
        result.sort(Comparator.comparing(SchematicSummary::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Local only, useful as a graceful fallback when the database is offline. */
    public List<SchematicSummary> listLocalSummaries() throws SchematicException {
        List<SchematicSummary> result = new ArrayList<>();
        if (!Files.isDirectory(localDirectory)) {
            return result;
        }
        try (Stream<Path> files = Files.list(localDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".properties")).toList()) {
                try {
                    Properties properties = readProperties(file);
                    String name = properties.getProperty("name");
                    if (name == null) {
                        continue;
                    }
                    result.add(new SchematicSummary(
                            file.getFileName().toString(),
                            name,
                            properties.getProperty("author", "Guest"),
                            true));
                } catch (IOException e) {
                    // Skip damaged local entries rather than blocking the whole list.
                }
            }
        } catch (IOException e) {
            throw new SchematicException("Could not read saved schematics.", e);
        }
        result.sort(Comparator.comparing(SchematicSummary::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public List<Schematic> listOnline() throws SchematicException {
        List<Schematic> result = new ArrayList<>();
        for (SchematicSummary summary : listOnlineSummaries(true)) {
            result.add(new Schematic(summary.key(), summary.name(), summary.author(),
                    loadOnline(summary), false));
        }
        return result;
    }

    public List<SchematicSummary> listOnlineSummaries() throws SchematicException {
        return listOnlineSummaries(false);
    }

    /**
     * @param force bypass the short-lived cache (used by the Refresh buttons)
     */
    public List<SchematicSummary> listOnlineSummaries(boolean force) throws SchematicException {
        long now = System.currentTimeMillis();
        List<SchematicSummary> cached = onlineCache;
        if (!force && cached != null && now - onlineCacheAt < ONLINE_CACHE_TTL_MS) {
            return cached;
        }

        String sql = """
                SELECT s.id, s.name, u.username
                FROM schematics s
                JOIN users u ON u.id = s.user_id
                WHERE s.is_public = TRUE
                ORDER BY s.name
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<SchematicSummary> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new SchematicSummary(
                        String.valueOf(rs.getLong("id")),
                        rs.getString("name"),
                        rs.getString("username"),
                        false));
            }
            List<SchematicSummary> snapshot = List.copyOf(result);
            onlineCache = snapshot;
            onlineCacheAt = now;
            return snapshot;
        } catch (SQLException e) {
            throw new SchematicException("Online schematics are unavailable (database offline).", e);
        }
    }

    // ---------------------------------------------------------------- loading

    public CircuitState loadSaved(UserSession session, SchematicSummary summary) throws SchematicException {
        if (summary.local()) {
            return loadLocal(summary);
        }
        try {
            return loadSavedFromDatabase(session, summary);
        } catch (SQLException e) {
            throw new SchematicException("Could not load the saved schematic.", e);
        }
    }

    public CircuitState loadOnline(SchematicSummary summary) throws SchematicException {
        String sql = "SELECT data_json FROM schematics WHERE id = ? AND is_public = TRUE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(summary.key()));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SchematicException("That online schematic no longer exists.");
                }
                try {
                    return SchematicCodec.decode(rs.getString("data_json"));
                } catch (IllegalArgumentException e) {
                    throw new SchematicException("The online schematic contains invalid data.", e);
                }
            }
        } catch (SQLException | NumberFormatException e) {
            throw new SchematicException("Could not load the online schematic.", e);
        }
    }

    // ---------------------------------------------------------------- saving

    public void saveSaved(UserSession session, String name, CircuitState state) throws SchematicException {
        String cleanName = validateName(name);
        if (session.isGuest()) {
            try {
                saveLocal(session.getUsername(), cleanName, state);
            } catch (IOException e) {
                throw new SchematicException("Could not save the schematic locally.", e);
            }
        } else {
            try {
                saveSavedToDatabase(session, cleanName, state);
            } catch (SQLException e) {
                throw new SchematicException("Could not save the schematic to the database.", e);
            }
        }
    }

    public void deleteSaved(UserSession session, Schematic schematic) throws SchematicException {
        deleteSaved(session, new SchematicSummary(
                schematic.key(), schematic.name(), schematic.author(), schematic.local()));
    }

    public void deleteSaved(UserSession session, SchematicSummary summary) throws SchematicException {
        if (summary.local()) {
            deleteLocal(summary);
        } else {
            deleteSavedFromDatabase(session, summary);
        }
    }

    public void publish(UserSession session, String name, CircuitState state) throws SchematicException {
        if (session.isGuest()) {
            throw new SchematicException("Sign in to publish schematics online.");
        }
        String cleanName = validateName(name);
        String delete = "DELETE FROM schematics WHERE user_id = ? AND name = ? AND is_public = TRUE";
        String insert = "INSERT INTO schematics (user_id, name, data_json, is_public) VALUES (?, ?, ?, TRUE)";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(delete)) {
                    ps.setInt(1, session.getUserId());
                    ps.setString(2, cleanName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setInt(1, session.getUserId());
                    ps.setString(2, cleanName);
                    ps.setString(3, SchematicCodec.encode(state));
                    ps.executeUpdate();
                }
                conn.commit();
                onlineCache = null;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new SchematicException("Could not publish the schematic.", e);
        }
    }

    // ---------------------------------------------------------------- local storage

    private static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return properties;
    }

    private CircuitState loadLocal(SchematicSummary summary) throws SchematicException {
        Path file = localFile(summary.key());
        try {
            String circuit = readProperties(file).getProperty("circuit");
            if (circuit == null) {
                throw new SchematicException("The saved schematic is missing its circuit data.");
            }
            return SchematicCodec.decode(circuit);
        } catch (IOException e) {
            throw new SchematicException("Could not read the saved schematic.", e);
        } catch (IllegalArgumentException e) {
            throw new SchematicException("The saved schematic contains invalid data.", e);
        }
    }

    private void saveLocal(String author, String name, CircuitState state) throws IOException {
        Files.createDirectories(localDirectory);
        Path file = localDirectory.resolve(localFileName(name));
        Properties properties = new Properties();
        properties.setProperty("name", name);
        properties.setProperty("author", author == null ? "Guest" : author);
        properties.setProperty("circuit", SchematicCodec.encode(state));
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "Logictory schematic");
        }
    }

    private void deleteLocal(SchematicSummary summary) throws SchematicException {
        Path file = localFile(summary.key());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new SchematicException("Could not delete the schematic.", e);
        }
    }

    private Path localFile(String key) throws SchematicException {
        Path root = localDirectory.toAbsolutePath().normalize();
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new SchematicException("Invalid local schematic.");
        }
        return file;
    }

    private static String localFileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 48) {
            safe = safe.substring(0, 48);
        }
        if (safe.isBlank()) {
            safe = "schematic";
        }
        return safe + "-" + Integer.toHexString(name.hashCode()) + ".properties";
    }

    // ---------------------------------------------------------------- database storage

    private List<SchematicSummary> listSavedSummariesFromDatabase(UserSession session) throws SQLException {
        String sql = "SELECT id, name FROM schematics WHERE user_id = ? AND is_public = FALSE ORDER BY name";
        List<SchematicSummary> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, session.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SchematicSummary(
                            String.valueOf(rs.getLong("id")),
                            rs.getString("name"),
                            session.getUsername(),
                            false));
                }
            }
        }
        return result;
    }

    private CircuitState loadSavedFromDatabase(UserSession session, SchematicSummary summary) throws SQLException {
        String sql = "SELECT data_json FROM schematics WHERE id = ? AND user_id = ? AND is_public = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(summary.key()));
            ps.setInt(2, session.getUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Schematic not found.");
                }
                try {
                    return SchematicCodec.decode(rs.getString("data_json"));
                } catch (IllegalArgumentException e) {
                    throw new SQLException("The saved schematic contains invalid data.", e);
                }
            }
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid schematic key.", e);
        }
    }

    private void saveSavedToDatabase(UserSession session, String name, CircuitState state) throws SQLException {
        String delete = "DELETE FROM schematics WHERE user_id = ? AND name = ? AND is_public = FALSE";
        String insert = "INSERT INTO schematics (user_id, name, data_json, is_public) VALUES (?, ?, ?, FALSE)";
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(delete)) {
                    ps.setInt(1, session.getUserId());
                    ps.setString(2, name);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setInt(1, session.getUserId());
                    ps.setString(2, name);
                    ps.setString(3, SchematicCodec.encode(state));
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            }
        }
    }

    private void deleteSavedFromDatabase(UserSession session, SchematicSummary summary) throws SchematicException {
        String sql = "DELETE FROM schematics WHERE id = ? AND user_id = ? AND is_public = FALSE";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Long.parseLong(summary.key()));
            ps.setInt(2, session.getUserId());
            ps.executeUpdate();
        } catch (SQLException | NumberFormatException e) {
            throw new SchematicException("Could not delete the schematic.", e);
        }
    }

    private static String validateName(String name) throws SchematicException {
        if (name == null) {
            throw new SchematicException("Schematic name is required.");
        }
        String clean = name.trim();
        if (clean.isEmpty()) {
            throw new SchematicException("Schematic name is required.");
        }
        if (clean.length() > 100) {
            throw new SchematicException("Schematic name must be 100 characters or fewer.");
        }
        return clean;
    }
}
