package com.xinyu.rag.service;

import com.xinyu.rag.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块器: 按字符数切片 + 重叠避免切断语义
 *
 * <p>策略 (简单可靠, M3 v1):
 * <ul>
 *   <li>按 chunkSize 字符切块 (默认 1000)</li>
 *   <li>相邻块重叠 chunkOverlap 字符 (默认 50), 避免句子被切断导致语义丢失</li>
 *   <li>优先按段落切: 遇到 \n\n 优先断开, 块不超过 chunkSize 上限</li>
 * </ul>
 *
 * <p>不做语义分块 (太重, 简历够用版不需要)。
 */
@Component
@RequiredArgsConstructor
public class ChunkSplitter {

    private final RagProperties ragProperties;

    /**
     * 把长文本切成块
     *
     * @param text 原始文本 (已去页眉页脚)
     * @return 块列表, 每块约 chunkSize 字符
     */
    public List<String> split(String text) {
        int chunkSize = ragProperties.getChunkSize();
        int overlap = ragProperties.getChunkOverlap();
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // 先按段落粗分 (段落 = \n\n 分隔)
        String[] paragraphs = text.split("\\n{2,}");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 当前块加这段不超限 → 追加到当前块
            if (current.length() + trimmed.length() + 2 <= chunkSize) {
                if (current.length() > 0) current.append("\n\n");
                current.append(trimmed);
                continue;
            }

            // 当前块已有内容 → 先收尾入列
            if (current.length() > 0) {
                chunks.add(current.toString());
                // 保留 overlap 字符作为下一块开头 (避免切断语义)
                String tail = current.substring(Math.max(0, current.length() - overlap));
                current = new StringBuilder(tail);
            }

            // 这段本身超长 → 强制硬切
            if (trimmed.length() > chunkSize) {
                hardSplit(trimmed, chunkSize, overlap, chunks, current);
            } else {
                current.append(trimmed);
            }
        }

        // 最后一块收尾
        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    /** 超长段落强制硬切: 每 chunkSize 字符一块, 块间 overlap 重叠 */
    private void hardSplit(String text, int chunkSize, int overlap,
                           List<String> chunks, StringBuilder current) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String piece = text.substring(start, end);
            if (current.length() > 0) {
                chunks.add(current.toString() + piece);
            } else {
                chunks.add(piece);
            }
            // 下一块从 end - overlap 开始 (保留重叠)
            start = end - overlap;
            if (start <= 0) break;
            current = new StringBuilder();
        }
    }
}
