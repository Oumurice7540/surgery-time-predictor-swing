package tw.edu.nkust.surgerytime.view;

import javax.swing.JPanel;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * 可設定填色、外框與圓角大小的卡片面板。
 *
 * <p>Swing 的 JPanel 預設是直角，本類別使用 Graphics2D 自行繪製圓角矩形，
 * 讓訓練指標、提示訊息與預測結果具有一致的卡片外觀。</p>
 */
class RoundedPanel extends JPanel {
    /** 圓角直徑，數值越大代表角落越圓。 */
    private final int radius;
    /** 卡片內部填色。 */
    private Color fill;
    /** 卡片外框色；null 表示不畫外框。 */
    private Color outline;

    /** 建立使用預設邊框顏色的圓角面板。 */
    RoundedPanel(Color fill, int radius) {
        this(fill, UiTheme.BORDER, radius);
    }

    /**
     * @param fill 卡片背景色
     * @param outline 外框色，傳入 null 可取消外框
     * @param radius 圓角大小
     */
    RoundedPanel(Color fill, Color outline, int radius) {
        this.fill = fill;
        this.outline = outline;
        this.radius = 4;
        // 圓角以外的區域必須透明，因此不能使用 JPanel 的不透明矩形背景。
        setOpaque(false);
    }

    /** 動態更換填色並要求 Swing 重新繪製。 */
    void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    /** 動態更換外框色並要求 Swing 重新繪製。 */
    void setOutline(Color outline) {
        this.outline = outline;
        repaint();
    }

    /**
     * 攔截子元件加入流程。當卡片使用 BoxLayout 時，自動讓 Swing 元件靠左，
     * 避免每個呼叫位置都重複設定 alignmentX。
     */
    @Override
    protected void addImpl(Component component, Object constraints, int index) {
        if (getLayout() instanceof BoxLayout && component instanceof JComponent swingComponent) {
            swingComponent.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        super.addImpl(component, constraints, index);
    }

    /** 在 Swing 繪製子元件之前畫出圓角背景與可選外框。 */
    @Override
    protected void paintComponent(Graphics graphics) {
        var copy = (Graphics2D) graphics.create();
        // 抗鋸齒讓圓角邊緣比較平滑。
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(fill);
        copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        if (outline != null) {
            copy.setColor(outline);
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
        copy.dispose();
        // 繼續讓 JPanel 繪製內部的文字、按鈕等子元件。
        super.paintComponent(graphics);
    }
}
