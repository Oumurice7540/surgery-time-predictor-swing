package tw.edu.nkust.surgerytime.model;

import java.util.Objects;

public record ProcessingStep(Operation operation, String column, String value, String upper) {
    public enum Operation {
        KEEP_EQUALS("保留等於指定值的列"), REMOVE_EQUALS("刪除等於指定值的列"),
        KEEP_CONTAINS("保留包含指定文字的列"), REMOVE_CONTAINS("刪除包含指定文字的列"),
        KEEP_RANGE("保留數值範圍內的列"), REMOVE_RANGE("刪除數值範圍內的列"),
        REMOVE_MISSING("刪除此欄為空白的列"), DROP_COLUMN("刪除欄位"), IQR("移除此欄的 IQR 離群值"), CATEGORY_CODES("類別數值化"), MIN_MAX("0～1 正規化"), Z_SCORE("Z 分數標準化");
        private final String label;
        Operation(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public ProcessingStep {
        Objects.requireNonNull(operation); Objects.requireNonNull(column);
        if (column.isBlank()) throw new IllegalArgumentException("請選擇處理欄位");
        value = value == null ? "" : value.trim(); upper = upper == null ? "" : upper.trim();
        switch (operation) {
            case KEEP_EQUALS, REMOVE_EQUALS, KEEP_CONTAINS, REMOVE_CONTAINS -> {
                if (value.isEmpty()) throw new IllegalArgumentException("請輸入比對值；空白資料請用「刪除此欄為空白的列」");
            }
            case KEEP_RANGE, REMOVE_RANGE -> {
                if (number(value) > number(upper)) throw new IllegalArgumentException("下界不可大於上界");
            }
            case IQR -> {
                double factor = number(value);
                if (factor < .1 || factor > 10) throw new IllegalArgumentException("IQR 倍數須介於 0.1–10，通常使用 1.5");
            }
            default -> { }
        }
    }
    public static double number(String value) {
        try {
            double result = new java.math.BigDecimal(value.trim()).doubleValue();
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (RuntimeException e) { throw new IllegalArgumentException("必須輸入有限數值：" + value); }
    }
}
