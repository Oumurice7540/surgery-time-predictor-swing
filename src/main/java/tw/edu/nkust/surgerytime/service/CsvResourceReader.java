package tw.edu.nkust.surgerytime.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads bundled UTF-8 CSV files, including quoted and escaped fields. */
final class CsvResourceReader {
    private CsvResourceReader() {
    }

    static List<Map<String, String>> read(String resourcePath) {
        Objects.requireNonNull(resourcePath, "CSV 資源路徑不可為空");
        var stream = CsvResourceReader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("找不到 CSV 資源：" + resourcePath);
        }
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalStateException("CSV 缺少標題列：" + resourcePath);
            }
            var headers = parseLine(stripBom(headerLine));
            var rows = new ArrayList<Map<String, String>>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                var values = parseLine(line);
                if (values.size() != headers.size()) {
                    throw new IllegalStateException("CSV 第 " + lineNumber + " 列欄位數不符：" + resourcePath);
                }
                var row = new LinkedHashMap<String, String>();
                for (int index = 0; index < headers.size(); index++) {
                    row.put(headers.get(index), values.get(index));
                }
                rows.add(Map.copyOf(row));
            }
            return List.copyOf(rows);
        } catch (IOException exception) {
            throw new IllegalStateException("無法讀取 CSV 資源：" + resourcePath, exception);
        }
    }

    static List<String> parseLine(String line) {
        var values = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IllegalStateException("CSV 引號未正確結束");
        }
        values.add(field.toString().trim());
        return List.copyOf(values);
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
