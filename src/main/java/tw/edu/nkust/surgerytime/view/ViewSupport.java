package tw.edu.nkust.surgerytime.view;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

final class ViewSupport {
    private ViewSupport() {
    }

    static JPanel box(int axis) {
        var panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, axis));
        return panel;
    }

    static JTextArea wrappedText(String text, float fontSize, Color color) {
        var area = new JTextArea(text);
        area.setFont(UiTheme.FONT.deriveFont(fontSize));
        area.setForeground(color);
        area.setOpaque(false);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setPreferredSize(new Dimension(420, 36));
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        return area;
    }

    static JSeparator separator() {
        var separator = new JSeparator();
        separator.setForeground(UiTheme.BORDER);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        return separator;
    }

    static JScrollPane scroll(Component content) {
        var scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    static void fullWidth(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    static JLabel sectionLabel(String text) {
        return UiTheme.label(text, 11f, java.awt.Font.BOLD, UiTheme.TEAL_DARK);
    }
}
