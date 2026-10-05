package tw.edu.nkust.surgerytime.view;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * 專門放在 {@link javax.swing.JScrollPane} 中的頁面內容面板。
 *
 * <p>實作 {@link Scrollable} 後，可以控制每次滾輪移動距離，以及內容是否跟著
 * 視窗寬高伸縮。本專案讓寬度跟著視窗，高度則依內容決定，內容太長時出現垂直捲軸。</p>
 */
final class ScrollablePanel extends JPanel implements Scrollable {
    /** 告訴 JScrollPane，初始可視區大小以面板的 preferredSize 為準。 */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    /** 每次小幅捲動（例如滑鼠滾輪一格）移動 24 像素。 */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    /**
     * 每次大幅捲動（例如 Page Down）接近一個可視頁面高度，
     * 至少移動 80 像素並保留 60 像素上下文。
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(80, visibleRect.height - 60);
    }

    /** 回傳 true，讓內容寬度跟隨 viewport，避免不必要的水平捲軸。 */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /** 回傳 false，讓內容維持自身高度；超出可視區時才能垂直捲動。 */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof javax.swing.JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }
}
