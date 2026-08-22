package tw.edu.nkust.surgerytime.view;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class GradientPanel extends JPanel {
    private final Color start;
    private final Color end;

    GradientPanel(Color start, Color end) {
        this.start = start;
        this.end = end;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        var copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        copy.setPaint(new GradientPaint(0, 0, start, getWidth(), 0, end));
        copy.fillRect(0, 0, getWidth(), getHeight());
        copy.dispose();
        super.paintComponent(graphics);
    }
}
