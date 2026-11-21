package com.logictory.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Circuit {
    private final List<Gate> gates = new ArrayList<>();
    private final Map<Integer, Gate> gatesById = new HashMap<>();
    private final List<Wire> wires = new ArrayList<>();
    private int nextId = 1;

    public Gate addGate(GateType type, int x, int y) {
        Gate gate = new Gate(nextId++, type, x, y);
        gates.add(gate);
        gatesById.put(gate.id(), gate);
        return gate;
    }

    public List<Gate> gates() {
        return Collections.unmodifiableList(gates);
    }

    public List<Wire> wires() {
        return Collections.unmodifiableList(wires);
    }

    public Gate gate(int id) {
        return gatesById.get(id);
    }

    public void bringToFront(Gate gate) {
        if (gates.remove(gate)) {
            gates.add(gate);
        }
    }

    public void removeGate(Gate gate) {
        gates.remove(gate);
        gatesById.remove(gate.id());
        wires.removeIf(w -> w.sourceId() == gate.id() || w.targetId() == gate.id());
    }

    /**
     * Connects an output to an input. Each input accepts a single wire; connecting again
     * replaces the previous wire. Returns false when the connection is not allowed.
     */
    public boolean connect(int sourceId, int targetId, int targetPort) {
        Gate source = gate(sourceId);
        Gate target = gate(targetId);
        if (source == null || target == null || sourceId == targetId) {
            return false;
        }
        if (source.type().outputCount() == 0) {
            return false;
        }
        if (targetPort < 0 || targetPort >= target.type().inputCount()) {
            return false;
        }
        wires.removeIf(w -> w.targetId() == targetId && w.targetPort() == targetPort);
        wires.add(new Wire(sourceId, targetId, targetPort));
        return true;
    }

    public void disconnect(Wire wire) {
        wires.remove(wire);
    }

    public Wire wireInto(int targetId, int targetPort) {
        for (Wire wire : wires) {
            if (wire.targetId() == targetId && wire.targetPort() == targetPort) {
                return wire;
            }
        }
        return null;
    }

    public void clear() {
        gates.clear();
        gatesById.clear();
        wires.clear();
        nextId = 1;
    }

    /** Captures the complete editable state of this circuit. */
    public CircuitState snapshot() {
        List<CircuitState.GateState> gateStates = new ArrayList<>(gates.size());
        for (Gate gate : gates) {
            gateStates.add(new CircuitState.GateState(
                    gate.id(), gate.type(), gate.x(), gate.y(), gate.output()));
        }
        return new CircuitState(gateStates, wires, nextId);
    }

    /** Replaces the circuit with a previously captured snapshot. */
    public void restore(CircuitState state) {
        gates.clear();
        gatesById.clear();
        wires.clear();
        for (CircuitState.GateState gateState : state.gates()) {
            Gate gate = new Gate(gateState.id(), gateState.type(), gateState.x(), gateState.y());
            gate.setOutput(gateState.output());
            gates.add(gate);
            gatesById.put(gate.id(), gate);
        }
        wires.addAll(state.wires());
        nextId = state.nextId();
    }
}
