package com.xinyu.stats.service.impl;

import com.xinyu.llm.config.LlmProperties;
import com.xinyu.message.service.MessageService;
import com.xinyu.message.vo.UsageSummary;
import com.xinyu.stats.service.StatsService;
import com.xinyu.stats.vo.UsageStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 用量统计实现
 *
 * <p>两次聚合查询（今日 + 累计）+ 成本估算。ZoneId 固定 Asia/Shanghai,
 * 与 application.yml 中 jackson 时区保持一致, 避免"今日"边界漂移。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    /** "今日"的时区基准 — 与 application.yml 的 jackson.time-zone 一致 */
    private static final ZoneId STATS_ZONE = ZoneId.of("Asia/Shanghai");

    private final MessageService messageService;

    private final LlmProperties llmProperties;

    @Override
    public UsageStatsVO getUsage(Long userId) {
        LocalDate today = LocalDate.now(STATS_ZONE);
        LocalDateTime sinceMidnight = today.atStartOfDay(STATS_ZONE).toLocalDateTime();

        UsageSummary todaySummary = messageService.summarizeUsage(userId, sinceMidnight);
        UsageSummary totalSummary = messageService.summarizeUsage(userId, null);

        return UsageStatsVO.builder()
                .date(today)
                .todayCallCount(todaySummary.callCount())
                .todayPromptTokens(todaySummary.promptTokens())
                .todayCompletionTokens(todaySummary.completionTokens())
                .todayCost(estimateCost(todaySummary))
                .totalCallCount(totalSummary.callCount())
                .totalPromptTokens(totalSummary.promptTokens())
                .totalCompletionTokens(totalSummary.completionTokens())
                .totalCost(estimateCost(totalSummary))
                .build();
    }

    /** 成本 = promptTokens/1000 * 输入单价 + completionTokens/1000 * 输出单价 */
    private double estimateCost(UsageSummary summary) {
        double inputCost = summary.promptTokens() / 1000.0 * llmProperties.inputPricePer1k();
        double outputCost = summary.completionTokens() / 1000.0 * llmProperties.outputPricePer1k();
        // 保留 4 位小数, 避免前端展示一长串浮点尾数
        return Math.round((inputCost + outputCost) * 10000) / 10000.0;
    }
}
