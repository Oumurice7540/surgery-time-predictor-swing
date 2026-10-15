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
    private final JLabel help = new WrappingLabel("");
    private final JLabel summary = new WrappingLabel("請先匯入或生成資料。");
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
    private final JPanel flowRows = new JPanel();
    private final List<JComponent> flowControls = new ArrayList<>();
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
        var tools = new JPanel(new GridLayout(7, 1, 3, 5));
        tools.setName("processing-toolbar"); tools.setOpaque(false);
        tools.add(tool("刪除離群值", "IQR", ProcessingStep.Operation.IQR));
        tools.add(tool("刪除特定值", "× 列", ProcessingStep.Operation.REMOVE_EQUALS));
        tools.add(tool("刪除特定欄", "× 欄", ProcessingStep.Operation.DROP_COLUMN));
        tools.add(tool("選取特定值", "篩選", ProcessingStep.Operation.KEEP_EQUALS));
        tools.add(tool("資料數值化", "123", ProcessingStep.Operation.CATEGORY_CODES));
        tools.add(tool("資料正規化", "0～1", ProcessingStep.Operation.MIN_MAX));
        tools.add(tool("轉換 Z 分數", "Z", ProcessingStep.Operation.Z_SCORE));
        var toolbar = new JPanel(new BorderLayout(0, 5)); toolbar.setOpaque(false);
        toolbar.add(new JLabel("工具箱", SwingConstants.CENTER), BorderLayout.NORTH);
        toolbar.add(tools); toolbar.setPreferredSize(new Dimension(96, 500));

        // 保留完整條件編輯，主要畫面使用參考圖的一列一步驟配置。
        var editor = new JPanel(); editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
        editor.add(new JLabel("操作")); editor.add(operation);
        editor.add(new JLabel("欄位")); editor.add(column);
        editor.add(valueLabel); editor.add(value); editor.add(new JLabel("範圍上界")); editor.add(upper);
        help.setFont(UiTheme.font(Font.PLAIN, 12f)); help.setPreferredSize(new Dimension(340, 70)); editor.add(help);
        editor.add(row(button("新增步驟", "add-processing-step", () -> editStep(false)),
                button("更新選取步驟", "update-processing-step", () -> editStep(true))));
        editor.add(row(button("上移", "move-step-up", () -> moveStep(-1)), button("下移", "move-step-down", () -> moveStep(1)),
                button("移除步驟", "remove-processing-step", this::removeStep)));
        editor.add(new JScrollPane(stepTable));
        for (Component c : editor.getComponents()) if (c instanceof JComboBox<?> || c instanceof JTextField)
            ((JComponent)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        flowRows.setLayout(new BoxLayout(flowRows, BoxLayout.Y_AXIS)); flowRows.setName("processing-flow-rows");
        var flowScroll = new JScrollPane(flowRows); flowScroll.setBorder(BorderFactory.createEmptyBorder());
        var flowTabs = new JTabbedPane(); flowTabs.setName("processing-flow-tabs");
        flowTabs.addTab("資料處理流程", flowScroll); flowTabs.addTab("詳細條件編輯", editor);
        var footer = new JPanel(); footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        summary.setFont(UiTheme.font(Font.PLAIN, 12f)); summary.setPreferredSize(new Dimension(350, 48));
        footer.add(summary);
        previewButton.setText("預覽結果");
        var actions = new JPanel(new GridLayout(1, 3, 4, 0));
        actions.add(button("儲存步驟", "save-processing-steps", this::saveSteps));
        actions.add(button("載入步驟", "load-processing-steps", this::loadSteps)); actions.add(previewButton);
        footer.add(actions);
        var extras = new JPanel(new GridLayout(1, 2, 4, 0));
        extras.add(button("清空／還原", "clear-processing-steps", () -> { steps.clear(); changed(); updateStepTable(); }));
        extras.add(applyButton); footer.add(extras);
        var flow = new JPanel(new BorderLayout(0, 4)); flow.add(flowTabs); flow.add(footer, BorderLayout.SOUTH);
        flow.setMinimumSize(new Dimension(380, 350));

        var beforePanel = new JPanel(new BorderLayout());
        var beforeHeading = new JPanel(new BorderLayout()); beforeHeading.add(new JLabel("資料結果（處理前）"));
        openButton.setText("開啟已套用 CSV"); beforeHeading.add(openButton, BorderLayout.EAST);
        beforePanel.add(beforeHeading, BorderLayout.NORTH); beforePanel.add(dataScroll(beforeModel, "processing-before"));
        var afterPanel = new JPanel(new BorderLayout());
        afterPanel.add(new JLabel("資料結果（處理後） · 探索預覽"), BorderLayout.NORTH);
        afterPanel.add(dataScroll(afterModel, "processing-after"));
        var tables = new JSplitPane(JSplitPane.VERTICAL_SPLIT, beforePanel, afterPanel);
        tables.setResizeWeight(.5); tables.setDividerLocation(.5);
        var tabs = new JTabbedPane(); tabs.setName("processing-preview-tabs");
        tabs.addTab("資料預覽", tables); tabs.addTab("執行紀錄／轉換設定", dataScroll(auditModel, "processing-audit"));
        var results = new JPanel(new BorderLayout()); results.add(tabs);
        var note = ViewSupport.wrappedText("每表預覽前 100 筆。探索以全部資料估計；正式訓練的 IQR、字典與尺度只由訓練集估計。", 12f, UiTheme.MUTED);
        note.setPreferredSize(new Dimension(330, 38)); results.add(note, BorderLayout.SOUTH);
        results.setMinimumSize(new Dimension(340, 350));
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, flow, results);
        split.setResizeWeight(.49); split.setDividerLocation(.49);
        var workspace = new JPanel(new BorderLayout(6, 0)); workspace.add(toolbar, BorderLayout.WEST); workspace.add(split);
        workspace.setPreferredSize(new Dimension(1100, 545));
        workspace.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); content.add(workspace);
        previewButton.setName("preview-processing"); applyButton.setName("apply-processing");
        previewButton.addActionListener(e -> previewSteps()); applyButton.addActionListener(e -> applyResult());
        openButton.addActionListener(e -> { try { Desktop.getDesktop().open(modelService.generatedCsvPath().toFile()); } catch (Exception ex) { fail(ex); } });
        operation.addActionListener(e -> updateHelp()); column.addActionListener(e -> refreshValues());
        stepTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) selectStep(); });
        updateHelp();
    }
    private static final class WrappingLabel extends JLabel {
        WrappingLabel(String text) { super(text); }
        @Override public void setText(String text) {
            super.setText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>");
        }
    }
    private JButton tool(String label, String symbol, ProcessingStep.Operation op) {
        var button = button("<html><center><b>" + symbol + "</b><br>" + label + "</center></html>",
                "processing-tool-" + op.name(), () -> {
                    if (column.getItemCount() == 0) return;
                    operation.setSelectedItem(op);
                    // 預設避開案例編號與目標欄，欄位仍可在流程列中變更。
                    for (int i = 0; i < column.getItemCount(); i++) {
                        var candidate = column.getItemAt(i);
                        if (candidate.feature() != null) { column.setSelectedIndex(i); break; }
                    }
                    updateHelp(); editStep(false);
                });
        button.setMargin(new Insets(2, 2, 2, 2)); button.setToolTipText(op.toString());
        return button;
    }
    private void rebuildFlowRows() {
        flowRows.removeAll(); flowControls.clear();
        if (steps.isEmpty()) {
            var hint = ViewSupport.wrappedText("從左側工具箱加入處理步驟。\n各列可修改欄位與條件，↑／↓ 排序，× 移除。", 13f, UiTheme.MUTED);
            hint.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8)); flowRows.add(hint);
        }
        for (int index = 0; index < steps.size(); index++) {
            final int at = index; var step = steps.get(at);
            var line = new JPanel(new BorderLayout(3, 0));
            line.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            var heading = new JPanel(new BorderLayout());
            String shortName = switch(step.operation()) {
                case DROP_COLUMN -> "刪欄"; case IQR -> "IQR";
                case CATEGORY_CODES -> "數值化"; case MIN_MAX -> "0～1"; case Z_SCORE -> "Z 分數";
                case KEEP_EQUALS, KEEP_CONTAINS, KEEP_RANGE -> "選取";
                default -> "刪除";
            };
            var label = new JLabel((at + 1) + " " + shortName);
            label.setToolTipText(step.operation().toString()); heading.add(label);
            var controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
            for (var direction : new String[]{"↑", "↓", "×"}) {
                var action = new JButton(direction); action.setMargin(new Insets(0, 5, 0, 5));
                action.setName("processing-row-" + at + "-" + direction);
                action.setToolTipText(direction.equals("×") ? "移除此步驟" : direction.equals("↑") ? "上移" : "下移");
                action.addActionListener(e -> { stepTable.setRowSelectionInterval(at, at);
                    if (direction.equals("×")) removeStep(); else moveStep(direction.equals("↑") ? -1 : 1); });
                controls.add(action); flowControls.add(action);
            }
            line.add(heading, BorderLayout.WEST); line.add(controls, BorderLayout.EAST);
            var fields = new JPanel(new GridLayout(1, 0, 3, 0));
            var columns = new JComboBox<>(modelService.processingOriginal().columns().toArray(ProcessingTable.Column[]::new));
            for (int i = 0; i < columns.getItemCount(); i++) if (columns.getItemAt(i).key().equals(step.column())) columns.setSelectedIndex(i);
            columns.setName("processing-row-" + at + "-column"); fields.add(columns); flowControls.add(columns);
            var input = new JTextField(step.value()); var high = new JTextField(step.upper());
            boolean needsValue = switch(step.operation()) {
                case DROP_COLUMN, REMOVE_MISSING, CATEGORY_CODES, MIN_MAX, Z_SCORE -> false;
                default -> true;
            };
            Runnable commit = () -> {
                try {
                    var chosen = (ProcessingTable.Column) columns.getSelectedItem();
                    var replacement = new ProcessingStep(step.operation(), chosen.key(), input.getText(), high.getText());
                    if (!replacement.equals(steps.get(at))) {
                        steps.set(at, replacement); changed();
                        stepModel.setValueAt(chosen.label(), at, 2); stepModel.setValueAt(replacement.value(), at, 3); stepModel.setValueAt(replacement.upper(), at, 4);
                    }
                } catch (RuntimeException ex) { input.setText(steps.get(at).value()); high.setText(steps.get(at).upper()); fail(ex); }
            };
            columns.addActionListener(e -> commit.run());
            if (needsValue) {
                input.setName("processing-row-" + at + "-value"); input.setToolTipText("比對值／下界／IQR 倍數；Enter 或離開欄位後更新");
                fields.add(input); flowControls.add(input);
                if (step.operation() == ProcessingStep.Operation.KEEP_RANGE || step.operation() == ProcessingStep.Operation.REMOVE_RANGE) {
                    high.setToolTipText("範圍上界"); fields.add(high); flowControls.add(high);
                }
                for (var text : new JTextField[]{input, high}) {
                    text.addActionListener(e -> commit.run());
                    text.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusLost(java.awt.event.FocusEvent e) {
                        if (text.isDisplayable() && at < steps.size() && steps.get(at).operation() == step.operation()) commit.run();
                    }});
                }
            }
            line.add(fields); flowRows.add(line);
        }
        flowRows.revalidate(); flowRows.repaint(); updateEnabled();
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
        rebuildFlowRows();
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
        flowControls.forEach(c -> c.setEnabled(enabled));
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
