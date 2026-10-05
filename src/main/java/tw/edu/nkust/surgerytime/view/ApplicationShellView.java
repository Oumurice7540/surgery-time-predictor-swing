package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.SurgeryModelService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 整個應用程式的共用外框。
 *
 * <p>九個頁面共用同一個 {@link SurgeryModelService}，因此從資料匯入、處理、
 * 特徵選取、訓練到預測都使用同一份狀態。程式啟動時不會自動產生資料或訓練；
 * 使用者可自由切換頁面；只有各頁面的實際動作會依資料或模型狀態提供提示。</p>
 */
public final class ApplicationShellView extends JPanel {
    /** 可顯示的九個工作流程頁面。 */
    public enum Page {
        DATA_SOURCE, DATA_CONVERSION, DATA_STATISTICS, DATA_PROCESSING,
        FEATURE_SELECTION, DATA_SPLIT, METHOD_SETTINGS, TRAINING_RESULTS, PREDICTION
    }

    private static final String SOURCE_CARD = "source";
    private static final String CONVERSION_CARD = "conversion";
    private static final String STATISTICS_CARD = "statistics";
    private static final String PROCESSING_CARD = "processing";
    private static final String FEATURES_CARD = "features";
    private static final String SPLIT_CARD = "split";
    private static final String SETTINGS_CARD = "settings";
    private static final String TRAINING_CARD = "training";
    private static final String PREDICTION_CARD = "prediction";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pageCards = new JPanel(cardLayout);
    private final JButton sourceButton = new JButton("資料來源");
    private final JButton conversionButton = new JButton("資料轉檔");
    private final JButton statisticsButton = new JButton("資料統計");
    private final JButton processingButton = new JButton("資料處理");
    private final JButton featuresButton = new JButton("特徵選取");
    private final JButton splitButton = new JButton("資料拆分");
    private final JButton settingsButton = new JButton("方法設定");
    private final JButton trainingButton = new JButton("訓練結果");
    private final JButton predictionButton = new JButton("手術時間預測");
    private final JLabel footerStatus = UiTheme.label("", 11f, Font.PLAIN, new Color(86, 107, 129));
    private final JLabel footerPage = UiTheme.label("", 10f, Font.BOLD, new Color(138, 154, 171));
    private final Map<Page, String> statusByPage = new EnumMap<>(Page.class);

    private final JLabel referencePage = new JLabel();
    private final SurgeryModelService modelService;
    private final DataSourceView sourceView;
    private final DataConversionView conversionView;
    private final DataStatisticsView statisticsView;
    private final DataProcessingView processingView;
    private final FeatureSelectionView featureSelectionView;
    private final DataSplitView splitView;
    private final MethodSettingsView settingsView;
    /** 尚未訓練時仍可開啟訓練結果頁，這張卡會說明目前沒有結果。 */
    private final JPanel trainingPlaceholder;
    private TrainingResultsView trainingView;
    private SurgeryPredictionView predictionView;
    private Page currentPage;

    /** 建立完整工作流程，但不自動執行任何資料處理或訓練。 */
    public ApplicationShellView(SurgeryModelService modelService) {
        this.modelService = Objects.requireNonNull(modelService, "模型服務不可為空");
        setName("application-shell");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        initializePageStatuses();

        sourceView = new DataSourceView(
                status -> setStatus(Page.DATA_SOURCE, status), this::handleSourceSelected);
        conversionView = new DataConversionView(
                modelService, sourceView::selection,
                status -> setStatus(Page.DATA_CONVERSION, status), this::handleConversionCompleted);
        statisticsView = new DataStatisticsView(
                modelService, status -> setStatus(Page.DATA_STATISTICS, status), this::handleStatisticsCompleted);
        processingView = new DataProcessingView(
                modelService, status -> setStatus(Page.DATA_PROCESSING, status), this::handleProcessingCompleted);
        featureSelectionView = new FeatureSelectionView(
                modelService, status -> setStatus(Page.FEATURE_SELECTION, status), this::handleFeaturesCompleted);
        splitView = new DataSplitView(
                modelService, status -> setStatus(Page.DATA_SPLIT, status), this::handleSplitCompleted);
        settingsView = new MethodSettingsView(
                modelService, this::handleTrainingCompleted,
                status -> setStatus(Page.METHOD_SETTINGS, status), this::refreshDefaultButton);
        trainingPlaceholder = buildTrainingPlaceholder();

        if (modelService.isTrained()) {
            createResultPages();
        } else {
            // 即使尚未訓練，也先建立安全的預測頁，讓使用者可直接載入模型。
            predictionView = createPredictionPage();
        }
        sourceView.attachPreparedPreview(modelService);
        buildView();
        showPage(modelService.isTrained() ? Page.METHOD_SETTINGS : Page.DATA_SOURCE);
    }

    private void initializePageStatuses() {
        statusByPage.put(Page.DATA_SOURCE, "請選擇資料夾、CSV 檔案或系統虛擬病例");
        statusByPage.put(Page.DATA_CONVERSION, "等待資料來源；本頁只轉檔，不會訓練模型");
        statusByPage.put(Page.DATA_STATISTICS, "轉檔完成後可依不同欄位檢視實際統計");
        statusByPage.put(Page.DATA_PROCESSING, "依欄位篩選、IQR 處理與儲存可重用步驟");
        statusByPage.put(Page.FEATURE_SELECTION, "選取真正送入 WEKA 模型 的欄位");
        statusByPage.put(Page.DATA_SPLIT, "設定訓練集比例及可重現亂數種子");
        statusByPage.put(Page.METHOD_SETTINGS, "調整 演算法與適用參數後由使用者執行訓練");
        statusByPage.put(Page.TRAINING_RESULTS, "完成 模型訓練後即可查看結果");
        statusByPage.put(Page.PREDICTION, "完成 模型訓練後即可進行預測");
    }

    public Page currentPage() { return currentPage; }
    public DataSourceView sourceView() { return sourceView; }
    public DataConversionView conversionView() { return conversionView; }
    public DataStatisticsView statisticsView() { return statisticsView; }
    public DataProcessingView processingView() { return processingView; }
    public FeatureSelectionView featureSelectionView() { return featureSelectionView; }
    public DataSplitView splitView() { return splitView; }
    public MethodSettingsView settingsView() { return settingsView; }
    public TrainingResultsView trainingView() { return trainingView; }
    public SurgeryPredictionView predictionView() { return predictionView; }

    /** 切換頁面；工作流程導覽不限制操作順序。 */
    public void showPage(Page requestedPage) {
        Objects.requireNonNull(requestedPage, "頁面不可為空");
        switch (requestedPage) {
            case DATA_SOURCE -> sourceView.refreshPreparedPreview();
            case DATA_STATISTICS -> statisticsView.refreshData();
            case DATA_PROCESSING -> processingView.refreshData();
            case FEATURE_SELECTION -> featureSelectionView.refreshData();
            case DATA_SPLIT -> splitView.refreshData();
            case METHOD_SETTINGS -> settingsView.refreshPreparedData();
            default -> { }
        }
        currentPage = requestedPage;
        cardLayout.show(pageCards, cardName(requestedPage));
        footerPage.setText(pageTitle(requestedPage));
        referencePage.setText("目前頁面為「" + pageTitle(requestedPage) + "」");
        footerStatus.setText(statusByPage.get(requestedPage));
        refreshNavigation();
        refreshDefaultButton();
        SwingUtilities.invokeLater(this::scrollCurrentPageToTop);
    }

    /** 讓 Enter 鍵只觸發目前頁面的主要操作。 */
    public void refreshDefaultButton() {
        SwingUtilities.invokeLater(() -> {
            JRootPane rootPane = SwingUtilities.getRootPane(this);
            if (rootPane == null) return;
            rootPane.setDefaultButton(switch (currentPage) {
                case DATA_CONVERSION -> conversionView.defaultButton();
                case METHOD_SETTINGS -> settingsView.defaultButton();
                case TRAINING_RESULTS -> trainingView == null ? null : trainingView.defaultButton();
                case PREDICTION -> predictionView == null ? null : predictionView.defaultButton();
                default -> null;
            });
        });
    }

    private void buildView() {
        add(buildReferenceHeader(), BorderLayout.NORTH);
        pageCards.setBackground(UiTheme.BACKGROUND);
        pageCards.add(sourceView, SOURCE_CARD);
        pageCards.add(conversionView, CONVERSION_CARD);
        pageCards.add(statisticsView, STATISTICS_CARD);
        pageCards.add(processingView, PROCESSING_CARD);
        pageCards.add(featureSelectionView, FEATURES_CARD);
        pageCards.add(splitView, SPLIT_CARD);
        pageCards.add(settingsView, SETTINGS_CARD);
        pageCards.add(trainingView == null ? trainingPlaceholder : trainingView, TRAINING_CARD);
        pageCards.add(predictionView, PREDICTION_CARD);
        add(pageCards, BorderLayout.CENTER);
        var bottom = new JPanel(new BorderLayout());
        bottom.add(buildFooter(), BorderLayout.NORTH);
        bottom.add(buildNavigation(), BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildReferenceHeader() {
        var header = new JPanel(new BorderLayout(18, 0));
        header.setName("reference-header"); header.setBackground(new Color(230, 234, 251));
        header.setBorder(UiTheme.padding(12, 18, 12, 18));
        var brand = new JPanel(new java.awt.GridLayout(2,1)); brand.setOpaque(false);
        brand.add(UiTheme.label("「醫」刻千金・料「術」如神", 21f, Font.BOLD, UiTheme.INK));
        brand.add(UiTheme.label("手術時間預測系統", 12f, Font.PLAIN, UiTheme.MUTED));
        header.add(brand, BorderLayout.WEST); referencePage.setForeground(UiTheme.INK);
        header.add(referencePage, BorderLayout.EAST); return header;
    }

    private JPanel buildNavigation() {
        var nav = new JPanel(new java.awt.GridLayout(1,9,4,0));
        nav.setName("reference-workflow-navigation"); nav.setBackground(new Color(230,234,251));
        nav.setBorder(UiTheme.padding(6,8,6,8)); nav.setPreferredSize(new Dimension(900,48));
        JButton[] buttons = {sourceButton,conversionButton,statisticsButton,processingButton,featuresButton,splitButton,settingsButton,trainingButton,predictionButton};
        Page[] pages=Page.values();
        for(int i=0;i<buttons.length;i++){configureNavButton(buttons[i],pages[i]);nav.add(buttons[i]);}
        return nav;
    }

    private void configureNavButton(JButton button, Page page) {
        button.setFont(UiTheme.font(Font.BOLD, 12f));
        button.setBorder(UiTheme.padding(5, 3, 5, 3));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> showPage(page));
    }

    private JPanel buildFooter() {
        var footer = new JPanel(new BorderLayout());
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER), UiTheme.padding(9, 28, 9, 28)));
        var status = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        status.setOpaque(false);
        status.add(UiTheme.label("●", 9f, Font.BOLD, UiTheme.TEAL));
        status.add(footerStatus);
        footer.add(status, BorderLayout.WEST);
        footer.add(footerPage, BorderLayout.EAST);
        footer.setPreferredSize(new Dimension(900, 30));
        return footer;
    }

    private void refreshNavigation() {
        // 導覽只負責切頁，所有九頁一律可選；資料完整性由各頁操作按鈕自行檢查。
        sourceButton.setEnabled(true);
        conversionButton.setEnabled(true);
        statisticsButton.setEnabled(true);
        processingButton.setEnabled(true);
        featuresButton.setEnabled(true);
        splitButton.setEnabled(true);
        settingsButton.setEnabled(true);
        trainingButton.setEnabled(true);
        predictionButton.setEnabled(true);
        styleNavButton(sourceButton, currentPage == Page.DATA_SOURCE);
        styleNavButton(conversionButton, currentPage == Page.DATA_CONVERSION);
        styleNavButton(statisticsButton, currentPage == Page.DATA_STATISTICS);
        styleNavButton(processingButton, currentPage == Page.DATA_PROCESSING);
        styleNavButton(featuresButton, currentPage == Page.FEATURE_SELECTION);
        styleNavButton(splitButton, currentPage == Page.DATA_SPLIT);
        styleNavButton(settingsButton, currentPage == Page.METHOD_SETTINGS);
        styleNavButton(trainingButton, currentPage == Page.TRAINING_RESULTS);
        styleNavButton(predictionButton, currentPage == Page.PREDICTION);
    }

    private void styleNavButton(JButton button, boolean selected) {
        button.setOpaque(true);
        button.setBackground(selected ? UiTheme.INK : Color.WHITE);
        button.setForeground(selected ? Color.WHITE : UiTheme.TEXT);
    }

    private String cardName(Page page) {
        return switch (page) {
            case DATA_SOURCE -> SOURCE_CARD;
            case DATA_CONVERSION -> CONVERSION_CARD;
            case DATA_STATISTICS -> STATISTICS_CARD;
            case DATA_PROCESSING -> PROCESSING_CARD;
            case FEATURE_SELECTION -> FEATURES_CARD;
            case DATA_SPLIT -> SPLIT_CARD;
            case METHOD_SETTINGS -> SETTINGS_CARD;
            case TRAINING_RESULTS -> TRAINING_CARD;
            case PREDICTION -> PREDICTION_CARD;
        };
    }

    private String pageTitle(Page page) {
        return switch (page) {
            case DATA_SOURCE -> "資料來源";
            case DATA_CONVERSION -> "資料轉檔";
            case DATA_STATISTICS -> "資料統計";
            case DATA_PROCESSING -> "資料處理";
            case FEATURE_SELECTION -> "特徵選取";
            case DATA_SPLIT -> "資料拆分";
            case METHOD_SETTINGS -> "方法設定";
            case TRAINING_RESULTS -> "訓練結果 · TEST METRICS";
            case PREDICTION -> "手術時間預測 · WEKA 模型";
        };
    }

    private void setStatus(Page page, String status) {
        statusByPage.put(page, status);
        if (currentPage == page) footerStatus.setText(status);
    }

    private void scrollCurrentPageToTop() {
        switch (currentPage) {
            case DATA_SOURCE -> sourceView.scrollToTop();
            case DATA_CONVERSION -> conversionView.scrollToTop();
            case DATA_STATISTICS -> statisticsView.scrollToTop();
            case DATA_PROCESSING -> processingView.scrollToTop();
            case FEATURE_SELECTION -> featureSelectionView.scrollToTop();
            case DATA_SPLIT -> splitView.scrollToTop();
            case METHOD_SETTINGS -> settingsView.scrollToTop();
            case TRAINING_RESULTS -> { if (trainingView != null) trainingView.scrollToTop(); }
            case PREDICTION -> { if (predictionView != null) predictionView.scrollToTop(); }
        }
    }

    private void handleSourceSelected() {
        conversionView.refreshSource(); // Keep source selection visible for individual report preview.
    }

    private void handleConversionCompleted() {
        statisticsView.refreshData();
        showPage(Page.DATA_STATISTICS);
    }

    private void handleStatisticsCompleted() {
        processingView.refreshData();
        showPage(Page.DATA_PROCESSING);
    }

    private void handleProcessingCompleted() {
        featureSelectionView.refreshData();
        showPage(Page.FEATURE_SELECTION);
    }

    private void handleFeaturesCompleted() {
        splitView.refreshData();
        showPage(Page.DATA_SPLIT);
    }

    private void handleSplitCompleted() {
        settingsView.syncFromWorkflow();
        showPage(Page.METHOD_SETTINGS);
    }

    private void createResultPages() {
        trainingView = new TrainingResultsView(
                modelService, () -> showPage(Page.PREDICTION),
                status -> setStatus(Page.TRAINING_RESULTS, status), this::handleTrainingCompleted);
        predictionView = createPredictionPage();
    }

    private SurgeryPredictionView createPredictionPage() {
        return new SurgeryPredictionView(
                modelService, status -> setStatus(Page.PREDICTION, status),
                this::refreshDefaultButton, this::handleModelLoaded);
    }

    private void replaceResultPages() {
        pageCards.remove(trainingView == null ? trainingPlaceholder : trainingView);
        if (predictionView != null) pageCards.remove(predictionView);
        createResultPages();
        pageCards.add(trainingView, TRAINING_CARD);
        pageCards.add(predictionView, PREDICTION_CARD);
        pageCards.revalidate();
        pageCards.repaint();
    }

    private void handleTrainingCompleted() {
        replaceResultPages();
        setStatus(Page.TRAINING_RESULTS, "WEKA 模型 訓練完成");
        setStatus(Page.PREDICTION, "模型已就緒，可輸入病例進行預測");
        showPage(Page.TRAINING_RESULTS);
    }

    private void handleModelLoaded() {
        replaceResultPages();
        showPage(Page.PREDICTION);
    }

    /** 建立沒有模型時的訓練結果空白狀態，頁面本身仍可自由開啟。 */
    private JPanel buildTrainingPlaceholder() {
        var page = new JPanel(new BorderLayout());
        page.setBackground(UiTheme.BACKGROUND);
        page.setBorder(UiTheme.padding(30, 34, 30, 34));
        var body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.setOpaque(false);
        var title = UiTheme.label("訓練結果", 29f, Font.BOLD, UiTheme.INK);
        title.setAlignmentX(LEFT_ALIGNMENT);
        body.add(title);
        body.add(javax.swing.Box.createVerticalStrut(18));
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(26, 28, 26, 28));
        card.add(ViewSupport.sectionLabel("WEKA 模型"));
        card.add(javax.swing.Box.createVerticalStrut(8));
        card.add(UiTheme.label("尚未有訓練結果", 22f, Font.BOLD, UiTheme.INK));
        card.add(javax.swing.Box.createVerticalStrut(8));
        card.add(ViewSupport.wrappedText(
                "可以自由檢視此頁。完成 模型訓練或在預測頁載入已儲存模型後，結果會顯示在這裡。",
                12f, UiTheme.MUTED));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        body.add(card);
        page.add(body, BorderLayout.NORTH);
        return page;
    }
}
