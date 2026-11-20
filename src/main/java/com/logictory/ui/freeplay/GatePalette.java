package com.logictory.ui.freeplay;

import com.logictory.game.GateType;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Left-hand palette; dragging an item drops a new gate onto the board. */
public final class GatePalette extends JPanel {
    private static final Color PANEL_BG = new Color(0x202127);
    private static final Color ITEM_BG = new Color(0x2a2c34);

    public GatePalette(CircuitCanvas canvas) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(PANEL_BG);
        setBorder(new EmptyBorder(14, 12, 14, 12));

        JLabel title = new JLabel("GATES");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(GatePainter.TEXT_DIM);
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(10));

        for (GateType type : GateType.values()) {
            add(new PaletteItem(type, canvas));
            add(Box.createVerticalStrut(6));
        }
    }

    private static final class PaletteItem extends JComponent {
        private static final int ITEM_HEIGHT = 46;

        private final GateType type;
        private final CircuitCanvas canvas;

        PaletteItem(GateType type, CircuitCanvas canvas) {
            this.type = type;
            this.canvas = canvas;
            setAlignmentX(LEFT_ALIGNMENT);
            setPreferredSize(new Dimension(148, ITEM_HEIGHT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Drag " + type.displayName() + " onto the board");

            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    canvas.beginPaletteDrag(type, toCanvas(e));
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    canvas.updatePaletteDrag(toCanvas(e));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    canvas.endPaletteDrag(toCanvas(e));
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        private Point toCanvas(MouseEvent e) {
            return SwingUtilities.convertPoint(this, e.getPoint(), canvas);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(ITEM_BG);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            GatePainter.paintIcon(g2, type, 8, (getHeight() - 28) / 2, 46, 28);

            g2.setColor(GatePainter.TEXT);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.drawString(type.displayName(), 64, getHeight() / 2 + 4);

            g2.dispose();
        }
    }
}
