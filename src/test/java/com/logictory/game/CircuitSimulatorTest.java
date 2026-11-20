package com.logictory.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitSimulatorTest {

    /** Two input gates feeding one gate under test. */
    private static final class Bench {
        final Circuit circuit = new Circuit();
        final Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        final Gate b = circuit.addGate(GateType.INPUT, 0, 100);
        final Gate gate;

        Bench(GateType type) {
            gate = circuit.addGate(type, 200, 50);
            circuit.connect(a.id(), gate.id(), 0);
            if (type.inputCount() >= 2) {
                circuit.connect(b.id(), gate.id(), 1);
            }
        }

        void apply(boolean av, boolean bv) {
            a.setOutput(av);
            b.setOutput(bv);
            CircuitSimulator.simulate(circuit);
        }
    }

    @Test
    void andMatchesTruthTable() {
        Bench bench = new Bench(GateType.AND);
        bench.apply(false, false);
        assertFalse(bench.gate.output());
        bench.apply(false, true);
        assertFalse(bench.gate.output());
        bench.apply(true, false);
        assertFalse(bench.gate.output());
        bench.apply(true, true);
        assertTrue(bench.gate.output());
    }

    @Test
    void orMatchesTruthTable() {
        Bench bench = new Bench(GateType.OR);
        bench.apply(false, false);
        assertFalse(bench.gate.output());
        bench.apply(false, true);
        assertTrue(bench.gate.output());
        bench.apply(true, false);
        assertTrue(bench.gate.output());
        bench.apply(true, true);
        assertTrue(bench.gate.output());
    }

    @Test
    void xorMatchesTruthTable() {
        Bench bench = new Bench(GateType.XOR);
        bench.apply(false, false);
        assertFalse(bench.gate.output());
        bench.apply(false, true);
        assertTrue(bench.gate.output());
        bench.apply(true, false);
        assertTrue(bench.gate.output());
        bench.apply(true, true);
        assertFalse(bench.gate.output());
    }

    @Test
    void nandMatchesTruthTable() {
        Bench bench = new Bench(GateType.NAND);
        bench.apply(false, false);
        assertTrue(bench.gate.output());
        bench.apply(true, true);
        assertFalse(bench.gate.output());
    }

    @Test
    void norMatchesTruthTable() {
        Bench bench = new Bench(GateType.NOR);
        bench.apply(false, false);
        assertTrue(bench.gate.output());
        bench.apply(true, false);
        assertFalse(bench.gate.output());
    }

    @Test
    void xnorMatchesTruthTable() {
        Bench bench = new Bench(GateType.XNOR);
        bench.apply(false, false);
        assertTrue(bench.gate.output());
        bench.apply(false, true);
        assertFalse(bench.gate.output());
        bench.apply(true, true);
        assertTrue(bench.gate.output());
    }

    @Test
    void notInvertsItsInput() {
        Bench bench = new Bench(GateType.NOT);
        bench.apply(false, false);
        assertTrue(bench.gate.output());
        bench.apply(true, false);
        assertFalse(bench.gate.output());
    }

    @Test
    void unconnectedInputDefaultsToFalse() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        Gate and = circuit.addGate(GateType.AND, 200, 0);
        circuit.connect(a.id(), and.id(), 0);
        a.setOutput(true);

        CircuitSimulator.simulate(circuit);

        assertFalse(and.output());
    }

    @Test
    void outputGateShowsIncomingValue() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        Gate out = circuit.addGate(GateType.OUTPUT, 200, 0);
        circuit.connect(a.id(), out.id(), 0);
        a.setOutput(true);

        CircuitSimulator.simulate(circuit);

        assertTrue(out.output());
    }

    @Test
    void fanOutDeliversValueToEveryTarget() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        Gate first = circuit.addGate(GateType.OUTPUT, 200, 0);
        Gate second = circuit.addGate(GateType.OUTPUT, 200, 100);
        circuit.connect(a.id(), first.id(), 0);
        circuit.connect(a.id(), second.id(), 0);
        a.setOutput(true);

        CircuitSimulator.simulate(circuit);

        assertTrue(first.output());
        assertTrue(second.output());
    }

    @Test
    void connectingAgainReplacesExistingWire() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        Gate b = circuit.addGate(GateType.INPUT, 0, 100);
        Gate out = circuit.addGate(GateType.OUTPUT, 200, 0);

        assertTrue(circuit.connect(a.id(), out.id(), 0));
        assertTrue(circuit.connect(b.id(), out.id(), 0));
        assertEquals(1, circuit.wires().size());

        a.setOutput(true);
        b.setOutput(false);
        CircuitSimulator.simulate(circuit);
        assertFalse(out.output());
    }

    @Test
    void removingGateRemovesItsWires() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        Gate out = circuit.addGate(GateType.OUTPUT, 200, 0);
        circuit.connect(a.id(), out.id(), 0);

        circuit.removeGate(a);

        assertTrue(circuit.wires().isEmpty());
        assertNotNull(circuit.gate(out.id()));
    }

    @Test
    void selfConnectionIsRejected() {
        Circuit circuit = new Circuit();
        Gate not = circuit.addGate(GateType.NOT, 0, 0);

        assertFalse(circuit.connect(not.id(), not.id(), 0));
        assertTrue(circuit.wires().isEmpty());
    }

    @Test
    void oscillatingFeedbackTerminatesAndReportsUnstable() {
        // Three inverters in a ring never settle; the simulator must stop and report it.
        Circuit circuit = new Circuit();
        Gate first = circuit.addGate(GateType.NOT, 0, 0);
        Gate second = circuit.addGate(GateType.NOT, 200, 0);
        Gate third = circuit.addGate(GateType.NOT, 400, 0);
        circuit.connect(first.id(), second.id(), 0);
        circuit.connect(second.id(), third.id(), 0);
        circuit.connect(third.id(), first.id(), 0);

        assertFalse(CircuitSimulator.simulate(circuit));
    }

    @Test
    void invalidPortIsRejected() {
        Circuit circuit = new Circuit();
        Gate a = circuit.addGate(GateType.INPUT, 0, 0);
        Gate not = circuit.addGate(GateType.NOT, 200, 0);

        assertFalse(circuit.connect(a.id(), not.id(), 1));
        assertFalse(circuit.connect(a.id(), not.id(), -1));
    }
}
