package tw.edu.nkust.surgerytime.view;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 能繪製水平漸層背景的自訂 Swing 面板。
 *
 * <p>一般 {@link JPanel} 只有單色背景。本類別覆寫 {@code paintComponent}，
 * 使用 {@link GradientPaint} 從左側顏色逐漸變成右側顏色，主要用於頁首。</p>
 */
final class GradientPanel extends JPanel {
    /** 漸層左側起始顏色。 */
    private final Color start;
    /** 漸層右側結束顏色。 */
    private final Color end;

    /**
     * @param start 左側顏色
     * @param end 右側顏色
     */
    GradientPanel(Color start, Color end) {
        this.start = start;
        this.end = end;
        // 背景由本類別自行繪製，因此告訴 Swing 不要先填上 JPanel 的預設背景。
        setOpaque(false);
    }

    /** 每次顯示或調整尺寸時，依照面板當下寬度重新繪製漸層。 */
    @Override
    protected void paintComponent(Graphics graphics) {
        // create() 產生繪圖環境副本，避免修改 Swing 傳入的原始 Graphics 狀態。
        var copy = (Graphics2D) graphics.create();
        // 優先使用較高品質的繪製設定。
        copy.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // 從 x=0 的 start 色，水平漸變到面板最右側的 end 色。
        copy.setPaint(new GradientPaint(0, 0, start, getWidth(), 0, end));
        copy.fillRect(0, 0, getWidth(), getHeight());
        // 釋放剛才建立的 Graphics2D 副本。
        copy.dispose();
        // 讓 JPanel 繼續繪製其中的子元件，例如標題與按鈕。
        super.paintComponent(graphics);
    }
}
