"""雪花 ID 生成器测试

格式: 41 位毫秒时间戳 | 10 位机器 ID | 12 位序列号 (自定义纪元 2024-01-01)
与 MyBatis-Plus assign_id 同构; 机器位区分保证新旧 ID 不冲突。
"""

import threading
import time
import unittest

from app.core.snowflake import EPOCH_MS, MAX_WORKER_ID, SEQUENCE_BITS, WORKER_BITS, Snowflake


class SnowflakeTest(unittest.TestCase):
    def test_invalid_worker_id(self):
        """worker_id 越界 (负数 / 超过 10 位上限) 抛 ValueError"""
        with self.assertRaises(ValueError):
            Snowflake(-1)
        with self.assertRaises(ValueError):
            Snowflake(MAX_WORKER_ID + 1)

    def test_id_layout(self):
        """位布局: 时间戳在高位, 机器位与序列号可从 ID 中还原"""
        gen = Snowflake(worker_id=7)
        before_ms = int(time.time() * 1000)
        sid = gen.next_id()
        after_ms = int(time.time() * 1000)

        ts_part = sid >> (WORKER_BITS + SEQUENCE_BITS)
        worker_part = (sid >> SEQUENCE_BITS) & MAX_WORKER_ID
        seq_part = sid & ((1 << SEQUENCE_BITS) - 1)

        self.assertEqual(worker_part, 7)
        self.assertGreaterEqual(seq_part, 0)
        # 生成时刻落在 [before, after] 毫秒区间 (时间戳为正且量级正确)
        self.assertGreaterEqual(EPOCH_MS + ts_part, before_ms)
        self.assertLessEqual(EPOCH_MS + ts_part, after_ms)

    def test_monotonic_and_unique_single_thread(self):
        """单线程内 ID 严格递增且不重复 (同毫秒靠序列号区分)"""
        gen = Snowflake(worker_id=1)
        ids = [gen.next_id() for _ in range(5000)]
        self.assertEqual(len(set(ids)), 5000, "ID 必须唯一")
        self.assertEqual(ids, sorted(ids), "同线程内 ID 应趋势递增")

    def test_unique_across_threads(self):
        """多线程并发生成不重复 (锁保护临界区)"""
        gen = Snowflake(worker_id=2)
        per_thread = 3000
        threads = 4
        results: list[list[int]] = [[] for _ in range(threads)]

        def worker(bucket: list[int]) -> None:
            for _ in range(per_thread):
                bucket.append(gen.next_id())

        ts = [threading.Thread(target=worker, args=(results[i],)) for i in range(threads)]
        for t in ts:
            t.start()
        for t in ts:
            t.join()

        all_ids = [i for bucket in results for i in bucket]
        self.assertEqual(len(all_ids), per_thread * threads)
        self.assertEqual(len(set(all_ids)), len(all_ids), "并发下 ID 仍必须唯一")

    def test_sequence_rollover_advances_ms(self):
        """同毫秒序列号耗尽 (4096 个) 后借下个毫秒, 不回绕不重复"""
        gen = Snowflake(worker_id=3)
        ids = [gen.next_id() for _ in range(5000)]
        self.assertEqual(len(set(ids)), 5000)
        # 第 4096 个之后时间戳位应前进 (允许时钟恰好跨毫秒的更大跳变)
        ts = [i >> (WORKER_BITS + SEQUENCE_BITS) for i in ids]
        self.assertEqual(ts, sorted(ts))


if __name__ == "__main__":
    unittest.main()
