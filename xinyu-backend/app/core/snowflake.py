"""雪花 ID 生成器

主键策略与 Java MyBatis-Plus 的 assign_id 保持同一格式:
41 位时间戳 + 10 位机器 ID + 12 位序列号, 趋势递增的 BIGINT。
存量数据无需迁移, 新旧 ID 天然不冲突 (机器位区分)。
"""

import threading
import time

# 自定义纪元: 2024-01-01 00:00:00 UTC (毫秒)
EPOCH_MS = 1_704_067_200_000

WORKER_BITS = 10
SEQUENCE_BITS = 12
MAX_WORKER_ID = (1 << WORKER_BITS) - 1
MAX_SEQUENCE = (1 << SEQUENCE_BITS) - 1


class Snowflake:
    def __init__(self, worker_id: int = 1):
        if not 0 <= worker_id <= MAX_WORKER_ID:
            raise ValueError(f"worker_id 必须在 0~{MAX_WORKER_ID} 之间")
        self.worker_id = worker_id
        self.sequence = 0
        self._last_ts = -1
        self._lock = threading.Lock()

    def _current_ms(self) -> int:
        return int(time.time() * 1000)

    def _wait_next_ms(self, last_ts: int) -> int:
        ts = self._current_ms()
        while ts <= last_ts:
            ts = self._current_ms()
        return ts

    def next_id(self) -> int:
        with self._lock:
            ts = self._current_ms()
            if ts < self._last_ts:
                # 时钟回拨: 借用下个毫秒, 保证单调递增
                ts = self._last_ts
            if ts == self._last_ts:
                self.sequence = (self.sequence + 1) & MAX_SEQUENCE
                if self.sequence == 0:
                    ts = self._wait_next_ms(self._last_ts)
            else:
                self.sequence = 0
            self._last_ts = ts
            return (
                ((ts - EPOCH_MS) << (WORKER_BITS + SEQUENCE_BITS))
                | (self.worker_id << SEQUENCE_BITS)
                | self.sequence
            )
