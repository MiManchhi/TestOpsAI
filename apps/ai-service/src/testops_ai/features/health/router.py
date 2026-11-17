from fastapi import APIRouter

router = APIRouter()


@router.get("/health/live")
@router.get("/health/ready")
def health() -> dict[str, str]:
    # M0 has no required model or datastore connection.
    return {"status": "UP"}
