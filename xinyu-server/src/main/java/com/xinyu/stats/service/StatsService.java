package com.xinyu.stats.service;

import com.xinyu.stats.vo.UsageStatsVO;

/**
 * 用量统计服务
 *
 * <p>当前用户视角: 仅统计本人调用量（数据按用户隔离的延续）。
 * 后续如需管理后台总览, 新增 AdminStatsService 即可, 不污染本接口。
 */
public interface StatsService {

    /**
     * 获取当前登录用户的用量统计（今日 + 累计 + 估算成本）
     */
    UsageStatsVO getUsage(Long userId);
}
