package com.xinyu.message.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xinyu.message.entity.Message;
import com.xinyu.message.vo.UsageSummary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息服务: message 模块对外唯一入口
 *
 * <p>其他模块（chat 等）只依赖本接口, 禁止直接引用 MessageMapper。
 */
public interface MessageService extends IService<Message> {

    /**
     * 计算会话内下一个业务序号（MAX+1, 单用户写场景无并发问题）
     */
    int nextSequenceNo(Long conversationId);

    /**
     * 游标分页查历史: 取 before 之前（不含）最近 size 条, 按 sequence_no 升序返回
     *
     * <p>雪花ID趋势递增, 以 id 作游标键; before 为空表示取最新一页。
     * M1-3 实现即为最终游标语义, 接口无需再改。
     *
     * @param before 游标消息ID, 可空
     * @param size   每页条数（调用方保证 1~100）
     */
    List<Message> listHistory(Long conversationId, Long before, int size);

    /**
     * 取会话最近 limit 条消息, 按 sequence_no 升序返回（上下文组装用）
     */
    List<Message> listRecent(Long conversationId, int limit);

    /**
     * 聚合统计用户在 [since, +∞) 时间段内的 LLM 用量（stats 模块用）
     *
     * @param since 起始时间, null 表示全部历史
     */
    UsageSummary summarizeUsage(Long userId, LocalDateTime since);
}
