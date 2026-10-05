package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration.Algorithm;
import tw.edu.nkust.surgerytime.service.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;

class MultiAlgorithmViewTest {
    @TempDir Path dir;
    @Test void algorithmSelectionEnablesApplicableControlsAndActuallyTrainsSelectedMethod() throws Exception {
        var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("source.csv")));
        service.generateData(new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9));
        var completed=new AtomicBoolean(); var reference=new AtomicReference<MethodSettingsView>();
        SwingUtilities.invokeAndWait(()->{
            UiThemeAccess.install(); System.setProperty("surgerytime.suppressDialogs","true");
            var view=new MethodSettingsView(service,()->completed.set(true),s->{},()->{}); reference.set(view); view.refreshPreparedData();
            var combo=(JComboBox<?>)find(view,"training-algorithm");
            combo.setSelectedItem(Algorithm.RANDOM_FOREST);
            assertTrue(find(view,"forest-tree-count").isEnabled()); assertFalse(find(view,"training-epochs").isEnabled());
            combo.setSelectedItem(Algorithm.LINEAR_REGRESSION);
            assertTrue(find(view,"linear-ridge").isEnabled()); assertFalse(find(view,"forest-tree-count").isEnabled());
            combo.setSelectedItem(Algorithm.SVR);
            ((JSpinner)find(view,"svr-cost")).setValue(4.0); ((JSpinner)find(view,"svr-gamma")).setValue(.05);
            assertTrue(find(view,"svr-cost").isEnabled()); assertFalse(find(view,"linear-ridge").isEnabled());
            render(view,"settings-desktop.png",1366,1400);
            view.trainForPreview(); assertFalse(combo.isEnabled());
        });
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while(!completed.get() && System.nanoTime()<deadline) Thread.sleep(30);
        assertTrue(completed.get(), "Training did not finish");
        assertEquals(Algorithm.SVR,service.algorithmConfiguration().algorithm()); assertEquals(4.0,service.algorithmConfiguration().cost());
        SwingUtilities.invokeAndWait(()->{
            assertTrue(find(reference.get(),"training-algorithm").isEnabled()); assertFalse(find(reference.get(),"training-epochs").isEnabled());
            render(reference.get(),"settings-compact.png",900,1000);
            var shell=new ApplicationShellView(service); shell.showPage(ApplicationShellView.Page.TRAINING_RESULTS);
            render(shell,"svr-results.png",1366,1000);
            shell.showPage(ApplicationShellView.Page.PREDICTION); render(shell,"svr-prediction.png",1366,1000);
        });
    }
    static void render(JComponent view,String name,int width,int height) {
        try {
            view.setSize(width,height); DataProcessingViewTest.layout(view); DataProcessingViewTest.layout(view);
            if(view instanceof MethodSettingsView) {
                var button=find(view,"train-model-button");
                assertNotNull(button); { Component parent=button.getParent();
                    var point=SwingUtilities.convertPoint(button,0,button.getHeight(),parent);
                    assertTrue(point.y <= parent.getHeight(), "Train button must not be clipped"); }
            }
            var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB); var graphics=image.createGraphics(); view.printAll(graphics); graphics.dispose();
            var path=Path.of("target/algorithm-preview"); Files.createDirectories(path); javax.imageio.ImageIO.write(image,"png",path.resolve(name).toFile());
        } catch(Exception e) { throw new RuntimeException(e); }
    }
}
