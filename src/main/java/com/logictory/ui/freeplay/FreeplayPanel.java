package com.logictory.ui.freeplay;

import com.logictory.game.Circuit;
import com.logictory.game.Gate;
import com.logictory.game.GateType;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/** Freeplay board embedded in the main window: palette on the left, board in the centre. */
public final class FreeplayPanel extends JPanel {
    private final Circuit circuit = new Circuit();
    private final CircuitCanvas canvas = new CircuitCanvas(circuit);
    private final JLabel statusLabel = new JLabel(" ");
    private final Runnable onBack;

    public FreeplayPanel(Runnable onBack) {
        this.onBack = onBack;
        setLayout(new BorderLayout());

        GatePalette palette = new GatePalette(canvas);
        JScrollPane paletteScroll = new JScrollPane(palette);
        paletteScroll.setBorder(null);
        paletteScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        paletteScroll.getViewport().setBackground(palette.getBackground());
        paletteScroll.getVerticalScrollBar().setUnitIncrement(16);

        add(buildToolbar(), BorderLayout.NORTH);
        add(paletteScroll, BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        canvas.setStatusSink(statusLabel::setText);
        seedDemo();
        canvas.refreshSimulation();
    }

    public void fitView() {
        canvas.fitView();
    }

    private JComponent buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton back = new JButton("← Menu");
        back.addActionListener(e -> onBack.run());
        JLabel title = new JLabel("FREEPLAY SANDBOX");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        left.add(back);
        left.add(title);

        JLabel hint = new JLabel("Drag gates from the palette  •  click INPUT to toggle  •  drag output → input to wire");
        hint.setForeground(GatePainter.TEXT_DIM);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JLabel wireLabel = new JLabel("Wire:");
        wireLabel.setForeground(GatePainter.TEXT_DIM);
        wireLabel.setFont(wireLabel.getFont().deriveFont(Font.PLAIN, 12f));
        JComboBox<WireStyle> wireStyle = new JComboBox<>(WireStyle.values());
        wireStyle.setSelectedItem(canvas.wireStyle());
        wireStyle.addActionListener(e -> canvas.setWireStyle((WireStyle) wireStyle.getSelectedItem()));
        right.add(wireLabel);
        right.add(wireStyle);

        JButton fitView = new JButton("Fit View");
        fitView.addActionListener(e -> canvas.fitView());
        right.add(fitView);

        JButton clear = new JButton("Clear Board");
        clear.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this, "Remove all gates and wires?",
                    "Clear Board", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                canvas.clearBoard();
            }
        });
        right.add(clear);

        bar.add(left, BorderLayout.WEST);
        bar.add(hint, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBorder(new EmptyBorder(6, 14, 8, 14));

        statusLabel.setForeground(GatePainter.TEXT_DIM);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));

        JLabel keys = new JLabel("Delete = remove  •  wheel = zoom  •  space/middle drag = pan");
        keys.setForeground(GatePainter.TEXT_DIM);
        keys.setFont(keys.getFont().deriveFont(Font.PLAIN, 11f));

        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(keys, BorderLayout.EAST);
        return bar;
    }

    /** Half-adder example so the board is not empty on first open. */
    private void seedDemo() {
        Gate a = circuit.addGate(GateType.INPUT, 80, 100);
        Gate b = circuit.addGate(GateType.INPUT, 80, 240);
        Gate xor = circuit.addGate(GateType.XOR, 300, 100);
        Gate and = circuit.addGate(GateType.AND, 300, 240);
        Gate sum = circuit.addGate(GateType.OUTPUT, 520, 100);
        Gate carry = circuit.addGate(GateType.OUTPUT, 520, 240);

        circuit.connect(a.id(), xor.id(), 0);
        circuit.connect(b.id(), xor.id(), 1);
        circuit.connect(a.id(), and.id(), 0);
        circuit.connect(b.id(), and.id(), 1);
        circuit.connect(xor.id(), sum.id(), 0);
        circuit.connect(and.id(), carry.id(), 0);
    }
}
