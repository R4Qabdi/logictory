package com.logictory.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitStateTest {

    @Test
    void snapshotAndRestorePreserveGatesWiresAndIds() {
        Circuit circuit = new Circuit();
        Gate input = circuit.addGate(GateType.INPUT, 10, 20);
        input.setOutput(true);
        Gate output = circuit.addGate(GateType.OUTPUT, 200, 40);
        circuit.connect(input.id(), output.id(), 0);

        CircuitState state = circuit.snapshot();

        circuit.addGate(GateType.AND, 300, 300);
        circuit.removeGate(input);

        circuit.restore(state);

        assertEquals(2, circuit.gates().size());
        Gate restoredInput = circuit.gate(input.id());
        assertNotNull(restoredInput);
        assertEquals(GateType.INPUT, restoredInput.type());
        assertEquals(10, restoredInput.x());
        assertEquals(20, restoredInput.y());
        assertTrue(restoredInput.output());

        assertEquals(1, circuit.wires().size());
        assertEquals(input.id(), circuit.wires().get(0).sourceId());
        assertEquals(output.id(), circuit.wires().get(0).targetId());

        assertEquals(state, circuit.snapshot());
    }

    @Test
    void restoreResetsTheNextGateId() {
        Circuit circuit = new Circuit();
        circuit.addGate(GateType.INPUT, 0, 0);
        circuit.addGate(GateType.OUTPUT, 200, 0);

        CircuitState state = circuit.snapshot();
        circuit.clear();
        circuit.restore(state);

        Gate next = circuit.addGate(GateType.NOT, 0, 0);
        assertEquals(3, next.id());
    }

    @Test
    void snapshotIsImmutableAndIndependentOfLaterEdits() {
        Circuit circuit = new Circuit();
        Gate gate = circuit.addGate(GateType.INPUT, 0, 0);
        CircuitState state = circuit.snapshot();

        gate.moveTo(100, 100);
        gate.setOutput(true);
        circuit.addGate(GateType.OUTPUT, 200, 0);

        assertEquals(1, state.gates().size());
        assertEquals(0, state.gates().get(0).x());
        assertFalse(state.gates().get(0).output());
        assertEquals(2, state.nextId());
    }
}
