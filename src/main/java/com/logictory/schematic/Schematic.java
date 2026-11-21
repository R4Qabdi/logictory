package com.logictory.schematic;

import com.logictory.game.CircuitState;

/**
 * A named circuit snapshot. The key identifies the item in its source
 * (a database row id or a local file name).
 */
public record Schematic(String key, String name, String author, CircuitState circuit, boolean local) {
}
