package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration.Algorithm;
import weka.core.SerializationHelper;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.functions.*;
import weka.classifiers.functions.supportVector.RBFKernel;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiAlgorithmTest {
    @TempDir Path dir;
    final TrainingConfiguration training = new TrainingConfiguration(240,.8,123L,8,50,.2,.1,.9);
    SurgeryModelService prepared() {
        var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("source.csv")));
        service.generateData(training); return service;
    }
    PredictionRequest request(SurgeryModelService service) {
        return new PredictionRequest(service.supportedOperatingRooms().getFirst(),40,service.predictionOptions("生理性別",List.of()).getFirst(),
            service.supportedProcedures().getFirst(),service.supportedSurgeons().getFirst(),service.predictionOptions("緊急程度",List.of()).getFirst(),
            service.predictionOptions("ASA 分級",List.of()).getFirst(),service.predictionOptions("麻醉方式",List.of()).getFirst(),1,service.predictionOptions("手術複雜度",List.of()).getFirst());
    }
    void roundtrip(Algorithm algorithm) throws Exception {
        var service=prepared(); var method=new AlgorithmConfiguration(algorithm,13,4,.002,4.0,.05);
        var result=service.trainGeneratedData(training,method); assertEquals(method,service.algorithmConfiguration());
        assertEquals(48,result.predictionComparisons().size()); assertTrue(Double.isFinite(result.summary().rootMeanSquaredErrorMinutes()));
        var req=request(service); var prediction=service.predict(req);
        assertTrue(prediction.explanation().contains(algorithm.toString()));
        Path file=dir.resolve(algorithm+".model"); service.saveModel(file);
        Object classifier=SerializationHelper.readAll(file.toString())[0];
        switch(algorithm) {
            case BPNN -> assertInstanceOf(MultilayerPerceptron.class,classifier);
            case RANDOM_FOREST -> { var forest=assertInstanceOf(RandomForest.class,classifier); assertEquals(13,forest.getNumIterations()); assertEquals(4,forest.getMaxDepth()); }
            case LINEAR_REGRESSION -> assertEquals(.002,assertInstanceOf(LinearRegression.class,classifier).getRidge());
            case SVR -> { var svr=assertInstanceOf(SMOreg.class,classifier); assertEquals(4,svr.getC()); assertEquals(.05,assertInstanceOf(RBFKernel.class,svr.getKernel()).getGamma()); }
        }
        var loaded=new SurgeryModelService(); loaded.loadModel(file);
        assertEquals(method,loaded.algorithmConfiguration()); assertEquals(prediction,loaded.predict(req)); assertEquals(result,loaded.trainingResult());
        assertEquals(algorithm==Algorithm.BPNN?50:1,result.summary().completedEpochs());
        var again=service.trainGeneratedData(training,method);
        assertEquals(result.predictionComparisons(),again.predictionComparisons());
    }
    @Test void bpnnRoundtrip() throws Exception { roundtrip(Algorithm.BPNN); }
    @Test void forestRoundtrip() throws Exception { roundtrip(Algorithm.RANDOM_FOREST); }
    @Test void linearRoundtrip() throws Exception { roundtrip(Algorithm.LINEAR_REGRESSION); }
    @Test void svrRoundtrip() throws Exception { roundtrip(Algorithm.SVR); }
    @Test void allMethodsUseIdenticalHoldoutRowsAndMetricsMatchDisplayedPredictions() {
        var service=prepared(); List<String> ids=null;
        for(var algorithm:Algorithm.values()) {
            var result=service.trainGeneratedData(training,AlgorithmConfiguration.defaults(algorithm));
            var comparisons=result.predictionComparisons(); var next=comparisons.stream().map(PredictionComparison::caseId).toList();
            if(ids!=null) assertEquals(ids,next); ids=next;
            double mse=comparisons.stream().mapToDouble(c->Math.pow(c.actualMinutes()-c.predictedMinutes(),2)).average().orElseThrow();
            assertEquals(Math.sqrt(mse),result.summary().rootMeanSquaredErrorMinutes(),1e-9);
        }
    }
    @Test void wrongClassifierAndFailedTrainingPreserveActiveModel() throws Exception {
        var service=prepared(); service.trainGeneratedData(training,AlgorithmConfiguration.defaults(Algorithm.RANDOM_FOREST));
        var req=request(service); var previous=service.predict(req); Path model=dir.resolve("valid.model"); service.saveModel(model);
        Object[] saved=SerializationHelper.readAll(model.toString()); saved[0]=new LinearRegression();
        Path bad=dir.resolve("bad.model"); SerializationHelper.writeAll(bad.toString(),saved);
        assertThrows(IllegalArgumentException.class,()->service.loadModel(bad)); assertEquals(previous,service.predict(req));
        assertThrows(NullPointerException.class,()->service.trainGeneratedData(training,null)); assertEquals(previous,service.predict(req));
    }
    @Test void unseenHoldoutCategoryDoesNotLeakIntoTrainingDictionary() throws Exception {
        var csv=new StringBuilder("案例編號,年齡,手術名稱,手術時間\n");
        for(int i=0;i<240;i++) csv.append("R").append(i).append(',').append(20+i%50).append(",唯一術式").append(i).append(',').append(50+i%70).append('\n');
        Path file=dir.resolve("unique.csv"); Files.writeString(file,csv);
        var importer=new MappedCsvImportService(); var report=importer.validate(List.of(importer.suggest(importer.inspect(file,java.nio.charset.StandardCharsets.UTF_8))),"手術時間");
        var service=prepared(); service.applyImport(report); service.trainGeneratedData(training,AlgorithmConfiguration.defaults(Algorithm.RANDOM_FOREST));
        assertEquals(192,service.supportedProcedures().size());
        var holdout=service.trainingResult().predictionComparisons().getFirst().caseId();
        assertFalse(service.supportedProcedures().contains("唯一術式"+holdout.substring(1)));
    }
    @Test void mismatchedHeaderIsRejectedWithoutReplacingCurrentModel() throws Exception {
        var service=prepared(); service.trainGeneratedData(training,AlgorithmConfiguration.defaults(Algorithm.LINEAR_REGRESSION));
        var before=service.predict(request(service)); var file=dir.resolve("header.model"); service.saveModel(file);
        var saved=SerializationHelper.readAll(file.toString()); ((weka.core.Instances)saved[1]).renameAttribute(0,"wrong_name");
        SerializationHelper.writeAll(file.toString(),saved);
        assertThrows(IllegalArgumentException.class,()->service.loadModel(file)); assertEquals(before,service.predict(request(service)));
    }
    @Test void rejectsNonfiniteAndOutOfRangeParameters() {
        assertThrows(IllegalArgumentException.class,()->new AlgorithmConfiguration(Algorithm.SVR,100,0,.01,Double.NaN,.01));
        assertThrows(IllegalArgumentException.class,()->new AlgorithmConfiguration(Algorithm.SVR,100,0,.01,1,0));
        assertThrows(IllegalArgumentException.class,()->new AlgorithmConfiguration(Algorithm.RANDOM_FOREST,0,0,.01,1,.01));
        assertThrows(IllegalArgumentException.class,()->new AlgorithmConfiguration(Algorithm.LINEAR_REGRESSION,100,0,-1,1,.01));
    }
}
