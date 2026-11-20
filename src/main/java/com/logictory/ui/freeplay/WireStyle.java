package com.logictory.ui.freeplay;

public enum WireStyle {
    SQUARE("Square"),
    BEZIER("Curved");

    private final String label;

    WireStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
