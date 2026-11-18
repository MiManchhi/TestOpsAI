import time
from uuid import uuid4

import httpx
import jwt
import pytest
from conftest import OUTPUT, headers, source, validate_contract
from fastapi.testclient import TestClient

from testops_ai.app import create_app
from testops_ai.core.settings import Settings


def robot_body(file, operation_id):
    return {
        "operationId": operation_id,
        "projectKey": "demo",
        "mode": "FULL_SNAPSHOT",
        "files": [file.reference.model_dump(by_alias=True)],
    }


def test_robot_http_callback_and_real_worker(configured, keys):
    file, operation = source(), str(uuid4())
    auth = headers(keys, operation, [file.reference.object_id])
    requests = []

    def callback(request):
        requests.append(request)
        claims = jwt.decode(
            request.headers["authorization"][7:],
            keys[1][1],
            algorithms=["RS256"],
            audience="testops-server-tools",
            issuer="testops-ai",
        )
        validate_contract("ServiceClaims", claims)
        assert claims["operationId"] == operation
        assert request.url.path == f"/internal/v1/objects/{file.reference.object_id}/content"
        assert request.headers["x-delegation-token"] == auth["X-Delegation-Token"]
        assert request.headers["x-request-id"] == auth["X-Request-Id"]
        return httpx.Response(200, content=file.content)

    with TestClient(create_app(configured, httpx.MockTransport(callback))) as client:
        response = client.post(
            "/internal/v1/parsers/robot", json=robot_body(file, operation), headers=auth
        )
    assert response.status_code == 200, response.text
    assert len(requests) == 1
    assert response.headers["x-request-id"] == auth["X-Request-Id"]
    assert response.json()["cases"][0]["name"] == "Example"
    validate_contract("RobotParseResult", response.json())


def test_output_http_contract(configured, keys):
    file, operation = source(OUTPUT, "output.xml"), str(uuid4())
    with TestClient(
        create_app(
            configured, httpx.MockTransport(lambda _: httpx.Response(200, content=file.content))
        )
    ) as client:
        response = client.post(
            "/internal/v1/parsers/robot-output",
            json={"operationId": operation, "object": file.reference.model_dump(by_alias=True)},
            headers=headers(keys, operation, [file.reference.object_id]),
        )
    assert response.status_code == 200, response.text
    validate_contract("ResultParseResult", response.json())


@pytest.mark.parametrize(
    "changes,expected",
    [
        ({"iss": "wrong"}, 401),
        ({"aud": "wrong"}, 401),
        ({"exp": 1}, 401),
        ({"iat": 1, "exp": int(time.time()) + 1}, 401),
        ({"iat": int(time.time()) + 100}, 401),
        ({"jti": "invalid"}, 401),
        ({"operationId": str(uuid4())}, 403),
        ({"extra": "field"}, 401),
    ],
)
def test_service_claims_fail_before_object_access(configured, keys, changes, expected):
    file, operation = source(), str(uuid4())

    def no_access(_):
        pytest.fail("Object service must not be called")

    with TestClient(create_app(configured, httpx.MockTransport(no_access))) as client:
        response = client.post(
            "/internal/v1/parsers/robot",
            json=robot_body(file, operation),
            headers=headers(keys, operation, [file.reference.object_id], service_changes=changes),
        )
    assert response.status_code == expected
    validate_contract("Error", response.json())


@pytest.mark.parametrize("mutation", ["missing", "wrong-signature", "hs256", "none", "delegation"])
def test_invalid_credentials(configured, keys, mutation):
    file, operation = source(), str(uuid4())
    auth = headers(keys, operation, [file.reference.object_id])
    if mutation == "missing":
        auth.pop("Authorization")
    elif mutation == "delegation":
        auth["X-Delegation-Token"] = "invalid"
    else:
        decoded = jwt.decode(auth["Authorization"][7:], options={"verify_signature": False})
        algorithm = {"wrong-signature": "RS256", "hs256": "HS256", "none": "none"}[mutation]
        key = keys[1][0] if mutation == "wrong-signature" else "x" * 64
        if mutation == "none":
            key = None
        auth["Authorization"] = "Bearer " + jwt.encode(decoded, key, algorithm=algorithm)
    with TestClient(create_app(configured)) as client:
        response = client.post(
            "/internal/v1/parsers/robot", json=robot_body(file, operation), headers=auth
        )
    assert response.status_code == 401


@pytest.mark.parametrize(
    "changes,expected",
    [
        ({"objectIds": []}, 403),
        ({"projectId": "9"}, 403),
        ({"operationId": str(uuid4())}, 403),
        ({"exp": 1}, 401),
        ({"exp": int(time.time()) + 1000}, 401),
    ],
)
def test_delegation_scope(configured, keys, changes, expected):
    file, operation = source(), str(uuid4())
    with TestClient(create_app(configured)) as client:
        response = client.post(
            "/internal/v1/parsers/robot",
            json=robot_body(file, operation),
            headers=headers(
                keys, operation, [file.reference.object_id], delegation_changes=changes
            ),
        )
    assert response.status_code == expected


@pytest.mark.parametrize("status", [401, 403, 404, 302, 500])
def test_object_service_failures(configured, keys, status):
    file, operation = source(), str(uuid4())
    requests = []

    def callback(request):
        requests.append(request)
        return httpx.Response(status, headers={"location": "http://untrusted.test"})

    with TestClient(create_app(configured, httpx.MockTransport(callback))) as client:
        response = client.post(
            "/internal/v1/parsers/robot",
            json=robot_body(file, operation),
            headers=headers(keys, operation, [file.reference.object_id]),
        )
    assert response.status_code == (status if status in {401, 403, 404} else 503)
    assert len(requests) == 1


@pytest.mark.parametrize("payload,expected", [(b"bad", 422), (b"x" * 1000, 413)])
def test_object_digest_size(configured, keys, payload, expected):
    file, operation = source(), str(uuid4())
    with TestClient(
        create_app(configured, httpx.MockTransport(lambda _: httpx.Response(200, content=payload)))
    ) as client:
        response = client.post(
            "/internal/v1/parsers/robot",
            json=robot_body(file, operation),
            headers=headers(keys, operation, [file.reference.object_id]),
        )
    assert response.status_code == expected


def test_unconfigured_and_input_validation(configured, keys):
    with TestClient(create_app(Settings())) as client:
        assert client.post("/internal/v1/parsers/robot", json={}).status_code == 503
        assert client.get("/health/ready").json() == {"status": "UP"}
    file, operation = source(), str(uuid4())
    auth = headers(keys, operation, [file.reference.object_id])
    with TestClient(create_app(configured)) as client:
        body = robot_body(file, operation)
        body["unknown"] = "not allowed"
        assert client.post("/internal/v1/parsers/robot", json=body, headers=auth).status_code == 400
        assert (
            client.post(
                "/internal/v1/parsers/robot",
                content="{",
                headers={**auth, "Content-Type": "application/json"},
            ).status_code
            == 400
        )
        assert (
            client.post(
                "/internal/v1/parsers/robot",
                content="x" * 524289,
                headers={**auth, "Content-Type": "application/json"},
            ).status_code
            == 413
        )
        assert (
            client.post("/internal/v1/parsers/robot", content="{}", headers=auth).status_code == 415
        )


def test_network_timeout_is_sanitized(configured, keys):
    file, operation = source(), str(uuid4())

    def callback(request):
        raise httpx.ReadTimeout("private diagnostics", request=request)

    with TestClient(create_app(configured, httpx.MockTransport(callback))) as client:
        response = client.post(
            "/internal/v1/parsers/robot",
            json=robot_body(file, operation),
            headers=headers(keys, operation, [file.reference.object_id]),
        )
    assert response.status_code == 503
    assert "private" not in response.text
