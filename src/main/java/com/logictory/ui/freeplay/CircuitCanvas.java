package com.logictory.ui.freeplay;

import com.logictory.game.Circuit;
import com.logictory.game.CircuitSimulator;
import com.logictory.game.CircuitState;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import com.logictory.game.Wire;
import com.logictory.prefs.AppPreferences;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Freeplay board: drag gates around, wire outputs to inputs, toggle inputs and pan/zoom.
 * All geometry is stored in "world" coordinates; the view transform maps it to the screen.
 * Selection is multi-item (shift/ctrl to add), and edits are undoable.
 */
public final class CircuitCanvas extends JPanel {
    private static final int GRID = 10;
    private static final double MIN_ZOOM = 0.35;
    private static final double MAX_ZOOM = 3.0;
    private static final double PORT_HIT_RADIUS = 9.0;
    private static final int MAX_HISTORY = 100;
    private static final int PASTE_OFFSET = GRID * 2;

    private final Circuit circuit;
    private final WireRouter router;
    private Consumer<String> statusSink = message -> {
    };
    private Runnable editStateListener = () -> {
    };
    private boolean stable = true;

    private double zoom = 1.0;
    private double panX = 60;
    private double panY = 40;
    private boolean autoFit = true;
    private boolean showGrid = true;
    private boolean snapToGrid = true;

    private final Set<Gate> selectedGates = new LinkedHashSet<>();
    private final Set<Wire> selectedWires = new LinkedHashSet<>();

    private Gate draggingGate;
    private Point2D dragStartWorld;
    private final Map<Gate, Point> dragOrigins = new LinkedHashMap<>();
    private boolean dragMoved;
    private boolean dragAdditive;
    private boolean dragSoleSelection;

    private Gate wiringSource;
    private Point2D wiringPoint;

    private Point2D marqueeStart;
    private Point2D marqueeEnd;
    private boolean marqueeAdditive;

    private GateType ghostType;
    private Point2D ghostPoint;
    private Point2D cursorWorld;

    private boolean panning;
    private Point panAnchor;
    private double panStartX;
    private double panStartY;
    private boolean spaceDown;
    private WindowFocusListener windowFocusListener;

    private double visibleLeft;
    private double visibleTop;
    private double visibleRight;
    private double visibleBottom;

    private Gate hoverGate;
    private int hoverInput = -1;
    private boolean hoverOutput;

    private final Deque<CircuitState> undoStack = new ArrayDeque<>();
    private final Deque<CircuitState> redoStack = new ArrayDeque<>();
    private CircuitState pendingUndo;

    private Clipboard clipboard;
    private int pasteSerial;

    public CircuitCanvas(Circuit circuit) {
        this.circuit = circuit;
        this.router = new WireRouter(circuit);
        setBackground(GatePainter.BG);
        setOpaque(true);
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleDragged(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleReleased(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMoved(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearHover();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(this::handleWheel);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (autoFit) {
                    fitView();
                }
            }
        });
        installKeyBindings();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window == null) {
            return;
        }
        if (windowFocusListener == null) {
            windowFocusListener = new WindowAdapter() {
                @Override
                public void windowLostFocus(WindowEvent e) {
                    // Do not leave the board stuck in pan mode after Alt+Tab.
                    spaceDown = false;
                    panning = false;
                    updateCursor();
                }
            };
        }
        window.removeWindowFocusListener(windowFocusListener);
        window.addWindowFocusListener(windowFocusListener);
    }

    @Override
    public void removeNotify() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && windowFocusListener != null) {
            window.removeWindowFocusListener(windowFocusListener);
        }
        super.removeNotify();
    }

    /** Gives the board keyboard focus so shortcuts (and hold-space panning) work immediately. */
    public void focusCanvas() {
        requestFocusInWindow();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 620);
    }

    // ---------------------------------------------------------------- palette drag

    public void beginPaletteDrag(GateType type, Point canvasPoint) {
        requestFocusInWindow();
        ghostType = type;
        ghostPoint = toGateOrigin(screenToWorld(canvasPoint));
        repaint();
    }

    public void updatePaletteDrag(Point canvasPoint) {
        if (ghostType == null) {
            return;
        }
        ghostPoint = toGateOrigin(screenToWorld(canvasPoint));
        repaint();
    }

    public void endPaletteDrag(Point canvasPoint) {
        if (ghostType == null) {
            return;
        }
        if (contains(canvasPoint)) {
            Point2D origin = toGateOrigin(screenToWorld(canvasPoint));
            beginMutation();
            Gate gate = circuit.addGate(ghostType,
                    snap((int) Math.round(origin.getX())),
                    snap((int) Math.round(origin.getY())));
            clearSelection();
            selectedGates.add(gate);
            router.invalidate();
            refreshSimulation();
            commitMutation();
        }
        ghostType = null;
        ghostPoint = null;
        repaint();
    }

    private static Point2D toGateOrigin(Point2D cursorWorld) {
        return new Point2D.Double(cursorWorld.getX() - Gate.WIDTH / 2.0,
                cursorWorld.getY() - Gate.HEIGHT / 2.0);
    }

    // ---------------------------------------------------------------- public commands

    public void setStatusSink(Consumer<String> sink) {
        this.statusSink = sink;
        updateStatus();
    }

    public void setEditStateListener(Runnable listener) {
        this.editStateListener = listener == null ? () -> {
        } : listener;
        editStateListener.run();
    }

    public void setWireStyle(WireStyle style) {
        router.setStyle(style);
        repaint();
    }

    public WireStyle wireStyle() {
        return router.style();
    }

    /** Applies the persisted preferences to this board. */
    public void applyPreferences(AppPreferences preferences) {
        setWireStyle(preferences.wireStyle());
        showGrid = preferences.showGrid();
        snapToGrid = preferences.snapToGrid();
        repaint();
    }

    public boolean showGrid() {
        return showGrid;
    }

    public boolean snapToGrid() {
        return snapToGrid;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public boolean hasSelection() {
        return !selectedGates.isEmpty() || !selectedWires.isEmpty();
    }

    public boolean hasGateSelection() {
        return !selectedGates.isEmpty();
    }

    public boolean hasClipboard() {
        return clipboard != null && !clipboard.gates().isEmpty();
    }

    public void refreshSimulation() {
        stable = CircuitSimulator.simulate(circuit);
        updateStatus();
    }

    public void resetView() {
        autoFit = false;
        zoom = 1.0;
        panX = 60;
        panY = 40;
        updateStatus();
        repaint();
    }

    public void zoomIn() {
        zoomAt(1.1, new Point(getWidth() / 2, getHeight() / 2));
    }

    public void zoomOut() {
        zoomAt(1 / 1.1, new Point(getWidth() / 2, getHeight() / 2));
    }

    /** Zooms/pans so every gate is visible with a small margin. */
    public void fitView() {
        autoFit = true;
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (circuit.gates().isEmpty()) {
            resetView();
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Gate gate : circuit.gates()) {
            minX = Math.min(minX, gate.x());
            minY = Math.min(minY, gate.y());
            maxX = Math.max(maxX, gate.x() + Gate.WIDTH);
            maxY = Math.max(maxY, gate.y() + Gate.HEIGHT);
        }
        double contentWidth = Math.max(1, maxX - minX) + 80;
        double contentHeight = Math.max(1, maxY - minY) + 80;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
                Math.min(getWidth() / contentWidth, getHeight() / contentHeight)));
        panX = getWidth() / 2.0 - (minX + maxX) / 2.0 * zoom;
        panY = getHeight() / 2.0 - (minY + maxY) / 2.0 * zoom;
        updateStatus();
        repaint();
    }

    public void clearBoard() {
        if (circuit.gates().isEmpty() && circuit.wires().isEmpty()) {
            return;
        }
        beginMutation();
        circuit.clear();
        clearSelection();
        router.invalidate();
        refreshSimulation();
        commitMutation();
        repaint();
    }

    /** Replaces the board with a schematic snapshot. The change can be undone. */
    public void loadState(CircuitState state) {
        cancelInteraction();
        beginMutation();
        circuit.restore(state);
        clearSelection();
        router.invalidate();
        refreshSimulation();
        commitMutation();
        fitView();
        repaint();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        pendingUndo = null;
        redoStack.push(circuit.snapshot());
        restoreState(undoStack.pop());
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        pendingUndo = null;
        undoStack.push(circuit.snapshot());
        restoreState(redoStack.pop());
    }

    public void selectAll() {
        selectedGates.clear();
        selectedWires.clear();
        selectedGates.addAll(circuit.gates());
        selectedWires.addAll(circuit.wires());
        updateStatus();
        repaint();
    }

    public void copySelection() {
        if (selectedGates.isEmpty()) {
            return;
        }
        List<CircuitState.GateState> gates = new ArrayList<>();
        Set<Integer> ids = new LinkedHashSet<>();
        for (Gate gate : circuit.gates()) {
            if (selectedGates.contains(gate)) {
                gates.add(new CircuitState.GateState(
                        gate.id(), gate.type(), gate.x(), gate.y(), gate.output()));
                ids.add(gate.id());
            }
        }
        List<Wire> wires = new ArrayList<>();
        for (Wire wire : circuit.wires()) {
            if (ids.contains(wire.sourceId()) && ids.contains(wire.targetId())) {
                wires.add(wire);
            }
        }
        clipboard = new Clipboard(gates, wires);
        pasteSerial = 0;
        updateStatus();
    }

    public void copyState(CircuitState state) {
        clipboard = new Clipboard(state.gates(), state.wires());
        pasteSerial = 0;
        updateStatus();
    }

    /** Returns a standalone snapshot of the selected gates and the wires between them. */
    public CircuitState selectedState() {
        if (selectedGates.isEmpty()) {
            return null;
        }
        List<CircuitState.GateState> gates = new ArrayList<>();
        Set<Integer> ids = new LinkedHashSet<>();
        int nextId = 1;
        for (Gate gate : circuit.gates()) {
            if (selectedGates.contains(gate)) {
                gates.add(new CircuitState.GateState(
                        gate.id(), gate.type(), gate.x(), gate.y(), gate.output()));
                ids.add(gate.id());
                nextId = Math.max(nextId, gate.id() + 1);
            }
        }
        List<Wire> wires = new ArrayList<>();
        for (Wire wire : circuit.wires()) {
            if (ids.contains(wire.sourceId()) && ids.contains(wire.targetId())) {
                wires.add(wire);
            }
        }
        return new CircuitState(gates, wires, nextId);
    }

    public CircuitState schematicState() {
        CircuitState selected = selectedState();
        return selected == null ? circuit.snapshot() : selected;
    }

    public void cutSelection() {
        if (!hasSelection()) {
            return;
        }
        copySelection();
        deleteSelection();
    }

    public void pasteClipboard() {
        if (!hasClipboard()) {
            return;
        }
        pasteClipboardAt(cursorWorld);
    }

    void pasteClipboardAt(Point2D cursor) {
        beginMutation();
        clearSelection();
        double offsetX;
        double offsetY;
        if (cursor == null) {
            int offset = PASTE_OFFSET * ++pasteSerial;
            offsetX = offset;
            offsetY = offset;
        } else {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (CircuitState.GateState gateState : clipboard.gates()) {
                minX = Math.min(minX, gateState.x());
                minY = Math.min(minY, gateState.y());
                maxX = Math.max(maxX, gateState.x() + Gate.WIDTH);
                maxY = Math.max(maxY, gateState.y() + Gate.HEIGHT);
            }
            offsetX = cursor.getX() - (minX + maxX) / 2.0;
            offsetY = cursor.getY() - (minY + maxY) / 2.0;
        }
        Map<Integer, Gate> remapped = new HashMap<>();
        for (CircuitState.GateState gateState : clipboard.gates()) {
            Gate gate = circuit.addGate(gateState.type(),
                    snap((int) Math.round(gateState.x() + offsetX)),
                    snap((int) Math.round(gateState.y() + offsetY)));
            gate.setOutput(gateState.output());
            remapped.put(gateState.id(), gate);
            selectedGates.add(gate);
        }
        for (Wire wire : clipboard.wires()) {
            Gate source = remapped.get(wire.sourceId());
            Gate target = remapped.get(wire.targetId());
            if (source != null && target != null) {
                circuit.connect(source.id(), target.id(), wire.targetPort());
            }
        }
        router.invalidate();
        refreshSimulation();
        commitMutation();
        repaint();
    }

    // ---------------------------------------------------------------- mouse handling

    private void handlePressed(MouseEvent e) {
        requestFocusInWindow();
        Point p = e.getPoint();
        cursorWorld = screenToWorld(p);

        if (SwingUtilities.isMiddleMouseButton(e) || (spaceDown && SwingUtilities.isLeftMouseButton(e))) {
            autoFit = false;
            panning = true;
            panAnchor = p;
            panStartX = panX;
            panStartY = panY;
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return;
        }
        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        Point2D world = screenToWorld(p);
        boolean additive = e.isShiftDown() || e.isControlDown();

        PortHit port = findPort(world);
        if (port != null) {
            if (port.output()) {
                wiringSource = port.gate();
                wiringPoint = world;
                clearSelection();
                selectedGates.add(port.gate());
                updateStatus();
                repaint();
                return;
            }
            Wire existing = circuit.wireInto(port.gate().id(), port.index());
            if (existing != null) {
                beginMutation();
                circuit.disconnect(existing);
                selectedWires.remove(existing);
                router.invalidate();
                refreshSimulation();
                commitMutation();
                repaint();
                return;
            }
        }

        Gate gate = findGate(world);
        if (gate != null) {
            if (additive && selectedGates.contains(gate)) {
                selectedGates.remove(gate);
                updateStatus();
                repaint();
                return;
            }
            circuit.bringToFront(gate);
            if (additive) {
                selectedGates.add(gate);
            } else if (!selectedGates.contains(gate)) {
                clearSelection();
                selectedGates.add(gate);
            }
            draggingGate = gate;
            dragStartWorld = world;
            dragMoved = false;
            dragAdditive = additive;
            dragSoleSelection = !additive && selectedGates.size() == 1
                    && selectedGates.contains(gate) && selectedWires.isEmpty();
            dragOrigins.clear();
            for (Gate selected : selectedGates) {
                dragOrigins.put(selected, new Point(selected.x(), selected.y()));
            }
            beginMutation();
            updateStatus();
            repaint();
            return;
        }

        Wire wire = findWire(world);
        if (wire != null) {
            if (additive) {
                if (!selectedWires.add(wire)) {
                    selectedWires.remove(wire);
                }
            } else {
                clearSelection();
                selectedWires.add(wire);
            }
            updateStatus();
            repaint();
            return;
        }

        if (!additive) {
            clearSelection();
        }
        marqueeAdditive = additive;
        marqueeStart = world;
        marqueeEnd = world;
        updateCursor();
        updateStatus();
        repaint();
    }

    private void handleDragged(MouseEvent e) {
        if (panning) {
            panX = panStartX + (e.getX() - panAnchor.x);
            panY = panStartY + (e.getY() - panAnchor.y);
            cursorWorld = screenToWorld(e.getPoint());
            repaint();
            return;
        }

        Point2D world = screenToWorld(e.getPoint());
        cursorWorld = world;
        if (draggingGate != null && dragStartWorld != null) {
            int dx = snap((int) Math.round(world.getX() - dragStartWorld.getX()));
            int dy = snap((int) Math.round(world.getY() - dragStartWorld.getY()));
            boolean changed = false;
            for (Map.Entry<Gate, Point> entry : dragOrigins.entrySet()) {
                Gate gate = entry.getKey();
                int nx = entry.getValue().x + dx;
                int ny = entry.getValue().y + dy;
                if (gate.x() != nx || gate.y() != ny) {
                    gate.moveTo(nx, ny);
                    changed = true;
                }
            }
            if (changed) {
                dragMoved = true;
                // Full lane/hop routing is O(n^2); use simple paths until the drag ends.
                router.setFastMode(true);
                router.invalidate();
            }
            repaint();
            return;
        }

        if (wiringSource != null) {
            wiringPoint = world;
            repaint();
            return;
        }

        if (marqueeStart != null) {
            marqueeEnd = world;
            repaint();
        }
    }

    private void handleReleased(MouseEvent e) {
        cursorWorld = screenToWorld(e.getPoint());
        if (panning) {
            panning = false;
            updateCursor();
            return;
        }

        if (draggingGate != null) {
            Gate clicked = draggingGate;
            if (!dragMoved) {
                if (!dragAdditive && (selectedGates.size() > 1 || !selectedWires.isEmpty())) {
                    clearSelection();
                    selectedGates.add(clicked);
                }
                if (dragSoleSelection && clicked.type() == GateType.INPUT) {
                    clicked.setOutput(!clicked.output());
                    refreshSimulation();
                }
            }
            draggingGate = null;
            dragStartWorld = null;
            dragOrigins.clear();
            dragMoved = false;
            dragAdditive = false;
            dragSoleSelection = false;
            router.setFastMode(false);
            router.invalidate();
            commitMutation();
            repaint();
            return;
        }

        if (wiringSource != null) {
            PortHit port = findPort(screenToWorld(e.getPoint()));
            if (port != null && !port.output() && port.gate() != wiringSource) {
                beginMutation();
                if (circuit.connect(wiringSource.id(), port.gate().id(), port.index())) {
                    router.invalidate();
                    refreshSimulation();
                    commitMutation();
                } else {
                    pendingUndo = null;
                }
            }
            wiringSource = null;
            wiringPoint = null;
            repaint();
            return;
        }

        if (marqueeStart != null) {
            finishMarquee();
        }
    }

    private void handleMoved(MouseEvent e) {
        Point2D world = screenToWorld(e.getPoint());
        cursorWorld = world;
        PortHit port = findPort(world);
        Gate newGate = port == null ? null : port.gate();
        int newInput = port == null || port.output() ? -1 : port.index();
        boolean newOutput = port != null && port.output();

        if (newGate != hoverGate || newInput != hoverInput || newOutput != hoverOutput) {
            hoverGate = newGate;
            hoverInput = newInput;
            hoverOutput = newOutput;
            updateCursor();
            repaint();
        }

        if (wiringSource != null) {
            wiringPoint = world;
            repaint();
        }
    }

    private void clearHover() {
        if (hoverGate != null || hoverInput != -1 || hoverOutput) {
            hoverGate = null;
            hoverInput = -1;
            hoverOutput = false;
            updateCursor();
            repaint();
        }
    }

    private void updateCursor() {
        if (panning) {
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        } else if (hoverGate != null || marqueeStart != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else if (spaceDown) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void handleWheel(MouseWheelEvent e) {
        zoomAt(Math.pow(1.1, -e.getPreciseWheelRotation()), e.getPoint());
    }

    private void zoomAt(double factor, Point anchor) {
        autoFit = false;
        Point2D before = screenToWorld(anchor);
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (newZoom == zoom) {
            return;
        }
        zoom = newZoom;
        panX = anchor.x - before.getX() * zoom;
        panY = anchor.y - before.getY() * zoom;
        updateStatus();
        repaint();
    }

    // ---------------------------------------------------------------- selection

    private void finishMarquee() {
        Point2D start = marqueeStart;
        Point2D end = marqueeEnd;
        boolean additive = marqueeAdditive;
        marqueeStart = null;
        marqueeEnd = null;
        marqueeAdditive = false;

        if (start == null || end == null) {
            return;
        }
        Rectangle2D area = rectangleBetween(start, end);
        if (area.getWidth() < 3 && area.getHeight() < 3) {
            if (!additive) {
                clearSelection();
            }
        } else {
            for (Gate gate : circuit.gates()) {
                Rectangle2D bounds = new Rectangle2D.Double(
                        gate.x(), gate.y(), Gate.WIDTH, Gate.HEIGHT);
                if (area.intersects(bounds)) {
                    selectedGates.add(gate);
                }
            }
            for (Wire wire : circuit.wires()) {
                if (router.intersects(wire, area)) {
                    selectedWires.add(wire);
                }
            }
        }
        updateCursor();
        updateStatus();
        repaint();
    }

    private static Rectangle2D rectangleBetween(Point2D a, Point2D b) {
        double x = Math.min(a.getX(), b.getX());
        double y = Math.min(a.getY(), b.getY());
        return new Rectangle2D.Double(x, y,
                Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
    }

    private void clearSelection() {
        selectedGates.clear();
        selectedWires.clear();
    }

    // ---------------------------------------------------------------- history

    private void beginMutation() {
        if (pendingUndo == null) {
            pendingUndo = circuit.snapshot();
        }
    }

    private void commitMutation() {
        if (pendingUndo == null) {
            return;
        }
        CircuitState before = pendingUndo;
        pendingUndo = null;
        if (before.equals(circuit.snapshot())) {
            return;
        }
        undoStack.push(before);
        while (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
        updateStatus();
    }

    private void restoreState(CircuitState state) {
        circuit.restore(state);
        clearSelection();
        cancelInteraction();
        router.invalidate();
        refreshSimulation();
        repaint();
    }

    /** Reverts an in-progress gesture (for example a drag cancelled with Escape). */
    private void revertPending() {
        if (pendingUndo == null) {
            return;
        }
        CircuitState before = pendingUndo;
        pendingUndo = null;
        if (!before.equals(circuit.snapshot())) {
            circuit.restore(before);
            router.invalidate();
            refreshSimulation();
        }
    }

    private void cancelInteraction() {
        draggingGate = null;
        dragStartWorld = null;
        dragOrigins.clear();
        dragMoved = false;
        dragAdditive = false;
        dragSoleSelection = false;
        wiringSource = null;
        wiringPoint = null;
        marqueeStart = null;
        marqueeEnd = null;
        marqueeAdditive = false;
        pendingUndo = null;
        router.setFastMode(false);
    }

    // ---------------------------------------------------------------- hit testing

    private Gate findGate(Point2D world) {
        List<Gate> gates = circuit.gates();
        for (int i = gates.size() - 1; i >= 0; i--) {
            Gate gate = gates.get(i);
            if (world.getX() >= gate.x() && world.getX() <= gate.x() + Gate.WIDTH
                    && world.getY() >= gate.y() && world.getY() <= gate.y() + Gate.HEIGHT) {
                return gate;
            }
        }
        return null;
    }

    private PortHit findPort(Point2D world) {
        List<Gate> gates = circuit.gates();
        for (int i = gates.size() - 1; i >= 0; i--) {
            Gate gate = gates.get(i);
            if (gate.type().outputCount() > 0) {
                Point output = gate.outputPort();
                if (output.distance(world) <= PORT_HIT_RADIUS) {
                    return new PortHit(gate, -1, true);
                }
            }
            for (int port = 0; port < gate.type().inputCount(); port++) {
                Point input = gate.inputPort(port);
                if (input.distance(world) <= PORT_HIT_RADIUS) {
                    return new PortHit(gate, port, false);
                }
            }
        }
        return null;
    }

    private Wire findWire(Point2D world) {
        List<Wire> wires = circuit.wires();
        for (int i = wires.size() - 1; i >= 0; i--) {
            Wire wire = wires.get(i);
            if (router.hitTest(wire, world)) {
                return wire;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        visibleLeft = -panX / zoom;
        visibleTop = -panY / zoom;
        visibleRight = (getWidth() - panX) / zoom;
        visibleBottom = (getHeight() - panY) / zoom;

        if (showGrid) {
            paintGrid(g2);
        }

        g2.translate(panX, panY);
        g2.scale(zoom, zoom);

        paintWires(g2);
        paintGates(g2);
        paintMarquee(g2);
        paintWiringPreview(g2);
        paintGhost(g2);

        g2.dispose();
    }

    private void paintGrid(Graphics2D g2) {
        // At low zoom the 10px grid becomes visual noise and costs many draw calls,
        // so only the major lines are drawn until there is room for the minor ones.
        boolean drawMinor = zoom * GRID >= 5;
        int step = drawMinor ? GRID : GRID * 5;
        int startX = ((int) Math.floor(visibleLeft / step)) * step;
        int startY = ((int) Math.floor(visibleTop / step)) * step;

        for (int x = startX; x <= visibleRight; x += step) {
            int sx = (int) Math.round(x * zoom + panX);
            g2.setColor(!drawMinor || x % (GRID * 5) == 0
                    ? GatePainter.GRID_MAJOR : GatePainter.GRID_MINOR);
            g2.drawLine(sx, 0, sx, getHeight());
        }
        for (int y = startY; y <= visibleBottom; y += step) {
            int sy = (int) Math.round(y * zoom + panY);
            g2.setColor(!drawMinor || y % (GRID * 5) == 0
                    ? GatePainter.GRID_MAJOR : GatePainter.GRID_MINOR);
            g2.drawLine(0, sy, getWidth(), sy);
        }
    }

    private void paintWires(Graphics2D g2) {
        float width = (float) (2.4 / zoom);
        BasicStroke normal = new BasicStroke(width,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        BasicStroke highlighted = new BasicStroke(width * 1.5f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        for (Wire wire : circuit.wires()) {
            Path2D path = router.pathFor(wire);
            if (path == null || !isVisible(path.getBounds2D())) {
                continue;
            }
            Gate source = circuit.gate(wire.sourceId());
            boolean on = source != null && source.output();
            boolean selected = selectedWires.contains(wire);
            g2.setColor(selected ? GatePainter.ACCENT : (on ? GatePainter.WIRE_ON : GatePainter.WIRE_OFF));
            g2.setStroke(selected ? highlighted : normal);
            g2.draw(path);
        }
    }

    private void paintGates(Graphics2D g2) {
        for (Gate gate : circuit.gates()) {
            if (isGateVisible(gate)) {
                GatePainter.paintGate(g2, gate, selectedGates.contains(gate), zoom);
            }
        }
        for (Gate gate : circuit.gates()) {
            if (!isGateVisible(gate)) {
                continue;
            }
            boolean hovered = gate == hoverGate;
            GatePainter.paintPorts(g2, gate, zoom, hovered ? hoverInput : -1, hovered && hoverOutput);
        }
    }

    private boolean isGateVisible(Gate gate) {
        return gate.x() + Gate.WIDTH >= visibleLeft && gate.x() <= visibleRight
                && gate.y() + Gate.HEIGHT >= visibleTop && gate.y() <= visibleBottom;
    }

    private boolean isVisible(Rectangle2D bounds) {
        return bounds.getMaxX() >= visibleLeft && bounds.getMinX() <= visibleRight
                && bounds.getMaxY() >= visibleTop && bounds.getMinY() <= visibleBottom;
    }

    private void paintMarquee(Graphics2D g2) {
        if (marqueeStart == null || marqueeEnd == null) {
            return;
        }
        Rectangle2D area = rectangleBetween(marqueeStart, marqueeEnd);
        g2.setColor(new Color(0xff, 0xb4, 0x54, 36));
        g2.fill(area);
        g2.setColor(GatePainter.ACCENT);
        g2.setStroke(new BasicStroke((float) (1.2 / zoom), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{(float) (6 / zoom), (float) (4 / zoom)}, 0f));
        g2.draw(area);
    }

    private void paintWiringPreview(Graphics2D g2) {
        if (wiringSource == null || wiringPoint == null) {
            return;
        }
        Path2D path = router.previewPath(wiringSource.outputPort(), wiringPoint);
        g2.setColor(GatePainter.ACCENT);
        g2.setStroke(new BasicStroke((float) (2.0 / zoom), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{(float) (8 / zoom), (float) (6 / zoom)}, 0f));
        g2.draw(path);
    }

    private void paintGhost(Graphics2D g2) {
        if (ghostType == null || ghostPoint == null) {
            return;
        }
        java.awt.Composite old = g2.getComposite();
        g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f));
        GatePainter.paintGhost(g2, ghostType,
                (int) Math.round(ghostPoint.getX()), (int) Math.round(ghostPoint.getY()), zoom);
        g2.setComposite(old);
    }

    // ---------------------------------------------------------------- helpers

    public void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        beginMutation();
        List<Gate> gates = new ArrayList<>(selectedGates);
        List<Wire> wires = new ArrayList<>(selectedWires);
        clearSelection();
        for (Gate gate : gates) {
            circuit.removeGate(gate);
        }
        for (Wire wire : wires) {
            circuit.disconnect(wire);
        }
        router.invalidate();
        refreshSimulation();
        commitMutation();
        repaint();
    }

    private void updateStatus() {
        statusSink.accept(String.format("Zoom %d%%  •  %s  •  selected: %s",
                (int) Math.round(zoom * 100),
                stable ? "circuit stable" : "unstable circuit (feedback loop)",
                selectionSummary()));
        editStateListener.run();
    }

    private String selectionSummary() {
        int gates = selectedGates.size();
        int wires = selectedWires.size();
        if (gates == 0 && wires == 0) {
            return "nothing";
        }
        if (gates == 1 && wires == 0) {
            Gate gate = selectedGates.iterator().next();
            return gate.type().displayName() + " #" + gate.id();
        }
        if (gates == 0 && wires == 1) {
            return "a wire";
        }
        if (wires == 0) {
            return gates + " gates";
        }
        if (gates == 0) {
            return wires + " wires";
        }
        return gates + " gates, " + wires + " wires";
    }

    private Point2D screenToWorld(Point p) {
        return new Point2D.Double((p.x - panX) / zoom, (p.y - panY) / zoom);
    }

    private int snap(int value) {
        return snapToGrid ? Math.round(value / (float) GRID) * GRID : value;
    }

    private void installKeyBindings() {
        int menuMask;
        try {
            menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException e) {
            menuMask = InputEvent.CTRL_DOWN_MASK;
        }

        bind(KeyStroke.getKeyStroke("DELETE"), "deleteSelection", this::deleteSelection);
        bind(KeyStroke.getKeyStroke("BACK_SPACE"), "deleteSelection", this::deleteSelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "undo", this::undo);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | InputEvent.SHIFT_DOWN_MASK), "redo", this::redo);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "redo", this::redo);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask), "selectAll", this::selectAll);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask), "copySelection", this::copySelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask), "cutSelection", this::cutSelection);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask), "pasteClipboard", this::pasteClipboard);

        bind(KeyStroke.getKeyStroke("ESCAPE"), "cancelInteraction", () -> {
            revertPending();
            cancelInteraction();
            clearSelection();
            updateStatus();
            repaint();
        });

        // Works wherever focus is inside the freeplay window, not only after clicking
        // the board. The status hint explicitly advertises hold-space panning.
        bindGlobal(KeyStroke.getKeyStroke("pressed SPACE"), "panModeOn", () -> {
            spaceDown = true;
            updateCursor();
        });
        bindGlobal(KeyStroke.getKeyStroke("released SPACE"), "panModeOff", () -> {
            spaceDown = false;
            updateCursor();
        });
    }

    private void bind(KeyStroke stroke, String name, Runnable action) {
        bindTo(getInputMap(WHEN_FOCUSED), stroke, name, action);
    }

    private void bindGlobal(KeyStroke stroke, String name, Runnable action) {
        bindTo(getInputMap(WHEN_IN_FOCUSED_WINDOW), stroke, name, action);
    }

    private void bindTo(javax.swing.InputMap inputMap, KeyStroke stroke, String name, Runnable action) {
        inputMap.put(stroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private record PortHit(Gate gate, int index, boolean output) {
    }

    private record Clipboard(List<CircuitState.GateState> gates, List<Wire> wires) {
    }
}
