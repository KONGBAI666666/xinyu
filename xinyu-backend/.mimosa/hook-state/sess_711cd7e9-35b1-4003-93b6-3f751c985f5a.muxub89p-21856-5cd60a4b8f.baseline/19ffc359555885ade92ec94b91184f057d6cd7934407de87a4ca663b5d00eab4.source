"""认证 Schema — 对应 Java LoginDTO/RegisterDTO/LoginVO/UserVO"""

from pydantic import BaseModel, field_validator


class RegisterDTO(BaseModel):
    username: str
    password: str
    nickname: str | None = None

    @field_validator("username")
    @classmethod
    def validate_username(cls, v: str) -> str:
        import re

        if not re.fullmatch(r"[a-zA-Z0-9_]{4,16}", v or ""):
            raise ValueError("用户名须为4-16位字母、数字或下划线")
        return v

    @field_validator("password")
    @classmethod
    def validate_password(cls, v: str) -> str:
        if not v or not (6 <= len(v) <= 20):
            raise ValueError("密码长度须为6-20位")
        return v

    @field_validator("nickname")
    @classmethod
    def validate_nickname(cls, v: str | None) -> str | None:
        if v and len(v) > 30:
            raise ValueError("昵称最长30个字符")
        return v


class LoginDTO(BaseModel):
    username: str
    password: str


class UserVO(BaseModel):
    id: str
    username: str
    nickname: str
    avatarUrl: str | None = None


class AuthResponse(BaseModel):
    """注册/登录共用响应: 注册即登录"""

    token: str
    user: UserVO
