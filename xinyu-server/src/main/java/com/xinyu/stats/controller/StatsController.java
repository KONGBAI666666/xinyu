package com.xinyu.stats.controller;

import com.xinyu.common.result.Result;
import com.xinyu.common.security.UserContext;
import com.xinyu.stats.service.StatsService;
import com.xinyu.stats.vo.UsageStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用量统计接口（需登录, 仅返回当前用户本人数据）
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 当前用户用量统计: 今日 + 累计 调用次数 / token / 估算成本 */
    @GetMapping("/usage")
    public Result<UsageStatsVO> usage() {
        return Result.success(statsService.getUsage(UserContext.getUserId()));
    }
}
