package com.logictory.schematic;

/**
 * Lightweight metadata for a saved or online schematic. The circuit data is loaded
 * on demand so browsing long lists does not decode every snapshot up front.
 */
public record SchematicSummary(String key, String name, String author, boolean local) {
}
