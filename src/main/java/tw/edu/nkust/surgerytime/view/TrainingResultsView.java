package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.FeatureImportance;
import tw.edu.nkust.surgerytime.model.PredictionComparison;
import tw.edu.nkust.surgerytime.model.TrainingResult;
import tw.edu.nkust.surgerytime.model.TrainingSummary;
import tw.edu.nkust.surgerytime.service.MockTrainingResultService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class TrainingResultsView extends JPanel {
    private static final int COMPACT_BREAKPOINT = 1_020;
    private static final int PREVIEW_ROWS = 5;

    private final TrainingResult result;
    private final Runnable openPrediction;
    private final Consumer<String> statusSink;
    private final Runnable defaultButtonChanged;
    private final ScrollablePanel content = new ScrollablePanel();
    private final JScrollPane pageScroll;
    private final JPanel metricGrid = new JPanel();
    private final JSplitPane detailSplit = new JSplitPane();
    private final JButton toggleRowsButton = new JButton("檢視完整測試集");
    private final JButton saveButton = new JButton("模擬儲存模型");
    private final JButton continueButton = new JButton("完成並前往預測");
    private final JLabel countBadge = new JLabel();
    private final ComparisonTableModel tableModel;
    private final JLabel actionNote;

    private boolean showingAll;
    private boolean compact;

    public TrainingResultsView(
            MockTrainingResultService service,
            Runnable openPrediction,
            Consumer<String> statusSink,
            Runnable defaultButtonChanged
    ) {
        this.result = Objects.requireNonNull(service, "訓練結果服務不可為空").getTrainingResult();
        this.openPrediction = Objects.requireNonNull(openPrediction, "切換頁面動作不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.defaultButtonChanged = Objects.requireNonNull(defaultButtonChanged, "預設按鈕更新動作不可為空");
        this.tableModel = new ComparisonTableModel(result.predictionComparisons());
        this.actionNote = UiTheme.label(
                "完成檢視後可直接銜接手術時間預測，切頁不會清除目前狀態。",
                10f,
                Font.PLAIN,
                UiTheme.MUTED
        );

        setName("training-results-page");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        buildContent();
        pageScroll = ViewSupport.scroll(content);
        pageScroll.setName("training-page-scroll");
        add(pageScroll, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyResponsiveLayout(getWidth());
            }
        });
        updateRows();
    }

    public JButton defaultButton() {
        return continueButton;
    }

    public void scrollToTop() {
        pageScroll.getVerticalScrollBar().setValue(0);
    }

    public void scrollToBottom() {
        pageScroll.getVerticalScrollBar().setValue(pageScroll.getVerticalScrollBar().getMaximum());
    }

    private void buildContent() {
        content.setBackground(UiTheme.BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(UiTheme.padding(26, 34, 28, 34));

        content.add(buildHeading());
        content.add(Box.createVerticalStrut(18));
        content.add(buildCompletionBanner());
        content.add(Box.createVerticalStrut(18));
        buildMetricGrid();
        content.add(metricGrid);
        content.add(Box.createVerticalStrut(18));
        buildDetailSplit();
        content.add(detailSplit);
        content.add(Box.createVerticalStrut(18));
        content.add(buildActions());
    }

    private JComponent buildHeading() {
        var panel = ViewSupport.box(BoxLayout.Y_AXIS);
        var titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        titleLine.add(UiTheme.label("訓練結果", 29f, Font.BOLD, UiTheme.INK));
        var step = UiTheme.label("步驟 1 / 2", 11f, Font.BOLD, UiTheme.TEAL_DARK);
        step.setOpaque(true);
        step.setBackground(UiTheme.TEAL_PALE);
        step.setBorder(UiTheme.padding(5, 10, 5, 10));
        titleLine.add(step);
        panel.add(titleLine);
        panel.add(Box.createVerticalStrut(5));
        panel.add(ViewSupport.wrappedText(
                "檢視 CSV 模擬資料的收斂趨勢、特徵重要度與測試案例；本頁不會執行真正的模型訓練。",
                13f,
                UiTheme.MUTED
        ));
        ViewSupport.fullWidth(panel);
        return panel;
    }

    private JComponent buildCompletionBanner() {
        TrainingSummary summary = result.summary();
        var banner = new RoundedPanel(new Color(230, 248, 244), new Color(198, 232, 223), 18);
        banner.setLayout(new BorderLayout(14, 0));
        banner.setBorder(UiTheme.padding(14, 16, 14, 16));

        var icon = UiTheme.label("OK", 11f, Font.BOLD, Color.WHITE);
        icon.setOpaque(true);
        icon.setBackground(UiTheme.TEAL);
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setPreferredSize(new Dimension(38, 38));
        banner.add(icon, BorderLayout.WEST);

        var text = ViewSupport.box(BoxLayout.Y_AXIS);
        text.add(UiTheme.label("模擬訓練已完成", 15f, Font.BOLD, new Color(20, 86, 73)));
        text.add(Box.createVerticalStrut(3));
        text.add(UiTheme.label(String.format(
                Locale.TAIWAN,
                "%s · %,d Epoch · %,d 筆 CSV 模擬訓練資料 · 耗時 %.1f 秒",
                summary.modelName(),
                summary.completedEpochs(),
                summary.trainingSampleCount(),
                summary.trainingDurationMillis() / 1_000.0
        ), 11f, Font.PLAIN, new Color(85, 118, 110)));
        banner.add(text, BorderLayout.CENTER);

        var badge = UiTheme.label("CSV · DEMO RESULT", 10f, Font.BOLD, UiTheme.TEAL_DARK);
        badge.setOpaque(true);
        badge.setBackground(new Color(211, 240, 234));
        badge.setBorder(UiTheme.padding(6, 10, 6, 10));
        banner.add(badge, BorderLayout.EAST);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        return banner;
    }

    private void buildMetricGrid() {
        metricGrid.setOpaque(false);
        metricGrid.setLayout(new GridLayout(1, 4, 14, 14));
        TrainingSummary summary = result.summary();
        metricGrid.add(metricCard("測試 MAE", String.format(Locale.TAIWAN, "%.1f 分", summary.meanAbsoluteErrorMinutes()),
                "平均絕對誤差", UiTheme.TEAL));
        metricGrid.add(metricCard("測試 RMSE", String.format(Locale.TAIWAN, "%.1f 分", summary.rootMeanSquaredErrorMinutes()),
                "較重視大幅誤差", UiTheme.BLUE));
        metricGrid.add(metricCard("決定係數 R²", String.format(Locale.TAIWAN, "%.3f", summary.rSquared()),
                "模擬擬合程度", UiTheme.PURPLE));
        metricGrid.add(metricCard("訓練耗時", String.format(Locale.TAIWAN, "%.1f 秒", summary.trainingDurationMillis() / 1_000.0),
                "固定 CSV 展示值", UiTheme.AMBER));
        metricGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        metricGrid.setPreferredSize(new Dimension(1000, 108));
        metricGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JComponent metricCard(String labelText, String valueText, String helperText, Color accent) {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                UiTheme.padding(14, 16, 14, 16)
        ));
        var body = ViewSupport.box(BoxLayout.Y_AXIS);
        body.add(UiTheme.label(labelText, 11f, Font.BOLD, UiTheme.MUTED));
        body.add(Box.createVerticalStrut(5));
        body.add(UiTheme.label(valueText, 24f, Font.BOLD, UiTheme.INK));
        body.add(Box.createVerticalStrut(5));
        body.add(UiTheme.label(helperText, 10f, Font.PLAIN, new Color(143, 159, 176)));
        card.add(body);
        return card;
    }

    private void buildDetailSplit() {
        var left = ViewSupport.box(BoxLayout.Y_AXIS);
        left.add(buildConvergenceCard());
        left.add(Box.createVerticalStrut(16));
        left.add(buildFeatureCard());

        detailSplit.setLeftComponent(left);
        detailSplit.setRightComponent(buildComparisonCard());
        detailSplit.setResizeWeight(0.43);
        detailSplit.setDividerLocation(0.43);
        detailSplit.setDividerSize(14);
        detailSplit.setBorder(null);
        detailSplit.setOpaque(false);
        detailSplit.setContinuousLayout(true);
        detailSplit.setPreferredSize(new Dimension(1200, 820));
        detailSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 820));
        detailSplit.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JComponent buildConvergenceCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("訓練模型的細節"));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("模擬 RMSE 收斂趨勢", 19f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(5));
        card.add(ViewSupport.wrappedText("以折線圖重整 CSV 中的 Epoch 紀錄，所有數值皆為固定展示資料。",
                12f, UiTheme.MUTED));
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(10));
        card.add(new RmseChartPanel(result.epochRmseHistory()));
        card.add(Box.createVerticalStrut(10));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(10));
        card.add(infoRow("示範方法", result.summary().algorithm()));
        card.add(Box.createVerticalStrut(8));
        card.add(infoRow("資料版本", result.summary().dataVersion()));
        card.setPreferredSize(new Dimension(460, 440));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 440));
        return card;
    }

    private JComponent buildFeatureCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("特徵與其重要度"));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("模擬影響排序", 19f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(5));
        card.add(ViewSupport.wrappedText("長條為相對占比，數值直接讀取 feature_importance.csv。",
                12f, UiTheme.MUTED));
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));
        for (FeatureImportance item : result.featureImportances()) {
            card.add(featureRow(item));
            card.add(Box.createVerticalStrut(10));
        }
        card.setPreferredSize(new Dimension(460, 365));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 365));
        return card;
    }

    private JComponent featureRow(FeatureImportance item) {
        var row = ViewSupport.box(BoxLayout.Y_AXIS);
        var heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(UiTheme.label(item.featureName(), 12f, Font.BOLD, UiTheme.TEXT), BorderLayout.WEST);
        heading.add(UiTheme.label(String.format(Locale.TAIWAN, "%.0f%%", item.importance() * 100),
                11f, Font.BOLD, UiTheme.TEAL_DARK), BorderLayout.EAST);
        row.add(heading);
        row.add(Box.createVerticalStrut(5));
        var bar = new JProgressBar(0, 100);
        bar.setValue((int) Math.round(item.importance() * 100));
        bar.setForeground(UiTheme.TEAL);
        bar.setBackground(new Color(232, 238, 244));
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(200, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        row.add(bar);
        return row;
    }

    private JComponent buildComparisonCard() {
        var card = card();
        var header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        var heading = ViewSupport.box(BoxLayout.Y_AXIS);
        heading.add(ViewSupport.sectionLabel("實際值與預測值的對照"));
        heading.add(Box.createVerticalStrut(5));
        heading.add(UiTheme.label("模擬測試集明細", 19f, Font.BOLD, UiTheme.INK));
        header.add(heading, BorderLayout.CENTER);
        countBadge.setFont(UiTheme.FONT.deriveFont(Font.BOLD, 10f));
        countBadge.setForeground(UiTheme.TEAL_DARK);
        countBadge.setOpaque(true);
        countBadge.setBackground(UiTheme.TEAL_PALE);
        countBadge.setBorder(UiTheme.padding(6, 10, 6, 10));
        header.add(countBadge, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        card.add(header);
        card.add(Box.createVerticalStrut(7));
        card.add(ViewSupport.wrappedText(
                "提供 10 筆 CSV 固定案例；差值定義為實際值減預測值。",
                12f,
                UiTheme.MUTED
        ));
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(10));

        var table = new JTable(tableModel);
        table.setName("training-comparison-table");
        table.setRowHeight(42);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(221, 243, 238));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setPreferredSize(new Dimension(100, 42));
        table.getTableHeader().setBackground(new Color(238, 244, 248));
        table.getTableHeader().setForeground(new Color(64, 88, 111));
        var centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        for (int column = 0; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(centered);
        }
        var tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        tableScroll.setPreferredSize(new Dimension(620, 510));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 510));
        card.add(tableScroll);
        card.add(Box.createVerticalStrut(8));
        card.add(UiTheme.label("正值：實際時間較長　｜　負值：預測時間較長",
                10f, Font.PLAIN, UiTheme.MUTED));
        return card;
    }

    private JComponent buildActions() {
        var actions = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 16);
        actions.setLayout(new BorderLayout(12, 0));
        actions.setBorder(UiTheme.padding(12, 14, 12, 14));
        actions.add(actionNote, BorderLayout.CENTER);

        UiTheme.styleButton(toggleRowsButton, new Color(237, 243, 248), new Color(41, 67, 94));
        UiTheme.styleButton(saveButton, new Color(237, 243, 248), new Color(41, 67, 94));
        UiTheme.styleButton(continueButton, UiTheme.TEAL, Color.WHITE);
        toggleRowsButton.setName("view-test-set-button");
        saveButton.setName("save-training-model-button");
        continueButton.setName("continue-to-prediction-button");

        toggleRowsButton.addActionListener(event -> {
            showingAll = !showingAll;
            updateRows();
        });
        saveButton.addActionListener(event -> simulateSave());
        continueButton.addActionListener(event -> openPrediction.run());

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(toggleRowsButton);
        buttons.add(saveButton);
        buttons.add(continueButton);
        actions.add(buttons, BorderLayout.EAST);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        return actions;
    }

    private RoundedPanel card() {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(20, 20, 20, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private JComponent infoRow(String label, String value) {
        var row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(UiTheme.label(label, 11f, Font.PLAIN, UiTheme.MUTED), BorderLayout.WEST);
        row.add(UiTheme.label(value, 11f, Font.BOLD, UiTheme.TEXT), BorderLayout.EAST);
        return row;
    }

    private void updateRows() {
        int count = showingAll ? result.predictionComparisons().size() : PREVIEW_ROWS;
        tableModel.setVisibleCount(count);
        countBadge.setText("顯示 " + count + " / " + result.predictionComparisons().size() + " 筆");
        toggleRowsButton.setText(showingAll ? "收合測試集" : "檢視完整測試集");
        statusSink.accept(showingAll
                ? "已顯示全部 10 筆 CSV 模擬測試案例"
                : "目前顯示 5 筆預覽案例，可展開完整 CSV 測試集");
    }

    private void simulateSave() {
        saveButton.setEnabled(false);
        saveButton.setText("模擬儲存中…");
        statusSink.accept("正在模擬儲存模型；不會建立任何模型檔案");
        defaultButtonChanged.run();
        var timer = new Timer(520, event -> {
            saveButton.setText("已模擬儲存");
            statusSink.accept("模型儲存流程模擬完成；未建立實體檔案");
            if (!Boolean.getBoolean("surgerytime.suppressDialogs") && isShowing()) {
                JOptionPane.showMessageDialog(
                        this,
                        "此按鈕只重現參考簡報流程。\n目前不會建立、覆寫或匯出任何模型檔案。",
                        "模擬儲存完成",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void applyResponsiveLayout(int width) {
        boolean shouldCompact = width > 0 && width < COMPACT_BREAKPOINT;
        if (shouldCompact == compact && width > 0) {
            return;
        }
        compact = shouldCompact;
        metricGrid.setLayout(new GridLayout(compact ? 2 : 1, compact ? 2 : 4, 14, 14));
        metricGrid.setPreferredSize(new Dimension(900, compact ? 230 : 108));
        metricGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 230 : 116));
        detailSplit.setOrientation(compact ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
        detailSplit.setPreferredSize(new Dimension(900, compact ? 1_570 : 820));
        detailSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 1_570 : 820));
        detailSplit.setResizeWeight(compact ? 0.58 : 0.43);
        detailSplit.setDividerLocation(compact ? 0.58 : 0.43);
        actionNote.setVisible(!compact);
        content.revalidate();
        content.repaint();
    }

    private static final class ComparisonTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "案例編號", "實際值（分鐘）", "預測值（分鐘）", "差值（實際－預測）"
        };
        private final List<PredictionComparison> rows;
        private int visibleCount;

        private ComparisonTableModel(List<PredictionComparison> rows) {
            this.rows = List.copyOf(rows);
            visibleCount = Math.min(PREVIEW_ROWS, rows.size());
        }

        void setVisibleCount(int visibleCount) {
            this.visibleCount = Math.min(visibleCount, rows.size());
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return visibleCount;
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PredictionComparison item = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.caseId();
                case 1 -> item.actualMinutes();
                case 2 -> item.predictedMinutes();
                case 3 -> String.format(Locale.TAIWAN, "%+d 分",
                        item.actualMinutes() - item.predictedMinutes());
                default -> "";
            };
        }
    }
}
