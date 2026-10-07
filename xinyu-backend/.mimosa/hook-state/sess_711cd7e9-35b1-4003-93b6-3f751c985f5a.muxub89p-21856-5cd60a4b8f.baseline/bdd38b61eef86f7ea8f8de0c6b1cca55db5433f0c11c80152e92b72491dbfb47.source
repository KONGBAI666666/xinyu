"""Prompt 模板中心"""

# ---------- 长期记忆 ----------

# 记忆提取: 让 LLM 从对话中提取关于用户的事实信息
EXTRACT_PROMPT = """你是一个记忆提取助手。请从以下对话中提取关于用户的长期记忆信息。

输出要求:
1. 只提取关于用户的事实信息（如姓名、职业、位置、偏好、性格等）
2. 忽略 AI 的回复内容
3. 【严格格式】你的回复必须是且仅是一个合法 JSON 数组, 不要输出任何解释、前后缀文字或 markdown 代码块标记
4. 数组元素格式: {"memory_key": "name/job/location/hobby/preference/personality/relationship/goal/fact", "content": "记忆内容", "importance": "HIGH/MEDIUM/LOW"}
5. 如果没有值得提取的记忆, 只输出 []
6. importance 判断标准: HIGH=核心个人信息, MEDIUM=偏好和习惯, LOW=一般事实

正确示例: [{"memory_key": "name", "content": "用户叫小明", "importance": "HIGH"}]

对话内容:
"""


def format_memory_injection(memory_contents: list[str]) -> str:
    """记忆注入: Top-K 记忆 → 追加到角色 system prompt 末尾的文本块"""
    if not memory_contents:
        return ""
    lines = ["\n\n[关于用户的长期记忆，请在回复时自然参考，不要生硬提及]"]
    lines.extend(f"- {content}" for content in memory_contents)
    return "\n".join(lines)
