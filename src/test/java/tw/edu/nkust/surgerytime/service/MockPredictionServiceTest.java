package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import tw.edu.nkust.surgerytime.model.PredictionRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPredictionServiceTest {
    private final MockPredictionService service = new MockPredictionService();

    @Test
    void predictionIsDeterministicAndReturnsValidRange() {
        var request = new PredictionRequest(
                "A01",
                60,
                "女性 F",
                "顎下腺切除術｜Ablation of Submaxillary Gland",
                "王國強 醫師"
        );

        var first = service.predict(request);
        var second = service.predict(request);

        assertEquals(first, second);
        assertTrue(first.lowerBoundMinutes() < first.predictedMinutes());
        assertTrue(first.upperBoundMinutes() > first.predictedMinutes());
        assertTrue(first.confidence() >= 0.78 && first.confidence() <= 0.94);
        assertTrue(first.explanation().contains("CSV"));
    }

    @Test
    void olderAgeProducesNoShorterEstimateForSameProcedure() {
        var younger = new PredictionRequest("A01", 40, "女性 F",
                "全膝關節置換術｜Total Knee Arthroplasty", "林志明 醫師");
        var older = new PredictionRequest("A01", 80, "女性 F",
                "全膝關節置換術｜Total Knee Arthroplasty", "林志明 醫師");

        assertTrue(service.predict(older).predictedMinutes() >= service.predict(younger).predictedMinutes());
    }

    @Test
    void predictionOptionsAreLoadedFromCsvInDisplayOrder() {
        assertIterableEquals(
                java.util.List.of("A01", "A02", "B01", "B03"),
                service.supportedOperatingRooms()
        );
        assertIterableEquals(
                java.util.List.of("王國強 醫師", "陳怡安 醫師", "林志明 醫師", "張雅雯 醫師"),
                service.supportedSurgeons()
        );
        assertEquals(5, service.supportedProcedures().size());
        assertEquals(
                "顎下腺切除術｜Ablation of Submaxillary Gland",
                service.supportedProcedures().getFirst()
        );
    }
}
