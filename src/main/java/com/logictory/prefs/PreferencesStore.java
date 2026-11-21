package com.logictory.prefs;

import com.logictory.ui.freeplay.WireStyle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads and saves preferences in {@code ~/.logictory/preferences.properties}.
 * Missing or unreadable files fall back to the defaults so startup never fails.
 */
public final class PreferencesStore {
    private static final String WIRE_STYLE = "wireStyle";
    private static final String SHOW_GRID = "showGrid";
    private static final String SNAP_TO_GRID = "snapToGrid";

    private final Path file;

    public PreferencesStore() {
        this(Path.of(System.getProperty("user.home"), ".logictory", "preferences.properties"));
    }

    public PreferencesStore(Path file) {
        this.file = file;
    }

    public AppPreferences load() {
        if (!Files.isRegularFile(file)) {
            return AppPreferences.defaults();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            return AppPreferences.defaults();
        }

        WireStyle style;
        try {
            style = WireStyle.valueOf(properties.getProperty(WIRE_STYLE, WireStyle.SQUARE.name()));
        } catch (IllegalArgumentException e) {
            style = WireStyle.SQUARE;
        }
        return new AppPreferences(
                style,
                booleanValue(properties, SHOW_GRID, true),
                booleanValue(properties, SNAP_TO_GRID, true));
    }

    private static boolean booleanValue(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    public void save(AppPreferences preferences) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Properties properties = new Properties();
        properties.setProperty(WIRE_STYLE, preferences.wireStyle().name());
        properties.setProperty(SHOW_GRID, Boolean.toString(preferences.showGrid()));
        properties.setProperty(SNAP_TO_GRID, Boolean.toString(preferences.snapToGrid()));
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "Logictory preferences");
        }
    }
}
