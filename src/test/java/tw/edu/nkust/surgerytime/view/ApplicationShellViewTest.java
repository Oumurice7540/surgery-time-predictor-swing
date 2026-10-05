package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.service.SurgeryModelService;
import tw.edu.nkust.surgerytime.service.SyntheticSurgeryDataService;

import javax.swing.SwingUtilities;
import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 驗證正式啟動時不會暗中生成病例或訓練模型。 */
class ApplicationShellViewTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void opensAtDataSourceWithoutTrainingOrCreatingResultPages() throws Exception {
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(
                temporaryDirectory.resolve("must-not-be-created.csv")));
        var shellReference = new AtomicReference<ApplicationShellView>();

        SwingUtilities.invokeAndWait(() -> shellReference.set(new ApplicationShellView(service)));

        var shell = shellReference.get();
        assertFalse(service.isTrained());
        assertFalse(service.hasGeneratedData());
        assertEquals(ApplicationShellView.Page.DATA_SOURCE, shell.currentPage());
        // 所有頁面都能直接開啟；只想預測時不必從第一步依序完成。
        assertTrue(findButton(shell, "手術時間預測").isEnabled());
        assertTrue(findButton(shell, "訓練結果").isEnabled());
        assertTrue(findButton(shell, "方法設定").isEnabled());
        SwingUtilities.invokeAndWait(() -> shell.showPage(ApplicationShellView.Page.PREDICTION));
        assertEquals(ApplicationShellView.Page.PREDICTION, shell.currentPage());
        assertNotNull(shell.predictionView());
        SwingUtilities.invokeAndWait(() -> shell.showPage(ApplicationShellView.Page.METHOD_SETTINGS));
        assertEquals(ApplicationShellView.Page.METHOD_SETTINGS, shell.currentPage());
        SwingUtilities.invokeAndWait(() -> shell.showPage(ApplicationShellView.Page.TRAINING_RESULTS));
        assertEquals(ApplicationShellView.Page.TRAINING_RESULTS, shell.currentPage());
        assertNull(shell.trainingView());
        assertFalse(java.nio.file.Files.exists(temporaryDirectory.resolve("must-not-be-created.csv")));
    }

    /** 由整個元件樹找到指定導覽按鈕，確保使用者真的可以點，而不只程式可以切頁。 */
    private JButton findButton(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) return button;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButtonOrNull(child, text);
                if (found != null) return found;
            }
        }
        throw new AssertionError("找不到按鈕：" + text);
    }

    private JButton findButtonOrNull(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) return button;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButtonOrNull(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
