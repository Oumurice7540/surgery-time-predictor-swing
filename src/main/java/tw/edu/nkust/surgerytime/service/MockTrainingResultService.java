package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.EpochRmsePoint;
import tw.edu.nkust.surgerytime.model.FeatureImportance;
import tw.edu.nkust.surgerytime.model.PredictionComparison;
import tw.edu.nkust.surgerytime.model.TrainingResult;
import tw.edu.nkust.surgerytime.model.TrainingSummary;

import java.util.Map;

/** Loads deterministic mock training results from bundled CSV resources. */
public final class MockTrainingResultService {
    private static final String DATA_ROOT = "/tw/edu/nkust/surgerytime/data/";
    private final TrainingResult trainingResult;

    public MockTrainingResultService() {
        trainingResult = loadTrainingResult();
    }

    public TrainingResult getTrainingResult() {
        return trainingResult;
    }

    private TrainingResult loadTrainingResult() {
        var summaryRows = CsvResourceReader.read(DATA_ROOT + "training_summary.csv");
        if (summaryRows.size() != 1) {
            throw new IllegalStateException("training_summary.csv 必須只有一筆資料");
        }
        Map<String, String> row = summaryRows.getFirst();
        var summary = new TrainingSummary(
                required(row, "model_name"),
                required(row, "algorithm"),
                integer(row, "training_sample_count"),
                integer(row, "validation_sample_count"),
                integer(row, "feature_count"),
                integer(row, "completed_epochs"),
                decimal(row, "mae_minutes"),
                decimal(row, "rmse_minutes"),
                decimal(row, "r_squared"),
                longValue(row, "training_duration_millis"),
                required(row, "data_version")
        );
        var epochs = CsvResourceReader.read(DATA_ROOT + "epoch_rmse.csv").stream()
                .map(item -> new EpochRmsePoint(integer(item, "epoch"), decimal(item, "rmse_minutes")))
                .toList();
        var importances = CsvResourceReader.read(DATA_ROOT + "feature_importance.csv").stream()
                .map(item -> new FeatureImportance(required(item, "feature"), decimal(item, "importance")))
                .toList();
        var comparisons = CsvResourceReader.read(DATA_ROOT + "prediction_comparisons.csv").stream()
                .map(item -> new PredictionComparison(
                        required(item, "case_id"),
                        integer(item, "actual_minutes"),
                        integer(item, "predicted_minutes")
                ))
                .toList();
        return new TrainingResult(summary, epochs, importances, comparisons);
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

    private long longValue(Map<String, String> row, String column) {
        try {
            return Long.parseLong(required(row, column));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("CSV 欄位必須是長整數：" + column, exception);
        }
    }

    private double decimal(Map<String, String> row, String column) {
        try {
            return Double.parseDouble(required(row, column));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("CSV 欄位必須是數值：" + column, exception);
        }
    }
}
