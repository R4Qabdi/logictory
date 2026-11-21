package com.logictory.schematic;

import com.logictory.auth.UserSession;
import com.logictory.game.Circuit;
import com.logictory.game.CircuitState;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void guestSchematicsAreSavedListedLoadedAndDeletedLocally() throws Exception {
        SchematicStore store = new SchematicStore(tempDir.resolve("schematics"));
        UserSession guest = UserSession.guest();
        CircuitState state = sampleState();

        store.saveSaved(guest, "Half Adder", state);
        List<Schematic> saved = store.listSaved(guest);

        assertEquals(1, saved.size());
        assertEquals("Half Adder", saved.get(0).name());
        assertEquals("Guest", saved.get(0).author());
        assertEquals(state, saved.get(0).circuit());
        assertTrue(saved.get(0).local());

        store.deleteSaved(guest, saved.get(0));

        assertTrue(store.listSaved(guest).isEmpty());
    }

    @Test
    void savingTheSameNameReplacesTheLocalSchematic() throws Exception {
        SchematicStore store = new SchematicStore(tempDir.resolve("schematics"));
        UserSession guest = UserSession.guest();

        store.saveSaved(guest, "Adder", sampleState());
        store.saveSaved(guest, "Adder", sampleState());

        assertEquals(1, store.listSaved(guest).size());
    }

    @Test
    void savedSchematicsAreSortedByName() throws Exception {
        SchematicStore store = new SchematicStore(tempDir.resolve("schematics"));
        UserSession guest = UserSession.guest();

        store.saveSaved(guest, "XOR", sampleState());
        store.saveSaved(guest, "Adder", sampleState());

        List<Schematic> saved = store.listSaved(guest);

        assertEquals("Adder", saved.get(0).name());
        assertEquals("XOR", saved.get(1).name());
    }

    @Test
    void blankNamesAreRejected() {
        SchematicStore store = new SchematicStore(tempDir.resolve("schematics"));

        assertThrows(SchematicException.class,
                () -> store.saveSaved(UserSession.guest(), "   ", sampleState()));
    }

    @Test
    void summariesLoadCircuitDataOnDemand() throws Exception {
        SchematicStore store = new SchematicStore(tempDir.resolve("schematics"));
        UserSession guest = UserSession.guest();
        CircuitState state = sampleState();
        store.saveSaved(guest, "Adder", state);

        List<SchematicSummary> summaries = store.listSavedSummaries(guest);

        assertEquals(1, summaries.size());
        SchematicSummary summary = summaries.get(0);
        assertEquals("Adder", summary.name());
        assertTrue(summary.local());
        assertEquals(state, store.loadSaved(guest, summary));

        store.deleteSaved(guest, summary);
        assertTrue(store.listSavedSummaries(guest).isEmpty());
    }

    private static CircuitState sampleState() {
        Circuit circuit = new Circuit();
        Gate input = circuit.addGate(GateType.INPUT, 0, 0);
        input.setOutput(true);
        Gate output = circuit.addGate(GateType.OUTPUT, 200, 0);
        circuit.connect(input.id(), output.id(), 0);
        return circuit.snapshot();
    }
}
