"""空库试跑 alembic 迁移链: 建 scratch 库 → upgrade head → 校验唯一索引 → 冲突插入测试 → 删库"""

import asyncio
import os
import subprocess
import sys

# 加载项目 .env (不打印密钥)
for line in open(r"E:/虚拟C盘/git/AI聊天项目（python)（W）/.env", encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip())

os.environ["XINYU_DB_NAME"] = "xinyu_alembic_tmp"
# 运行时配置前缀 XINYU_ (start.ps1 会把 MYSQL_PASSWORD 映射成 XINYU_DB_PASSWORD, 这里同样)
os.environ.setdefault("XINYU_DB_PASSWORD", os.environ["MYSQL_PASSWORD"])

import aiomysql  # noqa: E402

PWD = os.environ["MYSQL_PASSWORD"]


async def run_sql(sql: str, db: str = "mysql"):
    conn = await aiomysql.connect(host="127.0.0.1", port=3306, user="root", password=PWD, db=db)
    cur = await conn.cursor()
    await cur.execute(sql)
    await conn.commit()
    cur.close()
    conn.close()


async def main() -> bool:
    try:
        await run_sql("DROP DATABASE IF EXISTS xinyu_alembic_tmp")
    except Exception as e:
        print("drop 残留:", e)
    await run_sql("CREATE DATABASE xinyu_alembic_tmp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    print("[1] scratch 库已创建")

    r = subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"], capture_output=True, text=True, timeout=120
    )
    print("[2] alembic exit:", r.returncode)
    if r.returncode != 0:
        print(r.stdout[-600:])
        print(r.stderr[-600:])
        return False

    conn = await aiomysql.connect(
        host="127.0.0.1", port=3306, user="root", password=PWD, db="xinyu_alembic_tmp"
    )
    cur = await conn.cursor()
    await cur.execute(
        "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
        "WHERE TABLE_SCHEMA='xinyu_alembic_tmp' AND INDEX_NAME IN ('uk_conv_seq','uk_email')"
    )
    names = {row[0] for row in await cur.fetchall()}
    ok = {"uk_conv_seq", "uk_email"} <= names
    print("[3] 唯一索引校验:", "PASS" if ok else f"FAIL (found={names})")
    cur.close()
    conn.close()
    if not ok:
        return False

    print("[4] 冲突插入校验:", end=" ")
    conn = await aiomysql.connect(
        host="127.0.0.1", port=3306, user="root", password=PWD, db="xinyu_alembic_tmp"
    )
    cur = await conn.cursor()
    await cur.execute(
        "INSERT INTO message (id, conversation_id, user_id, sequence_no, message_type, content) "
        "VALUES (1, 1, 1, 5, 'USER', 'a')"
    )
    try:
        await cur.execute(
            "INSERT INTO message (id, conversation_id, user_id, sequence_no, message_type, content) "
            "VALUES (2, 1, 1, 5, 'ASSISTANT', 'b')"
        )
        print("FAIL (未触发唯一约束)")
        ok = False
    except Exception as e:
        print("PASS (", str(e).split(":")[0].strip()[:60], ")")
        ok = True
    await conn.rollback()
    cur.close()
    conn.close()

    await run_sql("DROP DATABASE IF EXISTS xinyu_alembic_tmp")
    print("[5] scratch 库已删除")
    return ok


sys.exit(0 if asyncio.run(main()) else 1)
