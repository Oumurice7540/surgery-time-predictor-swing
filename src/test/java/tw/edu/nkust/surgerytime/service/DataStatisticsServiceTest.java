package tw.edu.nkust.surgerytime.service;
import org.junit.jupiter.api.Test;
import tw.edu.nkust.surgerytime.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DataStatisticsServiceTest {
    private final DataStatisticsService service = new DataStatisticsService();
    private ProcessingTable table() {
        var columns = List.of(new ProcessingTable.Column("dept","科別",null), new ProcessingTable.Column("doctor","醫師",null),new ProcessingTable.Column("years","年資",null));
        var rows = new ArrayList<ProcessingTable.Row>();
        String[][] data={{"甲","D1","2","40"},{"甲","D1","2","80"},{"甲","D2","10","120"},{"乙","D1","10","60"},{"","D1","未知","100"}};
        for(var d:data) rows.add(new ProcessingTable.Row(new SyntheticSurgeryCase("C"+rows.size(),"R",40,"F","P",d[1],"E","I","G",0,"L",Integer.parseInt(d[3])),Map.of("dept",d[0],"doctor",d[1],"years",d[2])));
        return new ProcessingTable(columns,rows,"麻醉時間");
    }
    @Test void weightedAggregatesAndDenominatorExcludeMissingGroups() {
        var result=service.summarize(table(),List.of("dept"));
        assertEquals(4,result.included());assertEquals(1,result.excluded());assertEquals(2,result.groups().size());
        var a=result.groups().stream().filter(g->g.label().equals("甲")).findFirst().orElseThrow();
        assertEquals(3,a.count());assertEquals(80,a.average());assertEquals(40,a.minimum());assertEquals(120,a.maximum());assertEquals(75,a.percent());
        assertEquals(100,result.groups().stream().mapToDouble(DataStatisticsService.Group::percent).sum(),1e-9);
    }
    @Test void crossGroupsKeepSameDoctorInDifferentDepartmentsSeparate() {
        var source=table();var before=source.rows(); var result=service.summarize(source,List.of("dept","doctor"));
        assertEquals(3,result.groups().size());
        var pair=result.groups().stream().filter(g->g.values().equals(List.of("甲","D1"))).findFirst().orElseThrow();
        assertEquals(2,pair.count());assertEquals(60,pair.average());assertEquals(before,source.rows());
    }
    @Test void yearsSortNumericallyAndEmptyAndMissingColumnsAreExplicit() {
        assertEquals(List.of("2","10","未知"),service.summarize(table(),List.of("years")).groups().stream().map(DataStatisticsService.Group::label).toList());
        assertThrows(IllegalArgumentException.class,()->service.summarize(table(),List.of("missing")));
        assertThrows(IllegalArgumentException.class,()->service.summarize(table(),List.of("dept","dept")));
        assertThrows(IllegalArgumentException.class,()->service.summarize(table(),List.of()));
        assertTrue(service.summarize(new ProcessingTable(table().columns(),List.of(),"手術時間"),List.of("dept")).groups().isEmpty());
    }
}
