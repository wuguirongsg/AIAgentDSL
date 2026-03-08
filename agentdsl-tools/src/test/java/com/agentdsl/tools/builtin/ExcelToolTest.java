package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

public class ExcelToolTest {

    @Test
    public void testExcelReadWrite(@TempDir Path tempDir) {
        ExcelTool excelTool = new ExcelTool();
        File excelFile = tempDir.resolve("test.xlsx").toFile();

        String jsonData = "[{\"id\":\"1\", \"name\":\"Alice\"}, {\"id\":\"2\", \"name\":\"Bob\"}]";
        String writeResult = excelTool.excelWrite(excelFile.getAbsolutePath(), jsonData, "Sheet1");

        assertTrue(writeResult.contains("Successfully wrote 2 rows"));

        String readResult = excelTool.excelRead(excelFile.getAbsolutePath(), "Sheet1");
        Gson gson = new Gson();
        List<Map<String, Object>> rows = gson.fromJson(readResult, List.class);

        assertEquals(2, rows.size());
        assertEquals("Alice", rows.get(0).get("name"));
        assertEquals("2", rows.get(1).get("id"));
    }
}
