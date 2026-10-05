package tw.edu.nkust.surgerytime.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 驗證方法設定頁顯示的允許範圍與 Model 的實際限制完全一致。 */
class TrainingConfigurationTest {
    @Test
    void acceptsRecommendedHyperparameters() {
        assertDoesNotThrow(() -> new TrainingConfiguration(
                2_000, 0.80, 20260704L, 60, 1_000, 0.80, 0.20, 0.90));
    }

    @Test
    void rejectsHyperparametersOutsideDisplayedRanges() {
        assertThrows(IllegalArgumentException.class, () ->
                new TrainingConfiguration(2_000, 0.80, 1L, 0, 400, 0.20, 0.10, 0.90));
        assertThrows(IllegalArgumentException.class, () ->
                new TrainingConfiguration(2_000, 0.80, 1L, 8, 49, 0.20, 0.10, 0.90));
        assertThrows(IllegalArgumentException.class, () ->
                new TrainingConfiguration(2_000, 0.80, 1L, 8, 400, 0.91, 0.10, 0.90));
        assertThrows(IllegalArgumentException.class, () ->
                new TrainingConfiguration(2_000, 0.80, 1L, 8, 400, 0.20, 1.01, 0.90));
        assertThrows(IllegalArgumentException.class, () ->
                new TrainingConfiguration(2_000, 0.80, 1L, 8, 400, 0.20, 0.10, 0.79));
        assertThrows(IllegalArgumentException.class, () ->
                new TrainingConfiguration(2_000, 0.80, 0L, 8, 400, 0.20, 0.10, 0.90));
    }
}
