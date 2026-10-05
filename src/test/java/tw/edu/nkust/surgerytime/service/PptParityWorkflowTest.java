package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.MappedCsvImportService.RawTable;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.model.ProcessingStep.Operation.*;

class PptParityWorkflowTest {
    @TempDir Path dir;
    final ReportConversionService conversion=new ReportConversionService();
    @Test void durationHandlesMidnightDatesSecondsAndRejectsAmbiguity() {
        assertEquals(80,conversion.minutes("23:20","00:40",true));
        assertEquals(80,conversion.minutes("2026-10-03 23:20","2026-10-04 00:40",false));
        assertEquals(2,conversion.minutes("12:00:00","12:01:30",false));
        for(var pair:List.of(List.of("23:20","00:40"),List.of("12:00","12:00"),List.of("","12:00"),List.of("2026-10-03 12:00","13:00"),List.of("25:00","26:00")))
            assertThrows(IllegalArgumentException.class,()->conversion.minutes(pair.get(0),pair.get(1),false));
    }
    @Test void drgIsLeftJoinExposesMissingAndRejectsDuplicateBlankAndCollidingKeys() {
        var source=new RawTable(List.of("key","age"),List.of(List.of("A","20"),List.of("A","30"),List.of("B","40"),List.of("","50")));
        var lookup=new RawTable(List.of("code","weight"),List.of(List.of("A","2")));
        var result=conversion.merge(source,lookup,0,0,List.of(1));assertEquals(4,result.table().rows().size());assertEquals(2,result.matchedRows());assertEquals(List.of("B","（空白鍵）"),result.unmatchedKeys());assertEquals("",result.table().rows().get(2).get(2));
        assertThrows(IllegalArgumentException.class,()->conversion.merge(source,new RawTable(lookup.headers(),List.of(List.of("A","1"),List.of("A","2"))),0,0,List.of(1)));
        assertThrows(IllegalArgumentException.class,()->conversion.merge(source,new RawTable(lookup.headers(),List.of(List.of("","1"))),0,0,List.of(1)));
        assertThrows(IllegalArgumentException.class,()->conversion.merge(result.table(),lookup,0,0,List.of(1)));
    }
    @Test void rawCsvKeepsQuotedMultilineAndAllColumns() throws Exception {
        var raw=new RawTable(List.of("a","b","c"),List.of(List.of("甲,乙","換\n行","引\"號"),List.of("","2","3")));
        var file=dir.resolve("raw.csv");MappedCsvImportService.writeRaw(raw,file);
        assertEquals(raw,new MappedCsvImportService().readRaw(file,StandardCharsets.UTF_8,100000));
    }
    SurgeryModelService fixture() throws Exception {
        var rows=new ArrayList<List<String>>();
        for(int i=0;i<240;i++)rows.add(List.of("P"+i,Integer.toString(20+i%60),i%2==0?"A":"B","23:00",String.format("00:%02d",i%50),i%17==0?"":Integer.toString(i%30),"7"));
        var raw=new RawTable(List.of("案例編號","年齡","分類","開始","結束","檢驗值","常數"),rows);
        var joined=conversion.merge(raw,new RawTable(List.of("代碼","分組"),List.of(List.of("A","甲"),List.of("B","乙"))),2,0,List.of(1));
        var timed=conversion.duration(joined.table(),3,4,true);var file=dir.resolve("人工轉檔.csv");MappedCsvImportService.writeRaw(timed,file);
        var importer=new MappedCsvImportService();var source=importer.inspect(file,StandardCharsets.UTF_8);var suggestion=importer.suggest(source);
        var mapping=new EnumMap<MappedCsvImportService.Field,Integer>(MappedCsvImportService.Field.class);mapping.putAll(suggestion.columns());mapping.put(MappedCsvImportService.Field.TARGET,timed.headers().indexOf("麻醉分鐘"));
        var report=importer.validate(List.of(new MappedCsvImportService.Mapping(source,mapping,MappedCsvImportService.TimeFormat.AUTO)),"麻醉時間");assertTrue(report.valid(),report.issues().toString());
        var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("applied/data.csv")));service.applyImport(report);service.configureSplit(.8,123);
        return service;
    }
    List<ProcessingStep> steps(){return List.of(new ProcessingStep(CATEGORY_CODES,"extra:DRG_分組","",""),new ProcessingStep(MIN_MAX,"extra:檢驗值","",""),new ProcessingStep(Z_SCORE,"extra:常數","",""));}
    @Test void fullWorkflowFourModelsRoundtripDynamicFieldsAndMetrics() throws Exception {
        var s=fixture();var processor=new DataProcessingService();var recipe=new DataProcessingService.Recipe("麻醉時間",steps());
        var recipePath=dir.resolve("流程.steps");processor.saveRecipe(recipe,recipePath);assertEquals(recipe,processor.loadRecipe(recipePath));
        s.applyProcessing(s.previewProcessing(processor.loadRecipe(recipePath)));
        assertTrue(s.availableFeatureGroups().contains("extra:檢驗值"));assertFalse(s.availableFeatureGroups().contains("case_id"));assertFalse(s.availableFeatureGroups().contains("actual_minutes"));
        s.setSelectedFeatureGroups(List.of("病患年齡","extra:DRG_分組","extra:檢驗值","extra:常數"));
        var preview=s.modelDataPreview(.8,123);assertEquals(192,preview.training().size());assertEquals(48,preview.test().size());
        var config=new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9);
        var input=Map.of("病患年齡","45","extra:DRG_分組","從未見過","extra:檢驗值","","extra:常數","7");
        for(var algorithm:AlgorithmConfiguration.Algorithm.values()) {
            var result=s.trainGeneratedData(config,new AlgorithmConfiguration(algorithm,10,3,1e-6,1,.01));
            assertEquals(192,result.trainingMetrics().count());assertEquals(48,result.testMetrics().count());
            assertEquals(result.summary().rootMeanSquaredErrorMinutes(),result.testMetrics().rmse());
            assertTrue(Double.isFinite(result.trainingMetrics().rmse()));
            assertEquals(preview.test().stream().map(r->r.getFirst().toString()).toList(),result.predictionComparisons().stream().map(PredictionComparison::caseId).toList());
            var fitted=s.predictionTransforms();assertNotNull(fitted);assertEquals(0,fitted.encode(fitted.byFeature(input))[fitted.fields().stream().filter(f->f.name().equals("extra:常數")).findFirst().orElseThrow().offset()]);
            var predicted=s.predict(input);Path model=dir.resolve(algorithm.name()+".model");s.saveModel(model);
            var loaded=new SurgeryModelService();loaded.loadModel(model);assertEquals(predicted,loaded.predict(input));assertEquals(fitted.description(),loaded.predictionTransforms().description());assertEquals(result,loaded.trainingResult());
        }
        s.setSelectedFeatureGroups(List.of("extra:檢驗值"));s.trainGeneratedData(config,AlgorithmConfiguration.defaults(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION));
        assertEquals(List.of("extra:檢驗值"),s.predictionFeatures());assertEquals(1,s.predictionTransforms().fields().size());assertTrue(s.predict(Map.of("extra:檢驗值","10")).predictedMinutes()>0);
    }
    @Test void heldoutValuesCannotChangeFittedParametersOrPretrainingScores() throws Exception {
        var s=fixture();var table=s.processingCurrent().table();var shuffled=new ArrayList<>(table.rows());Collections.shuffle(shuffled,new Random(123 ^ 0x5DEECE66DL));var train=List.copyOf(shuffled.subList(0,192));
        var selected=List.of("extra:檢驗值","extra:常數","extra:DRG_分組");var fitted=FittedTransforms.fit(table,train,selected,steps());
        var raw=new HashMap<>(train.getFirst().values());raw.put("extra:檢驗值","99999");raw.put("extra:DRG_分組","測試專用");
        var encoded=fitted.encode(raw);assertTrue(encoded[0]>1);assertEquals(-1,encoded[2]);assertEquals(0,encoded[1]);
        assertFalse(fitted.fields().get(2).categories().contains("測試專用"));
        double mean=train.stream().map(r->r.values().get("extra:檢驗值")).filter(v->!v.isBlank()).mapToDouble(Double::parseDouble).average().orElseThrow();assertEquals(mean,fitted.fields().getFirst().impute());
        var heldout=shuffled.subList(192,240).stream().map(r->r.caseData().caseId()).collect(java.util.stream.Collectors.toSet());
        var scores=s.scoreFeatures();assertTrue(scores.stream().allMatch(score->score.count()<=192));
        // Modify only test targets and imported extra values, keep row identities/order and split.
        var changed=table.rows().stream().map(r->{if(!heldout.contains(r.caseData().caseId()))return r;var values=new HashMap<>(r.values());values.put("extra:檢驗值","1000000");return new ProcessingTable.Row(r.caseData(),values);}).toList();
        var other=new ProcessingTable(table.columns(),changed,table.target());
        assertEquals(fitted.description(),FittedTransforms.fit(other,other.rows().stream().filter(r->!heldout.contains(r.caseData().caseId())).toList(),selected,steps()).description());
    }
    @Test void processingOrderAndForeignRecipeAreRejected() throws Exception {
        var s=fixture();assertThrows(IllegalArgumentException.class,()->s.previewProcessing(new DataProcessingService.Recipe("麻醉時間",List.of(new ProcessingStep(MIN_MAX,"age","",""),new ProcessingStep(REMOVE_MISSING,"age","","")))));
        Path foreign=dir.resolve("foreign.step");Files.writeString(foreign,"not our format");assertThrows(java.io.IOException.class,()->new DataProcessingService().loadRecipe(foreign));
        Path model=dir.resolve("foreign.model");Files.writeString(model,"not our model");assertThrows(RuntimeException.class,()->s.loadModel(model));
    }
    @Test void iqrFitsTrainingOnlyAndRetainsEveryHeldoutRecord() throws Exception {
        var s=fixture();var table=s.processingCurrent().table();
        var recipe=new DataProcessingService.Recipe("麻醉時間",List.of(new ProcessingStep(IQR,"extra:檢驗值","1.5","")));
        s.applyProcessing(s.previewProcessing(recipe));assertEquals(240,s.generatedDataCount());
        var first=TrainingPartition.split(s.processingCurrent().table(),recipe.steps(),.8,123);
        var ids=first.test().stream().map(r->r.caseData().caseId()).collect(java.util.stream.Collectors.toSet());
        var changed=table.rows().stream().map(r->{if(!ids.contains(r.caseData().caseId()))return r;var values=new HashMap<>(r.values());values.put("extra:檢驗值","100000000");return new ProcessingTable.Row(r.caseData(),values);}).toList();
        var other=TrainingPartition.split(new ProcessingTable(table.columns(),changed,table.target()),recipe.steps(),.8,123);
        assertEquals(first.training(),other.training());assertEquals(first.audit(),other.audit());assertEquals(48,other.test().size());
        var result=s.trainGeneratedData(new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9),AlgorithmConfiguration.defaults(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION));
        assertEquals(48,result.predictionComparisons().size());assertEquals(result.trainingMetrics().count(),result.trainingComparisons().size());
        double rmse=Math.sqrt(result.trainingComparisons().stream().mapToDouble(r->Math.pow(r.actualMinutes()-r.predictedMinutes(),2)).average().orElseThrow());assertEquals(rmse,result.trainingMetrics().rmse(),1e-9);
    }

    @Test void importedAdditionalColumnsCanTrainWithoutAnyOfTheLegacyTenFields() throws Exception {
        var csv=new StringBuilder("案例編號,手術時間,檢驗量,常數\n");for(int i=0;i<240;i++)csv.append("E"+i+","+(60+i%30)+","+(i%20)+",7\n");
        Path file=dir.resolve("extras-only.csv");Files.writeString(file,csv);var importer=new MappedCsvImportService();var report=importer.validate(List.of(importer.suggest(importer.inspect(file,StandardCharsets.UTF_8))),"手術時間");assertTrue(report.valid(),report.issues().toString());assertTrue(report.features().isEmpty());
        var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("only/data.csv")));s.applyImport(report);assertEquals(List.of("extra:檢驗量","extra:常數"),s.selectedFeatureGroups());
        s.trainGeneratedData(new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9),AlgorithmConfiguration.defaults(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION));assertTrue(s.predict(Map.of("extra:檢驗量","10","extra:常數","7")).predictedMinutes()>0);
    }

}
