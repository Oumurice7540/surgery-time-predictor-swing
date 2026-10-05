package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

/**
 * 一列完全虛構的手術紀錄，也是 generated CSV 中的一筆資料。
 *
 * <p>案例不含姓名、病歷號、生日或任何真實病人識別資料。caseId 只是依序產生的
 * 虛擬編號。actualMinutes 是生成器依公開可說明的教學規則與隨機誤差建立的標籤。</p>
 *
 * @param caseId 虛擬案例編號，例如 SYN-000001
 * @param operatingRoom 手術室代碼
 * @param age 虛擬病患年齡
 * @param sex 生理性別選項
 * @param procedure 手術名稱
 * @param surgeon 虛擬醫師代碼
 * @param urgency 手術緊急程度
 * @param asaClass 虛擬 ASA 生理狀態分級
 * @param anesthesia 麻醉方式
 * @param comorbidityCount 虛擬共病數量
 * @param complexity 手術複雜度
 * @param actualMinutes 生成的手術時間，單位為分鐘
 */
public record SyntheticSurgeryCase(
        String caseId,
        String operatingRoom,
        int age,
        String sex,
        String procedure,
        String surgeon,
        String urgency,
        String asaClass,
        String anesthesia,
        int comorbidityCount,
        String complexity,
        int actualMinutes
) {
    /** 在資料進入 CSV 或模型以前執行基本完整性檢查。 */
    public SyntheticSurgeryCase {
        caseId = requireText(caseId, "案例編號");
        operatingRoom = requireText(operatingRoom, "手術室");
        sex = requireText(sex, "性別");
        procedure = requireText(procedure, "手術名稱");
        surgeon = requireText(surgeon, "虛擬醫師");
        urgency = requireText(urgency, "緊急程度");
        asaClass = requireText(asaClass, "ASA 分級");
        anesthesia = requireText(anesthesia, "麻醉方式");
        complexity = requireText(complexity, "手術複雜度");
        if (age < 0 || age > 120) {
            throw new IllegalArgumentException("年齡必須介於 0 到 120 歲");
        }
        if (comorbidityCount < 0 || comorbidityCount > 1000) {
            throw new IllegalArgumentException("共病數量必須介於 0 到 1000");
        }
        if (actualMinutes <= 0) {
            throw new IllegalArgumentException("手術時間必須大於 0 分鐘");
        }
    }

    /** 共用必要文字驗證，並移除使用者看不到的頭尾空白。 */
    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不可為空");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return normalized;
    }
}
