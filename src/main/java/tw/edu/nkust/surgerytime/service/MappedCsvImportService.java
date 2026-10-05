package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.SyntheticSurgeryCase;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** CSV 匯入的唯讀檢查階段；所有檔案通過後才由應用服務套用。 */
public final class MappedCsvImportService {
    public enum Field {
        ID("案例編號（可自動建立）", null, "case_id", "案例編號"),
        ROOM("手術室", "手術室別", "operating_room", "室別", "手術室"),
        AGE("年齡", "病患年齡", "age", "年齡"),
        SEX("性別", "生理性別", "sex", "性別"),
        PROCEDURE("手術名稱", "手術名稱", "procedure", "手術名稱", "術式"),
        SURGEON("主治醫師", "虛擬醫師", "surgeon_code", "主治醫師", "醫師"),
        URGENCY("緊急程度", "緊急程度", "urgency", "緊急程度", "分類"),
        ASA("ASA 分級", "ASA 分級", "asa_class", "ASA"),
        ANESTHESIA("麻醉方式", "麻醉方式", "anesthesia", "麻醉", "麻醉方式"),
        COMORBIDITY("共病數量", "共病數量", "comorbidity_count", "共病數量"),
        COMPLEXITY("手術複雜度", "手術複雜度", "complexity", "手術複雜度"),
        TARGET("預測目標時間（必填）", null, "actual_minutes", "手術時間", "麻醉時間");
        public final String label;
        public final String feature;
        final List<String> aliases;
        Field(String label, String feature, String... aliases) {
            this.label = label; this.feature = feature; this.aliases = List.of(aliases);
        }
        @Override public String toString() { return label; }
    }
    public enum TimeFormat {
        MINUTES("分鐘，例如 215 或 90.5"), HOURS_MINUTES("時:分，例如 03:35"),
        AUTO("自動：含冒號為時:分，否則為分鐘");
        private final String label;
        TimeFormat(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Source(Path path, Charset charset, List<String> headers) {
        public Source { path = path.toAbsolutePath().normalize(); headers = List.copyOf(headers); }
    }
    public record Mapping(Source source, Map<Field, Integer> columns, TimeFormat timeFormat) {
        public Mapping { columns = Map.copyOf(columns); Objects.requireNonNull(timeFormat); }
    }
    public record Issue(String file, int line, String field, String message) { }
    public record Report(List<SyntheticSurgeryCase> cases, List<Issue> issues,
                         List<String> features, String target, int totalRows,
                         List<String> auxiliaryColumns, Map<String, Map<String, String>> auxiliaryByCase) {
        public Report(List<SyntheticSurgeryCase> cases, List<Issue> issues, List<String> features, String target, int totalRows) {
            this(cases, issues, features, target, totalRows, List.of(), Map.of());
        }
        public Report {
            cases = List.copyOf(cases); issues = List.copyOf(issues); features = List.copyOf(features);
            if (target == null || target.isBlank()) throw new IllegalArgumentException("請指定預測目標名稱");
            target = target.trim();
            auxiliaryColumns = List.copyOf(auxiliaryColumns);
            var copy = new LinkedHashMap<String, Map<String, String>>();
            auxiliaryByCase.forEach((id, values) -> copy.put(id, Map.copyOf(values)));
            auxiliaryByCase = Collections.unmodifiableMap(copy);
        }
        public boolean valid() { return issues.isEmpty() && cases.size() >= 200 && cases.size() <= 100_000; }
    }
    private record Row(int line, List<String> cells, String error) { }

    public record RawTable(List<String> headers,List<List<String>> rows) {
        public RawTable { headers=List.copyOf(headers);rows=rows.stream().map(List::copyOf).toList(); }
    }
    public RawTable readRaw(Path path,Charset charset,int limit) throws IOException {
        var source=inspect(path,charset);var rows=new ArrayList<List<String>>();
        try(var csv=new CsvReader(path,charset)) {
            csv.next();Row row;
            while(rows.size()<limit && (row=csv.next())!=null) {
                if(row.error()!=null) throw new IOException("第 "+row.line()+" 列："+row.error());
                if(row.cells().size()!=source.headers().size()) throw new IOException("第 "+row.line()+" 列欄位數不一致");
                rows.add(row.cells());
            }
            if(limit>1000 && csv.next()!=null) throw new IOException("資料超過 "+limit+" 筆上限，未截斷匯入");
        }
        return new RawTable(source.headers(),rows);
    }
    public static void writeRaw(RawTable table,Path path) throws IOException {
        try(var out=Files.newBufferedWriter(path,java.nio.charset.StandardCharsets.UTF_8)) {
            var all=new ArrayList<List<String>>();all.add(table.headers());all.addAll(table.rows());
            for(var row:all) {out.write(row.stream().map(v->"\""+v.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(",")));out.newLine();}
        }
    }

    public Source inspect(Path path, Charset charset) throws IOException {
        try (var csv = new CsvReader(path, charset)) {
            Row header = csv.next();
            if (header == null || header.error() != null) throw new IOException("缺少或無法解析標題列：" + path.getFileName());
            List<String> names = header.cells().stream().map(String::trim).toList();
            if (names.stream().anyMatch(String::isBlank)) throw new IOException("標題列含空白欄名：" + path.getFileName());
            // 欄位以索引對應，因此同名欄位仍可由使用者依編號選取。
            return new Source(path, charset, names);
        }
    }
    public Mapping suggest(Source source) {
        Map<Field, Integer> columns = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < source.headers().size(); i++) {
                String name = source.headers().get(i).replaceAll("[（(][A-Za-z]+[）)]$", "").trim();
                if (field.aliases.stream().anyMatch(name::equalsIgnoreCase)) matches.add(i);
            }
            // 同名時間欄（原始 HH:mm 與分鐘）不猜測，要求使用者確認。
            if (matches.size() == 1) columns.put(field, matches.getFirst());
        }
        return new Mapping(source, columns, TimeFormat.AUTO);
    }

    public Report validate(List<Mapping> mappings, String target) {
        var issues = new ArrayList<Issue>();
        var cases = new ArrayList<SyntheticSurgeryCase>();
        var ids = new HashMap<String, String>();
        var auxiliaryColumns = new LinkedHashSet<String>();
        var auxiliaryByCase = new LinkedHashMap<String, Map<String, String>>();
        Set<Field> expected = null;
        int total = 0;
        for (int fileIndex = 0; fileIndex < mappings.size(); fileIndex++) {
            Mapping mapping = mappings.get(fileIndex);
            String file = mapping.source().path().toString();
            Set<Field> features = EnumSet.noneOf(Field.class);
            mapping.columns().keySet().stream().filter(f -> f.feature != null).forEach(features::add);
            boolean invalidMapping = false;
            boolean extraFeature=false;
            for(int i=0;i<mapping.source().headers().size();i++)if(!mapping.columns().containsValue(i)&&SurgeryModelService.eligibleExtraName(mapping.source().headers().get(i)))extraFeature=true;
            if (!mapping.columns().containsKey(Field.TARGET) || (features.isEmpty()&&!extraFeature)) {
                issues.add(new Issue(file, 1, "欄位對應", "必須選擇目標時間與至少一項輸入特徵")); invalidMapping = true;
            }
            if (new HashSet<>(mapping.columns().values()).size() != mapping.columns().size()) {
                issues.add(new Issue(file, 1, "欄位對應", "同一來源欄位不可同時當作多個欄位或預測目標")); invalidMapping = true;
            }
            if (mapping.columns().values().stream().anyMatch(i -> i < 0 || i >= mapping.source().headers().size())) {
                issues.add(new Issue(file, 1, "欄位對應", "欄位索引超出範圍")); invalidMapping = true;
            }
            if (expected == null) expected = features;
            else if (!expected.equals(features)) {
                issues.add(new Issue(file, 1, "欄位對應", "合併的每份檔案必須對應相同的輸入特徵")); invalidMapping = true;
            }
            if (invalidMapping) continue;
            var extraIndexes = new LinkedHashMap<String, Integer>();
            for (int i = 0; i < mapping.source().headers().size(); i++) {
                if (mapping.columns().containsValue(i)) continue;
                String rawName = mapping.source().headers().get(i);
                long totalNames = mapping.source().headers().stream().filter(rawName::equals).count();
                long occurrence = mapping.source().headers().subList(0, i + 1).stream().filter(rawName::equals).count();
                String name = totalNames > 1 ? rawName + "〔同名第" + occurrence + "欄〕" : rawName;
                if (extraIndexes.containsKey(name)) { issues.add(new Issue(file, 1, "輔助欄", "欄名無法唯一辨識，請重新命名：" + name)); invalidMapping = true; break; }
                extraIndexes.put(name, i); auxiliaryColumns.add(name);
            }
            if (invalidMapping) continue;
            try (var csv = new CsvReader(mapping.source().path(), mapping.source().charset())) {
                Row header = csv.next();
                if (header == null || header.error() != null || !header.cells().equals(mapping.source().headers())) {
                    issues.add(new Issue(file, 1, "標題列", "檔案欄位已改變，請重新讀取欄位")); continue;
                }
                Row row;
                while ((row = csv.next()) != null) {
                    if (row.cells().size() == 1 && row.cells().getFirst().isBlank() && row.error() == null) continue;
                    total++;
                    if (total > 100_000) {
                        issues.add(new Issue(file, row.line(), "筆數", "合併資料超過 100,000 筆，請縮小資料範圍"));
                        return report(cases, issues, expected, target, total, auxiliaryColumns, auxiliaryByCase);
                    }
                    if (row.error() != null || row.cells().size() != header.cells().size()) {
                        issues.add(new Issue(file, row.line(), "CSV", row.error() != null ? row.error() : "欄位數與標題列不一致")); continue;
                    }
                    var values = new EnumMap<Field, String>(Field.class);
                    int before = issues.size();
                    for (var entry : mapping.columns().entrySet()) {
                        String value = row.cells().get(entry.getValue()).trim();
                        values.put(entry.getKey(), value);
                        if (value.isEmpty()) issues.add(new Issue(file, row.line(), entry.getKey().label, "缺值；請補齊或取消此欄位對應"));
                    }
                    int age = number(values, Field.AGE, 0, 120, file, row.line(), issues);
                    int comorbidity = number(values, Field.COMORBIDITY, 0, 1000, file, row.line(), issues);
                    int minutes = 0;
                    try { minutes = parseMinutes(values.getOrDefault(Field.TARGET, ""), mapping.timeFormat()); }
                    catch (IllegalArgumentException e) { issues.add(new Issue(file, row.line(), Field.TARGET.label, e.getMessage())); }
                    String id = values.getOrDefault(Field.ID, "AUTO-" + (fileIndex + 1) + "-" + row.line());
                    String previous = ids.putIfAbsent(id, file + ":" + row.line());
                    if (previous != null) issues.add(new Issue(file, row.line(), "案例編號", "重複編號，首次出現於 " + previous));
                    if (issues.size() == before) {
                        cases.add(new SyntheticSurgeryCase(id,
                            value(values, Field.ROOM), age, value(values, Field.SEX), value(values, Field.PROCEDURE),
                            value(values, Field.SURGEON), value(values, Field.URGENCY), value(values, Field.ASA),
                            value(values, Field.ANESTHESIA), comorbidity, value(values, Field.COMPLEXITY), minutes));
                        var extras = new LinkedHashMap<String, String>();
                        for (var entry : extraIndexes.entrySet()) extras.put(entry.getKey(), row.cells().get(entry.getValue()));
                        auxiliaryByCase.put(id, extras);
                    }
                }
            } catch (IOException e) {
                issues.add(new Issue(file, 0, "檔案", "讀取失敗，請確認編碼與檔案內容：" + e.getMessage()));
            }
        }
        if (cases.size() < 200) issues.add(new Issue("全部檔案", 0, "筆數", "有效資料至少需要 200 筆，目前 " + cases.size() + " 筆"));
        return report(cases, issues, expected, target, total, auxiliaryColumns, auxiliaryByCase);
    }
    private Report report(List<SyntheticSurgeryCase> cases, List<Issue> issues, Set<Field> fields, String target, int total, Set<String> auxiliaryColumns, Map<String, Map<String, String>> auxiliaryByCase) {
        var features = fields == null ? List.<String>of() : SurgeryModelService.ALL_FEATURE_GROUPS.stream()
                .filter(group -> fields.stream().anyMatch(f -> group.equals(f.feature))).toList();
        return new Report(cases, issues, features, target, total, List.copyOf(auxiliaryColumns), auxiliaryByCase);
    }
    private static String value(Map<Field, String> values, Field field) { return values.getOrDefault(field, "未提供"); }
    private int number(Map<Field, String> values, Field field, int min, int max, String file, int line, List<Issue> issues) {
        if (!values.containsKey(field)) return 0;
        try {
            int value = new java.math.BigDecimal(values.get(field)).intValueExact();
            if (value < min || value > max) throw new ArithmeticException();
            return value;
        } catch (RuntimeException e) { issues.add(new Issue(file, line, field.label, "必須為 " + min + "–" + max + " 的整數")); return 0; }
    }
    public static int parseMinutes(String raw, TimeFormat format) {
        String value = raw.trim();
        double minutes;
        try {
            if (format == TimeFormat.HOURS_MINUTES || (format == TimeFormat.AUTO && value.contains(":"))) {
                if (!value.matches("\\d{1,7}:[0-5]\\d")) throw new NumberFormatException();
                String[] pieces = value.split(":");
                minutes = Long.parseLong(pieces[0]) * 60.0 + Integer.parseInt(pieces[1]);
            } else minutes = Double.parseDouble(value);
            if (!Double.isFinite(minutes) || minutes <= 0 || minutes > Integer.MAX_VALUE || Math.round(minutes) < 1) throw new NumberFormatException();
            return (int) Math.round(minutes);
        } catch (RuntimeException e) { throw new IllegalArgumentException("時間必須為正數分鐘或時:分（例如 03:35）；小數分鐘四捨五入為整分鐘"); }
    }
    public static void exportReport(Report report, Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("\uFEFF檔案,列號,欄位,原因\n");
            writer.write(csv("檢查摘要") + ",0,," + csv("總筆數=" + report.totalRows() + "；有效=" + report.cases().size() + "；錯誤=" + report.issues().size() + "；目標=" + report.target()) + "\n");
            for (Issue issue : report.issues()) writer.write(csv(issue.file()) + "," + issue.line() + "," + csv(issue.field()) + "," + csv(issue.message()) + "\n");
        }
    }
    private static String csv(String text) { return "\"" + text.replace("\"", "\"\"") + "\""; }

    /** 串流解析 RFC 4180 的引號、逗號與跨行欄位，保留實際起始列號。 */
    private static final class CsvReader implements AutoCloseable {
        private final PushbackReader reader;
        private int line = 1;
        CsvReader(Path path, Charset charset) throws IOException {
            reader = new PushbackReader(new BufferedReader(new InputStreamReader(Files.newInputStream(path),
                    charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))), 2);
            int first = reader.read(); if (first != 0xFEFF && first >= 0) reader.unread(first);
        }
        Row next() throws IOException {
            var cells = new ArrayList<String>(); var field = new StringBuilder();
            int start = line; boolean quoted = false, closed = false, any = false; String error = null;
            int c;
            while ((c = reader.read()) >= 0) {
                any = true;
                if (c == '\r') { int n = reader.read(); if (n != '\n' && n >= 0) reader.unread(n); c = '\n'; }
                if (c == '\n') line++;
                if (quoted) {
                    if (c == '"') {
                        int n = reader.read();
                        if (n == '"') field.append('"');
                        else { quoted = false; closed = true; if (n >= 0) reader.unread(n); }
                    } else field.append((char)c);
                } else if (c == ',' || c == '\n') {
                    cells.add(field.toString().trim()); field.setLength(0); closed = false;
                    if (c == '\n') return new Row(start, List.copyOf(cells), error);
                } else if (c == '"' && field.isEmpty() && !closed) quoted = true;
                else {
                    if (c == '"' || (closed && !Character.isWhitespace(c))) error = "CSV 引號格式錯誤";
                    field.append((char)c);
                }
            }
            if (!any) return null;
            cells.add(field.toString().trim());
            return new Row(start, List.copyOf(cells), quoted ? "CSV 引號未正確結束" : error);
        }
        @Override public void close() throws IOException { reader.close(); }
    }
}
