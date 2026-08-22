package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

/**
 * Headline values shown on the mock training-results page.
 */
public record TrainingSummary(
        String modelName,
        String algorithm,
        int trainingSampleCount,
        int validationSampleCount,
        int featureCount,
        int completedEpochs,
        double meanAbsoluteErrorMinutes,
        double rootMeanSquaredErrorMinutes,
        double rSquared,
        long trainingDurationMillis,
        String dataVersion
) {
    public TrainingSummary {
        modelName = requireText(modelName, "模型名稱");
        algorithm = requireText(algorithm, "演算法名稱");
        dataVersion = requireText(dataVersion, "資料版本");

        if (trainingSampleCount <= 0 || validationSampleCount <= 0) {
            throw new IllegalArgumentException("訓練與驗證資料筆數都必須大於 0");
        }
        if (featureCount <= 0) {
            throw new IllegalArgumentException("特徵數量必須大於 0");
        }
        if (completedEpochs <= 0) {
            throw new IllegalArgumentException("完成的訓練週期必須大於 0");
        }
        if (!Double.isFinite(meanAbsoluteErrorMinutes) || meanAbsoluteErrorMinutes < 0) {
            throw new IllegalArgumentException("MAE 必須是大於或等於 0 的有限數值");
        }
        if (!Double.isFinite(rootMeanSquaredErrorMinutes) || rootMeanSquaredErrorMinutes < 0) {
            throw new IllegalArgumentException("RMSE 必須是大於或等於 0 的有限數值");
        }
        if (!Double.isFinite(rSquared) || rSquared > 1) {
            throw new IllegalArgumentException("R² 必須是小於或等於 1 的有限數值");
        }
        if (trainingDurationMillis <= 0) {
            throw new IllegalArgumentException("訓練時間必須大於 0 毫秒");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不可為空");
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return normalized;
    }
}
