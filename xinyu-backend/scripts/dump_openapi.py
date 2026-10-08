"""导出 OpenAPI 契约 — 前端 codegen 的数据源 (无需启动服务)

用法 (在 xinyu-backend 目录, 用运行时 venv):
    python scripts/dump_openapi.py

产出: ../xinyu-web/openapi.json (入库提交, CI 校验其与代码同步)
前端再执行: pnpm/npm run codegen → src/types/api.generated.ts
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.main import app  # noqa: E402  (仅导入, 不触发 lifespan/DB 连接)

OUT = Path(__file__).resolve().parents[2] / "xinyu-web" / "openapi.json"

spec = app.openapi()
OUT.write_text(json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

paths = len(spec.get("paths", {}))
schemas = len(spec.get("components", {}).get("schemas", {}))
print(f"已导出 OpenAPI: {OUT}")
print(f"paths={paths}, schemas={schemas}")
