package tw.edu.nkust.surgerytime.model;

/**
 * One deterministic point in the mock training-loss history.
 */
public record EpochRmsePoint(int epoch, double rmseMinutes) {
    public EpochRmsePoint {
        if (epoch < 0) {
            throw new IllegalArgumentException("訓練週期不可小於 0");
        }
        if (!Double.isFinite(rmseMinutes) || rmseMinutes <= 0) {
            throw new IllegalArgumentException("RMSE 必須是大於 0 的有限數值");
        }
    }
}
