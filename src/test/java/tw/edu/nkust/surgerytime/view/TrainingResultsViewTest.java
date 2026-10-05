package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 驗證訓練結果頁中不需顯示視窗即可測試的小型格式化規則。 */
class TrainingResultsViewTest {
    @Test
    void defaultModelFileNameContainsLocalDateAndTime() {
        // 固定測試時間可避免測試剛好跨秒或跨日而偶爾失敗。
        var savedAt = LocalDateTime.of(2026, 9, 8, 15, 30, 45);

        assertEquals(
                "WEKA_BPNN_20260908_153045.model",
                TrainingResultsView.defaultModelFileName(savedAt)
        );
    }
}
