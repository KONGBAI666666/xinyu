"""工具注册表 — Agent 化的工具定义与执行

MVP 工具集 (全部只读、用户域内、无外部副作用):
- get_current_datetime: 当前日期时间与星期
- calculate: 安全算术表达式求值 (ast 白名单, 不触网)
- search_history: 按关键词检索该用户自己的历史消息

安全边界:
- 工具结果只作为 tool 消息回灌 LLM, 不回显原始异常 (防信息泄露, 与 LLM
  错误码收敛同一原则);
- calculate 用 ast 白名单求值, 拒绝函数调用/属性访问/下标等一切非算术节点;
- search_history 强制 user_id 过滤, 不存在跨用户读取面。
"""

import ast
import operator
import re
from collections.abc import Awaitable, Callable

from app.core.database import SessionFactory
from app.repositories import message_repo

# 注入 system prompt 的工具使用说明 (模型不一定主动看 schema, 文字说明更稳)
TOOL_GUIDE = """你可以使用以下工具回答问题 (通过 function calling):
- get_current_datetime: 获取当前日期、时间与星期 (用户问"今天几号/现在几点"时用)
- calculate: 精确计算数学表达式 (涉及数值计算时用, 不要心算)
- search_history: 按关键词检索该用户本人的历史聊天记录 (用户问"我之前说过什么"时用)
需要时直接调用, 拿到结果后再作答; 无需工具时正常回复即可。"""

# OpenAI function calling 格式的工具 schema
TOOLS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "get_current_datetime",
            "description": "获取当前的日期、时间和星期",
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "calculate",
            "description": "精确计算数学表达式 (支持 + - * / // % ** 和括号)",
            "parameters": {
                "type": "object",
                "properties": {
                    "expression": {"type": "string", "description": "算术表达式, 如 (3+4)*2"}
                },
                "required": ["expression"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_history",
            "description": "按关键词搜索该用户本人的历史聊天消息",
            "parameters": {
                "type": "object",
                "properties": {"keyword": {"type": "string", "description": "检索关键词"}},
                "required": ["keyword"],
            },
        },
    },
]

# 执行器签名: (name, arguments) -> 结果文本
ToolExecutor = Callable[[str, dict], Awaitable[str]]


# ---------------- calculate: ast 白名单求值 ----------------

_ALLOWED_BIN = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
}
_ALLOWED_UNARY = {ast.USub: operator.neg, ast.UAdd: operator.pos}

_MAX_EXPR_LEN = 200
_MAX_POW_EXPONENT = 1000
_MAX_RESULT = 1e15

_CALC_RE = re.compile(r"^[\d\s+\-*/%().]+$")  # 快速预筛 (防**? ** 由 * 组成, 在白名单内)


def _eval_node(node: ast.AST) -> float | int:
    if isinstance(node, ast.Expression):
        return _eval_node(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, int | float):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in _ALLOWED_BIN:
        left, right = _eval_node(node.left), _eval_node(node.right)
        if isinstance(node.op, ast.Pow) and abs(right) > _MAX_POW_EXPONENT:
            raise ValueError("指数过大")
        value = _ALLOWED_BIN[type(node.op)](left, right)
        if abs(value) > _MAX_RESULT:
            raise ValueError("结果过大")
        return value
    if isinstance(node, ast.UnaryOp) and type(node.op) in _ALLOWED_UNARY:
        return _ALLOWED_UNARY[type(node.op)](_eval_node(node.operand))
    raise ValueError("仅支持算术表达式")


def safe_calculate(expression: str) -> str:
    """求值算术表达式; 任何非法输入都返回错误文案 (不抛异常)"""
    expr = (expression or "").strip()
    if not expr or len(expr) > _MAX_EXPR_LEN:
        return "错误: 表达式为空或过长"
    if not _CALC_RE.match(expr):
        return "错误: 表达式含非法字符"
    try:
        tree = ast.parse(expr, mode="eval")
        value = _eval_node(tree)
    except (ValueError, SyntaxError, ZeroDivisionError, OverflowError) as e:
        return f"错误: {type(e).__name__}"
    if isinstance(value, int) or (isinstance(value, float) and value.is_integer()):
        return f"计算结果: {int(value)}"
    return f"计算结果: {round(value, 10)}"


# ---------------- 执行器 ----------------


def make_executor(user_id: int) -> ToolExecutor:
    """绑定用户域的工具执行器 (search_history 只查该用户自己的消息)"""

    async def execute(name: str, arguments: dict) -> str:
        try:
            if name == "get_current_datetime":
                from app.core.security import now_local

                now = now_local()
                weekdays = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"]
                return f"当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')} {weekdays[now.weekday()]}"

            if name == "calculate":
                return safe_calculate(str(arguments.get("expression", "")))

            if name == "search_history":
                keyword = str(arguments.get("keyword", "")).strip()
                if not keyword:
                    return "错误: 关键词为空"
                async with SessionFactory() as db:
                    hits = await message_repo.search_user_messages(db, user_id, keyword)
                if not hits:
                    return "未找到相关历史消息"
                lines = [
                    f"[{m.created_at:%m-%d %H:%M}] {m.message_type}: {m.content[:80]}"
                    for m in hits
                ]
                return "找到的历史消息:\n" + "\n".join(lines)

            return f"未知工具: {name}"
        except Exception as e:
            # 异常详情只进日志, 工具结果不回显原始错误 (防信息泄露)
            import logging

            logging.getLogger("xinyu.tools").warning(
                "工具执行失败: name=%s, user_id=%s, error=%r", name, user_id, e
            )
            return "工具执行出错, 请换个方式提问"

    return execute
