package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

public record PredictionRequest(
        String operatingRoom,
        int age,
        String sex,
        String procedure,
        String surgeon
) {
    public PredictionRequest {
        operatingRoom = requireText(operatingRoom, "手術室別");
        sex = requireText(sex, "性別");
        procedure = requireText(procedure, "手術名稱");
        surgeon = requireText(surgeon, "主治醫師");

        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("年齡必須介於 1 到 120 歲之間");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + "不可為空");
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不可為空");
        }
        return normalized;
    }
}
