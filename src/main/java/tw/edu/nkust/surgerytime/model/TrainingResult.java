package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 將訓練結果頁面需要的四類資料組合成一個完整且不可變的結果。
 *
 * <p>「aggregate（聚合物件）」表示它不是單一數值，而是把摘要、曲線、
 * 特徵與測試案例集中在一起。View 只需要取得一個 {@code TrainingResult}，
 * 不必分別向 Service 要四次資料。</p>
 *
 * @param summary 訓練摘要
 * @param epochRmseHistory RMSE 曲線上的資料點
 * @param featureImportances 展示用特徵重要度
 * @param predictionComparisons 驗證案例的實際值與預測值
 */
public record TrainingResult(
        TrainingSummary summary,
        List<EpochRmsePoint> epochRmseHistory,
        List<FeatureImportance> featureImportances,
        List<PredictionComparison> predictionComparisons,
        EvaluationMetrics trainingMetrics, EvaluationMetrics testMetrics,
        List<PredictionComparison> trainingComparisons
) implements Serializable {
    public TrainingResult(TrainingSummary summary,List<EpochRmsePoint> curve,List<FeatureImportance> importance,List<PredictionComparison> comparisons) {
        this(summary,curve,importance,comparisons,null,null,List.of());
    }
    public TrainingResult(TrainingSummary summary,List<EpochRmsePoint> curve,List<FeatureImportance> importance,List<PredictionComparison> comparisons,EvaluationMetrics training,EvaluationMetrics test) {
        this(summary,curve,importance,comparisons,training,test,List.of());
    }
    private static final long serialVersionUID = 1L;
    /** 浮點數相加可能有極小誤差，因此允許百萬分之一的差距。 */
    private static final double IMPORTANCE_SUM_TOLERANCE = 0.000_001;

    /** 建立完整結果時，同時驗證四類資料彼此是否一致。 */
    public TrainingResult {
        summary = Objects.requireNonNull(summary, "訓練摘要不可為空");

        // List.copyOf 會建立不可變清單，避免畫面顯示期間資料被其他程式修改。
        epochRmseHistory = immutableNonEmptyCopy(epochRmseHistory, "Epoch/RMSE 歷程");
        featureImportances = immutableNonEmptyCopy(featureImportances, "特徵重要度");
        predictionComparisons = immutableNonEmptyCopy(predictionComparisons, "實際/預測明細");

        trainingComparisons=trainingComparisons==null?List.of():List.copyOf(trainingComparisons);
        if(!trainingComparisons.isEmpty())validatePredictionComparisons(trainingComparisons,summary.trainingSampleCount());
        validateEpochHistory(epochRmseHistory, summary.completedEpochs());
        validateFeatureImportances(featureImportances, summary.featureCount());
        validatePredictionComparisons(predictionComparisons, summary.validationSampleCount());
    }

    /**
     * 建立不可變且至少包含一筆資料的清單副本。
     * {@code <T>} 表示此方法可以處理任何資料類型的 List。
     */
    private static <T> List<T> immutableNonEmptyCopy(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不可為空");
        var copy = List.copyOf(values);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return copy;
    }

    /** 確認 Epoch 依序增加，而且最後一點等於摘要所寫的完成週期。 */
    private static void validateEpochHistory(List<EpochRmsePoint> history, int completedEpochs) {
        int previousEpoch = -1;
        for (var point : history) {
            if (point.epoch() <= previousEpoch) {
                throw new IllegalArgumentException("Epoch/RMSE 歷程必須依訓練週期遞增排列");
            }
            previousEpoch = point.epoch();
        }
        if (history.getLast().epoch() != completedEpochs) {
            throw new IllegalArgumentException("最後一筆訓練週期必須等於摘要中的完成週期");
        }
    }

    /**
     * 確認特徵數量、名稱唯一性及重要度總和。
     * 展示百分比的總和必須約等於 1，也就是 100%。
     */
    private static void validateFeatureImportances(
            List<FeatureImportance> importances,
            int expectedFeatureCount
    ) {
        if (importances.size() != expectedFeatureCount) {
            throw new IllegalArgumentException("特徵重要度筆數必須符合摘要中的特徵數量");
        }

        // HashSet 不允許重複值，很適合用來檢查重複的特徵名稱。
        var featureNames = new HashSet<String>();
        double total = 0;
        for (var feature : importances) {
            if (!featureNames.add(feature.featureName())) {
                throw new IllegalArgumentException("特徵名稱不可重複");
            }
            total += feature.importance();
        }
        if (total != 0 && Math.abs(total - 1.0) > IMPORTANCE_SUM_TOLERANCE) {
            throw new IllegalArgumentException("特徵重要度總和必須等於 1");
        }
    }

    /**
     * 確認畫面明細筆數等於完整測試集，並且每個案例編號都不重複。
     */
    private static void validatePredictionComparisons(
            List<PredictionComparison> comparisons,
            int expectedValidationCount
    ) {
        if (comparisons.size() != expectedValidationCount) {
            throw new IllegalArgumentException("實際/預測明細必須包含完整測試資料");
        }

        var caseIds = new HashSet<String>();
        for (var comparison : comparisons) {
            if (!caseIds.add(comparison.caseId())) {
                throw new IllegalArgumentException("案例編號不可重複");
            }
        }
    }
}
