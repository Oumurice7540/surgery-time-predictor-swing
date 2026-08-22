package tw.edu.nkust.surgerytime.model;

public record PredictionResult(
        int predictedMinutes,
        int lowerBoundMinutes,
        int upperBoundMinutes,
        double confidence,
        String explanation
) {
    public PredictionResult {
        if (predictedMinutes <= 0
                || lowerBoundMinutes <= 0
                || lowerBoundMinutes > predictedMinutes
                || upperBoundMinutes < predictedMinutes) {
            throw new IllegalArgumentException("預測時間區間不正確");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("信心度必須介於 0 與 1 之間");
        }
    }
}
