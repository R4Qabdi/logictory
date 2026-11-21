package com.logictory.schematic;

import com.logictory.game.CircuitState;
import com.logictory.game.GateType;
import com.logictory.game.Wire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compact, dependency-free text format for circuit snapshots. It is stored in the
 * database TEXT columns and in local schematic files.
 */
public final class SchematicCodec {
    private static final String HEADER = "LOGICTORY-SCHEMATIC-1";

    private SchematicCodec() {
    }

    public static String encode(CircuitState state) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (CircuitState.GateState gate : state.gates()) {
            out.append("G|").append(gate.id()).append('|')
                    .append(gate.type().name()).append('|')
                    .append(gate.x()).append('|')
                    .append(gate.y()).append('|')
                    .append(gate.output() ? '1' : '0').append('\n');
        }
        for (Wire wire : state.wires()) {
            out.append("W|").append(wire.sourceId()).append('|')
                    .append(wire.targetId()).append('|')
                    .append(wire.targetPort()).append('\n');
        }
        out.append("N|").append(state.nextId()).append('\n');
        return out.toString();
    }

    public static CircuitState decode(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Missing schematic data.");
        }
        String[] lines = data.split("\\R");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("Unrecognized schematic format.");
        }

        List<CircuitState.GateState> gates = new ArrayList<>();
        List<Wire> wires = new ArrayList<>();
        Set<Integer> gateIds = new HashSet<>();
        int nextId = 0;
        int maxGateId = 0;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            try {
                switch (parts[0]) {
                    case "G" -> {
                        if (parts.length != 6) {
                            throw new IllegalArgumentException("Malformed gate entry.");
                        }
                        int id = Integer.parseInt(parts[1]);
                        if (!gateIds.add(id)) {
                            throw new IllegalArgumentException("Duplicate gate id " + id + ".");
                        }
                        gates.add(new CircuitState.GateState(
                                id,
                                GateType.valueOf(parts[2]),
                                Integer.parseInt(parts[3]),
                                Integer.parseInt(parts[4]),
                                "1".equals(parts[5])));
                        maxGateId = Math.max(maxGateId, id);
                    }
                    case "W" -> {
                        if (parts.length != 4) {
                            throw new IllegalArgumentException("Malformed wire entry.");
                        }
                        wires.add(new Wire(
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3])));
                    }
                    case "N" -> {
                        if (parts.length != 2) {
                            throw new IllegalArgumentException("Malformed next-id entry.");
                        }
                        nextId = Integer.parseInt(parts[1]);
                    }
                    default -> throw new IllegalArgumentException("Unknown schematic entry: " + parts[0]);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number in schematic data.", e);
            }
        }

        for (Wire wire : wires) {
            if (!gateIds.contains(wire.sourceId()) || !gateIds.contains(wire.targetId())) {
                throw new IllegalArgumentException("Wire references a missing gate.");
            }
        }
        return new CircuitState(gates, wires, Math.max(1, Math.max(nextId, maxGateId + 1)));
    }
}
