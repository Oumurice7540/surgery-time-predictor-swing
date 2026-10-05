package tw.edu.nkust.surgerytime.service;

import java.util.*;
import java.time.*;
import tw.edu.nkust.surgerytime.service.MappedCsvImportService.RawTable;

/** Explicit left join and duration conversion. Never expands the surgery grain. */
public final class ReportConversionService {
    public record JoinResult(RawTable table,List<String> unmatchedKeys,int matchedRows) { }
    public JoinResult merge(RawTable cases,RawTable lookup,int sourceKey,int lookupKey,List<Integer> added) {
        if(added.isEmpty()) throw new IllegalArgumentException("請選擇要加入的 DRG 欄位");
        var index=new LinkedHashMap<String,List<String>>();var duplicates=new TreeSet<String>();
        for(var row:lookup.rows()) {
            String key=row.get(lookupKey).strip();
            if(key.isEmpty()) throw new IllegalArgumentException("DRG 對照鍵含空白，請修正對照檔");
            if(index.putIfAbsent(key,row)!=null) duplicates.add(key);
        }
        if(!duplicates.isEmpty()) throw new IllegalArgumentException("DRG 重複鍵（已拒絕合併，不增加病例）："+duplicates);
        var headers=new ArrayList<>(cases.headers());
        for(int col:added) {String name="DRG_"+lookup.headers().get(col);if(headers.contains(name))throw new IllegalArgumentException("合併欄名衝突："+name);headers.add(name);}
        var rows=new ArrayList<List<String>>();var unmatched=new ArrayList<String>();int matched=0;
        for(var row:cases.rows()) {
            String key=row.get(sourceKey).strip();var match=index.get(key);var merged=new ArrayList<>(row);
            if(match==null) unmatched.add(key.isEmpty()?"（空白鍵）":key);else matched++;
            for(int col:added)merged.add(match==null?"":match.get(col));rows.add(merged);
        }
        return new JoinResult(new RawTable(headers,rows),List.copyOf(unmatched),matched);
    }
    public RawTable duration(RawTable source,int start,int end,boolean allowMidnight) {
        if(start==end)throw new IllegalArgumentException("開始與結束必須是不同欄位");
        String name="麻醉分鐘";
        if(source.headers().contains(name))throw new IllegalArgumentException("已存在麻醉分鐘欄位，請勿重複轉換");
        var headers=new ArrayList<>(source.headers());headers.add(name);var rows=new ArrayList<List<String>>();int line=1;
        for(var row:source.rows()) {
            line++;try {var values=new ArrayList<>(row);values.add(Long.toString(minutes(row.get(start),row.get(end),allowMidnight)));rows.add(values);}
            catch(RuntimeException e){throw new IllegalArgumentException("第 "+line+" 列："+e.getMessage(),e);}
        }
        return new RawTable(headers,rows);
    }
    public long minutes(String start,String end,boolean allowMidnight) {
        String a=start.strip(),b=end.strip();if(a.isEmpty()||b.isEmpty())throw new IllegalArgumentException("麻醉開始／結束時間缺值");
        Duration duration;
        try {
            if(a.contains("-")||b.contains("-")) duration=Duration.between(LocalDateTime.parse(a.replace(' ','T')),LocalDateTime.parse(b.replace(' ','T')));
            else {
                LocalTime from=LocalTime.parse(a),to=LocalTime.parse(b);
                if(from.equals(to))throw new IllegalArgumentException("相同時刻且沒有日期，無法判斷 0 或 24 小時");
                duration=Duration.between(from,to);
                if(duration.isNegative()) {if(!allowMidnight)throw new IllegalArgumentException("結束早於開始：請提供日期或確認跨午夜假設");duration=duration.plusDays(1);}
            }
        }catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("時間格式須為 HH:mm[:ss] 或 yyyy-MM-dd HH:mm[:ss]，兩欄須一致");}
        if(duration.isZero()||duration.isNegative()||duration.toMinutes()>10080)throw new IllegalArgumentException("麻醉時長須大於 0 且不超過 7 天，請確認日期");
        // Existing model target contract uses integer minutes; make rounding explicit in UI.
        long result=Math.round(duration.toMillis()/60000.0);
        if(result<1)throw new IllegalArgumentException("時長四捨五入不足 1 分鐘");return result;
    }
}
