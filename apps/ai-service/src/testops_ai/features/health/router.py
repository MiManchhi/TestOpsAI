from fastapi import APIRouter, Request

from testops_ai.core.settings import Settings

router = APIRouter()


@router.get("/health/live")
@router.get("/health/ready")
def health() -> dict[str, str]:
    return {"status": "UP"}


@router.get("/health/configuration")
def model_configuration(request: Request) -> dict[str, bool]:
    settings: Settings = request.app.state.parser.settings
    return {
        "chatModelConfigured": settings.chat_configured,
        "embeddingConfigured": settings.embedding_configured,
    }
