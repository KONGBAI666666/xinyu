package com.xinyu.stats.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户用量统计 VO（GET /api/stats/usage 返回体）
 *
 * <p>同时给出"今日"与"累计"两档, 前端可一屏展示; 成本基于 LlmProperties 单价估算,
 * 仅作参考（实际计费以供应商账单为准）。
 */
@Data
@Builder
public class UsageStatsVO {

    /** 统计日期（今日那一档对应的日期, 便于前端展示） */
    private LocalDate date;

    /** 今日调用次数 */
    private long todayCallCount;

    /** 今日输入 token */
    private long todayPromptTokens;

    /** 今日输出 token */
    private long todayCompletionTokens;

    /** 今日估算成本（元） */
    private double todayCost;

    /** 累计调用次数 */
    private long totalCallCount;

    /** 累计输入 token */
    private long totalPromptTokens;

    /** 累计输出 token */
    private long totalCompletionTokens;

    /** 累计估算成本（元） */
    private double totalCost;
}
