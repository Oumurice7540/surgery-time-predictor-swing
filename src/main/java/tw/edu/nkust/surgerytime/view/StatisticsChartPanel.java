package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.DataStatisticsService.Group;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.List;

/** Scrollable chart: all groups stay visible, pie always uses counts. */
final class StatisticsChartPanel extends JPanel {
    private List<Group> groups = List.of();
    private boolean counts, pie, line;
    void setLine(boolean line) {this.line=line;repaint();}
    private String context="";
    void setContext(String text){context=text;}
    private String target = "手術時間";
    StatisticsChartPanel() { setBackground(Color.WHITE); }
    void setData(List<Group> groups, boolean counts, boolean pie, String target) {
        this.groups = List.copyOf(groups); this.counts = counts; this.pie = pie; this.target = target;
        int longest = groups.stream().mapToInt(g -> getFontMetrics(UiTheme.font(Font.PLAIN, 12f)).stringWidth(g.label())).max().orElse(150);
        setPreferredSize(new Dimension(Math.max(550, longest + 300), Math.max(400, (pie || line ? 370 : 70) + groups.size()*30)));
        revalidate(); repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); var g = (Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(UiTheme.font(Font.PLAIN, 12f)); g.setColor(UiTheme.TEXT);
            if (groups.isEmpty()) { g.drawString("尚無可顯示資料", 20, 30); return; }
            g.drawString(context,20,20);g.translate(0,20);
            g.drawString(pie ? "手術人次占比（有效人次）" : counts ? "手術人次" : target + "平均（分鐘）", 20, 26);
            if (pie) {
                double total = groups.stream().mapToLong(Group::count).sum(), angle = 90;
                for (int i=0; i<groups.size(); i++) {
                    var item=groups.get(i); double sweep=-360.0*item.count()/total;
                    g.setColor(color(i)); g.fill(new Arc2D.Double(30,45,270,270,angle,sweep,Arc2D.PIE)); angle+=sweep;
                    int y=350+i*30; g.fillRect(20,y-12,14,14); g.setColor(UiTheme.TEXT);
                    g.drawString(item.label()+String.format("  %d 人次（%.2f%%）",item.count(),item.percent()),44,y);
                }
            } else if(line) {
                int left=70,top=65,bottom=280,width=Math.max(200,getWidth()-120);
                double max=Math.max(1,groups.stream().mapToDouble(this::value).max().orElse(1));
                g.setColor(UiTheme.MUTED);g.drawLine(left,top,left,bottom);g.drawLine(left,bottom,left+width,bottom);
                g.drawString(String.format("%.2f",max),8,top);g.drawString("0",45,bottom);
                int px=0,py=0;
                for(int i=0;i<groups.size();i++) {
                    int x=left+(int)((double)i*width/Math.max(1,groups.size()-1));
                    int y=bottom-(int)((bottom-top)*value(groups.get(i))/max);
                    g.setColor(UiTheme.TEAL);if(i>0)g.drawLine(px,py,x,y);g.fillOval(x-4,y-4,8,8);
                    g.setColor(UiTheme.TEXT);g.drawString(Integer.toString(i+1),x-4,bottom+20);
                    g.drawString((i+1)+". "+groups.get(i).label()+String.format("  %.2f %s",value(groups.get(i)),counts?"人次":"分鐘"),20,335+i*30);
                    px=x;py=y;
                }
                g.drawString("橫軸為目前群組排列順序（非時間趨勢）",left,310);
            } else {
                int labelWidth=groups.stream().mapToInt(item->g.getFontMetrics().stringWidth(item.label())).max().orElse(150)+30;
                double maximum=groups.stream().mapToDouble(this::value).max().orElse(1);
                int width=Math.max(130,getWidth()-labelWidth-115);
                for(int i=0;i<groups.size();i++) {
                    var item=groups.get(i); int y=52+i*30; g.setColor(UiTheme.TEXT);g.drawString(item.label(),15,y+13);
                    g.setColor(UiTheme.TEAL);g.fillRoundRect(labelWidth,y,(int)Math.round(width*value(item)/Math.max(1,maximum)),18,6,6);
                    g.setColor(UiTheme.TEXT);g.drawString(counts ? item.count()+" 人次" : String.format("%.2f 分",item.average()),labelWidth+width+10,y+13);
                }
            }
        } finally { g.dispose(); }
    }
    private double value(Group item) { return counts ? item.count() : item.average(); }
    private Color color(int index) { return Color.getHSBColor((float)((index*.61803398875)%1),.52f,.90f); }
}
