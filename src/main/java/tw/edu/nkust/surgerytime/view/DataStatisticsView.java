package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.ProcessingTable;
import tw.edu.nkust.surgerytime.service.DataStatisticsService;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/** PPT statistics, with explicit source column selection and no synthetic fallback. */
public final class DataStatisticsView extends WorkflowPagePanel {
    private final SurgeryModelService modelService;
    private final Consumer<String> statusSink;
    private final DataStatisticsService statistics = new DataStatisticsService();
    private final JComboBox<String> topic = new JComboBox<>(new String[]{
            "各疾病／術式平均時間", "各科別平均時間", "各醫師平均時間", "醫師年資與平均時間",
            "各科別／醫師平均時間", "各科別手術人次", "自訂欄位統計"});
    private final JComboBox<ProcessingTable.Column> first = new JComboBox<>();
    private final JComboBox<ProcessingTable.Column> second = new JComboBox<>();
    private final JComboBox<String> metric = new JComboBox<>(new String[]{"平均時間（分鐘）", "手術人次"});
    private final JComboBox<String> chartType = new JComboBox<>(new String[]{"長條圖", "圓餅圖（人次占比）", "折線圖"});
    private final JLabel message = new JLabel("請先匯入或生成資料");
    private final JLabel firstLabel = new JLabel("分組欄位");
    private final JLabel secondLabel = new JLabel("第二欄位");
    private final StatisticsChartPanel chart = new StatisticsChartPanel();
    private JTable table;
    private final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int col) { return false; }
        @Override public Class<?> getColumnClass(int col) {
            String name = getColumnName(col);
            if (name.equals("人次")) return Long.class;
            if (name.equals("占比（%）") || name.contains("平均")) return Double.class;
            if (name.equals("最短（分）") || name.equals("最長（分）")) return Integer.class;
            return String.class;
        }
    };
    private ProcessingTable source = new ProcessingTable(List.of(), List.of(), "手術時間");
    private boolean adjusting;

    public DataStatisticsView(SurgeryModelService service, Consumer<String> status, Runnable completed) {
        super("資料統計", "步驟 3 / 9");
        modelService = Objects.requireNonNull(service); statusSink = Objects.requireNonNull(status);
        topic.setName("statistics-topic"); first.setName("statistics-first"); second.setName("statistics-second");
        metric.setName("statistics-metric"); chartType.setName("statistics-chart-type"); message.setName("statistics-message");
        var card = card(); card.add(ViewSupport.sectionLabel("分組統計與圖表"));
        card.add(row(new JLabel("統計項目"), topic, new JLabel("圖表數值"), metric, new JLabel("圖表類型"), chartType));
        card.add(row(firstLabel, first, secondLabel, second));

        card.add(row(message));
        card.add(ViewSupport.wrappedText("使用目前已套用處理的資料；每筆手術紀錄計為 1 人次（非不重複病患）。缺少分組值的紀錄不納入，占比分母為有效人次。年資依來源值分組。圓餅圖固定呈現人次占比。", 12f, UiTheme.MUTED));
        table = new JTable(tableModel); table.setName("statistics-table"); table.setRowHeight(30); table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        var tableScroll = new JScrollPane(table); tableScroll.setMinimumSize(new Dimension(320, 200));
        tableScroll.setColumnHeaderView(table.getTableHeader());
        var chartScroll = new JScrollPane(chart); chartScroll.setMinimumSize(new Dimension(300, 200));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, chartScroll);
        split.setResizeWeight(.48); split.setDividerLocation(520);
        split.setPreferredSize(new Dimension(1100, 300)); split.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        split.setAlignmentX(LEFT_ALIGNMENT); card.add(split);
        var next = new JButton("完成統計檢視"); next.addActionListener(e -> completed.run());
        UiTheme.styleButton(next, UiTheme.TEAL, Color.WHITE); card.add(row(next));
        card.setPreferredSize(new Dimension(1100, 550)); content.add(card);
        topic.addActionListener(e -> { if (!adjusting) { selectDefaults(); updateStatistics(); } });
        first.addActionListener(e -> { if (!adjusting) updateStatistics(); });
        second.addActionListener(e -> { if (!adjusting) updateStatistics(); });
        metric.addActionListener(e -> { if (!adjusting) updateStatistics(); });
        chartType.addActionListener(e -> {
            if (adjusting) return;
            if (chartType.getSelectedIndex() == 1) { adjusting = true; metric.setSelectedIndex(1); adjusting = false; }
            updateStatistics();
        });
    }

    private JPanel row(Component... components) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)); panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT); panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        for (var component : components) {
            if (component instanceof JComboBox<?> combo) { UiTheme.styleInput(combo); combo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(207, 220, 234)), BorderFactory.createEmptyBorder(2, 6, 2, 6))); combo.setPreferredSize(new Dimension(220, Math.max(34, combo.getPreferredSize().height))); }
            panel.add(component);
        }
        return panel;
    }

    public void refreshData() {
        var newSource = modelService.processingCurrent().table();
        boolean sameColumns = source.columns().equals(newSource.columns()); source = newSource;
        if (!sameColumns) {
            adjusting = true;
            var columns = source.columns().stream().filter(c -> !c.key().equals("case_id") && !c.key().equals("actual_minutes")).toArray(ProcessingTable.Column[]::new);
            first.setModel(new DefaultComboBoxModel<>(columns)); second.setModel(new DefaultComboBoxModel<>(columns));
            adjusting = false; selectDefaults();
        }
        updateStatistics();
    }

    private boolean cross() { return topic.getSelectedIndex() == 0 || topic.getSelectedIndex() == 4; }
    private void selectDefaults() {
        adjusting = true;
        try {
            switch (topic.getSelectedIndex()) {
                case 0 -> { select(first, "疾病", "疾病名稱", "疾病代碼", "診斷", "診斷碼", "diagnosis", "disease"); select(second, "procedure", "術式", "手術名稱"); }
                case 1, 5 -> select(first, "科別", "手術科別", "科室", "department", "specialty");
                case 2 -> select(first, "surgeon_code", "主治醫師", "醫師", "surgeon");
                case 3 -> select(first, "醫師年資", "主治醫師年資", "年資", "surgeon_experience", "experience_years");
                case 4 -> { select(first, "科別", "手術科別", "科室", "department", "specialty"); select(second, "surgeon_code", "主治醫師", "醫師", "surgeon"); }
                default -> { if (first.getSelectedIndex() < 0 && first.getItemCount() > 0) first.setSelectedIndex(0); }
            }
            metric.setSelectedIndex(topic.getSelectedIndex() == 5 ? 1 : 0);
            chartType.setSelectedIndex(topic.getSelectedIndex() == 5 ? 1 : 0);
        } finally { adjusting = false; }
    }
    private void select(JComboBox<ProcessingTable.Column> box, String... aliases) {
        box.setSelectedIndex(-1);
        for (String alias : aliases) for (int i = 0; i < box.getItemCount(); i++) {
            var c = box.getItemAt(i);
            String name = c.key().startsWith("extra:") ? c.key().substring(6) : c.key();
            if (name.strip().equalsIgnoreCase(alias) || c.label().equalsIgnoreCase(alias)) { box.setSelectedIndex(i); return; }
        }
    }
    private void updateStatistics() {
        boolean cross = cross(); second.setVisible(cross); secondLabel.setVisible(cross);
        String[] roles = switch (topic.getSelectedIndex()) {
            case 0 -> new String[]{"疾病欄位", "術式欄位"};
            case 1, 5 -> new String[]{"科別欄位", ""};
            case 2 -> new String[]{"醫師欄位", ""};
            case 3 -> new String[]{"年資欄位", ""};
            case 4 -> new String[]{"科別欄位", "醫師欄位"};
            default -> new String[]{"分組欄位", ""};
        };
        firstLabel.setText(roles[0]); secondLabel.setText(roles[1]);
        metric.setEnabled(chartType.getSelectedIndex() != 1);
        var a = (ProcessingTable.Column) first.getSelectedItem(); var b = (ProcessingTable.Column) second.getSelectedItem();
        tableModel.setDataVector(new Object[0][], cross
                ? new String[]{"群組一", "群組二", "人次", "占比（%）", source.target()+"平均（分）", "最短（分）", "最長（分）"}
                : new String[]{"群組", "人次", "占比（%）", source.target()+"平均（分）", "最短（分）", "最長（分）"});
        for (int i = 0; i < table.getColumnCount(); i++) {
            var column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(i < (cross ? 2 : 1) ? 145 : table.getColumnName(i).contains("平均") ? 160 : 90);
        }
        chart.setData(List.of(), false, false, source.target());
        if (source.rows().isEmpty()) { showStatus("請先匯入或生成資料"); return; }
        if (a == null || cross && b == null) {
            showStatus("未辨識到" + (a == null ? roles[0] : roles[1]) + "：請匯入該欄位，或從上方手動指定。"); return;
        }
        if (cross && a.key().equals(b.key())) { showStatus("請選擇兩個不同的分組欄位"); return; }
        var result = statistics.summarize(source, cross ? List.of(a.key(), b.key()) : List.of(a.key()));
        for (var group : result.groups()) {
            var cells = new ArrayList<Object>(group.values());
            cells.add(group.count()); cells.add(round(group.percent())); cells.add(round(group.average())); cells.add(group.minimum()); cells.add(group.maximum());
            tableModel.addRow(cells.toArray());
        }
        chart.setContext(Objects.toString(topic.getSelectedItem())+"；有效 "+result.included()+" 人次／全部 "+source.rows().size()+" 筆");
        chart.setLine(chartType.getSelectedIndex() == 2);
        chart.setData(result.groups(), metric.getSelectedIndex() == 1, chartType.getSelectedIndex() == 1, source.target());
        showStatus("共 " + source.rows().size() + " 筆；有效 " + result.included() + " 人次；缺分組值排除 " + result.excluded() + " 筆；" + result.groups().size() + " 組");
    }
    private static double round(double n) { return Math.round(n * 100.0) / 100.0; }
    private void showStatus(String text) { message.setText(text); statusSink.accept(text); }
}
