from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="TESTOPS_AI_", extra="ignore")

    app_name: str = "testops-ai-service"
    server_base_url: str = ""
    server_public_key: SecretStr = SecretStr("")
    private_key: SecretStr = SecretStr("")
    parser_concurrency: int = Field(default=2, ge=1, le=8)
    parser_timeout_seconds: float = Field(default=10, gt=0, le=30)
    request_timeout_seconds: float = Field(default=120, gt=0, le=120)
    object_timeout_seconds: float = Field(default=15, gt=0, le=15)
