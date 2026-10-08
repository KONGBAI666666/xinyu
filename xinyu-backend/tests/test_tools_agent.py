"""工具层测试: 安全计算/执行器/agent 循环 (假客户端, 无网络)"""

import unittest

from app.ai.tools.agent import MAX_TOOL_ROUNDS, run_agent_stream
from app.ai.tools.registry import TOOLS, make_executor, safe_calculate


class SafeCalculateTest(unittest.TestCase):
    def test_basic_arithmetic(self) -> None:
        self.assertIn("10", safe_calculate("(3+7)*1"))
        self.assertIn("3.5", safe_calculate("7/2"))

    def test_int_result_no_decimal(self) -> None:
        self.assertEqual(safe_calculate("2+3"), "计算结果: 5")

    def test_negative_and_priority(self) -> None:
        self.assertIn("-4", safe_calculate("2*(3-5)"))

    def test_reject_function_call(self) -> None:
        self.assertTrue(safe_calculate("__import__('os').system('ls')").startswith("错误"))

    def test_reject_attribute_access(self) -> None:
        self.assertTrue(safe_calculate("().__class__").startswith("错误"))

    def test_reject_letters(self) -> None:
        self.assertTrue(safe_calculate("1+abc").startswith("错误"))

    def test_reject_huge_exponent(self) -> None:
        self.assertTrue(safe_calculate("9**99999999").startswith("错误"))

    def test_division_by_zero(self) -> None:
        self.assertTrue(safe_calculate("1/0").startswith("错误"))

    def test_empty_and_overlong(self) -> None:
        self.assertTrue(safe_calculate("").startswith("错误"))
        self.assertTrue(safe_calculate("1" * 300).startswith("错误"))


class _FakeClient:
    """脚本化假客户端: 依次回放预设轮次 (tool_calls 或纯文本)"""

    def __init__(self, rounds: list[dict]):
        self._rounds = rounds
        self.calls: list[dict] = []

    async def stream_chat(self, messages, temperature=0.8, max_tokens=1024, tools=None):
        self.calls.append({"messages": list(messages), "tools": tools})
        round = self._rounds.pop(0)
        for seg in round.get("deltas", []):
            yield "delta", {"content": seg}
        if "tool_calls" in round:
            yield "tool_calls", {
                "calls": round["tool_calls"],
                "content": round.get("content", ""),
                "promptTokens": round.get("promptTokens", 10),
                "completionTokens": round.get("completionTokens", 5),
            }
        else:
            yield "done", {
                "promptTokens": round.get("promptTokens", 10),
                "completionTokens": round.get("completionTokens", 5),
                "status": "COMPLETED",
            }


async def _collect(agen):
    return [item async for item in agen]


class _AsyncCase(unittest.IsolatedAsyncioTestCase):
    async def test_single_tool_round(self) -> None:
        """工具调用 → 回灌 → 续写; usage 累加; done 只出现一次"""
        client = _FakeClient([
            {"deltas": ["让我"], "tool_calls": [
                {"id": "c1", "name": "calculate", "arguments": '{"expression": "2+3"}'}
            ], "promptTokens": 10, "completionTokens": 2},
            {"deltas": ["等于", "5"], "promptTokens": 20, "completionTokens": 4},
        ])
        events = await _collect(run_agent_stream(
            client, [{"role": "user", "content": "2+3=?"}], TOOLS, make_executor(1),
        ))
        types = [e for e, _ in events]
        self.assertEqual(types.count("done"), 1)
        self.assertIn("tool", types)
        tool_payload = next(p for t, p in events if t == "tool")
        self.assertEqual(tool_payload["name"], "calculate")
        self.assertIn("5", tool_payload["result"])
        done = next(p for t, p in events if t == "done")
        self.assertEqual(done["promptTokens"], 30)
        self.assertEqual(done["completionTokens"], 6)
        # 第二轮请求应包含 assistant(tool_calls) 与 tool 结果消息
        second = client.calls[1]
        roles = [m["role"] for m in second["messages"]]
        self.assertIn("assistant", roles)
        self.assertIn("tool", roles)

    async def test_no_tool_single_round(self) -> None:
        client = _FakeClient([{"deltas": ["你好"]}, ])
        events = await _collect(run_agent_stream(
            client, [{"role": "user", "content": "hi"}], TOOLS, make_executor(1),
        ))
        types = [e for e, _ in events]
        self.assertNotIn("tool", types)
        self.assertEqual(types.count("done"), 1)

    async def test_rounds_capped_and_forced_final(self) -> None:
        """模型连续请求工具: 达到上限后强制无 tools 收敛, 流必然结束"""
        calls = [{"id": "cx", "name": "calculate", "arguments": '{"expression": "1+1"}'}]
        client = _FakeClient([{"tool_calls": calls}] * (MAX_TOOL_ROUNDS + 2))
        events = await _collect(run_agent_stream(
            client, [{"role": "user", "content": "loop"}], TOOLS, make_executor(1),
        ))
        done = [p for t, p in events if t == "done"]
        self.assertEqual(len(done), 1)
        # 末轮不应携带 tools
        self.assertIsNone(client.calls[-1]["tools"])

    async def test_unknown_tool_returns_error_text(self) -> None:
        client = _FakeClient([
            {"tool_calls": [{"id": "c9", "name": "no_such_tool", "arguments": "{}"}]},
            {"deltas": ["好的"]},
        ])
        events = await _collect(run_agent_stream(
            client, [{"role": "user", "content": "x"}], TOOLS, make_executor(1),
        ))
        tool_payload = next(p for t, p in events if t == "tool")
        self.assertIn("未知工具", tool_payload["result"])

    async def test_malformed_arguments_tolerated(self) -> None:
        client = _FakeClient([
            {"tool_calls": [{"id": "c2", "name": "calculate", "arguments": "{bad json"}]},
            {"deltas": ["抱歉"]},
        ])
        events = await _collect(run_agent_stream(
            client, [{"role": "user", "content": "x"}], TOOLS, make_executor(1),
        ))
        tool_payload = next(p for t, p in events if t == "tool")
        self.assertTrue(tool_payload["result"].startswith("错误"))


if __name__ == "__main__":
    unittest.main()
