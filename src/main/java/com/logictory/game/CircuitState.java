package com.logictory.game;

import java.util.List;

/**
 * Immutable snapshot of a circuit, used for undo/redo history and the clipboard.
 * Wires refer to gate ids, so gate ids and the next id counter are preserved.
 */
public record CircuitState(List<GateState> gates, List<Wire> wires, int nextId) {
    public CircuitState {
        gates = List.copyOf(gates);
        wires = List.copyOf(wires);
    }

    /** The parts of a gate that are not derived from the simulation. */
    public record GateState(int id, GateType type, int x, int y, boolean output) {
    }
}
