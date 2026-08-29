"""Prompt 模板中心"""

# ---------- 长期记忆 ----------

# 记忆提取: 让 LLM 从对话中提取关于用户的事实信息
EXTRACT_PROMPT = """你是一个记忆提取助手。请从以下对话中提取关于用户的长期记忆信息。

输出要求:
1. 只提取关于用户的事实信息（如姓名、职业、位置、偏好、性格等）
2. 忽略 AI 的回复内容
3. 输出 JSON 数组格式: [{"memory_key": "name/job/location/hobby/preference/personality/relationship/goal/fact", "content": "记忆内容", "importance": "HIGH/MEDIUM/LOW"}]
4. 如果没有值得提取的记忆, 输出空数组 []
5. importance 判断标准: HIGH=核心个人信息, MEDIUM=偏好和习惯, LOW=一般事实

对话内容:
"""


def format_memory_injection(memory_contents: list[str]) -> str:
    """记忆注入: Top-K 记忆 → 追加到角色 system prompt 末尾的文本块"""
    if not memory_contents:
        return ""
    lines = ["\n\n[关于用户的长期记忆，请在回复时自然参考，不要生硬提及]"]
    lines.extend(f"- {content}" for content in memory_contents)
    return "\n".join(lines)
