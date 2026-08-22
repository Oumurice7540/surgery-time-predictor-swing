package tw.edu.nkust.surgerytime.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PredictionResultTest {
    @Test
    void acceptsBoundsThatContainPrediction() {
        assertDoesNotThrow(() -> new PredictionResult(
                100,
                80,
                120,
                0.9,
                "模擬結果"
        ));
    }

    @Test
    void rejectsBoundsThatDoNotContainPrediction() {
        assertThrows(IllegalArgumentException.class, () ->
                new PredictionResult(100, 105, 120, 0.9, "下界過高"));
        assertThrows(IllegalArgumentException.class, () ->
                new PredictionResult(100, 80, 95, 0.9, "上界過低"));
    }
}
