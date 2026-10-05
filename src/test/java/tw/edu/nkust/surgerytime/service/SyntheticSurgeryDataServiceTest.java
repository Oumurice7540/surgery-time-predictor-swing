package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.TrainingConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 驗證虛擬資料真的輸出成 CSV，而且相同亂數種子能完整重現。 */
class SyntheticSurgeryDataServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesReloadsAndReproducesSyntheticCsv() throws Exception {
        Path csv = temporaryDirectory.resolve("synthetic.csv");
        var service = new SyntheticSurgeryDataService(csv);
        var configuration = new TrainingConfiguration(500, 0.8, 12345L, 8, 80, 0.20, 0.10, 0.90);

        var first = service.generateSaveAndReload(configuration);
        var second = service.generateSaveAndReload(configuration);
        var imported = service.importCsv(csv);

        assertEquals(500, first.size());
        assertEquals(first, second);
        assertEquals(second, imported);
        assertTrue(Files.exists(csv));
        assertEquals(501, Files.readAllLines(csv).size());
        String csvText = Files.readString(csv);
        assertTrue(csvText.startsWith(
                "\uFEFFcase_id,operating_room,age,sex,procedure,surgeon_code,urgency,asa_class,anesthesia,comorbidity_count,complexity,actual_minutes"));
        assertFalse(csvText.contains("病歷號"));
        assertTrue(first.stream().filter(item -> item.procedure().startsWith("剖腹產"))
                .allMatch(item -> item.sex().startsWith("女性")));
        assertTrue(first.stream().map(item -> item.urgency()).distinct().count() >= 2);
        assertTrue(first.stream().map(item -> item.asaClass()).distinct().count() >= 3);
    }
}
