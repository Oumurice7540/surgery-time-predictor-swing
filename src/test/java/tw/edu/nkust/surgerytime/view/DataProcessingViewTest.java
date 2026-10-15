package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;

class DataProcessingViewTest {
    @TempDir Path dir;
    @Test void previewApplyAndChangedDraftUseRealSwingActions() throws Exception {
        System.setProperty("surgerytime.suppressDialogs","true");
        var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("source.csv")));
        service.generateData(new TrainingConfiguration(400,.8,123L,8,50,.2,.1,.9));
        var ref=new AtomicReference<DataProcessingView>(); var completed=new AtomicBoolean();
        SwingUtilities.invokeAndWait(()->{
            UiThemeAccess.install(); var view=new DataProcessingView(service,s->{},()->completed.set(true)); ref.set(view);
            assertFalse(((JButton)find(view,"apply-processing")).isEnabled());
            ((JComboBox<?>)find(view,"processing-operation")).setSelectedItem(ProcessingStep.Operation.DROP_COLUMN);
            var columns=(JComboBox<?>)find(view,"processing-column");
            for(int i=0;i<columns.getItemCount();i++) if(((ProcessingTable.Column)columns.getItemAt(i)).key().equals("sex")) columns.setSelectedIndex(i);
            ((JButton)find(view,"add-processing-step")).doClick();
            assertEquals(1,((JTable)find(view,"processing-steps")).getRowCount());
            ((JButton)find(view,"preview-processing")).doClick();
        });
        await(()->((JButton)find(ref.get(),"apply-processing")).isEnabled());
        assertTrue(service.availableFeatureGroups().contains("生理性別"));
        SwingUtilities.invokeAndWait(()->{
            render(ref.get(),"preview.png",1200,1100);
            ((JButton)find(ref.get(),"apply-processing")).doClick();
        });
        await(completed::get); assertFalse(service.availableFeatureGroups().contains("生理性別"));
        SwingUtilities.invokeAndWait(()->{
            ((JButton)find(ref.get(),"clear-processing-steps")).doClick();
            assertFalse(((JButton)find(ref.get(),"apply-processing")).isEnabled());
            assertEquals(0,((JTable)find(ref.get(),"processing-steps")).getRowCount());
            assertEquals(0,((JTable)find(ref.get(),"processing-after")).getRowCount());
            ref.get().installRecipe(new DataProcessingService.Recipe("手術時間",List.of(new ProcessingStep(ProcessingStep.Operation.KEEP_EQUALS,"case_id","absent",""))));
            ((JButton)find(ref.get(),"preview-processing")).doClick();
        });
        await(()->((JButton)find(ref.get(),"preview-processing")).isEnabled());
        SwingUtilities.invokeAndWait(()->{
            assertFalse(((JButton)find(ref.get(),"apply-processing")).isEnabled());
            render(ref.get(),"insufficient.png",1000,900);
        });
        assertEquals(400,service.generatedDataCount());
    }
    @Test void emptyDataAndInvalidRecipeDoNotEnableApply() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var view=new DataProcessingView(new SurgeryModelService(),s->{},()->{});
            assertFalse(((JButton)find(view,"preview-processing")).isEnabled());
            assertFalse(((JButton)find(view,"apply-processing")).isEnabled());
            assertThrows(IllegalArgumentException.class,()->view.installRecipe(new DataProcessingService.Recipe("麻醉時間",List.of())));
        });
    }
    @Test void referenceToolbarInlineEditReorderAndDelete() throws Exception {
        var service = new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("toolbar.csv")));
        service.generateData(new TrainingConfiguration(400,.8,123L,8,50,.2,.1,.9));
        SwingUtilities.invokeAndWait(() -> {
            var view = new DataProcessingView(service, x -> {}, () -> {});
            ((JButton)find(view,"processing-tool-REMOVE_EQUALS")).doClick();
            var input = (JTextField)find(view,"processing-row-0-value");
            input.setText("999"); input.postActionEvent();
            var table = (JTable)find(view,"processing-steps"); assertEquals("999",table.getValueAt(0,3));
            ((JButton)find(view,"processing-tool-MIN_MAX")).doClick();
            assertEquals(2,table.getRowCount());
            ((JButton)find(view,"processing-row-1-↑")).doClick();
            assertEquals(ProcessingStep.Operation.MIN_MAX,table.getValueAt(0,1));
            ((JButton)find(view,"processing-row-0-×")).doClick();
            assertEquals(1,table.getRowCount()); assertEquals("999",table.getValueAt(0,3));
            render(view,"reference-toolbar.png",1100,620);
            assertEquals(400,service.generatedDataCount());
        });
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) {
            AtomicBoolean ready=new AtomicBoolean(); SwingUtilities.invokeAndWait(()->ready.set(condition.getAsBoolean()));
            if(ready.get()) return; Thread.sleep(30);
        }
        fail("Swing worker did not complete within 10 seconds");
    }
    static void layout(Container c) { c.doLayout(); for(Component child:c.getComponents()) if(child instanceof Container nested) layout(nested); }
    static void render(JComponent c,String name,int w,int h) {
        try {
            c.setSize(w,h); layout(c); layout(c);
            Component button=find(c,"preview-processing"); if(button!=null) { var position=SwingUtilities.convertPoint(button,0,0,c); assertTrue(position.y >= 0 && position.y + button.getHeight() <= h, "Editor controls must remain inside the visible viewport"); }
            var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); var g=image.createGraphics(); c.printAll(g); g.dispose();
            Path path=Path.of("target/processing-preview"); Files.createDirectories(path); javax.imageio.ImageIO.write(image,"png",path.resolve(name).toFile());
        } catch(Exception e) { throw new RuntimeException(e); }
    }
}
