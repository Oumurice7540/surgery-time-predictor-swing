package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.EpochRmsePoint;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Locale;

final class RmseChartPanel extends JPanel {
    private final List<EpochRmsePoint> points;

    RmseChartPanel(List<EpochRmsePoint> points) {
        this.points = List.copyOf(points);
        setOpaque(true);
        setBackground(new Color(247, 250, 252));
        setPreferredSize(new Dimension(430, 260));
        setMinimumSize(new Dimension(320, 230));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        setAlignmentX(LEFT_ALIGNMENT);
        setToolTipText("模擬 RMSE 從 58.4 分鐘逐步下降至 22.8 分鐘");
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var copy = (Graphics2D) graphics.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setFont(UiTheme.FONT.deriveFont(10f));

        int left = 54;
        int right = 20;
        int top = 20;
        int bottom = 42;
        int width = Math.max(1, getWidth() - left - right);
        int height = Math.max(1, getHeight() - top - bottom);
        double minY = points.stream().mapToDouble(EpochRmsePoint::rmseMinutes).min().orElse(0) - 3;
        double maxY = points.stream().mapToDouble(EpochRmsePoint::rmseMinutes).max().orElse(1) + 2;
        int maxEpoch = points.getLast().epoch();

        copy.setColor(new Color(222, 232, 241));
        copy.setStroke(new BasicStroke(1f));
        for (int tick = 0; tick <= 5; tick++) {
            int y = top + height * tick / 5;
            copy.drawLine(left, y, left + width, y);
            double value = maxY - (maxY - minY) * tick / 5;
            drawRightAligned(copy, String.format(Locale.TAIWAN, "%.0f", value), left - 8, y + 4);
        }
        for (int tick = 0; tick <= 5; tick++) {
            int x = left + width * tick / 5;
            copy.drawLine(x, top, x, top + height);
            String label = String.format(Locale.TAIWAN, "%,d", maxEpoch * tick / 5);
            FontMetrics metrics = copy.getFontMetrics();
            copy.setColor(UiTheme.MUTED);
            copy.drawString(label, x - metrics.stringWidth(label) / 2, top + height + 19);
            copy.setColor(new Color(222, 232, 241));
        }

        copy.setColor(UiTheme.TEAL);
        copy.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int previousX = -1;
        int previousY = -1;
        for (var point : points) {
            int x = left + (int) Math.round(width * point.epoch() / (double) maxEpoch);
            int y = top + (int) Math.round(height * (maxY - point.rmseMinutes()) / (maxY - minY));
            if (previousX >= 0) {
                copy.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }
        copy.setColor(UiTheme.MUTED);
        copy.drawString("Epoch", left + width / 2 - 18, getHeight() - 8);
        copy.dispose();
    }

    private void drawRightAligned(Graphics2D graphics, String text, int rightX, int baseline) {
        graphics.setColor(UiTheme.MUTED);
        graphics.drawString(text, rightX - graphics.getFontMetrics().stringWidth(text), baseline);
    }
}
