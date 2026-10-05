package tw.edu.nkust.surgerytime.service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration.Algorithm;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TrainingHistoryTest {
 @TempDir Path dir;
 final TrainingConfiguration config=new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9);
 SurgeryModelService service(){var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("source.csv")));s.generateData(config);return s;}
 @Test void batchRetainsFourModelsAndActivationRestoresPredictionAndConfiguration() throws Exception {
  var s=service();var messages=new ArrayList<String>();s.trainAllAlgorithms(config,AlgorithmConfiguration.DEFAULT,messages::add);
  assertEquals(4,s.trainingHistory().size());assertEquals(4,messages.size());
  assertEquals(1,s.trainingHistory().stream().map(TrainingRun::comparisonGroup).distinct().count());
  var data=s.preparedCases();var path=s.generatedCsvPath();
  for(var run:s.trainingHistory()) {
   s.activateRun(run.id());assertEquals(run.result(),s.trainingResult());assertEquals(run.method(),s.algorithmConfiguration());
   var c=data.getFirst(); var req=new PredictionRequest(c.operatingRoom(),c.age(),c.sex(),c.procedure(),c.surgeon(),c.urgency(),c.asaClass(),c.anesthesia(),c.comorbidityCount(),c.complexity());
   var prediction=s.predict(req);Path model=dir.resolve(run.id()+".model");s.saveRunModel(run.id(),model);
   var loaded=new SurgeryModelService();loaded.loadModel(model);assertEquals(prediction,loaded.predict(req));
  }
  assertEquals(data,s.preparedCases());assertEquals(path,s.generatedCsvPath());assertEquals(4,s.trainingHistory().size());
 }
 @Test void groupingChangesForFeaturesDataSeedRatioAndTargetButNotMethod() throws Exception {
  var s=service();s.trainGeneratedData(config,AlgorithmConfiguration.defaults(Algorithm.LINEAR_REGRESSION));String first=s.trainingHistory().getFirst().comparisonGroup();
  s.trainGeneratedData(config,AlgorithmConfiguration.defaults(Algorithm.RANDOM_FOREST));assertEquals(first,s.trainingHistory().getLast().comparisonGroup());
  s.setSelectedFeatureGroups(List.of("病患年齡"));s.trainGeneratedData(config);assertNotEquals(first,s.trainingHistory().getLast().comparisonGroup());
  s.setSelectedFeatureGroups(SurgeryModelService.ALL_FEATURE_GROUPS);
  s.trainGeneratedData(new TrainingConfiguration(240,.8,124,8,50,.2,.1,.9));assertNotEquals(first,s.trainingHistory().getLast().comparisonGroup());
  s.trainGeneratedData(new TrainingConfiguration(240,.7,123,8,50,.2,.1,.9));assertNotEquals(first,s.trainingHistory().getLast().comparisonGroup());
  s.generateData(new TrainingConfiguration(240,.8,125,8,50,.2,.1,.9));s.trainGeneratedData(config);assertNotEquals(first,s.trainingHistory().getLast().comparisonGroup());
  var before=s.trainingHistory().getLast().comparisonGroup();s.applyImport(new MappedCsvImportService.Report(s.preparedCases(),List.of(),SurgeryModelService.ALL_FEATURE_GROUPS,"麻醉時間",240));s.trainGeneratedData(config);assertNotEquals(before,s.trainingHistory().getLast().comparisonGroup());
 }
 @Test void invalidActionsAndFailedTrainingDoNotCreateHistoryOrSwitchModel() {
  var s=service();s.trainGeneratedData(config);long id=s.activeRunId();
  assertThrows(IllegalArgumentException.class,()->s.activateRun(999));assertEquals(id,s.activeRunId());
  assertThrows(NullPointerException.class,()->s.trainGeneratedData(config,null));assertEquals(1,s.trainingHistory().size());assertEquals(id,s.activeRunId());
 }
 @Test void exportsEveryRunAndFullPrecisionEveryTestCase() throws Exception {
  var s=service();s.trainAllAlgorithms(config,AlgorithmConfiguration.DEFAULT,x->{});var exporter=new TrainingComparisonExporter();
  Path summary=dir.resolve("summary.csv");exporter.exportSummary(s.trainingHistory(),summary);assertEquals(5,Files.readAllLines(summary).size());
  var run=s.trainingHistory().getFirst();Path details=dir.resolve("details.csv");exporter.exportPredictions(run,details);
  String content=Files.readString(details);assertTrue(content.startsWith("\uFEFF"));assertEquals(49,content.lines().count());
  assertTrue(content.contains(Double.toString(run.result().predictionComparisons().getFirst().predictedMinutes())));
  assertTrue(content.contains(run.comparisonGroup()));assertEquals(4,s.activeRunId());
 }
}
