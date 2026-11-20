package com.logictory.ui.freeplay;

import com.logictory.game.Circuit;
import com.logictory.game.Gate;
import com.logictory.game.Wire;

import java.awt.Point;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the drawn shape of every wire:
 * <ul>
 *   <li>square or curved (bezier) routing, selectable at runtime;</li>
 *   <li>wires whose paths run on top of each other are pushed into separate lanes;</li>
 *   <li>when two wires cross, the later wire draws a small hop (bridge) over the earlier one.</li>
 * </ul>
 * Results are cached and recomputed lazily after {@link #invalidate()}.
 */
public final class WireRouter {
    private static final double STUB = 24.0;
    private static final double LANE_GAP = 8.0;
    private static final double HOP_RADIUS = 5.0;
    private static final int BEZIER_SAMPLES = 32;
    private static final double PARALLEL_DISTANCE = 2.0;
    private static final double PARALLEL_SIN = 0.15;
    private static final double MIN_OVERLAP = 4.0;

    private final Circuit circuit;
    private WireStyle style = WireStyle.SQUARE;
    private boolean dirty = true;
    private Map<Wire, WirePath> paths = Map.of();

    public WireRouter(Circuit circuit) {
        this.circuit = circuit;
    }

    public WireStyle style() {
        return style;
    }

    public void setStyle(WireStyle style) {
        if (this.style != style) {
            this.style = style;
            dirty = true;
        }
    }

    public void invalidate() {
        dirty = true;
    }

    /** Path used for painting and hit-testing (already contains lane offsets and hops). */
    public Path2D pathFor(Wire wire) {
        ensure();
        WirePath path = paths.get(wire);
        return path == null ? null : path.display();
    }

    /** Rubber-band path while dragging a new connection; no lanes or hops needed. */
    public Path2D previewPath(Point from, Point2D to) {
        List<Point2D.Double> polyline = style == WireStyle.SQUARE
                ? squarePoints(from, to, 0)
                : bezierPoints(from, to, 0);
        return polylinePath(polyline);
    }

    public int laneOf(Wire wire) {
        ensure();
        WirePath path = paths.get(wire);
        return path == null ? 0 : path.lane();
    }

    public int hopCount(Wire wire) {
        ensure();
        WirePath path = paths.get(wire);
        return path == null ? 0 : path.hops();
    }

    private void ensure() {
        if (!dirty) {
            return;
        }
        rebuild();
        dirty = false;
    }

    private void rebuild() {
        List<Wire> wires = circuit.wires();
        List<List<Point2D.Double>> probes = new ArrayList<>(wires.size());
        for (Wire wire : wires) {
            probes.add(polyline(wire, 0));
        }

        int[] lanes = new int[wires.size()];
        for (int i = 0; i < wires.size(); i++) {
            Set<Integer> used = new HashSet<>();
            for (int j = 0; j < i; j++) {
                if (overlaps(probes.get(i), probes.get(j))) {
                    used.add(lanes[j]);
                }
            }
            lanes[i] = smallestFreeLane(used);
        }

        List<List<Point2D.Double>> routed = new ArrayList<>(wires.size());
        for (int i = 0; i < wires.size(); i++) {
            routed.add(polyline(wires.get(i), lanes[i] * LANE_GAP));
        }

        Map<Wire, WirePath> result = new HashMap<>();
        for (int i = 0; i < wires.size(); i++) {
            List<Crossing> crossings = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                collectCrossings(routed.get(i), routed.get(j), crossings);
            }
            result.put(wires.get(i),
                    new WirePath(buildDisplay(routed.get(i), crossings), crossings.size(), lanes[i]));
        }
        paths = result;
    }

    private List<Point2D.Double> polyline(Wire wire, double laneOffset) {
        Gate source = circuit.gate(wire.sourceId());
        Gate target = circuit.gate(wire.targetId());
        if (source == null || target == null) {
            return List.of();
        }
        Point from = source.outputPort();
        Point to = target.inputPort(wire.targetPort());
        return style == WireStyle.SQUARE
                ? squarePoints(from, to, laneOffset)
                : bezierPoints(from, to, laneOffset);
    }

    private static List<Point2D.Double> squarePoints(Point from, Point2D to, double offset) {
        double x1 = from.x;
        double y1 = from.y;
        double x2 = to.getX();
        double y2 = to.getY();

        List<Point2D.Double> points = new ArrayList<>();
        points.add(new Point2D.Double(x1, y1));
        if (x2 >= x1 + STUB * 2) {
            double midX = (x1 + x2) / 2 + offset;
            points.add(new Point2D.Double(midX, y1));
            points.add(new Point2D.Double(midX, y2));
        } else {
            double outX = x1 + STUB;
            double inX = x2 - STUB;
            double midY = (y1 + y2) / 2 + offset;
            points.add(new Point2D.Double(outX, y1));
            points.add(new Point2D.Double(outX, midY));
            points.add(new Point2D.Double(inX, midY));
            points.add(new Point2D.Double(inX, y2));
        }
        points.add(new Point2D.Double(x2, y2));
        return points;
    }

    private static List<Point2D.Double> bezierPoints(Point from, Point2D to, double offset) {
        double x1 = from.x;
        double y1 = from.y;
        double x2 = to.getX();
        double y2 = to.getY();
        double dx = Math.max(40, Math.abs(x2 - x1) / 2);

        double c1x = x1 + dx;
        double c1y = y1 + offset;
        double c2x = x2 - dx;
        double c2y = y2 + offset;

        List<Point2D.Double> points = new ArrayList<>(BEZIER_SAMPLES + 1);
        for (int i = 0; i <= BEZIER_SAMPLES; i++) {
            double t = i / (double) BEZIER_SAMPLES;
            double mt = 1 - t;
            double x = mt * mt * mt * x1 + 3 * mt * mt * t * c1x + 3 * mt * t * t * c2x + t * t * t * x2;
            double y = mt * mt * mt * y1 + 3 * mt * mt * t * c1y + 3 * mt * t * t * c2y + t * t * t * y2;
            points.add(new Point2D.Double(x, y));
        }
        return points;
    }

    private static int smallestFreeLane(Set<Integer> used) {
        if (!used.contains(0)) {
            return 0;
        }
        for (int k = 1; k < 1000; k++) {
            if (!used.contains(k)) {
                return k;
            }
            if (!used.contains(-k)) {
                return -k;
            }
        }
        return 0;
    }

    private static boolean overlaps(List<Point2D.Double> a, List<Point2D.Double> b) {
        for (int i = 0; i + 1 < a.size(); i++) {
            for (int j = 0; j + 1 < b.size(); j++) {
                if (collinearOverlap(a.get(i), a.get(i + 1), b.get(j), b.get(j + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean collinearOverlap(Point2D a1, Point2D a2, Point2D b1, Point2D b2) {
        double dx = a2.getX() - a1.getX();
        double dy = a2.getY() - a1.getY();
        double length = Math.hypot(dx, dy);
        if (length < 1e-6) {
            return false;
        }
        double ux = dx / length;
        double uy = dy / length;

        double db1 = Math.abs((b1.getX() - a1.getX()) * -uy + (b1.getY() - a1.getY()) * ux);
        double db2 = Math.abs((b2.getX() - a1.getX()) * -uy + (b2.getY() - a1.getY()) * ux);
        if (db1 > PARALLEL_DISTANCE || db2 > PARALLEL_DISTANCE) {
            return false;
        }

        double bdx = b2.getX() - b1.getX();
        double bdy = b2.getY() - b1.getY();
        double bLength = Math.hypot(bdx, bdy);
        if (bLength < 1e-6) {
            return false;
        }
        double cross = Math.abs((bdx / bLength) * uy - (bdy / bLength) * ux);
        if (cross > PARALLEL_SIN) {
            return false;
        }

        double t1 = (b1.getX() - a1.getX()) * ux + (b1.getY() - a1.getY()) * uy;
        double t2 = (b2.getX() - a1.getX()) * ux + (b2.getY() - a1.getY()) * uy;
        double lo = Math.max(0, Math.min(t1, t2));
        double hi = Math.min(length, Math.max(t1, t2));
        return hi - lo > MIN_OVERLAP;
    }

    private static void collectCrossings(List<Point2D.Double> a, List<Point2D.Double> b,
                                         List<Crossing> out) {
        for (int i = 0; i + 1 < a.size(); i++) {
            Point2D.Double p1 = a.get(i);
            Point2D.Double p2 = a.get(i + 1);
            double d1x = p2.x - p1.x;
            double d1y = p2.y - p1.y;
            double len1 = Math.hypot(d1x, d1y);
            if (len1 < 1e-6) {
                continue;
            }
            for (int j = 0; j + 1 < b.size(); j++) {
                Point2D.Double p3 = b.get(j);
                Point2D.Double p4 = b.get(j + 1);
                double d2x = p4.x - p3.x;
                double d2y = p4.y - p3.y;
                double len2 = Math.hypot(d2x, d2y);
                if (len2 < 1e-6) {
                    continue;
                }
                double denom = d1x * d2y - d1y * d2x;
                if (Math.abs(denom) < 1e-6 * len1 * len2) {
                    continue;
                }
                double cross = Math.abs((d1x / len1) * (d2y / len2) - (d1y / len1) * (d2x / len2));
                if (cross < 0.3) {
                    continue;
                }
                double t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denom;
                double u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denom;
                if (t <= 0.05 || t >= 0.95 || u <= 0.05 || u >= 0.95) {
                    continue;
                }
                out.add(new Crossing(i, t));
            }
        }
    }

    private static Path2D buildDisplay(List<Point2D.Double> polyline, List<Crossing> crossings) {
        Map<Integer, List<Double>> bySegment = new HashMap<>();
        for (Crossing crossing : crossings) {
            bySegment.computeIfAbsent(crossing.segment(), key -> new ArrayList<>()).add(crossing.t());
        }
        bySegment.values().forEach(values -> values.sort(Double::compare));

        Path2D path = new Path2D.Double();
        if (polyline.isEmpty()) {
            return path;
        }
        path.moveTo(polyline.get(0).x, polyline.get(0).y);

        double travelled = 0;
        double lastHopAlong = Double.NEGATIVE_INFINITY;
        for (int i = 0; i + 1 < polyline.size(); i++) {
            Point2D.Double a = polyline.get(i);
            Point2D.Double b = polyline.get(i + 1);
            double segmentLength = a.distance(b);
            if (segmentLength < 1e-6) {
                continue;
            }
            List<Double> ts = bySegment.get(i);
            if (ts != null) {
                for (double t : ts) {
                    double along = travelled + t * segmentLength;
                    if (along - lastHopAlong < HOP_RADIUS * 2.2) {
                        continue;
                    }
                    if (t * segmentLength < HOP_RADIUS * 1.5
                            || (1 - t) * segmentLength < HOP_RADIUS * 1.5) {
                        continue;
                    }
                    double x = a.x + (b.x - a.x) * t;
                    double y = a.y + (b.y - a.y) * t;
                    double ux = (b.x - a.x) / segmentLength;
                    double uy = (b.y - a.y) / segmentLength;
                    double beforeX = x - ux * HOP_RADIUS;
                    double beforeY = y - uy * HOP_RADIUS;
                    double afterX = x + ux * HOP_RADIUS;
                    double afterY = y + uy * HOP_RADIUS;
                    double px = uy;
                    double py = -ux;
                    double k = HOP_RADIUS * 1.34;

                    path.lineTo(beforeX, beforeY);
                    path.curveTo(beforeX + px * k, beforeY + py * k,
                            afterX + px * k, afterY + py * k,
                            afterX, afterY);
                    lastHopAlong = along;
                }
            }
            path.lineTo(b.x, b.y);
            travelled += segmentLength;
        }
        return path;
    }

    private static Path2D polylinePath(List<Point2D.Double> polyline) {
        Path2D path = new Path2D.Double();
        if (polyline.isEmpty()) {
            return path;
        }
        path.moveTo(polyline.get(0).x, polyline.get(0).y);
        for (int i = 1; i < polyline.size(); i++) {
            path.lineTo(polyline.get(i).x, polyline.get(i).y);
        }
        return path;
    }

    private record Crossing(int segment, double t) {
    }

    private record WirePath(Path2D display, int hops, int lane) {
    }
}
