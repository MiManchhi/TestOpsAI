from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="TESTOPS_AI_", extra="ignore")

    app_name: str = "testops-ai-service"
