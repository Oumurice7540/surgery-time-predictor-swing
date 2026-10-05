package tw.edu.nkust.surgerytime.view;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.*;
import javax.swing.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;
class MethodSettingsWorkflowTest {
 @TempDir Path dir;
 @Test void emptyStateHasNoPreparationControlsOrDefaultAction() throws Exception {
  SwingUtilities.invokeAndWait(()->{
   var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("data.csv")));
   var view=new MethodSettingsView(s,()->{},x->{},()->{});
   for(String name:List.of("generate-csv-button","import-csv-button","open-csv-button","sample-count","training-percent","random-seed"))assertNull(find(view,name));
   assertNull(view.defaultButton());assertFalse(find(view,"train-model-button").isEnabled());assertFalse(find(view,"train-all-methods").isEnabled());
   assertTrue(((JLabel)find(view,"training-data-status")).getText().contains("第一、二頁"));
  });
 }
 @Test void trainingUsesLatestAppliedRowsFeaturesAndSplitEvenWithoutRefresh() throws Exception {
  var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("data.csv")));
  s.generateData(new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9));
  var done=new AtomicBoolean();
  SwingUtilities.invokeAndWait(()->{
   UiThemeAccess.install();var view=new MethodSettingsView(s,()->done.set(true),x->{},()->{});
   String id=s.preparedCases().getFirst().caseId();
   s.applyProcessing(s.previewProcessing(new DataProcessingService.Recipe("手術時間",List.of(new ProcessingStep(ProcessingStep.Operation.REMOVE_EQUALS,"case_id",id,"")))));
   s.setSelectedFeatureGroups(List.of("病患年齡","手術名稱"));s.configureSplit(.7,456);
   view.syncFromWorkflow();
   assertTrue(((JLabel)find(view,"training-data-status")).getText().contains("239"));
   assertTrue(((JLabel)find(view,"training-data-path")).getText().contains(s.generatedCsvPath().toString()));
   ((JComboBox<AlgorithmConfiguration.Algorithm>)find(view,"training-algorithm")).setSelectedItem(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION);
   MultiAlgorithmViewTest.render(view,"method-workflow-desktop.png",1366,1400);
   MultiAlgorithmViewTest.render(view,"method-workflow-compact.png",900,1800);
   s.configureSplit(.75,789); // Changed after last UI refresh: click must use latest workflow values.
   ((JButton)find(view,"train-model-button")).doClick();
  });
  DataProcessingViewTest.await(done::get);
  var run=s.trainingHistory().getFirst();assertEquals(239,run.configuration().sampleCount());assertEquals(.75,run.configuration().trainingRatio());assertEquals(789,run.configuration().randomSeed());
  assertEquals(List.of("病患年齡","手術名稱"),run.features());assertEquals(239,s.preparedCases().size());
 }
}
