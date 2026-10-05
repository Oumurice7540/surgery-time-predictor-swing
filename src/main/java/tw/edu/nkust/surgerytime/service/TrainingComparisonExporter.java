package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.TrainingRun;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** 匯出顯示用比較資料；含完整精度與全部測試列，先寫暫存檔再取代目的檔。 */
public final class TrainingComparisonExporter {
    public void exportSummary(List<TrainingRun> runs, Path path) throws IOException {
        write(path, writer -> {
            row(writer,List.of("紀錄","時間UTC","比較組SHA256","預測目標","演算法","參數","特徵","訓練筆數","測試筆數","訓練比例","種子","MAE分鐘","RMSE分鐘","R2","耗時毫秒"));
            for(var run:runs) {
                var s=run.result().summary(); var c=run.configuration();
                row(writer,List.of(run.id(),run.createdAt(),run.comparisonGroup(),run.target(),run.method().algorithm(),
                    run.method().description(c),String.join("、",run.features()),s.trainingSampleCount(),s.validationSampleCount(),
                    c.trainingRatio(),c.randomSeed(),s.meanAbsoluteErrorMinutes(),s.rootMeanSquaredErrorMinutes(),s.rSquared(),s.trainingDurationMillis()));
            }
        });
    }
    public void exportPredictions(TrainingRun run, Path path) throws IOException {
        write(path, writer -> {
            row(writer,List.of("紀錄","比較組SHA256","預測目標","演算法","案例編號","實際分鐘","預測分鐘","差值_實際減預測","絕對誤差分鐘"));
            for(var c:run.result().predictionComparisons()) row(writer,List.of(run.id(),run.comparisonGroup(),run.target(),run.method().algorithm(),
                c.caseId(),c.actualMinutes(),c.predictedMinutes(),c.actualMinutes()-c.predictedMinutes(),c.absoluteErrorMinutes()));
        });
    }
    private interface WriterAction { void accept(Writer writer) throws IOException; }
    private void write(Path path, WriterAction action) throws IOException {
        Path target=path.toAbsolutePath(); Path temp=Files.createTempFile(target.getParent(),"comparison-",".tmp");
        try {
            try(var writer=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)) { writer.write('\uFEFF'); action.accept(writer); }
            try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private void row(Writer writer,List<?> values) throws IOException {
        for(int i=0;i<values.size();i++) {
            if(i>0) writer.write(','); Object value=values.get(i); String text=String.valueOf(value);
            // CSV 供試算表開啟；來源文字不應變成公式，數值則維持原有正負號。
            String stripped=text.stripLeading();
            if(!(value instanceof Number) && !stripped.isEmpty() && "=+@-".indexOf(stripped.charAt(0))>=0) text="'"+text;
            writer.write('"'); writer.write(text.replace("\"","\"\"")); writer.write('"');
        }
        writer.write("\r\n");
    }
}
