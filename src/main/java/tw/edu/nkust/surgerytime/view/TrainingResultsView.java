package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.FeatureImportance;
import tw.edu.nkust.surgerytime.model.PredictionComparison;
import tw.edu.nkust.surgerytime.model.TrainingResult;
import tw.edu.nkust.surgerytime.model.TrainingSummary;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 「訓練結果」完整頁面。
 *
 * <p>本頁呈現 Java 真正訓練後計算的結果，包括測試集摘要指標、RMSE 收斂曲線、
 * 由打亂欄位測得的 permutation importance 與未參與訓練的測試案例。</p>
 *
 * <p>這個類別屬於 View，只負責排列及更新 Swing 元件；資料載入工作由
 * {@link SurgeryModelService} 負責。</p>
 */
public final class TrainingResultsView extends JPanel {
    /** 寬度小於 1020 像素時，頁面改用較適合窄視窗的上下排列。 */
    private static final int COMPACT_BREAKPOINT = 1_020;
    /**
     * 模型檔名使用的日期時間格式：yyyyMMdd 是年月日，HHmmss 是 24 小時制的時分秒。
     * 例如 2026 年 9 月 8 日下午 3:30:45 會變成 20260908_153045。
     */
    private static final DateTimeFormatter MODEL_FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 已由 Service 載入並驗證完成的全部訓練展示資料。 */
    private final TrainingResult result;
    private final Runnable historyActivated;
    /** 保存共用服務，按下「儲存模型」時由它序列化真正的 WEKA 網路。 */
    private final SurgeryModelService modelService;
    /** 使用者完成檢視時，要求外框切換到預測頁面的動作。 */
    private final Runnable openPrediction;
    /** 將本頁最新狀態文字傳回外框頁尾。 */
    private final Consumer<String> statusSink;
    /** 所有縱向內容的主要容器。 */
    private final ScrollablePanel content = new ScrollablePanel();
    /** 包住 content 的捲動容器。 */
    private final JScrollPane pageScroll;
    /** 放置 MAE、RMSE、R² 與耗時四張卡片的網格。 */
    private final JPanel metricGrid = new JPanel();
    /** 寬視窗左右分割、窄視窗上下分割的詳細資料區。 */
    private final JSplitPane detailSplit = new JSplitPane();
    /** 切換至手術時間預測頁面。 */
    private final JButton continueButton = new JButton("完成並前往預測");
    /** 將目前已訓練 WEKA 網路與完整前處理資訊寫入 .model 檔。 */
    private final JButton saveModelButton = new JButton("儲存模型");
    /** 顯示目前表格列數，例如「顯示 5 / 10 筆」。 */
    private final JLabel countBadge = new JLabel();
    /** JTable 與 Java 資料清單之間的轉接模型。 */
    private final ComparisonTableModel tableModel;
    /** 保存表格元件，讓建構子能在整頁捲軸建立後設定滾輪事件轉交。 */
    private JTable comparisonTable;
    /** 表格自己的捲動容器；完整測試集很長時先在此容器內捲動。 */
    private JScrollPane comparisonTableScroll;
    /** 記錄目前是否使用窄版排列，避免每次 resize 都重做相同設定。 */
    private boolean compact;

    /**
     * 建立訓練結果頁面。
     *
     * @param service 提供最近一次真正計算的訓練結果
     * @param openPrediction 切換到預測頁面的回呼
     * @param statusSink 更新外框頁尾狀態的回呼
     */
    public TrainingResultsView(
            SurgeryModelService service,
            Runnable openPrediction,
            Consumer<String> statusSink
    ) { this(service, openPrediction, statusSink, () -> {}); }

    public TrainingResultsView(SurgeryModelService service, Runnable openPrediction, Consumer<String> statusSink, Runnable historyActivated) {
        this.historyActivated = historyActivated;
        // 先取得完整資料，若 service 為 null 會立即提供清楚錯誤。
        this.modelService = Objects.requireNonNull(service, "訓練結果服務不可為空");
        this.result = modelService.trainingResult();
        this.openPrediction = Objects.requireNonNull(openPrediction, "切換頁面動作不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        // 表格模型只需要驗證案例清單，不需要知道畫面其他資料。
        this.tableModel = new ComparisonTableModel(result.predictionComparisons());

        // 元件名稱供視覺測試尋找，使用者不會直接看到。
        setName("training-results-page");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        buildContent();
        pageScroll = ViewSupport.scroll(content);
        pageScroll.setName("training-page-scroll");
        // 表格可捲時先捲資料；到表格邊界後，滾輪自然接續控制整個結果頁。
        ViewSupport.routeNestedMouseWheel(comparisonTable, comparisonTableScroll, pageScroll);
        add(pageScroll, BorderLayout.CENTER);
        // 監聽頁面尺寸改變，讓寬版與窄版配置可以即時切換。
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyResponsiveLayout(getWidth());
            }
        });
        countBadge.setText("顯示 " + result.predictionComparisons().size()
                + " / " + result.predictionComparisons().size() + " 筆");
    }

    /** @return 此頁按 Enter 時應觸發的「前往預測」按鈕 */
    public JButton defaultButton() {
        return continueButton;
    }

    /** 將頁面捲到最上方；外框切換到本頁時會呼叫。 */
    public void scrollToTop() {
        pageScroll.getVerticalScrollBar().setValue(0);
    }

    /** 將頁面捲到最下方；主要供視覺測試檢查底部內容。 */
    public void scrollToBottom() {
        pageScroll.getVerticalScrollBar().setValue(pageScroll.getVerticalScrollBar().getMaximum());
    }

    /** 依照由上到下的順序組裝本頁所有主要區塊。 */
    private void buildContent() {
        content.setLayout(new BorderLayout(0,6));content.setBackground(UiTheme.BACKGROUND);
        content.setBorder(UiTheme.padding(8,8,8,8));
        var tabs=new javax.swing.JTabbedPane();tabs.setName("reference-result-tabs");
        var overview=new JPanel(new BorderLayout(0,6));overview.setBackground(UiTheme.BACKGROUND);
        var left=new JPanel(new BorderLayout(0,6));
        var summary=result.summary();
        var metrics=new javax.swing.table.DefaultTableModel(new String[]{"資料集","筆數","MAE 分","RMSE 分","R²","評估 ms"},0){public boolean isCellEditable(int r,int c){return false;}};
        if(result.trainingMetrics()!=null){var m=result.trainingMetrics();metrics.addRow(new Object[]{"訓練",m.count(),String.format("%.3f",m.mae()),String.format("%.3f",m.rmse()),String.format("%.4f",m.rSquared()),m.evaluationMillis()});}
        else metrics.addRow(new Object[]{"訓練",summary.trainingSampleCount(),"未保存","未保存","未保存","未保存"});
        metrics.addRow(new Object[]{"測試",summary.validationSampleCount(),String.format("%.3f",summary.meanAbsoluteErrorMinutes()),String.format("%.3f",summary.rootMeanSquaredErrorMinutes()),String.format("%.4f",summary.rSquared()),result.testMetrics()==null?"未保存":result.testMetrics().evaluationMillis()});
        var metricTable=new JTable(metrics);metricTable.setName("train-test-metrics");metricTable.setRowHeight(28);metricTable.setFont(UiTheme.font(Font.PLAIN,12f));metricTable.getTableHeader().setFont(UiTheme.font(Font.PLAIN,11f));
        var metricScroll=new JScrollPane(metricTable);metricScroll.setColumnHeaderView(metricTable.getTableHeader());metricScroll.setPreferredSize(new Dimension(400,82));left.add(metricScroll,BorderLayout.NORTH);
        var details=new javax.swing.JTextArea("模型："+summary.modelName()+"\n演算法："+summary.algorithm()+"\n參數："+modelService.algorithmConfiguration().description(modelService.configuration())
                +"\n原始／編碼特徵："+summary.featureCount()+" / "+summary.encodedFeatureCount()+"\n訓練與評估總耗時："+summary.trainingDurationMillis()+" ms\n\n資料版本："+summary.dataVersion()
                +"\n\nWEKA BPNN 不公開單次訓練中間輪次誤差；未重現簡報自主 BPNN。常數目標 R² 依本程式約定為 0。\n");
        details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);details.setFont(UiTheme.FONT);
        details.setBorder(UiTheme.padding(12,12,12,12));left.add(new JScrollPane(details));
        var weights=new javax.swing.table.DefaultTableModel(new String[]{"特徵", "相對重要度"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
        for(var item:result.featureImportances())weights.addRow(new Object[]{item.featureName(),item.importance()});
        var features=new JTable(weights);features.setRowHeight(25);var featureScroll=new JScrollPane(features);featureScroll.setColumnHeaderView(features.getTableHeader());
        featureScroll.setPreferredSize(new Dimension(400,180));var importancePanel=new JPanel(new BorderLayout());importancePanel.add(new JLabel("測試集 permutation importance（無增幅時為 0）"),BorderLayout.NORTH);importancePanel.add(featureScroll);left.add(importancePanel,BorderLayout.SOUTH);
        detailSplit.setLeftComponent(left);
        var predictions=new javax.swing.JTabbedPane();predictions.addTab("測試集逐筆結果",buildComparisonCard());
        var trainingRows=new javax.swing.table.DefaultTableModel(new String[]{"案例編號","實際分鐘","預測分鐘","差值（實際－預測）"},0){public boolean isCellEditable(int r,int c){return false;}};
        for(var item:result.trainingComparisons())trainingRows.addRow(new Object[]{item.caseId(),item.actualMinutes(),item.predictedMinutes(),item.actualMinutes()-item.predictedMinutes()});
        var trainingTable=new JTable(trainingRows);trainingTable.setName("training-prediction-table");trainingTable.setRowHeight(26);var trainScroll=new JScrollPane(trainingTable);trainScroll.setColumnHeaderView(trainingTable.getTableHeader());
        predictions.addTab("訓練集逐筆結果",trainScroll);detailSplit.setRightComponent(predictions);
        detailSplit.setName("reference-result-split");detailSplit.setResizeWeight(.45);detailSplit.setDividerLocation(.45);
        detailSplit.setPreferredSize(new Dimension(1100,650));
        overview.add(detailSplit);overview.add(buildActions(),BorderLayout.SOUTH);
        tabs.addTab("訓練結果",overview);
        tabs.addTab("四種方法比較",new TrainingHistoryPanel(modelService,historyActivated));
        tabs.addTab("RMSE 比較",buildConvergenceCard());
        content.add(tabs);content.setPreferredSize(new Dimension(1100,545));
    }

    /** 建立頁面標題、步驟標籤與「非真正訓練」說明。 */
    private JComponent buildHeading() {
        var panel = ViewSupport.box(BoxLayout.Y_AXIS);
        var titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        titleLine.add(UiTheme.label("訓練結果", 29f, Font.BOLD, UiTheme.INK));
        var step = UiTheme.label("步驟 8 / 9", 11f, Font.BOLD, UiTheme.TEAL_DARK);
        step.setOpaque(true);
        step.setBackground(UiTheme.TEAL_PALE);
        step.setBorder(UiTheme.padding(5, 10, 5, 10));
        titleLine.add(step);
        panel.add(titleLine);
        ViewSupport.fullWidth(panel);
        return panel;
    }

    /** 建立顯示模型名稱、Epoch、資料筆數與展示耗時的完成提示。 */
    private JComponent buildCompletionBanner() {
        // 先取出 summary，避免下方每個欄位都重複呼叫 result.summary()。
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
        text.add(UiTheme.label("WEKA 模型訓練已完成", 15f, Font.BOLD, new Color(20, 86, 73)));
        text.add(Box.createVerticalStrut(3));
        text.add(UiTheme.label(String.format(
                Locale.TAIWAN,
                "%s · %,d 筆訓練資料 · 耗時 %.3f 秒",
                summary.modelName(),
                summary.trainingSampleCount(),
                summary.trainingDurationMillis() / 1_000.0
        ), 11f, Font.PLAIN, new Color(85, 118, 110)));
        banner.add(text, BorderLayout.CENTER);

        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        return banner;
    }

    /** 建立四張摘要指標卡，並放入可響應式調整欄數的 metricGrid。 */
    private void buildMetricGrid() {
        metricGrid.setOpaque(false);
        metricGrid.setLayout(new GridLayout(1, 4, 14, 14));
        TrainingSummary summary = result.summary();
        metricGrid.add(metricCard("測試 MAE", String.format(Locale.TAIWAN, "%.1f 分鐘", summary.meanAbsoluteErrorMinutes()),
                String.format(Locale.TAIWAN, "正規化後 %.4f（依測試集範圍）",
                        normalizedByActualRange(summary.meanAbsoluteErrorMinutes()))));
        metricGrid.add(metricCard("測試 RMSE", String.format(Locale.TAIWAN, "%.1f 分鐘", summary.rootMeanSquaredErrorMinutes()),
                String.format(Locale.TAIWAN, "正規化後 %.4f（依測試集範圍）",
                        normalizedByActualRange(summary.rootMeanSquaredErrorMinutes()))));
        metricGrid.add(metricCard("決定係數 R²", String.format(Locale.TAIWAN, "%.3f", summary.rSquared()),
                "測試集擬合程度"));
        metricGrid.add(metricCard("訓練耗時", String.format(Locale.TAIWAN, "%.1f 秒", summary.trainingDurationMillis() / 1_000.0),
                "Java 實際執行時間"));
        metricGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        metricGrid.setPreferredSize(new Dimension(1000, 108));
        metricGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /**
     * 將分鐘誤差除以測試集的實際時間範圍，得到沒有單位的正規化誤差。
     * 例如最長與最短案例相差 250 分鐘，RMSE 為 15 分鐘，結果就是 15/250=0.06。
     */
    private double normalizedByActualRange(double errorMinutes) {
        double minimum = result.predictionComparisons().stream()
                .mapToDouble(PredictionComparison::actualMinutes)
                .min()
                .orElse(0.0);
        double maximum = result.predictionComparisons().stream()
                .mapToDouble(PredictionComparison::actualMinutes)
                .max()
                .orElse(0.0);
        double range = maximum - minimum;
        return range <= 0.0 ? 0.0 : errorMinutes / range;
    }

    /**
     * 建立單張指標卡。
     *
     * @param labelText 指標名稱
     * @param valueText 主要數值
     * @param helperText 數值下方的補充說明
     */
    private JComponent metricCard(String labelText, String valueText, String helperText) {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BorderLayout());
        // 四邊使用相同留白，不再繪製原本位於卡片左側的彩色直柱。
        card.setBorder(UiTheme.padding(14, 16, 14, 16));
        var body = ViewSupport.box(BoxLayout.Y_AXIS);
        body.add(UiTheme.label(labelText, 11f, Font.BOLD, UiTheme.MUTED));
        body.add(Box.createVerticalStrut(5));
        body.add(UiTheme.label(valueText, 24f, Font.BOLD, UiTheme.INK));
        body.add(Box.createVerticalStrut(5));
        body.add(UiTheme.label(helperText, 10f, Font.PLAIN, new Color(143, 159, 176)));
        card.add(body);
        return card;
    }

    /**
     * 建立詳細資料分割區：左側放曲線與特徵，右側放測試案例表格。
     * 窄版時同一個 JSplitPane 會切換成上下排列。
     */
    private void buildDetailSplit() {
        var left = ViewSupport.box(BoxLayout.Y_AXIS);
        left.add(buildConvergenceCard());
        left.add(Box.createVerticalStrut(16));
        left.add(buildFeatureCard());

        detailSplit.setLeftComponent(left);
        // 用透明容器把表格卡固定在頂端，避免分割區變高後右側被拉成大片白框。
        var right = ViewSupport.box(BoxLayout.Y_AXIS);
        right.add(buildComparisonCard());
        detailSplit.setRightComponent(right);
        detailSplit.setResizeWeight(0.43);
        detailSplit.setDividerLocation(0.43);
        // 隱藏 JSplitPane 預設的點狀拖曳柱，畫面只保留左右內容。
        detailSplit.setDividerSize(0);
        // 關閉分割面板的操作狀態，防止使用者拖動隱形的分隔位置。
        detailSplit.setEnabled(false);
        detailSplit.setFocusable(false);
        detailSplit.setBorder(null);
        detailSplit.setOpaque(false);
        detailSplit.setContinuousLayout(true);
        // 左欄共有 10 個重要度群組；高度必須容納完整清單，不能再以舊版 5 欄高度裁切。
        detailSplit.setPreferredSize(new Dimension(1200, 1_120));
        detailSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1_120));
        detailSplit.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** 建立 RMSE 收斂折線圖、演算法名稱與資料版本卡片。 */
    private JComponent buildConvergenceCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("訓練模型的細節"));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("訓練前後 RMSE 比較", 19f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(5));
        card.add(ViewSupport.wrappedText("比較訓練集平均值基準與完成模型的測試 RMSE；兩點比較不代表逐輪訓練曲線。",
                12f, UiTheme.MUTED));
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(10));
        card.add(new RmseChartPanel(result.epochRmseHistory(), result.summary().algorithm().contains("反向傳播") ? "Epoch" : "階段：0 基準／1 完成"));
        card.add(Box.createVerticalStrut(10));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(10));
        card.add(infoRow("訓練方法", result.summary().algorithm()));
        card.add(Box.createVerticalStrut(8));
        card.add(infoRow("資料版本", result.summary().dataVersion()));
        card.setPreferredSize(new Dimension(460, 440));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 440));
        return card;
    }

    /** 建立由 permutation importance 算出的特徵重要度進度條清單。 */
    private JComponent buildFeatureCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("特徵與其重要度"));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("Permutation importance", 19f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(5));
        card.add(ViewSupport.wrappedText("逐欄打亂測試資料，依 RMSE 增幅計算並正規化；不是臨床因果關係。",
                12f, UiTheme.MUTED));
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));
        // Service 回傳幾個特徵群組就建立幾列，不把百分比寫死在 View。
        for (FeatureImportance item : result.featureImportances()) {
            card.add(featureRow(item));
            card.add(Box.createVerticalStrut(10));
        }
        int featureCardHeight = 200 + result.featureImportances().size() * 42;
        card.setPreferredSize(new Dimension(460, featureCardHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, featureCardHeight));
        return card;
    }

    /** 將一筆 0 到 1 的重要度轉成名稱、百分比文字與 0 到 100 的進度條。 */
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

    /** 建立驗證案例 JTable、欄位標題、列數標籤及差值說明。 */
    private JComponent buildComparisonCard() {
        var card = card();
        var header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        var heading = ViewSupport.box(BoxLayout.Y_AXIS);
        heading.add(ViewSupport.sectionLabel("實際值與預測值的對照"));
        heading.add(Box.createVerticalStrut(5));
        heading.add(UiTheme.label("完整測試集明細", 19f, Font.BOLD, UiTheme.INK));
        header.add(heading, BorderLayout.CENTER);
        countBadge.setFont(UiTheme.font(Font.BOLD, 10f));
        countBadge.setForeground(UiTheme.TEAL_DARK);
        countBadge.setOpaque(true);
        countBadge.setBackground(UiTheme.TEAL_PALE);
        countBadge.setBorder(UiTheme.padding(6, 10, 6, 10));
        header.add(countBadge, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        card.add(header);
        card.add(Box.createVerticalStrut(7));
        card.add(ViewSupport.wrappedText(
                "下表預設展示完整測試集，每一列都來自本次 WEKA 模型 的實際預測。",
                12f,
                UiTheme.MUTED
        ));
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(10));

        // JTable 不直接保存業務資料，而是向 ComparisonTableModel 詢問列數與儲存格內容。
        comparisonTable = new JTable(tableModel);
        var table = comparisonTable;
        table.setName("training-comparison-table");
        table.setRowHeight(25);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(221, 243, 238));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setPreferredSize(new Dimension(100, 30));
        table.getTableHeader().setBackground(new Color(238, 244, 248));
        table.getTableHeader().setForeground(new Color(64, 88, 111));
        var centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        // 四欄都使用置中的 cell renderer，讓數字比較容易掃讀。
        for (int column = 0; column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(centered);
        }
        comparisonTableScroll = new JScrollPane(table);
        comparisonTableScroll.setColumnHeaderView(table.getTableHeader());
        comparisonTableScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        comparisonTableScroll.setPreferredSize(new Dimension(620, 510));
        comparisonTableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 510));
        comparisonTableScroll.getVerticalScrollBar().setUnitIncrement(42);
        card.add(comparisonTableScroll);
        card.setPreferredSize(new Dimension(620, 690));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 690));
        return card;
    }

    /** 建立頁面底部的模型儲存與前往預測按鈕。 */
    private JComponent buildActions() {
        // 只保留透明的按鈕列，避免用白色卡片包住按鈕造成多餘大白框。
        var actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        UiTheme.styleButton(saveModelButton, new Color(226, 239, 250), new Color(35, 91, 139));
        UiTheme.styleButton(continueButton, UiTheme.TEAL, Color.WHITE);
        saveModelButton.setName("save-bpnn-model-button");
        continueButton.setName("continue-to-prediction-button");

        saveModelButton.addActionListener(event -> chooseAndSaveModel());
        continueButton.addActionListener(event -> openPrediction.run());

        actions.add(saveModelButton);
        actions.add(continueButton);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        return actions;
    }

    /**
     * 顯示標準檔案選擇視窗，讓使用者決定模型檔名稱與位置。
     * 若檔案已存在會再次確認，避免不小心覆蓋重要模型。
     */
    private void chooseAndSaveModel() {
        // 先自動產生不易重複且能辨認建立時間的名稱；使用者仍可在視窗中自行修改。
        var selectedFile = NativeFileDialogs.saveFile(
                this, "儲存 WEKA 模型", result.summary().modelName().replaceAll("[^A-Za-z0-9_-]", "_") + "_" + MODEL_FILE_TIME_FORMAT.format(LocalDateTime.now()) + ".model", "model");
        if (selectedFile.isEmpty()) {
            statusSink.accept("已取消儲存模型");
            return;
        }

        Path selected = selectedFile.get();
        if (Files.exists(selected)) {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    "模型檔已存在，確定要覆蓋嗎？\n" + selected.toAbsolutePath(),
                    "確認覆蓋模型",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (answer != JOptionPane.YES_OPTION) {
                statusSink.accept("未覆蓋既有模型檔");
                return;
            }
        }

        try {
            modelService.saveModel(selected);
            statusSink.accept("WEKA 模型已儲存：" + selected.toAbsolutePath());
            JOptionPane.showMessageDialog(
                    this,
                    "模型儲存成功。\n\n檔案包含：\n"
                            + "• 已訓練的 WEKA 模型\n"
                            + "• 欄位結構與 one-hot 編碼規則\n"
                            + "• 預測區間與訓練超參數\n\n"
                            + selected.toAbsolutePath(),
                    "模型儲存成功",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (RuntimeException exception) {
            statusSink.accept("模型儲存失敗");
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage(),
                    "無法儲存模型",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * 依指定時間建立預設模型檔名。把時間當參數傳入，可讓自動化測試使用固定時間驗證格式。
     *
     * @param savedAt 準備儲存模型的本機日期與時間
     * @return 例如 {@code WEKA_BPNN_20260908_153045.model}
     */
    static String defaultModelFileName(LocalDateTime savedAt) {
        Objects.requireNonNull(savedAt, "模型儲存時間不可為空");
        return "WEKA_BPNN_" + MODEL_FILE_TIME_FORMAT.format(savedAt) + ".model";
    }

    /** 建立本頁共用的白色圓角卡片骨架。 */
    private RoundedPanel card() {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(10, 10, 10, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /** 建立左側名稱、右側數值的單行資訊。 */
    private JComponent infoRow(String label, String value) {
        var row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(UiTheme.label(label, 11f, Font.PLAIN, UiTheme.MUTED), BorderLayout.WEST);
        row.add(UiTheme.label(value, 11f, Font.BOLD, UiTheme.TEXT), BorderLayout.EAST);
        return row;
    }

    /**
     * 依頁面寬度切換桌面版或窄版配置。
     *
     * @param width 目前頁面寬度（像素）
     */
    private void applyResponsiveLayout(int width) {
        detailSplit.setDividerLocation(.45);
    }

    /**
     * 將 PredictionComparison 清單轉成 JTable 可理解的列與欄。
     * 這個內部類別只服務本頁表格，因此不另外建立公開檔案。
     */
    private static final class ComparisonTableModel extends AbstractTableModel {
        /** 表格四欄的顯示名稱。 */
        private static final String[] COLUMNS = {
                "案例編號", "實際值（分鐘）", "預測值（分鐘）", "差值（實際－預測）"
        };
        /** 全部不可修改的案例資料。 */
        private final List<PredictionComparison> rows;
        /** 建立模型時保存完整測試案例，表格不再提供精簡模式。 */
        private ComparisonTableModel(List<PredictionComparison> rows) {
            this.rows = List.copyOf(rows);
        }

        /** @return 完整測試集列數 */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /** @return 固定四個欄位 */
        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        /** @return 指定欄位的中文標題 */
        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        /**
         * JTable 逐格呼叫此方法取得顯示內容。
         * 差值定義為實際時間減預測時間，並保留正負號。
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PredictionComparison item = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.caseId();
                case 1 -> String.format(Locale.TAIWAN, "%.2f", item.actualMinutes());
                case 2 -> String.format(Locale.TAIWAN, "%.2f", item.predictedMinutes());
                case 3 -> String.format(Locale.TAIWAN, "%+.2f 分鐘",
                        item.actualMinutes() - item.predictedMinutes());
                default -> "";
            };
        }
    }
}
