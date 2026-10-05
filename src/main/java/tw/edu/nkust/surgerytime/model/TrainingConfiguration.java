package tw.edu.nkust.surgerytime.model;

import java.io.Serializable;

/**
 * 保存「方法設定」頁面中可以調整的資料參數與模型超參數。
 *
 * <p>把畫面設定放在獨立的 record，而不是直接把 JSpinner 傳給 Service，
 * 可以讓模型訓練與 Swing 畫面彼此分離，也讓單元測試能直接建立設定。</p>
 *
 * @param sampleCount 要產生的虛擬手術案例總數
 * @param trainingRatio 分配給訓練集的比例，例如 0.8 代表 80%
 * @param randomSeed 亂數種子；相同種子與設定會產生相同資料
 * @param hiddenNeuronCount BPNN 單一隱藏層中的神經元數量
 * @param epochs BPNN 反向傳播更新網路權重的完整訓練週期數
 * @param momentumAlpha Alpha 動量係數，保留上一輪權重更新方向
 * @param learningRate Eta 學習率，每個 Epoch 更新權重時的步伐大小
 * @param predictionIntervalTarget 預測區間的目標涵蓋比例，例如 0.90 代表 90%
 */
public record TrainingConfiguration(
        int sampleCount,
        double trainingRatio,
        long randomSeed,
        int hiddenNeuronCount,
        int epochs,
        double momentumAlpha,
        double learningRate,
        double predictionIntervalTarget
) implements Serializable {
    /** 固定版本號讓日後讀取舊模型時能判斷 Java 資料結構是否相容。 */
    private static final long serialVersionUID = 1L;
    /**
     * 經固定 CSV 比較後採用的穩定預設值。
     * 原簡報的 60／1,000／0.8／0.2 仍可手動輸入，但在目前資料上容易震盪且耗時。
     */
    public static final TrainingConfiguration DEFAULT =
            new TrainingConfiguration(2_000, 0.80, 20_260_704L, 16, 500, 0.20, 0.10, 0.90);

    /** 每次建立設定時先檢查數值，避免無法切分或無法訓練的組合。 */
    public TrainingConfiguration {
        if (sampleCount < 200 || sampleCount > 100_000) {
            throw new IllegalArgumentException("虛擬資料筆數必須介於 200 到 100,000 筆");
        }
        if (!Double.isFinite(trainingRatio) || trainingRatio < 0.60 || trainingRatio > 0.90) {
            throw new IllegalArgumentException("訓練集比例必須介於 60% 到 90%");
        }
        if (randomSeed < 1 || randomSeed > 999_999_999L) {
            throw new IllegalArgumentException("亂數種子必須介於 1 到 999,999,999");
        }
        if (hiddenNeuronCount < 1 || hiddenNeuronCount > 128) {
            throw new IllegalArgumentException("隱藏層神經元必須介於 1 到 128");
        }
        if (epochs < 50 || epochs > 2_000) {
            throw new IllegalArgumentException("Epoch 必須介於 50 到 2,000");
        }
        if (!Double.isFinite(momentumAlpha) || momentumAlpha < 0.0 || momentumAlpha > 0.90) {
            throw new IllegalArgumentException("Alpha 動量必須介於 0 到 0.90");
        }
        if (!Double.isFinite(learningRate) || learningRate < 0.001 || learningRate > 1.0) {
            throw new IllegalArgumentException("Eta 學習率必須介於 0.001 到 1.0");
        }
        if (!Double.isFinite(predictionIntervalTarget)
                || predictionIntervalTarget < 0.80
                || predictionIntervalTarget > 0.99) {
            throw new IllegalArgumentException("預測區間目標必須介於 80% 到 99%");
        }
    }

    /** @return 依總筆數與比例計算出的訓練資料筆數 */
    public int trainingSampleCount() {
        return (int) Math.round(sampleCount * trainingRatio);
    }

    /** @return 剩餘、未參與網路權重學習的測試資料筆數 */
    public int testSampleCount() {
        return sampleCount - trainingSampleCount();
    }
}
