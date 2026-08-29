"""安全组件: 密码哈希 / JWT / AES-GCM

兼容性约定 (保证存量数据平滑迁移):
- BCrypt: 与 Spring Security BCryptPasswordEncoder 互通 ($2a/$2b 前缀均可校验)
- JWT: HS256, payload 仅含 userId + username + iat + exp, 与 Java jjwt 签发的 token 互相可解析
- AES: AES-256-GCM, Base64(IV[12B] || ciphertext+tag), 密钥取 secret 前 32 字节右补 0,
  与 Java AesCryptoUtil 完全一致 (存量 api_key_encrypted 可继续解密)
"""

import base64
import os
import time
from datetime import datetime, timezone

import bcrypt
import jwt
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from app.core.config import settings

# ---------- 密码 (BCrypt) ----------


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("ascii")


def verify_password(plain: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("ascii"))
    except ValueError:
        return False


# ---------- JWT (HS256, 与 Java jjwt 互通) ----------

JWT_ALGORITHM = "HS256"


def create_token(user_id: int, username: str) -> str:
    now = int(time.time())
    payload = {
        "userId": user_id,
        "username": username,
        "iat": now,
        "exp": now + settings.jwt_expire_ms // 1000,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=JWT_ALGORITHM)


def parse_token(token: str) -> dict:
    """解析并校验 token (签名/过期), 不合法时抛 jwt.InvalidTokenError"""
    return jwt.decode(token, settings.jwt_secret, algorithms=[JWT_ALGORITHM])


def get_user_id_from_claims(claims: dict) -> int | None:
    """从 payload 取 userId (JSON 反序列化可能落在 int/float, 统一转 int)"""
    value = claims.get("userId")
    if value is None:
        return None
    return int(value)


# ---------- AES-256-GCM (与 Java AesCryptoUtil 互通) ----------

_IV_LENGTH = 12
_TAG_LENGTH_BIT = 128


def _aes_key() -> bytes:
    """密钥 = secret 前 32 字节 (不足右补 0), 与 Java 侧推导一致"""
    src = settings.effective_crypto_secret.encode("utf-8")
    return src[:32].ljust(32, b"\x00")


def aes_encrypt(plaintext: str | None) -> str | None:
    """明文 → Base64(IV || ciphertext+tag); None 输入返回 None"""
    if plaintext is None:
        return None
    iv = os.urandom(_IV_LENGTH)
    cipher = AESGCM(_aes_key()).encrypt(iv, plaintext.encode("utf-8"), None)
    return base64.b64encode(iv + cipher).decode("ascii")


def aes_decrypt(ciphertext: str | None) -> str | None:
    """Base64(IV || ciphertext+tag) → 明文; None/空输入返回 None"""
    if not ciphertext:
        return None
    combined = base64.b64decode(ciphertext)
    iv, cipher = combined[:_IV_LENGTH], combined[_IV_LENGTH:]
    return AESGCM(_aes_key()).decrypt(iv, cipher, None).decode("utf-8")


def now_local() -> datetime:
    """项目统一时间基准: Asia/Shanghai 的墙上时间 (无 tzinfo), 与 MySQL DATETIME 对齐"""
    from zoneinfo import ZoneInfo

    return datetime.now(ZoneInfo("Asia/Shanghai")).replace(tzinfo=None)
