package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** 順序執行純資料轉換，不更改來源、目前模型或應用程式狀態。 */
public final class DataProcessingService {
    public record Audit(int step, String description, int before, int after, String detail) { }
    public record Result(ProcessingTable table, List<Audit> audits) {
        public Result { audits = List.copyOf(audits); }
        public boolean trainable() { return table.rows().size() >= 200 && table.rows().size() <= 100_000 && !SurgeryModelService.eligibleFeatures(table).isEmpty(); }
    }
    public record Recipe(String target, List<ProcessingStep> steps) {
        public Recipe {
            if (target == null || target.isBlank()) throw new IllegalArgumentException("步驟檔缺少預測目標");
            steps = List.copyOf(steps);
            if (steps.size() > 200) throw new IllegalArgumentException("最多支援 200 個處理步驟");
        }
    }
    public Result process(ProcessingTable source, Recipe recipe) {
        if (!source.target().equals(recipe.target())) throw new IllegalArgumentException("步驟的預測目標為「" + recipe.target() + "」，目前資料為「" + source.target() + "」");
        var table = source; var audits = new ArrayList<Audit>(); int index = 0; boolean transforming=false, iqred=false;
        for (ProcessingStep step : recipe.steps()) {
            index++;
            try {
                if(transforming&&!FittedTransforms.transformation(step)) throw new IllegalArgumentException("列篩選／刪欄請排在轉換步驟之前；轉換在拆分後估計");
                if(iqred && !FittedTransforms.transformation(step) && step.operation()!=ProcessingStep.Operation.IQR && step.operation()!=ProcessingStep.Operation.DROP_COLUMN)
                    throw new IllegalArgumentException("固定條件篩選請排在 IQR 前面，確保探索與正式流程順序一致");
                if(step.operation()==ProcessingStep.Operation.IQR)iqred=true;
                var column = table.column(step.column());
                int before = table.rows().size(); String detail = "";
                if (FittedTransforms.transformation(step)) {
                    transforming=true;
                    if (step.column().equals("case_id") || step.column().equals("actual_minutes")) throw new IllegalArgumentException("識別碼及預測目標不能套用输入轉換");
                    detail="已記錄轉換；正式訓練只由訓練集估計參數。原始值保留，探索預覽不會覆蓋資料。";
                } else if (step.operation() == ProcessingStep.Operation.DROP_COLUMN) {
                    if (step.column().equals("case_id") || step.column().equals("actual_minutes"))
                        throw new IllegalArgumentException("案例編號與預測目標不能刪除");
                    var remaining = table.columns().stream().filter(c -> !c.key().equals(step.column())).toList();
                    if (SurgeryModelService.eligibleFeatures(new ProcessingTable(remaining,List.of(),table.target())).isEmpty()) throw new IllegalArgumentException("至少保留一個可供訓練的輸入特徵");
                    var rows = table.rows().stream().map(row -> {
                        var values = new LinkedHashMap<>(row.values()); values.remove(step.column());
                        return new ProcessingTable.Row(row.caseData(), values);
                    }).toList();
                    table = new ProcessingTable(remaining, rows, table.target());
                    detail = column.feature() == null ? "此輔助欄已從結果移除" : "此欄不再進入後續特徵選取與訓練";
                } else {
                    Predicate<ProcessingTable.Row> keep;
                    if (step.operation() == ProcessingStep.Operation.IQR) {
                        // 先完整驗證，空白不納入四分位數，且保留空白列供使用者另外處理。
                        var numbers = table.rows().stream().filter(r -> !cell(r, step).isEmpty())
                                .map(r -> numericCell(r, step)).sorted().toList();
                        if (numbers.size() < 4) throw new IllegalArgumentException("IQR 至少需要 4 筆非空白數值");
                        double q1 = percentile(numbers, .25), q3 = percentile(numbers, .75), factor = ProcessingStep.number(step.value());
                        double lower = q1 - factor * (q3 - q1), upper = q3 + factor * (q3 - q1);
                        if (!Double.isFinite(lower) || !Double.isFinite(upper)) throw new IllegalArgumentException("IQR 範圍超出可計算數值");
                        keep = r -> cell(r, step).isEmpty() || (numericCell(r, step) >= lower && numericCell(r, step) <= upper);
                        long missing = table.rows().size() - numbers.size();
                        detail = String.format(Locale.TAIWAN, "Q1=%.4f，Q3=%.4f；保留 [%.4f, %.4f]；空白 %d 筆保留", q1, q3, lower, upper, missing);
                    } else keep = predicate(step);
                    table = new ProcessingTable(table.columns(), table.rows().stream().filter(keep).toList(), table.target());
                }
                audits.add(new Audit(index, step.operation() + "：" + column.label(), before, table.rows().size(), detail));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("第 " + index + " 步失敗：" + e.getMessage(), e);
            }
        }
        return new Result(table, audits);
    }
    private Predicate<ProcessingTable.Row> predicate(ProcessingStep step) {
        return switch (step.operation()) {
            case KEEP_EQUALS -> r -> cell(r, step).equals(step.value());
            case REMOVE_EQUALS -> r -> !cell(r, step).equals(step.value());
            case KEEP_CONTAINS -> r -> cell(r, step).contains(step.value());
            case REMOVE_CONTAINS -> r -> !cell(r, step).contains(step.value());
            case REMOVE_MISSING -> r -> !cell(r, step).isEmpty();
            case KEEP_RANGE, REMOVE_RANGE -> {
                double lower = ProcessingStep.number(step.value()), upper = ProcessingStep.number(step.upper());
                yield r -> {
                    // 範圍保留不選取空白；範圍刪除不刪除空白。
                    boolean inside = !cell(r, step).isEmpty() && numericCell(r, step) >= lower && numericCell(r, step) <= upper;
                    return step.operation() == ProcessingStep.Operation.KEEP_RANGE ? inside : !inside;
                };
            }
            default -> throw new IllegalArgumentException("不支援的列處理操作");
        };
    }
    private static String cell(ProcessingTable.Row row, ProcessingStep step) { return row.values().getOrDefault(step.column(), "").trim(); }
    private static double numericCell(ProcessingTable.Row row, ProcessingStep step) {
        try { return ProcessingStep.number(cell(row, step)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("案例 " + row.caseData().caseId() + " 的欄位 " + step.column() + " 不是數值：" + cell(row, step)); }
    }
    private static double percentile(List<Double> sorted, double p) { return sorted.get(Math.max(0, (int)Math.ceil(p * sorted.size()) - 1)); }

    /** 步驟檔是 UTF-8 Properties，沒有 Java 物件反序列化或可執行程式碼。 */
    public void saveRecipe(Recipe recipe, Path path) throws IOException {
        var props = new Properties(); props.setProperty("format", "surgery-processing-steps"); props.setProperty("version", "1");
        props.setProperty("target", recipe.target()); props.setProperty("count", Integer.toString(recipe.steps().size()));
        for (int i = 0; i < recipe.steps().size(); i++) {
            var step = recipe.steps().get(i); String prefix = "step." + i + ".";
            props.setProperty(prefix + "operation", step.operation().name()); props.setProperty(prefix + "column", step.column());
            props.setProperty(prefix + "value", step.value()); props.setProperty(prefix + "upper", step.upper());
        }
        // 先完整寫入同目錄暫存檔，再取代目的檔，寫入失敗時保留舊步驟。
        Path absolute = path.toAbsolutePath(); Path temporary = Files.createTempFile(absolute.getParent(), "steps-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { props.store(writer, "Surgery processing recipe v1"); }
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public Recipe loadRecipe(Path path) throws IOException {
        if (Files.size(path) > 1_048_576) throw new IOException("步驟檔過大（上限 1 MB）");
        var props = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { props.load(reader); }
        if (!"surgery-processing-steps".equals(props.getProperty("format")) || !"1".equals(props.getProperty("version")))
            throw new IOException("不支援的步驟檔格式或版本；請使用本程式儲存的 .steps 檔");
        try {
            int count = Integer.parseInt(required(props, "count"));
            if (count < 0 || count > 200) throw new IllegalArgumentException("步驟數必須介於 0–200");
            var steps = new ArrayList<ProcessingStep>();
            for (int i = 0; i < count; i++) {
                String prefix = "step." + i + ".";
                steps.add(new ProcessingStep(ProcessingStep.Operation.valueOf(required(props, prefix + "operation")),
                        required(props, prefix + "column"), required(props, prefix + "value"), required(props, prefix + "upper")));
            }
            return new Recipe(required(props, "target"), steps);
        } catch (IllegalArgumentException e) { throw new IOException("步驟檔內容無效：" + e.getMessage(), e); }
    }
    private String required(Properties props, String key) {
        String value = props.getProperty(key); if (value == null) throw new IllegalArgumentException("缺少 " + key); return value;
    }
    public void writeCsv(ProcessingTable table, Path path) throws IOException {
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            // 模型欄位使用原標準名稱，輔助欄使用來源欄名；所有被刪除欄位均不輸出。
            var used = new HashSet<String>();
            table.columns().stream().filter(c -> !c.key().startsWith("extra:")).forEach(c -> used.add(c.key()));
            var headers = new ArrayList<String>();
            for (var column : table.columns()) {
                String name = column.key();
                if (name.startsWith("extra:")) {
                    name = name.substring(6);
                    while (!used.add(name)) name = "來源_" + name;
                }
                headers.add(name);
            }
            writeRow(writer, headers);
            for (var row : table.rows()) writeRow(writer, table.columns().stream().map(c -> row.values().getOrDefault(c.key(), "")).toList());
        }
    }
    private void writeRow(Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(','); writer.write('"'); writer.write(values.get(i).replace("\"", "\"\"")); writer.write('"');
        }
        writer.write('\n');
    }
}
