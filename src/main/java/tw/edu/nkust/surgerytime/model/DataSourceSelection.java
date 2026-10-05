package tw.edu.nkust.surgerytime.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 保存資料來源頁的選擇，供下一個「資料轉檔」頁使用。
 * 原始檔只記錄路徑，不會在使用者按下轉檔以前讀入或訓練。
 */
public record DataSourceSelection(SourceType type, List<Path> csvFiles) {
    public enum SourceType {
        SYNTHETIC,
        CSV_FILES
    }

    public DataSourceSelection {
        Objects.requireNonNull(type, "資料來源類型不可為空");
        csvFiles = csvFiles == null
                ? List.of()
                : csvFiles.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        if (type == SourceType.CSV_FILES && csvFiles.isEmpty()) {
            throw new IllegalArgumentException("CSV 資料來源至少要有一個檔案");
        }
    }

    public static DataSourceSelection synthetic() {
        return new DataSourceSelection(SourceType.SYNTHETIC, List.of());
    }
}
