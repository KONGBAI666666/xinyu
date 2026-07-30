package com.xinyu.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 普通分页结果（page + size 场景）
 *
 * <p>用于角色广场、会话列表、管理后台等常规分页；
 * 消息历史使用游标分页，不走本结构（见 docs/design.md 3.1）。
 *
 * @param <T> 记录类型
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页记录 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 构建分页结果 */
    public static <T> PageResult<T> of(List<T> records, long total) {
        return new PageResult<>(records, total);
    }

    /** 空页（查询无结果时避免前端判空 null） */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0L);
    }
}
