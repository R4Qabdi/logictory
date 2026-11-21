package com.logictory.prefs;

import com.logictory.ui.freeplay.WireStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PreferencesStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileLoadsDefaults() {
        PreferencesStore store = new PreferencesStore(tempDir.resolve("preferences.properties"));

        assertEquals(AppPreferences.defaults(), store.load());
        assertFalse(Files.exists(tempDir.resolve("preferences.properties")));
    }

    @Test
    void savedPreferencesRoundTrip() throws IOException {
        Path file = tempDir.resolve("nested/preferences.properties");
        PreferencesStore store = new PreferencesStore(file);
        AppPreferences preferences = new AppPreferences(WireStyle.BEZIER, false, false);

        store.save(preferences);

        assertEquals(preferences, store.load());
    }

    @Test
    void invalidValuesFallBackToDefaults() throws IOException {
        Path file = tempDir.resolve("preferences.properties");
        Files.writeString(file, "wireStyle=UNKNOWN\nshowGrid=not-a-boolean\n");

        AppPreferences preferences = new PreferencesStore(file).load();

        assertEquals(WireStyle.SQUARE, preferences.wireStyle());
        assertEquals(true, preferences.showGrid());
        assertEquals(true, preferences.snapToGrid());
    }
}
