package com.logictory.prefs;

import com.logictory.ui.freeplay.WireStyle;

/** User-adjustable freeplay preferences that are persisted between sessions. */
public record AppPreferences(WireStyle wireStyle, boolean showGrid, boolean snapToGrid) {
    public AppPreferences {
        wireStyle = wireStyle == null ? WireStyle.SQUARE : wireStyle;
    }

    public static AppPreferences defaults() {
        return new AppPreferences(WireStyle.SQUARE, true, true);
    }
}
