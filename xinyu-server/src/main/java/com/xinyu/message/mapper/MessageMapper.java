package com.xinyu.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.message.entity.Message;
import com.xinyu.message.vo.UsageSummary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 消息 Mapper
 *
 * <p>M1-3 全部走 BaseMapper; M2 起新增 token 用量聚合查询（stats 模块用）。
 */
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 聚合统计用户在 [since, +∞) 时间段内的 LLM 用量
     *
     * <p>口径: ASSISTANT 消息且 prompt_tokens / completion_tokens 至少一个非空
     * （即真实发生 LLM 调用, 排除 greeting 与中断占位）。
     * since 为 null 时统计全部历史。
     */
    @Select("""
            SELECT COUNT(*)                                            AS callCount,
                   COALESCE(SUM(prompt_tokens), 0)                     AS promptTokens,
                   COALESCE(SUM(completion_tokens), 0)                 AS completionTokens
            FROM message
            WHERE user_id = #{userId}
              AND message_type = 'ASSISTANT'
              AND deleted = 0
              AND (prompt_tokens IS NOT NULL OR completion_tokens IS NOT NULL)
              AND (#{since} IS NULL OR created_at >= #{since})
            """)
    UsageSummary summarizeUsage(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
