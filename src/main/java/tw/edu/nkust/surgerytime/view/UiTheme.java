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

final class UiTheme {
    static final Color NAVY = new Color(11, 37, 68);
    static final Color NAVY_LIGHT = new Color(18, 57, 93);
    static final Color INK = new Color(18, 43, 73);
    static final Color TEXT = new Color(46, 65, 88);
    static final Color MUTED = new Color(105, 126, 148);
    static final Color TEAL = new Color(16, 157, 132);
    static final Color TEAL_DARK = new Color(8, 119, 101);
    static final Color TEAL_PALE = new Color(227, 247, 242);
    static final Color BLUE = new Color(42, 145, 200);
    static final Color PURPLE = new Color(105, 88, 198);
    static final Color AMBER = new Color(226, 163, 58);
    static final Color BACKGROUND = new Color(242, 246, 251);
    static final Color SURFACE = Color.WHITE;
    static final Color BORDER = new Color(220, 229, 239);
    static final Color INPUT = new Color(247, 249, 252);
    static final Color WARNING = new Color(255, 248, 232);
    static final Color RESULT = new Color(15, 70, 96);

    static final Font FONT = new Font("Microsoft JhengHei UI", Font.PLAIN, 14);

    private UiTheme() {
    }

    static void install() {
        UIManager.put("Label.font", FONT);
        UIManager.put("Button.font", FONT.deriveFont(Font.BOLD));
        UIManager.put("ToggleButton.font", FONT.deriveFont(Font.BOLD));
        UIManager.put("ComboBox.font", FONT);
        UIManager.put("Spinner.font", FONT);
        UIManager.put("Table.font", FONT.deriveFont(12f));
        UIManager.put("TableHeader.font", FONT.deriveFont(Font.BOLD, 12f));
        UIManager.put("OptionPane.messageFont", FONT);
        UIManager.put("OptionPane.buttonFont", FONT.deriveFont(Font.BOLD));
        UIManager.put("ToolTip.font", FONT.deriveFont(12f));
        UIManager.put("ScrollBar.width", 12);
    }

    static JLabel label(String text, float size, int style, Color color) {
        var label = new JLabel(text);
        label.setFont(FONT.deriveFont(style, size));
        label.setForeground(color);
        label.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return label;
    }

    static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static void styleInput(JComponent component) {
        component.setFont(FONT);
        component.setForeground(TEXT);
        component.setBackground(INPUT);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(207, 220, 234)),
                padding(8, 10, 8, 10)
        ));
    }

    static void styleButton(AbstractButton button, Color background, Color foreground) {
        button.setFont(FONT.deriveFont(Font.BOLD, 13f));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setBorder(padding(11, 18, 11, 18));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
    }
}
