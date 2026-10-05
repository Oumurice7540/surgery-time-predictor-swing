package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

/**
 * 封裝使用者在「手術時間預測」頁面選擇的所有輸入條件。
 *
 * <p>View 先從 Swing 元件取得值，再建立此物件並交給預測 Service。
 * 這樣預測邏輯不必直接讀取按鈕或下拉選單。</p>
 *
 * @param operatingRoom 手術室別，例如 A01
 * @param age 病患年齡，允許 0 到 120 歲
 * @param sex 生理性別；會經 one-hot 編碼後送入模型
 * @param procedure 手術名稱
 * @param surgeon 主治醫師名稱
 * @param urgency 手術緊急程度
 * @param asaClass 虛擬 ASA 生理狀態分級
 * @param anesthesia 麻醉方式
 * @param comorbidityCount 虛擬共病數量
 * @param complexity 手術複雜度
 */
public record PredictionRequest(
        String operatingRoom,
        int age,
        String sex,
        String procedure,
        String surgeon,
        String urgency,
        String asaClass,
        String anesthesia,
        int comorbidityCount,
        String complexity
) {
    /** 每次建立預測請求時統一執行輸入驗證。 */
    public PredictionRequest {
        operatingRoom = requireText(operatingRoom, "手術室別");
        sex = requireText(sex, "性別");
        procedure = requireText(procedure, "手術名稱");
        surgeon = requireText(surgeon, "主治醫師");
        urgency = requireText(urgency, "緊急程度");
        asaClass = requireText(asaClass, "ASA 分級");
        anesthesia = requireText(anesthesia, "麻醉方式");
        complexity = requireText(complexity, "手術複雜度");

        if (age < 0 || age > 120) {
            throw new IllegalArgumentException("年齡必須介於 0 到 120 歲之間");
        }
        if (comorbidityCount < 0 || comorbidityCount > 1000) {
            throw new IllegalArgumentException("共病數量必須介於 0 到 1000");
        }
    }

    /** 檢查必要文字欄位，並把整理過的文字存回 record 欄位。 */
    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不可為空");
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return normalized;
    }
}
