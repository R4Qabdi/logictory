package com.logictory.ui.freeplay;

import com.logictory.game.Circuit;
import com.logictory.game.CircuitSimulator;
import com.logictory.game.Gate;
import com.logictory.game.GateType;
import com.logictory.game.Wire;

import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Consumer;

/**
 * Freeplay board: drag gates around, wire outputs to inputs, toggle inputs and pan/zoom.
 * All geometry is stored in "world" coordinates; the view transform maps it to the screen.
 */
public final class CircuitCanvas extends JPanel {
    private static final int GRID = 10;
    private static final double MIN_ZOOM = 0.35;
    private static final double MAX_ZOOM = 3.0;
    private static final double PORT_HIT_RADIUS = 9.0;
    private static final double WIRE_HIT_WIDTH = 10.0;

    private final Circuit circuit;
    private final WireRouter router;
    private Consumer<String> statusSink = message -> {
    };
    private boolean stable = true;

    private double zoom = 1.0;
    private double panX = 60;
    private double panY = 40;
    private boolean autoFit = true;

    private Gate selectedGate;
    private Wire selectedWire;

    private Gate draggingGate;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean gateMoved;

    private Gate wiringSource;
    private Point2D wiringPoint;

    private GateType ghostType;
    private Point2D ghostPoint;

    private boolean panning;
    private Point panAnchor;
    private double panStartX;
    private double panStartY;
    private boolean spaceDown;

    private Gate hoverGate;
    private int hoverInput = -1;
    private boolean hoverOutput;

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
            Gate gate = circuit.addGate(ghostType,
                    snap((int) Math.round(origin.getX())),
                    snap((int) Math.round(origin.getY())));
            selectedGate = gate;
            selectedWire = null;
            router.invalidate();
            refreshSimulation();
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

    public void setWireStyle(WireStyle style) {
        router.setStyle(style);
        repaint();
    }

    public WireStyle wireStyle() {
        return router.style();
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
        circuit.clear();
        selectedGate = null;
        selectedWire = null;
        router.invalidate();
        refreshSimulation();
        repaint();
    }

    // ---------------------------------------------------------------- mouse handling

    private void handlePressed(MouseEvent e) {
        requestFocusInWindow();
        Point p = e.getPoint();

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
        PortHit port = findPort(world);
        if (port != null) {
            if (port.output()) {
                wiringSource = port.gate();
                wiringPoint = world;
                selectedGate = port.gate();
                selectedWire = null;
                updateStatus();
                repaint();
                return;
            }
            Wire existing = circuit.wireInto(port.gate().id(), port.index());
            if (existing != null) {
                circuit.disconnect(existing);
                router.invalidate();
                if (existing.equals(selectedWire)) {
                    selectedWire = null;
                }
                refreshSimulation();
                repaint();
                return;
            }
        }

        Gate gate = findGate(world);
        if (gate != null) {
            circuit.bringToFront(gate);
            selectedGate = gate;
            selectedWire = null;
            draggingGate = gate;
            gateMoved = false;
            dragOffsetX = world.getX() - gate.x();
            dragOffsetY = world.getY() - gate.y();
            updateStatus();
            repaint();
            return;
        }

        Wire wire = findWire(world);
        if (wire != null) {
            selectedWire = wire;
            selectedGate = null;
            updateStatus();
            repaint();
            return;
        }

        selectedGate = null;
        selectedWire = null;
        updateStatus();
        repaint();
    }

    private void handleDragged(MouseEvent e) {
        if (panning) {
            panX = panStartX + (e.getX() - panAnchor.x);
            panY = panStartY + (e.getY() - panAnchor.y);
            repaint();
            return;
        }

        Point2D world = screenToWorld(e.getPoint());
        if (draggingGate != null) {
            int nx = snap((int) Math.round(world.getX() - dragOffsetX));
            int ny = snap((int) Math.round(world.getY() - dragOffsetY));
            if (nx != draggingGate.x() || ny != draggingGate.y()) {
                draggingGate.moveTo(nx, ny);
                gateMoved = true;
                router.invalidate();
            }
            repaint();
            return;
        }

        if (wiringSource != null) {
            wiringPoint = world;
            repaint();
        }
    }

    private void handleReleased(MouseEvent e) {
        if (panning) {
            panning = false;
            updateCursor();
            return;
        }

        if (draggingGate != null) {
            if (!gateMoved && draggingGate.type() == GateType.INPUT) {
                draggingGate.setOutput(!draggingGate.output());
                refreshSimulation();
            }
            draggingGate = null;
            gateMoved = false;
            repaint();
            return;
        }

        if (wiringSource != null) {
            PortHit port = findPort(screenToWorld(e.getPoint()));
            if (port != null && !port.output() && port.gate() != wiringSource
                    && circuit.connect(wiringSource.id(), port.gate().id(), port.index())) {
                router.invalidate();
                refreshSimulation();
            }
            wiringSource = null;
            wiringPoint = null;
            repaint();
        }
    }

    private void handleMoved(MouseEvent e) {
        Point2D world = screenToWorld(e.getPoint());
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
        } else if (hoverGate != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else if (spaceDown) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void handleWheel(MouseWheelEvent e) {
        autoFit = false;
        Point2D before = screenToWorld(e.getPoint());
        double factor = Math.pow(1.1, -e.getPreciseWheelRotation());
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (newZoom == zoom) {
            return;
        }
        zoom = newZoom;
        panX = e.getX() - before.getX() * zoom;
        panY = e.getY() - before.getY() * zoom;
        updateStatus();
        repaint();
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
            Path2D path = router.pathFor(wire);
            if (path == null) {
                continue;
            }
            BasicStroke stroke = new BasicStroke((float) WIRE_HIT_WIDTH,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            if (stroke.createStrokedShape(path).contains(world)) {
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

        paintGrid(g2);

        g2.translate(panX, panY);
        g2.scale(zoom, zoom);

        paintWires(g2);
        paintGates(g2);
        paintWiringPreview(g2);
        paintGhost(g2);

        g2.dispose();
    }

    private void paintGrid(Graphics2D g2) {
        Point2D topLeft = screenToWorld(new Point(0, 0));
        Point2D bottomRight = screenToWorld(new Point(getWidth(), getHeight()));
        int startX = ((int) Math.floor(topLeft.getX() / GRID)) * GRID;
        int startY = ((int) Math.floor(topLeft.getY() / GRID)) * GRID;

        for (int x = startX; x <= bottomRight.getX(); x += GRID) {
            int sx = (int) Math.round(x * zoom + panX);
            g2.setColor(x % (GRID * 5) == 0 ? GatePainter.GRID_MAJOR : GatePainter.GRID_MINOR);
            g2.drawLine(sx, 0, sx, getHeight());
        }
        for (int y = startY; y <= bottomRight.getY(); y += GRID) {
            int sy = (int) Math.round(y * zoom + panY);
            g2.setColor(y % (GRID * 5) == 0 ? GatePainter.GRID_MAJOR : GatePainter.GRID_MINOR);
            g2.drawLine(0, sy, getWidth(), sy);
        }
    }

    private void paintWires(Graphics2D g2) {
        float width = (float) (2.4 / zoom);
        for (Wire wire : circuit.wires()) {
            Path2D path = router.pathFor(wire);
            if (path == null) {
                continue;
            }
            Gate source = circuit.gate(wire.sourceId());
            boolean on = source != null && source.output();
            boolean selected = wire.equals(selectedWire);
            g2.setColor(selected ? GatePainter.ACCENT : (on ? GatePainter.WIRE_ON : GatePainter.WIRE_OFF));
            g2.setStroke(new BasicStroke(selected ? width * 1.5f : width,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }
    }

    private void paintGates(Graphics2D g2) {
        for (Gate gate : circuit.gates()) {
            GatePainter.paintGate(g2, gate, gate == selectedGate, zoom);
        }
        for (Gate gate : circuit.gates()) {
            boolean hovered = gate == hoverGate;
            GatePainter.paintPorts(g2, gate, zoom, hovered ? hoverInput : -1, hovered && hoverOutput);
        }
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

    private void deleteSelection() {
        boolean changed = false;
        if (selectedGate != null) {
            circuit.removeGate(selectedGate);
            selectedGate = null;
            changed = true;
        }
        if (selectedWire != null) {
            circuit.disconnect(selectedWire);
            selectedWire = null;
            changed = true;
        }
        if (changed) {
            router.invalidate();
            refreshSimulation();
            repaint();
        }
    }

    private void updateStatus() {
        String selection = selectedGate != null
                ? selectedGate.type().displayName() + " #" + selectedGate.id()
                : selectedWire != null ? "a wire" : "nothing";
        statusSink.accept(String.format("Zoom %d%%  •  %s  •  selected: %s",
                (int) Math.round(zoom * 100),
                stable ? "circuit stable" : "unstable circuit (feedback loop)",
                selection));
    }

    private Point2D screenToWorld(Point p) {
        return new Point2D.Double((p.x - panX) / zoom, (p.y - panY) / zoom);
    }

    private static int snap(int value) {
        return Math.round(value / (float) GRID) * GRID;
    }

    private void installKeyBindings() {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteSelection");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("BACK_SPACE"), "deleteSelection");
        getActionMap().put("deleteSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelection();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelInteraction");
        getActionMap().put("cancelInteraction", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                draggingGate = null;
                wiringSource = null;
                wiringPoint = null;
                gateMoved = false;
                repaint();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed SPACE"), "panModeOn");
        getActionMap().put("panModeOn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                spaceDown = true;
                updateCursor();
            }
        });

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released SPACE"), "panModeOff");
        getActionMap().put("panModeOff", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                spaceDown = false;
                updateCursor();
            }
        });
    }

    private record PortHit(Gate gate, int index, boolean output) {
    }
}
