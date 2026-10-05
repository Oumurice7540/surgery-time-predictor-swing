package tw.edu.nkust.surgerytime.view;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;

/**
 * 集中管理整個 Swing 應用程式的顏色、字型與共用元件樣式。
 *
 * <p>如果每個頁面各自寫顏色與字型，日後很難維持一致。此類別相當於桌面程式的
 * 設計規範：畫面只引用有意義的名稱，例如 {@code TEAL} 或 {@code BACKGROUND}。</p>
 */
final class UiTheme {
    // 以下色彩常數依用途命名。Color 的三個整數分別是紅、綠、藍（RGB，0 到 255）。
    /** 最深的頁首藍色。 */
    static final Color NAVY = new Color(11, 37, 68);
    /** 頁首漸層右側的較亮藍色。 */
    static final Color NAVY_LIGHT = new Color(18, 57, 93);
    /** 大標題使用的深色。 */
    static final Color INK = new Color(18, 43, 73);
    /** 一般內文顏色。 */
    static final Color TEXT = new Color(46, 65, 88);
    /** 次要說明文字顏色。 */
    static final Color MUTED = new Color(105, 126, 148);
    /** 主要操作與成功狀態使用的青綠色。 */
    static final Color TEAL = new Color(73, 94, 171);
    /** 青綠色文字的深色版本。 */
    static final Color TEAL_DARK = new Color(54, 73, 143);
    /** 青綠色提示區的淡色背景。 */
    static final Color TEAL_PALE = new Color(231, 235, 251);
    /** 指標卡的藍色重點。 */
    static final Color BLUE = new Color(42, 145, 200);
    /** 指標卡的紫色重點。 */
    static final Color PURPLE = new Color(105, 88, 198);
    /** 指標卡的琥珀色重點。 */
    static final Color AMBER = new Color(226, 163, 58);
    /** 頁面整體淡灰藍背景。 */
    static final Color BACKGROUND = new Color(241, 242, 249);
    /** 卡片表面顏色。 */
    static final Color SURFACE = Color.WHITE;
    /** 卡片、表格與輸入框的邊界顏色。 */
    static final Color BORDER = new Color(220, 229, 239);
    /** 輸入元件背景。 */
    static final Color INPUT = new Color(247, 249, 252);
    /** 警告訊息背景。 */
    static final Color WARNING = new Color(255, 248, 232);
    /** 預測結果卡背景。 */
    static final Color RESULT = new Color(15, 70, 96);

    /** 全站 Swing 原生元件使用的中文字型；16 pt 比原本 14 pt 更容易閱讀。 */
    static final Font FONT = new Font("Microsoft JhengHei UI", Font.PLAIN, 14);
    /**
     * 畫面自行指定的所有字級統一放大 15%。集中設定可確保三個頁面的比例一致，
     * 不必在每一個 JLabel 上逐一猜測新的數值。
     */
    private static final float TEXT_SCALE = 1.0f;

    /** 主題工具類別不需要建立物件。 */
    private UiTheme() {
    }

    /**
     * 透過 UIManager 設定常見 Swing 元件的全域預設值。
     * 必須在建立畫面元件之前呼叫，之後建立的元件才會採用這些字型與尺寸。
     */
    static void install() {
        UIManager.put("Label.font", FONT);
        UIManager.put("Button.font", FONT.deriveFont(Font.BOLD));
        UIManager.put("ToggleButton.font", FONT.deriveFont(Font.BOLD));
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("Spinner.font", FONT);
        UIManager.put("TextField.font", FONT);
        UIManager.put("FormattedTextField.font", FONT);
        UIManager.put("TextArea.font", FONT);
        UIManager.put("List.font", FONT);
        UIManager.put("Table.font", FONT.deriveFont(14f));
        UIManager.put("TableHeader.font", FONT.deriveFont(Font.BOLD, 14f));
        UIManager.put("OptionPane.messageFont", FONT);
        UIManager.put("OptionPane.buttonFont", FONT.deriveFont(Font.BOLD));
        UIManager.put("ToolTip.font", FONT.deriveFont(14f));
        UIManager.put("ScrollBar.width", 14);
    }

    /**
     * 建立已套用專案字型、大小、粗細與顏色的 JLabel。
     *
     * @param text 要顯示的文字
     * @param size 字型大小
     * @param style {@link Font#PLAIN} 或 {@link Font#BOLD}
     * @param color 文字顏色
     */
    static JLabel label(String text, float size, int style, Color color) {
        var label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * 建立套用全域放大倍率的字型，供 JLabel 以外需要自行設定字級的元件共用。
     *
     * @param style {@link Font#PLAIN} 或 {@link Font#BOLD}
     * @param originalSize 放大前的設計字級
     */
    static Font font(int style, float originalSize) {
        return FONT.deriveFont(style, originalSize * TEXT_SCALE);
    }

    /** 建立透明內距，參數順序是上、左、下、右，單位為像素。 */
    static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** 將共用字型、文字色、背景與複合邊框套用到輸入元件。 */
    static void styleInput(JComponent component) {
        component.setFont(FONT);
        component.setForeground(TEXT);
        component.setBackground(INPUT);
        // 外層是一像素線條，內層是留白，讓文字不會貼住邊框。
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(207, 220, 234)),
                padding(2, 5, 2, 5)
        ));
    }

    /** 套用專案共用按鈕樣式，包含顏色、內距及手形游標。 */
    static void styleButton(AbstractButton button, Color background, Color foreground) {
        button.setFont(font(Font.BOLD, 13f));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setBorder(padding(7, 12, 7, 12));
        // 關閉 Look and Feel 的預設焦點外框，改用本專案一致的外觀。
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
    }
}
