package tw.edu.nkust.surgerytime.view;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.*;
import javax.swing.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;
class TrainingHistoryViewTest {
 @TempDir Path dir;
 @Test void realBatchButtonCreatesComparisonAndSortingSelectsCorrectModel() throws Exception {
  var config=new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9);
  var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("data.csv")));s.generateData(config);
  var done=new AtomicBoolean();
  SwingUtilities.invokeAndWait(()->{
   UiThemeAccess.install();System.setProperty("surgerytime.suppressDialogs","true");
   var view=new MethodSettingsView(s,()->done.set(true),x->{},()->{});view.refreshPreparedData();
   ((JSpinner)find(view,"training-epochs")).setValue(50);
   ((JButton)find(view,"train-all-methods")).doClick();assertFalse(find(view,"train-all-methods").isEnabled());
  });
  long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(40);while(!done.get()&&System.nanoTime()<until)Thread.sleep(30);assertTrue(done.get());assertEquals(4,s.trainingHistory().size());
  var activated=new AtomicBoolean();var chosen=new AtomicLong();
  SwingUtilities.invokeAndWait(()->{
   var panel=new TrainingHistoryPanel(s,()->activated.set(true));var table=(JTable)find(panel,"training-history-table");assertEquals(4,table.getRowCount());
   table.getRowSorter().toggleSortOrder(3); table.setRowSelectionInterval(0,0);chosen.set(((Number)table.getValueAt(0,0)).longValue());
   MultiAlgorithmViewTest.render(panel,"comparison-table.png",1280,370);
   ((JButton)find(panel,"activate-history-model")).doClick();
  });
  DataProcessingViewTest.await(activated::get);assertEquals(chosen.get(),s.activeRunId());
  s.trainGeneratedData(new TrainingConfiguration(240,.7,123,8,50,.2,.1,.9));
  SwingUtilities.invokeAndWait(()->{var panel=new TrainingHistoryPanel(s,()->{});var table=(JTable)find(panel,"training-history-table");assertEquals(1,table.getRowCount());((JCheckBox)find(panel,"history-same-group")).doClick();assertEquals(5,table.getRowCount());});
 }
}
