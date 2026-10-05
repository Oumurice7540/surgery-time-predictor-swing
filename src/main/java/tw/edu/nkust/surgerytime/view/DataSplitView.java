package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.SurgeryModelService;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** 第六頁：設定可重現的訓練集／測試集比例與亂數種子。 */
public final class DataSplitView extends WorkflowPagePanel {
    private final SurgeryModelService modelService;
    private final Consumer<String> statusSink;
    private final Runnable completed;
    private final JSpinner trainingPercent = new JSpinner(new SpinnerNumberModel(80, 60, 90, 5));
    private final JSpinner randomSeed = new JSpinner(new SpinnerNumberModel(20_260_704L, 1L, 999_999_999L, 1L));
    private final JLabel trainingCount = valueLabel();
    private final JLabel testCount = valueLabel();
    private ReferenceDataPreview preview;
    private final JLabel totalCount = valueLabel();

    public DataSplitView(SurgeryModelService modelService, Consumer<String> statusSink, Runnable completed) {
        super("資料拆分", "步驟 6 / 9");
        this.modelService = Objects.requireNonNull(modelService, "模型服務不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.completed = Objects.requireNonNull(completed, "完成動作不可為空");
        buildContent();
        trainingPercent.addChangeListener(event -> refreshCounts());
    }

    public void refreshData() {
        trainingPercent.setValue((int) Math.round(modelService.pendingTrainingRatio() * 100));
        randomSeed.setValue(modelService.pendingRandomSeed());
        refreshCounts();
    }

    private void buildContent() {
        var card=card();
        var toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,10,4));toolbar.setOpaque(false);
        UiTheme.styleInput(trainingPercent); UiTheme.styleInput(randomSeed);
        trainingPercent.setPreferredSize(new Dimension(100,32));randomSeed.setPreferredSize(new Dimension(160,32));
        toolbar.add(new JLabel("訓練集比例（%）"));toolbar.add(trainingPercent);
        toolbar.add(new JLabel("亂數種子"));toolbar.add(randomSeed);
        var applyButton=new JButton("套用資料拆分");UiTheme.styleButton(applyButton,UiTheme.TEAL,Color.WHITE);
        applyButton.addActionListener(e->apply());toolbar.add(applyButton);card.add(toolbar);
        var counts=new JPanel(new FlowLayout(FlowLayout.LEFT,12,4));counts.setOpaque(false);
        counts.add(new JLabel("全部病例"));counts.add(totalCount);counts.add(new JLabel("訓練集"));counts.add(trainingCount);
        counts.add(new JLabel("測試集"));counts.add(testCount);card.add(counts);
        preview=new ReferenceDataPreview(modelService,true,javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        preview.setPreferredSize(new Dimension(1100,500));card.add(preview);
        card.setPreferredSize(new Dimension(1100,600));content.add(card);
        randomSeed.addChangeListener(e->refreshCounts());
    }

    private void refreshCounts() {
        int total = modelService.generatedDataCount();
        int training = (int) Math.round(total * ((Number) trainingPercent.getValue()).doubleValue() / 100.0);
        totalCount.setText(String.format(Locale.TAIWAN, "%,d 筆", total));
        trainingCount.setText(String.format(Locale.TAIWAN, "%,d 筆", training));
        testCount.setText(String.format(Locale.TAIWAN, "%,d 筆", total - training));
        if(preview!=null)preview.refresh(((Number)trainingPercent.getValue()).doubleValue()/100.0,((Number)randomSeed.getValue()).longValue());
    }

    private void apply() {
        try {
            trainingPercent.commitEdit();
            randomSeed.commitEdit();
            double ratio = ((Number) trainingPercent.getValue()).doubleValue() / 100.0;
            long seed = ((Number) randomSeed.getValue()).longValue();
            modelService.configureSplit(ratio, seed);
            statusSink.accept("資料拆分設定已保存；下一步調整 BPNN 超參數");
            completed.run();
        } catch (Exception exception) {
            javax.swing.JOptionPane.showMessageDialog(this, exception.getMessage(), "無法套用拆分設定",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel metric(String title, JLabel value) {
        var panel = new RoundedPanel(UiTheme.INPUT, UiTheme.BORDER, 14);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(UiTheme.padding(14, 15, 14, 15));
        panel.add(UiTheme.label(title, 10f, Font.BOLD, UiTheme.MUTED));
        panel.add(Box.createVerticalStrut(7));
        panel.add(value);
        return panel;
    }

    private static JLabel valueLabel() {
        return UiTheme.label("0 筆", 19f, Font.BOLD, UiTheme.INK);
    }
}
