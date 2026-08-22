package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import tw.edu.nkust.surgerytime.model.EpochRmsePoint;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockTrainingResultServiceTest {
    private static final double METRIC_TOLERANCE = 0.05;

    private final MockTrainingResultService service = new MockTrainingResultService();

    @Test
    void resultIsDeterministicAndImmutable() {
        var first = service.getTrainingResult();
        var second = service.getTrainingResult();

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, () ->
                first.epochRmseHistory().add(new EpochRmsePoint(110, 22.5)));
        assertThrows(UnsupportedOperationException.class, () ->
                first.featureImportances().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                first.predictionComparisons().clear());
    }

    @Test
    void epochHistoryAndFeatureImportancesAreReadyForCharts() {
        var result = service.getTrainingResult();
        var epochs = result.epochRmseHistory();
        var importances = result.featureImportances();

        assertEquals(0, epochs.getFirst().epoch());
        assertEquals(result.summary().completedEpochs(), epochs.getLast().epoch());
        assertEquals(
                result.summary().rootMeanSquaredErrorMinutes(),
                epochs.getLast().rmseMinutes(),
                METRIC_TOLERANCE
        );
        assertTrue(IntStream.range(1, epochs.size()).allMatch(index ->
                epochs.get(index).epoch() > epochs.get(index - 1).epoch()
                        && epochs.get(index).rmseMinutes() < epochs.get(index - 1).rmseMinutes()));

        assertEquals(result.summary().featureCount(), importances.size());
        assertEquals(1.0, importances.stream().mapToDouble(item -> item.importance()).sum(), 0.000_001);
        assertTrue(IntStream.range(1, importances.size()).allMatch(index ->
                importances.get(index).importance() <= importances.get(index - 1).importance()));
    }

    @Test
    void trainingSummaryMatchesPredictionComparisonDetails() {
        var result = service.getTrainingResult();
        var comparisons = result.predictionComparisons();
        var summary = result.summary();

        assertEquals(summary.validationSampleCount(), comparisons.size());

        double mae = comparisons.stream()
                .mapToInt(item -> item.absoluteErrorMinutes())
                .average()
                .orElseThrow();
        double rmse = Math.sqrt(comparisons.stream()
                .mapToDouble(item -> {
                    double error = item.actualMinutes() - item.predictedMinutes();
                    return error * error;
                })
                .average()
                .orElseThrow());
        double actualMean = comparisons.stream()
                .mapToInt(item -> item.actualMinutes())
                .average()
                .orElseThrow();
        double residualSquares = comparisons.stream()
                .mapToDouble(item -> {
                    double error = item.actualMinutes() - item.predictedMinutes();
                    return error * error;
                })
                .sum();
        double totalSquares = comparisons.stream()
                .mapToDouble(item -> {
                    double deviation = item.actualMinutes() - actualMean;
                    return deviation * deviation;
                })
                .sum();
        double rSquared = 1 - residualSquares / totalSquares;

        assertEquals(summary.meanAbsoluteErrorMinutes(), mae, METRIC_TOLERANCE);
        assertEquals(summary.rootMeanSquaredErrorMinutes(), rmse, METRIC_TOLERANCE);
        assertEquals(summary.rSquared(), rSquared, 0.001);
    }
}
