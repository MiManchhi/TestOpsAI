from fastapi.testclient import TestClient

from testops_ai.app import create_app


def test_health_and_internal_surface() -> None:
    with TestClient(create_app()) as client:
        for path in ("/health/live", "/health/ready"):
            response = client.get(path)
            assert response.status_code == 200
            assert response.json() == {"status": "UP"}
        for path in ("/docs", "/openapi.json", "/internal/ai/chat/stream"):
            assert client.get(path).status_code == 404
