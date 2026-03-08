package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * PDF 内容读取工具。
 * 使用 PDFBox 提取纯文本。
 */
public class PdfTool {
    private static final Logger log = LoggerFactory.getLogger(PdfTool.class);

    @AgentTool(name = "pdf_read", description = "从 PDF 文件中提取纯文本内容")
    public String pdfRead(
            @ToolParam(name = "filePath", description = "PDF 文件的绝对或相对路径") String filePath) {

        File file = new File(filePath);
        if (!file.exists()) {
            return "Error: File not found: " + filePath;
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // 更好地保留段落结构
            String text = stripper.getText(document);
            log.info("Extracted {} chars from {}", text.length(), filePath);

            // 如果文本太长可能需要截断，但交给大模型判断更好，这里做一层保险（比如限制200KB）
            int maxLength = 200 * 1024;
            if (text.length() > maxLength) {
                return text.substring(0, maxLength) + "\n\n...[Text truncated due to length limits]";
            }
            return text;
        } catch (Exception e) {
            log.error("Failed to read PDF {}", filePath, e);
            return "Error: Failed to read PDF: " + e.getMessage();
        }
    }
}
