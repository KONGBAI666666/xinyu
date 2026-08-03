package com.xinyu.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文档解析器: 从上传文件中抽取纯文本
 *
 * <p>支持 PDF / Markdown / TXT 三种格式:
 * <ul>
 *   <li>PDF: 用 PDFBox 抽取文本 (含跨页拼接, 去除页眉页脚空白)</li>
 *   <li>Markdown / TXT: 直接按 UTF-8 读取</li>
 * </ul>
 *
 * <p>不支持的格式抛 {@link IllegalArgumentException}, 由调用方标记文档 ERROR。
 */
@Slf4j
@Component
public class DocumentParser {

    /** 文件类型枚举 */
    public enum FileType {
        PDF, MARKDOWN, TXT;

        /** 按扩展名推断类型 */
        public static FileType fromFileName(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".pdf")) return PDF;
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) return MARKDOWN;
            if (lower.endsWith(".txt")) return TXT;
            throw new IllegalArgumentException("不支持的文件类型: " + fileName
                    + " (仅支持 PDF/Markdown/TXT)");
        }
    }

    /**
     * 解析文件为纯文本
     *
     * @param fileName 原始文件名 (用于推断类型)
     * @param content  文件字节流
     * @return 抽取出的纯文本
     */
    public String parse(String fileName, InputStream content) {
        FileType type = FileType.fromFileName(fileName);
        try {
            return switch (type) {
                case PDF -> parsePdf(content);
                case MARKDOWN, TXT -> parseText(content);
            };
        } catch (IOException e) {
            throw new RuntimeException("文件解析失败: " + fileName, e);
        }
    }

    private String parsePdf(InputStream content) throws IOException {
        try (PDDocument doc = Loader.loadPDF(content.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按页抽取后合并, 去除多余空白行
            String text = stripper.getText(doc);
            return text.replaceAll("\\n{3,}", "\n\n").trim();
        }
    }

    private String parseText(InputStream content) throws IOException {
        return new String(content.readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("\\uFEFF", "") // 去 BOM
                .trim();
    }
}
