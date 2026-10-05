package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.service.*;
import tw.edu.nkust.surgerytime.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CsvImportViewTest {
    @TempDir Path directory;
    Path fixture() throws Exception {
        Path file = directory.resolve("中文月報.csv");
        StringBuilder text = new StringBuilder("年齡,手術名稱,主治醫師,性別,麻醉時間\n");
        for (int i = 0; i < 240; i++) text.append(20 + i % 50).append(",測試術式").append(i % 2)
                .append(",醫師乙,X,").append(40 + i % 50).append('\n');
        Files.writeString(file, text); return file;
    }
    @Test void mappingChangesInvalidateApprovalAndReport() throws Exception {
        Path file = fixture(); var importer = new MappedCsvImportService();
        var source = importer.inspect(file, StandardCharsets.UTF_8);
        var report = importer.validate(List.of(importer.suggest(source)), "麻醉時間");
        var accepted = new AtomicReference<MappedCsvImportService.Report>();
        SwingUtilities.invokeAndWait(() -> {
            UiThemeAccess.install();
            var panel = new CsvImportPanel(List.of(file), accepted::set); panel.showSources(List.of(source));
            var apply = (JButton)find(panel, "apply-import"); assertFalse(apply.isEnabled());
            panel.showReport(report); assertTrue(apply.isEnabled());
            render(panel, "mapping-success.png", 1120, 820);
            ((JComboBox<?>)find(panel, "mapping-AGE")).setSelectedIndex(0);
            assertFalse(apply.isEnabled()); apply.doClick(); assertNull(accepted.get());
            var invalid = new MappedCsvImportService.Report(report.cases(),
                    List.of(new MappedCsvImportService.Issue("中文月報.csv", 25, "預測目標時間", "時間格式錯誤：03:99")), report.features(), report.target(), 241);
            panel.showReport(invalid); assertFalse(apply.isEnabled());
            render(panel, "mapping-errors.png", 1120, 820);
            panel.showReport(report); apply.doClick(); assertEquals(report, accepted.get());
        });
    }
    @Test void predictionFormUsesLoadedModelCategoriesAndOnlySelectedFeatures() throws Exception {
        var importer = new MappedCsvImportService(); Path file = fixture();
        var report = importer.validate(List.of(importer.suggest(importer.inspect(file, StandardCharsets.UTF_8))), "麻醉時間");
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(directory.resolve("data.csv")));
        service.applyImport(report); service.trainGeneratedData(new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9));
        Path model = directory.resolve("model.model"); service.saveModel(model);
        var loaded = new SurgeryModelService(); loaded.loadModel(model);
        SwingUtilities.invokeAndWait(() -> {
            System.setProperty("surgerytime.suppressDialogs", "true");
            var view = new SurgeryPredictionView(loaded, status -> {}, () -> {}, () -> {});
            assertEquals("醫師乙", ((JComboBox<?>)find(view, "surgeon")).getItemAt(0));
            assertEquals("X", ((JComboBox<?>)find(view, "sex")).getItemAt(0));
            assertNull(find(view, "operating-room")); assertNull(find(view, "asa-class"));
            view.predictForPreview(); assertNotNull(find(view, "predict-button"));
            render(view, "imported-model-prediction.png", 1366, 900);
        });
    }
    static Component find(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container parent) for (Component child : parent.getComponents()) {
            Component found = find(child, name); if (found != null) return found;
        }
        return null;
    }
    private static void layout(Container container) {
        container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container c) layout(c);
    }
    private static void render(JComponent component, String name, int width, int height) {
        try {
            component.setSize(width, height); layout(component);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics(); component.printAll(graphics); graphics.dispose();
            Path out = Path.of("target", "import-preview"); Files.createDirectories(out);
            javax.imageio.ImageIO.write(image, "png", out.resolve(name).toFile());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
