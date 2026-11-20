package com.logictory.ui.freeplay;

import com.logictory.game.Circuit;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import com.logictory.game.Wire;
import org.junit.jupiter.api.Test;

import java.awt.geom.Path2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireRouterTest {

    @Test
    void fanOutWiresSharingAPathGetSeparateLanes() {
        Circuit circuit = new Circuit();
        Gate input = circuit.addGate(GateType.INPUT, 0, 0);
        Gate straight = circuit.addGate(GateType.OUTPUT, 400, 0);
        Gate lower = circuit.addGate(GateType.OUTPUT, 400, 100);
        circuit.connect(input.id(), straight.id(), 0);
        circuit.connect(input.id(), lower.id(), 0);

        WireRouter router = new WireRouter(circuit);
        Wire first = circuit.wires().get(0);
        Wire second = circuit.wires().get(1);

        assertEquals(0, router.laneOf(first));
        assertNotEquals(router.laneOf(first), router.laneOf(second));
    }

    @Test
    void crossingWiresHopOverEachOther() {
        Circuit circuit = new Circuit();
        Gate in1 = circuit.addGate(GateType.INPUT, 0, 0);
        Gate in2 = circuit.addGate(GateType.INPUT, 0, 200);
        Gate out1 = circuit.addGate(GateType.OUTPUT, 400, 100);
        Gate out2 = circuit.addGate(GateType.OUTPUT, 500, 0);
        circuit.connect(in1.id(), out1.id(), 0);
        circuit.connect(in2.id(), out2.id(), 0);

        WireRouter router = new WireRouter(circuit);
        Wire first = circuit.wires().get(0);
        Wire second = circuit.wires().get(1);

        assertEquals(0, router.hopCount(first), "the earlier wire stays flat");
        assertTrue(router.hopCount(second) > 0, "the later wire bridges the crossing");
    }

    @Test
    void nonCrossingWiresHaveNoHops() {
        Circuit circuit = new Circuit();
        Gate in1 = circuit.addGate(GateType.INPUT, 0, 0);
        Gate in2 = circuit.addGate(GateType.INPUT, 0, 200);
        Gate out1 = circuit.addGate(GateType.OUTPUT, 400, 0);
        Gate out2 = circuit.addGate(GateType.OUTPUT, 400, 200);
        circuit.connect(in1.id(), out1.id(), 0);
        circuit.connect(in2.id(), out2.id(), 0);

        WireRouter router = new WireRouter(circuit);

        assertEquals(0, router.hopCount(circuit.wires().get(0)));
        assertEquals(0, router.hopCount(circuit.wires().get(1)));
    }

    @Test
    void bothStylesProducePaths() {
        Circuit circuit = new Circuit();
        Gate input = circuit.addGate(GateType.INPUT, 0, 0);
        Gate output = circuit.addGate(GateType.OUTPUT, 400, 100);
        circuit.connect(input.id(), output.id(), 0);

        Wire wire = circuit.wires().get(0);
        WireRouter router = new WireRouter(circuit);

        Path2D square = router.pathFor(wire);
        assertNotNull(square);
        assertEquals(WireStyle.SQUARE, router.style());

        router.setStyle(WireStyle.BEZIER);
        Path2D curved = router.pathFor(wire);
        assertNotNull(curved);
        assertEquals(WireStyle.BEZIER, router.style());
    }

    @Test
    void previewPathUsesSelectedStyle() {
        Circuit circuit = new Circuit();
        WireRouter router = new WireRouter(circuit);

        Path2D preview = router.previewPath(new java.awt.Point(0, 0),
                new java.awt.geom.Point2D.Double(200, 100));
        assertNotNull(preview);
    }
}
