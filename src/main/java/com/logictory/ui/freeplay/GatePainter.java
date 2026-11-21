package com.logictory.ui.freeplay;

import com.logictory.game.Gate;
import com.logictory.game.GateType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;

/** Java2D rendering for gates, ports and the palette icons. No image assets required. */
public final class GatePainter {
    public static final Color BG = new Color(0x1b1c21);
    public static final Color GRID_MINOR = new Color(0x24252c);
    public static final Color GRID_MAJOR = new Color(0x32343d);
    public static final Color WIRE_ON = new Color(0x5fd07a);
    public static final Color WIRE_OFF = new Color(0x6b7180);
    public static final Color ACCENT = new Color(0xffb454);
    public static final Color TEXT = new Color(0xe8eaf0);
    public static final Color TEXT_DIM = new Color(0x9aa0b0);
    public static final Color BORDER = new Color(0x4a4f61);
    public static final Color PORT = new Color(0x14151a);
    public static final Color PORT_RING = new Color(0x8a90a3);

    private static final Color LOGIC_BODY = new Color(0x2c2f3a);
    private static final Color INPUT_BODY = new Color(0x27352c);
    private static final Color INPUT_BORDER = new Color(0x4c7a56);
    private static final Color OUTPUT_BODY = new Color(0x352c27);
    private static final Color OUTPUT_BORDER = new Color(0x7a5a4c);
    private static final Color LED_OFF = new Color(0x4b4f5c);
    private static final Color SHADOW = new Color(0, 0, 0, 90);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 15);
    private static final Font ICON_FONT = new Font("SansSerif", Font.BOLD, 11);

    private GatePainter() {
    }

    public static void paintGate(Graphics2D g2, Gate gate, boolean selected, double zoom) {
        int x = gate.x();
        int y = gate.y();
        int w = Gate.WIDTH;
        int h = Gate.HEIGHT;

        g2.setColor(SHADOW);
        g2.fillRoundRect(x + 3, y + 4, w, h, 14, 14);

        g2.setColor(bodyColor(gate.type()));
        g2.fillRoundRect(x, y, w, h, 14, 14);

        g2.setStroke(new BasicStroke((float) ((selected ? 2.6 : 1.4) / zoom)));
        g2.setColor(selected ? ACCENT : borderColor(gate.type()));
        g2.drawRoundRect(x, y, w, h, 14, 14);

        drawLabel(g2, gate.type(), x, y);

        if (gate.type().invertedOutput()) {
            int bx = x + w - 16;
            int by = y + h / 2 - 5;
            g2.setColor(bodyColor(gate.type()));
            g2.fillOval(bx, by, 10, 10);
            g2.setColor(borderColor(gate.type()));
            g2.setStroke(new BasicStroke((float) (1.2 / zoom)));
            g2.drawOval(bx, by, 10, 10);
        }

        g2.setColor(gate.output() ? WIRE_ON : LED_OFF);
        g2.fillOval(x + w - 15, y + h - 15, 8, 8);
    }

    public static void paintPorts(Graphics2D g2, Gate gate, double zoom, int hoverInput, boolean hoverOutput) {
        g2.setStroke(new BasicStroke((float) (1.2 / zoom)));
        for (int i = 0; i < gate.type().inputCount(); i++) {
            Point p = gate.inputPort(i);
            drawPort(g2, p, i == hoverInput);
        }
        if (gate.type().outputCount() > 0) {
            drawPort(g2, gate.outputPort(), hoverOutput);
        }
    }

    private static void drawPort(Graphics2D g2, Point p, boolean hovered) {
        int r = hovered ? 6 : 4;
        g2.setColor(PORT);
        g2.fillOval(p.x - r, p.y - r, r * 2, r * 2);
        g2.setColor(hovered ? ACCENT : PORT_RING);
        g2.drawOval(p.x - r, p.y - r, r * 2, r * 2);
    }

    public static void paintGhost(Graphics2D g2, GateType type, int x, int y, double zoom) {
        g2.setColor(bodyColor(type));
        g2.fillRoundRect(x, y, Gate.WIDTH, Gate.HEIGHT, 14, 14);
        g2.setColor(ACCENT);
        g2.setStroke(new BasicStroke((float) (1.8 / zoom), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{(float) (6 / zoom), (float) (5 / zoom)}, 0f));
        g2.drawRoundRect(x, y, Gate.WIDTH, Gate.HEIGHT, 14, 14);
        drawLabel(g2, type, x, y);
    }

    public static void paintIcon(Graphics2D g2, GateType type, int x, int y, int w, int h) {
        g2.setColor(bodyColor(type));
        g2.fillRoundRect(x, y, w, h, 10, 10);
        g2.setColor(borderColor(type));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(x, y, w, h, 10, 10);

        g2.setColor(TEXT);
        g2.setFont(ICON_FONT);
        FontMetrics fm = g2.getFontMetrics();
        String label = type.label();
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2,
                y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    private static void drawLabel(Graphics2D g2, GateType type, int x, int y) {
        g2.setColor(TEXT);
        g2.setFont(LABEL_FONT);
        FontMetrics fm = g2.getFontMetrics();
        String label = type.label();
        int tx = x + (Gate.WIDTH - fm.stringWidth(label)) / 2;
        int ty = y + (Gate.HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(label, tx, ty);
    }

    private static Color bodyColor(GateType type) {
        return switch (type) {
            case INPUT -> INPUT_BODY;
            case OUTPUT -> OUTPUT_BODY;
            default -> LOGIC_BODY;
        };
    }

    private static Color borderColor(GateType type) {
        return switch (type) {
            case INPUT -> INPUT_BORDER;
            case OUTPUT -> OUTPUT_BORDER;
            default -> BORDER;
        };
    }
}
