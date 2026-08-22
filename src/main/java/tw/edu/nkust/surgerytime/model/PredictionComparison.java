package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

/**
 * Actual and predicted duration for one deterministic validation case.
 */
public record PredictionComparison(
        String caseId,
        int actualMinutes,
        int predictedMinutes
) {
    public PredictionComparison {
        caseId = requireText(caseId, "案例編號");
        if (actualMinutes <= 0 || predictedMinutes <= 0) {
            throw new IllegalArgumentException("實際與預測時間都必須大於 0 分鐘");
        }
    }

    public int absoluteErrorMinutes() {
        return Math.abs(actualMinutes - predictedMinutes);
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
