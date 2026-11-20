package com.logictory.game;

public enum GateType {
    INPUT(0, 1, "IN"),
    OUTPUT(1, 0, "OUT"),
    NOT(1, 1, "NOT"),
    AND(2, 1, "AND"),
    OR(2, 1, "OR"),
    NAND(2, 1, "NAND"),
    NOR(2, 1, "NOR"),
    XOR(2, 1, "XOR"),
    XNOR(2, 1, "XNOR");

    private final int inputCount;
    private final int outputCount;
    private final String label;

    GateType(int inputCount, int outputCount, String label) {
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.label = label;
    }

    public int inputCount() {
        return inputCount;
    }

    public int outputCount() {
        return outputCount;
    }

    /** Short label painted inside the gate body. */
    public String label() {
        return label;
    }

    /** Readable name used in the palette and status bar. */
    public String displayName() {
        return switch (this) {
            case INPUT -> "Input";
            case OUTPUT -> "Output";
            default -> name();
        };
    }

    public boolean invertedOutput() {
        return this == NOT || this == NAND || this == NOR || this == XNOR;
    }
}
