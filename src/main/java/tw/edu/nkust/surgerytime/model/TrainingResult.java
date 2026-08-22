package tw.edu.nkust.surgerytime.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable aggregate consumed by the training-results view.
 */
public record TrainingResult(
        TrainingSummary summary,
        List<EpochRmsePoint> epochRmseHistory,
        List<FeatureImportance> featureImportances,
        List<PredictionComparison> predictionComparisons
) {
    private static final double IMPORTANCE_SUM_TOLERANCE = 0.000_001;

    public TrainingResult {
        summary = Objects.requireNonNull(summary, "訓練摘要不可為空");
        epochRmseHistory = immutableNonEmptyCopy(epochRmseHistory, "Epoch/RMSE 歷程");
        featureImportances = immutableNonEmptyCopy(featureImportances, "特徵重要度");
        predictionComparisons = immutableNonEmptyCopy(predictionComparisons, "實際/預測明細");

        validateEpochHistory(epochRmseHistory, summary.completedEpochs());
        validateFeatureImportances(featureImportances, summary.featureCount());
        validatePredictionComparisons(predictionComparisons, summary.validationSampleCount());
    }

    private static <T> List<T> immutableNonEmptyCopy(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + "不可為空");
        var copy = List.copyOf(values);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return copy;
    }

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

    private static void validateFeatureImportances(
            List<FeatureImportance> importances,
            int expectedFeatureCount
    ) {
        if (importances.size() != expectedFeatureCount) {
            throw new IllegalArgumentException("特徵重要度筆數必須符合摘要中的特徵數量");
        }

        var featureNames = new HashSet<String>();
        double total = 0;
        for (var feature : importances) {
            if (!featureNames.add(feature.featureName())) {
                throw new IllegalArgumentException("特徵名稱不可重複");
            }
            total += feature.importance();
        }
        if (Math.abs(total - 1.0) > IMPORTANCE_SUM_TOLERANCE) {
            throw new IllegalArgumentException("特徵重要度總和必須等於 1");
        }
    }

    private static void validatePredictionComparisons(
            List<PredictionComparison> comparisons,
            int expectedValidationCount
    ) {
        if (comparisons.size() != expectedValidationCount) {
            throw new IllegalArgumentException("實際/預測明細筆數必須符合摘要中的驗證資料筆數");
        }

        var caseIds = new HashSet<String>();
        for (var comparison : comparisons) {
            if (!caseIds.add(comparison.caseId())) {
                throw new IllegalArgumentException("案例編號不可重複");
            }
        }
    }
}
