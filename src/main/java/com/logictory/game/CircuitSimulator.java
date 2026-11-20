package com.logictory.game;

/**
 * Evaluates the combinational circuit with a bounded fixed-point loop. Input gates keep
 * their user-set value; every other gate is recomputed from the wires feeding it.
 */
public final class CircuitSimulator {
    private CircuitSimulator() {
    }

    /**
     * @return true when the circuit settled, false when it hit the pass limit
     *         (typically an oscillating feedback loop).
     */
    public static boolean simulate(Circuit circuit) {
        int maxPasses = circuit.gates().size() + 2;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;
            for (Gate gate : circuit.gates()) {
                if (gate.type() == GateType.INPUT) {
                    continue;
                }
                boolean[] inputs = new boolean[gate.type().inputCount()];
                for (int i = 0; i < inputs.length; i++) {
                    Wire wire = circuit.wireInto(gate.id(), i);
                    if (wire != null) {
                        Gate source = circuit.gate(wire.sourceId());
                        inputs[i] = source != null && source.output();
                    }
                }
                gate.setInputs(inputs);
                boolean value = evaluate(gate.type(), inputs);
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

    private static boolean evaluate(GateType type, boolean[] in) {
        return switch (type) {
            case AND -> in[0] & in[1];
            case OR -> in[0] | in[1];
            case NOT -> !in[0];
            case NAND -> !(in[0] & in[1]);
            case NOR -> !(in[0] | in[1]);
            case XOR -> in[0] ^ in[1];
            case XNOR -> !(in[0] ^ in[1]);
            case OUTPUT -> in[0];
            case INPUT -> false;
        };
    }
}
