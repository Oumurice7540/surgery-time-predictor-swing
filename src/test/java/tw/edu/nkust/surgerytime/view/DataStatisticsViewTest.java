package tw.edu.nkust.surgerytime.view;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;

class DataStatisticsViewTest {
    @TempDir Path dir;
    SurgeryModelService fixture() throws Exception {
        var csv=new StringBuilder("案例編號,手術名稱,主治醫師,手術時間,科別,疾病,醫師年資\n");
        for(int i=0;i<240;i++) csv.append("S").append(i).append(",術式").append(i%2).append(",D").append(i%3).append(',').append(60+i%2*60).append(',').append(i%2==0?"骨科":"耳鼻喉科").append(",疾病").append(i%3).append(',').append(i%3==0?2:10).append('\n');
        var file=dir.resolve("statistics.csv");Files.writeString(file,csv);
        var importer=new MappedCsvImportService();var report=importer.validate(List.of(importer.suggest(importer.inspect(file,StandardCharsets.UTF_8))),"手術時間");
        assertTrue(report.valid(),report.issues().toString());
        var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("out.csv"))); service.applyImport(report);return service;
    }
    @Test void sixTopicsUseRealColumnsAndBothChartsAndNumericSorting() throws Exception {
        var service=fixture();
        SwingUtilities.invokeAndWait(()->{
            UiThemeAccess.install(); var view=new DataStatisticsView(service,x->{},()->{});view.refreshData();
            var topic=(JComboBox<?>)find(view,"statistics-topic");var table=(JTable)find(view,"statistics-table");
            int[] expected={6,2,3,2,6,2};
            for(int i=0;i<6;i++) {topic.setSelectedIndex(i);assertEquals(expected[i],table.getRowCount(),"topic "+i);}
            assertEquals(120L,table.getValueAt(0,1));assertEquals(50.0,table.getValueAt(0,2));
            assertEquals(1,((JComboBox<?>)find(view,"statistics-chart-type")).getSelectedIndex());
            assertFalse(find(view,"statistics-metric").isEnabled());
            MultiAlgorithmViewTest.render(view,"statistics-pie.png",1280,1000);
            topic.setSelectedIndex(4);assertEquals(7,table.getColumnCount());
            table.getRowSorter().toggleSortOrder(4);assertEquals(60.0,table.getValueAt(0,4));
            MultiAlgorithmViewTest.render(view,"statistics-cross.png",1280,1000);
            topic.setSelectedIndex(3);assertEquals("2",table.getValueAt(0,0));
            topic.setSelectedIndex(6); assertTrue(table.getRowCount()>0);
        });
    }
    @Test void appliedColumnDeletionClearsStaleResultsAndAllowsManualMapping() throws Exception {
        var service=fixture();
        SwingUtilities.invokeAndWait(()->{
            var view=new DataStatisticsView(service,x->{},()->{});view.refreshData();
            var topic=(JComboBox<?>)find(view,"statistics-topic");topic.setSelectedIndex(5);
            var table=(JTable)find(view,"statistics-table");assertEquals(2,table.getRowCount());
            var recipe=new DataProcessingService.Recipe("手術時間",List.of(new ProcessingStep(ProcessingStep.Operation.DROP_COLUMN,"extra:科別","","")));
            service.applyProcessing(service.previewProcessing(recipe)); view.refreshData();assertEquals(0,table.getRowCount());
            assertTrue(((JLabel)find(view,"statistics-message")).getText().contains("未辨識到科別"));
            var first=(JComboBox<?>)find(view,"statistics-first");first.setSelectedIndex(0);assertTrue(table.getRowCount()>0);
            topic.setSelectedIndex(0);((JComboBox<?>)find(view,"statistics-second")).setSelectedItem(first.getSelectedItem());
            first.setSelectedIndex(((JComboBox<?>)find(view,"statistics-second")).getSelectedIndex());assertEquals(0,table.getRowCount());
        });
    }
}
