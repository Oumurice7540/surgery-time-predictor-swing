package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.model.ProcessingTable;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.*;

/** Read-only raw-column previews, laid out like the reference desktop workflow. */
final class ReferenceDataPreview extends JPanel {
    private final SurgeryModelService service;
    private final boolean split;
    private final int orientation;
    private boolean processed;
    private final JComboBox<String> mode=new JComboBox<>(new String[]{"原始欄位","模型輸入欄位（訓練集估計）"});
    private double currentRatio;private long currentSeed;

    ReferenceDataPreview(SurgeryModelService service, boolean split) {
        this(service,split,JSplitPane.VERTICAL_SPLIT);
    }
    ReferenceDataPreview(SurgeryModelService service,boolean split,int orientation) {
        super(new BorderLayout(0, 6)); this.service=service; this.split=split; this.orientation=orientation;
        setMinimumSize(new Dimension(280, 250));
        mode.setName("reference-preview-mode");
        mode.addActionListener(e->{processed=mode.getSelectedIndex()==1;refresh(currentRatio,currentSeed);});refresh();
    }
    void refresh() { refresh(service.pendingTrainingRatio(),service.pendingRandomSeed()); }
    void refresh(double ratio,long seed) {
        currentRatio=ratio;currentSeed=seed;
        removeAll(); var table=service.processingCurrent().table();
        if(split)add(mode,BorderLayout.NORTH);
        if(split&&processed) {
            try {
                var preview=service.modelDataPreview(ratio,seed);
                var panes=new JSplitPane(orientation,encoded("訓練集",preview.columns(),preview.training()),encoded("測試集",preview.columns(),preview.test()));
                panes.setResizeWeight(.5);add(panes);
                var settings=new JTextArea("實際交給 WEKA 的數值欄位；分類器內部正規化另由 WEKA 在訓練集估計。\n"+preview.settings());settings.setEditable(false);settings.setLineWrap(true);settings.setWrapStyleWord(true);
                var scroll=new JScrollPane(settings);scroll.setPreferredSize(new Dimension(200,85));add(scroll,BorderLayout.SOUTH);
            }catch(RuntimeException e){add(new JLabel("無法建立模型輸入預覽："+e.getMessage()));}
            revalidate();repaint();return;
        }
        var columns=table.columns().stream().filter(c->c.feature()==null ? (!c.key().startsWith("extra:") || service.selectedFeatureGroups().contains(c.key())) : service.selectedFeatureGroups().contains(c.feature())).toList();
        if(split) {
            var rows=new ArrayList<>(table.rows()); Collections.shuffle(rows,new Random(seed ^ 0x5DEECE66DL));
            int cut=(int)Math.round(rows.size()*ratio);
            var panes=new JSplitPane(orientation,
                table("訓練集",columns,rows.subList(0,cut)),table("測試集",columns,rows.subList(cut,rows.size())));
            panes.setResizeWeight(.5); panes.setDividerLocation(.5); panes.setBorder(null); panes.setDividerSize(6);
            add(panes);
        } else {
            add(table("目前資料",table.columns(),table.rows()));
            var state=new JLabel(service.hasGeneratedData()?"已套用："+service.generatedCsvPath().getFileName():"尚無已套用資料");
            if(service.hasGeneratedData())state.setToolTipText(service.generatedCsvPath().toString());add(state,BorderLayout.NORTH);
        }
        revalidate(); repaint();
    }
    private JComponent encoded(String title,List<String> columns,List<List<Object>> rows){
        var p=new JPanel(new BorderLayout());p.add(new JLabel(title+"："+rows.size()+" 筆；模型輸入前 100 筆"),BorderLayout.NORTH);
        var model=new DefaultTableModel(columns.stream().map(n->n.replace("extra:","")).toArray(),0){public boolean isCellEditable(int r,int c){return false;}};
        rows.stream().limit(100).forEach(r->model.addRow(r.toArray()));var table=new JTable(model);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setRowHeight(25);for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(160);var scroll=new JScrollPane(table);scroll.setColumnHeaderView(table.getTableHeader());p.add(scroll);return p;
    }
    private JComponent table(String title,List<ProcessingTable.Column> columns,List<ProcessingTable.Row> rows) {
        var panel=new JPanel(new BorderLayout(0,4));panel.setBackground(UiTheme.SURFACE);
        var heading=new JLabel(title+"："+rows.size()+" 筆（原始欄位，預覽前 100 筆）");heading.setBorder(UiTheme.padding(6,8,6,8));panel.add(heading,BorderLayout.NORTH);
        var model=new DefaultTableModel(columns.stream().map(ProcessingTable.Column::label).toArray(),0){@Override public boolean isCellEditable(int r,int c){return false;}};
        rows.stream().limit(100).forEach(row->model.addRow(columns.stream().map(c->row.values().getOrDefault(c.key(),"")).toArray()));
        var grid=new JTable(model);grid.setName(title.equals("訓練集")?"reference-training-preview":title.equals("測試集")?"reference-test-preview":"reference-data-preview");
        grid.setRowHeight(25);grid.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);grid.setFillsViewportHeight(true);
        for(int i=0;i<grid.getColumnCount();i++)grid.getColumnModel().getColumn(i).setPreferredWidth(115);
        var scroll=new JScrollPane(grid);scroll.setColumnHeaderView(grid.getTableHeader());panel.add(scroll);
        panel.setMinimumSize(new Dimension(200,150));return panel;
    }
}
