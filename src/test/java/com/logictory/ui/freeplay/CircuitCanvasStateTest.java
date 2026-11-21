package com.logictory.ui.freeplay;

import com.logictory.game.Circuit;
import com.logictory.game.CircuitState;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitCanvasStateTest {

    @Test
    void selectedStateContainsSelectedGatesAndTheirInternalWires() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Circuit circuit = new Circuit();
            Gate input = circuit.addGate(GateType.INPUT, 0, 0);
            Gate output = circuit.addGate(GateType.OUTPUT, 200, 0);
            circuit.connect(input.id(), output.id(), 0);
            CircuitCanvas canvas = new CircuitCanvas(circuit);

            canvas.selectAll();
            CircuitState state = canvas.selectedState();

            assertEquals(2, state.gates().size());
            assertEquals(1, state.wires().size());
            assertEquals(3, state.nextId());
        });
    }

    @Test
    void loadStateReplacesTheBoardAndCanBeUndone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Circuit circuit = new Circuit();
            Gate input = circuit.addGate(GateType.INPUT, 0, 0);
            input.setOutput(true);
            Gate output = circuit.addGate(GateType.OUTPUT, 200, 0);
            circuit.connect(input.id(), output.id(), 0);
            CircuitCanvas canvas = new CircuitCanvas(circuit);
            canvas.selectAll();
            CircuitState saved = canvas.selectedState();

            canvas.clearBoard();
            assertTrue(circuit.gates().isEmpty());

            canvas.loadState(saved);
            assertEquals(2, circuit.gates().size());
            assertEquals(1, circuit.wires().size());
            assertTrue(canvas.canUndo());

            canvas.undo();
            assertTrue(circuit.gates().isEmpty());
        });
    }

    @Test
    void schematicStateUsesWholeBoardWhenNothingIsSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Circuit circuit = new Circuit();
            Gate input = circuit.addGate(GateType.INPUT, 0, 0);
            Gate output = circuit.addGate(GateType.OUTPUT, 200, 0);
            circuit.connect(input.id(), output.id(), 0);
            CircuitCanvas canvas = new CircuitCanvas(circuit);

            assertEquals(circuit.snapshot(), canvas.schematicState());
        });
    }

    @Test
    void copiedSchematicCanBePastedOntoTheBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Circuit source = new Circuit();
            Gate input = source.addGate(GateType.INPUT, 0, 0);
            Gate output = source.addGate(GateType.OUTPUT, 200, 0);
            source.connect(input.id(), output.id(), 0);
            Circuit target = new Circuit();
            CircuitCanvas canvas = new CircuitCanvas(target);

            canvas.copyState(source.snapshot());
            canvas.pasteClipboard();

            assertEquals(2, target.gates().size());
            assertEquals(1, target.wires().size());
            assertTrue(canvas.hasClipboard());
        });
    }
}
