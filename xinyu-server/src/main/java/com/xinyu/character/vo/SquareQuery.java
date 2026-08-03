package com.xinyu.character.vo;

import lombok.Data;

/**
 * 广场查询请求（GET /api/characters/square）
 *
 * <p>所有字段可选, 缺省: keyword 空 + sort=RECOMMEND。
 */
@Data
public class SquareQuery {

    /** 搜索关键词: 模糊匹配 name / intro, 可空 */
    private String keyword;

    /** 排序方式: RECOMMEND / HOT / LATEST, 缺省 RECOMMEND */
    private String sort;
}
