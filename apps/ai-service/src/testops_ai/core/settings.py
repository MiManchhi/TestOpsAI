from pathlib import Path
from typing import Self
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

IDENTITY_DIR = Path("/run/workspace-identity")


def valid_http_endpoint(value: str) -> bool:
    try:
        url = urlsplit(value)
        return (
            url.scheme in {"http", "https"}
            and bool(url.hostname)
            and not any((url.username, url.password, url.query, url.fragment))
        )
    except ValueError:
        return False


class EmbeddingProfile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    url: str
    model: str = Field(min_length=1)
    dimensions: int = Field(ge=1, le=65536)
    api_key: SecretStr = SecretStr("")
    batch_size: int = Field(default=16, ge=1, le=64)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="TESTOPS_AI_",
        extra="ignore",
        env_ignore_empty=True,
        secrets_dir=IDENTITY_DIR if IDENTITY_DIR.is_dir() else None,
    )

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
    embedding_url: str = ""
    embedding_model: str = ""
    embedding_dimensions: int | None = Field(default=None, ge=0, le=65536)
    embedding_api_key: SecretStr = SecretStr("")
    answer_embedding_profile: str = "default"
    chat_base_url: str = ""
    chat_timeout_seconds: float = Field(default=45, gt=0, le=100)
    chat_model: str = ""
    chat_api_key: SecretStr = SecretStr("")
    qdrant_url: str = ""
    qdrant_api_key: SecretStr = SecretStr("")
    qdrant_collection_prefix: str = "testops_knowledge"

    @model_validator(mode="after")
    def configure_single_embedding(self) -> Self:
        if self.embedding_url or self.embedding_model or self.embedding_dimensions:
            if self.embedding_url and self.embedding_model and self.embedding_dimensions:
                self.embedding_profiles[self.answer_embedding_profile] = EmbeddingProfile(
                    url=self.embedding_url,
                    model=self.embedding_model,
                    dimensions=self.embedding_dimensions,
                    api_key=self.embedding_api_key,
                )
            else:
                self.embedding_profiles.pop(self.answer_embedding_profile, None)
        return self

    @property
    def chat_configured(self) -> bool:
        return bool(self.chat_model.strip()) and valid_http_endpoint(self.chat_base_url)

    @property
    def embedding_configured(self) -> bool:
        profile = self.embedding_profiles.get(self.answer_embedding_profile)
        return bool(profile and profile.model.strip() and valid_http_endpoint(profile.url))
