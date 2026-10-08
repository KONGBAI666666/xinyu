"""迁移前存量数据体检: 检查 xinyu 库是否已有 uk_conv_seq / uk_email 冲突数据"""
import asyncio
import os

for line in open(r"E:/虚拟C盘/git/AI聊天项目（python)（W）/.env", encoding="utf-8"):
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip())

import aiomysql  # noqa: E402


async def main() -> bool:
    conn = await aiomysql.connect(host="127.0.0.1", port=3306, user="root",
                                  password=os.environ["MYSQL_PASSWORD"], db="xinyu")
    cur = await conn.cursor()

    await cur.execute(
        "SELECT conversation_id, sequence_no, COUNT(*) c FROM message "
        "GROUP BY conversation_id, sequence_no HAVING c > 1 LIMIT 10"
    )
    rows = await cur.fetchall()
    print(f"[1] uk_conv_seq 冲突组: {len(rows)}")
    for r in rows:
        print("    ", r)

    await cur.execute(
        "SELECT LOWER(email), COUNT(*) c FROM user "
        "WHERE email IS NOT NULL AND deleted=0 GROUP BY LOWER(email) HAVING c > 1 LIMIT 10"
    )
    rows = await cur.fetchall()
    print(f"[2] uk_email 冲突: {len(rows)}")
    for r in rows:
        print("    ", r)

    await cur.execute(
        "SELECT id, username, email FROM user WHERE email IS NOT NULL AND email != LOWER(email) LIMIT 10"
    )
    rows = await cur.fetchall()
    print(f"[3] 大写邮箱需归一化: {len(rows)}")
    for r in rows:
        print("    ", r)

    cur.close()
    conn.close()
    return not rows


ok = asyncio.run(main())
print("体检:", "PASS (可直接迁移)" if ok else "存在需处理数据")
