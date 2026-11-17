from fastapi import FastAPI

from testops_ai.core.settings import Settings
from testops_ai.features.health.router import router as health_router


def create_app() -> FastAPI:
    settings = Settings()
    app = FastAPI(title=settings.app_name, docs_url=None, redoc_url=None, openapi_url=None)
    app.include_router(health_router)
    return app
