package com.logictory.schematic;

import com.logictory.game.Circuit;
import com.logictory.game.CircuitState;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicCodecTest {

    @Test
    void roundTripPreservesGatesWiresOutputsAndNextId() {
        Circuit circuit = new Circuit();
        Gate input = circuit.addGate(GateType.INPUT, 10, 20);
        input.setOutput(true);
        Gate output = circuit.addGate(GateType.OUTPUT, 200, 40);
        circuit.connect(input.id(), output.id(), 0);

        CircuitState state = circuit.snapshot();
        CircuitState decoded = SchematicCodec.decode(SchematicCodec.encode(state));

        assertEquals(state, decoded);
    }

    @Test
    void encodedTextStartsWithTheVersionHeader() {
        Circuit circuit = new Circuit();
        circuit.addGate(GateType.INPUT, 0, 0);

        String encoded = SchematicCodec.encode(circuit.snapshot());

        assertTrue(encoded.startsWith("LOGICTORY-SCHEMATIC-1\n"));
    }

    @Test
    void rejectsUnknownFormat() {
        assertThrows(IllegalArgumentException.class, () -> SchematicCodec.decode("not a schematic"));
        assertThrows(IllegalArgumentException.class, () -> SchematicCodec.decode(null));
    }

    @Test
    void nextIdIsAtLeastOnePastTheHighestGateId() {
        String data = """
                LOGICTORY-SCHEMATIC-1
                G|5|INPUT|0|0|0
                N|1
                """;

        assertEquals(6, SchematicCodec.decode(data).nextId());
    }

    @Test
    void rejectsWireThatReferencesMissingGate() {
        String data = """
                LOGICTORY-SCHEMATIC-1
                G|1|INPUT|0|0|0
                W|1|99|0
                N|2
                """;

        assertThrows(IllegalArgumentException.class, () -> SchematicCodec.decode(data));
    }
}
