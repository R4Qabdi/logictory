package com.logictory.game;

import java.awt.Point;

public final class Gate {
    public static final int WIDTH = 78;
    public static final int HEIGHT = 56;

    private final int id;
    private final GateType type;
    private int x;
    private int y;
    private final boolean[] inputs;
    private boolean output;

    public Gate(int id, GateType type, int x, int y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.inputs = new boolean[type.inputCount()];
    }

    public int id() {
        return id;
    }

    public GateType type() {
        return type;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public boolean output() {
        return output;
    }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setOutput(boolean output) {
        this.output = output;
    }

    public boolean input(int index) {
        return inputs[index];
    }

    public boolean[] inputs() {
        return inputs.clone();
    }

    public void setInputs(boolean[] values) {
        System.arraycopy(values, 0, inputs, 0, Math.min(values.length, inputs.length));
    }

    public Point inputPort(int index) {
        int count = inputs.length;
        return new Point(x, y + HEIGHT * (index + 1) / (count + 1));
    }

    public Point outputPort() {
        return new Point(x + WIDTH, y + HEIGHT / 2);
    }
}
