package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.DataSourceSelection;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 完整流程的第一頁：指定月報表目錄、直接選取 CSV，或改用系統虛擬病例。
 * 本頁只保存檔案清單，不會在選取時暗中讀檔或啟動模型訓練。
 */
public final class DataSourceView extends WorkflowPagePanel {
    private final Consumer<String> statusSink;
    private final Runnable selectionCompleted;
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);
    private final JLabel selectionStatus = new JLabel("尚未選擇資料來源");
    private DataSourceSelection selection;
    private ReferenceDataPreview dataPreview;
    private final javax.swing.JTable sourceTable=new javax.swing.JTable();
    private final JLabel sourcePreviewStatus=new JLabel("選取左側報表可預覽來源內容（未套用）");
    private final javax.swing.JComboBox<String> sourceEncoding=new javax.swing.JComboBox<>(new String[]{"UTF-8","Big5","UTF-16LE"});
    private int previewVersion;


    public DataSourceView(Consumer<String> statusSink, Runnable selectionCompleted) {
        super("資料來源", "步驟 1 / 9");
        this.statusSink = Objects.requireNonNull(statusSink, "狀態輸出不可為空");
        this.selectionCompleted = Objects.requireNonNull(selectionCompleted, "完成動作不可為空");
        buildContent();
    }

    public DataSourceSelection selection() {
        if (selection == null) throw new IllegalStateException("尚未選擇資料來源");
        return selection;
    }

    private void buildContent() {
        var card = card();
        card.add(ViewSupport.sectionLabel("資料目錄與月報表"));
        card.add(Box.createVerticalStrut(6));
        card.add(UiTheme.label("選擇手術病例資料來源", 21f, Font.BOLD, UiTheme.INK));
        card.add(Box.createVerticalStrut(6));
        card.add(ViewSupport.wrappedText(
                "可以選擇含多份 CSV 的資料夾、直接選取一份或多份 CSV，或使用系統產生的虛擬病例。這一步不會開始訓練。",
                12f, UiTheme.MUTED));
        card.add(Box.createVerticalStrut(16));

        var actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.setOpaque(false);
        JButton folderButton = new JButton("選擇資料夾");
        JButton filesButton = new JButton("選擇 CSV 檔案");
        JButton syntheticButton = new JButton("使用虛擬病例");
        UiTheme.styleButton(folderButton, new Color(226, 239, 250), new Color(35, 91, 139));
        UiTheme.styleButton(filesButton, new Color(226, 239, 250), new Color(35, 91, 139));
        UiTheme.styleButton(syntheticButton, UiTheme.TEAL, Color.WHITE);
        folderButton.addActionListener(event -> chooseFolder());
        filesButton.addActionListener(event -> chooseFiles());
        syntheticButton.addActionListener(event -> useSynthetic());
        actions.add(folderButton);
        actions.add(Box.createHorizontalStrut(9));
        actions.add(filesButton);
        actions.add(Box.createHorizontalStrut(9));
        actions.add(syntheticButton);
        actions.add(Box.createHorizontalGlue());
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, actions.getPreferredSize().height));
        actions.setAlignmentX(LEFT_ALIGNMENT);
        card.add(actions);
        card.add(Box.createVerticalStrut(14));

        selectionStatus.setFont(UiTheme.font(Font.BOLD, 12f));
        selectionStatus.setForeground(UiTheme.TEXT);
        selectionStatus.setAlignmentX(LEFT_ALIGNMENT);
        card.add(selectionStatus);
        card.add(Box.createVerticalStrut(10));
        fileList.setName("source-file-list");sourceTable.setName("source-file-preview");
        fileList.setFont(UiTheme.font(Font.PLAIN, 11f));
        fileList.setBackground(UiTheme.INPUT);
        fileList.setFixedCellHeight(31);
        var listScroll = new JScrollPane(fileList);
        listScroll.setBorder(javax.swing.BorderFactory.createLineBorder(UiTheme.BORDER));
        listScroll.setPreferredSize(new Dimension(900, 360));
        listScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
        var columns=new JPanel(new java.awt.BorderLayout()); columns.setName("source-applied-preview");
        var browser=new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT,listScroll,columns);
        browser.setResizeWeight(.3);browser.setDividerLocation(230);browser.setPreferredSize(new Dimension(700,360));
        listScroll.setMinimumSize(new Dimension(150,200));columns.setMinimumSize(new Dimension(200,200));
        card.add(browser);
        card.setPreferredSize(new Dimension(1100, 560));
        var help = card(); help.setLayout(new java.awt.BorderLayout(0,8));
        help.add(ViewSupport.sectionLabel("此流程的詳細資訊"),java.awt.BorderLayout.NORTH);
        var instructions = ViewSupport.wrappedText("先選擇資料目錄或 CSV 檔案，再到資料轉檔頁驗證與合併。\n\n資料統計可檢查分組分布，資料處理可篩選病例並保存處理步驟。\n\n完成特徵選取及資料拆分後，再到方法設定訓練模型。\n\n已有模型時，可直接切換到手術時間預測並載入模型。", 14f, UiTheme.TEXT);
        help.add(new JScrollPane(instructions),java.awt.BorderLayout.CENTER);
        var split = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, card, help);
        split.setResizeWeight(.52);split.setDividerLocation(.52);split.setPreferredSize(new Dimension(1100,600));
        card.setMinimumSize(new Dimension(490,300)); help.setMinimumSize(new Dimension(250,300)); content.add(split);
    }

    void attachPreparedPreview(tw.edu.nkust.surgerytime.service.SurgeryModelService service) {
        dataPreview=new ReferenceDataPreview(service,false);
        for(var child:allChildren(this)) if(child instanceof JPanel panel && "source-applied-preview".equals(panel.getName())) {
            var tabs=new javax.swing.JTabbedPane();
            var sourcePanel=new JPanel(new java.awt.BorderLayout());var toolbar=new JPanel(new java.awt.BorderLayout());
            toolbar.add(sourcePreviewStatus);toolbar.add(sourceEncoding,java.awt.BorderLayout.EAST);sourcePanel.add(toolbar,java.awt.BorderLayout.NORTH);
            sourceTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);sourceTable.setRowHeight(25);var sourceScroll=new JScrollPane(sourceTable);sourceScroll.setColumnHeaderView(sourceTable.getTableHeader());sourcePanel.add(sourceScroll);
            tabs.addTab("來源檔預覽（未套用）",sourcePanel);tabs.addTab("已套用訓練資料",dataPreview);panel.add(tabs);
            fileList.addListSelectionListener(e->{if(!e.getValueIsAdjusting()){tabs.setSelectedIndex(0);previewSource();}});
            sourceEncoding.addActionListener(e->previewSource());break;
        }
    }
    void refreshPreparedPreview(){if(dataPreview!=null)dataPreview.refresh();}
    private java.util.List<java.awt.Component> allChildren(java.awt.Container parent){
        var result=new java.util.ArrayList<java.awt.Component>();
        for(var c:parent.getComponents()){result.add(c);if(c instanceof java.awt.Container container)result.addAll(allChildren(container));}return result;
    }

    private void previewSource() {
        int version=++previewVersion;
        sourceTable.setModel(new javax.swing.table.DefaultTableModel());
        int index=fileList.getSelectedIndex();
        if(selection==null||selection.type()!=DataSourceSelection.SourceType.CSV_FILES||index<0) {sourcePreviewStatus.setText("尚未選取來源檔（未套用）");return;}
        Path path=selection.csvFiles().get(index);var charset=java.nio.charset.Charset.forName((String)sourceEncoding.getSelectedItem());
        sourcePreviewStatus.setText("讀取來源："+path.getFileName());sourcePreviewStatus.setToolTipText(path.toString());
        new javax.swing.SwingWorker<tw.edu.nkust.surgerytime.service.MappedCsvImportService.RawTable,Void>() {
            protected tw.edu.nkust.surgerytime.service.MappedCsvImportService.RawTable doInBackground()throws Exception{return new tw.edu.nkust.surgerytime.service.MappedCsvImportService().readRaw(path,charset,100);}
            protected void done(){if(version!=previewVersion)return;try{var raw=get();var model=new javax.swing.table.DefaultTableModel(raw.headers().toArray(),0){public boolean isCellEditable(int r,int c){return false;}};
                raw.rows().forEach(r->model.addRow(r.toArray()));sourceTable.setModel(model);for(int i=0;i<sourceTable.getColumnCount();i++)sourceTable.getColumnModel().getColumn(i).setPreferredWidth(135);
                sourcePreviewStatus.setText("未套用來源："+path.getFileName()+"（最多 100 筆）");
            }catch(Exception ex){sourcePreviewStatus.setText("來源讀取失敗，請確認編碼："+ex.getMessage());}}
        }.execute();
    }

    private void chooseFolder() {
        var chooser = new JFileChooser();
        chooser.setDialogTitle("選擇月報表資料夾");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (var stream = Files.list(chooser.getSelectedFile().toPath())) {
            List<Path> csvFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            installCsvSelection(csvFiles);
        } catch (Exception exception) {
            showError("無法讀取資料夾：" + exception.getMessage());
        }
    }

    private void chooseFiles() {
        List<Path> selectedFiles = NativeFileDialogs.openFiles(
                this, "選擇一份或多份病例 CSV", "csv", true);
        if (selectedFiles.isEmpty()) return;
        installCsvSelection(selectedFiles);
    }

    void installCsvSelection(List<Path> csvFiles) {
        if (csvFiles.isEmpty()) {
            showError("所選位置沒有 CSV 檔案");
            return;
        }
        selection = new DataSourceSelection(DataSourceSelection.SourceType.CSV_FILES, csvFiles);
        fileListModel.clear();
        csvFiles.forEach(path -> fileListModel.addElement(path.toAbsolutePath().toString()));
        fileList.setSelectedIndex(0);
        selectionStatus.setText("已選擇 " + csvFiles.size() + " 份 CSV，可前往資料轉檔");
        statusSink.accept("資料來源已設定；下一步可驗證並合併月報表");
        selectionCompleted.run();
    }

    private void useSynthetic() {
        selection = DataSourceSelection.synthetic();
        fileListModel.clear();
        fileListModel.addElement("系統將依第二頁設定產生標準格式虛擬病例 CSV");
        selectionStatus.setText("已選擇系統虛擬病例");
        statusSink.accept("已選擇虛擬病例；下一步可產生標準 CSV");
        selectionCompleted.run();
    }

    private void showError(String message) {
        javax.swing.JOptionPane.showMessageDialog(this, message, "資料來源錯誤",
                javax.swing.JOptionPane.ERROR_MESSAGE);
    }
}
