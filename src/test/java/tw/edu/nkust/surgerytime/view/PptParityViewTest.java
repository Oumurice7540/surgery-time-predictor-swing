package tw.edu.nkust.surgerytime.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.model.*;
import tw.edu.nkust.surgerytime.service.*;
import javax.swing.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;

class PptParityViewTest {
 @TempDir Path dir;
 SurgeryModelService fixture() throws Exception {
  var text=new StringBuilder("案例編號,年齡,手術名称,手術時間,科別,檢驗值\n");
  for(int i=0;i<240;i++)text.append("A"+i+","+(20+i%60)+",術式,"+(60+i%50)+","+(i%2==0?"骨科":"耳鼻喉科")+","+(i%30)+"\n");
  var file=dir.resolve("source.csv");Files.writeString(file,text);
  var importer=new MappedCsvImportService();var report=importer.validate(List.of(importer.suggest(importer.inspect(file,StandardCharsets.UTF_8))),"手術時間");assertTrue(report.valid(),report.issues().toString());
  var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("applied.csv")));s.applyImport(report);s.configureSplit(.8,123);return s;
 }
 DataProcessingService.Recipe recipe(){return new DataProcessingService.Recipe("手術時間",List.of(new ProcessingStep(ProcessingStep.Operation.MIN_MAX,"age","",""),new ProcessingStep(ProcessingStep.Operation.CATEGORY_CODES,"extra:科別","",""),new ProcessingStep(ProcessingStep.Operation.Z_SCORE,"extra:檢驗值","","")));}
 @Test void selectedSourceIsPreviewedWithoutReplacingAppliedData()throws Exception {
  var s=fixture();var before=s.preparedCases();Path other=dir.resolve("other.csv");Files.writeString(other,"欄一,欄二\n新來源,內容\n");
  final DataSourceView[] view={null};
  SwingUtilities.invokeAndWait(()->{UiThemeAccess.install();view[0]=new DataSourceView(x->{},()->{});view[0].attachPreparedPreview(s);view[0].installCsvSelection(List.of(other,dir.resolve("source.csv")));});
  DataProcessingViewTest.await(()->((JTable)find(view[0],"source-file-preview")).getRowCount()==1);
  SwingUtilities.invokeAndWait(()->{assertEquals("新來源",((JTable)find(view[0],"source-file-preview")).getValueAt(0,0));MultiAlgorithmViewTest.render(view[0],"parity-source.png",1366,900);((JList<?>)find(view[0],"source-file-list")).setSelectedIndex(1);});
  DataProcessingViewTest.await(()->((JTable)find(view[0],"source-file-preview")).getRowCount()==100);
  assertEquals(before,s.preparedCases());
 }
 @Test void dynamicWorkflowControlsAndRenderedPages() throws Exception {
  System.setProperty("surgerytime.suppressDialogs","true");var s=fixture();
  final DataProcessingView[] processing={null};
  SwingUtilities.invokeAndWait(()->{UiThemeAccess.install();processing[0]=new DataProcessingView(s,x->{},()->{});processing[0].installRecipe(recipe());processing[0].previewSteps();});
  DataProcessingViewTest.await(()->find(processing[0],"apply-processing").isEnabled());
  SwingUtilities.invokeAndWait(()->MultiAlgorithmViewTest.render(processing[0],"parity-processing.png",1366,900));
  s.applyProcessing(s.previewProcessing(recipe()));s.setSelectedFeatureGroups(List.of("病患年齡","extra:科別","extra:檢驗值"));
  final FeatureSelectionView[] features={null};
  SwingUtilities.invokeAndWait(()->{features[0]=new FeatureSelectionView(s,x->{},()->{});((JButton)find(features[0],"pretraining-score")).doClick();});
  DataProcessingViewTest.await(()->!"尚未評分".equals(((JTable)find(features[0],"feature-selection-table")).getValueAt(0,2)));
  SwingUtilities.invokeAndWait(()->{MultiAlgorithmViewTest.render(features[0],"parity-features.png",1366,900);var split=new DataSplitView(s,x->{},()->{});split.refreshData();((JComboBox<?>)find(split,"reference-preview-mode")).setSelectedIndex(1);MultiAlgorithmViewTest.render(split,"parity-encoded-split.png",1366,900);});
  s.trainGeneratedData(new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9),AlgorithmConfiguration.defaults(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION));
  Path model=dir.resolve("dynamic.model");s.saveModel(model);var loaded=new SurgeryModelService();loaded.loadModel(model);
  SwingUtilities.invokeAndWait(()->{
   var prediction=new SurgeryPredictionView(loaded,x->{},()->{},()->{});assertNotNull(find(prediction,"prediction-input:extra:科別"));assertNotNull(find(prediction,"prediction-input:extra:檢驗值"));
   prediction.defaultButton().doClick();MultiAlgorithmViewTest.render(prediction,"parity-dynamic-prediction.png",1366,900);
   var shell=new ApplicationShellView(s);shell.setSize(1100,740);
   for(var page:ApplicationShellView.Page.values()){shell.showPage(page);MultiAlgorithmViewTest.render(shell,"parity-min-"+page.name()+".png",1100,740);}
   var stats=new DataStatisticsView(s,x->{},()->{});stats.refreshData();((JComboBox<?>)find(stats,"statistics-topic")).setSelectedIndex(1);((JComboBox<?>)find(stats,"statistics-chart-type")).setSelectedIndex(2);assertTrue(find(stats,"statistics-metric").isEnabled());MultiAlgorithmViewTest.render(stats,"parity-line-chart.png",1366,900);
   var raw=new MappedCsvImportService.RawTable(List.of("病例","DRG","麻醉分鐘"),List.of(List.of("A001","001","80")));MultiAlgorithmViewTest.render(new ReportPreparationDialog(raw),"parity-conversion-dialog.png",1000,700);
  });
 }
}
