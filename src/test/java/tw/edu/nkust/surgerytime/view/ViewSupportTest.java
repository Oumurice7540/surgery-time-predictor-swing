package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 驗證巢狀表格取得焦點後，滑鼠滾輪仍能控制整頁捲軸。 */
class ViewSupportTest {
    @Test
    void forwardsTableMouseWheelToOuterPageScroll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // 建立一個高度大於可視區的假頁面，確保外層捲軸真的有移動空間。
            var longPage = new JPanel();
            longPage.setPreferredSize(new Dimension(300, 1_200));
            var pageScroll = new JScrollPane(longPage);
            pageScroll.setSize(300, 200);
            pageScroll.doLayout();
            pageScroll.getVerticalScrollBar().setValue(100);

            var table = new JTable(2, 2);
            var tableScroll = new JScrollPane(table);
            tableScroll.setSize(300, 200);
            tableScroll.doLayout();
            ViewSupport.routeNestedMouseWheel(table, tableScroll, pageScroll);
            int before = pageScroll.getVerticalScrollBar().getValue();

            // 模擬游標位於表格上並向下滾動一格。
            var wheel = new MouseWheelEvent(
                    table,
                    MouseEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(),
                    0,
                    10,
                    10,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    3,
                    1
            );
            table.dispatchEvent(wheel);

            assertTrue(pageScroll.getVerticalScrollBar().getValue() > before,
                    "表格收到向下滾輪事件時，外層頁面捲軸也應向下移動");
        });
    }

    @Test
    void scrollsCompleteTableBeforeContinuingWithOuterPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var longPage = new JPanel();
            longPage.setPreferredSize(new Dimension(300, 1_200));
            var pageScroll = new JScrollPane(longPage);
            pageScroll.setSize(300, 200);
            pageScroll.doLayout();
            pageScroll.getVerticalScrollBar().setValue(100);

            // 100 列比 200 像素的可視區高，因此內層表格確實有捲動空間。
            var table = new JTable(100, 2);
            var tableScroll = new JScrollPane(table);
            tableScroll.setSize(300, 200);
            tableScroll.doLayout();
            ViewSupport.routeNestedMouseWheel(table, tableScroll, pageScroll);
            int outerBefore = pageScroll.getVerticalScrollBar().getValue();

            table.dispatchEvent(wheelDown(table));
            assertTrue(tableScroll.getVerticalScrollBar().getValue() > 0,
                    "完整表格仍有資料時應先捲動表格");
            assertEquals(outerBefore, pageScroll.getVerticalScrollBar().getValue(),
                    "表格尚未到底時不應移動整頁");

            var innerBar = tableScroll.getVerticalScrollBar();
            innerBar.setValue(innerBar.getMaximum() - innerBar.getModel().getExtent());
            table.dispatchEvent(wheelDown(table));
            assertTrue(pageScroll.getVerticalScrollBar().getValue() > outerBefore,
                    "表格到底後，向下滾輪應接續移動整頁");
        });
    }

    /** 建立一個向下滾動一格的滑鼠事件，供兩種巢狀捲動情境共用。 */
    private static MouseWheelEvent wheelDown(JTable table) {
        return new MouseWheelEvent(
                table,
                MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),
                0,
                10,
                10,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                3,
                1
        );
    }
}
