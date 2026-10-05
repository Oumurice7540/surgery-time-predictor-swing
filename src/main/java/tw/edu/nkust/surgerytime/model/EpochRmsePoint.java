package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;

/**
 * RMSE 收斂折線圖上的一個資料點。
 *
 * <p>{@code record} 是適合保存資料的 Java 類型。Java 會自動提供建構子、
 * {@code epoch()}、{@code rmseMinutes()}、{@code equals()} 與 {@code toString()}。</p>
 *
 * @param epoch 真正的批次梯度下降訓練週期，從 0 開始且不可為負數
 * @param rmseMinutes 該週期的均方根誤差，單位為分鐘且必須大於 0
 */
public record EpochRmsePoint(int epoch, double rmseMinutes) implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * record 的精簡建構子。每次建立資料點時都會自動執行這些驗證，
     * 防止無效資料進入圖表。
     */
    public EpochRmsePoint {
        if (epoch < 0) {
            throw new IllegalArgumentException("訓練週期不可小於 0");
        }
        // Double.isFinite 會排除 NaN、正無限大與負無限大等無法繪圖的數字。
        if (!Double.isFinite(rmseMinutes) || rmseMinutes <= 0) {
            throw new IllegalArgumentException("RMSE 必須是大於 0 的有限數值");
        }
    }
}
