package tw.edu.nkust.surgerytime.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import tw.edu.nkust.surgerytime.service.*;
import tw.edu.nkust.surgerytime.service.MappedCsvImportService.*;

/** Preparation stays separate from applying the validated training snapshot. */
final class ReportPreparationDialog extends JPanel {
    private RawTable current;
    private final JTextArea audit=new JTextArea("尚未修改來源。結果保留完整欄位；按完成後仍需欄位對應與驗證。\n");
    private final JTable table=new JTable();
    private final ReportConversionService converter=new ReportConversionService();
    ReportPreparationDialog(RawTable raw) {
        super(new BorderLayout(8,8));current=raw;
        var actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        var merge=new JButton("合併 DRG 對照");var duration=new JButton("由麻醉起訖計算分鐘");var export=new JButton("匯出完整轉檔資料");
        actions.add(merge);actions.add(duration);actions.add(export);add(actions,BorderLayout.NORTH);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setRowHeight(26);var dataScroll=new JScrollPane(table);dataScroll.setColumnHeaderView(table.getTableHeader());add(dataScroll);
        audit.setEditable(false);audit.setLineWrap(true);audit.setWrapStyleWord(true);var scroll=new JScrollPane(audit);scroll.setPreferredSize(new Dimension(800,150));add(scroll,BorderLayout.SOUTH);
        merge.addActionListener(e->merge());duration.addActionListener(e->duration());export.addActionListener(e->export());refresh();
    }
    private void refresh() {
        var model=new DefaultTableModel(current.headers().toArray(),0){public boolean isCellEditable(int r,int c){return false;}};
        current.rows().stream().limit(100).forEach(r->model.addRow(r.toArray()));table.setModel(model);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(145);
    }
    private void merge() {
        var files=NativeFileDialogs.openFiles(this,"選擇 DRG 對照 CSV","csv",false);if(files.isEmpty())return;
        var encoding=new JComboBox<String>(new String[]{"UTF-8","Big5","UTF-16LE"});
        if(JOptionPane.showConfirmDialog(this,encoding,"DRG 檔案編碼",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try {
            var lookup=new MappedCsvImportService().readRaw(files.getFirst(),Charset.forName((String)encoding.getSelectedItem()),100000);
            var from=new JComboBox<String>(current.headers().toArray(String[]::new));var to=new JComboBox<String>(lookup.headers().toArray(String[]::new));
            var added=new JList<String>(lookup.headers().toArray(String[]::new));added.setVisibleRowCount(8);
            var fields=new JPanel(new GridLayout(0,1,4,4));fields.add(new JLabel("病例對應鍵"));fields.add(from);fields.add(new JLabel("DRG 對應鍵"));fields.add(to);fields.add(new JLabel("加入欄位（Ctrl 多選；新增欄名加 DRG_ 前綴）"));fields.add(new JScrollPane(added));
            if(JOptionPane.showConfirmDialog(this,fields,"設定 DRG 左連接",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            var joined=converter.merge(current,lookup,from.getSelectedIndex(),to.getSelectedIndex(),Arrays.stream(added.getSelectedIndices()).boxed().toList());
            current=joined.table();audit.append("DRG：匹配 "+joined.matchedRows()+" 筆，未匹配 "+joined.unmatchedKeys().size()+" 筆；病例總數 "+current.rows().size()+" 不變。未匹配鍵："+joined.unmatchedKeys()+"\n");refresh();
        }catch(Exception e){failure(e);}
    }
    private void duration() {
        var start=new JComboBox<String>(current.headers().toArray(String[]::new));var end=new JComboBox<String>(current.headers().toArray(String[]::new));
        var midnight=new JCheckBox("只有時刻且結束較早時，明確假設為隔日（小於 24 小時）");
        var fields=new JPanel(new GridLayout(0,1,4,4));fields.add(new JLabel("開始欄位（HH:mm[:ss] 或 yyyy-MM-dd HH:mm[:ss]）"));fields.add(start);fields.add(new JLabel("結束欄位"));fields.add(end);fields.add(midnight);fields.add(new JLabel("新增麻醉分鐘；秒數四捨五入至整數分鐘。相同時刻無日期視為歧義。"));
        if(JOptionPane.showConfirmDialog(this,fields,"麻醉時間計算",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        try {current=converter.duration(current,start.getSelectedIndex(),end.getSelectedIndex(),midnight.isSelected());audit.append("已計算麻醉分鐘（秒數四捨五入）。跨午夜假設："+midnight.isSelected()+"。請將新欄對應至預測目標。\n");refresh();}catch(Exception e){failure(e);}
    }
    private void export() {
        var chosen=NativeFileDialogs.saveFile(this,"匯出完整轉檔資料","converted-full.csv","csv");if(chosen.isEmpty())return;
        if(Files.exists(chosen.get())&&JOptionPane.showConfirmDialog(this,"覆寫此檔案？","確認",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{MappedCsvImportService.writeRaw(current,chosen.get());Files.writeString(chosen.get().resolveSibling(chosen.get().getFileName()+".audit.txt"),audit.getText());audit.append("完整資料已匯出："+chosen.get()+"\n");}catch(Exception e){failure(e);}
    }
    private void failure(Exception e){audit.append("未套用此次操作："+e.getMessage()+"\n");JOptionPane.showMessageDialog(this,e.getMessage(),"轉檔檢查",JOptionPane.WARNING_MESSAGE);}
    static Source choose(Component owner,Source source) throws Exception {
        var raw=new MappedCsvImportService().readRaw(source.path(),source.charset(),100000);
        var panel=new ReportPreparationDialog(raw);panel.setPreferredSize(new Dimension(960,550));
        if(JOptionPane.showConfirmDialog(owner,panel,"DRG／麻醉時間轉檔："+source.path().getFileName(),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return null;
        Path output=Files.createTempFile("surgery-prepared-",".csv");MappedCsvImportService.writeRaw(panel.current,output);
        Files.writeString(output.resolveSibling(output.getFileName()+".audit.txt"),"原始來源："+source.path()+"\n"+panel.audit.getText());
        return new MappedCsvImportService().inspect(output,java.nio.charset.StandardCharsets.UTF_8);
    }
}
