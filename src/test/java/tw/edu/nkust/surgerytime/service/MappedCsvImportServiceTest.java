package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.service.MappedCsvImportService.*;
import tw.edu.nkust.surgerytime.model.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappedCsvImportServiceTest {
    @TempDir Path directory;
    final MappedCsvImportService importer = new MappedCsvImportService();

    Path write(String filename, String text) throws Exception {
        Path file = directory.resolve(filename); Files.writeString(file, text); return file;
    }
    Mapping suggest(Path file) throws Exception { return importer.suggest(importer.inspect(file, StandardCharsets.UTF_8)); }
    String rows(String header, int count) {
        var text = new StringBuilder(header + "\n");
        for (int i = 0; i < count; i++) text.append("R").append(i).append(",新院區,42,F,新術式,醫師甲,03:35\n");
        return text.toString();
    }
    static final String HEADER = "案例編號,室別,年齡,性別,手術名稱,主治醫師,手術時間";

    @Test void importsChineseHeadersAndNewCategoriesAndKeepsOriginalBytes() throws Exception {
        Path file = write("hospital.csv", "\uFEFF" + rows(HEADER, 220));
        byte[] original = Files.readAllBytes(file);
        Report report = importer.validate(List.of(suggest(file)), "手術時間");
        assertTrue(report.valid(), report.issues().toString());
        assertEquals(220, report.cases().size());
        assertEquals(215, report.cases().getFirst().actualMinutes());
        assertEquals("醫師甲", report.cases().getFirst().surgeon());
        assertEquals(List.of("病患年齡", "生理性別", "手術名稱", "虛擬醫師", "手術室別"), report.features());
        assertArrayEquals(original, Files.readAllBytes(file));
    }
    @Test void acceptsDifferentHeadersAndOrderInMultipleFiles() throws Exception {
        var first = suggest(write("first.csv", rows(HEADER, 200)));
        Path secondFile = write("second.csv", "術式名稱,耗時\n" + "另一術式,90\n".repeat(200));
        Source second = importer.inspect(secondFile, StandardCharsets.UTF_8);
        var one = new Mapping(first.source(), Map.of(Field.PROCEDURE, 4, Field.TARGET, 6), TimeFormat.AUTO);
        var two = new Mapping(second, Map.of(Field.PROCEDURE, 0, Field.TARGET, 1), TimeFormat.MINUTES);
        Report report = importer.validate(List.of(one, two), "麻醉時間");
        assertTrue(report.valid(), report.issues().toString());
        assertEquals(400, report.cases().size());
        assertEquals(400, report.cases().stream().map(SyntheticSurgeryCase::caseId).distinct().count());
        assertEquals("麻醉時間", report.target());
    }
    @Test void reportsMissingInvalidDuplicateAndMalformedRowsWithoutDroppingSilently() throws Exception {
        Path file = write("bad.csv", rows(HEADER, 200)
                + "R0,新院區,abc,F,,醫師甲,03:99\n"
                + "too,few\n"
                + "R202,新院區,40,F,wrong\"quote,醫師甲,30\n");
        Report report = importer.validate(List.of(suggest(file)), "手術時間");
        assertFalse(report.valid()); assertEquals(203, report.totalRows()); assertEquals(200, report.cases().size());
        assertTrue(report.issues().stream().anyMatch(i -> i.line() == 202 && i.field().equals("年齡")));
        assertTrue(report.issues().stream().anyMatch(i -> i.line() == 202 && i.message().contains("重複")));
        assertTrue(report.issues().stream().anyMatch(i -> i.line() == 203 && i.message().contains("欄位數")));
        assertTrue(report.issues().stream().anyMatch(i -> i.line() == 204 && i.message().contains("引號")));
        Path output = directory.resolve("report.csv"); MappedCsvImportService.exportReport(report, output);
        assertTrue(Files.readString(output).contains("缺值"));
    }
    @Test void handlesQuotedCommaEscapedQuotesNewlinesAndPhysicalLineNumbers() throws Exception {
        String text = "手術名稱,手術時間\r\n" + "\"手術,\"\"A\"\"\n第二行\",03:35\r\n".repeat(200) + "壞資料,abc\r\n";
        var report = importer.validate(List.of(suggest(write("quoted.csv", text))), "手術時間");
        assertEquals(200, report.cases().size());
        assertEquals("手術,\"A\"\n第二行", report.cases().getFirst().procedure());
        assertTrue(report.issues().stream().anyMatch(i -> i.line() == 402));
    }
    @Test void rejectsDuplicateMappingMismatchedFeaturesAndChangedHeaders() throws Exception {
        Path file = write("source.csv", rows(HEADER, 200)); Mapping mapping = suggest(file);
        var duplicate = new Mapping(mapping.source(), Map.of(Field.PROCEDURE, 4, Field.TARGET, 4), TimeFormat.AUTO);
        assertFalse(importer.validate(List.of(duplicate), "手術時間").valid());
        var different = new Mapping(mapping.source(), Map.of(Field.PROCEDURE, 4, Field.TARGET, 6), TimeFormat.AUTO);
        assertTrue(importer.validate(List.of(mapping, different), "手術時間").issues().stream().anyMatch(i -> i.message().contains("相同的輸入特徵")));
        Files.writeString(file, rows(HEADER.replace("年齡", "歲數"), 200));
        assertTrue(importer.validate(List.of(mapping), "手術時間").issues().stream().anyMatch(i -> i.message().contains("已改變")));
    }
    @Test void explicitlySupportsBig5AndDoesNotGuessAmbiguousTargetColumns() throws Exception {
        Path file = directory.resolve("big5.csv"); Files.writeString(file, rows(HEADER, 200), Charset.forName("Big5"));
        assertTrue(importer.validate(List.of(importer.suggest(importer.inspect(file, Charset.forName("Big5")))), "手術時間").valid());
        assertThrows(java.io.IOException.class, () -> importer.inspect(file, StandardCharsets.UTF_8));
        var source = importer.inspect(write("ambiguous.csv", "手術名稱,手術時間(K),手術時間(O)\n甲,03:35,215\n"), StandardCharsets.UTF_8);
        assertFalse(importer.suggest(source).columns().containsKey(Field.TARGET));
    }
    @Test void durationParsingIsExplicitAndRejectsInvalidValues() {
        assertEquals(215, MappedCsvImportService.parseMinutes("03:35", TimeFormat.AUTO));
        assertEquals(1530, MappedCsvImportService.parseMinutes("25:30", TimeFormat.HOURS_MINUTES));
        assertEquals(91, MappedCsvImportService.parseMinutes("90.5", TimeFormat.MINUTES));
        for (String bad : List.of("", "-1", "0", "NaN", "Infinity", "03:60", "3:5", "03:35:00", "999999999999999"))
            assertThrows(IllegalArgumentException.class, () -> MappedCsvImportService.parseMinutes(bad, TimeFormat.AUTO), bad);
        assertThrows(IllegalArgumentException.class, () -> MappedCsvImportService.parseMinutes("03:35", TimeFormat.MINUTES));
    }
    @Test void acceptsHospitalSizedInputAndChecksUpperAndLowerBounds() throws Exception {
        Path file = write("large.csv", "手術名稱,手術時間\n" + "新術式,90\n".repeat(92_277));
        assertTrue(importer.validate(List.of(suggest(file)), "手術時間").valid());
        Files.writeString(file, "手術名稱,手術時間\n" + "新術式,90\n".repeat(100_001));
        assertFalse(importer.validate(List.of(suggest(file)), "手術時間").valid());
        Files.writeString(file, "手術名稱,手術時間\n" + "新術式,90\n".repeat(199));
        assertFalse(importer.validate(List.of(suggest(file)), "手術時間").valid());
    }
    @Test void persistsTargetAndModelSpecificDictionariesAndProtectsStateOnFailure() throws Exception {
        StringBuilder csv = new StringBuilder("年齡,手術名稱,主治醫師,麻醉時間\n");
        for (int i = 0; i < 240; i++) csv.append(20 + i % 60).append(",新術式").append(i % 2).append(",醫師甲,").append(40 + i % 60).append('\n');
        Report report = importer.validate(List.of(suggest(write("train.csv", csv.toString()))), "麻醉時間");
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(directory.resolve("synthetic.csv")));
        service.applyImport(report);
        assertEquals(report.features(), service.selectedFeatureGroups());
        assertThrows(IllegalArgumentException.class, () -> service.setSelectedFeatureGroups(List.of("ASA 分級")));
        service.trainGeneratedData(new TrainingConfiguration(240, .8, 123L, 8, 50, .2, .1, .9));
        var request = new PredictionRequest("未使用", 40, "未使用", "新術式0", "醫師甲", "未使用", "未使用", "未使用", 0, "未使用");
        var prediction = service.predict(request);
        Path model = directory.resolve("hospital.model"); service.saveModel(model);
        var loaded = new SurgeryModelService(); loaded.loadModel(model);
        assertEquals("麻醉時間", loaded.predictionTarget());
        assertEquals(List.of("新術式0", "新術式1"), loaded.supportedProcedures());
        assertEquals(List.of("醫師甲"), loaded.supportedSurgeons());
        assertEquals(prediction, loaded.predict(request));
        assertEquals(report.features(), loaded.predictionFeatures());
        Report invalid = importer.validate(List.of(suggest(write("bad-small.csv", "手術名稱,手術時間\n新術式,abc\n"))), "手術時間");
        var original = service.preparedCases(); Path prepared = service.generatedCsvPath();
        assertThrows(IllegalArgumentException.class, () -> service.applyImport(invalid));
        assertEquals(original, service.preparedCases()); assertEquals(prepared, service.generatedCsvPath());
        assertEquals(prediction, service.predict(request));
        // 準備另一份資料也不能污染目前使用中的模型選項。
        service.generateData(new TrainingConfiguration(240, .8, 123L, 8, 50, .2, .1, .9));
        assertEquals(List.of("醫師甲"), service.supportedSurgeons());
        assertEquals("麻醉時間", service.predictionTarget());
    }
}
