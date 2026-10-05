package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.EpochRmsePoint;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Locale;

/**
 * 使用 Java 2D 自行繪製 RMSE 收斂趨勢的折線圖。
 *
 * <p>本專案沒有引入外部圖表套件。每次 Swing 要求重畫時，本類別會把 Epoch
 * 映射到 X 軸、RMSE 映射到 Y 軸，再依序連成折線。</p>
 */
final class RmseChartPanel extends JPanel {
    /** 圖表使用的不可變資料點清單。 */
    private final List<EpochRmsePoint> points;
    private final String axisLabel;

    /**
     * @param points 依 Epoch 由小到大排列的 RMSE 資料點
     */
    RmseChartPanel(List<EpochRmsePoint> points) { this(points, "Epoch"); }
    RmseChartPanel(List<EpochRmsePoint> points, String axisLabel) {
        this.axisLabel = axisLabel;
        // 建立副本，避免外部在圖表繪製期間修改清單。
        this.points = List.copyOf(points);
        setOpaque(true);
        setBackground(new Color(247, 250, 252));
        setPreferredSize(new Dimension(430, 260));
        setMinimumSize(new Dimension(320, 230));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        setAlignmentX(LEFT_ALIGNMENT);
        setToolTipText(String.format(Locale.TAIWAN,
                "測試 RMSE 從 %.1f 分鐘變為 %.1f 分鐘",
                points.getFirst().rmseMinutes(), points.getLast().rmseMinutes()));
    }

    /** 根據目前元件尺寸重新計算座標並繪製格線、標籤與折線。 */
    @Override
    protected void paintComponent(Graphics graphics) {
        // 先讓 JPanel 清除舊畫面及繪製背景。
        super.paintComponent(graphics);
        var copy = (Graphics2D) graphics.create();
        // 抗鋸齒讓折線與文字邊緣更平滑。
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setFont(UiTheme.font(Font.PLAIN, 10f));

        // 四個數值是圖表內距，左側較寬是為了放 Y 軸數字。
        int left = 54;
        int right = 20;
        int top = 20;
        int bottom = 42;
        // 至少保留 1 像素，避免視窗極小時發生除以零或負尺寸。
        int width = Math.max(1, getWidth() - left - right);
        int height = Math.max(1, getHeight() - top - bottom);
        // 找出資料最小與最大 RMSE，並在上下多留空間，折線不會貼住邊界。
        double minY = points.stream().mapToDouble(EpochRmsePoint::rmseMinutes).min().orElse(0) - 3;
        double maxY = points.stream().mapToDouble(EpochRmsePoint::rmseMinutes).max().orElse(1) + 2;
        int maxEpoch = points.getLast().epoch();

        // 第一個迴圈繪製六條水平格線與對應的 RMSE 數值。
        copy.setColor(new Color(222, 232, 241));
        copy.setStroke(new BasicStroke(1f));
        // 第二個迴圈繪製六條垂直格線與 Epoch 標籤。
        for (int tick = 0; tick <= 5; tick++) {
            int y = top + height * tick / 5;
            copy.drawLine(left, y, left + width, y);
            double value = maxY - (maxY - minY) * tick / 5;
            drawRightAligned(copy, String.format(Locale.TAIWAN, "%.0f", value), left - 8, y + 4);
        }
        for (int tick = 0; tick <= 5; tick++) {
            int x = left + width * tick / 5;
            copy.drawLine(x, top, x, top + height);
            String label = maxEpoch == 1 ? (tick == 0 ? "0" : tick == 5 ? "1" : "") : String.format(Locale.TAIWAN, "%,d", maxEpoch * tick / 5);
            FontMetrics metrics = copy.getFontMetrics();
            copy.setColor(UiTheme.MUTED);
            copy.drawString(label, x - metrics.stringWidth(label) / 2, top + height + 19);
            copy.setColor(new Color(222, 232, 241));
        }

        // 把各資料點的數值比例換算成面板中的像素位置，再逐點連線。
        copy.setColor(UiTheme.TEAL);
        copy.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int previousX = -1;
        int previousY = -1;
        for (var point : points) {
            int x = left + (int) Math.round(width * point.epoch() / (double) maxEpoch);
            int y = top + (int) Math.round(height * (maxY - point.rmseMinutes()) / (maxY - minY));
            // 第一個點沒有前一點；從第二點開始才畫連接線。
            if (previousX >= 0) {
                copy.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
        }
        copy.setColor(UiTheme.MUTED);
        copy.drawString(axisLabel, left + width / 2 - copy.getFontMetrics().stringWidth(axisLabel) / 2, getHeight() - 8);
        copy.dispose();
    }

    /**
     * 依文字寬度計算起點，讓所有 Y 軸數字以指定的右側位置對齊。
     */
    private void drawRightAligned(Graphics2D graphics, String text, int rightX, int baseline) {
        graphics.setColor(UiTheme.MUTED);
        graphics.drawString(text, rightX - graphics.getFontMetrics().stringWidth(text), baseline);
    }
}
