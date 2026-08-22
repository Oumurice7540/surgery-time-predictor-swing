package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.DemoMetadata;
import tw.edu.nkust.surgerytime.model.PredictionRequest;
import tw.edu.nkust.surgerytime.model.PredictionResult;
import tw.edu.nkust.surgerytime.service.MockPredictionService;

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
import javax.swing.Timer;
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
import java.text.ParseException;
import java.util.Objects;
import java.util.function.Consumer;

public final class SurgeryPredictionView extends JPanel {
    private static final int COMPACT_BREAKPOINT = 960;

    private final MockPredictionService predictionService;
    private final Consumer<String> statusSink;
    private final Runnable defaultButtonChanged;
    private final ScrollablePanel content = new ScrollablePanel();
    private final JScrollPane pageScroll;
    private final JSplitPane contentSplit = new JSplitPane();

    private final JComboBox<String> roomBox;
    private final JSpinner ageSpinner = new JSpinner(new SpinnerNumberModel(60, 1, 120, 1));
    private final JToggleButton femaleButton = new JToggleButton("女性 F", true);
    private final JToggleButton maleButton = new JToggleButton("男性 M");
    private final JComboBox<String> procedureBox;
    private final JComboBox<String> surgeonBox;
    private final JButton loadButton = new JButton("1  載入模擬模型");
    private final JButton predictButton = new JButton("2  開始預測");
    private final JLabel modelStatus = new JLabel("尚未載入");
    private final RoundedPanel resultCard = new RoundedPanel(UiTheme.RESULT, null, 20);

    private boolean modelLoaded;
    private boolean compact;
    private PredictionResult lastResult;
    private PredictionRequest lastRequest;

    public SurgeryPredictionView(
            MockPredictionService predictionService,
            Consumer<String> statusSink,
            Runnable defaultButtonChanged
    ) {
        this.predictionService = Objects.requireNonNull(predictionService, "預測服務不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.defaultButtonChanged = Objects.requireNonNull(defaultButtonChanged, "預設按鈕更新動作不可為空");
        roomBox = new JComboBox<>(predictionService.supportedOperatingRooms().toArray(String[]::new));
        procedureBox = new JComboBox<>(predictionService.supportedProcedures().toArray(String[]::new));
        surgeonBox = new JComboBox<>(predictionService.supportedSurgeons().toArray(String[]::new));

        setName("surgery-prediction-page");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);
        configureControls();
        buildContent();
        pageScroll = ViewSupport.scroll(content);
        pageScroll.setName("prediction-page-scroll");
        add(pageScroll, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                applyResponsiveLayout(getWidth());
            }
        });
        statusSink.accept("請先載入 CSV 模擬模型，再確認手術條件");
    }

    public JButton defaultButton() {
        if (!modelLoaded && loadButton.isEnabled()) {
            return loadButton;
        }
        return predictButton.isEnabled() ? predictButton : null;
    }

    public void scrollToTop() {
        pageScroll.getVerticalScrollBar().setValue(0);
    }

    public void scrollToBottom() {
        pageScroll.getVerticalScrollBar().setValue(pageScroll.getVerticalScrollBar().getMaximum());
    }

    public void loadModelForPreview() {
        loadButton.doClick();
    }

    public void predictForPreview() {
        predictButton.doClick();
    }

    private void configureControls() {
        for (JComponent component : new JComponent[]{roomBox, ageSpinner, procedureBox, surgeonBox}) {
            UiTheme.styleInput(component);
            component.setPreferredSize(new Dimension(420, 43));
        }
        roomBox.setName("operating-room");
        ageSpinner.setName("patient-age");
        procedureBox.setName("procedure");
        surgeonBox.setName("surgeon");
        roomBox.getAccessibleContext().setAccessibleName("手術室別");
        ageSpinner.getAccessibleContext().setAccessibleName("病患年齡，1 到 120 歲");
        procedureBox.getAccessibleContext().setAccessibleName("手術名稱");
        surgeonBox.getAccessibleContext().setAccessibleName("主治醫師");
        procedureBox.setToolTipText("完整手術名稱會保留在 CSV 與下拉選單中");
        if (ageSpinner.getEditor() instanceof JSpinner.NumberEditor editor) {
            JFormattedTextField field = editor.getTextField();
            field.setColumns(3);
            field.setHorizontalAlignment(SwingConstants.LEFT);
        }

        var sexGroup = new ButtonGroup();
        sexGroup.add(femaleButton);
        sexGroup.add(maleButton);
        UiTheme.styleButton(femaleButton, Color.WHITE, UiTheme.TEAL_DARK);
        UiTheme.styleButton(maleButton, new Color(237, 242, 247), UiTheme.MUTED);
        femaleButton.addActionListener(event -> refreshSexStyles());
        maleButton.addActionListener(event -> refreshSexStyles());

        UiTheme.styleButton(loadButton, UiTheme.TEAL, Color.WHITE);
        UiTheme.styleButton(predictButton, new Color(237, 243, 248), new Color(41, 67, 94));
        loadButton.setName("load-model-button");
        predictButton.setName("predict-button");
        loadButton.setMnemonic('L');
        predictButton.setMnemonic('P');
        predictButton.setEnabled(false);
        loadButton.addActionListener(event -> loadMockModel());
        predictButton.addActionListener(event -> predict());
    }

    private void buildContent() {
        content.setBackground(UiTheme.BACKGROUND);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(UiTheme.padding(22, 34, 26, 34));
        content.add(buildHeading());
        content.add(Box.createVerticalStrut(15));
        content.add(buildWorkflow());
        content.add(Box.createVerticalStrut(16));

        var insight = ViewSupport.box(BoxLayout.Y_AXIS);
        insight.add(buildResultCard());
        insight.add(Box.createVerticalStrut(16));
        insight.add(buildModelCard());
        contentSplit.setLeftComponent(buildFormCard());
        contentSplit.setRightComponent(insight);
        contentSplit.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        contentSplit.setResizeWeight(0.59);
        contentSplit.setDividerLocation(0.59);
        contentSplit.setDividerSize(14);
        contentSplit.setBorder(null);
        contentSplit.setOpaque(false);
        contentSplit.setContinuousLayout(true);
        contentSplit.setPreferredSize(new Dimension(1200, 560));
        contentSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 560));
        contentSplit.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(contentSplit);
    }

    private JComponent buildHeading() {
        var panel = ViewSupport.box(BoxLayout.Y_AXIS);
        var titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        titleLine.add(UiTheme.label("手術時間預測", 29f, Font.BOLD, UiTheme.INK));
        var step = UiTheme.label("步驟 2 / 2", 11f, Font.BOLD, UiTheme.TEAL_DARK);
        step.setOpaque(true);
        step.setBackground(UiTheme.TEAL_PALE);
        step.setBorder(UiTheme.padding(5, 10, 5, 10));
        titleLine.add(step);
        panel.add(titleLine);
        panel.add(Box.createVerticalStrut(5));
        panel.add(ViewSupport.wrappedText(
                "依序載入 CSV 模擬資料、確認五項手術條件，再取得預測分鐘與合理區間。",
                13f,
                UiTheme.MUTED
        ));
        ViewSupport.fullWidth(panel);
        return panel;
    }

    private JComponent buildWorkflow() {
        var guide = new RoundedPanel(new Color(232, 244, 248), new Color(207, 227, 237), 14);
        guide.setLayout(new BorderLayout());
        guide.setBorder(UiTheme.padding(10, 14, 10, 14));
        var steps = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        steps.setOpaque(false);
        steps.add(workflowStep("1", "載入 CSV"));
        steps.add(UiTheme.label("→", 14f, Font.BOLD, new Color(139, 160, 180)));
        steps.add(workflowStep("2", "確認條件"));
        steps.add(UiTheme.label("→", 14f, Font.BOLD, new Color(139, 160, 180)));
        steps.add(workflowStep("3", "查看結果"));
        guide.add(steps, BorderLayout.WEST);
        guide.add(UiTheme.label("全程使用模擬資料", 11f, Font.PLAIN, UiTheme.MUTED), BorderLayout.EAST);
        guide.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        guide.setAlignmentX(Component.LEFT_ALIGNMENT);
        return guide;
    }

    private JComponent workflowStep(String number, String text) {
        var step = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        step.setOpaque(false);
        var numberLabel = UiTheme.label(number, 10f, Font.BOLD, UiTheme.TEAL_DARK);
        numberLabel.setOpaque(true);
        numberLabel.setBackground(UiTheme.TEAL_PALE);
        numberLabel.setHorizontalAlignment(SwingConstants.CENTER);
        numberLabel.setPreferredSize(new Dimension(23, 23));
        step.add(numberLabel);
        step.add(UiTheme.label(text, 12f, Font.BOLD, new Color(54, 83, 107)));
        return step;
    }

    private JComponent buildFormCard() {
        var card = card();
        card.add(ViewSupport.sectionLabel("預測條件"));
        card.add(Box.createVerticalStrut(5));
        card.add(UiTheme.label("確認本次手術資料", 20f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(5));
        card.add(ViewSupport.wrappedText(
                "欄位已由 CSV 帶入示範資料；先載入資料，再視需要調整後執行預測。",
                12f,
                UiTheme.MUTED
        ));
        card.add(Box.createVerticalStrut(13));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));

        var grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        addFormRow(grid, 0, "手術室別", "ROOM", roomBox);
        addFormRow(grid, 1, "病患年齡", "AGE", ageSpinner);
        var sexSelector = new JPanel(new GridLayout(1, 2, 0, 0));
        sexSelector.setOpaque(false);
        sexSelector.add(femaleButton);
        sexSelector.add(maleButton);
        addFormRow(grid, 2, "生理性別", "SEX", sexSelector);
        addFormRow(grid, 3, "手術名稱", "PROCEDURE", procedureBox);
        addFormRow(grid, 4, "主治醫師", "SURGEON", surgeonBox);
        card.add(grid);
        card.add(Box.createVerticalStrut(12));

        var privacy = new RoundedPanel(new Color(237, 250, 247), null, 12);
        privacy.setLayout(new FlowLayout(FlowLayout.LEFT, 9, 8));
        privacy.add(UiTheme.label("●", 11f, Font.BOLD, UiTheme.TEAL));
        privacy.add(UiTheme.label("本頁僅使用 CSV 模擬資料，不會儲存或傳送病患資訊",
                11f, Font.PLAIN, new Color(66, 117, 111)));
        privacy.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        card.add(privacy);
        card.add(Box.createVerticalStrut(12));

        var actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(loadButton, BorderLayout.WEST);
        actions.add(predictButton, BorderLayout.EAST);
        card.add(actions);
        return card;
    }

    private void addFormRow(JPanel grid, int row, String labelText, String code, JComponent control) {
        var labelPanel = ViewSupport.box(BoxLayout.Y_AXIS);
        var fieldLabel = UiTheme.label(labelText, 13f, Font.BOLD, UiTheme.TEXT);
        fieldLabel.setLabelFor(control);
        labelPanel.add(fieldLabel);
        labelPanel.add(UiTheme.label(code, 9f, Font.BOLD, new Color(117, 140, 163)));
        var labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 0, 8, 18);
        grid.add(labelPanel, labelConstraints);

        var inputConstraints = new GridBagConstraints();
        inputConstraints.gridx = 1;
        inputConstraints.gridy = row;
        inputConstraints.weightx = 1;
        inputConstraints.fill = GridBagConstraints.HORIZONTAL;
        inputConstraints.insets = new Insets(6, 0, 8, 0);
        grid.add(control, inputConstraints);
    }

    private JComponent buildResultCard() {
        resultCard.setLayout(new BoxLayout(resultCard, BoxLayout.Y_AXIS));
        resultCard.setBorder(UiTheme.padding(20, 22, 20, 22));
        resultCard.setPreferredSize(new Dimension(420, 245));
        resultCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 245));
        resultCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshResultCard();
        return resultCard;
    }

    private void refreshResultCard() {
        resultCard.removeAll();
        resultCard.add(UiTheme.label("模擬預測結果", 11f, Font.BOLD, new Color(121, 234, 211)));
        resultCard.add(Box.createVerticalStrut(8));
        if (lastResult == null) {
            resultCard.add(UiTheme.label("尚未產生結果", 22f, Font.BOLD, Color.WHITE));
            resultCard.add(Box.createVerticalStrut(5));
            resultCard.add(UiTheme.label("完成載入與預測後，結果會顯示在這裡",
                    11f, Font.PLAIN, new Color(186, 208, 221)));
        } else {
            var valueLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            valueLine.setOpaque(false);
            valueLine.add(UiTheme.label(Integer.toString(lastResult.predictedMinutes()), 48f, Font.BOLD, Color.WHITE));
            valueLine.add(UiTheme.label("分鐘", 14f, Font.PLAIN, new Color(205, 224, 234)));
            resultCard.add(valueLine);
            resultCard.add(UiTheme.label(
                    "合理區間  " + lastResult.lowerBoundMinutes() + "–" + lastResult.upperBoundMinutes() + " 分鐘",
                    11f, Font.PLAIN, new Color(213, 231, 240)
            ));
            resultCard.add(Box.createVerticalStrut(5));
            resultCard.add(UiTheme.label(
                    "模擬信心度  " + Math.round(lastResult.confidence() * 100) + "%",
                    11f, Font.PLAIN, new Color(213, 231, 240)
            ));
            resultCard.add(Box.createVerticalStrut(8));
            var confidence = new JProgressBar(0, 100);
            confidence.setValue((int) Math.round(lastResult.confidence() * 100));
            confidence.setForeground(new Color(57, 212, 181));
            confidence.setBackground(new Color(47, 98, 120));
            confidence.setBorderPainted(false);
            confidence.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7));
            resultCard.add(confidence);
            resultCard.add(Box.createVerticalStrut(9));
            String procedure = lastRequest.procedure().split("｜", 2)[0];
            resultCard.add(UiTheme.label(
                    lastRequest.operatingRoom() + " · " + procedure + " · " + lastRequest.surgeon(),
                    10f, Font.PLAIN, new Color(210, 232, 240)
            ));
        }
        resultCard.add(Box.createVerticalGlue());
        resultCard.add(UiTheme.label("模擬結果僅供介面展示，不可用於臨床判斷",
                9f, Font.PLAIN, new Color(159, 196, 210)));
        resultCard.revalidate();
        resultCard.repaint();
    }

    private JComponent buildModelCard() {
        var card = card();
        var heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.add(UiTheme.label("使用中模型", 19f, Font.BOLD, UiTheme.INK), BorderLayout.WEST);
        modelStatus.setFont(UiTheme.FONT.deriveFont(Font.BOLD, 10f));
        modelStatus.setForeground(UiTheme.MUTED);
        modelStatus.setOpaque(true);
        modelStatus.setBackground(new Color(238, 242, 246));
        modelStatus.setBorder(UiTheme.padding(5, 9, 5, 9));
        heading.add(modelStatus, BorderLayout.EAST);
        card.add(heading);
        card.add(Box.createVerticalStrut(12));
        card.add(ViewSupport.separator());
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("模型名稱", "OR-Time Demo 1.0"));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("執行方式", "CSV 固定規則模擬（非 ML）"));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("輸入特徵", "5 項"));
        card.add(Box.createVerticalStrut(12));
        card.add(infoRow("資料版本", DemoMetadata.DATA_VERSION));
        card.add(Box.createVerticalStrut(14));
        var note = ViewSupport.wrappedText("完整模擬指標與測試案例請見「訓練結果」頁面。",
                10f, new Color(128, 101, 44));
        note.setOpaque(true);
        note.setBackground(UiTheme.WARNING);
        note.setBorder(UiTheme.padding(8, 9, 8, 9));
        card.add(note);
        card.setPreferredSize(new Dimension(420, 290));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 290));
        return card;
    }

    private RoundedPanel card() {
        var card = new RoundedPanel(Color.WHITE, UiTheme.BORDER, 18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(UiTheme.padding(22, 24, 22, 24));
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

    private void loadMockModel() {
        loadButton.setEnabled(false);
        loadButton.setText("正在讀取 CSV…");
        modelStatus.setText("載入中");
        statusSink.accept("正在讀取 CSV 模擬模型與固定示範參數");
        defaultButtonChanged.run();
        var timer = new Timer(650, event -> {
            modelLoaded = true;
            loadButton.setText("CSV 已載入");
            UiTheme.styleButton(loadButton, new Color(237, 243, 248), UiTheme.MUTED);
            modelStatus.setText("● 已就緒");
            modelStatus.setForeground(UiTheme.TEAL_DARK);
            modelStatus.setBackground(UiTheme.TEAL_PALE);
            predictButton.setEnabled(true);
            UiTheme.styleButton(predictButton, UiTheme.TEAL, Color.WHITE);
            statusSink.accept("CSV 模擬資料已就緒，請確認條件後開始預測");
            defaultButtonChanged.run();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void predict() {
        if (!modelLoaded) {
            showWarning("請先載入 CSV 模擬資料");
            return;
        }
        PredictionRequest request;
        try {
            ageSpinner.commitEdit();
            request = new PredictionRequest(
                    Objects.toString(roomBox.getSelectedItem(), ""),
                    ((Number) ageSpinner.getValue()).intValue(),
                    femaleButton.isSelected() ? femaleButton.getText() : maleButton.getText(),
                    Objects.toString(procedureBox.getSelectedItem(), ""),
                    Objects.toString(surgeonBox.getSelectedItem(), "")
            );
        } catch (ParseException | IllegalArgumentException exception) {
            showWarning(exception.getMessage() == null ? "請確認輸入資料" : exception.getMessage());
            return;
        }
        predictButton.setEnabled(false);
        predictButton.setText("正在預測…");
        statusSink.accept("正在依 CSV 模擬基準產生預測");
        defaultButtonChanged.run();
        var timer = new Timer(520, event -> {
            lastRequest = request;
            lastResult = predictionService.predict(request);
            refreshResultCard();
            predictButton.setText("重新預測");
            predictButton.setEnabled(true);
            statusSink.accept("預測完成；結果僅供 Swing 介面展示");
            defaultButtonChanged.run();
            if (!Boolean.getBoolean("surgerytime.suppressDialogs") && isShowing()) {
                showResultDialog();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void showResultDialog() {
        String message = String.join("\n",
                "室別：" + lastRequest.operatingRoom(),
                "年齡：" + lastRequest.age() + " 歲",
                "性別：" + lastRequest.sex(),
                "手術：" + lastRequest.procedure(),
                "醫師：" + lastRequest.surgeon(),
                "",
                "預測手術時間：" + lastResult.predictedMinutes() + " 分鐘",
                "合理區間：" + lastResult.lowerBoundMinutes() + "–" + lastResult.upperBoundMinutes() + " 分鐘",
                "",
                lastResult.explanation()
        );
        JOptionPane.showMessageDialog(this, message, "模擬預測結果", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWarning(String message) {
        if (!Boolean.getBoolean("surgerytime.suppressDialogs")) {
            JOptionPane.showMessageDialog(this, message, "無法執行預測", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void refreshSexStyles() {
        UiTheme.styleButton(
                femaleButton,
                femaleButton.isSelected() ? Color.WHITE : new Color(237, 242, 247),
                femaleButton.isSelected() ? UiTheme.TEAL_DARK : UiTheme.MUTED
        );
        UiTheme.styleButton(
                maleButton,
                maleButton.isSelected() ? Color.WHITE : new Color(237, 242, 247),
                maleButton.isSelected() ? UiTheme.TEAL_DARK : UiTheme.MUTED
        );
    }

    private void applyResponsiveLayout(int width) {
        boolean shouldCompact = width > 0 && width < COMPACT_BREAKPOINT;
        if (shouldCompact == compact && width > 0) {
            return;
        }
        compact = shouldCompact;
        contentSplit.setOrientation(compact ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT);
        contentSplit.setPreferredSize(new Dimension(900, compact ? 1_130 : 560));
        contentSplit.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 1_130 : 560));
        contentSplit.setResizeWeight(compact ? 0.53 : 0.59);
        contentSplit.setDividerLocation(compact ? 0.53 : 0.59);
        content.revalidate();
        content.repaint();
    }
}
