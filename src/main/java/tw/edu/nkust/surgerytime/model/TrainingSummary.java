package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 保存訓練結果頁面上方的摘要資料。
 *
 * <p>這些數值由 Java 訓練流程在執行時產生，不是預先寫死的 CSV 結果。
 * MAE、RMSE 與 R² 都只使用沒有參與網路權重學習的測試集計算。</p>
 *
 * @param modelName 展示模型名稱
 * @param algorithm 展示演算法名稱
 * @param trainingSampleCount 真正參與 BPNN 權重學習的虛擬訓練資料筆數
 * @param validationSampleCount 保留給最終評估的虛擬測試資料筆數
 * @param featureCount 原始特徵群組數量
 * @param encodedFeatureCount 經 one-hot 編碼後真正送入模型的數值欄位數
 * @param completedEpochs 完成的訓練週期數
 * @param meanAbsoluteErrorMinutes MAE，平均絕對誤差（分鐘）
 * @param rootMeanSquaredErrorMinutes RMSE，均方根誤差（分鐘）
 * @param rSquared 決定係數 R²
 * @param trainingDurationMillis Java 實際訓練與評估耗時（毫秒）
 * @param dataVersion CSV 展示資料版本
 */
public record TrainingSummary(
        String modelName,
        String algorithm,
        int trainingSampleCount,
        int validationSampleCount,
        int featureCount,
        int encodedFeatureCount,
        int completedEpochs,
        double meanAbsoluteErrorMinutes,
        double rootMeanSquaredErrorMinutes,
        double rSquared,
        long trainingDurationMillis,
        String dataVersion
) implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * record 的精簡建構子，在資料進入畫面以前完成一致性檢查。
     * 驗證集中在 Model，可以避免每個畫面重複寫相同判斷。
     */
    public TrainingSummary {
        // 文字欄位不可為 null、空字串或只有空白。
        modelName = requireText(modelName, "模型名稱");
        algorithm = requireText(algorithm, "演算法名稱");
        dataVersion = requireText(dataVersion, "資料版本");

        // 訓練頁面需要至少一筆訓練資料及一筆驗證資料。
        if (trainingSampleCount <= 0 || validationSampleCount <= 0) {
            throw new IllegalArgumentException("訓練與驗證資料筆數都必須大於 0");
        }
        if (featureCount <= 0 || encodedFeatureCount < featureCount) {
            throw new IllegalArgumentException("原始與編碼後特徵數量不正確");
        }
        if (completedEpochs <= 0) {
            throw new IllegalArgumentException("完成的訓練週期必須大於 0");
        }
        // 誤差可以為 0，但不能是負數、NaN 或無限大。
        if (!Double.isFinite(meanAbsoluteErrorMinutes) || meanAbsoluteErrorMinutes < 0) {
            throw new IllegalArgumentException("MAE 必須是大於或等於 0 的有限數值");
        }
        if (!Double.isFinite(rootMeanSquaredErrorMinutes) || rootMeanSquaredErrorMinutes < 0) {
            throw new IllegalArgumentException("RMSE 必須是大於或等於 0 的有限數值");
        }
        // R² 理論上可以小於 0，因此只限制它不可超過 1，並且必須是有限數值。
        if (!Double.isFinite(rSquared) || rSquared > 1) {
            throw new IllegalArgumentException("R² 必須是小於或等於 1 的有限數值");
        }
        if (trainingDurationMillis <= 0) {
            throw new IllegalArgumentException("訓練時間必須大於 0 毫秒");
        }
    }

    /**
     * 共用的必要文字驗證。
     *
     * @return 去除頭尾空白後的文字
     */
    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不可為空");
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return normalized;
    }
}
