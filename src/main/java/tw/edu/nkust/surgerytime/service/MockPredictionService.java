package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.PredictionRequest;
import tw.edu.nkust.surgerytime.model.PredictionResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic CSV-backed mock service. It does not load or run an ML model. */
public final class MockPredictionService {
    private static final String DATA_ROOT = "/tw/edu/nkust/surgerytime/data/";

    private final Map<String, Integer> baseMinutes;
    private final List<String> operatingRooms;
    private final List<String> surgeons;

    public MockPredictionService() {
        var procedures = new LinkedHashMap<String, Integer>();
        for (var row : CsvResourceReader.read(DATA_ROOT + "procedure_baselines.csv")) {
            procedures.put(required(row, "procedure"), integer(row, "base_minutes"));
        }
        baseMinutes = Collections.unmodifiableMap(new LinkedHashMap<>(procedures));
        operatingRooms = options("ROOM");
        surgeons = options("SURGEON");
    }

    public List<String> supportedProcedures() {
        return List.copyOf(baseMinutes.keySet());
    }

    public List<String> supportedOperatingRooms() {
        return operatingRooms;
    }

    public List<String> supportedSurgeons() {
        return surgeons;
    }

    public PredictionResult predict(PredictionRequest request) {
        int base = baseMinutes.getOrDefault(request.procedure(), 95);
        double ageAdjustment = Math.max(0, request.age() - 50) * 0.42;
        double roomAdjustment = switch (request.operatingRoom()) {
            case "A02" -> 4;
            case "B01" -> 7;
            case "B03" -> -3;
            default -> 0;
        };
        int stableVariation = Math.floorMod(request.surgeon().hashCode(), 13) - 6;
        int predicted = roundToFive(base + ageAdjustment + roomAdjustment + stableVariation);
        int margin = Math.max(12, roundToFive(predicted * 0.14));
        double confidence = Math.max(0.78, Math.min(0.94, 0.91 - Math.abs(stableVariation) * 0.008));
        return new PredictionResult(
                predicted,
                Math.max(10, predicted - margin),
                predicted + margin,
                confidence,
                "依 CSV 模擬基準、年齡與場地修正量計算；僅供介面展示。"
        );
    }

    private List<String> options(String type) {
        return CsvResourceReader.read(DATA_ROOT + "prediction_options.csv").stream()
                .filter(row -> type.equals(required(row, "type")))
                .map(row -> required(row, "value"))
                .toList();
    }

    private String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("CSV 缺少必要欄位：" + column);
        }
        return value;
    }

    private int integer(Map<String, String> row, String column) {
        try {
            return Integer.parseInt(required(row, column));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("CSV 欄位必須是整數：" + column, exception);
        }
    }

    private int roundToFive(double value) {
        return (int) (Math.round(value / 5.0) * 5);
    }
}
