package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.SurgeryModelService;
import tw.edu.nkust.surgerytime.model.ProcessingTable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Before/after data on the left; selectable input scores on the right. */
public final class FeatureSelectionView extends WorkflowPagePanel {
    private final SurgeryModelService service;
    private final Consumer<String> status;
    private final Runnable completed;
    private final DefaultTableModel features=new DefaultTableModel(new String[]{"選取","欄名","訓練前分數","方法／有效筆數"},0) {
        public Class<?> getColumnClass(int col){return col==0?Boolean.class:String.class;}
        public boolean isCellEditable(int row,int col){return col==0;}
    };
    private final DefaultTableModel before=readonly(),after=readonly();
    private final JLabel note=new JLabel("評分只使用目前拆分的訓練集；尚未評分。");
    private final java.util.List<String> featureKeys=new ArrayList<>();
    private long revision=-1;
    private double scoredRatio=-1;private long scoredSeed=-1;
    public FeatureSelectionView(SurgeryModelService service,Consumer<String> status,Runnable completed) {
        super("特徵選取","步驟 5 / 9");this.service=service;this.status=status;this.completed=completed;
        var left=new JSplitPane(JSplitPane.VERTICAL_SPLIT,grid("選取前資料（原始欄位）",before),grid("選取後資料（勾選預覽，尚未套用）",after));left.setResizeWeight(.5);
        var right=new JPanel(new BorderLayout(4,6));var table=new JTable(features);table.setName("feature-selection-table");table.setRowHeight(28);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths={50,150,95,180};for(int i=0;i<4;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        var featureScroll=new JScrollPane(table);featureScroll.setColumnHeaderView(table.getTableHeader());right.add(featureScroll);
        var actions=new JPanel();actions.setLayout(new BoxLayout(actions,BoxLayout.Y_AXIS));
        var scoreHelp=ViewSupport.wrappedText("數值：Pearson r²；類別：相關比 η²。缺值不計；常數輸入為 0，常數目標／全缺值為無法評分。高基數類別分數可能偏高。這是單欄關聯，與訓練後測試集 permutation importance 不同。識別碼及目標不供選取。",12f,UiTheme.MUTED);scoreHelp.setPreferredSize(new Dimension(380,76));scoreHelp.setMinimumSize(new Dimension(0,76));scoreHelp.setMaximumSize(new Dimension(Integer.MAX_VALUE,76));actions.add(scoreHelp);
        actions.add(note);var score=new JButton("使用訓練集評分");score.setName("pretraining-score");actions.add(score);score.addActionListener(e->{score.setEnabled(false);new SwingWorker<List<SurgeryModelService.FeatureScore>,Void>(){
            protected List<SurgeryModelService.FeatureScore> doInBackground(){return service.scoreFeatures();}
            protected void done(){try{var values=get();for(int i=0;i<features.getRowCount();i++){String name=featureKeys.get(i);var item=values.stream().filter(v->v.feature().equals(name)).findFirst().orElseThrow();features.setValueAt(Double.isFinite(item.score())?String.format("%.5f",item.score()):"無法評分",i,2);features.setValueAt(item.method()+" / "+item.count(),i,3);}scoredRatio=service.pendingTrainingRatio();scoredSeed=service.pendingRandomSeed();note.setText("訓練集評分；種子 "+scoredSeed+"，比例 "+scoredRatio);}catch(Exception ex){note.setText("評分失敗："+ex.getMessage());}finally{score.setEnabled(true);}}
        }.execute();});
        var all=new JButton("全部選取");actions.add(all);all.addActionListener(e->{for(int i=0;i<features.getRowCount();i++)features.setValueAt(true,i,0);});
        var apply=new JButton("套用特徵選取");actions.add(apply);apply.addActionListener(e->{try{service.setSelectedFeatureGroups(selected());status.accept("已套用 "+selected().size()+" 項特徵");completed.run();}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage());}});
        right.add(actions,BorderLayout.SOUTH);right.setMinimumSize(new Dimension(360,300));
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,right);split.setResizeWeight(.65);split.setPreferredSize(new Dimension(1100,545));content.add(split);
        features.addTableModelListener(e->updateAfter());refreshData();
    }
    public void refreshData(){
        if(revision!=service.processingRevision()||scoredRatio!=service.pendingTrainingRatio()||scoredSeed!=service.pendingRandomSeed()) {
            features.setRowCount(0);featureKeys.clear();for(String name:service.availableFeatureGroups()){featureKeys.add(name);features.addRow(new Object[]{service.selectedFeatureGroups().contains(name),name.replaceFirst("^extra:",""),"尚未評分",""});}
            revision=service.processingRevision();scoredRatio=service.pendingTrainingRatio();scoredSeed=service.pendingRandomSeed();note.setText("目前拆分尚未評分（只使用訓練集）。");
        }else for(int i=0;i<features.getRowCount();i++)features.setValueAt(service.selectedFeatureGroups().contains(featureKeys.get(i)),i,0);
        var table=service.processingCurrent().table();fill(before,table,table.columns());updateAfter();
    }
    private List<String> selected(){var selected=new ArrayList<String>();for(int i=0;i<features.getRowCount();i++)if(Boolean.TRUE.equals(features.getValueAt(i,0)))selected.add(featureKeys.get(i));return selected;}
    private void updateAfter(){var table=service.processingCurrent().table();var selected=selected();fill(after,table,table.columns().stream().filter(c->c.key().equals("case_id")||c.key().equals("actual_minutes")||selected.contains(c.feature())||selected.contains(c.key())).toList());}
    private static DefaultTableModel readonly(){return new DefaultTableModel(){public boolean isCellEditable(int r,int c){return false;}};}
    private static void fill(DefaultTableModel model,ProcessingTable data,List<ProcessingTable.Column> cols){model.setColumnIdentifiers(cols.stream().map(ProcessingTable.Column::label).toArray());model.setRowCount(0);data.rows().stream().limit(100).forEach(r->model.addRow(cols.stream().map(c->r.values().getOrDefault(c.key(),"")).toArray()));}
    private static JComponent grid(String title,DefaultTableModel model){var p=new JPanel(new BorderLayout());p.add(new JLabel(title+"；最多 100 筆"),BorderLayout.NORTH);var t=new JTable(model);model.addTableModelListener(e->{for(int i=0;i<t.getColumnCount();i++)t.getColumnModel().getColumn(i).setPreferredWidth(145);});t.setRowHeight(25);t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);var scroll=new JScrollPane(t);scroll.setColumnHeaderView(t.getTableHeader());p.add(scroll);p.setMinimumSize(new Dimension(200,180));return p;}
}
