package com.agentdsl.tools.builtin;

import com.agentdsl.core.annotation.AgentTool;
import com.agentdsl.core.annotation.ToolParam;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Word 文档工具。
 * 支持读取和写入 .docx 格式文档（写入支持 Markdown 转换为 Word 格式）。
 */
public class WordTool {
    private static final Logger log = LoggerFactory.getLogger(WordTool.class);
    private final List<String> allowedDirectories;

    public WordTool() {
        this(List.of("/tmp")); // 默认只允许操作 /tmp 目录
    }

    public WordTool(List<String> allowedDirectories) {
        this.allowedDirectories = allowedDirectories;
    }

    private boolean isAllowed(Path path) {
        String normalizedPath = path.toAbsolutePath().normalize().toString();
        for (String allowed : allowedDirectories) {
            String normalizedAllowed = Paths.get(allowed).toAbsolutePath().normalize().toString();
            if (normalizedPath.startsWith(normalizedAllowed)) {
                return true;
            }
        }
        return false;
    }

    @AgentTool(name = "word_read", description = "读取本地 Word（.docx）文档的纯文本内容。")
    public String wordRead(@ToolParam(name = "filePath", description = "Word 文档的本地绝对路径 (.docx)") String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";
            if (!Files.exists(path) || !Files.isRegularFile(path)) return "Error: File not found or not regular.";

            try (FileInputStream fis = new FileInputStream(path.toFile());
                 XWPFDocument document = new XWPFDocument(fis)) {
                
                StringBuilder sb = new StringBuilder();
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    sb.append(paragraph.getText()).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("读取 Word 失败: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "word_write", description = "创建新的 Word 文档（.docx）并写入内容，自动将传入的 Markdown 格式解析转化为 Word 样式（标题、加粗、斜体、列表、代码块等）。如果文件已存在将被覆盖。")
    public String wordWrite(
            @ToolParam(name = "filePath", description = "要保存的绝对路径 (.docx)") String filePath,
            @ToolParam(name = "content", description = "需要写入的 Markdown 内容") String content) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!isAllowed(path)) return "Error: Access denied.";

            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            try (XWPFDocument document = new XWPFDocument();
                 FileOutputStream out = new FileOutputStream(path.toFile())) {
                
                Parser parser = Parser.builder().build();
                Node documentNode = parser.parse(content);
                
                DocxVisitor visitor = new DocxVisitor(document);
                documentNode.accept(visitor);
                
                document.write(out);
                return "Successfully wrote docx to " + path;
            }
        } catch (Exception e) {
            log.error("写入 Word 失败: {}", filePath, e);
            return "Error: " + e.getMessage();
        }
    }

    private static class DocxVisitor extends AbstractVisitor {
        private final XWPFDocument document;
        private XWPFParagraph currentParagraph;
        private int listDepth = 0;
        private boolean isOrderedList = false;
        private int orderedListIndex = 1;

        public DocxVisitor(XWPFDocument document) {
            this.document = document;
        }

        @Override
        public void visit(Paragraph paragraph) {
            currentParagraph = document.createParagraph();
            if (listDepth > 0) {
                currentParagraph.setIndentationLeft(360 * listDepth);
                XWPFRun bullet = currentParagraph.createRun();
                if (isOrderedList) {
                    bullet.setText(orderedListIndex + ". ");
                    orderedListIndex++;
                } else {
                    bullet.setText("• ");
                }
            }
            visitChildren(paragraph);
        }

        @Override
        public void visit(Heading heading) {
            currentParagraph = document.createParagraph();
            currentParagraph.setStyle("Heading" + heading.getLevel());
            visitChildren(heading);
            
            // Apply formatting to heading runs
            for (XWPFRun run : currentParagraph.getRuns()) {
                run.setBold(true);
                run.setFontSize(24 - (heading.getLevel() * 2));
            }
        }

        @Override
        public void visit(Text text) {
            if (currentParagraph == null) {
                currentParagraph = document.createParagraph();
            }
            XWPFRun run = currentParagraph.createRun();
            run.setText(text.getLiteral());
            
            Node parent = text.getParent();
            if (parent instanceof StrongEmphasis) {
                run.setBold(true);
            } else if (parent instanceof Emphasis) {
                run.setItalic(true);
            }
        }

        @Override
        public void visit(BulletList bulletList) {
            boolean prevOrdered = isOrderedList;
            isOrderedList = false;
            listDepth++;
            visitChildren(bulletList);
            listDepth--;
            isOrderedList = prevOrdered;
        }

        @Override
        public void visit(OrderedList orderedList) {
            boolean prevOrdered = isOrderedList;
            int prevIndex = orderedListIndex;
            isOrderedList = true;
            orderedListIndex = 1; // 简单的统一从 1 开始
            
            listDepth++;
            visitChildren(orderedList);
            listDepth--;
            
            isOrderedList = prevOrdered;
            orderedListIndex = prevIndex;
        }

        @Override
        public void visit(ListItem listItem) {
            visitChildren(listItem);
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            currentParagraph = document.createParagraph();
            currentParagraph.setIndentationLeft(360);
            String[] lines = fencedCodeBlock.getLiteral().split("\n");
            for (int i = 0; i < lines.length; i++) {
                XWPFRun run = currentParagraph.createRun();
                run.setFontFamily("Courier New");
                run.setText(lines[i]);
                if (i < lines.length - 1) {
                    run.addBreak();
                }
            }
        }
        
        @Override
        public void visit(Code code) {
            if (currentParagraph == null) {
                currentParagraph = document.createParagraph();
            }
            XWPFRun run = currentParagraph.createRun();
            run.setFontFamily("Courier New");
            run.setText(code.getLiteral());
        }
    }
}
