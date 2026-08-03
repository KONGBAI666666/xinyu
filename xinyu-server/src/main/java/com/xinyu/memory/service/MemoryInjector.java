package com.xinyu.memory.service;

import com.xinyu.memory.entity.Memory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆注入器: 查询 Top-K ACTIVE 记忆并拼成可注入的文本块
 *
 * <p>注入策略（M2.1 决策）: 按 importance 权重截断 Top-K, HIGH 优先, 最多 5 条,
 * 拼接到角色 system prompt 末尾。ContextAssembler 只接收拼好的字符串, 不依赖 memory 实体。
 *
 * <p>独立为 Component 而非放在 ContextAssembler: ContextAssembler 属于 chat 模块,
 * 不应横向依赖 memory 模块（设计文档硬规则: 禁止业务模块横向调用）。
 * 由 ChatServiceImpl 调用本注入器拿到文本块, 再透传给 ContextAssembler。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryInjector {

    /** 注入上限: 最多 5 条, 控制 token 成本 */
    public static final int INJECT_LIMIT = 5;

    private final MemoryService memoryService;

    /**
     * 注入查询: 拼接记忆文本块
     *
     * @return 拼好的记忆块（含标题与每条编号）; 无记忆返回空字符串
     */
    public String inject(Long userId, Long characterId) {
        List<Memory> memories = memoryService.listActiveForInject(userId, characterId, INJECT_LIMIT);
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n[关于用户的长期记忆，请在回复时自然参考，不要生硬提及]\n");
        sb.append(memories.stream()
                .map(m -> "- " + m.getContent())
                .collect(Collectors.joining("\n")));
        return sb.toString();
    }
}
