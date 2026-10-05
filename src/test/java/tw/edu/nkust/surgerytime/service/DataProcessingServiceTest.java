package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.model.ProcessingStep.Operation.*;

class DataProcessingServiceTest {
    @TempDir Path dir;
    final DataProcessingService processor = new DataProcessingService();
    static ProcessingStep step(ProcessingStep.Operation op, String col, String value) { return new ProcessingStep(op,col,value,""); }
    static DataProcessingService.Recipe recipe(ProcessingStep... steps) { return new DataProcessingService.Recipe("手術時間",List.of(steps)); }
    MappedCsvImportService.Report fixture() throws Exception {
        var csv = new StringBuilder("案例編號,年齡,性別,手術名稱,手術時間,科別,檢驗值\n");
        for (int i=0;i<800;i++) csv.append("C").append(i).append(',').append(20+i%50).append(",F,測試術式,")
            .append(i==798?900:60+i%40).append(',').append(i%2==0?"耳鼻喉科":"骨科").append(',').append(i%20==0?"":Integer.toString(i%10)).append('\n');
        var path=dir.resolve("fixture.csv"); Files.writeString(path,csv);
        var importer=new MappedCsvImportService();
        var report=importer.validate(List.of(importer.suggest(importer.inspect(path,StandardCharsets.UTF_8))),"手術時間");
        assertTrue(report.valid(), report.issues().toString()); return report;
    }
    SurgeryModelService service() throws Exception {
        var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("data/out.csv")));
        service.applyImport(fixture()); return service;
    }
    @Test void auxiliaryColumnsSurviveAndPreviewIsRepeatableAndOrdered() throws Exception {
        var service=service(); var original=service.processingOriginal(); var oldPath=service.generatedCsvPath();
        assertEquals("耳鼻喉科",original.rows().getFirst().values().get("extra:科別"));
        var r=recipe(step(KEEP_EQUALS,"extra:科別","耳鼻喉科"),step(IQR,"actual_minutes","1.5"),step(DROP_COLUMN,"sex",""));
        var a=service.previewProcessing(r); var b=service.previewProcessing(r);
        assertEquals(a.result(),b.result()); assertEquals(399,a.result().table().rows().size());
        assertEquals(List.of(800,400,399),a.result().audits().stream().map(DataProcessingService.Audit::before).toList());
        assertFalse(a.result().table().features().contains("生理性別"));
        assertEquals(800,service.generatedDataCount()); assertEquals(oldPath,service.generatedCsvPath()); assertEquals(original,service.processingOriginal());
        assertThrows(IllegalArgumentException.class,()->processor.process(original,recipe(step(DROP_COLUMN,"sex",""),step(KEEP_EQUALS,"sex","F"))));
    }
    @Test void applyExportsAllRowsAndFeaturesAndCanRestoreOriginal() throws Exception {
        var service=service();
        var r=recipe(step(KEEP_EQUALS,"extra:科別","耳鼻喉科"),step(IQR,"actual_minutes","1.5"),step(DROP_COLUMN,"sex",""));
        var preview=service.previewProcessing(r); assertEquals(400,service.applyProcessing(preview));
        assertEquals(List.of("病患年齡","手術名稱","extra:科別","extra:檢驗值"),service.availableFeatureGroups());
        assertEquals(List.of("病患年齡","手術名稱"),service.selectedFeatureGroups());
        Path csv=service.generatedCsvPath(); String text=Files.readString(csv);
        assertEquals(401,text.lines().count()); assertFalse(text.lines().findFirst().orElseThrow().contains("sex")); assertTrue(text.contains("科別"));
        assertEquals(r,processor.loadRecipe(csv.resolveSibling(csv.getFileName().toString().replace(".csv",".steps"))));
        var importer=new MappedCsvImportService();
        assertTrue(importer.validate(List.of(importer.suggest(importer.inspect(csv,StandardCharsets.UTF_8))),"手術時間").valid());
        assertThrows(IllegalStateException.class,()->service.applyProcessing(preview));
        assertEquals(800,service.applyProcessing(service.previewProcessing(recipe())));
        assertTrue(service.availableFeatureGroups().contains("生理性別"));
    }
    @Test void failedAndStaleOrForeignPreviewsDoNotReplaceState() throws Exception {
        var service=service(); var before=service.preparedCases(); Path path=service.generatedCsvPath();
        var small=service.previewProcessing(recipe(step(KEEP_EQUALS,"case_id","C1")));
        assertFalse(small.result().trainable()); assertThrows(IllegalArgumentException.class,()->service.applyProcessing(small));
        assertEquals(before,service.preparedCases()); assertEquals(path,service.generatedCsvPath());
        var preview=service.previewProcessing(recipe()); var other=service();
        assertThrows(IllegalStateException.class,()->other.applyProcessing(preview));
        service.applyImport(fixture()); assertThrows(IllegalStateException.class,()->service.applyProcessing(preview));
        assertThrows(IllegalArgumentException.class,()->service.previewProcessing(new DataProcessingService.Recipe("麻醉時間",List.of())));
    }
    @Test void protectedColumnsAndLastFeatureCannotBeDropped() throws Exception {
        var source=service().processingOriginal();
        for (String col:List.of("case_id","actual_minutes")) assertThrows(IllegalArgumentException.class,()->processor.process(source,recipe(step(DROP_COLUMN,col,""))));
        assertThrows(IllegalArgumentException.class,()->processor.process(source,recipe(step(DROP_COLUMN,"age",""),step(DROP_COLUMN,"sex",""),step(DROP_COLUMN,"procedure",""),step(DROP_COLUMN,"extra:科別",""),step(DROP_COLUMN,"extra:檢驗值",""))));
    }
    @Test void numericRangesMissingAndIqrHaveExplicitBoundaries() throws Exception {
        var source=service().processingOriginal();
        var range=new ProcessingStep(KEEP_RANGE,"age","20","22");
        assertEquals(48,processor.process(source,recipe(range)).table().rows().size());
        assertEquals(752,processor.process(source,recipe(new ProcessingStep(REMOVE_RANGE,"age","20","22"))).table().rows().size());
        assertEquals(760,processor.process(source,recipe(step(REMOVE_MISSING,"extra:檢驗值",""))).table().rows().size());
        assertEquals(800,processor.process(source,recipe(step(IQR,"extra:檢驗值","1.5"))).table().rows().size());
        assertThrows(IllegalArgumentException.class,()->processor.process(source,recipe(step(IQR,"extra:科別","1.5"))));
        assertThrows(IllegalArgumentException.class,()->processor.process(source,recipe(step(KEEP_EQUALS,"case_id","C0"),step(IQR,"age","1.5"))));
        assertThrows(IllegalArgumentException.class,()->new ProcessingStep(KEEP_RANGE,"age","NaN","20"));
        assertThrows(IllegalArgumentException.class,()->new ProcessingStep(KEEP_RANGE,"age","30","20"));
    }
    @Test void textMatchingIsCaseSensitiveAndContainsDiffersFromEquals() throws Exception {
        var source=service().processingOriginal();
        assertEquals(400,processor.process(source,recipe(step(KEEP_CONTAINS,"extra:科別","喉"))).table().rows().size());
        assertEquals(400,processor.process(source,recipe(step(REMOVE_CONTAINS,"extra:科別","喉"))).table().rows().size());
        assertEquals(400,processor.process(source,recipe(step(REMOVE_EQUALS,"extra:科別","骨科"))).table().rows().size());
        assertEquals(0,processor.process(source,recipe(step(KEEP_EQUALS,"case_id","c0"))).table().rows().size());
    }
    @Test void recipeRoundtripPreservesUnicodeAndRejectsCorruption() throws Exception {
        Path file=dir.resolve("處理.steps"); var r=recipe(step(KEEP_EQUALS,"extra:中文","甲=乙\\丙\n丁"));
        processor.saveRecipe(r,file); assertEquals(r,processor.loadRecipe(file));
        String valid=Files.readString(file);
        for(String broken:List.of(valid.replace("version=1","version=99"),valid.replace("KEEP_EQUALS","BOGUS"),valid.replace("step.0.column=extra", "missing=extra"))) {
            Files.writeString(file,broken); assertThrows(java.io.IOException.class,()->processor.loadRecipe(file));
        }
        Files.writeString(file,"format=surgery-processing-steps\nversion=1\ncount=0\n"); assertThrows(java.io.IOException.class,()->processor.loadRecipe(file));
    }
    @Test void trainingUsesProcessedRowsAndDroppedFeatureAndModelRoundtrips() throws Exception {
        var service=service(); service.applyProcessing(service.previewProcessing(recipe(step(KEEP_EQUALS,"extra:科別","耳鼻喉科"),step(IQR,"actual_minutes","1.5"),step(DROP_COLUMN,"sex",""))));
        var result=service.trainGeneratedData(new TrainingConfiguration(400,.8,123L,8,50,.2,.1,.9));
        assertTrue(result.summary().trainingSampleCount()<=320);
        assertEquals(80,result.summary().validationSampleCount());
        assertFalse(service.predictionFeatures().contains("生理性別"));
        var request=new PredictionRequest("未使用",40,"未使用","測試術式","未使用","未使用","未使用","未使用",0,"未使用");
        var prediction=service.predict(request); Path model=dir.resolve("processed.model"); service.saveModel(model);
        var loaded=new SurgeryModelService(); loaded.loadModel(model); assertEquals(prediction,loaded.predict(request));
        service.applyProcessing(service.previewProcessing(recipe())); assertEquals(prediction,service.predict(request));
        assertFalse(service.predictionFeatures().contains("生理性別"));
    }
    @Test void exportsAuxiliaryHeaderCollisionsWithoutDuplicates() throws Exception {
        var source=service().processingOriginal(); var columns=new ArrayList<>(source.columns()); columns.add(new ProcessingTable.Column("extra:case_id","來源",null));
        Path path=dir.resolve("collision.csv"); processor.writeCsv(new ProcessingTable(columns,source.rows(),source.target()),path);
        String header=Files.readAllLines(path).getFirst(); assertTrue(header.contains("\"來源_case_id\""));
    }
    @Test void multipleFilesPreserveAbsentAuxiliaryValuesAsBlank() throws Exception {
        var first=fixture(); var importer=new MappedCsvImportService();
        Path second=dir.resolve("second.csv"); Files.writeString(second,"案例編號,年齡,性別,手術名稱,手術時間\n"+"Z,40,F,測試術式,70\n");
        var report=importer.validate(List.of(importer.suggest(importer.inspect(dir.resolve("fixture.csv"),StandardCharsets.UTF_8)),importer.suggest(importer.inspect(second,StandardCharsets.UTF_8))),"手術時間");
        assertTrue(report.valid()); var table=ProcessingTable.fromCases(report.cases(),report.features(),report.target(),report.auxiliaryColumns(),report.auxiliaryByCase());
        assertEquals("",table.rows().getLast().values().get("extra:科別")); assertEquals(801,table.rows().size());
    }
}
