package com.xinyu.character.enums;

/**
 * 广场排序方式
 *
 * <p>RECOMMEND: 官方优先 + chatCount 倒序（编辑推荐位 + 热度兜底）
 * <p>HOT: favoriteCount 倒序 + chatCount 倒序
 * <p>LATEST: createdAt 倒序
 */
public enum SquareSort {
    /** 推荐: 官方优先 + 对话数倒序 */
    RECOMMEND,
    /** 热门: 收藏数倒序 */
    HOT,
    /** 最新: 创建时间倒序 */
    LATEST
}
