package com.agentdsl.tools.builtin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PdfToolTest {

    @Test
    public void testPdfExtract(@TempDir Path tempDir) throws IOException {
        File pdfFile = tempDir.resolve("test.pdf").toFile();

        // Setup a simple pdf manually
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream contents = new PDPageContentStream(doc, page)) {
                contents.beginText();
                contents.setFont(new PDType1Font(FontName.HELVETICA), 12);
                contents.newLineAtOffset(100, 700);
                contents.showText("Hello PdfTool");
                contents.endText();
            }
            doc.save(pdfFile);
        }

        PdfTool pdfTool = new PdfTool();
        String result = pdfTool.pdfRead(pdfFile.getAbsolutePath());

        assertNotNull(result);
        assertTrue(result.contains("Hello PdfTool"));
    }
}
