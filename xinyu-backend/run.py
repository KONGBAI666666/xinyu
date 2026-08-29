"""开发启动入口 — PyCharm 里直接右键 Run 本文件即可启动后端

自动读取项目根目录 .env (MYSQL_PASSWORD / XINYU_JWT_SECRET) 并映射为
XINYU_ 前缀环境变量, 免去在 PyCharm 运行配置里手动填写。
"""

import os
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

# 1. 加载根目录 .env (存在则读, 不存在走默认值)
env_file = PROJECT_ROOT / ".env"
if env_file.exists():
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())

# 2. 映射为后端配置变量 (.env 用 MYSQL_PASSWORD, 后端读 XINYU_DB_PASSWORD)
os.environ.setdefault("XINYU_ENV", "dev")
os.environ.setdefault("XINYU_DB_PASSWORD", os.environ.get("MYSQL_PASSWORD", ""))

# 3. 确保包路径可解析 (PyCharm 以脚本方式运行时 sys.path[0] 即本目录, 此行兜底)
sys.path.insert(0, str(Path(__file__).resolve().parent))

import uvicorn  # noqa: E402

if __name__ == "__main__":
    uvicorn.run("app.main:app", host="127.0.0.1", port=9200, reload=True)
