package tw.edu.nkust.surgerytime.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvResourceReaderTest {
    @Test
    void parsesQuotedCommaAndEscapedQuote() {
        assertEquals(
                java.util.List.of("欄位一", "含,逗號", "含\"引號"),
                CsvResourceReader.parseLine("欄位一,\"含,逗號\",\"含\"\"引號\"")
        );
    }

    @Test
    void rejectsUnclosedQuotedField() {
        assertThrows(
                IllegalStateException.class,
                () -> CsvResourceReader.parseLine("正常,\"未結束")
        );
    }
}
