package tw.edu.nkust.surgerytime.service;

import java.util.*;
import tw.edu.nkust.surgerytime.model.*;

/** Reproducible holdout first; estimated outlier bounds never see test data. */
final class TrainingPartition {
    record Split(List<ProcessingTable.Row> training,List<ProcessingTable.Row> test,String audit) { }
    static Split split(ProcessingTable table,List<ProcessingStep> steps,double ratio,long seed) {
        var rows=new ArrayList<>(table.rows());Collections.shuffle(rows,new Random(seed ^ 0x5DEECE66DL));
        int cut=(int)Math.round(rows.size()*ratio);List<ProcessingTable.Row> training=List.copyOf(rows.subList(0,cut));
        var audit=new StringBuilder();
        for(var step:steps)if(step.operation()==ProcessingStep.Operation.IQR) {
            table.column(step.column());
            var numbers=training.stream().map(r->r.values().getOrDefault(step.column(),"")).filter(v->!v.isBlank()).map(ProcessingStep::number).sorted().toList();
            if(numbers.size()<4)throw new IllegalArgumentException("IQR 訓練集有效數值不足 4 筆："+step.column());
            double q1=numbers.get((int)Math.ceil(numbers.size()*.25)-1),q3=numbers.get((int)Math.ceil(numbers.size()*.75)-1),factor=ProcessingStep.number(step.value());
            double low=q1-factor*(q3-q1),high=q3+factor*(q3-q1);
            if(!Double.isFinite(low)||!Double.isFinite(high))throw new IllegalArgumentException("IQR 界線超出數值範圍");
            int before=training.size();training=training.stream().filter(r->{String value=r.values().getOrDefault(step.column(),"");if(value.isBlank())return true;double n=ProcessingStep.number(value);return n>=low&&n<=high;}).toList();
            audit.append("IQR ").append(step.column()).append("：訓練集界線 [").append(low).append(", ").append(high).append("]；訓練列 ").append(before).append(" → ").append(training.size()).append("，測試集不刪除。\n");
        }
        if(training.size()<2||rows.size()-cut<1)throw new IllegalArgumentException("拆分／IQR 後訓練或測試資料不足");
        return new Split(training,List.copyOf(rows.subList(cut,rows.size())),audit.toString());
    }
}
