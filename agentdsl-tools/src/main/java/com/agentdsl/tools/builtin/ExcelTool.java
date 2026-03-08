package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel工具，可读取和写入 .xlsx 文件。
 */
public class ExcelTool {
    private static final Logger log = LoggerFactory.getLogger(ExcelTool.class);
    private final Gson gson;

    public ExcelTool() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @AgentTool(name = "excel_read", description = "读取 Excel (.xlsx) 文件内容，返回 JSON 格式的表格数据")
    public String excelRead(
            @ToolParam(name = "filePath", description = "Excel 文件的绝对或相对路径") String filePath,
            @ToolParam(name = "sheetName", description = "Sheet 名称，默认读取第一个", required = false) String sheetName) {

        File file = new File(filePath);
        if (!file.exists()) {
            return "Error: File not found: " + filePath;
        }

        try (FileInputStream fis = new FileInputStream(file);
                Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet;
            if (sheetName != null && !sheetName.trim().isEmpty()) {
                sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    return "Error: Sheet not found: " + sheetName;
                }
            } else {
                sheet = workbook.getSheetAt(0);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return "[]";
            }

            int numCols = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < numCols; i++) {
                Cell cell = headerRow.getCell(i);
                headers.add(getCellValue(cell));
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;

                Map<String, Object> rowData = new LinkedHashMap<>();
                boolean hasData = false;
                for (int c = 0; c < numCols; c++) {
                    Cell cell = row.getCell(c);
                    String val = getCellValue(cell);
                    if (!val.isEmpty())
                        hasData = true;
                    String h = c < headers.size() ? headers.get(c) : "Column " + c;
                    rowData.put(h, val);
                }
                if (hasData) {
                    rows.add(rowData);
                }
            }

            log.info("Read {} rows from {}", rows.size(), filePath);
            return gson.toJson(rows);

        } catch (Exception e) {
            log.error("Failed to read Excel {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "excel_write", description = "将 JSON 格式的表格数据写入 Excel (.xlsx) 文件")
    public String excelWrite(
            @ToolParam(name = "filePath", description = "目标文件的路径") String filePath,
            @ToolParam(name = "jsonData", description = "JSON 格式的表格数据，应当为一个列表 [{col1: val1, col2: val2}]") String jsonData,
            @ToolParam(name = "sheetName", description = "Sheet 名称，默认写入为 Sheet1", required = false) String sheetName) {

        try {
            log.info("Received jsonData: {}", jsonData);
            List<Map<String, Object>> data = gson.fromJson(jsonData, List.class);
            if (data == null || data.isEmpty()) {
                log.warn("Parsed data is null or empty. Raw jsonData: {}", jsonData);
                return "Error: No data provided.";
            }

            try (Workbook workbook = new XSSFWorkbook()) {
                String sheetNameStr = (sheetName != null && !sheetName.trim().isEmpty()) ? sheetName : "Sheet1";
                Sheet sheet = workbook.createSheet(sheetNameStr);

                // create header
                Map<String, Object> firstRow = data.get(0);
                List<String> headers = new ArrayList<>(firstRow.keySet());
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    headerRow.createCell(i).setCellValue(headers.get(i));
                }

                // write data
                for (int r = 0; r < data.size(); r++) {
                    Row rowData = sheet.createRow(r + 1);
                    Map<String, Object> rowMap = data.get(r);
                    for (int c = 0; c < headers.size(); c++) {
                        Object val = rowMap.get(headers.get(c));
                        if (val != null) {
                            rowData.createCell(c).setCellValue(val.toString());
                        } else {
                            rowData.createCell(c).setCellValue("");
                        }
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(new File(filePath))) {
                    workbook.write(fos);
                }
                log.info("Wrote {} rows to {}", data.size(), filePath);
                return "Successfully wrote " + data.size() + " rows to " + filePath;
            }
        } catch (Exception e) {
            log.error("Failed to write Excel {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                // avoid scientific format or .0
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    return String.format("%d", (long) val);
                } else {
                    return String.format("%s", val);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}
