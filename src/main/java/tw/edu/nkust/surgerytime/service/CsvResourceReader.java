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

/**
 * 專案內部共用的 UTF-8 CSV 讀取器。
 *
 * <p>它負責把 {@code src/main/resources} 中的文字資料轉成 Java 結構。
 * 每一列會成為一個 {@code Map<String, String>}，Map 的 key 是第一列的欄位名稱，
 * value 是該筆資料的內容。</p>
 *
 * <p>例如 {@code procedure,base_minutes} 搭配 {@code 剖腹產,60}，會被轉成
 * {@code procedure -> 剖腹產} 與 {@code base_minutes -> 60}。</p>
 *
 * <p>這是一個 package-private 類別（前面沒有 public），只有同一個 service
 * 套件中的服務可以使用，避免畫面層直接依賴 CSV 格式。</p>
 */
final class CsvResourceReader {
    /** 工具類別只有靜態方法，不需要建立物件。 */
    private CsvResourceReader() {
    }

    /**
     * 從應用程式資源中讀取一份 CSV。
     *
     * @param resourcePath classpath 中的絕對資源路徑，必須以 {@code /} 開頭
     * @return 不可修改的資料列清單；每列都是「欄位名稱 -> 欄位值」的 Map
     * @throws IllegalStateException 找不到檔案、缺少標題或欄位數量不一致時拋出
     */
    static List<Map<String, String>> read(String resourcePath) {
        // 先阻止呼叫者傳入 null，錯誤會比後面找不到資源更容易理解。
        Objects.requireNonNull(resourcePath, "CSV 資源路徑不可為空");

        // getResourceAsStream 能同時讀取開發目錄與打包在 JAR 裡的資源。
        var stream = CsvResourceReader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("找不到 CSV 資源：" + resourcePath);
        }
        // try-with-resources 會在讀取完成或發生錯誤後自動關閉檔案串流。
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            // CSV 第一列是欄位名稱，例如 procedure,base_minutes。
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalStateException("CSV 缺少標題列：" + resourcePath);
            }
            var headers = parseLine(stripBom(headerLine));
            var rows = new ArrayList<Map<String, String>>();
            String line;
            int lineNumber = 1;

            // readLine() 回傳 null 代表已到達檔案結尾。
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // 空白列沒有資料，直接跳過。
                if (line.isBlank()) {
                    continue;
                }
                var values = parseLine(line);

                // 每列資料數量必須和標題數量相同，否則無法正確配對欄位。
                if (values.size() != headers.size()) {
                    throw new IllegalStateException("CSV 第 " + lineNumber + " 列欄位數不符：" + resourcePath);
                }
                // LinkedHashMap 會保留 CSV 欄位原本的順序，方便除錯與閱讀。
                var row = new LinkedHashMap<String, String>();
                for (int index = 0; index < headers.size(); index++) {
                    row.put(headers.get(index), values.get(index));
                }
                // 將每列轉成不可修改的 Map，避免後續邏輯意外更動原始資料。
                rows.add(Map.copyOf(row));
            }
            // 外層 List 同樣設成不可修改。
            return List.copyOf(rows);
        } catch (IOException exception) {
            throw new IllegalStateException("無法讀取 CSV 資源：" + resourcePath, exception);
        }
    }

    /**
     * 將一行 CSV 文字切成欄位。
     *
     * <p>不能直接使用 {@code split(",")}，因為合法 CSV 欄位本身也可能包含逗號，
     * 例如 {@code "手術,特殊說明",90}。本方法會追蹤目前是否位於雙引號內。</p>
     *
     * @param line 一行 CSV 文字
     * @return 依序排列且不可修改的欄位值
     */
    static List<String> parseLine(String line) {
        var values = new ArrayList<String>();
        // StringBuilder 用來逐字累積目前欄位的內容。
        var field = new StringBuilder();
        // true 代表目前位於雙引號包住的欄位中，裡面的逗號不能當分隔符號。
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                // CSV 使用連續兩個雙引號表示欄位內容中的一個雙引號。
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    // 單一雙引號代表進入或離開引號區域。
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                // 只有不在引號內的逗號才代表欄位結束。
                values.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        // 迴圈結束後仍在引號內，表示該行少了一個結束雙引號。
        if (quoted) {
            throw new IllegalStateException("CSV 引號未正確結束");
        }
        // 最後一個欄位後面通常沒有逗號，因此需要在迴圈後額外加入。
        values.add(field.toString().trim());
        return List.copyOf(values);
    }

    /**
     * 移除某些 UTF-8 檔案開頭可能出現的 BOM 字元，避免第一個欄位名稱多出隱藏字元。
     */
    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
