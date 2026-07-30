package com.xinyu.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xinyu.message.enums.MessageRole;
import com.xinyu.message.enums.MessageStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体（对应 scripts/schema.sql 的 message 表, 核心表）
 *
 * <p>枚举列（message_type/status）依赖 MyBatis 默认 EnumTypeHandler
 * 按枚举名读写 VARCHAR, 与数据库字典值严格一致。
 */
@Data
@TableName("message")
public class Message {

    /** 雪花ID（趋势递增, 兼作游标分页的排序键） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属会话 */
    private Long conversationId;

    /** 冗余: 归属用户（统计/权限校验免 join） */
    private Long userId;

    /** 消息树: 重新生成的候选挂同一 USER 消息下（M1-3 暂不使用） */
    private Long parentMessageId;

    /** 会话内自增业务序号（MAX+1, 单用户写无并发问题）, 上下文排序依据 */
    private Integer sequenceNo;

    /** 前端 UUID, 幂等防重复提交, uk(user_id, client_message_id), 可空 */
    private String clientMessageId;

    /** USER / ASSISTANT / SYSTEM */
    private MessageRole messageType;

    /** Markdown 原文 */
    private String content;

    /** GENERATING / COMPLETED / FAILED / STOPPED */
    private MessageStatus status;

    /** 输入 token（仅 ASSISTANT） */
    private Integer promptTokens;

    /** 输出 token（仅 ASSISTANT） */
    private Integer completionTokens;

    /** 实际使用模型（溯源） */
    private String modelCode;

    /** NONE / LIKE / DISLIKE（M1-3 走数据库默认 NONE） */
    private String feedback;

    /** 重新生成次数 */
    private Integer regenerateCount;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
}
