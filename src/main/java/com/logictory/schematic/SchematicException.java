package com.logictory.schematic;

/** Raised when a schematic cannot be loaded, saved, published or deleted. */
public class SchematicException extends Exception {
    public SchematicException(String message) {
        super(message);
    }

    public SchematicException(String message, Throwable cause) {
        super(message, cause);
    }
}
