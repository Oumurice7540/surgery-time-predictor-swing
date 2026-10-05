package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 保存一筆測試案例的實際時間與模型預測時間。
 *
 * <p>訓練結果頁面的表格會讀取這個物件。實際時間來自虛擬資料生成器，
 * 預測時間則由沒有看過此案例答案的模型計算。</p>
 *
 * @param caseId 案例編號，例如 VAL-001
 * @param actualMinutes 模擬的實際手術時間（分鐘）
 * @param predictedMinutes 模擬的預測手術時間（分鐘）
 */
public record PredictionComparison(
        String caseId,
        double actualMinutes,
        double predictedMinutes
) implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 建立比較資料時，確保編號存在且兩個時間皆為正數。 */
    public PredictionComparison {
        caseId = requireText(caseId, "案例編號");
        if (!Double.isFinite(actualMinutes) || !Double.isFinite(predictedMinutes)
                || actualMinutes <= 0 || predictedMinutes <= 0) {
            throw new IllegalArgumentException("實際與預測時間都必須大於 0 分鐘");
        }
    }

    /**
     * 計算實際值與預測值相差幾分鐘，不保留正負號。
     *
     * @return 絕對誤差，單位為分鐘
     */
    public double absoluteErrorMinutes() {
        return Math.abs(actualMinutes - predictedMinutes);
    }

    /** 檢查必要文字並回傳去除頭尾空白後的內容。 */
    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不可為空");
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return normalized;
    }
}
