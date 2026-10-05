package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.DataSourceSelection;
import tw.edu.nkust.surgerytime.model.SyntheticSurgeryCase;
import tw.edu.nkust.surgerytime.model.TrainingConfiguration;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 第二頁將來源檔驗證並合併成單一標準 CSV。
 * 選擇虛擬資料時，本頁會真正產生 CSV；任何模式都不會在此階段訓練模型。
 */
public final class DataConversionView extends WorkflowPagePanel {
    private final SurgeryModelService modelService;
    private final Supplier<DataSourceSelection> selectionSupplier;
    private final Consumer<String> statusSink;
    private final Runnable conversionCompleted;
    private final JSpinner sampleSpinner = new JSpinner(new SpinnerNumberModel(2_000, 200, 20_000, 200));
    private final JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(20_260_704L, 1L, 999_999_999L, 1L));
    private final JButton convertButton = new JButton("執行驗證與轉檔");
    private final JButton openButton = new JButton("開啟標準 CSV");
    private final JLabel sourceLabel = new JLabel();
    private final DefaultTableModel previewModel = new DefaultTableModel(
            new String[]{"案例編號", "手術室", "年齡", "性別", "手術名稱", "醫師", "分鐘"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public DataConversionView(
            SurgeryModelService modelService,
            Supplier<DataSourceSelection> selectionSupplier,
            Consumer<String> statusSink,
            Runnable conversionCompleted
    ) {
        super("資料轉檔", "步驟 2 / 9");
        this.modelService = Objects.requireNonNull(modelService, "模型服務不可為空");
        this.selectionSupplier = Objects.requireNonNull(selectionSupplier, "資料來源不可為空");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.conversionCompleted = Objects.requireNonNull(conversionCompleted, "完成動作不可為空");
        configureControls();
        buildContent();
        refreshSource();
    }

    public JButton defaultButton() { return convertButton.isEnabled() ? convertButton : null; }

    public void refreshSource() {
        try {
            DataSourceSelection selection = selectionSupplier.get();
            boolean synthetic = selection.type() == DataSourceSelection.SourceType.SYNTHETIC;
            sourceLabel.setText(synthetic
                    ? "來源：系統虛擬病例"
                    : "來源：" + selection.csvFiles().size() + " 份 CSV 月報表");
            sampleSpinner.setEnabled(synthetic);
            seedSpinner.setEnabled(synthetic);
            convertButton.setEnabled(true);
        } catch (RuntimeException exception) {
            sourceLabel.setText("請先回到資料來源頁完成選擇");
            convertButton.setEnabled(false);
        }
        fillPreview(modelService.preparedCases());
        openButton.setEnabled(modelService.hasGeneratedData()
                && Files.isRegularFile(modelService.generatedCsvPath()));
    }

    private void configureControls() {
        UiTheme.styleInput(sampleSpinner);
        UiTheme.styleInput(seedSpinner);
        sampleSpinner.setPreferredSize(new Dimension(220, 43));
        seedSpinner.setPreferredSize(new Dimension(220, 43));
        UiTheme.styleButton(convertButton, UiTheme.TEAL, Color.WHITE);
        UiTheme.styleButton(openButton, new Color(226, 239, 250), new Color(35, 91, 139));
        convertButton.addActionListener(event -> convert());
        openButton.addActionListener(event -> openCsv());
        sourceLabel.setFont(UiTheme.font(Font.BOLD, 12f));
        sourceLabel.setForeground(UiTheme.TEXT);
        sourceLabel.setAlignmentX(LEFT_ALIGNMENT);
    }

    private void buildContent() {
        var settings = card();
        settings.add(ViewSupport.sectionLabel("月報表合併與格式統一"));
        settings.add(Box.createVerticalStrut(6));
        settings.add(UiTheme.label("建立標準病例 CSV", 21f, Font.BOLD, UiTheme.INK));
        settings.add(Box.createVerticalStrut(8));
        settings.add(sourceLabel);
        settings.add(Box.createVerticalStrut(14));
        var fields = new JPanel(new java.awt.GridLayout(4,1,4,6));
        fields.setOpaque(false);
        fields.add(UiTheme.label("虛擬資料筆數", 12f, Font.BOLD, UiTheme.TEXT));
        fields.add(sampleSpinner);
        fields.add(UiTheme.label("亂數種子", 12f, Font.BOLD, UiTheme.TEXT));
        fields.add(seedSpinner);
        fields.setAlignmentX(LEFT_ALIGNMENT);
        fields.setMaximumSize(new Dimension(Integer.MAX_VALUE,160));
        settings.add(fields);
        settings.add(Box.createVerticalStrut(14));
        var actions = new JPanel(new java.awt.GridLayout(3,1,4,6));
        actions.setOpaque(false);
        actions.add(convertButton);
        actions.add(openButton);
        var export=new JButton("匯出完整資料");actions.add(export);
        export.addActionListener(e->{var chosen=NativeFileDialogs.saveFile(this,"匯出完整資料","完整病例.csv","csv");if(chosen.isEmpty())return;
            if(Files.exists(chosen.get())&&javax.swing.JOptionPane.showConfirmDialog(this,"覆寫此檔案？","確認",javax.swing.JOptionPane.YES_NO_OPTION)!=javax.swing.JOptionPane.YES_OPTION)return;
            try {new tw.edu.nkust.surgerytime.service.DataProcessingService().writeCsv(modelService.processingCurrent().table(),chosen.get());statusSink.accept("已匯出全部欄位與病例："+chosen.get());}catch(Exception ex){showError(ex.getMessage());}});
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE,126));
        settings.add(actions);
        settings.setPreferredSize(new Dimension(1100, 215));
        settings.setMaximumSize(new Dimension(Integer.MAX_VALUE, 215));



        var preview = card();
        preview.add(ViewSupport.sectionLabel("轉檔結果預覽"));
        preview.add(Box.createVerticalStrut(8));
        JTable table = new JTable(previewModel);
        previewModel.addTableModelListener(e->{for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(150);});
        table.setRowHeight(34);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(140);
        var tableScroll = new JScrollPane(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(1050, 390));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 390));
        preview.add(tableScroll);
        preview.setPreferredSize(new Dimension(1100, 465));
        settings.setMinimumSize(new Dimension(300,350));preview.setMinimumSize(new Dimension(300,350));
        settings.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));
        var split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, settings, preview);
        split.setResizeWeight(.28);split.setDividerLocation(320);split.setPreferredSize(new Dimension(1100,600));content.add(split);
    }

    private void convert() {
        DataSourceSelection selection;
        try {
            selection = selectionSupplier.get();
            sampleSpinner.commitEdit();
            seedSpinner.commitEdit();
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }
        final tw.edu.nkust.surgerytime.service.MappedCsvImportService.Report importReport;
        if (selection.type() == DataSourceSelection.SourceType.CSV_FILES) {
            importReport = CsvImportPanel.choose(this, selection.csvFiles());
            if (importReport == null) return;
        } else importReport = null;
        convertButton.setEnabled(false);
        convertButton.setText("正在轉檔…");
        statusSink.accept("正在驗證欄位、合併資料並建立標準 CSV");
        var worker = new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() {
                if (selection.type() == DataSourceSelection.SourceType.SYNTHETIC) {
                    var defaults = TrainingConfiguration.DEFAULT;
                    var configuration = new TrainingConfiguration(
                            ((Number) sampleSpinner.getValue()).intValue(),
                            defaults.trainingRatio(), ((Number) seedSpinner.getValue()).longValue(),
                            defaults.hiddenNeuronCount(), defaults.epochs(), defaults.momentumAlpha(),
                            defaults.learningRate(), defaults.predictionIntervalTarget());
                    modelService.configureSplit(configuration.trainingRatio(), configuration.randomSeed());
                    return modelService.generateData(configuration);
                }
                return modelService.applyImport(importReport);
            }

            @Override protected void done() {
                try {
                    int count = get();
                    fillPreview(modelService.preparedCases());
                    openButton.setEnabled(true);
                    statusSink.accept(String.format(Locale.TAIWAN,
                            "轉檔完成：%,d 筆病例，下一步可檢視資料統計", count));
                    conversionCompleted.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("轉檔被中斷");
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showError(cause == null ? exception.getMessage() : cause.getMessage());
                } finally {
                    convertButton.setText("執行驗證與轉檔");
                    convertButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void fillPreview(List<SyntheticSurgeryCase> cases) {
        var data=modelService.processingCurrent().table();
        previewModel.setColumnIdentifiers(data.columns().stream().map(tw.edu.nkust.surgerytime.model.ProcessingTable.Column::label).toArray());
        previewModel.setRowCount(0);
        data.rows().stream().limit(100).forEach(row->previewModel.addRow(data.columns().stream().map(c->row.values().getOrDefault(c.key(),"")).toArray()));
    }

    private void openCsv() {
        try {
            Desktop.getDesktop().open(modelService.generatedCsvPath().toFile());
        } catch (Exception exception) {
            showError("無法開啟 CSV：" + exception.getMessage());
        }
    }

    private void showError(String message) {
        javax.swing.JOptionPane.showMessageDialog(this, message, "資料轉檔失敗",
                javax.swing.JOptionPane.ERROR_MESSAGE);
    }
}
