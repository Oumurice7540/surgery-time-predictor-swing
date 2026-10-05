package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.TrainingConfiguration;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration.Algorithm;
import javax.swing.JComboBox;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** 第七頁只設定演算法並訓練前面流程已套用的資料。耗時工作由 SwingWorker 執行。 */
public final class MethodSettingsView extends JPanel {
    /** 視窗小於此寬度時，左右卡片改成上下排列。 */
    private static final int COMPACT_BREAKPOINT = 960;

    private final SurgeryModelService modelService;
    private final Runnable trainingCompleted;
    private final Consumer<String> statusSink;
    private final Runnable defaultButtonChanged;
    private final ScrollablePanel content = new ScrollablePanel();
    private final JScrollPane pageScroll;
    private final JSplitPane contentSplit = new JSplitPane();

    /** 單一隱藏層的神經元數量；60 對應參考簡報的預設值。 */
    private final JSpinner hiddenNeuronSpinner =
            new JSpinner(new SpinnerNumberModel(16, 1, 128, 1));
    /** Epoch、Alpha、Eta 及區間目標都會真正傳入 WEKA，不是畫面裝飾。 */
    private final JSpinner epochsSpinner =
            new JSpinner(new SpinnerNumberModel(500, 50, 2_000, 50));
    /** Alpha 是 momentum（動量），延續前一次權重更新的方向。 */
    private final JSpinner momentumAlphaSpinner =
            new JSpinner(new SpinnerNumberModel(0.20, 0.0, 0.90, 0.05));
    /** Eta 是 learning rate（學習率），控制每次修正權重的步幅。 */
    private final JSpinner learningRateSpinner =
            new JSpinner(new SpinnerNumberModel(0.10, 0.001, 1.0, 0.01));
    private final JSpinner intervalPercentSpinner =
            new JSpinner(new SpinnerNumberModel(90, 80, 99, 1));
    private final JComboBox<Algorithm> algorithmCombo = new JComboBox<>(Algorithm.values());
    private final JSpinner treeCountSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 10));
    private final JSpinner maxDepthSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JSpinner ridgeSpinner = new JSpinner(new SpinnerNumberModel(1e-8, 0.0, 100.0, .000001));
    private final JSpinner costSpinner = new JSpinner(new SpinnerNumberModel(1.0, .001, 10000.0, .1));
    private final JSpinner gammaSpinner = new JSpinner(new SpinnerNumberModel(.01, .000001, 100.0, .01));
    private final JButton compareAllButton = new JButton("以目前設定訓練四種方法");
    private final JButton trainButton = new JButton("執行模型訓練");
    private final JLabel trainingDataStatusLabel = new JLabel();
    private final JLabel workflowSettingsLabel = new JLabel();
    private final JLabel trainingDataPathLabel = new JLabel();
    private final JLabel currentModelLabel = new JLabel();
    private final JLabel currentSamplesLabel = new JLabel();
    private final JLabel currentMetricsLabel = new JLabel();
    private final JLabel currentHyperparametersLabel = new JLabel();
    private ReferenceDataPreview dataPreview;
    private boolean compact;
    private boolean busy;

    /**
     * @param modelService 三頁共用的模型服務
     * @param trainingCompleted 訓練成功後要求外框重建結果與預測頁
     * @param statusSink 更新頁尾狀態
     * @param defaultButtonChanged 通知外框重新指定 Enter 鍵按鈕
     */
    public MethodSettingsView(
            SurgeryModelService modelService,
            Runnable trainingCompleted,
            Consumer<String> statusSink,
            Runnable defaultButtonChanged
    ) {
        this.modelService = Objects.requireNonNull(modelService, "模型服務不可為空");
        this.trainingCompleted = Objects.requireNonNull(trainingCompleted, "訓練完成動作不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.defaultButtonChanged = Objects.requireNonNull(defaultButtonChanged, "預設按鈕更新動作不可為空");

        setName("method-settings-page");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        configureControls();
        algorithmCombo.setPreferredSize(new Dimension(220,32));
        for (var spinner : new JSpinner[]{treeCountSpinner,maxDepthSpinner,ridgeSpinner,costSpinner,gammaSpinner}) {
            UiTheme.styleInput(spinner); spinner.setPreferredSize(new Dimension(220,32));
        }
        var savedMethod = modelService.algorithmConfiguration();
        treeCountSpinner.setValue(savedMethod.treeCount()); maxDepthSpinner.setValue(savedMethod.maxDepth());
        ridgeSpinner.setValue(savedMethod.ridge()); costSpinner.setValue(savedMethod.cost()); gammaSpinner.setValue(savedMethod.gamma());
        algorithmCombo.setName("training-algorithm"); treeCountSpinner.setName("forest-tree-count");
        maxDepthSpinner.setName("forest-max-depth"); ridgeSpinner.setName("linear-ridge");
        costSpinner.setName("svr-cost"); gammaSpinner.setName("svr-gamma");
        ridgeSpinner.setEditor(new JSpinner.NumberEditor(ridgeSpinner, "0.##########"));
        gammaSpinner.setEditor(new JSpinner.NumberEditor(gammaSpinner, "0.######"));
        algorithmCombo.addActionListener(e -> updateAlgorithmControls());
        algorithmCombo.setSelectedItem(modelService.algorithmConfiguration().algorithm());
        updateAlgorithmControls();
        buildContent();
        updateAlgorithmControls();
        pageScroll = ViewSupport.scroll(content);
        pageScroll.setName("method-settings-scroll");
        add(pageScroll, BorderLayout.CENTER);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                applyResponsiveLayout(getWidth());
            }
        });
        refreshCurrentModel();
        refreshTrainingDataState();
    }

    /** @return 有可用資料時，Enter 只觸發模型訓練。 */
    public JButton defaultButton() { return trainButton.isEnabled() ? trainButton : null; }

    /** 將捲軸移回頁首。 */
    public void scrollToTop() {
        pageScroll.getVerticalScrollBar().setValue(0);
    }

    /** 更新前面流程已套用的資料與拆分摘要。 */
    public void refreshPreparedData() { refreshTrainingDataState(); }
    public void syncFromWorkflow() { refreshTrainingDataState(); }

    /** 將捲軸移到頁尾，主要供窄版視覺測試檢查下方內容。 */
    public void scrollToBottom() {
        pageScroll.getVerticalScrollBar().setValue(pageScroll.getVerticalScrollBar().getMaximum());
    }

    /** 供自動化視覺預覽呼叫，效果等同使用者按下訓練按鈕。 */
    public void trainForPreview() {
        trainButton.doClick();
    }

    /** 套用輸入元件樣式、輔助名稱與按鈕事件。 */
    private void configureControls() {
        for (JSpinner spinner : new JSpinner[]{
                hiddenNeuronSpinner,
                epochsSpinner, momentumAlphaSpinner, learningRateSpinner, intervalPercentSpinner
        }) {
            UiTheme.styleInput(spinner);
            spinner.setPreferredSize(new Dimension(220, 32));
        }
        hiddenNeuronSpinner.setName("hidden-neuron-count");
        epochsSpinner.setName("training-epochs");
        momentumAlphaSpinner.setName("momentum-alpha");
        learningRateSpinner.setName("learning-rate");
        intervalPercentSpinner.setName("prediction-interval-percent");
        hiddenNeuronSpinner.getAccessibleContext().setAccessibleName("BPNN 隱藏層神經元數量");
        epochsSpinner.getAccessibleContext().setAccessibleName("訓練 Epoch 數量");
        momentumAlphaSpinner.getAccessibleContext().setAccessibleName("BPNN Alpha 動量");
        learningRateSpinner.getAccessibleContext().setAccessibleName("BPNN Eta 學習率");
        intervalPercentSpinner.getAccessibleContext().setAccessibleName("預測區間目標百分比");

        // 指定小數顯示格式，避免 Spinner 顯示過長的小數位。
        momentumAlphaSpinner.setEditor(new JSpinner.NumberEditor(momentumAlphaSpinner, "0.00"));
        learningRateSpinner.setEditor(new JSpinner.NumberEditor(learningRateSpinner, "0.000"));

        // ToolTip 補充極端數值的影響，游標停在輸入欄位上即可看到。
        hiddenNeuronSpinner.setToolTipText("允許 1–128；建議 8–64。越多可學較複雜關係，但較慢且可能過度擬合");
        epochsSpinner.setToolTipText("建議 300–1,000；太少可能學不完，太多會增加訓練時間");
        momentumAlphaSpinner.setToolTipText("建議 0.10–0.80；太高可能使權重更新來回震盪");
        learningRateSpinner.setToolTipText("建議 0.05–0.30；太大可能不穩定，太小會學得很慢");
        intervalPercentSpinner.setToolTipText("建議 90–95%；百分比越高，預測區間通常越寬");

        UiTheme.styleButton(trainButton, UiTheme.TEAL, Color.WHITE);
        trainButton.setName("train-model-button");
        trainButton.setMnemonic('T');
        trainButton.addActionListener(event -> startTraining());

        configureValueLabel(trainingDataStatusLabel);
        configureValueLabel(workflowSettingsLabel);
        trainingDataStatusLabel.setName("training-data-status");
        trainingDataPathLabel.setName("training-data-path");
        workflowSettingsLabel.setName("workflow-settings-status");
        trainingDataPathLabel.setFont(UiTheme.font(Font.PLAIN, 9f));
        trainingDataPathLabel.setForeground(UiTheme.MUTED);
        trainingDataPathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** 建立頁首、主要左右內容與底部資料倫理說明。 */
    private void buildContent() {
        content.setBackground(UiTheme.BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(UiTheme.padding(8, 8, 8, 8));



        var settings=buildSettingsCard(); settings.setMinimumSize(new Dimension(480,540));
        contentSplit.setLeftComponent(settings);
        dataPreview=new ReferenceDataPreview(modelService,true);
        contentSplit.setRightComponent(dataPreview);
        contentSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        contentSplit.setResizeWeight(0.48);
        contentSplit.setDividerLocation(0.48);
        // 隱藏 JSplitPane 預設的點狀拖曳柱，畫面只保留左右內容。
        contentSplit.setDividerSize(0);
        // 關閉分割面板的操作狀態，防止使用者拖動隱形的分隔位置。
        contentSplit.setEnabled(false);
        contentSplit.setFocusable(false);
        contentSplit.setBorder(null);
        contentSplit.setOpaque(false);
        contentSplit.setContinuousLayout(true);
        // 保留模型參數、資料狀態及兩種訓練操作所需高度。
        contentSplit.setPreferredSize(new Dimension(1200, 565));
        contentSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        contentSplit.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(contentSplit);
    }

    /** 建立頁面標題與三頁工作流程中的步驟標籤。 */
    private JComponent buildHeading() {
        var panel = ViewSupport.box(BoxLayout.Y_AXIS);
        var titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.add(UiTheme.label("方法設定", 29f, Font.BOLD, UiTheme.INK));
        var step = UiTheme.label("步驟 7 / 9", 11f, Font.BOLD, UiTheme.TEAL_DARK);
        step.setOpaque(true);
        step.setBackground(UiTheme.TEAL_PALE);
        step.setBorder(UiTheme.padding(5, 10, 5, 10));
        titleLine.add(step);
        panel.add(titleLine);
        ViewSupport.fullWidth(panel);
        return panel;
    }

    /** 建立資料參數、模型超參數及執行按鈕。 */
    private JComponent buildSettingsCard() {
        var card = card();
        card.add(UiTheme.label("WEKA 模型設定", 20f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(14));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));

        var grid = new JPanel(new GridBagLayout());
        parameterGrid=grid;
        grid.setOpaque(false);
        addSettingRow(grid, 4, "隱藏層神經元", "允許 1–128；建議 8–64", hiddenNeuronSpinner);
        addSettingRow(grid, 5, "迭代次數 Epoch", "允許 50–2,000；建議 300–1,000", epochsSpinner);
        addSettingRow(grid, 6, "Alpha（動量）", "允許 0–0.90；建議 0.10–0.80", momentumAlphaSpinner);
        addSettingRow(grid, 7, "Eta（學習率）", "允許 0.001–1.0；建議 0.05–0.30", learningRateSpinner);
        addSettingRow(grid, 8, "預測區間目標", "允許 80–99%；建議 90–95%", intervalPercentSpinner);
        addSettingRow(grid, 0, "演算法", "啟用適用參數；共用資料拆分", algorithmCombo);
        addSettingRow(grid, 9, "森林樹數", "RandomForest：1–1000", treeCountSpinner);
        addSettingRow(grid, 10, "樹最大深度", "RandomForest：0 不限制；1–100", maxDepthSpinner);
        addSettingRow(grid, 11, "Ridge", "線性迴歸正則化：0–100", ridgeSpinner);
        addSettingRow(grid, 12, "C", "SVR 懲罰係數：0.001–10000", costSpinner);
        addSettingRow(grid, 13, "Gamma", "SVR RBF 核：0.000001–100", gammaSpinner);
        // GridBagLayout 預設會在額外高度中把整個網格置中；限制最大高度後，第一列會緊接標題。
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(grid);

        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));
        card.add(UiTheme.label("目前資料狀態", 14f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(8));
        card.add(trainingDataStatusLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(trainingDataPathLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(workflowSettingsLabel);

        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));
        card.add(UiTheme.label("使用目前資料訓練", 14f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(8));
        trainButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(trainButton);
        card.add(Box.createVerticalStrut(8));
        compareAllButton.setName("train-all-methods"); compareAllButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        compareAllButton.addActionListener(e -> startTraining(true)); card.add(compareAllButton);
        return card;
    }

    /** 在 GridBagLayout 中加入參數名稱、說明及輸入元件。 */
    private void addSettingRow(JPanel grid, int row, String name, String helper, JComponent input) {
        var text = ViewSupport.box(BoxLayout.Y_AXIS);
        text.add(UiTheme.label(name, 13f, Font.BOLD, UiTheme.TEXT));
        text.add(UiTheme.label(helper, 9f, Font.PLAIN, UiTheme.MUTED));

        var left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.weightx = 1;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(3, 0, 3, 12);
        grid.add(text, left);

        var right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(3, 0, 3, 0);
        grid.add(input, right);settingLabels.put(input,text);
    }

    /** 建立右側的流程說明與目前模型摘要。 */
    private JComponent buildExplanationColumn() {
        var column = ViewSupport.box(BoxLayout.Y_AXIS);
        column.add(buildPipelineCard());
        column.add(Box.createVerticalStrut(16));
        column.add(buildCurrentModelCard());
        return column;
    }

    /** 說明病例資料準備與模型訓練是兩個獨立動作。 */
    private JComponent buildPipelineCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("程式實際執行流程"));
        card.add(Box.createVerticalStrut(8));
        card.add(pipelineRow("1–2", "資料準備（第一、二頁）", "選擇來源、生成或匯入並完成轉檔"));
        card.add(Box.createVerticalStrut(12));
        card.add(pipelineRow("7", "使用目前資料訓練", "沿用已套用資料、特徵選取及拆分設定"));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 190));
        return card;
    }

    /** 建立單一流程步驟。 */
    private JComponent pipelineRow(String number, String title, String description) {
        var row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        var badge = UiTheme.label(number, 11f, Font.BOLD, Color.WHITE);
        badge.setOpaque(true);
        badge.setBackground(UiTheme.TEAL);
        badge.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        badge.setPreferredSize(new Dimension(30, 30));
        row.add(badge, BorderLayout.WEST);
        var text = ViewSupport.box(BoxLayout.Y_AXIS);
        text.add(UiTheme.label(title, 12f, Font.BOLD, UiTheme.TEXT));
        text.add(UiTheme.label(description, 10f, Font.PLAIN, UiTheme.MUTED));
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /** 建立目前已成功模型的摘要，訓練完成後會原地更新文字。 */
    private JComponent buildCurrentModelCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("目前可用模型"));
        card.add(Box.createVerticalStrut(9));
        configureValueLabel(currentModelLabel);
        configureValueLabel(currentSamplesLabel);
        configureValueLabel(currentMetricsLabel);
        configureValueLabel(currentHyperparametersLabel);
        card.add(currentModelLabel);
        card.add(Box.createVerticalStrut(7));
        card.add(currentSamplesLabel);
        card.add(Box.createVerticalStrut(7));
        card.add(currentMetricsLabel);
        card.add(Box.createVerticalStrut(7));
        card.add(currentHyperparametersLabel);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        return card;
    }

    /** 套用會動態改字的摘要標籤樣式。 */
    private void configureValueLabel(JLabel label) {
        label.setFont(UiTheme.font(Font.BOLD, 11f));
        label.setForeground(UiTheme.TEXT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    /** 執行前先提交 Spinner 文字並建立強型別 TrainingConfiguration。 */
    private TrainingConfiguration readConfiguration() throws ParseException {
        for (JSpinner spinner : new JSpinner[]{
                hiddenNeuronSpinner,
                epochsSpinner, momentumAlphaSpinner, learningRateSpinner, intervalPercentSpinner
        }) {
            if (spinner.isEnabled()) spinner.commitEdit();
        }
        return new TrainingConfiguration(
                modelService.generatedDataCount(),
                modelService.pendingTrainingRatio(),
                modelService.pendingRandomSeed(),
                ((Number) hiddenNeuronSpinner.getValue()).intValue(),
                ((Number) epochsSpinner.getValue()).intValue(),
                ((Number) momentumAlphaSpinner.getValue()).doubleValue(),
                ((Number) learningRateSpinner.getValue()).doubleValue(),
                ((Number) intervalPercentSpinner.getValue()).doubleValue() / 100.0
        );
    }

    private final java.util.Map<JComponent,JComponent> settingLabels=new java.util.LinkedHashMap<>();
    private JPanel parameterGrid;
    private void updateAlgorithmControls() {
        Algorithm algorithm = (Algorithm)algorithmCombo.getSelectedItem();
        algorithmCombo.setEnabled(!busy);
        for (var spinner : new JSpinner[]{hiddenNeuronSpinner, epochsSpinner, momentumAlphaSpinner, learningRateSpinner})
            spinner.setEnabled(!busy && algorithm == Algorithm.BPNN);
        treeCountSpinner.setEnabled(!busy && algorithm == Algorithm.RANDOM_FOREST);
        maxDepthSpinner.setEnabled(!busy && algorithm == Algorithm.RANDOM_FOREST);
        ridgeSpinner.setEnabled(!busy && algorithm == Algorithm.LINEAR_REGRESSION);
        costSpinner.setEnabled(!busy && algorithm == Algorithm.SVR);
        gammaSpinner.setEnabled(!busy && algorithm == Algorithm.SVR);
        if(!busy) settingLabels.forEach((input,label)->{boolean visible=input==algorithmCombo||input==intervalPercentSpinner||input.isEnabled();input.setVisible(visible);label.setVisible(visible);});
        if(parameterGrid!=null){parameterGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE,parameterGrid.getPreferredSize().height));parameterGrid.revalidate();content.revalidate();content.repaint();}
    }
    private AlgorithmConfiguration readAlgorithmConfiguration() throws ParseException {
        for (var spinner : new JSpinner[]{treeCountSpinner,maxDepthSpinner,ridgeSpinner,costSpinner,gammaSpinner})
            if (spinner.isEnabled()) spinner.commitEdit();
        return new AlgorithmConfiguration((Algorithm)algorithmCombo.getSelectedItem(),
                ((Number)treeCountSpinner.getValue()).intValue(), ((Number)maxDepthSpinner.getValue()).intValue(),
                ((Number)ridgeSpinner.getValue()).doubleValue(), ((Number)costSpinner.getValue()).doubleValue(),
                ((Number)gammaSpinner.getValue()).doubleValue());
    }

    /** 只使用前面流程已套用的資料；不重新生成或匯入。 */
    private void startTraining() { startTraining(false); }
    private void startTraining(boolean allMethods) {
        if (!modelService.hasGeneratedData()) {
            String message = "沒有可用資料，請回第一頁選擇資料來源，並在第二頁完成生成／轉檔。";
            statusSink.accept(message); showError(message); refreshTrainingDataState(); return;
        }
        final TrainingConfiguration requested;
        final AlgorithmConfiguration method;
        try {
            if (allMethods) for (var spinner : new JSpinner[]{hiddenNeuronSpinner,epochsSpinner,momentumAlphaSpinner,learningRateSpinner,treeCountSpinner,maxDepthSpinner,ridgeSpinner,costSpinner,gammaSpinner}) spinner.commitEdit();
            requested = readConfiguration();
            method = readAlgorithmConfiguration();
        } catch (ParseException | IllegalArgumentException exception) {
            showError(exception.getMessage() == null ? "請確認方法設定" : exception.getMessage());
            return;
        }

        if (!modelService.generatedDataMatches(requested.sampleCount(), requested.randomSeed())) {
            showError("資料已變更，請回第一、二頁確認已完成資料準備，再重新訓練");
            refreshTrainingDataState();
            return;
        }

        setBusy(true);
        trainButton.setText("正在訓練模型…");
        statusSink.accept("正在使用目前病例 CSV 訓練所選模型並計算測試指標");

        var worker = new SwingWorker<Void, String>() {
            @Override
            protected void process(java.util.List<String> messages) { statusSink.accept(messages.getLast()); }
            @Override
            protected Void doInBackground() {
                if (allMethods) {
                    modelService.trainAllAlgorithms(requested, method, this::publish);
                } else modelService.trainGeneratedData(requested, method);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshCurrentModel();
                    trainingCompleted.run();
                    statusSink.accept("重新訓練完成；訓練結果與預測頁已更新");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("訓練被中斷");
                } catch (ExecutionException exception) {
                    String message = exception.getCause() == null
                            ? exception.getMessage()
                            : exception.getCause().getMessage();
                    if (modelService.isTrained()) trainingCompleted.run();
                    showError("訓練失敗：" + message + "；此前成功紀錄仍保留於比較表");
                    statusSink.accept("訓練失敗；先前成功模型仍可使用");
                } finally {
                    trainButton.setText("執行模型訓練");
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    /** 顯示已套用資料與前面流程設定，不在本頁編輯資料來源。 */
    private void refreshTrainingDataState() {
        if(dataPreview!=null)dataPreview.refresh();
        boolean available = modelService.hasGeneratedData();
        boolean matches = modelService.generatedDataMatches(modelService.generatedDataCount(), modelService.pendingRandomSeed());
        Path csvPath = modelService.generatedCsvPath();
        trainButton.setEnabled(!busy && matches);
        compareAllButton.setEnabled(!busy && matches);
        if (!available) {
            trainingDataStatusLabel.setText("沒有可用資料，請回第一、二頁完成資料準備。");
            trainingDataPathLabel.setText("第一頁選擇來源 → 第二頁生成／轉檔");
            workflowSettingsLabel.setText("");
        } else {
            trainingDataStatusLabel.setText(String.format(Locale.TAIWAN,
                    "資料檔：%s · %,d 筆病例", csvPath == null ? "未提供檔名" : csvPath.getFileName(), modelService.generatedDataCount()));
            trainingDataPathLabel.setText("路徑：" + csvPath);
            trainingDataPathLabel.setToolTipText(csvPath == null ? "" : csvPath.toString());
            workflowSettingsLabel.setText(String.format(Locale.TAIWAN,
                    "已選 %d 項特徵 · 訓練 %.0f%% / 測試 %.0f%% · 種子 %d（沿用第五、六頁）",
                    modelService.selectedFeatureGroups().size(), modelService.pendingTrainingRatio()*100,
                    (1-modelService.pendingTrainingRatio())*100, modelService.pendingRandomSeed()));
        }
        defaultButtonChanged.run();
    }

    /** 統一鎖定或還原所有控制項，避免背景作業尚未完成時修改設定。 */
    private void setBusy(boolean newBusy) {
        busy = newBusy;
        for (JSpinner spinner : new JSpinner[]{
                hiddenNeuronSpinner,
                epochsSpinner, momentumAlphaSpinner, learningRateSpinner, intervalPercentSpinner
        }) {
            spinner.setEnabled(!busy);
        }
        updateAlgorithmControls();
        refreshTrainingDataState();
    }

    /** 從共用 Service 讀取最近結果並更新三行摘要。 */
    private void refreshCurrentModel() {
        if (!modelService.isTrained()) {
            currentModelLabel.setText("尚未建立模型");
            currentSamplesLabel.setText("請按左側按鈕開始");
            currentMetricsLabel.setText("尚無測試指標");
            currentHyperparametersLabel.setText("尚無超參數");
            return;
        }
        var summary = modelService.trainingResult().summary();
        currentModelLabel.setText(summary.modelName());
        currentSamplesLabel.setText(String.format(Locale.TAIWAN,
                "訓練 %,d 筆 · 測試 %,d 筆 · 編碼後 %d 欄",
                summary.trainingSampleCount(), summary.validationSampleCount(), summary.encodedFeatureCount()));
        currentMetricsLabel.setText(String.format(Locale.TAIWAN,
                "MAE %.1f 分鐘 · RMSE %.1f 分鐘 · R² %.3f",
                summary.meanAbsoluteErrorMinutes(), summary.rootMeanSquaredErrorMinutes(), summary.rSquared()));
        currentHyperparametersLabel.setText(modelService.algorithmConfiguration().description(modelService.configuration()));
    }

    /** 顯示設定或訓練錯誤；視覺預覽模式會抑制對話框。 */
    private void showError(String message) {
        if (!Boolean.getBoolean("surgerytime.suppressDialogs")) {
            JOptionPane.showMessageDialog(this, message, "無法完成操作", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 建立本頁共用白色圓角卡片。 */
    private RoundedPanel card() {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(10, 10, 10, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /** 依頁面寬度切換左右或上下配置。 */
    private void applyResponsiveLayout(int width) {
        boolean shouldCompact = width > 0 && width < COMPACT_BREAKPOINT;
        if (shouldCompact == compact && width > 0) {
            return;
        }
        compact = shouldCompact;
        contentSplit.setOrientation(compact ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
        contentSplit.setPreferredSize(new Dimension(900, compact ? 1_150 : 565));
        contentSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 1_150 : 565));
        contentSplit.setResizeWeight(compact ? 0.55 : 0.48);
        // 窄版以像素固定左側表單高度；寬版仍依視窗寬度按 48% 分割左右欄。
        if (compact) {
            contentSplit.setDividerLocation(565);
        } else {
            contentSplit.setDividerLocation(0.48);
        }
        content.revalidate();
        content.repaint();
    }
}
