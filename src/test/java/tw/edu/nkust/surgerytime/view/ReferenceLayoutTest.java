package tw.edu.nkust.surgerytime.view;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tw.edu.nkust.surgerytime.service.*;
import tw.edu.nkust.surgerytime.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static tw.edu.nkust.surgerytime.view.CsvImportViewTest.find;
class ReferenceLayoutTest {
 @TempDir Path dir;
 @Test void referencePagesRenderAndNavigationRemainsBelowContent() throws Exception {
  var service=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("source.csv")));
  var config=new TrainingConfiguration(240,.8,123,8,50,.2,.1,.9);service.generateData(config);service.configureSplit(.8,123);
  service.trainGeneratedData(config,new AlgorithmConfiguration(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION,10,0,1e-8,1,.01));
  var out=Path.of("target/reference-preview");Files.createDirectories(out);
  SwingUtilities.invokeAndWait(()->{
   UiThemeAccess.install();var shell=new ApplicationShellView(service);shell.setSize(1366,900);
   for(var page:ApplicationShellView.Page.values()){
    shell.showPage(page);DataProcessingViewTest.layout(shell);DataProcessingViewTest.layout(shell);
    var nav=find(shell,"reference-workflow-navigation");var header=find(shell,"reference-header");
    assertTrue(SwingUtilities.convertPoint(nav,0,0,shell).y>700);assertEquals(0,SwingUtilities.convertPoint(header,0,0,shell).y);
    var image=new BufferedImage(1366,900,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();shell.printAll(g);g.dispose();
    try{javax.imageio.ImageIO.write(image,"png",out.resolve(page.name()+".png").toFile());}catch(Exception e){throw new RuntimeException(e);}
   }
   assertNotNull(find(shell,"train-all-methods"));assertNotNull(find(shell,"training-history-table"));
   assertNull(find(shell.settingsView(),"generate-csv-button"));
  });
 }
 @Test void readOnlyPreviewShowsActualHoldoutIdsAndUpdatesAfterWorkflowChanges() throws Exception {
  var s=new SurgeryModelService(new SyntheticSurgeryDataService(dir.resolve("data.csv")));
  var c=new TrainingConfiguration(240,.7,123,8,50,.2,.1,.9);s.generateData(c);s.configureSplit(.7,123);
  s.trainGeneratedData(c,new AlgorithmConfiguration(AlgorithmConfiguration.Algorithm.LINEAR_REGRESSION,10,0,1e-8,1,.01));
  var before=s.preparedCases();var expected=s.trainingResult().predictionComparisons().stream().map(PredictionComparison::caseId).toList();
  SwingUtilities.invokeAndWait(()->{
   var panel=new ReferenceDataPreview(s,true);var table=(JTable)find(panel,"reference-test-preview");
   var ids=new ArrayList<String>();for(int i=0;i<table.getRowCount();i++)ids.add(table.getValueAt(i,0).toString());assertEquals(expected,ids);
   s.configureSplit(.8,789);panel.refresh();assertEquals(48,((JTable)find(panel,"reference-test-preview")).getRowCount());
  });assertEquals(before,s.preparedCases());
 }
}
