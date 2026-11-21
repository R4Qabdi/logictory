package com.logictory.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the combinational circuit with a bounded fixed-point loop. Input gates keep
 * their user-set value; every other gate is recomputed from the wires feeding it.
 *
 * <p>Wires and gates are indexed once per simulation, so each pass is linear in the
 * number of gates rather than re-scanning the whole circuit for every input.</p>
 */
public final class CircuitSimulator {
    private CircuitSimulator() {
    }

    /**
     * @return true when the circuit settled, false when it hit the pass limit
     *         (typically an oscillating feedback loop).
     */
    public static boolean simulate(Circuit circuit) {
        List<Gate> gates = circuit.gates();
        Map<Integer, Gate> gatesById = new HashMap<>(Math.max(16, gates.size() * 2));
        for (Gate gate : gates) {
            gatesById.put(gate.id(), gate);
        }

        Map<Integer, Wire[]> incomingByTarget = new HashMap<>();
        for (Wire wire : circuit.wires()) {
            Gate target = gatesById.get(wire.targetId());
            if (target == null) {
                continue;
            }
            int port = wire.targetPort();
            if (port < 0 || port >= target.type().inputCount()) {
                continue;
            }
            Wire[] ports = incomingByTarget.computeIfAbsent(
                    wire.targetId(), id -> new Wire[target.type().inputCount()]);
            ports[port] = wire;
        }

        int maxPasses = gates.size() + 2;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;
            for (Gate gate : gates) {
                if (gate.type() == GateType.INPUT) {
                    continue;
                }
                Wire[] incoming = incomingByTarget.get(gate.id());
                int inputCount = gate.type().inputCount();
                for (int i = 0; i < inputCount; i++) {
                    Wire wire = incoming == null ? null : incoming[i];
                    Gate source = wire == null ? null : gatesById.get(wire.sourceId());
                    gate.setInput(i, source != null && source.output());
                }
                boolean value = evaluate(gate);
                if (gate.output() != value) {
                    gate.setOutput(value);
                    changed = true;
                }
            }
            if (!changed) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluate(Gate gate) {
        return switch (gate.type()) {
            case AND -> gate.input(0) & gate.input(1);
            case OR -> gate.input(0) | gate.input(1);
            case NOT -> !gate.input(0);
            case NAND -> !(gate.input(0) & gate.input(1));
            case NOR -> !(gate.input(0) | gate.input(1));
            case XOR -> gate.input(0) ^ gate.input(1);
            case XNOR -> !(gate.input(0) ^ gate.input(1));
            case OUTPUT -> gate.input(0);
            case INPUT -> false;
        };
    }
}
