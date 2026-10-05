package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.PredictionRequest;
import tw.edu.nkust.surgerytime.model.TrainingConfiguration;

import java.nio.file.Path;
import java.nio.file.Files;

import weka.classifiers.functions.MultilayerPerceptron;
import weka.core.Instances;
import weka.core.SerializationHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 驗證從 CSV 生成到真正模型預測的完整流程。 */
class SurgeryModelServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void trainsEvaluatesPredictsSavesAndLoadsDeterministically() throws Exception {
        var configuration = new TrainingConfiguration(
                1_000, 0.8, 20260704L, 12, 250, 0.20, 0.10, 0.95);
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(
                temporaryDirectory.resolve("cases.csv")));

        // 驗證新介面所使用的兩階段流程：生成完成時尚未訓練，第二步才建立 BPNN。
        assertEquals(1_000, service.generateData(configuration));
        assertTrue(service.hasGeneratedData());
        assertTrue(service.generatedDataMatches(1_000, 20260704L));
        var firstTraining = service.trainGeneratedData(configuration);
        var request = new PredictionRequest(
                "A01", 60, "女性 F",
                "全膝關節置換術｜Total Knee Arthroplasty",
                "S003 虛擬醫師",
                "常規 Elective", "ASA II", "區域麻醉 Regional", 1, "標準 Standard"
        );
        var firstPrediction = service.predict(request);
        service.generateData(configuration);
        var secondTraining = service.trainGeneratedData(configuration);
        var secondPrediction = service.predict(request);

        assertEquals(firstTraining.summary().meanAbsoluteErrorMinutes(),
                secondTraining.summary().meanAbsoluteErrorMinutes(), 0.000_001);
        assertEquals(firstPrediction, secondPrediction);
        assertEquals(800, firstTraining.summary().trainingSampleCount());
        assertEquals(200, firstTraining.summary().validationSampleCount());
        assertEquals(29, firstTraining.summary().encodedFeatureCount());
        assertEquals(250, firstTraining.summary().completedEpochs());
        assertEquals(200, firstTraining.predictionComparisons().size());
        assertTrue(firstTraining.summary().algorithm().contains("WEKA MultilayerPerceptron"));
        assertTrue(firstTraining.summary().meanAbsoluteErrorMinutes() < 20.0);
        assertTrue(firstTraining.summary().rootMeanSquaredErrorMinutes() < 25.0);
        assertTrue(firstTraining.summary().rSquared() > 0.65);
        assertEquals(1.0, firstTraining.featureImportances().stream()
                .mapToDouble(item -> item.importance()).sum(), 0.000_001);
        assertTrue(firstPrediction.lowerBoundMinutes() < firstPrediction.predictedMinutes());
        assertTrue(firstPrediction.upperBoundMinutes() > firstPrediction.predictedMinutes());
        assertTrue(firstPrediction.intervalCoverage() >= 0.90);
        assertTrue(firstPrediction.explanation().contains("WEKA BPNN"));
        assertTrue(firstPrediction.explanation().contains("95 百分位"));

        Path modelFile = temporaryDirectory.resolve("saved-bpnn.model");
        service.saveModel(modelFile);
        assertTrue(Files.size(modelFile) > 0);
        Object[] savedObjects = SerializationHelper.readAll(modelFile.toString());
        assertEquals(3, savedObjects.length);
        assertTrue(savedObjects[0] instanceof MultilayerPerceptron,
                "模型檔第一個物件必須是真正的 WEKA BPNN");
        assertTrue(savedObjects[1] instanceof Instances,
                "模型檔第二個物件必須是 WEKA 訓練欄位結構");

        // 建立一個全新的服務物件，證明結果不是沿用記憶體中的舊網路，而是真的從檔案還原。
        var loadedService = new SurgeryModelService(new SyntheticSurgeryDataService(
                temporaryDirectory.resolve("loaded-cases.csv")));
        var loadedTraining = loadedService.loadModel(modelFile);

        assertTrue(loadedService.isTrained());
        assertEquals(configuration, loadedService.configuration());
        assertEquals("saved-bpnn.model", loadedService.modelSource());
        assertEquals(secondTraining, loadedTraining);
        assertEquals(secondPrediction, loadedService.predict(request));
    }

    @Test
    void exposesCsvOptionsInDisplayOrder() {
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(
                temporaryDirectory.resolve("options.csv")));

        assertIterableEquals(java.util.List.of("A01", "A02", "B01", "B03"),
                service.supportedOperatingRooms());
        assertIterableEquals(java.util.List.of(
                        "S001 虛擬醫師", "S002 虛擬醫師", "S003 虛擬醫師", "S004 虛擬醫師"),
                service.supportedSurgeons());
        assertEquals(5, service.supportedProcedures().size());
    }

    @Test
    void appliesSelectedFeaturesAndAllowsIndependentSplitSeed() {
        var generationConfiguration = new TrainingConfiguration(
                300, 0.8, 123L, 8, 50, 0.20, 0.10, 0.90);
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(
                temporaryDirectory.resolve("selected-features.csv")));
        service.generateData(generationConfiguration);

        service.setSelectedFeatureGroups(java.util.List.of("病患年齡", "共病數量"));
        service.configureSplit(0.75, 777L);
        var trainingConfiguration = new TrainingConfiguration(
                300, 0.75, 777L, 8, 50, 0.20, 0.10, 0.90);
        var result = service.trainGeneratedData(trainingConfiguration);

        assertEquals(2, result.summary().featureCount());
        assertEquals(2, result.summary().encodedFeatureCount());
        assertEquals(225, result.summary().trainingSampleCount());
        assertEquals(75, result.summary().validationSampleCount());
        assertEquals(2, result.featureImportances().size());
    }
}
