package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 驗證原生檔案視窗共用的副檔名處理，不在測試中實際開啟視窗。 */
class NativeFileDialogsTest {
    @Test
    void recognizesExtensionWithoutCaseSensitivity() {
        assertTrue(NativeFileDialogs.hasExtension("病例.CSV", "csv"));
        assertTrue(NativeFileDialogs.hasExtension("WEKA.MODEL", ".model"));
    }

    @Test
    void appendsMissingExtensionOnlyOnce() {
        assertEquals(Path.of("WEKA_BPNN.model"),
                NativeFileDialogs.ensureExtension(Path.of("WEKA_BPNN"), "model"));
        assertEquals(Path.of("WEKA_BPNN.model"),
                NativeFileDialogs.ensureExtension(Path.of("WEKA_BPNN.model"), ".model"));
    }
}
