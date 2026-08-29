"""安全组件跨语言兼容性测试

测试向量由 Java 侧真实工具类生成 (JDK 21 + xinyu-server 依赖):
- JWT: com.xinyu.common.security.JwtUtil (jjwt 0.12, HS256)
- AES: com.xinyu.common.security.AesCryptoUtil (AES-256-GCM)
- BCrypt: org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder ($2a$)

向量生成日期 2026-08-28。Java 签发的 JWT 带固定 exp, 过期后签名仍然有效,
因此签名兼容性断言关闭 exp 校验 (verify_exp=False), 过期行为由 Python 侧
自签 token 单独覆盖。
"""

import unittest

import jwt

from app.core.config import settings
from app.core.security import (
    aes_decrypt,
    aes_encrypt,
    create_token,
    get_user_id_from_claims,
    hash_password,
    parse_token,
    verify_password,
)

# ---- Java 侧生成的固定向量 ----
SECRET = "unit-test-secret-0123456789abcdef0123456789"
JAVA_JWT = (
    "eyJhbGciOiJIUzI1NiJ9."
    "eyJ1c2VySWQiOjM1MTY3MjI5NTA2MTcyMTA4OCwidXNlcm5hbWUiOiJ2ZWN0b3JfdXNlciIs"
    "ImlhdCI6MTc4NzkxMjkzNiwiZXhwIjoxNzg3OTIwMTM2fQ."
    "4ZPtAvcrj34v8Kecr1m0wvScdy6hje-6oF824EAWCS4"
)
JAVA_JWT_USER_ID = 351672295061721088
JAVA_JWT_USERNAME = "vector_user"
JAVA_AES_CIPHER = "f1e702sYQLbQDZG22hi9Hz++/pnlR4Nl78el7RwEchZxdBazzFvDXo6k7E1akXG2qf1byRJLnOPy"
JAVA_AES_PLAIN = "sk-java-vector-KEY-1234567890"
JAVA_BCRYPT_HASH = "$2a$10$hl5JMY/H2/GuqxAwFBGZM.63UppGBfjGACHWVcCRW.m3kBZ99LM8S"
JAVA_BCRYPT_PASSWORD = "Vector@Pass123"


class SecurityCompatTest(unittest.TestCase):
    """Python 实现与 Java 存量数据 (token / 密文 / 密码哈希) 的互通性"""

    def setUp(self):
        self._orig_jwt = settings.jwt_secret
        self._orig_crypto = settings.crypto_secret
        # 与 Java 向量生成时一致: 独立 crypto 密钥留空, 回退 jwt 密钥
        settings.jwt_secret = SECRET
        settings.crypto_secret = ""

    def tearDown(self):
        settings.jwt_secret = self._orig_jwt
        settings.crypto_secret = self._orig_crypto

    # ---------- JWT ----------

    def test_parse_java_token(self):
        """Python 可校验 jjwt 签发的 HS256 token 并取出 userId/username"""
        claims = jwt.decode(JAVA_JWT, SECRET, algorithms=["HS256"], options={"verify_exp": False})
        self.assertEqual(get_user_id_from_claims(claims), JAVA_JWT_USER_ID)
        self.assertEqual(claims["username"], JAVA_JWT_USERNAME)

    def test_python_token_roundtrip(self):
        """Python 自签 token 可被 parse_token 校验 (签名 + 过期)"""
        token = create_token(JAVA_JWT_USER_ID, JAVA_JWT_USERNAME)
        claims = parse_token(token)
        self.assertEqual(get_user_id_from_claims(claims), JAVA_JWT_USER_ID)
        self.assertEqual(claims["username"], JAVA_JWT_USERNAME)
        # 有效期 2 小时 (与 Java 配置 7_200_000ms 一致)
        self.assertEqual(claims["exp"] - claims["iat"], 7200)

    def test_token_wrong_secret_rejected(self):
        """密钥不一致时签名校验失败"""
        token = create_token(1, "u")
        settings.jwt_secret = "another-secret-another-secret-another-secret!!"
        with self.assertRaises(jwt.InvalidTokenError):
            parse_token(token)

    def test_expired_token_rejected(self):
        """过期 token 抛 InvalidTokenError"""
        expired = jwt.encode(
            {"userId": 1, "username": "u", "iat": 1000, "exp": 2000}, SECRET, algorithm="HS256"
        )
        with self.assertRaises(jwt.InvalidTokenError):
            parse_token(expired)

    # ---------- AES-256-GCM ----------

    def test_decrypt_java_ciphertext(self):
        """Python 可解密 Java AesCryptoUtil 加密的存量 api_key 密文"""
        self.assertEqual(aes_decrypt(JAVA_AES_CIPHER), JAVA_AES_PLAIN)

    def test_aes_roundtrip(self):
        """Python 加密 → 解密往返一致; 随机 IV 使两次密文不同"""
        plain = "sk-python-roundtrip-测试密钥"
        c1 = aes_encrypt(plain)
        c2 = aes_encrypt(plain)
        self.assertEqual(aes_decrypt(c1), plain)
        self.assertEqual(aes_decrypt(c2), plain)
        self.assertNotEqual(c1, c2, "IV 应随机, 同明文两次密文应不同")

    def test_aes_none_passthrough(self):
        """None/空输入原样返回 (对应未配置 API Key 的存量行)"""
        self.assertIsNone(aes_encrypt(None))
        self.assertIsNone(aes_decrypt(None))
        self.assertIsNone(aes_decrypt(""))

    def test_aes_tamper_rejected(self):
        """GCM 完整性: 密文被篡改时解密失败而非返回错乱明文"""
        cipher = aes_encrypt("sk-original")
        tampered = cipher[:-4] + ("AAAA" if not cipher.endswith("AAAA") else "BBBB")
        with self.assertRaises(Exception):
            aes_decrypt(tampered)

    # ---------- BCrypt ----------

    def test_verify_java_bcrypt_hash(self):
        """Python 可校验 Spring BCryptPasswordEncoder 生成的 $2a$ 哈希"""
        self.assertTrue(verify_password(JAVA_BCRYPT_PASSWORD, JAVA_BCRYPT_HASH))
        self.assertFalse(verify_password("wrong-password", JAVA_BCRYPT_HASH))

    def test_bcrypt_roundtrip(self):
        """Python 生成 $2b$ 哈希后哈希/校验往返一致"""
        hashed = hash_password("我的心屿密码123")
        self.assertTrue(hashed.startswith("$2"))
        self.assertTrue(verify_password("我的心屿密码123", hashed))
        self.assertFalse(verify_password("我的心屿密码124", hashed))


if __name__ == "__main__":
    unittest.main()
