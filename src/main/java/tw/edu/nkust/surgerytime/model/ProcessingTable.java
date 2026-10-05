package tw.edu.nkust.surgerytime.model;

import java.util.*;

/** 不可變的處理資料；模型欄位與只供篩選的來源欄位共用同一份列資料。 */
public record ProcessingTable(List<Column> columns, List<Row> rows, String target) {
    public record Column(String key, String label, String feature) {
        @Override public String toString() { return label; }
    }
    public record Row(SyntheticSurgeryCase caseData, Map<String, String> values) {
        public Row { values = Map.copyOf(values); }
    }
    public ProcessingTable {
        columns = List.copyOf(columns); rows = List.copyOf(rows); Objects.requireNonNull(target);
        if (columns.stream().map(Column::key).distinct().count() != columns.size()) throw new IllegalArgumentException("處理資料欄位重複");
    }
    public Column column(String key) {
        return columns.stream().filter(c -> c.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到欄位（可能已在先前步驟刪除）：" + key));
    }
    public List<String> features() { return columns.stream().map(Column::feature).filter(Objects::nonNull).toList(); }
    public List<SyntheticSurgeryCase> cases() { return rows.stream().map(Row::caseData).toList(); }
    public static String extraKey(String name) { return "extra:" + name; }

    public static ProcessingTable fromCases(List<SyntheticSurgeryCase> cases, List<String> features, String target,
                                           List<String> extraColumns, Map<String, Map<String, String>> extraValues) {
        List<Column> core = List.of(
                new Column("case_id", "案例編號", null),
                new Column("operating_room", "手術室", "手術室別"),
                new Column("age", "年齡", "病患年齡"),
                new Column("sex", "性別", "生理性別"),
                new Column("procedure", "手術名稱", "手術名稱"),
                new Column("surgeon_code", "主治醫師", "虛擬醫師"),
                new Column("urgency", "緊急程度", "緊急程度"),
                new Column("asa_class", "ASA 分級", "ASA 分級"),
                new Column("anesthesia", "麻醉方式", "麻醉方式"),
                new Column("comorbidity_count", "共病數量", "共病數量"),
                new Column("complexity", "手術複雜度", "手術複雜度"),
                new Column("actual_minutes", target + "（分鐘）", null));
        var columns = new ArrayList<>(core.stream().filter(c -> c.feature() == null || features.contains(c.feature())).toList());
        extraColumns.forEach(name -> columns.add(new Column(extraKey(name), name + "〔輔助欄〕", null)));
        var rows = new ArrayList<Row>(cases.size());
        for (SyntheticSurgeryCase c : cases) {
            var values = new LinkedHashMap<String, String>();
            values.put("case_id", c.caseId()); values.put("operating_room", c.operatingRoom());
            values.put("age", Integer.toString(c.age())); values.put("sex", c.sex());
            values.put("procedure", c.procedure()); values.put("surgeon_code", c.surgeon());
            values.put("urgency", c.urgency()); values.put("asa_class", c.asaClass());
            values.put("anesthesia", c.anesthesia()); values.put("comorbidity_count", Integer.toString(c.comorbidityCount()));
            values.put("complexity", c.complexity()); values.put("actual_minutes", Integer.toString(c.actualMinutes()));
            values.keySet().removeIf(key -> columns.stream().noneMatch(col -> col.key().equals(key)));
            var extras = extraValues.getOrDefault(c.caseId(), Map.of());
            extraColumns.forEach(name -> values.put(extraKey(name), extras.getOrDefault(name, "")));
            rows.add(new Row(c, values));
        }
        return new ProcessingTable(columns, rows, target);
    }
}
