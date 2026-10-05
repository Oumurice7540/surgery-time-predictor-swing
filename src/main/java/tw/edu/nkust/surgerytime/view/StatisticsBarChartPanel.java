package tw.edu.nkust.surgerytime.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/** 簡單的 Swing 橫向長條圖，用來呈現各群組平均手術時間。 */
final class StatisticsBarChartPanel extends javax.swing.JPanel {
    private List<String> labels = List.of();
    private List<Double> values = List.of();

    StatisticsBarChartPanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(700, 380));
    }

    void setData(List<String> labels, List<Double> values) {
        this.labels = List.copyOf(labels);
        this.values = List.copyOf(values);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(UiTheme.font(java.awt.Font.PLAIN, 10f));
            if (labels.isEmpty()) {
                g.setColor(UiTheme.MUTED);
                g.drawString("尚無可顯示資料", 24, 36);
                return;
            }
            double maximum = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            int labelWidth = Math.min(230, Math.max(110,
                    labels.stream().mapToInt(label -> g.getFontMetrics().stringWidth(label)).max().orElse(110) + 12));
            int rowHeight = Math.max(31, (getHeight() - 28) / labels.size());
            int barStart = labelWidth + 16;
            int barWidth = Math.max(80, getWidth() - barStart - 74);
            FontMetrics metrics = g.getFontMetrics();
            for (int index = 0; index < labels.size(); index++) {
                int y = 16 + index * rowHeight;
                String label = ellipsize(labels.get(index), metrics, labelWidth - 12);
                g.setColor(UiTheme.TEXT);
                g.drawString(label, 6, y + 18);
                g.setColor(new Color(232, 238, 244));
                g.fillRoundRect(barStart, y + 5, barWidth, 15, 8, 8);
                int valueWidth = (int) Math.round(barWidth * values.get(index) / Math.max(1.0, maximum));
                g.setColor(UiTheme.TEAL);
                g.fillRoundRect(barStart, y + 5, valueWidth, 15, 8, 8);
                g.setColor(UiTheme.INK);
                g.drawString(String.format("%.1f 分", values.get(index)), barStart + barWidth + 9, y + 18);
            }
        } finally {
            g.dispose();
        }
    }

    private String ellipsize(String text, FontMetrics metrics, int maximumWidth) {
        if (metrics.stringWidth(text) <= maximumWidth) return text;
        String suffix = "…";
        int end = text.length();
        while (end > 1 && metrics.stringWidth(text.substring(0, end) + suffix) > maximumWidth) end--;
        return text.substring(0, end) + suffix;
    }
}
