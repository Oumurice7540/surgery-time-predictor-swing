package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

/**
 * Relative contribution of one input field in the mock training result.
 */
public record FeatureImportance(String featureName, double importance) {
    public FeatureImportance {
        featureName = requireText(featureName, "特徵名稱");
        if (!Double.isFinite(importance) || importance < 0 || importance > 1) {
            throw new IllegalArgumentException("特徵重要度必須介於 0 與 1 之間");
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
