from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class EmbeddingProfile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    url: str
    model: str = Field(min_length=1)
    dimensions: int = Field(ge=1, le=65536)
    api_key: SecretStr = SecretStr("")
    batch_size: int = Field(default=16, ge=1, le=64)


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
    ingestion_state_dir: Path | None = None
    ingestion_concurrency: int = Field(default=2, ge=1, le=8)
    ingestion_queue_limit: int = Field(default=64, ge=1, le=1024)
    ingestion_input_quota: int = Field(default=512 * 1024 * 1024, ge=1)
    document_timeout_seconds: float = Field(default=30, gt=0, le=30)
    embedding_profiles: dict[str, EmbeddingProfile] = Field(default_factory=dict)
    qdrant_url: str = ""
    qdrant_api_key: SecretStr = SecretStr("")
    qdrant_collection_prefix: str = "testops_knowledge"
