package tw.edu.nkust.surgerytime.view;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * 新增資料流程頁面共用的基本骨架。
 * 它只處理一致的頁面留白、標題、步驟標籤、白色卡片與垂直捲動。
 */
abstract class WorkflowPagePanel extends JPanel {
    protected final ScrollablePanel content = new ScrollablePanel();
    private final JScrollPane scrollPane;

    protected WorkflowPagePanel(String title, String stepText) {
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(UiTheme.BACKGROUND);
        content.setBorder(UiTheme.padding(8, 8, 8, 8));


        scrollPane = ViewSupport.scroll(content);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void scrollToTop() {
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    /** 建立與既有三頁相同的頁面標題。 */
    private JComponent heading(String title, String stepText) {
        var line = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.add(UiTheme.label(title, 29f, Font.BOLD, UiTheme.INK));
        var step = UiTheme.label(stepText, 11f, Font.BOLD, UiTheme.TEAL_DARK);
        step.setOpaque(true);
        step.setBackground(UiTheme.TEAL_PALE);
        step.setBorder(UiTheme.padding(5, 10, 5, 10));
        line.add(step);
        ViewSupport.fullWidth(line);
        return line;
    }

    /** 建立資料流程頁共用的白色圓角卡片。 */
    protected RoundedPanel card() {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(10, 10, 10, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }
}
