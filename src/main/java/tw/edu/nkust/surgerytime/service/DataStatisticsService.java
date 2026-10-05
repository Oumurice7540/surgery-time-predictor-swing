package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.ProcessingTable;
import java.util.*;

/** Read-only aggregation of the currently applied processing table. */
public final class DataStatisticsService {
    public record Group(List<String> values, long count, double average, int minimum, int maximum, double percent) {
        public Group { values = List.copyOf(values); }
        public String label() { return String.join(" ／ ", values); }
    }
    public record Result(List<Group> groups, int included, int excluded) {
        public Result { groups = List.copyOf(groups); }
    }

    public Result summarize(ProcessingTable table, List<String> keys) {
        if (keys.isEmpty() || keys.size() > 2 || new HashSet<>(keys).size() != keys.size())
            throw new IllegalArgumentException("請選擇一至兩個不同的分組欄位");
        keys.forEach(table::column);
        Map<List<String>, IntSummaryStatistics> aggregates = new HashMap<>();
        int excluded = 0;
        for (var row : table.rows()) {
            var values = keys.stream().map(k -> row.values().getOrDefault(k, "").strip()).toList();
            if (values.stream().anyMatch(String::isEmpty)) { excluded++; continue; }
            aggregates.computeIfAbsent(values, ignored -> new IntSummaryStatistics()).accept(row.caseData().actualMinutes());
        }
        int included = table.rows().size() - excluded;
        Comparator<List<String>> order = (left, right) -> {
            for (int i = 0; i < left.size(); i++) {
                int c = compareValue(left.get(i), right.get(i));
                if (c != 0) return c;
            }
            return 0;
        };
        var groups = aggregates.entrySet().stream().sorted(Map.Entry.comparingByKey(order)).map(e -> {
            var s = e.getValue();
            return new Group(e.getKey(), s.getCount(), s.getAverage(), s.getMin(), s.getMax(), 100.0 * s.getCount() / included);
        }).toList();
        return new Result(groups, included, excluded);
    }

    private static int compareValue(String a, String b) {
        var left = numeric(a); var right = numeric(b);
        if (left != null && right != null) {
            int c = left.compareTo(right);
            if (c != 0) return c;
        } else if (left != null) return -1;
        else if (right != null) return 1;
        return a.compareTo(b);
    }
    private static java.math.BigDecimal numeric(String value) {
        try { return new java.math.BigDecimal(value); }
        catch (NumberFormatException ignored) { return null; }
    }
}
