import { get } from '@/api/request'
import type { UsageStatsVO } from '@/types/api'

/**
 * 用量统计接口（M2 Token 统计, 需登录; 仅返回当前用户本人数据）
 */
export const statsApi = {
  /** 今日 + 累计 调用次数 / token / 估算成本 */
  usage(): Promise<UsageStatsVO> {
    return get('/stats/usage')
  },
}
