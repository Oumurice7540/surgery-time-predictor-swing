package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.*;
import tw.edu.nkust.surgerytime.service.DataProcessingService.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/** 編輯處理步驟、預覽完整流程、確認套用；所有操作都以匯入快照為起點。 */
public final class DataProcessingView extends WorkflowPagePanel {
    private final SurgeryModelService modelService;
    private final Consumer<String> statusSink;
    private final Runnable completed;
    private final DataProcessingService processor = new DataProcessingService();
    private final JComboBox<ProcessingStep.Operation> operation = new JComboBox<>(ProcessingStep.Operation.values());
    private final JComboBox<ProcessingTable.Column> column = new JComboBox<>();
    private final JComboBox<String> value = new JComboBox<>();
    private final JTextField upper = new JTextField(10);
    private final JLabel valueLabel = new JLabel("比對值／下界／IQR 倍數");
    private final JLabel help = new JLabel();
    private final JLabel summary = new JLabel("請先匯入或生成資料。");
    private final DefaultTableModel stepModel = tableModel("順序", "操作", "欄位", "值／下界／倍數", "上界");
    private final JTable stepTable = new JTable(stepModel);
    private final DefaultTableModel beforeModel = tableModel();
    private final DefaultTableModel afterModel = tableModel();
    private final DefaultTableModel auditModel = tableModel("步驟", "操作", "處理前筆數", "處理後筆數", "移除筆數", "說明");
    private final List<ProcessingStep> steps = new ArrayList<>();
    private final List<JButton> editButtons = new ArrayList<>();
    private final JButton previewButton = new JButton("預覽全部步驟");
    private final JButton applyButton = new JButton("套用結果並繼續");
    private final JButton openButton = new JButton("開啟已套用 CSV");
    private SurgeryModelService.ProcessingPreview preview;
    private long shownRevision = -1;
    private boolean busy;
    private int draftVersion;

    public DataProcessingView(SurgeryModelService modelService, Consumer<String> statusSink, Runnable completed) {
        super("資料處理", "步驟 4 / 9");
        this.modelService = Objects.requireNonNull(modelService); this.statusSink = Objects.requireNonNull(statusSink);
        this.completed = Objects.requireNonNull(completed);
        setName("data-processing-page"); buildContent(); refreshData();
    }
    public void refreshData() {
        if (busy) return;
        long revision = modelService.processingRevision();
        if (revision != shownRevision) {
            shownRevision = revision; preview = null; draftVersion++;
            steps.clear(); steps.addAll(modelService.processingSteps());
            column.setModel(new DefaultComboBoxModel<>(modelService.processingOriginal().columns().toArray(ProcessingTable.Column[]::new)));
            updateStepTable(); fillTable(beforeModel, modelService.processingOriginal());
            showResult(modelService.processingCurrent());
            summary.setText(modelService.hasGeneratedData()
                    ? "原始 " + modelService.processingOriginal().rows().size() + " 筆；目前已套用 " + modelService.generatedDataCount() + " 筆。修改步驟後請先預覽。"
                    : "請先匯入或生成資料。");
            refreshValues();
        }
        updateEnabled();
    }
    private void buildContent() {
        value.setEditable(true); value.setPreferredSize(new Dimension(220, 32));
        column.setPreferredSize(new Dimension(240, 32)); operation.setPreferredSize(new Dimension(250, 32));
        operation.setName("processing-operation"); column.setName("processing-column");
        value.setName("processing-value"); upper.setName("processing-upper");
        stepTable.setName("processing-steps"); stepTable.setRowHeight(28); stepTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        var editor = card();
        editor.add(ViewSupport.sectionLabel("可重複使用的資料處理流程")); editor.add(Box.createVerticalStrut(8));
        var introduction = ViewSupport.wrappedText("篩選／刪欄先於數值轉換。探索預覽以全部資料估計；正式訓練的 IQR 僅清理訓練列，保留全部測試列，編碼與尺度亦僅用訓練集估計。", 12f, UiTheme.MUTED);
        introduction.setMinimumSize(new Dimension(0, 36)); editor.add(introduction);
        editor.add(Box.createVerticalStrut(8));
        editor.add(row(new JLabel("操作"), operation, new JLabel("欄位"), column));
        editor.add(row(valueLabel, value, new JLabel("範圍上界"), upper));
        help.setFont(UiTheme.font(Font.PLAIN, 12f)); help.setAlignmentX(LEFT_ALIGNMENT); editor.add(help);
        editor.add(row(button("新增步驟", "add-processing-step", () -> editStep(false)),
                button("更新選取步驟", "update-processing-step", () -> editStep(true)),
                button("上移", "move-step-up", () -> moveStep(-1)), button("下移", "move-step-down", () -> moveStep(1)),
                button("移除步驟", "remove-processing-step", this::removeStep)));
        var stepScroll = new JScrollPane(stepTable); stepScroll.setPreferredSize(new Dimension(1000, 165));
        stepScroll.setColumnHeaderView(stepTable.getTableHeader());
        stepScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 165)); stepScroll.setAlignmentX(LEFT_ALIGNMENT); editor.add(stepScroll);
        editor.add(row(button("儲存步驟", "save-processing-steps", this::saveSteps),
                button("載入步驟", "load-processing-steps", this::loadSteps),
                button("清空步驟／還原原始資料", "clear-processing-steps", () -> { steps.clear(); changed(); updateStepTable(); })));
        summary.setFont(UiTheme.font(Font.BOLD, 12f)); summary.setAlignmentX(LEFT_ALIGNMENT); editor.add(summary);
        editor.add(row(previewButton, applyButton, openButton));
        editor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 515)); editor.setPreferredSize(new Dimension(1100, 515));

        var results = card(); results.add(ViewSupport.sectionLabel("處理前後預覽（每個資料表顯示前 100 筆；CSV 包含全部結果）"));
        results.add(Box.createVerticalStrut(8));
        var tabs = new JTabbedPane(); tabs.setName("processing-preview-tabs");
        var beforePanel=new JPanel(new BorderLayout());beforePanel.add(new JLabel("原始匯入資料"),BorderLayout.NORTH);beforePanel.add(dataScroll(beforeModel,"processing-before"));
        var afterPanel=new JPanel(new BorderLayout());afterPanel.add(new JLabel("探索預覽／待訓練原始值（正式數值見第六頁）"),BorderLayout.NORTH);afterPanel.add(dataScroll(afterModel,"processing-after"));
        var tables=new JSplitPane(JSplitPane.VERTICAL_SPLIT,beforePanel,afterPanel);tables.setResizeWeight(.5);tables.setDividerLocation(.5);
        tabs.addTab("處理前後",tables);
        tabs.addTab("各步驟執行紀錄", dataScroll(auditModel, "processing-audit"));
        tabs.setPreferredSize(new Dimension(1050, 275)); tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 275)); results.add(tabs);
        results.setMaximumSize(new Dimension(Integer.MAX_VALUE, 345)); results.setPreferredSize(new Dimension(1100, 345));
        editor.setMinimumSize(new Dimension(600,515));results.setMinimumSize(new Dimension(330,345));
        results.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));
        tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,editor,results);
        split.setResizeWeight(.53);split.setDividerLocation(.53);split.setPreferredSize(new Dimension(1150,650));content.add(split);
        previewButton.setName("preview-processing"); applyButton.setName("apply-processing");
        previewButton.addActionListener(e -> previewSteps()); applyButton.addActionListener(e -> applyResult());
        openButton.addActionListener(e -> { try { Desktop.getDesktop().open(modelService.generatedCsvPath().toFile()); } catch (Exception ex) { fail(ex); } });
        operation.addActionListener(e -> updateHelp()); column.addActionListener(e -> refreshValues());
        stepTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) selectStep(); });
        updateHelp();
    }
    private JButton button(String text, String name, Runnable action) {
        var button = new JButton(text); button.setName(name); button.addActionListener(e -> action.run()); editButtons.add(button); return button;
    }
    private JPanel row(Component... children) {
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5)); row.setOpaque(false); row.setAlignmentX(LEFT_ALIGNMENT);
        for (Component child : children) row.add(child);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); return row;
    }
    private static DefaultTableModel tableModel(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }
    private JScrollPane dataScroll(DefaultTableModel model, String name) {
        var table = new JTable(model); table.setName(name); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setRowHeight(27);
        model.addTableModelListener(e -> SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < table.getColumnCount(); i++)
                table.getColumnModel().getColumn(i).setPreferredWidth(name.equals("processing-audit") && i == 5 ? 600 : 155);
        }));
        var scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader()); return scroll;
    }
    private void refreshValues() {
        if (busy) return;
        var selected = (ProcessingTable.Column)column.getSelectedItem();
        var values = selected == null ? List.<String>of() : modelService.processingOriginal().rows().stream()
                .map(r -> r.values().getOrDefault(selected.key(), "")).filter(v -> !v.isBlank()).distinct().limit(200).sorted().toList();
        value.setModel(new DefaultComboBoxModel<>(values.toArray(String[]::new)));
        updateHelp();
    }
    private void updateHelp() {
        var op = (ProcessingStep.Operation)operation.getSelectedItem();
        boolean range = op == ProcessingStep.Operation.KEEP_RANGE || op == ProcessingStep.Operation.REMOVE_RANGE;
        boolean noValue = op == ProcessingStep.Operation.DROP_COLUMN || op == ProcessingStep.Operation.REMOVE_MISSING || op == ProcessingStep.Operation.CATEGORY_CODES || op == ProcessingStep.Operation.MIN_MAX || op == ProcessingStep.Operation.Z_SCORE;
        value.setEnabled(!busy && !noValue); upper.setEnabled(!busy && range);
        if (op == ProcessingStep.Operation.IQR) { value.getEditor().setItem("1.5"); help.setText("以此步驟之前的數值計算 IQR；預設 1.5 倍，空白列保留，非數值會報錯。"); }
        else if (op == ProcessingStep.Operation.CATEGORY_CODES || op == ProcessingStep.Operation.MIN_MAX || op == ProcessingStep.Operation.Z_SCORE) help.setText("不需輸入值；正式轉換參數隨模型保存，未知類別不擴充字典。正規化後測試值可能超過 0～1。");
        else if (range) help.setText("上下界皆包含；數值欄不可含文字。保留範圍不選取空白，刪除範圍會保留空白。");
        else if (op == ProcessingStep.Operation.DROP_COLUMN) help.setText("刪除模型欄位也會將它排除於訓練。案例編號、目標時間與最後一項輸入特徵不能刪除。");
        else help.setText("文字區分大小寫，依原始內容比對；下拉提供前 200 種選項，也可自行輸入。");
    }
    private void editStep(boolean replace) {
        try {
            var col = (ProcessingTable.Column)column.getSelectedItem();
            if (col == null) throw new IllegalArgumentException("請先匯入資料並選擇欄位");
            var step = new ProcessingStep((ProcessingStep.Operation)operation.getSelectedItem(), col.key(),
                    Objects.toString(value.getEditor().getItem(), ""), upper.getText());
            int index = stepTable.getSelectedRow();
            if (replace) { if (index < 0) throw new IllegalArgumentException("請先選取要更新的步驟"); steps.set(index, step); }
            else { if (steps.size() >= 200) throw new IllegalArgumentException("最多 200 個步驟"); steps.add(step); index = steps.size() - 1; }
            changed(); updateStepTable(); stepTable.setRowSelectionInterval(index, index);
        } catch (RuntimeException e) { fail(e); }
    }
    private void selectStep() {
        int index = stepTable.getSelectedRow(); if (index < 0 || index >= steps.size()) return;
        var step = steps.get(index); operation.setSelectedItem(step.operation());
        for (int i = 0; i < column.getItemCount(); i++) if (column.getItemAt(i).key().equals(step.column())) { column.setSelectedIndex(i); break; }
        value.getEditor().setItem(step.value()); upper.setText(step.upper());
    }
    private void moveStep(int offset) {
        int index = stepTable.getSelectedRow(), next = index + offset;
        if (index < 0 || next < 0 || next >= steps.size()) return;
        Collections.swap(steps, index, next); changed(); updateStepTable(); stepTable.setRowSelectionInterval(next, next);
    }
    private void removeStep() {
        int index = stepTable.getSelectedRow(); if (index < 0) return;
        steps.remove(index); changed(); updateStepTable();
    }
    private void changed() {
        draftVersion++; preview = null; applyButton.setEnabled(false); afterModel.setRowCount(0); auditModel.setRowCount(0);
        summary.setText("步驟草稿已變更，目前資料尚未改動；請預覽全部步驟。");
    }
    private void updateStepTable() {
        stepModel.setRowCount(0); int index = 0;
        for (var step : steps) {
            String label = modelService.processingOriginal().columns().stream().filter(c -> c.key().equals(step.column()))
                    .map(ProcessingTable.Column::label).findFirst().orElse(step.column());
            stepModel.addRow(new Object[]{++index, step.operation(), label, step.value(), step.upper()});
        }
    }
    private Recipe recipe() { return new Recipe(modelService.processingOriginal().target(), steps); }
    private void saveSteps() {
        var chosen = NativeFileDialogs.saveFile(this, "儲存資料處理步驟", "資料處理.steps", "steps");
        if (chosen.isEmpty()) return;
        if (Files.exists(chosen.get()) && JOptionPane.showConfirmDialog(this, "覆寫既有步驟檔？", "確認覆寫", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { processor.saveRecipe(recipe(), chosen.get()); statusSink.accept("已儲存 " + steps.size() + " 個步驟：" + chosen.get()); }
        catch (Exception e) { fail(e); }
    }
    private void loadSteps() {
        var chosen = NativeFileDialogs.openFiles(this, "載入資料處理步驟", "steps", false); if (chosen.isEmpty()) return;
        try { installRecipe(processor.loadRecipe(chosen.getFirst())); }
        catch (Exception e) { fail(e); }
    }
    void installRecipe(Recipe loaded) {
        if (!loaded.target().equals(modelService.processingOriginal().target())) throw new IllegalArgumentException("步驟檔預測目標與目前資料不同");
        for (var step : loaded.steps()) modelService.processingOriginal().column(step.column());
        steps.clear(); steps.addAll(loaded.steps()); changed(); updateStepTable();
        summary.setText("已載入 " + steps.size() + " 個步驟，尚未套用；請先預覽。");
    }
    void previewSteps() {
        if (busy) return;
        Recipe recipe = recipe(); int version = draftVersion;
        preview = null; afterModel.setRowCount(0); auditModel.setRowCount(0);
        setBusy(true); summary.setText("正在從原始資料預覽全部步驟…");
        new SwingWorker<SurgeryModelService.ProcessingPreview, Void>() {
            protected SurgeryModelService.ProcessingPreview doInBackground() { return modelService.previewProcessing(recipe); }
            protected void done() {
                try {
                    var result = get();
                    if (version != draftVersion || result.revision() != modelService.processingRevision()) throw new IllegalStateException("資料或步驟已改變，請重新預覽");
                    showResult(result.result()); preview = result;
                    int count = result.result().table().rows().size();
                    summary.setText("預覽：" + modelService.processingOriginal().rows().size() + " → " + count + " 筆，"
                            + result.result().table().features().size() + " 項模型特徵。"
                            + (result.result().trainable() ? "尚未套用；確認後按「套用結果並繼續」。" : "不足 200 筆，無法套用訓練；請調整步驟。"));
                } catch (Exception e) { fail(e); }
                finally { setBusy(false); }
            }
        }.execute();
    }
    private void applyResult() {
        if (busy || preview == null) return;
        var accepted = preview; setBusy(true); summary.setText("正在另存處理結果與步驟…");
        new SwingWorker<Integer, Void>() {
            protected Integer doInBackground() { return modelService.applyProcessing(accepted); }
            protected void done() {
                boolean success = false;
                try { int count = get(); preview = null; shownRevision = -1; statusSink.accept("已套用資料處理：" + count + " 筆；CSV 與 .steps 已另存"); success = true; }
                catch (Exception e) { preview = null; fail(e); }
                finally { setBusy(false); }
                if (success) { refreshData(); completed.run(); }
            }
        }.execute();
    }
    private void showResult(Result result) {
        fillTable(afterModel, result.table()); auditModel.setRowCount(0);
        if(steps.stream().anyMatch(tw.edu.nkust.surgerytime.service.FittedTransforms::transformation)) {
            var selected=result.table().columns().stream().filter(c->steps.stream().anyMatch(st->st.column().equals(c.key())&&tw.edu.nkust.surgerytime.service.FittedTransforms.transformation(st)))
                    .map(c->c.feature()==null?c.key():c.feature()).toList();
            var fitted=tw.edu.nkust.surgerytime.service.FittedTransforms.fit(result.table(),result.table().rows(),selected,steps);
            afterModel.setColumnIdentifiers(result.table().columns().stream().map(c->c.label()+(selected.contains(c.feature())||selected.contains(c.key())?"〔探索〕":"")).toArray());afterModel.setRowCount(0);
            result.table().rows().stream().limit(100).forEach(r->{var encoded=fitted.encode(r.values());afterModel.addRow(result.table().columns().stream().map(c->{var field=fitted.fields().stream().filter(f->f.key().equals(c.key())).findFirst();return field.isPresent()?encoded[field.get().offset()]:r.values().getOrDefault(c.key(),"");}).toArray());});
            auditModel.addRow(new Object[]{"探索","全資料估計，僅預覽",result.table().rows().size(),result.table().rows().size(),0,"不送入模型；正式訓練重新由訓練集估計。"+fitted.description()});
        }
        result.audits().forEach(a -> auditModel.addRow(new Object[]{a.step(), a.description(), a.before(), a.after(), a.before() - a.after(), a.detail()}));
    }
    private void fillTable(DefaultTableModel model, ProcessingTable table) {
        model.setColumnIdentifiers(table.columns().stream().map(ProcessingTable.Column::label).toArray()); model.setRowCount(0);
        table.rows().stream().limit(100).forEach(r -> model.addRow(table.columns().stream().map(c -> r.values().getOrDefault(c.key(), "")).toArray()));
    }
    private void setBusy(boolean value) { busy = value; updateEnabled(); }
    private void updateEnabled() {
        boolean enabled = !busy && modelService.hasGeneratedData();
        editButtons.forEach(b -> b.setEnabled(enabled)); operation.setEnabled(enabled); column.setEnabled(enabled); stepTable.setEnabled(enabled);
        previewButton.setEnabled(enabled);
        applyButton.setEnabled(enabled && preview != null && preview.revision() == modelService.processingRevision() && preview.result().trainable());
        openButton.setEnabled(!busy && modelService.hasGeneratedData() && Files.isRegularFile(modelService.generatedCsvPath()));
        var op = (ProcessingStep.Operation)operation.getSelectedItem();
        value.setEnabled(enabled && op != ProcessingStep.Operation.DROP_COLUMN && op != ProcessingStep.Operation.REMOVE_MISSING && op != ProcessingStep.Operation.CATEGORY_CODES && op != ProcessingStep.Operation.MIN_MAX && op != ProcessingStep.Operation.Z_SCORE);
        upper.setEnabled(enabled && (op == ProcessingStep.Operation.KEEP_RANGE || op == ProcessingStep.Operation.REMOVE_RANGE));
    }
    private void fail(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        summary.setText("未套用：" + cause.getMessage()); statusSink.accept("資料處理未完成：" + cause.getMessage());
        if (!Boolean.getBoolean("surgerytime.suppressDialogs") && isShowing()) JOptionPane.showMessageDialog(this, cause.getMessage(), "資料處理", JOptionPane.WARNING_MESSAGE);
    }
}
