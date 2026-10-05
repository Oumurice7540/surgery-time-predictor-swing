package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.MappedCsvImportService;
import tw.edu.nkust.surgerytime.service.MappedCsvImportService.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/** 每份來源獨立對應欄位，檢查通過後回傳不可變快照。 */
public final class CsvImportPanel extends JPanel {
    private final MappedCsvImportService importer = new MappedCsvImportService();
    private final List<Path> files;
    private final Consumer<Report> accepted;
    private final JComboBox<String> encoding = new JComboBox<>(new String[]{"UTF-8", "Big5", "UTF-16LE"});
    private final JComboBox<String> target = new JComboBox<>(new String[]{"手術時間", "麻醉時間"});
    private final JButton read = new JButton("讀取／重新讀取欄位");
    private final JButton validate = new JButton("檢查所有資料");
    private final JButton apply = new JButton("套用已通過的資料");
    private final JButton export = new JButton("匯出檢查報告");
    private final JLabel status = new JLabel("先讀取欄位，再確認每份檔案的對應與時間格式。");
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<FileMappingPanel> mappings = new ArrayList<>();
    private final DefaultTableModel issues = new DefaultTableModel(new String[]{"檔案", "起始列號", "欄位", "原因"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private Report report;
    private boolean busy;

    public CsvImportPanel(List<Path> files, Consumer<Report> accepted) {
        this.files = List.copyOf(files); this.accepted = accepted;
        setName("csv-import-panel"); setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        var north = new JPanel(new GridLayout(0, 1, 4, 4));
        var controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("CSV 編碼")); controls.add(encoding); controls.add(read);
        var prepare=new JButton("DRG／麻醉時間轉檔");controls.add(prepare);
        prepare.addActionListener(e->{
            int index=tabs.getSelectedIndex();if(busy||index<0)return;
            try {
                var source=ReportPreparationDialog.choose(this,mappings.get(index).source);
                if(source!=null){var sources=new ArrayList<Source>();for(var p:mappings)sources.add(p.source);sources.set(index,source);invalidateReport();showSources(sources);status.setText("已建立轉檔暫存副本；請重新確認所有欄位對應並檢查資料。原始來源未修改。");}
            }catch(Exception ex){status.setText("轉檔未套用："+ex.getMessage());}
        });
        controls.add(new JLabel("預測目標名稱")); target.setEditable(true); controls.add(target);
        north.add(controls);
        north.add(new JLabel("選擇目標時間；可使用既有對應特徵，或保留合適輔助欄於第五頁選取。案例編號留空可自動建立。"));
        north.add(new JLabel("未對應的來源欄位會保留為輔助欄（例如科別），供資料處理篩選，不會自動加入模型。"));
        add(north, BorderLayout.NORTH);
        var table = new JTable(issues); table.setRowHeight(26); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(270);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(620);
        var errorScroll = new JScrollPane(table); errorScroll.setBorder(BorderFactory.createTitledBorder("匯入檢查報告（完整內容可匯出）"));
        var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, errorScroll);
        split.setResizeWeight(.70); split.setDividerLocation(405); add(split, BorderLayout.CENTER);
        var south = new JPanel(new BorderLayout()); south.add(status, BorderLayout.NORTH);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(validate); buttons.add(export); buttons.add(apply); south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        validate.setName("validate-import"); apply.setName("apply-import"); read.setName("read-columns");
        validate.setEnabled(false); apply.setEnabled(false); export.setEnabled(false);
        read.addActionListener(e -> readHeaders()); validate.addActionListener(e -> validateData());
        apply.addActionListener(e -> { if (report != null && report.valid()) accepted.accept(report); });
        export.addActionListener(e -> exportReport());
        encoding.addActionListener(e -> { mappings.clear(); tabs.removeAll(); invalidateReport(); validate.setEnabled(false); });
        target.addActionListener(e -> invalidateReport());
        ((JTextField)target.getEditor().getEditorComponent()).getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { invalidateReport(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { invalidateReport(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { invalidateReport(); }
        });
    }
    public static Report choose(Component owner, List<Path> files) {
        final Report[] result = {null};
        var dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "CSV 欄位對應與匯入檢查", Dialog.ModalityType.APPLICATION_MODAL);
        var panel = new CsvImportPanel(files, report -> { result[0] = report; dialog.dispose(); });
        dialog.setContentPane(panel); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(1120, screen.width - 60), Math.min(860, screen.height - 70));
        dialog.setLocationRelativeTo(owner);
        panel.readHeaders(); dialog.setVisible(true); return result[0];
    }
    private void invalidateReport() {
        report = null; apply.setEnabled(false); export.setEnabled(false); issues.setRowCount(0);
        status.setText("設定已變更，請重新檢查所有資料。");
    }
    private void setBusy(boolean value) {
        busy = value; read.setEnabled(!value); encoding.setEnabled(!value); target.setEnabled(!value);
        validate.setEnabled(!value && !mappings.isEmpty());
        mappings.forEach(p -> p.setControlsEnabled(!value));
        apply.setEnabled(!value && report != null && report.valid()); export.setEnabled(!value && report != null);
    }
    void readHeaders() {
        if (busy) return;
        invalidateReport(); mappings.clear(); tabs.removeAll(); setBusy(true);
        Charset charset = Charset.forName(Objects.toString(encoding.getSelectedItem()));
        status.setText("正在讀取 " + files.size() + " 份檔案的標題列…");
        new SwingWorker<List<Source>, Void>() {
            protected List<Source> doInBackground() throws Exception {
                var sources = new ArrayList<Source>(); for (Path file : files) sources.add(importer.inspect(file, charset)); return sources;
            }
            protected void done() {
                try { showSources(get()); }
                catch (Exception e) { status.setText("讀取失敗，請確認編碼：" + message(e)); }
                finally { setBusy(false); }
            }
        }.execute();
    }
    void showSources(List<Source> sources) {
        mappings.clear(); tabs.removeAll();
        for (Source source : sources) {
            var panel = new FileMappingPanel(importer.suggest(source)); mappings.add(panel);
            tabs.addTab(source.path().getFileName().toString(), new JScrollPane(panel));
            tabs.setToolTipTextAt(tabs.getTabCount() - 1, source.path().toString());
        }
        boolean anesthesia = !mappings.isEmpty() && mappings.stream().allMatch(p -> {
            Integer column = p.mapping().columns().get(Field.TARGET);
            return column != null && p.source.headers().get(column).contains("麻醉");
        });
        target.setSelectedItem(anesthesia ? "麻醉時間" : "手術時間");
        status.setText("已讀取欄位；請確認每份檔案，尤其是目標時間欄與格式。"); validate.setEnabled(true);
    }
    void validateData() {
        if (busy || mappings.isEmpty()) return;
        String targetName = Objects.toString(target.getEditor().getItem(), "").trim();
        if (targetName.isBlank()) { status.setText("請輸入預測目標名稱。"); return; }
        List<Mapping> snapshot = mappings.stream().map(FileMappingPanel::mapping).toList();
        invalidateReport(); setBusy(true); status.setText("正在逐列檢查，尚未套用資料…");
        new SwingWorker<Report, Void>() {
            protected Report doInBackground() { return importer.validate(snapshot, targetName); }
            protected void done() {
                try { showReport(get()); }
                catch (Exception e) { status.setText("檢查失敗：" + message(e)); }
                finally { setBusy(false); }
            }
        }.execute();
    }
    void showReport(Report result) {
        report = result; issues.setRowCount(0);
        result.issues().stream().limit(2000).forEach(i -> issues.addRow(new Object[]{i.file(), i.line(), i.field(), i.message()}));
        status.setText("共 " + result.totalRows() + " 筆，有效 " + result.cases().size() + " 筆，問題 " + result.issues().size()
                + " 項。" + (result.valid() ? "檢查通過，可套用。" : "請修正來源或對應後重新檢查；不會略過錯誤列。")
                + (result.issues().size() > 2000 ? " 畫面顯示前 2,000 項。" : ""));
        apply.setEnabled(result.valid()); export.setEnabled(true);
    }
    private void exportReport() {
        if (report == null) return;
        var selected = NativeFileDialogs.saveFile(this, "匯出匯入檢查報告", "import-report.csv", "csv");
        if (selected.isEmpty()) return;
        if (files.stream().map(p -> p.toAbsolutePath().normalize()).anyMatch(selected.get()::equals)) {
            JOptionPane.showMessageDialog(this, "報告不可覆寫來源 CSV，請使用其他檔名。"); return;
        }
        if (Files.exists(selected.get()) && JOptionPane.showConfirmDialog(this, "覆寫此報告檔？", "確認覆寫", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { MappedCsvImportService.exportReport(report, selected.get()); status.setText("已匯出完整報告：" + selected.get()); }
        catch (Exception e) { JOptionPane.showMessageDialog(this, "匯出失敗：" + message(e)); }
    }
    private static String message(Exception e) { return e.getCause() == null ? e.getMessage() : e.getCause().getMessage(); }
    private final class FileMappingPanel extends JPanel {
        final Source source;
        final Map<Field, JComboBox<String>> choices = new EnumMap<>(Field.class);
        final JComboBox<TimeFormat> format = new JComboBox<>(TimeFormat.values());
        FileMappingPanel(Mapping suggestion) {
            source = suggestion.source(); setLayout(new GridLayout(0, 2, 12, 5));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            var options = new ArrayList<String>(); options.add("（未提供／不使用）");
            for (int i = 0; i < source.headers().size(); i++) options.add((i + 1) + ". " + source.headers().get(i));
            var ordered = new ArrayList<Field>(); ordered.add(Field.TARGET);
            for (Field f : Field.values()) if (f != Field.TARGET) ordered.add(f);
            for (Field field : ordered) {
                add(new JLabel(field.label)); var box = new JComboBox<>(options.toArray(String[]::new));
                box.setSelectedIndex(suggestion.columns().getOrDefault(field, -1) + 1);
                box.setName("mapping-" + field.name()); box.addActionListener(e -> invalidateReport());
                choices.put(field, box); add(box);
                if (field == Field.TARGET) {
                    add(new JLabel("目標時間格式（持續時間，非開始時刻）"));
                    format.setSelectedItem(suggestion.timeFormat()); format.addActionListener(e -> invalidateReport()); add(format);
                }
            }
        }
        Mapping mapping() {
            Map<Field, Integer> columns = new EnumMap<>(Field.class);
            choices.forEach((f, box) -> { if (box.getSelectedIndex() > 0) columns.put(f, box.getSelectedIndex() - 1); });
            return new Mapping(source, columns, (TimeFormat)format.getSelectedItem());
        }
        void setControlsEnabled(boolean enabled) { choices.values().forEach(box -> box.setEnabled(enabled)); format.setEnabled(enabled); }
    }
}
