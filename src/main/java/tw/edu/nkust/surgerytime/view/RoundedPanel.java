package tw.edu.nkust.surgerytime.view;

import javax.swing.JPanel;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

class RoundedPanel extends JPanel {
    private final int radius;
    private Color fill;
    private Color outline;

    RoundedPanel(Color fill, int radius) {
        this(fill, UiTheme.BORDER, radius);
    }

    RoundedPanel(Color fill, Color outline, int radius) {
        this.fill = fill;
        this.outline = outline;
        this.radius = radius;
        setOpaque(false);
    }

    void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    void setOutline(Color outline) {
        this.outline = outline;
        repaint();
    }

    @Override
    protected void addImpl(Component component, Object constraints, int index) {
        if (getLayout() instanceof BoxLayout && component instanceof JComponent swingComponent) {
            swingComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        super.addImpl(component, constraints, index);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        var copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(fill);
        copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        if (outline != null) {
            copy.setColor(outline);
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
        copy.dispose();
        super.paintComponent(graphics);
    }
}
