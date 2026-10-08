"""文件服务测试: 魔数嗅探/大小限制/文件名安全"""

import unittest
from pathlib import Path

from app.core.exceptions import BizException
from app.services.file_service import UPLOAD_ROOT, save_image_sync

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 32
JPEG = b"\xff\xd8\xff" + b"\x00" * 32
# RIFF 容器但缺 WEBP 标记 → 应拒绝
FAKE_RIFF = b"RIFF" + b"\x00" * 4 + b"\x00" * 32


class SniffTest(unittest.TestCase):
    def test_png_accepted(self) -> None:
        record = save_image_sync(1, "USER_AVATAR", "a.png", PNG)
        self.assertEqual(record.file_type, "image/png")
        self.assertTrue(record.url.startswith("/api/static/uploads/user_avatar/"))
        self.assertTrue(record.url.endswith(".png"))
        # 落盘文件确实存在
        self.assertTrue((UPLOAD_ROOT / record.storage_path).exists())
        # 清理测试残留
        (UPLOAD_ROOT / record.storage_path).unlink()

    def test_jpeg_accepted(self) -> None:
        record = save_image_sync(1, "USER_AVATAR", "a.jpg", JPEG)
        self.assertEqual(record.file_type, "image/jpeg")
        (UPLOAD_ROOT / record.storage_path).unlink()

    def test_riff_without_webp_rejected(self) -> None:
        with self.assertRaises(BizException):
            save_image_sync(1, "USER_AVATAR", "a.webp", FAKE_RIFF)

    def test_text_rejected(self) -> None:
        with self.assertRaises(BizException):
            save_image_sync(1, "USER_AVATAR", "a.png", b"<html>fake image</html>")

    def test_empty_rejected(self) -> None:
        with self.assertRaises(BizException):
            save_image_sync(1, "USER_AVATAR", "a.png", b"")

    def test_oversize_rejected(self) -> None:
        big = PNG + b"\x00" * (2 * 1024 * 1024 + 1)
        with self.assertRaises(BizException):
            save_image_sync(1, "USER_AVATAR", "a.png", big)

    def test_filename_server_generated(self) -> None:
        """存储文件名由服务端生成, 客户端原始名不参与路径 (防穿越)"""
        evil = "../../etc/passwd.png"
        record = save_image_sync(1, "USER_AVATAR", evil, PNG)
        self.assertNotIn("..", record.storage_path)
        self.assertNotIn("..", record.url)
        self.assertEqual(record.original_name, evil)  # 原始名仅留痕
        (UPLOAD_ROOT / record.storage_path).unlink()

    def test_upload_root_within_repo(self) -> None:
        self.assertTrue(str(UPLOAD_ROOT).endswith("uploads"))
        self.assertIsInstance(UPLOAD_ROOT, Path)


if __name__ == "__main__":
    unittest.main()
