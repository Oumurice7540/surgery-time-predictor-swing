package tw.edu.nkust.surgerytime.view;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;

/**
 * Swing 畫面的共用小工具集合。
 *
 * <p>三個頁面經常需要相同的垂直面板、換行文字、分隔線與捲動容器。
 * 集中在這裡可以減少重複程式碼，並讓各頁面專注在自身內容。</p>
 */
final class ViewSupport {
    /** 只有靜態工具方法，不允許建立物件。 */
    private ViewSupport() {
    }

    /**
     * 建立透明並使用 BoxLayout 的面板。
     *
     * @param axis {@link BoxLayout#X_AXIS} 或 {@link BoxLayout#Y_AXIS}
     */
    static JPanel box(int axis) {
        var panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, axis));
        return panel;
    }

    /**
     * 建立看起來像一般標籤、但可以依單字自動換行的唯讀 JTextArea。
     * JLabel 不方便自動換行，所以說明段落使用這個方法。
     */
    static JTextArea wrappedText(String text, float fontSize, Color color) {
        var area = new JTextArea(text);
        area.setFont(UiTheme.font(java.awt.Font.PLAIN, fontSize));
        area.setForeground(color);
        area.setOpaque(false);
        // 唯讀且不可取得鍵盤焦點，使用者不會誤以為這是輸入框。
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setPreferredSize(new Dimension(420, 36));
        area.setMinimumSize(new Dimension(0, 36));
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        return area;
    }

    /** 建立可水平撐滿容器的一像素分隔線。 */
    static JSeparator separator() {
        var separator = new JSeparator();
        separator.setForeground(UiTheme.BORDER);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        return separator;
    }

    /** 建立已套用背景、無外框及適當滾輪速度的 JScrollPane。 */
    static JScrollPane scroll(Component content) {
        var scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /**
     * 在表格與整頁兩層捲動容器之間分配滑鼠滾輪動作。
     *
     * <p>JTable 通常會放在自己的 JScrollPane 裡。如果整個頁面外面還有另一個
     * JScrollPane，使用者點過表格後，內層捲動容器可能攔截滾輪事件；當表格沒有
     * 更多資料可捲時，內外兩層都不會移動。這個方法會先捲動仍有資料的內層表格；
     * 到達表格頂端或底端後，再把同方向滾輪動作交給外層頁面。</p>
     *
     * @param wheelSource 接收滑鼠滾輪事件的內層元件，例如 JTable
     * @param innerScroll 表格自己的 JScrollPane
     * @param outerScroll 包住整個頁面的 JScrollPane
     */
    static void routeNestedMouseWheel(
            Component wheelSource,
            JScrollPane innerScroll,
            JScrollPane outerScroll
    ) {
        wheelSource.addMouseWheelListener(event -> {
            int direction = Integer.compare(event.getWheelRotation(), 0);
            if (direction == 0) {
                return;
            }

            JScrollBar innerBar = innerScroll.getVerticalScrollBar();
            int innerMaximumValue = innerBar.getMaximum() - innerBar.getModel().getExtent();
            boolean innerCanMove = direction < 0
                    ? innerBar.getValue() > innerBar.getMinimum()
                    : innerBar.getValue() < innerMaximumValue;
            JScrollBar targetBar = innerCanMove
                    ? innerBar
                    : outerScroll.getVerticalScrollBar();

            // 一般滑鼠一次刻度通常包含三個單位；觸控板可提供更精細的小數旋轉量。
            double rotation = event.getPreciseWheelRotation();
            int increment = event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL
                    ? targetBar.getBlockIncrement(direction)
                    : targetBar.getUnitIncrement(direction) * event.getScrollAmount();
            int delta = (int) Math.round(rotation * increment);
            // 極小的觸控板動作四捨五入後可能為 0，至少移動一個像素才有操作回饋。
            if (delta == 0) {
                delta = direction;
            }
            targetBar.setValue(targetBar.getValue() + delta);
            // 已直接移動正確捲軸，避免 Swing 對同一事件重複捲動。
            event.consume();
        });
    }

    /**
     * 讓元件在 BoxLayout 中可以水平延伸到容器最大寬度，同時保留原本偏好高度。
     */
    static void fullWidth(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** 建立各卡片上方共用的小型青綠色分類標籤。 */
    static JLabel sectionLabel(String text) {
        return UiTheme.label(text, 11f, java.awt.Font.BOLD, UiTheme.TEAL_DARK);
    }
}
