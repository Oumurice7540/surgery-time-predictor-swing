package tw.edu.nkust.surgerytime.model;

import java.time.Instant;
import java.util.List;

/** 一次成功訓練的不可變結果；比較組由資料內容、特徵、目標與拆分設定決定。 */
public record TrainingRun(long id, Instant createdAt, String comparisonGroup, String target,
                          List<String> features, TrainingConfiguration configuration,
                          AlgorithmConfiguration method, TrainingResult result) {
    public TrainingRun { features = List.copyOf(features); }
}
