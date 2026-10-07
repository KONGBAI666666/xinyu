"""用户 Schema — 对应 Java UpdatePasswordDTO"""

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
