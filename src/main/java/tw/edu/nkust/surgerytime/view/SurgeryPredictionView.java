package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.PredictionRequest;
import tw.edu.nkust.surgerytime.model.PredictionResult;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 「手術時間預測」完整頁面。
 *
 * <p>本頁收集手術室、年齡、性別、手術名稱與醫師，再將資料包成
 * {@link PredictionRequest} 交給 {@link SurgeryModelService}。取得
 * {@link PredictionResult} 後，只負責把結果更新到 Swing 元件。</p>
 *
 * <p>預測確實使用方法設定頁訓練出的 WEKA 模型，但資料完全是人工生成，
 * 因此仍不是可供臨床使用的醫療工具。</p>
 */
public final class SurgeryPredictionView extends JPanel {
    /** 寬度小於 960 像素時，左右內容改為上下排列。 */
    private static final int COMPACT_BREAKPOINT = 960;

    /** 提供下拉選項與真正 WEKA 模型 預測的服務。 */
    private final SurgeryModelService predictionService;
    /** 將本頁操作狀態回傳給應用程式頁尾。 */
    private final Consumer<String> statusSink;
    /** 通知外框重新決定 Enter 鍵應觸發哪一個按鈕。 */
    private final Runnable defaultButtonChanged;
    /** 成功載入另一個模型後，通知外框重建結果與預測頁。 */
    private final Runnable modelLoaded;
    /** 頁面的縱向內容容器。 */
    private final ScrollablePanel content = new ScrollablePanel();
    /** 讓窄視窗可以捲動完整內容。 */
    private final JScrollPane pageScroll;
    /** 左側輸入表單與右側結果資訊的可調整分割容器。 */
    private final JSplitPane contentSplit = new JSplitPane();

    /** 手術室下拉選單，選項由 CSV 提供。 */
    private final JComboBox<String> roomBox;
    private final JComboBox<String> sexBox = new JComboBox<>();
    /** 年齡輸入，預設 60 歲，最小 1、最大 120、每次增減 1。 */
    private final JSpinner ageSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 120, 1));
    /** 女性切換按鈕，建立時預設選取。 */
    /** 男性切換按鈕。 */
    /** 手術名稱下拉選單，選項與基準時間來自 CSV。 */
    private final JComboBox<String> procedureBox;
    /** 主治醫師下拉選單。 */
    private final JComboBox<String> surgeonBox;
    /** 手術緊急程度。 */
    private final JComboBox<String> urgencyBox = new JComboBox<>(new String[]{"常規 Elective", "緊急 Emergency"});
    /** 虛擬 ASA 生理狀態分級。 */
    private final JComboBox<String> asaBox = new JComboBox<>(new String[]{"ASA I", "ASA II", "ASA III", "ASA IV"});
    /** 麻醉方式。 */
    private final JComboBox<String> anesthesiaBox = new JComboBox<>(new String[]{
            "全身麻醉 General", "區域麻醉 Regional", "鎮靜麻醉 Sedation"});
    /** 虛擬共病數量，範圍 0 到 4。 */
    private final JSpinner comorbiditySpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
    /** 手術複雜度。 */
    private final JComboBox<String> complexityBox = new JComboBox<>(new String[]{
            "標準 Standard", "複雜 Complex", "高複雜 High"});
    /** 收集輸入並呼叫已訓練模型。 */
    private final JButton predictButton = new JButton("開始模型預測");
    /** 從磁碟選擇本程式先前儲存的另一個 WEKA 模型。 */
    private final JButton loadModelButton = new JButton("載入模型");
    /** 顯示目前服務中的模型狀態。 */
    private final JLabel modelStatus = new JLabel();
    /** 與其他介面一致的淺色預測結果卡；內容會在預測完成後重建。 */
    private final RoundedPanel resultCard = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 20);

    /** 目前是否採用窄版排列。 */
    private boolean compact;
    /** 最近一次預測結果；尚未預測時為 null。 */
    private PredictionResult lastResult;
    /** 最近一次預測輸入；用於結果摘要及對話框。 */
    private PredictionRequest lastRequest;
    private final java.util.Map<String,JComponent> dynamicControls = new java.util.LinkedHashMap<>();
    private java.util.Map<String,String> lastDynamicValues;


    /**
     * 建立預測頁面並以服務中的 CSV 選項初始化下拉選單。
     *
     * @param predictionService 真正的訓練與預測服務
     * @param statusSink 更新頁尾狀態的回呼
     * @param defaultButtonChanged 通知外框更新 Enter 鍵預設按鈕的回呼
     * @param modelLoaded 成功換模型後重建相關頁面的回呼
     */
    public SurgeryPredictionView(
            SurgeryModelService predictionService,
            Consumer<String> statusSink,
            Runnable defaultButtonChanged,
            Runnable modelLoaded
    ) {
        this.predictionService = Objects.requireNonNull(predictionService, "預測服務不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.defaultButtonChanged = Objects.requireNonNull(defaultButtonChanged, "預設按鈕更新動作不可為空");
        this.modelLoaded = Objects.requireNonNull(modelLoaded, "模型載入完成動作不可為空");
        // JComboBox 建構子需要陣列，因此把 Service 回傳的 List 轉成 String[]。
        roomBox = new JComboBox<>(predictionService.supportedOperatingRooms().toArray(String[]::new));
        procedureBox = new JComboBox<>(predictionService.supportedProcedures().toArray(String[]::new));
        surgeonBox = new JComboBox<>(predictionService.supportedSurgeons().toArray(String[]::new));

        setOptions(sexBox, "生理性別", java.util.List.of("女性 F", "男性 M"));
        setOptions(urgencyBox, "緊急程度", java.util.List.of("常規 Elective", "緊急 Emergency"));
        setOptions(asaBox, "ASA 分級", java.util.List.of("ASA I", "ASA II", "ASA III", "ASA IV"));
        setOptions(anesthesiaBox, "麻醉方式", java.util.List.of("全身麻醉 General", "區域麻醉 Regional", "鎮靜麻醉 Sedation"));
        setOptions(complexityBox, "手術複雜度", java.util.List.of("標準 Standard", "複雜 Complex", "高複雜 High"));
        // setName 供自動化視覺測試辨識元件。
        setName("surgery-prediction-page");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        configureControls();
        buildContent();
        pageScroll = ViewSupport.scroll(content);
        pageScroll.setName("prediction-page-scroll");
        add(pageScroll, BorderLayout.CENTER);
        // 視窗尺寸改變時重新判斷是否切換窄版。
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyResponsiveLayout(getWidth());
            }
        });
        statusSink.accept(predictionService.isTrained()
                ? "WEKA 模型 已就緒，請確認模型所需的輸入條件"
                : "尚未載入模型；可直接按「載入模型」後進行預測");
    }

    /**
     * 決定使用者按 Enter 時執行預測；處理中按鈕停用時回傳 null。
     */
    public JButton defaultButton() {
        return predictButton.isEnabled() ? predictButton : null;
    }

    /** 將頁面捲到最上方。 */
    public void scrollToTop() {
        pageScroll.getVerticalScrollBar().setValue(0);
    }

    /** 將頁面捲到最下方，主要供視覺測試使用。 */
    public void scrollToBottom() {
        pageScroll.getVerticalScrollBar().setValue(pageScroll.getVerticalScrollBar().getMaximum());
    }

    /** 保留給既有視覺預覽程式；模型已由方法設定頁完成，因此不需額外載入。 */
    public void loadModelForPreview() {
        // 刻意不做任何事：SurgeryModelService 已保存最近一次成功模型。
    }

    /** 以程式方式按下預測按鈕，供 UI 預覽測試使用。 */
    public void predictForPreview() {
        predictButton.doClick();
    }

    /**
     * 一次設定所有輸入元件與按鈕的尺寸、樣式、無障礙名稱及事件。
     * 將設定集中可以保持建構子容易閱讀。
     */
    private void setOptions(JComboBox<String> box, String group, java.util.List<String> fallback) {
        box.setModel(new javax.swing.DefaultComboBoxModel<>(predictionService.predictionOptions(group, fallback).toArray(String[]::new)));
    }
    private void configureControls() {
        sexBox.setName("sex");
        // 所有下拉選單與數字輸入共用相同外觀及偏好尺寸。
        for (JComponent component : new JComponent[]{sexBox, roomBox, ageSpinner, procedureBox, surgeonBox,
                urgencyBox, asaBox, anesthesiaBox, comorbiditySpinner, complexityBox}) {
            UiTheme.styleInput(component);
            component.setPreferredSize(new Dimension(350, 32));
        }
        // name 供測試辨識；AccessibleName 供螢幕閱讀器描述元件用途。
        roomBox.setName("operating-room");
        ageSpinner.setName("patient-age");
        procedureBox.setName("procedure");
        surgeonBox.setName("surgeon");
        urgencyBox.setName("urgency");
        asaBox.setName("asa-class");
        anesthesiaBox.setName("anesthesia");
        comorbiditySpinner.setName("comorbidity-count");
        complexityBox.setName("complexity");
        roomBox.getAccessibleContext().setAccessibleName("手術室別");
        ageSpinner.getAccessibleContext().setAccessibleName("病患年齡，1 到 120 歲");
        procedureBox.getAccessibleContext().setAccessibleName("手術名稱");
        surgeonBox.getAccessibleContext().setAccessibleName("主治醫師");
        urgencyBox.getAccessibleContext().setAccessibleName("手術緊急程度");
        asaBox.getAccessibleContext().setAccessibleName("ASA 分級");
        anesthesiaBox.getAccessibleContext().setAccessibleName("麻醉方式");
        comorbiditySpinner.getAccessibleContext().setAccessibleName("共病數量，0 到 4");
        complexityBox.getAccessibleContext().setAccessibleName("手術複雜度");
        procedureBox.setToolTipText("完整手術名稱會保留在 CSV 與下拉選單中");
        loadModelButton.setToolTipText("僅載入由本程式儲存、且來源可信的 .model 檔案");
        loadModelButton.setName("load-bpnn-model-button");
        UiTheme.styleButton(loadModelButton, new Color(226, 239, 250), new Color(35, 91, 139));
        loadModelButton.addActionListener(event -> chooseAndLoadModel());
        // 取得 JSpinner 內部的文字欄位，使年齡靠左並限制適當寬度。
        if (ageSpinner.getEditor() instanceof JSpinner.NumberEditor editor) {
            JFormattedTextField field = editor.getTextField();
            field.setColumns(3);
            field.setHorizontalAlignment(SwingConstants.LEFT);
        }

        UiTheme.styleButton(predictButton, UiTheme.TEAL, Color.WHITE);
        predictButton.setName("predict-button");
        predictButton.addActionListener(event -> predict());
        predictButton.setEnabled(predictionService.isTrained());
    }

    /** 依序建立標題、輸入表單及右側資訊區。 */
    private void buildContent() {
        content.setBackground(UiTheme.BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(UiTheme.padding(8, 8, 8, 8));



        var insight = ViewSupport.box(BoxLayout.Y_AXIS);
        insight.add(buildModelCard());
        insight.add(Box.createVerticalStrut(16));
        insight.add(buildResultCard());
        contentSplit.setLeftComponent(buildFormCard());
        contentSplit.setRightComponent(insight);
        contentSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        contentSplit.setResizeWeight(0.5);
        contentSplit.setDividerLocation(0.5);
        // 隱藏 JSplitPane 預設的點狀拖曳柱，畫面只保留左右內容。
        contentSplit.setDividerSize(0);
        // 關閉分割面板的操作狀態，防止使用者拖動隱形的分隔位置。
        contentSplit.setEnabled(false);
        contentSplit.setFocusable(false);
        contentSplit.setBorder(null);
        contentSplit.setOpaque(false);
        contentSplit.setContinuousLayout(true);
        contentSplit.setPreferredSize(new Dimension(1200, 700));
        contentSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 700));
        contentSplit.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(contentSplit);
    }

    /** 建立預測頁的主標題、步驟標籤及用途說明。 */
    private JComponent buildHeading() {
        var panel = ViewSupport.box(BoxLayout.Y_AXIS);
        var titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        titleLine.add(UiTheme.label(predictionService.predictionTarget() + "預測", 29f, Font.BOLD, UiTheme.INK));
        var step = UiTheme.label("步驟 9 / 9", 11f, Font.BOLD, UiTheme.TEAL_DARK);
        step.setOpaque(true);
        step.setBackground(UiTheme.TEAL_PALE);
        step.setBorder(UiTheme.padding(5, 10, 5, 10));
        titleLine.add(step);
        panel.add(titleLine);
        ViewSupport.fullWidth(panel);
        return panel;
    }

    /** 建立十項預測條件與操作按鈕。 */
    private JComponent buildFormCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("預測條件"));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("確認本次手術資料", 20f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(5));
        card.add(ViewSupport.wrappedText(
                "僅顯示此模型使用的欄位；選項來自模型保存的資料類別。",
                12f,
                UiTheme.MUTED
        ));
        card.add(Box.createVerticalStrut(13));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));

        var grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        if(predictionService.predictionTransforms()!=null) {
            int index=0;
            for(var field:predictionService.predictionTransforms().fields()) {
                JComponent input;
                if(field.numeric()) input=new javax.swing.JTextField(Double.toString(field.impute()));
                else {var box=new JComboBox<String>(field.categories().toArray(String[]::new));box.setEditable(true);input=box;}
                input.setName("prediction-input:"+field.name()); UiTheme.styleInput(input);
                input.setPreferredSize(new Dimension(230,34)); dynamicControls.put(field.name(),input);
                addFormRow(grid,index++,field.name(),"",input);
            }
        } else {
        addFormRow(grid, 0, "手術室別", "ROOM", roomBox);
        addFormRow(grid, 1, "病患年齡", "AGE", ageSpinner);
        addFormRow(grid, 2, "生理性別", "SEX", sexBox);
        addFormRow(grid, 3, "手術名稱", "PROCEDURE", procedureBox);
        addFormRow(grid, 4, "主治醫師", "SURGEON", surgeonBox);
        addFormRow(grid, 5, "緊急程度", "URGENCY", urgencyBox);
        addFormRow(grid, 6, "ASA 分級", "ASA CLASS", asaBox);
        addFormRow(grid, 7, "麻醉方式", "ANESTHESIA", anesthesiaBox);
        addFormRow(grid, 8, "共病數量", "COMORBIDITY", comorbiditySpinner);
        addFormRow(grid, 9, "手術複雜度", "COMPLEXITY", complexityBox);
        }
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        card.add(grid);
        card.add(Box.createVerticalStrut(12));

        var actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(predictButton, BorderLayout.WEST);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        card.add(actions);
        return card;
    }

    /**
     * 使用 GridBagLayout 在指定列加入欄位名稱、英文代碼與輸入元件。
     *
     * @param grid 表單網格
     * @param row 要放入的列索引
     * @param labelText 使用者看到的中文欄位名稱
     * @param code 輔助辨識的英文代碼
     * @param control JComboBox、JSpinner 或性別按鈕容器
     */
    private void addFormRow(JPanel grid, int row, String labelText, String code, JComponent control) {
        String group = labelText.equals("主治醫師") ? "虛擬醫師" : labelText;
        if (predictionService.isTrained() && !predictionService.predictionFeatures().contains(group)) return;
        var labelPanel = ViewSupport.box(BoxLayout.Y_AXIS);
        var fieldLabel = UiTheme.label(labelText.replaceFirst("^extra:",""), 13f, Font.BOLD, UiTheme.TEXT);
        fieldLabel.setLabelFor(control);
        labelPanel.add(fieldLabel);

        var labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(3, 0, 3, 12);
        grid.add(labelPanel, labelConstraints);

        var inputConstraints = new GridBagConstraints();
        inputConstraints.gridx = 1;
        inputConstraints.gridy = row;
        inputConstraints.weightx = 1;
        inputConstraints.fill = GridBagConstraints.HORIZONTAL;
        inputConstraints.insets = new Insets(3, 0, 3, 0);
        grid.add(control, inputConstraints);
    }

    /** 建立淺色結果卡並依目前是否有結果填入初始內容。 */
    private JComponent buildResultCard() {
        resultCard.setLayout(new BoxLayout(resultCard, BoxLayout.Y_AXIS));
        resultCard.setBorder(UiTheme.padding(10, 10, 10, 10));
        // 放大卡片高度，為加大的結果文字保留足夠行距。
        resultCard.setPreferredSize(new Dimension(420, 245));
        resultCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 245));
        resultCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshResultCard();
        return resultCard;
    }

    /**
     * 清除舊內容，再根據 lastResult 重建結果卡。
     * 尚無結果時顯示操作提示；有結果時顯示分鐘、範圍、信心度與條件摘要。
     */
    private void refreshResultCard() {
        // removeAll 只移除卡片中的 Swing 子元件，不會刪除任何資料或檔案。
        resultCard.removeAll();
        resultCard.add(UiTheme.label(predictionService.predictionTarget() + "預測結果", 14f, Font.BOLD, UiTheme.TEAL_DARK));
        resultCard.add(Box.createVerticalStrut(10));
        if (lastResult == null) {
            resultCard.add(UiTheme.label(
                    predictionService.isTrained() ? "尚未產生結果" : "尚未載入模型",
                    26f, Font.BOLD, UiTheme.INK));
            if (!predictionService.isTrained()) {
                resultCard.add(Box.createVerticalStrut(8));
                resultCard.add(ViewSupport.wrappedText(
                        "可直接在下方載入已儲存的模型，不必先完成資料與訓練流程。",
                        12f, UiTheme.MUTED));
            }
        } else {
            var valueLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            valueLine.setOpaque(false);
            valueLine.add(UiTheme.label(Integer.toString(lastResult.predictedMinutes()), 54f, Font.BOLD, UiTheme.INK));
            valueLine.add(UiTheme.label("分鐘", 17f, Font.PLAIN, UiTheme.MUTED));
            resultCard.add(valueLine);
            resultCard.add(UiTheme.label(
                    intervalTargetPercent() + "% 預測區間  "
                            + lastResult.lowerBoundMinutes() + "–" + lastResult.upperBoundMinutes() + " 分鐘",
                    13f, Font.PLAIN, UiTheme.TEXT
            ));
            resultCard.add(Box.createVerticalStrut(7));
            resultCard.add(UiTheme.label(
                    "測試集區間涵蓋率  " + Math.round(lastResult.intervalCoverage() * 100) + "%",
                    13f, Font.PLAIN, UiTheme.TEXT
            ));
            resultCard.add(Box.createVerticalStrut(10));
            var coverageBar = new JProgressBar(0, 100);
            coverageBar.setValue((int) Math.round(lastResult.intervalCoverage() * 100));
            coverageBar.setForeground(UiTheme.TEAL);
            coverageBar.setBackground(new Color(232, 238, 244));
            coverageBar.setBorderPainted(false);
            coverageBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
            resultCard.add(coverageBar);
            resultCard.add(Box.createVerticalStrut(11));

            resultCard.add(UiTheme.label(
                    predictionService.predictionTarget() + "；使用 " + predictionService.predictionFeatures().size() + " 項輸入特徵",
                    12f, Font.PLAIN, UiTheme.MUTED
            ));
        }
        // 內容變動後要求 Layout 重新計算並重繪。
        resultCard.revalidate();
        resultCard.repaint();
    }

    /** 建立右側模型資訊卡，顯示最近一次真正訓練的摘要。 */
    private JComponent buildModelCard() {
        var card = card();
        var heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(UiTheme.label("使用中模型", 19f, Font.BOLD, UiTheme.INK), BorderLayout.WEST);
        modelStatus.setText(predictionService.isTrained() ? "● 已訓練" : "● 尚未載入");
        modelStatus.setFont(UiTheme.font(Font.BOLD, 10f));
        modelStatus.setForeground(predictionService.isTrained() ? UiTheme.TEAL_DARK : UiTheme.MUTED);
        modelStatus.setOpaque(true);
        modelStatus.setBackground(UiTheme.TEAL_PALE);
        modelStatus.setBorder(UiTheme.padding(5, 9, 5, 9));
        var modelActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        modelActions.setOpaque(false);
        modelActions.add(loadModelButton);
        modelActions.add(modelStatus);
        heading.add(modelActions, BorderLayout.EAST);
        card.add(heading);
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));
        if (!predictionService.isTrained()) {
            card.add(infoRow("模型狀態", "尚未載入"));
            card.add(Box.createVerticalStrut(12));
            card.add(infoRow("可用操作", "載入本程式儲存的 .model"));
            card.setPreferredSize(new Dimension(420, 180));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
            return card;
        }
        var summary = predictionService.trainingResult().summary();
        card.add(infoRow("模型名稱", summary.modelName()));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("模型來源", predictionService.modelSource()));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("執行方式", summary.algorithm()));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("輸入特徵", summary.featureCount() + " 群組／" + summary.encodedFeatureCount() + " 個編碼欄"));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("區間目標", intervalTargetPercent() + "% 測試殘差百分位"));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("資料版本", summary.dataVersion()));
        card.setPreferredSize(new Dimension(420, 315));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 315));
        return card;
    }

    /**
     * 開啟檔案選擇視窗並載入另一個已儲存 BPNN。成功後外框會重建本頁，
     * 因此右側模型名稱、超參數、資料版本與預測區間都會同步更新。
     */
    private void chooseAndLoadModel() {
        var selectedFiles = NativeFileDialogs.openFiles(
                this, "載入已儲存的 WEKA 模型", "model", false);
        if (selectedFiles.isEmpty()) {
            statusSink.accept("已取消載入模型");
            return;
        }
        Path selectedModel = selectedFiles.getFirst();

        try {
            predictionService.loadModel(selectedModel);
            lastRequest = null;
            lastResult = null;
            statusSink.accept("已切換至載入的 WEKA 模型：" + selectedModel);
            JOptionPane.showMessageDialog(
                    this,
                    "模型載入成功。\n後續預測會立即使用這個模型。\n\n"
                            + selectedModel,
                    "模型切換完成",
                    JOptionPane.INFORMATION_MESSAGE
            );
            modelLoaded.run();
        } catch (RuntimeException exception) {
            statusSink.accept("模型載入失敗；原本模型仍可使用");
            JOptionPane.showMessageDialog(
                    this,
                    exception.getMessage() + "\n\n請只選擇由本程式儲存且來源可信的 .model 檔。",
                    "無法載入模型",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /** 建立本頁共用的白色圓角卡片骨架。 */
    private RoundedPanel card() {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(10, 10, 10, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /** 建立左側標籤、右側值的模型資訊列。 */
    private JComponent infoRow(String label, String value) {
        var row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(UiTheme.label(label, 11f, Font.PLAIN, UiTheme.MUTED), BorderLayout.WEST);
        row.add(UiTheme.label(value, 11f, Font.BOLD, UiTheme.TEXT), BorderLayout.EAST);
        return row;
    }

    /**
     * 收集並驗證畫面輸入，再呼叫最近一次真正訓練的模型。
     */
    private void predict() {
        if(!dynamicControls.isEmpty()) {
            try {
                var values=new java.util.LinkedHashMap<String,String>();
                dynamicControls.forEach((key,input)->values.put(key,input instanceof javax.swing.JTextField text ? text.getText() : Objects.toString(((JComboBox<?>)input).getEditor().getItem(),"")));
                lastResult=predictionService.predict(values); lastDynamicValues=java.util.Map.copyOf(values);
                refreshResultCard(); statusSink.accept("模型預測完成；沿用模型保存的字典與轉換參數");
                if(!Boolean.getBoolean("surgerytime.suppressDialogs")&&isShowing()) showResultDialog();
            } catch(RuntimeException e) {showWarning(e.getMessage());}
            return;
        }
        PredictionRequest request;
        try {
            // 先提交正在編輯的文字，使 spinner.getValue() 取得畫面最新數字。
            ageSpinner.commitEdit();
            comorbiditySpinner.commitEdit();
            // 把十個 Swing 元件值轉成與畫面無關的 PredictionRequest。
            request = new PredictionRequest(
                    Objects.toString(roomBox.getSelectedItem(), ""),
                    ((Number) ageSpinner.getValue()).intValue(),
                    Objects.toString(sexBox.getSelectedItem(), ""),
                    Objects.toString(procedureBox.getSelectedItem(), ""),
                    Objects.toString(surgeonBox.getSelectedItem(), ""),
                    Objects.toString(urgencyBox.getSelectedItem(), ""),
                    Objects.toString(asaBox.getSelectedItem(), ""),
                    Objects.toString(anesthesiaBox.getSelectedItem(), ""),
                    ((Number) comorbiditySpinner.getValue()).intValue(),
                    Objects.toString(complexityBox.getSelectedItem(), "")
            );
        } catch (ParseException | IllegalArgumentException exception) {
            // 格式或 Model 驗證失敗時，只顯示警告，不執行 Service。
            showWarning(exception.getMessage() == null ? "請確認輸入資料" : exception.getMessage());
            return;
        }
        predictButton.setEnabled(false);
        predictButton.setText("正在預測…");
        statusSink.accept("正在套用編碼並交由已訓練的 WEKA 模型 計算");
        defaultButtonChanged.run();
        try {
            lastRequest = request;
            lastResult = predictionService.predict(request);
            refreshResultCard();
            predictButton.setText("重新預測");
            predictButton.setEnabled(true);
            statusSink.accept("模型預測完成；區間來自測試集第 "
                    + intervalTargetPercent() + " 百分位殘差");
            defaultButtonChanged.run();
            if (!Boolean.getBoolean("surgerytime.suppressDialogs") && isShowing()) {
                showResultDialog();
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            predictButton.setText("開始模型預測");
            predictButton.setEnabled(true);
            defaultButtonChanged.run();
            showWarning(exception.getMessage());
        }
    }

    /** 顯示最近一次輸入與結果的摘要對話框。 */
    private void showResultDialog() {
        var values = new java.util.LinkedHashMap<String, String>();
        if(lastDynamicValues!=null) values.putAll(lastDynamicValues);
        else {
        values.put("手術室別", lastRequest.operatingRoom());
        values.put("病患年齡", Integer.toString(lastRequest.age()));
        values.put("生理性別", lastRequest.sex());
        values.put("手術名稱", lastRequest.procedure());
        values.put("虛擬醫師", lastRequest.surgeon());
        values.put("緊急程度", lastRequest.urgency());
        values.put("ASA 分級", lastRequest.asaClass());
        values.put("麻醉方式", lastRequest.anesthesia());
        values.put("共病數量", Integer.toString(lastRequest.comorbidityCount()));
        values.put("手術複雜度", lastRequest.complexity());
        }
        var lines = new java.util.ArrayList<String>();
        values.forEach((key, value) -> {
            if (predictionService.predictionFeatures().contains(key)) lines.add((key.equals("虛擬醫師") ? "主治醫師" : key) + "：" + value);
        });
        lines.add("");
        lines.add("預測" + predictionService.predictionTarget() + "：" + lastResult.predictedMinutes() + " 分鐘");
        lines.add(intervalTargetPercent() + "% 預測區間：" + lastResult.lowerBoundMinutes() + "–" + lastResult.upperBoundMinutes() + " 分鐘");
        lines.add(lastResult.explanation());
        String message = String.join("\n", lines).replace("extra:","");
        var text=new javax.swing.JTextArea(message);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(UiTheme.FONT);var scroll=new JScrollPane(text);scroll.setPreferredSize(new Dimension(620,420));
        JOptionPane.showMessageDialog(this, scroll, "模型預測結果", JOptionPane.INFORMATION_MESSAGE);
    }

    /** 顯示輸入或操作順序錯誤；視覺測試模式會抑制對話框。 */
    private void showWarning(String message) {
        if (!Boolean.getBoolean("surgerytime.suppressDialogs")) {
            JOptionPane.showMessageDialog(this, message, "無法執行預測", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** @return 將設定中的 0.90 轉成畫面較容易閱讀的整數 90 */
    private int intervalTargetPercent() {
        return (int) Math.round(predictionService.configuration().predictionIntervalTarget() * 100);
    }

    /**
     * 寬版使用左右分割，窄版使用上下分割，並調整整體高度。
     *
     * @param width 預測頁目前寬度（像素）
     */
    private void applyResponsiveLayout(int width) {
        boolean shouldCompact = width > 0 && width < COMPACT_BREAKPOINT;
        // 只有跨越斷點時才重設版面，降低拖曳視窗時的額外計算。
        if (shouldCompact == compact && width > 0) {
            return;
        }
        compact = shouldCompact;
        contentSplit.setOrientation(compact ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
        contentSplit.setPreferredSize(new Dimension(900, compact ? 1_550 : 700));
        contentSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 1_550 : 700));
        contentSplit.setResizeWeight(compact ? 0.58 : 0.63);
        contentSplit.setDividerLocation(compact ? 0.58 : 0.63);
        // 通知 Swing 重新配置與繪製更新後的元件。
        content.revalidate();
        content.repaint();
    }
}
