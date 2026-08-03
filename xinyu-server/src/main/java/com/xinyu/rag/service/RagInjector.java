package com.xinyu.rag.service;

import com.xinyu.rag.qdrant.QdrantService.RetrievedChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 注入器: 把检索到的知识片段拼成可注入 system prompt 的文本块
 *
 * <p>注入格式与 {@link com.xinyu.memory.service.MemoryInjector} 风格一致,
 * 由 ChatServiceImpl 调用 RagRetriever 拿到块后, 再调本注入器拼字符串,
 * 透传给 ContextAssembler。
 *
 * <p>知识库块与记忆块功能定位不同:
 * <ul>
 *   <li>记忆块: "用户喜欢 Java" → 引导 AI 个性化</li>
 *   <li>知识块: "Java 接口是规范..." → 提供事实依据供 AI 引用</li>
 * </ul>
 * 两块并列注入 system prompt, 各自独立标记。
 */
@Slf4j
@Component
public class RagInjector {

    /**
     * 拼接知识块为注入文本
     *
     * @param chunks 检索到的知识片段
     * @return 拼好的知识块 (含标题与每条编号); 无知识返回空字符串
     */
    public String inject(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[知识库参考材料，请在回答时优先依据以下内容，若与问题无关可忽略]\n");
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("【片段").append(i + 1).append("】")
                    .append(chunks.get(i).text())
                    .append("\n");
        }
        return sb.toString();
    }
}
