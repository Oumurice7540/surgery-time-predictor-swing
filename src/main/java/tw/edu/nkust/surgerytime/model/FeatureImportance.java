package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 保存一組原始特徵及其模型重要度。
 *
 * <p>重要度採 permutation importance：逐組打亂測試資料的一個欄位，觀察
 * RMSE 增加多少，再正規化為總和 1。這可解釋 BPNN 在虛擬資料上的相對依賴
 * 程度，但仍不是臨床因果關係。</p>
 *
 * @param featureName 特徵名稱，例如手術名稱、年齡或手術室別
 * @param importance 相對重要度，以 0 到 1 表示
 */
public record FeatureImportance(String featureName, double importance) implements Serializable {
    private static final long serialVersionUID = 1L;
    /** 建立物件時立即驗證名稱及數值範圍。 */
    public FeatureImportance {
        featureName = requireText(featureName, "特徵名稱");
        if (!Double.isFinite(importance) || importance < 0 || importance > 1) {
            throw new IllegalArgumentException("特徵重要度必須介於 0 與 1 之間");
        }
    }

    /**
     * 檢查文字欄位並去除頭尾空白。
     *
     * @param value 要檢查的文字
     * @param fieldName 錯誤訊息中顯示的欄位名稱
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
