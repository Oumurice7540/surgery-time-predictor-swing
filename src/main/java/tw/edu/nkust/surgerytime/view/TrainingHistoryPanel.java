package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.TrainingRun;
import tw.edu.nkust.surgerytime.service.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.util.List;

/** 可排序、同條件分組的本次執行歷史。模型切換只改目前模型，不改待訓練資料。 */
public final class TrainingHistoryPanel extends JPanel {
    private final SurgeryModelService service;
    private final Runnable activated;
    private final JCheckBox sameGroup = new JCheckBox("只看與使用中模型相同條件",true);
    private final JLabel detail = new JLabel("選取紀錄查看參數");
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"紀錄","使用中","演算法","MAE 分鐘 ↓","RMSE 分鐘 ↓","R² ↑","耗時 ms","訓練筆數","測試筆數","目標","比較組","參數"},0) {
        public boolean isCellEditable(int r,int c){return false;}
        public Class<?> getColumnClass(int c){return switch(c){case 0,6 -> Long.class; case 3,4,5 -> Double.class; case 7,8 -> Integer.class; default -> String.class;};}
    };
    private final JTable table=new JTable(model);
    private List<TrainingRun> displayed=List.of();
    private final JButton activate=new JButton("使用選取模型");
    private final JButton summary=new JButton("匯出目前比較表");
    private final JButton predictions=new JButton("匯出選取預測明細");
    private final JButton save=new JButton("儲存選取模型");
    public TrainingHistoryPanel(SurgeryModelService service,Runnable activated) {
        this.service=service; this.activated=activated; setName("training-history-panel");
        setLayout(new BorderLayout(6,8)); setOpaque(true); setBackground(Color.WHITE); setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
        var top=new JPanel(new GridLayout(0,1)); top.setOpaque(false);
        top.add(new JLabel("歷次訓練比較｜本次開啟期間保留；關閉前可匯出結果及模型。"));
        top.add(new JLabel("同一比較組才是相同資料、特徵、目標與拆分；MAE／RMSE 越低越好，R² 越高越好。"));
        sameGroup.setOpaque(false); sameGroup.setName("history-same-group"); top.add(sameGroup); add(top,BorderLayout.NORTH);
        table.setName("training-history-table"); table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setRowHeight(28);
        for(int i=0;i<table.getColumnCount();i++) table.getColumnModel().getColumn(i).setPreferredWidth(i==11?420:i==2?270:i==10?130:110);
        var scroll=new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader()); add(scroll,BorderLayout.CENTER);
        var bottom=new JPanel(new BorderLayout()); bottom.setOpaque(false); bottom.add(detail,BorderLayout.NORTH);
        var buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0)); buttons.setOpaque(false);
        activate.setName("activate-history-model"); summary.setName("export-history-summary"); predictions.setName("export-history-predictions"); save.setName("save-history-model");
        for(var button:List.of(activate,summary,predictions,save)) buttons.add(button);
        bottom.add(buttons,BorderLayout.SOUTH); add(bottom,BorderLayout.SOUTH);
        sameGroup.addActionListener(e->refresh()); table.getSelectionModel().addListSelectionListener(e->selectionChanged());
        activate.addActionListener(e->{var run=selected(); if(run!=null) background(()->service.activateRun(run.id()),activated);});
        summary.addActionListener(e->{var rows=java.util.stream.IntStream.range(0,table.getRowCount()).mapToObj(i->displayed.get(table.convertRowIndexToModel(i))).toList(); var path=choose("模型比較.csv","csv"); if(path!=null) background(()->new TrainingComparisonExporter().exportSummary(rows,path),()->detail.setText("比較表已匯出："+path));});
        predictions.addActionListener(e->{var run=selected(); if(run==null)return; var path=choose("預測明細_"+run.id()+".csv","csv"); if(path!=null) background(()->new TrainingComparisonExporter().exportPredictions(run,path),()->detail.setText("完整測試明細已匯出："+path));});
        save.addActionListener(e->{var run=selected(); if(run==null)return; var path=choose("模型_"+run.id()+"_"+run.method().algorithm().name()+".model","model"); if(path!=null) background(()->service.saveRunModel(run.id(),path),()->detail.setText("模型已儲存："+path));});
        setPreferredSize(new Dimension(1100,330)); setMaximumSize(new Dimension(Integer.MAX_VALUE,330)); setAlignmentX(LEFT_ALIGNMENT); refresh();
    }
    private Path choose(String name,String extension) {
        var chosen=NativeFileDialogs.saveFile(this,"匯出訓練紀錄",name,extension); if(chosen.isEmpty())return null;
        Path path=chosen.get(); if(Files.exists(path) && JOptionPane.showConfirmDialog(this,"覆寫既有檔案？","確認覆寫",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;
        return path;
    }
    void refresh() {
        var all=service.trainingHistory(); var active=all.stream().filter(r->r.id()==service.activeRunId()).findFirst();
        sameGroup.setEnabled(active.isPresent());
        displayed=all.stream().filter(r->!sameGroup.isSelected()||active.isEmpty()||r.comparisonGroup().equals(active.get().comparisonGroup())).toList();
        model.setRowCount(0); int activeRow=-1;
        for(var run:displayed) {
            var s=run.result().summary(); if(run.id()==service.activeRunId())activeRow=model.getRowCount();
            model.addRow(new Object[]{run.id(),run.id()==service.activeRunId()?"目前":"",run.method().algorithm().toString(),s.meanAbsoluteErrorMinutes(),s.rootMeanSquaredErrorMinutes(),s.rSquared(),s.trainingDurationMillis(),s.trainingSampleCount(),s.validationSampleCount(),run.target(),run.comparisonGroup().substring(0,12),run.method().description(run.configuration())});
        }
        if(activeRow>=0){int row=table.convertRowIndexToView(activeRow);table.setRowSelectionInterval(row,row);}
        selectionChanged(); summary.setEnabled(!displayed.isEmpty());
    }
    private TrainingRun selected(){int row=table.getSelectedRow();return row<0?null:displayed.get(table.convertRowIndexToModel(row));}
    private void selectionChanged(){var run=selected(); for(var b:List.of(activate,predictions,save))b.setEnabled(run!=null); detail.setText(run==null?"尚無選取紀錄（載入的外部模型沒有本次比較條件）":"#"+run.id()+" · "+run.method().description(run.configuration())+" · 訓練比例 "+Math.round(run.configuration().trainingRatio()*100)+"% · 種子 "+run.configuration().randomSeed());}
    private interface Work {void run() throws Exception;}
    private void background(Work work,Runnable success){
        for(var b:List.of(activate,summary,predictions,save))b.setEnabled(false); table.setEnabled(false);sameGroup.setEnabled(false);
        new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws Exception {work.run();return null;}
            protected void done(){
                table.setEnabled(true); refresh();
                try { get(); success.run(); }
                catch(Exception e) { detail.setText("操作失敗："+(e.getCause()==null?e.getMessage():e.getCause().getMessage())); }
            }
        }.execute();
    }
}
