"""用户 Schema — 对应 Java UpdatePasswordDTO"""

import re

from pydantic import BaseModel, field_validator


class UpdatePasswordDTO(BaseModel):
    oldPassword: str
    newPassword: str

    @field_validator("newPassword")
    @classmethod
    def validate_new_password(cls, v: str) -> str:
        if not v or not (6 <= len(v) <= 20):
            raise ValueError("新密码长度须为6-20位")
        return v


class UpdateEmailDTO(BaseModel):
    """绑定/更换邮箱; 传 null 或空串清除"""

    email: str | None = None

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str | None) -> str | None:
        if v is None or v == "":
            return None
        if len(v) > 64 or not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", v):
            raise ValueError("邮箱格式不正确")
        return v
