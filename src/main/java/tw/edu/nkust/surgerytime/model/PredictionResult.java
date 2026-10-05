package tw.edu.nkust.surgerytime.model;

/**
 * 封裝一次真正由已訓練 WEKA BPNN 模型計算的預測輸出。
 *
 * <p>上下限使用方法設定指定的「測試集絕對殘差百分位」建立。intervalCoverage
 * 是這個區間在測試集實際涵蓋答案的比例；它不是個別病患的臨床可信度。</p>
 *
 * @param predictedMinutes 中央預估時間（分鐘）
 * @param lowerBoundMinutes 展示範圍下限（分鐘）
 * @param upperBoundMinutes 展示範圍上限（分鐘）
 * @param intervalCoverage 0 到 1 的測試集區間涵蓋率
 * @param explanation 顯示給使用者的計算來源說明
 */
public record PredictionResult(
        int predictedMinutes,
        int lowerBoundMinutes,
        int upperBoundMinutes,
        double intervalCoverage,
        String explanation
) {
    /** 確保結果的時間順序與信心度範圍合理。 */
    public PredictionResult {
        // 合理順序必須是：正數下限 <= 預測值 <= 上限。
        if (predictedMinutes <= 0
                || lowerBoundMinutes <= 0
                || lowerBoundMinutes > predictedMinutes
                || upperBoundMinutes < predictedMinutes) {
            throw new IllegalArgumentException("預測時間區間不正確");
        }
        // 百分比用 0 到 1 儲存，例如 0.90 代表測試集中約 90% 的答案落在區間內。
        if (!Double.isFinite(intervalCoverage) || intervalCoverage < 0 || intervalCoverage > 1) {
            throw new IllegalArgumentException("區間涵蓋率必須介於 0 與 1 之間");
        }
    }
}
